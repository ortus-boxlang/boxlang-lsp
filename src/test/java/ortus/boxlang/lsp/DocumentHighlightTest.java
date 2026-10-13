package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentHighlightKind;
import org.eclipse.lsp4j.DocumentHighlightParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ortus.boxlang.lsp.workspace.ProjectContextProvider;
import ortus.boxlang.lsp.workspace.index.ProjectIndex;

/**
 * Tests for Document Highlight (BLIDE-320).
 * Putting the cursor on a local variable or parameter highlights its declaration and
 * uses inside the same function only. Line and column numbers are 0-based, like LSP.
 */
public class DocumentHighlightTest extends BaseTest {

	private static final Path			FIXTURE	= Path.of( "src/test/resources/files/documentHighlightTest.bx" );

	private BoxLangTextDocumentService	svc;
	private String						fileUri;
	private String						source;

	@BeforeEach
	void setUp() throws Exception {
		svc = new BoxLangTextDocumentService();
		ProjectContextProvider.getInstance().setIndex( new ProjectIndex() );

		fileUri	= FIXTURE.toUri().toString();
		source	= Files.readString( FIXTURE );
		svc.didOpen( new DidOpenTextDocumentParams( new TextDocumentItem( fileUri, "boxlang", 1, source ) ) );
	}

	@Test
	void testInitializeAdvertisesDocumentHighlightProvider() throws Exception {
		InitializeParams params = new InitializeParams();
		params.setCapabilities( new ClientCapabilities() );

		var result = new LanguageServer().initialize( params ).get();

		assertThat( result.getCapabilities().getDocumentHighlightProvider().getLeft() ).isTrue();
	}

	@Test
	void testLocalVarHighlightsDeclarationAndUsesInSameFunction() throws Exception {
		// Cursor on `total` in `var total = 0;`. Matching is case-insensitive (TOTAL, Total).
		// The comment, the "total" string, object.total, the closure's total, and other()'s
		// total must all be left out.
		assertThat( highlightsAt( 3, 6 ) ).containsExactly( "3:6-3:11", "4:2-4:7", "4:10-4:15" ).inOrder();
	}

	@Test
	void testCursorOnAnyOccurrenceGivesTheSameResult() throws Exception {
		// Cursor on `Total` (the read in `TOTAL = Total + qty;`)
		assertThat( highlightsAt( 4, 12 ) ).containsExactly( "3:6-3:11", "4:2-4:7", "4:10-4:15" ).inOrder();
	}

	@Test
	void testParameterHighlightsDeclarationAndUses() throws Exception {
		List<String> expected = List.of( "2:24-2:27", "4:18-4:21" );

		// From the parameter in the signature, and from its use
		assertThat( highlightsAt( 2, 24 ) ).containsExactlyElementsIn( expected ).inOrder();
		assertThat( highlightsAt( 4, 18 ) ).containsExactlyElementsIn( expected ).inOrder();
	}

	@Test
	void testSameNameInOtherFunctionIsSeparate() throws Exception {
		// other() has its own `total`; none of calc()'s occurrences should appear
		assertThat( highlightsAt( 15, 6 ) ).containsExactly( "15:6-15:11", "16:9-16:14" ).inOrder();
	}

	@Test
	void testUnsupportedTargetsReturnEmpty() throws Exception {
		assertThat( highlightsAt( 5, 6 ) ).isEmpty(); // inside a comment
		assertThat( highlightsAt( 6, 16 ) ).isEmpty(); // inside the "total" string
		assertThat( highlightsAt( 7, 18 ) ).isEmpty(); // property name in object.total
		assertThat( highlightsAt( 7, 11 ) ).isEmpty(); // `object` is not a declared local or parameter
		assertThat( highlightsAt( 9, 8 ) ).isEmpty(); // `total` inside the nested closure
		assertThat( highlightsAt( 2, 16 ) ).isEmpty(); // parameter type is not the parameter name
		assertThat( highlightsAt( 2, 30 ) ).isEmpty(); // parameter default value is not the parameter name
		assertThat( highlightsAt( 0, 0 ) ).isEmpty(); // class keyword
	}

	@Test
	void testHighlightsUseTextKind() throws Exception {
		List<? extends DocumentHighlight> highlights = svc.documentHighlight( paramsAt( 3, 6 ) ).get();

		assertThat( highlights ).isNotEmpty();
		for ( DocumentHighlight highlight : highlights ) {
			assertThat( highlight.getKind() ).isEqualTo( DocumentHighlightKind.Text );
		}
	}

	@Test
	void testHighlightsUpdateAfterUnsavedEdit() throws Exception {
		// Change `return label;` to `return total;` without saving the file
		String edited = source.replace( "return label;", "return total;" );
		svc.didChange( new DidChangeTextDocumentParams(
		    new VersionedTextDocumentIdentifier( fileUri, 2 ),
		    List.of( new TextDocumentContentChangeEvent( edited ) ) ) );

		assertThat( highlightsAt( 3, 6 ) ).containsExactly( "3:6-3:11", "4:2-4:7", "4:10-4:15", "11:9-11:14" ).inOrder();
	}

	private DocumentHighlightParams paramsAt( int line, int character ) {
		return new DocumentHighlightParams( new TextDocumentIdentifier( fileUri ), new Position( line, character ) );
	}

	/**
	 * Ask the server for highlights and turn each range into "line:col-line:col".
	 */
	private List<String> highlightsAt( int line, int character ) throws Exception {
		return svc.documentHighlight( paramsAt( line, character ) ).get().stream()
		    .map( DocumentHighlight::getRange )
		    .map( r -> r.getStart().getLine() + ":" + r.getStart().getCharacter() + "-"
		        + r.getEnd().getLine() + ":" + r.getEnd().getCharacter() )
		    .toList();
	}
}
