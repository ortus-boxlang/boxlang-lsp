package ortus.boxlang.moduleslug;

import static com.google.common.truth.Truth.assertThat;

import java.nio.file.Paths;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.ScriptingRequestBoxContext;
import ortus.boxlang.runtime.modules.ModuleRecord;
import ortus.boxlang.runtime.scopes.IScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.scopes.VariablesScope;
import ortus.boxlang.runtime.services.DatasourceService;
import ortus.boxlang.runtime.services.ModuleService;

/**
 * This loads the module and runs an integration test on the module.
 */
public class IntegrationTest {

	static BoxRuntime		runtime;
	static ModuleService	moduleService;
	static Key				result	= new Key( "result" );
	IBoxContext				context;
	IScope					variables;

	@BeforeAll
	public static void setup() {
		runtime			= BoxRuntime.getInstance( true );
		moduleService	= runtime.getModuleService();
	}

	@BeforeEach
	public void setupEach() {
		context		= new ScriptingRequestBoxContext();
		variables	= context.getScopeNearby( VariablesScope.name );
	}

	@DisplayName( "Test the module loads in BoxLang" )
	@Test
	public void testModuleLoads() {
		// Given
		Key					moduleName			= new Key( "@MODULE_SLUG@" );
		String				physicalPath		= Paths.get( "./build/module" ).toAbsolutePath().toString();
		ModuleRecord		moduleRecord		= new ModuleRecord( physicalPath );
		IBoxContext			context				= new ScriptingRequestBoxContext();
		DatasourceService	datasourceService	= runtime.getDataSourceService();

		// When
		moduleRecord
		    .loadDescriptor( context )
		    .register( context )
		    .activate( context );

		moduleService.getRegistry().put( moduleName, moduleRecord );

		// Then
		assertThat( moduleService.getRegistry().containsKey( moduleName ) ).isTrue();

		// Verify things got registered
		// assertThat( datasourceService.hasDriver( Key.of( "derby" ) ) ).isTrue();

		// Register a named datasource
		// runtime.getConfiguration().runtime.datasources.put(
		// Key.of( "derby" ),
		// DatasourceConfig.fromStruct( Struct.of(
		// "name", "derby",
		// "driver", "derby",
		// "properties", Struct.of(
		// "database", "testDB",
		// "protocol", "memory"
		// )
		// ) )
		// );

		// @formatter:off
		runtime.executeSource(
		    """
			// Testing code here
			""",
		    context
		);
		// @formatter:on

		// Asserts here

	}
}
