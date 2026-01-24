/**
 * [BoxLang LSP]
 *
 * Copyright [2023] [Ortus Solutions, Corp]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ortus.boxlang.lsp.workspace.ProjectContextProvider;
import ortus.boxlang.lsp.workspace.index.ProjectIndex;
import ortus.boxlang.runtime.BoxRuntime;

/**
 * Tests for semantic error diagnostics (Task 1.5).
 *
 * Tests detection of:
 * - Invalid extends (class not found)
 * - Invalid implements (interface not found)
 * - Duplicate method definitions
 * - Duplicate property definitions
 */
public class SemanticErrorDiagnosticsTest extends BaseTest {

	@TempDir
	Path					tempDir;

	private ProjectIndex	index;

	static BoxRuntime		runtime;

	@BeforeAll
	static void setUpRuntime() {
		runtime = BoxRuntime.getInstance( true );
	}

	@BeforeEach
	void setUp() {
		index = new ProjectIndex();
		index.initialize( tempDir );
		// Set the index in the provider for the tests
		ProjectContextProvider.getInstance().setIndex( index );
	}

	// ============ Invalid Extends Tests ============

	@Test
	void testInvalidExtendsClassNotFound() throws Exception {
		String	classCode	= """
		                      class extends="NonExistentClass" {
		                          function init() { return this; }
		                      }
		                      """;

		Path	testFile	= createTestFile( "Child.bx", classCode );
		index.indexFile( testFile.toUri() );

		List<Diagnostic> diagnostics = ProjectContextProvider.getInstance().getFileDiagnostics( testFile.toUri() );
		assertNotNull( diagnostics );

		Diagnostic invalidExtends = diagnostics.stream()
		    .filter( d -> d.getMessage().contains( "NonExistentClass" ) && d.getMessage().toLowerCase().contains( "not found" ) )
		    .findFirst()
		    .orElse( null );

		assertThat( invalidExtends ).isNotNull();
		assertThat( invalidExtends.getSeverity() ).isEqualTo( DiagnosticSeverity.Error );
	}

	@Test
	void testValidExtendsNoError() throws Exception {
		// First create the parent class
		String	parentCode	= """
		                      class {
		                          function init() { return this; }
		                      }
		                      """;
		Path	parentFile	= createTestFile( "ParentClass.bx", parentCode );
		index.indexFile( parentFile.toUri() );

		// Now create a child that extends the parent
		String	childCode	= """
		                      class extends="ParentClass" {
		                          function init() { return super.init(); }
		                      }
		                      """;
		Path	childFile	= createTestFile( "ChildClass.bx", childCode );
		index.indexFile( childFile.toUri() );

		List<Diagnostic> diagnostics = ProjectContextProvider.getInstance().getFileDiagnostics( childFile.toUri() );
		assertNotNull( diagnostics );

		// Should not have any "not found" errors for ParentClass
		Diagnostic invalidExtends = diagnostics.stream()
		    .filter( d -> d.getMessage().contains( "ParentClass" ) && d.getMessage().toLowerCase().contains( "not found" ) )
		    .findFirst()
		    .orElse( null );

		assertThat( invalidExtends ).isNull();
	}

	// ============ Invalid Implements Tests ============

	@Test
	void testInvalidImplementsInterfaceNotFound() throws Exception {
		String	classCode	= """
		                      class implements="NonExistentInterface" {
		                          function init() { return this; }
		                      }
		                      """;

		Path	testFile	= createTestFile( "MyClass.bx", classCode );
		index.indexFile( testFile.toUri() );

		List<Diagnostic> diagnostics = ProjectContextProvider.getInstance().getFileDiagnostics( testFile.toUri() );
		assertNotNull( diagnostics );

		Diagnostic invalidImplements = diagnostics.stream()
		    .filter( d -> d.getMessage().contains( "NonExistentInterface" ) && d.getMessage().toLowerCase().contains( "not found" ) )
		    .findFirst()
		    .orElse( null );

		assertThat( invalidImplements ).isNotNull();
		assertThat( invalidImplements.getSeverity() ).isEqualTo( DiagnosticSeverity.Error );
	}

	@Test
	void testValidImplementsNoError() throws Exception {
		// First create the interface
		String	interfaceCode	= """
		                          interface {
		                              function getData();
		                          }
		                          """;
		Path	interfaceFile	= createTestFile( "MyInterface.bx", interfaceCode );
		index.indexFile( interfaceFile.toUri() );

		// Now create a class that implements it
		String	classCode	= """
		                      class implements="MyInterface" {
		                          function getData() { return "data"; }
		                      }
		                      """;
		Path	classFile	= createTestFile( "MyClass.bx", classCode );
		index.indexFile( classFile.toUri() );

		List<Diagnostic> diagnostics = ProjectContextProvider.getInstance().getFileDiagnostics( classFile.toUri() );
		assertNotNull( diagnostics );

		// Should not have any "not found" errors for MyInterface
		Diagnostic invalidImplements = diagnostics.stream()
		    .filter( d -> d.getMessage().contains( "MyInterface" ) && d.getMessage().toLowerCase().contains( "not found" ) )
		    .findFirst()
		    .orElse( null );

		assertThat( invalidImplements ).isNull();
	}

	@Test
	void testMultipleInvalidImplements() throws Exception {
		String	classCode	= """
		                      class implements="Interface1,Interface2,Interface3" {
		                          function init() { return this; }
		                      }
		                      """;

		Path	testFile	= createTestFile( "MultiImpl.bx", classCode );
		index.indexFile( testFile.toUri() );

		List<Diagnostic> diagnostics = ProjectContextProvider.getInstance().getFileDiagnostics( testFile.toUri() );
		assertNotNull( diagnostics );

		// Should have errors for all three non-existent interfaces
		long interfaceNotFoundCount = diagnostics.stream()
		    .filter( d -> d.getMessage().toLowerCase().contains( "not found" ) )
		    .count();

		assertThat( interfaceNotFoundCount ).isAtLeast( 3 );
	}

	// ============ Duplicate Method Definition Tests ============

	@Test
	void testDuplicateMethodDefinition() throws Exception {
		String	classCode	= """
		                      class {
		                          function myMethod() {
		                              return 1;
		                          }

		                          function myMethod() {
		                              return 2;
		                          }
		                      }
		                      """;

		Path	testFile	= createTestFile( "DuplicateMethods.bx", classCode );
		index.indexFile( testFile.toUri() );

		List<Diagnostic> diagnostics = ProjectContextProvider.getInstance().getFileDiagnostics( testFile.toUri() );
		assertNotNull( diagnostics );

		Diagnostic duplicateMethod = diagnostics.stream()
		    .filter( d -> d.getMessage().toLowerCase().contains( "duplicate" ) && d.getMessage().toLowerCase().contains( "method" ) )
		    .findFirst()
		    .orElse( null );

		assertThat( duplicateMethod ).isNotNull();
		assertThat( duplicateMethod.getSeverity() ).isEqualTo( DiagnosticSeverity.Error );
	}

	@Test
	void testNoDuplicateMethodsWithDifferentNames() throws Exception {
		String	classCode	= """
		                      class {
		                          function methodOne() {
		                              return 1;
		                          }

		                          function methodTwo() {
		                              return 2;
		                          }
		                      }
		                      """;

		Path	testFile	= createTestFile( "UniqueMethods.bx", classCode );
		index.indexFile( testFile.toUri() );

		List<Diagnostic> diagnostics = ProjectContextProvider.getInstance().getFileDiagnostics( testFile.toUri() );
		assertNotNull( diagnostics );

		Diagnostic duplicateMethod = diagnostics.stream()
		    .filter( d -> d.getMessage().toLowerCase().contains( "duplicate" ) && d.getMessage().toLowerCase().contains( "method" ) )
		    .findFirst()
		    .orElse( null );

		assertThat( duplicateMethod ).isNull();
	}

	// ============ Duplicate Property Definition Tests ============

	@Test
	void testDuplicatePropertyDefinition() throws Exception {
		String	classCode	= """
		                      class {
		                          property name="data" type="string";
		                          property name="data" type="numeric";
		                      }
		                      """;

		Path	testFile	= createTestFile( "DuplicateProperties.bx", classCode );
		index.indexFile( testFile.toUri() );

		List<Diagnostic> diagnostics = ProjectContextProvider.getInstance().getFileDiagnostics( testFile.toUri() );
		assertNotNull( diagnostics );

		Diagnostic duplicateProperty = diagnostics.stream()
		    .filter( d -> d.getMessage().toLowerCase().contains( "duplicate" ) && d.getMessage().toLowerCase().contains( "property" ) )
		    .findFirst()
		    .orElse( null );

		assertThat( duplicateProperty ).isNotNull();
		assertThat( duplicateProperty.getSeverity() ).isEqualTo( DiagnosticSeverity.Error );
	}

	@Test
	void testNoDuplicatePropertiesWithDifferentNames() throws Exception {
		String	classCode	= """
		                      class {
		                          property name="firstName" type="string";
		                          property name="lastName" type="string";
		                      }
		                      """;

		Path	testFile	= createTestFile( "UniqueProperties.bx", classCode );
		index.indexFile( testFile.toUri() );

		List<Diagnostic> diagnostics = ProjectContextProvider.getInstance().getFileDiagnostics( testFile.toUri() );
		assertNotNull( diagnostics );

		Diagnostic duplicateProperty = diagnostics.stream()
		    .filter( d -> d.getMessage().toLowerCase().contains( "duplicate" ) && d.getMessage().toLowerCase().contains( "property" ) )
		    .findFirst()
		    .orElse( null );

		assertThat( duplicateProperty ).isNull();
	}

	// ============ Helper Methods ============

	private Path createTestFile( String fileName, String content ) throws Exception {
		Path testFile = tempDir.resolve( fileName );
		Files.writeString( testFile, content );
		return testFile;
	}
}
