package ortus.boxlang.lsp.workspace.completion;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import ortus.boxlang.lsp.workspace.rules.IRule;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.components.Attribute;
import ortus.boxlang.runtime.components.ComponentDescriptor;
import ortus.boxlang.runtime.validation.Validator;

public class ComponentCompletionRule implements IRule<CompletionFacts, List<CompletionItem>> {

	@Override
	public boolean when( CompletionFacts facts ) {
		return facts.getContext().getKind() == CompletionContextKind.BXM_TAG;
	}

	@Override
	public void then( CompletionFacts facts, List<CompletionItem> result ) {
		var	cursor	= facts.completionParams().getPosition();
		var	tag		= TemplateTagContext.analyze( facts.fileParseResult(), cursor );
		var	options	= Stream.of( BoxRuntime.getInstance().getComponentService().getComponentNames() ).map( ( name ) -> {
						ComponentDescriptor	componentDescriptor	= BoxRuntime.getInstance().getComponentService()
						    .getComponent( name );

						CompletionItem		item				= new CompletionItem();
						item.setLabel( "bx:" + name.toLowerCase() );
						item.setFilterText( "<bx:" + name.toLowerCase() );
						item.setKind( CompletionItemKind.Snippet );
						item.setInsertTextFormat( InsertTextFormat.Snippet );
						String snippet = formatComponentInsert( componentDescriptor );
						item.setInsertText( snippet );
						item.setTextEdit( Either.forLeft( new TextEdit( new Range( tag.start(), cursor ), snippet ) ) );
						item.setDetail( formatComponentSignature( componentDescriptor ) );
						item.setDocumentation( formatComponentDocumentation( componentDescriptor ) );
						item.setSortText( "a" );

						return item;
					} )
		    .toList();

		result.addAll( options );

		// Script is parser syntax, not a runtime component descriptor.
		CompletionItem script = new CompletionItem( "bx:script" );
		script.setFilterText( "<bx:script" );
		script.setKind( CompletionItemKind.Snippet );
		script.setInsertTextFormat( InsertTextFormat.Snippet );
		script.setInsertText( "<bx:script>$0</bx:script>" );
		script.setTextEdit( Either.forLeft( new TextEdit( new Range( tag.start(), cursor ), script.getInsertText() ) ) );
		script.setDetail( "<bx:script>{body}</bx:script>" );
		script.setDocumentation( "Contains BoxLang script code within a template." );
		script.setSortText( "a" );
		result.add( script );
	}

	private String formatComponentInsert( ComponentDescriptor descriptor ) {
		String			name			= descriptor.name.toString();
		List<Attribute>	reqAttributes	= List.of( descriptor.getComponent().getDeclaredAttributes() )
		    .stream()
		    .filter( attr -> attr.validators().contains( Validator.REQUIRED ) )
		    .toList();
		String			args			= IntStream.range( 0, reqAttributes.size() )
		    .filter( i -> reqAttributes.get( i ).validators().contains( Validator.REQUIRED ) )
		    .mapToObj( i -> {
											    Attribute attr			= reqAttributes.get( i );
											    Object	defaultValue	= attr.defaultValue();

											    if ( defaultValue == null ) {
												    defaultValue = "";
											    }

											    return "%s=\"$%d%s\"".formatted( attr.name(), i + 1, defaultValue );

										    } )
		    .collect( Collectors.joining( " " ) );

		String			prompt;

		if ( descriptor.allowsBody || descriptor.requiresBody ) {
			prompt = "%s %s>$0</bx:%s>".formatted( name.toLowerCase(), args, name.toLowerCase() );
		} else {
			prompt = "%s %s />$0".formatted( name.toLowerCase(), args );
		}

		return "<bx:" + prompt;
	}

	private String formatComponentSignature( ComponentDescriptor descriptor ) {
		String	name	= descriptor.name.toString();
		String	args	= Stream.of( descriptor.getComponent().getDeclaredAttributes() )
		    .sorted( ( a, b ) -> {
							    if ( a.validators().contains( Validator.REQUIRED ) && !b.validators().contains( Validator.REQUIRED ) ) {
								    return -1;
							    } else if ( !a.validators().contains( Validator.REQUIRED )
							        && b.validators().contains( Validator.REQUIRED ) ) {
								    return 1;
							    }

							    return a.name().compareTo( b.name() );
						    } )
		    .map( ( attr ) -> {
			    Object defaultValue = attr.defaultValue();

			    if ( defaultValue == null ) {
				    defaultValue = "";
			    }

			    if ( !attr.validators().contains( Validator.REQUIRED ) ) {
				    return "[%s=\"%s\"]".formatted( attr.name(), defaultValue );
			    }

			    return "%s=\"%s\"".formatted( attr.name(), defaultValue );
		    } ).collect( Collectors.joining( " " ) );

		if ( descriptor.allowsBody || descriptor.requiresBody ) {
			return "<bx:%s %s>{body}</bx:%s>".formatted( name.toLowerCase(), args, name.toLowerCase() );
		}

		return "<bx:%s %s />".formatted( name.toLowerCase(), args );
	}

	/**
	 * Format comprehensive documentation for a component tag.
	 * Includes tag description, attributes with types and descriptions, and usage examples.
	 */
	private MarkupContent formatComponentDocumentation( ComponentDescriptor descriptor ) {
		StringBuilder	doc		= new StringBuilder();
		String			name	= descriptor.name.toString();

		// Tag signature
		doc.append( "**" ).append( name ).append( "** tag\n\n" );

		// Body requirement
		if ( descriptor.requiresBody ) {
			doc.append( "_Requires body content_\n\n" );
		} else if ( descriptor.allowsBody ) {
			doc.append( "_Allows body content_\n\n" );
		} else {
			doc.append( "_Self-closing tag_\n\n" );
		}

		// Attributes table
		Attribute[] attributes = descriptor.getComponent().getDeclaredAttributes();
		if ( attributes.length > 0 ) {
			doc.append( "**Attributes:**\n\n" );

			// Sort: required first, then alphabetically
			Stream.of( attributes )
			    .sorted( ( a, b ) -> {
				    if ( a.validators().contains( Validator.REQUIRED ) && !b.validators().contains( Validator.REQUIRED ) ) {
					    return -1;
				    } else if ( !a.validators().contains( Validator.REQUIRED ) && b.validators().contains( Validator.REQUIRED ) ) {
					    return 1;
				    }
				    return a.name().compareTo( b.name() );
			    } )
			    .forEach( attr -> {
				    String attrName		= attr.name().toString();
				    boolean isRequired	= attr.validators().contains( Validator.REQUIRED );
				    Object defaultValue	= attr.defaultValue();

				    doc.append( "- **" ).append( attrName ).append( "**" );

				    if ( isRequired ) {
					    doc.append( " _(required)_" );
				    } else if ( defaultValue != null && !defaultValue.toString().isEmpty() ) {
					    doc.append( " — default: `" ).append( defaultValue ).append( "`" );
				    }

				    doc.append( "\n" );
			    } );
		}

		// Create MarkupContent
		MarkupContent markup = new MarkupContent();
		markup.setKind( MarkupKind.MARKDOWN );
		markup.setValue( doc.toString() );

		return markup;
	}

}
