package ortus.boxlang.lsp;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.lsp4j.CodeLensOptions;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

import ortus.boxlang.lsp.workspace.ProjectContextProvider;

public class LanguageServer implements org.eclipse.lsp4j.services.LanguageServer, LanguageClientAware {

	private WorkspaceService		workspaceService		= new BoxLangWorkspaceService();
	private TextDocumentService		textDocumentService		= new BoxLangTextDocumentService();
	private ProjectContextProvider	projectContextProvider	= ProjectContextProvider.getInstance();

	@JsonNotification( value = "boxlang/changesettings", useSegment = false )
	public CompletableFuture<Void> changeSettings( ChangeSettingParams params ) {

		projectContextProvider.setShouldPublishDiagnostics( params.enableExperimentalDiagnostics );
		return CompletableFuture.completedFuture( null );
	}

	@Override
	public CompletableFuture<InitializeResult> initialize( InitializeParams params ) {
		return CompletableFuture.supplyAsync( () -> {
			ServerCapabilities capabilities = new ServerCapabilities();

			capabilities.setTextDocumentSync( TextDocumentSyncKind.Full );
			capabilities.setDocumentSymbolProvider( true );
			capabilities.setDocumentFormattingProvider( true );
			// capabilities.setReferencesProvider(true);
			capabilities.setDefinitionProvider( true );
			CompletionOptions completionOptions = new CompletionOptions();
			// completionOptions.
			capabilities.setCompletionProvider( completionOptions );

			capabilities.setCodeLensProvider( new CodeLensOptions( true ) );

			// removing this until we improve the parser
			// scanWorkspaceFolders(params.getWorkspaceFolders());

			return new InitializeResult( capabilities );
		} );
	}

	@Override
	public CompletableFuture<Object> shutdown() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException( "Unimplemented method 'shutdown'" );
	}

	@Override
	public void exit() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException( "Unimplemented method 'exit'" );
	}

	@Override
	public TextDocumentService getTextDocumentService() {
		return textDocumentService;
	}

	@Override
	public WorkspaceService getWorkspaceService() {
		return workspaceService;
	}

	@Override
	public void connect( LanguageClient client ) {

		// textDocumentService.setClient(client);
		( ( BoxLangTextDocumentService ) textDocumentService ).setLanguageClient( client );
		projectContextProvider.setLanguageClient( client );

		client.showMessage( new MessageParams( MessageType.Info, "Connected to the BoxLang Language Server!" ) );
		// TODO Auto-generated method stub
		// throw new UnsupportedOperationException("Unimplemented method 'connect'");
	}

	private void scanWorkspaceFolders( List<WorkspaceFolder> folders ) {
		ProjectContextProvider provider = ProjectContextProvider.getInstance();
		try {
			Files
			    .walk( Path.of( new URI( folders.get( 0 ).getUri() ) ) )
			    .filter( Files::isRegularFile )
			    .filter( ( path ) -> StringUtils.endsWithAny( path.toString(), ".bx", ".bxs", ".bxm", ".cfc", ".cfs",
			        ".cfm" ) )
			    .forEach( ( clazzPath ) -> {
				    try {

					    provider.consumeFile( clazzPath.toUri() );
				    } catch ( Exception e ) {
					    // TODO Auto-generated catch block
					    e.printStackTrace();
				    }
			    } );
		} catch ( IOException | URISyntaxException e ) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
