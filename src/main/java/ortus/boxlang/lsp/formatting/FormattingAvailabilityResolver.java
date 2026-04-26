package ortus.boxlang.lsp.formatting;

import ortus.boxlang.lsp.UserSettings;
import ortus.boxlang.lsp.lint.LintConfig;

public class FormattingAvailabilityResolver {

	private final FormattingSettingsResolver	settingsResolver;
	private final PrettyPrintRuntimeAdapter		runtimeAdapter;

	public FormattingAvailabilityResolver() {
		this( new FormattingSettingsResolver(), new PrettyPrintRuntimeAdapter() );
	}

	FormattingAvailabilityResolver( FormattingSettingsResolver settingsResolver, PrettyPrintRuntimeAdapter runtimeAdapter ) {
		this.settingsResolver	= settingsResolver;
		this.runtimeAdapter		= runtimeAdapter;
	}

	public boolean isFormattingEnabled( LintConfig lintConfig, UserSettings userSettings ) {
		return settingsResolver.isExperimentalFormattingEnabled( lintConfig, userSettings );
	}

	public boolean isFormattingAvailable( LintConfig lintConfig, UserSettings userSettings ) {
		return isFormattingEnabled( lintConfig, userSettings )
		    && runtimeAdapter.isPrettyPrintAvailable();
	}

	public boolean isRuntimeSupported() {
		return runtimeAdapter.isPrettyPrintAvailable();
	}
}