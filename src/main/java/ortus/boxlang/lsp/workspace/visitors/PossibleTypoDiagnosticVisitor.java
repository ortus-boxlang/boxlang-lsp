package ortus.boxlang.lsp.workspace.visitors;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import ortus.boxlang.compiler.ast.BoxClass;
import ortus.boxlang.compiler.ast.BoxInterface;
import ortus.boxlang.compiler.ast.BoxNode;
import ortus.boxlang.compiler.ast.BoxScript;
import ortus.boxlang.compiler.ast.BoxTemplate;
import ortus.boxlang.compiler.ast.expression.BoxAccess;
import ortus.boxlang.compiler.ast.expression.BoxAssignment;
import ortus.boxlang.compiler.ast.expression.BoxFQN;
import ortus.boxlang.compiler.ast.expression.BoxFunctionInvocation;
import ortus.boxlang.compiler.ast.expression.BoxIdentifier;
import ortus.boxlang.compiler.ast.expression.BoxStringInterpolation;
import ortus.boxlang.compiler.ast.expression.BoxFunctionalBIFAccess;
import ortus.boxlang.compiler.ast.expression.BoxFunctionalMemberAccess;
import ortus.boxlang.compiler.ast.statement.BoxAnnotation;
import ortus.boxlang.compiler.ast.statement.BoxFunctionDeclaration;
import ortus.boxlang.compiler.ast.statement.BoxImport;
import ortus.boxlang.compiler.ast.statement.BoxProperty;
import ortus.boxlang.lsp.SourceCodeVisitor;
import ortus.boxlang.lsp.lint.DiagnosticRuleRegistry;
import ortus.boxlang.lsp.lint.LintConfigLoader;
import ortus.boxlang.lsp.lint.RuleSettings;
import ortus.boxlang.lsp.lint.rules.PossibleTypoRule;
import ortus.boxlang.lsp.workspace.BLASTTools;
import ortus.boxlang.lsp.workspace.FileParseResult;
import ortus.boxlang.lsp.workspace.ProjectContextProvider;
import ortus.boxlang.lsp.workspace.index.IndexedClass;
import ortus.boxlang.lsp.workspace.index.IndexedMethod;
import ortus.boxlang.lsp.workspace.index.IndexedProperty;
import ortus.boxlang.lsp.workspace.index.ProjectIndex;
import ortus.boxlang.runtime.BoxRuntime;

/** Reports likely misspellings without changing parser behavior. */
public class PossibleTypoDiagnosticVisitor extends SourceCodeVisitor {

	private static final int					DEFAULT_KEYWORD_DISTANCE	= 3;
	private static final int					DEFAULT_IDENTIFIER_DISTANCE	= 2;
	private static final Set<String>			KEYWORDS					= Set.of(
	    "abort", "abstract", "and", "as", "assert", "break", "case", "catch", "class", "component", "contain", "contains",
	    "continue", "default", "do", "does", "else", "extends", "false", "final", "finally", "for", "function", "if",
	    "implements", "import", "in", "include", "instanceof", "interface", "is", "java", "lock", "mod", "new", "not", "null",
	    "or", "package", "param", "private", "property", "public", "remote", "required", "rethrow", "return", "static", "switch",
	    "thread", "than", "throw", "to", "transaction", "true", "try", "var", "when", "while", "xor" );
	private static final Set<String>			RESERVED_IDENTIFIERS		= Set.of(
	    "arguments", "application", "caller", "cgi", "cookie", "form", "local", "request", "server", "session", "super", "this",
	    "thread", "url", "variables" );
	private static final Comparator<BoxNode>	NODE_POSITION_COMPARATOR	= Comparator
	    .comparingInt( ( BoxNode node ) -> node.getPosition() == null ? Integer.MAX_VALUE : node.getPosition().getStart().getLine() )
	    .thenComparingInt( node -> node.getPosition() == null ? Integer.MAX_VALUE : node.getPosition().getStart().getColumn() );

	private record IdentifierUse( String name, BoxNode node ) {
	}

	private final List<IdentifierUse>		uses					= new ArrayList<>();
	private final Set<BoxIdentifier>		ignoredIdentifiers		= new HashSet<>();
	private final Map<String, String>		visibleIdentifiers		= new LinkedHashMap<>();
	private final Map<String, String>		fileIdentifiers			= new LinkedHashMap<>();
	private final Map<String, String>		parentIdentifiers		= new LinkedHashMap<>();
	private final Map<String, String>		globalIdentifiers		= new LinkedHashMap<>();
	private final List<Diagnostic>			diagnostics				= new ArrayList<>();
	private final Map<Diagnostic, String>	suggestionsByDiagnostic	= new LinkedHashMap<>();
	private final Map<Diagnostic, String>	actualNamesByDiagnostic	= new LinkedHashMap<>();
	private boolean							analyzed;

	@Override
	public void visit( BoxClass node ) {
		analyze( node );
	}

	@Override
	public void visit( BoxInterface node ) {
		analyze( node );
	}

	@Override
	public void visit( BoxScript node ) {
		analyze( node );
	}

	@Override
	public void visit( BoxTemplate node ) {
		analyze( node );
	}

	@Override
	public List<Diagnostic> getDiagnostics() {
		return diagnostics;
	}

	@Override
	public List<CodeAction> getCodeActions() {
		if ( !DiagnosticRuleRegistry.getInstance().isEnabled( PossibleTypoRule.ID, true ) ) {
			return List.of();
		}

		return diagnostics.stream()
		    .map( diagnostic -> createCodeAction( diagnostic, actualNamesByDiagnostic.get( diagnostic ), suggestionsByDiagnostic.get( diagnostic ) ) )
		    .filter( action -> action != null )
		    .toList();
	}

	@Override
	public boolean canVisit( FileParseResult parseResult ) {
		return true;
	}

	private void analyze( BoxNode root ) {
		if ( analyzed || !DiagnosticRuleRegistry.getInstance().isEnabled( PossibleTypoRule.ID, true ) ) {
			return;
		}
		analyzed = true;

		RuleSettings	settings			= LintConfigLoader.get().forRule( PossibleTypoRule.ID );
		int				keywordDistance		= getDistance( settings, "keywordDistance", DEFAULT_KEYWORD_DISTANCE );
		int				identifierDistance	= getDistance( settings, "identifierDistance", DEFAULT_IDENTIFIER_DISTANCE );

		collectDeclarations( root );
		collectIndexedIdentifiers();
		collectParentIdentifiers( root );
		collectUses( root );

		DiagnosticSeverity severity = settings == null
		    ? DiagnosticSeverity.Warning
		    : settings.toSeverityOr( DiagnosticSeverity.Warning );

		for ( IdentifierUse use : uses ) {
			String actualName = identifierPart( use.name() );
			if ( use.node().getPosition() == null || !isCheckable( actualName ) || isKnown( actualName ) ) {
				continue;
			}

			String suggestion = findIdentifierSuggestion( actualName, identifierDistance );
			if ( suggestion == null && !isInsideStringInterpolation( use.node() ) ) {
				suggestion = findKeywordSuggestion( actualName, keywordDistance );
			}
			if ( suggestion == null ) {
				continue;
			}

			Diagnostic diagnostic = new Diagnostic(
			    rangeFor( use ),
			    "Possible typo: '" + actualName + "' may be '" + suggestion + "'.",
			    severity,
			    "boxlang",
			    PossibleTypoRule.ID
			);
			diagnostic.setData( Map.of( "id", UUID.randomUUID().toString() ) );
			diagnostics.add( diagnostic );
			actualNamesByDiagnostic.put( diagnostic, actualName );
			suggestionsByDiagnostic.put( diagnostic, suggestion );
		}
	}

	private void collectDeclarations( BoxNode root ) {
		for ( BoxFunctionDeclaration function : root.getDescendantsOfType( BoxFunctionDeclaration.class ) ) {
			addCandidate( fileIdentifiers, function.getName() );
			if ( function.getArgs() != null ) {
				function.getArgs().forEach( argument -> addCandidate( visibleIdentifiers, argument.getName() ) );
			}
		}

		for ( BoxProperty property : root.getDescendantsOfType( BoxProperty.class ) ) {
			addCandidate( fileIdentifiers, propertyName( property ) );
		}

		for ( BoxImport importNode : root.getDescendantsOfType( BoxImport.class ) ) {
			if ( importNode.getAlias() != null ) {
				addCandidate( fileIdentifiers, importNode.getAlias().getName() );
				ignoredIdentifiers.add( importNode.getAlias() );
			}
			if ( importNode.getExpression() instanceof BoxFQN fqn ) {
				addCandidate( fileIdentifiers, identifierPart( fqn.getValue() ) );
			}
		}

		for ( BoxAssignment assignment : root.getDescendantsOfType( BoxAssignment.class ) ) {
			if ( assignment.getLeft() instanceof BoxIdentifier identifier ) {
				addCandidate( visibleIdentifiers, identifier.getName() );
				ignoredIdentifiers.add( identifier );
			} else if ( assignment.getLeft() instanceof BoxAccess access ) {
				if ( access.getAccess() instanceof BoxIdentifier identifier ) {
					addCandidate( fileIdentifiers, identifier.getName() );
					ignoredIdentifiers.add( identifier );
				}
			}
		}

		String className = fileName();
		addCandidate( fileIdentifiers, className );
	}

	private void collectIndexedIdentifiers() {
		ProjectIndex index = ProjectContextProvider.getInstance().getIndex();
		if ( index == null || filePath == null ) {
			return;
		}

		for ( IndexedClass indexedClass : index.getAllClasses() ) {
			if ( filePath.equals( indexedClass.fileUri() ) ) {
				addCandidate( fileIdentifiers, indexedClass.name() );
			}
		}
		for ( IndexedMethod method : index.getAllMethods() ) {
			if ( filePath.equals( method.fileUri() ) ) {
				addCandidate( fileIdentifiers, method.name() );
			}
		}
		for ( IndexedProperty property : index.getAllProperties() ) {
			if ( filePath.equals( property.fileUri() ) ) {
				addCandidate( fileIdentifiers, property.name() );
			}
		}

		try {
			Arrays.stream( BoxRuntime.getInstance().getFunctionService().getGlobalFunctionNames() )
			    .forEach( name -> addCandidate( globalIdentifiers, name ) );
		} catch ( Exception ignored ) {
			// Runtime BIFs are optional during parser-only operation.
		}
	}

	private void collectParentIdentifiers( BoxNode root ) {
		String parentName = getParentName( root );
		if ( parentName == null || parentName.isBlank() || filePath == null ) {
			return;
		}

		ProjectIndex index = ProjectContextProvider.getInstance().getIndex();
		if ( index == null ) {
			return;
		}

		Set<String> visited = new HashSet<>();
		while ( parentName != null && visited.add( parentName.toLowerCase( Locale.ROOT ) ) ) {
			Optional<IndexedClass> parent = findClass( index, parentName );
			if ( parent.isEmpty() ) {
				return;
			}

			IndexedClass parentClass = parent.get();
			addCandidate( parentIdentifiers, parentClass.name() );
			for ( IndexedMethod method : index.getMethodsOfClass( parentClass.fullyQualifiedName() ) ) {
				addCandidate( parentIdentifiers, method.name() );
			}
			for ( IndexedProperty property : index.findPropertiesOfClass( parentClass.fullyQualifiedName() ) ) {
				addCandidate( parentIdentifiers, property.name() );
			}
			parentName = parentClass.extendsClass();
		}
	}

	private Optional<IndexedClass> findClass( ProjectIndex index, String name ) {
		try {
			return index.findClassWithContext( name, URI.create( filePath ) );
		} catch ( Exception e ) {
			return index.findClassByName( name );
		}
	}

	private void collectUses( BoxNode root ) {
		for ( BoxIdentifier identifier : root.getDescendantsOfType( BoxIdentifier.class ) ) {
			if ( !ignoredIdentifiers.contains( identifier ) ) {
				addUse( identifier.getName(), identifier );
			}
		}
		for ( BoxFunctionInvocation invocation : root.getDescendantsOfType( BoxFunctionInvocation.class ) ) {
			addUse( invocation.getName(), invocation );
		}
		for ( BoxFunctionalBIFAccess invocation : root.getDescendantsOfType( BoxFunctionalBIFAccess.class ) ) {
			addUse( invocation.getName(), invocation );
		}
		for ( BoxFunctionalMemberAccess invocation : root.getDescendantsOfType( BoxFunctionalMemberAccess.class ) ) {
			addUse( invocation.getName(), invocation );
		}
		for ( BoxFQN fqn : root.getDescendantsOfType( BoxFQN.class ) ) {
			if ( isCheckableFqn( fqn ) ) {
				addUse( identifierPart( fqn.getValue() ), fqn );
			}
		}
		uses.sort( Comparator.comparing( IdentifierUse::node, NODE_POSITION_COMPARATOR ) );
	}

	private void addUse( String name, BoxNode node ) {
		if ( name != null && node != null ) {
			uses.add( new IdentifierUse( name, node ) );
		}
	}

	private boolean isCheckableFqn( BoxFQN fqn ) {
		if ( ! ( fqn.getParent() instanceof BoxAnnotation annotation ) ) {
			return true;
		}
		return annotation.getValue() == fqn
		    && BLASTTools.getAnnotationName( annotation )
		        .filter( key -> key.equalsIgnoreCase( "extends" ) || key.equalsIgnoreCase( "implements" ) )
		        .isPresent();
	}

	private String findIdentifierSuggestion( String actualName, int maxDistance ) {
		for ( Map<String, String> candidates : List.of( visibleIdentifiers, fileIdentifiers, parentIdentifiers, globalIdentifiers ) ) {
			List<String> matches = closeMatches( actualName, candidates, maxDistance );
			if ( matches.size() == 1 ) {
				return matches.getFirst();
			}
			if ( matches.size() > 1 ) {
				return null;
			}
		}
		return null;
	}

	private String findKeywordSuggestion( String actualName, int maxDistance ) {
		List<String> matches = KEYWORDS.stream()
		    .filter( keyword -> !keyword.equalsIgnoreCase( actualName ) )
		    .filter( keyword -> actualName.length() >= 4 && keyword.length() >= 4 )
		    .filter( keyword -> levenshteinDistance( actualName, keyword ) <= maxDistance )
		    .toList();
		if ( matches.isEmpty() ) {
			return null;
		}

		int				bestDistance	= matches.stream().mapToInt( keyword -> levenshteinDistance( actualName, keyword ) ).min().orElse( Integer.MAX_VALUE );
		List<String>	bestMatches		= matches.stream()
		    .filter( keyword -> levenshteinDistance( actualName, keyword ) == bestDistance )
		    .toList();
		return bestMatches.size() == 1 ? bestMatches.getFirst() : null;
	}

	private List<String> closeMatches( String actualName, Map<String, String> candidates, int maxDistance ) {
		return candidates.entrySet().stream()
		    .filter( entry -> !entry.getKey().equalsIgnoreCase( actualName ) )
		    .filter( entry -> isCheckable( entry.getValue() ) )
		    .filter( entry -> levenshteinDistance( actualName, entry.getKey() ) <= maxDistance )
		    .map( Map.Entry::getValue )
		    .toList();
	}

	private boolean isInsideStringInterpolation( BoxNode node ) {
		return node instanceof BoxIdentifier && node.getFirstAncestorOfType( BoxStringInterpolation.class ) != null;
	}

	private boolean isKnown( String name ) {
		String lowerName = name.toLowerCase( Locale.ROOT );
		return RESERVED_IDENTIFIERS.contains( lowerName )
		    || visibleIdentifiers.containsKey( lowerName )
		    || fileIdentifiers.containsKey( lowerName )
		    || parentIdentifiers.containsKey( lowerName )
		    || globalIdentifiers.containsKey( lowerName );
	}

	private boolean isCheckable( String name ) {
		return name != null && name.length() >= 3 && name.matches( "[A-Za-z_$][A-Za-z0-9_$]*" );
	}

	private int getDistance( RuleSettings settings, String name, int defaultValue ) {
		if ( settings == null || settings.params == null ) {
			return defaultValue;
		}
		Object value = settings.params.get( name );
		if ( value instanceof Number number ) {
			return Math.max( 0, number.intValue() );
		}
		try {
			return Math.max( 0, Integer.parseInt( value.toString() ) );
		} catch ( Exception e ) {
			return defaultValue;
		}
	}

	private CodeAction createCodeAction( Diagnostic diagnostic, String actualName, String suggestion ) {
		if ( actualName == null || suggestion == null || diagnostic.getRange() == null || filePath == null ) {
			return null;
		}

		CodeAction action = new CodeAction( "Replace '" + actualName + "' with '" + suggestion + "'" );
		action.setKind( CodeActionKind.QuickFix );
		action.setIsPreferred( true );
		action.setDiagnostics( List.of( diagnostic ) );
		action.setEdit( new WorkspaceEdit( Map.of(
		    filePath,
		    List.of( new TextEdit( diagnostic.getRange(), suggestion ) ) ) ) );
		return action;
	}

	private Range rangeFor( IdentifierUse use ) {
		Range range = BLASTTools.positionToRange( use.node().getPosition() );
		if ( use.node() instanceof BoxFunctionInvocation invocation && use.node().getPosition() != null ) {
			int startColumn = range.getStart().getCharacter();
			range.setEnd( new org.eclipse.lsp4j.Position( range.getStart().getLine(), startColumn + invocation.getName().length() ) );
		}
		return range;
	}

	private String propertyName( BoxProperty property ) {
		return BLASTTools.getPropertyName( property ).orElseGet( () -> property.getAllAnnotations().stream()
		    .filter( annotation -> annotation.getValue() == null )
		    .flatMap( annotation -> BLASTTools.getAnnotationName( annotation ).stream() )
		    .findFirst()
		    .orElse( null ) );
	}

	private String getParentName( BoxNode root ) {
		List<BoxAnnotation> annotations = new ArrayList<>();
		if ( root instanceof BoxClass boxClass ) {
			annotations = boxClass.getAnnotations();
		} else if ( root instanceof BoxInterface boxInterface ) {
			annotations = boxInterface.getAllAnnotations();
		}
		return annotations.stream()
		    .filter( annotation -> BLASTTools.getAnnotationName( annotation ).filter( key -> key.equalsIgnoreCase( "extends" ) ).isPresent() )
		    .map( annotation -> BLASTTools.getAnnotationValue( annotation ).orElse( null ) )
		    .filter( value -> value != null )
		    .map( Object::toString )
		    .findFirst()
		    .orElse( null );
	}

	private void addCandidate( Map<String, String> candidates, String name ) {
		String identifier = identifierPart( name );
		if ( isCheckable( identifier ) ) {
			candidates.putIfAbsent( identifier.toLowerCase( Locale.ROOT ), identifier );
		}
	}

	private String identifierPart( String name ) {
		if ( name == null ) {
			return null;
		}
		int dot = name.lastIndexOf( '.' );
		return ( dot >= 0 ? name.substring( dot + 1 ) : name ).trim();
	}

	private String fileName() {
		if ( filePath == null ) {
			return null;
		}
		String	name	= filePath.substring( Math.max( filePath.lastIndexOf( '/' ), filePath.lastIndexOf( '\\' ) ) + 1 );
		int		dot		= name.lastIndexOf( '.' );
		return dot > 0 ? name.substring( 0, dot ) : name;
	}

	private static int levenshteinDistance( String left, String right ) {
		String	a	= left.toLowerCase( Locale.ROOT );
		String	b	= right.toLowerCase( Locale.ROOT );
		if ( a.length() < b.length() ) {
			String swap = a;
			a	= b;
			b	= swap;
		}

		int[]	previous	= new int[ b.length() + 1 ];
		int[]	current		= new int[ b.length() + 1 ];
		for ( int j = 0; j <= b.length(); j++ ) {
			previous[ j ] = j;
		}
		for ( int i = 1; i <= a.length(); i++ ) {
			current[ 0 ] = i;
			for ( int j = 1; j <= b.length(); j++ ) {
				int substitution = previous[ j - 1 ] + ( a.charAt( i - 1 ) == b.charAt( j - 1 ) ? 0 : 1 );
				current[ j ] = Math.min( Math.min( current[ j - 1 ] + 1, previous[ j ] + 1 ), substitution );
			}
			int[] swap = previous;
			previous	= current;
			current		= swap;
		}
		return previous[ b.length() ];
	}
}
