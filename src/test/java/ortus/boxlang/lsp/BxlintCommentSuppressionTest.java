package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertWithMessage;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ortus.boxlang.lsp.workspace.ProjectContextProvider;
import ortus.boxlang.lsp.workspace.index.ProjectIndex;
import ortus.boxlang.runtime.BoxRuntime;

public class BxlintCommentSuppressionTest extends BaseTest {

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
		ProjectContextProvider.getInstance().setIndex( index );
	}

	@Test
	void testBxlintDisableAndEnableSuppressesAllRulesOnlyWithinSection() throws Exception {
		String	code		= """
		                      class {
		                          function demo() {
		                              // bxlint:disable
		                              suppressed = 1;
		                              var ignored = 2;
		                              // bxlint:enable

		                              unsuppressed = 3;
		                              var reported = 4;
		                          }
		                      }
		                      """;

		Path	testFile	= createTestFile( "BxlintDisableEnableSection.bx", code );
		index.indexFile( testFile.toUri() );

		List<Diagnostic> diagnostics = ProjectContextProvider.getInstance().getFileDiagnostics( testFile.toUri() );
		assertNotNull( diagnostics );

		assertWithMessage( diagnosticSummary( diagnostics ) ).that( hasDiagnostic( diagnostics, "unusedVariable", 3 ) ).isFalse();
		assertWithMessage( diagnosticSummary( diagnostics ) ).that( hasDiagnostic( diagnostics, "unusedVariable", 4 ) ).isFalse();
		assertWithMessage( diagnosticSummary( diagnostics ) ).that( hasDiagnostic( diagnostics, "unusedVariable", 7 ) ).isTrue();
		assertWithMessage( diagnosticSummary( diagnostics ) ).that( hasDiagnostic( diagnostics, "unusedVariable", 8 ) ).isTrue();
	}

	@Test
	void testBxlintDisableForFunctionSuppressesOnlyTheNextFunction() throws Exception {
		String	code		= """
		                      class {
		                          // bxlint-disable-for-function unusedVariable
		                          function first() {
		                              suppressed = 1;
		                          }

		                          function second() {
		                              reported = 2;
		                          }
		                      }
		                      """;

		Path	testFile	= createTestFile( "BxlintDisableForFunction.bx", code );
		index.indexFile( testFile.toUri() );

		List<Diagnostic> diagnostics = ProjectContextProvider.getInstance().getFileDiagnostics( testFile.toUri() );
		assertNotNull( diagnostics );

		assertWithMessage( diagnosticSummary( diagnostics ) ).that( hasDiagnostic( diagnostics, "unusedVariable", 3 ) ).isFalse();
		assertWithMessage( diagnosticSummary( diagnostics ) ).that( hasDiagnostic( diagnostics, "unusedVariable", 7 ) ).isTrue();
	}

	@Test
	void testBxlintEnableForFunctionReenablesTheNextFunctionInsideDisabledSection() throws Exception {
		String	code		= """
		                      class {
		                          // bxlint-disable unusedVariable
		                          // bxlint-enable-for-function unusedVariable
		                          function first() {
		                              restored = 1;
		                          }

		                          function second() {
		                              stillSuppressed = 2;
		                          }
		                      }
		                      """;

		Path	testFile	= createTestFile( "BxlintEnableForFunction.bx", code );
		index.indexFile( testFile.toUri() );

		List<Diagnostic> diagnostics = ProjectContextProvider.getInstance().getFileDiagnostics( testFile.toUri() );
		assertNotNull( diagnostics );

		assertWithMessage( diagnosticSummary( diagnostics ) ).that( hasDiagnostic( diagnostics, "unusedVariable", 4 ) ).isTrue();
		assertWithMessage( diagnosticSummary( diagnostics ) ).that( hasDiagnostic( diagnostics, "unusedVariable", 8 ) ).isFalse();
	}

	@Test
	void testBxlintDisableAndEnableSpecificRulesSuppressesOnlyMatchingRules() throws Exception {
		String	code		= """
		                      class {
		                          function demo() {
		                              // bxlint-disable emptyCatchBlock
		                              suppressed = 1;
		                              try {
		                                  foo();
		                              } catch (any e) {
		                              }
		                              // bxlint-enable emptyCatchBlock

		                              try {
		                                  foo();
		                              } catch (any e) {
		                              }
		                          }
		                      }
		                      """;

		Path	testFile	= createTestFile( "BxlintDisableSpecificRules.bx", code );
		index.indexFile( testFile.toUri() );

		List<Diagnostic> diagnostics = ProjectContextProvider.getInstance().getFileDiagnostics( testFile.toUri() );
		assertNotNull( diagnostics );

		assertWithMessage( diagnosticSummary( diagnostics ) ).that( hasDiagnostic( diagnostics, "unusedVariable", 3 ) ).isTrue();
		assertWithMessage( diagnosticSummary( diagnostics ) ).that( hasDiagnostic( diagnostics, "emptyCatchBlock", 6 ) ).isFalse();
		assertWithMessage( diagnosticSummary( diagnostics ) ).that( hasDiagnostic( diagnostics, "emptyCatchBlock", 12 ) ).isTrue();
	}

	@Test
	void testBxlintDisableForClassSuppressesOnlyTheNextClass() throws Exception {
		String	suppressedCode	= """
		                          // bxlint-disable-for-class unusedVariable
		                          class First {
		                              function demo() {
		                                  suppressed = 1;
		                              }
		                          }
		                          """;
		String	controlCode		= """
		                          class Second {
		                              function demo() {
		                                  reported = 2;
		                              }
		                          }
		                          """;

		Path	suppressedFile	= createTestFile( "BxlintDisableForClass.bx", suppressedCode );
		Path	controlFile		= createTestFile( "BxlintDisableForClassControl.bx", controlCode );
		index.indexFile( suppressedFile.toUri() );
		index.indexFile( controlFile.toUri() );

		List<Diagnostic>	suppressedDiagnostics	= ProjectContextProvider.getInstance().getFileDiagnostics( suppressedFile.toUri() );
		List<Diagnostic>	controlDiagnostics		= ProjectContextProvider.getInstance().getFileDiagnostics( controlFile.toUri() );
		assertNotNull( suppressedDiagnostics );
		assertNotNull( controlDiagnostics );

		assertWithMessage( diagnosticSummary( suppressedDiagnostics ) ).that( hasDiagnostic( suppressedDiagnostics, "unusedVariable", 3 ) ).isFalse();
		assertWithMessage( diagnosticSummary( controlDiagnostics ) ).that( hasDiagnostic( controlDiagnostics, "unusedVariable", 2 ) ).isTrue();
	}

	@Test
	void testBxlintEnableForClassReenablesTheNextClassInsideDisabledSection() throws Exception {
		String	code		= """
		                      // bxlint-disable unusedVariable
		                      // bxlint-enable-for-class unusedVariable
		                      class First {
		                          function demo() {
		                              restored = 1;
		                          }
		                      }
		                      """;

		Path	testFile	= createTestFile( "BxlintEnableForClass.bx", code );
		index.indexFile( testFile.toUri() );

		List<Diagnostic> diagnostics = ProjectContextProvider.getInstance().getFileDiagnostics( testFile.toUri() );
		assertNotNull( diagnostics );

		assertWithMessage( diagnosticSummary( diagnostics ) ).that( hasDiagnostic( diagnostics, "unusedVariable", 4 ) ).isTrue();
	}

	private boolean hasDiagnostic( List<Diagnostic> diagnostics, String ruleId, int line ) {
		return diagnostics.stream().anyMatch( diagnostic -> diagnostic.getCode() != null
		    && diagnostic.getCode().isLeft()
		    && ruleId.equals( diagnostic.getCode().getLeft() )
		    && diagnostic.getRange() != null
		    && diagnostic.getRange().getStart() != null
		    && diagnostic.getRange().getStart().getLine() == line );
	}

	private String diagnosticSummary( List<Diagnostic> diagnostics ) {
		return diagnostics.stream()
		    .map( diagnostic -> {
			    String code	= diagnostic.getCode() != null && diagnostic.getCode().isLeft() ? diagnostic.getCode().getLeft() : "<none>";
			    int	line	= diagnostic.getRange() != null && diagnostic.getRange().getStart() != null ? diagnostic.getRange().getStart().getLine() : -1;
			    return code + "@" + line;
		    } )
		    .reduce( ( left, right ) -> left + ", " + right )
		    .orElse( "<no diagnostics>" );
	}

	private Path createTestFile( String fileName, String content ) throws Exception {
		Path testFile = tempDir.resolve( fileName );
		Files.writeString( testFile, content );
		return testFile;
	}
}