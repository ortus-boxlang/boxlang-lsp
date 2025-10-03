package ortus.boxlang.lsp.workspace;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import ortus.boxlang.compiler.ast.BoxNode;
import ortus.boxlang.compiler.ast.Issue;
import ortus.boxlang.compiler.ast.statement.BoxFunctionDeclaration;
import ortus.boxlang.compiler.parser.Parser;
import ortus.boxlang.compiler.parser.ParsingResult;
import ortus.boxlang.lsp.App;
import ortus.boxlang.lsp.DocumentSymbolBoxNodeVisitor;
import ortus.boxlang.lsp.SourceCodeVisitor;
import ortus.boxlang.lsp.SourceCodeVisitorService;
import ortus.boxlang.lsp.workspace.types.ParsedProperty;
import ortus.boxlang.lsp.workspace.visitors.FunctionReturnDiagnosticVisitor;
import ortus.boxlang.lsp.workspace.visitors.PropertyVisitor;

public class FileParseResult {

	private URI												uri;
	private boolean											isOpen				= false;
	private String											source				= null;
	private WeakReference<ParsingResult>					parseResultRef		= new WeakReference<ParsingResult>( null );
	private List<Issue>										issues				= new ArrayList<Issue>();
	private List<Diagnostic>								diagnostics			= new ArrayList<Diagnostic>();
	private List<CodeAction>								codeActions			= new ArrayList<CodeAction>();
	private List<Either<SymbolInformation, DocumentSymbol>>	outline				= new ArrayList<Either<SymbolInformation, DocumentSymbol>>();
	private List<ParsedProperty>							properties			= new ArrayList<ParsedProperty>();
	private List<SourceCodeVisitor>							visitors			= new ArrayList<SourceCodeVisitor>();
	private List<FunctionDefinition>						functionDefinitions	= new ArrayList<FunctionDefinition>();

	public static FileParseResult fromFileSystem( URI uri ) {
		FileParseResult fpr = new FileParseResult();
		fpr.uri = uri;

		fpr.fullyParse();

		return fpr;
	}

	public static FileParseResult fromSourceString( URI uri, String source ) {
		FileParseResult fpr = new FileParseResult();
		fpr.uri		= uri;
		fpr.source	= source;
		fpr.isOpen	= true;

		fpr.fullyParse();

		return fpr;
	}

	public URI getURI() {
		return uri;
	}

	public List<ParsedProperty> properties() {
		return properties;
	}

	public List<Either<SymbolInformation, DocumentSymbol>> getOutline() {
		return outline;
	}

	public List<Diagnostic> getDiagnostics() {
		return diagnostics;
	}

	public List<CodeAction> getCodeActions() {
		return codeActions;
	}

	public List<Issue> getIssues() {
		return issues;
	}

	public String readLine( int lineNumber ) {
		Stream<String> lineStream = Stream.ofNullable( null );

		if ( this.isOpen ) {
			lineStream = List.of( this.source.split( "\n" ) )
			    .stream();
		} else {
			try {
				lineStream = Files.lines( Path.of( this.uri ) );
			} catch ( IOException e ) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return "";
			}
		}

		if ( lineNumber < 0 ) {
			return "";
		}

		return lineStream.skip( lineNumber ).findFirst().orElse( "" );
	}

	public boolean isTemplate() {
		return uri.toString().endsWith( ".bxm" );
	}

	public boolean isClass() {
		return uri.toString().endsWith( ".bx" );
	}

	public boolean isCF() {
		return uri.toString().endsWith( ".cfc" ) || uri.toString().endsWith( ".cfm" ) || uri.toString().endsWith( ".cfml" );
	}

	public Optional<BoxFunctionDeclaration> getMainFunction() {
		return findAstRoot()
		    .map( root -> {
			    var funcs = root.getDescendantsOfType( BoxFunctionDeclaration.class, ( n ) -> n.getName().equalsIgnoreCase( "main" ) );

			    return funcs.size() == 0 ? null : funcs.getLast();
		    } );
	}

	public Optional<ParsingResult> getParsingResult() {
		return findParsingResult();
	}

	public Optional<BoxNode> findAstRoot() {
		return findParsingResult()
		    .map( ParsingResult::getRoot );
	}

	private Optional<ParsingResult> findParsingResult() {

		if ( parseResultRef.get() == null ) {
			parseResultRef = new WeakReference<>( parseSource() );
		}

		return Optional.ofNullable( parseResultRef.get() );
	}

	private ParsingResult parseSource() {
		Parser parser = new Parser();

		try {
			if ( this.isOpen ) {
				return parser.parse(
				    this.source,
				    Parser.detectFile( new File( this.uri ) ),
				    Parser.getFileExtension( this.uri.toString() ).orElseGet( () -> "bxs" ).matches( "cfc|bx" ) );
			}

			return parser.parse( Paths.get( this.uri ).toFile() );
		} catch ( Exception e ) {
			App.logger.error( "Unable to parse " + this.uri, e );
			return null;
		}
	}

	private List<Diagnostic> generateDiagnostics() {

		List<Diagnostic>	fileDiagnostics	= new ArrayList<>();
		List<CodeAction>	fileCodeActions	= new ArrayList<>();

		fileDiagnostics.addAll( issues.stream().map( ( issue ) -> {
			Diagnostic diagnostic = new Diagnostic();

			diagnostic.setSeverity( DiagnosticSeverity.Error );
			diagnostic.setMessage( issue.getMessage() );

			diagnostic.setRange( BLASTTools.positionToRange( issue.getPosition() ) );

			diagnostic.setMessage( issue.getMessage() );

			return diagnostic;
		} ).toList() );

		findAstRoot().ifPresent( astRoot -> {
			FunctionReturnDiagnosticVisitor returnDiagnosticVisitor = new FunctionReturnDiagnosticVisitor();
			astRoot.accept( returnDiagnosticVisitor );
			fileDiagnostics.addAll( returnDiagnosticVisitor.getDiagnostics() );

			List<SourceCodeVisitor> visitors = SourceCodeVisitorService.getInstance().forceVisit( this.uri.toString(),
			    astRoot );

			for ( SourceCodeVisitor visitor : visitors ) {
				if ( !visitor.canVisit( this ) ) {
					continue;
				}

				fileDiagnostics.addAll( visitor.getDiagnostics() );
				fileCodeActions.addAll( visitor.getCodeActions() );
			}
		} );

		this.codeActions = fileCodeActions;

		return fileDiagnostics;
	}

	private void fullyParse() {
		parseResultRef = new WeakReference<>( parseSource() );

		findAstRoot().ifPresent( root -> {
			properties			= parseProperties( root );
			outline				= generateOutline( this.uri, root );

			functionDefinitions	= generateFunctionDefinitions( this.uri, root );
			diagnostics			= generateDiagnostics();
		} );
	}

	private List<ParsedProperty> parseProperties( BoxNode root ) {
		PropertyVisitor visitor = new PropertyVisitor();

		root.accept( visitor );

		return visitor.getProperties();
	}

	private List<Either<SymbolInformation, DocumentSymbol>> generateOutline( URI textDocument, BoxNode root ) {
		DocumentSymbolBoxNodeVisitor visitor = new DocumentSymbolBoxNodeVisitor();

		visitor.setFilePath( Paths.get( textDocument ) );
		root.accept( visitor );

		return visitor.getDocumentSymbols();
	}

	private List<FunctionDefinition> generateFunctionDefinitions( URI textDocument, BoxNode root ) {
		FunctionDefinitionVisitor visitor = new FunctionDefinitionVisitor();

		visitor.setFileURI( textDocument );
		root.accept( visitor );

		this.functionDefinitions = this.functionDefinitions.stream().filter( ( fnDef ) -> {
			return !fnDef.getFileURI().equals( textDocument );
		} )
		    .collect( Collectors.toList() );

		return visitor.getFunctionDefinitions();
	}
}
