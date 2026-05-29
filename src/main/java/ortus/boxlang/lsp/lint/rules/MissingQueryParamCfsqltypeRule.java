package ortus.boxlang.lsp.lint.rules;

import org.eclipse.lsp4j.DiagnosticSeverity;

import ortus.boxlang.lsp.config.annotation.LintRule;
import ortus.boxlang.lsp.lint.DiagnosticRule;

@LintRule( id = "missingQueryParamCfsqltype", description = "Flags <cfqueryparam> tags that are missing a cfsqltype attribute.", defaultSeverity = "warning" )
public class MissingQueryParamCfsqltypeRule implements DiagnosticRule {

	public static final String ID = "missingQueryParamCfsqltype";

	@Override
	public String getId() {
		return ID;
	}

	@Override
	public DiagnosticSeverity getDefaultSeverity() {
		return DiagnosticSeverity.Warning;
	}
}