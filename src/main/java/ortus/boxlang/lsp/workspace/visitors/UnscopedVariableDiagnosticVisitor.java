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
import java.util.stream.Collectors;

import org.eclipse.lsp4j.Diagnostic;

import ortus.boxlang.compiler.ast.expression.BoxAssignment;
import ortus.boxlang.compiler.ast.expression.BoxAssignmentModifier;
import ortus.boxlang.compiler.ast.expression.BoxIdentifier;
import ortus.boxlang.compiler.ast.statement.BoxFunctionDeclaration;
import ortus.boxlang.compiler.ast.statement.BoxProperty;
import ortus.boxlang.compiler.ast.visitor.VoidBoxVisitor;
import ortus.boxlang.lsp.workspace.BLASTTools;
import ortus.boxlang.lsp.workspace.ProjectContextProvider;

/**
 * Visitor for detecting unscoped variables.
 * 
 * This is meant for cfc and cfm files as BoxLang puts things into the local scope first.
 */
public class UnscopedVariableDiagnosticVisitor extends VoidBoxVisitor {

	private List<Diagnostic>							diagnostics			= new ArrayList<Diagnostic>();
	private Set<String>									properties			= new HashSet<String>();
	private Map<BoxFunctionDeclaration, Set<String>>	functionDiagnostics	= new HashMap<>();

	public List<Diagnostic> getDiagnostics() {
		return this.diagnostics.stream()
		    .filter( d -> !properties.contains( ( ( String ) d.getData() ).toLowerCase() ) )
		    .collect( Collectors.toList() );
	}

	public void visit( BoxProperty node ) {
		properties.add( BLASTTools.getPropertyName( node ).toLowerCase() );
	}

	public void visit( BoxAssignment node ) {
		if ( ! ( node.getLeft() instanceof BoxIdentifier ) ) {
			return;
		}

		if ( isVarScoped( node ) ) {
			return;
		}

		var	name		= node.getLeft().getSourceText();

		var	function	= node.getFirstAncestorOfType( BoxFunctionDeclaration.class );

		if ( function == null
		    || ( functionDiagnostics.containsKey( function )
		        && functionDiagnostics.get( function ).contains( name.toLowerCase() ) ) ) {
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
		d.setData( name );
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
}
