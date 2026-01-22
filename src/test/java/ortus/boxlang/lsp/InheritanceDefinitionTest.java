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
 * Tests for go-to-definition with inheritance and cross-file dependencies.
 * These tests verify that:
 * 1. Calling inherited functions without receiver works
 * 2. Calling methods on objects works
 * 3. Calling inherited methods on objects works
 */
public class InheritanceDefinitionTest extends BaseTest {

	private BoxLangTextDocumentService	svc;
	private ProjectContextProvider		provider;
	private ProjectIndex				index;
	private Path						testDir;

	@BeforeEach
	void setUp() throws Exception {
		svc			= new BoxLangTextDocumentService();
		provider	= ProjectContextProvider.getInstance();
		index		= new ProjectIndex();
		index.initialize( Paths.get( "src/test/resources/files/inheritanceTest" ) );
		provider.setIndex( index );
		testDir		= Paths.get( "src/test/resources/files/inheritanceTest" );

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
	 * Test that calling an inherited function without receiver navigates to parent class.
	 * In Child.bx: parentFunc() should go to Parent.bx
	 */
	@Test
	void testInheritedFunctionWithoutReceiver() throws Exception {
		Path	childPath	= testDir.resolve( "Child.bx" );
		String	childUri	= childPath.toUri().toString();
		Path	parentPath	= testDir.resolve( "Parent.bx" );
		String	parentUri	= parentPath.toUri().toString();

		// Position at 'parentFunc' on line 8: parentFunc();
		// Line 8 (0-indexed: 7), character at 'parentFunc'
		DefinitionParams params = new DefinitionParams();
		params.setTextDocument( new TextDocumentIdentifier( childUri ) );
		params.setPosition( new Position( 7, 5 ) );

		var result = svc.definition( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.getLeft() ).isNotNull();
		assertThat( result.getLeft().size() ).isGreaterThan( 0 );

		var def = result.getLeft().get( 0 );
		// Should navigate to Parent.bx
		assertThat( def.getUri() ).isEqualTo( parentUri );
		// parentFunc is defined on line 6 (0-indexed: 5)
		assertThat( def.getRange().getStart().getLine() ).isEqualTo( 5 );
	}

	/**
	 * Test that calling a method on an object navigates to the class definition.
	 * In ChildDependency.bx: child.childFunc() should go to Child.bx
	 */
	@Test
	void testMethodInvocationOnObject() throws Exception {
		Path	depPath		= testDir.resolve( "ChildDependency.bx" );
		String	depUri		= depPath.toUri().toString();
		Path	childPath	= testDir.resolve( "Child.bx" );
		String	childUri	= childPath.toUri().toString();

		// Position at 'childFunc' on line 10: child.childFunc();
		// Line 10 (0-indexed: 9), character at 'childFunc'
		DefinitionParams params = new DefinitionParams();
		params.setTextDocument( new TextDocumentIdentifier( depUri ) );
		params.setPosition( new Position( 9, 10 ) );

		var result = svc.definition( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.getLeft() ).isNotNull();
		assertThat( result.getLeft().size() ).isGreaterThan( 0 );

		var def = result.getLeft().get( 0 );
		// Should navigate to Child.bx
		assertThat( def.getUri() ).isEqualTo( childUri );
		// childFunc is defined on line 6 (0-indexed: 5)
		assertThat( def.getRange().getStart().getLine() ).isEqualTo( 5 );
	}

	/**
	 * Test that calling an inherited method on an object navigates to the parent class.
	 * In ChildDependency.bx: child.parentFunc() should go to Parent.bx
	 */
	@Test
	void testInheritedMethodInvocationOnObject() throws Exception {
		Path	depPath		= testDir.resolve( "ChildDependency.bx" );
		String	depUri		= depPath.toUri().toString();
		Path	parentPath	= testDir.resolve( "Parent.bx" );
		String	parentUri	= parentPath.toUri().toString();

		// Position at 'parentFunc' on line 13: child.parentFunc();
		// Line 13 (0-indexed: 12), character at 'parentFunc'
		DefinitionParams params = new DefinitionParams();
		params.setTextDocument( new TextDocumentIdentifier( depUri ) );
		params.setPosition( new Position( 12, 10 ) );

		var result = svc.definition( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.getLeft() ).isNotNull();
		assertThat( result.getLeft().size() ).isGreaterThan( 0 );

		var def = result.getLeft().get( 0 );
		// Should navigate to Parent.bx
		assertThat( def.getUri() ).isEqualTo( parentUri );
		// parentFunc is defined on line 6 (0-indexed: 5)
		assertThat( def.getRange().getStart().getLine() ).isEqualTo( 5 );
	}

	/**
	 * Test that calling an inherited function WITH this prefix navigates to parent class.
	 * In Child.bx: this.parentFunc() should go to Parent.bx
	 */
	@Test
	void testInheritedFunctionWithThisPrefix() throws Exception {
		Path	childPath	= testDir.resolve( "Child.bx" );
		String	childUri	= childPath.toUri().toString();
		Path	parentPath	= testDir.resolve( "Parent.bx" );
		String	parentUri	= parentPath.toUri().toString();

		// Position at 'parentFunc' on line 13: this.parentFunc();
		// Line 13 (0-indexed: 12), character at 'parentFunc' (after "this.")
		DefinitionParams params = new DefinitionParams();
		params.setTextDocument( new TextDocumentIdentifier( childUri ) );
		params.setPosition( new Position( 12, 8 ) );

		var result = svc.definition( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.getLeft() ).isNotNull();
		assertThat( result.getLeft().size() ).isGreaterThan( 0 );

		var def = result.getLeft().get( 0 );
		// Should navigate to Parent.bx
		assertThat( def.getUri() ).isEqualTo( parentUri );
		// parentFunc is defined on line 6 (0-indexed: 5)
		assertThat( def.getRange().getStart().getLine() ).isEqualTo( 5 );
	}

}
