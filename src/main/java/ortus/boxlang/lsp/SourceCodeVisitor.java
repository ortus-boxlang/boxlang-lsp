package ortus.boxlang.lsp;

import java.util.List;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Diagnostic;

import ortus.boxlang.compiler.ast.visitor.VoidBoxVisitor;

public abstract class SourceCodeVisitor extends VoidBoxVisitor {

	protected String filePath;

	public void setFilePath( String filePath ) {
		this.filePath = filePath;
	}

	public abstract List<Diagnostic> getDiagnostics();

	public abstract List<CodeAction> getCodeActions();
}
