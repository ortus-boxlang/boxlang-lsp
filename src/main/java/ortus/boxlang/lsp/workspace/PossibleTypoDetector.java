package ortus.boxlang.lsp.workspace;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import ortus.boxlang.compiler.ast.BoxClass;
import ortus.boxlang.compiler.ast.BoxStatement;
import ortus.boxlang.compiler.ast.expression.BoxFunctionInvocation;
import ortus.boxlang.compiler.ast.expression.BoxIdentifier;
import ortus.boxlang.compiler.ast.statement.BoxExpressionStatement;
import ortus.boxlang.compiler.ast.statement.BoxStatementBlock;
import ortus.boxlang.lsp.lint.LintConfigLoader;
import ortus.boxlang.lsp.lint.RuleSettings;
import ortus.boxlang.lsp.lint.rules.PossibleTypoRule;

/** Finds possible typos in the parser-recovery shapes supported by the rule. */
public final class PossibleTypoDetector {

	public static final int DEFAULT_KEYWORD_DISTANCE = 3;

	public record Match( BoxExpressionStatement statement, BoxIdentifier identifier, String suggestion ) {
	}

	private PossibleTypoDetector() {
	}

	public static List<Match> findFunctionKeywordTypos( BoxClass boxClass ) {
		int					distance	= configuredKeywordDistance();
		List<Match>			matches		= new ArrayList<>();
		List<BoxStatement>	body		= boxClass.getBody();

		for ( int i = 0; i + 2 < body.size(); i++ ) {
			if ( ! ( body.get( i ) instanceof BoxExpressionStatement keywordStatement )
			    || ! ( keywordStatement.getExpression() instanceof BoxIdentifier keyword )
			    || ! ( body.get( i + 1 ) instanceof BoxExpressionStatement invocationStatement )
			    || ! ( invocationStatement.getExpression() instanceof BoxFunctionInvocation )
			    || ! ( body.get( i + 2 ) instanceof BoxStatementBlock ) ) {
				continue;
			}

			findClosestMatch( keyword.getName(), List.of( "function" ), distance )
			    .ifPresent( suggestion -> matches.add( new Match( keywordStatement, keyword, suggestion ) ) );
		}
		return matches;
	}

	public static Optional<String> findClosestMatch( String actual, Collection<String> candidates, int maxDistance ) {
		if ( actual == null || candidates == null ) {
			return Optional.empty();
		}

		String	actualName		= actual.toLowerCase( Locale.ROOT );
		String	bestMatch		= null;
		int		bestDistance	= Integer.MAX_VALUE;
		for ( String candidate : candidates ) {
			if ( candidate == null ) {
				continue;
			}

			int distance = levenshteinDistance( actualName, candidate );
			if ( distance > maxDistance ) {
				continue;
			}
			if ( distance < bestDistance ) {
				bestDistance	= distance;
				bestMatch		= candidate;
			} else if ( distance == bestDistance && !candidate.equalsIgnoreCase( bestMatch ) ) {
				return Optional.empty();
			}
		}
		return Optional.ofNullable( bestMatch );
	}

	private static int configuredKeywordDistance() {
		RuleSettings settings = LintConfigLoader.get().forRule( PossibleTypoRule.ID );
		if ( settings == null || settings.params == null ) {
			return DEFAULT_KEYWORD_DISTANCE;
		}

		Object value = settings.params.get( "keywordDistance" );
		if ( value instanceof Number number ) {
			return Math.max( 0, number.intValue() );
		}
		try {
			return Math.max( 0, Integer.parseInt( value.toString() ) );
		} catch ( Exception e ) {
			return DEFAULT_KEYWORD_DISTANCE;
		}
	}

	private static int levenshteinDistance( String left, String right ) {
		String	a	= left.toLowerCase( Locale.ROOT );
		String	b	= right.toLowerCase( Locale.ROOT );
		if ( a.length() < b.length() ) {
			String swap = a;
			a	= b;
			b	= swap;
		}

		int[]	previous	= new int[ b.length() + 1 ];
		int[]	current		= new int[ b.length() + 1 ];
		for ( int j = 0; j <= b.length(); j++ ) {
			previous[ j ] = j;
		}
		for ( int i = 1; i <= a.length(); i++ ) {
			current[ 0 ] = i;
			for ( int j = 1; j <= b.length(); j++ ) {
				int substitution = previous[ j - 1 ] + ( a.charAt( i - 1 ) == b.charAt( j - 1 ) ? 0 : 1 );
				current[ j ] = Math.min( Math.min( current[ j - 1 ] + 1, previous[ j ] + 1 ), substitution );
			}
			int[] swap = previous;
			previous	= current;
			current		= swap;
		}
		return previous[ b.length() ];
	}
}
