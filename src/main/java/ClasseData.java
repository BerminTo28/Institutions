import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClasseData {

    private static final Logger LOGGER = Logger.getLogger(ClasseData.class.getName());
    private static final String TABLE = "classes";

    private static final com.google.gson.Gson GSON = new com.google.gson.GsonBuilder()
            .registerTypeAdapter(java.time.LocalDateTime.class,
                    (com.google.gson.JsonSerializer<java.time.LocalDateTime>)
                            (src, type, ctx) -> new com.google.gson.JsonPrimitive(src.toString()))
            .registerTypeAdapter(java.time.LocalDate.class,
                    (com.google.gson.JsonSerializer<java.time.LocalDate>)
                            (src, type, ctx) -> new com.google.gson.JsonPrimitive(src.toString()))
            .create();

    /** Timeout d'une requête SQL en secondes. */
    private static final int QUERY_TIMEOUT_SECONDS = 15;

    /** Timeout SQL pour les écritures outbox. */
    private static final int OUTBOX_TIMEOUT_SECONDS = 10;

    /** Connexion partagée optionnelle (null = mode autonome). */
    private final Connection connection;

    public ClasseData() {
        this.connection = null;
    }

    public ClasseData(Connection connection) {
        this.connection = connection;
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private DatabaseManager db() {
        return DatabaseManager.getInstance();
    }

    private Connection getConnection() throws SQLException {
        if (connection != null && !connection.isClosed()) return connection;
        return db().getConnection();
    }

    private boolean shouldClose(Connection conn) {
        return conn != connection;
    }

    private void closeIfNeeded(Connection conn) {
        if (shouldClose(conn) && conn != null) {
            try {
                if (!conn.isClosed()) conn.close();
            } catch (SQLException ignored) {
                // fermeture silencieuse
            }
        }
    }

    /**
     * Détermine la source (LOCAL/REMOTE) d'une connexion par son URL JDBC.
     * On ne peut plus se baser sur un ThreadLocal car DatabaseManager
     * retourne directement les connexions (sans proxy).
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
        try {
            return conn.isValid(5);
        } catch (SQLException e) {
            return false;
        }
    }

    // =========================================================
    // MAPPING
    // =========================================================

    public Classe mapResultSetToClasse(ResultSet rs) throws SQLException {
        Classe c = new Classe();
        c.setInstitutionId(rs.getString("institution_id"));
        c.setCodeClasse(rs.getString("code_classe"));
        c.setNomClasse(rs.getString("nom_classe"));
        c.setNiveauClasse(rs.getInt("niveau_classe"));
        c.setCapaciteClasse(rs.getInt("capacite_classe"));
        c.setNombreEtudiants(rs.getInt("nombre_etudiants"));
        c.setNombreMatieres(rs.getInt("nombre_matieres"));
        c.setPeriode(rs.getString("periode"));
        c.setAnneeAcademique(rs.getString("annee_academique"));
        c.setStatut(rs.getString("statut"));

        Timestamp timestamp = rs.getTimestamp("date_creation");
        if (timestamp != null) {
            c.setDateCreation(new java.util.Date(timestamp.getTime()));
        }

        c.setMoyennePassage(rs.getDouble("moyenne_passage"));
        c.setPromotion(rs.getString("promotion"));

        try {
            c.setSource(rs.getString("source"));
        } catch (SQLException ignored) {
            // colonne absente sur anciennes versions
        }

        try {
            Timestamp lm = rs.getTimestamp("last_modified");
            if (lm != null) c.setLastModified(lm.toLocalDateTime());
        } catch (SQLException ignored) {
            // colonne absente sur anciennes versions
        }

        return c;
    }

    // =========================================================
    // ÉCRITURE INTELLIGENTE — LOCAL D'ABORD, JAMAIS BLOQUANTE
    // =========================================================

    /**
     * Retourne la connexion d'écriture PRIVILÉGIÉE :
     *  1. LOCAL si configuré ET sain (priorité : rapide, jamais bloquant)
     *  2. REMOTE si LOCAL indisponible et REMOTE sain + migration terminée
     *  3. Sinon → LOCAL en dernier recours
     *  Ne lève jamais d'exception : renvoie null si aucune connexion.
     */
    private Connection getPreferredWriteConnection() {
        DatabaseManager m = db();

        // 1) LOCAL en priorité
        try {
            if (m.isLocalConfigured() && m.isLocalHealthy()) {
                Connection c = m.getLocalConnection();
                if (c != null && !c.isClosed() && isConnectionAlive(c)) {
                    return c;
                }
                closeIfNeeded(c);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "LOCAL indisponible pour écriture", e);
        }

        // 2) REMOTE en secours
        try {
            if (m.isRemoteConfigured() && m.isRemoteHealthy()
                    && m.isMigrationDistanteTerminee()) {
                Connection c = m.getRemoteConnection();
                if (c != null && !c.isClosed() && isConnectionAlive(c)) {
                    return c;
                }
                closeIfNeeded(c);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "REMOTE indisponible pour écriture", e);
        }

        // 3) Aucune base saine → on tente quand même LOCAL
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
    // CREATE
    // =========================================================

    public boolean ajouter(Classe classe) throws SQLException {
        if (classe == null) throw new IllegalArgumentException("Classe nulle");

        boolean writeOk = false;
        String usedSource = null;

        Connection conn = getPreferredWriteConnection();
        if (conn == null) {
            LOGGER.severe(() -> "❌ Aucune connexion disponible pour ajouter "
                    + classe.getCodeClasse());
            return false;
        }

        try {
            usedSource = getSource(conn);
            writeOk = ajouterSansSync(conn, classe, usedSource);

            if (writeOk) {
                declencherSyncSiHorsTransaction(conn);
            }
        } catch (SQLException e) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec ajout sur " + srcErr + " : " + classe.getCodeClasse());
        } finally {
            closeIfNeeded(conn);
        }

        if (!writeOk) {
            LOGGER.severe(() -> "❌ Ajout impossible (aucune base n'a accepté) : "
                    + classe.getCodeClasse());
            return false;
        }

        // Filet de sécurité : enqueue dans l'outbox
        enregistrerDansOutbox(classe, "INSERT");

        final String src = usedSource;
        LOGGER.info(() -> "✅ Classe ajoutée sur " + src
                + " + outbox : " + classe.getCodeClasse());
        return true;
    }

    private boolean ajouterSansSync(Connection conn, Classe classe, String source)
            throws SQLException {

        String sql = """
                INSERT INTO classes (institution_id, code_classe, nom_classe, niveau_classe,
                                     capacite_classe, nombre_etudiants, nombre_matieres, periode,
                                     annee_academique, statut, date_creation, moyenne_passage,
                                     promotion, source)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        Connection c = (conn != null) ? conn : getConnection();
        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);

            stmt.setString(1, classe.getInstitutionId());
            stmt.setString(2, classe.getCodeClasse());
            stmt.setString(3, classe.getNomClasse());
            stmt.setInt(4, classe.getNiveauClasse());
            stmt.setInt(5, classe.getCapaciteClasse());
            stmt.setInt(6, classe.getNombreEtudiants());
            stmt.setInt(7, classe.getNombreMatieres());
            stmt.setString(8, classe.getPeriode());
            stmt.setString(9, classe.getAnneeAcademique());
            stmt.setString(10, classe.getStatut());
            stmt.setTimestamp(11, classe.getDateCreation() != null
                    ? new Timestamp(classe.getDateCreation().getTime())
                    : new Timestamp(System.currentTimeMillis()));
            stmt.setDouble(12, classe.getMoyennePassage());
            stmt.setString(13, classe.getPromotion());
            stmt.setString(14, source != null ? source : getSource(c));

            return stmt.executeUpdate() > 0;
        }
    }

    // =========================================================
    // UPDATE
    // =========================================================

    public boolean modifier(Classe classe) throws SQLException {
        if (classe == null) throw new IllegalArgumentException("Classe nulle");

        boolean writeOk = false;
        String usedSource = null;

        Connection conn = getPreferredWriteConnection();
        if (conn == null) {
            LOGGER.severe(() -> "❌ Aucune connexion disponible pour modifier "
                    + classe.getCodeClasse());
            return false;
        }

        try {
            usedSource = getSource(conn);
            writeOk = modifierSansSync(conn, classe, usedSource);

            if (writeOk) {
                declencherSyncSiHorsTransaction(conn);
            }
        } catch (SQLException e) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec update sur " + srcErr + " : " + classe.getCodeClasse());
        } finally {
            closeIfNeeded(conn);
        }

        if (!writeOk) {
            LOGGER.severe(() -> "❌ Modification impossible : " + classe.getCodeClasse());
            return false;
        }

        enregistrerDansOutbox(classe, "UPDATE");

        final String src = usedSource;
        LOGGER.info(() -> "✅ Classe mise à jour sur " + src
                + " + outbox : " + classe.getCodeClasse());
        return true;
    }

    private boolean modifierSansSync(Connection conn, Classe classe, String source)
            throws SQLException {

        String sql = """
                UPDATE classes SET nom_classe = ?, niveau_classe = ?, capacite_classe = ?,
                                   nombre_etudiants = ?, nombre_matieres = ?, periode = ?,
                                   annee_academique = ?, statut = ?, moyenne_passage = ?,
                                   promotion = ?, source = ?
                WHERE code_classe = ? AND institution_id = ?
                """;

        Connection c = (conn != null) ? conn : getConnection();
        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);

            stmt.setString(1, classe.getNomClasse());
            stmt.setInt(2, classe.getNiveauClasse());
            stmt.setInt(3, classe.getCapaciteClasse());
            stmt.setInt(4, classe.getNombreEtudiants());
            stmt.setInt(5, classe.getNombreMatieres());
            stmt.setString(6, classe.getPeriode());
            stmt.setString(7, classe.getAnneeAcademique());
            stmt.setString(8, classe.getStatut());
            stmt.setDouble(9, classe.getMoyennePassage());
            stmt.setString(10, classe.getPromotion());
            stmt.setString(11, source != null ? source : getSource(c));
            stmt.setString(12, classe.getCodeClasse());
            stmt.setString(13, classe.getInstitutionId());

            return stmt.executeUpdate() > 0;
        }
    }

    // =========================================================
    // DELETE
    // =========================================================

    public boolean supprimer(String codeClasse, String institutionId) throws SQLException {
        if (codeClasse == null || codeClasse.isBlank()
                || institutionId == null || institutionId.isBlank()) {
            LOGGER.warning("supprimer() : codeClasse ou institutionId null/vide");
            return false;
        }

        boolean writeOk = false;
        String usedSource = null;

        Connection conn = getPreferredWriteConnection();
        if (conn == null) {
            LOGGER.severe(() -> "❌ Aucune connexion disponible pour supprimer " + codeClasse);
            return false;
        }

        try {
            usedSource = getSource(conn);
            writeOk = supprimerDansBase(conn, codeClasse, institutionId, usedSource);

            if (writeOk) {
                declencherSyncSiHorsTransaction(conn);
            }
        } catch (SQLException e) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec suppression sur " + srcErr + " : " + codeClasse);
        } finally {
            closeIfNeeded(conn);
        }

        if (!writeOk) {
            LOGGER.warning(() -> "⚠️ Suppression impossible (déjà supprimée ?) : " + codeClasse);
            return false;
        }

        enregistrerSuppressionOutbox(codeClasse, institutionId, usedSource);

        final String src = usedSource;
        LOGGER.info(() -> "✅ Classe supprimée sur " + src
                + " + outbox : " + codeClasse);
        return true;
    }

    private boolean supprimerDansBase(Connection conn, String codeClasse,
                                       String institutionId, String source)
            throws SQLException {

        boolean autoCommitOriginal = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            // ① Tombstone EN PREMIER
            DeleteTracker.enregistrerSuppression(conn, TABLE, codeClasse, source);

            // ② DELETE réel
            String sql = "DELETE FROM " + TABLE
                    + " WHERE code_classe = ? AND institution_id = ?";
            int rows;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                stmt.setString(1, codeClasse);
                stmt.setString(2, institutionId);
                rows = stmt.executeUpdate();
            }

            conn.commit();
            return rows > 0;

        } catch (SQLException e) {
            try {
                conn.rollback();
            } catch (SQLException ignored) {
                // rollback best-effort
            }
            throw e;
        } finally {
            try {
                conn.setAutoCommit(autoCommitOriginal);
            } catch (SQLException ignored) {
                // restore best-effort
            }
        }
    }

    // =========================================================
    // UPDATE NOMBRE ÉTUDIANTS
    // =========================================================

    public boolean updateNombreEtudiants(String codeClasse, String institutionId,
                                          int nouveauNombre) throws SQLException {

        boolean writeOk = false;
        String usedSource = null;

        Connection conn = getPreferredWriteConnection();
        if (conn == null) return false;

        try {
            usedSource = getSource(conn);
            writeOk = updateNombreEtudiantsSansSync(
                    conn, codeClasse, institutionId, nouveauNombre);

            if (writeOk) {
                declencherSyncSiHorsTransaction(conn);
            }
        } catch (SQLException e) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec update nombre sur " + srcErr + " : " + codeClasse);
        } finally {
            closeIfNeeded(conn);
        }

        return writeOk;
    }

    private boolean updateNombreEtudiantsSansSync(Connection conn, String codeClasse,
                                                    String institutionId, int nouveauNombre)
            throws SQLException {
        String sql = "UPDATE " + TABLE
                + " SET nombre_etudiants = ? WHERE code_classe = ? AND institution_id = ?";
        Connection c = (conn != null) ? conn : getConnection();
        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            stmt.setInt(1, nouveauNombre);
            stmt.setString(2, codeClasse);
            stmt.setString(3, institutionId);
            return stmt.executeUpdate() > 0;
        }
    }

    // =========================================================
    // OUTBOX
    // =========================================================

    /**
     * Enregistre une opération (INSERT ou UPDATE) dans l'outbox LOCAL.
     * ✅ Cohérent avec sync_outbox corrigé (table_name, operation, primary_key,
     *    row_data, target_source, statut).
     * Ne bloque jamais l'appelant : échec silencieux loggué.
     */
    private void enregistrerDansOutbox(Classe classe, String operation) {
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
            // PK composite : institution_id|code_classe
            ps.setString(3, classe.getInstitutionId() + "|" + classe.getCodeClasse());
            ps.setString(4, toJson(classe));
            ps.executeUpdate();

            LOGGER.fine(() -> "📥 Outbox " + operation + " : " + classe.getCodeClasse());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Impossible d'enregistrer dans l'outbox : "
                            + classe.getCodeClasse());
        }
    }

    /**
     * Enregistre une suppression dans l'outbox (payload minimal : juste le code).
     */
    private void enregistrerSuppressionOutbox(String codeClasse, String institutionId,
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
            ps.setString(2, institutionId + "|" + codeClasse);
            ps.setString(3, "{\"codeClasse\":\"" + codeClasse
                    + "\",\"source\":\"" + source + "\"}");
            ps.executeUpdate();

            LOGGER.fine(() -> "📥 Outbox DELETE : " + codeClasse);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Impossible d'enregistrer suppression outbox : " + codeClasse);
        }
    }

    private String toJson(Classe c) {
        try {
            return GSON.toJson(c);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, e,
                    () -> "Impossible de sérialiser la classe en JSON");
            return "{}";
        }
    }

    // =========================================================
    // RECHERCHE FILTRÉE
    // =========================================================

    public List<Classe> obtenirClassesFiltrees(String institutionId, String motCle,
                                                String annee, String periode,
                                                String nomClasse) throws SQLException {
        List<Classe> classes = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM " + TABLE
                + " WHERE institution_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(institutionId);

        if (motCle != null && !motCle.isEmpty()) {
            sql.append(" AND (code_classe LIKE ? OR nom_classe LIKE ?)");
            params.add("%" + motCle + "%");
            params.add("%" + motCle + "%");
        }
        if (annee != null && !annee.isEmpty()) {
            sql.append(" AND annee_academique = ?");
            params.add(annee);
        }
        if (periode != null && !periode.isEmpty()) {
            sql.append(" AND periode = ?");
            params.add(periode);
        }
        if (nomClasse != null && !nomClasse.isEmpty()) {
            sql.append(" AND nom_classe = ?");
            params.add(nomClasse);
        }

        sql.append(" ORDER BY annee_academique DESC, nom_classe ASC");

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) classes.add(mapResultSetToClasse(rs));
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return classes;
    }

    // =========================================================
    // LECTURES
    // =========================================================

    public int getCapaciteClasse(String codeClasse, String institutionId) throws SQLException {
        String sql = "SELECT capacite_classe FROM " + TABLE
                + " WHERE code_classe = ? AND institution_id = ?";
        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                stmt.setString(1, codeClasse);
                stmt.setString(2, institutionId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) return rs.getInt("capacite_classe");
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return -1;
    }

    public int getNombreEtudiants(String codeClasse, String institutionId) throws SQLException {
        String sql = "SELECT nombre_etudiants FROM " + TABLE
                + " WHERE code_classe = ? AND institution_id = ?";
        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                stmt.setString(1, codeClasse);
                stmt.setString(2, institutionId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) return rs.getInt("nombre_etudiants");
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return -1;
    }

    public Classe trouverParCode(String codeClasse, String institutionId) throws SQLException {
        String sql = "SELECT * FROM " + TABLE
                + " WHERE code_classe = ? AND institution_id = ?";
        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                stmt.setString(1, codeClasse);
                stmt.setString(2, institutionId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) return mapResultSetToClasse(rs);
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return null;
    }

    public Classe trouverParNom(String institutionId, String nomClasse) throws SQLException {
        String sql = "SELECT * FROM " + TABLE
                + " WHERE institution_id = ? AND nom_classe = ? LIMIT 1";
        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                ps.setString(2, nomClasse);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return mapResultSetToClasse(rs);
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return null;
    }

    public List<Classe> toutLister(String institutionId) throws SQLException {
        List<Classe> liste = new ArrayList<>();
        String sql = "SELECT * FROM " + TABLE
                + " WHERE institution_id = ? ORDER BY nom_classe ASC";
        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                stmt.setString(1, institutionId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) liste.add(mapResultSetToClasse(rs));
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return liste;
    }

    public List<Classe> listByInstitution(String institutionId) throws SQLException {
        List<Classe> liste = new ArrayList<>();
        String sql = "SELECT * FROM " + TABLE
                + " WHERE institution_id = ? ORDER BY nom_classe ASC";

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                stmt.setString(1, institutionId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        liste.add(mapResultSetToClasse(rs));
                    }
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return liste;
    }

    public List<Classe> listByInstitutionAnneePeriode(String institutionId, String annee,
                                                       String periode) throws SQLException {
        List<Classe> list = new ArrayList<>();
        String sql = "SELECT * FROM " + TABLE + " WHERE institution_id = ? "
                + "AND annee_academique = ? AND periode = ? ORDER BY nom_classe";
        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                ps.setString(2, annee);
                ps.setString(3, periode);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) list.add(mapResultSetToClasse(rs));
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return list;
    }

    // =========================================================
    // SYNCHRONISATION
    // =========================================================

    /**
     * Déclenche la sync asynchrone UNIQUEMENT si la connexion est hors transaction
     * (autoCommit=true). Sinon, la sync est différée : elle sera déclenchée plus tard,
     * une fois la transaction commitée.
     */
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