import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.itextpdf.text.DocumentException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class HandlerPalmares implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(HandlerPalmares.class.getName());
    private static final Gson GSON = new Gson();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        LOGGER.info(() -> "PALMARES HANDLER - " + method + " " + path);

        // Déclaration pour le catch
        String institutionId = null;
        String utilisateurId ;

        try {
            // 1. Récupération de l'institution et de l'utilisateur depuis la session
            institutionId = getInstitutionIdFromSession(exchange);
            utilisateurId = getUtilisateurIdFromSession(exchange);

            // Redirection si session invalide
            if (institutionId == null || institutionId.isBlank() || utilisateurId == null || utilisateurId.isBlank()) {
                LOGGER.warning("⛔ Session invalide - Redirection vers login");
                exchange.getResponseHeaders().set("Location", "/login?error=Session+expirée");
                exchange.sendResponseHeaders(302, -1);
                return;
            }

            // 2. Chargement des permissions
            AccesAdmin acces = chargerPermissions(utilisateurId, institutionId);
            if (acces == null) {
                LOGGER.warning(() -> "⛔ Permissions non trouvées pour " + utilisateurId);
                exchange.getResponseHeaders().set("Location", "/dashboard?error=Permissions+non+trouvées");
                exchange.sendResponseHeaders(302, -1);
                return;
            }

            // Vérification READ pour accéder à la page (et aux API de consultation)
            if (!acces.hasPermission(AccesAdmin.Module.PALMARES, AccesAdmin.Action.READ)) {
                LOGGER.warning("⛔ Accès refusé - Pas de READ sur PALMARES");
                exchange.getResponseHeaders().set("Location", "/dashboard?error=Accès+non+autorisé");
                exchange.sendResponseHeaders(302, -1);
                return;
            }

            // Gestion des API
            if (path.startsWith("/admin/palmares/api/")) {
                handleApi(exchange, institutionId, acces, path);
                return;
            }

            // Page HTML : on récupère les droits d'export (CREATE)
            boolean canExport = acces.hasPermission(AccesAdmin.Module.PALMARES, AccesAdmin.Action.CREATE);
            String html = UIPalmares.renderPage(institutionId, canExport);
            sendResponse(exchange, 200, html);

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Erreur", e);
            String instId = (institutionId != null && !institutionId.isBlank()) ? institutionId : "ERR";
            String html = UIPalmares.renderPage(instId, false);
            sendResponse(exchange, 500, html);
        }
    }

    private void handleApi(HttpExchange exchange, String institutionId, AccesAdmin acces, String path) throws IOException {
        String method = exchange.getRequestMethod();

        if ("OPTIONS".equals(method)) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            PalmaresService service = new PalmaresService(conn, institutionId);

            if (path.endsWith("/data") || path.endsWith("/annees") || path.endsWith("/sessions") || path.endsWith("/classes")) {
                // Ces API de consultation nécessitent READ (déjà vérifié dans handle, mais on double-vérifie)
                if (!acces.hasPermission(AccesAdmin.Module.PALMARES, AccesAdmin.Action.READ)) {
                    sendJsonError(exchange, "Permission READ refusée");
                    return;
                }
                if (path.endsWith("/data")) {
                    handleData(exchange, service);
                } else if (path.endsWith("/annees")) {
                    handleAnnees(exchange, service);
                } else if (path.endsWith("/sessions")) {
                    handleSessions(exchange, service);
                } else if (path.endsWith("/classes")) {
                    handleClasses(exchange, service);
                }
                return;
            }

            // Exportations : nécessitent CREATE
            if (path.endsWith("/export/pdf") || path.endsWith("/export/excel")) {
                if (!acces.hasPermission(AccesAdmin.Module.PALMARES, AccesAdmin.Action.CREATE)) {
                    sendJsonError(exchange, "Permission CREATE requise pour exporter");
                    return;
                }
                if (path.endsWith("/export/pdf")) {
                    handleExportPDF(exchange, service);
                } else if (path.endsWith("/export/excel")) {
                    handleExportExcel(exchange, service);
                }
                return;
            }

            sendJsonError(exchange, "API inconnue: " + path);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur SQL: " + e.getMessage(), e);
            sendJsonError(exchange, "Erreur base de données");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erreur API: " + e.getMessage(), e);
            sendJsonError(exchange, e.getMessage());
        }
    }

    // ============================================================
    // API : DONNÉES
    // ============================================================
    private void handleData(HttpExchange exchange, PalmaresService service) throws IOException {
        Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
        String annee = params.get("annee");
        String session = params.get("session");
        String classe = params.get("classe");

        if (annee == null || session == null || classe == null) {
            sendJsonError(exchange, "Paramètres manquants: annee, session, classe requis");
            return;
        }

        LOGGER.info(() -> "📊 Récupération palmarès - Année: " + annee + ", Session: " + session + ", Classe: " + classe);

        try {
            List<PalmaresEntry> entries = service.getPalmaresDetail(annee, session, classe);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", entries);
            response.put("count", entries != null ? entries.size() : 0);
            sendJson(exchange, response);
        } catch (SQLException e) {
            LOGGER.severe(() -> "Erreur récupération palmarès: " + e.getMessage());
            sendJsonError(exchange, e.getMessage());
        }
    }

    private void handleExportPDF(HttpExchange exchange, PalmaresService service) throws IOException {
        Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
        String annee = params.get("annee");
        String session = params.get("session");
        String classe = params.get("classe");

        if (annee == null || session == null || classe == null) {
            sendJsonError(exchange, "Paramètres manquants");
            return;
        }

        LOGGER.info(() -> "📄 Export PDF - Année: " + annee + ", Session: " + session + ", Classe: " + classe);

        try {
            List<PalmaresEntry> entries = service.getPalmaresDetail(annee, session, classe);
            Institution institution = service.getInstitution();
            byte[] pdfBytes = PalmaresPDFExporter.export(entries, institution, annee, session, classe);

            exchange.getResponseHeaders().set("Content-Type", "application/pdf");
            exchange.getResponseHeaders().set("Content-Disposition",
                "attachment; filename=\"palmares_" + classe + "_" + session + ".pdf\"");
            exchange.sendResponseHeaders(200, pdfBytes.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(pdfBytes);
                os.flush();
            }
        } catch (DocumentException | IOException | SQLException e) {
            LOGGER.severe(() -> "Erreur export PDF: " + e.getMessage());
            sendJsonError(exchange, e.getMessage());
        }
    }

    private void handleExportExcel(HttpExchange exchange, PalmaresService service) throws IOException {
        Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
        String annee = params.get("annee");
        String session = params.get("session");
        String classe = params.get("classe");

        if (annee == null || session == null || classe == null) {
            sendJsonError(exchange, "Paramètres manquants");
            return;
        }

        LOGGER.info(() -> "📊 Export Excel - Année: " + annee + ", Session: " + session + ", Classe: " + classe);

        try {
            List<PalmaresEntry> entries = service.getPalmaresDetail(annee, session, classe);
            Institution institution = service.getInstitution();
            byte[] excelBytes = PalmaresExcelExporter.export(entries, institution, annee, session, classe);

            exchange.getResponseHeaders().set("Content-Type",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            exchange.getResponseHeaders().set("Content-Disposition",
                "attachment; filename=\"palmares_" + classe + "_" + session + ".xlsx\"");
            exchange.sendResponseHeaders(200, excelBytes.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(excelBytes);
                os.flush();
            }
        } catch (IOException | SQLException e) {
            LOGGER.severe(() -> "Erreur export Excel: " + e.getMessage());
            sendJsonError(exchange, e.getMessage());
        }
    }

    private void handleAnnees(HttpExchange exchange, PalmaresService service) throws IOException {
        List<String> annees = service.getAnneesAcademiques();
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", annees);
        sendJson(exchange, result);
    }

    private void handleSessions(HttpExchange exchange, PalmaresService service) throws IOException {
        Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
        String annee = params.get("annee");
        List<String> sessions = service.getSessions(annee);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", sessions);
        sendJson(exchange, result);
    }

    private void handleClasses(HttpExchange exchange, PalmaresService service) throws IOException {
        List<String> classes = service.getClasses();
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", classes);
        sendJson(exchange, result);
    }

    // ============================================================
    // CHARGEMENT DES PERMISSIONS
    // ============================================================
    private AccesAdmin chargerPermissions(String utilisateurId, String institutionId) {
    try (Connection conn = DatabaseManager.getInstance().getConnection()) {
        AccesAdminData dao = new AccesAdminData(conn);
        return dao.loadByUtilisateurId(utilisateurId, institutionId);   // ✅
    } catch (SQLException e) {
        LOGGER.log(Level.WARNING, "Erreur chargement permissions", e);
        return new AccesAdmin();
    }
}

    // ============================================================
    // RÉCUPÉRATION SESSION
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
            LOGGER.warning(() -> "Erreur récupération institutionId: " + e.getMessage());
            return null;
        }
    }

    private String getUtilisateurIdFromSession(HttpExchange exchange) {
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
            String userId = (String) session.getAttribute("userId");
            if (userId == null || userId.isBlank()) {
                userId = (String) session.getAttribute("username");
            }
            return userId;
        } catch (Exception e) {
            LOGGER.warning(() -> "Erreur récupération utilisateurId: " + e.getMessage());
            return null;
        }
    }

    // ============================================================
    // UTILITAIRES
    // ============================================================
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