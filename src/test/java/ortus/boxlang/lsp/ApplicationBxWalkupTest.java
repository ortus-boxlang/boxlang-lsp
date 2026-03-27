package ortus.boxlang.lsp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ortus.boxlang.lsp.workspace.MappingConfig;
import ortus.boxlang.lsp.workspace.MappingResolver;

public class ApplicationBxWalkupTest extends BaseTest {

	private static final Path TEST_PROJECT = Paths.get( "src/test/resources/test-bx-project" ).toAbsolutePath();

	@BeforeEach
	void setUp() {
		MappingResolver.invalidate( TEST_PROJECT );
	}

	// ─── Cycle 1 ──────────────────────────────────────────────────────────────
	// Static mapping from Application.bx is included in the resolved config

	@Test
	void resolveForFilePicksUpStaticMappingFromApplicationBx() {
		Path			bxObjFile	= TEST_PROJECT.resolve( "bxObj.bx" );
		MappingConfig	config		= MappingResolver.resolveForFile( bxObjFile, TEST_PROJECT );

		assertNotNull( config );
		assertTrue( config.getMappings().containsKey( "/appmodel" ),
		    "Application.bx static mapping '/appmodel' should be in the resolved config" );
	}

	// ─── Cycle 2 ──────────────────────────────────────────────────────────────
	// Dynamic entries (expandPath, variable refs) are silently skipped

	@Test
	void resolveForFileSkipsDynamicEntries() {
		Path			bxObjFile	= TEST_PROJECT.resolve( "bxObj.bx" );
		MappingConfig	config		= MappingResolver.resolveForFile( bxObjFile, TEST_PROJECT );

		assertFalse( config.getMappings().containsKey( "/appdynamic" ),
		    "Dynamic Application.bx mapping '/appdynamic' should be absent from the resolved config" );
	}

	// ─── Cycle 3 ──────────────────────────────────────────────────────────────
	// Merged config still contains entries from boxlang.json

	@Test
	void resolveForFileMergesWithBoxlangJson() {
		Path			bxObjFile	= TEST_PROJECT.resolve( "bxObj.bx" );
		MappingConfig	config		= MappingResolver.resolveForFile( bxObjFile, TEST_PROJECT );

		// boxlang.json has "/ext" → mock-external; Application.bx overrides it
		// but the key "/ext" must still be present
		assertTrue( config.getMappings().containsKey( "/ext" ),
		    "The '/ext' key from boxlang.json should still be present in the merged config" );
	}

	// ─── Cycle 4 ──────────────────────────────────────────────────────────────
	// Application.bx key overrides boxlang.json value on collision

	@Test
	void resolveForFileApplicationBxKeyOverridesBoxlangJson() {
		// Application.bx has "/ext" → "./app-ext-override"
		// boxlang.json has "/ext" → "./mock-external"
		// Application.bx should win
		Path			bxObjFile	= TEST_PROJECT.resolve( "bxObj.bx" );
		MappingConfig	config		= MappingResolver.resolveForFile( bxObjFile, TEST_PROJECT );

		Path			extPath		= config.getMappings().get( "/ext" );
		assertNotNull( extPath, "'/ext' mapping should be present" );
		assertTrue( extPath.toString().contains( "app-ext-override" ),
		    "Application.bx value should override boxlang.json for '/ext'" );
	}

	// ─── Cycle 5 ──────────────────────────────────────────────────────────────
	// Result is cached — same object returned on repeated calls

	@Test
	void resolveForFileCachesResult() {
		Path			bxObjFile	= TEST_PROJECT.resolve( "bxObj.bx" );
		MappingConfig	config1		= MappingResolver.resolveForFile( bxObjFile, TEST_PROJECT );
		MappingConfig	config2		= MappingResolver.resolveForFile( bxObjFile, TEST_PROJECT );

		assertSame( config1, config2, "resolveForFile should return a cached MappingConfig on repeated calls" );
	}

	// ─── Cycle 6 ──────────────────────────────────────────────────────────────
	// Walk-up: file in a subdirectory finds Application.bx in the parent

	@Test
	void resolveForFileWalksUpFromSubdirectory() {
		Path			subFile	= TEST_PROJECT.resolve( "subpackage/BaseType.bx" );
		MappingConfig	config	= MappingResolver.resolveForFile( subFile, TEST_PROJECT );

		// Application.bx is in test-bx-project root; walk-up from subpackage/ should find it
		assertTrue( config.getMappings().containsKey( "/appmodel" ),
		    "resolveForFile should walk up to the workspace root to find Application.bx" );
	}
}
