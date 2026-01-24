package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertThat;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

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

public class ImportPathCompletionDebugTest extends BaseTest {

	private static Path				projectRoot;
	private static Path				testDir;
	private ProjectContextProvider	pcp;

	@BeforeAll
	public static void setUpClass() {
		projectRoot	= Paths.get( System.getProperty( "user.dir" ) );
		testDir		= projectRoot.resolve( "src/test/resources/files/importPathCompletionTest" );
	}

	@BeforeEach
	public void setUp() throws Exception {
		pcp = ProjectContextProvider.getInstance();

		// Initialize index with test files
		ProjectIndex index = pcp.getIndex();
		index.clear();
		index.initialize( testDir );

		// Index all test files recursively
		indexDirectory( testDir, index );
	}

	private void indexDirectory( Path dir, ProjectIndex index ) throws Exception {
		for ( File file : dir.toFile().listFiles() ) {
			if ( file.isDirectory() ) {
				indexDirectory( file.toPath(), index );
			} else if ( file.getName().endsWith( ".bx" ) || file.getName().endsWith( ".bxs" ) ) {
				index.indexFile( file.toURI() );
			}
		}
	}

	@Test
	void debugIndexContents() throws Exception {
		ProjectIndex index = pcp.getIndex();

		System.out.println( "============ DEBUG: Index Contents ============" );
		System.out.println( "Workspace root: " + index.getWorkspaceRoot() );
		System.out.println( "Total classes in index: " + index.getAllClasses().size() );

		index.getAllClasses().forEach( indexedClass -> {
			System.out.println( "  - " + indexedClass.fullyQualifiedName() + " (name: " + indexedClass.name() + ")" );
		} );

		// Try to get completions
		Path					p					= testDir.resolve( "emptyImport.bx" );
		File					f					= p.toFile();

		CompletionParams		completionParams	= new CompletionParams();
		TextDocumentIdentifier	td					= new TextDocumentIdentifier( p.toUri().toString() );
		completionParams.setPosition( new Position( 0, 7 ) );
		completionParams.setTextDocument( td );

		List<CompletionItem> completionItems = pcp.getAvailableCompletions( f.toURI(), completionParams );

		System.out.println( "\n============ DEBUG: Completion Items ============" );
		System.out.println( "Total completion items: " + completionItems.size() );

		completionItems.stream()
		    .filter( ci -> ci.getKind() == CompletionItemKind.Module || ci.getKind() == CompletionItemKind.Class )
		    .forEach( ci -> {
			    System.out.println( "  - " + ci.getLabel() + " (kind: " + ci.getKind() + ", detail: "
			        + ( ci.getLabelDetails() != null ? ci.getLabelDetails().getDescription() : "null" ) + ")" );
		    } );
	}
}
