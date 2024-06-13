package ortus.boxlanglsp.workspace.completion;

import org.eclipse.lsp4j.CompletionParams;

import ortus.boxlanglsp.workspace.ProjectContextProvider.FileParseResult;

public record CompletionFacts(FileParseResult fileParseResult, CompletionParams completionParams) {

}
