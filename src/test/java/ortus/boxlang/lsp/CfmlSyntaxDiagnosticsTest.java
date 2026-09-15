package ortus.boxlang.lsp;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ortus.boxlang.lsp.workspace.FileParseResult;

public class CfmlSyntaxDiagnosticsTest extends BaseTest {

	@TempDir
	Path directory;

	static Stream<Arguments> cfmlCases() {
		return Stream.of(
		    Arguments.of( "template.cfm", "<cfoutput>#ucase(\"ok\")#</cfoutput>", false ),
		    Arguments.of( "script.cfm", "<cfscript>value = len(\"ok\");</cfscript>", false ),
		    Arguments.of( "component.cfc", "component { public string function greet(required string name) { return \"Hi \" & arguments.name; } }", false ),
		    Arguments.of( "tag-component.cfc",
		        "<cfcomponent><cffunction name=\"greet\" returntype=\"string\"><cfargument name=\"name\" type=\"string\" required=\"true\"><cfreturn arguments.name></cffunction></cfcomponent>",
		        false ),
		    Arguments.of( "comments.cfm", "<!--- <cfif> ([) ---><cfset value = \"([)\">", false ),
		    Arguments.of( "empty.cfm", "", false ),
		    Arguments.of( "whitespace.cfm", "  \n\t", false ),
		    Arguments.of( "comment-only.cfm", "<!--- <cfif true> --->", false ),
		    Arguments.of( "nested-comment.cfm", "<!--- outer <!--- <cfif true> ---> end --->", false ),
		    Arguments.of( "trailing-comment.cfm", "<cfset value = 1><!--- done --->", false ),
		    Arguments.of( "text-and-tags.cfm", "hello\n<cfset value = 1>world", false ),
		    Arguments.of( "unclosed-after-text.cfm", "hello\n<cfif true><cfset value = 1>", true ),
		    Arguments.of( "unclosed-tag.cfm", "<cfif true><cfset value = 1>", true ),
		    Arguments.of( "mismatched-tags.cfm", "<cfoutput>hello</cfif>", true ),
		    Arguments.of( "missing-paren.cfm", "<cfscript>value = len(\"ok\";</cfscript>", true ),
		    Arguments.of( "missing-brace.cfc", "component { function greet() { return \"ok\"; }", true ),
		    Arguments.of( "missing-expression.cfm", "<cfset value = >", true ),
		    Arguments.of( "bad-expression.cfm", "<cfscript>value = 1 + ;</cfscript>", true )
		);
	}

	@ParameterizedTest
	@MethodSource( "cfmlCases" )
	void openDocumentsReportParserErrors( String name, String source, boolean expectError ) throws Exception {
		Files.writeString( directory.resolve( name ), source );
		var result = FileParseResult.fromSourceString( directory.resolve( name ).toUri(), source );
		assertEquals( expectError, result.getDiagnostics().stream().anyMatch( d -> d.getSeverity() == DiagnosticSeverity.Error ),
		    () -> "Unexpected diagnostics: " + result.getDiagnostics() );
		if ( expectError ) {
			assertFalse( result.getIssues().isEmpty() );
			assertTrue( result.getDiagnostics().stream().allMatch( d -> d.getRange() != null && !d.getMessage().isBlank() ) );
		}
	}

	@Test
	void reparsingCorrectedFileClearsPreviousErrors() throws Exception {
		Path file = directory.resolve( "changing.cfm" );
		Files.writeString( file, "<cfset value = >" );
		var result = FileParseResult.fromFileSystem( file.toUri() );
		assertFalse( result.getDiagnostics().isEmpty() );
		Files.writeString( file, "<cfset value = 1>" );
		result.reparse();
		assertTrue( result.getIssues().isEmpty() );
		assertTrue( result.getDiagnostics().isEmpty() );
	}
}
