import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class HandlerParametresInscription implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(HandlerParametresInscription.class.getName());

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        System.out.println("Path choisi : " + path);

        try {
            String institutionId = getInstitutionIdFromSession(exchange);
            if (institutionId == null || institutionId.isBlank()) {
                exchange.getResponseHeaders().set("Location", "/connexion");
                exchange.sendResponseHeaders(302, -1);
                return;
            }

            if ("GET".equalsIgnoreCase(method)) {
                handleGet(exchange, institutionId);
            } else if ("POST".equalsIgnoreCase(method)) {
                handlePost(exchange, institutionId);
            } else {
                ResponseUtil.sendError(exchange, 405, "Méthode non autorisée");
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Erreur dans HandlerParametresInscription", e);
            String html = UIParametresInscription.renderError("Erreur interne: " + e.getMessage());
            ResponseUtil.sendHtml(exchange, 500, html);
        }
    }

    private void handleGet(HttpExchange exchange, String institutionId) throws IOException {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            // Récupérer toutes les classes (avec promotion)
            ClasseData classeData = new ClasseData();
            List<Classe> classes = classeData.toutLister(institutionId);

            // Récupérer les années académiques
            AnneeAcademiqueData anneeData = new AnneeAcademiqueData(conn);
            List<AnneeAcademique> annees = anneeData.listByInstitution(institutionId);

            // Récupérer les périodes
            PeriodeData periodeData = new PeriodeData(conn);
            List<Periode> periodes = periodeData.listByInstitution(institutionId);

            // Paramètres sélectionnés (si année et période sont fournies dans la query)
            Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
            String anneeSel = params.getOrDefault("annee", "");
            String periodeSel = params.getOrDefault("periode", "");

            List<ParametreInscription> parametres = new ArrayList<>();
            if (!anneeSel.isBlank() && !periodeSel.isBlank()) {
                ParametreInscriptionManager manager = new ParametreInscriptionManager();
                parametres = manager.getByInstitutionAnneePeriode(institutionId, anneeSel, periodeSel);
            }

            String html = UIParametresInscription.render(
                institutionId,
                annees,
                periodes,
                classes,
                parametres,
                anneeSel,
                periodeSel,
                null,
                null
            );
            ResponseUtil.sendHtml(exchange, 200, html);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur SQL", e);
            String html = UIParametresInscription.renderError("Erreur base de données.");
            ResponseUtil.sendHtml(exchange, 500, html);
        }
    }

    private void handlePost(HttpExchange exchange, String institutionId) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> params = parseFormData(body);
        String annee = params.get("annee");
        String periode = params.get("periode");

        if (annee == null || annee.isBlank() || periode == null || periode.isBlank()) {
            String html = UIParametresInscription.renderError("Veuillez sélectionner une année et une période.");
            ResponseUtil.sendHtml(exchange, 400, html);
            return;
        }

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            // Récupérer toutes les classes (avec promotion)
            ClasseData classeData = new ClasseData();
            List<Classe> classes = classeData.toutLister(institutionId);

            // Construire la liste des paramètres
            List<ParametreInscription> parametres = new ArrayList<>();
            for (Classe c : classes) {
                String key = "inscription_" + c.getNomClasse().replaceAll("\\s+", "_");
                boolean actif = "on".equals(params.get(key));
                ParametreInscription p = new ParametreInscription();
                p.setInstitutionId(institutionId);
                p.setAnneeAcademique(annee);
                p.setPeriode(periode);
                p.setClasse(c.getNomClasse());
                p.setActif(actif);
                // ✅ Récupérer la promotion depuis la classe
                p.setPromotion(c.getPromotion()); // Assurez-vous que Classe a getPromotion()
                parametres.add(p);
            }

            ParametreInscriptionManager manager = new ParametreInscriptionManager();
            manager.saveAll(institutionId, annee, periode, parametres);

            // Recharger les données pour l'affichage
            AnneeAcademiqueData anneeData = new AnneeAcademiqueData(conn);
            List<AnneeAcademique> annees = anneeData.listByInstitution(institutionId);
            PeriodeData periodeData = new PeriodeData(conn);
            List<Periode> periodes = periodeData.listByInstitution(institutionId);

            String html = UIParametresInscription.render(
                institutionId,
                annees,
                periodes,
                classes,
                parametres,
                annee,
                periode,
                "success",
                "Paramètres d'inscription mis à jour avec succès."
            );
            ResponseUtil.sendHtml(exchange, 200, html);

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur sauvegarde", e);
            String html = UIParametresInscription.renderError("Erreur lors de l'enregistrement.");
            ResponseUtil.sendHtml(exchange, 500, html);
        }
    }

    // ==================== SESSION ET PARSING ====================

    private String getInstitutionIdFromSession(HttpExchange exchange) {
        try {
            String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
            if (cookieHeader == null) return null;
            String sessionId = null;
            for (String cookie : cookieHeader.split(";")) {
                String[] parts = cookie.trim().split("=");
                if (parts.length == 2 && "SESSION_ID".equals(parts[0])) {
                    sessionId = parts[1];
                    break;
                }
            }
            if (sessionId == null) return null;
            HttpSession session = SessionManager.getSession(sessionId);
            if (session == null) return null;
            String instId = (String) session.getAttribute("institutionId");
            if (instId == null || instId.isBlank()) {
                instId = (String) session.getAttribute("institution_id");
            }
            return instId;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erreur récupération institutionId", e);
            return null;
        }
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

    private Map<String, String> parseFormData(String body) {
        Map<String, String> params = new HashMap<>();
        if (body == null || body.isBlank()) return params;
        for (String pair : body.split("&")) {
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