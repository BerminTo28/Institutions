import java.io.IOException;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * Handler pour le module "Double Saisie" dans les paramètres.
 * Redirige vers la page de validation des notes.
 */
public class HandlerParametresDoubleSaisie implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // Redirection vers la page de validation des notes
        exchange.getResponseHeaders().set("Location", "/admin/validation-notes");
        exchange.sendResponseHeaders(302, -1);
    }
}