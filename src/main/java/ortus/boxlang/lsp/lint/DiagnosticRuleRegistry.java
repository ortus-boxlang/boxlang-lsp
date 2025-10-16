package ortus.boxlang.lsp.lint;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Holds registered diagnostic rules. SourceCodeVisitorService can leverage this later for dynamic enablement. */
public class DiagnosticRuleRegistry {
    private static final DiagnosticRuleRegistry INSTANCE = new DiagnosticRuleRegistry();
    private final Map<String, DiagnosticRule> rules = new ConcurrentHashMap<>();

    public static DiagnosticRuleRegistry getInstance() { return INSTANCE; }

    public void register( DiagnosticRule rule ) {
        if ( rule == null || rule.getId() == null ) return;
        rules.putIfAbsent( rule.getId(), rule );
    }

    public DiagnosticRule get( String id ) { return rules.get( id ); }

    public boolean isEnabled( String id, boolean defaultValue ) {
        var cfg = LintConfigLoader.get().forRule( id );
        return cfg == null ? defaultValue : cfg.enabled;
    }

    public Collection<DiagnosticRule> all() { return Collections.unmodifiableCollection( rules.values() ); }
}
