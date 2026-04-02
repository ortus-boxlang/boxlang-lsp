package ortus.boxlang.lsp.config;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import ortus.boxlang.lsp.UserSettings;
import ortus.boxlang.lsp.config.annotation.ConfigGroup;
import ortus.boxlang.lsp.config.annotation.ConfigSetting;
import ortus.boxlang.lsp.config.annotation.LintRule;
import ortus.boxlang.lsp.lint.LintConfig;
import ortus.boxlang.lsp.lint.rules.DuplicateMethodRule;
import ortus.boxlang.lsp.lint.rules.DuplicatePropertyRule;
import ortus.boxlang.lsp.lint.rules.EmptyCatchBlockRule;
import ortus.boxlang.lsp.lint.rules.InvalidExtendsRule;
import ortus.boxlang.lsp.lint.rules.InvalidImplementsRule;
import ortus.boxlang.lsp.lint.rules.MissingReturnStatementRule;
import ortus.boxlang.lsp.lint.rules.ShadowedVariableRule;
import ortus.boxlang.lsp.lint.rules.UnreachableCodeRule;
import ortus.boxlang.lsp.lint.rules.UnscopedVariableRule;
import ortus.boxlang.lsp.lint.rules.UnusedImportRule;
import ortus.boxlang.lsp.lint.rules.UnusedPrivateMethodRule;
import ortus.boxlang.lsp.lint.rules.UnusedVariableRule;
import ortus.boxlang.lsp.workspace.MappingConfig;

public class ConfigDocGenerator {

	private static final List<Class<?>>	CONFIG_GROUP_CLASSES	= List.of(
	    UserSettings.class,
	    LintConfig.class,
	    MappingConfig.class
	);

	private static final List<Class<?>>	LINT_RULE_CLASSES		= List.of(
	    UnusedVariableRule.class,
	    UnscopedVariableRule.class,
	    DuplicateMethodRule.class,
	    DuplicatePropertyRule.class,
	    EmptyCatchBlockRule.class,
	    InvalidExtendsRule.class,
	    InvalidImplementsRule.class,
	    MissingReturnStatementRule.class,
	    ShadowedVariableRule.class,
	    UnreachableCodeRule.class,
	    UnusedImportRule.class,
	    UnusedPrivateMethodRule.class
	);

	public static void main( String[] args ) throws IOException {
		String	outputDirArg	= args.length > 0 ? args[ 0 ] : "docs";
		Path	outputDir		= Paths.get( outputDirArg );
		Files.createDirectories( outputDir );

		List<ConfigGroupEntry>	configGroups	= collectConfigGroups();
		List<LintRuleEntry>		lintRules		= collectLintRules();

		Files.writeString( outputDir.resolve( "config-reference.md" ), generateMarkdown( configGroups, lintRules ) );
		Files.writeString( outputDir.resolve( "config-reference.json" ), generateJson( configGroups, lintRules ) );
	}

	// --- Data collection ---

	private static List<ConfigGroupEntry> collectConfigGroups() {
		List<ConfigGroupEntry> result = new ArrayList<>();
		for ( Class<?> clazz : CONFIG_GROUP_CLASSES ) {
			ConfigGroup group = clazz.getAnnotation( ConfigGroup.class );
			if ( group == null )
				continue;

			List<SettingEntry> settings = new ArrayList<>();
			for ( Field field : clazz.getDeclaredFields() ) {
				ConfigSetting setting = field.getAnnotation( ConfigSetting.class );
				if ( setting == null )
					continue;
				String key = setting.key().isEmpty() ? field.getName() : setting.key();
				settings.add( new SettingEntry( key, setting.type(), setting.defaultValue(), setting.since(), setting.description() ) );
			}
			result.add( new ConfigGroupEntry( group.configFile(), group.title(), group.description(), settings ) );
		}
		return result;
	}

	private static List<LintRuleEntry> collectLintRules() {
		List<LintRuleEntry> result = new ArrayList<>();
		for ( Class<?> clazz : LINT_RULE_CLASSES ) {
			LintRule annotation = clazz.getAnnotation( LintRule.class );
			if ( annotation == null )
				continue;
			result.add( new LintRuleEntry( annotation.id(), annotation.defaultSeverity(), annotation.since(), annotation.description() ) );
		}
		return result;
	}

	// --- Markdown generation ---

	private static String generateMarkdown( List<ConfigGroupEntry> configGroups, List<LintRuleEntry> lintRules ) {
		StringBuilder sb = new StringBuilder();
		sb.append( "# BoxLang LSP Configuration Reference\n\n" );
		sb.append( "The BoxLang LSP is controlled through three separate configuration systems: " );
		sb.append( "**IDE Workspace Settings** (`boxlang.lsp.*`), " );
		sb.append( "**Lint Configuration** (`.bxlint.json`), and " );
		sb.append( "**Project Mappings** (`boxlang.json`). " );
		sb.append( "This document lists all available settings for each system.\n\n" );

		for ( ConfigGroupEntry group : configGroups ) {
			sb.append( "## " ).append( group.title() ).append( "\n\n" );
			sb.append( "**Config file:** `" ).append( group.configFile() ).append( "`\n\n" );
			if ( !group.description().isEmpty() ) {
				sb.append( group.description() ).append( "\n\n" );
			}
			sb.append( "| Key | Type | Default | Since | Description |\n" );
			sb.append( "| --- | ---- | ------- | ----- | ----------- |\n" );
			for ( SettingEntry s : group.settings() ) {
				sb.append( "| `" ).append( s.key() ).append( "` | " )
				    .append( s.type() ).append( " | " )
				    .append( "`" ).append( s.defaultValue() ).append( "` | " )
				    .append( s.since().isEmpty() ? "" : s.since() ).append( " | " )
				    .append( s.description() ).append( " |\n" );
			}
			sb.append( "\n" );
		}

		sb.append( "## Lint Rules\n\n" );
		sb.append( "| Rule ID | Default Severity | Since | Description |\n" );
		sb.append( "| ------- | ---------------- | ----- | ----------- |\n" );
		for ( LintRuleEntry rule : lintRules ) {
			sb.append( "| `" ).append( rule.id() ).append( "` | " )
			    .append( rule.defaultSeverity() ).append( " | " )
			    .append( rule.since().isEmpty() ? "" : rule.since() ).append( " | " )
			    .append( rule.description() ).append( " |\n" );
		}
		sb.append( "\n" );
		sb.append( "### Rule settings\n\n" );
		sb.append( "Every rule supports the following fields in `.bxlint.json` under the `diagnostics` key:\n\n" );
		sb.append( "- `enabled` (boolean) — set to `false` to disable the rule entirely.\n" );
		sb.append( "- `severity` (string) — override the default severity: `\"error\"`, `\"warning\"`, `\"information\"`, or `\"hint\"`.\n" );
		sb.append( "- `params` (object) — rule-specific parameters (if supported by the rule).\n" );

		return sb.toString();
	}

	// --- JSON generation ---

	private static String generateJson( List<ConfigGroupEntry> configGroups, List<LintRuleEntry> lintRules ) {
		Map<String, Object> root = new LinkedHashMap<>();

		for ( ConfigGroupEntry group : configGroups ) {
			Map<String, Object> entry = new LinkedHashMap<>();
			entry.put( "title", group.title() );
			entry.put( "description", group.description() );

			List<Map<String, String>> settings = new ArrayList<>();
			for ( SettingEntry s : group.settings() ) {
				Map<String, String> setting = new LinkedHashMap<>();
				setting.put( "key", s.key() );
				setting.put( "type", s.type() );
				setting.put( "default", s.defaultValue() );
				setting.put( "since", s.since() );
				setting.put( "description", s.description() );
				settings.add( setting );
			}
			entry.put( "settings", settings );

			if ( ".bxlint.json".equals( group.configFile() ) ) {
				List<Map<String, String>> rules = new ArrayList<>();
				for ( LintRuleEntry rule : lintRules ) {
					Map<String, String> ruleMap = new LinkedHashMap<>();
					ruleMap.put( "id", rule.id() );
					ruleMap.put( "defaultSeverity", rule.defaultSeverity() );
					ruleMap.put( "since", rule.since() );
					ruleMap.put( "description", rule.description() );
					rules.add( ruleMap );
				}
				entry.put( "rules", rules );
			}

			root.put( group.configFile(), entry );
		}

		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		return gson.toJson( root );
	}

	// --- Inner record types ---

	private record ConfigGroupEntry( String configFile, String title, String description, List<SettingEntry> settings ) {
	}

	private record SettingEntry( String key, String type, String defaultValue, String since, String description ) {
	}

	private record LintRuleEntry( String id, String defaultSeverity, String since, String description ) {
	}
}
