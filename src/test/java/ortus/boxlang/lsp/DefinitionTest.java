package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertThat;

import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.Test;

public class DefinitionTest extends BaseTest {

	@Test
	void testDefinition() throws Exception {
		var							defPath	= java.nio.file.Paths.get( "src/test/resources/files/definitionTestClass.bx" );

		BoxLangTextDocumentService	svc		= new BoxLangTextDocumentService();
		svc.didOpen( new org.eclipse.lsp4j.DidOpenTextDocumentParams(
		    new org.eclipse.lsp4j.TextDocumentItem( defPath.toUri().toString(), "boxlang", 1, java.nio.file.Files.readString( defPath ) ) ) );

		DefinitionParams defParams = new DefinitionParams();
		defParams.setTextDocument( new TextDocumentIdentifier( defPath.toUri().toString() ) );
		defParams.setPosition( new Position( 6, 18 ) );
		var refs = svc.definition( defParams ).get();

		assertThat( refs ).isNotNull();
		assertThat( refs.getLeft() ).isNotNull();
		assertThat( refs.getLeft().size() ).isGreaterThan( 0 );

		var theDef = refs.getLeft().stream()
		    .filter( loc -> loc.getUri().equals( defPath.toUri().toString() ) )
		    .findFirst()
		    .orElse( null );

		assertThat( theDef ).isNotNull();
		assertThat( theDef.getRange().getStart().getLine() ).isEqualTo( 1 );
		assertThat( theDef.getRange().getStart().getCharacter() ).isEqualTo( 4 );
		assertThat( theDef.getRange().getEnd().getLine() ).isEqualTo( 3 );
		assertThat( theDef.getRange().getEnd().getCharacter() ).isEqualTo( 6 );
	}
}
