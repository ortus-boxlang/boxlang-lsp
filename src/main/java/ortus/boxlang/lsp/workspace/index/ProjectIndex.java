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
import ortus.boxlang.lsp.workspace.MappingConfig;
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
	private MappingConfig								mappingConfig;

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
		initialize( workspaceRoot, null );
	}

	/**
	 * Initialize the project index with a workspace root and a MappingConfig.
	 *
	 * @param workspaceRoot The root path of the workspace
	 * @param mappingConfig Optional mapping configuration for virtual FQN computation
	 */
	public void initialize( Path workspaceRoot, MappingConfig mappingConfig ) {
		this.workspaceRoot	= workspaceRoot;
		this.mappingConfig	= mappingConfig;
		this.cacheFilePath	= getDefaultCacheFilePath( workspaceRoot );
		loadCache();

		// Index external directories declared in the MappingConfig so that
		// virtual FQNs for those files are available immediately.
		if ( mappingConfig != null ) {
			indexExternalDirectories( mappingConfig, workspaceRoot );
		}
	}

	/**
	 * Reinitialize the index with updated configuration without loading from the
	 * disk cache. Clears all in-memory data, then stores the new config. The
	 * caller is responsible for re-walking and re-indexing all relevant files.
	 *
	 * @param workspaceRoot The root path of the workspace
	 * @param mappingConfig Updated mapping configuration
	 */
	public void reinitialize( Path workspaceRoot, MappingConfig mappingConfig ) {
		this.workspaceRoot	= workspaceRoot;
		this.mappingConfig	= mappingConfig;
		this.cacheFilePath	= getDefaultCacheFilePath( workspaceRoot );
		clear();
	}

	/**
	 * Index all external directories declared in the given MappingConfig.
	 * This is the same logic executed during {@link #initialize(Path, MappingConfig)}
	 * and is exposed publicly so callers can trigger selective re-indexing after a
	 * configuration change.
	 *
	 * @param config Mapping configuration whose external dirs should be indexed
	 */
	public void indexExternalDirs( MappingConfig config ) {
		if ( config != null && workspaceRoot != null ) {
			indexExternalDirectories( config, workspaceRoot );
		}
	}

	/**
	 * Walks every external directory declared in the MappingConfig and indexes
	 * each BoxLang file found. Directories that already sit inside the workspace
	 * root are skipped to prevent double-indexing.
	 */
	private void indexExternalDirectories( MappingConfig config, Path workspaceRoot ) {
		Path				normalizedRoot	= workspaceRoot.toAbsolutePath().normalize();

		// Collect all directories to walk: mapped real paths + classPaths + modulesDirectory
		java.util.Set<Path>	dirs			= new java.util.LinkedHashSet<>();
		config.getMappings().values().forEach( dirs::add );
		dirs.addAll( config.getClassPaths() );
		dirs.addAll( config.getModulesDirectory() );

		for ( Path dir : dirs ) {
			Path normalizedDir = dir.toAbsolutePath().normalize();
			// Skip dirs that are already under the workspace root (they'll be indexed separately)
			if ( normalizedDir.startsWith( normalizedRoot ) ) {
				continue;
			}
			if ( !Files.isDirectory( normalizedDir ) ) {
				continue;
			}
			try ( java.util.stream.Stream<Path> stream = Files.walk( normalizedDir ) ) {
				stream.filter( p -> {
					try {
						return Files.isRegularFile( p ) &&
						    ortus.boxlang.lsp.LSPTools.canWalkFile( p );
					} catch ( Exception e ) {
						return false;
					}
				} ).forEach( p -> indexFile( p.toUri() ) );
			} catch ( IOException e ) {
				if ( App.logger != null ) {
					App.logger.warn( "Failed to index external directory: " + normalizedDir, e );
				}
			}
		}

		// Also walk directories that ARE inside the workspace root
		// (they need indexing too, just skip de-dup concern — indexFile is idempotent via removeFile)
		for ( Path dir : dirs ) {
			Path normalizedDir = dir.toAbsolutePath().normalize();
			if ( !normalizedDir.startsWith( normalizedRoot ) ) {
				continue; // already handled above
			}
			if ( !Files.isDirectory( normalizedDir ) ) {
				continue;
			}
			try ( java.util.stream.Stream<Path> stream = Files.walk( normalizedDir ) ) {
				stream.filter( p -> {
					try {
						return Files.isRegularFile( p ) &&
						    ortus.boxlang.lsp.LSPTools.canWalkFile( p );
					} catch ( Exception e ) {
						return false;
					}
				} ).forEach( p -> indexFile( p.toUri() ) );
			} catch ( IOException e ) {
				if ( App.logger != null ) {
					App.logger.warn( "Failed to index directory: " + normalizedDir, e );
				}
			}
		}
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
			ProjectIndexVisitor visitor = new ProjectIndexVisitor( fileUri, workspaceRoot, mappingConfig );
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
		Optional<IndexedClass> found = Optional.ofNullable( classesByFQN.get( fqn ) );
		if ( found.isPresent() ) {
			return found;
		}
		// Final fallback: bxmodule.* prefix resolution
		return findClassByBxModuleFqn( fqn );
	}

	/**
	 * Find a class by name with support for relative path resolution.
	 * Tries multiple strategies in order:
	 * 1. Simple name lookup (e.g., "User")
	 * 2. Full FQN lookup (e.g., "models.User")
	 * 3. Relative path resolution (if contextFileUri provided and className contains ".")
	 *
	 * @param className      The class name to find (simple name, FQN, or relative path)
	 * @param contextFileUri The URI of the file making the reference (for relative path resolution), or null
	 *
	 * @return Optional containing the class if found
	 */
	public Optional<IndexedClass> findClassWithContext( String className, URI contextFileUri ) {
		if ( className == null || className.isEmpty() ) {
			return Optional.empty();
		}

		// Try simple name first
		Optional<IndexedClass> result = findClassByName( className );
		if ( result.isPresent() ) {
			return result;
		}

		// Try by FQN
		result = findClassByFQN( className );
		if ( result.isPresent() ) {
			return result;
		}

		// If still not found and className contains a dot (potential relative path),
		// try resolving relative to the context file's package
		if ( className.contains( "." ) && contextFileUri != null && workspaceRoot != null ) {
			String currentPackage = getPackageFromURI( contextFileUri );
			if ( currentPackage != null && !currentPackage.isEmpty() ) {
				String qualifiedName = currentPackage + "." + className;
				result = findClassByFQN( qualifiedName );
				if ( result.isPresent() ) {
					return result;
				}
			}
		}

		// Filesystem fallback: resolve dot-path as a file path
		if ( className.contains( "." ) && workspaceRoot != null ) {
			result = findClassByFileSystemPath( className, contextFileUri );
			if ( result.isPresent() ) {
				return result;
			}
		}

		// Final fallback: bxmodule.* prefix resolution
		return findClassByBxModuleFqn( className );
	}

	/**
	 * Get the package (directory path) of a file relative to workspace root.
	 * For example, if file is at "subpackage/BaseType.bx", returns "subpackage".
	 * Returns null if package cannot be determined.
	 *
	 * @param fileUri The file URI
	 *
	 * @return The package path (dot-separated), or null
	 */
	private String getPackageFromURI( URI fileUri ) {
		if ( fileUri == null || workspaceRoot == null ) {
			return null;
		}

		try {
			Path	filePath		= Paths.get( fileUri );
			Path	relativePath	= workspaceRoot.relativize( filePath );
			String	pathStr			= relativePath.toString();

			// Get the directory part (without filename)
			int		lastSlash		= Math.max( pathStr.lastIndexOf( '/' ), pathStr.lastIndexOf( '\\' ) );
			if ( lastSlash < 0 ) {
				// File is in root directory
				return null;
			}

			String packagePath = pathStr.substring( 0, lastSlash );

			// Convert path separators to dots
			return packagePath.replace( '/', '.' ).replace( '\\', '.' );
		} catch ( Exception e ) {
			// If we can't determine package, return null
			return null;
		}
	}

	/**
	 * Resolve a dot-path class name by locating the corresponding file directly
	 * on disk relative to the file making the reference and indexing it on-demand
	 * if it is not already in the index.
	 *
	 * <p>
	 * For example, from {@code subpackage/BaseType.bx} the reference
	 * {@code subsubpackage.EvenBaserType} is resolved to
	 * {@code subpackage/subsubpackage/EvenBaserType.bx}.
	 *
	 * @param className      the dot-path class name to resolve
	 * @param contextFileUri the file making the reference, or {@code null}
	 *
	 * @return the matching {@link IndexedClass}, or empty if not found
	 */
	private Optional<IndexedClass> findClassByFileSystemPath( String className, URI contextFileUri ) {
		if ( className == null || className.isEmpty() || contextFileUri == null ) {
			return Optional.empty();
		}

		try {
			// Convert dots to path separators
			String	subPath	= className.replace( '.', java.io.File.separatorChar );
			Path	contextParent	= Paths.get( contextFileUri ).getParent();

			if ( contextParent == null ) {
				return Optional.empty();
			}

			for ( String ext : ortus.boxlang.lsp.LSPTools.BOXLANG_EXTENSIONS ) {
				Path	candidate	= contextParent.resolve( subPath + ext ).normalize();
				if ( !Files.isRegularFile( candidate ) ) {
					continue;
				}

				URI candidateUri = candidate.toUri();

				// Index on-demand if not already in the index
				if ( !classesByFileUri.containsKey( candidateUri.toString() ) ) {
					indexFile( candidateUri );
				}

				List<IndexedClass> classes = classesByFileUri.get( candidateUri.toString() );
				if ( classes != null && !classes.isEmpty() ) {
					return Optional.of( classes.get( 0 ) );
				}
			}
		} catch ( Exception e ) {
			App.logger.warn( "Filesystem class resolution failed for '" + className + "' from " + contextFileUri, e );
		}

		return Optional.empty();
	}

	/**
	 * Resolve a {@code bxmodule.}-prefixed FQN by locating the corresponding
	 * file on disk inside the configured {@code modulesDirectory} paths and
	 * indexing it on-demand if it is not already in the index.
	 *
	 * <p>
	 * The expected format is {@code bxmodule.<moduleName>.<subpath>}, e.g.
	 * {@code bxmodule.myModule.models.User}. The prefix matching is
	 * case-insensitive; the module name and subpath are matched as-given.
	 *
	 * @param fqn the fully qualified name to resolve
	 *
	 * @return the matching {@link IndexedClass}, or empty if not found
	 */
	public Optional<IndexedClass> findClassByBxModuleFqn( String fqn ) {
		if ( fqn == null || !fqn.toLowerCase().startsWith( "bxmodule." ) ) {
			return Optional.empty();
		}
		if ( mappingConfig == null ) {
			return Optional.empty();
		}

		// Strip the "bxmodule." prefix (9 chars, case-insensitive handled above)
		String	rest		= fqn.substring( "bxmodule.".length() );
		int		dotIndex	= rest.indexOf( '.' );
		if ( dotIndex < 0 ) {
			// No subpath — just a module name with no class segment
			return Optional.empty();
		}

		String	moduleName	= rest.substring( 0, dotIndex );
		// Convert remaining dots to path separators to build the file subpath
		String	subPath		= rest.substring( dotIndex + 1 ).replace( '.', java.io.File.separatorChar );

		for ( Path moduleDir : mappingConfig.getModulesDirectory() ) {
			Path moduleRoot = moduleDir.toAbsolutePath().normalize();
			if ( !Files.isDirectory( moduleRoot ) ) {
				continue;
			}

			// Try each valid BoxLang extension
			for ( String ext : ortus.boxlang.lsp.LSPTools.BOXLANG_EXTENSIONS ) {
				Path candidate = moduleRoot.resolve( moduleName ).resolve( subPath + ext ).normalize();
				if ( !Files.isRegularFile( candidate ) ) {
					continue;
				}

				URI candidateUri = candidate.toUri();

				// Index on-demand if not already in the index
				if ( !classesByFileUri.containsKey( candidateUri.toString() ) ) {
					indexFile( candidateUri );
				}

				List<IndexedClass> classes = classesByFileUri.get( candidateUri.toString() );
				if ( classes != null && !classes.isEmpty() ) {
					return Optional.of( classes.get( 0 ) );
				}
			}
		}

		return Optional.empty();
	}

	/**
	 * Find all classes that extend a given class.
	 * Tries both FQN and simple name lookup since extends may store
	 * simple names from annotations.
	 *
	 * @param className The parent class name (can be FQN or simple name)
	 *
	 * @return List of classes extending the specified class
	 */
	public List<IndexedClass> findClassesExtending( String className ) {
		// Try FQN first
		List<String> descendants = inheritanceGraph.getDescendants( className );

		// If no results, try extracting simple name and searching
		if ( descendants.isEmpty() && className.contains( "." ) ) {
			String simpleName = className.substring( className.lastIndexOf( "." ) + 1 );
			descendants = inheritanceGraph.getDescendants( simpleName );
		}

		// Also try with just the simple name if we were given an FQN
		if ( descendants.isEmpty() ) {
			String simpleName = className.contains( "." )
			    ? className.substring( className.lastIndexOf( "." ) + 1 )
			    : className;
			descendants = inheritanceGraph.getDescendants( simpleName );
		}

		return descendants.stream()
		    .map( classesByFQN::get )
		    .filter( c -> c != null )
		    .collect( Collectors.toList() );
	}

	/**
	 * Find all classes that implement a given interface.
	 * Tries both FQN and simple name lookup since implementations may store
	 * simple names from annotations.
	 *
	 * @param interfaceName The interface name (can be FQN or simple name)
	 *
	 * @return List of classes implementing the specified interface
	 */
	public List<IndexedClass> findClassesImplementing( String interfaceName ) {
		// Try FQN first
		List<String> implementors = inheritanceGraph.getImplementors( interfaceName );

		// If no results, try extracting simple name and searching
		if ( implementors.isEmpty() && interfaceName.contains( "." ) ) {
			String simpleName = interfaceName.substring( interfaceName.lastIndexOf( "." ) + 1 );
			implementors = inheritanceGraph.getImplementors( simpleName );
		}

		// Also try with just the simple name if we were given an FQN
		if ( implementors.isEmpty() ) {
			// Extract simple name if it's a FQN
			String simpleName = interfaceName.contains( "." )
			    ? interfaceName.substring( interfaceName.lastIndexOf( "." ) + 1 )
			    : interfaceName;
			implementors = inheritanceGraph.getImplementors( simpleName );
		}

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

		String			lowerQuery	= query.toLowerCase();
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
		String				lowerMethodName	= methodName.toLowerCase();
		List<IndexedMethod>	overrides		= new ArrayList<>();

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
				    URI	uri			= URI.create( fileUri );
				    String filePath	= Paths.get( uri ).toString().replace( '\\', '/' );
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

			JsonObject	cacheObject		= new JsonObject();

			// Save file modification times
			JsonObject	fileTimesObj	= new JsonObject();
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

	/**
	 * Get the workspace root path.
	 *
	 * @return The workspace root path, or null if not initialized
	 */
	public Path getWorkspaceRoot() {
		return workspaceRoot;
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
