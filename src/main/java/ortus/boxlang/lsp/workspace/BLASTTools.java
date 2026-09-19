package ortus.boxlang.lsp.workspace;

import java.util.Optional;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import ortus.boxlang.compiler.ast.BoxNode;
import ortus.boxlang.compiler.ast.expression.BoxFQN;
import ortus.boxlang.compiler.ast.expression.BoxFunctionInvocation;
import ortus.boxlang.compiler.ast.expression.BoxIdentifier;
import ortus.boxlang.compiler.ast.expression.BoxMethodInvocation;
import ortus.boxlang.compiler.ast.expression.BoxStringLiteral;
import ortus.boxlang.compiler.ast.statement.BoxAnnotation;
import ortus.boxlang.compiler.ast.statement.BoxDocumentationAnnotation;
import ortus.boxlang.compiler.ast.statement.BoxProperty;
import ortus.boxlang.runtime.dynamic.casters.StringCaster;

public class BLASTTools {

	/**
	 * Get source text when the AST node retained it.
	 */
	public static Optional<String> getSourceText( BoxNode node ) {
		return Optional.ofNullable( node ).map( BoxNode::getSourceText );
	}

	public static Optional<String> getName( BoxIdentifier node ) {
		return Optional.ofNullable( node ).map( BoxIdentifier::getName );
	}

	public static Optional<String> getName( BoxFunctionInvocation node ) {
		return Optional.ofNullable( node ).map( BoxFunctionInvocation::getName );
	}

	public static Optional<String> getName( BoxMethodInvocation node ) {
		if ( node == null || node.getName() == null ) {
			return Optional.empty();
		}

		BoxNode name = node.getName();
		if ( name instanceof BoxIdentifier identifier ) {
			return getName( identifier );
		}
		if ( name instanceof BoxFQN fqn ) {
			return getValue( fqn );
		}
		if ( name instanceof BoxStringLiteral literal ) {
			return getValue( literal );
		}
		return getSourceText( name );
	}

	public static Optional<String> getValue( BoxFQN node ) {
		return Optional.ofNullable( node ).map( BoxFQN::getValue );
	}

	public static Optional<String> getValue( BoxStringLiteral node ) {
		return Optional.ofNullable( node ).map( BoxStringLiteral::getValue );
	}

	public static Optional<String> getValue( BoxNode node ) {
		if ( node instanceof BoxFQN fqn ) {
			return getValue( fqn );
		}
		if ( node instanceof BoxStringLiteral literal ) {
			return getValue( literal );
		}
		if ( node instanceof BoxIdentifier identifier ) {
			return getName( identifier );
		}
		return getSourceText( node );
	}

	public static Optional<String> getAnnotationName( BoxAnnotation annotation ) {
		return annotation == null ? Optional.empty() : getValue( annotation.getKey() );
	}

	public static Optional<String> getAnnotationValue( BoxAnnotation annotation ) {
		return annotation == null ? Optional.empty() : getValue( annotation.getValue() );
	}

	public static Optional<String> getAnnotationName( BoxDocumentationAnnotation annotation ) {
		return annotation == null ? Optional.empty() : getValue( annotation.getKey() );
	}

	public static Optional<String> getAnnotationValue( BoxDocumentationAnnotation annotation ) {
		return annotation == null ? Optional.empty() : getValue( annotation.getValue() );
	}

	public static Optional<String> getPropertyName( BoxProperty property ) {
		if ( property == null ) {
			return Optional.empty();
		}

		for ( var annotation : property.getAllAnnotations() ) {
			if ( getAnnotationName( annotation ).filter( name -> name.equalsIgnoreCase( "name" ) ).isPresent() ) {
				Object value = annotation.getValue() == null ? null : annotation.getValue().getAsSimpleValue();
				return Optional.ofNullable( value ).map( item -> StringCaster.cast( item ) );
			}
		}

		return Optional.empty();
	}

	public static Range positionToRange( ortus.boxlang.compiler.ast.Position pos ) {
		return new Range(
		    new Position( pos.getStart().getLine() - 1, pos.getStart().getColumn() ),
		    new Position( pos.getEnd().getLine() - 1, pos.getEnd().getColumn() ) );
	}

	public static boolean containsPosition( BoxNode node, int line, int column ) {
		ortus.boxlang.compiler.ast.Position nodePos = node.getPosition();

		if ( nodePos == null ) {
			return false;
		}

		int	boxStartLine	= nodePos.getStart().getLine();
		int	boxStartCol		= nodePos.getStart().getColumn();
		int	boxEndLine		= nodePos.getEnd().getLine();
		int	boxEndCol		= nodePos.getEnd().getColumn();

		if ( node instanceof BoxFunctionInvocation bfi ) {
			boxEndLine	= boxStartLine;
			boxEndCol	= boxStartCol + bfi.getName().length();
		}

		if ( line < boxStartLine || line > boxEndLine ) {
			return false;
		}

		if ( line == boxStartLine && column < boxStartCol ) {
			return false;
		}

		if ( line == boxEndLine && column > boxEndCol ) {
			return false;
		}

		return true;
	}
}
