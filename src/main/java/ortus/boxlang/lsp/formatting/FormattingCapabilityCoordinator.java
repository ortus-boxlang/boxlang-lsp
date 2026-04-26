package ortus.boxlang.lsp.formatting;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.DocumentFilter;
import org.eclipse.lsp4j.DocumentFormattingRegistrationOptions;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.Registration;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.Unregistration;
import org.eclipse.lsp4j.UnregistrationParams;
import org.eclipse.lsp4j.services.LanguageClient;

import ortus.boxlang.lsp.App;
import ortus.boxlang.lsp.LSPTools;
import ortus.boxlang.lsp.UserSettings;
import ortus.boxlang.lsp.lint.LintConfig;

public class FormattingCapabilityCoordinator {

	private static final String								FORMATTING_METHOD			= "textDocument/formatting";

	private final FormattingAvailabilityResolver			availabilityResolver;
	private final FormattingCapabilityTransitionResolver	transitionResolver;
	private static final String								UNSUPPORTED_RUNTIME_MESSAGE	= "BoxLang experimental formatter is enabled, but the current runtime does not support PrettyPrint. Formatting remains unavailable.";

	private boolean											supportsDynamicRegistration;
	private boolean											formattingRegistered;
	private boolean											previouslyEnabledButUnsupported;
	private String											registrationId;
	private LanguageClient									languageClient;

	public FormattingCapabilityCoordinator() {
		this( new FormattingAvailabilityResolver(), new FormattingCapabilityTransitionResolver() );
	}

	FormattingCapabilityCoordinator( FormattingAvailabilityResolver availabilityResolver, FormattingCapabilityTransitionResolver transitionResolver ) {
		this.availabilityResolver	= availabilityResolver;
		this.transitionResolver		= transitionResolver;
	}

	public void setSupportsDynamicRegistration( boolean supportsDynamicRegistration ) {
		this.supportsDynamicRegistration = supportsDynamicRegistration;
	}

	public void setLanguageClient( LanguageClient languageClient ) {
		this.languageClient = languageClient;
	}

	public boolean shouldAdvertiseFormattingStatically() {
		return !supportsDynamicRegistration && availabilityResolver.isRuntimeSupported();
	}

	public void refresh( LintConfig lintConfig, UserSettings userSettings ) {
		boolean							runtimeSupported		= availabilityResolver.isRuntimeSupported();
		boolean							formattingEnabled		= availabilityResolver.isFormattingEnabled( lintConfig, userSettings );
		boolean							enabledButUnsupported	= formattingEnabled && !runtimeSupported;
		boolean							shouldBeRegistered		= formattingEnabled && runtimeSupported;
		FormattingCapabilityTransition	transition				= transitionResolver.resolve( supportsDynamicRegistration, formattingRegistered,
		    shouldBeRegistered );

		App.logger.info(
		    "Formatting capability refresh: enabled={} runtimeSupported={} dynamicRegistration={} currentlyRegistered={} transition={}",
		    formattingEnabled,
		    runtimeSupported,
		    supportsDynamicRegistration,
		    formattingRegistered,
		    transition );

		if ( enabledButUnsupported && !previouslyEnabledButUnsupported ) {
			App.logger.warn( UNSUPPORTED_RUNTIME_MESSAGE );
			if ( languageClient != null ) {
				languageClient.logMessage( new MessageParams( MessageType.Warning, UNSUPPORTED_RUNTIME_MESSAGE ) );
			}
		}

		previouslyEnabledButUnsupported = enabledButUnsupported;

		if ( languageClient == null ) {
			App.logger.info( "Formatting capability refresh skipped registration update because no language client is connected yet" );
			return;
		}

		if ( transition == FormattingCapabilityTransition.REGISTER ) {
			registrationId = UUID.randomUUID().toString();
			App.logger.info( "Registering dynamic formatting capability with id={}", registrationId );
			languageClient.registerCapability(
			    new RegistrationParams( List.of( new Registration( registrationId, FORMATTING_METHOD, createRegistrationOptions() ) ) )
			);
			formattingRegistered = true;
		} else if ( transition == FormattingCapabilityTransition.UNREGISTER && registrationId != null ) {
			App.logger.info( "Unregistering dynamic formatting capability with id={}", registrationId );
			languageClient.unregisterCapability( new UnregistrationParams( List.of( new Unregistration( registrationId, FORMATTING_METHOD ) ) ) );
			formattingRegistered = false;
		} else {
			App.logger.info( "Formatting capability registration unchanged" );
		}
	}

	private DocumentFormattingRegistrationOptions createRegistrationOptions() {
		DocumentFormattingRegistrationOptions options = new DocumentFormattingRegistrationOptions();
		options.setDocumentSelector( List.of( new DocumentFilter( null, "file", supportedGlobPattern() ) ) );
		return options;
	}

	private String supportedGlobPattern() {
		String extensions = java.util.Arrays.stream( LSPTools.BOXLANG_EXTENSIONS )
		    .map( extension -> extension.replace( ".", "" ) )
		    .collect( Collectors.joining( "," ) );
		return "**/*.{" + extensions + "}";
	}
}