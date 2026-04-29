package ortus.boxlang.lsp.config;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import ortus.boxlang.lsp.config.annotation.ConfigSetting;
import ortus.boxlang.lsp.lint.LintConfig;

/** Generates a JSON Schema for .bxlint.json based on the LintConfig model. */
public class LintSchemaGenerator {

	private static final String	SCHEMA_FILENAME	= "bxlint.schema.json";
	private static final String	SCHEMA_VERSION	= "https://json-schema.org/draft/2020-12/schema";

	public static void main( String[] args ) throws IOException {
		String	outputDirArg	= args.length > 0 ? args[ 0 ] : "docs";
		Path	outputDir		= Paths.get( outputDirArg );
		Files.createDirectories( outputDir );
		Files.writeString( outputDir.resolve( SCHEMA_FILENAME ), generateJsonSchema() );
	}

	static String generateJsonSchema() {
		Map<String, Object>	root		= new LinkedHashMap<>();
		Map<String, Object>	properties	= new LinkedHashMap<>();

		root.put( "$schema", SCHEMA_VERSION );
		root.put( "title", "BoxLang Lint Configuration" );
		root.put( "description", "Schema for .bxlint.json, generated from the BoxLang LSP lint configuration model." );
		root.put( "type", "object" );
		root.put( "additionalProperties", false );

		for ( Field field : LintConfig.class.getDeclaredFields() ) {
			if ( !field.isAnnotationPresent( ConfigSetting.class ) ) {
				continue;
			}
			ConfigSetting	setting	= field.getAnnotation( ConfigSetting.class );
			String			key		= setting.key().isEmpty() ? field.getName() : setting.key();
			properties.put( key, buildFieldSchema( key, setting ) );
		}

		root.put( "properties", properties );
		root.put( "$defs", buildDefinitions() );

		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		return gson.toJson( root ) + System.lineSeparator();
	}

	private static Map<String, Object> buildFieldSchema( String key, ConfigSetting setting ) {
		Map<String, Object> schema = switch ( key ) {
			case "diagnostics" -> diagnosticsSchema();
			case "include", "exclude" -> stringArraySchema();
			case "mappings" -> stringMapSchema();
			case "formatting" -> formattingSchema();
			default -> defaultSchema( setting.type() );
		};

		schema.putIfAbsent( "description", setting.description() );
		addDefaultIfPresent( schema, setting.defaultValue() );
		return schema;
	}

	private static Map<String, Object> diagnosticsSchema() {
		Map<String, Object>	schema			= new LinkedHashMap<>();
		List<String>		knownRuleIds	= ConfigDocGenerator.collectLintRules().stream().map( ConfigDocGenerator.LintRuleEntry::id ).sorted()
		    .collect( Collectors.toList() );

		schema.put( "type", "object" );
		schema.put( "propertyNames", Map.of( "enum", knownRuleIds ) );
		schema.put( "additionalProperties", Map.of( "$ref", "#/$defs/ruleSettings" ) );
		return schema;
	}

	private static Map<String, Object> stringArraySchema() {
		Map<String, Object> schema = new LinkedHashMap<>();
		schema.put( "type", "array" );
		schema.put( "items", Map.of( "type", "string" ) );
		return schema;
	}

	private static Map<String, Object> stringMapSchema() {
		Map<String, Object> schema = new LinkedHashMap<>();
		schema.put( "type", "object" );
		schema.put( "additionalProperties", Map.of( "type", "string" ) );
		return schema;
	}

	private static Map<String, Object> formattingSchema() {
		Map<String, Object>	formatting		= new LinkedHashMap<>();
		Map<String, Object>	experimental	= new LinkedHashMap<>();

		experimental.put( "type", "object" );
		experimental.put( "additionalProperties", false );
		experimental.put( "properties", Map.of(
		    "enabled", Map.of(
		        "type", "boolean",
		        "description", "Enables the experimental formatter for the workspace when present."
		    )
		) );

		formatting.put( "type", "object" );
		formatting.put( "additionalProperties", false );
		formatting.put( "properties", Map.of( "experimental", experimental ) );
		return formatting;
	}

	private static Map<String, Object> defaultSchema( String type ) {
		Map<String, Object> schema = new LinkedHashMap<>();
		schema.put( "type", switch ( type ) {
			case "boolean" -> "boolean";
			case "object", "object{}" -> "object";
			case "string[]" -> "array";
			default -> "string";
		} );
		if ( "string[]".equals( type ) ) {
			schema.put( "items", Map.of( "type", "string" ) );
		}
		return schema;
	}

	private static Map<String, Object> buildDefinitions() {
		Map<String, Object>	defs			= new LinkedHashMap<>();
		Map<String, Object>	ruleSettings	= new LinkedHashMap<>();

		ruleSettings.put( "type", "object" );
		ruleSettings.put( "additionalProperties", false );
		ruleSettings.put( "properties", Map.of(
		    "enabled", Map.of(
		        "type", "boolean",
		        "default", true,
		        "description", "Set to false to disable the rule entirely."
		    ),
		    "severity", Map.of(
		        "type", "string",
		        "enum", List.of( "error", "warning", "information", "info", "hint" ),
		        "description", "Overrides the diagnostic severity for the rule."
		    ),
		    "params", Map.of(
		        "type", "object",
		        "description", "Rule-specific parameters. Supported keys depend on the rule.",
		        "additionalProperties", true
		    )
		) );

		defs.put( "ruleSettings", ruleSettings );
		return defs;
	}

	private static void addDefaultIfPresent( Map<String, Object> schema, String defaultValue ) {
		if ( defaultValue == null || defaultValue.isBlank() ) {
			return;
		}
		switch ( defaultValue ) {
			case "{}" -> schema.put( "default", Map.of() );
			case "[]" -> schema.put( "default", List.of() );
			case "true" -> schema.put( "default", true );
			case "false" -> schema.put( "default", false );
			default -> schema.put( "default", defaultValue );
		}
	}
}