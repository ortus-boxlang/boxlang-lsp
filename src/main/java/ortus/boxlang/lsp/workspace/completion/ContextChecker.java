package ortus.boxlang.lsp.workspace.completion;

import java.util.regex.Pattern;

import ortus.boxlang.lsp.workspace.ProjectContextProvider;

public class ContextChecker {

	static final Pattern newPattern = Pattern.compile( "\\W*new\\W(\\w[\\w\\d\\$\\-_\\.]*)*$", Pattern.CASE_INSENSITIVE );

	public static boolean isNewExpression( CompletionFacts facts ) {
		var existingPrompt = ProjectContextProvider.readLine( facts.completionParams().getTextDocument().getUri(),
		    facts.completionParams().getPosition().getLine() );

		existingPrompt = existingPrompt.substring( 0, facts.completionParams().getPosition().getCharacter() );

		return newPattern.matcher( existingPrompt ).find();
	}
}
