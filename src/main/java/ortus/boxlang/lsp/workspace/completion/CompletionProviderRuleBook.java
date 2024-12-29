package ortus.boxlang.lsp.workspace.completion;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.CompletionItem;

import ortus.boxlang.lsp.workspace.rules.RuleCollection;

public class CompletionProviderRuleBook extends RuleCollection<CompletionFacts, List<CompletionItem>> {

	private static CompletionProviderRuleBook instance = new CompletionProviderRuleBook();

	static {
		instance.addRule( new ComponentCompletionRule() )
		    .addRule( new BIFCompletionRule() )
		    .addRule( new PropertyCompletionRule() );
	}

	public static List<CompletionItem> execute( CompletionFacts facts ) {
		return instance.execute( facts, new ArrayList<CompletionItem>() );
	}
}
