import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * Handler HTTP pour la gestion des sauvegardes.
 *
 * ✅ ISOLATION STRICTE PAR INSTITUTION :
 *   - L'institutionId est TOUJOURS lu depuis la session (jamais de la query/form)
 *   - Elle est vérifiée en BD contre la table d'appartenance utilisateur↔institution
 *   - Toutes les opérations (list, create, download, restore, delete, deleteData)
 *     passent institutionId au BackupService qui applique un second filtre
 *   - Une institution ne peut JAMAIS voir, télécharger, restaurer ou supprimer
 *     les sauvegardes d'une autre institution
 */
public class HandlerSauvegarde implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(HandlerSauvegarde.class.getName());
    private static final String BASE_PATH = "/admin/parametres/sauvegarde";

    private final BackupService backupService = new BackupService();
    private final Gson gson = new Gson();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        String query = exchange.getRequestURI().getQuery();

        // ============================================================
        // 1) AUTHENTIFICATION STRICTE
        // ============================================================
        HttpSession session = resolveSession(exchange);
        if (session == null) {
            LOGGER.warning("⛔ Session invalide → refusé");
            sendJsonError(exchange, 401, "Session invalide");
            return;
        }

        String role = readAttributeAsString(session, "role");
        boolean isAdmin = "ADMINISTRATEUR".equals(role) || "ADMIN".equals(role);
        if (!isAdmin) {
            LOGGER.warning(() -> "⛔ Rôle non admin (" + role + ") → refusé");
            sendJsonError(exchange, 403, "Accès refusé : rôle administrateur requis");
            return;
        }

        // ============================================================
        // 2) INSTITUTION SÉCURISÉE (session + vérif BD)
        // ============================================================
        String institutionId = getInstitutionSecurisee(session);
        if (institutionId == null) {
            LOGGER.warning("⛔ Institution non sécurisée → refusé");
            sendJsonError(exchange, 403,
                    "Institution introuvable ou invalide dans la session");
            return;
        }

        LOGGER.info(() -> "📥 " + method + " " + path
                + " | institution=" + institutionId);

        try {
            // ============================================================
            // PAGE HTML
            // ============================================================
            if ("GET".equals(method) && path.equals(BASE_PATH)) {
                ResponseUtil.sendHtml(exchange, 200,
                        UISauvegarde.rendrePage(institutionId, null));
                return;
            }

            // ============================================================
            // API : LIST (filtrée)
            // ============================================================
            if (path.endsWith("/api/list") && "GET".equals(method)) {
                handleList(exchange, institutionId);
                return;
            }

            // ============================================================
            // API : CREATE (institutionId de session obligatoire)
            // ============================================================
            if (path.endsWith("/api/create") && "POST".equals(method)) {
                handleCreate(exchange, institutionId);
                return;
            }

            // ============================================================
            // API : DOWNLOAD (contrôle d'appartenance)
            // ============================================================
            if (path.endsWith("/api/download") && "GET".equals(method)) {
                handleDownload(exchange, query, institutionId);
                return;
            }

            // ============================================================
            // API : DELETE fichier (contrôle d'appartenance)
            // ============================================================
            if (path.endsWith("/api/delete") && "DELETE".equals(method)) {
                handleDeleteFile(exchange, query, institutionId);
                return;
            }

            // ============================================================
            // API : RESTORE (contrôle d'appartenance)
            // ============================================================
            if (path.endsWith("/api/restore") && "POST".equals(method)) {
                handleRestore(exchange, query, institutionId);
                return;
            }

            // ============================================================
            // API : DELETE DATA (institutionId de session uniquement)
            // ============================================================
            if (path.endsWith("/api/deleteData") && "POST".equals(method)) {
                handleDeleteData(exchange, institutionId);
                return;
            }

            // ============================================================
            // 404
            // ============================================================
            ResponseUtil.sendError(exchange, 404, "Endpoint non trouvé");

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "❌ Erreur handler sauvegarde", e);
            sendJsonError(exchange, 500, "Erreur interne : " + e.getMessage());
        }
    }

    // ================================================================
    // LIST — Filtrée par institution
    // ================================================================
    private void handleList(HttpExchange exchange, String institutionId)
            throws IOException {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("institution", institutionId);
        // ✅ BackupService.listerSauvegardes applique le filtre
        result.put("data", backupService.listerSauvegardes(institutionId));
        sendJson(exchange, 200, result);
    }

    // ================================================================
    // CREATE — institutionId de session obligatoire
    // ================================================================
    private void handleCreate(HttpExchange exchange, String institutionId)
            throws IOException {
        LOGGER.info(() -> "📦 Création sauvegarde pour institution=" + institutionId);

        String nom = backupService.creerSauvegarde(institutionId);
        if (nom != null) {
            Map<String, Object> r = new HashMap<>();
            r.put("success", true);
            r.put("message", "Sauvegarde créée avec succès");
            r.put("filename", nom);
            r.put("institution", institutionId);
            sendJson(exchange, 200, r);
        } else {
            sendJsonError(exchange, 500, "Échec de la création de la sauvegarde");
        }
    }

    // ================================================================
    // DOWNLOAD — Contrôle d'appartenance strict
    // ================================================================
    private void handleDownload(HttpExchange exchange, String query,
                                  String institutionId) throws IOException {

        String filename = extractParam(query, "file");

        // ✅ Validation 1 : nom de fichier
        if (!nomFichierValide(filename)) {
            LOGGER.warning(() -> "⛔ Téléchargement refusé (nom invalide) : " + filename);
            sendJsonError(exchange, 400, "Nom de fichier invalide");
            return;
        }

        // ✅ Validation 2 : appartenance à l'institution
        if (!backupService.fichierAppartientAInstitution(filename, institutionId)) {
            LOGGER.warning(() -> "⛔ Tentative téléchargement fichier d'une autre institution : "
                    + filename + " | institution=" + institutionId);
            sendJsonError(exchange, 403, "Accès refusé à ce fichier");
            return;
        }

        // ✅ BackupService.getFichier refait le contrôle (double sécurité)
        Path f = backupService.getFichier(filename, institutionId);
        if (f == null) {
            sendJsonError(exchange, 404, "Fichier introuvable");
            return;
        }

        exchange.getResponseHeaders().set("Content-Type", "application/sql");
        exchange.getResponseHeaders().set("Content-Disposition",
                "attachment; filename=\"" + filename + "\"");
        exchange.sendResponseHeaders(200, Files.size(f));
        try (OutputStream os = exchange.getResponseBody()) {
            Files.copy(f, os);
        }
    }

    // ================================================================
    // RESTORE — Contrôle d'appartenance strict
    // ================================================================
    private void handleRestore(HttpExchange exchange, String query,
                                String institutionId) throws IOException {

        String filename = extractParam(query, "file");
        Map<String, Object> r = new HashMap<>();

        // ✅ Validation 1 : nom de fichier
        if (!nomFichierValide(filename)) {
            r.put("success", false);
            r.put("error", "Nom de fichier invalide");
            sendJson(exchange, 400, r);
            return;
        }

        // ✅ Validation 2 : appartenance à l'institution
        if (!backupService.fichierAppartientAInstitution(filename, institutionId)) {
            LOGGER.warning(() -> "⛔ Tentative restauration fichier d'une autre institution : "
                    + filename + " | institution=" + institutionId);
            r.put("success", false);
            r.put("error", "Accès refusé à ce fichier");
            sendJson(exchange, 403, r);
            return;
        }

        LOGGER.info(() -> "♻️ Restauration : " + filename
                + " | institution=" + institutionId);

        boolean ok = backupService.restaurerSauvegarde(filename, institutionId);
        r.put("success", ok);
        r.put("message", ok
                ? "Restauration réussie pour l'institution " + institutionId
                : "Échec de la restauration");
        r.put("institution", institutionId);
        sendJson(exchange, 200, r);
    }

    // ================================================================
    // DELETE FICHIER — Contrôle d'appartenance strict
    // ================================================================
    private void handleDeleteFile(HttpExchange exchange, String query,
                                   String institutionId) throws IOException {

        String filename = extractParam(query, "file");
        Map<String, Object> r = new HashMap<>();

        // ✅ Validation 1 : nom de fichier
        if (!nomFichierValide(filename)) {
            r.put("success", false);
            r.put("error", "Nom de fichier invalide");
            sendJson(exchange, 400, r);
            return;
        }

        // ✅ Validation 2 : appartenance à l'institution
        if (!backupService.fichierAppartientAInstitution(filename, institutionId)) {
            LOGGER.warning(() -> "⛔ Tentative suppression fichier d'une autre institution : "
                    + filename + " | institution=" + institutionId);
            r.put("success", false);
            r.put("error", "Accès refusé à ce fichier");
            sendJson(exchange, 403, r);
            return;
        }

        LOGGER.info(() -> "🗑️ Suppression fichier : " + filename
                + " | institution=" + institutionId);

        boolean ok = backupService.supprimerSauvegarde(filename, institutionId);
        r.put("success", ok);
        r.put("message", ok ? "Fichier supprimé" : "Échec de la suppression");
        r.put("institution", institutionId);
        sendJson(exchange, 200, r);
    }

    // ================================================================
    // DELETE DATA — institutionId de session uniquement
    // ================================================================
    private void handleDeleteData(HttpExchange exchange, String institutionId)
            throws IOException {

        // ✅ GARDE ABSOLUE : institutionId issu de la session, jamais de la query
        if (institutionId == null || institutionId.isBlank()) {
            LOGGER.warning("⛔ Suppression données refusée : institutionId null");
            sendJsonError(exchange, 403, "Institution non sécurisée");
            return;
        }

        LOGGER.info(() -> "🗑️ Suppression des données pour institution=" + institutionId);

        // ✅ BackupService.supprimerDonnees applique DELETE WHERE institution_id = ?
        boolean ok = backupService.supprimerDonnees(institutionId);

        Map<String, Object> r = new HashMap<>();
        r.put("success", ok);
        r.put("message", ok
                ? "Données supprimées pour l'institution " + institutionId
                : "Échec de la suppression des données");
        r.put("institution", institutionId);
        sendJson(exchange, 200, r);
    }

    // ================================================================
    // INSTITUTION SÉCURISÉE
    // ================================================================
    /**
     * ✅ Récupère l'institutionId de façon STRICTEMENT SÉCURISÉE :
     *    1) Lit institutionId depuis la session
     *    2) Lit utilisateurId depuis la session
     *    3) Vérifie en BD que le couple (utilisateur, institution) existe
     *    4) Retourne null si l'une des étapes échoue
     */
    private String getInstitutionSecurisee(HttpSession session) {
        if (session == null) return null;

        // 1) institutionId
        String institutionId = readAttributeAsString(session, "institutionId");
        if (institutionId == null) {
            institutionId = readAttributeAsString(session, "institution_id");
        }
        if (institutionId == null || institutionId.isBlank()) {
            LOGGER.warning("⛔ institutionId absent de la session");
            return null;
        }

        // 2) utilisateurId
        String utilisateurId = readAttributeAsString(session, "userId");
        if (utilisateurId == null) {
            utilisateurId = readAttributeAsString(session, "utilisateurId");
        }
        if (utilisateurId == null) {
            utilisateurId = readAttributeAsString(session, "username");
        }
        if (utilisateurId == null) {
            utilisateurId = readAttributeAsString(session, "email");
        }
        if (utilisateurId == null || utilisateurId.isBlank()) {
            LOGGER.warning("⛔ utilisateurId absent de la session");
            return null;
        }

        // 3) Vérification BD : appartenance utilisateur ↔ institution
        if (!utilisateurAppartientAInstitution(utilisateurId, institutionId)) {
            LOGGER.log(Level.WARNING, "\u26d4 Utilisateur {0} \u2209 institution {1}", new Object[]{utilisateurId, institutionId});
            return null;
        }

        return institutionId.trim();
    }

    /**
     * ✅ Vérifie en BD que le couple (utilisateur_id, institution_id) existe
     *    dans la table d'accès admin.
     */
    private boolean utilisateurAppartientAInstitution(String utilisateurId,
                                                        String institutionId) {
        if (utilisateurId == null || institutionId == null) return false;

        String sql = "SELECT COUNT(*) FROM acces_admin "
                   + "WHERE utilisateur_id = ? AND institution_id = ?";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, utilisateurId);
            ps.setString(2, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING,
                    "Erreur utilisateurAppartientAInstitution", e);
        }
        return false;
    }

    // ================================================================
    // VALIDATION NOM DE FICHIER
    // ================================================================
    private boolean nomFichierValide(String nom) {
        if (nom == null || nom.isBlank()) return false;
        if (nom.contains("..")) return false;
        if (nom.contains("/")) return false;
        if (nom.contains("\\")) return false;
        return nom.endsWith(".sql");
    }

    // ================================================================
    // EXTRACT PARAM (robuste)
    // ================================================================
    private String extractParam(String query, String key) {
        if (query == null || key == null) return null;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            String k = pair.substring(0, eq);
            if (k.equals(key)) {
                String v = pair.substring(eq + 1);
                return URLDecoder.decode(v, StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    // ================================================================
    // SESSION
    // ================================================================
    private HttpSession resolveSession(HttpExchange exchange) {
        String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookieHeader == null) return null;

        String sessionId = extractSessionId(cookieHeader);
        if (sessionId == null) return null;

        return SessionManager.getSession(sessionId);
    }

    private String extractSessionId(String cookieHeader) {
        for (String cookie : cookieHeader.split(";")) {
            String trimmed = cookie.trim();
            int eq = trimmed.indexOf('=');
            if (eq <= 0) continue;

            String name  = trimmed.substring(0, eq).trim();
            String value = trimmed.substring(eq + 1).trim();

            if ("SESSION_ID".equals(name) && !value.isEmpty()) {
                return value;
            }
        }
        return null;
    }

    private String readAttributeAsString(HttpSession session, String name) {
        Object value = session.getAttribute(name);
        if (value == null) return null;
        String s = value.toString();
        return s.isBlank() ? null : s;
    }

    // ================================================================
    // JSON HELPERS
    // ================================================================
    private void sendJson(HttpExchange exchange, int status,
                          Map<String, Object> data) throws IOException {
        String json = gson.toJson(data);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type",
                "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendJsonError(HttpExchange exchange, int status,
                                String message) throws IOException {
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", message);
        sendJson(exchange, status, error);
    }
}