package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.eclipse.lsp4j.WorkspaceFolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ortus.boxlang.lsp.workspace.DiagnosticReport;
import ortus.boxlang.lsp.workspace.ProjectContextProvider;
import ortus.boxlang.lsp.workspace.index.ProjectIndex;

public class DiagnosticReportCacheTest extends BaseTest {

	private static final Path		TEST_ROOT	= Paths.get( "src/test/resources/files" ).toAbsolutePath();
	private static final Path		TEST_FILE	= TEST_ROOT.resolve( "unusedVariablesTest1.bx" );

	private ProjectContextProvider	provider;

	@BeforeEach
	void setUp() {
		ProjectIndex index = new ProjectIndex();
		index.initialize( TEST_ROOT );

		provider = ProjectContextProvider.getInstance();
		provider.setIndex( index );

		WorkspaceFolder folder = new WorkspaceFolder();
		folder.setUri( TEST_ROOT.toUri().toString() );
		folder.setName( "files" );
		provider.setWorkspaceFolders( List.of( folder ) );
	}

	@AfterEach
	void tearDown() {
		if ( provider != null ) {
			provider.remove( TEST_FILE.toUri() );
			provider.setIndex( null );
			provider.setWorkspaceFolders( List.of() );
		}
	}

	@Test
	void repeatedUpdatesReuseSingleDiagnosticReportPerUri() throws Exception {
		String content = Files.readString( TEST_FILE );

		provider.trackDocumentOpen( TEST_FILE.toUri(), content );

		List<DiagnosticReport> initialReports = provider.getCachedDiagnosticReports().stream()
		    .filter( report -> report.getFileURI().equals( TEST_FILE.toUri() ) )
		    .toList();

		assertThat( initialReports ).hasSize( 1 );
		long initialResultId = initialReports.getFirst().getResultId();

		provider.trackDocumentSave( TEST_FILE.toUri(), content + System.lineSeparator() );

		List<DiagnosticReport> updatedReports = provider.getCachedDiagnosticReports().stream()
		    .filter( report -> report.getFileURI().equals( TEST_FILE.toUri() ) )
		    .toList();

		assertThat( updatedReports ).hasSize( 1 );
		assertThat( updatedReports.getFirst().getResultId() ).isGreaterThan( initialResultId );
	}

	@Test
	void removeClearsCachedDiagnosticReportForUri() throws Exception {
		provider.trackDocumentOpen( TEST_FILE.toUri(), Files.readString( TEST_FILE ) );

		assertThat( provider.getCachedDiagnosticReports().stream()
		    .filter( report -> report.getFileURI().equals( TEST_FILE.toUri() ) )
		    .count() ).isEqualTo( 1 );

		provider.remove( TEST_FILE.toUri() );

		assertThat( provider.getCachedDiagnosticReports().stream()
		    .filter( report -> report.getFileURI().equals( TEST_FILE.toUri() ) )
		    .count() ).isEqualTo( 0 );
	}

	@Test
	void repeatedFilesystemReadsReuseCachedFileParseResult() {
		var	first	= provider.getLatestFileParseResultPublic( TEST_FILE.toUri() );
		var	second	= provider.getLatestFileParseResultPublic( TEST_FILE.toUri() );

		assertThat( first ).isPresent();
		assertThat( second ).isPresent();
		assertThat( second.get() ).isSameInstanceAs( first.get() );
	}
}