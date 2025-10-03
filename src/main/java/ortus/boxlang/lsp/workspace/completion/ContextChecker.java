package ortus.boxlang.lsp.workspace.completion;

import java.util.regex.Pattern;

public class ContextChecker {

	static final Pattern	newPattern		= Pattern.compile( "\\W*new\\W(\\w[\\w\\d\\$\\-_\\.]*)*$", Pattern.CASE_INSENSITIVE );
	static final Pattern	importPattern	= Pattern.compile( "^\\s*import\\s+(\\w[\\w\\d\\$\\-_\\.]*)*$", Pattern.CASE_INSENSITIVE );

	public static boolean isNewExpression( CompletionFacts facts ) {
		var existingPrompt = facts.fileParseResult().readLine(
		    facts.completionParams().getPosition().getLine() );

		existingPrompt = existingPrompt.substring( 0, facts.completionParams().getPosition().getCharacter() );

		return newPattern.matcher( existingPrompt ).find();
	}

	public static boolean isImportExpression( CompletionFacts facts ) {
		var existingPrompt = facts.fileParseResult().readLine(
		    facts.completionParams().getPosition().getLine() );

		existingPrompt = existingPrompt.substring( 0, facts.completionParams().getPosition().getCharacter() );

		return importPattern.matcher( existingPrompt ).find();
	}
}
