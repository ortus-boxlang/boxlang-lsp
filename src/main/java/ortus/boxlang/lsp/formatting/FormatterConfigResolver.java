package ortus.boxlang.lsp.formatting;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class FormatterConfigResolver {

	private record CacheKey( Path documentPath, Path workspaceRoot ) {
	}

	private final Map<CacheKey, Optional<Path>> configPathCache = new ConcurrentHashMap<>();

	public Optional<Path> resolveConfigPath( Path documentPath, Path workspaceRoot ) {
		if ( documentPath == null || workspaceRoot == null ) {
			return Optional.empty();
		}

		Path		normalizedDocumentPath	= documentPath.toAbsolutePath().normalize();
		Path		normalizedWorkspaceRoot	= workspaceRoot.toAbsolutePath().normalize();
		CacheKey	cacheKey				= new CacheKey( normalizedDocumentPath, normalizedWorkspaceRoot );

		return configPathCache.computeIfAbsent( cacheKey, key -> findConfigPath( key.documentPath(), key.workspaceRoot() ) );
	}

	public void invalidate( Path changedConfigPath ) {
		if ( changedConfigPath == null ) {
			configPathCache.clear();
			return;
		}

		Path normalizedChangedPath = changedConfigPath.toAbsolutePath().normalize();
		configPathCache.entrySet().removeIf( entry -> entry.getValue().map( normalizedChangedPath::equals ).orElse( false ) );
	}

	private Optional<Path> findConfigPath( Path documentPath, Path workspaceRoot ) {
		Path	normalizedWorkspaceRoot	= workspaceRoot.toAbsolutePath().normalize();
		Path	current					= Files.isDirectory( documentPath ) ? documentPath.toAbsolutePath().normalize()
		    : documentPath.toAbsolutePath().normalize().getParent();

		if ( current == null || !current.startsWith( normalizedWorkspaceRoot ) ) {
			return Optional.empty();
		}

		while ( current != null && current.startsWith( normalizedWorkspaceRoot ) ) {
			Path bxformat = current.resolve( ".bxformat.json" );
			if ( Files.exists( bxformat ) ) {
				return Optional.of( bxformat );
			}

			Path cfformat = current.resolve( ".cfformat.json" );
			if ( Files.exists( cfformat ) ) {
				return Optional.of( cfformat );
			}

			if ( current.equals( normalizedWorkspaceRoot ) ) {
				break;
			}

			current = current.getParent();
		}

		return Optional.empty();
	}
}