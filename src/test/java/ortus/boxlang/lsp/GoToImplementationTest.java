package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertThat;

import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.ImplementationParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import ortus.boxlang.lsp.workspace.ProjectContextProvider;
import ortus.boxlang.lsp.workspace.index.ProjectIndex;

/**
 * Tests for Go to Implementation functionality.
 * Task 2.11: Go to Implementation
 *
 * When the cursor is on an interface method or interface name,
 * "Go to Implementation" navigates to the concrete implementations.
 */
public class GoToImplementationTest extends BaseTest {

	private BoxLangTextDocumentService	svc;
	private ProjectContextProvider		provider;
	private ProjectIndex				index;
	private Path						testDir;
	private String						interfaceFileUri;
	private String						userRepoFileUri;
	private String						productRepoFileUri;
	private String						abstractEntityFileUri;
	private String						concreteEntityFileUri;
	private String						anotherConcreteEntityFileUri;

	@BeforeEach
	void setUp() throws Exception {
		svc			= new BoxLangTextDocumentService();
		provider	= ProjectContextProvider.getInstance();
		index		= new ProjectIndex();
		provider.setIndex( index );
		testDir = Paths.get( "src/test/resources/files/goToImplementationTest" );

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
		interfaceFileUri				= testDir.resolve( "IRepository.bx" ).toUri().toString();
		userRepoFileUri					= testDir.resolve( "UserRepository.bx" ).toUri().toString();
		productRepoFileUri				= testDir.resolve( "ProductRepository.bx" ).toUri().toString();
		abstractEntityFileUri			= testDir.resolve( "AbstractEntity.bx" ).toUri().toString();
		concreteEntityFileUri			= testDir.resolve( "ConcreteEntity.bx" ).toUri().toString();
		anotherConcreteEntityFileUri	= testDir.resolve( "AnotherConcreteEntity.bx" ).toUri().toString();
	}

	/**
	 * Test Go to Implementation on interface method findById.
	 * From interface method declaration, should navigate to all implementing methods.
	 * Line 9: public any function findById( required numeric id );
	 */
	@Test
	void testGoToImplementationOnInterfaceMethod() throws Exception {
		// Position at 'findById' on line 9 (0-indexed: line 8, col 22)
		ImplementationParams params = new ImplementationParams();
		params.setTextDocument( new TextDocumentIdentifier( interfaceFileUri ) );
		params.setPosition( new Position( 8, 22 ) );

		var result = svc.implementation( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.getLeft() ).isNotNull();
		assertThat( result.getLeft().size() ).isEqualTo( 2 );

		// Should find implementations in UserRepository and ProductRepository
		Set<String> uris = result.getLeft().stream()
		    .map( Location::getUri )
		    .collect( Collectors.toSet() );

		assertThat( uris ).contains( userRepoFileUri );
		assertThat( uris ).contains( productRepoFileUri );
	}

	/**
	 * Test Go to Implementation on interface name.
	 * From interface declaration, should navigate to all implementing classes.
	 * Line 4: interface IRepository {
	 */
	@Test
	void testGoToImplementationOnInterfaceName() throws Exception {
		// Position at 'IRepository' on line 4 (0-indexed: line 3, col 10)
		ImplementationParams params = new ImplementationParams();
		params.setTextDocument( new TextDocumentIdentifier( interfaceFileUri ) );
		params.setPosition( new Position( 3, 10 ) );

		var result = svc.implementation( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.getLeft() ).isNotNull();
		assertThat( result.getLeft().size() ).isEqualTo( 2 );

		// Should find implementing classes
		Set<String> uris = result.getLeft().stream()
		    .map( Location::getUri )
		    .collect( Collectors.toSet() );

		assertThat( uris ).contains( userRepoFileUri );
		assertThat( uris ).contains( productRepoFileUri );
	}

	/**
	 * Test Go to Implementation on abstract method.
	 * From abstract method declaration, should navigate to overriding methods.
	 * Line 12: public abstract numeric function getId();
	 */
	@Test
	void testGoToImplementationOnAbstractMethod() throws Exception {
		// Position at 'getId' on line 12 (0-indexed: line 11, col 38)
		ImplementationParams params = new ImplementationParams();
		params.setTextDocument( new TextDocumentIdentifier( abstractEntityFileUri ) );
		params.setPosition( new Position( 11, 38 ) );

		var result = svc.implementation( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.getLeft() ).isNotNull();
		assertThat( result.getLeft().size() ).isEqualTo( 2 );

		// Should find implementations in ConcreteEntity and AnotherConcreteEntity
		Set<String> uris = result.getLeft().stream()
		    .map( Location::getUri )
		    .collect( Collectors.toSet() );

		assertThat( uris ).contains( concreteEntityFileUri );
		assertThat( uris ).contains( anotherConcreteEntityFileUri );
	}

	/**
	 * Test Go to Implementation on abstract class name.
	 * From abstract class declaration, should navigate to all extending classes.
	 * Line 4: abstract class AbstractEntity {
	 */
	@Test
	void testGoToImplementationOnAbstractClassName() throws Exception {
		// Position at 'AbstractEntity' on line 4 (0-indexed: line 3, col 16)
		ImplementationParams params = new ImplementationParams();
		params.setTextDocument( new TextDocumentIdentifier( abstractEntityFileUri ) );
		params.setPosition( new Position( 3, 16 ) );

		var result = svc.implementation( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.getLeft() ).isNotNull();
		assertThat( result.getLeft().size() ).isEqualTo( 2 );

		// Should find extending classes
		Set<String> uris = result.getLeft().stream()
		    .map( Location::getUri )
		    .collect( Collectors.toSet() );

		assertThat( uris ).contains( concreteEntityFileUri );
		assertThat( uris ).contains( anotherConcreteEntityFileUri );
	}

	/**
	 * Test Go to Implementation returns multiple locations (client shows picker).
	 * Verifies we return all implementations for the method.
	 * Line 14: public void function save( required any entity );
	 */
	@Test
	void testGoToImplementationReturnsMultipleLocations() throws Exception {
		// Position at 'save' on line 14 (0-indexed: line 13, col 22)
		ImplementationParams params = new ImplementationParams();
		params.setTextDocument( new TextDocumentIdentifier( interfaceFileUri ) );
		params.setPosition( new Position( 13, 22 ) );

		var result = svc.implementation( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.getLeft() ).isNotNull();
		// Should return both implementations
		assertThat( result.getLeft().size() ).isGreaterThan( 1 );

		// Verify both files are represented
		Set<String> uris = result.getLeft().stream()
		    .map( Location::getUri )
		    .collect( Collectors.toSet() );

		assertThat( uris.size() ).isEqualTo( 2 );
	}

	/**
	 * Test Go to Implementation on regular (non-abstract, non-interface) method returns empty.
	 * From: ConcreteEntity.getId() implementation (line 9)
	 */
	@Test
	void testGoToImplementationOnConcreteMethodReturnsEmpty() throws Exception {
		// Position at 'getId' in ConcreteEntity (line 9, col 26)
		ImplementationParams params = new ImplementationParams();
		params.setTextDocument( new TextDocumentIdentifier( concreteEntityFileUri ) );
		params.setPosition( new Position( 8, 26 ) );

		var result = svc.implementation( params ).get();

		// Should return empty for concrete methods (no further implementations)
		if ( result != null && result.getLeft() != null ) {
			assertThat( result.getLeft().size() ).isEqualTo( 0 );
		}
	}

	/**
	 * Test Go to Implementation on non-symbol position returns empty.
	 */
	@Test
	void testGoToImplementationOnNonSymbolReturnsEmpty() throws Exception {
		// Position on whitespace/comment area
		ImplementationParams params = new ImplementationParams();
		params.setTextDocument( new TextDocumentIdentifier( interfaceFileUri ) );
		params.setPosition( new Position( 0, 0 ) );

		var result = svc.implementation( params ).get();

		// Should return empty for non-symbol positions
		if ( result != null && result.getLeft() != null ) {
			assertThat( result.getLeft().size() ).isEqualTo( 0 );
		}
	}

	/**
	 * Test Go to Implementation finds implementations even with case-insensitive matching.
	 * BoxLang is case-insensitive, so method names should match regardless of case.
	 */
	@Test
	void testGoToImplementationIsCaseInsensitive() throws Exception {
		// Position at 'delete' on line 19 (0-indexed: line 18, col 22)
		ImplementationParams params = new ImplementationParams();
		params.setTextDocument( new TextDocumentIdentifier( interfaceFileUri ) );
		params.setPosition( new Position( 18, 22 ) );

		var result = svc.implementation( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.getLeft() ).isNotNull();
		assertThat( result.getLeft().size() ).isEqualTo( 2 );
	}

}
