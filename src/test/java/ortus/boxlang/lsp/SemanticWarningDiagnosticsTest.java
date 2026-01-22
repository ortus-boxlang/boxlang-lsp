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
import org.eclipse.lsp4j.DiagnosticTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ortus.boxlang.lsp.workspace.ProjectContextProvider;
import ortus.boxlang.lsp.workspace.index.ProjectIndex;
import ortus.boxlang.runtime.BoxRuntime;

/**
 * Tests for semantic warning diagnostics (Task 1.6).
 *
 * Tests detection of:
 * - Unused private methods
 * - Unused imports
 * - Empty catch blocks
 * - Unreachable code after return/throw/break/continue
 * - Shadowed variables
 * - Missing return statement when return type hint is present
 */
public class SemanticWarningDiagnosticsTest extends BaseTest {

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

	// ============ Empty Catch Block Tests ============

	@Test
	void testEmptyCatchBlock() throws Exception {
		String code = """
		              class {
		                  function doSomething() {
		                      try {
		                          var x = 1;
		                      } catch (any e) {
		                      }
		                  }
		              }
		              """;

		Path testFile = createTestFile( "EmptyCatch.bx", code );
		index.indexFile( testFile.toUri() );

		List<Diagnostic> diagnostics = ProjectContextProvider.getInstance().getFileDiagnostics( testFile.toUri() );
		assertNotNull( diagnostics );

		Diagnostic emptyCatch = diagnostics.stream()
		    .filter( d -> d.getMessage().toLowerCase().contains( "empty catch" ) )
		    .findFirst()
		    .orElse( null );

		assertThat( emptyCatch ).isNotNull();
		assertThat( emptyCatch.getSeverity() ).isEqualTo( DiagnosticSeverity.Warning );
	}

	@Test
	void testNonEmptyCatchBlockNoWarning() throws Exception {
		String code = """
		              class {
		                  function doSomething() {
		                      try {
		                          var x = 1;
		                      } catch (any e) {
		                          log(e.message);
		                      }
		                  }
		              }
		              """;

		Path testFile = createTestFile( "NonEmptyCatch.bx", code );
		index.indexFile( testFile.toUri() );

		List<Diagnostic> diagnostics = ProjectContextProvider.getInstance().getFileDiagnostics( testFile.toUri() );
		assertNotNull( diagnostics );

		Diagnostic emptyCatch = diagnostics.stream()
		    .filter( d -> d.getMessage().toLowerCase().contains( "empty catch" ) )
		    .findFirst()
		    .orElse( null );

		assertThat( emptyCatch ).isNull();
	}

	// ============ Unreachable Code Tests ============

	@Test
	void testUnreachableCodeAfterReturn() throws Exception {
		String code = """
		              class {
		                  function calculate() {
		                      return 42;
		                      var x = 1;
		                  }
		              }
		              """;

		Path testFile = createTestFile( "UnreachableReturn.bx", code );
		index.indexFile( testFile.toUri() );

		List<Diagnostic> diagnostics = ProjectContextProvider.getInstance().getFileDiagnostics( testFile.toUri() );
		assertNotNull( diagnostics );

		Diagnostic unreachable = diagnostics.stream()
		    .filter( d -> d.getMessage().toLowerCase().contains( "unreachable" ) )
		    .findFirst()
		    .orElse( null );

		assertThat( unreachable ).isNotNull();
		assertThat( unreachable.getSeverity() ).isEqualTo( DiagnosticSeverity.Warning );
		assertThat( unreachable.getTags() ).contains( DiagnosticTag.Unnecessary );
	}

	@Test
	void testUnreachableCodeAfterThrow() throws Exception {
		String code = """
		              class {
		                  function validate() {
		                      throw "Invalid input";
		                      return false;
		                  }
		              }
		              """;

		Path testFile = createTestFile( "UnreachableThrow.bx", code );
		index.indexFile( testFile.toUri() );

		List<Diagnostic> diagnostics = ProjectContextProvider.getInstance().getFileDiagnostics( testFile.toUri() );
		assertNotNull( diagnostics );

		Diagnostic unreachable = diagnostics.stream()
		    .filter( d -> d.getMessage().toLowerCase().contains( "unreachable" ) )
		    .findFirst()
		    .orElse( null );

		assertThat( unreachable ).isNotNull();
	}

	@Test
	void testUnreachableCodeAfterBreak() throws Exception {
		String code = """
		              class {
		                  function search() {
		                      for (var i = 0; i < 10; i++) {
		                          if (i == 5) {
		                              break;
		                              var found = true;
		                          }
		                      }
		                  }
		              }
		              """;

		Path testFile = createTestFile( "UnreachableBreak.bx", code );
		index.indexFile( testFile.toUri() );

		List<Diagnostic> diagnostics = ProjectContextProvider.getInstance().getFileDiagnostics( testFile.toUri() );
		assertNotNull( diagnostics );

		Diagnostic unreachable = diagnostics.stream()
		    .filter( d -> d.getMessage().toLowerCase().contains( "unreachable" ) )
		    .findFirst()
		    .orElse( null );

		assertThat( unreachable ).isNotNull();
	}

	@Test
	void testUnreachableCodeAfterContinue() throws Exception {
		String code = """
		              class {
		                  function process() {
		                      for (var i = 0; i < 10; i++) {
		                          if (i == 5) {
		                              continue;
		                              var skipped = true;
		                          }
		                      }
		                  }
		              }
		              """;

		Path testFile = createTestFile( "UnreachableContinue.bx", code );
		index.indexFile( testFile.toUri() );

		List<Diagnostic> diagnostics = ProjectContextProvider.getInstance().getFileDiagnostics( testFile.toUri() );
		assertNotNull( diagnostics );

		Diagnostic unreachable = diagnostics.stream()
		    .filter( d -> d.getMessage().toLowerCase().contains( "unreachable" ) )
		    .findFirst()
		    .orElse( null );

		assertThat( unreachable ).isNotNull();
	}

	@Test
	void testNoUnreachableCodeWarningForConditionalReturn() throws Exception {
		String code = """
		              class {
		                  function calculate(required numeric value) {
		                      if (value > 0) {
		                          return value;
		                      }
		                      return 0;
		                  }
		              }
		              """;

		Path testFile = createTestFile( "ConditionalReturn.bx", code );
		index.indexFile( testFile.toUri() );

		List<Diagnostic> diagnostics = ProjectContextProvider.getInstance().getFileDiagnostics( testFile.toUri() );
		assertNotNull( diagnostics );

		Diagnostic unreachable = diagnostics.stream()
		    .filter( d -> d.getMessage().toLowerCase().contains( "unreachable" ) )
		    .findFirst()
		    .orElse( null );

		assertThat( unreachable ).isNull();
	}

	// ============ Shadowed Variable Tests ============

	@Test
	void testShadowedVariableLocalShadowsParameter() throws Exception {
		String code = """
		              class {
		                  function process(required string name) {
		                      var name = "different";
		                      return name;
		                  }
		              }
		              """;

		Path testFile = createTestFile( "ShadowedParam.bx", code );
		index.indexFile( testFile.toUri() );

		List<Diagnostic> diagnostics = ProjectContextProvider.getInstance().getFileDiagnostics( testFile.toUri() );
		assertNotNull( diagnostics );

		Diagnostic shadowed = diagnostics.stream()
		    .filter( d -> d.getMessage().toLowerCase().contains( "shadow" ) )
		    .findFirst()
		    .orElse( null );

		assertThat( shadowed ).isNotNull();
		assertThat( shadowed.getSeverity() ).isEqualTo( DiagnosticSeverity.Warning );
	}

	@Test
	void testNoShadowWarningForDifferentNames() throws Exception {
		String code = """
		              class {
		                  function process(required string firstName) {
		                      var lastName = "Smith";
		                      return firstName & " " & lastName;
		                  }
		              }
		              """;

		Path testFile = createTestFile( "NoShadow.bx", code );
		index.indexFile( testFile.toUri() );

		List<Diagnostic> diagnostics = ProjectContextProvider.getInstance().getFileDiagnostics( testFile.toUri() );
		assertNotNull( diagnostics );

		Diagnostic shadowed = diagnostics.stream()
		    .filter( d -> d.getMessage().toLowerCase().contains( "shadow" ) )
		    .findFirst()
		    .orElse( null );

		assertThat( shadowed ).isNull();
	}

	// ============ Missing Return Statement Tests ============

	@Test
	void testMissingReturnWithReturnTypeHint() throws Exception {
		String code = """
		              class {
		                  string function getName() {
		                      var name = "test";
		                  }
		              }
		              """;

		Path testFile = createTestFile( "MissingReturn.bx", code );
		index.indexFile( testFile.toUri() );

		List<Diagnostic> diagnostics = ProjectContextProvider.getInstance().getFileDiagnostics( testFile.toUri() );
		assertNotNull( diagnostics );

		Diagnostic missingReturn = diagnostics.stream()
		    .filter( d -> d.getMessage().toLowerCase().contains( "may not return a value" ) ||
		        d.getMessage().toLowerCase().contains( "missing return" ) ||
		        d.getMessage().toLowerCase().contains( "return statement" ) )
		    .findFirst()
		    .orElse( null );

		assertThat( missingReturn ).isNotNull();
		assertThat( missingReturn.getSeverity() ).isEqualTo( DiagnosticSeverity.Warning );
	}

	@Test
	void testNoMissingReturnWarningForVoidFunction() throws Exception {
		String code = """
		              class {
		                  void function doSomething() {
		                      var x = 1;
		                  }
		              }
		              """;

		Path testFile = createTestFile( "VoidFunction.bx", code );
		index.indexFile( testFile.toUri() );

		List<Diagnostic> diagnostics = ProjectContextProvider.getInstance().getFileDiagnostics( testFile.toUri() );
		assertNotNull( diagnostics );

		Diagnostic missingReturn = diagnostics.stream()
		    .filter( d -> d.getMessage().toLowerCase().contains( "missing return" ) )
		    .findFirst()
		    .orElse( null );

		assertThat( missingReturn ).isNull();
	}

	@Test
	void testNoMissingReturnWarningWhenReturnExists() throws Exception {
		String code = """
		              class {
		                  string function getName() {
		                      return "test";
		                  }
		              }
		              """;

		Path testFile = createTestFile( "HasReturn.bx", code );
		index.indexFile( testFile.toUri() );

		List<Diagnostic> diagnostics = ProjectContextProvider.getInstance().getFileDiagnostics( testFile.toUri() );
		assertNotNull( diagnostics );

		Diagnostic missingReturn = diagnostics.stream()
		    .filter( d -> d.getMessage().toLowerCase().contains( "missing return" ) )
		    .findFirst()
		    .orElse( null );

		assertThat( missingReturn ).isNull();
	}

	// ============ Unused Private Method Tests ============

	@Test
	void testUnusedPrivateMethod() throws Exception {
		String code = """
		              class {
		                  public function publicMethod() {
		                      return;
		                  }

		                  private function unusedHelper() {
		                      return;
		                  }
		              }
		              """;

		Path testFile = createTestFile( "UnusedPrivate.bx", code );
		index.indexFile( testFile.toUri() );

		List<Diagnostic> diagnostics = ProjectContextProvider.getInstance().getFileDiagnostics( testFile.toUri() );
		assertNotNull( diagnostics );

		Diagnostic unusedPrivate = diagnostics.stream()
		    .filter( d -> d.getMessage().toLowerCase().contains( "private" ) && d.getMessage().toLowerCase().contains( "never" ) )
		    .findFirst()
		    .orElse( null );

		assertThat( unusedPrivate ).isNotNull();
		assertThat( unusedPrivate.getSeverity() ).isEqualTo( DiagnosticSeverity.Warning );
		assertThat( unusedPrivate.getTags() ).contains( DiagnosticTag.Unnecessary );
	}

	@Test
	void testUsedPrivateMethodNoWarning() throws Exception {
		String code = """
		              class {
		                  public function publicMethod() {
		                      helperMethod();
		                  }

		                  private function helperMethod() {
		                      return;
		                  }
		              }
		              """;

		Path testFile = createTestFile( "UsedPrivate.bx", code );
		index.indexFile( testFile.toUri() );

		List<Diagnostic> diagnostics = ProjectContextProvider.getInstance().getFileDiagnostics( testFile.toUri() );
		assertNotNull( diagnostics );

		Diagnostic unusedPrivate = diagnostics.stream()
		    .filter( d -> d.getMessage().toLowerCase().contains( "helpermethod" ) && d.getMessage().toLowerCase().contains( "never" ) )
		    .findFirst()
		    .orElse( null );

		assertThat( unusedPrivate ).isNull();
	}

	// ============ Unused Import Tests ============

	@Test
	void testUnusedImport() throws Exception {
		String code = """
		              import java:java.util.ArrayList;

		              class {
		                  function void doSomething() {
		                      var x = 1;
		                  }
		              }
		              """;

		Path testFile = createTestFile( "UnusedImport.bx", code );
		index.indexFile( testFile.toUri() );

		List<Diagnostic> diagnostics = ProjectContextProvider.getInstance().getFileDiagnostics( testFile.toUri() );
		assertNotNull( diagnostics );

		Diagnostic unusedImport = diagnostics.stream()
		    .filter( d -> d.getMessage().toLowerCase().contains( "import" ) && d.getMessage().toLowerCase().contains( "never used" ) )
		    .findFirst()
		    .orElse( null );

		assertThat( unusedImport ).isNotNull();
		assertThat( unusedImport.getSeverity() ).isEqualTo( DiagnosticSeverity.Warning );
		assertThat( unusedImport.getTags() ).contains( DiagnosticTag.Unnecessary );
	}

	@Test
	void testUsedImportNoWarning() throws Exception {
		String code = """
		              import java:java.util.ArrayList;

		              class {
		                  function void doSomething() {
		                      var list = new ArrayList();
		                  }
		              }
		              """;

		Path testFile = createTestFile( "UsedImport.bx", code );
		index.indexFile( testFile.toUri() );

		List<Diagnostic> diagnostics = ProjectContextProvider.getInstance().getFileDiagnostics( testFile.toUri() );
		assertNotNull( diagnostics );

		Diagnostic unusedImport = diagnostics.stream()
		    .filter( d -> d.getMessage().toLowerCase().contains( "arraylist" ) && d.getMessage().toLowerCase().contains( "never used" ) )
		    .findFirst()
		    .orElse( null );

		assertThat( unusedImport ).isNull();
	}

	// ============ Helper Methods ============

	private Path createTestFile( String fileName, String content ) throws Exception {
		Path testFile = tempDir.resolve( fileName );
		Files.writeString( testFile, content );
		return testFile;
	}
}
