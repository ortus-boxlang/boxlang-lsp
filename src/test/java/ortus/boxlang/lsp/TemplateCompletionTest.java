package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertThat;

import java.net.URI;
import java.util.List;

import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.CompletionContext;
import org.eclipse.lsp4j.CompletionTriggerKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import ortus.boxlang.lsp.workspace.FileParseResult;
import ortus.boxlang.lsp.workspace.completion.CompletionFacts;
import ortus.boxlang.lsp.workspace.completion.CompletionProviderRuleBook;
import ortus.boxlang.runtime.BoxRuntime;

class TemplateCompletionTest extends BaseTest {

	@BeforeAll
	static void startRuntime() {
		BoxRuntime.getInstance( true );
	}

	@ParameterizedTest
	@ValueSource( strings = { "<", "<b", "<bx", "<bx:", "<bx:set", "  <BX:SET", "<div>\n\t<bx:set" } )
	void acceptingSettingReplacesExistingPrefix( String source ) {
		CompletionItem item = completions( source ).stream().filter( candidate -> candidate.getLabel().equals( "bx:setting" ) ).findFirst().orElseThrow();
		// VS Code filters against the edit range, including '<', rather than just the tag's label.
		assertThat( item.getFilterText() ).isEqualTo( "<bx:setting" );
		assertThat( item.getTextEdit() ).isNotNull();
		var		edit		= item.getTextEdit().getLeft();
		int		lineStart	= source.lastIndexOf( '\n' ) + 1;
		String	result		= source.substring( 0, lineStart + edit.getRange().getStart().getCharacter() ) + edit.getNewText().replace( "$0", "" )
		    + source.substring( lineStart + edit.getRange().getEnd().getCharacter() );
		assertThat( result ).isEqualTo( source.substring( 0, source.lastIndexOf( '<' ) ) + "<bx:setting  />" );
	}

	@Test
	void attributePositionOffersAttributesNotTags() {
		List<CompletionItem> items = completions( "<bx:setting " );
		assertThat( items.stream().map( CompletionItem::getLabel ).toList() ).contains( "requestTimeout" );
		assertThat( items.stream().anyMatch( item -> item.getLabel().startsWith( "bx:" ) ) ).isFalse();
	}

	@ParameterizedTest
	@ValueSource( strings = { "<bx:setting\n requestTimeout=\"30\"\n ", "<bx:setting requestTimeout = 30 ", "<bx:setting requestTimeout='30' " } )
	void multilineTagOffersOnlyUnusedAttributes( String source ) {
		List<String> labels = completions( source ).stream().map( CompletionItem::getLabel ).toList();
		assertThat( labels ).contains( "showDebugOutput" );
		assertThat( labels ).doesNotContain( "requestTimeout" );
		assertThat( labels.stream().anyMatch( label -> label.startsWith( "bx:" ) ) ).isFalse();
	}

	@ParameterizedTest
	@ValueSource( strings = {
	    "<button", "</bx:", "<bx:setting>", "<bx:setting />", "<bx:setting requestTimeout=\"<bx:",
	    "<div title=\"<bx:", "<!--\n > <bx:", "<!---\n <bx:", "<!--- outer\n <!--- inner --->\n <bx:",
	    "<bx:script>\n <bx:", "<script>\n '<bx:", "#\"<bx:"
	} )
	void unrelatedOrSuppressedContextsDoNotOfferTags( String source ) {
		assertThat( completions( source ).stream().anyMatch( item -> item.getLabel().startsWith( "bx:" ) ) ).isFalse();
	}

	@Test
	void advertisesTemplateAndMemberTriggers() throws Exception {
		InitializeParams params = new InitializeParams();
		params.setCapabilities( new ClientCapabilities() );
		params.setWorkspaceFolders( List.of() );
		assertThat( new LanguageServer().initialize( params ).get().getCapabilities().getCompletionProvider().getTriggerCharacters() )
		    .containsExactly( ".", "<", ":", " " );
	}

	@ParameterizedTest
	@ValueSource( strings = { "var value = ", "value:", "a <" } )
	void templateTriggersDoNotOpenScriptSuggestions( String source ) {
		assertThat( completions( source, ".bx", source.substring( source.length() - 1 ) ) ).isEmpty();
		assertThat( completions( source, ".bx", null ) ).isNotEmpty();
	}

	@ParameterizedTest
	@ValueSource( strings = { "<", "<bx:", "<bx:setting " } )
	void templateTriggersReturnRelevantSuggestions( String source ) {
		List<CompletionItem>	items		= completions( source, ".bxm", source.substring( source.length() - 1 ) );
		String					expected	= source.endsWith( " " ) ? "requestTimeout" : "bx:setting";
		assertThat( items.stream().map( CompletionItem::getLabel ).toList() ).contains( expected );
	}

	@ParameterizedTest
	@ValueSource( strings = { "<!-- done -->\n<bx:", "<!--- done --->\n<bx:", "<bx:script>writeOutput(1);</bx:script>\n<bx:",
	    "<script>const a = 1;</script>\n<bx:", "#\"text\"#\n<bx:", "##\n<bx:" } )
	void tagCompletionResumesAfterSuppressedContexts( String source ) {
		assertThat( completions( source ).stream().map( CompletionItem::getLabel ).toList() ).contains( "bx:setting" );
	}

	@Test
	void quotedDelimitersDoNotEndTheOpeningTag() {
		assertThat( completions( "<bx:setting requestTimeout=\"a > b < c 'quoted'\" " ).stream().map( CompletionItem::getLabel ).toList() )
		    .contains( "showDebugOutput" );
	}

	@Test
	void attributesOnOtherTagsDoNotHideCurrentAttributes() {
		assertThat( completions( "<bx:setting requestTimeout=\"30\" /><bx:setting " ).stream().map( CompletionItem::getLabel ).toList() )
		    .contains( "requestTimeout" );
	}

	@Test
	void dotTriggeredScriptCompletionIsUnchanged() {
		String			source	= "class { property name=\"title\"; function demo() { this.";
		List<String>	manual	= completions( source, ".bx", null ).stream().map( CompletionItem::getLabel ).toList();
		assertThat( manual ).isNotEmpty();
		assertThat( completions( source, ".bx", "." ).stream().map( CompletionItem::getLabel ).toList() ).containsExactlyElementsIn( manual );
	}

	@ParameterizedTest
	@ValueSource( strings = { "associate", "savecontent" } )
	void requiredAttributeIsNotLinkedToFinalCursor( String tag ) {
		CompletionItem	item	= completions( "<bx:" ).stream()
		    .filter( candidate -> candidate.getLabel().equals( "bx:" + tag ) ).findFirst().orElseThrow();
		String			snippet	= item.getTextEdit().getLeft().getNewText();
		assertThat( snippet ).contains( "=\"$1\"" );
		assertThat( snippet.indexOf( "$1" ) ).isEqualTo( snippet.lastIndexOf( "$1" ) );
		assertThat( snippet ).contains( tag.equals( "associate" ) ? "/>$0" : ">$0</bx:savecontent>" );
	}

	@ParameterizedTest
	@ValueSource( strings = { "<", "<b", "<bx:", "<bx:scr" } )
	void scriptCompletionReplacesPrefixAndPlacesCursorInBody( String source ) {
		CompletionItem	item	= completions( source ).stream()
		    .filter( candidate -> candidate.getLabel().equals( "bx:script" ) ).findFirst().orElseThrow();
		var				edit	= item.getTextEdit().getLeft();
		assertThat( edit.getRange().getStart() ).isEqualTo( new Position( 0, 0 ) );
		assertThat( edit.getRange().getEnd() ).isEqualTo( new Position( 0, source.length() ) );
		assertThat( edit.getNewText() ).isEqualTo( "<bx:script>$0</bx:script>" );
		assertThat( item.getFilterText() ).isEqualTo( "<bx:script" );
		assertThat( item.getInsertTextFormat() ).isEqualTo( org.eclipse.lsp4j.InsertTextFormat.Snippet );
	}

	private List<CompletionItem> completions( String source ) {
		return completions( source, ".bxm", null );
	}

	private List<CompletionItem> completions( String source, String extension, String trigger ) {
		URI					uri		= URI.create( "file:///test/Completion" + extension );
		String[]			lines	= source.split( "\n", -1 );
		CompletionParams	params	= new CompletionParams( new TextDocumentIdentifier( uri.toString() ),
		    new Position( lines.length - 1, lines[ lines.length - 1 ].length() ) );
		if ( trigger != null )
			params.setContext( new CompletionContext( CompletionTriggerKind.TriggerCharacter, trigger ) );
		return CompletionProviderRuleBook.execute( new CompletionFacts( FileParseResult.fromSourceString( uri, source ), params ) );
	}
}
