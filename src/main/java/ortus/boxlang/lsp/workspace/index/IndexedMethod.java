package ortus.boxlang.lsp.workspace.index;

import java.util.List;

import org.eclipse.lsp4j.Range;

/**
 * Represents an indexed method or function from a BoxLang file.
 */
public record IndexedMethod(
    String name,
    String containingClass,
    String fileUri,
    Range location,
    String returnTypeHint,
    List<IndexedParameter> parameters,
    String accessModifier,
    List<String> modifiers,
    String documentation ) {

	/**
	 * Get a unique key for this method in the form "classname.methodname" or just "methodname" for top-level functions.
	 * Keys are lowercase for case-insensitive lookup (BoxLang is case-insensitive).
	 */
	public String getKey() {
		if ( containingClass != null && !containingClass.isEmpty() ) {
			return ( containingClass + "." + name ).toLowerCase();
		}
		return name.toLowerCase();
	}
}
