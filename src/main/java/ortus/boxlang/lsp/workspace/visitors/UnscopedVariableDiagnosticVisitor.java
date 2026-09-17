/**
 * [BoxLang LSP]
 *
 * Copyright [2023] [Ortus Solutions, Corp]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ortus.boxlang.lsp.workspace.visitors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;

import ortus.boxlang.compiler.ast.BoxNode;
import ortus.boxlang.compiler.ast.expression.BoxArrayAccess;
import ortus.boxlang.compiler.ast.expression.BoxAssignment;
import ortus.boxlang.compiler.ast.expression.BoxAssignmentModifier;
import ortus.boxlang.compiler.ast.expression.BoxDotAccess;
import ortus.boxlang.compiler.ast.expression.BoxIdentifier;
import ortus.boxlang.compiler.ast.expression.BoxStringLiteral;
import ortus.boxlang.compiler.ast.statement.BoxArgumentDeclaration;
import ortus.boxlang.compiler.ast.statement.BoxFunctionDeclaration;
import ortus.boxlang.compiler.ast.statement.BoxProperty;
import ortus.boxlang.lsp.SourceCodeVisitor;
import ortus.boxlang.lsp.lint.DiagnosticRuleRegistry;
import ortus.boxlang.lsp.lint.LintConfigLoader;
import ortus.boxlang.lsp.lint.rules.UnscopedVariableRule;
import ortus.boxlang.lsp.workspace.BLASTTools;
import ortus.boxlang.lsp.workspace.FileParseResult;
import ortus.boxlang.lsp.workspace.ProjectContextProvider;

/**
 * Visitor for detecting unscoped variables.
 * 
 * This is meant for cfc and cfm files as BoxLang puts things into the local scope first.
 */
public class UnscopedVariableDiagnosticVisitor extends SourceCodeVisitor {

	private List<Diagnostic>							diagnostics			= new ArrayList<Diagnostic>();
	private Set<String>									properties			= new HashSet<String>();
	private Map<BoxFunctionDeclaration, Set<String>>	functionDiagnostics	= new HashMap<>();
	private Map<BoxFunctionDeclaration, Set<String>>	functionVard		= new HashMap<>();
	private Map<Diagnostic, BoxNode>					diagnosticNodes		= new WeakHashMap<>();

	public List<Diagnostic> getDiagnostics() {
		if ( !DiagnosticRuleRegistry.getInstance().isEnabled( UnscopedVariableRule.ID, true ) ) {
			return List.of();
		}
		var settings = LintConfigLoader.get().forRule( UnscopedVariableRule.ID );
		return this.diagnostics.stream()
		    .filter( d -> {
			    Object dataObj = d.getData();
			    if ( dataObj instanceof Map<?, ?> m ) {
				    Object v = m.get( "variableName" );
				    if ( v != null ) {
					    return !properties.contains( v.toString().toLowerCase() );
				    }
			    }
			    return true;
		    } )
		    .peek( d -> {
			    if ( settings != null ) {
				    d.setSeverity( settings.toSeverityOr( DiagnosticSeverity.Warning ) );
			    }
		    } )
		    .collect( Collectors.toList() );
	}

	public boolean canVisit( FileParseResult parseResult ) {
		return parseResult.isCF();
	}

	@Override
	public List<CodeAction> getCodeActions() {
		if ( !DiagnosticRuleRegistry.getInstance().isEnabled( UnscopedVariableRule.ID, true ) ) {
			return List.of();
		}
		return this.diagnostics.stream()
		    .map( d -> this.createCodeAction( d ) )
		    .filter( c -> c != null )
		    .toList();
	}

	public void visit( BoxProperty node ) {
		BLASTTools.getPropertyName( node ).ifPresent( name -> properties.add( name.toLowerCase() ) );
	}

	public void visit( BoxArgumentDeclaration node ) {
		BoxFunctionDeclaration function = node.getFirstAncestorOfType( BoxFunctionDeclaration.class );
		functionVard.computeIfAbsent( function, k -> new HashSet<>() )
		    .add( node.getName().toLowerCase() );
	}

	public void visit( BoxAssignment node ) {

		if ( !DiagnosticRuleRegistry.getInstance().isEnabled( UnscopedVariableRule.ID, true ) ) {
			return;
		}

		var function = node.getFirstAncestorOfType( BoxFunctionDeclaration.class );

		// we are in a script or psuedo constructor
		if ( function == null ) {
			trackAssignmentInPsuedoConstructor( node );
			return;
		}

		if ( ! ( node.getLeft() instanceof BoxIdentifier identifier ) ) {
			return;
		}

		Optional<String> name = BLASTTools.getName( identifier );
		if ( name.isEmpty() ) {
			return;
		}
		String variableName = name.get();

		functionVard.computeIfAbsent( function, k -> new HashSet<>() );

		if ( isVarScoped( node ) ) {
			functionVard.get( function )
			    .add( variableName.toLowerCase() );
			return;
		}

		if ( functionVard.get( function ).contains( variableName.toLowerCase() ) ) {
			return;
		}

		if ( functionDiagnostics.containsKey( function ) && functionDiagnostics.get( function ).contains( variableName.toLowerCase() ) ) {
			return;
		}

		if ( properties.contains( variableName.toLowerCase() ) ) {
			return;
		}

		var					range		= ProjectContextProvider.positionToRange( node.getPosition() );
		Optional<String>	sourceText	= BLASTTools.getSourceText( node );
		if ( range.getStart().getLine() != range.getEnd().getLine() && sourceText.isPresent() ) {
			String firstLine = sourceText.get().lines().findFirst().orElse( sourceText.get() );
			range.getEnd().setLine( range.getStart().getLine() );
			range.getEnd().setCharacter( range.getStart().getCharacter() + firstLine.length() );
		}

		var d = new Diagnostic(
		    range,
		    "Variable [" + variableName + "] is not scoped.",
		    org.eclipse.lsp4j.DiagnosticSeverity.Warning,
		    "boxlang",
		    UnscopedVariableRule.ID
		);
		d.setData( Map.of( "variableName", variableName, "id", UUID.randomUUID().toString() ) );
		diagnosticNodes.put( d, node );

		diagnostics.add( d );

		functionDiagnostics.computeIfAbsent( function, k -> new HashSet<>() )
		    .add( variableName.toLowerCase() );
	}

	public boolean isVarScoped( BoxAssignment node ) {
		for ( var modifier : node.getModifiers() ) {
			if ( modifier == BoxAssignmentModifier.VAR ) {
				return true;
			}
		}

		return false;
	}

	private void trackAssignmentInPsuedoConstructor( BoxAssignment node ) {
		if ( node.getLeft() instanceof BoxDotAccess bda ) {
			var access = bda.getAccess();

			if ( access instanceof BoxIdentifier accessIdentifier ) {
				BLASTTools.getName( accessIdentifier ).ifPresent( name -> properties.add( name.toLowerCase() ) );
			}
		} else if ( node.getLeft() instanceof BoxIdentifier id ) {
			BLASTTools.getName( id ).ifPresent( name -> properties.add( name.toLowerCase() ) );
		} else if ( node.getLeft() instanceof BoxArrayAccess arrayAccess ) {
			if ( arrayAccess.getAccess() instanceof BoxStringLiteral accessIdentifier ) {
				BLASTTools.getValue( accessIdentifier ).ifPresent( name -> properties.add( name.toLowerCase() ) );
			}
		}
	}

	private CodeAction createCodeAction( Diagnostic diagnostic ) {
		if ( !DiagnosticRuleRegistry.getInstance().isEnabled( UnscopedVariableRule.ID, true ) ) {
			return null;
		}
		BoxNode node = diagnosticNodes.get( diagnostic );

		if ( node == null || node.getPosition() == null ) {
			return null;
		}
		Optional<String> sourceText = BLASTTools.getSourceText( node );
		if ( sourceText.isEmpty() ) {
			return null;
		}
		String			source			= sourceText.get();

		TextEdit		edit			= new TextEdit(
		    ProjectContextProvider.positionToRange( node.getPosition() ),
		    source.replaceAll( "^", "var " ) );

		WorkspaceEdit	workspaceEdit	= new WorkspaceEdit( new HashMap<>() );

		workspaceEdit.getChanges().put( this.filePath, new ArrayList<>() );
		workspaceEdit.getChanges().get( this.filePath ).add( edit );

		CodeAction action = new CodeAction( "Add var keyword to " + source );
		action.setEdit( workspaceEdit );
		action.setKind( CodeActionKind.QuickFix );
		action.setDiagnostics( new ArrayList<>() );
		action.getDiagnostics().add( diagnostic );

		return action;
	}
}
