package ortus.boxlang.lsp.workspace.completion;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;

import ortus.boxlang.compiler.ast.BoxNode;
import ortus.boxlang.compiler.ast.expression.BoxArgument;
import ortus.boxlang.compiler.ast.expression.BoxFunctionInvocation;
import ortus.boxlang.compiler.ast.expression.BoxIdentifier;
import ortus.boxlang.compiler.ast.expression.BoxMethodInvocation;
import ortus.boxlang.compiler.ast.expression.BoxNew;
import ortus.boxlang.compiler.ast.expression.BoxStringLiteral;
import ortus.boxlang.compiler.ast.statement.BoxArgumentDeclaration;
import ortus.boxlang.compiler.ast.statement.BoxFunctionDeclaration;
import ortus.boxlang.lsp.workspace.BLASTTools;
import ortus.boxlang.lsp.workspace.ProjectContextProvider;
import ortus.boxlang.lsp.workspace.index.IndexedMethod;
import ortus.boxlang.lsp.workspace.index.IndexedParameter;
import ortus.boxlang.lsp.workspace.rules.IRule;
import ortus.boxlang.lsp.workspace.visitors.VariableScopeCollectorVisitor;
import ortus.boxlang.lsp.workspace.visitors.VariableScopeCollectorVisitor.VariableInfo;
import ortus.boxlang.lsp.workspace.visitors.VariableTypeCollectorVisitor;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.bifs.BIFDescriptor;
import ortus.boxlang.runtime.types.Argument;

/**
 * Provides smart completion inside function call arguments.
 * 
 * Features:
 * - Named argument completion (paramName:)
 * - Variable suggestions matching parameter type
 * - Boolean literals (true/false) for boolean parameters
 * - Shows remaining required parameters
 * 
 * Task 3.8: Completion - Arguments in Function Calls
 */
public class ArgumentCompletionRule implements IRule<CompletionFacts, List<CompletionItem>> {

	@Override
	public boolean when( CompletionFacts facts ) {
		// Only provide argument completion when inside function call parentheses
		return facts.getContext().getKind() == CompletionContextKind.FUNCTION_ARGUMENT;
	}

	@Override
	public void then( CompletionFacts facts, List<CompletionItem> result ) {
		CompletionContext	context			= facts.getContext();
		int					argumentIndex	= context.getArgumentIndex();

		// Find the function/method call node containing the cursor
		BoxNode				callNode		= findCallNodeAtCursor( facts );
		if ( callNode == null ) {
			return;
		}

		// Get parameter information based on call type
		List<ParameterInfo> parameters = getParametersForCall( facts, callNode );
		if ( parameters == null || parameters.isEmpty() ) {
			return;
		}

		// Get already-used named arguments
		Set<String> usedArguments = getUsedNamedArguments( callNode );

		// Add named argument completions for remaining parameters
		addNamedArgumentCompletions( parameters, usedArguments, result );

		// Add variable suggestions if we have a current parameter (positional)
		if ( argumentIndex >= 0 && argumentIndex < parameters.size() ) {
			ParameterInfo currentParam = parameters.get( argumentIndex );
			addVariableSuggestions( facts, currentParam, result );
			addBooleanLiterals( currentParam, result );
		}
	}

	/**
	 * Find the function/method call node at the cursor position.
	 * For argument completion, we need to check if the cursor is within the arguments,
	 * not just the function name itself.
	 */
	private BoxNode findCallNodeAtCursor( CompletionFacts facts ) {
		CompletionContext	context			= facts.getContext();
		int					cursorLine		= context.getCursorPosition().getLine() + 1; // 1-based
		int					cursorColumn	= context.getCursorPosition().getCharacter();

		return facts.fileParseResult().findAstRoot()
		    .map( root -> {
			    // Search for function invocations, method invocations, and constructor calls
			    List<BoxNode> candidates = new ArrayList<>();
			    candidates.addAll( root.getDescendantsOfType( BoxFunctionInvocation.class ) );
			    candidates.addAll( root.getDescendantsOfType( BoxMethodInvocation.class ) );
			    candidates.addAll( root.getDescendantsOfType( BoxNew.class ) );

			    // Find the closest call node whose FULL extent (including arguments) contains the cursor
			    for ( BoxNode node : candidates ) {
				    if ( containsPositionInArguments( node, cursorLine, cursorColumn ) ) {
					    return node;
				    }
			    }
			    return null;
		    } )
		    .orElse( null );
	}

	/**
	 * Check if cursor position is within the arguments of a function/method call.
	 * This includes the opening paren, arguments, and closing paren.
	 */
	private boolean containsPositionInArguments( BoxNode node, int line, int column ) {
		ortus.boxlang.compiler.ast.Position nodePos = node.getPosition();
		if ( nodePos == null ) {
			return false;
		}

		int	startLine	= nodePos.getStart().getLine();
		int	startCol	= nodePos.getStart().getColumn();
		int	endLine		= nodePos.getEnd().getLine();
		int	endCol		= nodePos.getEnd().getColumn();

		// Check if cursor is within the overall node bounds
		if ( line < startLine || line > endLine ) {
			return false;
		}
		if ( line == startLine && column < startCol ) {
			return false;
		}
		if ( line == endLine && column > endCol ) {
			return false;
		}

		return true;
	}

	/**
	 * Get parameter information for a function/method/constructor call.
	 */
	private List<ParameterInfo> getParametersForCall( CompletionFacts facts, BoxNode callNode ) {
		if ( callNode instanceof BoxFunctionInvocation fnInvocation ) {
			return getParametersForFunctionInvocation( facts, fnInvocation );
		} else if ( callNode instanceof BoxMethodInvocation methodInvocation ) {
			return getParametersForMethodInvocation( facts, methodInvocation );
		} else if ( callNode instanceof BoxNew newExpr ) {
			return getParametersForConstructor( facts, newExpr );
		}
		return null;
	}

	/**
	 * Get parameters for a function invocation (UDF or BIF).
	 */
	private List<ParameterInfo> getParametersForFunctionInvocation( CompletionFacts facts, BoxFunctionInvocation fnInvocation ) {
		String	functionName	= fnInvocation.getName();

		// Try UDF first
		var		udfParams		= facts.fileParseResult().findAstRoot()
		    .flatMap( root -> root.getDescendantsOfType( BoxFunctionDeclaration.class,
		        n -> n.getName().equalsIgnoreCase( functionName ) )
		        .stream()
		        .findFirst() )
		    .map( this::extractParametersFromFunction )
		    .orElse( null );

		if ( udfParams != null ) {
			return udfParams;
		}

		// Try BIF
		return getParametersForBIF( functionName );
	}

	/**
	 * Get parameters for a method invocation.
	 */
	private List<ParameterInfo> getParametersForMethodInvocation( CompletionFacts facts, BoxMethodInvocation methodInvocation ) {
		String	methodName	= methodInvocation.getName().getSourceText();

		// Try to resolve the receiver's type
		BoxNode	obj			= methodInvocation.getObj();
		if ( obj instanceof BoxIdentifier objIdentifier ) {
			String	varName			= objIdentifier.getName();

			// Collect variable types
			var		typeCollector	= new VariableTypeCollectorVisitor();
			facts.fileParseResult().findAstRoot().ifPresent( root -> root.accept( typeCollector ) );
			String className = typeCollector.getVariableType( varName );

			if ( className != null ) {
				// Look up method in project index
				var indexedMethodOpt = ProjectContextProvider.getInstance().getIndex().findMethod( className, methodName );
				if ( indexedMethodOpt.isPresent() ) {
					return extractParametersFromIndexedMethod( indexedMethodOpt.get() );
				}
			}
		}

		// Fall back to finding method in same file
		return facts.fileParseResult().findAstRoot()
		    .flatMap( root -> root.getDescendantsOfType( BoxFunctionDeclaration.class,
		        n -> n.getName().equalsIgnoreCase( methodName ) )
		        .stream()
		        .findFirst() )
		    .map( this::extractParametersFromFunction )
		    .orElse( null );
	}

	/**
	 * Get parameters for a constructor call (new ClassName()).
	 */
	private List<ParameterInfo> getParametersForConstructor( CompletionFacts facts, BoxNew newExpr ) {
		String className = extractClassNameFromNew( newExpr );
		if ( className != null ) {
			// Look for init method
			var initMethodOpt = ProjectContextProvider.getInstance().getIndex().findMethod( className, "init" );
			if ( initMethodOpt.isPresent() ) {
				return extractParametersFromIndexedMethod( initMethodOpt.get() );
			}
		}
		return null;
	}

	/**
	 * Extract class name from a new expression.
	 */
	private String extractClassNameFromNew( BoxNew newExpr ) {
		var expr = newExpr.getExpression();
		if ( expr instanceof BoxIdentifier id ) {
			return id.getName();
		}
		// Handle other cases if needed
		return null;
	}

	/**
	 * Get parameters for a BIF.
	 */
	private List<ParameterInfo> getParametersForBIF( String bifName ) {
		BIFDescriptor func = BoxRuntime.getInstance().getFunctionService().getGlobalFunction( bifName );
		if ( func == null ) {
			return null;
		}

		List<ParameterInfo> params = new ArrayList<>();
		for ( Argument arg : func.getBIF().getDeclaredArguments() ) {
			params.add( new ParameterInfo(
			    arg.name().getName(),
			    arg.type(),
			    arg.required()
			) );
		}
		return params;
	}

	/**
	 * Extract parameters from a BoxFunctionDeclaration.
	 */
	private List<ParameterInfo> extractParametersFromFunction( BoxFunctionDeclaration fnDecl ) {
		List<ParameterInfo> params = new ArrayList<>();
		for ( BoxNode child : fnDecl.getChildren() ) {
			if ( child instanceof BoxArgumentDeclaration arg ) {
				String	type		= arg.getType() != null ? arg.getType().toString() : "any";
				boolean	required	= arg.getRequired();
				params.add( new ParameterInfo( arg.getName(), type, required ) );
			}
		}
		return params;
	}

	/**
	 * Extract parameters from an IndexedMethod.
	 */
	private List<ParameterInfo> extractParametersFromIndexedMethod( IndexedMethod method ) {
		List<ParameterInfo> params = new ArrayList<>();
		for ( IndexedParameter param : method.parameters() ) {
			String	type		= param.typeHint() != null && !param.typeHint().isEmpty() ? param.typeHint() : "any";
			boolean	required	= param.required();
			params.add( new ParameterInfo( param.name(), type, required ) );
		}
		return params;
	}

	/**
	 * Get the set of named arguments already used in the call.
	 */
	private Set<String> getUsedNamedArguments( BoxNode callNode ) {
		Set<String>			usedNames	= new HashSet<>();
		List<BoxArgument>	arguments	= null;

		if ( callNode instanceof BoxFunctionInvocation fnInvocation ) {
			arguments = fnInvocation.getArguments();
		} else if ( callNode instanceof BoxMethodInvocation methodInvocation ) {
			arguments = methodInvocation.getArguments();
		} else if ( callNode instanceof BoxNew newExpr ) {
			arguments = newExpr.getArguments();
		}

		if ( arguments != null ) {
			for ( BoxArgument arg : arguments ) {
				// Named arguments can be BoxIdentifier or BoxStringLiteral
				BoxNode	nameNode	= arg.getName();
				String	argName		= null;

				if ( nameNode instanceof BoxIdentifier nameId ) {
					argName = nameId.getName();
				} else if ( nameNode instanceof BoxStringLiteral nameLit ) {
					argName = nameLit.getValue();
				}

				if ( argName != null ) {
					usedNames.add( argName.toLowerCase() );
				}
			}
		}

		return usedNames;
	}

	/**
	 * Add named argument completions for remaining parameters.
	 */
	private void addNamedArgumentCompletions( List<ParameterInfo> parameters, Set<String> usedArguments, List<CompletionItem> result ) {
		for ( ParameterInfo param : parameters ) {
			// Skip already-used parameters
			if ( usedArguments.contains( param.name.toLowerCase() ) ) {
				continue;
			}

			CompletionItem item = new CompletionItem();
			item.setLabel( param.name + "=" );
			item.setKind( CompletionItemKind.Field );
			item.setInsertText( param.name + "=" );

			// Build detail showing type and required status
			StringBuilder detail = new StringBuilder();
			if ( param.required ) {
				detail.append( "required " );
			}
			detail.append( param.type );
			item.setDetail( detail.toString() );

			// Sort required parameters higher
			String sortPrefix = param.required ? "0" : "1";
			item.setSortText( sortPrefix + param.name );

			result.add( item );
		}
	}

	/**
	 * Add variable suggestions matching the expected parameter type.
	 */
	private void addVariableSuggestions( CompletionFacts facts, ParameterInfo currentParam, List<CompletionItem> result ) {
		// Collect variables in scope
		VariableScopeCollectorVisitor scopeCollector = new VariableScopeCollectorVisitor();
		facts.fileParseResult().findAstRoot().ifPresent( root -> root.accept( scopeCollector ) );

		// Find containing function
		BoxFunctionDeclaration			containingFunction	= facts.fileParseResult().findAstRoot()
		    .flatMap( root -> {
																    int line = facts.getContext().getCursorPosition().getLine() + 1;
																    int col	= facts.getContext().getCursorPosition().getCharacter();
																    return root.getDescendantsOfType( BoxFunctionDeclaration.class ).stream()
																        .filter( func -> BLASTTools.containsPosition( func, line, col ) )
																        .findFirst();
															    } )
		    .orElse( null );

		// Collect variable types
		VariableTypeCollectorVisitor	typeCollector		= new VariableTypeCollectorVisitor();
		facts.fileParseResult().findAstRoot().ifPresent( root -> root.accept( typeCollector ) );

		// Get all visible variables
		var visibleVariables = scopeCollector.getAllVisibleVariables( containingFunction );

		for ( var entry : visibleVariables.entrySet() ) {
			String			varName	= entry.getKey();
			VariableInfo	varInfo	= entry.getValue();
			String			varType	= varInfo.typeHint() != null ? varInfo.typeHint() : typeCollector.getVariableType( varName );

			if ( varType != null && typesMatch( varType, currentParam.type ) ) {
				CompletionItem item = new CompletionItem();
				item.setLabel( varName );
				item.setKind( CompletionItemKind.Variable );
				item.setInsertText( varName );
				item.setDetail( varType );
				item.setSortText( "2" + varName ); // Sort after named arguments
				result.add( item );
			}
		}
	}

	/**
	 * Add boolean literals (true/false) for boolean parameters.
	 */
	private void addBooleanLiterals( ParameterInfo currentParam, List<CompletionItem> result ) {
		if ( isBooleanType( currentParam.type ) ) {
			// Add true
			CompletionItem trueItem = new CompletionItem();
			trueItem.setLabel( "true" );
			trueItem.setKind( CompletionItemKind.Keyword );
			trueItem.setInsertText( "true" );
			trueItem.setDetail( "boolean literal" );
			trueItem.setSortText( "3true" );
			result.add( trueItem );

			// Add false
			CompletionItem falseItem = new CompletionItem();
			falseItem.setLabel( "false" );
			falseItem.setKind( CompletionItemKind.Keyword );
			falseItem.setInsertText( "false" );
			falseItem.setDetail( "boolean literal" );
			falseItem.setSortText( "3false" );
			result.add( falseItem );
		}
	}

	/**
	 * Check if a type is boolean.
	 */
	private boolean isBooleanType( String type ) {
		if ( type == null ) {
			return false;
		}
		String lowerType = type.toLowerCase();
		return lowerType.equals( "boolean" ) || lowerType.equals( "bool" );
	}

	/**
	 * Check if two types match (for variable suggestions).
	 * Uses case-insensitive comparison and basic type compatibility.
	 */
	private boolean typesMatch( String varType, String paramType ) {
		if ( varType == null || paramType == null ) {
			return false;
		}

		String	vt	= varType.toLowerCase();
		String	pt	= paramType.toLowerCase();

		// Exact match
		if ( vt.equals( pt ) ) {
			return true;
		}

		// "any" matches everything
		if ( pt.equals( "any" ) ) {
			return true;
		}

		// Numeric types
		if ( ( pt.equals( "numeric" ) || pt.equals( "number" ) || pt.equals( "integer" ) )
		    && ( vt.equals( "numeric" ) || vt.equals( "number" ) || vt.equals( "integer" ) ) ) {
			return true;
		}

		// String types
		if ( pt.equals( "string" ) && vt.equals( "string" ) ) {
			return true;
		}

		return false;
	}

	/**
	 * Holds parameter information.
	 */
	private static class ParameterInfo {

		String	name;
		String	type;
		boolean	required;

		ParameterInfo( String name, String type, boolean required ) {
			this.name		= name;
			this.type		= type;
			this.required	= required;
		}
	}
}
