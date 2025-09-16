package ortus.boxlang.lsp;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentDiagnosticParams;
import org.eclipse.lsp4j.DocumentDiagnosticReport;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolCapabilities;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.RelatedFullDocumentDiagnosticReport;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentRegistrationOptions;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.adapters.CodeActionResponseAdapter;
import org.eclipse.lsp4j.adapters.DocumentDiagnosticReportTypeAdapter;
import org.eclipse.lsp4j.adapters.DocumentSymbolResponseAdapter;
import org.eclipse.lsp4j.adapters.LocationLinkListAdapter;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4j.jsonrpc.json.ResponseJsonAdapter;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.services.TextDocumentService;

import ortus.boxlang.lsp.workspace.ProjectContextProvider;

public class BoxLangTextDocumentService implements TextDocumentService {

	@JsonRequest
	public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion( CompletionParams position ) {
		return CompletableFutures.computeAsync( ( cancelToken ) -> {
			return Either.forLeft( ProjectContextProvider.getInstance()
			    .getAvailableCompletions( LSPTools.convertDocumentURI( position.getTextDocument().getUri() ),
			        position ) );
		} );
	}

	@Override
	public void didOpen( DidOpenTextDocumentParams params ) {
		ProjectContextProvider.getInstance().trackDocumentOpen(
		    LSPTools.convertDocumentURI( params.getTextDocument().getUri() ),
		    params.getTextDocument().getText() );
		App.logger.debug( "The file was opened" );
		App.logger.debug( params.getTextDocument().getUri() );
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
		App.logger.debug( "The file was closed" );
		App.logger.debug( params.getTextDocument().getUri() );
		// throw new UnsupportedOperationException("Unimplemented method 'didClose'");
	}

	@Override
	public void didSave( DidSaveTextDocumentParams params ) {
		ProjectContextProvider.getInstance().trackDocumentSave(
		    LSPTools.convertDocumentURI( params.getTextDocument().getUri() ),
		    params.getText() );
		App.logger.debug( "The file was saved" );
		App.logger.debug( params.getTextDocument().getUri() );
		// TODO Auto-generated method stub
		// throw new UnsupportedOperationException("Unimplemented method 'didSave'");
	}

	@ResponseJsonAdapter( DocumentDiagnosticReportTypeAdapter.class )
	public CompletableFuture<DocumentDiagnosticReport> diagnostic( DocumentDiagnosticParams params ) {

		return CompletableFutures.computeAsync( ( cancelToken ) -> {
			URI									docURI	= LSPTools.convertDocumentURI( params.getTextDocument().getUri() );
			RelatedFullDocumentDiagnosticReport	rep		= new RelatedFullDocumentDiagnosticReport(
			    ProjectContextProvider.getInstance().getFileDiagnostics( docURI ) );
			DocumentDiagnosticReport			ddr		= new DocumentDiagnosticReport( rep );

			cancelToken.checkCanceled();

			return ddr;

		} );
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
		return CompletableFutures.computeAsync( ( cancelToken ) -> {
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

			return CompletableFutures.computeAsync( ( cancelToken ) -> {
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

		return CompletableFutures.computeAsync( ( cancelToken ) -> {

			return ProjectContextProvider.getInstance()
			    .getDocumentSymbols( URI.create( params.getTextDocument().getUri() ) )
			    .orElseGet( () -> new ArrayList<Either<SymbolInformation, DocumentSymbol>>() );
		} );
	}

	/**
	 * The code lens request is sent from the client to the server to compute
	 * code lenses for a given text document.
	 * <p>
	 * Registration Options: {@link org.eclipse.lsp4j.CodeLensRegistrationOptions}
	 */
	@JsonRequest
	@Override
	public CompletableFuture<List<? extends CodeLens>> codeLens( CodeLensParams params ) {

		return CompletableFutures.computeAsync( ( cancelToken ) -> {
			return ProjectContextProvider.getInstance()
			    .getAvailableCodeLenses(
			        LSPTools.convertDocumentURI( params.getTextDocument().getUri() ),
			        params
			    );
		} );
	}

	@JsonRequest
	@ResponseJsonAdapter( CodeActionResponseAdapter.class )
	public CompletableFuture<List<Either<Command, CodeAction>>> codeAction( CodeActionParams params ) {
		return CompletableFutures.computeAsync( ( cancelToken ) -> {
			var result = ProjectContextProvider.getInstance()
			    .getAvailableCodeActions(
			        LSPTools.convertDocumentURI( params.getTextDocument().getUri() ),
			        params
			    );

			cancelToken.checkCanceled();

			return result;
		} );

	}

}
