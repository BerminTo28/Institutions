import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class HandlerEtudiantProfil implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(HandlerEtudiantProfil.class.getName());

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        if (!"GET".equalsIgnoreCase(method)) {
            ResponseUtil.sendError(exchange, 405, "Méthode non autorisée");
            return;
        }

        try {
            String numeroEtudiant = getNumeroEtudiantFromSession(exchange);
            String institutionId = getInstitutionIdFromSession(exchange);

            if (numeroEtudiant == null || institutionId == null) {
                exchange.getResponseHeaders().set("Location", "/connexion");
                exchange.sendResponseHeaders(302, -1);
                return;
            }

            // Vérifier les droits d'accès (profil)
            AccesEtudiantData accesData = new AccesEtudiantData();
            AccesEtudiant acces = accesData.getByEtudiant(numeroEtudiant, institutionId);
            if (acces == null || !acces.isProfil()) {
                String html = UIEtudiantProfil.renderError("Vous n'avez pas l'autorisation de consulter votre profil.");
                ResponseUtil.sendHtml(exchange, 403, html);
                return;
            }

            handleGet(exchange, numeroEtudiant, institutionId);

        } catch (IOException | SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur dans HandlerEtudiantProfil", e);
            String html = UIEtudiantProfil.renderError("Erreur interne: " + e.getMessage());
            ResponseUtil.sendHtml(exchange, 500, html);
        }
    }

    private void handleGet(HttpExchange exchange, String numeroEtudiant, String institutionId) throws IOException {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            EtudiantData etudiantData = new EtudiantData(conn);
            Etudiant etudiant = etudiantData.read(numeroEtudiant,institutionId);
            if (etudiant == null) {
                String html = UIEtudiantProfil.renderError("Étudiant non trouvé.");
                ResponseUtil.sendHtml(exchange, 404, html);
                return;
            }

            // Vérifier que l'étudiant appartient bien à l'institution
            if (!institutionId.equals(etudiant.getInstitutionId())) {
                String html = UIEtudiantProfil.renderError("Accès non autorisé à ce profil.");
                ResponseUtil.sendHtml(exchange, 403, html);
                return;
            }

            // Générer la page
            String html = UIEtudiantProfil.render(etudiant);
            ResponseUtil.sendHtml(exchange, 200, html);

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur base de données", e);
            String html = UIEtudiantProfil.renderError("Erreur lors du chargement du profil.");
            ResponseUtil.sendHtml(exchange, 500, html);
        }
    }

    // ==================== MÉTHODES DE SESSION ====================

    private String getNumeroEtudiantFromSession(HttpExchange exchange) {
        try {
            String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
            if (cookieHeader == null) return null;
            String sessionId = extractSessionId(cookieHeader);
            if (sessionId == null) return null;
            HttpSession session = SessionManager.getSession(sessionId);
            if (session == null) return null;
            return (String) session.getAttribute("userId");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Impossible de récupérer l'ID étudiant", e);
            return null;
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
            LOGGER.log(Level.WARNING, "Impossible de récupérer l'institutionId", e);
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
}