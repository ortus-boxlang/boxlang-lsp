package ortus.boxlang.lsp.lint.rules;

import org.eclipse.lsp4j.DiagnosticSeverity;

import ortus.boxlang.lsp.config.annotation.LintRule;
import ortus.boxlang.lsp.lint.DiagnosticRule;

/**
 * Rule for literal struct keys that appear more than once in the same struct literal.
 */
@LintRule( id = "duplicateStructKey", description = "Flags struct literal keys that repeat an earlier key in the same struct (case-insensitive), which silently overwrites the earlier value.", defaultSeverity = "warning" )
public class DuplicateStructKeyRule implements DiagnosticRule {

	public static final String ID = "duplicateStructKey";

	@Override
	public String getId() {
		return ID;
	}

	@Override
	public DiagnosticSeverity getDefaultSeverity() {
		return DiagnosticSeverity.Warning;
	}
}
