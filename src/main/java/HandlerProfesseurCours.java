import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class HandlerProfesseurCours implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(HandlerProfesseurCours.class.getName());

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            // Récupérer l'ID du professeur depuis la session
            String professeurId = getProfesseurIdFromSession(exchange);
            String institutionId = getInstitutionIdFromSession(exchange);

            if (professeurId == null || institutionId == null) {
                exchange.getResponseHeaders().set("Location", "/connexion");
                exchange.sendResponseHeaders(302, -1);
                return;
            }

            // Récupérer le professeur complet
            Professeur professeur = getProfesseur(professeurId, institutionId);
            if (professeur == null) {
                String html = UIProfesseurCours.renderError("Professeur introuvable.");
                ResponseUtil.sendHtml(exchange, 404, html);
                return;
            }

            // Récupérer les créneaux (cours) du professeur
            List<Professeur.Creneau> creneaux = getCreneaux(professeurId, institutionId);
            professeur.setCreneaux(creneaux);

            // Générer la page HTML (appel avec 2 paramètres)
            String html = UIProfesseurCours.render(professeur, creneaux);
            ResponseUtil.sendHtml(exchange, 200, html);

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Erreur I/O dans HandlerProfesseurCours", e);
            String html = UIProfesseurCours.renderError("Erreur réseau: " + e.getMessage());
            ResponseUtil.sendHtml(exchange, 500, html);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erreur inattendue dans HandlerProfesseurCours", e);
            String html = UIProfesseurCours.renderError("Erreur: " + e.getMessage());
            ResponseUtil.sendHtml(exchange, 500, html);
        }
    }

    // ==================== RÉCUPÉRATION SESSION ====================

    private String getProfesseurIdFromSession(HttpExchange exchange) {
        try {
            String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
            if (cookieHeader == null) return null;
            String sessionId = extractSessionId(cookieHeader);
            if (sessionId == null) return null;
            HttpSession session = SessionManager.getSession(sessionId);
            if (session == null) return null;
            return (String) session.getAttribute("userId");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Impossible de récupérer l'ID professeur", e);
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

    // ==================== RÉCUPÉRATION DONNÉES ====================

    private Professeur getProfesseur(String professeurId, String institutionId) {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            ProfesseurData data = new ProfesseurData(conn);
            return data.trouverParId(professeurId, institutionId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur récupération professeur", e);
            return null;
        }
    }

    private List<Professeur.Creneau> getCreneaux(String professeurId, String institutionId) {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            ProfesseurData data = new ProfesseurData(conn);
            return data.getCreneauxParProfesseur(professeurId, institutionId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur récupération créneaux", e);
            return java.util.Collections.emptyList();
        }
    }
}