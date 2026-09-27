import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * Handler HTTP pour le module BULLETIN.
 *
 * Rôle : traduire HTTP ↔ métier. Aucun calcul ici.
 * Délègue à BulletinGenerator (calculs) et BulletinPDFBuilder (rendu PDF).
 */
public class HandlerBulletins implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(HandlerBulletins.class.getName());
    private static final Gson GSON = new Gson();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        LOGGER.info(() -> "BULLETINS HANDLER - " + method + " " + path);

        String institutionId = null;

        try {
            institutionId = getInstitutionIdFromSession(exchange);
            String utilisateurId = getUtilisateurIdFromSession(exchange);

            if (institutionId == null || institutionId.isBlank()
                    || utilisateurId == null || utilisateurId.isBlank()) {
                LOGGER.warning("⛔ Session invalide");
                redirect(exchange, "/login?error=Session+expirée");
                return;
            }

            AccesAdmin acces = chargerPermissions(utilisateurId, institutionId);
            if (acces == null) {
                redirect(exchange, "/dashboard?error=Permissions+non+trouvées");
                return;
            }

            if (!acces.hasPermission(AccesAdmin.Module.BULLETIN, AccesAdmin.Action.READ)) {
                redirect(exchange, "/dashboard?error=Accès+non+autorisé");
                return;
            }

            if (path.startsWith("/admin/bulletins/api/")) {
                handleApi(exchange, institutionId, acces, path);
                return;
            }

            String html = UIBulletins.rendrePage(institutionId);
            sendHtml(exchange, 200, html);

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Erreur I/O: {0}", e.getMessage());
            String instId = (institutionId != null && !institutionId.isBlank()) ? institutionId : "ERR";
            try {
                sendHtml(exchange, 500, UIBulletins.rendrePage(instId, "Erreur: " + e.getMessage()));
            } catch (IOException ignored) {}
        }
    }

    // ============================================================
    // API
    // ============================================================
    private void handleApi(HttpExchange exchange, String institutionId,
                            AccesAdmin acces, String path) throws IOException {
        String method = exchange.getRequestMethod();

        if ("OPTIONS".equals(method)) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        try {
            if (path.endsWith("/annees")) {
                handleAnnees(exchange, institutionId);
                return;
            }
            if (path.endsWith("/sessions")) {
                handleSessions(exchange, institutionId);
                return;
            }
            if (path.endsWith("/classes")) {
                handleClasses(exchange, institutionId);
                return;
            }
            if (path.endsWith("/etudiants")) {
                handleEtudiants(exchange, institutionId);
                return;
            }
            if (path.endsWith("/matieres")) {
                handleMatieres(exchange, institutionId);
                return;
            }
            if (path.endsWith("/notes")) {
                handleNotes(exchange, institutionId);
                return;
            }
            if (path.endsWith("/generer")) {
                if (!"POST".equals(method)) {
                    sendJsonError(exchange, "Méthode non autorisée");
                    return;
                }
                if (!acces.hasPermission(AccesAdmin.Module.BULLETIN, AccesAdmin.Action.CREATE)) {
                    sendJsonError(exchange, "Permission CREATE refusée");
                    return;
                }
                handleGenerer(exchange, institutionId);
                return;
            }
            if (path.endsWith("/telecharger")) {
                handleTelecharger(exchange, institutionId);
                return;
            }

            sendJsonError(exchange, "API inconnue: " + path);

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Erreur API: {0}", e.getMessage());
            sendJsonError(exchange, e.getMessage());
        }
    }

    // ============================================================
    // ANNÉES — utilise getAnneesAcademiques() existant
    // ============================================================
    private void handleAnnees(HttpExchange exchange, String institutionId) throws IOException {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            BulletinGenerator generator = new BulletinGenerator(institutionId, conn);
            List<String> annees = generator.getAnneesAcademiques();
            if (annees.isEmpty()) {
                int an = Calendar.getInstance().get(Calendar.YEAR);
                annees.add((an - 1) + "-" + an);
                annees.add(an + "-" + (an + 1));
            }
            sendJsonSuccess(exchange, annees);
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erreur handleAnnees", e);
            sendJsonError(exchange, "Erreur base de données");
        }
    }

    // ============================================================
    // SESSIONS — utilise getPeriodesForYear() existant
    // ============================================================
    private void handleSessions(HttpExchange exchange, String institutionId) throws IOException {
        String annee = parseQueryParams(exchange.getRequestURI().getQuery()).get("annee");
        if (annee == null || annee.isBlank()) {
            sendJsonError(exchange, "Paramètre 'annee' manquant");
            return;
        }

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            BulletinGenerator generator = new BulletinGenerator(institutionId, conn);
            List<String> sessions = generator.getPeriodesForYear(annee, null);

            List<Map<String, Object>> data = new ArrayList<>();
            for (String s : sessions) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", s);
                item.put("nom", s);
                item.put("active", true);
                data.add(item);
            }
            sendJsonSuccess(exchange, data);
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erreur handleSessions", e);
            sendJsonError(exchange, "Erreur base de données");
        }
    }

    // ============================================================
    // CLASSES — requête SQL simple (pas de méthode dédiée)
    // ============================================================
    private void handleClasses(HttpExchange exchange, String institutionId) throws IOException {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            String sql = "SELECT DISTINCT nom_classe FROM " + MigrationManager.TABLE_CLASSES
                    + " WHERE institution_id = ? ORDER BY nom_classe";
            List<String> classes = new ArrayList<>();
            try (var ps = conn.prepareStatement(sql)) {
                ps.setString(1, institutionId);
                try (var rs = ps.executeQuery()) {
                    while (rs.next()) classes.add(rs.getString("nom_classe"));
                }
            }
            sendJsonSuccess(exchange, classes);
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erreur handleClasses", e);
            sendJsonError(exchange, "Erreur base de données");
        }
    }

    // ============================================================
    // ÉTUDIANTS — orchestre getEtudiants + getNotesEtudiant + calculerMoyenneGenerale
    // ============================================================
    private void handleEtudiants(HttpExchange exchange, String institutionId) throws IOException {
        Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
        String annee = params.get("annee");
        String session = params.get("session");
        String classe = params.get("classe");

        if (annee == null || annee.isBlank()
                || session == null || session.isBlank()
                || classe == null || classe.isBlank()) {
            sendJsonError(exchange, "Paramètres manquants: annee, session, classe requis");
            return;
        }

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            BulletinGenerator generator = new BulletinGenerator(institutionId, conn);

            // ✅ Utilise les méthodes EXISTANTES
            List<Etudiant> etudiants = generator.getEtudiants(classe, annee);
            List<Map<String, Object>> data = new ArrayList<>();

            for (Etudiant e : etudiants) {
                List<BulletinReleveMatiere> notes = generator.getNotesEtudiant(
                        e.getNumeroIdentifiantEtudiant(), annee, session, classe);

                double moyenne = BulletinGenerator.calculerMoyenneGenerale(notes, session);
                double moyenneArrondie = Math.round(moyenne * 100.0) / 100.0;
                boolean admis = moyenneArrondie >= 5.0;

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", e.getNumeroIdentifiantEtudiant());
                row.put("nom", e.getNom() != null ? e.getNom() : "");
                row.put("prenom", e.getPrenom() != null ? e.getPrenom() : "");
                row.put("classe", e.getClasse() != null ? e.getClasse() : "");
                row.put("moyenne", moyenneArrondie);
                row.put("nbMatieres", notes.size());
                row.put("decision", admis ? "ADMIS" : "ÉCHEC");
                row.put("decisionClass", admis ? "admis" : "echec");
                row.put("selected", false);
                data.add(row);
            }

            sendJsonSuccess(exchange, data);

        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erreur handleEtudiants", e);
            sendJsonError(exchange, "Erreur base de données");
        }
    }

    // ============================================================
    // MATIÈRES — utilise getNotesEtudiant() existant
    // ============================================================
    private void handleMatieres(HttpExchange exchange, String institutionId) throws IOException {
        Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
        String etudiantId = params.get("etudiantId");
        String annee = params.get("annee");
        String session = params.get("session");
        String classe = params.get("classe");

        if (etudiantId == null || annee == null || session == null) {
            sendJsonError(exchange, "Paramètres manquants: etudiantId, annee, session requis");
            return;
        }

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            BulletinGenerator generator = new BulletinGenerator(institutionId, conn);

            // ✅ Utilise getNotesEtudiant() existant
            List<BulletinReleveMatiere> matieres = generator.getNotesEtudiant(
                    etudiantId, annee, session, classe);

            List<Map<String, Object>> data = new ArrayList<>();
            for (BulletinReleveMatiere rm : matieres) {
                double note = rm.getNoteBrute(session);
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("nom", rm.getIntituleMatiere());
                m.put("code", rm.getCodeCours());
                m.put("note", note < 0 ? 0.0 : note);
                m.put("noteSur", rm.getNoteMaximale());
                m.put("coefficient", rm.getCoefficient());
                m.put("noteSur10", Math.round(
                        (note < 0 ? 0 : (note / rm.getNoteMaximale()) * 10) * 100.0) / 100.0);
                m.put("rang", "-");
                data.add(m);
            }

            sendJsonSuccess(exchange, data);

        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erreur handleMatieres", e);
            sendJsonError(exchange, "Erreur base de données");
        }
    }

    // ============================================================
    // NOTES DÉTAILLÉES — utilise getNotesEtudiant + calculerMoyenneGenerale
    // ============================================================
    private void handleNotes(HttpExchange exchange, String institutionId) throws IOException {
        Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
        String etudiantId = params.get("etudiantId");
        String annee = params.get("annee");
        String session = params.get("session");
        String classe = params.get("classe");

        if (etudiantId == null || annee == null || session == null) {
            sendJsonError(exchange, "Paramètres manquants: etudiantId, annee, session requis");
            return;
        }

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            BulletinGenerator generator = new BulletinGenerator(institutionId, conn);

            // ✅ Récupérer l'étudiant (via requête SQL directe)
            Etudiant etudiant = chargerEtudiant(conn, etudiantId, institutionId);
            if (etudiant == null) {
                sendJsonError(exchange, "Étudiant introuvable");
                return;
            }

            String classeEffective = (classe != null && !classe.isBlank())
                    ? classe : etudiant.getClasse();

            // ✅ Utilise getNotesEtudiant() + calculerMoyenneGenerale()
            List<BulletinReleveMatiere> notes = generator.getNotesEtudiant(
                    etudiantId, annee, session, classeEffective);
            double moyenne = BulletinGenerator.calculerMoyenneGenerale(notes, session);

            List<Map<String, Object>> matieres = new ArrayList<>();
            for (BulletinReleveMatiere rm : notes) {
                double noteBrute = rm.getNoteBrute(session);
                double noteSur10 = (noteBrute < 0) ? 0
                        : (noteBrute / rm.getNoteMaximale()) * 10;

                Map<String, Object> m = new LinkedHashMap<>();
                m.put("nom", rm.getIntituleMatiere());
                m.put("code", rm.getCodeCours());
                m.put("note", noteBrute < 0 ? 0 : noteBrute);
                m.put("noteSur", rm.getNoteMaximale());
                m.put("coefficient", rm.getCoefficient());
                m.put("noteSur10", Math.round(noteSur10 * 100.0) / 100.0);
                m.put("rang", "-");
                m.put("statut", noteSur10 >= 5 ? "ADMIS" : "ÉCHEC");
                matieres.add(m);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("nom", etudiant.getNom());
            result.put("prenom", etudiant.getPrenom());
            result.put("classe", classeEffective);
            result.put("matieres", matieres);
            result.put("nbMatieres", matieres.size());
            result.put("moyenne", Math.round(moyenne * 100.0) / 100.0);
            result.put("decision", moyenne >= 5 ? "ADMIS" : "ÉCHEC");

            sendJson(exchange, successMap(result));

        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erreur handleNotes", e);
            sendJsonError(exchange, "Erreur base de données");
        }
    }

    // ============================================================
    // GÉNÉRATION PDF — utilise BulletinService + BulletinPDFBuilder
    // ============================================================
    private void handleGenerer(HttpExchange exchange, String institutionId) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> params = parseQueryParams(body);

        String annee = params.get("annee");
        String sessionStr = params.get("session");
        String classe = params.get("classe");
        String numero = params.get("numero");
        String reference = params.get("reference");
        String etudiantIdsStr = params.get("etudiants");

        if (annee == null || sessionStr == null || classe == null || etudiantIdsStr == null) {
            sendJsonError(exchange, "Paramètres manquants");
            return;
        }

        List<String> sessions = parseCsvToList(sessionStr);
        if (sessions.isEmpty()) {
            sendJsonError(exchange, "Aucune session valide");
            return;
        }

        Set<String> etudiantsSet = new LinkedHashSet<>();
        for (String id : etudiantIdsStr.split(",")) {
            String trimmed = id.trim();
            if (!trimmed.isEmpty()) etudiantsSet.add(trimmed);
        }
        if (etudiantsSet.isEmpty()) {
            sendJsonError(exchange, "Aucun étudiant sélectionné");
            return;
        }

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            // ✅ Utilise les classes EXISTANTES
            BulletinGenerator generator = new BulletinGenerator(institutionId, conn);
            BulletinService service = new BulletinService(institutionId, conn);
            BulletinPDFBuilder pdfBuilder = new BulletinPDFBuilder();

            File downloadsDir = getDownloadsDirectory();
            File bulletinsDir = new File(downloadsDir, "bulletins_" + institutionId);
            if (!bulletinsDir.exists()) bulletinsDir.mkdirs();

            List<String> fichiersGeneres = new ArrayList<>();

            for (String id : etudiantsSet) {
                try {
                    // Récupérer l'étudiant
                    Etudiant etudiant = chargerEtudiant(conn, id, institutionId);
                    if (etudiant == null) {
                        LOGGER.warning(() -> "Étudiant introuvable: " + id);
                        continue;
                    }

                    // Construire les données pour chaque session
                    List<BulletinData> datasParSession = new ArrayList<>();
                    List<Integer> rangsParSession = new ArrayList<>();
                    double moyenneAnnuelle = 0;

                    for (String session : sessions) {
                        Map<String, Integer> rangs = service.calculerRangsParClasse(
                                classe, annee, session);

                        BulletinData data = service.buildBulletinData(
                                etudiant, annee, session, rangs);

                        data.setReference(reference);
                        data.setNumero(numero);

                        datasParSession.add(data);
                        rangsParSession.add(data.getRang());

                        moyenneAnnuelle = data.getMoyenneGenerale();
                    }

                    // ✅ Utilise buildMultiSession() EXISTANT
                    Institution institution = generator.getInstitutionInfo();
                    File fichier = pdfBuilder.buildMultiSession(
                            datasParSession, bulletinsDir, etudiant, institution,
                            moyenneAnnuelle, rangsParSession);

                    if (fichier != null && fichier.exists()) {
                        fichiersGeneres.add(fichier.getName());
                        LOGGER.info(() -> "✅ Bulletin généré : " + fichier.getAbsolutePath());
                    }

                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Erreur génération pour " + id, e);
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("fichiers", fichiersGeneres);
            result.put("count", fichiersGeneres.size());
            result.put("downloadDir", downloadsDir.getAbsolutePath());
            if (fichiersGeneres.isEmpty()) {
                result.put("error", "Aucun bulletin n'a pu être généré");
            }
            sendJson(exchange, successMap(result));

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur handleGenerer", e);
            sendJsonError(exchange, "Erreur base de données: " + e.getMessage());
        }
    }

    // ============================================================
    // TÉLÉCHARGEMENT
    // ============================================================
    private void handleTelecharger(HttpExchange exchange, String institutionId) throws IOException {
        Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
        String fichier = params.get("fichier");

        if (fichier == null || fichier.isBlank()) {
            sendJsonError(exchange, "Nom de fichier manquant");
            return;
        }

        fichier = new File(fichier).getName();

        File pdfFile = localiserFichier(institutionId, fichier);
        if (pdfFile == null || !pdfFile.exists()) {
            LOGGER.log(Level.WARNING, "Fichier non trouvé: {0}", fichier);
            sendJsonError(exchange, "Fichier non trouvé: " + fichier);
            return;
        }

        LOGGER.log(Level.INFO, "Téléchargement: {0}", pdfFile.getAbsolutePath());

        exchange.getResponseHeaders().set("Content-Type", "application/pdf");
        exchange.getResponseHeaders().set("Content-Disposition",
                "attachment; filename=\"" + fichier + "\"");
        exchange.sendResponseHeaders(200, pdfFile.length());

        try (FileInputStream fis = new FileInputStream(pdfFile);
             OutputStream os = exchange.getResponseBody()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                os.write(buffer, 0, read);
            }
            os.flush();
        }
    }

    // ============================================================
    // UTILITAIRES
    // ============================================================
    private Etudiant chargerEtudiant(Connection conn, String id, String institutionId) {
        String sql = "SELECT * FROM " + MigrationManager.TABLE_ETUDIANTS
                + " WHERE numero_identifiant = ? AND institution_id = ?";
        try (var ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, institutionId);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    Etudiant e = new Etudiant();
                    e.setNumeroIdentifiantEtudiant(rs.getString("numero_identifiant"));
                    e.setNom(rs.getString("nom"));
                    e.setPrenom(rs.getString("prenom"));
                    e.setClasse(rs.getString("classe"));
                    e.setAnneeAcademique(rs.getString("annee_academique"));
                    e.setInstitutionId(rs.getString("institution_id"));
                    return e;
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erreur chargerEtudiant", e);
        }
        return null;
    }

    private File localiserFichier(String institutionId, String fichier) {
        File downloadsDir = getDownloadsDirectory();
        File bulletinsDir = new File(downloadsDir, "bulletins_" + institutionId);

        File pdfFile = new File(bulletinsDir, fichier);
        if (pdfFile.exists()) return pdfFile;

        File appFile = new File(new File("./bulletins_" + institutionId), fichier);
        if (appFile.exists()) return appFile;

        File downloadsFile = new File(downloadsDir, fichier);
        if (downloadsFile.exists()) return downloadsFile;

        return null;
    }

    private File getDownloadsDirectory() {
        String userHome = System.getProperty("user.home");
        String os = System.getProperty("os.name").toLowerCase();

        String[] candidates;
        if (os.contains("win")) {
            candidates = new String[]{userHome + "\\Downloads", userHome + "\\Téléchargements"};
        } else if (os.contains("mac")) {
            candidates = new String[]{userHome + "/Downloads"};
        } else {
            candidates = new String[]{userHome + "/Téléchargements", userHome + "/Downloads"};
        }

        for (String path : candidates) {
            File dir = new File(path);
            if (dir.exists() && dir.isDirectory()) return dir;
        }

        File fallback = new File(candidates[0]);
        if (!fallback.exists()) fallback.mkdirs();
        return fallback;
    }

    private AccesAdmin chargerPermissions(String utilisateurId, String institutionId) {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            AccesAdminData dao = new AccesAdminData(conn);
            return dao.loadByUtilisateurId(utilisateurId, institutionId);
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erreur chargement permissions", e);
            return new AccesAdmin();
        }
    }

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
            if (cookieHeader == null) return null;
            String sessionId = null;
            for (String cookie : cookieHeader.split(";")) {
                String[] parts = cookie.trim().split("=", 2);
                if (parts.length == 2 && "SESSION_ID".equals(parts[0])) {
                    sessionId = parts[1];
                    break;
                }
            }
            if (sessionId == null) return null;
            return SessionManager.getSession(sessionId);
        } catch (Exception e) {
            return null;
        }
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
        } catch (Exception ignored) {}
        return params;
    }

    private List<String> parseCsvToList(String csv) {
        List<String> list = new ArrayList<>();
        if (csv == null || csv.isBlank()) return list;
        for (String s : csv.split(",")) {
            String trimmed = s.trim();
            if (!trimmed.isEmpty()) list.add(trimmed);
        }
        return list;
    }

    private Map<String, Object> successMap(Map<String, Object> data) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.putAll(data);
        return result;
    }

    private void sendJsonSuccess(HttpExchange exchange, Object data) throws IOException {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", data);
        sendJson(exchange, result);
    }

    private void sendJson(HttpExchange exchange, Map<String, Object> data) throws IOException {
        if (exchange.getResponseCode() != -1) return;
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

    private void sendHtml(HttpExchange exchange, int status, String html) throws IOException {
        if (exchange.getResponseCode() != -1) return;
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
            os.flush();
        }
    }

    private void redirect(HttpExchange exchange, String location) throws IOException {
        if (exchange.getResponseCode() != -1) return;
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(302, -1);
    }
}