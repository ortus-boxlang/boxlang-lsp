package ortus.boxlang.lsp.workspace.completion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;

import ortus.boxlang.lsp.workspace.rules.IRule;

/**
 * Provides keyword completions based on the current context.
 * Keywords are only suggested in appropriate contexts (top-level, class body, function body, etc.)
 */
public class KeywordCompletionRule implements IRule<CompletionFacts, List<CompletionItem>> {

	// Top-level keywords (outside class/interface)
	private static final List<String>	TOP_LEVEL_KEYWORDS		= Arrays.asList(
	    "class",
	    "interface",
	    "abstract",
	    "final",
	    "import",
	    "extends",
	    "implements"
	);

	// Class body keywords (inside class but outside methods)
	private static final List<String>	CLASS_BODY_KEYWORDS		= Arrays.asList(
	    "function",
	    "property",
	    "static",
	    "private",
	    "public",
	    "remote",
	    "required"
	);

	// Function body keywords (inside methods/functions)
	private static final List<String>	FUNCTION_BODY_KEYWORDS	= Arrays.asList(
	    "var",
	    "if",
	    "else",
	    "for",
	    "while",
	    "do",
	    "switch",
	    "try",
	    "catch",
	    "finally",
	    "throw",
	    "return",
	    "break",
	    "continue",
	    "required"
	);

	// Expression keywords (can be used in expressions)
	private static final List<String>	EXPRESSION_KEYWORDS		= Arrays.asList(
	    "new",
	    "true",
	    "false",
	    "null"
	);

	@Override
	public boolean when( CompletionFacts facts ) {
		// Don't provide keywords in specific contexts where they don't make sense
		CompletionContextKind kind = facts.getContext().getKind();

		// Skip in contexts where keywords aren't appropriate
		if ( kind == CompletionContextKind.NONE
		    || kind == CompletionContextKind.MEMBER_ACCESS
		    || kind == CompletionContextKind.IMPORT
		    || kind == CompletionContextKind.NEW_EXPRESSION
		    || kind == CompletionContextKind.EXTENDS
		    || kind == CompletionContextKind.IMPLEMENTS
		    || kind == CompletionContextKind.BXM_TAG
		    || kind == CompletionContextKind.TEMPLATE_EXPRESSION ) {
			return false;
		}

		// Only provide keywords in GENERAL context
		return kind == CompletionContextKind.GENERAL;
	}

	@Override
	public void then( CompletionFacts facts, List<CompletionItem> result ) {
		CompletionContext	context				= facts.getContext();
		String				containingClassName	= context.getContainingClassName();
		String				containingMethodName = context.getContainingMethodName();

		List<String>		keywords			= new ArrayList<>();

		// Determine which keywords to include based on context
		if ( containingMethodName != null ) {
			// Inside a function/method - include function body and expression keywords
			keywords.addAll( FUNCTION_BODY_KEYWORDS );
			keywords.addAll( EXPRESSION_KEYWORDS );
		} else if ( containingClassName != null ) {
			// Inside a class but not in a method - include class body keywords
			keywords.addAll( CLASS_BODY_KEYWORDS );
		} else {
			// Top level - include top-level keywords
			keywords.addAll( TOP_LEVEL_KEYWORDS );
		}

		// Create completion items for each keyword
		for ( String keyword : keywords ) {
			CompletionItem item = new CompletionItem();
			item.setLabel( keyword );
			item.setKind( CompletionItemKind.Keyword );
			item.setInsertText( keyword );
			item.setDetail( "keyword" );
			// Use "1" prefix for sort text to prioritize keywords
			item.setSortText( "1" + keyword );

			result.add( item );
		}
	}
}
