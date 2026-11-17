package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonParser;

import ortus.boxlang.lsp.workspace.ProjectContextProvider;
import ortus.boxlang.lsp.workspace.index.ProjectIndex;

public class ConstructorArgumentEdgeCaseTest extends BaseTest {

	@TempDir
	Path							tempDir;

	private ProjectContextProvider	pcp;
	private ProjectIndex			originalIndex;
	private Path					userFile;

	@BeforeEach
	void setUp() throws Exception {
		pcp				= ProjectContextProvider.getInstance();
		originalIndex	= pcp.getIndex();
		userFile		= tempDir.resolve( "User.bx" );
		Files.writeString( userFile, "class { function init(required string username, boolean active = true) { return this; } }" );

		ProjectIndex index = new ProjectIndex();
		index.initialize( tempDir );
		index.indexFile( userFile.toUri() );
		pcp.setIndex( index );
	}

	@AfterEach
	void tearDown() throws Exception {
		pcp.setIndex( originalIndex );
		Files.deleteIfExists( ProjectIndex.getDefaultCacheFilePath( tempDir ) );
	}

	@Test
	void nestedConstructorOffersItsOwnParametersNotOuterFunctionParameters() throws Exception {
		List<CompletionItem> items = complete( "arrayAppend([], new User(|));" );

		assertThat( items.stream().map( CompletionItem::getLabel ).toList() ).containsAtLeast( "username=", "active=" );
		assertThat( items.stream().map( CompletionItem::getLabel ).toList() ).containsNoneOf( "array=", "value=", "merge=" );
	}

	@Test
	void requiredConstructorParameterRemainsRequiredAfterLoadingLegacyCache() throws Exception {
		pcp.getIndex().saveCache();
		Path	cacheFile	= ProjectIndex.getDefaultCacheFilePath( tempDir );

		// Before this PR, the index persisted every parameter as optional, even for required declarations.
		// Seed that legacy cache state without changing the source file or its modification time.
		var		cache		= JsonParser.parseString( Files.readString( cacheFile ) ).getAsJsonObject();
		for ( var method : cache.getAsJsonArray( "methods" ) ) {
			for ( var parameter : method.getAsJsonObject().getAsJsonArray( "parameters" ) ) {
				parameter.getAsJsonObject().addProperty( "required", false );
			}
		}
		Files.writeString( cacheFile, cache.toString() );

		ProjectIndex restartedIndex = new ProjectIndex();
		restartedIndex.initialize( tempDir );
		// Workspace startup reindexes files only when the index marks them as stale.
		if ( restartedIndex.needsReindexing( userFile.toUri() ) ) {
			restartedIndex.indexFile( userFile.toUri() );
		}
		pcp.setIndex( restartedIndex );

		List<CompletionItem>	items		= complete( "new User(|);" );
		CompletionItem			username	= items.stream().filter( item -> item.getLabel().equals( "username=" ) ).findFirst().orElseThrow();
		CompletionItem			active		= items.stream().filter( item -> item.getLabel().equals( "active=" ) ).findFirst().orElseThrow();

		assertThat( username.getDetail() ).isEqualTo( "required string" );
		assertThat( active.getDetail() ).isEqualTo( "optional boolean" );
		assertThat( username.getSortText() ).isLessThan( active.getSortText() );
	}

	private List<CompletionItem> complete( String callWithCursor ) throws Exception {
		int column = callWithCursor.indexOf( '|' );
		assertThat( column ).isAtLeast( 0 );
		String	source	= callWithCursor.replace( "|", "" );
		Path	file	= tempDir.resolve( "constructor.bxs" );
		Files.writeString( file, source );
		String						uri	= file.toUri().toString();
		BoxLangTextDocumentService	svc	= new BoxLangTextDocumentService();
		svc.didOpen( new DidOpenTextDocumentParams( new TextDocumentItem( uri, "boxlang", 1, source ) ) );
		try {
			var response = svc.completion( new CompletionParams( new TextDocumentIdentifier( uri ), new Position( 0, column ) ) ).get();
			return response.isLeft() ? response.getLeft() : response.getRight().getItems();
		} finally {
			svc.didClose( new DidCloseTextDocumentParams( new TextDocumentIdentifier( uri ) ) );
		}
	}
}
