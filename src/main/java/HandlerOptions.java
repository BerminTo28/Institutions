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
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class HandlerOptions implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(HandlerOptions.class.getName());
    private static final Gson GSON = new Gson();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // CORS
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        String path = exchange.getRequestURI().getPath();
        String institutionId = getInstitutionIdFromSession(exchange);
        if (institutionId == null || institutionId.isBlank()) {
            sendJsonError(exchange, 401, "Non authentifié");
            return;
        }

        try {
            // --- PAGE HTML ---
            if (path.equals("/admin/parametres/options") && "GET".equals(exchange.getRequestMethod())) {
                handlePageHtml(exchange, institutionId);
                return;
            }

            // --- API ---
            if (path.equals("/admin/parametres/options/api/options") && "GET".equals(exchange.getRequestMethod())) {
                handleGetAll(exchange, institutionId);
                return;
            }
            if (path.equals("/admin/parametres/options/api/options") && "POST".equals(exchange.getRequestMethod())) {
                handleCreate(exchange, institutionId);
                return;
            }

            // Routes avec clé composite : /api/options/{option}/{classe}/{annee}
            if (path.startsWith("/admin/parametres/options/api/options/")) {
                String reste = path.substring("/admin/parametres/options/api/options/".length());
                String[] parts = reste.split("/");
                if (parts.length < 3) {
                    sendJsonError(exchange, 400,
                            "Clé composite manquante : option + classe + annee requis");
                    return;
                }

                String option = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
                String classe = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
                String annee = URLDecoder.decode(parts[2], StandardCharsets.UTF_8);

                if ("GET".equals(exchange.getRequestMethod())) {
                    handleGetByCle(exchange, institutionId, option, classe, annee);
                    return;
                }
                if ("PUT".equals(exchange.getRequestMethod())) {
                    handleUpdate(exchange, institutionId, option, classe, annee);
                    return;
                }
                if ("DELETE".equals(exchange.getRequestMethod())) {
                    handleDelete(exchange, institutionId, option, classe, annee);
                    return;
                }
            }

            sendJsonError(exchange, 404, "Endpoint non trouvé");

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur SQL", e);
            sendJsonError(exchange, 500, "Erreur base de données: " + e.getMessage());
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Erreur", e);
            sendJsonError(exchange, 500, "Erreur interne: " + e.getMessage());
        }
    }

    // ============================================================
    // PAGE HTML – avec chargement des listes depuis les tables
    // ============================================================
    private void handlePageHtml(HttpExchange exchange, String institutionId) throws IOException, SQLException {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            OptionManager manager = new OptionManager(institutionId, conn);
            List<Options> options = manager.recupererToutesLesOptions();

            List<Map<String, Object>> optionsMap = options.stream().map(opt -> {
                Map<String, Object> map = new HashMap<>();
                // ✅ Plus de "id" — clé composite
                map.put("option", opt.getOption());
                map.put("classe", opt.getClasse());
                map.put("anneeAcademique", opt.getAnneeAcademique());
                map.put("periode", opt.getPeriode());
                map.put("promotion", opt.getPromotion());
                return map;
            }).collect(Collectors.toList());

            List<String> annees = getAnneesAcademiques(conn, institutionId);
            List<String> periodes = getPeriodes(conn, institutionId);
            List<String> classes = getClasses(conn, institutionId);
            List<String> promotions = getPromotions(conn, institutionId);

            String html = UIOptions.rendrePage(optionsMap, institutionId, annees, periodes,
                    classes, promotions, null, null);
            sendResponse(exchange, 200, html);
        }
    }

    // ============================================================
    // API – GET ALL
    // ============================================================
    private void handleGetAll(HttpExchange exchange, String institutionId) throws IOException, SQLException {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            OptionManager manager = new OptionManager(institutionId, conn);
            List<Options> options = manager.recupererToutesLesOptions();
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", options);
            sendJson(exchange, 200, result);
        }
    }

    // ============================================================
    // API – GET BY CLÉ COMPOSITE
    // ============================================================
    private void handleGetByCle(HttpExchange exchange, String institutionId,
                                  String option, String classe, String annee)
            throws IOException, SQLException {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            OptionManager manager = new OptionManager(institutionId, conn);
            Options opt = manager.recupererOption(option, classe, annee);
            if (opt == null) {
                sendJsonError(exchange, 404, "Option non trouvée");
                return;
            }
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", opt);
            sendJson(exchange, 200, result);
        }
    }

    // ============================================================
    // API – CREATE (avec transaction + sync)
    // ============================================================
    private void handleCreate(HttpExchange exchange, String institutionId)
            throws IOException, SQLException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Options option;
        try {
            option = GSON.fromJson(body, Options.class);
        } catch (JsonSyntaxException e) {
            sendJsonError(exchange, 400, "JSON invalide");
            return;
        }

        if (option.getOption() == null || option.getOption().isBlank()
                || option.getClasse() == null || option.getClasse().isBlank()
                || option.getAnneeAcademique() == null || option.getAnneeAcademique().isBlank()) {
            sendJsonError(exchange, 400, "Champs option, classe, anneeAcademique requis");
            return;
        }
        option.setInstitutionId(institutionId);

        Connection conn = null;
        boolean autoCommitOriginal;
        try {
            conn = DatabaseManager.getInstance().getConnection();
            autoCommitOriginal = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try {
                OptionManager manager = new OptionManager(institutionId, conn);
                manager.ajouterOption(option);

                conn.commit();
                LOGGER.info(() -> "✅ Option créée : " + option.getOption()
                        + " (classe=" + option.getClasse() + ")");

                declencherSyncAsyncApresCommit();

                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("message", "Option créée avec succès");
                sendJson(exchange, 201, result);

            } catch (SQLException e) {
                conn.rollback();
                LOGGER.log(Level.SEVERE, "❌ Rollback création option", e);
                sendJsonError(exchange, 500, "Erreur base de données: " + e.getMessage());
            } finally {
                restoreAutoCommit(conn, autoCommitOriginal);
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "❌ Erreur connexion création option", e);
            sendJsonError(exchange, 500, "Erreur base de données");
        } finally {
            closeQuietly(conn);
        }
    }

    // ============================================================
    // API – UPDATE (par clé composite)
    // ============================================================
    private void handleUpdate(HttpExchange exchange, String institutionId,
                                String ancienneOption, String ancienneClasse,
                                String ancienneAnnee) throws IOException, SQLException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Options nouvelle;
        try {
            nouvelle = GSON.fromJson(body, Options.class);
        } catch (JsonSyntaxException e) {
            sendJsonError(exchange, 400, "JSON invalide");
            return;
        }

        Connection conn = null;
        boolean autoCommitOriginal;
        try {
            conn = DatabaseManager.getInstance().getConnection();
            autoCommitOriginal = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try {
                OptionManager manager = new OptionManager(institutionId, conn);

                // Vérifier que l'option existe
                Options existing = manager.recupererOption(ancienneOption, ancienneClasse, ancienneAnnee);
                if (existing == null) {
                    conn.rollback();
                    sendJsonError(exchange, 404, "Option non trouvée");
                    return;
                }

                // Déterminer les nouvelles valeurs
                String nouvelleOption = (nouvelle.getOption() != null
                        && !nouvelle.getOption().isBlank())
                        ? nouvelle.getOption() : existing.getOption();
                String nouvelleClasse = (nouvelle.getClasse() != null
                        && !nouvelle.getClasse().isBlank())
                        ? nouvelle.getClasse() : existing.getClasse();
                String nouvelleAnnee = (nouvelle.getAnneeAcademique() != null
                        && !nouvelle.getAnneeAcademique().isBlank())
                        ? nouvelle.getAnneeAcademique() : existing.getAnneeAcademique();

                boolean cleChange = !nouvelleOption.equals(ancienneOption)
                        || !nouvelleClasse.equals(ancienneClasse)
                        || !nouvelleAnnee.equals(ancienneAnnee);

                // Mettre à jour les champs modifiables
                existing.setPeriode(nouvelle.getPeriode() != null
                        ? nouvelle.getPeriode() : existing.getPeriode());
                existing.setPromotion(nouvelle.getPromotion() != null
                        ? nouvelle.getPromotion() : existing.getPromotion());

                if (cleChange) {
                    // ✅ Clé composite change → DELETE + INSERT
                    Options nouvelleCle = new Options();
                    nouvelleCle.setInstitutionId(institutionId);
                    nouvelleCle.setOption(nouvelleOption);
                    nouvelleCle.setClasse(nouvelleClasse);
                    nouvelleCle.setAnneeAcademique(nouvelleAnnee);
                    nouvelleCle.setPeriode(existing.getPeriode());
                    nouvelleCle.setPromotion(existing.getPromotion());

                    manager.mettreAJourOptionAvecChangementDeCle(
                            ancienneOption, ancienneClasse, ancienneAnnee, nouvelleCle);
                } else {
                    manager.mettreAJourOption(existing);
                }

                conn.commit();
                LOGGER.info(() -> "✅ Option mise à jour : " + nouvelleOption);

                declencherSyncAsyncApresCommit();

                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("message", "Option mise à jour");
                sendJson(exchange, 200, result);

            } catch (SQLException e) {
                conn.rollback();
                LOGGER.log(Level.SEVERE, "❌ Rollback mise à jour option", e);
                sendJsonError(exchange, 500, "Erreur base de données: " + e.getMessage());
            } finally {
                restoreAutoCommit(conn, autoCommitOriginal);
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "❌ Erreur connexion mise à jour option", e);
            sendJsonError(exchange, 500, "Erreur base de données");
        } finally {
            closeQuietly(conn);
        }
    }

    // ============================================================
    // API – DELETE (par clé composite)
    // ============================================================
    private void handleDelete(HttpExchange exchange, String institutionId,
                                String option, String classe, String annee)
            throws IOException, SQLException {
        Connection conn = null;
        boolean autoCommitOriginal;
        try {
            conn = DatabaseManager.getInstance().getConnection();
            autoCommitOriginal = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try {
                OptionManager manager = new OptionManager(institutionId, conn);
                Options existing = manager.recupererOption(option, classe, annee);
                if (existing == null) {
                    conn.rollback();
                    sendJsonError(exchange, 404, "Option non trouvée");
                    return;
                }

                manager.supprimerOption(option, classe, annee);

                conn.commit();
                LOGGER.info(() -> "✅ Option supprimée : " + option);

                declencherSyncAsyncApresCommit();

                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("message", "Option supprimée");
                sendJson(exchange, 200, result);

            } catch (SQLException e) {
                conn.rollback();
                LOGGER.log(Level.SEVERE, "❌ Rollback suppression option", e);
                sendJsonError(exchange, 500, "Erreur base de données: " + e.getMessage());
            } finally {
                restoreAutoCommit(conn, autoCommitOriginal);
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "❌ Erreur connexion suppression option", e);
            sendJsonError(exchange, 500, "Erreur base de données");
        } finally {
            closeQuietly(conn);
        }
    }

    // ============================================================
    // SYNCHRONISATION ASYNCHRONE
    // ============================================================
    private void declencherSyncAsyncApresCommit() {
        try {
            DatabaseManager.getInstance().declencherSyncImmediateAsync();
            LOGGER.fine("⚡ Sync asynchrone déclenchée après commit.");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "⚠️ Impossible de déclencher la sync async", e);
        }
    }

    // ============================================================
    // GESTION PROPRE DES CONNEXIONS
    // ============================================================
    private void restoreAutoCommit(Connection conn, boolean autoCommitOriginal) {
        if (conn == null) return;
        try {
            if (conn.getAutoCommit() != autoCommitOriginal) {
                conn.setAutoCommit(autoCommitOriginal);
            }
        } catch (SQLException ignored) {}
    }

    private void closeQuietly(Connection conn) {
        if (conn == null) return;
        try {
            if (!conn.isClosed()) conn.close();
        } catch (SQLException ignored) {}
    }

    // ============================================================
    // MÉTHODES DE RÉCUPÉRATION DES LISTES
    // ============================================================
    private List<String> getAnneesAcademiques(Connection conn, String institutionId) throws SQLException {
        List<String> list = new ArrayList<>();
        String sql = "SELECT annee_academique FROM annees_academiques "
                + "WHERE institution_id = ? ORDER BY annee_academique DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(rs.getString("annee_academique"));
            }
        }
        return list;
    }

    private List<String> getPeriodes(Connection conn, String institutionId) throws SQLException {
        List<String> list = new ArrayList<>();
        String sql = "SELECT periode FROM periodes WHERE institution_id = ? ORDER BY periode";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(rs.getString("periode"));
            }
        }
        return list;
    }

    private List<String> getClasses(Connection conn, String institutionId) throws SQLException {
        List<String> list = new ArrayList<>();
        String sql = "SELECT nom_classe FROM classes WHERE institution_id = ? ORDER BY nom_classe";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(rs.getString("nom_classe"));
            }
        }
        return list;
    }

    private List<String> getPromotions(Connection conn, String institutionId) throws SQLException {
        List<String> list = new ArrayList<>();
        String sql = "SELECT DISTINCT promotion FROM promotions "
                + "WHERE institution_id = ? ORDER BY promotion";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(rs.getString("promotion"));
            }
        }
        return list;
    }

    // ============================================================
    // UTILITAIRES
    // ============================================================
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
            LOGGER.warning(() -> "Erreur getInstitutionIdFromSession: " + e.getMessage());
            return null;
        }
    }

    private void sendJson(HttpExchange exchange, int statusCode, Map<String, Object> data) throws IOException {
        String json = GSON.toJson(data);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
            os.flush();
        }
    }

    private void sendJsonError(HttpExchange exchange, int statusCode, String message) throws IOException {
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", message);
        sendJson(exchange, statusCode, error);
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
            os.flush();
        }
    }
}