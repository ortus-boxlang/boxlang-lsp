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

public class VariableCompletionTest extends BaseTest {

	private static Path				projectRoot;
	private static Path				testDir;
	private ProjectContextProvider	pcp;

	@BeforeAll
	public static void setUpClass() {
		projectRoot	= Paths.get( System.getProperty( "user.dir" ) );
		testDir		= projectRoot.resolve( "src/test/resources/files/variableCompletionTest" );
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
	void testLocalVariableCompletion() {
		Path					testFile	= testDir.resolve( "VariableTest.bx" );
		File					f			= testFile.toFile();

		CompletionParams		params		= new CompletionParams();
		TextDocumentIdentifier	td			= new TextDocumentIdentifier( testFile.toUri().toString() );
		params.setTextDocument( td );
		// Inside init method, after variables are declared
		params.setPosition( new Position( 19, 2 ) );

		List<CompletionItem> items = pcp.getAvailableCompletions( f.toURI(), params );

		// Should include localGreeting
		assertThat( hasItem( items, "localGreeting" ) ).isTrue();
		// Should include initialName (argument)
		assertThat( hasItem( items, "initialName" ) ).isTrue();
		// Should include age (argument)
		assertThat( hasItem( items, "age" ) ).isTrue();
		// Should include combined
		assertThat( hasItem( items, "combined" ) ).isTrue();
	}

	@Test
	void testLoopVariableCompletion() {
		Path					testFile	= testDir.resolve( "VariableTest.bx" );
		File					f			= testFile.toFile();

		CompletionParams		params		= new CompletionParams();
		TextDocumentIdentifier	td			= new TextDocumentIdentifier( testFile.toUri().toString() );
		params.setTextDocument( td );
		// Inside for loop
		params.setPosition( new Position( 9, 3 ) );

		List<CompletionItem> items = pcp.getAvailableCompletions( f.toURI(), params );

		// Should include i (loop iterator)
		assertThat( hasItem( items, "i" ) ).isTrue();
		// Should include loopVar
		assertThat( hasItem( items, "loopVar" ) ).isTrue();
	}

	@Test
	void testCatchVariableCompletion() {
		Path					testFile	= testDir.resolve( "VariableTest.bx" );
		File					f			= testFile.toFile();

		CompletionParams		params		= new CompletionParams();
		TextDocumentIdentifier	td			= new TextDocumentIdentifier( testFile.toUri().toString() );
		params.setTextDocument( td );
		// Inside catch block
		params.setPosition( new Position( 16, 3 ) );

		List<CompletionItem> items = pcp.getAvailableCompletions( f.toURI(), params );

		// Should include e (exception variable)
		assertThat( hasItem( items, "e" ) ).isTrue();
	}

	@Test
	void testScopeKeywordCompletion() {
		Path					testFile	= testDir.resolve( "VariableTest.bx" );
		File					f			= testFile.toFile();

		CompletionParams		params		= new CompletionParams();
		TextDocumentIdentifier	td			= new TextDocumentIdentifier( testFile.toUri().toString() );
		params.setTextDocument( td );
		params.setPosition( new Position( 19, 2 ) );

		List<CompletionItem> items = pcp.getAvailableCompletions( f.toURI(), params );

		// Should include scope keywords
		assertThat( hasItem( items, "arguments" ) ).isTrue();
		assertThat( hasItem( items, "variables" ) ).isTrue();
		assertThat( hasItem( items, "this" ) ).isTrue();
		assertThat( hasItem( items, "local" ) ).isTrue();
	}

	@Test
	void testInjectedVariables() {
		// Test for variables like "cgi", "url", "form" etc.
		Path					testFile	= testDir.resolve( "VariableTest.bx" );
		File					f			= testFile.toFile();

		CompletionParams		params		= new CompletionParams();
		TextDocumentIdentifier	td			= new TextDocumentIdentifier( testFile.toUri().toString() );
		params.setTextDocument( td );
		params.setPosition( new Position( 19, 2 ) );

		List<CompletionItem> items = pcp.getAvailableCompletions( f.toURI(), params );

		assertThat( hasItem( items, "cgi" ) ).isTrue();
		assertThat( hasItem( items, "url" ) ).isTrue();
		assertThat( hasItem( items, "form" ) ).isTrue();
	}

	private boolean hasItem( List<CompletionItem> items, String label ) {
		return items.stream().anyMatch( item -> item.getLabel().equalsIgnoreCase( label ) );
	}
}
