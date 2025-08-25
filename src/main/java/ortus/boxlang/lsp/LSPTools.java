package ortus.boxlang.lsp;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.lang3.StringUtils;

public class LSPTools {

	public static boolean canWalkFile( Path path ) {
		try {
			return Files.isRegularFile( path )
			    && StringUtils.endsWithAny(
			        path.toString(),
			        ".bx",
			        ".bxs",
			        ".bxm",
			        ".cfc",
			        ".cfs",
			        ".cfm" )
			    && !LSPTools.isJavaBytecode( path.toFile() );
		} catch ( Exception e ) {
			App.logger.debug( String.format( "Unable to walk file {}", path ), e );
			return false;
		}
	}

	public static URI convertDocumentURI( String docURI ) {
		try {
			return new URI( docURI );
		} catch ( URISyntaxException e ) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	public static boolean isJavaBytecode( File sourceFile ) {
		try ( FileInputStream fis = new FileInputStream( sourceFile );
		    DataInputStream dis = new DataInputStream( fis ) ) {
			// File may be empty! At least 4 bytes are needed to read an int
			if ( dis.available() < 4 ) {
				return false;
			}
			// Are we the Java Magic number?
			return dis.readInt() == 0xCAFEBABE;
		} catch ( IOException e ) {
			throw new RuntimeException( "Failed to read file", e );
		}
	}
}
