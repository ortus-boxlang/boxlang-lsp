package ortus.boxlang.lsp.formatting;

import static com.google.common.truth.Truth.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PrettyPrintRuntimeAdapterTest {

	@TempDir
	Path tempDir;

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

	@Test
	void readsDefaultFormatterConfigFromPrettyPrintRuntime() throws Exception {
		PrettyPrintRuntimeAdapter adapter = new PrettyPrintRuntimeAdapter( getClass().getClassLoader(), StubPrettyPrint.class.getName() );

		assertThat( adapter.getDefaultConfigJson() ).contains( "\"indentSize\" : 2" );
	}

	@Test
	void convertsCFFormatConfigToBxFormatJson() throws Exception {
		Path cfformatPath = tempDir.resolve( ".cfformat.json" );
		Files.writeString( cfformatPath, "{}" );

		PrettyPrintRuntimeAdapter adapter = new PrettyPrintRuntimeAdapter( getClass().getClassLoader(), StubPrettyPrint.class.getName(),
		    StubConfig.class.getName() );

		assertThat( adapter.convertCFFormatConfigToBxFormatJson( cfformatPath ) ).contains( "\"converted\" : true" );
	}

	public static class StubPrettyPrint {

		@SuppressWarnings( "unused" )
		private static final String DEFAULT_CONFIG = "{\n  \"indentSize\" : 2\n}";
	}

	public static class StubConfig {

		public static StubConfig loadConfigAutoDetect( String filePath ) {
			return new StubConfig( filePath );
		}

		private final String filePath;

		private StubConfig( String filePath ) {
			this.filePath = filePath;
		}

		public String toJSON() {
			return "{\n  \"converted\" : true,\n  \"sourcePath\" : \"" + filePath.replace( "\\", "\\\\" ) + "\"\n}";
		}
	}
}