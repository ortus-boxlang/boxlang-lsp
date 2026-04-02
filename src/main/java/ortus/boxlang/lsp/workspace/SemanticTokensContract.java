package ortus.boxlang.lsp.workspace;

import java.util.List;

import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensLegend;

/**
 * Shared semantic-token legend and index contract for the BoxLang LSP.
 * Keep ordering immutable: clients decode token indexes by position in these lists.
 */
public final class SemanticTokensContract {

	public static final List<String>			TOKEN_TYPES				= List.of(
	    "function",
	    "method",
	    "property"
	);
	public static final List<String>			TOKEN_MODIFIERS			= List.of(
	    "declaration",
	    "defaultLibrary"
	);

	public static final int						TOKEN_TYPE_FUNCTION		= 0;
	public static final int						TOKEN_TYPE_METHOD		= 1;
	public static final int						TOKEN_TYPE_PROPERTY		= 2;

	public static final int						MODIFIER_DECLARATION	= 1 << 0;
	public static final int						MODIFIER_DEFAULT_LIB	= 1 << 1;

	public static final SemanticTokensLegend	LEGEND					= new SemanticTokensLegend( TOKEN_TYPES, TOKEN_MODIFIERS );

	private SemanticTokensContract() {
	}

	public static SemanticTokens emptyTokens() {
		return new SemanticTokens( List.of() );
	}
}
