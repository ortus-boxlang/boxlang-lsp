package ortus.boxlanglsp;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolCapabilities;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentRegistrationOptions;
import org.eclipse.lsp4j.adapters.DocumentSymbolResponseAdapter;
import org.eclipse.lsp4j.jsonrpc.json.ResponseJsonAdapter;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.TextDocumentService;

import ortus.boxlang.compiler.ast.BoxNode;
import ortus.boxlang.compiler.ast.Issue;
import ortus.boxlang.compiler.javaboxpiler.JavaBoxpiler;
import ortus.boxlang.compiler.parser.ParsingResult;

public class BoxLangTextDocumentService implements TextDocumentService {

    private LanguageClient client;

    public void setLanguageClient(LanguageClient client) {
        this.client = client;
    }

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        System.out.println("The file was opened");
        System.out.println(params.getTextDocument().getUri());
        // TODO Auto-generated method stub
        // throw new UnsupportedOperationException("Unimplemented method 'didOpen'");
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'didChange'");
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        System.out.println("The file was closed");
        System.out.println(params.getTextDocument().getUri());
        // throw new UnsupportedOperationException("Unimplemented method 'didClose'");
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        System.out.println("The file was saved");
        System.out.println(params.getTextDocument().getUri());
        // TODO Auto-generated method stub
        // throw new UnsupportedOperationException("Unimplemented method 'didSave'");
    }

    /**
     * The document symbol request is sent from the client to the server to list all
     * symbols found in a given text document.
     * <p>
     * Registration Options: {@link TextDocumentRegistrationOptions}
     * <p>
     * <b>Caveat</b>: although the return type allows mixing the
     * {@link DocumentSymbol} and {@link SymbolInformation} instances into a list do
     * not do it because the clients cannot accept a heterogeneous list. A list of
     * {@code DocumentSymbol} instances is only a valid return value if the
     * {@link DocumentSymbolCapabilities#getHierarchicalDocumentSymbolSupport()
     * textDocument.documentSymbol.hierarchicalDocumentSymbolSupport} is
     * {@code true}. More details on this difference between the LSP and the LSP4J
     * can be found
     * <a href="https://github.com/eclipse-lsp4j/lsp4j/issues/252">here</a>.
     * </p>
     * Servers should whenever possible return {@link DocumentSymbol} since it is
     * the richer data structure.
     */
    @JsonRequest
    @ResponseJsonAdapter(DocumentSymbolResponseAdapter.class)
    @Override
    public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(
            DocumentSymbolParams params) {

        return CompletableFuture.supplyAsync(() -> {
            URI fileURI = URI.create(params.getTextDocument().getUri());
            Path path = Paths.get(fileURI);
            ParsingResult result = JavaBoxpiler.getInstance()
                    .parse(path.toFile());

            DocumentSymbolBoxNodeVisitor visitor = new DocumentSymbolBoxNodeVisitor();

            visitor.setFilePath(path);

            BoxNode root = result.getRoot();

            if (root != null) {
                result.getRoot().accept(visitor);
            }

            List<Issue> issues = result.getIssues();

            return visitor.getDocumentSymbols();
        });
    }

}
