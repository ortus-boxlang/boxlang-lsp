package ortus.boxlang.lsp.workspace;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ortus.boxlang.compiler.ast.BoxNode;
import ortus.boxlang.compiler.ast.expression.BoxArrayAccess;
import ortus.boxlang.compiler.ast.expression.BoxAssignment;
import ortus.boxlang.compiler.ast.expression.BoxDotAccess;
import ortus.boxlang.compiler.ast.expression.BoxIdentifier;
import ortus.boxlang.compiler.ast.expression.BoxStringLiteral;
import ortus.boxlang.compiler.ast.expression.BoxStructLiteral;
import ortus.boxlang.compiler.parser.Parser;
import ortus.boxlang.compiler.parser.ParsingResult;
import ortus.boxlang.lsp.App;

/**
 * Extracts static {@code this.mappings} entries from an {@code Application.bx}
 * or {@code Application.cfc} file by parsing it with the BoxLang parser and
 * walking the resulting AST.
 *
 * <p>
 * Two source patterns are recognised:
 * <ol>
 * <li>Struct-literal form — {@code this.mappings = { "/key": "/value", ... }}
 * <li>Bracket-assignment form — {@code this.mappings["/key"] = "/value"}
 * </ol>
 *
 * <p>
 * Only entries where <em>both</em> key and value are static string literals
 * are returned. Dynamic values are silently skipped. The returned map contains
 * raw string values; path resolution is the caller's responsibility.
 */
public class ApplicationBxMappingExtractor {

	/**
	 * Extract {@code this.mappings} entries from the given file.
	 *
	 * @param filePath path to an Application.bx / Application.cfc file
	 *
	 * @return map of raw key → raw value; never null, empty if file missing,
	 *         unparseable, or contains no static {@code this.mappings} entries
	 */
	public static Map<String, String> extract( Path filePath ) {
		Map<String, String> result = new LinkedHashMap<>();

		if ( filePath == null || !Files.isRegularFile( filePath ) ) {
			return result;
		}

		try {
			Parser			parser			= new Parser();
			ParsingResult	parsingResult	= parser.parse( filePath.toFile() );

			if ( parsingResult == null || parsingResult.getRoot() == null ) {
				return result;
			}

			BoxNode				root		= parsingResult.getRoot();

			// Walk every assignment expression in the file
			List<BoxAssignment>	assignments	= root.getDescendantsOfType( BoxAssignment.class );
			for ( BoxAssignment assignment : assignments ) {
				var left = assignment.getLeft();

				if ( left instanceof BoxDotAccess dotAccess ) {
					// Struct-literal form: this.mappings = { ... }
					if ( isThisDotMappings( dotAccess ) &&
					    assignment.getRight() instanceof BoxStructLiteral structLit ) {
						collectStructLiteralEntries( structLit, result );
					}
				} else if ( left instanceof BoxArrayAccess arrayAccess ) {
					// Bracket-assignment form: this.mappings["/key"] = "/value"
					if ( isThisDotMappingsAccess( arrayAccess ) &&
					    arrayAccess.getAccess() instanceof BoxStringLiteral keyLit &&
					    assignment.getRight() instanceof BoxStringLiteral valueLit ) {
						result.put( keyLit.getValue(), valueLit.getValue() );
					}
				}
			}
		} catch ( Exception e ) {
			if ( App.logger != null ) {
				App.logger.warn( "Failed to extract mappings from " + filePath, e );
			}
		}

		return result;
	}

	// ────────────────────────────────────────────────────────────────────────────
	// Helpers
	// ────────────────────────────────────────────────────────────────────────────

	/**
	 * Returns true if the given DotAccess node represents {@code this.mappings}.
	 */
	private static boolean isThisDotMappings( BoxDotAccess dotAccess ) {
		return dotAccess.getContext() instanceof BoxIdentifier contextId &&
		    contextId.getName().equalsIgnoreCase( "this" ) &&
		    dotAccess.getAccess() instanceof BoxIdentifier accessId &&
		    accessId.getName().equalsIgnoreCase( "mappings" );
	}

	/**
	 * Returns true if the given ArrayAccess node represents
	 * {@code this.mappings[...]}.
	 */
	private static boolean isThisDotMappingsAccess( BoxArrayAccess arrayAccess ) {
		return arrayAccess.getContext() instanceof BoxDotAccess dotAccess &&
		    isThisDotMappings( dotAccess );
	}

	/**
	 * Collect all static key/value pairs from a struct literal into the result
	 * map. Entries where either the key or value is not a string literal are
	 * silently skipped.
	 */
	private static void collectStructLiteralEntries( BoxStructLiteral structLit, Map<String, String> result ) {
		var values = structLit.getValues();
		for ( int i = 0; i + 1 < values.size(); i += 2 ) {
			var	keyNode		= values.get( i );
			var	valueNode	= values.get( i + 1 );

			// Key can be a BoxStringLiteral or a BoxIdentifier (bare word key);
			// we only accept string-literal keys to stay faithful to the spec.
			if ( keyNode instanceof BoxStringLiteral keyLit &&
			    valueNode instanceof BoxStringLiteral valueLit ) {
				result.put( keyLit.getValue(), valueLit.getValue() );
			}
		}
	}
}
