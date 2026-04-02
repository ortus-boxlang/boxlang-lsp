package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertThat;

import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.ImplementationParams;
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
import ortus.boxlang.lsp.workspace.index.IndexedClass;

/**
 * Debug test to understand how the index works
 */
public class DebugImplementationTest extends BaseTest {

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
	}

	@Test
	void debugFindInterface() throws Exception {
		// Find the interface
		var interfaceOpt = index.findClassByName( "IRepository" );
		System.out.println( "Found IRepository: " + interfaceOpt.isPresent() );
		if ( interfaceOpt.isPresent() ) {
			IndexedClass iface = interfaceOpt.get();
			System.out.println( "  Name: " + iface.name() );
			System.out.println( "  FQN: " + iface.fullyQualifiedName() );
			System.out.println( "  isInterface: " + iface.isInterface() );
		}

		// Find UserRepository
		var userRepoOpt = index.findClassByName( "UserRepository" );
		System.out.println( "Found UserRepository: " + userRepoOpt.isPresent() );
		if ( userRepoOpt.isPresent() ) {
			IndexedClass userRepo = userRepoOpt.get();
			System.out.println( "  Name: " + userRepo.name() );
			System.out.println( "  FQN: " + userRepo.fullyQualifiedName() );
			System.out.println( "  implements: " + userRepo.implementsInterfaces() );
		}

		// Try to find implementing classes
		String interfaceFQN = interfaceOpt.isPresent() ? interfaceOpt.get().fullyQualifiedName() : "N/A";
		System.out.println( "Searching for implementors of FQN: " + interfaceFQN );
		var implementors = index.findClassesImplementing( interfaceFQN );
		System.out.println( "Found " + implementors.size() + " implementors:" );
		for ( IndexedClass impl : implementors ) {
			System.out.println( "  - " + impl.name() + " (" + impl.fullyQualifiedName() + ")" );
		}

		// Also try with simple name
		System.out.println( "Searching for implementors of simple name: IRepository" );
		var implementors2 = index.findClassesImplementing( "IRepository" );
		System.out.println( "Found " + implementors2.size() + " implementors:" );
		for ( IndexedClass impl : implementors2 ) {
			System.out.println( "  - " + impl.name() + " (" + impl.fullyQualifiedName() + ")" );
		}

		// Check the inheritance graph directly
		System.out.println( "Checking inheritance graph directly..." );
		var	graph				= index.getInheritanceGraph();
		var	graphImplementors	= graph.getImplementors( "IRepository" );
		System.out.println( "Graph implementors for 'IRepository': " + graphImplementors );
		var graphImplementorsFQN = graph.getImplementors( interfaceFQN );
		System.out.println( "Graph implementors for FQN '" + interfaceFQN + "': " + graphImplementorsFQN );

		assertThat( implementors.size() ).isGreaterThan( 0 );
	}

	@Test
	void debugGoToImplementationOnInterfaceName() throws Exception {
		String					interfaceFileUri	= testDir.resolve( "IRepository.bx" ).toUri().toString();

		// Line 4: interface IRepository {
		// Trying position at 'IRepository' (0-indexed: line 3, col 10)
		ImplementationParams	params				= new ImplementationParams();
		params.setTextDocument( new TextDocumentIdentifier( interfaceFileUri ) );
		params.setPosition( new Position( 3, 10 ) );

		System.out.println( "Testing Go to Implementation at line 3, col 10" );

		var result = svc.implementation( params ).get();

		System.out.println( "Result: " + result );
		if ( result != null && result.getLeft() != null ) {
			System.out.println( "  Found " + result.getLeft().size() + " implementations" );
			for ( var loc : result.getLeft() ) {
				System.out.println( "    - " + loc.getUri() );
			}
		}
	}

}
