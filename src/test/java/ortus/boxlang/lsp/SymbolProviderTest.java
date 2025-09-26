package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ortus.boxlang.lsp.workspace.ClassSymbol;
import ortus.boxlang.lsp.workspace.SymbolProvider;
import ortus.boxlang.runtime.BoxRuntime;

class SymbolProviderTest extends BaseTest {

	@TempDir
	Path					tempDir;

	private SymbolProvider	symbolProvider;

	@BeforeEach
	void setUp() throws IOException {
		symbolProvider = new SymbolProvider();
		symbolProvider.initialize( Files.createTempFile( "lspcache-", ".json" ) );
	}

	@Test
	void testFindClassSymbolsWithEmptyCache() {
		URI					fileUri	= URI.create( "file:///test.bx" );
		List<ClassSymbol>	symbols	= symbolProvider.findClassSymbols( fileUri, "Test" );

		assertTrue( symbols.isEmpty(), "Empty cache should return no symbols" );
	}

	@Test
	void testAddAndFindClassSymbols() {
		URI					fileUri			= URI.create( "file:///test.bx" );
		String				fileUriString	= fileUri.toString();

		// Create test symbols
		Range				range			= new Range( new Position( 0, 0 ), new Position( 5, 10 ) );
		ClassSymbol			symbol1			= new ClassSymbol( "TestClass", range, fileUriString, Instant.now() );
		ClassSymbol			symbol2			= new ClassSymbol( "MyClass", range, fileUriString, Instant.now() );

		List<ClassSymbol>	symbols			= List.of( symbol1, symbol2 );

		// Add symbols
		symbolProvider.addClassSymbols( fileUriString, symbols );

		// Find symbols with partial match
		List<ClassSymbol> foundSymbols = symbolProvider.findClassSymbols( fileUri, "Test" );
		assertEquals( 1, foundSymbols.size(), "Should find one symbol matching 'Test'" );
		assertEquals( "TestClass", foundSymbols.get( 0 ).name() );

		// Find symbols with different partial match
		foundSymbols = symbolProvider.findClassSymbols( fileUri, "Class" );
		assertEquals( 2, foundSymbols.size(), "Should find two symbols containing 'Class'" );

		// Find symbols with exact match
		foundSymbols = symbolProvider.findClassSymbols( fileUri, "MyClass" );
		assertEquals( 1, foundSymbols.size(), "Should find one symbol matching 'MyClass'" );
		assertEquals( "MyClass", foundSymbols.get( 0 ).name() );
	}

	@Test
	void testFindClassSymbolsCaseInsensitive() {
		URI			fileUri			= URI.create( "file:///test.bx" );
		String		fileUriString	= fileUri.toString();

		Range		range			= new Range( new Position( 0, 0 ), new Position( 5, 10 ) );
		ClassSymbol	symbol			= new ClassSymbol( "TestClass", range, fileUriString, Instant.now() );

		symbolProvider.addClassSymbols( fileUriString, List.of( symbol ) );

		// Test case insensitive search
		List<ClassSymbol> foundSymbols = symbolProvider.findClassSymbols( fileUri, "testclass" );
		assertEquals( 1, foundSymbols.size(), "Should find symbol with case-insensitive search" );

		foundSymbols = symbolProvider.findClassSymbols( fileUri, "TEST" );
		assertEquals( 1, foundSymbols.size(), "Should find symbol with uppercase search" );
	}

	@Test
	void testFindClassSymbolsWithEmptyOrNullText() {
		URI					fileUri			= URI.create( "file:///test.bx" );

		List<ClassSymbol>	foundSymbols	= symbolProvider.findClassSymbols( fileUri, null );
		assertTrue( foundSymbols.isEmpty(), "Should return empty list for null completion text" );

		foundSymbols = symbolProvider.findClassSymbols( fileUri, "" );
		assertTrue( foundSymbols.isEmpty(), "Should return empty list for empty completion text" );

		foundSymbols = symbolProvider.findClassSymbols( fileUri, "   " );
		assertTrue( foundSymbols.isEmpty(), "Should return empty list for whitespace-only completion text" );
	}

	@Test
	void testRemoveSymbols() {
		URI			fileUri			= URI.create( "file:///test.bx" );
		String		fileUriString	= fileUri.toString();

		Range		range			= new Range( new Position( 0, 0 ), new Position( 5, 10 ) );
		ClassSymbol	symbol			= new ClassSymbol( "TestClass", range, fileUriString, Instant.now() );

		symbolProvider.addClassSymbols( fileUriString, List.of( symbol ) );

		// Verify symbol was added
		List<ClassSymbol> foundSymbols = symbolProvider.findClassSymbols( fileUri, "Test" );
		assertEquals( 1, foundSymbols.size() );

		// Remove symbols
		symbolProvider.removeSymbols( fileUriString );

		// Verify symbol was removed
		foundSymbols = symbolProvider.findClassSymbols( fileUri, "Test" );
		assertTrue( foundSymbols.isEmpty(), "Should return empty list after symbols are removed" );
	}

	@Test
	void testClearCache() {
		URI			fileUri			= URI.create( "file:///test.bx" );
		String		fileUriString	= fileUri.toString();

		Range		range			= new Range( new Position( 0, 0 ), new Position( 5, 10 ) );
		ClassSymbol	symbol			= new ClassSymbol( "TestClass", range, fileUriString, Instant.now() );

		symbolProvider.addClassSymbols( fileUriString, List.of( symbol ) );

		// Verify symbol was added
		assertEquals( 1, symbolProvider.getAllClassSymbols().size() );

		// Clear cache
		symbolProvider.clearCache();

		// Verify cache is empty
		assertTrue( symbolProvider.getAllClassSymbols().isEmpty(), "Cache should be empty after clear" );
	}

	@Test
	void testGetAllClassSymbols() {
		URI			fileUri1	= URI.create( "file:///test1.bx" );
		URI			fileUri2	= URI.create( "file:///test2.bx" );

		Range		range		= new Range( new Position( 0, 0 ), new Position( 5, 10 ) );
		ClassSymbol	symbol1		= new ClassSymbol( "TestClass1", range, fileUri1.toString(), Instant.now() );
		ClassSymbol	symbol2		= new ClassSymbol( "TestClass2", range, fileUri2.toString(), Instant.now() );

		symbolProvider.addClassSymbols( fileUri1.toString(), List.of( symbol1 ) );
		symbolProvider.addClassSymbols( fileUri2.toString(), List.of( symbol2 ) );

		List<ClassSymbol> allSymbols = symbolProvider.getAllClassSymbols();
		assertEquals( 2, allSymbols.size(), "Should return all symbols from all files" );
	}

	@Test
	void testWillUseSpecificCacheFile() throws Exception {
		String			cacheFilePath	= "src/test/resources/.test-cache1.json";
		SymbolProvider	symbolProvider	= new SymbolProvider();
		symbolProvider.initialize( Paths.get( cacheFilePath ) );

		assertThat( symbolProvider.getCacheFilePath().toString() ).isEqualTo( cacheFilePath );
	}

	@Test
	void testWillDefaultCacheLocationToBoxLangHome() throws Exception {
		String			projectPath		= "src/test/resources/files";
		SymbolProvider	symbolProvider	= new SymbolProvider();
		symbolProvider.initializeWithDefaultCacheLocation( Paths.get( projectPath ) );

		assertThat( symbolProvider.getCacheFilePath().toString() ).startsWith( BoxRuntime.getInstance().getRuntimeHome().toString() );
	}

	@Test
	void testWillCreateCacheFile() throws Exception {
		Path			cacheFilePath	= Files.createTempFile( "lspcache-", ".json" );
		SymbolProvider	symbolProvider	= new SymbolProvider();
		symbolProvider.initialize( cacheFilePath );

		System.out.println( "Cache file path: " + symbolProvider.getCacheFilePath().toString() );

		URI			fileUri			= URI.create( "file:///test.bx" );
		String		fileUriString	= fileUri.toString();

		Range		range			= new Range( new Position( 0, 0 ), new Position( 5, 10 ) );
		ClassSymbol	symbol			= new ClassSymbol( "TestClass", range, fileUriString, Instant.now() );

		// Add symbol and verify cache file is created
		symbolProvider.addClassSymbols( fileUriString, List.of( symbol ) );

		assertThat( Files.exists( symbolProvider.getCacheFilePath() ) ).isTrue();

	}
}