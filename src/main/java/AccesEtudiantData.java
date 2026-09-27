import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AccesEtudiantData {

    private static final Logger LOGGER = Logger.getLogger(AccesEtudiantData.class.getName());
    private static final String TABLE = MigrationManager.TABLE_ACCES;

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

    private final Connection connection;

    public AccesEtudiantData() { this.connection = null; }
    public AccesEtudiantData(Connection connection) { this.connection = connection; }

    // ============================================================
    // HELPERS
    // ============================================================
    private DatabaseManager db() { return DatabaseManager.getInstance(); }

    private Connection getConnection() throws SQLException {
        if (connection != null && !connection.isClosed()) return connection;
        return db().getConnection();
    }

    private boolean shouldClose(Connection conn) { return conn != connection; }

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

    // ============================================================
    // LECTURE
    // ============================================================
    public AccesEtudiant getByEtudiant(String numeroIdentifiant, String institutionId)
            throws SQLException {
        if (numeroIdentifiant == null || numeroIdentifiant.isBlank()
                || institutionId == null || institutionId.isBlank()) {
            return null;
        }

        // 1) Base active
        AccesEtudiant result = getFromConnection(connection, numeroIdentifiant, institutionId);
        if (result != null) return result;

        // 2) Fallback sur l'autre base (si les 2 sont saines)
        DatabaseManager m = db();
        if (m.isLocalHealthy() && m.isRemoteHealthy()) {
            try (Connection otherConn = "LOCAL".equals(getSource(connection))
                    ? m.getRemoteConnection()
                    : m.getLocalConnection()) {
                return getFromConnection(otherConn, numeroIdentifiant, institutionId);
            } catch (SQLException e) {
                LOGGER.fine(() -> "Fallback lecture acces échoué : " + e.getMessage());
            }
        }
        return null;
    }

    private AccesEtudiant getFromConnection(Connection conn, String numeroIdentifiant,
                                              String institutionId) {
        String sql = "SELECT * FROM " + TABLE
                + " WHERE numero_identifiant = ? AND institution_id = ?";
        Connection c = (conn != null) ? conn : connection;
        if (c == null) {
            try { c = getConnection(); } catch (SQLException e) { return null; }
        }
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setString(1, numeroIdentifiant);
            ps.setString(2, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapResultSet(rs);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lecture acces " + numeroIdentifiant, e);
        }
        return null;
    }

    public List<AccesEtudiant> getAllByInstitution(String institutionId) throws SQLException {
        Map<String, AccesEtudiant> byId = new LinkedHashMap<>();

        // 1) LOCAL
        try (Connection localConn = db().getLocalConnection()) {
            for (AccesEtudiant a : getFromConnectionAll(localConn, institutionId)) {
                if (a.getNumeroIdentifiant() != null) {
                    byId.put(a.getNumeroIdentifiant(), a);
                }
            }
        } catch (SQLException e) {
            LOGGER.fine(() -> "LOCAL indisponible : " + e.getMessage());
        }

        // 2) REMOTE (ajoute les absents)
        try (Connection remoteConn = db().getRemoteConnection()) {
            for (AccesEtudiant a : getFromConnectionAll(remoteConn, institutionId)) {
                if (a.getNumeroIdentifiant() != null
                        && !byId.containsKey(a.getNumeroIdentifiant())) {
                    byId.put(a.getNumeroIdentifiant(), a);
                }
            }
        } catch (SQLException e) {
            LOGGER.fine(() -> "REMOTE indisponible : " + e.getMessage());
        }

        return new ArrayList<>(byId.values());
    }

    private List<AccesEtudiant> getFromConnectionAll(Connection conn, String institutionId) {
        List<AccesEtudiant> list = new ArrayList<>();
        String sql = "SELECT * FROM " + TABLE
                + " WHERE institution_id = ? ORDER BY numero_identifiant";
        if (conn == null) return list;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setString(1, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapResultSet(rs));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.FINE, "Erreur getAllByInstitution sur une base", e);
        }
        return list;
    }

    // ============================================================
    // INSERT — LOCAL uniquement + outbox
    // ============================================================
    public boolean insert(AccesEtudiant acces) {
        if (acces == null) throw new IllegalArgumentException("AccesEtudiant nul");

        Connection conn = getPreferredWriteConnection();
        if (conn == null) {
            LOGGER.severe(() -> "❌ Aucune connexion disponible pour insert acces "
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
                    () -> "❌ Échec insert acces sur " + srcErr
                            + " : " + acces.getNumeroIdentifiant());
        } finally {
            closeIfNeeded(conn);
        }

        if (ok) {
            enregistrerDansOutbox(acces, "INSERT");

            final String src = usedSource;
            LOGGER.info(() -> "✅ Acces étudiant créé sur " + src
                    + " + outbox : " + acces.getNumeroIdentifiant());
        }
        return ok;
    }

    private boolean insertSansSync(Connection conn, AccesEtudiant acces, String source)
            throws SQLException {
        String sql = """
            INSERT INTO acces (numero_identifiant, institution_id, connexion, profil, notes, bulletin,
                               paiement, messages, documents, edt, attestations, absences, assistance,
                               activites, statistiques, source)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            setParameters(ps, acces);
            ps.setString(16, source != null ? source : getSource(conn));
            return ps.executeUpdate() > 0;
        }
    }

    // ============================================================
    // UPDATE — LOCAL uniquement + outbox
    // ============================================================
    public boolean update(AccesEtudiant acces) {
        if (acces == null) throw new IllegalArgumentException("AccesEtudiant nul");

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
                    () -> "❌ Échec update acces sur " + srcErr
                            + " : " + acces.getNumeroIdentifiant());
        } finally {
            closeIfNeeded(conn);
        }

        if (ok) {
            enregistrerDansOutbox(acces, "UPDATE");

            final String src = usedSource;
            LOGGER.info(() -> "✅ Acces étudiant mis à jour sur " + src
                    + " + outbox : " + acces.getNumeroIdentifiant());
        }
        return ok;
    }

    private boolean updateSansSync(Connection conn, AccesEtudiant acces, String source)
            throws SQLException {
        String sql = """
            UPDATE acces SET
                connexion=?, profil=?, notes=?, bulletin=?, paiement=?,
                messages=?, documents=?, edt=?, attestations=?, absences=?,
                assistance=?, activites=?, statistiques=?, source=?
            WHERE numero_identifiant = ? AND institution_id = ?
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setBoolean(1, acces.isConnexion());
            ps.setBoolean(2, acces.isProfil());
            ps.setBoolean(3, acces.isNotes());
            ps.setBoolean(4, acces.isBulletin());
            ps.setBoolean(5, acces.isPaiement());
            ps.setBoolean(6, acces.isMessages());
            ps.setBoolean(7, acces.isDocuments());
            ps.setBoolean(8, acces.isEdt());
            ps.setBoolean(9, acces.isAttestations());
            ps.setBoolean(10, acces.isAbsences());
            ps.setBoolean(11, acces.isAssistance());
            ps.setBoolean(12, acces.isActivites());
            ps.setBoolean(13, acces.isStatistiques());
            ps.setString(14, source != null ? source : getSource(conn));
            ps.setString(15, acces.getNumeroIdentifiant());
            ps.setString(16, acces.getInstitutionId());
            return ps.executeUpdate() > 0;
        }
    }

    // ============================================================
    // DELETE — LOCAL uniquement + tombstone composite + outbox
    // ============================================================
    public boolean delete(String numeroIdentifiant, String institutionId) {
        if (numeroIdentifiant == null || numeroIdentifiant.isBlank()
                || institutionId == null || institutionId.isBlank()) {
            LOGGER.warning("delete() : paramètres invalides");
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
                boolean ok = deleteInBase(conn, numeroIdentifiant, institutionId, usedSource);
                conn.commit();

                if (ok) {
                    enregistrerDeleteDansOutbox(numeroIdentifiant, institutionId, usedSource);
                    declencherSyncSiHorsTransaction(conn);
                    LOGGER.info(() -> "✅ Acces supprimé : " + numeroIdentifiant + " + outbox");
                }
                return ok;

            } catch (SQLException e) {
                try { conn.rollback(); } catch (SQLException ignored) {}
                LOGGER.log(Level.SEVERE, "❌ Erreur delete acces", e);
                return false;
            } finally {
                try { conn.setAutoCommit(autoCommitOriginal); } catch (SQLException ignored) {}
            }

        } catch (SQLException | RuntimeException e) {
            LOGGER.log(Level.SEVERE, e, () -> "❌ Échec delete acces");
            return false;
        } finally {
            closeIfNeeded(conn);
        }
    }

    private boolean deleteInBase(Connection conn, String numeroIdentifiant,
                                   String institutionId, String source) throws SQLException {
        // ✅ Tombstone AVEC clé composite (numero_identifiant|institution_id)
        DeleteTracker.enregistrerSuppression(
                conn, TABLE, cleComposite(numeroIdentifiant, institutionId), source);

        String sql = "DELETE FROM " + TABLE
                + " WHERE numero_identifiant = ? AND institution_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setString(1, numeroIdentifiant);
            ps.setString(2, institutionId);
            return ps.executeUpdate() > 0;
        }
    }

    // ============================================================
    // DELETE PAR INSTITUTION — LOCAL uniquement + tombstones composites + outbox
    // ============================================================
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
                boolean ok = deleteByInstitutionInBase(conn, institutionId, usedSource);
                conn.commit();

                if (ok) {
                    // ✅ Outbox DELETE global pour l'institution
                    enregistrerDeleteInstitutionDansOutbox(institutionId, usedSource);
                    declencherSyncSiHorsTransaction(conn);
                    LOGGER.info(() -> "✅ Acces supprimés pour institution "
                            + institutionId + " + outbox");
                }
                return ok;

            } catch (SQLException e) {
                try { conn.rollback(); } catch (SQLException ignored) {}
                LOGGER.log(Level.SEVERE, "❌ Erreur deleteByInstitution acces", e);
                return false;
            } finally {
                try { conn.setAutoCommit(autoCommitOriginal); } catch (SQLException ignored) {}
            }

        } catch (SQLException | RuntimeException e) {
            LOGGER.log(Level.SEVERE, e, () -> "❌ Échec deleteByInstitution acces");
            return false;
        } finally {
            closeIfNeeded(conn);
        }
    }

    /**
     * ✅ Supprime tous les acces d'une institution avec tombstones pour CHAQUE ligne.
     */
    private boolean deleteByInstitutionInBase(Connection conn, String institutionId,
                                                String source) throws SQLException {
        // ① Lire TOUS les numero_identifiant à supprimer
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
            return false;
        }

        // ② Tombstone POUR CHAQUE ligne (clé composite)
        for (String id : ids) {
            DeleteTracker.enregistrerSuppression(conn, TABLE, cleComposite(id, institutionId), source);
        }

        // ③ DELETE massif
        String deleteSql = "DELETE FROM " + TABLE + " WHERE institution_id = ?";
        int rows;
        try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setString(1, institutionId);
            rows = ps.executeUpdate();
        }

        final int r = rows, t = ids.size();
        LOGGER.info(() -> "✅ " + r + " acces supprimés pour institution " + institutionId
                + " (" + t + " tombstones créées)");
        return rows > 0;
    }

    // ============================================================
    // OUTBOX
    // ============================================================
    private void enregistrerDansOutbox(AccesEtudiant acces, String operation) {
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

    private String toJson(AccesEtudiant acces) {
        try {
            return GSON.toJson(acces);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, e,
                    () -> "Impossible de sérialiser l'accès étudiant en JSON");
            return "{}";
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // ============================================================
    // MAPPING
    // ============================================================
    private AccesEtudiant mapResultSet(ResultSet rs) throws SQLException {
        AccesEtudiant acces = new AccesEtudiant();
        acces.setNumeroIdentifiant(rs.getString("numero_identifiant"));
        acces.setInstitutionId(rs.getString("institution_id"));
        acces.setConnexion(rs.getBoolean("connexion"));
        acces.setProfil(rs.getBoolean("profil"));
        acces.setNotes(rs.getBoolean("notes"));
        acces.setBulletin(rs.getBoolean("bulletin"));
        acces.setPaiement(rs.getBoolean("paiement"));
        acces.setMessages(rs.getBoolean("messages"));
        acces.setDocuments(rs.getBoolean("documents"));
        acces.setEdt(rs.getBoolean("edt"));
        acces.setAttestations(rs.getBoolean("attestations"));
        acces.setAbsences(rs.getBoolean("absences"));
        acces.setAssistance(rs.getBoolean("assistance"));
        acces.setActivites(rs.getBoolean("activites"));
        acces.setStatistiques(rs.getBoolean("statistiques"));
        try { acces.setSource(rs.getString("source")); } catch (SQLException ignored) {}
        return acces;
    }

    private void setParameters(PreparedStatement ps, AccesEtudiant acces) throws SQLException {
        ps.setString(1, acces.getNumeroIdentifiant());
        ps.setString(2, acces.getInstitutionId());
        ps.setBoolean(3, acces.isConnexion());
        ps.setBoolean(4, acces.isProfil());
        ps.setBoolean(5, acces.isNotes());
        ps.setBoolean(6, acces.isBulletin());
        ps.setBoolean(7, acces.isPaiement());
        ps.setBoolean(8, acces.isMessages());
        ps.setBoolean(9, acces.isDocuments());
        ps.setBoolean(10, acces.isEdt());
        ps.setBoolean(11, acces.isAttestations());
        ps.setBoolean(12, acces.isAbsences());
        ps.setBoolean(13, acces.isAssistance());
        ps.setBoolean(14, acces.isActivites());
        ps.setBoolean(15, acces.isStatistiques());
    }

    // ============================================================
    // SYNC
    // ============================================================
    private void declencherSyncSiHorsTransaction(Connection conn) {
        try {
            if (conn != null && conn.getAutoCommit()) {
                db().declencherSyncImmediateAsync();
                LOGGER.fine("⚡ Sync asynchrone déclenchée (hors transaction).");
            } else {
                LOGGER.fine("ℹ️ Sync différée : transaction en cours.");
            }
        } catch (SQLException e) {
            LOGGER.log(Level.FINE, "Impossible de déclencher la sync", e);
        }
    }
}