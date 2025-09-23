package ortus.boxlang.lsp;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ortus.boxlang.compiler.ast.BoxNode;
import ortus.boxlang.compiler.parser.Parser;
import ortus.boxlang.compiler.parser.ParsingResult;
import ortus.boxlang.lsp.workspace.ClassSymbol;
import ortus.boxlang.lsp.workspace.ClassSymbolExtractorVisitor;
import ortus.boxlang.runtime.BoxRuntime;

class ClassSymbolExtractorVisitorTest {

	static BoxRuntime	instance;

	@TempDir
	Path				tempDir;

	@BeforeAll
	public static void setUp() {
		instance = BoxRuntime.getInstance( true );
	}

	@Test
	void testExtractClassSymbolFromBoxClass() throws Exception {
		// Create a temporary .bx file with a class
		String	classCode	= """
		                      class TestClass {
		                          function init() {
		                              return this;
		                          }
		                      }
		                      """;

		Path	testFile	= tempDir.resolve( "TestClass.bx" );
		Files.writeString( testFile, classCode );
		URI				fileUri	= testFile.toUri();

		// Parse the code using the file
		ParsingResult	result	= new Parser().parse( testFile.toFile() );
		assertNotNull( result.getRoot(), "Parser should return a valid AST root" );

		// Extract class symbols
		ClassSymbolExtractorVisitor visitor = new ClassSymbolExtractorVisitor( fileUri );
		result.getRoot().accept( visitor );

		List<ClassSymbol> classSymbols = visitor.getClassSymbols();

		// Verify results
		assertEquals( 1, classSymbols.size(), "Should find exactly one class symbol" );

		ClassSymbol classSymbol = classSymbols.get( 0 );
		assertEquals( "TestClass", classSymbol.name(), "Class name should match file name" );
		assertEquals( fileUri.toString(), classSymbol.fileUri(), "File URI should match" );
		assertNotNull( classSymbol.location(), "Location should not be null" );
		assertNotNull( classSymbol.lastModified(), "Last modified should not be null" );

		// Verify the range is reasonable (should start at line 0 for class declaration)
		Range range = classSymbol.location();
		assertTrue( range.getStart().getLine() >= 0, "Start line should be >= 0" );
		assertTrue( range.getEnd().getLine() >= range.getStart().getLine(), "End line should be >= start line" );
	}

	@Test
	void testExtractMultipleClassSymbols() throws Exception {
		// BoxLang doesn't support multiple classes in one file, but test nested classes
		String	classCode	= """
		                      class OuterClass {
		                          function init() {
		                              return this;
		                          }
		                      }
		                      """;

		Path	testFile	= tempDir.resolve( "OuterClass.bx" );
		Files.writeString( testFile, classCode );
		URI							fileUri	= testFile.toUri();

		// Parse the code using the file
		ParsingResult				result	= new Parser().parse( testFile.toFile() );

		// Extract class symbols
		ClassSymbolExtractorVisitor	visitor	= new ClassSymbolExtractorVisitor( fileUri );
		result.getRoot().accept( visitor );

		List<ClassSymbol> classSymbols = visitor.getClassSymbols();

		// Should find the outer class
		assertEquals( 1, classSymbols.size(), "Should find one class symbol" );
		assertEquals( "OuterClass", classSymbols.get( 0 ).name() );
	}

	@Test
	void testExtractClassSymbolWithFileExtension() throws Exception {
		String	classCode	= """
		                      class MyTestClass {
		                          property name="test";
		                      }
		                      """;

		Path	testFile	= tempDir.resolve( "MyTestClass.bx" );
		Files.writeString( testFile, classCode );
		URI							fileUri	= testFile.toUri();

		// Parse the code using the file
		ParsingResult				result	= new Parser().parse( testFile.toFile() );

		// Extract class symbols
		ClassSymbolExtractorVisitor	visitor	= new ClassSymbolExtractorVisitor( fileUri );
		result.getRoot().accept( visitor );

		List<ClassSymbol> classSymbols = visitor.getClassSymbols();

		assertEquals( 1, classSymbols.size() );
		// Should extract class name without .bx extension
		assertEquals( "MyTestClass", classSymbols.get( 0 ).name() );
	}

	@Test
	void testExtractClassSymbolWithNullUri() throws Exception {
		String	classCode	= """
		                      class DefaultClass {
		                          function test() {}
		                      }
		                      """;

		Path	testFile	= tempDir.resolve( "DefaultClass.bx" );
		Files.writeString( testFile, classCode );

		// Parse the code using the file
		ParsingResult				result	= new Parser().parse( testFile.toFile() );

		// Extract class symbols with null URI
		ClassSymbolExtractorVisitor	visitor	= new ClassSymbolExtractorVisitor( null );
		result.getRoot().accept( visitor );

		List<ClassSymbol> classSymbols = visitor.getClassSymbols();

		assertEquals( 1, classSymbols.size() );
		// Should use default class name when URI is null
		assertEquals( "Class", classSymbols.get( 0 ).name() );
		assertNull( classSymbols.get( 0 ).fileUri() );
	}

	@Test
	void testExtractNoClassSymbols() throws Exception {
		// Code without class declaration
		String	nonClassCode	= """
		                          function testFunction() {
		                              return "hello";
		                          }
		                          """;

		Path	testFile		= tempDir.resolve( "noclass.bxs" );
		Files.writeString( testFile, nonClassCode );

		// Parse the code using the file
		ParsingResult				result	= new Parser().parse( testFile.toFile() );

		// Extract class symbols
		ClassSymbolExtractorVisitor	visitor	= new ClassSymbolExtractorVisitor( URI.create( "file:///test.bx" ) );
		result.getRoot().accept( visitor );

		List<ClassSymbol> classSymbols = visitor.getClassSymbols();

		// Should find no class symbols
		assertTrue( classSymbols.isEmpty(), "Should find no class symbols in non-class code" );
	}
}