package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ortus.boxlang.lsp.lint.rules.DuplicateStructKeyRule;
import ortus.boxlang.lsp.workspace.ProjectContextProvider;
import ortus.boxlang.lsp.workspace.index.ProjectIndex;
import ortus.boxlang.runtime.BoxRuntime;

/**
 * Tests for the duplicateStructKey lint rule.
 */
public class DuplicateStructKeyDiagnosticsTest extends BaseTest {

	@TempDir
	Path					tempDir;

	private ProjectIndex	index;

	static BoxRuntime		runtime;

	@BeforeAll
	static void setUpRuntime() {
		runtime = BoxRuntime.getInstance( true );
	}

	@BeforeEach
	void setUp() {
		index = new ProjectIndex();
		index.initialize( tempDir );
		ProjectContextProvider.getInstance().setIndex( index );
	}

	@Test
	void testDifferentCasingIsFlaggedOnTheRepeatedKeyOnly() throws Exception {
		String				code		= "x = { name: 1, NAME: 2 };";
		List<Diagnostic>	duplicates	= duplicateKeyDiagnostics( "Casing.bxs", code );

		assertThat( duplicates ).hasSize( 1 );
		Diagnostic duplicate = duplicates.get( 0 );
		assertThat( duplicate.getSeverity() ).isEqualTo( DiagnosticSeverity.Warning );
		assertThat( duplicate.getMessage().getLeft() ).contains( "NAME" );
		// Only the repeated key is highlighted, not the value or the whole struct
		assertThat( duplicate.getRange().getStart().getLine() ).isEqualTo( 0 );
		assertThat( duplicate.getRange().getStart().getCharacter() ).isEqualTo( code.indexOf( "NAME" ) );
		assertThat( duplicate.getRange().getEnd().getCharacter() ).isEqualTo( code.indexOf( "NAME" ) + 4 );
	}

	@Test
	void testQuotedAndBareKeysMatch() throws Exception {
		String				code		= "x = { name: 1, \"Name\": 2 }; y = { 'id' = 1, ID = 2 };";
		List<Diagnostic>	duplicates	= duplicateKeyDiagnostics( "Quoted.bxs", code );

		assertThat( startCharacters( duplicates ) ).containsExactly( code.indexOf( "\"Name\"" ), code.indexOf( "ID =" ) );
	}

	@Test
	void testThreeOccurrencesFlagEachRepeatAfterTheFirst() throws Exception {
		String				code		= "x = { a: 1, A: 2, \"a\": 3 };";
		List<Diagnostic>	duplicates	= duplicateKeyDiagnostics( "Three.bxs", code );

		assertThat( startCharacters( duplicates ) ).containsExactly( code.indexOf( "A:" ), code.indexOf( "\"a\"" ) );
	}

	@Test
	void testOrderedStructIsChecked() throws Exception {
		String				code		= "x = [ k: 1, K: 2 ];";
		List<Diagnostic>	duplicates	= duplicateKeyDiagnostics( "Ordered.bxs", code );

		assertThat( startCharacters( duplicates ) ).containsExactly( code.indexOf( "K:" ) );
	}

	@Test
	void testNestedStructsAreCheckedIndependently() throws Exception {
		String				code		= "x = { name: 1, inner: { name: 2, id: 1, id: 2 } };";
		List<Diagnostic>	duplicates	= duplicateKeyDiagnostics( "Nested.bxs", code );

		// "name" in the inner struct is not a duplicate of the outer "name"; the inner "id" repeats
		assertThat( startCharacters( duplicates ) ).containsExactly( code.lastIndexOf( "id:" ) );
	}

	@Test
	void testComputedAndSpreadKeysAreIgnored() throws Exception {
		String				code		= "x = { \"#k#\": 1, \"#k#\": 2, ...other, name: 1, NAME: 2 };";
		List<Diagnostic>	duplicates	= duplicateKeyDiagnostics( "Computed.bxs", code );

		// A single spread has no value; if it were read as a key/value pair, "name" would become a value and NAME would not be flagged
		assertThat( startCharacters( duplicates ) ).containsExactly( code.indexOf( "NAME:" ) );
	}

	@Test
	void testUniqueKeysAreNotFlagged() throws Exception {
		String code = "x = { name: 1, id: 2, \"other\": 3 };";

		assertThat( duplicateKeyDiagnostics( "Unique.bxs", code ) ).isEmpty();
	}

	private List<Diagnostic> duplicateKeyDiagnostics( String fileName, String code ) throws Exception {
		Path testFile = tempDir.resolve( fileName );
		Files.writeString( testFile, code );
		index.indexFile( testFile.toUri() );

		List<Diagnostic> diagnostics = ProjectContextProvider.getInstance().getFileDiagnostics( testFile.toUri() );
		assertThat( diagnostics ).isNotNull();

		return diagnostics.stream()
		    .filter( d -> d.getCode() != null && DuplicateStructKeyRule.ID.equals( d.getCode().getLeft() ) )
		    .toList();
	}

	private List<Integer> startCharacters( List<Diagnostic> diagnostics ) {
		return diagnostics.stream()
		    .map( d -> d.getRange().getStart().getCharacter() )
		    .toList();
	}
}
