package ortus.boxlang.lsp.workspace.completion;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import ortus.boxlang.lsp.lint.LintRuleCatalog;
import ortus.boxlang.lsp.workspace.rules.IRule;

public class BxlintRuleCompletionRule implements IRule<CompletionFacts, List<CompletionItem>> {

	private static final Pattern BXLINT_COMMENT_PATTERN = Pattern.compile(
	    "^\\s*(?://\\s*|<!---?\\s*)bxlint(?::|-)(?:disable|enable|disable-for-function|disable-for-class|enable-for-function|enable-for-class)(?:\\s+(.*?))?\\s*(?:--+>)?$",
	    Pattern.CASE_INSENSITIVE
	);

	private record ParsedRuleSelection( String triggerText, Set<String> existingRuleIds ) {
	}

	@Override
	public boolean when( CompletionFacts facts ) {
		return facts.getContext().getKind() == CompletionContextKind.BXLINT_RULE_COMMENT;
	}

	@Override
	public void then( CompletionFacts facts, List<CompletionItem> result ) {
		ParsedRuleSelection	selection	= parseRuleSelection( facts );
		String				triggerText	= selection.triggerText().toLowerCase( Locale.ROOT );

		for ( LintRuleCatalog.LintRuleInfo rule : LintRuleCatalog.all() ) {
			if ( selection.existingRuleIds().contains( rule.id().toLowerCase( Locale.ROOT ) ) ) {
				continue;
			}

			if ( !triggerText.isEmpty() && !rule.id().toLowerCase( Locale.ROOT ).startsWith( triggerText ) ) {
				continue;
			}

			CompletionItem item = new CompletionItem();
			item.setLabel( rule.id() );
			item.setInsertText( rule.id() );
			item.setKind( CompletionItemKind.EnumMember );
			item.setDetail( rule.description() );
			item.setDocumentation( Either.forRight( toDocumentation( rule ) ) );
			item.setSortText( "0" + rule.id() );

			result.add( item );
		}
	}

	private static ParsedRuleSelection parseRuleSelection( CompletionFacts facts ) {
		String lineText = facts.getContext().getFileParseResult().readLine( facts.getContext().getCursorPosition().getLine() );
		if ( lineText == null ) {
			return new ParsedRuleSelection( facts.getContext().getTriggerText(), Set.of() );
		}

		String	commentBeforeCursor	= lineText.substring( 0, Math.min( facts.getContext().getCursorPosition().getCharacter(), lineText.length() ) );
		Matcher	matcher				= BXLINT_COMMENT_PATTERN.matcher( commentBeforeCursor );
		if ( !matcher.matches() ) {
			return new ParsedRuleSelection( facts.getContext().getTriggerText(), Set.of() );
		}

		return parseRuleSelection( matcher.group( 1 ), facts.getContext().getTriggerText() );
	}

	private static ParsedRuleSelection parseRuleSelection( String rawRuleText, String fallbackTriggerText ) {
		String rawSelection = stripReason( rawRuleText );
		if ( rawSelection == null || rawSelection.isBlank() ) {
			return new ParsedRuleSelection( fallbackTriggerText == null ? "" : fallbackTriggerText, Set.of() );
		}

		boolean			endsWithSeparator	= Character.isWhitespace( rawSelection.charAt( rawSelection.length() - 1 ) ) || rawSelection.endsWith( "," );
		List<String>	tokens				= List.of( rawSelection.trim().split( "[,\\s]+" ) ).stream()
		    .map( String::trim )
		    .filter( token -> !token.isEmpty() )
		    .toList();

		if ( tokens.isEmpty() ) {
			return new ParsedRuleSelection( "", Set.of() );
		}

		Set<String>	existingRuleIds	= new HashSet<>();
		int			exclusionLimit	= endsWithSeparator ? tokens.size() : tokens.size() - 1;
		for ( int i = 0; i < exclusionLimit; i++ ) {
			existingRuleIds.add( tokens.get( i ).toLowerCase( Locale.ROOT ) );
		}

		String triggerText = endsWithSeparator ? "" : tokens.getLast();
		return new ParsedRuleSelection( triggerText, existingRuleIds );
	}

	private static String stripReason( String rawRuleText ) {
		if ( rawRuleText == null ) {
			return null;
		}

		int reasonIndex = rawRuleText.indexOf( "--" );
		if ( reasonIndex < 0 ) {
			return rawRuleText;
		}

		return rawRuleText.substring( 0, reasonIndex );
	}

	private static MarkupContent toDocumentation( LintRuleCatalog.LintRuleInfo rule ) {
		MarkupContent documentation = new MarkupContent();
		documentation.setKind( "markdown" );
		documentation.setValue(
		    "**" + rule.id() + "**\n\n"
		        + rule.description()
		        + "\n\nDefault severity: `"
		        + rule.defaultSeverity()
		        + "`"
		);
		return documentation;
	}
}