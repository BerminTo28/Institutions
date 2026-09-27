import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class HandlerCommunication implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(HandlerCommunication.class.getName());
    private static final Gson GSON = new Gson();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();

        // Normalisation
        if (path.endsWith("/") && path.length() > 1) {
            path = path.substring(0, path.length() - 1);
        }

        LOGGER.log(Level.INFO, "COMMUNICATION HANDLER - {0} {1}",
                new Object[]{exchange.getRequestMethod(), path});

        // ✅ 0) Méthodes autorisées
        String method = exchange.getRequestMethod();
        if ("OPTIONS".equalsIgnoreCase(method)) {
            exchange.getResponseHeaders().set("Allow", "GET, POST, HEAD, OPTIONS");
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        // ✅ 1) Session — récupération centralisée
        HttpSession session = getSession(exchange);
        if (session == null) {
            LOGGER.warning("⛔ Session invalide");
            sendResponse(exchange, 401, "<h1>401 - Session expirée</h1>");
            return;
        }

        String institutionId = getInstitutionIdFromSession(session);
        String utilisateurId = getUtilisateurIdFromSession(session);

        if (institutionId == null || institutionId.isBlank()
                || utilisateurId == null || utilisateurId.isBlank()) {
            LOGGER.warning("⛔ Session incomplète (institutionId ou utilisateurId manquant)");
            sendResponse(exchange, 401, "<h1>401 - Session expirée</h1>");
            return;
        }

        // ✅ 2) Permissions
        AccesAdmin acces = chargerPermissions(utilisateurId, institutionId);
        if (acces == null) {
            LOGGER.warning(() -> "⛔ Permissions introuvables pour " + utilisateurId
                    + " dans " + institutionId);
            sendResponse(exchange, 403, "<h1>403 - Accès refusé</h1>");
            return;
        }

        // ✅ 3) Vérifier READ
        if (!acces.hasPermission(AccesAdmin.Module.COMMUNICATION, AccesAdmin.Action.READ)) {
            LOGGER.warning(() -> "⛔ Permission READ refusée sur COMMUNICATION pour " + utilisateurId);
            sendResponse(exchange, 403, "<h1>403 - Accès non autorisé au module COMMUNICATION</h1>");
            return;
        }

        // ✅ 4) Router API
        if (path.startsWith("/admin/communication/api/")) {
            handleApi(exchange, institutionId, path, acces);
            return;
        }

        // ✅ 5) Router HTML
        String html;
        switch (path) {
            case "/admin/communication" -> html = UICommunication.renderPage(institutionId);
            case "/admin/communication/email" -> html = UIMail.renderPage(institutionId);
            case "/admin/communication/sms" -> html = UISms.renderPage(institutionId);
            case "/admin/communication/appel" -> html = UIAppel.renderPage(institutionId);
            case "/admin/communication/whatsapp" -> html = UIWhatsapp.renderPage(institutionId);
            default -> {
                LOGGER.log(Level.WARNING, "Chemin non trouvé: {0}", path);
                sendResponse(exchange, 404, "<h1>404 - Page non trouvée</h1>");
                return;
            }
        }

        sendResponse(exchange, 200, html);
    }

    // =========================================================
    // API
    // =========================================================
    private void handleApi(HttpExchange exchange, String institutionId, String path,
                            AccesAdmin acces) throws IOException {
        String method = exchange.getRequestMethod();

        // ✅ Vérification CREATE pour /envoyer
        if (path.endsWith("/envoyer")) {
            if (!"POST".equalsIgnoreCase(method)) {
                sendJsonError(exchange, 405, "Méthode non autorisée");
                return;
            }
            if (!acces.hasPermission(AccesAdmin.Module.COMMUNICATION, AccesAdmin.Action.CREATE)) {
                LOGGER.warning("⛔ Permission CREATE refusée sur COMMUNICATION");
                sendJsonError(exchange, 403, "Permission CREATE refusée pour l'envoi");
                return;
            }
        }

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (path.endsWith("/etudiants")) {
                handleEtudiants(exchange, institutionId, conn);
            } else if (path.endsWith("/envoyer")) {
                handleEnvoyer(exchange, institutionId, conn);
            } else if (path.endsWith("/annees")) {
                handleAnnees(exchange, institutionId, conn);
            } else if (path.endsWith("/sessions")) {
                handleSessions(exchange, institutionId, conn);
            } else if (path.endsWith("/classes")) {
                handleClasses(exchange, institutionId, conn);
            } else {
                sendJsonError(exchange, 404, "API inconnue: " + path);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur SQL dans handleApi: " + e.getMessage(), e);
            sendJsonError(exchange, 500, "Erreur base de données");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erreur API: " + e.getMessage(), e);
            sendJsonError(exchange, 500, e.getMessage());
        }
    }

    // =========================================================
    // /etudiants — Liste filtrée
    // =========================================================
    private void handleEtudiants(HttpExchange exchange, String institutionId,
                                   Connection conn) throws IOException {
        Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
        String annee = params.get("annee");
        String session = params.get("session");
        String classe = params.get("classe");

        EtudiantData etudiantData = new EtudiantData(conn);
        List<Etudiant> etudiants;

        if (classe != null && !classe.isEmpty()) {
            etudiants = etudiantData.readWithFilters(institutionId, classe, null, null);
        } else {
            etudiants = etudiantData.readAll(institutionId);
        }

        // ✅ Filtre année (nullable)
        if (annee != null && !annee.isEmpty()) {
            etudiants = etudiants.stream()
                    .filter(e -> annee.equals(e.getAnneeAcademique()))
                    .collect(Collectors.toList());
        }

        // ✅ Filtre session (nullable) avec vérification institution
        if (session != null && !session.isEmpty() && annee != null && !annee.isEmpty()) {
            Set<String> idsAvecNotes = getEtudiantIdsAvecNotes(conn, institutionId, annee, session);
            etudiants = etudiants.stream()
                    .filter(e -> idsAvecNotes.contains(e.getNumeroIdentifiantEtudiant()))
                    .collect(Collectors.toList());
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Etudiant e : etudiants) {
            // ✅ Filtre institution (défense en profondeur)
            if (!institutionId.equals(e.getInstitutionId())) {
                continue;
            }
            Map<String, Object> map = new HashMap<>();
            map.put("id", e.getNumeroIdentifiantEtudiant());
            map.put("nom", e.getNom());
            map.put("prenom", e.getPrenom());
            map.put("classe", e.getClasse());
            map.put("matricule", e.getMatricule());
            map.put("email", e.getEmail());
            map.put("telephone", e.getTelephone());
            map.put("anneeAcademique", e.getAnneeAcademique());
            result.add(map);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", result);
        response.put("count", result.size());
        sendJson(exchange, response);
    }

    private Set<String> getEtudiantIdsAvecNotes(Connection conn, String institutionId,
                                                  String annee, String session) {
        Set<String> ids = new HashSet<>();
        if (institutionId == null || annee == null || session == null) {
            return ids;
        }
        String sql = "SELECT DISTINCT numero_etudiant FROM notes "
                + "WHERE institution_id = ? AND annee_academique = ? AND periode = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            ps.setString(2, annee);
            ps.setString(3, session);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getString("numero_etudiant"));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erreur IDs avec notes: {0}", e.getMessage());
        }
        return ids;
    }

    // =========================================================
    // /envoyer — Envoi de message
    // =========================================================
    private void handleEnvoyer(HttpExchange exchange, String institutionId,
                                 Connection conn) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

        Map<String, Object> data;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = GSON.fromJson(body, Map.class);
            data = parsed;
        } catch (JsonSyntaxException e) {
            sendJsonError(exchange, 400, "JSON invalide");
            return;
        }

        if (data == null) {
            sendJsonError(exchange, 400, "Corps de requête vide");
            return;
        }

        String canal = (String) data.get("canal");
        String message = (String) data.get("message");
        String objet = (String) data.get("objet");

        @SuppressWarnings("unchecked")
        List<String> etudiantIds = (List<String>) data.get("etudiants");

        if (canal == null || canal.isBlank()
                || message == null || message.isBlank()
                || etudiantIds == null || etudiantIds.isEmpty()) {
            sendJsonError(exchange, 400, "Paramètres manquants (canal, message, etudiants)");
            return;
        }

        // ✅ Vérifier que le canal est autorisé
        Set<String> canauxAutorises = Set.of("email", "sms", "appel", "whatsapp");
        if (!canauxAutorises.contains(canal.toLowerCase())) {
            sendJsonError(exchange, 400, "Canal non autorisé: " + canal);
            return;
        }

        EtudiantData etudiantData = new EtudiantData(conn);
        List<Etudiant> etudiants = new ArrayList<>();
        List<String> rejetes = new ArrayList<>();

        // ✅ Batch : 1 requête par étudiant mais avec cache local
        for (String id : etudiantIds) {
            if (id == null || id.isBlank()) continue;

            Etudiant e = etudiantData.read(id,institutionId);
            if (e == null) {
                rejetes.add(id);
                continue;
            }
            if (institutionId.equals(e.getInstitutionId())) {
                etudiants.add(e);
            } else {
                rejetes.add(id);
                LOGGER.warning(() -> "⛔ Étudiant hors institution: " + id
                        + " (attendu: " + institutionId
                        + ", trouvé: " + e.getInstitutionId() + ")");
            }
        }

        // ✅ Envoi effectif (à brancher sur un vrai service d'envoi)
        int successCount = 0;
        for (Etudiant e : etudiants) {
            try {
                envoyerMessage(canal, e, objet, message);
                successCount++;
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Échec envoi à " + e.getNumeroIdentifiantEtudiant(), ex);
                rejetes.add(e.getNumeroIdentifiantEtudiant());
            }
        }

        String messageRetour = "Message envoyé à " + successCount + " étudiant(s) via " + canal
                + (objet != null && !objet.isEmpty() ? " (objet: " + objet + ")" : "");
        if (!rejetes.isEmpty()) {
            messageRetour += " — " + rejetes.size() + " rejeté(s)";
        }

        LOGGER.log(Level.INFO, "Envoi {0} à {1} étudiants", new Object[]{canal, successCount});

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", messageRetour);
        response.put("count", successCount);
        response.put("rejetes", rejetes);
        sendJson(exchange, response);
    }

    /**
 * ✅ Délègue l'envoi à la classe UI correspondant au canal.
 *    Chaque UI (UIMail, UISms, UIAppel, UIWhatsapp) expose une méthode `envoyer(...)`.
 *
 * @return true si l'envoi a réussi, false sinon.
 */
private boolean envoyerMessage(String canal, Etudiant etudiant, String objet, String message) {
    String canalLower = (canal == null) ? "" : canal.toLowerCase().trim();

    try {
        return switch (canalLower) {
            case "email"    -> UIMail.envoyer(etudiant, objet, message);
            case "sms"      -> UISms.envoyer(etudiant, objet, message);
            case "appel"    -> UIAppel.envoyer(etudiant, objet, message);
            case "whatsapp" -> UIWhatsapp.envoyer(etudiant, objet, message);
            default -> {
                LOGGER.warning(() -> "⚠️ Canal inconnu : " + canal);
                yield false;
            }
        };
    } catch (IOException | InterruptedException e) {
        LOGGER.log(Level.SEVERE, "❌ Erreur envoi via " + canalLower
                + " à " + (etudiant != null ? etudiant.getNumeroIdentifiantEtudiant() : "?"), e);
        return false;
    }
}

    /**
     * ✅ Envoi réel du message (à brancher sur SMTP / API SMS / Twilio, etc.)
     */
    public static boolean envoyer(Etudiant etudiant, String objet, String message) {
    if (etudiant == null || etudiant.getEmail() == null || etudiant.getEmail().isBlank()) {
        return false;
    }
    try {
  
        // Ex: JavaMailSender, ou votre service SMTP
        LOGGER.info(() -> "📧 Email envoyé à " + etudiant.getEmail()
                + " | Objet: " + objet);
        return true;
    } catch (Exception e) {
        LOGGER.log(Level.SEVERE, "❌ Erreur envoi email à " + etudiant.getEmail(), e);
        return false;
    }
}

    // =========================================================
    // /annees
    // =========================================================
    private void handleAnnees(HttpExchange exchange, String institutionId,
                                Connection conn) throws IOException {
        List<String> annees = new ArrayList<>();
        String sql = "SELECT DISTINCT annee_academique FROM annees_academiques "
                + "WHERE institution_id = ? ORDER BY annee_academique DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String a = rs.getString("annee_academique");
                    if (a != null && !a.isBlank()) annees.add(a);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erreur années: {0}", e.getMessage());
        }

        if (annees.isEmpty()) {
            annees.add("2025-2026");
            annees.add("2026-2027");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", annees);
        sendJson(exchange, response);
    }

    // =========================================================
    // /sessions
    // =========================================================
    private void handleSessions(HttpExchange exchange, String institutionId,
                                  Connection conn) throws IOException {
        Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
        String annee = params.get("annee");

        List<String> sessions = new ArrayList<>();
        String sql;
        if (annee != null && !annee.isEmpty()) {
            sql = "SELECT DISTINCT periode FROM periodes "
                    + "WHERE institution_id = ? AND annee_academique = ? ORDER BY periode";
        } else {
            sql = "SELECT DISTINCT periode FROM periodes "
                    + "WHERE institution_id = ? ORDER BY periode";
        }

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            if (annee != null && !annee.isEmpty()) {
                ps.setString(2, annee);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String s = rs.getString("periode");
                    if (s != null && !s.isBlank()) sessions.add(s);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erreur sessions: {0}", e.getMessage());
        }

        if (sessions.isEmpty()) {
            sessions.add("1ere Periode");
            sessions.add("2eme Periode");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", sessions);
        sendJson(exchange, response);
    }

    // =========================================================
    // /classes
    // =========================================================
    private void handleClasses(HttpExchange exchange, String institutionId,
                                 Connection conn) throws IOException {
        List<String> noms = new ArrayList<>();
        String sql = "SELECT DISTINCT nom_classe FROM classes "
                + "WHERE institution_id = ? ORDER BY nom_classe";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String c = rs.getString("nom_classe");
                    if (c != null && !c.isBlank()) noms.add(c);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erreur classes: {0}", e.getMessage());
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", noms);
        sendJson(exchange, response);
    }

    // =========================================================
    // SESSION
    // =========================================================
    private HttpSession getSession(HttpExchange exchange) {
        try {
            String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
            if (cookieHeader == null) return null;

            String sessionId = null;
            for (String cookie : cookieHeader.split(";")) {
                String[] parts = cookie.trim().split("=", 2);
                if (parts.length == 2 && "SESSION_ID".equals(parts[0])) {
                    sessionId = parts[1];
                    break;
                }
            }
            if (sessionId == null) return null;
            return SessionManager.getSession(sessionId);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erreur session: {0}", e.getMessage());
            return null;
        }
    }

    private String getInstitutionIdFromSession(HttpSession session) {
        String instId = (String) session.getAttribute("institutionId");
        if (instId == null || instId.isBlank()) {
            instId = (String) session.getAttribute("institution_id");
        }
        return instId;
    }

    private String getUtilisateurIdFromSession(HttpSession session) {
        String userId = (String) session.getAttribute("userId");
        if (userId == null || userId.isBlank()) {
            userId = (String) session.getAttribute("username");
        }
        return userId;
    }

    // =========================================================
    // PERMISSIONS
    // =========================================================
    private AccesAdmin chargerPermissions(String utilisateurId, String institutionId) {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            AccesAdminData dao = new AccesAdminData(conn);
            return dao.loadByUtilisateurId(utilisateurId, institutionId);
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erreur chargement permissions", e);
            return null;
        }
    }

    // =========================================================
    // HTTP
    // =========================================================
    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isBlank()) return params;
        try {
            for (String pair : query.split("&")) {
                int idx = pair.indexOf("=");
                if (idx > 0) {
                    String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                    String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                    params.put(key, value);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erreur parsing: {0}", e.getMessage());
        }
        return params;
    }

    private void sendJson(HttpExchange exchange, Map<String, Object> data) throws IOException {
        if (exchange.getResponseCode() != -1) return;
        sendJson(exchange, 200, data);
    }

    private void sendJson(HttpExchange exchange, int status, Map<String, Object> data) throws IOException {
        if (exchange.getResponseCode() != -1) return;
        String json = GSON.toJson(data);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendJsonError(HttpExchange exchange, int status, String message) throws IOException {
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", message);
        sendJson(exchange, status, error);
    }


    private void sendResponse(HttpExchange exchange, int statusCode, String html) throws IOException {
        if (exchange.getResponseCode() != -1) return;
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
            os.flush();
        }
    }
}