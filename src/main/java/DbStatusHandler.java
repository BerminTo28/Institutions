import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

public class DbStatusHandler implements HttpHandler {
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        ResponseUtil.addCorsHeaders(exchange);
        
        boolean localOk = testLocalConnection();
        boolean remoteOk = testRemoteConnection();
        
        try {
            String message;
            if (localOk && remoteOk) {
                message = "Bases de données locale et distante connectées";
            } else if (localOk) {
                message = "Base de données locale connectée (distante non disponible)";
            } else {
                message = "Base de données non disponible";
            }
            
            String json = String.format(
                "{\"local\":%b,\"remote\":%b,\"message\":\"%s\"}",
                localOk,
                remoteOk,
                message
            );
            ResponseUtil.sendJson(exchange, 200, json);
        } catch (IOException e) {
            String json = String.format("{\"local\":false,\"remote\":false,\"message\":\"Erreur: %s\"}", e.getMessage());
            ResponseUtil.sendJson(exchange, 500, json);
        }
    }
    
    private boolean testLocalConnection() {
        try (Connection conn = DatabaseManager.getInstance().getLocalConnection()) {
            return conn != null && !conn.isClosed() && conn.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }

    private boolean testRemoteConnection() {
        try (Connection conn = DatabaseManager.getInstance().getRemoteConnection()) {
            return conn != null && !conn.isClosed() && conn.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }
}