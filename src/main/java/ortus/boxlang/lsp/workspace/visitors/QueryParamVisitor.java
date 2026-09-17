package ortus.boxlang.lsp.workspace.visitors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;

import ortus.boxlang.compiler.ast.BoxNode;
import ortus.boxlang.compiler.ast.expression.BoxFunctionInvocation;
import ortus.boxlang.compiler.ast.expression.BoxStringInterpolation;
import ortus.boxlang.compiler.ast.statement.BoxAnnotation;
import ortus.boxlang.compiler.ast.statement.component.BoxComponent;
import ortus.boxlang.lsp.SourceCodeVisitor;
import ortus.boxlang.lsp.lint.DiagnosticRuleRegistry;
import ortus.boxlang.lsp.lint.LintConfigLoader;
import ortus.boxlang.lsp.lint.rules.MissingQueryParamCfsqltypeRule;
import ortus.boxlang.lsp.lint.rules.UnescapedQueryParamRule;
import ortus.boxlang.lsp.workspace.BLASTTools;
import ortus.boxlang.lsp.workspace.ProjectContextProvider;
import ortus.boxlang.runtime.types.QueryColumnType;

public class QueryParamVisitor extends SourceCodeVisitor {

	private static final Pattern						MARKED_SAFE_PATTERN				= Pattern.compile( "/\\*\\s*safe\\s*\\*/", Pattern.CASE_INSENSITIVE );
	private static final Pattern						OPERATOR_VALUE_PATTERN_RIGHT	= Pattern.compile( "^(%)*(['\"])", Pattern.CASE_INSENSITIVE );

	private final List<Diagnostic>						diagnostics						= new ArrayList<>();
	private final List<CodeAction>						codeActions						= new ArrayList<>();
	private final Map<String, Map<String, CodeAction>>	sqlTypeRefactors				= new HashMap<>();

	private CodeAction									refactorAll;

	private record OperatorContext(
	    boolean list,
	    int replacementRangeLeftOffset,
	    int replacementRangeRightOffset,
	    String includeLeft,
	    String includeRight,
	    String table,
	    String column ) {
	}

	@Override
	public List<CodeAction> getCodeActions() {
		List<CodeAction> enabledActions = new ArrayList<>();

		for ( CodeAction action : codeActions ) {
			if ( action.getDiagnostics() == null || action.getDiagnostics().isEmpty() ) {
				enabledActions.add( action );
				continue;
			}

			List<Diagnostic> enabledDiagnostics = action.getDiagnostics().stream()
			    .filter( this::isDiagnosticEnabled )
			    .toList();

			if ( enabledDiagnostics.isEmpty() ) {
				continue;
			}

			action.setDiagnostics( new ArrayList<>( enabledDiagnostics ) );
			enabledActions.add( action );
		}

		return enabledActions;
	}

	@Override
	public List<Diagnostic> getDiagnostics() {
		return diagnostics.stream()
		    .filter( this::isDiagnosticEnabled )
		    .peek( this::applyConfiguredSeverity )
		    .toList();
	}

	@Override
	public void visit( BoxComponent node ) {
		if ( node.getName().equalsIgnoreCase( "query" ) ) {
			node.getBody().forEach( this::checkNode );
		} else if ( node.getName().equalsIgnoreCase( "queryparam" ) ) {
			diagnoseQueryParam( node );
		}

		super.visit( node );
	}

	private void diagnoseQueryParam( BoxComponent node ) {
		Optional<BoxAnnotation>	valueAttr			= node.getAttributes().stream()
		    .filter( annotation -> BLASTTools.getAnnotationName( annotation )
		        .filter( name -> name.equalsIgnoreCase( "value" ) )
		        .isPresent() )
		    .findFirst();
		Optional<BoxAnnotation>	cfSQLTypeAttribute	= node.getAttributes().stream()
		    .filter( annotation -> BLASTTools.getAnnotationName( annotation )
		        .filter( name -> name.equalsIgnoreCase( "cfsqltype" ) )
		        .isPresent() )
		    .findFirst();

		if ( cfSQLTypeAttribute.isPresent() ) {
			return;
		}

		String		valueText	= valueAttr.flatMap( BLASTTools::getAnnotationValue )
		    .orElse( "query parameter" );
		Diagnostic	diagnostic	= new Diagnostic(
		    ProjectContextProvider.positionToRange( node.getPosition() ),
		    "Missing cfsqltype attribute: " + valueText,
		    DiagnosticSeverity.Warning,
		    "boxlang",
		    MissingQueryParamCfsqltypeRule.ID );
		diagnostic.setData( Map.of( "id", UUID.randomUUID().toString() ) );

		diagnostics.add( diagnostic );
		addParamSqlTypeRefactoring( node, diagnostic );
	}

	private void addParamSqlTypeRefactoring( BoxNode node, Diagnostic diagnostic ) {
		Optional<String> sourceText = BLASTTools.getSourceText( node );
		if ( sourceText.isEmpty() ) {
			return;
		}

		sqlTypeRefactors.computeIfAbsent( sourceText.get(), ignored -> new HashMap<>() );

		for ( QueryColumnType columnType : QueryColumnType.values() ) {
			createSpecificSQLTypeRefactoring( "cf_sql_" + columnType.name().toLowerCase(), node, diagnostic );
		}

		createSpecificSQLTypeRefactoring( "cf_sql_nvarchar", node, diagnostic );
		createSpecificSQLTypeRefactoring( "cf_sql_numeric", node, diagnostic );
	}

	private void createSpecificSQLTypeRefactoring( String type, BoxNode node, Diagnostic diagnostic ) {
		Optional<String> sourceText = BLASTTools.getSourceText( node );
		if ( sourceText.isEmpty() ) {
			return;
		}
		String nodeSourceText = sourceText.get();

		if ( !sqlTypeRefactors.get( nodeSourceText ).containsKey( type ) ) {
			CodeAction action = new CodeAction( "Refactor similar as: " + type );
			action.setEdit( new WorkspaceEdit( new HashMap<>() ) );
			action.getEdit().getChanges().put( filePath, new ArrayList<>() );
			action.setKind( CodeActionKind.RefactorRewrite );
			action.setDiagnostics( new ArrayList<>() );
			codeActions.add( action );
			sqlTypeRefactors.get( nodeSourceText ).put( type, action );
		}

		TextEdit edit = new TextEdit(
		    ProjectContextProvider.positionToRange( node.getPosition() ),
		    nodeSourceText.replaceAll( ">$", " cfsqltype=\"" + type + "\">" ) );

		sqlTypeRefactors.get( nodeSourceText ).get( type ).getDiagnostics().add( diagnostic );
		sqlTypeRefactors.get( nodeSourceText ).get( type ).getEdit().getChanges().get( filePath ).add( edit );
	}

	private void checkNode( BoxNode node ) {
		if ( node instanceof BoxStringInterpolation ) {
			if ( node.getFirstAncestorOfType( BoxComponent.class, candidate -> candidate.getName().equalsIgnoreCase( "queryparam" ) ) == null ) {
				if ( shouldIgnore( node ) ) {
					node.getChildren().forEach( this::checkNode );
					return;
				}

				Diagnostic diagnostic = new Diagnostic(
				    ProjectContextProvider.positionToRange( node.getPosition() ),
				    BLASTTools.getSourceText( node )
				        .map( text -> "Possible unescaped query param: " + text )
				        .orElse( "Possible unescaped query param" ),
				    DiagnosticSeverity.Warning,
				    "boxlang",
				    UnescapedQueryParamRule.ID );
				diagnostic.setData( Map.of( "id", UUID.randomUUID().toString() ) );
				diagnostics.add( diagnostic );

				createQueryParamCodeAction( node, diagnostic ).ifPresent( codeActions::add );
				addRefactoring( node, diagnostic );
				addRefactorSimilarStringInterpolation( node, diagnostic );
				codeActions.add( createMarkSafeCodeAction( node, diagnostic ) );
			}
		} else if ( ! ( node instanceof BoxComponent component && component.getName().equalsIgnoreCase( "queryparam" ) ) ) {
			node.getChildren().forEach( this::checkNode );
		}
	}

	private void addRefactorSimilarStringInterpolation( BoxNode node, Diagnostic diagnostic ) {
		Optional<String> sourceText = BLASTTools.getSourceText( node );
		if ( sourceText.isEmpty() ) {
			return;
		}

		sqlTypeRefactors.computeIfAbsent( sourceText.get(), ignored -> new HashMap<>() );

		for ( QueryColumnType columnType : QueryColumnType.values() ) {
			createSpecificSqlTypeInterpolationRefactoring( "cf_sql_" + columnType.name().toLowerCase(), node, diagnostic );
		}

		createSpecificSqlTypeInterpolationRefactoring( "cf_sql_nvarchar", node, diagnostic );
		createSpecificSqlTypeInterpolationRefactoring( "cf_sql_numeric", node, diagnostic );
	}

	private void createSpecificSqlTypeInterpolationRefactoring( String type, BoxNode node, Diagnostic diagnostic ) {
		Optional<String> sourceText = BLASTTools.getSourceText( node );
		if ( sourceText.isEmpty() ) {
			return;
		}
		String		nodeSourceText	= sourceText.get();
		TextEdit	edit			= createGenericTextEdit( node, type );
		if ( edit == null ) {
			return;
		}

		if ( !sqlTypeRefactors.get( nodeSourceText ).containsKey( type ) ) {
			CodeAction action = new CodeAction( "Refactor similar as: " + type );
			action.setEdit( new WorkspaceEdit( new HashMap<>() ) );
			action.getEdit().getChanges().put( filePath, new ArrayList<>() );
			action.setKind( CodeActionKind.RefactorRewrite );
			action.setDiagnostics( new ArrayList<>() );
			codeActions.add( action );
			sqlTypeRefactors.get( nodeSourceText ).put( type, action );
		}

		sqlTypeRefactors.get( nodeSourceText ).get( type ).getDiagnostics().add( diagnostic );
		sqlTypeRefactors.get( nodeSourceText ).get( type ).getEdit().getChanges().get( filePath ).add( edit );
	}

	private boolean shouldIgnore( BoxNode node ) {
		return BLASTTools.getSourceText( node ).map( text -> MARKED_SAFE_PATTERN.matcher( text ).find() ).orElse( false );
	}

	private void addRefactoring( BoxNode node, Diagnostic diagnostic ) {
		TextEdit edit = createTextEdit( node );
		if ( edit == null ) {
			return;
		}

		if ( refactorAll == null ) {
			refactorAll = new CodeAction( "Refactor all unescaped query params" );
			refactorAll.setEdit( new WorkspaceEdit( new HashMap<>() ) );
			refactorAll.getEdit().getChanges().put( filePath, new ArrayList<>() );
			refactorAll.setKind( CodeActionKind.RefactorRewrite );
			refactorAll.setDiagnostics( new ArrayList<>() );
			codeActions.add( refactorAll );
		}

		refactorAll.getDiagnostics().add( diagnostic );
		refactorAll.getEdit().getChanges().get( filePath ).add( edit );
	}

	private Optional<CodeAction> createQueryParamCodeAction( BoxNode node, Diagnostic diagnostic ) {
		WorkspaceEdit edit = createWorkspaceEdit( node );
		if ( edit == null ) {
			return Optional.empty();
		}

		CodeAction action = new CodeAction( BLASTTools.getSourceText( node )
		    .map( text -> "Parameterize " + text )
		    .orElse( "Parameterize query parameter" ) );
		action.setKind( CodeActionKind.QuickFix );
		action.setDiagnostics( List.of( diagnostic ) );
		action.setIsPreferred( true );
		action.setEdit( edit );

		return Optional.of( action );
	}

	private CodeAction createMarkSafeCodeAction( BoxNode node, Diagnostic diagnostic ) {
		CodeAction action = new CodeAction( BLASTTools.getSourceText( node )
		    .map( text -> "Mark as safe " + text )
		    .orElse( "Mark query parameter as safe" ) );
		action.setKind( CodeActionKind.QuickFix );
		action.setDiagnostics( List.of( diagnostic ) );

		Range range = ProjectContextProvider.positionToRange( node.getPosition() );
		range.getEnd().setLine( range.getStart().getLine() );
		range.getEnd().setCharacter( range.getStart().getCharacter() + 1 );

		Map<String, List<TextEdit>> edits = new HashMap<>();
		edits.put( filePath, List.of( new TextEdit( range, "#/*safe*/" ) ) );

		action.setEdit( new WorkspaceEdit( edits ) );
		return action;
	}

	private WorkspaceEdit createWorkspaceEdit( BoxNode node ) {
		TextEdit edit = createTextEdit( node );
		if ( edit == null ) {
			return null;
		}

		Map<String, List<TextEdit>> edits = new HashMap<>();
		edits.put( filePath, List.of( edit ) );
		return new WorkspaceEdit( edits );
	}

	private TextEdit createTextEdit( BoxNode node ) {
		Optional<String>	nodeSourceText	= BLASTTools.getSourceText( node );
		Optional<String>	leftSourceText	= findLeftText( node );
		Optional<String>	rightSourceText	= findRightText( node );
		if ( nodeSourceText.isEmpty() || leftSourceText.isEmpty() || rightSourceText.isEmpty() ) {
			return null;
		}

		Range			editRange		= ProjectContextProvider.positionToRange( node.getPosition() );
		OperatorContext	operatorContext	= fallbackOperatorContext( leftSourceText.get(), rightSourceText.get() );
		if ( operatorContext == null ) {
			return null;
		}

		editRange.getStart().setCharacter( editRange.getStart().getCharacter() - operatorContext.replacementRangeLeftOffset );
		editRange.getEnd().setCharacter( editRange.getEnd().getCharacter() + operatorContext.replacementRangeRightOffset );

		String replacementValue = operatorContext.includeLeft + nodeSourceText.get() + operatorContext.includeRight;
		return new TextEdit( editRange, getEditText( replacementValue, determineSQLType( node, operatorContext ), operatorContext.list ) );
	}

	private Optional<String> findLeftText( BoxNode node ) {
		BoxNode parent = node.getParent();

		while ( ! ( parent instanceof BoxComponent component && component.getName().equalsIgnoreCase( "query" ) ) ) {
			BoxNode	grandParent		= parent.getParent();
			int		thisNodeIndex	= grandParent.getChildren().indexOf( parent );

			if ( thisNodeIndex > 0 ) {
				List<BoxNode> children = grandParent.getChildren().get( thisNodeIndex - 1 ).getChildren();
				if ( !children.isEmpty() ) {
					return BLASTTools.getSourceText( children.getLast() );
				}
			}

			parent = grandParent;
		}

		return Optional.of( "" );
	}

	private Optional<String> findRightText( BoxNode node ) {
		BoxNode parent = node.getParent();

		while ( ! ( parent instanceof BoxComponent component && component.getName().equalsIgnoreCase( "query" ) ) ) {
			BoxNode	grandParent		= parent.getParent();
			int		thisNodeIndex	= grandParent.getChildren().indexOf( parent );

			if ( thisNodeIndex < grandParent.getChildren().size() - 1 ) {
				List<BoxNode> children = grandParent.getChildren().get( thisNodeIndex + 1 ).getChildren();
				if ( !children.isEmpty() ) {
					return BLASTTools.getSourceText( children.getFirst() );
				}
			}

			parent = grandParent;
		}

		return Optional.of( "" );
	}

	private TextEdit createGenericTextEdit( BoxNode node, String sqlType ) {
		Optional<String>	nodeSourceText	= BLASTTools.getSourceText( node );
		Optional<String>	leftSourceText	= findLeftText( node );
		Optional<String>	rightSourceText	= findRightText( node );
		if ( nodeSourceText.isEmpty() || leftSourceText.isEmpty() || rightSourceText.isEmpty() ) {
			return null;
		}

		Range			editRange		= ProjectContextProvider.positionToRange( node.getPosition() );
		OperatorContext	operatorContext	= fallbackOperatorContext( leftSourceText.get(), rightSourceText.get() );
		if ( operatorContext == null ) {
			return null;
		}
		editRange.getStart().setCharacter( editRange.getStart().getCharacter() - operatorContext.replacementRangeLeftOffset );
		editRange.getEnd().setCharacter( editRange.getEnd().getCharacter() + operatorContext.replacementRangeRightOffset );

		String replacementValue = operatorContext.includeLeft + nodeSourceText.get() + operatorContext.includeRight;
		return new TextEdit( editRange, getEditText( replacementValue, sqlType, operatorContext.list ) );
	}

	private String getEditText( String sourceText, String sqlType, boolean isList ) {
		StringBuilder param = new StringBuilder();
		param.append( "<cfqueryparam value=\"" ).append( sourceText ).append( "\"" );

		if ( isList ) {
			param.append( " list=\"true\"" );
		}

		if ( sqlType != null ) {
			param.append( " cfsqltype=\"" ).append( sqlType ).append( "\"" );
		}

		param.append( ">" );
		return param.toString();
	}

	private boolean isDiagnosticEnabled( Diagnostic diagnostic ) {
		String ruleId = getRuleId( diagnostic );
		return ruleId == null || DiagnosticRuleRegistry.getInstance().isEnabled( ruleId, true );
	}

	private void applyConfiguredSeverity( Diagnostic diagnostic ) {
		String ruleId = getRuleId( diagnostic );
		if ( ruleId == null ) {
			return;
		}

		var settings = LintConfigLoader.get().forRule( ruleId );
		if ( settings != null ) {
			diagnostic.setSeverity( settings.toSeverityOr( DiagnosticSeverity.Warning ) );
		}
	}

	private String getRuleId( Diagnostic diagnostic ) {
		return diagnostic.getCode() != null ? diagnostic.getCode().getLeft() : null;
	}

	private String determineSQLType( BoxNode node, OperatorContext operatorContext ) {
		List<BoxFunctionInvocation> descendants = node.getDescendantsOfType( BoxFunctionInvocation.class );

		if ( descendants.size() == 1 && descendants.getFirst().getName().toLowerCase().matches( "datetime" ) ) {
			return "timestamp";
		}
		if ( descendants.size() == 1 && descendants.getFirst().getName().toLowerCase().matches( "date" ) ) {
			return "date";
		}
		if ( operatorContext.includeLeft.contains( "%" ) ) {
			return "varchar";
		}

		return null;
	}

	private OperatorContext fallbackOperatorContext( String preText, String postText ) {
		if ( preText == null || postText == null ) {
			return null;
		}

		String	includeRight	= "";
		Matcher	matcher			= OPERATOR_VALUE_PATTERN_RIGHT.matcher( postText );
		if ( matcher.find() ) {
			includeRight = matcher.group( 1 ) != null ? matcher.group( 1 ) : "";
		}

		int	startIndex					= SQLFeatureExtractor.getReplacementStartIndex( preText );
		int	endIndex					= SQLFeatureExtractor.getReplacementEndIndex( postText );

		int	replacementRangeLeftOffset	= startIndex == -1 ? 0 : preText.length() - startIndex;
		int	replacementRangeRightOffset	= endIndex == -1 ? 0 : endIndex;

		return new OperatorContext(
		    SQLFeatureExtractor.isList( preText ),
		    replacementRangeLeftOffset,
		    replacementRangeRightOffset,
		    SQLFeatureExtractor.getLeftInclude( preText ),
		    includeRight,
		    null,
		    null );
	}
}