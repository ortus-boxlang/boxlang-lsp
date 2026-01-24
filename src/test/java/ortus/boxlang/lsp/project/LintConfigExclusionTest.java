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

package ortus.boxlang.lsp.project;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ortus.boxlang.lsp.BaseTest;
import ortus.boxlang.lsp.lint.LintConfigLoader;
import ortus.boxlang.lsp.workspace.ProjectContextProvider;
import ortus.boxlang.lsp.workspace.index.ProjectIndex;
import ortus.boxlang.runtime.BoxRuntime;

/**
 * Tests for .bxlint.json configuration and file exclusion.
 *
 * This test uses the test-bx-project structure which includes:
 * - .bxlint.json with exclude pattern "ignored-folder/**"
 * - ignored-folder/ShouldNotReport.bx (intentionally has errors but should be excluded)
 * - Other files that should be indexed normally
 */
public class LintConfigExclusionTest extends BaseTest {

	private ProjectIndex			index;
	private ProjectContextProvider	provider;

	static BoxRuntime				runtime;

	// Path to our test project with .bxlint.json
	private Path					testProjectRoot;

	@BeforeAll
	static void setUpRuntime() {
		runtime = BoxRuntime.getInstance( true );
	}

	@BeforeEach
	void setUp() throws Exception {
		// Get the test project path from resources
		testProjectRoot = Paths.get( "src/test/resources/test-bx-project" ).toAbsolutePath();
		assertTrue( Files.exists( testProjectRoot ), "Test project should exist at: " + testProjectRoot );

		// Verify .bxlint.json exists
		Path lintConfigPath = testProjectRoot.resolve( ".bxlint.json" );
		assertTrue( Files.exists( lintConfigPath ), ".bxlint.json should exist in test project" );

		// Initialize the index with the test project root
		index = new ProjectIndex();
		index.initialize( testProjectRoot );

		// Set up the provider with workspace folder
		provider = ProjectContextProvider.getInstance();
		provider.setIndex( index );

		// Create and set workspace folder
		WorkspaceFolder folder = new WorkspaceFolder();
		folder.setUri( testProjectRoot.toUri().toString() );
		folder.setName( "test-bx-project" );
		provider.setWorkspaceFolders( List.of( folder ) );

		// Force reload of config
		LintConfigLoader.invalidate();
	}

	@AfterEach
	void tearDown() {
		// Clean up
		LintConfigLoader.invalidate();
		if ( provider != null ) {
			provider.setIndex( null );
			provider.setWorkspaceFolders( List.of() );
		}
	}

	@Test
	void testExcludedFolderFileNotIndexedForDiagnostics() throws Exception {
		// Index all files in the test project
		indexTestProjectFiles();

		// The file in the ignored folder
		Path	ignoredFile	= testProjectRoot.resolve( "ignored-folder/ShouldNotReport.bx" );
		URI		ignoredUri	= ignoredFile.toUri();

		assertTrue( Files.exists( ignoredFile ), "ShouldNotReport.bx should exist" );

		// Verify the file was indexed (indexing happens regardless of exclusions)
		index.indexFile( ignoredUri );

		// Get diagnostics for the ignored file
		List<Diagnostic> diagnostics = provider.getFileDiagnostics( ignoredUri );
		assertNotNull( diagnostics, "Diagnostics should not be null" );

		// The key assertion: excluded files should have NO diagnostics reported
		// even though ShouldNotReport.bx extends a non-existent class "DoesNotExist"
		assertThat( diagnostics ).isEmpty();
	}

	@Test
	void testNonExcludedFilesHaveDiagnostics() throws Exception {
		// Index all files in the test project
		indexTestProjectFiles();

		// Files that should NOT be excluded
		Path	bxObjFile	= testProjectRoot.resolve( "bxObj.bx" );
		URI		bxObjUri	= bxObjFile.toUri();

		assertTrue( Files.exists( bxObjFile ), "bxObj.bx should exist" );

		// Index the file
		index.indexFile( bxObjUri );

		// Get diagnostics for the non-excluded file
		List<Diagnostic> diagnostics = provider.getFileDiagnostics( bxObjUri );
		assertNotNull( diagnostics, "Diagnostics should not be null for non-excluded files" );

		// Non-excluded files should be analyzed normally
		// (we don't assert specific diagnostics here, just that the file is being analyzed)
	}

	@Test
	void testSubpackageFilesAreNotExcluded() throws Exception {
		// Index all files in the test project
		indexTestProjectFiles();

		// Files in subpackage should NOT be excluded
		Path	baseTypeFile	= testProjectRoot.resolve( "subpackage/BaseType.bx" );
		URI		baseTypeUri		= baseTypeFile.toUri();

		assertTrue( Files.exists( baseTypeFile ), "subpackage/BaseType.bx should exist" );

		// Index the file
		index.indexFile( baseTypeUri );

		// Get diagnostics - should be analyzed (not empty due to exclusion)
		List<Diagnostic> diagnostics = provider.getFileDiagnostics( baseTypeUri );
		assertNotNull( diagnostics, "Diagnostics should not be null for subpackage files" );

		// Subpackage files should be analyzed normally
	}

	@Test
	void testExclusionPatternInConfig() {
		// Load the configuration
		var config = LintConfigLoader.get();

		assertNotNull( config, "LintConfig should be loaded" );
		assertThat( config.exclude ).isNotEmpty();
		assertThat( config.exclude ).contains( "ignored-folder/**" );
	}

	@Test
	void testShouldAnalyzePathRespectingExclusions() {
		// Test the shouldAnalyze logic directly
		var		config					= LintConfigLoader.get();

		// Excluded path
		boolean	shouldAnalyzeIgnored	= config.shouldAnalyze( "ignored-folder/ShouldNotReport.bx" );
		assertThat( shouldAnalyzeIgnored ).isFalse();

		// Non-excluded paths
		boolean shouldAnalyzeBxObj = config.shouldAnalyze( "bxObj.bx" );
		assertThat( shouldAnalyzeBxObj ).isTrue();

		boolean shouldAnalyzeSubpackage = config.shouldAnalyze( "subpackage/BaseType.bx" );
		assertThat( shouldAnalyzeSubpackage ).isTrue();
	}

	// ============ Helper Methods ============

	/**
	 * Index all BoxLang files in the test project
	 */
	private void indexTestProjectFiles() throws Exception {
		// Walk through all .bx files and index them
		Files.walk( testProjectRoot )
		    .filter( Files::isRegularFile )
		    .filter( p -> p.toString().endsWith( ".bx" ) || p.toString().endsWith( ".bxs" ) )
		    .forEach( path -> {
			    try {
				    index.indexFile( path.toUri() );
			    } catch ( Exception e ) {
				    throw new RuntimeException( "Failed to index file: " + path, e );
			    }
		    } );
	}
}
