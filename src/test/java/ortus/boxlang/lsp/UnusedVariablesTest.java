package ortus.boxlang.lsp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import ortus.boxlang.lsp.workspace.ProjectContextProvider;

public class UnusedVariablesTest {

	@Test
	void testDeemphasizesUnusedVariables() {
		ProjectContextProvider	pcp			= ProjectContextProvider.getInstance();
		// Get the project root directory
		Path					projectRoot	= Paths.get( System.getProperty( "user.dir" ) );
		// Resolve the file path relative to the project root directory
		Path					p			= projectRoot.resolve( "src/test/resources/files/unusedVariablesTest1.bx" );
		File					f			= p.toFile();
		assertTrue( f.exists(), "Test file does not exist: " + p.toString() );
		List<Diagnostic> diagnostics = pcp.getFileDiagnostics( f.toURI() );
		assertNotNull( diagnostics, "Diagnostics should not be null." );
		assertFalse( diagnostics.isEmpty(), "Diagnostics should not be empty." );
		assertEquals( 1, diagnostics.size(), "Diagnostics should contain 1 item." );
		Diagnostic d = diagnostics.get( 0 );
		assertEquals( "Variable [bar] is declared but never used.", d.getMessage() );
	}
}
