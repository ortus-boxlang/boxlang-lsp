package ortus.boxlang.lsp.workspace;

import static com.google.common.truth.Truth.assertThat;

import java.nio.file.Path;
import java.nio.file.Files;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import ortus.boxlang.lsp.workspace.index.ProjectIndex;
import ortus.boxlang.lsp.UserSettings;

import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ortus.boxlang.lsp.BaseTest;
import ortus.boxlang.lsp.SourceCodeVisitor;
import ortus.boxlang.lsp.SourceCodeVisitorService;
import ortus.boxlang.compiler.parser.ParsingResult;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.CodeAction;

class MemoryManagementTest extends BaseTest {

	@TempDir
	Path directory;

	@Test
	void anImmediateRequestUsesTheLatestUnsavedVersionWithoutRepeatedFullParsing() {
		ProjectContextProvider	provider	= new ProjectContextProvider();
		var						uri			= directory.resolve( "Edited.bx" ).toUri();
		String					original	= "class { function original() {} }";
		String					edited		= "class { function edited() {} }";
		provider.trackDocumentOpen( uri, original, 1 );
		try {
			provider.trackDocumentChange( uri, List.of( new TextDocumentContentChangeEvent( edited ) ), 2 );
			FileParseResult.resetProfiling();
			assertThat( provider.getLatestFileParseResultPublic( uri ).orElseThrow().hasSource( edited ) ).isTrue();
			assertThat( provider.getSemanticTokens( uri ).getData() ).isNotEmpty();
			provider.trackDocumentChange( uri, List.of( new TextDocumentContentChangeEvent( original ) ), 1 );
			assertThat( provider.getLatestFileParseResultPublic( uri ).orElseThrow().hasSource( edited ) ).isTrue();
			assertThat( FileParseResult.getProfilingSnapshot().fullParses() ).isEqualTo( 1 );
		} finally {
			provider.trackDocumentClose( uri );
		}
	}

	@Test
	void concurrentWorkspaceRequestsShareTheRunningScan() throws Exception {
		var file = directory.resolve( "Target.bx" );
		Files.writeString( file, "class {}" );
		ProjectContextProvider	provider	= new ProjectContextProvider();
		UserSettings			settings	= new UserSettings() {

												@Override
												public boolean isEnableBackgroundParsing() {
													return true;
												}
											};
		provider.setUserSettings( settings );
		provider.setWorkspaceFolders( List.of( new WorkspaceFolder( directory.toUri().toString(), "scan" ) ) );
		CountDownLatch	entered	= new CountDownLatch( 1 );
		CountDownLatch	release	= new CountDownLatch( 1 );
		var				indexed	= new java.util.concurrent.atomic.AtomicInteger();
		ProjectIndex	index	= new ProjectIndex() {

									@Override
									public void indexFile( URI uri ) {
										indexed.incrementAndGet();
										entered.countDown();
										try {
											if ( !release.await( 10, TimeUnit.SECONDS ) )
												throw new IllegalStateException( "Index release timed out" );
										} catch ( InterruptedException e ) {
											Thread.currentThread().interrupt();
											throw new RuntimeException( e );
										}
										super.indexFile( uri );
									}
								};
		index.initialize( directory );
		provider.setIndex( index );
		var												first	= provider.parseWorkspace();
		java.util.concurrent.CompletableFuture<Void>	opening	= null;
		try {
			assertThat( entered.await( 10, TimeUnit.SECONDS ) ).isTrue();
			assertThat( provider.parseWorkspace() ).isSameInstanceAs( first );
			opening = java.util.concurrent.CompletableFuture.runAsync( () -> provider.trackDocumentOpen( file.toUri(), "class {}" ) );
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos( 5 );
			while ( provider.getDocumentModel( file.toUri() ) == null && System.nanoTime() < deadline )
				Thread.sleep( 10 );
			assertThat( provider.getDocumentModel( file.toUri() ) ).isNotNull();
		} finally {
			release.countDown();
			first.get( 10, TimeUnit.SECONDS );
			if ( opening != null )
				opening.get( 10, TimeUnit.SECONDS );
			provider.trackDocumentClose( file.toUri() );
		}
		assertThat( indexed.get() ).isEqualTo( 1 );
	}

	@Test
	void referencesIncludeUncachedWorkspaceScriptsAndUnsavedOpenContent() throws Exception {
		var		declaration	= directory.resolve( "Target.bx" );
		var		closed		= directory.resolve( "Closed.bxs" );
		var		unsaved		= directory.resolve( "Unsaved.bxs" );
		String	source		= "class {\n function ping() {}\n}";
		Files.writeString( declaration, source );
		Files.writeString( closed, "ping();" );
		Files.writeString( unsaved, "different();" );
		Files.writeString( directory.resolve( ".gitignore" ), "Ignored.bxs\n" );
		var ignored = directory.resolve( "Ignored.bxs" );
		Files.writeString( ignored, "ping();" );
		ProjectContextProvider provider = new ProjectContextProvider();
		provider.setWorkspaceFolders( List.of( new WorkspaceFolder( directory.toUri().toString(), "references" ) ) );
		provider.trackDocumentOpen( declaration.toUri(), source );
		provider.trackDocumentOpen( unsaved.toUri(), "ping();" );
		try {
			var references = provider.findReferences( declaration.toUri(), new Position( 1, source.split( "\\n" )[ 1 ].indexOf( "ping" ) ), false );
			assertThat( references.stream().map( location -> location.getUri() ).toList() )
			    .containsAtLeast( closed.toUri().toString(), unsaved.toUri().toString() );
			assertThat( references.stream().map( location -> location.getUri() ).toList() ).doesNotContain( ignored.toUri().toString() );
		} finally {
			provider.trackDocumentClose( declaration.toUri() );
			provider.trackDocumentClose( unsaved.toUri() );
		}
	}

	@Test
	void closedFileResultsAreEvictedAndCanBeReloaded() throws Exception {
		ProjectContextProvider	provider	= new ProjectContextProvider();
		var						firstFile	= directory.resolve( "First.bxs" );
		Files.writeString( firstFile, "first = 1;" );
		var first = provider.getLatestFileParseResultPublic( firstFile.toUri() ).orElseThrow();
		for ( int i = 0; i < 300; i++ ) {
			var file = directory.resolve( "Other" + i + ".bxs" );
			Files.writeString( file, "value = 1;" );
			provider.getLatestFileParseResultPublic( file.toUri() ).orElseThrow();
		}
		assertThat( provider.getClosedFileCacheSize() ).isAtMost( 256 );
		assertThat( provider.getLatestFileParseResultPublic( firstFile.toUri() ).orElseThrow() ).isNotSameInstanceAs( first );
	}

	@Test
	void processingKeepsItsParsingResultAliveDuringGc() {
		GcVisitor.target = directory.resolve( "Lifetime.bx" ).toUri();
		SourceCodeVisitorService.getInstance().addVisitor( GcVisitor.class );
		try {
			var result = FileParseResult.fromSourceString( GcVisitor.target, "class { property name='value'; function run() {} }" );
			assertThat( result.properties() ).hasSize( 1 );
			assertThat( result.getOutline() ).isNotEmpty();
		} finally {
			GcVisitor.target = null;
		}
	}

	public static class GcVisitor extends SourceCodeVisitor {

		static URI target;

		public GcVisitor() {
		}

		@Override
		public boolean canVisit( FileParseResult result ) {
			if ( !result.getURI().equals( target ) )
				return false;
			WeakReference<ParsingResult> reference = new WeakReference<>( result.getParsingResult().orElseThrow() );
			for ( int i = 0; i < 5; i++ )
				System.gc();
			assertThat( reference.get() ).isNotNull();
			return false;
		}

		@Override
		public List<Diagnostic> getDiagnostics() {
			return List.of();
		}

		@Override
		public List<CodeAction> getCodeActions() {
			return List.of();
		}
	}

	@Test
	void semanticTokensReuseTheCurrentDocumentParse() {
		ProjectContextProvider	provider	= new ProjectContextProvider();
		var						uri			= directory.resolve( "Example.bx" ).toUri();
		provider.trackDocumentOpen( uri, "class { function original() { return 1; } }", 1 );
		try {
			FileParseResult.resetProfiling();
			var first = provider.getSemanticTokens( uri ).getData();
			assertThat( first ).isNotEmpty();
			assertThat( provider.getSemanticTokens( uri ).getData() ).isEqualTo( first );
			assertThat( FileParseResult.getProfilingSnapshot().fullParses() ).isEqualTo( 0 );
		} finally {
			provider.trackDocumentClose( uri );
		}
	}
}
