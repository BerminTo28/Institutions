import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
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

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class HandlerValidationNotes implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(HandlerValidationNotes.class.getName());
    private static final Gson GSON = new Gson();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        try {
            String institutionId = getInstitutionIdFromSession(exchange);
            if (institutionId == null || institutionId.isBlank()) {
                exchange.getResponseHeaders().set("Location", "/connexion");
                exchange.sendResponseHeaders(302, -1);
                return;
            }

            if (path.startsWith("/admin/validation-notes/api/")) {
                handleApi(exchange, institutionId);
                return;
            }

            if ("GET".equalsIgnoreCase(method)) {
                handleGet(exchange, institutionId);
            } else {
                ResponseUtil.sendError(exchange, 405, "Méthode non autorisée");
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Erreur dans HandlerValidationNotes", e);
            String html = UINotesValidation.renderError("Erreur interne: " + e.getMessage());
            ResponseUtil.sendHtml(exchange, 500, html);
        }
    }

    private void handleGet(HttpExchange exchange, String institutionId) throws IOException {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            List<String> annees = getAnnees(conn, institutionId);
            List<String> periodes = getPeriodes(conn, institutionId);
            List<String> classes = getClasses(conn, institutionId);
            List<String> matieres = getMatieres(conn, institutionId);

            Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
            String annee = params.getOrDefault("annee", "");
            String periode = params.getOrDefault("periode", "");
            String classe = params.getOrDefault("classe", "");
            String matiere = params.getOrDefault("matiere", "");

            NoteData noteData = new NoteData(conn);
            List<Note> notes = noteData.getNotesNonValidees(institutionId, matiere, periode, classe, annee);

            String html = UINotesValidation.render(
                annees, periodes, classes, matieres,
                annee, periode, classe, matiere,
                notes, null, null
            );
            ResponseUtil.sendHtml(exchange, 200, html);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur SQL", e);
            String html = UINotesValidation.renderError("Erreur base de données.");
            ResponseUtil.sendHtml(exchange, 500, html);
        }
    }

    private void handleApi(HttpExchange exchange, String institutionId) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            NoteData noteData = new NoteData(conn);

            // ==================== VALIDER ====================
            if (path.endsWith("/valider") && "POST".equalsIgnoreCase(method)) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                Map<String, String> params = parseFormData(body);

                // ✅ id String (UUID)
                String noteId = params.get("id");
                if (noteId == null || noteId.isBlank()) {
                    sendJsonError(exchange, "ID manquant");
                    return;
                }

                String verifiedBy = "Administrateur";
                boolean success = noteData.validerNote(noteId, verifiedBy);   // ✅ String

                Map<String, Object> result = new HashMap<>();
                result.put("success", success);
                result.put("message", success ? "Note validée" : "Erreur lors de la validation");
                sendJson(exchange, result);
                return;
            }

            // ==================== REJETER ====================
            if (path.endsWith("/rejeter") && "POST".equalsIgnoreCase(method)) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                Map<String, String> params = parseFormData(body);

                // ✅ id String (UUID)
                String noteId = params.get("id");
                if (noteId == null || noteId.isBlank()) {
                    sendJsonError(exchange, "ID manquant");
                    return;
                }

                boolean success = noteData.rejeterNote(noteId);               // ✅ String

                Map<String, Object> result = new HashMap<>();
                result.put("success", success);
                result.put("message", success ? "Note rejetée" : "Erreur lors du rejet");
                sendJson(exchange, result);
                return;
            }

            // ==================== LISTE ====================
            if (path.endsWith("/liste") && "GET".equalsIgnoreCase(method)) {
                Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
                String annee = params.get("annee");
                String periode = params.get("periode");
                String classe = params.get("classe");
                String matiere = params.get("matiere");
                List<Note> notes = noteData.getNotesNonValidees(institutionId, matiere, periode, classe, annee);
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("notes", notes);
                sendJson(exchange, result);
                return;
            }

            sendJsonError(exchange, "API inconnue");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur API", e);
            sendJsonError(exchange, "Erreur base de données");
        } catch (Exception e) {
            // ✅ Catch générique au lieu de NumberFormatException
            LOGGER.log(Level.SEVERE, "Erreur inattendue API", e);
            sendJsonError(exchange, "Erreur: " + e.getMessage());
        }
    }

    // ==================== MÉTHODES DE RÉCUPÉRATION ====================

    private List<String> getAnnees(Connection conn, String institutionId) throws SQLException {
        List<String> annees = new ArrayList<>();
        String sql = "SELECT DISTINCT annee_academique FROM " + MigrationManager.TABLE_ANNEES_ACADEMIQUES +
                     " WHERE institution_id = ? ORDER BY annee_academique DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    annees.add(rs.getString("annee_academique"));
                }
            }
        }
        return annees;
    }

    private List<String> getPeriodes(Connection conn, String institutionId) throws SQLException {
        List<String> periodes = new ArrayList<>();
        String sql = "SELECT DISTINCT periode FROM " + MigrationManager.TABLE_PERIODES +
                     " WHERE institution_id = ? ORDER BY periode";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    periodes.add(rs.getString("periode"));
                }
            }
        }
        return periodes;
    }

    private List<String> getClasses(Connection conn, String institutionId) throws SQLException {
        List<String> classes = new ArrayList<>();
        String sql = "SELECT DISTINCT nom_classe FROM " + MigrationManager.TABLE_CLASSES +
                     " WHERE institution_id = ? ORDER BY nom_classe";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    classes.add(rs.getString("nom_classe"));
                }
            }
        }
        return classes;
    }

    private List<String> getMatieres(Connection conn, String institutionId) throws SQLException {
        List<String> matieres = new ArrayList<>();
        String sql = "SELECT DISTINCT code_cours FROM " + MigrationManager.TABLE_MATIERES +
                     " WHERE institution_id = ? AND statut = 'ACTIF' ORDER BY code_cours";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    matieres.add(rs.getString("code_cours"));
                }
            }
        }
        return matieres;
    }

    // ==================== SESSION ET PARSING ====================

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
            LOGGER.log(Level.WARNING, "Erreur récupération institutionId", e);
            return null;
        }
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null) return params;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=");
            if (kv.length == 2) {
                try {
                    params.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8.name()),
                               URLDecoder.decode(kv[1], StandardCharsets.UTF_8.name()));
                } catch (UnsupportedEncodingException e) { /* ignore */ }
            }
        }
        return params;
    }

    private Map<String, String> parseFormData(String body) {
        Map<String, String> params = new HashMap<>();
        if (body == null || body.isBlank()) return params;
        for (String pair : body.split("&")) {
            String[] kv = pair.split("=");
            if (kv.length == 2) {
                try {
                    params.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8.name()),
                               URLDecoder.decode(kv[1], StandardCharsets.UTF_8.name()));
                } catch (UnsupportedEncodingException e) { /* ignore */ }
            }
        }
        return params;
    }

    private void sendJson(HttpExchange exchange, Map<String, Object> data) throws IOException {
        String json = GSON.toJson(data);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendJsonError(HttpExchange exchange, String message) throws IOException {
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", message);
        sendJson(exchange, error);
    }
}