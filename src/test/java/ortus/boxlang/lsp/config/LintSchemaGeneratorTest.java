package ortus.boxlang.lsp.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class LintSchemaGeneratorTest {

	@TempDir
	Path tempDir;

	@Test
	void generator_runsWithoutErrors() {
		assertDoesNotThrow( () -> LintSchemaGenerator.main( new String[] { tempDir.toString() } ) );
	}

	@Test
	void generator_producesSchemaFile() {
		assertDoesNotThrow( () -> LintSchemaGenerator.main( new String[] { tempDir.toString() } ) );
		File schema = tempDir.resolve( "bxlint.schema.json" ).toFile();
		assertTrue( schema.exists(), "bxlint.schema.json must be created" );
		assertTrue( schema.length() > 0, "bxlint.schema.json must not be empty" );
	}

	@Test
	void schemaContainsRootPropertiesAndDefinitions() throws IOException {
		LintSchemaGenerator.main( new String[] { tempDir.toString() } );
		String json = Files.readString( tempDir.resolve( "bxlint.schema.json" ) );
		assertTrue( json.contains( "\"$schema\"" ), "schema must declare a JSON Schema version" );
		assertTrue( json.contains( "\"diagnostics\"" ), "schema must contain diagnostics property" );
		assertTrue( json.contains( "\"formatting\"" ), "schema must contain formatting property" );
		assertTrue( json.contains( "\"ruleSettings\"" ), "schema must define reusable rule settings" );
	}

	@Test
	void schemaEnumeratesKnownRuleIds() throws IOException {
		LintSchemaGenerator.main( new String[] { tempDir.toString() } );
		String json = Files.readString( tempDir.resolve( "bxlint.schema.json" ) );
		assertTrue( json.contains( "\"unusedVariable\"" ), "schema must enumerate unusedVariable" );
		assertTrue( json.contains( "\"unreachableCode\"" ), "schema must enumerate unreachableCode" );
		assertTrue( json.contains( "\"unusedPrivateMethod\"" ), "schema must enumerate unusedPrivateMethod" );
	}
}