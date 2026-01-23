package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertThat;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ortus.boxlang.lsp.workspace.ProjectContextProvider;
import ortus.boxlang.lsp.workspace.index.ProjectIndex;

public class SnippetCompletionTest extends BaseTest {

	private static Path				projectRoot;
	private static Path				testDir;
	private ProjectContextProvider	pcp;

	@BeforeAll
	public static void setUpClass() {
		projectRoot	= Paths.get( System.getProperty( "user.dir" ) );
		testDir		= projectRoot.resolve( "src/test/resources/files/snippetCompletionTest" );
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
	public void testTopLevelSnippets() {
		Path					testFile	= testDir.resolve( "TopLevel.bx" );
		CompletionParams		params		= new CompletionParams();
		TextDocumentIdentifier	td			= new TextDocumentIdentifier( testFile.toUri().toString() );
		params.setTextDocument( td );
		params.setPosition( new Position( 4, 0 ) ); // Line 5 - blank line after comment

		List<CompletionItem> items = pcp.getAvailableCompletions( testFile.toUri(), params );

		// Verify top-level snippets are present
		assertThat( hasSnippet( items, "class" ) ).isTrue();
		assertThat( hasSnippet( items, "cls" ) ).isTrue();
		assertThat( hasSnippet( items, "interface" ) ).isTrue();
		assertThat( hasSnippet( items, "int" ) ).isTrue();
		assertThat( hasSnippet( items, "classext" ) ).isTrue();

		// Verify function body snippets are NOT present at top level
		assertThat( hasSnippet( items, "if" ) ).isFalse();
		assertThat( hasSnippet( items, "for" ) ).isFalse();
		assertThat( hasSnippet( items, "while" ) ).isFalse();
		assertThat( hasSnippet( items, "var" ) ).isFalse();
	}

	@Test
	public void testClassBodySnippets() {
		Path					testFile	= testDir.resolve( "ClassBody.bx" );
		CompletionParams		params		= new CompletionParams();
		TextDocumentIdentifier	td			= new TextDocumentIdentifier( testFile.toUri().toString() );
		params.setTextDocument( td );
		params.setPosition( new Position( 5, 1 ) ); // Inside class body

		List<CompletionItem> items = pcp.getAvailableCompletions( testFile.toUri(), params );

		// Verify class body snippets are present
		assertThat( hasSnippet( items, "fun" ) ).isTrue();
		assertThat( hasSnippet( items, "function" ) ).isTrue();
		assertThat( hasSnippet( items, "prop" ) ).isTrue();
		assertThat( hasSnippet( items, "property" ) ).isTrue();
		assertThat( hasSnippet( items, "init" ) ).isTrue();
		assertThat( hasSnippet( items, "constructor" ) ).isTrue();
		assertThat( hasSnippet( items, "get" ) ).isTrue();
		assertThat( hasSnippet( items, "getter" ) ).isTrue();
		assertThat( hasSnippet( items, "set" ) ).isTrue();
		assertThat( hasSnippet( items, "setter" ) ).isTrue();
		assertThat( hasSnippet( items, "pubfun" ) ).isTrue();
		assertThat( hasSnippet( items, "privfun" ) ).isTrue();

		// Verify top-level snippets are NOT present in class body
		assertThat( hasSnippet( items, "class" ) ).isFalse();
		assertThat( hasSnippet( items, "interface" ) ).isFalse();

		// Verify function body snippets are NOT present in class body
		assertThat( hasSnippet( items, "if" ) ).isFalse();
		assertThat( hasSnippet( items, "for" ) ).isFalse();
	}

	@Test
	public void testFunctionBodySnippets() {
		Path					testFile	= testDir.resolve( "FunctionBody.bx" );
		CompletionParams		params		= new CompletionParams();
		TextDocumentIdentifier	td			= new TextDocumentIdentifier( testFile.toUri().toString() );
		params.setTextDocument( td );
		params.setPosition( new Position( 6, 2 ) ); // Inside function body

		List<CompletionItem> items = pcp.getAvailableCompletions( testFile.toUri(), params );

		// Verify function body snippets are present
		assertThat( hasSnippet( items, "if" ) ).isTrue();
		assertThat( hasSnippet( items, "ifelse" ) ).isTrue();
		assertThat( hasSnippet( items, "for" ) ).isTrue();
		assertThat( hasSnippet( items, "forin" ) ).isTrue();
		assertThat( hasSnippet( items, "while" ) ).isTrue();
		assertThat( hasSnippet( items, "dowhile" ) ).isTrue();
		assertThat( hasSnippet( items, "try" ) ).isTrue();
		assertThat( hasSnippet( items, "trycatch" ) ).isTrue();
		assertThat( hasSnippet( items, "switch" ) ).isTrue();
		assertThat( hasSnippet( items, "var" ) ).isTrue();
		assertThat( hasSnippet( items, "return" ) ).isTrue();
		assertThat( hasSnippet( items, "throw" ) ).isTrue();

		// Verify top-level snippets are NOT present in function body
		assertThat( hasSnippet( items, "class" ) ).isFalse();
		assertThat( hasSnippet( items, "interface" ) ).isFalse();

		// Verify class body snippets are NOT present in function body
		assertThat( hasSnippet( items, "property" ) ).isFalse();
	}

	@Test
	public void testSnippetsHaveCorrectKind() {
		Path					testFile	= testDir.resolve( "FunctionBody.bx" );
		CompletionParams		params		= new CompletionParams();
		TextDocumentIdentifier	td			= new TextDocumentIdentifier( testFile.toUri().toString() );
		params.setTextDocument( td );
		params.setPosition( new Position( 6, 2 ) );

		List<CompletionItem> items = pcp.getAvailableCompletions( testFile.toUri(), params );

		// Find an if snippet
		CompletionItem ifSnippet = items.stream()
		    .filter( item -> item.getLabel().equals( "if" ) && item.getKind() == CompletionItemKind.Snippet )
		    .findFirst()
		    .orElse( null );

		assertThat( ifSnippet ).isNotNull();
		assertThat( ifSnippet.getKind() ).isEqualTo( CompletionItemKind.Snippet );
	}

	@Test
	public void testSnippetsHaveInsertTextFormat() {
		Path					testFile	= testDir.resolve( "FunctionBody.bx" );
		CompletionParams		params		= new CompletionParams();
		TextDocumentIdentifier	td			= new TextDocumentIdentifier( testFile.toUri().toString() );
		params.setTextDocument( td );
		params.setPosition( new Position( 6, 2 ) );

		List<CompletionItem> items = pcp.getAvailableCompletions( testFile.toUri(), params );

		// Find a for snippet
		CompletionItem forSnippet = items.stream()
		    .filter( item -> item.getLabel().equals( "for" ) && item.getKind() == CompletionItemKind.Snippet )
		    .findFirst()
		    .orElse( null );

		assertThat( forSnippet ).isNotNull();
		assertThat( forSnippet.getInsertTextFormat() ).isEqualTo( InsertTextFormat.Snippet );
		assertThat( forSnippet.getInsertText() ).isNotNull();
		assertThat( forSnippet.getInsertText() ).contains( "$" ); // Should contain snippet placeholders
	}

	@Test
	public void testSnippetsSortCorrectly() {
		Path					testFile	= testDir.resolve( "FunctionBody.bx" );
		CompletionParams		params		= new CompletionParams();
		TextDocumentIdentifier	td			= new TextDocumentIdentifier( testFile.toUri().toString() );
		params.setTextDocument( td );
		params.setPosition( new Position( 6, 2 ) );

		List<CompletionItem> items = pcp.getAvailableCompletions( testFile.toUri(), params );

		// Find an if snippet
		CompletionItem ifSnippet = items.stream()
		    .filter( item -> item.getLabel().equals( "if" ) && item.getKind() == CompletionItemKind.Snippet )
		    .findFirst()
		    .orElse( null );

		assertThat( ifSnippet ).isNotNull();
		assertThat( ifSnippet.getSortText() ).isNotNull();
		assertThat( ifSnippet.getSortText() ).startsWith( "0" ); // Should have high priority
	}

	@Test
	public void testFunctionSnippetContent() {
		Path					testFile	= testDir.resolve( "ClassBody.bx" );
		CompletionParams		params		= new CompletionParams();
		TextDocumentIdentifier	td			= new TextDocumentIdentifier( testFile.toUri().toString() );
		params.setTextDocument( td );
		params.setPosition( new Position( 5, 1 ) );

		List<CompletionItem> items = pcp.getAvailableCompletions( testFile.toUri(), params );

		// Find function snippet with "fun" trigger
		CompletionItem funSnippet = items.stream()
		    .filter( item -> item.getLabel().equals( "fun" ) && item.getKind() == CompletionItemKind.Snippet )
		    .findFirst()
		    .orElse( null );

		assertThat( funSnippet ).isNotNull();
		assertThat( funSnippet.getInsertText() ).contains( "function" );
		assertThat( funSnippet.getInsertText() ).contains( "${1:name}" ); // Tab stop for name
		assertThat( funSnippet.getInsertText() ).contains( "${2:params}" ); // Tab stop for params
		assertThat( funSnippet.getInsertText() ).contains( "$0" ); // Final tab stop
	}

	@Test
	public void testIfElseSnippetContent() {
		Path					testFile	= testDir.resolve( "FunctionBody.bx" );
		CompletionParams		params		= new CompletionParams();
		TextDocumentIdentifier	td			= new TextDocumentIdentifier( testFile.toUri().toString() );
		params.setTextDocument( td );
		params.setPosition( new Position( 6, 2 ) );

		List<CompletionItem> items = pcp.getAvailableCompletions( testFile.toUri(), params );

		// Find ifelse snippet
		CompletionItem ifelseSnippet = items.stream()
		    .filter( item -> item.getLabel().equals( "ifelse" ) && item.getKind() == CompletionItemKind.Snippet )
		    .findFirst()
		    .orElse( null );

		assertThat( ifelseSnippet ).isNotNull();
		assertThat( ifelseSnippet.getInsertText() ).contains( "if" );
		assertThat( ifelseSnippet.getInsertText() ).contains( "else" );
		assertThat( ifelseSnippet.getInsertText() ).contains( "${1:condition}" );
	}

	@Test
	public void testPropertySnippetContent() {
		Path					testFile	= testDir.resolve( "ClassBody.bx" );
		CompletionParams		params		= new CompletionParams();
		TextDocumentIdentifier	td			= new TextDocumentIdentifier( testFile.toUri().toString() );
		params.setTextDocument( td );
		params.setPosition( new Position( 5, 1 ) );

		List<CompletionItem> items = pcp.getAvailableCompletions( testFile.toUri(), params );

		// Find property snippet
		CompletionItem propSnippet = items.stream()
		    .filter( item -> item.getLabel().equals( "prop" ) && item.getKind() == CompletionItemKind.Snippet )
		    .findFirst()
		    .orElse( null );

		assertThat( propSnippet ).isNotNull();
		assertThat( propSnippet.getInsertText() ).contains( "property" );
		assertThat( propSnippet.getInsertText() ).contains( "${1:type}" );
		assertThat( propSnippet.getInsertText() ).contains( "${2:name}" );
	}

	@Test
	public void testTryCatchSnippetContent() {
		Path					testFile	= testDir.resolve( "FunctionBody.bx" );
		CompletionParams		params		= new CompletionParams();
		TextDocumentIdentifier	td			= new TextDocumentIdentifier( testFile.toUri().toString() );
		params.setTextDocument( td );
		params.setPosition( new Position( 6, 2 ) );

		List<CompletionItem> items = pcp.getAvailableCompletions( testFile.toUri(), params );

		// Find try snippet
		CompletionItem trySnippet = items.stream()
		    .filter( item -> item.getLabel().equals( "try" ) && item.getKind() == CompletionItemKind.Snippet )
		    .findFirst()
		    .orElse( null );

		assertThat( trySnippet ).isNotNull();
		assertThat( trySnippet.getInsertText() ).contains( "try" );
		assertThat( trySnippet.getInsertText() ).contains( "catch" );
		assertThat( trySnippet.getInsertText() ).contains( "any" );
	}

	@Test
	public void testMultipleTriggersForSameSnippet() {
		Path					testFile	= testDir.resolve( "ClassBody.bx" );
		CompletionParams		params		= new CompletionParams();
		TextDocumentIdentifier	td			= new TextDocumentIdentifier( testFile.toUri().toString() );
		params.setTextDocument( td );
		params.setPosition( new Position( 5, 1 ) );

		List<CompletionItem> items = pcp.getAvailableCompletions( testFile.toUri(), params );

		// Both "fun" and "function" should trigger the function snippet
		assertThat( hasSnippet( items, "fun" ) ).isTrue();
		assertThat( hasSnippet( items, "function" ) ).isTrue();
		assertThat( hasSnippet( items, "func" ) ).isTrue();

		// Verify they have the same body content (both create function definitions)
		CompletionItem funSnippet = items.stream()
		    .filter( item -> item.getLabel().equals( "fun" ) && item.getKind() == CompletionItemKind.Snippet )
		    .findFirst()
		    .orElse( null );

		CompletionItem functionSnippet = items.stream()
		    .filter( item -> item.getLabel().equals( "function" ) && item.getKind() == CompletionItemKind.Snippet )
		    .findFirst()
		    .orElse( null );

		assertThat( funSnippet ).isNotNull();
		assertThat( functionSnippet ).isNotNull();
		assertThat( funSnippet.getInsertText() ).isEqualTo( functionSnippet.getInsertText() );
	}

	// Helper method to check if a snippet with the given label exists
	private boolean hasSnippet( List<CompletionItem> items, String label ) {
		return items.stream()
		    .anyMatch( item -> item.getLabel().equals( label ) && item.getKind() == CompletionItemKind.Snippet );
	}
}
