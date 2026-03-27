package ortus.boxlang.lsp;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.WorkspaceDiagnosticParams;
import org.eclipse.lsp4j.WorkspaceDiagnosticReport;
import org.eclipse.lsp4j.WorkspaceDocumentDiagnosticReport;
import org.eclipse.lsp4j.WorkspaceFullDocumentDiagnosticReport;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.eclipse.lsp4j.WorkspaceUnchangedDocumentDiagnosticReport;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.WorkspaceService;

import ortus.boxlang.lsp.workspace.ProjectContextProvider;
import ortus.boxlang.lsp.workspace.index.IndexedClass;
import ortus.boxlang.lsp.workspace.index.IndexedMethod;
import ortus.boxlang.lsp.workspace.index.IndexedProperty;
import ortus.boxlang.lsp.workspace.index.ProjectIndex;

public class BoxLangWorkspaceService implements WorkspaceService {

	private static final int	MAX_RESULTS	= 200;
	private LanguageClient		client;

	public void setLanguageClient( LanguageClient client ) {
		this.client = client;
	}

	@Override
	public void didChangeConfiguration( DidChangeConfigurationParams params ) {
		ProjectContextProvider	provider	= ProjectContextProvider.getInstance();
		var						oldSettings	= provider.getUserSettings();
		var						newSettings	= UserSettings.fromChangeConfigurationParams( this.client, params );

		if ( oldSettings.isEnableBackgroundParsing() == false && newSettings.isEnableBackgroundParsing() == true ) {
			// if we are enabling background parsing, kick off a parse of the workspace
			provider.parseWorkspace();
		}

		provider.setUserSettings( newSettings );
	}

	@Override
	public void didChangeWatchedFiles( DidChangeWatchedFilesParams params ) {
		ProjectContextProvider provider = ProjectContextProvider.getInstance();
		if ( params.getChanges() == null ) {
			return;
		}
		for ( org.eclipse.lsp4j.FileEvent event : params.getChanges() ) {
			try {
				java.net.URI fileUri = LSPTools.convertDocumentURI( event.getUri() );
				if ( fileUri != null ) {
					provider.handleConfigFileChange( fileUri );
				}
			} catch ( Exception e ) {
				App.logger.warn( "Error processing file-change event for: " + event.getUri(), e );
			}
		}
	}

	/**
	 * Handle workspace/symbol request to search for symbols across the workspace.
	 */
	@Override
	public CompletableFuture<Either<List<? extends SymbolInformation>, List<? extends WorkspaceSymbol>>> symbol( WorkspaceSymbolParams params ) {
		return CompletableFutures.computeAsync( ( cancelToken ) -> {
			ProjectContextProvider	provider	= ProjectContextProvider.getInstance();
			ProjectIndex			index		= provider.getIndex();

			if ( index == null ) {
				return Either.forLeft( new ArrayList<>() );
			}

			String				query			= params.getQuery() != null ? params.getQuery() : "";
			String				lowerQuery		= query.toLowerCase();
			List<ScoredSymbol>	scoredSymbols	= new ArrayList<>();

			// Search classes
			for ( IndexedClass indexedClass : index.getAllClasses() ) {
				int score = calculateScore( indexedClass.name(), lowerQuery );
				if ( score > 0 || query.isEmpty() ) {
					SymbolInformation symbol = createClassSymbol( indexedClass );
					scoredSymbols.add( new ScoredSymbol( symbol, score ) );
				}
			}

			// Search methods
			for ( IndexedMethod indexedMethod : index.getAllMethods() ) {
				int score = calculateScore( indexedMethod.name(), lowerQuery );
				if ( score > 0 || query.isEmpty() ) {
					SymbolInformation symbol = createMethodSymbol( indexedMethod );
					scoredSymbols.add( new ScoredSymbol( symbol, score ) );
				}
			}

			// Search properties
			for ( IndexedProperty indexedProperty : index.getAllProperties() ) {
				int score = calculateScore( indexedProperty.name(), lowerQuery );
				if ( score > 0 || query.isEmpty() ) {
					SymbolInformation symbol = createPropertySymbol( indexedProperty );
					scoredSymbols.add( new ScoredSymbol( symbol, score ) );
				}
			}

			cancelToken.checkCanceled();

			// Sort by score (descending), then by name length (shorter first)
			scoredSymbols.sort( Comparator
			    .comparingInt( ( ScoredSymbol s ) -> -s.score )
			    .thenComparingInt( s -> s.symbol.getName().length() )
			    .thenComparing( s -> s.symbol.getName().toLowerCase() ) );

			// Limit results
			List<SymbolInformation> results = scoredSymbols.stream()
			    .limit( MAX_RESULTS )
			    .map( s -> s.symbol )
			    .toList();

			return Either.forLeft( results );
		} );
	}

	/**
	 * Calculate a score for how well a symbol name matches the query.
	 * Higher scores indicate better matches.
	 *
	 * Scoring:
	 * - Exact match: 1000
	 * - Prefix match (case-insensitive): 500
	 * - Contains match (case-insensitive): 100
	 * - No match: 0
	 */
	private int calculateScore( String symbolName, String lowerQuery ) {
		if ( lowerQuery.isEmpty() ) {
			return 1; // Return all symbols for empty query with low score
		}

		String lowerName = symbolName.toLowerCase();

		// Exact match (case-insensitive)
		if ( lowerName.equals( lowerQuery ) ) {
			return 1000;
		}

		// Prefix match
		if ( lowerName.startsWith( lowerQuery ) ) {
			return 500;
		}

		// Contains match
		if ( lowerName.contains( lowerQuery ) ) {
			return 100;
		}

		return 0;
	}

	/**
	 * Create a SymbolInformation for an indexed class.
	 */
	private SymbolInformation createClassSymbol( IndexedClass indexedClass ) {
		SymbolInformation symbol = new SymbolInformation();
		symbol.setName( indexedClass.name() );
		symbol.setKind( indexedClass.isInterface() ? SymbolKind.Interface : SymbolKind.Class );
		symbol.setContainerName( null ); // Classes don't have containers

		// Set location
		if ( indexedClass.fileUri() != null ) {
			Range range = indexedClass.location() != null ? indexedClass.location()
			    : new Range( new Position( 0, 0 ), new Position( 0, 0 ) );
			symbol.setLocation( new Location( indexedClass.fileUri(), range ) );
		}

		return symbol;
	}

	/**
	 * Create a SymbolInformation for an indexed method.
	 */
	private SymbolInformation createMethodSymbol( IndexedMethod indexedMethod ) {
		SymbolInformation symbol = new SymbolInformation();
		symbol.setName( indexedMethod.name() );

		// Use Function kind for standalone functions, Method for class methods
		if ( indexedMethod.containingClass() == null || indexedMethod.containingClass().isEmpty() ) {
			symbol.setKind( SymbolKind.Function );
		} else {
			symbol.setKind( SymbolKind.Method );
			symbol.setContainerName( indexedMethod.containingClass() );
		}

		// Set location
		if ( indexedMethod.fileUri() != null ) {
			Range range = indexedMethod.location() != null ? indexedMethod.location()
			    : new Range( new Position( 0, 0 ), new Position( 0, 0 ) );
			symbol.setLocation( new Location( indexedMethod.fileUri(), range ) );
		}

		return symbol;
	}

	/**
	 * Create a SymbolInformation for an indexed property.
	 */
	private SymbolInformation createPropertySymbol( IndexedProperty indexedProperty ) {
		SymbolInformation symbol = new SymbolInformation();
		symbol.setName( indexedProperty.name() );
		symbol.setKind( SymbolKind.Property );
		symbol.setContainerName( indexedProperty.containingClass() );

		// Set location
		if ( indexedProperty.fileUri() != null ) {
			Range range = indexedProperty.location() != null ? indexedProperty.location()
			    : new Range( new Position( 0, 0 ), new Position( 0, 0 ) );
			symbol.setLocation( new Location( indexedProperty.fileUri(), range ) );
		}

		return symbol;
	}

	/**
	 * Helper record for sorting symbols by score.
	 */
	private record ScoredSymbol( SymbolInformation symbol, int score ) {
	}

	public CompletableFuture<WorkspaceDiagnosticReport> diagnostic( WorkspaceDiagnosticParams params ) {
		return CompletableFutures.computeAsync( ( cancelToken ) -> {
			ProjectContextProvider					provider	= ProjectContextProvider.getInstance();
			WorkspaceDiagnosticReport				report		= new WorkspaceDiagnosticReport();
			List<WorkspaceDocumentDiagnosticReport>	docReports	= new ArrayList<>();

			report.setItems( docReports );

			provider.getCachedDiagnosticReports().stream()
			    .forEach( cachedFileDiagnostics -> {
				    for ( var prevId : params.getPreviousResultIds() ) {
					    if ( cachedFileDiagnostics.matches( prevId ) ) {
						    // TODO the null value needs to check if the file is in an open state and return the version identifier
						    WorkspaceDocumentDiagnosticReport docReport = new WorkspaceDocumentDiagnosticReport(
						        new WorkspaceUnchangedDocumentDiagnosticReport( prevId.getValue(), cachedFileDiagnostics.getFileURI().toString(), null ) );

						    docReports.add( docReport );
						    return;
					    }
				    }

				    WorkspaceFullDocumentDiagnosticReport fullReport = new WorkspaceFullDocumentDiagnosticReport();
				    WorkspaceDocumentDiagnosticReport	docReport	= new WorkspaceDocumentDiagnosticReport( fullReport );
				    fullReport.setResultId( String.valueOf( cachedFileDiagnostics.getResultId() ) );
				    fullReport.setUri( cachedFileDiagnostics.getFileURI().toString() );
				    fullReport.setItems( cachedFileDiagnostics.getDiagnostics() );

				    docReports.add( docReport );
			    } );

			cancelToken.checkCanceled();

			return report;
		} );
	}

}
