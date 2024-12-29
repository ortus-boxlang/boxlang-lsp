package ortus.boxlang.lsp.workspace.rules;

public interface IRule<T, U> {

    public boolean when( T facts );

    public void then( T facts, U result );

    default public boolean stop() {
        return false;
    }
}
