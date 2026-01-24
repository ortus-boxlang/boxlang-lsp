package ortus.boxlang.lsp.lint.rules;

import org.eclipse.lsp4j.DiagnosticSeverity;

import ortus.boxlang.lsp.lint.DiagnosticRule;

/**
 * Rule for duplicate property definitions within a class.
 */
public class DuplicatePropertyRule implements DiagnosticRule {

	public static final String ID = "duplicateProperty";

	@Override
	public String getId() {
		return ID;
	}

	@Override
	public DiagnosticSeverity getDefaultSeverity() {
		return DiagnosticSeverity.Error;
	}
}
