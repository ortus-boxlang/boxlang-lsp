package ortus.boxlang.lsp;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static com.google.common.truth.Truth.assertThat;

import ortus.boxlang.lsp.workspace.ProjectContextProvider;
import ortus.boxlang.lsp.workspace.index.ProjectIndex;

public class BxlintRuleCompletionTest extends BaseTest {

	@TempDir
	Path							tempDir;

	private ProjectContextProvider	provider;
	private ProjectIndex			index;

	@BeforeEach
	void setUp() {
		provider	= ProjectContextProvider.getInstance();
		index		= new ProjectIndex();
		index.initialize( tempDir );
		provider.setIndex( index );
	}

	@Test
	void testCompletesRuleNamesInsideBxlintDisableComment() throws Exception {
		String	source		= """
		                      class {
		                          function demo() {
		                              // bxlint-disable unu
		                          }
		                      }
		                      """;
		Path	testFile	= tempDir.resolve( "BxlintRuleCompletion.bx" );
		Files.writeString( testFile, source );

		index.indexFile( testFile.toUri() );
		provider.trackDocumentOpen( testFile.toUri(), source );

		CompletionParams		params		= new CompletionParams();
		TextDocumentIdentifier	identifier	= new TextDocumentIdentifier( testFile.toUri().toString() );
		int						cursorCol	= source.lines().toList().get( 2 ).length();
		params.setTextDocument( identifier );
		params.setPosition( new Position( 2, cursorCol ) );

		List<CompletionItem> items = provider.getAvailableCompletions( testFile.toUri(), params );

		assertThat( items.stream().map( CompletionItem::getLabel ).toList() ).contains( "unusedVariable" );
		CompletionItem unusedVariable = items.stream().filter( item -> "unusedVariable".equals( item.getLabel() ) ).findFirst().orElseThrow();
		assertThat( unusedVariable.getDetail() ).contains( "Flags local variables that are declared but never used in the code." );
		assertThat( unusedVariable.getDocumentation() ).isNotNull();
		assertThat( unusedVariable.getDocumentation().isRight() ).isTrue();
		MarkupContent documentation = unusedVariable.getDocumentation().getRight();
		assertThat( documentation.getValue() ).contains( "Flags local variables that are declared but never used in the code." );
	}

	@Test
	void testCompletesRuleNamesInsideTemplateBxlintComment() throws Exception {
		String	source		= """
		                      <!-- bxlint-disable unu -->
		                      <bx:output>#value#</bx:output>
		                      """;
		Path	testFile	= tempDir.resolve( "BxlintRuleCompletionTemplate.bxm" );
		Files.writeString( testFile, source );

		index.indexFile( testFile.toUri() );
		provider.trackDocumentOpen( testFile.toUri(), source );

		CompletionParams		params		= new CompletionParams();
		TextDocumentIdentifier	identifier	= new TextDocumentIdentifier( testFile.toUri().toString() );
		int						cursorCol	= source.lines().toList().get( 0 ).indexOf( " -->" );
		params.setTextDocument( identifier );
		params.setPosition( new Position( 0, cursorCol ) );

		List<CompletionItem> items = provider.getAvailableCompletions( testFile.toUri(), params );

		assertThat( items.stream().map( CompletionItem::getLabel ).toList() ).contains( "unusedVariable" );
	}

	@Test
	void testExcludesRulesAlreadyPresentInCommaSeparatedBxlintComment() throws Exception {
		String	source		= """
		                      class {
		                          function demo() {
		                              // bxlint-disable unusedVariable, unu
		                          }
		                      }
		                      """;
		Path	testFile	= tempDir.resolve( "BxlintRuleCompletionExistingRules.bx" );
		Files.writeString( testFile, source );

		index.indexFile( testFile.toUri() );
		provider.trackDocumentOpen( testFile.toUri(), source );

		CompletionParams		params		= new CompletionParams();
		TextDocumentIdentifier	identifier	= new TextDocumentIdentifier( testFile.toUri().toString() );
		int						cursorCol	= source.lines().toList().get( 2 ).length();
		params.setTextDocument( identifier );
		params.setPosition( new Position( 2, cursorCol ) );

		List<CompletionItem> items = provider.getAvailableCompletions( testFile.toUri(), params );

		assertThat( items.stream().map( CompletionItem::getLabel ).toList() ).doesNotContain( "unusedVariable" );
	}

	@Test
	void testKeepsCurrentPartialRuleAvailableWhenEditingExistingCommaSeparatedBxlintComment() throws Exception {
		String	source		= """
		                      class {
		                          function demo() {
		                              // bxlint-disable unusedImport, unusedVa
		                          }
		                      }
		                      """;
		Path	testFile	= tempDir.resolve( "BxlintRuleCompletionCurrentToken.bx" );
		Files.writeString( testFile, source );

		index.indexFile( testFile.toUri() );
		provider.trackDocumentOpen( testFile.toUri(), source );

		CompletionParams		params		= new CompletionParams();
		TextDocumentIdentifier	identifier	= new TextDocumentIdentifier( testFile.toUri().toString() );
		int						cursorCol	= source.lines().toList().get( 2 ).length();
		params.setTextDocument( identifier );
		params.setPosition( new Position( 2, cursorCol ) );

		List<CompletionItem> items = provider.getAvailableCompletions( testFile.toUri(), params );

		assertThat( items.stream().map( CompletionItem::getLabel ).toList() ).contains( "unusedVariable" );
		assertThat( items.stream().map( CompletionItem::getLabel ).toList() ).doesNotContain( "unusedImport" );
	}

	@Test
	void testDisableNextLineTextInsideStringDoesNotCompleteRuleNames() throws Exception {
		String	source		= """
		                      class {
		                          function demo() {
		                              var example = "Example directive:
		                      // bxlint-disable-next-line unu
		                      ";
		                              writeOutput(example);
		                          }
		                      }
		                      """;
		Path	testFile	= tempDir.resolve( "StringDirectiveCompletion.bx" );
		Files.writeString( testFile, source );
		index.indexFile( testFile.toUri() );
		provider.trackDocumentOpen( testFile.toUri(), source );
		try {
			CompletionParams params = new CompletionParams();
			params.setTextDocument( new TextDocumentIdentifier( testFile.toUri().toString() ) );
			params.setPosition( new Position( 3, source.lines().toList().get( 3 ).length() ) );

			List<CompletionItem> items = provider.getAvailableCompletions( testFile.toUri(), params );

			// The cursor is inside a multiline string, not a bxlint comment.
			assertThat( items.stream().map( CompletionItem::getLabel ).toList() ).containsNoneOf( "unusedVariable", "unusedImport" );
		} finally {
			provider.trackDocumentClose( testFile.toUri() );
		}
	}

	@Test
	void testCompletesRuleNamesInsideBxlintDisableNextLine() throws Exception {
		String	source		= """
		                      class {
		                          function demo() {
		                              // bxlint-disable-next-line unu
		                          }
		                      }
		                      """;
		Path	testFile	= tempDir.resolve( "BxlintRuleCompletionNextLine.bx" );
		Files.writeString( testFile, source );

		index.indexFile( testFile.toUri() );
		provider.trackDocumentOpen( testFile.toUri(), source );

		CompletionParams		params		= new CompletionParams();
		TextDocumentIdentifier	identifier	= new TextDocumentIdentifier( testFile.toUri().toString() );
		int						cursorCol	= source.lines().toList().get( 2 ).length();
		params.setTextDocument( identifier );
		params.setPosition( new Position( 2, cursorCol ) );

		List<CompletionItem> items = provider.getAvailableCompletions( testFile.toUri(), params );

		assertThat( items.stream().map( CompletionItem::getLabel ).toList() ).contains( "unusedVariable" );
	}

	@Test
	void testDisableNextLineExcludesListedRulesAndSuggestsOthers() throws Exception {
		String	source		= """
		                      class {
		                          function demo() {
		                              // bxlint-disable-next-line unusedVariable, unu
		                          }
		                      }
		                      """;
		Path	testFile	= tempDir.resolve( "BxlintRuleCompletionNextLineExistingRules.bx" );
		Files.writeString( testFile, source );

		index.indexFile( testFile.toUri() );
		provider.trackDocumentOpen( testFile.toUri(), source );

		CompletionParams		params		= new CompletionParams();
		TextDocumentIdentifier	identifier	= new TextDocumentIdentifier( testFile.toUri().toString() );
		int						cursorCol	= source.lines().toList().get( 2 ).length();
		params.setTextDocument( identifier );
		params.setPosition( new Position( 2, cursorCol ) );

		List<CompletionItem> items = provider.getAvailableCompletions( testFile.toUri(), params );

		assertThat( items.stream().map( CompletionItem::getLabel ).toList() ).doesNotContain( "unusedVariable" );
		assertThat( items.stream().map( CompletionItem::getLabel ).toList() ).contains( "unusedImport" );
	}

	@Test
	void testCompletesRuleNamesInsideTemplateBxlintNextLineComment() throws Exception {
		String	source		= """
		                      <!-- bxlint-disable-next-line unu -->
		                      <bx:output>#value#</bx:output>
		                      """;
		Path	testFile	= tempDir.resolve( "BxlintRuleCompletionTemplateNextLine.bxm" );
		Files.writeString( testFile, source );

		index.indexFile( testFile.toUri() );
		provider.trackDocumentOpen( testFile.toUri(), source );

		CompletionParams		params		= new CompletionParams();
		TextDocumentIdentifier	identifier	= new TextDocumentIdentifier( testFile.toUri().toString() );
		int						cursorCol	= source.lines().toList().get( 0 ).indexOf( " -->" );
		params.setTextDocument( identifier );
		params.setPosition( new Position( 0, cursorCol ) );

		List<CompletionItem> items = provider.getAvailableCompletions( testFile.toUri(), params );

		assertThat( items.stream().map( CompletionItem::getLabel ).toList() ).contains( "unusedVariable" );
	}
}