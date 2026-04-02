package ortus.boxlang.lsp.lint.rules;

import org.eclipse.lsp4j.DiagnosticSeverity;

import ortus.boxlang.lsp.config.annotation.LintRule;
import ortus.boxlang.lsp.lint.DiagnosticRule;

@LintRule( id = "unscopedVariable", description = "Flags variables that are used without an explicit scope prefix (e.g. variables.foo instead of foo).", defaultSeverity = "warning" )
public class UnscopedVariableRule implements DiagnosticRule {

	public static final String ID = "unscopedVariable";

	@Override
	public String getId() {
		return ID;
	}

	@Override
	public DiagnosticSeverity getDefaultSeverity() {
		return DiagnosticSeverity.Warning;
	}
}
