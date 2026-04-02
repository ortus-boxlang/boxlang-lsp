package ortus.boxlang.lsp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.eclipse.lsp4j.FileChangeType;
import org.eclipse.lsp4j.FileEvent;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ortus.boxlang.lsp.workspace.MappingConfig;
import ortus.boxlang.lsp.workspace.MappingResolver;
import ortus.boxlang.lsp.workspace.ProjectContextProvider;
import ortus.boxlang.lsp.workspace.index.ProjectIndex;

public class CacheInvalidationTest extends BaseTest {

	private static final Path		TEST_PROJECT	= Paths.get( "src/test/resources/test-bx-project" ).toAbsolutePath();
	private ProjectContextProvider	provider;

	@BeforeEach
	void setUp() {
		MappingResolver.invalidate( TEST_PROJECT );

		ProjectIndex index = new ProjectIndex();
		index.initialize( TEST_PROJECT );

		provider = ProjectContextProvider.getInstance();
		provider.setIndex( index );

		WorkspaceFolder folder = new WorkspaceFolder();
		folder.setUri( TEST_PROJECT.toUri().toString() );
		folder.setName( "test-bx-project" );
		provider.setWorkspaceFolders( List.of( folder ) );
	}

	@AfterEach
	void tearDown() {
		MappingResolver.invalidate( TEST_PROJECT );
		if ( provider != null ) {
			provider.setIndex( null );
			provider.setWorkspaceFolders( List.of() );
		}
	}

	// ─── Cycle 1 ──────────────────────────────────────────────────────────────
	// Saving boxlang.json invalidates the MappingResolver workspace cache

	@Test
	void savingBoxlangJsonInvalidatesMappingResolverCache() {
		// Prime the cache
		MappingConfig before = MappingResolver.resolve( TEST_PROJECT );
		assertNotNull( before );

		URI boxlangJsonUri = TEST_PROJECT.resolve( "boxlang.json" ).toUri();

		// Act: simulate a save event for boxlang.json
		provider.handleConfigFileChange( boxlangJsonUri );

		// After invalidation, resolve should produce a NEW object
		MappingConfig after = MappingResolver.resolve( TEST_PROJECT );
		assertNotNull( after );
		assertNotSame( before, after,
		    "MappingResolver cache should have been invalidated — resolve() must return a new instance" );
	}

	// ─── Cycle 2 ──────────────────────────────────────────────────────────────
	// Saving Application.bx invalidates the per-file walk-up cache

	@Test
	void savingApplicationBxInvalidatesPerFileMappingCache() {
		Path	appBxPath	= TEST_PROJECT.resolve( "Application.bx" );
		Path	bxObjFile	= TEST_PROJECT.resolve( "bxObj.bx" );

		assertTrue( Files.exists( appBxPath ),
		    "Application.bx must exist in test-bx-project for this test" );

		// Prime the per-file cache
		MappingConfig before = MappingResolver.resolveForFile( bxObjFile, TEST_PROJECT );
		assertNotNull( before );

		URI appBxUri = appBxPath.toUri();

		// Act: simulate saving Application.bx
		provider.handleConfigFileChange( appBxUri );

		// After invalidation, resolveForFile should produce a NEW object
		MappingConfig after = MappingResolver.resolveForFile( bxObjFile, TEST_PROJECT );
		assertNotNull( after );
		assertNotSame( before, after,
		    "Per-file cache should have been cleared — resolveForFile() must return a new instance" );
	}

	// ─── Cycle 3 ──────────────────────────────────────────────────────────────
	// Changing an unrelated file does NOT invalidate the mapping caches

	@Test
	void savingUnrelatedFileDoesNotInvalidateCache() {
		// Prime the workspace cache
		MappingConfig before = MappingResolver.resolve( TEST_PROJECT );
		assertNotNull( before );

		URI unrelatedUri = TEST_PROJECT.resolve( "bxObj.bx" ).toUri();

		// Act: simulate saving an unrelated .bx file
		provider.handleConfigFileChange( unrelatedUri );

		// Cache should still be the same object — nothing invalidated
		MappingConfig after = MappingResolver.resolve( TEST_PROJECT );
		assertSame( before, after,
		    "Saving an unrelated file must not invalidate the MappingResolver cache" );
	}

	// ─── Cycle 4 ──────────────────────────────────────────────────────────────
	// didChangeWatchedFiles is implemented (no longer throws)

	@Test
	void didChangeWatchedFilesNoLongerThrows() {
		BoxLangWorkspaceService							service	= new BoxLangWorkspaceService();

		org.eclipse.lsp4j.DidChangeWatchedFilesParams	params	= new org.eclipse.lsp4j.DidChangeWatchedFilesParams();
		FileEvent										event	= new FileEvent(
		    TEST_PROJECT.resolve( "boxlang.json" ).toUri().toString(),
		    FileChangeType.Changed );
		params.setChanges( List.of( event ) );

		// Must not throw
		service.didChangeWatchedFiles( params );
	}

	// ─── Cycle 5 ──────────────────────────────────────────────────────────────
	// After boxlang.json change and re-index, a class in a newly-added
	// mapped directory becomes resolvable

	@Test
	void afterBoxlangJsonChangeNewExternalClassBecomesResolvable( @TempDir Path tempDir ) throws Exception {
		Path externalDir = tempDir.resolve( "new-ext" );
		Files.createDirectories( externalDir );

		// Create a BoxLang class in the external dir
		Files.writeString( externalDir.resolve( "NewExternalClass.bx" ), "class {}" );

		// Write a boxlang.json that maps /newext -> the temp external dir
		String	boxlangJson		= "{\n  \"mappings\": {\n    \"/newext\": \"" +
		    externalDir.toString().replace( "\\", "/" ) + "\"\n  }\n}";
		Path	workspaceRoot	= tempDir.resolve( "workspace" );
		Files.createDirectories( workspaceRoot );
		Files.writeString( workspaceRoot.resolve( "boxlang.json" ), boxlangJson );

		// Set up a fresh index + provider for the temp workspace
		MappingResolver.invalidate( workspaceRoot );
		ProjectIndex tempIndex = new ProjectIndex();
		tempIndex.initialize( workspaceRoot );
		provider.setIndex( tempIndex );

		WorkspaceFolder tempFolder = new WorkspaceFolder();
		tempFolder.setUri( workspaceRoot.toUri().toString() );
		tempFolder.setName( "temp-workspace" );
		provider.setWorkspaceFolders( List.of( tempFolder ) );

		// Before: class is not resolvable since the initial index has no external dirs
		assertFalse( tempIndex.findClassByName( "NewExternalClass" ).isPresent(),
		    "NewExternalClass should not be found before config file change event" );

		// Act: simulate a save of boxlang.json (triggers re-index with new mapping)
		provider.handleConfigFileChange( workspaceRoot.resolve( "boxlang.json" ).toUri() );

		// After: NewExternalClass should now be resolvable
		assertTrue( tempIndex.findClassByName( "NewExternalClass" ).isPresent(),
		    "NewExternalClass should be resolvable after boxlang.json reload" );
	}

	// ─── Cycle 6 ──────────────────────────────────────────────────────────────
	// After boxlang.json change, classes from removed mapped directories
	// are no longer in the index

	@Test
	void afterBoxlangJsonChangeRemovedExternalClassIsNoLongerInIndex( @TempDir Path tempDir ) throws Exception {
		Path externalDir = tempDir.resolve( "old-ext" );
		Files.createDirectories( externalDir );
		Files.writeString( externalDir.resolve( "OldExternalClass.bx" ), "class {}" );

		// Initial boxlang.json — has the external mapping
		String	initialJson		= "{\n  \"mappings\": {\n    \"/oldext\": \"" +
		    externalDir.toString().replace( "\\", "/" ) + "\"\n  }\n}";
		Path	workspaceRoot	= tempDir.resolve( "workspace" );
		Path	boxlangJsonPath	= workspaceRoot.resolve( "boxlang.json" );
		Files.createDirectories( workspaceRoot );
		Files.writeString( boxlangJsonPath, initialJson );

		// Initialize with the initial config (includes OldExternalClass)
		MappingResolver.invalidate( workspaceRoot );
		MappingConfig	initialConfig	= MappingResolver.resolve( workspaceRoot );
		ProjectIndex	tempIndex		= new ProjectIndex();
		tempIndex.initialize( workspaceRoot, initialConfig );

		WorkspaceFolder tempFolder = new WorkspaceFolder();
		tempFolder.setUri( workspaceRoot.toUri().toString() );
		tempFolder.setName( "temp-workspace" );
		provider.setIndex( tempIndex );
		provider.setWorkspaceFolders( List.of( tempFolder ) );

		assertTrue( tempIndex.findClassByName( "OldExternalClass" ).isPresent(),
		    "OldExternalClass should be in index initially" );

		// Now update boxlang.json to remove the mapping
		Files.writeString( boxlangJsonPath, "{}" );
		MappingResolver.invalidate( workspaceRoot );

		// Act: simulate saving the updated boxlang.json
		provider.handleConfigFileChange( boxlangJsonPath.toUri() );

		// After: OldExternalClass should be gone from the index
		assertFalse( tempIndex.findClassByName( "OldExternalClass" ).isPresent(),
		    "OldExternalClass should be removed from index after its mapping was deleted from boxlang.json" );
	}
}
