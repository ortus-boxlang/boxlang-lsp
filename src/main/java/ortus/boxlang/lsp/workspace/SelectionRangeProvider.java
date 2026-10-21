package ortus.boxlang.lsp.workspace;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SelectionRange;

import ortus.boxlang.compiler.ast.BoxNode;

/**
 * Builds textDocument/selectionRange results from a document's AST.
 *
 * Each chain starts at the smallest node containing the cursor and walks out through
 * its ancestors, so the editor's expand-selection command follows the code structure.
 */
public class SelectionRangeProvider {

	private SelectionRangeProvider() {
	}

	// Returns one chain per position, in request order. Positions with no containing
	// node (empty, broken, or outside the AST) get a caret-only range instead of an error.
	public static List<SelectionRange> getSelectionRanges( Optional<BoxNode> astRoot, List<Position> positions ) {
		List<SelectionRange> results = new ArrayList<>();

		if ( positions == null ) {
			return results;
		}

		for ( Position position : positions ) {
			results.add( astRoot.map( root -> buildChain( root, position ) ).orElseGet( () -> caretOnly( position ) ) );
		}

		return results;
	}

	// Descends to the innermost node containing the position, then links its ancestors
	// from the outside in, dropping any range that does not strictly contain the one below.
	private static SelectionRange buildChain( BoxNode root, Position position ) {
		BoxNode innermost = findInnermostNode( root, position );

		if ( innermost == null ) {
			return caretOnly( position );
		}

		List<Range> ranges = new ArrayList<>();
		for ( BoxNode node = innermost; node != null; node = node.getParent() ) {
			if ( node.getPosition() == null ) {
				continue;
			}

			Range range = BLASTTools.positionToRange( node.getPosition() );
			if ( ranges.isEmpty() || strictlyContains( range, ranges.get( ranges.size() - 1 ) ) ) {
				ranges.add( range );
			}
		}

		SelectionRange chain = null;
		for ( int i = ranges.size() - 1; i >= 0; i-- ) {
			chain = new SelectionRange( ranges.get( i ), chain );
		}

		return chain;
	}

	// Walks down from the root, always into a child that contains the position. A child the
	// cursor is strictly inside wins over one it merely touches at the end boundary.
	private static BoxNode findInnermostNode( BoxNode root, Position position ) {
		if ( !contains( root, position, true ) ) {
			return null;
		}

		BoxNode current = root;
		while ( true ) {
			BoxNode next = null;
			for ( BoxNode child : current.getChildren() ) {
				if ( contains( child, position, false ) ) {
					next = child;
					break;
				}
				if ( next == null && contains( child, position, true ) ) {
					next = child;
				}
			}

			if ( next == null ) {
				return current;
			}
			current = next;
		}
	}

	// Checks a node's range against an LSP position. Ranges are end-exclusive; with
	// includeEnd, a cursor sitting just after the last character also counts as inside.
	private static boolean contains( BoxNode node, Position position, boolean includeEnd ) {
		if ( node == null || node.getPosition() == null ) {
			return false;
		}

		Range range = BLASTTools.positionToRange( node.getPosition() );
		if ( compare( position, range.getStart() ) < 0 ) {
			return false;
		}

		int endComparison = compare( position, range.getEnd() );
		return includeEnd ? endComparison <= 0 : endComparison < 0;
	}

	private static boolean strictlyContains( Range outer, Range inner ) {
		return compare( outer.getStart(), inner.getStart() ) <= 0
		    && compare( outer.getEnd(), inner.getEnd() ) >= 0
		    && !outer.equals( inner );
	}

	private static int compare( Position a, Position b ) {
		if ( a.getLine() != b.getLine() ) {
			return Integer.compare( a.getLine(), b.getLine() );
		}
		return Integer.compare( a.getCharacter(), b.getCharacter() );
	}

	private static SelectionRange caretOnly( Position position ) {
		Position caret = new Position( position.getLine(), position.getCharacter() );
		return new SelectionRange( new Range( caret, caret ), null );
	}
}
