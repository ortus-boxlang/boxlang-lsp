package ortus.boxlang.lsp.workspace.index;

import java.time.Instant;
import java.util.List;

import org.eclipse.lsp4j.Range;

/**
 * Represents an indexed class or interface from a BoxLang file.
 * Contains metadata about inheritance, location, and modification time.
 */
public record IndexedClass(
    String name,
    String fullyQualifiedName,
    String fileUri,
    Range location,
    String extendsClass,
    List<String> implementsInterfaces,
    List<String> modifiers,
    boolean isInterface,
    Instant lastModified ) {

	/**
	 * Get the simple class name (last part of FQN).
	 */
	public String getSimpleName() {
		return name;
	}

	/**
	 * Check if this class extends another class.
	 */
	public boolean hasParent() {
		return extendsClass != null && !extendsClass.isEmpty();
	}

	/**
	 * Check if this class implements any interfaces.
	 */
	public boolean hasInterfaces() {
		return implementsInterfaces != null && !implementsInterfaces.isEmpty();
	}
}
