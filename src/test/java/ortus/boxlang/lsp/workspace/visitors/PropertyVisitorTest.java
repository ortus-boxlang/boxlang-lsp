package ortus.boxlang.lsp.workspace.visitors;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.List;

import org.eclipse.lsp4j.CompletionItemKind;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import ortus.boxlang.compiler.ast.BoxNode;
import ortus.boxlang.compiler.parser.BoxSourceType;
import ortus.boxlang.compiler.parser.Parser;
import ortus.boxlang.lsp.BaseTest;
import ortus.boxlang.lsp.workspace.types.ParsedProperty;

class PropertyVisitorTest extends BaseTest {

	@ParameterizedTest
	@EnumSource( value = BoxSourceType.class, names = { "BOXSCRIPT", "CFSCRIPT" } )
	void propertyCompletionsRemainUsableWithoutRetainingTheSourceAst( BoxSourceType sourceType ) throws Exception {
		ReferenceQueue<BoxNode>			queue		= new ReferenceQueue<>();
		PropertyMetadata				metadata	= extractProperties( sourceType, queue );

		Reference<? extends BoxNode>	collected	= null;
		for ( int attempt = 0; attempt < 20 && collected == null; attempt++ ) {
			System.gc();
			collected = queue.remove( 100 );
		}
		Reference.reachabilityFence( metadata );

		assertWithMessage( "Cached property metadata must not keep its source AST alive" )
		    .that( collected ).isSameInstanceAs( metadata.ast() );
		assertThat( metadata.properties() ).hasSize( 1 );
		var completion = metadata.properties().getFirst().asCompletionItem();
		assertThat( completion.getLabel() ).isEqualTo( "username" );
		assertThat( completion.getInsertText() ).isEqualTo( "username" );
		assertThat( completion.getKind() ).isEqualTo( CompletionItemKind.Property );
		assertThat( completion.getDetail() ).isEqualTo( "string" );
	}

	private PropertyMetadata extractProperties( BoxSourceType sourceType, ReferenceQueue<BoxNode> queue ) throws Exception {
		String	keyword	= sourceType == BoxSourceType.CFSCRIPT ? "component" : "class";
		var		result	= new Parser().parse( keyword + " { property name=\"username\" type=\"string\"; }", sourceType, true, false );
		assertThat( result.getIssues() ).isEmpty();
		BoxNode root = result.getRoot();
		assertThat( root ).isNotNull();
		PropertyVisitor visitor = new PropertyVisitor();
		root.accept( visitor );
		return new PropertyMetadata( visitor.getProperties(), new WeakReference<>( root, queue ) );
	}

	private record PropertyMetadata( List<ParsedProperty> properties, WeakReference<BoxNode> ast ) {
	}
}
