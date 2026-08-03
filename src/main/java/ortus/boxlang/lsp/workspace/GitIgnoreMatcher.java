package ortus.boxlang.lsp.workspace;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

import org.eclipse.jgit.ignore.IgnoreNode;

import ortus.boxlang.lsp.App;

/** Applies root and nested .gitignore rules to paths within a workspace. */
public class GitIgnoreMatcher {

	private record IgnoreRules( Path directory, IgnoreNode node ) {
	}

	private final Path				workspaceRoot;
	private final List<IgnoreRules>	rules;

	private GitIgnoreMatcher( Path workspaceRoot, List<IgnoreRules> rules ) {
		this.workspaceRoot	= workspaceRoot.toAbsolutePath().normalize();
		this.rules			= rules;
	}

	public static GitIgnoreMatcher create( Path workspaceRoot ) {
		Path				normalizedRoot	= workspaceRoot.toAbsolutePath().normalize();
		List<IgnoreRules>	rules			= new ArrayList<>();
		GitIgnoreMatcher	matcher			= new GitIgnoreMatcher( normalizedRoot, rules );
		try {
			Files.walkFileTree( normalizedRoot, new SimpleFileVisitor<>() {

				@Override
				public FileVisitResult preVisitDirectory( Path directory, BasicFileAttributes attributes ) {
					if ( !directory.equals( normalizedRoot ) && matcher.isIgnored( directory ) ) {
						return FileVisitResult.SKIP_SUBTREE;
					}
					Path gitIgnoreFile = directory.resolve( ".gitignore" );
					if ( Files.isRegularFile( gitIgnoreFile, LinkOption.NOFOLLOW_LINKS ) ) {
						addRules( gitIgnoreFile, rules );
						rules.sort( Comparator.comparingInt( rule -> rule.directory().getNameCount() ) );
					}
					return FileVisitResult.CONTINUE;
				}
			} );
		} catch ( IOException e ) {
			if ( App.logger != null ) {
				App.logger.warn( "Unable to discover .gitignore files under " + normalizedRoot, e );
			}
		}
		return matcher;
	}

	public void walk( Path start, Consumer<Path> consumer ) throws IOException {
		Path normalizedStart = start.toAbsolutePath().normalize();
		Files.walkFileTree( normalizedStart, new SimpleFileVisitor<>() {

			@Override
			public FileVisitResult preVisitDirectory( Path directory, BasicFileAttributes attributes ) {
				if ( !directory.equals( workspaceRoot ) && isIgnored( directory ) ) {
					return FileVisitResult.SKIP_SUBTREE;
				}
				consumer.accept( directory );
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile( Path file, BasicFileAttributes attributes ) {
				if ( !isIgnored( file ) ) {
					consumer.accept( file );
				}
				return FileVisitResult.CONTINUE;
			}
		} );
	}

	public boolean isIgnored( Path path ) {
		Path normalizedPath = path.toAbsolutePath().normalize();
		if ( !normalizedPath.startsWith( workspaceRoot ) || normalizedPath.equals( workspaceRoot ) ) {
			return false;
		}

		Path	relativePath	= workspaceRoot.relativize( normalizedPath );
		Path	current			= workspaceRoot;
		for ( int i = 0; i < relativePath.getNameCount() - 1; i++ ) {
			current = current.resolve( relativePath.getName( i ) );
			if ( Boolean.TRUE.equals( checkRules( current, true ) ) ) {
				return true;
			}
		}

		return Boolean.TRUE.equals( checkRules( normalizedPath, Files.isDirectory( normalizedPath, LinkOption.NOFOLLOW_LINKS ) ) );
	}

	private Boolean checkRules( Path path, boolean directory ) {
		Boolean ignored = null;
		for ( IgnoreRules ignoreRules : rules ) {
			if ( !path.startsWith( ignoreRules.directory() ) || path.equals( ignoreRules.directory() ) ) {
				continue;
			}
			String	relativePath	= ignoreRules.directory().relativize( path ).toString().replace( path.getFileSystem().getSeparator(), "/" );
			Boolean	result			= ignoreRules.node().checkIgnored( relativePath, directory );
			if ( result != null ) {
				ignored = result;
			}
		}
		return ignored;
	}

	private static void addRules( Path gitIgnoreFile, List<IgnoreRules> rules ) {
		IgnoreNode node = new IgnoreNode();
		try ( InputStream input = Files.newInputStream( gitIgnoreFile ) ) {
			node.parse( gitIgnoreFile.toString(), input );
			rules.add( new IgnoreRules( gitIgnoreFile.getParent().toAbsolutePath().normalize(), node ) );
		} catch ( IOException e ) {
			if ( App.logger != null ) {
				App.logger.warn( "Unable to read .gitignore file " + gitIgnoreFile, e );
			}
		}
	}
}
