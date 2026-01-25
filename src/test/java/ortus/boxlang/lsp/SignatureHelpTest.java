package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertThat;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SignatureHelpParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ortus.boxlang.lsp.workspace.ProjectContextProvider;
import ortus.boxlang.lsp.workspace.index.ProjectIndex;

public class SignatureHelpTest extends BaseTest {

	@BeforeEach
	void setUp() {
		// Clear and initialize the index to avoid contamination from other tests
		ProjectIndex index = ProjectContextProvider.getInstance().getIndex();
		index.clear();
		// Initialize with src/test/resources/files as the workspace root
		Path testFilesRoot = Paths.get("src/test/resources/files");
		index.initialize(testFilesRoot);
	}

	// Line numbers (1-indexed from file):
	// Line 32: var result = getUser(1); -> col 21-31
	// Line 33: simpleFunction(); -> col 8-24
	// Line 34: undocumentedFunction("World"); -> col 8-37
	// Line 35: multiParamFunc( "test", 42 ); -> col 8-36
	// Line 36: arrayAppend( [], "item" ); -> col 8-33
	// Line 37: var obj = new ClassWithDocFunc();
	// Line 38: obj.documentedFunction( 10, "Hello" );

	@Test
	void testSignatureHelpOnFunctionInvocation() throws Exception {
		var							defPath	= java.nio.file.Paths.get( "src/test/resources/files/signatureHelpTest.bx" );

		BoxLangTextDocumentService	svc		= new BoxLangTextDocumentService();
		svc.didOpen( new org.eclipse.lsp4j.DidOpenTextDocumentParams(
		    new org.eclipse.lsp4j.TextDocumentItem( defPath.toUri().toString(), "boxlang", 1, java.nio.file.Files.readString( defPath ) ) ) );

		// Line 32 (0-indexed: 31), getUser starts at col 21, inside parens is col 29
		SignatureHelpParams params = new SignatureHelpParams();
		params.setTextDocument( new TextDocumentIdentifier( defPath.toUri().toString() ) );
		params.setPosition( new Position( 31, 29 ) ); // Inside getUser( ... )

		var signatureHelp = svc.signatureHelp( params ).get();

		assertThat( signatureHelp ).isNotNull();
		assertThat( signatureHelp.getSignatures() ).isNotEmpty();

		// Should contain the function name and parameters
		var signature = signatureHelp.getSignatures().get( 0 );
		assertThat( signature.getLabel() ).contains( "getUser" );
		assertThat( signature.getLabel() ).contains( "id" );
	}

	@Test
	void testSignatureHelpShowsAllParameters() throws Exception {
		var							defPath	= java.nio.file.Paths.get( "src/test/resources/files/signatureHelpTest.bx" );

		BoxLangTextDocumentService	svc		= new BoxLangTextDocumentService();
		svc.didOpen( new org.eclipse.lsp4j.DidOpenTextDocumentParams(
		    new org.eclipse.lsp4j.TextDocumentItem( defPath.toUri().toString(), "boxlang", 1, java.nio.file.Files.readString( defPath ) ) ) );

		// Line 32 (0-indexed: 31), inside getUser(1) call
		SignatureHelpParams params = new SignatureHelpParams();
		params.setTextDocument( new TextDocumentIdentifier( defPath.toUri().toString() ) );
		params.setPosition( new Position( 31, 29 ) );

		var signatureHelp = svc.signatureHelp( params ).get();

		assertThat( signatureHelp ).isNotNull();

		var signature = signatureHelp.getSignatures().get( 0 );
		// Should have parameter information
		assertThat( signature.getParameters() ).isNotNull();
		assertThat( signature.getParameters().size() ).isAtLeast( 1 );

		// First parameter should be id
		var firstParam = signature.getParameters().get( 0 );
		assertThat( firstParam.getLabel().getLeft() ).contains( "id" );
	}

	@Test
	void testSignatureHelpActiveParameter() throws Exception {
		var							defPath	= java.nio.file.Paths.get( "src/test/resources/files/signatureHelpTest.bx" );

		BoxLangTextDocumentService	svc		= new BoxLangTextDocumentService();
		svc.didOpen( new org.eclipse.lsp4j.DidOpenTextDocumentParams(
		    new org.eclipse.lsp4j.TextDocumentItem( defPath.toUri().toString(), "boxlang", 1, java.nio.file.Files.readString( defPath ) ) ) );

		// Line 32 (0-indexed: 31), inside getUser(1) - first param
		SignatureHelpParams params = new SignatureHelpParams();
		params.setTextDocument( new TextDocumentIdentifier( defPath.toUri().toString() ) );
		params.setPosition( new Position( 31, 29 ) ); // First param

		var signatureHelp = svc.signatureHelp( params ).get();

		assertThat( signatureHelp ).isNotNull();
		// Active parameter should be 0 (first parameter)
		assertThat( signatureHelp.getActiveParameter() ).isEqualTo( 0 );
	}

	@Test
	void testSignatureHelpSecondParameter() throws Exception {
		var							defPath	= java.nio.file.Paths.get( "src/test/resources/files/signatureHelpTest.bx" );

		BoxLangTextDocumentService	svc		= new BoxLangTextDocumentService();
		svc.didOpen( new org.eclipse.lsp4j.DidOpenTextDocumentParams(
		    new org.eclipse.lsp4j.TextDocumentItem( defPath.toUri().toString(), "boxlang", 1, java.nio.file.Files.readString( defPath ) ) ) );

		// Line 35 (0-indexed: 34) has multiParamFunc( "test", 42 ) - after the comma for second param
		// multiParamFunc is at col 8-36
		SignatureHelpParams params = new SignatureHelpParams();
		params.setTextDocument( new TextDocumentIdentifier( defPath.toUri().toString() ) );
		params.setPosition( new Position( 34, 33 ) ); // After the comma, on "42"

		var signatureHelp = svc.signatureHelp( params ).get();

		assertThat( signatureHelp ).isNotNull();
		// Active parameter should be 1 (second parameter)
		assertThat( signatureHelp.getActiveParameter() ).isEqualTo( 1 );
	}

	@Test
	void testSignatureHelpShowsDocumentation() throws Exception {
		var							defPath	= java.nio.file.Paths.get( "src/test/resources/files/signatureHelpTest.bx" );

		BoxLangTextDocumentService	svc		= new BoxLangTextDocumentService();
		svc.didOpen( new org.eclipse.lsp4j.DidOpenTextDocumentParams(
		    new org.eclipse.lsp4j.TextDocumentItem( defPath.toUri().toString(), "boxlang", 1, java.nio.file.Files.readString( defPath ) ) ) );

		// Line 32 (0-indexed: 31), inside getUser call
		SignatureHelpParams params = new SignatureHelpParams();
		params.setTextDocument( new TextDocumentIdentifier( defPath.toUri().toString() ) );
		params.setPosition( new Position( 31, 29 ) );

		var signatureHelp = svc.signatureHelp( params ).get();

		assertThat( signatureHelp ).isNotNull();
		var signature = signatureHelp.getSignatures().get( 0 );

		// Should have documentation
		assertThat( signature.getDocumentation() ).isNotNull();
		String docContent = signature.getDocumentation().getRight().getValue();
		assertThat( docContent ).contains( "Retrieves a user by their unique identifier" );
	}

	@Test
	void testSignatureHelpForBIF() throws Exception {
		var							defPath	= java.nio.file.Paths.get( "src/test/resources/files/signatureHelpTest.bx" );

		BoxLangTextDocumentService	svc		= new BoxLangTextDocumentService();
		svc.didOpen( new org.eclipse.lsp4j.DidOpenTextDocumentParams(
		    new org.eclipse.lsp4j.TextDocumentItem( defPath.toUri().toString(), "boxlang", 1, java.nio.file.Files.readString( defPath ) ) ) );

		// Line 36 (0-indexed: 35) has arrayAppend( [], "item" ) - col 8-33
		SignatureHelpParams params = new SignatureHelpParams();
		params.setTextDocument( new TextDocumentIdentifier( defPath.toUri().toString() ) );
		params.setPosition( new Position( 35, 20 ) ); // Inside arrayAppend(

		var signatureHelp = svc.signatureHelp( params ).get();

		assertThat( signatureHelp ).isNotNull();
		assertThat( signatureHelp.getSignatures() ).isNotEmpty();

		var signature = signatureHelp.getSignatures().get( 0 );
		assertThat( signature.getLabel() ).containsMatch( "(?i)arrayAppend" );
	}

	@Test
	void testSignatureHelpForMethodInvocation() throws Exception {
		// First, index the class with the documented function
		Path classWithDocFunc = java.nio.file.Paths.get( "src/test/resources/files/ClassWithDocFunc.bx" );
		ProjectContextProvider.getInstance().getIndex().indexFile( classWithDocFunc.toUri() );

		var							defPath	= java.nio.file.Paths.get( "src/test/resources/files/signatureHelpTest.bx" );

		BoxLangTextDocumentService	svc		= new BoxLangTextDocumentService();
		svc.didOpen( new org.eclipse.lsp4j.DidOpenTextDocumentParams(
		    new org.eclipse.lsp4j.TextDocumentItem( defPath.toUri().toString(), "boxlang", 1, java.nio.file.Files.readString( defPath ) ) ) );

		// Line 38 (0-indexed: 37) has obj.documentedFunction( 10, "Hello" )
		SignatureHelpParams params = new SignatureHelpParams();
		params.setTextDocument( new TextDocumentIdentifier( defPath.toUri().toString() ) );
		params.setPosition( new Position( 37, 35 ) ); // Inside method call

		var signatureHelp = svc.signatureHelp( params ).get();

		assertThat( signatureHelp ).isNotNull();
		assertThat( signatureHelp.getSignatures() ).isNotEmpty();

		var signature = signatureHelp.getSignatures().get( 0 );
		assertThat( signature.getLabel() ).contains( "documentedFunction" );
	}

	@Test
	void testSignatureHelpForConstructor() throws Exception {
		// First, index the class
		Path classWithDocFunc = java.nio.file.Paths.get( "src/test/resources/files/ClassWithDocFunc.bx" );
		ProjectContextProvider.getInstance().getIndex().indexFile( classWithDocFunc.toUri() );

		var							defPath	= java.nio.file.Paths.get( "src/test/resources/files/signatureHelpTest.bx" );

		BoxLangTextDocumentService	svc		= new BoxLangTextDocumentService();
		svc.didOpen( new org.eclipse.lsp4j.DidOpenTextDocumentParams(
		    new org.eclipse.lsp4j.TextDocumentItem( defPath.toUri().toString(), "boxlang", 1, java.nio.file.Files.readString( defPath ) ) ) );

		// Line 37 (0-indexed: 36) has new ClassWithDocFunc()
		SignatureHelpParams params = new SignatureHelpParams();
		params.setTextDocument( new TextDocumentIdentifier( defPath.toUri().toString() ) );
		params.setPosition( new Position( 36, 38 ) ); // Inside constructor parens

		var signatureHelp = svc.signatureHelp( params ).get();

		// Constructor signature help (may return null if no init method defined)
		// Just verify it doesn't throw an error
		// If class has init(), it should show signature
	}

	@Test
	void testSignatureHelpOutsideFunctionCallReturnsNull() throws Exception {
		var							defPath	= java.nio.file.Paths.get( "src/test/resources/files/signatureHelpTest.bx" );

		BoxLangTextDocumentService	svc		= new BoxLangTextDocumentService();
		svc.didOpen( new org.eclipse.lsp4j.DidOpenTextDocumentParams(
		    new org.eclipse.lsp4j.TextDocumentItem( defPath.toUri().toString(), "boxlang", 1, java.nio.file.Files.readString( defPath ) ) ) );

		// Position outside any function call
		SignatureHelpParams params = new SignatureHelpParams();
		params.setTextDocument( new TextDocumentIdentifier( defPath.toUri().toString() ) );
		params.setPosition( new Position( 0, 0 ) ); // Start of file

		var signatureHelp = svc.signatureHelp( params ).get();

		// Should return null or empty signatures when not in a function call
		if ( signatureHelp != null ) {
			assertThat( signatureHelp.getSignatures() ).isEmpty();
		}
	}

	@Test
	void testSignatureHelpParameterLabels() throws Exception {
		var							defPath	= java.nio.file.Paths.get( "src/test/resources/files/signatureHelpTest.bx" );

		BoxLangTextDocumentService	svc		= new BoxLangTextDocumentService();
		svc.didOpen( new org.eclipse.lsp4j.DidOpenTextDocumentParams(
		    new org.eclipse.lsp4j.TextDocumentItem( defPath.toUri().toString(), "boxlang", 1, java.nio.file.Files.readString( defPath ) ) ) );

		// Line 35 (0-indexed: 34) has multiParamFunc( "test", 42 )
		SignatureHelpParams params = new SignatureHelpParams();
		params.setTextDocument( new TextDocumentIdentifier( defPath.toUri().toString() ) );
		params.setPosition( new Position( 34, 25 ) );

		var signatureHelp = svc.signatureHelp( params ).get();

		assertThat( signatureHelp ).isNotNull();
		var signature = signatureHelp.getSignatures().get( 0 );

		// Should have two parameters
		assertThat( signature.getParameters() ).hasSize( 2 );

		// Each parameter should have a label
		var	firstParam	= signature.getParameters().get( 0 );
		var	secondParam	= signature.getParameters().get( 1 );

		assertThat( firstParam.getLabel().getLeft() ).contains( "name" );
		assertThat( secondParam.getLabel().getLeft() ).contains( "age" );
	}
}
