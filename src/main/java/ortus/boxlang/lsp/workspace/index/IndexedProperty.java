package ortus.boxlang.lsp.workspace.index;

import org.eclipse.lsp4j.Range;

/**
 * Represents an indexed property from a BoxLang class.
 */
public record IndexedProperty(
    String name,
    String containingClass,
    String fileUri,
    Range location,
    String typeHint,
    String defaultValue,
    boolean hasGetter,
    boolean hasSetter ) {
}
