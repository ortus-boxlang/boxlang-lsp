package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticTag;
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

	@Test
	void testShouldShowUnusedLocalVaraibles() {
		ProjectContextProvider	pcp			= ProjectContextProvider.getInstance();
		// Get the project root directory
		Path					projectRoot	= Paths.get( System.getProperty( "user.dir" ) );
		// Resolve the file path relative to the project root directory
		Path					p			= projectRoot.resolve( "src/test/resources/files/unusedVariables.bx" );
		File					f			= p.toFile();
		assertTrue( f.exists(), "Test file does not exist: " + p.toString() );

		List<Diagnostic> diagnostics = pcp.getFileDiagnostics( f.toURI() );
		assertNotNull( diagnostics, "Diagnostics should not be null." );

		Diagnostic unusedVariables = diagnostics.stream()
		    .filter( d -> d.getMessage().contains( "Variable [theUnusedVar] is declared but never used." ) )
		    .findFirst()
		    .orElse( null );

		assertThat( unusedVariables ).isNotNull();
	}

	@Test
	void testShouldNotShowUsedLocalVariables() {
		ProjectContextProvider	pcp			= ProjectContextProvider.getInstance();
		// Get the project root directory
		Path					projectRoot	= Paths.get( System.getProperty( "user.dir" ) );
		// Resolve the file path relative to the project root directory
		Path					p			= projectRoot.resolve( "src/test/resources/files/unusedVariables.bx" );
		File					f			= p.toFile();
		assertTrue( f.exists(), "Test file does not exist: " + p.toString() );

		List<Diagnostic> diagnostics = pcp.getFileDiagnostics( f.toURI() );
		assertNotNull( diagnostics, "Diagnostics should not be null." );

		Diagnostic unusedVariables = diagnostics.stream()
		    .filter( d -> d.getMessage().contains( "Variable [usedVar] is declared but never used." ) )
		    .findFirst()
		    .orElse( null );

		assertThat( unusedVariables ).isNull();
	}

	@Test
	void testShouldNotShowVariablesUsedByAssignment() {
		ProjectContextProvider	pcp			= ProjectContextProvider.getInstance();
		// Get the project root directory
		Path					projectRoot	= Paths.get( System.getProperty( "user.dir" ) );
		// Resolve the file path relative to the project root directory
		Path					p			= projectRoot.resolve( "src/test/resources/files/unusedVariables.bx" );
		File					f			= p.toFile();
		assertTrue( f.exists(), "Test file does not exist: " + p.toString() );

		List<Diagnostic> diagnostics = pcp.getFileDiagnostics( f.toURI() );
		assertNotNull( diagnostics, "Diagnostics should not be null." );

		Diagnostic unusedVariables = diagnostics.stream()
		    .filter( d -> d.getMessage().contains( "Variable [usedByX] is declared but never used." ) )
		    .findFirst()
		    .orElse( null );

		assertThat( unusedVariables ).isNull();
	}

	@Test
	void testShouldWarnForUnusedArguments() {
		ProjectContextProvider	pcp			= ProjectContextProvider.getInstance();
		// Get the project root directory
		Path					projectRoot	= Paths.get( System.getProperty( "user.dir" ) );
		// Resolve the file path relative to the project root directory
		Path					p			= projectRoot.resolve( "src/test/resources/files/unusedVariables.bx" );
		File					f			= p.toFile();
		assertTrue( f.exists(), "Test file does not exist: " + p.toString() );

		List<Diagnostic> diagnostics = pcp.getFileDiagnostics( f.toURI() );
		assertNotNull( diagnostics, "Diagnostics should not be null." );

		Diagnostic unusedVariables = diagnostics.stream()
		    .filter( d -> d.getMessage().contains( "Variable [theUnusedArg] is declared but never used." ) )
		    .findFirst()
		    .orElse( null );

		assertThat( unusedVariables ).isNotNull();
	}

	@Test
	void testHasDiagnosticTag() {
		ProjectContextProvider	pcp			= ProjectContextProvider.getInstance();
		// Get the project root directory
		Path					projectRoot	= Paths.get( System.getProperty( "user.dir" ) );
		// Resolve the file path relative to the project root directory
		Path					p			= projectRoot.resolve( "src/test/resources/files/unusedVariables.bx" );
		File					f			= p.toFile();
		assertTrue( f.exists(), "Test file does not exist: " + p.toString() );

		List<Diagnostic> diagnostics = pcp.getFileDiagnostics( f.toURI() );
		assertNotNull( diagnostics, "Diagnostics should not be null." );

		Diagnostic unusedVariables = diagnostics.stream()
		    .filter( d -> d.getMessage().contains( "Variable [theUnusedArg] is declared but never used." ) )
		    .findFirst()
		    .orElse( null );

		assertThat( unusedVariables ).isNotNull();
		assertThat( unusedVariables.getTags() ).contains( DiagnosticTag.Unnecessary );
	}

	@Test
	void testCountsArrayAccessAsUse() {
		ProjectContextProvider	pcp			= ProjectContextProvider.getInstance();
		// Get the project root directory
		Path					projectRoot	= Paths.get( System.getProperty( "user.dir" ) );
		// Resolve the file path relative to the project root directory
		Path					p			= projectRoot.resolve( "src/test/resources/files/unusedVariables.bx" );
		File					f			= p.toFile();
		assertTrue( f.exists(), "Test file does not exist: " + p.toString() );

		List<Diagnostic> diagnostics = pcp.getFileDiagnostics( f.toURI() );
		assertNotNull( diagnostics, "Diagnostics should not be null." );

		Diagnostic theStruct = diagnostics.stream()
		    .filter( d -> d.getMessage().contains( "Variable [theStruct] is declared but never used." ) )
		    .findFirst()
		    .orElse( null );

		assertThat( theStruct ).isNull();

		Diagnostic theKey = diagnostics.stream()
		    .filter( d -> d.getMessage().contains( "Variable [theKey] is declared but never used." ) )
		    .findFirst()
		    .orElse( null );

		assertThat( theKey ).isNull();

	}

	@Test
	void testShouldNotReportVariableUsedInReassignment() {
		ProjectContextProvider	pcp			= ProjectContextProvider.getInstance();
		// Get the project root directory
		Path					projectRoot	= Paths.get( System.getProperty( "user.dir" ) );
		// Resolve the file path relative to the project root directory
		Path					p			= projectRoot.resolve( "src/test/resources/files/unusedVariablesAssignmentTest.bx" );
		File					f			= p.toFile();
		assertTrue( f.exists(), "Test file does not exist: " + p.toString() );

		List<Diagnostic> diagnostics = pcp.getFileDiagnostics( f.toURI() );
		assertNotNull( diagnostics, "Diagnostics should not be null." );

		// Variable x should NOT be reported as unused because it has a reassignment
		Diagnostic xUnused = diagnostics.stream()
		    .filter( d -> d.getMessage().contains( "Variable [x] is declared but never used." ) )
		    .findFirst()
		    .orElse( null );

		assertThat( xUnused ).isNull();
	}

	@Test
	void testManualExampleFromIssueDescription() {
		ProjectContextProvider	pcp			= ProjectContextProvider.getInstance();
		// Get the project root directory
		Path					projectRoot	= Paths.get( System.getProperty( "user.dir" ) );
		// Resolve the file path relative to the project root directory
		Path					p			= projectRoot.resolve( "src/test/resources/files/manual-test-example.bx" );
		File					f			= p.toFile();
		assertTrue( f.exists(), "Test file does not exist: " + p.toString() );

		List<Diagnostic> diagnostics = pcp.getFileDiagnostics( f.toURI() );
		assertNotNull( diagnostics, "Diagnostics should not be null." );

		// The variable x should NOT be reported as unused because it has a reassignment
		// This test verifies the exact example from the issue description
		Diagnostic xUnused = diagnostics.stream()
		    .filter( d -> d.getMessage().contains( "Variable [x] is declared but never used." ) )
		    .findFirst()
		    .orElse( null );

		assertThat( xUnused ).isNull();
	}
}
