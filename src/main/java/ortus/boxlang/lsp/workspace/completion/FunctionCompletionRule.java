package ortus.boxlang.lsp.workspace.completion;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;

import ortus.boxlang.compiler.ast.BoxNode;
import ortus.boxlang.compiler.ast.BoxScript;
import ortus.boxlang.compiler.ast.statement.BoxArgumentDeclaration;
import ortus.boxlang.compiler.ast.statement.BoxFunctionDeclaration;
import ortus.boxlang.lsp.workspace.rules.IRule;

/**
 * This rule provides completions for User-Defined Functions (UDFs) and methods.
 * It suggests functions from the current file and potentially imported/indexed functions.
 */
public class FunctionCompletionRule implements IRule<CompletionFacts, List<CompletionItem>> {

	@Override
	public boolean when( CompletionFacts facts ) {
		// Only trigger in general context (not after a dot, etc.)
		return facts.getContext().getKind() == CompletionContextKind.GENERAL;
	}

	@Override
	public void then( CompletionFacts facts, List<CompletionItem> result ) {
		BoxNode root = facts.fileParseResult().findAstRoot().orElse( null );
		if ( root == null ) {
			return;
		}

		var						cursor		= facts.getContext().getCursorPosition();
		var						functions	= root.getDescendantsOfType( BoxFunctionDeclaration.class ).stream()
		    .filter( function -> {
												    var enclosing = function.getFirstAncestorOfType( BoxFunctionDeclaration.class );
												    return enclosing == null || ortus.boxlang.lsp.workspace.BLASTTools.containsPosition( enclosing,
												        cursor.getLine() + 1, cursor.getCharacter() );
											    } )
		    .sorted( java.util.Comparator.comparingInt( ( BoxFunctionDeclaration function ) -> function.getAncestors().size() ).reversed() )
		    .toList();
		java.util.Set<String>	seen		= new java.util.HashSet<>();
		for ( var function : functions ) {
			if ( !seen.add( function.getName().toLowerCase( java.util.Locale.ROOT ) ) )
				continue;
			// The nearest visible declaration shadows both outer functions and built-ins.
			result.removeIf( item -> item.getKind() == CompletionItemKind.Function && item.getLabel().equalsIgnoreCase( function.getName() ) );
			result.add( createFunctionCompletionItem( function ) );
		}

	}

	/**
	 * Create a CompletionItem for a BoxFunctionDeclaration.
	 *
	 * @param func The function declaration
	 *
	 * @return The completion item
	 */
	private CompletionItem createFunctionCompletionItem( BoxFunctionDeclaration func ) {
		CompletionItem item = new CompletionItem( func.getName() );
		item.setKind( CompletionItemKind.Function );

		// Build signature for detail
		StringBuilder					signature	= new StringBuilder( func.getName() ).append( "(" );
		List<BoxArgumentDeclaration>	args		= func.getArgs();
		for ( int i = 0; i < args.size(); i++ ) {
			BoxArgumentDeclaration arg = args.get( i );
			if ( i > 0 ) {
				signature.append( ", " );
			}
			if ( arg.getType() != null ) {
				signature.append( arg.getType().toString() ).append( " " );
			}
			signature.append( arg.getName() );
			if ( arg.getValue() != null ) {
				signature.append( "=" ).append( arg.getValue().toString() );
			}
		}
		signature.append( ")" );
		if ( func.getType() != null ) {
			signature.append( " : " ).append( func.getType().toString() );
		}

		item.setDetail( signature.toString() );
		item.setInsertText( func.getName() + "($1)" );
		item.setInsertTextFormat( org.eclipse.lsp4j.InsertTextFormat.Snippet );
		CallableCompletionData.attach( item, func.getType() == null ? "any" : func.getType().toString(),
		    args.stream().map( arg -> CallableCompletionData.parameter( arg.getName(), arg.getType(), arg.getRequired() ) ).toList(),
		    "Project-defined function" );
		item.setSortText( "2" + func.getName() ); // Sort UDFs before BIFs (which are 5)

		return item;
	}
}
