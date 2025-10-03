package ortus.boxlang.lsp.workspace;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.PreviousResultId;

public class DiagnosticReport {

	private URI					fileURI;
	private long				resultId	= 0;
	private List<Diagnostic>	diagnostics	= new ArrayList<>();

	public DiagnosticReport( URI fileURI ) {
		this.fileURI = fileURI;
	}

	public List<Diagnostic> getDiagnostics() {
		return diagnostics;
	}

	public void setDiagnostics( List<Diagnostic> diagnostics ) {
		this.diagnostics = diagnostics;
		this.resultId++;
	}

	public URI getFileURI() {
		return fileURI;
	}

	public long getResultId() {
		return resultId;
	}

	public boolean matches( PreviousResultId previousResultId ) {
		if ( previousResultId == null || previousResultId.getValue() == null ) {
			return false;
		}

		if ( this.fileURI.equals( previousResultId ) == false ) {
			return false;
		}

		try {
			long	prevId	= Long.parseLong( previousResultId.getValue() );
			boolean	matches	= this.resultId == prevId;

			return matches;
		} catch ( NumberFormatException nfe ) {
			return false;
		}
	}
}
