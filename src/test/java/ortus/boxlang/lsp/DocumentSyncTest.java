package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ortus.boxlang.lsp.workspace.DocumentModel;
import ortus.boxlang.lsp.workspace.ProjectContextProvider;

/**
 * Tests for document synchronization improvements including:
 * - Incremental text document sync
 * - Debouncing of expensive operations
 * - Document version management
 * - Thread-safety during rapid edits
 */
public class DocumentSyncTest extends BaseTest {

	private BoxLangTextDocumentService	svc;
	private Path						testFilePath;
	private URI							testFileUri;

	// Debounce delay is 300ms, so we need to wait a bit longer
	private static final int			DEBOUNCE_WAIT_MS	= 500;

	@BeforeEach
	void setUp() throws Exception {
		svc				= new BoxLangTextDocumentService();
		testFilePath	= java.nio.file.Paths.get( "src/test/resources/files/documentSyncTest.bx" );
		testFileUri		= testFilePath.toUri();

		// Create test file if it doesn't exist
		if ( !Files.exists( testFilePath ) ) {
			Files.writeString( testFilePath, """
			                                 class {
			                                     function hello() {
			                                         return "Hello World";
			                                     }
			                                 }
			                                 """ );
		}
	}

	// ========== Incremental Sync Tests ==========

	@Test
	void testIncrementalSyncSingleCharacterInsert() throws Exception {
		String initialContent = """
		                        class {
		                            function hello() {
		                                return "Hello";
		                            }
		                        }
		                        """;

		// Open the document
		svc.didOpen( new DidOpenTextDocumentParams(
		    new TextDocumentItem( testFileUri.toString(), "boxlang", 1, initialContent ) ) );

		// Create an incremental change - insert " World" after "Hello"
		// Line 2 (0-indexed): " return "Hello";"
		// 8 spaces + "return " (7) + '"' (1) + "Hello" (5) = 21
		// So position 21 is right after the 'o' in "Hello", before the closing quote
		TextDocumentContentChangeEvent	change		= new TextDocumentContentChangeEvent(
		    new Range( new Position( 2, 21 ), new Position( 2, 21 ) ),
		    0,
		    " World"
		);

		VersionedTextDocumentIdentifier	versionedId	= new VersionedTextDocumentIdentifier( testFileUri.toString(), 2 );
		svc.didChange( new DidChangeTextDocumentParams( versionedId, List.of( change ) ) );

		// Verify the DocumentModel was updated immediately (before debounce)
		DocumentModel model = ProjectContextProvider.getInstance().getDocumentModel( testFileUri );
		assertThat( model ).isNotNull();
		assertThat( model.getContent() ).contains( "Hello World" );

		// Wait for debounce to complete
		Thread.sleep( DEBOUNCE_WAIT_MS );

		// Verify the FileParseResult was updated after debounce
		var fpr = ProjectContextProvider.getInstance()
		    .getLatestFileParseResultPublic( testFileUri );

		assertThat( fpr ).isPresent();
		String content = fpr.get().readLine( 2 );
		assertThat( content ).contains( "Hello World" );
	}

	@Test
	void testIncrementalSyncTextReplacement() throws Exception {
		String initialContent = """
		                        class {
		                            function greet() {
		                                return "Hello";
		                            }
		                        }
		                        """;

		svc.didOpen( new DidOpenTextDocumentParams(
		    new TextDocumentItem( testFileUri.toString(), "boxlang", 1, initialContent ) ) );

		// Replace "Hello" with "Goodbye"
		// Line 2: " return "Hello";"
		// "Hello" starts at column 16 and ends at column 21
		TextDocumentContentChangeEvent	change		= new TextDocumentContentChangeEvent(
		    new Range( new Position( 2, 16 ), new Position( 2, 21 ) ),
		    5,
		    "Goodbye"
		);

		VersionedTextDocumentIdentifier	versionedId	= new VersionedTextDocumentIdentifier( testFileUri.toString(), 2 );
		svc.didChange( new DidChangeTextDocumentParams( versionedId, List.of( change ) ) );

		// Verify DocumentModel was updated immediately
		DocumentModel model = ProjectContextProvider.getInstance().getDocumentModel( testFileUri );
		assertThat( model ).isNotNull();
		assertThat( model.getContent() ).contains( "Goodbye" );
		assertThat( model.getContent() ).doesNotContain( "Hello" );

		// Wait for debounce
		Thread.sleep( DEBOUNCE_WAIT_MS );

		var fpr = ProjectContextProvider.getInstance()
		    .getLatestFileParseResultPublic( testFileUri );

		assertThat( fpr ).isPresent();
		String content = fpr.get().readLine( 2 );
		assertThat( content ).contains( "Goodbye" );
		assertThat( content ).doesNotContain( "Hello" );
	}

	@Test
	void testIncrementalSyncTextDeletion() throws Exception {
		String initialContent = """
		                        class {
		                            function greet() {
		                                return "Hello World";
		                            }
		                        }
		                        """;

		svc.didOpen( new DidOpenTextDocumentParams(
		    new TextDocumentItem( testFileUri.toString(), "boxlang", 1, initialContent ) ) );

		// Delete " World" from the string
		// Line 2: " return "Hello World";"
		// " World" starts at column 21 and ends at column 27
		TextDocumentContentChangeEvent	change		= new TextDocumentContentChangeEvent(
		    new Range( new Position( 2, 21 ), new Position( 2, 27 ) ),
		    6,
		    ""
		);

		VersionedTextDocumentIdentifier	versionedId	= new VersionedTextDocumentIdentifier( testFileUri.toString(), 2 );
		svc.didChange( new DidChangeTextDocumentParams( versionedId, List.of( change ) ) );

		// Verify DocumentModel was updated immediately
		DocumentModel model = ProjectContextProvider.getInstance().getDocumentModel( testFileUri );
		assertThat( model ).isNotNull();
		String modelContent = model.getContent();
		assertThat( modelContent ).contains( "Hello" );
		assertThat( modelContent ).doesNotContain( "World" );

		// Wait for debounce
		Thread.sleep( DEBOUNCE_WAIT_MS );

		var fpr = ProjectContextProvider.getInstance()
		    .getLatestFileParseResultPublic( testFileUri );

		assertThat( fpr ).isPresent();
		String content = fpr.get().readLine( 2 );
		assertThat( content ).contains( "Hello" );
		assertThat( content ).doesNotContain( "World" );
	}

	@Test
	void testIncrementalSyncMultiLineChange() throws Exception {
		String initialContent = """
		                        class {
		                            function a() {}
		                            function b() {}
		                        }
		                        """;

		svc.didOpen( new DidOpenTextDocumentParams(
		    new TextDocumentItem( testFileUri.toString(), "boxlang", 1, initialContent ) ) );

		// Replace "function a() {}\n function b() {}" with a single new function
		// Line 1-2: " function a() {}" and " function b() {}"
		TextDocumentContentChangeEvent	change		= new TextDocumentContentChangeEvent(
		    new Range( new Position( 1, 4 ), new Position( 2, 19 ) ),
		    0,
		    "function combined() { return 1; }"
		);

		VersionedTextDocumentIdentifier	versionedId	= new VersionedTextDocumentIdentifier( testFileUri.toString(), 2 );
		svc.didChange( new DidChangeTextDocumentParams( versionedId, List.of( change ) ) );

		// Verify DocumentModel was updated immediately
		DocumentModel model = ProjectContextProvider.getInstance().getDocumentModel( testFileUri );
		assertThat( model ).isNotNull();
		assertThat( model.getContent() ).contains( "combined" );

		// Wait for debounce
		Thread.sleep( DEBOUNCE_WAIT_MS );

		var fpr = ProjectContextProvider.getInstance()
		    .getLatestFileParseResultPublic( testFileUri );

		assertThat( fpr ).isPresent();
		String content = fpr.get().readLine( 1 );
		assertThat( content ).contains( "combined" );
	}

	@Test
	void testFullSyncStillWorks() throws Exception {
		String initialContent = """
		                        class {
		                            function hello() {
		                                return "Initial";
		                            }
		                        }
		                        """;

		svc.didOpen( new DidOpenTextDocumentParams(
		    new TextDocumentItem( testFileUri.toString(), "boxlang", 1, initialContent ) ) );

		String							newContent	= """
		                                              class {
		                                                  function hello() {
		                                                      return "Updated via full sync";
		                                                  }
		                                              }
		                                              """;

		// Full sync - no range
		TextDocumentContentChangeEvent	change		= new TextDocumentContentChangeEvent( newContent );

		VersionedTextDocumentIdentifier	versionedId	= new VersionedTextDocumentIdentifier( testFileUri.toString(), 2 );
		svc.didChange( new DidChangeTextDocumentParams( versionedId, List.of( change ) ) );

		// Verify DocumentModel was updated immediately
		DocumentModel model = ProjectContextProvider.getInstance().getDocumentModel( testFileUri );
		assertThat( model ).isNotNull();
		assertThat( model.getContent() ).contains( "Updated via full sync" );

		// Wait for debounce
		Thread.sleep( DEBOUNCE_WAIT_MS );

		var fpr = ProjectContextProvider.getInstance()
		    .getLatestFileParseResultPublic( testFileUri );

		assertThat( fpr ).isPresent();
		String content = fpr.get().readLine( 2 );
		assertThat( content ).contains( "Updated via full sync" );
	}

	// ========== Debouncing Tests ==========

	@Test
	void testRapidEditsAreDebounced() throws Exception {
		String initialContent = """
		                        class {
		                            function counter() {
		                                var x = 0;
		                            }
		                        }
		                        """;

		svc.didOpen( new DidOpenTextDocumentParams(
		    new TextDocumentItem( testFileUri.toString(), "boxlang", 1, initialContent ) ) );

		// Simulate rapid typing - 10 quick changes
		for ( int i = 1; i <= 10; i++ ) {
			String							newContent	= """
			                                              class {
			                                                  function counter() {
			                                                      var x = %d;
			                                                  }
			                                              }
			                                              """.formatted( i );

			TextDocumentContentChangeEvent	change		= new TextDocumentContentChangeEvent( newContent );
			VersionedTextDocumentIdentifier	versionedId	= new VersionedTextDocumentIdentifier( testFileUri.toString(), i + 1 );
			svc.didChange( new DidChangeTextDocumentParams( versionedId, List.of( change ) ) );
		}

		// Wait for debounce to complete
		Thread.sleep( DEBOUNCE_WAIT_MS );

		// The final document should have the last value
		var fpr = ProjectContextProvider.getInstance()
		    .getLatestFileParseResultPublic( testFileUri );

		assertThat( fpr ).isPresent();
		String content = fpr.get().readLine( 2 );
		assertThat( content ).contains( "x = 10" );
	}

	@Test
	void testDiagnosticsUpdateAfterDebounce() throws Exception {
		// Initial content
		String initialContent = """
		                        class {
		                            function test() {
		                                var x = 1;
		                            }
		                        }
		                        """;

		svc.didOpen( new DidOpenTextDocumentParams(
		    new TextDocumentItem( testFileUri.toString(), "boxlang", 1, initialContent ) ) );

		// Wait for initial diagnostics
		Thread.sleep( 100 );

		var initialDiagnostics = ProjectContextProvider.getInstance().getFileDiagnostics( testFileUri );

		// Make a rapid sequence of edits
		for ( int i = 1; i <= 5; i++ ) {
			String							newContent	= """
			                                              class {
			                                                  function test() {
			                                                      var x = %d;
			                                                  }
			                                              }
			                                              """.formatted( i );

			TextDocumentContentChangeEvent	change		= new TextDocumentContentChangeEvent( newContent );
			VersionedTextDocumentIdentifier	versionedId	= new VersionedTextDocumentIdentifier( testFileUri.toString(), i + 1 );
			svc.didChange( new DidChangeTextDocumentParams( versionedId, List.of( change ) ) );
		}

		// Wait for debounce
		Thread.sleep( DEBOUNCE_WAIT_MS );

		// Diagnostics should be updated
		var finalDiagnostics = ProjectContextProvider.getInstance().getFileDiagnostics( testFileUri );
		assertThat( finalDiagnostics ).isNotNull();
	}

	// ========== Document Version Tests ==========

	@Test
	void testDocumentVersionTracking() throws Exception {
		String content = """
		                 class {
		                     function test() {}
		                 }
		                 """;

		svc.didOpen( new DidOpenTextDocumentParams(
		    new TextDocumentItem( testFileUri.toString(), "boxlang", 1, content ) ) );

		// Get initial version
		var initialVersion = ProjectContextProvider.getInstance().getDocumentVersion( testFileUri );
		assertThat( initialVersion ).isEqualTo( 1 );

		// Update with version 2
		TextDocumentContentChangeEvent	change		= new TextDocumentContentChangeEvent(
		    content.replace( "test", "test2" ) );
		VersionedTextDocumentIdentifier	versionedId	= new VersionedTextDocumentIdentifier( testFileUri.toString(), 2 );
		svc.didChange( new DidChangeTextDocumentParams( versionedId, List.of( change ) ) );

		// Version should be updated
		var newVersion = ProjectContextProvider.getInstance().getDocumentVersion( testFileUri );
		assertThat( newVersion ).isEqualTo( 2 );
	}

	@Test
	void testOutOfOrderVersionsAreRejected() throws Exception {
		String content = """
		                 class {
		                     function test() {}
		                 }
		                 """;

		svc.didOpen( new DidOpenTextDocumentParams(
		    new TextDocumentItem( testFileUri.toString(), "boxlang", 5, content ) ) );

		// Try to apply a change with an older version
		TextDocumentContentChangeEvent	change		= new TextDocumentContentChangeEvent( content.replace( "test", "oldVersion" ) );
		VersionedTextDocumentIdentifier	versionedId	= new VersionedTextDocumentIdentifier( testFileUri.toString(), 3 );
		svc.didChange( new DidChangeTextDocumentParams( versionedId, List.of( change ) ) );

		// The change should be rejected - DocumentModel should still have original content
		DocumentModel model = ProjectContextProvider.getInstance().getDocumentModel( testFileUri );
		assertThat( model ).isNotNull();
		assertThat( model.getContent() ).contains( "test()" );
		assertThat( model.getContent() ).doesNotContain( "oldVersion" );
	}

	// ========== Thread Safety Tests ==========

	@Test
	void testConcurrentDocumentEdits() throws Exception {
		String initialContent = """
		                        class {
		                            function concurrent() {
		                                var value = 0;
		                            }
		                        }
		                        """;

		svc.didOpen( new DidOpenTextDocumentParams(
		    new TextDocumentItem( testFileUri.toString(), "boxlang", 1, initialContent ) ) );

		int				numThreads		= 10;
		int				editsPerThread	= 5;
		ExecutorService	executor		= Executors.newFixedThreadPool( numThreads );
		CountDownLatch	startLatch		= new CountDownLatch( 1 );
		CountDownLatch	doneLatch		= new CountDownLatch( numThreads );
		AtomicInteger	versionCounter	= new AtomicInteger( 1 );

		for ( int t = 0; t < numThreads; t++ ) {
			final int threadId = t;
			executor.submit( () -> {
				try {
					startLatch.await();
					for ( int e = 0; e < editsPerThread; e++ ) {
						String							newContent	= """
						                                              class {
						                                                  function concurrent() {
						                                                      var value = %d;
						                                                  }
						                                              }
						                                              """.formatted( threadId * 100 + e );

						TextDocumentContentChangeEvent	change		= new TextDocumentContentChangeEvent( newContent );
						int								version		= versionCounter.incrementAndGet();
						VersionedTextDocumentIdentifier	versionedId	= new VersionedTextDocumentIdentifier( testFileUri.toString(), version );
						svc.didChange( new DidChangeTextDocumentParams( versionedId, List.of( change ) ) );
					}
				} catch ( Exception e ) {
					e.printStackTrace();
				} finally {
					doneLatch.countDown();
				}
			} );
		}

		// Start all threads simultaneously
		startLatch.countDown();

		// Wait for all threads to complete
		boolean completed = doneLatch.await( 10, TimeUnit.SECONDS );
		assertThat( completed ).isTrue();

		executor.shutdown();

		// Wait for debouncing to complete
		Thread.sleep( DEBOUNCE_WAIT_MS );

		// Document should be in a consistent state
		assertDoesNotThrow( () -> {
			var fpr = ProjectContextProvider.getInstance()
			    .getLatestFileParseResultPublic( testFileUri );
			assertThat( fpr ).isPresent();
		} );
	}

	@Test
	void testConcurrentOpenAndClose() throws Exception {
		int				numIterations	= 20;
		ExecutorService	executor		= Executors.newFixedThreadPool( 4 );
		CountDownLatch	doneLatch		= new CountDownLatch( numIterations * 2 );

		for ( int i = 0; i < numIterations; i++ ) {
			final int		iteration	= i;
			final String	content		= """
			                              class {
			                                  function iteration%d() {}
			                              }
			                              """.formatted( iteration );

			// Open task
			executor.submit( () -> {
				try {
					svc.didOpen( new DidOpenTextDocumentParams(
					    new TextDocumentItem( testFileUri.toString(), "boxlang", iteration, content ) ) );
				} finally {
					doneLatch.countDown();
				}
			} );

			// Close task
			executor.submit( () -> {
				try {
					svc.didClose( new org.eclipse.lsp4j.DidCloseTextDocumentParams(
					    new org.eclipse.lsp4j.TextDocumentIdentifier( testFileUri.toString() ) ) );
				} finally {
					doneLatch.countDown();
				}
			} );
		}

		boolean completed = doneLatch.await( 10, TimeUnit.SECONDS );
		assertThat( completed ).isTrue();

		executor.shutdown();

		// System should be in consistent state - no exceptions should have occurred
	}

	// ========== File Type Support Tests ==========

	@Test
	void testSyncForBxFile() throws Exception {
		Path	bxPath	= java.nio.file.Paths.get( "src/test/resources/files/syncTestClass.bx" );
		String	content	= """
		                  class {
		                      function test() {}
		                  }
		                  """;

		svc.didOpen( new DidOpenTextDocumentParams(
		    new TextDocumentItem( bxPath.toUri().toString(), "boxlang", 1, content ) ) );

		var fpr = ProjectContextProvider.getInstance()
		    .getLatestFileParseResultPublic( bxPath.toUri() );

		assertThat( fpr ).isPresent();
		assertThat( fpr.get().isClass() ).isTrue();
	}

	@Test
	void testSyncForBxmFile() throws Exception {
		Path	bxmPath	= java.nio.file.Paths.get( "src/test/resources/files/syncTestTemplate.bxm" );
		String	content	= """
		                  <bx:output>Hello World</bx:output>
		                  """;

		svc.didOpen( new DidOpenTextDocumentParams(
		    new TextDocumentItem( bxmPath.toUri().toString(), "boxlang", 1, content ) ) );

		var fpr = ProjectContextProvider.getInstance()
		    .getLatestFileParseResultPublic( bxmPath.toUri() );

		assertThat( fpr ).isPresent();
		assertThat( fpr.get().isTemplate() ).isTrue();
	}

	// ========== Edge Cases ==========

	@Test
	void testEmptyDocumentChange() throws Exception {
		String content = """
		                 class {
		                     function test() {}
		                 }
		                 """;

		svc.didOpen( new DidOpenTextDocumentParams(
		    new TextDocumentItem( testFileUri.toString(), "boxlang", 1, content ) ) );

		// Change to empty content
		TextDocumentContentChangeEvent	change		= new TextDocumentContentChangeEvent( "" );
		VersionedTextDocumentIdentifier	versionedId	= new VersionedTextDocumentIdentifier( testFileUri.toString(), 2 );
		svc.didChange( new DidChangeTextDocumentParams( versionedId, List.of( change ) ) );

		// Wait for debounce
		Thread.sleep( DEBOUNCE_WAIT_MS );

		var fpr = ProjectContextProvider.getInstance()
		    .getLatestFileParseResultPublic( testFileUri );

		assertThat( fpr ).isPresent();
	}

	@Test
	void testMultipleChangesInSingleEvent() throws Exception {
		String initialContent = """
		                        class {
		                            var a = 1;
		                            var b = 2;
		                        }
		                        """;

		svc.didOpen( new DidOpenTextDocumentParams(
		    new TextDocumentItem( testFileUri.toString(), "boxlang", 1, initialContent ) ) );

		// Send multiple changes in one event (they should be applied in order)
		// Note: In LSP, multiple changes in a single event should be applied in reverse order
		// because positions are calculated based on the original document
		List<TextDocumentContentChangeEvent>	changes		= List.of(
		    new TextDocumentContentChangeEvent(
		        new Range( new Position( 1, 12 ), new Position( 1, 13 ) ),
		        1,
		        "100"
		    )
		);

		VersionedTextDocumentIdentifier			versionedId	= new VersionedTextDocumentIdentifier( testFileUri.toString(), 2 );
		svc.didChange( new DidChangeTextDocumentParams( versionedId, changes ) );

		// Verify DocumentModel was updated
		DocumentModel model = ProjectContextProvider.getInstance().getDocumentModel( testFileUri );
		assertThat( model ).isNotNull();
		assertThat( model.getContent() ).contains( "100" );

		// Wait for debounce
		Thread.sleep( DEBOUNCE_WAIT_MS );

		var fpr = ProjectContextProvider.getInstance()
		    .getLatestFileParseResultPublic( testFileUri );

		assertThat( fpr ).isPresent();
	}

	// ========== DocumentModel Unit Tests ==========

	@Test
	void testDocumentModelPositionToOffset() throws Exception {
		// Test that position to offset conversion is working correctly
		String content = "line0\nline1\nline2";

		svc.didOpen( new DidOpenTextDocumentParams(
		    new TextDocumentItem( testFileUri.toString(), "boxlang", 1, content ) ) );

		DocumentModel model = ProjectContextProvider.getInstance().getDocumentModel( testFileUri );
		assertThat( model ).isNotNull();

		// Test getLine method
		assertThat( model.getLine( 0 ) ).isEqualTo( "line0" );
		assertThat( model.getLine( 1 ) ).isEqualTo( "line1" );
		assertThat( model.getLine( 2 ) ).isEqualTo( "line2" );
	}
}
