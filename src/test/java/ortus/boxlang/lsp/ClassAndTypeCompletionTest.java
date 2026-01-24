package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertThat;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

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

public class ClassAndTypeCompletionTest extends BaseTest {

	private static Path				projectRoot;
	private static Path				testDir;
	private ProjectContextProvider	pcp;

	@BeforeAll
	public static void setUpClass() {
		projectRoot	= Paths.get( System.getProperty( "user.dir" ) );
		testDir		= projectRoot.resolve( "src/test/resources/files/classTypeCompletionTest" );
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
			} else if ( file.getName().endsWith( ".bx" ) ) {
				index.indexFile( file.toURI() );
				pcp.trackDocumentOpen( file.toURI(), Files.readString( file.toPath() ) );
			}
		}
	}

	@Test
	void testCompletionAfterNewKeyword() {
		// Test completion after "new " keyword
		Path					testFile	= testDir.resolve( "TestConsumer.bx" );
		File					f			= testFile.toFile();

		CompletionParams		params		= new CompletionParams();
		TextDocumentIdentifier	td			= new TextDocumentIdentifier( testFile.toUri().toString() );
		params.setTextDocument( td );
		params.setPosition( new Position( 3, 18 ) );	// After "new " on line 4 (0-indexed = line 3)

		List<CompletionItem>		items	= pcp.getAvailableCompletions( f.toURI(), params );

		// Should include User class
		Optional<CompletionItem>	user	= items.stream()
		    .filter( item -> item.getLabel().equals( "User" ) && item.getKind() == CompletionItemKind.Class )
		    .findFirst();

		assertThat( user.isPresent() ).isTrue();
		assertThat( user.get().getDetail() ).contains( "User" );

		// Should include BaseEntity
		Optional<CompletionItem> baseEntity = items.stream()
		    .filter( item -> item.getLabel().equals( "BaseEntity" ) && item.getKind() == CompletionItemKind.Class )
		    .findFirst();

		assertThat( baseEntity.isPresent() ).isTrue();

		// Should NOT include interfaces in new completion
		boolean hasInterface = items.stream()
		    .anyMatch( item -> item.getLabel().equals( "IRepository" ) );

		assertThat( hasInterface ).isFalse();
	}

	@Test
	void testCompletionAfterExtendsKeyword() {
		// Test completion after "extends " keyword
		Path					testFile	= testDir.resolve( "TestExtendsCompletion.bx" );
		File					f			= testFile.toFile();

		CompletionParams		params		= new CompletionParams();
		TextDocumentIdentifier	td			= new TextDocumentIdentifier( testFile.toUri().toString() );
		params.setTextDocument( td );
		params.setPosition( new Position( 3, 15 ) );	// Between quotes in extends="" on line 4 (0-indexed = line 3)

		List<CompletionItem>		items		= pcp.getAvailableCompletions( f.toURI(), params );

		// Should include classes
		Optional<CompletionItem>	baseEntity	= items.stream()
		    .filter( item -> item.getLabel().equals( "BaseEntity" ) && item.getKind() == CompletionItemKind.Class )
		    .findFirst();

		assertThat( baseEntity.isPresent() ).isTrue();

		// Should NOT include interfaces in extends completion
		boolean hasInterface = items.stream()
		    .anyMatch( item -> item.getLabel().equals( "IRepository" ) );

		assertThat( hasInterface ).isFalse();
	}

	@Test
	void testCompletionAfterImplementsKeyword() {
		// Test completion after "implements " keyword
		Path					testFile	= testDir.resolve( "TestImplementsCompletion.bx" );
		File					f			= testFile.toFile();

		CompletionParams		params		= new CompletionParams();
		TextDocumentIdentifier	td			= new TextDocumentIdentifier( testFile.toUri().toString() );
		params.setTextDocument( td );
		params.setPosition( new Position( 3, 18 ) );	// Between quotes in implements="" on line 4 (0-indexed = line 3)

		List<CompletionItem>		items		= pcp.getAvailableCompletions( f.toURI(), params );

		// Should include interfaces
		Optional<CompletionItem>	iRepository	= items.stream()
		    .filter( item -> item.getLabel().equals( "IRepository" ) && item.getKind() == CompletionItemKind.Interface )
		    .findFirst();

		assertThat( iRepository.isPresent() ).isTrue();

		Optional<CompletionItem> iValidatable = items.stream()
		    .filter( item -> item.getLabel().equals( "IValidatable" ) && item.getKind() == CompletionItemKind.Interface )
		    .findFirst();

		assertThat( iValidatable.isPresent() ).isTrue();

		// Should NOT include classes in implements completion
		boolean hasClass = items.stream()
		    .anyMatch( item -> item.getLabel().equals( "BaseEntity" ) && item.getKind() == CompletionItemKind.Class );

		assertThat( hasClass ).isFalse();
	}

	@Test
	void testCompletionWithPrefix() {
		// Test that prefix filtering works
		Path	testFile	= testDir.resolve( "TestConsumer.bx" );
		File	f			= testFile.toFile();

		// Modify the content to have a prefix
		try {
			String fileContent = Files.readString( testFile );
			fileContent = fileContent.replace( "var user = new", "var user = new Us" );
			pcp.trackDocumentOpen( f.toURI(), fileContent );

			CompletionParams		params	= new CompletionParams();
			TextDocumentIdentifier	td		= new TextDocumentIdentifier( testFile.toUri().toString() );
			params.setTextDocument( td );
			params.setPosition( new Position( 3, 20 ) );	// After "new Us"

			List<CompletionItem>		items	= pcp.getAvailableCompletions( f.toURI(), params );

			// Should include User (matches "Us" prefix)
			Optional<CompletionItem>	user	= items.stream()
			    .filter( item -> item.getLabel().equals( "User" ) )
			    .findFirst();

			assertThat( user.isPresent() ).isTrue();

			// Should NOT include ProductRepository (doesn't match "Us" prefix)
			boolean hasProductRepository = items.stream()
			    .anyMatch( item -> item.getLabel().equals( "ProductRepository" ) );

			assertThat( hasProductRepository ).isFalse();

		} catch ( Exception e ) {
			throw new RuntimeException( e );
		}
	}

	@Test
	void testCompletionShowsFileLocation() {
		// Test that completion items show file location in detail
		Path					testFile	= testDir.resolve( "TestConsumer.bx" );
		File					f			= testFile.toFile();

		CompletionParams		params		= new CompletionParams();
		TextDocumentIdentifier	td			= new TextDocumentIdentifier( testFile.toUri().toString() );
		params.setTextDocument( td );
		params.setPosition( new Position( 3, 18 ) );	// After "new "

		List<CompletionItem>		items	= pcp.getAvailableCompletions( f.toURI(), params );

		// Find User completion item
		Optional<CompletionItem>	user	= items.stream()
		    .filter( item -> item.getLabel().equals( "User" ) )
		    .findFirst();

		assertThat( user.isPresent() ).isTrue();
		// Detail should contain file reference
		assertThat( user.get().getDetail() ).isNotNull();
	}

	@Test
	void testCompletionIncludesSubpackageClasses() {
		// Test that classes from subpackages are included
		Path					testFile	= testDir.resolve( "TestConsumer.bx" );
		File					f			= testFile.toFile();

		CompletionParams		params		= new CompletionParams();
		TextDocumentIdentifier	td			= new TextDocumentIdentifier( testFile.toUri().toString() );
		params.setTextDocument( td );
		params.setPosition( new Position( 3, 18 ) );	// After "new "

		List<CompletionItem>		items		= pcp.getAvailableCompletions( f.toURI(), params );

		// Should include ProductRepository from subpackage
		Optional<CompletionItem>	productRepo	= items.stream()
		    .filter( item -> item.getLabel().equals( "ProductRepository" ) )
		    .findFirst();

		assertThat( productRepo.isPresent() ).isTrue();
		// Should show the full package path
		assertThat( productRepo.get().getDetail() ).contains( "subpackage" );
	}
}
