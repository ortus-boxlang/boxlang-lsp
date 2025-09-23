package ortus.boxlang.lsp.workspace;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.Range;

import ortus.boxlang.compiler.ast.BoxClass;
import ortus.boxlang.compiler.ast.BoxNode;
import ortus.boxlang.compiler.ast.visitor.VoidBoxVisitor;

/**
 * Visitor that extracts ClassSymbol information from BoxLang AST nodes.
 * This is used to populate the SymbolProvider cache with class symbols.
 */
public class ClassSymbolExtractorVisitor extends VoidBoxVisitor {

	private final List<ClassSymbol>	classSymbols	= new ArrayList<>();
	private final URI				fileUri;
	private final Instant			lastModified;

	public ClassSymbolExtractorVisitor( URI fileUri ) {
		this.fileUri		= fileUri;
		this.lastModified	= getFileLastModified( fileUri );
	}

	/**
	 * Get the extracted class symbols.
	 * 
	 * @return List of ClassSymbol instances found in the AST
	 */
	public List<ClassSymbol> getClassSymbols() {
		return classSymbols;
	}

	@Override
	public void visit( BoxClass node ) {
		String		className	= getClassName( node );
		Range		range		= BLASTTools.positionToRange( node.getPosition() );

		ClassSymbol	classSymbol	= new ClassSymbol(
		    className,
		    range,
		    fileUri != null ? fileUri.toString() : null,
		    lastModified
		);

		classSymbols.add( classSymbol );

		// Continue visiting children
		visitChildren( node );
	}

	/**
	 * Visit all children of a node.
	 */
	private void visitChildren( BoxNode node ) {
		for ( BoxNode child : node.getChildren() ) {
			child.accept( this );
		}
	}

	/**
	 * Extract the class name from the file path or BoxClass node.
	 * Uses the same logic as DocumentSymbolBoxNodeVisitor.
	 */
	private String getClassName( BoxClass node ) {
		if ( fileUri == null ) {
			return "Class";
		}

		Path	path		= Paths.get( fileUri );
		String	fileName	= path.getFileName().toString();

		if ( fileName.indexOf( "." ) > 0 ) {
			return fileName.substring( 0, fileName.lastIndexOf( "." ) );
		} else {
			return fileName;
		}
	}

	/**
	 * Get the last modified time of the file.
	 */
	private Instant getFileLastModified( URI fileUri ) {
		try {
			if ( fileUri != null ) {
				Path path = Paths.get( fileUri );
				if ( Files.exists( path ) ) {
					return Files.getLastModifiedTime( path ).toInstant();
				}
			}
		} catch ( Exception e ) {
			// Ignore errors, use current time as fallback
		}
		return Instant.now();
	}
}