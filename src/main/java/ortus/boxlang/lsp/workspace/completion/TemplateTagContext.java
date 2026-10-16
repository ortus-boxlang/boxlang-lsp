package ortus.boxlang.lsp.workspace.completion;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.lsp4j.Position;

import ortus.boxlang.lsp.workspace.FileParseResult;

/** Text-based opening-tag detection, including unfinished and multiline tags. */
record TemplateTagContext( CompletionContextKind kind, String partial, String name, Position start, String text ) {

	private static final Pattern TAG = Pattern.compile( "<(?:b(?:x(?::(\\w*))?)?)?$", Pattern.CASE_INSENSITIVE );
	private static final Pattern ATTRIBUTE = Pattern.compile( "<bx:(\\w+)\\s+(?:[\\w-]+\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]+)\\s+)*([\\w-]*)$",
	    Pattern.CASE_INSENSITIVE );

	private static final Pattern BOX_COMMENT_DELIMITER = Pattern.compile( "<!---|--->" );

	private static int commentEnd( String source, int start ) {
		if ( !source.startsWith( "<!---", start ) ) {
			int end = source.indexOf( "-->", start + 4 );
			return end < 0 ? -1 : end + 3;
		}
		int		depth		= 1;
		Matcher	delimiter	= BOX_COMMENT_DELIMITER.matcher( source );
		delimiter.region( start + 5, source.length() );
		while ( delimiter.find() ) {
			depth += delimiter.group().equals( "<!---" ) ? 1 : -1;
			if ( depth == 0 )
				return delimiter.end();
		}
		return -1;
	}

	static TemplateTagContext analyze( FileParseResult file, Position cursor ) {
		if ( !file.isTemplate() ) {
			return null;
		}
		var				lines	= file.readAllLines();
		StringBuilder	before	= new StringBuilder();
		for ( int i = 0; i <= cursor.getLine() && i < lines.size(); i++ ) {
			if ( i > 0 )
				before.append( '\n' );
			String line = lines.get( i );
			before.append( i == cursor.getLine() ? line.substring( 0, Math.min( cursor.getCharacter(), line.length() ) ) : line );
		}
		String	source		= before.toString();
		// ponytail: scan the document on each request; cache lexical states if large-template latency becomes measurable.
		int		opening		= -1;
		char	quote		= 0;
		boolean	expression	= false;
		String	script		= null;
		for ( int i = 0; i < source.length(); i++ ) {
			char c = source.charAt( i );
			if ( script != null ) {
				Matcher close = Pattern.compile( "</" + script + "\\s*>", Pattern.CASE_INSENSITIVE ).matcher( source );
				if ( !close.find( i ) )
					return null;
				i		= close.end() - 1;
				script	= null;
				continue;
			}
			if ( opening < 0 ) {
				if ( expression ) {
					if ( quote != 0 ) {
						if ( c == quote ) {
							if ( i + 1 < source.length() && source.charAt( i + 1 ) == quote )
								i++;
							else
								quote = 0;
						}
					} else if ( c == '"' || c == '\'' )
						quote = c;
					else if ( c == '#' )
						expression = false;
				} else if ( source.startsWith( "<!--", i ) ) {
					int end = commentEnd( source, i );
					if ( end < 0 )
						return new TemplateTagContext( CompletionContextKind.NONE, "", null, null, "" );
					i = end - 1;
				} else if ( c == '#' ) {
					if ( i + 1 < source.length() && source.charAt( i + 1 ) == '#' )
						i++;
					else
						expression = true;
				} else if ( c == '<' )
					opening = i;
			} else if ( quote != 0 ) {
				if ( c == quote )
					quote = 0;
			} else if ( c == '"' || c == '\'' ) {
				quote = c;
			} else if ( c == '>' ) {
				String	tagText		= source.substring( opening, i + 1 );
				Matcher	scriptTag	= Pattern.compile( "<(bx:script|script)(?:\\s.*)?>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL ).matcher( tagText );
				if ( scriptTag.matches() && !tagText.endsWith( "/>" ) )
					script = scriptTag.group( 1 );
				opening = -1;
			} else if ( c == '<' ) {
				opening = i;
			}
		}
		if ( opening < 0 )
			return null;
		String		text	= source.substring( opening );
		int			line	= ( int ) source.substring( 0, opening ).chars().filter( c -> c == '\n' ).count();
		Position	start	= new Position( line, opening - source.lastIndexOf( '\n', opening ) - 1 );
		Matcher		tag		= TAG.matcher( text );
		if ( tag.matches() ) {
			return new TemplateTagContext( CompletionContextKind.BXM_TAG, tag.group( 1 ) == null ? "" : tag.group( 1 ), null, start, text );
		}
		Matcher attribute = ATTRIBUTE.matcher( text );
		if ( quote == 0 && attribute.matches() ) {
			return new TemplateTagContext( CompletionContextKind.BXM_TAG_ATTRIBUTE, attribute.group( 2 ), attribute.group( 1 ), start, text );
		}
		return new TemplateTagContext( CompletionContextKind.NONE, "", null, start, text );
	}
}
