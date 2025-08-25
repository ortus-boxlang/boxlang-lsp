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
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Diagnostic;
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
		return this.diagnostics.stream()
		    .filter( d -> !properties.contains( ( ( String ) ( ( Map ) d.getData() ).get( "variableName" ) ).toLowerCase() ) )
		    .collect( Collectors.toList() );
	}

	public boolean canVisit( FileParseResult parseResult ) {
		return parseResult.isCF();
	}

	@Override
	public List<CodeAction> getCodeActions() {
		return this.diagnostics.stream()
		    .map( d -> this.createCodeAction( d ) )
		    .toList();
	}

	public void visit( BoxProperty node ) {
		properties.add( BLASTTools.getPropertyName( node ).toLowerCase() );
	}

	public void visit( BoxArgumentDeclaration node ) {
		BoxFunctionDeclaration function = node.getFirstAncestorOfType( BoxFunctionDeclaration.class );
		functionVard.computeIfAbsent( function, k -> new HashSet<>() )
		    .add( node.getName().toLowerCase() );
	}

	public void visit( BoxAssignment node ) {

		var function = node.getFirstAncestorOfType( BoxFunctionDeclaration.class );

		// we are in a script or psuedo constructor
		if ( function == null ) {
			trackAssignmentInPsuedoConstructor( node );
			return;
		}

		if ( ! ( node.getLeft() instanceof BoxIdentifier ) ) {
			return;
		}

		var name = node.getLeft().getSourceText();

		functionVard.computeIfAbsent( function, k -> new HashSet<>() );

		if ( isVarScoped( node ) ) {
			functionVard.get( function )
			    .add( name.toLowerCase() );
			return;
		}

		if ( functionVard.get( function ).contains( name.toLowerCase() ) ) {
			return;
		}

		if ( functionDiagnostics.containsKey( function ) && functionDiagnostics.get( function ).contains( name.toLowerCase() ) ) {
			return;
		}

		if ( properties.contains( name.toLowerCase() ) ) {
			return;
		}

		var d = new Diagnostic(
		    ProjectContextProvider.positionToRange( node.getPosition() ),
		    "Variable [" + name + "] is not scoped.",
		    org.eclipse.lsp4j.DiagnosticSeverity.Warning,
		    "boxlang"
		);
		d.setData( Map.of( "variableName", name, "id", UUID.randomUUID().toString() ) );
		diagnosticNodes.put( d, node );

		diagnostics.add( d );

		functionDiagnostics.computeIfAbsent( function, k -> new HashSet<>() )
		    .add( name.toLowerCase() );
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
				properties.add( ( ( String ) accessIdentifier.getSourceText() ).toLowerCase() );
			}
		} else if ( node.getLeft() instanceof BoxIdentifier id ) {
			properties.add( ( ( String ) id.getSourceText() ).toLowerCase() );
		} else if ( node.getLeft() instanceof BoxArrayAccess arrayAccess ) {
			if ( arrayAccess.getAccess() instanceof BoxStringLiteral accessIdentifier ) {
				properties.add( ( ( String ) accessIdentifier.getAsSimpleValue() ).toLowerCase() );
			}
		}
	}

	private CodeAction createCodeAction( Diagnostic diagnostic ) {
		BoxNode			node			= diagnosticNodes.get( diagnostic );
		TextEdit		edit			= new TextEdit(
		    ProjectContextProvider.positionToRange( node.getPosition() ),
		    node.getSourceText().replaceAll( "^", "var " ) );

		WorkspaceEdit	workspaceEdit	= new WorkspaceEdit( new HashMap<>() );

		workspaceEdit.getChanges().put( this.filePath, new ArrayList<>() );
		workspaceEdit.getChanges().get( this.filePath ).add( edit );

		CodeAction action = new CodeAction( "Add var keyword to " + node.getSourceText() );
		action.setEdit( workspaceEdit );
		action.setKind( CodeActionKind.QuickFix );
		action.setDiagnostics( new ArrayList<>() );
		action.getDiagnostics().add( diagnostic );

		return action;
	}
}
