package ortus.boxlang.lsp.workspace;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import ortus.boxlang.compiler.ast.BoxNode;
import ortus.boxlang.compiler.ast.expression.BoxArrayLiteral;
import ortus.boxlang.compiler.ast.expression.BoxStringLiteral;
import ortus.boxlang.compiler.ast.statement.BoxAnnotation;

/**
 * Applies @SuppressWarnings annotations to diagnostics.
 */
public class DiagnosticSuppressionFilter {

	private static final String SUPPRESS_WARNINGS_KEY = "suppresswarnings";

	private record SuppressionRule( Range range, boolean suppressAllWarningsAndErrors, Set<String> suppressedRuleIds ) {
	}

	private final List<SuppressionRule> suppressionRules;

	private DiagnosticSuppressionFilter( List<SuppressionRule> suppressionRules ) {
		this.suppressionRules = suppressionRules;
	}

	public static DiagnosticSuppressionFilter fromAst( BoxNode astRoot ) {
		if ( astRoot == null ) {
			return new DiagnosticSuppressionFilter( List.of() );
		}

		List<SuppressionRule> rules = astRoot.getDescendantsOfType( BoxAnnotation.class ).stream()
		    .filter( DiagnosticSuppressionFilter::isSuppressWarningsAnnotation )
		    .map( DiagnosticSuppressionFilter::toSuppressionRule )
		    .filter( rule -> rule != null )
		    .toList();

		return new DiagnosticSuppressionFilter( rules );
	}

	public List<Diagnostic> filterDiagnostics( List<Diagnostic> diagnostics ) {
		if ( diagnostics == null || diagnostics.isEmpty() || suppressionRules.isEmpty() ) {
			return diagnostics == null ? List.of() : diagnostics;
		}

		return diagnostics.stream()
		    .filter( diagnostic -> !isSuppressed( diagnostic ) )
		    .toList();
	}

	public List<CodeAction> filterCodeActions( List<CodeAction> codeActions ) {
		if ( codeActions == null || codeActions.isEmpty() || suppressionRules.isEmpty() ) {
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

		for ( SuppressionRule suppressionRule : suppressionRules ) {
			if ( !contains( suppressionRule.range(), diagnosticPosition ) ) {
				continue;
			}

			if ( suppressionRule.suppressAllWarningsAndErrors() && isWarningOrError( diagnostic ) ) {
				return true;
			}

			if ( ruleId != null && suppressionRule.suppressedRuleIds().contains( ruleId ) ) {
				return true;
			}
		}

		return false;
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

	private static boolean isWarningOrError( Diagnostic diagnostic ) {
		if ( diagnostic.getSeverity() == null ) {
			return false;
		}
		return diagnostic.getSeverity() == DiagnosticSeverity.Warning
		    || diagnostic.getSeverity() == DiagnosticSeverity.Error;
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
		boolean		suppressAllWarningsAndErrors	= annotation.getValue() == null || suppressedRuleIds.isEmpty() || suppressedRuleIds.contains( "all" );

		return new SuppressionRule( range, suppressAllWarningsAndErrors, suppressedRuleIds );
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

		String[]		tokens	= trimmed.split( "," );
		List<String>	results	= new ArrayList<>( tokens.length );

		for ( String token : tokens ) {
			String normalized = stripQuotes( token.trim() ).toLowerCase( Locale.ROOT );
			if ( !normalized.isEmpty() ) {
				results.add( normalized );
			}
		}

		return results;
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