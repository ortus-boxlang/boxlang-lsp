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
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

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

		String propertyName = BLASTTools.getPropertyName( node );
		if ( propertyName == null ) {
			return;
		}

		propertyName = propertyName.toLowerCase();

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
	 * Create a range that covers from the "class" or "interface" keyword to the opening brace "{".
	 * This provides a more precise diagnostic location for class declaration issues.
	 *
	 * @param node The BoxClass or BoxInterface node
	 *
	 * @return Range from class/interface keyword to opening brace
	 */
	private Range getClassDeclarationRange( BoxNode node ) {
		// Get the full source text of the class/interface
		String sourceText = node.getSourceText();
		if ( sourceText == null || sourceText.isEmpty() ) {
			// Fallback to full node range if no source text
			return ProjectContextProvider.positionToRange( node.getPosition() );
		}

		// Find the opening brace position in the source text
		int braceIndex = sourceText.indexOf( '{' );
		if ( braceIndex < 0 ) {
			// No brace found, use full range
			return ProjectContextProvider.positionToRange( node.getPosition() );
		}

		// Calculate the end position (at the opening brace)
		ortus.boxlang.compiler.ast.Position	nodePos		= node.getPosition();
		ortus.boxlang.compiler.ast.Point	startPoint	= nodePos.getStart();

		// Count lines and columns up to the brace
		int									line		= startPoint.getLine();
		int									column		= startPoint.getColumn();

		for ( int i = 0; i < braceIndex; i++ ) {
			char c = sourceText.charAt( i );
			if ( c == '\n' ) {
				line++;
				column = 0;
			} else {
				column++;
			}
		}

		// Create range from start to opening brace
		return new Range(
		    new Position( startPoint.getLine() - 1, startPoint.getColumn() ),
		    new Position( line - 1, column )
		);
	}

	private void validateExtendsReference( String className, BoxNode node ) {
		ProjectIndex index = ProjectContextProvider.getInstance().getIndex();
		if ( index == null ) {
			return;
		}

		var foundClass = index.findClassWithContext( className, resolveFileUri() );

		if ( foundClass.isEmpty() ) {
			Diagnostic diagnostic = new Diagnostic(
			    getClassDeclarationRange( node ),
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

		var foundClass = index.findClassWithContext( interfaceName, resolveFileUri() );

		if ( foundClass.isEmpty() ) {
			Diagnostic diagnostic = new Diagnostic(
			    getClassDeclarationRange( node ),
			    "Interface '" + interfaceName + "' not found (implements reference).",
			    DiagnosticSeverity.Error,
			    "boxlang",
			    InvalidImplementsRule.ID
			);
			diagnostics.add( diagnostic );
		}
	}

	/**
	 * Convert {@code this.filePath} to a {@code URI} so it can be passed to
	 * {@link ProjectIndex#findClassWithContext} for context-aware resolution.
	 *
	 * @return the file URI, or {@code null} if the path cannot be converted
	 */
	private java.net.URI resolveFileUri() {
		if ( this.filePath == null ) {
			return null;
		}
		try {
			if ( this.filePath.startsWith( "file:" ) ) {
				return new java.net.URI( this.filePath );
			}
			return java.nio.file.Paths.get( this.filePath ).toUri();
		} catch ( Exception e ) {
			return null;
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
