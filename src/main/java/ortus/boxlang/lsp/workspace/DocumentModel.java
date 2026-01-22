package ortus.boxlang.lsp.workspace;

import java.net.URI;
import java.util.List;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;

/**
 * Maintains the in-memory content of an open document and supports
 * both full and incremental text synchronization.
 */
public class DocumentModel {

	private final URI	uri;
	private String		content;
	private int			version;

	public DocumentModel( URI uri, String content, int version ) {
		this.uri		= uri;
		this.content	= content;
		this.version	= version;
	}

	public URI getUri() {
		return uri;
	}

	public String getContent() {
		return content;
	}

	public int getVersion() {
		return version;
	}

	/**
	 * Applies a list of content change events to the document.
	 * Supports both incremental (range-based) and full document changes.
	 *
	 * @param changes The list of changes to apply
	 * @param newVersion The new version number
	 * @return true if the changes were applied, false if the version was stale
	 */
	public synchronized boolean applyChanges( List<TextDocumentContentChangeEvent> changes, int newVersion ) {
		// Reject out-of-order versions
		if ( newVersion <= version ) {
			return false;
		}

		for ( TextDocumentContentChangeEvent change : changes ) {
			if ( change.getRange() == null ) {
				// Full document sync - replace entire content
				content = change.getText();
			} else {
				// Incremental sync - apply range-based change
				content = applyIncrementalChange( change );
			}
		}

		version = newVersion;
		return true;
	}

	/**
	 * Applies a single incremental change to the document content.
	 *
	 * @param change The change event with a range
	 * @return The updated content
	 */
	private String applyIncrementalChange( TextDocumentContentChangeEvent change ) {
		Range	range	= change.getRange();
		String	newText	= change.getText();

		int startOffset = positionToOffset( range.getStart() );
		int endOffset	= positionToOffset( range.getEnd() );

		// Bounds checking
		startOffset	= Math.max( 0, Math.min( startOffset, content.length() ) );
		endOffset	= Math.max( startOffset, Math.min( endOffset, content.length() ) );

		StringBuilder result = new StringBuilder();
		result.append( content.substring( 0, startOffset ) );
		result.append( newText );
		result.append( content.substring( endOffset ) );

		return result.toString();
	}

	/**
	 * Converts an LSP Position (line, character) to an offset in the document content.
	 *
	 * @param position The LSP position (0-indexed line and character)
	 * @return The offset in the content string
	 */
	private int positionToOffset( Position position ) {
		int		targetLine	= position.getLine();
		int		targetChar	= position.getCharacter();

		int		offset		= 0;
		int		currentLine	= 0;
		String	text		= content;

		// Iterate through lines until we reach the target line
		while ( currentLine < targetLine && offset < text.length() ) {
			int lineEnd = text.indexOf( '\n', offset );
			if ( lineEnd == -1 ) {
				// No more newlines - we're at the last line
				break;
			}
			offset = lineEnd + 1;
			currentLine++;
		}

		// Add the character offset within the line
		// Make sure we don't exceed the line length
		int lineStart	= offset;
		int lineEnd		= text.indexOf( '\n', lineStart );
		if ( lineEnd == -1 ) {
			lineEnd = text.length();
		}

		int lineLength	= lineEnd - lineStart;
		int charOffset	= Math.min( targetChar, lineLength );

		return offset + charOffset;
	}

	/**
	 * Gets the content of a specific line.
	 *
	 * @param lineNumber 0-indexed line number
	 * @return The line content, or empty string if line doesn't exist
	 */
	public String getLine( int lineNumber ) {
		String[] lines = content.split( "\n", -1 );
		if ( lineNumber >= 0 && lineNumber < lines.length ) {
			return lines[ lineNumber ];
		}
		return "";
	}

	/**
	 * Updates the content directly (used for full sync).
	 *
	 * @param newContent The new content
	 * @param newVersion The new version
	 */
	public synchronized void setContent( String newContent, int newVersion ) {
		this.content	= newContent;
		this.version	= newVersion;
	}
}
