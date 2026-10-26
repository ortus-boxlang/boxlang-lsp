package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.FoldingRangeKind;
import org.eclipse.lsp4j.FoldingRangeRequestParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import ortus.boxlang.lsp.formatting.FormattingCapabilityCoordinator;
import ortus.boxlang.lsp.workspace.ProjectContextProvider;

class FoldingRangeTest extends BaseTest {

	@TempDir
	Path										tempDir;

	private final BoxLangTextDocumentService	service	= new BoxLangTextDocumentService();

	@Test
	void advertisesFoldingRangeSupport() throws Exception {
		LanguageServer		server	= new LanguageServer( new BoxLangWorkspaceService(), service,
		    mock( ProjectContextProvider.class ), mock( FormattingCapabilityCoordinator.class ) );
		InitializeParams	params	= new InitializeParams();
		params.setCapabilities( new ClientCapabilities() );
		params.setWorkspaceFolders( List.of() );

		var capability = server.initialize( params ).get( 5, TimeUnit.SECONDS ).getCapabilities().getFoldingRangeProvider();

		assertThat( capability ).isNotNull();
		assertThat( capability.isRight() || Boolean.TRUE.equals( capability.getLeft() ) ).isTrue();
	}

	@Test
	void foldsClassFunctionsAndNestedBlocksWithoutCrossing() throws Exception {
		assertThat( folds( "Test.bx", """
		                              class {
		                                  function first() {
		                                      if ( ready ) {
		                                          return 1;
		                                      }
		                                  }
		                                  function second() {
		                                      return 2;
		                                  }
		                              }
		                              """ ) ).containsExactly( new FoldingRange( 0, 9 ), new FoldingRange( 1, 5 ), new FoldingRange( 2, 4 ),
		    new FoldingRange( 6, 8 ) );
	}

	@Test
	void startsFoldAtOpeningBrace() throws Exception {
		assertThat( folds( "test.bxs", "function example()\n{\n    return 1;\n}\n" ) )
		    .containsExactly( new FoldingRange( 1, 3 ) );
	}

	// The "{}" default value comes before the body's brace, so the fold must start at the body's "{" on line 3.
	@Test
	void startsFoldAtBodyBraceAfterMultilineSignature() throws Exception {
		assertThat( folds( "test.bxs", """
		                               function example(
		                                   a,
		                                   b = {}
		                               ) {
		                                   return 1;
		                               }
		                               """ ) ).containsExactly( new FoldingRange( 3, 5 ) );
	}

	@Test
	void startsClassFoldAtOpeningBraceOnNextLine() throws Exception {
		assertThat( folds( "Test.bx", """
		                              class extends="Base"
		                              {
		                                  x = 1;
		                              }
		                              """ ) ).containsExactly( new FoldingRange( 1, 3 ) );
	}

	// A fold whose closing-brace line starts another fold ends one line early, so collapsing it keeps "} else {" visible.
	// Prevents the if statement block from getting a FoldingRange from 0-2, which would cause it to fold the else block in it.
	@Test
	void foldsIfAndElseBodiesSeparately() throws Exception {
		assertThat( folds( "test.bxs", """
		                               if ( ready ) {
		                                   writeOutput( 1 );
		                               } else {
		                                   writeOutput( 2 );
		                               }
		                               """ ) ).containsExactly( new FoldingRange( 0, 1 ), new FoldingRange( 2, 4 ) );
	}

	@Test
	void foldsElseIfChainWithoutSharingLines() throws Exception {
		assertThat( folds( "test.bxs", """
		                               if ( first ) {
		                                   writeOutput( 1 );
		                               } else if ( second ) {
		                                   writeOutput( 2 );
		                               } else {
		                                   writeOutput( 3 );
		                               }
		                               """ ) ).containsExactly( new FoldingRange( 0, 1 ), new FoldingRange( 2, 3 ), new FoldingRange( 4, 6 ) );
	}

	// A braceless else gets no fold of its own, but "} else" still continues the if, so collapsing the if must keep that line visible.
	@Test
	void endsIfFoldBeforeBracelessElse() throws Exception {
		assertThat( folds( "test.bxs", """
		                               if ( ready ) {
		                                   writeOutput( 1 );
		                               } else
		                                   writeOutput( 2 );
		                               """ ) ).containsExactly( new FoldingRange( 0, 1 ) );
	}

	@Test
	void endsIfFoldBeforeBracelessElseInsideClassMethod() throws Exception {
		assertThat( folds( "Greeter.bx", """
		                                 class {
		                                     /* one-liner */
		                                     function greet( opts = {} )
		                                     {
		                                         var s = "}";
		                                         if ( a ) {
		                                             return s;
		                                         } else
		                                             return "";
		                                     }
		                                 }
		                                 """ ) ).containsExactly( new FoldingRange( 0, 10 ), new FoldingRange( 3, 9 ), new FoldingRange( 5, 6 ) );
	}

	// This test excludes duplicate ranges, so that two FoldingRanges from 0-2 don't get added.
	@Test
	void collapsesDuplicateRangesFromBlocksSharingLines() throws Exception {
		assertThat( folds( "test.bxs", """
		                               if ( outer ) { if ( inner ) {
		                                   writeOutput( 1 );
		                               } }
		                               """ ) ).containsExactly( new FoldingRange( 0, 2 ) );
	}

	@ParameterizedTest
	@ValueSource( strings = { "for ( i = 0; i < 3; i++ )", "for ( item in items )", "while ( ready )" } )
	void foldsLoopBodies( String header ) throws Exception {
		assertThat( folds( "test.bxs", header + " {\n    writeOutput( 1 );\n}\n" ) )
		    .containsExactly( new FoldingRange( 0, 2 ) );
	}

	@ParameterizedTest
	@ValueSource( strings = { "if ( ready )", "for ( i = 0; i < 3; i++ )", "for ( item in items )", "while ( ready )" } )
	void skipsBracelessMultilineBodies( String header ) throws Exception {
		assertThat( folds( "test.bxs", header + "\n    writeOutput(\n        1\n    );\n" ) ).isEmpty();
	}

	@Test
	void foldsBlockCommentAndIgnoresItsBraces() throws Exception {
		FoldingRange comment = new FoldingRange( 0, 2 );
		comment.setKind( FoldingRangeKind.Comment );

		assertThat( folds( "test.bxs", "/*\n * comment with unmatched {\n */\nvalue = 1;\n" ) )
		    .containsExactly( comment );
	}

	// The stray "}" sits inside the function body, where the function fold's brace matcher has to skip it.
	@Test
	void ignoresBracesInsideBlockCommentInFunctionBody() throws Exception {
		assertThat( folds( "test.bxs", """
		                               function example() {
		                                   /*
		                                    * } stray brace
		                                    */
		                                   return 1;
		                               }
		                               """ ) ).containsExactly( new FoldingRange( 0, 5 ), commentFold( 1, 3 ) );
	}

	@Test
	void foldsBlockCommentInsideClassBody() throws Exception {
		assertThat( folds( "Test.bx", """
		                              class {
		                                  /*
		                                   * class notes
		                                   */
		                                  function run() {
		                                      return 1;
		                                  }
		                              }
		                              """ ) ).containsExactly( new FoldingRange( 0, 7 ), new FoldingRange( 4, 6 ), commentFold( 1, 3 ) );
	}

	@Test
	void foldsCommentAboveFunction() throws Exception {
		assertThat( folds( "test.bxs", """
		                               /**
		                                * Runs it.
		                                */
		                               function run() {
		                                   return 1;
		                               }
		                               """ ) ).containsExactly( commentFold( 0, 2 ), new FoldingRange( 3, 5 ) );
	}

	@Test
	void skipsSingleLineConstructs() throws Exception {
		assertThat( folds( "test.bxs", """
		                               function inline() { return 1; }
		                               if ( ready ) { writeOutput( 1 ); }
		                               for ( item in items ) { writeOutput( item ); }
		                               /* inline comment */
		                               """ ) ).isEmpty();
	}

	@Test
	void ignoresBracesInsideStringsAndLineComments() throws Exception {
		assertThat( folds( "test.bxs", """
		                               function example() {
		                                   var value = "{";
		                                   // } function fake() {
		                                   return '}';
		                               }
		                               """ ) ).containsExactly( new FoldingRange( 0, 4 ) );
	}

	@Test
	void incompleteFileDoesNotThrowOrInventClosingBrace() throws Exception {
		URI uri = Files.writeString( tempDir.resolve( "incomplete.bxs" ), "function broken() {\n    return 1;\n" ).toUri();
		try {
			assertThat( requestFolds( uri ) ).isEmpty();
		} finally {
			ProjectContextProvider.getInstance().remove( uri );
		}
	}

	@ParameterizedTest( name = "{0}" )
	@MethodSource( "incompleteSources" )
	void incompleteStringOrCommentDoesNotThrowOrInventClosingBrace( String label, String source ) throws Exception {
		URI uri = Files.writeString( tempDir.resolve( "incomplete.bxs" ), source ).toUri();
		try {
			assertThat( requestFolds( uri ) ).isEmpty();
		} finally {
			ProjectContextProvider.getInstance().remove( uri );
		}
	}

	static Stream<Arguments> incompleteSources() {
		return Stream.of(
		    Arguments.of( "unclosed string", "function broken() {\n    x = \"abc;\n}\n" ),
		    Arguments.of( "unclosed block comment", "function broken() {\n    /* never closed\n}\n" ) );
	}

	// Folds must come from the editor's unsaved text, not the file on disk, and must update after each edit.
	@Test
	void foldsFollowUnsavedEdits() throws Exception {
		// The saved file has nothing to fold, so any fold must come from the editor's text
		Path	path	= tempDir.resolve( "edited.bxs" );
		String	uri		= Files.writeString( path, "x = 1;\n" ).toUri().toString();
		try {
			// open the file with unsaved text
			service.didOpen( new DidOpenTextDocumentParams( new TextDocumentItem( uri, "boxlang", 1, """
			                                                                                         function first() {
			                                                                                             return 1;
			                                                                                         }
			                                                                                         """ ) ) );
			assertThat( requestFolds( URI.create( uri ) ) ).containsExactly( new FoldingRange( 0, 2 ) );

			// make an edit to the file without saving
			changeDocument( uri, 2, """
			                        function first() {


			                        	return 1;
			                        }
			                        while ( ready ) {
			                        	writeOutput( 1 );
			                        }
			                        """ );
			assertThat( requestFolds( URI.create( uri ) ) ).containsExactly( new FoldingRange( 0, 4 ), new FoldingRange( 5, 7 ) );
		} finally {
			ProjectContextProvider.getInstance().remove( URI.create( uri ) );
		}
	}

	// While typing, the code is briefly incomplete: folds must disappear rather than go stale or crash, then return once it is finished.
	@Test
	void incompleteEditReturnsNoFoldsUntilCodeIsFinished() throws Exception {
		String uri = Files.writeString( tempDir.resolve( "typing.bxs" ), "x = 1;\n" ).toUri().toString();
		try {
			service.didOpen( new DidOpenTextDocumentParams( new TextDocumentItem( uri, "boxlang", 1, "function f() {\n    return 1;\n}\n" ) ) );
			assertThat( requestFolds( URI.create( uri ) ) ).containsExactly( new FoldingRange( 0, 2 ) );

			// the closing brace has been deleted mid-edit
			changeDocument( uri, 2, "function f() {\n    return 1;\n" );
			assertThat( requestFolds( URI.create( uri ) ) ).isEmpty();

			// the code is finished again, one line longer than before
			changeDocument( uri, 3, "function f() {\n    var x = 1;\n    return x;\n}\n" );
			assertThat( requestFolds( URI.create( uri ) ) ).containsExactly( new FoldingRange( 0, 3 ) );
		} finally {
			ProjectContextProvider.getInstance().remove( URI.create( uri ) );
		}
	}

	private void changeDocument( String uri, int version, String text ) {
		service.didChange( new DidChangeTextDocumentParams( new VersionedTextDocumentIdentifier( uri, version ),
		    List.of( new TextDocumentContentChangeEvent( text ) ) ) );
	}

	// Folding is scoped to BoxLang script and class files, so other BoxLang/CFML files get no folds even with foldable code.
	@ParameterizedTest( name = "{0}" )
	@MethodSource( "nonTargetFiles" )
	void returnsNoFoldsOutsideBxAndBxsFiles( String filename, String source ) throws Exception {
		assertThat( folds( filename, source ) ).isEmpty();
	}

	static Stream<Arguments> nonTargetFiles() {
		return Stream.of(
		    Arguments.of( "Test.cfc", "component {\n    function run() {\n        return 1;\n    }\n}\n" ),
		    Arguments.of( "test.cfs", "function run() {\n    return 1;\n}\n" ),
		    Arguments.of( "test.bxm", "<bx:script>\nfunction run() {\n    return 1;\n}\n</bx:script>\n" ),
		    Arguments.of( "test.cfm", "<cfscript>\nfunction run() {\n    return 1;\n}\n</cfscript>\n" ) );
	}

	private static FoldingRange commentFold( int startLine, int endLine ) {
		FoldingRange range = new FoldingRange( startLine, endLine );
		range.setKind( FoldingRangeKind.Comment );
		return range;
	}

	private List<FoldingRange> folds( String filename, String source ) throws Exception {
		URI uri = Files.writeString( tempDir.resolve( filename ), source ).toUri();
		try {
			var parsed = ProjectContextProvider.getInstance().getLatestFileParseResultPublic( uri ).orElseThrow();
			assertThat( parsed.getIssues() ).isEmpty();
			assertThat( parsed.findAstRoot() ).isPresent();
			return requestFolds( uri );
		} finally {
			ProjectContextProvider.getInstance().remove( uri );
		}
	}

	private List<FoldingRange> requestFolds( URI uri ) throws Exception {
		FoldingRangeRequestParams params = new FoldingRangeRequestParams();
		params.setTextDocument( new TextDocumentIdentifier( uri.toString() ) );
		return service.foldingRange( params ).get( 5, TimeUnit.SECONDS );
	}
}
