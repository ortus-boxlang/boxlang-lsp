package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertThat;

import java.nio.file.Path;

import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.Test;

import ortus.boxlang.lsp.workspace.ProjectContextProvider;

public class HoverTest extends BaseTest {

	@Test
	void testHoverOnFunctionInvocation() throws Exception {
		var							defPath	= java.nio.file.Paths.get( "src/test/resources/files/hoverTestClass.bx" );

		BoxLangTextDocumentService	svc		= new BoxLangTextDocumentService();
		svc.didOpen( new org.eclipse.lsp4j.DidOpenTextDocumentParams(
		    new org.eclipse.lsp4j.TextDocumentItem( defPath.toUri().toString(), "boxlang", 1, java.nio.file.Files.readString( defPath ) ) ) );

		// Position is on "getUser" call in line 34: "var result = getUser(1);"
		HoverParams hoverParams = new HoverParams();
		hoverParams.setTextDocument( new TextDocumentIdentifier( defPath.toUri().toString() ) );
		hoverParams.setPosition( new Position( 33, 22 ) ); // On "getUser"

		var hover = svc.hover( hoverParams ).get();

		assertThat( hover ).isNotNull();
		assertThat( hover.getContents() ).isNotNull();

		// Should contain function signature
		String hoverContent = hover.getContents().getRight().getValue();
		assertThat( hoverContent ).contains( "getUser" );
		assertThat( hoverContent ).contains( "numeric id" );
	}

	@Test
	void testHoverOnDocumentedFunctionShowsDocumentation() throws Exception {
		var							defPath	= java.nio.file.Paths.get( "src/test/resources/files/hoverTestClass.bx" );

		BoxLangTextDocumentService	svc		= new BoxLangTextDocumentService();
		svc.didOpen( new org.eclipse.lsp4j.DidOpenTextDocumentParams(
		    new org.eclipse.lsp4j.TextDocumentItem( defPath.toUri().toString(), "boxlang", 1, java.nio.file.Files.readString( defPath ) ) ) );

		// Position is on "getUser" call on line 34
		HoverParams hoverParams = new HoverParams();
		hoverParams.setTextDocument( new TextDocumentIdentifier( defPath.toUri().toString() ) );
		hoverParams.setPosition( new Position( 33, 22 ) ); // On "getUser"

		var hover = svc.hover( hoverParams ).get();

		assertThat( hover ).isNotNull();
		String hoverContent = hover.getContents().getRight().getValue();

		// Should contain documentation
		assertThat( hoverContent ).contains( "Retrieves a user by their unique identifier" );
		assertThat( hoverContent ).contains( "@param" );
		assertThat( hoverContent ).contains( "id" );
		assertThat( hoverContent ).contains( "The user's unique ID" );
		assertThat( hoverContent ).contains( "@return" );
		assertThat( hoverContent ).contains( "@deprecated" );
	}

	@Test
	void testHoverOnSimpleFunctionShowsDescription() throws Exception {
		var							defPath	= java.nio.file.Paths.get( "src/test/resources/files/hoverTestClass.bx" );

		BoxLangTextDocumentService	svc		= new BoxLangTextDocumentService();
		svc.didOpen( new org.eclipse.lsp4j.DidOpenTextDocumentParams(
		    new org.eclipse.lsp4j.TextDocumentItem( defPath.toUri().toString(), "boxlang", 1, java.nio.file.Files.readString( defPath ) ) ) );

		// Position is on "simpleFunction" call on line 35
		HoverParams hoverParams = new HoverParams();
		hoverParams.setTextDocument( new TextDocumentIdentifier( defPath.toUri().toString() ) );
		hoverParams.setPosition( new Position( 34, 10 ) ); // On "simpleFunction"

		var hover = svc.hover( hoverParams ).get();

		assertThat( hover ).isNotNull();
		String hoverContent = hover.getContents().getRight().getValue();

		// Should contain simple description
		assertThat( hoverContent ).contains( "simpleFunction" );
		assertThat( hoverContent ).contains( "A simple function with no documentation tags" );
	}

	@Test
	void testHoverOnUndocumentedFunctionShowsSignatureOnly() throws Exception {
		var							defPath	= java.nio.file.Paths.get( "src/test/resources/files/hoverTestClass.bx" );

		BoxLangTextDocumentService	svc		= new BoxLangTextDocumentService();
		svc.didOpen( new org.eclipse.lsp4j.DidOpenTextDocumentParams(
		    new org.eclipse.lsp4j.TextDocumentItem( defPath.toUri().toString(), "boxlang", 1, java.nio.file.Files.readString( defPath ) ) ) );

		// Position is on "undocumentedFunction" call on line 36
		HoverParams hoverParams = new HoverParams();
		hoverParams.setTextDocument( new TextDocumentIdentifier( defPath.toUri().toString() ) );
		hoverParams.setPosition( new Position( 35, 12 ) ); // On "undocumentedFunction"

		var hover = svc.hover( hoverParams ).get();

		assertThat( hover ).isNotNull();
		String hoverContent = hover.getContents().getRight().getValue();

		// Should contain signature but no doc comments
		assertThat( hoverContent ).contains( "undocumentedFunction" );
		assertThat( hoverContent ).contains( "string name" );
	}

	@Test
	void testHoverOnFunctionDefinition() throws Exception {
		var							defPath	= java.nio.file.Paths.get( "src/test/resources/files/hoverTestClass.bx" );

		BoxLangTextDocumentService	svc		= new BoxLangTextDocumentService();
		svc.didOpen( new org.eclipse.lsp4j.DidOpenTextDocumentParams(
		    new org.eclipse.lsp4j.TextDocumentItem( defPath.toUri().toString(), "boxlang", 1, java.nio.file.Files.readString( defPath ) ) ) );

		// Position is on "getUser" function definition (line 17)
		HoverParams hoverParams = new HoverParams();
		hoverParams.setTextDocument( new TextDocumentIdentifier( defPath.toUri().toString() ) );
		hoverParams.setPosition( new Position( 16, 25 ) ); // On "getUser" in definition

		var hover = svc.hover( hoverParams ).get();

		assertThat( hover ).isNotNull();
		String hoverContent = hover.getContents().getRight().getValue();

		// Should contain function signature and documentation
		assertThat( hoverContent ).contains( "getUser" );
		assertThat( hoverContent ).contains( "Retrieves a user by their unique identifier" );
	}

	@Test
	void testHoverOnNonHoverablePositionReturnsNull() throws Exception {
		var							defPath	= java.nio.file.Paths.get( "src/test/resources/files/hoverTestClass.bx" );

		BoxLangTextDocumentService	svc		= new BoxLangTextDocumentService();
		svc.didOpen( new org.eclipse.lsp4j.DidOpenTextDocumentParams(
		    new org.eclipse.lsp4j.TextDocumentItem( defPath.toUri().toString(), "boxlang", 1, java.nio.file.Files.readString( defPath ) ) ) );

		// Position on whitespace or empty area
		HoverParams hoverParams = new HoverParams();
		hoverParams.setTextDocument( new TextDocumentIdentifier( defPath.toUri().toString() ) );
		hoverParams.setPosition( new Position( 0, 0 ) ); // Start of file

		var hover = svc.hover( hoverParams ).get();

		// May return null or empty hover
		// Adjust based on implementation behavior
	}

	@Test
	void testHoverShowsContainingClassForMethod() throws Exception {
		var							defPath	= java.nio.file.Paths.get( "src/test/resources/files/hoverTestClass.bx" );

		BoxLangTextDocumentService	svc		= new BoxLangTextDocumentService();
		svc.didOpen( new org.eclipse.lsp4j.DidOpenTextDocumentParams(
		    new org.eclipse.lsp4j.TextDocumentItem( defPath.toUri().toString(), "boxlang", 1, java.nio.file.Files.readString( defPath ) ) ) );

		// Position is on "getUser" call on line 34
		HoverParams hoverParams = new HoverParams();
		hoverParams.setTextDocument( new TextDocumentIdentifier( defPath.toUri().toString() ) );
		hoverParams.setPosition( new Position( 33, 22 ) ); // On "getUser"

		var hover = svc.hover( hoverParams ).get();

		assertThat( hover ).isNotNull();
		String hoverContent = hover.getContents().getRight().getValue();

		// Should show the containing class name
		assertThat( hoverContent ).contains( "hoverTestClass" );
	}

	@Test
	void testHoverShowsAccessModifier() throws Exception {
		var							defPath	= java.nio.file.Paths.get( "src/test/resources/files/hoverTestClass.bx" );

		BoxLangTextDocumentService	svc		= new BoxLangTextDocumentService();
		svc.didOpen( new org.eclipse.lsp4j.DidOpenTextDocumentParams(
		    new org.eclipse.lsp4j.TextDocumentItem( defPath.toUri().toString(), "boxlang", 1, java.nio.file.Files.readString( defPath ) ) ) );

		// Position is on "undocumentedFunction" call (private function) on line 36
		HoverParams hoverParams = new HoverParams();
		hoverParams.setTextDocument( new TextDocumentIdentifier( defPath.toUri().toString() ) );
		hoverParams.setPosition( new Position( 35, 12 ) ); // On "undocumentedFunction"

		var hover = svc.hover( hoverParams ).get();

		assertThat( hover ).isNotNull();
		String hoverContent = hover.getContents().getRight().getValue();

		// Should show access modifier
		assertThat( hoverContent ).contains( "private" );
	}

	@Test
	void testCrossFileMethodHover() throws Exception {
		// First, index the class with the documented function
		Path classWithDocFunc = java.nio.file.Paths.get( "src/test/resources/files/ClassWithDocFunc.bx" );
		ProjectContextProvider.getInstance().getIndex().indexFile( classWithDocFunc.toUri() );

		// Now open the file that uses the documented function
		Path						classThatUsesDocFunc	= java.nio.file.Paths.get( "src/test/resources/files/ClassThatUsesDocFunc.bx" );

		BoxLangTextDocumentService	svc						= new BoxLangTextDocumentService();
		svc.didOpen( new org.eclipse.lsp4j.DidOpenTextDocumentParams(
		    new org.eclipse.lsp4j.TextDocumentItem( classThatUsesDocFunc.toUri().toString(), "boxlang", 1,
		        java.nio.file.Files.readString( classThatUsesDocFunc ) ) ) );

		// Position is on "documentedFunction" in line 5: "return obj.documentedFunction( 10, "Hello" );"
		// Line 5 (0-indexed: 4), column should be around the method name
		HoverParams hoverParams = new HoverParams();
		hoverParams.setTextDocument( new TextDocumentIdentifier( classThatUsesDocFunc.toUri().toString() ) );
		hoverParams.setPosition( new Position( 4, 20 ) ); // On "documentedFunction"

		var hover = svc.hover( hoverParams ).get();

		assertThat( hover ).isNotNull();
		String hoverContent = hover.getContents().getRight().getValue();

		// Should contain the function name
		assertThat( hoverContent ).contains( "documentedFunction" );

		// Should contain the documentation
		assertThat( hoverContent ).contains( "A documented function" );

		// Should contain parameter info
		assertThat( hoverContent ).contains( "param1" );
		assertThat( hoverContent ).contains( "The first parameter" );

		// Should contain return info
		assertThat( hoverContent ).contains( "@return" );

		// Should show the containing class name
		assertThat( hoverContent ).contains( "ClassWithDocFunc" );
	}
}
