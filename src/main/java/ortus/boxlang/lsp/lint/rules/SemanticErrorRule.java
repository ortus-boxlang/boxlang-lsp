package ortus.boxlang.lsp.lint.rules;

import org.eclipse.lsp4j.DiagnosticSeverity;

import ortus.boxlang.lsp.lint.DiagnosticRule;

/**
 * Rule for semantic error diagnostics.
 * Covers: invalid extends, invalid implements, duplicate definitions.
 */
public class SemanticErrorRule implements DiagnosticRule {

	public static final String ID = "semanticError";

	@Override
	public String getId() {
		return ID;
	}

	@Override
	public DiagnosticSeverity getDefaultSeverity() {
		return DiagnosticSeverity.Error;
	}
}
