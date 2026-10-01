package ortus.boxlang.lsp.workspace.types;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;

public record ParsedProperty(
    String name,
    String type ) {

	public CompletionItem asCompletionItem() {
		CompletionItem item = new CompletionItem();
		item.setLabel( this.name );
		item.setKind( CompletionItemKind.Property );
		item.setInsertText( this.name );
		item.setDetail( this.type );

		return item;
	}
}
