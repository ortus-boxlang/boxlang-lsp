package ortus.boxlang.lsp.workspace.visitors;

import org.eclipse.lsp4j.Position;

import ortus.boxlang.compiler.ast.BoxNode;
import ortus.boxlang.compiler.ast.expression.BoxFunctionInvocation;
import ortus.boxlang.compiler.ast.expression.BoxIdentifier;
import ortus.boxlang.compiler.ast.expression.BoxMethodInvocation;
import ortus.boxlang.compiler.ast.statement.BoxFunctionDeclaration;
import ortus.boxlang.compiler.ast.visitor.VoidBoxVisitor;
import ortus.boxlang.lsp.workspace.BLASTTools;

/**
 * Visitor to find the AST node at a given cursor position for hover information.
 * This visitor looks for function invocations, method invocations, and function declarations.
 */
public class FindHoverTargetVisitor extends VoidBoxVisitor {

	private BoxNode			hoverTarget;
	private final Position	cursorPosition;
	private int				line;
	private int				column;

	public FindHoverTargetVisitor( Position cursorPosition ) {
		this.cursorPosition	= cursorPosition;
		this.line			= this.cursorPosition.getLine() + 1;
		this.column			= this.cursorPosition.getCharacter();
	}

	public BoxNode getHoverTarget() {
		return hoverTarget;
	}

	@Override
	public void visit( BoxFunctionDeclaration node ) {
		if ( !BLASTTools.containsPosition( node, line, column ) ) {
			return;
		}

		// First, recurse into children to find more specific targets (like function invocations)
		visitChildren( node );

		// Only set this as the target if we haven't found a more specific one
		// and the cursor is on the function name (in the declaration line)
		if ( this.hoverTarget == null && isPositionOnFunctionName( node ) ) {
			this.hoverTarget = node;
		}
	}

	@Override
	public void visit( BoxMethodInvocation node ) {
		if ( !BLASTTools.containsPosition( node, line, column ) ) {
			return;
		}

		// Method invocations are specific - set as target
		this.hoverTarget = node;
	}

	@Override
	public void visit( BoxFunctionInvocation node ) {
		if ( !BLASTTools.containsPosition( node, line, column ) ) {
			return;
		}

		// Function invocations are specific - set as target
		this.hoverTarget = node;
	}

	@Override
	public void visit( BoxIdentifier node ) {
		if ( !BLASTTools.containsPosition( node, line, column ) ) {
			return;
		}

		// For now, we'll capture identifiers but may want to resolve them to functions
		// This allows hover on variable references that might be function references
		// Only set if no more specific target found
		if ( this.hoverTarget == null ) {
			this.hoverTarget = node;
		}
	}

	private boolean isPositionOnFunctionName( BoxFunctionDeclaration node ) {
		// The function name position is typically within the function declaration
		// We need to check if the cursor is specifically on the function name
		var pos = node.getPosition();
		if ( pos == null ) {
			return false;
		}

		// Simple heuristic: if we're in the function and haven't found a more specific target,
		// and the function has a name at this position
		String	funcName	= node.getName();
		int		startLine	= pos.getStart().getLine();
		int		startCol	= pos.getStart().getColumn();

		// Check if cursor is roughly in the area where the function name would be
		// The function name appears after modifiers, return type, and "function" keyword
		// This is an approximation - we check if cursor is on the first line of the function
		// and within a reasonable column range
		return line == startLine || ( line == startLine + 1 && column < startCol + 100 );
	}

	private void visitChildren( BoxNode node ) {
		for ( BoxNode child : node.getChildren() ) {
			child.accept( this );
		}
	}
}
