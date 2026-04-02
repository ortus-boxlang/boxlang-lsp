package ortus.boxlang.lsp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import org.eclipse.lsp4j.WorkspaceFolder;

import ortus.boxlang.lsp.workspace.MappingConfig;
import ortus.boxlang.lsp.workspace.MappingResolver;
import ortus.boxlang.lsp.workspace.ProjectContextProvider;
import ortus.boxlang.runtime.BoxRuntime;

public class MappingResolverTest extends BaseTest {

	@BeforeEach
	void clearCache() {
		// Ensure each test starts with a cold cache so tests are independent
		MappingResolver.invalidate( fixtureDir( "withMappings" ) );
		MappingResolver.invalidate( fixtureDir( "withComments" ) );
		MappingResolver.invalidate( fixtureDir( "withVariables" ) );
		MappingResolver.invalidate( fixtureDir( "withEnvVar" ) );
		MappingResolver.invalidate( fixtureDir( "withRelativePaths" ) );
		MappingResolver.invalidate( fixtureDir( "walkUpRoot/subdir" ) );
		MappingResolver.invalidate( fixtureDir( "noConfig" ) );
	}

	private static Path fixtureDir( String name ) {
		return Paths.get( "src/test/resources/files/mappingResolverTest" ).resolve( name ).toAbsolutePath();
	}

	// ─── Cycle 1 ─────────────────────────────────────────────────────────────
	// Tracer bullet: resolve reads mappings from root boxlang.json

	@Test
	void resolveReturnsMappingsFromRootConfig() {
		Path			root	= fixtureDir( "withMappings" );
		MappingConfig	config	= MappingResolver.resolve( root );
		assertNotNull( config );
		assertTrue( config.getMappings().containsKey( "/models" ),
		    "Expected '/models' mapping key to be present" );
	}

	// ─── Cycle 2 ─────────────────────────────────────────────────────────────
	// classPaths and modulesDirectory fields are populated

	@Test
	void resolvePopulatesClassPathsAndModulesDirectory() {
		Path			root	= fixtureDir( "withMappings" );
		MappingConfig	config	= MappingResolver.resolve( root );
		assertNotNull( config );
		assertEquals( 1, config.getClassPaths().size(), "Expected one classPaths entry" );
		assertEquals( 1, config.getModulesDirectory().size(), "Expected one modulesDirectory entry" );
	}

	// ─── Cycle 3 ─────────────────────────────────────────────────────────────
	// Empty config returned when no boxlang.json exists anywhere in ancestor chain

	@Test
	void resolveReturnsEmptyConfigWhenNoBoxlangJsonFound() {
		// Use a directory that has no boxlang.json in its ancestor chain.
		// We create the path at filesystem root level to guarantee no walk-up match.
		Path root = Path.of( "/tmp/bx_lsp_test_no_config_dir_xyz" );
		MappingResolver.invalidate( root );
		MappingConfig config = MappingResolver.resolve( root );
		assertNotNull( config );
		assertTrue( config.getMappings().isEmpty(), "Mappings should be empty when no config found" );
		assertTrue( config.getClassPaths().isEmpty(), "ClassPaths should be empty when no config found" );
		assertTrue( config.getModulesDirectory().isEmpty(), "ModulesDirectory should be empty when no config found" );
	}

	// ─── Cycle 4 ─────────────────────────────────────────────────────────────
	// Walk-up discovery: resolve finds boxlang.json in parent directory

	@Test
	void resolveFindsConfigInParentDirectory() {
		Path			childDir	= fixtureDir( "walkUpRoot/subdir" );
		MappingConfig	config		= MappingResolver.resolve( childDir );
		assertNotNull( config );
		assertTrue( config.getMappings().containsKey( "/walkup" ),
		    "Walk-up should find '/walkup' from parent boxlang.json" );
	}

	// ─── Cycle 5 ─────────────────────────────────────────────────────────────
	// Comment stripping: // comments in boxlang.json do not break parsing

	@Test
	void resolveStripsLineCommentsBeforeParsing() {
		Path			root	= fixtureDir( "withComments" );
		MappingConfig	config	= MappingResolver.resolve( root );
		assertNotNull( config );
		assertTrue( config.getMappings().containsKey( "/api" ),
		    "Expected '/api' key after stripping comments" );
	}

	// ─── Cycle 6 ─────────────────────────────────────────────────────────────
	// ${user-dir} expansion

	@Test
	void resolveExpandsUserDirVariable() {
		Path			root	= fixtureDir( "withVariables" );
		MappingConfig	config	= MappingResolver.resolve( root );
		assertNotNull( config );
		Path appPath = config.getMappings().get( "/app" );
		assertNotNull( appPath, "Expected '/app' mapping to exist" );
		assertTrue( appPath.startsWith( root ),
		    "Expected '/app' path to start with workspace root after ${user-dir} expansion, but was: " + appPath );
	}

	// ─── Cycle 7 ─────────────────────────────────────────────────────────────
	// ${boxlang-home} expansion

	@Test
	void resolveExpandsBoxlangHomeVariable() {
		Path			root	= fixtureDir( "withVariables" );
		MappingConfig	config	= MappingResolver.resolve( root );
		assertNotNull( config );
		Path runtimePath = config.getMappings().get( "/runtime" );
		assertNotNull( runtimePath, "Expected '/runtime' mapping to exist" );
		Path bxHome = BoxRuntime.getInstance().getRuntimeHome().toAbsolutePath().normalize();
		assertTrue( runtimePath.startsWith( bxHome ),
		    "Expected path to start with boxlang home (" + bxHome + ") but was: " + runtimePath );
	}

	// ─── Cycle 8 ─────────────────────────────────────────────────────────────
	// ${env.VAR:default} expansion — fallback branch (var absent)

	@Test
	void resolveUsesEnvVarFallbackWhenVariableAbsent() {
		Path			root	= fixtureDir( "withEnvVar" );
		MappingConfig	config	= MappingResolver.resolve( root );
		assertNotNull( config );
		Path envPath = config.getMappings().get( "/envpath" );
		assertNotNull( envPath, "Expected '/envpath' mapping to exist" );
		// BX_LSP_TEST_MISSING_VAR is not set in the test environment, so fallback applies.
		// "fallback" is a relative path so it resolves relative to the config file's directory.
		Path expected = fixtureDir( "withEnvVar" ).resolve( "fallback" ).normalize();
		assertEquals( expected, envPath,
		    "Expected fallback value when env var is absent" );
	}

	// ─── Cycle 9 ─────────────────────────────────────────────────────────────
	// Relative paths resolved relative to boxlang.json location, not workspace root

	@Test
	void resolveExpandsRelativePathsRelativeToConfigFile() {
		Path			root	= fixtureDir( "withRelativePaths" );
		MappingConfig	config	= MappingResolver.resolve( root );
		assertNotNull( config );
		Path modelsPath = config.getMappings().get( "/models" );
		assertNotNull( modelsPath, "Expected '/models' mapping to exist" );
		// ./models should resolve relative to the withRelativePaths/ directory
		Path expected = root.resolve( "models" ).toAbsolutePath().normalize();
		assertEquals( expected, modelsPath,
		    "Relative path should be resolved relative to boxlang.json directory" );
	}

	// ─── Cycle 10 ─────────────────────────────────────────────────────────────
	// Static cache: two calls with the same root return the same object

	@Test
	void resolveReturnsCachedInstanceForSameRoot() {
		Path			root	= fixtureDir( "withMappings" );
		MappingConfig	first	= MappingResolver.resolve( root );
		MappingConfig	second	= MappingResolver.resolve( root );
		assertSame( first, second, "Expected same cached MappingConfig instance for the same root" );
	}

	// ─── Cycle 11 ─────────────────────────────────────────────────────────────
	// ProjectContextProvider.getMappings() delegates to MappingResolver

	@Test
	void projectContextProviderGetMappingsDelegatesToMappingResolver() {
		ProjectContextProvider	pcp		= ProjectContextProvider.getInstance();
		List<WorkspaceFolder>	saved	= pcp.getWorkspaceFolders();
		Path					root	= fixtureDir( "withMappings" );
		MappingResolver.invalidate( root );
		try {
			WorkspaceFolder folder = new WorkspaceFolder();
			folder.setUri( root.toUri().toString() );
			pcp.setWorkspaceFolders( List.of( folder ) );
			Map<String, Path> mappings = pcp.getMappings();
			assertFalse( mappings.isEmpty(), "Expected non-empty mappings when boxlang.json exists" );
			assertTrue( mappings.containsKey( "/models" ), "Expected '/models' mapping key" );
		} finally {
			pcp.setWorkspaceFolders( saved );
		}
	}
}
