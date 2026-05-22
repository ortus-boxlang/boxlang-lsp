package ortus.boxlang.lsp.config;

import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import ortus.boxlang.lsp.config.annotation.ConfigSetting;
import ortus.boxlang.lsp.lint.LintConfig;

public final class BxlintConfigGenerator {

	private static final Gson			GSON			= new GsonBuilder().setPrettyPrinting().create();
	private static final List<String>	TOP_LEVEL_ORDER	= List.of( "diagnostics", "include", "exclude", "mappings", "formatting" );

	private BxlintConfigGenerator() {
	}

	public static String generateFullConfig() {
		Map<String, ConfigSetting>	settingsByKey	= List.of( LintConfig.class.getDeclaredFields() ).stream()
		    .filter( field -> field.isAnnotationPresent( ConfigSetting.class ) )
		    .collect( Collectors.toMap( BxlintConfigGenerator::getConfigKey, field -> field.getAnnotation( ConfigSetting.class ) ) );

		JsonObject					root			= new JsonObject();
		for ( String key : TOP_LEVEL_ORDER ) {
			ConfigSetting setting = settingsByKey.get( key );
			if ( setting != null ) {
				root.add( key, buildValue( key, setting ) );
			}
		}

		return GSON.toJson( root ) + System.lineSeparator();
	}

	private static String getConfigKey( Field field ) {
		ConfigSetting setting = field.getAnnotation( ConfigSetting.class );
		return setting.key().isEmpty() ? field.getName() : setting.key();
	}

	private static JsonElement buildValue( String key, ConfigSetting setting ) {
		if ( "diagnostics".equals( key ) ) {
			return buildDiagnostics();
		}

		String defaultValue = setting.defaultValue();
		if ( defaultValue == null || defaultValue.isBlank() ) {
			return new JsonObject();
		}

		try {
			return JsonParser.parseString( defaultValue );
		} catch ( RuntimeException e ) {
			return new JsonPrimitive( defaultValue );
		}
	}

	private static JsonObject buildDiagnostics() {
		JsonObject diagnostics = new JsonObject();

		ConfigDocGenerator.collectLintRules().stream()
		    .sorted( Comparator.comparing( ConfigDocGenerator.LintRuleEntry::id ) )
		    .forEach( rule -> {
			    JsonObject ruleSettings = new JsonObject();
			    ruleSettings.addProperty( "enabled", true );
			    ruleSettings.addProperty( "severity", normalizeSeverity( rule.defaultSeverity() ) );
			    diagnostics.add( rule.id(), ruleSettings );
		    } );

		return diagnostics;
	}

	private static String normalizeSeverity( String severity ) {
		return "info".equalsIgnoreCase( severity ) ? "information" : severity;
	}
}