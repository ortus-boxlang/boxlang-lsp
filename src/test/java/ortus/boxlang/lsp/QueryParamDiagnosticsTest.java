package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ortus.boxlang.lsp.lint.LintConfigLoader;
import ortus.boxlang.lsp.workspace.ProjectContextProvider;

public class QueryParamDiagnosticsTest extends BaseTest {

	@TempDir
	Path tempDir;

	@Test
	void testPublishesDiagnosticsForUnescapedQueryParams() {
		ProjectContextProvider	provider	= ProjectContextProvider.getInstance();
		Path					projectRoot	= Paths.get( System.getProperty( "user.dir" ) );
		Path					path		= projectRoot.resolve( "src/test/resources/files/queryParam/unescapedQueryParam.cfm" );
		File					file		= path.toFile();
		assertTrue( file.exists(), "Test file does not exist: " + path );

		List<Diagnostic> diagnostics = provider.getFileDiagnostics( file.toURI() );

		assertThat( diagnostics ).isNotEmpty();
		assertThat( diagnostics.stream().anyMatch( diagnostic -> diagnostic.getCode() != null
		    && "unescapedQueryParam".equals( diagnostic.getCode().getLeft() ) ) ).isTrue();
		assertThat( diagnostics.stream().anyMatch( diagnostic -> diagnostic.getMessage().equals( "Possible unescaped query param: #paramRef#" ) ) )
		    .isTrue();
	}

	@Test
	void testPublishesCodeActionsForUnescapedQueryParams() {
		ProjectContextProvider	provider	= ProjectContextProvider.getInstance();
		Path					projectRoot	= Paths.get( System.getProperty( "user.dir" ) );
		Path					path		= projectRoot.resolve( "src/test/resources/files/queryParam/unescapedQueryParam.cfm" );
		File					file		= path.toFile();
		assertTrue( file.exists(), "Test file does not exist: " + path );

		List<CodeAction> codeActions = provider.getFileCodeActions( file.toURI() );

		assertThat( codeActions ).isNotEmpty();
		assertThat( codeActions.stream().anyMatch( codeAction -> codeAction.getEdit() != null
		    && codeAction.getEdit().getChanges() != null
		    && codeAction.getEdit().getChanges().containsKey( file.toURI().toString() )
		    && codeAction.getEdit().getChanges().get( file.toURI().toString() ).stream()
		        .anyMatch( edit -> edit.getNewText().equals( "<cfqueryparam value=\"#paramRef#\">" ) ) ) )
		            .isTrue();
	}

	@Test
	void testDisablesUnescapedQueryParamRuleViaBxlint() throws Exception {
		ProjectContextProvider	provider		= ProjectContextProvider.getInstance();
		List<WorkspaceFolder>	savedFolders	= provider.getWorkspaceFolders();

		Path					workspaceRoot	= Files.createDirectories( tempDir.resolve( "query-lint-disable" ) );
		Path					documentPath	= workspaceRoot.resolve( "query.cfm" );
		Path					lintConfig		= workspaceRoot.resolve( ".bxlint.json" );
		WorkspaceFolder			folder			= new WorkspaceFolder();

		Files.writeString( documentPath, """
		                                 <cfquery>
		                                 	SELECT * FROM items u
		                                 	WHERE u.code = '#paramRef#'
		                                 </cfquery>
		                                 """ );
		Files.writeString( lintConfig, """
		                               {
		                                 "diagnostics": {
		                                   "unescapedQueryParam": {
		                                     "enabled": false
		                                   }
		                                 }
		                               }
		                               """ );
		folder.setUri( workspaceRoot.toUri().toString() );

		try {
			provider.setWorkspaceFolders( List.of( folder ) );
			LintConfigLoader.invalidate();

			List<Diagnostic>	diagnostics	= provider.getFileDiagnostics( documentPath.toUri() );
			List<CodeAction>	codeActions	= provider.getFileCodeActions( documentPath.toUri() );

			assertThat( diagnostics.stream().anyMatch( diagnostic -> diagnostic.getCode() != null
			    && "unescapedQueryParam".equals( diagnostic.getCode().getLeft() ) ) ).isFalse();
			assertThat( codeActions ).isEmpty();
		} finally {
			provider.remove( documentPath.toUri() );
			provider.setWorkspaceFolders( savedFolders );
			LintConfigLoader.invalidate();
		}
	}

	@Test
	void testAppliesSeverityOverrideForMissingQueryParamCfsqltypeRule() throws Exception {
		ProjectContextProvider	provider		= ProjectContextProvider.getInstance();
		List<WorkspaceFolder>	savedFolders	= provider.getWorkspaceFolders();

		Path					workspaceRoot	= Files.createDirectories( tempDir.resolve( "query-lint-severity" ) );
		Path					documentPath	= workspaceRoot.resolve( "missing-type.cfm" );
		Path					lintConfig		= workspaceRoot.resolve( ".bxlint.json" );
		WorkspaceFolder			folder			= new WorkspaceFolder();

		Files.writeString( documentPath, """
		                                 <cfquery>
		                                 	<cfqueryparam value="#paramRef#">
		                                 </cfquery>
		                                 """ );
		Files.writeString( lintConfig, """
		                               {
		                                 "diagnostics": {
		                                   "missingQueryParamCfsqltype": {
		                                     "severity": "information"
		                                   }
		                                 }
		                               }
		                               """ );
		folder.setUri( workspaceRoot.toUri().toString() );

		try {
			provider.setWorkspaceFolders( List.of( folder ) );
			LintConfigLoader.invalidate();

			Diagnostic diagnostic = provider.getFileDiagnostics( documentPath.toUri() ).stream()
			    .filter( candidate -> candidate.getCode() != null && "missingQueryParamCfsqltype".equals( candidate.getCode().getLeft() ) )
			    .findFirst()
			    .orElse( null );

			assertThat( diagnostic ).isNotNull();
			assertThat( diagnostic.getSeverity() ).isEqualTo( DiagnosticSeverity.Information );
		} finally {
			provider.remove( documentPath.toUri() );
			provider.setWorkspaceFolders( savedFolders );
			LintConfigLoader.invalidate();
		}
	}
}