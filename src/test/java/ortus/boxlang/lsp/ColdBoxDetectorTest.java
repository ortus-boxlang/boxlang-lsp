package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertThat;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import org.junit.jupiter.api.Test;

import ortus.boxlang.lsp.workspace.ColdBoxDetector;

public class ColdBoxDetectorTest extends BaseTest {

	private static Path fixtureDir( String name ) {
		return Paths.get( "src/test/resources/files/coldboxDetectorTest" ).resolve( name ).toAbsolutePath();
	}

	// ─── Cycle 1 ─────────────────────────────────────────────────────────────
	// Tracer bullet: isColdBoxApp detects extends="coldbox.system.Bootstrap"

	@Test
	void isColdBoxAppDetectsExtendsBootstrap() {
		Path appRoot = fixtureDir( "withExtends" );
		assertThat( ColdBoxDetector.isColdBoxApp( appRoot ) ).isTrue();
	}

	// ─── Cycle 2 ─────────────────────────────────────────────────────────────
	// isColdBoxApp detects /coldbox directory

	@Test
	void isColdBoxAppDetectsColdboxDir() {
		Path appRoot = fixtureDir( "withColdboxDir" );
		assertThat( ColdBoxDetector.isColdBoxApp( appRoot ) ).isTrue();
	}

	// ─── Cycle 3 ─────────────────────────────────────────────────────────────
	// isColdBoxApp detects config/Coldbox.cfc

	@Test
	void isColdBoxAppDetectsConfigColdbox() {
		Path appRoot = fixtureDir( "withConfigColdbox" );
		assertThat( ColdBoxDetector.isColdBoxApp( appRoot ) ).isTrue();
	}

	// ─── Cycle 4 ─────────────────────────────────────────────────────────────
	// isColdBoxApp returns false for non-ColdBox app

	@Test
	void isColdBoxAppReturnsFalseForNonColdbox() {
		Path appRoot = fixtureDir( "nonColdbox" );
		assertThat( ColdBoxDetector.isColdBoxApp( appRoot ) ).isFalse();
	}

	// ─── Cycle 5 ─────────────────────────────────────────────────────────────
	// discoverModuleMappings returns flat keys for modules

	@Test
	void discoverModuleMappingsReturnsFlatKeys() {
		Path				appRoot	= fixtureDir( "withExtends" );
		Map<String, Path>	result	= ColdBoxDetector.discoverModuleMappings( appRoot );
		assertThat( result ).isNotNull();
		assertThat( result ).containsKey( "/foo" );
		assertThat( result.get( "/foo" ).toString() ).endsWith( "modules/foo" );
	}

	// ─── Cycle 6 ─────────────────────────────────────────────────────────────
	// discoverModuleMappings recursively scans nested modules/

	@Test
	void discoverModuleMappingsScansNestedModules() {
		Path				appRoot	= fixtureDir( "withNestedModules" );
		Map<String, Path>	result	= ColdBoxDetector.discoverModuleMappings( appRoot );
		assertThat( result ).isNotNull();
		assertThat( result ).containsKey( "/foo" );
		assertThat( result ).containsKey( "/bar" );
	}

	// ─── Cycle 7 ─────────────────────────────────────────────────────────────
	// discoverModuleMappings least-nested wins on name collision

	@Test
	void discoverModuleMappingsLeastNestedWins() {
		Path				appRoot	= fixtureDir( "withTieBreaker" );
		Map<String, Path>	result	= ColdBoxDetector.discoverModuleMappings( appRoot );
		assertThat( result ).isNotNull();
		assertThat( result ).containsKey( "/foo" );
		// /modules/foo is less nested than /modules/foo/modules/sub/foo
		Path fooPath = result.get( "/foo" );
		assertThat( fooPath.toString() ).doesNotContain( "sub" );
		assertThat( fooPath.toString() ).endsWith( "modules/foo" );
	}

	// ─── Cycle 8 ─────────────────────────────────────────────────────────────
	// discoverModuleMappings returns empty map when no modules directory

	@Test
	void discoverModuleMappingsReturnsEmptyMapWhenNoModulesDir() {
		Path				appRoot	= fixtureDir( "nonColdbox" );
		Map<String, Path>	result	= ColdBoxDetector.discoverModuleMappings( appRoot );
		assertThat( result ).isNotNull();
		assertThat( result ).isEmpty();
	}

	// ─── Cycle 9 ─────────────────────────────────────────────────────────────
	// discoverModuleMappings scans modules_app directory

	@Test
	void discoverModuleMappingsScansModulesAppDirectory() {
		Path				appRoot	= fixtureDir( "withModulesApp" );
		Map<String, Path>	result	= ColdBoxDetector.discoverModuleMappings( appRoot );
		assertThat( result ).isNotNull();
		assertThat( result ).containsKey( "/bar" );
		assertThat( result.get( "/bar" ).toString() ).endsWith( "modules_app/bar" );
	}

	// ─── Cycle 10 ────────────────────────────────────────────────────────────
	// modules/ wins over modules_app/ at same depth on name collision

	@Test
	void discoverModuleMappingsModulesWinsOverModulesAppAtSameDepth() {
		Path				appRoot	= fixtureDir( "withModulesAppTieBreaker" );
		Map<String, Path>	result	= ColdBoxDetector.discoverModuleMappings( appRoot );
		assertThat( result ).isNotNull();
		assertThat( result ).containsKey( "/foo" );
		Path fooPath = result.get( "/foo" );
		assertThat( fooPath.toString() ).contains( "modules/foo" );
		assertThat( fooPath.toString() ).doesNotContain( "modules_app" );
	}

	// ─── Cycle 11 ────────────────────────────────────────────────────────────
	// discoverModuleMappings recursively scans nested modules_app/

	@Test
	void discoverModuleMappingsScansNestedModulesApp() {
		Path				appRoot	= fixtureDir( "withNestedModulesApp" );
		Map<String, Path>	result	= ColdBoxDetector.discoverModuleMappings( appRoot );
		assertThat( result ).isNotNull();
		assertThat( result ).containsKey( "/foo" );
		assertThat( result ).containsKey( "/baz" );
		assertThat( result.get( "/baz" ).toString() ).contains( "modules_app/baz" );
	}
}
