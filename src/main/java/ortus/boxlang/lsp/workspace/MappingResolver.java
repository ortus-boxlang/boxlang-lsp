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

import ortus.boxlang.lsp.lint.LintConfig;
import ortus.boxlang.lsp.lint.LintConfigLoader;

public class MappingResolver {

	private static final Map<Path, MappingConfig> cache = new ConcurrentHashMap<>();

	private record FileConfigKey( Path directory, Path workspaceRoot ) {
	}

	/** Cache scoped by source directory and workspace boundary, independently of client overrides. */
	private static final Map<FileConfigKey, MappingConfig> fileCache = com.google.common.cache.CacheBuilder.newBuilder()
	    .maximumSize( 256 ).<FileConfigKey, MappingConfig>build().asMap();

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
	 * per-directory mapping cache entries underneath that workspace root.
	 */
	public static void invalidate( Path workspaceRoot ) {
		Path normRoot = workspaceRoot.toAbsolutePath().normalize();
		cache.remove( normRoot );
		fileCache.keySet().removeIf( key -> key.directory().startsWith( normRoot ) || key.workspaceRoot().startsWith( normRoot ) );
	}

	/**
	 * Invalidate directory-scoped results below the given Application.bx,
	 * Application.cfc, or nested .bxlint.json so the next resolution re-reads it.
	 */
	public static void invalidateFile( Path appBxPath ) {
		Path directory = appBxPath.toAbsolutePath().normalize().getParent();
		fileCache.keySet().removeIf( key -> key.directory().startsWith( directory ) );
	}

	/**
	 * Resolve a per-file {@link MappingConfig} for the given source file.
	 *
	 * <p>
	 * Inherits .bxlint.json mappings from the workspace root down to the source
	 * directory; nearer virtual keys override ancestors, and each relative path
	 * uses its config's directory. The nearest Application.bx / Application.cfc
	 * then overrides lint mappings. Discovery never walks above workspaceRoot.
	 *
	 * <p>
	 * Results are cached by source directory and workspace boundary (256 entries).
	 * VSCode overrides are applied separately and remain highest priority.
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
		Path	directory	= filePath.toAbsolutePath().normalize().getParent();
		if ( directory == null || !directory.startsWith( normalRoot ) ) {
			return resolve( normalRoot, vscodeMappings );
		}
		MappingConfig base = fileCache.computeIfAbsent( new FileConfigKey( directory, normalRoot ), MappingResolver::computeFileConfig );
		return vscodeMappings == null || vscodeMappings.isEmpty() ? base : mergeVscodeMappings( base, vscodeMappings, normalRoot );
	}

	private static MappingConfig computeFileConfig( FileConfigKey key ) {
		Path			root		= key.workspaceRoot();
		MappingConfig	base		= resolve( root );
		List<Path>		lintFiles	= new ArrayList<>();
		Path			application	= null;
		for ( Path directory = key.directory(); directory != null && directory.startsWith( root ); directory = directory.getParent() ) {
			if ( application == null )
				application = findApplicationBx( directory );
			Path lint = directory.resolve( LintConfigLoader.CONFIG_FILENAME );
			if ( !directory.equals( root ) && Files.isRegularFile( lint ) )
				lintFiles.add( lint );
		}
		Collections.reverse( lintFiles );
		Map<String, Path> mappings = new java.util.LinkedHashMap<>( base.getMappings() );
		for ( Path lint : lintFiles ) {
			try {
				mergeMappings( mappings, parseConfig( lint, root ).getMappings() );
			} catch ( RuntimeException e ) {
				ortus.boxlang.lsp.App.logger.warn( "Unable to read nested lint mappings from {}", lint, e );
			}
		}
		base = new MappingConfig( mappings, base.getClassPaths(), base.getModulesDirectory(), root );
		return application == null ? base : mergeWithApplicationBx( application, root, base );
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
	 * Merge the effective workspace/nested lint config with static entries from
	 * Application.bx. Application.bx entries override base config on collision.
	 *
	 * <p>
	 * ColdBox implicit module mappings are injected at the lowest priority
	 * (below boxlang.json). Precedence stack:
	 * <ol>
	 * <li>VSCode mappings (highest)
	 * <li>Application.bx
	 * <li>.bxlint.json mappings
	 * <li>boxlang.json
	 * <li>ColdBox implicit modules (lowest)
	 * </ol>
	 */
	private static MappingConfig mergeWithApplicationBx( Path appBxPath, Path workspaceRoot, MappingConfig base ) {
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

		// Merge precedence: ColdBox implicit (lowest) → boxlang.json → .bxlint.json → Application.bx
		Map<String, Path> merged = new java.util.LinkedHashMap<>();

		// 1. ColdBox implicit module mappings (lowest priority)
		if ( ColdBoxDetector.isColdBoxApp( appDir ) ) {
			merged.putAll( ColdBoxDetector.discoverModuleMappings( appDir ) );
		}

		// 2. boxlang.json overrides ColdBox implicit
		mergeMappings( merged, base.getMappings() );

		// 3. Application.bx overrides lower-priority config layers
		mergeMappings( merged, appMappings );

		return new MappingConfig( merged, base.getClassPaths(), base.getModulesDirectory(), workspaceRoot );
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
		mergeMappings( merged, resolvedVscode );
		for ( Map.Entry<String, String> entry : vscodeMappings.entrySet() ) {
			if ( entry.getValue() == null || entry.getValue().isEmpty() ) {
				merged.keySet().removeIf( key -> normalizeMappingKey( key ).equalsIgnoreCase( normalizeMappingKey( entry.getKey() ) ) );
			}
		}

		return new MappingConfig( merged, base.getClassPaths(), base.getModulesDirectory(), workspaceRoot );
	}

	public static String normalizeMappingKey( String key ) {
		return key == null ? "" : key.replaceAll( "^/+", "" ).replace( '/', '.' );
	}

	private static void mergeMappings( Map<String, Path> target, Map<String, Path> overrides ) {
		overrides.forEach( ( key, path ) -> {
			target.keySet().removeIf( oldKey -> normalizeMappingKey( oldKey ).equalsIgnoreCase( normalizeMappingKey( key ) ) );
			target.put( key, path );
		} );
	}

	private static MappingConfig computeConfig( Path workspaceRoot ) {
		Path			configFile	= findConfigFile( workspaceRoot );
		MappingConfig	base		= configFile == null
		    ? emptyConfig( workspaceRoot )
		    : parseConfig( configFile, workspaceRoot );
		MappingConfig	withLint	= mergeLintMappings( base, workspaceRoot );

		// Inject ColdBox implicit module mappings at workspace level so that
		// ProjectIndexVisitor.computeFQN() can resolve module files correctly
		// even when resolveForFile() has not been called.
		if ( ColdBoxDetector.isColdBoxApp( workspaceRoot ) ) {
			Map<String, Path> merged = new java.util.LinkedHashMap<>();
			merged.putAll( ColdBoxDetector.discoverModuleMappings( workspaceRoot ) );
			merged.putAll( withLint.getMappings() );
			return new MappingConfig(
			    merged,
			    withLint.getClassPaths(),
			    withLint.getModulesDirectory(),
			    workspaceRoot
			);
		}

		return withLint;
	}

	private static MappingConfig mergeLintMappings( MappingConfig base, Path workspaceRoot ) {
		LintConfig lintConfig = LintConfigLoader.get( workspaceRoot );
		if ( lintConfig == null || lintConfig.mappings == null || lintConfig.mappings.isEmpty() ) {
			return base;
		}

		Map<String, Path> merged = new java.util.LinkedHashMap<>( base.getMappings() );
		for ( Map.Entry<String, String> entry : lintConfig.mappings.entrySet() ) {
			String rawPath = entry.getValue();
			if ( rawPath == null || rawPath.isBlank() ) {
				continue;
			}

			Path resolved = resolvePath( rawPath, workspaceRoot );
			if ( resolved != null ) {
				merged.put( entry.getKey(), resolved );
			}
		}

		return new MappingConfig( merged, base.getClassPaths(), base.getModulesDirectory(), workspaceRoot );
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
