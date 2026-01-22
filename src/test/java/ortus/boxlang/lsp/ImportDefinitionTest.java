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
 * Tests for go-to-definition functionality on import statements.
 * Task 2.5: Go to Definition - Imports
 */
public class ImportDefinitionTest extends BaseTest {

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
		testDir		= Paths.get( "src/test/resources/files/importDefinitionTest" );

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
	 * Test go-to-definition on a simple import statement.
	 * From: `import UserEntity;`
	 * To: UserEntity.bx class definition
	 */
	@Test
	void testGoToDefinitionOnSimpleImport() throws Exception {
		Path	testFilePath		= testDir.resolve( "UserServiceImpl.bx" );
		String	testFileUri			= testFilePath.toUri().toString();
		Path	userEntityPath		= testDir.resolve( "UserEntity.bx" );
		String	userEntityUri		= userEntityPath.toUri().toString();

		// Position at 'UserEntity' in `import UserEntity;` on line 5 (0-indexed: 4)
		DefinitionParams params = new DefinitionParams();
		params.setTextDocument( new TextDocumentIdentifier( testFileUri ) );
		params.setPosition( new Position( 4, 10 ) ); // Position within 'UserEntity'

		var result = svc.definition( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.getLeft() ).isNotNull();
		assertThat( result.getLeft().size() ).isGreaterThan( 0 );

		var def = result.getLeft().get( 0 );
		// Should navigate to UserEntity.bx
		assertThat( def.getUri() ).isEqualTo( userEntityUri );
		// Class definition starts at line 7 (0-indexed: 6)
		assertThat( def.getRange().getStart().getLine() ).isEqualTo( 6 );
	}

	/**
	 * Test go-to-definition on an interface import statement.
	 * From: `import IUserService;`
	 * To: IUserService.bx interface definition
	 */
	@Test
	void testGoToDefinitionOnInterfaceImport() throws Exception {
		Path	testFilePath		= testDir.resolve( "UserServiceImpl.bx" );
		String	testFileUri			= testFilePath.toUri().toString();
		Path	interfacePath		= testDir.resolve( "IUserService.bx" );
		String	interfaceUri		= interfacePath.toUri().toString();

		// Position at 'IUserService' in `import IUserService;` on line 6 (0-indexed: 5)
		DefinitionParams params = new DefinitionParams();
		params.setTextDocument( new TextDocumentIdentifier( testFileUri ) );
		params.setPosition( new Position( 5, 10 ) ); // Position within 'IUserService'

		var result = svc.definition( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.getLeft() ).isNotNull();
		assertThat( result.getLeft().size() ).isGreaterThan( 0 );

		var def = result.getLeft().get( 0 );
		// Should navigate to IUserService.bx
		assertThat( def.getUri() ).isEqualTo( interfaceUri );
		// Interface definition starts at line 5 (0-indexed: 4)
		assertThat( def.getRange().getStart().getLine() ).isEqualTo( 4 );
	}

	/**
	 * Test go-to-definition on an aliased import (the original class name).
	 * From: `import UserEntity as User;` (on 'UserEntity' part)
	 * To: UserEntity.bx class definition
	 */
	@Test
	void testGoToDefinitionOnAliasedImportClassName() throws Exception {
		Path	testFilePath		= testDir.resolve( "AliasedImports.bx" );
		String	testFileUri			= testFilePath.toUri().toString();
		Path	userEntityPath		= testDir.resolve( "UserEntity.bx" );
		String	userEntityUri		= userEntityPath.toUri().toString();

		// Position at 'UserEntity' in `import UserEntity as User;` on line 5 (0-indexed: 4)
		DefinitionParams params = new DefinitionParams();
		params.setTextDocument( new TextDocumentIdentifier( testFileUri ) );
		params.setPosition( new Position( 4, 10 ) ); // Position within 'UserEntity'

		var result = svc.definition( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.getLeft() ).isNotNull();
		assertThat( result.getLeft().size() ).isGreaterThan( 0 );

		var def = result.getLeft().get( 0 );
		// Should navigate to UserEntity.bx
		assertThat( def.getUri() ).isEqualTo( userEntityUri );
		// Class definition starts at line 7 (0-indexed: 6)
		assertThat( def.getRange().getStart().getLine() ).isEqualTo( 6 );
	}

	/**
	 * Test go-to-definition on the alias part of an import.
	 * From: `import UserEntity as User;` (on 'User' alias part)
	 * To: UserEntity.bx class definition (same destination)
	 */
	@Test
	void testGoToDefinitionOnAliasedImportAlias() throws Exception {
		Path	testFilePath		= testDir.resolve( "AliasedImports.bx" );
		String	testFileUri			= testFilePath.toUri().toString();
		Path	userEntityPath		= testDir.resolve( "UserEntity.bx" );
		String	userEntityUri		= userEntityPath.toUri().toString();

		// Position at 'User' in `import UserEntity as User;` on line 5 (0-indexed: 4)
		// The alias 'User' starts around column 23
		DefinitionParams params = new DefinitionParams();
		params.setTextDocument( new TextDocumentIdentifier( testFileUri ) );
		params.setPosition( new Position( 4, 25 ) ); // Position within 'User' alias

		var result = svc.definition( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.getLeft() ).isNotNull();
		assertThat( result.getLeft().size() ).isGreaterThan( 0 );

		var def = result.getLeft().get( 0 );
		// Should navigate to UserEntity.bx
		assertThat( def.getUri() ).isEqualTo( userEntityUri );
		// Class definition starts at line 7 (0-indexed: 6)
		assertThat( def.getRange().getStart().getLine() ).isEqualTo( 6 );
	}

	/**
	 * Test go-to-definition on Java import returns empty.
	 * Java imports (java:java.util.ArrayList) have no source to navigate to.
	 */
	@Test
	void testGoToDefinitionOnJavaImportReturnsEmpty() throws Exception {
		Path	testFilePath	= testDir.resolve( "JavaImports.bx" );
		String	testFileUri		= testFilePath.toUri().toString();

		// Position at 'ArrayList' in `import java:java.util.ArrayList;` on line 5 (0-indexed: 4)
		DefinitionParams params = new DefinitionParams();
		params.setTextDocument( new TextDocumentIdentifier( testFileUri ) );
		params.setPosition( new Position( 4, 25 ) ); // Position within 'ArrayList'

		var result = svc.definition( params ).get();

		// Java imports should return empty results (no source to navigate to)
		if ( result != null && result.getLeft() != null ) {
			assertThat( result.getLeft().size() ).isEqualTo( 0 );
		}
	}

	/**
	 * Test go-to-definition on unknown import returns empty.
	 */
	@Test
	void testGoToDefinitionOnUnknownImportReturnsEmpty() throws Exception {
		// Create a temp file with an unknown import
		Path	tempFile	= testDir.resolve( "TempUnknownImport.bx" );
		String	content		= """
		    import NonExistentClass;

		    class {
		        public function test() {
		            var x = new NonExistentClass();
		        }
		    }
		                        """;

		Files.writeString( tempFile, content );

		try {
			svc.didOpen( new DidOpenTextDocumentParams(
			    new TextDocumentItem( tempFile.toUri().toString(), "boxlang", 1, content ) ) );

			// Position at 'NonExistentClass' in import statement
			DefinitionParams params = new DefinitionParams();
			params.setTextDocument( new TextDocumentIdentifier( tempFile.toUri().toString() ) );
			params.setPosition( new Position( 0, 12 ) );

			var result = svc.definition( params ).get();

			// Unknown imports should return empty results
			if ( result != null && result.getLeft() != null ) {
				assertThat( result.getLeft().size() ).isEqualTo( 0 );
			}
		} finally {
			Files.deleteIfExists( tempFile );
		}
	}
}
