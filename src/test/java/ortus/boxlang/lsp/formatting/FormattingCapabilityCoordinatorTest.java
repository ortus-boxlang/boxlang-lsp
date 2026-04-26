package ortus.boxlang.lsp.formatting;

import static com.google.common.truth.Truth.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DocumentFormattingRegistrationOptions;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Registration;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.UnregistrationParams;
import org.eclipse.lsp4j.services.LanguageClient;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

import ortus.boxlang.lsp.UserSettings;
import ortus.boxlang.lsp.lint.LintConfig;

class FormattingCapabilityCoordinatorTest {

	@Test
	void refreshRegistersFormattingCapabilityWhenDynamicRegistrationIsSupportedAndFormattingBecomesAvailable() {
		RecordingLanguageClient			client		= new RecordingLanguageClient();
		FormattingCapabilityCoordinator	coordinator	= new FormattingCapabilityCoordinator(
		    new FormattingAvailabilityResolver( new FormattingSettingsResolver(), alwaysAvailableAdapter() ),
		    new FormattingCapabilityTransitionResolver()
		);

		coordinator.setSupportsDynamicRegistration( true );
		coordinator.setLanguageClient( client );
		coordinator.refresh( enabledLintConfig(), userSettingsWithFormattingEnabled( true ) );

		assertThat( client.registrationRequests ).hasSize( 1 );
		assertThat( client.unregistrationRequests ).isEmpty();
	}

	@Test
	void refreshRegistersFormattingCapabilityWithDocumentSelectorOptions() {
		RecordingLanguageClient			client		= new RecordingLanguageClient();
		FormattingCapabilityCoordinator	coordinator	= new FormattingCapabilityCoordinator(
		    new FormattingAvailabilityResolver( new FormattingSettingsResolver(), alwaysAvailableAdapter() ),
		    new FormattingCapabilityTransitionResolver()
		);

		coordinator.setSupportsDynamicRegistration( true );
		coordinator.setLanguageClient( client );
		coordinator.refresh( enabledLintConfig(), userSettingsWithFormattingEnabled( true ) );

		Registration registration = client.registrationRequests.getFirst().getRegistrations().getFirst();
		assertThat( registration.getRegisterOptions() ).isInstanceOf( DocumentFormattingRegistrationOptions.class );

		DocumentFormattingRegistrationOptions options = ( DocumentFormattingRegistrationOptions ) registration.getRegisterOptions();
		assertThat( options.getDocumentSelector() ).isNotEmpty();
		assertThat( options.getDocumentSelector().stream().map( filter -> filter.getPattern() ).toList() )
		    .contains( "**/*.{bx,bxs,bxm,cfc,cfs,cfm}" );
	}

	@Test
	void refreshUnregistersFormattingCapabilityWhenPreviouslyRegisteredCapabilityBecomesUnavailable() {
		RecordingLanguageClient			client			= new RecordingLanguageClient();
		ToggleableRuntimeAdapter		runtimeAdapter	= new ToggleableRuntimeAdapter( true );
		FormattingCapabilityCoordinator	coordinator		= new FormattingCapabilityCoordinator(
		    new FormattingAvailabilityResolver( new FormattingSettingsResolver(), runtimeAdapter ),
		    new FormattingCapabilityTransitionResolver()
		);

		coordinator.setSupportsDynamicRegistration( true );
		coordinator.setLanguageClient( client );
		coordinator.refresh( enabledLintConfig(), userSettingsWithFormattingEnabled( true ) );

		runtimeAdapter.available = false;
		coordinator.refresh( enabledLintConfig(), userSettingsWithFormattingEnabled( true ) );

		assertThat( client.registrationRequests ).hasSize( 1 );
		assertThat( client.unregistrationRequests ).hasSize( 1 );
	}

	@Test
	void staticCapabilityAdvertisementDependsOnRuntimeSupportAndDynamicRegistration() {
		FormattingCapabilityCoordinator dynamicCoordinator = new FormattingCapabilityCoordinator(
		    new FormattingAvailabilityResolver( new FormattingSettingsResolver(), alwaysAvailableAdapter() ),
		    new FormattingCapabilityTransitionResolver()
		);
		dynamicCoordinator.setSupportsDynamicRegistration( true );

		FormattingCapabilityCoordinator nonDynamicCoordinator = new FormattingCapabilityCoordinator(
		    new FormattingAvailabilityResolver( new FormattingSettingsResolver(), alwaysAvailableAdapter() ),
		    new FormattingCapabilityTransitionResolver()
		);
		nonDynamicCoordinator.setSupportsDynamicRegistration( false );

		assertThat( dynamicCoordinator.shouldAdvertiseFormattingStatically() ).isFalse();
		assertThat( nonDynamicCoordinator.shouldAdvertiseFormattingStatically() ).isTrue();
	}

	@Test
	void refreshLogsUnsupportedRuntimeOnlyWhenTransitioningIntoUnsupportedState() {
		RecordingLanguageClient			client			= new RecordingLanguageClient();
		ToggleableRuntimeAdapter		runtimeAdapter	= new ToggleableRuntimeAdapter( false );
		FormattingCapabilityCoordinator	coordinator		= new FormattingCapabilityCoordinator(
		    new FormattingAvailabilityResolver( new FormattingSettingsResolver(), runtimeAdapter ),
		    new FormattingCapabilityTransitionResolver()
		);

		coordinator.setSupportsDynamicRegistration( true );
		coordinator.setLanguageClient( client );

		coordinator.refresh( enabledLintConfig(), userSettingsWithFormattingEnabled( true ) );
		coordinator.refresh( enabledLintConfig(), userSettingsWithFormattingEnabled( true ) );

		assertThat( client.loggedMessages ).hasSize( 1 );
		assertThat( client.loggedMessages.getFirst().getType() ).isEqualTo( MessageType.Warning );
		assertThat( client.loggedMessages.getFirst().getMessage() ).contains( "experimental formatter is enabled" );

		runtimeAdapter.available = true;
		coordinator.refresh( enabledLintConfig(), userSettingsWithFormattingEnabled( true ) );

		runtimeAdapter.available = false;
		coordinator.refresh( enabledLintConfig(), userSettingsWithFormattingEnabled( true ) );

		assertThat( client.loggedMessages ).hasSize( 2 );
	}

	private LintConfig enabledLintConfig() {
		LintConfig lintConfig = new LintConfig();
		lintConfig.formatting.experimental.enabled = true;
		return lintConfig;
	}

	private UserSettings userSettingsWithFormattingEnabled( boolean enabled ) {
		JsonObject settings = new JsonObject();
		settings.addProperty( "experimentalFormatterEnabled", enabled );
		return UserSettings.fromChangeConfigurationParams( new RecordingLanguageClient(), new DidChangeConfigurationParams( settings ) );
	}

	private PrettyPrintRuntimeAdapter alwaysAvailableAdapter() {
		return new ToggleableRuntimeAdapter( true );
	}

	private static class ToggleableRuntimeAdapter extends PrettyPrintRuntimeAdapter {

		private boolean available;

		private ToggleableRuntimeAdapter( boolean available ) {
			this.available = available;
		}

		@Override
		public boolean isPrettyPrintAvailable() {
			return available;
		}
	}

	private static class RecordingLanguageClient implements LanguageClient {

		private final List<RegistrationParams>		registrationRequests	= new ArrayList<>();
		private final List<UnregistrationParams>	unregistrationRequests	= new ArrayList<>();
		private final List<MessageParams>			loggedMessages			= new ArrayList<>();

		@Override
		public CompletableFuture<Void> registerCapability( RegistrationParams params ) {
			registrationRequests.add( params );
			return CompletableFuture.completedFuture( null );
		}

		@Override
		public CompletableFuture<Void> unregisterCapability( UnregistrationParams params ) {
			unregistrationRequests.add( params );
			return CompletableFuture.completedFuture( null );
		}

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
			loggedMessages.add( message );
		}
	}
}