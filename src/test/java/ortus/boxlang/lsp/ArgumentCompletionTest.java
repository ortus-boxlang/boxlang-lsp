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

/**
 * Tests for argument completion in function calls.
 * Task 3.8: Completion - Arguments in Function Calls
 */
public class ArgumentCompletionTest extends BaseTest {

	private static Path				projectRoot;
	private static Path				testDir;
	private ProjectContextProvider	pcp;

	@BeforeAll
	public static void setUpClass() {
		projectRoot	= Paths.get( System.getProperty( "user.dir" ) );
		testDir		= projectRoot.resolve( "src/test/resources/files/argumentCompletionTest" );
	}

	@BeforeEach
	public void setUp() throws Exception {
		pcp = ProjectContextProvider.getInstance();

		// Initialize index with test files
		ProjectIndex index = pcp.getIndex();
		index.clear();
		index.initialize( testDir );

		// Index test files
		for ( File file : testDir.toFile().listFiles() ) {
			if ( file.getName().endsWith( ".bx" ) ) {
				index.indexFile( file.toURI() );
			}
		}
	}

	// ==================== Named Argument Completion Tests ====================

	@Test
	public void testNamedArgumentsInEmptyCall() throws Exception {
		// Test: createUser(|) should suggest name=, age=, active=
		List<CompletionItem> items = getCompletionsWithModifiedLine(
			"var test1 = createUser(\"John\", 30, true);",
			"var test1 = createUser();",
			28, 25
		);

		// Should suggest all parameter names with trailing equals
		assertThat( hasItem( items, "name=" ) ).isTrue();
		assertThat( hasItem( items, "age=" ) ).isTrue();
		assertThat( hasItem( items, "active=" ) ).isTrue();
	}

	@Test
	public void testNamedArgumentsAfterFirstArgument() throws Exception {
		// Test: createUser(name="Jane", |) should suggest age=, active= (not name=)
		List<CompletionItem> items = getCompletionsWithModifiedLine(
			"var test2 = createUser(name=\"Jane\", age=25, active=false);",
			"var test2 = createUser(name=\"Jane\", );",
			29, 37
		);

		// Should suggest remaining parameters
		assertThat( hasItem( items, "age=" ) ).isTrue();
		assertThat( hasItem( items, "active=" ) ).isTrue();

		// Should NOT suggest already-used parameter
		assertThat( hasItem( items, "name=" ) ).isFalse();
	}

	@Test
	public void testNamedArgumentsHaveCorrectKind() throws Exception {
		List<CompletionItem> items = getCompletionsWithModifiedLine(
			"var test1 = createUser(\"John\", 30, true);",
			"var test1 = createUser();",
			28, 25
		);

		CompletionItem nameItem = findItem( items, "name=" );
		assertThat( nameItem ).isNotNull();
		assertThat( nameItem.getKind() ).isEqualTo( CompletionItemKind.Field );
	}

	@Test
	public void testRequiredParametersMarked() throws Exception {
		List<CompletionItem> items = getCompletionsWithModifiedLine(
			"var test1 = createUser(\"John\", 30, true);",
			"var test1 = createUser();",
			28, 25
		);

		CompletionItem nameItem = findItem( items, "name=" );
		assertThat( nameItem ).isNotNull();
		// Detail should indicate it's required and show type
		assertThat( nameItem.getDetail() ).contains( "required" );
		assertThat( nameItem.getDetail() ).contains( "string" );

		CompletionItem ageItem = findItem( items, "age=" );
		assertThat( ageItem ).isNotNull();
		assertThat( ageItem.getDetail() ).contains( "required" );
		assertThat( ageItem.getDetail() ).contains( "numeric" );

		CompletionItem activeItem = findItem( items, "active=" );
		assertThat( activeItem ).isNotNull();
		// Optional parameter should NOT have "required" in detail
		assertThat( activeItem.getDetail() == null || !activeItem.getDetail().contains( "required" ) ).isTrue();
		assertThat( activeItem.getDetail() ).contains( "boolean" );
	}

	// ==================== Helper Methods ====================

	/**
	 * Helper method to get completions after modifying a specific line in the test file.
	 */
	private List<CompletionItem> getCompletionsWithModifiedLine(String oldLine, String newLine, int line, int column) throws Exception {
		Path	testFile	= testDir.resolve( "TestClass.bx" );
		File	f			= testFile.toFile();
		
		// Read and modify content
		String content = Files.readString( testFile );
		content = content.replace(oldLine, newLine);
		
		// Track the modified content
		pcp.trackDocumentOpen( f.toURI(), content );
		
		// Create completion request
		CompletionParams		params	= new CompletionParams();
		TextDocumentIdentifier	td		= new TextDocumentIdentifier( testFile.toUri().toString() );
		params.setTextDocument( td );
		params.setPosition( new Position( line, column ) );

		return pcp.getAvailableCompletions( f.toURI(), params );
	}

	private boolean hasItem( List<CompletionItem> items, String label ) {
		return items.stream().anyMatch( item -> item.getLabel().equals( label ) );
	}

	private CompletionItem findItem( List<CompletionItem> items, String label ) {
		return items.stream()
		    .filter( item -> item.getLabel().equals( label ) )
		    .findFirst()
		    .orElse( null );
	}
}
