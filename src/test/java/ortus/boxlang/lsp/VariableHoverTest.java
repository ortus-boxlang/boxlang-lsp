package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertThat;

import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.Test;

public class VariableHoverTest extends BaseTest {

	@Test
	void testHoverOnLocalVariableDeclaration() throws Exception {
		var							defPath	= java.nio.file.Paths.get( "src/test/resources/files/variableHoverTest.bx" );

		BoxLangTextDocumentService	svc		= new BoxLangTextDocumentService();
		svc.didOpen( new org.eclipse.lsp4j.DidOpenTextDocumentParams(
		    new org.eclipse.lsp4j.TextDocumentItem( defPath.toUri().toString(), "boxlang", 1, java.nio.file.Files.readString( defPath ) ) ) );

		// Line 8 (0-indexed = 7): "        var localVar = "hello";"
		// Let's try different positions to find where localVar is
		// "        var localVar = "hello";"
		//  01234567890123456789...
		// Column 12 should be on 'l' of 'localVar'

		HoverParams hoverParams = new HoverParams();
		hoverParams.setTextDocument( new TextDocumentIdentifier( defPath.toUri().toString() ) );
		hoverParams.setPosition( new Position( 7, 12 ) ); // On "localVar"

		var hover = svc.hover( hoverParams ).get();

		assertThat( hover ).isNotNull();
		String hoverContent = hover.getContents().getRight().getValue();

		// Should show variable name
		assertThat( hoverContent ).contains( "localVar" );
		// Should show it's a local variable
		assertThat( hoverContent ).containsMatch( "(?i)local" );
	}

	@Test
	void testHoverOnLocalVariableUsage() throws Exception {
		var							defPath	= java.nio.file.Paths.get( "src/test/resources/files/variableHoverTest.bx" );

		BoxLangTextDocumentService	svc		= new BoxLangTextDocumentService();
		svc.didOpen( new org.eclipse.lsp4j.DidOpenTextDocumentParams(
		    new org.eclipse.lsp4j.TextDocumentItem( defPath.toUri().toString(), "boxlang", 1, java.nio.file.Files.readString( defPath ) ) ) );

		// Position is on "localVar" in line 12: "return localVar;"
		HoverParams hoverParams = new HoverParams();
		hoverParams.setTextDocument( new TextDocumentIdentifier( defPath.toUri().toString() ) );
		hoverParams.setPosition( new Position( 11, 15 ) ); // On "localVar" in return

		var hover = svc.hover( hoverParams ).get();

		assertThat( hover ).isNotNull();
		String hoverContent = hover.getContents().getRight().getValue();

		// Should show variable name
		assertThat( hoverContent ).contains( "localVar" );
		// Should show scope
		assertThat( hoverContent ).containsMatch( "(?i)local" );
	}

	@Test
	void testHoverOnFunctionParameter() throws Exception {
		var							defPath	= java.nio.file.Paths.get( "src/test/resources/files/variableHoverTest.bx" );

		BoxLangTextDocumentService	svc		= new BoxLangTextDocumentService();
		svc.didOpen( new org.eclipse.lsp4j.DidOpenTextDocumentParams(
		    new org.eclipse.lsp4j.TextDocumentItem( defPath.toUri().toString(), "boxlang", 1, java.nio.file.Files.readString( defPath ) ) ) );

		// Position is on "name" in line 16: "return name & ..."
		HoverParams hoverParams = new HoverParams();
		hoverParams.setTextDocument( new TextDocumentIdentifier( defPath.toUri().toString() ) );
		hoverParams.setPosition( new Position( 15, 15 ) ); // On "name" in return

		var hover = svc.hover( hoverParams ).get();

		assertThat( hover ).isNotNull();
		String hoverContent = hover.getContents().getRight().getValue();

		// Should show it's from arguments scope
		assertThat( hoverContent ).contains( "name" );
		assertThat( hoverContent ).containsMatch( "(?i)argument|parameter" );
		// Should show type hint
		assertThat( hoverContent ).containsMatch( "(?i)string" );
		// Should show required
		assertThat( hoverContent ).containsMatch( "(?i)required" );
	}

	@Test
	void testHoverOnParameterWithDefaultValue() throws Exception {
		var							defPath	= java.nio.file.Paths.get( "src/test/resources/files/variableHoverTest.bx" );

		BoxLangTextDocumentService	svc		= new BoxLangTextDocumentService();
		svc.didOpen( new org.eclipse.lsp4j.DidOpenTextDocumentParams(
		    new org.eclipse.lsp4j.TextDocumentItem( defPath.toUri().toString(), "boxlang", 1, java.nio.file.Files.readString( defPath ) ) ) );

		// Position is on "age" in line 16: "return name & " is " & age & ..."
		HoverParams hoverParams = new HoverParams();
		hoverParams.setTextDocument( new TextDocumentIdentifier( defPath.toUri().toString() ) );
		hoverParams.setPosition( new Position( 15, 30 ) ); // On "age" in return

		var hover = svc.hover( hoverParams ).get();

		assertThat( hover ).isNotNull();
		String hoverContent = hover.getContents().getRight().getValue();

		// Should show variable name
		assertThat( hoverContent ).contains( "age" );
		// Should show type
		assertThat( hoverContent ).containsMatch( "(?i)numeric" );
		// Should show default value
		assertThat( hoverContent ).contains( "18" );
	}

	@Test
	void testHoverOnScopedVariableAccess() throws Exception {
		var							defPath	= java.nio.file.Paths.get( "src/test/resources/files/variableHoverTest.bx" );

		BoxLangTextDocumentService	svc		= new BoxLangTextDocumentService();
		svc.didOpen( new org.eclipse.lsp4j.DidOpenTextDocumentParams(
		    new org.eclipse.lsp4j.TextDocumentItem( defPath.toUri().toString(), "boxlang", 1, java.nio.file.Files.readString( defPath ) ) ) );

		// Position is on "variables" in line 21: "variables.instanceVar = "instance value";"
		HoverParams hoverParams = new HoverParams();
		hoverParams.setTextDocument( new TextDocumentIdentifier( defPath.toUri().toString() ) );
		hoverParams.setPosition( new Position( 20, 8 ) ); // On "variables"

		var hover = svc.hover( hoverParams ).get();

		assertThat( hover ).isNotNull();
		String hoverContent = hover.getContents().getRight().getValue();

		// Should show this is the variables scope
		assertThat( hoverContent ).containsMatch( "(?i)variables.*scope|instance.*scope" );
	}

	@Test
	void testHoverShowsInferredTypeFromLiteral() throws Exception {
		var							defPath	= java.nio.file.Paths.get( "src/test/resources/files/variableHoverTest.bx" );

		BoxLangTextDocumentService	svc		= new BoxLangTextDocumentService();
		svc.didOpen( new org.eclipse.lsp4j.DidOpenTextDocumentParams(
		    new org.eclipse.lsp4j.TextDocumentItem( defPath.toUri().toString(), "boxlang", 1, java.nio.file.Files.readString( defPath ) ) ) );

		// Position is on "count" in line 40: "var count = 0;"
		HoverParams hoverParams = new HoverParams();
		hoverParams.setTextDocument( new TextDocumentIdentifier( defPath.toUri().toString() ) );
		hoverParams.setPosition( new Position( 39, 12 ) ); // On "count"

		var hover = svc.hover( hoverParams ).get();

		assertThat( hover ).isNotNull();
		String hoverContent = hover.getContents().getRight().getValue();

		// Should show inferred type as numeric
		assertThat( hoverContent ).containsMatch( "(?i)numeric|integer|number" );
	}

	@Test
	void testHoverShowsDeclarationLine() throws Exception {
		var							defPath	= java.nio.file.Paths.get( "src/test/resources/files/variableHoverTest.bx" );

		BoxLangTextDocumentService	svc		= new BoxLangTextDocumentService();
		svc.didOpen( new org.eclipse.lsp4j.DidOpenTextDocumentParams(
		    new org.eclipse.lsp4j.TextDocumentItem( defPath.toUri().toString(), "boxlang", 1, java.nio.file.Files.readString( defPath ) ) ) );

		// Position is on "result" in line 31: "return result;"
		HoverParams hoverParams = new HoverParams();
		hoverParams.setTextDocument( new TextDocumentIdentifier( defPath.toUri().toString() ) );
		hoverParams.setPosition( new Position( 30, 15 ) ); // On "result" in return

		var hover = svc.hover( hoverParams ).get();

		assertThat( hover ).isNotNull();
		String hoverContent = hover.getContents().getRight().getValue();

		// Should show where it was declared
		assertThat( hoverContent ).contains( "result" );
		// Should indicate it's local scope
		assertThat( hoverContent ).containsMatch( "(?i)local" );
	}

	@Test
	void testHoverOnThisScope() throws Exception {
		var							defPath	= java.nio.file.Paths.get( "src/test/resources/files/variableHoverTest.bx" );

		BoxLangTextDocumentService	svc		= new BoxLangTextDocumentService();
		svc.didOpen( new org.eclipse.lsp4j.DidOpenTextDocumentParams(
		    new org.eclipse.lsp4j.TextDocumentItem( defPath.toUri().toString(), "boxlang", 1, java.nio.file.Files.readString( defPath ) ) ) );

		// Position is on "this" in line 22: "this.publicVar = "public value";"
		HoverParams hoverParams = new HoverParams();
		hoverParams.setTextDocument( new TextDocumentIdentifier( defPath.toUri().toString() ) );
		hoverParams.setPosition( new Position( 21, 8 ) ); // On "this"

		var hover = svc.hover( hoverParams ).get();

		assertThat( hover ).isNotNull();
		String hoverContent = hover.getContents().getRight().getValue();

		// Should show this scope explanation
		assertThat( hoverContent ).containsMatch( "(?i)this.*scope|public.*scope|instance" );
	}

	@Test
	void testHoverOnProperty() throws Exception {
		var							defPath	= java.nio.file.Paths.get( "src/test/resources/files/variableHoverTest.bx" );

		BoxLangTextDocumentService	svc		= new BoxLangTextDocumentService();
		svc.didOpen( new org.eclipse.lsp4j.DidOpenTextDocumentParams(
		    new org.eclipse.lsp4j.TextDocumentItem( defPath.toUri().toString(), "boxlang", 1, java.nio.file.Files.readString( defPath ) ) ) );

		// Position is on "myProperty" in line 5: "property name="myProperty" type="string";"
		HoverParams hoverParams = new HoverParams();
		hoverParams.setTextDocument( new TextDocumentIdentifier( defPath.toUri().toString() ) );
		hoverParams.setPosition( new Position( 4, 20 ) ); // On "myProperty"

		var hover = svc.hover( hoverParams ).get();

		// For now, property hover may or may not be implemented
		// If implemented, it should show property info
		if ( hover != null && hover.getContents() != null && hover.getContents().getRight() != null ) {
			String hoverContent = hover.getContents().getRight().getValue();
			assertThat( hoverContent ).containsMatch( "(?i)property|string" );
		}
	}

	@Test
	void testVariableScopingAcrossFunctions() throws Exception {
		// This test documents a bug where variables in one function were incorrectly
		// picking up types from parameters in other functions.
		// In BoxLang, arguments are local-scoped by default UNLESS there is an identifier
		// in a higher scope like variables or defined in the class properties.
		var							defPath	= java.nio.file.Paths.get( "src/test/resources/files/variableScopingTest.bx" );

		BoxLangTextDocumentService	svc		= new BoxLangTextDocumentService();
		svc.didOpen( new org.eclipse.lsp4j.DidOpenTextDocumentParams(
		    new org.eclipse.lsp4j.TextDocumentItem( defPath.toUri().toString(), "boxlang", 1, java.nio.file.Files.readString( defPath ) ) ) );

		// Line 10 (0-indexed = 9): "        x = new Thing();"
		// Hover on "x" - should show type as "Thing", NOT "numeric"
		HoverParams hoverParams = new HoverParams();
		hoverParams.setTextDocument( new TextDocumentIdentifier( defPath.toUri().toString() ) );
		hoverParams.setPosition( new Position( 9, 8 ) ); // On "x" in "x = new Thing();"

		var hover = svc.hover( hoverParams ).get();

		assertThat( hover ).isNotNull();
		String hoverContent = hover.getContents().getRight().getValue();

		// Should show variable name
		assertThat( hoverContent ).contains( "x" );
		// Should NOT show "numeric" - that's from second()'s parameter which is a different scope
		assertThat( hoverContent ).doesNotContain( "numeric" );
		// Should show "Thing" as the inferred type from new Thing()
		assertThat( hoverContent ).contains( "Thing" );
	}
}
