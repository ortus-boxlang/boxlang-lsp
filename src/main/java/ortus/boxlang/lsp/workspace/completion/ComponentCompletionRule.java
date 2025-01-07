package ortus.boxlang.lsp.workspace.completion;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.InsertTextFormat;

import ortus.boxlang.lsp.workspace.ProjectContextProvider;
import ortus.boxlang.lsp.workspace.rules.IRule;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.components.Attribute;
import ortus.boxlang.runtime.components.ComponentDescriptor;
import ortus.boxlang.runtime.validation.Validator;

public class ComponentCompletionRule implements IRule<CompletionFacts, List<CompletionItem>> {

	@Override
	public boolean when( CompletionFacts facts ) {
		return facts.fileParseResult().isTemplate();
	}

	@Override
	public void then( CompletionFacts facts, List<CompletionItem> result ) {
		var	existingPrompt	= getExistingPrompt(
		    ProjectContextProvider.readLine( facts.completionParams().getTextDocument().getUri(),
		        facts.completionParams().getPosition().getLine() ),
		    facts.completionParams().getPosition().getCharacter() );
		var	options			= Stream.of( BoxRuntime.getInstance().getComponentService().getComponentNames() ).map( ( name ) -> {
								ComponentDescriptor	componentDescriptor	= BoxRuntime.getInstance().getComponentService()
								    .getComponent( name );

								CompletionItem		item				= new CompletionItem();
								item.setLabel( "bx:" + name.toLowerCase() );
								item.setKind( CompletionItemKind.Snippet );
								item.setInsertTextFormat( InsertTextFormat.Snippet );
								item.setInsertText( formatComponentInsert( existingPrompt, facts, componentDescriptor ) );
								item.setDetail( formatComponentSignature( componentDescriptor ) );
								item.setSortText( "a" );

								return item;
							} )
		    .toList();

		result.addAll( options );
	}

	private String formatComponentInsert( String existingPrompt, CompletionFacts facts, ComponentDescriptor descriptor ) {
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

		String			prompt			= "<bx:%s %s>$0</bx:%s>";

		boolean			startsWithAngle	= existingPrompt.startsWith( "<" );
		boolean			startsWithColon	= existingPrompt.startsWith( ":" );

		int				tabIndex		= reqAttributes.size() > 0 ? reqAttributes.size() : 0;

		if ( descriptor.allowsBody || descriptor.requiresBody ) {
			prompt = "%s %s>$%d</bx:%s>".formatted( name.toLowerCase(), args, tabIndex, name.toLowerCase() );
		} else {
			prompt = "%s %s />$%d".formatted( name.toLowerCase(), args, tabIndex );
		}

		if ( !startsWithAngle && !startsWithColon ) {
			prompt = "<bx:" + prompt;
		} else if ( startsWithAngle ) {
			prompt = "bx:" + prompt;
		}

		return prompt;
	}

	private String getExistingPrompt( String line, int charIndex ) {
		try {
			StringBuilder sb = new StringBuilder();

			for ( var i = charIndex; i > 0; i-- ) {
				char c = line.charAt( i - 1 );

				if ( c == '<' || c == ':' ) {
					sb.insert( 0, c );
					break;
				}

				if ( !Character.isAlphabetic( c ) ) {
					break;
				}

				sb.insert( 0, c );
			}

			return sb.toString();
		} catch ( Exception e ) {
			return "";
		}
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

}
