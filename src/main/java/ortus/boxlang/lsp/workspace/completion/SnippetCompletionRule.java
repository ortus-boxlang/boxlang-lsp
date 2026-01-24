package ortus.boxlang.lsp.workspace.completion;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.InsertTextFormat;

import ortus.boxlang.lsp.workspace.rules.IRule;

/**
 * Provides snippet completions for common BoxLang patterns.
 * Snippets are context-aware and only appear in appropriate locations.
 * 
 * Supports both short triggers (e.g., "fun") and full keyword triggers (e.g., "function").
 */
public class SnippetCompletionRule implements IRule<CompletionFacts, List<CompletionItem>> {

	/**
	 * Represents a snippet with its triggers, body, and context
	 */
	private static class Snippet {

		String		label;
		String[]	triggers;
		String		body;
		String		description;
		Context		context;

		Snippet( String label, String[] triggers, String body, String description, Context context ) {
			this.label			= label;
			this.triggers		= triggers;
			this.body			= body;
			this.description	= description;
			this.context		= context;
		}
	}

	/**
	 * Context in which a snippet should appear
	 */
	private enum Context {
		TOP_LEVEL,		// Outside class/interface
		CLASS_BODY,		// Inside class but outside methods
		FUNCTION_BODY,	// Inside function/method
		ANY				// Any context
	}

	// All available snippets
	private static final List<Snippet> SNIPPETS = new ArrayList<>();

	static {
		// Function definition snippets
		SNIPPETS.add( new Snippet(
		    "Function definition",
		    new String[] { "fun", "function", "func" },
		    "function ${1:name}(${2:params}) {\n\t$0\n}",
		    "Create a new function",
		    Context.CLASS_BODY
		) );

		// Public function with return type
		SNIPPETS.add( new Snippet(
		    "Public function with return type",
		    new String[] { "pubfun", "publicfunction" },
		    "public ${1:any} function ${2:name}(${3:params}) {\n\t$0\n}",
		    "Create a public function with return type",
		    Context.CLASS_BODY
		) );

		// Private function
		SNIPPETS.add( new Snippet(
		    "Private function",
		    new String[] { "privfun", "privatefunction" },
		    "private function ${1:name}(${2:params}) {\n\t$0\n}",
		    "Create a private function",
		    Context.CLASS_BODY
		) );

		// Class definition snippet
		SNIPPETS.add( new Snippet(
		    "Class definition",
		    new String[] { "class", "cls" },
		    "class ${1:ClassName} {\n\t$0\n}",
		    "Create a new class",
		    Context.TOP_LEVEL
		) );

		// Class with extends
		SNIPPETS.add( new Snippet(
		    "Class with extends",
		    new String[] { "classext", "extclass" },
		    "class ${1:ClassName} extends=\"${2:BaseClass}\" {\n\t$0\n}",
		    "Create a class that extends another class",
		    Context.TOP_LEVEL
		) );

		// Interface definition
		SNIPPETS.add( new Snippet(
		    "Interface definition",
		    new String[] { "interface", "int" },
		    "interface ${1:InterfaceName} {\n\t$0\n}",
		    "Create a new interface",
		    Context.TOP_LEVEL
		) );

		// If statement
		SNIPPETS.add( new Snippet(
		    "If statement",
		    new String[] { "if" },
		    "if (${1:condition}) {\n\t$0\n}",
		    "Create an if statement",
		    Context.FUNCTION_BODY
		) );

		// If-else statement
		SNIPPETS.add( new Snippet(
		    "If-else statement",
		    new String[] { "ifelse", "ife" },
		    "if (${1:condition}) {\n\t${2}\n} else {\n\t$0\n}",
		    "Create an if-else statement",
		    Context.FUNCTION_BODY
		) );

		// For loop
		SNIPPETS.add( new Snippet(
		    "For loop",
		    new String[] { "for" },
		    "for (var ${1:i} = ${2:1}; $1 <= ${3:10}; $1++) {\n\t$0\n}",
		    "Create a for loop",
		    Context.FUNCTION_BODY
		) );

		// For-in loop
		SNIPPETS.add( new Snippet(
		    "For-in loop",
		    new String[] { "forin", "foreach" },
		    "for (var ${1:item} in ${2:collection}) {\n\t$0\n}",
		    "Create a for-in loop",
		    Context.FUNCTION_BODY
		) );

		// While loop
		SNIPPETS.add( new Snippet(
		    "While loop",
		    new String[] { "while" },
		    "while (${1:condition}) {\n\t$0\n}",
		    "Create a while loop",
		    Context.FUNCTION_BODY
		) );

		// Do-while loop
		SNIPPETS.add( new Snippet(
		    "Do-while loop",
		    new String[] { "dowhile", "do" },
		    "do {\n\t$0\n} while (${1:condition});",
		    "Create a do-while loop",
		    Context.FUNCTION_BODY
		) );

		// Try-catch
		SNIPPETS.add( new Snippet(
		    "Try-catch",
		    new String[] { "try", "trycatch" },
		    "try {\n\t${1}\n} catch (any ${2:e}) {\n\t$0\n}",
		    "Create a try-catch block",
		    Context.FUNCTION_BODY
		) );

		// Try-catch-finally
		SNIPPETS.add( new Snippet(
		    "Try-catch-finally",
		    new String[] { "tryfinally", "trycatchfinally" },
		    "try {\n\t${1}\n} catch (any ${2:e}) {\n\t${3}\n} finally {\n\t$0\n}",
		    "Create a try-catch-finally block",
		    Context.FUNCTION_BODY
		) );

		// Switch statement
		SNIPPETS.add( new Snippet(
		    "Switch statement",
		    new String[] { "switch" },
		    "switch (${1:expression}) {\n\tcase ${2:value}:\n\t\t${3}\n\t\tbreak;\n\tdefault:\n\t\t$0\n}",
		    "Create a switch statement",
		    Context.FUNCTION_BODY
		) );

		// Property definition
		SNIPPETS.add( new Snippet(
		    "Property",
		    new String[] { "prop", "property" },
		    "property ${1:type} ${2:name};",
		    "Create a property",
		    Context.CLASS_BODY
		) );

		// Property with default value
		SNIPPETS.add( new Snippet(
		    "Property with default",
		    new String[] { "propdef", "propertydefault" },
		    "property ${1:type} ${2:name} = ${3:defaultValue};",
		    "Create a property with default value",
		    Context.CLASS_BODY
		) );

		// Constructor (init function)
		SNIPPETS.add( new Snippet(
		    "Constructor",
		    new String[] { "init", "constructor" },
		    "function init(${1:params}) {\n\t${2}\n\treturn this;\n}",
		    "Create a constructor function",
		    Context.CLASS_BODY
		) );

		// Getter method
		SNIPPETS.add( new Snippet(
		    "Getter method",
		    new String[] { "get", "getter" },
		    "function get${1:PropertyName}() {\n\treturn variables.${2:propertyName};\n}",
		    "Create a getter method",
		    Context.CLASS_BODY
		) );

		// Setter method
		SNIPPETS.add( new Snippet(
		    "Setter method",
		    new String[] { "set", "setter" },
		    "function set${1:PropertyName}(required ${2:type} ${3:value}) {\n\tvariables.${4:propertyName} = arguments.${3:value};\n\treturn this;\n}",
		    "Create a setter method",
		    Context.CLASS_BODY
		) );

		// Variable declaration
		SNIPPETS.add( new Snippet(
		    "Variable declaration",
		    new String[] { "var" },
		    "var ${1:name} = ${0:value};",
		    "Declare a variable",
		    Context.FUNCTION_BODY
		) );

		// Return statement
		SNIPPETS.add( new Snippet(
		    "Return statement",
		    new String[] { "ret", "return" },
		    "return ${0:value};",
		    "Return a value",
		    Context.FUNCTION_BODY
		) );

		// Throw statement
		SNIPPETS.add( new Snippet(
		    "Throw statement",
		    new String[] { "throw" },
		    "throw \"${1:Error message}\";",
		    "Throw an exception",
		    Context.FUNCTION_BODY
		) );
	}

	@Override
	public boolean when( CompletionFacts facts ) {
		CompletionContextKind kind = facts.getContext().getKind();

		// Don't provide snippets in specific contexts where they don't make sense
		if ( kind == CompletionContextKind.NONE
		    || kind == CompletionContextKind.MEMBER_ACCESS
		    || kind == CompletionContextKind.IMPORT
		    || kind == CompletionContextKind.NEW_EXPRESSION
		    || kind == CompletionContextKind.EXTENDS
		    || kind == CompletionContextKind.IMPLEMENTS
		    || kind == CompletionContextKind.BXM_TAG
		    || kind == CompletionContextKind.TEMPLATE_EXPRESSION ) {
			return false;
		}

		// Only provide snippets in GENERAL context
		return kind == CompletionContextKind.GENERAL;
	}

	@Override
	public void then( CompletionFacts facts, List<CompletionItem> result ) {
		CompletionContext	context					= facts.getContext();
		String				containingClassName		= context.getContainingClassName();
		String				containingMethodName	= context.getContainingMethodName();

		// Determine current context
		Context				currentContext;
		if ( containingMethodName != null ) {
			currentContext = Context.FUNCTION_BODY;
		} else if ( containingClassName != null ) {
			currentContext = Context.CLASS_BODY;
		} else {
			currentContext = Context.TOP_LEVEL;
		}

		// Add applicable snippets
		for ( Snippet snippet : SNIPPETS ) {
			// Check if snippet applies to current context
			if ( snippet.context != Context.ANY && snippet.context != currentContext ) {
				continue;
			}

			// Create a completion item for each trigger
			for ( String trigger : snippet.triggers ) {
				CompletionItem item = new CompletionItem();
				item.setLabel( trigger );
				item.setKind( CompletionItemKind.Snippet );
				item.setInsertText( snippet.body );
				item.setInsertTextFormat( InsertTextFormat.Snippet );
				item.setDetail( snippet.label );
				item.setDocumentation( snippet.description );
				// Use "0" prefix for sort text to prioritize snippets highly
				item.setSortText( "0" + trigger );

				result.add( item );
			}
		}
	}
}
