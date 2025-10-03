package ortus.boxlang.lsp;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.WorkspaceDiagnosticParams;
import org.eclipse.lsp4j.WorkspaceDiagnosticReport;
import org.eclipse.lsp4j.WorkspaceDocumentDiagnosticReport;
import org.eclipse.lsp4j.WorkspaceFullDocumentDiagnosticReport;
import org.eclipse.lsp4j.WorkspaceUnchangedDocumentDiagnosticReport;
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
		ProjectContextProvider	provider	= ProjectContextProvider.getInstance();
		var						oldSettings	= provider.getUserSettings();
		var						newSettings	= UserSettings.fromChangeConfigurationParams( this.client, params );

		if ( oldSettings.isEnableBackgroundParsing() == false && newSettings.isEnableBackgroundParsing() == true ) {
			// if we are enabling background parsing, kick off a parse of the workspace
			provider.parseWorkspace();
		}

		provider.setUserSettings( newSettings );
	}

	@Override
	public void didChangeWatchedFiles( DidChangeWatchedFilesParams params ) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException( "Unimplemented method 'didChangeWatchedFiles'" );
	}

	public CompletableFuture<WorkspaceDiagnosticReport> diagnostic( WorkspaceDiagnosticParams params ) {
		return CompletableFutures.computeAsync( ( cancelToken ) -> {
			ProjectContextProvider					provider	= ProjectContextProvider.getInstance();
			WorkspaceDiagnosticReport				report		= new WorkspaceDiagnosticReport();
			List<WorkspaceDocumentDiagnosticReport>	docReports	= new ArrayList<>();

			report.setItems( docReports );

			provider.getCachedDiagnosticReports().stream()
			    .forEach( cachedFileDiagnostics -> {
				    for ( var prevId : params.getPreviousResultIds() ) {
					    if ( cachedFileDiagnostics.matches( prevId ) ) {
						    // TODO the null value needs to check if the file is in an open state and return the version identifier
						    WorkspaceDocumentDiagnosticReport docReport = new WorkspaceDocumentDiagnosticReport(
						        new WorkspaceUnchangedDocumentDiagnosticReport( prevId.getValue(), cachedFileDiagnostics.getFileURI().toString(), null ) );

						    docReports.add( docReport );
						    return;
					    }
				    }

				    WorkspaceFullDocumentDiagnosticReport fullReport = new WorkspaceFullDocumentDiagnosticReport();
				    WorkspaceDocumentDiagnosticReport	docReport	= new WorkspaceDocumentDiagnosticReport( fullReport );
				    fullReport.setResultId( String.valueOf( cachedFileDiagnostics.getResultId() ) );
				    fullReport.setUri( cachedFileDiagnostics.getFileURI().toString() );
				    fullReport.setItems( cachedFileDiagnostics.getDiagnostics() );

				    docReports.add( docReport );
			    } );

			cancelToken.checkCanceled();

			return report;
		} );
	}

}
