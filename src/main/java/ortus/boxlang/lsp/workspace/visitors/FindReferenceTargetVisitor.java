package ortus.boxlang.lsp.workspace.visitors;

import org.eclipse.lsp4j.Position;

import ortus.boxlang.compiler.ast.BoxClass;
import ortus.boxlang.compiler.ast.BoxInterface;
import ortus.boxlang.compiler.ast.BoxNode;
import ortus.boxlang.compiler.ast.BoxScript;
import ortus.boxlang.compiler.ast.BoxTemplate;
import ortus.boxlang.compiler.ast.expression.BoxDotAccess;
import ortus.boxlang.compiler.ast.expression.BoxFQN;
import ortus.boxlang.compiler.ast.expression.BoxFunctionInvocation;
import ortus.boxlang.compiler.ast.expression.BoxIdentifier;
import ortus.boxlang.compiler.ast.expression.BoxMethodInvocation;
import ortus.boxlang.compiler.ast.expression.BoxNew;
import ortus.boxlang.compiler.ast.expression.BoxScope;
import ortus.boxlang.compiler.ast.statement.BoxAnnotation;
import ortus.boxlang.compiler.ast.statement.BoxArgumentDeclaration;
import ortus.boxlang.compiler.ast.statement.BoxFunctionDeclaration;
import ortus.boxlang.compiler.ast.statement.BoxProperty;
import ortus.boxlang.compiler.ast.visitor.VoidBoxVisitor;
import ortus.boxlang.lsp.workspace.BLASTTools;

/**
 * Visitor that finds the AST node at a specific cursor position for finding references.
 * Handles classes, interfaces, methods, functions, properties, and variables.
 */
public class FindReferenceTargetVisitor extends VoidBoxVisitor {

	private BoxNode			referenceTarget;
	private final Position	cursorPosition;
	private int				line;
	private int				column;

	public FindReferenceTargetVisitor( Position cursorPosition ) {
		this.cursorPosition	= cursorPosition;
		this.line			= this.cursorPosition.getLine() + 1;
		this.column			= this.cursorPosition.getCharacter();
	}

	public BoxNode getReferenceTarget() {
		return referenceTarget;
	}

	// ============ Container node types - traverse into them ============

	@Override
	public void visit( BoxClass node ) {
		// Check if cursor is on the class declaration line itself (not inside methods)
		if ( BLASTTools.containsPosition( node, line, column ) ) {
			var pos = node.getPosition();
			if ( pos != null ) {
				int classStartLine = pos.getStart().getLine();
				// Only set target if cursor is on the class declaration line itself
				if ( line == classStartLine && this.referenceTarget == null ) {
					this.referenceTarget = node;
				}
			}
		}
		visitChildren( node );
	}

	@Override
	public void visit( BoxInterface node ) {
		// Check if cursor is on the interface declaration line itself
		if ( BLASTTools.containsPosition( node, line, column ) ) {
			var pos = node.getPosition();
			if ( pos != null ) {
				int interfaceStartLine = pos.getStart().getLine();
				// Only set target if cursor is on the interface declaration line itself
				if ( line == interfaceStartLine && this.referenceTarget == null ) {
					this.referenceTarget = node;
				}
			}
		}
		visitChildren( node );
	}

	@Override
	public void visit( BoxScript node ) {
		visitChildren( node );
	}

	@Override
	public void visit( BoxTemplate node ) {
		visitChildren( node );
	}

	// ============ Target node types - check position and set target ============

	@Override
	public void visit( BoxFunctionDeclaration node ) {
		if ( !BLASTTools.containsPosition( node, line, column ) ) {
			return;
		}

		// Visit children first to find more specific targets like parameters
		visitChildren( node );

		// Check if cursor is on the function declaration line (the function name itself)
		var	nodePos		= node.getPosition();
		int	funcLine	= nodePos.getStart().getLine();

		// If cursor is on the function declaration line and no more specific target found,
		// set the function as the target
		if ( line == funcLine && this.referenceTarget == null ) {
			this.referenceTarget = node;
		}
	}

	/**
	 * Visit property declarations for finding property references.
	 */
	@Override
	public void visit( BoxProperty node ) {
		if ( !BLASTTools.containsPosition( node, line, column ) ) {
			visitChildren( node );
			return;
		}

		this.referenceTarget = node;
	}

	/**
	 * Visit argument declarations for finding parameter references.
	 */
	@Override
	public void visit( BoxArgumentDeclaration node ) {
		if ( !BLASTTools.containsPosition( node, line, column ) ) {
			visitChildren( node );
			return;
		}

		this.referenceTarget = node;
	}

	/**
	 * Visit method invocations for finding method references.
	 */
	@Override
	public void visit( BoxMethodInvocation node ) {
		if ( !BLASTTools.containsPosition( node, line, column ) ) {
			visitChildren( node );
			return;
		}

		this.referenceTarget = node;
	}

	/**
	 * Visit function invocations for finding function references.
	 */
	@Override
	public void visit( BoxFunctionInvocation node ) {
		if ( !BLASTTools.containsPosition( node, line, column ) ) {
			visitChildren( node );
			return;
		}

		this.referenceTarget = node;
	}

	/**
	 * Visit variable identifiers for finding variable references.
	 */
	@Override
	public void visit( BoxIdentifier node ) {
		if ( !BLASTTools.containsPosition( node, line, column ) ) {
			visitChildren( node );
			return;
		}

		// Only set as target if we haven't found a more specific node
		if ( this.referenceTarget == null ) {
			this.referenceTarget = node;
		}
	}

	/**
	 * Visit new expressions (class instantiation) for finding class references.
	 */
	@Override
	public void visit( BoxNew node ) {
		if ( !BLASTTools.containsPosition( node, line, column ) ) {
			visitChildren( node );
			return;
		}

		// Check if cursor is on the class name part
		BoxNode expression = node.getExpression();
		if ( expression instanceof BoxFQN fqn && BLASTTools.containsPosition( fqn, line, column ) ) {
			// Cursor is on the class name - set BoxNew as target
			this.referenceTarget = node;
			return;
		}

		visitChildren( node );
	}

	/**
	 * Visit annotations (extends, implements) for finding class/interface references.
	 */
	@Override
	public void visit( BoxAnnotation node ) {
		if ( !BLASTTools.containsPosition( node, line, column ) ) {
			visitChildren( node );
			return;
		}

		String key = node.getKey().getValue().toLowerCase();
		if ( key.equals( "extends" ) || key.equals( "implements" ) ) {
			// Check if cursor is on the value
			if ( node.getValue() != null && BLASTTools.containsPosition( node.getValue(), line, column ) ) {
				this.referenceTarget = node;
				return;
			}
		}

		visitChildren( node );
	}

	/**
	 * Visit dot access for property references via scoped access.
	 */
	@Override
	public void visit( BoxDotAccess node ) {
		if ( !BLASTTools.containsPosition( node, line, column ) ) {
			visitChildren( node );
			return;
		}

		// Check if this is a property access
		BoxNode context = node.getContext();
		if ( context instanceof BoxScope scope ) {
			String scopeName = scope.getName().toLowerCase();
			if ( scopeName.equals( "variables" ) || scopeName.equals( "this" ) ) {
				if ( node.getAccess() != null && BLASTTools.containsPosition( node.getAccess(), line, column ) ) {
					this.referenceTarget = node;
					return;
				}
			}
		}

		visitChildren( node );
	}

	/**
	 * Visit FQN for class references in type contexts.
	 */
	@Override
	public void visit( BoxFQN node ) {
		if ( !BLASTTools.containsPosition( node, line, column ) ) {
			visitChildren( node );
			return;
		}

		if ( this.referenceTarget == null ) {
			this.referenceTarget = node;
		}
	}

	/**
	 * Default handler for any BoxNode - ensures we traverse all children.
	 * This is crucial because VoidBoxVisitor doesn't automatically traverse children.
	 */
	public void visit( BoxNode node ) {
		visitChildren( node );
	}

	private void visitChildren( BoxNode node ) {
		for ( BoxNode child : node.getChildren() ) {
			child.accept( this );
		}
	}
}
