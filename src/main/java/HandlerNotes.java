import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * Handler HTTP pour le module NOTES.
 *
 * CORRECTIFS :
 *  1. getMatiereByNom filtre par période (5 critères).
 *  2. getMatieresFiltrees filtre les matières selon classe/année/période
 *     → l'utilisateur ne voit QUE les matières valides pour sa sélection.
 *  3. Garde-fous explicites (période, année, classe) avant toute écriture.
 *  4. Utilisation de trouverNoteParContexte (6 critères).
 *  5. Catch global Throwable : aucune exception ne remonte au framework.
 */
public class HandlerNotes implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(HandlerNotes.class.getName());

    /** ✅ Gson configuré pour Java 17+. */
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(java.time.LocalDateTime.class,
                    (com.google.gson.JsonSerializer<java.time.LocalDateTime>)
                            (src, type, ctx) -> new com.google.gson.JsonPrimitive(src.toString()))
            .registerTypeAdapter(java.time.LocalDate.class,
                    (com.google.gson.JsonSerializer<java.time.LocalDate>)
                            (src, type, ctx) -> new com.google.gson.JsonPrimitive(src.toString()))
            .registerTypeAdapter(java.time.LocalTime.class,
                    (com.google.gson.JsonSerializer<java.time.LocalTime>)
                            (src, type, ctx) -> new com.google.gson.JsonPrimitive(src.toString()))
            .create();

    private final Map<String, Map<String, String>> enseignantCache = new HashMap<>();

    // ============================================================
    // POINT D'ENTRÉE
    // ============================================================
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        final String reqId = java.util.UUID.randomUUID().toString().substring(0, 8);

        try {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            String institutionId = getInstitutionIdFromSession(exchange);
            String utilisateurId = getUtilisateurIdFromSession(exchange);

            if (isBlank(institutionId) || isBlank(utilisateurId)) {
                safeRedirect(exchange, "/login?error=Session+expirée");
                return;
            }

            AccesAdmin acces = chargerPermissions(utilisateurId, institutionId);
            if (acces == null) {
                safeRedirect(exchange, "/dashboard?error=Permissions+non+trouvées");
                return;
            }

            if (!acces.hasPermission(AccesAdmin.Module.NOTE, AccesAdmin.Action.READ)) {
                safeRedirect(exchange, "/dashboard?error=Accès+non+autorisé+au+module+NOTE");
                return;
            }

            String path = exchange.getRequestURI().getPath();

            if (path != null && path.startsWith("/admin/notes/api/")) {
                handleApi(exchange, institutionId, acces, path, reqId);
                return;
            }

            handlePageHtml(exchange, institutionId, acces, reqId);

        } catch (IOException t) {
            // ✅ catch(Throwable) au lieu de catch(IOException)
            LOGGER.log(Level.SEVERE,
                    "[" + reqId + "] ❌ Exception non catchée dans handle()", t);
            try { safeError(exchange, 500, "Erreur interne"); }
            catch (Throwable ignored) {}
        } finally {
            try { exchange.close(); } catch (Exception ignored) {}
        }
    }

    // ============================================================
    // PAGE HTML
    // ============================================================
    private void handlePageHtml(HttpExchange exchange, String institutionId,
                                  AccesAdmin acces, String reqId) throws IOException {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {

            Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());

            String annee = params.getOrDefault("annee", "");
            String periode = params.getOrDefault("periode", "");
            String classe = params.getOrDefault("classe", "");
            String matiere = params.getOrDefault("matiere", "");

            List<String> annees = getAnnees(conn, institutionId);
            List<String> periodes = getPeriodes(conn, institutionId);
            List<String> classes = getClasses(conn, institutionId);

            // ✅ FIX : matières filtrées selon la sélection
            List<String> matieres = getMatieresFiltrees(
                    conn, institutionId, classe, annee, periode);

            List<Map<String, Object>> etudiants = new ArrayList<>();

            if (!isBlank(annee) && !isBlank(periode)
                    && !isBlank(classe) && !isBlank(matiere)) {
                etudiants = getEtudiantsAvecNotes(conn, institutionId,
                        annee, periode, classe, matiere);
            }

            boolean canUpdate = acces.hasPermission(AccesAdmin.Module.NOTE, AccesAdmin.Action.UPDATE);

            // ✅ FIX : passage des filtres actifs à UINotes
            String html = UINotes.rendrePage(
                    annees, periodes, classes, matieres, etudiants,
                    institutionId,
                    params.getOrDefault("error", ""),
                    params.getOrDefault("success", ""),
                    canUpdate,
                    annee, periode, classe, matiere);
            safeHtml(exchange, 200, html);

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "[" + reqId + "] Erreur SQL page HTML", e);
            String html = UINotes.rendrePage(
                    List.of(), List.of(), List.of(), List.of(), List.of(),
                    institutionId, "Erreur base de données", "", false,
                    "", "", "", "");
            safeHtml(exchange, 500, html);
        }
    }

    // ============================================================
    // API
    // ============================================================
    private void handleApi(HttpExchange exchange, String institutionId, AccesAdmin acces,
                            String path, String reqId) throws IOException {
        String method = exchange.getRequestMethod();

        if ("/admin/notes/api/etudiants".equals(path) && "GET".equals(method)) {
            if (!acces.hasPermission(AccesAdmin.Module.NOTE, AccesAdmin.Action.READ)) {
                safeJsonError(exchange, "Accès non autorisé");
                return;
            }
            handleApiEtudiants(exchange, institutionId, reqId);
            return;
        }

        if ("/admin/notes/api/matieres".equals(path) && "GET".equals(method)) {
            // ✅ NOUVEAU : endpoint pour recharger les matières en AJAX
            handleApiMatieres(exchange, institutionId, reqId);
            return;
        }

        if ("/admin/notes/api/save".equals(path) && "POST".equals(method)) {
            if (!acces.hasPermission(AccesAdmin.Module.NOTE, AccesAdmin.Action.UPDATE)) {
                safeJsonError(exchange, "Permission UPDATE refusée pour les notes");
                return;
            }
            handleApiSave(exchange, institutionId, reqId);
            return;
        }

        safeJsonError(exchange, "API inconnue: " + path);
    }

    // ============================================================
    // API — MATIÈRES FILTRÉES
    // ============================================================
    private void handleApiMatieres(HttpExchange exchange, String institutionId,
                                     String reqId) throws IOException {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {

            Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
            String annee = params.getOrDefault("annee", "");
            String periode = params.getOrDefault("periode", "");
            String classe = params.getOrDefault("classe", "");

            List<String> matieres = getMatieresFiltrees(
                    conn, institutionId, classe, annee, periode);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("matieres", matieres);
            safeJson(exchange, result);

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "[" + reqId + "] Erreur SQL API matières", e);
            safeJsonError(exchange, "Erreur base de données");
        }
    }

    // ============================================================
    // API — LECTURE ÉTUDIANTS
    // ============================================================
    private void handleApiEtudiants(HttpExchange exchange, String institutionId,
                                      String reqId) throws IOException {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {

            Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());

            String annee = params.get("annee");
            String periode = params.get("periode");
            String classe = params.get("classe");
            String matiere = params.get("matiere");

            if (isBlank(annee) || isBlank(periode)
                    || isBlank(classe) || isBlank(matiere)) {
                safeJsonError(exchange, "Tous les filtres sont obligatoires");
                return;
            }

            List<Map<String, Object>> etudiants = getEtudiantsAvecNotes(
                    conn, institutionId, annee, periode, classe, matiere);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("etudiants", etudiants);
            result.put("count", etudiants.size());

            safeJson(exchange, result);

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "[" + reqId + "] Erreur SQL API étudiants", e);
            safeJsonError(exchange, "Erreur base de données");
        }
    }

    // ============================================================
    // API — SAUVEGARDE
    // ============================================================
    @SuppressWarnings("unchecked")
    private void handleApiSave(HttpExchange exchange, String institutionId,
                                 String reqId) throws IOException {

        boolean autoCommitOriginal = true;
        Connection conn = null;

        try {
            String body = new String(exchange.getRequestBody().readAllBytes(),
                                      StandardCharsets.UTF_8);
            Map<String, Object> data = GSON.fromJson(body, Map.class);

            String annee = (String) data.get("annee");
            String periode = (String) data.get("periode");
            String classe = (String) data.get("classe");
            String matiere = (String) data.get("matiere");
            List<Map<String, Object>> notes = (List<Map<String, Object>>) data.get("notes");

            // ---------- Validations ----------
            if (isBlank(annee) || isBlank(periode)
                    || isBlank(classe) || isBlank(matiere)) {
                safeJsonError(exchange, "Paramètres manquants");
                return;
            }
            if (notes == null || notes.isEmpty()) {
                safeJsonError(exchange, "Aucune note à enregistrer");
                return;
            }

            String verifiedBy = getUtilisateurIdFromSession(exchange);
            if (isBlank(verifiedBy)) verifiedBy = "ADMIN_SYSTEM";
            final String verifiedByFinal = verifiedBy;

            conn = DatabaseManager.getInstance().getConnection();
            autoCommitOriginal = conn.getAutoCommit();
            conn.setAutoCommit(false);

            NoteData noteData = new NoteData(conn);
            NoteManager noteManager = new NoteManager(noteData);

            // ---------- Résolution de la matière ----------
            Matiere matiereObj = getMatiereByNom(
                    conn, matiere, institutionId, classe, annee, periode);

            if (matiereObj == null) {
                conn.rollback();
                safeJsonError(exchange, "Matière '" + matiere
                        + "' non trouvée pour la classe '" + classe
                        + "', année '" + annee
                        + "', période '" + periode + "'");
                return;
            }

            // ✅ GARDE-FOU 1 : période
            if (!isBlank(matiereObj.getPeriode())
                    && !matiereObj.getPeriode().equals(periode)) {
                conn.rollback();
                LOGGER.severe(() -> "[" + reqId + "] ❌ INCOHÉRENCE PÉRIODE : matière='"
                        + matiere + "' appartient à periode='" + matiereObj.getPeriode()
                        + "', mais periode demandée='" + periode + "'");
                safeJsonError(exchange, "Incohérence de période : la matière '"
                        + matiere + "' appartient à '" + matiereObj.getPeriode()
                        + "' mais vous avez sélectionné '" + periode + "'");
                return;
            }

            // ✅ GARDE-FOU 2 : année
            if (!isBlank(matiereObj.getAnneeAcademique())
                    && !matiereObj.getAnneeAcademique().equals(annee)) {
                conn.rollback();
                safeJsonError(exchange, "Incohérence d'année : la matière '"
                        + matiere + "' appartient à '" + matiereObj.getAnneeAcademique()
                        + "' mais vous avez sélectionné '" + annee + "'");
                return;
            }

            // ✅ GARDE-FOU 3 : classe
            if (!isBlank(matiereObj.getClasseMatiere())
                    && !matiereObj.getClasseMatiere().equals(classe)) {
                conn.rollback();
                safeJsonError(exchange, "Incohérence de classe : la matière '"
                        + matiere + "' appartient à '" + matiereObj.getClasseMatiere()
                        + "' mais vous avez sélectionné '" + classe + "'");
                return;
            }

            String codeCours = matiereObj.getCodeCours();
            double noteMin = matiereObj.getNoteMinimale();
            double noteMax = matiereObj.getNoteMaximale();
            double notePassage = matiereObj.getNotePassage();
            double coefficient = matiereObj.getCoefficient();

            Map<String, String> enseignantInfo = getEnseignantInfo(conn, codeCours, institutionId);
            if (enseignantInfo == null) {
                LOGGER.warning(() -> "[" + reqId + "] Aucun enseignant associé à " + matiere);
            }

            // ---------- Boucle de sauvegarde ----------
            int saved = 0;
            List<String> errors = new ArrayList<>();

            for (Map<String, Object> n : notes) {
                String idEtudiant = (String) n.get("id");
                Object noteObj = n.get("note");

                if (isBlank(idEtudiant) || noteObj == null) continue;

                Double noteVal;
                try {
                    noteVal = ((Number) noteObj).doubleValue();
                } catch (Exception e) {
                    errors.add("Note invalide pour " + idEtudiant);
                    continue;
                }

                if (noteVal < noteMin || noteVal > noteMax) {
                    errors.add("Note " + noteVal + " pour " + idEtudiant
                            + " hors limites [" + noteMin + ", " + noteMax + "]");
                    continue;
                }

                // ✅ RECHERCHE PAR CONTEXTE COMPLET (6 critères)
                Note existing = noteManager.trouverNoteParContexte(
                        codeCours, idEtudiant, institutionId, periode, annee, classe);

                if (existing != null) {
                    existing.setNoteValue(String.valueOf(noteVal));
                    existing.setAnneeAcademique(annee);
                    existing.setPeriode(periode);
                    existing.setClasse(classe);
                    existing.setNoteMinimale(String.valueOf(noteMin));
                    existing.setNoteMaximale(String.valueOf(noteMax));
                    existing.setNoteDePassage(String.valueOf(notePassage));
                    existing.setCoefficient(String.valueOf(coefficient));
                    if (enseignantInfo != null) {
                        existing.setNumeroIdentifiantEnseignant(enseignantInfo.get("numero_identifiant"));
                        existing.setEnseignantNom(enseignantInfo.get("nom"));
                        existing.setEnseignantPrenom(enseignantInfo.get("prenom"));
                    }
                    existing.setEstValidee(true);
                    existing.setIsVerified(true);
                    existing.setVerifiedBy(verifiedByFinal);

                    if (noteManager.mettreAJourNote(existing)) {
                        saved++;
                    } else {
                        errors.add("Échec mise à jour pour " + idEtudiant);
                    }
                } else {
                    Note newNote = createNewNote(conn, institutionId, idEtudiant, codeCours,
                            matiere, annee, periode, classe, noteVal, noteMin, noteMax,
                            notePassage, coefficient, enseignantInfo);
                    newNote.setEstValidee(true);
                    newNote.setIsVerified(true);
                    newNote.setVerifiedBy(verifiedByFinal);

                    if (noteManager.ajouterNote(newNote)) {
                        saved++;
                    } else {
                        errors.add("Échec insertion pour " + idEtudiant);
                    }
                }
            }

            if (saved > 0) {
                calculerEtMettreAJourRangs(conn, codeCours, institutionId,
                        annee, periode, classe);
            }

            conn.commit();
            clearEnseignantCache();

            final int savedFinal = saved;
            LOGGER.info(() -> "[" + reqId + "] ✅ Notes enregistrées : "
                    + savedFinal + "/" + notes.size());

            declencherSyncAsyncApresCommit();

            Map<String, Object> result = new HashMap<>();
            result.put("success", saved > 0);
            result.put("saved", saved);
            result.put("total", notes.size());
            if (!errors.isEmpty()) {
                result.put("errors", errors);
                result.put("message", saved + "/" + notes.size()
                        + " notes enregistrées, " + errors.size() + " erreur(s)");
            } else {
                result.put("message", saved + " notes enregistrées avec succès");
            }
            safeJson(exchange, result);

        } catch (JsonSyntaxException e) {
            rollbackQuietly(conn);
            safeJsonError(exchange, "Format JSON invalide");
        } catch (SQLException e) {
            rollbackQuietly(conn);
            LOGGER.log(Level.SEVERE, "[" + reqId + "] Erreur SQL save", e);
            safeJsonError(exchange, "Erreur base de données");
        } catch (IOException e) {
            rollbackQuietly(conn);
            LOGGER.log(Level.SEVERE, "[" + reqId + "] Erreur IO save", e);
            safeJsonError(exchange, "Erreur interne");
        } catch (Throwable t) {
            rollbackQuietly(conn);
            LOGGER.log(Level.SEVERE, "[" + reqId + "] Erreur inattendue save", t);
            safeJsonError(exchange, "Erreur interne");
        } finally {
            restoreAutoCommit(conn, autoCommitOriginal);
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
    private void rollbackQuietly(Connection conn) {
        if (conn == null) return;
        try {
            if (!conn.getAutoCommit()) conn.rollback();
        } catch (SQLException ignored) {}
    }

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
    // CHARGEMENT DES PERMISSIONS
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
        try {
            String sessionId = extractSessionId(exchange);
            if (sessionId == null) return null;
            HttpSession session = SessionManager.getSession(sessionId);
            if (session == null) return null;
            String instId = (String) session.getAttribute("institutionId");
            if (isBlank(instId)) instId = (String) session.getAttribute("institution_id");
            return instId;
        } catch (Exception e) {
            return null;
        }
    }

    private String getUtilisateurIdFromSession(HttpExchange exchange) {
        try {
            String sessionId = extractSessionId(exchange);
            if (sessionId == null) return null;
            HttpSession session = SessionManager.getSession(sessionId);
            if (session == null) return null;
            String userId = (String) session.getAttribute("userId");
            if (isBlank(userId)) userId = (String) session.getAttribute("username");
            return userId;
        } catch (Exception e) {
            return null;
        }
    }

    private String extractSessionId(HttpExchange exchange) {
        String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookieHeader == null) return null;
        for (String cookie : cookieHeader.split(";")) {
            String[] parts = cookie.trim().split("=", 2);
            if (parts.length == 2 && "SESSION_ID".equals(parts[0])) {
                return parts[1];
            }
        }
        return null;
    }

    // ============================================================
    // FILTRES
    // ============================================================
    private List<String> getAnnees(Connection conn, String institutionId) throws SQLException {
        List<String> annees = new ArrayList<>();
        String sql = "SELECT DISTINCT annee_academique FROM " + MigrationManager.TABLE_ANNEES_ACADEMIQUES
                + " WHERE institution_id = ? ORDER BY annee_academique DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) annees.add(rs.getString("annee_academique"));
            }
        }
        if (annees.isEmpty()) {
            int an = Calendar.getInstance().get(Calendar.YEAR);
            annees.add((an - 1) + "-" + an);
            annees.add(an + "-" + (an + 1));
        }
        return annees;
    }

    private List<String> getPeriodes(Connection conn, String institutionId) throws SQLException {
        List<String> periodes = new ArrayList<>();
        String sql = "SELECT DISTINCT periode FROM " + MigrationManager.TABLE_PERIODES
                + " WHERE institution_id = ? ORDER BY periode";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) periodes.add(rs.getString("periode"));
            }
        }
        if (periodes.isEmpty()) periodes.add("None");
        return periodes;
    }

    private List<String> getClasses(Connection conn, String institutionId) throws SQLException {
        List<String> classes = new ArrayList<>();
        String sql = "SELECT DISTINCT nom_classe FROM " + MigrationManager.TABLE_CLASSES
                + " WHERE institution_id = ? ORDER BY nom_classe";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) classes.add(rs.getString("nom_classe"));
            }
        }
        return classes;
    }

    /**
     * ✅ FIX : liste les matières FILTRÉES par classe + année + période.
     *    Si les 3 filtres ne sont pas fournis, retourne toutes les matières.
     */
    private List<String> getMatieresFiltrees(Connection conn, String institutionId,
                                               String classe, String annee, String periode)
            throws SQLException {
        List<String> matieres = new ArrayList<>();

        boolean filtreComplet = !isBlank(classe) && !isBlank(annee) && !isBlank(periode);

        StringBuilder sql = new StringBuilder(
            "SELECT DISTINCT nom_matiere FROM " + MigrationManager.TABLE_MATIERES
            + " WHERE institution_id = ? AND statut = 'ACTIF'");

        if (filtreComplet) {
            sql.append(" AND classe_matiere = ?");
            sql.append(" AND annee_academique = ?");
            sql.append(" AND periode = ?");
        }
        sql.append(" ORDER BY nom_matiere");

        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setString(1, institutionId);
            if (filtreComplet) {
                ps.setString(2, classe);
                ps.setString(3, annee);
                ps.setString(4, periode);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) matieres.add(rs.getString("nom_matiere"));
            }
        }
        return matieres;
    }

    // ============================================================
    // DONNÉES
    // ============================================================

    /**
     * ✅ FIX CRITIQUE : filtre par nom + institution + classe + année + PÉRIODE.
     */
    private Matiere getMatiereByNom(Connection conn, String nomMatiere, String institutionId,
                                     String classe, String annee, String periode)
            throws SQLException {
        String sql = "SELECT * FROM " + MigrationManager.TABLE_MATIERES
                + " WHERE nom_matiere = ? AND institution_id = ?"
                + "   AND classe_matiere = ?"
                + "   AND annee_academique = ?"
                + "   AND periode = ?"
                + " LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nomMatiere);
            ps.setString(2, institutionId);
            ps.setString(3, classe);
            ps.setString(4, annee);
            ps.setString(5, periode);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Matiere m = new Matiere();
                    m.setCodeCours(rs.getString("code_cours"));
                    m.setNomMatiere(rs.getString("nom_matiere"));
                    m.setCoefficient(rs.getDouble("coefficient"));
                    m.setNoteMinimale(rs.getDouble("note_minimale"));
                    m.setNoteMaximale(rs.getDouble("note_maximale"));
                    m.setNotePassage(rs.getDouble("note_passage"));
                    m.setInstitutionId(rs.getString("institution_id"));
                    m.setClasseMatiere(rs.getString("classe_matiere"));
                    m.setPeriode(rs.getString("periode"));
                    m.setAnneeAcademique(rs.getString("annee_academique"));
                    m.setStatut(rs.getString("statut"));

                    LOGGER.info(() -> "🔍 Matière trouvée : nom='" + nomMatiere
                            + "', classe='" + classe
                            + "', annee='" + annee
                            + "', periode='" + periode
                            + "' → code_cours=" + m.getCodeCours());
                    return m;
                }
            }
        }
        LOGGER.warning(() -> "⚠️ Aucune matière pour nom='" + nomMatiere
                + "', classe='" + classe
                + "', annee='" + annee
                + "', periode='" + periode + "'");
        return null;
    }

    private Map<String, String> getEnseignantInfo(Connection conn, String codeCours,
                                                    String institutionId) {
        String cacheKey = codeCours + ":" + institutionId;
        if (enseignantCache.containsKey(cacheKey)) {
            return enseignantCache.get(cacheKey);
        }

        Map<String, String> info = new HashMap<>();
        String sql = "SELECT DISTINCT p.numero_identifiant_professeur, p.nom, p.prenom "
                + "FROM professeurs p "
                + "INNER JOIN professeur_matieres pm ON p.institution_id = pm.institution_id "
                + "  AND p.numero_identifiant_professeur = pm.numero_identifiant "
                + "WHERE pm.institution_id = ? AND pm.code_cours = ? LIMIT 1";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            ps.setString(2, codeCours);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    info.put("numero_identifiant", rs.getString("numero_identifiant_professeur"));
                    info.put("nom", rs.getString("nom"));
                    info.put("prenom", rs.getString("prenom"));
                }
            }
        } catch (SQLException e) {
            LOGGER.warning(() -> "Erreur enseignant pour " + codeCours + " : " + e.getMessage());
            enseignantCache.put(cacheKey, null);
            return null;
        }

        Map<String, String> cached = info.isEmpty() ? null : info;
        enseignantCache.put(cacheKey, cached);
        return cached;
    }

    private void clearEnseignantCache() {
        enseignantCache.clear();
    }

    private List<Map<String, Object>> getEtudiantsAvecNotes(Connection conn, String institutionId,
            String annee, String periode, String classe, String matiere) throws SQLException {

        List<Map<String, Object>> result = new ArrayList<>();

        Matiere matiereObj = getMatiereByNom(conn, matiere, institutionId, classe, annee, periode);
        if (matiereObj == null) {
            LOGGER.warning(() -> "⚠️ Matière introuvable pour classe='" + classe
                    + "', annee='" + annee + "', periode='" + periode
                    + "', matiere='" + matiere + "'");
            return result;
        }
        String codeCours = matiereObj.getCodeCours();

        boolean hasAnneeEtudiant = columnExists(conn, "etudiants", "annee_academique");

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT e.numero_identifiant, e.nom, e.prenom, e.classe, ")
           .append("n.note_value AS note, n.rang_etudiant AS rang, n.id AS note_id ")
           .append("FROM ").append(MigrationManager.TABLE_ETUDIANTS).append(" e ")
           .append("LEFT JOIN ").append(MigrationManager.TABLE_NOTES).append(" n ON ")
           .append("  n.institution_id = e.institution_id ")
           .append("  AND n.numero_etudiant = e.numero_identifiant ")
           .append("  AND n.code_cours = ? ")
           .append("  AND n.periode = ? ")
           .append("  AND n.annee_academique = ? ")
           .append("WHERE e.institution_id = ? ")
           .append("  AND e.classe = ? ");

        if (hasAnneeEtudiant) {
            sql.append("  AND e.annee_academique = ? ");
        }

        sql.append("ORDER BY e.nom, e.prenom");

        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            ps.setString(idx++, codeCours);
            ps.setString(idx++, periode);
            ps.setString(idx++, annee);
            ps.setString(idx++, institutionId);
            ps.setString(idx++, classe);
            if (hasAnneeEtudiant) {
                ps.setString(idx++, annee);
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", rs.getString("numero_identifiant"));
                    map.put("nom", rs.getString("nom") != null ? rs.getString("nom") : "");
                    map.put("prenom", rs.getString("prenom") != null ? rs.getString("prenom") : "");
                    map.put("classe", rs.getString("classe") != null ? rs.getString("classe") : "");
                    Double note = rs.getObject("note") != null ? rs.getDouble("note") : null;
                    map.put("note", note);
                    Integer rang = rs.getObject("rang") != null ? rs.getInt("rang") : null;
                    map.put("rang", rang);
                    map.put("noteId", rs.getString("note_id"));
                    result.add(map);
                }
            }
        }

        LOGGER.info(() -> "📊 Étudiants retournés : " + result.size()
                + " (classe='" + classe + "', annee='" + annee
                + "', periode='" + periode + "', matiere='" + matiere + "')");

        return result;
    }

    private boolean columnExists(Connection conn, String tableName, String columnName)
            throws SQLException {
        String sql = "SELECT COUNT(*) FROM information_schema.COLUMNS "
                + "WHERE TABLE_NAME = ? AND COLUMN_NAME = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tableName);
            ps.setString(2, columnName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1) > 0;
            }
        }
        return false;
    }

    private void calculerEtMettreAJourRangs(Connection conn, String codeCours,
            String institutionId, String annee, String periode, String classe) {
        try {
            String sqlSelect = "SELECT n.id, n.numero_etudiant, n.note_value "
                    + "FROM " + MigrationManager.TABLE_NOTES + " n "
                    + "JOIN " + MigrationManager.TABLE_ETUDIANTS + " e ON n.institution_id = e.institution_id "
                    + "  AND n.numero_etudiant = e.numero_identifiant "
                    + "WHERE n.institution_id = ? AND n.code_cours = ? AND n.annee_academique = ? "
                    + "AND n.periode = ? AND e.classe = ? AND n.note_value IS NOT NULL "
                    + "ORDER BY CAST(n.note_value AS DECIMAL(10,2)) DESC";

            List<Map<String, Object>> notes = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sqlSelect)) {
                ps.setString(1, institutionId);
                ps.setString(2, codeCours);
                ps.setString(3, annee);
                ps.setString(4, periode);
                ps.setString(5, classe);
                try (ResultSet rs = ps.executeQuery()) {
                    int rang = 1;
                    Double prev = null;
                    int counter = 1;
                    while (rs.next()) {
                        Map<String, Object> map = new HashMap<>();
                        map.put("id", rs.getString("id"));
                        Double current = rs.getDouble("note_value");
                        if (prev != null && prev.equals(current)) {
                            map.put("rang", rang);
                        } else {
                            rang = counter;
                            map.put("rang", rang);
                        }
                        prev = current;
                        counter++;
                        notes.add(map);
                    }
                }
            }

            if (!notes.isEmpty()) {
                String sqlUpdate = "UPDATE " + MigrationManager.TABLE_NOTES
                        + " SET rang_etudiant = ? WHERE id = ?";
                try (PreparedStatement ps = conn.prepareStatement(sqlUpdate)) {
                    for (Map<String, Object> map : notes) {
                        ps.setInt(1, (Integer) map.get("rang"));
                        ps.setString(2, (String) map.get("id"));
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }
        } catch (SQLException e) {
            LOGGER.warning(() -> "Erreur calcul rangs: " + e.getMessage());
        }
    }

    private Note createNewNote(Connection conn, String institutionId, String idEtudiant,
            String codeCours, String matiere, String annee, String periode, String classe,
            double noteVal, double noteMin, double noteMax, double notePassage,
            double coefficient, Map<String, String> enseignantInfo) {
        Note note = new Note();
        note.setInstitutionId(institutionId);
        note.setNumeroIdentifiantEtudiant(idEtudiant);
        note.setCodeCours(codeCours);
        note.setMatiereNom(matiere);
        note.setAnneeAcademique(annee);
        note.setPeriode(periode);
        note.setClasse(classe);
        note.setNoteValue(String.valueOf(noteVal));
        note.setNoteSur(String.valueOf(noteMax));
        note.setNoteBase(String.valueOf(noteMin));
        note.setCoefficient(String.valueOf(coefficient));
        note.setNoteMinimale(String.valueOf(noteMin));
        note.setNoteMaximale(String.valueOf(noteMax));
        note.setNoteDePassage(String.valueOf(notePassage));

        String sql = "SELECT nom, prenom FROM " + MigrationManager.TABLE_ETUDIANTS
                + " WHERE institution_id = ? AND numero_identifiant = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            ps.setString(2, idEtudiant);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    note.setEtudiantNom(rs.getString("nom"));
                    note.setEtudiantPrenom(rs.getString("prenom"));
                }
            }
        } catch (SQLException e) {
            LOGGER.warning(() -> "Erreur récupération étudiant: " + e.getMessage());
        }

        if (enseignantInfo != null && !enseignantInfo.isEmpty()) {
            note.setNumeroIdentifiantEnseignant(enseignantInfo.get("numero_identifiant"));
            note.setEnseignantNom(enseignantInfo.get("nom"));
            note.setEnseignantPrenom(enseignantInfo.get("prenom"));
        }
        return note;
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
        } catch (Exception ignored) {}
        return params;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    // ============================================================
    // HTTP RESPONSES SÛRES
    // ============================================================
    private void safeJson(HttpExchange exchange, Map<String, Object> data) {
        if (data == null) data = new HashMap<>();
        try {
            if (exchange.getResponseCode() != -1) return;
            byte[] bytes = GSON.toJson(data).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
                os.flush();
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "⚠️ safeJson : envoi impossible", e);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "❌ safeJson : erreur inattendue", e);
        }
    }

    private void safeJsonError(HttpExchange exchange, String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", message != null ? message : "Erreur inconnue");
        safeJson(exchange, error);
    }

    private void safeHtml(HttpExchange exchange, int status, String html) {
        if (html == null) html = "";
        try {
            if (exchange.getResponseCode() != -1) return;
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
                os.flush();
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "⚠️ safeHtml : envoi impossible", e);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "❌ safeHtml : erreur inattendue", e);
        }
    }

    private void safeError(HttpExchange exchange, int status, String message) {
        if (message == null) message = "";
        try {
            if (exchange.getResponseCode() != -1) return;
            byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "⚠️ safeError : envoi impossible", e);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "❌ safeError : erreur inattendue", e);
        }
    }

    private void safeRedirect(HttpExchange exchange, String location) {
        if (location == null) location = "/admin/notes";
        try {
            if (exchange.getResponseCode() != -1) return;
            exchange.getResponseHeaders().set("Location", location);
            exchange.sendResponseHeaders(302, -1);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "⚠️ safeRedirect : envoi impossible", e);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "❌ safeRedirect : erreur inattendue", e);
        }
    }
}