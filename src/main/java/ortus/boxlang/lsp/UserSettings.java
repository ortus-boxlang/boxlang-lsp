package ortus.boxlang.lsp;

import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.services.LanguageClient;

import com.google.gson.JsonObject;

public class UserSettings {

	private boolean			enableBackgroundParsing			= false;
	private boolean			processDiagnosticsInParallel	= true;

	private LanguageClient	client							= null;

	public void setLanguageClient( LanguageClient client ) {
		this.client = client;
	}

	public boolean isEnableBackgroundParsing() {
		return enableBackgroundParsing;
	}

	public boolean isProcessDiagnosticsInParallel() {
		return processDiagnosticsInParallel;
	}

	public static UserSettings fromChangeConfigurationParams( LanguageClient client, DidChangeConfigurationParams params ) {
		JsonObject		settings		= ( JsonObject ) params.getSettings();

		UserSettings	userSettings	= new UserSettings();
		userSettings.client							= client;
		userSettings.enableBackgroundParsing		= userSettings.checkBoolean( settings, "enableBackgroundParsing", false );
		userSettings.processDiagnosticsInParallel	= userSettings.checkBoolean( settings, "processDiagnosticsInParallel", true );

		return userSettings;
	}

	private boolean checkBoolean(
	    JsonObject settings,
	    String key,
	    boolean defaultValue ) {
		try {
			if ( settings.has( key ) ) {
				boolean newValue = settings.get( key ).getAsBoolean();
				this.client.logMessage( new MessageParams( MessageType.Info, "Changing " + key + " to " + newValue ) );

				return newValue;
			}

			return defaultValue;
		} catch ( Exception e ) {
			this.client.logMessage( new MessageParams( MessageType.Error, "Unable to parse " + key + " setting, defaulting to " + defaultValue ) );
			return defaultValue;
		}

	}
}
