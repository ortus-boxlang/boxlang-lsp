package ortus.boxlang.lsp.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;

import ortus.boxlang.lsp.UserSettings;
import ortus.boxlang.lsp.config.annotation.ConfigGroup;
import ortus.boxlang.lsp.config.annotation.ConfigSetting;
import ortus.boxlang.lsp.lint.LintConfig;
import ortus.boxlang.lsp.workspace.MappingConfig;

public class ConfigGroupAnnotationsTest {

	// --- UserSettings ---

	@Test
	void userSettings_classHasConfigGroup() {
		ConfigGroup annotation = UserSettings.class.getAnnotation( ConfigGroup.class );
		assertNotNull( annotation, "UserSettings must be annotated with @ConfigGroup" );
		assertEquals( "boxlang.lsp.*", annotation.configFile() );
		assertEquals( "IDE Workspace Settings", annotation.title() );
	}

	@Test
	void userSettings_enableBackgroundParsing_hasConfigSetting() throws NoSuchFieldException {
		Field			field		= UserSettings.class.getDeclaredField( "enableBackgroundParsing" );
		ConfigSetting	annotation	= field.getAnnotation( ConfigSetting.class );
		assertNotNull( annotation, "enableBackgroundParsing must be annotated with @ConfigSetting" );
		assertEquals( "boolean", annotation.type() );
		assertEquals( "false", annotation.defaultValue() );
		assertEquals( "1.0.0", annotation.since() );
	}

	@Test
	void userSettings_processDiagnosticsInParallel_hasConfigSetting() throws NoSuchFieldException {
		Field			field		= UserSettings.class.getDeclaredField( "processDiagnosticsInParallel" );
		ConfigSetting	annotation	= field.getAnnotation( ConfigSetting.class );
		assertNotNull( annotation, "processDiagnosticsInParallel must be annotated with @ConfigSetting" );
		assertEquals( "boolean", annotation.type() );
		assertEquals( "true", annotation.defaultValue() );
		assertEquals( "1.0.0", annotation.since() );
	}

	@Test
	void userSettings_client_hasNoConfigSetting() throws NoSuchFieldException {
		Field			field		= UserSettings.class.getDeclaredField( "client" );
		ConfigSetting	annotation	= field.getAnnotation( ConfigSetting.class );
		assertNull( annotation, "client is internal and must NOT have @ConfigSetting" );
	}

	// --- LintConfig ---

	@Test
	void lintConfig_classHasConfigGroup() {
		ConfigGroup annotation = LintConfig.class.getAnnotation( ConfigGroup.class );
		assertNotNull( annotation, "LintConfig must be annotated with @ConfigGroup" );
		assertEquals( ".bxlint.json", annotation.configFile() );
		assertEquals( "Lint Configuration", annotation.title() );
	}

	@Test
	void lintConfig_diagnostics_hasConfigSetting() throws NoSuchFieldException {
		Field			field		= LintConfig.class.getDeclaredField( "diagnostics" );
		ConfigSetting	annotation	= field.getAnnotation( ConfigSetting.class );
		assertNotNull( annotation, "LintConfig.diagnostics must be annotated with @ConfigSetting" );
		assertEquals( "diagnostics", annotation.key() );
		assertEquals( "object{}", annotation.type() );
		assertEquals( "{}", annotation.defaultValue() );
	}

	@Test
	void lintConfig_include_hasConfigSetting() throws NoSuchFieldException {
		Field			field		= LintConfig.class.getDeclaredField( "include" );
		ConfigSetting	annotation	= field.getAnnotation( ConfigSetting.class );
		assertNotNull( annotation, "LintConfig.include must be annotated with @ConfigSetting" );
		assertEquals( "string[]", annotation.type() );
		assertEquals( "[]", annotation.defaultValue() );
	}

	@Test
	void lintConfig_exclude_hasConfigSetting() throws NoSuchFieldException {
		Field			field		= LintConfig.class.getDeclaredField( "exclude" );
		ConfigSetting	annotation	= field.getAnnotation( ConfigSetting.class );
		assertNotNull( annotation, "LintConfig.exclude must be annotated with @ConfigSetting" );
		assertEquals( "string[]", annotation.type() );
		assertEquals( "[]", annotation.defaultValue() );
	}

	@Test
	void lintConfig_mappings_hasConfigSetting() throws NoSuchFieldException {
		Field			field		= LintConfig.class.getDeclaredField( "mappings" );
		ConfigSetting	annotation	= field.getAnnotation( ConfigSetting.class );
		assertNotNull( annotation, "LintConfig.mappings must be annotated with @ConfigSetting" );
		assertEquals( "object{}", annotation.type() );
		assertEquals( "{}", annotation.defaultValue() );
	}

	// --- MappingConfig ---

	@Test
	void mappingConfig_classHasConfigGroup() {
		ConfigGroup annotation = MappingConfig.class.getAnnotation( ConfigGroup.class );
		assertNotNull( annotation, "MappingConfig must be annotated with @ConfigGroup" );
		assertEquals( "boxlang.json", annotation.configFile() );
		assertEquals( "Project Mappings", annotation.title() );
	}

	@Test
	void mappingConfig_mappings_hasConfigSetting() throws NoSuchFieldException {
		Field			field		= MappingConfig.class.getDeclaredField( "mappings" );
		ConfigSetting	annotation	= field.getAnnotation( ConfigSetting.class );
		assertNotNull( annotation, "MappingConfig.mappings must be annotated with @ConfigSetting" );
		assertEquals( "object{}", annotation.type() );
		assertEquals( "{}", annotation.defaultValue() );
	}

	@Test
	void mappingConfig_classPaths_hasConfigSetting() throws NoSuchFieldException {
		Field			field		= MappingConfig.class.getDeclaredField( "classPaths" );
		ConfigSetting	annotation	= field.getAnnotation( ConfigSetting.class );
		assertNotNull( annotation, "MappingConfig.classPaths must be annotated with @ConfigSetting" );
		assertEquals( "string[]", annotation.type() );
		assertEquals( "[]", annotation.defaultValue() );
	}

	@Test
	void mappingConfig_modulesDirectory_hasConfigSetting() throws NoSuchFieldException {
		Field			field		= MappingConfig.class.getDeclaredField( "modulesDirectory" );
		ConfigSetting	annotation	= field.getAnnotation( ConfigSetting.class );
		assertNotNull( annotation, "MappingConfig.modulesDirectory must be annotated with @ConfigSetting" );
		assertEquals( "string[]", annotation.type() );
		assertEquals( "[\"boxlang_modules\"]", annotation.defaultValue() );
	}

	@Test
	void mappingConfig_workspaceRoot_hasNoConfigSetting() throws NoSuchFieldException {
		Field			field		= MappingConfig.class.getDeclaredField( "workspaceRoot" );
		ConfigSetting	annotation	= field.getAnnotation( ConfigSetting.class );
		assertNull( annotation, "workspaceRoot is internal and must NOT have @ConfigSetting" );
	}
}
