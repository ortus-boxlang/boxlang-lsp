package ortus.boxlang.lsp.workspace.completion;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;

import ortus.boxlang.compiler.ast.BoxNode;
import ortus.boxlang.lsp.workspace.ProjectContextProvider;
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

		// 1. Get UDFs from the current file
		List<BoxFunctionDeclaration> functions = root.getDescendantsOfType( BoxFunctionDeclaration.class );
		for ( BoxFunctionDeclaration func : functions ) {
			result.add( createFunctionCompletionItem( func ) );
		}

		String className = facts.getContext().getContainingClassName();
		if ( className != null ) {
			Set<String>					declared	= functions.stream().map( func -> func.getName().toLowerCase() ).collect( Collectors.toSet() );
			MemberCompletionCollector	collector	= new MemberCompletionCollector(
			    ProjectContextProvider.getInstance().getIndex(), className, facts.fileParseResult().getURI() );
			collector.collectMembers( className, facts.getContext().getTriggerText() ).stream()
			    .filter( item -> item.getKind() == CompletionItemKind.Method && !declared.contains( item.getLabel().toLowerCase() ) )
			    .forEach( result::add );
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
		item.setSortText( "2" + func.getName() ); // Sort UDFs before BIFs (which are 5)

		return item;
	}
}
