package ortus.boxlang.lsp.lint.rules;

import org.eclipse.lsp4j.DiagnosticSeverity;

import ortus.boxlang.lsp.lint.DiagnosticRule;

public class UnscopedVariableRule implements DiagnosticRule {
    public static final String ID = "unscopedVariable";

    @Override
    public String getId() { return ID; }

    @Override
    public DiagnosticSeverity getDefaultSeverity() { return DiagnosticSeverity.Warning; }
}
