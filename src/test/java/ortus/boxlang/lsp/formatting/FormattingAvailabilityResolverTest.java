package ortus.boxlang.lsp.formatting;

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

import ortus.boxlang.lsp.UserSettings;
import ortus.boxlang.lsp.lint.LintConfig;

class FormattingAvailabilityResolverTest {

	private static final LanguageClient NO_OP_CLIENT = new LanguageClient() {

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
	};

	@Test
	void formattingIsUnavailableWhenEnabledButRuntimeDoesNotSupportPrettyPrint() {
		LintConfig lintConfig = new LintConfig();
		lintConfig.formatting.experimental.enabled = true;

		PrettyPrintRuntimeAdapter	runtimeAdapter	= new PrettyPrintRuntimeAdapter() {

														@Override
														public boolean isPrettyPrintAvailable() {
															return false;
														}
													};

		boolean						available		= new FormattingAvailabilityResolver( new FormattingSettingsResolver(), runtimeAdapter )
		    .isFormattingAvailable( lintConfig, userSettingsWithFormattingEnabled( true ) );

		assertThat( available ).isFalse();
	}

	private UserSettings userSettingsWithFormattingEnabled( boolean enabled ) {
		JsonObject settings = new JsonObject();
		settings.addProperty( "experimentalFormatterEnabled", enabled );
		return UserSettings.fromChangeConfigurationParams( NO_OP_CLIENT, new DidChangeConfigurationParams( settings ) );
	}
}