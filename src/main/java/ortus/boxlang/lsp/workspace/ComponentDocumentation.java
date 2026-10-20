package ortus.boxlang.lsp.workspace;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;

import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;

import ortus.boxlang.runtime.components.Attribute;
import ortus.boxlang.runtime.components.BoxComponent;
import ortus.boxlang.runtime.components.ComponentDescriptor;
import ortus.boxlang.runtime.validation.Validator;

/** Component metadata shared by hover and completion documentation. */
public class ComponentDocumentation {

	public static MarkupContent format( ComponentDescriptor descriptor ) {
		String			name		= descriptor.name.getName().toLowerCase( Locale.ROOT );
		String			signature	= descriptor.allowsBody || descriptor.requiresBody ? "<bx:" + name + ">…</bx:" + name + ">" : "<bx:" + name + " />";
		StringBuilder	doc			= new StringBuilder( "```boxlang\n" + signature + "\n```\n\n" );
		BoxComponent	annotation	= Arrays.stream( descriptor.componentClass.getAnnotationsByType( BoxComponent.class ) )
		    .filter( candidate -> candidate.name().isBlank() || candidate.name().equalsIgnoreCase( name ) || candidate.alias().equalsIgnoreCase( name ) )
		    .findFirst().orElse( null );
		if ( annotation != null && !annotation.description().isBlank() ) {
			doc.append( annotation.description() ).append( "\n\n" );
		}
		Attribute[] attributes = descriptor.getComponent().getDeclaredAttributes();
		if ( attributes.length > 0 ) {
			doc.append( "| Attribute | Type | Default |\n| --- | --- | --- |\n" );
			Arrays.stream( attributes )
			    .sorted( Comparator.comparing( ( Attribute attr ) -> !attr.validators().contains( Validator.REQUIRED ) )
			        .thenComparing( attr -> attr.name().getName() ) )
			    .forEach( attr -> {
				    doc.append( "| `" ).append( attr.name() ).append( '`' );
				    if ( attr.validators().contains( Validator.REQUIRED ) )
					    doc.append( " **required**" );
				    doc.append( " | `" ).append( attr.type() ).append( "` | " );
				    doc.append( formatDefault( attr.defaultValue() ) );
				    doc.append( " |\n" );
			    } );
		}
		doc.append( '\n' )
		    .append( descriptor.requiresBody ? "_Requires body content_" : descriptor.allowsBody ? "_Allows body content_" : "_Self-closing tag_" );
		String	packageName	= descriptor.componentClass.getPackageName();
		String	corePackage	= "ortus.boxlang.runtime.components.";
		if ( !descriptor.hasModule() && packageName.startsWith( corePackage ) ) {
			String canonicalName = annotation == null || annotation.name().isBlank() ? descriptor.componentClass.getSimpleName() : annotation.name();
			doc.append( " · [Documentation ↗](https://boxlang.ortusbooks.com/boxlang-language/reference/components/" )
			    .append( packageName.substring( corePackage.length() ).replace( '.', '/' ) ).append( '/' )
			    .append( canonicalName.toLowerCase( Locale.ROOT ) ).append( ")\n" );
		}
		return new MarkupContent( MarkupKind.MARKDOWN, doc.toString() );
	}

	private static String formatDefault( Object value ) {
		if ( value == null )
			return "—";
		String	text		= ( value instanceof String ? "\"" + value + "\"" : value.toString() )
		    .replace( "|", "\\|" ).replace( "\r", " " ).replace( "\n", " " );
		String	delimiter	= "`";
		while ( text.contains( delimiter ) )
			delimiter += "`";
		String padding = text.startsWith( "`" ) || text.endsWith( "`" ) ? " " : "";
		return delimiter + padding + text + padding + delimiter;
	}
}
