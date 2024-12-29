package ortus.boxlang.lsp.workspace.types;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;

import ortus.boxlang.compiler.ast.statement.BoxProperty;

public record ParsedProperty(
    String name,
    String type,
    BoxProperty node ) {

    public CompletionItem asCompletionItem() {
        CompletionItem item = new CompletionItem();
        item.setLabel( this.name );
        item.setKind( CompletionItemKind.Property );
        item.setInsertText( this.name );
        item.setDetail( this.type );

        return item;
    }
}
