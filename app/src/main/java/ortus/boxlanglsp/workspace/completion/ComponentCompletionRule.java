package ortus.boxlanglsp.workspace.completion;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;

import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.components.ComponentDescriptor;
import ortus.boxlang.runtime.validation.Validator;
import ortus.boxlanglsp.workspace.rules.IRule;

public class ComponentCompletionRule implements IRule<CompletionFacts, List<CompletionItem>> {

    @Override
    public boolean when(CompletionFacts facts) {
        return facts.fileParseResult().isTemplate();
    }

    @Override
    public void then(CompletionFacts facts, List<CompletionItem> result) {

        var options = Stream.of(BoxRuntime.getInstance().getComponentService().getComponentNames()).map((name) -> {
            ComponentDescriptor componentDescriptor = BoxRuntime.getInstance().getComponentService()
                    .getComponent(name);

            CompletionItem item = new CompletionItem();
            item.setLabel("bx:" + name.toLowerCase());
            item.setKind(CompletionItemKind.Function);
            item.setInsertText(formatComponentInsert(facts, componentDescriptor));
            item.setDetail(formatComponentSignature(componentDescriptor));

            return item;
        }).toList();

        result.addAll(options);
    }

    private String formatComponentInsert(CompletionFacts facts, ComponentDescriptor descriptor) {
        String name = descriptor.name.toString();
        String args = Stream.of(descriptor.getComponent().getDeclaredAttributes())
                .filter((attr) -> attr.validators().contains(Validator.REQUIRED))
                .map((attr) -> {
                    Object defaultValue = attr.defaultValue();

                    if (defaultValue == null) {
                        defaultValue = "";
                    }

                    return "%s=\"%s\"".formatted(attr.name(), defaultValue);
                }).collect(Collectors.joining(" "));

        if (descriptor.allowsBody || descriptor.requiresBody) {
            return "<bx:%s %s></bx:%s>".formatted(name.toLowerCase(), args, name.toLowerCase());
        }

        return "<bx:%s %s />".formatted(name.toLowerCase(), args);
    }

    private String formatComponentSignature(ComponentDescriptor descriptor) {
        String name = descriptor.name.toString();
        String args = Stream.of(descriptor.getComponent().getDeclaredAttributes())
                .sorted((a, b) -> {
                    if (a.validators().contains(Validator.REQUIRED) && !b.validators().contains(Validator.REQUIRED)) {
                        return -1;
                    } else if (!a.validators().contains(Validator.REQUIRED)
                            && b.validators().contains(Validator.REQUIRED)) {
                        return 1;
                    }

                    return a.name().compareTo(b.name());
                })
                .map((attr) -> {
                    Object defaultValue = attr.defaultValue();

                    if (defaultValue == null) {
                        defaultValue = "";
                    }

                    if (!attr.validators().contains(Validator.REQUIRED)) {
                        return "[%s=\"%s\"]".formatted(attr.name(), defaultValue);
                    }

                    return "%s=\"%s\"".formatted(attr.name(), defaultValue);
                }).collect(Collectors.joining(" "));

        if (descriptor.allowsBody || descriptor.requiresBody) {
            return "<bx:%s %s>{body}</bx:%s>".formatted(name.toLowerCase(), args, name.toLowerCase());
        }

        return "<bx:%s %s />".formatted(name.toLowerCase(), args);
    }

}
