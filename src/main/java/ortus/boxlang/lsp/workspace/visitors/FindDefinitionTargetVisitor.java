package ortus.boxlang.lsp.workspace.visitors;

import org.eclipse.lsp4j.Position;

import ortus.boxlang.compiler.ast.BoxClass;
import ortus.boxlang.compiler.ast.BoxInterface;
import ortus.boxlang.compiler.ast.BoxNode;
import ortus.boxlang.compiler.ast.BoxScript;
import ortus.boxlang.compiler.ast.BoxTemplate;
import ortus.boxlang.compiler.ast.expression.BoxFQN;
import ortus.boxlang.compiler.ast.expression.BoxFunctionInvocation;
import ortus.boxlang.compiler.ast.expression.BoxIdentifier;
import ortus.boxlang.compiler.ast.expression.BoxMethodInvocation;
import ortus.boxlang.compiler.ast.expression.BoxNew;
import ortus.boxlang.compiler.ast.expression.BoxStringLiteral;
import ortus.boxlang.compiler.ast.statement.BoxAnnotation;
import ortus.boxlang.compiler.ast.statement.BoxArgumentDeclaration;
import ortus.boxlang.compiler.ast.statement.BoxFunctionDeclaration;
import ortus.boxlang.compiler.ast.statement.BoxReturnType;
import ortus.boxlang.compiler.ast.visitor.VoidBoxVisitor;
import ortus.boxlang.lsp.workspace.BLASTTools;

/**
 * Visitor that finds the AST node at a specific cursor position for go-to-definition.
 * Handles function invocations, method invocations, variable identifiers, class references,
 * and inheritance annotations (extends/implements).
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

	// ============ Container node types - traverse into them ============

	@Override
	public void visit( BoxClass node ) {
		visitChildren( node );
	}

	@Override
	public void visit( BoxInterface node ) {
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

	@Override
	public void visit( BoxFunctionDeclaration node ) {
		visitChildren( node );
	}

	// ============ Target node types - check position and set target ============

	@Override
	public void visit( BoxMethodInvocation node ) {
		if ( !BLASTTools.containsPosition( node, line, column ) ) {
			visitChildren( node );
			return;
		}

		this.definitionTarget = node;
	}

	@Override
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
	@Override
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

	/**
	 * Visit new expressions (class instantiation) for go-to-definition on class names.
	 * This enables navigating from `new ClassName()` to the class definition.
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
			// Cursor is on the class name - set BoxNew as target so we have full context
			this.definitionTarget = node;
			return;
		}

		// Otherwise, visit children for other targets
		visitChildren( node );
	}

	/**
	 * Visit fully qualified names (class references in type hints, etc.).
	 * This enables navigating from type hints to class definitions.
	 */
	@Override
	public void visit( BoxFQN node ) {
		if ( !BLASTTools.containsPosition( node, line, column ) ) {
			visitChildren( node );
			return;
		}

		// A fully qualified name - could be a class reference in various contexts
		// (e.g., type hints, extends/implements, etc.)
		// Only set if no more specific target found
		if ( this.definitionTarget == null ) {
			this.definitionTarget = node;
		}
	}

	/**
	 * Visit annotations (extends, implements) for go-to-definition on class/interface names.
	 * This enables navigating from `extends="ClassName"` to the class definition.
	 */
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
				this.definitionTarget = node;
				return;
			}
		}

		// Visit children for other annotations
		visitChildren( node );
	}

	/**
	 * Visit string literals for class names in annotations.
	 * This is needed when the cursor is inside the string value of extends/implements.
	 */
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
				this.definitionTarget = annotation;
				return;
			}
		}

		// For other string literals, don't set as target
		visitChildren( node );
	}

	/**
	 * Visit return type nodes for go-to-definition on class types.
	 * This enables navigating from `public User function ...` to the User class definition.
	 */
	@Override
	public void visit( BoxReturnType node ) {
		if ( !BLASTTools.containsPosition( node, line, column ) ) {
			visitChildren( node );
			return;
		}

		// Only handle custom class types (not built-in types like string, numeric, etc.)
		String fqn = node.getFqn();
		if ( fqn != null && !fqn.isEmpty() ) {
			this.definitionTarget = node;
		}
	}

	/**
	 * Visit argument declarations for go-to-definition on parameter types.
	 * This enables navigating from `required User user` to the User class definition.
	 */
	@Override
	public void visit( BoxArgumentDeclaration node ) {
		if ( !BLASTTools.containsPosition( node, line, column ) ) {
			visitChildren( node );
			return;
		}

		// Check if cursor is on the type portion of the argument
		// The type is stored as a String, so we need to check position
		String type = node.getType();
		if ( type != null && !type.isEmpty() && !isBuiltInType( type ) ) {
			// Set as target - we'll extract the type string later
			this.definitionTarget = node;
			return;
		}

		visitChildren( node );
	}

	/**
	 * Check if the type is a built-in BoxLang type.
	 */
	private boolean isBuiltInType( String type ) {
		if ( type == null ) {
			return false;
		}
		String lowerType = type.toLowerCase();
		return lowerType.equals( "any" ) || lowerType.equals( "string" ) ||
		    lowerType.equals( "numeric" ) || lowerType.equals( "boolean" ) ||
		    lowerType.equals( "array" ) || lowerType.equals( "struct" ) ||
		    lowerType.equals( "query" ) || lowerType.equals( "void" ) ||
		    lowerType.equals( "function" ) || lowerType.equals( "date" ) ||
		    lowerType.equals( "xml" ) || lowerType.equals( "binary" ) ||
		    lowerType.equals( "uuid" ) || lowerType.equals( "guid" );
	}

	private void visitChildren( BoxNode node ) {
		for ( BoxNode child : node.getChildren() ) {
			child.accept( this );
		}
	}
}
