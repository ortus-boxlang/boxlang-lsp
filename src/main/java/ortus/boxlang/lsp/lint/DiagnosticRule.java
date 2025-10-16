package ortus.boxlang.lsp.lint;

import org.eclipse.lsp4j.DiagnosticSeverity;

/**
 * Lightweight descriptor for a diagnostic rule. Visitors implementing rules can reference this to discover
 * their configuration and enabled state. Implementations may be backed by SourceCodeVisitor subclasses.
 */
public interface DiagnosticRule {

    /** Unique, stable identifier (e.g., "unscopedVariable"). */
    String getId();

    /** Default severity if the config file omits one. */
    DiagnosticSeverity getDefaultSeverity();
}
