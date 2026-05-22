package ortus.boxlang.lsp.config;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class BxlintConfigGeneratorTest {

	@Test
	void generateFullConfigIncludesKnownRulesAndTopLevelDefaults() {
		JsonObject root = JsonParser.parseString( BxlintConfigGenerator.generateFullConfig() ).getAsJsonObject();

		assertThat( root.keySet() ).containsAtLeast( "diagnostics", "include", "exclude", "mappings", "formatting" );

		JsonObject diagnostics = root.getAsJsonObject( "diagnostics" );
		assertThat( diagnostics.size() ).isEqualTo( ConfigDocGenerator.collectLintRules().size() );

		JsonObject invalidExtends = diagnostics.getAsJsonObject( "invalidExtends" );
		assertThat( invalidExtends.get( "enabled" ).getAsBoolean() ).isTrue();
		assertThat( invalidExtends.get( "severity" ).getAsString() ).isEqualTo( "error" );

		assertThat( root.getAsJsonArray( "include" ).size() ).isEqualTo( 0 );
		assertThat( root.getAsJsonArray( "exclude" ).size() ).isEqualTo( 0 );
		assertThat( root.getAsJsonObject( "mappings" ).size() ).isEqualTo( 0 );
		assertThat( root.getAsJsonObject( "formatting" ).size() ).isEqualTo( 0 );
	}
}