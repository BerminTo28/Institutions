import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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

public class HandlerEtudiantBulletins implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(HandlerEtudiantBulletins.class.getName());
    private static final Gson GSON = new Gson();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        // API pour récupérer les périodes en AJAX
        if (path.equals("/etudiant/bulletins/api/periodes") && "GET".equalsIgnoreCase(method)) {
            handleApiPeriodes(exchange);
            return;
        }

        try {
            String numeroEtudiant = getNumeroEtudiantFromSession(exchange);
            String institutionId = getInstitutionIdFromSession(exchange);

            if (numeroEtudiant == null || institutionId == null) {
                exchange.getResponseHeaders().set("Location", "/connexion");
                exchange.sendResponseHeaders(302, -1);
                return;
            }

            // Vérifier les droits d'accès (bulletins)
            AccesEtudiantData accesData = new AccesEtudiantData();
            AccesEtudiant acces = accesData.getByEtudiant(numeroEtudiant, institutionId);
            if (acces == null || !acces.isBulletin()) {
                LOGGER.warning(() -> "🚫 Accès bulletins refusé pour " + numeroEtudiant
                        + " (acces=" + (acces == null ? "null" : "ok")
                        + ", bulletin=" + (acces != null ? acces.isBulletin() : "n/a") + ")");
                String html = UIEtudiantBulletins.renderError(
                        "Vous n'avez pas l'autorisation de consulter vos bulletins.");
                ResponseUtil.sendHtml(exchange, 403, html);
                return;
            }

            if ("GET".equalsIgnoreCase(method)) {
                handleGet(exchange, institutionId);
            } else if ("POST".equalsIgnoreCase(method)) {
                handlePost(exchange, numeroEtudiant, institutionId);
            } else {
                ResponseUtil.sendError(exchange, 405, "Méthode non autorisée");
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur SQL dans HandlerEtudiantBulletins", e);
            try {
                String html = UIEtudiantBulletins.renderError("Erreur base de données.");
                ResponseUtil.sendHtml(exchange, 500, html);
            } catch (IOException ignored) {
                // Client probablement déconnecté — on ignore
            }
        } catch (IOException e) {
            // ✅ Capture les autres exceptions (NumberFormat, NullPointer, etc.)
            // mais ne capture plus les IOException réseau (déjà gérées par ResponseUtil)
            String msg = e.getMessage();
            if (msg != null && (msg.contains("aborted")
                    || msg.contains("Broken pipe")
                    || msg.contains("Connection reset"))) {
                LOGGER.fine(() -> "Client déconnecté : " + msg);
                return;
            }
            LOGGER.log(Level.SEVERE, "Erreur inattendue dans HandlerEtudiantBulletins", e);
            try {
                String html = UIEtudiantBulletins.renderError("Erreur interne.");
                ResponseUtil.sendHtml(exchange, 500, html);
            } catch (IOException ignored) {
                // ignore
            }
        }
    }

    // ============================================================
    // API PERIODES (AJAX)
    // ============================================================
    private void handleApiPeriodes(HttpExchange exchange) {
        String query = exchange.getRequestURI().getQuery();
        Map<String, String> params = parseQueryParams(query);
        String annee = params.get("annee");
        String institutionId = getInstitutionIdFromSession(exchange);

        List<String> periodes = new ArrayList<>();
        if (annee != null && !annee.isBlank() && institutionId != null) {
            try (Connection conn = DatabaseManager.getInstance().getConnection()) {
                periodes = getPeriodes(conn, institutionId, annee);
            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "Erreur chargement périodes", e);
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", periodes);
        String json = GSON.toJson(response);
        try {
            ResponseUtil.sendJson(exchange, 200, json);
        } catch (IOException e) {

        }
    }

    // ============================================================
    // GET – Affiche le formulaire
    // ============================================================
   private void handleGet(HttpExchange exchange, String institutionId) throws SQLException {
    Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
    String annee = params.getOrDefault("annee", "");
    List<String> periodes = new ArrayList<>();

    if (!annee.isEmpty()) {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            periodes = getPeriodes(conn, institutionId, annee);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur chargement périodes", e);
        }
    }

    try (Connection conn = DatabaseManager.getInstance().getConnection()) {
        List<String> annees = getAnneesAcademiques(conn, institutionId);
        String html = UIEtudiantBulletins.render(annees, periodes, annee, null, null, null);
        try {
            ResponseUtil.sendHtml(exchange, 200, html);
        } catch (IOException e) {
            LOGGER.fine(() -> "Client déconnecté : " + e.getMessage());
        }
    } catch (SQLException e) {
        LOGGER.log(Level.SEVERE, "Erreur SQL", e);
        String html = UIEtudiantBulletins.renderError("Erreur base de données");
        try {
            ResponseUtil.sendHtml(exchange, 500, html);
        } catch (IOException e1) {
            LOGGER.fine(() -> "Client déconnecté : " + e1.getMessage());
        }
    }
}
    // ============================================================
    // POST – Génération du bulletin multi-sessions
    // ============================================================
    private void handlePost(HttpExchange exchange, String numeroEtudiant,
                             String institutionId) throws IOException, SQLException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, List<String>> params = parseFormDataMulti(body);
        List<String> periodes = params.getOrDefault("periodes", new ArrayList<>());
        String annee = params.containsKey("annee") && !params.get("annee").isEmpty()
                ? params.get("annee").get(0) : null;

        if (annee == null || annee.isBlank() || periodes.isEmpty()) {
            try (Connection conn = DatabaseManager.getInstance().getConnection()) {
                List<String> annees = getAnneesAcademiques(conn, institutionId);
                List<String> periodesDispo = getPeriodes(conn, institutionId, annee);
                String html = UIEtudiantBulletins.render(annees, periodesDispo, annee, periodes,
                        "Veuillez sélectionner une année et au moins une période.", null);
                ResponseUtil.sendHtml(exchange, 400, html);
            }
            return;
        }

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            // 1. Récupérer l'étudiant
            EtudiantData etudiantData = new EtudiantData(conn);
            Etudiant etudiant = etudiantData.read(numeroEtudiant,institutionId);
            if (etudiant == null) {
                throw new SQLException("Étudiant non trouvé");
            }

            // 2. Générer le bulletin multi-sessions via BulletinController
            BulletinController controller = new BulletinController(institutionId, conn);
            String classe = etudiant.getClasse();
            String numero = "";
            String reference = "";

            String nomFichier = controller.genererBulletinMultiSessionPDF(
                    numeroEtudiant,
                    annee,
                    periodes.toArray(String[]::new),
                    classe,
                    numero,
                    reference
            );

            if (nomFichier == null || nomFichier.isEmpty()) {
                throw new SQLException("Échec de la génération du bulletin");
            }

            // 3. Récupérer le fichier généré
            File bulletinsDir = new File("./bulletins_" + institutionId);
            File pdfFile = new File(bulletinsDir, nomFichier);
            if (!pdfFile.exists()) {
                File downloadsDir = getDownloadsDirectory();
                File altFile = new File(downloadsDir, nomFichier);
                if (altFile.exists()) {
                    pdfFile = altFile;
                } else {
                    throw new SQLException("Fichier PDF introuvable: " + nomFichier);
                }
            }

            // 4. Envoyer le PDF
            byte[] pdfBytes = Files.readAllBytes(pdfFile.toPath());
            exchange.getResponseHeaders().set("Content-Type", "application/pdf");
            exchange.getResponseHeaders().set("Content-Disposition",
                    "attachment; filename=\"" + nomFichier + "\"");
            exchange.sendResponseHeaders(200, pdfBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(pdfBytes);
                os.flush();
            }

        } catch (Exception e) {
            // ✅ Erreur de génération → afficher la page avec message d'erreur
            String msg = e.getMessage();
            if (msg != null && (msg.contains("aborted")
                    || msg.contains("Broken pipe")
                    || msg.contains("Connection reset"))) {
                LOGGER.fine(() -> "Client déconnecté pendant la génération : " + msg);
                return;
            }
            LOGGER.log(Level.SEVERE, "Erreur génération bulletin", e);

            try (Connection conn = DatabaseManager.getInstance().getConnection()) {
                List<String> annees = getAnneesAcademiques(conn, institutionId);
                List<String> periodesDispo = getPeriodes(conn, institutionId, annee);
                String html = UIEtudiantBulletins.render(annees, periodesDispo, annee, periodes,
                        "Erreur: " + msg, null);
                ResponseUtil.sendHtml(exchange, 500, html);
            } catch (SQLException ex) {
                String html = UIEtudiantBulletins.renderError("Erreur interne");
                ResponseUtil.sendHtml(exchange, 500, html);
            }
        }
    }

    // ============================================================
    // MÉTHODES DE RÉCUPÉRATION DES DONNÉES
    // ============================================================

    private List<String> getAnneesAcademiques(Connection conn, String institutionId) throws SQLException {
        List<String> annees = new ArrayList<>();
        String sql = "SELECT annee_academique FROM " + MigrationManager.TABLE_ANNEES_ACADEMIQUES +
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

    private List<String> getPeriodes(Connection conn, String institutionId,
                                       String annee) throws SQLException {
        List<String> periodes = new ArrayList<>();
        String sql = "SELECT periode FROM " + MigrationManager.TABLE_PERIODES +
                     " WHERE institution_id = ? AND annee_academique = ? ORDER BY periode";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            ps.setString(2, annee);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    periodes.add(rs.getString("periode"));
                }
            }
        }
        return periodes;
    }

    private File getDownloadsDirectory() {
        String userHome = System.getProperty("user.home");
        String os = System.getProperty("os.name").toLowerCase();
        String downloadsPath;
        if (os.contains("win")) {
            downloadsPath = userHome + "\\Downloads";
        } else {
            downloadsPath = userHome + "/Downloads";
        }
        File dir = new File(downloadsPath);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    // ============================================================
    // SESSION
    // ============================================================

    private String getNumeroEtudiantFromSession(HttpExchange exchange) {
        try {
            String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
            if (cookieHeader == null) return null;
            String sessionId = extractSessionId(cookieHeader);
            if (sessionId == null) return null;
            HttpSession session = SessionManager.getSession(sessionId);
            if (session == null) return null;
            return (String) session.getAttribute("userId");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Impossible de récupérer l'ID étudiant", e);
            return null;
        }
    }

    private String getInstitutionIdFromSession(HttpExchange exchange) {
        try {
            String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
            if (cookieHeader == null) return null;
            String sessionId = extractSessionId(cookieHeader);
            if (sessionId == null) return null;
            HttpSession session = SessionManager.getSession(sessionId);
            if (session == null) return null;
            String instId = (String) session.getAttribute("institutionId");
            if (instId == null || instId.isBlank()) {
                instId = (String) session.getAttribute("institution_id");
            }
            return instId;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Impossible de récupérer l'institutionId", e);
            return null;
        }
    }

    private String extractSessionId(String cookieHeader) {
        for (String cookie : cookieHeader.split(";")) {
            String[] parts = cookie.trim().split("=");
            if (parts.length == 2 && "SESSION_ID".equals(parts[0])) {
                return parts[1];
            }
        }
        return null;
    }

    // ============================================================
    // PARSING
    // ============================================================

    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null) return params;
        for (String pair : query.split("&")) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                try {
                    String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                    String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                    params.put(key, value);
                } catch (Exception ignored) {}
            }
        }
        return params;
    }

    private Map<String, List<String>> parseFormDataMulti(String body) {
        Map<String, List<String>> params = new HashMap<>();
        if (body == null || body.isBlank()) return params;
        for (String pair : body.split("&")) {
            int idx = pair.indexOf("=");
            try {
                String key = (idx > 0)
                        ? URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8)
                        : URLDecoder.decode(pair, StandardCharsets.UTF_8);
                String value = (idx > 0)
                        ? URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8) : "";
                params.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
            } catch (Exception ignored) {}
        }
        return params;
    }
}