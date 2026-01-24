package ortus.boxlang.lsp.workspace.completion;

/**
 * Result of type inference for an expression.
 * Used to determine what completions to offer after a dot.
 */
public record TypeInferenceResult(
    String className,
    String fullyQualifiedName,
    InferenceConfidence confidence,
    String source ) {

	public enum InferenceConfidence {
		/** Type explicitly declared via type hint */
		HIGH,
		/** Type inferred from new expression or return type */
		MEDIUM,
		/** Type inferred from less reliable sources */
		LOW,
		/** Could not determine type */
		UNKNOWN
	}

	/**
	 * Create an unknown result when type cannot be inferred.
	 */
	public static TypeInferenceResult unknown() {
		return new TypeInferenceResult( null, null, InferenceConfidence.UNKNOWN, "Could not infer type" );
	}

	/**
	 * Check if a type was successfully inferred.
	 */
	public boolean isResolved() {
		return className != null && confidence != InferenceConfidence.UNKNOWN;
	}
}
