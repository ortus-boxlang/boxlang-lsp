package ortus.boxlang.lsp.workspace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class MappingResolver {

	private static final Map<Path, MappingConfig>	cache		= new ConcurrentHashMap<>();
	/** Cache keyed by the resolved Application.bx / Application.cfc path. */
	private static final Map<Path, MappingConfig>	fileCache	= new ConcurrentHashMap<>();

	private MappingResolver() {
	}

	/**
	 * Resolve a MappingConfig for the given workspace root. The result is cached
	 * keyed by workspace root so repeated calls are cheap.
	 *
	 * @param workspaceRoot the workspace root directory
	 * 
	 * @return the resolved MappingConfig (never null)
	 */
	public static MappingConfig resolve( Path workspaceRoot ) {
		return resolve( workspaceRoot, Collections.emptyMap() );
	}

	/**
	 * Resolve a MappingConfig for the given workspace root, overlaying VSCode
	 * settings mappings on top of the project-level config with the highest
	 * precedence.
	 *
	 * @param workspaceRoot  the workspace root directory
	 * @param vscodeMappings raw virtual-key → path-string map from VSCode settings
	 *
	 * @return the resolved MappingConfig (never null)
	 */
	public static MappingConfig resolve( Path workspaceRoot, Map<String, String> vscodeMappings ) {
		MappingConfig base = cache.computeIfAbsent( workspaceRoot.toAbsolutePath().normalize(), MappingResolver::computeConfig );

		if ( vscodeMappings == null || vscodeMappings.isEmpty() ) {
			return base;
		}

		return mergeVscodeMappings( base, vscodeMappings, workspaceRoot );
	}

	/**
	 * Invalidate any cached result for the given workspace root so the next
	 * {@link #resolve(Path)} call re-reads the filesystem. Also clears any
	 * per-file Application.bx cache entries underneath that workspace root.
	 */
	public static void invalidate( Path workspaceRoot ) {
		Path normRoot = workspaceRoot.toAbsolutePath().normalize();
		cache.remove( normRoot );
		fileCache.keySet().removeIf( p -> p.startsWith( normRoot ) );
	}

	/**
	 * Invalidate the per-file cache entry for the given Application.bx (or
	 * Application.cfc) path so the next {@link #resolveForFile} call re-reads
	 * it from disk.
	 */
	public static void invalidateFile( Path appBxPath ) {
		fileCache.remove( appBxPath.toAbsolutePath().normalize() );
	}

	/**
	 * Resolve a per-file {@link MappingConfig} for the given source file.
	 *
	 * <p>
	 * Walks upward from the file's parent directory toward {@code workspaceRoot},
	 * looking for the nearest {@code Application.bx} or {@code Application.cfc}
	 * (case-insensitive). When found, static {@code this.mappings} entries are
	 * extracted, resolved relative to the Application.bx directory, and merged
	 * with the workspace-level config from {@link #resolve(Path)}. Application.bx
	 * keys take priority on collision.
	 *
	 * <p>
	 * The result is cached by the Application.bx path so repeated calls are cheap.
	 * When no Application.bx is found the workspace-level config is returned.
	 *
	 * @param filePath      the source file being analysed
	 * @param workspaceRoot the workspace root (walk-up boundary, inclusive)
	 *
	 * @return merged MappingConfig (never null)
	 */
	public static MappingConfig resolveForFile( Path filePath, Path workspaceRoot ) {
		return resolveForFile( filePath, workspaceRoot, Collections.emptyMap() );
	}

	/**
	 * Resolve a per-file {@link MappingConfig} for the given source file,
	 * overlaying VSCode settings mappings with the highest precedence.
	 *
	 * <p>
	 * VSCode mappings override both {@code Application.bx} / {@code Application.cfc}
	 * and {@code boxlang.json} entries. Null or empty values in
	 * {@code vscodeMappings} remove inherited mappings.
	 *
	 * @param filePath       the source file being analysed
	 * @param workspaceRoot  the workspace root (walk-up boundary, inclusive)
	 * @param vscodeMappings raw virtual-key → path-string map from VSCode settings
	 *
	 * @return merged MappingConfig (never null)
	 */
	public static MappingConfig resolveForFile( Path filePath, Path workspaceRoot, Map<String, String> vscodeMappings ) {
		Path	normalRoot	= workspaceRoot.toAbsolutePath().normalize();
		Path	dir			= filePath.toAbsolutePath().normalize().getParent();

		// Walk upward until we hit the workspace boundary (inclusive)
		while ( dir != null ) {
			if ( !dir.startsWith( normalRoot ) && !dir.equals( normalRoot ) ) {
				break;
			}

			// Look for Application.bx or Application.cfc in this directory
			Path appBx = findApplicationBx( dir );
			if ( appBx != null ) {
				boolean hasVscodeMappings = vscodeMappings != null && !vscodeMappings.isEmpty();
				if ( hasVscodeMappings ) {
					// Bypass cache when vscode mappings are present to avoid stale results
					return mergeWithApplicationBx( appBx, workspaceRoot, vscodeMappings );
				}
				return fileCache.computeIfAbsent( appBx, k -> mergeWithApplicationBx( k, workspaceRoot, Collections.emptyMap() ) );
			}

			if ( dir.equals( normalRoot ) ) {
				break;
			}
			dir = dir.getParent();
		}

		// No Application.bx found within workspace — fall back to base config
		return resolve( workspaceRoot, vscodeMappings );
	}

	// ───────────────────────────────────────────────────────────────────────────
	// Private helpers
	// ───────────────────────────────────────────────────────────────────────────

	/**
	 * Find Application.bx or Application.cfc (case-insensitive) in {@code dir}.
	 * Returns null if none is present.
	 */
	private static Path findApplicationBx( Path dir ) {
		try ( java.util.stream.Stream<Path> files = Files.list( dir ) ) {
			return files
			    .filter( p -> Files.isRegularFile( p ) )
			    .filter( p -> {
				    String name = p.getFileName().toString();
				    return name.equalsIgnoreCase( "Application.bx" ) ||
				        name.equalsIgnoreCase( "Application.cfc" );
			    } )
			    .findFirst()
			    .orElse( null );
		} catch ( IOException e ) {
			return null;
		}
	}

	/**
	 * Merge the workspace-level config with static entries from the given
	 * Application.bx file. Application.bx entries override base config on collision.
	 * When vscodeMappings are provided, they are applied on top with the highest
	 * precedence.
	 *
	 * <p>
	 * ColdBox implicit module mappings are injected at the lowest priority
	 * (below boxlang.json). Precedence stack:
	 * <ol>
	 * <li>VSCode mappings (highest)
	 * <li>Application.bx
	 * <li>boxlang.json
	 * <li>ColdBox implicit modules (lowest)
	 * </ol>
	 */
	private static MappingConfig mergeWithApplicationBx( Path appBxPath, Path workspaceRoot, Map<String, String> vscodeMappings ) {
		MappingConfig		base		= resolve( workspaceRoot );
		Map<String, String>	rawMappings	= ApplicationBxMappingExtractor.extract( appBxPath );
		Path				appDir		= appBxPath.getParent();

		// Resolve raw paths relative to Application.bx's directory
		Map<String, Path>	appMappings	= new java.util.LinkedHashMap<>();
		for ( Map.Entry<String, String> entry : rawMappings.entrySet() ) {
			Path resolved = resolvePath( entry.getValue(), appDir );
			if ( resolved != null ) {
				appMappings.put( entry.getKey(), resolved );
			}
		}

		// Merge precedence: ColdBox implicit (lowest) → boxlang.json → Application.bx
		Map<String, Path> merged = new java.util.LinkedHashMap<>();

		// 1. ColdBox implicit module mappings (lowest priority)
		if ( ColdBoxDetector.isColdBoxApp( appDir ) ) {
			merged.putAll( ColdBoxDetector.discoverModuleMappings( appDir ) );
		}

		// 2. boxlang.json overrides ColdBox implicit
		merged.putAll( base.getMappings() );

		// 3. Application.bx overrides both
		merged.putAll( appMappings );

		MappingConfig intermediate = new MappingConfig( merged, base.getClassPaths(), base.getModulesDirectory(), workspaceRoot );

		// 4. VSCode mappings on top with highest precedence
		if ( vscodeMappings != null && !vscodeMappings.isEmpty() ) {
			return mergeVscodeMappings( intermediate, vscodeMappings, workspaceRoot );
		}
		return intermediate;
	}

	/**
	 * Overlay VSCode mappings on top of a base MappingConfig. VSCode mappings have
	 * the highest precedence. Null or empty values remove inherited mappings.
	 */
	private static MappingConfig mergeVscodeMappings( MappingConfig base, Map<String, String> vscodeMappings, Path workspaceRoot ) {
		// Resolve vscode paths relative to workspace root
		Map<String, Path> resolvedVscode = new java.util.LinkedHashMap<>();
		for ( Map.Entry<String, String> entry : vscodeMappings.entrySet() ) {
			String	key		= entry.getKey();
			String	rawPath	= entry.getValue();
			if ( rawPath != null && !rawPath.isEmpty() ) {
				resolvedVscode.put( key, resolvePath( rawPath, workspaceRoot ) );
			}
		}

		// Merge: base first, then vscode overrides, then remove null/empty keys
		Map<String, Path> merged = new java.util.LinkedHashMap<>( base.getMappings() );
		for ( Map.Entry<String, String> entry : vscodeMappings.entrySet() ) {
			String key = entry.getKey();
			if ( entry.getValue() == null || entry.getValue().isEmpty() ) {
				merged.remove( key );
			} else if ( resolvedVscode.containsKey( key ) ) {
				merged.put( key, resolvedVscode.get( key ) );
			}
		}

		return new MappingConfig( merged, base.getClassPaths(), base.getModulesDirectory(), workspaceRoot );
	}

	private static MappingConfig computeConfig( Path workspaceRoot ) {
		Path			configFile	= findConfigFile( workspaceRoot );
		MappingConfig	base		= configFile == null
		    ? emptyConfig( workspaceRoot )
		    : parseConfig( configFile, workspaceRoot );

		// Inject ColdBox implicit module mappings at workspace level so that
		// ProjectIndexVisitor.computeFQN() can resolve module files correctly
		// even when resolveForFile() has not been called.
		if ( ColdBoxDetector.isColdBoxApp( workspaceRoot ) ) {
			Map<String, Path> merged = new java.util.LinkedHashMap<>();
			merged.putAll( ColdBoxDetector.discoverModuleMappings( workspaceRoot ) );
			merged.putAll( base.getMappings() );
			return new MappingConfig(
			    merged,
			    base.getClassPaths(),
			    base.getModulesDirectory(),
			    workspaceRoot
			);
		}

		return base;
	}

	/**
	 * Walk up from workspaceRoot until a boxlang.json is found, or return null.
	 */
	private static Path findConfigFile( Path startDir ) {
		Path dir = startDir.toAbsolutePath().normalize();
		while ( dir != null ) {
			Path candidate = dir.resolve( "boxlang.json" );
			if ( Files.isRegularFile( candidate ) ) {
				return candidate;
			}
			dir = dir.getParent();
		}
		return null;
	}

	private static MappingConfig parseConfig( Path configFile, Path workspaceRoot ) {
		String raw;
		try {
			raw = Files.readString( configFile );
		} catch ( IOException e ) {
			return emptyConfig( workspaceRoot );
		}

		String				stripped			= stripLineComments( raw );
		JsonObject			root				= JsonParser.parseString( stripped ).getAsJsonObject();
		Path				configDir			= configFile.getParent();

		Map<String, Path>	mappings			= parseMappings( root, configDir, workspaceRoot );
		List<Path>			classPaths			= parsePathArray( root, "classPaths", configDir, workspaceRoot );
		List<Path>			modulesDirectory	= parsePathArray( root, "modulesDirectory", configDir, workspaceRoot );

		return new MappingConfig( mappings, classPaths, modulesDirectory, workspaceRoot );
	}

	private static String stripLineComments( String json ) {
		StringBuilder	sb		= new StringBuilder();
		boolean			inStr	= false;
		int				i		= 0;
		while ( i < json.length() ) {
			char c = json.charAt( i );
			if ( c == '\\' && inStr ) {
				sb.append( c );
				i++;
				if ( i < json.length() ) {
					sb.append( json.charAt( i ) );
					i++;
				}
				continue;
			}
			if ( c == '"' ) {
				inStr = !inStr;
				sb.append( c );
				i++;
				continue;
			}
			if ( !inStr && c == '/' && i + 1 < json.length() && json.charAt( i + 1 ) == '/' ) {
				// skip to end of line
				while ( i < json.length() && json.charAt( i ) != '\n' ) {
					i++;
				}
				continue;
			}
			sb.append( c );
			i++;
		}
		return sb.toString();
	}

	private static Map<String, Path> parseMappings( JsonObject root, Path configDir, Path workspaceRoot ) {
		if ( !root.has( "mappings" ) || !root.get( "mappings" ).isJsonObject() ) {
			return Collections.emptyMap();
		}
		JsonObject			mappingsObj	= root.getAsJsonObject( "mappings" );
		Map<String, Path>	result		= new HashMap<>();
		for ( Map.Entry<String, JsonElement> entry : mappingsObj.entrySet() ) {
			String	key			= entry.getKey();
			String	rawVal		= entry.getValue().getAsString();
			String	expanded	= expandVariables( rawVal, workspaceRoot );
			result.put( key, resolvePath( expanded, configDir ) );
		}
		return result;
	}

	private static List<Path> parsePathArray( JsonObject root, String field, Path configDir, Path workspaceRoot ) {
		if ( !root.has( field ) || !root.get( field ).isJsonArray() ) {
			return Collections.emptyList();
		}
		JsonArray	arr		= root.getAsJsonArray( field );
		List<Path>	result	= new ArrayList<>();
		for ( JsonElement el : arr ) {
			String	rawVal		= el.getAsString();
			String	expanded	= expandVariables( rawVal, workspaceRoot );
			result.add( resolvePath( expanded, configDir ) );
		}
		return result;
	}

	private static String expandVariables( String value, Path workspaceRoot ) {
		// ${user-dir}
		value = value.replace( "${user-dir}", workspaceRoot.toAbsolutePath().normalize().toString() );

		// ${boxlang-home}
		try {
			String bxHome = ortus.boxlang.runtime.BoxRuntime.getInstance().getRuntimeHome().toAbsolutePath().toString();
			value = value.replace( "${boxlang-home}", bxHome );
		} catch ( Exception ignored ) {
			// BoxRuntime not available in this context — leave token as-is
		}

		// ${env.VAR_NAME:default}
		java.util.regex.Matcher	m	= java.util.regex.Pattern
		    .compile( "\\$\\{env\\.([^}:]+)(?::([^}]*))?\\}" )
		    .matcher( value );
		StringBuffer			sb	= new StringBuffer();
		while ( m.find() ) {
			String	varName		= m.group( 1 );
			String	defaultVal	= m.group( 2 ) != null ? m.group( 2 ) : "";
			String	envVal		= System.getenv( varName );
			m.appendReplacement( sb, java.util.regex.Matcher.quoteReplacement( envVal != null ? envVal : defaultVal ) );
		}
		m.appendTail( sb );
		return sb.toString();
	}

	private static Path resolvePath( String val, Path configDir ) {
		Path p = Path.of( val );
		if ( p.isAbsolute() ) {
			return p.normalize();
		}
		return configDir.resolve( val ).toAbsolutePath().normalize();
	}

	private static MappingConfig emptyConfig( Path workspaceRoot ) {
		return new MappingConfig( Collections.emptyMap(), Collections.emptyList(), Collections.emptyList(), workspaceRoot );
	}
}
