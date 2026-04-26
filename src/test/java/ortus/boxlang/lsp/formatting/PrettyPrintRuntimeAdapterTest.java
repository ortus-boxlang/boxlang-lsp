package ortus.boxlang.lsp.formatting;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.Test;

class PrettyPrintRuntimeAdapterTest {

	@Test
	void reportsPrettyPrintAsUnavailableWhenClassCannotBeLoaded() {
		ClassLoader					missingClassLoader	= new ClassLoader( getClass().getClassLoader() ) {

															@Override
															public Class<?> loadClass( String name ) throws ClassNotFoundException {
																if ( PrettyPrintRuntimeAdapter.PRETTY_PRINT_CLASS_NAME.equals( name ) ) {
																	throw new ClassNotFoundException( name );
																}
																return super.loadClass( name );
															}
														};

		PrettyPrintRuntimeAdapter	adapter				= new PrettyPrintRuntimeAdapter( missingClassLoader,
		    PrettyPrintRuntimeAdapter.PRETTY_PRINT_CLASS_NAME );

		assertThat( adapter.isPrettyPrintAvailable() ).isFalse();
	}

	@Test
	void reportsPrettyPrintAsAvailableWhenConfiguredClassCanBeLoaded() {
		PrettyPrintRuntimeAdapter adapter = new PrettyPrintRuntimeAdapter( getClass().getClassLoader(), String.class.getName() );

		assertThat( adapter.isPrettyPrintAvailable() ).isTrue();
	}
}