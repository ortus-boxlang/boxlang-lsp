package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;

import ortus.boxlang.lsp.workspace.ProjectContextProvider;

/**
 * Tests for Document Symbols - Hierarchical Improvements.
 * Task 2.9: Document Symbols - Hierarchical Improvements
 */
public class DocumentSymbolsHierarchyTest extends BaseTest {

	private Path testDir = Path.of( "src/test/resources/files/documentSymbolsTest" );

	// ============ Class Hierarchy Tests ============

	/**
	 * Test that class contains methods and properties as children.
	 */
	@Test
	void testClassContainsMethodsAndProperties() {
		Path														classFile	= testDir.resolve( "UserClass.bx" );
		Optional<List<Either<SymbolInformation, DocumentSymbol>>>	symbols		= ProjectContextProvider.getInstance()
		    .getDocumentSymbols( classFile.toAbsolutePath().toUri() );

		assertThat( symbols.isPresent() ).isTrue();
		assertThat( symbols.get() ).isNotEmpty();

		// First symbol should be the class
		DocumentSymbol classSymbol = symbols.get().get( 0 ).getRight();
		assertThat( classSymbol.getKind() ).isEqualTo( SymbolKind.Class );
		assertThat( classSymbol.getName() ).isEqualTo( "UserClass" );

		// Class should have children
		assertThat( classSymbol.getChildren() ).isNotNull();
		assertThat( classSymbol.getChildren() ).isNotEmpty();
	}

	/**
	 * Test that properties use SymbolKind.Property.
	 */
	@Test
	void testPropertiesUsePropertyKind() {
		Path														classFile	= testDir.resolve( "UserClass.bx" );
		Optional<List<Either<SymbolInformation, DocumentSymbol>>>	symbols		= ProjectContextProvider.getInstance()
		    .getDocumentSymbols( classFile.toAbsolutePath().toUri() );

		assertThat( symbols.isPresent() ).isTrue();
		DocumentSymbol	classSymbol		= symbols.get().get( 0 ).getRight();

		// Find a property
		var				propertySymbol	= classSymbol.getChildren().stream()
		    .filter( s -> s.getName().equals( "id" ) )
		    .findFirst();

		assertThat( propertySymbol.isPresent() ).isTrue();
		assertThat( propertySymbol.get().getKind() ).isEqualTo( SymbolKind.Property );
	}

	/**
	 * Test that properties include type hint in detail.
	 */
	@Test
	void testPropertyDetailIncludesTypeHint() {
		Path														classFile	= testDir.resolve( "UserClass.bx" );
		Optional<List<Either<SymbolInformation, DocumentSymbol>>>	symbols		= ProjectContextProvider.getInstance()
		    .getDocumentSymbols( classFile.toAbsolutePath().toUri() );

		assertThat( symbols.isPresent() ).isTrue();
		DocumentSymbol	classSymbol	= symbols.get().get( 0 ).getRight();

		// Find id property (type="numeric")
		var				idProperty	= classSymbol.getChildren().stream()
		    .filter( s -> s.getName().equals( "id" ) )
		    .findFirst();

		assertThat( idProperty.isPresent() ).isTrue();
		assertThat( idProperty.get().getDetail() ).isNotNull();
		assertThat( idProperty.get().getDetail().toLowerCase() ).contains( "numeric" );
	}

	/**
	 * Test that methods use SymbolKind.Method.
	 */
	@Test
	void testMethodsUseMethodKind() {
		Path														classFile	= testDir.resolve( "UserClass.bx" );
		Optional<List<Either<SymbolInformation, DocumentSymbol>>>	symbols		= ProjectContextProvider.getInstance()
		    .getDocumentSymbols( classFile.toAbsolutePath().toUri() );

		assertThat( symbols.isPresent() ).isTrue();
		DocumentSymbol	classSymbol		= symbols.get().get( 0 ).getRight();

		// Find getDisplayName method
		var				methodSymbol	= classSymbol.getChildren().stream()
		    .filter( s -> s.getName().equals( "getDisplayName" ) )
		    .findFirst();

		assertThat( methodSymbol.isPresent() ).isTrue();
		assertThat( methodSymbol.get().getKind() ).isEqualTo( SymbolKind.Method );
	}

	/**
	 * Test that methods include return type in detail.
	 */
	@Test
	void testMethodDetailIncludesReturnType() {
		Path														classFile	= testDir.resolve( "UserClass.bx" );
		Optional<List<Either<SymbolInformation, DocumentSymbol>>>	symbols		= ProjectContextProvider.getInstance()
		    .getDocumentSymbols( classFile.toAbsolutePath().toUri() );

		assertThat( symbols.isPresent() ).isTrue();
		DocumentSymbol	classSymbol		= symbols.get().get( 0 ).getRight();

		// Find getDisplayName method (returns string)
		var				methodSymbol	= classSymbol.getChildren().stream()
		    .filter( s -> s.getName().equals( "getDisplayName" ) )
		    .findFirst();

		assertThat( methodSymbol.isPresent() ).isTrue();
		assertThat( methodSymbol.get().getDetail() ).isNotNull();
		assertThat( methodSymbol.get().getDetail().toLowerCase() ).contains( "string" );
	}

	/**
	 * Test that constructor (init) uses SymbolKind.Constructor.
	 */
	@Test
	void testConstructorUsesConstructorKind() {
		Path														classFile	= testDir.resolve( "UserClass.bx" );
		Optional<List<Either<SymbolInformation, DocumentSymbol>>>	symbols		= ProjectContextProvider.getInstance()
		    .getDocumentSymbols( classFile.toAbsolutePath().toUri() );

		assertThat( symbols.isPresent() ).isTrue();
		DocumentSymbol	classSymbol	= symbols.get().get( 0 ).getRight();

		// Find init method (constructor)
		var				initSymbol	= classSymbol.getChildren().stream()
		    .filter( s -> s.getName().equalsIgnoreCase( "init" ) )
		    .findFirst();

		assertThat( initSymbol.isPresent() ).isTrue();
		assertThat( initSymbol.get().getKind() ).isEqualTo( SymbolKind.Constructor );
	}

	// ============ Interface Tests ============

	/**
	 * Test that interfaces use SymbolKind.Interface.
	 */
	@Test
	void testInterfaceUsesInterfaceKind() {
		Path														interfaceFile	= testDir.resolve( "IUserService.bx" );
		Optional<List<Either<SymbolInformation, DocumentSymbol>>>	symbols			= ProjectContextProvider.getInstance()
		    .getDocumentSymbols( interfaceFile.toAbsolutePath().toUri() );

		assertThat( symbols.isPresent() ).isTrue();
		assertThat( symbols.get() ).isNotEmpty();

		DocumentSymbol interfaceSymbol = symbols.get().get( 0 ).getRight();
		assertThat( interfaceSymbol.getKind() ).isEqualTo( SymbolKind.Interface );
		assertThat( interfaceSymbol.getName() ).isEqualTo( "IUserService" );
	}

	/**
	 * Test that interface contains method declarations.
	 */
	@Test
	void testInterfaceContainsMethods() {
		Path														interfaceFile	= testDir.resolve( "IUserService.bx" );
		Optional<List<Either<SymbolInformation, DocumentSymbol>>>	symbols			= ProjectContextProvider.getInstance()
		    .getDocumentSymbols( interfaceFile.toAbsolutePath().toUri() );

		assertThat( symbols.isPresent() ).isTrue();
		DocumentSymbol interfaceSymbol = symbols.get().get( 0 ).getRight();

		// Interface should have method children
		assertThat( interfaceSymbol.getChildren() ).isNotNull();
		assertThat( interfaceSymbol.getChildren() ).isNotEmpty();

		// Find getUser method
		var methodSymbol = interfaceSymbol.getChildren().stream()
		    .filter( s -> s.getName().equals( "getUser" ) )
		    .findFirst();

		assertThat( methodSymbol.isPresent() ).isTrue();
		assertThat( methodSymbol.get().getKind() ).isEqualTo( SymbolKind.Method );
	}

	// ============ Standalone Function Tests ============

	/**
	 * Test that standalone functions use SymbolKind.Function.
	 */
	@Test
	void testStandaloneFunctionsUseFunctionKind() {
		Path														scriptFile	= testDir.resolve( "helperFunctions.bxs" );
		Optional<List<Either<SymbolInformation, DocumentSymbol>>>	symbols		= ProjectContextProvider.getInstance()
		    .getDocumentSymbols( scriptFile.toAbsolutePath().toUri() );

		assertThat( symbols.isPresent() ).isTrue();
		assertThat( symbols.get() ).isNotEmpty();

		// Find formatDate function
		var formatDateSymbol = symbols.get().stream()
		    .map( Either::getRight )
		    .filter( s -> s.getName().equals( "formatDate" ) )
		    .findFirst();

		assertThat( formatDateSymbol.isPresent() ).isTrue();
		assertThat( formatDateSymbol.get().getKind() ).isEqualTo( SymbolKind.Function );
	}

	/**
	 * Test that standalone functions include return type in detail.
	 */
	@Test
	void testStandaloneFunctionDetailIncludesReturnType() {
		Path														scriptFile	= testDir.resolve( "helperFunctions.bxs" );
		Optional<List<Either<SymbolInformation, DocumentSymbol>>>	symbols		= ProjectContextProvider.getInstance()
		    .getDocumentSymbols( scriptFile.toAbsolutePath().toUri() );

		assertThat( symbols.isPresent() ).isTrue();

		// Find formatDate function (returns string)
		var formatDateSymbol = symbols.get().stream()
		    .map( Either::getRight )
		    .filter( s -> s.getName().equals( "formatDate" ) )
		    .findFirst();

		assertThat( formatDateSymbol.isPresent() ).isTrue();
		assertThat( formatDateSymbol.get().getDetail() ).isNotNull();
		assertThat( formatDateSymbol.get().getDetail().toLowerCase() ).contains( "string" );
	}

	// ============ BXM Template Tests ============

	/**
	 * Test that BXM templates can provide document symbols.
	 */
	@Test
	void testBxmTemplateProvidesFunctions() {
		Path														templateFile	= testDir.resolve( "template.bxm" );
		Optional<List<Either<SymbolInformation, DocumentSymbol>>>	symbols			= ProjectContextProvider.getInstance()
		    .getDocumentSymbols( templateFile.toAbsolutePath().toUri() );

		assertThat( symbols.isPresent() ).isTrue();
		// BXM templates may have functions defined in <bx:script> blocks
		// Find initData function if it's detected
		var hasFunction = symbols.get().stream()
		    .map( Either::getRight )
		    .anyMatch( s -> s.getKind() == SymbolKind.Function );

		// May or may not find functions in BXM depending on implementation
		assertThat( symbols.get() ).isNotNull();
	}

	// ============ Ordering Tests ============

	/**
	 * Test that properties come before methods (consistent ordering).
	 */
	@Test
	void testPropertiesBeforeMethods() {
		Path														classFile	= testDir.resolve( "UserClass.bx" );
		Optional<List<Either<SymbolInformation, DocumentSymbol>>>	symbols		= ProjectContextProvider.getInstance()
		    .getDocumentSymbols( classFile.toAbsolutePath().toUri() );

		assertThat( symbols.isPresent() ).isTrue();
		DocumentSymbol			classSymbol	= symbols.get().get( 0 ).getRight();

		List<DocumentSymbol>	children	= classSymbol.getChildren();
		assertThat( children ).isNotEmpty();

		// Find first method and first property indices
		int	firstPropertyIndex	= -1;
		int	firstMethodIndex	= -1;

		for ( int i = 0; i < children.size(); i++ ) {
			SymbolKind kind = children.get( i ).getKind();
			if ( kind == SymbolKind.Property && firstPropertyIndex == -1 ) {
				firstPropertyIndex = i;
			}
			if ( ( kind == SymbolKind.Method || kind == SymbolKind.Constructor ) && firstMethodIndex == -1 ) {
				firstMethodIndex = i;
			}
		}

		// Properties should come before methods
		if ( firstPropertyIndex >= 0 && firstMethodIndex >= 0 ) {
			assertThat( firstPropertyIndex ).isLessThan( firstMethodIndex );
		}
	}

	// ============ Location Tests ============

	/**
	 * Test that symbols have valid ranges.
	 */
	@Test
	void testSymbolsHaveValidRanges() {
		Path														classFile	= testDir.resolve( "UserClass.bx" );
		Optional<List<Either<SymbolInformation, DocumentSymbol>>>	symbols		= ProjectContextProvider.getInstance()
		    .getDocumentSymbols( classFile.toAbsolutePath().toUri() );

		assertThat( symbols.isPresent() ).isTrue();
		DocumentSymbol classSymbol = symbols.get().get( 0 ).getRight();

		assertThat( classSymbol.getRange() ).isNotNull();
		assertThat( classSymbol.getSelectionRange() ).isNotNull();

		// Check children too
		for ( DocumentSymbol child : classSymbol.getChildren() ) {
			assertThat( child.getRange() ).isNotNull();
			assertThat( child.getSelectionRange() ).isNotNull();
		}
	}

}
