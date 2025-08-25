package ortus.boxlang.lsp.workspace.completion;

import org.eclipse.lsp4j.CompletionParams;

import ortus.boxlang.lsp.workspace.FileParseResult;

public record CompletionFacts( FileParseResult fileParseResult, CompletionParams completionParams ) {

}
