package ortus.boxlang.lsp.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import ortus.boxlang.lsp.config.annotation.LintRule;
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

public class LintRuleAnnotationsTest {

	private void assertLintRule( Class<?> clazz, String expectedId, String expectedSeverity ) {
		LintRule annotation = clazz.getAnnotation( LintRule.class );
		assertNotNull( annotation, clazz.getSimpleName() + " must be annotated with @LintRule" );
		assertEquals( expectedId, annotation.id(), clazz.getSimpleName() + " @LintRule.id must match ID constant" );
		assertEquals( expectedSeverity, annotation.defaultSeverity(), clazz.getSimpleName() + " @LintRule.defaultSeverity must match actual severity" );
		assertNotNull( annotation.description(), clazz.getSimpleName() + " @LintRule.description must not be null" );
		// description must be non-empty
		assertEquals( true, !annotation.description().isBlank(), clazz.getSimpleName() + " @LintRule.description must not be blank" );
	}

	@Test
	void unusedVariableRule_hasLintRule() {
		assertLintRule( UnusedVariableRule.class, "unusedVariable", "hint" );
	}

	@Test
	void unscopedVariableRule_hasLintRule() {
		assertLintRule( UnscopedVariableRule.class, "unscopedVariable", "warning" );
	}

	@Test
	void duplicateMethodRule_hasLintRule() {
		assertLintRule( DuplicateMethodRule.class, "duplicateMethod", "error" );
	}

	@Test
	void duplicatePropertyRule_hasLintRule() {
		assertLintRule( DuplicatePropertyRule.class, "duplicateProperty", "error" );
	}

	@Test
	void emptyCatchBlockRule_hasLintRule() {
		assertLintRule( EmptyCatchBlockRule.class, "emptyCatchBlock", "warning" );
	}

	@Test
	void invalidExtendsRule_hasLintRule() {
		assertLintRule( InvalidExtendsRule.class, "invalidExtends", "error" );
	}

	@Test
	void invalidImplementsRule_hasLintRule() {
		assertLintRule( InvalidImplementsRule.class, "invalidImplements", "error" );
	}

	@Test
	void missingReturnStatementRule_hasLintRule() {
		assertLintRule( MissingReturnStatementRule.class, "missingReturnStatement", "warning" );
	}

	@Test
	void shadowedVariableRule_hasLintRule() {
		assertLintRule( ShadowedVariableRule.class, "shadowedVariable", "warning" );
	}

	@Test
	void unreachableCodeRule_hasLintRule() {
		assertLintRule( UnreachableCodeRule.class, "unreachableCode", "warning" );
	}

	@Test
	void unusedImportRule_hasLintRule() {
		assertLintRule( UnusedImportRule.class, "unusedImport", "warning" );
	}

	@Test
	void unusedPrivateMethodRule_hasLintRule() {
		assertLintRule( UnusedPrivateMethodRule.class, "unusedPrivateMethod", "warning" );
	}
}
