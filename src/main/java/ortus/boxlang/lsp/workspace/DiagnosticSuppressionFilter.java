package ortus.boxlang.lsp.workspace;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import ortus.boxlang.compiler.ast.BoxClass;
import ortus.boxlang.compiler.ast.BoxInterface;
import ortus.boxlang.compiler.ast.BoxNode;
import ortus.boxlang.compiler.ast.expression.BoxArrayLiteral;
import ortus.boxlang.compiler.ast.expression.BoxStringLiteral;
import ortus.boxlang.compiler.ast.statement.BoxAnnotation;
import ortus.boxlang.compiler.ast.statement.BoxFunctionDeclaration;

/**
 * Applies annotation and comment-based suppression directives to diagnostics.
 */
public class DiagnosticSuppressionFilter {

	private static final String		SUPPRESS_WARNINGS_KEY			= "suppresswarnings";
	private static final String		ALL_RULES						= "all";
	private static final String		DIRECTIVE_DISABLE				= "disable";
	private static final String		DIRECTIVE_ENABLE				= "enable";
	private static final String		DIRECTIVE_ENABLE_FOR_FUNCTION	= "enable-for-function";
	private static final String		DIRECTIVE_ENABLE_FOR_CLASS		= "enable-for-class";
	private static final String		DIRECTIVE_DISABLE_FOR_FUNCTION	= "disable-for-function";
	private static final String		DIRECTIVE_DISABLE_FOR_CLASS		= "disable-for-class";
	private static final Pattern	COMMENT_DIRECTIVE_PATTERN		= Pattern.compile(
	    "^\\s*(?://\\s*|<!---?\\s*)bxlint(?::|-)(enable-for-function|enable-for-class|disable-for-function|disable-for-class|disable|enable)(?:\\s+(.*?))?\\s*(?:--+>)?\\s*$",
	    Pattern.CASE_INSENSITIVE
	);

	private record SuppressionRule( Range range, boolean suppressAllWarningsAndErrors, Set<String> suppressedRuleIds ) {
	}

	private record CommentDirective( String type, int line, boolean suppressAllWarningsAndErrors, Set<String> suppressedRuleIds ) {
	}

	private record OpenSectionDirective( int startLine, boolean suppressAllWarningsAndErrors, Set<String> suppressedRuleIds ) {
	}

	private record ParsedCommentRules( List<SuppressionRule> suppressionRules, List<SuppressionRule> unsuppressionRules ) {
	}

	private final List<SuppressionRule>	suppressionRules;
	private final List<SuppressionRule>	unsuppressionRules;

	private DiagnosticSuppressionFilter( List<SuppressionRule> suppressionRules ) {
		this( suppressionRules, List.of() );
	}

	private DiagnosticSuppressionFilter( List<SuppressionRule> suppressionRules, List<SuppressionRule> unsuppressionRules ) {
		this.suppressionRules	= suppressionRules;
		this.unsuppressionRules	= unsuppressionRules;
	}

	public static DiagnosticSuppressionFilter fromAst( BoxNode astRoot ) {
		return fromAst( astRoot, List.of() );
	}

	public static DiagnosticSuppressionFilter fromAst( BoxNode astRoot, List<String> sourceLines ) {
		if ( astRoot == null ) {
			return new DiagnosticSuppressionFilter( List.of() );
		}

		List<SuppressionRule>	rules			= new ArrayList<>( astRoot.getDescendantsOfType( BoxAnnotation.class ).stream()
		    .filter( DiagnosticSuppressionFilter::isSuppressWarningsAnnotation )
		    .map( DiagnosticSuppressionFilter::toSuppressionRule )
		    .filter( rule -> rule != null )
		    .toList() );

		ParsedCommentRules		commentRules	= extractCommentSuppressionRules( astRoot, sourceLines );
		rules.addAll( commentRules.suppressionRules() );

		return new DiagnosticSuppressionFilter( rules, commentRules.unsuppressionRules() );
	}

	public List<Diagnostic> filterDiagnostics( List<Diagnostic> diagnostics ) {
		if ( diagnostics == null || diagnostics.isEmpty() || ( suppressionRules.isEmpty() && unsuppressionRules.isEmpty() ) ) {
			return diagnostics == null ? List.of() : diagnostics;
		}

		return diagnostics.stream()
		    .filter( diagnostic -> !isSuppressed( diagnostic ) )
		    .toList();
	}

	public List<CodeAction> filterCodeActions( List<CodeAction> codeActions ) {
		if ( codeActions == null || codeActions.isEmpty() || ( suppressionRules.isEmpty() && unsuppressionRules.isEmpty() ) ) {
			return codeActions == null ? List.of() : codeActions;
		}

		return codeActions.stream()
		    .filter( this::hasUnsuppressedDiagnostic )
		    .toList();
	}

	private boolean hasUnsuppressedDiagnostic( CodeAction codeAction ) {
		if ( codeAction == null || codeAction.getDiagnostics() == null || codeAction.getDiagnostics().isEmpty() ) {
			return true;
		}

		return codeAction.getDiagnostics().stream().anyMatch( diagnostic -> !isSuppressed( diagnostic ) );
	}

	private boolean isSuppressed( Diagnostic diagnostic ) {
		if ( diagnostic == null || diagnostic.getRange() == null || diagnostic.getRange().getStart() == null ) {
			return false;
		}

		Position	diagnosticPosition	= diagnostic.getRange().getStart();
		String		ruleId				= normalizeRuleId( diagnostic );

		for ( SuppressionRule unsuppressionRule : unsuppressionRules ) {
			if ( !contains( unsuppressionRule.range(), diagnosticPosition ) ) {
				continue;
			}

			if ( matchesRule( unsuppressionRule, ruleId ) ) {
				return false;
			}
		}

		for ( SuppressionRule suppressionRule : suppressionRules ) {
			if ( !contains( suppressionRule.range(), diagnosticPosition ) ) {
				continue;
			}

			if ( matchesRule( suppressionRule, ruleId ) ) {
				return true;
			}
		}

		return false;
	}

	private static boolean matchesRule( SuppressionRule suppressionRule, String ruleId ) {
		if ( ruleId == null ) {
			return false;
		}

		return suppressionRule.suppressAllWarningsAndErrors() || suppressionRule.suppressedRuleIds().contains( ruleId );
	}

	private static boolean contains( Range range, Position position ) {
		if ( range == null || range.getStart() == null || range.getEnd() == null || position == null ) {
			return false;
		}

		return compare( position, range.getStart() ) >= 0
		    && compare( position, range.getEnd() ) <= 0;
	}

	private static int compare( Position a, Position b ) {
		if ( a.getLine() != b.getLine() ) {
			return Integer.compare( a.getLine(), b.getLine() );
		}
		return Integer.compare( a.getCharacter(), b.getCharacter() );
	}

	private static String normalizeRuleId( Diagnostic diagnostic ) {
		if ( diagnostic.getCode() == null ) {
			return null;
		}

		if ( diagnostic.getCode().isLeft() && diagnostic.getCode().getLeft() != null ) {
			return diagnostic.getCode().getLeft().trim().toLowerCase( Locale.ROOT );
		}

		if ( diagnostic.getCode().isRight() && diagnostic.getCode().getRight() != null ) {
			return diagnostic.getCode().getRight().toString().trim().toLowerCase( Locale.ROOT );
		}

		return null;
	}

	private static boolean isSuppressWarningsAnnotation( BoxAnnotation annotation ) {
		if ( annotation == null || annotation.getKey() == null || annotation.getKey().getValue() == null ) {
			return false;
		}

		return SUPPRESS_WARNINGS_KEY.equals( annotation.getKey().getValue().trim().toLowerCase( Locale.ROOT ) );
	}

	private static SuppressionRule toSuppressionRule( BoxAnnotation annotation ) {
		BoxNode targetNode = annotation.getParent();
		if ( targetNode == null || targetNode.getPosition() == null ) {
			return null;
		}

		Range range = ProjectContextProvider.positionToRange( targetNode.getPosition() );
		if ( range == null ) {
			return null;
		}

		Set<String>	suppressedRuleIds				= extractSuppressedRuleIds( annotation );
		boolean		suppressAllWarningsAndErrors	= annotation.getValue() == null || suppressedRuleIds.isEmpty() || suppressedRuleIds.contains( ALL_RULES );
		if ( suppressAllWarningsAndErrors ) {
			suppressedRuleIds = Set.of();
		}

		return new SuppressionRule( range, suppressAllWarningsAndErrors, suppressedRuleIds );
	}

	private static ParsedCommentRules extractCommentSuppressionRules( BoxNode astRoot, List<String> sourceLines ) {
		if ( sourceLines == null || sourceLines.isEmpty() ) {
			return new ParsedCommentRules( List.of(), List.of() );
		}

		List<CommentDirective> directives = parseCommentDirectives( sourceLines );
		if ( directives.isEmpty() ) {
			return new ParsedCommentRules( List.of(), List.of() );
		}

		List<SuppressionRule>		rules				= new ArrayList<>();
		List<SuppressionRule>		unsuppressionRules	= new ArrayList<>();
		List<OpenSectionDirective>	openSections		= new ArrayList<>();

		for ( CommentDirective directive : directives ) {
			switch ( directive.type() ) {
				case DIRECTIVE_DISABLE -> openSections
				    .add( new OpenSectionDirective( directive.line(), directive.suppressAllWarningsAndErrors(), directive.suppressedRuleIds() ) );
				case DIRECTIVE_ENABLE -> closeMatchingSectionDirective( directive, openSections, rules, sourceLines.size() );
				case DIRECTIVE_DISABLE_FOR_FUNCTION -> addScopedSuppressionRule( rules, astRoot, directive, true );
				case DIRECTIVE_DISABLE_FOR_CLASS -> addScopedSuppressionRule( rules, astRoot, directive, false );
				case DIRECTIVE_ENABLE_FOR_FUNCTION -> addScopedSuppressionRule( unsuppressionRules, astRoot, directive, true );
				case DIRECTIVE_ENABLE_FOR_CLASS -> addScopedSuppressionRule( unsuppressionRules, astRoot, directive, false );
				default -> {
				}
			}
		}

		for ( OpenSectionDirective openSection : openSections ) {
			Range range = toSectionRange( openSection.startLine(), sourceLines.size() - 1 );
			if ( range != null ) {
				rules.add( new SuppressionRule( range, openSection.suppressAllWarningsAndErrors(), openSection.suppressedRuleIds() ) );
			}
		}

		return new ParsedCommentRules( rules, unsuppressionRules );
	}

	private static void closeMatchingSectionDirective(
	    CommentDirective directive,
	    List<OpenSectionDirective> openSections,
	    List<SuppressionRule> rules,
	    int sourceLineCount ) {
		int matchIndex = findMatchingSectionDirective( directive, openSections );
		if ( matchIndex < 0 ) {
			return;
		}

		OpenSectionDirective	openSection	= openSections.remove( matchIndex );
		Range					range		= toSectionRange( openSection.startLine(), directive.line() );
		if ( range == null ) {
			return;
		}

		rules.add( new SuppressionRule( range, openSection.suppressAllWarningsAndErrors(), openSection.suppressedRuleIds() ) );
	}

	private static int findMatchingSectionDirective( CommentDirective directive, List<OpenSectionDirective> openSections ) {
		for ( int i = openSections.size() - 1; i >= 0; i-- ) {
			OpenSectionDirective openSection = openSections.get( i );
			if ( directive.suppressAllWarningsAndErrors() ) {
				return i;
			}

			if ( !openSection.suppressAllWarningsAndErrors() && openSection.suppressedRuleIds().equals( directive.suppressedRuleIds() ) ) {
				return i;
			}
		}

		return -1;
	}

	private static Range toSectionRange( int startLine, int endLine ) {
		int effectiveStart = startLine;
		if ( endLine < effectiveStart ) {
			return null;
		}

		return new Range( new Position( effectiveStart, 0 ), new Position( endLine, Integer.MAX_VALUE ) );
	}

	private static void addScopedSuppressionRule( List<SuppressionRule> rules, BoxNode astRoot, CommentDirective directive, boolean functionScope ) {
		Range targetRange = functionScope ? findNextFunctionRange( astRoot, directive.line() ) : findNextClassLikeRange( astRoot, directive.line() );
		if ( targetRange == null ) {
			return;
		}

		rules.add( new SuppressionRule( targetRange, directive.suppressAllWarningsAndErrors(), directive.suppressedRuleIds() ) );
	}

	private static Range findNextFunctionRange( BoxNode astRoot, int commentLine ) {
		return astRoot.getDescendantsOfType( BoxFunctionDeclaration.class ).stream()
		    .map( node -> ProjectContextProvider.positionToRange( node.getPosition() ) )
		    .filter( range -> range != null && range.getStart() != null && range.getStart().getLine() > commentLine )
		    .min( Comparator.comparingInt( range -> range.getStart().getLine() ) )
		    .orElse( null );
	}

	private static Range findNextClassLikeRange( BoxNode astRoot, int commentLine ) {
		List<Range> ranges = new ArrayList<>();
		astRoot.getDescendantsOfType( BoxClass.class ).stream()
		    .map( node -> ProjectContextProvider.positionToRange( node.getPosition() ) )
		    .filter( range -> range != null && range.getStart() != null && range.getStart().getLine() > commentLine )
		    .forEach( ranges::add );
		astRoot.getDescendantsOfType( BoxInterface.class ).stream()
		    .map( node -> ProjectContextProvider.positionToRange( node.getPosition() ) )
		    .filter( range -> range != null && range.getStart() != null && range.getStart().getLine() > commentLine )
		    .forEach( ranges::add );

		return ranges.stream()
		    .min( Comparator.comparingInt( range -> range.getStart().getLine() ) )
		    .orElse( null );
	}

	private static List<CommentDirective> parseCommentDirectives( List<String> sourceLines ) {
		List<CommentDirective> directives = new ArrayList<>();

		for ( int line = 0; line < sourceLines.size(); line++ ) {
			String currentLine = sourceLines.get( line );
			if ( currentLine == null ) {
				continue;
			}

			Matcher matcher = COMMENT_DIRECTIVE_PATTERN.matcher( currentLine );
			if ( !matcher.matches() ) {
				continue;
			}

			String directiveType = toDirectiveType( matcher.group( 1 ) );
			if ( directiveType == null ) {
				continue;
			}

			Set<String>	suppressedRuleIds				= extractSuppressedRuleIds( matcher.group( 2 ) );
			boolean		suppressAllWarningsAndErrors	= suppressedRuleIds.isEmpty() || suppressedRuleIds.contains( ALL_RULES );
			if ( suppressAllWarningsAndErrors ) {
				suppressedRuleIds = Set.of();
			}

			directives.add( new CommentDirective( directiveType, line, suppressAllWarningsAndErrors, suppressedRuleIds ) );
		}

		return directives;
	}

	private static String toDirectiveType( String rawDirective ) {
		if ( rawDirective == null ) {
			return null;
		}

		return switch ( rawDirective.trim().toLowerCase( Locale.ROOT ) ) {
			case DIRECTIVE_DISABLE -> DIRECTIVE_DISABLE;
			case DIRECTIVE_ENABLE -> DIRECTIVE_ENABLE;
			case DIRECTIVE_ENABLE_FOR_FUNCTION -> DIRECTIVE_ENABLE_FOR_FUNCTION;
			case DIRECTIVE_ENABLE_FOR_CLASS -> DIRECTIVE_ENABLE_FOR_CLASS;
			case DIRECTIVE_DISABLE_FOR_FUNCTION -> DIRECTIVE_DISABLE_FOR_FUNCTION;
			case DIRECTIVE_DISABLE_FOR_CLASS -> DIRECTIVE_DISABLE_FOR_CLASS;
			default -> null;
		};
	}

	private static Set<String> extractSuppressedRuleIds( String rawValue ) {
		return new HashSet<>( parseRuleList( stripReason( rawValue ) ) );
	}

	private static Set<String> extractSuppressedRuleIds( BoxAnnotation annotation ) {
		Set<String> ruleIds = new HashSet<>();

		if ( annotation == null || annotation.getValue() == null ) {
			return ruleIds;
		}

		if ( annotation.getValue() instanceof BoxArrayLiteral arrayLiteral ) {
			for ( BoxNode valueNode : arrayLiteral.getValues() ) {
				ruleIds.addAll( parseRuleList( sourceTextForValue( valueNode ) ) );
			}
			return ruleIds;
		}

		ruleIds.addAll( parseRuleList( sourceTextForValue( annotation.getValue() ) ) );
		return ruleIds;
	}

	private static String sourceTextForValue( BoxNode valueNode ) {
		if ( valueNode == null ) {
			return "";
		}

		if ( valueNode instanceof BoxStringLiteral boxStringLiteral ) {
			return boxStringLiteral.getValue();
		}

		return valueNode.getSourceText();
	}

	private static List<String> parseRuleList( String rawValue ) {
		if ( rawValue == null ) {
			return List.of();
		}

		String trimmed = rawValue.trim();
		if ( trimmed.isEmpty() ) {
			return List.of();
		}

		String[]		tokens	= trimmed.split( "[,\\s]+" );
		List<String>	results	= new ArrayList<>( tokens.length );

		for ( String token : tokens ) {
			String normalized = stripQuotes( token.trim() ).toLowerCase( Locale.ROOT );
			if ( !normalized.isEmpty() ) {
				results.add( normalized );
			}
		}

		return results;
	}

	private static String stripReason( String rawValue ) {
		if ( rawValue == null ) {
			return null;
		}

		int reasonIndex = rawValue.indexOf( "--" );
		if ( reasonIndex < 0 ) {
			return rawValue;
		}

		return rawValue.substring( 0, reasonIndex ).trim();
	}

	private static String stripQuotes( String value ) {
		if ( value == null || value.length() < 2 ) {
			return value == null ? "" : value;
		}

		char	first	= value.charAt( 0 );
		char	last	= value.charAt( value.length() - 1 );
		if ( ( first == '"' && last == '"' ) || ( first == '\'' && last == '\'' ) ) {
			return value.substring( 1, value.length() - 1 );
		}

		return value;
	}
}