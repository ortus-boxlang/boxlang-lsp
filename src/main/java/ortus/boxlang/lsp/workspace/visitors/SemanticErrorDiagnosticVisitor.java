/**
 * [BoxLang LSP]
 *
 * Copyright [2023] [Ortus Solutions, Corp]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ortus.boxlang.lsp.workspace.visitors;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.CreateFile;
import org.eclipse.lsp4j.CreateFileOptions;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ortus.boxlang.compiler.ast.BoxClass;
import ortus.boxlang.compiler.ast.BoxInterface;
import ortus.boxlang.compiler.ast.BoxNode;
import ortus.boxlang.compiler.ast.expression.BoxArrayLiteral;
import ortus.boxlang.compiler.ast.expression.BoxFQN;
import ortus.boxlang.compiler.ast.expression.BoxStringLiteral;
import ortus.boxlang.compiler.ast.statement.BoxAnnotation;
import ortus.boxlang.compiler.ast.statement.BoxFunctionDeclaration;
import ortus.boxlang.compiler.ast.statement.BoxProperty;
import ortus.boxlang.lsp.BoxLangWorkspaceService;
import ortus.boxlang.lsp.SourceCodeVisitor;
import ortus.boxlang.lsp.lint.DiagnosticRuleRegistry;
import ortus.boxlang.lsp.lint.LintConfigLoader;
import ortus.boxlang.lsp.lint.rules.DuplicateMethodRule;
import ortus.boxlang.lsp.lint.rules.DuplicatePropertyRule;
import ortus.boxlang.lsp.lint.rules.InvalidExtendsRule;
import ortus.boxlang.lsp.lint.rules.InvalidImplementsRule;
import ortus.boxlang.lsp.workspace.ApplicationBxMappingExtractor;
import ortus.boxlang.lsp.workspace.BLASTTools;
import ortus.boxlang.lsp.workspace.FileParseResult;
import ortus.boxlang.lsp.workspace.ProjectContextProvider;
import ortus.boxlang.lsp.workspace.index.IndexedClass;
import ortus.boxlang.lsp.workspace.index.ProjectIndex;

/**
 * Visitor for detecting semantic errors in BoxLang code.
 *
 * Detects:
 * - Invalid extends (class not found)
 * - Invalid implements (interface not found)
 * - Duplicate method definitions within a class
 * - Duplicate property definitions within a class
 *
 * Each diagnostic type has its own rule ID for individual configuration.
 */
public class SemanticErrorDiagnosticVisitor extends SourceCodeVisitor {

	private static final ObjectMapper					JSON_MAPPER						= new ObjectMapper();
	private static final int							MINIMUM_MAPPING_MATCH_SEGMENTS	= 2;
	private static final String							BOXLANG_CONFIG_FILE				= "boxlang.json";
	private static final String							BXLINT_CONFIG_FILE				= ".bxlint.json";
	private static final String							APPLICATION_BX_FILE				= "Application.bx";
	private static final String							APPLICATION_CFC_FILE			= "Application.cfc";
	private static final Comparator<MappingSuggestion>	MAPPING_SUGGESTION_COMPARATOR	= Comparator
	    .comparingInt( MappingSuggestion::matchedSegments ).reversed()
	    .thenComparing( Comparator.comparingInt( MappingSuggestion::mappingKeyDepth ).reversed() )
	    .thenComparing( MappingSuggestion::mappingPathString );

	private List<Diagnostic>							diagnostics						= new ArrayList<>();
	private Set<String>									seenMethods						= new HashSet<>();
	private Set<String>									seenProperties					= new HashSet<>();
	private String										currentClassName				= null;
	private Map<Diagnostic, List<MappingSuggestion>>	mappingSuggestionsByDiagnostic	= new WeakHashMap<>();

	@Override
	public List<Diagnostic> getDiagnostics() {
		// Filter and adjust severity based on individual rule settings
		return this.diagnostics.stream()
		    .filter( d -> {
			    String ruleId = d.getCode() != null ? d.getCode().getLeft() : null;
			    if ( ruleId == null ) {
				    return true;
			    }
			    return DiagnosticRuleRegistry.getInstance().isEnabled( ruleId, true );
		    } )
		    .peek( d -> {
			    String ruleId = d.getCode() != null ? d.getCode().getLeft() : null;
			    if ( ruleId != null ) {
				    var settings = LintConfigLoader.get().forRule( ruleId );
				    if ( settings != null ) {
					    d.setSeverity( settings.toSeverityOr( d.getSeverity() ) );
				    }
			    }
		    } )
		    .toList();
	}

	@Override
	public boolean canVisit( FileParseResult parseResult ) {
		// Visit all BoxLang files - .bx, .bxs, .cfc, .cfm
		return true;
	}

	@Override
	public List<CodeAction> getCodeActions() {
		if ( !DiagnosticRuleRegistry.getInstance().isEnabled( InvalidExtendsRule.ID, true ) ) {
			return List.of();
		}

		return getDiagnostics().stream()
		    .flatMap( diagnostic -> createMappingCodeActions( diagnostic ).stream() )
		    .toList();
	}

	@Override
	public void visit( BoxClass node ) {
		// Reset state for new class
		seenMethods.clear();
		seenProperties.clear();

		// Get class name from file path
		currentClassName = extractClassName();

		// Check extends
		List<BoxAnnotation>	annotations		= findAnnotations( node );
		String				extendsClass	= extractExtends( annotations );
		if ( extendsClass != null && !extendsClass.isEmpty() ) {
			validateExtendsReference( extendsClass, node );
		}

		// Check implements
		List<String> implementsInterfaces = extractImplements( annotations );
		for ( String interfaceName : implementsInterfaces ) {
			if ( interfaceName != null && !interfaceName.isEmpty() ) {
				validateImplementsReference( interfaceName, node );
			}
		}

		// Visit children to check methods and properties
		visitChildren( node );

		currentClassName = null;
	}

	@Override
	public void visit( BoxInterface node ) {
		// Reset state for new interface
		seenMethods.clear();

		currentClassName = extractClassName();

		// Check extends for interfaces
		List<BoxAnnotation>	annotations			= findAnnotations( node );
		String				extendsInterface	= extractExtends( annotations );
		if ( extendsInterface != null && !extendsInterface.isEmpty() ) {
			validateExtendsReference( extendsInterface, node );
		}

		// Visit children
		visitChildren( node );

		currentClassName = null;
	}

	@Override
	public void visit( BoxFunctionDeclaration node ) {
		if ( currentClassName == null ) {
			// Standalone function, not in a class - skip duplicate check
			return;
		}

		String methodName = node.getName().toLowerCase();

		if ( seenMethods.contains( methodName ) ) {
			Diagnostic diagnostic = new Diagnostic(
			    ProjectContextProvider.positionToRange( node.getPosition() ),
			    "Duplicate method definition: '" + node.getName() + "' is already defined in this class.",
			    DiagnosticSeverity.Error,
			    "boxlang",
			    DuplicateMethodRule.ID
			);
			diagnostics.add( diagnostic );
		} else {
			seenMethods.add( methodName );
		}
	}

	@Override
	public void visit( BoxProperty node ) {
		if ( currentClassName == null ) {
			return;
		}

		String propertyName = BLASTTools.getPropertyName( node );
		if ( propertyName == null ) {
			return;
		}

		propertyName = propertyName.toLowerCase();

		if ( seenProperties.contains( propertyName ) ) {
			Diagnostic diagnostic = new Diagnostic(
			    ProjectContextProvider.positionToRange( node.getPosition() ),
			    "Duplicate property definition: '" + propertyName + "' is already defined in this class.",
			    DiagnosticSeverity.Error,
			    "boxlang",
			    DuplicatePropertyRule.ID
			);
			diagnostics.add( diagnostic );
		} else {
			seenProperties.add( propertyName );
		}
	}

	// ============ Helper Methods ============

	private void visitChildren( BoxNode node ) {
		for ( BoxNode child : node.getChildren() ) {
			child.accept( this );
		}
	}

	private String extractClassName() {
		if ( this.filePath == null ) {
			return null;
		}

		String	path		= this.filePath;
		int		lastSlash	= Math.max( path.lastIndexOf( '/' ), path.lastIndexOf( '\\' ) );
		String	fileName	= lastSlash >= 0 ? path.substring( lastSlash + 1 ) : path;

		int		dotIndex	= fileName.lastIndexOf( '.' );
		return dotIndex > 0 ? fileName.substring( 0, dotIndex ) : fileName;
	}

	/**
	 * Create a range that covers from the "class" or "interface" keyword to the opening brace "{".
	 * This provides a more precise diagnostic location for class declaration issues.
	 *
	 * @param node The BoxClass or BoxInterface node
	 *
	 * @return Range from class/interface keyword to opening brace
	 */
	private Range getClassDeclarationRange( BoxNode node ) {
		// Get the full source text of the class/interface
		String sourceText = node.getSourceText();
		if ( sourceText == null || sourceText.isEmpty() ) {
			// Fallback to full node range if no source text
			return ProjectContextProvider.positionToRange( node.getPosition() );
		}

		int declarationEndOffset = findDeclarationEndOffset( sourceText );
		if ( declarationEndOffset < 0 ) {
			// No opening brace or template tag end found, use full range
			return ProjectContextProvider.positionToRange( node.getPosition() );
		}

		// Calculate the end position at the declaration boundary.
		ortus.boxlang.compiler.ast.Position	nodePos		= node.getPosition();
		ortus.boxlang.compiler.ast.Point	startPoint	= nodePos.getStart();

		// Count lines and columns up to the brace
		int									line		= startPoint.getLine();
		int									column		= startPoint.getColumn();

		for ( int i = 0; i < declarationEndOffset; i++ ) {
			char c = sourceText.charAt( i );
			if ( c == '\n' ) {
				line++;
				column = 0;
			} else {
				column++;
			}
		}

		// Create range from start to opening brace
		return new Range(
		    new Position( startPoint.getLine() - 1, startPoint.getColumn() ),
		    new Position( line - 1, column )
		);
	}

	private int findDeclarationEndOffset( String sourceText ) {
		int braceIndex = sourceText.indexOf( '{' );
		if ( braceIndex >= 0 ) {
			return braceIndex;
		}

		if ( !sourceText.startsWith( "<" ) ) {
			return -1;
		}

		boolean	inSingleQuote	= false;
		boolean	inDoubleQuote	= false;

		for ( int i = 1; i < sourceText.length(); i++ ) {
			char c = sourceText.charAt( i );

			if ( c == '\'' && !inDoubleQuote ) {
				inSingleQuote = !inSingleQuote;
				continue;
			}

			if ( c == '"' && !inSingleQuote ) {
				inDoubleQuote = !inDoubleQuote;
				continue;
			}

			if ( c == '>' && !inSingleQuote && !inDoubleQuote ) {
				return i + 1;
			}
		}

		return -1;
	}

	private void validateExtendsReference( String className, BoxNode node ) {
		ProjectIndex index = ProjectContextProvider.getInstance().getIndex();
		if ( index == null ) {
			return;
		}

		var foundClass = index.findClassWithContext( className, resolveFileUri() );

		if ( foundClass.isEmpty() ) {
			List<MappingSuggestion>	suggestions		= findMappingSuggestions( className, index );
			boolean					hasSuggestions	= !suggestions.isEmpty();
			Diagnostic				diagnostic		= new Diagnostic(
			    getClassDeclarationRange( node ),
			    hasSuggestions
			        ? buildPossibleMatchMessage( className, suggestions )
			        : "Class or interface '" + className + "' not found (extends reference).",
			    hasSuggestions ? DiagnosticSeverity.Warning : DiagnosticSeverity.Error,
			    "boxlang",
			    InvalidExtendsRule.ID
			);
			diagnostic.setData( Map.of(
			    "className", className,
			    "id", UUID.randomUUID().toString(),
			    "possibleMatchCount", suggestions.size()
			) );
			if ( hasSuggestions ) {
				mappingSuggestionsByDiagnostic.put( diagnostic, suggestions );
			}
			diagnostics.add( diagnostic );
		}
	}

	private List<CodeAction> createMappingCodeActions( Diagnostic diagnostic ) {
		List<MappingSuggestion> suggestions = mappingSuggestionsByDiagnostic.get( diagnostic );
		if ( suggestions == null || suggestions.isEmpty() ) {
			return List.of();
		}

		Path	workspaceRoot	= resolveWorkspaceRoot();
		Path	sourcePath		= resolveSourcePath();
		if ( workspaceRoot == null || sourcePath == null ) {
			return List.of();
		}

		List<Path>			applicationTargets	= findApplicationConfigTargets( sourcePath, workspaceRoot );
		List<CodeAction>	actions				= new ArrayList<>();

		for ( MappingSuggestion suggestion : suggestions ) {
			for ( Path applicationTarget : applicationTargets ) {
				createApplicationMappingCodeAction( diagnostic, suggestion, workspaceRoot, applicationTarget ).ifPresent( actions::add );
			}

			createJsonMappingCodeAction( diagnostic, suggestion, workspaceRoot.resolve( BOXLANG_CONFIG_FILE ), workspaceRoot, BOXLANG_CONFIG_FILE, true )
			    .ifPresent( actions::add );
			createJsonMappingCodeAction( diagnostic, suggestion, workspaceRoot.resolve( BXLINT_CONFIG_FILE ), workspaceRoot, BXLINT_CONFIG_FILE, false )
			    .ifPresent( actions::add );
		}

		return actions;
	}

	private java.util.Optional<CodeAction> createApplicationMappingCodeAction( Diagnostic diagnostic, MappingSuggestion suggestion, Path workspaceRoot,
	    Path applicationFile ) {
		TextEdit edit = createApplicationMappingEdit( applicationFile, suggestion );
		if ( edit == null ) {
			return java.util.Optional.empty();
		}

		CodeAction action = new CodeAction(
		    "Add mapping to " + applicationFile.getFileName() + ": " + suggestion.mappingKey() + " -> " + suggestion.mappingPathString()
		);
		action.setKind( CodeActionKind.QuickFix );
		action.setDiagnostics( List.of( diagnostic ) );
		action.setEdit( createSingleFileWorkspaceEdit( applicationFile, edit ) );
		action.setCommand( createShowDocumentCommand( applicationFile ) );
		return java.util.Optional.of( action );
	}

	private java.util.Optional<CodeAction> createJsonMappingCodeAction( Diagnostic diagnostic, MappingSuggestion suggestion, Path configFile,
	    Path workspaceRoot,
	    String label, boolean allowLineComments ) {
		WorkspaceEdit edit = createJsonMappingWorkspaceEdit( configFile, workspaceRoot, suggestion, allowLineComments );
		if ( edit == null ) {
			return java.util.Optional.empty();
		}

		CodeAction action = new CodeAction( "Add mapping to " + label + ": " + suggestion.mappingKey() + " -> " + suggestion.mappingPathString() );
		action.setKind( CodeActionKind.QuickFix );
		action.setDiagnostics( List.of( diagnostic ) );
		action.setEdit( edit );
		action.setCommand( createShowDocumentCommand( configFile ) );
		return java.util.Optional.of( action );
	}

	private Command createShowDocumentCommand( Path targetFile ) {
		return new Command( "Open " + targetFile.getFileName(), BoxLangWorkspaceService.SHOW_DOCUMENT_COMMAND, List.of( targetFile.toUri().toString() ) );
	}

	private WorkspaceEdit createSingleFileWorkspaceEdit( Path targetFile, TextEdit edit ) {
		Map<String, List<TextEdit>> changes = new HashMap<>();
		changes.put( targetFile.toUri().toString(), List.of( edit ) );
		return new WorkspaceEdit( changes );
	}

	private WorkspaceEdit createJsonMappingWorkspaceEdit( Path configFile, Path workspaceRoot, MappingSuggestion suggestion, boolean allowLineComments ) {
		String mappingValue = formatPathForConfig( workspaceRoot, suggestion.mappingPath() );

		if ( !Files.exists( configFile ) ) {
			try {
				JsonObject	root		= new JsonObject();
				JsonObject	mappings	= new JsonObject();
				mappings.addProperty( suggestion.mappingKey(), mappingValue );
				root.add( "mappings", mappings );
				return createJsonCreateWorkspaceEdit( configFile, formatJson( root, "\n" ) );
			} catch ( IOException e ) {
				return null;
			}
		}

		try {
			String		rawText	= Files.readString( configFile );
			JsonObject	root	= parseJsonObject( rawText, allowLineComments );
			if ( root == null ) {
				return null;
			}

			JsonObject mappings = root.has( "mappings" ) && root.get( "mappings" ).isJsonObject()
			    ? root.getAsJsonObject( "mappings" )
			    : new JsonObject();
			if ( mappings.has( suggestion.mappingKey() ) && mappingValue.equals( mappings.get( suggestion.mappingKey() ).getAsString() ) ) {
				return null;
			}

			mappings.addProperty( suggestion.mappingKey(), mappingValue );
			root.add( "mappings", mappings );

			String formatted = formatJson( root, detectLineSeparator( rawText ) );
			return createSingleFileWorkspaceEdit( configFile, new TextEdit( fullDocumentRange( rawText ), formatted ) );
		} catch ( IOException e ) {
			return null;
		}
	}

	private WorkspaceEdit createJsonCreateWorkspaceEdit( Path targetFile, String contents ) {
		CreateFileOptions createFileOptions = new CreateFileOptions();
		createFileOptions.setOverwrite( false );
		createFileOptions.setIgnoreIfExists( false );

		CreateFile											createFile			= new CreateFile( targetFile.toUri().toString(), createFileOptions );
		Either<TextEdit, org.eclipse.lsp4j.SnippetTextEdit>	initialContents		= Either.forLeft(
		    new TextEdit( new Range( new Position( 0, 0 ), new Position( 0, 0 ) ), contents ) );
		TextDocumentEdit									textDocumentEdit	= new TextDocumentEdit(
		    new VersionedTextDocumentIdentifier( targetFile.toUri().toString(), null ), List.of( initialContents ) );
		WorkspaceEdit										edit				= new WorkspaceEdit();
		edit.setDocumentChanges( List.of( Either.forRight( createFile ), Either.forLeft( textDocumentEdit ) ) );
		return edit;
	}

	private TextEdit createApplicationMappingEdit( Path applicationFile, MappingSuggestion suggestion ) {
		try {
			Map<String, String>	existingMappings	= ApplicationBxMappingExtractor.extract( applicationFile );
			String				existingValue		= existingMappings.get( suggestion.mappingKey() );
			String				mappingValue		= formatPathForConfig( applicationFile.getParent(), suggestion.mappingPath() );
			if ( existingValue != null && existingValue.equals( mappingValue ) ) {
				return null;
			}

			String	sourceText			= Files.readString( applicationFile );
			int		closingBraceOffset	= sourceText.lastIndexOf( '}' );
			if ( closingBraceOffset < 0 ) {
				closingBraceOffset = sourceText.length();
			}

			String			newline	= detectLineSeparator( sourceText );
			String			indent	= detectBlockIndent( sourceText );
			StringBuilder	builder	= new StringBuilder();
			if ( closingBraceOffset > 0 && sourceText.charAt( closingBraceOffset - 1 ) != '\n' && sourceText.charAt( closingBraceOffset - 1 ) != '\r' ) {
				builder.append( newline );
			}
			builder.append( indent )
			    .append( "this.mappings[ \"" )
			    .append( escapeBoxLangString( suggestion.mappingKey() ) )
			    .append( "\" ] = \"" )
			    .append( escapeBoxLangString( mappingValue ) )
			    .append( "\";" )
			    .append( newline );

			Position insertPosition = positionAt( sourceText, closingBraceOffset );
			return new TextEdit( new Range( insertPosition, insertPosition ), builder.toString() );
		} catch ( IOException e ) {
			return null;
		}
	}

	private List<MappingSuggestion> findMappingSuggestions( String className, ProjectIndex index ) {
		Path workspaceRoot = resolveWorkspaceRoot();
		if ( workspaceRoot == null ) {
			return List.of();
		}

		List<String> unresolvedSegments = splitQualifiedName( className );
		if ( unresolvedSegments.size() < MINIMUM_MAPPING_MATCH_SEGMENTS ) {
			return List.of();
		}

		Map<String, MappingSuggestion> uniqueSuggestions = new HashMap<>();
		for ( IndexedClass indexedClass : index.getAllClasses() ) {
			MappingSuggestion suggestion = createSuggestion( unresolvedSegments, workspaceRoot, indexedClass );
			if ( suggestion == null ) {
				continue;
			}

			String				key			= suggestion.mappingKey().toLowerCase( Locale.ROOT ) + "\u0000"
			    + suggestion.mappingPath().toString().toLowerCase( Locale.ROOT );
			MappingSuggestion	existing	= uniqueSuggestions.get( key );
			if ( existing == null || MAPPING_SUGGESTION_COMPARATOR.compare( suggestion, existing ) < 0 ) {
				uniqueSuggestions.put( key, suggestion );
			}
		}

		return uniqueSuggestions.values().stream()
		    .sorted( MAPPING_SUGGESTION_COMPARATOR )
		    .toList();
	}

	private MappingSuggestion createSuggestion( List<String> unresolvedSegments, Path workspaceRoot, IndexedClass indexedClass ) {
		if ( indexedClass.fileUri() == null ) {
			return null;
		}

		try {
			Path candidateFile = Path.of( URI.create( indexedClass.fileUri() ) ).toAbsolutePath().normalize();
			if ( !candidateFile.startsWith( workspaceRoot ) || !Files.isRegularFile( candidateFile ) ) {
				return null;
			}

			List<String> relativeSegments = splitRelativePathSegments( workspaceRoot.relativize( candidateFile ) );
			if ( relativeSegments.size() < MINIMUM_MAPPING_MATCH_SEGMENTS ) {
				return null;
			}

			int matchLength = countCommonSuffixSegments( unresolvedSegments, relativeSegments );
			if ( matchLength < MINIMUM_MAPPING_MATCH_SEGMENTS ) {
				return null;
			}

			int		unresolvedMatchStart	= unresolvedSegments.size() - matchLength;
			int		fileMatchStart			= relativeSegments.size() - matchLength;
			String	mappingKey				= String.join( ".", unresolvedSegments.subList( 0, unresolvedMatchStart + 1 ) );
			Path	mappingPath				= buildPathFromSegments( workspaceRoot, relativeSegments.subList( 0, fileMatchStart + 1 ) );
			if ( mappingKey.isBlank() || mappingPath == null ) {
				return null;
			}

			return new MappingSuggestion(
			    mappingKey,
			    mappingPath,
			    candidateFile,
			    matchLength,
			    unresolvedMatchStart + 1
			);
		} catch ( Exception e ) {
			return null;
		}
	}

	private String buildPossibleMatchMessage( String className, List<MappingSuggestion> suggestions ) {
		if ( suggestions.size() == 1 ) {
			MappingSuggestion suggestion = suggestions.getFirst();
			return "Invalid extends reference '" + className + "' with possible match: " + suggestion.matchedFileDisplay() + ".";
		}

		return "Invalid extends reference '" + className + "' with " + suggestions.size()
		    + " possible matches. Quick fixes are ordered by longest suffix match.";
	}

	private Path resolveWorkspaceRoot() {
		List<org.eclipse.lsp4j.WorkspaceFolder> folders = ProjectContextProvider.getInstance().getWorkspaceFolders();
		if ( folders == null || folders.isEmpty() ) {
			return null;
		}

		try {
			return Path.of( new URI( folders.getFirst().getUri() ) ).toAbsolutePath().normalize();
		} catch ( Exception e ) {
			return null;
		}
	}

	private Path resolveSourcePath() {
		URI fileUri = resolveFileUri();
		if ( fileUri == null ) {
			return null;
		}

		try {
			return Path.of( fileUri ).toAbsolutePath().normalize();
		} catch ( Exception e ) {
			return null;
		}
	}

	private List<Path> findApplicationConfigTargets( Path sourcePath, Path workspaceRoot ) {
		List<Path>	targets	= new ArrayList<>();
		Path		dir		= sourcePath.getParent();

		while ( dir != null && dir.startsWith( workspaceRoot ) ) {
			Path	applicationBx	= dir.resolve( APPLICATION_BX_FILE );
			Path	applicationCfc	= dir.resolve( APPLICATION_CFC_FILE );
			if ( Files.isRegularFile( applicationBx ) ) {
				targets.add( applicationBx );
			}
			if ( Files.isRegularFile( applicationCfc ) ) {
				targets.add( applicationCfc );
			}
			if ( !targets.isEmpty() || dir.equals( workspaceRoot ) ) {
				break;
			}
			dir = dir.getParent();
		}

		return targets;
	}

	private List<String> splitQualifiedName( String qualifiedName ) {
		return List.of( qualifiedName.split( "\\." ) ).stream()
		    .map( String::trim )
		    .filter( segment -> !segment.isEmpty() )
		    .toList();
	}

	private List<String> splitRelativePathSegments( Path relativePath ) {
		List<String> segments = new ArrayList<>();
		for ( Path segment : relativePath ) {
			segments.add( segment.toString() );
		}

		if ( segments.isEmpty() ) {
			return List.of();
		}

		int		lastIndex	= segments.size() - 1;
		String	lastSegment	= segments.get( lastIndex );
		int		dotIndex	= lastSegment.lastIndexOf( '.' );
		if ( dotIndex > 0 ) {
			segments.set( lastIndex, lastSegment.substring( 0, dotIndex ) );
		}

		return segments;
	}

	private int countCommonSuffixSegments( List<String> left, List<String> right ) {
		int	count	= 0;
		int	li		= left.size() - 1;
		int	ri		= right.size() - 1;

		while ( li >= 0 && ri >= 0 && left.get( li ).equalsIgnoreCase( right.get( ri ) ) ) {
			count++;
			li--;
			ri--;
		}

		return count;
	}

	private Path buildPathFromSegments( Path workspaceRoot, List<String> segments ) {
		if ( segments.isEmpty() ) {
			return null;
		}

		Path result = workspaceRoot;
		for ( String segment : segments ) {
			result = result.resolve( segment );
		}
		return result.normalize();
	}

	private JsonObject parseJsonObject( String rawText, boolean allowLineComments ) {
		String normalized = allowLineComments ? stripLineComments( rawText ) : rawText;
		return JsonParser.parseString( normalized ).getAsJsonObject();
	}

	private String stripLineComments( String json ) {
		StringBuilder	sb			= new StringBuilder();
		boolean			inString	= false;
		int				i			= 0;

		while ( i < json.length() ) {
			char c = json.charAt( i );
			if ( c == '\\' && inString ) {
				sb.append( c );
				i++;
				if ( i < json.length() ) {
					sb.append( json.charAt( i ) );
					i++;
				}
				continue;
			}

			if ( c == '"' ) {
				inString = !inString;
				sb.append( c );
				i++;
				continue;
			}

			if ( !inString && c == '/' && i + 1 < json.length() && json.charAt( i + 1 ) == '/' ) {
				while ( i < json.length() && json.charAt( i ) != '\n' ) {
					i++;
				}
				continue;
			}

			sb.append( c );
			i++;
		}

		return sb.toString();
	}

	private String formatJson( JsonObject root, String newline ) throws IOException {
		DefaultPrettyPrinter	printer		= new DefaultPrettyPrinter();
		DefaultIndenter			indenter	= new DefaultIndenter( "    ", newline );
		printer.indentArraysWith( indenter );
		printer.indentObjectsWith( indenter );
		return JSON_MAPPER.writer( printer ).writeValueAsString( JSON_MAPPER.readTree( root.toString() ) );
	}

	private Range fullDocumentRange( String sourceText ) {
		Position end = positionAt( sourceText, sourceText.length() );
		return new Range( new Position( 0, 0 ), end );
	}

	private Position positionAt( String sourceText, int offset ) {
		int	line		= 0;
		int	character	= 0;
		for ( int i = 0; i < offset && i < sourceText.length(); i++ ) {
			char c = sourceText.charAt( i );
			if ( c == '\n' ) {
				line++;
				character = 0;
			} else if ( c != '\r' ) {
				character++;
			}
		}
		return new Position( line, character );
	}

	private String detectLineSeparator( String sourceText ) {
		return sourceText.contains( "\r\n" ) ? "\r\n" : "\n";
	}

	private String detectBlockIndent( String sourceText ) {
		for ( String line : sourceText.split( "\\R" ) ) {
			if ( line.isBlank() ) {
				continue;
			}
			int index = 0;
			while ( index < line.length() && Character.isWhitespace( line.charAt( index ) ) ) {
				index++;
			}
			if ( index > 0 ) {
				return line.substring( 0, index );
			}
		}
		return "    ";
	}

	private String formatPathForConfig( Path baseDir, Path targetPath ) {
		Path	normalizedBase		= baseDir.toAbsolutePath().normalize();
		Path	normalizedTarget	= targetPath.toAbsolutePath().normalize();
		try {
			return normalizeSlashes( normalizedBase.relativize( normalizedTarget ) );
		} catch ( IllegalArgumentException e ) {
			return normalizeSlashes( normalizedTarget );
		}
	}

	private String normalizeSlashes( Path path ) {
		return path.toString().replace( '\\', '/' );
	}

	private String escapeBoxLangString( String value ) {
		return value.replace( "\\", "\\\\" ).replace( "\"", "\\\"" );
	}

	private record MappingSuggestion( String mappingKey, Path mappingPath, Path matchedFile, int matchedSegments, int mappingKeyDepth ) {

		private String matchedFileDisplay() {
			return matchedFile.toString().replace( '\\', '/' );
		}

		private String mappingPathString() {
			return mappingPath.toString().replace( '\\', '/' );
		}
	}

	private void validateImplementsReference( String interfaceName, BoxNode node ) {
		ProjectIndex index = ProjectContextProvider.getInstance().getIndex();
		if ( index == null ) {
			return;
		}

		var foundClass = index.findClassWithContext( interfaceName, resolveFileUri() );

		if ( foundClass.isEmpty() ) {
			Diagnostic diagnostic = new Diagnostic(
			    getClassDeclarationRange( node ),
			    "Interface '" + interfaceName + "' not found (implements reference).",
			    DiagnosticSeverity.Error,
			    "boxlang",
			    InvalidImplementsRule.ID
			);
			diagnostics.add( diagnostic );
		}
	}

	/**
	 * Convert {@code this.filePath} to a {@code URI} so it can be passed to
	 * {@link ProjectIndex#findClassWithContext} for context-aware resolution.
	 *
	 * @return the file URI, or {@code null} if the path cannot be converted
	 */
	private java.net.URI resolveFileUri() {
		if ( this.filePath == null ) {
			return null;
		}
		try {
			if ( this.filePath.startsWith( "file:" ) ) {
				return new java.net.URI( this.filePath );
			}
			return java.nio.file.Paths.get( this.filePath ).toUri();
		} catch ( Exception e ) {
			return null;
		}
	}

	/**
	 * Find annotations for a node by looking at its immediate children.
	 */
	private List<BoxAnnotation> findAnnotations( BoxNode node ) {
		List<BoxAnnotation> annotations = new ArrayList<>();
		for ( BoxNode child : node.getChildren() ) {
			if ( child instanceof BoxAnnotation annotation ) {
				annotations.add( annotation );
			}
		}
		return annotations;
	}

	private String extractExtends( List<BoxAnnotation> annotations ) {
		return annotations.stream()
		    .filter( a -> a.getKey().getValue().equalsIgnoreCase( "extends" ) )
		    .findFirst()
		    .map( this::extractAnnotationValue )
		    .orElse( null );
	}

	private List<String> extractImplements( List<BoxAnnotation> annotations ) {
		return annotations.stream()
		    .filter( a -> a.getKey().getValue().equalsIgnoreCase( "implements" ) )
		    .findFirst()
		    .map( this::extractAnnotationValueAsList )
		    .orElse( new ArrayList<>() );
	}

	private String extractAnnotationValue( BoxAnnotation annotation ) {
		if ( annotation.getValue() == null ) {
			return null;
		}

		if ( annotation.getValue() instanceof BoxStringLiteral bsl ) {
			return bsl.getValue();
		}

		if ( annotation.getValue() instanceof BoxFQN fqn ) {
			return fqn.getValue();
		}

		return annotation.getValue().getSourceText();
	}

	private List<String> extractAnnotationValueAsList( BoxAnnotation annotation ) {
		List<String> values = new ArrayList<>();

		if ( annotation.getValue() == null ) {
			return values;
		}

		if ( annotation.getValue() instanceof BoxArrayLiteral arrayLiteral ) {
			for ( BoxNode element : arrayLiteral.getValues() ) {
				if ( element instanceof BoxStringLiteral bsl ) {
					values.add( bsl.getValue() );
				} else if ( element instanceof BoxFQN fqn ) {
					values.add( fqn.getValue() );
				} else {
					values.add( element.getSourceText() );
				}
			}
		} else if ( annotation.getValue() instanceof BoxStringLiteral bsl ) {
			// Single value or comma-separated list in string
			String value = bsl.getValue();
			if ( value.contains( "," ) ) {
				for ( String part : value.split( "," ) ) {
					values.add( part.trim() );
				}
			} else {
				values.add( value );
			}
		} else if ( annotation.getValue() instanceof BoxFQN fqn ) {
			values.add( fqn.getValue() );
		} else {
			String text = annotation.getValue().getSourceText();
			if ( text != null && text.contains( "," ) ) {
				for ( String part : text.split( "," ) ) {
					values.add( part.trim() );
				}
			} else if ( text != null ) {
				values.add( text );
			}
		}

		return values;
	}
}
