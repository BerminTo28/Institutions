import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AnneeAcademiqueData {

    private static final Logger LOGGER = Logger.getLogger(AnneeAcademiqueData.class.getName());
    private static final String TABLE = MigrationManager.TABLE_ANNEES_ACADEMIQUES;

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

    public AnneeAcademiqueData() {
        this.connection = null;
    }

    public AnneeAcademiqueData(Connection connection) {
        this.connection = connection;
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
        try { return conn.isValid(5); } catch (SQLException e) { return false; }
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

    /**
     * ✅ Clé composite pour les tombstones.
     */
    private static String cleComposite(String institutionId, String anneeAcademique) {
        return institutionId + "|" + anneeAcademique;
    }

    // =========================================================
    // CREATE
    // =========================================================
    public boolean create(AnneeAcademique annee) {
        if (annee == null) throw new IllegalArgumentException("AnneeAcademique nulle");

        Connection conn = getPreferredWriteConnection();
        if (conn == null) {
            LOGGER.severe(() -> "❌ Aucune connexion disponible pour créer "
                    + annee.getAnneeAcademique());
            return false;
        }

        boolean ok = false;
        String usedSource = null;
        try {
            usedSource = getSource(conn);
            ok = createSansSync(conn, annee, usedSource);
            if (ok) declencherSyncSiHorsTransaction(conn);
        } catch (SQLException | RuntimeException e) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec création année académique sur " + srcErr
                            + " : " + annee.getAnneeAcademique());
        } finally {
            closeIfNeeded(conn);
        }

        if (ok) {
            enregistrerDansOutbox(annee, "INSERT");

            final String src = usedSource;
            LOGGER.info(() -> "✅ Année académique créée sur " + src
                    + " + outbox : " + annee.getAnneeAcademique());
        }
        return ok;
    }

    private boolean createSansSync(Connection conn, AnneeAcademique annee, String source)
            throws SQLException {
        String sql = "INSERT INTO " + TABLE
                + " (institution_id, annee_academique, date_debut, date_fin, est_active, source) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setString(1, annee.getInstitutionId());
            ps.setString(2, annee.getAnneeAcademique());
            ps.setString(3, annee.getDateDebut());
            ps.setString(4, annee.getDateFin());
            ps.setBoolean(5, annee.isEstActive());
            ps.setString(6, source != null ? source : getSource(conn));
            return ps.executeUpdate() > 0;
        }
    }

    // =========================================================
    // READ
    // =========================================================
    public List<AnneeAcademique> listByInstitution(String institutionId) throws SQLException {
        List<AnneeAcademique> list = new ArrayList<>();
        String sql = "SELECT institution_id, annee_academique, date_debut, date_fin, est_active, source "
                + "FROM " + TABLE + " "
                + "WHERE institution_id = ? "
                + "ORDER BY annee_academique DESC";

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

    public AnneeAcademique readByInstitutionAndAnnee(String institutionId, String annee)
            throws SQLException {
        String sql = "SELECT institution_id, annee_academique, date_debut, date_fin, est_active, source "
                + "FROM " + TABLE + " "
                + "WHERE institution_id = ? AND annee_academique = ?";

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                ps.setString(2, annee);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return mapRow(rs);
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return null;
    }

    // =========================================================
    // UPDATE — par clé composite
    // =========================================================
    public boolean update(AnneeAcademique annee) {
        if (annee == null) throw new IllegalArgumentException("AnneeAcademique nulle");

        Connection conn = getPreferredWriteConnection();
        if (conn == null) return false;

        boolean updated = false;
        String usedSource = null;
        try {
            usedSource = getSource(conn);
            updated = updateSansSync(conn, annee, usedSource);
            if (updated) declencherSyncSiHorsTransaction(conn);
        } catch (SQLException | RuntimeException e) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec update année sur " + srcErr
                            + " : " + annee.getAnneeAcademique());
        } finally {
            closeIfNeeded(conn);
        }

        if (updated) enregistrerDansOutbox(annee, "UPDATE");

        return updated;
    }

    private boolean updateSansSync(Connection conn, AnneeAcademique annee, String source)
            throws SQLException {
        String sql = "UPDATE " + TABLE + " "
                + "SET date_debut = ?, date_fin = ?, est_active = ?, source = ? "
                + "WHERE institution_id = ? AND annee_academique = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setString(1, annee.getDateDebut());
            ps.setString(2, annee.getDateFin());
            ps.setBoolean(3, annee.isEstActive());
            ps.setString(4, source != null ? source : getSource(conn));
            ps.setString(5, annee.getInstitutionId());
            ps.setString(6, annee.getAnneeAcademique());
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * ✅ Mise à jour avec changement de clé composite (si l'année change).
     * Effectue DELETE + INSERT dans une transaction.
     */
    public boolean updateAvecChangementDeCle(String institutionId,
                                               String ancienneAnnee,
                                               AnneeAcademique nouvelle) {
        if (nouvelle == null) throw new IllegalArgumentException("AnneeAcademique nulle");

        Connection conn = getPreferredWriteConnection();
        if (conn == null) return false;

        boolean autoCommitOriginal;
        String usedSource = null;
        try {
            usedSource = getSource(conn);
            autoCommitOriginal = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try {
                // ① Tombstone de l'ancienne clé
                String ancienneCle = cleComposite(institutionId, ancienneAnnee);
                DeleteTracker.enregistrerSuppression(conn, TABLE, ancienneCle, usedSource);

                // ② Supprimer l'ancienne
                String deleteSql = "DELETE FROM " + TABLE
                        + " WHERE institution_id = ? AND annee_academique = ?";
                try (PreparedStatement del = conn.prepareStatement(deleteSql)) {
                    del.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                    del.setString(1, institutionId);
                    del.setString(2, ancienneAnnee);
                    del.executeUpdate();
                }

                // ③ Insérer la nouvelle
                String insertSql = "INSERT INTO " + TABLE
                        + " (institution_id, annee_academique, date_debut, date_fin, est_active, source) "
                        + "VALUES (?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ins = conn.prepareStatement(insertSql)) {
                    ins.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                    ins.setString(1, nouvelle.getInstitutionId());
                    ins.setString(2, nouvelle.getAnneeAcademique());
                    ins.setString(3, nouvelle.getDateDebut());
                    ins.setString(4, nouvelle.getDateFin());
                    ins.setBoolean(5, nouvelle.isEstActive());
                    ins.setString(6, usedSource);
                    ins.executeUpdate();
                }

                conn.commit();

            } catch (SQLException e) {
                try { conn.rollback(); } catch (SQLException ignored) {}
                LOGGER.log(Level.SEVERE, "❌ Rollback updateAvecChangementDeCle année "
                        + ancienneAnnee, e);
                return false;
            } finally {
                try { conn.setAutoCommit(autoCommitOriginal); } catch (SQLException ignored) {}
            }

        } catch (SQLException | RuntimeException e) {
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec updateAvecChangementDeCle année : " + ancienneAnnee);
            return false;
        } finally {
            closeIfNeeded(conn);
        }

        // Sync + outbox après succès
        enregistrerDansOutbox(nouvelle, "INSERT");
        declencherSyncSiHorsTransaction(conn);

        final String src = usedSource;
        LOGGER.info(() -> "✅ Année renommée sur " + src
                + " + outbox : " + ancienneAnnee + " → " + nouvelle.getAnneeAcademique());
        return true;
    }

    // =========================================================
    // DELETE — par clé composite
    // =========================================================
    public boolean delete(String institutionId, String anneeAcademique) {
        if (institutionId == null || institutionId.isBlank()
                || anneeAcademique == null || anneeAcademique.isBlank()) {
            LOGGER.warning("delete() : paramètres null ou vides");
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
                String cleComposite = cleComposite(institutionId, anneeAcademique);
                DeleteTracker.enregistrerSuppression(conn, TABLE, cleComposite, usedSource);

                // ② DELETE réel
                String sql = "DELETE FROM " + TABLE
                        + " WHERE institution_id = ? AND annee_academique = ?";
                int rows;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                    ps.setString(1, institutionId);
                    ps.setString(2, anneeAcademique);
                    rows = ps.executeUpdate();
                }

                conn.commit();

                if (rows > 0) {
                    enregistrerDeleteDansOutbox(institutionId, anneeAcademique, usedSource);
                    declencherSyncSiHorsTransaction(conn);
                    LOGGER.info(() -> "✅ Année académique supprimée : "
                            + institutionId + "/" + anneeAcademique + " + outbox");
                }
                return rows > 0;

            } catch (SQLException e) {
                try { conn.rollback(); } catch (SQLException ignored) {}
                LOGGER.log(Level.SEVERE, "❌ Rollback suppression année "
                        + anneeAcademique, e);
                return false;
            } finally {
                try { conn.setAutoCommit(autoCommitOriginal); } catch (SQLException ignored) {}
            }

        } catch (SQLException | RuntimeException e) {
            LOGGER.log(Level.SEVERE, e, () -> "❌ Échec delete année : " + anneeAcademique);
            return false;
        } finally {
            closeIfNeeded(conn);
        }
    }

    // =========================================================
    // OUTBOX
    // =========================================================
    private void enregistrerDansOutbox(AnneeAcademique annee, String operation) {
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
            ps.setString(3, cleComposite(annee.getInstitutionId(), annee.getAnneeAcademique()));
            ps.setString(4, toJson(annee));
            ps.executeUpdate();
            LOGGER.fine(() -> "📥 Outbox " + operation + " : " + annee.getAnneeAcademique());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Impossible d'enregistrer dans l'outbox : "
                            + annee.getAnneeAcademique());
        }
    }

    private void enregistrerDeleteDansOutbox(String institutionId, String anneeAcademique,
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
            ps.setString(2, cleComposite(institutionId, anneeAcademique));
            ps.setString(3, "{\"institutionId\":\"" + escapeJson(institutionId) + "\","
                    + "\"anneeAcademique\":\"" + escapeJson(anneeAcademique) + "\","
                    + "\"source\":\"" + escapeJson(source) + "\"}");
            ps.executeUpdate();
            LOGGER.fine(() -> "📥 Outbox DELETE : " + anneeAcademique);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Impossible d'enregistrer le DELETE dans l'outbox");
        }
    }

    private String toJson(AnneeAcademique a) {
        try {
            return GSON.toJson(a);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, e,
                    () -> "Impossible de sérialiser l'année académique en JSON");
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
    private AnneeAcademique mapRow(ResultSet rs) throws SQLException {
        AnneeAcademique a = new AnneeAcademique();
        a.setInstitutionId(rs.getString("institution_id"));
        a.setAnneeAcademique(rs.getString("annee_academique"));
        a.setDateDebut(rs.getString("date_debut"));
        a.setDateFin(rs.getString("date_fin"));
        a.setEstActive(rs.getBoolean("est_active"));
        try { a.setSource(rs.getString("source")); } catch (SQLException ignored) {}
        return a;
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