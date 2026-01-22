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
import java.util.concurrent.ConcurrentHashMap;
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
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.ParameterInformation;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SignatureInformation;
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
import ortus.boxlang.compiler.ast.IBoxDocumentableNode;
import ortus.boxlang.compiler.ast.Point;
import ortus.boxlang.compiler.ast.comment.BoxDocComment;
import ortus.boxlang.compiler.ast.expression.BoxFQN;
import ortus.boxlang.compiler.ast.expression.BoxFunctionInvocation;
import ortus.boxlang.compiler.ast.expression.BoxIdentifier;
import ortus.boxlang.compiler.ast.expression.BoxMethodInvocation;
import ortus.boxlang.compiler.ast.expression.BoxNew;
import ortus.boxlang.compiler.ast.expression.BoxScope;
import ortus.boxlang.compiler.ast.statement.BoxArgumentDeclaration;
import ortus.boxlang.compiler.ast.statement.BoxDocumentationAnnotation;
import ortus.boxlang.compiler.ast.statement.BoxFunctionDeclaration;
import ortus.boxlang.compiler.ast.statement.BoxProperty;
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
import ortus.boxlang.lsp.workspace.index.IndexedClass;
import ortus.boxlang.lsp.workspace.index.IndexedMethod;
import ortus.boxlang.lsp.workspace.index.IndexedParameter;
import ortus.boxlang.lsp.workspace.index.ProjectIndex;
import ortus.boxlang.lsp.workspace.visitors.FindDefinitionTargetVisitor;
import ortus.boxlang.lsp.workspace.visitors.FindHoverTargetVisitor;
import ortus.boxlang.lsp.workspace.visitors.FindReferenceTargetVisitor;
import ortus.boxlang.lsp.workspace.visitors.FindSignatureHelpTargetVisitor;
import ortus.boxlang.lsp.workspace.visitors.VariableScopeCollectorVisitor;
import ortus.boxlang.lsp.workspace.visitors.VariableScopeCollectorVisitor.VariableInfo;
import ortus.boxlang.lsp.workspace.visitors.VariableScopeCollectorVisitor.VariableScope;
import ortus.boxlang.lsp.workspace.visitors.VariableTypeCollectorVisitor;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.async.executors.BoxExecutor;
import ortus.boxlang.runtime.bifs.BIFDescriptor;
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

	static ProjectContextProvider					instance;
	private List<WorkspaceFolder>					workspaceFolders			= new ArrayList<WorkspaceFolder>();
	private LanguageClient							client;
	private Map<URI, FileParseResult>				parsedFiles					= new ConcurrentHashMap<URI, FileParseResult>();
	private Map<URI, FileParseResult>				openDocuments				= new ConcurrentHashMap<URI, FileParseResult>();
	private Map<URI, DocumentModel>					documentModels				= new ConcurrentHashMap<URI, DocumentModel>();
	private List<FunctionDefinition>				functionDefinitions			= new ArrayList<FunctionDefinition>();
	private UserSettings							userSettings				= new UserSettings();
	private long									WorkspaceDiagnosticReportId	= 1;
	private List<DiagnosticReport>					cachedDiagnosticReports		= new CopyOnWriteArrayList<DiagnosticReport>();

	private boolean									shouldPublishDiagnostics	= false;
	private final AtomicBoolean						workspaceParseRunning		= new AtomicBoolean( false );
	private ProjectIndex							projectIndex;
	private final DebouncedDocumentProcessor		documentProcessor			= new DebouncedDocumentProcessor( 300 );

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

	/**
	 * Get the project index for symbol lookups.
	 * Lazily initializes the index if needed.
	 *
	 * @return The project index
	 */
	public ProjectIndex getIndex() {
		if ( projectIndex == null ) {
			projectIndex = new ProjectIndex();
			// Initialize with workspace root if available
			if ( workspaceFolders != null && !workspaceFolders.isEmpty() ) {
				try {
					Path workspaceRoot = Path.of( new URI( workspaceFolders.getFirst().getUri() ) );
					projectIndex.initialize( workspaceRoot );
				} catch ( Exception e ) {
					App.logger.warn( "Failed to initialize project index with workspace root", e );
				}
			}
		}
		return projectIndex;
	}

	/**
	 * Set the project index. Primarily used for testing.
	 *
	 * @param index The project index to use
	 */
	public void setIndex( ProjectIndex index ) {
		this.projectIndex = index;
	}

	public List<DiagnosticReport> getCachedDiagnosticReports() {
		return cachedDiagnosticReports;
	}

	public void remove( URI docURI ) {
		this.parsedFiles.remove( docURI );
		this.openDocuments.remove( docURI );
		// Remove from project index as well
		if ( projectIndex != null ) {
			projectIndex.removeFile( docURI );
		}
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

						// Get the project index for incremental indexing
						var index = getIndex();

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

								    // Index the file for symbol lookups - only if it needs re-indexing
								    // This implements incremental re-indexing: skip files that haven't changed
								    if ( index.needsReindexing( clazzPath.toUri() ) ) {
									    index.indexFile( clazzPath.toUri() );
								    }
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
						// Save the project index cache
						if ( projectIndex != null ) {
							projectIndex.saveCache();
							App.logger.info( "Saved project index cache" );
						}
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
		trackDocumentChange( docUri, changes, -1 );
	}

	public void trackDocumentChange( URI docUri, List<TextDocumentContentChangeEvent> changes, int version ) {
		DocumentModel model = documentModels.get( docUri );

		if ( model == null ) {
			// No model exists - create one from the first change
			String initialContent = "";
			for ( TextDocumentContentChangeEvent change : changes ) {
				if ( change.getRange() == null ) {
					initialContent = change.getText();
					break;
				}
			}
			model = new DocumentModel( docUri, initialContent, version > 0 ? version : 1 );
			documentModels.put( docUri, model );
		} else {
			// Apply changes to existing model
			int newVersion = version > 0 ? version : model.getVersion() + 1;
			if ( !model.applyChanges( changes, newVersion ) ) {
				// Version was stale, ignore these changes
				App.logger.debug( "Ignoring stale document changes for " + docUri + " (version " + version + " <= " + model.getVersion() + ")" );
				return;
			}
		}

		// Schedule debounced processing
		final DocumentModel finalModel = model;
		documentProcessor.scheduleProcessing( docUri, () -> {
			processDocumentUpdate( docUri, finalModel.getContent() );
		} );
	}

	/**
	 * Processes a document update after debouncing.
	 * This performs the expensive parsing and diagnostic operations.
	 */
	private void processDocumentUpdate( URI docUri, String content ) {
		FileParseResult fpr = FileParseResult.fromSourceString( docUri, content );
		this.openDocuments.put( docUri, fpr );
		cacheLatestDiagnostics( fpr );
		publishDiagnostics( docUri );
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

		// Reindex the file for symbol lookups
		getIndex().reindexFile( docUri );
	}

	public void trackDocumentOpen( URI docUri, String text ) {
		trackDocumentOpen( docUri, text, 1 );
	}

	public void trackDocumentOpen( URI docUri, String text, int version ) {
		// Create document model for incremental sync support
		DocumentModel model = new DocumentModel( docUri, text, version );
		documentModels.put( docUri, model );

		// Parse immediately on open (no debouncing)
		FileParseResult fpr = FileParseResult.fromSourceString( docUri, text );
		this.openDocuments.put( docUri, fpr );
		cacheLatestDiagnostics( fpr );
	}

	public void trackDocumentClose( URI docUri ) {
		// Cancel any pending processing
		documentProcessor.cancelPendingProcessing( docUri );
		// Clean up document model
		documentModels.remove( docUri );
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

	/**
	 * Public accessor for getLatestFileParseResult, used for testing.
	 *
	 * @param docUri The document URI
	 * @return Optional containing the FileParseResult if found
	 */
	public Optional<FileParseResult> getLatestFileParseResultPublic( URI docUri ) {
		return getLatestFileParseResult( docUri );
	}

	/**
	 * Gets the current version of a document.
	 *
	 * @param docUri The document URI
	 * @return The document version, or -1 if the document is not tracked
	 */
	public int getDocumentVersion( URI docUri ) {
		DocumentModel model = documentModels.get( docUri );
		return model != null ? model.getVersion() : -1;
	}

	/**
	 * Gets the document model for a URI.
	 *
	 * @param docUri The document URI
	 * @return The DocumentModel, or null if not found
	 */
	public DocumentModel getDocumentModel( URI docUri ) {
		return documentModels.get( docUri );
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

	/**
	 * Get hover information for the symbol at the given position.
	 *
	 * @param docURI The document URI
	 * @param position The cursor position
	 *
	 * @return Hover information or null if no hover is available
	 */
	public Hover getHoverInfo( URI docURI, Position position ) {
		return getLatestFileParseResult( docURI )
		    .flatMap( fpr -> fpr.findAstRoot() )
		    .map( rootNode -> {
			    FindHoverTargetVisitor visitor = new FindHoverTargetVisitor( position );
			    rootNode.accept( visitor );
			    BoxNode target = visitor.getHoverTarget();

			    if ( target == null ) {
				    return null;
			    }

			    // Handle function invocations - look up the function definition
			    if ( target instanceof BoxFunctionInvocation fnInvocation ) {
				    String functionName = fnInvocation.getName();
				    // Find the function declaration in the same file
				    return rootNode
				        .getDescendantsOfType( BoxFunctionDeclaration.class,
				            n -> n.getName().equalsIgnoreCase( functionName ) )
				        .stream()
				        .findFirst()
				        .map( fnDecl -> buildHoverForFunction( fnDecl, getClassNameFromUri( docURI ) ) )
				        .orElse( null );
			    }

			    // Handle function declarations directly
			    if ( target instanceof BoxFunctionDeclaration fnDecl ) {
				    return buildHoverForFunction( fnDecl, getClassNameFromUri( docURI ) );
			    }

			    // Handle method invocations
			    if ( target instanceof BoxMethodInvocation methodInvocation ) {
				    String methodName = methodInvocation.getName().getSourceText();

				    // First, try to resolve the object's type using variable tracking
				    BoxNode obj = methodInvocation.getObj();
				    if ( obj instanceof BoxIdentifier objIdentifier ) {
					    String varName = objIdentifier.getName();

					    // Collect variable types from the AST
					    VariableTypeCollectorVisitor typeCollector = new VariableTypeCollectorVisitor();
					    rootNode.accept( typeCollector );
					    String className = typeCollector.getVariableType( varName );

					    if ( className != null ) {
						    // Look up method in the project index
						    var indexedMethodOpt = getIndex().findMethod( className, methodName );
						    if ( indexedMethodOpt.isPresent() ) {
							    return buildHoverForIndexedMethod( indexedMethodOpt.get() );
						    }
					    }
				    }

				    // Fall back to finding method in the same file
				    return rootNode
				        .getDescendantsOfType( BoxFunctionDeclaration.class,
				            n -> n.getName().equalsIgnoreCase( methodName ) )
				        .stream()
				        .findFirst()
				        .map( fnDecl -> buildHoverForFunction( fnDecl, getClassNameFromUri( docURI ) ) )
				        .orElse( null );
			    }

			    // Handle function parameter declarations
			    if ( target instanceof BoxArgumentDeclaration argDecl ) {
				    return buildHoverForParameter( argDecl );
			    }

			    // Handle property declarations
			    if ( target instanceof BoxProperty property ) {
				    return buildHoverForProperty( property );
			    }

			    // Handle new expressions (class instantiation) - e.g., new UserService()
			    if ( target instanceof BoxNew newExpr ) {
				    String className = extractClassNameFromNew( newExpr );
				    if ( className != null ) {
					    var indexedClassOpt = getIndex().findClassByName( className );
					    if ( indexedClassOpt.isPresent() ) {
						    return buildHoverForClass( indexedClassOpt.get() );
					    }
				    }
			    }

			    // Handle fully qualified names (class references in type hints, extends, implements, etc.)
			    if ( target instanceof BoxFQN fqn ) {
				    String className = extractClassNameFromFQN( fqn );
				    if ( className != null ) {
					    var indexedClassOpt = getIndex().findClassByName( className );
					    if ( indexedClassOpt.isPresent() ) {
						    return buildHoverForClass( indexedClassOpt.get() );
					    }
				    }
			    }

			    // Handle scope keywords (variables, local, this, arguments, etc.)
			    if ( target instanceof BoxScope scopeNode ) {
				    String scopeName = scopeNode.getName();
				    VariableScopeCollectorVisitor scopeCollector = new VariableScopeCollectorVisitor();
				    VariableInfo scopeInfo = scopeCollector.getScopeKeywordInfo( scopeName );
				    if ( scopeInfo != null ) {
					    return buildHoverForScopeKeyword( scopeInfo );
				    }
			    }

			    // Handle variable identifiers
			    if ( target instanceof BoxIdentifier identifier ) {
				    // Collect variable scope information
				    VariableScopeCollectorVisitor scopeCollector = new VariableScopeCollectorVisitor();
				    rootNode.accept( scopeCollector );

				    String varName = identifier.getName();

				    // First check if this is a scope keyword
				    if ( scopeCollector.isScopeKeyword( varName ) ) {
					    VariableInfo scopeInfo = scopeCollector.getScopeKeywordInfo( varName );
					    if ( scopeInfo != null ) {
						    return buildHoverForScopeKeyword( scopeInfo );
					    }
				    }

				    // Find the containing function for context
				    BoxFunctionDeclaration containingFunc = identifier.getFirstAncestorOfType( BoxFunctionDeclaration.class );

				    // Look up variable info
				    VariableInfo varInfo = scopeCollector.getVariableInfo( varName, containingFunc );
				    if ( varInfo != null ) {
					    return buildHoverForVariable( varInfo );
				    }

				    // No variable info found - return null
				    return null;
			    }

			    return null;
		    } )
		    .orElse( null );
	}

	/**
	 * Get signature help for function/method calls at the given position.
	 * This displays parameter hints while typing function calls.
	 *
	 * @param docURI   The document URI
	 * @param position The cursor position
	 *
	 * @return SignatureHelp with function signatures and active parameter
	 */
	public SignatureHelp getSignatureHelp( URI docURI, Position position ) {
		return getLatestFileParseResult( docURI )
		    .flatMap( fpr -> fpr.findAstRoot() )
		    .map( rootNode -> {
			    // Find the function/method invocation at or containing the cursor
			    // The visitor searches the AST on construction
			    FindSignatureHelpTargetVisitor visitor = new FindSignatureHelpTargetVisitor( position, rootNode );

			    BoxNode				target			= visitor.getTarget();
			    int					activeParam		= visitor.getActiveParameter();

			    if ( target == null ) {
				    return null;
			    }

			    // Handle function invocations (UDFs and BIFs)
			    if ( target instanceof BoxFunctionInvocation fnInvocation ) {
				    String functionName = fnInvocation.getName();

				    // First try to find a user-defined function in the same file
				    var udfOpt = rootNode
				        .getDescendantsOfType( BoxFunctionDeclaration.class,
				            n -> n.getName().equalsIgnoreCase( functionName ) )
				        .stream()
				        .findFirst();

				    if ( udfOpt.isPresent() ) {
					    return buildSignatureHelpForFunction( udfOpt.get(), getClassNameFromUri( docURI ), activeParam );
				    }

				    // Then try BIFs
				    SignatureHelp bifHelp = buildSignatureHelpForBIF( functionName, activeParam );
				    if ( bifHelp != null ) {
					    return bifHelp;
				    }

				    return null;
			    }

			    // Handle method invocations
			    if ( target instanceof BoxMethodInvocation methodInvocation ) {
				    String	methodName	= methodInvocation.getName().getSourceText();

				    // Try to resolve the object's type using variable tracking
				    BoxNode	obj			= methodInvocation.getObj();
				    if ( obj instanceof BoxIdentifier objIdentifier ) {
					    String varName = objIdentifier.getName();

					    // Collect variable types from the AST
					    VariableTypeCollectorVisitor typeCollector = new VariableTypeCollectorVisitor();
					    rootNode.accept( typeCollector );
					    String className = typeCollector.getVariableType( varName );

					    if ( className != null ) {
						    // Look up method in the project index
						    var indexedMethodOpt = getIndex().findMethod( className, methodName );
						    if ( indexedMethodOpt.isPresent() ) {
							    return buildSignatureHelpForIndexedMethod( indexedMethodOpt.get(), activeParam );
						    }
					    }
				    }

				    // Fall back to finding method in the same file
				    var localMethodOpt = rootNode
				        .getDescendantsOfType( BoxFunctionDeclaration.class,
				            n -> n.getName().equalsIgnoreCase( methodName ) )
				        .stream()
				        .findFirst();

				    if ( localMethodOpt.isPresent() ) {
					    return buildSignatureHelpForFunction( localMethodOpt.get(), getClassNameFromUri( docURI ), activeParam );
				    }

				    return null;
			    }

			    // Handle constructor calls (new ClassName())
			    if ( target instanceof BoxNew newExpr ) {
				    String className = extractClassNameFromNew( newExpr );
				    if ( className != null ) {
					    // Look for init method in the class
					    var indexedClassOpt = getIndex().findClassByName( className );
					    if ( indexedClassOpt.isPresent() ) {
						    var initMethodOpt = getIndex().findMethod( className, "init" );
						    if ( initMethodOpt.isPresent() ) {
							    return buildSignatureHelpForIndexedMethod( initMethodOpt.get(), activeParam );
						    }
					    }
				    }
				    return null;
			    }

			    return null;
		    } )
		    .orElse( null );
	}

	/**
	 * Build SignatureHelp for a function declaration.
	 */
	private SignatureHelp buildSignatureHelpForFunction( BoxFunctionDeclaration fnDecl, String className, int activeParam ) {
		SignatureHelp			help		= new SignatureHelp();
		List<SignatureInformation>	signatures	= new ArrayList<>();

		SignatureInformation	sigInfo		= new SignatureInformation();

		// Build the signature label
		String signatureLabel = buildFunctionSignature( fnDecl, null );
		sigInfo.setLabel( signatureLabel );

		// Build parameter information
		List<ParameterInformation> params = new ArrayList<>();
		for ( BoxNode child : fnDecl.getChildren() ) {
			if ( child instanceof BoxArgumentDeclaration arg ) {
				ParameterInformation paramInfo = new ParameterInformation();

				// Build the parameter label
				StringBuilder paramLabel = new StringBuilder();
				if ( arg.getRequired() ) {
					paramLabel.append( "required " );
				}
				if ( arg.getType() != null ) {
					paramLabel.append( arg.getType().toString() ).append( " " );
				}
				paramLabel.append( arg.getName() );
				if ( arg.getValue() != null ) {
					paramLabel.append( " = " ).append( arg.getValue().getSourceText() );
				}

				paramInfo.setLabel( paramLabel.toString() );
				params.add( paramInfo );
			}
		}
		sigInfo.setParameters( params );

		// Add documentation if available
		if ( fnDecl instanceof IBoxDocumentableNode documentableNode ) {
			BoxDocComment docComment = documentableNode.getDocComment();
			if ( docComment != null ) {
				StringBuilder docContent = new StringBuilder();

				String commentText = docComment.getCommentText();
				if ( commentText != null && !commentText.isBlank() ) {
					String cleanedDescription = cleanDocCommentDescription( commentText );
					if ( !cleanedDescription.isBlank() ) {
						docContent.append( cleanedDescription );
					}
				}

				List<BoxDocumentationAnnotation> annotations = docComment.getAnnotations();
				if ( annotations != null && !annotations.isEmpty() ) {
					if ( docContent.length() > 0 ) {
						docContent.append( "\n\n" );
					}
					docContent.append( formatDocumentationAnnotations( annotations ) );
				}

				if ( docContent.length() > 0 ) {
					MarkupContent markup = new MarkupContent();
					markup.setKind( MarkupKind.MARKDOWN );
					markup.setValue( docContent.toString().trim() );
					sigInfo.setDocumentation( markup );
				}
			}
		}

		signatures.add( sigInfo );
		help.setSignatures( signatures );
		help.setActiveSignature( 0 );
		help.setActiveParameter( Math.min( activeParam, Math.max( 0, params.size() - 1 ) ) );

		return help;
	}

	/**
	 * Build SignatureHelp for an indexed method (from project index).
	 */
	private SignatureHelp buildSignatureHelpForIndexedMethod( IndexedMethod method, int activeParam ) {
		SignatureHelp			help		= new SignatureHelp();
		List<SignatureInformation>	signatures	= new ArrayList<>();

		SignatureInformation	sigInfo		= new SignatureInformation();

		// Build the signature label
		String signatureLabel = buildSignatureFromIndexedMethod( method );
		sigInfo.setLabel( signatureLabel );

		// Build parameter information
		List<ParameterInformation> params = new ArrayList<>();
		for ( IndexedParameter param : method.parameters() ) {
			ParameterInformation paramInfo = new ParameterInformation();

			StringBuilder paramLabel = new StringBuilder();
			if ( param.required() ) {
				paramLabel.append( "required " );
			}
			if ( param.typeHint() != null && !param.typeHint().isEmpty() ) {
				paramLabel.append( param.typeHint() ).append( " " );
			}
			paramLabel.append( param.name() );
			if ( param.defaultValue() != null ) {
				paramLabel.append( " = " ).append( param.defaultValue() );
			}

			paramInfo.setLabel( paramLabel.toString() );
			params.add( paramInfo );
		}
		sigInfo.setParameters( params );

		// Add documentation if available
		if ( method.documentation() != null && !method.documentation().isBlank() ) {
			MarkupContent markup = new MarkupContent();
			markup.setKind( MarkupKind.MARKDOWN );
			markup.setValue( formatIndexedMethodDocumentation( method.documentation() ) );
			sigInfo.setDocumentation( markup );
		}

		signatures.add( sigInfo );
		help.setSignatures( signatures );
		help.setActiveSignature( 0 );
		help.setActiveParameter( Math.min( activeParam, Math.max( 0, params.size() - 1 ) ) );

		return help;
	}

	/**
	 * Build SignatureHelp for a Built-in Function (BIF).
	 */
	private SignatureHelp buildSignatureHelpForBIF( String functionName, int activeParam ) {
		try {
			BIFDescriptor bifDesc = BoxRuntime.getInstance().getFunctionService().getGlobalFunction( functionName );
			if ( bifDesc == null ) {
				return null;
			}

			SignatureHelp			help		= new SignatureHelp();
			List<SignatureInformation>	signatures	= new ArrayList<>();

			SignatureInformation	sigInfo		= new SignatureInformation();

			// Build parameters
			var					declaredArgs	= bifDesc.getBIF().getDeclaredArguments();
			List<String>		paramStrings	= new ArrayList<>();
			List<ParameterInformation>	params	= new ArrayList<>();

			for ( var arg : declaredArgs ) {
				ParameterInformation paramInfo = new ParameterInformation();
				String				paramSig	= arg.signatureAsString();

				// Add brackets for optional params in signature
				if ( !arg.required() ) {
					paramStrings.add( "[" + paramSig + "]" );
				} else {
					paramStrings.add( paramSig );
				}

				paramInfo.setLabel( paramSig );
				params.add( paramInfo );
			}

			String signatureLabel = functionName + "(" + String.join( ", ", paramStrings ) + ")";
			sigInfo.setLabel( signatureLabel );
			sigInfo.setParameters( params );

			signatures.add( sigInfo );
			help.setSignatures( signatures );
			help.setActiveSignature( 0 );
			help.setActiveParameter( Math.min( activeParam, Math.max( 0, params.size() - 1 ) ) );

			return help;
		} catch ( Exception e ) {
			// BIF lookup failed
			return null;
		}
	}

	/**
	 * Build hover content for a function declaration.
	 */
	private Hover buildHoverForFunction( BoxFunctionDeclaration fnDecl, String className ) {
		StringBuilder content = new StringBuilder();

		// Add function signature in code block
		content.append( "```boxlang\n" );
		content.append( buildFunctionSignature( fnDecl, className ) );
		content.append( "\n```\n\n" );

		// Add documentation if available
		if ( fnDecl instanceof IBoxDocumentableNode documentableNode ) {
			BoxDocComment docComment = documentableNode.getDocComment();
			if ( docComment != null ) {
				// Get the comment text (description)
				String commentText = docComment.getCommentText();
				if ( commentText != null && !commentText.isBlank() ) {
					// Clean up the comment text
					String cleanedDescription = cleanDocCommentDescription( commentText );
					if ( !cleanedDescription.isBlank() ) {
						content.append( cleanedDescription );
						content.append( "\n\n" );
					}
				}

				// Add documentation annotations (@param, @return, etc.)
				List<BoxDocumentationAnnotation> annotations = docComment.getAnnotations();
				if ( annotations != null && !annotations.isEmpty() ) {
					content.append( formatDocumentationAnnotations( annotations ) );
				}
			}
		}

		Hover			hover			= new Hover();
		MarkupContent	markupContent	= new MarkupContent();
		markupContent.setKind( MarkupKind.MARKDOWN );
		markupContent.setValue( content.toString().trim() );
		hover.setContents( markupContent );

		return hover;
	}

	/**
	 * Build hover content from an indexed method (for cross-file lookups).
	 */
	private Hover buildHoverForIndexedMethod( IndexedMethod method ) {
		StringBuilder content = new StringBuilder();

		// Add function signature in code block
		content.append( "```boxlang\n" );
		content.append( buildSignatureFromIndexedMethod( method ) );
		content.append( "\n```\n\n" );

		// Add documentation if available
		if ( method.documentation() != null && !method.documentation().isBlank() ) {
			content.append( formatIndexedMethodDocumentation( method.documentation() ) );
		}

		Hover			hover			= new Hover();
		MarkupContent	markupContent	= new MarkupContent();
		markupContent.setKind( MarkupKind.MARKDOWN );
		markupContent.setValue( content.toString().trim() );
		hover.setContents( markupContent );

		return hover;
	}

	/**
	 * Build hover content for a variable.
	 */
	private Hover buildHoverForVariable( VariableInfo varInfo ) {
		StringBuilder content = new StringBuilder();

		// Add variable signature in code block
		content.append( "```boxlang\n" );
		content.append( buildVariableSignature( varInfo ) );
		content.append( "\n```\n\n" );

		// Add scope information
		content.append( "**Scope:** " ).append( varInfo.scope().getDisplayName() ).append( "\n\n" );

		// Add type information if available
		if ( varInfo.typeHint() != null && !varInfo.typeHint().isEmpty() ) {
			content.append( "**Type:** `" ).append( varInfo.typeHint() ).append( "`\n\n" );
		} else if ( varInfo.inferredType() != null && !varInfo.inferredType().isEmpty() ) {
			content.append( "**Type:** `" ).append( varInfo.inferredType() ).append( "` (inferred)\n\n" );
		}

		// Add declaration line if available
		if ( varInfo.declarationLine() > 0 ) {
			content.append( "**Declared:** line " ).append( varInfo.declarationLine() ).append( "\n\n" );
		}

		// For parameters, add required and default info
		if ( varInfo.scope() == VariableScope.ARGUMENTS ) {
			if ( varInfo.isRequired() ) {
				content.append( "**Required:** yes\n\n" );
			}
			if ( varInfo.defaultValue() != null && !varInfo.defaultValue().isEmpty() ) {
				content.append( "**Default:** `" ).append( varInfo.defaultValue() ).append( "`\n\n" );
			}
		}

		Hover			hover			= new Hover();
		MarkupContent	markupContent	= new MarkupContent();
		markupContent.setKind( MarkupKind.MARKDOWN );
		markupContent.setValue( content.toString().trim() );
		hover.setContents( markupContent );

		return hover;
	}

	/**
	 * Build a variable signature string.
	 */
	private String buildVariableSignature( VariableInfo varInfo ) {
		StringBuilder sig = new StringBuilder();

		// Add scope prefix
		sig.append( "(" ).append( varInfo.scope().getDisplayName() ).append( ") " );

		// Add type if available
		if ( varInfo.typeHint() != null && !varInfo.typeHint().isEmpty() ) {
			sig.append( varInfo.typeHint() ).append( " " );
		} else if ( varInfo.inferredType() != null && !varInfo.inferredType().isEmpty() ) {
			sig.append( varInfo.inferredType() ).append( " " );
		}

		// Add variable name
		sig.append( varInfo.name() );

		return sig.toString();
	}

	/**
	 * Build hover content for a function parameter declaration.
	 */
	private Hover buildHoverForParameter( BoxArgumentDeclaration argDecl ) {
		StringBuilder content = new StringBuilder();

		// Add parameter signature in code block
		content.append( "```boxlang\n" );

		// Build signature
		StringBuilder sig = new StringBuilder();
		sig.append( "(argument) " );

		// Check for required annotation
		boolean isRequired = argDecl.getAnnotations().stream()
		    .anyMatch( a -> a.getKey().getValue().equalsIgnoreCase( "required" ) );

		if ( isRequired ) {
			sig.append( "required " );
		}

		// Add type
		if ( argDecl.getType() != null ) {
			sig.append( argDecl.getType().toString() ).append( " " );
		}

		// Add name
		sig.append( argDecl.getName() );

		// Add default value
		if ( argDecl.getValue() != null ) {
			sig.append( " = " ).append( argDecl.getValue().getSourceText() );
		}

		content.append( sig.toString() );
		content.append( "\n```\n\n" );

		// Add scope info
		content.append( "**Scope:** arguments (function parameter)\n\n" );

		// Add type if available
		if ( argDecl.getType() != null ) {
			content.append( "**Type:** `" ).append( argDecl.getType().toString() ).append( "`\n\n" );
		}

		// Add required status
		if ( isRequired ) {
			content.append( "**Required:** yes\n\n" );
		} else {
			content.append( "**Required:** no\n\n" );
		}

		// Add default value if present
		if ( argDecl.getValue() != null ) {
			content.append( "**Default:** `" ).append( argDecl.getValue().getSourceText() ).append( "`\n\n" );
		}

		Hover			hover			= new Hover();
		MarkupContent	markupContent	= new MarkupContent();
		markupContent.setKind( MarkupKind.MARKDOWN );
		markupContent.setValue( content.toString().trim() );
		hover.setContents( markupContent );

		return hover;
	}

	/**
	 * Build hover content for a property declaration.
	 */
	private Hover buildHoverForProperty( BoxProperty property ) {
		StringBuilder content = new StringBuilder();

		// Extract property info from annotations
		String	name		= null;
		String	type		= null;
		String	defaultVal	= null;

		for ( var annotation : property.getAnnotations() ) {
			String key = annotation.getKey().getValue().toLowerCase();
			if ( key.equals( "name" ) && annotation.getValue() != null ) {
				name = annotation.getValue().getSourceText().replace( "\"", "" ).replace( "'", "" );
			} else if ( key.equals( "type" ) && annotation.getValue() != null ) {
				type = annotation.getValue().getSourceText().replace( "\"", "" ).replace( "'", "" );
			} else if ( key.equals( "default" ) && annotation.getValue() != null ) {
				defaultVal = annotation.getValue().getSourceText();
			}
		}

		if ( name == null ) {
			name = "property";
		}

		// Add property signature in code block
		content.append( "```boxlang\n" );
		content.append( "(property) " );
		if ( type != null ) {
			content.append( type ).append( " " );
		}
		content.append( name );
		content.append( "\n```\n\n" );

		// Add scope info
		content.append( "**Scope:** variables (instance property)\n\n" );

		// Add type if available
		if ( type != null ) {
			content.append( "**Type:** `" ).append( type ).append( "`\n\n" );
		}

		// Add default value if present
		if ( defaultVal != null ) {
			content.append( "**Default:** `" ).append( defaultVal ).append( "`\n\n" );
		}

		Hover			hover			= new Hover();
		MarkupContent	markupContent	= new MarkupContent();
		markupContent.setKind( MarkupKind.MARKDOWN );
		markupContent.setValue( content.toString().trim() );
		hover.setContents( markupContent );

		return hover;
	}

	/**
	 * Build hover content for a scope keyword (variables, local, this, arguments, etc.).
	 */
	private Hover buildHoverForScopeKeyword( VariableInfo scopeInfo ) {
		StringBuilder content = new StringBuilder();

		String scopeName = scopeInfo.name();

		content.append( "```boxlang\n" );
		content.append( scopeName ).append( " (scope)" );
		content.append( "\n```\n\n" );

		// Add description based on scope
		switch ( scopeName.toLowerCase() ) {
			case "variables" :
				content.append( "The **variables** scope contains instance-level variables for the component. " );
				content.append( "These are private to the component instance unless accessed via public methods.\n\n" );
				break;
			case "local" :
				content.append( "The **local** scope contains function-local variables. " );
				content.append( "Variables declared with `var` are automatically placed in this scope.\n\n" );
				break;
			case "this" :
				content.append( "The **this** scope provides access to public properties and methods of the component instance.\n\n" );
				break;
			case "arguments" :
				content.append( "The **arguments** scope contains all parameters passed to the current function.\n\n" );
				break;
			case "request" :
				content.append( "The **request** scope contains variables available for the duration of a single HTTP request.\n\n" );
				break;
			case "session" :
				content.append( "The **session** scope contains variables available for the duration of a user session.\n\n" );
				break;
			case "application" :
				content.append( "The **application** scope contains variables shared across all users of the application.\n\n" );
				break;
			case "server" :
				content.append( "The **server** scope contains variables shared across all applications on the server.\n\n" );
				break;
			case "cgi" :
				content.append( "The **cgi** scope contains CGI environment variables for the current request.\n\n" );
				break;
			case "form" :
				content.append( "The **form** scope contains form field values submitted via POST.\n\n" );
				break;
			case "url" :
				content.append( "The **url** scope contains URL query string parameters.\n\n" );
				break;
			case "cookie" :
				content.append( "The **cookie** scope contains HTTP cookie values.\n\n" );
				break;
			default :
				content.append( "Built-in scope.\n\n" );
		}

		Hover			hover			= new Hover();
		MarkupContent	markupContent	= new MarkupContent();
		markupContent.setKind( MarkupKind.MARKDOWN );
		markupContent.setValue( content.toString().trim() );
		hover.setContents( markupContent );

		return hover;
	}

	/**
	 * Build a function signature string from an IndexedMethod.
	 */
	private String buildSignatureFromIndexedMethod( IndexedMethod method ) {
		StringBuilder sig = new StringBuilder();

		// Add class name if available
		if ( method.containingClass() != null && !method.containingClass().isEmpty() ) {
			sig.append( "(" ).append( method.containingClass() ).append( ") " );
		}

		// Add access modifier
		if ( method.accessModifier() != null && !method.accessModifier().isEmpty() ) {
			sig.append( method.accessModifier() ).append( " " );
		}

		// Add return type
		if ( method.returnTypeHint() != null && !method.returnTypeHint().isEmpty() ) {
			sig.append( method.returnTypeHint() ).append( " " );
		}

		// Add function keyword and name
		sig.append( "function " ).append( method.name() );

		// Add parameters
		sig.append( "(" );
		List<String> paramStrings = new ArrayList<>();
		for ( var param : method.parameters() ) {
			StringBuilder paramStr = new StringBuilder();

			if ( param.required() ) {
				paramStr.append( "required " );
			}

			if ( param.typeHint() != null && !param.typeHint().isEmpty() ) {
				paramStr.append( param.typeHint() ).append( " " );
			}

			paramStr.append( param.name() );

			if ( param.defaultValue() != null ) {
				paramStr.append( " = " ).append( param.defaultValue() );
			}

			paramStrings.add( paramStr.toString() );
		}
		sig.append( String.join( ", ", paramStrings ) );
		sig.append( ")" );

		return sig.toString();
	}

	/**
	 * Format documentation from an indexed method.
	 * The documentation is stored as raw text with @tags.
	 */
	private String formatIndexedMethodDocumentation( String documentation ) {
		if ( documentation == null || documentation.isBlank() ) {
			return "";
		}

		StringBuilder	sb		= new StringBuilder();
		String[]		lines	= documentation.split( "\n" );

		// Separate description from tags
		StringBuilder	description			= new StringBuilder();
		List<String>	paramTags			= new ArrayList<>();
		String			returnTag			= null;
		List<String>	throwsTags			= new ArrayList<>();
		String			deprecatedTag		= null;
		String			sinceTag			= null;
		String			authorTag			= null;

		for ( String line : lines ) {
			String trimmed = line.trim();
			if ( trimmed.startsWith( "@param" ) ) {
				paramTags.add( trimmed.substring( 6 ).trim() );
			} else if ( trimmed.startsWith( "@return" ) || trimmed.startsWith( "@returns" ) ) {
				returnTag = trimmed.startsWith( "@returns" )
				    ? trimmed.substring( 8 ).trim()
				    : trimmed.substring( 7 ).trim();
			} else if ( trimmed.startsWith( "@throws" ) || trimmed.startsWith( "@throw" ) ) {
				throwsTags.add( trimmed.startsWith( "@throws" )
				    ? trimmed.substring( 7 ).trim()
				    : trimmed.substring( 6 ).trim() );
			} else if ( trimmed.startsWith( "@deprecated" ) ) {
				deprecatedTag = trimmed.substring( 11 ).trim();
			} else if ( trimmed.startsWith( "@since" ) ) {
				sinceTag = trimmed.substring( 6 ).trim();
			} else if ( trimmed.startsWith( "@author" ) ) {
				authorTag = trimmed.substring( 7 ).trim();
			} else if ( !trimmed.startsWith( "@" ) ) {
				if ( description.length() > 0 ) {
					description.append( "\n" );
				}
				description.append( trimmed );
			}
		}

		// Add description
		if ( description.length() > 0 ) {
			sb.append( description.toString().trim() );
			sb.append( "\n\n" );
		}

		// Add parameters
		if ( !paramTags.isEmpty() ) {
			sb.append( "**Parameters:**\n" );
			for ( String param : paramTags ) {
				sb.append( "- " ).append( param ).append( "\n" );
			}
			sb.append( "\n" );
		}

		// Add return
		if ( returnTag != null && !returnTag.isEmpty() ) {
			sb.append( "**@return** " ).append( returnTag ).append( "\n\n" );
		}

		// Add throws
		if ( !throwsTags.isEmpty() ) {
			sb.append( "**Throws:**\n" );
			for ( String throwsTag : throwsTags ) {
				sb.append( "- " ).append( throwsTag ).append( "\n" );
			}
			sb.append( "\n" );
		}

		// Add deprecated
		if ( deprecatedTag != null ) {
			sb.append( "**@deprecated** " ).append( deprecatedTag ).append( "\n\n" );
		}

		// Add since
		if ( sinceTag != null && !sinceTag.isEmpty() ) {
			sb.append( "**@since** " ).append( sinceTag ).append( "\n\n" );
		}

		// Add author
		if ( authorTag != null && !authorTag.isEmpty() ) {
			sb.append( "**@author** " ).append( authorTag ).append( "\n\n" );
		}

		return sb.toString();
	}

	/**
	 * Build a function signature string.
	 */
	private String buildFunctionSignature( BoxFunctionDeclaration fnDecl, String className ) {
		StringBuilder sig = new StringBuilder();

		// Add class name if available
		if ( className != null && !className.isEmpty() ) {
			sig.append( "(" ).append( className ).append( ") " );
		}

		// Add access modifier
		var accessModifier = fnDecl.getAccessModifier();
		if ( accessModifier != null ) {
			sig.append( accessModifier.name().toLowerCase() ).append( " " );
		}

		// Add return type
		var returnType = fnDecl.getType();
		if ( returnType != null ) {
			sig.append( returnType.toString() ).append( " " );
		}

		// Add function keyword and name
		sig.append( "function " ).append( fnDecl.getName() );

		// Add parameters
		sig.append( "(" );
		List<String> paramStrings = new ArrayList<>();
		for ( BoxNode child : fnDecl.getChildren() ) {
			if ( child instanceof BoxArgumentDeclaration arg ) {
				StringBuilder paramStr = new StringBuilder();

				// Check for required annotation
				boolean isRequired = arg.getAnnotations().stream()
				    .anyMatch( a -> a.getKey().getValue().equalsIgnoreCase( "required" ) );

				if ( isRequired ) {
					paramStr.append( "required " );
				}

				// Add type
				if ( arg.getType() != null ) {
					paramStr.append( arg.getType().toString() ).append( " " );
				}

				// Add name
				paramStr.append( arg.getName() );

				// Add default value
				if ( arg.getValue() != null ) {
					paramStr.append( " = " ).append( arg.getValue().getSourceText() );
				}

				paramStrings.add( paramStr.toString() );
			}
		}
		sig.append( String.join( ", ", paramStrings ) );
		sig.append( ")" );

		return sig.toString();
	}

	/**
	 * Clean up a documentation comment description by removing leading asterisks and extra whitespace.
	 */
	private String cleanDocCommentDescription( String commentText ) {
		if ( commentText == null ) {
			return "";
		}

		// Split into lines and clean each line
		String[] lines = commentText.split( "\n" );
		StringBuilder cleaned = new StringBuilder();

		for ( String line : lines ) {
			// Remove leading whitespace, asterisks, and trailing whitespace
			String trimmed = line.trim();
			if ( trimmed.startsWith( "*" ) ) {
				trimmed = trimmed.substring( 1 ).trim();
			}

			// Skip lines that start with @ (documentation tags)
			if ( trimmed.startsWith( "@" ) ) {
				continue;
			}

			// Skip empty lines at the start
			if ( cleaned.length() == 0 && trimmed.isEmpty() ) {
				continue;
			}

			if ( cleaned.length() > 0 ) {
				cleaned.append( "\n" );
			}
			cleaned.append( trimmed );
		}

		return cleaned.toString().trim();
	}

	/**
	 * Format documentation annotations (@param, @return, etc.) as markdown.
	 */
	private String formatDocumentationAnnotations( List<BoxDocumentationAnnotation> annotations ) {
		StringBuilder sb = new StringBuilder();

		// Group annotations by type
		List<BoxDocumentationAnnotation>	paramAnnotations		= new ArrayList<>();
		BoxDocumentationAnnotation			returnAnnotation		= null;
		List<BoxDocumentationAnnotation>	throwsAnnotations		= new ArrayList<>();
		BoxDocumentationAnnotation			deprecatedAnnotation	= null;
		BoxDocumentationAnnotation			sinceAnnotation			= null;
		BoxDocumentationAnnotation			authorAnnotation		= null;

		for ( BoxDocumentationAnnotation annotation : annotations ) {
			String key = annotation.getKey().getValue().toLowerCase();
			switch ( key ) {
				case "param" :
					paramAnnotations.add( annotation );
					break;
				case "return" :
				case "returns" :
					returnAnnotation = annotation;
					break;
				case "throws" :
				case "throw" :
					throwsAnnotations.add( annotation );
					break;
				case "deprecated" :
					deprecatedAnnotation = annotation;
					break;
				case "since" :
					sinceAnnotation = annotation;
					break;
				case "author" :
					authorAnnotation = annotation;
					break;
			}
		}

		// Format parameters
		if ( !paramAnnotations.isEmpty() ) {
			sb.append( "**Parameters:**\n" );
			for ( BoxDocumentationAnnotation param : paramAnnotations ) {
				String value = getAnnotationValue( param );
				sb.append( "- " ).append( value ).append( "\n" );
			}
			sb.append( "\n" );
		}

		// Format return
		if ( returnAnnotation != null ) {
			String value = getAnnotationValue( returnAnnotation );
			sb.append( "**@return** " ).append( value ).append( "\n\n" );
		}

		// Format throws
		if ( !throwsAnnotations.isEmpty() ) {
			sb.append( "**Throws:**\n" );
			for ( BoxDocumentationAnnotation throwsAnn : throwsAnnotations ) {
				String value = getAnnotationValue( throwsAnn );
				sb.append( "- " ).append( value ).append( "\n" );
			}
			sb.append( "\n" );
		}

		// Format deprecated
		if ( deprecatedAnnotation != null ) {
			String value = getAnnotationValue( deprecatedAnnotation );
			sb.append( "**@deprecated** " ).append( value != null ? value : "" ).append( "\n\n" );
		}

		// Format since
		if ( sinceAnnotation != null ) {
			String value = getAnnotationValue( sinceAnnotation );
			sb.append( "**@since** " ).append( value ).append( "\n\n" );
		}

		// Format author
		if ( authorAnnotation != null ) {
			String value = getAnnotationValue( authorAnnotation );
			sb.append( "**@author** " ).append( value ).append( "\n\n" );
		}

		return sb.toString();
	}

	/**
	 * Get the string value from a documentation annotation.
	 */
	private String getAnnotationValue( BoxDocumentationAnnotation annotation ) {
		if ( annotation.getValue() == null ) {
			return "";
		}
		return annotation.getValue().getSourceText();
	}

	/**
	 * Extract the class name from a file URI.
	 */
	private String getClassNameFromUri( URI docURI ) {
		if ( docURI == null ) {
			return null;
		}
		Path	path		= Paths.get( docURI );
		String	fileName	= path.getFileName().toString();
		if ( fileName.contains( "." ) ) {
			return fileName.substring( 0, fileName.lastIndexOf( "." ) );
		}
		return fileName;
	}

	/**
	 * Extract the class name from a BoxNew expression.
	 */
	private String extractClassNameFromNew( BoxNew newExpr ) {
		BoxNode expression = newExpr.getExpression();

		if ( expression instanceof BoxFQN fqn ) {
			return extractClassNameFromFQN( fqn );
		}

		// Try to get the source text as a fallback
		if ( expression != null ) {
			String sourceText = expression.getSourceText();
			if ( sourceText != null ) {
				sourceText = sourceText.trim();
				int lastDot = sourceText.lastIndexOf( '.' );
				int lastColon = sourceText.lastIndexOf( ':' );
				int lastSeparator = Math.max( lastDot, lastColon );

				if ( lastSeparator >= 0 && lastSeparator < sourceText.length() - 1 ) {
					return sourceText.substring( lastSeparator + 1 );
				}
				return sourceText;
			}
		}

		return null;
	}

	/**
	 * Extract the class name from a BoxFQN node.
	 */
	private String extractClassNameFromFQN( BoxFQN fqn ) {
		String fullPath = fqn.getValue();
		if ( fullPath == null || fullPath.isEmpty() ) {
			return null;
		}

		// Get just the class name (last part after any dots or colons)
		int lastDot = fullPath.lastIndexOf( '.' );
		int lastColon = fullPath.lastIndexOf( ':' );
		int lastSeparator = Math.max( lastDot, lastColon );

		if ( lastSeparator >= 0 && lastSeparator < fullPath.length() - 1 ) {
			return fullPath.substring( lastSeparator + 1 );
		}
		return fullPath;
	}

	/**
	 * Build hover content for a class or interface.
	 */
	private Hover buildHoverForClass( IndexedClass indexedClass ) {
		StringBuilder content = new StringBuilder();

		// Add class name with type indicator
		content.append( "**" ).append( indexedClass.name() ).append( "** " );
		content.append( "(" ).append( indexedClass.isInterface() ? "interface" : "class" ).append( ")\n\n" );

		// Add class/interface declaration in code block
		content.append( "```boxlang\n" );
		content.append( buildClassSignature( indexedClass ) );
		content.append( "\n```\n\n" );

		// Add class documentation if available
		if ( indexedClass.documentation() != null && !indexedClass.documentation().isBlank() ) {
			content.append( formatClassDocumentation( indexedClass.documentation() ) );
		}

		// Add inheritance information
		if ( indexedClass.hasParent() ) {
			content.append( "**Extends:** `" ).append( indexedClass.extendsClass() ).append( "`\n\n" );
		}

		if ( indexedClass.hasInterfaces() ) {
			String interfaces = String.join( "`, `", indexedClass.implementsInterfaces() );
			content.append( "**Implements:** `" ).append( interfaces ).append( "`\n\n" );
		}

		// Add file location
		if ( indexedClass.fileUri() != null ) {
			String fileName = getFileNameFromUri( indexedClass.fileUri() );
			content.append( "**File:** `" ).append( fileName ).append( "`" );
		}

		Hover			hover			= new Hover();
		MarkupContent	markupContent	= new MarkupContent();
		markupContent.setKind( MarkupKind.MARKDOWN );
		markupContent.setValue( content.toString().trim() );
		hover.setContents( markupContent );

		return hover;
	}

	/**
	 * Format class documentation for display in hover.
	 */
	private String formatClassDocumentation( String documentation ) {
		if ( documentation == null || documentation.isBlank() ) {
			return "";
		}

		StringBuilder	sb		= new StringBuilder();
		String[]		lines	= documentation.split( "\n" );

		// Separate description from tags
		StringBuilder	description		= new StringBuilder();
		String			authorTag		= null;
		String			sinceTag		= null;
		String			deprecatedTag	= null;

		for ( String line : lines ) {
			String trimmed = line.trim();
			if ( trimmed.startsWith( "@author" ) ) {
				authorTag = trimmed.substring( 7 ).trim();
			} else if ( trimmed.startsWith( "@since" ) ) {
				sinceTag = trimmed.substring( 6 ).trim();
			} else if ( trimmed.startsWith( "@deprecated" ) ) {
				deprecatedTag = trimmed.substring( 11 ).trim();
			} else if ( !trimmed.startsWith( "@" ) ) {
				if ( description.length() > 0 ) {
					description.append( "\n" );
				}
				description.append( trimmed );
			}
		}

		// Add description
		if ( description.length() > 0 ) {
			sb.append( description.toString().trim() );
			sb.append( "\n\n" );
		}

		// Add deprecated
		if ( deprecatedTag != null ) {
			sb.append( "**@deprecated** " ).append( deprecatedTag ).append( "\n\n" );
		}

		// Add author
		if ( authorTag != null && !authorTag.isEmpty() ) {
			sb.append( "**@author** " ).append( authorTag ).append( "\n\n" );
		}

		// Add since
		if ( sinceTag != null && !sinceTag.isEmpty() ) {
			sb.append( "**@since** " ).append( sinceTag ).append( "\n\n" );
		}

		return sb.toString();
	}

	/**
	 * Build a class/interface signature string.
	 */
	private String buildClassSignature( IndexedClass indexedClass ) {
		StringBuilder sig = new StringBuilder();

		// Add modifiers if any
		if ( indexedClass.modifiers() != null && !indexedClass.modifiers().isEmpty() ) {
			sig.append( String.join( " ", indexedClass.modifiers() ) ).append( " " );
		}

		// Add class or interface keyword
		if ( indexedClass.isInterface() ) {
			sig.append( "interface " );
		} else {
			sig.append( "class " );
		}

		// Add class name
		sig.append( indexedClass.name() );

		// Add extends
		if ( indexedClass.hasParent() ) {
			sig.append( " extends " ).append( indexedClass.extendsClass() );
		}

		// Add implements
		if ( indexedClass.hasInterfaces() ) {
			sig.append( " implements " ).append( String.join( ", ", indexedClass.implementsInterfaces() ) );
		}

		return sig.toString();
	}

	/**
	 * Extract the filename from a file URI string.
	 */
	private String getFileNameFromUri( String fileUri ) {
		if ( fileUri == null || fileUri.isEmpty() ) {
			return null;
		}
		try {
			Path path = Paths.get( new URI( fileUri ) );
			return path.getFileName().toString();
		} catch ( Exception e ) {
			// Fallback: try to extract filename from URI string
			int lastSlash = Math.max( fileUri.lastIndexOf( '/' ), fileUri.lastIndexOf( '\\' ) );
			if ( lastSlash >= 0 && lastSlash < fileUri.length() - 1 ) {
				return fileUri.substring( lastSlash + 1 );
			}
			return fileUri;
		}
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
