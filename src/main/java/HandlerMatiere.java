import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class HandlerMatiere implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(HandlerMatiere.class.getName());
    private static final int QUERY_TIMEOUT_SECONDS = 30;

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        LOGGER.info(() -> String.format("🚀 MATIERE HANDLER - %s %s",
                exchange.getRequestMethod(), exchange.getRequestURI()));

        cors(exchange);

        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        String institutionId = getInstitutionIdFromSession(exchange);
        String utilisateurId = getUtilisateurIdFromSession(exchange);

        if (institutionId == null || institutionId.isBlank()
                || utilisateurId == null || utilisateurId.isBlank()) {
            redirect(exchange, "/dashboard?error=" + encodeParam("Session invalide"));
            return;
        }

        AccesAdmin acces = chargerPermissions(utilisateurId, institutionId);
        if (acces == null) {
            redirect(exchange, "/dashboard?error=" + encodeParam("Permissions non trouvées"));
            return;
        }

        if ("POST".equals(exchange.getRequestMethod())) {
            handlePost(exchange, institutionId, acces);
            return;
        }

        handleGet(exchange, institutionId, acces);
    }

    // ============================================================
    // GET
    // ============================================================
    private void handleGet(HttpExchange exchange, String institutionId, AccesAdmin acces)
            throws IOException {
        try {
            Map<String, String> queryParams = parseQueryParams(exchange.getRequestURI().getQuery());
            String action = queryParams.get("action");

            // ---------- SUPPRESSION ----------
            if ("delete".equals(action)) {
                if (!acces.hasPermission(AccesAdmin.Module.MATIERE, AccesAdmin.Action.DELETE)) {
                    redirect(exchange, "/admin/matieres?error="
                            + encodeParam("Permission DELETE refusée"));
                    return;
                }
                String code = queryParams.get("code");
                if (code == null || code.isBlank()) {
                    redirect(exchange, "/admin/matieres?error=" + encodeParam("Code requis"));
                    return;
                }
                handleDelete(exchange, code, institutionId);
                return;
            }

            // ---------- LECTURE ----------
            if (!acces.hasPermission(AccesAdmin.Module.MATIERE, AccesAdmin.Action.READ)) {
                redirect(exchange, "/dashboard?error=" + encodeParam("Accès non autorisé"));
                return;
            }

            String anneeFiltre = queryParams.getOrDefault("annee", "");
            String periodeFiltre = queryParams.getOrDefault("periode", "");
            String classeFiltre = queryParams.getOrDefault("classe", "");
            String motCle = queryParams.getOrDefault("q", "");

            // ✅ Mode autonome : MatiereManager gère ses propres connexions
            MatiereManager matiereManager = new MatiereManager(institutionId);

            List<String> anneesDisponibles = getAnnees(institutionId);
            List<String> periodesDisponibles = getPeriodes(institutionId);
            List<String> classesDisponibles = getClasses(institutionId);

            List<Matiere> matieresFiltrees = matiereManager.rechercherAvecFiltres(
                    motCle, anneeFiltre, periodeFiltre, classeFiltre);

            boolean canCreate = acces.hasPermission(AccesAdmin.Module.MATIERE, AccesAdmin.Action.CREATE);
            boolean canUpdate = acces.hasPermission(AccesAdmin.Module.MATIERE, AccesAdmin.Action.UPDATE);
            boolean canDelete = acces.hasPermission(AccesAdmin.Module.MATIERE, AccesAdmin.Action.DELETE);

            // ✅ Ordre cohérent : annee, PERIODE, CLASSE
            String htmlResponse = UIMatiere.rendrePage(
                    matieresFiltrees, motCle, anneeFiltre, periodeFiltre, classeFiltre,
                    anneesDisponibles, periodesDisponibles, classesDisponibles, institutionId,
                    queryParams.get("success"), queryParams.get("error"),
                    canCreate, canUpdate, canDelete);

            sendHtml(exchange, 200, htmlResponse);

        } catch (IllegalArgumentException e) {
            LOGGER.warning(() -> "⚠️ Argument invalide : " + e.getMessage());
            sendError(exchange, 400, "Requête invalide: " + e.getMessage());
        } catch (IOException | SQLException e) {
            LOGGER.log(Level.SEVERE, "❌ Erreur GET /admin/matieres", e);
            sendError(exchange, 500, "Erreur interne: " + e.getMessage());
        }
    }

    // ============================================================
    // SUPPRESSION
    // ============================================================
    private void handleDelete(HttpExchange exchange, String code, String institutionId)
            throws IOException {
        try {
            // ✅ Mode autonome : MatiereData gère SA PROPRE transaction
            MatiereManager manager = new MatiereManager(institutionId);
            boolean ok = manager.supprimerMatiere(code);

            if (ok) {
                LOGGER.info(() -> "✅ Matière " + code + " supprimée");
                redirect(exchange, "/admin/matieres?success="
                        + encodeParam("Matière supprimée avec succès"));
            } else {
                redirect(exchange, "/admin/matieres?error="
                        + encodeParam("Échec de la suppression"));
            }

        } catch (IllegalArgumentException e) {
            LOGGER.warning(() -> "⚠️ " + e.getMessage());
            redirect(exchange, "/admin/matieres?error=" + encodeParam(e.getMessage()));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "❌ Erreur suppression matière " + code, e);
            redirect(exchange, "/admin/matieres?error="
                    + encodeParam(e.getMessage() != null ? e.getMessage() : "Erreur interne"));
        }
    }

    // ============================================================
    // POST (AJOUT / MODIFICATION)
    // ============================================================
   private void handlePost(HttpExchange exchange, String institutionId, AccesAdmin acces)
        throws IOException {
    Map<String, String> params = parseBody(exchange);
    String action = params.getOrDefault("action", "add");

    // Vérification des permissions selon l'action
    switch (action) {
        case "add" -> {
            if (!acces.hasPermission(AccesAdmin.Module.MATIERE, AccesAdmin.Action.CREATE)) {
                redirect(exchange, "/admin/matieres?error="
                        + encodeParam("Permission CREATE refusée"));
                return;
            }
        }
        case "update" -> {
            if (!acces.hasPermission(AccesAdmin.Module.MATIERE, AccesAdmin.Action.UPDATE)) {
                redirect(exchange, "/admin/matieres?error="
                        + encodeParam("Permission UPDATE refusée"));
                return;
            }
        }
        default -> {
            redirect(exchange, "/admin/matieres?error=" + encodeParam("Action inconnue"));
            return;
        }
    }

    // Récupération des champs
    String codeCours = params.get("codeCours");
    String nomMatiere = params.get("nomMatiere");
    String classeMatiere = "add".equals(action)
            ? params.get("classeMatiereSelect") : params.get("classeMatiere");
    String periode = "add".equals(action)
            ? params.get("periodeSelect") : params.get("periode");
    String anneeAcademique = "add".equals(action)
            ? params.get("anneeAcademiqueSelect") : params.get("anneeAcademique");
    String dureeCours = params.get("dureeCours");
    String statut = params.getOrDefault("statut", "ACTIF");

    double coefficient = parseDouble(params.get("coefficient"), 1.0);
    int nombreCredits = parseInt(params.get("nombreCredits"), 1);
    double notePassage = parseDouble(params.get("notePassage"), 50.0);
    double noteMaximale = parseDouble(params.get("noteMaximale"), 100.0);
    double noteMinimale = parseDouble(params.get("noteMinimale"), 0.0);

    // Validation des champs obligatoires
    if (nomMatiere == null || nomMatiere.isBlank()
            || classeMatiere == null || classeMatiere.isBlank()
            || periode == null || periode.isBlank()
            || anneeAcademique == null || anneeAcademique.isBlank()) {
        redirect(exchange, "/admin/matieres?error="
                + encodeParam("Champs obligatoires manquants"));
        return;
    }

    // Validation logique des barèmes
    if (noteMinimale >= noteMaximale
            || notePassage < noteMinimale
            || notePassage > noteMaximale) {
        redirect(exchange, "/admin/matieres?error="
                + encodeParam("Configuration du barème invalide"));
        return;
    }

    // ✅ Génération automatique du code pour la création
    if ("add".equals(action)) {
        codeCours = GenerateurCle.genererCodeCours(nomMatiere);
    } else if (codeCours == null || codeCours.isBlank()) {
        redirect(exchange, "/admin/matieres?error="
                + encodeParam("Code cours introuvable"));
        return;
    }

    // ✅ CRÉER UNE COPIE FINALE pour utilisation dans les lambdas
    final String codeCoursFinal = codeCours;

    // Vérification d'existence dans les tables de référence
    try {
        if (!existsInDb(MigrationManager.TABLE_CLASSES, "nom_classe",
                classeMatiere, institutionId)) {
            redirect(exchange, "/admin/matieres?error=" + encodeParam("Classe invalide"));
            return;
        }
        if (!existsInDb(MigrationManager.TABLE_ANNEES_ACADEMIQUES, "annee_academique",
                anneeAcademique, institutionId)) {
            redirect(exchange, "/admin/matieres?error="
                    + encodeParam("Année académique invalide"));
            return;
        }
        if (!existsInDb(MigrationManager.TABLE_PERIODES, "periode",
                periode, institutionId)) {
            redirect(exchange, "/admin/matieres?error=" + encodeParam("Période invalide"));
            return;
        }
    } catch (SQLException e) {
        LOGGER.log(Level.SEVERE, "❌ Erreur vérification références", e);
        redirect(exchange, "/admin/matieres?error="
                + encodeParam("Erreur interne de vérification"));
        return;
    }

    // ✅ Construction de l'objet Matiere (utilise codeCoursFinal)
    Matiere matiere = new Matiere(
            institutionId, nomMatiere, coefficient, notePassage,
            noteMaximale, noteMinimale, classeMatiere, nombreCredits,
            dureeCours, periode, statut, codeCoursFinal, anneeAcademique);

    // ============================================================
    // OPÉRATION — mode autonome
    // ============================================================
    try {
        MatiereManager matiereManager = new MatiereManager(institutionId);

        boolean success;
        if ("update".equals(action)) {
            success = matiereManager.mettreAJourMatiere(matiere);
        } else {
            success = matiereManager.ajouterMatiere(matiere);
        }

        if (success) {
            // ✅ Utilise codeCoursFinal (effectively final) dans la lambda
            LOGGER.info(() -> "✅ Matière " + codeCoursFinal + " enregistrée");
            redirect(exchange, "/admin/matieres?success="
                    + encodeParam("Opération réussie"));
        } else {
            LOGGER.warning("⚠️ Échec enregistrement matière");
            redirect(exchange, "/admin/matieres?error="
                    + encodeParam("Échec de l'enregistrement"));
        }

    } catch (IllegalArgumentException | IllegalStateException e) {
        LOGGER.warning(() -> "⚠️ Validation : " + e.getMessage());
        redirect(exchange, "/admin/matieres?error=" + encodeParam(e.getMessage()));
    } catch (Exception e) {
        LOGGER.log(Level.SEVERE, "❌ Erreur enregistrement matière", e);
        redirect(exchange, "/admin/matieres?error="
                + encodeParam(e.getMessage() != null ? e.getMessage() : "Erreur interne"));
    }
}
    // ============================================================
    // CHARGEMENT DES PERMISSIONS
    // ============================================================
    private AccesAdmin chargerPermissions(String utilisateurId, String institutionId) {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            AccesAdminData dao = new AccesAdminData(conn);
            AccesAdmin acces = dao.loadByUtilisateurId(utilisateurId, institutionId);
            if (acces == null) {
                LOGGER.warning(() -> "⚠️ Aucune permission trouvée pour " + utilisateurId);
                return new AccesAdmin();   // Vide mais non-null
            }
            return acces;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "❌ Erreur chargement permissions", e);
            return null;   // ✅ null → redirection vers dashboard
        }
    }

    // ============================================================
    // SESSION
    // ============================================================
    private String getInstitutionIdFromSession(HttpExchange exchange) {
        HttpSession session = getSession(exchange);
        if (session == null) return null;
        String instId = (String) session.getAttribute("institutionId");
        if (instId == null || instId.isBlank()) {
            instId = (String) session.getAttribute("institution_id");
        }
        return instId;
    }

    private String getUtilisateurIdFromSession(HttpExchange exchange) {
        HttpSession session = getSession(exchange);
        if (session == null) return null;
        String userId = (String) session.getAttribute("userId");
        if (userId == null || userId.isBlank()) {
            userId = (String) session.getAttribute("username");
        }
        return userId;
    }

    private HttpSession getSession(HttpExchange exchange) {
        try {
            String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
            if (cookieHeader == null || cookieHeader.isBlank()) return null;

            String sessionId = null;
            for (String cookie : cookieHeader.split(";")) {
                String[] parts = cookie.trim().split("=");
                if (parts.length == 2 && "SESSION_ID".equals(parts[0])) {
                    sessionId = parts[1];
                    break;
                }
            }
            if (sessionId == null || sessionId.isBlank()) return null;

            return SessionManager.getSession(sessionId);
        } catch (Exception e) {
            LOGGER.warning(() -> "Impossible de récupérer la session: " + e.getMessage());
            return null;
        }
    }

    // ============================================================
    // EXTRACTIONS SQL
    // ============================================================
    private List<String> getAnnees(String institutionId) throws SQLException {
        return getDistinctValues(MigrationManager.TABLE_ANNEES_ACADEMIQUES,
                "annee_academique", institutionId, "annee_academique DESC");
    }

    private List<String> getPeriodes(String institutionId) throws SQLException {
        return getDistinctValues(MigrationManager.TABLE_PERIODES,
                "periode", institutionId, "periode");
    }

    private List<String> getClasses(String institutionId) throws SQLException {
        return getDistinctValues(MigrationManager.TABLE_CLASSES,
                "nom_classe", institutionId, "nom_classe");
    }

    private List<String> getDistinctValues(String table, String column,
                                            String institutionId, String orderBy)
            throws SQLException {
        List<String> values = new ArrayList<>();
        String sql = "SELECT DISTINCT " + column + " FROM " + table
                + " WHERE institution_id = ? ORDER BY " + orderBy;
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setString(1, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String v = rs.getString(1);
                    if (v != null && !v.isBlank()) values.add(v);
                }
            }
        }
        return values;
    }

    private boolean existsInDb(String table, String column, String value,
                                String institutionId) throws SQLException {
        if (value == null || value.isBlank()) return false;
        String sql = "SELECT 1 FROM " + table
                + " WHERE institution_id = ? AND " + column + " = ? LIMIT 1";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setString(1, institutionId);
            ps.setString(2, value);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    // ============================================================
    // UTILITAIRES
    // ============================================================
    private void cors(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isBlank()) return params;
        for (String pair : query.split("&")) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                try {
                    params.put(
                        URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8));
                } catch (Exception ignored) {}
            }
        }
        return params;
    }

    private Map<String, String> parseBody(HttpExchange exchange) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

        // ✅ Support JSON minimal (fallback x-www-form-urlencoded)
        if (contentType != null && contentType.contains("application/json")) {
            return parseJson(body);
        }
        return parseQueryParams(body);
    }

    /**
     * ✅ Parsing JSON minimal (sans dépendance externe).
     *    Gère les paires "clé":"valeur" simples.
     */
    private Map<String, String> parseJson(String json) {
        Map<String, String> params = new HashMap<>();
        if (json == null || json.isBlank()) return params;

        // Nettoyer les accolades
        String clean = json.trim();
        if (clean.startsWith("{")) clean = clean.substring(1);
        if (clean.endsWith("}")) clean = clean.substring(0, clean.length() - 1);

        // Séparer les paires par virgules (basique, ne gère pas les virgules dans les valeurs)
        for (String pair : clean.split(",")) {
            int idx = pair.indexOf(":");
            if (idx > 0) {
                String key = pair.substring(0, idx).trim().replaceAll("^\"|\"$", "");
                String value = pair.substring(idx + 1).trim().replaceAll("^\"|\"$", "");
                params.put(key, value);
            }
        }
        return params;
    }

    private String encodeParam(String value) {
        if (value == null) return "";
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private double parseDouble(String val, double def) {
        try {
            return val != null ? Double.parseDouble(val.trim()) : def;
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private int parseInt(String val, int def) {
        try {
            return val != null ? Integer.parseInt(val.trim()) : def;
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private void sendHtml(HttpExchange exchange, int status, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendError(HttpExchange exchange, int status, String message) throws IOException {
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(302, -1);
    }
}