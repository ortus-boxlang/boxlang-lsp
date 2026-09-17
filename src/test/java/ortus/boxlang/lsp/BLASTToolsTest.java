package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import ortus.boxlang.compiler.ast.expression.BoxFQN;
import ortus.boxlang.compiler.ast.expression.BoxIdentifier;
import ortus.boxlang.compiler.ast.expression.BoxMethodInvocation;
import ortus.boxlang.compiler.ast.expression.BoxStringLiteral;
import ortus.boxlang.compiler.ast.statement.BoxAnnotation;
import ortus.boxlang.compiler.ast.statement.BoxProperty;
import ortus.boxlang.lsp.workspace.BLASTTools;

class BLASTToolsTest extends BaseTest {

	@Test
	void optionalNodeValuesUseSemanticValuesWhenSourceTextIsMissing() {
		BoxIdentifier		identifier	= new BoxIdentifier( "field", null, null );
		BoxMethodInvocation	method		= new BoxMethodInvocation( identifier, new BoxIdentifier( "object", null, null ), List.of(), null, null );
		BoxFQN				key			= new BoxFQN( "name", null, null );
		BoxStringLiteral	value		= new BoxStringLiteral( "field", null, null );
		BoxAnnotation		annotation	= new BoxAnnotation( key, value, null, null );
		BoxProperty			property	= new BoxProperty( List.of( annotation ), List.of(), List.of(), null, null );

		assertThat( BLASTTools.getSourceText( identifier ) ).isEqualTo( Optional.empty() );
		assertThat( BLASTTools.getName( identifier ) ).isEqualTo( Optional.of( "field" ) );
		assertThat( BLASTTools.getName( method ) ).isEqualTo( Optional.of( "field" ) );
		assertThat( BLASTTools.getValue( key ) ).isEqualTo( Optional.of( "name" ) );
		assertThat( BLASTTools.getValue( value ) ).isEqualTo( Optional.of( "field" ) );
		assertThat( BLASTTools.getAnnotationName( annotation ) ).isEqualTo( Optional.of( "name" ) );
		assertThat( BLASTTools.getAnnotationValue( annotation ) ).isEqualTo( Optional.of( "field" ) );
		assertThat( BLASTTools.getPropertyName( property ) ).isEqualTo( Optional.of( "field" ) );
	}
}
