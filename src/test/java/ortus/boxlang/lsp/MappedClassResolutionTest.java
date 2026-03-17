package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ortus.boxlang.lsp.lint.rules.InvalidExtendsRule;
import ortus.boxlang.lsp.lint.rules.InvalidImplementsRule;
import ortus.boxlang.lsp.workspace.ProjectAppContext;
import ortus.boxlang.lsp.workspace.ProjectContextProvider;
import ortus.boxlang.lsp.workspace.index.ProjectIndex;

public class MappedClassResolutionTest extends BaseTest {

	@TempDir
	Path							workspaceRoot;

	@TempDir
	Path							externalRoot;

	private ProjectContextProvider	provider;
	private ProjectIndex			index;
	private Path					mappedRoot;

	@BeforeEach
	void setUp() throws Exception {
		provider	= ProjectContextProvider.getInstance();
		index		= new ProjectIndex();
		index.initialize( workspaceRoot );
		provider.setIndex( index );
		provider.setWorkspaceFolders( List.of( new WorkspaceFolder( workspaceRoot.toUri().toString(), "workspace" ) ) );

		mappedRoot = externalRoot.resolve( "mapped-api" );
		Files.createDirectories( mappedRoot );
		provider.setAppContext( new ProjectAppContext(
		    workspaceRoot,
		    Map.of( "api", mappedRoot ),
		    List.of(),
		    "mapped-test-hash"
		) );
	}

	@Test
	void testMappedExtendsAndImplementsDoNotEmitInvalidDiagnostics() throws Exception {
		Path baseHandler = mappedRoot.resolve( "handlers/BaseHandler.bx" );
		Files.createDirectories( baseHandler.getParent() );
		Files.writeString(
		    baseHandler,
		    """
		    class {
		        function init() { return this; }
		    }
		    """
		);

		Path baseContract = mappedRoot.resolve( "contracts/BaseContract.bx" );
		Files.createDirectories( baseContract.getParent() );
		Files.writeString(
		    baseContract,
		    """
		    interface {
		        function execute();
		    }
		    """
		);

		Path child = workspaceRoot.resolve( "ChildHandler.bx" );
		Files.writeString(
		    child,
		    """
		    class extends="api.handlers.BaseHandler" implements="api.contracts.BaseContract" {
		        function execute() { return true; }
		    }
		    """
		);

		List<Diagnostic>	diagnostics				= provider.getFileDiagnostics( child.toUri() );

		boolean				hasInvalidExtends		= diagnostics.stream().anyMatch( this::isInvalidExtendsDiagnostic );
		boolean				hasInvalidImplements	= diagnostics.stream().anyMatch( this::isInvalidImplementsDiagnostic );

		assertThat( hasInvalidExtends ).isFalse();
		assertThat( hasInvalidImplements ).isFalse();
	}

	@Test
	void testUnresolvedMappingStillEmitsInvalidExtends() throws Exception {
		provider.setAppContext( new ProjectAppContext( workspaceRoot, Map.of(), List.of(), "no-mappings" ) );

		Path child = workspaceRoot.resolve( "MissingMappedParent.bx" );
		Files.writeString(
		    child,
		    """
		    class extends="missing.handlers.DoesNotExist" {
		        function init() { return this; }
		    }
		    """
		);

		List<Diagnostic>	diagnostics			= provider.getFileDiagnostics( child.toUri() );
		boolean				hasInvalidExtends	= diagnostics.stream().anyMatch( this::isInvalidExtendsDiagnostic );

		assertThat( hasInvalidExtends ).isTrue();
	}

	@Test
	void testDefinitionOnMappedClassOutsideWorkspaceLazyIndexesTarget() throws Exception {
		Path mappedClass = mappedRoot.resolve( "handlers/BaseHandler.bx" );
		Files.createDirectories( mappedClass.getParent() );
		Files.writeString(
		    mappedClass,
		    """
		    class {
		        function init() { return this; }
		    }
		    """
		);

		assertThat( index.findClassByName( "BaseHandler" ) ).isEmpty();

		Path	consumer	= workspaceRoot.resolve( "Consumer.bx" );
		String	source		= """
		                      class {
		                          function run() {
		                              var handler = new api.handlers.BaseHandler();
		                              return handler;
		                          }
		                      }
		                      """;
		Files.writeString( consumer, source );

		BoxLangTextDocumentService svc = new BoxLangTextDocumentService();
		svc.didOpen( new DidOpenTextDocumentParams(
		    new TextDocumentItem( consumer.toUri().toString(), "boxlang", 1, source )
		) );

		int					tokenOffset	= source.indexOf( "BaseHandler" );
		Position			position	= positionFromOffset( source, tokenOffset + 1 );

		DefinitionParams	params		= new DefinitionParams();
		params.setTextDocument( new TextDocumentIdentifier( consumer.toUri().toString() ) );
		params.setPosition( position );

		var result = svc.definition( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.isLeft() ).isTrue();
		assertThat( result.getLeft() ).isNotEmpty();
		assertThat( result.getLeft().getFirst().getUri() ).isEqualTo( mappedClass.toUri().toString() );

		var indexedClass = index.findClassByName( "BaseHandler" );
		assertThat( indexedClass ).isPresent();
		assertThat( indexedClass.get().fileUri() ).isEqualTo( mappedClass.toUri().toString() );
	}

	@Test
	void testDefinitionOnMappedCreateObjectClassArgument() throws Exception {
		Path mappedClass = mappedRoot.resolve( "handlers/BaseHandler.bx" );
		Files.createDirectories( mappedClass.getParent() );
		Files.writeString(
		    mappedClass,
		    """
		    class {
		        function init() { return this; }
		    }
		    """
		);

		Path	consumer	= workspaceRoot.resolve( "CreateObjectConsumer.bx" );
		String	source		= """
		                      class {
		                          function build() {
		                              return createObject( "component", "api.handlers.BaseHandler" );
		                          }
		                      }
		                      """;
		Files.writeString( consumer, source );

		BoxLangTextDocumentService svc = new BoxLangTextDocumentService();
		svc.didOpen( new DidOpenTextDocumentParams(
		    new TextDocumentItem( consumer.toUri().toString(), "boxlang", 1, source )
		) );

		int					tokenOffset	= source.indexOf( "api.handlers.BaseHandler" );
		Position			position	= positionFromOffset( source, tokenOffset + 2 );

		DefinitionParams	params		= new DefinitionParams();
		params.setTextDocument( new TextDocumentIdentifier( consumer.toUri().toString() ) );
		params.setPosition( position );

		var result = svc.definition( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.isLeft() ).isTrue();
		assertThat( result.getLeft() ).isNotEmpty();
		assertThat( result.getLeft().getFirst().getUri() ).isEqualTo( mappedClass.toUri().toString() );
	}

	@Test
	void testDefinitionOnMappedExtendsAnnotationKey() throws Exception {
		Path mappedClass = mappedRoot.resolve( "handlers/BaseSpec.bx" );
		Files.createDirectories( mappedClass.getParent() );
		Files.writeString(
		    mappedClass,
		    """
		    class {
		        function init() { return this; }
		    }
		    """
		);

		Path	consumer	= workspaceRoot.resolve( "ExtendsConsumer.bx" );
		String	source		= """
		                      class extends="api.handlers.BaseSpec" {
		                      }
		                      """;
		Files.writeString( consumer, source );

		BoxLangTextDocumentService svc = new BoxLangTextDocumentService();
		svc.didOpen( new DidOpenTextDocumentParams(
		    new TextDocumentItem( consumer.toUri().toString(), "boxlang", 1, source )
		) );

		int					tokenOffset	= source.indexOf( "extends" );
		Position			position	= positionFromOffset( source, tokenOffset + 1 );

		DefinitionParams	params		= new DefinitionParams();
		params.setTextDocument( new TextDocumentIdentifier( consumer.toUri().toString() ) );
		params.setPosition( position );

		var result = svc.definition( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.isLeft() ).isTrue();
		assertThat( result.getLeft() ).isNotEmpty();
		assertThat( result.getLeft().getFirst().getUri() ).isEqualTo( mappedClass.toUri().toString() );
	}

	@Test
	void testDefinitionOnMappedCfcExtendsAnnotationKey() throws Exception {
		Path mappedClass = mappedRoot.resolve( "system/BaseSpec.cfc" );
		Files.createDirectories( mappedClass.getParent() );
		Files.writeString(
		    mappedClass,
		    """
		    component {
		        function beforeAll() {}
		    }
		    """
		);

		provider.setAppContext( new ProjectAppContext(
		    workspaceRoot,
		    Map.of( "testbox", mappedRoot ),
		    List.of(),
		    "mapped-cfc-extends"
		) );

		Path	consumer	= workspaceRoot.resolve( "QueryUtilsSpec.cfc" );
		String	source		= """
		                      component extends="testbox.system.BaseSpec" {
		                      }
		                      """;
		Files.writeString( consumer, source );

		BoxLangTextDocumentService svc = new BoxLangTextDocumentService();
		svc.didOpen( new DidOpenTextDocumentParams(
		    new TextDocumentItem( consumer.toUri().toString(), "boxlang", 1, source )
		) );

		int					tokenOffset	= source.indexOf( "extends" );
		Position			position	= positionFromOffset( source, tokenOffset + 1 );

		DefinitionParams	params		= new DefinitionParams();
		params.setTextDocument( new TextDocumentIdentifier( consumer.toUri().toString() ) );
		params.setPosition( position );

		var result = svc.definition( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.isLeft() ).isTrue();
		assertThat( result.getLeft() ).isNotEmpty();
		assertThat( result.getLeft().getFirst().getUri() ).isEqualTo( mappedClass.toUri().toString() );
	}

	@Test
	void testDefinitionReturnsMappedReferenceUriWhenServerCannotResolveMapping() throws Exception {
		provider.setAppContext( new ProjectAppContext(
		    workspaceRoot,
		    Map.of(),
		    List.of(),
		    "no-server-mappings"
		) );

		Path	consumer	= workspaceRoot.resolve( "MappedFallbackConsumer.cfc" );
		String	source		= """
		                      component extends="testbox.system.BaseSpec" {
		                      }
		                      """;
		Files.writeString( consumer, source );

		BoxLangTextDocumentService svc = new BoxLangTextDocumentService();
		svc.didOpen( new DidOpenTextDocumentParams(
		    new TextDocumentItem( consumer.toUri().toString(), "boxlang", 1, source )
		) );

		int					tokenOffset	= source.indexOf( "extends" );
		Position			position	= positionFromOffset( source, tokenOffset + 1 );

		DefinitionParams	params		= new DefinitionParams();
		params.setTextDocument( new TextDocumentIdentifier( consumer.toUri().toString() ) );
		params.setPosition( position );

		var result = svc.definition( params ).get();

		assertThat( result ).isNotNull();
		assertThat( result.isLeft() ).isTrue();
		assertThat( result.getLeft() ).isNotEmpty();
		assertThat( result.getLeft().getFirst().getUri() ).endsWith( "/testbox/system/BaseSpec" );
	}

	private boolean isInvalidExtendsDiagnostic( Diagnostic diagnostic ) {
		if ( diagnostic.getCode() != null && diagnostic.getCode().isLeft() ) {
			return InvalidExtendsRule.ID.equals( diagnostic.getCode().getLeft() );
		}
		return diagnostic.getMessage() != null && diagnostic.getMessage().contains( "extends reference" );
	}

	private boolean isInvalidImplementsDiagnostic( Diagnostic diagnostic ) {
		if ( diagnostic.getCode() != null && diagnostic.getCode().isLeft() ) {
			return InvalidImplementsRule.ID.equals( diagnostic.getCode().getLeft() );
		}
		return diagnostic.getMessage() != null && diagnostic.getMessage().contains( "implements reference" );
	}

	private Position positionFromOffset( String source, int offset ) {
		int	line	= 0;
		int	column	= 0;
		for ( int i = 0; i < offset && i < source.length(); i++ ) {
			if ( source.charAt( i ) == '\n' ) {
				line++;
				column = 0;
			} else {
				column++;
			}
		}
		return new Position( line, column );
	}
}
