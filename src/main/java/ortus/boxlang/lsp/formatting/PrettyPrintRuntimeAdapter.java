package ortus.boxlang.lsp.formatting;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;

import ortus.boxlang.compiler.ast.BoxNode;

public class PrettyPrintRuntimeAdapter {

	static final String			PRETTY_PRINT_CLASS_NAME	= "ortus.boxlang.compiler.prettyprint.PrettyPrint";
	static final String			CONFIG_CLASS_NAME		= "ortus.boxlang.compiler.prettyprint.config.Config";

	private final ClassLoader	classLoader;
	private final String		className;

	public PrettyPrintRuntimeAdapter() {
		this( PrettyPrintRuntimeAdapter.class.getClassLoader(), PRETTY_PRINT_CLASS_NAME );
	}

	PrettyPrintRuntimeAdapter( ClassLoader classLoader, String className ) {
		this.classLoader	= classLoader;
		this.className		= className;
	}

	public boolean isPrettyPrintAvailable() {
		try {
			classLoader.loadClass( className );
			return true;
		} catch ( ClassNotFoundException e ) {
			return false;
		}
	}

	public String prettyPrint( BoxNode node, Path configPath, Integer tabSize, Boolean insertSpaces ) throws PrettyPrintException {
		try {
			Class<?>	prettyPrintClass	= classLoader.loadClass( className );
			Class<?>	configClass			= classLoader.loadClass( CONFIG_CLASS_NAME );
			Object		config				= loadConfig( configClass, configPath, tabSize, insertSpaces );

			return ( String ) prettyPrintClass.getMethod( "prettyPrint", BoxNode.class, configClass )
			    .invoke( null, node, config );
		} catch ( PrettyPrintException e ) {
			throw e;
		} catch ( ClassNotFoundException | NoSuchMethodException | IllegalAccessException e ) {
			throw new PrettyPrintException( "PrettyPrint runtime support is unavailable", e, false );
		} catch ( InvocationTargetException e ) {
			Throwable cause = e.getCause() == null ? e : e.getCause();
			throw new PrettyPrintException( "PrettyPrint invocation failed", cause, false );
		}
	}

	private Object loadConfig( Class<?> configClass, Path configPath, Integer tabSize, Boolean insertSpaces ) throws PrettyPrintException {
		try {
			Object config = configPath == null
			    ? configClass.getDeclaredConstructor().newInstance()
			    : configClass.getMethod( "loadConfigAutoDetect", String.class ).invoke( null, configPath.toString() );

			if ( configPath == null ) {
				if ( tabSize != null ) {
					configClass.getMethod( "setIndentSize", int.class ).invoke( config, tabSize.intValue() );
				}

				if ( insertSpaces != null ) {
					configClass.getMethod( "setTabIndent", boolean.class ).invoke( config, !insertSpaces.booleanValue() );
				}
			}

			return config;
		} catch ( InvocationTargetException e ) {
			Throwable cause = e.getCause() == null ? e : e.getCause();
			throw new PrettyPrintException( "Failed to load formatter configuration", cause, true );
		} catch ( ReflectiveOperationException e ) {
			throw new PrettyPrintException( "Failed to prepare PrettyPrint configuration", e, false );
		}
	}

	public static class PrettyPrintException extends Exception {

		private final boolean configError;

		public PrettyPrintException( String message, Throwable cause, boolean configError ) {
			super( message, cause );
			this.configError = configError;
		}

		public boolean isConfigError() {
			return configError;
		}
	}
}