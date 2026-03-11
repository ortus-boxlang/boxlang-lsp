package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.junit.jupiter.api.Test;

import ortus.boxlang.lsp.workspace.SemanticTokensContract;

public class SemanticTokensTest extends BaseTest {

	@Test
	void testInitializeAdvertisesSemanticTokensProvider() throws Exception {
		LanguageServer		server	= new LanguageServer();

		InitializeParams	params	= new InitializeParams();
		params.setCapabilities( new ClientCapabilities() );

		InitializeResult result = server.initialize( params ).get();
		assertThat( result.getCapabilities().getSemanticTokensProvider() ).isNotNull();

		var provider = result.getCapabilities().getSemanticTokensProvider();
		assertThat( provider.getLegend() ).isNotNull();
		assertThat( provider.getLegend().getTokenTypes() ).containsExactlyElementsIn( SemanticTokensContract.TOKEN_TYPES ).inOrder();
		assertThat( provider.getLegend().getTokenModifiers() ).containsExactlyElementsIn( SemanticTokensContract.TOKEN_MODIFIERS ).inOrder();
		assertThat( provider.getFull() ).isNotNull();
		assertThat( provider.getFull().isLeft() ).isTrue();
		assertThat( provider.getFull().getLeft() ).isTrue();
	}

	@Test
	void testSemanticTokensFullClassifiesCallsAndDeclarations() throws Exception {
		Path						path	= Path.of( "src/test/resources/files/semanticTokensTest.bx" );
		String						source	= Files.readString( path );

		BoxLangTextDocumentService	service	= new BoxLangTextDocumentService();
		service.didOpen( new DidOpenTextDocumentParams( new TextDocumentItem( path.toUri().toString(), "boxlang", 1, source ) ) );

		SemanticTokensParams	params	= new SemanticTokensParams( new TextDocumentIdentifier( path.toUri().toString() ) );
		SemanticTokens			tokens	= service.semanticTokensFull( params ).get();

		assertThat( tokens ).isNotNull();
		assertThat( tokens.getData() ).isNotEmpty();

		List<DecodedToken> decoded = decodeTokens( tokens, source );

		assertHasToken( decoded, "len", "function", Set.of( "defaultLibrary" ) );
		assertHasToken( decoded, "customHelper", "function", Set.of() );
		assertHasToken( decoded, "toList", "method", Set.of( "defaultLibrary" ) );
		assertHasToken( decoded, "from", "method", Set.of() );
		assertThat( hasToken( decoded, "from", "method", Set.of( "defaultLibrary" ) ) ).isFalse();

		assertHasToken( decoded, "doThing", "method", Set.of() );
		assertHasToken( decoded, "doThing", "method", Set.of( "declaration" ) );

		assertHasToken( decoded, "convertEmptyStringsToNull", "property", Set.of() );
		assertThat( hasToken( decoded, "runtimeFlag", "property", Set.of() ) ).isFalse();
		assertThat( hasToken( decoded, "param", "property", Set.of() ) ).isFalse();
	}

	@Test
	void testSemanticTokensAreSortedAndRelativeEncodingIsStable() throws Exception {
		Path						path	= Path.of( "src/test/resources/files/semanticTokensTest.bx" );
		String						source	= Files.readString( path );

		BoxLangTextDocumentService	service	= new BoxLangTextDocumentService();
		service.didOpen( new DidOpenTextDocumentParams( new TextDocumentItem( path.toUri().toString(), "boxlang", 1, source ) ) );

		SemanticTokensParams	params	= new SemanticTokensParams( new TextDocumentIdentifier( path.toUri().toString() ) );
		SemanticTokens			tokens	= service.semanticTokensFull( params ).get();

		List<DecodedToken>		decoded	= decodeTokens( tokens, source );
		assertThat( decoded ).isNotEmpty();

		for ( int i = 1; i < decoded.size(); i++ ) {
			DecodedToken	previous	= decoded.get( i - 1 );
			DecodedToken	current		= decoded.get( i );

			boolean			inOrder		= current.line > previous.line || ( current.line == previous.line && current.start >= previous.start );
			assertThat( inOrder ).isTrue();
		}
	}

	@Test
	void testSemanticTokensUpdateAfterDidChange() throws Exception {
		Path						path			= Path.of( "src/test/resources/files/semanticTokensTest.bx" );
		String						originalSource	= Files.readString( path );

		BoxLangTextDocumentService	service			= new BoxLangTextDocumentService();
		service.didOpen( new DidOpenTextDocumentParams( new TextDocumentItem( path.toUri().toString(), "boxlang", 1, originalSource ) ) );

		SemanticTokensParams	params			= new SemanticTokensParams( new TextDocumentIdentifier( path.toUri().toString() ) );
		SemanticTokens			before			= service.semanticTokensFull( params ).get();
		List<DecodedToken>		beforeDecoded	= decodeTokens( before, originalSource );
		assertHasToken( beforeDecoded, "len", "function", Set.of( "defaultLibrary" ) );
		assertHasToken( beforeDecoded, "toList", "method", Set.of( "defaultLibrary" ) );

		String modifiedSource = originalSource.replace( "len(", "notBuiltInLen(" ).replace( "toList(", "notMemberList(" );
		service.didChange( new DidChangeTextDocumentParams(
		    new VersionedTextDocumentIdentifier( path.toUri().toString(), 2 ),
		    List.of( new TextDocumentContentChangeEvent( modifiedSource ) )
		) );

		SemanticTokens		after			= service.semanticTokensFull( params ).get();
		List<DecodedToken>	afterDecoded	= decodeTokens( after, modifiedSource );
		assertHasToken( afterDecoded, "notBuiltInLen", "function", Set.of() );
		assertThat( hasToken( afterDecoded, "notBuiltInLen", "function", Set.of( "defaultLibrary" ) ) ).isFalse();

		assertHasToken( afterDecoded, "notMemberList", "method", Set.of() );
		assertThat( hasToken( afterDecoded, "notMemberList", "method", Set.of( "defaultLibrary" ) ) ).isFalse();
	}

	private void assertHasToken( List<DecodedToken> tokens, String text, String tokenType, Set<String> modifiers ) {
		assertThat( hasToken( tokens, text, tokenType, modifiers ) ).isTrue();
	}

	private boolean hasToken( List<DecodedToken> tokens, String text, String tokenType, Set<String> modifiers ) {
		return tokens.stream()
		    .anyMatch( t -> t.text.equals( text ) && t.tokenType.equals( tokenType ) && t.modifiers.equals( modifiers ) );
	}

	private List<DecodedToken> decodeTokens( SemanticTokens tokens, String source ) {
		List<DecodedToken>	decoded	= new ArrayList<>();
		List<Integer>		data	= tokens.getData();

		int					line	= 0;
		int					start	= 0;
		for ( int i = 0; i + 4 < data.size(); i += 5 ) {
			int	deltaLine		= data.get( i );
			int	deltaStart		= data.get( i + 1 );
			int	length			= data.get( i + 2 );
			int	typeIndex		= data.get( i + 3 );
			int	modifierBits	= data.get( i + 4 );

			line	+= deltaLine;
			start	= deltaLine == 0 ? start + deltaStart : deltaStart;

			if ( typeIndex < 0 || typeIndex >= SemanticTokensContract.TOKEN_TYPES.size() ) {
				continue;
			}

			String		tokenType	= SemanticTokensContract.TOKEN_TYPES.get( typeIndex );
			Set<String>	modifiers	= decodeModifiers( modifierBits );
			String		text		= textAt( source, line, start, length );

			decoded.add( new DecodedToken( line, start, length, tokenType, modifiers, text ) );
		}

		return decoded;
	}

	private Set<String> decodeModifiers( int modifierBits ) {
		Set<String> decoded = new HashSet<>();
		for ( int i = 0; i < SemanticTokensContract.TOKEN_MODIFIERS.size(); i++ ) {
			if ( ( modifierBits & ( 1 << i ) ) != 0 ) {
				decoded.add( SemanticTokensContract.TOKEN_MODIFIERS.get( i ) );
			}
		}
		return Set.copyOf( decoded );
	}

	private String textAt( String source, int line, int start, int length ) {
		int	offset	= offsetFor( source, line, start );
		int	end		= Math.min( offset + Math.max( 0, length ), source.length() );
		if ( offset < 0 || offset >= end || offset >= source.length() ) {
			return "";
		}
		return source.substring( offset, end );
	}

	private int offsetFor( String source, int targetLine, int targetColumn ) {
		int	line	= 0;
		int	offset	= 0;
		while ( line < targetLine && offset < source.length() ) {
			int newline = source.indexOf( '\n', offset );
			if ( newline < 0 ) {
				return source.length();
			}
			offset = newline + 1;
			line++;
		}

		int lineEnd = source.indexOf( '\n', offset );
		if ( lineEnd < 0 ) {
			lineEnd = source.length();
		}

		int lineLength = lineEnd - offset;
		return offset + Math.max( 0, Math.min( targetColumn, lineLength ) );
	}

	private record DecodedToken( int line, int start, int length, String tokenType, Set<String> modifiers, String text ) {
	}
}
