package ortus.boxlang.lsp.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ConfigDocGeneratorTest {

	@TempDir
	Path tempDir;

	@Test
	void generator_runsWithoutErrors() {
		assertDoesNotThrow( () -> ConfigDocGenerator.main( new String[] { tempDir.toString() } ) );
	}

	@Test
	void generator_producesMarkdownFile() {
		assertDoesNotThrow( () -> ConfigDocGenerator.main( new String[] { tempDir.toString() } ) );
		File md = tempDir.resolve( "config-reference.md" ).toFile();
		assertTrue( md.exists(), "config-reference.md must be created" );
		assertTrue( md.length() > 0, "config-reference.md must not be empty" );
	}

	@Test
	void generator_producesJsonFile() {
		assertDoesNotThrow( () -> ConfigDocGenerator.main( new String[] { tempDir.toString() } ) );
		File json = tempDir.resolve( "config-reference.json" ).toFile();
		assertTrue( json.exists(), "config-reference.json must be created" );
		assertTrue( json.length() > 0, "config-reference.json must not be empty" );
	}

	@Test
	void markdownContainsConfigGroupSections() throws IOException {
		ConfigDocGenerator.main( new String[] { tempDir.toString() } );
		String md = Files.readString( tempDir.resolve( "config-reference.md" ) );
		assertTrue( md.contains( "## IDE Workspace Settings" ), "md must contain IDE Workspace Settings section" );
		assertTrue( md.contains( "## Lint Configuration" ), "md must contain Lint Configuration section" );
		assertTrue( md.contains( "## Project Mappings" ), "md must contain Project Mappings section" );
	}

	@Test
	void markdownContainsLintRulesSection() throws IOException {
		ConfigDocGenerator.main( new String[] { tempDir.toString() } );
		String md = Files.readString( tempDir.resolve( "config-reference.md" ) );
		assertTrue( md.contains( "## Lint Rules" ), "md must contain Lint Rules section" );
	}

	@Test
	void markdownListsAllTwelveLintRules() throws IOException {
		ConfigDocGenerator.main( new String[] { tempDir.toString() } );
		String md = Files.readString( tempDir.resolve( "config-reference.md" ) );
		assertTrue( md.contains( "unusedVariable" ), "md must list unusedVariable rule" );
		assertTrue( md.contains( "unscopedVariable" ), "md must list unscopedVariable rule" );
		assertTrue( md.contains( "duplicateMethod" ), "md must list duplicateMethod rule" );
		assertTrue( md.contains( "duplicateProperty" ), "md must list duplicateProperty rule" );
		assertTrue( md.contains( "emptyCatchBlock" ), "md must list emptyCatchBlock rule" );
		assertTrue( md.contains( "invalidExtends" ), "md must list invalidExtends rule" );
		assertTrue( md.contains( "invalidImplements" ), "md must list invalidImplements rule" );
		assertTrue( md.contains( "missingReturnStatement" ), "md must list missingReturnStatement rule" );
		assertTrue( md.contains( "shadowedVariable" ), "md must list shadowedVariable rule" );
		assertTrue( md.contains( "unreachableCode" ), "md must list unreachableCode rule" );
		assertTrue( md.contains( "unusedImport" ), "md must list unusedImport rule" );
		assertTrue( md.contains( "unusedPrivateMethod" ), "md must list unusedPrivateMethod rule" );
	}

	@Test
	void markdownContainsConfigSettingFields() throws IOException {
		ConfigDocGenerator.main( new String[] { tempDir.toString() } );
		String md = Files.readString( tempDir.resolve( "config-reference.md" ) );
		assertTrue( md.contains( "enableBackgroundParsing" ), "md must contain enableBackgroundParsing setting" );
		assertTrue( md.contains( "processDiagnosticsInParallel" ), "md must contain processDiagnosticsInParallel setting" );
		assertTrue( md.contains( "diagnostics" ), "md must contain diagnostics setting from LintConfig" );
		assertTrue( md.contains( "mappings" ), "md must contain mappings setting from MappingConfig" );
	}

	@Test
	void jsonIsKeyedByConfigFile() throws IOException {
		ConfigDocGenerator.main( new String[] { tempDir.toString() } );
		String json = Files.readString( tempDir.resolve( "config-reference.json" ) );
		assertTrue( json.contains( "\"boxlang.lsp.*\"" ), "json must have boxlang.lsp.* key" );
		assertTrue( json.contains( "\".bxlint.json\"" ), "json must have .bxlint.json key" );
		assertTrue( json.contains( "\"boxlang.json\"" ), "json must have boxlang.json key" );
	}

	@Test
	void jsonBxlintHasRulesArray() throws IOException {
		ConfigDocGenerator.main( new String[] { tempDir.toString() } );
		String json = Files.readString( tempDir.resolve( "config-reference.json" ) );
		assertTrue( json.contains( "\"rules\"" ), "json .bxlint.json entry must contain rules array" );
	}

	@Test
	void jsonHasSettingsArray() throws IOException {
		ConfigDocGenerator.main( new String[] { tempDir.toString() } );
		String json = Files.readString( tempDir.resolve( "config-reference.json" ) );
		assertTrue( json.contains( "\"settings\"" ), "json must contain settings arrays" );
	}
}
