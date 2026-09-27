import java.io.IOException;
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

public class HandlerMessagesProfesseur implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(HandlerMessagesProfesseur.class.getName());

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        try {
            String professeurId = getProfesseurIdFromSession(exchange);
            String institutionId = getInstitutionIdFromSession(exchange);

            if (professeurId == null || professeurId.isBlank()
                    || institutionId == null || institutionId.isBlank()) {
                redirect(exchange, "/connexion");
                return;
            }

            if ("GET".equalsIgnoreCase(method)) {
                handleGet(exchange, professeurId, institutionId);
            } else if ("POST".equalsIgnoreCase(method)) {
                handlePost(exchange, professeurId, institutionId);
            } else {
                ResponseUtil.sendError(exchange, 405, "Méthode non autorisée");
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Erreur dans HandlerMessagesProfesseur", e);
            String html = UIMessagesProfesseur.renderError("Erreur interne: " + e.getMessage());
            ResponseUtil.sendHtml(exchange, 500, html);
        }
    }

    // ==================== GET ====================
    private void handleGet(HttpExchange exchange, String professeurId, String institutionId)
            throws IOException {
        Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
        String action = params.getOrDefault("action", "");
        String id = params.getOrDefault("id", "");
        String vue = params.getOrDefault("vue", "reception");

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            MessageManager manager = new MessageManager(conn);

            // ---------- SUPPRESSION ----------
            if ("delete".equals(action) && !id.isBlank()) {
                boolean deleted = manager.supprimerMessage(id, institutionId, false);
                String msg = deleted ? "Message supprimé." : "Erreur lors de la suppression.";
                handleGetWithMessage(exchange, professeurId, institutionId,
                        deleted ? "success" : "error", msg);
                return;
            }

            // ---------- LECTURE ----------
            if ("lire".equals(action) && !id.isBlank()) {
                manager.marquerCommeLu(id, institutionId);
                Message message = manager.getMessage(id, institutionId);
                if (message == null) {
                    String html = UIMessagesProfesseur.renderError("Message introuvable.");
                    ResponseUtil.sendHtml(exchange, 404, html);
                    return;
                }
                enrichirMessage(conn, message);
                String html = UIMessagesProfesseur.renderLire(professeurId, message, institutionId);
                ResponseUtil.sendHtml(exchange, 200, html);
                return;
            }

            // ---------- NOUVEAU ----------
            if ("nouveau".equals(vue)) {
                List<Etudiant> etudiants = getEtudiants(conn, institutionId);
                List<Professeur> professeurs = getProfesseurs(conn, institutionId);
                String html = UIMessagesProfesseur.renderNouveau(
                        professeurId, etudiants, professeurs, institutionId, null, null);
                ResponseUtil.sendHtml(exchange, 200, html);
                return;
            }

            // ---------- LISTE ----------
            List<Message> messages;
            int nonLus = manager.countNonLus(professeurId, "PROFESSEUR", institutionId);
            if ("envoyes".equals(vue)) {
                messages = manager.getEnvoyés(professeurId, "PROFESSEUR", institutionId);
            } else {
                messages = manager.getReçus(professeurId, "PROFESSEUR", institutionId);
            }
            for (Message m : messages) {
                enrichirMessage(conn, m);
            }

            String html = UIMessagesProfesseur.renderListe(
                    professeurId, messages, vue, nonLus, institutionId, null, null);
            ResponseUtil.sendHtml(exchange, 200, html);

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur GET messages professeur", e);
            String html = UIMessagesProfesseur.renderError("Erreur: " + e.getMessage());
            ResponseUtil.sendHtml(exchange, 500, html);
        }
    }

    private void handleGetWithMessage(HttpExchange exchange, String professeurId,
                                       String institutionId, String msgType, String msg)
            throws IOException {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            MessageManager manager = new MessageManager(conn);
            List<Message> messages = manager.getReçus(professeurId, "PROFESSEUR", institutionId);
            for (Message m : messages) enrichirMessage(conn, m);
            int nonLus = manager.countNonLus(professeurId, "PROFESSEUR", institutionId);
            String html = UIMessagesProfesseur.renderListe(
                    professeurId, messages, "reception", nonLus, institutionId, msgType, msg);
            ResponseUtil.sendHtml(exchange, 200, html);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur handleGetWithMessage", e);
            String html = UIMessagesProfesseur.renderError("Erreur: " + e.getMessage());
            ResponseUtil.sendHtml(exchange, 500, html);
        }
    }

    // ==================== POST ====================
    private void handlePost(HttpExchange exchange, String professeurId, String institutionId)
            throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> params = parseFormData(body);
        String action = params.get("action");

        if ("envoyer".equals(action)) {
            String destinataireId = params.get("destinataire_id");
            String destinataireType = params.get("destinataire_type");
            String sujet = params.get("sujet");
            String contenu = params.get("contenu");
            String reponseA = params.get("reponse_a");

            if (destinataireId == null || destinataireId.isBlank()
                    || sujet == null || sujet.isBlank()
                    || contenu == null || contenu.isBlank()) {
                String html = UIMessagesProfesseur.renderError(
                        "Tous les champs sont obligatoires.");
                ResponseUtil.sendHtml(exchange, 400, html);
                return;
            }

            Message msg = new Message();
            msg.setInstitutionId(institutionId);
            msg.setExpediteurId(professeurId);
            msg.setExpediteurType("PROFESSEUR");
            msg.setDestinataireId(destinataireId);
            msg.setDestinataireType(destinataireType);
            msg.setSujet(sujet);
            msg.setContenu(contenu);
            msg.setLu(false);

            if (reponseA != null && !reponseA.isBlank()) {
                msg.setReponseA(reponseA);
            }

            try (Connection conn = DatabaseManager.getInstance().getConnection()) {
                MessageManager manager = new MessageManager(conn);
                boolean ok = manager.envoyerMessage(msg);
                String msgType = ok ? "success" : "error";
                String message = ok ? "Message envoyé avec succès." : "Erreur lors de l'envoi.";
                handleGetWithMessage(exchange, professeurId, institutionId, msgType, message);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Erreur envoi message", e);
                handleGetWithMessage(exchange, professeurId, institutionId,
                        "error", "Erreur: " + e.getMessage());
            }
        } else {
            handleGetWithMessage(exchange, professeurId, institutionId,
                    "error", "Action inconnue.");
        }
    }

    // ==================== ENRICHISSEMENT ====================
    /**
     * ✅ Enrichit un message avec les noms complets de l'expéditeur et du destinataire.
     *    Utilise message.getInstitutionId() (l'institution du message).
     */
    private void enrichirMessage(Connection conn, Message message) throws SQLException {
        if (message == null) return;

        final String institutionId = message.getInstitutionId();
        if (institutionId == null || institutionId.isBlank()) {
            LOGGER.fine(() -> "enrichirMessage : institutionId manquant pour "
                    + message.getId());
            return;
        }

        String expediteurId = message.getExpediteurId();
        String expediteurType = message.getExpediteurType();
        String destinataireId = message.getDestinataireId();
        String destinataireType = message.getDestinataireType();

        // ---------- Expéditeur ----------
        if ("ETUDIANT".equals(expediteurType)) {
            EtudiantData data = new EtudiantData(conn);
            Etudiant e = data.read(expediteurId, institutionId);
            if (e != null) message.setNomExpediteur(e.getNomComplet());
        } else if ("PROFESSEUR".equals(expediteurType)) {
            ProfesseurData data = new ProfesseurData(conn);
            Professeur p = data.trouverParId(expediteurId, institutionId);
            if (p != null) message.setNomExpediteur(p.getNomComplet());
        }

        // ---------- Destinataire ----------
        if ("ETUDIANT".equals(destinataireType)) {
            EtudiantData data = new EtudiantData(conn);
            Etudiant e = data.read(destinataireId, institutionId);
            if (e != null) message.setNomDestinataire(e.getNomComplet());
        } else if ("PROFESSEUR".equals(destinataireType)) {
            ProfesseurData data = new ProfesseurData(conn);
            Professeur p = data.trouverParId(destinataireId, institutionId);
            if (p != null) message.setNomDestinataire(p.getNomComplet());
        }
    }

    // ==================== LECTURE UTILITAIRES ====================
    private List<Etudiant> getEtudiants(Connection conn, String institutionId) {
        EtudiantData data = new EtudiantData(conn);
        // ✅ EtudiantData.readAll ne lance plus SQLException
        return data.readAll(institutionId);
    }

    private List<Professeur> getProfesseurs(Connection conn, String institutionId)
            throws SQLException {
        ProfesseurData data = new ProfesseurData(conn);
        // ⚠️ ProfesseurData.listerParInstitution lance toujours SQLException
        return data.listerParInstitution(institutionId);
    }

    // ==================== SESSION ====================
    private String getProfesseurIdFromSession(HttpExchange exchange) {
        try {
            String sessionId = extractSessionId(
                    exchange.getRequestHeaders().getFirst("Cookie"));
            if (sessionId == null) return null;
            HttpSession session = SessionManager.getSession(sessionId);
            return session != null ? (String) session.getAttribute("userId") : null;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Impossible de récupérer l'ID professeur", e);
            return null;
        }
    }

    private String getInstitutionIdFromSession(HttpExchange exchange) {
        try {
            String sessionId = extractSessionId(
                    exchange.getRequestHeaders().getFirst("Cookie"));
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

    /**
     * ✅ Extrait le SESSION_ID du cookie.
     *    Utilise split("=", 2) pour gérer les cookies contenant '='.
     */
    private String extractSessionId(String cookieHeader) {
        if (cookieHeader == null || cookieHeader.isBlank()) return null;
        for (String cookie : cookieHeader.split(";")) {
            String[] parts = cookie.trim().split("=", 2);   // ✅ split limité à 2
            if (parts.length == 2 && "SESSION_ID".equals(parts[0])) {
                return parts[1];
            }
        }
        return null;
    }

    // ==================== PARSING ====================
    private Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isBlank()) return params;
        for (String pair : query.split("&")) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                try {
                    String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                    String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                    params.put(key, value);
                } catch (Exception ignored) { /* rien */ }
            }
        }
        return params;
    }

    private Map<String, String> parseFormData(String body) {
        return parseQuery(body);
    }

    // ==================== REDIRECTION ====================
    private void redirect(HttpExchange exchange, String location) throws IOException {
        if (exchange.getResponseCode() != -1) return;
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(302, -1);
    }
}