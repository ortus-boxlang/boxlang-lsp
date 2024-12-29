package ortus.boxlanglsp.workspace.visitors;

import org.eclipse.lsp4j.Position;

import ortus.boxlang.compiler.ast.BoxNode;
import ortus.boxlang.compiler.ast.expression.BoxFunctionInvocation;
import ortus.boxlang.compiler.ast.expression.BoxMethodInvocation;
import ortus.boxlang.compiler.ast.visitor.VoidBoxVisitor;

public class DefinitionTargetVisitor extends VoidBoxVisitor {

    private BoxNode definitionTarget;
    private final Position cursorPosition;
    private int line;
    private int column;

    public DefinitionTargetVisitor(Position cursorPosition) {
        this.cursorPosition = cursorPosition;
        this.line = this.cursorPosition.getLine() + 1;
        this.column = this.cursorPosition.getCharacter();
    }

    public BoxNode getDefinitionTarget() {
        return definitionTarget;
    }

    public void visit(BoxMethodInvocation node) {
        visitChildren(node);
    }

    public void visit(BoxFunctionInvocation node) {
        if (!containsPosition(node)) {
            visitChildren(node);
            return;
        }

        this.definitionTarget = node;
    }

    // public void visit(BoxFunctionDeclaration node) {
    // if (!containsPosition(node)) {
    // return;
    // }

    // this.definitionTarget = node;
    // }

    private void visitChildren(BoxNode node) {
        for (BoxNode child : node.getChildren()) {
            child.accept(this);
        }
    }

    private boolean containsPosition(BoxNode node) {
        ortus.boxlang.compiler.ast.Position nodePos = node.getPosition();
        int boxStartLine = nodePos.getStart().getLine();
        int boxStartCol = nodePos.getStart().getColumn();
        int boxEndLine = nodePos.getEnd().getLine();
        int boxEndCol = nodePos.getEnd().getColumn();

        if (node instanceof BoxFunctionInvocation bfi) {
            boxEndLine = boxStartLine;
            boxEndCol = boxStartCol + bfi.getName().length();
        }

        return !(line < boxStartLine
                || (line == boxStartLine && column <= boxStartCol)
                || line > boxEndLine
                || (line == boxEndLine && column >= boxEndCol));
    }
}
