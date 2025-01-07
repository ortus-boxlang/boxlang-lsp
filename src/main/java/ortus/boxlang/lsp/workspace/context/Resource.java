package ortus.boxlang.lsp.workspace.context;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

import ortus.boxlang.lsp.workspace.ProjectContextProvider.FileParseResult;

public record Resource(
    Long id,
    URI path,
    Instant lastModified,
    String hash,
    Long size ) {

	private static long idCounter = 0;

	public static Resource replace( Resource res, FileParseResult result ) {
		return fromFileParseResult( res.id(), result );
	}

	public static Resource fromFileParseResult( FileParseResult result ) {
		return fromFileParseResult( idCounter++, result );
	}

	private static Resource fromFileParseResult( Long id, FileParseResult result ) {
		Instant	lastModified	= Instant.now();
		String	hash			= "";
		Long	size			= 0l;

		try {
			lastModified	= Files.getLastModifiedTime( Path.of( result.uri() ) ).toInstant();
			size			= Files.size( Path.of( result.uri() ) );
			hash			= getMD5( result.uri() );
		} catch ( IOException e ) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return new Resource(
		    id,
		    result.uri(),
		    lastModified,
		    hash,
		    size
		);
	}

	public static String getMD5( URI docUri ) throws IOException {
		MessageDigest md;
		try {
			md = MessageDigest.getInstance( "MD5" );
			byte[] digest = md.digest();

			try ( InputStream is = Files.newInputStream( Paths.get( "file.txt" ) );
			    DigestInputStream dis = new DigestInputStream( is, md ) ) {
				/* Read decorated stream (dis) to EOF as normal... */
			}

			return new String( digest );
		} catch ( NoSuchAlgorithmException e ) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return "";

	}

}
