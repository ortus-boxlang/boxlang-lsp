package ortus.boxlang.lsp.lint;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.lsp4j.WorkspaceFolder;

import com.fasterxml.jackson.databind.ObjectMapper;

import ortus.boxlang.lsp.workspace.ProjectContextProvider;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.logging.BoxLangLogger;

/** Loads and caches the lint configuration file .boxlang-lsp.json at the project root. */
public class LintConfigLoader {

	public static final String							CONFIG_FILENAME	= ".boxlang-lsp.json";
	private static final ObjectMapper					MAPPER			= new ObjectMapper();
	private static final AtomicReference<LintConfig>	CACHE			= new AtomicReference<>( new LintConfig() );
	private static volatile long						lastLoad		= 0L;
	private static final long							TTL_MS			= 5_000; // simple debounce until we add file watch
	private static final BoxLangLogger					LOGGER			= BoxRuntime.getInstance().getLoggingService().getLogger( "lsp.lint" );

	public static LintConfig get() {
		long now = System.currentTimeMillis();
		if ( now - lastLoad < TTL_MS ) {
			LOGGER.trace( "Lint config cache hit (TTL window)." );
			return CACHE.get();
		}
		Path	root	= null;
		var		folders	= ProjectContextProvider.getInstance().getWorkspaceFolders();
		if ( folders != null && !folders.isEmpty() ) {
			WorkspaceFolder wf = folders.getFirst();
			try {
				root = Path.of( new URI( wf.getUri() ) );
			} catch ( URISyntaxException e ) {
				// ignore
			}
		}
		if ( root == null ) {
			LOGGER.debug( "No workspace root detected; using existing lint config cache." );
			lastLoad = now;
			return CACHE.get();
		}
		Path cfgPath = root.resolve( CONFIG_FILENAME );
		if ( !Files.exists( cfgPath ) ) {
			LOGGER.trace( ".boxlang-lsp.json not found at " + cfgPath + "; using defaults." );
			lastLoad = now;
			return CACHE.get();
		}
		try {
			LOGGER.debug( "Loading lint config from " + cfgPath );
			byte[]		bytes	= Files.readAllBytes( cfgPath );
			LintConfig	lc		= MAPPER.readValue( bytes, LintConfig.class );
			if ( lc == null )
				lc = new LintConfig();
			CACHE.set( lc );
			LOGGER.info( "Lint config loaded: " + lc.diagnostics.keySet() );
		} catch ( IOException e ) {
			LOGGER.error( "Failed to load lint config; keeping previous configuration", e );
			// ignore malformed file; keep prior config
		} finally {
			lastLoad = now;
		}
		return CACHE.get();
	}

	/** Force the next get() call to reload from disk immediately. */
	public static void invalidate() {
		LOGGER.debug( "Lint config cache invalidated." );
		lastLoad = 0L;
	}
}
