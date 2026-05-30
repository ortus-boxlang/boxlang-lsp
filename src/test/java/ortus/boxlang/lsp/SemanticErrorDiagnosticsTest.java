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
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.services.LanguageClient;
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
		    .filter( d -> d.getMessage().getLeft().contains( "NonExistentClass" ) && d.getMessage().getLeft().toLowerCase().contains( "not found" ) )
		    .findFirst()
		    .orElse( null );

		assertThat( invalidExtends ).isNotNull();
		assertThat( invalidExtends.getSeverity() ).isEqualTo( DiagnosticSeverity.Error );
	}

	@Test
	void testInvalidExtendsRangeOnlyCoversClassDeclaration() throws Exception {
		String	classCode	= """
		                      class extends="NonExistentClass" {
		                          function init() { return this; }
		                      }
		                      """;

		Path	testFile	= createTestFile( "RangeTest.bx", classCode );
		index.indexFile( testFile.toUri() );

		List<Diagnostic> diagnostics = ProjectContextProvider.getInstance().getFileDiagnostics( testFile.toUri() );
		assertNotNull( diagnostics );

		Diagnostic invalidExtends = diagnostics.stream()
		    .filter( d -> d.getMessage().getLeft().contains( "NonExistentClass" ) && d.getMessage().getLeft().toLowerCase().contains( "not found" ) )
		    .findFirst()
		    .orElse( null );

		assertThat( invalidExtends ).isNotNull();

		// Verify range only covers from "class" to the opening brace "{"
		// The range should be on line 0 (first line) and should not extend to line 2 (closing brace)
		assertThat( invalidExtends.getRange().getStart().getLine() ).isEqualTo( 0 );
		assertThat( invalidExtends.getRange().getEnd().getLine() ).isEqualTo( 0 );

		// The end character should be at or before the opening brace position
		// "class extends="NonExistentClass" {" - brace is at position 35
		assertThat( invalidExtends.getRange().getEnd().getCharacter() ).isAtMost( 36 );
	}

	@Test
	void testInvalidExtendsRangeOnlyCoversTemplateOpeningTag() throws Exception {
		ProjectContextProvider	provider	= ProjectContextProvider.getInstance();
		Path					projectRoot	= Path.of( "src/test/resources/test-bx-project" ).toAbsolutePath();
		Path					sourceFile	= Path.of( "src/test/resources/test-bx-project/InvalidExtendsTemplate.cfm" );
		assertThat( Files.exists( sourceFile ) ).isTrue();

		String sourceText = Files.readString( sourceFile );
		index.reinitialize( projectRoot, null );
		provider.setIndex( index );

		provider.trackDocumentOpen( sourceFile.toAbsolutePath().toUri(), sourceText );
		List<Diagnostic> diagnostics;
		try {
			diagnostics = provider.getFileDiagnostics( sourceFile.toAbsolutePath().toUri() );
			assertNotNull( diagnostics );

			Diagnostic invalidExtends = diagnostics.stream()
			    .filter( d -> d.getMessage().getLeft().contains( "waht" ) && d.getMessage().getLeft().toLowerCase().contains( "not found" ) )
			    .findFirst()
			    .orElse( null );

			assertThat( invalidExtends ).isNotNull();

			int openingTagEnd = sourceText.indexOf( '>' );
			assertThat( openingTagEnd ).isAtLeast( 0 );

			assertThat( invalidExtends.getRange().getStart().getLine() ).isEqualTo( 0 );
			assertThat( invalidExtends.getRange().getEnd().getLine() ).isEqualTo( 0 );
			assertThat( invalidExtends.getRange().getEnd().getCharacter() ).isAtMost( openingTagEnd + 1 );
		} finally {
			provider.trackDocumentClose( sourceFile.toAbsolutePath().toUri() );
		}
	}

	@Test
	void testPublishedDiagnosticsForInvalidExtendsTemplateStayOnOpeningTag() throws Exception {
		ProjectContextProvider	provider	= ProjectContextProvider.getInstance();
		Path					projectRoot	= Path.of( "src/test/resources/test-bx-project" ).toAbsolutePath();
		Path					sourceFile	= Path.of( "src/test/resources/test-bx-project/InvalidExtendsTemplate.cfm" );
		RecordingLanguageClient	client		= new RecordingLanguageClient();
		assertThat( Files.exists( sourceFile ) ).isTrue();

		String sourceText = Files.readString( sourceFile );
		index.reinitialize( projectRoot, null );
		provider.setIndex( index );
		provider.setLanguageClient( client );

		provider.trackDocumentOpen( sourceFile.toAbsolutePath().toUri(), sourceText );
		try {
			provider.flushPublishDebouncer();
			PublishDiagnosticsParams params = client.awaitPublished( sourceFile.toAbsolutePath().toUri().toString() );
			assertThat( params.getDiagnostics() ).isNotEmpty();

			for ( Diagnostic diagnostic : params.getDiagnostics() ) {
				assertThat( diagnostic.getRange().getEnd().getLine() ).isEqualTo( 0 );
			}
		} finally {
			provider.trackDocumentClose( sourceFile.toAbsolutePath().toUri() );
			provider.setLanguageClient( null );
		}
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
		    .filter( d -> d.getMessage().getLeft().contains( "ParentClass" ) && d.getMessage().getLeft().toLowerCase().contains( "not found" ) )
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
		    .filter( d -> d.getMessage().getLeft().contains( "NonExistentInterface" ) && d.getMessage().getLeft().toLowerCase().contains( "not found" ) )
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
		    .filter( d -> d.getMessage().getLeft().contains( "MyInterface" ) && d.getMessage().getLeft().toLowerCase().contains( "not found" ) )
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
		    .filter( d -> d.getMessage().getLeft().toLowerCase().contains( "not found" ) )
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
		    .filter( d -> d.getMessage().getLeft().toLowerCase().contains( "duplicate" ) && d.getMessage().getLeft().toLowerCase().contains( "method" ) )
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
		    .filter( d -> d.getMessage().getLeft().toLowerCase().contains( "duplicate" ) && d.getMessage().getLeft().toLowerCase().contains( "method" ) )
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
		    .filter( d -> d.getMessage().getLeft().toLowerCase().contains( "duplicate" ) && d.getMessage().getLeft().toLowerCase().contains( "property" ) )
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
		    .filter( d -> d.getMessage().getLeft().toLowerCase().contains( "duplicate" ) && d.getMessage().getLeft().toLowerCase().contains( "property" ) )
		    .findFirst()
		    .orElse( null );

		assertThat( duplicateProperty ).isNull();
	}

	// ============ SuppressWarnings Tests ============

	@Test
	void testSuppressWarningsInvalidExtendsTypedSuppression() throws Exception {
		String	classCode	= """
		                      @SuppressWarnings( invalidExtends )
		                      class extends="NonExistentClass" {
		                          function init() { return this; }
		                      }
		                      """;

		Path	testFile	= createTestFile( "SuppressedExtends.bx", classCode );
		index.indexFile( testFile.toUri() );

		List<Diagnostic> diagnostics = ProjectContextProvider.getInstance().getFileDiagnostics( testFile.toUri() );
		assertNotNull( diagnostics );

		Diagnostic invalidExtends = diagnostics.stream()
		    .filter( d -> d.getMessage().getLeft().contains( "NonExistentClass" ) && d.getMessage().getLeft().toLowerCase().contains( "not found" ) )
		    .findFirst()
		    .orElse( null );

		assertThat( invalidExtends ).isNull();
	}

	@Test
	void testSuppressWarningsOtherRuleDoesNotSuppressInvalidExtends() throws Exception {
		String	classCode	= """
		                      @SuppressWarnings( unusedVariable )
		                      class extends="NonExistentClass" {
		                          function init() { return this; }
		                      }
		                      """;

		Path	testFile	= createTestFile( "WrongSuppressedExtends.bx", classCode );
		index.indexFile( testFile.toUri() );

		List<Diagnostic> diagnostics = ProjectContextProvider.getInstance().getFileDiagnostics( testFile.toUri() );
		assertNotNull( diagnostics );

		Diagnostic invalidExtends = diagnostics.stream()
		    .filter( d -> d.getMessage().getLeft().contains( "NonExistentClass" ) && d.getMessage().getLeft().toLowerCase().contains( "not found" ) )
		    .findFirst()
		    .orElse( null );

		assertThat( invalidExtends ).isNotNull();
	}

	// ============ Helper Methods ============

	private Path createTestFile( String fileName, String content ) throws Exception {
		Path testFile = tempDir.resolve( fileName );
		Files.writeString( testFile, content );
		return testFile;
	}

	private static class RecordingLanguageClient implements LanguageClient {

		private final java.util.concurrent.ConcurrentHashMap<String, PublishDiagnosticsParams> publishedDiagnostics = new java.util.concurrent.ConcurrentHashMap<>();

		@Override
		public void telemetryEvent( Object object ) {
		}

		@Override
		public void publishDiagnostics( PublishDiagnosticsParams diagnostics ) {
			publishedDiagnostics.put( diagnostics.getUri(), diagnostics );
		}

		@Override
		public void showMessage( MessageParams messageParams ) {
		}

		@Override
		public CompletableFuture<MessageActionItem> showMessageRequest( ShowMessageRequestParams requestParams ) {
			return CompletableFuture.completedFuture( null );
		}

		@Override
		public void logMessage( MessageParams message ) {
		}

		private PublishDiagnosticsParams awaitPublished( String uri ) throws InterruptedException {
			for ( int attempt = 0; attempt < 40; attempt++ ) {
				PublishDiagnosticsParams params = publishedDiagnostics.get( uri );
				if ( params != null ) {
					return params;
				}
				Thread.sleep( 50 );
			}
			throw new AssertionError( "Timed out waiting for diagnostics publish for " + uri );
		}
	}
}
