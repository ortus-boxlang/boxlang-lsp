package ortus.boxlang.lsp;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.ApplyWorkspaceEditParams;
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse;
import org.eclipse.lsp4j.ConfigurationItem;
import org.eclipse.lsp4j.ConfigurationParams;
import org.eclipse.lsp4j.CreateFile;
import org.eclipse.lsp4j.CreateFileOptions;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ShowDocumentParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceDiagnosticParams;
import org.eclipse.lsp4j.WorkspaceDiagnosticReport;
import org.eclipse.lsp4j.WorkspaceDocumentDiagnosticReport;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.WorkspaceFullDocumentDiagnosticReport;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.eclipse.lsp4j.WorkspaceUnchangedDocumentDiagnosticReport;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.WorkspaceService;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import ortus.boxlang.lsp.config.BxlintConfigGenerator;
import ortus.boxlang.lsp.formatting.FormattingCapabilityCoordinator;
import ortus.boxlang.lsp.formatting.PrettyPrintRuntimeAdapter;
import ortus.boxlang.lsp.workspace.ProjectContextProvider;
import ortus.boxlang.lsp.workspace.index.IndexedClass;
import ortus.boxlang.lsp.workspace.index.IndexedMethod;
import ortus.boxlang.lsp.workspace.index.IndexedProperty;
import ortus.boxlang.lsp.workspace.index.ProjectIndex;

public class BoxLangWorkspaceService implements WorkspaceService {

	public static final String						CREATE_BXLINT_CONFIG_COMMAND	= "boxlang.createBxlintConfig";
	public static final String						CREATE_FORMATTER_CONFIG_COMMAND	= "boxlang.createFormatterConfig";
	public static final String						CONVERT_CFFORMAT_CONFIG_COMMAND	= "boxlang.convertCFFormatConfig";
	private static final String						CREATE_BXLINT_CONFIG_LABEL		= "Create .bxlint.json";
	private static final String						CREATE_FORMATTER_CONFIG_LABEL	= "Create .bxformat.json";
	private static final String						CONVERT_CFFORMAT_CONFIG_LABEL	= "Convert .cfformat.json to .bxformat.json";
	private static final String						BXLINT_CONFIG_FILE_NAME			= ".bxlint.json";
	private static final String						FORMATTER_CONFIG_FILE_NAME		= ".bxformat.json";
	private static final String						CFFORMAT_CONFIG_FILE_NAME		= ".cfformat.json";
	private static final String						CFCONFIG_CONFIG_FILE_NAME		= ".cfconfig.json";
	private static final Gson						GSON							= new Gson();
	private static final int						MAX_RESULTS						= 200;
	private LanguageClient							client;
	private final FormattingCapabilityCoordinator	formattingCapabilityCoordinator;
	private final PrettyPrintRuntimeAdapter			prettyPrintRuntimeAdapter;

	public BoxLangWorkspaceService() {
		this( new FormattingCapabilityCoordinator(), new PrettyPrintRuntimeAdapter() );
	}

	BoxLangWorkspaceService( FormattingCapabilityCoordinator formattingCapabilityCoordinator ) {
		this( formattingCapabilityCoordinator, new PrettyPrintRuntimeAdapter() );
	}

	BoxLangWorkspaceService( FormattingCapabilityCoordinator formattingCapabilityCoordinator, PrettyPrintRuntimeAdapter prettyPrintRuntimeAdapter ) {
		this.formattingCapabilityCoordinator	= formattingCapabilityCoordinator;
		this.prettyPrintRuntimeAdapter			= prettyPrintRuntimeAdapter;
	}

	public void setLanguageClient( LanguageClient client ) {
		this.client = client;
	}

	public CompletableFuture<Void> loadInitialConfiguration() {
		if ( this.client == null ) {
			return CompletableFuture.completedFuture( null );
		}

		ConfigurationItem lspSettings = new ConfigurationItem();
		lspSettings.setSection( "boxlang.lsp" );

		ConfigurationItem boxlangSettings = new ConfigurationItem();
		boxlangSettings.setSection( "boxlang" );

		try {
			return this.client.configuration( new ConfigurationParams( List.of( lspSettings, boxlangSettings ) ) )
			    .thenAccept( settings -> didChangeConfiguration( new DidChangeConfigurationParams( mergeConfigurationSettings( settings ) ) ) )
			    .exceptionally( throwable -> {
				    App.logger.warn( "Unable to load initial workspace configuration", throwable );
				    return null;
			    } );
		} catch ( UnsupportedOperationException e ) {
			return CompletableFuture.completedFuture( null );
		}
	}

	@Override
	public void didChangeConfiguration( DidChangeConfigurationParams params ) {
		ProjectContextProvider	provider		= ProjectContextProvider.getInstance();
		var						oldSettings		= provider.getUserSettings();
		var						newSettings		= UserSettings.fromChangeConfigurationParams( this.client, params );

		boolean					mappingsChanged	= !oldSettings.getMappings().equals( newSettings.getMappings() );

		provider.setUserSettings( newSettings );

		if ( mappingsChanged ) {
			provider.handleMappingChange( newSettings.getMappings() );
		}

		if ( oldSettings.isEnableBackgroundParsing() == false && newSettings.isEnableBackgroundParsing() == true ) {
			// if we are enabling background parsing, kick off a parse of the workspace
			provider.parseWorkspace();
		}
		formattingCapabilityCoordinator.refresh( ortus.boxlang.lsp.lint.LintConfigLoader.get(), newSettings );
	}

	private static JsonObject mergeConfigurationSettings( List<Object> settings ) {
		JsonObject	mergedSettings	= new JsonObject();

		JsonObject	lspSettings		= getConfigurationSection( settings, 0 );
		if ( lspSettings != null ) {
			lspSettings.entrySet().forEach( entry -> mergedSettings.add( entry.getKey(), entry.getValue() ) );
		}

		JsonObject boxlangSettings = getConfigurationSection( settings, 1 );
		if ( boxlangSettings != null ) {
			mergedSettings.add( "boxlang", boxlangSettings );
		}

		return mergedSettings;
	}

	private static JsonObject getConfigurationSection( List<Object> settings, int index ) {
		if ( settings == null || settings.size() <= index || settings.get( index ) == null ) {
			return null;
		}

		Object		value	= settings.get( index );
		JsonElement	element	= value instanceof JsonElement jsonElement ? jsonElement.deepCopy() : GSON.toJsonTree( value );
		if ( element == null || !element.isJsonObject() ) {
			return null;
		}

		return element.getAsJsonObject();
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

	@Override
	public CompletableFuture<Object> executeCommand( ExecuteCommandParams params ) {
		if ( client == null ) {
			return CompletableFuture.failedFuture( new IllegalStateException( "Language client is not connected" ) );
		}

		return switch ( params.getCommand() ) {
			case CREATE_BXLINT_CONFIG_COMMAND -> executeCreateBxlintConfig( params );
			case CREATE_FORMATTER_CONFIG_COMMAND -> executeCreateFormatterConfig( params );
			case CONVERT_CFFORMAT_CONFIG_COMMAND -> executeConvertCFFormatConfig( params );
			default -> CompletableFuture.failedFuture( new UnsupportedOperationException( "Unsupported command: " + params.getCommand() ) );
		};
	}

	private CompletableFuture<Object> executeCreateBxlintConfig( ExecuteCommandParams params ) {
		CreateConfigCommandArguments	arguments		= CreateConfigCommandArguments.from( params.getArguments() );
		String							workspaceUri	= arguments.workspaceUri() != null ? arguments.workspaceUri() : getDefaultWorkspaceUri();
		if ( workspaceUri == null || workspaceUri.isBlank() ) {
			return CompletableFuture.failedFuture( new IllegalStateException( "No workspace folder available to create .bxlint.json" ) );
		}

		String			configUri	= resolveConfigUri( workspaceUri, BXLINT_CONFIG_FILE_NAME );
		WorkspaceEdit	edit		= createJsonConfigWorkspaceEdit( configUri, arguments.overwrite(), BxlintConfigGenerator.generateFullConfig() );

		return client.applyEdit( new ApplyWorkspaceEditParams( edit, CREATE_BXLINT_CONFIG_LABEL ) )
		    .thenCompose( response -> handleCreateConfigResponse( response, configUri, arguments.openDocument() ) );
	}

	private CompletableFuture<Object> executeCreateFormatterConfig( ExecuteCommandParams params ) {
		if ( !formattingCapabilityCoordinator.isRuntimeSupported() ) {
			return CompletableFuture.failedFuture( new UnsupportedOperationException( "PrettyPrint runtime support is unavailable" ) );
		}

		CreateConfigCommandArguments	arguments		= CreateConfigCommandArguments.from( params.getArguments() );
		String							workspaceUri	= arguments.workspaceUri() != null ? arguments.workspaceUri() : getDefaultWorkspaceUri();
		if ( workspaceUri == null || workspaceUri.isBlank() ) {
			return CompletableFuture.failedFuture( new IllegalStateException( "No workspace folder available to create .bxformat.json" ) );
		}

		String formatterConfig;
		try {
			formatterConfig = prettyPrintRuntimeAdapter.getDefaultConfigJson();
		} catch ( PrettyPrintRuntimeAdapter.PrettyPrintException e ) {
			return CompletableFuture.failedFuture( e );
		}

		String			configUri	= resolveConfigUri( workspaceUri, FORMATTER_CONFIG_FILE_NAME );
		WorkspaceEdit	edit		= createJsonConfigWorkspaceEdit( configUri, arguments.overwrite(), formatterConfig );

		return client.applyEdit( new ApplyWorkspaceEditParams( edit, CREATE_FORMATTER_CONFIG_LABEL ) )
		    .thenCompose( response -> handleCreateConfigResponse( response, configUri, arguments.openDocument() ) );
	}

	private CompletableFuture<Object> executeConvertCFFormatConfig( ExecuteCommandParams params ) {
		if ( !formattingCapabilityCoordinator.isRuntimeSupported() ) {
			return CompletableFuture.failedFuture( new UnsupportedOperationException( "PrettyPrint runtime support is unavailable" ) );
		}

		CreateConfigCommandArguments	arguments		= CreateConfigCommandArguments.from( params.getArguments() );
		String							workspaceUri	= arguments.workspaceUri() != null ? arguments.workspaceUri() : getDefaultWorkspaceUri();
		if ( workspaceUri == null || workspaceUri.isBlank() ) {
			return CompletableFuture.failedFuture( new IllegalStateException( "No workspace folder available to convert .cfformat.json" ) );
		}

		Path sourceConfigPath = resolveCFFormatSourcePath( workspaceUri );
		if ( sourceConfigPath == null ) {
			return CompletableFuture.failedFuture( new IllegalStateException( "No .cfformat.json or .cfconfig.json file exists in the workspace root" ) );
		}

		String formatterConfig;
		try {
			formatterConfig = prettyPrintRuntimeAdapter.convertCFFormatConfigToBxFormatJson( sourceConfigPath );
		} catch ( PrettyPrintRuntimeAdapter.PrettyPrintException e ) {
			return CompletableFuture.failedFuture( e );
		}

		String			configUri	= resolveConfigUri( workspaceUri, FORMATTER_CONFIG_FILE_NAME );
		WorkspaceEdit	edit		= createJsonConfigWorkspaceEdit( configUri, arguments.overwrite(), formatterConfig );

		return client.applyEdit( new ApplyWorkspaceEditParams( edit, CONVERT_CFFORMAT_CONFIG_LABEL ) )
		    .thenCompose( response -> handleCreateConfigResponse( response, configUri, arguments.openDocument() ) );
	}

	private CompletableFuture<Object> handleCreateConfigResponse( ApplyWorkspaceEditResponse response, String configUri, boolean openDocument ) {
		if ( response == null || !response.isApplied() ) {
			String failureReason = response != null && response.getFailureReason() != null
			    ? response.getFailureReason()
			    : "The client rejected the workspace edit";
			client.showMessage( new MessageParams( MessageType.Warning, failureReason ) );
			return CompletableFuture.failedFuture( new IllegalStateException( failureReason ) );
		}

		if ( !openDocument ) {
			return CompletableFuture.completedFuture( configUri );
		}

		ShowDocumentParams showDocumentParams = new ShowDocumentParams( configUri );
		showDocumentParams.setTakeFocus( true );

		try {
			return client.showDocument( showDocumentParams )
			    .handle( ( ignored, throwable ) -> {
				    if ( throwable != null ) {
					    App.logger.warn( "Unable to open generated configuration file", throwable );
				    }
				    return configUri;
			    } );
		} catch ( UnsupportedOperationException e ) {
			return CompletableFuture.completedFuture( configUri );
		}
	}

	private static String getDefaultWorkspaceUri() {
		List<org.eclipse.lsp4j.WorkspaceFolder> folders = ProjectContextProvider.getInstance().getWorkspaceFolders();
		if ( folders == null || folders.isEmpty() ) {
			return null;
		}

		return folders.getFirst().getUri();
	}

	private static String resolveConfigUri( String workspaceUri, String fileName ) {
		return Path.of( URI.create( workspaceUri ) ).resolve( fileName ).toUri().toString();
	}

	private static Path resolveCFFormatSourcePath( String workspaceUri ) {
		Path	workspacePath	= Path.of( URI.create( workspaceUri ) );
		Path	cfformatPath	= workspacePath.resolve( CFFORMAT_CONFIG_FILE_NAME );
		if ( Files.exists( cfformatPath ) ) {
			return cfformatPath;
		}

		Path cfconfigPath = workspacePath.resolve( CFCONFIG_CONFIG_FILE_NAME );
		if ( Files.exists( cfconfigPath ) ) {
			return cfconfigPath;
		}

		return null;
	}

	private static WorkspaceEdit createJsonConfigWorkspaceEdit( String configUri, boolean overwrite, String contents ) {
		CreateFileOptions createFileOptions = new CreateFileOptions();
		createFileOptions.setOverwrite( overwrite );
		createFileOptions.setIgnoreIfExists( false );

		CreateFile			createFile			= new CreateFile( configUri, createFileOptions );
		TextEdit			initialContents		= new TextEdit( new Range( new Position( 0, 0 ), new Position( 0, 0 ) ), contents );
		TextDocumentEdit	textDocumentEdit	= new TextDocumentEdit( new VersionedTextDocumentIdentifier( configUri, null ), List.of( initialContents ) );
		WorkspaceEdit		edit				= new WorkspaceEdit();

		edit.setDocumentChanges( List.of( Either.forRight( createFile ), Either.forLeft( textDocumentEdit ) ) );
		return edit;
	}

	private record CreateConfigCommandArguments( String workspaceUri, boolean overwrite, boolean openDocument ) {

		private static CreateConfigCommandArguments from( List<Object> arguments ) {
			if ( arguments == null || arguments.isEmpty() || arguments.getFirst() == null ) {
				return new CreateConfigCommandArguments( null, false, true );
			}

			Object firstArgument = arguments.getFirst();
			if ( firstArgument instanceof String workspaceUri ) {
				return new CreateConfigCommandArguments( workspaceUri, false, true );
			}

			JsonElement element = firstArgument instanceof JsonElement jsonElement ? jsonElement : GSON.toJsonTree( firstArgument );
			if ( element == null || !element.isJsonObject() ) {
				return new CreateConfigCommandArguments( null, false, true );
			}

			JsonObject	jsonObject		= element.getAsJsonObject();
			String		workspaceUri	= getString( jsonObject, "workspaceUri", getString( jsonObject, "workspaceFolderUri", null ) );
			boolean		overwrite		= getBoolean( jsonObject, "overwrite", false );
			boolean		openDocument	= getBoolean( jsonObject, "openDocument", true );
			return new CreateConfigCommandArguments( workspaceUri, overwrite, openDocument );
		}

		private static boolean getBoolean( JsonObject jsonObject, String key, boolean defaultValue ) {
			return jsonObject.has( key ) && !jsonObject.get( key ).isJsonNull() ? jsonObject.get( key ).getAsBoolean() : defaultValue;
		}

		private static String getString( JsonObject jsonObject, String key, String defaultValue ) {
			return jsonObject.has( key ) && !jsonObject.get( key ).isJsonNull() ? jsonObject.get( key ).getAsString() : defaultValue;
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
