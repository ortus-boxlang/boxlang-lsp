package ortus.boxlang.lsp;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.WorkspaceDiagnosticParams;
import org.eclipse.lsp4j.WorkspaceDiagnosticReport;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.WorkspaceService;

import ortus.boxlang.lsp.workspace.ProjectContextProvider;

public class BoxLangWorkspaceService implements WorkspaceService {

	private LanguageClient client;

	public void setLanguageClient( LanguageClient client ) {
		this.client = client;
	}

	@Override
	public void didChangeConfiguration( DidChangeConfigurationParams params ) {
		ProjectContextProvider provider = ProjectContextProvider.getInstance();

		provider.setUserSettings( UserSettings.fromChangeConfigurationParams( this.client, params ) );

		// TODO Auto-generated method stub
		// throw new UnsupportedOperationException( "Unimplemented method 'didChangeConfiguration'" );
	}

	@Override
	public void didChangeWatchedFiles( DidChangeWatchedFilesParams params ) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException( "Unimplemented method 'didChangeWatchedFiles'" );
	}

	public CompletableFuture<WorkspaceDiagnosticReport> diagnostic( WorkspaceDiagnosticParams params ) {
		return CompletableFutures.computeAsync( ( cancelToken ) -> {
			ProjectContextProvider		provider	= ProjectContextProvider.getInstance();

			WorkspaceDiagnosticReport	report		= null;
			try {
				report = provider.generateWorkspaceDiagnosticReport().get();
			} catch ( InterruptedException e ) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch ( ExecutionException e ) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			cancelToken.checkCanceled();

			return report;
		} );
	}

}
