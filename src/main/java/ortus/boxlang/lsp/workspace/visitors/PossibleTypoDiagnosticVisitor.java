package ortus.boxlang.lsp.workspace.visitors;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import ortus.boxlang.compiler.ast.BoxClass;
import ortus.boxlang.compiler.ast.BoxStatement;
import ortus.boxlang.compiler.ast.expression.BoxFunctionInvocation;
import ortus.boxlang.compiler.ast.expression.BoxIdentifier;
import ortus.boxlang.compiler.ast.statement.BoxExpressionStatement;
import ortus.boxlang.compiler.ast.statement.BoxStatementBlock;
import ortus.boxlang.lsp.SourceCodeVisitor;
import ortus.boxlang.lsp.lint.DiagnosticRuleRegistry;
import ortus.boxlang.lsp.lint.LintConfigLoader;
import ortus.boxlang.lsp.lint.RuleSettings;
import ortus.boxlang.lsp.lint.rules.PossibleTypoRule;
import ortus.boxlang.lsp.workspace.BLASTTools;
import ortus.boxlang.lsp.workspace.FileParseResult;

/** Reports a misspelled {@code function} keyword in a class pseudo-constructor. */
public class PossibleTypoDiagnosticVisitor extends SourceCodeVisitor {

	private static final String				EXPECTED_FUNCTION		= "function";

	private final List<Diagnostic>			diagnostics				= new ArrayList<>();
	private final Map<Diagnostic, String>	actualNamesByDiagnostic	= new LinkedHashMap<>();
	private boolean							analyzed;

	@Override
	public void visit( BoxClass node ) {
		if ( analyzed || !DiagnosticRuleRegistry.getInstance().isEnabled( PossibleTypoRule.ID, true ) ) {
			return;
		}
		analyzed = true;

		RuleSettings		settings	= LintConfigLoader.get().forRule( PossibleTypoRule.ID );
		DiagnosticSeverity	severity	= settings == null
		    ? DiagnosticSeverity.Warning
		    : settings.toSeverityOr( DiagnosticSeverity.Warning );

		List<BoxStatement>	body		= node.getBody();
		for ( int i = 0; i + 2 < body.size(); i++ ) {
			if ( ! ( body.get( i ) instanceof BoxExpressionStatement keywordStatement )
			    || ! ( keywordStatement.getExpression() instanceof BoxIdentifier keyword )
			    || !isFunctionKeywordTypo( keyword.getName() )
			    || ! ( body.get( i + 1 ) instanceof BoxExpressionStatement invocationStatement )
			    || ! ( invocationStatement.getExpression() instanceof BoxFunctionInvocation )
			    || ! ( body.get( i + 2 ) instanceof BoxStatementBlock )
			    || keyword.getPosition() == null ) {
				continue;
			}

			Diagnostic diagnostic = new Diagnostic(
			    BLASTTools.positionToRange( keyword.getPosition() ),
			    "Possible typo: '" + keyword.getName() + "' may be '" + EXPECTED_FUNCTION + "'.",
			    severity,
			    "boxlang",
			    PossibleTypoRule.ID
			);
			diagnostic.setData( Map.of( "id", UUID.randomUUID().toString() ) );
			diagnostics.add( diagnostic );
			actualNamesByDiagnostic.put( diagnostic, keyword.getName() );
		}
	}

	@Override
	public List<Diagnostic> getDiagnostics() {
		return diagnostics;
	}

	@Override
	public List<CodeAction> getCodeActions() {
		if ( !DiagnosticRuleRegistry.getInstance().isEnabled( PossibleTypoRule.ID, true ) ) {
			return List.of();
		}

		return diagnostics.stream()
		    .map( diagnostic -> createCodeAction( diagnostic, actualNamesByDiagnostic.get( diagnostic ) ) )
		    .filter( action -> action != null )
		    .toList();
	}

	@Override
	public boolean canVisit( FileParseResult parseResult ) {
		return true;
	}

	private CodeAction createCodeAction( Diagnostic diagnostic, String actualName ) {
		if ( actualName == null || diagnostic.getRange() == null || filePath == null ) {
			return null;
		}

		CodeAction action = new CodeAction( "Replace '" + actualName + "' with '" + EXPECTED_FUNCTION + "'" );
		action.setKind( CodeActionKind.QuickFix );
		action.setIsPreferred( true );
		action.setDiagnostics( List.of( diagnostic ) );
		action.setEdit( new WorkspaceEdit( Map.of(
		    filePath,
		    List.of( new TextEdit( diagnostic.getRange(), EXPECTED_FUNCTION ) ) ) ) );
		return action;
	}

	private boolean isFunctionKeywordTypo( String identifier ) {
		String	keyword				= identifier.toLowerCase( Locale.ROOT );
		int		lengthDifference	= Math.abs( keyword.length() - EXPECTED_FUNCTION.length() );
		if ( keyword.equals( EXPECTED_FUNCTION ) ) {
			return false;
		}
		if ( keyword.startsWith( EXPECTED_FUNCTION ) ) {
			return true;
		}
		if ( lengthDifference > 1 ) {
			return false;
		}

		int	candidateIndex	= 0;
		int	expectedIndex	= 0;
		int	differences		= 0;
		while ( candidateIndex < keyword.length() && expectedIndex < EXPECTED_FUNCTION.length() ) {
			if ( keyword.charAt( candidateIndex ) == EXPECTED_FUNCTION.charAt( expectedIndex ) ) {
				candidateIndex++;
				expectedIndex++;
				continue;
			}

			if ( ++differences > 1 ) {
				return false;
			}
			if ( keyword.length() > EXPECTED_FUNCTION.length() ) {
				candidateIndex++;
			} else if ( keyword.length() < EXPECTED_FUNCTION.length() ) {
				expectedIndex++;
			} else {
				candidateIndex++;
				expectedIndex++;
			}
		}

		return differences + Math.abs( ( keyword.length() - candidateIndex ) - ( EXPECTED_FUNCTION.length() - expectedIndex ) ) <= 1;
	}
}
