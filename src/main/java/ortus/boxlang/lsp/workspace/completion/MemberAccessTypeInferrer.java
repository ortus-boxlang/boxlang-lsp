package ortus.boxlang.lsp.workspace.completion;

import java.util.List;
import java.util.Optional;

import ortus.boxlang.compiler.ast.BoxClass;
import ortus.boxlang.compiler.ast.BoxNode;
import ortus.boxlang.compiler.ast.statement.BoxArgumentDeclaration;
import ortus.boxlang.compiler.ast.statement.BoxFunctionDeclaration;
import ortus.boxlang.lsp.workspace.BLASTTools;
import ortus.boxlang.lsp.workspace.FileParseResult;
import ortus.boxlang.lsp.workspace.completion.TypeInferenceResult.InferenceConfidence;
import ortus.boxlang.lsp.workspace.index.IndexedClass;
import ortus.boxlang.lsp.workspace.index.IndexedMethod;
import ortus.boxlang.lsp.workspace.index.ProjectIndex;
import ortus.boxlang.lsp.workspace.visitors.VariableTypeCollectorVisitor;

/**
 * Infers the type of an expression for member access completion.
 * Handles variables, new expressions, method calls, and chained access.
 *
 * Simplified first iteration focuses on:
 * - New expressions: var x = new Class()
 * - Explicit type hints from variable declarations
 * - Parameter type hints
 * - Basic method return types
 */
public class MemberAccessTypeInferrer {

	private final FileParseResult	fileParseResult;
	private final ProjectIndex		index;

	public MemberAccessTypeInferrer( FileParseResult fileParseResult, ProjectIndex index ) {
		this.fileParseResult	= fileParseResult;
		this.index				= index;
	}

	/**
	 * Infer the type of an expression.
	 *
	 * @param receiverText The text before the dot (e.g., "myVar" or "obj.method()")
	 * @param cursorLine   The line number where completion is requested (0-based)
	 * @param cursorColumn The column where completion is requested (0-based)
	 *
	 * @return TypeInferenceResult with the inferred type information
	 */
	public TypeInferenceResult inferType( String receiverText, int cursorLine, int cursorColumn ) {
		if ( receiverText == null || receiverText.isEmpty() ) {
			return TypeInferenceResult.unknown();
		}

		// Trim whitespace
		receiverText = receiverText.trim();

		// Handle "this" keyword
		if ( "this".equalsIgnoreCase( receiverText ) || "variables".equalsIgnoreCase( receiverText ) ) {
			String containingClass = findContainingClassName( cursorLine );
			if ( containingClass != null ) {
				Optional<IndexedClass> indexed = resolveClass( containingClass );
				return new TypeInferenceResult(
				    containingClass,
				    indexed.map( IndexedClass::fullyQualifiedName ).orElse( null ),
				    InferenceConfidence.HIGH,
				    receiverText.toLowerCase() + " scope"
				);
			}
			return TypeInferenceResult.unknown();
		}

		// Check if it's a chained expression (contains method call or dot access)
		// For now, handle simple method calls like "service.getUser(1)"
		if ( receiverText.contains( "(" ) && receiverText.contains( ")" ) ) {
			return inferChainedExpressionType( receiverText, cursorLine, cursorColumn );
		}

		// Simple identifier - look up variable
		return inferVariableType( receiverText, cursorLine, cursorColumn );
	}

	/**
	 * Infer the type of a simple identifier (variable name).
	 */
	private TypeInferenceResult inferVariableType( String varName, int cursorLine, int cursorColumn ) {
		BoxNode root = fileParseResult.findAstRoot().orElse( null );
		if ( root == null ) {
			return TypeInferenceResult.unknown();
		}

		// Strategy 1: Check for parameter type hints
		TypeInferenceResult paramType = inferFromParameterTypeHint( root, varName, cursorLine );
		if ( paramType.isResolved() ) {
			return paramType;
		}

		// Strategy 2: Use VariableTypeCollectorVisitor to find assignments
		VariableTypeCollectorVisitor visitor = new VariableTypeCollectorVisitor();
		root.accept( visitor );
		String inferredType = visitor.getVariableType( varName );

		if ( inferredType != null ) {
			Optional<IndexedClass> indexed = resolveClass( inferredType );
			return new TypeInferenceResult(
			    inferredType,
			    indexed.map( IndexedClass::fullyQualifiedName ).orElse( null ),
			    InferenceConfidence.HIGH,
			    "new expression assignment"
			);
		}

		// Strategy 3: Check for explicit type declarations (future enhancement)

		return TypeInferenceResult.unknown();
	}

	/**
	 * Infer type from function parameter with type hint.
	 */
	private TypeInferenceResult inferFromParameterTypeHint( BoxNode root, String varName, int cursorLine ) {
		// Find the function containing the cursor
		int line1Based = cursorLine + 1;

		for ( BoxNode child : root.getDescendantsOfType( BoxFunctionDeclaration.class ) ) {
			BoxFunctionDeclaration func = ( BoxFunctionDeclaration ) child;
			if ( !BLASTTools.containsPosition( func, line1Based, 0 ) ) {
				continue;
			}

			// Check function arguments
			List<BoxArgumentDeclaration> args = func.getArgs();
			if ( args != null ) {
				for ( BoxArgumentDeclaration arg : args ) {
					if ( arg.getName().equalsIgnoreCase( varName ) ) {
						String typeHint = arg.getType();
						if ( typeHint != null && !typeHint.isEmpty() ) {
							Optional<IndexedClass> indexed = resolveClass( typeHint );
							return new TypeInferenceResult(
							    typeHint,
							    indexed.map( IndexedClass::fullyQualifiedName ).orElse( null ),
							    InferenceConfidence.HIGH,
							    "parameter type hint"
							);
						}
					}
				}
			}
		}

		return TypeInferenceResult.unknown();
	}

	/**
	 * Infer type from chained expression like "service.getUser(1)".
	 * This is a simplified version that handles basic chaining.
	 */
	private TypeInferenceResult inferChainedExpressionType( String expression, int cursorLine, int cursorColumn ) {
		// Parse the expression to find the last method call
		// For "service.getUser(1)", we need to:
		// 1. Find type of "service"
		// 2. Look up "getUser" method in that class
		// 3. Return the method's return type

		// Simple approach: find the last closing paren and work backwards
		int lastParen = expression.lastIndexOf( ')' );
		if ( lastParen < 0 ) {
			return TypeInferenceResult.unknown();
		}

		// Find the opening paren of this method call
		int	parenDepth	= 1;
		int	openParen	= -1;
		for ( int i = lastParen - 1; i >= 0; i-- ) {
			char c = expression.charAt( i );
			if ( c == ')' ) {
				parenDepth++;
			} else if ( c == '(' ) {
				parenDepth--;
				if ( parenDepth == 0 ) {
					openParen = i;
					break;
				}
			}
		}

		if ( openParen < 0 ) {
			return TypeInferenceResult.unknown();
		}

		// Find the method name (between the last dot and the opening paren)
		String	beforeParen	= expression.substring( 0, openParen );
		int		lastDot		= beforeParen.lastIndexOf( '.' );

		if ( lastDot < 0 ) {
			// No dot before method - might be a standalone function call
			return TypeInferenceResult.unknown();
		}

		String				methodName		= beforeParen.substring( lastDot + 1 ).trim();
		String				receiver		= beforeParen.substring( 0, lastDot ).trim();

		// Recursively infer the receiver's type
		TypeInferenceResult	receiverType	= inferType( receiver, cursorLine, cursorColumn );
		if ( !receiverType.isResolved() ) {
			return TypeInferenceResult.unknown();
		}

		// Look up the method in the receiver's class
		String					className	= receiverType.className();
		Optional<IndexedMethod>	method		= findMethodInClassOrParents( className, methodName );

		if ( method.isPresent() ) {
			String returnType = method.get().returnTypeHint();
			if ( returnType != null && !returnType.isEmpty() && !"void".equalsIgnoreCase( returnType ) ) {
				Optional<IndexedClass> indexed = resolveClass( returnType );
				return new TypeInferenceResult(
				    returnType,
				    indexed.map( IndexedClass::fullyQualifiedName ).orElse( null ),
				    InferenceConfidence.MEDIUM,
				    "method return type"
				);
			}
		}

		return TypeInferenceResult.unknown();
	}

	/**
	 * Find a method in a class or its parent classes.
	 */
	private Optional<IndexedMethod> findMethodInClassOrParents( String className, String methodName ) {
		if ( index == null ) {
			return Optional.empty();
		}

		// Try to find the class first
		Optional<IndexedClass> classOpt = resolveClass( className );
		if ( classOpt.isEmpty() ) {
			return Optional.empty();
		}

		IndexedClass			clazz	= classOpt.get();

		// Look up method in this class
		Optional<IndexedMethod>	method	= index.findMethod( clazz.name(), methodName );
		if ( method.isPresent() ) {
			return method;
		}

		// Check parent classes
		String parentClass = clazz.extendsClass();
		if ( parentClass != null && !parentClass.isEmpty() ) {
			return findMethodInClassOrParents( parentClass, methodName );
		}

		return Optional.empty();
	}

	/**
	 * Resolve a class by name (simple name, FQN, or relative path).
	 * Supports relative class paths like "subpackage.User" from within a package.
	 */
	private Optional<IndexedClass> resolveClass( String className ) {
		if ( index == null || className == null ) {
			return Optional.empty();
		}

		// Use the comprehensive lookup that includes relative path resolution
		return index.findClassWithContext( className, fileParseResult.getURI() );
	}

	/**
	 * Find the name of the class containing the given line.
	 */
	private String findContainingClassName( int cursorLine ) {
		return fileParseResult.findAstRoot()
		    .map( root -> {
			    int line1Based = cursorLine + 1;

			    for ( BoxNode child : root.getDescendantsOfType( BoxClass.class ) ) {
				    BoxClass clazz = ( BoxClass ) child;
				    if ( BLASTTools.containsPosition( clazz, line1Based, 0 ) ) {
					    // Get class name from filename
					    return getClassNameFromFile();
				    }
			    }
			    return null;
		    } )
		    .orElse( null );
	}

	/**
	 * Extract class name from the file URI.
	 */
	private String getClassNameFromFile() {
		String	uriString	= fileParseResult.getURI().toString();
		int		lastSlash	= uriString.lastIndexOf( '/' );
		String	filename	= lastSlash >= 0 ? uriString.substring( lastSlash + 1 ) : uriString;
		int		dotIndex	= filename.lastIndexOf( '.' );
		return dotIndex > 0 ? filename.substring( 0, dotIndex ) : filename;
	}
}
