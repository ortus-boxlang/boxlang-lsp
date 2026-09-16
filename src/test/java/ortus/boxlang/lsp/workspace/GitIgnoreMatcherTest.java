package ortus.boxlang.lsp.workspace;

import static com.google.common.truth.Truth.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitIgnoreMatcherTest {

	@TempDir
	Path workspaceRoot;

	@Test
	void respectsRootDirectoryAndNegationRules() throws Exception {
		Files.writeString( workspaceRoot.resolve( ".gitignore" ), "generated/\n*.tmp.bx\n!keep.tmp.bx\n" );
		Files.createDirectories( workspaceRoot.resolve( "generated" ) );

		GitIgnoreMatcher matcher = GitIgnoreMatcher.create( workspaceRoot );

		assertThat( matcher.isIgnored( workspaceRoot.resolve( "generated/Hidden.bx" ) ) ).isTrue();
		assertThat( matcher.isIgnored( workspaceRoot.resolve( "discard.tmp.bx" ) ) ).isTrue();
		assertThat( matcher.isIgnored( workspaceRoot.resolve( "keep.tmp.bx" ) ) ).isFalse();
		assertThat( matcher.isIgnored( workspaceRoot.resolve( "Visible.bx" ) ) ).isFalse();
	}

	@Test
	void nestedGitignoreOverridesParentRules() throws Exception {
		Files.writeString( workspaceRoot.resolve( ".gitignore" ), "*.generated.bx\n" );
		Path nested = Files.createDirectories( workspaceRoot.resolve( "nested" ) );
		Files.writeString( nested.resolve( ".gitignore" ), "!Included.generated.bx\n" );

		GitIgnoreMatcher matcher = GitIgnoreMatcher.create( workspaceRoot );

		assertThat( matcher.isIgnored( nested.resolve( "Included.generated.bx" ) ) ).isFalse();
		assertThat( matcher.isIgnored( nested.resolve( "Ignored.generated.bx" ) ) ).isTrue();
		assertThat( matcher.isIgnored( workspaceRoot.resolve( "Ignored.generated.bx" ) ) ).isTrue();
	}

	@Test
	void walkPrunesIgnoredDirectories() throws Exception {
		Files.writeString( workspaceRoot.resolve( ".gitignore" ), "generated/\n" );
		Path generated = Files.createDirectories( workspaceRoot.resolve( "generated/nested" ) );
		Files.writeString( generated.resolve( "Ignored.bx" ), "class {}" );
		Path visible = workspaceRoot.resolve( "Visible.bx" );
		Files.writeString( visible, "class {}" );

		GitIgnoreMatcher	matcher	= GitIgnoreMatcher.create( workspaceRoot );
		List<Path>			visited	= new ArrayList<>();
		matcher.walk( workspaceRoot, visited::add );

		assertThat( visited ).contains( visible );
		assertThat( visited ).doesNotContain( workspaceRoot.resolve( "generated" ) );
		assertThat( visited ).doesNotContain( generated.resolve( "Ignored.bx" ) );
	}
}
