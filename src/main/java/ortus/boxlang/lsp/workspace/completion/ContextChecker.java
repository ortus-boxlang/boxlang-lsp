package ortus.boxlang.lsp.workspace.completion;

/**
 * Helper class for checking completion context types.
 * Delegates to the CompletionContext framework for consistent behavior.
 */
public class ContextChecker {

	/**
	 * Check if the cursor is in a "new" expression context.
	 *
	 * @param facts The completion facts
	 *
	 * @return true if this is a new expression context
	 */
	public static boolean isNewExpression( CompletionFacts facts ) {
		return facts.getContext().getKind() == CompletionContextKind.NEW_EXPRESSION;
	}

	/**
	 * Check if the cursor is in an import statement context.
	 *
	 * @param facts The completion facts
	 *
	 * @return true if this is an import context
	 */
	public static boolean isImportExpression( CompletionFacts facts ) {
		return facts.getContext().getKind() == CompletionContextKind.IMPORT;
	}

	/**
	 * Check if the cursor is in a member access (dot) context.
	 *
	 * @param facts The completion facts
	 *
	 * @return true if this is a member access context
	 */
	public static boolean isMemberAccess( CompletionFacts facts ) {
		return facts.getContext().getKind() == CompletionContextKind.MEMBER_ACCESS;
	}

	/**
	 * Check if the cursor is in an extends context.
	 *
	 * @param facts The completion facts
	 *
	 * @return true if this is an extends context
	 */
	public static boolean isExtendsExpression( CompletionFacts facts ) {
		return facts.getContext().getKind() == CompletionContextKind.EXTENDS;
	}

	/**
	 * Check if the cursor is in an implements context.
	 *
	 * @param facts The completion facts
	 *
	 * @return true if this is an implements context
	 */
	public static boolean isImplementsExpression( CompletionFacts facts ) {
		return facts.getContext().getKind() == CompletionContextKind.IMPLEMENTS;
	}

	/**
	 * Check if the cursor is inside a function argument context.
	 *
	 * @param facts The completion facts
	 *
	 * @return true if this is a function argument context
	 */
	public static boolean isFunctionArgument( CompletionFacts facts ) {
		return facts.getContext().getKind() == CompletionContextKind.FUNCTION_ARGUMENT;
	}

	/**
	 * Check if the cursor is in a BXM tag context.
	 *
	 * @param facts The completion facts
	 *
	 * @return true if this is a BXM tag context
	 */
	public static boolean isBxmTag( CompletionFacts facts ) {
		return facts.getContext().getKind() == CompletionContextKind.BXM_TAG;
	}

	/**
	 * Check if the cursor is in a template expression context.
	 *
	 * @param facts The completion facts
	 *
	 * @return true if this is a template expression context
	 */
	public static boolean isTemplateExpression( CompletionFacts facts ) {
		return facts.getContext().getKind() == CompletionContextKind.TEMPLATE_EXPRESSION;
	}

	/**
	 * Check if the cursor is in a general context (no specific trigger).
	 *
	 * @param facts The completion facts
	 *
	 * @return true if this is a general context
	 */
	public static boolean isGeneralContext( CompletionFacts facts ) {
		return facts.getContext().getKind() == CompletionContextKind.GENERAL;
	}
}
