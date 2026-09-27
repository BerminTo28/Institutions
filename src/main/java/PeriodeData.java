import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PeriodeData {

    private static final Logger LOGGER = Logger.getLogger(PeriodeData.class.getName());
    private static final String TABLE = MigrationManager.TABLE_PERIODES;

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

    /** Connexion partagée optionnelle (null = mode autonome). */
    private final Connection connection;

    // ✅ Constructeur 1 : mode transactionnel (connexion partagée)
    public PeriodeData(Connection connection) {
        this.connection = connection;
    }

    // ✅ Constructeur 2 : mode autonome
    public PeriodeData() {
        this.connection = null;
    }

    // =========================================================
    // HELPERS
    // =========================================================
    private DatabaseManager db() { return DatabaseManager.getInstance(); }

    private Connection getConnection() throws SQLException {
        if (connection != null && !connection.isClosed()) return connection;
        return db().getConnection();
    }

    private boolean shouldClose(Connection conn) {
        return conn != connection;
    }

    private void closeIfNeeded(Connection conn) {
        if (shouldClose(conn) && conn != null) {
            try { if (!conn.isClosed()) conn.close(); } catch (SQLException ignored) {}
        }
    }

    private boolean isConnectionAlive(Connection conn) {
        if (conn == null) return false;
        try { return conn.isValid(5); }
        catch (SQLException e) { return false; }
    }

    /**
     * ✅ Détermine la source (LOCAL/REMOTE) par comparaison d'URL JDBC.
     */
    private String getSource(Connection conn) {
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
    private Connection getPreferredWriteConnection() {
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

    // =========================================================
    // CREATE
    // =========================================================
    public boolean create(Periode periode) throws SQLException {
        if (periode == null) throw new IllegalArgumentException("Période nulle");

        Connection conn = getPreferredWriteConnection();
        if (conn == null) {
            LOGGER.severe(() -> "❌ Aucune connexion disponible pour créer "
                    + periode.getCleComposite());
            return false;
        }

        boolean created = false;
        String usedSource = null;
        try {
            usedSource = getSource(conn);
            created = createSansSync(conn, periode, usedSource);
            if (created) declencherSyncSiHorsTransaction(conn);
        } catch (RuntimeException e) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec création période sur " + srcErr
                            + " : " + periode.getCleComposite());
        } finally {
            closeIfNeeded(conn);
        }

        if (!created) return false;

        enregistrerDansOutbox(periode, "INSERT");

        final String src = usedSource;
        LOGGER.info(() -> "✅ Période créée sur " + src
                + " + outbox : " + periode.getCleComposite());
        return true;
    }

    private boolean createSansSync(Connection conn, Periode periode, String source) {
        String sql = "INSERT INTO " + TABLE
                + " (institution_id, annee_academique, periode, date_debut, date_fin, est_active, source) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setString(1, periode.getInstitutionId());
            ps.setString(2, periode.getAnneeAcademique());
            ps.setString(3, periode.getPeriode());
            ps.setString(4, periode.getDateDebut());
            ps.setString(5, periode.getDateFin());
            ps.setBoolean(6, periode.isEstActive());
            ps.setString(7, source != null ? source : getSource(conn));
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE,
                    "Erreur création période " + periode.getCleComposite(), e);
            return false;
        }
    }

    // =========================================================
    // UPDATE
    // =========================================================
    public boolean update(Periode periode) throws SQLException {
        if (periode == null) throw new IllegalArgumentException("Période nulle");

        Connection conn = getPreferredWriteConnection();
        if (conn == null) {
            LOGGER.severe(() -> "❌ Aucune connexion disponible pour update "
                    + periode.getCleComposite());
            return false;
        }

        boolean updated = false;
        String usedSource = null;
        try {
            usedSource = getSource(conn);
            updated = updateSansSync(conn, periode, usedSource);
            if (updated) declencherSyncSiHorsTransaction(conn);
        } catch (RuntimeException e) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec update période sur " + srcErr
                            + " : " + periode.getCleComposite());
        } finally {
            closeIfNeeded(conn);
        }

        if (!updated) return false;

        enregistrerDansOutbox(periode, "UPDATE");

        final String src = usedSource;
        LOGGER.info(() -> "✅ Période mise à jour sur " + src
                + " + outbox : " + periode.getCleComposite());
        return true;
    }

    private boolean updateSansSync(Connection conn, Periode periode, String source) {
        String sql = "UPDATE " + TABLE
                + " SET date_debut = ?, date_fin = ?, est_active = ?, source = ? "
                + "WHERE institution_id = ? AND annee_academique = ? AND periode = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setString(1, periode.getDateDebut());
            ps.setString(2, periode.getDateFin());
            ps.setBoolean(3, periode.isEstActive());
            ps.setString(4, source != null ? source : getSource(conn));
            ps.setString(5, periode.getInstitutionId());
            ps.setString(6, periode.getAnneeAcademique());
            ps.setString(7, periode.getPeriode());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE,
                    "Erreur update période " + periode.getCleComposite(), e);
            return false;
        }
    }

    // =========================================================
    // DELETE
    // =========================================================
    public boolean delete(String institutionId, String annee, String periode) {
        if (institutionId == null || institutionId.isBlank()
                || annee == null || annee.isBlank()
                || periode == null || periode.isBlank()) {
            LOGGER.warning("delete() : paramètres null ou vides");
            return false;
        }

        Connection conn = getPreferredWriteConnection();
        if (conn == null) {
            LOGGER.severe(() -> "❌ Aucune connexion disponible pour delete "
                    + institutionId + "/" + annee + "/" + periode);
            return false;
        }

        boolean autoCommitOriginal;
        String usedSource;
        try {
            usedSource = getSource(conn);
            autoCommitOriginal = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try {
                boolean ok = deleteSansSync(conn, institutionId, annee, periode, usedSource);
                conn.commit();

                if (ok) {
                    enregistrerDeleteDansOutbox(institutionId, annee, periode, usedSource);
                    declencherSyncSiHorsTransaction(conn);
                    LOGGER.info(() -> "✅ Période supprimée : "
                            + institutionId + "/" + annee + "/" + periode + " + outbox");
                }
                return ok;

            } catch (SQLException e) {
                try { conn.rollback(); } catch (SQLException ignored) {}
                LOGGER.log(Level.SEVERE,
                        "❌ Rollback suppression période", e);
                return false;
            } finally {
                try { conn.setAutoCommit(autoCommitOriginal); } catch (SQLException ignored) {}
            }

        } catch (SQLException | RuntimeException e) {
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec delete période : " + institutionId
                            + "/" + annee + "/" + periode);
            return false;
        } finally {
            closeIfNeeded(conn);
        }
    }

    private boolean deleteSansSync(Connection conn, String institutionId, String annee,
                                    String periode, String source) throws SQLException {
        // Tombstone
        String cleComposite = institutionId + "|" + annee + "|" + periode;
        DeleteTracker.enregistrerSuppression(conn, TABLE, cleComposite, source);

        String sql = "DELETE FROM " + TABLE
                + " WHERE institution_id = ? AND annee_academique = ? AND periode = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setString(1, institutionId);
            ps.setString(2, annee);
            ps.setString(3, periode);
            return ps.executeUpdate() > 0;
        }
    }

    // =========================================================
    // OUTBOX
    // =========================================================
    private void enregistrerDansOutbox(Periode periode, String operation) {
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
            ps.setString(3, periode.getInstitutionId() + "|"
                    + periode.getAnneeAcademique() + "|" + periode.getPeriode());
            ps.setString(4, toJson(periode));
            ps.executeUpdate();
            LOGGER.fine(() -> "📥 Outbox " + operation + " : "
                    + periode.getCleComposite());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Impossible d'enregistrer dans l'outbox : "
                            + periode.getCleComposite());
        }
    }

    private void enregistrerDeleteDansOutbox(String institutionId, String annee,
                                                String periode, String source) {
        String sql = """
            INSERT INTO sync_outbox
              (table_name, operation, primary_key, row_data, target_source, statut)
            VALUES (?, 'DELETE', ?, ?, 'REMOTE', 'PENDING')
        """;
        try (Connection conn = db().getLocalConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(OUTBOX_TIMEOUT_SECONDS);
            ps.setString(1, TABLE);
            ps.setString(2, institutionId + "|" + annee + "|" + periode);
            ps.setString(3, "{\"institutionId\":\"" + escapeJson(institutionId) + "\","
                    + "\"anneeAcademique\":\"" + escapeJson(annee) + "\","
                    + "\"periode\":\"" + escapeJson(periode) + "\","
                    + "\"source\":\"" + escapeJson(source) + "\"}");
            ps.executeUpdate();
            LOGGER.fine(() -> "📥 Outbox DELETE : " + institutionId + "/" + annee + "/" + periode);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Impossible d'enregistrer le DELETE dans l'outbox");
        }
    }

    private String toJson(Periode periode) {
        try {
            return GSON.toJson(periode);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, e,
                    () -> "Impossible de sérialiser la période en JSON");
            return "{}";
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // =========================================================
    // READ
    // =========================================================
    public List<Periode> listByInstitution(String institutionId) throws SQLException {
        List<Periode> list = new ArrayList<>();
        String sql = "SELECT * FROM " + TABLE + " WHERE institution_id = ? "
                + "ORDER BY annee_academique DESC, periode";

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) list.add(mapRow(rs));
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return list;
    }

    public List<Periode> listByInstitutionAndAnnee(String institutionId, String annee)
            throws SQLException {
        List<Periode> list = new ArrayList<>();
        String sql = "SELECT * FROM " + TABLE + " WHERE institution_id = ? "
                + "AND annee_academique = ? ORDER BY periode";

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                ps.setString(2, annee);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) list.add(mapRow(rs));
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return list;
    }

    public Periode readByCleComposite(String institutionId, String annee, String periode)
            throws SQLException {
        String sql = "SELECT * FROM " + TABLE + " WHERE institution_id = ? "
                + "AND annee_academique = ? AND periode = ?";

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                ps.setString(2, annee);
                ps.setString(3, periode);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return mapRow(rs);
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return null;
    }

    public Periode readByInstitutionAnneePeriode(String institutionId, String annee,
                                                   String periode) throws SQLException {
        return readByCleComposite(institutionId, annee, periode);
    }

    public boolean deleteByInstitutionAnneePeriode(String institutionId, String annee,
                                                     String periode) {
        return delete(institutionId, annee, periode);
    }

    // =========================================================
    // MAPPING
    // =========================================================
    private Periode mapRow(ResultSet rs) throws SQLException {
        Periode p = new Periode();
        p.setInstitutionId(rs.getString("institution_id"));
        p.setAnneeAcademique(rs.getString("annee_academique"));
        p.setPeriode(rs.getString("periode"));
        p.setDateDebut(rs.getString("date_debut"));
        p.setDateFin(rs.getString("date_fin"));
        p.setEstActive(rs.getBoolean("est_active"));
        try { p.setSource(rs.getString("source")); } catch (SQLException ignored) {}
        return p;
    }

    // =========================================================
    // SYNCHRONISATION
    // =========================================================
    private void declencherSyncSiHorsTransaction(Connection conn) {
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