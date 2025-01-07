package ortus.boxlang.lsp.workspace.context;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import ortus.boxlang.compiler.ast.expression.BoxArgument;
import ortus.boxlang.lsp.workspace.ProjectContextProvider.FileParseResult;

public record Newable(
    Long resourceId,
    String name,
    List<BoxArgument> args ) {

	private static Long idCounter = 0l;

	public static Newable replace( Newable res, FileParseResult result ) {
		return fromFileParseResult( 1l, result );
	}

	public static Newable fromFileParseResult( FileParseResult result ) {
		return fromFileParseResult( idCounter++, result );
	}

	private static Newable fromFileParseResult( Long id, FileParseResult result ) {
		return new Newable(
		    id,
		    nameFromURI( result.uri() ),
		    new ArrayList<>()
		);
	}

	private static String nameFromURI( URI docURI ) {
		String fileName = Path.of( docURI ).getFileName().toString();

		return fileName.substring( 0, fileName.lastIndexOf( "." ) );
	}
}
