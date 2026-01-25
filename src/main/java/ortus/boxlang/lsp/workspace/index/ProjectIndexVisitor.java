package ortus.boxlang.lsp.workspace.index;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.Range;

import ortus.boxlang.compiler.ast.BoxClass;
import ortus.boxlang.compiler.ast.BoxInterface;
import ortus.boxlang.compiler.ast.BoxNode;
import ortus.boxlang.compiler.ast.IBoxDocumentableNode;
import ortus.boxlang.compiler.ast.comment.BoxDocComment;
import ortus.boxlang.compiler.ast.expression.BoxArrayLiteral;
import ortus.boxlang.compiler.ast.expression.BoxFQN;
import ortus.boxlang.compiler.ast.expression.BoxStringLiteral;
import ortus.boxlang.compiler.ast.statement.BoxAnnotation;
import ortus.boxlang.compiler.ast.statement.BoxArgumentDeclaration;
import ortus.boxlang.compiler.ast.statement.BoxDocumentationAnnotation;
import ortus.boxlang.compiler.ast.statement.BoxFunctionDeclaration;
import ortus.boxlang.compiler.ast.statement.BoxProperty;
import ortus.boxlang.compiler.ast.visitor.VoidBoxVisitor;
import ortus.boxlang.lsp.workspace.BLASTTools;

/**
 * Visitor that extracts indexable symbols from BoxLang AST nodes.
 * Used to populate the ProjectIndex with class, method, and property information.
 */
public class ProjectIndexVisitor extends VoidBoxVisitor {

	private final List<IndexedClass>	indexedClasses		= new ArrayList<>();
	private final List<IndexedMethod>	indexedMethods		= new ArrayList<>();
	private final List<IndexedProperty>	indexedProperties	= new ArrayList<>();
	private final URI					fileUri;
	private final Instant				lastModified;
	private final String				fullyQualifiedName;
	private String						currentClassName	= null;

	public ProjectIndexVisitor( URI fileUri, Path workspaceRoot ) {
		this.fileUri			= fileUri;
		this.lastModified		= getFileLastModified( fileUri );
		this.fullyQualifiedName	= computeFQN( fileUri, workspaceRoot );
	}

	public List<IndexedClass> getIndexedClasses() {
		return indexedClasses;
	}

	public List<IndexedMethod> getIndexedMethods() {
		return indexedMethods;
	}

	public List<IndexedProperty> getIndexedProperties() {
		return indexedProperties;
	}

	@Override
	public void visit( BoxClass node ) {
		String				className				= getClassName();
		Range				range					= BLASTTools.positionToRange( node.getPosition() );
		List<BoxAnnotation>	annotations				= findAnnotations( node );
		String				extendsClass			= extractExtends( annotations );
		List<String>		implementsInterfaces	= extractImplements( annotations );
		List<String>		modifiers				= extractModifiers( annotations );
		String				documentation			= extractClassDocumentation( node );

		IndexedClass		indexedClass			= new IndexedClass(
		    className,
		    fullyQualifiedName,
		    fileUri != null ? fileUri.toString() : null,
		    range,
		    extendsClass,
		    implementsInterfaces,
		    modifiers,
		    false,
		    documentation,
		    lastModified
		);

		indexedClasses.add( indexedClass );
		currentClassName = fullyQualifiedName;	// Use FQN so methods/properties are associated correctly

		visitChildren( node );

		currentClassName = null;
	}

	@Override
	public void visit( BoxInterface node ) {
		String				className			= getClassName();
		Range				range				= BLASTTools.positionToRange( node.getPosition() );
		List<BoxAnnotation>	annotations			= findAnnotations( node );
		String				extendsInterface	= extractExtends( annotations );
		List<String>		modifiers			= extractModifiers( annotations );
		String				documentation		= extractInterfaceDocumentation( node );

		IndexedClass		indexedInterface	= new IndexedClass(
		    className,
		    fullyQualifiedName,
		    fileUri != null ? fileUri.toString() : null,
		    range,
		    extendsInterface,
		    new ArrayList<>(),
		    modifiers,
		    true,
		    documentation,
		    lastModified
		);

		indexedClasses.add( indexedInterface );
		currentClassName = fullyQualifiedName;	// Use FQN so methods/properties are associated correctly

		visitChildren( node );

		currentClassName = null;
	}

	@Override
	public void visit( BoxFunctionDeclaration node ) {
		String					name			= node.getName();
		Range					range			= BLASTTools.positionToRange( node.getPosition() );
		String					returnTypeHint	= node.getType() != null ? node.getType().toString() : "any";
		List<IndexedParameter>	parameters		= extractParameters( node );
		List<BoxAnnotation>		annotations		= findAnnotations( node );
		String					accessModifier	= extractAccessModifier( annotations );
		List<String>			modifiers		= extractFunctionModifiers( annotations );
		String					documentation	= extractDocumentation( node );

		IndexedMethod			indexedMethod	= new IndexedMethod(
		    name,
		    currentClassName,
		    fileUri != null ? fileUri.toString() : null,
		    range,
		    returnTypeHint,
		    parameters,
		    accessModifier,
		    modifiers,
		    documentation
		);

		indexedMethods.add( indexedMethod );

		// Don't recurse into nested functions for now
	}

	@Override
	public void visit( BoxProperty node ) {
		String			name			= extractPropertyName( node );
		Range			range			= BLASTTools.positionToRange( node.getPosition() );
		String			typeHint		= extractPropertyType( node );
		String			defaultValue	= extractPropertyDefault( node );
		boolean			hasGetter		= extractPropertyAccessor( node, "getter" );
		boolean			hasSetter		= extractPropertyAccessor( node, "setter" );

		IndexedProperty	indexedProperty	= new IndexedProperty(
		    name,
		    currentClassName,
		    fileUri != null ? fileUri.toString() : null,
		    range,
		    typeHint,
		    defaultValue,
		    hasGetter,
		    hasSetter
		);

		indexedProperties.add( indexedProperty );
	}

	private void visitChildren( BoxNode node ) {
		for ( BoxNode child : node.getChildren() ) {
			child.accept( this );
		}
	}

	private String getClassName() {
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

	private String computeFQN( URI fileUri, Path workspaceRoot ) {
		if ( fileUri == null || workspaceRoot == null ) {
			return getClassName();
		}

		try {
			Path	filePath		= Paths.get( fileUri );
			Path	relativePath	= workspaceRoot.relativize( filePath );
			String	pathStr			= relativePath.toString();

			// Remove file extension
			int		dotIndex		= pathStr.lastIndexOf( '.' );
			if ( dotIndex > 0 ) {
				pathStr = pathStr.substring( 0, dotIndex );
			}

			// Convert path separators to dots
			return pathStr.replace( '/', '.' ).replace( '\\', '.' );
		} catch ( Exception e ) {
			return getClassName();
		}
	}

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

	/**
	 * Find annotations for a node by looking at its immediate children.
	 * This works for BoxClass, BoxInterface, BoxFunctionDeclaration, and BoxArgumentDeclaration.
	 */
	private List<BoxAnnotation> findAnnotations( BoxNode node ) {
		List<BoxAnnotation> annotations = new ArrayList<>();
		for ( BoxNode child : node.getChildren() ) {
			if ( child instanceof BoxAnnotation annotation ) {
				annotations.add( annotation );
			}
		}
		return annotations;
	}

	private String extractExtends( List<BoxAnnotation> annotations ) {
		return annotations.stream()
		    .filter( a -> a.getKey().getValue().equalsIgnoreCase( "extends" ) )
		    .findFirst()
		    .map( this::extractAnnotationValue )
		    .orElse( null );
	}

	private List<String> extractImplements( List<BoxAnnotation> annotations ) {
		return annotations.stream()
		    .filter( a -> a.getKey().getValue().equalsIgnoreCase( "implements" ) )
		    .findFirst()
		    .map( a -> extractAnnotationValueAsList( a ) )
		    .orElse( new ArrayList<>() );
	}

	private List<String> extractModifiers( List<BoxAnnotation> annotations ) {
		List<String> modifiers = new ArrayList<>();

		for ( BoxAnnotation annotation : annotations ) {
			String key = annotation.getKey().getValue().toLowerCase();
			if ( key.equals( "abstract" ) || key.equals( "final" ) ) {
				modifiers.add( key );
			}
		}

		return modifiers;
	}

	private String extractAccessModifier( List<BoxAnnotation> annotations ) {
		return annotations.stream()
		    .filter( a -> {
			    String key = a.getKey().getValue().toLowerCase();
			    return key.equals( "access" ) || key.equals( "public" ) ||
			        key.equals( "private" ) || key.equals( "remote" ) ||
			        key.equals( "package" );
		    } )
		    .findFirst()
		    .map( a -> {
			    String key = a.getKey().getValue().toLowerCase();
			    if ( key.equals( "access" ) ) {
				    return extractAnnotationValue( a );
			    }
			    return key;
		    } )
		    .orElse( "public" );
	}

	private List<String> extractFunctionModifiers( List<BoxAnnotation> annotations ) {
		List<String> modifiers = new ArrayList<>();

		for ( BoxAnnotation annotation : annotations ) {
			String key = annotation.getKey().getValue().toLowerCase();
			if ( key.equals( "static" ) || key.equals( "final" ) || key.equals( "abstract" ) ) {
				modifiers.add( key );
			}
		}

		return modifiers;
	}

	private List<IndexedParameter> extractParameters( BoxFunctionDeclaration node ) {
		List<IndexedParameter> parameters = new ArrayList<>();

		// Get arguments from the function's children
		for ( BoxNode child : node.getChildren() ) {
			if ( child instanceof BoxArgumentDeclaration argDecl ) {
				String	name			= argDecl.getName();
				String	typeHint		= argDecl.getType() != null ? argDecl.getType().toString() : "any";
				boolean	required		= extractArgumentRequired( argDecl );
				String	defaultValue	= argDecl.getValue() != null ? argDecl.getValue().getSourceText() : null;

				parameters.add( new IndexedParameter( name, typeHint, required, defaultValue ) );
			}
		}

		return parameters;
	}

	private boolean extractArgumentRequired( BoxArgumentDeclaration argDecl ) {
		// Check annotations for required
		List<BoxAnnotation> annotations = findAnnotations( argDecl );
		for ( BoxAnnotation annotation : annotations ) {
			String key = annotation.getKey().getValue().toLowerCase();
			if ( key.equals( "required" ) ) {
				// If just "required" annotation with no value, it's true
				if ( annotation.getValue() == null ) {
					return true;
				}
				String value = extractAnnotationValue( annotation );
				return value == null || value.equalsIgnoreCase( "true" );
			}
		}
		return false;
	}

	private String extractPropertyName( BoxProperty node ) {
		for ( BoxAnnotation annotation : node.getAllAnnotations() ) {
			if ( annotation.getKey().getValue().equalsIgnoreCase( "name" ) ) {
				String value = extractAnnotationValue( annotation );
				if ( value != null ) {
					return value;
				}
			}
		}

		// If no name annotation, use the first annotation key as the name
		if ( !node.getAllAnnotations().isEmpty() ) {
			BoxAnnotation firstAnnotation = node.getAllAnnotations().get( 0 );
			if ( firstAnnotation.getValue() == null ) {
				return firstAnnotation.getKey().getValue();
			}
		}

		return "unknown";
	}

	private String extractPropertyType( BoxProperty node ) {
		for ( BoxAnnotation annotation : node.getAllAnnotations() ) {
			if ( annotation.getKey().getValue().equalsIgnoreCase( "type" ) ) {
				return extractAnnotationValue( annotation );
			}
		}
		return "any";
	}

	private String extractPropertyDefault( BoxProperty node ) {
		for ( BoxAnnotation annotation : node.getAllAnnotations() ) {
			if ( annotation.getKey().getValue().equalsIgnoreCase( "default" ) ) {
				return extractAnnotationValue( annotation );
			}
		}
		return null;
	}

	private boolean extractPropertyAccessor( BoxProperty node, String accessorType ) {
		for ( BoxAnnotation annotation : node.getAllAnnotations() ) {
			if ( annotation.getKey().getValue().equalsIgnoreCase( accessorType ) ) {
				if ( annotation.getValue() == null ) {
					return true;
				}
				String value = extractAnnotationValue( annotation );
				return value == null || value.equalsIgnoreCase( "true" );
			}
		}
		// Default: both getters and setters are true unless accessors annotation is present
		return true;
	}

	private String extractAnnotationValue( BoxAnnotation annotation ) {
		if ( annotation.getValue() == null ) {
			return null;
		}

		if ( annotation.getValue() instanceof BoxStringLiteral bsl ) {
			return bsl.getValue();
		}

		if ( annotation.getValue() instanceof BoxFQN fqn ) {
			return fqn.getValue();
		}

		return annotation.getValue().getSourceText();
	}

	private List<String> extractAnnotationValueAsList( BoxAnnotation annotation ) {
		List<String> values = new ArrayList<>();

		if ( annotation.getValue() == null ) {
			return values;
		}

		if ( annotation.getValue() instanceof BoxArrayLiteral arrayLiteral ) {
			for ( BoxNode element : arrayLiteral.getValues() ) {
				if ( element instanceof BoxStringLiteral bsl ) {
					values.add( bsl.getValue() );
				} else if ( element instanceof BoxFQN fqn ) {
					values.add( fqn.getValue() );
				} else {
					values.add( element.getSourceText() );
				}
			}
		} else if ( annotation.getValue() instanceof BoxStringLiteral bsl ) {
			// Single value or comma-separated list in string
			String value = bsl.getValue();
			if ( value.contains( "," ) ) {
				for ( String part : value.split( "," ) ) {
					values.add( part.trim() );
				}
			} else {
				values.add( value );
			}
		} else if ( annotation.getValue() instanceof BoxFQN fqn ) {
			values.add( fqn.getValue() );
		} else {
			String text = annotation.getValue().getSourceText();
			if ( text != null && text.contains( "," ) ) {
				for ( String part : text.split( "," ) ) {
					values.add( part.trim() );
				}
			} else if ( text != null ) {
				values.add( text );
			}
		}

		return values;
	}

	/**
	 * Extract documentation from a class node.
	 * Returns a formatted string containing the description and all documentation annotations.
	 */
	private String extractClassDocumentation( BoxClass node ) {
		if ( ! ( node instanceof IBoxDocumentableNode documentableNode ) ) {
			return null;
		}

		BoxDocComment docComment = documentableNode.getDocComment();
		if ( docComment == null ) {
			return null;
		}

		StringBuilder	doc			= new StringBuilder();

		// Get the comment text (description)
		String			commentText	= docComment.getCommentText();
		if ( commentText != null && !commentText.isBlank() ) {
			String cleanedDescription = cleanDocCommentDescription( commentText );
			if ( !cleanedDescription.isBlank() ) {
				doc.append( cleanedDescription );
			}
		}

		// Add documentation annotations (@author, @since, etc.)
		List<BoxDocumentationAnnotation> annotations = docComment.getAnnotations();
		if ( annotations != null && !annotations.isEmpty() ) {
			for ( BoxDocumentationAnnotation annotation : annotations ) {
				if ( doc.length() > 0 ) {
					doc.append( "\n" );
				}
				String	key		= annotation.getKey().getValue();
				String	value	= "";
				if ( annotation.getValue() != null ) {
					value = annotation.getValue().getSourceText();
					String tagPrefix = "@" + key + " ";
					if ( value.startsWith( tagPrefix ) ) {
						value = value.substring( tagPrefix.length() );
					}
					value = cleanAnnotationValue( value );
				}
				doc.append( "@" ).append( key ).append( " " ).append( value );
			}
		}

		return doc.length() > 0 ? doc.toString() : null;
	}

	/**
	 * Extract documentation from an interface node.
	 * Returns a formatted string containing the description and all documentation annotations.
	 */
	private String extractInterfaceDocumentation( BoxInterface node ) {
		if ( ! ( node instanceof IBoxDocumentableNode documentableNode ) ) {
			return null;
		}

		BoxDocComment docComment = documentableNode.getDocComment();
		if ( docComment == null ) {
			return null;
		}

		StringBuilder	doc			= new StringBuilder();

		// Get the comment text (description)
		String			commentText	= docComment.getCommentText();
		if ( commentText != null && !commentText.isBlank() ) {
			String cleanedDescription = cleanDocCommentDescription( commentText );
			if ( !cleanedDescription.isBlank() ) {
				doc.append( cleanedDescription );
			}
		}

		// Add documentation annotations (@author, @since, etc.)
		List<BoxDocumentationAnnotation> annotations = docComment.getAnnotations();
		if ( annotations != null && !annotations.isEmpty() ) {
			for ( BoxDocumentationAnnotation annotation : annotations ) {
				if ( doc.length() > 0 ) {
					doc.append( "\n" );
				}
				String	key		= annotation.getKey().getValue();
				String	value	= "";
				if ( annotation.getValue() != null ) {
					value = annotation.getValue().getSourceText();
					String tagPrefix = "@" + key + " ";
					if ( value.startsWith( tagPrefix ) ) {
						value = value.substring( tagPrefix.length() );
					}
					value = cleanAnnotationValue( value );
				}
				doc.append( "@" ).append( key ).append( " " ).append( value );
			}
		}

		return doc.length() > 0 ? doc.toString() : null;
	}

	/**
	 * Extract documentation from a documentable node (like BoxFunctionDeclaration).
	 * Returns a formatted string containing the description and all documentation annotations.
	 */
	private String extractDocumentation( BoxFunctionDeclaration node ) {
		if ( ! ( node instanceof IBoxDocumentableNode documentableNode ) ) {
			return null;
		}

		BoxDocComment docComment = documentableNode.getDocComment();
		if ( docComment == null ) {
			return null;
		}

		StringBuilder	doc			= new StringBuilder();

		// Get the comment text (description)
		String			commentText	= docComment.getCommentText();
		if ( commentText != null && !commentText.isBlank() ) {
			// Clean up the comment text
			String cleanedDescription = cleanDocCommentDescription( commentText );
			if ( !cleanedDescription.isBlank() ) {
				doc.append( cleanedDescription );
			}
		}

		// Add documentation annotations (@param, @return, etc.)
		List<BoxDocumentationAnnotation> annotations = docComment.getAnnotations();
		if ( annotations != null && !annotations.isEmpty() ) {
			for ( BoxDocumentationAnnotation annotation : annotations ) {
				if ( doc.length() > 0 ) {
					doc.append( "\n" );
				}
				String	key		= annotation.getKey().getValue();
				String	value	= "";
				if ( annotation.getValue() != null ) {
					value = annotation.getValue().getSourceText();
					// Clean up the value - it may contain the tag prefix again
					String tagPrefix = "@" + key + " ";
					if ( value.startsWith( tagPrefix ) ) {
						value = value.substring( tagPrefix.length() );
					}
					// Remove trailing asterisks and whitespace from multiline comments
					value = cleanAnnotationValue( value );
				}
				doc.append( "@" ).append( key ).append( " " ).append( value );
			}
		}

		return doc.length() > 0 ? doc.toString() : null;
	}

	/**
	 * Clean up an annotation value by removing trailing asterisks and extra whitespace.
	 */
	private String cleanAnnotationValue( String value ) {
		if ( value == null ) {
			return "";
		}

		// Split into lines and clean each line
		String[]		lines	= value.split( "\n" );
		StringBuilder	cleaned	= new StringBuilder();

		for ( String line : lines ) {
			String trimmed = line.trim();
			// Remove leading asterisks
			if ( trimmed.startsWith( "*" ) ) {
				trimmed = trimmed.substring( 1 ).trim();
			}
			// Skip lines that are just asterisks or empty
			if ( trimmed.isEmpty() || trimmed.equals( "*" ) ) {
				continue;
			}
			// Skip lines that start with @ (next annotation)
			if ( trimmed.startsWith( "@" ) ) {
				break;
			}
			if ( cleaned.length() > 0 ) {
				cleaned.append( " " );
			}
			cleaned.append( trimmed );
		}

		return cleaned.toString().trim();
	}

	/**
	 * Clean up a documentation comment description by removing leading asterisks and extra whitespace.
	 */
	private String cleanDocCommentDescription( String commentText ) {
		if ( commentText == null ) {
			return "";
		}

		// Split into lines and clean each line
		String[]		lines	= commentText.split( "\n" );
		StringBuilder	cleaned	= new StringBuilder();

		for ( String line : lines ) {
			// Remove leading whitespace, asterisks, and trailing whitespace
			String trimmed = line.trim();
			if ( trimmed.startsWith( "*" ) ) {
				trimmed = trimmed.substring( 1 ).trim();
			}

			// Skip lines that start with @ (documentation tags)
			if ( trimmed.startsWith( "@" ) ) {
				continue;
			}

			// Skip empty lines at the start
			if ( cleaned.length() == 0 && trimmed.isEmpty() ) {
				continue;
			}

			if ( cleaned.length() > 0 ) {
				cleaned.append( "\n" );
			}
			cleaned.append( trimmed );
		}

		return cleaned.toString().trim();
	}
}
