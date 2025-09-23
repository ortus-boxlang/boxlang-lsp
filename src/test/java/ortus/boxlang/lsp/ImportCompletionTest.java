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
import org.junit.jupiter.api.Test;

import ortus.boxlang.lsp.workspace.ProjectContextProvider;

public class ImportCompletionTest {

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
		assertThat( javaNetItem.getTextEdit() ).isNotNull();
		assertThat( javaNetItem.getTextEdit().isLeft() ).isTrue();
		assertThat( javaNetItem.getTextEdit().getLeft().getNewText() ).isEqualTo( "import java.net;" );
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
		    .anyMatch( ci -> ci.getLabel().equals( "java.net.URI" ) );

		assertThat( hasURI ).isTrue();

		// Verify it's marked as a Class
		CompletionItem uriItem = completionItems.stream()
		    .filter( ci -> ci.getLabel().equals( "java.net.URI" ) )
		    .findFirst()
		    .orElse( null );

		assertThat( uriItem ).isNotNull();
		assertThat( uriItem.getKind() ).isEqualTo( CompletionItemKind.Class );
		assertThat( uriItem.getDetail() ).contains( "Java class" );
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
		    .anyMatch( ci -> ci.getLabel().equals( "java.net.URI" ) );

		assertThat( hasURI ).isTrue();

		// Should also find other URI-related classes
		boolean hasOtherURIClass = completionItems.stream()
		    .anyMatch( ci -> ci.getLabel().contains( "URI" ) && ci.getLabel().startsWith( "java." ) );

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
		    .anyMatch( ci -> ci.getLabel().equals( "java.util.List" ) );

		assertThat( hasList ).isTrue();
	}
}