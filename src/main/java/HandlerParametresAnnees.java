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

public class HandlerParametresAnnees implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(HandlerParametresAnnees.class.getName());
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
            if (path.equals("/admin/parametres/annees") && "GET".equals(exchange.getRequestMethod())) {
                handlePageHtml(exchange, institutionId);
                return;
            }

            // --- API ---
            if (path.equals("/admin/parametres/annees/api/annees") && "GET".equals(exchange.getRequestMethod())) {
                handleGetAll(exchange, institutionId);
            } else if (path.equals("/admin/parametres/annees/api/annees") && "POST".equals(exchange.getRequestMethod())) {
                handleCreate(exchange, institutionId);
            } else if (path.startsWith("/admin/parametres/annees/api/annees/") && "GET".equals(exchange.getRequestMethod())) {
                handleGetByAnnee(exchange, institutionId);
            } else if (path.startsWith("/admin/parametres/annees/api/annees/") && "PUT".equals(exchange.getRequestMethod())) {
                handleUpdate(exchange, institutionId);
            } else if (path.startsWith("/admin/parametres/annees/api/annees/") && "DELETE".equals(exchange.getRequestMethod())) {
                handleDelete(exchange, institutionId);
            } else {
                sendJsonError(exchange, 404, "Endpoint non trouvé");
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur SQL", e);
            sendJsonError(exchange, 500, "Erreur base de données: " + e.getMessage());
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Erreur", e);
            sendJsonError(exchange, 500, "Erreur interne: " + e.getMessage());
        }
    }

    // ============================================================
    // PAGE HTML
    // ============================================================
    private void handlePageHtml(HttpExchange exchange, String institutionId) throws IOException, SQLException {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            List<AnneeAcademique> annees = getAnneesAcademiques(conn, institutionId);

            List<Map<String, Object>> anneesMap = annees.stream().map(a -> {
                Map<String, Object> map = new HashMap<>();
                // ✅ Plus de "id" — clé composite = anneeAcademique
                map.put("anneeAcademique", a.getAnneeAcademique());
                map.put("dateDebut", a.getDateDebut());
                map.put("dateFin", a.getDateFin());
                map.put("estActive", a.isEstActive());
                return map;
            }).collect(Collectors.toList());

            String html = UIParametresAnnees.rendrePage(anneesMap, institutionId, null, null);
            sendResponse(exchange, 200, html);
        }
    }

    // ============================================================
    // API – GET ALL
    // ============================================================
    private void handleGetAll(HttpExchange exchange, String institutionId) throws IOException, SQLException {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            List<AnneeAcademique> annees = getAnneesAcademiques(conn, institutionId);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", annees);
            sendJson(exchange, 200, result);
        }
    }

    // ============================================================
    // API – GET BY ANNEE (clé composite)
    // ============================================================
    private void handleGetByAnnee(HttpExchange exchange, String institutionId) throws IOException, SQLException {
        String anneeAcademique = extraireAnneeFromPath(exchange.getRequestURI().getPath());
        if (anneeAcademique == null || anneeAcademique.isBlank()) {
            sendJsonError(exchange, 400, "Année académique manquante");
            return;
        }
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            AnneeAcademique annee = getAnneeAcademiqueParCle(conn, institutionId, anneeAcademique);
            if (annee == null) {
                sendJsonError(exchange, 404, "Année non trouvée");
                return;
            }
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", annee);
            sendJson(exchange, 200, result);
        }
    }

    // ============================================================
    // API – CREATE
    // ============================================================
    private void handleCreate(HttpExchange exchange, String institutionId) throws IOException, SQLException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        AnneeAcademique annee;
        try {
            annee = GSON.fromJson(body, AnneeAcademique.class);
        } catch (JsonSyntaxException e) {
            sendJsonError(exchange, 400, "JSON invalide");
            return;
        }
        if (annee.getAnneeAcademique() == null || annee.getAnneeAcademique().isBlank()
                || annee.getDateDebut() == null || annee.getDateDebut().isBlank()
                || annee.getDateFin() == null || annee.getDateFin().isBlank()) {
            sendJsonError(exchange, 400, "Champs anneeAcademique, dateDebut, dateFin requis");
            return;
        }
        annee.setInstitutionId(institutionId);
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (anneeExiste(conn, institutionId, annee.getAnneeAcademique())) {
                sendJsonError(exchange, 400, "Une année académique avec ce nom existe déjà");
                return;
            }
            ajouterAnneeAcademique(conn, annee);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Année académique créée avec succès");
            sendJson(exchange, 201, result);
        }
    }

    // ============================================================
    // API – UPDATE (clé composite : ancienne annee → nouvelle annee)
    // ============================================================
    private void handleUpdate(HttpExchange exchange, String institutionId) throws IOException, SQLException {
        String ancienneAnnee = extraireAnneeFromPath(exchange.getRequestURI().getPath());
        if (ancienneAnnee == null || ancienneAnnee.isBlank()) {
            sendJsonError(exchange, 400, "Année académique manquante dans l'URL");
            return;
        }

        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        AnneeAcademique annee;
        try {
            annee = GSON.fromJson(body, AnneeAcademique.class);
        } catch (JsonSyntaxException e) {
            sendJsonError(exchange, 400, "JSON invalide");
            return;
        }

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            AnneeAcademique existing = getAnneeAcademiqueParCle(conn, institutionId, ancienneAnnee);
            if (existing == null) {
                sendJsonError(exchange, 404, "Année non trouvée");
                return;
            }

            String nouvelleAnnee = annee.getAnneeAcademique();
            if (nouvelleAnnee == null || nouvelleAnnee.isBlank()) {
                sendJsonError(exchange, 400, "Le nom de l'année est obligatoire");
                return;
            }

            boolean cleChange = !ancienneAnnee.equals(nouvelleAnnee);

            if (cleChange && anneeExiste(conn, institutionId, nouvelleAnnee)) {
                sendJsonError(exchange, 400, "Une année académique avec ce nom existe déjà");
                return;
            }

            if (cleChange) {
                // ✅ La PK change → DELETE + INSERT
                supprimerAnneeAcademique(conn, institutionId, ancienneAnnee);
                AnneeAcademique nouvelle = new AnneeAcademique();
                nouvelle.setInstitutionId(institutionId);
                nouvelle.setAnneeAcademique(nouvelleAnnee);
                nouvelle.setDateDebut(annee.getDateDebut());
                nouvelle.setDateFin(annee.getDateFin());
                nouvelle.setEstActive(annee.isEstActive());
                ajouterAnneeAcademique(conn, nouvelle);
            } else {
                // Mise à jour des champs modifiables
                existing.setDateDebut(annee.getDateDebut());
                existing.setDateFin(annee.getDateFin());
                existing.setEstActive(annee.isEstActive());
                mettreAJourAnneeAcademique(conn, existing);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Année académique mise à jour");
            sendJson(exchange, 200, result);
        }
    }

    // ============================================================
    // API – DELETE (clé composite)
    // ============================================================
    private void handleDelete(HttpExchange exchange, String institutionId) throws IOException, SQLException {
        String anneeAcademique = extraireAnneeFromPath(exchange.getRequestURI().getPath());
        if (anneeAcademique == null || anneeAcademique.isBlank()) {
            sendJsonError(exchange, 400, "Année académique manquante dans l'URL");
            return;
        }
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            AnneeAcademique existing = getAnneeAcademiqueParCle(conn, institutionId, anneeAcademique);
            if (existing == null) {
                sendJsonError(exchange, 404, "Année non trouvée");
                return;
            }
            supprimerAnneeAcademique(conn, institutionId, anneeAcademique);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Année académique supprimée");
            sendJson(exchange, 200, result);
        }
    }

    // ============================================================
    // MÉTHODES DAO (clé composite)
    // ============================================================

    /**
     * ✅ Sans `id` — la PK est (institution_id, annee_academique).
     */
    private List<AnneeAcademique> getAnneesAcademiques(Connection conn, String institutionId) throws SQLException {
        List<AnneeAcademique> list = new ArrayList<>();
        String sql = "SELECT institution_id, annee_academique, date_debut, date_fin, est_active "
                   + "FROM annees_academiques "
                   + "WHERE institution_id = ? "
                   + "ORDER BY annee_academique DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
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

    /**
     * ✅ getAnneeAcademiqueParCle (au lieu de ParId).
     */
    private AnneeAcademique getAnneeAcademiqueParCle(Connection conn, String institutionId,
                                                       String anneeAcademique) throws SQLException {
        String sql = "SELECT institution_id, annee_academique, date_debut, date_fin, est_active "
                   + "FROM annees_academiques "
                   + "WHERE institution_id = ? AND annee_academique = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            ps.setString(2, anneeAcademique);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    AnneeAcademique a = new AnneeAcademique();
                    a.setInstitutionId(rs.getString("institution_id"));
                    a.setAnneeAcademique(rs.getString("annee_academique"));
                    a.setDateDebut(rs.getString("date_debut"));
                    a.setDateFin(rs.getString("date_fin"));
                    a.setEstActive(rs.getBoolean("est_active"));
                    return a;
                }
            }
        }
        return null;
    }

    private boolean anneeExiste(Connection conn, String institutionId, String anneeAcademique) throws SQLException {
        String sql = "SELECT COUNT(*) FROM annees_academiques "
                   + "WHERE institution_id = ? AND annee_academique = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            ps.setString(2, anneeAcademique);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }

    private void ajouterAnneeAcademique(Connection conn, AnneeAcademique annee) throws SQLException {
        String sql = "INSERT INTO annees_academiques "
                   + "(institution_id, annee_academique, date_debut, date_fin, est_active) "
                   + "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, annee.getInstitutionId());
            ps.setString(2, annee.getAnneeAcademique());
            ps.setString(3, annee.getDateDebut());
            ps.setString(4, annee.getDateFin());
            ps.setBoolean(5, annee.isEstActive());
            ps.executeUpdate();
        }
    }

    private void mettreAJourAnneeAcademique(Connection conn, AnneeAcademique annee) throws SQLException {
        // ✅ WHERE sur la PK composite (l'annee ne change pas ici)
        String sql = "UPDATE annees_academiques SET date_debut = ?, date_fin = ?, est_active = ? "
                   + "WHERE institution_id = ? AND annee_academique = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, annee.getDateDebut());
            ps.setString(2, annee.getDateFin());
            ps.setBoolean(3, annee.isEstActive());
            ps.setString(4, annee.getInstitutionId());
            ps.setString(5, annee.getAnneeAcademique());
            ps.executeUpdate();
        }
    }

    private void supprimerAnneeAcademique(Connection conn, String institutionId,
                                            String anneeAcademique) throws SQLException {
        String sql = "DELETE FROM annees_academiques "
                   + "WHERE institution_id = ? AND annee_academique = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            ps.setString(2, anneeAcademique);
            ps.executeUpdate();
        }
    }

    // ============================================================
    // UTILITAIRES
    // ============================================================

    /**
     * ✅ Extrait l'année académique depuis l'URL.
     * Exemple : /admin/parametres/annees/api/annees/2026-2027
     *          → retourne "2026-2027"
     */
    private String extraireAnneeFromPath(String path) {
        if (path == null) return null;
        String[] segments = path.split("/");
        if (segments.length < 7) return null;
        try {
            return URLDecoder.decode(segments[6], StandardCharsets.UTF_8);
        } catch (Exception e) {
            return segments[6];
        }
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
            if (instId == null || instId.isBlank()) instId = (String) session.getAttribute("institution_id");
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