package ortus.boxlang.lsp.workspace.completion;

import java.util.List;
import java.util.Map;
import org.eclipse.lsp4j.CompletionItem;

/** Optional structured signatures for clients that provide their own CFML editing UI. */
final class CallableCompletionData {

	private CallableCompletionData() {
	}

	static Map<String, Object> parameter( String name, String type, boolean required ) {
		return Map.of( "name", name, "type", type == null ? "any" : type, "required", required, "description", "" );
	}

	static void attach( CompletionItem item, String returns, List<Map<String, Object>> parameters, String description ) {
		item.setData( Map.of( "boxlangCallable", Map.of(
		    "name", item.getLabel(), "syntax", item.getDetail(),
		    "returns", returns == null ? "any" : returns,
		    "description", description == null ? "Project-defined function" : description,
		    "params", parameters, "origin", "project" ) ) );
	}
}
