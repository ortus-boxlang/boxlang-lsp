package ortus.boxlang.lsp.formatting;

public class FormattingCapabilityTransitionResolver {

	public FormattingCapabilityTransition resolve( boolean supportsDynamicRegistration, boolean currentlyRegistered, boolean shouldBeRegistered ) {
		if ( !supportsDynamicRegistration ) {
			return FormattingCapabilityTransition.NONE;
		}

		if ( !currentlyRegistered && shouldBeRegistered ) {
			return FormattingCapabilityTransition.REGISTER;
		}

		if ( currentlyRegistered && !shouldBeRegistered ) {
			return FormattingCapabilityTransition.UNREGISTER;
		}

		return FormattingCapabilityTransition.NONE;
	}
}