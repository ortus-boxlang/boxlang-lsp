package ortus.boxlang.lsp.workspace.visitors;

import java.util.List;

import org.eclipse.lsp4j.Position;

import ortus.boxlang.compiler.ast.BoxNode;
import ortus.boxlang.compiler.ast.expression.BoxArgument;
import ortus.boxlang.compiler.ast.expression.BoxFunctionInvocation;
import ortus.boxlang.compiler.ast.expression.BoxMethodInvocation;
import ortus.boxlang.compiler.ast.expression.BoxNew;

/**
 * Finds function/method invocation nodes that contain the cursor position.
 * Used for signature help to determine which function call the user is typing in
 * and which parameter they are currently on.
 *
 * Uses direct AST traversal to ensure all node types in the tree are visited.
 */
public class FindSignatureHelpTargetVisitor {

	private BoxNode			target;
	private int				activeParameter	= 0;
	private final Position	cursorPosition;
	private final int		line;
	private final int		column;
	private final BoxNode	rootNode;

	public FindSignatureHelpTargetVisitor( Position cursorPosition, BoxNode rootNode ) {
		this.cursorPosition	= cursorPosition;
		this.line			= this.cursorPosition.getLine() + 1; // Convert to 1-based
		this.column			= this.cursorPosition.getCharacter();
		this.rootNode		= rootNode;

		// Perform the search immediately on construction
		searchNode( rootNode );
	}

	public BoxNode getTarget() {
		return target;
	}

	public int getActiveParameter() {
		return activeParameter;
	}

	/**
	 * Recursively search the AST for function/method calls containing the cursor.
	 */
	private void searchNode( BoxNode node ) {
		if ( node == null ) {
			return;
		}

		// Check if this is a function invocation
		if ( node instanceof BoxFunctionInvocation fnInvocation ) {
			if ( containsPositionFull( fnInvocation ) && isCursorInArgumentList( fnInvocation ) ) {
				this.target			= fnInvocation;
				this.activeParameter	= calculateActiveParameter( fnInvocation.getArguments() );
			}
		}

		// Check if this is a method invocation
		if ( node instanceof BoxMethodInvocation methodInvocation ) {
			if ( containsPositionFull( methodInvocation ) && isCursorInArgumentList( methodInvocation ) ) {
				this.target			= methodInvocation;
				this.activeParameter	= calculateActiveParameter( methodInvocation.getArguments() );
			}
		}

		// Check if this is a new expression
		if ( node instanceof BoxNew newExpr ) {
			if ( containsPositionFull( newExpr ) && isCursorInNewExpression( newExpr ) ) {
				this.target			= newExpr;
				this.activeParameter	= calculateActiveParameter( newExpr.getArguments() );
			}
		}

		// Recurse into children
		for ( BoxNode child : node.getChildren() ) {
			searchNode( child );
		}
	}

	/**
	 * Determine if the cursor is within the argument list of a function invocation.
	 * This means after the opening '(' and before or at the closing ')'.
	 */
	private boolean isCursorInArgumentList( BoxFunctionInvocation node ) {
		var pos = node.getPosition();
		if ( pos == null ) {
			return false;
		}

		// The node spans the entire invocation including function name
		// We need to check if we're past the opening paren
		// Get the function name length
		String funcName = node.getName();

		// Position is within the node, and we're likely in the arguments
		// since we already verified containsPosition
		int startLine	= pos.getStart().getLine();
		int startCol	= pos.getStart().getColumn();
		int endLine		= pos.getEnd().getLine();
		int endCol		= pos.getEnd().getColumn();

		// Simple heuristic: if cursor is within the node and there are arguments
		// or if cursor is past the function name position, we're in the args
		// Check if cursor is after the function name + opening paren
		if ( line == startLine ) {
			// Same line - check column is past function name + (
			int openParenApprox = startCol + funcName.length();
			return column >= openParenApprox;
		} else if ( line > startLine && line < endLine ) {
			// Cursor is on a middle line of a multi-line call
			return true;
		} else if ( line == endLine ) {
			// On the last line - should still be within bounds
			return column <= endCol;
		}

		return false;
	}

	/**
	 * Determine if the cursor is within the argument list of a method invocation.
	 */
	private boolean isCursorInArgumentList( BoxMethodInvocation node ) {
		var pos = node.getPosition();
		if ( pos == null ) {
			return false;
		}

		// Method invocations have the pattern: obj.methodName(args)
		// We need to check if cursor is in the argument part
		int endLine	= pos.getEnd().getLine();
		int endCol	= pos.getEnd().getColumn();

		// If there are arguments, check their positions
		List<BoxArgument> args = node.getArguments();
		if ( args != null && !args.isEmpty() ) {
			// Check if cursor is within the first arg's start to the end of the node
			var firstArgPos = args.get( 0 ).getPosition();
			if ( firstArgPos != null ) {
				int argStartLine = firstArgPos.getStart().getLine();
				int argStartCol	 = firstArgPos.getStart().getColumn();

				if ( line > argStartLine || ( line == argStartLine && column >= argStartCol - 1 ) ) {
					if ( line < endLine || ( line == endLine && column <= endCol ) ) {
						return true;
					}
				}
			}
		}

		// No args or couldn't determine - use node position heuristic
		// If we're past the method name identifier, we're probably in args
		var methodNameId = node.getName();
		if ( methodNameId != null && methodNameId.getPosition() != null ) {
			var namePos		= methodNameId.getPosition();
			int nameEndLine	= namePos.getEnd().getLine();
			int nameEndCol	= namePos.getEnd().getColumn();

			// If cursor is after the method name, we're in args
			if ( line > nameEndLine || ( line == nameEndLine && column > nameEndCol ) ) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Determine if the cursor is within the argument list of a new expression.
	 */
	private boolean isCursorInNewExpression( BoxNew node ) {
		var pos = node.getPosition();
		if ( pos == null ) {
			return false;
		}

		// Check if there's an expression (class name) and cursor is past it
		var expression = node.getExpression();
		if ( expression != null && expression.getPosition() != null ) {
			var exprPos		= expression.getPosition();
			int exprEndLine	= exprPos.getEnd().getLine();
			int exprEndCol	= exprPos.getEnd().getColumn();

			// If cursor is after the class name, we're likely in the constructor args
			if ( line > exprEndLine || ( line == exprEndLine && column > exprEndCol ) ) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Calculate which parameter the cursor is currently on based on the
	 * positions of arguments.
	 */
	private int calculateActiveParameter( List<BoxArgument> arguments ) {
		if ( arguments == null || arguments.isEmpty() ) {
			return 0;
		}

		int paramIndex = 0;

		for ( int i = 0; i < arguments.size(); i++ ) {
			BoxArgument arg		= arguments.get( i );
			var			argPos	= arg.getPosition();

			if ( argPos == null ) {
				continue;
			}

			int argStartLine	= argPos.getStart().getLine();
			int argStartCol		= argPos.getStart().getColumn();
			int argEndLine		= argPos.getEnd().getLine();
			int argEndCol		= argPos.getEnd().getColumn();

			// Check if cursor is before this argument starts
			if ( line < argStartLine || ( line == argStartLine && column < argStartCol ) ) {
				// Cursor is before this argument, so it's on the previous parameter slot
				return i;
			}

			// Check if cursor is within this argument
			if ( ( line > argStartLine || ( line == argStartLine && column >= argStartCol ) ) &&
			    ( line < argEndLine || ( line == argEndLine && column <= argEndCol ) ) ) {
				return i;
			}

			// Cursor is after this argument
			paramIndex = i + 1;
		}

		// Cursor is after all arguments - return last valid index
		return Math.min( paramIndex, arguments.size() );
	}

	/**
	 * Check if the cursor is within the full extent of the node (not limited to function name).
	 * Unlike BLASTTools.containsPosition, this uses the full node position including arguments.
	 */
	private boolean containsPositionFull( BoxNode node ) {
		var nodePos = node.getPosition();

		if ( nodePos == null ) {
			return false;
		}

		int	boxStartLine	= nodePos.getStart().getLine();
		int	boxStartCol		= nodePos.getStart().getColumn();
		int	boxEndLine		= nodePos.getEnd().getLine();
		int	boxEndCol		= nodePos.getEnd().getColumn();

		if ( line < boxStartLine || line > boxEndLine ) {
			return false;
		}

		if ( line == boxStartLine && column < boxStartCol ) {
			return false;
		}

		if ( line == boxEndLine && column > boxEndCol ) {
			return false;
		}

		return true;
	}
}
