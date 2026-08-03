package ortus.boxlang.lsp.project;

import static com.google.common.truth.Truth.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.lsp4j.WorkspaceFolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ortus.boxlang.lsp.BaseTest;
import ortus.boxlang.lsp.workspace.ProjectContextProvider;
import ortus.boxlang.lsp.workspace.index.ProjectIndex;
import ortus.boxlang.runtime.BoxRuntime;

class GitIgnoreScanningTest extends BaseTest {

	@TempDir
	Path							workspaceRoot;

	private ProjectContextProvider	provider;
	private Path					includedFile;

	@BeforeAll
	static void setUpRuntime() {
		BoxRuntime.getInstance( true );
	}

	@AfterEach
	void tearDown() {
		if ( provider != null ) {
			if ( includedFile != null ) {
				provider.trackDocumentClose( includedFile.toUri() );
			}
			provider.setIndex( null );
			provider.setWorkspaceFolders( List.of() );
		}
	}

	@Test
	void coldOpenWorkspaceScanDoesNotIndexGitignoredFiles() throws Exception {
		Files.writeString( workspaceRoot.resolve( ".gitignore" ), "ignored/\n" );
		Path	ignoredDirectory	= Files.createDirectories( workspaceRoot.resolve( "ignored" ) );
		Path	ignoredFile			= ignoredDirectory.resolve( "Ignored.bx" );
		includedFile = workspaceRoot.resolve( "Included.bx" );
		Files.writeString( ignoredFile, "class {}" );
		Files.writeString( includedFile, "class {}" );

		ProjectIndex index = new ProjectIndex();
		index.initialize( workspaceRoot );
		provider = ProjectContextProvider.getInstance();
		provider.setIndex( index );
		provider.setWorkspaceFolders( List.of( new WorkspaceFolder( workspaceRoot.toUri().toString(), "gitignore-test" ) ) );

		provider.trackDocumentOpen( includedFile.toUri(), Files.readString( includedFile ) );

		assertThat( index.getIndexedFiles() ).contains( includedFile.toUri().toString() );
		assertThat( index.getIndexedFiles() ).doesNotContain( ignoredFile.toUri().toString() );
	}

	@Test
	void gitignoreChangeRemovesNewlyIgnoredFilesFromIndex() throws Exception {
		Path	ignoredDirectory	= Files.createDirectories( workspaceRoot.resolve( "ignored" ) );
		Path	ignoredFile			= ignoredDirectory.resolve( "Ignored.bx" );
		includedFile = workspaceRoot.resolve( "Included.bx" );
		Files.writeString( ignoredFile, "class {}" );
		Files.writeString( includedFile, "class {}" );

		ProjectIndex index = new ProjectIndex();
		index.initialize( workspaceRoot );
		provider = ProjectContextProvider.getInstance();
		provider.setIndex( index );
		provider.setWorkspaceFolders( List.of( new WorkspaceFolder( workspaceRoot.toUri().toString(), "gitignore-test" ) ) );
		provider.trackDocumentOpen( includedFile.toUri(), Files.readString( includedFile ) );
		assertThat( index.getIndexedFiles() ).contains( ignoredFile.toUri().toString() );

		Path gitIgnoreFile = workspaceRoot.resolve( ".gitignore" );
		Files.writeString( gitIgnoreFile, "ignored/\n" );
		provider.handleConfigFileChange( gitIgnoreFile.toUri() ).get();

		assertThat( index.getIndexedFiles() ).doesNotContain( ignoredFile.toUri().toString() );
	}
}
