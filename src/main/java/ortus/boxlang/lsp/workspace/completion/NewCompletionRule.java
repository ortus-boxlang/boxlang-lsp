package ortus.boxlang.lsp.workspace.completion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.InsertTextFormat;

import ortus.boxlang.lsp.workspace.FileParseResult;
import ortus.boxlang.lsp.workspace.ProjectContextProvider;
import ortus.boxlang.lsp.workspace.rules.IRule;

public class NewCompletionRule implements IRule<CompletionFacts, List<CompletionItem>> {

	static final Pattern newPattern = Pattern.compile( "\\W*new\\W(\\w[\\w\\d\\$\\-_\\.]*)*$", Pattern.CASE_INSENSITIVE );

	@Override
	public boolean when( CompletionFacts facts ) {
		return !facts.fileParseResult().isTemplate()
		    && ContextChecker.isNewExpression( facts );
	}

	@Override
	public void then( CompletionFacts facts, List<CompletionItem> result ) {
		FileParseResult	fileParseResult	= facts.fileParseResult();
		var				existingPrompt	= fileParseResult.readLine( facts.completionParams().getPosition().getLine() );

		existingPrompt = existingPrompt.substring( 0, facts.completionParams().getPosition().getCharacter() );

		String					afterNewPrompt	= getAfterNewText( existingPrompt );
		List<CompletionItem>	options			= new ArrayList<CompletionItem>();

		// TODO needs to pull from imports including aliases
		// TODO needs to autocomplete with full path to newable or insert an import and autocomplete just the name
		// TODO when navigating a folder it should still show newables beneath the current directory
		// TODO set sort text to be more useful
		// -- sort "closer" newables before further ones
		// -- sort newables, then mappings, then folders
		// TODO module support?

		if ( afterNewPrompt.length() == 0 ) {
			options.addAll( getMappingCompletionItems() );
		}

		// if ( !afterNewPrompt.contains( "." ) ) {
		// options.addAll(
		// ProjectContextProvider.getInstance().newables.stream().map( ( newable ) -> {

		// CompletionItem item = new CompletionItem();
		// item.setLabel( newable.name() );
		// item.setKind( CompletionItemKind.Constructor );
		// item.setInsertTextFormat( InsertTextFormat.Snippet );
		// item.setInsertText( newable.name() + "()" );
		// item.setDetail( "class " + newable.name() );

		// return item;
		// } )
		// .collect( Collectors.toList() )
		// );
		// }

		options.addAll( getDirectoryCompletions( chooseDirToFindCompletions( Path.of( facts.fileParseResult().getURI() ), afterNewPrompt ) )
		    .stream()
		    .map( NewCompletionRule::completionItemFromPath ).collect( Collectors.toList() ) );

		result.addAll( options );
	}

	private Path chooseDirToFindCompletions( Path filePath, String afterNewPrompt ) {
		Map<String, Path>	mappings	= ProjectContextProvider.getInstance().getMappings();
		Path				fileDir		= filePath.getParent();
		Path				promptDir	= fileDir;

		if ( afterNewPrompt.endsWith( "." ) ) {
			promptDir = promptDir.resolve( afterNewPrompt.replace( ".", "/" ) );
		}

		String[] parts = afterNewPrompt.split( "\\." );

		if ( parts.length > 0 ) {
			String first = parts[ 0 ];

			if ( mappings.containsKey( first.toLowerCase() ) ) {
				promptDir	= mappings.get( first.toLowerCase() );
				promptDir	= promptDir.resolve( afterNewPrompt.replace( first + ".", "" ).replace( ".", "/" ) );
			}
		}

		return promptDir;
	}

	private Collection<? extends CompletionItem> getMappingCompletionItems() {
		return ProjectContextProvider.getInstance().getMappings().entrySet().stream().map( entrySet -> {
			CompletionItem item = new CompletionItem();
			item.setLabel( entrySet.getKey() );
			item.setKind( CompletionItemKind.Folder );
			item.setInsertTextFormat( InsertTextFormat.Snippet );
			item.setInsertText( entrySet.getKey() );
			item.setDetail( "mapping " + entrySet.getKey() );

			return item;
		} ).collect( Collectors.toList() );
	}

	private static CompletionItem completionItemFromPath( Path p ) {
		String name = p.getFileName().toString();

		if ( Files.isDirectory( p ) ) {
			CompletionItem item = new CompletionItem();
			item.setLabel( name );
			item.setKind( CompletionItemKind.Folder );
			item.setInsertTextFormat( InsertTextFormat.Snippet );
			item.setInsertText( name );
			item.setDetail( "folder" );
			return item;
		}

		name = name.replaceFirst( "\\.\\w+", "" );

		CompletionItem item = new CompletionItem();
		item.setLabel( name );
		item.setKind( CompletionItemKind.Constructor );
		item.setInsertTextFormat( InsertTextFormat.Snippet );
		item.setInsertText( name + "()" );
		item.setDetail( "class " + name );
		return item;
	}

	/**
	 * 
	 * b/
	 * other.bx
	 * comp.bx
	 * folder/
	 * app/
	 * thing.bx
	 * 
	 * 
	 * file: /a/b/comp.bx
	 * prompt: new
	 * expect: other.bx, folder/
	 * 
	 * file: /a/b/comp.bx
	 * prompt: new folder
	 * expect: folder
	 * 
	 * prompt: new folder.
	 * expect: app/
	 * 
	 * prompt: new folder.app.
	 * expect: thing.bx
	 *
	 *
	 * @param dir
	 * 
	 * @return
	 */
	private List<Path> getDirectoryCompletions( Path dir ) {
		try {
			if ( !Files.exists( dir ) ) {
				return new ArrayList<Path>();
			}

			return Files.list( dir )
			    .filter( ( path ) -> {
				    String lc = path.toString().toLowerCase();

				    return Files.isDirectory( path )
				        || ( lc.endsWith( ".bx" )
				            || lc.endsWith( ".cfc" ) );

			    } )
			    .collect( Collectors.toList() );
		} catch ( IOException e ) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	private String getAfterNewText( String prompt ) {
		Matcher match = newPattern.matcher( prompt );
		match.find();

		String afterNew = match.group( 1 );

		if ( afterNew == null ) {
			return "";
		}

		return afterNew;
	}

}
