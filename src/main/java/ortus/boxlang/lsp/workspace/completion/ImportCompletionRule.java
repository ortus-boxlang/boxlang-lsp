package ortus.boxlang.lsp.workspace.completion;

import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionItemLabelDetails;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

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
		int				line			= facts.completionParams().getPosition().getLine();
		int				character		= facts.completionParams().getPosition().getCharacter();

		existingPrompt = existingPrompt.substring( 0, character );

		String					afterImportPrompt	= getAfterImportText( existingPrompt );
		List<CompletionItem>	options				= new ArrayList<CompletionItem>();

		// Get available JDK packages and classes based on the current prompt
		options.addAll( getJdkCompletions( afterImportPrompt, line, existingPrompt ) );

		CompletionItem a = new CompletionItem();
		a.setLabel( "WHAT" );
		a.setSortText( "0" );
		// a.setInsertText( "WHAT" );
		options.add( a );
		TextEdit	te	= new TextEdit();
		Range		r	= new Range();
		r.setStart( new Position( line, character - 1 ) );
		r.setEnd( new Position( line, character ) );

		te.setRange( r );
		te.setNewText( "xxxxx" );
		a.setTextEdit( Either.forLeft( te ) );
		a.setInsertTextFormat( InsertTextFormat.PlainText );

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

	private List<CompletionItem> getJdkCompletions( String prefix, int line, String existingPrompt ) {
		List<CompletionItem>	completions			= new ArrayList<>();
		Set<String>				packages			= new HashSet<>();
		Set<String>				classes				= new HashSet<>();

		boolean					hasDot				= prefix.contains( "." );
		String					classSortPrefix		= hasDot ? "0" : "1"; // Packages before classes if prefix has dot
		String					packageSortPrefix	= hasDot ? "1" : "0"; // Packages before classes if prefix has dot

		try {
			// Get all system modules
			ModuleFinder.ofSystem().findAll().forEach( moduleRef -> {
				String moduleName = moduleRef.descriptor().name();
				// Focus on common JDK modules
				if ( moduleName.startsWith( "java." ) || moduleName.startsWith( "javax." ) ) {
					Set<String> modulePackages = getPackagesFromModule( moduleRef, prefix );
					packages.addAll( modulePackages );

					// Get classes for relevant packages
					classes.addAll( getClassesFromModule( moduleRef, prefix, modulePackages ) );
				}
			} );

			// Convert packages to completion items
			for ( String packageName : packages ) {
				if ( matchesPrefix( packageName, prefix ) ) {
					String			insertText	= !hasDot ? packageName : packageName.substring( packageName.lastIndexOf( '.' ) + 1 );
					CompletionItem	item		= createCompletionItem( packageName, "Java package", packageName, insertText, CompletionItemKind.Module,
					    existingPrompt, packageSortPrefix, line, existingPrompt.length() );
					completions.add( item );
				}
			}

			// Convert classes to completion items
			for ( String className : classes ) {
				if ( matchesPrefix( className, prefix ) ) {
					String simpleClassName = className.substring( className.lastIndexOf( '.' ) + 1 );

					if ( hasDot && className.replace( prefix, "" ).contains( "." ) ) {
						continue;
					}

					String			insertText	= hasDot ? simpleClassName : className;

					CompletionItem	item		= createCompletionItem( simpleClassName, className, className, insertText, CompletionItemKind.Class,
					    existingPrompt, classSortPrefix, line, existingPrompt.length() );

					completions.add( item );
				}
			}

		} catch ( Exception e ) {
			// If module system approach fails, fall back to common JDK packages and classes
			completions.addAll( getCommonJdkCompletions( prefix, line, existingPrompt ) );
		}

		return completions;
	}

	private boolean matchesPrefix( String candidate, String prefix ) {
		if ( prefix.isEmpty() ) {
			return true;
		}

		// Direct prefix match
		if ( candidate.toLowerCase().startsWith( prefix.toLowerCase() ) ) {
			return true;
		}

		// For searches without dots, check if the class name matches
		if ( !prefix.contains( "." ) && candidate.contains( "." ) ) {
			String simpleClassName = candidate.substring( candidate.lastIndexOf( '.' ) + 1 );
			return simpleClassName.toLowerCase().startsWith( prefix.toLowerCase() );
		}

		return false;
	}

	private CompletionItem createCompletionItem( String label, String detail, String filterText, String insertText, CompletionItemKind kind,
	    String existingPrompt, String sortTextPrefix, int line, int character ) {
		CompletionItem item = new CompletionItem();
		item.setLabel( label );
		item.setKind( kind );
		var details = new CompletionItemLabelDetails();
		details.setDescription( detail );
		item.setLabelDetails( details );
		item.setInsertText( insertText );
		item.setFilterText( filterText );
		item.setSortText( sortTextPrefix + label );

		return item;
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
							    .filter( packageName -> prefix.isEmpty() || packageName.toLowerCase().startsWith( prefix.toLowerCase() ) )
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

	private Set<String> getClassesFromModule( ModuleReference moduleRef, String prefix, Set<String> packages ) {
		Set<String> classes = new HashSet<>();

		try {
			URI moduleUri = moduleRef.location().orElse( null );
			if ( moduleUri != null && "jrt".equals( moduleUri.getScheme() ) ) {
				try ( FileSystem fs = FileSystems.getFileSystem( URI.create( "jrt:/" ) ) ) {
					Path modulePath = fs.getPath( "/modules/" + moduleRef.descriptor().name() );
					if ( Files.exists( modulePath ) ) {
						try ( Stream<Path> paths = Files.walk( modulePath ) ) {
							paths.filter( Files::isRegularFile )
							    .filter( path -> path.toString().endsWith( ".class" ) )
							    .map( path -> modulePath.relativize( path ).toString() )
							    .filter( pathStr -> !pathStr.contains( "$" ) ) // Skip inner classes
							    .filter( pathStr -> !pathStr.contains( "module-info" ) )
							    .map( pathStr -> pathStr.replace( '/', '.' ).replace( ".class", "" ) )
							    .filter( className -> {
								    // Only include classes from relevant packages or that match simple name search
								    if ( prefix.contains( "." ) ) {
									    return className.toLowerCase().startsWith( prefix.toLowerCase() );
								    } else if ( !prefix.isEmpty() ) {
									    String simpleClassName = className.substring( className.lastIndexOf( '.' ) + 1 );
									    return simpleClassName.toLowerCase().startsWith( prefix.toLowerCase() );
								    } else {
									    // For empty prefix, only include classes from common packages
									    return packages.stream().anyMatch( pkg -> className.startsWith( pkg + "." ) );
								    }
							    } )
							    .limit( 50 ) // Limit to avoid too many results
							    .forEach( classes::add );
						}
					}
				}
			}
		} catch ( Exception e ) {
			// Ignore errors and continue
		}

		return classes;
	}

	private List<CompletionItem> getCommonJdkCompletions( String prefix, int line, String existingPrompt ) {
		List<CompletionItem>	completions			= new ArrayList<>();
		boolean					hasDot				= prefix.contains( "." );
		String					classSortPrefix		= hasDot ? "0" : "1"; // Packages before classes if prefix has dot
		String					packageSortPrefix	= hasDot ? "1" : "0"; // Packages before classes if prefix has dot
		String[]				commonPackages		= {
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

		String[]				commonClasses		= {
		    "java.lang.String",
		    "java.lang.Object",
		    "java.lang.Integer",
		    "java.lang.Long",
		    "java.lang.Double",
		    "java.lang.Boolean",
		    "java.util.List",
		    "java.util.Map",
		    "java.util.Set",
		    "java.util.ArrayList",
		    "java.util.HashMap",
		    "java.util.HashSet",
		    "java.util.Date",
		    "java.util.UUID",
		    "java.net.URI",
		    "java.net.URL",
		    "java.nio.file.Path",
		    "java.nio.file.Paths",
		    "java.nio.file.Files",
		    "java.time.LocalDate",
		    "java.time.LocalDateTime",
		    "java.time.Instant",
		    "java.io.File",
		    "java.io.InputStream",
		    "java.io.OutputStream"
		};

		// Add packages
		for ( String packageName : commonPackages ) {
			if ( matchesPrefix( packageName, prefix ) ) {
				CompletionItem item = createCompletionItem( packageName, "Java package", packageName, packageName, CompletionItemKind.Module,
				    existingPrompt, packageSortPrefix, line, existingPrompt.length() );
				completions.add( item );
			}
		}

		// Add classes
		for ( String className : commonClasses ) {
			if ( matchesPrefix( className, prefix ) ) {
				String			simpleClassName	= className.substring( className.lastIndexOf( '.' ) + 1 );
				String			detail			= "Java class from " + className.substring( 0, className.lastIndexOf( '.' ) );

				CompletionItem	item			= createCompletionItem( className, detail, className, simpleClassName, CompletionItemKind.Class,
				    existingPrompt, classSortPrefix, line, existingPrompt.length() );
				item.setFilterText( "aaa" + simpleClassName );
				// For class name without package prefix search (e.g., "URI" -> "java.net.URI")
				// if ( !prefix.contains( "." ) && simpleClassName.toLowerCase().startsWith( prefix.toLowerCase() ) ) {
				// item.setFilterText( simpleClassName );
				// item.setSortText( "aa" + simpleClassName ); // Sort classes after packages
				// }

				completions.add( item );
			}
		}

		return completions;
	}
}