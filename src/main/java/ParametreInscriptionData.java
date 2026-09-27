import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * DAO pour la gestion des paramètres d'inscription.
 *
 * ✅ ARCHITECTURE MULTI-INSTITUTIONS + UUID :
 *   - PK composite (institution_id, id) avec id VARCHAR(36) (UUID)
 *   - Clé unique métier : (institution_id, annee_academique, periode, classe)
 *   - UPSERT via INSERT ... ON DUPLICATE KEY UPDATE
 *
 * ✅ Cohérent avec DatabaseManager corrigé :
 *   - getSource() par comparaison d'URL JDBC
 *   - getPreferredWriteConnection() : LOCAL prioritaire, REMOTE secours
 *   - Enregistrement dans sync_outbox pour toutes les écritures
 *   - Déclenchement de sync après commit
 */
public class ParametreInscriptionData {

    private static final Logger LOGGER = Logger.getLogger(ParametreInscriptionData.class.getName());

    private static final String TABLE = MigrationManager.TABLE_PARAMETRES_INSCRIPTION;

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

    public ParametreInscriptionData() {
        this.connection = null;
    }

    public ParametreInscriptionData(Connection connection) {
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
    // LECTURE
    // =========================================================

    public List<ParametreInscription> getByInstitutionAnneePeriode(String institutionId,
                                                                    String annee,
                                                                    String periode) throws SQLException {
        List<ParametreInscription> list = new ArrayList<>();
        String sql = "SELECT * FROM " + TABLE + " "
                + "WHERE institution_id = ? AND annee_academique = ? AND periode = ? "
                + "ORDER BY classe";

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                ps.setString(2, annee);
                ps.setString(3, periode);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapResultSet(rs));
                    }
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return list;
    }

    public List<String[]> getAnneesPeriodes(String institutionId) throws SQLException {
        List<String[]> list = new ArrayList<>();
        String sql = "SELECT DISTINCT annee_academique, periode FROM " + TABLE + " "
                + "WHERE institution_id = ? ORDER BY annee_academique, periode";

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(new String[]{
                                rs.getString("annee_academique"),
                                rs.getString("periode")
                        });
                    }
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return list;
    }

    // =========================================================
    // SAUVEGARDE (UPSERT + UUID + transaction + sync après commit)
    // =========================================================

    /**
     * Sauvegarde la liste des paramètres pour une année/période donnée.
     *
     * ✅ Utilise INSERT ... ON DUPLICATE KEY UPDATE avec génération d'UUID.
     * ✅ Ne supprime PAS les enregistrements existants.
     * ✅ Enregistre dans sync_outbox après commit.
     */
       /**
     * Sauvegarde la liste des paramètres pour une année/période donnée.
     *
     * ✅ Utilise INSERT ... ON DUPLICATE KEY UPDATE avec génération d'UUID.
     * ✅ Ne supprime PAS les enregistrements existants.
     * ✅ Enregistre dans sync_outbox après commit.
     */
    public boolean saveAll(String institutionId, String annee, String periode,
                            List<ParametreInscription> parametres) {

        if (parametres == null || parametres.isEmpty()) {
            LOGGER.info("ℹ️ saveAll : aucun paramètre à enregistrer");
            return false;
        }

        Connection conn = getPreferredWriteConnection();
        if (conn == null) {
            LOGGER.severe("❌ Aucune connexion disponible pour saveAll");
            return false;
        }

        // ✅ conn est garanti non-null à partir d'ici → plus de test redondant
        boolean autoCommitOriginal = true;
        String usedSource ;
        try {
            usedSource = getSource(conn);
            autoCommitOriginal = conn.getAutoCommit();
            conn.setAutoCommit(false);

            String upsertSql = "INSERT INTO " + TABLE + " "
                    + "(institution_id, id, annee_academique, periode, classe, actif, promotion, source) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE "
                    + "  actif = VALUES(actif), "
                    + "  promotion = VALUES(promotion), "
                    + "  source = VALUES(source)";

            int nbTraites ;
            List<String[]> outboxEntries = new ArrayList<>();

            try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);

                for (ParametreInscription p : parametres) {
                    String instId = (p.getInstitutionId() != null && !p.getInstitutionId().isBlank())
                            ? p.getInstitutionId() : institutionId;
                    String anneeVal = (p.getAnneeAcademique() != null && !p.getAnneeAcademique().isBlank())
                            ? p.getAnneeAcademique() : annee;
                    String periodeVal = (p.getPeriode() != null && !p.getPeriode().isBlank())
                            ? p.getPeriode() : periode;

                    String idVal = (p.getId() != null && !p.getId().isBlank())
                            ? p.getId()
                            : UUID.randomUUID().toString();
                    p.setId(idVal);

                    ps.setString(1, instId);
                    ps.setString(2, idVal);
                    ps.setString(3, anneeVal);
                    ps.setString(4, periodeVal);
                    ps.setString(5, p.getClasse());
                    ps.setBoolean(6, p.isActif());
                    ps.setString(7, p.getPromotion());
                    ps.setString(8, usedSource);
                    ps.addBatch();

                    outboxEntries.add(new String[]{
                            instId + "|" + idVal,
                            toJson(p)
                    });
                }

                int[] resultats = ps.executeBatch();
                nbTraites = resultats.length;
            }

            conn.commit();

            // Enregistrement outbox APRÈS commit (hors transaction)
            for (String[] entry : outboxEntries) {
                enregistrerDansOutbox(entry[0], entry[1], "UPDATE");
            }

            declencherSyncSiHorsTransaction(conn);

            final int n = nbTraites;
            LOGGER.info(() -> "✅ " + n + " paramètres d'inscription traités (upsert)");
            return true;

        } catch (SQLException | RuntimeException e) {
            // ✅ Pas de if (conn != null) : conn est garanti non-null ici
            try {
                conn.rollback();
                LOGGER.warning("↩️ Rollback saveAll paramètres inscription");
            } catch (SQLException rollbackEx) {
                LOGGER.log(Level.SEVERE, "❌ Erreur rollback", rollbackEx);
            }
            LOGGER.log(Level.SEVERE, "❌ Erreur saveAll paramètres inscription", e);
            return false;

        } finally {
            // ✅ Pas de if (conn != null) : conn est garanti non-null ici
            try { conn.setAutoCommit(autoCommitOriginal); } catch (SQLException ignored) {}
            closeIfNeeded(conn);
        }
    }
    // =========================================================
    // SUPPRESSION
    // =========================================================

    /**
     * ✅ Utilise la clé métier + institution_id.
     */
    public boolean supprimer(String institutionId, String annee, String periode,
                              String classe) {

        Connection conn = getPreferredWriteConnection();
        if (conn == null) return false;

        boolean autoCommitOriginal;
        String usedSource ;
        try {
            usedSource = getSource(conn);
            autoCommitOriginal = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try {
                // ① Tombstone
                String cleMetier = institutionId + "|" + annee + "|" + periode + "|" + classe;
                DeleteTracker.enregistrerSuppression(conn, TABLE, cleMetier, usedSource);

                // ② DELETE réel
                String sql = "DELETE FROM " + TABLE + " "
                        + "WHERE institution_id = ? AND annee_academique = ? "
                        + "AND periode = ? AND classe = ?";
                int rows;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                    ps.setString(1, institutionId);
                    ps.setString(2, annee);
                    ps.setString(3, periode);
                    ps.setString(4, classe);
                    rows = ps.executeUpdate();
                }

                conn.commit();

                if (rows > 0) {
                    // ✅ Outbox DELETE + sync
                    enregistrerDeleteDansOutbox(institutionId, annee, periode,
                            classe, usedSource);
                    declencherSyncSiHorsTransaction(conn);
                    LOGGER.info(() -> "✅ Paramètre inscription supprimé : "
                            + annee + "/" + periode + "/" + classe);
                }
                return rows > 0;

            } catch (SQLException e) {
                try { conn.rollback(); } catch (SQLException ignored) {}
                LOGGER.log(Level.SEVERE,
                        "❌ Rollback suppression paramètre inscription", e);
                return false;
            } finally {
                try { conn.setAutoCommit(autoCommitOriginal); } catch (SQLException ignored) {}
            }

        } catch (SQLException | RuntimeException e) {
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec supprimer paramètre : " + classe);
            return false;
        } finally {
            closeIfNeeded(conn);
        }
    }

    // =========================================================
    // MAPPING
    // =========================================================
    private ParametreInscription mapResultSet(ResultSet rs) throws SQLException {
        ParametreInscription p = new ParametreInscription();

        try { p.setId(rs.getString("id")); } catch (SQLException ignored) {}

        p.setInstitutionId(rs.getString("institution_id"));
        p.setAnneeAcademique(rs.getString("annee_academique"));
        p.setPeriode(rs.getString("periode"));
        p.setClasse(rs.getString("classe"));
        p.setActif(rs.getBoolean("actif"));
        p.setPromotion(rs.getString("promotion"));

        try { p.setCreatedAt(rs.getTimestamp("created_at")); } catch (SQLException ignored) {}
        try { p.setUpdatedAt(rs.getTimestamp("updated_at")); } catch (SQLException ignored) {}
        try { p.setSource(rs.getString("source")); } catch (SQLException ignored) {}

        return p;
    }

    // =========================================================
    // OUTBOX
    // =========================================================
    private void enregistrerDansOutbox(String primaryKey, String json, String operation) {
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
            ps.setString(3, primaryKey);
            ps.setString(4, json);
            ps.executeUpdate();

            LOGGER.fine(() -> "📥 Outbox " + operation + " : " + primaryKey);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Impossible d'enregistrer dans l'outbox : " + primaryKey);
        }
    }

    private void enregistrerDeleteDansOutbox(String institutionId, String annee,
                                                String periode, String classe,
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
            ps.setString(2, institutionId + "|" + annee + "|" + periode + "|" + classe);
            ps.setString(3, "{\"institutionId\":\"" + escapeJson(institutionId) + "\","
                    + "\"anneeAcademique\":\"" + escapeJson(annee) + "\","
                    + "\"periode\":\"" + escapeJson(periode) + "\","
                    + "\"classe\":\"" + escapeJson(classe) + "\","
                    + "\"source\":\"" + escapeJson(source) + "\"}");
            ps.executeUpdate();

            LOGGER.fine(() -> "📥 Outbox DELETE : " + classe);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Impossible d'enregistrer le DELETE dans l'outbox");
        }
    }

    private String toJson(ParametreInscription p) {
        try {
            return GSON.toJson(p);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, e,
                    () -> "Impossible de sérialiser le paramètre en JSON");
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