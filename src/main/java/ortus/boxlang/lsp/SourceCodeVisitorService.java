package ortus.boxlang.lsp;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import ortus.boxlang.compiler.ast.BoxNode;
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
		// Always create fresh visitor instances — no caching to avoid concurrent-parse races
		return this.visitors.stream()
		    .map( vc -> {
			    try {
				    Constructor		c		= vc.getConstructor();

				    SourceCodeVisitor visitor = ( SourceCodeVisitor ) c.newInstance();

				    visitor.setFilePath( path );

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
}
