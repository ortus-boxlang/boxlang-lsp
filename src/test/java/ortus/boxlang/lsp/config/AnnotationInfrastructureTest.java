package ortus.boxlang.lsp.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;

import ortus.boxlang.lsp.config.annotation.ConfigGroup;
import ortus.boxlang.lsp.config.annotation.ConfigSetting;
import ortus.boxlang.lsp.config.annotation.LintRule;

public class AnnotationInfrastructureTest {

	// --- @ConfigGroup ---

	@ConfigGroup( configFile = "boxlang.json", title = "Server Settings" )
	private static class SampleConfigClass {
	}

	@ConfigGroup( configFile = "boxlang.json", title = "Server Settings", description = "Controls server behavior" )
	private static class SampleConfigClassWithDesc {
	}

	@Test
	void configGroup_canBeReadAtRuntime() {
		ConfigGroup annotation = SampleConfigClass.class.getAnnotation( ConfigGroup.class );
		assertNotNull( annotation, "@ConfigGroup should be readable at runtime" );
		assertEquals( "boxlang.json", annotation.configFile() );
		assertEquals( "Server Settings", annotation.title() );
		assertEquals( "", annotation.description(), "description should default to empty string" );
	}

	@Test
	void configGroup_acceptsDescription() {
		ConfigGroup annotation = SampleConfigClassWithDesc.class.getAnnotation( ConfigGroup.class );
		assertEquals( "Controls server behavior", annotation.description() );
	}

	@Test
	void configGroup_hasRuntimeRetention() {
		Retention retention = ConfigGroup.class.getAnnotation( Retention.class );
		assertNotNull( retention, "@ConfigGroup must have @Retention" );
		assertEquals( RetentionPolicy.RUNTIME, retention.value() );
	}

	@Test
	void configGroup_targetsType() {
		Target target = ConfigGroup.class.getAnnotation( Target.class );
		assertNotNull( target, "@ConfigGroup must have @Target" );
		assertEquals( 1, target.value().length );
		assertEquals( ElementType.TYPE, target.value()[ 0 ] );
	}

	// --- @ConfigSetting ---

	private static class SampleSettingsHolder {

		@ConfigSetting( type = "boolean", description = "Enable debug mode", defaultValue = "false" )
		public boolean	debugMode;

		@ConfigSetting( key = "custom-key", type = "string", description = "Some setting", defaultValue = "hello", since = "1.2.0" )
		public String	someSetting;
	}

	@Test
	void configSetting_canBeReadAtRuntime() throws NoSuchFieldException {
		Field			field		= SampleSettingsHolder.class.getField( "debugMode" );
		ConfigSetting	annotation	= field.getAnnotation( ConfigSetting.class );
		assertNotNull( annotation, "@ConfigSetting should be readable at runtime" );
		assertEquals( "boolean", annotation.type() );
		assertEquals( "Enable debug mode", annotation.description() );
		assertEquals( "false", annotation.defaultValue() );
		assertEquals( "", annotation.key(), "key should default to empty string" );
		assertEquals( "", annotation.since(), "since should default to empty string" );
	}

	@Test
	void configSetting_acceptsAllAttributes() throws NoSuchFieldException {
		Field			field		= SampleSettingsHolder.class.getField( "someSetting" );
		ConfigSetting	annotation	= field.getAnnotation( ConfigSetting.class );
		assertEquals( "custom-key", annotation.key() );
		assertEquals( "string", annotation.type() );
		assertEquals( "Some setting", annotation.description() );
		assertEquals( "hello", annotation.defaultValue() );
		assertEquals( "1.2.0", annotation.since() );
	}

	@Test
	void configSetting_hasRuntimeRetention() {
		Retention retention = ConfigSetting.class.getAnnotation( Retention.class );
		assertNotNull( retention, "@ConfigSetting must have @Retention" );
		assertEquals( RetentionPolicy.RUNTIME, retention.value() );
	}

	@Test
	void configSetting_targetsField() {
		Target target = ConfigSetting.class.getAnnotation( Target.class );
		assertNotNull( target, "@ConfigSetting must have @Target" );
		assertEquals( 1, target.value().length );
		assertEquals( ElementType.FIELD, target.value()[ 0 ] );
	}

	// --- @LintRule ---

	@LintRule( id = "unused-variables", description = "Flags variables that are declared but never used", defaultSeverity = "warning" )
	private static class SampleLintRuleClass {
	}

	@LintRule( id = "my-rule", description = "Something", defaultSeverity = "error", since = "1.0.0" )
	private static class SampleLintRuleClassWithSince {
	}

	@Test
	void lintRule_canBeReadAtRuntime() {
		LintRule annotation = SampleLintRuleClass.class.getAnnotation( LintRule.class );
		assertNotNull( annotation, "@LintRule should be readable at runtime" );
		assertEquals( "unused-variables", annotation.id() );
		assertEquals( "Flags variables that are declared but never used", annotation.description() );
		assertEquals( "warning", annotation.defaultSeverity() );
		assertEquals( "", annotation.since(), "since should default to empty string" );
	}

	@Test
	void lintRule_acceptsSince() {
		LintRule annotation = SampleLintRuleClassWithSince.class.getAnnotation( LintRule.class );
		assertEquals( "1.0.0", annotation.since() );
	}

	@Test
	void lintRule_hasRuntimeRetention() {
		Retention retention = LintRule.class.getAnnotation( Retention.class );
		assertNotNull( retention, "@LintRule must have @Retention" );
		assertEquals( RetentionPolicy.RUNTIME, retention.value() );
	}

	@Test
	void lintRule_targetsType() {
		Target target = LintRule.class.getAnnotation( Target.class );
		assertNotNull( target, "@LintRule must have @Target" );
		assertEquals( 1, target.value().length );
		assertEquals( ElementType.TYPE, target.value()[ 0 ] );
	}
}
