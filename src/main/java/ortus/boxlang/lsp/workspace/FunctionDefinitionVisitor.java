package ortus.boxlang.lsp.workspace;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import ortus.boxlang.compiler.ast.BoxNode;
import ortus.boxlang.compiler.ast.statement.BoxFunctionDeclaration;
import ortus.boxlang.compiler.ast.visitor.VoidBoxVisitor;

public class FunctionDefinitionVisitor extends VoidBoxVisitor {

    private List<FunctionDefinition> functionDefinitions = new ArrayList<FunctionDefinition>();

    public List<FunctionDefinition> getFunctionDefinitions() {
        return functionDefinitions;
    }

    public void setFunctionDefinitions( List<FunctionDefinition> functionDefinitions ) {
        this.functionDefinitions = functionDefinitions;
    }

    public URI getFileURI() {
        return fileURI;
    }

    public void setFileURI( URI fileURI ) {
        this.fileURI = fileURI;
    }

    private URI fileURI;

    public void visit( BoxFunctionDeclaration node ) {
        FunctionDefinition func = new FunctionDefinition();
        func.setFileURI( fileURI );
        func.setASTNode( node );
        this.functionDefinitions.add( func );
        visitChildren( node );
    }

    private void visitChildren( BoxNode node ) {
        for ( BoxNode child : node.getChildren() ) {
            child.accept( this );
        }
    }
}
