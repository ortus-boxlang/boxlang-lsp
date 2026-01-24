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

import ortus.boxlang.lsp.workspace.ProjectContextProvider;
import ortus.boxlang.lsp.workspace.index.ProjectIndex;

/**
 * Tests for go-to-definition functionality on functions and methods.
 * Task 2.2: Go to Definition - Functions and Methods
 */
public class FunctionDefinitionTest extends BaseTest {

	private BoxLangTextDocumentService	svc;
	private ProjectContextProvider		provider;
	private ProjectIndex				index;
	private Path						testDir;

	@BeforeEach
	void setUp() throws Exception {
		svc			= new BoxLangTextDocumentService();
		provider	= ProjectContextProvider.getInstance();
		index		= new ProjectIndex();
		provider.setIndex( index );
		testDir = Paths.get( "src/test/resources/files/functionDefinitionTest" );

		// Index all test files
		for ( Path file : Files.list( testDir ).filter( p -> p.toString().endsWith( ".bx" ) ).toList() ) {
			index.indexFile( file.toUri() );
		}

		// Open all test files in the LSP
		for ( Path file : Files.list( testDir ).filter( p -> p.toString().endsWith( ".bx" ) ).toList() ) {
			svc.didOpen( new DidOpenTextDocumentParams(
			    new TextDocumentItem( file.toUri().toString(), "boxlang", 1, Files.readString( file ) ) ) );
		}
	}

	/**
	 * Test go-to-definition on a function invocation in the same file.
	 * From: `helperFunction( "test input" )` in localFunctionCaller()
	 * To: `function helperFunction()` definition
	 */
	@Test
	void testSameFileFunctionInvocation() throws Exception {
		Path				testFilePath	= testDir.resolve( "ClassThatUsesService.bx" );
		String				testFileUri		= testFilePath.toUri().toString();

		// Position at 'helperFunction' in the call on line 43: var result = helperFunction( "test input" );
		// Line 43 (0-indexed: 42), character position at 'helperFunction'
		DefinitionParams	params			= new DefinitionParams();
		params.setTextDocument( new TextDocumentIdentifier( testFileUri ) );
		params.setPosition( new Position( 42, 20 ) );

		var result = svc.definition( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.getLeft() ).isNotNull();
		assertThat( result.getLeft().size() ).isGreaterThan( 0 );

		var def = result.getLeft().get( 0 );
		assertThat( def.getUri() ).isEqualTo( testFileUri );
		// helperFunction is defined on line 54 (0-indexed: 53)
		assertThat( def.getRange().getStart().getLine() ).isEqualTo( 53 );
	}

	/**
	 * Test go-to-definition on a function call within another function in the same file.
	 * From: `helperFunction( "another test" )` in anotherCaller()
	 * To: `function helperFunction()` definition
	 */
	@Test
	void testAnotherSameFileFunctionCall() throws Exception {
		Path				testFilePath	= testDir.resolve( "ClassThatUsesService.bx" );
		String				testFileUri		= testFilePath.toUri().toString();

		// Position at 'helperFunction' in anotherCaller() on line 63: return helperFunction( "another test" );
		// Line 63 (0-indexed: 62), character at 'helperFunction'
		DefinitionParams	params			= new DefinitionParams();
		params.setTextDocument( new TextDocumentIdentifier( testFileUri ) );
		params.setPosition( new Position( 62, 12 ) );

		var result = svc.definition( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.getLeft() ).isNotNull();
		assertThat( result.getLeft().size() ).isGreaterThan( 0 );

		var def = result.getLeft().get( 0 );
		assertThat( def.getUri() ).isEqualTo( testFileUri );
		// helperFunction is defined on line 54 (0-indexed: 53)
		assertThat( def.getRange().getStart().getLine() ).isEqualTo( 53 );
	}

	/**
	 * Test go-to-definition on a method invocation on an object (cross-file).
	 * From: `service.getUserById( 123 )` in doSomething()
	 * To: `function getUserById()` in ServiceClass.bx
	 */
	@Test
	void testCrossFileMethodInvocation() throws Exception {
		Path				testFilePath	= testDir.resolve( "ClassThatUsesService.bx" );
		String				testFileUri		= testFilePath.toUri().toString();
		Path				serviceFilePath	= testDir.resolve( "ServiceClass.bx" );
		String				serviceFileUri	= serviceFilePath.toUri().toString();

		// Position at 'getUserById' on line 14: var user = service.getUserById( 123 );
		// Line 14 (0-indexed: 13), character at 'getUserById'
		DefinitionParams	params			= new DefinitionParams();
		params.setTextDocument( new TextDocumentIdentifier( testFileUri ) );
		params.setPosition( new Position( 13, 25 ) );

		var result = svc.definition( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.getLeft() ).isNotNull();
		assertThat( result.getLeft().size() ).isGreaterThan( 0 );

		var def = result.getLeft().get( 0 );
		// Should navigate to ServiceClass.bx
		assertThat( def.getUri() ).isEqualTo( serviceFileUri );
		// getUserById is defined on line 13 (0-indexed: 12)
		assertThat( def.getRange().getStart().getLine() ).isEqualTo( 12 );
	}

	/**
	 * Test go-to-definition on an overridden method goes to the override.
	 * From: `child.getGreeting()` in testInheritedMethod()
	 * To: `function getGreeting()` in ChildClass.bx (the override, not base)
	 */
	@Test
	void testOverriddenMethodGoesToOverride() throws Exception {
		Path				testFilePath	= testDir.resolve( "ClassThatUsesService.bx" );
		String				testFileUri		= testFilePath.toUri().toString();
		Path				childFilePath	= testDir.resolve( "ChildClass.bx" );
		String				childFileUri	= childFilePath.toUri().toString();

		// Position at 'getGreeting' on line 30: var greeting = child.getGreeting();
		// Line 30 (0-indexed: 29), character at 'getGreeting'
		DefinitionParams	params			= new DefinitionParams();
		params.setTextDocument( new TextDocumentIdentifier( testFileUri ) );
		params.setPosition( new Position( 29, 24 ) );

		var result = svc.definition( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.getLeft() ).isNotNull();
		assertThat( result.getLeft().size() ).isGreaterThan( 0 );

		var def = result.getLeft().get( 0 );
		// Should navigate to ChildClass.bx (override)
		assertThat( def.getUri() ).isEqualTo( childFileUri );
		// getGreeting in ChildClass is defined on line 12 (0-indexed: 11)
		assertThat( def.getRange().getStart().getLine() ).isEqualTo( 11 );
	}

	/**
	 * Test go-to-definition on an inherited method goes to the parent class.
	 * From: `child.logMessage( "test" )` in testInheritedMethod()
	 * To: `function logMessage()` in BaseClass.bx (inherited)
	 */
	@Test
	void testInheritedMethodGoesToParent() throws Exception {
		Path				testFilePath	= testDir.resolve( "ClassThatUsesService.bx" );
		String				testFileUri		= testFilePath.toUri().toString();
		Path				baseFilePath	= testDir.resolve( "BaseClass.bx" );
		String				baseFileUri		= baseFilePath.toUri().toString();

		// Position at 'logMessage' on line 33: var logged = child.logMessage( "test" );
		// Line 33 (0-indexed: 32), character at 'logMessage'
		DefinitionParams	params			= new DefinitionParams();
		params.setTextDocument( new TextDocumentIdentifier( testFileUri ) );
		params.setPosition( new Position( 32, 22 ) );

		var result = svc.definition( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.getLeft() ).isNotNull();
		assertThat( result.getLeft().size() ).isGreaterThan( 0 );

		var def = result.getLeft().get( 0 );
		// Should navigate to BaseClass.bx (inherited method)
		assertThat( def.getUri() ).isEqualTo( baseFileUri );
		// logMessage in BaseClass is defined on line 13 (0-indexed: 12)
		assertThat( def.getRange().getStart().getLine() ).isEqualTo( 12 );
	}

	/**
	 * Test go-to-definition returns nothing for BIFs.
	 * BIFs like trim(), len(), etc. don't have a source location to navigate to.
	 */
	@Test
	void testBIFReturnsNoResult() throws Exception {
		Path				serviceFilePath	= testDir.resolve( "ServiceClass.bx" );
		String				serviceFileUri	= serviceFilePath.toUri().toString();

		// Position at 'trim' in sanitizeInput() on line 37: return trim( input );
		// Line 37 (0-indexed: 36), character at 'trim'
		DefinitionParams	params			= new DefinitionParams();
		params.setTextDocument( new TextDocumentIdentifier( serviceFileUri ) );
		params.setPosition( new Position( 36, 10 ) );

		var result = svc.definition( params ).get();

		// BIFs should return empty or null since there's no source to navigate to
		if ( result != null && result.getLeft() != null ) {
			assertThat( result.getLeft().size() ).isEqualTo( 0 );
		}
	}

	/**
	 * Test go-to-definition on a method invocation on `this` keyword.
	 * From: `this.getGreeting()` in multiGreet() in ChildClass
	 * To: `function getGreeting()` in the same class
	 */
	@Test
	void testThisMethodInvocation() throws Exception {
		Path				childFilePath	= testDir.resolve( "ChildClass.bx" );
		String				childFileUri	= childFilePath.toUri().toString();

		// Position at 'getGreeting' in multiGreet() on line 26: result &= this.getGreeting() & " ";
		// Line 26 (0-indexed: 25), character at 'getGreeting' (starts at position 14)
		DefinitionParams	params			= new DefinitionParams();
		params.setTextDocument( new TextDocumentIdentifier( childFileUri ) );
		params.setPosition( new Position( 25, 18 ) );

		var result = svc.definition( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.getLeft() ).isNotNull();
		assertThat( result.getLeft().size() ).isGreaterThan( 0 );

		var def = result.getLeft().get( 0 );
		// Should navigate to getGreeting in the same file
		assertThat( def.getUri() ).isEqualTo( childFileUri );
		// getGreeting is defined on line 12 (0-indexed: 11)
		assertThat( def.getRange().getStart().getLine() ).isEqualTo( 11 );
	}

}
