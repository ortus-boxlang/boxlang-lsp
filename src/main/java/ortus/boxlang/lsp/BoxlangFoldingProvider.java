package ortus.boxlang.lsp;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.FoldingRangeKind;
import ortus.boxlang.compiler.ast.BoxClass;
import ortus.boxlang.compiler.ast.BoxNode;
import ortus.boxlang.compiler.ast.comment.BoxMultiLineComment;
import ortus.boxlang.compiler.ast.statement.*;
import ortus.boxlang.lsp.workspace.FileParseResult;
import ortus.boxlang.lsp.workspace.ProjectContextProvider;
import ortus.boxlang.parser.antlr.BoxLexer;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class BoxlangFoldingProvider {

	// Finds all folding ranges within a document
	public static List<FoldingRange> getFoldingRanges( URI documentURI ) {

		List<FoldingRange>	ranges	= new ArrayList<>();

		// Folding currently targets BoxLang class (.bx) and script (.bxs) files only
		String				uri		= documentURI.toString();
		if ( !uri.endsWith( ".bx" ) && !uri.endsWith( ".bxs" ) )
			return ranges;

		// Find the specific document the user is on right now
		Optional<FileParseResult> parseResultOpt = ProjectContextProvider.getInstance().getLatestFileParseResultPublic( documentURI );
		if ( parseResultOpt.isEmpty() )
			return ranges;

		FileParseResult		parseResult	= parseResultOpt.get();

		// Find the AST root of the document
		Optional<BoxNode>	rootOpt		= parseResult.findAstRoot();
		if ( rootOpt.isEmpty() )
			return ranges;

		BoxNode							root				= rootOpt.get();

		// Find all code folding types
		List<BoxFunctionDeclaration>	functions			= root.getDescendantsOfType( BoxFunctionDeclaration.class );
		List<BoxClass>					classes				= root.getDescendantsOfType( BoxClass.class );
		List<BoxIfElse>					ifElses				= root.getDescendantsOfType( BoxIfElse.class );
		List<BoxForIndex>				forIndex			= root.getDescendantsOfType( BoxForIndex.class );
		List<BoxForIn>					forIns				= root.getDescendantsOfType( BoxForIn.class );
		List<BoxWhile>					whiles				= root.getDescendantsOfType( BoxWhile.class );
		List<BoxMultiLineComment>		multiLineComments	= root.getDescendantsOfType( BoxMultiLineComment.class );

		// Add only nodes to ranges if they are multiline
		for ( BoxFunctionDeclaration node : functions ) {
			addMultilineRange( ranges, node );
		}
		for ( BoxClass node : classes ) {
			addMultilineRange( ranges, node );
		}
		for ( BoxIfElse node : ifElses ) {
			addMultilineRange( ranges, node.getThenBody() );
			addMultilineRange( ranges, node.getElseBody() );
		}
		for ( BoxForIndex node : forIndex ) {
			addMultilineRange( ranges, node.getBody() );
		}
		for ( BoxForIn node : forIns ) {
			addMultilineRange( ranges, node.getBody() );
		}
		for ( BoxWhile node : whiles ) {
			addMultilineRange( ranges, node.getBody() );
		}
		for ( BoxMultiLineComment node : multiLineComments ) {
			addMultilineRange( ranges, node );
		}

		return finalizeRanges( ranges, findElseContinuationLines( root ) );
	}

	// End a code fold one line early when its closing-brace line starts another fold or continues
	// with "else" (e.g. "} else" before a braceless else body), so collapsing it does not hide that line or cross the next fold.
	// Also uses a LinkedHashSet to drop duplicate ranges and keep ranges in order they were added.
	// Refer to FoldingRangeTest.foldsIfAndElseBodiesSeparately and endsIfFoldBeforeBracelessElse tests to see it in use.
	private static List<FoldingRange> finalizeRanges( List<FoldingRange> ranges, Set<Integer> elseContinuationLines ) {
		Set<Integer>		startLines	= ranges.stream().map( FoldingRange::getStartLine ).collect( Collectors.toSet() );
		Set<FoldingRange>	result		= new LinkedHashSet<>();
		for ( FoldingRange range : ranges ) {
			int endLine = range.getEndLine();
			if ( range.getKind() == null && ( startLines.contains( endLine ) || elseContinuationLines.contains( endLine ) ) )
				range.setEndLine( endLine - 1 );
			if ( range.getEndLine() > range.getStartLine() )
				result.add( range );
		}
		return new ArrayList<>( result );
	}

	// Finds lines whose code starts with one or more closing braces followed by a "else" -- e.g. "} else".
	// Needed to prevent a braceless "else" from being folded into an if statement.
	// Uses syntax tokens so braces and keywords inside strings and comments are ignored.
	private static Set<Integer> findElseContinuationLines( BoxNode root ) {
		Set<Integer> lines = new HashSet<>();
		if ( root.getSourceText() == null || root.getPosition() == null )
			return lines;

		BoxLexer lexer = new BoxLexer( CharStreams.fromString( root.getSourceText() ) );
		lexer.pushMode( BoxLexer.DEFAULT_SCRIPT_MODE );

		int		lineOffset	= root.getPosition().getStart().getLine() - 2;
		int		currentLine	= -1;
		boolean	onlyBraces	= false;
		for ( Token token : lexer.getAllTokens() ) {
			if ( token.getChannel() != Token.DEFAULT_CHANNEL )
				continue;
			// First code token on a new line: track whether the line so far is only closing braces
			if ( token.getLine() != currentLine ) {
				currentLine	= token.getLine();
				onlyBraces	= token.getType() == BoxLexer.RBRACE;
				continue;
			}
			if ( onlyBraces && token.getType() == BoxLexer.ELSE )
				lines.add( lineOffset + token.getLine() );
			onlyBraces = onlyBraces && token.getType() == BoxLexer.RBRACE;
		}
		return lines;
	}

	private static void addMultilineRange( List<FoldingRange> ranges, BoxNode node ) {
		if ( ! ( node instanceof BoxStatementBlock || node instanceof BoxClass || node instanceof BoxFunctionDeclaration
		    || node instanceof BoxMultiLineComment ) || node.getPosition() == null )
			return;

		int	startLine	= node.getPosition().getStart().getLine() - 1;
		int	endLine		= node.getPosition().getEnd().getLine() - 1;

		// Class/function positions include their headers; match the final body brace using syntax tokens.
		if ( node instanceof BoxClass || node instanceof BoxFunctionDeclaration ) {
			if ( node.getSourceText() == null )
				return;

			BoxLexer lexer = new BoxLexer( CharStreams.fromString( node.getSourceText() ) );
			lexer.pushMode( BoxLexer.DEFAULT_SCRIPT_MODE );

			// ignores comments and whitespaces (those are on a hidden channel)
			var tokens = lexer.getAllTokens().stream().filter( token -> token.getChannel() == Token.DEFAULT_CHANNEL ).toList();
			if ( tokens.isEmpty() || tokens.getLast().getType() != BoxLexer.RBRACE )
				return;

			int	lineOffset	= startLine - 1;
			int	depth		= 0;
			startLine	= -1;
			endLine		= lineOffset + tokens.getLast().getLine();

			for ( Token token : tokens.reversed() ) {
				if ( token.getType() == BoxLexer.RBRACE ) {
					depth++;
				} else if ( token.getType() == BoxLexer.LBRACE && --depth == 0 ) {
					startLine = lineOffset + token.getLine();
					break;
				}
			}
			if ( startLine < 0 )
				return;
		}

		if ( endLine > startLine ) {
			FoldingRange range = new FoldingRange( startLine, endLine );
			if ( node instanceof BoxMultiLineComment )
				range.setKind( FoldingRangeKind.Comment );
			ranges.add( range );
		}
	}
}
