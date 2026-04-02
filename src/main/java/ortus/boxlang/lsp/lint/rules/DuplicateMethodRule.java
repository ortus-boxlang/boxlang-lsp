package ortus.boxlang.lsp.lint.rules;

import org.eclipse.lsp4j.DiagnosticSeverity;

import ortus.boxlang.lsp.config.annotation.LintRule;
import ortus.boxlang.lsp.lint.DiagnosticRule;

/**
 * Rule for duplicate method definitions within a class.
 */
@LintRule( id = "duplicateMethod", description = "Flags multiple method definitions with the same name within the same class.", defaultSeverity = "error" )
public class DuplicateMethodRule implements DiagnosticRule {

	public static final String ID = "duplicateMethod";

	@Override
	public String getId() {
		return ID;
	}

	@Override
	public DiagnosticSeverity getDefaultSeverity() {
		return DiagnosticSeverity.Error;
	}
}
