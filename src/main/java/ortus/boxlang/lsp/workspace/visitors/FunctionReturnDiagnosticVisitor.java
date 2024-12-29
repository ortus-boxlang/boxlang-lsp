package ortus.boxlang.lsp.workspace.visitors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.tools.Diagnostic;

import org.eclipse.lsp4j.DiagnosticSeverity;

import ortus.boxlang.compiler.ast.BoxNode;
import ortus.boxlang.compiler.ast.statement.BoxFunctionDeclaration;
import ortus.boxlang.compiler.ast.statement.BoxReturn;
import ortus.boxlang.compiler.ast.statement.BoxType;
import ortus.boxlang.compiler.ast.visitor.VoidBoxVisitor;
import ortus.boxlang.lsp.workspace.ExpressionTypeResolver;
import ortus.boxlang.lsp.workspace.ProjectContextProvider;
import ortus.boxlang.runtime.types.BoxLangType;

public class FunctionReturnDiagnosticVisitor extends VoidBoxVisitor {

    private List<BoxFunctionDeclaration> funcStack   = new ArrayList<BoxFunctionDeclaration>();

    private List<Diagnostic>             diagnostics = new ArrayList<Diagnostic>();

    public List<Diagnostic> getDiagnostics() {
        return this.diagnostics;
    }

    public void visit( BoxReturn node ) {
        if ( this.funcStack.size() == 0 ) {
            return;
        }
        BoxFunctionDeclaration currentFunc = this.funcStack.get( this.funcStack.size() - 1 );

        if ( currentFunc.getType() == null ) {
            // TODO add support for void type
            // currently it is just stored as null and we cannot distinguish it from an
            // unspecififed return type
            visitChildren( node );
            return;
        }

        // TODO when return type is any all code paths should have a return statement
        // TODO when return type is void no code paths should return a value
        // TODO when the return type is a specific type all code paths should return a
        // value that matches that type
        var found = Arrays.asList(
            checkVoidTriesToReturnValue( node ),
            checkMismatchedReturnValue( node ) ).stream()
            .filter( d -> d != null )
            .toList();

        this.diagnostics.addAll( found );

    }

    public void visit( BoxFunctionDeclaration node ) {
        funcStack.add( node );

        visitChildren( node );

        funcStack.remove( node );
    }

    private Diagnostic checkMismatchedReturnValue( BoxReturn node ) {
        BoxType     declaredReturnType = this.funcStack.get( this.funcStack.size() - 1 ).getType().getType();
        BoxLangType returnValueType    = ExpressionTypeResolver.determineType( node.getExpression() );

        if ( declaredReturnType == BoxType.String
            && ( returnValueType == BoxLangType.STRING || returnValueType == BoxLangType.ANY ) ) {
            return null;
        }
        if ( declaredReturnType == BoxType.String && returnValueType == BoxLangType.NUMERIC ) {
            // TODO provide a setting in vscode that will control "strict type warnings"
            return new Diagnostic(
                ProjectContextProvider.positionToRange( node.getPosition() ),
                "Consider changing the return type of this function or converting the returned value to a string.",
                DiagnosticSeverity.Warning,
                "boxlang" );
        }
        if ( declaredReturnType == BoxType.String && returnValueType != BoxLangType.STRING ) {
            return new Diagnostic(
                ProjectContextProvider.positionToRange( node.getPosition() ),
                "The function must return a string value",
                DiagnosticSeverity.Error,
                "boxlang" );
        }

        return null;
    }

    private Diagnostic checkVoidTriesToReturnValue( BoxReturn node ) {
        if ( ! ( this.funcStack.get( this.funcStack.size() - 1 ).getType().getType() == BoxType.Void
            && node.getExpression() != null ) ) {
            return null;
        }

        return new Diagnostic(
            ProjectContextProvider.positionToRange( node.getPosition() ),
            "A void function may not return a value",
            DiagnosticSeverity.Error,
            "boxlang" );
    }

    private void visitChildren( BoxNode node ) {
        for ( BoxNode child : node.getChildren() ) {
            child.accept( this );
        }
    }
}
