package ortus.boxlang.lsp.workspace;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;

import org.eclipse.lsp4j.Range;

/**
 * A lightweight representation of a class symbol for caching purposes.
 */
public class ClassSymbol {

	private final String	name;
	private final Range		location;
	private final String	fileUri;
	private final Instant	lastModified;

	public ClassSymbol( String name, Range location, String fileUri, Instant lastModified ) {
		this.name			= name;
		this.location		= location;
		this.fileUri		= fileUri;
		this.lastModified	= lastModified;
	}

	public String name() {
		return name;
	}

	public Range location() {
		return location;
	}

	public String fileUri() {
		return fileUri;
	}

	public Instant lastModified() {
		return lastModified;
	}

	@Override
	public boolean equals( Object obj ) {
		if ( this == obj )
			return true;
		if ( obj == null || getClass() != obj.getClass() )
			return false;
		ClassSymbol that = ( ClassSymbol ) obj;
		return Objects.equals( name, that.name ) &&
		    Objects.equals( location, that.location ) &&
		    Objects.equals( fileUri, that.fileUri ) &&
		    Objects.equals( lastModified, that.lastModified );
	}

	@Override
	public int hashCode() {
		return Objects.hash( name, location, fileUri, lastModified );
	}

	@Override
	public String toString() {
		return "ClassSymbol{" +
		    "name='" + name + '\'' +
		    ", location=" + location +
		    ", fileUri='" + fileUri + '\'' +
		    ", lastModified=" + lastModified +
		    '}';
	}
}