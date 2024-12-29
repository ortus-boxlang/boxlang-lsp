package ortus.boxlang.lsp;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolCapabilities;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentRegistrationOptions;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.adapters.DocumentSymbolResponseAdapter;
import org.eclipse.lsp4j.adapters.LocationLinkListAdapter;
import org.eclipse.lsp4j.jsonrpc.json.ResponseJsonAdapter;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.TextDocumentService;

import ortus.boxlanglsp.workspace.ProjectContextProvider;

public class BoxLangTextDocumentService implements TextDocumentService {

    private LanguageClient client;

    public void setLanguageClient( LanguageClient client ) {
        this.client = client;
    }

    @JsonRequest
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion( CompletionParams position ) {
        return CompletableFuture.supplyAsync( () -> {
            return Either.forLeft( ProjectContextProvider.getInstance()
                .getAvaialbeCompletions( LSPTools.convertDocumentURI( position.getTextDocument().getUri() ),
                    position ) );
        } );
    }

    @Override
    public void didOpen( DidOpenTextDocumentParams params ) {
        ProjectContextProvider.getInstance().trackDocumentOpen(
            LSPTools.convertDocumentURI( params.getTextDocument().getUri() ),
            params.getTextDocument().getText() );
        System.out.println( "The file was opened" );
        System.out.println( params.getTextDocument().getUri() );
    }

    @Override
    public void didChange( DidChangeTextDocumentParams params ) {
        ProjectContextProvider.getInstance().trackDocumentChange(
            LSPTools.convertDocumentURI( params.getTextDocument().getUri() ),
            params.getContentChanges() );
        // TODO Auto-generated method stub
        // throw new UnsupportedOperationException("Unimplemented method 'didChange'");
    }

    @Override
    public void didClose( DidCloseTextDocumentParams params ) {
        ProjectContextProvider.getInstance()
            .trackDocumentClose( LSPTools.convertDocumentURI( params.getTextDocument().getUri() ) );
        System.out.println( "The file was closed" );
        System.out.println( params.getTextDocument().getUri() );
        // throw new UnsupportedOperationException("Unimplemented method 'didClose'");
    }

    @Override
    public void didSave( DidSaveTextDocumentParams params ) {
        ProjectContextProvider.getInstance().trackDocumentSave(
            LSPTools.convertDocumentURI( params.getTextDocument().getUri() ),
            params.getText() );
        System.out.println( "The file was saved" );
        System.out.println( params.getTextDocument().getUri() );
        // TODO Auto-generated method stub
        // throw new UnsupportedOperationException("Unimplemented method 'didSave'");
    }

    /**
     * The document formatting request is sent from the client to the server to
     * format a whole document.
     * <p>
     * Registration Options:
     * {@link org.eclipse.lsp4j.DocumentFormattingRegistrationOptions}
     */
    @JsonRequest
    public CompletableFuture<List<? extends TextEdit>> formatting( DocumentFormattingParams params ) {
        return CompletableFuture.supplyAsync( () -> {
            return ProjectContextProvider.getInstance()
                .formatDocument( LSPTools.convertDocumentURI( params.getTextDocument().getUri() ) );
        } );
    }

    /**
     * The goto definition request is sent from the client to the server to resolve
     * the definition location of a symbol at a given text document position.
     * <p>
     * Registration Options: {@link org.eclipse.lsp4j.DefinitionRegistrationOptions}
     */
    @JsonRequest
    @ResponseJsonAdapter( LocationLinkListAdapter.class )
    public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(
        DefinitionParams params ) {

        try {
            URI docURI = new URI( params.getTextDocument().getUri() );

            return CompletableFuture.supplyAsync( () -> {
                return Either
                    .forLeft( ProjectContextProvider.getInstance().findDefinitionPossibiltiies( docURI,
                        params.getPosition() ) );
            } );
        } catch ( URISyntaxException e ) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return CompletableFuture.supplyAsync( () -> {
            return Either.forLeft( null );
        } );

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
    @ResponseJsonAdapter( DocumentSymbolResponseAdapter.class )
    @Override
    public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(
        DocumentSymbolParams params ) {

        return CompletableFuture.supplyAsync( () -> {

            return ProjectContextProvider.getInstance()
                .getDocumentSymbols( URI.create( params.getTextDocument().getUri() ) )
                .orElseGet( () -> new ArrayList<Either<SymbolInformation, DocumentSymbol>>() );
        } );
    }

}
