import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class EtudiantCoursLienHandler implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(EtudiantCoursLienHandler.class.getName());
    private final CoursLienManager manager = new CoursLienManager();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            if (!"GET".equals(method)) {
                ResponseUtil.sendError(exchange, 405, "Méthode non autorisée");
                return;
            }

            String institutionId = getInstitutionIdFromSession(exchange);
            if (institutionId == null) {
                redirect(exchange, "/connexion");
                return;
            }

            List<CoursLien> liens = manager.listerPourEtudiant(institutionId);
            String html = UICoursLienEtudiant.renderListe(liens, null);
            ResponseUtil.sendHtml(exchange, 200, html);

        } catch (IOException | SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur EtudiantCoursLienHandler", e);
            ResponseUtil.sendError(exchange, 500, "Erreur interne");
        }
    }

    private String getInstitutionIdFromSession(HttpExchange exchange) {
        try {
            String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
            if (cookieHeader == null) return null;
            String sessionId = extractSessionId(cookieHeader);
            if (sessionId == null) return null;
            HttpSession session = SessionManager.getSession(sessionId);
            if (session == null) return null;
            String instId = (String) session.getAttribute("institutionId");
            if (instId == null || instId.isBlank()) {
                instId = (String) session.getAttribute("institution_id");
            }
            return instId;
        } catch (Exception e) {
            return null;
        }
    }

    private String extractSessionId(String cookieHeader) {
        for (String cookie : cookieHeader.split(";")) {
            String[] parts = cookie.trim().split("=");
            if (parts.length == 2 && "SESSION_ID".equals(parts[0])) {
                return parts[1];
            }
        }
        return null;
    }

    private void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(302, -1);
    }
}