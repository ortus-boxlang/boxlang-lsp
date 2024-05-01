package ortus.boxlanglsp.workspace;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import ortus.boxlang.compiler.ast.BoxNode;
import ortus.boxlang.compiler.ast.Issue;
import ortus.boxlang.compiler.ast.Point;
import ortus.boxlang.compiler.ast.expression.BoxFunctionInvocation;
import ortus.boxlang.compiler.javaboxpiler.JavaBoxpiler;
import ortus.boxlang.compiler.parser.ParsingResult;
import ortus.boxlanglsp.DocumentSymbolBoxNodeVisitor;
import ortus.boxlanglsp.workspace.visitors.DefinitionTargetVisitor;

public class ProjectContextProvider {

    static ProjectContextProvider instance;
    private Map<URI, FileParseResult> parsedFiles = new HashMap<URI, FileParseResult>();
    private Map<URI, BoxNode> astCache = new HashMap<URI, BoxNode>();
    private List<FunctionDefinition> functionDefinitions = new ArrayList<FunctionDefinition>();

    record FileParseResult(
            URI uri,
            BoxNode astRoot,
            List<Issue> issues,
            List<Either<SymbolInformation, DocumentSymbol>> outline) {

    }

    public static ortus.boxlang.compiler.ast.Position toBLPosition(Position lspPosition) {
        Point start = new Point(lspPosition.getLine(), lspPosition.getCharacter());
        ortus.boxlang.compiler.ast.Position BLPos = new ortus.boxlang.compiler.ast.Position(start, start);

        return BLPos;
    }

    public static ProjectContextProvider getInstance() {
        if (instance == null) {
            instance = new ProjectContextProvider();
        }

        return instance;
    }

    public FileParseResult consumeFile(URI textDocument) {
        Path path = Paths.get(textDocument);
        ParsingResult result = JavaBoxpiler.getInstance()
                .parse(path.toFile());

        BoxNode root = result.getRoot();

        FileParseResult res;

        if (root == null) {
            res = new FileParseResult(textDocument, null, result.getIssues(), null);
        } else {
            res = new FileParseResult(
                    textDocument,
                    root,
                    result.getIssues(),
                    generateOutline(textDocument, root));
            generateOutline(textDocument, root);

            generateFunctionDefinitions(textDocument, root);
        }

        this.parsedFiles.put(textDocument, res);

        return res;
    }

    public Optional<List<Either<SymbolInformation, DocumentSymbol>>> getDocumentSymbols(URI docURI) {
        return Optional.ofNullable(this.parsedFiles.get(docURI))
                .or(() -> Optional.of(this.consumeFile(docURI)))
                .map((res) -> res.outline);
    }

    public List<Location> findMatchingFunctionDeclerations(String functionName) {
        return this.functionDefinitions.stream()
                .filter((fn) -> fn.getFunctionName().equals(functionName))
                .map(FunctionDefinition::toLocation)
                .toList();
    }

    public List<Location> findDefinitionPossibiltiies(URI docURI, Position pos) {
        return findDefinitionTarget(docURI, pos)
                .map((node) -> {
                    if (node instanceof BoxFunctionInvocation fnUse) {
                        return findMatchingFunctionDeclerations(fnUse.getName());
                    }

                    return new ArrayList<Location>();
                })
                .orElseGet(() -> new ArrayList<Location>());
    }

    private List<Either<SymbolInformation, DocumentSymbol>> generateOutline(URI textDocument, BoxNode root) {
        DocumentSymbolBoxNodeVisitor visitor = new DocumentSymbolBoxNodeVisitor();

        visitor.setFilePath(Paths.get(textDocument));
        root.accept(visitor);

        return visitor.getDocumentSymbols();
    }

    private void generateFunctionDefinitions(URI textDocument, BoxNode root) {
        FunctionDefinitionVisitor visitor = new FunctionDefinitionVisitor();

        visitor.setFileURI(textDocument);
        root.accept(visitor);

        this.functionDefinitions.addAll(visitor.getFunctionDefinitions());
    }

    public Optional<BoxNode> findDefinitionTarget(URI docURI, Position position) {
        return Optional.ofNullable(this.parsedFiles.get(docURI))
                .or(() -> Optional.of(this.consumeFile(docURI)))
                .map((res) -> res.astRoot())
                .map((rootNode) -> {
                    return searchForCursorTarget(rootNode, position);
                });
    }

    private BoxNode searchForCursorTarget(BoxNode root, Position position) {
        DefinitionTargetVisitor visitor = new DefinitionTargetVisitor(position);

        root.accept(visitor);

        return visitor.getDefinitionTarget();
    }
}
