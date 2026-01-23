package ortus.boxlang.lsp.workspace.completion;

import java.util.List;

import org.eclipse.lsp4j.CompletionItem;

import ortus.boxlang.lsp.workspace.FileParseResult;
import ortus.boxlang.lsp.workspace.ProjectContextProvider;
import ortus.boxlang.lsp.workspace.index.ProjectIndex;
import ortus.boxlang.lsp.workspace.rules.IRule;

/**
 * Completion rule for member access (dot completion).
 * Provides method and property completions after a dot.
 */
public class MemberAccessCompletionRule implements IRule<CompletionFacts, List<CompletionItem>> {

	@Override
	public boolean when( CompletionFacts facts ) {
		return ContextChecker.isMemberAccess( facts );
	}

	@Override
	public void then( CompletionFacts facts, List<CompletionItem> result ) {
		CompletionContext	context			= facts.getContext();
		FileParseResult		fileParseResult	= facts.fileParseResult();
		ProjectIndex		index			= ProjectContextProvider.getInstance().getIndex();

		// Get the receiver text (expression before the dot)
		String receiverText = context.getReceiverText();
		if ( receiverText == null || receiverText.isEmpty() ) {
			return;
		}

		// Get the partial text typed after the dot (for filtering)
		String filterPrefix = context.getTriggerText();

		// Get the containing class name (for visibility filtering)
		String containingClassName = context.getContainingClassName();

		// Create type inferrer and infer the type
		MemberAccessTypeInferrer	inferrer		= new MemberAccessTypeInferrer( fileParseResult, index );
		TypeInferenceResult			inferredType	= inferrer.inferType(
		    receiverText,
		    context.getCursorPosition().getLine(),
		    context.getCursorPosition().getCharacter()
		);

		if ( !inferredType.isResolved() ) {
			// Could not determine type - return empty
			return;
		}

		// Collect members for the inferred type
		MemberCompletionCollector	collector	= new MemberCompletionCollector( index, containingClassName );
		List<CompletionItem>		members		= collector.collectMembers( inferredType.className(), filterPrefix );

		result.addAll( members );
	}

}
