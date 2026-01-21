package ortus.boxlang.lsp.lint.rules;

import org.eclipse.lsp4j.DiagnosticSeverity;

import ortus.boxlang.lsp.lint.DiagnosticRule;

/**
 * Rule for invalid implements references (interface not found).
 */
public class InvalidImplementsRule implements DiagnosticRule {

	public static final String ID = "invalidImplements";

	@Override
	public String getId() {
		return ID;
	}

	@Override
	public DiagnosticSeverity getDefaultSeverity() {
		return DiagnosticSeverity.Error;
	}
}
