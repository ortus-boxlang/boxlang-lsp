package ortus.boxlang.lsp.workspace.visitors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ortus.boxlang.compiler.ast.BoxNode;
import ortus.boxlang.compiler.ast.expression.BoxAssignment;
import ortus.boxlang.compiler.ast.expression.BoxAssignmentModifier;
import ortus.boxlang.compiler.ast.expression.BoxDotAccess;
import ortus.boxlang.compiler.ast.expression.BoxIdentifier;
import ortus.boxlang.compiler.ast.statement.BoxArgumentDeclaration;
import ortus.boxlang.compiler.ast.statement.BoxFunctionDeclaration;
import ortus.boxlang.compiler.ast.statement.BoxProperty;

/**
 * Resolves variable usages to their declaration sites.
 * Implements proper scoping rules for BoxLang:
 * - Function parameters are declared in the function signature
 * - Local variables are declared at first assignment with var keyword
 * - Handles shadowed variables correctly (inner scope shadows outer scope)
 *
 * Uses getDescendantsOfType for reliable AST traversal instead of visitor pattern.
 */
public class VariableDefinitionResolver {

	/**
	 * Represents a variable declaration site.
	 */
	public record VariableDeclaration(
	    String name,
	    BoxNode declarationNode,
	    DeclarationType type,
	    int declarationLine
	) {}

	/**
	 * Types of variable declarations.
	 */
	public enum DeclarationType {
		PARAMETER,
		LOCAL_VAR,
		PROPERTY,
		SCOPED_VAR
	}

	// The target identifier we're looking for
	private final BoxIdentifier	targetIdentifier;
	private final int			targetLine;
	private final int			targetColumn;

	// Resolved declaration (if found)
	private VariableDeclaration	resolvedDeclaration	= null;

	// Map of function -> variable declarations within that function
	private final Map<BoxFunctionDeclaration, List<VariableDeclaration>>	functionDeclarations;

	// Class-level declarations (properties)
	private final List<VariableDeclaration>						classDeclarations;

	public VariableDefinitionResolver( BoxIdentifier targetIdentifier ) {
		this.targetIdentifier		= targetIdentifier;
		this.targetLine				= targetIdentifier.getPosition().getStart().getLine();
		this.targetColumn			= targetIdentifier.getPosition().getStart().getColumn();
		this.functionDeclarations	= new HashMap<>();
		this.classDeclarations		= new ArrayList<>();
	}

	/**
	 * Get the resolved variable declaration, or null if not found.
	 */
	public VariableDeclaration getResolvedDeclaration() {
		return resolvedDeclaration;
	}

	/**
	 * Resolve the variable by collecting declarations and finding the match.
	 */
	public void resolve( BoxNode root ) {
		// Collect all declarations using getDescendantsOfType
		collectDeclarations( root );

		// Now resolve the target identifier
		resolveTarget();
	}

	/**
	 * Collect all variable declarations from the AST.
	 */
	private void collectDeclarations( BoxNode root ) {
		// Collect all function declarations first
		List<BoxFunctionDeclaration> functions = root.getDescendantsOfType( BoxFunctionDeclaration.class );
		for ( BoxFunctionDeclaration func : functions ) {
			functionDeclarations.computeIfAbsent( func, k -> new ArrayList<>() );
		}

		// Collect properties (class-level)
		List<BoxProperty> properties = root.getDescendantsOfType( BoxProperty.class );
		for ( BoxProperty property : properties ) {
			collectProperty( property );
		}

		// Collect function parameters
		List<BoxArgumentDeclaration> params = root.getDescendantsOfType( BoxArgumentDeclaration.class );
		for ( BoxArgumentDeclaration param : params ) {
			collectParameter( param );
		}

		// Collect variable assignments (var keyword)
		List<BoxAssignment> assignments = root.getDescendantsOfType( BoxAssignment.class );
		for ( BoxAssignment assignment : assignments ) {
			collectAssignment( assignment );
		}
	}

	/**
	 * Collect a property declaration.
	 */
	private void collectProperty( BoxProperty node ) {
		String name = null;

		// Extract property name from annotations
		for ( var annotation : node.getAnnotations() ) {
			String key = annotation.getKey().getValue().toLowerCase();
			if ( key.equals( "name" ) && annotation.getValue() != null ) {
				name = annotation.getValue().getSourceText().replace( "\"", "" ).replace( "'", "" );
				break;
			}
		}

		if ( name != null ) {
			int line = node.getPosition() != null ? node.getPosition().getStart().getLine() : 0;

			VariableDeclaration decl = new VariableDeclaration(
			    name,
			    node,
			    DeclarationType.PROPERTY,
			    line
			);

			classDeclarations.add( decl );
		}
	}

	/**
	 * Collect a function parameter declaration.
	 */
	private void collectParameter( BoxArgumentDeclaration node ) {
		BoxFunctionDeclaration containingFunc = node.getFirstAncestorOfType( BoxFunctionDeclaration.class );
		if ( containingFunc != null ) {
			String	name	= node.getName();
			int		line	= node.getPosition() != null ? node.getPosition().getStart().getLine() : 0;

			VariableDeclaration decl = new VariableDeclaration(
			    name,
			    node,
			    DeclarationType.PARAMETER,
			    line
			);

			functionDeclarations.computeIfAbsent( containingFunc, k -> new ArrayList<>() ).add( decl );
		}
	}

	/**
	 * Collect a variable assignment (var keyword or scoped assignment).
	 */
	private void collectAssignment( BoxAssignment node ) {
		// Check if this is a var-scoped assignment
		boolean isVarScoped = node.getModifiers().stream()
		    .anyMatch( m -> m == BoxAssignmentModifier.VAR );

		if ( isVarScoped && node.getLeft() instanceof BoxIdentifier identifier ) {
			BoxFunctionDeclaration containingFunc = node.getFirstAncestorOfType( BoxFunctionDeclaration.class );
			if ( containingFunc != null ) {
				String	name	= identifier.getName();
				int		line	= node.getPosition() != null ? node.getPosition().getStart().getLine() : 0;

				VariableDeclaration decl = new VariableDeclaration(
				    name,
				    node,
				    DeclarationType.LOCAL_VAR,
				    line
				);

				functionDeclarations.computeIfAbsent( containingFunc, k -> new ArrayList<>() ).add( decl );
			}
		}

		// Handle scoped assignments like variables.foo or this.bar
		if ( node.getLeft() instanceof BoxDotAccess dotAccess ) {
			if ( dotAccess.getContext() instanceof BoxIdentifier scopeId ) {
				String scopeName = scopeId.getName().toLowerCase();
				if ( scopeName.equals( "variables" ) || scopeName.equals( "this" ) ) {
					if ( dotAccess.getAccess() instanceof BoxIdentifier propId ) {
						String	name	= propId.getName();
						int		line	= node.getPosition() != null ? node.getPosition().getStart().getLine() : 0;

						VariableDeclaration decl = new VariableDeclaration(
						    name,
						    node,
						    DeclarationType.SCOPED_VAR,
						    line
						);

						classDeclarations.add( decl );
					}
				}
			}
		}
	}

	/**
	 * Resolve the target identifier to its declaration.
	 */
	private void resolveTarget() {
		String targetName = targetIdentifier.getName().toLowerCase();

		// Find the containing function for the target identifier
		BoxFunctionDeclaration containingFunction = targetIdentifier.getFirstAncestorOfType( BoxFunctionDeclaration.class );

		if ( containingFunction != null ) {
			// Look in the function's declarations
			List<VariableDeclaration> declarations = functionDeclarations.get( containingFunction );
			if ( declarations != null ) {
				// Find the declaration that is:
				// 1. Before the target usage
				// 2. Closest to the target usage (handles shadowing)
				VariableDeclaration bestMatch = null;

				for ( VariableDeclaration decl : declarations ) {
					if ( decl.name().equalsIgnoreCase( targetName ) ) {
						// Check if this declaration is before the target usage
						if ( decl.declarationLine() <= targetLine ) {
							// For same-line declarations, check column position
							if ( decl.declarationLine() == targetLine ) {
								// Get the declaration column
								int declCol = decl.declarationNode().getPosition().getStart().getColumn();
								if ( declCol > targetColumn ) {
									continue; // Declaration is after usage
								}
							}

							// Check if this is a better match than what we have
							if ( bestMatch == null ) {
								bestMatch = decl;
							} else {
								// Prefer the declaration closest to (but before) the usage
								// This handles shadowing - the later declaration shadows the earlier one
								if ( decl.declarationLine() > bestMatch.declarationLine() ) {
									bestMatch = decl;
								}
							}
						}
					}
				}

				if ( bestMatch != null ) {
					resolvedDeclaration = bestMatch;
					return;
				}
			}
		}

		// If not found in function scope, check class-level declarations
		for ( VariableDeclaration decl : classDeclarations ) {
			if ( decl.name().equalsIgnoreCase( targetName ) ) {
				resolvedDeclaration = decl;
				return;
			}
		}
	}
}
