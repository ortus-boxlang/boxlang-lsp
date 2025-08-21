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

package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.junit.jupiter.api.Test;

import ortus.boxlang.lsp.workspace.ProjectContextProvider;

public class UnscopedVariablesTest {

	@Test
	void testReturnWarningForUnscopedVariable() {
		ProjectContextProvider	pcp			= ProjectContextProvider.getInstance();
		// Get the project root directory
		Path					projectRoot	= Paths.get( System.getProperty( "user.dir" ) );
		// Resolve the file path relative to the project root directory
		Path					p			= projectRoot.resolve( "src/test/resources/files/unscopedVariable.cfc" );
		File					f			= p.toFile();
		assertTrue( f.exists(), "Test file does not exist: " + p.toString() );
		List<Diagnostic> diagnostics = pcp.getFileDiagnostics( f.toURI() );
		assertNotNull( diagnostics, "Diagnostics should not be null." );
		assertFalse( diagnostics.isEmpty(), "Diagnostics should not be empty." );

		Diagnostic unscopedVariable = diagnostics.stream()
		    .filter( d -> d.getMessage().contains( "Variable [foo] is not scoped." ) )
		    .findFirst()
		    .orElse( null );

		assertThat( unscopedVariable ).isNotNull();
		assertThat( unscopedVariable.getSeverity() ).isEqualTo( DiagnosticSeverity.Warning );
	}

	@Test
	void testDoNotWarnForProperties() {
		ProjectContextProvider	pcp			= ProjectContextProvider.getInstance();
		// Get the project root directory
		Path					projectRoot	= Paths.get( System.getProperty( "user.dir" ) );
		// Resolve the file path relative to the project root directory
		Path					p			= projectRoot.resolve( "src/test/resources/files/unscopedVariable.cfc" );
		File					f			= p.toFile();
		assertTrue( f.exists(), "Test file does not exist: " + p.toString() );
		List<Diagnostic> diagnostics = pcp.getFileDiagnostics( f.toURI() );
		assertNotNull( diagnostics, "Diagnostics should not be null." );

		Diagnostic unscopedVariable = diagnostics.stream()
		    .filter( d -> d.getMessage().contains( "Variable [isAProperty] is not scoped." ) )
		    .findFirst()
		    .orElse( null );

		assertThat( unscopedVariable ).isNull();
	}

	@Test
	void testDoNotWarnMultipleTimesWithinFunction() {
		ProjectContextProvider	pcp			= ProjectContextProvider.getInstance();
		// Get the project root directory
		Path					projectRoot	= Paths.get( System.getProperty( "user.dir" ) );
		// Resolve the file path relative to the project root directory
		Path					p			= projectRoot.resolve( "src/test/resources/files/unscopedVariable.cfc" );
		File					f			= p.toFile();
		assertTrue( f.exists(), "Test file does not exist: " + p.toString() );
		List<Diagnostic> diagnostics = pcp.getFileDiagnostics( f.toURI() );
		assertNotNull( diagnostics, "Diagnostics should not be null." );

		long c = diagnostics.stream()
		    .filter( d -> d.getMessage().contains( "Variable [multiple] is not scoped." ) )
		    .count();

		assertThat( c ).isEqualTo( 1 );
	}

	@Test
	void testDoNotIncludeBoxLang() {
		ProjectContextProvider	pcp			= ProjectContextProvider.getInstance();
		// Get the project root directory
		Path					projectRoot	= Paths.get( System.getProperty( "user.dir" ) );
		// Resolve the file path relative to the project root directory
		Path					p			= projectRoot.resolve( "src/test/resources/files/unscopedVariable.bx" );
		File					f			= p.toFile();
		assertTrue( f.exists(), "Test file does not exist: " + p.toString() );
		List<Diagnostic> diagnostics = pcp.getFileDiagnostics( f.toURI() );
		assertNotNull( diagnostics, "Diagnostics should not be null." );

		long c = diagnostics.stream()
		    .filter( d -> d.getMessage().contains( "] is not scoped." ) )
		    .count();

		assertThat( c ).isEqualTo( 0 );
	}

	@Test
	void testDoNotWarnForVariablesInPsuedoConstructor() {
		ProjectContextProvider	pcp			= ProjectContextProvider.getInstance();
		// Get the project root directory
		Path					projectRoot	= Paths.get( System.getProperty( "user.dir" ) );
		// Resolve the file path relative to the project root directory
		Path					p			= projectRoot.resolve( "src/test/resources/files/unscopedVariable.cfc" );
		File					f			= p.toFile();
		assertTrue( f.exists(), "Test file does not exist: " + p.toString() );
		List<Diagnostic> diagnostics = pcp.getFileDiagnostics( f.toURI() );
		assertNotNull( diagnostics, "Diagnostics should not be null." );

		long c = diagnostics.stream()
		    .filter( d -> {
			    return d.getMessage().contains( "Variable [inVariables" )
			        && d.getMessage().contains( "] is not scoped." );
		    } )
		    .count();

		assertThat( c ).isEqualTo( 0 );
	}

	// TODO do not warn for assignments to variables that have already been scoped
}
