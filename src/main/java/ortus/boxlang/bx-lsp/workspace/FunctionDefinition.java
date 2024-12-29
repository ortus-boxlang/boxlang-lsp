package ortus.boxlanglsp.workspace;

import java.net.URI;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import ortus.boxlang.compiler.ast.Point;
import ortus.boxlang.compiler.ast.statement.BoxFunctionDeclaration;

public class FunctionDefinition {

    private URI fileURI;
    private BoxFunctionDeclaration ASTNode;

    public String getFunctionName() {
        return ASTNode.getName();
    }

    public URI getFileURI() {
        return fileURI;
    }

    public void setFileURI(URI fileURI) {
        this.fileURI = fileURI;
    }

    public BoxFunctionDeclaration getASTNode() {
        return ASTNode;
    }

    public void setASTNode(BoxFunctionDeclaration aSTNode) {
        ASTNode = aSTNode;
    }

    public static Location toLocation(FunctionDefinition fn) {
        Location loc = new Location();

        ortus.boxlang.compiler.ast.Position pos = fn.getASTNode().getPosition();
        Point start = pos.getStart();
        Point end = pos.getEnd();

        loc.setUri(fn.getFileURI().toString());
        loc.setRange(new Range(
                new Position(start.getLine() - 1, start.getColumn()),
                new Position(end.getLine() - 1, end.getColumn() + 1)));

        return loc;
    }
}
