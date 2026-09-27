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

/**
 * Handler pour la gestion des périodes académiques.
 *
 * ✅ ARCHITECTURE MULTI-INSTITUTIONS :
 *   - Clé composite (institution_id, annee_academique, periode)
 *   - Plus de référence à la colonne `id`
 *   - Délègue les opérations d'écriture à {@link PeriodeData} (sync LOCAL/REMOTE)
 */
public class HandlerParametresPeriodes implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(HandlerParametresPeriodes.class.getName());
    private static final Gson GSON = new Gson();
    private static final int QUERY_TIMEOUT_SECONDS = 30;

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        cors(exchange);

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
            // ---------- PAGE HTML ----------
            if (path.equals("/admin/parametres/periodes") && "GET".equals(exchange.getRequestMethod())) {
                handlePageHtml(exchange, institutionId);
                return;
            }

            // ---------- API RESTful ----------
            if (path.equals("/admin/parametres/periodes/api/periodes")) {
                if (null == exchange.getRequestMethod()) {
                    sendJsonError(exchange, 405, "Méthode non autorisée");
                } else switch (exchange.getRequestMethod()) {
                    case "GET" -> handleGetAll(exchange, institutionId);
                    case "POST" -> handleCreate(exchange, institutionId);
                    default -> sendJsonError(exchange, 405, "Méthode non autorisée");
                }
                return;
            }

            // Routes avec clé composite
            if (path.startsWith("/admin/parametres/periodes/api/periodes/")) {
                String[] segments = path.split("/");
                if (segments.length < 8) {
                    sendJsonError(exchange, 400, "Clé composite manquante (annee + periode)");
                    return;
                }
                String annee = URLDecoder.decode(segments[6], StandardCharsets.UTF_8);
                String periode = URLDecoder.decode(segments[7], StandardCharsets.UTF_8);

                switch (exchange.getRequestMethod()) {
                    case "GET" -> handleGetByComposite(exchange, institutionId, annee, periode);
                    case "PUT" -> handleUpdate(exchange, institutionId, annee, periode);
                    case "DELETE" -> handleDelete(exchange, institutionId, annee, periode);
                    default -> sendJsonError(exchange, 405, "Méthode non autorisée");
                }
                return;
            }

            sendJsonError(exchange, 404, "Endpoint non trouvé");

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "❌ Erreur SQL", e);
            sendJsonError(exchange, 500, "Erreur base de données: " + e.getMessage());
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "❌ Erreur", e);
            sendJsonError(exchange, 500, "Erreur interne: " + e.getMessage());
        }
    }

    // ============================================================
    // PAGE HTML
    // ============================================================
    private void handlePageHtml(HttpExchange exchange, String institutionId)
            throws IOException, SQLException {
        Map<String, String> queryParams = parseQueryParams(exchange.getRequestURI().getQuery());
        String anneeFiltre = queryParams.getOrDefault("annee", "");

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            List<AnneeAcademique> annees = getAnneesAcademiques(conn, institutionId);
            List<Periode> periodes = anneeFiltre.isEmpty()
                    ? getPeriodes(conn, institutionId)
                    : getPeriodesByAnnee(conn, institutionId, anneeFiltre);

            List<Map<String, Object>> periodesMap = periodes.stream()
                    .map(this::toMap)
                    .collect(Collectors.toList());

            List<Map<String, Object>> anneesMap = annees.stream()
                    .map(a -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("anneeAcademique", a.getAnneeAcademique());
                        return map;
                    })
                    .collect(Collectors.toList());

            String html = UIParametresPeriodes.rendrePage(
                    periodesMap, anneesMap, anneeFiltre, institutionId, null, null);
            sendResponse(exchange, 200, html);
        }
    }

    private Map<String, Object> toMap(Periode p) {
        Map<String, Object> map = new HashMap<>();
        map.put("institutionId", p.getInstitutionId());
        map.put("anneeAcademique", p.getAnneeAcademique());
        map.put("periode", p.getPeriode());
        map.put("dateDebut", p.getDateDebut());
        map.put("dateFin", p.getDateFin());
        map.put("estActive", p.isEstActive());
        map.put("cleComposite", p.getCleComposite());
        return map;
    }

    // ============================================================
    // API – GET ALL
    // ============================================================
    private void handleGetAll(HttpExchange exchange, String institutionId)
            throws IOException, SQLException {
        Map<String, String> queryParams = parseQueryParams(exchange.getRequestURI().getQuery());
        String anneeFiltre = queryParams.getOrDefault("annee", "");

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            List<Periode> periodes = anneeFiltre.isEmpty()
                    ? getPeriodes(conn, institutionId)
                    : getPeriodesByAnnee(conn, institutionId, anneeFiltre);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", periodes);
            sendJson(exchange, 200, result);
        }
    }

    // ============================================================
    // API – GET BY COMPOSITE KEY
    // ============================================================
    private void handleGetByComposite(HttpExchange exchange, String institutionId,
                                       String annee, String periode)
            throws IOException, SQLException {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            Periode p = getPeriodeParCle(conn, institutionId, annee, periode);
            if (p == null) {
                sendJsonError(exchange, 404, "Période non trouvée");
                return;
            }
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", p);
            sendJson(exchange, 200, result);
        }
    }

    // ============================================================
    // API – CREATE
    // ============================================================
    private void handleCreate(HttpExchange exchange, String institutionId)
            throws IOException, SQLException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Periode periode;
        try {
            periode = GSON.fromJson(body, Periode.class);
        } catch (JsonSyntaxException e) {
            sendJsonError(exchange, 400, "JSON invalide");
            return;
        }

        // Validation
        if (periode.getAnneeAcademique() == null || periode.getAnneeAcademique().isBlank()
                || periode.getPeriode() == null || periode.getPeriode().isBlank()
                || periode.getDateDebut() == null || periode.getDateDebut().isBlank()
                || periode.getDateFin() == null || periode.getDateFin().isBlank()) {
            sendJsonError(exchange, 400,
                    "Champs anneeAcademique, periode, dateDebut, dateFin requis");
            return;
        }

        periode.setInstitutionId(institutionId);

        // ✅ Vérifier que l'année académique existe
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (!anneeExiste(conn, institutionId, periode.getAnneeAcademique())) {
                sendJsonError(exchange, 400,
                        "L'année académique '" + periode.getAnneeAcademique() + "' n'existe pas");
                return;
            }
            if (periodeExiste(conn, institutionId,
                    periode.getAnneeAcademique(), periode.getPeriode())) {
                sendJsonError(exchange, 400,
                        "Une période avec ce nom existe déjà pour cette année");
                return;
            }
        }

        // ✅ Déléguer à PeriodeData (gère sync + tombstone + retry + outbox)
        try {
            PeriodeData periodeData = new PeriodeData();
            boolean ok = periodeData.create(periode);

            if (ok) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("message", "Période créée avec succès");
                sendJson(exchange, 201, result);
            } else {
                sendJsonError(exchange, 500, "Échec de la création");
            }
        } catch (IOException | SQLException e) {
            LOGGER.log(Level.SEVERE, "❌ Erreur création période", e);
            sendJsonError(exchange, 500, "Erreur: " + e.getMessage());
        }
    }

    // ============================================================
    // API – UPDATE
    // ============================================================
    private void handleUpdate(HttpExchange exchange, String institutionId,
                               String annee, String periode)
            throws IOException, SQLException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Periode nouvelle;
        try {
            nouvelle = GSON.fromJson(body, Periode.class);
        } catch (JsonSyntaxException e) {
            sendJsonError(exchange, 400, "JSON invalide");
            return;
        }

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            Periode existing = getPeriodeParCle(conn, institutionId, annee, periode);
            if (existing == null) {
                sendJsonError(exchange, 404, "Période non trouvée");
                return;
            }
        }

        // ✅ Mise à jour
        Periode periodeMaj = new Periode();
        periodeMaj.setInstitutionId(institutionId);
        periodeMaj.setAnneeAcademique(annee);
        periodeMaj.setPeriode(periode);
        periodeMaj.setDateDebut(nouvelle.getDateDebut() != null
                ? nouvelle.getDateDebut() : "");
        periodeMaj.setDateFin(nouvelle.getDateFin() != null
                ? nouvelle.getDateFin() : "");
        periodeMaj.setEstActive(nouvelle.isEstActive());

        try {
            PeriodeData periodeData = new PeriodeData();
            boolean ok = periodeData.update(periodeMaj);

            if (ok) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("message", "Période mise à jour");
                sendJson(exchange, 200, result);
            } else {
                sendJsonError(exchange, 500, "Échec de la mise à jour");
            }
        } catch (IOException | SQLException e) {
            LOGGER.log(Level.SEVERE, "❌ Erreur update période", e);
            sendJsonError(exchange, 500, "Erreur: " + e.getMessage());
        }
    }

    // ============================================================
    // API – DELETE
    // ============================================================
    private void handleDelete(HttpExchange exchange, String institutionId,
                               String annee, String periode)
            throws IOException, SQLException {
        // Vérifier existence
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            Periode existing = getPeriodeParCle(conn, institutionId, annee, periode);
            if (existing == null) {
                sendJsonError(exchange, 404, "Période non trouvée");
                return;
            }
        }

        // ✅ Déléguer à PeriodeData (gère tombstone + sync + retry + outbox)
        try {
            PeriodeData periodeData = new PeriodeData();
            boolean ok = periodeData.delete(institutionId, annee, periode);

            if (ok) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("message", "Période supprimée");
                sendJson(exchange, 200, result);
            } else {
                sendJsonError(exchange, 500, "Échec de la suppression");
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "❌ Erreur suppression période", e);
            sendJsonError(exchange, 500, "Erreur: " + e.getMessage());
        }
    }

    // ============================================================
    // MÉTHODES DAO (lecture seule)
    // ============================================================
    private List<AnneeAcademique> getAnneesAcademiques(Connection conn, String institutionId)
            throws SQLException {
        List<AnneeAcademique> list = new ArrayList<>();
        String sql = "SELECT institution_id, annee_academique, date_debut, date_fin, est_active "
                   + "FROM annees_academiques "
                   + "WHERE institution_id = ? "
                   + "ORDER BY annee_academique DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setString(1, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AnneeAcademique a = new AnneeAcademique();
                    a.setInstitutionId(rs.getString("institution_id"));
                    a.setAnneeAcademique(rs.getString("annee_academique"));
                    a.setDateDebut(rs.getString("date_debut"));
                    a.setDateFin(rs.getString("date_fin"));
                    a.setEstActive(rs.getBoolean("est_active"));
                    list.add(a);
                }
            }
        }
        return list;
    }

    private List<Periode> getPeriodes(Connection conn, String institutionId)
            throws SQLException {
        List<Periode> list = new ArrayList<>();
        String sql = "SELECT institution_id, annee_academique, periode, "
                   + "date_debut, date_fin, est_active "
                   + "FROM periodes WHERE institution_id = ? "
                   + "ORDER BY annee_academique DESC, periode";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setString(1, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }

    private List<Periode> getPeriodesByAnnee(Connection conn, String institutionId, String annee)
            throws SQLException {
        List<Periode> list = new ArrayList<>();
        String sql = "SELECT institution_id, annee_academique, periode, "
                   + "date_debut, date_fin, est_active "
                   + "FROM periodes WHERE institution_id = ? AND annee_academique = ? "
                   + "ORDER BY periode";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setString(1, institutionId);
            ps.setString(2, annee);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }

    private Periode getPeriodeParCle(Connection conn, String institutionId,
                                      String annee, String periode) throws SQLException {
        String sql = "SELECT institution_id, annee_academique, periode, "
                   + "date_debut, date_fin, est_active "
                   + "FROM periodes "
                   + "WHERE institution_id = ? AND annee_academique = ? AND periode = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setString(1, institutionId);
            ps.setString(2, annee);
            ps.setString(3, periode);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    private boolean anneeExiste(Connection conn, String institutionId, String annee)
            throws SQLException {
        String sql = "SELECT 1 FROM annees_academiques "
                   + "WHERE institution_id = ? AND annee_academique = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setString(1, institutionId);
            ps.setString(2, annee);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean periodeExiste(Connection conn, String institutionId,
                                    String annee, String periode) throws SQLException {
        String sql = "SELECT 1 FROM periodes "
                   + "WHERE institution_id = ? AND annee_academique = ? AND periode = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setString(1, institutionId);
            ps.setString(2, annee);
            ps.setString(3, periode);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    // ============================================================
    // MAPPING
    // ============================================================
    private Periode mapRow(ResultSet rs) throws SQLException {
        Periode p = new Periode();
        p.setInstitutionId(rs.getString("institution_id"));
        p.setAnneeAcademique(rs.getString("annee_academique"));
        p.setPeriode(rs.getString("periode"));
        p.setDateDebut(rs.getString("date_debut"));
        p.setDateFin(rs.getString("date_fin"));
        p.setEstActive(rs.getBoolean("est_active"));
        try { p.setSource(rs.getString("source")); } catch (SQLException ignored) {}
        return p;
    }

    // ============================================================
    // UTILITAIRES
    // ============================================================
    private void cors(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

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

    private void sendJson(HttpExchange exchange, int statusCode,
                           Map<String, Object> data) throws IOException {
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

    private void sendJsonError(HttpExchange exchange, int statusCode,
                                String message) throws IOException {
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", message);
        sendJson(exchange, statusCode, error);
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String html)
            throws IOException {
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