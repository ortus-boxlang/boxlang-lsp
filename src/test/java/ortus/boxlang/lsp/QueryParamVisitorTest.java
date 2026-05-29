package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertThat;

import java.io.IOException;
import java.util.List;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.compiler.parser.CFParser;
import ortus.boxlang.compiler.parser.ParsingResult;
import ortus.boxlang.lsp.workspace.visitors.QueryParamVisitor;
import ortus.boxlang.lsp.workspace.visitors.SQLFeatureExtractor;

public class QueryParamVisitorTest extends BaseTest {

	@DisplayName( "It should find a string literal within a query component" )
	@Test
	public void testFindIssue() throws IOException {
		CFParser			parser	= new CFParser();

		// formatting is significant for the test to work properly
		// @formatter:off
		ParsingResult		pr		= parser.parse( """
		                                            	<cfquery datasource="#thing#">
		                                            		SELECT * FROM items u
		                                            		WHERE u.code = '#paramRef#'
		                                            	</cfquery>
		                                            """, false );
		// @formatter:on

		QueryParamVisitor	visitor	= new QueryParamVisitor();

		visitor.setFilePath( "test" );

		pr.getRoot().accept( visitor );

		List<Diagnostic> diagnosticIssues = visitor.getDiagnostics();
		assertThat( diagnosticIssues.size() ).isEqualTo( 1 );

		Diagnostic diagnostic = diagnosticIssues.getFirst();

		assertThat( diagnostic.getMessage().getLeft() ).isEqualTo( "Possible unescaped query param: #paramRef#" );

		List<CodeAction>	codeActions	= visitor.getCodeActions();

		CodeAction			action		= codeActions.getFirst();

		assertThat( action.getEdit().getChanges().get( "test" ).size() ).isEqualTo( 1 );
		assertThat( action.getEdit().getChanges().get( "test" ).getFirst().getRange().getStart().getLine() ).isEqualTo( 2 );
		assertThat( action.getEdit().getChanges().get( "test" ).getFirst().getRange().getStart().getCharacter() ).isEqualTo( 17 );
		assertThat( action.getEdit().getChanges().get( "test" ).getFirst().getRange().getEnd().getLine() ).isEqualTo( 2 );
		assertThat( action.getEdit().getChanges().get( "test" ).getFirst().getRange().getEnd().getCharacter() ).isEqualTo( 29 );
		assertThat( action.getEdit().getChanges().get( "test" ).getFirst().getNewText() )
		    .isEqualTo( "<cfqueryparam value=\"#paramRef#\">" );
	}

	@DisplayName( "It should identify list contexts" )
	@Test
	public void testListCheck() {
		boolean isList = false;
		isList = SQLFeatureExtractor.isList( "something = " );
		assertThat( isList ).isEqualTo( false );

		isList = SQLFeatureExtractor.isList( "something in (" );
		assertThat( isList ).isEqualTo( true );

		isList = SQLFeatureExtractor.isList( "something in ( " );
		assertThat( isList ).isEqualTo( true );

		isList = SQLFeatureExtractor.isList( "something in ( '" );
		assertThat( isList ).isEqualTo( true );

		isList = SQLFeatureExtractor.isList( "something not in (" );
		assertThat( isList ).isEqualTo( true );

		isList = SQLFeatureExtractor.isList( "something not in ( " );
		assertThat( isList ).isEqualTo( true );

		isList = SQLFeatureExtractor.isList( "something not in ( '" );
		assertThat( isList ).isEqualTo( true );

		isList = SQLFeatureExtractor.isList( "left join ( x.thing = " );
		assertThat( isList ).isEqualTo( false );
	}

	@DisplayName( "It should extract left-side include text" )
	@Test
	public void testGetLeftText() {
		String captured = "";
		captured = SQLFeatureExtractor.getLeftInclude( "WHERE code like '%" );
		assertThat( captured ).isEqualTo( "%" );

		captured = SQLFeatureExtractor.getLeftInclude( "dbo.fn_payloads( 'seed','" );
		assertThat( captured ).isEqualTo( "" );

		captured = SQLFeatureExtractor.getLeftInclude( "' ug.goalyear = " );
		assertThat( captured ).isEqualTo( "" );

		captured = SQLFeatureExtractor.getLeftInclude( "x = 'test' and ug.goalyear = " );
		assertThat( captured ).isEqualTo( "" );

		// formatting is significant for the test to work properly
		// @formatter:off
		captured = SQLFeatureExtractor.getLeftInclude(
		"""
		'-->' + a.b as c
		from d e inner join f a on e.typeID = a.typeID
		where e.scopeID = 
		""" );
		// @formatter:on
		assertThat( captured ).isEqualTo( "" );
	}

	@DisplayName( "It should handle consecutive interpolations" )
	@Test
	public void testTwoInterpolationsInARow() throws IOException {
		CFParser			parser	= new CFParser();
		// formatting is significant for the test to work properly
		// @formatter:off
		ParsingResult		pr		= parser.parse( """
		                                            	<cfquery datasource="#thing#">
		                                            		#val(rtnvar.values)#,#val(rtnvar.recordtoken)#
		                                            	</cfquery>
		                                            """, false );
		// @formatter:on

		QueryParamVisitor	visitor	= new QueryParamVisitor();

		visitor.setFilePath( "test" );

		pr.getRoot().accept( visitor );

		List<Diagnostic> diagnosticIssues = visitor.getDiagnostics();
		assertThat( diagnosticIssues.size() ).isEqualTo( 2 );

		List<CodeAction> codeActions = visitor.getCodeActions();

		assertThat( codeActions.size() ).isGreaterThan( 0 );
		assertThat( codeActions.getFirst().getEdit().getChanges().get( "test" ).getFirst().getRange().getStart().getCharacter() ).isGreaterThan( -1 );
	}

	@DisplayName( "It should find replacement starts" )
	@Test
	public void testGetReplacementStart() {
		int replacementStart = 0;

		replacementStart = SQLFeatureExtractor.getReplacementStartIndex( "N'" );
		assertThat( replacementStart ).isEqualTo( 0 );

		replacementStart = SQLFeatureExtractor.getReplacementStartIndex( "'" );
		assertThat( replacementStart ).isEqualTo( 0 );

		replacementStart = SQLFeatureExtractor.getReplacementStartIndex( "something = " );
		assertThat( replacementStart ).isEqualTo( 12 );

		replacementStart = SQLFeatureExtractor.getReplacementStartIndex( "something = '" );
		assertThat( replacementStart ).isEqualTo( 12 );

		replacementStart = SQLFeatureExtractor.getReplacementStartIndex( "something = N'" );
		assertThat( replacementStart ).isEqualTo( 12 );

		replacementStart = SQLFeatureExtractor.getReplacementStartIndex( "something = '%" );
		assertThat( replacementStart ).isEqualTo( 12 );

		replacementStart = SQLFeatureExtractor.getReplacementStartIndex( "dateDiff( " );
		assertThat( replacementStart ).isEqualTo( 10 );

		replacementStart = SQLFeatureExtractor.getReplacementStartIndex( "dateDiff( getDate(), '" );
		assertThat( replacementStart ).isEqualTo( 21 );

		replacementStart = SQLFeatureExtractor.getReplacementStartIndex( "something = 'blah" );
		assertThat( replacementStart ).isEqualTo( 12 );

		replacementStart = SQLFeatureExtractor.getReplacementStartIndex( "something '%blah" );
		assertThat( replacementStart ).isEqualTo( 10 );

		replacementStart = SQLFeatureExtractor.getReplacementStartIndex( ",something " );
		assertThat( replacementStart ).isEqualTo( 11 );

		replacementStart = SQLFeatureExtractor.getReplacementStartIndex( "<cfif arguments.orderby NEQ \"s.snapid\">,s.snapid " );
		assertThat( replacementStart ).isEqualTo( 49 );

		replacementStart = SQLFeatureExtractor.getReplacementStartIndex( "a." );
		assertThat( replacementStart ).isEqualTo( 2 );
	}

	@DisplayName( "It should prefer operators to single quotes" )
	@Test
	public void testShouldPreferOperatorsToSingleQuotes() {
		int replacementStart = 0;
		// formatting is significant for the test to work properly
		// @formatter:off
		replacementStart = SQLFeatureExtractor.getReplacementStartIndex( """
		SELECT 'something'
		INTO 	
		""" );
		// @formatter:on
		assertThat( replacementStart ).isEqualTo( 23 );
	}

	@DisplayName( "It should find replacement ends" )
	@Test
	public void testGetReplacementEnd() {
		int replacementStart = 0;
		replacementStart = SQLFeatureExtractor.getReplacementEndIndex( "," );
		assertThat( replacementStart ).isEqualTo( 0 );

		replacementStart = SQLFeatureExtractor.getReplacementEndIndex( "%'" );
		assertThat( replacementStart ).isEqualTo( 2 );

		replacementStart = SQLFeatureExtractor.getReplacementEndIndex( "'" );
		assertThat( replacementStart ).isEqualTo( 1 );

		replacementStart = SQLFeatureExtractor.getReplacementEndIndex( "blah'" );
		assertThat( replacementStart ).isEqualTo( 5 );

		replacementStart = SQLFeatureExtractor.getReplacementEndIndex( ")" );
		assertThat( replacementStart ).isEqualTo( 0 );

		replacementStart = SQLFeatureExtractor.getReplacementEndIndex( " )" );
		assertThat( replacementStart ).isEqualTo( 1 );
	}

	@DisplayName( "It should prefer parens to quotes" )
	@Test
	public void testPreferParensToQuotes() {
		int replacementStart = 0;
		replacementStart = SQLFeatureExtractor.getReplacementEndIndex( ") '" );
		assertThat( replacementStart ).isEqualTo( 0 );
	}

	@DisplayName( "It should find NVARCHAR parameters" )
	@Test
	public void testFindIssueNVarchar() throws IOException {
		CFParser			parser	= new CFParser();

		// formatting is significant for the test to work properly
		// @formatter:off
		ParsingResult		pr		= parser.parse( """
		                                            	<cfquery datasource="#thing#">
		                                            		SELECT * FROM items u
		                                            		WHERE u.code = N'#paramRef#'
		                                            	</cfquery>
		                                            """, false );
		// @formatter:on

		QueryParamVisitor	visitor	= new QueryParamVisitor();

		visitor.setFilePath( "test" );

		pr.getRoot().accept( visitor );

		List<Diagnostic> diagnosticIssues = visitor.getDiagnostics();
		assertThat( diagnosticIssues.size() ).isEqualTo( 1 );

		Diagnostic diagnostic = diagnosticIssues.getFirst();

		assertThat( diagnostic.getMessage().getLeft() ).isEqualTo( "Possible unescaped query param: #paramRef#" );

		List<CodeAction>	codeActions	= visitor.getCodeActions();
		CodeAction			action		= codeActions.getFirst();

		assertThat( action.getEdit().getChanges().get( "test" ).size() ).isEqualTo( 1 );
		assertThat( action.getEdit().getChanges().get( "test" ).getFirst().getRange().getStart().getLine() ).isEqualTo( 2 );
		assertThat( action.getEdit().getChanges().get( "test" ).getFirst().getRange().getStart().getCharacter() ).isEqualTo( 17 );
		assertThat( action.getEdit().getChanges().get( "test" ).getFirst().getRange().getEnd().getLine() ).isEqualTo( 2 );
		assertThat( action.getEdit().getChanges().get( "test" ).getFirst().getRange().getEnd().getCharacter() ).isEqualTo( 30 );
		assertThat( action.getEdit().getChanges().get( "test" ).getFirst().getNewText() )
		    .isEqualTo( "<cfqueryparam value=\"#paramRef#\">" );
	}

	@DisplayName( "It should add list=true for IN clauses" )
	@Test
	public void testAddList() throws IOException {
		CFParser			parser	= new CFParser();

		// formatting is significant for the test to work properly
		// @formatter:off
		ParsingResult		pr		= parser.parse( """
			<cfquery datasource="#variables.dsn#" name="qryResult">
				SELECT * FROM dataBlock
				WHERE r.responseid in (#arguments.ids#)
			</cfquery>
		                                            """, false );
		// @formatter:on

		QueryParamVisitor	visitor	= new QueryParamVisitor();

		visitor.setFilePath( "test" );

		pr.getRoot().accept( visitor );

		List<Diagnostic> diagnosticIssues = visitor.getDiagnostics();
		assertThat( diagnosticIssues.size() ).isEqualTo( 1 );

		List<CodeAction> codeActions = visitor.getCodeActions();

		codeActions.stream()
		    .filter( codeAction -> !codeAction.getEdit().getChanges().get( "test" ).getFirst().getNewText().contains( "safe" ) )
		    .forEach( codeAction -> {
			    assertThat( codeAction.getEdit().getChanges().get( "test" ).getFirst().getNewText() ).contains( "list" );
		    } );
	}

	@DisplayName( "It should avoid adding list=true outside IN clauses" )
	@Test
	public void testDontAddList() throws IOException {
		CFParser			parser	= new CFParser();

		// formatting is significant for the test to work properly
		// @formatter:off
		ParsingResult		pr		= parser.parse( """
			<cfquery datasource="#variables.dsn#" name="qryResult">
				SELECT DISTINCT am.ItemID, am.Caption
				FROM	EntityRecords o
						INNER JOIN EdgeLink ot ON o.RefID = ot.RefID AND o.StateKey = ot.StateKey
						LEFT JOIN BridgeNode w
							INNER JOIN Entities am ON w.ItemID = am.ItemID
						ON o.RefID = w.RefID AND w.MapID = o.mapid
				WHERE	o.Del = 0
						AND am.ScopeID = 1
						AND ot.MapID = o.mapid
						AND o.ScopeID = #request.scope.scopeID#
				ORDER BY am.Caption
			</cfquery>
		                                            """, false );
		// @formatter:on

		QueryParamVisitor	visitor	= new QueryParamVisitor();

		visitor.setFilePath( "test" );

		pr.getRoot().accept( visitor );

		List<Diagnostic> diagnosticIssues = visitor.getDiagnostics();
		assertThat( diagnosticIssues.size() ).isEqualTo( 1 );

		List<CodeAction> codeActions = visitor.getCodeActions();

		codeActions.forEach( codeAction -> {
			assertThat( codeAction.getEdit().getChanges().get( "test" ).getFirst().getNewText() ).doesNotContain( "list" );
		} );
	}

	@DisplayName( "It should stop the prefix at commas" )
	@Test
	public void testPrefixEndsAtComma() throws IOException {
		CFParser			parser	= new CFParser();

		// formatting is significant for the test to work properly
		// @formatter:off
		ParsingResult		pr		= parser.parse( """
			<cfquery datasource="#variables.dsn#" name="qryAllMTD">
				select s.scopeid,g.classid,sum(cast(metric as int)) as totalqty1,sum(cast(measure01 as int)) as totalqty02,u.itemID,u.alias,ug.plangoal,sum(s.totals) as totals, sum(s.units) as units, sum(s.idx) as idx
				from dbo.fn_payloads( 'seed','#DateFormat(stamp, "mm-dd-yyyy")#') s
				inner join items u on s.itemID = u.itemID
				inner join scopes g on g.scopeid = s.scopeid
				left join itemplans ug on u.itemID = ug.itemID and ug.yearslot = 5
				where (s.flag = 1 or s.second = 1)
				group by g.classid,s.scopeid,u.itemID,u.alias,ug.plangoal	
				order by g.classid,s.scopeid,u.alias
			</cfquery>
		                                            """, false );
		// @formatter:on

		QueryParamVisitor	visitor	= new QueryParamVisitor();

		visitor.setFilePath( "test" );

		pr.getRoot().accept( visitor );

		List<CodeAction> codeActions = visitor.getCodeActions();

		codeActions.stream()
		    .filter( codeAction -> !codeAction.getEdit().getChanges().get( "test" ).getFirst().getNewText().contains( "safe" ) )
		    .forEach( codeAction -> {
			    assertThat( codeAction.getEdit().getChanges().get( "test" ).getFirst().getRange().getStart().getLine() ).isEqualTo( 2 );
			    assertThat( codeAction.getEdit().getChanges().get( "test" ).getFirst().getRange().getStart().getCharacter() ).isEqualTo( 30 );
			    assertThat( codeAction.getEdit().getChanges().get( "test" ).getFirst().getRange().getEnd().getLine() ).isEqualTo( 2 );
			    assertThat( codeAction.getEdit().getChanges().get( "test" ).getFirst().getRange().getEnd().getCharacter() ).isEqualTo( 65 );
			    assertThat( codeAction.getEdit().getChanges().get( "test" ).getFirst().getNewText() )
			        .contains( "<cfqueryparam value=\"#DateFormat(stamp, \"mm-dd-yyyy\")#\"" );
		    } );
	}

	@DisplayName( "It should handle a trailing quote before a method close" )
	@Test
	public void testEndSingleQuote() throws IOException {
		CFParser			parser	= new CFParser();

		// formatting is significant for the test to work properly
		// @formatter:off
		ParsingResult		pr		= parser.parse( """
			<cfquery datasource="#variables.dsn#" name="x">
				, '#DateFormat(stamp, "mm-dd-yyyy")#') s
			</cfquery>
		                                            """, false );
		// @formatter:on

		QueryParamVisitor	visitor	= new QueryParamVisitor();

		visitor.setFilePath( "test" );

		pr.getRoot().accept( visitor );

		List<CodeAction> codeActions = visitor.getCodeActions();

		codeActions.stream()
		    .filter( codeAction -> !codeAction.getEdit().getChanges().get( "test" ).getFirst().getNewText().contains( "safe" ) )
		    .filter( codeAction -> !codeAction.getEdit().getChanges().get( "test" ).getFirst().getNewText().contains( "sdate" ) )
		    .forEach( codeAction -> {
			    assertThat( codeAction.getEdit().getChanges().get( "test" ).getFirst().getNewText() )
			        .contains( "<cfqueryparam value=\"#DateFormat(stamp, \"mm-dd-yyyy\")#\"" );
		    } );
	}

	@DisplayName( "It should parameterize LIKE values with percent signs" )
	@Test
	public void testFindLike() throws IOException {
		CFParser			parser	= new CFParser();

		// formatting is significant for the test to work properly
		// @formatter:off
		ParsingResult		pr		= parser.parse( """
		                                            	<cfquery datasource="#thing#">
		                                            		SELECT * FROM items
		                                            		WHERE code like '%#paramRef#%'
		                                            	</cfquery>
		                                            """, false );
		// @formatter:on

		QueryParamVisitor	visitor	= new QueryParamVisitor();

		visitor.setFilePath( "test" );

		pr.getRoot().accept( visitor );

		List<Diagnostic> diagnosticIssues = visitor.getDiagnostics();
		assertThat( diagnosticIssues.size() ).isEqualTo( 1 );

		Diagnostic diagnostic = diagnosticIssues.getFirst();

		assertThat( diagnostic.getMessage().getLeft() ).isEqualTo( "Possible unescaped query param: #paramRef#" );

		List<CodeAction>	codeActions	= visitor.getCodeActions();
		CodeAction			action		= codeActions.getFirst();

		assertThat( action.getEdit().getChanges().get( "test" ).size() ).isEqualTo( 1 );
		assertThat( action.getEdit().getChanges().get( "test" ).getFirst().getRange().getStart().getLine() ).isEqualTo( 2 );
		assertThat( action.getEdit().getChanges().get( "test" ).getFirst().getRange().getStart().getCharacter() ).isEqualTo( 18 );
		assertThat( action.getEdit().getChanges().get( "test" ).getFirst().getRange().getEnd().getLine() ).isEqualTo( 2 );
		assertThat( action.getEdit().getChanges().get( "test" ).getFirst().getRange().getEnd().getCharacter() ).isEqualTo( 32 );
		assertThat( action.getEdit().getChanges().get( "test" ).getFirst().getNewText() )
		    .isEqualTo( "<cfqueryparam value=\"%#paramRef#%\" cfsqltype=\"varchar\">" );
	}

	@DisplayName( "It should still parse other query shapes without throwing" )
	@Test
	public void testOther() throws IOException {
		CFParser			parser	= new CFParser();

		ParsingResult		pr		= parser.parse(
		    """
		                                                                                               	<cfquery datasource="#thing#">
		                                                                                               		select 1
		    from entities a
		    where a.caption = '#arguments.caption#' and a.scopeID = '#arguments.destScopeID#'
		    	and a.mapID = '#arguments.destMapID#' and a.del = 0
		                                                                                               	</cfquery>
		                                                                                              """,
		    false );

		QueryParamVisitor	visitor	= new QueryParamVisitor();

		visitor.setFilePath( "test" );

		pr.getRoot().accept( visitor );
		assertThat( visitor.getDiagnostics() ).isNotEmpty();
	}

	@DisplayName( "It should not keep the pound on the right side" )
	@Test
	public void testDoesntKeepPoundOnRightSide() throws IOException {
		CFParser			parser	= new CFParser();

		// formatting is significant for the test to work properly
		// @formatter:off
		ParsingResult		pr		= parser.parse(
		    """
				<cfquery datasource="#thing#">
					select 1 from seed
			    		where o.mapID = #val(arguments.mapID)# and o.refID = #val(arguments.refID)#
				</cfquery>
			""",
		    false
		);
		// @formatter:on
		QueryParamVisitor	visitor	= new QueryParamVisitor();

		visitor.setFilePath( "test" );

		pr.getRoot().accept( visitor );

		CodeAction action = visitor.getCodeActions().stream().filter( ca -> {
			return ca.getEdit().getChanges().get( "test" ).getFirst().getNewText().contains( "arguments.refID" )
			    && ca.getEdit().getChanges().get( "test" ).getFirst().getNewText().contains( "varchar" );
		} ).findFirst().get();

		assertThat( action.getEdit().getChanges().get( "test" ).getFirst().getRange().getEnd().getCharacter() ).isEqualTo( 81 );
	}

	@DisplayName( "It should not keep the left quote after select" )
	@Test
	public void testDoesntKeepLeftQuoteAfterSelect() throws IOException {
		CFParser			parser	= new CFParser();

		// formatting is significant for the test to work properly
		// @formatter:off
		ParsingResult		pr		= parser.parse(
		    """
				<cfquery datasource="#thing#">
					select
					<!--- Will aggregate across all groups but in case they want this distinction in the future, leaving this code commented out --->
				<!---			g.scopeid,
						g.labeltext,
						g.classid,
						gt.folder,--->
						DATEDIFF(d, '#dateformat(arguments.stamp,'mm/dd/yyyy')#', eventday) >= 0

					<!---group by g.scopeid,g.classid,g.labeltext,gt.folder --->
				<!---			order by case when g.classid = 3 then 1 when g.classid = 2 then 2 else 3 end asc,g.labeltext--->
				</cfquery>
			""",
		    false
		);
		// @formatter:on
		QueryParamVisitor	visitor	= new QueryParamVisitor();

		visitor.setFilePath( "test" );

		pr.getRoot().accept( visitor );

		CodeAction action = visitor.getCodeActions().stream().filter( ca -> {
			return ca.getEdit().getChanges().get( "test" ).getFirst().getNewText().contains( "arguments.stamp" )
			    && ca.getEdit().getChanges().get( "test" ).getFirst().getNewText().contains( "varchar" );
		} ).findFirst().get();

		assertThat( action.getEdit().getChanges().get( "test" ).getFirst().getRange().getStart().getCharacter() ).isEqualTo( 15 );
	}
}