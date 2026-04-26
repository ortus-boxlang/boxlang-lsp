package ortus.boxlang.lsp.formatting;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.Test;

class FormattingCapabilityTransitionResolverTest {

	@Test
	void returnsRegisterWhenDynamicRegistrationIsSupportedAndFormattingBecomesAvailable() {
		FormattingCapabilityTransition transition = new FormattingCapabilityTransitionResolver()
		    .resolve( true, false, true );

		assertThat( transition ).isEqualTo( FormattingCapabilityTransition.REGISTER );
	}
}