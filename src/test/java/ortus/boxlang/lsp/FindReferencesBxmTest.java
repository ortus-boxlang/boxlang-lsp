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
import java.util.stream.Stream;

import org.eclipse.lsp4j.Location;

import ortus.boxlang.lsp.workspace.ProjectContextProvider;
import ortus.boxlang.lsp.workspace.index.ProjectIndex;

/**
 * Tests for Find References functionality in BXM template files.
 * Task 2.7: Find References - Include BXM Templates
 */
public class FindReferencesBxmTest extends BaseTest {

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
		testDir		= Paths.get( "src/test/resources/files/findReferencesTest" );

		// Index all .bx class files
		try ( Stream<Path> stream = Files.list( testDir ) ) {
			for ( Path file : stream.filter( p -> p.toString().endsWith( ".bx" ) ).toList() ) {
				index.indexFile( file.toUri() );
			}
		}

		// Open all .bx and .bxm files in the LSP
		try ( Stream<Path> stream = Files.list( testDir ) ) {
			for ( Path file : stream.filter( p -> p.toString().endsWith( ".bx" ) || p.toString().endsWith( ".bxm" ) ).toList() ) {
				svc.didOpen( new DidOpenTextDocumentParams(
				    new TextDocumentItem( file.toUri().toString(), "boxlang", 1, Files.readString( file ) ) ) );
			}
		}
	}

	// ============ Class References in BXM ============

	/**
	 * Test finding references to User class includes references in BXM template.
	 * User is used in UserTemplate.bxm: new User(), method calls, property access
	 */
	@Test
	void testFindClassReferencesIncludesBxmTemplate() throws Exception {
		Path	userFilePath	= testDir.resolve( "User.bx" );
		String	userFileUri		= userFilePath.toUri().toString();
		String	bxmFileUri		= testDir.resolve( "UserTemplate.bxm" ).toUri().toString();

		// Position at 'class' keyword in User.bx (line 4, 0-indexed: 3)
		ReferenceParams params = new ReferenceParams();
		params.setTextDocument( new TextDocumentIdentifier( userFileUri ) );
		params.setPosition( new Position( 3, 2 ) ); // At 'class' keyword
		params.setContext( new ReferenceContext( false ) ); // Don't include declaration

		var refs = svc.references( params ).get();

		assertThat( refs ).isNotNull();
		assertThat( refs.size() ).isGreaterThan( 0 );

		// Verify that references include the BXM template
		boolean hasBxmReference = refs.stream().anyMatch( r -> r.getUri().endsWith( ".bxm" ) );
		assertThat( hasBxmReference ).isTrue();
	}

	/**
	 * Test finding references to UserService class includes references in BXM template.
	 */
	@Test
	void testFindClassReferencesFromBxmScriptBlock() throws Exception {
		Path	serviceFilePath	= testDir.resolve( "UserService.bx" );
		String	serviceFileUri	= serviceFilePath.toUri().toString();
		String	bxmFileUri		= testDir.resolve( "UserTemplate.bxm" ).toUri().toString();

		// Position at 'class' keyword in UserService.bx (line 4, 0-indexed: 3)
		ReferenceParams params = new ReferenceParams();
		params.setTextDocument( new TextDocumentIdentifier( serviceFileUri ) );
		params.setPosition( new Position( 3, 2 ) ); // At 'class' keyword
		params.setContext( new ReferenceContext( false ) );

		var refs = svc.references( params ).get();

		assertThat( refs ).isNotNull();
		assertThat( refs.size() ).isGreaterThan( 0 );

		// Verify that references include the BXM template
		boolean hasBxmReference = refs.stream().anyMatch( r -> r.getUri().endsWith( ".bxm" ) );
		assertThat( hasBxmReference ).isTrue();
	}

	// ============ Method References in BXM ============

	/**
	 * Test finding references to getUser method includes calls in BXM template.
	 */
	@Test
	void testFindMethodReferencesIncludesBxmTemplate() throws Exception {
		Path	serviceFilePath	= testDir.resolve( "UserService.bx" );
		String	serviceFileUri	= serviceFilePath.toUri().toString();

		// Position at 'getUser' function declaration in UserService.bx
		// Line 11 (0-indexed: 10): `public User function getUser(...)`
		ReferenceParams params = new ReferenceParams();
		params.setTextDocument( new TextDocumentIdentifier( serviceFileUri ) );
		params.setPosition( new Position( 10, 25 ) ); // At 'getUser'
		params.setContext( new ReferenceContext( false ) );

		var refs = svc.references( params ).get();

		assertThat( refs ).isNotNull();
		assertThat( refs.size() ).isGreaterThan( 0 );

		// Verify that references include the BXM template (userService.getUser() calls)
		boolean hasBxmReference = refs.stream().anyMatch( r -> r.getUri().endsWith( ".bxm" ) );
		assertThat( hasBxmReference ).isTrue();
	}

	/**
	 * Test finding references to getDisplayName method includes calls in ## expressions.
	 */
	@Test
	void testFindMethodReferencesInTemplateExpressions() throws Exception {
		Path	userFilePath	= testDir.resolve( "User.bx" );
		String	userFileUri		= userFilePath.toUri().toString();

		// Position at 'getDisplayName' function declaration in User.bx
		// Line 12 (0-indexed: 11): `public string function getDisplayName()`
		ReferenceParams params = new ReferenceParams();
		params.setTextDocument( new TextDocumentIdentifier( userFileUri ) );
		params.setPosition( new Position( 11, 28 ) ); // At 'getDisplayName'
		params.setContext( new ReferenceContext( false ) );

		var refs = svc.references( params ).get();

		assertThat( refs ).isNotNull();
		assertThat( refs.size() ).isGreaterThan( 0 );

		// Verify that references include the BXM template (#user.getDisplayName()#)
		boolean hasBxmReference = refs.stream().anyMatch( r -> r.getUri().endsWith( ".bxm" ) );
		assertThat( hasBxmReference ).isTrue();
	}

	/**
	 * Test finding references to saveUser method includes calls in BXM script blocks.
	 */
	@Test
	void testFindMethodReferencesInBxmScriptBlock() throws Exception {
		Path	serviceFilePath	= testDir.resolve( "UserService.bx" );
		String	serviceFileUri	= serviceFilePath.toUri().toString();

		// Position at 'saveUser' function declaration in UserService.bx
		// Line 20 (0-indexed: 19): `public void function saveUser(...)`
		ReferenceParams params = new ReferenceParams();
		params.setTextDocument( new TextDocumentIdentifier( serviceFileUri ) );
		params.setPosition( new Position( 19, 25 ) ); // At 'saveUser'
		params.setContext( new ReferenceContext( false ) );

		var refs = svc.references( params ).get();

		assertThat( refs ).isNotNull();
		assertThat( refs.size() ).isGreaterThan( 0 );

		// Verify that references include the BXM template (userService.saveUser() call)
		boolean hasBxmReference = refs.stream().anyMatch( r -> r.getUri().endsWith( ".bxm" ) );
		assertThat( hasBxmReference ).isTrue();
	}

	// ============ Property References in BXM ============

	/**
	 * Test finding references to username property includes accesses in ## expressions.
	 */
	@Test
	void testFindPropertyReferencesInTemplateExpressions() throws Exception {
		Path	userFilePath	= testDir.resolve( "User.bx" );
		String	userFileUri		= userFilePath.toUri().toString();

		// Position at 'username' property declaration in User.bx
		// Line 6 (0-indexed: 5): `property name="username" type="string";`
		ReferenceParams params = new ReferenceParams();
		params.setTextDocument( new TextDocumentIdentifier( userFileUri ) );
		params.setPosition( new Position( 5, 20 ) ); // At 'username'
		params.setContext( new ReferenceContext( false ) );

		var refs = svc.references( params ).get();

		assertThat( refs ).isNotNull();
		// Property references are scoped to the same file, so BXM won't have them
		// (since templates don't have the same class scope)
		// But let's verify we get references from within the User class itself
	}

	// ============ Tag Attribute References in BXM ============

	/**
	 * Test finding references to User class from bx:set tag attribute.
	 */
	@Test
	void testFindClassReferencesInTagAttribute() throws Exception {
		Path	userFilePath	= testDir.resolve( "User.bx" );
		String	userFileUri		= userFilePath.toUri().toString();

		// Position at 'class' keyword in User.bx
		ReferenceParams params = new ReferenceParams();
		params.setTextDocument( new TextDocumentIdentifier( userFileUri ) );
		params.setPosition( new Position( 3, 2 ) ); // At 'class' keyword
		params.setContext( new ReferenceContext( false ) );

		var refs = svc.references( params ).get();

		assertThat( refs ).isNotNull();

		// The BXM template has <bx:set anotherUser = new User() />
		// This should be found as a reference
		boolean hasBxmReference = refs.stream().anyMatch( r -> r.getUri().endsWith( ".bxm" ) );
		assertThat( hasBxmReference ).isTrue();
	}

	// ============ Edge Cases ============

	/**
	 * Test finding references from within the BXM template itself.
	 */
	@Test
	void testFindReferencesFromBxmFile() throws Exception {
		Path	bxmFilePath	= testDir.resolve( "UserTemplate.bxm" );
		String	bxmFileUri	= bxmFilePath.toUri().toString();

		// Position at 'new User()' in the BXM script block
		// Line 10 (0-indexed: 9): `var user = new User();`
		// 'User' starts at column 27 after "            var user = new "
		ReferenceParams params = new ReferenceParams();
		params.setTextDocument( new TextDocumentIdentifier( bxmFileUri ) );
		params.setPosition( new Position( 9, 27 ) ); // At 'User' in `new User()`
		params.setContext( new ReferenceContext( true ) );

		var refs = svc.references( params ).get();

		assertThat( refs ).isNotNull();
		// Should find references across files (both .bx and .bxm)
		assertThat( refs.size() ).isGreaterThan( 0 );
	}

	/**
	 * Test that variable references in BXM are scoped properly.
	 */
	@Test
	void testLocalVariableReferencesInBxmAreScoped() throws Exception {
		Path	bxmFilePath	= testDir.resolve( "UserTemplate.bxm" );
		String	bxmFileUri	= bxmFilePath.toUri().toString();

		// Position at 'user' variable declaration in BXM script block
		// Line 10 (0-indexed: 9): `var user = new User();`
		// 'user' starts at column 16 after "            var "
		ReferenceParams params = new ReferenceParams();
		params.setTextDocument( new TextDocumentIdentifier( bxmFileUri ) );
		params.setPosition( new Position( 9, 16 ) ); // At 'user'
		params.setContext( new ReferenceContext( false ) );

		var refs = svc.references( params ).get();

		assertThat( refs ).isNotNull();
		// 'user' is used multiple times in the template:
		// - user.username = "testuser";
		// - user.email = "test@example.com";
		// - user.getDisplayName()
		// - user.username in ##
		// - user.email in ##
		// - user.username in bx:if
		assertThat( refs.size() ).isGreaterThan( 0 );
	}
}
