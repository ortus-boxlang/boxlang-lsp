package ortus.boxlang.lsp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionParams;
import org.junit.jupiter.api.Test;

import ortus.boxlang.lsp.workspace.ProjectContextProvider;

public class CompletionsTest {

	@Test
	void testGetAvailableCompletions() {
		ProjectContextProvider	pcp			= ProjectContextProvider.getInstance();
		// Get the project root directory
		Path					projectRoot	= Paths.get( System.getProperty( "user.dir" ) );
		// Resolve the file path relative to the project root directory
		Path					p			= projectRoot.resolve( "src/test/resources/files/completionTest1.bx" );
		File					f			= p.toFile();
		assertTrue( f.exists(), "Test file does not exist: " + p.toString() );
		List<CompletionItem> completionItems = pcp.getAvailableCompletions( f.toURI(), new CompletionParams() );
		assertFalse( completionItems.isEmpty(), "Completion items should not be empty." );
	}
}
