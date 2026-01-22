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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DiagnosticTag;

import ortus.boxlang.compiler.ast.BoxClass;
import ortus.boxlang.compiler.ast.BoxNode;
import ortus.boxlang.compiler.ast.BoxScript;
import ortus.boxlang.compiler.ast.expression.BoxAssignment;
import ortus.boxlang.compiler.ast.expression.BoxFQN;
import ortus.boxlang.compiler.ast.expression.BoxFunctionInvocation;
import ortus.boxlang.compiler.ast.expression.BoxIdentifier;
import ortus.boxlang.compiler.ast.expression.BoxMethodInvocation;
import ortus.boxlang.compiler.ast.expression.BoxNew;
import ortus.boxlang.compiler.ast.statement.BoxAnnotation;
import ortus.boxlang.compiler.ast.statement.BoxArgumentDeclaration;
import ortus.boxlang.compiler.ast.statement.BoxBreak;
import ortus.boxlang.compiler.ast.statement.BoxContinue;
import ortus.boxlang.compiler.ast.statement.BoxAccessModifier;
import ortus.boxlang.compiler.ast.statement.BoxFunctionDeclaration;
import ortus.boxlang.compiler.ast.statement.BoxImport;
import ortus.boxlang.compiler.ast.statement.BoxReturn;
import ortus.boxlang.compiler.ast.statement.BoxThrow;
import ortus.boxlang.compiler.ast.statement.BoxTry;
import ortus.boxlang.compiler.ast.statement.BoxTryCatch;
import ortus.boxlang.compiler.ast.statement.BoxType;
import ortus.boxlang.lsp.SourceCodeVisitor;
import ortus.boxlang.lsp.lint.DiagnosticRuleRegistry;
import ortus.boxlang.lsp.lint.LintConfigLoader;
import ortus.boxlang.lsp.lint.rules.EmptyCatchBlockRule;
import ortus.boxlang.lsp.lint.rules.MissingReturnStatementRule;
import ortus.boxlang.lsp.lint.rules.ShadowedVariableRule;
import ortus.boxlang.lsp.lint.rules.UnreachableCodeRule;
import ortus.boxlang.lsp.lint.rules.UnusedImportRule;
import ortus.boxlang.lsp.lint.rules.UnusedPrivateMethodRule;
import ortus.boxlang.lsp.workspace.FileParseResult;
import ortus.boxlang.lsp.workspace.ProjectContextProvider;

/**
 * Visitor for detecting semantic warnings in BoxLang code.
 *
 * Detects:
 * - Empty catch blocks
 * - Unreachable code after return/throw/break/continue
 * - Shadowed variables (local shadows parameter)
 * - Missing return statement when return type hint is present
 * - Unused private methods
 * - Unused imports
 *
 * Each diagnostic type has its own rule ID for individual configuration.
 */
public class SemanticWarningDiagnosticVisitor extends SourceCodeVisitor {

	private List<Diagnostic>							diagnostics			= new ArrayList<>();

	// For tracking shadowed variables per function
	private Map<BoxFunctionDeclaration, Set<String>>	functionParameters	= new HashMap<>();
	private BoxFunctionDeclaration						currentFunction		= null;

	// For tracking imports
	private Map<String, BoxImport>						imports				= new HashMap<>();
	private Set<String>									usedIdentifiers		= new HashSet<>();

	// For tracking private methods and their usage
	private Map<String, BoxFunctionDeclaration>			privateMethods		= new HashMap<>();
	private Set<String>									calledMethods		= new HashSet<>();

	// For tracking return statements in functions
	private Map<BoxFunctionDeclaration, Boolean>		functionHasReturn	= new HashMap<>();

	// For tracking if we're in a class
	private boolean										inClass				= false;

	@Override
	public List<Diagnostic> getDiagnostics() {
		// Generate diagnostics for unused imports
		generateUnusedImportDiagnostics();

		// Generate diagnostics for unused private methods
		generateUnusedPrivateMethodDiagnostics();

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
		return true;
	}

	@Override
	public List<CodeAction> getCodeActions() {
		return List.of();
	}

	// ============ Empty Catch Block Detection ============

	@Override
	public void visit( BoxTry node ) {
		// Visit children - this will process catch blocks
		visitChildren( node );
	}

	@Override
	public void visit( BoxTryCatch node ) {
		var catchBody = node.getCatchBody();

		if ( catchBody == null || catchBody.isEmpty() ) {
			Diagnostic diagnostic = new Diagnostic(
			    ProjectContextProvider.positionToRange( node.getPosition() ),
			    "Empty catch block. Consider logging or handling the exception.",
			    DiagnosticSeverity.Warning,
			    "boxlang",
			    EmptyCatchBlockRule.ID
			);
			diagnostics.add( diagnostic );
		}

		// Visit children
		visitChildren( node );
	}

	// ============ Unreachable Code Detection ============

	@Override
	public void visit( BoxReturn node ) {
		// Mark that this function has a return
		if ( currentFunction != null ) {
			functionHasReturn.put( currentFunction, true );
		}

		checkForSiblingsAfterTerminal( node, "return" );
		visitChildren( node );
	}

	@Override
	public void visit( BoxThrow node ) {
		checkForSiblingsAfterTerminal( node, "throw" );
		visitChildren( node );
	}

	@Override
	public void visit( BoxBreak node ) {
		checkForSiblingsAfterTerminal( node, "break" );
	}

	@Override
	public void visit( BoxContinue node ) {
		checkForSiblingsAfterTerminal( node, "continue" );
	}

	private void checkForSiblingsAfterTerminal( BoxNode node, String statementType ) {
		BoxNode parent = node.getParent();
		if ( parent == null ) {
			return;
		}

		List<BoxNode> siblings = parent.getChildren();
		int nodeIndex = -1;

		for ( int i = 0; i < siblings.size(); i++ ) {
			if ( siblings.get( i ) == node ) {
				nodeIndex = i;
				break;
			}
		}

		if ( nodeIndex >= 0 && nodeIndex < siblings.size() - 1 ) {
			// There are statements after this terminal statement
			BoxNode nextNode = siblings.get( nodeIndex + 1 );

			// Don't warn about catch blocks or annotations that follow
			if ( nextNode instanceof BoxTryCatch || nextNode instanceof BoxAnnotation ) {
				return;
			}

			Diagnostic diagnostic = new Diagnostic(
			    ProjectContextProvider.positionToRange( nextNode.getPosition() ),
			    "Unreachable code after " + statementType + " statement.",
			    DiagnosticSeverity.Warning,
			    "boxlang",
			    UnreachableCodeRule.ID
			);
			diagnostic.setTags( List.of( DiagnosticTag.Unnecessary ) );
			diagnostics.add( diagnostic );
		}
	}

	// ============ Shadowed Variable Detection ============

	@Override
	public void visit( BoxFunctionDeclaration node ) {
		BoxFunctionDeclaration previousFunction = currentFunction;
		currentFunction = node;

		// Initialize parameter tracking for this function
		Set<String> params = new HashSet<>();
		functionParameters.put( node, params );
		functionHasReturn.put( node, false );

		// Extract parameters
		for ( BoxNode child : node.getChildren() ) {
			if ( child instanceof BoxArgumentDeclaration argDecl ) {
				params.add( argDecl.getName().toLowerCase() );
			}
		}

		// Check for private methods (only in class context)
		if ( inClass ) {
			BoxAccessModifier accessModifier = node.getAccessModifier();
			if ( accessModifier == BoxAccessModifier.Private ) {
				privateMethods.put( node.getName().toLowerCase(), node );
			}
		}

		// Visit function body
		visitChildren( node );

		// After visiting body, check for missing return statement
		checkMissingReturn( node );

		// Restore previous function context
		currentFunction = previousFunction;
	}

	@Override
	public void visit( BoxArgumentDeclaration node ) {
		// Parameters are tracked in visit(BoxFunctionDeclaration)
	}

	@Override
	public void visit( BoxAssignment node ) {
		// Check if we're declaring a local variable with 'var' that shadows a parameter
		BoxNode left = node.getLeft();

		if ( left instanceof BoxIdentifier identifier && currentFunction != null ) {
			String varName = identifier.getName().toLowerCase();
			Set<String> params = functionParameters.get( currentFunction );

			// Check if this is a 'var' declaration (first assignment)
			// by checking if it uses the var modifier
			if ( params != null && params.contains( varName ) ) {
				// Check if this assignment uses 'var' keyword
				// The var keyword creates a new local variable, which would shadow the parameter
				if ( node.getModifiers() != null && !node.getModifiers().isEmpty() ) {
					String modifier = node.getModifiers().get( 0 ).toString().toLowerCase();
					if ( modifier.equals( "var" ) || modifier.equals( "local" ) ) {
						Diagnostic diagnostic = new Diagnostic(
						    ProjectContextProvider.positionToRange( identifier.getPosition() ),
						    "Variable '" + identifier.getName() + "' shadows a function parameter.",
						    DiagnosticSeverity.Warning,
						    "boxlang",
						    ShadowedVariableRule.ID
						);
						diagnostics.add( diagnostic );
					}
				}
			}
		}

		visitChildren( node );
	}

	// ============ Missing Return Statement Detection ============

	private void checkMissingReturn( BoxFunctionDeclaration node ) {
		// Check if function has a non-void return type hint
		if ( node.getType() == null ) {
			return;
		}

		BoxType returnType = node.getType().getType();

		// Void functions don't need a return
		if ( returnType == BoxType.Void ) {
			return;
		}

		// Check if function has a return statement
		Boolean hasReturn = functionHasReturn.get( node );
		if ( hasReturn == null || !hasReturn ) {
			Diagnostic diagnostic = new Diagnostic(
			    ProjectContextProvider.positionToRange( node.getPosition() ),
			    "Function '" + node.getName() + "' has return type '" + returnType + "' but may not return a value.",
			    DiagnosticSeverity.Warning,
			    "boxlang",
			    MissingReturnStatementRule.ID
			);
			diagnostics.add( diagnostic );
		}
	}

	// ============ Import Tracking ============

	@Override
	public void visit( BoxImport node ) {
		// Extract the imported class name
		String importedName = extractImportName( node );
		if ( importedName != null ) {
			imports.put( importedName.toLowerCase(), node );
		}
	}

	private String extractImportName( BoxImport node ) {
		// If there's an alias, use that
		if ( node.getAlias() != null ) {
			return node.getAlias().getName();
		}

		// Otherwise extract the class name from the import expression
		if ( node.getExpression() != null ) {
			String fullPath = node.getExpression().getSourceText();
			// Get the last part after the last dot or colon
			int lastDot = fullPath.lastIndexOf( '.' );
			int lastColon = fullPath.lastIndexOf( ':' );
			int lastSeparator = Math.max( lastDot, lastColon );

			if ( lastSeparator >= 0 && lastSeparator < fullPath.length() - 1 ) {
				return fullPath.substring( lastSeparator + 1 );
			}
			return fullPath;
		}

		return null;
	}

	// ============ Identifier Tracking for Imports and Method Calls ============

	@Override
	public void visit( BoxIdentifier node ) {
		usedIdentifiers.add( node.getName().toLowerCase() );
	}

	@Override
	public void visit( BoxFunctionInvocation node ) {
		// Track method calls for unused private method detection
		calledMethods.add( node.getName().toLowerCase() );
		visitChildren( node );
	}

	@Override
	public void visit( BoxMethodInvocation node ) {
		// Track method calls - getName() returns a BoxExpression, get source text
		String methodName = node.getName().getSourceText();
		if ( methodName != null ) {
			calledMethods.add( methodName.toLowerCase() );
		}
		visitChildren( node );
	}

	@Override
	public void visit( BoxNew node ) {
		// Track 'new ClassName()' - the class name in new expressions
		// is stored as a BoxFQN, we visit children to let BoxFQN visit handle it
		visitChildren( node );
	}

	@Override
	public void visit( BoxFQN node ) {
		// Extract just the class name (last part) from the FQN
		String fqn = node.getValue();
		if ( fqn != null ) {
			int lastDot = fqn.lastIndexOf( '.' );
			int lastColon = fqn.lastIndexOf( ':' );
			int lastSeparator = Math.max( lastDot, lastColon );
			String className;
			if ( lastSeparator >= 0 && lastSeparator < fqn.length() - 1 ) {
				className = fqn.substring( lastSeparator + 1 );
			} else {
				className = fqn;
			}
			usedIdentifiers.add( className.toLowerCase() );
		}
	}

	// ============ Class Context Tracking ============

	@Override
	public void visit( BoxClass node ) {
		inClass = true;
		visitChildren( node );

		// Generate unused private method diagnostics before leaving class context
		generateUnusedPrivateMethodDiagnostics();

		// Clear private method tracking
		privateMethods.clear();
		calledMethods.clear();

		inClass = false;
	}

	@Override
	public void visit( BoxScript node ) {
		visitChildren( node );
	}

	// ============ Diagnostic Generation ============

	private void generateUnusedImportDiagnostics() {
		for ( Map.Entry<String, BoxImport> entry : imports.entrySet() ) {
			String importedName = entry.getKey();
			BoxImport importNode = entry.getValue();

			if ( !usedIdentifiers.contains( importedName ) ) {
				Diagnostic diagnostic = new Diagnostic(
				    ProjectContextProvider.positionToRange( importNode.getPosition() ),
				    "Import '" + extractImportName( importNode ) + "' is never used.",
				    DiagnosticSeverity.Warning,
				    "boxlang",
				    UnusedImportRule.ID
				);
				diagnostic.setTags( List.of( DiagnosticTag.Unnecessary ) );
				diagnostics.add( diagnostic );
			}
		}
	}

	private void generateUnusedPrivateMethodDiagnostics() {
		for ( Map.Entry<String, BoxFunctionDeclaration> entry : privateMethods.entrySet() ) {
			String methodName = entry.getKey();
			BoxFunctionDeclaration methodNode = entry.getValue();

			if ( !calledMethods.contains( methodName ) ) {
				Diagnostic diagnostic = new Diagnostic(
				    ProjectContextProvider.positionToRange( methodNode.getPosition() ),
				    "Private method '" + methodNode.getName() + "' is never called.",
				    DiagnosticSeverity.Warning,
				    "boxlang",
				    UnusedPrivateMethodRule.ID
				);
				diagnostic.setTags( List.of( DiagnosticTag.Unnecessary ) );
				diagnostics.add( diagnostic );
			}
		}
	}

	// ============ Helper Methods ============

	private void visitChildren( BoxNode node ) {
		for ( BoxNode child : node.getChildren() ) {
			child.accept( this );
		}
	}
}
