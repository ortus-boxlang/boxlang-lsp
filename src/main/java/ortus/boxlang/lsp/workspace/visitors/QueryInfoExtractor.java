package ortus.boxlang.lsp.workspace.visitors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ortus.boxlang.compiler.ast.expression.BoxStringLiteral;
import ortus.boxlang.compiler.ast.statement.BoxBufferOutput;
import ortus.boxlang.compiler.ast.statement.component.BoxComponent;

class QueryInfoExtractor {

	private static final Map<Pattern, BiConsumer<Matcher, QueryInfoExtractor>>	patterns			= new LinkedHashMap<>();
	private static final Map<Pattern, BiConsumer<Matcher, QueryInfoExtractor>>	columnPatterns		= new LinkedHashMap<>();

	private final List<String>													possibleRootTables	= new ArrayList<>();
	private final Map<String, String>											aliases				= new HashMap<>();

	static {
		patterns.put( Pattern.compile( "FROM\\s+(\\w+.)(\\w+)\\s+as\\+(\\w+)", Pattern.CASE_INSENSITIVE ), ( matcher, info ) -> {
			info.possibleRootTables.add( matcher.group( 2 ) );
			info.aliases.put( matcher.group( 3 ), matcher.group( 2 ) );
		} );
		patterns.put( Pattern.compile( "FROM\\s+(\\w+)\\.(\\w+) +(\\w+)", Pattern.CASE_INSENSITIVE ), ( matcher, info ) -> {
			info.possibleRootTables.add( matcher.group( 2 ) );
			info.aliases.put( matcher.group( 3 ), matcher.group( 2 ) );
		} );
		patterns.put( Pattern.compile( "FROM\\s+(\\w+) +(\\w+)", Pattern.CASE_INSENSITIVE ), ( matcher, info ) -> {
			info.possibleRootTables.add( matcher.group( 1 ) );
			info.aliases.put( matcher.group( 2 ), matcher.group( 1 ) );
		} );
		patterns.put( Pattern.compile( "FROM\\s+(\\w+).(\\w+)\\s", Pattern.CASE_INSENSITIVE ), ( matcher, info ) -> {
			info.possibleRootTables.add( matcher.group( 2 ) );
		} );
		patterns.put( Pattern.compile( "FROM\\s+(\\w+)\\s", Pattern.CASE_INSENSITIVE ), ( matcher, info ) -> {
			info.possibleRootTables.add( matcher.group( 1 ) );
		} );

		columnPatterns.put(
		    Pattern.compile( "where\\s+(\\w+)\\.(\\w+)\\s*(in|not in|=|>|<|>=|<=|like|not like|<>|!=)", Pattern.CASE_INSENSITIVE ),
		    ( matcher, info ) -> {
			    if ( !info.aliases.containsKey( matcher.group( 1 ) ) ) {
				    return;
			    }
			    info.aliases.get( matcher.group( 1 ) );
		    } );
		columnPatterns.put(
		    Pattern.compile( "where\\s+(\\w+)\\s*(in|not in|=|>|<|>=|<=|like|not like|<>|!=)", Pattern.CASE_INSENSITIVE ),
		    ( matcher, info ) -> {
			    if ( info.possibleRootTables.isEmpty() ) {
				    return;
			    }
		    } );
	}

	QueryInfoExtractor( BoxComponent node ) {
		process( node );
	}

	private void process( BoxComponent node ) {
		List<BoxStringLiteral> outputs = node.getDescendantsOfType( BoxStringLiteral.class, candidate -> candidate.getParent() instanceof BoxBufferOutput );

		for ( BoxStringLiteral output : outputs ) {
			for ( Entry<Pattern, BiConsumer<Matcher, QueryInfoExtractor>> entry : patterns.entrySet() ) {
				Matcher matcher = entry.getKey().matcher( output.getSourceText() );
				if ( !matcher.find() ) {
					continue;
				}
				entry.getValue().accept( matcher, this );
				break;
			}
		}

		for ( BoxStringLiteral output : outputs ) {
			for ( Entry<Pattern, BiConsumer<Matcher, QueryInfoExtractor>> entry : columnPatterns.entrySet() ) {
				Matcher matcher = entry.getKey().matcher( output.getSourceText() );
				if ( !matcher.find() ) {
					continue;
				}
				entry.getValue().accept( matcher, this );
				break;
			}
		}
	}

	String getTableName( String tableOrAlias, String columnName ) {
		if ( tableOrAlias != null && aliases.containsKey( tableOrAlias ) ) {
			return aliases.get( tableOrAlias );
		}

		return possibleRootTables.isEmpty() ? null : possibleRootTables.getFirst();
	}
}