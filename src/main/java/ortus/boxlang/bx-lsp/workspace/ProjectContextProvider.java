package ortus.boxlanglsp.workspace;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;

import ortus.boxlang.compiler.ast.BoxNode;
import ortus.boxlang.compiler.ast.Issue;
import ortus.boxlang.compiler.ast.Point;
import ortus.boxlang.compiler.ast.expression.BoxFunctionInvocation;
import ortus.boxlang.compiler.ast.expression.BoxMethodInvocation;
import ortus.boxlang.compiler.ast.visitor.PrettyPrintBoxVisitor;
import ortus.boxlang.compiler.javaboxpiler.JavaBoxpiler;
import ortus.boxlang.compiler.parser.Parser;
import ortus.boxlang.compiler.parser.ParsingResult;
import ortus.boxlanglsp.DocumentSymbolBoxNodeVisitor;
import ortus.boxlanglsp.LSPTools;
import ortus.boxlanglsp.workspace.completion.CompletionFacts;
import ortus.boxlanglsp.workspace.completion.CompletionProviderRuleBook;
import ortus.boxlanglsp.workspace.types.ParsedProperty;
import ortus.boxlanglsp.workspace.visitors.DefinitionTargetVisitor;
import ortus.boxlanglsp.workspace.visitors.FunctionReturnDiagnosticVisitor;
import ortus.boxlanglsp.workspace.visitors.PropertyVisitor;

public class ProjectContextProvider {

    static ProjectContextProvider instance;
    private LanguageClient client;
    private Map<URI, FileParseResult> parsedFiles = new HashMap<URI, FileParseResult>();
    private Map<URI, BoxNode> astCache = new HashMap<URI, BoxNode>();
    private List<FunctionDefinition> functionDefinitions = new ArrayList<FunctionDefinition>();
    private Map<URI, OpenDocument> openDocuments = new HashMap<URI, OpenDocument>();

    private boolean shouldPublishDiagnostics = false;

    record OpenDocument(
            URI uri,
            String latestContent) {

    }

    public record FileParseResult(
            URI uri,
            BoxNode astRoot,
            List<Issue> issues,
            List<Either<SymbolInformation, DocumentSymbol>> outline,
            List<ParsedProperty> properties) {

        public boolean isTemplate() {
            return uri.toString().endsWith(".bxm");
        }

        public boolean isClass() {
            return uri.toString().endsWith(".bx");
        }
    }

    public static String readLine(String docURI, int lineNumber) {
        return readLine(LSPTools.convertDocumentURI(docURI), lineNumber);
    }

    public static String readLine(URI docURI, int lineNumber) {
        ProjectContextProvider instance = getInstance();
        Stream<String> lineStream = Stream.ofNullable(null);

        if (instance.openDocuments.containsKey(docURI)) {
            OpenDocument doc = instance.openDocuments.get(docURI);
            lineStream = List.of(doc.latestContent.split("\n"))
                    .stream();
        } else if (instance.parsedFiles.containsKey(docURI) && instance.parsedFiles.get(docURI).astRoot() != null) {
            lineStream = List.of(instance.parsedFiles.get(docURI).astRoot().getSourceText().split("\n")).stream();
        } else {
            try {
                lineStream = Files.lines(Path.of(docURI));
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        return lineStream.skip(lineNumber).findFirst().get();
    }

    public static ortus.boxlang.compiler.ast.Position toBLPosition(Position lspPosition) {
        Point start = new Point(lspPosition.getLine(), lspPosition.getCharacter());
        ortus.boxlang.compiler.ast.Position BLPos = new ortus.boxlang.compiler.ast.Position(start, start);

        return BLPos;
    }

    public static Range positionToRange(ortus.boxlang.compiler.ast.Position pos) {
        return new Range(
                new Position(pos.getStart().getLine() - 1, pos.getStart().getColumn()),
                new Position(pos.getEnd().getLine() - 1, pos.getEnd().getColumn()));
    }

    public static ProjectContextProvider getInstance() {
        if (instance == null) {
            instance = new ProjectContextProvider();
        }

        return instance;
    }

    public void setShouldPublishDiagnostics(boolean shouldPublishDiagnostics) {
        if (this.shouldPublishDiagnostics == shouldPublishDiagnostics) {
            return;
        }

        this.shouldPublishDiagnostics = shouldPublishDiagnostics;

        this.parsedFiles.keySet()
                .stream()
                .parallel()
                .forEach((uri) -> publishDiagnostics(uri));
    }

    public void setLanguageClient(LanguageClient client) {
        this.client = client;
    }

    public List<? extends TextEdit> formatDocument(URI docUri) {
        ParsingResult res = this.getLatestParsingResult(docUri);

        List<TextEdit> edits = new ArrayList<TextEdit>();

        PrettyPrintBoxVisitor prettyPrintBoxVisitor = new PrettyPrintBoxVisitor();

        res.getRoot().accept(prettyPrintBoxVisitor);

        if (res.getRoot() != null) {
            Range range = positionToRange(res.getRoot().getPosition());
            range.getStart().setLine(0);
            range.getStart().setCharacter(0);
            range.getEnd().setLine(range.getEnd().getLine() + 1);
            edits.add(new TextEdit(range,
                    prettyPrintBoxVisitor.getOutput()));
        }

        return edits;
    }

    private FileParseResult consumeOrGet(URI docUri) {
        if (!this.parsedFiles.containsKey(docUri)) {
            this.consumeFile(docUri);
        }

        return this.parsedFiles.get(docUri);
    }

    public void trackDocumentChange(URI docUri, List<TextDocumentContentChangeEvent> changes) {
        for (TextDocumentContentChangeEvent change : changes) {
            if (change.getRange() == null) {
                this.openDocuments.put(docUri, new OpenDocument(docUri, change.getText()));
            }
        }

        this.consumeFile(docUri);
    }

    public void trackDocumentSave(URI docUri, String text) {
        String fileContent = text;

        if (fileContent == null) {
            try {
                fileContent = Files.readString(Paths.get(docUri));
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        this.openDocuments.put(docUri, new OpenDocument(docUri, fileContent));

        this.consumeFile(docUri);
    }

    public void trackDocumentOpen(URI docUri, String text) {
        this.openDocuments.put(docUri, new OpenDocument(docUri, text));

        this.consumeFile(docUri);
    }

    public void trackDocumentClose(URI docUri) {
        this.openDocuments.remove(docUri);

        this.consumeFile(docUri);
    }

    private ParsingResult getLatestParsingResult(URI docUri) {
        if (this.openDocuments.containsKey(docUri)) {
            return JavaBoxpiler.getInstance().parse(
                    this.openDocuments.get(docUri).latestContent(),
                    Parser.detectFile(new File(docUri)),
                    Parser.getFileExtension(docUri.toString()).orElseGet(() -> "bxs").equals("bx"));
        }

        return JavaBoxpiler.getInstance().parse(Paths.get(docUri).toFile());
    }

    public FileParseResult consumeFile(URI docUri) {
        ParsingResult result = getLatestParsingResult(docUri);

        BoxNode root = result.getRoot();

        FileParseResult res;

        if (root == null) {
            res = new FileParseResult(docUri, null, result.getIssues(), null, null);
        } else {
            res = new FileParseResult(
                    docUri,
                    root,
                    result.getIssues(),
                    generateOutline(docUri, root),
                    parseProperties(root));
            generateOutline(docUri, root);

            generateFunctionDefinitions(docUri, root);
        }

        this.parsedFiles.put(docUri, res);

        publishDiagnostics(docUri);

        return res;
    }

    public Optional<List<Either<SymbolInformation, DocumentSymbol>>> getDocumentSymbols(URI docURI) {
        return Optional.ofNullable(this.parsedFiles.get(docURI))
                .or(() -> Optional.of(this.consumeFile(docURI)))
                .map((res) -> res.outline);
    }

    public List<Location> findMatchingFunctionDeclerations(URI docURI, String functionName) {
        return this.functionDefinitions.stream()
                .filter((fn) -> fn.getFunctionName().equals(functionName) && docURI.equals(fn.getFileURI()))
                .map(FunctionDefinition::toLocation)
                .toList();
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
                        // should only be able to find function definitions in these locations
                        // same file
                        // global - should this be found? or what should we show?
                        // parent class
                        return findMatchingFunctionDeclerations(docURI, fnUse.getName());
                    } else if (node instanceof BoxMethodInvocation methodUse) {
                        // should only be able to find function definitions in these locations
                        // the type being accessed
                        // a parent of the type being accessed
                        // member function versions of BIFs if the type matches
                        // this -> same rules as BoxFunctionInvocation
                    }

                    return new ArrayList<Location>();
                })
                .orElseGet(() -> new ArrayList<Location>());
    }

    public List<CompletionItem> getAvaialbeCompletions(URI docURI, CompletionParams params) {
        FileParseResult res = consumeOrGet(docURI);

        // TODO if you are in a cfscript component within a template script completions
        // TODO if you are in a cfset return script completions
        // TODO add completions for in-scope symbols (properties, local variables,

        return CompletionProviderRuleBook.execute(new CompletionFacts(res, params));
    }

    private List<ParsedProperty> parseProperties(BoxNode root) {
        PropertyVisitor visitor = new PropertyVisitor();

        root.accept(visitor);

        return visitor.getProperties();
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

        this.functionDefinitions = this.functionDefinitions.stream().filter((fnDef) -> {
            return !fnDef.getFileURI().equals(textDocument);
        })
                .collect(Collectors.toList());

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

    private void publishDiagnostics(URI docURI) {
        if (this.client == null) {
            return;
        }

        FileParseResult res = this.parsedFiles.get(docURI);

        PublishDiagnosticsParams diagnositcParams = new PublishDiagnosticsParams();

        diagnositcParams.setUri(docURI.toString());

        List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();
        diagnositcParams.setDiagnostics(diagnostics);

        if (!this.shouldPublishDiagnostics) {
            this.client.publishDiagnostics(diagnositcParams);
            return;
        }

        diagnostics.addAll(res.issues().stream().map((issue) -> {
            Diagnostic diagnostic = new Diagnostic();

            diagnostic.setSeverity(DiagnosticSeverity.Error);
            diagnostic.setMessage(issue.getMessage());

            diagnostic.setRange(positionToRange(issue.getPosition()));

            diagnostic.setMessage(issue.getMessage());

            return diagnostic;
        }).toList());

        if (res.astRoot() != null) {
            FunctionReturnDiagnosticVisitor returnDiagnosticVisitor = new FunctionReturnDiagnosticVisitor();
            res.astRoot().accept(returnDiagnosticVisitor);
            diagnostics.addAll(returnDiagnosticVisitor.getDiagnostics());
        }

        this.client.publishDiagnostics(diagnositcParams);
    }

}
