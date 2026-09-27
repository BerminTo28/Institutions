import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;

public class AuthenticatedHandler implements HttpHandler {
    private final HttpHandler delegate;
    
    public AuthenticatedHandler(HttpHandler delegate) {
        this.delegate = delegate;
    }
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // Récupérer le cookie de session
        String sessionId = extractSessionId(exchange);
        HttpSession session = SessionManager.getSession(sessionId);
        
        if (session == null) {
            // Non authentifié - rediriger vers login
            exchange.getResponseHeaders().set("Location", "/login");
            exchange.sendResponseHeaders(302, -1);
            return;
        }
        
        // Ajouter la session dans l'attribut pour le handler
        exchange.setAttribute("session", session);
        delegate.handle(exchange);
    }
    
    private String extractSessionId(HttpExchange exchange) {
        String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookieHeader == null) return null;
        
        for (String cookie : cookieHeader.split(";")) {
            String[] parts = cookie.trim().split("=");
            if (parts.length == 2 && "SESSION_ID".equals(parts[0])) {
                return parts[1];
            }
        }
        return null;
    }
}