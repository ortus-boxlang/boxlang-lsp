package ortus.boxlang.lsp.workspace.completion;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionItemLabelDetails;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;

import ortus.boxlang.compiler.ast.BoxNode;
import ortus.boxlang.compiler.ast.statement.BoxImport;
import ortus.boxlang.lsp.workspace.ProjectContextProvider;
import ortus.boxlang.lsp.workspace.index.IndexedClass;
import ortus.boxlang.lsp.workspace.index.ProjectIndex;
import ortus.boxlang.lsp.workspace.rules.IRule;

/**
 * Completion rule that suggests class and interface names in appropriate contexts:
 * - After `new` keyword (classes only)
 * - After `extends` keyword (classes only)
 * - After `implements` keyword (interfaces only)
 *
 * Also provides auto-import functionality when completing unimported types.
 */
public class ClassAndTypeCompletionRule implements IRule<CompletionFacts, List<CompletionItem>> {

	@Override
	public boolean when( CompletionFacts facts ) {
		// This rule applies to new, extends, and implements contexts
		return !facts.fileParseResult().isTemplate()
		    && ( ContextChecker.isNewExpression( facts )
		        || ContextChecker.isExtendsExpression( facts )
		        || ContextChecker.isImplementsExpression( facts )
		        || isInExtendsAttribute( facts )
		        || isInImplementsAttribute( facts ) );
	}

	/**
	 * Check if cursor is inside an extends="..." attribute.
	 */
	private boolean isInExtendsAttribute( CompletionFacts facts ) {
		int		line		= facts.completionParams().getPosition().getLine();
		String	lineText	= facts.fileParseResult().readLine( line );
		int		col			= facts.completionParams().getPosition().getCharacter();
		String	beforeCursor = lineText.substring( 0, Math.min( col, lineText.length() ) );

		// Check if we're inside extends="..."
		// Pattern: extends="partial or extends=" or class extends="
		return beforeCursor.matches( ".*\\bextends\\s*=\\s*\"[^\"]*$" );
	}

	/**
	 * Check if cursor is inside an implements="..." attribute.
	 */
	private boolean isInImplementsAttribute( CompletionFacts facts ) {
		int		line		= facts.completionParams().getPosition().getLine();
		String	lineText	= facts.fileParseResult().readLine( line );
		int		col			= facts.completionParams().getPosition().getCharacter();
		String	beforeCursor = lineText.substring( 0, Math.min( col, lineText.length() ) );

		// Check if we're inside implements="..."
		// Pattern: implements="partial or implements=" or class implements="
		return beforeCursor.matches( ".*\\bimplements\\s*=\\s*\"[^\"]*$" );
	}

	@Override
	public void then( CompletionFacts facts, List<CompletionItem> result ) {
		CompletionContext	context			= facts.getContext();
		String				prefix			= context.getTriggerText();
		ProjectIndex		index			= ProjectContextProvider.getInstance().getIndex();

		// Get all classes from the index
		List<IndexedClass>	allClasses		= index.getAllClasses();

		// Determine if we should filter for interfaces or classes
		boolean				onlyInterfaces	= ContextChecker.isImplementsExpression( facts ) || isInImplementsAttribute( facts );
		boolean				onlyClasses		= ContextChecker.isNewExpression( facts ) || ContextChecker.isExtendsExpression( facts ) || isInExtendsAttribute( facts );

		// Filter based on context and prefix
		List<IndexedClass> filteredClasses = allClasses.stream()
		    .filter( cls -> matchesContextRequirements( cls, onlyInterfaces, onlyClasses ) )
		    .filter( cls -> matchesPrefix( cls, prefix ) )
		    .collect( Collectors.toList() );

		// Get currently imported classes to check if auto-import is needed
		List<String> currentImports = getImportedClasses( facts );

		// Convert to completion items
		for ( IndexedClass indexedClass : filteredClasses ) {
			CompletionItem item = createCompletionItem( indexedClass, facts, currentImports );
			result.add( item );
		}
	}

	/**
	 * Check if a class matches the context requirements (interface vs class).
	 */
	private boolean matchesContextRequirements( IndexedClass cls, boolean onlyInterfaces, boolean onlyClasses ) {
		if ( onlyInterfaces ) {
			return cls.isInterface();
		}
		if ( onlyClasses ) {
			return !cls.isInterface();
		}
		return true;
	}

	/**
	 * Check if a class matches the prefix filter.
	 * Supports both simple names and dotted package paths:
	 * - "User" matches classes named "User*"
	 * - "models.User" matches classes with FQN "models.User*"
	 * - "models." matches all classes in "models" package
	 */
	private boolean matchesPrefix( IndexedClass cls, String prefix ) {
		if ( prefix == null || prefix.isEmpty() ) {
			return true;
		}

		// If prefix contains dots, it's a package-qualified prefix
		if ( prefix.contains( "." ) ) {
			// Match against the fully qualified name
			return cls.fullyQualifiedName().toLowerCase().startsWith( prefix.toLowerCase() );
		}

		// Otherwise, match on simple class name only
		return cls.name().toLowerCase().startsWith( prefix.toLowerCase() );
	}

	/**
	 * Create a completion item for a class.
	 */
	private CompletionItem createCompletionItem( IndexedClass indexedClass, CompletionFacts facts, List<String> currentImports ) {
		CompletionItem item = new CompletionItem();

		// Set label to simple class name
		item.setLabel( indexedClass.name() );

		// Set kind based on whether it's an interface or class
		item.setKind( indexedClass.isInterface() ? CompletionItemKind.Interface : CompletionItemKind.Class );

		// Set detail to show the fully qualified name
		item.setDetail( indexedClass.fullyQualifiedName() );

		// Add label details showing the package/file location
		CompletionItemLabelDetails labelDetails = new CompletionItemLabelDetails();
		String packagePath = getPackagePathFromFQN( indexedClass.fullyQualifiedName() );
		if ( packagePath != null && !packagePath.isEmpty() ) {
			labelDetails.setDescription( packagePath );
		}
		item.setLabelDetails( labelDetails );

		// Set insert text format
		item.setInsertTextFormat( InsertTextFormat.PlainText );
		item.setInsertText( indexedClass.name() );

		// Add documentation if available
		if ( indexedClass.documentation() != null && !indexedClass.documentation().isEmpty() ) {
			item.setDocumentation( indexedClass.documentation() );
		}

		// Sort by package proximity and then alphabetically
		String sortText = calculateSortText( indexedClass, facts );
		item.setSortText( sortText );

		// Add auto-import if needed
		if ( !isAlreadyImported( indexedClass, currentImports, facts ) ) {
			addAutoImport( item, indexedClass, facts );
		}

		return item;
	}

	/**
	 * Extract the package path from a fully qualified name.
	 * For example, "subpackage.ProductRepository" -> "subpackage"
	 */
	private String getPackagePathFromFQN( String fqn ) {
		int lastDot = fqn.lastIndexOf( '.' );
		if ( lastDot > 0 ) {
			return fqn.substring( 0, lastDot );
		}
		return "";
	}

	/**
	 * Calculate sort text for a class to prioritize closer/more relevant completions.
	 * - Classes in same package get priority (prefix "0")
	 * - Classes in root package get next priority (prefix "1")
	 * - Classes in subpackages get lower priority (prefix "2")
	 * - Then alphabetically by name
	 */
	private String calculateSortText( IndexedClass indexedClass, CompletionFacts facts ) {
		String currentFilePackage = getCurrentFilePackage( facts );
		String classPackage = getPackagePathFromFQN( indexedClass.fullyQualifiedName() );

		String priority;
		if ( classPackage.equals( currentFilePackage ) ) {
			priority = "0"; // Same package
		} else if ( classPackage.isEmpty() ) {
			priority = "1"; // Root package
		} else {
			priority = "2"; // Other package
		}

		return priority + "_" + indexedClass.name().toLowerCase();
	}

	/**
	 * Get the package of the current file being edited.
	 */
	private String getCurrentFilePackage( CompletionFacts facts ) {
		try {
			URI		fileUri		= URI.create( facts.fileParseResult().getURI().toString() );
			Path	filePath	= Paths.get( fileUri );
			Path	workspaceRoot = ProjectContextProvider.getInstance().getIndex().getWorkspaceRoot();

			if ( workspaceRoot != null && filePath.startsWith( workspaceRoot ) ) {
				Path	relativePath	= workspaceRoot.relativize( filePath );
				Path	parentPath		= relativePath.getParent();
				if ( parentPath != null ) {
					return parentPath.toString().replace( java.io.File.separator, "." );
				}
			}
		} catch ( Exception e ) {
			// If we can't determine package, just return empty
		}
		return "";
	}

	/**
	 * Get the list of classes currently imported in the file.
	 */
	private List<String> getImportedClasses( CompletionFacts facts ) {
		List<String> imports = new ArrayList<>();

		facts.fileParseResult().findAstRoot().ifPresent( root -> {
			List<BoxImport> importNodes = root.getDescendantsOfType( BoxImport.class );
			for ( BoxImport importNode : importNodes ) {
				// Get the imported class name (simple or fully qualified)
				if ( importNode.getExpression() != null ) {
					String importedName = importNode.getExpression().getSourceText();
					imports.add( importedName );
				}
			}
		} );

		return imports;
	}

	/**
	 * Check if a class is already imported in the current file.
	 */
	private boolean isAlreadyImported( IndexedClass indexedClass, List<String> currentImports, CompletionFacts facts ) {
		// Check if already imported by FQN or simple name
		for ( String imported : currentImports ) {
			if ( imported.equals( indexedClass.fullyQualifiedName() )
			    || imported.equals( indexedClass.name() )
			    || imported.endsWith( "." + indexedClass.name() ) ) {
				return true;
			}
		}

		// Also check if it's in the same package (no import needed)
		String currentPackage = getCurrentFilePackage( facts );
		String classPackage = getPackagePathFromFQN( indexedClass.fullyQualifiedName() );

		return currentPackage.equals( classPackage );
	}

	/**
	 * Add auto-import functionality to a completion item.
	 * This adds an additional text edit that inserts the import statement.
	 */
	private void addAutoImport( CompletionItem item, IndexedClass indexedClass, CompletionFacts facts ) {
		// Find the position where we should insert the import
		Position importPosition = findImportInsertPosition( facts );

		if ( importPosition != null ) {
			// Create the import statement
			String importStatement = "import " + indexedClass.fullyQualifiedName() + ";\n";

			// Create a text edit to insert the import
			TextEdit importEdit = new TextEdit();
			importEdit.setRange( new Range( importPosition, importPosition ) );
			importEdit.setNewText( importStatement );

			// Add to additional edits
			List<TextEdit> additionalEdits = new ArrayList<>();
			additionalEdits.add( importEdit );
			item.setAdditionalTextEdits( additionalEdits );
		}
	}

	/**
	 * Find the position where an import statement should be inserted.
	 * This looks for existing imports and adds after them, or adds at the top of the file.
	 */
	private Position findImportInsertPosition( CompletionFacts facts ) {
		final Position[] position = { new Position( 0, 0 ) };

		facts.fileParseResult().findAstRoot().ifPresent( root -> {
			List<BoxImport> importNodes = root.getDescendantsOfType( BoxImport.class );
			if ( !importNodes.isEmpty() ) {
				// Insert after the last import
				BoxImport	lastImport	= importNodes.get( importNodes.size() - 1 );
				int			line		= lastImport.getPosition().getEnd().getLine();
				position[ 0 ] = new Position( line, 0 );
			} else {
				// No imports yet - insert at the beginning
				// Check if there's a class declaration and insert before it
				if ( root.getChildren() != null && !root.getChildren().isEmpty() ) {
					BoxNode firstNode = root.getChildren().get( 0 );
					int		line		= Math.max( 0, firstNode.getPosition().getStart().getLine() - 1 );
					position[ 0 ] = new Position( line, 0 );
				}
			}
		} );

		return position[ 0 ];
	}
}
