package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.components.ComponentDescriptor;
import ortus.boxlang.runtime.components.Attribute;
import ortus.boxlang.lsp.workspace.ComponentDocumentation;
import ortus.boxlang.runtime.components.system.Setting;
import ortus.boxlang.runtime.scopes.Key;

class ComponentHoverTest extends BaseTest {

	@TempDir
	Path directory;

	@Test
	void tagHoverShowsAnnotationDescriptionAttributesAndGitBookLink() throws Exception {
		Hover hover = hover( "<bx:set|ting requestTimeout=\"30\" />" );
		assertThat( hover ).isNotNull();
		String content = hover.getContents().getRight().getValue();
		assertThat( content ).contains( "Modify runtime settings and configuration" );
		assertThat( content ).contains( "requestTimeout" );
		assertThat( content ).contains( "long" );
		assertThat( content ).contains( "https://boxlang.ortusbooks.com/boxlang-language/reference/components/system/setting" );
	}

	@Test
	void hoverUsesHighlightedSignatureAndCompactAttributeTable() throws Exception {
		String content = hover( "<bx:du|mp />" ).getContents().getRight().getValue();
		assertThat( content ).startsWith( "```boxlang\n<bx:dump />\n```\n\n" );
		assertThat( content ).contains( "| Attribute | Type | Default |\n| --- | --- | --- |" );
		assertThat( content ).contains( "| `label` | `string` | `\"\"` |" );
		assertThat( content ).contains( "_Self-closing tag_ · [Documentation ↗]" );
		assertThat( content ).doesNotContain( "- **" );
	}

	@Test
	void closingTagShowsTheSameComponentDocumentation() throws Exception {
		Hover hover = hover( "<bx:output>\nHello\n</bx:out|put>" );
		assertThat( hover ).isNotNull();
		assertThat( hover.getContents().getRight().getValue() ).contains( "Control and format output of content blocks" );
	}

	@ParameterizedTest
	@ValueSource( strings = {
	    "<bx:setting requestTimeout=\"3|0\" />", "<bx:setting request|Timeout=\"30\" />",
	    "<bx:output>he|llo</bx:output>", "<!--- <bx:set|ting /> --->",
	    "<div title=\"<bx:set|ting />\">", "<bx:script>var text = '<bx:set|ting />';</bx:script>"
	} )
	void componentDocumentationDoesNotLeakIntoAttributesBodiesCommentsOrStrings( String source ) throws Exception {
		assertThat( hover( source ) ).isNull();
	}

	@Test
	void nestedAndUppercaseTagsUseTheirOwnDocumentation() throws Exception {
		Hover hover = hover( "<bx:output>\n  <BX:SET|TING requestTimeout=\"30\" />\n</bx:output>" );
		assertThat( hover ).isNotNull();
		assertThat( hover.getContents().getRight().getValue() ).contains( "Modify runtime settings and configuration" );
	}

	@Test
	void requiredAttributesAndDefaultsAreDocumented() throws Exception {
		Hover hover = hover( "<bx:asso|ciate baseTag=\"parent\" />" );
		assertThat( hover ).isNotNull();
		String content = hover.getContents().getRight().getValue();
		assertThat( content ).contains( "| `baseTag` **required** | `string` | — |" );
		assertThat( content ).contains( "| `dataCollection` | `string` | `\"AssocAttribs\"` |" );
	}

	@Test
	void aliasLinksToCanonicalComponentPage() throws Exception {
		Hover hover = hover( "<bx:g|zip action=\"list\" file=\"test.zip\" name=\"result\" />" );
		assertThat( hover ).isNotNull();
		assertThat( hover.getContents().getRight().getValue() ).contains( "/components/zip/zip)" );
	}

	@Test
	void moduleWithoutDocumentationAnnotationsStillShowsMetadataWithoutInventingCoreLink() throws Exception {
		var	service	= BoxRuntime.getInstance().getComponentService();
		Key	name	= Key.of( "lspTestUndocumented" );
		service.registerComponent( new ComponentDescriptor( name, UndocumentedComponent.class, "testModule", null,
		    new UndocumentedComponent(), false, false, false, false ), false );
		try {
			Hover hover = hover( "<bx:lspTestUndocu|mented />" );
			assertThat( hover ).isNotNull();
			String content = hover.getContents().getRight().getValue();
			assertThat( content ).contains( "requestTimeout" );
			assertThat( content ).doesNotContain( "boxlang.ortusbooks.com" );
		} finally {
			service.unregisterComponent( name );
		}
	}

	@Test
	void attributeDefaultsCannotBreakTheMarkdownTable() {
		var		component	= new UndocumentedComponent( new Attribute( Key.of( "example" ), "string", "left|right`\nnext" ) );
		var		descriptor	= new ComponentDescriptor( Key.of( "example" ), UndocumentedComponent.class, "testModule", null,
		    component, false, false, false, false );
		String	content		= ComponentDocumentation.format( descriptor ).getValue();
		assertThat( content ).contains( "| `example` | `string` | ``\"left\\|right` next\"`` |" );
	}

	public static class UndocumentedComponent extends Setting {

		public UndocumentedComponent( Attribute... attributes ) {
			if ( attributes.length > 0 )
				declaredAttributes = attributes;
		}
	}

	private Hover hover( String markedSource ) throws Exception {
		int			cursor		= markedSource.indexOf( '|' );
		String		before		= markedSource.substring( 0, cursor );
		Position	position	= new Position( ( int ) before.chars().filter( c -> c == '\n' ).count(), cursor - before.lastIndexOf( '\n' ) - 1 );
		Path		path		= directory.resolve( "Hover.bxm" );
		String		source		= markedSource.replace( "|", "" );
		Files.writeString( path, source );
		BoxLangTextDocumentService service = new BoxLangTextDocumentService();
		service.didOpen( new DidOpenTextDocumentParams( new TextDocumentItem( path.toUri().toString(), "boxlang", 1, source ) ) );
		return service.hover( new HoverParams( new TextDocumentIdentifier( path.toUri().toString() ), position ) ).get();
	}
}
