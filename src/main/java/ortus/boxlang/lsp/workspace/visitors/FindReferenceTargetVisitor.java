package ortus.boxlang.lsp.workspace.visitors;

import org.eclipse.lsp4j.Position;

import ortus.boxlang.compiler.ast.BoxNode;
import ortus.boxlang.compiler.ast.visitor.VoidBoxVisitor;
import ortus.boxlang.lsp.workspace.BLASTTools;

public class FindReferenceTargetVisitor extends VoidBoxVisitor {

	private BoxNode			referenceTarget;
	private final Position	cursorPosition;
	private int				line;
	private int				column;

	public FindReferenceTargetVisitor( Position cursorPosition ) {
		this.cursorPosition	= cursorPosition;
		this.line			= this.cursorPosition.getLine() + 1;
		this.column			= this.cursorPosition.getCharacter();
	}

	public BoxNode getReferenceTarget() {
		return referenceTarget;
	}

	public void visit( ortus.boxlang.compiler.ast.statement.BoxFunctionDeclaration node ) {
		if ( !BLASTTools.containsPosition( node, line, column ) ) {
			visitChildren( node );
			return;
		}

		this.referenceTarget = node;
	}

	private void visitChildren( BoxNode node ) {
		for ( BoxNode child : node.getChildren() ) {
			child.accept( this );
		}
	}
}
