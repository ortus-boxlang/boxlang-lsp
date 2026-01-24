package ortus.boxlang.lsp.workspace.index;

/**
 * Represents a parameter of an indexed method or function.
 */
public record IndexedParameter(
    String name,
    String typeHint,
    boolean required,
    String defaultValue ) {
}
