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
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ortus.boxlang.lsp.workspace.ProjectContextProvider;
import ortus.boxlang.lsp.workspace.index.ProjectIndex;

public class KeywordCompletionTest extends BaseTest {

	private static Path				projectRoot;
	private static Path				testDir;
	private ProjectContextProvider	pcp;

	@BeforeAll
	public static void setUpClass() {
		projectRoot	= Paths.get( System.getProperty( "user.dir" ) );
		testDir		= projectRoot.resolve( "src/test/resources/files/keywordCompletionTest" );
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
	public void testTopLevelKeywords() {
		Path					testFile	= testDir.resolve( "TopLevel.bx" );
		CompletionParams		params		= new CompletionParams();
		TextDocumentIdentifier	td			= new TextDocumentIdentifier( testFile.toUri().toString() );
		params.setTextDocument( td );
		params.setPosition( new Position( 4, 0 ) ); // Top level - line 5 (blank line after comment)

		List<CompletionItem> items = pcp.getAvailableCompletions( testFile.toUri(), params );

		// Verify top-level keywords are present
		assertThat( hasKeyword( items, "class" ) ).isTrue();
		assertThat( hasKeyword( items, "interface" ) ).isTrue();
		assertThat( hasKeyword( items, "abstract" ) ).isTrue();
		assertThat( hasKeyword( items, "final" ) ).isTrue();
		assertThat( hasKeyword( items, "import" ) ).isTrue();
		assertThat( hasKeyword( items, "extends" ) ).isTrue();
		assertThat( hasKeyword( items, "implements" ) ).isTrue();
	}

	@Test
	public void testClassBodyKeywords() {
		Path					testFile	= testDir.resolve( "ClassBody.bx" );
		CompletionParams		params		= new CompletionParams();
		TextDocumentIdentifier	td			= new TextDocumentIdentifier( testFile.toUri().toString() );
		params.setTextDocument( td );
		params.setPosition( new Position( 4, 1 ) ); // Inside class body

		List<CompletionItem> items = pcp.getAvailableCompletions( testFile.toUri(), params );

		// Verify class body keywords are present
		assertThat( hasKeyword( items, "function" ) ).isTrue();
		assertThat( hasKeyword( items, "property" ) ).isTrue();
		assertThat( hasKeyword( items, "static" ) ).isTrue();
		assertThat( hasKeyword( items, "private" ) ).isTrue();
		assertThat( hasKeyword( items, "public" ) ).isTrue();
		assertThat( hasKeyword( items, "remote" ) ).isTrue();
		assertThat( hasKeyword( items, "required" ) ).isTrue();
	}

	@Test
	public void testFunctionBodyKeywords() {
		Path					testFile	= testDir.resolve( "FunctionBody.bx" );
		CompletionParams		params		= new CompletionParams();
		TextDocumentIdentifier	td			= new TextDocumentIdentifier( testFile.toUri().toString() );
		params.setTextDocument( td );
		params.setPosition( new Position( 5, 2 ) ); // Inside function body

		List<CompletionItem> items = pcp.getAvailableCompletions( testFile.toUri(), params );

		// Verify function body keywords are present
		assertThat( hasKeyword( items, "var" ) ).isTrue();
		assertThat( hasKeyword( items, "if" ) ).isTrue();
		assertThat( hasKeyword( items, "else" ) ).isTrue();
		assertThat( hasKeyword( items, "for" ) ).isTrue();
		assertThat( hasKeyword( items, "while" ) ).isTrue();
		assertThat( hasKeyword( items, "do" ) ).isTrue();
		assertThat( hasKeyword( items, "switch" ) ).isTrue();
		assertThat( hasKeyword( items, "try" ) ).isTrue();
		assertThat( hasKeyword( items, "catch" ) ).isTrue();
		assertThat( hasKeyword( items, "finally" ) ).isTrue();
		assertThat( hasKeyword( items, "throw" ) ).isTrue();
		assertThat( hasKeyword( items, "return" ) ).isTrue();
		assertThat( hasKeyword( items, "break" ) ).isTrue();
		assertThat( hasKeyword( items, "continue" ) ).isTrue();
		assertThat( hasKeyword( items, "required" ) ).isTrue();
	}

	@Test
	public void testExpressionKeywords() {
		Path					testFile	= testDir.resolve( "ExpressionContext.bx" );
		CompletionParams		params		= new CompletionParams();
		TextDocumentIdentifier	td			= new TextDocumentIdentifier( testFile.toUri().toString() );
		params.setTextDocument( td );
		params.setPosition( new Position( 5, 12 ) ); // After "var x = " at word "cursor"

		List<CompletionItem> items = pcp.getAvailableCompletions( testFile.toUri(), params );

		// Verify expression keywords are present
		assertThat( hasKeyword( items, "new" ) ).isTrue();
		assertThat( hasKeyword( items, "true" ) ).isTrue();
		assertThat( hasKeyword( items, "false" ) ).isTrue();
		assertThat( hasKeyword( items, "null" ) ).isTrue();
	}

	@Test
	public void testKeywordsHaveCorrectKind() {
		Path					testFile	= testDir.resolve( "FunctionBody.bx" );
		CompletionParams		params		= new CompletionParams();
		TextDocumentIdentifier	td			= new TextDocumentIdentifier( testFile.toUri().toString() );
		params.setTextDocument( td );
		params.setPosition( new Position( 5, 2 ) ); // Inside function body

		List<CompletionItem>	items		= pcp.getAvailableCompletions( testFile.toUri(), params );

		// Find a keyword and verify it has the correct kind
		CompletionItem			ifKeyword	= findKeyword( items, "if" );
		assertThat( ifKeyword ).isNotNull();
		assertThat( ifKeyword.getKind() ).isEqualTo( CompletionItemKind.Keyword );
	}

	@Test
	public void testKeywordsSortCorrectly() {
		Path					testFile	= testDir.resolve( "FunctionBody.bx" );
		CompletionParams		params		= new CompletionParams();
		TextDocumentIdentifier	td			= new TextDocumentIdentifier( testFile.toUri().toString() );
		params.setTextDocument( td );
		params.setPosition( new Position( 5, 2 ) ); // Inside function body

		List<CompletionItem>	items		= pcp.getAvailableCompletions( testFile.toUri(), params );

		// Keywords should have a sortText that prioritizes them
		CompletionItem			ifKeyword	= findKeyword( items, "if" );
		assertThat( ifKeyword ).isNotNull();
		assertThat( ifKeyword.getSortText() ).isNotNull();
		// Keywords should sort before most other items (using "1" prefix)
		assertThat( ifKeyword.getSortText() ).startsWith( "1" );
	}

	@Test
	public void testTopLevelDoesNotIncludeFunctionBodyKeywords() {
		Path					testFile	= testDir.resolve( "TopLevel.bx" );
		CompletionParams		params		= new CompletionParams();
		TextDocumentIdentifier	td			= new TextDocumentIdentifier( testFile.toUri().toString() );
		params.setTextDocument( td );
		params.setPosition( new Position( 4, 0 ) ); // Top level

		List<CompletionItem> items = pcp.getAvailableCompletions( testFile.toUri(), params );

		// Function body keywords should NOT be in top level
		assertThat( hasKeyword( items, "if" ) ).isFalse();
		assertThat( hasKeyword( items, "for" ) ).isFalse();
		assertThat( hasKeyword( items, "while" ) ).isFalse();
		assertThat( hasKeyword( items, "return" ) ).isFalse();
	}

	@Test
	public void testClassBodyDoesNotIncludeFunctionBodyKeywords() {
		Path					testFile	= testDir.resolve( "ClassBody.bx" );
		CompletionParams		params		= new CompletionParams();
		TextDocumentIdentifier	td			= new TextDocumentIdentifier( testFile.toUri().toString() );
		params.setTextDocument( td );
		params.setPosition( new Position( 4, 1 ) ); // Inside class body

		List<CompletionItem> items = pcp.getAvailableCompletions( testFile.toUri(), params );

		// Function body keywords should NOT be in class body
		assertThat( hasKeyword( items, "if" ) ).isFalse();
		assertThat( hasKeyword( items, "for" ) ).isFalse();
		assertThat( hasKeyword( items, "while" ) ).isFalse();
		assertThat( hasKeyword( items, "return" ) ).isFalse();
	}

	@Test
	public void testFunctionBodyIncludesExpressionKeywords() {
		Path					testFile	= testDir.resolve( "FunctionBody.bx" );
		CompletionParams		params		= new CompletionParams();
		TextDocumentIdentifier	td			= new TextDocumentIdentifier( testFile.toUri().toString() );
		params.setTextDocument( td );
		params.setPosition( new Position( 5, 2 ) ); // Inside function body

		List<CompletionItem> items = pcp.getAvailableCompletions( testFile.toUri(), params );

		// Expression keywords should also be available in function bodies
		assertThat( hasKeyword( items, "new" ) ).isTrue();
		assertThat( hasKeyword( items, "true" ) ).isTrue();
		assertThat( hasKeyword( items, "false" ) ).isTrue();
		assertThat( hasKeyword( items, "null" ) ).isTrue();
	}

	private boolean hasKeyword( List<CompletionItem> items, String keyword ) {
		return items.stream()
		    .anyMatch( item -> item.getKind() == CompletionItemKind.Keyword
		        && item.getLabel().equalsIgnoreCase( keyword ) );
	}

	private CompletionItem findKeyword( List<CompletionItem> items, String keyword ) {
		return items.stream()
		    .filter( item -> item.getKind() == CompletionItemKind.Keyword
		        && item.getLabel().equalsIgnoreCase( keyword ) )
		    .findFirst()
		    .orElse( null );
	}
}
