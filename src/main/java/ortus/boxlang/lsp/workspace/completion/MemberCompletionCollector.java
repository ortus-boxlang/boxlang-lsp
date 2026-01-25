package ortus.boxlang.lsp.workspace.completion;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionItemLabelDetails;
import org.eclipse.lsp4j.InsertTextFormat;

import ortus.boxlang.lsp.workspace.index.IndexedClass;
import ortus.boxlang.lsp.workspace.index.IndexedMethod;
import ortus.boxlang.lsp.workspace.index.IndexedParameter;
import ortus.boxlang.lsp.workspace.index.IndexedProperty;
import ortus.boxlang.lsp.workspace.index.InheritanceGraph;
import ortus.boxlang.lsp.workspace.index.ProjectIndex;

/**
 * Collects method and property completions for a class, including inherited members.
 */
public class MemberCompletionCollector {

	private final ProjectIndex	index;
	private final String		currentClassName;	// The class where completion is triggered (for visibility)
	private final URI			contextFileUri;		// The file URI for relative path resolution

	public MemberCompletionCollector( ProjectIndex index, String currentClassName ) {
		this( index, currentClassName, null );
	}

	public MemberCompletionCollector( ProjectIndex index, String currentClassName, URI contextFileUri ) {
		this.index				= index;
		this.currentClassName	= currentClassName;
		this.contextFileUri		= contextFileUri;
	}

	/**
	 * Collect all completion items for members of the given class.
	 *
	 * @param className    The class name to get members for
	 * @param filterPrefix Optional prefix to filter member names
	 *
	 * @return List of CompletionItems for methods and properties
	 */
	public List<CompletionItem> collectMembers( String className, String filterPrefix ) {
		List<CompletionItem>	items		= new ArrayList<>();
		Set<String>				seenMembers	= new HashSet<>();	// Track to avoid duplicates from inheritance

		if ( index == null || className == null ) {
			return items;
		}

		// Find the class in the index
		Optional<IndexedClass> indexedClassOpt = resolveClass( className );
		if ( indexedClassOpt.isEmpty() ) {
			return items;
		}

		IndexedClass	targetClass		= indexedClassOpt.get();
		boolean			isCurrentClass	= className.equalsIgnoreCase( currentClassName ) ||
		    ( targetClass.fullyQualifiedName() != null && targetClass.fullyQualifiedName().equalsIgnoreCase( currentClassName ) );

		// Collect own members first (higher priority)
		collectClassMembers( targetClass, items, seenMembers, filterPrefix, isCurrentClass, 0 );

		// Collect inherited members
		InheritanceGraph	graph		= index.getInheritanceGraph();
		List<String>		ancestors	= graph.getAncestors( targetClass.fullyQualifiedName() );
		int					depth		= 1;
		for ( String ancestorFqn : ancestors ) {
			Optional<IndexedClass> ancestor = index.findClassByFQN( ancestorFqn );
			if ( ancestor.isPresent() ) {
				collectClassMembers( ancestor.get(), items, seenMembers, filterPrefix, false, depth );
				depth++;
			}
		}

		// Sort by relevance (depth, then alphabetically)
		items.sort( Comparator.comparing( CompletionItem::getSortText ) );

		return items;
	}

	/**
	 * Collect members (methods and properties) from a specific class.
	 */
	private void collectClassMembers(
	    IndexedClass clazz,
	    List<CompletionItem> items,
	    Set<String> seenMembers,
	    String filterPrefix,
	    boolean isSameClass,
	    int inheritanceDepth ) {

		// Collect methods
		List<IndexedMethod> methods = index.getMethodsOfClass( clazz.fullyQualifiedName() );
		for ( IndexedMethod method : methods ) {
			// Skip if already seen (overridden in subclass)
			String memberKey = method.name().toLowerCase();
			if ( seenMembers.contains( memberKey ) ) {
				continue;
			}

			// Skip private methods from other classes
			if ( !isSameClass && "private".equalsIgnoreCase( method.accessModifier() ) ) {
				continue;
			}

			// Apply prefix filter
			if ( filterPrefix != null && !filterPrefix.isEmpty()
			    && !method.name().toLowerCase().startsWith( filterPrefix.toLowerCase() ) ) {
				continue;
			}

			seenMembers.add( memberKey );
			items.add( createMethodCompletionItem( method, inheritanceDepth, clazz.name() ) );
		}

		// Collect properties
		List<IndexedProperty> properties = index.findPropertiesOfClass( clazz.fullyQualifiedName() );
		for ( IndexedProperty property : properties ) {
			String memberKey = "prop_" + property.name().toLowerCase();
			if ( seenMembers.contains( memberKey ) ) {
				continue;
			}

			// Apply prefix filter
			if ( filterPrefix != null && !filterPrefix.isEmpty()
			    && !property.name().toLowerCase().startsWith( filterPrefix.toLowerCase() ) ) {
				continue;
			}

			seenMembers.add( memberKey );
			items.add( createPropertyCompletionItem( property, inheritanceDepth, clazz.name() ) );

			// Also add getter/setter if they exist
			if ( property.hasGetter() ) {
				String getterKey = "get" + capitalize( property.name() );
				if ( !seenMembers.contains( getterKey.toLowerCase() ) ) {
					seenMembers.add( getterKey.toLowerCase() );
					items.add( createGetterCompletionItem( property, inheritanceDepth, clazz.name() ) );
				}
			}
			if ( property.hasSetter() ) {
				String setterKey = "set" + capitalize( property.name() );
				if ( !seenMembers.contains( setterKey.toLowerCase() ) ) {
					seenMembers.add( setterKey.toLowerCase() );
					items.add( createSetterCompletionItem( property, inheritanceDepth, clazz.name() ) );
				}
			}
		}
	}

	/**
	 * Create a completion item for a method.
	 */
	private CompletionItem createMethodCompletionItem( IndexedMethod method, int depth, String declaringClass ) {
		CompletionItem item = new CompletionItem();
		item.setLabel( method.name() );
		item.setKind( CompletionItemKind.Method );
		item.setInsertTextFormat( InsertTextFormat.Snippet );

		// Build parameter snippet
		StringBuilder			insertText	= new StringBuilder( method.name() ).append( "(" );
		List<IndexedParameter>	params		= method.parameters();
		if ( params != null && !params.isEmpty() ) {
			for ( int i = 0; i < params.size(); i++ ) {
				if ( i > 0 )
					insertText.append( ", " );
				IndexedParameter param = params.get( i );
				insertText.append( "${" ).append( i + 1 ).append( ":" ).append( param.name() ).append( "}" );
			}
		}
		insertText.append( ")" );
		item.setInsertText( insertText.toString() );

		// Build detail showing signature
		StringBuilder detail = new StringBuilder();
		if ( method.accessModifier() != null ) {
			detail.append( method.accessModifier() ).append( " " );
		}
		detail.append( "function " ).append( method.name() ).append( "(" );
		if ( params != null ) {
			for ( int i = 0; i < params.size(); i++ ) {
				if ( i > 0 )
					detail.append( ", " );
				IndexedParameter param = params.get( i );
				if ( param.required() )
					detail.append( "required " );
				if ( param.typeHint() != null )
					detail.append( param.typeHint() ).append( " " );
				detail.append( param.name() );
			}
		}
		detail.append( ")" );
		if ( method.returnTypeHint() != null && !method.returnTypeHint().isEmpty() ) {
			detail.append( " : " ).append( method.returnTypeHint() );
		}
		item.setDetail( detail.toString() );

		// Sort text: depth prefix + name for relevance sorting
		item.setSortText( String.format( "%02d_%s", depth, method.name().toLowerCase() ) );

		// Documentation
		if ( method.documentation() != null && !method.documentation().isEmpty() ) {
			item.setDocumentation( method.documentation() );
		}

		// Add "from ClassName" label if inherited
		if ( depth > 0 ) {
			CompletionItemLabelDetails labelDetails = new CompletionItemLabelDetails();
			labelDetails.setDescription( "from " + declaringClass );
			item.setLabelDetails( labelDetails );
		}

		return item;
	}

	/**
	 * Create a completion item for a property.
	 */
	private CompletionItem createPropertyCompletionItem( IndexedProperty property, int depth, String declaringClass ) {
		CompletionItem item = new CompletionItem();
		item.setLabel( property.name() );
		item.setKind( CompletionItemKind.Property );
		item.setInsertText( property.name() );

		StringBuilder detail = new StringBuilder( "property" );
		if ( property.typeHint() != null && !property.typeHint().isEmpty() ) {
			detail.append( " " ).append( property.typeHint() );
		}
		detail.append( " " ).append( property.name() );
		item.setDetail( detail.toString() );

		item.setSortText( String.format( "%02d_%s", depth, property.name().toLowerCase() ) );

		if ( depth > 0 ) {
			CompletionItemLabelDetails labelDetails = new CompletionItemLabelDetails();
			labelDetails.setDescription( "from " + declaringClass );
			item.setLabelDetails( labelDetails );
		}

		return item;
	}

	/**
	 * Create a completion item for a property getter.
	 */
	private CompletionItem createGetterCompletionItem( IndexedProperty property, int depth, String declaringClass ) {
		CompletionItem	item		= new CompletionItem();
		String			getterName	= "get" + capitalize( property.name() );
		item.setLabel( getterName );
		item.setKind( CompletionItemKind.Method );
		item.setInsertText( getterName + "()" );
		item.setInsertTextFormat( InsertTextFormat.PlainText );

		StringBuilder detail = new StringBuilder( "function " ).append( getterName ).append( "()" );
		if ( property.typeHint() != null && !property.typeHint().isEmpty() ) {
			detail.append( " : " ).append( property.typeHint() );
		}
		item.setDetail( detail.toString() );

		item.setSortText( String.format( "%02d_%s", depth, getterName.toLowerCase() ) );

		return item;
	}

	/**
	 * Create a completion item for a property setter.
	 */
	private CompletionItem createSetterCompletionItem( IndexedProperty property, int depth, String declaringClass ) {
		CompletionItem	item		= new CompletionItem();
		String			setterName	= "set" + capitalize( property.name() );
		item.setLabel( setterName );
		item.setKind( CompletionItemKind.Method );
		item.setInsertTextFormat( InsertTextFormat.Snippet );
		item.setInsertText( setterName + "(${1:value})" );

		StringBuilder detail = new StringBuilder( "function " ).append( setterName ).append( "(" );
		if ( property.typeHint() != null && !property.typeHint().isEmpty() ) {
			detail.append( property.typeHint() ).append( " " );
		}
		detail.append( property.name() ).append( ")" );
		item.setDetail( detail.toString() );

		item.setSortText( String.format( "%02d_%s", depth, setterName.toLowerCase() ) );

		return item;
	}

	/**
	 * Resolve a class by name (simple name, FQN, or relative path).
	 * Uses context-aware lookup to support relative class paths.
	 */
	private Optional<IndexedClass> resolveClass( String className ) {
		if ( index == null || className == null ) {
			return Optional.empty();
		}
		return index.findClassWithContext( className, contextFileUri );
	}

	/**
	 * Capitalize the first letter of a string.
	 */
	private String capitalize( String str ) {
		if ( str == null || str.isEmpty() )
			return str;
		return Character.toUpperCase( str.charAt( 0 ) ) + str.substring( 1 );
	}
}
