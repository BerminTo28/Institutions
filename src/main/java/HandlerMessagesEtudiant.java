import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class HandlerMessagesEtudiant implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(HandlerMessagesEtudiant.class.getName());
    private static final int QUERY_TIMEOUT_SECONDS = 15;

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        try {
            String etudiantId = getEtudiantIdFromSession(exchange);
            String institutionId = getInstitutionIdFromSession(exchange);

            if (etudiantId == null || etudiantId.isBlank()
                    || institutionId == null || institutionId.isBlank()) {
                redirect(exchange, "/connexion");
                return;
            }

            // Vérifier les droits d'accès pour le module "messages"
            try (Connection conn = DatabaseManager.getInstance().getConnection()) {
                if (!verifierDroitMessages(conn, etudiantId, institutionId)) {
                    redirect(exchange, "/etudiant/dashboard?error="
                            + encodeParam("acces_refuse_messages"));
                    return;
                }
            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "Erreur de connexion", e);
                String html = UIMessagesEtudiant.renderError(
                        "Erreur de base de données: " + e.getMessage());
                ResponseUtil.sendHtml(exchange, 500, html);
                return;
            }

            if ("GET".equalsIgnoreCase(method)) {
                handleGet(exchange, etudiantId, institutionId);
            } else if ("POST".equalsIgnoreCase(method)) {
                handlePost(exchange, etudiantId, institutionId);
            } else {
                ResponseUtil.sendError(exchange, 405, "Méthode non autorisée");
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Erreur dans HandlerMessagesEtudiant", e);
            String html = UIMessagesEtudiant.renderError("Erreur interne: " + e.getMessage());
            ResponseUtil.sendHtml(exchange, 500, html);
        }
    }

    // ==================== VÉRIFICATION DES DROITS ====================
    private boolean verifierDroitMessages(Connection conn, String etudiantId, String institutionId) {
        String sql = "SELECT messages FROM acces " +
                     "WHERE numero_identifiant = ? AND institution_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setString(1, etudiantId);
            ps.setString(2, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBoolean("messages");
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de la vérification des droits messages", e);
        }
        return false;
    }

    // ==================== GET ====================
    private void handleGet(HttpExchange exchange, String etudiantId, String institutionId)
            throws IOException {
        Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
        String action = params.getOrDefault("action", "");
        String id = params.getOrDefault("id", "");
        String vue = params.getOrDefault("vue", "reception");

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            MessageManager manager = new MessageManager(conn);

            // ---------- DELETE ----------
            if ("delete".equals(action) && !id.isBlank()) {
                boolean deleted = manager.supprimerMessage(id, institutionId, false);
                String msg = deleted ? "Message supprimé." : "Erreur lors de la suppression.";
                handleGetWithMessage(exchange, etudiantId, institutionId,
                        deleted ? "success" : "error", msg);
                return;
            }

            // ---------- LIRE ----------
            if ("lire".equals(action) && !id.isBlank()) {
                manager.marquerCommeLu(id, institutionId);
                Message message = manager.getMessage(id, institutionId);
                if (message == null) {
                    String html = UIMessagesEtudiant.renderError("Message introuvable.");
                    ResponseUtil.sendHtml(exchange, 404, html);
                    return;
                }
                enrichirMessage(conn, message);
                String html = UIMessagesEtudiant.renderLire(etudiantId, message, institutionId);
                ResponseUtil.sendHtml(exchange, 200, html);
                return;
            }

            // ---------- NOUVEAU ----------
            if ("nouveau".equals(vue)) {
                List<Professeur> professeurs = getProfesseurs(conn, institutionId);
                String html = UIMessagesEtudiant.renderNouveau(
                        etudiantId, professeurs, institutionId, null, null);
                ResponseUtil.sendHtml(exchange, 200, html);
                return;
            }

            // ---------- LISTE ----------
            List<Message> messages;
            int nonLus = manager.countNonLus(etudiantId, "ETUDIANT", institutionId);
            if ("envoyes".equals(vue)) {
                messages = manager.getEnvoyés(etudiantId, "ETUDIANT", institutionId);
            } else {
                messages = manager.getReçus(etudiantId, "ETUDIANT", institutionId);
            }
            for (Message m : messages) {
                enrichirMessage(conn, m);
            }

            String html = UIMessagesEtudiant.renderListe(
                    etudiantId, messages, vue, nonLus, institutionId, null, null);
            ResponseUtil.sendHtml(exchange, 200, html);

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur GET messages", e);
            String html = UIMessagesEtudiant.renderError("Erreur: " + e.getMessage());
            ResponseUtil.sendHtml(exchange, 500, html);
        }
    }

    private void handleGetWithMessage(HttpExchange exchange, String etudiantId,
                                       String institutionId, String msgType, String msg)
            throws IOException {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            MessageManager manager = new MessageManager(conn);
            List<Message> messages = manager.getReçus(etudiantId, "ETUDIANT", institutionId);
            for (Message m : messages) enrichirMessage(conn, m);
            int nonLus = manager.countNonLus(etudiantId, "ETUDIANT", institutionId);
            String html = UIMessagesEtudiant.renderListe(
                    etudiantId, messages, "reception", nonLus, institutionId, msgType, msg);
            ResponseUtil.sendHtml(exchange, 200, html);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur handleGetWithMessage", e);
            String html = UIMessagesEtudiant.renderError("Erreur: " + e.getMessage());
            ResponseUtil.sendHtml(exchange, 500, html);
        }
    }

    // ==================== POST ====================
    private void handlePost(HttpExchange exchange, String etudiantId, String institutionId)
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
                String html = UIMessagesEtudiant.renderError(
                        "Tous les champs sont obligatoires.");
                ResponseUtil.sendHtml(exchange, 400, html);
                return;
            }

            Message msg = new Message();
            msg.setInstitutionId(institutionId);
            msg.setExpediteurId(etudiantId);
            msg.setExpediteurType("ETUDIANT");
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
                handleGetWithMessage(exchange, etudiantId, institutionId, msgType, message);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Erreur envoi message", e);
                handleGetWithMessage(exchange, etudiantId, institutionId,
                        "error", "Erreur: " + e.getMessage());
            }
        } else {
            handleGetWithMessage(exchange, etudiantId, institutionId,
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
    private List<Professeur> getProfesseurs(Connection conn, String institutionId)
            throws SQLException {
        ProfesseurData data = new ProfesseurData(conn);
        // ⚠️ ProfesseurData.listerParInstitution lance toujours SQLException
        return data.listerParInstitution(institutionId);
    }

    // ==================== SESSION ====================
    private String getEtudiantIdFromSession(HttpExchange exchange) {
        try {
            String sessionId = extractSessionId(
                    exchange.getRequestHeaders().getFirst("Cookie"));
            if (sessionId == null) return null;
            HttpSession session = SessionManager.getSession(sessionId);
            return session != null ? (String) session.getAttribute("userId") : null;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Impossible de récupérer l'ID étudiant", e);
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
            String[] parts = cookie.trim().split("=", 2);
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

    private String encodeParam(String value) {
        if (value == null) return "";
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    // ==================== REDIRECTION ====================
    private void redirect(HttpExchange exchange, String location) throws IOException {
        if (exchange.getResponseCode() != -1) return;
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(302, -1);
    }
}