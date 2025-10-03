package ortus.boxlang.lsp.workspace;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import ortus.boxlang.compiler.ast.BoxNode;

/**
 * Example integration showing how SymbolProvider can be integrated with
 * the existing ProjectContextProvider infrastructure.
 * 
 * This class demonstrates the integration points but is not activated by default.
 * To activate, integrate these methods into ProjectContextProvider.
 */
public class SymbolProviderIntegration {

	private final SymbolProvider symbolProvider;

	public SymbolProviderIntegration() {
		this.symbolProvider = new SymbolProvider();
	}

	/**
	 * Initialize the symbol provider with the workspace root.
	 * This should be called when the workspace is initialized.
	 */
	public void initializeWithWorkspace( Path workspaceRoot ) {
		symbolProvider.initialize( workspaceRoot );
	}

	/**
	 * Update symbols for a file when it's parsed.
	 * This should be called from FileParseResult or when documents are processed.
	 */
	public void updateSymbolsForFile( URI fileUri, BoxNode astRoot ) {
		if ( astRoot == null ) {
			// Remove symbols if parsing failed
			symbolProvider.removeSymbols( fileUri.toString() );
			return;
		}

		// Extract class symbols from the AST
		ClassSymbolExtractorVisitor visitor = new ClassSymbolExtractorVisitor( fileUri );
		astRoot.accept( visitor );

		List<ClassSymbol> classSymbols = visitor.getClassSymbols();

		// Update the symbol cache
		symbolProvider.addClassSymbols( fileUri.toString(), classSymbols );
	}

	/**
	 * Remove symbols for a file when it's closed or deleted.
	 */
	public void removeSymbolsForFile( URI fileUri ) {
		symbolProvider.removeSymbols( fileUri.toString() );
	}

	/**
	 * Find class symbols for code completion.
	 * This can be called from completion providers.
	 */
	public List<ClassSymbol> findClassSymbolsForCompletion( URI currentFileUri, String completionText ) {
		return symbolProvider.findClassSymbols( currentFileUri, completionText );
	}

	/**
	 * Get all cached class symbols.
	 * Useful for workspace-wide symbol searches.
	 */
	public List<ClassSymbol> getAllClassSymbols() {
		return symbolProvider.getAllClassSymbols();
	}

	/**
	 * Clear all cached symbols.
	 * Useful when the workspace is reset.
	 */
	public void clearAllSymbols() {
		symbolProvider.clearCache();
	}

	/**
	 * Example integration with FileParseResult.
	 * This shows how the symbol provider could be called when files are parsed.
	 */
	public static void integrateWithFileParseResult( FileParseResult parseResult, SymbolProviderIntegration integration ) {
		Optional<BoxNode> astRoot = parseResult.findAstRoot();
		if ( astRoot.isPresent() ) {
			integration.updateSymbolsForFile( parseResult.getURI(), astRoot.get() );
		} else {
			integration.removeSymbolsForFile( parseResult.getURI() );
		}
	}

	/**
	 * Example integration points for ProjectContextProvider:
	 * 
	 * 1. In ProjectContextProvider constructor or initialization:
	 * symbolProviderIntegration = new SymbolProviderIntegration();
	 * symbolProviderIntegration.initializeWithWorkspace(workspaceRoot);
	 * 
	 * 2. In trackDocumentOpen/trackDocumentSave methods:
	 * FileParseResult parseResult = getLatestFileParseResult(docUri);
	 * if (parseResult.isPresent()) {
	 * SymbolProviderIntegration.integrateWithFileParseResult(parseResult.get(), symbolProviderIntegration);
	 * }
	 * 
	 * 3. In trackDocumentClose method:
	 * symbolProviderIntegration.removeSymbolsForFile(docUri);
	 * 
	 * 4. In completion providers:
	 * List<ClassSymbol> classSymbols = symbolProviderIntegration.findClassSymbolsForCompletion(fileUri, completionText);
	 * // Convert ClassSymbol to CompletionItem instances
	 * 
	 * 5. For workspace symbol search:
	 * List<ClassSymbol> allSymbols = symbolProviderIntegration.getAllClassSymbols();
	 * // Filter and return as needed
	 */
}