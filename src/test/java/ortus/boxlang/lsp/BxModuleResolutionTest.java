package ortus.boxlang.lsp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ortus.boxlang.lsp.workspace.MappingConfig;
import ortus.boxlang.lsp.workspace.MappingResolver;
import ortus.boxlang.lsp.workspace.ProjectContextProvider;
import ortus.boxlang.lsp.workspace.index.IndexedClass;
import ortus.boxlang.lsp.workspace.index.ProjectIndex;

public class BxModuleResolutionTest extends BaseTest {

	private static final Path		PROJECT_ROOT	= Paths.get( "src/test/resources/test-bx-project" ).toAbsolutePath();
	private ProjectIndex			index;
	private ProjectContextProvider	provider;

	@BeforeEach
	void setUp() {
		MappingResolver.invalidate( PROJECT_ROOT );
		MappingConfig config = MappingResolver.resolve( PROJECT_ROOT );
		index = new ProjectIndex();
		index.initialize( PROJECT_ROOT, config );

		provider = ProjectContextProvider.getInstance();
		provider.setIndex( index );

		WorkspaceFolder folder = new WorkspaceFolder();
		folder.setUri( PROJECT_ROOT.toUri().toString() );
		folder.setName( "test-bx-project" );
		provider.setWorkspaceFolders( List.of( folder ) );
	}

	@AfterEach
	void tearDown() {
		MappingResolver.invalidate( PROJECT_ROOT );
		if ( provider != null ) {
			provider.setIndex( null );
			provider.setWorkspaceFolders( List.of() );
		}
	}

	// ─── Cycle 1 ──────────────────────────────────────────────────────────────
	// bxmodule.testModule.TestModuleClass resolves correctly

	@Test
	void bxModuleFqnResolvesToTestModuleClass() {
		Optional<IndexedClass> result = index.findClassByFQN( "bxmodule.testModule.TestModuleClass" );

		assertTrue( result.isPresent(),
		    "bxmodule.testModule.TestModuleClass should resolve to an IndexedClass" );
		assertEquals( "TestModuleClass", result.get().name(),
		    "Resolved class should have correct simple name" );
	}

	// ─── Cycle 2 ──────────────────────────────────────────────────────────────
	// bxmodule.testModule.sub.NestedUtil resolves to the nested fixture

	@Test
	void bxModuleNestedFqnResolvesToNestedUtil() {
		Optional<IndexedClass> result = index.findClassByFQN( "bxmodule.testModule.sub.NestedUtil" );

		assertTrue( result.isPresent(),
		    "bxmodule.testModule.sub.NestedUtil should resolve to NestedUtil" );
		assertEquals( "NestedUtil", result.get().name(),
		    "Resolved class should have correct simple name" );
	}

	// ─── Cycle 3 ──────────────────────────────────────────────────────────────
	// Unknown module name returns empty without error

	@Test
	void unknownBxModuleReturnsEmpty() {
		Optional<IndexedClass> result = index.findClassByFQN( "bxmodule.noSuchModule.Foo" );

		assertFalse( result.isPresent(),
		    "Unknown bxmodule reference should return Optional.empty()" );
	}

	// ─── Cycle 4 ──────────────────────────────────────────────────────────────
	// Lookup is case-insensitive on the "bxmodule." prefix

	@Test
	void bxModuleLookupIsCaseInsensitiveOnPrefix() {
		Optional<IndexedClass>	lower	= index.findClassByFQN( "bxmodule.testModule.TestModuleClass" );
		Optional<IndexedClass>	upper	= index.findClassByFQN( "BXMODULE.testModule.TestModuleClass" );
		Optional<IndexedClass>	mixed	= index.findClassByFQN( "BxModule.testModule.TestModuleClass" );

		assertTrue( lower.isPresent(), "'bxmodule.' (lowercase) should resolve" );
		assertTrue( upper.isPresent(), "'BXMODULE.' (uppercase) should resolve" );
		assertTrue( mixed.isPresent(), "'BxModule.' (mixed case) should resolve" );
	}

	// ─── Cycle 5 ──────────────────────────────────────────────────────────────
	// File extending a bxmodule class produces zero error diagnostics

	@Test
	void fileExtendingBxModuleClassHasZeroErrorDiagnostics() throws Exception {
		// Index all project files so normal imports work
		java.nio.file.Files.walk( PROJECT_ROOT )
		    .filter( p -> java.nio.file.Files.isRegularFile( p ) &&
		        ortus.boxlang.lsp.LSPTools.canWalkFile( p ) )
		    .forEach( p -> index.indexFile( p.toUri() ) );

		URI					bxModuleUserUri	= PROJECT_ROOT.resolve( "BxModuleUser.bx" ).toUri();
		List<Diagnostic>	diagnostics		= provider.getFileDiagnostics( bxModuleUserUri );

		long				errorCount		= diagnostics.stream()
		    .filter( d -> d.getSeverity() == DiagnosticSeverity.Error )
		    .count();

		assertEquals( 0, errorCount,
		    "BxModuleUser.bx (extends bxmodule.testModule.TestModuleClass) should have zero error diagnostics" );
	}
}
