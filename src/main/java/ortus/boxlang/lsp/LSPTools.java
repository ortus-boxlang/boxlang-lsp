package ortus.boxlang.lsp;

import java.net.URI;
import java.net.URISyntaxException;

public class LSPTools {

	public static URI convertDocumentURI( String docURI ) {
		try {
			return new URI( docURI );
		} catch ( URISyntaxException e ) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}
}
