import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class HandlerAbsenceProfesseur implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(HandlerAbsenceProfesseur.class.getName());
    private final AbsenceManager absenceManager = new AbsenceManager();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        try {
            String professeurId = getProfesseurIdFromSession(exchange);
            String institutionId = getInstitutionIdFromSession(exchange);

            if (professeurId == null || institutionId == null) {
                exchange.getResponseHeaders().set("Location", "/connexion");
                exchange.sendResponseHeaders(302, -1);
                return;
            }

            if ("GET".equalsIgnoreCase(method)) {
                handleGet(exchange, professeurId, institutionId);
            } else {
                ResponseUtil.sendError(exchange, 405, "Méthode non autorisée");
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Erreur dans HandlerAbsenceProfesseur", e);
            String html = UIAbsenceProfesseur.renderError("Erreur interne: " + e.getMessage());
            ResponseUtil.sendHtml(exchange, 500, html);
        }
    }

    private void handleGet(HttpExchange exchange, String professeurId, String institutionId) throws IOException {
        Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
        String annee = params.getOrDefault("annee", "");
        String periode = params.getOrDefault("periode", "");
        String matiere = params.getOrDefault("matiere", ""); // optionnel

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            // Charger les années et périodes disponibles pour les filtres
            AnneeAcademiqueData anneeData = new AnneeAcademiqueData(conn);
            List<AnneeAcademique> annees = anneeData.listByInstitution(institutionId);

            PeriodeData periodeData = new PeriodeData(conn);
            List<Periode> periodes = periodeData.listByInstitution(institutionId);

            // Récupérer les absences du professeur avec les filtres (directement en base)
            List<Absence> absences = absenceManager.rechercherAbsences(
                "PROFESSEUR",
                annee.isEmpty() ? null : annee,
                periode.isEmpty() ? null : periode,
                null,                  // classe (non utilisée pour le professeur)
                matiere.isEmpty() ? null : matiere,
                professeurId           // numéro d'identifiant du professeur
            );

            // Générer la page HTML (le filtre matière est passé en paramètre)
            String html = UIAbsenceProfesseur.render(
                professeurId,
                annees,
                periodes,
                absences,
                annee,
                periode,
                matiere,
                null,
                null
            );
            ResponseUtil.sendHtml(exchange, 200, html);

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur SQL", e);
            String html = UIAbsenceProfesseur.renderError("Erreur base de données: " + e.getMessage());
            ResponseUtil.sendHtml(exchange, 500, html);
        }
    }

    // ==================== MÉTHODES DE SESSION ====================

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

    private Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null) return params;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=");
            if (kv.length == 2) {
                try {
                    params.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8.name()),
                               URLDecoder.decode(kv[1], StandardCharsets.UTF_8.name()));
                } catch (UnsupportedEncodingException e) { /* ignore */ }
            }
        }
        return params;
    }
}