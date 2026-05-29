package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import ortus.boxlang.lsp.workspace.ProjectContextProvider;

public class QueryParamDiagnosticsTest extends BaseTest {

	@Test
	void testPublishesDiagnosticsForUnescapedQueryParams() {
		ProjectContextProvider	provider	= ProjectContextProvider.getInstance();
		Path					projectRoot	= Paths.get( System.getProperty( "user.dir" ) );
		Path					path		= projectRoot.resolve( "src/test/resources/files/queryParam/unescapedQueryParam.cfm" );
		File					file		= path.toFile();
		assertTrue( file.exists(), "Test file does not exist: " + path );

		List<Diagnostic> diagnostics = provider.getFileDiagnostics( file.toURI() );

		assertThat( diagnostics ).isNotEmpty();
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
}