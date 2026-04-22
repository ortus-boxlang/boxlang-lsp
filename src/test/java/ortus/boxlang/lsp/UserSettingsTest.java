package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Map;

import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.services.LanguageClient;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

public class UserSettingsTest extends BaseTest {

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
		LanguageClient					mockClient		= mock( LanguageClient.class );

		UserSettings					userSettings	= UserSettings.fromChangeConfigurationParams( mockClient, params );

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
		LanguageClient					mockClient		= mock( LanguageClient.class );

		UserSettings					userSettings	= UserSettings.fromChangeConfigurationParams( mockClient, params );

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
		LanguageClient					mockClient		= mock( LanguageClient.class );

		UserSettings					userSettings	= UserSettings.fromChangeConfigurationParams( mockClient, params );

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
		LanguageClient					mockClient		= mock( LanguageClient.class );

		UserSettings					userSettings	= UserSettings.fromChangeConfigurationParams( mockClient, params );

		Map<String, String>				result			= userSettings.getMappings();
		assertThat( result ).isNotNull();
		assertThat( result ).isEmpty();
	}
}
