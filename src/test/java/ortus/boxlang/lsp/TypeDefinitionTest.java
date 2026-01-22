package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertThat;

import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.TypeDefinitionParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import ortus.boxlang.lsp.workspace.ProjectContextProvider;
import ortus.boxlang.lsp.workspace.index.ProjectIndex;

/**
 * Tests for go-to-type-definition functionality.
 * Task 2.10: Go to Type Definition
 *
 * When the cursor is on a variable, "Go to Type Definition" navigates
 * to the class definition of that variable's type.
 */
public class TypeDefinitionTest extends BaseTest {

	private BoxLangTextDocumentService	svc;
	private ProjectContextProvider		provider;
	private ProjectIndex				index;
	private Path						testDir;
	private Path						testFilePath;
	private String						testFileUri;
	private Path						userFilePath;
	private String						userFileUri;
	private Path						userRepoFilePath;
	private String						userRepoFileUri;

	@BeforeEach
	void setUp() throws Exception {
		svc			= new BoxLangTextDocumentService();
		provider	= ProjectContextProvider.getInstance();
		index		= new ProjectIndex();
		provider.setIndex( index );
		testDir		= Paths.get( "src/test/resources/files/typeDefinitionTest" );

		// Index all test files
		for ( Path file : Files.list( testDir ).filter( p -> p.toString().endsWith( ".bx" ) ).toList() ) {
			index.indexFile( file.toUri() );
		}

		// Open all test files in the LSP
		for ( Path file : Files.list( testDir ).filter( p -> p.toString().endsWith( ".bx" ) ).toList() ) {
			svc.didOpen( new DidOpenTextDocumentParams(
			    new TextDocumentItem( file.toUri().toString(), "boxlang", 1, Files.readString( file ) ) ) );
		}

		// Set up file path references
		testFilePath	= testDir.resolve( "UserService.bx" );
		testFileUri		= testFilePath.toUri().toString();
		userFilePath	= testDir.resolve( "User.bx" );
		userFileUri		= userFilePath.toUri().toString();
		userRepoFilePath	= testDir.resolve( "UserRepository.bx" );
		userRepoFileUri		= userRepoFilePath.toUri().toString();
	}

	/**
	 * Test go-to-type-definition on a variable assigned via new expression.
	 * From: `var user = new User();` (line 20)
	 * Cursor on 'user' should navigate to User.bx
	 */
	@Test
	void testTypeDefinitionOnNewExpressionVariable() throws Exception {
		// Position at 'user' on line 20 (0-indexed: line 19, around col 6)
		TypeDefinitionParams params = new TypeDefinitionParams();
		params.setTextDocument( new TextDocumentIdentifier( testFileUri ) );
		params.setPosition( new Position( 19, 6 ) );

		var result = svc.typeDefinition( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.getLeft() ).isNotNull();
		assertThat( result.getLeft().size() ).isGreaterThan( 0 );

		var def = result.getLeft().get( 0 );
		// Should navigate to User.bx (the type definition)
		assertThat( def.getUri() ).endsWith( "User.bx" );
	}

	/**
	 * Test go-to-type-definition on a variable usage.
	 * From: `var displayName = user.getDisplayName();` (line 22)
	 * Cursor on 'user' should navigate to User.bx
	 */
	@Test
	void testTypeDefinitionOnVariableUsage() throws Exception {
		// Position at 'user' on line 22 (0-indexed: line 21, col 21)
		TypeDefinitionParams params = new TypeDefinitionParams();
		params.setTextDocument( new TextDocumentIdentifier( testFileUri ) );
		params.setPosition( new Position( 21, 21 ) );

		var result = svc.typeDefinition( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.getLeft() ).isNotNull();
		assertThat( result.getLeft().size() ).isGreaterThan( 0 );

		var def = result.getLeft().get( 0 );
		assertThat( def.getUri() ).endsWith( "User.bx" );
	}

	/**
	 * Test go-to-type-definition on a typed function parameter.
	 * From: `required User userParam` (line 32)
	 * Cursor on 'userParam' should navigate to User.bx
	 */
	@Test
	void testTypeDefinitionOnTypedParameter() throws Exception {
		// Position at 'userParam' on line 32 (0-indexed: line 31, col 51 - start of 'userParam')
		TypeDefinitionParams params = new TypeDefinitionParams();
		params.setTextDocument( new TextDocumentIdentifier( testFileUri ) );
		params.setPosition( new Position( 31, 51 ) );

		var result = svc.typeDefinition( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.getLeft() ).isNotNull();
		assertThat( result.getLeft().size() ).isGreaterThan( 0 );

		var def = result.getLeft().get( 0 );
		assertThat( def.getUri() ).endsWith( "User.bx" );
	}

	/**
	 * Test go-to-type-definition on a parameter usage within function body.
	 * From: `return userParam.getDisplayName();` (line 34)
	 * Cursor on 'userParam' should navigate to User.bx
	 */
	@Test
	void testTypeDefinitionOnTypedParameterUsage() throws Exception {
		// Position at 'userParam' on line 34 (0-indexed: line 33, col 10)
		TypeDefinitionParams params = new TypeDefinitionParams();
		params.setTextDocument( new TextDocumentIdentifier( testFileUri ) );
		params.setPosition( new Position( 33, 10 ) );

		var result = svc.typeDefinition( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.getLeft() ).isNotNull();
		assertThat( result.getLeft().size() ).isGreaterThan( 0 );

		var def = result.getLeft().get( 0 );
		assertThat( def.getUri() ).endsWith( "User.bx" );
	}

	/**
	 * Test go-to-type-definition on a primitive variable returns empty.
	 * From: `var count = 10;` (line 42)
	 * Cursor on 'count' should return empty (primitive type)
	 */
	@Test
	void testTypeDefinitionOnPrimitiveVariableReturnsEmpty() throws Exception {
		// Position at 'count' on line 42 (0-indexed: line 41, col 6)
		TypeDefinitionParams params = new TypeDefinitionParams();
		params.setTextDocument( new TextDocumentIdentifier( testFileUri ) );
		params.setPosition( new Position( 41, 6 ) );

		var result = svc.typeDefinition( params ).get();

		// Should return empty or null for primitive types
		if ( result != null && result.getLeft() != null ) {
			assertThat( result.getLeft().size() ).isEqualTo( 0 );
		}
	}

	/**
	 * Test go-to-type-definition on a string variable returns empty.
	 * From: `var name = "hello";` (line 46)
	 * Cursor on 'name' should return empty (primitive type)
	 */
	@Test
	void testTypeDefinitionOnStringVariableReturnsEmpty() throws Exception {
		// Position at 'name' on line 46 (0-indexed: line 45, col 6)
		TypeDefinitionParams params = new TypeDefinitionParams();
		params.setTextDocument( new TextDocumentIdentifier( testFileUri ) );
		params.setPosition( new Position( 45, 6 ) );

		var result = svc.typeDefinition( params ).get();

		// Should return empty for string literals
		if ( result != null && result.getLeft() != null ) {
			assertThat( result.getLeft().size() ).isEqualTo( 0 );
		}
	}

	/**
	 * Test go-to-type-definition on another variable from UserRepository.
	 * From: `var repo = new UserRepository();` (line 25 in UserRepository.bx)
	 * Cursor on 'repo' should navigate to UserRepository.bx
	 */
	@Test
	void testTypeDefinitionNavigatesToSameFile() throws Exception {
		// Position at 'repo' on line 25 (0-indexed: line 24, col 6) in UserRepository.bx
		TypeDefinitionParams params = new TypeDefinitionParams();
		params.setTextDocument( new TextDocumentIdentifier( userRepoFileUri ) );
		params.setPosition( new Position( 24, 6 ) );

		var result = svc.typeDefinition( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.getLeft() ).isNotNull();
		assertThat( result.getLeft().size() ).isGreaterThan( 0 );

		var def = result.getLeft().get( 0 );
		assertThat( def.getUri() ).endsWith( "UserRepository.bx" );
	}

	/**
	 * Test go-to-type-definition on non-symbol position returns empty.
	 */
	@Test
	void testTypeDefinitionOnNonSymbolReturnsEmpty() throws Exception {
		// Position on whitespace/comment area
		TypeDefinitionParams params = new TypeDefinitionParams();
		params.setTextDocument( new TextDocumentIdentifier( testFileUri ) );
		params.setPosition( new Position( 0, 0 ) );

		var result = svc.typeDefinition( params ).get();

		// Should return empty for non-symbol positions
		if ( result != null && result.getLeft() != null ) {
			assertThat( result.getLeft().size() ).isEqualTo( 0 );
		}
	}

}
