package ortus.boxlang.lsp.workspace.completion;

import java.util.List;
import java.util.stream.Stream;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;

import ortus.boxlang.lsp.workspace.rules.IRule;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.components.Attribute;
import ortus.boxlang.runtime.components.ComponentDescriptor;
import ortus.boxlang.runtime.validation.Validator;

/**
 * Provides completion for BXM tag attributes.
 * 
 * Triggers when inside a BXM tag after the tag name:
 * - <bx:output |
 * - <bx:thread action="|
 * 
 * Provides:
 * - Attribute names with type information
 * - Required attributes prioritized
 * - Attribute documentation
 */
public class BxmTagAttributeCompletionRule implements IRule<CompletionFacts, List<CompletionItem>> {

	@Override
	public boolean when( CompletionFacts facts ) {
		return facts.getContext().getKind() == CompletionContextKind.BXM_TAG_ATTRIBUTE;
	}

	@Override
	public void then( CompletionFacts facts, List<CompletionItem> result ) {
		// Get the tag name from the receiver text (stored in context)
		String tagName = facts.getContext().getReceiverText();
		if ( tagName == null || tagName.isEmpty() ) {
			return;
		}

		// Get the component descriptor
		ComponentDescriptor descriptor = BoxRuntime.getInstance().getComponentService().getComponent( tagName );
		if ( descriptor == null ) {
			return;
		}

		// Get already-used attributes to avoid suggesting them again
		String	lineText		= facts.fileParseResult().readLine( facts.completionParams().getPosition().getLine() );
		var		usedAttributes	= extractUsedAttributes( lineText );

		// Add attribute completions
		Stream.of( descriptor.getComponent().getDeclaredAttributes() )
		    .filter( attr -> !usedAttributes.contains( attr.name().getName().toLowerCase() ) )
		    .forEach( attr -> result.add( createAttributeCompletion( attr ) ) );
	}

	/**
	 * Create a completion item for a component attribute.
	 */
	private CompletionItem createAttributeCompletion( Attribute attr ) {
		CompletionItem	item		= new CompletionItem();
		String			attrName	= attr.name().toString();
		boolean			isRequired	= attr.validators().contains( Validator.REQUIRED );
		Object			defaultValue = attr.defaultValue();

		item.setLabel( attrName );
		item.setKind( CompletionItemKind.Property );
		item.setInsertText( attrName + "=\"$1\"$0" );
		item.setInsertTextFormat( org.eclipse.lsp4j.InsertTextFormat.Snippet );

		// Build detail showing type and required status
		StringBuilder detail = new StringBuilder();
		if ( isRequired ) {
			detail.append( "(required) " );
		}
		if ( defaultValue != null && !defaultValue.toString().isEmpty() ) {
			detail.append( "default: " ).append( defaultValue );
		}

		if ( detail.length() > 0 ) {
			item.setDetail( detail.toString() );
		}

		// Sort required attributes first
		String sortPrefix = isRequired ? "0" : "1";
		item.setSortText( sortPrefix + attrName );

		return item;
	}

	/**
	 * Extract already-used attribute names from the line text.
	 * Simple regex-based extraction looking for attribute="value" patterns.
	 */
	private java.util.Set<String> extractUsedAttributes( String lineText ) {
		java.util.Set<String>	used	= new java.util.HashSet<>();
		java.util.regex.Pattern	pattern	= java.util.regex.Pattern.compile( "\\b([\\w\\-]+)\\s*=\\s*[\"']" );
		java.util.regex.Matcher	matcher	= pattern.matcher( lineText );

		while ( matcher.find() ) {
			used.add( matcher.group( 1 ).toLowerCase() );
		}

		return used;
	}
}
