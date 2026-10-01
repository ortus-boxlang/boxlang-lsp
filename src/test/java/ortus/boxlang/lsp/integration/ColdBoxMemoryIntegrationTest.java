package ortus.boxlang.lsp.integration;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.api.Git;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.ConfigurationParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DocumentSymbolCapabilities;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.WorkspaceClientCapabilities;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.fasterxml.jackson.databind.ObjectMapper;

import jdk.jfr.consumer.RecordingFile;
import ortus.boxlang.lsp.BaseTest;
import ortus.boxlang.lsp.LSPTools;
import ortus.boxlang.lsp.workspace.GitIgnoreMatcher;

@Tag( "coldboxIntegration" )
class ColdBoxMemoryIntegrationTest extends BaseTest {

	private static final String	REPOSITORY	= "https://github.com/ColdBox/coldbox-platform.git";
	private static final String	REF			= "v8.2.0";
	private static final String	REVISION	= "97a2e9ec2ff463d38106f76f926981726fc121f5";

	@Test
	@Timeout( value = 5, unit = TimeUnit.MINUTES )
	void coldBoxWorkspaceRemainsResponsiveWithA512MegabyteHeap() throws Exception {
		Path				artifacts	= Files.createDirectories( Path.of( System.getProperty( "coldbox.artifactsDir" ) ) );
		Path				run			= Files.createTempDirectory( artifacts, "run-" );
		Path				workspace	= run.resolve( "coldbox" );
		boolean				profiling	= Boolean.getBoolean( "coldbox.profile" );
		Map<String, Object>	metrics		= new TreeMap<>();
		System.out.println( "ColdBox integration artifacts: " + run );
		try ( Git git = Git.cloneRepository().setURI( REPOSITORY ).setBranch( "refs/tags/" + REF )
		    .setBranchesToClone( List.of( "refs/tags/" + REF ) ).setDepth( 1 )
		    .setTimeout( 120 ).setDirectory( workspace.toFile() ).call() ) {
			assertEquals( REVISION, git.getRepository().resolve( "HEAD" ).getName(), "ColdBox fixture revision changed" );
		}

		Set<String> files = new HashSet<>();
		GitIgnoreMatcher.create( workspace ).walk( workspace, path -> {
			if ( LSPTools.canWalkFile( path ) ) {
				files.add( path.toUri().toString() );
			}
		} );
		assertThat( files.size() ).isGreaterThan( 500 );
		metrics.put( "workload", "coldbox-lifecycle-v2" );
		metrics.put( "sourceFiles", files.size() );
		metrics.put( "coldboxRevision", REVISION );
		metrics.put( "heapMaxBytes", 512L * 1024 * 1024 );
		metrics.put( "profiling", profiling );
		metrics.put( "lspVersion", System.getProperty( "coldbox.lspVersion" ) );
		metrics.put( "lspRevision", System.getProperty( "coldbox.lspRevision" ) );
		metrics.put( "boxlangVersion", System.getProperty( "coldbox.boxlangVersion" ) );
		metrics.put( "lspClasspathSha256", System.getProperty( "coldbox.lspClasspathSha256" ) );
		metrics.put( "javaVersion", System.getProperty( "java.version" ) );
		Files.writeString( run.resolve( "workload.txt" ), REPOSITORY + "\n" + REF + " " + REVISION
		    + "\nsourceFiles=" + files.size() + "\nheap=512m\nenableBackgroundParsing=true\nprocessDiagnosticsInParallel=true\njava="
		    + System.getProperty( "java.version" )
		    + "\nclasspath=" + System.getProperty( "coldbox.lspClasspath" ) + "\n" );

		Path	home	= run.resolve( "home" );
		Path	config	= home.resolve( ".boxlang/config/boxlang.json" );
		Files.createDirectories( config.getParent() );
		Files.writeString( config, """
		                           {"logging":{"loggers":{"lsp":{"level":"INFO","appender":"console","additive":false}}}}
		                           """ );
		String			java	= Path.of( System.getProperty( "java.home" ), "bin", "java" ).toString();
		ProcessBuilder	builder	= new ProcessBuilder( java, "-Xmx512m", "-XX:+ExitOnOutOfMemoryError",
		    "-XX:+HeapDumpOnOutOfMemoryError", "-XX:HeapDumpPath=heap.hprof", "-Xlog:gc*:file=gc.log",
		    "-Duser.home=" + home, "-cp", System.getProperty( "coldbox.lspClasspath" ),
		    "ortus.boxlang.lsp.App", "--debug-server-port", "0" )
		    .directory( run.toFile() ).redirectErrorStream( true );
		if ( profiling ) {
			builder.command().add( 1, "-XX:StartFlightRecording=name=ColdBox,settings=profile,filename=profile.jfr,dumponexit=true,"
			    + "jdk.InitialEnvironmentVariable#enabled=false,jdk.InitialSystemProperty#enabled=false,jdk.SystemProcess#enabled=false" );
		}
		builder.environment().keySet().removeIf( key -> key.startsWith( "BOXLANG_" )
		    || Set.of( "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS" ).contains( key ) );

		long						start			= System.nanoTime();
		Process						process			= builder.start();
		CompletableFuture<Integer>	port			= new CompletableFuture<>();
		CompletableFuture<Void>		scanCompleted	= new CompletableFuture<>();
		TestClient					client			= new TestClient( files );
		AtomicInteger				completedScans	= new AtomicInteger();
		process.onExit().thenAccept( exited -> {
			IOException failure = new IOException( "LSP exited with code " + exited.exitValue() + "; see " + run );
			port.completeExceptionally( failure );
			scanCompleted.completeExceptionally( failure );
			client.diagnosticsCompleted.completeExceptionally( failure );
		} );
		Thread	output		= captureOutput( process, run.resolve( "lsp.log" ), port, scanCompleted, completedScans );
		var		messages	= Executors.newCachedThreadPool();
		try ( Socket socket = new Socket( "127.0.0.1", port.get( 30, TimeUnit.SECONDS ) ) ) {
			var launcher = LSPLauncher.createClientLauncher( client, socket.getInputStream(), socket.getOutputStream(), messages, consumer -> consumer );
			launcher.startListening();
			LanguageServer		server	= launcher.getRemoteProxy();
			InitializeParams	params	= new InitializeParams();
			params.setProcessId( Math.toIntExact( ProcessHandle.current().pid() ) );
			params.setWorkspaceFolders( List.of( new WorkspaceFolder( workspace.toUri().toString(), "ColdBox" ) ) );
			WorkspaceClientCapabilities workspaceCapabilities = new WorkspaceClientCapabilities();
			workspaceCapabilities.setConfiguration( true );
			TextDocumentClientCapabilities	textCapabilities	= new TextDocumentClientCapabilities();
			DocumentSymbolCapabilities		symbolCapabilities	= new DocumentSymbolCapabilities();
			symbolCapabilities.setHierarchicalDocumentSymbolSupport( true );
			textCapabilities.setDocumentSymbol( symbolCapabilities );
			params.setCapabilities( new ClientCapabilities( workspaceCapabilities, textCapabilities, null ) );
			assertThat( server.initialize( params ).get( 30, TimeUnit.SECONDS ).getCapabilities().getDocumentSymbolProvider() ).isNotNull();
			metrics.put( "initializeResponseMillis", elapsedMillis( start ) );
			server.initialized( new InitializedParams() );

			Path					document	= workspace.resolve( "system/Bootstrap.cfc" );
			TextDocumentIdentifier	id			= new TextDocumentIdentifier( document.toUri().toString() );
			server.getTextDocumentService().didOpen( new DidOpenTextDocumentParams(
			    new TextDocumentItem( id.getUri(), "cfml", 1, Files.readString( document ) ) ) );
			assertResponsive( server, id, metrics, "cold" );
			client.diagnosticsCompleted.get( 180, TimeUnit.SECONDS );
			scanCompleted.get( 180, TimeUnit.SECONDS );
			metrics.put( "workspaceReadyMillis", elapsedMillis( start ) );
			assertResponsive( server, id, metrics, "afterScan" );
			metrics.put( "coldWorkloadMillis", elapsedMillis( start ) );
			assertTrue( process.isAlive(), "LSP must survive background indexing" );
			assertTrue( Files.notExists( run.resolve( "heap.hprof" ) ), "LSP exhausted its heap" );
			if ( profiling )
				captureLiveObjects( process, run, "afterScan" );
			metrics.put( "idleObservationMillis", 12_000 );
			Thread.sleep( 12_000 );
			assertResponsive( server, id, metrics, "afterIdle" );
			if ( profiling )
				captureLiveObjects( process, run, "afterIdle" );

			String	original		= Files.readString( document );
			int		closingBrace	= original.lastIndexOf( '}' );
			assertThat( closingBrace ).isAtLeast( 0 );
			String edited = original.substring( 0, closingBrace ) + "\nfunction lspMemoryProbe() { return 1; }\n" + original.substring( closingBrace );
			server.getTextDocumentService().didChange( new DidChangeTextDocumentParams(
			    new VersionedTextDocumentIdentifier( id.getUri(), 2 ), List.of( new TextDocumentContentChangeEvent( edited ) ) ) );
			assertResponsive( server, id, metrics, "afterEdit" );
			assertThat( new ObjectMapper().writeValueAsString( server.getTextDocumentService().documentSymbol( new DocumentSymbolParams( id ) ).get() ) )
			    .contains( "lspMemoryProbe" );
			if ( profiling )
				captureLiveObjects( process, run, "afterEdit" );
			for ( int pass = 1; pass <= 2; pass++ ) {
				int		expectedScans	= completedScans.get() + 1;
				long	scanStart		= System.nanoTime();
				server.getWorkspaceService().didChangeConfiguration( new DidChangeConfigurationParams(
				    Map.of( "enableBackgroundParsing", false, "processDiagnosticsInParallel", true ) ) );
				server.getWorkspaceService().didChangeConfiguration( new DidChangeConfigurationParams(
				    Map.of( "enableBackgroundParsing", true, "processDiagnosticsInParallel", true ) ) );
				long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos( 180 );
				while ( process.isAlive() && completedScans.get() < expectedScans && System.nanoTime() < deadline )
					Thread.sleep( 25 );
				assertThat( completedScans.get() ).isAtLeast( expectedScans );
				String phase = "afterRescan" + pass;
				metrics.put( phase + ".scanMillis", elapsedMillis( scanStart ) );
				assertResponsive( server, id, metrics, phase );
				assertThat( new ObjectMapper().writeValueAsString( server.getTextDocumentService().documentSymbol( new DocumentSymbolParams( id ) ).get() ) )
				    .contains( "lspMemoryProbe" );
				if ( profiling )
					captureLiveObjects( process, run, phase );
			}
			metrics.put( "completedScans", completedScans.get() );
			var cacheEntries = Pattern.compile( "Closed-file parse cache: entries=(\\d+)" )
			    .matcher( Files.readString( run.resolve( "lsp.log" ) ) ).results()
			    .map( match -> Integer.parseInt( match.group( 1 ) ) ).toList();
			assertThat( cacheEntries.size() ).isAtLeast( 3 );
			assertTrue( cacheEntries.stream().allMatch( size -> size <= 256 ) );
			metrics.put( "maxClosedFileCacheEntriesAtScanCompletion", cacheEntries.stream().mapToInt( Integer::intValue ).max().orElseThrow() );
			metrics.put( "workloadMillis", elapsedMillis( start ) );
			assertTrue( process.isAlive() );
			assertTrue( Files.notExists( run.resolve( "heap.hprof" ) ) );
			server.getTextDocumentService().didClose( new DidCloseTextDocumentParams( id ) );
			server.shutdown().get( 10, TimeUnit.SECONDS );
			server.exit();
			assertTrue( process.waitFor( 10, TimeUnit.SECONDS ), "LSP did not exit after shutdown" );
			assertEquals( 0, process.exitValue() );
			assertThat( Files.readString( run.resolve( "gc.log" ) ) ).contains( "Heap Max Capacity: 512M" );
			metrics.put( "processElapsedMillis", elapsedMillis( start ) );
			if ( profiling ) {
				assertTrue( Files.isRegularFile( run.resolve( "profile.jfr" ) ), "Profiling must record the LSP subprocess" );
				assertTrue( Files.isRegularFile( run.resolve( "live-objects.txt" ) ), "Profiling must capture live objects after the workspace scan" );
				addRecordingMetrics( run.resolve( "profile.jfr" ), process.pid(), metrics );
			}
			ObjectMapper mapper = new ObjectMapper();
			mapper.writerWithDefaultPrettyPrinter().writeValue( run.resolve( "metrics.json" ).toFile(), metrics );
			if ( profiling ) {
				var summary = mapper.readTree( run.resolve( "metrics.json" ).toFile() );
				assertThat( summary.path( "heapAfterCheckpointGcBytes" ).asLong() ).isGreaterThan( 0L );
				assertThat( summary.path( "gcObservedPeakHeapBytes" ).asLong() ).isAtLeast( summary.path( "heapAfterCheckpointGcBytes" ).asLong() );
				assertThat( summary.path( "allocationSamples" ).asInt() ).isGreaterThan( 0 );
				assertThat( summary.path( "estimatedAllocatedBytes" ).asLong() ).isGreaterThan( 0L );
				assertThat( summary.path( "cold.documentSymbolsMillis" ).asDouble() ).isGreaterThan( 0.0 );
				for ( String phase : List.of( "afterScan", "afterIdle", "afterEdit", "afterRescan1", "afterRescan2" ) ) {
					assertThat( summary.path( phase + ".heapAfterGcBytes" ).asLong() ).isGreaterThan( 0L );
					assertThat( summary.path( phase + ".documentSymbolsMillis" ).asDouble() ).isGreaterThan( 0.0 );
				}
			}
			System.out.println( "ColdBox metrics: " + metrics );
			Files.writeString( run.resolve( "result.txt" ), "PASS\nsourceFiles=" + files.size() + "\nelapsedMillis="
			    + TimeUnit.NANOSECONDS.toMillis( System.nanoTime() - start ) + "\n" );
		} finally {
			process.destroyForcibly();
			messages.shutdownNow();
			process.waitFor( 10, TimeUnit.SECONDS );
			output.join( 1_000 );
		}
	}

	private void captureLiveObjects( Process server, Path run, String phase ) throws Exception {
		String	file	= "afterScan".equals( phase ) ? "live-objects.txt" : phase + "-live-objects.txt";
		String	jcmd	= Path.of( System.getProperty( "java.home" ), "bin", "jcmd" ).toString();
		Process	tool	= new ProcessBuilder( jcmd, Long.toString( server.pid() ), "GC.class_histogram" )
		    .redirectErrorStream( true ).redirectOutput( run.resolve( file ).toFile() ).start();
		try {
			assertTrue( tool.waitFor( 30, TimeUnit.SECONDS ), "Live-object snapshot timed out" );
			assertEquals( 0, tool.exitValue(), "jcmd failed; see live-objects.txt" );
			assertThat( Files.readString( run.resolve( file ) ) ).contains( "Total" );
		} finally {
			tool.destroyForcibly();
		}
	}

	private void assertResponsive( LanguageServer server, TextDocumentIdentifier id, Map<String, Object> metrics, String phase ) throws Exception {
		long start = System.nanoTime();
		assertThat( server.getTextDocumentService().documentSymbol( new DocumentSymbolParams( id ) ).get( 30, TimeUnit.SECONDS ) ).isNotEmpty();
		metrics.put( phase + ".documentSymbolsMillis", elapsedMillis( start ) );
		start = System.nanoTime();
		assertThat( server.getTextDocumentService().completion( new CompletionParams( id, new Position( 0, 0 ) ) ).get( 30, TimeUnit.SECONDS ) ).isNotNull();
		metrics.put( phase + ".completionMillis", elapsedMillis( start ) );
		start = System.nanoTime();
		assertThat( server.getTextDocumentService().semanticTokensFull( new SemanticTokensParams( id ) ).get( 30, TimeUnit.SECONDS ).getData() ).isNotEmpty();
		metrics.put( phase + ".semanticTokensMillis", elapsedMillis( start ) );
	}

	private double elapsedMillis( long start ) {
		return ( System.nanoTime() - start ) / 1_000_000.0;
	}

	private void addRecordingMetrics( Path path, long pid, Map<String, Object> metrics ) throws Exception {
		long					peakHeap			= 0, allocatedBytes = 0, pauseNanos = 0;
		int						gcCount				= 0, allocationSamples = 0, checkpointGcId = -1;
		Instant					checkpointTime		= Instant.MIN, firstEvent = Instant.MAX, lastEvent = Instant.MIN;
		Map<Integer, Long>		heapAfterGc			= new HashMap<>();
		Map<Instant, Integer>	checkpoints			= new TreeMap<>();
		Map<Integer, Double>	checkpointPauses	= new HashMap<>();
		boolean					recordedServer		= false;
		try ( RecordingFile recording = new RecordingFile( path ) ) {
			while ( recording.hasMoreEvents() ) {
				var event = recording.readEvent();
				if ( event.getStartTime().isBefore( firstEvent ) ) {
					firstEvent = event.getStartTime();
				}
				if ( event.getEndTime().isAfter( lastEvent ) ) {
					lastEvent = event.getEndTime();
				}
				switch ( event.getEventType().getName() ) {
					case "jdk.JVMInformation" -> recordedServer |= event.getLong( "pid" ) == pid;
					case "jdk.GCHeapSummary" -> {
						long used = event.getLong( "heapUsed" );
						peakHeap = Math.max( peakHeap, used );
						if ( "After GC".equals( event.getString( "when" ) ) ) {
							heapAfterGc.put( event.getInt( "gcId" ), used );
						}
					}
					case "jdk.GarbageCollection" -> {
						gcCount++;
						pauseNanos += event.getDuration( "sumOfPauses" ).toNanos();
						if ( "Heap Inspection Initiated GC".equals( event.getString( "cause" ) ) ) {
							checkpoints.put( event.getStartTime(), event.getInt( "gcId" ) );
							checkpointPauses.put( event.getInt( "gcId" ), event.getDuration( "sumOfPauses" ).toNanos() / 1_000_000.0 );
						}
						if ( "Heap Inspection Initiated GC".equals( event.getString( "cause" ) ) && event.getStartTime().isAfter( checkpointTime ) ) {
							checkpointGcId	= event.getInt( "gcId" );
							checkpointTime	= event.getStartTime();
							metrics.put( "checkpointGcPauseMillis", event.getDuration( "sumOfPauses" ).toNanos() / 1_000_000.0 );
						}
					}
					case "jdk.ObjectAllocationSample" -> {
						allocationSamples++;
						allocatedBytes += event.getLong( "weight" );
					}
				}
			}
		}
		assertTrue( recordedServer, "The recording must describe the LSP, not the Gradle/test JVM" );
		assertThat( heapAfterGc ).containsKey( checkpointGcId );
		metrics.put( "gcObservedPeakHeapBytes", peakHeap );
		metrics.put( "heapAfterCheckpointGcBytes", heapAfterGc.get( checkpointGcId ) );
		List<String> phases = List.of( "afterScan", "afterIdle", "afterEdit", "afterRescan1", "afterRescan2" );
		assertThat( checkpoints ).hasSize( phases.size() );
		int phaseIndex = 0;
		for ( int id : checkpoints.values() ) {
			String phase = phases.get( phaseIndex++ );
			assertThat( heapAfterGc ).containsKey( id );
			metrics.put( phase + ".heapAfterGcBytes", heapAfterGc.get( id ) );
			metrics.put( phase + ".checkpointGcPauseMillis", checkpointPauses.get( id ) );
		}
		metrics.put( "gcCount", gcCount );
		metrics.put( "gcPauseMillis", pauseNanos / 1_000_000.0 );
		metrics.put( "allocationSamples", allocationSamples );
		metrics.put( "estimatedAllocatedBytes", allocatedBytes );
		double recordingSeconds = Duration.between( firstEvent, lastEvent ).toNanos() / 1_000_000_000.0;
		assertThat( recordingSeconds ).isGreaterThan( 0.0 );
		metrics.put( "recordedEventWindowMillis", recordingSeconds * 1_000 );
		metrics.put( "estimatedAllocationBytesPerSecond", allocatedBytes / recordingSeconds );
	}

	private Thread captureOutput( Process process, Path log, CompletableFuture<Integer> port, CompletableFuture<Void> scanCompleted,
	    AtomicInteger completedScans ) {
		return Thread.ofPlatform().daemon().start( () -> {
			try ( var reader = process.inputReader(); var writer = Files.newBufferedWriter( log ) ) {
				String line;
				while ( ( line = reader.readLine() ) != null ) {
					writer.write( line );
					writer.newLine();
					writer.flush();
					if ( line.startsWith( "Listening on port: " ) ) {
						port.complete( Integer.parseInt( line.substring( "Listening on port: ".length() ).trim() ) );
					}
					// ponytail: use the existing scan-completion log until protocol work-done progress is exposed.
					if ( line.contains( "Saved project index cache" ) ) {
						completedScans.incrementAndGet();
						scanCompleted.complete( null );
					}
				}
			} catch ( Exception e ) {
				port.completeExceptionally( e );
				scanCompleted.completeExceptionally( e );
			}
		} );
	}

	private static class TestClient implements LanguageClient {

		private final Set<String>				remaining				= ConcurrentHashMap.newKeySet();
		private final CompletableFuture<Void>	diagnosticsCompleted	= new CompletableFuture<>();

		private TestClient( Set<String> files ) {
			remaining.addAll( files );
		}

		@Override
		public CompletableFuture<List<Object>> configuration( ConfigurationParams params ) {
			return CompletableFuture.completedFuture( params.getItems().stream()
			    .map( item -> ( Object ) ( "boxlang.lsp".equals( item.getSection() )
			        ? Map.of( "enableBackgroundParsing", true, "processDiagnosticsInParallel", true )
			        : Map.of() ) )
			    .toList() );
		}

		@Override
		public void publishDiagnostics( PublishDiagnosticsParams params ) {
			remaining.remove( params.getUri() );
			if ( remaining.isEmpty() ) {
				diagnosticsCompleted.complete( null );
			}
		}

		@Override
		public void telemetryEvent( Object event ) {
		}

		@Override
		public void showMessage( MessageParams params ) {
		}

		@Override
		public CompletableFuture<MessageActionItem> showMessageRequest( ShowMessageRequestParams params ) {
			return CompletableFuture.completedFuture( null );
		}

		@Override
		public void logMessage( MessageParams params ) {
		}
	}
}
