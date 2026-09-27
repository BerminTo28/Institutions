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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class HandlerEmploiduTemps implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(HandlerEmploiduTemps.class.getName());
    private static final Gson GSON = new Gson();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String institutionId = getInstitutionId(exchange);
        if (institutionId == null || institutionId.isBlank()) {
            institutionId = "ADMIN12345";
        }

        LOGGER.log(Level.INFO, "EMPLOI DU TEMPS HANDLER - {0} {1}", new Object[]{exchange.getRequestMethod(), path});

        if (path.startsWith("/admin/emploidutemps/api/")) {
            handleApi(exchange, institutionId, path);
            return;
        }

        // Page HTML
        String html = UIEmploiduTemps.renderPage(institutionId);
        sendResponse(exchange, 200, html);
    }

    private void handleApi(HttpExchange exchange, String institutionId, String path) throws IOException {
        String method = exchange.getRequestMethod();
        if ("OPTIONS".equals(method)) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (path.endsWith("/creneaux")) {
                handleCreneaux(exchange, institutionId, conn);
            } else if (path.endsWith("/annees")) {
                handleAnnees(exchange, institutionId, conn);
            } else if (path.endsWith("/periodes")) {
                handlePeriodes(exchange, institutionId, conn);
            } else if (path.endsWith("/classes")) {
                handleClasses(exchange, institutionId, conn);
            } else {
                sendJsonError(exchange, "API inconnue: " + path);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur SQL: " + e.getMessage(), e);
            sendJsonError(exchange, "Erreur base de données");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erreur API: " + e.getMessage(), e);
            sendJsonError(exchange, e.getMessage());
        }
    }

    private void handleCreneaux(HttpExchange exchange, String institutionId, Connection conn) throws IOException {
        Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
        String annee = params.get("annee");
        String periode = params.get("periode");
        String classe = params.get("classe");

        // Requête pour récupérer les créneaux avec les noms des professeurs
        StringBuilder sql = new StringBuilder("""
            SELECT pm.*, p.nom, p.prenom 
            FROM professeur_matieres pm
            LEFT JOIN professeurs p ON pm.numero_identifiant = p.numero_identifiant_professeur AND pm.institution_id = p.institution_id
            WHERE pm.institution_id = ?
            """);

        List<Object> paramsList = new ArrayList<>();
        paramsList.add(institutionId);

        if (annee != null && !annee.isEmpty()) {
            sql.append(" AND pm.annee_academique = ?");
            paramsList.add(annee);
        }
        if (periode != null && !periode.isEmpty()) {
            sql.append(" AND pm.periode = ?");
            paramsList.add(periode);
        }
        if (classe != null && !classe.isEmpty()) {
            sql.append(" AND pm.classe = ?");
            paramsList.add(classe);
        }

        sql.append(" ORDER BY pm.jour, pm.heure_debut");

        List<Map<String, Object>> data = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < paramsList.size(); i++) {
                ps.setObject(i + 1, paramsList.get(i));
            }
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("codeCours", rs.getString("code_cours"));
                map.put("nomMatiere", rs.getString("nom_matiere"));
                map.put("classe", rs.getString("classe"));
                map.put("coefficient", rs.getDouble("coefficient"));
                map.put("jour", rs.getString("jour"));
                map.put("heureDebut", rs.getString("heure_debut"));
                map.put("heureFin", rs.getString("heure_fin"));
                map.put("duree", rs.getInt("duree_minutes"));
                map.put("anneeAcademique", rs.getString("annee_academique"));
                map.put("periode", rs.getString("periode"));
                map.put("professeurNom", rs.getString("nom") + " " + rs.getString("prenom"));
                data.add(map);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur getCreneaux: {0}", e.getMessage());
            sendJsonError(exchange, e.getMessage());
            return;
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", data);
        response.put("count", data.size());
        sendJson(exchange, response);
    }

    private void handleAnnees(HttpExchange exchange, String institutionId, Connection conn) throws IOException {
        String sql = "SELECT DISTINCT annee_academique FROM annees_academiques WHERE institution_id = ? ORDER BY annee_academique DESC";
        List<String> annees = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                annees.add(rs.getString("annee_academique"));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erreur getAnnees: {0}", e.getMessage());
        }
        if (annees.isEmpty()) {
            annees.add("2024-2025");
        }
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", annees);
        sendJson(exchange, response);
    }

    private void handlePeriodes(HttpExchange exchange, String institutionId, Connection conn) throws IOException {
        Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
        String annee = params.get("annee");
        List<String> periodes = new ArrayList<>();
        if (annee != null && !annee.isEmpty()) {
            String sql = "SELECT DISTINCT periode FROM periodes WHERE institution_id = ? AND annee_academique = ? ORDER BY periode";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, institutionId);
                ps.setString(2, annee);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    periodes.add(rs.getString("periode"));
                }
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Erreur getPeriodes: {0}", e.getMessage());
            }
        }
        if (periodes.isEmpty()) {
            periodes.add("S1");
            periodes.add("S2");
        }
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", periodes);
        sendJson(exchange, response);
    }

    private void handleClasses(HttpExchange exchange, String institutionId, Connection conn) throws IOException {
        String sql = "SELECT DISTINCT nom_classe FROM classes WHERE institution_id = ? AND nom_classe IS NOT NULL AND nom_classe != '' ORDER BY nom_classe";
        List<String> classes = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                classes.add(rs.getString("nom_classe"));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erreur getClasses: {0}", e.getMessage());
        }
        if (classes.isEmpty()) {
            classes.add("Toutes");
        }
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", classes);
        sendJson(exchange, response);
    }

    // ========== MÉTHODES UTILITAIRES (inchangées) ==========
    private String getInstitutionId(HttpExchange exchange) {
        try {
            String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
            if (cookieHeader == null) return null;
            for (String cookie : cookieHeader.split(";")) {
                String[] parts = cookie.trim().split("=");
                if (parts.length == 2 && "SESSION_ID".equals(parts[0])) {
                    String sessionId = parts[1];
                    HttpSession session = SessionManager.getSession(sessionId);
                    if (session != null) {
                        String instId = (String) session.getAttribute("institutionId");
                        if (instId == null || instId.isBlank()) {
                            instId = (String) session.getAttribute("institution_id");
                        }
                        return instId;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erreur r\u00e9cup\u00e9ration institutionId: {0}", e.getMessage());
        }
        return null;
    }

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
        String json = GSON.toJson(data);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
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

    private void sendResponse(HttpExchange exchange, int statusCode, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
            os.flush();
        }
    }
}