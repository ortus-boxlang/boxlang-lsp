package ortus.boxlang.lsp.workspace.visitors;

import java.util.HashMap;
import java.util.Map;

import ortus.boxlang.compiler.ast.BoxClass;
import ortus.boxlang.compiler.ast.BoxNode;
import ortus.boxlang.compiler.ast.BoxScript;
import ortus.boxlang.compiler.ast.expression.BoxArrayLiteral;
import ortus.boxlang.compiler.ast.expression.BoxAssignment;
import ortus.boxlang.compiler.ast.expression.BoxAssignmentModifier;
import ortus.boxlang.compiler.ast.expression.BoxBooleanLiteral;
import ortus.boxlang.compiler.ast.expression.BoxDecimalLiteral;
import ortus.boxlang.compiler.ast.expression.BoxDotAccess;
import ortus.boxlang.compiler.ast.expression.BoxIdentifier;
import ortus.boxlang.compiler.ast.expression.BoxIntegerLiteral;
import ortus.boxlang.compiler.ast.expression.BoxNew;
import ortus.boxlang.compiler.ast.expression.BoxStringLiteral;
import ortus.boxlang.compiler.ast.expression.BoxStructLiteral;
import ortus.boxlang.compiler.ast.statement.BoxArgumentDeclaration;
import ortus.boxlang.compiler.ast.statement.BoxFunctionDeclaration;
import ortus.boxlang.compiler.ast.statement.BoxProperty;
import ortus.boxlang.compiler.ast.visitor.VoidBoxVisitor;

/**
 * Visitor that collects variable scope and type information for hover purposes.
 * Tracks:
 * - Local variables (var keyword)
 * - Function parameters (arguments scope)
 * - Properties (this/variables scope)
 * - Inferred types from literals and new expressions
 */
public class VariableScopeCollectorVisitor extends VoidBoxVisitor {

	/**
	 * Represents collected information about a variable.
	 */
	public record VariableInfo(
	    String name,
	    VariableScope scope,
	    String typeHint,
	    String inferredType,
	    int declarationLine,
	    boolean isRequired,
	    String defaultValue,
	    BoxNode declarationNode
	) {}

	/**
	 * Variable scope types in BoxLang.
	 */
	public enum VariableScope {
		LOCAL( "local" ),
		ARGUMENTS( "arguments" ),
		VARIABLES( "variables" ),
		THIS( "this" ),
		PROPERTY( "property" ),
		UNKNOWN( "unknown" );

		private final String displayName;

		VariableScope( String displayName ) {
			this.displayName = displayName;
		}

		public String getDisplayName() {
			return displayName;
		}
	}

	// Maps property names (lowercase) to their info (class-level scope)
	private final Map<String, VariableInfo>								properties		= new HashMap<>();
	// The current function being visited
	private BoxFunctionDeclaration										currentFunction	= null;
	// Maps function to its variable info (includes both parameters and local variables)
	private final Map<BoxFunctionDeclaration, Map<String, VariableInfo>>	functionVariables;

	public VariableScopeCollectorVisitor() {
		this.functionVariables = new HashMap<>();
	}

	/**
	 * Get variable info by name, searching in order: function-local (params + locals), properties.
	 * Variables are properly scoped to their containing function - parameters from other functions
	 * are not visible.
	 *
	 * @param variableName The variable name (case-insensitive)
	 * @param containingFunction The function context (may be null for class-level)
	 *
	 * @return The VariableInfo if found, null otherwise
	 */
	public VariableInfo getVariableInfo( String variableName, BoxFunctionDeclaration containingFunction ) {
		String lowerName = variableName.toLowerCase();

		// First check variables in the function context (includes both parameters and local variables)
		if ( containingFunction != null ) {
			Map<String, VariableInfo> funcVars = functionVariables.get( containingFunction );
			if ( funcVars != null && funcVars.containsKey( lowerName ) ) {
				return funcVars.get( lowerName );
			}
		}

		// Then check properties (class-level scope, visible from all functions)
		if ( properties.containsKey( lowerName ) ) {
			return properties.get( lowerName );
		}

		return null;
	}

	/**
	 * Check if a name represents a built-in scope keyword.
	 */
	public boolean isScopeKeyword( String name ) {
		if ( name == null )
			return false;
		String lower = name.toLowerCase();
		return lower.equals( "variables" ) ||
		    lower.equals( "local" ) ||
		    lower.equals( "this" ) ||
		    lower.equals( "arguments" ) ||
		    lower.equals( "request" ) ||
		    lower.equals( "session" ) ||
		    lower.equals( "application" ) ||
		    lower.equals( "server" ) ||
		    lower.equals( "cgi" ) ||
		    lower.equals( "form" ) ||
		    lower.equals( "url" ) ||
		    lower.equals( "cookie" );
	}

	/**
	 * Get scope information for a scope keyword.
	 */
	public VariableInfo getScopeKeywordInfo( String name ) {
		if ( name == null )
			return null;
		String lower = name.toLowerCase();
		return switch ( lower ) {
			case "variables" -> new VariableInfo( "variables", VariableScope.VARIABLES,
			    "struct", "struct", 0, false, null, null );
			case "local" -> new VariableInfo( "local", VariableScope.LOCAL,
			    "struct", "struct", 0, false, null, null );
			case "this" -> new VariableInfo( "this", VariableScope.THIS,
			    "component", "component", 0, false, null, null );
			case "arguments" -> new VariableInfo( "arguments", VariableScope.ARGUMENTS,
			    "struct", "struct", 0, false, null, null );
			case "request" -> new VariableInfo( "request", VariableScope.UNKNOWN,
			    "struct", "struct", 0, false, null, null );
			case "session" -> new VariableInfo( "session", VariableScope.UNKNOWN,
			    "struct", "struct", 0, false, null, null );
			case "application" -> new VariableInfo( "application", VariableScope.UNKNOWN,
			    "struct", "struct", 0, false, null, null );
			case "server" -> new VariableInfo( "server", VariableScope.UNKNOWN,
			    "struct", "struct", 0, false, null, null );
			case "cgi" -> new VariableInfo( "cgi", VariableScope.UNKNOWN,
			    "struct", "struct", 0, false, null, null );
			case "form" -> new VariableInfo( "form", VariableScope.UNKNOWN,
			    "struct", "struct", 0, false, null, null );
			case "url" -> new VariableInfo( "url", VariableScope.UNKNOWN,
			    "struct", "struct", 0, false, null, null );
			case "cookie" -> new VariableInfo( "cookie", VariableScope.UNKNOWN,
			    "struct", "struct", 0, false, null, null );
			default -> null;
		};
	}

	@Override
	public void visit( BoxClass node ) {
		// Traverse into the class body to find functions and properties
		visitChildren( node );
	}

	@Override
	public void visit( BoxScript node ) {
		// Traverse into script body to find functions
		visitChildren( node );
	}

	@Override
	public void visit( BoxFunctionDeclaration node ) {
		BoxFunctionDeclaration previousFunction = currentFunction;
		currentFunction = node;
		functionVariables.computeIfAbsent( node, k -> new HashMap<>() );

		// Visit children (including parameter declarations)
		visitChildren( node );

		currentFunction = previousFunction;
	}

	@Override
	public void visit( BoxArgumentDeclaration node ) {
		String		name			= node.getName();
		String		typeHint		= node.getType() != null ? node.getType().toString() : null;

		// Get required status directly from the node
		boolean		isRequired		= node.getRequired();

		String		defaultValue	= node.getValue() != null ? node.getValue().getSourceText() : null;

		int			line			= node.getPosition() != null ? node.getPosition().getStart().getLine() : 0;

		VariableInfo info = new VariableInfo(
		    name,
		    VariableScope.ARGUMENTS,
		    typeHint,
		    typeHint, // For parameters, type hint is also the inferred type
		    line,
		    isRequired,
		    defaultValue,
		    node
		);

		// Add to the function's variable map - parameters are scoped to their function only
		if ( currentFunction != null ) {
			functionVariables.get( currentFunction ).put( name.toLowerCase(), info );
		}
	}

	@Override
	public void visit( BoxProperty node ) {
		String	name		= null;
		String	typeHint	= null;

		// Extract property name and type from annotations
		for ( var annotation : node.getAnnotations() ) {
			String key = annotation.getKey().getValue().toLowerCase();
			if ( key.equals( "name" ) && annotation.getValue() != null ) {
				name = annotation.getValue().getSourceText().replace( "\"", "" ).replace( "'", "" );
			} else if ( key.equals( "type" ) && annotation.getValue() != null ) {
				typeHint = annotation.getValue().getSourceText().replace( "\"", "" ).replace( "'", "" );
			}
		}

		if ( name != null ) {
			int line = node.getPosition() != null ? node.getPosition().getStart().getLine() : 0;

			VariableInfo info = new VariableInfo(
			    name,
			    VariableScope.PROPERTY,
			    typeHint,
			    typeHint,
			    line,
			    false,
			    null,
			    node
			);

			properties.put( name.toLowerCase(), info );
		}
	}

	@Override
	public void visit( BoxAssignment node ) {
		// Check if this is a var-scoped assignment (explicit local)
		boolean isVarScoped = node.getModifiers().stream()
		    .anyMatch( m -> m == BoxAssignmentModifier.VAR );

		if ( node.getLeft() instanceof BoxIdentifier identifier ) {
			String	name			= identifier.getName();
			String	lowerName		= name.toLowerCase();
			String	inferredType	= inferTypeFromExpression( node.getRight() );
			int		line			= node.getPosition() != null ? node.getPosition().getStart().getLine() : 0;

			if ( isVarScoped ) {
				// Explicit var declaration - always local scope
				VariableInfo info = new VariableInfo(
				    name,
				    VariableScope.LOCAL,
				    null, // No explicit type hint for var declarations
				    inferredType,
				    line,
				    false,
				    null,
				    node
				);

				if ( currentFunction != null ) {
					functionVariables.get( currentFunction ).put( lowerName, info );
				}
			} else if ( currentFunction != null ) {
				// Implicit assignment (no var keyword)
				// In BoxLang, this becomes local UNLESS there's a property or higher scope variable
				// Check if this variable already exists in properties
				if ( !properties.containsKey( lowerName ) ) {
					// Not a property, so it's an implicit local variable
					// Only add if not already tracked in this function
					Map<String, VariableInfo> funcVars = functionVariables.get( currentFunction );
					if ( !funcVars.containsKey( lowerName ) ) {
						VariableInfo info = new VariableInfo(
						    name,
						    VariableScope.LOCAL,
						    null,
						    inferredType,
						    line,
						    false,
						    null,
						    node
						);
						funcVars.put( lowerName, info );
					}
				}
			}
		}

		// Handle scoped assignments like variables.foo or this.bar
		if ( node.getLeft() instanceof BoxDotAccess dotAccess ) {
			if ( dotAccess.getContext() instanceof BoxIdentifier scopeId ) {
				String scopeName = scopeId.getName().toLowerCase();
				if ( scopeName.equals( "variables" ) || scopeName.equals( "this" ) ) {
					if ( dotAccess.getAccess() instanceof BoxIdentifier propId ) {
						String	name			= propId.getName();
						String	inferredType	= inferTypeFromExpression( node.getRight() );
						int		line			= node.getPosition() != null ? node.getPosition().getStart().getLine() : 0;

						VariableScope scope = scopeName.equals( "this" ) ? VariableScope.THIS : VariableScope.VARIABLES;

						VariableInfo info = new VariableInfo(
						    name,
						    scope,
						    null,
						    inferredType,
						    line,
						    false,
						    null,
						    node
						);

						properties.put( name.toLowerCase(), info );
					}
				}
			}
		}

		visitChildren( node );
	}

	/**
	 * Infer type from an expression based on literals and new expressions.
	 */
	private String inferTypeFromExpression( BoxNode expression ) {
		if ( expression == null )
			return null;

		if ( expression instanceof BoxStringLiteral ) {
			return "string";
		} else if ( expression instanceof BoxIntegerLiteral ) {
			return "numeric";
		} else if ( expression instanceof BoxDecimalLiteral ) {
			return "numeric";
		} else if ( expression instanceof BoxBooleanLiteral ) {
			return "boolean";
		} else if ( expression instanceof BoxArrayLiteral ) {
			return "array";
		} else if ( expression instanceof BoxStructLiteral ) {
			return "struct";
		} else if ( expression instanceof BoxNew newExpr ) {
			// Try to extract the class name
			BoxNode expr = newExpr.getExpression();
			if ( expr != null ) {
				String sourceText = expr.getSourceText();
				if ( sourceText != null ) {
					// Get just the class name (last part)
					int lastDot = sourceText.lastIndexOf( '.' );
					int lastColon = sourceText.lastIndexOf( ':' );
					int lastSeparator = Math.max( lastDot, lastColon );
					if ( lastSeparator >= 0 && lastSeparator < sourceText.length() - 1 ) {
						return sourceText.substring( lastSeparator + 1 );
					}
					return sourceText;
				}
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
