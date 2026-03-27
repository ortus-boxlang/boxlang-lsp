package ortus.boxlang.lsp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ortus.boxlang.lsp.workspace.MappingConfig;
import ortus.boxlang.lsp.workspace.MappingResolver;
import ortus.boxlang.lsp.workspace.index.IndexedClass;
import ortus.boxlang.lsp.workspace.index.ProjectIndex;

public class ExternalIndexingTest extends BaseTest {

	private static final Path	PROJECT_ROOT	= Paths.get( "src/test/resources/test-bx-project" ).toAbsolutePath();
	private ProjectIndex		index;

	@BeforeEach
	void setUp() {
		MappingResolver.invalidate( PROJECT_ROOT );
		MappingConfig config = MappingResolver.resolve( PROJECT_ROOT );
		index = new ProjectIndex();
		index.initialize( PROJECT_ROOT, config );
	}

	// ─── Cycle 1 ─────────────────────────────────────────────────────────────
	// Class in external mapped directory is retrievable by virtual FQN

	@Test
	void externalClassIsIndexedByVirtualFQN() {
		// mock-external/ is mapped to /ext → ExternalClass.bx → FQN "ext.ExternalClass"
		Optional<IndexedClass> found = index.findClassByFQN( "ext.ExternalClass" );
		assertTrue( found.isPresent(), "ExternalClass should be indexed as 'ext.ExternalClass' via mappings" );
	}

	@Test
	void externalClassIsAlsoFindableBySimpleName() {
		Optional<IndexedClass> found = index.findClassByName( "ExternalClass" );
		assertTrue( found.isPresent(), "ExternalClass should be findable by simple name" );
	}

	// ─── Cycle 2 ─────────────────────────────────────────────────────────────
	// Class in classPaths directory is retrievable

	@Test
	void classPathClassIsIndexed() {
		// class-paths/ is a classPaths entry → FlatClass.bx → findable by simple name at minimum
		Optional<IndexedClass> found = index.findClassByName( "FlatClass" );
		assertTrue( found.isPresent(), "FlatClass in classPaths should be indexed" );
	}

	// ─── Cycle 3 ─────────────────────────────────────────────────────────────
	// Class in modulesDirectory is retrievable

	@Test
	void moduleClassIsIndexed() {
		// boxlang_modules/testModule/TestModuleClass.bx should be indexed
		Optional<IndexedClass> found = index.findClassByName( "TestModuleClass" );
		assertTrue( found.isPresent(), "TestModuleClass in modulesDirectory should be indexed" );
	}

	// ─── Cycle 4 ─────────────────────────────────────────────────────────────
	// Workspace files are NOT double-indexed when a mapping's real path is inside the workspace

	@Test
	void workspaceFilesAreNotDoubleIndexed() {
		// Car.bx lives in the workspace root — there should be exactly one entry for it
		long carCount = index.getAllClasses().stream()
		    .filter( c -> c.name().equalsIgnoreCase( "Car" ) )
		    .count();
		assertFalse( carCount > 1, "Car class should not be indexed more than once, but found: " + carCount );
	}
}
