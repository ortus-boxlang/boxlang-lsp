package ortus.boxlang.lsp.workspace.completion;

import java.util.List;
import java.util.Map;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;

import ortus.boxlang.compiler.ast.statement.BoxFunctionDeclaration;
import ortus.boxlang.lsp.workspace.BLASTTools;
import ortus.boxlang.lsp.workspace.FileParseResult;
import ortus.boxlang.lsp.workspace.rules.IRule;
import ortus.boxlang.lsp.workspace.visitors.VariableScopeCollectorVisitor;
import ortus.boxlang.lsp.workspace.visitors.VariableScopeCollectorVisitor.VariableInfo;

/**
 * Completion rule for variables based on scope.
 * Provides completions for:
 * - Local variables
 * - Function arguments
 * - Class properties
 * - Scope keywords (this, variables, etc.)
 */
public class VariableCompletionRule implements IRule<CompletionFacts, List<CompletionItem>> {

	@Override
	public boolean when( CompletionFacts facts ) {
		CompletionContext context = facts.getContext();
		return context.getKind() == CompletionContextKind.GENERAL ||
		    context.getKind() == CompletionContextKind.FUNCTION_ARGUMENT ||
		    context.getKind() == CompletionContextKind.TEMPLATE_EXPRESSION;
	}

	@Override
	public void then( CompletionFacts facts, List<CompletionItem> items ) {
		CompletionContext				context		= facts.getContext();
		FileParseResult					parseResult	= facts.fileParseResult();
		String							triggerText	= context.getTriggerText().toLowerCase();

		// Use VariableScopeCollectorVisitor to find all visible variables
		VariableScopeCollectorVisitor	visitor		= new VariableScopeCollectorVisitor();
		parseResult.findAstRoot().ifPresent( root -> root.accept( visitor ) );

		// Find containing function to filter variables
		BoxFunctionDeclaration		containingFunction	= findContainingFunction( parseResult, context );

		// 1. Add visible variables
		Map<String, VariableInfo>	visibleVariables	= visitor.getAllVisibleVariables( containingFunction );
		for ( Map.Entry<String, VariableInfo> entry : visibleVariables.entrySet() ) {
			String name = entry.getKey();
			if ( name.toLowerCase().startsWith( triggerText ) ) {
				VariableInfo	info	= entry.getValue();
				CompletionItem	item	= createCompletionItem( info );
				items.add( item );
			}
		}

		// 2. Add scope keywords
		Map<String, VariableInfo> keywords = visitor.getScopeKeywords();
		for ( Map.Entry<String, VariableInfo> entry : keywords.entrySet() ) {
			String name = entry.getKey();
			if ( name.startsWith( triggerText ) ) {
				CompletionItem item = createCompletionItem( entry.getValue() );
				item.setKind( CompletionItemKind.Keyword );
				items.add( item );
			}
		}
	}

	private BoxFunctionDeclaration findContainingFunction( FileParseResult parseResult, CompletionContext context ) {
		return parseResult.findAstRoot().flatMap( root -> {
			int	line	= context.getCursorPosition().getLine() + 1;
			int	col		= context.getCursorPosition().getCharacter();
			return root.getDescendantsOfType( BoxFunctionDeclaration.class ).stream()
			    .filter( func -> BLASTTools.containsPosition( func, line, col ) )
			    .findFirst();
		} ).orElse( null );
	}

	private CompletionItem createCompletionItem( VariableInfo info ) {
		CompletionItem item = new CompletionItem( info.name() );
		item.setKind( getKindFromScope( info.scope() ) );
		item.setDetail( info.scope().getDisplayName() + ( info.typeHint() != null ? " : " + info.typeHint() : "" ) );

		StringBuilder docs = new StringBuilder();
		docs.append( "**Scope:** " ).append( info.scope().getDisplayName() ).append( "\n\n" );
		if ( info.typeHint() != null ) {
			docs.append( "**Type Hint:** `" ).append( info.typeHint() ).append( "`\n\n" );
		}
		if ( info.inferredType() != null ) {
			docs.append( "**Inferred Type:** `" ).append( info.inferredType() ).append( "`\n\n" );
		}
		if ( info.defaultValue() != null ) {
			docs.append( "**Default Value:** `" ).append( info.defaultValue() ).append( "`\n\n" );
		}

		MarkupContent markup = new MarkupContent();
		markup.setKind( MarkupKind.MARKDOWN );
		markup.setValue( docs.toString() );
		item.setDocumentation( markup );

		// Sort text to prefer locals over properties
		item.setSortText( getSortPrefix( info.scope() ) + "_" + info.name() );

		return item;
	}

	private CompletionItemKind getKindFromScope( VariableScopeCollectorVisitor.VariableScope scope ) {
		return switch ( scope ) {
			case LOCAL -> CompletionItemKind.Variable;
			case ARGUMENTS -> CompletionItemKind.Property;
			case PROPERTY, VARIABLES, THIS -> CompletionItemKind.Field;
			default -> CompletionItemKind.Variable;
		};
	}

	private String getSortPrefix( VariableScopeCollectorVisitor.VariableScope scope ) {
		return switch ( scope ) {
			case LOCAL -> "a";
			case ARGUMENTS -> "b";
			case PROPERTY, VARIABLES, THIS -> "c";
			default -> "z";
		};
	}
}
