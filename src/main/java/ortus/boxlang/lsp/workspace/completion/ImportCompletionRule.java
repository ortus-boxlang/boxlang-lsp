package ortus.boxlang.lsp.workspace.completion;

import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.InsertTextFormat;

import ortus.boxlang.lsp.workspace.FileParseResult;
import ortus.boxlang.lsp.workspace.rules.IRule;

public class ImportCompletionRule implements IRule<CompletionFacts, List<CompletionItem>> {

	static final Pattern importPattern = Pattern.compile( "^\\s*import\\s+(\\w[\\w\\d\\$\\-_\\.]*)*$", Pattern.CASE_INSENSITIVE );

	@Override
	public boolean when( CompletionFacts facts ) {
		return !facts.fileParseResult().isTemplate()
		    && ContextChecker.isImportExpression( facts );
	}

	@Override
	public void then( CompletionFacts facts, List<CompletionItem> result ) {
		FileParseResult	fileParseResult	= facts.fileParseResult();
		var				existingPrompt	= fileParseResult.readLine( facts.completionParams().getPosition().getLine() );

		existingPrompt = existingPrompt.substring( 0, facts.completionParams().getPosition().getCharacter() );

		String					afterImportPrompt	= getAfterImportText( existingPrompt );
		List<CompletionItem>	options				= new ArrayList<CompletionItem>();

		// Get available JDK packages based on the current prompt
		options.addAll( getJdkPackageCompletions( afterImportPrompt ) );

		result.addAll( options );
	}

	private String getAfterImportText( String prompt ) {
		Matcher match = importPattern.matcher( prompt );
		if ( match.find() ) {
			String afterImport = match.group( 1 );
			if ( afterImport == null ) {
				return "";
			}
			return afterImport;
		}
		return "";
	}

	private List<CompletionItem> getJdkPackageCompletions( String prefix ) {
		List<CompletionItem>	completions	= new ArrayList<>();
		Set<String>				packages	= new HashSet<>();

		try {
			// Get all system modules
			ModuleFinder.ofSystem().findAll().forEach( moduleRef -> {
				String moduleName = moduleRef.descriptor().name();
				// Focus on common JDK modules
				if ( moduleName.startsWith( "java." ) || moduleName.startsWith( "javax." ) ) {
					packages.addAll( getPackagesFromModule( moduleRef, prefix ) );
				}
			} );

			// Convert packages to completion items
			for ( String packageName : packages ) {
				if ( prefix.isEmpty() || packageName.startsWith( prefix ) ) {
					CompletionItem item = new CompletionItem();
					item.setLabel( packageName );
					item.setKind( CompletionItemKind.Module );
					item.setInsertTextFormat( InsertTextFormat.PlainText );
					item.setInsertText( packageName );
					item.setDetail( "Java package" );
					completions.add( item );
				}
			}

		} catch ( Exception e ) {
			// If module system approach fails, fall back to common JDK packages
			completions.addAll( getCommonJdkPackages( prefix ) );
		}

		return completions;
	}

	private Set<String> getPackagesFromModule( ModuleReference moduleRef, String prefix ) {
		Set<String> packages = new HashSet<>();

		try {
			URI moduleUri = moduleRef.location().orElse( null );
			if ( moduleUri != null && "jrt".equals( moduleUri.getScheme() ) ) {
				try ( FileSystem fs = FileSystems.getFileSystem( URI.create( "jrt:/" ) ) ) {
					Path modulePath = fs.getPath( "/modules/" + moduleRef.descriptor().name() );
					if ( Files.exists( modulePath ) ) {
						try ( Stream<Path> paths = Files.walk( modulePath ) ) {
							paths.filter( Files::isDirectory )
							    .map( path -> modulePath.relativize( path ).toString() )
							    .filter( pathStr -> !pathStr.isEmpty() )
							    .map( pathStr -> pathStr.replace( '/', '.' ) )
							    .filter( packageName -> prefix.isEmpty() || packageName.startsWith( prefix ) )
							    .forEach( packages::add );
						}
					}
				}
			}
		} catch ( Exception e ) {
			// Ignore errors and continue
		}

		return packages;
	}

	private List<CompletionItem> getCommonJdkPackages( String prefix ) {
		List<CompletionItem>	completions		= new ArrayList<>();
		String[]				commonPackages	= {
		    "java.lang",
		    "java.util",
		    "java.util.concurrent",
		    "java.util.function",
		    "java.util.stream",
		    "java.io",
		    "java.nio",
		    "java.nio.file",
		    "java.net",
		    "java.text",
		    "java.time",
		    "java.time.format",
		    "java.math",
		    "java.security",
		    "java.sql",
		    "javax.sql",
		    "java.awt",
		    "java.awt.event",
		    "javax.swing",
		    "javax.swing.event"
		};

		for ( String packageName : commonPackages ) {
			if ( prefix.isEmpty() || packageName.startsWith( prefix ) ) {
				CompletionItem item = new CompletionItem();
				item.setLabel( packageName );
				item.setKind( CompletionItemKind.Module );
				item.setInsertTextFormat( InsertTextFormat.PlainText );
				item.setInsertText( packageName );
				item.setDetail( "Java package" );
				completions.add( item );
			}
		}

		return completions;
	}
}