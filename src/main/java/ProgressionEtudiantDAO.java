import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * DAO pour la gestion des progressions des étudiants.
 *
 * ✅ Cohérent avec DatabaseManager corrigé :
 *   - getSource() par comparaison d'URL JDBC
 *   - getPreferredWriteConnection() : LOCAL prioritaire, REMOTE secours
 *   - Enregistrement dans sync_outbox pour toutes les écritures
 *   - Déclenchement de sync après commit
 */
public class ProgressionEtudiantDAO {

    private static final Logger LOGGER = Logger.getLogger(ProgressionEtudiantDAO.class.getName());

    private static final String TABLE_PROGRESSIONS = "progressions_etudiant";
    private static final String TABLE_LECONS_TERMINEES = "lecons_terminees";

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

    private final DatabaseManager dbManager;

    public ProgressionEtudiantDAO() {
        this.dbManager = DatabaseManager.getInstance();
    }

    // =========================================================
    // HELPERS
    // =========================================================
    private Connection getConnection() throws SQLException {
        return dbManager.getConnection();
    }

    private void closeIfNeeded(Connection conn) {
        if (conn != null) {
            try { if (!conn.isClosed()) conn.close(); } catch (SQLException ignored) {}
        }
    }

    private boolean isConnectionAlive(Connection conn) {
        if (conn == null) return false;
        try { return conn.isValid(5); } catch (SQLException e) { return false; }
    }

    /**
     * ✅ Détermine la source (LOCAL/REMOTE) par comparaison d'URL JDBC.
     */
    private String getSource(Connection conn) {
        if (conn == null) return "LOCAL";
        try {
            String url = conn.getMetaData().getURL();
            String localUrl  = dbManager.getDataSource().getSecondaryUrl();
            String remoteUrl = dbManager.getDataSource().getPrimaryUrl();

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
    private Connection getPreferredWriteConnection() {
        try {
            if (dbManager.isLocalConfigured() && dbManager.isLocalHealthy()) {
                Connection c = dbManager.getLocalConnection();
                if (c != null && !c.isClosed() && isConnectionAlive(c)) return c;
                closeIfNeeded(c);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "LOCAL indisponible pour écriture", e);
        }

        try {
            if (dbManager.isRemoteConfigured() && dbManager.isRemoteHealthy()
                    && dbManager.isMigrationDistanteTerminee()) {
                Connection c = dbManager.getRemoteConnection();
                if (c != null && !c.isClosed() && isConnectionAlive(c)) return c;
                closeIfNeeded(c);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "REMOTE indisponible pour écriture", e);
        }

        try {
            Connection c = dbManager.getLocalConnection();
            if (c != null && !c.isClosed()) {
                LOGGER.warning("⚠️ Aucune base saine — tentative directe sur LOCAL");
                return c;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Impossible d'obtenir une connexion d'écriture", e);
        }

        return null;
    }

    // =========================================================
    // CREATE — progression étudiant
    // =========================================================
    public boolean create(ProgressionEtudiant progression, String coursId) {
        if (progression == null) throw new IllegalArgumentException("Progression nulle");

        Connection conn = getPreferredWriteConnection();
        if (conn == null) {
            LOGGER.severe(() -> "❌ Aucune connexion disponible pour create progression");
            return false;
        }

        boolean ok = false;
        String usedSource = null;
        try {
            usedSource = getSource(conn);
            ok = createSansSync(conn, progression, coursId, usedSource);
            if (ok) declencherSyncSiHorsTransaction(conn);
        } catch (SQLException | RuntimeException e) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec create progression sur " + srcErr);
        } finally {
            closeIfNeeded(conn);
        }

        if (!ok) return false;

        enregistrerDansOutbox(TABLE_PROGRESSIONS,
                progression.getEtudiantId() + "|" + coursId,
                toJson(progression), "INSERT");

        final String src = usedSource;
        LOGGER.info(() -> "✅ Progression créée sur " + src + " + outbox");
        return true;
    }

    private boolean createSansSync(Connection conn, ProgressionEtudiant progression,
                                     String coursId, String source) throws SQLException {
        String sql = """
            INSERT INTO progressions_etudiant (
                etudiant_id, cours_id, progression, moyenne_globale,
                date_derniere_activite, date_inscription, a_obtenu_certificat,
                note_finale, source
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setString(1, progression.getEtudiantId());
            ps.setString(2, coursId);
            ps.setDouble(3, progression.getProgression());
            ps.setDouble(4, progression.getMoyenneGlobale());
            ps.setTimestamp(5, progression.getDateDerniereActivite() != null
                    ? Timestamp.valueOf(progression.getDateDerniereActivite())
                    : new Timestamp(System.currentTimeMillis()));
            ps.setTimestamp(6, progression.getDateInscription() != null
                    ? Timestamp.valueOf(progression.getDateInscription())
                    : new Timestamp(System.currentTimeMillis()));
            ps.setBoolean(7, progression.isaObtenuCertificat());
            ps.setDouble(8, progression.getNoteFinale());
            ps.setString(9, source != null ? source : getSource(conn));

            return ps.executeUpdate() > 0;
        }
    }

    // =========================================================
    // READ
    // =========================================================
    public ProgressionEtudiant findByEtudiantAndCours(String etudiantId, String coursId)
            throws SQLException {
        String sql = "SELECT * FROM " + TABLE_PROGRESSIONS
                   + " WHERE etudiant_id = ? AND cours_id = ?";

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, etudiantId);
                ps.setString(2, coursId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapToProgression(rs);
                    }
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return null;
    }

    public List<ProgressionEtudiant> findByEtudiant(String etudiantId) throws SQLException {
        List<ProgressionEtudiant> progressions = new ArrayList<>();
        String sql = "SELECT * FROM " + TABLE_PROGRESSIONS + " WHERE etudiant_id = ?";

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, etudiantId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        progressions.add(mapToProgression(rs));
                    }
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return progressions;
    }

    // =========================================================
    // UPDATE — progression étudiant
    // =========================================================
    public boolean update(ProgressionEtudiant progression, String coursId) {
        if (progression == null) throw new IllegalArgumentException("Progression nulle");

        Connection conn = getPreferredWriteConnection();
        if (conn == null) return false;

        boolean ok = false;
        String usedSource = null;
        try {
            usedSource = getSource(conn);
            ok = updateSansSync(conn, progression, coursId, usedSource);
            if (ok) declencherSyncSiHorsTransaction(conn);
        } catch (SQLException | RuntimeException e) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec update progression sur " + srcErr);
        } finally {
            closeIfNeeded(conn);
        }

        if (ok) {
            enregistrerDansOutbox(TABLE_PROGRESSIONS,
                    progression.getEtudiantId() + "|" + coursId,
                    toJson(progression), "UPDATE");
        }

        return ok;
    }

    private boolean updateSansSync(Connection conn, ProgressionEtudiant progression,
                                     String coursId, String source) throws SQLException {
        String sql = """
            UPDATE progressions_etudiant SET
                progression = ?, moyenne_globale = ?,
                date_derniere_activite = ?, a_obtenu_certificat = ?,
                note_finale = ?, source = ?
            WHERE etudiant_id = ? AND cours_id = ?
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setDouble(1, progression.getProgression());
            ps.setDouble(2, progression.getMoyenneGlobale());
            ps.setTimestamp(3, progression.getDateDerniereActivite() != null
                    ? Timestamp.valueOf(progression.getDateDerniereActivite())
                    : new Timestamp(System.currentTimeMillis()));
            ps.setBoolean(4, progression.isaObtenuCertificat());
            ps.setDouble(5, progression.getNoteFinale());
            ps.setString(6, source != null ? source : getSource(conn));
            ps.setString(7, progression.getEtudiantId());
            ps.setString(8, coursId);

            return ps.executeUpdate() > 0;
        }
    }

    // =========================================================
    // UTILITAIRES
    // =========================================================
    public boolean isInscrit(String etudiantId, String coursId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + TABLE_PROGRESSIONS
                   + " WHERE etudiant_id = ? AND cours_id = ?";

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, etudiantId);
                ps.setString(2, coursId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1) > 0;
                    }
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return false;
    }

    public boolean delete(String etudiantId, String coursId) {
        Connection conn = getPreferredWriteConnection();
        if (conn == null) return false;

        boolean autoCommitOriginal;
        String usedSource;
        try {
            usedSource = getSource(conn);
            autoCommitOriginal = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try {
                // ① Tombstone
                String cle = etudiantId + "|" + coursId;
                DeleteTracker.enregistrerSuppression(conn, TABLE_PROGRESSIONS, cle, usedSource);

                // ② DELETE réel
                String sql = "DELETE FROM " + TABLE_PROGRESSIONS
                           + " WHERE etudiant_id = ? AND cours_id = ?";
                int rows;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                    ps.setString(1, etudiantId);
                    ps.setString(2, coursId);
                    rows = ps.executeUpdate();
                }

                conn.commit();

                if (rows > 0) {
                    enregistrerDeleteDansOutbox(TABLE_PROGRESSIONS,
                            etudiantId + "|" + coursId, usedSource);
                    declencherSyncSiHorsTransaction(conn);
                    LOGGER.info(() -> "✅ Progression supprimée : " + etudiantId + "/" + coursId);
                }
                return rows > 0;

            } catch (SQLException e) {
                try { conn.rollback(); } catch (SQLException ignored) {}
                LOGGER.log(Level.SEVERE, "❌ Erreur delete progression", e);
                return false;
            } finally {
                try { conn.setAutoCommit(autoCommitOriginal); } catch (SQLException ignored) {}
            }

        } catch (SQLException | RuntimeException e) {
            LOGGER.log(Level.SEVERE, e, () -> "❌ Échec delete progression");
            return false;
        } finally {
            closeIfNeeded(conn);
        }
    }

    // =========================================================
    // LEÇONS TERMINÉES
    // =========================================================
    public boolean marquerLeconTerminee(String etudiantId, String coursId, String leconId) {
        Connection conn = getPreferredWriteConnection();
        if (conn == null) return false;

        boolean ok = false;
        String usedSource = null;
        try {
            usedSource = getSource(conn);
            ok = marquerLeconTermineeSansSync(conn, etudiantId, coursId, leconId, usedSource);
            if (ok) declencherSyncSiHorsTransaction(conn);
        } catch (SQLException | RuntimeException e) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec marquerLeconTerminee sur " + srcErr);
        } finally {
            closeIfNeeded(conn);
        }

        if (ok) {
            enregistrerDansOutbox(TABLE_LECONS_TERMINEES,
                    etudiantId + "|" + coursId + "|" + leconId,
                    "{\"etudiantId\":\"" + escapeJson(etudiantId) + "\","
                            + "\"coursId\":\"" + escapeJson(coursId) + "\","
                            + "\"leconId\":\"" + escapeJson(leconId) + "\"}",
                    "INSERT");
        }

        return ok;
    }

    private boolean marquerLeconTermineeSansSync(Connection conn, String etudiantId,
                                                   String coursId, String leconId,
                                                   String source) throws SQLException {
        String sql = """
            INSERT INTO lecons_terminees (etudiant_id, cours_id, lecon_id, date_terminaison, source)
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE date_terminaison = VALUES(date_terminaison), source = VALUES(source)
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setString(1, etudiantId);
            ps.setString(2, coursId);
            ps.setString(3, leconId);
            ps.setTimestamp(4, Timestamp.valueOf(java.time.LocalDateTime.now()));
            ps.setString(5, source != null ? source : getSource(conn));
            return ps.executeUpdate() > 0;
        }
    }

    public List<String> getLeconsTerminees(String etudiantId, String coursId) throws SQLException {
        List<String> lecons = new ArrayList<>();
        String sql = "SELECT lecon_id FROM " + TABLE_LECONS_TERMINEES
                   + " WHERE etudiant_id = ? AND cours_id = ?";

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, etudiantId);
                ps.setString(2, coursId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        lecons.add(rs.getString("lecon_id"));
                    }
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return lecons;
    }

    // =========================================================
    // OUTBOX
    // =========================================================
    private void enregistrerDansOutbox(String table, String primaryKey,
                                         String json, String operation) {
        String sql = """
            INSERT INTO sync_outbox
              (table_name, operation, primary_key, row_data, target_source, statut)
            VALUES (?, ?, ?, ?, 'REMOTE', 'PENDING')
        """;
        try (Connection conn = dbManager.getLocalConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(OUTBOX_TIMEOUT_SECONDS);
            ps.setString(1, table);
            ps.setString(2, operation);
            ps.setString(3, primaryKey);
            ps.setString(4, json);
            ps.executeUpdate();
            LOGGER.fine(() -> "📥 Outbox " + operation + " : " + primaryKey);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Impossible d'enregistrer dans l'outbox : " + primaryKey);
        }
    }

    private void enregistrerDeleteDansOutbox(String table, String primaryKey,
                                                String source) {
        String sql = """
            INSERT INTO sync_outbox
              (table_name, operation, primary_key, row_data, target_source, statut)
            VALUES (?, 'DELETE', ?, ?, 'REMOTE', 'PENDING')
        """;
        try (Connection conn = dbManager.getLocalConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(OUTBOX_TIMEOUT_SECONDS);
            ps.setString(1, table);
            ps.setString(2, primaryKey);
            ps.setString(3, "{\"primaryKey\":\"" + escapeJson(primaryKey) + "\","
                    + "\"source\":\"" + escapeJson(source) + "\"}");
            ps.executeUpdate();
            LOGGER.fine(() -> "📥 Outbox DELETE : " + primaryKey);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Impossible d'enregistrer le DELETE dans l'outbox");
        }
    }

    private String toJson(ProgressionEtudiant p) {
        try {
            return GSON.toJson(p);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, e,
                    () -> "Impossible de sérialiser la progression en JSON");
            return "{}";
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // =========================================================
    // MAPPING
    // =========================================================
    private ProgressionEtudiant mapToProgression(ResultSet rs) throws SQLException {
        ProgressionEtudiant progression = new ProgressionEtudiant(rs.getString("etudiant_id"));

        progression.setProgression(rs.getDouble("progression"));
        progression.setMoyenneGlobale(rs.getDouble("moyenne_globale"));

        Timestamp dateActivite = rs.getTimestamp("date_derniere_activite");
        if (dateActivite != null) progression.setDateDerniereActivite(dateActivite.toLocalDateTime());

        Timestamp dateInscription = rs.getTimestamp("date_inscription");
        if (dateInscription != null) progression.setDateInscription(dateInscription.toLocalDateTime());

        progression.setaObtenuCertificat(rs.getBoolean("a_obtenu_certificat"));
        progression.setNoteFinale(rs.getDouble("note_finale"));

        try {
            progression.setSource(rs.getString("source"));
        } catch (SQLException ignored) {}

        return progression;
    }

    // =========================================================
    // SYNCHRONISATION
    // =========================================================
    private void declencherSyncSiHorsTransaction(Connection conn) {
        try {
            if (conn != null && conn.getAutoCommit()) {
                dbManager.declencherSyncImmediateAsync();
                LOGGER.fine("⚡ Sync asynchrone déclenchée (hors transaction).");
            } else {
                LOGGER.fine("ℹ️ Sync différée : transaction en cours (autoCommit=false).");
            }
        } catch (SQLException e) {
            LOGGER.log(Level.FINE, "Impossible de déclencher la sync", e);
        }
    }
}