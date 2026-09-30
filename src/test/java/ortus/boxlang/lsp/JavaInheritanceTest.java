package ortus.boxlang.lsp;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ortus.boxlang.lsp.workspace.ProjectContextProvider;
import ortus.boxlang.lsp.workspace.index.ProjectIndex;

class JavaInheritanceTest extends BaseTest {

	@TempDir
	Path					tempDir;
	ProjectIndex			index;
	ProjectContextProvider	provider;

	@BeforeEach
	void setUp() {
		index = new ProjectIndex();
		index.initialize( tempDir );
		provider = ProjectContextProvider.getInstance();
		provider.setIndex( index );
	}

	@Test
	void javaReferencesResolveWithoutFilesystemLookup() throws Exception {
		Path file = openClass( "JavaImplements", """
		                                         class extends="java:java.util.ArrayList" implements="java:java.lang.Runnable, java:java.io.Serializable" {
		                                             function run() {}
		                                         }
		                                         """ );

		assertTrue( provider.getFileDiagnostics( file.toUri() ).stream()
		    .noneMatch( d -> d.getCode() != null && List.of( "invalidExtends", "invalidImplements" ).contains( d.getCode().getLeft() ) ) );
		var runnable = index.findClassWithContext( "JAVA:java.lang.Runnable", file.toUri() ).orElseThrow();
		assertTrue( runnable.isInterface() );
		assertNull( runnable.fileUri() );
		URI windowsFile = URI.create( "file:///c:/Users/brad/project/JavaImplements.bx" );
		assertTrue( index.findClassWithContext( "java:java.lang.Runnable", windowsFile ).isPresent() );
		assertTrue( index.findClassWithContext( "java:missing.Interface", windowsFile ).isEmpty() );
		assertEquals( 1, index.getAllClasses().size(), "Java metadata must not enter the source/disk index" );
	}

	@Test
	void missingJavaTypesStillReportDiagnosticsAndNeverResolveBoxLangFiles() throws Exception {
		// A colon is legal on Unix; this decoy must not satisfy a Java reference on any platform.
		if ( !System.getProperty( "os.name" ).startsWith( "Windows" ) ) {
			Path decoy = tempDir.resolve( "java:missing" );
			Files.createDirectories( decoy );
			Files.writeString( decoy.resolve( "Parent.bx" ), "class {}" );
		}
		Path file = openClass( "Missing", "class extends=\"java:missing.Parent\" implements=\"java:missing.Interface\" {}" );
		assertTrue( index.findClassWithContext( "java:missing.Parent", file.toUri() ).isEmpty() );
		var codes = provider.getFileDiagnostics( file.toUri() ).stream()
		    .filter( d -> d.getCode() != null ).map( d -> d.getCode().getLeft() ).toList();
		assertTrue( codes.contains( "invalidExtends" ), codes.toString() );
		assertTrue( codes.contains( "invalidImplements" ), codes.toString() );
	}

	@Test
	void inheritedJavaCompletionsIncludeOverloadsAndRespectOverrides() throws Exception {
		Path					file	= openClass( "JavaList", """
		                                                         class extends="java:java.util.ArrayList" {
		                                                             function size() { return 42; }
		                                                             function example() {
		                                                                 this.add( "value" );
		                                                                 variables.clear();
		                                                                 super.size();
		                                                                 isEmpty();
		                                                             }
		                                                         }
		                                                         """ );
		List<CompletionItem>	adds	= completions( file, 3, "            this.ad" ).stream()
		    .filter( c -> c.getLabel().equals( "add" ) ).toList();
		assertEquals( 2, adds.size() );
		assertTrue( adds.stream().allMatch( c -> c.getDetail().contains( "java.lang.Object" ) && c.getInsertText().contains( "${1:" ) ) );
		assertNotNull( adds.getFirst().getLabelDetails() );
		assertTrue( completions( file, 4, "            variables.cl" ).stream().anyMatch( c -> c.getLabel().equals( "clear" ) ) );
		assertTrue( completions( file, 5, "            super.si" ).stream().anyMatch( c -> c.getLabel().equals( "size" ) ) );
		assertTrue( completions( file, 6, "            isEm" ).stream().anyMatch( c -> c.getLabel().equals( "isEmpty" ) ) );
		var sizes = completions( file, 3, "            this.si" ).stream().filter( c -> c.getLabel().equals( "size" ) ).toList();
		assertEquals( 1, sizes.size() );
		assertNull( sizes.getFirst().getLabelDetails() );
	}

	@Test
	void javaMembersHaveHoverAndSignatureHelp() throws Exception {
		Path	file	= openClass( "JavaList", """
		                                         class extends="java:java.util.ArrayList" {
		                                             function example() {
		                                                 this.add( "value" );
		                                                 isEmpty();
		                                                 super.clear();
		                                             }
		                                         }
		                                         """ );
		var		hover	= provider.getHoverInfo( file.toUri(), new Position( 2, 18 ) );
		assertNotNull( hover );
		assertTrue( hover.getContents().getRight().getValue().contains( "add" ) );
		var help = provider.getSignatureHelp( file.toUri(), new Position( 2, 23 ) );
		assertNotNull( help );
		assertEquals( 2, help.getSignatures().size() );
		assertNotNull( provider.getHoverInfo( file.toUri(), new Position( 3, 14 ) ) );
		assertNotNull( provider.getSignatureHelp( file.toUri(), new Position( 3, 16 ) ) );
		assertNotNull( provider.getHoverInfo( file.toUri(), new Position( 4, 19 ) ) );
		assertNotNull( provider.getHoverInfo( file.toUri(), new Position( 0, 34 ) ) );
		provider.trackDocumentOpen( file.toUri(), Files.readString( file ).replace( "this.add( \"value\" )", "this.add( 0, \"value\" )" ) );
		var secondParameter = provider.getSignatureHelp( file.toUri(), new Position( 2, 22 ) );
		assertNotNull( secondParameter );
		assertEquals( 1, secondParameter.getActiveParameter() );
		assertEquals( 2, secondParameter.getSignatures().get( secondParameter.getActiveSignature() ).getParameters().size() );
	}

	@Test
	void javaMemberVisibilityAndFields() throws Exception {
		Path file = openClass( "JavaList", """
		                                   class extends="java:java.util.ArrayList" {
		                                       function example() {
		                                           this.removeRange( 0, 1 );
		                                           var list = new JavaList();
		                                           list.clear();
		                                       }
		                                   }
		                                   """ );
		assertTrue( completions( file, 2, "            this.removeR" ).stream().anyMatch( c -> c.getLabel().equals( "removeRange" ) ) );
		Path consumer = openClass( "Consumer", """
		                                       class {
		                                           function example() {
		                                               var list = new JavaList();
		                                               list.clear();
		                                           }
		                                       }
		                                       """ );
		assertTrue( completions( consumer, 3, "            list.removeR" ).stream().noneMatch( c -> c.getLabel().equals( "removeRange" ) ) );
		assertTrue( completions( file, 2, "            this.fastR" ).isEmpty() );
		Path point = openClass( "JavaPoint", """
		                                     class extends="java:java.awt.Point" {
		                                         function example() {
		                                             this.x;
		                                         }
		                                     }
		                                     """ );
		assertTrue( completions( point, 2, "            this.x" ).stream().anyMatch( c -> c.getLabel().equals( "x" ) ) );
	}

	private static boolean initialized;

	public static class InitializationProbe {

		static {
			initialized = true;
		}

		public void example() {
		}
	}

	@Test
	void reflectionDoesNotRunStaticInitializers() {
		String name = "java:" + InitializationProbe.class.getName();
		assertTrue( index.findClassWithContext( name, null ).isPresent() );
		assertFalse( index.getMethodsOfClass( name ).isEmpty() );
		assertFalse( initialized );
	}

	@Test
	void javaInheritanceWorksThroughBoxLangParent() throws Exception {
		openClass( "Base", "class extends=\"java:java.util.ArrayList\" {}" );
		Path file = openClass( "Child", """
		                                class extends="Base" {
		                                    function example() {
		                                        this.isEmpty();
		                                    }
		                                }
		                                """ );
		assertTrue( completions( file, 2, "            this.isEm" ).stream().anyMatch( c -> c.getLabel().equals( "isEmpty" ) ) );
	}

	Path openClass( String name, String code ) throws Exception {
		Path file = tempDir.resolve( name + ".bx" );
		Files.writeString( file, code );
		index.indexFile( file.toUri() );
		provider.trackDocumentOpen( file.toUri(), code );
		return file;
	}

	List<CompletionItem> completions( Path file, int line, String beforeCursor ) throws Exception {
		String		original	= Files.readString( file );
		String[]	lines		= original.split( "\n" );
		lines[ line ] = beforeCursor + "();";
		provider.trackDocumentOpen( file.toUri(), String.join( "\n", lines ) );
		try {
			return provider.getAvailableCompletions( file.toUri(), new CompletionParams(
			    new TextDocumentIdentifier( file.toUri().toString() ), new Position( line, beforeCursor.length() ) ) );
		} finally {
			provider.trackDocumentOpen( file.toUri(), original );
		}
	}
}
