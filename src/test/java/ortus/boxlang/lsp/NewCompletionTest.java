package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertThat;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.Test;

import ortus.boxlang.lsp.workspace.ProjectContextProvider;

public class NewCompletionTest {

	@Test
	void testItShouldOfferCompletionsForFilesInSameFolder() {
		ProjectContextProvider	pcp					= ProjectContextProvider.getInstance();
		// Get the project root directory
		Path					projectRoot			= Paths.get( System.getProperty( "user.dir" ) );
		// Resolve the file path relative to the project root directory
		Path					p					= projectRoot.resolve( "src/test/resources/files/newCompletions/mainTest.bx" );
		File					f					= p.toFile();

		CompletionParams		completionParams	= new CompletionParams();
		TextDocumentIdentifier	td					= new TextDocumentIdentifier( p.toUri().toString() );
		completionParams.setPosition( new Position( 2, 20 ) );
		completionParams.setTextDocument( td );

		List<CompletionItem>	completionItems	= pcp.getAvailableCompletions( f.toURI(), completionParams );

		boolean					hasWidget		= completionItems.stream()
		    .anyMatch( ci -> ci.getInsertText().contains( "Widget()" ) );

		assertThat( hasWidget ).isTrue();
	}

	@Test
	void testItShouldOfferCompletionsForSelf() {
		ProjectContextProvider	pcp					= ProjectContextProvider.getInstance();
		// Get the project root directory
		Path					projectRoot			= Paths.get( System.getProperty( "user.dir" ) );
		// Resolve the file path relative to the project root directory
		Path					p					= projectRoot.resolve( "src/test/resources/files/newCompletions/mainTest.bx" );
		File					f					= p.toFile();

		CompletionParams		completionParams	= new CompletionParams();
		TextDocumentIdentifier	td					= new TextDocumentIdentifier( p.toUri().toString() );
		completionParams.setPosition( new Position( 2, 20 ) );
		completionParams.setTextDocument( td );

		List<CompletionItem>	completionItems	= pcp.getAvailableCompletions( f.toURI(), completionParams );

		boolean					hasMainTest		= completionItems.stream()
		    .anyMatch( ci -> ci.getInsertText().contains( "mainTest()" ) );

		assertThat( hasMainTest ).isTrue();
	}

	@Test
	void testItShouldOfferCompletionsForFolders() {
		ProjectContextProvider	pcp					= ProjectContextProvider.getInstance();
		// Get the project root directory
		Path					projectRoot			= Paths.get( System.getProperty( "user.dir" ) );
		// Resolve the file path relative to the project root directory
		Path					p					= projectRoot.resolve( "src/test/resources/files/newCompletions/mainTest.bx" );
		File					f					= p.toFile();

		CompletionParams		completionParams	= new CompletionParams();
		TextDocumentIdentifier	td					= new TextDocumentIdentifier( p.toUri().toString() );
		completionParams.setPosition( new Position( 2, 20 ) );
		completionParams.setTextDocument( td );

		List<CompletionItem>		completionItems	= pcp.getAvailableCompletions( f.toURI(), completionParams );

		Optional<CompletionItem>	cItem			= completionItems.stream()
		    .filter( ci -> ci.getInsertText().contains( "sub" ) )
		    .findFirst();

		assertThat( cItem.isPresent() ).isTrue();
		assertThat( cItem.get().getInsertText() ).contains( "sub" );
		assertThat( cItem.get().getDetail() ).contains( "folder" );
	}

	@Test
	void testItShouldOfferCompletionsForClassInAFolder() {
		ProjectContextProvider	pcp					= ProjectContextProvider.getInstance();
		// Get the project root directory
		Path					projectRoot			= Paths.get( System.getProperty( "user.dir" ) );
		// Resolve the file path relative to the project root directory
		Path					p					= projectRoot.resolve( "src/test/resources/files/newCompletions/mainTest.bx" );
		File					f					= p.toFile();

		CompletionParams		completionParams	= new CompletionParams();
		TextDocumentIdentifier	td					= new TextDocumentIdentifier( p.toUri().toString() );
		completionParams.setPosition( new Position( 6, 24 ) );
		completionParams.setTextDocument( td );

		List<CompletionItem>		completionItems	= pcp.getAvailableCompletions( f.toURI(), completionParams );

		Optional<CompletionItem>	cItem			= completionItems.stream()
		    .filter( ci -> ci.getInsertText().contains( "ClassA" ) )
		    .findFirst();

		assertThat( cItem.isPresent() ).isTrue();
		assertThat( cItem.get().getInsertText() ).contains( "ClassA" );
		assertThat( cItem.get().getDetail() ).contains( "class" );
	}
}
