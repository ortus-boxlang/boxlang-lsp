package ortus.boxlang.lsp;

import ortus.boxlang.runtime.BoxRuntime;

public class BaseTest {

	static {
		setup();
	}

	/**
	 * This is my poor attempt to have control over BoxRuntime initialization
	 * I wasn't able to get environment variables working in the test context
	 * through vscode.
	 */
	public static void setup() {
		BoxRuntime.getInstance( "src/test/resources/.boxlang.json", ".boxlang_home" );
	}
}
