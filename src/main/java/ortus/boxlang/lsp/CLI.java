package ortus.boxlang.lsp;

import picocli.CommandLine.Option;

public class CLI {

	@Option( names = {
	    "--debug-server" }, description = "Run the language server as a server that accepts socket connections" )
	boolean	debugServer;

	@Option( names = {
	    "-dp",
	    "--debug-server-port" }, description = "Run the language server as a server that accepts socket connections", defaultValue = "0" )
	int		debugServerPort;
}
