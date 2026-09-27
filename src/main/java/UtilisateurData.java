import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.mindrot.jbcrypt.BCrypt;

public class UtilisateurData {

    private static final Logger LOGGER = Logger.getLogger(UtilisateurData.class.getName());
    private static final String TABLE = MigrationManager.TABLE_ACCES_ADMIN;

    /** ✅ Timeout SQL général — 60s pour supporter les verrous concurrents. */
    private static final int QUERY_TIMEOUT_SECONDS = 60;

    /** Timeout écriture outbox. */
    private static final int OUTBOX_TIMEOUT_SECONDS = 10;

    /** ✅ Nombre max de tentatives en cas d'interruption MySQL. */
    private static final int MAX_RETRIES = 3;

    /** ✅ Délai de base entre tentatives (ms). */
    private static final long RETRY_BASE_DELAY_MS = 500L;

    private static final int MASK_READ   = 1;
    private static final int MASK_CREATE = 2;
    private static final int MASK_UPDATE = 4;
    private static final int MASK_DELETE = 8;

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

    public UtilisateurData(Connection connection) {
        if (connection == null) throw new IllegalArgumentException("Connexion requise");
        this.connection = connection;
    }

    public UtilisateurData() {
        this.connection = null;
    }

    // ============================================================
    // HELPERS
    // ============================================================
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

    private static String cleComposite(String utilisateurId, String institutionId) {
        return utilisateurId + "|" + institutionId;
    }

    // ============================================================
    // ✅ CREATE — VERSION TRANSACTIONNELLE (connexion fournie)
    // ============================================================
    /**
     * ✅ Crée un utilisateur EN UTILISANT LA CONNEXION FOURNIE.
     *    <p><b>À utiliser dans une transaction englobante</b> (ex: création
     *    institution + admin) pour éviter les deadlocks.</p>
     *
     * <p>Cette méthode :</p>
     * <ul>
     *   <li>N'ouvre PAS de nouvelle connexion</li>
     *   <li>Ne commit PAS (c'est l'appelant qui gère)</li>
     *   <li>Ne déclenche PAS la sync (c'est l'appelant qui gère)</li>
     * </ul>
     */
    public boolean create(Connection conn, Utilisateur utilisateur,
                           String motDePasseClair, String institutionId) {
        if (conn == null) {
            LOGGER.severe("create(conn, ...) : connexion null");
            return false;
        }
        if (utilisateur == null || motDePasseClair == null || motDePasseClair.isBlank()
                || institutionId == null || institutionId.isBlank()) {
            LOGGER.warning("create(conn, ...) : paramètres invalides");
            return false;
        }

        try {
            // ✅ Existence SUR LA MÊME CONNEXION
            if (existsInInstitution(conn, utilisateur.getEmail(), institutionId)) {
                LOGGER.warning(() -> "⚠️ Email déjà utilisé dans cette institution → update : "
                        + utilisateur.getEmail());
                return update(conn, utilisateur, motDePasseClair, institutionId);
            }

            String source = getSource(conn);
            boolean ok = createSansSync(conn, utilisateur, motDePasseClair,
                    institutionId, source);

            if (ok) {
                LOGGER.fine(() -> "🟢 Utilisateur créé (transaction englobante) : "
                        + utilisateur.getEmail());
            }
            return ok;

        } catch (SQLException | RuntimeException e) {
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec création utilisateur (transaction) : "
                            + utilisateur.getEmail());
            return false;
        }
    }

    // ============================================================
    // ✅ CREATE — VERSION AUTONOME (ouvre sa propre connexion)
    // ============================================================
    public boolean create(Utilisateur utilisateur, String motDePasseClair, String institutionId) {
        if (utilisateur == null || motDePasseClair == null || motDePasseClair.isBlank()
                || institutionId == null || institutionId.isBlank()) {
            LOGGER.warning("create() : paramètres invalides");
            return false;
        }

        // ✅ Si l'utilisateur existe déjà → update
        if (existsInInstitution(utilisateur.getEmail(), institutionId)) {
            LOGGER.warning(() -> "⚠️ Email déjà utilisé dans cette institution → update : "
                    + utilisateur.getEmail());
            return update(utilisateur, motDePasseClair, institutionId);
        }

        Connection conn = getPreferredWriteConnection();
        if (conn == null) {
            LOGGER.severe(() -> "❌ Aucune connexion disponible pour créer utilisateur "
                    + utilisateur.getEmail());
            return false;
        }

        boolean ok = false;
        String usedSource = null;
        try {
            usedSource = getSource(conn);
            ok = createSansSync(conn, utilisateur, motDePasseClair, institutionId, usedSource);

            if (ok) {
                // ✅ Enregistrer dans outbox
                enregistrerDansOutbox(utilisateur, institutionId, "INSERT");
                // ✅ Déclencher la sync (hors transaction)
                declencherSyncSiHorsTransaction(conn);

                final String src = usedSource;
                LOGGER.info(() -> "✅ Utilisateur créé sur " + src
                        + " + outbox : " + utilisateur.getEmail());
            }
        } catch (SQLException | RuntimeException e) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec création utilisateur sur " + srcErr
                            + " : " + utilisateur.getEmail());
        } finally {
            closeIfNeeded(conn);
        }

        return ok;
    }

    /**
     * ✅ INSERT bas niveau avec retry sur interruption MySQL.
     */
    private boolean createSansSync(Connection conn, Utilisateur utilisateur,
                                     String motDePasseClair, String institutionId,
                                     String source) throws SQLException {

        String hash = BCrypt.hashpw(motDePasseClair, BCrypt.gensalt(12));

        StringBuilder columns = new StringBuilder(
            "utilisateur_id, institution_id, nom, prenom, mot_de_passe, email, niveau, source"
        );
        StringBuilder placeholders = new StringBuilder("?, ?, ?, ?, ?, ?, ?, ?");

        for (AccesAdmin.Module module : AccesAdmin.Module.values()) {
            columns.append(", ").append(module.name().toLowerCase()).append("_perms");
            placeholders.append(", ?");
        }

        String sql = "INSERT INTO " + TABLE + " (" + columns + ") VALUES (" + placeholders + ")";

        Connection c = (conn != null) ? conn : getConnection();

        // ✅ Retry avec backoff sur MySQLQueryInterruptedException
        for (int tentative = 1; tentative <= MAX_RETRIES; tentative++) {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                int idx = 1;
                ps.setString(idx++, utilisateur.getEmail());
                ps.setString(idx++, institutionId);
                ps.setString(idx++, utilisateur.getNom());
                ps.setString(idx++, utilisateur.getPrenom());
                ps.setString(idx++, hash);
                ps.setString(idx++, utilisateur.getEmail());
                ps.setInt(idx++, utilisateur.getNiveau());
                ps.setString(idx++, source != null ? source : getSource(c));

                AccesAdmin acces = utilisateur.getAcces() != null
                        ? utilisateur.getAcces() : new AccesAdmin();
                for (AccesAdmin.Module module : AccesAdmin.Module.values()) {
                    EnumSet<AccesAdmin.Action> actions = acces.getPermissionsForModule(module);
                    ps.setInt(idx++, computeMask(actions));
                }

                int rows = ps.executeUpdate();
                LOGGER.fine(() -> "🟢 create() : " + rows + " ligne(s) pour "
                        + utilisateur.getEmail());
                return rows > 0;

            } catch (SQLException e) {
                // ✅ Retry uniquement sur MySQLQueryInterruptedException
                boolean retry = isInterruption(e) && tentative < MAX_RETRIES;
                if (retry) {
                    final int t = tentative;
                    final long delay = RETRY_BASE_DELAY_MS * t;
                    LOGGER.warning(() -> "⚠️ Tentative " + t + "/" + MAX_RETRIES
                            + " interrompue pour " + utilisateur.getEmail()
                            + " → retry dans " + delay + "ms");
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                    continue;
                }
                throw e;
            }
        }
        return false;
    }

    /** ✅ Détecte une interruption MySQL (query tuée par timeout / KILL). */
    private boolean isInterruption(SQLException e) {
        if (e == null) return false;
        String className = e.getClass().getName();
        if (className.contains("MySQLQueryInterruptedException")) return true;
        String msg = e.getMessage();
        return msg != null && (msg.contains("Query execution was interrupted")
                || msg.contains("interrupted")
                || msg.contains("lock wait timeout"));
    }

    // ============================================================
    // ✅ UPDATE — VERSION TRANSACTIONNELLE
    // ============================================================
    public boolean update(Connection conn, Utilisateur utilisateur,
                           String nouveauMotDePasseClair, String institutionId) {
        if (conn == null || utilisateur == null
                || institutionId == null || institutionId.isBlank()) {
            return false;
        }
        try {
            String source = getSource(conn);
            return updateSansSync(conn, utilisateur, nouveauMotDePasseClair,
                    institutionId, source);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec update utilisateur (transaction) : "
                            + utilisateur.getEmail());
            return false;
        }
    }

    // ============================================================
    // ✅ UPDATE — VERSION AUTONOME
    // ============================================================
    public boolean update(Utilisateur utilisateur, String nouveauMotDePasseClair,
                            String institutionId) {
        if (utilisateur == null || institutionId == null || institutionId.isBlank()) {
            return false;
        }

        if (!existsInInstitution(utilisateur.getEmail(), institutionId)) {
            LOGGER.warning(() -> "Utilisateur non trouvé dans " + institutionId
                    + " : " + utilisateur.getEmail());
            return false;
        }

        Connection conn = getPreferredWriteConnection();
        if (conn == null) return false;

        boolean ok = false;
        String usedSource = null;
        try {
            usedSource = getSource(conn);
            ok = updateSansSync(conn, utilisateur, nouveauMotDePasseClair,
                    institutionId, usedSource);

            if (ok) {
                enregistrerDansOutbox(utilisateur, institutionId, "UPDATE");
                declencherSyncSiHorsTransaction(conn);

                final String src = usedSource;
                LOGGER.info(() -> "✅ Utilisateur mis à jour sur " + src
                        + " + outbox : " + utilisateur.getEmail());
            }
        } catch (SQLException | RuntimeException e) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec update utilisateur sur " + srcErr
                            + " : " + utilisateur.getEmail());
        } finally {
            closeIfNeeded(conn);
        }

        return ok;
    }

    @Deprecated
    public boolean update(Utilisateur utilisateur, String nouveauMotDePasseClair) {
        LOGGER.warning("⚠️ update(user, pwd) déprécié — "
                + "utilisez update(user, pwd, institutionId)");
        return update(utilisateur, nouveauMotDePasseClair,
                utilisateur.getInstitutionId());
    }

    @Deprecated
    public boolean update(Utilisateur utilisateur) {
        return update(utilisateur, null);
    }

    private boolean updateSansSync(Connection conn, Utilisateur utilisateur,
                                     String nouveauMotDePasseClair, String institutionId,
                                     String source) throws SQLException {
        StringBuilder sql = new StringBuilder("UPDATE " + TABLE + " SET " +
                "nom = ?, prenom = ?, niveau = ?");
        List<Object> params = new ArrayList<>();
        params.add(utilisateur.getNom());
        params.add(utilisateur.getPrenom());
        params.add(utilisateur.getNiveau());

        if (nouveauMotDePasseClair != null && !nouveauMotDePasseClair.isBlank()) {
            sql.append(", mot_de_passe = ?");
            params.add(BCrypt.hashpw(nouveauMotDePasseClair, BCrypt.gensalt(12)));
        }

        AccesAdmin acces = utilisateur.getAcces() != null
                ? utilisateur.getAcces() : new AccesAdmin();
        for (AccesAdmin.Module module : AccesAdmin.Module.values()) {
            sql.append(", ").append(module.name().toLowerCase()).append("_perms = ?");
            params.add(computeMask(acces.getPermissionsForModule(module)));
        }

        sql.append(", source = ?");
        Connection c = (conn != null) ? conn : getConnection();
        params.add(source != null ? source : getSource(c));

        sql.append(" WHERE utilisateur_id = ? AND institution_id = ?");
        params.add(utilisateur.getEmail());
        params.add(institutionId);

        try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            return ps.executeUpdate() > 0;
        }
    }

    // ============================================================
    // ✅ EXISTS — VERSION TRANSACTIONNELLE
    // ============================================================
    public boolean existsInInstitution(Connection conn, String email, String institutionId) {
        if (conn == null) return false;
        if (email == null || email.isBlank()
                || institutionId == null || institutionId.isBlank()) {
            return false;
        }
        String sql = "SELECT 1 FROM " + TABLE
                + " WHERE utilisateur_id = ? AND institution_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setString(1, email);
            ps.setString(2, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur existsInInstitution(conn)", e);
            return false;
        }
    }

    // ============================================================
    // ✅ EXISTS — VERSION AUTONOME
    // ============================================================
    public boolean existsInInstitution(String email, String institutionId) {
        if (email == null || email.isBlank()
                || institutionId == null || institutionId.isBlank()) {
            return false;
        }
        String sql = "SELECT 1 FROM " + TABLE
                + " WHERE utilisateur_id = ? AND institution_id = ?";

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, email);
                ps.setString(2, institutionId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur existsInInstitution", e);
            return false;
        } finally {
            closeIfNeeded(conn);
        }
    }

    @Deprecated
    public boolean exists(String email) {
        LOGGER.warning("⚠️ exists(email) déprécié — utilisez existsInInstitution(email, inst)");
        String sql = "SELECT 1 FROM " + TABLE + " WHERE utilisateur_id = ?";

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, email);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur exists", e);
            return false;
        } finally {
            closeIfNeeded(conn);
        }
    }

    // ============================================================
    // READ
    // ============================================================
    public Utilisateur read(String email, String institutionId) {
        if (email == null || email.isBlank()
                || institutionId == null || institutionId.isBlank()) {
            return null;
        }
        String sql = "SELECT * FROM " + TABLE
                + " WHERE utilisateur_id = ? AND institution_id = ?";

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, email);
                ps.setString(2, institutionId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return mapUtilisateur(rs);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lecture utilisateur " + email, e);
        } finally {
            closeIfNeeded(conn);
        }
        return null;
    }

    @Deprecated
    public Utilisateur read(String email) {
        LOGGER.warning("⚠️ read(email) déprécié — utilisez read(email, institutionId)");
        String sql = "SELECT * FROM " + TABLE + " WHERE utilisateur_id = ?";

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, email);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return mapUtilisateur(rs);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lecture utilisateur", e);
        } finally {
            closeIfNeeded(conn);
        }
        return null;
    }

    public List<Utilisateur> readAllByInstitution(String institutionId) {
        List<Utilisateur> result = new ArrayList<>();
        if (institutionId == null || institutionId.isBlank()) return result;

        java.util.Map<String, Utilisateur> byId = new java.util.LinkedHashMap<>();

        for (Utilisateur u : readAllFromConnection(connection, institutionId)) {
            if (u.getIdentifiant() != null) byId.put(u.getIdentifiant(), u);
        }

        DatabaseManager m = db();
        if (m.isLocalHealthy() && m.isRemoteHealthy()) {
            try (Connection otherConn = "LOCAL".equals(getSource(connection))
                    ? m.getRemoteConnection()
                    : m.getLocalConnection()) {
                for (Utilisateur u : readAllFromConnection(otherConn, institutionId)) {
                    if (u.getIdentifiant() != null && !byId.containsKey(u.getIdentifiant())) {
                        byId.put(u.getIdentifiant(), u);
                    }
                }
            } catch (SQLException e) {
                LOGGER.fine(() -> "Fallback readAllByInstitution échoué : " + e.getMessage());
            }
        }

        result.addAll(byId.values());
        return result;
    }

    private List<Utilisateur> readAllFromConnection(Connection conn, String institutionId) {
        List<Utilisateur> list = new ArrayList<>();
        String sql = "SELECT * FROM " + TABLE
                + " WHERE institution_id = ? ORDER BY nom, prenom";
        if (conn == null) return list;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setString(1, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapUtilisateur(rs));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur readAllByInstitution", e);
        }
        return list;
    }

    // ============================================================
    // DELETE
    // ============================================================
    public boolean delete(String email, String institutionId) {
        if (email == null || email.isBlank()
                || institutionId == null || institutionId.isBlank()) {
            LOGGER.warning("delete() : paramètres invalides");
            return false;
        }

        Connection conn = getPreferredWriteConnection();
        if (conn == null) return false;

        boolean autoCommitOriginal;
        String usedSource;
        try {
            usedSource = getSource(conn);
            autoCommitOriginal = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try {
                boolean ok = deleteInBase(conn, email, institutionId, usedSource);
                conn.commit();

                if (ok) {
                    enregistrerDeleteDansOutbox(email, institutionId, usedSource);
                    declencherSyncSiHorsTransaction(conn);
                    LOGGER.info(() -> "✅ Utilisateur supprimé : " + email + " + outbox");
                }
                return ok;

            } catch (SQLException e) {
                try { conn.rollback(); } catch (SQLException ignored) {}
                LOGGER.log(Level.SEVERE, "Erreur delete utilisateur " + email, e);
                return false;
            } finally {
                try { conn.setAutoCommit(autoCommitOriginal); } catch (SQLException ignored) {}
            }

        } catch (SQLException | RuntimeException e) {
            LOGGER.log(Level.SEVERE, e, () -> "❌ Échec delete utilisateur : " + email);
            return false;
        } finally {
            closeIfNeeded(conn);
        }
    }

    @Deprecated
    public boolean delete(String email) {
        LOGGER.warning("⚠️ delete(email) déprécié — utilisez delete(email, institutionId)");

        Utilisateur u = read(email);
        if (u == null) return false;

        String institutionId = null;
        String sql = "SELECT institution_id FROM " + TABLE
                + " WHERE utilisateur_id = ? LIMIT 1";

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, email);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) institutionId = rs.getString("institution_id");
                }
            }
        } catch (SQLException ignored) {
        } finally {
            closeIfNeeded(conn);
        }

        if (institutionId == null) return false;
        return delete(email, institutionId);
    }

    private boolean deleteInBase(Connection conn, String email,
                                   String institutionId, String source)
            throws SQLException {
        DeleteTracker.enregistrerSuppression(
                conn, TABLE, cleComposite(email, institutionId), source);

        String sql = "DELETE FROM " + TABLE
                + " WHERE utilisateur_id = ? AND institution_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setString(1, email);
            ps.setString(2, institutionId);
            return ps.executeUpdate() > 0;
        }
    }

    // ============================================================
    // AUTHENTIFICATION
    // ============================================================
    public Utilisateur authentifier(String email, String motDePasseClair) {
        String sql = "SELECT * FROM " + TABLE + " WHERE email = ? OR utilisateur_id = ?";

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, email);
                ps.setString(2, email);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String hash = null;
                        try { hash = rs.getString("mot_de_passe"); } catch (SQLException ignored) {}
                        if (hash != null && BCrypt.checkpw(motDePasseClair, hash)) {
                            return mapUtilisateur(rs);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur authentification", e);
        } finally {
            closeIfNeeded(conn);
        }
        return null;
    }

    // ============================================================
    // OUTBOX
    // ============================================================
    private void enregistrerDansOutbox(Utilisateur u, String institutionId, String operation) {
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
            ps.setString(3, cleComposite(u.getEmail(), institutionId));
            ps.setString(4, toJson(u, institutionId));
            ps.executeUpdate();
            LOGGER.fine(() -> "📥 Outbox " + operation + " : " + u.getEmail());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Impossible d'enregistrer dans l'outbox : " + u.getEmail());
        }
    }

    private void enregistrerDeleteDansOutbox(String email, String institutionId, String source) {
        String sql = """
            INSERT INTO sync_outbox
              (table_name, operation, primary_key, row_data, target_source, statut)
            VALUES (?, 'DELETE', ?, ?, 'REMOTE', 'PENDING')
        """;
        try (Connection conn = db().getLocalConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(OUTBOX_TIMEOUT_SECONDS);
            ps.setString(1, TABLE);
            ps.setString(2, cleComposite(email, institutionId));
            ps.setString(3, "{\"email\":\"" + escapeJson(email) + "\","
                    + "\"institutionId\":\"" + escapeJson(institutionId) + "\","
                    + "\"source\":\"" + escapeJson(source) + "\"}");
            ps.executeUpdate();
            LOGGER.fine(() -> "📥 Outbox DELETE : " + email);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Impossible d'enregistrer le DELETE dans l'outbox");
        }
    }

    private String toJson(Utilisateur u, String institutionId) {
        try {
            java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("email", u.getEmail());
            map.put("nom", u.getNom());
            map.put("prenom", u.getPrenom());
            map.put("niveau", u.getNiveau());
            map.put("institutionId", institutionId);
            return GSON.toJson(map);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, e,
                    () -> "Impossible de sérialiser l'utilisateur en JSON");
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
    private Utilisateur mapUtilisateur(ResultSet rs) throws SQLException {
        String identifiant = rs.getString("utilisateur_id");
        String nom = rs.getString("nom");
        String prenom = rs.getString("prenom");
        String email = identifiant;
        int niveau = 0;
        try { niveau = rs.getInt("niveau"); } catch (SQLException ignored) {}

        String institutionId = null;
        try { institutionId = rs.getString("institution_id"); } catch (SQLException ignored) {}

        AccesAdmin acces = new AccesAdmin();
        for (AccesAdmin.Module module : AccesAdmin.Module.values()) {
            String colName = module.name().toLowerCase() + "_perms";
            int mask = 0;
            try { mask = rs.getInt(colName); } catch (SQLException ignored) {}
            acces.setPermissionsForModule(module, maskToActions(mask));
        }

        try { acces.setSource(rs.getString("source")); } catch (SQLException ignored) {}

        List<String> modules = new ArrayList<>();
        for (AccesAdmin.Module module : AccesAdmin.Module.values()) {
            if (acces.hasPermission(module, AccesAdmin.Action.READ)) {
                modules.add(module.name().toLowerCase());
            }
        }

        Utilisateur u = new Utilisateur(identifiant, nom, prenom, email, niveau,
                acces, modules);
        u.setInstitutionId(institutionId);
        return u;
    }

    // ============================================================
    // MASQUES
    // ============================================================
    private int computeMask(EnumSet<AccesAdmin.Action> actions) {
        if (actions == null) return 0;
        int mask = 0;
        if (actions.contains(AccesAdmin.Action.READ))   mask |= MASK_READ;
        if (actions.contains(AccesAdmin.Action.CREATE)) mask |= MASK_CREATE;
        if (actions.contains(AccesAdmin.Action.UPDATE)) mask |= MASK_UPDATE;
        if (actions.contains(AccesAdmin.Action.DELETE)) mask |= MASK_DELETE;
        return mask;
    }

    private EnumSet<AccesAdmin.Action> maskToActions(int mask) {
        EnumSet<AccesAdmin.Action> actions = EnumSet.noneOf(AccesAdmin.Action.class);
        if ((mask & MASK_READ)   != 0) actions.add(AccesAdmin.Action.READ);
        if ((mask & MASK_CREATE) != 0) actions.add(AccesAdmin.Action.CREATE);
        if ((mask & MASK_UPDATE) != 0) actions.add(AccesAdmin.Action.UPDATE);
        if ((mask & MASK_DELETE) != 0) actions.add(AccesAdmin.Action.DELETE);
        return actions;
    }

    // ============================================================
    // SYNC
    // ============================================================
    private void declencherSyncSiHorsTransaction(Connection conn) {
        try {
            if (conn != null && conn.getAutoCommit()) {
                db().declencherSyncImmediateAsync();
                LOGGER.fine("⚡ Sync asynchrone déclenchée (hors transaction).");
            }
        } catch (SQLException e) {
            LOGGER.log(Level.FINE, "Impossible de déclencher la sync", e);
        }
    }
}