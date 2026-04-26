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

import ortus.boxlang.lsp.formatting.FormattingSettingsResolver;
import ortus.boxlang.lsp.lint.LintConfig;

class FormattingSettingsResolverTest {

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
	void repoOverrideWinsOverIdeToggle() {
		LintConfig config = new LintConfig();
		config.formatting.experimental.enabled = false;

		boolean enabled = new FormattingSettingsResolver().isExperimentalFormattingEnabled( config, userSettingsWithFormattingEnabled( true ) );

		assertThat( enabled ).isFalse();
	}

	@Test
	void missingRepoSettingFallsBackToIdeToggle() {
		LintConfig config = new LintConfig();
		config.formatting.experimental.enabled = null;

		boolean enabled = new FormattingSettingsResolver().isExperimentalFormattingEnabled( config, userSettingsWithFormattingEnabled( true ) );

		assertThat( enabled ).isTrue();
	}

	@Test
	void defaultsToDisabledWhenRepoAndIdeDoNotEnableFormatting() {
		LintConfig config = new LintConfig();
		config.formatting.experimental.enabled = null;

		boolean enabled = new FormattingSettingsResolver().isExperimentalFormattingEnabled( config, userSettingsWithFormattingEnabled( false ) );

		assertThat( enabled ).isFalse();
	}

	private UserSettings userSettingsWithFormattingEnabled( boolean enabled ) {
		JsonObject settings = new JsonObject();
		settings.addProperty( "experimentalFormatterEnabled", enabled );
		return UserSettings.fromChangeConfigurationParams( NO_OP_CLIENT, new DidChangeConfigurationParams( settings ) );
	}
}