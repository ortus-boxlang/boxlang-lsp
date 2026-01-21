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
    List<String> modifiers ) {

	/**
	 * Get a unique key for this method in the form "ClassName.methodName" or just "methodName" for top-level functions.
	 */
	public String getKey() {
		if ( containingClass != null && !containingClass.isEmpty() ) {
			return containingClass + "." + name;
		}
		return name;
	}
}
