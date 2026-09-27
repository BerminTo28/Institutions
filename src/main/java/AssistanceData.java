import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AssistanceData {

    private static final Logger LOGGER = Logger.getLogger(AssistanceData.class.getName());
    private static final String TABLE = MigrationManager.TABLE_ASSISTANCE;

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

    public AssistanceData() {
        this.connection = null;
    }

    public AssistanceData(Connection connection) {
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
    private static String cleComposite(String id, String institutionId) {
        return id + "|" + institutionId;
    }

    // =========================================================
    // CREATE — avec UUID + outbox
    // =========================================================
    public boolean create(Assistance assistance) {
        if (assistance == null) {
            throw new IllegalArgumentException("Assistance ne peut pas être nulle.");
        }

        // ✅ Générer un UUID si absent
        if (assistance.getId() == null || assistance.getId().isBlank()) {
            assistance.setId(UUID.randomUUID().toString());
        }

        Connection conn = getPreferredWriteConnection();
        if (conn == null) {
            LOGGER.severe(() -> "❌ Aucune connexion disponible pour créer assistance "
                    + assistance.getId());
            return false;
        }

        boolean ok = false;
        String usedSource = null;
        try {
            usedSource = getSource(conn);
            ok = createSansSync(conn, assistance, usedSource);
            if (ok) declencherSyncSiHorsTransaction(conn);
        } catch (SQLException | RuntimeException e) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec création assistance sur " + srcErr
                            + " : " + assistance.getId());
        } finally {
            closeIfNeeded(conn);
        }

        if (ok) {
            enregistrerDansOutbox(assistance, "INSERT");

            final String src = usedSource;
            LOGGER.info(() -> "✅ Assistance créée sur " + src
                    + " + outbox : " + assistance.getId());
        }
        return ok;
    }

    private boolean createSansSync(Connection conn, Assistance assistance, String source)
            throws SQLException {
        String sql = "INSERT INTO " + TABLE + " "
                + "(institution_id, id, etudiant_id, type_demande, sujet, message, statut, "
                + "date_creation, fichier_joint, source) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            int i = 1;
            ps.setString(i++, assistance.getInstitutionId());
            ps.setString(i++, assistance.getId());
            ps.setString(i++, assistance.getEtudiantId());
            ps.setString(i++, assistance.getTypeDemande());
            ps.setString(i++, assistance.getSujet());
            ps.setString(i++, assistance.getMessage());
            ps.setString(i++, assistance.getStatut() != null
                    ? assistance.getStatut() : "EN_ATTENTE");
            ps.setTimestamp(i++, assistance.getDateCreation() != null
                    ? new Timestamp(assistance.getDateCreation().getTime())
                    : new Timestamp(System.currentTimeMillis()));
            ps.setString(i++, assistance.getFichierJoint());
            ps.setString(i, source != null ? source : getSource(conn));

            return ps.executeUpdate() > 0;
        }
    }

    // =========================================================
    // READ
    // =========================================================
    public List<Assistance> getByEtudiant(String etudiantId, String institutionId)
            throws SQLException {
        List<Assistance> list = new ArrayList<>();
        String sql = "SELECT * FROM " + TABLE + " "
                + "WHERE institution_id = ? AND etudiant_id = ? "
                + "ORDER BY date_creation DESC";

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                ps.setString(2, etudiantId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) list.add(mapRow(rs));
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return list;
    }

    public List<Assistance> getByInstitution(String institutionId) throws SQLException {
        List<Assistance> list = new ArrayList<>();
        String sql = "SELECT * FROM " + TABLE + " "
                + "WHERE institution_id = ? ORDER BY date_creation DESC";

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

    public Assistance getById(String id, String institutionId) throws SQLException {
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
                    if (rs.next()) return mapRow(rs);
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return null;
    }

    // =========================================================
    // UPDATE
    // =========================================================
    public boolean update(Assistance assistance) {
        if (assistance == null || assistance.getId() == null || assistance.getId().isBlank()) {
            throw new IllegalArgumentException("Assistance invalide pour mise à jour.");
        }

        Connection conn = getPreferredWriteConnection();
        if (conn == null) return false;

        boolean updated = false;
        String usedSource = null;
        try {
            usedSource = getSource(conn);
            updated = updateSansSync(conn, assistance, usedSource);
            if (updated) declencherSyncSiHorsTransaction(conn);
        } catch (SQLException | RuntimeException e) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec update assistance sur " + srcErr
                            + " : " + assistance.getId());
        } finally {
            closeIfNeeded(conn);
        }

        if (updated) enregistrerDansOutbox(assistance, "UPDATE");

        return updated;
    }

    private boolean updateSansSync(Connection conn, Assistance assistance, String source)
            throws SQLException {
        // ✅ Ajout de source = ?
        String sql = "UPDATE " + TABLE + " SET type_demande = ?, sujet = ?, message = ?, "
                + "statut = ?, date_creation = ?, fichier_joint = ?, source = ? "
                + "WHERE institution_id = ? AND id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            int i = 1;
            ps.setString(i++, assistance.getTypeDemande());
            ps.setString(i++, assistance.getSujet());
            ps.setString(i++, assistance.getMessage());
            ps.setString(i++, assistance.getStatut());
            ps.setTimestamp(i++, assistance.getDateCreation() != null
                    ? new Timestamp(assistance.getDateCreation().getTime())
                    : new Timestamp(System.currentTimeMillis()));
            ps.setString(i++, assistance.getFichierJoint());
            ps.setString(i++, source != null ? source : getSource(conn));
            ps.setString(i++, assistance.getInstitutionId());
            ps.setString(i, assistance.getId());
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * ✅ Mise à jour du statut uniquement.
     */
    public boolean updateStatut(String id, String institutionId, String nouveauStatut) {
        if (id == null || id.isBlank()) {
            LOGGER.warning("updateStatut() : id null ou vide");
            return false;
        }

        Connection conn = getPreferredWriteConnection();
        if (conn == null) return false;

        boolean updated = false;
        String usedSource = null;
        try {
            usedSource = getSource(conn);
            updated = updateStatutSansSync(conn, id, institutionId, nouveauStatut, usedSource);
            if (updated) declencherSyncSiHorsTransaction(conn);
        } catch (SQLException | RuntimeException e) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec updateStatut assistance sur " + srcErr
                            + " : " + id);
        } finally {
            closeIfNeeded(conn);
        }

        if (updated) {
            enregistrerUpdateStatutDansOutbox(id, institutionId, nouveauStatut, usedSource);
        }

        return updated;
    }

    private boolean updateStatutSansSync(Connection conn, String id, String institutionId,
                                           String nouveauStatut, String source)
            throws SQLException {
        String sql = "UPDATE " + TABLE + " SET statut = ?, source = ? "
                + "WHERE institution_id = ? AND id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setString(1, nouveauStatut);
            ps.setString(2, source != null ? source : getSource(conn));
            ps.setString(3, institutionId);
            ps.setString(4, id);
            return ps.executeUpdate() > 0;
        }
    }

    // =========================================================
    // DELETE
    // =========================================================
    public boolean delete(String id, String institutionId) {
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
                    LOGGER.info(() -> "✅ Assistance supprimée : " + id + " + outbox");
                }
                return rows > 0;

            } catch (SQLException e) {
                try { conn.rollback(); } catch (SQLException ignored) {}
                LOGGER.log(Level.SEVERE, "❌ Erreur delete assistance", e);
                return false;
            } finally {
                try { conn.setAutoCommit(autoCommitOriginal); } catch (SQLException ignored) {}
            }

        } catch (SQLException | RuntimeException e) {
            LOGGER.log(Level.SEVERE, e, () -> "❌ Échec delete assistance : " + id);
            return false;
        } finally {
            closeIfNeeded(conn);
        }
    }

    // =========================================================
    // OUTBOX
    // =========================================================
    private void enregistrerDansOutbox(Assistance assistance, String operation) {
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
            ps.setString(3, cleComposite(assistance.getId(), assistance.getInstitutionId()));
            ps.setString(4, toJson(assistance));
            ps.executeUpdate();
            LOGGER.fine(() -> "📥 Outbox " + operation + " : " + assistance.getId());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Impossible d'enregistrer dans l'outbox : " + assistance.getId());
        }
    }

    private void enregistrerUpdateStatutDansOutbox(String id, String institutionId,
                                                     String nouveauStatut, String source) {
        String sql = """
            INSERT INTO sync_outbox
              (table_name, operation, primary_key, row_data, target_source, statut)
            VALUES (?, 'UPDATE', ?, ?, 'REMOTE', 'PENDING')
        """;
        try (Connection conn = db().getLocalConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(OUTBOX_TIMEOUT_SECONDS);
            ps.setString(1, TABLE);
            ps.setString(2, cleComposite(id, institutionId));
            ps.setString(3, "{\"id\":\"" + escapeJson(id) + "\","
                    + "\"institutionId\":\"" + escapeJson(institutionId) + "\","
                    + "\"statut\":\"" + escapeJson(nouveauStatut) + "\","
                    + "\"source\":\"" + escapeJson(source) + "\"}");
            ps.executeUpdate();
            LOGGER.fine(() -> "📥 Outbox UPDATE statut : " + id);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Impossible d'enregistrer l'UPDATE statut dans l'outbox");
        }
    }

    private void enregistrerDeleteDansOutbox(String id, String institutionId, String source) {
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
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Impossible d'enregistrer le DELETE dans l'outbox");
        }
    }

    private String toJson(Assistance a) {
        try {
            return GSON.toJson(a);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, e,
                    () -> "Impossible de sérialiser l'assistance en JSON");
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
    private Assistance mapRow(ResultSet rs) throws SQLException {
        Assistance a = new Assistance();
        try { a.setId(rs.getString("id")); } catch (SQLException ignored) {}

        a.setInstitutionId(rs.getString("institution_id"));
        a.setEtudiantId(rs.getString("etudiant_id"));
        a.setTypeDemande(rs.getString("type_demande"));
        a.setSujet(rs.getString("sujet"));
        a.setMessage(rs.getString("message"));
        a.setStatut(rs.getString("statut"));
        a.setDateCreation(rs.getTimestamp("date_creation"));
        a.setFichierJoint(rs.getString("fichier_joint"));

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