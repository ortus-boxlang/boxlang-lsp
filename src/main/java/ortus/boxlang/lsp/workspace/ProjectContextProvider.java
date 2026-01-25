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

import ortus.boxlang.compiler.ast.BoxClass;
import ortus.boxlang.compiler.ast.BoxInterface;
import ortus.boxlang.compiler.ast.BoxNode;
import ortus.boxlang.compiler.ast.IBoxDocumentableNode;
import ortus.boxlang.compiler.ast.Point;
import ortus.boxlang.compiler.ast.comment.BoxDocComment;
import ortus.boxlang.compiler.ast.expression.BoxDotAccess;
import ortus.boxlang.compiler.ast.expression.BoxFQN;
import ortus.boxlang.compiler.ast.expression.BoxFunctionInvocation;
import ortus.boxlang.compiler.ast.expression.BoxIdentifier;
import ortus.boxlang.compiler.ast.expression.BoxMethodInvocation;
import ortus.boxlang.compiler.ast.expression.BoxNew;
import ortus.boxlang.compiler.ast.expression.BoxScope;
import ortus.boxlang.compiler.ast.expression.BoxStringLiteral;
import ortus.boxlang.compiler.ast.statement.BoxAnnotation;
import ortus.boxlang.compiler.ast.statement.BoxArgumentDeclaration;
import ortus.boxlang.compiler.ast.statement.BoxImport;
import ortus.boxlang.compiler.ast.statement.BoxReturnType;
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
import ortus.boxlang.lsp.workspace.index.IndexedProperty;
import ortus.boxlang.lsp.workspace.index.ProjectIndex;
import ortus.boxlang.lsp.workspace.visitors.FindDefinitionTargetVisitor;
import ortus.boxlang.lsp.workspace.visitors.FindHoverTargetVisitor;
import ortus.boxlang.lsp.workspace.visitors.FindReferenceTargetVisitor;
import ortus.boxlang.lsp.workspace.visitors.FindSignatureHelpTargetVisitor;
import ortus.boxlang.lsp.workspace.visitors.VariableScopeCollectorVisitor;
import ortus.boxlang.lsp.workspace.visitors.VariableScopeCollectorVisitor.VariableInfo;
import ortus.boxlang.lsp.workspace.visitors.VariableScopeCollectorVisitor.VariableScope;
import ortus.boxlang.lsp.workspace.visitors.VariableDefinitionResolver;
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

	static ProjectContextProvider				instance;
	private List<WorkspaceFolder>				workspaceFolders			= new ArrayList<WorkspaceFolder>();
	private LanguageClient						client;
	private Map<URI, FileParseResult>			parsedFiles					= new ConcurrentHashMap<URI, FileParseResult>();
	private Map<URI, FileParseResult>			openDocuments				= new ConcurrentHashMap<URI, FileParseResult>();
	private Map<URI, DocumentModel>				documentModels				= new ConcurrentHashMap<URI, DocumentModel>();
	private List<FunctionDefinition>			functionDefinitions			= new ArrayList<FunctionDefinition>();
	private UserSettings						userSettings				= new UserSettings();
	private long								WorkspaceDiagnosticReportId	= 1;
	private List<DiagnosticReport>				cachedDiagnosticReports		= new CopyOnWriteArrayList<DiagnosticReport>();

	private boolean								shouldPublishDiagnostics	= false;
	private final AtomicBoolean					workspaceParseRunning		= new AtomicBoolean( false );
	private ProjectIndex						projectIndex;
	private final DebouncedDocumentProcessor	documentProcessor			= new DebouncedDocumentProcessor( 300 );

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
	 * 
	 * @return Optional containing the FileParseResult if found
	 */
	public Optional<FileParseResult> getLatestFileParseResultPublic( URI docUri ) {
		return getLatestFileParseResult( docUri );
	}

	/**
	 * Gets the current version of a document.
	 *
	 * @param docUri The document URI
	 * 
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
	 * 
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

	/**
	 * Find all references to the symbol at the given position.
	 * Searches across all open documents and parsed files.
	 *
	 * @param docURI             The document URI
	 * @param pos                The cursor position
	 * @param includeDeclaration Whether to include the declaration itself as a reference
	 *
	 * @return List of locations where the symbol is referenced
	 */
	public List<Location> findReferences( URI docURI, Position pos, boolean includeDeclaration ) {
		return findReferenceTarget( docURI, pos )
		    .map( node -> {
			    List<Location> references = new ArrayList<>();

			    if ( node instanceof BoxFunctionDeclaration fnDecl ) {
				    // Find references to this function/method
				    references.addAll( findFunctionReferences( fnDecl.getName(), docURI, includeDeclaration, fnDecl ) );
			    } else if ( node instanceof BoxClass classNode ) {
				    // Find references to this class
				    String className = extractClassNameFromUri( docURI );
				    references.addAll( findClassReferences( className, includeDeclaration, classNode, docURI ) );
			    } else if ( node instanceof BoxInterface interfaceNode ) {
				    // Find references to this interface
				    String interfaceName = extractClassNameFromUri( docURI );
				    references.addAll( findInterfaceReferences( interfaceName, includeDeclaration, interfaceNode, docURI ) );
			    } else if ( node instanceof BoxProperty propertyNode ) {
				    // Find references to this property
				    String propertyName = extractPropertyName( propertyNode );
				    references.addAll( findPropertyReferences( propertyName, docURI, includeDeclaration, propertyNode ) );
			    } else if ( node instanceof BoxNew newExpr ) {
				    // Find references from a new expression - user is on the class name
				    String className = extractClassNameFromNew( newExpr );
				    if ( className != null ) {
					    references.addAll( findClassReferences( className, includeDeclaration, null, docURI ) );
				    }
			    } else if ( node instanceof BoxIdentifier identifier ) {
				    // Could be a local variable or parameter
				    references.addAll( findVariableReferences( identifier, docURI, includeDeclaration ) );
			    } else if ( node instanceof BoxArgumentDeclaration argDecl ) {
				    // Find references to this parameter
				    references.addAll( findParameterReferences( argDecl, docURI, includeDeclaration ) );
			    } else if ( node instanceof BoxFunctionInvocation fnInvocation ) {
				    // User is on a function call - find all references to that function
				    references.addAll( findFunctionReferences( fnInvocation.getName(), docURI, includeDeclaration, null ) );
			    } else if ( node instanceof BoxMethodInvocation methodInvocation ) {
				    // User is on a method call - find all references to that method
				    references.addAll( findMethodInvocationReferences( methodInvocation, docURI, includeDeclaration ) );
			    }

			    return references;
		    } )
		    .orElseGet( () -> new ArrayList<Location>() );
	}

	/**
	 * Find all references to a function/method by name.
	 *
	 * @param functionName       The function name to search for
	 * @param currentDocURI      The current document URI
	 * @param includeDeclaration Whether to include the declaration
	 * @param declarationNode    The declaration node (if finding from declaration)
	 *
	 * @return List of reference locations
	 */
	private List<Location> findFunctionReferences( String functionName, URI currentDocURI, boolean includeDeclaration,
	    BoxFunctionDeclaration declarationNode ) {
		List<Location>				references	= new ArrayList<>();

		// Search across all open documents and parsed files
		Map<URI, FileParseResult>	allFiles	= new HashMap<>();
		allFiles.putAll( openDocuments );
		allFiles.putAll( parsedFiles );

		for ( Map.Entry<URI, FileParseResult> entry : allFiles.entrySet() ) {
			URI					fileUri	= entry.getKey();
			Optional<BoxNode>	rootOpt	= entry.getValue().findAstRoot();

			if ( rootOpt.isEmpty() ) {
				continue;
			}

			BoxNode						root		= rootOpt.get();

			// Find all function invocations with matching name
			List<BoxFunctionInvocation>	invocations	= root.getDescendantsOfType(
			    BoxFunctionInvocation.class,
			    n -> n.getName().equalsIgnoreCase( functionName ) );

			for ( BoxFunctionInvocation inv : invocations ) {
				references.add( createLocationFromNode( inv, fileUri, inv.getName().length() ) );
			}

			// Also find method invocations with matching name (for this.methodName() calls)
			List<BoxMethodInvocation> methodInvocations = root.getDescendantsOfType(
			    BoxMethodInvocation.class,
			    n -> n.getName().getSourceText().equalsIgnoreCase( functionName ) );

			for ( BoxMethodInvocation inv : methodInvocations ) {
				references.add( createLocationFromMethodInvocation( inv, fileUri ) );
			}
		}

		// Include declaration if requested
		if ( includeDeclaration && declarationNode != null ) {
			references.add( createLocationFromNode( declarationNode, currentDocURI, declarationNode.getName().length() ) );
		}

		return references;
	}

	/**
	 * Find all references to a class by name.
	 *
	 * @param className          The class name to search for
	 * @param includeDeclaration Whether to include the declaration
	 * @param declarationNode    The declaration node (if finding from declaration)
	 * @param currentDocURI      The current document URI
	 *
	 * @return List of reference locations
	 */
	private List<Location> findClassReferences( String className, boolean includeDeclaration, BoxClass declarationNode,
	    URI currentDocURI ) {
		List<Location> references = new ArrayList<>();

		if ( className == null || className.isEmpty() ) {
			return references;
		}

		// Search across all open documents and parsed files
		Map<URI, FileParseResult> allFiles = new HashMap<>();
		allFiles.putAll( openDocuments );
		allFiles.putAll( parsedFiles );

		for ( Map.Entry<URI, FileParseResult> entry : allFiles.entrySet() ) {
			URI					fileUri	= entry.getKey();
			Optional<BoxNode>	rootOpt	= entry.getValue().findAstRoot();

			if ( rootOpt.isEmpty() ) {
				continue;
			}

			BoxNode			root			= rootOpt.get();

			// Find new ClassName() expressions
			List<BoxNew>	newExpressions	= root.getDescendantsOfType(
			    BoxNew.class,
			    n -> {
				    String newClassName = extractClassNameFromNew( n );
				    return newClassName != null && newClassName.equalsIgnoreCase( className );
			    } );

			for ( BoxNew newExpr : newExpressions ) {
				references.add( createLocationFromNewExpression( newExpr, fileUri ) );
			}

			// Find extends="ClassName" annotations
			List<BoxAnnotation> extendsAnnotations = root.getDescendantsOfType(
			    BoxAnnotation.class,
			    n -> {
				    String key = n.getKey().getValue().toLowerCase();
				    if ( !key.equals( "extends" ) ) {
					    return false;
				    }
				    String value = extractAnnotationValueForRefs( n );
				    return value != null && value.equalsIgnoreCase( className );
			    } );

			for ( BoxAnnotation annotation : extendsAnnotations ) {
				references.add( createLocationFromAnnotationValue( annotation, fileUri ) );
			}

			// Find implements="ClassName" annotations (interfaces can be implemented)
			List<BoxAnnotation> implementsAnnotations = root.getDescendantsOfType(
			    BoxAnnotation.class,
			    n -> {
				    String key = n.getKey().getValue().toLowerCase();
				    if ( !key.equals( "implements" ) ) {
					    return false;
				    }
				    String value = extractAnnotationValueForRefs( n );
				    if ( value == null ) {
					    return false;
				    }
				    // Implements can have comma-separated values
				    for ( String impl : value.split( "," ) ) {
					    if ( impl.trim().equalsIgnoreCase( className ) ) {
						    return true;
					    }
				    }
				    return false;
			    } );

			for ( BoxAnnotation annotation : implementsAnnotations ) {
				references.add( createLocationFromAnnotationValue( annotation, fileUri ) );
			}

			// Find type hints (return types, parameter types)
			findTypeHintReferences( root, className, fileUri, references );
		}

		// Include declaration if requested
		if ( includeDeclaration && declarationNode != null ) {
			references.add( createLocationFromNode( declarationNode, currentDocURI, className.length() ) );
		}

		return references;
	}

	/**
	 * Find all references to an interface by name.
	 *
	 * @param interfaceName      The interface name to search for
	 * @param includeDeclaration Whether to include the declaration
	 * @param declarationNode    The declaration node
	 * @param currentDocURI      The current document URI
	 *
	 * @return List of reference locations
	 */
	private List<Location> findInterfaceReferences( String interfaceName, boolean includeDeclaration,
	    BoxInterface declarationNode, URI currentDocURI ) {
		List<Location> references = new ArrayList<>();

		if ( interfaceName == null || interfaceName.isEmpty() ) {
			return references;
		}

		// Search across all files
		Map<URI, FileParseResult> allFiles = new HashMap<>();
		allFiles.putAll( openDocuments );
		allFiles.putAll( parsedFiles );

		for ( Map.Entry<URI, FileParseResult> entry : allFiles.entrySet() ) {
			URI					fileUri	= entry.getKey();
			Optional<BoxNode>	rootOpt	= entry.getValue().findAstRoot();

			if ( rootOpt.isEmpty() ) {
				continue;
			}

			BoxNode				root					= rootOpt.get();

			// Find implements="InterfaceName" annotations
			List<BoxAnnotation>	implementsAnnotations	= root.getDescendantsOfType(
			    BoxAnnotation.class,
			    n -> {
				    String key = n.getKey().getValue().toLowerCase();
				    if ( !key.equals( "implements" ) ) {
					    return false;
				    }
				    String value = extractAnnotationValueForRefs( n );
				    if ( value == null ) {
					    return false;
				    }
				    for ( String impl : value.split( "," ) ) {
					    if ( impl.trim().equalsIgnoreCase( interfaceName ) ) {
						    return true;
					    }
				    }
				    return false;
			    } );

			for ( BoxAnnotation annotation : implementsAnnotations ) {
				references.add( createLocationFromAnnotationValue( annotation, fileUri ) );
			}

			// Find type hints using the interface
			findTypeHintReferences( root, interfaceName, fileUri, references );
		}

		// Include declaration if requested
		if ( includeDeclaration && declarationNode != null ) {
			references.add( createLocationFromNode( declarationNode, currentDocURI, interfaceName.length() ) );
		}

		return references;
	}

	/**
	 * Find type hint references for a class/interface name in a file.
	 */
	private void findTypeHintReferences( BoxNode root, String typeName, URI fileUri, List<Location> references ) {
		// Find return type hints
		List<BoxFunctionDeclaration> functions = root.getDescendantsOfType( BoxFunctionDeclaration.class );

		for ( BoxFunctionDeclaration fn : functions ) {
			// Check return type via getType() -> BoxReturnType
			BoxReturnType returnType = fn.getType();
			if ( returnType != null ) {
				String fqn = returnType.getFqn();
				if ( fqn != null && fqn.equalsIgnoreCase( typeName ) ) {
					references.add( createLocationFromNode( returnType, fileUri, typeName.length() ) );
				}
			}

			// Check parameter types by iterating children
			for ( BoxNode child : fn.getChildren() ) {
				if ( child instanceof BoxArgumentDeclaration arg ) {
					String argType = arg.getType();
					if ( argType != null && argType.equalsIgnoreCase( typeName ) ) {
						references.add( createLocationFromArgumentType( arg, fileUri, typeName.length() ) );
					}
				}
			}
		}
	}

	/**
	 * Find all references to a property by name within the current file.
	 *
	 * @param propertyName       The property name to search for
	 * @param currentDocURI      The current document URI
	 * @param includeDeclaration Whether to include the declaration
	 * @param declarationNode    The declaration node
	 *
	 * @return List of reference locations
	 */
	private List<Location> findPropertyReferences( String propertyName, URI currentDocURI, boolean includeDeclaration,
	    BoxProperty declarationNode ) {
		List<Location> references = new ArrayList<>();

		if ( propertyName == null || propertyName.isEmpty() ) {
			return references;
		}

		// Get the current file's AST
		Optional<BoxNode> rootOpt = getLatestFileParseResult( currentDocURI )
		    .flatMap( fpr -> fpr.findAstRoot() );

		if ( rootOpt.isEmpty() ) {
			return references;
		}

		BoxNode				root		= rootOpt.get();

		// Find variables.propertyName and this.propertyName access
		List<BoxDotAccess>	dotAccesses	= root.getDescendantsOfType( BoxDotAccess.class );

		for ( BoxDotAccess dotAccess : dotAccesses ) {
			BoxNode	context		= dotAccess.getContext();
			// 'this' and 'variables' can be represented as BoxScope or BoxIdentifier
			String	scopeName	= null;
			if ( context instanceof BoxScope scope ) {
				scopeName = scope.getName().toLowerCase();
			} else if ( context instanceof BoxIdentifier scopeId ) {
				scopeName = scopeId.getName().toLowerCase();
			}

			if ( scopeName != null && ( scopeName.equals( "variables" ) || scopeName.equals( "this" ) ) ) {
				BoxNode access = dotAccess.getAccess();
				if ( access instanceof BoxIdentifier propId && propId.getName().equalsIgnoreCase( propertyName ) ) {
					references.add( createLocationFromNode( access, currentDocURI, propertyName.length() ) );
				}
			}
		}

		// Include declaration if requested
		if ( includeDeclaration && declarationNode != null ) {
			references.add( createLocationFromProperty( declarationNode, currentDocURI ) );
		}

		return references;
	}

	/**
	 * Find all references to a local variable within its scope.
	 *
	 * @param identifier         The variable identifier
	 * @param currentDocURI      The current document URI
	 * @param includeDeclaration Whether to include the declaration
	 *
	 * @return List of reference locations
	 */
	private List<Location> findVariableReferences( BoxIdentifier identifier, URI currentDocURI, boolean includeDeclaration ) {
		List<Location>		references	= new ArrayList<>();
		String				varName		= identifier.getName();

		// Get the current file's AST
		Optional<BoxNode>	rootOpt		= getLatestFileParseResult( currentDocURI )
		    .flatMap( fpr -> fpr.findAstRoot() );

		if ( rootOpt.isEmpty() ) {
			return references;
		}

		BoxNode	root				= rootOpt.get();

		// Find the containing function to scope the search
		BoxNode	containingFunction	= findContainingFunction( identifier );

		if ( containingFunction != null ) {
			// Search only within the containing function
			List<BoxIdentifier> identifiers = containingFunction.getDescendantsOfType(
			    BoxIdentifier.class,
			    n -> n.getName().equalsIgnoreCase( varName ) );

			for ( BoxIdentifier id : identifiers ) {
				references.add( createLocationFromNode( id, currentDocURI, varName.length() ) );
			}
		} else {
			// Search entire file for class-level variables
			List<BoxIdentifier> identifiers = root.getDescendantsOfType(
			    BoxIdentifier.class,
			    n -> n.getName().equalsIgnoreCase( varName ) );

			for ( BoxIdentifier id : identifiers ) {
				references.add( createLocationFromNode( id, currentDocURI, varName.length() ) );
			}
		}

		return references;
	}

	/**
	 * Find all references to a function parameter within its function.
	 *
	 * @param argDecl            The argument declaration
	 * @param currentDocURI      The current document URI
	 * @param includeDeclaration Whether to include the declaration
	 *
	 * @return List of reference locations
	 */
	private List<Location> findParameterReferences( BoxArgumentDeclaration argDecl, URI currentDocURI, boolean includeDeclaration ) {
		List<Location>	references			= new ArrayList<>();
		String			paramName			= argDecl.getName();

		// Find the containing function
		BoxNode			containingFunction	= findContainingFunction( argDecl );

		if ( containingFunction == null ) {
			return references;
		}

		// Search for all identifiers with the parameter name within the function
		List<BoxIdentifier> identifiers = containingFunction.getDescendantsOfType(
		    BoxIdentifier.class,
		    n -> n.getName().equalsIgnoreCase( paramName ) );

		for ( BoxIdentifier id : identifiers ) {
			references.add( createLocationFromNode( id, currentDocURI, paramName.length() ) );
		}

		// Include declaration if requested
		if ( includeDeclaration ) {
			references.add( createLocationFromNode( argDecl, currentDocURI, paramName.length() ) );
		}

		return references;
	}

	/**
	 * Find references from a method invocation (user clicked on a method call).
	 *
	 * @param methodInvocation   The method invocation node
	 * @param currentDocURI      The current document URI
	 * @param includeDeclaration Whether to include the declaration
	 *
	 * @return List of reference locations
	 */
	private List<Location> findMethodInvocationReferences( BoxMethodInvocation methodInvocation, URI currentDocURI,
	    boolean includeDeclaration ) {
		List<Location>				references	= new ArrayList<>();
		String						methodName	= methodInvocation.getName().getSourceText();

		// Search across all files for method invocations with this name
		Map<URI, FileParseResult>	allFiles	= new HashMap<>();
		allFiles.putAll( openDocuments );
		allFiles.putAll( parsedFiles );

		for ( Map.Entry<URI, FileParseResult> entry : allFiles.entrySet() ) {
			URI					fileUri	= entry.getKey();
			Optional<BoxNode>	rootOpt	= entry.getValue().findAstRoot();

			if ( rootOpt.isEmpty() ) {
				continue;
			}

			BoxNode						root		= rootOpt.get();

			// Find all method invocations with matching name
			List<BoxMethodInvocation>	invocations	= root.getDescendantsOfType(
			    BoxMethodInvocation.class,
			    n -> n.getName().getSourceText().equalsIgnoreCase( methodName ) );

			for ( BoxMethodInvocation inv : invocations ) {
				references.add( createLocationFromMethodInvocation( inv, fileUri ) );
			}
		}

		return references;
	}

	/**
	 * Find the containing function for a node.
	 */
	private BoxNode findContainingFunction( BoxNode node ) {
		BoxNode current = node.getParent();
		while ( current != null ) {
			if ( current instanceof BoxFunctionDeclaration ) {
				return current;
			}
			current = current.getParent();
		}
		return null;
	}

	/**
	 * Extract class name from URI (file name without extension).
	 */
	private String extractClassNameFromUri( URI uri ) {
		String path = uri.getPath();
		if ( path == null ) {
			return null;
		}
		int		lastSlash	= path.lastIndexOf( '/' );
		String	fileName	= lastSlash >= 0 ? path.substring( lastSlash + 1 ) : path;
		int		dotIndex	= fileName.lastIndexOf( '.' );
		return dotIndex > 0 ? fileName.substring( 0, dotIndex ) : fileName;
	}

	/**
	 * Extract annotation value as string (for Find References).
	 */
	private String extractAnnotationValueForRefs( BoxAnnotation annotation ) {
		BoxNode value = annotation.getValue();
		if ( value instanceof BoxStringLiteral stringLiteral ) {
			return stringLiteral.getValue();
		} else if ( value instanceof BoxFQN fqn ) {
			return fqn.getValue();
		} else if ( value instanceof BoxIdentifier identifier ) {
			return identifier.getName();
		}
		return null;
	}

	/**
	 * Create a Location from a BoxNode.
	 */
	private Location createLocationFromNode( BoxNode node, URI fileUri, int nameLength ) {
		Location location = new Location();
		location.setUri( fileUri.toString() );

		ortus.boxlang.compiler.ast.Position pos = node.getPosition();
		if ( pos != null ) {
			int	startLine	= pos.getStart().getLine() - 1; // Convert to 0-indexed
			int	startCol	= pos.getStart().getColumn();
			location.setRange( new Range(
			    new Position( startLine, startCol ),
			    new Position( startLine, startCol + nameLength ) ) );
		}

		return location;
	}

	/**
	 * Create a Location from a BoxNew expression (pointing to class name).
	 */
	private Location createLocationFromNewExpression( BoxNew newExpr, URI fileUri ) {
		Location location = new Location();
		location.setUri( fileUri.toString() );

		BoxNode expression = newExpr.getExpression();
		if ( expression instanceof BoxFQN fqn ) {
			ortus.boxlang.compiler.ast.Position pos = fqn.getPosition();
			if ( pos != null ) {
				int		startLine	= pos.getStart().getLine() - 1;
				int		startCol	= pos.getStart().getColumn();
				String	className	= fqn.getValue();
				// Get simple name if FQN
				if ( className.contains( "." ) ) {
					className = className.substring( className.lastIndexOf( "." ) + 1 );
				}
				location.setRange( new Range(
				    new Position( startLine, startCol ),
				    new Position( startLine, startCol + className.length() ) ) );
			}
		}

		return location;
	}

	/**
	 * Create a Location from a BoxMethodInvocation.
	 */
	private Location createLocationFromMethodInvocation( BoxMethodInvocation inv, URI fileUri ) {
		Location location = new Location();
		location.setUri( fileUri.toString() );

		BoxNode nameNode = inv.getName();
		if ( nameNode != null ) {
			ortus.boxlang.compiler.ast.Position pos = nameNode.getPosition();
			if ( pos != null ) {
				int	startLine	= pos.getStart().getLine() - 1;
				int	startCol	= pos.getStart().getColumn();
				location.setRange( new Range(
				    new Position( startLine, startCol ),
				    new Position( startLine, startCol + nameNode.getSourceText().length() ) ) );
			}
		}

		return location;
	}

	/**
	 * Create a Location from an annotation value.
	 */
	private Location createLocationFromAnnotationValue( BoxAnnotation annotation, URI fileUri ) {
		Location location = new Location();
		location.setUri( fileUri.toString() );

		BoxNode value = annotation.getValue();
		if ( value != null ) {
			ortus.boxlang.compiler.ast.Position pos = value.getPosition();
			if ( pos != null ) {
				int	startLine	= pos.getStart().getLine() - 1;
				int	startCol	= pos.getStart().getColumn();
				location.setRange( new Range(
				    new Position( startLine, startCol ),
				    new Position( pos.getEnd().getLine() - 1, pos.getEnd().getColumn() ) ) );
			}
		}

		return location;
	}

	/**
	 * Create a Location from a return type node.
	 */
	private Location createLocationFromReturnType( BoxReturnType returnType, URI fileUri, int nameLength ) {
		Location location = new Location();
		location.setUri( fileUri.toString() );

		ortus.boxlang.compiler.ast.Position pos = returnType.getPosition();
		if ( pos != null ) {
			int	startLine	= pos.getStart().getLine() - 1;
			int	startCol	= pos.getStart().getColumn();
			location.setRange( new Range(
			    new Position( startLine, startCol ),
			    new Position( startLine, startCol + nameLength ) ) );
		}

		return location;
	}

	/**
	 * Create a Location from an argument declaration (for type reference).
	 */
	private Location createLocationFromArgumentType( BoxArgumentDeclaration arg, URI fileUri, int typeLength ) {
		Location location = new Location();
		location.setUri( fileUri.toString() );

		ortus.boxlang.compiler.ast.Position pos = arg.getPosition();
		if ( pos != null ) {
			int	startLine	= pos.getStart().getLine() - 1;
			int	startCol	= pos.getStart().getColumn();
			location.setRange( new Range(
			    new Position( startLine, startCol ),
			    new Position( startLine, startCol + typeLength ) ) );
		}

		return location;
	}

	/**
	 * Create a Location from a property declaration.
	 */
	private Location createLocationFromProperty( BoxProperty property, URI fileUri ) {
		Location location = new Location();
		location.setUri( fileUri.toString() );

		ortus.boxlang.compiler.ast.Position pos = property.getPosition();
		if ( pos != null ) {
			int	startLine	= pos.getStart().getLine() - 1;
			int	startCol	= pos.getStart().getColumn();
			location.setRange( new Range(
			    new Position( startLine, startCol ),
			    new Position( pos.getEnd().getLine() - 1, pos.getEnd().getColumn() ) ) );
		}

		return location;
	}

	/**
	 * Create a list containing a single Location from a BoxNode.
	 * Used when we want to return the node's own location (for "Find References" on declarations).
	 */
	private List<Location> createLocationList( BoxNode node, URI fileUri ) {
		List<Location>	locations	= new ArrayList<>();
		Location		location	= new Location();
		location.setUri( fileUri.toString() );

		ortus.boxlang.compiler.ast.Position pos = node.getPosition();
		if ( pos != null ) {
			int	startLine	= pos.getStart().getLine() - 1;
			int	startCol	= pos.getStart().getColumn();
			location.setRange( new Range(
			    new Position( startLine, startCol ),
			    new Position( pos.getEnd().getLine() - 1, pos.getEnd().getColumn() ) ) );
			locations.add( location );
		}

		return locations;
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
		return getLatestFileParseResult( docURI )
		    .flatMap( fpr -> fpr.findAstRoot() )
		    .map( rootNode -> {
			    return findDefinitionTarget( docURI, pos )
			        .map( ( node ) -> {
				        if ( node instanceof BoxFunctionDeclaration fnDecl ) {
					        // Cursor is on a function declaration - return its own location
					        // This enables VS Code to show "Find References" when on a declaration
					        return createLocationList( fnDecl, docURI );
				        } else if ( node instanceof BoxProperty property ) {
					        // Cursor is on a property declaration - return its own location
					        // This enables VS Code to show "Find References" when on a declaration
					        return createLocationList( property, docURI );
				        } else if ( node instanceof BoxFunctionInvocation fnUse ) {
					        // Find function definitions in these locations:
					        // 1. Same file
					        // 2. Parent classes (if inside a class that extends another)
					        // 3. BIFs - return empty (no source to navigate to)
					        return findFunctionDefinition( rootNode, fnUse.getName(), docURI );
				        } else if ( node instanceof BoxMethodInvocation methodInvocation ) {
					        // Find method definitions:
					        // 1. Resolve the receiver type (obj in obj.method())
					        // 2. Look up method in that class via project index
					        // 3. If not found, walk up inheritance chain
					        return findMethodDefinition( rootNode, methodInvocation, docURI );
				        } else if ( node instanceof BoxNew newExpr ) {
					        // Handle class instantiation - navigate to class definition
					        return findClassDefinition( newExpr );
				        } else if ( node instanceof BoxAnnotation annotation ) {
					        // Handle extends/implements annotations - navigate to class/interface definition
					        return findClassDefinitionFromAnnotation( annotation, docURI );
				        } else if ( node instanceof BoxReturnType returnType ) {
					        // Handle return type hints - navigate to class definition
					        return findClassDefinitionFromReturnType( returnType );
				        } else if ( node instanceof BoxArgumentDeclaration argDecl ) {
					        // Try to navigate to the type class definition first
					        List<Location> typeLocations = findClassDefinitionFromArgumentType( argDecl );
					        if ( !typeLocations.isEmpty() ) {
						        return typeLocations;
					        }
					        // If no type to navigate to, return the parameter's own location
					        // This enables VS Code to show "Find References" when on a parameter
					        return createLocationList( argDecl, docURI );
				        } else if ( node instanceof BoxFQN fqn ) {
					        // Handle type hints (return type, parameter type, variable type)
					        return findClassDefinitionFromFQN( fqn );
				        } else if ( node instanceof BoxImport importNode ) {
					        // Handle import statements - navigate to imported class/interface definition
					        return findClassDefinitionFromImport( importNode );
				        } else if ( node instanceof BoxDotAccess dotAccess ) {
					        // Handle property access via scoped access (variables.x, this.x)
					        return findPropertyDefinition( rootNode, dotAccess, docURI );
				        } else if ( node instanceof BoxIdentifier identifier ) {
					        // First, check if this identifier is part of a property access expression
					        // (e.g., the 'username' part of 'variables.username')
					        List<Location> propertyAccessLocations = findPropertyDefinitionAtPosition( rootNode, pos, docURI );
					        if ( !propertyAccessLocations.isEmpty() ) {
						        return propertyAccessLocations;
					        }

					        // Also check if this identifier is a class reference in type context
					        List<Location> classLocations = findClassDefinitionFromIdentifier( identifier );
					        if ( !classLocations.isEmpty() ) {
						        return classLocations;
					        }
					        // Check if this identifier is a property reference
					        List<Location> propertyLocations = findPropertyDefinitionFromIdentifier( rootNode, identifier, docURI );
					        if ( !propertyLocations.isEmpty() ) {
						        return propertyLocations;
					        }
					        return findVariableDefinition( rootNode, identifier, docURI );
				        }

				        return new ArrayList<Location>();
			        } )
			        .orElseGet( () -> new ArrayList<Location>() );
		    } )
		    .orElseGet( () -> new ArrayList<Location>() );
	}

	/**
	 * Find the type definition location for a variable at the given position.
	 * This navigates from a variable to its type's class definition.
	 *
	 * @param docURI The document URI
	 * @param pos    The cursor position
	 *
	 * @return List containing the type definition location, or empty list if not found
	 */
	public List<Location> findTypeDefinition( URI docURI, Position pos ) {
		return getLatestFileParseResult( docURI )
		    .flatMap( fpr -> fpr.findAstRoot() )
		    .map( rootNode -> {
			    // Find the target node at the cursor position
			    return findDefinitionTarget( docURI, pos )
			        .map( ( node ) -> {
				        // Handle identifiers (variables)
				        if ( node instanceof BoxIdentifier identifier ) {
					        return findTypeDefinitionFromIdentifier( rootNode, identifier );
				        }

				        // Handle argument declarations with type hints
				        if ( node instanceof BoxArgumentDeclaration argDecl ) {
					        return findTypeDefinitionFromArgument( argDecl );
				        }

				        return new ArrayList<Location>();
			        } )
			        .orElseGet( () -> new ArrayList<Location>() );
		    } )
		    .orElseGet( () -> new ArrayList<Location>() );
	}

	/**
	 * Find the type definition for a variable identifier.
	 * Determines the variable's type from type hints or inferred from assignments.
	 *
	 * @param rootNode   The AST root node
	 * @param identifier The variable identifier
	 *
	 * @return List containing the type definition location, or empty list if not found or primitive type
	 */
	private List<Location> findTypeDefinitionFromIdentifier( BoxNode rootNode, BoxIdentifier identifier ) {
		// First collect variable scope/type information
		VariableScopeCollectorVisitor scopeCollector = new VariableScopeCollectorVisitor();
		rootNode.accept( scopeCollector );

		String varName = identifier.getName();

		// Skip scope keywords
		if ( scopeCollector.isScopeKeyword( varName ) ) {
			return new ArrayList<>();
		}

		// Find the containing function for context
		BoxFunctionDeclaration	containingFunc	= identifier.getFirstAncestorOfType( BoxFunctionDeclaration.class );

		// Look up variable info
		VariableInfo			varInfo			= scopeCollector.getVariableInfo( varName, containingFunc );

		if ( varInfo == null ) {
			// No variable info found - try the type collector as fallback
			VariableTypeCollectorVisitor typeCollector = new VariableTypeCollectorVisitor();
			rootNode.accept( typeCollector );
			String inferredType = typeCollector.getVariableType( varName );

			if ( inferredType != null && !isPrimitiveType( inferredType ) ) {
				return findClassByNameAndGetLocation( inferredType );
			}

			return new ArrayList<>();
		}

		// Get the type - prefer explicit type hint, then inferred type
		String typeName = varInfo.typeHint();
		if ( typeName == null || typeName.isEmpty() ) {
			typeName = varInfo.inferredType();
		}

		if ( typeName == null || typeName.isEmpty() || isPrimitiveType( typeName ) ) {
			return new ArrayList<>();
		}

		// Look up the class in the index
		return findClassByNameAndGetLocation( typeName );
	}

	/**
	 * Find the type definition for a function argument with a type hint.
	 *
	 * @param argDecl The argument declaration
	 *
	 * @return List containing the type definition location, or empty list if not found or primitive type
	 */
	private List<Location> findTypeDefinitionFromArgument( BoxArgumentDeclaration argDecl ) {
		String typeName = argDecl.getType() != null ? argDecl.getType().toString() : null;

		if ( typeName == null || typeName.isEmpty() || isPrimitiveType( typeName ) ) {
			return new ArrayList<>();
		}

		return findClassByNameAndGetLocation( typeName );
	}

	/**
	 * Check if a type name represents a primitive or built-in type.
	 *
	 * @param typeName The type name to check
	 *
	 * @return true if the type is primitive/built-in, false otherwise
	 */
	private boolean isPrimitiveType( String typeName ) {
		if ( typeName == null ) {
			return true;
		}
		String lower = typeName.toLowerCase();
		return lower.equals( "string" )
		    || lower.equals( "numeric" )
		    || lower.equals( "number" )
		    || lower.equals( "boolean" )
		    || lower.equals( "array" )
		    || lower.equals( "struct" )
		    || lower.equals( "query" )
		    || lower.equals( "any" )
		    || lower.equals( "void" )
		    || lower.equals( "date" )
		    || lower.equals( "datetime" )
		    || lower.equals( "binary" )
		    || lower.equals( "function" )
		    || lower.equals( "xml" )
		    || lower.equals( "object" );
	}

	/**
	 * Find implementation locations for an interface method or interface/abstract class.
	 * This navigates from interface/abstract method to concrete implementations.
	 *
	 * @param docURI The document URI
	 * @param pos    The cursor position
	 *
	 * @return List of locations pointing to implementations
	 */
	public List<Location> findImplementations( URI docURI, Position pos ) {
		return getLatestFileParseResult( docURI )
		    .flatMap( fpr -> fpr.findAstRoot() )
		    .map( rootNode -> {
			    // Find the target node at the cursor position
			    return findDefinitionTarget( docURI, pos )
			        .map( ( node ) -> {
				        // Handle function declarations (interface methods or abstract methods)
				        if ( node instanceof BoxFunctionDeclaration fnDecl ) {
					        return findImplementationsOfMethod( fnDecl, rootNode, docURI );
				        }

				        // Handle interface declarations
				        if ( node instanceof BoxInterface ) {
					        return findImplementationsOfClassOrInterface( rootNode, docURI );
				        }

				        // Handle class declarations (for abstract classes)
				        if ( node instanceof BoxClass ) {
					        return findImplementationsOfClassOrInterface( rootNode, docURI );
				        }

				        return new ArrayList<Location>();
			        } )
			        .orElseGet( () -> new ArrayList<Location>() );
		    } )
		    .orElseGet( () -> new ArrayList<Location>() );
	}

	/**
	 * Find implementations of a method declared in an interface or abstract class.
	 *
	 * @param fnDecl   The function declaration
	 * @param rootNode The AST root node
	 * @param docURI   The document URI
	 *
	 * @return List of locations pointing to implementing methods
	 */
	private List<Location> findImplementationsOfMethod( BoxFunctionDeclaration fnDecl, BoxNode rootNode, URI docURI ) {
		List<Location>	locations	= new ArrayList<>();

		String			methodName	= fnDecl.getName();
		String			className	= getClassNameFromUri( docURI );

		if ( methodName == null || className == null ) {
			return locations;
		}

		ProjectIndex	index		= getIndex();

		// Find the class in the index to determine if it's an interface or abstract class
		var				classOpt	= index.findClassByName( className );
		if ( classOpt.isEmpty() ) {
			return locations;
		}

		IndexedClass		indexedClass		= classOpt.get();
		String				classFQN			= indexedClass.fullyQualifiedName();

		// Get implementing/extending classes
		List<IndexedClass>	implementingClasses	= new ArrayList<>();

		if ( indexedClass.isInterface() ) {
			// Find all classes implementing this interface
			implementingClasses.addAll( index.findClassesImplementing( classFQN ) );
		} else {
			// Find all classes extending this class (for abstract methods)
			implementingClasses.addAll( index.findClassesExtending( classFQN ) );
		}

		// For each implementing/extending class, find the method implementation
		for ( IndexedClass implClass : implementingClasses ) {
			var methodOpt = index.findMethod( implClass.name(), methodName );
			if ( methodOpt.isPresent() ) {
				IndexedMethod	method		= methodOpt.get();
				Location		location	= createLocationFromIndexedMethod( method );
				if ( location != null ) {
					locations.add( location );
				}
			}
		}

		return locations;
	}

	/**
	 * Find implementations of an interface or extending classes of a class.
	 * This is called when the cursor is on the interface/class declaration itself.
	 *
	 * @param rootNode The AST root node
	 * @param docURI   The document URI
	 *
	 * @return List of locations pointing to implementing/extending classes
	 */
	private List<Location> findImplementationsOfClassOrInterface( BoxNode rootNode, URI docURI ) {
		List<Location>	locations	= new ArrayList<>();

		String			className	= getClassNameFromUri( docURI );
		if ( className == null ) {
			return locations;
		}

		ProjectIndex	index		= getIndex();

		// Find the class in the index
		var				classOpt	= index.findClassByName( className );
		if ( classOpt.isEmpty() ) {
			return locations;
		}

		IndexedClass		indexedClass		= classOpt.get();
		String				classFQN			= indexedClass.fullyQualifiedName();

		// Get implementing/extending classes
		List<IndexedClass>	implementingClasses	= new ArrayList<>();

		if ( indexedClass.isInterface() ) {
			// Find all classes implementing this interface
			implementingClasses.addAll( index.findClassesImplementing( classFQN ) );
		} else {
			// Find all classes extending this class
			implementingClasses.addAll( index.findClassesExtending( classFQN ) );
		}

		// Add locations for each implementing/extending class
		for ( IndexedClass implClass : implementingClasses ) {
			if ( implClass.fileUri() != null && implClass.location() != null ) {
				Location location = new Location();
				location.setUri( implClass.fileUri() );
				location.setRange( implClass.location() );
				locations.add( location );
			}
		}

		return locations;
	}

	/**
	 * Find the definition location for a variable identifier.
	 * Uses VariableDefinitionResolver to properly handle scoping rules.
	 *
	 * @param rootNode   The AST root node
	 * @param identifier The variable identifier to find definition for
	 * @param docURI     The document URI
	 *
	 * @return List containing the definition location, or empty list if not found
	 */
	private List<Location> findVariableDefinition( BoxNode rootNode, BoxIdentifier identifier, URI docURI ) {
		List<Location>				locations	= new ArrayList<>();

		// Use the VariableDefinitionResolver to find the declaration
		VariableDefinitionResolver	resolver	= new VariableDefinitionResolver( identifier );
		resolver.resolve( rootNode );

		var declaration = resolver.getResolvedDeclaration();
		if ( declaration != null && declaration.declarationNode() != null ) {
			Location location = new Location();
			location.setUri( docURI.toString() );

			var declPos = declaration.declarationNode().getPosition();
			if ( declPos != null ) {
				location.setRange( positionToRange( declPos ) );
				locations.add( location );
			}
		}

		return locations;
	}

	/**
	 * Find the definition location for a property from a BoxDotAccess node.
	 * Handles `variables.propertyName` and `this.propertyName` access patterns.
	 *
	 * @param rootNode  The AST root node
	 * @param dotAccess The BoxDotAccess node representing the property access
	 * @param docURI    The document URI
	 *
	 * @return List containing the property definition location, or empty list if not found
	 */
	private List<Location> findPropertyDefinition( BoxNode rootNode, BoxDotAccess dotAccess, URI docURI ) {
		List<Location>	locations		= new ArrayList<>();

		// Extract the property name from the access part
		String			propertyName	= null;
		if ( dotAccess.getAccess() instanceof BoxIdentifier propId ) {
			propertyName = propId.getName();
		}

		if ( propertyName == null ) {
			return locations;
		}

		// First, look for property in the same file
		List<Location> sameFileLocations = findPropertyInSameFile( rootNode, propertyName, docURI );
		if ( !sameFileLocations.isEmpty() ) {
			return sameFileLocations;
		}

		// If not found, check inherited properties via the project index
		return findPropertyInInheritanceChain( rootNode, propertyName, docURI );
	}

	/**
	 * Find property definition at a specific position using getDescendantsOfType.
	 * This is a fallback for when the visitor pattern doesn't traverse to BoxDotAccess nodes.
	 *
	 * @param rootNode The AST root node
	 * @param pos      The cursor position
	 * @param docURI   The document URI
	 *
	 * @return List containing the property definition location, or empty list if not found
	 */
	private List<Location> findPropertyDefinitionAtPosition( BoxNode rootNode, Position pos, URI docURI ) {
		int					line		= pos.getLine() + 1; // Convert to 1-indexed
		int					column		= pos.getCharacter();

		// Find all BoxDotAccess nodes in the AST
		List<BoxDotAccess>	dotAccesses	= rootNode.getDescendantsOfType( BoxDotAccess.class );

		for ( BoxDotAccess dotAccess : dotAccesses ) {
			// Check if cursor is within this BoxDotAccess
			if ( !BLASTTools.containsPosition( dotAccess, line, column ) ) {
				continue;
			}

			// Check if this is a scoped property access (variables.x, this.x)
			BoxNode	context		= dotAccess.getContext();
			String	scopeName	= null;

			if ( context instanceof BoxScope scope ) {
				scopeName = scope.getName();
			} else if ( context instanceof BoxIdentifier scopeId ) {
				scopeName = scopeId.getName();
			}

			if ( scopeName != null ) {
				String lowerScopeName = scopeName.toLowerCase();
				if ( lowerScopeName.equals( "variables" ) || lowerScopeName.equals( "this" ) ) {
					// Check if cursor is on the property name (the access part)
					if ( dotAccess.getAccess() != null && BLASTTools.containsPosition( dotAccess.getAccess(), line, column ) ) {
						// Extract property name and find definition
						return findPropertyDefinition( rootNode, dotAccess, docURI );
					}
				}
			}
		}

		return new ArrayList<>();
	}

	/**
	 * Find the definition location for a property from an unqualified identifier.
	 * This handles cases where properties are accessed without a scope prefix within a class.
	 *
	 * @param rootNode   The AST root node
	 * @param identifier The BoxIdentifier node
	 * @param docURI     The document URI
	 *
	 * @return List containing the property definition location, or empty list if not found
	 */
	private List<Location> findPropertyDefinitionFromIdentifier( BoxNode rootNode, BoxIdentifier identifier, URI docURI ) {
		String	propertyName	= identifier.getName();

		// Check if this identifier is the access part of a BoxDotAccess (e.g., 'foo' in 'a.foo')
		// In that case, we should only resolve if the receiver is 'variables' or 'this'
		// (which is handled by findPropertyDefinitionAtPosition)
		BoxNode	parent			= identifier.getParent();
		if ( parent instanceof BoxDotAccess dotAccess && dotAccess.getAccess() == identifier ) {
			// Check if the receiver is 'variables' or 'this'
			BoxNode	context		= dotAccess.getContext();
			String	scopeName	= null;
			if ( context instanceof BoxScope scope ) {
				scopeName = scope.getName();
			} else if ( context instanceof BoxIdentifier scopeId ) {
				scopeName = scopeId.getName();
			}

			// Only allow property lookup if the receiver is 'variables' or 'this'
			if ( scopeName == null ) {
				return new ArrayList<>();
			}
			String lowerScopeName = scopeName.toLowerCase();
			if ( !lowerScopeName.equals( "variables" ) && !lowerScopeName.equals( "this" ) ) {
				// Unknown receiver - can't determine the type, so don't resolve
				return new ArrayList<>();
			}
		}

		// First, look for property in the same file
		List<Location> sameFileLocations = findPropertyInSameFile( rootNode, propertyName, docURI );
		if ( !sameFileLocations.isEmpty() ) {
			return sameFileLocations;
		}

		// If not found, check inherited properties via the project index
		return findPropertyInInheritanceChain( rootNode, propertyName, docURI );
	}

	/**
	 * Find a property declaration in the same file.
	 *
	 * @param rootNode     The AST root node
	 * @param propertyName The property name to find
	 * @param docURI       The document URI
	 *
	 * @return List containing the property definition location, or empty list if not found
	 */
	private List<Location> findPropertyInSameFile( BoxNode rootNode, String propertyName, URI docURI ) {
		List<Location>		locations	= new ArrayList<>();

		// Find all property declarations in the file
		List<BoxProperty>	properties	= rootNode.getDescendantsOfType( BoxProperty.class );

		for ( BoxProperty property : properties ) {
			// Extract property name from annotations
			String declaredName = extractPropertyName( property );
			if ( declaredName != null && declaredName.equalsIgnoreCase( propertyName ) ) {
				Location location = new Location();
				location.setUri( docURI.toString() );

				var propPos = property.getPosition();
				if ( propPos != null ) {
					location.setRange( positionToRange( propPos ) );
					locations.add( location );
				}
				return locations; // Return first match
			}
		}

		return locations;
	}

	/**
	 * Find a property declaration in parent classes via the project index.
	 *
	 * @param rootNode     The AST root node
	 * @param propertyName The property name to find
	 * @param docURI       The document URI
	 *
	 * @return List containing the property definition location, or empty list if not found
	 */
	private List<Location> findPropertyInInheritanceChain( BoxNode rootNode, String propertyName, URI docURI ) {
		List<Location>	locations	= new ArrayList<>();

		// Get the class name from the current document
		String			className	= getClassNameFromUri( docURI );
		if ( className == null ) {
			return locations;
		}

		// Find the class in the index to get its parent
		Optional<IndexedClass> currentClass = getIndex().findClassByName( className );
		if ( currentClass.isEmpty() ) {
			return locations;
		}

		// Walk up the inheritance chain
		String parentClassName = currentClass.get().extendsClass();
		while ( parentClassName != null && !parentClassName.isEmpty() ) {
			// Look for the property in the parent class via the index
			Optional<IndexedProperty> indexedProperty = getIndex().findProperty( parentClassName, propertyName );
			if ( indexedProperty.isPresent() ) {
				Location location = new Location();
				location.setUri( indexedProperty.get().fileUri() );
				location.setRange( indexedProperty.get().location() );
				locations.add( location );
				return locations;
			}

			// Move to the next parent
			Optional<IndexedClass> parentClass = getIndex().findClassByName( parentClassName );
			if ( parentClass.isEmpty() ) {
				break;
			}
			parentClassName = parentClass.get().extendsClass();
		}

		return locations;
	}

	/**
	 * Extract the property name from a BoxProperty node.
	 *
	 * @param property The BoxProperty node
	 *
	 * @return The property name, or null if not found
	 */
	private String extractPropertyName( BoxProperty property ) {
		// Use BLASTTools.getPropertyName for consistency
		return BLASTTools.getPropertyName( property );
	}

	/**
	 * Find the definition location for a class from a new expression.
	 * e.g., `new User()` -> navigate to User.bx
	 *
	 * @param newExpr The BoxNew node representing the class instantiation
	 *
	 * @return List containing the class definition location, or empty list if not found
	 */
	private List<Location> findClassDefinition( BoxNew newExpr ) {
		String className = extractClassNameFromNew( newExpr );
		if ( className == null ) {
			return new ArrayList<>();
		}

		return findClassByNameAndGetLocation( className );
	}

	/**
	 * Find the definition location for a class/interface from an annotation.
	 * Handles `extends="ClassName"` and `implements="InterfaceName"` annotations.
	 *
	 * @param annotation The BoxAnnotation node
	 *
	 * @return List containing the class/interface definition location, or empty list if not found
	 */
	private List<Location> findClassDefinitionFromAnnotation( BoxAnnotation annotation, URI docURI ) {
		String key = annotation.getKey().getValue().toLowerCase();

		if ( !key.equals( "extends" ) && !key.equals( "implements" ) ) {
			return new ArrayList<>();
		}

		// Extract class/interface name from annotation value
		String className = null;
		if ( annotation.getValue() instanceof BoxStringLiteral strLiteral ) {
			className = strLiteral.getValue();
		} else if ( annotation.getValue() instanceof BoxFQN fqn ) {
			className = extractClassNameFromFQN( fqn );
		} else if ( annotation.getValue() != null ) {
			className	= annotation.getValue().getSourceText();
			// Clean up quotes if present
			className	= className.replace( "\"", "" ).replace( "'", "" );
		}

		if ( className == null || className.isEmpty() ) {
			return new ArrayList<>();
		}

		// Handle comma-separated list for implements
		if ( className.contains( "," ) ) {
			// Find which class name the cursor is on (we'll use the first one for now)
			// In a more sophisticated implementation, we'd track cursor position within the string
			String[] classes = className.split( "," );
			className = classes[ 0 ].trim();
		}

		return findClassByNameAndGetLocation( className, docURI );
	}

	/**
	 * Find the definition location for a class from a BoxFQN node.
	 * Handles type hints in return types, parameter types, and variable types.
	 *
	 * @param fqn The BoxFQN node representing the class reference
	 *
	 * @return List containing the class definition location, or empty list if not found
	 */
	private List<Location> findClassDefinitionFromFQN( BoxFQN fqn ) {
		String className = extractClassNameFromFQN( fqn );
		if ( className == null ) {
			return new ArrayList<>();
		}

		return findClassByNameAndGetLocation( className );
	}

	/**
	 * Find the definition location for a class from a BoxImport node.
	 * Handles import statements like `import ClassName;` and `import package.ClassName;`
	 *
	 * Note: Java imports (e.g., `import java:java.util.ArrayList;`) return empty
	 * since there's no source file to navigate to.
	 *
	 * For package-qualified imports (e.g., `import subpackage.Item;`), looks up by FQN only.
	 * This ensures that `import subpackage.Item;` does NOT match `Item.bx` in the root folder.
	 *
	 * @param importNode The BoxImport node representing the import statement
	 *
	 * @return List containing the class definition location, or empty list if not found
	 */
	private List<Location> findClassDefinitionFromImport( BoxImport importNode ) {
		if ( importNode.getExpression() == null ) {
			return new ArrayList<>();
		}

		String fullPath = importNode.getExpression().getSourceText();
		if ( fullPath == null || fullPath.isEmpty() ) {
			return new ArrayList<>();
		}

		// Check if this is a Java import (contains "java:" prefix)
		// Java imports don't have source to navigate to
		if ( fullPath.toLowerCase().startsWith( "java:" ) ) {
			return new ArrayList<>();
		}

		// Check if this is a package-qualified import (contains a dot)
		// e.g., `import subpackage.Item;`
		if ( fullPath.contains( "." ) ) {
			// Use FQN lookup - this ensures we don't match the wrong class
			// e.g., `import subpackage.Item;` should NOT match `Item.bx` in root
			return findClassByFQNAndGetLocation( fullPath );
		}

		// Simple import (no package path) - use simple name lookup
		// e.g., `import Item;`
		return findClassByNameAndGetLocation( fullPath );
	}

	/**
	 * Look up a class/interface by FQN in the project index and return its location.
	 * FQN lookup is case-insensitive to match BoxLang semantics.
	 *
	 * @param fqn The fully qualified name to look up (e.g., "subpackage.Item")
	 *
	 * @return List containing the definition location, or empty list if not found
	 */
	private List<Location> findClassByFQNAndGetLocation( String fqn ) {
		if ( fqn == null || fqn.isEmpty() ) {
			return new ArrayList<>();
		}

		ProjectIndex	index		= getIndex();

		// Try exact FQN match first
		var				classOpt	= index.findClassByFQN( fqn );

		// If not found, try case-insensitive search through all classes
		if ( classOpt.isEmpty() ) {
			String lowerFqn = fqn.toLowerCase();
			classOpt = index.getAllClasses().stream()
			    .filter( c -> c.fullyQualifiedName() != null && c.fullyQualifiedName().toLowerCase().equals( lowerFqn ) )
			    .findFirst();
		}

		if ( classOpt.isEmpty() ) {
			return new ArrayList<>();
		}

		IndexedClass indexedClass = classOpt.get();

		if ( indexedClass.fileUri() == null || indexedClass.location() == null ) {
			return new ArrayList<>();
		}

		Location location = new Location();
		location.setUri( indexedClass.fileUri() );
		location.setRange( indexedClass.location() );

		return List.of( location );
	}

	/**
	 * Find the definition location for a class from a BoxReturnType node.
	 * Handles return type hints in function declarations.
	 *
	 * @param returnType The BoxReturnType node
	 *
	 * @return List containing the class definition location, or empty list if not found
	 */
	private List<Location> findClassDefinitionFromReturnType( BoxReturnType returnType ) {
		String fqn = returnType.getFqn();
		if ( fqn == null || fqn.isEmpty() ) {
			return new ArrayList<>();
		}

		// Extract just the class name (last part of FQN)
		String	className	= fqn;
		int		lastDot		= fqn.lastIndexOf( '.' );
		if ( lastDot >= 0 && lastDot < fqn.length() - 1 ) {
			className = fqn.substring( lastDot + 1 );
		}

		return findClassByNameAndGetLocation( className );
	}

	/**
	 * Find the definition location for a class from a BoxArgumentDeclaration node.
	 * Handles parameter type hints in function declarations.
	 *
	 * @param argDecl The BoxArgumentDeclaration node
	 *
	 * @return List containing the class definition location, or empty list if not found
	 */
	private List<Location> findClassDefinitionFromArgumentType( BoxArgumentDeclaration argDecl ) {
		String type = argDecl.getType();
		if ( type == null || type.isEmpty() ) {
			return new ArrayList<>();
		}

		// Extract just the class name (last part if it's a FQN)
		String	className	= type;
		int		lastDot		= type.lastIndexOf( '.' );
		if ( lastDot >= 0 && lastDot < type.length() - 1 ) {
			className = type.substring( lastDot + 1 );
		}

		return findClassByNameAndGetLocation( className );
	}

	/**
	 * Find the definition location for a class from a BoxIdentifier.
	 * This handles cases where the class name appears as an identifier (e.g., in type hints).
	 *
	 * @param identifier The BoxIdentifier node
	 *
	 * @return List containing the class definition location, or empty list if not found
	 */
	private List<Location> findClassDefinitionFromIdentifier( BoxIdentifier identifier ) {
		String name = identifier.getName();
		if ( name == null || name.isEmpty() ) {
			return new ArrayList<>();
		}

		// Check if this identifier is the access part of a BoxDotAccess (e.g., 'foo' in 'a.foo')
		// In that case, we should NOT try to resolve it as a class name - it's a property/method access
		BoxNode parent = identifier.getParent();
		if ( parent instanceof BoxDotAccess dotAccess && dotAccess.getAccess() == identifier ) {
			// This identifier is the accessed member of a dot expression
			// Don't try to resolve it as a class name
			return new ArrayList<>();
		}

		// Check if this identifier matches a known class name
		return findClassByNameAndGetLocation( name );
	}

	/**
	 * Look up a class/interface by name in the project index and return its location.
	 *
	 * @param className The class or interface name to look up
	 *
	 * @return List containing the definition location, or empty list if not found
	 */
	private List<Location> findClassByNameAndGetLocation( String className ) {
		return findClassByNameAndGetLocation( className, null );
	}

	/**
	 * Look up a class/interface by name in the project index and return its location.
	 * Supports relative class path resolution when docURI is provided.
	 *
	 * @param className The class or interface name to look up
	 * @param docURI    The current document URI (for relative path resolution), or null
	 *
	 * @return List containing the definition location, or empty list if not found
	 */
	private List<Location> findClassByNameAndGetLocation( String className, URI docURI ) {
		if ( className == null || className.isEmpty() ) {
			return new ArrayList<>();
		}

		ProjectIndex	index		= getIndex();
		var				classOpt	= index.findClassByName( className );

		// Try by FQN if simple name lookup failed
		if ( classOpt.isEmpty() ) {
			classOpt = index.findClassByFQN( className );
		}

		// If still not found and className contains a dot (potential relative path),
		// try resolving relative to the current file's package
		if ( classOpt.isEmpty() && className.contains( "." ) && docURI != null ) {
			String currentPackage = getCurrentPackageFromURI( docURI );
			if ( currentPackage != null && !currentPackage.isEmpty() ) {
				String qualifiedName = currentPackage + "." + className;
				classOpt = index.findClassByFQN( qualifiedName );
			}
		}

		if ( classOpt.isEmpty() ) {
			return new ArrayList<>();
		}

		IndexedClass indexedClass = classOpt.get();

		if ( indexedClass.fileUri() == null || indexedClass.location() == null ) {
			return new ArrayList<>();
		}

		Location location = new Location();
		location.setUri( indexedClass.fileUri() );
		location.setRange( indexedClass.location() );

		return List.of( location );
	}

	/**
	 * Get the package (directory path) of a file relative to workspace root.
	 * For example, if file is at "subpackage/BaseType.bx", returns "subpackage".
	 * Returns null if package cannot be determined.
	 */
	private String getCurrentPackageFromURI( URI fileUri ) {
		if ( fileUri == null ) {
			return null;
		}

		try {
			// Get workspace root
			var folders = getWorkspaceFolders();
			if ( folders == null || folders.isEmpty() ) {
				return null;
			}

			java.net.URI		workspaceUri	= new java.net.URI( folders.get( 0 ).getUri() );
			java.nio.file.Path	workspaceRoot	= java.nio.file.Paths.get( workspaceUri );
			java.nio.file.Path	filePath		= java.nio.file.Paths.get( fileUri );

			// Get relative path from workspace root
			java.nio.file.Path	relativePath	= workspaceRoot.relativize( filePath );
			String				pathStr			= relativePath.toString();

			// Get the directory part (without filename)
			int					lastSlash		= Math.max( pathStr.lastIndexOf( '/' ), pathStr.lastIndexOf( '\\' ) );
			if ( lastSlash < 0 ) {
				// File is in root directory
				return null;
			}

			String packagePath = pathStr.substring( 0, lastSlash );

			// Convert path separators to dots
			return packagePath.replace( '/', '.' ).replace( '\\', '.' );
		} catch ( Exception e ) {
			// If we can't determine package, return null
			return null;
		}
	}

	/**
	 * Find the definition location for a function invocation (without receiver).
	 * First looks in the same file, then walks up the inheritance chain
	 * if the file defines a class that extends another class.
	 *
	 * @param rootNode     The AST root node
	 * @param functionName The function name to find
	 * @param docURI       The document URI
	 *
	 * @return List containing the definition location, or empty list if not found
	 */
	private List<Location> findFunctionDefinition( BoxNode rootNode, String functionName, URI docURI ) {
		// First, look in the same file
		List<Location> sameFileLocations = findMatchingFunctionDeclarations( docURI, functionName );
		if ( !sameFileLocations.isEmpty() ) {
			return sameFileLocations;
		}

		// If not found, check if we're in a class that extends another class
		// and look for the function in parent classes
		ProjectIndex	index		= getIndex();

		// Get the class name from the current file
		String			className	= getClassNameFromUri( docURI );
		if ( className == null ) {
			return new ArrayList<>();
		}

		// Find the class in the index
		var classOpt = index.findClassByName( className );
		if ( classOpt.isEmpty() ) {
			return new ArrayList<>();
		}

		IndexedClass indexedClass = classOpt.get();

		// If the class doesn't extend anything, we can't find the function
		if ( indexedClass.extendsClass() == null || indexedClass.extendsClass().isEmpty() ) {
			return new ArrayList<>();
		}

		// Walk up the inheritance chain looking for the function as a method
		String			classFQN	= indexedClass.fullyQualifiedName();
		List<String>	ancestors	= index.getInheritanceGraph().getAncestors( classFQN );

		for ( String ancestorFQN : ancestors ) {
			String	ancestorSimpleName	= extractSimpleNameFromFQN( ancestorFQN );

			// Try to find the method in this ancestor
			var		methodOpt			= index.findMethod( ancestorSimpleName, functionName );
			if ( methodOpt.isPresent() ) {
				IndexedMethod	method		= methodOpt.get();
				Location		location	= createLocationFromIndexedMethod( method );
				if ( location != null ) {
					return List.of( location );
				}
			}
		}

		return new ArrayList<>();
	}

	/**
	 * Find the definition location for a method invocation.
	 * Uses VariableTypeCollectorVisitor to resolve the receiver type,
	 * then looks up the method in the project index.
	 * Walks up the inheritance chain if the method is not found in the immediate class.
	 *
	 * @param rootNode         The AST root node
	 * @param methodInvocation The method invocation node
	 * @param docURI           The document URI
	 *
	 * @return List containing the definition location, or empty list if not found
	 */
	private List<Location> findMethodDefinition( BoxNode rootNode, BoxMethodInvocation methodInvocation, URI docURI ) {
		List<Location>	locations		= new ArrayList<>();
		String			methodName		= methodInvocation.getName().getSourceText();

		// Get the receiver object
		BoxNode			obj				= methodInvocation.getObj();

		// Handle `this.methodName()` - look in the same file first
		// The `this` keyword can be represented as BoxScope or BoxIdentifier
		boolean			isThisReceiver	= false;
		if ( obj instanceof BoxScope scope && "this".equalsIgnoreCase( scope.getName() ) ) {
			isThisReceiver = true;
		} else if ( obj instanceof BoxIdentifier identifier && "this".equalsIgnoreCase( identifier.getName() ) ) {
			isThisReceiver = true;
		}

		if ( isThisReceiver ) {
			// Look for method in the same file first
			List<Location> sameFileLocations = findMatchingFunctionDeclarations( docURI, methodName );
			if ( !sameFileLocations.isEmpty() ) {
				return sameFileLocations;
			}

			// If not found in same file, check parent classes (for inherited methods)
			String currentClassName = getClassNameFromUri( docURI );
			if ( currentClassName != null ) {
				Location inheritedLocation = findMethodInClassHierarchy( currentClassName, methodName );
				if ( inheritedLocation != null ) {
					return List.of( inheritedLocation );
				}
			}
			return locations;
		}

		// Resolve the receiver type for obj.method() calls
		String className = null;

		if ( obj instanceof BoxIdentifier objIdentifier ) {
			// Collect variable types from the AST
			VariableTypeCollectorVisitor typeCollector = new VariableTypeCollectorVisitor();
			rootNode.accept( typeCollector );
			className = typeCollector.getVariableType( objIdentifier.getName() );
		}

		if ( className == null ) {
			// Could not determine receiver type
			return locations;
		}

		// Look up the method in the project index, walking up the inheritance chain
		Location methodLocation = findMethodInClassHierarchy( className, methodName );
		if ( methodLocation != null ) {
			locations.add( methodLocation );
		}

		return locations;
	}

	/**
	 * Find a method in a class or its ancestors.
	 * Walks up the inheritance chain until the method is found.
	 *
	 * @param className  The starting class name
	 * @param methodName The method name to find
	 *
	 * @return The Location of the method definition, or null if not found
	 */
	private Location findMethodInClassHierarchy( String className, String methodName ) {
		ProjectIndex	index		= getIndex();

		// First, try to find the method in the immediate class
		var				methodOpt	= index.findMethod( className, methodName );
		if ( methodOpt.isPresent() ) {
			IndexedMethod method = methodOpt.get();
			return createLocationFromIndexedMethod( method );
		}

		// Find the class to get its FQN for inheritance lookup
		var classOpt = index.findClassByName( className );
		if ( classOpt.isEmpty() ) {
			return null;
		}

		String			classFQN	= classOpt.get().fullyQualifiedName();

		// Walk up the inheritance chain
		List<String>	ancestors	= index.getInheritanceGraph().getAncestors( classFQN );
		for ( String ancestorFQN : ancestors ) {
			// Extract simple class name from FQN
			String ancestorSimpleName = extractSimpleNameFromFQN( ancestorFQN );

			// Try to find the method in this ancestor
			methodOpt = index.findMethod( ancestorSimpleName, methodName );
			if ( methodOpt.isPresent() ) {
				IndexedMethod method = methodOpt.get();
				return createLocationFromIndexedMethod( method );
			}
		}

		return null;
	}

	/**
	 * Create an LSP Location from an IndexedMethod.
	 */
	private Location createLocationFromIndexedMethod( IndexedMethod method ) {
		if ( method.fileUri() == null || method.location() == null ) {
			return null;
		}

		Location location = new Location();
		location.setUri( method.fileUri() );
		location.setRange( method.location() );
		return location;
	}

	/**
	 * Extract the simple class name from a fully qualified name.
	 * e.g., "models.services.UserService" -> "UserService"
	 */
	private String extractSimpleNameFromFQN( String fqn ) {
		if ( fqn == null || fqn.isEmpty() ) {
			return fqn;
		}
		int lastDot = fqn.lastIndexOf( '.' );
		if ( lastDot >= 0 && lastDot < fqn.length() - 1 ) {
			return fqn.substring( lastDot + 1 );
		}
		return fqn;
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
	 * @param docURI   The document URI
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
				    BoxNode obj		= methodInvocation.getObj();
				    if ( obj instanceof BoxIdentifier objIdentifier ) {
					    String						varName			= objIdentifier.getName();

					    // Collect variable types from the AST
					    VariableTypeCollectorVisitor typeCollector	= new VariableTypeCollectorVisitor();
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

			    // Handle extends/implements annotations - provide hover for class/interface names
			    if ( target instanceof BoxAnnotation annotation ) {
				    String className = extractClassNameFromAnnotation( annotation );
				    if ( className != null ) {
					    var indexedClassOpt = findClassByNameWithRelativeResolution( className, docURI );
					    if ( indexedClassOpt.isPresent() ) {
						    return buildHoverForClass( indexedClassOpt.get() );
					    }
				    }
			    }

			    // Handle scope keywords (variables, local, this, arguments, etc.)
			    if ( target instanceof BoxScope scopeNode ) {
				    String						scopeName		= scopeNode.getName();
				    VariableScopeCollectorVisitor scopeCollector = new VariableScopeCollectorVisitor();
				    VariableInfo				scopeInfo		= scopeCollector.getScopeKeywordInfo( scopeName );
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
				    VariableInfo		varInfo			= scopeCollector.getVariableInfo( varName, containingFunc );
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
			    FindSignatureHelpTargetVisitor visitor	= new FindSignatureHelpTargetVisitor( position, rootNode );

			    BoxNode						target		= visitor.getTarget();
			    int							activeParam	= visitor.getActiveParameter();

			    if ( target == null ) {
				    return null;
			    }

			    // Handle function invocations (UDFs and BIFs)
			    if ( target instanceof BoxFunctionInvocation fnInvocation ) {
				    String functionName	= fnInvocation.getName();

				    // First try to find a user-defined function in the same file
				    var	udfOpt			= rootNode
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
				    String methodName = methodInvocation.getName().getSourceText();

				    // Try to resolve the object's type using variable tracking
				    BoxNode obj		= methodInvocation.getObj();
				    if ( obj instanceof BoxIdentifier objIdentifier ) {
					    String						varName			= objIdentifier.getName();

					    // Collect variable types from the AST
					    VariableTypeCollectorVisitor typeCollector	= new VariableTypeCollectorVisitor();
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
		SignatureHelp				help			= new SignatureHelp();
		List<SignatureInformation>	signatures		= new ArrayList<>();

		SignatureInformation		sigInfo			= new SignatureInformation();

		// Build the signature label
		String						signatureLabel	= buildFunctionSignature( fnDecl, null );
		sigInfo.setLabel( signatureLabel );

		// Build parameter information
		List<ParameterInformation> params = new ArrayList<>();
		for ( BoxNode child : fnDecl.getChildren() ) {
			if ( child instanceof BoxArgumentDeclaration arg ) {
				ParameterInformation	paramInfo	= new ParameterInformation();

				// Build the parameter label
				StringBuilder			paramLabel	= new StringBuilder();
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
				StringBuilder	docContent	= new StringBuilder();

				String			commentText	= docComment.getCommentText();
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
		SignatureHelp				help			= new SignatureHelp();
		List<SignatureInformation>	signatures		= new ArrayList<>();

		SignatureInformation		sigInfo			= new SignatureInformation();

		// Build the signature label
		String						signatureLabel	= buildSignatureFromIndexedMethod( method );
		sigInfo.setLabel( signatureLabel );

		// Build parameter information
		List<ParameterInformation> params = new ArrayList<>();
		for ( IndexedParameter param : method.parameters() ) {
			ParameterInformation	paramInfo	= new ParameterInformation();

			StringBuilder			paramLabel	= new StringBuilder();
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

			SignatureHelp				help			= new SignatureHelp();
			List<SignatureInformation>	signatures		= new ArrayList<>();

			SignatureInformation		sigInfo			= new SignatureInformation();

			// Build parameters
			var							declaredArgs	= bifDesc.getBIF().getDeclaredArguments();
			List<String>				paramStrings	= new ArrayList<>();
			List<ParameterInformation>	params			= new ArrayList<>();

			for ( var arg : declaredArgs ) {
				ParameterInformation	paramInfo	= new ParameterInformation();
				String					paramSig	= arg.signatureAsString();

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
		StringBuilder	content		= new StringBuilder();

		// Extract property info from annotations
		String			name		= null;
		String			type		= null;
		String			defaultVal	= null;

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
		StringBuilder	content		= new StringBuilder();

		String			scopeName	= scopeInfo.name();

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

		StringBuilder	sb				= new StringBuilder();
		String[]		lines			= documentation.split( "\n" );

		// Separate description from tags
		StringBuilder	description		= new StringBuilder();
		List<String>	paramTags		= new ArrayList<>();
		String			returnTag		= null;
		List<String>	throwsTags		= new ArrayList<>();
		String			deprecatedTag	= null;
		String			sinceTag		= null;
		String			authorTag		= null;

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
				StringBuilder	paramStr	= new StringBuilder();

				// Check for required annotation
				boolean			isRequired	= arg.getAnnotations().stream()
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
		String[]		lines	= commentText.split( "\n" );
		StringBuilder	cleaned	= new StringBuilder();

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
		StringBuilder						sb						= new StringBuilder();

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
				int	lastDot			= sourceText.lastIndexOf( '.' );
				int	lastColon		= sourceText.lastIndexOf( ':' );
				int	lastSeparator	= Math.max( lastDot, lastColon );

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
		int	lastDot			= fullPath.lastIndexOf( '.' );
		int	lastColon		= fullPath.lastIndexOf( ':' );
		int	lastSeparator	= Math.max( lastDot, lastColon );

		if ( lastSeparator >= 0 && lastSeparator < fullPath.length() - 1 ) {
			return fullPath.substring( lastSeparator + 1 );
		}
		return fullPath;
	}

	/**
	 * Extract class name from an extends/implements annotation.
	 */
	private String extractClassNameFromAnnotation( BoxAnnotation annotation ) {
		String key = annotation.getKey().getValue().toLowerCase();

		if ( !key.equals( "extends" ) && !key.equals( "implements" ) ) {
			return null;
		}

		// Extract class/interface name from annotation value
		String className = null;
		if ( annotation.getValue() instanceof BoxStringLiteral strLiteral ) {
			className = strLiteral.getValue();
		} else if ( annotation.getValue() instanceof BoxFQN fqn ) {
			className = extractClassNameFromFQN( fqn );
		} else if ( annotation.getValue() != null ) {
			className	= annotation.getValue().getSourceText();
			// Clean up quotes if present
			className	= className.replace( "\"", "" ).replace( "'", "" );
		}

		if ( className == null || className.isEmpty() ) {
			return null;
		}

		// Handle comma-separated list for implements (return first one for now)
		if ( className.contains( "," ) ) {
			String[] classes = className.split( "," );
			className = classes[ 0 ].trim();
		}

		return className;
	}

	/**
	 * Find a class by name with relative path resolution support.
	 * Tries simple name, FQN, and relative package resolution.
	 */
	private java.util.Optional<IndexedClass> findClassByNameWithRelativeResolution( String className, URI docURI ) {
		if ( className == null || className.isEmpty() ) {
			return java.util.Optional.empty();
		}

		ProjectIndex	index		= getIndex();
		var				classOpt	= index.findClassByName( className );

		// Try by FQN if simple name lookup failed
		if ( classOpt.isEmpty() ) {
			classOpt = index.findClassByFQN( className );
		}

		// If still not found and className contains a dot (potential relative path),
		// try resolving relative to the current file's package
		if ( classOpt.isEmpty() && className.contains( "." ) && docURI != null ) {
			String currentPackage = getCurrentPackageFromURI( docURI );
			if ( currentPackage != null && !currentPackage.isEmpty() ) {
				String qualifiedName = currentPackage + "." + className;
				classOpt = index.findClassByFQN( qualifiedName );
			}
		}

		return classOpt;
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

		StringBuilder	sb				= new StringBuilder();
		String[]		lines			= documentation.split( "\n" );

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
