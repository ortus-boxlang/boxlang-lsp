package ortus.boxlang.lsp.lint;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;

import ortus.boxlang.lsp.workspace.ProjectContextProvider;
import org.eclipse.lsp4j.WorkspaceFolder;
import java.net.URI;
import java.net.URISyntaxException;

/** Loads and caches the lint configuration file .boxlang-lsp.json at the project root. */
public class LintConfigLoader {
    public static final String CONFIG_FILENAME = ".boxlang-lsp.json";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final AtomicReference<LintConfig> CACHE = new AtomicReference<>( new LintConfig() );
    private static volatile long lastLoad = 0L;
    private static final long TTL_MS = 5_000; // simple debounce until we add file watch

    public static LintConfig get() {
        long now = System.currentTimeMillis();
        if ( now - lastLoad < TTL_MS ) {
            return CACHE.get();
        }
        Path root = null;
        var folders = ProjectContextProvider.getInstance().getWorkspaceFolders();
        if ( folders != null && !folders.isEmpty() ) {
            WorkspaceFolder wf = folders.getFirst();
            try {
                root = Path.of( new URI( wf.getUri() ) );
            } catch ( URISyntaxException e ) {
                // ignore
            }
        }
        if ( root == null ) {
            lastLoad = now;
            return CACHE.get();
        }
        Path cfgPath = root.resolve( CONFIG_FILENAME );
        if ( !Files.exists( cfgPath ) ) {
            lastLoad = now;
            return CACHE.get();
        }
        try {
            byte[] bytes = Files.readAllBytes( cfgPath );
            LintConfig lc = MAPPER.readValue( bytes, LintConfig.class );
            if ( lc == null ) lc = new LintConfig();
            CACHE.set( lc );
        } catch ( IOException e ) {
            // ignore malformed file; keep prior config
        } finally {
            lastLoad = now;
        }
        return CACHE.get();
    }
}
