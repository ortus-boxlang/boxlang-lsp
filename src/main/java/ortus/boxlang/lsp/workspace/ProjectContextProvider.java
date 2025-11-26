package ortus.boxlang.lsp.workspace;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DidChangeWatchedFilesRegistrationOptions;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.FileSystemWatcher;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.Registration;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WatchKind;
import org.eclipse.lsp4j.WorkspaceDiagnosticReport;
import org.eclipse.lsp4j.WorkspaceDocumentDiagnosticReport;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;

import com.google.gson.JsonObject;

import ortus.boxlang.compiler.ast.BoxNode;
import ortus.boxlang.compiler.ast.Point;
import ortus.boxlang.compiler.ast.expression.BoxFunctionInvocation;
import ortus.boxlang.compiler.ast.expression.BoxMethodInvocation;
import ortus.boxlang.compiler.ast.visitor.PrettyPrintBoxVisitor;
import ortus.boxlang.lsp.App;
import ortus.boxlang.lsp.LSPTools;
import ortus.boxlang.lsp.UserSettings;
import ortus.boxlang.lsp.lint.LintConfig;
import ortus.boxlang.lsp.lint.LintConfigLoader;
import ortus.boxlang.lsp.workspace.codeLens.CodeLensFacts;
import ortus.boxlang.lsp.workspace.codeLens.CodeLensRuleBook;
import ortus.boxlang.lsp.workspace.completion.CompletionFacts;
import ortus.boxlang.lsp.workspace.completion.CompletionProviderRuleBook;
import ortus.boxlang.lsp.workspace.visitors.FindDefinitionTargetVisitor;
import ortus.boxlang.lsp.workspace.visitors.FindReferenceTargetVisitor;
import ortus.boxlang.runtime.async.executors.BoxExecutor;
import ortus.boxlang.runtime.services.AsyncService;

public class ProjectContextProvider {

	private boolean shouldAnalyzePath( URI docURI ) {
		try {
			var folders = getWorkspaceFolders();
			if ( folders == null || folders.isEmpty() ) {
				return true; // no workspace context -> analyze
			}
			Path	root		= Path.of( new URI( folders.getFirst().getUri() ) );
			Path	filePath	= Paths.get( docURI );
			if ( !filePath.startsWith( root ) ) {
				return true; // outside workspace? allow
			}
			Path		rel	= root.relativize( filePath );
			LintConfig	lc	= LintConfigLoader.get();
			return lc.shouldAnalyze( rel.toString() );
		} catch ( Exception e ) {
			return true; // fail open
		}
	}

	static ProjectContextProvider		instance;
	private List<WorkspaceFolder>		workspaceFolders			= new ArrayList<WorkspaceFolder>();
	private LanguageClient				client;
	private Map<URI, FileParseResult>	parsedFiles					= new HashMap<URI, FileParseResult>();
	private Map<URI, FileParseResult>	openDocuments				= new HashMap<URI, FileParseResult>();
	private List<FunctionDefinition>	functionDefinitions			= new ArrayList<FunctionDefinition>();
	private UserSettings				userSettings				= new UserSettings();
	private long						WorkspaceDiagnosticReportId	= 1;
	private List<DiagnosticReport>		cachedDiagnosticReports		= new CopyOnWriteArrayList<DiagnosticReport>();

	private boolean						shouldPublishDiagnostics	= false;
	private final AtomicBoolean			workspaceParseRunning		= new AtomicBoolean( false );

	public static ortus.boxlang.compiler.ast.Position toBLPosition( Position lspPosition ) {
		Point								start	= new Point( lspPosition.getLine(), lspPosition.getCharacter() );
		ortus.boxlang.compiler.ast.Position	BLPos	= new ortus.boxlang.compiler.ast.Position( start, start );

		return BLPos;
	}

	public static Range positionToRange( ortus.boxlang.compiler.ast.Position pos ) {
		return new Range(
		    new Position( pos.getStart().getLine() - 1, pos.getStart().getColumn() ),
		    new Position( pos.getEnd().getLine() - 1, pos.getEnd().getColumn() ) );
	}

	public static ProjectContextProvider getInstance() {
		if ( instance == null ) {
			instance = new ProjectContextProvider();
		}

		return instance;
	}

	public List<DiagnosticReport> getCachedDiagnosticReports() {
		return cachedDiagnosticReports;
	}

	public void remove( URI docURI ) {
		this.parsedFiles.remove( docURI );
		this.openDocuments.remove( docURI );
	}

	public void setUserSettings( UserSettings settings ) {
		this.userSettings = settings;
	}

	public UserSettings getUserSettings() {
		return userSettings;
	}

	public long getWorkspaceDiagnosticReportId() {
		return WorkspaceDiagnosticReportId;
	}

	public void parseWorkspace() {
		CompletableFuture.runAsync( () -> {
			System.out.println( "Generating workspace diagnostic report" );
			ProjectContextProvider					provider	= ProjectContextProvider.getInstance();
			WorkspaceDiagnosticReport				report		= new WorkspaceDiagnosticReport();
			List<WorkspaceDocumentDiagnosticReport>	docReports	= new ArrayList<>();
			report.setItems( docReports );

			if ( provider.getWorkspaceFolders() == null
			    || provider.getWorkspaceFolders().isEmpty() ) {
				return;
			}

			if ( !this.userSettings.isEnableBackgroundParsing() ) {
				return;
			}

			// TODO: this code should only be able to run one at a time, if it is already
			// running, it should be cancelled and restarted
			// once it completes (exceptionally or successfully) it should end the lock so that the
			// next one can run

			if ( !this.workspaceParseRunning.compareAndSet( false, true ) ) {
				App.logger.info( "Workspace parsing is already running" );
				// already running
				return;
			}

			try {
				BoxExecutor executor = AsyncService.chooseParallelExecutor( "LSP_diagnostic", 0, true );
				executor.submitAndGet( () -> {
					try {
						Stream<Path> stream = Files
						    .walk( Path.of( new URI( provider.getWorkspaceFolders().getFirst().getUri() ) ) );

						if ( this.userSettings.isProcessDiagnosticsInParallel() ) {
							stream.parallel();
						}

						stream
						    .filter( LSPTools::canWalkFile )
						    .filter( p -> shouldAnalyzePath( p.toUri() ) )
						    .forEach( ( clazzPath ) -> {
							    try {
								    List<Diagnostic> diagnostics			= provider.getFileDiagnostics( clazzPath.toUri() );

								    DiagnosticReport cachedFileDiagnostics	= this.cachedDiagnosticReports.stream()
								        .filter( dr -> dr.getFileURI().toString().equals( clazzPath.toUri().toString() ) )
								        .findFirst()
								        .orElseGet( () -> {
																				        DiagnosticReport newReport = new DiagnosticReport( clazzPath.toUri() );
																				        this.cachedDiagnosticReports.add( newReport );
																				        return newReport;
																			        } );

								    cachedFileDiagnostics.setDiagnostics( diagnostics );
							    } catch ( Exception e ) {
								    // TODO Auto-generated catch block
								    e.printStackTrace();
							    }
						    } );

						stream.close();

					} catch ( IOException | URISyntaxException e ) {
						e.printStackTrace();
					} finally {
						App.logger.info( "Completed workspace diagnostic report" );
						workspaceParseRunning.set( false );
					}

				} );
			} catch ( Exception e ) {
				e.printStackTrace();
				App.logger.info( "Completed workspace diagnostic report" );
				workspaceParseRunning.set( false );
			}
		} );

	}

	public Map<String, Path> getMappings() {
		return new HashMap<>();
	}

	public List<Diagnostic> getFileDiagnostics( URI docURI ) {
		if ( !shouldAnalyzePath( docURI ) ) {
			return new ArrayList<>();
		}
		return getLatestFileParseResult( docURI )
		    .map( ( res ) -> res.getDiagnostics() )
		    .orElseGet( () -> new ArrayList<Diagnostic>() );
	}

	public List<CodeAction> getFileCodeActions( URI docURI ) {
		return getLatestFileParseResult( docURI )
		    .map( ( res ) -> res.getCodeActions() )
		    .orElseGet( () -> new ArrayList<CodeAction>() );
	}

	public List<WorkspaceFolder> getWorkspaceFolders() {
		return this.workspaceFolders;
	}

	public void setWorkspaceFolders( List<WorkspaceFolder> folders ) {
		this.workspaceFolders = folders;
	}

	public void setShouldPublishDiagnostics( boolean shouldPublishDiagnostics ) {
		if ( this.shouldPublishDiagnostics == shouldPublishDiagnostics ) {
			return;
		}

		this.shouldPublishDiagnostics = shouldPublishDiagnostics;

		BoxExecutor executor = AsyncService.chooseParallelExecutor( "LSP_publish", 0, true );

		executor.submitAndGet( () -> {
			this.parsedFiles.keySet()
			    .stream()
			    .parallel()
			    .forEach( ( uri ) -> publishDiagnostics( uri ) );

		} );
	}

	public void setLanguageClient( LanguageClient client ) {
		this.client = client;
	}

	public List<? extends TextEdit> formatDocument( URI docUri ) {
		return this.getLatestFileParseResult( docUri )
		    .flatMap( fpr -> fpr.findAstRoot() )
		    .map( astRoot -> {
			    List<TextEdit>		edits					= new ArrayList<TextEdit>();

			    PrettyPrintBoxVisitor prettyPrintBoxVisitor	= new PrettyPrintBoxVisitor();

			    astRoot.accept( prettyPrintBoxVisitor );

			    if ( astRoot != null ) {
				    Range range = positionToRange( astRoot.getPosition() );
				    range.getStart().setLine( 0 );
				    range.getStart().setCharacter( 0 );
				    range.getEnd().setLine( range.getEnd().getLine() + 1 );
				    edits.add( new TextEdit( range,
				        prettyPrintBoxVisitor.getOutput() ) );
			    }

			    return edits;
		    } ).orElse( new ArrayList<>() );

	}

	public void trackDocumentChange( URI docUri, List<TextDocumentContentChangeEvent> changes ) {

		if ( openDocuments.containsKey( docUri ) ) {
			this.openDocuments.remove( docUri );
		}

		for ( TextDocumentContentChangeEvent change : changes ) {
			if ( change.getRange() == null ) {
				FileParseResult fpr = FileParseResult.fromSourceString( docUri, change.getText() );
				this.openDocuments.put( docUri, fpr );
				cacheLatestDiagnostics( fpr );
			}
		}
	}

	public void trackDocumentSave( URI docUri, String text ) {
		String fileContent = text;

		if ( fileContent == null ) {
			try {
				fileContent = Files.readString( Paths.get( docUri ) );
			} catch ( IOException e ) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		FileParseResult fpr = FileParseResult.fromSourceString( docUri, fileContent );
		this.openDocuments.put( docUri, fpr );
		cacheLatestDiagnostics( fpr );
	}

	public void trackDocumentOpen( URI docUri, String text ) {
		FileParseResult fpr = FileParseResult.fromSourceString( docUri, text );
		this.openDocuments.put( docUri, fpr );
		cacheLatestDiagnostics( fpr );
	}

	public void trackDocumentClose( URI docUri ) {
		this.openDocuments.remove( docUri );
		this.parsedFiles.remove( docUri );
	}

	private Optional<FileParseResult> getLatestFileParseResult( URI docUri ) {
		if ( this.openDocuments.containsKey( docUri ) ) {
			return Optional.of( this.openDocuments.get( docUri ) );
		}

		if ( this.parsedFiles.containsKey( docUri ) ) {
			return Optional.of( this.parsedFiles.get( docUri ) );
		}

		FileParseResult result = FileParseResult.fromFileSystem( docUri );

		cacheLatestDiagnostics( result );

		return Optional.of( result );
	}

	public Optional<List<Either<SymbolInformation, DocumentSymbol>>> getDocumentSymbols( URI docURI ) {
		return getLatestFileParseResult( docURI )
		    .map( ( res ) -> res.getOutline() );
	}

	public List<Location> findFunctionUsages( URI docURI, Position pos ) {
		return findReferenceTarget( docURI, pos )
		    .map( node -> {
			    if ( node instanceof ortus.boxlang.compiler.ast.statement.BoxFunctionDeclaration fnDecl ) {
				    String name = fnDecl.getName();
				    return getLatestFileParseResult( docURI )
				        .map( fpr -> fpr.findAstRoot()
				            .map( root -> {
					            // naive search: all BoxFunctionInvocation with matching name
					            return root
					                .getDescendantsOfType( ortus.boxlang.compiler.ast.expression.BoxFunctionInvocation.class,
					                    n -> n.getName().equalsIgnoreCase( name ) )
					                .stream()
					                .map( inv -> {
						                ortus.boxlang.compiler.ast.Position p = inv.getPosition();
						                Location			l	= new Location();
						                l.setUri( docURI.toString() );
						                l.setRange( new Range( new Position( p.getStart().getLine() - 1, p.getStart().getColumn() ),
						                    new Position( p.getStart().getLine() - 1, p.getStart().getColumn() + inv.getName().length() ) ) );
						                return l;
					                } )
					                .toList();
				            } ).orElseGet( () -> new ArrayList<Location>() ) )
				        .orElseGet( () -> new ArrayList<Location>() );
			    }
			    return new ArrayList<Location>();
		    } )
		    .orElseGet( () -> new ArrayList<Location>() );
	}

	public List<Location> findMatchingFunctionDeclarations( URI docURI, String functionName ) {
		return getLatestFileParseResult( docURI )
		    .map( fpr -> fpr.getFunctionDefinitions().stream()
		        .filter( fn -> fn.getFunctionName().equalsIgnoreCase( functionName ) )
		        .map( FunctionDefinition::getLocation )
		        .toList() )
		    .orElseGet( () -> new ArrayList<Location>() );
	}

	public List<Location> findDefinitionPossibiltiies( URI docURI, Position pos ) {
		return findDefinitionTarget( docURI, pos )
		    .map( ( node ) -> {
			    if ( node instanceof BoxFunctionInvocation fnUse ) {
				    // should only be able to find function definitions in these locations
				    // same file
				    // global - should this be found? or what should we show?
				    // parent class
				    return findMatchingFunctionDeclarations( docURI, fnUse.getName() );
			    } else if ( node instanceof BoxMethodInvocation ) {
				    // should only be able to find function definitions in these locations
				    // the type being accessed
				    // a parent of the type being accessed
				    // member function versions of BIFs if the type matches
				    // this -> same rules as BoxFunctionInvocation
			    }

			    return new ArrayList<Location>();
		    } )
		    .orElseGet( () -> new ArrayList<Location>() );
	}

	public List<CompletionItem> getAvailableCompletions( URI docURI, CompletionParams params ) {
		// TODO if you are in a cfscript component within a template script completions
		// TODO if you are in a cfset return script completions
		// TODO add completions for in-scope symbols (properties, local variables,

		return getLatestFileParseResult( docURI ).map( ( res ) -> {
			return CompletionProviderRuleBook.execute( new CompletionFacts( res, params ) );
		} ).orElseGet( () -> new ArrayList<CompletionItem>() );
	}

	public List<CodeLens> getAvailableCodeLenses( URI docURI, CodeLensParams params ) {
		return getLatestFileParseResult( docURI ).map( ( res ) -> {
			return CodeLensRuleBook.execute( new CodeLensFacts( res, params ) );
		} ).orElseGet( () -> new ArrayList<CodeLens>() );
	}

	public Optional<BoxNode> findReferenceTarget( URI docURI, Position position ) {
		return getLatestFileParseResult( docURI )
		    .flatMap( fpr -> fpr.findAstRoot() )
		    .map( ( rootNode ) -> {
			    FindReferenceTargetVisitor visitor = new FindReferenceTargetVisitor( position );
			    rootNode.accept( visitor );

			    return visitor.getReferenceTarget();
		    } );

	}

	public Optional<BoxNode> findDefinitionTarget( URI docURI, Position position ) {
		return getLatestFileParseResult( docURI )
		    .flatMap( fpr -> fpr.findAstRoot() )
		    .map( ( rootNode ) -> {
			    FindDefinitionTargetVisitor visitor = new FindDefinitionTargetVisitor( position );

			    rootNode.accept( visitor );

			    return visitor.getDefinitionTarget();
		    } );

	}

	private void publishDiagnostics( URI docURI ) {
		if ( this.client == null ) {
			return;
		}

		PublishDiagnosticsParams diagnosticParams = new PublishDiagnosticsParams();
		diagnosticParams.setUri( docURI.toString() );
		List<Diagnostic> diagnostics = getLatestFileParseResult( docURI )
		    .map( res -> res.getDiagnostics() )
		    .orElseGet( () -> new ArrayList<Diagnostic>() );

		diagnosticParams.setDiagnostics( diagnostics );

		if ( !this.shouldPublishDiagnostics ) {
			this.client.publishDiagnostics( diagnosticParams );
			return;
		}

		this.client.publishDiagnostics( diagnosticParams );
	}

	public List<Either<Command, CodeAction>> getAvailableCodeActions( URI convertDocumentURI, CodeActionParams params ) {
		List<Either<Command, CodeAction>> actions = new ArrayList<>();

		if ( params.getContext().getDiagnostics().size() != 0 ) {
			this.getFileCodeActions( convertDocumentURI ).stream().filter( codeAction -> {
				for ( Diagnostic cad : codeAction.getDiagnostics() ) {
					@SuppressWarnings( "unchecked" )
					Map<String, Object>	cadData					= ( Map<String, Object> ) cad.getData();
					String				codeActionDiagnosticId	= ( String ) cadData.get( "id" );

					for ( Diagnostic d : params.getContext().getDiagnostics() ) {
						JsonObject data = ( JsonObject ) d.getData();

						if ( data == null ) {
							return false;
						}

						String clientDiagnosticId = data.get( "id" ).getAsString();
						if ( codeActionDiagnosticId.equals( clientDiagnosticId ) ) {
							return true;
						}
					}
				}

				return false;
			} )
			    .forEach( action -> actions.add( Either.forRight( action ) ) );
		}

		return actions;
	}

	private void cacheLatestDiagnostics( FileParseResult fpr ) {
		DiagnosticReport cachedFileDiagnostics = this.cachedDiagnosticReports.stream()
		    .filter( dr -> dr.getFileURI().toString().equals( fpr.getURI().toString() ) )
		    .findFirst()
		    .orElseGet( () -> {
			    DiagnosticReport newReport = new DiagnosticReport( fpr.getURI() );
			    this.cachedDiagnosticReports.add( newReport );
			    return newReport;
		    } );

		cachedFileDiagnostics.setDiagnostics( fpr.getDiagnostics() );
	}

	/** Recompute diagnostics for all currently open documents and publish immediately. */
	public void recomputeAndPublishDiagnosticsForOpenDocuments() {
		this.openDocuments.forEach( ( uri, fpr ) -> {
			fpr.reparse();
			cacheLatestDiagnostics( fpr );
			publishDiagnostics( uri );
		} );
	}

	public void watchLSPConfig() {
		startConfigWatcher();
		try {
			FileSystemWatcher watcher = new FileSystemWatcher();
			watcher.setGlobPattern( ".bxlint.json" );
			watcher.setKind( WatchKind.Create + WatchKind.Change + WatchKind.Delete );
			DidChangeWatchedFilesRegistrationOptions	options			= new DidChangeWatchedFilesRegistrationOptions( List.of( watcher ) );
			Registration								registration	= new Registration( UUID.randomUUID().toString(), "workspace/didChangeWatchedFiles",
			    options );
			client.registerCapability( new RegistrationParams( List.of( registration ) ) );
			App.logger.info( "Registered dynamic file watcher for .bxlint.json" );
		} catch ( Exception e ) {
			App.logger.warn( "Failed to register dynamic file watcher", e );
		}
	}

	private void startConfigWatcher() {
		var folders = ortus.boxlang.lsp.workspace.ProjectContextProvider.getInstance().getWorkspaceFolders();
		if ( folders == null || folders.isEmpty() ) {
			return;
		}
		new Thread( () -> {
			try {
				java.nio.file.Path			root	= java.nio.file.Path.of( new java.net.URI( folders.getFirst().getUri() ) );
				java.nio.file.Path			cfg		= root.resolve( ortus.boxlang.lsp.lint.LintConfigLoader.CONFIG_FILENAME );
				java.nio.file.WatchService	watcher	= root.getFileSystem().newWatchService();
				root.register( watcher, java.nio.file.StandardWatchEventKinds.ENTRY_CREATE, java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY,
				    java.nio.file.StandardWatchEventKinds.ENTRY_DELETE );
				App.logger.info( "Started lint config watcher at: " + cfg );
				while ( true ) {
					java.nio.file.WatchKey	key		= watcher.take();
					boolean					changed	= false;
					for ( var event : key.pollEvents() ) {
						java.nio.file.Path changedPath = root.resolve( ( java.nio.file.Path ) event.context() );
						if ( changedPath.equals( cfg ) ) {
							changed = true;
							break;
						}
					}
					key.reset();
					if ( changed ) {
						App.logger.info( "Detected lint config change: " + cfg + "; reloading and recomputing diagnostics." );
						LintConfigLoader.invalidate();
						// Eager reload to surface any parse errors immediately
						LintConfigLoader.get();
						// Recompute diagnostics for currently open documents
						var provider = ortus.boxlang.lsp.workspace.ProjectContextProvider.getInstance();
						provider.recomputeAndPublishDiagnosticsForOpenDocuments();

						try {
							provider.clearExcludedDiagnostics();
							provider.parseWorkspace();
						} catch ( Exception e ) {
							App.logger.warn( "Failed to re-parse workspace after lint config change", e );
						}
					}
				}
			} catch ( Exception e ) {
				App.logger.warn( "Config watcher failure", e );
			}
		}, "boxlang-lsp-config-watcher" ).start();
	}

	private void clearExcludedDiagnostics() {
		var		lc				= LintConfigLoader.get();

		String	workspaceFolder	= workspaceFolders.getFirst().getUri();

		if ( workspaceFolder == null || workspaceFolder.isEmpty() ) {
			return;
		}

		Path workspaceFolderPath;
		try {
			workspaceFolderPath = Paths.get( new URI( workspaceFolder ) );
		} catch ( URISyntaxException e ) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		}

		var keep = this.cachedDiagnosticReports.stream()
		    .map( dr -> {
			    Path filePath		= Paths.get( dr.getFileURI() );
			    Path relativePath	= workspaceFolderPath.relativize( filePath );

			    if ( !lc.shouldAnalyze( relativePath.toString() ) ) {
				    dr.setDiagnostics( new ArrayList() );
			    }

			    return dr;
		    } )
		    .collect( Collectors.toList() );

		this.cachedDiagnosticReports = keep;
		// this.cachedDiagnosticReports.forEach( dr -> {

		// // clear first
		// var clearParams = new PublishDiagnosticsParams();
		// clearParams.setUri( dr.getFileURI().toString() );
		// clearParams.setDiagnostics( List.of() );

		// this.client.publishDiagnostics( clearParams );

		// var params = new PublishDiagnosticsParams();
		// params.setUri( dr.getFileURI().toString() );
		// params.setDiagnostics( dr.getDiagnostics() );

		// this.client.publishDiagnostics( params );
		// } );

	}
}
