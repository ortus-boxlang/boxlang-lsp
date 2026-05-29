package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesRegistrationOptions;
import org.eclipse.lsp4j.FormattingOptions;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.services.LanguageClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;

import ortus.boxlang.lsp.formatting.FormatterConfigResolver;
import ortus.boxlang.lsp.formatting.FormattingCapabilityCoordinator;
import ortus.boxlang.lsp.formatting.PrettyPrintRuntimeAdapter;
import ortus.boxlang.lsp.lint.LintConfig;
import ortus.boxlang.lsp.lint.LintConfigLoader;
import ortus.boxlang.lsp.workspace.ProjectContextProvider;

class ProjectContextProviderFormattingTest extends BaseTest {

	@TempDir
	Path tempDir;

	@Test
	void handleConfigFileChangeRefreshesFormattingCapabilityForBxlintChanges() throws Exception {
		ProjectContextProvider						provider			= ProjectContextProvider.getInstance();
		List<WorkspaceFolder>						savedFolders		= provider.getWorkspaceFolders();
		UserSettings								savedSettings		= provider.getUserSettings();
		FormattingCapabilityCoordinator				savedCoordinator	= provider.getFormattingCapabilityCoordinator();

		RecordingFormattingCapabilityCoordinator	coordinator			= new RecordingFormattingCapabilityCoordinator();
		Path										lintConfig			= Files.writeString( tempDir.resolve( ".bxlint.json" ), "{}" );

		WorkspaceFolder								folder				= new WorkspaceFolder();
		folder.setUri( tempDir.toUri().toString() );

		try {
			provider.setWorkspaceFolders( List.of( folder ) );
			provider.setUserSettings(
			    UserSettings.fromChangeConfigurationParams( new NoOpLanguageClient(), new DidChangeConfigurationParams( new JsonObject() ) ) );
			provider.setFormattingCapabilityCoordinator( coordinator );

			provider.handleConfigFileChange( lintConfig.toUri() );

			assertThat( coordinator.refreshCalls ).isEqualTo( 1 );
		} finally {
			provider.setWorkspaceFolders( savedFolders );
			provider.setUserSettings( savedSettings );
			provider.setFormattingCapabilityCoordinator( savedCoordinator );
		}
	}

	@Test
	void handleConfigFileChangeInvalidatesFormatterConfigResolverForBxformatChanges() throws Exception {
		ProjectContextProvider				provider		= ProjectContextProvider.getInstance();
		List<WorkspaceFolder>				savedFolders	= provider.getWorkspaceFolders();
		FormatterConfigResolver				savedResolver	= provider.getFormatterConfigResolver();
		RecordingFormatterConfigResolver	resolver		= new RecordingFormatterConfigResolver();

		Path								workspaceRoot	= Files.createDirectories( tempDir.resolve( "workspace-resolver" ) );
		Path								formatterConfig	= Files.writeString( workspaceRoot.resolve( ".bxformat.json" ), "{}" );
		WorkspaceFolder						folder			= new WorkspaceFolder();
		folder.setUri( workspaceRoot.toUri().toString() );

		try {
			provider.setWorkspaceFolders( List.of( folder ) );
			provider.setFormatterConfigResolver( resolver );

			provider.handleConfigFileChange( formatterConfig.toUri() );

			assertThat( resolver.invalidatedPaths ).containsExactly( formatterConfig );
		} finally {
			provider.setWorkspaceFolders( savedFolders );
			provider.setFormatterConfigResolver( savedResolver );
		}
	}

	@Test
	void handleConfigFileChangeReparsesClosedFilesWhenLintRulesChange() throws Exception {
		ProjectContextProvider	provider		= ProjectContextProvider.getInstance();
		List<WorkspaceFolder>	savedFolders	= provider.getWorkspaceFolders();
		UserSettings			savedSettings	= provider.getUserSettings();

		Path					workspaceRoot	= Files.createDirectories( tempDir.resolve( "workspace-lint-refresh" ) );
		Path					documentPath	= workspaceRoot.resolve( "UnusedVariableExample.bx" );
		Path					lintConfig		= workspaceRoot.resolve( ".bxlint.json" );
		WorkspaceFolder			folder			= new WorkspaceFolder();

		Files.writeString( documentPath, """
		                                 class {
		                                 	function demo() {
		                                 		var unused = 1;
		                                 	}
		                                 }
		                                 """ );
		Files.writeString( lintConfig, "{}" );
		folder.setUri( workspaceRoot.toUri().toString() );

		try {
			provider.setWorkspaceFolders( List.of( folder ) );
			provider.setUserSettings( new UserSettings() );
			LintConfigLoader.invalidate();

			assertThat( provider.getFileDiagnostics( documentPath.toUri() ).stream()
			    .anyMatch( diagnostic -> diagnostic.getCode() != null && "unusedVariable".equals( diagnostic.getCode().getLeft() ) ) ).isTrue();

			Files.writeString( lintConfig, """
			                               {
			                                 "diagnostics": {
			                                   "unusedVariable": {
			                                     "enabled": false
			                                   }
			                                 }
			                               }
			                               """ );

			provider.handleConfigFileChange( lintConfig.toUri() );

			assertThat( provider.getFileDiagnostics( documentPath.toUri() ).stream()
			    .anyMatch( diagnostic -> diagnostic.getCode() != null && "unusedVariable".equals( diagnostic.getCode().getLeft() ) ) ).isFalse();
		} finally {
			provider.remove( documentPath.toUri() );
			provider.setWorkspaceFolders( savedFolders );
			provider.setUserSettings( savedSettings );
			LintConfigLoader.invalidate();
		}
	}

	@Test
	@DisabledOnOs( OS.WINDOWS )
	void handleConfigFileChangeRebuildsClosedFileWorkspaceDiagnosticsWhenRuleBecomesEnabledWithBackgroundParsingDisabled() throws Exception {
		ProjectContextProvider	provider		= ProjectContextProvider.getInstance();
		List<WorkspaceFolder>	savedFolders	= provider.getWorkspaceFolders();
		UserSettings			savedSettings	= provider.getUserSettings();

		Path					workspaceRoot	= Files.createDirectories( tempDir.resolve( "workspace-lint-invalid-extends" ) );
		Path					documentPath	= workspaceRoot.resolve( "InvalidExtendsExample.bx" );
		Path					lintConfig		= workspaceRoot.resolve( ".bxlint.json" );
		WorkspaceFolder			folder			= new WorkspaceFolder();

		Files.writeString( documentPath, """
		                                 class extends="DoesNotExist" {
		                                 }
		                                 """ );
		Files.writeString( lintConfig, """
		                               {
		                                 "diagnostics": {
		                                   "invalidExtends": {
		                                     "enabled": false
		                                   }
		                                 }
		                               }
		                               """ );
		folder.setUri( workspaceRoot.toUri().toString() );

		try {
			provider.setWorkspaceFolders( List.of( folder ) );
			provider.setUserSettings( new UserSettings() );
			LintConfigLoader.invalidate();

			assertThat( provider.getFileDiagnostics( documentPath.toUri() ).stream()
			    .anyMatch( diagnostic -> diagnostic.getCode() != null && "invalidExtends".equals( diagnostic.getCode().getLeft() ) ) ).isFalse();

			Files.writeString( lintConfig, """
			                               {
			                                 "diagnostics": {
			                                   "invalidExtends": {
			                                     "enabled": true
			                                   }
			                                 }
			                               }
			                               """ );

			// Synchronously wait for the lint config change (including workspace parse) to complete
			provider.handleConfigFileChange( lintConfig.toUri() ).get( 10, java.util.concurrent.TimeUnit.SECONDS );
			// Flush any pending debounced publishes
			provider.flushPublishDebouncer();

			assertThat( awaitCachedDiagnostic( provider, documentPath.toUri(), "invalidExtends" ) ).isTrue();
		} finally {
			provider.remove( documentPath.toUri() );
			provider.setWorkspaceFolders( savedFolders );
			provider.setUserSettings( savedSettings );
			LintConfigLoader.invalidate();
		}
	}

	@Test
	@DisabledOnOs( OS.WINDOWS )
	void handleConfigFileChangePublishesClosedFileDiagnosticsWhenRuleBecomesEnabled() throws Exception {
		ProjectContextProvider	provider		= ProjectContextProvider.getInstance();
		List<WorkspaceFolder>	savedFolders	= provider.getWorkspaceFolders();
		UserSettings			savedSettings	= provider.getUserSettings();
		RecordingLanguageClient	client			= new RecordingLanguageClient();

		Path					workspaceRoot	= Files.createDirectories( tempDir.resolve( "workspace-lint-invalid-extends-publish" ) );
		Path					documentPath	= workspaceRoot.resolve( "InvalidExtendsPublishExample.bx" );
		Path					lintConfig		= workspaceRoot.resolve( ".bxlint.json" );
		WorkspaceFolder			folder			= new WorkspaceFolder();

		Files.writeString( documentPath, """
		                                 class extends="DoesNotExist" {
		                                 }
		                                 """ );
		Files.writeString( lintConfig, """
		                               {
		                                 "diagnostics": {
		                                   "invalidExtends": {
		                                     "enabled": false
		                                   }
		                                 }
		                               }
		                               """ );
		folder.setUri( workspaceRoot.toUri().toString() );

		try {
			provider.setWorkspaceFolders( List.of( folder ) );
			provider.setUserSettings( new UserSettings() );
			provider.setLanguageClient( client );
			LintConfigLoader.invalidate();

			assertThat( provider.getFileDiagnostics( documentPath.toUri() ).stream()
			    .anyMatch( diagnostic -> diagnostic.getCode() != null && "invalidExtends".equals( diagnostic.getCode().getLeft() ) ) ).isFalse();
			client.publishedDiagnostics.clear();

			Files.writeString( lintConfig, """
			                               {
			                                 "diagnostics": {
			                                   "invalidExtends": {
			                                     "enabled": true
			                                   }
			                                 }
			                               }
			                               """ );

			// Synchronously wait for the lint config change (including workspace parse) to complete
			provider.handleConfigFileChange( lintConfig.toUri() ).get( 10, java.util.concurrent.TimeUnit.SECONDS );
			// Flush any pending debounced publishes
			provider.flushPublishDebouncer();

			assertThat( awaitPublishedDiagnostics( client, documentPath.toUri().toString(), diagnostics -> diagnostics.stream()
			    .anyMatch( diagnostic -> diagnostic.getCode() != null && "invalidExtends".equals( diagnostic.getCode().getLeft() ) ) ).getDiagnostics() )
			    .isNotEmpty();
		} finally {
			provider.remove( documentPath.toUri() );
			provider.setWorkspaceFolders( savedFolders );
			provider.setUserSettings( savedSettings );
			provider.setLanguageClient( null );
			LintConfigLoader.invalidate();
		}
	}

	@Test
	void watchLspConfigRegistersFormatterConfigWatchers() throws Exception {
		ProjectContextProvider	provider		= ProjectContextProvider.getInstance();
		List<WorkspaceFolder>	savedFolders	= provider.getWorkspaceFolders();
		RecordingLanguageClient	client			= new RecordingLanguageClient();

		Path					workspaceRoot	= Files.createDirectories( tempDir.resolve( "workspace-watchers" ) );
		WorkspaceFolder			folder			= new WorkspaceFolder();
		folder.setUri( workspaceRoot.toUri().toString() );

		try {
			provider.setWorkspaceFolders( List.of( folder ) );
			provider.setLanguageClient( client );

			provider.watchLSPConfig();

			assertThat( client.registrationRequests ).hasSize( 1 );
			DidChangeWatchedFilesRegistrationOptions options = ( DidChangeWatchedFilesRegistrationOptions ) client.registrationRequests.getFirst()
			    .getRegistrations().getFirst().getRegisterOptions();
			assertThat( options.getWatchers().stream().map( watcher -> watcher.getGlobPattern().getLeft() ).toList() )
			    .containsAtLeast( ".bxlint.json", "**/.bxformat.json", "**/.cfformat.json" );
		} finally {
			provider.setWorkspaceFolders( savedFolders );
			provider.setLanguageClient( null );
		}
	}

	@Test
	void formatDocumentUsesPrettyPrintAdapterWhenExperimentalFormatterEnabled() throws Exception {
		ProjectContextProvider				provider			= ProjectContextProvider.getInstance();
		List<WorkspaceFolder>				savedFolders		= provider.getWorkspaceFolders();
		UserSettings						savedSettings		= provider.getUserSettings();
		PrettyPrintRuntimeAdapter			savedRuntimeAdapter	= provider.getPrettyPrintRuntimeAdapter();

		RecordingPrettyPrintRuntimeAdapter	runtimeAdapter		= new RecordingPrettyPrintRuntimeAdapter();
		Path								workspaceRoot		= tempDir.resolve( "workspace" );
		Path								nestedDir			= workspaceRoot.resolve( "nested" );
		Path								documentPath		= nestedDir.resolve( "Sample.bx" );
		Path								formatterConfigPath	= workspaceRoot.resolve( ".bxformat.json" );
		String								source				= """
		                                                          class {

		                                                          	function foo() {
		                                                          		return \"bar\";
		                                                          	}

		                                                          }
		                                                          """;

		Files.createDirectories( nestedDir );
		Files.writeString( workspaceRoot.resolve( ".bxlint.json" ), """
		                                                            {
		                                                              \"formatting\": {
		                                                                \"experimental\": {
		                                                                  \"enabled\": true
		                                                                }
		                                                              }
		                                                            }
		                                                            """ );
		Files.writeString( formatterConfigPath, "{}" );
		Files.writeString( documentPath, source );

		WorkspaceFolder folder = new WorkspaceFolder();
		folder.setUri( workspaceRoot.toUri().toString() );

		try {
			provider.setWorkspaceFolders( List.of( folder ) );
			provider.setUserSettings( new UserSettings() );
			provider.setPrettyPrintRuntimeAdapter( runtimeAdapter );
			provider.trackDocumentOpen( documentPath.toUri(), source );
			LintConfigLoader.invalidate();

			List<? extends TextEdit>	edits	= provider.formatDocument( documentPath.toUri(), new FormattingOptions( 2, true ) );
			TextEdit					edit	= edits.getFirst();

			assertThat( edits ).hasSize( 1 );
			assertThat( edit ).isNotNull();
			if ( edit == null ) {
				return;
			}
			assertThat( edit.getNewText() ).isEqualTo( "formatted output" );
			assertThat( edit.getRange() ).isNotNull();
			assertThat( edit.getRange().getStart().getLine() ).isEqualTo( 0 );
			assertThat( edit.getRange().getStart().getCharacter() ).isEqualTo( 0 );
			assertThat( runtimeAdapter.prettyPrintCalls ).isEqualTo( 1 );
			assertThat( runtimeAdapter.lastConfigPath ).isEqualTo( formatterConfigPath );
		} finally {
			provider.trackDocumentClose( documentPath.toUri() );
			provider.setWorkspaceFolders( savedFolders );
			provider.setUserSettings( savedSettings );
			provider.setPrettyPrintRuntimeAdapter( savedRuntimeAdapter );
			LintConfigLoader.invalidate();
		}
	}

	@Test
	void handleConfigFileChangeClearsPublishedDiagnosticsWhenFileBecomesExcluded() throws Exception {
		ProjectContextProvider	provider		= ProjectContextProvider.getInstance();
		List<WorkspaceFolder>	savedFolders	= provider.getWorkspaceFolders();
		UserSettings			savedSettings	= provider.getUserSettings();
		RecordingLanguageClient	client			= new RecordingLanguageClient();

		Path					workspaceRoot	= Files.createDirectories( tempDir.resolve( "workspace-lint-exclusion" ) );
		Path					documentPath	= workspaceRoot.resolve( "ExcludedAfterConfigChange.bx" );
		Path					lintConfig		= workspaceRoot.resolve( ".bxlint.json" );
		String					source			= """
		                                          class {
		                                          	function demo() {
		                                          		var unused = 1;
		                                          	}
		                                          }
		                                          """;
		WorkspaceFolder			folder			= new WorkspaceFolder();

		Files.writeString( documentPath, source );
		Files.writeString( lintConfig, "{}" );
		folder.setUri( workspaceRoot.toUri().toString() );

		try {
			provider.setWorkspaceFolders( List.of( folder ) );
			provider.setUserSettings( new UserSettings() );
			provider.setLanguageClient( client );
			LintConfigLoader.invalidate();

			provider.trackDocumentOpen( documentPath.toUri(), source );
			PublishDiagnosticsParams initialPublish = awaitPublishedDiagnostics( client, documentPath.toUri().toString(),
			    diagnostics -> !diagnostics.isEmpty() );

			assertThat( initialPublish.getDiagnostics() ).isNotEmpty();

			Files.writeString( lintConfig, """
			                               {
			                                 "exclude": [
			                                   "ExcludedAfterConfigChange.bx"
			                                 ]
			                               }
			                               """ );

			provider.handleConfigFileChange( lintConfig.toUri() );

			assertThat( awaitPublishedDiagnostics( client, documentPath.toUri().toString(), List::isEmpty ).getDiagnostics() ).isEmpty();
		} finally {
			provider.trackDocumentClose( documentPath.toUri() );
			provider.setWorkspaceFolders( savedFolders );
			provider.setUserSettings( savedSettings );
			provider.setLanguageClient( null );
			LintConfigLoader.invalidate();
		}
	}

	@Test
	@DisabledOnOs( OS.WINDOWS )
	void handleConfigFileChangeClearsPreviouslyPublishedDiagnosticsForClosedFilesThatBecomeExcluded() throws Exception {
		ProjectContextProvider	provider		= ProjectContextProvider.getInstance();
		List<WorkspaceFolder>	savedFolders	= provider.getWorkspaceFolders();
		UserSettings			savedSettings	= provider.getUserSettings();
		RecordingLanguageClient	client			= new RecordingLanguageClient();

		Path					workspaceRoot	= Files.createDirectories( tempDir.resolve( "workspace-lint-closed-exclusion" ) );
		Path					documentPath	= workspaceRoot.resolve( "ClosedAfterConfigChange.bx" );
		Path					lintConfig		= workspaceRoot.resolve( ".bxlint.json" );
		String					source			= """
		                                          class {
		                                          	function demo() {
		                                          		var unused = 1;
		                                          	}
		                                          }
		                                          """;
		WorkspaceFolder			folder			= new WorkspaceFolder();

		Files.writeString( documentPath, source );
		Files.writeString( lintConfig, "{}" );
		folder.setUri( workspaceRoot.toUri().toString() );

		try {
			provider.setWorkspaceFolders( List.of( folder ) );
			provider.setUserSettings( new UserSettings() );
			provider.setLanguageClient( client );
			LintConfigLoader.invalidate();

			provider.trackDocumentOpen( documentPath.toUri(), source );
			assertThat( awaitPublishedDiagnostics( client, documentPath.toUri().toString(), diagnostics -> !diagnostics.isEmpty() ).getDiagnostics() )
			    .isNotEmpty();

			provider.trackDocumentClose( documentPath.toUri() );

			Files.writeString( lintConfig, """
			                               {
			                                 "exclude": [
			                                   "ClosedAfterConfigChange.bx"
			                                 ]
			                               }
			                               """ );

			// Synchronously wait for the lint config change (including workspace parse) to complete
			provider.handleConfigFileChange( lintConfig.toUri() ).get( 10, java.util.concurrent.TimeUnit.SECONDS );
			// Flush any pending debounced publishes
			provider.flushPublishDebouncer();

			assertThat( awaitPublishedDiagnostics( client, documentPath.toUri().toString(), List::isEmpty ).getDiagnostics() ).isEmpty();
		} finally {
			provider.trackDocumentClose( documentPath.toUri() );
			provider.setWorkspaceFolders( savedFolders );
			provider.setUserSettings( savedSettings );
			provider.setLanguageClient( null );
			LintConfigLoader.invalidate();
		}
	}

	@Test
	void handleConfigFileChangeRestoresDefaultDiagnosticsWhenLintConfigIsDeleted() throws Exception {
		ProjectContextProvider	provider		= ProjectContextProvider.getInstance();
		List<WorkspaceFolder>	savedFolders	= provider.getWorkspaceFolders();
		UserSettings			savedSettings	= provider.getUserSettings();
		RecordingLanguageClient	client			= new RecordingLanguageClient();

		Path					workspaceRoot	= Files.createDirectories( tempDir.resolve( "workspace-lint-delete" ) );
		Path					documentPath	= workspaceRoot.resolve( "RestoredAfterConfigDelete.bx" );
		Path					lintConfig		= workspaceRoot.resolve( ".bxlint.json" );
		String					source			= """
		                                          class {
		                                          	function demo() {
		                                          		var unused = 1;
		                                          	}
		                                          }
		                                          """;
		WorkspaceFolder			folder			= new WorkspaceFolder();

		Files.writeString( documentPath, source );
		Files.writeString( lintConfig, """
		                               {
		                                 "exclude": [
		                                   "RestoredAfterConfigDelete.bx"
		                                 ]
		                               }
		                               """ );
		folder.setUri( workspaceRoot.toUri().toString() );

		try {
			provider.setWorkspaceFolders( List.of( folder ) );
			provider.setUserSettings( new UserSettings() );
			provider.setLanguageClient( client );
			LintConfigLoader.invalidate();

			provider.trackDocumentOpen( documentPath.toUri(), source );
			assertThat( awaitPublishedDiagnostics( client, documentPath.toUri().toString(), List::isEmpty ).getDiagnostics() ).isEmpty();

			Files.delete( lintConfig );
			provider.handleConfigFileChange( lintConfig.toUri() );

			assertThat( awaitPublishedDiagnostics( client, documentPath.toUri().toString(), diagnostics -> !diagnostics.isEmpty() ).getDiagnostics() )
			    .isNotEmpty();
		} finally {
			provider.trackDocumentClose( documentPath.toUri() );
			provider.setWorkspaceFolders( savedFolders );
			provider.setUserSettings( savedSettings );
			provider.setLanguageClient( null );
			LintConfigLoader.invalidate();
		}
	}

	@Test
	void formatDocumentFallsBackToLspFormattingOptionsWhenNoFormatterConfigExists() throws Exception {
		ProjectContextProvider				provider			= ProjectContextProvider.getInstance();
		List<WorkspaceFolder>				savedFolders		= provider.getWorkspaceFolders();
		UserSettings						savedSettings		= provider.getUserSettings();
		PrettyPrintRuntimeAdapter			savedRuntimeAdapter	= provider.getPrettyPrintRuntimeAdapter();

		RecordingPrettyPrintRuntimeAdapter	runtimeAdapter		= new RecordingPrettyPrintRuntimeAdapter();
		Path								workspaceRoot		= tempDir.resolve( "workspace-no-config" );
		Path								documentPath		= workspaceRoot.resolve( "Sample.bx" );
		String								source				= """
		                                                          class {
		                                                          	function foo() {
		                                                          		return \"bar\";
		                                                          	}
		                                                          }
		                                                          """;

		Files.createDirectories( workspaceRoot );
		Files.writeString( workspaceRoot.resolve( ".bxlint.json" ), """
		                                                            {
		                                                              \"formatting\": {
		                                                                \"experimental\": {
		                                                                  \"enabled\": true
		                                                                }
		                                                              }
		                                                            }
		                                                            """ );
		Files.writeString( documentPath, source );

		WorkspaceFolder folder = new WorkspaceFolder();
		folder.setUri( workspaceRoot.toUri().toString() );

		try {
			provider.setWorkspaceFolders( List.of( folder ) );
			provider.setUserSettings( new UserSettings() );
			provider.setPrettyPrintRuntimeAdapter( runtimeAdapter );
			provider.trackDocumentOpen( documentPath.toUri(), source );
			LintConfigLoader.invalidate();

			provider.formatDocument( documentPath.toUri(), new FormattingOptions( 2, true ) );

			assertThat( runtimeAdapter.lastConfigPath ).isNull();
			assertThat( runtimeAdapter.lastTabSize ).isEqualTo( 2 );
			assertThat( runtimeAdapter.lastInsertSpaces ).isTrue();
		} finally {
			provider.trackDocumentClose( documentPath.toUri() );
			provider.setWorkspaceFolders( savedFolders );
			provider.setUserSettings( savedSettings );
			provider.setPrettyPrintRuntimeAdapter( savedRuntimeAdapter );
			LintConfigLoader.invalidate();
		}
	}

	@Test
	void formatDocumentAppliesFormatterConfigFromDocumentDirectory() throws Exception {
		ProjectContextProvider		provider			= ProjectContextProvider.getInstance();
		List<WorkspaceFolder>		savedFolders		= provider.getWorkspaceFolders();
		UserSettings				savedSettings		= provider.getUserSettings();
		PrettyPrintRuntimeAdapter	savedRuntimeAdapter	= provider.getPrettyPrintRuntimeAdapter();

		Path						repoRoot			= Path.of( "" ).toAbsolutePath().normalize();
		Path						documentPath		= repoRoot.resolve( "src/test/resources/test-bx-project/Car.bx" );
		String						source				= Files.readString( documentPath );

		WorkspaceFolder				folder				= new WorkspaceFolder();
		folder.setUri( repoRoot.toUri().toString() );

		try {
			provider.setWorkspaceFolders( List.of( folder ) );
			provider.setUserSettings( createUserSettings( true ) );
			provider.setPrettyPrintRuntimeAdapter( new PrettyPrintRuntimeAdapter() );
			provider.trackDocumentOpen( documentPath.toUri(), source );

			List<? extends TextEdit> edits = provider.formatDocument( documentPath.toUri(), new FormattingOptions( 4, true ) );

			assertThat( edits ).isNotEmpty();
			TextEdit edit = edits.getFirst();
			assertThat( edit ).isNotNull();
			if ( edit == null ) {
				return;
			}
			String formatted = edit.getNewText();
			assertThat( formatted.indexOf( "property name=\"a\";" ) ).isLessThan( formatted.indexOf( "property name=\"b\";" ) );
		} finally {
			provider.trackDocumentClose( documentPath.toUri() );
			provider.setWorkspaceFolders( savedFolders );
			provider.setUserSettings( savedSettings );
			provider.setPrettyPrintRuntimeAdapter( savedRuntimeAdapter );
		}
	}

	@Test
	void formatDocumentReturnsNoEditsWhenFormatterConfigCannotBeLoaded() throws Exception {
		ProjectContextProvider				provider			= ProjectContextProvider.getInstance();
		List<WorkspaceFolder>				savedFolders		= provider.getWorkspaceFolders();
		UserSettings						savedSettings		= provider.getUserSettings();
		PrettyPrintRuntimeAdapter			savedRuntimeAdapter	= provider.getPrettyPrintRuntimeAdapter();

		RecordingPrettyPrintRuntimeAdapter	runtimeAdapter		= new RecordingPrettyPrintRuntimeAdapter();
		runtimeAdapter.configException = new PrettyPrintRuntimeAdapter.PrettyPrintException( "bad config", new IllegalArgumentException( "bad json" ), true );

		Path	workspaceRoot	= tempDir.resolve( "workspace-bad-config" );
		Path	nestedDir		= workspaceRoot.resolve( "nested" );
		Path	documentPath	= nestedDir.resolve( "Sample.bx" );
		String	source			= """
		                          class {
		                          	function foo() {
		                          		return \"bar\";
		                          	}
		                          }
		                          """;

		Files.createDirectories( nestedDir );
		Files.writeString( workspaceRoot.resolve( ".bxlint.json" ), """
		                                                            {
		                                                              \"formatting\": {
		                                                                \"experimental\": {
		                                                                  \"enabled\": true
		                                                                }
		                                                              }
		                                                            }
		                                                            """ );
		Files.writeString( workspaceRoot.resolve( ".bxformat.json" ), "{ invalid" );
		Files.writeString( documentPath, source );

		WorkspaceFolder folder = new WorkspaceFolder();
		folder.setUri( workspaceRoot.toUri().toString() );

		try {
			provider.setWorkspaceFolders( List.of( folder ) );
			provider.setUserSettings( new UserSettings() );
			provider.setPrettyPrintRuntimeAdapter( runtimeAdapter );
			provider.trackDocumentOpen( documentPath.toUri(), source );
			LintConfigLoader.invalidate();

			List<? extends TextEdit> edits = provider.formatDocument( documentPath.toUri(), new FormattingOptions( 2, true ) );

			assertThat( edits ).isEmpty();
			assertThat( runtimeAdapter.prettyPrintCalls ).isEqualTo( 1 );
		} finally {
			provider.trackDocumentClose( documentPath.toUri() );
			provider.setWorkspaceFolders( savedFolders );
			provider.setUserSettings( savedSettings );
			provider.setPrettyPrintRuntimeAdapter( savedRuntimeAdapter );
			LintConfigLoader.invalidate();
		}
	}

	private static class RecordingFormattingCapabilityCoordinator extends FormattingCapabilityCoordinator {

		private int refreshCalls;

		@Override
		public void refresh( LintConfig lintConfig, UserSettings userSettings ) {
			refreshCalls++;
		}
	}

	private static class RecordingFormatterConfigResolver extends FormatterConfigResolver {

		private final java.util.ArrayList<Path> invalidatedPaths = new java.util.ArrayList<>();

		@Override
		public void invalidate( Path changedConfigPath ) {
			invalidatedPaths.add( changedConfigPath );
		}
	}

	private static class RecordingPrettyPrintRuntimeAdapter extends PrettyPrintRuntimeAdapter {

		private int						prettyPrintCalls;
		private Path					lastConfigPath;
		private Integer					lastTabSize;
		private Boolean					lastInsertSpaces;
		private PrettyPrintException	configException;

		@Override
		public boolean isPrettyPrintAvailable() {
			return true;
		}

		@Override
		public String prettyPrint( ortus.boxlang.compiler.ast.BoxNode node, Path configPath, Integer tabSize, Boolean insertSpaces )
		    throws PrettyPrintException {
			prettyPrintCalls++;
			lastConfigPath		= configPath;
			lastTabSize			= tabSize;
			lastInsertSpaces	= insertSpaces;
			if ( configException != null ) {
				throw configException;
			}
			return "formatted output";
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

		private final java.util.ArrayList<RegistrationParams>									registrationRequests	= new java.util.ArrayList<>();
		private final java.util.concurrent.ConcurrentHashMap<String, PublishDiagnosticsParams>	publishedDiagnostics	= new java.util.concurrent.ConcurrentHashMap<>();

		@Override
		public CompletableFuture<Void> registerCapability( RegistrationParams params ) {
			registrationRequests.add( params );
			return CompletableFuture.completedFuture( null );
		}

		@Override
		public void publishDiagnostics( PublishDiagnosticsParams diagnostics ) {
			publishedDiagnostics.put( diagnostics.getUri(), diagnostics );
		}
	}

	private static UserSettings createUserSettings( boolean experimentalFormatterEnabled ) {
		JsonObject settings = new JsonObject();
		settings.addProperty( "experimentalFormatterEnabled", experimentalFormatterEnabled );
		return UserSettings.fromChangeConfigurationParams( new NoOpLanguageClient(), new DidChangeConfigurationParams( settings ) );
	}

	private static PublishDiagnosticsParams awaitPublishedDiagnostics(
	    RecordingLanguageClient client,
	    String uri,
	    java.util.function.Predicate<List<org.eclipse.lsp4j.Diagnostic>> matcher ) throws InterruptedException {
		for ( int attempt = 0; attempt < 40; attempt++ ) {
			PublishDiagnosticsParams params = client.publishedDiagnostics.get( uri );
			if ( params != null && matcher.test( params.getDiagnostics() ) ) {
				return params;
			}
			Thread.sleep( 50 );
		}
		throw new AssertionError( "Timed out waiting for diagnostics publish for " + uri );
	}

	private static boolean awaitCachedDiagnostic( ProjectContextProvider provider, java.net.URI uri, String diagnosticCode ) throws InterruptedException {
		for ( int attempt = 0; attempt < 40; attempt++ ) {
			boolean found = provider.getCachedDiagnosticReports().stream()
			    .filter( report -> uri.equals( report.getFileURI() ) )
			    .flatMap( report -> report.getDiagnostics().stream() )
			    .anyMatch( diagnostic -> diagnostic.getCode() != null && diagnosticCode.equals( diagnostic.getCode().getLeft() ) );
			if ( found ) {
				return true;
			}
			Thread.sleep( 50 );
		}
		return false;
	}
}