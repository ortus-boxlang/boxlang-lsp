package ortus.boxlang.lsp.workspace.visitors;

import java.util.HashMap;
import java.util.Map;

import ortus.boxlang.compiler.ast.BoxClass;
import ortus.boxlang.compiler.ast.BoxInterface;
import ortus.boxlang.compiler.ast.BoxNode;
import ortus.boxlang.compiler.ast.BoxScript;
import ortus.boxlang.compiler.ast.BoxTemplate;
import ortus.boxlang.compiler.ast.expression.BoxAssignment;
import ortus.boxlang.compiler.ast.expression.BoxFQN;
import ortus.boxlang.compiler.ast.expression.BoxIdentifier;
import ortus.boxlang.compiler.ast.expression.BoxNew;
import ortus.boxlang.compiler.ast.statement.BoxFunctionDeclaration;
import ortus.boxlang.compiler.ast.visitor.VoidBoxVisitor;

/**
 * Visitor that collects variable type information from assignments.
 * Specifically tracks variables assigned via `new ClassName()` expressions.
 */
public class VariableTypeCollectorVisitor extends VoidBoxVisitor {

	// Maps variable names (lowercase) to their inferred class types
	private final Map<String, String> variableTypes = new HashMap<>();

	/**
	 * Get the inferred type for a variable.
	 *
	 * @param variableName The variable name (case-insensitive)
	 *
	 * @return The class name if known, null otherwise
	 */
	public String getVariableType( String variableName ) {
		return variableTypes.get( variableName.toLowerCase() );
	}

	/**
	 * Get all collected variable types.
	 */
	public Map<String, String> getVariableTypes() {
		return variableTypes;
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

	// ============ Target node types ============

	@Override
	public void visit( BoxAssignment node ) {
		// Check if this is assigning a new expression to a variable
		BoxNode	left	= node.getLeft();
		BoxNode	right	= node.getRight();

		if ( left instanceof BoxIdentifier identifier && right instanceof BoxNew newExpr ) {
			// Extract the class name from the new expression
			String className = extractClassNameFromNew( newExpr );
			if ( className != null ) {
				variableTypes.put( identifier.getName().toLowerCase(), className );
			}
		}

		visitChildren( node );
	}

	/**
	 * Extract the class name from a BoxNew expression.
	 */
	private String extractClassNameFromNew( BoxNew newExpr ) {
		// The expression in BoxNew should be a BoxFQN or similar
		BoxNode expression = newExpr.getExpression();

		if ( expression instanceof BoxFQN fqn ) {
			String	fullPath		= fqn.getValue();
			// Get just the class name (last part after any dots or colons)
			int		lastDot			= fullPath.lastIndexOf( '.' );
			int		lastColon		= fullPath.lastIndexOf( ':' );
			int		lastSeparator	= Math.max( lastDot, lastColon );

			if ( lastSeparator >= 0 && lastSeparator < fullPath.length() - 1 ) {
				return fullPath.substring( lastSeparator + 1 );
			}
			return fullPath;
		}

		// Try to get the source text as a fallback
		if ( expression != null ) {
			String sourceText = expression.getSourceText();
			if ( sourceText != null ) {
				// Clean up and extract class name
				sourceText = sourceText.trim();
				int	lastDot			= sourceText.lastIndexOf( '.' );
				int	lastColon		= sourceText.lastIndexOf( ':' );
				int	lastSeparator	= Math.max( lastDot, lastColon );

				if ( lastSeparator >= 0 && lastSeparator < sourceText.length() - 1 ) {
					return sourceText.substring( lastSeparator + 1 );
				}
				return sourceText;
			}
		}

		return null;
	}

	private void visitChildren( BoxNode node ) {
		for ( BoxNode child : node.getChildren() ) {
			child.accept( this );
		}
	}
}
