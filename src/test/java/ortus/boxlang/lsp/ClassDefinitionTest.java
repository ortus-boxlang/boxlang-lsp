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
 * Tests for go-to-definition functionality on classes and interfaces.
 * Task 2.3: Go to Definition - Classes and Interfaces
 */
public class ClassDefinitionTest extends BaseTest {

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
		testDir		= Paths.get( "src/test/resources/files/classDefinitionTest" );

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
	 * Test go-to-definition on a class in a new expression.
	 * From: `new User()` in getUser()
	 * To: User.bx class definition
	 */
	@Test
	void testGoToDefinitionOnNewExpression() throws Exception {
		Path	testFilePath	= testDir.resolve( "UserService.bx" );
		String	testFileUri		= testFilePath.toUri().toString();
		Path	userFilePath	= testDir.resolve( "User.bx" );
		String	userFileUri		= userFilePath.toUri().toString();

		// Position at 'User' in `var defaultUser = new User();` on line 28 (0-indexed: 27)
		// Character position at 'User' after 'new '
		DefinitionParams params = new DefinitionParams();
		params.setTextDocument( new TextDocumentIdentifier( testFileUri ) );
		params.setPosition( new Position( 27, 30 ) ); // Position at 'User'

		var result = svc.definition( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.getLeft() ).isNotNull();
		assertThat( result.getLeft().size() ).isGreaterThan( 0 );

		var def = result.getLeft().get( 0 );
		// Should navigate to User.bx
		assertThat( def.getUri() ).isEqualTo( userFileUri );
		// Class definition starts at line 6 (0-indexed: 5) where "class extends="AbstractEntity"" is
		assertThat( def.getRange().getStart().getLine() ).isEqualTo( 5 );
	}

	/**
	 * Test go-to-definition on extends clause.
	 * From: `extends="AbstractEntity"` in User.bx
	 * To: AbstractEntity.bx class definition
	 */
	@Test
	void testGoToDefinitionOnExtendsClause() throws Exception {
		Path	testFilePath		= testDir.resolve( "User.bx" );
		String	testFileUri			= testFilePath.toUri().toString();
		Path	abstractFilePath	= testDir.resolve( "AbstractEntity.bx" );
		String	abstractFileUri		= abstractFilePath.toUri().toString();

		// Position at 'AbstractEntity' in `class extends="AbstractEntity"` on line 6
		// Line 6 (0-indexed: 5), character position at 'AbstractEntity'
		DefinitionParams params = new DefinitionParams();
		params.setTextDocument( new TextDocumentIdentifier( testFileUri ) );
		params.setPosition( new Position( 5, 20 ) ); // Position within 'AbstractEntity'

		var result = svc.definition( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.getLeft() ).isNotNull();
		assertThat( result.getLeft().size() ).isGreaterThan( 0 );

		var def = result.getLeft().get( 0 );
		// Should navigate to AbstractEntity.bx
		assertThat( def.getUri() ).isEqualTo( abstractFileUri );
		// AbstractEntity class definition starts at line 7 (0-indexed: 6)
		assertThat( def.getRange().getStart().getLine() ).isEqualTo( 6 );
	}

	/**
	 * Test go-to-definition on implements clause.
	 * From: `implements="IRepository"` in UserRepository.bx
	 * To: IRepository.bx interface definition
	 */
	@Test
	void testGoToDefinitionOnImplementsClause() throws Exception {
		Path	testFilePath		= testDir.resolve( "UserRepository.bx" );
		String	testFileUri			= testFilePath.toUri().toString();
		Path	interfaceFilePath	= testDir.resolve( "IRepository.bx" );
		String	interfaceFileUri	= interfaceFilePath.toUri().toString();

		// Position at 'IRepository' in `class implements="IRepository"` on line 6
		// Line 6 (0-indexed: 5), character position at 'IRepository'
		DefinitionParams params = new DefinitionParams();
		params.setTextDocument( new TextDocumentIdentifier( testFileUri ) );
		params.setPosition( new Position( 5, 23 ) ); // Position within 'IRepository'

		var result = svc.definition( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.getLeft() ).isNotNull();
		assertThat( result.getLeft().size() ).isGreaterThan( 0 );

		var def = result.getLeft().get( 0 );
		// Should navigate to IRepository.bx
		assertThat( def.getUri() ).isEqualTo( interfaceFileUri );
		// IRepository interface definition starts at line 6 (0-indexed: 5)
		assertThat( def.getRange().getStart().getLine() ).isEqualTo( 5 );
	}

	/**
	 * Test go-to-definition on a return type hint.
	 * From: `public User function createUser(...)` in UserService.bx
	 * To: User.bx class definition
	 */
	@Test
	void testGoToDefinitionOnReturnTypeHint() throws Exception {
		Path	testFilePath	= testDir.resolve( "UserService.bx" );
		String	testFileUri		= testFilePath.toUri().toString();
		Path	userFilePath	= testDir.resolve( "User.bx" );
		String	userFileUri		= userFilePath.toUri().toString();

		// Position at 'User' in `public User function createUser(...)` on line 16
		// Line 16 (0-indexed: 15), character position at 'User' (return type)
		DefinitionParams params = new DefinitionParams();
		params.setTextDocument( new TextDocumentIdentifier( testFileUri ) );
		params.setPosition( new Position( 15, 12 ) ); // Position at 'User'

		var result = svc.definition( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.getLeft() ).isNotNull();
		assertThat( result.getLeft().size() ).isGreaterThan( 0 );

		var def = result.getLeft().get( 0 );
		// Should navigate to User.bx
		assertThat( def.getUri() ).isEqualTo( userFileUri );
		// User class definition starts at line 6 (0-indexed: 5)
		assertThat( def.getRange().getStart().getLine() ).isEqualTo( 5 );
	}

	/**
	 * Test go-to-definition on a parameter type hint.
	 * From: `required User user` in createUser() in UserService.bx
	 * To: User.bx class definition
	 */
	@Test
	void testGoToDefinitionOnParameterTypeHint() throws Exception {
		Path	testFilePath	= testDir.resolve( "UserService.bx" );
		String	testFileUri		= testFilePath.toUri().toString();
		Path	userFilePath	= testDir.resolve( "User.bx" );
		String	userFileUri		= userFilePath.toUri().toString();

		// Position at 'User' in `required User user` on line 16
		// Line 16 (0-indexed: 15), character position at 'User' (parameter type)
		DefinitionParams params = new DefinitionParams();
		params.setTextDocument( new TextDocumentIdentifier( testFileUri ) );
		params.setPosition( new Position( 15, 44 ) ); // Position at 'User' in parameter

		var result = svc.definition( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.getLeft() ).isNotNull();
		assertThat( result.getLeft().size() ).isGreaterThan( 0 );

		var def = result.getLeft().get( 0 );
		// Should navigate to User.bx
		assertThat( def.getUri() ).isEqualTo( userFileUri );
		// User class definition starts at line 6 (0-indexed: 5)
		assertThat( def.getRange().getStart().getLine() ).isEqualTo( 5 );
	}

	/**
	 * Test go-to-definition on interface.
	 * From: `new UserRepository()` referencing a class that implements IRepository
	 * Verify the interface is navigable
	 */
	@Test
	void testGoToDefinitionOnInterface() throws Exception {
		Path	interfaceFilePath	= testDir.resolve( "IRepository.bx" );
		String	interfaceFileUri	= interfaceFilePath.toUri().toString();

		// UserService.bx has `implements="IRepository"` on line 7
		Path	testFilePath	= testDir.resolve( "UserService.bx" );
		String	testFileUri		= testFilePath.toUri().toString();

		// Position at 'IRepository' in `implements="IRepository"` on line 7
		// Line 7 (0-indexed: 6), character position at 'IRepository'
		DefinitionParams params = new DefinitionParams();
		params.setTextDocument( new TextDocumentIdentifier( testFileUri ) );
		params.setPosition( new Position( 6, 50 ) ); // Position within 'IRepository'

		var result = svc.definition( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.getLeft() ).isNotNull();
		assertThat( result.getLeft().size() ).isGreaterThan( 0 );

		var def = result.getLeft().get( 0 );
		// Should navigate to IRepository.bx
		assertThat( def.getUri() ).isEqualTo( interfaceFileUri );
		// IRepository interface definition starts at line 6 (0-indexed: 5)
		assertThat( def.getRange().getStart().getLine() ).isEqualTo( 5 );
	}

	/**
	 * Test go-to-definition when class not found returns empty.
	 */
	@Test
	void testGoToDefinitionOnUnknownClassReturnsEmpty() throws Exception {
		// Create a temp file with an unknown class reference
		Path	tempFile	= testDir.resolve( "TempUnknownClass.bx" );
		String	content		= """
		    class {
		        public function test() {
		            var unknown = new NonExistentClass();
		        }
		    }
		                        """;

		Files.writeString( tempFile, content );

		try {
			svc.didOpen( new DidOpenTextDocumentParams(
			    new TextDocumentItem( tempFile.toUri().toString(), "boxlang", 1, content ) ) );

			// Position at 'NonExistentClass'
			DefinitionParams params = new DefinitionParams();
			params.setTextDocument( new TextDocumentIdentifier( tempFile.toUri().toString() ) );
			params.setPosition( new Position( 2, 34 ) );

			var result = svc.definition( params ).get();

			// Should return empty or null since class doesn't exist
			if ( result != null && result.getLeft() != null ) {
				assertThat( result.getLeft().size() ).isEqualTo( 0 );
			}
		} finally {
			Files.deleteIfExists( tempFile );
		}
	}
}
