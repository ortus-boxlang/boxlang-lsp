package ortus.boxlang.lsp.formatting;

import ortus.boxlang.lsp.UserSettings;
import ortus.boxlang.lsp.lint.LintConfig;

public class FormattingSettingsResolver {

	public boolean isExperimentalFormattingEnabled( LintConfig lintConfig, UserSettings userSettings ) {
		if ( lintConfig != null
		    && lintConfig.formatting != null
		    && lintConfig.formatting.experimental != null
		    && lintConfig.formatting.experimental.enabled != null ) {
			return lintConfig.formatting.experimental.enabled;
		}

		return userSettings != null && userSettings.isExperimentalFormatterEnabled();
	}
}