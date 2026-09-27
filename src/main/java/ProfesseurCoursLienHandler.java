import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * Handler pour la gestion des liens de cours (professeur)
 */
public class ProfesseurCoursLienHandler implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(ProfesseurCoursLienHandler.class.getName());
    private final CoursLienManager manager = new CoursLienManager();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        try {
            String professeurId = getProfesseurIdFromSession(exchange);
            String institutionId = getInstitutionIdFromSession(exchange);
            if (professeurId == null || institutionId == null) {
                redirect(exchange, "/connexion");
                return;
            }

            String[] segments = path.split("/");
            String lienId = segments.length > 4 ? segments[4] : null;
            String action = segments.length > 5 ? segments[5] : null;

            // Création - afficher formulaire
            if ("GET".equals(method) && path.endsWith("/creer")) {
                String html = UICoursLienProf.renderFormulaire(null, null, null, false);
                ResponseUtil.sendHtml(exchange, 200, html);
                return;
            }

            // Création - traiter le formulaire
            if ("POST".equals(method) && path.endsWith("/creer")) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                Map<String, String> params = JsonUtil.parseQuery(body);
                CoursLien lien = new CoursLien();
                lien.setTitre(params.get("titre"));
                lien.setDescription(params.get("description"));
                lien.setUrl(params.get("url"));
                lien.setDateDebut(parseDateTime(params.get("dateDebut")));
                lien.setDateFin(parseDateTime(params.get("dateFin")));
                lien.setProfesseurId(professeurId);
                lien.setInstitutionId(institutionId);
                try {
                    boolean success = manager.creer(lien);
                    if (success) {
                        redirect(exchange, "/professeur/cours-en-ligne?success=Lien ajouté avec succès");
                    } else {
                        String html = UICoursLienProf.renderFormulaire(lien, "Erreur lors de l'ajout", null, false);
                        ResponseUtil.sendHtml(exchange, 200, html);
                    }
                } catch (SQLException e) {
                    LOGGER.log(Level.SEVERE, "Erreur création lien", e);
                    String html = UICoursLienProf.renderFormulaire(lien, "Erreur base de données", null, false);
                    ResponseUtil.sendHtml(exchange, 500, html);
                }
                return;
            }

            // Modification - afficher formulaire
            if (lienId != null && "modifier".equals(action) && "GET".equals(method)) {
                CoursLien lien ;
                try {
                    lien = manager.trouverParId(lienId, institutionId);
                } catch (SQLException e) {
                    LOGGER.log(Level.SEVERE, "Erreur recherche lien pour modification", e);
                    ResponseUtil.sendError(exchange, 500, "Erreur base de données");
                    return;
                }
                if (lien == null) {
                    ResponseUtil.sendError(exchange, 404, "Lien non trouvé");
                    return;
                }
                String html = UICoursLienProf.renderFormulaire(lien, null, null, true);
                ResponseUtil.sendHtml(exchange, 200, html);
                return;
            }

            // Modification - traiter le formulaire
            if (lienId != null && "modifier".equals(action) && "POST".equals(method)) {
                CoursLien lien ;
                try {
                    lien = manager.trouverParId(lienId, institutionId);
                } catch (SQLException e) {
                    LOGGER.log(Level.SEVERE, "Erreur recherche lien pour mise à jour", e);
                    ResponseUtil.sendError(exchange, 500, "Erreur base de données");
                    return;
                }
                if (lien == null) {
                    ResponseUtil.sendError(exchange, 404, "Lien non trouvé");
                    return;
                }
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                Map<String, String> params = JsonUtil.parseQuery(body);
                lien.setTitre(params.get("titre"));
                lien.setDescription(params.get("description"));
                lien.setUrl(params.get("url"));
                lien.setDateDebut(parseDateTime(params.get("dateDebut")));
                lien.setDateFin(parseDateTime(params.get("dateFin")));
                try {
                    boolean success = manager.mettreAJour(lien);
                    if (success) {
                        redirect(exchange, "/professeur/cours-en-ligne?success=Lien mis à jour");
                    } else {
                        String html = UICoursLienProf.renderFormulaire(lien, "Erreur mise à jour", null, true);
                        ResponseUtil.sendHtml(exchange, 200, html);
                    }
                } catch (SQLException e) {
                    LOGGER.log(Level.SEVERE, "Erreur mise à jour lien", e);
                    String html = UICoursLienProf.renderFormulaire(lien, "Erreur base de données", null, true);
                    ResponseUtil.sendHtml(exchange, 500, html);
                }
                return;
            }

            // Suppression
            if (lienId != null && "supprimer".equals(action)) {
                try {
                    boolean success = manager.supprimer(lienId, institutionId);
                    if (success) {
                        redirect(exchange, "/professeur/cours-en-ligne?success=Lien supprimé");
                    } else {
                        redirect(exchange, "/professeur/cours-en-ligne?error=Échec suppression");
                    }
                } catch (SQLException e) {
                    LOGGER.log(Level.SEVERE, "Erreur suppression lien", e);
                    redirect(exchange, "/professeur/cours-en-ligne?error=Erreur base de données");
                }
                return;
            }

            // Liste des liens (GET)
            if ("GET".equals(method)) {
                String success = getQueryParam(exchange, "success");
                String error = getQueryParam(exchange, "error");
                List<CoursLien> liens;
                try {
                    liens = manager.listerParProfesseur(professeurId, institutionId);
                } catch (SQLException e) {
                    LOGGER.log(Level.SEVERE, "Erreur liste liens", e);
                    ResponseUtil.sendError(exchange, 500, "Erreur base de données");
                    return;
                }
                String html = UICoursLienProf.renderListe(liens, success, error);
                ResponseUtil.sendHtml(exchange, 200, html);
                return;
            }

            ResponseUtil.sendError(exchange, 405, "Méthode non autorisée");

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Erreur dans ProfesseurCoursLienHandler", e);
            ResponseUtil.sendError(exchange, 500, "Erreur interne: " + e.getMessage());
        }
    }

    // ==================== UTILITAIRES ====================

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

    private String getQueryParam(HttpExchange exchange, String key) {
        String query = exchange.getRequestURI().getQuery();
        if (query == null) return null;
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=");
            if (parts.length == 2 && parts[0].equals(key)) {
                return parts[1];
            }
        }
        return null;
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception e) {
            return null;
        }
    }
}