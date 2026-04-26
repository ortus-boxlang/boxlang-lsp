package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertThat;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.services.LanguageClient;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

public class UserSettingsTest extends BaseTest {

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

	// ─── Cycle 1 ─────────────────────────────────────────────────────────────
	// Tracer bullet: UserSettings parses boxlang.mappings from settings payload

	@Test
	void parsesBoxlangMappingsFromSettingsPayload() {
		JsonObject mappings = new JsonObject();
		mappings.addProperty( "/models", "/abs/path/to/models" );
		mappings.addProperty( "/helpers", "./relative/helpers" );

		JsonObject settings = new JsonObject();
		settings.add( "boxlang.mappings", mappings );

		DidChangeConfigurationParams	params			= new DidChangeConfigurationParams( settings );

		UserSettings					userSettings	= UserSettings.fromChangeConfigurationParams( NO_OP_CLIENT, params );

		Map<String, String>				result			= userSettings.getMappings();
		assertThat( result ).isNotNull();
		assertThat( result ).hasSize( 2 );
		assertThat( result ).containsEntry( "/models", "/abs/path/to/models" );
		assertThat( result ).containsEntry( "/helpers", "./relative/helpers" );
	}

	// ─── Cycle 2 ─────────────────────────────────────────────────────────────
	// Missing boxlang.mappings falls back to empty map

	@Test
	void missingMappingsFallsBackToEmptyMap() {
		JsonObject settings = new JsonObject();
		settings.addProperty( "enableBackgroundParsing", true );

		DidChangeConfigurationParams	params			= new DidChangeConfigurationParams( settings );

		UserSettings					userSettings	= UserSettings.fromChangeConfigurationParams( NO_OP_CLIENT, params );

		Map<String, String>				result			= userSettings.getMappings();
		assertThat( result ).isNotNull();
		assertThat( result ).isEmpty();
	}

	// ─── Cycle 3 ─────────────────────────────────────────────────────────────
	// Malformed boxlang.mappings (not an object) falls back to empty map

	@Test
	void malformedMappingsFallsBackToEmptyMap() {
		JsonObject settings = new JsonObject();
		settings.addProperty( "boxlang.mappings", "not-an-object" );

		DidChangeConfigurationParams	params			= new DidChangeConfigurationParams( settings );

		UserSettings					userSettings	= UserSettings.fromChangeConfigurationParams( NO_OP_CLIENT, params );

		Map<String, String>				result			= userSettings.getMappings();
		assertThat( result ).isNotNull();
		assertThat( result ).isEmpty();
	}

	// ─── Cycle 4 ─────────────────────────────────────────────────────────────
	// Empty settings payload falls back to empty map

	@Test
	void emptySettingsPayloadFallsBackToEmptyMap() {
		JsonObject						settings		= new JsonObject();

		DidChangeConfigurationParams	params			= new DidChangeConfigurationParams( settings );

		UserSettings					userSettings	= UserSettings.fromChangeConfigurationParams( NO_OP_CLIENT, params );

		Map<String, String>				result			= userSettings.getMappings();
		assertThat( result ).isNotNull();
		assertThat( result ).isEmpty();
	}

	// ─── Cycle 5 ─────────────────────────────────────────────────────────────
	// Experimental formatter IDE toggle parses from settings payload

	@Test
	void parsesExperimentalFormattingToggleFromSettingsPayload() {
		JsonObject settings = new JsonObject();
		settings.addProperty( "experimentalFormatterEnabled", true );

		DidChangeConfigurationParams	params			= new DidChangeConfigurationParams( settings );

		UserSettings					userSettings	= UserSettings.fromChangeConfigurationParams( NO_OP_CLIENT, params );

		assertThat( userSettings.isExperimentalFormatterEnabled() ).isTrue();
	}
}
