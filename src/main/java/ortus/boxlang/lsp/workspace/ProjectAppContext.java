package ortus.boxlang.lsp.workspace;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * App-aware context sent by LSP clients during initialize.
 */
public record ProjectAppContext(
    Path rootPath,
    Map<String, Path> mappings,
    List<Path> moduleDirs,
    String contextHash ) {

	private static final String DEFAULT_HASH = "default";
	private static final Pattern MODULE_MAPPING_PATTERN = Pattern.compile(
	    "(?is)this\\.(?:mapping|cfmapping)\\s*=\\s*(['\"])([^'\"]+)\\1"
	);

	public ProjectAppContext {
		rootPath	= normalizePath( rootPath );
		mappings	= Map.copyOf( mappings );
		moduleDirs	= List.copyOf( moduleDirs );
		contextHash	= contextHash == null || contextHash.isBlank()
		    ? computeFallbackHash( rootPath, mappings, moduleDirs )
		    : contextHash;
	}

	public static ProjectAppContext empty() {
		return new ProjectAppContext(
		    null,
		    Map.of(),
		    List.of(),
		    DEFAULT_HASH
		);
	}

	@SuppressWarnings( "unchecked" )
	public static ProjectAppContext fromInitializationOptions( Object initializationOptions, Path workspaceRoot ) {
		Map<?, ?> rootOptions = asMap( initializationOptions );
		if ( rootOptions == null ) {
			return emptyWithRoot( workspaceRoot );
		}

		Map<?, ?> boxlang = asMap( rootOptions.get( "boxlang" ) );
		if ( boxlang == null ) {
			return emptyWithRoot( workspaceRoot );
		}
		Map<?, ?> appContext = asMap( boxlang.get( "appContext" ) );
		if ( appContext == null ) {
			return emptyWithRoot( workspaceRoot );
		}

		Path rootPath = toPath( appContext.get( "rootPath" ), workspaceRoot, workspaceRoot );
		if ( rootPath == null ) {
			rootPath = normalizePath( workspaceRoot );
		}
		if ( rootPath == null ) {
			rootPath = Paths.get( "." ).toAbsolutePath().normalize();
		}

		Map<String, Path>	mappings	= parseMappings( appContext.get( "mappings" ), rootPath );
		List<Path>			moduleDirs	= parseModuleDirs( appContext.get( "moduleDirs" ), rootPath );
		String				contextHash	= asString( appContext.get( "contextHash" ) );
		mergeModuleDerivedMappings( mappings, moduleDirs );

		return new ProjectAppContext( rootPath, mappings, moduleDirs, contextHash );
	}

	private static ProjectAppContext emptyWithRoot( Path workspaceRoot ) {
		Path root = workspaceRoot != null
		    ? normalizePath( workspaceRoot )
		    : Paths.get( "." ).toAbsolutePath().normalize();
		return new ProjectAppContext( root, Map.of(), List.of(), DEFAULT_HASH );
	}

	@SuppressWarnings( "unchecked" )
	private static Map<?, ?> asMap( Object value ) {
		value = normalizeJsonLike( value );
		return value instanceof Map<?, ?> map ? map : null;
	}

	@SuppressWarnings( "unchecked" )
	private static List<?> asList( Object value ) {
		value = normalizeJsonLike( value );
		return value instanceof List<?> list ? list : null;
	}

	private static Object normalizeJsonLike( Object value ) {
		if ( value == null ) {
			return null;
		}
		if ( value instanceof JsonNull ) {
			return null;
		}
		if ( value instanceof JsonObject jsonObject ) {
			Map<String, Object> converted = new LinkedHashMap<>();
			for ( Map.Entry<String, JsonElement> entry : jsonObject.entrySet() ) {
				converted.put( entry.getKey(), normalizeJsonLike( entry.getValue() ) );
			}
			return converted;
		}
		if ( value instanceof JsonArray jsonArray ) {
			List<Object> converted = new ArrayList<>();
			for ( JsonElement element : jsonArray ) {
				converted.add( normalizeJsonLike( element ) );
			}
			return converted;
		}
		if ( value instanceof JsonPrimitive primitive ) {
			if ( primitive.isBoolean() ) {
				return primitive.getAsBoolean();
			}
			if ( primitive.isNumber() ) {
				return primitive.getAsString();
			}
			return primitive.getAsString();
		}
		if ( value instanceof JsonElement element ) {
			return normalizeJsonLike( element.deepCopy() );
		}
		return value;
	}

	private static Map<String, Path> parseMappings( Object rawMappings, Path rootPath ) {
		Map<?, ?>			source	= asMap( rawMappings );
		Map<String, Path>	parsed	= new LinkedHashMap<>();
		if ( source == null || source.isEmpty() ) {
			return parsed;
		}
		for ( Map.Entry<?, ?> entry : source.entrySet() ) {
			String alias = canonicalizeAlias( asString( entry.getKey() ) );
			if ( alias.isEmpty() ) {
				continue;
			}
			Path path = toPath( entry.getValue(), rootPath, rootPath );
			if ( path == null ) {
				continue;
			}
			parsed.put( alias, path );
		}
		return parsed;
	}

	private static List<Path> parseModuleDirs( Object rawModuleDirs, Path rootPath ) {
		List<?> moduleDirValues = asList( rawModuleDirs );
		if ( moduleDirValues == null || moduleDirValues.isEmpty() ) {
			return List.of();
		}
		List<Path> parsed = new ArrayList<>();
		for ( Object value : moduleDirValues ) {
			Path path = toPath( value, rootPath, rootPath );
			if ( path != null ) {
				parsed.add( path );
			}
		}
		return parsed.stream().distinct().collect( Collectors.toList() );
	}

	private static void mergeModuleDerivedMappings( Map<String, Path> mappings, List<Path> moduleDirs ) {
		if ( mappings == null || moduleDirs == null || moduleDirs.isEmpty() ) {
			return;
		}
		for ( Path moduleDir : moduleDirs ) {
			if ( moduleDir == null || !Files.isDirectory( moduleDir ) ) {
				continue;
			}
			mergeModuleMappingForRoot( mappings, moduleDir );
			try ( var stream = Files.list( moduleDir ) ) {
				stream.filter( Files::isDirectory ).forEach( child -> mergeModuleMappingForRoot( mappings, child ) );
			} catch ( IOException ignored ) {
				// Best effort; explicit mappings from initialize options still apply.
			}
		}
	}

	private static void mergeModuleMappingForRoot( Map<String, Path> mappings, Path moduleRoot ) {
		Path moduleConfig = Files.isRegularFile( moduleRoot.resolve( "ModuleConfig.bx" ) )
		    ? moduleRoot.resolve( "ModuleConfig.bx" )
		    : moduleRoot.resolve( "ModuleConfig.cfc" );
		if ( !Files.isRegularFile( moduleConfig ) ) {
			return;
		}
		String alias = extractModuleMappingAlias( moduleConfig );
		if ( alias == null || alias.isBlank() ) {
			return;
		}
		mappings.putIfAbsent( canonicalizeAlias( alias ), moduleRoot.toAbsolutePath().normalize() );
	}

	private static String extractModuleMappingAlias( Path moduleConfigPath ) {
		try {
			String	contents	= Files.readString( moduleConfigPath, StandardCharsets.UTF_8 );
			Matcher	matcher		= MODULE_MAPPING_PATTERN.matcher( contents );
			if ( matcher.find() ) {
				return matcher.group( 2 );
			}
		} catch ( IOException ignored ) {
			// Best effort only.
		}
		return null;
	}

	private static String canonicalizeAlias( String rawAlias ) {
		if ( rawAlias == null || rawAlias.isBlank() ) {
			return "";
		}
		String alias = rawAlias.trim().replace( '\\', '/' );
		while ( alias.startsWith( "/" ) ) {
			alias = alias.substring( 1 );
		}
		while ( alias.endsWith( "/" ) ) {
			alias = alias.substring( 0, alias.length() - 1 );
		}
		return alias.toLowerCase( Locale.ROOT );
	}

	private static String asString( Object value ) {
		if ( value == null ) {
			return null;
		}
		String text = String.valueOf( value ).trim();
		return text.isEmpty() ? null : text;
	}

	private static Path toPath( Object rawValue, Path sourceDir, Path rootPath ) {
		String rawPath = asString( rawValue );
		if ( rawPath == null ) {
			return null;
		}
		try {
			Path candidate = Paths.get( rawPath );
			if ( candidate.isAbsolute() ) {
				return normalizePath( candidate );
			}
			if ( rawPath.startsWith( "/" ) || rawPath.startsWith( "\\" ) ) {
				String relativeFromRoot = rawPath.replaceFirst( "^[\\\\/]+", "" );
				return normalizePath( rootPath.resolve( relativeFromRoot ) );
			}
			return normalizePath( sourceDir.resolve( candidate ) );
		} catch ( Exception e ) {
			return null;
		}
	}

	private static Path normalizePath( Path path ) {
		return path == null ? null : path.toAbsolutePath().normalize();
	}

	private static String computeFallbackHash( Path rootPath, Map<String, Path> mappings, List<Path> moduleDirs ) {
		return Integer.toHexString( Objects.hash( rootPath, mappings, moduleDirs ) );
	}
}
