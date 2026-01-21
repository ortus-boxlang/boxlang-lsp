package ortus.boxlang.lsp.index;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ortus.boxlang.lsp.BaseTest;
import ortus.boxlang.lsp.workspace.index.IndexedClass;
import ortus.boxlang.lsp.workspace.index.IndexedMethod;
import ortus.boxlang.lsp.workspace.index.IndexedProperty;
import ortus.boxlang.lsp.workspace.index.ProjectIndex;
import ortus.boxlang.runtime.BoxRuntime;

class ProjectIndexTest extends BaseTest {

	@TempDir
	Path				tempDir;

	private ProjectIndex	index;

	static BoxRuntime		runtime;

	@BeforeAll
	static void setUpRuntime() {
		runtime = BoxRuntime.getInstance( true );
	}

	@BeforeEach
	void setUp() {
		index = new ProjectIndex();
		index.initialize( tempDir );
	}

	@Test
	void testIndexSimpleClass() throws Exception {
		String classCode = """
		                   class {
		                       property name="firstName" type="string";

		                       public function getName() {
		                           return variables.firstName;
		                       }
		                   }
		                   """;

		Path testFile = createTestFile( "User.bx", classCode );
		index.indexFile( testFile.toUri() );

		// Verify class was indexed
		Optional<IndexedClass> foundClass = index.findClassByName( "User" );
		assertTrue( foundClass.isPresent() );
		assertEquals( "User", foundClass.get().name() );

		// Verify method was indexed
		List<IndexedMethod> methods = index.findMethodsByName( "getName" );
		assertThat( methods ).hasSize( 1 );
		assertEquals( "User", methods.get( 0 ).containingClass() );

		// Verify property was indexed
		Optional<IndexedProperty> property = index.findProperty( "User", "firstName" );
		assertTrue( property.isPresent() );
		assertEquals( "string", property.get().typeHint() );
	}

	@Test
	void testFindClassByFQN() throws Exception {
		// Create nested directory structure
		Path modelsDir = tempDir.resolve( "models" );
		Files.createDirectories( modelsDir );

		String classCode = """
		                   class {
		                       function init() { return this; }
		                   }
		                   """;

		Path testFile = modelsDir.resolve( "User.bx" );
		Files.writeString( testFile, classCode );
		index.indexFile( testFile.toUri() );

		// Find by FQN
		Optional<IndexedClass> foundClass = index.findClassByFQN( "models.User" );
		assertTrue( foundClass.isPresent() );
		assertEquals( "models.User", foundClass.get().fullyQualifiedName() );

		// Find by simple name should also work
		foundClass = index.findClassByName( "User" );
		assertTrue( foundClass.isPresent() );
	}

	@Test
	void testFindAllClassesByName() throws Exception {
		// Create two classes with similar names in different locations
		Path modelsDir = tempDir.resolve( "models" );
		Path servicesDir = tempDir.resolve( "services" );
		Files.createDirectories( modelsDir );
		Files.createDirectories( servicesDir );

		String classCode = """
		                   class {
		                       function init() { return this; }
		                   }
		                   """;

		Path modelUser = modelsDir.resolve( "User.bx" );
		Files.writeString( modelUser, classCode );
		index.indexFile( modelUser.toUri() );

		// Different class but same search matches
		String serviceCode = """
		                     class {
		                         function getUser() { return null; }
		                     }
		                     """;
		Path userService = servicesDir.resolve( "UserService.bx" );
		Files.writeString( userService, serviceCode );
		index.indexFile( userService.toUri() );

		// Search for "User" should find classes containing "User"
		List<IndexedClass> foundClasses = index.findAllClassesByName( "User" );
		assertThat( foundClasses ).hasSize( 1 ); // Only exact match "User"

		foundClasses = index.findAllClassesByName( "UserService" );
		assertThat( foundClasses ).hasSize( 1 );
	}

	@Test
	void testRemoveFile() throws Exception {
		String classCode = """
		                   class {
		                       property name="data";
		                       function getData() { return variables.data; }
		                   }
		                   """;

		Path	testFile	= createTestFile( "TempClass.bx", classCode );
		URI		fileUri		= testFile.toUri();

		index.indexFile( fileUri );

		// Verify indexed
		assertTrue( index.findClassByName( "TempClass" ).isPresent() );
		assertThat( index.findMethodsByName( "getData" ) ).hasSize( 1 );

		// Remove file
		index.removeFile( fileUri );

		// Verify removed
		assertFalse( index.findClassByName( "TempClass" ).isPresent() );
		assertThat( index.findMethodsByName( "getData" ) ).isEmpty();
	}

	@Test
	void testReindexFile() throws Exception {
		String classCode = """
		                   class {
		                       function oldMethod() {}
		                   }
		                   """;

		Path	testFile	= createTestFile( "ChangingClass.bx", classCode );
		URI		fileUri		= testFile.toUri();

		index.indexFile( fileUri );
		assertThat( index.findMethodsByName( "oldMethod" ) ).hasSize( 1 );
		assertThat( index.findMethodsByName( "newMethod" ) ).isEmpty();

		// Update file content
		String newClassCode = """
		                      class {
		                          function newMethod() {}
		                      }
		                      """;
		Files.writeString( testFile, newClassCode );

		// Reindex
		index.reindexFile( fileUri );

		// Verify old method removed, new method added
		assertThat( index.findMethodsByName( "oldMethod" ) ).isEmpty();
		assertThat( index.findMethodsByName( "newMethod" ) ).hasSize( 1 );
	}

	@Test
	void testSearchSymbols() throws Exception {
		String classCode = """
		                   class {
		                       property name="userName";
		                       property name="userEmail";
		                       function getUserName() { return variables.userName; }
		                       function getEmail() { return variables.userEmail; }
		                   }
		                   """;

		Path testFile = createTestFile( "SearchTest.bx", classCode );
		index.indexFile( testFile.toUri() );

		// Search for "user" should find class, properties, and methods containing "user"
		List<Object> results = index.searchSymbols( "user" );
		assertThat( results ).isNotEmpty();

		// Search for "Email" should find property and method
		results = index.searchSymbols( "Email" );
		assertThat( results ).isNotEmpty();
	}

	@Test
	void testGetAllClasses() throws Exception {
		String classCode1 = "class { }";
		String classCode2 = "class { }";

		createTestFile( "Class1.bx", classCode1 );
		createTestFile( "Class2.bx", classCode2 );

		index.indexFile( tempDir.resolve( "Class1.bx" ).toUri() );
		index.indexFile( tempDir.resolve( "Class2.bx" ).toUri() );

		List<IndexedClass> allClasses = index.getAllClasses();
		assertThat( allClasses ).hasSize( 2 );
	}

	@Test
	void testGetAllMethods() throws Exception {
		String classCode = """
		                   class {
		                       function method1() {}
		                       function method2() {}
		                       function method3() {}
		                   }
		                   """;

		Path testFile = createTestFile( "MethodTest.bx", classCode );
		index.indexFile( testFile.toUri() );

		List<IndexedMethod> allMethods = index.getAllMethods();
		assertThat( allMethods ).hasSize( 3 );
	}

	@Test
	void testGetAllProperties() throws Exception {
		String classCode = """
		                   class {
		                       property name="prop1";
		                       property name="prop2";
		                   }
		                   """;

		Path testFile = createTestFile( "PropertyTest.bx", classCode );
		index.indexFile( testFile.toUri() );

		List<IndexedProperty> allProperties = index.getAllProperties();
		assertThat( allProperties ).hasSize( 2 );
	}

	@Test
	void testFindPropertiesOfClass() throws Exception {
		String classCode = """
		                   class {
		                       property name="firstName" type="string";
		                       property name="lastName" type="string";
		                       property name="age" type="numeric";
		                   }
		                   """;

		Path testFile = createTestFile( "Person.bx", classCode );
		index.indexFile( testFile.toUri() );

		List<IndexedProperty> properties = index.findPropertiesOfClass( "Person" );
		assertThat( properties ).hasSize( 3 );

		// Test case insensitivity
		properties = index.findPropertiesOfClass( "person" );
		assertThat( properties ).hasSize( 3 );
	}

	@Test
	void testFindMethodInClass() throws Exception {
		String classCode = """
		                   class {
		                       function specificMethod() {}
		                   }
		                   """;

		Path testFile = createTestFile( "MethodClass.bx", classCode );
		index.indexFile( testFile.toUri() );

		Optional<IndexedMethod> method = index.findMethod( "MethodClass", "specificMethod" );
		assertTrue( method.isPresent() );
		assertEquals( "specificMethod", method.get().name() );
	}

	@Test
	void testClear() throws Exception {
		String classCode = """
		                   class {
		                       property name="data";
		                       function getData() {}
		                   }
		                   """;

		Path testFile = createTestFile( "ClearTest.bx", classCode );
		index.indexFile( testFile.toUri() );

		// Verify indexed
		assertThat( index.getAllClasses() ).hasSize( 1 );
		assertThat( index.getAllMethods() ).hasSize( 1 );
		assertThat( index.getAllProperties() ).hasSize( 1 );

		// Clear
		index.clear();

		// Verify empty
		assertThat( index.getAllClasses() ).isEmpty();
		assertThat( index.getAllMethods() ).isEmpty();
		assertThat( index.getAllProperties() ).isEmpty();
	}

	@Test
	void testInheritanceGraphIntegration() throws Exception {
		// Note: Since BoxLang uses annotation-style inheritance that may not be
		// fully extracted yet, we test the graph directly
		var inheritanceGraph = index.getInheritanceGraph();
		assertNotNull( inheritanceGraph );

		// Graph should be accessible and functional
		inheritanceGraph.addClassRelationship( "Child", "Parent", List.of() );
		assertThat( inheritanceGraph.getAncestors( "Child" ) ).contains( "Parent" );
	}

	@Test
	void testCaseInsensitiveSearch() throws Exception {
		String classCode = """
		                   class {
		                       function MyMethod() {}
		                   }
		                   """;

		Path testFile = createTestFile( "CaseTest.bx", classCode );
		index.indexFile( testFile.toUri() );

		// Search should be case-insensitive
		List<IndexedMethod> methods = index.findMethodsByName( "mymethod" );
		assertThat( methods ).hasSize( 1 );

		methods = index.findMethodsByName( "MYMETHOD" );
		assertThat( methods ).hasSize( 1 );

		methods = index.findMethodsByName( "MyMethod" );
		assertThat( methods ).hasSize( 1 );
	}

	@Test
	void testEmptySearchQueries() {
		// Should handle empty/null gracefully
		assertThat( index.findClassByName( null ).isPresent() ).isFalse();
		assertThat( index.findClassByName( "" ).isPresent() ).isFalse();
		assertThat( index.findMethodsByName( null ) ).isEmpty();
		assertThat( index.findMethodsByName( "" ) ).isEmpty();
		assertThat( index.searchSymbols( null ) ).isEmpty();
		assertThat( index.searchSymbols( "" ) ).isEmpty();
	}

	private Path createTestFile( String fileName, String content ) throws Exception {
		Path testFile = tempDir.resolve( fileName );
		Files.writeString( testFile, content );
		return testFile;
	}
}
