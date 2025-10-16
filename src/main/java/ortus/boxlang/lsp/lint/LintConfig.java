package ortus.boxlang.lsp.lint;

import java.util.Collections;
import java.util.Map;

/** Root configuration loaded from .boxlang-lsp.json */
public class LintConfig {

	public Map<String, RuleSettings> diagnostics = Collections.emptyMap();

	public RuleSettings forRule( String id ) {
		return diagnostics == null ? null : diagnostics.get( id );
	}
}
