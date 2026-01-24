package ortus.boxlang.lsp.workspace.completion;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Position;

import ortus.boxlang.compiler.ast.BoxClass;
import ortus.boxlang.compiler.ast.BoxNode;
import ortus.boxlang.compiler.ast.statement.BoxFunctionDeclaration;
import ortus.boxlang.lsp.workspace.BLASTTools;
import ortus.boxlang.lsp.workspace.FileParseResult;

/**
 * Analyzes the cursor position and surrounding context to determine
 * what kind of completion is appropriate.
 *
 * This is the foundation for context-aware completions in the BoxLang LSP.
 */
public class CompletionContext {

	// Patterns for detecting context from text
	// Note: \s* used (not \s+) to match even when cursor is immediately after keyword
	private static final Pattern		NEW_PATTERN				= Pattern.compile( "\\bnew\\s*(\\w[\\w\\d\\$\\-_\\.]*)?$", Pattern.CASE_INSENSITIVE );
	private static final Pattern		IMPORT_PATTERN			= Pattern.compile( "^\\s*import\\s*(\\w[\\w\\d\\$\\-_\\.]*)?$", Pattern.CASE_INSENSITIVE );
	private static final Pattern		EXTENDS_PATTERN			= Pattern.compile( "\\bextends\\s*(\\w[\\w\\d\\$\\-_\\.]*)?$", Pattern.CASE_INSENSITIVE );
	private static final Pattern		IMPLEMENTS_PATTERN		= Pattern.compile( "\\bimplements\\s*(\\w[\\w\\d\\$\\-_,\\s\\.]*)?$",
	    Pattern.CASE_INSENSITIVE );
	// Member access matches: identifier., identifier.partial, expr().partial, etc.
	// The receiver group captures what's before the last dot (simplified - may include parens)
	private static final Pattern		MEMBER_ACCESS_PATTERN	= Pattern.compile( "([\\w\\d\\$_\\)\\]]+)\\s*\\.\\s*(\\w[\\w\\d\\$_]*)?$" );
	private static final Pattern		BXM_TAG_PATTERN			= Pattern.compile( "<bx:(\\w*)$", Pattern.CASE_INSENSITIVE );
	// Pattern for BXM tag attributes: <bx:tagname followed by space and optional partial attribute name
	// Captures: group(1) = tag name, group(2) = partial attribute name (if any)
	private static final Pattern		BXM_TAG_ATTR_PATTERN	= Pattern.compile( "<bx:(\\w+)\\s+(?:[\\w\\-]+=[\"'][^\"']*[\"']\\s+)*([\\w\\-]*)$",
	    Pattern.CASE_INSENSITIVE );
	private static final Pattern		TEMPLATE_EXPR_PATTERN	= Pattern.compile( "#(\\w*)$" );
	private static final Pattern		IDENTIFIER_PATTERN		= Pattern.compile( "(\\w+)$" );

	// Instance fields
	private final CompletionContextKind	kind;
	private final String				triggerText;
	private final String				receiverText;		// Text before the dot in member access
	private final String				containingMethodName;
	private final String				containingClassName;
	private final int					argumentIndex;
	private final Position				cursorPosition;
	private final FileParseResult		fileParseResult;

	/**
	 * Private constructor - use analyze() factory method
	 */
	private CompletionContext(
	    CompletionContextKind kind,
	    String triggerText,
	    String receiverText,
	    String containingMethodName,
	    String containingClassName,
	    int argumentIndex,
	    Position cursorPosition,
	    FileParseResult fileParseResult ) {
		this.kind					= kind;
		this.triggerText			= triggerText;
		this.receiverText			= receiverText;
		this.containingMethodName	= containingMethodName;
		this.containingClassName	= containingClassName;
		this.argumentIndex			= argumentIndex;
		this.cursorPosition			= cursorPosition;
		this.fileParseResult		= fileParseResult;
	}

	/**
	 * Analyze the file and cursor position to determine the completion context.
	 *
	 * @param fileParseResult The parsed file
	 * @param params          The completion parameters from the client
	 *
	 * @return A CompletionContext describing the current context
	 */
	public static CompletionContext analyze( FileParseResult fileParseResult, CompletionParams params ) {
		Position	cursorPosition			= params.getPosition();
		String		lineText				= fileParseResult.readLine( cursorPosition.getLine() );
		int			cursorCol				= cursorPosition.getCharacter();
		String		textBeforeCursor		= lineText.substring( 0, Math.min( cursorCol, lineText.length() ) );

		// Find containing method and class from AST
		String		containingMethodName	= findContainingMethodName( fileParseResult, cursorPosition );
		String		containingClassName		= findContainingClassName( fileParseResult, cursorPosition );

		// Check if we're in a context where completion shouldn't be offered
		if ( isInsideStringLiteral( textBeforeCursor ) || isInsideComment( lineText, cursorCol ) ) {
			return new CompletionContext(
			    CompletionContextKind.NONE,
			    "",
			    null,
			    containingMethodName,
			    containingClassName,
			    -1,
			    cursorPosition,
			    fileParseResult
			);
		}

		// Check for BXM-specific contexts in template files
		if ( fileParseResult.isTemplate() ) {
			// Check for BXM tag attributes first (more specific than BXM tag)
			Matcher bxmTagAttrMatcher = BXM_TAG_ATTR_PATTERN.matcher( textBeforeCursor );
			if ( bxmTagAttrMatcher.find() ) {
				String	tagName		= bxmTagAttrMatcher.group( 1 );
				String	partialAttr	= bxmTagAttrMatcher.group( 2 ) != null ? bxmTagAttrMatcher.group( 2 ) : "";
				return new CompletionContext(
				    CompletionContextKind.BXM_TAG_ATTRIBUTE,
				    partialAttr,
				    tagName, // Use receiverText to store tag name
				    containingMethodName,
				    containingClassName,
				    -1,
				    cursorPosition,
				    fileParseResult
				);
			}

			// Check for BXM tag
			Matcher bxmTagMatcher = BXM_TAG_PATTERN.matcher( textBeforeCursor );
			if ( bxmTagMatcher.find() ) {
				return new CompletionContext(
				    CompletionContextKind.BXM_TAG,
				    bxmTagMatcher.group( 1 ) != null ? bxmTagMatcher.group( 1 ) : "",
				    null,
				    containingMethodName,
				    containingClassName,
				    -1,
				    cursorPosition,
				    fileParseResult
				);
			}

			// Check for template expression
			Matcher templateExprMatcher = TEMPLATE_EXPR_PATTERN.matcher( textBeforeCursor );
			if ( templateExprMatcher.find() ) {
				return new CompletionContext(
				    CompletionContextKind.TEMPLATE_EXPRESSION,
				    templateExprMatcher.group( 1 ) != null ? templateExprMatcher.group( 1 ) : "",
				    null,
				    containingMethodName,
				    containingClassName,
				    -1,
				    cursorPosition,
				    fileParseResult
				);
			}
		}

		// Check for import statement (must be checked before other patterns)
		Matcher importMatcher = IMPORT_PATTERN.matcher( textBeforeCursor );
		if ( importMatcher.find() ) {
			return new CompletionContext(
			    CompletionContextKind.IMPORT,
			    importMatcher.group( 1 ) != null ? importMatcher.group( 1 ) : "",
			    null,
			    containingMethodName,
			    containingClassName,
			    -1,
			    cursorPosition,
			    fileParseResult
			);
		}

		// Check for new expression
		Matcher newMatcher = NEW_PATTERN.matcher( textBeforeCursor );
		if ( newMatcher.find() ) {
			return new CompletionContext(
			    CompletionContextKind.NEW_EXPRESSION,
			    newMatcher.group( 1 ) != null ? newMatcher.group( 1 ) : "",
			    null,
			    containingMethodName,
			    containingClassName,
			    -1,
			    cursorPosition,
			    fileParseResult
			);
		}

		// Check for extends keyword
		Matcher extendsMatcher = EXTENDS_PATTERN.matcher( textBeforeCursor );
		if ( extendsMatcher.find() ) {
			return new CompletionContext(
			    CompletionContextKind.EXTENDS,
			    extendsMatcher.group( 1 ) != null ? extendsMatcher.group( 1 ) : "",
			    null,
			    containingMethodName,
			    containingClassName,
			    -1,
			    cursorPosition,
			    fileParseResult
			);
		}

		// Check for implements keyword
		Matcher implementsMatcher = IMPLEMENTS_PATTERN.matcher( textBeforeCursor );
		if ( implementsMatcher.find() ) {
			String	group		= implementsMatcher.group( 1 );
			// Get the last interface being typed (after the last comma if any)
			String	triggerText	= "";
			if ( group != null ) {
				String[] parts = group.split( "," );
				triggerText = parts[ parts.length - 1 ].trim();
			}
			return new CompletionContext(
			    CompletionContextKind.IMPLEMENTS,
			    triggerText,
			    null,
			    containingMethodName,
			    containingClassName,
			    -1,
			    cursorPosition,
			    fileParseResult
			);
		}

		// Check for member access (dot completion)
		Matcher memberAccessMatcher = MEMBER_ACCESS_PATTERN.matcher( textBeforeCursor );
		if ( memberAccessMatcher.find() ) {
			return new CompletionContext(
			    CompletionContextKind.MEMBER_ACCESS,
			    memberAccessMatcher.group( 2 ) != null ? memberAccessMatcher.group( 2 ) : "",
			    memberAccessMatcher.group( 1 ),
			    containingMethodName,
			    containingClassName,
			    -1,
			    cursorPosition,
			    fileParseResult
			);
		}

		// Check for function argument context
		int argumentIndex = calculateArgumentIndex( textBeforeCursor );
		if ( argumentIndex >= 0 ) {
			// Extract any partial text after the last comma or opening paren
			String	triggerText			= "";
			Matcher	identifierMatcher	= IDENTIFIER_PATTERN.matcher( textBeforeCursor );
			if ( identifierMatcher.find() ) {
				// Make sure this identifier is after the function call start
				int	identEnd			= identifierMatcher.end();
				int	lastParenOrComma	= Math.max( textBeforeCursor.lastIndexOf( '(' ), textBeforeCursor.lastIndexOf( ',' ) );
				if ( identEnd > lastParenOrComma ) {
					triggerText = identifierMatcher.group( 1 );
				}
			}

			return new CompletionContext(
			    CompletionContextKind.FUNCTION_ARGUMENT,
			    triggerText,
			    null,
			    containingMethodName,
			    containingClassName,
			    argumentIndex,
			    cursorPosition,
			    fileParseResult
			);
		}

		// Default: general identifier context
		String	triggerText			= "";
		Matcher	identifierMatcher	= IDENTIFIER_PATTERN.matcher( textBeforeCursor );
		if ( identifierMatcher.find() ) {
			triggerText = identifierMatcher.group( 1 );
		}

		return new CompletionContext(
		    CompletionContextKind.GENERAL,
		    triggerText,
		    null,
		    containingMethodName,
		    containingClassName,
		    -1,
		    cursorPosition,
		    fileParseResult
		);
	}

	/**
	 * Calculate the argument index (0-based) if we're inside a function call.
	 * Returns -1 if not inside a function call.
	 */
	private static int calculateArgumentIndex( String textBeforeCursor ) {
		int		parenDepth		= 0;
		int		bracketDepth	= 0;
		int		braceDepth		= 0;
		int		argumentIndex	= 0;
		boolean	inString		= false;
		char	stringChar		= 0;

		// Find the opening paren of the current function call
		int		funcCallStart	= -1;
		for ( int i = textBeforeCursor.length() - 1; i >= 0; i-- ) {
			char c = textBeforeCursor.charAt( i );

			// Track string state (going backwards)
			if ( ( c == '"' || c == '\'' ) && ( i == 0 || textBeforeCursor.charAt( i - 1 ) != '\\' ) ) {
				if ( !inString ) {
					inString	= true;
					stringChar	= c;
				} else if ( c == stringChar ) {
					inString = false;
				}
				continue;
			}

			if ( inString )
				continue;

			if ( c == ')' )
				parenDepth++;
			else if ( c == '(' ) {
				if ( parenDepth > 0 ) {
					parenDepth--;
				} else {
					// Found our opening paren
					funcCallStart = i;
					break;
				}
			} else if ( c == ']' )
				bracketDepth++;
			else if ( c == '[' )
				bracketDepth--;
			else if ( c == '}' )
				braceDepth++;
			else if ( c == '{' )
				braceDepth--;
		}

		if ( funcCallStart < 0 )
			return -1;

		// Check if there's a function name before the opening paren
		String beforeParen = textBeforeCursor.substring( 0, funcCallStart ).trim();
		if ( beforeParen.isEmpty() || !Character.isLetterOrDigit( beforeParen.charAt( beforeParen.length() - 1 ) ) ) {
			// No function name - might be a grouping paren
			return -1;
		}

		// Count commas to determine argument index
		String argsText = textBeforeCursor.substring( funcCallStart + 1 );
		inString		= false;
		parenDepth		= 0;
		bracketDepth	= 0;
		braceDepth		= 0;

		for ( int i = 0; i < argsText.length(); i++ ) {
			char c = argsText.charAt( i );

			if ( ( c == '"' || c == '\'' ) && ( i == 0 || argsText.charAt( i - 1 ) != '\\' ) ) {
				if ( !inString ) {
					inString	= true;
					stringChar	= c;
				} else if ( c == stringChar ) {
					inString = false;
				}
				continue;
			}

			if ( inString )
				continue;

			if ( c == '(' )
				parenDepth++;
			else if ( c == ')' )
				parenDepth--;
			else if ( c == '[' )
				bracketDepth++;
			else if ( c == ']' )
				bracketDepth--;
			else if ( c == '{' )
				braceDepth++;
			else if ( c == '}' )
				braceDepth--;
			else if ( c == ',' && parenDepth == 0 && bracketDepth == 0 && braceDepth == 0 ) {
				argumentIndex++;
			}
		}

		return argumentIndex;
	}

	/**
	 * Find the name of the method containing the cursor position.
	 */
	private static String findContainingMethodName( FileParseResult fileParseResult, Position cursorPosition ) {
		return fileParseResult.findAstRoot()
		    .map( root -> {
			    int line = cursorPosition.getLine() + 1; // BoxLang uses 1-based lines
			    int column = cursorPosition.getCharacter();

			    for ( BoxNode child : root.getDescendantsOfType( BoxFunctionDeclaration.class ) ) {
				    BoxFunctionDeclaration func = ( BoxFunctionDeclaration ) child;
				    if ( BLASTTools.containsPosition( func, line, column ) ) {
					    return func.getName();
				    }
			    }
			    return null;
		    } )
		    .orElse( null );
	}

	/**
	 * Find the name of the class containing the cursor position.
	 */
	private static String findContainingClassName( FileParseResult fileParseResult, Position cursorPosition ) {
		int		line	= cursorPosition.getLine() + 1;
		int		column	= cursorPosition.getCharacter();
		BoxNode	root	= fileParseResult.findAstRoot().orElse( null );

		if ( root == null ) {
			return null;
		}

		for ( BoxNode child : root.getDescendantsOfType( BoxClass.class ) ) {
			BoxClass clazz = ( BoxClass ) child;
			if ( BLASTTools.containsPosition( clazz, line, column ) ) {
				// Get class name from filename
				return getClassNameFromFile( fileParseResult );
			}
		}
		return null;
	}

	/**
	 * Extract class name from BoxClass node using the file URI.
	 * The class name is typically derived from the filename.
	 */
	private static String getClassNameFromFile( FileParseResult fileParseResult ) {
		String	uriString	= fileParseResult.getURI().toString();
		int		lastSlash	= uriString.lastIndexOf( '/' );
		String	filename	= lastSlash >= 0 ? uriString.substring( lastSlash + 1 ) : uriString;
		int		dotIndex	= filename.lastIndexOf( '.' );
		return dotIndex > 0 ? filename.substring( 0, dotIndex ) : filename;
	}

	/**
	 * Check if cursor position is inside a string literal.
	 * This is a simple heuristic that counts unescaped quotes.
	 */
	private static boolean isInsideStringLiteral( String textBeforeCursor ) {
		boolean	inDoubleQuote	= false;
		boolean	inSingleQuote	= false;

		for ( int i = 0; i < textBeforeCursor.length(); i++ ) {
			char c = textBeforeCursor.charAt( i );

			// Check for escape character
			if ( c == '\\' && i + 1 < textBeforeCursor.length() ) {
				i++; // Skip next character
				continue;
			}

			if ( c == '"' && !inSingleQuote ) {
				inDoubleQuote = !inDoubleQuote;
			} else if ( c == '\'' && !inDoubleQuote ) {
				inSingleQuote = !inSingleQuote;
			}
		}

		return inDoubleQuote || inSingleQuote;
	}

	/**
	 * Check if cursor position is inside a comment.
	 */
	private static boolean isInsideComment( String lineText, int cursorCol ) {
		// Check for single-line comment
		int singleLineComment = lineText.indexOf( "//" );
		if ( singleLineComment >= 0 && singleLineComment < cursorCol ) {
			// Make sure // is not inside a string
			String beforeComment = lineText.substring( 0, singleLineComment );
			if ( !isInsideStringLiteral( beforeComment ) ) {
				return true;
			}
		}

		// Check for multi-line comment start (/* ... ) - simple heuristic
		int multiLineStart = lineText.indexOf( "/*" );
		if ( multiLineStart >= 0 && multiLineStart < cursorCol ) {
			int multiLineEnd = lineText.indexOf( "*/", multiLineStart + 2 );
			if ( multiLineEnd < 0 || multiLineEnd >= cursorCol ) {
				String beforeComment = lineText.substring( 0, multiLineStart );
				if ( !isInsideStringLiteral( beforeComment ) ) {
					return true;
				}
			}
		}

		return false;
	}

	// ==================== GETTERS ====================

	/**
	 * Get the kind of completion context detected.
	 */
	public CompletionContextKind getKind() {
		return kind;
	}

	/**
	 * Get the text that triggered the completion (partial identifier being typed).
	 */
	public String getTriggerText() {
		return triggerText;
	}

	/**
	 * Get the receiver text (for member access, this is the text before the dot).
	 */
	public String getReceiverText() {
		return receiverText;
	}

	/**
	 * Get the name of the method containing the cursor, if any.
	 */
	public String getContainingMethodName() {
		return containingMethodName;
	}

	/**
	 * Get the name of the class containing the cursor, if any.
	 */
	public String getContainingClassName() {
		return containingClassName;
	}

	/**
	 * Get the argument index (0-based) if in a function call context.
	 * Returns -1 if not in a function call.
	 */
	public int getArgumentIndex() {
		return argumentIndex;
	}

	/**
	 * Get the cursor position.
	 */
	public Position getCursorPosition() {
		return cursorPosition;
	}

	/**
	 * Get the file parse result for additional context.
	 */
	public FileParseResult getFileParseResult() {
		return fileParseResult;
	}

	@Override
	public String toString() {
		return String.format(
		    "CompletionContext[kind=%s, triggerText='%s', receiver='%s', method='%s', class='%s', argIndex=%d]",
		    kind, triggerText, receiverText, containingMethodName, containingClassName, argumentIndex
		);
	}
}
