package ortus.boxlang.lsp.workspace.visitors;

import org.eclipse.lsp4j.Position;

import ortus.boxlang.compiler.ast.BoxClass;
import ortus.boxlang.compiler.ast.BoxInterface;
import ortus.boxlang.compiler.ast.BoxNode;
import ortus.boxlang.compiler.ast.BoxScript;
import ortus.boxlang.compiler.ast.BoxTemplate;
import ortus.boxlang.compiler.ast.expression.BoxArgument;
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
import ortus.boxlang.compiler.ast.statement.BoxImport;
import ortus.boxlang.compiler.ast.statement.BoxProperty;
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
		// Always visit children first to find more specific targets (like imports, annotations)
		// Note: In BoxLang's AST, imports may be children of BoxClass
		visitChildren( node );

		// If cursor is on the class declaration line and no more specific target found,
		// set the class as the target (enables "Go to Implementation" on class name)
		var	nodePos		= node.getPosition();
		int	classLine	= nodePos.getStart().getLine();

		if ( line == classLine && this.definitionTarget == null ) {
			this.definitionTarget = node;
		}
	}

	@Override
	public void visit( BoxInterface node ) {
		// Always visit children first to find more specific targets
		visitChildren( node );

		// If cursor is on the interface declaration line and no more specific target found,
		// set the interface as the target (enables "Go to Implementation" on interface name)
		var	nodePos			= node.getPosition();
		int	interfaceLine	= nodePos.getStart().getLine();

		if ( line == interfaceLine && this.definitionTarget == null ) {
			this.definitionTarget = node;
		}
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
		if ( !BLASTTools.containsPosition( node, line, column ) ) {
			return;
		}

		// Visit children first to find more specific targets like parameters or return types
		visitChildren( node );

		// If cursor is on the function declaration line and no more specific target found,
		// set the function as the target (enables "find references" when on a declaration)
		var	nodePos		= node.getPosition();
		int	funcLine	= nodePos.getStart().getLine();

		if ( line == funcLine && this.definitionTarget == null ) {
			this.definitionTarget = node;
		}
	}

	/**
	 * Visit property declarations for go-to-definition on property names.
	 * This enables "find references" when cursor is on a property declaration.
	 */
	@Override
	public void visit( BoxProperty node ) {
		if ( !BLASTTools.containsPosition( node, line, column ) ) {
			visitChildren( node );
			return;
		}

		this.definitionTarget = node;
	}

	// ============ Target node types - check position and set target ============

	/**
	 * Visit import statements for go-to-definition on imported classes.
	 * This enables navigating from `import ClassName;` or `import ClassName as Alias;` to the class definition.
	 */
	@Override
	public void visit( BoxImport node ) {
		if ( !BLASTTools.containsPosition( node, line, column ) ) {
			visitChildren( node );
			return;
		}

		// Set the BoxImport as target - handles both class name and alias
		this.definitionTarget = node;
	}

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

		// Skip if this identifier is the class/interface name (parent is BoxClass or BoxInterface)
		// We want to let the class/interface itself be the target in that case
		BoxNode parent = node.getParent();
		if ( parent instanceof BoxClass || parent instanceof BoxInterface ) {
			return;
		}

		// Only set as definition target if we haven't found a more specific node
		// (function invocations take priority)
		if ( this.definitionTarget == null ) {
			this.definitionTarget = node;
		}
	}

	/**
	 * Visit dot access expressions (property access) for go-to-definition on properties.
	 * This enables navigating from `variables.propertyName` or `this.propertyName` to the property declaration.
	 */
	@Override
	public void visit( BoxDotAccess node ) {
		if ( !BLASTTools.containsPosition( node, line, column ) ) {
			visitChildren( node );
			return;
		}

		// Check if this is a scoped property access (variables.x, this.x)
		BoxNode context = node.getContext();
		if ( context instanceof BoxScope scope ) {
			String scopeName = scope.getName().toLowerCase();
			if ( scopeName.equals( "variables" ) || scopeName.equals( "this" ) ) {
				// Check if cursor is on the property name (the access part)
				if ( node.getAccess() != null && BLASTTools.containsPosition( node.getAccess(), line, column ) ) {
					// Set the BoxDotAccess as target so we have full context
					this.definitionTarget = node;
					return;
				}
			}
		}

		// Also check for BoxIdentifier context (e.g., variables as identifier)
		if ( context instanceof BoxIdentifier scopeId ) {
			String scopeName = scopeId.getName().toLowerCase();
			if ( scopeName.equals( "variables" ) || scopeName.equals( "this" ) ) {
				// Check if cursor is on the property name (the access part)
				if ( node.getAccess() != null && BLASTTools.containsPosition( node.getAccess(), line, column ) ) {
					// Set the BoxDotAccess as target so we have full context
					this.definitionTarget = node;
					return;
				}
			}
		}

		// Otherwise, visit children for other targets
		visitChildren( node );
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

		// Skip if this FQN is the class/interface name
		// Check if parent is BoxAnnotation and grandparent is BoxClass/BoxInterface
		// This indicates the FQN is the class/interface name itself
		BoxNode parent = node.getParent();
		if ( parent instanceof BoxAnnotation annotation ) {
			BoxNode grandparent = annotation.getParent();
			if ( grandparent instanceof BoxClass || grandparent instanceof BoxInterface ) {
				// Check if we're on the declaration line - skip to let BoxClass/BoxInterface be target
				var declPos = grandparent.getPosition();
				if ( declPos != null && line == declPos.getStart().getLine() ) {
					return;
				}
			}
		}

		// Also skip if immediate parent is BoxClass or BoxInterface (direct name reference)
		if ( parent instanceof BoxClass || parent instanceof BoxInterface ) {
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
			// Resolve from anywhere within the annotation (key, equals, value, quotes)
			// so Cmd/Ctrl+Click on `extends`/`implements` consistently navigates.
			this.definitionTarget = node;
			return;
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

		// Handle class argument in createObject("component", "path.to.Class")
		if ( isCreateObjectClassArgument( node ) ) {
			this.definitionTarget = node;
			return;
		}

		// For other string literals, don't set as target
		visitChildren( node );
	}

	private boolean isCreateObjectClassArgument( BoxStringLiteral literal ) {
		BoxFunctionInvocation invocation = literal.getFirstAncestorOfType( BoxFunctionInvocation.class );
		if ( invocation == null || invocation.getName() == null || !invocation.getName().equalsIgnoreCase( "createObject" ) ) {
			return false;
		}
		int index = 0;
		for ( BoxArgument argument : invocation.getArguments() ) {
			if ( argument == null || argument.getValue() != literal ) {
				index++;
				continue;
			}
			String argumentName = extractArgumentName( argument );
			if ( argumentName != null ) {
				return argumentName.equalsIgnoreCase( "class" )
				    || argumentName.equalsIgnoreCase( "classname" )
				    || argumentName.equalsIgnoreCase( "path" )
				    || argumentName.equalsIgnoreCase( "component" );
			}
			return index == 1;
		}
		return false;
	}

	private String extractArgumentName( BoxArgument argument ) {
		if ( argument == null || argument.getName() == null ) {
			return null;
		}
		BoxNode nameNode = argument.getName();
		if ( nameNode instanceof BoxIdentifier identifier ) {
			return identifier.getName();
		}
		if ( nameNode instanceof BoxStringLiteral literal ) {
			return literal.getValue();
		}
		return nameNode.getSourceText();
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

		// Always set as target when cursor is within the argument declaration
		// This enables both type hint navigation and "find references" for the parameter
		this.definitionTarget = node;
	}

	private void visitChildren( BoxNode node ) {
		for ( BoxNode child : node.getChildren() ) {
			child.accept( this );
		}
	}
}
