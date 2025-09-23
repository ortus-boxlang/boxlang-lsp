package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.Test;

import ortus.boxlang.lsp.workspace.ProjectContextProvider;

public class ImportCompletionDebugTest {

	@Test
	void testDebugImportCompletion() throws IOException {
		// Create a simple test file with import statement
		Path	projectRoot	= Paths.get( System.getProperty( "user.dir" ) );
		Path	testFile	= projectRoot.resolve( "src/test/resources/files/importDebug.bx" );

		// Create test content with specific import statement
		String	testContent	= "import java.";
		Files.write( testFile, testContent.getBytes() );

		try {
			ProjectContextProvider	pcp					= ProjectContextProvider.getInstance();
			File					f					= testFile.toFile();

			CompletionParams		completionParams	= new CompletionParams();
			TextDocumentIdentifier	td					= new TextDocumentIdentifier( testFile.toUri().toString() );
			// Position at end of "import java."
			completionParams.setPosition( new Position( 0, 12 ) );
			completionParams.setTextDocument( td );

			List<CompletionItem> completionItems = pcp.getAvailableCompletions( f.toURI(), completionParams );

			System.out.println( "Found " + completionItems.size() + " completion items:" );
			for ( CompletionItem item : completionItems ) {
				System.out.println( "  - " + item.getLabel() + " (" + item.getKind() + "): " + item.getDetail() );
			}

			// Should find java packages
			boolean hasAnyJavaPackage = completionItems.stream()
			    .anyMatch( ci -> ci.getLabel().startsWith( "java." ) );

			assertThat( hasAnyJavaPackage ).isTrue();

		} finally {
			// Clean up
			Files.deleteIfExists( testFile );
		}
	}
}