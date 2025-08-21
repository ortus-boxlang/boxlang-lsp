package ortus.boxlang.lsp.workspace;

import javax.swing.text.Position;

import ortus.boxlang.compiler.ast.BoxNode;
import ortus.boxlang.compiler.ast.statement.BoxProperty;
import ortus.boxlang.runtime.dynamic.casters.StringCaster;

public class BLASTTools {

	public static BoxNode findNodeAtPosition( BoxNode node, Position position ) {
		return null;
	}

	/**
	 * Get the name of a BoxProperty.
	 * 
	 * @param property
	 * 
	 * @return
	 */
	public static String getPropertyName( BoxProperty property ) {
		for ( var anno : property.getAllAnnotations() ) {
			if ( anno.getKey().getSourceText().equalsIgnoreCase( "name" ) ) {
				return StringCaster.cast( anno.getValue().getAsSimpleValue() );
			}
		}

		return null;
	}
}
