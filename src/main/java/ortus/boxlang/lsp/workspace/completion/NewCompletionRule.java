package ortus.boxlang.lsp.workspace.completion;

import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.InsertTextFormat;

import ortus.boxlang.lsp.workspace.ProjectContextProvider;
import ortus.boxlang.lsp.workspace.rules.IRule;

public class NewCompletionRule implements IRule<CompletionFacts, List<CompletionItem>> {

	static final Pattern newPattern = Pattern.compile( "\\W*new\\W(\\w[\\w\\d\\$\\-_\\.]*)*$", Pattern.CASE_INSENSITIVE );

	@Override
	public boolean when( CompletionFacts facts ) {
		return !facts.fileParseResult().isTemplate()
		    && ContextChecker.isNewExpression( facts );
	}

	@Override
	public void then( CompletionFacts facts, List<CompletionItem> result ) {
		var existingPrompt = ProjectContextProvider.readLine( facts.completionParams().getTextDocument().getUri(),
		    facts.completionParams().getPosition().getLine() );

		existingPrompt = existingPrompt.substring( 0, facts.completionParams().getPosition().getCharacter() );

		// TODO Types of completions
		// classes in same folder
		// classes in a sub folder (should have path)
		// folders in immediate location
		// folders in sub location
		// mappings
		// folders in mappings

		Path	fileDir	= Path.of( facts.fileParseResult().uri() );

		var		options	= ProjectContextProvider.getInstance().newables.stream().map( ( newable ) -> {
							// need to find the resource for the path
							// Path newablePath = Path.of( newable.)
							Path			newableDirPath	= null;

							CompletionItem	item			= new CompletionItem();
							item.setLabel( newable.name() );
							item.setKind( CompletionItemKind.Constructor );
							item.setInsertTextFormat( InsertTextFormat.Snippet );
							item.setInsertText( newable.name() );
							item.setDetail( "class " + newable.name() );

							// build up label
							String label = newable.name();

							if ( newableDirPath.startsWith( fileDir ) && !newableDirPath.equals( fileDir ) ) {
								// it is a in a sub folder
							}

							return item;
						} )
		    .toList();

		result.addAll( options );
	}

}
