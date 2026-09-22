package ortus.boxlang.lsp.lint.rules;

import org.eclipse.lsp4j.DiagnosticSeverity;

import ortus.boxlang.lsp.config.annotation.LintRule;
import ortus.boxlang.lsp.lint.DiagnosticRule;

@LintRule( id = "possibleTypo", description = "Reports likely misspellings of hard to detect identifiers.", defaultSeverity = "warning" )
public class PossibleTypoRule implements DiagnosticRule {

	public static final String ID = "possibleTypo";

	@Override
	public String getId() {
		return ID;
	}

	@Override
	public DiagnosticSeverity getDefaultSeverity() {
		return DiagnosticSeverity.Warning;
	}
}
