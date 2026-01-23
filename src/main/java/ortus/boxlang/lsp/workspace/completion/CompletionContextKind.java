package ortus.boxlang.lsp.workspace.completion;

/**
 * Enumeration of completion context types.
 * Each kind represents a different trigger scenario that requires different completions.
 */
public enum CompletionContextKind {

	/**
	 * Member access after dot: `obj.` or `obj.partial`
	 * Complete with: methods, properties of the object's type
	 */
	MEMBER_ACCESS,

	/**
	 * Static access: `ClassName.`
	 * Complete with: static methods, constants
	 */
	STATIC_ACCESS,

	/**
	 * After `new` keyword: `new ` or `new Partial`
	 * Complete with: class names
	 */
	NEW_EXPRESSION,

	/**
	 * After `extends` keyword in class declaration
	 * Complete with: class names
	 */
	EXTENDS,

	/**
	 * After `implements` keyword in class declaration
	 * Complete with: interface names
	 */
	IMPLEMENTS,

	/**
	 * After `import` keyword
	 * Complete with: package paths, class names
	 */
	IMPORT,

	/**
	 * Inside function call parentheses: `func(` or `func(arg1, `
	 * Complete with: parameter hints, local variables, expressions
	 */
	FUNCTION_ARGUMENT,

	/**
	 * BXM tag context: `<bx:` or `<bx:partial`
	 * Complete with: tag names
	 */
	BXM_TAG,

	/**
	 * BXM tag attribute context: `<bx:tagname ` or `<bx:tagname attr`
	 * Complete with: attribute names for the tag
	 */
	BXM_TAG_ATTRIBUTE,

	/**
	 * Template expression: `#` or `#partial` inside BXM
	 * Complete with: variables, functions
	 */
	TEMPLATE_EXPRESSION,

	/**
	 * General identifier context - no specific trigger
	 * Complete with: keywords, variables, functions, classes
	 */
	GENERAL,

	/**
	 * No completion should be offered (e.g., inside string literal or comment)
	 */
	NONE
}
