package ortus.boxlang.lsp.workspace;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.Range;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import ortus.boxlang.lsp.App;

/**
 * Provides symbol caching functionality for the language server.
 * Stores and retrieves lightweight symbol representations across restarts.
 */
public class SymbolProvider {

	private static final String								CACHE_FILE_NAME	= ".boxlang-symbols-cache.json";
	private final ConcurrentMap<String, List<ClassSymbol>>	symbolCache		= new ConcurrentHashMap<>();
	private final Gson										gson;
	private Path											cacheFilePath;

	/**
	 * Custom TypeAdapter for Instant to handle Java 21 module restrictions
	 */
	private static class InstantTypeAdapter implements JsonSerializer<Instant>, JsonDeserializer<Instant> {

		@Override
		public JsonElement serialize( Instant src, java.lang.reflect.Type typeOfSrc, JsonSerializationContext context ) {
			return new JsonPrimitive( src.toString() );
		}

		@Override
		public Instant deserialize( JsonElement json, java.lang.reflect.Type typeOfT, JsonDeserializationContext context ) {
			return Instant.parse( json.getAsString() );
		}
	}

	public SymbolProvider() {
		this.gson = new GsonBuilder()
		    .setPrettyPrinting()
		    .registerTypeAdapter( Instant.class, new InstantTypeAdapter() )
		    .create();
	}

	/**
	 * Initialize the symbol provider with a workspace root path.
	 * 
	 * @param workspaceRoot The root path of the workspace where the cache file will be stored
	 */
	public void initialize( Path workspaceRoot ) {
		this.cacheFilePath = workspaceRoot.resolve( CACHE_FILE_NAME );
		loadCache();
	}

	/**
	 * Find class symbols that match the given completion text.
	 * 
	 * @param filePath       The URI of the file being analyzed
	 * @param completionText The text to search for in class names (can be partial)
	 * 
	 * @return List of matching class symbols
	 */
	public List<ClassSymbol> findClassSymbols( URI filePath, String completionText ) {
		if ( completionText == null || completionText.trim().isEmpty() ) {
			return new ArrayList<>();
		}

		String searchText = completionText.toLowerCase().trim();

		return symbolCache.values().stream()
		    .flatMap( List::stream )
		    .filter( symbol -> {
			    // Check if the class name contains the completion text (case-insensitive)
			    return symbol.name().toLowerCase().contains( searchText ) ||
			        symbol.name().toLowerCase().startsWith( searchText );
		    } )
		    .collect( Collectors.toList() );
	}

	/**
	 * Add or update class symbols for a file.
	 * 
	 * @param fileUri The URI of the file
	 * @param symbols List of class symbols found in the file
	 */
	public void addClassSymbols( String fileUri, List<ClassSymbol> symbols ) {
		symbolCache.put( fileUri, new ArrayList<>( symbols ) );
		saveCache();
	}

	/**
	 * Remove symbols for a file (e.g., when file is deleted).
	 * 
	 * @param fileUri The URI of the file to remove symbols for
	 */
	public void removeSymbols( String fileUri ) {
		symbolCache.remove( fileUri );
		saveCache();
	}

	/**
	 * Clear all cached symbols.
	 */
	public void clearCache() {
		symbolCache.clear();
		saveCache();
	}

	/**
	 * Get all cached class symbols.
	 * 
	 * @return List of all cached class symbols
	 */
	public List<ClassSymbol> getAllClassSymbols() {
		return symbolCache.values().stream()
		    .flatMap( List::stream )
		    .collect( Collectors.toList() );
	}

	/**
	 * Load the symbol cache from the JSON file.
	 */
	private void loadCache() {
		if ( cacheFilePath == null || !Files.exists( cacheFilePath ) ) {
			if ( App.logger != null ) {
				App.logger.debug( "Symbol cache file does not exist, starting with empty cache" );
			}
			return;
		}

		try ( FileReader reader = new FileReader( cacheFilePath.toFile() ) ) {
			JsonElement element = JsonParser.parseReader( reader );
			if ( !element.isJsonObject() ) {
				if ( App.logger != null ) {
					App.logger.warn( "Invalid cache file format, starting with empty cache" );
				}
				return;
			}

			JsonObject cacheObject = element.getAsJsonObject();

			for ( String fileUri : cacheObject.keySet() ) {
				JsonArray			symbolsArray	= cacheObject.getAsJsonArray( fileUri );
				List<ClassSymbol>	symbols			= new ArrayList<>();

				for ( JsonElement symbolElement : symbolsArray ) {
					try {
						ClassSymbol symbol = gson.fromJson( symbolElement, ClassSymbol.class );
						symbols.add( symbol );
					} catch ( Exception e ) {
						if ( App.logger != null ) {
							App.logger.warn( "Failed to parse symbol from cache: " + e.getMessage() );
						}
					}
				}

				symbolCache.put( fileUri, symbols );
			}

			if ( App.logger != null ) {
				App.logger.debug( "Loaded {} files with symbols from cache", symbolCache.size() );
			}

		} catch ( IOException e ) {
			if ( App.logger != null ) {
				App.logger.error( "Failed to load symbol cache: " + e.getMessage(), e );
			}
		}
	}

	/**
	 * Save the symbol cache to the JSON file.
	 */
	private void saveCache() {
		if ( cacheFilePath == null ) {
			if ( App.logger != null ) {
				App.logger.warn( "Cache file path not initialized, cannot save cache" );
			}
			return;
		}

		try {
			// Ensure parent directory exists
			Files.createDirectories( cacheFilePath.getParent() );

			JsonObject cacheObject = new JsonObject();

			for ( String fileUri : symbolCache.keySet() ) {
				JsonArray			symbolsArray	= new JsonArray();
				List<ClassSymbol>	symbols			= symbolCache.get( fileUri );

				for ( ClassSymbol symbol : symbols ) {
					JsonElement symbolElement = gson.toJsonTree( symbol );
					symbolsArray.add( symbolElement );
				}

				cacheObject.add( fileUri, symbolsArray );
			}

			try ( FileWriter writer = new FileWriter( cacheFilePath.toFile() ) ) {
				gson.toJson( cacheObject, writer );
			}

			if ( App.logger != null ) {
				App.logger.debug( "Saved symbol cache with {} files", symbolCache.size() );
			}

		} catch ( IOException e ) {
			if ( App.logger != null ) {
				App.logger.error( "Failed to save symbol cache: " + e.getMessage(), e );
			}
		}
	}
}