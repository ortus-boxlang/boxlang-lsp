package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.ApplyWorkspaceEditParams;
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ShowDocumentParams;
import org.eclipse.lsp4j.ShowDocumentResult;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;

import ortus.boxlang.lsp.formatting.FormattingCapabilityCoordinator;
import ortus.boxlang.lsp.formatting.PrettyPrintRuntimeAdapter;
import ortus.boxlang.lsp.lint.LintConfig;
import ortus.boxlang.lsp.workspace.ProjectContextProvider;

class BoxLangWorkspaceServiceFormattingTest extends BaseTest {

	@TempDir
	Path tempDir;

	@Test
	void didChangeConfigurationRefreshesFormattingCapabilityCoordinator() {
		RecordingFormattingCapabilityCoordinator	coordinator	= new RecordingFormattingCapabilityCoordinator();
		BoxLangWorkspaceService						service		= new BoxLangWorkspaceService( coordinator );
		service.setLanguageClient( new NoOpLanguageClient() );

		ProjectContextProvider	provider		= ProjectContextProvider.getInstance();
		UserSettings			savedSettings	= provider.getUserSettings();

		try {
			provider.setUserSettings(
			    UserSettings.fromChangeConfigurationParams( new NoOpLanguageClient(), new DidChangeConfigurationParams( new JsonObject() ) ) );

			JsonObject settings = new JsonObject();
			settings.addProperty( "experimentalFormatterEnabled", true );
			service.didChangeConfiguration( new DidChangeConfigurationParams( settings ) );

			assertThat( coordinator.refreshCalls ).isEqualTo( 1 );
			assertThat( coordinator.lastUserSettings ).isNotNull();
			assertThat( coordinator.lastUserSettings.isExperimentalFormatterEnabled() ).isTrue();
		} finally {
			provider.setUserSettings( savedSettings );
		}
	}

	@Test
	void executeCommandAppliesWorkspaceEditForBxlintConfig() throws Exception {
		BoxLangWorkspaceService			service			= new BoxLangWorkspaceService();
		RecordingCommandLanguageClient	client			= new RecordingCommandLanguageClient();
		Path							workspaceRoot	= Files.createDirectories( tempDir.resolve( "workspace" ) );
		String							expectedUri		= workspaceRoot.resolve( ".bxlint.json" ).toUri().toString();

		service.setLanguageClient( client );

		Object result = service
		    .executeCommand( new ExecuteCommandParams( BoxLangWorkspaceService.CREATE_BXLINT_CONFIG_COMMAND, List.of( workspaceRoot.toUri().toString() ) ) )
		    .get();

		assertThat( result ).isEqualTo( expectedUri );
		assertThat( client.lastAppliedEdit ).isNotNull();
		assertThat( client.lastShowDocumentUri ).isEqualTo( expectedUri );
		assertThat( getCreateFileUri( client.lastAppliedEdit ) ).isEqualTo( expectedUri );
		assertThat( getCreatedFileContents( client.lastAppliedEdit ) ).contains( "\"invalidExtends\"" );
	}

	@Test
	void executeCommandShowsDocumentWhenRequestedDirectly() throws Exception {
		BoxLangWorkspaceService			service		= new BoxLangWorkspaceService();
		RecordingCommandLanguageClient	client		= new RecordingCommandLanguageClient();
		String							expectedUri	= tempDir.resolve( "target.json" ).toUri().toString();

		service.setLanguageClient( client );

		Object result = service
		    .executeCommand( new ExecuteCommandParams( BoxLangWorkspaceService.SHOW_DOCUMENT_COMMAND, List.of( expectedUri ) ) )
		    .get();

		assertThat( result ).isEqualTo( expectedUri );
		assertThat( client.lastShowDocumentUri ).isEqualTo( expectedUri );
	}

	@Test
	void executeCommandAppliesWorkspaceEditForFormatterConfigWhenRuntimeSupported() throws Exception {
		RecordingFormattingCapabilityCoordinator coordinator = new RecordingFormattingCapabilityCoordinator();
		coordinator.runtimeSupported = true;
		String							formatterConfig	= "{\n  \"source\" : \"adapter\"\n}";
		BoxLangWorkspaceService			service			= new BoxLangWorkspaceService( coordinator, new StubPrettyPrintRuntimeAdapter( formatterConfig ) );
		RecordingCommandLanguageClient	client			= new RecordingCommandLanguageClient();
		Path							workspaceRoot	= Files.createDirectories( tempDir.resolve( "formatter-workspace" ) );
		String							expectedUri		= workspaceRoot.resolve( ".bxformat.json" ).toUri().toString();

		service.setLanguageClient( client );

		Object result = service
		    .executeCommand( new ExecuteCommandParams( BoxLangWorkspaceService.CREATE_FORMATTER_CONFIG_COMMAND, List.of( workspaceRoot.toUri().toString() ) ) )
		    .get();

		assertThat( result ).isEqualTo( expectedUri );
		assertThat( getCreateFileUri( client.lastAppliedEdit ) ).isEqualTo( expectedUri );
		assertThat( getCreatedFileContents( client.lastAppliedEdit ) ).isEqualTo( formatterConfig );
	}

	@Test
	void executeCommandRejectsFormatterConfigWhenRuntimeUnsupported() {
		RecordingFormattingCapabilityCoordinator coordinator = new RecordingFormattingCapabilityCoordinator();
		coordinator.runtimeSupported = false;
		BoxLangWorkspaceService			service	= new BoxLangWorkspaceService( coordinator );
		RecordingCommandLanguageClient	client	= new RecordingCommandLanguageClient();

		service.setLanguageClient( client );

		Throwable thrown = null;
		try {
			service.executeCommand( new ExecuteCommandParams( BoxLangWorkspaceService.CREATE_FORMATTER_CONFIG_COMMAND, List.of( tempDir.toUri().toString() ) ) )
			    .get();
		} catch ( Exception e ) {
			thrown = e;
		}

		assertThat( thrown ).isNotNull();
	}

	@Test
	void executeCommandConvertsCFFormatConfigToBxFormatConfig() throws Exception {
		RecordingFormattingCapabilityCoordinator coordinator = new RecordingFormattingCapabilityCoordinator();
		coordinator.runtimeSupported = true;
		String							convertedConfig	= "{\n  \"converted\" : true\n}";
		BoxLangWorkspaceService			service			= new BoxLangWorkspaceService( coordinator,
		    new StubPrettyPrintRuntimeAdapter( "{}", convertedConfig ) );
		RecordingCommandLanguageClient	client			= new RecordingCommandLanguageClient();
		Path							workspaceRoot	= Files.createDirectories( tempDir.resolve( "cfformat-workspace" ) );
		Path							cfformatPath	= workspaceRoot.resolve( ".cfformat.json" );
		String							expectedUri		= workspaceRoot.resolve( ".bxformat.json" ).toUri().toString();

		Files.writeString( cfformatPath, "{}" );
		service.setLanguageClient( client );

		Object result = service
		    .executeCommand( new ExecuteCommandParams( BoxLangWorkspaceService.CONVERT_CFFORMAT_CONFIG_COMMAND, List.of( workspaceRoot.toUri().toString() ) ) )
		    .get();

		assertThat( result ).isEqualTo( expectedUri );
		assertThat( getCreateFileUri( client.lastAppliedEdit ) ).isEqualTo( expectedUri );
		assertThat( getCreatedFileContents( client.lastAppliedEdit ) ).isEqualTo( convertedConfig );
	}

	private static String getCreateFileUri( WorkspaceEdit edit ) {
		return edit.getDocumentChanges().stream()
		    .filter( Either::isRight )
		    .map( Either::getRight )
		    .filter( org.eclipse.lsp4j.CreateFile.class::isInstance )
		    .map( org.eclipse.lsp4j.CreateFile.class::cast )
		    .findFirst()
		    .orElseThrow()
		    .getUri();
	}

	private static String getCreatedFileContents( WorkspaceEdit edit ) {
		return edit.getDocumentChanges().stream()
		    .filter( Either::isLeft )
		    .map( Either::getLeft )
		    .findFirst()
		    .orElseThrow()
		    .getEdits()
		    .stream()
		    .findFirst()
		    .orElseThrow()
		    .getLeft()
		    .getNewText();
	}

	private static class RecordingFormattingCapabilityCoordinator extends FormattingCapabilityCoordinator {

		private int				refreshCalls;
		private UserSettings	lastUserSettings;
		private boolean			runtimeSupported	= true;

		@Override
		public void refresh( LintConfig lintConfig, UserSettings userSettings ) {
			refreshCalls++;
			lastUserSettings = userSettings;
		}

		@Override
		public boolean isRuntimeSupported() {
			return runtimeSupported;
		}
	}

	private static class StubPrettyPrintRuntimeAdapter extends PrettyPrintRuntimeAdapter {

		private final String	defaultConfigJson;
		private final String	convertedConfigJson;

		private StubPrettyPrintRuntimeAdapter( String defaultConfigJson ) {
			this( defaultConfigJson, defaultConfigJson );
		}

		private StubPrettyPrintRuntimeAdapter( String defaultConfigJson, String convertedConfigJson ) {
			this.defaultConfigJson		= defaultConfigJson;
			this.convertedConfigJson	= convertedConfigJson;
		}

		@Override
		public String getDefaultConfigJson() {
			return defaultConfigJson;
		}

		@Override
		public String convertCFFormatConfigToBxFormatJson( Path configPath ) {
			return convertedConfigJson;
		}
	}

	private static class NoOpLanguageClient implements LanguageClient {

		@Override
		public void telemetryEvent( Object object ) {
		}

		@Override
		public void publishDiagnostics( PublishDiagnosticsParams diagnostics ) {
		}

		@Override
		public void showMessage( MessageParams messageParams ) {
		}

		@Override
		public CompletableFuture<MessageActionItem> showMessageRequest( ShowMessageRequestParams requestParams ) {
			return CompletableFuture.completedFuture( null );
		}

		@Override
		public void logMessage( MessageParams message ) {
		}
	}

	private static class RecordingCommandLanguageClient extends NoOpLanguageClient {

		private WorkspaceEdit	lastAppliedEdit;
		private String			lastShowDocumentUri;

		@Override
		public CompletableFuture<ApplyWorkspaceEditResponse> applyEdit( ApplyWorkspaceEditParams params ) {
			lastAppliedEdit = params.getEdit();
			return CompletableFuture.completedFuture( new ApplyWorkspaceEditResponse( true ) );
		}

		@Override
		public CompletableFuture<ShowDocumentResult> showDocument( ShowDocumentParams params ) {
			lastShowDocumentUri = params.getUri();
			return CompletableFuture.completedFuture( new ShowDocumentResult( true ) );
		}
	}
}