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

public class ProgrammeData {

    private static final Logger LOGGER = Logger.getLogger(ProgrammeData.class.getName());
    private static final String TABLE = MigrationManager.TABLE_PROGRAMMES;

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

    public ProgrammeData() {
        this.connection = null;
    }

    public ProgrammeData(Connection connection) {
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
    public boolean create(Programme programme) {
        if (programme == null) {
            throw new IllegalArgumentException("Programme null");
        }

        // ✅ Générer un UUID si absent
        if (programme.getId() == null || programme.getId().isBlank()) {
            programme.setId(UUID.randomUUID().toString());
        }

        Connection conn = getPreferredWriteConnection();
        if (conn == null) {
            LOGGER.severe(() -> "❌ Aucune connexion disponible pour créer programme");
            return false;
        }

        boolean ok = false;
        String usedSource = null;
        try {
            usedSource = getSource(conn);
            ok = createSansSync(conn, programme, usedSource);
            if (ok) declencherSyncSiHorsTransaction(conn);
        } catch (SQLException | RuntimeException e) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec création programme sur " + srcErr);
        } finally {
            closeIfNeeded(conn);
        }

        if (ok) {
            enregistrerDansOutbox(programme, "INSERT");

            final String src = usedSource;
            LOGGER.info(() -> "✅ Programme créé sur " + src + " + outbox");
        }
        return ok;
    }

    private boolean createSansSync(Connection conn, Programme programme, String source)
            throws SQLException {
        String sql = "INSERT INTO " + TABLE + " " +
                "(institution_id, id, professeur_id, code_cours, matiere, classe, periode, " +
                "annee_academique, titre_chapitre, description, date_debut, date_fin, " +
                "est_termine, cotation, chapitre, source) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);

            int i = 1;
            ps.setString(i++, programme.getInstitutionId());
            ps.setString(i++, programme.getId());
            ps.setString(i++, programme.getProfesseurId());
            ps.setString(i++, programme.getCodeCours());
            // ✅ Bug corrigé : "matiere" ≠ code_cours
            ps.setString(i++, programme.getMatiere() != null
                    ? programme.getMatiere() : "");
            ps.setString(i++, programme.getClasse());
            ps.setString(i++, programme.getPeriode());
            ps.setString(i++, programme.getAnneeAcademique());
            ps.setString(i++, programme.getTitreChapitre());
            ps.setString(i++, programme.getDescription() != null
                    ? programme.getDescription() : "");
            ps.setDate(i++, programme.getDateDebut() != null
                    ? new java.sql.Date(programme.getDateDebut().getTime()) : null);
            ps.setDate(i++, programme.getDateFin() != null
                    ? new java.sql.Date(programme.getDateFin().getTime()) : null);
            ps.setBoolean(i++, programme.isEstTermine());
            ps.setInt(i++, programme.getCotation());
            // ✅ "chapitre" = titre_chapitre
            ps.setString(i++, programme.getTitreChapitre() != null
                    ? programme.getTitreChapitre() : "");
            ps.setString(i, source != null ? source : getSource(conn));

            return ps.executeUpdate() > 0;
        }
    }

    // =========================================================
    // UPDATE — LOCAL uniquement + outbox
    // =========================================================
    public boolean update(Programme programme) {
        if (programme == null || programme.getId() == null || programme.getId().isBlank()) {
            throw new IllegalArgumentException("Programme ou id null");
        }

        Connection conn = getPreferredWriteConnection();
        if (conn == null) return false;

        boolean ok = false;
        String usedSource = null;
        try {
            usedSource = getSource(conn);
            ok = updateSansSync(conn, programme, usedSource);
            if (ok) declencherSyncSiHorsTransaction(conn);
        } catch (SQLException | RuntimeException e) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec update programme sur " + srcErr);
        } finally {
            closeIfNeeded(conn);
        }

        if (ok) {
            enregistrerDansOutbox(programme, "UPDATE");

            final String src = usedSource;
            LOGGER.info(() -> "✅ Programme mis à jour sur " + src + " + outbox");
        }
        return ok;
    }

    private boolean updateSansSync(Connection conn, Programme programme, String source)
            throws SQLException {
        // ✅ Ajout de source = ?
        String sql = "UPDATE " + TABLE + " SET " +
                "titre_chapitre=?, description=?, date_debut=?, date_fin=?, " +
                "est_termine=?, cotation=?, source=? " +
                "WHERE id=? AND institution_id=? AND professeur_id=?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);

            int i = 1;
            ps.setString(i++, programme.getTitreChapitre());
            ps.setString(i++, programme.getDescription() != null
                    ? programme.getDescription() : "");
            ps.setDate(i++, programme.getDateDebut() != null
                    ? new java.sql.Date(programme.getDateDebut().getTime()) : null);
            ps.setDate(i++, programme.getDateFin() != null
                    ? new java.sql.Date(programme.getDateFin().getTime()) : null);
            ps.setBoolean(i++, programme.isEstTermine());
            ps.setInt(i++, programme.getCotation());
            ps.setString(i++, source != null ? source : getSource(conn));
            ps.setString(i++, programme.getId());
            ps.setString(i++, programme.getInstitutionId());
            ps.setString(i, programme.getProfesseurId());

            return ps.executeUpdate() > 0;
        }
    }

    // =========================================================
    // DELETE — LOCAL uniquement + tombstone + outbox
    // =========================================================
    public boolean delete(String id, String institutionId, String professeurId) {
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
                        + " WHERE id=? AND institution_id=? AND professeur_id=?";
                int rows;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                    ps.setString(1, id);
                    ps.setString(2, institutionId);
                    ps.setString(3, professeurId);
                    rows = ps.executeUpdate();
                }

                conn.commit();

                if (rows > 0) {
                    enregistrerDeleteDansOutbox(id, institutionId, usedSource);
                    declencherSyncSiHorsTransaction(conn);
                    LOGGER.info(() -> "✅ Programme supprimé : " + id + " + outbox");
                }
                return rows > 0;

            } catch (SQLException e) {
                try { conn.rollback(); } catch (SQLException ignored) {}
                LOGGER.log(Level.SEVERE, "Erreur suppression programme", e);
                return false;
            } finally {
                try { conn.setAutoCommit(autoCommitOriginal); } catch (SQLException ignored) {}
            }

        } catch (SQLException | RuntimeException e) {
            LOGGER.log(Level.SEVERE, e, () -> "❌ Échec delete programme : " + id);
            return false;
        } finally {
            closeIfNeeded(conn);
        }
    }

    // =========================================================
    // READ — Tous les programmes d'un professeur
    // =========================================================
    public List<Programme> getAllByProfesseur(String professeurId, String institutionId)
            throws SQLException {
        List<Programme> list = new ArrayList<>();
        String sql = "SELECT * FROM " + TABLE
                + " WHERE professeur_id = ? AND institution_id = ? "
                + "ORDER BY date_debut, id";

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, professeurId);
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

    // =========================================================
    // READ — Programmes filtrés
    // =========================================================
    public List<Programme> getByFiltres(String professeurId, String institutionId,
                                          String codeCours, String classe,
                                          String periode, String annee) throws SQLException {
        List<Programme> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT * FROM " + TABLE
                + " WHERE professeur_id = ? AND institution_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(professeurId);
        params.add(institutionId);

        if (codeCours != null && !codeCours.isBlank()) {
            sql.append(" AND code_cours = ?"); params.add(codeCours);
        }
        if (classe != null && !classe.isBlank()) {
            sql.append(" AND classe = ?"); params.add(classe);
        }
        if (periode != null && !periode.isBlank()) {
            sql.append(" AND periode = ?"); params.add(periode);
        }
        if (annee != null && !annee.isBlank()) {
            sql.append(" AND annee_academique = ?"); params.add(annee);
        }
        sql.append(" ORDER BY date_debut, id");

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) list.add(mapResultSet(rs));
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return list;
    }

    // =========================================================
    // READ — Programme par ID
    // =========================================================
    public Programme getById(String id, String institutionId, String professeurId)
            throws SQLException {
        if (id == null || id.isBlank()) return null;

        String sql = "SELECT * FROM " + TABLE
                + " WHERE id = ? AND institution_id = ? AND professeur_id = ?";

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, id);
                ps.setString(2, institutionId);
                ps.setString(3, professeurId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return mapResultSet(rs);
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return null;
    }

    // =========================================================
    // OUTBOX
    // =========================================================
    private void enregistrerDansOutbox(Programme p, String operation) {
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
            ps.setString(3, cleComposite(p.getId(), p.getInstitutionId()));
            ps.setString(4, toJson(p));
            ps.executeUpdate();
            LOGGER.fine(() -> "📥 Outbox " + operation + " : " + p.getId());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Impossible d'enregistrer dans l'outbox : " + p.getId());
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

    private String toJson(Programme p) {
        try {
            return GSON.toJson(p);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, e,
                    () -> "Impossible de sérialiser le programme en JSON");
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
    private Programme mapResultSet(ResultSet rs) throws SQLException {
        Programme p = new Programme();
        try { p.setId(rs.getString("id")); } catch (SQLException ignored) {}

        p.setInstitutionId(rs.getString("institution_id"));
        p.setProfesseurId(rs.getString("professeur_id"));
        p.setCodeCours(rs.getString("code_cours"));
        p.setClasse(rs.getString("classe"));
        p.setPeriode(rs.getString("periode"));
        p.setAnneeAcademique(rs.getString("annee_academique"));
        p.setTitreChapitre(rs.getString("titre_chapitre"));
        p.setDescription(rs.getString("description"));
        p.setDateDebut(rs.getDate("date_debut"));
        p.setDateFin(rs.getDate("date_fin"));
        p.setEstTermine(rs.getBoolean("est_termine"));
        p.setCotation(rs.getInt("cotation"));

        Timestamp ts = rs.getTimestamp("date_creation");
        if (ts != null) p.setCreatedAt(new java.util.Date(ts.getTime()));

        ts = rs.getTimestamp("date_modification");
        if (ts != null) p.setUpdatedAt(new java.util.Date(ts.getTime()));

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