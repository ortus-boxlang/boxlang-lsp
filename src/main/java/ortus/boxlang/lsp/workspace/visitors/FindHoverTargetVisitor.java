package ortus.boxlang.lsp.workspace.visitors;

import org.eclipse.lsp4j.Position;

import ortus.boxlang.compiler.ast.BoxNode;
import ortus.boxlang.compiler.ast.expression.BoxDotAccess;
import ortus.boxlang.compiler.ast.expression.BoxFQN;
import ortus.boxlang.compiler.ast.expression.BoxFunctionInvocation;
import ortus.boxlang.compiler.ast.expression.BoxIdentifier;
import ortus.boxlang.compiler.ast.expression.BoxMethodInvocation;
import ortus.boxlang.compiler.ast.expression.BoxNew;
import ortus.boxlang.compiler.ast.expression.BoxScope;
import ortus.boxlang.compiler.ast.expression.BoxStringLiteral;
import ortus.boxlang.compiler.ast.statement.BoxAnnotation;
import ortus.boxlang.compiler.ast.statement.BoxArgumentDeclaration;
import ortus.boxlang.compiler.ast.statement.BoxFunctionDeclaration;
import ortus.boxlang.compiler.ast.statement.BoxProperty;
import ortus.boxlang.compiler.ast.visitor.VoidBoxVisitor;
import ortus.boxlang.lsp.workspace.BLASTTools;

/**
 * Visitor to find the AST node at a given cursor position for hover information.
 * This visitor looks for function invocations, method invocations, function declarations,
 * variable identifiers, and properties.
 *
 * The visitor traverses the AST and finds the most specific node at the cursor position.
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
			visitChildren( node );
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
			visitChildren( node );
			return;
		}

		// Method invocations are specific - set as target
		this.hoverTarget = node;
	}

	@Override
	public void visit( BoxFunctionInvocation node ) {
		if ( !BLASTTools.containsPosition( node, line, column ) ) {
			visitChildren( node );
			return;
		}

		// Function invocations are specific - set as target
		this.hoverTarget = node;
	}

	@Override
	public void visit( BoxArgumentDeclaration node ) {
		if ( !BLASTTools.containsPosition( node, line, column ) ) {
			visitChildren( node );
			return;
		}

		// Parameter declarations have priority over generic identifiers
		this.hoverTarget = node;
	}

	@Override
	public void visit( BoxProperty node ) {
		if ( !BLASTTools.containsPosition( node, line, column ) ) {
			visitChildren( node );
			return;
		}

		// Property declarations
		this.hoverTarget = node;
	}

	@Override
	public void visit( BoxDotAccess node ) {
		if ( !BLASTTools.containsPosition( node, line, column ) ) {
			visitChildren( node );
			return;
		}

		// Check if cursor is on the scope prefix (e.g., "variables", "this", "local")
		if ( node.getContext() instanceof BoxIdentifier scopeId ) {
			if ( BLASTTools.containsPosition( scopeId, line, column ) ) {
				// Cursor is on the scope keyword
				this.hoverTarget = scopeId;
				return;
			}
		}

		// Otherwise, visit children for the accessed property
		visitChildren( node );
	}

	@Override
	public void visit( BoxScope node ) {
		if ( !BLASTTools.containsPosition( node, line, column ) ) {
			visitChildren( node );
			return;
		}

		// Scope keywords (variables, local, this, arguments, etc.)
		// Set as target for hover on scope
		this.hoverTarget = node;
	}

	@Override
	public void visit( BoxNew node ) {
		if ( !BLASTTools.containsPosition( node, line, column ) ) {
			visitChildren( node );
			return;
		}

		// Check if cursor is on the class name part (BoxFQN)
		BoxNode expression = node.getExpression();
		if ( expression instanceof BoxFQN fqn && BLASTTools.containsPosition( fqn, line, column ) ) {
			// Cursor is on the class name - set BoxNew as target so we have context
			this.hoverTarget = node;
			return;
		}

		// Otherwise, visit children for any other targets
		visitChildren( node );
	}

	@Override
	public void visit( BoxFQN node ) {
		if ( !BLASTTools.containsPosition( node, line, column ) ) {
			visitChildren( node );
			return;
		}

		// A fully qualified name - could be a class reference in various contexts
		// (e.g., type hints, extends/implements, etc.)
		// Only set if no more specific target found
		if ( this.hoverTarget == null ) {
			this.hoverTarget = node;
		}
	}

	@Override
	public void visit( BoxAnnotation node ) {
		if ( !BLASTTools.containsPosition( node, line, column ) ) {
			visitChildren( node );
			return;
		}

		String key = node.getKey().getValue().toLowerCase();

		// Handle extends and implements annotations
		if ( key.equals( "extends" ) || key.equals( "implements" ) ) {
			// Check if cursor is on the value (the class/interface name)
			if ( node.getValue() != null && BLASTTools.containsPosition( node.getValue(), line, column ) ) {
				// Set the annotation as target so we can extract the class name from it
				this.hoverTarget = node;
				return;
			}
		}

		// Visit children for other annotations
		visitChildren( node );
	}

	@Override
	public void visit( BoxStringLiteral node ) {
		if ( !BLASTTools.containsPosition( node, line, column ) ) {
			visitChildren( node );
			return;
		}

		// Check if this string literal is the value of an extends or implements annotation
		BoxNode parent = node.getParent();
		if ( parent instanceof BoxAnnotation annotation ) {
			String key = annotation.getKey().getValue().toLowerCase();
			if ( key.equals( "extends" ) || key.equals( "implements" ) ) {
				// Set the parent annotation as target
				this.hoverTarget = annotation;
				return;
			}
		}

		// For other string literals, don't set as target
		visitChildren( node );
	}

	@Override
	public void visit( BoxIdentifier node ) {
		if ( !BLASTTools.containsPosition( node, line, column ) ) {
			visitChildren( node );
			return;
		}

		// Identifiers represent variables - capture them for hover
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
