package ortus.boxlang.lsp.workspace.codeLens;

import org.eclipse.lsp4j.CodeLensParams;

import ortus.boxlang.lsp.workspace.ProjectContextProvider.FileParseResult;

public record CodeLensFacts( FileParseResult fileParseResult, CodeLensParams codeLensParams ) {

}
