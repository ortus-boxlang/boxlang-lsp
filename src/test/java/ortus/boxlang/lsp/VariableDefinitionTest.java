package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertThat;

import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Tests for go-to-definition functionality on local variables.
 * Task 2.1: Go to Definition - Local Variables
 */
public class VariableDefinitionTest extends BaseTest {

	private BoxLangTextDocumentService	svc;
	private Path						testFilePath;
	private String						testFileUri;

	@BeforeEach
	void setUp() throws Exception {
		svc				= new BoxLangTextDocumentService();
		testFilePath	= Paths.get( "src/test/resources/files/variableDefinitionTest.bx" );
		testFileUri		= testFilePath.toUri().toString();

		svc.didOpen( new DidOpenTextDocumentParams(
		    new TextDocumentItem( testFileUri, "boxlang", 1, Files.readString( testFilePath ) ) ) );
	}

	/**
	 * Test go-to-definition on a local variable usage.
	 * From: `return localVar & anotherVar;` (line 12)
	 * To: `var localVar = "hello";` (line 8)
	 */
	@Test
	void testLocalVariableUsage() throws Exception {
		// Position at 'localVar' on line 12 (0-indexed: line 11, col 15)
		DefinitionParams params = new DefinitionParams();
		params.setTextDocument( new TextDocumentIdentifier( testFileUri ) );
		params.setPosition( new Position( 11, 15 ) );

		var result = svc.definition( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.getLeft() ).isNotNull();
		assertThat( result.getLeft().size() ).isGreaterThan( 0 );

		var def = result.getLeft().get( 0 );
		assertThat( def.getUri() ).isEqualTo( testFileUri );
		// Declaration is on line 8 (0-indexed: 7)
		assertThat( def.getRange().getStart().getLine() ).isEqualTo( 7 );
	}

	/**
	 * Test go-to-definition on a function parameter usage.
	 * From: `var greeting = "Hello, " & userName;` (line 17)
	 * To: `required string userName` (line 15)
	 */
	@Test
	void testParameterUsage() throws Exception {
		// Position at 'userName' on line 17 (0-indexed: line 16, col 32)
		DefinitionParams params = new DefinitionParams();
		params.setTextDocument( new TextDocumentIdentifier( testFileUri ) );
		params.setPosition( new Position( 16, 35 ) );

		var result = svc.definition( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.getLeft() ).isNotNull();
		assertThat( result.getLeft().size() ).isGreaterThan( 0 );

		var def = result.getLeft().get( 0 );
		assertThat( def.getUri() ).isEqualTo( testFileUri );
		// Parameter is on line 15 (0-indexed: 14)
		assertThat( def.getRange().getStart().getLine() ).isEqualTo( 14 );
	}

	/**
	 * Test go-to-definition for a shadowed variable.
	 * When a local var shadows a parameter, go-to-definition should find the local var.
	 * From: `return name` on line 31 (1-indexed)
	 * To: `var name = "shadowed";` on line 28 (1-indexed)
	 */
	@Test
	void testShadowedVariableGoesToLocalDeclaration() throws Exception {
		// Position at 'name' on line 31 (0-indexed: line 30, col 15)
		DefinitionParams params = new DefinitionParams();
		params.setTextDocument( new TextDocumentIdentifier( testFileUri ) );
		params.setPosition( new Position( 30, 15 ) );

		var result = svc.definition( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.getLeft() ).isNotNull();
		assertThat( result.getLeft().size() ).isGreaterThan( 0 );

		var def = result.getLeft().get( 0 );
		assertThat( def.getUri() ).isEqualTo( testFileUri );
		// Local var declaration is on line 28 (0-indexed: 27)
		assertThat( def.getRange().getStart().getLine() ).isEqualTo( 27 );
	}

	/**
	 * Test go-to-definition for a parameter before it's shadowed.
	 * From: `var result = name;` on line 25 (1-indexed)
	 * To: `string name` parameter on line 23 (1-indexed)
	 */
	@Test
	void testParameterBeforeShadowing() throws Exception {
		// Position at 'name' on line 25 (0-indexed: line 24, col 21)
		DefinitionParams params = new DefinitionParams();
		params.setTextDocument( new TextDocumentIdentifier( testFileUri ) );
		params.setPosition( new Position( 24, 21 ) );

		var result = svc.definition( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.getLeft() ).isNotNull();
		assertThat( result.getLeft().size() ).isGreaterThan( 0 );

		var def = result.getLeft().get( 0 );
		assertThat( def.getUri() ).isEqualTo( testFileUri );
		// Parameter is on line 23 (0-indexed: 22)
		assertThat( def.getRange().getStart().getLine() ).isEqualTo( 22 );
	}

	/**
	 * Test go-to-definition for a loop variable.
	 * From: `total = total + i;` on line 39 (1-indexed)
	 * To: `var i = 0` on line 37 (1-indexed)
	 */
	@Test
	void testLoopVariableDefinition() throws Exception {
		// Position at 'i' on line 39 (0-indexed: line 38, col 28)
		DefinitionParams params = new DefinitionParams();
		params.setTextDocument( new TextDocumentIdentifier( testFileUri ) );
		params.setPosition( new Position( 38, 28 ) );

		var result = svc.definition( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.getLeft() ).isNotNull();
		assertThat( result.getLeft().size() ).isGreaterThan( 0 );

		var def = result.getLeft().get( 0 );
		assertThat( def.getUri() ).isEqualTo( testFileUri );
		// Loop var 'i' is declared on line 37 (0-indexed: 36)
		assertThat( def.getRange().getStart().getLine() ).isEqualTo( 36 );
	}

	/**
	 * Test go-to-definition for a variable declared outside a loop.
	 * From: `return total;` on line 43 (1-indexed)
	 * To: `var total = 0;` on line 35 (1-indexed)
	 */
	@Test
	void testOuterVariableFromLoop() throws Exception {
		// Position at 'total' on line 43 (0-indexed: line 42, col 15)
		DefinitionParams params = new DefinitionParams();
		params.setTextDocument( new TextDocumentIdentifier( testFileUri ) );
		params.setPosition( new Position( 42, 15 ) );

		var result = svc.definition( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.getLeft() ).isNotNull();
		assertThat( result.getLeft().size() ).isGreaterThan( 0 );

		var def = result.getLeft().get( 0 );
		assertThat( def.getUri() ).isEqualTo( testFileUri );
		// 'total' is declared on line 35 (0-indexed: 34)
		assertThat( def.getRange().getStart().getLine() ).isEqualTo( 34 );
	}

	/**
	 * Test go-to-definition returns no result for unknown variable.
	 */
	@Test
	void testUnknownVariableReturnsEmpty() throws Exception {
		// Position somewhere in whitespace or on a keyword
		DefinitionParams params = new DefinitionParams();
		params.setTextDocument( new TextDocumentIdentifier( testFileUri ) );
		params.setPosition( new Position( 0, 0 ) );

		var result = svc.definition( params ).get();

		// Should return empty or null since there's no variable at this position
		if ( result != null && result.getLeft() != null ) {
			assertThat( result.getLeft().size() ).isEqualTo( 0 );
		}
	}

	/**
	 * Test go-to-definition for multiple variables on same line.
	 * From: `return x + y + z;` on line 68 (1-indexed)
	 * Tests that 'y' goes to line 64 (1-indexed)
	 */
	@Test
	void testMultipleVariablesOnSameLine() throws Exception {
		// Position at 'y' on line 68 (0-indexed: line 67, col 19)
		DefinitionParams params = new DefinitionParams();
		params.setTextDocument( new TextDocumentIdentifier( testFileUri ) );
		params.setPosition( new Position( 67, 19 ) );

		var result = svc.definition( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.getLeft() ).isNotNull();
		assertThat( result.getLeft().size() ).isGreaterThan( 0 );

		var def = result.getLeft().get( 0 );
		assertThat( def.getUri() ).isEqualTo( testFileUri );
		// 'y' is declared on line 64 (0-indexed: 63)
		assertThat( def.getRange().getStart().getLine() ).isEqualTo( 63 );
	}

	/**
	 * Test go-to-definition for deeply nested variable.
	 * From: `return deepest + inner + outer;` on line 81 (1-indexed)
	 * To: `var deepest = 30;` on line 78 (1-indexed)
	 */
	@Test
	void testDeepNestedVariableDefinition() throws Exception {
		// Position at 'deepest' on line 81 (0-indexed: line 80, col 23)
		DefinitionParams params = new DefinitionParams();
		params.setTextDocument( new TextDocumentIdentifier( testFileUri ) );
		params.setPosition( new Position( 80, 23 ) );

		var result = svc.definition( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.getLeft() ).isNotNull();
		assertThat( result.getLeft().size() ).isGreaterThan( 0 );

		var def = result.getLeft().get( 0 );
		assertThat( def.getUri() ).isEqualTo( testFileUri );
		// 'deepest' is declared on line 78 (0-indexed: 77)
		assertThat( def.getRange().getStart().getLine() ).isEqualTo( 77 );
	}
}
