package ortus.boxlanglsp;

import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

public class LanguageServer implements org.eclipse.lsp4j.services.LanguageServer, LanguageClientAware {

    private WorkspaceService workspaceService = new BoxLangWorkspaceService();
    private TextDocumentService textDocumentService = new BoxLangTextDocumentService();

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        return CompletableFuture.supplyAsync(() -> {
            ServerCapabilities capabilities = new ServerCapabilities();

            capabilities.setTextDocumentSync(TextDocumentSyncKind.Full);
            capabilities.setDocumentSymbolProvider(true);
            // CompletionOptions completionOptions = new CompletionOptions();
            // completionOptions.
            // capabilities.setCompletionProvider(null);

            return new InitializeResult(capabilities);
        });
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'shutdown'");
    }

    @Override
    public void exit() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'exit'");
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
    public void connect(LanguageClient client) {
        // textDocumentService.setClient(client);
        ((BoxLangTextDocumentService) textDocumentService).setLanguageClient(client);

        client.showMessage(new MessageParams(MessageType.Info, "Connected to the BoxLang Langauge Server!"));
        // TODO Auto-generated method stub
        // throw new UnsupportedOperationException("Unimplemented method 'connect'");
    }

}
