package ortus.boxlang.lsp.lint.rules;

import org.eclipse.lsp4j.DiagnosticSeverity;

import ortus.boxlang.lsp.config.annotation.LintRule;
import ortus.boxlang.lsp.lint.DiagnosticRule;

@LintRule( id = "unescapedQueryParam", description = "Flags query string interpolations that should be wrapped in <cfqueryparam>.", defaultSeverity = "warning" )
public class UnescapedQueryParamRule implements DiagnosticRule {

	public static final String ID = "unescapedQueryParam";

	@Override
	public String getId() {
		return ID;
	}

	@Override
	public DiagnosticSeverity getDefaultSeverity() {
		return DiagnosticSeverity.Warning;
	}
}