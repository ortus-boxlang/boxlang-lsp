package ortus.boxlang.lsp.workspace.visitors;

import org.eclipse.lsp4j.Position;

import ortus.boxlang.compiler.ast.BoxNode;
import ortus.boxlang.compiler.ast.expression.BoxFunctionInvocation;
import ortus.boxlang.compiler.ast.expression.BoxMethodInvocation;
import ortus.boxlang.compiler.ast.visitor.VoidBoxVisitor;
import ortus.boxlang.lsp.workspace.BLASTTools;

public class FindDefinitionTargetVisitor extends VoidBoxVisitor {

	private BoxNode			definitionTarget;
	private final Position	cursorPosition;
	private int				line;
	private int				column;

	public FindDefinitionTargetVisitor( Position cursorPosition ) {
		this.cursorPosition	= cursorPosition;
		this.line			= this.cursorPosition.getLine() + 1;
		this.column			= this.cursorPosition.getCharacter();
	}

	public BoxNode getDefinitionTarget() {
		return definitionTarget;
	}

	public void visit( BoxMethodInvocation node ) {
		if ( !BLASTTools.containsPosition( node, line, column ) ) {
			visitChildren( node );
			return;
		}

		this.definitionTarget = node;
	}

	public void visit( BoxFunctionInvocation node ) {
		if ( !BLASTTools.containsPosition( node, line, column ) ) {
			visitChildren( node );
			return;
		}

		this.definitionTarget = node;
	}

	private void visitChildren( BoxNode node ) {
		for ( BoxNode child : node.getChildren() ) {
			child.accept( this );
		}
	}
}
