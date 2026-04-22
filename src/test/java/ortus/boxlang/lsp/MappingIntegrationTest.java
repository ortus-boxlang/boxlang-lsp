package ortus.boxlang.lsp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.util.Optional;

import ortus.boxlang.compiler.parser.Parser;
import ortus.boxlang.compiler.parser.ParsingResult;
import ortus.boxlang.lsp.workspace.MappingConfig;
import ortus.boxlang.lsp.workspace.MappingResolver;
import ortus.boxlang.lsp.workspace.index.IndexedClass;
import ortus.boxlang.lsp.workspace.index.ProjectIndex;
import ortus.boxlang.lsp.workspace.index.ProjectIndexVisitor;

public class MappingIntegrationTest extends BaseTest {

	// ─── Cycle 1 ─────────────────────────────────────────────────────────────
	// Tracer bullet: boxlang.json mapping changes FQN computation

	@Test
	void boxlangJsonMappingChangesFQNComputation( @TempDir Path tempDir ) throws Exception {
		// Create workspace: src/models/User.bx
		Path modelsDir = tempDir.resolve( "src/models" );
		Files.createDirectories( modelsDir );
		Path userBx = modelsDir.resolve( "User.bx" );
		Files.writeString( userBx, "class {}" );

		// boxlang.json maps /models → ./src/models
		Path boxlangJson = tempDir.resolve( "boxlang.json" );
		Files.writeString( boxlangJson, "{ \"mappings\": { \"/models\": \"./src/models\" } }" );

		MappingResolver.invalidate( tempDir );
		MappingConfig		config	= MappingResolver.resolve( tempDir );

		ProjectIndexVisitor	visitor	= parseAndVisit( userBx.toUri(), tempDir, config );
		assertEquals( "models.User", visitor.getComputedFQN(),
		    "Mapping should change FQN from 'src.models.User' to 'models.User'" );
	}

	// ─── Cycle 2 ─────────────────────────────────────────────────────────────
	// VSCode mapping change causes new FQN computation

	@Test
	void vscodeMappingChangeCausesNewFQNComputation( @TempDir Path tempDir ) throws Exception {
		// Create workspace: realmodels/User.bx (no boxlang.json)
		Path modelsDir = tempDir.resolve( "realmodels" );
		Files.createDirectories( modelsDir );
		Path userBx = modelsDir.resolve( "User.bx" );
		Files.writeString( userBx, "class {}" );

		// VSCode mapping /models → ./realmodels
		Map<String, String> vscodeMappings = Map.of( "/models", "./realmodels" );

		MappingResolver.invalidate( tempDir );
		MappingConfig		config	= MappingResolver.resolve( tempDir, vscodeMappings );

		ProjectIndexVisitor	visitor	= parseAndVisit( userBx.toUri(), tempDir, config );
		assertEquals( "models.User", visitor.getComputedFQN(),
		    "VSCode mapping should change FQN from 'realmodels.User' to 'models.User'" );
	}

	// ─── Cycle 3 ─────────────────────────────────────────────────────────────
	// ColdBox detection + module discovery yields correct implicit FQN

	@Test
	void coldBoxModuleDiscoveryYieldsCorrectImplicitFQN( @TempDir Path tempDir ) throws Exception {
		// Create ColdBox app: Application.cfc + modules/foo/models/User.bx
		Path appCfc = tempDir.resolve( "Application.cfc" );
		Files.writeString( appCfc, "component extends=\"coldbox.system.Bootstrap\" {}" );

		Path moduleModelsDir = tempDir.resolve( "modules/foo/models" );
		Files.createDirectories( moduleModelsDir );
		Path userBx = moduleModelsDir.resolve( "User.bx" );
		Files.writeString( userBx, "class {}" );

		// Need a boxlang.json for MappingResolver to find workspace root
		Path boxlangJson = tempDir.resolve( "boxlang.json" );
		Files.writeString( boxlangJson, "{}" );

		MappingResolver.invalidate( tempDir );
		MappingResolver.invalidateFile( appCfc );

		// Use resolveForFile so Application.bx is found and ColdBox detection runs
		MappingConfig config = MappingResolver.resolveForFile( userBx, tempDir );
		assertNotNull( config );
		assertEquals( tempDir, config.getWorkspaceRoot() );

		ProjectIndexVisitor visitor = parseAndVisit( userBx.toUri(), tempDir, config );
		assertEquals( "foo.models.User", visitor.getComputedFQN(),
		    "ColdBox implicit mapping should produce FQN 'foo.models.User'" );
	}

	// ─── Cycle 4 ─────────────────────────────────────────────────────────────
	// Full stack: VSCode overrides boxlang.json, both affect FQN

	@Test
	void vscodeOverridesBoxlangJsonForFQNComputation( @TempDir Path tempDir ) throws Exception {
		// Workspace: jsonmodels/User.bx
		Path jsonModelsDir = tempDir.resolve( "jsonmodels" );
		Files.createDirectories( jsonModelsDir );
		Path userBx = jsonModelsDir.resolve( "User.bx" );
		Files.writeString( userBx, "class {}" );

		// boxlang.json maps /models → ./jsonmodels
		Path boxlangJson = tempDir.resolve( "boxlang.json" );
		Files.writeString( boxlangJson, "{ \"mappings\": { \"/models\": \"./jsonmodels\" } }" );

		// VSCode overrides /models → ./vscodemodels (which doesn't exist, so it won't match)
		// Instead, let's make vscode point to a DIFFERENT existing dir
		Path vscodeModelsDir = tempDir.resolve( "vscodemodels" );
		Files.createDirectories( vscodeModelsDir );
		Path vscodeUserBx = vscodeModelsDir.resolve( "Admin.bx" );
		Files.writeString( vscodeUserBx, "class {}" );

		Map<String, String> vscodeMappings = Map.of( "/models", "./vscodemodels" );

		MappingResolver.invalidate( tempDir );
		MappingConfig		config	= MappingResolver.resolve( tempDir, vscodeMappings );

		ProjectIndexVisitor	visitor	= parseAndVisit( vscodeUserBx.toUri(), tempDir, config );
		assertEquals( "models.Admin", visitor.getComputedFQN(),
		    "VSCode mapping should override boxlang.json for FQN" );
	}

	// ─── Cycle 5 ─────────────────────────────────────────────────────────────
	// modules_app/core/models/BaseEntity.bx gets FQN core.models.BaseEntity
	// via workspace-level resolve (no boxlang.json, no resolveForFile walk-up)

	@Test
	void coldBoxModulesAppFQNComputation( @TempDir Path tempDir ) throws Exception {
		// ColdBox app root without boxlang.json
		Path appCfc = tempDir.resolve( "Application.cfc" );
		Files.writeString( appCfc, "component extends=\"coldbox.system.Bootstrap\" {}" );

		Path baseEntityBx = tempDir.resolve( "modules_app/core/models/BaseEntity.bx" );
		Files.createDirectories( baseEntityBx.getParent() );
		Files.writeString( baseEntityBx, "class {}" );

		MappingResolver.invalidate( tempDir );
		MappingConfig		config	= MappingResolver.resolve( tempDir );

		ProjectIndexVisitor	visitor	= parseAndVisit( baseEntityBx.toUri(), tempDir, config );
		assertEquals( "core.models.BaseEntity", visitor.getComputedFQN(),
		    "modules_app file should get FQN via ColdBox implicit mapping" );
	}

	// ─── Cycle 6 ─────────────────────────────────────────────────────────────
	// modules/BCrypt/models/BCrypt.bx gets FQN BCrypt.models.BCrypt

	@Test
	void coldBoxThirdPartyModuleFQNComputation( @TempDir Path tempDir ) throws Exception {
		Path appCfc = tempDir.resolve( "Application.cfc" );
		Files.writeString( appCfc, "component extends=\"coldbox.system.Bootstrap\" {}" );

		Path bcryptBx = tempDir.resolve( "modules/BCrypt/models/BCrypt.bx" );
		Files.createDirectories( bcryptBx.getParent() );
		Files.writeString( bcryptBx, "class {}" );

		MappingResolver.invalidate( tempDir );
		MappingConfig		config	= MappingResolver.resolve( tempDir );

		ProjectIndexVisitor	visitor	= parseAndVisit( bcryptBx.toUri(), tempDir, config );
		assertEquals( "BCrypt.models.BCrypt", visitor.getComputedFQN(),
		    "modules/ file should get FQN via ColdBox implicit mapping" );
	}

	// ─── Cycle 7 ─────────────────────────────────────────────────────────────
	// End-to-end: ProjectIndex resolves core.models.BaseEntity from module files

	@Test
	void projectIndexResolvesModuleClassReferences() throws Exception {
		java.nio.file.Path tempDir = Files.createTempDirectory( "coldboxIndex" );
		try {
			// ColdBox app root
			Path appCfc = tempDir.resolve( "Application.cfc" );
			Files.writeString( appCfc, "component extends=\"coldbox.system.Bootstrap\" {}" );

			// modules_app/core/models/BaseEntity.bx
			Path baseEntityBx = tempDir.resolve( "modules_app/core/models/BaseEntity.bx" );
			Files.createDirectories( baseEntityBx.getParent() );
			Files.writeString( baseEntityBx,
			    "class accessors=\"true\" {\n" +
			        "    property name=\"id\";\n" +
			        "}\n" );

			// modules_app/core/models/IngestionJob.bx
			Path ingestionJobBx = tempDir.resolve( "modules_app/core/models/IngestionJob.bx" );
			Files.createDirectories( ingestionJobBx.getParent() );
			Files.writeString( ingestionJobBx,
			    "class extends=\"core.models.BaseEntity\" {\n" +
			        "    property name=\"jobId\";\n" +
			        "}\n" );

			// modules/BCrypt/models/BCrypt.bx
			Path bcryptBx = tempDir.resolve( "modules/BCrypt/models/BCrypt.bx" );
			Files.createDirectories( bcryptBx.getParent() );
			Files.writeString( bcryptBx,
			    "class {\n" +
			        "    function hash() { return \"\"; }\n" +
			        "}\n" );

			MappingResolver.invalidate( tempDir );
			MappingConfig	config	= MappingResolver.resolve( tempDir );

			ProjectIndex	index	= new ProjectIndex();
			index.initialize( tempDir, config );

			// Index all three files
			index.indexFile( baseEntityBx.toUri() );
			index.indexFile( ingestionJobBx.toUri() );
			index.indexFile( bcryptBx.toUri() );

			// Verify BaseEntity is found by its ColdBox FQN
			Optional<IndexedClass> baseEntity = index.findClassByFQN( "core.models.BaseEntity" );
			assertTrue( baseEntity.isPresent(),
			    "ProjectIndex should resolve core.models.BaseEntity via ColdBox implicit mapping" );

			// Verify IngestionJob is found by its ColdBox FQN
			Optional<IndexedClass> ingestionJob = index.findClassByFQN( "core.models.IngestionJob" );
			assertTrue( ingestionJob.isPresent(),
			    "ProjectIndex should resolve core.models.IngestionJob via ColdBox implicit mapping" );

			// Verify BCrypt module model is found
			Optional<IndexedClass> bcrypt = index.findClassByFQN( "BCrypt.models.BCrypt" );
			assertTrue( bcrypt.isPresent(),
			    "ProjectIndex should resolve BCrypt.models.BCrypt via ColdBox implicit mapping" );

			// Verify IngestionJob correctly records its extends relationship
			assertEquals( "core.models.BaseEntity", ingestionJob.get().extendsClass(),
			    "IngestionJob should record extends=core.models.BaseEntity" );
		} finally {
			try ( java.util.stream.Stream<Path> walk = Files.walk( tempDir ) ) {
				walk.sorted( java.util.Comparator.reverseOrder() )
				    .forEach( p -> {
					    try {
						    Files.deleteIfExists( p );
					    } catch ( java.io.IOException ignored ) {
					    }
				    } );
			}
		}
	}

	private ProjectIndexVisitor parseAndVisit( URI fileUri, Path workspaceRoot, ortus.boxlang.lsp.workspace.MappingConfig mappingConfig )
	    throws Exception {
		Parser			parser	= new Parser();
		ParsingResult	result	= parser.parse( Path.of( fileUri ).toFile() );
		assertNotNull( result, "Parsing should succeed" );
		assertNotNull( result.getRoot(), "AST root should not be null" );

		ProjectIndexVisitor visitor = new ProjectIndexVisitor( fileUri, workspaceRoot, mappingConfig );
		result.getRoot().accept( visitor );
		return visitor;
	}
}
