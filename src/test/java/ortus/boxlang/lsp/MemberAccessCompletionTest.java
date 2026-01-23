package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertThat;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ortus.boxlang.lsp.workspace.ProjectContextProvider;
import ortus.boxlang.lsp.workspace.index.ProjectIndex;

public class MemberAccessCompletionTest extends BaseTest {

	private static Path				projectRoot;
	private static Path				testDir;
	private ProjectContextProvider	pcp;

	@BeforeAll
	public static void setUpClass() {
		projectRoot	= Paths.get( System.getProperty( "user.dir" ) );
		testDir		= projectRoot.resolve( "src/test/resources/files/memberAccessTest" );
	}

	@BeforeEach
	public void setUp() throws Exception {
		pcp = ProjectContextProvider.getInstance();

		// Initialize index with test files
		ProjectIndex index = pcp.getIndex();
		index.clear();
		index.initialize( testDir );

		// Index all test files
		for ( File file : testDir.toFile().listFiles() ) {
			if ( file.getName().endsWith( ".bx" ) ) {
				index.indexFile( file.toURI() );
				pcp.trackDocumentOpen( file.toURI(), Files.readString( file.toPath() ) );
			}
		}
	}

	@Test
	void testCompletionsAfterNewExpression() {
		// In TestConsumer.bx, line 3 has "user." after "var user = new User(...)"
		Path					testFile	= testDir.resolve( "TestConsumer.bx" );
		File					f			= testFile.toFile();

		CompletionParams		params		= new CompletionParams();
		TextDocumentIdentifier	td			= new TextDocumentIdentifier( testFile.toUri().toString() );
		params.setTextDocument( td );
		params.setPosition( new Position( 3, 7 ) );	// After "user." on line 4 (0-indexed = line 3)

		List<CompletionItem> items = pcp.getAvailableCompletions( f.toURI(), params );

		// Should include User's methods
		Optional<CompletionItem> getDisplayName = items.stream()
		    .filter( item -> item.getLabel().equals( "getDisplayName" ) )
		    .findFirst();

		assertThat( getDisplayName.isPresent() ).isTrue();
		assertThat( getDisplayName.get().getKind() ).isEqualTo( CompletionItemKind.Method );
	}

	@Test
	void testCompletionsShowInheritedMembers() {
		Path					testFile	= testDir.resolve( "TestConsumer.bx" );
		File					f			= testFile.toFile();

		CompletionParams		params		= new CompletionParams();
		TextDocumentIdentifier	td			= new TextDocumentIdentifier( testFile.toUri().toString() );
		params.setTextDocument( td );
		params.setPosition( new Position( 3, 7 ) );	// After "user." on line 4 (0-indexed)

		List<CompletionItem> items = pcp.getAvailableCompletions( f.toURI(), params );

		// Should include inherited method from BaseEntity
		Optional<CompletionItem> inherited = items.stream()
		    .filter( item -> item.getLabel().equals( "getCreatedAt" ) )
		    .findFirst();

		assertThat( inherited.isPresent() ).isTrue();
		assertThat( inherited.get().getLabelDetails() ).isNotNull();
		assertThat( inherited.get().getLabelDetails().getDescription() ).contains( "BaseEntity" );
	}

	@Test
	void testCompletionsIncludeProperties() {
		Path					testFile	= testDir.resolve( "TestConsumer.bx" );
		File					f			= testFile.toFile();

		CompletionParams		params		= new CompletionParams();
		TextDocumentIdentifier	td			= new TextDocumentIdentifier( testFile.toUri().toString() );
		params.setTextDocument( td );
		params.setPosition( new Position( 3, 7 ) );	// After "user." on line 4 (0-indexed)

		List<CompletionItem> items = pcp.getAvailableCompletions( f.toURI(), params );

		// Should include properties
		Optional<CompletionItem> username = items.stream()
		    .filter( item -> item.getLabel().equals( "username" ) && item.getKind() == CompletionItemKind.Property )
		    .findFirst();

		assertThat( username.isPresent() ).isTrue();
	}

	@Test
	void testCompletionsIncludeGetterSetter() {
		Path					testFile	= testDir.resolve( "TestConsumer.bx" );
		File					f			= testFile.toFile();

		CompletionParams		params		= new CompletionParams();
		TextDocumentIdentifier	td			= new TextDocumentIdentifier( testFile.toUri().toString() );
		params.setTextDocument( td );
		params.setPosition( new Position( 3, 7 ) );	// After "user." on line 4 (0-indexed)

		List<CompletionItem> items = pcp.getAvailableCompletions( f.toURI(), params );

		// Should include getter for username property
		Optional<CompletionItem> getUsername = items.stream()
		    .filter( item -> item.getLabel().equals( "getUsername" ) )
		    .findFirst();

		// Should include setter for username property
		Optional<CompletionItem> setUsername = items.stream()
		    .filter( item -> item.getLabel().equals( "setUsername" ) )
		    .findFirst();

		assertThat( getUsername.isPresent() ).isTrue();
		assertThat( setUsername.isPresent() ).isTrue();
	}

	@Test
	void testCompletionsForThis() {
		// Create a test file with "this."
		Path		testFile			= testDir.resolve( "User.bx" );
		File		f					= testFile.toFile();

		String		fileContent			= null;
		Position	completionPosition	= null;

		try {
			fileContent = Files.readString( testFile );
			// Add a test line: "this." inside the getDisplayName method
			String[] lines = fileContent.split( "\n" );
			// Find the line with "return variables.username"
			for ( int i = 0; i < lines.length; i++ ) {
				if ( lines[ i ].contains( "return variables.username" ) ) {
					completionPosition = new Position( i, 7 );	// After "this."
					// Temporarily modify the file content in memory for completion context
					lines[ i ] = "\t\tthis.";
					fileContent = String.join( "\n", lines );
					break;
				}
			}

			if ( completionPosition == null ) {
				// Fallback if we couldn't find the line
				completionPosition = new Position( 15, 7 );
			}

			// Track the modified content
			pcp.trackDocumentOpen( f.toURI(), fileContent );

			CompletionParams		params	= new CompletionParams();
			TextDocumentIdentifier	td		= new TextDocumentIdentifier( testFile.toUri().toString() );
			params.setTextDocument( td );
			params.setPosition( completionPosition );

			List<CompletionItem> items = pcp.getAvailableCompletions( f.toURI(), params );

			// Should include User's own methods
			Optional<CompletionItem> getDisplayName = items.stream()
			    .filter( item -> item.getLabel().equals( "getDisplayName" ) )
			    .findFirst();

			assertThat( getDisplayName.isPresent() ).isTrue();

		} catch ( Exception e ) {
			throw new RuntimeException( e );
		}
	}
}
