package ortus.boxlang.lsp.workspace.visitors;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import ortus.boxlang.compiler.ast.BoxClass;
import ortus.boxlang.compiler.ast.expression.BoxIdentifier;
import ortus.boxlang.lsp.SourceCodeVisitor;
import ortus.boxlang.lsp.lint.DiagnosticRuleRegistry;
import ortus.boxlang.lsp.lint.LintConfigLoader;
import ortus.boxlang.lsp.lint.RuleSettings;
import ortus.boxlang.lsp.lint.rules.PossibleTypoRule;
import ortus.boxlang.lsp.workspace.BLASTTools;
import ortus.boxlang.lsp.workspace.FileParseResult;
import ortus.boxlang.lsp.workspace.PossibleTypoDetector;

/** Reports likely misspellings identified by the possible-typo detector. */
public class PossibleTypoDiagnosticVisitor extends SourceCodeVisitor {

	private final List<Diagnostic>			diagnostics				= new ArrayList<>();
	private final Map<Diagnostic, String>	actualNamesByDiagnostic	= new LinkedHashMap<>();
	private final Map<Diagnostic, String>	suggestionsByDiagnostic	= new LinkedHashMap<>();
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

		for ( PossibleTypoDetector.Match match : PossibleTypoDetector.findFunctionKeywordTypos( node ) ) {
			BoxIdentifier keyword = match.identifier();
			if ( keyword.getPosition() == null ) {
				continue;
			}

			Diagnostic diagnostic = new Diagnostic(
			    BLASTTools.positionToRange( keyword.getPosition() ),
			    "Possible typo: '" + keyword.getName() + "' may be '" + match.suggestion() + "'.",
			    severity,
			    "boxlang",
			    PossibleTypoRule.ID
			);
			diagnostic.setData( Map.of( "id", UUID.randomUUID().toString() ) );
			diagnostics.add( diagnostic );
			actualNamesByDiagnostic.put( diagnostic, keyword.getName() );
			suggestionsByDiagnostic.put( diagnostic, match.suggestion() );
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
		    .map( diagnostic -> createCodeAction( diagnostic, actualNamesByDiagnostic.get( diagnostic ), suggestionsByDiagnostic.get( diagnostic ) ) )
		    .filter( action -> action != null )
		    .toList();
	}

	@Override
	public boolean canVisit( FileParseResult parseResult ) {
		return true;
	}

	private CodeAction createCodeAction( Diagnostic diagnostic, String actualName, String suggestion ) {
		if ( actualName == null || suggestion == null || diagnostic.getRange() == null || filePath == null ) {
			return null;
		}

		CodeAction action = new CodeAction( "Replace '" + actualName + "' with '" + suggestion + "'" );
		action.setKind( CodeActionKind.QuickFix );
		action.setIsPreferred( true );
		action.setDiagnostics( List.of( diagnostic ) );
		action.setEdit( new WorkspaceEdit( Map.of(
		    filePath,
		    List.of( new TextEdit( diagnostic.getRange(), suggestion ) ) ) ) );
		return action;
	}
}
