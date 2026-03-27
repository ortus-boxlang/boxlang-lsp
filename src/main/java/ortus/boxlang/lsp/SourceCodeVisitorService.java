package ortus.boxlang.lsp;

import java.lang.reflect.Constructor;
import java.net.URI;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import ortus.boxlang.compiler.ast.BoxNode;
import ortus.boxlang.lsp.workspace.MappingConfig;
import ortus.boxlang.lsp.workspace.MappingResolver;
import ortus.boxlang.lsp.workspace.ProjectContextProvider;
import ortus.boxlang.lsp.workspace.visitors.SemanticErrorDiagnosticVisitor;
import ortus.boxlang.lsp.workspace.visitors.SemanticWarningDiagnosticVisitor;
import ortus.boxlang.lsp.workspace.visitors.UnscopedVariableDiagnosticVisitor;
import ortus.boxlang.lsp.workspace.visitors.UnusedVariableDiagnosticVisitor;

public class SourceCodeVisitorService {

	private static SourceCodeVisitorService				instance	= null;
	private List<Class<? extends SourceCodeVisitor>>	visitors	= new ArrayList<Class<? extends SourceCodeVisitor>>();

	static {
		instance = new SourceCodeVisitorService();

		instance.addVisitor( UnscopedVariableDiagnosticVisitor.class );
		instance.addVisitor( UnusedVariableDiagnosticVisitor.class );
		instance.addVisitor( SemanticErrorDiagnosticVisitor.class );
		instance.addVisitor( SemanticWarningDiagnosticVisitor.class );
	}

	private SourceCodeVisitorService() {

	}

	public static SourceCodeVisitorService getInstance() {
		return instance;
	}

	public void addVisitor( Class<? extends SourceCodeVisitor> visitorClass ) {
		this.visitors.add( visitorClass );
	}

	public void clearResults( String path ) {
		// no-op: caching was removed to prevent thread-safety issues
	}

	public List<SourceCodeVisitor> forceVisit( String path, BoxNode root ) {
		return this.visitAll( path, root );
	}

	public List<SourceCodeVisitor> visitAll( String path, BoxNode root ) {
		// Resolve per-file MappingConfig so visitors (e.g. SemanticErrorDiagnosticVisitor)
		// have access to Application.bx-derived mappings for the file being analysed.
		MappingConfig perFileConfig = resolvePerFileConfig( path );

		// Always create fresh visitor instances — no caching to avoid concurrent-parse races
		return this.visitors.stream()
		    .map( vc -> {
			    try {
				    Constructor		c		= vc.getConstructor();

				    SourceCodeVisitor visitor = ( SourceCodeVisitor ) c.newInstance();

				    visitor.setFilePath( path );
				    visitor.setMappingConfig( perFileConfig );

				    root.accept( visitor );

				    return visitor;
			    } catch ( Exception e ) {
				    App.logger.error( "Error running SourceCodeVisitor", e );
				    return null;
			    }
		    } )
		    .filter( visitor -> visitor != null )
		    .collect( Collectors.toList() );
	}

	private MappingConfig resolvePerFileConfig( String path ) {
		try {
			var folders = ProjectContextProvider.getInstance().getWorkspaceFolders();
			if ( folders == null || folders.isEmpty() ) {
				return null;
			}
			var	workspaceRoot	= Paths.get( new URI( folders.get( 0 ).getUri() ) );
			var	filePath		= Paths.get( new URI( path ) );
			return MappingResolver.resolveForFile( filePath, workspaceRoot );
		} catch ( Exception e ) {
			return null;
		}
	}
}
