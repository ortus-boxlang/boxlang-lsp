package ortus.boxlang.lsp.workspace.visitors;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DiagnosticTag;

import ortus.boxlang.compiler.ast.BoxNode;
import ortus.boxlang.compiler.ast.expression.BoxArrayAccess;
import ortus.boxlang.compiler.ast.expression.BoxAssignment;
import ortus.boxlang.compiler.ast.expression.BoxIdentifier;
import ortus.boxlang.compiler.ast.statement.BoxArgumentDeclaration;
import ortus.boxlang.compiler.ast.statement.BoxFunctionDeclaration;
import ortus.boxlang.compiler.ast.statement.BoxProperty;
import ortus.boxlang.lsp.SourceCodeVisitor;
import ortus.boxlang.lsp.workspace.BLASTTools;
import ortus.boxlang.lsp.workspace.ProjectContextProvider;
import ortus.boxlang.lsp.lint.DiagnosticRuleRegistry;
import ortus.boxlang.lsp.lint.LintConfigLoader;
import ortus.boxlang.lsp.lint.rules.UnusedVariableRule;

public class UnusedVariableDiagnosticVisitor extends SourceCodeVisitor {

	private Set<String>									properties				= new HashSet<String>();
	private Set<BoxFunctionDeclaration>					hasArgumentsIdentifier	= new HashSet<>();
	private Map<BoxFunctionDeclaration, Set<BoxNode>>	assignedVars			= new WeakHashMap<>();
	private Map<BoxFunctionDeclaration, Set<String>>	usedVars				= new WeakHashMap<>();

	public List<Diagnostic> getDiagnostics() {
		if ( !DiagnosticRuleRegistry.getInstance().isEnabled( UnusedVariableRule.ID, true ) ) {
			return List.of();
		}
		var settings = LintConfigLoader.get().forRule( UnusedVariableRule.ID );
		return assignedVars.entrySet().stream()
		    .flatMap( entry -> {
			    return entry.getValue().stream()
			        .filter(
			            item -> !usedVars.containsKey( entry.getKey() ) || !usedVars.get( entry.getKey() ).contains( getNameFromNode( item ).toLowerCase() ) );
		    } )
		    .filter( varNode -> !properties.contains( getNameFromNode( varNode ).toLowerCase() ) )
		    .filter( varNode -> !hasArgumentsIdentifier.contains( varNode.getFirstAncestorOfType( BoxFunctionDeclaration.class ) ) )
		    .map( varNode -> {
			    var diagnostic = new Diagnostic(
			        ProjectContextProvider.positionToRange( varNode.getPosition() ),
			        "Variable [" + getNameFromNode( varNode ) + "] is declared but never used.",
			        settings == null ? DiagnosticSeverity.Hint : settings.toSeverityOr( DiagnosticSeverity.Hint ),
			        "boxlang"
			    );

			    diagnostic.setTags( List.of( DiagnosticTag.Unnecessary ) );

			    return diagnostic;
		    } )
		    .collect( Collectors.toList() );
	}

	public List<CodeAction> getCodeActions() {
		if ( !DiagnosticRuleRegistry.getInstance().isEnabled( UnusedVariableRule.ID, true ) ) {
			return List.of();
		}
		return new ArrayList<CodeAction>();
	}

	public void visit( BoxProperty node ) {
		properties.add( BLASTTools.getPropertyName( node ).toLowerCase() );
	}

	private String getNameFromNode( BoxNode node ) {
		if ( node instanceof BoxIdentifier ) {
			return ( ( BoxIdentifier ) node ).getName();
		} else if ( node instanceof BoxArgumentDeclaration ) {
			return ( ( BoxArgumentDeclaration ) node ).getName();
		}
		return null;
	}

	public void visit( BoxArgumentDeclaration node ) {
		BoxFunctionDeclaration func = node.getFirstAncestorOfType( BoxFunctionDeclaration.class );

		assignedVars.computeIfAbsent( func, k -> new HashSet<>() ).add( node );
	}

	public void visit( BoxIdentifier node ) {
		BoxFunctionDeclaration	func	= node.getFirstAncestorOfType( BoxFunctionDeclaration.class );
		String					name	= getNameFromNode( node ).toLowerCase();

		// if arguments is used anywhere in a function then we assume all args are used
		if ( name.equalsIgnoreCase( "arguments" ) ) {
			hasArgumentsIdentifier.add( func );
			return;
		}

		// if not in a func then we are in the pseudo constructor, just track as a property
		if ( func == null ) {
			properties.add( name );
			return;
		}

		if ( this.isBeingAssignedTo( node ) ) {
			// Check if this variable name has already been assigned in this function
			String			varName			= node.getName().toLowerCase();
			Set<BoxNode>	assignedInFunc	= assignedVars.computeIfAbsent( func, k -> new HashSet<>() );

			// If we already have an assignment for this variable name, treat this as a use (reassignment)
			boolean			alreadyAssigned	= assignedInFunc.stream()
			    .anyMatch( n -> varName.equals( getNameFromNode( n ).toLowerCase() ) );

			if ( alreadyAssigned ) {
				// This is a reassignment, count it as a use
				usedVars.computeIfAbsent( func, k -> new HashSet<>() ).add( varName );
			} else {
				// This is the first assignment, track it as assigned
				assignedInFunc.add( node );
			}
			return;
		}

		usedVars.computeIfAbsent( func, k -> new HashSet<>() ).add( node.getName().toLowerCase() );
	}

	private boolean isBeingAssignedTo( BoxIdentifier node ) {
		BoxAssignment assignment = ( BoxAssignment ) node.getFirstAncestorOfType( BoxAssignment.class );

		if ( assignment != null && node.getAncestors().contains( assignment.getRight() ) ) {
			return false;
		}

		BoxArrayAccess arrayAccess = ( BoxArrayAccess ) node.getFirstAncestorOfType( BoxArrayAccess.class );

		if ( arrayAccess != null
		    && ( node.getAncestors().contains( arrayAccess.getAccess() )
		        || node == arrayAccess.getAccess() ) ) {
			return false;
		}

		if ( assignment != null
		    && ( node.getAncestors().contains( assignment.getLeft() )
		        || assignment.getLeft() == node ) ) {
			return true;
		}

		if ( node.getFirstAncestorOfType( BoxArgumentDeclaration.class ) != null ) {
			return true;
		}

		// TODO what about thing[ usedVariable ] = 4? That should count as a use of usedVariable but it is to the left
		// check if the identifier is part of an array access

		return false;
	}

}
