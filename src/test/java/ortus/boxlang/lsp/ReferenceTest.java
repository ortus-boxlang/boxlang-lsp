package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertThat;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.Test;

public class ReferenceTest extends BaseTest {

	@Test
	void testReferences() throws Exception {
		var							defPath	= java.nio.file.Paths.get( "src/test/resources/files/definitionTestClass.bx" );

		BoxLangTextDocumentService	svc		= new BoxLangTextDocumentService();
		svc.didOpen( new org.eclipse.lsp4j.DidOpenTextDocumentParams(
		    new org.eclipse.lsp4j.TextDocumentItem( defPath.toUri().toString(), "boxlang", 1, java.nio.file.Files.readString( defPath ) ) ) );

		ReferenceParams referenceParams = new ReferenceParams();
		referenceParams.setTextDocument( new TextDocumentIdentifier( defPath.toUri().toString() ) );
		referenceParams.setPosition( new Position( 2, 5 ) );
		var refs = svc.references( referenceParams ).get();

		assertThat( refs ).isNotNull();

		var theRef = refs.stream()
		    .filter( loc -> loc.getUri().equals( defPath.toUri().toString() ) )
		    .findFirst()
		    .orElse( null );

		assertThat( theRef ).isNotNull();
		assertThat( theRef.getRange().getStart().getLine() ).isEqualTo( 6 );
		assertThat( theRef.getRange().getStart().getCharacter() ).isEqualTo( 15 );
		assertThat( theRef.getRange().getEnd().getLine() ).isEqualTo( 6 );
		assertThat( theRef.getRange().getEnd().getCharacter() ).isEqualTo( 18 );
	}
}
