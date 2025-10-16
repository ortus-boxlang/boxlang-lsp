package ortus.boxlang.lsp.lint;

import java.util.Collections;
import java.util.Map;

import org.eclipse.lsp4j.DiagnosticSeverity;

/** Per‑rule user configuration. */
public class RuleSettings {
    public boolean enabled = true;
    public String severity; // optional, maps to DiagnosticSeverity
    public Map<String, Object> params = Collections.emptyMap();

    public DiagnosticSeverity toSeverityOr( DiagnosticSeverity def ) {
        if ( severity == null ) return def;
        return switch ( severity.toLowerCase() ) {
            case "error" -> DiagnosticSeverity.Error;
            case "information", "info" -> DiagnosticSeverity.Information;
            case "hint" -> DiagnosticSeverity.Hint;
            default -> DiagnosticSeverity.Warning;
        };
    }
}
