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
import ortus.boxlang.lsp.workspace.ProjectContextProvider;
import ortus.boxlang.runtime.types.QueryColumnType;

public class QueryParamVisitor extends SourceCodeVisitor {

	private static final Pattern						MARKED_SAFE_PATTERN				= Pattern.compile( "/\\*\\s*safe\\s*\\*/", Pattern.CASE_INSENSITIVE );
	private static final Pattern						OPERATOR_VALUE_PATTERN_RIGHT	= Pattern.compile( "^(%)*(')", Pattern.CASE_INSENSITIVE );

	private final List<Diagnostic>						diagnostics						= new ArrayList<>();
	private final List<CodeAction>						codeActions						= new ArrayList<>();
	private final Map<String, Map<String, CodeAction>>	sqlTypeRefactors				= new HashMap<>();

	private CodeAction									refactorAll;
	private QueryInfoExtractor							queryInfo;

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
		return codeActions;
	}

	@Override
	public List<Diagnostic> getDiagnostics() {
		return diagnostics;
	}

	public void visit( BoxComponent node ) {
		if ( node.getName().equalsIgnoreCase( "query" ) ) {
			queryInfo = new QueryInfoExtractor( node );
			node.getBody().forEach( this::checkNode );
		} else if ( node.getName().equalsIgnoreCase( "queryparam" ) ) {
			diagnoseQueryParam( node );
		}

		super.visit( node );
	}

	private void diagnoseQueryParam( BoxComponent node ) {
		Optional<BoxAnnotation>	valueAttr			= node.getAttributes().stream()
		    .filter( annotation -> annotation.getKey().getSourceText().equalsIgnoreCase( "value" ) )
		    .findFirst();
		Optional<BoxAnnotation>	cfSQLTypeAttribute	= node.getAttributes().stream()
		    .filter( annotation -> annotation.getKey().getSourceText().equalsIgnoreCase( "cfsqltype" ) )
		    .findFirst();

		if ( cfSQLTypeAttribute.isPresent() ) {
			return;
		}

		Diagnostic diagnostic = new Diagnostic(
		    ProjectContextProvider.positionToRange( node.getPosition() ),
		    "Missing cfsqltype attribute: " + valueAttr.map( value -> value.getSourceText() ).orElse( node.getSourceText() ),
		    DiagnosticSeverity.Warning,
		    "boxlang" );
		diagnostic.setData( Map.of( "id", UUID.randomUUID().toString() ) );

		diagnostics.add( diagnostic );
		addParamSqlTypeRefactoring( node, diagnostic );
	}

	private void addParamSqlTypeRefactoring( BoxNode node, Diagnostic diagnostic ) {
		sqlTypeRefactors.computeIfAbsent( node.getSourceText(), ignored -> new HashMap<>() );

		for ( QueryColumnType columnType : QueryColumnType.values() ) {
			createSpecificSQLTypeRefactoring( "cf_sql_" + columnType.name().toLowerCase(), node, diagnostic );
		}

		createSpecificSQLTypeRefactoring( "cf_sql_nvarchar", node, diagnostic );
		createSpecificSQLTypeRefactoring( "cf_sql_numeric", node, diagnostic );
	}

	private void createSpecificSQLTypeRefactoring( String type, BoxNode node, Diagnostic diagnostic ) {
		if ( !sqlTypeRefactors.get( node.getSourceText() ).containsKey( type ) ) {
			CodeAction action = new CodeAction( "Refactor similar as: " + type );
			action.setEdit( new WorkspaceEdit( new HashMap<>() ) );
			action.getEdit().getChanges().put( filePath, new ArrayList<>() );
			action.setKind( CodeActionKind.RefactorRewrite );
			action.setDiagnostics( new ArrayList<>() );
			codeActions.add( action );
			sqlTypeRefactors.get( node.getSourceText() ).put( type, action );
		}

		TextEdit edit = new TextEdit(
		    ProjectContextProvider.positionToRange( node.getPosition() ),
		    node.getSourceText().replaceAll( ">$", " cfsqltype=\"" + type + "\">" ) );

		sqlTypeRefactors.get( node.getSourceText() ).get( type ).getDiagnostics().add( diagnostic );
		sqlTypeRefactors.get( node.getSourceText() ).get( type ).getEdit().getChanges().get( filePath ).add( edit );
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
				    "Possible unescaped query param: " + node.getSourceText(),
				    DiagnosticSeverity.Warning,
				    "boxlang" );
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
		sqlTypeRefactors.computeIfAbsent( node.getSourceText(), ignored -> new HashMap<>() );

		for ( QueryColumnType columnType : QueryColumnType.values() ) {
			createSpecificSqlTypeInterpolationRefactoring( "cf_sql_" + columnType.name().toLowerCase(), node, diagnostic );
		}

		createSpecificSqlTypeInterpolationRefactoring( "cf_sql_nvarchar", node, diagnostic );
		createSpecificSqlTypeInterpolationRefactoring( "cf_sql_numeric", node, diagnostic );
	}

	private void createSpecificSqlTypeInterpolationRefactoring( String type, BoxNode node, Diagnostic diagnostic ) {
		if ( !sqlTypeRefactors.get( node.getSourceText() ).containsKey( type ) ) {
			CodeAction action = new CodeAction( "Refactor similar as: " + type );
			action.setEdit( new WorkspaceEdit( new HashMap<>() ) );
			action.getEdit().getChanges().put( filePath, new ArrayList<>() );
			action.setKind( CodeActionKind.RefactorRewrite );
			action.setDiagnostics( new ArrayList<>() );
			codeActions.add( action );
			sqlTypeRefactors.get( node.getSourceText() ).put( type, action );
		}

		sqlTypeRefactors.get( node.getSourceText() ).get( type ).getDiagnostics().add( diagnostic );
		sqlTypeRefactors.get( node.getSourceText() ).get( type ).getEdit().getChanges().get( filePath )
		    .add( createGenericTextEdit( node, type ) );
	}

	private boolean shouldIgnore( BoxNode node ) {
		return MARKED_SAFE_PATTERN.matcher( node.getSourceText() ).find();
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

		CodeAction action = new CodeAction( "Parameterize " + node.getSourceText() );
		action.setKind( CodeActionKind.QuickFix );
		action.setDiagnostics( List.of( diagnostic ) );
		action.setIsPreferred( true );
		action.setEdit( edit );

		return Optional.of( action );
	}

	private CodeAction createMarkSafeCodeAction( BoxNode node, Diagnostic diagnostic ) {
		CodeAction action = new CodeAction( "Mark as safe " + node.getSourceText() );
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
		Range			editRange		= ProjectContextProvider.positionToRange( node.getPosition() );
		String			leftSourceText	= findLeftText( node );
		String			rightSourceText	= findRightText( node );

		OperatorContext	operatorContext	= fallbackOperatorContext( leftSourceText, rightSourceText );
		if ( operatorContext == null ) {
			return null;
		}

		editRange.getStart().setCharacter( editRange.getStart().getCharacter() - operatorContext.replacementRangeLeftOffset );
		editRange.getEnd().setCharacter( editRange.getEnd().getCharacter() + operatorContext.replacementRangeRightOffset );

		String replacementValue = operatorContext.includeLeft + node.getSourceText() + operatorContext.includeRight;
		return new TextEdit( editRange, getEditText( replacementValue, determineSQLType( node, operatorContext ), operatorContext.list ) );
	}

	private String findLeftText( BoxNode node ) {
		BoxNode parent = node.getParent();

		while ( ! ( parent instanceof BoxComponent component && component.getName().equalsIgnoreCase( "query" ) ) ) {
			BoxNode	grandParent		= parent.getParent();
			int		thisNodeIndex	= grandParent.getChildren().indexOf( parent );

			if ( thisNodeIndex > 0 ) {
				List<BoxNode> children = grandParent.getChildren().get( thisNodeIndex - 1 ).getChildren();
				if ( !children.isEmpty() ) {
					return children.getLast().getSourceText();
				}
			}

			parent = grandParent;
		}

		return "";
	}

	private String findRightText( BoxNode node ) {
		BoxNode parent = node.getParent();

		while ( ! ( parent instanceof BoxComponent component && component.getName().equalsIgnoreCase( "query" ) ) ) {
			BoxNode	grandParent		= parent.getParent();
			int		thisNodeIndex	= grandParent.getChildren().indexOf( parent );

			if ( thisNodeIndex < grandParent.getChildren().size() - 1 ) {
				List<BoxNode> children = grandParent.getChildren().get( thisNodeIndex + 1 ).getChildren();
				if ( !children.isEmpty() ) {
					return children.getFirst().getSourceText();
				}
			}

			parent = grandParent;
		}

		return "";
	}

	private TextEdit createGenericTextEdit( BoxNode node, String sqlType ) {
		Range			editRange		= ProjectContextProvider.positionToRange( node.getPosition() );
		String			leftSourceText	= findLeftText( node );
		String			rightSourceText	= findRightText( node );

		OperatorContext	operatorContext	= fallbackOperatorContext( leftSourceText, rightSourceText );
		editRange.getStart().setCharacter( editRange.getStart().getCharacter() - operatorContext.replacementRangeLeftOffset );
		editRange.getEnd().setCharacter( editRange.getEnd().getCharacter() + operatorContext.replacementRangeRightOffset );

		String replacementValue = operatorContext.includeLeft + node.getSourceText() + operatorContext.includeRight;
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