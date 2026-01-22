package ortus.boxlang.lsp.workspace.visitors;

import org.eclipse.lsp4j.Position;

import ortus.boxlang.compiler.ast.BoxNode;
import ortus.boxlang.compiler.ast.expression.BoxFunctionInvocation;
import ortus.boxlang.compiler.ast.expression.BoxIdentifier;
import ortus.boxlang.compiler.ast.expression.BoxMethodInvocation;
import ortus.boxlang.compiler.ast.visitor.VoidBoxVisitor;
import ortus.boxlang.lsp.workspace.BLASTTools;

/**
 * Visitor that finds the AST node at a specific cursor position for go-to-definition.
 * Handles function invocations, method invocations, and variable identifiers.
 */
public class FindDefinitionTargetVisitor extends VoidBoxVisitor {

	private BoxNode			definitionTarget;
	private final Position	cursorPosition;
	private int				line;
	private int				column;

	public FindDefinitionTargetVisitor( Position cursorPosition ) {
		this.cursorPosition	= cursorPosition;
		this.line			= this.cursorPosition.getLine() + 1;
		this.column			= this.cursorPosition.getCharacter();
	}

	public BoxNode getDefinitionTarget() {
		return definitionTarget;
	}

	public void visit( BoxMethodInvocation node ) {
		if ( !BLASTTools.containsPosition( node, line, column ) ) {
			visitChildren( node );
			return;
		}

		this.definitionTarget = node;
	}

	public void visit( BoxFunctionInvocation node ) {
		if ( !BLASTTools.containsPosition( node, line, column ) ) {
			visitChildren( node );
			return;
		}

		this.definitionTarget = node;
	}

	/**
	 * Visit variable identifiers for go-to-definition on local variables.
	 * This enables navigating from variable usage to its declaration.
	 */
	public void visit( BoxIdentifier node ) {
		if ( !BLASTTools.containsPosition( node, line, column ) ) {
			visitChildren( node );
			return;
		}

		// Only set as definition target if we haven't found a more specific node
		// (function invocations take priority)
		if ( this.definitionTarget == null ) {
			this.definitionTarget = node;
		}
	}

	private void visitChildren( BoxNode node ) {
		for ( BoxNode child : node.getChildren() ) {
			child.accept( this );
		}
	}
}
