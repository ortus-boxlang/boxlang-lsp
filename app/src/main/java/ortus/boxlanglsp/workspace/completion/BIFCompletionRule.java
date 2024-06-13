package ortus.boxlanglsp.workspace.completion;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;

import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.bifs.BIFDescriptor;
import ortus.boxlanglsp.workspace.rules.IRule;

public class BIFCompletionRule implements IRule<CompletionFacts, List<CompletionItem>> {

    @Override
    public boolean when(CompletionFacts facts) {
        return !facts.fileParseResult().isTemplate();
    }

    @Override
    public void then(CompletionFacts facts, List<CompletionItem> result) {

        var options = Stream.of(BoxRuntime.getInstance().getFunctionService().getGlobalFunctionNames()).map((name) -> {
            BIFDescriptor func = BoxRuntime.getInstance().getFunctionService().getGlobalFunction(name);
            String args = Stream.of(func.getBIF().getDeclaredArguments()).map((arg) -> {
                if (!arg.required()) {
                    return "[" + arg.signatureAsString() + "]";
                }

                return arg.signatureAsString();
            }).collect(Collectors.joining(", "));

            String signature = "%s(%s)".formatted(name, args);

            CompletionItem item = new CompletionItem();
            item.setLabel(name);
            item.setKind(CompletionItemKind.Function);
            item.setInsertText(name);
            item.setDetail(signature);

            return item;
        }).toList();

        result.addAll(options);
    }

}
