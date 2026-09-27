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

public class MessageData {

    private static final Logger LOGGER = Logger.getLogger(MessageData.class.getName());
    private static final String TABLE = MigrationManager.TABLE_MESSAGES;

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

    public MessageData() {
        this.connection = null;
    }

    public MessageData(Connection connection) {
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
    // CREATE — LOCAL uniquement + outbox
    // =========================================================
    public boolean create(Message message) {
        if (message == null) {
            throw new IllegalArgumentException("Message nul");
        }

        // ✅ Générer un UUID si absent
        if (message.getId() == null || message.getId().isBlank()) {
            message.setId(UUID.randomUUID().toString());
        }

        Connection conn = getPreferredWriteConnection();
        if (conn == null) {
            LOGGER.severe(() -> "❌ Aucune connexion disponible pour créer message "
                    + message.getId());
            return false;
        }

        boolean ok = false;
        String usedSource = null;
        try {
            usedSource = getSource(conn);
            ok = createSansSync(conn, message, usedSource);
            if (ok) declencherSyncSiHorsTransaction(conn);
        } catch (SQLException | RuntimeException e) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec création message sur " + srcErr
                            + " : " + message.getId());
        } finally {
            closeIfNeeded(conn);
        }

        if (ok) {
            enregistrerDansOutbox(message, "INSERT");

            final String src = usedSource;
            LOGGER.info(() -> "✅ Message créé sur " + src
                    + " + outbox : " + message.getId());
        }
        return ok;
    }

    private boolean createSansSync(Connection conn, Message message, String source)
            throws SQLException {
        String sql = "INSERT INTO " + TABLE + " " +
                "(institution_id, id, expediteur_id, expediteur_type, " +
                "destinataire_id, destinataire_type, sujet, contenu, lu, date_envoi, " +
                "reponse_a, est_supprime_expediteur, est_supprime_destinataire, source) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);

            ps.setString(1, message.getInstitutionId());
            ps.setString(2, message.getId());
            ps.setString(3, message.getExpediteurId());
            ps.setString(4, message.getExpediteurType());
            ps.setString(5, message.getDestinataireId());
            ps.setString(6, message.getDestinataireType());
            ps.setString(7, message.getSujet());
            ps.setString(8, message.getContenu());
            ps.setBoolean(9, message.isLu());
            ps.setTimestamp(10, message.getDateEnvoi() != null
                    ? new Timestamp(message.getDateEnvoi().getTime())
                    : new Timestamp(System.currentTimeMillis()));
            ps.setString(11, message.getReponseA());
            ps.setBoolean(12, message.isEstSupprimeExpediteur());
            ps.setBoolean(13, message.isEstSupprimeDestinataire());
            ps.setString(14, source != null ? source : getSource(conn));

            return ps.executeUpdate() > 0;
        }
    }

    // =========================================================
    // UPDATE — LOCAL uniquement + outbox
    // =========================================================
    public boolean update(Message message) {
        if (message == null || message.getId() == null || message.getId().isBlank()) {
            throw new IllegalArgumentException("Message ou id nul");
        }

        Connection conn = getPreferredWriteConnection();
        if (conn == null) return false;

        boolean ok = false;
        String usedSource = null;
        try {
            usedSource = getSource(conn);
            ok = updateSansSync(conn, message, usedSource);
            if (ok) declencherSyncSiHorsTransaction(conn);
        } catch (SQLException | RuntimeException e) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec update message sur " + srcErr
                            + " : " + message.getId());
        } finally {
            closeIfNeeded(conn);
        }

        if (ok) {
            enregistrerDansOutbox(message, "UPDATE");

            final String src = usedSource;
            LOGGER.info(() -> "✅ Message mis à jour sur " + src
                    + " + outbox : " + message.getId());
        }
        return ok;
    }

    private boolean updateSansSync(Connection conn, Message message, String source)
            throws SQLException {
        // ✅ Ajout de source = ?
        String sql = "UPDATE " + TABLE + " SET " +
                "lu = ?, est_supprime_expediteur = ?, est_supprime_destinataire = ?, source = ? " +
                "WHERE id = ? AND institution_id = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);

            ps.setBoolean(1, message.isLu());
            ps.setBoolean(2, message.isEstSupprimeExpediteur());
            ps.setBoolean(3, message.isEstSupprimeDestinataire());
            ps.setString(4, source != null ? source : getSource(conn));
            ps.setString(5, message.getId());
            ps.setString(6, message.getInstitutionId());

            return ps.executeUpdate() > 0;
        }
    }

    // =========================================================
    // DELETE LOGIQUE — LOCAL uniquement + tombstone + outbox
    // =========================================================
    public boolean deleteLogique(String id, String institutionId, boolean pourExpediteur) {
        if (id == null || id.isBlank() || institutionId == null || institutionId.isBlank()) {
            LOGGER.warning("deleteLogique() : id ou institutionId null/vide");
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

                // ② UPDATE logique
                String column = pourExpediteur
                        ? "est_supprime_expediteur" : "est_supprime_destinataire";
                String sql = "UPDATE " + TABLE + " SET " + column + " = TRUE " +
                             "WHERE id = ? AND institution_id = ?";
                int rows;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                    ps.setString(1, id);
                    ps.setString(2, institutionId);
                    rows = ps.executeUpdate();
                }

                conn.commit();

                if (rows > 0) {
                    enregistrerDeleteDansOutbox(id, institutionId, usedSource);
                    declencherSyncSiHorsTransaction(conn);
                    LOGGER.info(() -> "✅ Message supprimé (logique) : "
                            + id + " + outbox");
                }
                return rows > 0;

            } catch (SQLException e) {
                try { conn.rollback(); } catch (SQLException ignored) {}
                LOGGER.log(Level.SEVERE, "❌ Erreur suppression logique message " + id, e);
                return false;
            } finally {
                try { conn.setAutoCommit(autoCommitOriginal); } catch (SQLException ignored) {}
            }

        } catch (SQLException | RuntimeException e) {
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec deleteLogique message : " + id);
            return false;
        } finally {
            closeIfNeeded(conn);
        }
    }

    // =========================================================
    // READ
    // =========================================================
    public Message findById(String id, String institutionId) throws SQLException {
        if (id == null || id.isBlank()) return null;

        String sql = "SELECT * FROM " + TABLE +
                     " WHERE id = ? AND institution_id = ?";

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, id);
                ps.setString(2, institutionId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return mapResultSet(rs);
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return null;
    }

    public List<Message> getReçus(String destinataireId, String destinataireType,
                                   String institutionId) throws SQLException {
        List<Message> list = new ArrayList<>();
        String sql = "SELECT * FROM " + TABLE +
                " WHERE destinataire_id = ? AND destinataire_type = ? AND institution_id = ? " +
                "AND est_supprime_destinataire = FALSE " +
                "ORDER BY date_envoi DESC";

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, destinataireId);
                ps.setString(2, destinataireType);
                ps.setString(3, institutionId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) list.add(mapResultSet(rs));
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return list;
    }

    public List<Message> getEnvoyés(String expediteurId, String expediteurType,
                                     String institutionId) throws SQLException {
        List<Message> list = new ArrayList<>();
        String sql = "SELECT * FROM " + TABLE +
                " WHERE expediteur_id = ? AND expediteur_type = ? AND institution_id = ? " +
                "AND est_supprime_expediteur = FALSE " +
                "ORDER BY date_envoi DESC";

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, expediteurId);
                ps.setString(2, expediteurType);
                ps.setString(3, institutionId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) list.add(mapResultSet(rs));
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return list;
    }

    public List<Message> getConversation(String reponseA, String institutionId)
            throws SQLException {
        List<Message> list = new ArrayList<>();
        String sql = "SELECT * FROM " + TABLE +
                " WHERE reponse_a = ? AND institution_id = ? " +
                "AND (est_supprime_expediteur = FALSE AND est_supprime_destinataire = FALSE) " +
                "ORDER BY date_envoi ASC";

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, reponseA);
                ps.setString(2, institutionId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) list.add(mapResultSet(rs));
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return list;
    }

    public int countNonLus(String destinataireId, String destinataireType,
                            String institutionId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + TABLE +
                " WHERE destinataire_id = ? AND destinataire_type = ? AND institution_id = ? " +
                "AND lu = FALSE AND est_supprime_destinataire = FALSE";

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, destinataireId);
                ps.setString(2, destinataireType);
                ps.setString(3, institutionId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt(1);
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return 0;
    }

    // =========================================================
    // OUTBOX
    // =========================================================
    private void enregistrerDansOutbox(Message message, String operation) {
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
            ps.setString(3, cleComposite(message.getId(), message.getInstitutionId()));
            ps.setString(4, toJson(message));
            ps.executeUpdate();
            LOGGER.fine(() -> "📥 Outbox " + operation + " : " + message.getId());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Impossible d'enregistrer dans l'outbox : " + message.getId());
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

    private String toJson(Message m) {
        try {
            return GSON.toJson(m);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, e,
                    () -> "Impossible de sérialiser le message en JSON");
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
    private Message mapResultSet(ResultSet rs) throws SQLException {
        Message m = new Message();
        m.setId(rs.getString("id"));
        m.setInstitutionId(rs.getString("institution_id"));
        m.setExpediteurId(rs.getString("expediteur_id"));
        m.setExpediteurType(rs.getString("expediteur_type"));
        m.setDestinataireId(rs.getString("destinataire_id"));
        m.setDestinataireType(rs.getString("destinataire_type"));
        m.setSujet(rs.getString("sujet"));
        m.setContenu(rs.getString("contenu"));
        m.setLu(rs.getBoolean("lu"));
        m.setDateEnvoi(rs.getTimestamp("date_envoi"));

        String reponseA = rs.getString("reponse_a");
        m.setReponseA(reponseA);

        m.setEstSupprimeExpediteur(rs.getBoolean("est_supprime_expediteur"));
        m.setEstSupprimeDestinataire(rs.getBoolean("est_supprime_destinataire"));

        try { m.setSource(rs.getString("source")); } catch (SQLException ignored) {}

        return m;
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