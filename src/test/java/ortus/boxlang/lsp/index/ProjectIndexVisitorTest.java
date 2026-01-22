package ortus.boxlang.lsp.index;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ortus.boxlang.compiler.parser.Parser;
import ortus.boxlang.compiler.parser.ParsingResult;
import ortus.boxlang.lsp.BaseTest;
import ortus.boxlang.lsp.workspace.index.IndexedClass;
import ortus.boxlang.lsp.workspace.index.IndexedMethod;
import ortus.boxlang.lsp.workspace.index.IndexedParameter;
import ortus.boxlang.lsp.workspace.index.IndexedProperty;
import ortus.boxlang.lsp.workspace.index.ProjectIndexVisitor;
import ortus.boxlang.runtime.BoxRuntime;

class ProjectIndexVisitorTest extends BaseTest {

	@TempDir
	Path			tempDir;

	static BoxRuntime	runtime;

	@BeforeAll
	static void setUpRuntime() {
		runtime = BoxRuntime.getInstance( true );
	}

	@Test
	void testExtractSimpleClass() throws Exception {
		String classCode = """
		                   class {
		                       function init() {
		                           return this;
		                       }
		                   }
		                   """;

		Path	testFile	= tempDir.resolve( "SimpleClass.bx" );
		Files.writeString( testFile, classCode );
		URI		fileUri		= testFile.toUri();

		ProjectIndexVisitor visitor = parseAndVisit( fileUri, tempDir );

		List<IndexedClass> classes = visitor.getIndexedClasses();
		assertThat( classes ).hasSize( 1 );

		IndexedClass indexedClass = classes.get( 0 );
		assertEquals( "SimpleClass", indexedClass.name() );
		assertFalse( indexedClass.isInterface() );
		assertNotNull( indexedClass.location() );
	}

	@Test
	void testExtractClassWithMethods() throws Exception {
		String classCode = """
		                   class {
		                       public function doSomething() {
		                           return "hello";
		                       }

		                       private function helperMethod(required string name) {
		                           return name;
		                       }
		                   }
		                   """;

		Path	testFile	= tempDir.resolve( "ClassWithMethods.bx" );
		Files.writeString( testFile, classCode );
		URI		fileUri		= testFile.toUri();

		ProjectIndexVisitor visitor = parseAndVisit( fileUri, tempDir );

		List<IndexedMethod> methods = visitor.getIndexedMethods();
		assertThat( methods ).hasSize( 2 );

		// Check first method
		IndexedMethod doSomething = methods.stream()
		    .filter( m -> m.name().equals( "doSomething" ) )
		    .findFirst()
		    .orElse( null );
		assertNotNull( doSomething );
		assertEquals( "ClassWithMethods", doSomething.containingClass() );

		// Check second method with parameter
		IndexedMethod helperMethod = methods.stream()
		    .filter( m -> m.name().equals( "helperMethod" ) )
		    .findFirst()
		    .orElse( null );
		assertNotNull( helperMethod );
		assertThat( helperMethod.parameters() ).hasSize( 1 );

		IndexedParameter param = helperMethod.parameters().get( 0 );
		assertEquals( "name", param.name() );
		assertEquals( "string", param.typeHint().toLowerCase() );
	}

	@Test
	void testExtractClassWithProperties() throws Exception {
		String classCode = """
		                   class {
		                       property name="firstName" type="string";
		                       property name="lastName" type="string" default="Smith";
		                       property name="age" type="numeric";
		                   }
		                   """;

		Path	testFile	= tempDir.resolve( "ClassWithProperties.bx" );
		Files.writeString( testFile, classCode );
		URI		fileUri		= testFile.toUri();

		ProjectIndexVisitor visitor = parseAndVisit( fileUri, tempDir );

		List<IndexedProperty> properties = visitor.getIndexedProperties();
		assertThat( properties ).hasSize( 3 );

		// Check firstName property
		IndexedProperty firstName = properties.stream()
		    .filter( p -> p.name().equals( "firstName" ) )
		    .findFirst()
		    .orElse( null );
		assertNotNull( firstName );
		assertEquals( "string", firstName.typeHint() );
		assertEquals( "ClassWithProperties", firstName.containingClass() );

		// Check lastName property with default
		IndexedProperty lastName = properties.stream()
		    .filter( p -> p.name().equals( "lastName" ) )
		    .findFirst()
		    .orElse( null );
		assertNotNull( lastName );
		assertEquals( "Smith", lastName.defaultValue() );
	}

	@Test
	void testExtractMethodWithMultipleParameters() throws Exception {
		String classCode = """
		                   class {
		                       public function createUser(
		                           required string username,
		                           string email,
		                           numeric age
		                       ) {
		                           return {};
		                       }
		                   }
		                   """;

		Path	testFile	= tempDir.resolve( "MultiParamClass.bx" );
		Files.writeString( testFile, classCode );
		URI		fileUri		= testFile.toUri();

		ProjectIndexVisitor visitor = parseAndVisit( fileUri, tempDir );

		List<IndexedMethod> methods = visitor.getIndexedMethods();
		assertThat( methods ).hasSize( 1 );

		IndexedMethod createUser = methods.get( 0 );
		assertEquals( "createUser", createUser.name() );

		List<IndexedParameter> params = createUser.parameters();
		assertThat( params ).hasSize( 3 );

		// Check first parameter
		IndexedParameter username = params.stream()
		    .filter( p -> p.name().equals( "username" ) )
		    .findFirst()
		    .orElse( null );
		assertNotNull( username );
		// Note: required extraction depends on how BoxLang represents this in the AST
		// The important thing is that the parameter was extracted with correct name and type
		assertEquals( "string", username.typeHint().toLowerCase() );
	}

	@Test
	void testExtractMethodWithReturnType() throws Exception {
		String classCode = """
		                   class {
		                       public string function getName() {
		                           return "test";
		                       }

		                       public numeric function getAge() {
		                           return 25;
		                       }

		                       public function noReturnType() {
		                           return null;
		                       }
		                   }
		                   """;

		Path	testFile	= tempDir.resolve( "ReturnTypeClass.bx" );
		Files.writeString( testFile, classCode );
		URI		fileUri		= testFile.toUri();

		ProjectIndexVisitor visitor = parseAndVisit( fileUri, tempDir );

		List<IndexedMethod> methods = visitor.getIndexedMethods();
		assertThat( methods ).hasSize( 3 );

		IndexedMethod getName = methods.stream()
		    .filter( m -> m.name().equals( "getName" ) )
		    .findFirst()
		    .orElse( null );
		assertNotNull( getName );
		assertEquals( "string", getName.returnTypeHint().toLowerCase() );

		IndexedMethod getAge = methods.stream()
		    .filter( m -> m.name().equals( "getAge" ) )
		    .findFirst()
		    .orElse( null );
		assertNotNull( getAge );
		assertEquals( "numeric", getAge.returnTypeHint().toLowerCase() );

		IndexedMethod noReturnType = methods.stream()
		    .filter( m -> m.name().equals( "noReturnType" ) )
		    .findFirst()
		    .orElse( null );
		assertNotNull( noReturnType );
		assertEquals( "any", noReturnType.returnTypeHint().toLowerCase() );
	}

	@Test
	void testComputeFullyQualifiedName() throws Exception {
		// Create a nested directory structure
		Path modelsDir = tempDir.resolve( "models" );
		Files.createDirectories( modelsDir );

		String classCode = """
		                   class {
		                       function init() {
		                           return this;
		                       }
		                   }
		                   """;

		Path	testFile	= modelsDir.resolve( "User.bx" );
		Files.writeString( testFile, classCode );
		URI		fileUri		= testFile.toUri();

		ProjectIndexVisitor visitor = parseAndVisit( fileUri, tempDir );

		List<IndexedClass> classes = visitor.getIndexedClasses();
		assertThat( classes ).hasSize( 1 );

		IndexedClass indexedClass = classes.get( 0 );
		assertEquals( "User", indexedClass.name() );
		assertEquals( "models.User", indexedClass.fullyQualifiedName() );
	}

	@Test
	void testExtractPropertyWithShorthandName() throws Exception {
		String classCode = """
		                   class {
		                       property firstName;
		                       property lastName;
		                   }
		                   """;

		Path	testFile	= tempDir.resolve( "ShorthandProps.bx" );
		Files.writeString( testFile, classCode );
		URI		fileUri		= testFile.toUri();

		ProjectIndexVisitor visitor = parseAndVisit( fileUri, tempDir );

		List<IndexedProperty> properties = visitor.getIndexedProperties();
		assertThat( properties ).hasSize( 2 );

		assertThat( properties.stream().map( IndexedProperty::name ).toList() )
		    .containsExactly( "firstName", "lastName" );
	}

	@Test
	void testMethodKeyGeneration() throws Exception {
		String classCode = """
		                   class {
		                       function myMethod() {}
		                   }
		                   """;

		Path	testFile	= tempDir.resolve( "KeyTestClass.bx" );
		Files.writeString( testFile, classCode );
		URI		fileUri		= testFile.toUri();

		ProjectIndexVisitor visitor = parseAndVisit( fileUri, tempDir );

		List<IndexedMethod> methods = visitor.getIndexedMethods();
		assertThat( methods ).hasSize( 1 );

		IndexedMethod method = methods.get( 0 );
		// Keys are lowercase for case-insensitive lookup
		assertEquals( "keytestclass.mymethod", method.getKey() );
	}

	private ProjectIndexVisitor parseAndVisit( URI fileUri, Path workspaceRoot ) throws Exception {
		Parser			parser	= new Parser();
		ParsingResult	result	= parser.parse( Path.of( fileUri ).toFile() );

		assertNotNull( result, "Parsing should succeed" );
		assertNotNull( result.getRoot(), "AST root should not be null" );

		ProjectIndexVisitor visitor = new ProjectIndexVisitor( fileUri, workspaceRoot );
		result.getRoot().accept( visitor );

		return visitor;
	}
}
