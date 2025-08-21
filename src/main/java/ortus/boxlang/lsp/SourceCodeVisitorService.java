package ortus.boxlang.lsp;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import ortus.boxlang.compiler.ast.BoxNode;
import ortus.boxlang.lsp.workspace.visitors.UnscopedVariableDiagnosticVisitor;

public class SourceCodeVisitorService {

	private static SourceCodeVisitorService				instance	= null;
	private List<Class<? extends SourceCodeVisitor>>	visitors	= new ArrayList<Class<? extends SourceCodeVisitor>>();
	private Map<String, List<SourceCodeVisitor>>		results		= new HashMap<String, List<SourceCodeVisitor>>();

	static {
		instance = new SourceCodeVisitorService();

		instance.addVisitor( UnscopedVariableDiagnosticVisitor.class );
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
		this.results.remove( path );
	}

	public List<SourceCodeVisitor> forceVisit( String path, BoxNode root ) {
		this.results.remove( path );

		return this.visitAll( path, root );
	}

	public List<SourceCodeVisitor> visitAll( String path, BoxNode root ) {
		if ( !this.results.containsKey( path ) ) {

			var visitors = this.visitors.stream()
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

			this.results.put( path, visitors );

		}

		return this.results.get( path );
	}
}
