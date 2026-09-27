import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class OptionData {

    private static final Logger LOGGER = Logger.getLogger(OptionData.class.getName());
    private static final String TABLE = MigrationManager.TABLE_OPTIONS;

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

    public OptionData() {
        this.connection = null;
    }

    public OptionData(Connection connection) {
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

    private boolean isConnectionAlive(Connection conn) {
        if (conn == null) return false;
        try { return conn.isValid(5); }
        catch (SQLException e) { return false; }
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
    // CREATE (sans id — PK composite)
    // =========================================================
    public boolean create(Options option) throws SQLException {
        if (option == null) throw new IllegalArgumentException("Option nulle");

        Connection conn = getPreferredWriteConnection();
        if (conn == null) {
            LOGGER.severe(() -> "❌ Aucune connexion disponible pour créer "
                    + option.getOption());
            return false;
        }

        boolean inserted = false;
        String usedSource = null;
        try {
            usedSource = getSource(conn);
            inserted = createSansSync(conn, option, usedSource);
            if (inserted) declencherSyncSiHorsTransaction(conn);
        } catch (RuntimeException e) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec création option sur " + srcErr
                            + " : " + option.getOption());
        } finally {
            closeIfNeeded(conn);
        }

        if (!inserted) return false;

        enregistrerDansOutbox(option, "INSERT");

        final String src = usedSource;
        LOGGER.info(() -> "✅ Option créée sur " + src
                + " + outbox : " + option.getOption());
        return true;
    }

    private boolean createSansSync(Connection conn, Options option, String source) {
        String sql = "INSERT INTO " + TABLE + " "
                + "(institution_id, `option`, classe, annee_academique, periode, promotion, source) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setString(1, option.getInstitutionId());
            ps.setString(2, option.getOption());
            ps.setString(3, option.getClasse());
            ps.setString(4, option.getAnneeAcademique());
            ps.setString(5, option.getPeriode());
            ps.setString(6, option.getPromotion());
            ps.setString(7, source != null ? source : getSource(conn));
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur insertion option : " + option.getOption(), e);
            return false;
        }
    }

    // =========================================================
    // READ — par clé composite
    // =========================================================
    public Options read(String institutionId, String option, String classe,
                         String anneeAcademique) throws SQLException {
        String sql = "SELECT * FROM " + TABLE
                + " WHERE institution_id = ? AND `option` = ? AND classe = ? AND annee_academique = ?";

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                ps.setString(2, option);
                ps.setString(3, classe);
                ps.setString(4, anneeAcademique);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return mapRow(rs);
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return null;
    }

    public List<Options> readAll(String institutionId) throws SQLException {
        List<Options> list = new ArrayList<>();
        String sql = "SELECT * FROM " + TABLE
                + " WHERE institution_id = ? "
                + "ORDER BY annee_academique DESC, `option`, classe";

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

    // =========================================================
    // UPDATE — par clé composite
    // =========================================================
    public boolean update(Options option) throws SQLException {
        if (option == null) throw new IllegalArgumentException("Option nulle");

        Connection conn = getPreferredWriteConnection();
        if (conn == null) return false;

        boolean updated = false;
        String usedSource = null;
        try {
            usedSource = getSource(conn);
            updated = updateSansSync(conn, option, usedSource);
            if (updated) declencherSyncSiHorsTransaction(conn);
        } catch (RuntimeException e) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec update option sur " + srcErr
                            + " : " + option.getOption());
        } finally {
            closeIfNeeded(conn);
        }

        if (updated) enregistrerDansOutbox(option, "UPDATE");

        return updated;
    }

    private boolean updateSansSync(Connection conn, Options option, String source) {
        String sql = "UPDATE " + TABLE
                + " SET periode = ?, promotion = ?, source = ? "
                + "WHERE institution_id = ? AND `option` = ? AND classe = ? AND annee_academique = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setString(1, option.getPeriode());
            ps.setString(2, option.getPromotion());
            ps.setString(3, source != null ? source : getSource(conn));
            ps.setString(4, option.getInstitutionId());
            ps.setString(5, option.getOption());
            ps.setString(6, option.getClasse());
            ps.setString(7, option.getAnneeAcademique());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur update option : " + option.getOption(), e);
            return false;
        }
    }

    /**
     * Mise à jour avec changement de clé composite (option/classe/année change).
     * Effectue DELETE + INSERT dans une transaction.
     */
    public boolean updateAvecChangementDeCle(String institutionId,
                                               String ancienneOption,
                                               String ancienneClasse,
                                               String ancienneAnnee,
                                               Options nouvelle) {

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
                String ancienneCle = institutionId + "|" + ancienneOption + "|"
                        + ancienneClasse + "|" + ancienneAnnee;
                DeleteTracker.enregistrerSuppression(conn, TABLE, ancienneCle, usedSource);

                // ② Supprimer l'ancienne
                String deleteSql = "DELETE FROM " + TABLE
                        + " WHERE institution_id = ? AND `option` = ? AND classe = ? AND annee_academique = ?";
                try (PreparedStatement del = conn.prepareStatement(deleteSql)) {
                    del.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                    del.setString(1, institutionId);
                    del.setString(2, ancienneOption);
                    del.setString(3, ancienneClasse);
                    del.setString(4, ancienneAnnee);
                    del.executeUpdate();
                }

                // ③ Insérer la nouvelle
                String insertSql = "INSERT INTO " + TABLE
                        + " (institution_id, `option`, classe, annee_academique, periode, promotion, source) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ins = conn.prepareStatement(insertSql)) {
                    ins.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                    ins.setString(1, nouvelle.getInstitutionId());
                    ins.setString(2, nouvelle.getOption());
                    ins.setString(3, nouvelle.getClasse());
                    ins.setString(4, nouvelle.getAnneeAcademique());
                    ins.setString(5, nouvelle.getPeriode());
                    ins.setString(6, nouvelle.getPromotion());
                    ins.setString(7, usedSource);
                    ins.executeUpdate();
                }

                conn.commit();

            } catch (SQLException e) {
                try { conn.rollback(); } catch (SQLException ignored) {}
                LOGGER.log(Level.SEVERE, "❌ Rollback updateAvecChangementDeCle option "
                        + ancienneOption, e);
                return false;
            } finally {
                try { conn.setAutoCommit(autoCommitOriginal); } catch (SQLException ignored) {}
            }

        } catch (SQLException | RuntimeException e) {
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec updateAvecChangementDeCle option : " + ancienneOption);
            return false;
        } finally {
            closeIfNeeded(conn);
        }

        // ✅ Sync + outbox après succès
        enregistrerDansOutbox(nouvelle, "UPDATE");
        declencherSyncSiHorsTransaction(conn);

        final String src = usedSource;
        LOGGER.info(() -> "✅ Option renommée sur " + src
                + " + outbox : " + ancienneOption + " → " + nouvelle.getOption());
        return true;
    }

    // =========================================================
    // DELETE — par clé composite
    // =========================================================
    public boolean delete(String institutionId, String option, String classe,
                           String anneeAcademique) {

        if (institutionId == null || institutionId.isBlank()
                || option == null || option.isBlank()
                || classe == null || classe.isBlank()
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
                String cleComposite = institutionId + "|" + option + "|"
                        + classe + "|" + anneeAcademique;
                DeleteTracker.enregistrerSuppression(conn, TABLE, cleComposite, usedSource);

                // ② DELETE réel
                String sql = "DELETE FROM " + TABLE
                        + " WHERE institution_id = ? AND `option` = ? AND classe = ? AND annee_academique = ?";
                int rows;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                    ps.setString(1, institutionId);
                    ps.setString(2, option);
                    ps.setString(3, classe);
                    ps.setString(4, anneeAcademique);
                    rows = ps.executeUpdate();
                }

                conn.commit();

                if (rows > 0) {
                    // ✅ AJOUT : outbox DELETE + sync
                    enregistrerDeleteDansOutbox(institutionId, option, classe,
                            anneeAcademique, usedSource);
                    declencherSyncSiHorsTransaction(conn);
                    LOGGER.info(() -> "✅ Option supprimée : " + option
                            + " / " + classe + " + outbox (transaction)");
                }
                return rows > 0;

            } catch (SQLException e) {
                try { conn.rollback(); } catch (SQLException ignored) {}
                LOGGER.log(Level.SEVERE, "❌ Rollback suppression option " + option, e);
                return false;
            } finally {
                try { conn.setAutoCommit(autoCommitOriginal); } catch (SQLException ignored) {}
            }

        } catch (SQLException | RuntimeException e) {
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec delete option : " + option);
            return false;
        } finally {
            closeIfNeeded(conn);
        }
    }

    // =========================================================
    // MAPPING
    // =========================================================
    private Options mapRow(ResultSet rs) throws SQLException {
        Options option = new Options();
        option.setInstitutionId(rs.getString("institution_id"));
        option.setOption(rs.getString("option"));
        option.setClasse(rs.getString("classe"));
        option.setAnneeAcademique(rs.getString("annee_academique"));
        option.setPeriode(rs.getString("periode"));
        option.setPromotion(rs.getString("promotion"));

        try {
            option.setSource(rs.getString("source"));
        } catch (SQLException e) {
            // Colonne source absente
        }
        return option;
    }

    // =========================================================
    // OUTBOX
    // =========================================================
    private void enregistrerDansOutbox(Options option, String operation) {
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
            ps.setString(3, buildPrimaryKey(option));
            ps.setString(4, toJson(option));
            ps.executeUpdate();

            LOGGER.fine(() -> "📥 Outbox " + operation + " : " + option.getOption());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Impossible d'enregistrer dans l'outbox : " + option.getOption());
        }
    }

    private void enregistrerDeleteDansOutbox(String institutionId, String option,
                                                String classe, String anneeAcademique,
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
            ps.setString(2, institutionId + "|" + option + "|" + classe + "|" + anneeAcademique);
            ps.setString(3, "{\"institutionId\":\"" + escapeJson(institutionId) + "\","
                    + "\"option\":\"" + escapeJson(option) + "\","
                    + "\"classe\":\"" + escapeJson(classe) + "\","
                    + "\"anneeAcademique\":\"" + escapeJson(anneeAcademique) + "\","
                    + "\"source\":\"" + escapeJson(source) + "\"}");
            ps.executeUpdate();

            LOGGER.fine(() -> "📥 Outbox DELETE : " + option);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Impossible d'enregistrer le DELETE dans l'outbox");
        }
    }

    private String buildPrimaryKey(Options option) {
        return option.getInstitutionId() + "|" + option.getOption() + "|"
                + option.getClasse() + "|" + option.getAnneeAcademique();
    }

    private String toJson(Options option) {
        try {
            return GSON.toJson(option);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, e, () -> "Impossible de sérialiser l'option en JSON");
            return "{}";
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
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