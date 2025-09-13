package ortus.boxlang.lsp;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionOptions;
import org.eclipse.lsp4j.CodeLensOptions;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.DiagnosticRegistrationOptions;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.SetTraceParams;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

import ortus.boxlang.lsp.workspace.ProjectContextProvider;

public class LanguageServer implements org.eclipse.lsp4j.services.LanguageServer, LanguageClientAware {

	private WorkspaceService		workspaceService		= new BoxLangWorkspaceService();
	private TextDocumentService		textDocumentService		= new BoxLangTextDocumentService();
	private ProjectContextProvider	projectContextProvider	= ProjectContextProvider.getInstance();

	@Override
	public CompletableFuture<InitializeResult> initialize( InitializeParams params ) {
		return CompletableFutures.computeAsync( ( cancelToken ) -> {
			ServerCapabilities capabilities = new ServerCapabilities();

			capabilities.setTextDocumentSync( TextDocumentSyncKind.Full );
			capabilities.setDocumentSymbolProvider( true );
			// capabilities.setDocumentFormattingProvider( true );
			// capabilities.setReferencesProvider(true);
			capabilities.setDefinitionProvider( true );
			CompletionOptions completionOptions = new CompletionOptions();
			completionOptions.setTriggerCharacters( List.of( "." ) );
			// completionOptions.
			capabilities.setCompletionProvider( completionOptions );
			capabilities.setDiagnosticProvider( new DiagnosticRegistrationOptions( false, true ) );
			capabilities.setCodeLensProvider( new CodeLensOptions( true ) );
			capabilities.setCodeActionProvider( new CodeActionOptions( List.of(
			    CodeActionKind.QuickFix,
			    CodeActionKind.SourceFixAll,
			    CodeActionKind.RefactorRewrite
			) ) );

			ProjectContextProvider.getInstance().setWorkspaceFolders( params.getWorkspaceFolders() );

			return new InitializeResult( capabilities );
		} );
	}

	@Override
	public CompletableFuture<Object> shutdown() {
		return CompletableFutures.computeAsync( ( cancelChecker ) -> {
			App.logger.info( "Received shutdown command - shutting down now" );
			return null;
		} );
	}

	@Override
	public void exit() {
		App.logger.info( "Received exit command - exiting" );
		System.exit( 0 );
	}

	@Override
	public void setTrace( SetTraceParams params ) {
		App.logger.info( "Received setTrace command" );
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
		( ( BoxLangWorkspaceService ) workspaceService ).setLanguageClient( client );
		projectContextProvider.setLanguageClient( client );

		client.showMessage( new MessageParams( MessageType.Info, "Connected to the BoxLang Language Server!" ) );
		// TODO Auto-generated method stub
		// throw new UnsupportedOperationException("Unimplemented method 'connect'");
	}

}
