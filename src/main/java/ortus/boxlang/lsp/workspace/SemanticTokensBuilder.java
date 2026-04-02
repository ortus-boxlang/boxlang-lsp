package ortus.boxlang.lsp.workspace;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.eclipse.lsp4j.SemanticTokens;

import ortus.boxlang.compiler.ast.BoxClass;
import ortus.boxlang.compiler.ast.BoxExpression;
import ortus.boxlang.compiler.ast.BoxNode;
import ortus.boxlang.compiler.ast.BoxInterface;
import ortus.boxlang.compiler.ast.Point;
import ortus.boxlang.compiler.ast.expression.BoxDotAccess;
import ortus.boxlang.compiler.ast.expression.BoxFunctionInvocation;
import ortus.boxlang.compiler.ast.expression.BoxIdentifier;
import ortus.boxlang.compiler.ast.expression.BoxMethodInvocation;
import ortus.boxlang.compiler.ast.expression.BoxScope;
import ortus.boxlang.compiler.ast.statement.BoxFunctionDeclaration;
import ortus.boxlang.compiler.ast.statement.BoxProperty;
import ortus.boxlang.lsp.App;
import ortus.boxlang.lsp.workspace.visitors.VariableScopeCollectorVisitor;
import ortus.boxlang.lsp.workspace.visitors.VariableScopeCollectorVisitor.VariableInfo;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.BoxLangType;

/**
 * Builds semantic tokens for function/member invocations, declarations, and property references.
 */
public class SemanticTokensBuilder {

	public SemanticTokens build( BoxNode root ) {
		if ( root == null ) {
			return SemanticTokensContract.emptyTokens();
		}

		try {
			List<AbsoluteSemanticToken>	tokens				= new ArrayList<>();
			Map<String, Boolean>		builtinCache		= new HashMap<>();
			MemberResolutionContext		memberResolutionCtx	= buildMemberResolutionContext( root );

			collectFunctionInvocationTokens( root, tokens, builtinCache );
			collectMethodInvocationTokens( root, tokens, memberResolutionCtx );
			collectFunctionDeclarationTokens( root, tokens );
			collectPropertyAccessTokens( root, tokens, memberResolutionCtx.scopeCollector() );

			if ( tokens.isEmpty() ) {
				return SemanticTokensContract.emptyTokens();
			}

			tokens.sort(
			    Comparator.comparingInt( AbsoluteSemanticToken::line )
			        .thenComparingInt( AbsoluteSemanticToken::start )
			        .thenComparingInt( AbsoluteSemanticToken::length )
			        .thenComparingInt( AbsoluteSemanticToken::tokenType )
			        .thenComparingInt( AbsoluteSemanticToken::modifiers )
			);

			return encodeRelative( tokens );
		} catch ( Exception e ) {
			App.logger.debug( "Failed to build semantic tokens", e );
			return SemanticTokensContract.emptyTokens();
		}
	}

	private MemberResolutionContext buildMemberResolutionContext( BoxNode root ) {
		VariableScopeCollectorVisitor scopeCollector = new VariableScopeCollectorVisitor();
		root.accept( scopeCollector );
		return new MemberResolutionContext( scopeCollector, new HashMap<>() );
	}

	private void collectFunctionInvocationTokens( BoxNode root, List<AbsoluteSemanticToken> tokens, Map<String, Boolean> builtinCache ) {
		List<BoxFunctionInvocation> invocations = root.getDescendantsOfType( BoxFunctionInvocation.class );
		for ( BoxFunctionInvocation invocation : invocations ) {
			String name = invocation.getName();
			if ( name == null || name.isBlank() ) {
				continue;
			}

			Point start = getStartPoint( invocation );
			if ( start == null ) {
				continue;
			}

			int modifiers = 0;
			if ( isBuiltInFunction( name, builtinCache ) ) {
				modifiers |= SemanticTokensContract.MODIFIER_DEFAULT_LIB;
			}

			tokens.add( new AbsoluteSemanticToken(
			    Math.max( 0, start.getLine() - 1 ),
			    Math.max( 0, start.getColumn() ),
			    name.length(),
			    SemanticTokensContract.TOKEN_TYPE_FUNCTION,
			    modifiers
			) );
		}
	}

	private void collectMethodInvocationTokens( BoxNode root, List<AbsoluteSemanticToken> tokens, MemberResolutionContext memberResolutionCtx ) {
		List<BoxMethodInvocation> invocations = root.getDescendantsOfType( BoxMethodInvocation.class );
		for ( BoxMethodInvocation invocation : invocations ) {
			BoxExpression nameNode = invocation.getName();
			if ( nameNode == null ) {
				continue;
			}

			String nameText = nameNode.getSourceText();
			if ( nameText == null || nameText.isBlank() ) {
				continue;
			}

			Point start = getStartPoint( nameNode );
			if ( start == null ) {
				continue;
			}

			int modifiers = 0;
			if ( isBuiltInMemberMethod( invocation, nameText, memberResolutionCtx ) ) {
				modifiers |= SemanticTokensContract.MODIFIER_DEFAULT_LIB;
			}

			tokens.add( new AbsoluteSemanticToken(
			    Math.max( 0, start.getLine() - 1 ),
			    Math.max( 0, start.getColumn() ),
			    nameText.length(),
			    SemanticTokensContract.TOKEN_TYPE_METHOD,
			    modifiers
			) );
		}
	}

	private void collectFunctionDeclarationTokens( BoxNode root, List<AbsoluteSemanticToken> tokens ) {
		List<BoxFunctionDeclaration> declarations = root.getDescendantsOfType( BoxFunctionDeclaration.class );
		for ( BoxFunctionDeclaration declaration : declarations ) {
			String name = declaration.getName();
			if ( name == null || name.isBlank() ) {
				continue;
			}

			TokenStart tokenStart = findFunctionNameStart( declaration, name );
			if ( tokenStart == null ) {
				continue;
			}

			int tokenType = isMethodDeclaration( declaration )
			    ? SemanticTokensContract.TOKEN_TYPE_METHOD
			    : SemanticTokensContract.TOKEN_TYPE_FUNCTION;

			tokens.add( new AbsoluteSemanticToken(
			    tokenStart.line,
			    tokenStart.column,
			    name.length(),
			    tokenType,
			    SemanticTokensContract.MODIFIER_DECLARATION
			) );
		}
	}

	private void collectPropertyAccessTokens( BoxNode root, List<AbsoluteSemanticToken> tokens, VariableScopeCollectorVisitor scopeCollector ) {
		List<BoxDotAccess>			accesses			= root.getDescendantsOfType( BoxDotAccess.class );
		Map<BoxClass, Set<String>>	declaredProperties	= new HashMap<>();

		for ( BoxDotAccess dotAccess : accesses ) {
			if ( !isPropertyScopeAccess( dotAccess.getContext() ) ) {
				continue;
			}

			if ( ! ( dotAccess.getAccess() instanceof BoxIdentifier accessIdentifier ) ) {
				continue;
			}

			BoxClass containingClass = dotAccess.getFirstAncestorOfType( BoxClass.class );
			if ( containingClass == null ) {
				continue;
			}

			String propertyName = accessIdentifier.getName();
			if ( propertyName == null || propertyName.isBlank() ) {
				continue;
			}

			Set<String> classProperties = declaredProperties.computeIfAbsent( containingClass, this::collectDeclaredPropertyNames );
			if ( !classProperties.contains( propertyName.toLowerCase( Locale.ROOT ) ) ) {
				continue;
			}

			Point start = getStartPoint( accessIdentifier );
			if ( start == null ) {
				continue;
			}

			tokens.add( new AbsoluteSemanticToken(
			    Math.max( 0, start.getLine() - 1 ),
			    Math.max( 0, start.getColumn() ),
			    propertyName.length(),
			    SemanticTokensContract.TOKEN_TYPE_PROPERTY,
			    0
			) );
		}

		collectUnscopedPropertyTokens( root, tokens, declaredProperties, scopeCollector );
	}

	private void collectUnscopedPropertyTokens(
	    BoxNode root,
	    List<AbsoluteSemanticToken> tokens,
	    Map<BoxClass, Set<String>> declaredProperties,
	    VariableScopeCollectorVisitor scopeCollector ) {

		List<BoxIdentifier> identifiers = root.getDescendantsOfType( BoxIdentifier.class );
		for ( BoxIdentifier identifier : identifiers ) {
			BoxClass containingClass = identifier.getFirstAncestorOfType( BoxClass.class );
			if ( containingClass == null ) {
				continue;
			}

			if ( identifier.getFirstAncestorOfType( BoxProperty.class ) != null ) {
				continue;
			}

			BoxNode parent = identifier.getParent();
			if ( parent instanceof BoxDotAccess dotAccess ) {
				if ( dotAccess.getAccess() == identifier || dotAccess.getContext() == identifier ) {
					continue;
				}
			}

			String propertyName = identifier.getName();
			if ( propertyName == null || propertyName.isBlank() ) {
				continue;
			}

			Set<String> classProperties = declaredProperties.computeIfAbsent( containingClass, this::collectDeclaredPropertyNames );
			if ( !classProperties.contains( propertyName.toLowerCase( Locale.ROOT ) ) ) {
				continue;
			}

			if ( isShadowedByLocalOrArgument( identifier, scopeCollector ) ) {
				continue;
			}

			Point start = getStartPoint( identifier );
			if ( start == null ) {
				continue;
			}

			tokens.add( new AbsoluteSemanticToken(
			    Math.max( 0, start.getLine() - 1 ),
			    Math.max( 0, start.getColumn() ),
			    propertyName.length(),
			    SemanticTokensContract.TOKEN_TYPE_PROPERTY,
			    0
			) );
		}
	}

	private boolean isShadowedByLocalOrArgument( BoxIdentifier identifier, VariableScopeCollectorVisitor scopeCollector ) {
		if ( identifier == null || scopeCollector == null ) {
			return false;
		}

		String identifierName = identifier.getName();
		if ( identifierName == null || identifierName.isBlank() ) {
			return false;
		}

		BoxFunctionDeclaration	containingFunction	= identifier.getFirstAncestorOfType( BoxFunctionDeclaration.class );
		VariableInfo			variableInfo		= scopeCollector.getVariableInfo( identifierName, containingFunction );
		if ( variableInfo == null || variableInfo.scope() == null ) {
			return false;
		}

		return variableInfo.scope() == VariableScopeCollectorVisitor.VariableScope.LOCAL
		    || variableInfo.scope() == VariableScopeCollectorVisitor.VariableScope.ARGUMENTS;
	}

	private Set<String> collectDeclaredPropertyNames( BoxClass boxClass ) {
		Set<String> declaredProperties = new HashSet<>();
		if ( boxClass == null || boxClass.getProperties() == null ) {
			return declaredProperties;
		}

		for ( BoxProperty property : boxClass.getProperties() ) {
			String propertyName = BLASTTools.getPropertyName( property );
			if ( propertyName != null && !propertyName.isBlank() ) {
				declaredProperties.add( propertyName.toLowerCase( Locale.ROOT ) );
			}
		}

		return declaredProperties;
	}

	private boolean isPropertyScopeAccess( BoxExpression contextExpression ) {
		if ( contextExpression instanceof BoxScope scope ) {
			return isPropertyScopeName( scope.getName() );
		}

		if ( contextExpression instanceof BoxIdentifier scopeIdentifier ) {
			return isPropertyScopeName( scopeIdentifier.getName() );
		}

		return false;
	}

	private boolean isPropertyScopeName( String scopeName ) {
		if ( scopeName == null ) {
			return false;
		}

		String normalized = scopeName.toLowerCase( Locale.ROOT );
		return normalized.equals( "variables" ) || normalized.equals( "this" );
	}

	private boolean isMethodDeclaration( BoxFunctionDeclaration declaration ) {
		return declaration.getFirstAncestorOfType( BoxClass.class ) != null
		    || declaration.getFirstAncestorOfType( BoxInterface.class ) != null;
	}

	private boolean isBuiltInFunction( String functionName, Map<String, Boolean> cache ) {
		String	key		= functionName.toLowerCase( Locale.ROOT );
		Boolean	cached	= cache.get( key );
		if ( cached != null ) {
			return cached;
		}

		boolean isBuiltin;
		try {
			isBuiltin = BoxRuntime.getInstance().getFunctionService().getGlobalFunction( functionName ) != null;
		} catch ( Exception e ) {
			isBuiltin = false;
		}

		cache.put( key, isBuiltin );
		return isBuiltin;
	}

	private boolean isBuiltInMemberMethod( BoxMethodInvocation invocation, String methodName, MemberResolutionContext context ) {
		BoxLangType receiverType = inferReceiverType( invocation, context.scopeCollector() );
		if ( receiverType == null || receiverType == BoxLangType.ANY ) {
			return false;
		}

		String	cacheKey	= receiverType.name() + ":" + methodName.toLowerCase( Locale.ROOT );
		Boolean	cached		= context.memberLookupCache().get( cacheKey );
		if ( cached != null ) {
			return cached;
		}

		boolean isBuiltinMember;
		try {
			isBuiltinMember = BoxRuntime.getInstance().getFunctionService().getMemberMethod( Key.of( methodName ), receiverType ) != null;
		} catch ( Exception e ) {
			isBuiltinMember = false;
		}

		context.memberLookupCache().put( cacheKey, isBuiltinMember );
		return isBuiltinMember;
	}

	private BoxLangType inferReceiverType( BoxMethodInvocation invocation, VariableScopeCollectorVisitor scopeCollector ) {
		BoxExpression receiver = invocation.getObj();
		if ( receiver == null ) {
			return null;
		}

		BoxLangType directType = ExpressionTypeResolver.determineType( receiver );
		if ( directType != null && directType != BoxLangType.ANY ) {
			return directType;
		}

		if ( receiver instanceof BoxIdentifier identifier ) {
			return inferTypeFromIdentifier( identifier.getName(), invocation, scopeCollector );
		}

		if ( receiver instanceof BoxDotAccess dotAccess ) {
			return inferTypeFromDotAccess( dotAccess, invocation, scopeCollector );
		}

		if ( receiver instanceof BoxScope scope && isTypedScopeName( scope.getName() ) ) {
			return BoxLangType.STRUCT;
		}

		return null;
	}

	private BoxLangType inferTypeFromIdentifier( String name, BoxMethodInvocation invocation, VariableScopeCollectorVisitor scopeCollector ) {
		if ( name == null || name.isBlank() ) {
			return null;
		}

		if ( scopeCollector.isScopeKeyword( name ) ) {
			return isTypedScopeName( name ) ? BoxLangType.STRUCT : null;
		}

		BoxFunctionDeclaration	containingFunction	= invocation.getFirstAncestorOfType( BoxFunctionDeclaration.class );
		VariableInfo			varInfo				= scopeCollector.getVariableInfo( name, containingFunction );
		if ( varInfo == null ) {
			return null;
		}

		BoxLangType typeFromInferred = coerceBoxLangType( varInfo.inferredType() );
		if ( typeFromInferred != null ) {
			return typeFromInferred;
		}

		return coerceBoxLangType( varInfo.typeHint() );
	}

	private BoxLangType inferTypeFromDotAccess( BoxDotAccess dotAccess, BoxMethodInvocation invocation, VariableScopeCollectorVisitor scopeCollector ) {
		BoxExpression	context	= dotAccess.getContext();
		BoxExpression	access	= dotAccess.getAccess();
		if ( ! ( access instanceof BoxIdentifier accessIdentifier ) ) {
			return null;
		}

		if ( context instanceof BoxScope scope && isTypedScopeName( scope.getName() ) ) {
			return inferTypeFromIdentifier( accessIdentifier.getName(), invocation, scopeCollector );
		}

		if ( context instanceof BoxIdentifier scopeIdentifier && isTypedScopeName( scopeIdentifier.getName() ) ) {
			return inferTypeFromIdentifier( accessIdentifier.getName(), invocation, scopeCollector );
		}

		return null;
	}

	private boolean isTypedScopeName( String scopeName ) {
		if ( scopeName == null ) {
			return false;
		}

		String normalized = scopeName.toLowerCase( Locale.ROOT );
		return normalized.equals( "this" ) ||
		    normalized.equals( "variables" ) ||
		    normalized.equals( "local" ) ||
		    normalized.equals( "arguments" ) ||
		    normalized.equals( "request" ) ||
		    normalized.equals( "session" ) ||
		    normalized.equals( "application" ) ||
		    normalized.equals( "server" ) ||
		    normalized.equals( "cgi" ) ||
		    normalized.equals( "form" ) ||
		    normalized.equals( "url" ) ||
		    normalized.equals( "cookie" );
	}

	private BoxLangType coerceBoxLangType( String typeName ) {
		if ( typeName == null || typeName.isBlank() ) {
			return null;
		}

		String normalized = typeName.trim();
		for ( BoxLangType type : BoxLangType.values() ) {
			if ( type.name().equalsIgnoreCase( normalized ) || type.getKey().getName().equalsIgnoreCase( normalized ) ) {
				return type;
			}
		}

		return switch ( normalized.toLowerCase( Locale.ROOT ) ) {
			case "number", "double", "float", "integer", "int", "long", "short", "byte", "bigdecimal" -> BoxLangType.NUMERIC;
			case "bool" -> BoxLangType.BOOLEAN;
			default -> null;
		};
	}

	private SemanticTokens encodeRelative( List<AbsoluteSemanticToken> tokens ) {
		List<Integer>	data			= new ArrayList<>( tokens.size() * 5 );

		int				previousLine	= 0;
		int				previousStart	= 0;
		for ( AbsoluteSemanticToken token : tokens ) {
			int	deltaLine	= token.line - previousLine;
			int	deltaStart	= deltaLine == 0
			    ? token.start - previousStart
			    : token.start;

			data.add( deltaLine );
			data.add( deltaStart );
			data.add( token.length );
			data.add( token.tokenType );
			data.add( token.modifiers );

			previousLine	= token.line;
			previousStart	= token.start;
		}

		return new SemanticTokens( data );
	}

	private TokenStart findFunctionNameStart( BoxFunctionDeclaration declaration, String functionName ) {
		Point start = getStartPoint( declaration );
		if ( start == null ) {
			return null;
		}

		String sourceText = declaration.getSourceText();
		if ( sourceText == null || sourceText.isBlank() ) {
			return new TokenStart( Math.max( 0, start.getLine() - 1 ), Math.max( 0, start.getColumn() ) );
		}

		String	lowerSource				= sourceText.toLowerCase( Locale.ROOT );
		String	lowerName				= functionName.toLowerCase( Locale.ROOT );
		int		functionKeywordIndex	= lowerSource.indexOf( "function" );
		int		nameIndex				= functionKeywordIndex >= 0
		    ? lowerSource.indexOf( lowerName, functionKeywordIndex )
		    : lowerSource.indexOf( lowerName );

		if ( nameIndex < 0 ) {
			return new TokenStart( Math.max( 0, start.getLine() - 1 ), Math.max( 0, start.getColumn() ) );
		}

		int	baseLine	= Math.max( 0, start.getLine() - 1 );
		int	baseColumn	= Math.max( 0, start.getColumn() );

		int	line		= baseLine;
		int	column		= baseColumn;
		for ( int i = 0; i < nameIndex && i < sourceText.length(); i++ ) {
			char ch = sourceText.charAt( i );
			if ( ch == '\n' ) {
				line++;
				column = 0;
			} else {
				column++;
			}
		}

		return new TokenStart( line, Math.max( 0, column ) );
	}

	private Point getStartPoint( BoxNode node ) {
		if ( node == null || node.getPosition() == null || node.getPosition().getStart() == null ) {
			return null;
		}
		return node.getPosition().getStart();
	}

	private record TokenStart( int line, int column ) {
	}

	private record AbsoluteSemanticToken( int line, int start, int length, int tokenType, int modifiers ) {
	}

	private record MemberResolutionContext( VariableScopeCollectorVisitor scopeCollector, Map<String, Boolean> memberLookupCache ) {
	}
}
