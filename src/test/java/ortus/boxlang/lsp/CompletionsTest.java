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
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import ortus.boxlang.lsp.workspace.ProjectContextProvider;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.IBoxContext;

public class CompletionsTest {

	static BoxRuntime	instance;
	IBoxContext			context;

	@BeforeAll
	public static void setUp() {
		instance = BoxRuntime.getInstance( true );
	}

	@Test
	void testGetAvailableCompletions() {
		ProjectContextProvider	pcp			= ProjectContextProvider.getInstance();
		// Get the project root directory
		Path					projectRoot	= Paths.get( System.getProperty( "user.dir" ) );
		// Resolve the file path relative to the project root directory
		Path					p			= projectRoot.resolve( "src/test/resources/files/completionTest1.bx" );
		File					f			= p.toFile();
		assertTrue( f.exists(), "Test file does not exist: " + p.toString() );
		List<CompletionItem> completionItems = pcp.getAvailableCompletions( f.toURI(), new CompletionParams() );
		assertFalse( completionItems.isEmpty(), "Completion items should not be empty." );
	}

	@Test
	void testGetAvailableCompletionsInTemplate() {
		ProjectContextProvider	pcp			= ProjectContextProvider.getInstance();
		// Get the project root directory
		Path					projectRoot	= Paths.get( System.getProperty( "user.dir" ) );
		// Resolve the file path relative to the project root directory
		Path					p			= projectRoot.resolve( "src/test/resources/files/template.bxm" );
		File					f			= p.toFile();
		assertTrue( f.exists(), "Test file does not exist: " + p.toString() );

		CompletionParams		c	= new CompletionParams();
		TextDocumentIdentifier	td	= new TextDocumentIdentifier( p.toUri().toString() );
		c.setPosition( new Position( 3, 1 ) );
		c.setTextDocument( td );
		List<CompletionItem> completionItems = pcp.getAvailableCompletions( f.toURI(), c );
		assertFalse( completionItems.isEmpty(), "Completion items should not be empty." );

		CompletionItem item = completionItems.stream().filter( ci -> ci.getLabel().equals( "bx:thread" ) ).findFirst().get();

		assertTrue( item != null, "Couldn't find the bx:thread autosuggest." );

		assertThat( item.getInsertText() ).isEqualTo( "<bx:thread >$0</bx:thread>" );
	}

	@Test
	void testGetAvailableCompletionsInTemplateForPartialComponent() {
		ProjectContextProvider	pcp			= ProjectContextProvider.getInstance();
		// Get the project root directory
		Path					projectRoot	= Paths.get( System.getProperty( "user.dir" ) );
		// Resolve the file path relative to the project root directory
		Path					p			= projectRoot.resolve( "src/test/resources/files/template.bxm" );
		File					f			= p.toFile();
		assertTrue( f.exists(), "Test file does not exist: " + p.toString() );

		try {
			ProjectContextProvider.getInstance().trackDocumentOpen( p.toUri(), Files.readString( p ) );
		} catch ( IOException e ) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		CompletionParams		c	= new CompletionParams();
		TextDocumentIdentifier	td	= new TextDocumentIdentifier( p.toUri().toString() );
		c.setPosition( new Position( 5, 7 ) );
		c.setTextDocument( td );
		List<CompletionItem> completionItems = pcp.getAvailableCompletions( f.toURI(), c );
		assertFalse( completionItems.isEmpty(), "Completion items should not be empty." );

		CompletionItem item = completionItems.stream().filter( ci -> ci.getLabel().equals( "bx:thread" ) ).findFirst().get();

		assertTrue( item != null, "Couldn't find the bx:thread autosuggest." );

		assertThat( item.getInsertText() ).isEqualTo( "thread >$0</bx:thread>" );
	}
}
