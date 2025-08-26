package ortus.boxlang.lsp.workspace;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import ortus.boxlang.compiler.ast.statement.BoxProperty;
import ortus.boxlang.runtime.dynamic.casters.StringCaster;

public class BLASTTools {

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

	public static Range positionToRange( ortus.boxlang.compiler.ast.Position pos ) {
		return new Range(
		    new Position( pos.getStart().getLine() - 1, pos.getStart().getColumn() ),
		    new Position( pos.getEnd().getLine() - 1, pos.getEnd().getColumn() ) );
	}
}
