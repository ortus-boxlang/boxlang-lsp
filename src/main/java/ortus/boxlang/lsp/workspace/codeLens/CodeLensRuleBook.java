package ortus.boxlang.lsp.workspace.codeLens;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.CodeLens;

import ortus.boxlang.lsp.workspace.rules.RuleCollection;

public class CodeLensRuleBook extends RuleCollection<CodeLensFacts, List<CodeLens>> {

	private static CodeLensRuleBook instance = new CodeLensRuleBook();

	static {
		instance.addRule( new RunClassRule() );
	}

	public static List<CodeLens> execute( CodeLensFacts facts ) {
		return instance.execute( facts, new ArrayList<CodeLens>() );
	}
}
