package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertThat;

import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.services.LanguageClient;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

import ortus.boxlang.lsp.formatting.FormattingCapabilityCoordinator;
import ortus.boxlang.lsp.lint.LintConfig;
import ortus.boxlang.lsp.workspace.ProjectContextProvider;

class BoxLangWorkspaceServiceFormattingTest extends BaseTest {

	@Test
	void didChangeConfigurationRefreshesFormattingCapabilityCoordinator() {
		RecordingFormattingCapabilityCoordinator	coordinator	= new RecordingFormattingCapabilityCoordinator();
		BoxLangWorkspaceService						service		= new BoxLangWorkspaceService( coordinator );
		service.setLanguageClient( new NoOpLanguageClient() );

		ProjectContextProvider	provider		= ProjectContextProvider.getInstance();
		UserSettings			savedSettings	= provider.getUserSettings();

		try {
			provider.setUserSettings(
			    UserSettings.fromChangeConfigurationParams( new NoOpLanguageClient(), new DidChangeConfigurationParams( new JsonObject() ) ) );

			JsonObject settings = new JsonObject();
			settings.addProperty( "experimentalFormatterEnabled", true );
			service.didChangeConfiguration( new DidChangeConfigurationParams( settings ) );

			assertThat( coordinator.refreshCalls ).isEqualTo( 1 );
			assertThat( coordinator.lastUserSettings ).isNotNull();
			assertThat( coordinator.lastUserSettings.isExperimentalFormatterEnabled() ).isTrue();
		} finally {
			provider.setUserSettings( savedSettings );
		}
	}

	private static class RecordingFormattingCapabilityCoordinator extends FormattingCapabilityCoordinator {

		private int				refreshCalls;
		private UserSettings	lastUserSettings;

		@Override
		public void refresh( LintConfig lintConfig, UserSettings userSettings ) {
			refreshCalls++;
			lastUserSettings = userSettings;
		}
	}

	private static class NoOpLanguageClient implements LanguageClient {

		@Override
		public void telemetryEvent( Object object ) {
		}

		@Override
		public void publishDiagnostics( PublishDiagnosticsParams diagnostics ) {
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
	}
}