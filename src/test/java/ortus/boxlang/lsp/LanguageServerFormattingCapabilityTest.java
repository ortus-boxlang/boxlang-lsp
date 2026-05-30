package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.ConfigurationParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.WorkspaceClientCapabilities;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.services.LanguageClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;

import ortus.boxlang.lsp.formatting.FormattingCapabilityCoordinator;
import ortus.boxlang.lsp.lint.LintConfigLoader;
import ortus.boxlang.lsp.workspace.ProjectContextProvider;

class LanguageServerFormattingCapabilityTest extends BaseTest {

	@TempDir
	Path tempDir;

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

	@Test
	void initializeAdvertisesExecuteCommandProviderForBxlintConfigCreation() throws Exception {
		LanguageServer		server	= new LanguageServer();
		InitializeParams	params	= new InitializeParams();

		params.setCapabilities( new ClientCapabilities() );
		params.setWorkspaceFolders( List.of() );

		InitializeResult result = server.initialize( params ).get();

		assertThat( result.getCapabilities().getExecuteCommandProvider() ).isNotNull();
		assertThat( result.getCapabilities().getExecuteCommandProvider().getCommands() )
		    .contains( BoxLangWorkspaceService.CREATE_BXLINT_CONFIG_COMMAND );
	}

	@Test
	void initializeAdvertisesFormatterConfigCommandWhenRuntimeSupported() throws Exception {
		TestFormattingCapabilityCoordinator coordinator = new TestFormattingCapabilityCoordinator( true );
		coordinator.runtimeSupported = true;
		LanguageServer		server	= new LanguageServer( new BoxLangWorkspaceService( coordinator ), new BoxLangTextDocumentService(),
		    ProjectContextProvider.getInstance(), coordinator );
		InitializeParams	params	= new InitializeParams();

		params.setCapabilities( new ClientCapabilities() );
		params.setWorkspaceFolders( List.of() );

		InitializeResult result = server.initialize( params ).get();

		assertThat( result.getCapabilities().getExecuteCommandProvider().getCommands() )
		    .contains( BoxLangWorkspaceService.CREATE_FORMATTER_CONFIG_COMMAND );
	}

	@Test
	void initializeAdvertisesCFFormatConversionCommandWhenRuntimeSupported() throws Exception {
		TestFormattingCapabilityCoordinator coordinator = new TestFormattingCapabilityCoordinator( true );
		coordinator.runtimeSupported = true;
		LanguageServer		server	= new LanguageServer( new BoxLangWorkspaceService( coordinator ), new BoxLangTextDocumentService(),
		    ProjectContextProvider.getInstance(), coordinator );
		InitializeParams	params	= new InitializeParams();

		params.setCapabilities( new ClientCapabilities() );
		params.setWorkspaceFolders( List.of() );

		InitializeResult result = server.initialize( params ).get();

		assertThat( result.getCapabilities().getExecuteCommandProvider().getCommands() )
		    .contains( BoxLangWorkspaceService.CONVERT_CFFORMAT_CONFIG_COMMAND );
	}

	@Test
	void initializeDoesNotAdvertiseFormatterConfigCommandWhenRuntimeUnsupported() throws Exception {
		TestFormattingCapabilityCoordinator coordinator = new TestFormattingCapabilityCoordinator( true );
		coordinator.runtimeSupported = false;
		LanguageServer		server	= new LanguageServer( new BoxLangWorkspaceService( coordinator ), new BoxLangTextDocumentService(),
		    ProjectContextProvider.getInstance(), coordinator );
		InitializeParams	params	= new InitializeParams();

		params.setCapabilities( new ClientCapabilities() );
		params.setWorkspaceFolders( List.of() );

		InitializeResult result = server.initialize( params ).get();

		assertThat( result.getCapabilities().getExecuteCommandProvider().getCommands() )
		    .doesNotContain( BoxLangWorkspaceService.CREATE_FORMATTER_CONFIG_COMMAND );
	}

	@Test
	void initializeDoesNotAdvertiseCFFormatConversionCommandWhenRuntimeUnsupported() throws Exception {
		TestFormattingCapabilityCoordinator coordinator = new TestFormattingCapabilityCoordinator( true );
		coordinator.runtimeSupported = false;
		LanguageServer		server	= new LanguageServer( new BoxLangWorkspaceService( coordinator ), new BoxLangTextDocumentService(),
		    ProjectContextProvider.getInstance(), coordinator );
		InitializeParams	params	= new InitializeParams();

		params.setCapabilities( new ClientCapabilities() );
		params.setWorkspaceFolders( List.of() );

		InitializeResult result = server.initialize( params ).get();

		assertThat( result.getCapabilities().getExecuteCommandProvider().getCommands() )
		    .doesNotContain( BoxLangWorkspaceService.CONVERT_CFFORMAT_CONFIG_COMMAND );
	}

	@Test
	void initializeLoadsWorkspaceLintConfigAfterConnectRunsWithoutWorkspaceFolders() throws Exception {
		Path					testProjectRoot	= Paths.get( "src/test/resources/test-bx-project" ).toAbsolutePath();
		ProjectContextProvider	provider		= ProjectContextProvider.getInstance();
		List<WorkspaceFolder>	savedFolders	= provider.getWorkspaceFolders();
		LanguageServer			server			= new LanguageServer();
		WorkspaceFolder			folder			= new WorkspaceFolder();

		folder.setUri( testProjectRoot.toUri().toString() );
		folder.setName( "test-bx-project" );

		try {
			provider.setWorkspaceFolders( List.of() );
			LintConfigLoader.invalidate();

			server.connect( new NoOpLanguageClient() );

			InitializeParams params = new InitializeParams();
			params.setCapabilities( new ClientCapabilities() );
			params.setWorkspaceFolders( List.of( folder ) );

			server.initialize( params ).get();

			assertThat( provider.getFileDiagnostics( testProjectRoot.resolve( "ignored-folder/ShouldNotReport.bx" ).toUri() ) ).isEmpty();
		} finally {
			provider.setWorkspaceFolders( savedFolders );
			LintConfigLoader.invalidate();
		}
	}

	@Test
	@DisabledOnOs( OS.WINDOWS ) // Flaky on Windows CI, likely due to timing issues around file watching and test cleanup
	void initializedLoadsInitialClientSettingsBeforeStartupWorkspaceParse() throws Exception {
		ProjectContextProvider			provider		= ProjectContextProvider.getInstance();
		List<WorkspaceFolder>			savedFolders	= provider.getWorkspaceFolders();
		UserSettings					savedSettings	= provider.getUserSettings();
		Path							workspaceRoot	= Files.createDirectories( tempDir.resolve( "workspace-startup-config" ) );
		Path							documentPath	= workspaceRoot.resolve( "InvalidExtendsOnStartup.bx" );
		Path							lintConfig		= workspaceRoot.resolve( ".bxlint.json" );
		WorkspaceFolder					folder			= new WorkspaceFolder();
		DeferredStartupLanguageClient	client			= new DeferredStartupLanguageClient( createLspSettings( true ) );
		LanguageServer					server			= new LanguageServer();

		Files.writeString( documentPath, """
		                                 class extends=\"DoesNotExist\" {
		                                 }
		                                 """ );
		Files.writeString( lintConfig, """
		                               {
		                                 "diagnostics": {
		                                   "invalidExtends": {
		                                     "enabled": true
		                                   }
		                                 }
		                               }
		                               """ );
		folder.setUri( workspaceRoot.toUri().toString() );

		try {
			provider.setWorkspaceFolders( List.of() );
			provider.setUserSettings( new UserSettings() );
			LintConfigLoader.invalidate();

			server.connect( client );

			InitializeParams			params					= new InitializeParams();
			ClientCapabilities			clientCapabilities		= new ClientCapabilities();
			WorkspaceClientCapabilities	workspaceCapabilities	= new WorkspaceClientCapabilities();
			workspaceCapabilities.setConfiguration( true );
			clientCapabilities.setWorkspace( workspaceCapabilities );
			params.setCapabilities( clientCapabilities );
			params.setWorkspaceFolders( List.of( folder ) );

			server.initialize( params ).get();

			// Wait for any pending async operations from initialize() to settle
			Thread.sleep( 500 );

			assertThat( client.getPrematureConfigurationRequests() ).isEqualTo( 0 );
			assertThat( client.getPrematureDiagnosticPublishes() ).isEqualTo( 0 );

			client.markInitialized();
			server.initialized( new InitializedParams() );

			assertThat( awaitPublishedDiagnostics( client, documentPath.toUri().toString(), diagnostics -> diagnostics.stream()
			    .anyMatch( diagnostic -> diagnostic.getCode() != null && "invalidExtends".equals( diagnostic.getCode().getLeft() ) ) ).getDiagnostics() )
			        .isNotEmpty();
			assertThat( client.getConfigurationRequests() ).isEqualTo( 1 );
		} finally {
			provider.remove( documentPath.toUri() );
			provider.setWorkspaceFolders( savedFolders );
			provider.setUserSettings( savedSettings );
			LintConfigLoader.invalidate();
		}
	}

	@Test
	void initializeRecomputesDiagnosticsForDocumentsOpenedBeforeWorkspaceInitialization() throws Exception {
		ProjectContextProvider	provider		= ProjectContextProvider.getInstance();
		List<WorkspaceFolder>	savedFolders	= provider.getWorkspaceFolders();
		UserSettings			savedSettings	= provider.getUserSettings();
		Path					workspaceRoot	= Files.createDirectories( tempDir.resolve( "workspace-open-before-initialize" ) );
		Path					documentPath	= workspaceRoot.resolve( "InvalidExtendsOpenOnStartup.bx" );
		WorkspaceFolder			folder			= new WorkspaceFolder();
		RecordingLanguageClient	client			= new RecordingLanguageClient( new JsonObject() );
		LanguageServer			server			= new LanguageServer();

		Files.writeString( documentPath, """
		                                 class extends=\"DoesNotExist\" {
		                                 }
		                                 """ );
		folder.setUri( workspaceRoot.toUri().toString() );

		try {
			provider.setWorkspaceFolders( List.of() );
			provider.setUserSettings( new UserSettings() );
			LintConfigLoader.invalidate();

			server.connect( client );
			server.getTextDocumentService().didOpen( new DidOpenTextDocumentParams(
			    new TextDocumentItem( documentPath.toUri().toString(), "boxlang", 1, Files.readString( documentPath ) ) ) );

			InitializeParams params = new InitializeParams();
			params.setCapabilities( new ClientCapabilities() );
			params.setWorkspaceFolders( List.of( folder ) );

			server.initialize( params ).get();
			server.initialized( new InitializedParams() );

			assertThat( awaitPublishedDiagnostics( client, documentPath.toUri().toString(), diagnostics -> diagnostics.stream()
			    .anyMatch( diagnostic -> diagnostic.getCode() != null && "invalidExtends".equals( diagnostic.getCode().getLeft() ) ) ).getDiagnostics() )
			        .isNotEmpty();
		} finally {
			provider.remove( documentPath.toUri() );
			provider.setWorkspaceFolders( savedFolders );
			provider.setUserSettings( savedSettings );
			LintConfigLoader.invalidate();
		}
	}

	@Test
	void openTemplateAfterInitializationPublishesInvalidExtendsOnOpeningTagOnly() throws Exception {
		ProjectContextProvider	provider		= ProjectContextProvider.getInstance();
		List<WorkspaceFolder>	savedFolders	= provider.getWorkspaceFolders();
		UserSettings			savedSettings	= provider.getUserSettings();
		Path					workspaceRoot	= Files.createDirectories( tempDir.resolve( "workspace-open-template-after-initialize" ) );
		Path					documentPath	= workspaceRoot.resolve( "InvalidExtendsTemplate.cfm" );
		WorkspaceFolder			folder			= new WorkspaceFolder();
		RecordingLanguageClient	client			= new RecordingLanguageClient( createLspSettings( true ) );
		LanguageServer			server			= new LanguageServer();

		Files.writeString( documentPath, """
		                                 <cfcomponent extends=\"waht\">

		                                 </cfcomponent>
		                                 """ );
		folder.setUri( workspaceRoot.toUri().toString() );

		try {
			provider.setWorkspaceFolders( List.of() );
			provider.setUserSettings( new UserSettings() );
			LintConfigLoader.invalidate();

			server.connect( client );

			InitializeParams			params					= new InitializeParams();
			ClientCapabilities			clientCapabilities		= new ClientCapabilities();
			WorkspaceClientCapabilities	workspaceCapabilities	= new WorkspaceClientCapabilities();
			workspaceCapabilities.setConfiguration( true );
			clientCapabilities.setWorkspace( workspaceCapabilities );
			params.setCapabilities( clientCapabilities );
			params.setWorkspaceFolders( List.of( folder ) );

			server.initialize( params ).get();
			server.initialized( new InitializedParams() );
			server.getTextDocumentService().didOpen( new DidOpenTextDocumentParams(
			    new TextDocumentItem( documentPath.toUri().toString(), "boxlang", 1, Files.readString( documentPath ) ) ) );

			provider.flushPublishDebouncer();
			PublishDiagnosticsParams paramsPublished = awaitPublishedDiagnostics( client, documentPath.toUri().toString(), diagnostics -> diagnostics.stream()
			    .anyMatch( diagnostic -> diagnostic.getCode() != null && "invalidExtends".equals( diagnostic.getCode().getLeft() ) ) );

			assertThat( paramsPublished.getDiagnostics() ).isNotEmpty();
			assertThat( paramsPublished.getDiagnostics().stream().allMatch( diagnostic -> diagnostic.getRange().getEnd().getLine() == 0 ) ).isTrue();
		} finally {
			provider.remove( documentPath.toUri() );
			provider.setWorkspaceFolders( savedFolders );
			provider.setUserSettings( savedSettings );
			LintConfigLoader.invalidate();
		}
	}

	@Test
	void saveTemplateAfterOpenPublishesInvalidExtendsOnOpeningTagOnly() throws Exception {
		ProjectContextProvider	provider		= ProjectContextProvider.getInstance();
		List<WorkspaceFolder>	savedFolders	= provider.getWorkspaceFolders();
		UserSettings			savedSettings	= provider.getUserSettings();
		Path					workspaceRoot	= Files.createDirectories( tempDir.resolve( "workspace-save-template-after-open" ) );
		Path					documentPath	= workspaceRoot.resolve( "InvalidExtendsTemplate.cfm" );
		WorkspaceFolder			folder			= new WorkspaceFolder();
		RecordingLanguageClient	client			= new RecordingLanguageClient( createLspSettings( true ) );
		LanguageServer			server			= new LanguageServer();

		Files.writeString( documentPath, """
		                                 <cfcomponent extends=\"waht\">

		                                 </cfcomponent>
		                                 """ );
		folder.setUri( workspaceRoot.toUri().toString() );

		try {
			provider.setWorkspaceFolders( List.of() );
			provider.setUserSettings( new UserSettings() );
			LintConfigLoader.invalidate();

			server.connect( client );

			InitializeParams			params					= new InitializeParams();
			ClientCapabilities			clientCapabilities		= new ClientCapabilities();
			WorkspaceClientCapabilities	workspaceCapabilities	= new WorkspaceClientCapabilities();
			workspaceCapabilities.setConfiguration( true );
			clientCapabilities.setWorkspace( workspaceCapabilities );
			params.setCapabilities( clientCapabilities );
			params.setWorkspaceFolders( List.of( folder ) );

			server.initialize( params ).get();
			server.initialized( new InitializedParams() );
			server.getTextDocumentService().didOpen( new DidOpenTextDocumentParams(
			    new TextDocumentItem( documentPath.toUri().toString(), "boxlang", 1, Files.readString( documentPath ) ) ) );
			client.publishedDiagnostics.clear();

			DidSaveTextDocumentParams saveParams = new DidSaveTextDocumentParams();
			saveParams.setTextDocument( new TextDocumentIdentifier( documentPath.toUri().toString() ) );
			server.getTextDocumentService().didSave( saveParams );

			provider.flushPublishDebouncer();
			PublishDiagnosticsParams paramsPublished = awaitPublishedDiagnostics( client, documentPath.toUri().toString(), diagnostics -> diagnostics.stream()
			    .anyMatch( diagnostic -> diagnostic.getCode() != null && "invalidExtends".equals( diagnostic.getCode().getLeft() ) ) );

			assertThat( paramsPublished.getDiagnostics() ).isNotEmpty();
			assertThat( paramsPublished.getDiagnostics().stream().allMatch( diagnostic -> diagnostic.getRange().getEnd().getLine() == 0 ) ).isTrue();
		} finally {
			provider.remove( documentPath.toUri() );
			provider.setWorkspaceFolders( savedFolders );
			provider.setUserSettings( savedSettings );
			LintConfigLoader.invalidate();
		}
	}

	@Test
	void saveApplicationBxAfterOpenRefreshesMappedExtendsDiagnosticsWithoutRestart() throws Exception {
		ProjectContextProvider	provider		= ProjectContextProvider.getInstance();
		List<WorkspaceFolder>	savedFolders	= provider.getWorkspaceFolders();
		UserSettings			savedSettings	= provider.getUserSettings();
		Path					workspaceRoot	= Files.createDirectories( tempDir.resolve( "workspace-save-application-mapping" ) );
		Path					applicationPath	= workspaceRoot.resolve( "Application.bx" );
		Path					mappedClassPath	= workspaceRoot.resolve( "src/potentialMapping/Base.bx" );
		Path					potentialPath	= workspaceRoot.resolve( "mappingTests/PotentialMapping.bx" );
		WorkspaceFolder			folder			= new WorkspaceFolder();
		RecordingLanguageClient	client			= new RecordingLanguageClient( createLspSettings( true ) );
		LanguageServer			server			= new LanguageServer();

		Files.createDirectories( mappedClassPath.getParent() );
		Files.createDirectories( potentialPath.getParent() );
		Files.writeString( applicationPath, "class {\n}\n" );
		Files.writeString( mappedClassPath, "class {}\n" );
		Files.writeString( potentialPath, "class extends=\"potentialMapping.Base\" {\n}\n" );
		folder.setUri( workspaceRoot.toUri().toString() );

		try {
			provider.setWorkspaceFolders( List.of() );
			provider.setUserSettings( new UserSettings() );
			LintConfigLoader.invalidate();

			server.connect( client );

			InitializeParams			params					= new InitializeParams();
			ClientCapabilities			clientCapabilities		= new ClientCapabilities();
			WorkspaceClientCapabilities	workspaceCapabilities	= new WorkspaceClientCapabilities();
			workspaceCapabilities.setConfiguration( true );
			clientCapabilities.setWorkspace( workspaceCapabilities );
			params.setCapabilities( clientCapabilities );
			params.setWorkspaceFolders( List.of( folder ) );

			server.initialize( params ).get();
			server.initialized( new InitializedParams() );
			server.getTextDocumentService().didOpen( new DidOpenTextDocumentParams(
			    new TextDocumentItem( potentialPath.toUri().toString(), "boxlang", 1, Files.readString( potentialPath ) ) ) );
			server.getTextDocumentService().didOpen( new DidOpenTextDocumentParams(
			    new TextDocumentItem( applicationPath.toUri().toString(), "boxlang", 1, Files.readString( applicationPath ) ) ) );

			awaitPublishedDiagnostics( client, potentialPath.toUri().toString(), diagnostics -> diagnostics.stream()
			    .anyMatch( diagnostic -> diagnostic.getCode() != null && "invalidExtends".equals( diagnostic.getCode().getLeft() ) ) );

			Files.writeString( applicationPath, "class {\n\tthis.mappings[ \"potentialMapping\" ] = \"src/potentialMapping\";\n}\n" );

			DidSaveTextDocumentParams saveParams = new DidSaveTextDocumentParams();
			saveParams.setTextDocument( new TextDocumentIdentifier( applicationPath.toUri().toString() ) );
			server.getTextDocumentService().didSave( saveParams );

			provider.flushPublishDebouncer();
			PublishDiagnosticsParams refreshed = awaitPublishedDiagnostics( client, potentialPath.toUri().toString(), diagnostics -> diagnostics.stream()
			    .noneMatch( diagnostic -> diagnostic.getCode() != null && "invalidExtends".equals( diagnostic.getCode().getLeft() ) ) );

			assertThat( refreshed.getDiagnostics().stream()
			    .noneMatch( diagnostic -> diagnostic.getCode() != null && "invalidExtends".equals( diagnostic.getCode().getLeft() ) ) ).isTrue();
		} finally {
			provider.trackDocumentClose( potentialPath.toUri() );
			provider.trackDocumentClose( applicationPath.toUri() );
			provider.setWorkspaceFolders( savedFolders );
			provider.setUserSettings( savedSettings );
			LintConfigLoader.invalidate();
		}
	}

	@Test
	void saveBoxlangJsonAfterOpenRefreshesMappedExtendsDiagnosticsWithoutRestart() throws Exception {
		ProjectContextProvider	provider		= ProjectContextProvider.getInstance();
		List<WorkspaceFolder>	savedFolders	= provider.getWorkspaceFolders();
		UserSettings			savedSettings	= provider.getUserSettings();
		Path					workspaceRoot	= Files.createDirectories( tempDir.resolve( "workspace-save-boxlang-mapping" ) );
		Path					boxlangJsonPath	= workspaceRoot.resolve( "boxlang.json" );
		Path					mappedClassPath	= workspaceRoot.resolve( "src/potentialMapping/Base.bx" );
		Path					potentialPath	= workspaceRoot.resolve( "mappingTests/PotentialMapping.bx" );
		WorkspaceFolder			folder			= new WorkspaceFolder();
		RecordingLanguageClient	client			= new RecordingLanguageClient( createLspSettings( true ) );
		LanguageServer			server			= new LanguageServer();

		Files.createDirectories( mappedClassPath.getParent() );
		Files.createDirectories( potentialPath.getParent() );
		Files.writeString( boxlangJsonPath, "{}\n" );
		Files.writeString( mappedClassPath, "class {}\n" );
		Files.writeString( potentialPath, "class extends=\"potentialMapping.Base\" {\n}\n" );
		folder.setUri( workspaceRoot.toUri().toString() );

		try {
			provider.setWorkspaceFolders( List.of() );
			provider.setUserSettings( new UserSettings() );
			LintConfigLoader.invalidate();

			server.connect( client );

			InitializeParams			params					= new InitializeParams();
			ClientCapabilities			clientCapabilities		= new ClientCapabilities();
			WorkspaceClientCapabilities	workspaceCapabilities	= new WorkspaceClientCapabilities();
			workspaceCapabilities.setConfiguration( true );
			clientCapabilities.setWorkspace( workspaceCapabilities );
			params.setCapabilities( clientCapabilities );
			params.setWorkspaceFolders( List.of( folder ) );

			server.initialize( params ).get();
			server.initialized( new InitializedParams() );
			server.getTextDocumentService().didOpen( new DidOpenTextDocumentParams(
			    new TextDocumentItem( potentialPath.toUri().toString(), "boxlang", 1, Files.readString( potentialPath ) ) ) );
			server.getTextDocumentService().didOpen( new DidOpenTextDocumentParams(
			    new TextDocumentItem( boxlangJsonPath.toUri().toString(), "json", 1, Files.readString( boxlangJsonPath ) ) ) );

			awaitPublishedDiagnostics( client, potentialPath.toUri().toString(), diagnostics -> diagnostics.stream()
			    .anyMatch( diagnostic -> diagnostic.getCode() != null && "invalidExtends".equals( diagnostic.getCode().getLeft() ) ) );

			Files.writeString( boxlangJsonPath, "{\n  \"mappings\": {\n    \"/potentialMapping\": \"./src/potentialMapping\"\n  }\n}\n" );

			DidSaveTextDocumentParams saveParams = new DidSaveTextDocumentParams();
			saveParams.setTextDocument( new TextDocumentIdentifier( boxlangJsonPath.toUri().toString() ) );
			server.getTextDocumentService().didSave( saveParams );

			provider.flushPublishDebouncer();
			PublishDiagnosticsParams refreshed = awaitPublishedDiagnostics( client, potentialPath.toUri().toString(), diagnostics -> diagnostics.stream()
			    .noneMatch( diagnostic -> diagnostic.getCode() != null && "invalidExtends".equals( diagnostic.getCode().getLeft() ) ) );

			assertThat( refreshed.getDiagnostics().stream()
			    .noneMatch( diagnostic -> diagnostic.getCode() != null && "invalidExtends".equals( diagnostic.getCode().getLeft() ) ) ).isTrue();

			Files.writeString( boxlangJsonPath, "{}\n" );
			server.getTextDocumentService().didSave( saveParams );

			provider.flushPublishDebouncer();
			PublishDiagnosticsParams reverted = awaitPublishedDiagnostics( client, potentialPath.toUri().toString(), diagnostics -> diagnostics.stream()
			    .anyMatch( diagnostic -> diagnostic.getCode() != null && "invalidExtends".equals( diagnostic.getCode().getLeft() ) ) );

			assertThat( reverted.getDiagnostics().stream()
			    .anyMatch( diagnostic -> diagnostic.getCode() != null && "invalidExtends".equals( diagnostic.getCode().getLeft() ) ) ).isTrue();
		} finally {
			provider.trackDocumentClose( potentialPath.toUri() );
			provider.trackDocumentClose( boxlangJsonPath.toUri() );
			provider.setWorkspaceFolders( savedFolders );
			provider.setUserSettings( savedSettings );
			LintConfigLoader.invalidate();
		}
	}

	@Test
	void initializedDoesNotBlockWhileWaitingForInitialConfigurationResponse() throws Exception {
		ProjectContextProvider				provider		= ProjectContextProvider.getInstance();
		List<WorkspaceFolder>				savedFolders	= provider.getWorkspaceFolders();
		UserSettings						savedSettings	= provider.getUserSettings();
		Path								workspaceRoot	= Files.createDirectories( tempDir.resolve( "workspace-initialized-async" ) );
		Path								documentPath	= workspaceRoot.resolve( "InvalidExtendsAfterAsyncConfig.bx" );
		WorkspaceFolder						folder			= new WorkspaceFolder();
		CompletableFuture<List<Object>>		configFuture	= new CompletableFuture<>();
		AsyncConfigurationLanguageClient	client			= new AsyncConfigurationLanguageClient( configFuture );
		LanguageServer						server			= new LanguageServer();
		ExecutorService						executor		= Executors.newSingleThreadExecutor();

		Files.writeString( documentPath, """
		                                 class extends=\"DoesNotExist\" {
		                                 }
		                                 """ );
		folder.setUri( workspaceRoot.toUri().toString() );

		try {
			provider.setWorkspaceFolders( List.of() );
			provider.setUserSettings( new UserSettings() );
			LintConfigLoader.invalidate();

			server.connect( client );

			InitializeParams			params					= new InitializeParams();
			ClientCapabilities			clientCapabilities		= new ClientCapabilities();
			WorkspaceClientCapabilities	workspaceCapabilities	= new WorkspaceClientCapabilities();
			workspaceCapabilities.setConfiguration( true );
			clientCapabilities.setWorkspace( workspaceCapabilities );
			params.setCapabilities( clientCapabilities );
			params.setWorkspaceFolders( List.of( folder ) );

			server.initialize( params ).get();

			Future<?> initializedCall = executor.submit( () -> server.initialized( new InitializedParams() ) );
			try {
				initializedCall.get( 200, TimeUnit.MILLISECONDS );
			} catch ( TimeoutException e ) {
				throw new AssertionError( "initialized() blocked while waiting for workspace/configuration", e );
			}

			configFuture.complete( List.<Object>of( createLspSettings( true ), new JsonObject() ) );

			assertThat( awaitPublishedDiagnostics( client, documentPath.toUri().toString(), diagnostics -> diagnostics.stream()
			    .anyMatch( diagnostic -> diagnostic.getCode() != null && "invalidExtends".equals( diagnostic.getCode().getLeft() ) ) ).getDiagnostics() )
			        .isNotEmpty();
		} finally {
			configFuture.complete( List.<Object>of( createLspSettings( true ), new JsonObject() ) );
			executor.shutdownNow();
			provider.remove( documentPath.toUri() );
			provider.setWorkspaceFolders( savedFolders );
			provider.setUserSettings( savedSettings );
			LintConfigLoader.invalidate();
		}
	}

	private static JsonObject createLspSettings( boolean enableBackgroundParsing ) {
		JsonObject settings = new JsonObject();
		settings.addProperty( "enableBackgroundParsing", enableBackgroundParsing );
		return settings;
	}

	private static PublishDiagnosticsParams awaitPublishedDiagnostics(
	    RecordingLanguageClient client,
	    String uri,
	    java.util.function.Predicate<List<org.eclipse.lsp4j.Diagnostic>> matcher ) throws InterruptedException {
		for ( int attempt = 0; attempt < 20; attempt++ ) {
			PublishDiagnosticsParams params = client.publishedDiagnostics.get( uri );
			if ( params != null && matcher.test( params.getDiagnostics() ) ) {
				return params;
			}
			Thread.sleep( 25 );
		}
		throw new AssertionError( "Timed out waiting for diagnostics publish for " + uri );
	}

	private static class TestFormattingCapabilityCoordinator extends FormattingCapabilityCoordinator {

		private final boolean	shouldAdvertiseStatically;
		private boolean			runtimeSupported	= true;

		private TestFormattingCapabilityCoordinator( boolean shouldAdvertiseStatically ) {
			this.shouldAdvertiseStatically = shouldAdvertiseStatically;
		}

		@Override
		public boolean shouldAdvertiseFormattingStatically() {
			return shouldAdvertiseStatically;
		}

		@Override
		public boolean isRuntimeSupported() {
			return runtimeSupported;
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

	private static class RecordingLanguageClient extends NoOpLanguageClient {

		private final JsonObject											configuration;
		private final ConcurrentHashMap<String, PublishDiagnosticsParams>	publishedDiagnostics	= new ConcurrentHashMap<>();

		private RecordingLanguageClient( JsonObject configuration ) {
			this.configuration = configuration;
		}

		@Override
		public CompletableFuture<List<Object>> configuration( ConfigurationParams params ) {
			return CompletableFuture.completedFuture( List.<Object>of( configuration, new JsonObject() ) );
		}

		@Override
		public void publishDiagnostics( PublishDiagnosticsParams diagnostics ) {
			publishedDiagnostics.put( diagnostics.getUri(), diagnostics );
		}
	}

	private static final class DeferredStartupLanguageClient extends RecordingLanguageClient {

		private volatile boolean	initialized;
		private volatile int		configurationRequests;
		private volatile int		prematureConfigurationRequests;
		private volatile int		prematureDiagnosticPublishes;

		private DeferredStartupLanguageClient( JsonObject configuration ) {
			super( configuration );
		}

		private void markInitialized() {
			initialized = true;
		}

		private int getConfigurationRequests() {
			return configurationRequests;
		}

		private int getPrematureConfigurationRequests() {
			return prematureConfigurationRequests;
		}

		private int getPrematureDiagnosticPublishes() {
			return prematureDiagnosticPublishes;
		}

		@Override
		public CompletableFuture<List<Object>> configuration( ConfigurationParams params ) {
			if ( !initialized ) {
				prematureConfigurationRequests++;
				return CompletableFuture.completedFuture( List.of( new JsonObject(), new JsonObject() ) );
			}
			configurationRequests++;
			return super.configuration( params );
		}

		@Override
		public void publishDiagnostics( PublishDiagnosticsParams diagnostics ) {
			if ( !initialized ) {
				prematureDiagnosticPublishes++;
				return;
			}
			super.publishDiagnostics( diagnostics );
		}
	}

	private static final class AsyncConfigurationLanguageClient extends RecordingLanguageClient {

		private final CompletableFuture<List<Object>> configurationFuture;

		private AsyncConfigurationLanguageClient( CompletableFuture<List<Object>> configurationFuture ) {
			super( new JsonObject() );
			this.configurationFuture = configurationFuture;
		}

		@Override
		public CompletableFuture<List<Object>> configuration( ConfigurationParams params ) {
			return configurationFuture;
		}
	}
}