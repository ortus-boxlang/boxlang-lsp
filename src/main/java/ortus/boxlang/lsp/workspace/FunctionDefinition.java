package ortus.boxlang.lsp.workspace;

import java.net.URI;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import ortus.boxlang.compiler.ast.Point;
import ortus.boxlang.compiler.ast.statement.BoxFunctionDeclaration;

public class FunctionDefinition {

	private URI			fileURI;
	private Location	location;
	private String		name;

	public static FunctionDefinition fromASTNode( URI fileURI, BoxFunctionDeclaration ASTNode ) {
		FunctionDefinition newDef = new FunctionDefinition();

		newDef.location	= FunctionDefinition.toLocation( fileURI, ASTNode );
		newDef.name		= ASTNode.getName();
		newDef.fileURI	= fileURI;

		return newDef;
	}

	public Location getLocation() {
		return this.location;
	}

	public String getFunctionName() {
		return this.name;
	}

	public URI getFileURI() {
		return this.fileURI;
	}

	public void setFileURI( URI fileURI ) {
		this.fileURI = fileURI;
	}

	private static Location toLocation( URI fileURI, BoxFunctionDeclaration ASTNode ) {
		Location							loc		= new Location();

		ortus.boxlang.compiler.ast.Position	pos		= ASTNode.getPosition();
		Point								start	= pos.getStart();
		Point								end		= pos.getEnd();

		loc.setUri( fileURI.toString() );
		loc.setRange( new Range(
		    new Position( start.getLine() - 1, start.getColumn() ),
		    new Position( end.getLine() - 1, end.getColumn() + 1 ) ) );

		return loc;
	}
}
