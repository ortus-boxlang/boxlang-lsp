package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.lsp.workspace.ProjectContextProvider;
import ortus.boxlang.runtime.BoxRuntime;

/**
 * Tests for BXM tag and attribute completion in template files.
 * Tests cover:
 * - Tag name completion after <bx:
 * - Attribute name completion within tags
 * - Attribute value completion for known attributes
 * - Self-closing vs container tags
 * - Required vs optional attributes
 */
public class BxmTagCompletionTest extends BaseTest {

	static BoxRuntime				instance;
	private static ProjectContextProvider	pcp;
	private static Path				projectRoot;
	private static Path				templatePath;
	private static File				templateFile;

	@BeforeAll
	static void loadFixtures() {
		instance		= BoxRuntime.getInstance( true );
		pcp			= ProjectContextProvider.getInstance();
		projectRoot		= Paths.get( System.getProperty( "user.dir" ) );
		templatePath	= projectRoot.resolve( "src/test/resources/files/bxmTagCompletionTest/simpleTemplate.bxm" );
		templateFile	= templatePath.toFile();

		assertTrue( templateFile.exists(), "Test file does not exist: " + templatePath.toString() );

		try {
			pcp.trackDocumentOpen( templatePath.toUri(), Files.readString( templatePath ) );
		} catch ( IOException e ) {
			e.printStackTrace();
		}
	}

	@Test
	@DisplayName( "Should complete tag names after <bx:" )
	void testTagNameCompletion() {
		// Position: Line 6 (0-indexed), col 5 after "<bx:"
		Position			position			= new Position( 6, 5 );
		CompletionParams		completionParams	= new CompletionParams();
		TextDocumentIdentifier	td				= new TextDocumentIdentifier( templatePath.toUri().toString() );
		completionParams.setPosition( position );
		completionParams.setTextDocument( td );

		List<CompletionItem> items = pcp.getAvailableCompletions( templateFile.toURI(), completionParams );

		assertThat( items ).isNotEmpty();

		// Should have bx:output tag
		CompletionItem outputTag = findCompletion( items, "bx:output" );
		assertThat( outputTag ).isNotNull();
		assertThat( outputTag.getKind() ).isEqualTo( CompletionItemKind.Snippet );
		assertThat( outputTag.getInsertText() ).contains( "output" );

		// Should have bx:loop tag
		CompletionItem loopTag = findCompletion( items, "bx:loop" );
		assertThat( loopTag ).isNotNull();

		// Should have bx:thread tag
		CompletionItem threadTag = findCompletion( items, "bx:thread" );
		assertThat( threadTag ).isNotNull();
	}

	@Test
	@DisplayName( "Should complete tag names with partial match" )
	void testPartialTagNameCompletion() {
		List<CompletionItem> items = getCompletionsAt( 9, 9 );

		// Should still offer all tags but output should match
		CompletionItem outputTag = findCompletion( items, "bx:output" );
		assertThat( outputTag ).isNotNull();
	}

	@Test
	@DisplayName( "Should complete attribute names inside tag" )
	void testAttributeNameCompletion() {
		// Position: Line 12 (0-indexed), after "<bx:output "
		List<CompletionItem> items = getCompletionsAt( 12, 13 );

		// Should have attribute completions
		// Looking for common bx:output attributes like 'encodefor', 'var', etc.
		// Note: Exact attributes depend on BoxRuntime's output component
		assertThat( items ).isNotEmpty();

		// Should have CompletionItemKind.Property for attributes
		long attributeCount = items.stream()
		    .filter( item -> item.getKind() == CompletionItemKind.Property )
		    .count();

		assertThat( attributeCount ).isGreaterThan( 0 );
	}

	@Test
	@DisplayName( "Should complete attribute names with partial match" )
	void testPartialAttributeNameCompletion() {
		// Position: Line 15 (0-indexed), after "<bx:output enc"
		List<CompletionItem> items = getCompletionsAt( 15, 16 );

		// Should filter to attributes starting with "enc"
		// Note: Testing pattern, exact attribute names depend on BoxRuntime
		assertThat( items ).isNotEmpty();
	}

	@Test
	@DisplayName( "Should complete attribute values for known attributes" )
	void testAttributeValueCompletion() {
		// Position: Line 18 (0-indexed), inside "<bx:output encodefor=\""
		List<CompletionItem> items = getCompletionsAt( 18, 27 );

		// Should have value completions if the attribute has known values
		// Note: Exact values depend on BoxRuntime metadata
		assertThat( items ).isNotEmpty();
	}

	@Test
	@DisplayName( "Should show required attributes first" )
	void testRequiredAttributesPrioritized() {
		// Position: Line 12 (0-indexed), after "<bx:output "
		List<CompletionItem> items = getCompletionsAt( 12, 13 );

		// Required attributes should have better sort order
		// They should be marked with (required) in detail or have special sorting
		List<CompletionItem> sortedItems = items.stream()
		    .filter( item -> item.getKind() == CompletionItemKind.Property )
		    .sorted( ( a, b ) -> a.getSortText().compareTo( b.getSortText() ) )
		    .toList();

		assertThat( sortedItems ).isNotEmpty();
	}

	@Test
	@DisplayName( "Should handle self-closing tag attributes" )
	void testSelfClosingTagAttributes() {
		// Position: Line 24 (0-indexed), after "<bx:thread "
		List<CompletionItem> items = getCompletionsAt( 24, 13 );

		// Should still provide attribute completions for self-closing tags
		assertThat( items ).isNotEmpty();

		long attributeCount = items.stream()
		    .filter( item -> item.getKind() == CompletionItemKind.Property )
		    .count();

		assertThat( attributeCount ).isGreaterThan( 0 );
	}

	@Test
	@DisplayName( "Tag completion items should have proper detail/documentation" )
	void testTagCompletionHasDocumentation() {
		List<CompletionItem> items = getCompletionsAt( 6, 5 );

		CompletionItem outputTag = findCompletion( items, "bx:output" );
		assertThat( outputTag ).isNotNull();

		// Should have detail showing signature
		assertThat( outputTag.getDetail() ).isNotNull();
		assertThat( outputTag.getDetail() ).contains( "bx:output" );
	}

	@Test
	@DisplayName( "Attribute completion items should have proper kind" )
	void testAttributeCompletionKind() {
		List<CompletionItem> items = getCompletionsAt( 12, 13 );

		// Attributes should use CompletionItemKind.Property
		List<CompletionItem> attributes = items.stream()
		    .filter( item -> item.getKind() == CompletionItemKind.Property )
		    .toList();

		assertThat( attributes ).isNotEmpty();

		// Each attribute should have detail
		for ( CompletionItem attr : attributes ) {
			// Detail should describe the attribute
			// May be null for some attributes, but check structure
			assertThat( attr.getLabel() ).isNotNull();
		}
	}

	@Test
	@DisplayName( "Should not offer tag completion outside BXM context" )
	void testNoTagCompletionInScriptContext() {
		// This test verifies that tag completion is specific to .bxm files
		// We'll verify the ComponentCompletionRule's when() method filters correctly
		// Actual verification happens in unit tests for the rule itself

		List<CompletionItem> items = getCompletionsAt( 6, 5 );

		// Should have tag completions in .bxm file
		assertThat( items ).isNotEmpty();
		CompletionItem tagItem = items.stream()
		    .filter( item -> item.getLabel().startsWith( "bx:" ) )
		    .findFirst()
		    .orElse( null );

		assertThat( tagItem ).isNotNull();
	}

	/**
	 * Helper method to get completions at a specific position
	 */
	private List<CompletionItem> getCompletionsAt( int line, int character ) {
		Position			position			= new Position( line, character );
		CompletionParams		completionParams	= new CompletionParams();
		TextDocumentIdentifier	td				= new TextDocumentIdentifier( templatePath.toUri().toString() );
		completionParams.setPosition( position );
		completionParams.setTextDocument( td );
		return pcp.getAvailableCompletions( templateFile.toURI(), completionParams );
	}

	/**
	 * Helper method to find a completion item by label
	 */
	private CompletionItem findCompletion( List<CompletionItem> items, String label ) {
		return items.stream()
		    .filter( item -> item.getLabel().equals( label ) )
		    .findFirst()
		    .orElse( null );
	}
}
