package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ortus.boxlang.lsp.workspace.ProjectContextProvider;
import ortus.boxlang.lsp.workspace.index.ProjectIndex;

/**
 * Tests for Workspace Symbols functionality.
 * Task 2.8: Workspace Symbols
 */
public class WorkspaceSymbolsTest extends BaseTest {

	private BoxLangWorkspaceService	svc;
	private ProjectContextProvider	provider;
	private ProjectIndex			index;
	private Path					testDir;

	@BeforeEach
	void setUp() throws Exception {
		svc			= new BoxLangWorkspaceService();
		provider	= ProjectContextProvider.getInstance();
		index		= new ProjectIndex();
		provider.setIndex( index );
		testDir		= Paths.get( "src/test/resources/files/workspaceSymbolsTest" );

		// Index all test files
		for ( Path file : Files.list( testDir ).filter( p -> p.toString().endsWith( ".bx" ) ).toList() ) {
			index.indexFile( file.toUri() );
		}
	}

	// ============ Basic Search Tests ============

	/**
	 * Test searching for classes by exact name.
	 */
	@Test
	void testSearchClassByExactName() throws Exception {
		WorkspaceSymbolParams params = new WorkspaceSymbolParams( "UserEntity" );

		var result = svc.symbol( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.isLeft() ).isTrue();
		List<? extends SymbolInformation> symbols = result.getLeft();

		assertThat( symbols ).isNotEmpty();
		assertThat( symbols.stream().anyMatch( s -> s.getName().equals( "UserEntity" ) && s.getKind() == SymbolKind.Class ) ).isTrue();
	}

	/**
	 * Test searching for interfaces.
	 */
	@Test
	void testSearchInterface() throws Exception {
		WorkspaceSymbolParams params = new WorkspaceSymbolParams( "IUserRepository" );

		var result = svc.symbol( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.isLeft() ).isTrue();
		List<? extends SymbolInformation> symbols = result.getLeft();

		assertThat( symbols ).isNotEmpty();
		assertThat( symbols.stream().anyMatch( s -> s.getName().equals( "IUserRepository" ) && s.getKind() == SymbolKind.Interface ) ).isTrue();
	}

	/**
	 * Test searching for methods.
	 */
	@Test
	void testSearchMethod() throws Exception {
		WorkspaceSymbolParams params = new WorkspaceSymbolParams( "getDisplayName" );

		var result = svc.symbol( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.isLeft() ).isTrue();
		List<? extends SymbolInformation> symbols = result.getLeft();

		assertThat( symbols ).isNotEmpty();
		assertThat( symbols.stream().anyMatch( s -> s.getName().equals( "getDisplayName" ) && s.getKind() == SymbolKind.Method ) ).isTrue();
	}

	/**
	 * Test searching for properties.
	 */
	@Test
	void testSearchProperty() throws Exception {
		WorkspaceSymbolParams params = new WorkspaceSymbolParams( "username" );

		var result = svc.symbol( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.isLeft() ).isTrue();
		List<? extends SymbolInformation> symbols = result.getLeft();

		assertThat( symbols ).isNotEmpty();
		assertThat( symbols.stream().anyMatch( s -> s.getName().equals( "username" ) && s.getKind() == SymbolKind.Property ) ).isTrue();
	}

	// ============ Fuzzy Matching Tests ============

	/**
	 * Test fuzzy matching - partial prefix match.
	 */
	@Test
	void testFuzzyMatchPrefix() throws Exception {
		WorkspaceSymbolParams params = new WorkspaceSymbolParams( "User" );

		var result = svc.symbol( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.isLeft() ).isTrue();
		List<? extends SymbolInformation> symbols = result.getLeft();

		assertThat( symbols ).isNotEmpty();
		// Should find UserEntity, UserRepository, UserService, username
		assertThat( symbols.stream().anyMatch( s -> s.getName().equals( "UserEntity" ) ) ).isTrue();
		assertThat( symbols.stream().anyMatch( s -> s.getName().equals( "UserRepository" ) ) ).isTrue();
		assertThat( symbols.stream().anyMatch( s -> s.getName().equals( "UserService" ) ) ).isTrue();
	}

	/**
	 * Test fuzzy matching - substring match.
	 */
	@Test
	void testFuzzyMatchSubstring() throws Exception {
		WorkspaceSymbolParams params = new WorkspaceSymbolParams( "Repo" );

		var result = svc.symbol( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.isLeft() ).isTrue();
		List<? extends SymbolInformation> symbols = result.getLeft();

		assertThat( symbols ).isNotEmpty();
		// Should find UserRepository, IUserRepository, repository property
		assertThat( symbols.stream().anyMatch( s -> s.getName().contains( "Repo" ) ) ).isTrue();
	}

	/**
	 * Test case-insensitive matching.
	 */
	@Test
	void testCaseInsensitiveMatch() throws Exception {
		WorkspaceSymbolParams params = new WorkspaceSymbolParams( "userentity" );

		var result = svc.symbol( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.isLeft() ).isTrue();
		List<? extends SymbolInformation> symbols = result.getLeft();

		assertThat( symbols ).isNotEmpty();
		assertThat( symbols.stream().anyMatch( s -> s.getName().equalsIgnoreCase( "UserEntity" ) ) ).isTrue();
	}

	// ============ Container Name Tests ============

	/**
	 * Test that methods include container name (class).
	 */
	@Test
	void testMethodContainerName() throws Exception {
		WorkspaceSymbolParams params = new WorkspaceSymbolParams( "findById" );

		var result = svc.symbol( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.isLeft() ).isTrue();
		List<? extends SymbolInformation> symbols = result.getLeft();

		assertThat( symbols ).isNotEmpty();
		var findByIdSymbol = symbols.stream()
		    .filter( s -> s.getName().equals( "findById" ) && s.getKind() == SymbolKind.Method )
		    .findFirst();
		assertThat( findByIdSymbol.isPresent() ).isTrue();
		// Container name should be the class name
		assertThat( findByIdSymbol.get().getContainerName() ).isNotNull();
	}

	/**
	 * Test that properties include container name (class).
	 */
	@Test
	void testPropertyContainerName() throws Exception {
		WorkspaceSymbolParams params = new WorkspaceSymbolParams( "email" );

		var result = svc.symbol( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.isLeft() ).isTrue();
		List<? extends SymbolInformation> symbols = result.getLeft();

		assertThat( symbols ).isNotEmpty();
		var emailSymbol = symbols.stream()
		    .filter( s -> s.getName().equals( "email" ) && s.getKind() == SymbolKind.Property )
		    .findFirst();
		assertThat( emailSymbol.isPresent() ).isTrue();
		// Container name should be the class name (UserEntity)
		assertThat( emailSymbol.get().getContainerName() ).isEqualTo( "UserEntity" );
	}

	// ============ Scoring/Ranking Tests ============

	/**
	 * Test that exact matches rank higher than substring matches.
	 */
	@Test
	void testExactMatchRanksHigher() throws Exception {
		WorkspaceSymbolParams params = new WorkspaceSymbolParams( "init" );

		var result = svc.symbol( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.isLeft() ).isTrue();
		List<? extends SymbolInformation> symbols = result.getLeft();

		assertThat( symbols ).isNotEmpty();
		// Exact matches for "init" should come first
		var firstSymbol = symbols.get( 0 );
		assertThat( firstSymbol.getName() ).isEqualTo( "init" );
	}

	/**
	 * Test that prefix matches rank higher than substring matches.
	 */
	@Test
	void testPrefixMatchRanksHigher() throws Exception {
		WorkspaceSymbolParams params = new WorkspaceSymbolParams( "save" );

		var result = svc.symbol( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.isLeft() ).isTrue();
		List<? extends SymbolInformation> symbols = result.getLeft();

		assertThat( symbols ).isNotEmpty();
		// "save" method should come before "clearCache" (which doesn't match)
		var firstSymbol = symbols.get( 0 );
		assertThat( firstSymbol.getName().toLowerCase().startsWith( "save" ) ).isTrue();
	}

	// ============ Empty Query Tests ============

	/**
	 * Test empty query returns limited symbols (or all symbols).
	 */
	@Test
	void testEmptyQueryReturnsSymbols() throws Exception {
		WorkspaceSymbolParams params = new WorkspaceSymbolParams( "" );

		var result = svc.symbol( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.isLeft() ).isTrue();
		List<? extends SymbolInformation> symbols = result.getLeft();

		// Should return symbols even with empty query (may be limited)
		assertThat( symbols ).isNotNull();
		// With 4 files, we have: 4 classes/interfaces, multiple methods, multiple properties
		// We should have at least some symbols
		assertThat( symbols.size() ).isGreaterThan( 0 );
	}

	// ============ Symbol Kind Tests ============

	/**
	 * Test that all expected symbol kinds are returned.
	 */
	@Test
	void testSymbolKindsPresent() throws Exception {
		WorkspaceSymbolParams params = new WorkspaceSymbolParams( "" );

		var result = svc.symbol( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.isLeft() ).isTrue();
		List<? extends SymbolInformation> symbols = result.getLeft();

		// Should have classes, interfaces, methods, and properties
		assertThat( symbols.stream().anyMatch( s -> s.getKind() == SymbolKind.Class ) ).isTrue();
		assertThat( symbols.stream().anyMatch( s -> s.getKind() == SymbolKind.Interface ) ).isTrue();
		assertThat( symbols.stream().anyMatch( s -> s.getKind() == SymbolKind.Method ) ).isTrue();
		assertThat( symbols.stream().anyMatch( s -> s.getKind() == SymbolKind.Property ) ).isTrue();
	}

	// ============ Location Tests ============

	/**
	 * Test that symbols include valid file locations.
	 */
	@Test
	void testSymbolsHaveValidLocations() throws Exception {
		WorkspaceSymbolParams params = new WorkspaceSymbolParams( "UserEntity" );

		var result = svc.symbol( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.isLeft() ).isTrue();
		List<? extends SymbolInformation> symbols = result.getLeft();

		assertThat( symbols ).isNotEmpty();
		var symbol = symbols.stream().filter( s -> s.getName().equals( "UserEntity" ) ).findFirst();
		assertThat( symbol.isPresent() ).isTrue();
		assertThat( symbol.get().getLocation() ).isNotNull();
		assertThat( symbol.get().getLocation().getUri() ).contains( "UserEntity.bx" );
	}

}
