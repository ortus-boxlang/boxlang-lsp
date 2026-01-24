/**
 * [BoxLang LSP]
 *
 * Copyright [2023] [Ortus Solutions, Corp]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ortus.boxlang.lsp.workspace.visitors;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

import ortus.boxlang.compiler.ast.BoxClass;
import ortus.boxlang.compiler.ast.BoxInterface;
import ortus.boxlang.compiler.ast.BoxNode;
import ortus.boxlang.compiler.ast.expression.BoxArrayLiteral;
import ortus.boxlang.compiler.ast.expression.BoxFQN;
import ortus.boxlang.compiler.ast.expression.BoxStringLiteral;
import ortus.boxlang.compiler.ast.statement.BoxAnnotation;
import ortus.boxlang.compiler.ast.statement.BoxFunctionDeclaration;
import ortus.boxlang.compiler.ast.statement.BoxProperty;
import ortus.boxlang.lsp.SourceCodeVisitor;
import ortus.boxlang.lsp.lint.DiagnosticRuleRegistry;
import ortus.boxlang.lsp.lint.LintConfigLoader;
import ortus.boxlang.lsp.lint.rules.DuplicateMethodRule;
import ortus.boxlang.lsp.lint.rules.DuplicatePropertyRule;
import ortus.boxlang.lsp.lint.rules.InvalidExtendsRule;
import ortus.boxlang.lsp.lint.rules.InvalidImplementsRule;
import ortus.boxlang.lsp.workspace.BLASTTools;
import ortus.boxlang.lsp.workspace.FileParseResult;
import ortus.boxlang.lsp.workspace.ProjectContextProvider;
import ortus.boxlang.lsp.workspace.index.ProjectIndex;

/**
 * Visitor for detecting semantic errors in BoxLang code.
 *
 * Detects:
 * - Invalid extends (class not found)
 * - Invalid implements (interface not found)
 * - Duplicate method definitions within a class
 * - Duplicate property definitions within a class
 *
 * Each diagnostic type has its own rule ID for individual configuration.
 */
public class SemanticErrorDiagnosticVisitor extends SourceCodeVisitor {

	private List<Diagnostic>	diagnostics			= new ArrayList<>();
	private Set<String>			seenMethods			= new HashSet<>();
	private Set<String>			seenProperties		= new HashSet<>();
	private String				currentClassName	= null;

	@Override
	public List<Diagnostic> getDiagnostics() {
		// Filter and adjust severity based on individual rule settings
		return this.diagnostics.stream()
		    .filter( d -> {
			    String ruleId = d.getCode() != null ? d.getCode().getLeft() : null;
			    if ( ruleId == null ) {
				    return true;
			    }
			    return DiagnosticRuleRegistry.getInstance().isEnabled( ruleId, true );
		    } )
		    .peek( d -> {
			    String ruleId = d.getCode() != null ? d.getCode().getLeft() : null;
			    if ( ruleId != null ) {
				    var settings = LintConfigLoader.get().forRule( ruleId );
				    if ( settings != null ) {
					    d.setSeverity( settings.toSeverityOr( d.getSeverity() ) );
				    }
			    }
		    } )
		    .toList();
	}

	@Override
	public boolean canVisit( FileParseResult parseResult ) {
		// Visit all BoxLang files - .bx, .bxs, .cfc, .cfm
		return true;
	}

	@Override
	public List<CodeAction> getCodeActions() {
		// No code actions for semantic errors yet
		return List.of();
	}

	@Override
	public void visit( BoxClass node ) {
		// Reset state for new class
		seenMethods.clear();
		seenProperties.clear();

		// Get class name from file path
		currentClassName = extractClassName();

		// Check extends
		List<BoxAnnotation>	annotations		= findAnnotations( node );
		String				extendsClass	= extractExtends( annotations );
		if ( extendsClass != null && !extendsClass.isEmpty() ) {
			validateExtendsReference( extendsClass, node );
		}

		// Check implements
		List<String> implementsInterfaces = extractImplements( annotations );
		for ( String interfaceName : implementsInterfaces ) {
			if ( interfaceName != null && !interfaceName.isEmpty() ) {
				validateImplementsReference( interfaceName, node );
			}
		}

		// Visit children to check methods and properties
		visitChildren( node );

		currentClassName = null;
	}

	@Override
	public void visit( BoxInterface node ) {
		// Reset state for new interface
		seenMethods.clear();

		currentClassName = extractClassName();

		// Check extends for interfaces
		List<BoxAnnotation>	annotations			= findAnnotations( node );
		String				extendsInterface	= extractExtends( annotations );
		if ( extendsInterface != null && !extendsInterface.isEmpty() ) {
			validateExtendsReference( extendsInterface, node );
		}

		// Visit children
		visitChildren( node );

		currentClassName = null;
	}

	@Override
	public void visit( BoxFunctionDeclaration node ) {
		if ( currentClassName == null ) {
			// Standalone function, not in a class - skip duplicate check
			return;
		}

		String methodName = node.getName().toLowerCase();

		if ( seenMethods.contains( methodName ) ) {
			Diagnostic diagnostic = new Diagnostic(
			    ProjectContextProvider.positionToRange( node.getPosition() ),
			    "Duplicate method definition: '" + node.getName() + "' is already defined in this class.",
			    DiagnosticSeverity.Error,
			    "boxlang",
			    DuplicateMethodRule.ID
			);
			diagnostics.add( diagnostic );
		} else {
			seenMethods.add( methodName );
		}
	}

	@Override
	public void visit( BoxProperty node ) {
		if ( currentClassName == null ) {
			return;
		}

		String propertyName = BLASTTools.getPropertyName( node ).toLowerCase();

		if ( seenProperties.contains( propertyName ) ) {
			Diagnostic diagnostic = new Diagnostic(
			    ProjectContextProvider.positionToRange( node.getPosition() ),
			    "Duplicate property definition: '" + propertyName + "' is already defined in this class.",
			    DiagnosticSeverity.Error,
			    "boxlang",
			    DuplicatePropertyRule.ID
			);
			diagnostics.add( diagnostic );
		} else {
			seenProperties.add( propertyName );
		}
	}

	// ============ Helper Methods ============

	private void visitChildren( BoxNode node ) {
		for ( BoxNode child : node.getChildren() ) {
			child.accept( this );
		}
	}

	private String extractClassName() {
		if ( this.filePath == null ) {
			return null;
		}

		String	path		= this.filePath;
		int		lastSlash	= Math.max( path.lastIndexOf( '/' ), path.lastIndexOf( '\\' ) );
		String	fileName	= lastSlash >= 0 ? path.substring( lastSlash + 1 ) : path;

		int		dotIndex	= fileName.lastIndexOf( '.' );
		return dotIndex > 0 ? fileName.substring( 0, dotIndex ) : fileName;
	}

	/**
	 * Get the package (directory path) of the current file relative to workspace root.
	 * For example, if file is at "subpackage/BaseType.bx", returns "subpackage".
	 * Returns null if package cannot be determined.
	 */
	private String getCurrentPackage() {
		if ( this.filePath == null ) {
			return null;
		}

		try {
			// Get workspace root
			var folders = ProjectContextProvider.getInstance().getWorkspaceFolders();
			if ( folders == null || folders.isEmpty() ) {
				return null;
			}

			java.net.URI	workspaceUri	= new java.net.URI( folders.get( 0 ).getUri() );
			java.nio.file.Path	workspaceRoot	= java.nio.file.Paths.get( workspaceUri );

			// Convert filePath to Path (handle both URI and file path formats)
			java.nio.file.Path filePath;
			if ( this.filePath.startsWith( "file:" ) ) {
				filePath = java.nio.file.Paths.get( new java.net.URI( this.filePath ) );
			} else {
				filePath = java.nio.file.Paths.get( this.filePath );
			}

			// Get relative path from workspace root
			java.nio.file.Path	relativePath	= workspaceRoot.relativize( filePath );
			String				pathStr			= relativePath.toString();

			// Get the directory part (without filename)
			int					lastSlash		= Math.max( pathStr.lastIndexOf( '/' ), pathStr.lastIndexOf( '\\' ) );
			if ( lastSlash < 0 ) {
				// File is in root directory
				return null;
			}

			String packagePath = pathStr.substring( 0, lastSlash );

			// Convert path separators to dots
			return packagePath.replace( '/', '.' ).replace( '\\', '.' );
		} catch ( Exception e ) {
			// If we can't determine package, return null
			return null;
		}
	}

	private void validateExtendsReference( String className, BoxNode node ) {
		ProjectIndex index = ProjectContextProvider.getInstance().getIndex();
		if ( index == null ) {
			return;
		}

		// Try to find by simple name first
		var foundClass = index.findClassByName( className );
		if ( foundClass.isEmpty() ) {
			// Try by FQN
			foundClass = index.findClassByFQN( className );
		}

		// If still not found and className contains a dot (potential relative path),
		// try resolving relative to the current file's package
		if ( foundClass.isEmpty() && className.contains( "." ) ) {
			String currentPackage = getCurrentPackage();
			if ( currentPackage != null && !currentPackage.isEmpty() ) {
				String qualifiedName = currentPackage + "." + className;
				foundClass = index.findClassByFQN( qualifiedName );
			}
		}

		if ( foundClass.isEmpty() ) {
			Diagnostic diagnostic = new Diagnostic(
			    ProjectContextProvider.positionToRange( node.getPosition() ),
			    "Class or interface '" + className + "' not found (extends reference).",
			    DiagnosticSeverity.Error,
			    "boxlang",
			    InvalidExtendsRule.ID
			);
			diagnostics.add( diagnostic );
		}
	}

	private void validateImplementsReference( String interfaceName, BoxNode node ) {
		ProjectIndex index = ProjectContextProvider.getInstance().getIndex();
		if ( index == null ) {
			return;
		}

		// Try to find by simple name first
		var foundClass = index.findClassByName( interfaceName );
		if ( foundClass.isEmpty() ) {
			// Try by FQN
			foundClass = index.findClassByFQN( interfaceName );
		}

		// If still not found and interfaceName contains a dot (potential relative path),
		// try resolving relative to the current file's package
		if ( foundClass.isEmpty() && interfaceName.contains( "." ) ) {
			String currentPackage = getCurrentPackage();
			if ( currentPackage != null && !currentPackage.isEmpty() ) {
				String qualifiedName = currentPackage + "." + interfaceName;
				foundClass = index.findClassByFQN( qualifiedName );
			}
		}

		if ( foundClass.isEmpty() ) {
			Diagnostic diagnostic = new Diagnostic(
			    ProjectContextProvider.positionToRange( node.getPosition() ),
			    "Interface '" + interfaceName + "' not found (implements reference).",
			    DiagnosticSeverity.Error,
			    "boxlang",
			    InvalidImplementsRule.ID
			);
			diagnostics.add( diagnostic );
		}
	}

	/**
	 * Find annotations for a node by looking at its immediate children.
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
		    .map( this::extractAnnotationValueAsList )
		    .orElse( new ArrayList<>() );
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
}
