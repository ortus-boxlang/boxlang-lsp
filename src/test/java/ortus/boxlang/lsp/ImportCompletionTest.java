package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertThat;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.eclipse.lsp4j.CompletionItem;
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
	}

	@Test
	void testItShouldOfferSpecificJavaPackageForPartialImport() {
		ProjectContextProvider	pcp					= ProjectContextProvider.getInstance();
		// Get the project root directory
		Path					projectRoot			= Paths.get( System.getProperty( "user.dir" ) );
		// Resolve the file path relative to the project root directory
		Path					p					= projectRoot.resolve( "src/test/resources/files/importTest.bx" );
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
}