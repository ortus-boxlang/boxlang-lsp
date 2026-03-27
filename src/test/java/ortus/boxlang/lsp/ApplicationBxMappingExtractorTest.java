package ortus.boxlang.lsp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import org.junit.jupiter.api.Test;

import ortus.boxlang.lsp.workspace.ApplicationBxMappingExtractor;

public class ApplicationBxMappingExtractorTest extends BaseTest {

	private static final Path FIXTURES = Paths
	    .get( "src/test/resources/files/applicationBxExtractorTest" )
	    .toAbsolutePath();

	// ─── Cycle 1 ──────────────────────────────────────────────────────────────
	// Struct-literal form: this.mappings = { "/key": "/value" }

	@Test
	void structLiteralFormReturnsAllStaticEntries() {
		Map<String, String> result = ApplicationBxMappingExtractor.extract(
		    FIXTURES.resolve( "structLiteral.bx" ) );

		assertNotNull( result );
		assertEquals( 2, result.size() );
		assertEquals( "/var/www/models", result.get( "/models" ) );
		assertEquals( "/var/www/helpers", result.get( "/helpers" ) );
	}

	// ─── Cycle 2 ──────────────────────────────────────────────────────────────
	// Bracket-assignment form: this.mappings["/key"] = "/value"

	@Test
	void bracketAssignmentFormReturnsAllStaticEntries() {
		Map<String, String> result = ApplicationBxMappingExtractor.extract(
		    FIXTURES.resolve( "bracketAssignment.bx" ) );

		assertNotNull( result );
		assertEquals( 2, result.size() );
		assertEquals( "/var/www/models", result.get( "/models" ) );
		assertEquals( "/var/www/helpers", result.get( "/helpers" ) );
	}

	// ─── Cycle 3 ──────────────────────────────────────────────────────────────
	// Mixed static + dynamic: only static entries are returned

	@Test
	void mixedEntriesReturnsOnlyStaticOnes() {
		Map<String, String> result = ApplicationBxMappingExtractor.extract(
		    FIXTURES.resolve( "mixedEntries.bx" ) );

		assertNotNull( result );
		assertEquals( 1, result.size() );
		assertEquals( "/var/www/static", result.get( "/static" ) );
	}

	// ─── Cycle 4 ──────────────────────────────────────────────────────────────
	// No this.mappings in file → empty map

	@Test
	void fileWithNoMappingsReturnsEmptyMap() {
		Map<String, String> result = ApplicationBxMappingExtractor.extract(
		    FIXTURES.resolve( "noMappings.bx" ) );

		assertNotNull( result );
		assertTrue( result.isEmpty() );
	}

	// ─── Cycle 5 ──────────────────────────────────────────────────────────────
	// Missing file → empty map, no exception

	@Test
	void missingFileReturnsEmptyMapWithoutThrowing() {
		Map<String, String> result = ApplicationBxMappingExtractor.extract(
		    FIXTURES.resolve( "doesNotExist.bx" ) );

		assertNotNull( result );
		assertTrue( result.isEmpty() );
	}
}
