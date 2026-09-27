import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * Handler HTTP pour le module CLASSE.
 *
 * CORRECTIFS APPLIQUÉS :
 *  1. Gson configuré avec des TypeAdapter java.time (Java 17+ compatible).
 *  2. safeJson avec fallback : ne laisse JAMAIS le client sans réponse.
 *  3. Une seule connexion Hikari par requête.
 *  4. Réponse HTTP immédiate avant toute opération non essentielle.
 *  5. Aucune exception ne remonte au framework.
 *  6. Recalcul des stats en arrière-plan.
 */
public class HandlerClasse implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(HandlerClasse.class.getName());

    /** ✅ Gson configuré pour Java 17+ : java.time accessible. */
    private static final Gson GSON = buildGson();

    /** Executor dédié aux tâches de fond. */
    private static final ExecutorService BACKGROUND_EXECUTOR =
            Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "classe-bg-" + System.nanoTime());
                t.setDaemon(true);
                return t;
            });

    // ============================================================
    // CONSTRUCTION DE GSON
    // ============================================================
    private static Gson buildGson() {
        return new GsonBuilder()
                .registerTypeAdapter(LocalDate.class, new LocalDateAdapter())
                .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
                .registerTypeAdapter(LocalTime.class, new LocalTimeAdapter())
                .serializeNulls()
                .disableHtmlEscaping()
                .create();
    }

    private static class LocalDateAdapter
            implements JsonSerializer<LocalDate>, JsonDeserializer<LocalDate> {
        private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE;

        @Override
        public JsonElement serialize(LocalDate src, Type t, JsonSerializationContext ctx) {
            return src == null ? JsonNull.INSTANCE : new JsonPrimitive(src.format(FMT));
        }

        @Override
        public LocalDate deserialize(JsonElement json, Type t, JsonDeserializationContext ctx) {
            return json == null || json.isJsonNull() ? null
                    : LocalDate.parse(json.getAsString(), FMT);
        }
    }

    private static class LocalDateTimeAdapter
            implements JsonSerializer<LocalDateTime>, JsonDeserializer<LocalDateTime> {
        private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

        @Override
        public JsonElement serialize(LocalDateTime src, Type t, JsonSerializationContext ctx) {
            return src == null ? JsonNull.INSTANCE : new JsonPrimitive(src.format(FMT));
        }

        @Override
        public LocalDateTime deserialize(JsonElement json, Type t, JsonDeserializationContext ctx) {
            return json == null || json.isJsonNull() ? null
                    : LocalDateTime.parse(json.getAsString(), FMT);
        }
    }

    private static class LocalTimeAdapter
            implements JsonSerializer<LocalTime>, JsonDeserializer<LocalTime> {
        private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_TIME;

        @Override
        public JsonElement serialize(LocalTime src, Type t, JsonSerializationContext ctx) {
            return src == null ? JsonNull.INSTANCE : new JsonPrimitive(src.format(FMT));
        }

        @Override
        public LocalTime deserialize(JsonElement json, Type t, JsonDeserializationContext ctx) {
            return json == null || json.isJsonNull() ? null
                    : LocalTime.parse(json.getAsString(), FMT);
        }
    }

    // ============================================================
    // POINT D'ENTRÉE
    // ============================================================
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        final String reqId = UUID.randomUUID().toString().substring(0, 8);
        final long t0 = System.currentTimeMillis();

        try {
            try { cors(exchange); } catch (Exception ignored) {}

            String method = exchange.getRequestMethod();
            if ("OPTIONS".equalsIgnoreCase(method)) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            // 1) Session
            String institutionId = getInstitutionIdDynamique(exchange);
            String utilisateurId = getUtilisateurIdDynamique(exchange);

            if (isBlank(institutionId) || isBlank(utilisateurId)) {
                LOGGER.warning(() -> "[" + reqId + "] ⚠️ Session expirée");
                safeRedirect(exchange, "/login?error="
                        + encodeParam("Session expirée. Veuillez vous reconnecter."));
                return;
            }

            // 2) Permissions
            AccesAdmin acces = chargerPermissions(utilisateurId, institutionId);
            if (acces == null
                    || !acces.hasPermission(AccesAdmin.Module.CLASSE, AccesAdmin.Action.READ)) {
                safeRedirect(exchange, "/dashboard?error="
                        + encodeParam("Accès non autorisé au module CLASSE"));
                return;
            }

            String path = exchange.getRequestURI().getPath();

            // 3) API JSON
            if (path != null && path.startsWith("/admin/classes/api/")) {
                handleApi(exchange, path, institutionId, reqId);
                return;
            }

            // 4) POST
            if ("POST".equalsIgnoreCase(method)) {
                handlePost(exchange, institutionId, acces, reqId, t0);
                return;
            }

            // 5) GET
            if ("GET".equalsIgnoreCase(method)) {
                handleGet(exchange, institutionId, acces, reqId);
                return;
            }

            safeError(exchange, 405, "Méthode non autorisée");

        } catch (IOException t) {
            LOGGER.log(Level.SEVERE,
                    "[" + reqId + "] ❌ Exception non catchée dans handle()", t);
            try { safeError(exchange, 500, "Erreur interne du serveur"); }
            catch (Throwable ignored) {}
        } finally {
            try { exchange.close(); } catch (Exception ignored) {}
        }
    }

    // ============================================================
    // API
    // ============================================================
    private void handleApi(HttpExchange exchange, String path,
                            String institutionId, String reqId) {
        if ("/admin/classes/api/filtres".equals(path)) {
            try (Connection conn = DatabaseManager.getInstance().getConnection()) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("annees", getAnnees(conn, institutionId));
                result.put("periodes", getPeriodes(conn, institutionId));
                result.put("promotions", getPromotions(conn, institutionId));
                result.put("statsClasses", StatsCache.getStatsEtudiants(institutionId));
                safeJson(exchange, result);
            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "[" + reqId + "] ❌ Erreur API Filtres", e);
                safeJsonError(exchange, "Erreur base de données");
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "[" + reqId + "] ❌ Erreur inattendue API Filtres", e);
                safeJsonError(exchange, "Erreur interne");
            }
            return;
        }
        safeJsonError(exchange, "API inconnue");
    }

    // ============================================================
    // GET
    // ============================================================
    private void handleGet(HttpExchange exchange, String institutionId,
                            AccesAdmin acces, String reqId) {
        Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());

        if ("delete".equals(params.get("action"))) {
            if (!acces.hasPermission(AccesAdmin.Module.CLASSE, AccesAdmin.Action.DELETE)) {
                safeRedirect(exchange, "/admin/classes?error="
                        + encodeParam("Suppression non autorisée"));
                return;
            }
            handleDelete(exchange, params.get("code"), institutionId, false, reqId);
            return;
        }

        boolean isAjax = "1".equals(params.get("ajax"));

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {

            if ("1".equals(params.get("refreshStats"))) {
                mettreAJourTousLesNombresEtudiantsAvecConn(conn, institutionId);
                StatsCache.invalidate(institutionId);
                LOGGER.fine(() -> "[" + reqId + "] 📊 Stats rafraîchies à la demande");
            }

            String motCle = params.getOrDefault("q", "").trim();
            String anneeFiltre = params.getOrDefault("annee", "").trim();
            String periodeFiltre = params.getOrDefault("periode", "").trim();
            String nomClasseFiltre = params.getOrDefault("nomClasseFiltre", "").trim();

            ClasseData classeData = new ClasseData(conn);
            List<Classe> classes = classeData.obtenirClassesFiltrees(
                    institutionId, motCle, anneeFiltre, periodeFiltre, nomClasseFiltre);

            if (isAjax) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("classes", classes);
                result.put("stats", StatsCache.getStatsEtudiants(institutionId));
                safeJson(exchange, result);
                return;
            }

            List<String> annees = getAnnees(conn, institutionId);
            List<String> periodes = getPeriodes(conn, institutionId);
            List<String> promotions = getPromotions(conn, institutionId);

            boolean canCreate = acces.hasPermission(AccesAdmin.Module.CLASSE, AccesAdmin.Action.CREATE);
            boolean canUpdate = acces.hasPermission(AccesAdmin.Module.CLASSE, AccesAdmin.Action.UPDATE);
            boolean canDelete = acces.hasPermission(AccesAdmin.Module.CLASSE, AccesAdmin.Action.DELETE);

            List<Classe> toutesClasses = classeData.toutLister(institutionId);
            List<String> nomsClasses = getNomsClasses(toutesClasses);

            String html = UIClasses.rendrePage(
                    classes, motCle, anneeFiltre, periodeFiltre, nomClasseFiltre,
                    annees, periodes, promotions, nomsClasses, institutionId,
                    params.get("success"), params.get("error"),
                    canCreate, canUpdate, canDelete);
            safeHtml(exchange, 200, html);

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "[" + reqId + "] ❌ Erreur GET /admin/classes", e);
            if (isAjax) safeJsonError(exchange, "Erreur base de données");
            else safeError(exchange, 500, "Erreur interne du serveur");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[" + reqId + "] ❌ Erreur inattendue GET /admin/classes", e);
            if (isAjax) safeJsonError(exchange, "Erreur interne");
            else safeError(exchange, 500, "Erreur interne du serveur");
        }
    }

    // ============================================================
    // POST — UNE SEULE CONNEXION, RÉPONSE IMMÉDIATE
    // ============================================================
    private void handlePost(HttpExchange exchange, String institutionId,
                             AccesAdmin acces, String reqId, long t0) {

        Map<String, String> params;
        try {
            params = parseBody(exchange);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "[" + reqId + "] ⚠️ Body illisible", e);
            safeJsonError(exchange, "Corps de requête illisible");
            return;
        }

        boolean isAjax = "1".equals(params.get("ajax"));
        String action = params.getOrDefault("action", "add");

        // ---------- DELETE ----------
        if ("delete".equalsIgnoreCase(action)) {
            if (!acces.hasPermission(AccesAdmin.Module.CLASSE, AccesAdmin.Action.DELETE)) {
                safeRespondError(exchange, isAjax,
                        "Suppression non autorisée", "/admin/classes");
                return;
            }
            String code = params.get("codeClasse");
            if (isBlank(code)) code = params.get("code");
            handleDelete(exchange, code, institutionId, isAjax, reqId);
            return;
        }

        // ---------- ADD / UPDATE ----------
        String code = params.get("codeClasse");
        String nom = params.get("nomClasse");
        String periode = params.get("periode");
        String annee = params.get("anneeAcademique");
        String promotion = params.get("promotion");
        int niveau = parseInt(params.get("niveauClasse"), 1);

        if ("add".equalsIgnoreCase(action)
                && !acces.hasPermission(AccesAdmin.Module.CLASSE, AccesAdmin.Action.CREATE)) {
            safeRespondError(exchange, isAjax,
                    "Création non autorisée", "/admin/classes");
            return;
        }
        if ("update".equalsIgnoreCase(action)
                && !acces.hasPermission(AccesAdmin.Module.CLASSE, AccesAdmin.Action.UPDATE)) {
            safeRespondError(exchange, isAjax,
                    "Modification non autorisée", "/admin/classes");
            return;
        }

        if ("add".equalsIgnoreCase(action) && isBlank(code)) {
            code = GenerateurCle.genererCodeClasse(nom, niveau, annee);
            params.put("codeClasse", code);
        }

        final String codeFinal = code;

        if (isInvalid(codeFinal, nom, periode, annee, promotion)) {
            safeRespondError(exchange, isAjax,
                    "Tous les champs obligatoires (y compris Promotion, Période et Année Académique) doivent être renseignés",
                    "/admin/classes");
            return;
        }

        String operationError = null;

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {

            if (!existsInDb(conn, MigrationManager.TABLE_ANNEES_ACADEMIQUES,
                    "annee_academique", annee, institutionId)) {
                safeRespondError(exchange, isAjax,
                        "L'année académique '" + annee + "' n'existe pas.", "/admin/classes");
                return;
            }
            if (!existsInDb(conn, MigrationManager.TABLE_PERIODES,
                    "periode", periode, institutionId)) {
                safeRespondError(exchange, isAjax,
                        "La période '" + periode + "' n'existe pas.", "/admin/classes");
                return;
            }
            if (!existsInDb(conn, MigrationManager.TABLE_PROMOTIONS,
                    "promotion", promotion, institutionId)) {
                safeRespondError(exchange, isAjax,
                        "La promotion '" + promotion + "' n'existe pas.", "/admin/classes");
                return;
            }

            Classe classe = buildClasse(params, institutionId, promotion);
            ClasseData classeData = new ClasseData(conn);

            boolean operationOk = "update".equalsIgnoreCase(action)
                    ? classeData.modifier(classe)
                    : classeData.ajouter(classe);

            if (!operationOk) {
                operationError = "L'opération a échoué en base de données";
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "[" + reqId + "] ❌ SQLException handlePost", e);
            operationError = "Erreur base de données";
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[" + reqId + "] ❌ Exception inattendue handlePost", e);
            operationError = "Erreur interne";
        }

        if (operationError != null) {
            safeRespondError(exchange, isAjax, operationError, "/admin/classes");
            return;
        }

        final long elapsed = System.currentTimeMillis() - t0;
        LOGGER.info(() -> "[" + reqId + "] ✅ Classe " + codeFinal
                + " enregistrée en " + elapsed + " ms");

        if (isAjax) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Classe enregistrée avec succès");
            result.put("codeClasse", codeFinal);
            safeJson(exchange, result);
        } else {
            safeRedirect(exchange, "/admin/classes?success="
                    + encodeParam("Classe enregistrée avec succès"));
        }

        scheduleStatsRecalc(institutionId, reqId);
    }

    // ============================================================
    // DELETE
    // ============================================================
    private void handleDelete(HttpExchange exchange, String code, String institutionId,
                               boolean isAjax, String reqId) {
        if (isBlank(code)) {
            safeRespondError(exchange, isAjax,
                    "Code classe requis pour la suppression", "/admin/classes");
            return;
        }

        String operationError = null;

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            ClasseData classeData = new ClasseData(conn);
            boolean ok = classeData.supprimer(code, institutionId);
            if (!ok) {
                operationError = "Classe introuvable ou déjà supprimée";
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE,
                    "[" + reqId + "] ❌ SQLException handleDelete " + code, e);
            operationError = "Erreur base de données";
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE,
                    "[" + reqId + "] ❌ Exception inattendue handleDelete " + code, e);
            operationError = "Erreur interne";
        }

        if (operationError != null) {
            safeRespondError(exchange, isAjax, operationError, "/admin/classes");
            return;
        }

        LOGGER.info(() -> "[" + reqId + "] ✅ Classe " + code + " supprimée");

        if (isAjax) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Classe " + code + " supprimée avec succès");
            safeJson(exchange, result);
        } else {
            safeRedirect(exchange, "/admin/classes?success="
                    + encodeParam("Classe " + code + " supprimée avec succès"));
        }

        scheduleStatsRecalc(institutionId, reqId);
    }

    // ============================================================
    // RECALCUL ASYNCHRONE
    // ============================================================
    private void scheduleStatsRecalc(String institutionId, String reqId) {
        try {
            BACKGROUND_EXECUTOR.submit(() -> {
                try {
                    mettreAJourTousLesNombresEtudiants(institutionId);
                    StatsCache.invalidate(institutionId);
                    LOGGER.fine(() -> "[" + reqId + "] 📊 Stats recalculées");
                } catch (Throwable t) {
                    LOGGER.log(Level.WARNING,
                            "[" + reqId + "] ⚠️ Échec recalcul stats", t);
                }
            });
        } catch (RejectedExecutionException e) {
            LOGGER.log(Level.WARNING,
                    "[" + reqId + "] ⚠️ Executor saturé, recalcul ignoré", e);
        }
    }

    private void mettreAJourTousLesNombresEtudiants(String institutionId) {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            mettreAJourTousLesNombresEtudiantsAvecConn(conn, institutionId);
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING,
                    "⚠️ Impossible de mettre à jour les compteurs étudiants", e);
        }
    }

    private void mettreAJourTousLesNombresEtudiantsAvecConn(Connection conn,
                                                             String institutionId)
            throws SQLException {
        String sql = """
            UPDATE classes c
            SET c.nombre_etudiants = (
                SELECT COUNT(*)
                FROM etudiants e
                WHERE e.institution_id = c.institution_id
                    AND e.classe = c.nom_classe
                    AND e.annee_academique = c.annee_academique
                    AND e.periode = c.periode
            )
            WHERE c.institution_id = ?
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            int updated = ps.executeUpdate();
            if (updated > 0) {
                final int u = updated;
                LOGGER.fine(() -> "📊 Mise à jour de " + u + " classes");
            }
        }
    }

    // ============================================================
    // SESSION
    // ============================================================
    private String getInstitutionIdDynamique(HttpExchange exchange) {
        try {
            String sessionId = extractSessionId(exchange);
            if (sessionId == null) return null;
            HttpSession session = SessionManager.getSession(sessionId);
            if (session == null) return null;
            String instId = (String) session.getAttribute("institutionId");
            if (isBlank(instId)) instId = (String) session.getAttribute("institution_id");
            if (isBlank(instId)) instId = (String) session.getAttribute("INSTITUTION_ID");
            return instId;
        } catch (Exception e) {
            LOGGER.warning(() -> "⚠️ Erreur lecture session : " + e.getMessage());
            return null;
        }
    }

    private String getUtilisateurIdDynamique(HttpExchange exchange) {
        try {
            String sessionId = extractSessionId(exchange);
            if (sessionId == null) return null;
            HttpSession session = SessionManager.getSession(sessionId);
            if (session == null) return null;
            String userId = (String) session.getAttribute("userId");
            if (isBlank(userId)) userId = (String) session.getAttribute("username");
            return userId;
        } catch (Exception e) {
            LOGGER.warning(() -> "⚠️ Erreur lecture utilisateur : " + e.getMessage());
            return null;
        }
    }

    private String extractSessionId(HttpExchange exchange) {
        String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
        if (isBlank(cookieHeader)) return null;
        for (String cookie : cookieHeader.split(";")) {
            String[] parts = cookie.trim().split("=", 2);
            if (parts.length == 2 && "SESSION_ID".equalsIgnoreCase(parts[0])) {
                return parts[1];
            }
        }
        return null;
    }

    private AccesAdmin chargerPermissions(String utilisateurId, String institutionId) {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            AccesAdminData dao = new AccesAdminData(conn);
            return dao.loadByUtilisateurId(utilisateurId, institutionId);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erreur chargement permissions", e);
            return new AccesAdmin();
        }
    }

    // ============================================================
    // REQUÊTES BDD
    // ============================================================
    private List<String> getAnnees(Connection conn, String institutionId) throws SQLException {
        return getDistinctValues(conn, MigrationManager.TABLE_ANNEES_ACADEMIQUES,
                "annee_academique", institutionId, "annee_academique DESC");
    }

    private List<String> getPeriodes(Connection conn, String institutionId) throws SQLException {
        return getDistinctValues(conn, MigrationManager.TABLE_PERIODES,
                "periode", institutionId, "periode ASC");
    }

    private List<String> getPromotions(Connection conn, String institutionId) throws SQLException {
        return getDistinctValues(conn, MigrationManager.TABLE_PROMOTIONS,
                "promotion", institutionId, "promotion ASC");
    }

    private List<String> getDistinctValues(Connection conn, String table, String column,
                                             String institutionId, String orderBy)
            throws SQLException {
        List<String> values = new ArrayList<>();
        String sql = "SELECT DISTINCT " + column + " FROM " + table
                + " WHERE institution_id = ? ORDER BY " + orderBy;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String val = rs.getString(1);
                    if (val != null && !val.isBlank()) values.add(val);
                }
            }
        }
        return values;
    }

    private boolean existsInDb(Connection conn, String table, String column,
                                 String value, String institutionId) throws SQLException {
        String sql = "SELECT 1 FROM " + table
                + " WHERE institution_id = ? AND " + column + " = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            ps.setString(2, value);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private List<String> getNomsClasses(List<Classe> classes) {
        if (classes == null) return List.of();
        return classes.stream()
                .map(Classe::getNomClasse)
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    // ============================================================
    // UTILITAIRES
    // ============================================================
    private Classe buildClasse(Map<String, String> params, String institutionId,
                                 String promotion) {
        Classe c = new Classe();
        c.setInstitutionId(institutionId);
        c.setCodeClasse(params.get("codeClasse"));
        c.setNomClasse(params.get("nomClasse"));
        c.setPeriode(params.get("periode"));
        c.setAnneeAcademique(params.get("anneeAcademique"));
        c.setNiveauClasse(parseInt(params.get("niveauClasse"), 1));
        c.setCapaciteClasse(parseInt(params.get("capaciteClasse"), 30));
        c.setNombreEtudiants(0);
        c.setMoyennePassage(parseDouble(params.get("moyennePassage"), 10.0));
        c.setStatut("ACTIF");
        c.setPromotion(promotion);
        return c;
    }

    private boolean isInvalid(String... values) {
        for (String v : values) {
            if (isBlank(v)) return true;
        }
        return false;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        if (isBlank(query)) return params;
        try {
            for (String pair : query.split("&")) {
                int idx = pair.indexOf("=");
                if (idx > 0) {
                    params.put(
                            URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8),
                            URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8));
                }
            }
        } catch (Exception e) {
            LOGGER.warning(() -> "⚠️ Erreur parsing query : " + e.getMessage());
        }
        return params;
    }

    private Map<String, String> parseBody(HttpExchange exchange) throws IOException {
        byte[] bytes = exchange.getRequestBody().readAllBytes();
        String body = new String(bytes, StandardCharsets.UTF_8);
        return parseQuery(body);
    }

    private String encodeParam(String value) {
        if (value == null) return "";
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private int parseInt(String val, int def) {
        try { return val != null ? Integer.parseInt(val.trim()) : def; }
        catch (NumberFormatException e) { return def; }
    }

    private double parseDouble(String val, double def) {
        try { return val != null ? Double.parseDouble(val.trim()) : def; }
        catch (NumberFormatException e) { return def; }
    }

    // ============================================================
    // HTTP RESPONSES SÛRES
    // ============================================================
    private void cors(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    private void safeHtml(HttpExchange exchange, int status, String html) {
        if (html == null) html = "";
        try {
            if (exchange.getResponseCode() != -1) return;
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "⚠️ safeHtml : envoi impossible", e);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "❌ safeHtml : erreur inattendue", e);
        }
    }

    /**
     * Envoie un JSON. Ne laisse JAMAIS le client sans réponse.
     * En cas d'échec de sérialisation, un JSON de secours est envoyé.
     */
    private void safeJson(HttpExchange exchange, Map<String, Object> data) {
        if (data == null) data = new HashMap<>();
        try {
            if (exchange.getResponseCode() != -1) return;

            byte[] bytes;
            try {
                bytes = GSON.toJson(data).getBytes(StandardCharsets.UTF_8);
            } catch (Throwable gsonError) {
                // ✅ Fallback : ne JAMAIS laisser le client sans réponse
                LOGGER.log(Level.SEVERE,
                        "❌ Échec sérialisation Gson — envoi d'un JSON de secours", gsonError);

                Map<String, Object> fallback = new HashMap<>();
                fallback.put("success", Boolean.TRUE.equals(data.get("success")));
                fallback.put("error", "Erreur de sérialisation côté serveur");

                if (data.containsKey("classes")) {
                    fallback.put("classes", java.util.Collections.emptyList());
                }
                if (data.containsKey("stats")) {
                    fallback.put("stats", java.util.Collections.emptyMap());
                }

                try {
                    bytes = GSON.toJson(fallback).getBytes(StandardCharsets.UTF_8);
                } catch (Throwable fatal) {
                    bytes = "{\"success\":false,\"error\":\"Erreur serveur\"}"
                            .getBytes(StandardCharsets.UTF_8);
                }
            }

            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "⚠️ safeJson : envoi impossible", e);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "❌ safeJson : erreur inattendue", e);
        }
    }

    private void safeJsonError(HttpExchange exchange, String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", message != null ? message : "Erreur inconnue");
        safeJson(exchange, error);
    }

    private void safeError(HttpExchange exchange, int status, String message) {
        if (message == null) message = "";
        try {
            if (exchange.getResponseCode() != -1) return;
            byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "⚠️ safeError : envoi impossible", e);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "❌ safeError : erreur inattendue", e);
        }
    }

    private void safeRedirect(HttpExchange exchange, String location) {
        if (location == null) location = "/admin/classes";
        try {
            if (exchange.getResponseCode() != -1) return;
            exchange.getResponseHeaders().set("Location", location);
            exchange.sendResponseHeaders(302, -1);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "⚠️ safeRedirect : envoi impossible", e);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "❌ safeRedirect : erreur inattendue", e);
        }
    }

    private void safeRespondError(HttpExchange exchange, boolean isAjax,
                                    String message, String redirectBase) {
        if (isAjax) {
            safeJsonError(exchange, message);
        } else {
            safeRedirect(exchange, redirectBase + "?error=" + encodeParam(message));
        }
    }
}