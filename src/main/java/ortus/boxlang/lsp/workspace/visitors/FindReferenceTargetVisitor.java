package ortus.boxlang.lsp.workspace.visitors;

import org.eclipse.lsp4j.Position;

import ortus.boxlang.compiler.ast.BoxNode;
import ortus.boxlang.compiler.ast.expression.BoxFunctionInvocation;
import ortus.boxlang.compiler.ast.visitor.VoidBoxVisitor;

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
		if ( !containsPosition( node ) ) {
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

	private boolean containsPosition( BoxNode node ) {
		ortus.boxlang.compiler.ast.Position nodePos = node.getPosition();

		if ( nodePos == null ) {
			return false;
		}

		int	boxStartLine	= nodePos.getStart().getLine();
		int	boxStartCol		= nodePos.getStart().getColumn();
		int	boxEndLine		= nodePos.getEnd().getLine();
		int	boxEndCol		= nodePos.getEnd().getColumn();

		if ( node instanceof BoxFunctionInvocation bfi ) {
			boxEndLine	= boxStartLine;
			boxEndCol	= boxStartCol + bfi.getName().length();
		}

		return ! ( line < boxStartLine
		    || ( line == boxStartLine && column <= boxStartCol )
		    || line > boxEndLine
		    || ( line == boxEndLine && column >= boxEndCol ) );
	}
}
