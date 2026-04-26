package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertThat;

import java.util.List;

import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.junit.jupiter.api.Test;

import ortus.boxlang.lsp.formatting.FormattingCapabilityCoordinator;
import ortus.boxlang.lsp.workspace.ProjectContextProvider;

class LanguageServerFormattingCapabilityTest extends BaseTest {

	@Test
	void initializeAdvertisesDocumentFormattingProviderWhenCoordinatorSaysToAdvertiseStatically() throws Exception {
		TestFormattingCapabilityCoordinator	coordinator	= new TestFormattingCapabilityCoordinator( true );
		LanguageServer						server		= new LanguageServer( new BoxLangWorkspaceService( coordinator ), new BoxLangTextDocumentService(),
		    ProjectContextProvider.getInstance(), coordinator );

		InitializeParams					params		= new InitializeParams();
		params.setCapabilities( new ClientCapabilities() );
		params.setWorkspaceFolders( List.of() );

		InitializeResult result = server.initialize( params ).get();

		assertThat( result.getCapabilities().getDocumentFormattingProvider().isLeft() ).isTrue();
		assertThat( result.getCapabilities().getDocumentFormattingProvider().getLeft() ).isTrue();
	}

	private static class TestFormattingCapabilityCoordinator extends FormattingCapabilityCoordinator {

		private final boolean shouldAdvertiseStatically;

		private TestFormattingCapabilityCoordinator( boolean shouldAdvertiseStatically ) {
			this.shouldAdvertiseStatically = shouldAdvertiseStatically;
		}

		@Override
		public boolean shouldAdvertiseFormattingStatically() {
			return shouldAdvertiseStatically;
		}
	}
}