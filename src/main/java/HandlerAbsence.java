import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class HandlerAbsence implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(HandlerAbsence.class.getName());
    private static final Gson GSON = new Gson();
    private final AbsenceManager absenceManager = new AbsenceManager();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

    // ============================================================
    // POINT D'ENTRÉE
    // ============================================================
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        LOGGER.fine(() -> "📋 ABSENCE HANDLER - " + method + " " + path);

        String institutionId;
        String utilisateurId;

        try {
            // 1. Récupération session
            institutionId = getInstitutionIdFromSession(exchange);
            utilisateurId = getUtilisateurIdFromSession(exchange);

            if (institutionId == null || institutionId.isBlank()
                    || utilisateurId == null || utilisateurId.isBlank()) {
                LOGGER.warning("⛔ Session invalide");
                if (path.startsWith("/admin/absences/api/")) {
                    sendJsonError(exchange, 401, "Session expirée");
                    return;
                }
                exchange.getResponseHeaders().set("Location", "/login?error=Session+expirée");
                exchange.sendResponseHeaders(302, -1);
                return;
            }

            // 2. Chargement des permissions
            AccesAdmin acces = chargerPermissions(utilisateurId, institutionId);
            if (acces == null
                    || !acces.hasPermission(AccesAdmin.Module.ABSENCE, AccesAdmin.Action.READ)) {
                LOGGER.warning(() -> "⛔ Pas de READ sur ABSENCE : " + utilisateurId);
                if (path.startsWith("/admin/absences/api/")) {
                    sendJsonError(exchange, 403, "Permission READ refusée");
                    return;
                }
                exchange.getResponseHeaders().set("Location", "/dashboard?error=Accès+non+autorisé");
                exchange.sendResponseHeaders(302, -1);
                return;
            }

            // 3. Routage
            if (path.startsWith("/admin/absences/api/")) {
                handleApi(exchange, method, institutionId, acces);
                return;
            }

            if ("GET".equals(method)
                    && (path.equals("/admin/absences") || path.equals("/admin/absences/"))) {
                Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
                String onglet = params.getOrDefault("onglet", "etudiant");

                boolean canCreate = acces.hasPermission(
                        AccesAdmin.Module.ABSENCE, AccesAdmin.Action.CREATE);
                boolean canUpdate = acces.hasPermission(
                        AccesAdmin.Module.ABSENCE, AccesAdmin.Action.UPDATE);
                boolean canDelete = acces.hasPermission(
                        AccesAdmin.Module.ABSENCE, AccesAdmin.Action.DELETE);

                String html = UIAbsence.renderPage(institutionId, onglet,
                        canCreate, canUpdate, canDelete);
                sendHtml(exchange, 200, html);
                return;
            }

            exchange.sendResponseHeaders(404, -1);

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Erreur dans HandlerAbsence", e);
            if (path.startsWith("/admin/absences/api/")) {
                sendJsonError(exchange, 500, "Erreur interne: " + e.getMessage());
            } else {
                String html = UIAbsence.renderError(
                        "Erreur interne du serveur: " + e.getMessage());
                sendHtml(exchange, 500, html);
            }
        }
    }

    // ============================================================
    // API ROUTING
    // ============================================================
    private void handleApi(HttpExchange exchange, String method,
                            String institutionId, AccesAdmin acces) throws IOException {
        String path = exchange.getRequestURI().getPath();

        try {
            switch (method) {
                case "GET" -> {
                    if (path.endsWith("/list")) {
                        handleListAbsences(exchange, institutionId);
                    } else if (path.endsWith("/etudiants")) {
                        handleGetEtudiants(exchange, institutionId);
                    } else if (path.endsWith("/professeurs")) {
                        handleGetProfesseurs(exchange, institutionId);
                    } else if (path.endsWith("/matieres")) {
                        handleGetMatieres(exchange, institutionId);
                    } else if (path.endsWith("/annees")) {
                        handleGetAnnees(exchange, institutionId);
                    } else if (path.endsWith("/periodes")) {
                        handleGetPeriodes(exchange, institutionId);
                    } else if (path.endsWith("/classes")) {
                        handleGetClasses(exchange, institutionId);
                    } else if (path.endsWith("/get")) {
                        handleGetAbsence(exchange, institutionId);
                    } else {
                        sendJsonError(exchange, 404, "API inconnue");
                    }
                }
                case "POST" -> {
                    if (path.endsWith("/add")) {
                        if (!acces.hasPermission(AccesAdmin.Module.ABSENCE, AccesAdmin.Action.CREATE)) {
                            sendJsonError(exchange, 403, "Permission CREATE refusée");
                            return;
                        }
                        handleAddAbsence(exchange, institutionId);
                    } else if (path.endsWith("/update")) {
                        if (!acces.hasPermission(AccesAdmin.Module.ABSENCE, AccesAdmin.Action.UPDATE)) {
                            sendJsonError(exchange, 403, "Permission UPDATE refusée");
                            return;
                        }
                        handleUpdateAbsence(exchange, institutionId);
                    } else if (path.endsWith("/delete")) {
                        if (!acces.hasPermission(AccesAdmin.Module.ABSENCE, AccesAdmin.Action.DELETE)) {
                            sendJsonError(exchange, 403, "Permission DELETE refusée");
                            return;
                        }
                        handleDeleteAbsence(exchange, institutionId);
                    } else if (path.endsWith("/justify")) {
                        if (!acces.hasPermission(AccesAdmin.Module.ABSENCE, AccesAdmin.Action.UPDATE)) {
                            sendJsonError(exchange, 403, "Permission UPDATE refusée");
                            return;
                        }
                        handleJustifyAbsence(exchange, institutionId);
                    } else {
                        sendJsonError(exchange, 404, "API inconnue");
                    }
                }
                default -> sendJsonError(exchange, 405, "Méthode non supportée");
            }
        } catch (IOException | SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur API : " + e.getMessage(), e);
            sendJsonError(exchange, 500, e.getMessage());
        }
    }

    // ============================================================
    // ✅ API : FOURNISSEURS DE LISTES (SOURCES DIRECTES)
    // ============================================================

    /**
     * ✅ Années académiques — directement depuis la table `annees_academiques`.
     */
    private void handleGetAnnees(HttpExchange exchange, String institutionId)
            throws IOException, SQLException {

        List<String> annees = new ArrayList<>();

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            List<AnneeAcademique> liste =
                    new AnneeAcademiqueData(conn).listByInstitution(institutionId);

            for (AnneeAcademique a : liste) {
                String annee = a.getAnneeAcademique();
                if (annee != null && !annee.isBlank() && !annees.contains(annee)) {
                    annees.add(annee);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erreur chargement années", e);
        }

        LOGGER.info(() -> "📋 " + annees.size() + " année(s) pour " + institutionId);
        sendJsonSuccess(exchange, annees);
    }

    /**
     * ✅ Périodes — directement depuis la table `periodes`.
     *    Si `anneeAcademique` fourni → filtré sur cette année.
     *    Sinon → TOUTES les périodes de l'institution.
     */
    private void handleGetPeriodes(HttpExchange exchange, String institutionId)
            throws IOException, SQLException {

        Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
        String anneeAcademique = params.get("anneeAcademique");

        List<String> periodes = new ArrayList<>();

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            PeriodeData periodeData = new PeriodeData(conn);

            List<Periode> liste = (anneeAcademique != null && !anneeAcademique.isBlank())
                    ? periodeData.listByInstitutionAndAnnee(institutionId, anneeAcademique)
                    : periodeData.listByInstitution(institutionId);

            for (Periode p : liste) {
                String nom = p.getPeriode();
                if (nom != null && !nom.isBlank() && !periodes.contains(nom)) {
                    periodes.add(nom);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erreur chargement périodes", e);
        }

        LOGGER.info(() -> "📋 " + periodes.size() + " période(s) pour " + institutionId
                + (anneeAcademique != null && !anneeAcademique.isBlank()
                    ? " (année=" + anneeAcademique + ")" : " (toutes années)"));

        sendJsonSuccess(exchange, periodes);
    }

    /**
     * ✅ Classes — directement depuis la table `classes`.
     *    Si `anneeAcademique` fourni → filtré sur cette année.
     */
    private void handleGetClasses(HttpExchange exchange, String institutionId)
            throws IOException, SQLException {

        Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
        String anneeAcademique = params.get("anneeAcademique");

        List<String> classes = new ArrayList<>();

        try {
            List<Classe> liste = new ClasseData().toutLister(institutionId);

            for (Classe c : liste) {
                // Filtrer par année si fourni
                if (anneeAcademique != null && !anneeAcademique.isBlank()) {
                    String anneeClasse = c.getAnneeAcademique();
                    if (anneeClasse == null || !anneeAcademique.equals(anneeClasse)) {
                        continue;
                    }
                }
                String nom = c.getNomClasse();
                if (nom != null && !nom.isBlank() && !classes.contains(nom)) {
                    classes.add(nom);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erreur chargement classes", e);
        }

        LOGGER.info(() -> "📋 " + classes.size() + " classe(s) pour " + institutionId);
        sendJsonSuccess(exchange, classes);
    }

    /**
     * ✅ Matières — directement depuis la table `matieres`.
     */
    private void handleGetMatieres(HttpExchange exchange, String institutionId)
            throws IOException, SQLException {

        List<String> matieres = new ArrayList<>();

        try {
            List<Matiere> liste = new MatiereData(institutionId).readAll();

            for (Matiere m : liste) {
                String code = m.getCodeCours();
                if (code != null && !code.isBlank() && !matieres.contains(code)) {
                    matieres.add(code);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erreur chargement matières", e);
        }

        LOGGER.info(() -> "📋 " + matieres.size() + " matière(s) pour " + institutionId);
        sendJsonSuccess(exchange, matieres);
    }

    /**
     * ✅ Étudiants — filtre sur classe, période, année.
     */
    private void handleGetEtudiants(HttpExchange exchange, String institutionId)
            throws IOException, SQLException {

        Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
        String anneeAcademique = params.get("anneeAcademique");
        String periode = params.get("periode");
        String classe = params.get("classe");

        List<Map<String, String>> etudiants = getEtudiantsList(
                institutionId, anneeAcademique, periode, classe);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", etudiants);
        sendJson(exchange, result, 200);
    }

    /**
     * ✅ Professeurs — filtre sur matière et classe.
     */
    private void handleGetProfesseurs(HttpExchange exchange, String institutionId)
            throws IOException, SQLException {

        Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
        String codeCours = params.get("matiere");
        String classe = params.get("classe");

        List<Map<String, String>> professeurs = new ArrayList<>();

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            FilterLoaderManager loader = new FilterLoaderManager(conn);
            Map<String, String> profsMap = loader.chargerProfesseursParFiltre(
                    institutionId, codeCours, classe);

            for (Map.Entry<String, String> entry : profsMap.entrySet()) {
                Map<String, String> item = new HashMap<>();
                item.put("id", entry.getKey());
                item.put("nom", entry.getValue());
                professeurs.add(item);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", professeurs);
        sendJson(exchange, result, 200);
    }

    // ============================================================
    // API — ABSENCES (CRUD)
    // ============================================================
    private void handleListAbsences(HttpExchange exchange, String institutionId)
            throws IOException, SQLException {

        Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());

        String type = params.get("type");
        String classe = params.get("classe");
        String anneeAcademique = params.get("anneeAcademique");
        String periode = params.get("periode");
        String codeCours = params.get("matiere");

        List<Absence> absences = absenceManager.getAbsencesFiltrees(
                institutionId, type, classe, anneeAcademique, periode, codeCours);

        Map<String, String> etudiants = getEtudiantsMap(institutionId);
        Map<String, String> professeurs = getProfesseursMap(institutionId);

        List<Map<String, Object>> list = new ArrayList<>();
        for (Absence a : absences) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", a.getId());
            item.put("date", a.getDateAbsence() != null
                    ? dateFormat.format(a.getDateAbsence()) : "");
            item.put("justifiee", a.isJustifiee());
            item.put("motif", a.getMotif() != null ? a.getMotif() : "");
            item.put("codeCours", a.getCodeCours());
            item.put("periode", a.getPeriode());
            item.put("classe", a.getClasse());
            item.put("personneType", a.getPersonneType());

            String identifiant = a.getNumeroIdentifiant();
            String nomComplet = ("ETUDIANT".equals(a.getPersonneType()))
                    ? etudiants.getOrDefault(identifiant, identifiant)
                    : professeurs.getOrDefault(identifiant, identifiant);

            item.put("nomComplet", nomComplet);
            item.put("numeroIdentifiant", identifiant);
            list.add(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", list);
        sendJson(exchange, result, 200);
    }

    private void handleGetAbsence(HttpExchange exchange, String institutionId)
            throws IOException, SQLException {

        Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
        String idStr = params.get("id");
        if (idStr == null || idStr.isEmpty()) {
            sendJsonError(exchange, 400, "ID manquant");
            return;
        }
        int id;
        try {
            id = Integer.parseInt(idStr);
        } catch (NumberFormatException e) {
            sendJsonError(exchange, 400, "ID invalide");
            return;
        }

        Absence a = absenceManager.getAbsenceById(id);
        if (a == null || !a.getInstitutionId().equals(institutionId)) {
            sendJsonError(exchange, 404, "Absence introuvable");
            return;
        }

        Map<String, Object> item = new HashMap<>();
        item.put("id", a.getId());
        item.put("date", a.getDateAbsence() != null ? dateFormat.format(a.getDateAbsence()) : "");
        item.put("justifiee", a.isJustifiee());
        item.put("motif", a.getMotif());
        item.put("codeCours", a.getCodeCours());
        item.put("periode", a.getPeriode());
        item.put("classe", a.getClasse());
        item.put("promotion", a.getPromotion());
        item.put("anneeAcademique", a.getAnneeAcademique());
        item.put("personneType", a.getPersonneType());
        item.put("numeroIdentifiant", a.getNumeroIdentifiant());

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", item);
        sendJson(exchange, result, 200);
    }

    private void handleAddAbsence(HttpExchange exchange, String institutionId)
            throws IOException, SQLException {

        Map<String, Object> body = readJsonBody(exchange);
        String personneType = (String) body.get("personneType");
        String numeroIdentifiant = (String) body.get("numeroIdentifiant");
        String dateStr = (String) body.get("date");
        String codeCours = (String) body.get("codeCours");
        String motif = (String) body.get("motif");

        Object justifieeObj = body.get("justifiee");
        boolean justifiee = justifieeObj instanceof Boolean
                ? (Boolean) justifieeObj
                : Boolean.parseBoolean(String.valueOf(justifieeObj));

        String classe = (String) body.get("classe");
        String promotion = (String) body.get("promotion");
        String periode = (String) body.get("periode");
        String anneeAcademique = (String) body.get("anneeAcademique");

        if (numeroIdentifiant == null || numeroIdentifiant.isEmpty()) {
            sendJsonError(exchange, 400, "Numéro d'identification requis");
            return;
        }
        if (dateStr == null || dateStr.isEmpty()) {
            sendJsonError(exchange, 400, "Date requise");
            return;
        }
        if (codeCours == null || codeCours.isEmpty()) {
            sendJsonError(exchange, 400, "Code cours / matière requis");
            return;
        }
        java.util.Date date;
        try {
            date = dateFormat.parse(dateStr);
        } catch (java.text.ParseException e) {
            sendJsonError(exchange, 400, "Format de date invalide (yyyy-MM-dd)");
            return;
        }

        Absence absence = new Absence();
        absence.setInstitutionId(institutionId);
        absence.setPersonneType(personneType);
        absence.setNumeroIdentifiant(numeroIdentifiant);
        absence.setDateAbsence(date);
        absence.setJustifiee(justifiee);
        absence.setMotif(motif);
        absence.setCodeCours(codeCours);
        absence.setClasse(classe);
        absence.setPromotion(promotion);
        absence.setPeriode(periode);
        absence.setAnneeAcademique(anneeAcademique);
        absence.setCreatedBy(getCurrentUser(exchange));

        int id = absenceManager.enregistrerAbsence(absence);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("id", id);
        sendJson(exchange, result, 200);
    }

    private void handleUpdateAbsence(HttpExchange exchange, String institutionId)
            throws IOException, SQLException {

        Map<String, Object> body = readJsonBody(exchange);
        Integer id = getAsInteger(body.get("id"));

        if (id == null) {
            sendJsonError(exchange, 400, "ID requis");
            return;
        }

        Absence absence = absenceManager.getAbsenceById(id);
        if (absence == null || !absence.getInstitutionId().equals(institutionId)) {
            sendJsonError(exchange, 404, "Absence introuvable");
            return;
        }

        if (body.containsKey("date")) {
            String dateStr = (String) body.get("date");
            try {
                absence.setDateAbsence(dateFormat.parse(dateStr));
            } catch (java.text.ParseException e) {
                sendJsonError(exchange, 400, "Format de date invalide");
                return;
            }
        }
        if (body.containsKey("codeCours")) absence.setCodeCours((String) body.get("codeCours"));
        if (body.containsKey("motif")) absence.setMotif((String) body.get("motif"));
        if (body.containsKey("justifiee")) {
            Object jObj = body.get("justifiee");
            absence.setJustifiee(jObj instanceof Boolean
                    ? (Boolean) jObj
                    : Boolean.parseBoolean(String.valueOf(jObj)));
        }
        if (body.containsKey("classe")) absence.setClasse((String) body.get("classe"));
        if (body.containsKey("promotion")) absence.setPromotion((String) body.get("promotion"));
        if (body.containsKey("periode")) absence.setPeriode((String) body.get("periode"));
        if (body.containsKey("anneeAcademique")) absence.setAnneeAcademique((String) body.get("anneeAcademique"));
        if (body.containsKey("numeroIdentifiant")) absence.setNumeroIdentifiant((String) body.get("numeroIdentifiant"));

        boolean updated = absenceManager.mettreAJourAbsence(absence);

        Map<String, Object> result = new HashMap<>();
        result.put("success", updated);
        sendJson(exchange, result, 200);
    }

    private void handleDeleteAbsence(HttpExchange exchange, String institutionId)
            throws IOException, SQLException {

        Map<String, Object> body = readJsonBody(exchange);
        Integer id = getAsInteger(body.get("id"));

        if (id == null) {
            sendJsonError(exchange, 400, "ID requis");
            return;
        }
        boolean deleted = absenceManager.supprimerAbsence(id, institutionId);
        Map<String, Object> result = new HashMap<>();
        result.put("success", deleted);
        sendJson(exchange, result, 200);
    }

    private void handleJustifyAbsence(HttpExchange exchange, String institutionId)
            throws IOException, SQLException {

        Map<String, Object> body = readJsonBody(exchange);
        Integer id = getAsInteger(body.get("id"));
        String motif = (String) body.get("motif");

        if (id == null) {
            sendJsonError(exchange, 400, "ID requis");
            return;
        }
        boolean updated = absenceManager.justifierAbsence(id, institutionId, motif);
        Map<String, Object> result = new HashMap<>();
        result.put("success", updated);
        sendJson(exchange, result, 200);
    }

    // ============================================================
    // PERMISSIONS
    // ============================================================
    private AccesAdmin chargerPermissions(String utilisateurId, String institutionId) {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            AccesAdminData dao = new AccesAdminData(conn);
            return dao.loadByUtilisateurId(utilisateurId, institutionId);
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erreur chargement permissions", e);
            return new AccesAdmin();
        }
    }

    // ============================================================
    // SESSION
    // ============================================================
    private String getInstitutionIdFromSession(HttpExchange exchange) {
        return readSessionAttr(exchange, "institutionId", "institution_id");
    }

    private String getUtilisateurIdFromSession(HttpExchange exchange) {
        return readSessionAttr(exchange, "userId", "username", "email");
    }

    private String readSessionAttr(HttpExchange exchange, String... names) {
        try {
            String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
            if (cookieHeader == null) return null;
            String sessionId = extractSessionId(cookieHeader);
            if (sessionId == null) return null;
            HttpSession session = SessionManager.getSession(sessionId);
            if (session == null) return null;

            for (String name : names) {
                Object val = session.getAttribute(name);
                if (val != null) {
                    String s = val.toString();
                    if (!s.isBlank()) return s;
                }
            }
        } catch (Exception e) {
            LOGGER.fine(() -> "Erreur lecture session : " + e.getMessage());
        }
        return null;
    }

    private String extractSessionId(String cookieHeader) {
        for (String cookie : cookieHeader.split(";")) {
            String trimmed = cookie.trim();
            int eq = trimmed.indexOf('=');
            if (eq <= 0) continue;
            String name = trimmed.substring(0, eq).trim();
            String value = trimmed.substring(eq + 1).trim();
            if ("SESSION_ID".equals(name) && !value.isEmpty()) {
                return value;
            }
        }
        return null;
    }

    // ============================================================
    // UTILITAIRES SQL
    // ============================================================
    private Map<String, String> getEtudiantsMap(String institutionId) throws SQLException {
        Map<String, String> map = new HashMap<>();
        String sql = "SELECT numero_identifiant, CONCAT(nom, ' ', prenom) as nomComplet FROM "
                + MigrationManager.TABLE_ETUDIANTS + " WHERE institution_id = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    map.put(rs.getString("numero_identifiant"), rs.getString("nomComplet"));
                }
            }
        }
        return map;
    }

    private Map<String, String> getProfesseursMap(String institutionId) throws SQLException {
        Map<String, String> map = new HashMap<>();
        String sql = "SELECT numero_identifiant_professeur, CONCAT(nom, ' ', prenom) as nomComplet FROM "
                + MigrationManager.TABLE_PROFESSEURS + " WHERE institution_id = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    map.put(rs.getString("numero_identifiant_professeur"), rs.getString("nomComplet"));
                }
            }
        }
        return map;
    }

    private List<Map<String, String>> getEtudiantsList(String institutionId,
                                                         String anneeAcademique,
                                                         String periode,
                                                         String classe) throws SQLException {
        List<Map<String, String>> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT numero_identifiant, CONCAT(nom, ' ', prenom) as nomComplet, classe "
                + "FROM " + MigrationManager.TABLE_ETUDIANTS + " WHERE institution_id = ?");

        List<Object> params = new ArrayList<>();
        params.add(institutionId);

        if (anneeAcademique != null && !anneeAcademique.isEmpty()) {
            sql.append(" AND annee_academique = ?");
            params.add(anneeAcademique);
        }
        if (periode != null && !periode.isEmpty()) {
            sql.append(" AND periode = ?");
            params.add(periode);
        }
        if (classe != null && !classe.isEmpty()) {
            sql.append(" AND classe = ?");
            params.add(classe);
        }

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, String> item = new HashMap<>();
                    item.put("id", rs.getString("numero_identifiant"));
                    item.put("nom", rs.getString("nomComplet"));
                    item.put("classe", rs.getString("classe"));
                    list.add(item);
                }
            }
        }
        return list;
    }

    // ============================================================
    // PARSING / UTILITAIRES
    // ============================================================
    private Map<String, Object> readJsonBody(HttpExchange exchange) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            String body = sb.toString().trim();
            if (body.isEmpty()) return new HashMap<>();
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = GSON.fromJson(body, Map.class);
                return map != null ? map : new HashMap<>();
            } catch (JsonSyntaxException e) {
                LOGGER.warning(() -> "Erreur parsing JSON : " + e.getMessage());
                return new HashMap<>();
            }
        }
    }

    private Integer getAsInteger(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Integer integer) return integer;
        if (obj instanceof Number number) return number.intValue();
        try {
            return Double.valueOf(obj.toString()).intValue();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isBlank()) return params;

        for (String pair : query.split("&")) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                try {
                    String key = java.net.URLDecoder.decode(
                            pair.substring(0, idx), StandardCharsets.UTF_8);
                    String value = java.net.URLDecoder.decode(
                            pair.substring(idx + 1), StandardCharsets.UTF_8);
                    params.put(key, value);
                } catch (Exception ignored) {}
            }
        }
        return params;
    }

    private String getCurrentUser(HttpExchange exchange) {
        try {
            String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
            if (cookieHeader != null) {
                String sessionId = extractSessionId(cookieHeader);
                if (sessionId != null) {
                    HttpSession session = SessionManager.getSession(sessionId);
                    if (session != null) {
                        Object userId = session.getAttribute("userId");
                        if (userId != null && !userId.toString().isBlank()) {
                            return userId.toString();
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.fine(() -> "Impossible de récupérer l'utilisateur courant : " + e.getMessage());
        }
        return "SYSTEM";
    }

    // ============================================================
    // RÉPONSES HTTP
    // ============================================================
    private void sendJsonSuccess(HttpExchange exchange, Object data) throws IOException {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", data);
        sendJson(exchange, result, 200);
    }

    private void sendJsonError(HttpExchange exchange, int status, String message)
            throws IOException {
        Map<String, Object> err = new HashMap<>();
        err.put("success", false);
        err.put("error", message);
        sendJson(exchange, err, status);
    }

    private void sendJson(HttpExchange exchange, Map<String, Object> data, int status)
            throws IOException {
        String json = GSON.toJson(data);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendHtml(HttpExchange exchange, int status, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}