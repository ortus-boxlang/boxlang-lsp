package ortus.boxlang.lsp.workspace;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import ortus.boxlang.lsp.workspace.index.IndexedClass;
import ortus.boxlang.lsp.workspace.index.ProjectIndex;

/**
 * Shared resolver for class/interface references used by diagnostics and navigation.
 */
public class ClassReferenceResolver {

	private static final List<String>		CLASS_EXTENSIONS	= List.of( ".bx", ".cfc", ".bxs", ".cfm", ".bxm" );

	private final ProjectContextProvider	provider;

	public ClassReferenceResolver( ProjectContextProvider provider ) {
		this.provider = provider;
	}

	public Optional<IndexedClass> resolveClass( String classReference, URI contextDocUri ) {
		String cleanedReference = cleanClassReference( classReference );
		if ( cleanedReference == null ) {
			return Optional.empty();
		}

		ProjectIndex			index	= provider.getIndex();

		// 1) Existing index/classpath resolution.
		Optional<IndexedClass>	found	= index.findClassWithContext( cleanedReference, contextDocUri );
		if ( found.isPresent() ) {
			return found;
		}

		String simpleName = toSimpleClassName( cleanedReference );
		if ( !simpleName.equalsIgnoreCase( cleanedReference ) ) {
			found = index.findClassWithContext( simpleName, contextDocUri );
			if ( found.isPresent() ) {
				return found;
			}
		}

		// 2) Mapping-aware resolution and lazy indexing.
		Optional<Path> mappedClassPath = resolveMappedClassPath( cleanedReference );
		if ( mappedClassPath.isEmpty() ) {
			return Optional.empty();
		}

		Path	mappedPath	= mappedClassPath.get().toAbsolutePath().normalize();
		URI		mappedUri	= mappedPath.toUri();
		if ( index.needsReindexing( mappedUri ) ) {
			index.indexFile( mappedUri );
		}

		// Resolve indexed class by file URI first.
		Optional<IndexedClass> byFile = index.getAllClasses().stream()
		    .filter( c -> c.fileUri() != null && c.fileUri().equals( mappedUri.toString() ) )
		    .findFirst();
		if ( byFile.isPresent() ) {
			return byFile;
		}

		// Fall back to name-based lookups after indexing.
		found = index.findClassWithContext( simpleName, contextDocUri );
		if ( found.isPresent() ) {
			return found;
		}

		return index.findClassWithContext( cleanedReference, contextDocUri );
	}

	public List<Location> resolveLocation( String classReference, URI contextDocUri ) {
		String					cleanedReference	= cleanClassReference( classReference );
		Optional<IndexedClass>	resolved			= resolveClass( cleanedReference, contextDocUri );
		if ( resolved.isPresent() ) {
			IndexedClass indexedClass = resolved.get();
			if ( indexedClass.fileUri() != null ) {
				Location location = new Location();
				location.setUri( indexedClass.fileUri() );
				location.setRange( indexedClass.location() != null ? indexedClass.location() : fileStartRange() );
				return List.of( location );
			}
		}

		// Fallback: if mapped file exists but indexing failed, still allow navigation to file start.
		Optional<Path> mappedPath = resolveMappedClassPath( classReference );
		if ( mappedPath.isPresent() ) {
			Location location = new Location();
			location.setUri( mappedPath.get().toAbsolutePath().normalize().toUri().toString() );
			location.setRange( fileStartRange() );
			return List.of( location );
		}

		// Joint client/server fallback: return a path-like URI based on the class reference.
		// Mapping-aware clients can expand this with their local app context mappings.
		if ( looksLikeMappedReference( cleanedReference ) ) {
			Location location = new Location();
			location.setUri( toUnresolvedReferencePathUri( cleanedReference ) );
			location.setRange( fileStartRange() );
			return List.of( location );
		}

		return new ArrayList<>();
	}

	private Range fileStartRange() {
		return new Range( new Position( 0, 0 ), new Position( 0, 0 ) );
	}

	public Optional<Path> resolveMappedClassPath( String classReference ) {
		String cleanedReference = cleanClassReference( classReference );
		if ( cleanedReference == null ) {
			return Optional.empty();
		}

		int firstDot = cleanedReference.indexOf( '.' );
		if ( firstDot <= 0 || firstDot == cleanedReference.length() - 1 ) {
			return Optional.empty();
		}

		String				alias		= canonicalizeAlias( cleanedReference.substring( 0, firstDot ) );
		Map<String, Path>	mappings	= provider.getMappings();
		Path				mappingRoot	= mappings.get( alias );
		if ( mappingRoot == null ) {
			return Optional.empty();
		}

		String remainder = cleanedReference.substring( firstDot + 1 ).replace( '.', '/' );
		for ( String extension : CLASS_EXTENSIONS ) {
			Path candidate = mappingRoot.resolve( remainder + extension ).toAbsolutePath().normalize();
			if ( Files.isRegularFile( candidate ) ) {
				return Optional.of( candidate );
			}
		}
		return Optional.empty();
	}

	private String cleanClassReference( String classReference ) {
		if ( classReference == null ) {
			return null;
		}
		String cleaned = classReference.trim();
		if ( cleaned.isEmpty() ) {
			return null;
		}
		cleaned = cleaned.replace( "\"", "" ).replace( "'", "" ).trim();
		return cleaned.isEmpty() ? null : cleaned;
	}

	private String toSimpleClassName( String classReference ) {
		int lastDot = classReference.lastIndexOf( '.' );
		return lastDot >= 0 && lastDot < classReference.length() - 1
		    ? classReference.substring( lastDot + 1 )
		    : classReference;
	}

	private String canonicalizeAlias( String rawAlias ) {
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

	private boolean looksLikeMappedReference( String classReference ) {
		if ( classReference == null || classReference.isBlank() || !classReference.contains( "." ) ) {
			return false;
		}
		String lower = classReference.toLowerCase( Locale.ROOT );
		return !lower.startsWith( "java." );
	}

	private String toUnresolvedReferencePathUri( String classReference ) {
		String normalizedPath = "/" + classReference.replace( '.', '/' );
		return Path.of( normalizedPath ).toUri().toString();
	}
}
