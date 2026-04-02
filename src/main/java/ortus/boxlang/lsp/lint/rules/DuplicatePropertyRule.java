package ortus.boxlang.lsp.lint.rules;

import org.eclipse.lsp4j.DiagnosticSeverity;

import ortus.boxlang.lsp.config.annotation.LintRule;
import ortus.boxlang.lsp.lint.DiagnosticRule;

/**
 * Rule for duplicate property definitions within a class.
 */
@LintRule( id = "duplicateProperty", description = "Flags multiple property definitions with the same name within the same class.", defaultSeverity = "error" )
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
