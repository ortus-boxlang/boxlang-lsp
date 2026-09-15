package ortus.boxlang.lsp;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.eclipse.lsp4j.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ortus.boxlang.lsp.workspace.ProjectContextProvider;
import ortus.boxlang.lsp.workspace.index.ProjectIndex;

public class CfmlCallableCompletionTest extends BaseTest {

	@TempDir
	Path							directory;
	private ProjectContextProvider	provider;

	@BeforeEach
	void initialize() {
		provider = ProjectContextProvider.getInstance();
		ProjectIndex index = new ProjectIndex();
		index.initialize( directory );
		provider.setIndex( index );
	}

	@Test
	void localFunctionShadowsBuiltinWithItsOwnSignature() throws Exception {
		var	items		= complete( "Caller.cfc",
		    "component { function len(required numeric customLength) { return customLength; } function run() { return le<caret>(1); } }" );
		var	matching	= items.stream().filter( i -> i.getLabel().equalsIgnoreCase( "len" ) ).toList();
		assertEquals( 1, matching.size() );
		var callable = callable( matching.getFirst() );
		assertEquals( "project", callable.get( "origin" ) );
		assertEquals( "customLength", ( ( Map<?, ?> ) ( ( List<?> ) callable.get( "params" ) ).getFirst() ).get( "name" ) );
	}

	@Test
	void nestedFunctionIsOnlyVisibleInItsEnclosingFunction() throws Exception {
		var items = complete( "Caller.cfc", "component { function outer() { function secretLocal() { return 1; } } function other() { sec<caret>(); } }" );
		assertFalse( items.stream().anyMatch( i -> i.getLabel().equalsIgnoreCase( "secretLocal" ) ) );
	}

	@Test
	void nearestLocalFunctionWinsOverClassMethod() throws Exception {
		var	items		= complete( "Caller.cfc",
		    "component { function len(required numeric classValue) { return classValue; } function run() { function len(required string localValue) { return localValue; } return le<caret>(1); } }" );
		var	matching	= items.stream().filter( i -> i.getLabel().equalsIgnoreCase( "len" ) ).toList();
		assertEquals( 1, matching.size() );
		assertEquals( "localValue", ( ( Map<?, ?> ) ( ( List<?> ) callable( matching.getFirst() ).get( "params" ) ).getFirst() ).get( "name" ) );
	}

	@Test
	void projectComponentMemberCarriesSignatureAndRespectsVisibility() throws Exception {
		Path service = directory.resolve( "GreetingService.cfc" );
		Files.writeString( service, "component { public string function greetPerson(required string name) { return name; } private function hidden() {} }" );
		provider.getIndex().indexFile( service.toUri() );
		var	items	= complete( "Caller.cfc", "component { function run(required GreetingService service) { service.<caret>greetPerson(\"x\"); } }" );
		var	method	= items.stream().filter( i -> i.getLabel().equalsIgnoreCase( "greetPerson" ) ).findFirst().orElseThrow();
		assertEquals( CompletionItemKind.Method, method.getKind() );
		assertEquals( "project", callable( method ).get( "origin" ) );
		assertFalse( items.stream().anyMatch( i -> i.getLabel().equalsIgnoreCase( "hidden" ) ) );
	}

	@Test
	void incompleteExpressionStillCompletesLocalFunction() throws Exception {
		var	items	= complete( "Caller.cfc",
		    "component { function len(required numeric customLength) { return customLength; } function run() { le<caret>; } }" );
		var	len		= items.stream().filter( i -> i.getLabel().equalsIgnoreCase( "len" ) ).findFirst().orElseThrow();
		assertEquals( "project", callable( len ).get( "origin" ) );
	}

	@Test
	void completionUsesUnsavedChangeBeforeDiagnosticDebounce() throws Exception {
		complete( "Caller.cfc", "component { function beforeEdit() {} function run() { be<caret>(); } }" );
		var		uri		= directory.resolve( "Caller.cfc" ).toUri();
		String	source	= "component { function afterEdit(required string value) {} function run() { af; } }";
		provider.trackDocumentChange( uri, List.of( new TextDocumentContentChangeEvent( source ) ), 2 );
		var items = provider.getAvailableCompletions( uri,
		    new CompletionParams( new TextDocumentIdentifier( uri.toString() ), new Position( 0, source.indexOf( "af;" ) + 2 ) ) );
		assertTrue( items.stream().anyMatch( i -> i.getLabel().equals( "afterEdit" ) ) );
		assertFalse( items.stream().anyMatch( i -> i.getLabel().equals( "beforeEdit" ) ) );
	}

	@Test
	void typedArrayHasNativeMembersWithoutUnrelatedTypes() throws Exception {
		var	items	= complete( "Caller.cfc", "component { function run(required array values) { values.<caret>append(1); } }" );
		var	append	= items.stream().filter( i -> i.getLabel().equalsIgnoreCase( "append" ) ).findFirst().orElseThrow();
		assertTrue( ( ( Map<?, ?> ) append.getData() ).containsKey( "boxlangMember" ) );
		assertFalse( items.stream().anyMatch( i -> i.getLabel().equalsIgnoreCase( "keyExists" ) ) );
	}

	private List<CompletionItem> complete( String name, String marked ) throws Exception {
		int		offset	= marked.indexOf( "<caret>" );
		String	source	= marked.replace( "<caret>", "" );
		Path	file	= directory.resolve( name );
		Files.writeString( file, source );
		provider.getIndex().indexFile( file.toUri() );
		provider.trackDocumentOpen( file.toUri(), source );
		String	before	= source.substring( 0, offset );
		int		line	= ( int ) before.chars().filter( c -> c == '\n' ).count();
		int		column	= offset - before.lastIndexOf( '\n' ) - 1;
		return provider.getAvailableCompletions( file.toUri(),
		    new CompletionParams( new TextDocumentIdentifier( file.toUri().toString() ), new Position( line, column ) ) );
	}

	private Map<?, ?> callable( CompletionItem item ) {
		return ( Map<?, ?> ) ( ( Map<?, ?> ) item.getData() ).get( "boxlangCallable" );
	}
}
