import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class EtudiantData {

    private static final Logger LOGGER = Logger.getLogger(EtudiantData.class.getName());
    private static final String TABLE = MigrationManager.TABLE_ETUDIANTS;

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

    public EtudiantData() {
        this.connection = null;
    }

    public EtudiantData(Connection connection) {
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
     * ✅ Clé composite pour les tombstones et l'outbox.
     */
    private static String cleComposite(String numeroIdentifiant, String institutionId) {
        return numeroIdentifiant + "|" + institutionId;
    }

    // =========================================================
    // CREATE — LOCAL uniquement + outbox
    // =========================================================
    public boolean create(Etudiant etudiant) throws SQLException {
        if (etudiant == null) throw new IllegalArgumentException("Etudiant nul");

        Connection conn = getPreferredWriteConnection();
        if (conn == null) {
            LOGGER.severe(() -> "❌ Aucune connexion disponible pour créer étudiant "
                    + etudiant.getNumeroIdentifiantEtudiant());
            return false;
        }

        boolean ok = false;
        String usedSource = null;
        try {
            usedSource = getSource(conn);
            ok = createSansSync(conn, etudiant, usedSource);
            if (ok) declencherSyncSiHorsTransaction(conn);
        } catch (RuntimeException e) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec création étudiant sur " + srcErr
                            + " : " + etudiant.getNumeroIdentifiantEtudiant());
        } finally {
            closeIfNeeded(conn);
        }

        if (ok) {
            enregistrerDansOutbox(etudiant, "INSERT");

            final String src = usedSource;
            LOGGER.info(() -> "✅ Étudiant créé sur " + src
                    + " + outbox : " + etudiant.getNumeroIdentifiantEtudiant());
        }
        return ok;
    }

    private boolean createSansSync(Connection conn, Etudiant etudiant, String source) {
        String sql = """
            INSERT INTO etudiants (
                numero_identifiant, nom, prenom, sexe, date_naissance, groupe_sanguin,
                telephone, email, adresse, classe, annee_academique, promotion, matricule,
                ninu, institution_id, statut, date_inscription,
                departement_naissance, commune_naissance,
                nom_pere, prenom_pere, nom_mere, prenom_mere,
                telephone_parents, residence_parents, lien_parente, type_responsable,
                nom_responsable, prenom_responsable,
                photo_path, observations,
                empreinte_path, visage_path, biometrie_active,
                periode, `option`,
                departement_residence, commune_residence,
                source
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            fillPreparedStatement(ps, etudiant, 1, source);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur création étudiant "
                    + etudiant.getNumeroIdentifiantEtudiant(), e);
            return false;
        }
    }

    // =========================================================
    // READ — fusionnée LOCAL + REMOTE (ancienne signature)
    // =========================================================
    @Deprecated
    public Etudiant read(String numeroIdentifiant) {
        // 1) Chercher dans la base active
        Etudiant e = readFromConnection(connection, numeroIdentifiant);
        if (e != null) return e;

        // 2) Si les 2 bases sont saines, chercher dans l'autre
        DatabaseManager m = db();
        if (m.isLocalHealthy() && m.isRemoteHealthy()) {
            try (Connection otherConn = "LOCAL".equals(getSource(connection))
                    ? m.getRemoteConnection()
                    : m.getLocalConnection()) {
                return readFromConnection(otherConn, numeroIdentifiant);
            } catch (SQLException ex) {
                LOGGER.fine(() -> "Lecture fallback échouée : " + ex.getMessage());
            }
        }
        return null;
    }

    /**
     * ✅ Nouvelle signature : lit un étudiant d'une institution donnée.
     */
    public Etudiant read(String numeroIdentifiant, String institutionId) {
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
                    if (rs.next()) return mapEtudiant(rs);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lecture étudiant "
                    + numeroIdentifiant + " / " + institutionId, e);
        } finally {
            closeIfNeeded(conn);
        }
        return null;
    }

    private Etudiant readFromConnection(Connection conn, String numeroIdentifiant) {
        String sql = "SELECT * FROM " + TABLE + " WHERE numero_identifiant = ?";
        Connection c = (conn != null) ? conn : null;
        try {
            if (c == null) c = getConnection();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, numeroIdentifiant);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return mapEtudiant(rs);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lecture étudiant " + numeroIdentifiant, e);
        }
        return null;
    }

    public List<Etudiant> readAll(String institutionId) {
        java.util.Map<String, Etudiant> byId = new java.util.LinkedHashMap<>();

        try (Connection localConn = db().getLocalConnection()) {
            for (Etudiant e : readAllFromConnection(localConn, institutionId)) {
                if (e.getNumeroIdentifiantEtudiant() != null) {
                    byId.put(e.getNumeroIdentifiantEtudiant(), e);
                }
            }
        } catch (SQLException ex) {
            LOGGER.fine(() -> "LOCAL indisponible pour readAll : " + ex.getMessage());
        }

        try (Connection remoteConn = db().getRemoteConnection()) {
            for (Etudiant e : readAllFromConnection(remoteConn, institutionId)) {
                if (e.getNumeroIdentifiantEtudiant() == null) continue;
                Etudiant existing = byId.get(e.getNumeroIdentifiantEtudiant());
                if (existing == null || isMoreRecent(e, existing)) {
                    byId.put(e.getNumeroIdentifiantEtudiant(), e);
                }
            }
        } catch (SQLException ex) {
            LOGGER.fine(() -> "REMOTE indisponible pour readAll : " + ex.getMessage());
        }

        List<Etudiant> result = new ArrayList<>(byId.values());
        result.sort((a, b) -> {
            int cmp = safeCompare(a.getNom(), b.getNom());
            if (cmp != 0) return cmp;
            return safeCompare(a.getPrenom(), b.getPrenom());
        });
        return result;
    }

    private int safeCompare(String a, String b) {
        if (a == null && b == null) return 0;
        if (a == null) return 1;
        if (b == null) return -1;
        return a.compareToIgnoreCase(b);
    }

    private boolean isMoreRecent(Etudiant a, Etudiant b) {
        LocalDateTime ta = a.getLastModified();
        LocalDateTime tb = b.getLastModified();
        if (ta == null) return false;
        if (tb == null) return true;
        return ta.isAfter(tb);
    }

    private List<Etudiant> readAllFromConnection(Connection conn, String institutionId) {
        List<Etudiant> liste = new ArrayList<>();
        String sql = "SELECT * FROM " + TABLE
                + " WHERE institution_id = ? ORDER BY nom, prenom";
        if (conn == null) return liste;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setString(1, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) liste.add(mapEtudiant(rs));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur readAll pour " + institutionId, e);
        }
        return liste;
    }

    // =========================================================
    // READ WITH FILTERS
    // =========================================================
    public List<Etudiant> readWithFilters(String institutionId, String classe,
                                           String anneeAcademique, String periode) {
        List<Etudiant> liste = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM " + TABLE
                + " WHERE institution_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(institutionId);

        if (classe != null && !classe.isBlank()) {
            sql.append(" AND classe = ?"); params.add(classe);
        }
        if (anneeAcademique != null && !anneeAcademique.isBlank()) {
            sql.append(" AND annee_academique = ?"); params.add(anneeAcademique);
        }
        if (periode != null && !periode.isBlank()) {
            sql.append(" AND periode = ?"); params.add(periode);
        }
        sql.append(" ORDER BY nom, prenom");

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) liste.add(mapEtudiant(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur readWithFilters", e);
        } finally {
            closeIfNeeded(conn);
        }
        return liste;
    }

    // =========================================================
    // UPDATE — LOCAL uniquement + outbox
    // =========================================================
    public boolean update(Etudiant etudiant) throws SQLException {
        if (etudiant == null) throw new IllegalArgumentException("Etudiant nul");

        Connection conn = getPreferredWriteConnection();
        if (conn == null) return false;

        boolean updated = false;
        String usedSource = null;
        try {
            usedSource = getSource(conn);
            updated = updateSansSync(conn, etudiant, usedSource);
            if (updated) declencherSyncSiHorsTransaction(conn);
        } catch (RuntimeException e) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec update étudiant sur " + srcErr
                            + " : " + etudiant.getNumeroIdentifiantEtudiant());
        } finally {
            closeIfNeeded(conn);
        }

        if (updated) {
            enregistrerDansOutbox(etudiant, "UPDATE");

            final String src = usedSource;
            LOGGER.info(() -> "✅ Étudiant mis à jour sur " + src
                    + " + outbox : " + etudiant.getNumeroIdentifiantEtudiant());
        }
        return updated;
    }

    private boolean updateSansSync(Connection conn, Etudiant etudiant, String source) {
        String sql = """
            UPDATE etudiants SET
                nom=?, prenom=?, sexe=?, date_naissance=?, groupe_sanguin=?,
                telephone=?, email=?, adresse=?,
                classe=?, annee_academique=?, promotion=?, matricule=?,
                ninu=?, institution_id=?, statut=?, date_inscription=?,
                departement_naissance=?, commune_naissance=?,
                nom_pere=?, prenom_pere=?, nom_mere=?, prenom_mere=?,
                telephone_parents=?, residence_parents=?, lien_parente=?, type_responsable=?,
                nom_responsable=?, prenom_responsable=?,
                photo_path=?, observations=?,
                empreinte_path=?, visage_path=?, biometrie_active=?,
                periode=?, `option`=?,
                departement_residence=?, commune_residence=?,
                source=?
            WHERE numero_identifiant = ? AND institution_id = ?
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            int idx = fillPreparedStatementForUpdate(ps, etudiant, 1);
            ps.setString(idx++, source != null ? source : getSource(conn));
            ps.setString(idx++, etudiant.getNumeroIdentifiantEtudiant());
            ps.setString(idx, etudiant.getInstitutionId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur update étudiant "
                    + etudiant.getNumeroIdentifiantEtudiant(), e);
            return false;
        }
    }

    // =========================================================
    // DELETE — LOCAL uniquement + tombstone + outbox
    // =========================================================

    /**
     * ✅ Nouvelle signature : supprime un étudiant d'une institution donnée.
     */
    public boolean delete(String numeroIdentifiant, String institutionId) {
        if (numeroIdentifiant == null || numeroIdentifiant.isBlank()
                || institutionId == null || institutionId.isBlank()) {
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
                boolean ok = supprimerDansBase(conn, numeroIdentifiant, institutionId, usedSource);
                conn.commit();

                if (ok) {
                    enregistrerDeleteDansOutbox(numeroIdentifiant, institutionId, usedSource);
                    declencherSyncSiHorsTransaction(conn);
                    LOGGER.info(() -> "✅ Étudiant supprimé : " + numeroIdentifiant + " + outbox");
                }
                return ok;

            } catch (SQLException e) {
                try { conn.rollback(); } catch (SQLException ignored) {}
                LOGGER.log(Level.SEVERE, "Erreur delete " + numeroIdentifiant, e);
                return false;
            } finally {
                try { conn.setAutoCommit(autoCommitOriginal); } catch (SQLException ignored) {}
            }

        } catch (SQLException | RuntimeException e) {
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec delete étudiant : " + numeroIdentifiant);
            return false;
        } finally {
            closeIfNeeded(conn);
        }
    }

    /**
     * ⚠️ Ancienne signature (dépréciée). Charge l'étudiant pour récupérer son institution.
     * Peut être ambigu si deux institutions ont le même numeroIdentifiant.
     */
    @Deprecated
    public boolean delete(String numeroIdentifiant) {
        LOGGER.warning("⚠️ delete(numeroIdentifiant) déprécié — "
                + "utilisez delete(numeroIdentifiant, institutionId)");

        Etudiant e = read(numeroIdentifiant);
        if (e == null) return false;
        return delete(numeroIdentifiant, e.getInstitutionId());
    }

    private boolean supprimerDansBase(Connection conn, String numeroIdentifiant,
                                       String institutionId, String source)
            throws SQLException {
        // ✅ Tombstone AVEC clé composite
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

    // =========================================================
    // EXISTS
    // =========================================================
    public boolean exists(String numeroIdentifiant) {
        String sql = "SELECT COUNT(*) FROM " + TABLE + " WHERE numero_identifiant = ?";
        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, numeroIdentifiant);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur exists " + numeroIdentifiant, e);
        } finally {
            closeIfNeeded(conn);
        }
        return false;
    }

    public boolean existsByMatricule(String matricule) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + TABLE + " WHERE matricule = ?";
        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, matricule);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt(1) > 0;
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return false;
    }

    public boolean existsByNinu(String ninu, String institutionId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + TABLE + " WHERE ninu = ? AND institution_id = ?";
        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, ninu);
                ps.setString(2, institutionId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt(1) > 0;
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return false;
    }

    public boolean existsWithInstitution(String numeroIdentifiant, String institutionId) {
        String sql = "SELECT COUNT(*) FROM " + TABLE
                + " WHERE numero_identifiant = ? AND institution_id = ?";
        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, numeroIdentifiant);
                ps.setString(2, institutionId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur existsWithInstitution " + numeroIdentifiant, e);
        } finally {
            closeIfNeeded(conn);
        }
        return false;
    }

    // =========================================================
    // COUNT
    // =========================================================
    public int count(String institutionId) {
        String sql = "SELECT COUNT(*) FROM " + TABLE + " WHERE institution_id = ?";
        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur count " + institutionId, e);
        } finally {
            closeIfNeeded(conn);
        }
        return 0;
    }

    public int countByClasse(String institutionId, String classe) {
        String sql = "SELECT COUNT(*) FROM " + TABLE
                + " WHERE institution_id = ? AND classe = ?";
        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                ps.setString(2, classe);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur countByClasse", e);
        } finally {
            closeIfNeeded(conn);
        }
        return 0;
    }

    public int countByClasseAnneePeriode(String institutionId, String classe,
                                           String anneeAcademique, String periode)
            throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + TABLE + " WHERE institution_id = ? AND classe = ? "
                + "AND annee_academique = ? AND periode = ?";
        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                ps.setString(2, classe);
                ps.setString(3, anneeAcademique);
                ps.setString(4, periode);
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
    // SEARCH
    // =========================================================
    public List<Etudiant> search(String institutionId, String keyword) {
        List<Etudiant> liste = new ArrayList<>();
        String sql = "SELECT * FROM " + TABLE + " WHERE institution_id = ? "
                + "AND (nom LIKE ? OR prenom LIKE ? OR numero_identifiant LIKE ?) "
                + "ORDER BY nom, prenom";
        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                String pattern = "%" + keyword + "%";
                ps.setString(1, institutionId);
                ps.setString(2, pattern);
                ps.setString(3, pattern);
                ps.setString(4, pattern);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) liste.add(mapEtudiant(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur search", e);
        } finally {
            closeIfNeeded(conn);
        }
        return liste;
    }

    // =========================================================
    // OUTBOX
    // =========================================================
    private void enregistrerDansOutbox(Etudiant e, String operation) {
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
            ps.setString(3, cleComposite(e.getNumeroIdentifiantEtudiant(), e.getInstitutionId()));
            ps.setString(4, toJson(e));
            ps.executeUpdate();
            LOGGER.fine(() -> "📥 Outbox " + operation + " : "
                    + e.getNumeroIdentifiantEtudiant());
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, ex,
                    () -> "❌ Impossible d'enregistrer dans l'outbox : "
                            + e.getNumeroIdentifiantEtudiant());
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
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, ex,
                    () -> "❌ Impossible d'enregistrer le DELETE dans l'outbox");
        }
    }

    private String toJson(Etudiant e) {
        try {
            return GSON.toJson(e);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, ex,
                    () -> "Impossible de sérialiser l'étudiant en JSON");
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
    private Etudiant mapEtudiant(ResultSet rs) throws SQLException {
        Etudiant e = new Etudiant();
        e.setNumeroIdentifiantEtudiant(rs.getString("numero_identifiant"));
        e.setNom(rs.getString("nom"));
        e.setPrenom(rs.getString("prenom"));
        e.setSexe(rs.getString("sexe"));
        e.setDateNaissance(rs.getDate("date_naissance"));
        e.setGroupeSanguin(rs.getString("groupe_sanguin"));
        e.setTelephone(rs.getString("telephone"));
        e.setEmail(rs.getString("email"));
        e.setAdresse(rs.getString("adresse"));
        e.setClasse(rs.getString("classe"));
        e.setAnneeAcademique(rs.getString("annee_academique"));
        e.setPromotion(rs.getString("promotion"));
        e.setMatricule(rs.getString("matricule"));
        e.setNinu(rs.getString("ninu"));
        e.setInstitutionId(rs.getString("institution_id"));
        e.setStatut(rs.getString("statut"));
        e.setDateInscription(rs.getDate("date_inscription"));
        e.setDepartementNaissance(rs.getString("departement_naissance"));
        e.setCommuneNaissance(rs.getString("commune_naissance"));
        e.setNomPere(rs.getString("nom_pere"));
        e.setPrenomPere(rs.getString("prenom_pere"));
        e.setNomMere(rs.getString("nom_mere"));
        e.setPrenomMere(rs.getString("prenom_mere"));
        e.setTelephoneParents(rs.getString("telephone_parents"));
        e.setResidenceParents(rs.getString("residence_parents"));
        e.setLienParente(rs.getString("lien_parente"));
        e.setTypeResponsable(rs.getString("type_responsable"));
        e.setNomResponsable(rs.getString("nom_responsable"));
        e.setPrenomResponsable(rs.getString("prenom_responsable"));
        e.setPhotoPath(rs.getString("photo_path"));
        e.setObservations(rs.getString("observations"));
        e.setEmpreintePath(rs.getString("empreinte_path"));
        e.setVisagePath(rs.getString("visage_path"));
        e.setBiometrieActive(rs.getBoolean("biometrie_active"));
        e.setPeriode(rs.getString("periode"));
        e.setOption(rs.getString("option"));
        e.setDepartementResidence(rs.getString("departement_residence"));
        e.setCommuneResidence(rs.getString("commune_residence"));
        try { e.setSource(rs.getString("source")); } catch (SQLException ignored) {}

        try {
            Timestamp ts = rs.getTimestamp("last_modified");
            if (ts != null) e.setLastModified(ts.toLocalDateTime());
        } catch (SQLException ignored) {}

        return e;
    }

    // =========================================================
    // REMPLISSAGE INSERT
    // =========================================================
    private void fillPreparedStatement(PreparedStatement ps, Etudiant e,
                                         int startIndex, String source) throws SQLException {
        ps.setString(startIndex++, e.getNumeroIdentifiantEtudiant());
        ps.setString(startIndex++, e.getNom());
        ps.setString(startIndex++, e.getPrenom());
        ps.setString(startIndex++, e.getSexe());
        ps.setDate(startIndex++, e.getDateNaissance() != null
                ? new Date(e.getDateNaissance().getTime()) : null);
        ps.setString(startIndex++, e.getGroupeSanguin());
        ps.setString(startIndex++, e.getTelephone());
        ps.setString(startIndex++, e.getEmail());
        ps.setString(startIndex++, e.getAdresse());
        ps.setString(startIndex++, e.getClasse());
        ps.setString(startIndex++, e.getAnneeAcademique());
        ps.setString(startIndex++, e.getPromotion());
        ps.setString(startIndex++, e.getMatricule());
        ps.setString(startIndex++, e.getNinu());
        ps.setString(startIndex++, e.getInstitutionId());
        ps.setString(startIndex++, e.getStatut());
        ps.setDate(startIndex++, e.getDateInscription() != null
                ? new Date(e.getDateInscription().getTime()) : null);
        ps.setString(startIndex++, e.getDepartementNaissance());
        ps.setString(startIndex++, e.getCommuneNaissance());
        ps.setString(startIndex++, e.getNomPere());
        ps.setString(startIndex++, e.getPrenomPere());
        ps.setString(startIndex++, e.getNomMere());
        ps.setString(startIndex++, e.getPrenomMere());
        ps.setString(startIndex++, e.getTelephoneParents());
        ps.setString(startIndex++, e.getResidenceParents());
        ps.setString(startIndex++, e.getLienParente());
        ps.setString(startIndex++, e.getTypeResponsable());
        ps.setString(startIndex++, e.getNomResponsable());
        ps.setString(startIndex++, e.getPrenomResponsable());
        ps.setString(startIndex++, e.getPhotoPath());
        ps.setString(startIndex++, e.getObservations());
        ps.setString(startIndex++, e.getEmpreintePath());
        ps.setString(startIndex++, e.getVisagePath());
        ps.setBoolean(startIndex++, e.isBiometrieActive());
        ps.setString(startIndex++, e.getPeriode());
        ps.setString(startIndex++, e.getOption());
        ps.setString(startIndex++, e.getDepartementResidence());
        ps.setString(startIndex++, e.getCommuneResidence());
        // ✅ Utilise la source passée en paramètre (fallback LOCAL)
        ps.setString(startIndex, source != null ? source : "LOCAL");
    }

    // =========================================================
    // REMPLISSAGE UPDATE
    // =========================================================
    private int fillPreparedStatementForUpdate(PreparedStatement ps, Etudiant e,
                                                 int startIndex) throws SQLException {
        ps.setString(startIndex++, e.getNom());
        ps.setString(startIndex++, e.getPrenom());
        ps.setString(startIndex++, e.getSexe());
        ps.setDate(startIndex++, e.getDateNaissance() != null
                ? new Date(e.getDateNaissance().getTime()) : null);
        ps.setString(startIndex++, e.getGroupeSanguin());
        ps.setString(startIndex++, e.getTelephone());
        ps.setString(startIndex++, e.getEmail());
        ps.setString(startIndex++, e.getAdresse());
        ps.setString(startIndex++, e.getClasse());
        ps.setString(startIndex++, e.getAnneeAcademique());
        ps.setString(startIndex++, e.getPromotion());
        ps.setString(startIndex++, e.getMatricule());
        ps.setString(startIndex++, e.getNinu());
        ps.setString(startIndex++, e.getInstitutionId());
        ps.setString(startIndex++, e.getStatut());
        ps.setDate(startIndex++, e.getDateInscription() != null
                ? new Date(e.getDateInscription().getTime()) : null);
        ps.setString(startIndex++, e.getDepartementNaissance());
        ps.setString(startIndex++, e.getCommuneNaissance());
        ps.setString(startIndex++, e.getNomPere());
        ps.setString(startIndex++, e.getPrenomPere());
        ps.setString(startIndex++, e.getNomMere());
        ps.setString(startIndex++, e.getPrenomMere());
        ps.setString(startIndex++, e.getTelephoneParents());
        ps.setString(startIndex++, e.getResidenceParents());
        ps.setString(startIndex++, e.getLienParente());
        ps.setString(startIndex++, e.getTypeResponsable());
        ps.setString(startIndex++, e.getNomResponsable());
        ps.setString(startIndex++, e.getPrenomResponsable());
        ps.setString(startIndex++, e.getPhotoPath());
        ps.setString(startIndex++, e.getObservations());
        ps.setString(startIndex++, e.getEmpreintePath());
        ps.setString(startIndex++, e.getVisagePath());
        ps.setBoolean(startIndex++, e.isBiometrieActive());
        ps.setString(startIndex++, e.getPeriode());
        ps.setString(startIndex++, e.getOption());
        ps.setString(startIndex++, e.getDepartementResidence());
        ps.setString(startIndex++, e.getCommuneResidence());
        return startIndex;
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