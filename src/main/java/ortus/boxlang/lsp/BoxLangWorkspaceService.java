package ortus.boxlang.lsp;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.WorkspaceDiagnosticParams;
import org.eclipse.lsp4j.WorkspaceDiagnosticReport;
import org.eclipse.lsp4j.WorkspaceDocumentDiagnosticReport;
import org.eclipse.lsp4j.WorkspaceFullDocumentDiagnosticReport;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4j.services.WorkspaceService;

import ortus.boxlang.lsp.workspace.ProjectContextProvider;
import ortus.boxlang.runtime.async.executors.ExecutorRecord;
import ortus.boxlang.runtime.services.AsyncService;

public class BoxLangWorkspaceService implements WorkspaceService {

	@Override
	public void didChangeConfiguration( DidChangeConfigurationParams params ) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException( "Unimplemented method 'didChangeConfiguration'" );
	}

	@Override
	public void didChangeWatchedFiles( DidChangeWatchedFilesParams params ) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException( "Unimplemented method 'didChangeWatchedFiles'" );
	}

	public CompletableFuture<WorkspaceDiagnosticReport> diagnostic( WorkspaceDiagnosticParams params ) {
		return CompletableFutures.computeAsync( ( cancelToken ) -> {
			ProjectContextProvider					provider	= ProjectContextProvider.getInstance();
			List<WorkspaceDocumentDiagnosticReport>	docReports	= new ArrayList<>();

			if ( provider.getWorkspaceFolders() == null || provider.getWorkspaceFolders().isEmpty() ) {
				return new WorkspaceDiagnosticReport();
			}

			ForkJoinPool pool = new ForkJoinPool( 4 );

			try {
				// TODO: This needs to change to `BoxExecutor` once 1.6 is released
				ExecutorRecord executor = AsyncService.chooseParallelExecutor( "LSP_diagnostic", 0, true );
				executor.submitAndGet( () -> {
					try {
						Files
						    .walk( Path.of( new URI( provider.getWorkspaceFolders().getFirst().getUri() ) ) )
						    .parallel()
						    .filter( LSPTools::canWalkFile )
						    .forEach( ( clazzPath ) -> {
							    try {
								    WorkspaceFullDocumentDiagnosticReport fullReport = new WorkspaceFullDocumentDiagnosticReport();
								    fullReport.setItems( provider.getFileDiagnostics( clazzPath.toUri() ) );
								    fullReport.setUri( clazzPath.toUri().toString() );
								    docReports.add( new WorkspaceDocumentDiagnosticReport( fullReport ) );

							    } catch ( Exception e ) {
								    // TODO Auto-generated catch block
								    e.printStackTrace();
							    }
						    } );

					} catch ( IOException | URISyntaxException e ) {
						e.printStackTrace();
					}

				} );
			} catch ( Exception e ) {
				e.printStackTrace();
			} finally {
				pool.shutdown();
				pool.close();
			}

			cancelToken.checkCanceled();

			WorkspaceDiagnosticReport report = new WorkspaceDiagnosticReport();
			report.setItems( docReports );
			return report;
		} );
	}

}
