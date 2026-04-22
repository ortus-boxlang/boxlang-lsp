package ortus.boxlang.lsp.workspace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

public class ColdBoxDetector {

	private ColdBoxDetector() {
	}

	/**
	 * Determine whether the given directory is a ColdBox application root.
	 *
	 * <p>
	 * A directory is considered a ColdBox app if <em>any</em> of the following
	 * heuristics match:
	 * <ol>
	 * <li>An {@code Application.bx} or {@code Application.cfc} in the directory
	 * contains {@code extends="coldbox.system.Bootstrap"}.
	 * <li>A {@code /coldbox} subdirectory exists in the directory.
	 * <li>A {@code config/Coldbox.cfc} file exists in the directory.
	 * </ol>
	 *
	 * @param appRoot the candidate application root directory
	 *
	 * @return {@code true} if the directory is recognised as a ColdBox app
	 */
	public static boolean isColdBoxApp( Path appRoot ) {
		Path appBx = findApplicationFile( appRoot );

		// Heuristic 1: extends="coldbox.system.Bootstrap"
		if ( appBx != null && containsExtendsBootstrap( appBx ) ) {
			return true;
		}

		// Heuristic 2: /coldbox directory exists
		if ( Files.isDirectory( appRoot.resolve( "coldbox" ) ) ) {
			return true;
		}

		// Heuristic 3: config/Coldbox.cfc exists
		if ( Files.isRegularFile( appRoot.resolve( "config/Coldbox.cfc" ) ) ) {
			return true;
		}

		return false;
	}

	/**
	 * Recursively discover implicit module mappings under
	 * {@code {appRoot}/modules} and {@code {appRoot}/modules_app}.
	 *
	 * <p>
	 * Both directories are treated identically: flat keys
	 * ({@code /{moduleName}}), recursive scanning, and least-nested tie-breaking.
	 * If a module name exists in both {@code modules/} and {@code modules_app/}
	 * at the same depth, the one in {@code modules/} wins because it is scanned
	 * first.
	 *
	 * @param appRoot the ColdBox application root directory
	 *
	 * @return map of flat virtual key → absolute module path; never null
	 */
	public static Map<String, Path> discoverModuleMappings( Path appRoot ) {
		Map<String, Path> result = new LinkedHashMap<>();

		for ( String dirName : new String[] { "modules", "modules_app" } ) {
			Path modulesDir = appRoot.resolve( dirName );
			if ( Files.isDirectory( modulesDir ) ) {
				scanModules( modulesDir, appRoot, result );
			}
		}

		return result.isEmpty() ? Collections.emptyMap() : Collections.unmodifiableMap( result );
	}

	// ──────────────────────────────────────────────────────────────────────────
	// Private helpers
	// ──────────────────────────────────────────────────────────────────────────

	private static Path findApplicationFile( Path dir ) {
		for ( String name : new String[] { "Application.bx", "Application.cfc" } ) {
			Path candidate = dir.resolve( name );
			if ( Files.isRegularFile( candidate ) ) {
				return candidate;
			}
		}
		return null;
	}

	private static boolean containsExtendsBootstrap( Path filePath ) {
		try {
			String content = Files.readString( filePath ).toLowerCase();
			return content.contains( "extends" ) && content.contains( "coldbox.system.bootstrap" );
		} catch ( IOException e ) {
			return false;
		}
	}

	private static void scanModules( Path modulesDir, Path appRoot, Map<String, Path> result ) {
		try ( Stream<Path> entries = Files.list( modulesDir ) ) {
			entries
			    .filter( Files::isDirectory )
			    .forEach( moduleDir -> {
				    String key	= "/" + moduleDir.getFileName().toString();
				    int	depth	= moduleDir.getNameCount() - appRoot.getNameCount();

				    // Only add if key not present or this one is less nested
				    if ( !result.containsKey( key ) ) {
					    result.put( key, moduleDir.toAbsolutePath().normalize() );
				    } else {
					    int existingDepth = result.get( key ).getNameCount() - appRoot.getNameCount();
					    if ( depth < existingDepth ) {
						    result.put( key, moduleDir.toAbsolutePath().normalize() );
					    }
				    }

				    // Recurse into nested modules/ and modules_app/
				    for ( String nestedName : new String[] { "modules", "modules_app" } ) {
					    Path nestedModules = moduleDir.resolve( nestedName );
					    if ( Files.isDirectory( nestedModules ) ) {
						    scanModules( nestedModules, appRoot, result );
					    }
				    }
			    } );
		} catch ( IOException e ) {
			// Silently ignore unreadable directories
		}
	}
}
