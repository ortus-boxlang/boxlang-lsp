package ortus.boxlang.lsp.workspace.index;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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

import ortus.boxlang.compiler.parser.Parser;
import ortus.boxlang.compiler.parser.ParsingResult;
import ortus.boxlang.lsp.App;
import ortus.boxlang.runtime.BoxRuntime;

/**
 * Main project index API that provides rich metadata about all BoxLang files in the workspace.
 * Thread-safe for concurrent access during workspace parsing.
 */
public class ProjectIndex {

	// In-memory indexes for fast lookup
	private final Map<String, IndexedClass>				classesByFQN			= new ConcurrentHashMap<>();
	private final Map<String, List<IndexedClass>>		classesBySimpleName		= new ConcurrentHashMap<>();
	private final Map<String, IndexedMethod>			methodsByKey			= new ConcurrentHashMap<>();
	private final Map<String, List<IndexedMethod>>		methodsByName			= new ConcurrentHashMap<>();
	private final Map<String, IndexedProperty>			propertiesByKey			= new ConcurrentHashMap<>();
	private final Map<String, List<IndexedProperty>>	propertiesByClassName	= new ConcurrentHashMap<>();
	private final Map<String, List<IndexedClass>>		classesByFileUri		= new ConcurrentHashMap<>();
	private final Map<String, List<IndexedMethod>>		methodsByFileUri		= new ConcurrentHashMap<>();
	private final Map<String, List<IndexedProperty>>	propertiesByFileUri		= new ConcurrentHashMap<>();

	// Track file modification times for cache freshness validation
	private final Map<String, Instant>					fileModifiedTimes		= new ConcurrentHashMap<>();
	// Files that need re-indexing after cache load (stale or missing)
	private final List<String>							staleFiles				= new ArrayList<>();
	// Flag indicating if cache was corrupted and full re-index is needed
	private boolean										cacheCorrupted			= false;

	private final InheritanceGraph						inheritanceGraph		= new InheritanceGraph();
	private final Gson									gson;
	private Path										workspaceRoot;
	private Path										cacheFilePath;

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

	public ProjectIndex() {
		this.gson = new GsonBuilder()
		    .setPrettyPrinting()
		    .registerTypeAdapter( Instant.class, new InstantTypeAdapter() )
		    .create();
	}

	/**
	 * Initialize the project index with a workspace root.
	 *
	 * @param workspaceRoot The root path of the workspace
	 */
	public void initialize( Path workspaceRoot ) {
		this.workspaceRoot	= workspaceRoot;
		this.cacheFilePath	= getDefaultCacheFilePath( workspaceRoot );
		loadCache();
	}

	/**
	 * Get the default cache file path for a workspace.
	 */
	public static Path getDefaultCacheFilePath( Path workspaceRoot ) {
		return BoxRuntime.getInstance().getRuntimeHome().resolve( "index-" + sha256( workspaceRoot.toString() ).substring( 0, 16 ) + ".json" );
	}

	/**
	 * Index a single file, extracting all class, method, and property information.
	 *
	 * @param fileUri The URI of the file to index
	 */
	public void indexFile( URI fileUri ) {
		try {
			Path filePath = Paths.get( fileUri );
			if ( !Files.exists( filePath ) ) {
				return;
			}

			// Parse the file
			Parser			parser	= new Parser();
			ParsingResult	result	= parser.parse( filePath.toFile() );

			if ( result == null || result.getRoot() == null ) {
				return;
			}

			// Extract symbols using visitor
			ProjectIndexVisitor visitor = new ProjectIndexVisitor( fileUri, workspaceRoot );
			result.getRoot().accept( visitor );

			// Store extracted data
			String fileUriStr = fileUri.toString();

			// Remove old data for this file first
			removeFile( fileUri );

			// Track the file modification time for cache freshness validation
			try {
				Instant modTime = Files.getLastModifiedTime( filePath ).toInstant();
				fileModifiedTimes.put( fileUriStr, modTime );
			} catch ( IOException e ) {
				// If we can't read mod time, use current time
				fileModifiedTimes.put( fileUriStr, Instant.now() );
			}

			// Add classes
			List<IndexedClass> classes = visitor.getIndexedClasses();
			classesByFileUri.put( fileUriStr, classes );
			for ( IndexedClass indexedClass : classes ) {
				classesByFQN.put( indexedClass.fullyQualifiedName(), indexedClass );
				classesBySimpleName.computeIfAbsent( indexedClass.name().toLowerCase(), k -> new ArrayList<>() ).add( indexedClass );

				// Update inheritance graph
				inheritanceGraph.addClassRelationship(
				    indexedClass.fullyQualifiedName(),
				    indexedClass.extendsClass(),
				    indexedClass.implementsInterfaces()
				);
			}

			// Add methods
			List<IndexedMethod> methods = visitor.getIndexedMethods();
			methodsByFileUri.put( fileUriStr, methods );
			for ( IndexedMethod indexedMethod : methods ) {
				methodsByKey.put( indexedMethod.getKey(), indexedMethod );
				methodsByName.computeIfAbsent( indexedMethod.name().toLowerCase(), k -> new ArrayList<>() ).add( indexedMethod );
			}

			// Add properties
			List<IndexedProperty> properties = visitor.getIndexedProperties();
			propertiesByFileUri.put( fileUriStr, properties );
			for ( IndexedProperty indexedProperty : properties ) {
				String key = indexedProperty.containingClass().toLowerCase() + "." + indexedProperty.name().toLowerCase();
				propertiesByKey.put( key, indexedProperty );
				if ( indexedProperty.containingClass() != null ) {
					propertiesByClassName.computeIfAbsent( indexedProperty.containingClass().toLowerCase(), k -> new ArrayList<>() )
					    .add( indexedProperty );
				}
			}

		} catch ( Exception e ) {
			if ( App.logger != null ) {
				App.logger.error( "Failed to index file: " + fileUri, e );
			}
		}
	}

	/**
	 * Remove all indexed data for a file.
	 *
	 * @param fileUri The URI of the file to remove
	 */
	public void removeFile( URI fileUri ) {
		String fileUriStr = fileUri.toString();

		// Remove file modification time tracking
		fileModifiedTimes.remove( fileUriStr );

		// Remove classes
		List<IndexedClass> oldClasses = classesByFileUri.remove( fileUriStr );
		if ( oldClasses != null ) {
			for ( IndexedClass oldClass : oldClasses ) {
				classesByFQN.remove( oldClass.fullyQualifiedName() );
				List<IndexedClass> byName = classesBySimpleName.get( oldClass.name().toLowerCase() );
				if ( byName != null ) {
					byName.removeIf( c -> c.fileUri() != null && c.fileUri().equals( fileUriStr ) );
				}
				inheritanceGraph.removeClass( oldClass.fullyQualifiedName() );
			}
		}

		// Remove methods
		List<IndexedMethod> oldMethods = methodsByFileUri.remove( fileUriStr );
		if ( oldMethods != null ) {
			for ( IndexedMethod oldMethod : oldMethods ) {
				methodsByKey.remove( oldMethod.getKey() );
				List<IndexedMethod> byName = methodsByName.get( oldMethod.name().toLowerCase() );
				if ( byName != null ) {
					byName.removeIf( m -> m.fileUri() != null && m.fileUri().equals( fileUriStr ) );
				}
			}
		}

		// Remove properties
		List<IndexedProperty> oldProperties = propertiesByFileUri.remove( fileUriStr );
		if ( oldProperties != null ) {
			for ( IndexedProperty oldProperty : oldProperties ) {
				String key = oldProperty.containingClass().toLowerCase() + "." + oldProperty.name().toLowerCase();
				propertiesByKey.remove( key );
				if ( oldProperty.containingClass() != null ) {
					List<IndexedProperty> byClass = propertiesByClassName.get( oldProperty.containingClass().toLowerCase() );
					if ( byClass != null ) {
						byClass.removeIf( p -> p.fileUri() != null && p.fileUri().equals( fileUriStr ) );
					}
				}
			}
		}
	}

	/**
	 * Re-index a file (remove and add).
	 *
	 * @param fileUri The URI of the file to re-index
	 */
	public void reindexFile( URI fileUri ) {
		removeFile( fileUri );
		indexFile( fileUri );
		saveCache();
	}

	// ============ Query Methods ============

	/**
	 * Find a class by simple name.
	 *
	 * @param name The simple class name (case-insensitive)
	 *
	 * @return Optional containing the first matching class, or empty if not found
	 */
	public Optional<IndexedClass> findClassByName( String name ) {
		if ( name == null || name.isEmpty() ) {
			return Optional.empty();
		}
		List<IndexedClass> matches = classesBySimpleName.get( name.toLowerCase() );
		return matches != null && !matches.isEmpty() ? Optional.of( matches.get( 0 ) ) : Optional.empty();
	}

	/**
	 * Find all classes matching a simple name.
	 *
	 * @param name The simple class name (case-insensitive)
	 *
	 * @return List of matching classes
	 */
	public List<IndexedClass> findAllClassesByName( String name ) {
		if ( name == null || name.isEmpty() ) {
			return new ArrayList<>();
		}
		List<IndexedClass> matches = classesBySimpleName.get( name.toLowerCase() );
		return matches != null ? new ArrayList<>( matches ) : new ArrayList<>();
	}

	/**
	 * Find a class by fully qualified name.
	 *
	 * @param fqn The fully qualified class name
	 *
	 * @return Optional containing the class, or empty if not found
	 */
	public Optional<IndexedClass> findClassByFQN( String fqn ) {
		if ( fqn == null || fqn.isEmpty() ) {
			return Optional.empty();
		}
		return Optional.ofNullable( classesByFQN.get( fqn ) );
	}

	/**
	 * Find all classes that extend a given class.
	 *
	 * @param className The parent class name
	 *
	 * @return List of classes extending the specified class
	 */
	public List<IndexedClass> findClassesExtending( String className ) {
		List<String> descendants = inheritanceGraph.getDescendants( className );
		return descendants.stream()
		    .map( classesByFQN::get )
		    .filter( c -> c != null )
		    .collect( Collectors.toList() );
	}

	/**
	 * Find all classes that implement a given interface.
	 *
	 * @param interfaceName The interface name
	 *
	 * @return List of classes implementing the specified interface
	 */
	public List<IndexedClass> findClassesImplementing( String interfaceName ) {
		List<String> implementors = inheritanceGraph.getImplementors( interfaceName );
		return implementors.stream()
		    .map( classesByFQN::get )
		    .filter( c -> c != null )
		    .collect( Collectors.toList() );
	}

	/**
	 * Find methods by name.
	 *
	 * @param methodName The method name (case-insensitive)
	 *
	 * @return List of matching methods
	 */
	public List<IndexedMethod> findMethodsByName( String methodName ) {
		if ( methodName == null || methodName.isEmpty() ) {
			return new ArrayList<>();
		}
		List<IndexedMethod> matches = methodsByName.get( methodName.toLowerCase() );
		return matches != null ? new ArrayList<>( matches ) : new ArrayList<>();
	}

	/**
	 * Find a specific method by class and method name.
	 *
	 * @param className  The class name
	 * @param methodName The method name
	 *
	 * @return Optional containing the method, or empty if not found
	 */
	public Optional<IndexedMethod> findMethod( String className, String methodName ) {
		// Use lowercase key for case-insensitive lookup (BoxLang is case-insensitive)
		String key = ( className + "." + methodName ).toLowerCase();
		return Optional.ofNullable( methodsByKey.get( key ) );
	}

	/**
	 * Find a property by class and property name.
	 *
	 * @param className    The class name
	 * @param propertyName The property name
	 *
	 * @return Optional containing the property, or empty if not found
	 */
	public Optional<IndexedProperty> findProperty( String className, String propertyName ) {
		String key = className.toLowerCase() + "." + propertyName.toLowerCase();
		return Optional.ofNullable( propertiesByKey.get( key ) );
	}

	/**
	 * Find all properties of a class.
	 *
	 * @param className The class name (case-insensitive)
	 *
	 * @return List of properties for the class
	 */
	public List<IndexedProperty> findPropertiesOfClass( String className ) {
		if ( className == null || className.isEmpty() ) {
			return new ArrayList<>();
		}
		List<IndexedProperty> matches = propertiesByClassName.get( className.toLowerCase() );
		return matches != null ? new ArrayList<>( matches ) : new ArrayList<>();
	}

	/**
	 * Search for symbols matching a query string.
	 *
	 * @param query The search query (matches names containing this string)
	 *
	 * @return List of matching symbols (classes, methods, properties)
	 */
	public List<Object> searchSymbols( String query ) {
		if ( query == null || query.isEmpty() ) {
			return new ArrayList<>();
		}

		String		lowerQuery	= query.toLowerCase();
		List<Object>	results		= new ArrayList<>();

		// Search classes
		classesByFQN.values().stream()
		    .filter( c -> c.name().toLowerCase().contains( lowerQuery ) ||
		        c.fullyQualifiedName().toLowerCase().contains( lowerQuery ) )
		    .forEach( results::add );

		// Search methods
		methodsByKey.values().stream()
		    .filter( m -> m.name().toLowerCase().contains( lowerQuery ) )
		    .forEach( results::add );

		// Search properties
		propertiesByKey.values().stream()
		    .filter( p -> p.name().toLowerCase().contains( lowerQuery ) )
		    .forEach( results::add );

		return results;
	}

	/**
	 * Get all indexed classes.
	 *
	 * @return List of all indexed classes
	 */
	public List<IndexedClass> getAllClasses() {
		return new ArrayList<>( classesByFQN.values() );
	}

	/**
	 * Get all indexed methods.
	 *
	 * @return List of all indexed methods
	 */
	public List<IndexedMethod> getAllMethods() {
		return new ArrayList<>( methodsByKey.values() );
	}

	/**
	 * Get all indexed properties.
	 *
	 * @return List of all indexed properties
	 */
	public List<IndexedProperty> getAllProperties() {
		return new ArrayList<>( propertiesByKey.values() );
	}

	/**
	 * Get all indexed standalone functions (methods with no containing class).
	 * These are typically functions defined in .bxs script files.
	 *
	 * @return List of all standalone functions
	 */
	public List<IndexedMethod> getAllFunctions() {
		return methodsByKey.values().stream()
		    .filter( m -> m.containingClass() == null )
		    .collect( Collectors.toList() );
	}

	/**
	 * Find a standalone function by name.
	 * Standalone functions are those defined outside of classes (e.g., in .bxs files).
	 *
	 * @param name The function name (case-insensitive)
	 *
	 * @return Optional containing the first matching function, or empty if not found
	 */
	public Optional<IndexedMethod> findFunction( String name ) {
		if ( name == null || name.isEmpty() ) {
			return Optional.empty();
		}
		List<IndexedMethod> matches = methodsByName.get( name.toLowerCase() );
		if ( matches == null ) {
			return Optional.empty();
		}
		return matches.stream()
		    .filter( m -> m.containingClass() == null )
		    .findFirst();
	}

	/**
	 * Find all standalone functions in a specific file.
	 *
	 * @param filePath The file URI string
	 *
	 * @return List of standalone functions in the file
	 */
	public List<IndexedMethod> findFunctionsInFile( String filePath ) {
		if ( filePath == null || filePath.isEmpty() ) {
			return new ArrayList<>();
		}
		List<IndexedMethod> methods = methodsByFileUri.get( filePath );
		if ( methods == null ) {
			return new ArrayList<>();
		}
		return methods.stream()
		    .filter( m -> m.containingClass() == null )
		    .collect( Collectors.toList() );
	}

	/**
	 * Find methods that override a given method in subclasses.
	 *
	 * @param className  The class containing the original method
	 * @param methodName The method name to find overrides for
	 *
	 * @return List of methods in subclasses that override this method
	 */
	public List<IndexedMethod> findOverrides( String className, String methodName ) {
		if ( className == null || className.isEmpty() || methodName == null || methodName.isEmpty() ) {
			return new ArrayList<>();
		}

		// Get all descendants of the class
		List<String> descendants = inheritanceGraph.getDescendants( className );
		if ( descendants.isEmpty() ) {
			return new ArrayList<>();
		}

		// Find methods with the same name in descendant classes
		String lowerMethodName = methodName.toLowerCase();
		List<IndexedMethod> overrides = new ArrayList<>();

		for ( String descendant : descendants ) {
			// Look for methods in this descendant class that match the method name
			List<IndexedMethod> methods = methodsByName.get( lowerMethodName );
			if ( methods != null ) {
				for ( IndexedMethod method : methods ) {
					if ( method.containingClass() != null && method.containingClass().equals( descendant ) ) {
						overrides.add( method );
					}
				}
			}
		}

		return overrides;
	}

	/**
	 * Get all methods of a class.
	 *
	 * @param className The class name (case-insensitive)
	 *
	 * @return List of methods for the class
	 */
	public List<IndexedMethod> getMethodsOfClass( String className ) {
		if ( className == null || className.isEmpty() ) {
			return new ArrayList<>();
		}
		String lowerClassName = className.toLowerCase();
		return methodsByKey.values().stream()
		    .filter( m -> m.containingClass() != null && m.containingClass().toLowerCase().equals( lowerClassName ) )
		    .collect( Collectors.toList() );
	}

	/**
	 * Get all indexed file URIs.
	 *
	 * @return List of all indexed file URIs
	 */
	public List<String> getIndexedFiles() {
		return new ArrayList<>( fileModifiedTimes.keySet() );
	}

	/**
	 * Get all indexed files within a specific directory.
	 *
	 * @param directory The directory path
	 *
	 * @return List of file URIs within the directory
	 */
	public List<String> getFilesInDirectory( String directory ) {
		if ( directory == null || directory.isEmpty() ) {
			return new ArrayList<>();
		}
		// Normalize directory path for comparison
		String normalizedDir = directory.replace( '\\', '/' );
		if ( !normalizedDir.endsWith( "/" ) ) {
			normalizedDir = normalizedDir + "/";
		}
		final String dirPrefix = normalizedDir;

		return fileModifiedTimes.keySet().stream()
		    .filter( fileUri -> {
			    try {
				    URI uri = URI.create( fileUri );
				    String filePath = Paths.get( uri ).toString().replace( '\\', '/' );
				    return filePath.startsWith( dirPrefix ) || filePath.contains( "/" + dirPrefix );
			    } catch ( Exception e ) {
				    return false;
			    }
		    } )
		    .collect( Collectors.toList() );
	}

	/**
	 * Get files that depend on a given file.
	 * Note: Dependency tracking is not yet implemented. This returns an empty list.
	 *
	 * @param filePath The file path to check dependents for
	 *
	 * @return List of file paths that depend on the given file (currently empty)
	 */
	public List<String> getFilesDependingOn( String filePath ) {
		// TODO: Implement dependency tracking
		return new ArrayList<>();
	}

	/**
	 * Get the files that a given file depends on.
	 * Note: Dependency tracking is not yet implemented. This returns an empty list.
	 *
	 * @param filePath The file path to check dependencies for
	 *
	 * @return List of file paths that the given file depends on (currently empty)
	 */
	public List<String> getDependenciesOf( String filePath ) {
		// TODO: Implement dependency tracking
		return new ArrayList<>();
	}

	/**
	 * Get the inheritance graph for hierarchy queries.
	 *
	 * @return The inheritance graph
	 */
	public InheritanceGraph getInheritanceGraph() {
		return inheritanceGraph;
	}

	/**
	 * Get the list of file URIs that were marked as stale during cache loading.
	 * These files need to be re-indexed.
	 *
	 * @return List of stale file URIs
	 */
	public List<String> getStaleFiles() {
		return new ArrayList<>( staleFiles );
	}

	/**
	 * Check if the cache was corrupted and needs a full re-index.
	 *
	 * @return true if cache was corrupted
	 */
	public boolean isCacheCorrupted() {
		return cacheCorrupted;
	}

	/**
	 * Check if a specific file needs re-indexing.
	 *
	 * @param fileUri The URI of the file to check
	 *
	 * @return true if the file needs re-indexing (not in cache, stale, or corrupted cache)
	 */
	public boolean needsReindexing( URI fileUri ) {
		if ( cacheCorrupted ) {
			return true;
		}

		String fileUriStr = fileUri.toString();

		// If the file has no cached modification time, it needs indexing
		if ( !fileModifiedTimes.containsKey( fileUriStr ) ) {
			return true;
		}

		// If the file is in the stale list, it needs re-indexing
		return staleFiles.contains( fileUriStr );
	}

	/**
	 * Clear all indexed data.
	 */
	public void clear() {
		classesByFQN.clear();
		classesBySimpleName.clear();
		methodsByKey.clear();
		methodsByName.clear();
		propertiesByKey.clear();
		propertiesByClassName.clear();
		classesByFileUri.clear();
		methodsByFileUri.clear();
		propertiesByFileUri.clear();
		inheritanceGraph.clear();
		fileModifiedTimes.clear();
		staleFiles.clear();
		cacheCorrupted = false;
	}

	// ============ Persistence ============

	/**
	 * Load the index from the cache file, validating freshness and marking stale files.
	 */
	private void loadCache() {
		if ( cacheFilePath == null || !Files.exists( cacheFilePath ) ) {
			if ( App.logger != null ) {
				App.logger.debug( "Project index cache file does not exist, starting with empty index" );
			}
			return;
		}

		try ( FileReader reader = new FileReader( cacheFilePath.toFile() ) ) {
			JsonElement element = JsonParser.parseReader( reader );
			if ( !element.isJsonObject() ) {
				handleCacheCorruption( "Invalid project index cache file format" );
				return;
			}

			JsonObject cacheObject = element.getAsJsonObject();

			// Load file modification times first
			if ( cacheObject.has( "fileModifiedTimes" ) ) {
				JsonObject fileTimesObj = cacheObject.getAsJsonObject( "fileModifiedTimes" );
				for ( String fileUri : fileTimesObj.keySet() ) {
					try {
						Instant cachedTime = Instant.parse( fileTimesObj.get( fileUri ).getAsString() );
						fileModifiedTimes.put( fileUri, cachedTime );
					} catch ( Exception e ) {
						if ( App.logger != null ) {
							App.logger.warn( "Failed to parse file modification time from cache: " + e.getMessage() );
						}
					}
				}
			}

			// Load classes
			if ( cacheObject.has( "classes" ) ) {
				JsonArray classesArray = cacheObject.getAsJsonArray( "classes" );
				for ( JsonElement classElement : classesArray ) {
					try {
						IndexedClass indexedClass = gson.fromJson( classElement, IndexedClass.class );
						if ( indexedClass == null ) {
							continue;
						}
						classesByFQN.put( indexedClass.fullyQualifiedName(), indexedClass );
						classesBySimpleName.computeIfAbsent( indexedClass.name().toLowerCase(), k -> new ArrayList<>() ).add( indexedClass );
						if ( indexedClass.fileUri() != null ) {
							classesByFileUri.computeIfAbsent( indexedClass.fileUri(), k -> new ArrayList<>() ).add( indexedClass );
						}
						inheritanceGraph.addClassRelationship(
						    indexedClass.fullyQualifiedName(),
						    indexedClass.extendsClass(),
						    indexedClass.implementsInterfaces()
						);
					} catch ( Exception e ) {
						if ( App.logger != null ) {
							App.logger.warn( "Failed to parse class from cache: " + e.getMessage() );
						}
					}
				}
			}

			// Load methods
			if ( cacheObject.has( "methods" ) ) {
				JsonArray methodsArray = cacheObject.getAsJsonArray( "methods" );
				for ( JsonElement methodElement : methodsArray ) {
					try {
						IndexedMethod indexedMethod = gson.fromJson( methodElement, IndexedMethod.class );
						if ( indexedMethod == null ) {
							continue;
						}
						methodsByKey.put( indexedMethod.getKey(), indexedMethod );
						methodsByName.computeIfAbsent( indexedMethod.name().toLowerCase(), k -> new ArrayList<>() ).add( indexedMethod );
						if ( indexedMethod.fileUri() != null ) {
							methodsByFileUri.computeIfAbsent( indexedMethod.fileUri(), k -> new ArrayList<>() ).add( indexedMethod );
						}
					} catch ( Exception e ) {
						if ( App.logger != null ) {
							App.logger.warn( "Failed to parse method from cache: " + e.getMessage() );
						}
					}
				}
			}

			// Load properties
			if ( cacheObject.has( "properties" ) ) {
				JsonArray propertiesArray = cacheObject.getAsJsonArray( "properties" );
				for ( JsonElement propertyElement : propertiesArray ) {
					try {
						IndexedProperty indexedProperty = gson.fromJson( propertyElement, IndexedProperty.class );
						if ( indexedProperty == null ) {
							continue;
						}
						String key = indexedProperty.containingClass().toLowerCase() + "." + indexedProperty.name().toLowerCase();
						propertiesByKey.put( key, indexedProperty );
						if ( indexedProperty.containingClass() != null ) {
							propertiesByClassName.computeIfAbsent( indexedProperty.containingClass().toLowerCase(), k -> new ArrayList<>() )
							    .add( indexedProperty );
						}
						if ( indexedProperty.fileUri() != null ) {
							propertiesByFileUri.computeIfAbsent( indexedProperty.fileUri(), k -> new ArrayList<>() ).add( indexedProperty );
						}
					} catch ( Exception e ) {
						if ( App.logger != null ) {
							App.logger.warn( "Failed to parse property from cache: " + e.getMessage() );
						}
					}
				}
			}

			// Validate cache freshness for all loaded files
			validateCacheFreshness();

			if ( App.logger != null ) {
				App.logger.debug( "Loaded project index cache with {} classes, {} methods, {} properties, {} stale files",
				    classesByFQN.size(), methodsByKey.size(), propertiesByKey.size(), staleFiles.size() );
			}

		} catch ( Exception e ) {
			handleCacheCorruption( "Failed to load project index cache: " + e.getMessage() );
		}
	}

	/**
	 * Handle cache corruption by clearing and marking for full re-index.
	 */
	private void handleCacheCorruption( String reason ) {
		if ( App.logger != null ) {
			App.logger.warn( reason + ", falling back to full re-index" );
		}
		clear();
		cacheCorrupted = true;
	}

	/**
	 * Validate cache freshness by comparing cached file modification times against current.
	 * Files that have been modified since caching are marked as stale.
	 */
	private void validateCacheFreshness() {
		staleFiles.clear();

		for ( Map.Entry<String, Instant> entry : fileModifiedTimes.entrySet() ) {
			String	fileUri		= entry.getKey();
			Instant	cachedTime	= entry.getValue();

			try {
				URI		uri			= URI.create( fileUri );
				Path	filePath	= Paths.get( uri );

				if ( !Files.exists( filePath ) ) {
					// File was deleted - mark as stale and remove from index
					staleFiles.add( fileUri );
					removeFileByUri( fileUri );
					continue;
				}

				Instant currentModTime = Files.getLastModifiedTime( filePath ).toInstant();
				if ( currentModTime.isAfter( cachedTime ) ) {
					// File has been modified since caching - mark as stale
					staleFiles.add( fileUri );
					// Remove stale data from index (will be re-indexed later)
					removeFileByUri( fileUri );
				}
			} catch ( Exception e ) {
				// If we can't check, mark as stale to be safe
				if ( App.logger != null ) {
					App.logger.debug( "Could not validate cache freshness for {}: {}", fileUri, e.getMessage() );
				}
				staleFiles.add( fileUri );
				removeFileByUri( fileUri );
			}
		}
	}

	/**
	 * Remove all indexed data for a file using its URI string.
	 * Internal helper for cache validation.
	 */
	private void removeFileByUri( String fileUriStr ) {
		try {
			removeFile( URI.create( fileUriStr ) );
		} catch ( Exception e ) {
			// Ignore errors during cleanup
		}
	}

	/**
	 * Save the index to the cache file.
	 */
	public void saveCache() {
		if ( cacheFilePath == null ) {
			if ( App.logger != null ) {
				App.logger.warn( "Cache file path not initialized, cannot save project index cache" );
			}
			return;
		}

		try {
			// Ensure parent directory exists
			Files.createDirectories( cacheFilePath.getParent() );

			JsonObject cacheObject = new JsonObject();

			// Save file modification times
			JsonObject fileTimesObj = new JsonObject();
			for ( Map.Entry<String, Instant> entry : fileModifiedTimes.entrySet() ) {
				fileTimesObj.addProperty( entry.getKey(), entry.getValue().toString() );
			}
			cacheObject.add( "fileModifiedTimes", fileTimesObj );

			// Save classes
			JsonArray classesArray = new JsonArray();
			for ( IndexedClass indexedClass : classesByFQN.values() ) {
				classesArray.add( gson.toJsonTree( indexedClass ) );
			}
			cacheObject.add( "classes", classesArray );

			// Save methods
			JsonArray methodsArray = new JsonArray();
			for ( IndexedMethod indexedMethod : methodsByKey.values() ) {
				methodsArray.add( gson.toJsonTree( indexedMethod ) );
			}
			cacheObject.add( "methods", methodsArray );

			// Save properties
			JsonArray propertiesArray = new JsonArray();
			for ( IndexedProperty indexedProperty : propertiesByKey.values() ) {
				propertiesArray.add( gson.toJsonTree( indexedProperty ) );
			}
			cacheObject.add( "properties", propertiesArray );

			try ( FileWriter writer = new FileWriter( cacheFilePath.toFile() ) ) {
				gson.toJson( cacheObject, writer );
			}

			if ( App.logger != null ) {
				App.logger.debug( "Saved project index cache with {} classes, {} methods, {} properties",
				    classesByFQN.size(), methodsByKey.size(), propertiesByKey.size() );
			}

		} catch ( IOException e ) {
			if ( App.logger != null ) {
				App.logger.error( "Failed to save project index cache: " + e.getMessage(), e );
			}
		}
	}

	private static String sha256( String input ) {
		try {
			MessageDigest	digest		= MessageDigest.getInstance( "SHA-256" );
			byte[]			hashBytes	= digest.digest( input.getBytes() );
			StringBuilder	hexString	= new StringBuilder();
			for ( byte b : hashBytes ) {
				hexString.append( String.format( "%02x", b ) );
			}
			return hexString.toString();
		} catch ( NoSuchAlgorithmException e ) {
			throw new RuntimeException( e );
		}
	}
}
