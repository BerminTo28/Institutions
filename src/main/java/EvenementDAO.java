import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class EvenementDAO {

    private static final Logger LOGGER = Logger.getLogger(EvenementDAO.class.getName());
    private static final String TABLE = MigrationManager.TABLE_EVENEMENTS;

    /** Timeout SQL général. */
    private static final int QUERY_TIMEOUT_SECONDS = 15;

    /** Timeout écriture outbox. */
    private static final int OUTBOX_TIMEOUT_SECONDS = 10;

    private static final com.google.gson.Gson GSON = new com.google.gson.GsonBuilder()
            .registerTypeAdapter(java.time.LocalDateTime.class,
                    (com.google.gson.JsonSerializer<java.time.LocalDateTime>)
                            (src, type, ctx) -> new com.google.gson.JsonPrimitive(src.toString()))
            .registerTypeAdapter(java.time.LocalDate.class,
                    (com.google.gson.JsonSerializer<java.time.LocalDate>)
                            (src, type, ctx) -> new com.google.gson.JsonPrimitive(src.toString()))
            .create();

    private EvenementDAO() {}

    // =========================================================
    // HELPERS
    // =========================================================
    private static DatabaseManager db() { return DatabaseManager.getInstance(); }

    private static Connection getConnection() throws SQLException {
        return db().getConnection();
    }

    private static void closeIfNeeded(Connection conn) {
        if (conn != null) {
            try { if (!conn.isClosed()) conn.close(); } catch (SQLException ignored) {}
        }
    }

    private static boolean isConnectionAlive(Connection conn) {
        if (conn == null) return false;
        try { return conn.isValid(5); } catch (SQLException e) { return false; }
    }

    /**
     * ✅ Détermine la source (LOCAL/REMOTE) par comparaison d'URL JDBC.
     */
    private static String getSource(Connection conn) {
        if (conn == null) return "LOCAL";
        try {
            String url = conn.getMetaData().getURL();
            String localUrl  = db().getDataSource().getSecondaryUrl();
            String remoteUrl = db().getDataSource().getPrimaryUrl();

            if (localUrl != null && localUrl.equals(url))  return "LOCAL";
            if (remoteUrl != null && remoteUrl.equals(url)) return "REMOTE";
        } catch (SQLException e) {
            LOGGER.fine("Source indéterminée, fallback LOCAL");
        }
        return "LOCAL";
    }

    /**
     * ✅ Retourne une connexion d'écriture : LOCAL en priorité, REMOTE en secours.
     */
    private static Connection getPreferredWriteConnection() {
        DatabaseManager m = db();

        try {
            if (m.isLocalConfigured() && m.isLocalHealthy()) {
                Connection c = m.getLocalConnection();
                if (c != null && !c.isClosed() && isConnectionAlive(c)) return c;
                closeIfNeeded(c);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "LOCAL indisponible pour écriture", e);
        }

        try {
            if (m.isRemoteConfigured() && m.isRemoteHealthy()
                    && m.isMigrationDistanteTerminee()) {
                Connection c = m.getRemoteConnection();
                if (c != null && !c.isClosed() && isConnectionAlive(c)) return c;
                closeIfNeeded(c);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "REMOTE indisponible pour écriture", e);
        }

        try {
            Connection c = m.getLocalConnection();
            if (c != null && !c.isClosed()) {
                LOGGER.warning("⚠️ Aucune base saine — tentative directe sur LOCAL");
                return c;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Impossible d'obtenir une connexion d'écriture", e);
        }

        return null;
    }

    /**
     * ✅ Clé composite pour les tombstones.
     */
    private static String cleComposite(String id, String institutionId) {
        return id + "|" + institutionId;
    }

    // ==================== LECTURE ====================

    public static List<Evenement> getAll(String institutionId) {
        List<Evenement> list = new ArrayList<>();
        String sql = "SELECT * FROM " + TABLE
                + " WHERE institution_id = ? ORDER BY date_debut DESC";

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) list.add(mapResultSetToEvenement(rs));
                }
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Erreur getAll evenements", ex);
        } finally {
            closeIfNeeded(conn);
        }
        return list;
    }

    public static Evenement getById(String id, String institutionId) {
        if (id == null || id.isBlank()) return null;

        String sql = "SELECT * FROM " + TABLE
                + " WHERE institution_id = ? AND id = ?";

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                ps.setString(2, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return mapResultSetToEvenement(rs);
                }
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Erreur getById evenement", ex);
        } finally {
            closeIfNeeded(conn);
        }
        return null;
    }

    public static List<Evenement> getFiltres(String institutionId, String annee,
                                              String periode, String classe) {
        List<Evenement> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT * FROM " + TABLE + " WHERE institution_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(institutionId);

        if (annee != null && !annee.isBlank()) {
            sql.append(" AND annee_academique = ?"); params.add(annee);
        }
        if (periode != null && !periode.isBlank()) {
            sql.append(" AND periode = ?"); params.add(periode);
        }
        if (classe != null && !classe.isBlank()) {
            sql.append(" AND classe = ?"); params.add(classe);
        }
        sql.append(" ORDER BY date_debut DESC");

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) list.add(mapResultSetToEvenement(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur getFiltres", e);
        } finally {
            closeIfNeeded(conn);
        }
        return list;
    }

    // ==================== SAUVEGARDE ====================

    public static boolean save(Evenement evenement) {
        if (evenement == null) return false;
        if (evenement.getId() == null || evenement.getId().isBlank()) {
            return insert(evenement);
        } else {
            return update(evenement);
        }
    }

    // -------------------- INSERT --------------------
    private static boolean insert(Evenement e) {
        if (e.getId() == null || e.getId().isBlank()) {
            e.setId(UUID.randomUUID().toString());
        }

        Connection conn = getPreferredWriteConnection();
        if (conn == null) {
            LOGGER.severe(() -> "❌ Aucune connexion disponible pour insert événement "
                    + e.getTitre());
            return false;
        }

        boolean ok = false;
        String usedSource = null;
        try {
            usedSource = getSource(conn);
            ok = insertSansSync(conn, e, usedSource);
            if (ok) declencherSyncSiHorsTransaction(conn);
        } catch (SQLException | RuntimeException ex) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, ex,
                    () -> "❌ Échec insert événement sur " + srcErr
                            + " : " + e.getTitre());
        } finally {
            closeIfNeeded(conn);
        }

        if (ok) {
            enregistrerDansOutbox(e, "INSERT");

            final String src = usedSource;
            LOGGER.info(() -> "✅ Événement créé sur " + src
                    + " + outbox : " + e.getTitre());
        }
        return ok;
    }

    private static boolean insertSansSync(Connection conn, Evenement e, String source)
            throws SQLException {
        String sql = "INSERT INTO " + TABLE
                + " (institution_id, id, titre, description, "
                + "date_debut, date_fin, lieu, classe, annee_academique, periode, "
                + "etudiants_emails, professeurs_emails, statut, source) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            int i = 1;
            ps.setString(i++, e.getInstitutionId());
            ps.setString(i++, e.getId());
            ps.setString(i++, e.getTitre());
            ps.setString(i++, e.getDescription());
            ps.setTimestamp(i++, e.getDateDebut());
            ps.setTimestamp(i++, e.getDateFin());
            ps.setString(i++, e.getLieu());
            ps.setString(i++, e.getClasse());
            ps.setString(i++, e.getAnneeAcademique());
            ps.setString(i++, e.getPeriode());
            ps.setString(i++, e.getEtudiantsCsv());
            ps.setString(i++, e.getProfesseursCsv());
            ps.setString(i++, e.getStatut());
            ps.setString(i, source != null ? source : getSource(conn));

            return ps.executeUpdate() > 0;
        }
    }

    // -------------------- UPDATE --------------------
    private static boolean update(Evenement e) {
        if (e.getId() == null || e.getId().isBlank()) {
            LOGGER.warning("update() : id null ou vide");
            return false;
        }

        Connection conn = getPreferredWriteConnection();
        if (conn == null) return false;

        boolean ok = false;
        String usedSource = null;
        try {
            usedSource = getSource(conn);
            ok = updateSansSync(conn, e, usedSource);
            if (ok) declencherSyncSiHorsTransaction(conn);
        } catch (SQLException | RuntimeException ex) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, ex,
                    () -> "❌ Échec update événement sur " + srcErr
                            + " : " + e.getTitre());
        } finally {
            closeIfNeeded(conn);
        }

        if (ok) {
            enregistrerDansOutbox(e, "UPDATE");

            final String src = usedSource;
            LOGGER.info(() -> "✅ Événement mis à jour sur " + src
                    + " + outbox : " + e.getTitre());
        }
        return ok;
    }

    private static boolean updateSansSync(Connection conn, Evenement e, String source)
            throws SQLException {
        // ✅ Ajout de source = ?
        String sql = "UPDATE " + TABLE + " SET titre=?, description=?, date_debut=?, date_fin=?, lieu=?, "
                + "classe=?, annee_academique=?, periode=?, etudiants_emails=?, professeurs_emails=?, "
                + "statut=?, source=? WHERE institution_id=? AND id=?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            int i = 1;
            ps.setString(i++, e.getTitre());
            ps.setString(i++, e.getDescription());
            ps.setTimestamp(i++, e.getDateDebut());
            ps.setTimestamp(i++, e.getDateFin());
            ps.setString(i++, e.getLieu());
            ps.setString(i++, e.getClasse());
            ps.setString(i++, e.getAnneeAcademique());
            ps.setString(i++, e.getPeriode());
            ps.setString(i++, e.getEtudiantsCsv());
            ps.setString(i++, e.getProfesseursCsv());
            ps.setString(i++, e.getStatut());
            ps.setString(i++, source != null ? source : getSource(conn));
            ps.setString(i++, e.getInstitutionId());
            ps.setString(i, e.getId());

            return ps.executeUpdate() > 0;
        }
    }

    // ==================== SUPPRESSION ====================

    public static boolean delete(String id, String institutionId) {
        if (id == null || id.isBlank()) {
            LOGGER.warning("delete() : id null ou vide");
            return false;
        }

        Connection conn = getPreferredWriteConnection();
        if (conn == null) return false;

        boolean autoCommitOriginal;
        String usedSource ;
        try {
            usedSource = getSource(conn);
            autoCommitOriginal = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try {
                // ① Tombstone composite
                DeleteTracker.enregistrerSuppression(
                        conn, TABLE, cleComposite(id, institutionId), usedSource);

                // ② DELETE réel
                String sql = "DELETE FROM " + TABLE
                        + " WHERE institution_id = ? AND id = ?";
                int rows;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                    ps.setString(1, institutionId);
                    ps.setString(2, id);
                    rows = ps.executeUpdate();
                }

                conn.commit();

                if (rows > 0) {
                    enregistrerDeleteDansOutbox(id, institutionId, usedSource);
                    declencherSyncSiHorsTransaction(conn);
                    LOGGER.info(() -> "✅ Événement supprimé : " + id + " + outbox");
                }
                return rows > 0;

            } catch (SQLException ex) {
                try { conn.rollback(); } catch (SQLException ignored) {}
                LOGGER.log(Level.SEVERE, "Erreur delete evenement", ex);
                return false;
            } finally {
                try { conn.setAutoCommit(autoCommitOriginal); } catch (SQLException ignored) {}
            }

        } catch (SQLException | RuntimeException ex) {
            LOGGER.log(Level.SEVERE, ex, () -> "❌ Échec delete événement : " + id);
            return false;
        } finally {
            closeIfNeeded(conn);
        }
    }

    // ==================== LISTES POUR FILTRES ====================

    public static List<String> getAnneesAcademiquesFromTable(String institutionId) {
        List<String> annees = new ArrayList<>();
        String sql = "SELECT annee_academique FROM " + MigrationManager.TABLE_ANNEES_ACADEMIQUES
                + " WHERE institution_id = ? ORDER BY annee_academique DESC";

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) annees.add(rs.getString("annee_academique"));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur getAnneesAcademiquesFromTable", e);
        } finally {
            closeIfNeeded(conn);
        }
        return annees;
    }

    public static List<String> getPeriodesFromTable(String institutionId) {
        List<String> periodes = new ArrayList<>();
        String sql = "SELECT periode FROM " + MigrationManager.TABLE_PERIODES
                + " WHERE institution_id = ? ORDER BY periode";

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) periodes.add(rs.getString("periode"));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur getPeriodesFromTable", e);
        } finally {
            closeIfNeeded(conn);
        }
        return periodes;
    }

    public static List<String> getClassesFromTable(String institutionId) {
        List<String> classes = new ArrayList<>();
        String sql = "SELECT DISTINCT nom_classe FROM " + MigrationManager.TABLE_CLASSES
                + " WHERE institution_id = ? ORDER BY nom_classe";

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) classes.add(rs.getString("nom_classe"));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur getClassesFromTable", e);
        } finally {
            closeIfNeeded(conn);
        }
        return classes;
    }

    public static List<Evenement> getByProfesseurEmail(String institutionId, String email) {
        List<Evenement> list = new ArrayList<>();
        String cleanEmail = email.trim();
        String sql = "SELECT * FROM " + TABLE + " WHERE institution_id = ? "
                + "AND (REPLACE(REPLACE(professeurs_emails, ' ', ''), '\r', '') LIKE ? "
                + "OR REPLACE(REPLACE(professeurs_emails, ' ', ''), '\r', '') LIKE ? "
                + "OR REPLACE(REPLACE(professeurs_emails, ' ', ''), '\r', '') LIKE ? "
                + "OR REPLACE(REPLACE(professeurs_emails, ' ', ''), '\r', '') = ?) "
                + "ORDER BY date_debut DESC";
        String pattern1 = cleanEmail + ",%";
        String pattern2 = "%," + cleanEmail + ",%";
        String pattern3 = "%," + cleanEmail;
        String pattern4 = cleanEmail;

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                ps.setString(2, pattern1);
                ps.setString(3, pattern2);
                ps.setString(4, pattern3);
                ps.setString(5, pattern4);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) list.add(mapResultSetToEvenement(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur getByProfesseurEmail", e);
        } finally {
            closeIfNeeded(conn);
        }
        return list;
    }

    public static List<Evenement> getByEtudiantEmail(String institutionId, String email) {
        List<Evenement> list = new ArrayList<>();
        String sql = "SELECT * FROM " + TABLE + " WHERE institution_id = ? "
                + "AND FIND_IN_SET(?, REPLACE(REPLACE(etudiants_emails, ' ', ''), '\r', '')) > 0 "
                + "ORDER BY date_debut DESC";

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                ps.setString(2, email.trim());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) list.add(mapResultSetToEvenement(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur getByEtudiantEmail", e);
        } finally {
            closeIfNeeded(conn);
        }
        return list;
    }

    // ==================== LISTES DE PARTICIPANTS ====================

    public static List<Object[]> getEtudiantsActifs(String institutionId) {
        List<Object[]> etudiants = new ArrayList<>();
        String sql = "SELECT numero_identifiant, nom, prenom, email, classe FROM "
                + MigrationManager.TABLE_ETUDIANTS
                + " WHERE institution_id = ? AND statut = 'ACTIF' ORDER BY nom";

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        etudiants.add(new Object[]{
                                rs.getString("numero_identifiant"),
                                rs.getString("nom"),
                                rs.getString("prenom"),
                                rs.getString("email"),
                                rs.getString("classe")
                        });
                    }
                }
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Erreur getEtudiantsActifs", ex);
        } finally {
            closeIfNeeded(conn);
        }
        return etudiants;
    }

    public static List<Object[]> getProfesseursActifs(String institutionId) {
        List<Object[]> professeurs = new ArrayList<>();
        String sql = "SELECT numero_identifiant_professeur, nom, prenom, email FROM "
                + MigrationManager.TABLE_PROFESSEURS
                + " WHERE institution_id = ? AND statut = 'ACTIF' ORDER BY nom";

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        professeurs.add(new Object[]{
                                rs.getString("numero_identifiant_professeur"),
                                rs.getString("nom"),
                                rs.getString("prenom"),
                                rs.getString("email")
                        });
                    }
                }
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Erreur getProfesseursActifs", ex);
        } finally {
            closeIfNeeded(conn);
        }
        return professeurs;
    }

    // ==================== OUTBOX ====================

    private static void enregistrerDansOutbox(Evenement e, String operation) {
        String sql = """
            INSERT INTO sync_outbox
              (table_name, operation, primary_key, row_data, target_source, statut)
            VALUES (?, ?, ?, ?, 'REMOTE', 'PENDING')
        """;
        try (Connection conn = db().getLocalConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(OUTBOX_TIMEOUT_SECONDS);
            ps.setString(1, TABLE);
            ps.setString(2, operation);
            ps.setString(3, cleComposite(e.getId(), e.getInstitutionId()));
            ps.setString(4, toJson(e));
            ps.executeUpdate();
            LOGGER.fine(() -> "📥 Outbox " + operation + " : " + e.getId());
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, ex,
                    () -> "❌ Impossible d'enregistrer dans l'outbox : " + e.getId());
        }
    }

    private static void enregistrerDeleteDansOutbox(String id, String institutionId,
                                                       String source) {
        String sql = """
            INSERT INTO sync_outbox
              (table_name, operation, primary_key, row_data, target_source, statut)
            VALUES (?, 'DELETE', ?, ?, 'REMOTE', 'PENDING')
        """;
        try (Connection conn = db().getLocalConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(OUTBOX_TIMEOUT_SECONDS);
            ps.setString(1, TABLE);
            ps.setString(2, cleComposite(id, institutionId));
            ps.setString(3, "{\"id\":\"" + escapeJson(id) + "\","
                    + "\"institutionId\":\"" + escapeJson(institutionId) + "\","
                    + "\"source\":\"" + escapeJson(source) + "\"}");
            ps.executeUpdate();
            LOGGER.fine(() -> "📥 Outbox DELETE : " + id);
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, ex,
                    () -> "❌ Impossible d'enregistrer le DELETE dans l'outbox");
        }
    }

    private static String toJson(Evenement e) {
        try {
            return GSON.toJson(e);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, ex,
                    () -> "Impossible de sérialiser l'événement en JSON");
            return "{}";
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // ==================== MAPPING ====================

    private static Evenement mapResultSetToEvenement(ResultSet rs) throws SQLException {
        Evenement e = new Evenement();
        try { e.setId(rs.getString("id")); } catch (SQLException ignored) {}

        e.setTitre(rs.getString("titre"));
        e.setDescription(rs.getString("description"));
        e.setDateDebut(rs.getTimestamp("date_debut"));
        e.setDateFin(rs.getTimestamp("date_fin"));
        e.setLieu(rs.getString("lieu"));
        e.setClasse(rs.getString("classe"));
        e.setAnneeAcademique(rs.getString("annee_academique"));
        e.setPeriode(rs.getString("periode"));
        e.setEtudiantsFromCsv(rs.getString("etudiants_emails"));
        e.setProfesseursFromCsv(rs.getString("professeurs_emails"));
        e.setStatut(rs.getString("statut"));
        e.setInstitutionId(rs.getString("institution_id"));
        try {
            e.setSource(rs.getString("source"));
        } catch (SQLException ignored) {}
        return e;
    }

    // ==================== SYNCHRONISATION ====================

    private static void declencherSyncSiHorsTransaction(Connection conn) {
        try {
            if (conn != null && conn.getAutoCommit()) {
                db().declencherSyncImmediateAsync();
                LOGGER.fine("⚡ Sync asynchrone déclenchée (hors transaction).");
            } else {
                LOGGER.fine("ℹ️ Sync différée : transaction en cours (autoCommit=false).");
            }
        } catch (SQLException e) {
            LOGGER.log(Level.FINE, "Impossible de déclencher la sync", e);
        }
    }
}