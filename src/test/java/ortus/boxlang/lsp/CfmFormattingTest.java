package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.lsp4j.FormattingOptions;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.junit.jupiter.api.Test;

import ortus.boxlang.lsp.formatting.PrettyPrintRuntimeAdapter;
import ortus.boxlang.lsp.workspace.ProjectContextProvider;

public class CfmFormattingTest extends BaseTest {

	@Test
	void formatDocumentDoesNotConvertCfTagsToBxTags() throws Exception {
		ProjectContextProvider		provider			= ProjectContextProvider.getInstance();
		List<WorkspaceFolder>		savedFolders		= provider.getWorkspaceFolders();
		UserSettings				savedSettings		= provider.getUserSettings();
		PrettyPrintRuntimeAdapter	savedRuntimeAdapter	= provider.getPrettyPrintRuntimeAdapter();

		Path						repoRoot			= Path.of( "" ).toAbsolutePath().normalize();
		Path						documentPath		= repoRoot.resolve( "src/test/resources/files/cfmFormattingTest.cfm" );
		String						source				= Files.readString( documentPath );

		WorkspaceFolder				folder				= new WorkspaceFolder();
		folder.setUri( repoRoot.toUri().toString() );

		try {
			provider.setWorkspaceFolders( List.of( folder ) );
			provider.setUserSettings( createUserSettings( true ) );
			provider.setPrettyPrintRuntimeAdapter( new PrettyPrintRuntimeAdapter() );
			provider.trackDocumentOpen( documentPath.toUri(), source );

			List<? extends TextEdit> edits = provider.formatDocument( documentPath.toUri(), new FormattingOptions( 4, true ) );

			assertThat( edits ).isNotEmpty();
			TextEdit	edit		= edits.getFirst();
			String		formatted	= edit.getNewText();

			// CF tags should remain as CF tags
			assertThat( formatted ).contains( "<cfset" );
			assertThat( formatted ).contains( "<cfoutput>" );
			assertThat( formatted ).contains( "</cfoutput>" );
			assertThat( formatted ).contains( "<cfif" );
			assertThat( formatted ).contains( "</cfif>" );

			// Should NOT be converted to bx: tags
			assertThat( formatted ).doesNotContain( "<bx:set" );
			assertThat( formatted ).doesNotContain( "<bx:output>" );
			assertThat( formatted ).doesNotContain( "</bx:output>" );
			assertThat( formatted ).doesNotContain( "<bx:if" );
			assertThat( formatted ).doesNotContain( "</bx:if>" );
		} finally {
			provider.trackDocumentClose( documentPath.toUri() );
			provider.setWorkspaceFolders( savedFolders );
			provider.setUserSettings( savedSettings );
			provider.setPrettyPrintRuntimeAdapter( savedRuntimeAdapter );
		}
	}

	private static UserSettings createUserSettings( boolean experimentalFormatterEnabled ) {
		com.google.gson.JsonObject settings = new com.google.gson.JsonObject();
		settings.addProperty( "experimentalFormatterEnabled", experimentalFormatterEnabled );
		org.eclipse.lsp4j.DidChangeConfigurationParams params = new org.eclipse.lsp4j.DidChangeConfigurationParams( settings );
		return UserSettings.fromChangeConfigurationParams( new NoOpLanguageClient(), params );
	}

	private static class NoOpLanguageClient implements org.eclipse.lsp4j.services.LanguageClient {

		@Override
		public void telemetryEvent( Object object ) {
		}

		@Override
		public void publishDiagnostics( org.eclipse.lsp4j.PublishDiagnosticsParams diagnostics ) {
		}

		@Override
		public void showMessage( org.eclipse.lsp4j.MessageParams messageParams ) {
		}

		@Override
		public java.util.concurrent.CompletableFuture<org.eclipse.lsp4j.MessageActionItem> showMessageRequest(
		    org.eclipse.lsp4j.ShowMessageRequestParams requestParams ) {
			return java.util.concurrent.CompletableFuture.completedFuture( null );
		}

		@Override
		public void logMessage( org.eclipse.lsp4j.MessageParams message ) {
		}
	}
}
