package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertThat;

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
import org.junit.jupiter.api.Test;

import ortus.boxlang.lsp.workspace.ProjectContextProvider;
import ortus.boxlang.lsp.workspace.completion.ContextChecker;
import ortus.boxlang.lsp.workspace.completion.CompletionFacts;
import ortus.boxlang.lsp.workspace.FileParseResult;

public class ImportContextTest {

	@Test
	void testImportContextDetection() throws IOException {
		// Test that import context is detected correctly
		Path	projectRoot	= Paths.get( System.getProperty( "user.dir" ) );
		Path	testFile	= projectRoot.resolve( "src/test/resources/files/importContext.bx" );

		String	testContent	= "import java.net.\nvar x = 5;";
		Files.write( testFile, testContent.getBytes() );

		try {
			FileParseResult			fpr					= FileParseResult.fromFileSystem( testFile.toUri() );
			CompletionParams		completionParams	= new CompletionParams();
			TextDocumentIdentifier	td					= new TextDocumentIdentifier( testFile.toUri().toString() );

			// Test import line
			completionParams.setPosition( new Position( 0, 13 ) ); // Position after "import java.net."
			CompletionFacts	facts1			= new CompletionFacts( fpr, completionParams );
			boolean			isImportContext	= ContextChecker.isImportExpression( facts1 );
			assertThat( isImportContext ).isTrue();

			// Test non-import line
			completionParams.setPosition( new Position( 1, 9 ) ); // Position after "var x = 5"
			CompletionFacts	facts2				= new CompletionFacts( fpr, completionParams );
			boolean			isImportContext2	= ContextChecker.isImportExpression( facts2 );
			assertThat( isImportContext2 ).isFalse();

		} finally {
			Files.deleteIfExists( testFile );
		}
	}
}