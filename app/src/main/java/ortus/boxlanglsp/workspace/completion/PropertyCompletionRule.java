package ortus.boxlanglsp.workspace.completion;

import java.util.List;

import org.eclipse.lsp4j.CompletionItem;

import ortus.boxlanglsp.workspace.rules.IRule;
import ortus.boxlanglsp.workspace.types.ParsedProperty;

public class PropertyCompletionRule implements IRule<CompletionFacts, List<CompletionItem>> {

    @Override
    public boolean when(CompletionFacts facts) {
        return facts.fileParseResult().isClass() && facts.fileParseResult().properties() != null;
    }

    @Override
    public void then(CompletionFacts facts, List<CompletionItem> result) {
        result.addAll(facts.fileParseResult().properties().stream().map(ParsedProperty::asCompletionItem).toList());
    }

}
