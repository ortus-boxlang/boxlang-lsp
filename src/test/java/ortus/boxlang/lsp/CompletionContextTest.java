package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertThat;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import ortus.boxlang.lsp.workspace.FileParseResult;
import ortus.boxlang.lsp.workspace.completion.CompletionContext;
import ortus.boxlang.lsp.workspace.completion.CompletionContextKind;
import ortus.boxlang.runtime.BoxRuntime;

/**
 * Tests for the CompletionContext class that analyzes cursor position
 * to determine what kind of completion is appropriate.
 */
public class CompletionContextTest {

	static BoxRuntime instance;

	@BeforeAll
	public static void setUp() {
		instance = BoxRuntime.getInstance( true );
	}

	/**
	 * Helper method to create a CompletionContext from source code and position.
	 */
	private CompletionContext createContext( String source, int line, int character ) {
		URI					uri				= URI.create( "file:///test/TestFile.bx" );
		FileParseResult		fileParseResult	= FileParseResult.fromSourceString( uri, source );
		CompletionParams	params			= new CompletionParams();
		params.setPosition( new Position( line, character ) );
		params.setTextDocument( new TextDocumentIdentifier( uri.toString() ) );
		return CompletionContext.analyze( fileParseResult, params );
	}

	/**
	 * Helper method for template files (.bxm)
	 */
	private CompletionContext createTemplateContext( String source, int line, int character ) {
		URI					uri				= URI.create( "file:///test/TestFile.bxm" );
		FileParseResult		fileParseResult	= FileParseResult.fromSourceString( uri, source );
		CompletionParams	params			= new CompletionParams();
		params.setPosition( new Position( line, character ) );
		params.setTextDocument( new TextDocumentIdentifier( uri.toString() ) );
		return CompletionContext.analyze( fileParseResult, params );
	}

	// ==================== NEW EXPRESSION TESTS ====================

	@Test
	void testNewExpression_afterNew() {
		String				source	= """
		                              class {
		                              	function init() {
		                              		var x = new
		                              	}
		                              }
		                              """;
		CompletionContext	context	= createContext( source, 2, 14 );
		assertThat( context.getKind() ).isEqualTo( CompletionContextKind.NEW_EXPRESSION );
	}

	@Test
	void testNewExpression_partialClassName() {
		String				source	= """
		                              class {
		                              	function init() {
		                              		var x = new User
		                              	}
		                              }
		                              """;
		CompletionContext	context	= createContext( source, 2, 18 );
		assertThat( context.getKind() ).isEqualTo( CompletionContextKind.NEW_EXPRESSION );
		assertThat( context.getTriggerText() ).isEqualTo( "User" );
	}

	@Test
	void testNewExpression_withDots() {
		String				source	= """
		                              class {
		                              	function init() {
		                              		var x = new models.User
		                              	}
		                              }
		                              """;
		CompletionContext	context	= createContext( source, 2, 25 );
		assertThat( context.getKind() ).isEqualTo( CompletionContextKind.NEW_EXPRESSION );
		assertThat( context.getTriggerText() ).isEqualTo( "models.User" );
	}

	// ==================== IMPORT TESTS ====================

	@Test
	void testImport_afterImport() {
		String				source	= """
		                              import
		                              class {}
		                              """;
		CompletionContext	context	= createContext( source, 0, 6 );
		assertThat( context.getKind() ).isEqualTo( CompletionContextKind.IMPORT );
	}

	@Test
	void testImport_partialPath() {
		String				source	= """
		                              import java.util.
		                              class {}
		                              """;
		CompletionContext	context	= createContext( source, 0, 17 );
		assertThat( context.getKind() ).isEqualTo( CompletionContextKind.IMPORT );
		assertThat( context.getTriggerText() ).isEqualTo( "java.util." );
	}

	// ==================== EXTENDS TESTS ====================

	@Test
	void testExtends_afterExtends() {
		String				source	= """
		                              class extends
		                              """;
		CompletionContext	context	= createContext( source, 0, 13 );
		assertThat( context.getKind() ).isEqualTo( CompletionContextKind.EXTENDS );
	}

	@Test
	void testExtends_partialClassName() {
		String				source	= """
		                              class extends Base
		                              """;
		CompletionContext	context	= createContext( source, 0, 18 );
		assertThat( context.getKind() ).isEqualTo( CompletionContextKind.EXTENDS );
		assertThat( context.getTriggerText() ).isEqualTo( "Base" );
	}

	// ==================== IMPLEMENTS TESTS ====================

	@Test
	void testImplements_afterImplements() {
		String				source	= """
		                              class implements
		                              """;
		CompletionContext	context	= createContext( source, 0, 16 );
		assertThat( context.getKind() ).isEqualTo( CompletionContextKind.IMPLEMENTS );
	}

	@Test
	void testImplements_partialInterfaceName() {
		String				source	= """
		                              class implements IUser
		                              """;
		CompletionContext	context	= createContext( source, 0, 22 );
		assertThat( context.getKind() ).isEqualTo( CompletionContextKind.IMPLEMENTS );
		assertThat( context.getTriggerText() ).isEqualTo( "IUser" );
	}

	// ==================== MEMBER ACCESS (DOT) TESTS ====================

	@Test
	void testMemberAccess_afterDot() {
		String				source	= """
		                              class {
		                              	function test() {
		                              		var user = new User();
		                              		user.
		                              	}
		                              }
		                              """;
		CompletionContext	context	= createContext( source, 3, 7 );
		assertThat( context.getKind() ).isEqualTo( CompletionContextKind.MEMBER_ACCESS );
	}

	@Test
	void testMemberAccess_partialMember() {
		String				source	= """
		                              class {
		                              	function test() {
		                              		var user = new User();
		                              		user.getName
		                              	}
		                              }
		                              """;
		CompletionContext	context	= createContext( source, 3, 14 );
		assertThat( context.getKind() ).isEqualTo( CompletionContextKind.MEMBER_ACCESS );
		assertThat( context.getTriggerText() ).isEqualTo( "getName" );
	}

	@Test
	void testMemberAccess_chainedCalls() {
		String				source	= """
		                              class {
		                              	function test() {
		                              		user.getName().toUpper
		                              	}
		                              }
		                              """;
		CompletionContext	context	= createContext( source, 2, 24 );
		assertThat( context.getKind() ).isEqualTo( CompletionContextKind.MEMBER_ACCESS );
		assertThat( context.getTriggerText() ).isEqualTo( "toUpper" );
	}

	@Test
	void testMemberAccess_thisKeyword() {
		String				source	= """
		                              class {
		                              	function test() {
		                              		this.
		                              	}
		                              }
		                              """;
		CompletionContext	context	= createContext( source, 2, 7 );
		assertThat( context.getKind() ).isEqualTo( CompletionContextKind.MEMBER_ACCESS );
	}

	// ==================== FUNCTION ARGUMENT TESTS ====================

	@Test
	void testFunctionArgument_afterOpenParen() {
		String				source	= """
		                              class {
		                              	function test() {
		                              		println(
		                              	}
		                              }
		                              """;
		CompletionContext	context	= createContext( source, 2, 10 );
		assertThat( context.getKind() ).isEqualTo( CompletionContextKind.FUNCTION_ARGUMENT );
		assertThat( context.getArgumentIndex() ).isEqualTo( 0 );
	}

	@Test
	void testFunctionArgument_secondArgument() {
		String				source	= """
		                              class {
		                              	function test() {
		                              		myFunc(arg1,
		                              	}
		                              }
		                              """;
		CompletionContext	context	= createContext( source, 2, 15 );
		assertThat( context.getKind() ).isEqualTo( CompletionContextKind.FUNCTION_ARGUMENT );
		assertThat( context.getArgumentIndex() ).isEqualTo( 1 );
	}

	@Test
	void testFunctionArgument_thirdArgument() {
		String				source	= """
		                              class {
		                              	function test() {
		                              		myFunc(arg1, arg2,
		                              	}
		                              }
		                              """;
		CompletionContext	context	= createContext( source, 2, 21 );
		assertThat( context.getKind() ).isEqualTo( CompletionContextKind.FUNCTION_ARGUMENT );
		assertThat( context.getArgumentIndex() ).isEqualTo( 2 );
	}

	// ==================== BXM TAG TESTS ====================

	@Test
	void testBxmTag_afterBxColon() {
		String				source	= """
		                              <bx:
		                              """;
		CompletionContext	context	= createTemplateContext( source, 0, 4 );
		assertThat( context.getKind() ).isEqualTo( CompletionContextKind.BXM_TAG );
	}

	@Test
	void testBxmTag_partialTagName() {
		String				source	= """
		                              <bx:out
		                              """;
		CompletionContext	context	= createTemplateContext( source, 0, 7 );
		assertThat( context.getKind() ).isEqualTo( CompletionContextKind.BXM_TAG );
		assertThat( context.getTriggerText() ).isEqualTo( "out" );
	}

	// ==================== TEMPLATE EXPRESSION TESTS ====================

	@Test
	void testTemplateExpr_afterHash() {
		String				source	= """
		                              <bx:output>#</bx:output>
		                              """;
		CompletionContext	context	= createTemplateContext( source, 0, 12 );
		assertThat( context.getKind() ).isEqualTo( CompletionContextKind.TEMPLATE_EXPRESSION );
	}

	@Test
	void testTemplateExpr_partialVariable() {
		String				source	= """
		                              <bx:output>#user</bx:output>
		                              """;
		CompletionContext	context	= createTemplateContext( source, 0, 16 );
		assertThat( context.getKind() ).isEqualTo( CompletionContextKind.TEMPLATE_EXPRESSION );
		assertThat( context.getTriggerText() ).isEqualTo( "user" );
	}

	// ==================== GENERAL CONTEXT TESTS ====================

	@Test
	void testGeneral_identifier() {
		String				source	= """
		                              class {
		                              	function test() {
		                              		print
		                              	}
		                              }
		                              """;
		CompletionContext	context	= createContext( source, 2, 7 );
		assertThat( context.getKind() ).isEqualTo( CompletionContextKind.GENERAL );
		assertThat( context.getTriggerText() ).isEqualTo( "print" );
	}

	@Test
	void testGeneral_emptyInFunction() {
		String				source	= """
		                              class {
		                              	function test() {

		                              	}
		                              }
		                              """;
		CompletionContext	context	= createContext( source, 2, 2 );
		assertThat( context.getKind() ).isEqualTo( CompletionContextKind.GENERAL );
	}

	// ==================== CONTAINING SCOPE TESTS ====================

	@Test
	void testContainingMethod_insideFunction() {
		String				source	= """
		                              class {
		                              	function myMethod() {
		                              		var x = 1;
		                              	}
		                              }
		                              """;
		CompletionContext	context	= createContext( source, 2, 12 );
		assertThat( context.getContainingMethodName() ).isEqualTo( "myMethod" );
	}

	@Test
	void testContainingClass_insideClass() {
		String				source	= """
		                              class MyClass {
		                              	function test() {
		                              		var x = 1;
		                              	}
		                              }
		                              """;
		CompletionContext	context	= createContext( source, 2, 12 );
		assertThat( context.getContainingClassName() ).isNotNull();
	}

	// ==================== EDGE CASES ====================

	@Test
	void testEdgeCase_stringLiteral() {
		String				source	= """
		                              class {
		                              	function test() {
		                              		var x = "hello.world";
		                              	}
		                              }
		                              """;
		// Inside the string - should not trigger member access
		CompletionContext	context	= createContext( source, 2, 18 );
		assertThat( context.getKind() ).isNotEqualTo( CompletionContextKind.MEMBER_ACCESS );
	}

	@Test
	void testEdgeCase_comment() {
		String				source	= """
		                              class {
		                              	function test() {
		                              		// var x = new
		                              	}
		                              }
		                              """;
		// Inside a comment - should not trigger new expression
		CompletionContext	context	= createContext( source, 2, 16 );
		// In a comment, we might get GENERAL or NONE - the key is that it's not NEW_EXPRESSION
		assertThat( context.getKind() ).isNotEqualTo( CompletionContextKind.NEW_EXPRESSION );
	}
}
