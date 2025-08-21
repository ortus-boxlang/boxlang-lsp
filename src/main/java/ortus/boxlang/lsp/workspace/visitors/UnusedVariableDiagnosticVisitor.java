package ortus.boxlang.lsp.workspace.visitors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

import ortus.boxlang.compiler.ast.BoxNode;
import ortus.boxlang.compiler.ast.expression.BoxAssignment;
import ortus.boxlang.compiler.ast.expression.BoxComparisonOperation;
import ortus.boxlang.compiler.ast.expression.BoxIdentifier;
import ortus.boxlang.compiler.ast.expression.BoxUnaryOperation;
import ortus.boxlang.compiler.ast.statement.BoxArgumentDeclaration;
import ortus.boxlang.compiler.ast.statement.BoxFunctionDeclaration;
import ortus.boxlang.compiler.ast.visitor.VoidBoxVisitor;
import ortus.boxlang.lsp.workspace.ProjectContextProvider;

public class UnusedVariableDiagnosticVisitor extends VoidBoxVisitor {

	private List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();

	public List<Diagnostic> getDiagnostics() {
		return this.diagnostics;
	}

	public void visit( BoxFunctionDeclaration node ) {
		// TODO: mark any code after a top-level BoxReturnStatement as unreachable
		var fvc = new FunctionVariablesCollector( node );
		new FunctionVariablesTracker( fvc ).track( node );
		this.diagnostics = fvc.varUsage.values().stream()
		    .filter( tv -> !tv.used )
		    .map( tv -> {
			    return new Diagnostic(
			        ProjectContextProvider.positionToRange( tv.node.getPosition() ),
			        "Variable [" + tv.name + "] is declared but never used.",
			        DiagnosticSeverity.Warning,
			        "boxlang"
			    );
		    } ).collect( Collectors.toList() );
	}

	private class TrackedVariable {

		public String	name;
		public boolean	used;
		public BoxNode	node;

		public TrackedVariable( String name, boolean used, BoxNode node ) {
			this.name	= name;
			this.used	= used;
			this.node	= node;
		}

		public TrackedVariable markUsed() {
			this.used = true;
			return this;
		}
	}

	private class FunctionVariablesCollector extends VoidBoxVisitor {

		private BoxFunctionDeclaration		func;
		public Map<String, TrackedVariable>	varUsage	= new HashMap<>();

		public FunctionVariablesCollector( BoxFunctionDeclaration func ) {
			this.func = func;
			this.visit();
		}

		public void visit() {
			for ( BoxArgumentDeclaration arg : this.func.getArgs() ) {
				arg.accept( this );
			}

			for ( BoxNode child : this.func.getChildren() ) {
				child.accept( this );
			}
		}

		public void visit( BoxArgumentDeclaration node ) {
			this.varUsage.put( node.getName(), new TrackedVariable( node.getName(), false, node ) );
		}

		public void visit( BoxAssignment node ) {
			if ( node.getLeft() instanceof BoxIdentifier id ) {
				this.varUsage.put( id.getName(), new TrackedVariable( id.getName(), false, node ) );
			}
		}

		public void visit( BoxComparisonOperation node ) {
			if ( node.getLeft() instanceof BoxIdentifier id ) {
				this.varUsage.put( id.getName(), new TrackedVariable( id.getName(), false, node ) );
			}
			if ( node.getRight() instanceof BoxIdentifier id ) {
				this.varUsage.put( id.getName(), new TrackedVariable( id.getName(), false, node ) );
			}
		}
	}

	private class FunctionVariablesTracker extends VoidBoxVisitor {

		private FunctionVariablesCollector fvc;

		public FunctionVariablesTracker( FunctionVariablesCollector fvc ) {
			this.fvc = fvc;
		}

		public void track( BoxFunctionDeclaration node ) {
			for ( BoxArgumentDeclaration arg : node.getArgs() ) {
				arg.accept( this );
			}

			for ( BoxNode child : node.getChildren() ) {
				child.accept( this );
			}

		}

		public void visit( BoxArgumentDeclaration node ) {
			Optional.ofNullable( node.getValue() ).ifPresent( v -> {
				if ( v instanceof BoxIdentifier id ) {
					this.fvc.varUsage.get( id.getName() ).markUsed();
				}
			} );
		}

		public void visit( BoxAssignment node ) {
			if ( node.getRight() instanceof BoxIdentifier id ) {
				this.fvc.varUsage.get( id.getName() ).markUsed();
			}
		}

		public void visit( BoxComparisonOperation node ) {
			if ( node.getLeft() instanceof BoxIdentifier id ) {
				this.fvc.varUsage.get( id.getName() ).markUsed();
			}
			if ( node.getRight() instanceof BoxIdentifier id ) {
				this.fvc.varUsage.get( id.getName() ).markUsed();
			}
		}

		public void visit( BoxUnaryOperation node ) {
			if ( node.getExpr() instanceof BoxIdentifier id ) {
				this.fvc.varUsage.get( id.getName() ).markUsed();
			}
		}

	}

}
