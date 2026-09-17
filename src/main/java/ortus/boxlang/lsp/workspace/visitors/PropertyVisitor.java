package ortus.boxlang.lsp.workspace.visitors;

import java.util.ArrayList;
import java.util.List;

import ortus.boxlang.compiler.ast.statement.BoxAnnotation;
import ortus.boxlang.compiler.ast.statement.BoxProperty;
import ortus.boxlang.compiler.ast.visitor.VoidBoxVisitor;
import ortus.boxlang.lsp.workspace.BLASTTools;
import ortus.boxlang.lsp.workspace.types.ParsedProperty;

public class PropertyVisitor extends VoidBoxVisitor {

	List<ParsedProperty> properties = new ArrayList<ParsedProperty>();

	public List<ParsedProperty> getProperties() {
		return properties;
	}

	public void visit( BoxProperty node ) {
		properties.add( new ParsedProperty( getName( node ), getType( node ), node ) );
	}

	private String getName( BoxProperty node ) {
		return BLASTTools.getPropertyName( node ).orElseGet( () -> node.getAllAnnotations().stream()
		    .findFirst()
		    .flatMap( annotation -> annotation.getValue() == null
		        ? BLASTTools.getAnnotationName( annotation )
		        : BLASTTools.getAnnotationValue( annotation ) )
		    .orElse( null ) );
	}

	private String getType( BoxProperty node ) {
		BoxAnnotation typeAnnotation = node.getAllAnnotations().stream()
		    .filter( annotation -> BLASTTools.getAnnotationName( annotation ).filter( name -> name.equalsIgnoreCase( "type" ) ).isPresent() )
		    .findFirst()
		    .orElseGet( () -> node.getAllAnnotations().get( 0 ) );
		return typeAnnotation.getValue() == null
		    ? BLASTTools.getAnnotationName( typeAnnotation ).orElse( null )
		    : BLASTTools.getAnnotationValue( typeAnnotation ).orElse( null );
	}

}
