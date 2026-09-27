import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Year;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.ImageIO;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.itextpdf.awt.PdfGraphics2D;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfTemplate;
import com.itextpdf.text.pdf.PdfWriter;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class HandlerAttestations implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(HandlerAttestations.class.getName());
    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        LOGGER.info(() -> "📄 ATTESTATIONS HANDLER - " + method + " " + path);

        // Déclaration en dehors du try pour être accessible dans le catch
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
            AccesAdmin acces = chargerPermissions(utilisateurId,institutionId);
            if (acces == null) {
                LOGGER.log(Level.WARNING, "\u26d4 Permissions non trouv\u00e9es pour {0}", utilisateurId);
                exchange.getResponseHeaders().set("Location", "/dashboard?error=Permissions+non+trouvées");
                exchange.sendResponseHeaders(302, -1);
                return;
            }

            // Vérification de l'accès en lecture au module ATTESTATION (pour toute requête)
            if (!acces.hasPermission(AccesAdmin.Module.ATTESTATION, AccesAdmin.Action.READ)) {
                LOGGER.warning("⛔ Accès refusé - Pas de READ sur ATTESTATION");
                exchange.getResponseHeaders().set("Location", "/dashboard?error=Accès+non+autorisé");
                exchange.sendResponseHeaders(302, -1);
                return;
            }

            // Gestion des API
            if (path.startsWith("/admin/attestations/api/")) {
                handleApi(exchange, institutionId, acces, path);
                return;
            }

            // Pages HTML (formulaire)
            if (path.equals("/admin/attestations/form") || path.equals("/admin/attestations/nouveau")) {
                String html = renderFormPage(institutionId);
                sendResponse(exchange, 200, html);
                return;
            }

            // Redirection par défaut
            exchange.getResponseHeaders().set("Location", "/admin/attestations/form");
            exchange.sendResponseHeaders(302, -1);

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, e, () -> "Erreur SQL");
            if (path.startsWith("/admin/attestations/api/")) {
                sendJson(exchange, Map.of("success", false, "error", "Erreur base de données: " + e.getMessage()));
            } else {
                String instId = (institutionId != null && !institutionId.isBlank()) ? institutionId : "ERR";
                sendResponse(exchange, 500, renderErrorPage(instId, "Erreur base de données: " + e.getMessage()));
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, e, () -> "Erreur générale");
            if (path.startsWith("/admin/attestations/api/")) {
                sendJson(exchange, Map.of("success", false, "error", e.getMessage()));
            } else {
                String instId = (institutionId != null && !institutionId.isBlank()) ? institutionId : "ERR";
                sendResponse(exchange, 500, renderErrorPage(instId, "Erreur: " + e.getMessage()));
            }
        }
    }

    // ============================================================
    // PAGE DU FORMULAIRE
    // ============================================================
    private String renderFormPage(String institutionId) throws SQLException {
        List<Map<String, String>> promotions = getPromotions(institutionId);
        String selectedPromo = promotions.isEmpty() ? "" : promotions.get(0).get("promotion");
        String numero = AttestationDAO.genererNumero(institutionId);
        String reference = "ATT-" + System.currentTimeMillis();

        return UIAttestation.renderPage(
                institutionId,
                promotions,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                selectedPromo,
                "",
                "",
                "",
                "",
                "",
                reference,
                numero,
                new java.text.SimpleDateFormat("yyyy-MM-dd").format(new Date()),
                "",
                null
        );
    }

    private String renderErrorPage(String institutionId, String message) {
        return """
        <!DOCTYPE html>
        <html>
        <head><meta charset='UTF-8'><title>Erreur</title>
        <style>
        body{font-family:sans-serif;padding:40px;background:#f5f6fa;text-align:center}
        .box{background:#fff;padding:40px;border-radius:12px;max-width:600px;margin:auto}
        h1{color:#e74c3c}
        </style>
        </head>
        <body>
        <div class='box'>
        <h1>⚠️ Erreur</h1>
        <p>%s</p>
        <p><small>Institution : %s</small></p>
        <a href='/admin/attestations/form' style='display:inline-block;padding:10px 20px;background:#2563eb;color:#fff;text-decoration:none;border-radius:8px;'>Réessayer</a>
        </div>
        </body>
        </html>
        """.formatted(message, institutionId != null ? institutionId : "non définie");
    }

    // ============================================================
    // GESTION DES API
    // ============================================================
    private void handleApi(HttpExchange exchange, String institutionId, AccesAdmin acces, String path)
            throws IOException, SQLException {
        String method = exchange.getRequestMethod();

        if ("OPTIONS".equals(method)) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        if (path.startsWith("/admin/attestations/api/listes/")) {
            // READ déjà vérifié
            handleListesApi(exchange, institutionId, path);
            return;
        }

        switch (path) {
            case "/admin/attestations/api/list" -> {
                // READ déjà vérifié
                handleList(exchange, institutionId);
            }
            case "/admin/attestations/api/save" -> {
                // CREATE pour sauvegarder
                if (!acces.hasPermission(AccesAdmin.Module.ATTESTATION, AccesAdmin.Action.CREATE)) {
                    sendJson(exchange, Map.of("success", false, "error", "Permission CREATE refusée"));
                    return;
                }
                handleSave(exchange, institutionId);
            }
            case "/admin/attestations/api/delete" -> {
                // DELETE pour supprimer
                if (!acces.hasPermission(AccesAdmin.Module.ATTESTATION, AccesAdmin.Action.DELETE)) {
                    sendJson(exchange, Map.of("success", false, "error", "Permission DELETE refusée"));
                    return;
                }
                handleDelete(exchange, institutionId);
            }
            case "/admin/attestations/api/preview" -> {
                // READ suffit pour l'aperçu
                handlePreview(exchange, institutionId);
            }
            case "/admin/attestations/api/print" -> {
                // L'impression nécessite la permission UPDATE (pour restreindre aux utilisateurs autorisés)
                if (!acces.hasPermission(AccesAdmin.Module.ATTESTATION, AccesAdmin.Action.UPDATE)) {
                    sendJson(exchange, Map.of("success", false, "error", "Permission UPDATE requise pour imprimer"));
                    return;
                }
                handlePrint(exchange, institutionId);
            }
            default -> sendJson(exchange, Map.of("success", false, "error", "API inconnue: " + path));
        }
    }
    
 private void handlePrint(HttpExchange exchange, String institutionId) throws IOException {
    if (!"POST".equals(exchange.getRequestMethod())) {
        sendJson(exchange, Map.of("success", false, "error", "Méthode non autorisée"));
        return;
    }

    try {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

        // ---------- Parsing de la requête ----------
        Map<String, Object> data = null;
        try {
            data = GSON.fromJson(body, MAP_TYPE);
        } catch (JsonSyntaxException e) {
            Map<String, String> formParams = parseQueryParams(body);
            if (!formParams.isEmpty()) {
                if (formParams.containsKey("data")) {
                    try {
                        data = GSON.fromJson(formParams.get("data"), MAP_TYPE);
                    } catch (JsonSyntaxException ignored) {
                        data = new HashMap<>(formParams);
                    }
                } else {
                    data = new HashMap<>(formParams);
                }
            }
        }

        if (data == null || data.isEmpty()) {
            Map<String, String> queryParams = parseQueryParams(exchange.getRequestURI().getQuery());
            if (!queryParams.isEmpty()) {
                data = new HashMap<>(queryParams);
            }
        }

        if (data == null || data.isEmpty()) {
            sendJson(exchange, Map.of("success", false, "error", "Données manquantes ou invalides"));
            return;
        }

        String etudiantId = (String) data.get("etudiantId");
        String type = (String) data.get("type");
        String reference = (String) data.get("reference");
        String numero = (String) data.get("numero");
        String dateStr = (String) data.get("date");
        String option = (String) data.get("option");
        String classe = (String) data.get("classe");
        String anneeAcademique = (String) data.get("anneeAcademique");

        if (etudiantId == null || type == null) {
            sendJson(exchange, Map.of("success", false, "error", "Étudiant et type requis"));
            return;
        }

        Date date = new Date();
        if (dateStr != null && !dateStr.isEmpty()) {
            try { date = java.sql.Date.valueOf(dateStr); } catch (Exception ignored) {}
        }

        Etudiant etudiant = AttestationDAO.getEtudiant(etudiantId, institutionId);
        Institution institution = AttestationDAO.getInstitution(institutionId);
        if (etudiant == null || institution == null) {
            sendJson(exchange, Map.of("success", false, "error", "Données manquantes (étudiant ou institution)"));
            return;
        }

        if (classe == null || classe.isEmpty()) classe = etudiant.getClasse();
        if (anneeAcademique == null || anneeAcademique.isEmpty()) anneeAcademique = etudiant.getAnneeAcademique();

        // ---------- Génération de l'image AWT ----------
        AttestationGenerator generator = new AttestationGenerator(
                etudiant, institution, type, reference, numero, date, option, classe, anneeAcademique
        );
        java.awt.image.BufferedImage image =
                new java.awt.image.BufferedImage(595, 842, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g2 = image.createGraphics();
        g2.setColor(java.awt.Color.WHITE);
        g2.fillRect(0, 0, 595, 842);
        generator.drawDocument(g2);
        g2.dispose();

        // ---------- Génération du PDF (DocumentException gérée ICI) ----------
        ByteArrayOutputStream pdfBaos = new ByteArrayOutputStream();
        try {
            Document document = new Document(PageSize.A4, 30, 30, 25, 25);
            PdfWriter writer = PdfWriter.getInstance(document, pdfBaos);   // ⚠️ DocumentException
            document.open();                                                // ⚠️ DocumentException

            PdfContentByte cb = writer.getDirectContent();
            PdfTemplate template = cb.createTemplate(PageSize.A4.getWidth(), PageSize.A4.getHeight());
            java.awt.Graphics2D pdfG2 = new PdfGraphics2D(
                    template, PageSize.A4.getWidth(), PageSize.A4.getHeight()
            );
            pdfG2.drawImage(image, 0, 0, null);
            pdfG2.dispose();

            cb.addTemplate(template, 0, 0);
            document.close();                                               // ⚠️ DocumentException
        } catch (DocumentException de) {
            // ✅ On ne propage PAS DocumentException
            LOGGER.log(Level.SEVERE, de, () -> "Erreur génération PDF");
            sendJson(exchange, Map.of(
                    "success", false,
                    "error", "Erreur PDF: " + de.getMessage()
            ));
            return;
        }

        byte[] pdfBytes = pdfBaos.toByteArray();

        exchange.getResponseHeaders().set("Content-Type", "application/pdf");
        exchange.getResponseHeaders().set(
                "Content-Disposition",
                "attachment; filename=\"attestation.pdf\""
        );
        exchange.sendResponseHeaders(200, pdfBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(pdfBytes);
        }

    } catch (IOException e) {
        LOGGER.log(Level.SEVERE, e, () -> "Erreur impression");
        sendJson(exchange, Map.of("success", false, "error", e.getMessage()));
    }
}
    // ============================================================
    // API : LISTES DYNAMIQUES (CASCADE)
    // ============================================================
    private void handleListesApi(HttpExchange exchange, String institutionId, String path) throws IOException, SQLException {
        Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
        LOGGER.info(() -> "📥 Paramètres reçus: " + params);

        String promo = params.get("promo");
        String annee = params.get("annee");
        String periode = params.get("periode");
        String classe = params.get("classe");
        List<?> result;

        if (path.endsWith("/promotions")) {
            result = getPromotions(institutionId);
        } else if (path.endsWith("/annees")) {
            result = getAnnees(institutionId);
        } else if (path.endsWith("/periodes")) {
            result = getPeriodes(institutionId, annee);
        } else if (path.endsWith("/classes")) {
            result = getClassesFiltrees(institutionId, promo, annee, periode);
        } else if (path.endsWith("/etudiants")) {
            result = getEtudiantsFiltres(institutionId, promo, annee, periode, classe);
        } else {
            sendJson(exchange, Map.of("success", false, "error", "Liste inconnue"));
            return;
        }

        LOGGER.info(() -> "📤 Résultat envoyé: " + result.size() + " éléments");
        Map<String, Object> json = new HashMap<>();
        json.put("success", true);
        json.put("data", result);
        sendJson(exchange, json);
    }

    // ============================================================
    // REQUÊTES SQL AVEC FALLBACKS
    // ============================================================

    private List<Map<String, String>> getPromotions(String institutionId) throws SQLException {
        List<Map<String, String>> list = new ArrayList<>();
        String sql = "SELECT DISTINCT promotion FROM promotions WHERE institution_id = ? AND promotion IS NOT NULL AND promotion != '' ORDER BY promotion";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, String> m = new HashMap<>();
                    m.put("promotion", rs.getString("promotion"));
                    list.add(m);
                }
            }
        }
        if (list.isEmpty()) {
            String defaultPromo = Year.now().getValue() + "-" + (Year.now().getValue() + 1);
            Map<String, String> m = new HashMap<>();
            m.put("promotion", defaultPromo);
            list.add(m);
            LOGGER.info(() -> "🔍 Aucune promotion trouvée dans promotions, utilisation: " + defaultPromo);
        }
        return list;
    }

    private List<Map<String, String>> getAnnees(String institutionId) throws SQLException {
        List<Map<String, String>> list = new ArrayList<>();
        String sql = "SELECT DISTINCT annee_academique FROM " + MigrationManager.TABLE_ANNEES_ACADEMIQUES +
                     " WHERE institution_id = ? AND annee_academique IS NOT NULL AND annee_academique != '' ORDER BY annee_academique DESC";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, String> m = new HashMap<>();
                    m.put("annee", rs.getString("annee_academique"));
                    list.add(m);
                }
            }
        }
        if (list.isEmpty()) {
            String defaultAnnee = Year.now().getValue() + "-" + (Year.now().getValue() + 1);
            Map<String, String> m = new HashMap<>();
            m.put("annee", defaultAnnee);
            list.add(m);
            LOGGER.info(() -> "🔍 Aucune année trouvée dans annees_academiques, utilisation: " + defaultAnnee);
        }
        return list;
    }

    private List<Map<String, String>> getPeriodes(String institutionId, String annee) throws SQLException {
        List<Map<String, String>> list = new ArrayList<>();
        String sql;
        if (annee != null && !annee.isEmpty()) {
            sql = "SELECT DISTINCT periode FROM periodes WHERE institution_id = ? AND annee_academique = ? AND periode IS NOT NULL AND periode != '' ORDER BY periode";
        } else {
            sql = "SELECT DISTINCT periode FROM periodes WHERE institution_id = ? AND periode IS NOT NULL AND periode != '' ORDER BY periode";
        }
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            if (annee != null && !annee.isEmpty()) {
                ps.setString(2, annee);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, String> m = new HashMap<>();
                    m.put("periode", rs.getString("periode"));
                    list.add(m);
                }
            }
        }
        if (list.isEmpty()) {
            Map<String, String> m = new HashMap<>();
            m.put("periode", "S1");
            list.add(m);
            LOGGER.info(() -> "🔍 Aucune période trouvée dans la table periodes, utilisation: S1");
        }
        return list;
    }

    private List<Map<String, String>> getClassesFiltrees(String institutionId, String promo, String annee, String periode) throws SQLException {
        List<Map<String, String>> list = new ArrayList<>();
        if (promo == null || promo.isEmpty() || annee == null || annee.isEmpty() || periode == null || periode.isEmpty()) {
            String sql = "SELECT DISTINCT nom_classe FROM " + MigrationManager.TABLE_CLASSES +
                         " WHERE institution_id = ? AND nom_classe IS NOT NULL AND nom_classe != '' ORDER BY nom_classe";
            try (Connection conn = DatabaseManager.getInstance().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, institutionId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, String> m = new HashMap<>();
                        m.put("classe", rs.getString("nom_classe"));
                        list.add(m);
                    }
                }
            }
        } else {
            String sql = "SELECT DISTINCT classe FROM " + MigrationManager.TABLE_ETUDIANTS +
                         " WHERE institution_id = ? AND promotion = ? AND annee_academique = ? AND periode = ? AND classe IS NOT NULL AND classe != '' ORDER BY classe";
            try (Connection conn = DatabaseManager.getInstance().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, institutionId);
                ps.setString(2, promo);
                ps.setString(3, annee);
                ps.setString(4, periode);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, String> m = new HashMap<>();
                        m.put("classe", rs.getString("classe"));
                        list.add(m);
                    }
                }
            }
        }
        if (list.isEmpty()) {
            Map<String, String> m = new HashMap<>();
            m.put("classe", "6ème");
            list.add(m);
            LOGGER.info(() -> "🔍 Aucune classe trouvée, utilisation: 6ème");
        }
        return list;
    }

    private List<Map<String, Object>> getEtudiantsFiltres(String institutionId, String promo, String annee, String periode, String classe) throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        if (promo == null || promo.isEmpty() || annee == null || annee.isEmpty() ||
            periode == null || periode.isEmpty() || classe == null || classe.isEmpty()) {
            String sql = "SELECT numero_identifiant, nom, prenom FROM " + MigrationManager.TABLE_ETUDIANTS +
                         " WHERE institution_id = ? ORDER BY nom, prenom";
            try (Connection conn = DatabaseManager.getInstance().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, institutionId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> m = new HashMap<>();
                        m.put("id", rs.getString("numero_identifiant"));
                        m.put("nom", rs.getString("nom"));
                        m.put("prenom", rs.getString("prenom"));
                        list.add(m);
                    }
                }
            }
        } else {
            String sql = "SELECT numero_identifiant, nom, prenom FROM " + MigrationManager.TABLE_ETUDIANTS +
                         " WHERE institution_id = ? AND promotion = ? AND annee_academique = ? AND periode = ? AND classe = ? ORDER BY nom, prenom";
            try (Connection conn = DatabaseManager.getInstance().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, institutionId);
                ps.setString(2, promo);
                ps.setString(3, annee);
                ps.setString(4, periode);
                ps.setString(5, classe);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> m = new HashMap<>();
                        m.put("id", rs.getString("numero_identifiant"));
                        m.put("nom", rs.getString("nom"));
                        m.put("prenom", rs.getString("prenom"));
                        list.add(m);
                    }
                }
            }
        }
        LOGGER.info(() -> "🔍 Étudiants trouvés: " + list.size());
        return list;
    }

    // ============================================================
    // API : LISTE DES ATTESTATIONS
    // ============================================================
    private void handleList(HttpExchange exchange, String institutionId) throws IOException {
        Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
        String etudiant = params.get("etudiant");
        String annee = params.get("annee");
        String periode = params.get("periode");
        String classe = params.get("classe");
        List<Attestation> list = AttestationDAO.getAllWithFilters(institutionId, etudiant, annee, null, classe, periode);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("attestations", list);
        sendJson(exchange, result);
    }

    // ============================================================
    // API : SAUVEGARDE (nécessite CREATE)
    // ============================================================
  @SuppressWarnings("UseSpecificCatch")
private void handleSave(HttpExchange exchange, String institutionId) throws IOException {
    if (!"POST".equals(exchange.getRequestMethod())) {
        sendJson(exchange, Map.of("success", false, "error", "Méthode non autorisée"));
        return;
    }
    try {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, Object> data = GSON.fromJson(body, MAP_TYPE);

        // ✅ id est un String (UUID) — plus de Integer.parseInt
        String id = data.get("id") != null ? data.get("id").toString().trim() : null;
        if (id != null && id.isEmpty()) id = null;

        String etudiantId = (String) data.get("etudiantId");
        String type = (String) data.get("type");
        String reference = (String) data.get("reference");
        String numero = (String) data.get("numero");
        String classe = (String) data.get("classe");
        String anneeAcademique = (String) data.get("anneeAcademique");
        String option = (String) data.get("option");
        String periode = (String) data.get("periode");
        String session = (String) data.get("session");
        String dateEmissionStr = (String) data.get("dateEmission");

        if (etudiantId == null || type == null) {
            sendJson(exchange, Map.of("success", false, "error", "Étudiant et type sont obligatoires"));
            return;
        }

        Attestation att = new Attestation();
        att.setInstitutionId(institutionId);
        att.setEtudiantId(etudiantId);
        att.setType(type);
        att.setReference(reference);
        att.setNumero(numero);
        att.setClasse(classe);
        att.setAnneeAcademique(anneeAcademique);
        att.setOption(option);
        att.setPeriode(periode);
        att.setSession(session);

        if (dateEmissionStr != null && !dateEmissionStr.isEmpty()) {
            try {
                att.setDateEmission(java.sql.Date.valueOf(dateEmissionStr));
            } catch (IllegalArgumentException ignored) {
                att.setDateEmission(new Date());
            }
        } else {
            att.setDateEmission(new Date());
        }

        // ✅ id String (UUID) ou null → insert
        if (id != null && !id.isBlank()) {
            att.setId(id);
        }

        boolean success = AttestationDAO.save(att);
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("message", success ? "Attestation enregistrée" : "Échec de l'enregistrement");
        if (success) result.put("id", att.getId());   // ✅ String UUID
        sendJson(exchange, result);
    } catch (Exception e) {
        LOGGER.log(Level.SEVERE, e, () -> "Erreur save");
        sendJson(exchange, Map.of("success", false, "error", e.getMessage()));
    }
}
    // ============================================================
    // API : SUPPRESSION (nécessite DELETE)
    // ============================================================
 private void handleDelete(HttpExchange exchange, String institutionId) throws IOException {
    if (!"POST".equals(exchange.getRequestMethod())) {
        sendJson(exchange, Map.of("success", false, "error", "Méthode non autorisée"));
        return;
    }
    try {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, Object> data = GSON.fromJson(body, MAP_TYPE);

        // ✅ id est un String (UUID)
        String id = data.get("id") != null ? data.get("id").toString().trim() : null;
        if (id == null || id.isEmpty()) {
            sendJson(exchange, Map.of("success", false, "error", "ID manquant"));
            return;
        }

        boolean success = AttestationDAO.delete(id, institutionId);
        sendJson(exchange, Map.of(
                "success", success,
                "message", success ? "Attestation supprimée" : "Échec de la suppression"
        ));
    } catch (JsonSyntaxException | IOException e) {
        LOGGER.log(Level.SEVERE, e, () -> "Erreur delete");
        sendJson(exchange, Map.of("success", false, "error", e.getMessage()));
    }
}
    // ============================================================
    // API : APERÇU (lecture)
    // ============================================================
 private void handlePreview(HttpExchange exchange, String institutionId) throws IOException {
    if (!"POST".equals(exchange.getRequestMethod())) {
        sendJson(exchange, Map.of("success", false, "error", "Méthode non autorisée"));
        return;
    }
    try {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, Object> data = GSON.fromJson(body, MAP_TYPE);

        String etudiantId = (String) data.get("etudiantId");
        String type = (String) data.get("type");
        String reference = (String) data.get("reference");
        String numero = (String) data.get("numero");
        String dateStr = (String) data.get("date");
        String option = (String) data.get("option");
        String classe = (String) data.get("classe");
        String anneeAcademique = (String) data.get("anneeAcademique");

        if (etudiantId == null || type == null) {
            sendJson(exchange, Map.of("success", false, "error", "Étudiant et type requis"));
            return;
        }

        Date date = new Date();
        if (dateStr != null && !dateStr.isEmpty()) {
            try { date = java.sql.Date.valueOf(dateStr); } catch (Exception ignored) {}
        }

        Etudiant etudiant = AttestationDAO.getEtudiant(etudiantId, institutionId);
        Institution institution = AttestationDAO.getInstitution(institutionId);
        if (etudiant == null || institution == null) {
            sendJson(exchange, Map.of("success", false, "error", "Données manquantes (étudiant ou institution)"));
            return;
        }

        if (classe == null || classe.isEmpty()) classe = etudiant.getClasse();
        if (anneeAcademique == null || anneeAcademique.isEmpty()) anneeAcademique = etudiant.getAnneeAcademique();

        AttestationGenerator generator = new AttestationGenerator(
                etudiant, institution, type, reference, numero, date, option, classe, anneeAcademique
        );
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(595, 842, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g2 = image.createGraphics();
        g2.setColor(java.awt.Color.WHITE);
        g2.fillRect(0, 0, 595, 842);
        generator.drawDocument(g2);
        g2.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("image", base64);
        sendJson(exchange, result);
    } catch (JsonSyntaxException | IOException e) {
        LOGGER.log(Level.SEVERE, e, () -> "Erreur génération aperçu");
        sendJson(exchange, Map.of("success", false, "error", e.getMessage()));
    }
}    // ============================================================
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
                if (parts.length == 2 && "SESSION_ID".equalsIgnoreCase(parts[0].trim())) {
                    sessionId = parts[1].trim();
                    break;
                }
            }
            if (sessionId == null) return null;
            HttpSession session = SessionManager.getSession(sessionId);
            if (session == null) return null;
            return (String) session.getAttribute("institutionId");
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
                if (parts.length == 2 && "SESSION_ID".equalsIgnoreCase(parts[0].trim())) {
                    sessionId = parts[1].trim();
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
    // MÉTHODES UTILITAIRES
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
            LOGGER.warning(() -> "Erreur parsing params: " + e.getMessage());
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

    private void sendResponse(HttpExchange exchange, int statusCode, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}