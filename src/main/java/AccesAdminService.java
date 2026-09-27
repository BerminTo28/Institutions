import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AccesAdminService extends AccesAdminData {

    private static final Logger LOGGER = Logger.getLogger(AccesAdminService.class.getName());
    private static final String TABLE = MigrationManager.TABLE_ACCES_ADMIN;

    /** Timeout SQL général. */
    private static final int QUERY_TIMEOUT_SECONDS = 15;

    /** Timeout écriture outbox. */
    private static final int OUTBOX_TIMEOUT_SECONDS = 10;

    /** ✅ Connexion locale au service (ne dépend pas du parent). */
    private final Connection defaultConnection;

    public AccesAdminService(Connection connection) {
        super(connection);
        this.defaultConnection = connection;
    }

    // =========================================================
    // HELPERS
    // =========================================================
    private DatabaseManager db() { return DatabaseManager.getInstance(); }

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
     * ✅ Ferme la connexion si elle n'est PAS la connexion "par défaut" du service.
     *    Utilise `defaultConnection` (champ local) au lieu de `this.connection`.
     */
    private void closeIfNeeded(Connection conn) {
        if (conn != null && conn != defaultConnection) {
            try { if (!conn.isClosed()) conn.close(); } catch (SQLException ignored) {}
        }
    }

    /**
     * ✅ Clé composite sécurisée : encode les séparateurs pour éviter
     *    toute ambiguïté si utilisateurId ou institutionId contient '|' ou '%'.
     */
    private static String cleComposite(String utilisateurId, String institutionId) {
        return enc(utilisateurId) + "|" + enc(institutionId);
    }

    private static String enc(String s) {
        if (s == null) return "";
        return s.replace("%", "%25").replace("|", "%7C");
    }

    // =========================================================
    // LECTURE
    // =========================================================

    /**
     * ✅ Récupère les identifiants des utilisateurs d'une institution.
     */
    public List<String> getAllUserIds(String institutionId) {
        List<String> ids = new ArrayList<>();
        if (institutionId == null || institutionId.isBlank()) {
            LOGGER.warning("getAllUserIds() : institutionId null ou vide");
            return ids;
        }

        Set<String> seen = new LinkedHashSet<>();

        // 1) Base de la connexion héritée
        idsFromConnection(defaultConnection, institutionId, seen);

        // 2) Fallback sur l'autre base si les 2 sont saines
        DatabaseManager m = db();
        if (m.isLocalHealthy() && m.isRemoteHealthy()) {
            try (Connection otherConn = "LOCAL".equals(getSource(defaultConnection))
                    ? m.getRemoteConnection()
                    : m.getLocalConnection()) {
                idsFromConnection(otherConn, institutionId, seen);
            } catch (SQLException e) {
                LOGGER.fine(() -> "Fallback getAllUserIds échoué : " + e.getMessage());
            }
        }

        ids.addAll(seen);
        return ids;
    }

    private void idsFromConnection(Connection conn, String institutionId, Set<String> seen) {
        if (conn == null) return;
        String sql = "SELECT DISTINCT utilisateur_id FROM " + TABLE
                + " WHERE institution_id = ? ORDER BY utilisateur_id";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setString(1, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    seen.add(rs.getString("utilisateur_id"));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur getAllUserIds pour " + institutionId, e);
        }
    }

    @Deprecated
    public List<String> getAllUserIds() {
        LOGGER.warning("⚠️ getAllUserIds() déprécié — "
                + "utilisez getAllUserIds(institutionId)");
        List<String> ids = new ArrayList<>();
        String sql = "SELECT DISTINCT utilisateur_id FROM " + TABLE
                + " ORDER BY utilisateur_id";
        try (PreparedStatement ps = defaultConnection.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getString("utilisateur_id"));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur getAllUserIds()", e);
        }
        return ids;
    }

    // =========================================================
    // DELETE — LOCAL uniquement + tombstone + outbox
    // =========================================================
    public boolean deleteByUtilisateurId(String utilisateurId, String institutionId) {
        if (utilisateurId == null || utilisateurId.isBlank()
                || institutionId == null || institutionId.isBlank()) {
            LOGGER.warning("deleteByUtilisateurId() : paramètres invalides");
            return false;
        }

        Connection conn = getPreferredWriteConnection();
        if (conn == null) return false;

        boolean autoCommitOriginal = true;
        String usedSource ;
        boolean ok ;

        try {
            usedSource = getSource(conn);
            autoCommitOriginal = conn.getAutoCommit();
            conn.setAutoCommit(false);

            ok = deleteInBase(conn, utilisateurId, institutionId, usedSource);
            conn.commit();

            if (ok) {
                enregistrerDeleteDansOutbox(utilisateurId, institutionId, usedSource);

                // ✅ Sync déclenchée APRÈS commit — inconditionnellement
                declencherSyncApresCommit();

                final String src = usedSource;
                LOGGER.info(() -> "✅ Permissions supprimées : "
                        + utilisateurId + " / " + institutionId
                        + " (source=" + src + ") + outbox");
            }
            return ok;

        } catch (SQLException | RuntimeException e) {
            try { conn.rollback(); } catch (SQLException ignored) {}
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec deleteByUtilisateurId : " + utilisateurId);
            return false;
        } finally {
            try { conn.setAutoCommit(autoCommitOriginal); } catch (SQLException ignored) {}
            closeIfNeeded(conn);
        }
    }

    private boolean deleteInBase(Connection conn, String utilisateurId,
                                   String institutionId, String source)
            throws SQLException {
        // ✅ Tombstone avec clé composite
        DeleteTracker.enregistrerSuppression(
                conn, TABLE, cleComposite(utilisateurId, institutionId), source);

        String sql = "DELETE FROM " + TABLE
                + " WHERE utilisateur_id = ? AND institution_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setString(1, utilisateurId);
            ps.setString(2, institutionId);
            return ps.executeUpdate() > 0;
        }
    }

    @Deprecated
    public boolean deleteByUtilisateurId(String utilisateurId) {
        LOGGER.warning("⚠️ deleteByUtilisateurId(id) déprécié — "
                + "utilisez deleteByUtilisateurId(id, institutionId)");

        Connection conn = getPreferredWriteConnection();
        if (conn == null) return false;

        boolean autoCommitOriginal = true;
        String usedSource ;
        boolean ok ;

        try {
            usedSource = getSource(conn);
            autoCommitOriginal = conn.getAutoCommit();
            conn.setAutoCommit(false);

            // ① Lire les institutions concernées
            List<String> institutions = new ArrayList<>();
            String selectSql = "SELECT DISTINCT institution_id FROM " + TABLE
                    + " WHERE utilisateur_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, utilisateurId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) institutions.add(rs.getString("institution_id"));
                }
            }

            // ② Tombstones pour chaque institution
            for (String inst : institutions) {
                DeleteTracker.enregistrerSuppression(conn, TABLE,
                        cleComposite(utilisateurId, inst), usedSource);
            }

            // ③ DELETE global
            String sql = "DELETE FROM " + TABLE + " WHERE utilisateur_id = ?";
            int rows;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, utilisateurId);
                rows = ps.executeUpdate();
            }

            conn.commit();
            ok = rows > 0;

            if (ok) {
                for (String inst : institutions) {
                    enregistrerDeleteDansOutbox(utilisateurId, inst, usedSource);
                }
                declencherSyncApresCommit();

                final int nb = rows;
                final String src = usedSource;
                LOGGER.info(() -> "✅ Permissions supprimées (toutes institutions) : "
                        + utilisateurId + " (" + nb + " ligne(s), source=" + src + ")");
            }
            return ok;

        } catch (SQLException | RuntimeException e) {
            try { conn.rollback(); } catch (SQLException ignored) {}
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec deleteByUtilisateurId() : " + utilisateurId);
            return false;
        } finally {
            try { conn.setAutoCommit(autoCommitOriginal); } catch (SQLException ignored) {}
            closeIfNeeded(conn);
        }
    }

    // =========================================================
    // OUTBOX
    // =========================================================
    private void enregistrerDeleteDansOutbox(String utilisateurId, String institutionId,
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
            ps.setString(2, cleComposite(utilisateurId, institutionId));
            ps.setString(3, "{\"utilisateurId\":\"" + escapeJson(utilisateurId) + "\","
                    + "\"institutionId\":\"" + escapeJson(institutionId) + "\","
                    + "\"source\":\"" + escapeJson(source) + "\"}");
            ps.executeUpdate();
            LOGGER.fine(() -> "📥 Outbox DELETE : " + utilisateurId + " / " + institutionId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Impossible d'enregistrer le DELETE dans l'outbox");
        }
    }

    /**
     * ✅ Échappement JSON complet (backslash, quote, \n, \r, \t, caractères de contrôle).
     */
    private String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"'  -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    // =========================================================
    // SYNCHRONISATION
    // =========================================================
    /**
     * ✅ Appelée APRÈS un commit() de transaction.
     *    Comme on a désactivé l'autocommit pendant la transaction, on ne peut
     *    PAS tester conn.getAutoCommit() à ce moment-là (il vaut false).
     *    On déclenche donc la sync inconditionnellement.
     */
    private void declencherSyncApresCommit() {
        try {
            db().declencherSyncImmediateAsync();
            LOGGER.fine("⚡ Sync asynchrone déclenchée après commit.");
        } catch (RuntimeException e) {
            LOGGER.log(Level.FINE, "Impossible de déclencher la sync", e);
        }
    }
}