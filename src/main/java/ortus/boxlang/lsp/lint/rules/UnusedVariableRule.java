package ortus.boxlang.lsp.lint.rules;

import org.eclipse.lsp4j.DiagnosticSeverity;

import ortus.boxlang.lsp.config.annotation.LintRule;
import ortus.boxlang.lsp.lint.DiagnosticRule;

@LintRule( id = "unusedVariable", description = "Flags local variables that are declared but never used in the code.", defaultSeverity = "hint" )
public class UnusedVariableRule implements DiagnosticRule {

	public static final String ID = "unusedVariable";

	@Override
	public String getId() {
		return ID;
	}

	@Override
	public DiagnosticSeverity getDefaultSeverity() {
		return DiagnosticSeverity.Hint;
	}
}
