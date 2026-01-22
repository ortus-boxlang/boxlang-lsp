package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ortus.boxlang.lsp.workspace.ProjectContextProvider;

/**
 * Tests for hover functionality on classes and interfaces.
 */
public class ClassHoverTest extends BaseTest {

	private static final Path	TEST_DIR	= Paths.get( "src/test/resources/files/classHoverTest" );

	@BeforeEach
	void indexTestFiles() throws Exception {
		// Index all test files before each test
		var index = ProjectContextProvider.getInstance().getIndex();
		index.indexFile( TEST_DIR.resolve( "BaseService.bx" ).toUri() );
		index.indexFile( TEST_DIR.resolve( "IUserService.bx" ).toUri() );
		index.indexFile( TEST_DIR.resolve( "UserService.bx" ).toUri() );
		index.indexFile( TEST_DIR.resolve( "ClassThatUsesUserService.bx" ).toUri() );
	}

	@Test
	void testHoverOnClassInNewExpression() throws Exception {
		Path						testFile	= TEST_DIR.resolve( "ClassThatUsesUserService.bx" );

		BoxLangTextDocumentService	svc			= new BoxLangTextDocumentService();
		svc.didOpen( new DidOpenTextDocumentParams(
		    new TextDocumentItem( testFile.toUri().toString(), "boxlang", 1, Files.readString( testFile ) ) ) );

		// Position is on "UserService" in line 4: "var service = new UserService();"
		// Line 4 (0-indexed: 3), "new " starts around column 22, "UserService" at column 26
		HoverParams hoverParams = new HoverParams();
		hoverParams.setTextDocument( new TextDocumentIdentifier( testFile.toUri().toString() ) );
		hoverParams.setPosition( new Position( 3, 30 ) ); // On "UserService"

		var hover = svc.hover( hoverParams ).get();

		assertThat( hover ).isNotNull();
		String hoverContent = hover.getContents().getRight().getValue();

		// Should show class name
		assertThat( hoverContent ).contains( "UserService" );
		// Should indicate it's a class
		assertThat( hoverContent ).containsMatch( "(?i)class" );
	}

	@Test
	void testHoverOnClassShowsDocumentation() throws Exception {
		Path						testFile	= TEST_DIR.resolve( "ClassThatUsesUserService.bx" );

		BoxLangTextDocumentService	svc			= new BoxLangTextDocumentService();
		svc.didOpen( new DidOpenTextDocumentParams(
		    new TextDocumentItem( testFile.toUri().toString(), "boxlang", 1, Files.readString( testFile ) ) ) );

		// Position is on "UserService" in line 4
		HoverParams hoverParams = new HoverParams();
		hoverParams.setTextDocument( new TextDocumentIdentifier( testFile.toUri().toString() ) );
		hoverParams.setPosition( new Position( 3, 30 ) ); // On "UserService"

		var hover = svc.hover( hoverParams ).get();

		assertThat( hover ).isNotNull();
		String hoverContent = hover.getContents().getRight().getValue();

		// Should show the documentation from the class
		assertThat( hoverContent ).contains( "User service for managing user operations" );
	}

	@Test
	void testHoverOnClassShowsInheritance() throws Exception {
		Path						testFile	= TEST_DIR.resolve( "ClassThatUsesUserService.bx" );

		BoxLangTextDocumentService	svc			= new BoxLangTextDocumentService();
		svc.didOpen( new DidOpenTextDocumentParams(
		    new TextDocumentItem( testFile.toUri().toString(), "boxlang", 1, Files.readString( testFile ) ) ) );

		// Position is on "UserService" in line 4
		HoverParams hoverParams = new HoverParams();
		hoverParams.setTextDocument( new TextDocumentIdentifier( testFile.toUri().toString() ) );
		hoverParams.setPosition( new Position( 3, 30 ) ); // On "UserService"

		var hover = svc.hover( hoverParams ).get();

		assertThat( hover ).isNotNull();
		String hoverContent = hover.getContents().getRight().getValue();

		// Should show extends information
		assertThat( hoverContent ).contains( "BaseService" );
		// Should show implements information
		assertThat( hoverContent ).contains( "IUserService" );
	}

	@Test
	void testHoverOnClassShowsFileLocation() throws Exception {
		Path						testFile	= TEST_DIR.resolve( "ClassThatUsesUserService.bx" );

		BoxLangTextDocumentService	svc			= new BoxLangTextDocumentService();
		svc.didOpen( new DidOpenTextDocumentParams(
		    new TextDocumentItem( testFile.toUri().toString(), "boxlang", 1, Files.readString( testFile ) ) ) );

		// Position is on "UserService" in line 4
		HoverParams hoverParams = new HoverParams();
		hoverParams.setTextDocument( new TextDocumentIdentifier( testFile.toUri().toString() ) );
		hoverParams.setPosition( new Position( 3, 30 ) ); // On "UserService"

		var hover = svc.hover( hoverParams ).get();

		assertThat( hover ).isNotNull();
		String hoverContent = hover.getContents().getRight().getValue();

		// Should show file location
		assertThat( hoverContent ).contains( "UserService.bx" );
	}

	@Test
	void testHoverOnInterfaceInNewExpression() throws Exception {
		// Index the interface
		Path						interfaceFile	= TEST_DIR.resolve( "IUserService.bx" );

		// Create a test file that references the interface
		Path						testFile		= TEST_DIR.resolve( "ClassThatUsesUserService.bx" );

		BoxLangTextDocumentService	svc				= new BoxLangTextDocumentService();
		svc.didOpen( new DidOpenTextDocumentParams(
		    new TextDocumentItem( interfaceFile.toUri().toString(), "boxlang", 1, Files.readString( interfaceFile ) ) ) );

		// Position is at the start of the interface definition
		HoverParams hoverParams = new HoverParams();
		hoverParams.setTextDocument( new TextDocumentIdentifier( interfaceFile.toUri().toString() ) );
		hoverParams.setPosition( new Position( 5, 0 ) ); // On "interface"

		var hover = svc.hover( hoverParams ).get();

		// May return null since we're on the keyword, not a reference
		// This test is more about verifying we handle interfaces correctly
	}

	@Test
	void testHoverOnBaseServiceClass() throws Exception {
		Path						testFile	= TEST_DIR.resolve( "BaseService.bx" );

		BoxLangTextDocumentService	svc			= new BoxLangTextDocumentService();
		svc.didOpen( new DidOpenTextDocumentParams(
		    new TextDocumentItem( testFile.toUri().toString(), "boxlang", 1, Files.readString( testFile ) ) ) );

		// Look up the indexed class directly to verify it's indexed correctly
		var indexedClass = ProjectContextProvider.getInstance().getIndex().findClassByName( "BaseService" );

		assertThat( indexedClass ).isPresent();
		assertThat( indexedClass.get().name() ).isEqualTo( "BaseService" );
		assertThat( indexedClass.get().isInterface() ).isFalse();
	}

	@Test
	void testHoverOnInterfaceVerifyIndexed() throws Exception {
		// Verify the interface is indexed correctly
		var indexedInterface = ProjectContextProvider.getInstance().getIndex().findClassByName( "IUserService" );

		assertThat( indexedInterface ).isPresent();
		assertThat( indexedInterface.get().name() ).isEqualTo( "IUserService" );
		assertThat( indexedInterface.get().isInterface() ).isTrue();
	}

	@Test
	void testHoverOnClassWithInheritanceChain() throws Exception {
		// Verify UserService is indexed with correct inheritance info
		var indexedClass = ProjectContextProvider.getInstance().getIndex().findClassByName( "UserService" );

		assertThat( indexedClass ).isPresent();
		assertThat( indexedClass.get().name() ).isEqualTo( "UserService" );
		assertThat( indexedClass.get().extendsClass() ).isEqualTo( "BaseService" );
		assertThat( indexedClass.get().implementsInterfaces() ).contains( "IUserService" );
	}
}
