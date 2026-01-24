package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertThat;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ortus.boxlang.lsp.workspace.ProjectContextProvider;
import ortus.boxlang.lsp.workspace.index.ProjectIndex;

public class FunctionCompletionTest extends BaseTest {

	private static Path				projectRoot;
	private static Path				testDir;
	private ProjectContextProvider	pcp;

	@BeforeAll
	public static void setUpClass() {
		projectRoot	= Paths.get( System.getProperty( "user.dir" ) );
		testDir		= projectRoot.resolve( "src/test/resources/files/functionCompletionTest" );
	}

	@BeforeEach
	public void setUp() throws Exception {
		pcp = ProjectContextProvider.getInstance();

		// Initialize index with test files
		ProjectIndex index = pcp.getIndex();
		index.clear();
		index.initialize( testDir );

		// Index all test files
		for ( File file : testDir.toFile().listFiles() ) {
			if ( file.getName().endsWith( ".bx" ) ) {
				index.indexFile( file.toURI() );
				pcp.trackDocumentOpen( file.toURI(), Files.readString( file.toPath() ) );
			}
		}
	}

	@Test
	public void testBIFCompletionInGeneralContext() {
		Path					testFile	= testDir.resolve( "User.bx" );
		CompletionParams		params		= new CompletionParams();
		TextDocumentIdentifier	td			= new TextDocumentIdentifier( testFile.toUri().toString() );
		params.setTextDocument( td );
		params.setPosition( new Position( 30, 8 ) ); // Inside testFunctionCalls()

		List<CompletionItem> items = pcp.getAvailableCompletions( testFile.toUri(), params );

		// Verify BIFs are present
		assertThat( hasItem( items, "arrayAppend" ) ).isTrue();
		assertThat( hasItem( items, "len" ) ).isTrue();
		assertThat( hasItem( items, "structNew" ) ).isTrue();
	}

	@Test
	public void testUDFCompletionInSameFile() {
		Path					testFile	= testDir.resolve( "User.bx" );
		CompletionParams		params		= new CompletionParams();
		TextDocumentIdentifier	td			= new TextDocumentIdentifier( testFile.toUri().toString() );
		params.setTextDocument( td );
		params.setPosition( new Position( 30, 8 ) ); // Inside testFunctionCalls()

		List<CompletionItem> items = pcp.getAvailableCompletions( testFile.toUri(), params );

		// Verify UDFs from same file are present
		assertThat( hasItem( items, "setName" ) ).isTrue();
		assertThat( hasItem( items, "getName" ) ).isTrue();
		assertThat( hasItem( items, "setAge" ) ).isTrue();
		assertThat( hasItem( items, "getAge" ) ).isTrue();
		assertThat( hasItem( items, "validateName" ) ).isTrue();
	}

	@Test
	public void testFunctionCompletionHasSignature() {
		Path					testFile	= testDir.resolve( "User.bx" );
		CompletionParams		params		= new CompletionParams();
		TextDocumentIdentifier	td			= new TextDocumentIdentifier( testFile.toUri().toString() );
		params.setTextDocument( td );
		params.setPosition( new Position( 30, 8 ) ); // Inside testFunctionCalls()

		List<CompletionItem>	items		= pcp.getAvailableCompletions( testFile.toUri(), params );

		// Find setName function and verify it has a signature
		CompletionItem			setNameItem	= findItem( items, "setName" );
		assertThat( setNameItem ).isNotNull();
		assertThat( setNameItem.getDetail() ).isNotNull();
		assertThat( setNameItem.getDetail() ).contains( "setName" );
		assertThat( setNameItem.getDetail() ).contains( "name" );
	}

	@Test
	public void testFunctionWithReturnTypeHint() {
		Path					testFile	= testDir.resolve( "User.bx" );
		CompletionParams		params		= new CompletionParams();
		TextDocumentIdentifier	td			= new TextDocumentIdentifier( testFile.toUri().toString() );
		params.setTextDocument( td );
		params.setPosition( new Position( 30, 8 ) ); // Inside testFunctionCalls()

		List<CompletionItem>	items		= pcp.getAvailableCompletions( testFile.toUri(), params );

		// Find getName which has a string return type
		CompletionItem			getNameItem	= findItem( items, "getName" );
		assertThat( getNameItem ).isNotNull();
		assertThat( getNameItem.getDetail() ).isNotNull();
		assertThat( getNameItem.getDetail() ).contains( "string" );
	}

	@Test
	public void testFunctionHasSnippetInsertText() {
		Path					testFile	= testDir.resolve( "User.bx" );
		CompletionParams		params		= new CompletionParams();
		TextDocumentIdentifier	td			= new TextDocumentIdentifier( testFile.toUri().toString() );
		params.setTextDocument( td );
		params.setPosition( new Position( 30, 8 ) ); // Inside testFunctionCalls()

		List<CompletionItem>	items		= pcp.getAvailableCompletions( testFile.toUri(), params );

		// Verify UDFs have snippet insert text
		CompletionItem			setNameItem	= findItem( items, "setName" );
		assertThat( setNameItem ).isNotNull();
		assertThat( setNameItem.getInsertText() ).contains( "setName" );
		assertThat( setNameItem.getInsertText() ).contains( "(" );
		assertThat( setNameItem.getInsertText() ).contains( "$1" ); // Snippet placeholder
	}

	@Test
	public void testBIFHasSignature() {
		Path					testFile	= testDir.resolve( "User.bx" );
		CompletionParams		params		= new CompletionParams();
		TextDocumentIdentifier	td			= new TextDocumentIdentifier( testFile.toUri().toString() );
		params.setTextDocument( td );
		params.setPosition( new Position( 30, 8 ) ); // Inside testFunctionCalls()

		List<CompletionItem>	items	= pcp.getAvailableCompletions( testFile.toUri(), params );

		// Verify BIFs have signatures
		CompletionItem			lenItem	= findItem( items, "len" );
		assertThat( lenItem ).isNotNull();
		assertThat( lenItem.getDetail() ).isNotNull();
		assertThat( lenItem.getDetail().toLowerCase() ).contains( "len" );
		assertThat( lenItem.getDetail() ).contains( "(" );
	}

	@Test
	public void testUDFsSortBeforeBIFs() {
		Path					testFile	= testDir.resolve( "User.bx" );
		CompletionParams		params		= new CompletionParams();
		TextDocumentIdentifier	td			= new TextDocumentIdentifier( testFile.toUri().toString() );
		params.setTextDocument( td );
		params.setPosition( new Position( 30, 8 ) ); // Inside testFunctionCalls()

		List<CompletionItem>	items		= pcp.getAvailableCompletions( testFile.toUri(), params );

		// Verify sort order
		CompletionItem			setNameItem	= findItem( items, "setName" );
		CompletionItem			lenItem		= findItem( items, "len" );

		assertThat( setNameItem ).isNotNull();
		assertThat( lenItem ).isNotNull();

		// UDFs should have sortText starting with "2", BIFs with "5"
		assertThat( setNameItem.getSortText() ).startsWith( "2" );
		assertThat( lenItem.getSortText() ).startsWith( "5" );
	}

	@Test
	public void testPrivateFunctionIncluded() {
		Path					testFile	= testDir.resolve( "User.bx" );
		CompletionParams		params		= new CompletionParams();
		TextDocumentIdentifier	td			= new TextDocumentIdentifier( testFile.toUri().toString() );
		params.setTextDocument( td );
		params.setPosition( new Position( 30, 8 ) ); // Inside testFunctionCalls()

		List<CompletionItem> items = pcp.getAvailableCompletions( testFile.toUri(), params );

		// Private functions should be included when completing in the same class
		assertThat( hasItem( items, "validateName" ) ).isTrue();
	}

	@Test
	public void testNoCompletionInStringLiteral() {
		Path					testFile	= testDir.resolve( "User.bx" );
		CompletionParams		params		= new CompletionParams();
		TextDocumentIdentifier	td			= new TextDocumentIdentifier( testFile.toUri().toString() );
		params.setTextDocument( td );
		// Position inside a string would need special test file setup
		// This is a placeholder for when we add that capability
	}

	private boolean hasItem( List<CompletionItem> items, String label ) {
		return items.stream().anyMatch( item -> item.getLabel().equalsIgnoreCase( label ) );
	}

	private CompletionItem findItem( List<CompletionItem> items, String label ) {
		return items.stream()
		    .filter( item -> item.getLabel().equalsIgnoreCase( label ) )
		    .findFirst()
		    .orElse( null );
	}
}
