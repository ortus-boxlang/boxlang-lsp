package ortus.boxlang.lsp;

import java.util.List;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Diagnostic;

import ortus.boxlang.compiler.ast.visitor.VoidBoxVisitor;
import ortus.boxlang.lsp.workspace.FileParseResult;
import ortus.boxlang.lsp.workspace.MappingConfig;

public abstract class SourceCodeVisitor extends VoidBoxVisitor {

	protected String		filePath;
	protected MappingConfig	mappingConfig;

	public void setFilePath( String filePath ) {
		this.filePath = filePath;
	}

	public void setMappingConfig( MappingConfig mappingConfig ) {
		this.mappingConfig = mappingConfig;
	}

	public boolean canVisit( FileParseResult parseResult ) {
		return true;
	}

	public abstract List<Diagnostic> getDiagnostics();

	public abstract List<CodeAction> getCodeActions();
}
