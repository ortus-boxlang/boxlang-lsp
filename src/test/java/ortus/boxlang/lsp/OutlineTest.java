package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;

import ortus.boxlang.lsp.workspace.ProjectContextProvider;

/**
 * Tests for document outline (document symbols) generation.
 */
public class OutlineTest extends BaseTest {

	@Test
	void testItShouldGenerateAnOutlineForBXFiles() {
		Path												p		= Path.of( "src/test/resources/files/outline.bx" );
		Optional<List<Either<SymbolInformation, DocumentSymbol>>>	symbols	= ProjectContextProvider.getInstance()
		    .getDocumentSymbols( p.toAbsolutePath().toUri() );

		assertThat( symbols.isPresent() ).isTrue();
		assertThat( symbols.get().getFirst().getRight().getKind() ).isEqualTo( SymbolKind.Class );
		assertThat( symbols.get().getFirst().getRight().getChildren().size() ).isEqualTo( 5 );

		var children = symbols.get().getFirst().getRight().getChildren();
		// Properties come first (sorted), then methods
		assertThat( children.get( 0 ).getKind() ).isEqualTo( SymbolKind.Property );
		assertThat( children.get( 1 ).getKind() ).isEqualTo( SymbolKind.Property );
		assertThat( children.get( 2 ).getKind() ).isEqualTo( SymbolKind.Method );
		assertThat( children.get( 3 ).getKind() ).isEqualTo( SymbolKind.Method );
		assertThat( children.get( 4 ).getKind() ).isEqualTo( SymbolKind.Method );
	}

	@Test
	void testItShouldGenerateAnOutlineForCFCFiles() {
		Path												p		= Path.of( "src/test/resources/files/outline.cfc" );

		Optional<List<Either<SymbolInformation, DocumentSymbol>>>	symbols	= ProjectContextProvider.getInstance()
		    .getDocumentSymbols( p.toAbsolutePath().toUri() );

		assertThat( symbols.isPresent() ).isTrue();
		assertThat( symbols.get().getFirst().getRight().getKind() ).isEqualTo( SymbolKind.Class );
		assertThat( symbols.get().getFirst().getRight().getChildren().size() ).isEqualTo( 5 );

		var children = symbols.get().getFirst().getRight().getChildren();
		// Properties come first (sorted), then methods
		assertThat( children.get( 0 ).getKind() ).isEqualTo( SymbolKind.Property );
		assertThat( children.get( 1 ).getKind() ).isEqualTo( SymbolKind.Property );
		assertThat( children.get( 2 ).getKind() ).isEqualTo( SymbolKind.Method );
		assertThat( children.get( 3 ).getKind() ).isEqualTo( SymbolKind.Method );
		assertThat( children.get( 4 ).getKind() ).isEqualTo( SymbolKind.Method );
	}
}
