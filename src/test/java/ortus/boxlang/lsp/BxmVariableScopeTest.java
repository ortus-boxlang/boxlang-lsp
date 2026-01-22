package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertThat;

import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.ReferenceContext;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.lsp4j.Location;

import ortus.boxlang.lsp.workspace.ProjectContextProvider;
import ortus.boxlang.lsp.workspace.index.ProjectIndex;

/**
 * Tests for variable references across BXM template blocks.
 * Bug: Variables defined in bx:script should be found in bx:output ## expressions.
 */
public class BxmVariableScopeTest extends BaseTest {

	private BoxLangTextDocumentService	svc;
	private ProjectContextProvider		provider;
	private ProjectIndex				index;

	@BeforeEach
	void setUp() throws Exception {
		svc			= new BoxLangTextDocumentService();
		provider	= ProjectContextProvider.getInstance();
		index		= new ProjectIndex();
		provider.setIndex( index );
	}

	/**
	 * Test that variable 'y' defined in bx:script can be found when used in bx:output.
	 */
	@Test
	void testVariableReferencesAcrossTemplateBlocks() throws Exception {
		Path	bxmFilePath	= Paths.get( "src/test/resources/files/bxmVariableTest.bxm" );
		String	bxmFileUri	= bxmFilePath.toUri().toString();

		// Open the file
		svc.didOpen( new DidOpenTextDocumentParams(
		    new TextDocumentItem( bxmFileUri, "boxlang", 1, Files.readString( bxmFilePath ) ) ) );

		// Position at 'y' in the bx:script block (line 6, 0-indexed: 5)
		// The line is: "    y = "what""
		// 'y' starts at column 4 (0-indexed)
		ReferenceParams params = new ReferenceParams();
		params.setTextDocument( new TextDocumentIdentifier( bxmFileUri ) );
		params.setPosition( new Position( 5, 4 ) ); // At 'y' in assignment
		params.setContext( new ReferenceContext( true ) ); // Include declaration

		var refs = svc.references( params ).get();

		System.out.println( "Found " + ( refs == null ? "null" : refs.size() ) + " references" );
		if ( refs != null ) {
			for ( Location ref : refs ) {
				System.out.println( "  - Line " + ref.getRange().getStart().getLine() +
				    ", Col " + ref.getRange().getStart().getCharacter() +
				    " in " + ref.getUri() );
			}
		}

		assertThat( refs ).isNotNull();
		// Should find at least 2 references:
		// 1. The assignment in bx:script (line 5)
		// 2. The usage in bx:output #y# (line 10)
		assertThat( refs.size() ).isAtLeast( 2 );
	}

	/**
	 * Test finding references from the output expression side.
	 */
	@Test
	void testVariableReferencesFromOutputExpression() throws Exception {
		Path	bxmFilePath	= Paths.get( "src/test/resources/files/bxmVariableTest.bxm" );
		String	bxmFileUri	= bxmFilePath.toUri().toString();

		// Open the file
		svc.didOpen( new DidOpenTextDocumentParams(
		    new TextDocumentItem( bxmFileUri, "boxlang", 1, Files.readString( bxmFilePath ) ) ) );

		// Position at 'y' in the bx:output block (line 10, 0-indexed: 9)
		// The line is: "    #y#"
		// 'y' starts at column 5 (after "    #")
		ReferenceParams params = new ReferenceParams();
		params.setTextDocument( new TextDocumentIdentifier( bxmFileUri ) );
		params.setPosition( new Position( 9, 5 ) ); // At 'y' in #y#
		params.setContext( new ReferenceContext( true ) ); // Include declaration

		var refs = svc.references( params ).get();

		System.out.println( "Found " + ( refs == null ? "null" : refs.size() ) + " references from output" );
		if ( refs != null ) {
			for ( Location ref : refs ) {
				System.out.println( "  - Line " + ref.getRange().getStart().getLine() +
				    ", Col " + ref.getRange().getStart().getCharacter() +
				    " in " + ref.getUri() );
			}
		}

		assertThat( refs ).isNotNull();
		// Should find at least 2 references
		assertThat( refs.size() ).isAtLeast( 2 );
	}

	/**
	 * Test Go to Definition from output expression goes to the assignment.
	 */
	@Test
	void testGoToDefinitionFromOutputExpression() throws Exception {
		Path	bxmFilePath	= Paths.get( "src/test/resources/files/bxmVariableTest.bxm" );
		String	bxmFileUri	= bxmFilePath.toUri().toString();

		// Open the file
		svc.didOpen( new DidOpenTextDocumentParams(
		    new TextDocumentItem( bxmFileUri, "boxlang", 1, Files.readString( bxmFilePath ) ) ) );

		// Position at 'y' in the bx:output block (line 10, 0-indexed: 9)
		// The line is: "    #y#"
		// 'y' starts at column 5 (after "    #")
		DefinitionParams params = new DefinitionParams();
		params.setTextDocument( new TextDocumentIdentifier( bxmFileUri ) );
		params.setPosition( new Position( 9, 5 ) ); // At 'y' in #y#

		var result = svc.definition( params ).get();

		System.out.println( "Go to Definition result: " + result );
		if ( result.isLeft() ) {
			var locations = result.getLeft();
			System.out.println( "Found " + locations.size() + " definitions" );
			for ( var loc : locations ) {
				System.out.println( "  - Line " + loc.getRange().getStart().getLine() +
				    ", Col " + loc.getRange().getStart().getCharacter() );
			}
		}

		assertThat( result ).isNotNull();
		assertThat( result.isLeft() ).isTrue();
		assertThat( result.getLeft() ).isNotEmpty();
		// Should go to line 5 where y is assigned
		assertThat( result.getLeft().get( 0 ).getRange().getStart().getLine() ).isEqualTo( 5 );
	}
}
