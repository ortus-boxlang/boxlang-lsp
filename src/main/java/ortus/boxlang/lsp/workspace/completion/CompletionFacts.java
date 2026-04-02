package ortus.boxlang.lsp.workspace.completion;

import org.eclipse.lsp4j.CompletionParams;

import ortus.boxlang.lsp.workspace.FileParseResult;

/**
 * Facts about a completion request, used by completion rules to determine
 * what completions to provide.
 */
public record CompletionFacts( FileParseResult fileParseResult, CompletionParams completionParams ) {

	/**
	 * Get the analyzed completion context for this request.
	 * The context determines what kind of completion is appropriate
	 * (member access, import, new expression, etc.)
	 *
	 * @return The analyzed CompletionContext
	 */
	public CompletionContext getContext() {
		return CompletionContext.analyze( fileParseResult, completionParams );
	}
}
