package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonParser;

import ortus.boxlang.lsp.workspace.ProjectAppContext;
import ortus.boxlang.lsp.workspace.ProjectContextProvider;

public class LanguageServerAppContextInitializationTest extends BaseTest {

	@TempDir
	Path workspaceRoot;

	@BeforeEach
	void setUp() {
		ProjectContextProvider provider = ProjectContextProvider.getInstance();
		provider.setAppContext( ProjectAppContext.empty() );
	}

	@Test
	void testInitializeStoresAppContextFromInitializationOptions() throws Exception {
		Path				mappedRoot	= workspaceRoot.resolve( "modules_app/api-root" );
		Path				moduleDir	= workspaceRoot.resolve( "modules_app" );

		LanguageServer		server		= new LanguageServer();

		InitializeParams	params		= new InitializeParams();
		params.setCapabilities( new ClientCapabilities() );
		params.setWorkspaceFolders( List.of( new WorkspaceFolder( workspaceRoot.toUri().toString(), "workspace" ) ) );
		params.setInitializationOptions(
		    Map.of(
		        "boxlang",
		        Map.of(
		            "appContext",
		            Map.of(
		                "rootPath",
		                workspaceRoot.toString(),
		                "mappings",
		                Map.of( "api", mappedRoot.toString() ),
		                "moduleDirs",
		                List.of( moduleDir.toString() ),
		                "contextHash",
		                "app-context-hash"
		            )
		        )
		    )
		);

		server.initialize( params ).get();

		ProjectAppContext appContext = ProjectContextProvider.getInstance().getAppContext();
		assertThat( appContext.rootPath() ).isEqualTo( workspaceRoot.toAbsolutePath().normalize() );
		assertThat( appContext.contextHash() ).isEqualTo( "app-context-hash" );
		assertThat( appContext.mappings().get( "api" ) ).isEqualTo( mappedRoot.toAbsolutePath().normalize() );
		assertThat( appContext.moduleDirs() ).contains( moduleDir.toAbsolutePath().normalize() );
	}

	@Test
	void testInitializeStoresAppContextFromJsonObjectInitializationOptions() throws Exception {
		Path	mappedRoot	= workspaceRoot.resolve( "modules_app/api-root" );
		Path	moduleDir	= workspaceRoot.resolve( "modules_app" );
		Files.createDirectories( moduleDir );

		LanguageServer		server	= new LanguageServer();
		InitializeParams	params	= new InitializeParams();
		params.setCapabilities( new ClientCapabilities() );
		params.setWorkspaceFolders( List.of( new WorkspaceFolder( workspaceRoot.toUri().toString(), "workspace" ) ) );
		params.setInitializationOptions(
		    JsonParser.parseString(
		        """
		        {
		          "boxlang": {
		            "appContext": {
		              "rootPath": "%s",
		              "mappings": { "api": "%s" },
		              "moduleDirs": [ "%s" ],
		              "contextHash": "json-app-context-hash"
		            }
		          }
		        }
		        """.formatted(
		            workspaceRoot.toString().replace( "\\", "\\\\" ),
		            mappedRoot.toString().replace( "\\", "\\\\" ),
		            moduleDir.toString().replace( "\\", "\\\\" )
		        )
		    ).getAsJsonObject()
		);

		server.initialize( params ).get();

		ProjectAppContext appContext = ProjectContextProvider.getInstance().getAppContext();
		assertThat( appContext.rootPath() ).isEqualTo( workspaceRoot.toAbsolutePath().normalize() );
		assertThat( appContext.contextHash() ).isEqualTo( "json-app-context-hash" );
		assertThat( appContext.mappings().get( "api" ) ).isEqualTo( mappedRoot.toAbsolutePath().normalize() );
		assertThat( appContext.moduleDirs() ).contains( moduleDir.toAbsolutePath().normalize() );
	}

	@Test
	void testInitializeDerivesMappingsFromModuleDirectories() throws Exception {
		Path	moduleDir		= workspaceRoot.resolve( ".boxlang/modules" );
		Path	testboxModule	= moduleDir.resolve( "testbox" );
		Files.createDirectories( testboxModule.resolve( "system" ) );
		Files.writeString(
		    testboxModule.resolve( "ModuleConfig.bx" ),
		    """
		    component {
		        this.mapping = "testbox";
		    }
		    """,
		    StandardCharsets.UTF_8
		);

		LanguageServer		server	= new LanguageServer();
		InitializeParams	params	= new InitializeParams();
		params.setCapabilities( new ClientCapabilities() );
		params.setWorkspaceFolders( List.of( new WorkspaceFolder( workspaceRoot.toUri().toString(), "workspace" ) ) );
		params.setInitializationOptions(
		    Map.of(
		        "boxlang",
		        Map.of(
		            "appContext",
		            Map.of(
		                "rootPath",
		                workspaceRoot.toString(),
		                "mappings",
		                Map.of(),
		                "moduleDirs",
		                List.of( moduleDir.toString() ),
		                "contextHash",
		                "module-derived-mapping"
		            )
		        )
		    )
		);

		server.initialize( params ).get();

		ProjectAppContext appContext = ProjectContextProvider.getInstance().getAppContext();
		assertThat( appContext.mappings().get( "testbox" ) ).isEqualTo( testboxModule.toAbsolutePath().normalize() );
	}

	@Test
	void testInitializeDerivesMappingsFromModuleConfigCfc() throws Exception {
		Path	moduleDir		= workspaceRoot.resolve( ".boxlang/modules" );
		Path	testboxModule	= moduleDir.resolve( "testbox" );
		Files.createDirectories( testboxModule.resolve( "system" ) );
		Files.writeString(
		    testboxModule.resolve( "ModuleConfig.cfc" ),
		    """
		    component {
		        this.mapping = "testbox";
		    }
		    """,
		    StandardCharsets.UTF_8
		);

		LanguageServer		server	= new LanguageServer();
		InitializeParams	params	= new InitializeParams();
		params.setCapabilities( new ClientCapabilities() );
		params.setWorkspaceFolders( List.of( new WorkspaceFolder( workspaceRoot.toUri().toString(), "workspace" ) ) );
		params.setInitializationOptions(
		    Map.of(
		        "boxlang",
		        Map.of(
		            "appContext",
		            Map.of(
		                "rootPath",
		                workspaceRoot.toString(),
		                "mappings",
		                Map.of(),
		                "moduleDirs",
		                List.of( moduleDir.toString() ),
		                "contextHash",
		                "module-derived-mapping-cfc"
		            )
		        )
		    )
		);

		server.initialize( params ).get();

		ProjectAppContext appContext = ProjectContextProvider.getInstance().getAppContext();
		assertThat( appContext.mappings().get( "testbox" ) ).isEqualTo( testboxModule.toAbsolutePath().normalize() );
	}

	@Test
	void testInitializeDerivesMappingsFromModuleConfigCfcCfmapping() throws Exception {
		Path	moduleDir	= workspaceRoot.resolve( ".boxlang/modules" );
		Path	qbModule	= moduleDir.resolve( "qb" );
		Files.createDirectories( qbModule.resolve( "models" ) );
		Files.writeString(
		    qbModule.resolve( "ModuleConfig.cfc" ),
		    """
		    component {
		        this.cfmapping = "qb";
		    }
		    """,
		    StandardCharsets.UTF_8
		);

		LanguageServer		server	= new LanguageServer();
		InitializeParams	params	= new InitializeParams();
		params.setCapabilities( new ClientCapabilities() );
		params.setWorkspaceFolders( List.of( new WorkspaceFolder( workspaceRoot.toUri().toString(), "workspace" ) ) );
		params.setInitializationOptions(
		    Map.of(
		        "boxlang",
		        Map.of(
		            "appContext",
		            Map.of(
		                "rootPath",
		                workspaceRoot.toString(),
		                "mappings",
		                Map.of(),
		                "moduleDirs",
		                List.of( moduleDir.toString() ),
		                "contextHash",
		                "module-derived-cfmapping-cfc"
		            )
		        )
		    )
		);

		server.initialize( params ).get();

		ProjectAppContext appContext = ProjectContextProvider.getInstance().getAppContext();
		assertThat( appContext.mappings().get( "qb" ) ).isEqualTo( qbModule.toAbsolutePath().normalize() );
	}
}
