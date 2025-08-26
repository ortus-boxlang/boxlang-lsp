package ortus.boxlang.lsp;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

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

		int						i				= 0;
	}
}
