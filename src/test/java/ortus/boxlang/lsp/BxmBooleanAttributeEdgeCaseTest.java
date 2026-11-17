package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.TextEdit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class BxmBooleanAttributeEdgeCaseTest extends BaseTest {

	@TempDir
	Path tempDir;

	@Test
	void completingInsideExistingValueDoesNotDuplicateSuffix() throws Exception {
		String					source	= "<bx:setting showDebugOutput=\"true\">";

		// The cursor is between "tr" and "ue" in the existing value.
		List<CompletionItem>	items	= complete( "existingValue.bxm", source, new Position( 0, 31 ) );
		CompletionItem			item	= items.stream()
		    .filter( candidate -> candidate.getLabel().equals( "true" ) )
		    .findFirst()
		    .orElseThrow();

		assertThat( item.getTextEdit() ).isNotNull();
		assertThat( item.getTextEdit().isLeft() ).isTrue();
		TextEdit edit = item.getTextEdit().getLeft();
		assertThat( edit.getRange().getStart().getLine() ).isEqualTo( 0 );
		assertThat( edit.getRange().getEnd().getLine() ).isEqualTo( 0 );

		// Apply the server's edit exactly as the client would, including the remaining suffix.
		String edited = source.substring( 0, edit.getRange().getStart().getCharacter() )
		    + edit.getNewText()
		    + source.substring( edit.getRange().getEnd().getCharacter() );

		assertThat( edited ).isEqualTo( "<bx:setting showDebugOutput=\"true\">" );
	}

	@Test
	void spacesAroundEarlierAttributeEqualsDoNotDisableBooleanValues() throws Exception {
		String					source	= "<bx:setting requestTimeout = \"30\" showDebugOutput=\"\">";

		// The cursor is between the empty quotes of showDebugOutput.
		List<CompletionItem>	items	= complete( "earlierAttribute.bxm", source, new Position( 0, 51 ) );

		assertThat( items.stream().map( CompletionItem::getLabel ).toList() ).containsExactly( "true", "false" );
	}

	@Test
	void multilineTagOffersBooleanValues() throws Exception {
		String					source	= "<bx:setting\n    showDebugOutput=\"\">";

		// The opening tag starts on the previous line. The cursor is between the empty quotes.
		List<CompletionItem>	items	= complete( "multiline.bxm", source, new Position( 1, 21 ) );

		assertThat( items.stream().map( CompletionItem::getLabel ).toList() ).containsExactly( "true", "false" );
	}

	@Test
	void ordinaryScriptStringDoesNotOfferBooleanAttributeValues() throws Exception {
		String					source	= "var markup = \"<bx:setting showDebugOutput=''>\";";

		// The cursor is between the single quotes inside a script string, not in a template tag.
		List<CompletionItem>	items	= complete( "scriptString.bxs", source, new Position( 0, 43 ) );

		assertThat( items.stream().map( CompletionItem::getLabel ).toList() ).containsNoneOf( "true", "false" );
	}

	private List<CompletionItem> complete( String filename, String source, Position position ) throws Exception {
		Path file = tempDir.resolve( filename );
		Files.writeString( file, source );
		String						uri	= file.toUri().toString();
		BoxLangTextDocumentService	svc	= new BoxLangTextDocumentService();
		svc.didOpen( new DidOpenTextDocumentParams( new TextDocumentItem( uri, "boxlang", 1, source ) ) );

		var response = svc.completion( new CompletionParams( new TextDocumentIdentifier( uri ), position ) ).get();
		return response.isLeft() ? response.getLeft() : response.getRight().getItems();
	}
}
