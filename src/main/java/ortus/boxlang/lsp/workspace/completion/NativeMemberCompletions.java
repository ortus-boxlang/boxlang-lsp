package ortus.boxlang.lsp.workspace.completion;

import java.util.*;
import org.eclipse.lsp4j.*;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.bifs.BoxMember;
import ortus.boxlang.runtime.scopes.Key;

/** Registered native members for a known receiver type. No application code is executed. */
final class NativeMemberCompletions {

	private NativeMemberCompletions() {
	}

	static List<CompletionItem> collect( String receiverType, String prefix ) {
		var							service	= BoxRuntime.getInstance().getFunctionService();
		Map<String, CompletionItem>	items	= new TreeMap<>();
		for ( String globalName : service.getGlobalFunctionNames() ) {
			var descriptor = service.getGlobalFunction( globalName );
			if ( descriptor.BIFClass == null )
				continue;
			for ( var member : descriptor.BIFClass.getAnnotationsByType( BoxMember.class ) ) {
				String baseType = member.type().name().split( "_" )[ 0 ].toLowerCase( Locale.ROOT );
				if ( !baseType.equalsIgnoreCase( receiverType ) )
					continue;
				String name = member.name().isEmpty() ? descriptor.BIFClass.getSimpleName().toLowerCase( Locale.ROOT ).replace( baseType, "" ) : member.name();
				if ( prefix != null && !name.toLowerCase( Locale.ROOT ).startsWith( prefix.toLowerCase( Locale.ROOT ) ) )
					continue;
				if ( service.getMemberMethod( Key.of( name ), member.type() ) == null )
					continue;
				var		args		= descriptor.getBIF().getDeclaredArguments();
				String	receiverArg	= member.objectArgument().isEmpty() && args.length > 0 ? args[ 0 ].name().getName() : member.objectArgument();
				String	signature	= Arrays.stream( args ).filter( arg -> !arg.name().getName().equalsIgnoreCase( receiverArg ) )
				    .map( arg -> arg.signatureAsString() ).collect( java.util.stream.Collectors.joining( ", " ) );
				var		item		= new CompletionItem( name );
				item.setKind( CompletionItemKind.Method );
				item.setInsertText( name );
				item.setDetail( name + "(" + signature + ")" );
				item.setSortText( "4" + name );
				item.setData( Map.of( "boxlangMember", Map.of( "globalName", globalName, "name", name, "receiverType", baseType ) ) );
				items.putIfAbsent( globalName.toLowerCase( Locale.ROOT ) + ":" + name.toLowerCase( Locale.ROOT ), item );
			}
		}
		return new ArrayList<>( items.values() );
	}
}
