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
 * Tests for go-to-definition functionality on properties.
 * Task 2.4: Go to Definition - Properties
 */
public class PropertyDefinitionTest extends BaseTest {

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
		testDir = Paths.get( "src/test/resources/files/propertyDefinitionTest" );

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
	 * Test go-to-definition on property access via variables scope.
	 * From: `variables.username` in getUsername()
	 * To: `property name="username"` declaration
	 */
	@Test
	void testGoToDefinitionOnVariablesScopedProperty() throws Exception {
		Path				testFilePath	= testDir.resolve( "User.bx" );
		String				testFileUri		= testFilePath.toUri().toString();

		// Position at 'username' in `return variables.username;` on line 13 (0-indexed: 12)
		DefinitionParams	params			= new DefinitionParams();
		params.setTextDocument( new TextDocumentIdentifier( testFileUri ) );
		params.setPosition( new Position( 12, 26 ) ); // Position at 'username' after 'variables.'

		var result = svc.definition( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.getLeft() ).isNotNull();
		assertThat( result.getLeft().size() ).isGreaterThan( 0 );

		var def = result.getLeft().get( 0 );
		// Should navigate to same file
		assertThat( def.getUri() ).isEqualTo( testFileUri );
		// Property 'username' is declared on line 5 (0-indexed: 4)
		assertThat( def.getRange().getStart().getLine() ).isEqualTo( 4 );
	}

	/**
	 * Test go-to-definition on property access via this scope.
	 * From: `this.age` in processUser()
	 * To: `property name="age"` declaration
	 */
	@Test
	void testGoToDefinitionOnThisScopedProperty() throws Exception {
		Path				testFilePath	= testDir.resolve( "User.bx" );
		String				testFileUri		= testFilePath.toUri().toString();

		// Position at 'age' in `var userAge = this.age;` on line 45 (0-indexed: 44)
		DefinitionParams	params			= new DefinitionParams();
		params.setTextDocument( new TextDocumentIdentifier( testFileUri ) );
		params.setPosition( new Position( 44, 27 ) ); // Position at 'age' after 'this.'

		var result = svc.definition( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.getLeft() ).isNotNull();
		assertThat( result.getLeft().size() ).isGreaterThan( 0 );

		var def = result.getLeft().get( 0 );
		// Should navigate to same file
		assertThat( def.getUri() ).isEqualTo( testFileUri );
		// Property 'age' is declared on line 7 (0-indexed: 6)
		assertThat( def.getRange().getStart().getLine() ).isEqualTo( 6 );
	}

	/**
	 * Test go-to-definition on unqualified property access.
	 * From: `email` (unqualified) in processUser()
	 * To: `property name="email"` declaration
	 */
	@Test
	void testGoToDefinitionOnUnqualifiedProperty() throws Exception {
		Path				testFilePath	= testDir.resolve( "User.bx" );
		String				testFileUri		= testFilePath.toUri().toString();

		// Position at 'email' in `var userEmail = email;` on line 42 (0-indexed: 41)
		DefinitionParams	params			= new DefinitionParams();
		params.setTextDocument( new TextDocumentIdentifier( testFileUri ) );
		params.setPosition( new Position( 41, 24 ) ); // Position at 'email'

		var result = svc.definition( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.getLeft() ).isNotNull();
		assertThat( result.getLeft().size() ).isGreaterThan( 0 );

		var def = result.getLeft().get( 0 );
		// Should navigate to same file
		assertThat( def.getUri() ).isEqualTo( testFileUri );
		// Property 'email' is declared on line 6 (0-indexed: 5)
		assertThat( def.getRange().getStart().getLine() ).isEqualTo( 5 );
	}

	/**
	 * Test go-to-definition on inherited property access.
	 * From: `variables.id` in processUser() (id is inherited from BaseEntity)
	 * To: `property name="id"` in BaseEntity.bx
	 */
	@Test
	void testGoToDefinitionOnInheritedProperty() throws Exception {
		Path				testFilePath	= testDir.resolve( "User.bx" );
		String				testFileUri		= testFilePath.toUri().toString();
		Path				baseFilePath	= testDir.resolve( "BaseEntity.bx" );
		String				baseFileUri		= baseFilePath.toUri().toString();

		// Position at 'id' in `var entityId = variables.id;` on line 39 (0-indexed: 38)
		DefinitionParams	params			= new DefinitionParams();
		params.setTextDocument( new TextDocumentIdentifier( testFileUri ) );
		params.setPosition( new Position( 38, 35 ) ); // Position at 'id' after 'variables.'

		var result = svc.definition( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.getLeft() ).isNotNull();
		assertThat( result.getLeft().size() ).isGreaterThan( 0 );

		var def = result.getLeft().get( 0 );
		// Should navigate to BaseEntity.bx
		assertThat( def.getUri() ).isEqualTo( baseFileUri );
		// Property 'id' is declared on line 5 (0-indexed: 4)
		assertThat( def.getRange().getStart().getLine() ).isEqualTo( 4 );
	}

	/**
	 * Test go-to-definition on property in unqualified return expression.
	 * From: `username` in getUserSummary()
	 * To: `property name="username"` declaration
	 */
	@Test
	void testGoToDefinitionOnUnqualifiedPropertyInReturn() throws Exception {
		Path				testFilePath	= testDir.resolve( "User.bx" );
		String				testFileUri		= testFilePath.toUri().toString();

		// Position at 'username' in `return username & ...` on line 60 (0-indexed: 59)
		DefinitionParams	params			= new DefinitionParams();
		params.setTextDocument( new TextDocumentIdentifier( testFileUri ) );
		params.setPosition( new Position( 59, 15 ) ); // Position at 'username'

		var result = svc.definition( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.getLeft() ).isNotNull();
		assertThat( result.getLeft().size() ).isGreaterThan( 0 );

		var def = result.getLeft().get( 0 );
		// Should navigate to same file
		assertThat( def.getUri() ).isEqualTo( testFileUri );
		// Property 'username' is declared on line 5 (0-indexed: 4)
		assertThat( def.getRange().getStart().getLine() ).isEqualTo( 4 );
	}

	/**
	 * Test go-to-definition on property in assignment (setter).
	 * From: `variables.username` in setUsername()
	 * To: `property name="username"` declaration
	 */
	@Test
	void testGoToDefinitionOnPropertyInSetter() throws Exception {
		Path				testFilePath	= testDir.resolve( "User.bx" );
		String				testFileUri		= testFilePath.toUri().toString();

		// Position at 'username' in `variables.username = username;` on line 20 (0-indexed: 19)
		DefinitionParams	params			= new DefinitionParams();
		params.setTextDocument( new TextDocumentIdentifier( testFileUri ) );
		params.setPosition( new Position( 19, 18 ) ); // Position at 'username' after 'variables.'

		var result = svc.definition( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.getLeft() ).isNotNull();
		assertThat( result.getLeft().size() ).isGreaterThan( 0 );

		var def = result.getLeft().get( 0 );
		// Should navigate to same file
		assertThat( def.getUri() ).isEqualTo( testFileUri );
		// Property 'username' is declared on line 5 (0-indexed: 4)
		assertThat( def.getRange().getStart().getLine() ).isEqualTo( 4 );
	}

	/**
	 * Test that go-to-definition on a non-existent property returns empty.
	 */
	@Test
	void testGoToDefinitionOnUnknownPropertyReturnsEmpty() throws Exception {
		// Create a temp file with a non-existent property reference
		Path	tempFile	= testDir.resolve( "TempUnknownProperty.bx" );
		String	content		= """
		                      class {
		                          public function test() {
		                              return variables.nonExistentProperty;
		                          }
		                      }
		                                          """;

		Files.writeString( tempFile, content );

		try {
			svc.didOpen( new DidOpenTextDocumentParams(
			    new TextDocumentItem( tempFile.toUri().toString(), "boxlang", 1, content ) ) );

			// Position at 'nonExistentProperty'
			DefinitionParams params = new DefinitionParams();
			params.setTextDocument( new TextDocumentIdentifier( tempFile.toUri().toString() ) );
			params.setPosition( new Position( 2, 32 ) );

			var result = svc.definition( params ).get();

			// Should return empty since property doesn't exist
			if ( result != null && result.getLeft() != null ) {
				assertThat( result.getLeft().size() ).isEqualTo( 0 );
			}
		} finally {
			Files.deleteIfExists( tempFile );
		}
	}

	/**
	 * Test that go-to-definition on property access from unknown receiver returns empty.
	 * This prevents incorrectly matching 'user' in 'a.user' to a class named 'User'.
	 */
	@Test
	void testGoToDefinitionOnUnknownReceiverReturnsEmpty() throws Exception {
		// Create a temp file with an undefined variable and property access
		Path	tempFile	= testDir.resolve( "TempUnknownReceiver.bx" );
		String	content		= """
		                      class {
		                          public function test() {
		                              a.user;
		                          }
		                      }
		                                          """;

		Files.writeString( tempFile, content );

		try {
			svc.didOpen( new DidOpenTextDocumentParams(
			    new TextDocumentItem( tempFile.toUri().toString(), "boxlang", 1, content ) ) );

			// Position at 'user' in 'a.user'
			DefinitionParams params = new DefinitionParams();
			params.setTextDocument( new TextDocumentIdentifier( tempFile.toUri().toString() ) );
			params.setPosition( new Position( 2, 15 ) ); // Position at 'user' after 'a.'

			var result = svc.definition( params ).get();

			// Should return empty since we can't determine the type of 'a'
			// and shouldn't match 'user' to the User class
			if ( result != null && result.getLeft() != null ) {
				assertThat( result.getLeft().size() ).isEqualTo( 0 );
			}
		} finally {
			Files.deleteIfExists( tempFile );
		}
	}
}
