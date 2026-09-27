import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AccesProfesseurData {

    private static final Logger LOGGER = Logger.getLogger(AccesProfesseurData.class.getName());
    private static final String TABLE = MigrationManager.TABLE_ACCES_PROFESSEUR;

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

    public AccesProfesseurData() {
        this.connection = null;
    }

    public AccesProfesseurData(Connection connection) {
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
    private static String cleComposite(String numeroIdentifiant, String institutionId) {
        return numeroIdentifiant + "|" + institutionId;
    }

    // =========================================================
    // READ
    // =========================================================
    public AccesProfesseur getByProfesseur(String numeroIdentifiant, String institutionId)
            throws SQLException {
        if (numeroIdentifiant == null || numeroIdentifiant.isBlank()
                || institutionId == null || institutionId.isBlank()) {
            return null;
        }

        String sql = "SELECT * FROM " + TABLE
                + " WHERE numero_identifiant = ? AND institution_id = ?";

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, numeroIdentifiant);
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

    public List<AccesProfesseur> getAllByInstitution(String institutionId) throws SQLException {
        List<AccesProfesseur> list = new ArrayList<>();
        String sql = "SELECT * FROM " + TABLE
                + " WHERE institution_id = ? ORDER BY numero_identifiant";

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
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
    // INSERT — LOCAL uniquement + outbox
    // =========================================================
    public boolean insert(AccesProfesseur acces) {
        if (acces == null) throw new IllegalArgumentException("AccesProfesseur nul");

        Connection conn = getPreferredWriteConnection();
        if (conn == null) {
            LOGGER.severe(() -> "❌ Aucune connexion disponible pour insert acces_professeur "
                    + acces.getNumeroIdentifiant());
            return false;
        }

        boolean ok = false;
        String usedSource = null;
        try {
            usedSource = getSource(conn);
            ok = insertSansSync(conn, acces, usedSource);
            if (ok) declencherSyncSiHorsTransaction(conn);
        } catch (SQLException | RuntimeException e) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec insert acces_professeur sur " + srcErr
                            + " : " + acces.getNumeroIdentifiant());
        } finally {
            closeIfNeeded(conn);
        }

        if (ok) {
            enregistrerDansOutbox(acces, "INSERT");

            final String src = usedSource;
            LOGGER.info(() -> "✅ Acces professeur créé sur " + src
                    + " + outbox : " + acces.getNumeroIdentifiant());
        }
        return ok;
    }

    private boolean insertSansSync(Connection conn, AccesProfesseur acces, String source)
            throws SQLException {
        String sql = """
            INSERT INTO acces_professeur (
                numero_identifiant, institution_id, profil, cours, notes, edt, 
                absences, documents, messages, parametres, programmes, activites, source
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            setParameters(ps, acces);
            ps.setString(13, source != null ? source : getSource(conn));
            return ps.executeUpdate() > 0;
        }
    }

    // =========================================================
    // UPDATE — LOCAL uniquement + outbox
    // =========================================================
    public boolean update(AccesProfesseur acces) {
        if (acces == null) throw new IllegalArgumentException("AccesProfesseur nul");

        Connection conn = getPreferredWriteConnection();
        if (conn == null) return false;

        boolean ok = false;
        String usedSource = null;
        try {
            usedSource = getSource(conn);
            ok = updateSansSync(conn, acces, usedSource);
            if (ok) declencherSyncSiHorsTransaction(conn);
        } catch (SQLException | RuntimeException e) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec update acces_professeur sur " + srcErr
                            + " : " + acces.getNumeroIdentifiant());
        } finally {
            closeIfNeeded(conn);
        }

        if (ok) {
            enregistrerDansOutbox(acces, "UPDATE");

            final String src = usedSource;
            LOGGER.info(() -> "✅ Acces professeur mis à jour sur " + src
                    + " + outbox : " + acces.getNumeroIdentifiant());
        }
        return ok;
    }

    private boolean updateSansSync(Connection conn, AccesProfesseur acces, String source)
            throws SQLException {
        // ✅ Ajout de source = ?
        String sql = """
            UPDATE acces_professeur SET 
                profil=?, cours=?, notes=?, edt=?, absences=?, 
                documents=?, messages=?, parametres=?, programmes=?, activites=?, source=?
            WHERE numero_identifiant = ? AND institution_id = ?
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setBoolean(1, acces.isProfil());
            ps.setBoolean(2, acces.isCours());
            ps.setBoolean(3, acces.isNotes());
            ps.setBoolean(4, acces.isEdt());
            ps.setBoolean(5, acces.isAbsences());
            ps.setBoolean(6, acces.isDocuments());
            ps.setBoolean(7, acces.isMessages());
            ps.setBoolean(8, acces.isParametres());
            ps.setBoolean(9, acces.isProgrammes());
            ps.setBoolean(10, acces.isActivites());
            ps.setString(11, source != null ? source : getSource(conn));
            ps.setString(12, acces.getNumeroIdentifiant());
            ps.setString(13, acces.getInstitutionId());
            return ps.executeUpdate() > 0;
        }
    }

    // =========================================================
    // DELETE — LOCAL uniquement + tombstones + outbox
    // =========================================================

    /**
     * Supprime tous les accès professeur d'une institution.
     * Enregistre un tombstone par professeur supprimé.
     */
    public boolean deleteByInstitution(String institutionId) {
        if (institutionId == null || institutionId.isBlank()) {
            LOGGER.warning("deleteByInstitution() : institutionId invalide");
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
                // ① Lire TOUS les numero_identifiant
                List<String> ids = new ArrayList<>();
                String selectSql = "SELECT numero_identifiant FROM " + TABLE
                        + " WHERE institution_id = ?";
                try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                    ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                    ps.setString(1, institutionId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) ids.add(rs.getString("numero_identifiant"));
                    }
                }

                if (ids.isEmpty()) {
                    conn.rollback();
                    return false;
                }

                // ② Tombstone POUR CHAQUE ligne (clé composite)
                for (String id : ids) {
                    DeleteTracker.enregistrerSuppression(
                            conn, TABLE, cleComposite(id, institutionId), usedSource);
                }

                // ③ DELETE massif
                String sql = "DELETE FROM " + TABLE + " WHERE institution_id = ?";
                int rows;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                    ps.setString(1, institutionId);
                    rows = ps.executeUpdate();
                }

                conn.commit();

                if (rows > 0) {
                    enregistrerDeleteInstitutionDansOutbox(institutionId, usedSource);
                    declencherSyncSiHorsTransaction(conn);
                    final int r = rows, t = ids.size();
                    LOGGER.info(() -> "✅ " + r + " acces_professeur supprimés pour institution "
                            + institutionId + " (" + t + " tombstones) + outbox");
                }
                return rows > 0;

            } catch (SQLException e) {
                try { conn.rollback(); } catch (SQLException ignored) {}
                LOGGER.log(Level.SEVERE, "❌ Erreur deleteByInstitution acces_professeur", e);
                return false;
            } finally {
                try { conn.setAutoCommit(autoCommitOriginal); } catch (SQLException ignored) {}
            }

        } catch (SQLException | RuntimeException e) {
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec deleteByInstitution acces_professeur");
            return false;
        } finally {
            closeIfNeeded(conn);
        }
    }

    /**
     * Supprime un accès professeur spécifique.
     */
    public boolean deleteByProfesseur(String numeroIdentifiant, String institutionId) {
        if (numeroIdentifiant == null || numeroIdentifiant.isBlank()
                || institutionId == null || institutionId.isBlank()) {
            LOGGER.warning("deleteByProfesseur() : paramètres invalides");
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
                        conn, TABLE, cleComposite(numeroIdentifiant, institutionId), usedSource);

                // ② DELETE réel
                String sql = "DELETE FROM " + TABLE
                        + " WHERE numero_identifiant = ? AND institution_id = ?";
                int rows;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                    ps.setString(1, numeroIdentifiant);
                    ps.setString(2, institutionId);
                    rows = ps.executeUpdate();
                }

                conn.commit();

                if (rows > 0) {
                    enregistrerDeleteDansOutbox(numeroIdentifiant, institutionId, usedSource);
                    declencherSyncSiHorsTransaction(conn);
                    LOGGER.info(() -> "✅ Acces professeur supprimé : "
                            + numeroIdentifiant + " + outbox");
                }
                return rows > 0;

            } catch (SQLException e) {
                try { conn.rollback(); } catch (SQLException ignored) {}
                LOGGER.log(Level.SEVERE, "❌ Erreur deleteByProfesseur", e);
                return false;
            } finally {
                try { conn.setAutoCommit(autoCommitOriginal); } catch (SQLException ignored) {}
            }

        } catch (SQLException | RuntimeException e) {
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec deleteByProfesseur : " + numeroIdentifiant);
            return false;
        } finally {
            closeIfNeeded(conn);
        }
    }

    // =========================================================
    // OUTBOX
    // =========================================================
    private void enregistrerDansOutbox(AccesProfesseur acces, String operation) {
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
            ps.setString(3, cleComposite(acces.getNumeroIdentifiant(), acces.getInstitutionId()));
            ps.setString(4, toJson(acces));
            ps.executeUpdate();
            LOGGER.fine(() -> "📥 Outbox " + operation + " : " + acces.getNumeroIdentifiant());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Impossible d'enregistrer dans l'outbox : "
                            + acces.getNumeroIdentifiant());
        }
    }

    private void enregistrerDeleteDansOutbox(String numeroIdentifiant, String institutionId,
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
            ps.setString(2, cleComposite(numeroIdentifiant, institutionId));
            ps.setString(3, "{\"numeroIdentifiant\":\"" + escapeJson(numeroIdentifiant) + "\","
                    + "\"institutionId\":\"" + escapeJson(institutionId) + "\","
                    + "\"source\":\"" + escapeJson(source) + "\"}");
            ps.executeUpdate();
            LOGGER.fine(() -> "📥 Outbox DELETE : " + numeroIdentifiant);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Impossible d'enregistrer le DELETE dans l'outbox");
        }
    }

    private void enregistrerDeleteInstitutionDansOutbox(String institutionId, String source) {
        String sql = """
            INSERT INTO sync_outbox
              (table_name, operation, primary_key, row_data, target_source, statut)
            VALUES (?, 'DELETE', ?, ?, 'REMOTE', 'PENDING')
        """;
        try (Connection conn = db().getLocalConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(OUTBOX_TIMEOUT_SECONDS);
            ps.setString(1, TABLE);
            ps.setString(2, "INSTITUTION|" + institutionId);
            ps.setString(3, "{\"institutionId\":\"" + escapeJson(institutionId) + "\","
                    + "\"scope\":\"INSTITUTION\","
                    + "\"source\":\"" + escapeJson(source) + "\"}");
            ps.executeUpdate();
            LOGGER.fine(() -> "📥 Outbox DELETE institution : " + institutionId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Impossible d'enregistrer le DELETE institution dans l'outbox");
        }
    }

    private String toJson(AccesProfesseur acces) {
        try {
            return GSON.toJson(acces);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, e,
                    () -> "Impossible de sérialiser l'accès professeur en JSON");
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
    private AccesProfesseur mapResultSet(ResultSet rs) throws SQLException {
        AccesProfesseur acces = new AccesProfesseur();
        acces.setNumeroIdentifiant(rs.getString("numero_identifiant"));
        acces.setInstitutionId(rs.getString("institution_id"));
        acces.setProfil(rs.getBoolean("profil"));
        acces.setCours(rs.getBoolean("cours"));
        acces.setNotes(rs.getBoolean("notes"));
        acces.setEdt(rs.getBoolean("edt"));
        acces.setAbsences(rs.getBoolean("absences"));
        acces.setDocuments(rs.getBoolean("documents"));
        acces.setMessages(rs.getBoolean("messages"));
        acces.setParametres(rs.getBoolean("parametres"));
        acces.setProgrammes(rs.getBoolean("programmes"));
        acces.setActivites(rs.getBoolean("activites"));

        try {
            acces.setSource(rs.getString("source"));
        } catch (SQLException ignored) {
            // colonne source absente
        }

        return acces;
    }

    private void setParameters(PreparedStatement ps, AccesProfesseur acces) throws SQLException {
        ps.setString(1, acces.getNumeroIdentifiant());
        ps.setString(2, acces.getInstitutionId());
        ps.setBoolean(3, acces.isProfil());
        ps.setBoolean(4, acces.isCours());
        ps.setBoolean(5, acces.isNotes());
        ps.setBoolean(6, acces.isEdt());
        ps.setBoolean(7, acces.isAbsences());
        ps.setBoolean(8, acces.isDocuments());
        ps.setBoolean(9, acces.isMessages());
        ps.setBoolean(10, acces.isParametres());
        ps.setBoolean(11, acces.isProgrammes());
        ps.setBoolean(12, acces.isActivites());
        // position 13 = source (mise par l'appelant)
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