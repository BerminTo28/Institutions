import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * Interface HTML de gestion des sauvegardes.
 *
 * ✅ ISOLATION STRICTE PAR INSTITUTION :
 *   - L'institutionId est TOUJOURS lu depuis la session (jamais du formulaire)
 *   - La liste affichée est filtrée par BackupService.listerSauvegardes(institutionId)
 *   - Chaque action (restaurer / supprimer fichier / supprimer données)
 *     récupère l'institutionId de la session et le passe au service
 *   - Le formulaire HTML ne contient AUCUN champ institutionId manipulable
 */
public class UIParametresSauvegarde implements HttpHandler {

    private static final Logger LOGGER =
            Logger.getLogger(UIParametresSauvegarde.class.getName());

    private static final String BASE_PATH = "/admin/parametres/sauvegarde";
    private static final SimpleDateFormat DATE_FMT =
            new SimpleDateFormat("dd/MM/yyyy HH:mm");

    private final BackupService backupService = new BackupService();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        String query = exchange.getRequestURI().getQuery();

        // ============================================================
        // 1) AUTHENTIFICATION
        // ============================================================
        HttpSession session = resolveSession(exchange);
        if (session == null) {
            LOGGER.warning("⛔ Session invalide → redirection");
            ResponseUtil.sendRedirect(exchange, "/admin/parametres?error=session");
            return;
        }

        String role = readAttributeAsString(session, "role");
        boolean isAdmin = "ADMINISTRATEUR".equals(role) || "ADMIN".equals(role);
        if (!isAdmin) {
            LOGGER.warning(() -> "⛔ Rôle non admin (" + role + ") → redirection");
            ResponseUtil.sendRedirect(exchange, "/admin/parametres?error=forbidden");
            return;
        }

        // ============================================================
        // 2) INSTITUTION SÉCURISÉE
        // ============================================================
        String institutionId = getInstitutionSecurisee(session);
        if (institutionId == null) {
            LOGGER.warning("⛔ Institution non sécurisée → redirection");
            ResponseUtil.sendRedirect(exchange, "/admin/parametres?error=institution");
            return;
        }

        try {
            // ========== GET — Téléchargement ==========
            if ("GET".equalsIgnoreCase(method)
                    && path.startsWith(BASE_PATH + "/api/download")) {
                handleDownload(exchange, query, institutionId);
                return;
            }

            // ========== GET — Liste JSON (filtrée) ==========
            if ("GET".equalsIgnoreCase(method)
                    && path.equals(BASE_PATH + "/api/list")) {
                handleListJson(exchange, institutionId);
                return;
            }

            // ========== POST — Créer / Restaurer / Supprimer ==========
            if ("POST".equalsIgnoreCase(method)) {
                if (path.equals(BASE_PATH + "/api/create")) {
                    handleCreate(exchange, institutionId);
                    return;
                }
                if (path.equals(BASE_PATH + "/api/restore")) {
                    handleRestore(exchange, query, institutionId);
                    return;
                }
                if (path.equals(BASE_PATH + "/api/deleteData")) {
                    handleDeleteData(exchange, institutionId);
                    return;
                }
                // POST depuis le formulaire HTML → on redirige
                handleFormPost(exchange, institutionId);
                return;
            }

            // ========== DELETE — Supprimer un fichier ==========
            if ("DELETE".equalsIgnoreCase(method)
                    && path.equals(BASE_PATH + "/api/delete")) {
                handleDeleteFile(exchange, query, institutionId);
                return;
            }

            // ========== GET — Page HTML ==========
            if ("GET".equalsIgnoreCase(method) && path.equals(BASE_PATH)) {
                afficherPage(exchange, institutionId);
                return;
            }

            ResponseUtil.sendError(exchange, 404, "Endpoint non trouvé");

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "❌ Erreur handler sauvegarde", e);
            try {
                ResponseUtil.sendError(exchange, 500, "Erreur interne : " + e.getMessage());
            } catch (IOException ignored) {}
        }
    }

    // ============================================================
    // GET — Page HTML principale (FILTRÉE)
    // ============================================================
    private void afficherPage(HttpExchange exchange, String institutionId)
            throws IOException {

        // ✅ UNIQUEMENT les sauvegardes de l'institution
        List<Map<String, Object>> sauvegardes =
                backupService.listerSauvegardes(institutionId);

        StringBuilder html = new StringBuilder();
        html.append("""
        <!DOCTYPE html>
        <html lang="fr">
        <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Sauvegarde</title>
        <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
        <style>
            * { margin:0; padding:0; box-sizing:border-box; font-family:'Segoe UI', sans-serif; }
            body { background:#f4f6f9; padding:20px; }
            .container { max-width:1100px; margin:0 auto; }
            .header { background:white; padding:20px 30px; border-radius:12px; display:flex; justify-content:space-between; align-items:center; margin-bottom:25px; }
            .header h1 { font-size:22px; color:#1e293b; }
            .header h1 i { color:#1e40af; margin-right:10px; }
            .header a { color:#1e40af; text-decoration:none; font-weight:600; }
            .header a:hover { text-decoration:underline; }
            .card { background:white; border-radius:12px; padding:25px; margin-bottom:20px; }
            .card h2 { font-size:18px; color:#1e293b; margin-bottom:15px; }
            .actions-row { display:flex; gap:12px; flex-wrap:wrap; margin-bottom:15px; }
            .btn { padding:10px 18px; border:none; border-radius:8px; cursor:pointer; font-weight:600; text-decoration:none; display:inline-flex; align-items:center; gap:8px; font-size:14px; }
            .btn-primary { background:#1e40af; color:white; }
            .btn-primary:hover { background:#1e3a8a; }
            .btn-danger { background:#dc2626; color:white; }
            .btn-danger:hover { background:#b91c1c; }
            .btn-secondary { background:#e2e8f0; color:#475569; }
            .btn-secondary:hover { background:#cbd5e1; }
            .btn-small { padding:6px 12px; font-size:13px; }
            table { width:100%; border-collapse:collapse; font-size:14px; }
            th, td { padding:12px 10px; text-align:left; border-bottom:1px solid #e2e8f0; }
            th { background:#f8fafc; font-weight:600; color:#475569; }
            .empty { padding:30px; color:#94a3b8; font-style:italic; text-align:center; }
            .footer { margin-top:30px; text-align:center; color:#94a3b8; font-size:13px; }
            .warning { background:#fef3c7; color:#92400e; padding:12px 15px; border-radius:8px; border-left:4px solid #f59e0b; margin-bottom:15px; }
            .info { background:#dbeafe; color:#1e40af; padding:10px 15px; border-radius:8px; margin-bottom:15px; font-size:13px; }
            .info i { margin-right:6px; }
            .form-inline { display:flex; gap:10px; align-items:center; flex-wrap:wrap; }
            .alert { padding:12px 15px; border-radius:8px; margin-bottom:15px; font-size:14px; }
            .alert-success { background:#dcfce7; color:#166534; border-left:4px solid #22c55e; }
            .alert-error { background:#fee2e2; color:#991b1b; border-left:4px solid #ef4444; }
        </style>
        </head>
        <body>
        <div class="container">
            <div class="header">
                <h1><i class="fas fa-database"></i> Gestion des sauvegardes</h1>
                <a href="/admin/parametres"><i class="fas fa-arrow-left"></i> Retour</a>
            </div>
        """);

        // ✅ Bandeau institution
        html.append("""
            <div class="info">
                <i class="fas fa-building"></i>
                Institution : <strong>""")
            .append(echapperHtml(institutionId))
            .append("""
                </strong> — Vous ne voyez et ne gérez que les sauvegardes de votre institution.
            </div>
        """);

        // ✅ Messages de succès / erreur depuis la query string
        String success = extractParam(exchange.getRequestURI().getQuery(), "success");
        String error   = extractParam(exchange.getRequestURI().getQuery(), "error");
        if (success != null) {
            html.append("<div class=\"alert alert-success\">")
                .append("<i class=\"fas fa-check-circle\"></i> ")
                .append(echapperHtml(messageSucces(success)))
                .append("</div>");
        }
        if (error != null) {
            html.append("<div class=\"alert alert-error\">")
                .append("<i class=\"fas fa-exclamation-circle\"></i> ")
                .append(echapperHtml(messageErreur(error)))
                .append("</div>");
        }

        // ============================================================
        // Carte : Créer une sauvegarde (AUCUN champ institutionId)
        // ============================================================
        html.append("""
            <div class="card">
                <h2><i class="fas fa-plus-circle"></i> Créer une sauvegarde</h2>
                <p style="color:#64748b; margin-bottom:15px; font-size:14px;">
                    Une sauvegarde complète des données de votre institution sera créée.
                </p>
                <form method="POST" action="/admin/parametres/sauvegarde" class="form-inline">
                    <input type="hidden" name="action" value="creer">
                    <button type="submit" class="btn btn-primary">
                        <i class="fas fa-save"></i> Créer la sauvegarde
                    </button>
                </form>
            </div>
        """);

        // ============================================================
        // Carte : Supprimer les données (AUCUN champ institutionId)
        // ============================================================
        html.append("""
            <div class="card">
                <h2><i class="fas fa-trash-alt"></i> Supprimer les données</h2>
                <div class="warning">
                    <i class="fas fa-exclamation-triangle"></i>
                    <strong>Attention :</strong> cette action est irréversible.
                    Seules les données de <strong>votre institution</strong> seront supprimées.
                    Créez une sauvegarde avant de continuer.
                </div>
                <form method="POST" action="/admin/parametres/sauvegarde" class="form-inline"
                      onsubmit="return confirm('Êtes-vous sûr de vouloir supprimer TOUTES les données de votre institution ? Cette action est irréversible.');">
                    <input type="hidden" name="action" value="supprimerDonnees">
                    <button type="submit" class="btn btn-danger">
                        <i class="fas fa-trash"></i> Supprimer mes données
                    </button>
                </form>
            </div>
        """);

        // ============================================================
        // Carte : Liste des sauvegardes (FILTRÉE)
        // ============================================================
        html.append("""
            <div class="card">
                <h2><i class="fas fa-list"></i> Sauvegardes disponibles</h2>
        """);

        if (sauvegardes.isEmpty()) {
            html.append("<p class=\"empty\">Aucune sauvegarde disponible pour votre institution.</p>");
        } else {
            html.append("<table>");
            html.append("<thead><tr><th>Nom</th><th>Date</th><th>Taille</th><th>Actions</th></tr></thead>");
            html.append("<tbody>");

            for (Map<String, Object> bf : sauvegardes) {
                String nom = (String) bf.get("name");
                String nomHtml = echapperHtml(nom);
                Long sizeObj = (Long) bf.get("size");
                Long lastModObj = (Long) bf.get("lastModified");

                long size = (sizeObj != null) ? sizeObj : 0L;
                long lastMod = (lastModObj != null) ? lastModObj : 0L;

                String tailleFormatee = formaterTaille(size);
                String dateFormatee = DATE_FMT.format(new Date(lastMod));

                html.append("<tr>");
                html.append("<td><strong>").append(nomHtml).append("</strong></td>");
                html.append("<td>").append(dateFormatee).append("</td>");
                html.append("<td>").append(tailleFormatee).append("</td>");
                html.append("<td>");

                // Télécharger (URL-encodée)
                html.append("<a href=\"/admin/parametres/sauvegarde/api/download?file=")
                    .append(encoderUrl(nom))
                    .append("\" class=\"btn btn-secondary btn-small\">")
                    .append("<i class=\"fas fa-download\"></i> Télécharger</a> ");

                // Restaurer (POST — AUCUN institutionId transmis, il vient de la session)
                html.append("<form method=\"POST\" action=\"/admin/parametres/sauvegarde\" ")
                    .append("style=\"display:inline;\" ")
                    .append("onsubmit=\"return confirm('Restaurer cette sauvegarde ? Les données actuelles de votre institution seront écrasées.');\">")
                    .append("<input type=\"hidden\" name=\"action\" value=\"restaurer\">")
                    .append("<input type=\"hidden\" name=\"nomFichier\" value=\"")
                    .append(nomHtml).append("\">")
                    .append("<button type=\"submit\" class=\"btn btn-primary btn-small\">")
                    .append("<i class=\"fas fa-undo\"></i> Restaurer</button></form> ");

                // Supprimer le fichier
                html.append("<form method=\"POST\" action=\"/admin/parametres/sauvegarde\" ")
                    .append("style=\"display:inline;\" ")
                    .append("onsubmit=\"return confirm('Supprimer ce fichier de sauvegarde ?');\">")
                    .append("<input type=\"hidden\" name=\"action\" value=\"supprimerFichier\">")
                    .append("<input type=\"hidden\" name=\"nomFichier\" value=\"")
                    .append(nomHtml).append("\">")
                    .append("<button type=\"submit\" class=\"btn btn-danger btn-small\">")
                    .append("<i class=\"fas fa-trash\"></i> Supprimer</button></form>");

                html.append("</td></tr>");
            }
            html.append("</tbody></table>");
        }

        html.append("""
            </div>

            <div class="footer">© M-TECH - Gestion des sauvegardes</div>
        </div>
        </body>
        </html>
        """);

        sendHtml(exchange, 200, html.toString());
    }

    // ============================================================
    // POST — Depuis le formulaire HTML
    // ============================================================
    private void handleFormPost(HttpExchange exchange, String institutionId)
            throws IOException {

        String body = new String(exchange.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8);
        Map<String, String> params = parseFormData(body);
        String action = params.get("action");

        LOGGER.info(() -> "📋 Action formulaire sauvegarde : " + action
                + " | institution=" + institutionId);

        switch (action == null ? "" : action) {

            // --------------------------------------------------------
            // CRÉER — institutionId de session obligatoire
            // --------------------------------------------------------
            case "creer" -> {
                String nom = backupService.creerSauvegarde(institutionId);
                String redirect = (nom != null)
                        ? BASE_PATH + "?success=create_success"
                        : BASE_PATH + "?error=create_failed";
                ResponseUtil.sendRedirect(exchange, redirect);
            }

            // --------------------------------------------------------
            // RESTAURER — vérifie appartenance avant restauration
            // --------------------------------------------------------
            case "restaurer" -> {
                String nomFichier = params.get("nomFichier");

                if (!nomFichierValide(nomFichier)) {
                    LOGGER.warning(() -> "⛔ nomFichier invalide (restaurer) : " + nomFichier);
                    ResponseUtil.sendRedirect(exchange, BASE_PATH + "?error=invalid_file");
                    return;
                }

                if (!backupService.fichierAppartientAInstitution(nomFichier, institutionId)) {
                    LOGGER.warning(() -> "⛔ Tentative restauration fichier autre institution : "
                            + nomFichier + " | institution=" + institutionId);
                    ResponseUtil.sendRedirect(exchange, BASE_PATH + "?error=access_denied");
                    return;
                }

                boolean ok = backupService.restaurerSauvegarde(nomFichier, institutionId);
                String redirect = ok
                        ? BASE_PATH + "?success=restore_success"
                        : BASE_PATH + "?error=restore_failed";
                ResponseUtil.sendRedirect(exchange, redirect);
            }

            // --------------------------------------------------------
            // SUPPRIMER FICHIER — vérifie appartenance avant suppression
            // --------------------------------------------------------
            case "supprimerFichier" -> {
                String nomFichier = params.get("nomFichier");

                if (!nomFichierValide(nomFichier)) {
                    LOGGER.warning(() -> "⛔ nomFichier invalide (supprimer) : " + nomFichier);
                    ResponseUtil.sendRedirect(exchange, BASE_PATH + "?error=invalid_file");
                    return;
                }

                if (!backupService.fichierAppartientAInstitution(nomFichier, institutionId)) {
                    LOGGER.warning(() -> "⛔ Tentative suppression fichier autre institution : "
                            + nomFichier + " | institution=" + institutionId);
                    ResponseUtil.sendRedirect(exchange, BASE_PATH + "?error=access_denied");
                    return;
                }

                boolean ok = backupService.supprimerSauvegarde(nomFichier, institutionId);
                String redirect = ok
                        ? BASE_PATH + "?success=delete_success"
                        : BASE_PATH + "?error=delete_failed";
                ResponseUtil.sendRedirect(exchange, redirect);
            }

            // --------------------------------------------------------
            // SUPPRIMER DONNÉES — institutionId de session uniquement
            // --------------------------------------------------------
            case "supprimerDonnees" -> {
                // ✅ Aucun institutionId lu du formulaire : il vient de la session
                boolean ok = backupService.supprimerDonnees(institutionId);
                String redirect = ok
                        ? BASE_PATH + "?success=data_deleted"
                        : BASE_PATH + "?error=data_delete_failed";
                ResponseUtil.sendRedirect(exchange, redirect);
            }

            default -> {
                LOGGER.warning(() -> "⛔ Action inconnue : " + action);
                ResponseUtil.sendRedirect(exchange, BASE_PATH + "?error=unknown_action");
            }
        }
    }

    // ============================================================
    // API — Liste JSON (filtrée)
    // ============================================================
    private void handleListJson(HttpExchange exchange, String institutionId)
            throws IOException {
        List<Map<String, Object>> backups =
                backupService.listerSauvegardes(institutionId);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("institution", institutionId);
        result.put("data", backups);
        sendJson(exchange, result);
    }

    // ============================================================
    // API — Créer (JSON)
    // ============================================================
    private void handleCreate(HttpExchange exchange, String institutionId)
            throws IOException {
        String nom = backupService.creerSauvegarde(institutionId);
        Map<String, Object> result = new HashMap<>();
        if (nom != null) {
            result.put("success", true);
            result.put("message", "Sauvegarde créée avec succès");
            result.put("filename", nom);
            result.put("institution", institutionId);
        } else {
            result.put("success", false);
            result.put("error", "Échec de la création de la sauvegarde");
        }
        sendJson(exchange, result);
    }

    // ============================================================
    // API — Télécharger (contrôle d'appartenance)
    // ============================================================
    private void handleDownload(HttpExchange exchange, String query,
                                  String institutionId) throws IOException {

        String filename = extractParam(query, "file");
        if (!nomFichierValide(filename)) {
            ResponseUtil.sendError(exchange, 400, "Nom de fichier invalide");
            return;
        }

        if (!backupService.fichierAppartientAInstitution(filename, institutionId)) {
            LOGGER.warning(() -> "⛔ Téléchargement refusé : " + filename
                    + " | institution=" + institutionId);
            ResponseUtil.sendError(exchange, 403, "Accès refusé à ce fichier");
            return;
        }

        java.nio.file.Path file = backupService.getFichier(filename, institutionId);
        if (file == null) {
            ResponseUtil.sendError(exchange, 404, "Fichier introuvable");
            return;
        }

        exchange.getResponseHeaders().set("Content-Type", "application/sql");
        exchange.getResponseHeaders().set("Content-Disposition",
                "attachment; filename=\"" + filename + "\"");
        exchange.sendResponseHeaders(200, java.nio.file.Files.size(file));

        try (OutputStream os = exchange.getResponseBody()) {
            java.nio.file.Files.copy(file, os);
        }
    }

    // ============================================================
    // API — Restaurer (contrôle d'appartenance)
    // ============================================================
    private void handleRestore(HttpExchange exchange, String query,
                                String institutionId) throws IOException {

        String filename = extractParam(query, "file");
        Map<String, Object> result = new HashMap<>();

        if (!nomFichierValide(filename)) {
            result.put("success", false);
            result.put("error", "Nom de fichier invalide");
            sendJson(exchange, result);
            return;
        }

        if (!backupService.fichierAppartientAInstitution(filename, institutionId)) {
            LOGGER.warning(() -> "⛔ Restauration refusée : " + filename
                    + " | institution=" + institutionId);
            result.put("success", false);
            result.put("error", "Accès refusé à ce fichier");
            sendJson(exchange, result);
            return;
        }

        boolean ok = backupService.restaurerSauvegarde(filename, institutionId);
        result.put("success", ok);
        result.put("message", ok
                ? "Restauration réussie pour l'institution " + institutionId
                : "Échec de la restauration");
        sendJson(exchange, result);
    }

    // ============================================================
    // API — Supprimer fichier (contrôle d'appartenance)
    // ============================================================
    private void handleDeleteFile(HttpExchange exchange, String query,
                                   String institutionId) throws IOException {

        String filename = extractParam(query, "file");
        Map<String, Object> result = new HashMap<>();

        if (!nomFichierValide(filename)) {
            result.put("success", false);
            result.put("error", "Nom de fichier invalide");
            sendJson(exchange, result);
            return;
        }

        if (!backupService.fichierAppartientAInstitution(filename, institutionId)) {
            LOGGER.warning(() -> "⛔ Suppression refusée : " + filename
                    + " | institution=" + institutionId);
            result.put("success", false);
            result.put("error", "Accès refusé à ce fichier");
            sendJson(exchange, result);
            return;
        }

        boolean ok = backupService.supprimerSauvegarde(filename, institutionId);
        result.put("success", ok);
        result.put("message", ok ? "Fichier supprimé" : "Échec de la suppression");
        sendJson(exchange, result);
    }

    // ============================================================
    // API — Supprimer données (institutionId de session uniquement)
    // ============================================================
    private void handleDeleteData(HttpExchange exchange, String institutionId)
            throws IOException {

        // ✅ GARDE ABSOLUE : institutionId issu de la session, jamais de la query
        if (institutionId == null || institutionId.isBlank()) {
            sendJsonError(exchange, "Institution non sécurisée");
            return;
        }

        LOGGER.info(() -> "🗑️ Suppression données API pour institution=" + institutionId);

        boolean ok = backupService.supprimerDonnees(institutionId);
        Map<String, Object> result = new HashMap<>();
        result.put("success", ok);
        result.put("message", ok
                ? "Données supprimées pour l'institution " + institutionId
                : "Échec de la suppression des données");
        sendJson(exchange, result);
    }

    // ============================================================
    // INSTITUTION SÉCURISÉE (depuis la session + vérif BD)
    // ============================================================
    private String getInstitutionSecurisee(HttpSession session) {
        if (session == null) return null;

        String institutionId = readAttributeAsString(session, "institutionId");
        if (institutionId == null) {
            institutionId = readAttributeAsString(session, "institution_id");
        }
        if (institutionId == null || institutionId.isBlank()) {
            return null;
        }

        String utilisateurId = readAttributeAsString(session, "userId");
        if (utilisateurId == null) {
            utilisateurId = readAttributeAsString(session, "username");
        }
        if (utilisateurId == null) {
            utilisateurId = readAttributeAsString(session, "email");
        }
        if (utilisateurId == null || utilisateurId.isBlank()) {
            return null;
        }

        if (!utilisateurAppartientAInstitution(utilisateurId, institutionId)) {
            LOGGER.log(Level.WARNING, "\u26d4 Utilisateur {0} \u2209 institution {1}", new Object[]{utilisateurId, institutionId});
            return null;
        }

        return institutionId.trim();
    }

    private boolean utilisateurAppartientAInstitution(String utilisateurId,
                                                        String institutionId) {
        if (utilisateurId == null || institutionId == null) return false;

        String sql = "SELECT COUNT(*) FROM acces_admin "
                   + "WHERE utilisateur_id = ? AND institution_id = ?";

        try (java.sql.Connection conn = DatabaseManager.getInstance().getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, utilisateurId);
            ps.setString(2, institutionId);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erreur utilisateurAppartientAInstitution", e);
        }
        return false;
    }

    // ============================================================
    // VALIDATION & HELPERS
    // ============================================================
    private boolean nomFichierValide(String nom) {
        if (nom == null || nom.isBlank()) return false;
        if (nom.contains("..")) return false;
        if (nom.contains("/")) return false;
        if (nom.contains("\\")) return false;
        return nom.endsWith(".sql");
    }

    private String formaterTaille(long taille) {
        if (taille < 1024) return taille + " B";
        if (taille < 1024 * 1024) return String.format("%.1f KB", taille / 1024.0);
        if (taille < 1024L * 1024 * 1024) return String.format("%.1f MB", taille / (1024.0 * 1024));
        return String.format("%.1f GB", taille / (1024.0 * 1024 * 1024));
    }

    private String extractParam(String query, String key) {
        if (query == null || key == null) return null;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            String k = pair.substring(0, eq);
            if (k.equals(key)) {
                String v = pair.substring(eq + 1);
                try {
                    return URLDecoder.decode(v, StandardCharsets.UTF_8.name());
                } catch (UnsupportedEncodingException e) {
                    return v;
                }
            }
        }
        return null;
    }

    private Map<String, String> parseFormData(String body) {
        Map<String, String> params = new HashMap<>();
        if (body == null || body.isBlank()) return params;
        for (String pair : body.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                try {
                    params.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8.name()),
                               URLDecoder.decode(kv[1], StandardCharsets.UTF_8.name()));
                } catch (UnsupportedEncodingException ignored) {}
            }
        }
        return params;
    }

    /** Encode un nom de fichier pour l'insérer dans une URL. */
    private String encoderUrl(String value) {
        try {
            return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            return value;
        }
    }

    private void sendJson(HttpExchange exchange, Map<String, Object> data)
            throws IOException {
        String json = new com.google.gson.Gson().toJson(data);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type",
                "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendJsonError(HttpExchange exchange, String message)
            throws IOException {
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", message);
        sendJson(exchange, error);
    }

    private void sendHtml(HttpExchange exchange, int status, String html)
            throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String echapperHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }

    // ============================================================
    // SESSION
    // ============================================================
    private HttpSession resolveSession(HttpExchange exchange) {
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
    }

    private String readAttributeAsString(HttpSession session, String name) {
        Object value = session.getAttribute(name);
        if (value == null) return null;
        String s = value.toString();
        return s.isBlank() ? null : s;
    }

    // ============================================================
    // MESSAGES SUCCÈS / ERREUR
    // ============================================================
    private String messageSucces(String code) {
        return switch (code) {
            case "create_success" -> "Sauvegarde créée avec succès.";
            case "restore_success" -> "Restauration effectuée avec succès.";
            case "delete_success" -> "Fichier de sauvegarde supprimé.";
            case "data_deleted" -> "Données de votre institution supprimées.";
            default -> "Opération réussie.";
        };
    }

    private String messageErreur(String code) {
        return switch (code) {
            case "session" -> "Session expirée, veuillez vous reconnecter.";
            case "forbidden" -> "Accès refusé : rôle administrateur requis.";
            case "institution" -> "Institution introuvable ou invalide.";
            case "create_failed" -> "Échec de la création de la sauvegarde.";
            case "restore_failed" -> "Échec de la restauration.";
            case "delete_failed" -> "Échec de la suppression du fichier.";
            case "data_delete_failed" -> "Échec de la suppression des données.";
            case "access_denied" -> "Accès refusé à ce fichier.";
            case "invalid_file" -> "Nom de fichier invalide.";
            case "unknown_action" -> "Action inconnue.";
            default -> "Une erreur est survenue.";
        };
    }
}