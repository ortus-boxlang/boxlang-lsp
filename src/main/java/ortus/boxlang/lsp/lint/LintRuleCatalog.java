package ortus.boxlang.lsp.lint;

import java.util.Comparator;
import java.util.List;

import ortus.boxlang.lsp.config.annotation.LintRule;
import ortus.boxlang.lsp.lint.rules.DuplicateMethodRule;
import ortus.boxlang.lsp.lint.rules.DuplicatePropertyRule;
import ortus.boxlang.lsp.lint.rules.EmptyCatchBlockRule;
import ortus.boxlang.lsp.lint.rules.InvalidExtendsRule;
import ortus.boxlang.lsp.lint.rules.InvalidImplementsRule;
import ortus.boxlang.lsp.lint.rules.MissingQueryParamCfsqltypeRule;
import ortus.boxlang.lsp.lint.rules.MissingReturnStatementRule;
import ortus.boxlang.lsp.lint.rules.ShadowedVariableRule;
import ortus.boxlang.lsp.lint.rules.UnescapedQueryParamRule;
import ortus.boxlang.lsp.lint.rules.UnreachableCodeRule;
import ortus.boxlang.lsp.lint.rules.UnscopedVariableRule;
import ortus.boxlang.lsp.lint.rules.UnusedImportRule;
import ortus.boxlang.lsp.lint.rules.UnusedPrivateMethodRule;
import ortus.boxlang.lsp.lint.rules.UnusedVariableRule;

public final class LintRuleCatalog {

	private static final List<Class<?>>		RULE_CLASSES	= List.of(
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
	    UnusedPrivateMethodRule.class,
	    UnescapedQueryParamRule.class,
	    MissingQueryParamCfsqltypeRule.class
	);

	private static final List<LintRuleInfo>	RULES			= RULE_CLASSES.stream()
	    .map( LintRuleCatalog::toRuleInfo )
	    .filter( rule -> rule != null )
	    .sorted( Comparator.comparing( LintRuleInfo::id ) )
	    .toList();

	private LintRuleCatalog() {
	}

	public static List<LintRuleInfo> all() {
		return RULES;
	}

	private static LintRuleInfo toRuleInfo( Class<?> clazz ) {
		LintRule annotation = clazz.getAnnotation( LintRule.class );
		if ( annotation == null ) {
			return null;
		}

		return new LintRuleInfo( annotation.id(), annotation.description(), annotation.defaultSeverity(), annotation.since() );
	}

	public record LintRuleInfo( String id, String description, String defaultSeverity, String since ) {
	}
}