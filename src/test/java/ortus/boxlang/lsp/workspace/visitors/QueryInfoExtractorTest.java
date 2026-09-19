package ortus.boxlang.lsp.workspace.visitors;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.List;

import org.junit.jupiter.api.Test;

import ortus.boxlang.compiler.ast.expression.BoxStringLiteral;
import ortus.boxlang.compiler.ast.statement.BoxBufferOutput;
import ortus.boxlang.compiler.ast.statement.component.BoxComponent;

class QueryInfoExtractorTest {

	@Test
	void testOutputWithoutSourceTextDoesNotAbortExtraction() {
		BoxBufferOutput			output		= new BoxBufferOutput( new BoxStringLiteral( "SELECT * FROM items ", null, null ), null, null );
		BoxComponent			query		= new BoxComponent( "query", List.of(), List.of( output ), null, null );

		QueryInfoExtractor[]	extractor	= new QueryInfoExtractor[ 1 ];
		assertDoesNotThrow( () -> extractor[ 0 ] = new QueryInfoExtractor( query ) );
		assertThat( extractor[ 0 ] ).isNotNull();
	}
}
