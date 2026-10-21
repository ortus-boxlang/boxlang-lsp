package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SelectionRange;
import org.eclipse.lsp4j.SelectionRangeParams;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class SelectionRangeTest extends BaseTest {

	private static final Path	HOVER_CLASS		= Paths.get( "src/test/resources/files/hoverTestClass.bx" );
	private static final Path	SELECTION_BX	= Paths.get( "src/test/resources/test-bx-project/src/selectionRangeTest/selectionRange.bx" );
	private static final Path	SELECTION_BXS	= Paths.get( "src/test/resources/test-bx-project/src/selectionRangeTest/selectionRange.bxs" );

	@TempDir
	Path						tempDir;

	@Test
	void initializeAdvertisesSelectionRangeProvider() throws Exception {
		InitializeParams params = new InitializeParams();
		params.setCapabilities( new ClientCapabilities() );
		params.setWorkspaceFolders( List.of() );

		InitializeResult result = new LanguageServer().initialize( params ).get();

		assertThat( result.getCapabilities().getSelectionRangeProvider().isLeft() ).isTrue();
		assertThat( result.getCapabilities().getSelectionRangeProvider().getLeft() ).isTrue();
	}

	@Test
	void functionCallExpandsThroughAssignmentStatementFunctionAndClass() throws Exception {
		BoxLangTextDocumentService	svc		= new BoxLangTextDocumentService();
		String						uri		= open( svc, HOVER_CLASS );

		// Line 34: "var result = getUser(1);" with the cursor on the "e" in getUser.
		// The call is the smallest node; its name is not a separate AST node.
		List<SelectionRange>		ranges	= request( svc, uri, new Position( 33, 22 ) );

		assertThat( ranges ).hasSize( 1 );
		assertThat( chain( ranges.get( 0 ) ) ).containsExactly(
		    range( 33, 21, 33, 31 ), // getUser(1)
		    range( 33, 12, 33, 31 ), // result = getUser(1)
		    range( 33, 8, 33, 31 ),  // var result = getUser(1)
		    range( 32, 4, 36, 5 ),   // function caller() { ... }
		    range( 6, 0, 37, 1 )     // class { ... }
		).inOrder();
	}

	@Test
	void identifierExpandsThroughExpressionReturnFunctionAndClass() throws Exception {
		BoxLangTextDocumentService	svc		= new BoxLangTextDocumentService();
		String						uri		= open( svc, HOVER_CLASS );

		// Line 30: return "Hello, " & name; with the cursor on the "a" in name.
		List<SelectionRange>		ranges	= request( svc, uri, new Position( 29, 28 ) );

		assertThat( ranges ).hasSize( 1 );
		assertThat( chain( ranges.get( 0 ) ) ).containsExactly(
		    range( 29, 27, 29, 31 ), // name
		    range( 29, 15, 29, 31 ), // "Hello, " & name
		    range( 29, 8, 29, 31 ),  // return "Hello, " & name
		    range( 28, 4, 30, 5 ),   // private function undocumentedFunction( ... ) { ... }
		    range( 6, 0, 37, 1 )     // class { ... }
		).inOrder();
	}

	@Test
	void nestedExpressionChainEachParentStrictlyContainsItsChild() throws Exception {
		BoxLangTextDocumentService	svc		= new BoxLangTextDocumentService();
		String						uri		= open( svc, SELECTION_BX );

		// Line 10: "var c = a - b;" with the cursor on "a".
		List<SelectionRange>		ranges	= request( svc, uri, new Position( 9, 12 ) );

		assertThat( chain( ranges.get( 0 ) ) ).containsExactly(
		    range( 9, 12, 9, 13 ), // a
		    range( 9, 12, 9, 17 ), // a - b
		    range( 9, 8, 9, 17 ),  // c = a - b
		    range( 9, 4, 9, 17 ),  // var c = a - b
		    range( 7, 1, 12, 1 ),  // function subtract( a, b ) { ... }
		    range( 6, 0, 13, 1 )   // class { ... }
		).inOrder();
		assertStrictlyNested( ranges.get( 0 ) );
	}

	@Test
	void scriptFileOmitsParentWithIdenticalRange() throws Exception {
		BoxLangTextDocumentService	svc		= new BoxLangTextDocumentService();
		String						uri		= open( svc, SELECTION_BXS );

		// Line 4: "var c = a + b;" with the cursor on "a". The script root spans exactly the
		// same text as the function, so it must not appear as a separate step.
		List<SelectionRange>		ranges	= request( svc, uri, new Position( 3, 12 ) );

		assertThat( chain( ranges.get( 0 ) ) ).containsExactly(
		    range( 3, 12, 3, 13 ), // a
		    range( 3, 12, 3, 17 ), // a + b
		    range( 3, 8, 3, 17 ),  // c = a + b
		    range( 3, 4, 3, 17 ),  // var c = a + b
		    range( 0, 0, 6, 1 )    // function add( a, b ) { ... } (same range as the script root)
		).inOrder();
		assertStrictlyNested( ranges.get( 0 ) );
	}

	@Test
	void multipleCursorsReturnOneResultPerPositionInRequestOrder() throws Exception {
		BoxLangTextDocumentService	svc		= new BoxLangTextDocumentService();
		String						uri		= open( svc, HOVER_CLASS );

		List<SelectionRange>		ranges	= request( svc, uri,
		    new Position( 33, 22 ), // getUser(1)
		    new Position( 1, 3 ),   // inside the doc comment above the class: no AST node
		    new Position( 29, 28 )  // name
		);

		assertThat( ranges ).hasSize( 3 );
		assertThat( ranges.get( 0 ).getRange() ).isEqualTo( range( 33, 21, 33, 31 ) );
		assertCaretOnly( ranges.get( 1 ), new Position( 1, 3 ) );
		assertThat( ranges.get( 2 ).getRange() ).isEqualTo( range( 29, 27, 29, 31 ) );
	}

	@Test
	void emptyFileReturnsCaretOnlyRange() throws Exception {
		BoxLangTextDocumentService	svc		= new BoxLangTextDocumentService();
		String						uri		= open( svc, write( "empty.bxs", "" ) );

		List<SelectionRange>		ranges	= request( svc, uri, new Position( 0, 0 ) );

		assertThat( ranges ).hasSize( 1 );
		assertCaretOnly( ranges.get( 0 ), new Position( 0, 0 ) );
	}

	@Test
	void incompleteFileReturnsUsableResultWithoutFailing() throws Exception {
		BoxLangTextDocumentService	svc		= new BoxLangTextDocumentService();
		String						uri		= open( svc, write( "incomplete.bxs", "function broken( a, b {\n\tvar c = a +\n" ) );

		List<SelectionRange>		ranges	= request( svc, uri, new Position( 1, 6 ), new Position( 0, 3 ) );

		// The parser may or may not recover a partial tree; either way every cursor gets a
		// well-formed answer instead of an error.
		assertThat( ranges ).hasSize( 2 );
		for ( SelectionRange selection : ranges ) {
			assertStrictlyNested( selection );
		}
	}

	@Test
	void unsavedEditsAreUsedInsteadOfFileOnDisk() throws Exception {
		Path							file	= write( "unsaved.bxs", "var x = 1;" );
		BoxLangTextDocumentService		svc		= new BoxLangTextDocumentService();
		String							uri		= open( svc, file );

		// Replace the text in the editor without saving. Column 21 only exists in the new text.
		VersionedTextDocumentIdentifier	doc		= new VersionedTextDocumentIdentifier( uri, 2 );
		svc.didChange( new DidChangeTextDocumentParams( doc, List.of( new TextDocumentContentChangeEvent( "var longerName = 1 + 2;" ) ) ) );

		List<SelectionRange> ranges = request( svc, uri, new Position( 0, 21 ) );

		assertThat( Files.readString( file ) ).isEqualTo( "var x = 1;" );
		assertThat( chain( ranges.get( 0 ) ) ).containsAtLeast(
		    range( 0, 21, 0, 22 ), // 2
		    range( 0, 17, 0, 22 )  // 1 + 2
		).inOrder();
		assertThat( ranges.get( 0 ).getRange() ).isEqualTo( range( 0, 21, 0, 22 ) );
	}

	private Path write( String name, String content ) throws Exception {
		Path file = tempDir.resolve( name );
		Files.writeString( file, content );
		return file;
	}

	private static String open( BoxLangTextDocumentService svc, Path file ) throws Exception {
		String uri = file.toUri().toString();
		svc.didOpen( new DidOpenTextDocumentParams( new TextDocumentItem( uri, "boxlang", 1, Files.readString( file ) ) ) );
		return uri;
	}

	private static List<SelectionRange> request( BoxLangTextDocumentService svc, String uri, Position... positions ) throws Exception {
		return svc.selectionRange( new SelectionRangeParams( new TextDocumentIdentifier( uri ), List.of( positions ) ) ).get();
	}

	// Flattens a selection chain into its ranges, innermost first.
	private static List<Range> chain( SelectionRange selection ) {
		List<Range> ranges = new ArrayList<>();
		for ( SelectionRange current = selection; current != null; current = current.getParent() ) {
			ranges.add( current.getRange() );
		}
		return ranges;
	}

	private static void assertStrictlyNested( SelectionRange selection ) {
		List<Range> ranges = chain( selection );
		for ( int i = 1; i < ranges.size(); i++ ) {
			Range	child	= ranges.get( i - 1 );
			Range	parent	= ranges.get( i );
			assertThat( parent ).isNotEqualTo( child );
			assertThat( compare( parent.getStart(), child.getStart() ) ).isAtMost( 0 );
			assertThat( compare( parent.getEnd(), child.getEnd() ) ).isAtLeast( 0 );
		}
	}

	private static void assertCaretOnly( SelectionRange selection, Position caret ) {
		assertThat( selection.getRange() ).isEqualTo( new Range( caret, caret ) );
		assertThat( selection.getParent() ).isNull();
	}

	private static int compare( Position a, Position b ) {
		return a.getLine() != b.getLine() ? Integer.compare( a.getLine(), b.getLine() ) : Integer.compare( a.getCharacter(), b.getCharacter() );
	}

	private static Range range( int startLine, int startChar, int endLine, int endChar ) {
		return new Range( new Position( startLine, startChar ), new Position( endLine, endChar ) );
	}
}
