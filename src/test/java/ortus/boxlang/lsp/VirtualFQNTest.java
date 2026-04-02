package ortus.boxlang.lsp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import ortus.boxlang.lsp.workspace.MappingConfig;
import ortus.boxlang.lsp.workspace.index.IndexedClass;
import ortus.boxlang.lsp.workspace.index.ProjectIndex;
import ortus.boxlang.lsp.workspace.index.ProjectIndexVisitor;

public class VirtualFQNTest extends BaseTest {

	// ─── Cycle 1 ─────────────────────────────────────────────────────────────
	// Workspace root mapped to "/" → subpackage/BaseType.bx gets FQN "subpackage.BaseType"

	@Test
	void fileUnderRootMappingGetsDotSeparatedFQN() {
		Path				workspaceRoot	= Paths.get( "src/test/resources/test-bx-project" ).toAbsolutePath();
		Path				file			= workspaceRoot.resolve( "subpackage/BaseType.bx" );
		MappingConfig		config			= new MappingConfig(
		    Map.of( "/", workspaceRoot ),
		    Collections.emptyList(),
		    Collections.emptyList(),
		    workspaceRoot
		);

		ProjectIndexVisitor	visitor			= new ProjectIndexVisitor( file.toUri(), workspaceRoot, config );
		// Access computed FQN via a one-class index — we need the FQN that was assigned
		String				fqn				= visitor.getComputedFQN();

		assertEquals( "subpackage.BaseType", fqn );
	}

	// ─── Cycle 2 ─────────────────────────────────────────────────────────────
	// External directory mapped to "/myapp" → models/User.bx gets FQN "myapp.models.User"

	@Test
	void fileUnderExternalMappingGetsVirtualFQN() throws Exception {
		Path				workspaceRoot	= Paths.get( "src/test/resources/test-bx-project" ).toAbsolutePath();
		// Simulate an external directory mapped to /myapp
		Path				externalDir		= Paths.get( "/tmp/bx_lsp_test_external" );
		Path				file			= externalDir.resolve( "models/User.bx" );
		MappingConfig		config			= new MappingConfig(
		    Map.of( "/myapp", externalDir ),
		    Collections.emptyList(),
		    Collections.emptyList(),
		    workspaceRoot
		);

		ProjectIndexVisitor	visitor			= new ProjectIndexVisitor( file.toUri(), workspaceRoot, config );
		String				fqn				= visitor.getComputedFQN();

		assertEquals( "myapp.models.User", fqn );
	}

	// ─── Cycle 3 ─────────────────────────────────────────────────────────────
	// Longest (most specific) mapping wins when multiple could match

	@Test
	void longestMappingPathWins() {
		Path				workspaceRoot	= Paths.get( "src/test/resources/test-bx-project" ).toAbsolutePath();
		Path				specificDir		= Paths.get( "/tmp/bx_lsp_test_specific" );
		Path				parentDir		= Paths.get( "/tmp/bx_lsp_test_specific/sub" );
		Path				file			= parentDir.resolve( "Widget.bx" );

		MappingConfig		config			= new MappingConfig(
		    Map.of(
		        "/app", specificDir,          // shorter match
		        "/app.sub", parentDir         // longer (more specific) match
		    ),
		    Collections.emptyList(),
		    Collections.emptyList(),
		    workspaceRoot
		);

		ProjectIndexVisitor	visitor			= new ProjectIndexVisitor( file.toUri(), workspaceRoot, config );
		String				fqn				= visitor.getComputedFQN();

		// "/app.sub" matched → file directly in that dir → FQN is "app.sub.Widget"
		assertEquals( "app.sub.Widget", fqn );
	}

	// ─── Cycle 4 ─────────────────────────────────────────────────────────────
	// File not under any mapped path → existing workspace-root-relative fallback

	@Test
	void unmappedFileFallsBackToWorkspaceRelativeFQN() {
		Path				workspaceRoot	= Paths.get( "src/test/resources/test-bx-project" ).toAbsolutePath();
		Path				file			= workspaceRoot.resolve( "Car.bx" );
		// No mappings at all
		MappingConfig		config			= new MappingConfig(
		    Collections.emptyMap(),
		    Collections.emptyList(),
		    Collections.emptyList(),
		    workspaceRoot
		);

		ProjectIndexVisitor	visitor			= new ProjectIndexVisitor( file.toUri(), workspaceRoot, config );
		String				fqn				= visitor.getComputedFQN();

		assertEquals( "Car", fqn );
	}

	// ─── Cycle 5 ─────────────────────────────────────────────────────────────
	// ProjectIndex.indexFile() passes MappingConfig → FQN stored in index is virtual

	@Test
	void projectIndexUsesVirtualFQNWhenMappingConfigIsSet() {
		Path			workspaceRoot	= Paths.get( "src/test/resources/test-bx-project" ).toAbsolutePath();
		Path			file			= workspaceRoot.resolve( "subpackage/BaseType.bx" );
		MappingConfig	config			= new MappingConfig(
		    Map.of( "/", workspaceRoot ),
		    Collections.emptyList(),
		    Collections.emptyList(),
		    workspaceRoot
		);

		ProjectIndex	index			= new ProjectIndex();
		index.initialize( workspaceRoot, config );
		index.indexFile( file.toUri() );

		Optional<IndexedClass> byFQN = index.findClassByFQN( "subpackage.BaseType" );
		assertTrue( byFQN.isPresent(), "Expected class to be findable by virtual FQN 'subpackage.BaseType'" );
	}
}
