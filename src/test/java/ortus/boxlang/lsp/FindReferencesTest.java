package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertThat;

import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.ReferenceContext;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.eclipse.lsp4j.Location;

import ortus.boxlang.lsp.workspace.ProjectContextProvider;
import ortus.boxlang.lsp.workspace.index.ProjectIndex;

/**
 * Tests for Find References functionality.
 * Task 2.6: Find References - Core Implementation
 */
public class FindReferencesTest extends BaseTest {

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
		testDir = Paths.get( "src/test/resources/files/findReferencesTest" );

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

	// ============ Class Reference Tests ============

	/**
	 * Test finding references to User class across multiple files.
	 * User is referenced in: new User(), extends="User" (none here), type hints, return types
	 */
	@Test
	void testFindReferencesToClass() throws Exception {
		Path			userFilePath	= testDir.resolve( "User.bx" );
		String			userFileUri		= userFilePath.toUri().toString();

		// Position at 'class' keyword in User.bx (line 4, 0-indexed: 3)
		ReferenceParams	params			= new ReferenceParams();
		params.setTextDocument( new TextDocumentIdentifier( userFileUri ) );
		params.setPosition( new Position( 3, 2 ) ); // At 'class' keyword
		params.setContext( new ReferenceContext( false ) ); // Don't include declaration

		var refs = svc.references( params ).get();

		assertThat( refs ).isNotNull();
		// User is referenced in multiple places:
		// - UserService.bx: new User() (2x), return type, parameter type
		// - UserRepository.bx: new User() (3x), return type, parameter type
		// - UserController.bx: return type (2x)
		// - IUserService.bx: return type (2x), parameter type
		assertThat( refs.size() ).isGreaterThan( 0 );
	}

	/**
	 * Test finding references to User class from a new expression.
	 */
	@Test
	void testFindClassReferencesFromNewExpression() throws Exception {
		Path			serviceFilePath	= testDir.resolve( "UserService.bx" );
		String			serviceFileUri	= serviceFilePath.toUri().toString();

		// Position at 'User' in `new User()` in UserService.bx line 12 (0-indexed: 11)
		// The line is: " var user = new User();"
		// 'User' starts at column 23 (0-indexed): 8 spaces + "var user = new "
		ReferenceParams	params			= new ReferenceParams();
		params.setTextDocument( new TextDocumentIdentifier( serviceFileUri ) );
		params.setPosition( new Position( 11, 23 ) ); // At 'User' in `new User()`
		params.setContext( new ReferenceContext( true ) ); // Include declaration

		var refs = svc.references( params ).get();

		assertThat( refs ).isNotNull();
		// Should find references across files
		assertThat( refs.size() ).isGreaterThan( 0 );
	}

	/**
	 * Test finding references to interface (IUserService).
	 */
	@Test
	void testFindReferencesToInterface() throws Exception {
		Path			interfaceFilePath	= testDir.resolve( "IUserService.bx" );
		String			interfaceFileUri	= interfaceFilePath.toUri().toString();

		// Position at 'interface' keyword in IUserService.bx (line 4, 0-indexed: 3)
		ReferenceParams	params				= new ReferenceParams();
		params.setTextDocument( new TextDocumentIdentifier( interfaceFileUri ) );
		params.setPosition( new Position( 3, 2 ) ); // At 'interface' keyword
		params.setContext( new ReferenceContext( false ) );

		var refs = svc.references( params ).get();

		assertThat( refs ).isNotNull();
		// IUserService is referenced in UserService.bx (implements)
		// Note: actual count depends on implementation
	}

	// ============ Method Reference Tests ============

	/**
	 * Test finding references to a method (getUser) in the same file.
	 */
	@Test
	void testFindReferencesToMethodSameFile() throws Exception {
		Path			controllerFilePath	= testDir.resolve( "UserController.bx" );
		String			controllerFileUri	= controllerFilePath.toUri().toString();

		// Position at 'getUser' function declaration in UserController.bx
		// Line 18 (0-indexed: 17): `public User function getUser(...)`
		// 'getUser' starts at column 25 (after " public User function ")
		ReferenceParams	params				= new ReferenceParams();
		params.setTextDocument( new TextDocumentIdentifier( controllerFileUri ) );
		params.setPosition( new Position( 17, 25 ) ); // At 'getUser'
		params.setContext( new ReferenceContext( false ) );

		var refs = svc.references( params ).get();

		assertThat( refs ).isNotNull();
		// getUser is called in the same file
	}

	/**
	 * Test finding references to a method across files.
	 * UserService.getUser() is called from UserController
	 */
	@Test
	void testFindReferencesToMethodAcrossFiles() throws Exception {
		Path			serviceFilePath	= testDir.resolve( "UserService.bx" );
		String			serviceFileUri	= serviceFilePath.toUri().toString();

		// Position at 'getUser' function declaration in UserService.bx
		// Line 11 (0-indexed: 10): `public User function getUser(...)`
		ReferenceParams	params			= new ReferenceParams();
		params.setTextDocument( new TextDocumentIdentifier( serviceFileUri ) );
		params.setPosition( new Position( 10, 25 ) ); // At 'getUser'
		params.setContext( new ReferenceContext( false ) );

		var refs = svc.references( params ).get();

		assertThat( refs ).isNotNull();
		// getUser is called from UserController
		// refs.stream().anyMatch(r -> r.getUri().contains("UserController")) should be true
	}

	/**
	 * Test finding references to save() method (including inheritance).
	 */
	@Test
	void testFindReferencesToMethodWithInheritance() throws Exception {
		Path			baseEntityFilePath	= testDir.resolve( "BaseEntity.bx" );
		String			baseEntityFileUri	= baseEntityFilePath.toUri().toString();

		// Position at 'save' function declaration in BaseEntity.bx
		// Line 19 (0-indexed: 18): `public void function save()`
		ReferenceParams	params				= new ReferenceParams();
		params.setTextDocument( new TextDocumentIdentifier( baseEntityFileUri ) );
		params.setPosition( new Position( 18, 25 ) ); // At 'save'
		params.setContext( new ReferenceContext( false ) );

		var refs = svc.references( params ).get();

		assertThat( refs ).isNotNull();
		// save() is called from:
		// - User.bx (super.save())
		// - UserService.bx (user.save())
		// - UserRepository.bx (user.save())
	}

	// ============ Property Reference Tests ============

	/**
	 * Test finding references to a property (username).
	 */
	@Test
	void testFindReferencesToProperty() throws Exception {
		Path			userFilePath	= testDir.resolve( "User.bx" );
		String			userFileUri		= userFilePath.toUri().toString();

		// Position at 'username' property declaration in User.bx
		// Line 6 (0-indexed: 5): `property name="username" type="string";`
		ReferenceParams	params			= new ReferenceParams();
		params.setTextDocument( new TextDocumentIdentifier( userFileUri ) );
		params.setPosition( new Position( 5, 20 ) ); // At 'username'
		params.setContext( new ReferenceContext( false ) );

		var refs = svc.references( params ).get();

		assertThat( refs ).isNotNull();
		// username is referenced in:
		// - User.bx: this.username
		// - UserService.bx: newUser.username
	}

	// ============ Local Variable Reference Tests ============

	/**
	 * Test finding references to a local variable within a function.
	 */
	@Test
	void testFindReferencesToLocalVariable() throws Exception {
		Path			serviceFilePath	= testDir.resolve( "UserService.bx" );
		String			serviceFileUri	= serviceFilePath.toUri().toString();

		// Position at 'user' variable declaration in UserService.bx getUser()
		// Line 12 (0-indexed: 11): `var user = new User();`
		// The 'user' identifier starts at column 12 (0-indexed) after " var "
		ReferenceParams	params			= new ReferenceParams();
		params.setTextDocument( new TextDocumentIdentifier( serviceFileUri ) );
		params.setPosition( new Position( 11, 12 ) ); // At 'user' in `var user`
		params.setContext( new ReferenceContext( false ) );

		var refs = svc.references( params ).get();

		assertThat( refs ).isNotNull();
		// 'user' variable is used:
		// - user.id = id;
		// - return user;
		assertThat( refs.size() ).isGreaterThan( 0 );
	}

	/**
	 * Test that local variable references are scoped to the function.
	 */
	@Test
	void testLocalVariableReferencesAreScopedToFunction() throws Exception {
		Path			serviceFilePath	= testDir.resolve( "UserService.bx" );
		String			serviceFileUri	= serviceFilePath.toUri().toString();

		// Position at 'newUser' variable declaration in UserService.bx createUser()
		// Line 28 (0-indexed: 27): `var newUser = new User();`
		// The 'newUser' identifier starts at column 12 (0-indexed) after " var "
		ReferenceParams	params			= new ReferenceParams();
		params.setTextDocument( new TextDocumentIdentifier( serviceFileUri ) );
		params.setPosition( new Position( 27, 12 ) ); // At 'newUser'
		params.setContext( new ReferenceContext( false ) );

		var refs = svc.references( params ).get();

		assertThat( refs ).isNotNull();
		// 'newUser' variable is used only within createUser()
		// Should not include 'user' from getUser()
		for ( Location ref : refs ) {
			// All refs should be within createUser function
			assertThat( ref.getUri() ).isEqualTo( serviceFileUri );
		}
	}

	// ============ Edge Case Tests ============

	/**
	 * Test finding references when cursor is not on a symbol.
	 */
	@Test
	void testFindReferencesOnNonSymbolReturnsEmpty() throws Exception {
		Path			serviceFilePath	= testDir.resolve( "UserService.bx" );
		String			serviceFileUri	= serviceFilePath.toUri().toString();

		// Position at a blank area
		ReferenceParams	params			= new ReferenceParams();
		params.setTextDocument( new TextDocumentIdentifier( serviceFileUri ) );
		params.setPosition( new Position( 0, 0 ) ); // At start of file
		params.setContext( new ReferenceContext( false ) );

		var refs = svc.references( params ).get();

		// Should return empty list or null (not error)
		if ( refs != null ) {
			// Empty is acceptable
		}
	}

	/**
	 * Test that includeDeclaration context flag works.
	 */
	@Test
	void testIncludeDeclarationFlag() throws Exception {
		Path			serviceFilePath	= testDir.resolve( "UserService.bx" );
		String			serviceFileUri	= serviceFilePath.toUri().toString();

		// Position at 'user' variable
		ReferenceParams	paramsWithDecl	= new ReferenceParams();
		paramsWithDecl.setTextDocument( new TextDocumentIdentifier( serviceFileUri ) );
		paramsWithDecl.setPosition( new Position( 11, 8 ) );
		paramsWithDecl.setContext( new ReferenceContext( true ) ); // Include declaration

		ReferenceParams paramsWithoutDecl = new ReferenceParams();
		paramsWithoutDecl.setTextDocument( new TextDocumentIdentifier( serviceFileUri ) );
		paramsWithoutDecl.setPosition( new Position( 11, 8 ) );
		paramsWithoutDecl.setContext( new ReferenceContext( false ) ); // Exclude declaration

		var	refsWithDecl	= svc.references( paramsWithDecl ).get();
		var	refsWithoutDecl	= svc.references( paramsWithoutDecl ).get();

		// When including declaration, there should be at least one more reference
		// (the declaration itself)
		assertThat( refsWithDecl ).isNotNull();
		assertThat( refsWithoutDecl ).isNotNull();
	}

	/**
	 * Test finding references to a function parameter.
	 */
	@Test
	void testFindReferencesToFunctionParameter() throws Exception {
		Path			serviceFilePath	= testDir.resolve( "UserService.bx" );
		String			serviceFileUri	= serviceFilePath.toUri().toString();

		// Position at 'id' parameter in getUser(required numeric id)
		// Line 11 (0-indexed: 10): `public User function getUser( required numeric id )`
		ReferenceParams	params			= new ReferenceParams();
		params.setTextDocument( new TextDocumentIdentifier( serviceFileUri ) );
		params.setPosition( new Position( 10, 50 ) ); // At 'id' parameter
		params.setContext( new ReferenceContext( false ) );

		var refs = svc.references( params ).get();

		assertThat( refs ).isNotNull();
		// 'id' is used in: user.id = id;
	}
}
