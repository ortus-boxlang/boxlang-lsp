package ortus.boxlanglsp.workspace;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import ortus.boxlang.compiler.ast.BoxNode;
import ortus.boxlang.compiler.ast.Issue;
import ortus.boxlang.compiler.javaboxpiler.JavaBoxpiler;
import ortus.boxlang.compiler.parser.ParsingResult;
import ortus.boxlanglsp.DocumentSymbolBoxNodeVisitor;

public class ProjectContextProvider {

    static ProjectContextProvider instance;
    private Map<URI, List<Issue>> issues = new HashMap<URI, List<Issue>>();
    private Map<URI, List<Either<SymbolInformation, DocumentSymbol>>> outlines = new HashMap<URI, List<Either<SymbolInformation, DocumentSymbol>>>();
    private List<FunctionDefinition> functionDefinitions = new ArrayList<FunctionDefinition>();

    public static ProjectContextProvider getInstance() {
        if (instance == null) {
            instance = new ProjectContextProvider();
        }

        return instance;
    }

    public void consumeFile(URI textDocument) {
        Path path = Paths.get(textDocument);
        ParsingResult result = JavaBoxpiler.getInstance()
                .parse(path.toFile());

        result.getIssues();

        if (result.getIssues().size() > 0) {
            this.issues.put(textDocument, result.getIssues());
        } else {
            this.issues.remove(textDocument);
        }

        BoxNode root = result.getRoot();

        if (root != null) {
            generateOutline(textDocument, root);
            generateFunctionDefinitions(textDocument, root);
        }
    }

    public List<Either<SymbolInformation, DocumentSymbol>> getDocumentSymbols(URI fileURI) {
        try {

            if (!this.outlines.containsKey(fileURI)) {
                this.consumeFile(fileURI);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return this.outlines.get(fileURI);
    }

    public List<Location> findMatchingFunctionDeclerations(String functionName) {
        return this.functionDefinitions.stream()
                .filter((fn) -> fn.getFunctionName().equals(functionName))
                .map(FunctionDefinition::toLocation)
                .toList();
    }

    private void generateOutline(URI textDocument, BoxNode root) {
        DocumentSymbolBoxNodeVisitor visitor = new DocumentSymbolBoxNodeVisitor();

        visitor.setFilePath(Paths.get(textDocument));
        root.accept(visitor);

        this.outlines.put(textDocument, visitor.getDocumentSymbols());
    }

    private void generateFunctionDefinitions(URI textDocument, BoxNode root) {
        FunctionDefinitionVisitor visitor = new FunctionDefinitionVisitor();

        visitor.setFileURI(textDocument);
        root.accept(visitor);

        this.functionDefinitions.addAll(visitor.getFunctionDefinitions());
    }
}
