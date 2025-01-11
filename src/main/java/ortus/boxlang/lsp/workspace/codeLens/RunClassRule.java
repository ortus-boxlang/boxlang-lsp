package ortus.boxlang.lsp.workspace.codeLens;

import java.util.List;

import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import ortus.boxlang.lsp.workspace.rules.IRule;

public class RunClassRule implements IRule<CodeLensFacts, List<CodeLens>> {

	@Override
	public boolean when( CodeLensFacts facts ) {
		return facts.fileParseResult().isClass()
		    && facts.fileParseResult().getMainFunction().isPresent();
	}

	@Override
	public void then( CodeLensFacts facts, List<CodeLens> result ) {

		facts.fileParseResult()
		    .getMainFunction()
		    .ifPresent( node -> {
			    int line = node.getPosition().getStart().getLine() - 1;
			    var lens = new CodeLens();
			    lens.setRange( new Range( new Position( line, 0 ), new Position( line, 0 ) ) );
			    var command = new Command( "Run", "boxlang.runFile" );
			    command.setArguments( List.of( facts.codeLensParams().getTextDocument().getUri() ) );
			    lens.setCommand( command );

			    result.add( lens );
		    } );

	}

}
