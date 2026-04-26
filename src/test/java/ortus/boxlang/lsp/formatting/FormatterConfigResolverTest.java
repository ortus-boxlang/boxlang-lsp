package ortus.boxlang.lsp.formatting;

import static com.google.common.truth.Truth.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FormatterConfigResolverTest {

	@TempDir
	Path tempDir;

	@Test
	void resolvesNearestBxformatFileFromDocumentDirectory() throws IOException {
		Path	workspaceRoot	= Files.createDirectories( tempDir.resolve( "workspace" ) );
		Path	sourceDir		= Files.createDirectories( workspaceRoot.resolve( "src/nested" ) );
		Path	document		= Files.createFile( sourceDir.resolve( "Example.bx" ) );
		Path	bxformat		= Files.writeString( sourceDir.resolve( ".bxformat.json" ), "{}" );

		Path	resolved		= new FormatterConfigResolver().resolveConfigPath( document, workspaceRoot ).orElse( null );

		assertThat( resolved ).isEqualTo( bxformat );
	}

	@Test
	void resolvedConfigPathRemainsCachedUntilInvalidated() throws IOException {
		Path					workspaceRoot	= Files.createDirectories( tempDir.resolve( "workspace-cache" ) );
		Path					sourceDir		= Files.createDirectories( workspaceRoot.resolve( "src/nested" ) );
		Path					document		= Files.createFile( sourceDir.resolve( "Example.bx" ) );
		Path					bxformat		= Files.writeString( workspaceRoot.resolve( ".bxformat.json" ), "{}" );
		FormatterConfigResolver	resolver		= new FormatterConfigResolver();

		assertThat( resolver.resolveConfigPath( document, workspaceRoot ) ).hasValue( bxformat );

		Files.delete( bxformat );

		assertThat( resolver.resolveConfigPath( document, workspaceRoot ) ).hasValue( bxformat );

		resolver.invalidate( bxformat );

		assertThat( resolver.resolveConfigPath( document, workspaceRoot ) ).isEmpty();
	}
}