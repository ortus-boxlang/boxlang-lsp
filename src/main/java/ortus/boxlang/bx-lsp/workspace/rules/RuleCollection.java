package ortus.boxlang.lsp.workspace.rules;

import java.util.ArrayList;
import java.util.List;

public class RuleCollection<T, U> {

    private List<IRule<T, U>> rules = new ArrayList<IRule<T, U>>();

    public RuleCollection<T, U> addRule( IRule<T, U> rule ) {
        this.rules.add( rule );
        return this;
    }

    public U execute( T facts, U result ) {
        for ( IRule<T, U> rule : rules ) {
            if ( !rule.when( facts ) ) {
                continue;
            }

            rule.then( facts, result );

            if ( rule.stop() ) {
                return result;
            }
        }

        return result;
    }
}
