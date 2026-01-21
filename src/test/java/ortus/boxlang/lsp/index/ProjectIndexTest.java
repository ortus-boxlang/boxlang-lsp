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

	// ============ Persistent Cache Tests ============

	@Test
	void testCachePersistence() throws Exception {
		// Create and index a file
		String classCode = """
		                   class {
		                       property name="cached" type="string";
		                       function getCached() { return variables.cached; }
		                   }
		                   """;

		Path testFile = createTestFile( "CacheTest.bx", classCode );
		index.indexFile( testFile.toUri() );

		// Verify it's indexed
		assertTrue( index.findClassByName( "CacheTest" ).isPresent() );
		assertThat( index.findMethodsByName( "getCached" ) ).hasSize( 1 );

		// Save cache
		index.saveCache();

		// Create a new index and initialize it (should load from cache)
		ProjectIndex newIndex = new ProjectIndex();
		newIndex.initialize( tempDir );

		// Verify data was loaded from cache
		assertTrue( newIndex.findClassByName( "CacheTest" ).isPresent() );
		assertThat( newIndex.findMethodsByName( "getCached" ) ).hasSize( 1 );
		Optional<IndexedProperty> prop = newIndex.findProperty( "CacheTest", "cached" );
		assertTrue( prop.isPresent() );
		assertEquals( "string", prop.get().typeHint() );
	}

	@Test
	void testCacheFreshnessValidation() throws Exception {
		// Create and index a file
		String classCode = """
		                   class {
		                       function oldMethod() {}
		                   }
		                   """;

		Path testFile = createTestFile( "FreshnessTest.bx", classCode );
		index.indexFile( testFile.toUri() );

		// Save cache
		index.saveCache();

		// Wait a moment and modify the file
		Thread.sleep( 100 );
		String newClassCode = """
		                      class {
		                          function newMethod() {}
		                      }
		                      """;
		Files.writeString( testFile, newClassCode );

		// Create a new index and initialize it
		ProjectIndex newIndex = new ProjectIndex();
		newIndex.initialize( tempDir );

		// The file should be marked as stale since it was modified after caching
		assertTrue( newIndex.needsReindexing( testFile.toUri() ) );
		assertThat( newIndex.getStaleFiles() ).contains( testFile.toUri().toString() );

		// The old data should have been removed during freshness validation
		// (stale files have their data removed)
		assertFalse( newIndex.findClassByName( "FreshnessTest" ).isPresent() );
	}

	@Test
	void testCacheHandlesDeletedFiles() throws Exception {
		// Create and index a file
		String classCode = "class { }";
		Path testFile = createTestFile( "DeletedFile.bx", classCode );
		index.indexFile( testFile.toUri() );

		// Save cache
		index.saveCache();

		// Delete the file
		Files.delete( testFile );

		// Create a new index and initialize it
		ProjectIndex newIndex = new ProjectIndex();
		newIndex.initialize( tempDir );

		// The file should be marked as stale
		assertTrue( newIndex.needsReindexing( testFile.toUri() ) );

		// The old data should have been removed
		assertFalse( newIndex.findClassByName( "DeletedFile" ).isPresent() );
	}

	@Test
	void testCacheCorruptionHandling() throws Exception {
		// Create and index a file
		String classCode = "class { }";
		Path testFile = createTestFile( "CorruptionTest.bx", classCode );
		index.indexFile( testFile.toUri() );

		// Save cache
		index.saveCache();

		// Corrupt the cache file
		Path cacheFile = ProjectIndex.getDefaultCacheFilePath( tempDir );
		Files.writeString( cacheFile, "not valid json {{{" );

		// Create a new index and initialize it
		ProjectIndex newIndex = new ProjectIndex();
		newIndex.initialize( tempDir );

		// The cache should be marked as corrupted
		assertTrue( newIndex.isCacheCorrupted() );

		// All files should need re-indexing
		assertTrue( newIndex.needsReindexing( testFile.toUri() ) );

		// Index should be empty (cleared due to corruption)
		assertThat( newIndex.getAllClasses() ).isEmpty();
	}

	@Test
	void testNeedsReindexingForNewFile() throws Exception {
		// Index should need re-indexing for files not in cache
		Path newFile = tempDir.resolve( "NewFile.bx" );
		Files.writeString( newFile, "class { }" );

		assertTrue( index.needsReindexing( newFile.toUri() ) );
	}

	@Test
	void testCacheWithMultipleFiles() throws Exception {
		// Create and index multiple files
		String class1 = "class { function method1() {} }";
		String class2 = "class { function method2() {} }";
		String class3 = "class { function method3() {} }";

		Path file1 = createTestFile( "Multi1.bx", class1 );
		Path file2 = createTestFile( "Multi2.bx", class2 );
		Path file3 = createTestFile( "Multi3.bx", class3 );

		index.indexFile( file1.toUri() );
		index.indexFile( file2.toUri() );
		index.indexFile( file3.toUri() );

		// Save cache
		index.saveCache();

		// Modify only one file
		Thread.sleep( 100 );
		Files.writeString( file2, "class { function updatedMethod() {} }" );

		// Create a new index and initialize it
		ProjectIndex newIndex = new ProjectIndex();
		newIndex.initialize( tempDir );

		// Only the modified file should need re-indexing
		assertFalse( newIndex.needsReindexing( file1.toUri() ) );
		assertTrue( newIndex.needsReindexing( file2.toUri() ) );
		assertFalse( newIndex.needsReindexing( file3.toUri() ) );

		// Unmodified files should still have their data
		assertTrue( newIndex.findClassByName( "Multi1" ).isPresent() );
		assertThat( newIndex.findMethodsByName( "method1" ) ).hasSize( 1 );
		assertTrue( newIndex.findClassByName( "Multi3" ).isPresent() );
		assertThat( newIndex.findMethodsByName( "method3" ) ).hasSize( 1 );

		// Modified file should have had its data removed (stale)
		assertFalse( newIndex.findClassByName( "Multi2" ).isPresent() );
		assertThat( newIndex.findMethodsByName( "method2" ) ).isEmpty();
	}

	// ============ Index Query API Tests (Task 1.3) ============

	@Test
	void testFindFunction() throws Exception {
		// Create a .bxs script file with a standalone function (no class)
		String scriptCode = """
		                    function greet( name ) {
		                        return "Hello, " & name;
		                    }

		                    function farewell( name ) {
		                        return "Goodbye, " & name;
		                    }
		                    """;

		Path testFile = createTestFile( "utilities.bxs", scriptCode );
		index.indexFile( testFile.toUri() );

		// Find the standalone function
		Optional<IndexedMethod> greetFunc = index.findFunction( "greet" );
		assertTrue( greetFunc.isPresent() );
		assertEquals( "greet", greetFunc.get().name() );
		// Standalone functions have null containingClass
		assertEquals( null, greetFunc.get().containingClass() );

		// Should also find the second function
		Optional<IndexedMethod> farewellFunc = index.findFunction( "farewell" );
		assertTrue( farewellFunc.isPresent() );
	}

	@Test
	void testFindFunctionCaseInsensitive() throws Exception {
		String scriptCode = """
		                    function MyFunction() {
		                        return true;
		                    }
		                    """;

		Path testFile = createTestFile( "test.bxs", scriptCode );
		index.indexFile( testFile.toUri() );

		// Case insensitive search
		assertTrue( index.findFunction( "myfunction" ).isPresent() );
		assertTrue( index.findFunction( "MYFUNCTION" ).isPresent() );
		assertTrue( index.findFunction( "MyFunction" ).isPresent() );
	}

	@Test
	void testFindFunctionDoesNotFindClassMethods() throws Exception {
		// Create a class with a method
		String classCode = """
		                   class {
		                       function classMethod() {}
		                   }
		                   """;

		Path testFile = createTestFile( "MyClass.bx", classCode );
		index.indexFile( testFile.toUri() );

		// findFunction should NOT find class methods (only standalone functions)
		Optional<IndexedMethod> method = index.findFunction( "classMethod" );
		assertFalse( method.isPresent() );

		// But findMethodsByName should find it
		List<IndexedMethod> methods = index.findMethodsByName( "classMethod" );
		assertThat( methods ).hasSize( 1 );
	}

	@Test
	void testFindFunctionsInFile() throws Exception {
		String scriptCode = """
		                    function func1() {}
		                    function func2() {}
		                    function func3() {}
		                    """;

		Path testFile = createTestFile( "multi_func.bxs", scriptCode );
		index.indexFile( testFile.toUri() );

		List<IndexedMethod> functions = index.findFunctionsInFile( testFile.toUri().toString() );
		assertThat( functions ).hasSize( 3 );

		// Verify all are standalone functions
		for ( IndexedMethod func : functions ) {
			assertEquals( null, func.containingClass() );
		}
	}

	@Test
	void testFindFunctionsInFileExcludesClassMethods() throws Exception {
		// Create a class file with methods
		String classCode = """
		                   class {
		                       function method1() {}
		                       function method2() {}
		                   }
		                   """;

		Path testFile = createTestFile( "ClassFile.bx", classCode );
		index.indexFile( testFile.toUri() );

		// findFunctionsInFile should return empty for class files (only returns standalone functions)
		List<IndexedMethod> functions = index.findFunctionsInFile( testFile.toUri().toString() );
		assertThat( functions ).isEmpty();
	}

	@Test
	void testFindOverrides() throws Exception {
		// Set up inheritance relationship in the graph
		var inheritanceGraph = index.getInheritanceGraph();
		inheritanceGraph.addClassRelationship( "ChildClass", "ParentClass", List.of() );

		// Create parent class with a method
		String parentCode = """
		                    class {
		                        function baseMethod() {
		                            return "parent";
		                        }
		                    }
		                    """;
		Path parentFile = createTestFile( "ParentClass.bx", parentCode );
		index.indexFile( parentFile.toUri() );

		// Create child class that overrides the method
		String childCode = """
		                   class {
		                       function baseMethod() {
		                           return "child";
		                       }
		                   }
		                   """;
		Path childFile = createTestFile( "ChildClass.bx", childCode );
		index.indexFile( childFile.toUri() );

		// Find overrides of baseMethod in ParentClass
		List<IndexedMethod> overrides = index.findOverrides( "ParentClass", "baseMethod" );
		assertThat( overrides ).hasSize( 1 );
		assertEquals( "ChildClass", overrides.get( 0 ).containingClass() );
	}

	@Test
	void testFindOverridesDeepHierarchy() throws Exception {
		// Set up a deeper inheritance chain: GrandChild -> Child -> Parent
		var inheritanceGraph = index.getInheritanceGraph();
		inheritanceGraph.addClassRelationship( "Child", "Parent", List.of() );
		inheritanceGraph.addClassRelationship( "GrandChild", "Child", List.of() );

		// Create all three classes with the same method
		Path parentFile = createTestFile( "Parent.bx", "class { function myMethod() {} }" );
		Path childFile = createTestFile( "Child.bx", "class { function myMethod() {} }" );
		Path grandChildFile = createTestFile( "GrandChild.bx", "class { function myMethod() {} }" );

		index.indexFile( parentFile.toUri() );
		index.indexFile( childFile.toUri() );
		index.indexFile( grandChildFile.toUri() );

		// Find all overrides of Parent.myMethod
		List<IndexedMethod> overrides = index.findOverrides( "Parent", "myMethod" );
		assertThat( overrides ).hasSize( 2 );

		// Both Child and GrandChild should be in the results
		List<String> overrideClasses = overrides.stream().map( IndexedMethod::containingClass ).toList();
		assertThat( overrideClasses ).containsExactly( "Child", "GrandChild" );
	}

	@Test
	void testFindOverridesNoOverrides() throws Exception {
		// Create a class with a method that has no overrides
		String classCode = "class { function uniqueMethod() {} }";
		Path testFile = createTestFile( "UniqueClass.bx", classCode );
		index.indexFile( testFile.toUri() );

		List<IndexedMethod> overrides = index.findOverrides( "UniqueClass", "uniqueMethod" );
		assertThat( overrides ).isEmpty();
	}

	@Test
	void testGetAllFunctions() throws Exception {
		// Create both class methods and standalone functions
		String classCode = "class { function classMethod() {} }";
		String scriptCode = """
		                    function standaloneFunc1() {}
		                    function standaloneFunc2() {}
		                    """;

		Path classFile = createTestFile( "MyClass.bx", classCode );
		Path scriptFile = createTestFile( "helpers.bxs", scriptCode );

		index.indexFile( classFile.toUri() );
		index.indexFile( scriptFile.toUri() );

		// getAllFunctions should only return standalone functions
		List<IndexedMethod> functions = index.getAllFunctions();
		assertThat( functions ).hasSize( 2 );

		// Verify they are all standalone (null containingClass)
		for ( IndexedMethod func : functions ) {
			assertEquals( null, func.containingClass() );
		}
	}

	@Test
	void testGetMethodsOfClass() throws Exception {
		String classCode = """
		                   class {
		                       function method1() {}
		                       function method2() {}
		                       function method3() {}
		                   }
		                   """;

		Path testFile = createTestFile( "MethodsClass.bx", classCode );
		index.indexFile( testFile.toUri() );

		List<IndexedMethod> methods = index.getMethodsOfClass( "MethodsClass" );
		assertThat( methods ).hasSize( 3 );

		// Verify all methods belong to the class
		for ( IndexedMethod method : methods ) {
			assertEquals( "MethodsClass", method.containingClass() );
		}
	}

	@Test
	void testGetMethodsOfClassCaseInsensitive() throws Exception {
		String classCode = "class { function someMethod() {} }";
		Path testFile = createTestFile( "CasedClass.bx", classCode );
		index.indexFile( testFile.toUri() );

		// Case insensitive lookup
		assertThat( index.getMethodsOfClass( "casedclass" ) ).hasSize( 1 );
		assertThat( index.getMethodsOfClass( "CASEDCLASS" ) ).hasSize( 1 );
		assertThat( index.getMethodsOfClass( "CasedClass" ) ).hasSize( 1 );
	}

	@Test
	void testGetIndexedFiles() throws Exception {
		String code1 = "class { }";
		String code2 = "class { }";
		String code3 = "function test() {}";

		Path file1 = createTestFile( "File1.bx", code1 );
		Path file2 = createTestFile( "File2.bx", code2 );
		Path file3 = createTestFile( "script.bxs", code3 );

		index.indexFile( file1.toUri() );
		index.indexFile( file2.toUri() );
		index.indexFile( file3.toUri() );

		List<String> indexedFiles = index.getIndexedFiles();
		assertThat( indexedFiles ).hasSize( 3 );
		assertThat( indexedFiles ).contains( file1.toUri().toString() );
		assertThat( indexedFiles ).contains( file2.toUri().toString() );
		assertThat( indexedFiles ).contains( file3.toUri().toString() );
	}

	@Test
	void testGetFilesInDirectory() throws Exception {
		// Create nested directories
		Path subDir = tempDir.resolve( "subdir" );
		Files.createDirectories( subDir );

		String code = "class { }";
		Path file1 = createTestFile( "Root1.bx", code );
		Path file2 = subDir.resolve( "Sub1.bx" );
		Path file3 = subDir.resolve( "Sub2.bx" );
		Files.writeString( file2, code );
		Files.writeString( file3, code );

		index.indexFile( file1.toUri() );
		index.indexFile( file2.toUri() );
		index.indexFile( file3.toUri() );

		// Get files in subdir only
		List<String> subDirFiles = index.getFilesInDirectory( subDir.toString() );
		assertThat( subDirFiles ).hasSize( 2 );
		assertThat( subDirFiles ).contains( file2.toUri().toString() );
		assertThat( subDirFiles ).contains( file3.toUri().toString() );
		assertThat( subDirFiles ).doesNotContain( file1.toUri().toString() );
	}

	@Test
	void testGetFilesDependingOnPlaceholder() throws Exception {
		// Dependency tracking is not implemented yet, should return empty list
		List<String> dependents = index.getFilesDependingOn( "any/path/file.bx" );
		assertThat( dependents ).isEmpty();
	}

	@Test
	void testGetDependenciesOfPlaceholder() throws Exception {
		// Dependency tracking is not implemented yet, should return empty list
		List<String> dependencies = index.getDependenciesOf( "any/path/file.bx" );
		assertThat( dependencies ).isEmpty();
	}

	@Test
	void testFindFunctionEmptyInput() {
		// Should handle null/empty gracefully
		assertFalse( index.findFunction( null ).isPresent() );
		assertFalse( index.findFunction( "" ).isPresent() );
	}

	@Test
	void testFindFunctionsInFileEmptyInput() {
		// Should handle null/empty gracefully
		assertThat( index.findFunctionsInFile( null ) ).isEmpty();
		assertThat( index.findFunctionsInFile( "" ) ).isEmpty();
	}

	@Test
	void testFindOverridesEmptyInput() {
		// Should handle null/empty gracefully
		assertThat( index.findOverrides( null, "method" ) ).isEmpty();
		assertThat( index.findOverrides( "class", null ) ).isEmpty();
		assertThat( index.findOverrides( "", "method" ) ).isEmpty();
		assertThat( index.findOverrides( "class", "" ) ).isEmpty();
	}

	@Test
	void testGetMethodsOfClassEmptyInput() {
		// Should handle null/empty gracefully
		assertThat( index.getMethodsOfClass( null ) ).isEmpty();
		assertThat( index.getMethodsOfClass( "" ) ).isEmpty();
	}

	@Test
	void testGetFilesInDirectoryEmptyInput() {
		// Should handle null/empty gracefully
		assertThat( index.getFilesInDirectory( null ) ).isEmpty();
		assertThat( index.getFilesInDirectory( "" ) ).isEmpty();
	}

	private Path createTestFile( String fileName, String content ) throws Exception {
		Path testFile = tempDir.resolve( fileName );
		Files.writeString( testFile, content );
		return testFile;
	}
}
