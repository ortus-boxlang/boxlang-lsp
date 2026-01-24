package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertThat;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ortus.boxlang.lsp.workspace.ProjectContextProvider;
import ortus.boxlang.lsp.workspace.index.ProjectIndex;

public class ImportCompletionTest extends BaseTest {

	private static Path				projectRoot;
	private static Path				testDir;
	private ProjectContextProvider	pcp;

	@BeforeAll
	public static void setUpClass() {
		projectRoot	= Paths.get( System.getProperty( "user.dir" ) );
		testDir		= projectRoot.resolve( "src/test/resources/files/importPathCompletionTest" );
	}

	@BeforeEach
	public void setUp() throws Exception {
		pcp = ProjectContextProvider.getInstance();

		// Initialize index with test files
		ProjectIndex index = pcp.getIndex();
		index.clear();
		index.initialize( testDir );

		// Index all test files recursively
		indexDirectory( testDir, index );
	}

	private void indexDirectory( Path dir, ProjectIndex index ) throws Exception {
		for ( File file : dir.toFile().listFiles() ) {
			if ( file.isDirectory() ) {
				indexDirectory( file.toPath(), index );
			} else if ( file.getName().endsWith( ".bx" ) || file.getName().endsWith( ".bxs" ) ) {
				index.indexFile( file.toURI() );
			}
		}
	}

	@Test
	void testItShouldOfferJavaPackageCompletionsForImport() {
		ProjectContextProvider	pcp					= ProjectContextProvider.getInstance();
		// Get the project root directory
		Path					projectRoot			= Paths.get( System.getProperty( "user.dir" ) );
		// Resolve the file path relative to the project root directory
		Path					p					= projectRoot.resolve( "src/test/resources/files/importTest.bx" );
		File					f					= p.toFile();

		CompletionParams		completionParams	= new CompletionParams();
		TextDocumentIdentifier	td					= new TextDocumentIdentifier( p.toUri().toString() );
		// Position at the end of "import java." - should offer java.* packages
		completionParams.setPosition( new Position( 0, 11 ) );
		completionParams.setTextDocument( td );

		List<CompletionItem>	completionItems	= pcp.getAvailableCompletions( f.toURI(), completionParams );

		// Should find java.net package among completions
		boolean					hasJavaNet		= completionItems.stream()
		    .anyMatch( ci -> ci.getLabel().equals( "java.net" ) );

		assertThat( hasJavaNet ).isTrue();

		// Should find java.util package among completions
		boolean hasJavaUtil = completionItems.stream()
		    .anyMatch( ci -> ci.getLabel().equals( "java.util" ) );

		assertThat( hasJavaUtil ).isTrue();

		// Should have TextEdit that replaces the line
		CompletionItem javaNetItem = completionItems.stream()
		    .filter( ci -> ci.getLabel().equals( "java.net" ) )
		    .findFirst()
		    .orElse( null );

		assertThat( javaNetItem ).isNotNull();
		assertThat( javaNetItem.getInsertText() ).isNotNull();
		assertThat( javaNetItem.getInsertText() ).isEqualTo( "java.net" );
	}

	@Test
	void testItShouldOfferSpecificJavaPackageForPartialImport() {
		ProjectContextProvider	pcp					= ProjectContextProvider.getInstance();
		// Get the project root directory
		Path					projectRoot			= Paths.get( System.getProperty( "user.dir" ) );
		// Resolve the file path relative to the project root directory
		Path					p					= projectRoot.resolve( "src/test/resources/files/importPartialTest.bx" );
		File					f					= p.toFile();

		CompletionParams		completionParams	= new CompletionParams();
		TextDocumentIdentifier	td					= new TextDocumentIdentifier( p.toUri().toString() );
		// Position at the end of "import java.n" - should offer java.net
		completionParams.setPosition( new Position( 0, 13 ) );
		completionParams.setTextDocument( td );

		List<CompletionItem>	completionItems	= pcp.getAvailableCompletions( f.toURI(), completionParams );

		// Should find java.net package among completions
		boolean					hasJavaNet		= completionItems.stream()
		    .anyMatch( ci -> ci.getLabel().equals( "java.net" ) );

		assertThat( hasJavaNet ).isTrue();

		// Should not find java.util since it doesn't start with "java.n"
		boolean hasJavaUtil = completionItems.stream()
		    .anyMatch( ci -> ci.getLabel().equals( "java.util" ) );

		assertThat( hasJavaUtil ).isFalse();
	}

	@Test
	void testItShouldOfferJavaClassCompletionsForImport() {
		ProjectContextProvider	pcp					= ProjectContextProvider.getInstance();
		// Get the project root directory
		Path					projectRoot			= Paths.get( System.getProperty( "user.dir" ) );
		// Resolve the file path relative to the project root directory
		Path					p					= projectRoot.resolve( "src/test/resources/files/importClassTest.bx" );
		File					f					= p.toFile();

		CompletionParams		completionParams	= new CompletionParams();
		TextDocumentIdentifier	td					= new TextDocumentIdentifier( p.toUri().toString() );
		// Position at the end of "import java.net.U" - should offer java.net.URI and other classes
		completionParams.setPosition( new Position( 0, 17 ) );
		completionParams.setTextDocument( td );

		List<CompletionItem>	completionItems	= pcp.getAvailableCompletions( f.toURI(), completionParams );

		// Should find java.net.URI class among completions
		boolean					hasURI			= completionItems.stream()
		    .anyMatch( ci -> ci.getLabel().equals( "URI" ) );

		assertThat( hasURI ).isTrue();

		// Verify it's marked as a Class
		CompletionItem uriItem = completionItems.stream()
		    .filter( ci -> ci.getLabel().equals( "URI" ) )
		    .findFirst()
		    .orElse( null );

		assertThat( uriItem ).isNotNull();
		assertThat( uriItem.getKind() ).isEqualTo( CompletionItemKind.Class );
		assertThat( uriItem.getLabelDetails().getDescription() ).contains( "java.net.URI" );
	}

	@Test
	void testItShouldOfferClassCompletionsBySimpleName() {
		ProjectContextProvider	pcp					= ProjectContextProvider.getInstance();
		// Get the project root directory
		Path					projectRoot			= Paths.get( System.getProperty( "user.dir" ) );
		// Resolve the file path relative to the project root directory
		Path					p					= projectRoot.resolve( "src/test/resources/files/importSimpleNameTest.bx" );
		File					f					= p.toFile();

		CompletionParams		completionParams	= new CompletionParams();
		TextDocumentIdentifier	td					= new TextDocumentIdentifier( p.toUri().toString() );
		// Position at the end of "import URI" - should offer java.net.URI
		completionParams.setPosition( new Position( 0, 10 ) );
		completionParams.setTextDocument( td );

		List<CompletionItem>	completionItems	= pcp.getAvailableCompletions( f.toURI(), completionParams );

		// Should find java.net.URI class among completions when searching by simple name
		boolean					hasURI			= completionItems.stream()
		    .anyMatch( ci -> ci.getLabel().equals( "URI" ) );

		assertThat( hasURI ).isTrue();

		// Should also find other URI-related classes
		boolean hasOtherURIClass = completionItems.stream()
		    .anyMatch( ci -> ci.getLabel().contains( "URI" ) && ci.getLabelDetails().getDescription().startsWith( "java." ) );

		assertThat( hasOtherURIClass ).isTrue();
	}

	@Test
	void testItShouldOfferListCompletionsBySimpleName() {
		ProjectContextProvider	pcp					= ProjectContextProvider.getInstance();
		// Get the project root directory
		Path					projectRoot			= Paths.get( System.getProperty( "user.dir" ) );
		// Resolve the file path relative to the project root directory
		Path					p					= projectRoot.resolve( "src/test/resources/files/importListTest.bx" );
		File					f					= p.toFile();

		CompletionParams		completionParams	= new CompletionParams();
		TextDocumentIdentifier	td					= new TextDocumentIdentifier( p.toUri().toString() );
		// Position at the end of "import List" - should offer java.util.List
		completionParams.setPosition( new Position( 0, 11 ) );
		completionParams.setTextDocument( td );

		List<CompletionItem>	completionItems	= pcp.getAvailableCompletions( f.toURI(), completionParams );

		// Should find java.util.List class among completions when searching by simple name
		boolean					hasList			= completionItems.stream()
		    .anyMatch( ci -> ci.getLabelDetails().getDescription().equals( "java.util.List" ) );

		assertThat( hasList ).isTrue();
	}

	// Tests for BoxLang class import path completion

	@Test
	void testItShouldOfferPackageNamesForEmptyImport() throws Exception {
		Path					p					= testDir.resolve( "emptyImport.bx" );
		File					f					= p.toFile();

		CompletionParams		completionParams	= new CompletionParams();
		TextDocumentIdentifier	td					= new TextDocumentIdentifier( p.toUri().toString() );
		// Position after "import " - should show package names
		completionParams.setPosition( new Position( 0, 7 ) );
		completionParams.setTextDocument( td );

		List<CompletionItem>	completionItems	= pcp.getAvailableCompletions( f.toURI(), completionParams );

		// Should find "models" package
		boolean					hasModels		= completionItems.stream()
		    .anyMatch( ci -> ci.getLabel().equals( "models" ) && ci.getKind() == CompletionItemKind.Module );

		assertThat( hasModels ).isTrue();

		// Should find "services" package
		boolean hasServices = completionItems.stream()
		    .anyMatch( ci -> ci.getLabel().equals( "services" ) && ci.getKind() == CompletionItemKind.Module );

		assertThat( hasServices ).isTrue();

		// Should find "controllers" package
		boolean hasControllers = completionItems.stream()
		    .anyMatch( ci -> ci.getLabel().equals( "controllers" ) && ci.getKind() == CompletionItemKind.Module );

		assertThat( hasControllers ).isTrue();
	}

	@Test
	void testItShouldOfferClassNamesAfterPackagePrefix() throws Exception {
		Path					p					= testDir.resolve( "partialPackage.bx" );
		File					f					= p.toFile();

		CompletionParams		completionParams	= new CompletionParams();
		TextDocumentIdentifier	td					= new TextDocumentIdentifier( p.toUri().toString() );
		// Position after "import models." - should show classes in models package
		completionParams.setPosition( new Position( 0, 14 ) );
		completionParams.setTextDocument( td );

		List<CompletionItem>	completionItems	= pcp.getAvailableCompletions( f.toURI(), completionParams );

		// Should find "User" class in models package
		boolean					hasUser			= completionItems.stream()
		    .anyMatch( ci -> ci.getLabel().equals( "User" ) && ci.getKind() == CompletionItemKind.Class );

		assertThat( hasUser ).isTrue();

		// Should find "Product" class in models package
		boolean hasProduct = completionItems.stream()
		    .anyMatch( ci -> ci.getLabel().equals( "Product" ) && ci.getKind() == CompletionItemKind.Class );

		assertThat( hasProduct ).isTrue();

		// Verify detail shows FQN
		CompletionItem userItem = completionItems.stream()
		    .filter( ci -> ci.getLabel().equals( "User" ) )
		    .findFirst()
		    .orElse( null );

		assertThat( userItem ).isNotNull();
		assertThat( userItem.getLabelDetails().getDescription() ).contains( "models.User" );
	}

	@Test
	void testItShouldOfferPackagesMatchingPrefix() throws Exception {
		Path					p					= testDir.resolve( "partialPrefix.bx" );
		File					f					= p.toFile();

		CompletionParams		completionParams	= new CompletionParams();
		TextDocumentIdentifier	td					= new TextDocumentIdentifier( p.toUri().toString() );
		// Position after "import m" - should show packages starting with 'm'
		completionParams.setPosition( new Position( 0, 8 ) );
		completionParams.setTextDocument( td );

		List<CompletionItem>	completionItems	= pcp.getAvailableCompletions( f.toURI(), completionParams );

		// Should find "models" package
		boolean					hasModels		= completionItems.stream()
		    .anyMatch( ci -> ci.getLabel().equals( "models" ) );

		assertThat( hasModels ).isTrue();

		// Should NOT find "services" package (doesn't start with 'm')
		boolean hasServices = completionItems.stream()
		    .anyMatch( ci -> ci.getLabel().equals( "services" ) );

		assertThat( hasServices ).isFalse();
	}

	@Test
	void testItShouldOfferClassesMatchingPartialName() throws Exception {
		Path					p					= testDir.resolve( "partialClassName.bx" );
		File					f					= p.toFile();

		CompletionParams		completionParams	= new CompletionParams();
		TextDocumentIdentifier	td					= new TextDocumentIdentifier( p.toUri().toString() );
		// Position after "import services.U" - should show UserService
		completionParams.setPosition( new Position( 0, 17 ) );
		completionParams.setTextDocument( td );

		List<CompletionItem>	completionItems	= pcp.getAvailableCompletions( f.toURI(), completionParams );

		// Should find "UserService" class
		boolean					hasUserService	= completionItems.stream()
		    .anyMatch( ci -> ci.getLabel().equals( "UserService" ) && ci.getKind() == CompletionItemKind.Class );

		assertThat( hasUserService ).isTrue();

		// Should NOT find "ProductService" class (doesn't start with 'U')
		boolean hasProductService = completionItems.stream()
		    .anyMatch( ci -> ci.getLabel().equals( "ProductService" ) );

		assertThat( hasProductService ).isFalse();
	}

	@Test
	void testItShouldOfferRootLevelClasses() throws Exception {
		Path					p					= testDir.resolve( "emptyImport.bx" );
		File					f					= p.toFile();

		CompletionParams		completionParams	= new CompletionParams();
		TextDocumentIdentifier	td					= new TextDocumentIdentifier( p.toUri().toString() );
		// Position after "import " - should show root level classes
		completionParams.setPosition( new Position( 0, 7 ) );
		completionParams.setTextDocument( td );

		List<CompletionItem>	completionItems	= pcp.getAvailableCompletions( f.toURI(), completionParams );

		// Should find "RootClass" at root level
		boolean					hasRootClass	= completionItems.stream()
		    .anyMatch( ci -> ci.getLabel().equals( "RootClass" ) && ci.getKind() == CompletionItemKind.Class );

		assertThat( hasRootClass ).isTrue();
	}
}