package ortus.boxlang.lsp.workspace.visitors;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SQLFeatureExtractor {

	private static final Pattern	replacementStartParens		= Pattern.compile( "\\((\s*)$", Pattern.CASE_INSENSITIVE );
	private static final Pattern	replacementStartComma		= Pattern.compile( "(,)(\s*)$", Pattern.CASE_INSENSITIVE );
	private static final Pattern	replacementStartOperator	= Pattern.compile( "(,|in|not in|=|>|<|>=|<=|like|not like|<>|!=)(\s*)$",
	    Pattern.CASE_INSENSITIVE );
	private static final Pattern	replacementStartKeyword		= Pattern.compile( "(by|then|select|into|from)(\s*)$", Pattern.CASE_INSENSITIVE );
	private static final Pattern	replacementStartQuote		= Pattern.compile( "(n*')[^']*?$", Pattern.CASE_INSENSITIVE );
	private static final Pattern	replacementStartSpace		= Pattern.compile( " $", Pattern.CASE_INSENSITIVE );

	private static final Pattern	replacementEndComma			= Pattern.compile( "^\s*,", Pattern.CASE_INSENSITIVE );
	private static final Pattern	replacementEndParens		= Pattern.compile( "^\s*(\\))", Pattern.CASE_INSENSITIVE );
	private static final Pattern	replacementEndSingleQuote	= Pattern.compile( "^([^']*)?(')", Pattern.CASE_INSENSITIVE );
	private static final Pattern	replacementEndMethod		= Pattern.compile( "^(.+)*(')\s*(,|\\))", Pattern.CASE_INSENSITIVE );
	private static final Pattern	replacementEndSpace			= Pattern.compile( "^( |\r*\n)", Pattern.CASE_INSENSITIVE );

	private static final Pattern	listPattern					= Pattern.compile( "(in|not in)\\s*\\(\\s*'*\\s*$", Pattern.CASE_INSENSITIVE );
	private static final Pattern	leftIncludeQuotePattern		= Pattern.compile(
	    "(by|then|select|,|in|not in|=|>|<|>=|<=|like|not like|<>|!=)\s*'([^']+?)$",
	    Pattern.CASE_INSENSITIVE );
	private static final Pattern	leftIncludeNoQuote			= Pattern.compile(
	    "(by|then|select|,|in|not in|=|>|<|>=|<=|like|not like|<>|!=)\s*(\s+)*$",
	    Pattern.CASE_INSENSITIVE );

	private SQLFeatureExtractor() {
	}

	public static boolean isList( String leftText ) {
		return listPattern.matcher( leftText ).find();
	}

	public static String getLeftInclude( String leftText ) {
		if ( leftText.endsWith( "'" ) ) {
			return "";
		}

		Matcher	match		= leftIncludeNoQuote.matcher( leftText );
		String	captured	= "";

		for ( int i = leftText.length() - 1; i >= 0; i-- ) {
			match = leftIncludeNoQuote.matcher( leftText.substring( i ) );
			if ( match.find() ) {
				captured = match.group( 2 );
				return captured == null ? "" : captured;
			}
		}

		match = leftIncludeQuotePattern.matcher( leftText );
		if ( match.find() ) {
			captured = match.group( 2 );
		}

		return captured;
	}

	public static int getReplacementEndIndex( String rightText ) {
		int start = testPatternStart( replacementEndComma, rightText, 0 );
		if ( start > -1 ) {
			return start;
		}

		start = testPatternStart( replacementEndParens, rightText, 1 );
		if ( start > -1 ) {
			return start;
		}

		start = testPatternEnd( replacementEndSingleQuote, rightText, 2 );
		if ( start > -1 ) {
			return start;
		}

		start = testPatternEnd( replacementEndMethod, rightText, 1 );
		if ( start > -1 ) {
			return start;
		}

		return testPatternStart( replacementEndSpace, rightText, 0 );
	}

	public static int getReplacementStartIndex( String leftText ) {
		int start = testPatternEnd( replacementStartComma, leftText, 2 );
		if ( start > 0 ) {
			return start;
		}

		start = testPatternEnd( replacementStartParens, leftText, 1 );
		if ( start > 0 ) {
			return start;
		}

		start = testPatternEnd( replacementStartOperator, leftText, 2 );
		if ( start > 0 ) {
			return start;
		}

		start = testPatternEnd( replacementStartKeyword, leftText, 2 );
		if ( start > 0 ) {
			return start;
		}

		start = testPatternStart( replacementStartQuote, leftText, 1 );
		if ( start > -1 ) {
			return start;
		}

		start = testPatternEnd( replacementStartSpace, leftText, 0 );
		if ( start > 0 ) {
			return start;
		}

		if ( leftText.endsWith( "." ) ) {
			return leftText.length();
		}

		return -1;
	}

	private static int testPatternStart( Pattern pattern, String source, int group ) {
		Matcher matcher = pattern.matcher( source );
		if ( !matcher.find() ) {
			return -1;
		}
		return matcher.start( group );
	}

	private static int testPatternEnd( Pattern pattern, String source, int group ) {
		Matcher matcher = pattern.matcher( source );
		if ( !matcher.find() ) {
			return -1;
		}
		return matcher.end( group );
	}
}