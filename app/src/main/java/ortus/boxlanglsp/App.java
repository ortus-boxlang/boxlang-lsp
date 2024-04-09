/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package ortus.boxlanglsp;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutionException;

import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;

public class App {
    public String getGreeting() {
        return "Hello World!";
    }

    public static void main(String[] args) {
        try (ServerSocket socket = new ServerSocket(5173)) {
            while (true) {
                System.out.println("waiting for a connection");
                Socket connectionSocket = socket.accept();

                System.out.println("Got a connection");
                LanguageServer languageServer = new LanguageServer();

                Launcher<LanguageClient> launcher = LSPLauncher.createServerLauncher(
                        languageServer,
                        connectionSocket.getInputStream(),
                        connectionSocket.getOutputStream());

                if (languageServer instanceof LanguageClientAware lca) {
                    LanguageClient client = launcher.getRemoteProxy();
                    lca.connect(client);
                }

                try {
                    launcher.startListening().get();
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } catch (ExecutionException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } finally {
                    connectionSocket.close();
                    System.out.println("Closing debug connection");
                }

            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
