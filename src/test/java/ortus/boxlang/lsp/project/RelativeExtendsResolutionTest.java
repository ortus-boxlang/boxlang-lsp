package ortus.boxlang.lsp.project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.WorkspaceFolder;

import ortus.boxlang.lsp.BaseTest;
import ortus.boxlang.lsp.workspace.ProjectContextProvider;
import ortus.boxlang.lsp.workspace.index.ProjectIndex;

/**
 * Tests for relative extends/implements resolution.
 *
 * When a class references another class using a dot-path like "subpackage.BaseType",
 * the LSP should resolve it relative to the current file's directory, even if the
 * referenced file has not been pre-indexed.
 */
public class RelativeExtendsResolutionTest extends BaseTest {

	@TempDir
	Path							tempDir;

	private ProjectIndex			index;
	private ProjectContextProvider	provider;

	@BeforeEach
	void setUp() throws Exception {
		index = new ProjectIndex();
		index.initialize( tempDir );

		provider = ProjectContextProvider.getInstance();
		provider.setIndex( index );

		WorkspaceFolder folder = new WorkspaceFolder();
		folder.setUri( tempDir.toUri().toString() );
		folder.setName( "test-project" );
		provider.setWorkspaceFolders( List.of( folder ) );
	}

	/**
	 * This test reproduces the issue where bxObj.bx extends "subpackage.BaseType"
	 * and implements "subpackage.InterfaceDef", but the LSP reports them as not found
	 * when only bxObj.bx has been indexed (i.e. the referenced files are not in the index).
	 *
	 * The extends/implements paths are relative to the file's location, so the LSP
	 * should be able to resolve them by checking the filesystem.
	 */
	@Test
	void relativeExtendsAndImplementsShouldResolveWithoutPreIndexing() throws Exception {
		// Create the subpackage directory and referenced files
		Path subpackageDir = tempDir.resolve( "subpackage" );
		Files.createDirectories( subpackageDir );

		String baseTypeCode = """
		                      class {
		                          property name="propA";
		                      }
		                      """;
		Files.writeString( subpackageDir.resolve( "BaseType.bx" ), baseTypeCode );

		String interfaceCode = """
		                       interface InterfaceDef {
		                           public function doSomething( string a, string b );
		                       }
		                       """;
		Files.writeString( subpackageDir.resolve( "InterfaceDef.bx" ), interfaceCode );

		// Create bxObj.bx in the root that references subpackage types
		String	bxObjCode	= """
		                      class extends="subpackage.BaseType" implements="subpackage.InterfaceDef" {
		                          property name="bxObjProp1";

		                          function bxObjFunction( string param1, numeric param2 ) {
		                              // function body
		                          }
		                      }
		                      """;
		Path	bxObjFile	= tempDir.resolve( "bxObj.bx" );
		Files.writeString( bxObjFile, bxObjCode );

		// Index ONLY bxObj.bx — simulate a cold index where the referenced files
		// have not yet been indexed (e.g. the background workspace scan hasn't run).
		index.indexFile( bxObjFile.toUri() );

		// Request diagnostics for bxObj.bx
		List<Diagnostic> diagnostics = provider.getFileDiagnostics( bxObjFile.toUri() );
		assertNotNull( diagnostics );

		// Print diagnostics for debugging
		diagnostics.forEach( d -> {
			System.out.println( "Diagnostic [" + d.getSeverity() + "]: " + d.getMessage() );
		} );

		// We expect ZERO error diagnostics — the relative extends/implements paths
		// should resolve to the files on disk even without pre-indexing.
		List<Diagnostic> errors = diagnostics.stream()
		    .filter( d -> d.getSeverity() == DiagnosticSeverity.Error )
		    .toList();

		assertTrue( errors.isEmpty(),
		    "Expected no error diagnostics, but got: " +
		        errors.stream().map( Diagnostic::getMessage ).toList() );
	}

	/**
	 * This test reproduces the issue where BaseType.bx (inside subpackage/)
	 * extends "subsubpackage.EvenBaserType" — the dot-path is relative to the
	 * current file's directory, not the workspace root.
	 */
	@Test
	void nestedRelativeExtendsShouldResolveFromParentDirectory() throws Exception {
		// Create subpackage/subsubpackage directory and the base type
		Path subsubpackageDir = tempDir.resolve( "subpackage" ).resolve( "subsubpackage" );
		Files.createDirectories( subsubpackageDir );

		String evenBaserTypeCode = """
		                           class {
		                           }
		                           """;
		Files.writeString( subsubpackageDir.resolve( "EvenBaserType.bx" ), evenBaserTypeCode );

		// Create BaseType.bx inside subpackage/ that references subsubpackage.EvenBaserType
		Path	subpackageDir	= tempDir.resolve( "subpackage" );
		String	baseTypeCode	= """
		                          class extends="subsubpackage.EvenBaserType" {
		                              property name="propA";
		                          }
		                          """;
		Path	baseTypeFile	= subpackageDir.resolve( "BaseType.bx" );
		Files.writeString( baseTypeFile, baseTypeCode );

		// Index ONLY BaseType.bx — the referenced file is not in the index yet
		index.indexFile( baseTypeFile.toUri() );

		// Request diagnostics for BaseType.bx
		List<Diagnostic> diagnostics = provider.getFileDiagnostics( baseTypeFile.toUri() );
		assertNotNull( diagnostics );

		diagnostics.forEach( d -> {
			System.out.println( "Nested Diagnostic [" + d.getSeverity() + "]: " + d.getMessage() );
		} );

		List<Diagnostic> errors = diagnostics.stream()
		    .filter( d -> d.getSeverity() == DiagnosticSeverity.Error )
		    .toList();

		assertTrue( errors.isEmpty(),
		    "Expected no error diagnostics for nested relative extends, but got: " +
		        errors.stream().map( Diagnostic::getMessage ).toList() );
	}
}
