package ortus.boxlang.lsp.workspace;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;

import com.google.gson.JsonObject;

import ortus.boxlang.compiler.ast.BoxNode;
import ortus.boxlang.compiler.ast.Point;
import ortus.boxlang.compiler.ast.expression.BoxFunctionInvocation;
import ortus.boxlang.compiler.ast.expression.BoxMethodInvocation;
import ortus.boxlang.compiler.ast.visitor.PrettyPrintBoxVisitor;
import ortus.boxlang.lsp.workspace.codeLens.CodeLensFacts;
import ortus.boxlang.lsp.workspace.codeLens.CodeLensRuleBook;
import ortus.boxlang.lsp.workspace.completion.CompletionFacts;
import ortus.boxlang.lsp.workspace.completion.CompletionProviderRuleBook;
import ortus.boxlang.lsp.workspace.visitors.DefinitionTargetVisitor;

public class ProjectContextProvider {

	static ProjectContextProvider		instance;
	private List<WorkspaceFolder>		workspaceFolders			= new ArrayList<WorkspaceFolder>();
	private LanguageClient				client;
	private Map<URI, FileParseResult>	parsedFiles					= new HashMap<URI, FileParseResult>();
	private Map<URI, FileParseResult>	openDocuments				= new HashMap<URI, FileParseResult>();
	private List<FunctionDefinition>	functionDefinitions			= new ArrayList<FunctionDefinition>();

	private boolean						shouldPublishDiagnostics	= false;

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

	public Map<String, Path> getMappings() {
		return new HashMap<>();
	}

	public List<Diagnostic> getFileDiagnostics( URI docURI ) {
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

		// TODO implement this differently

		this.parsedFiles.keySet()
		    .stream()
		    .parallel()
		    .forEach( ( uri ) -> publishDiagnostics( uri ) );
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
				this.openDocuments.put( docUri, FileParseResult.fromSourceString( docUri, change.getText() ) );
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

		this.openDocuments.put( docUri, FileParseResult.fromSourceString( docUri, fileContent ) );
	}

	public void trackDocumentOpen( URI docUri, String text ) {
		this.openDocuments.put( docUri, FileParseResult.fromSourceString( docUri, text ) );
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

		return Optional.of( result );
	}

	public Optional<List<Either<SymbolInformation, DocumentSymbol>>> getDocumentSymbols( URI docURI ) {
		return getLatestFileParseResult( docURI )
		    .map( ( res ) -> res.getOutline() );
	}

	public List<Location> findMatchingFunctionDeclarations( URI docURI, String functionName ) {
		return this.functionDefinitions.stream()
		    .filter( ( fn ) -> fn.getFunctionName().equals( functionName ) && docURI.equals( fn.getFileURI() ) )
		    .map( FunctionDefinition::getLocation )
		    .toList();
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
			    } else if ( node instanceof BoxMethodInvocation methodUse ) {
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

	public Optional<BoxNode> findDefinitionTarget( URI docURI, Position position ) {
		return getLatestFileParseResult( docURI )
		    .flatMap( fpr -> fpr.findAstRoot() )
		    .map( ( rootNode ) -> {
			    return searchForCursorTarget( rootNode, position );
		    } );

	}

	private BoxNode searchForCursorTarget( BoxNode root, Position position ) {
		DefinitionTargetVisitor visitor = new DefinitionTargetVisitor( position );

		root.accept( visitor );

		return visitor.getDefinitionTarget();
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
					Map<String, Object>	cadData					= ( Map ) cad.getData();
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
}
