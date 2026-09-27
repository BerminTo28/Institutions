import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AbsenceData {

    private static final Logger LOGGER = Logger.getLogger(AbsenceData.class.getName());
    private static final String TABLE_ABSENCES = MigrationManager.TABLE_ABSENCES;

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

    /** Connexion partagée (null = mode autonome). */
    private final Connection connection;

    public AbsenceData() {
        this.connection = null;
    }

    public AbsenceData(Connection connection) {
        this.connection = connection;
    }

    // ============================================================
    // GESTION DE LA CONNEXION
    // ============================================================

    private Connection getConnection() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            return connection;
        }
        return DatabaseManager.getInstance().getConnection();
    }

    private boolean shouldClose(Connection conn) {
        return conn != connection;
    }

    private void closeIfNeeded(Connection conn) {
        if (shouldClose(conn) && conn != null) {
            try {
                if (!conn.isClosed()) conn.close();
            } catch (SQLException ignored) {}
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
            String localUrl  = DatabaseManager.getInstance().getDataSource().getSecondaryUrl();
            String remoteUrl = DatabaseManager.getInstance().getDataSource().getPrimaryUrl();

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
        DatabaseManager m = DatabaseManager.getInstance();

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
    // INSERT
    // =========================================================
    public int insert(Absence absence) {
        if (absence == null) throw new IllegalArgumentException("Absence nulle");

        Connection conn = getPreferredWriteConnection();
        if (conn == null) {
            LOGGER.severe("❌ Aucune connexion disponible pour insert absence");
            return -1;
        }

        int id = -1;
        String usedSource = null;
        try {
            usedSource = getSource(conn);
            id = insertSansSync(conn, absence, usedSource);
            if (id > 0) {
                declencherSyncSiHorsTransaction(conn);

                // ✅ Outbox
                enregistrerDansOutbox(absence, "INSERT");
            }
        } catch (SQLException | RuntimeException e) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec insert absence sur " + srcErr);
        } finally {
            closeIfNeeded(conn);
        }

        if (id > 0) {
            final int generatedId = id;
            LOGGER.info(() -> "✅ Absence créée : " + generatedId + " + outbox");
        }
        return id;
    }

    private int insertSansSync(Connection conn, Absence absence, String source)
            throws SQLException {
        String sql = "INSERT INTO " + TABLE_ABSENCES +
                " (institution_id, date_absence, justifiee, motif, code_cours, periode, promotion, " +
                " annee_academique, classe, personne_type, numero_identifiant, " +
                " created_by, created_at, updated_at, source) " +
                " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);

            long now = System.currentTimeMillis();

            ps.setString(1, absence.getInstitutionId());
            ps.setTimestamp(2, absence.getDateAbsence() != null
                    ? new java.sql.Timestamp(absence.getDateAbsence().getTime())
                    : new java.sql.Timestamp(now));
            ps.setBoolean(3, absence.isJustifiee());
            ps.setString(4, absence.getMotif());
            ps.setString(5, absence.getCodeCours());
            ps.setString(6, absence.getPeriode());
            ps.setString(7, absence.getPromotion());
            ps.setString(8, absence.getAnneeAcademique());
            ps.setString(9, absence.getClasse());
            ps.setString(10, absence.getPersonneType());
            ps.setString(11, absence.getNumeroIdentifiant());
            ps.setString(12, absence.getCreatedBy());
            ps.setTimestamp(13, new java.sql.Timestamp(now));
            ps.setTimestamp(14, new java.sql.Timestamp(now));
            ps.setString(15, source != null ? source : getSource(conn));

            int affected = ps.executeUpdate();
            if (affected == 0) {
                LOGGER.warning("⚠️ Insertion absence : aucune ligne affectée");
                return -1;
            }

            try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                }
            }
            return -1;
        }
    }

    // =========================================================
    // RECHERCHE AVEC FILTRES
    // =========================================================
    public List<Absence> rechercherAbsences(String type, String annee, String periode,
                                              String classe, String matiere,
                                              String numeroIdentifiant) throws SQLException {
        List<Absence> absences = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM " + TABLE_ABSENCES + " WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (type != null && !type.isBlank()) {
            sql.append(" AND personne_type = ?"); params.add(type);
        }
        if (annee != null && !annee.isBlank()) {
            sql.append(" AND annee_academique = ?"); params.add(annee);
        }
        if (periode != null && !periode.isBlank()) {
            sql.append(" AND periode = ?"); params.add(periode);
        }
        if (classe != null && !classe.isBlank()) {
            sql.append(" AND classe = ?"); params.add(classe);
        }
        if (matiere != null && !matiere.isBlank()) {
            sql.append(" AND code_cours = ?"); params.add(matiere);
        }
        if (numeroIdentifiant != null && !numeroIdentifiant.isBlank()) {
            sql.append(" AND numero_identifiant = ?"); params.add(numeroIdentifiant);
        }

        sql.append(" ORDER BY date_absence DESC");

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        absences.add(mapResultSetToAbsence(rs));
                    }
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return absences;
    }

    // =========================================================
    // LISTES DISTINCTES
    // =========================================================
    public List<String> listerAnneesAcademiques() throws SQLException {
        List<String> list = new ArrayList<>();
        String sql = "SELECT DISTINCT annee_academique FROM " + TABLE_ABSENCES
                + " WHERE annee_academique IS NOT NULL ORDER BY annee_academique DESC";
        Connection conn = null;
        try {
            conn = getConnection();
            try (Statement stmt = conn.createStatement()) {
                stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                try (ResultSet rs = stmt.executeQuery(sql)) {
                    while (rs.next()) list.add(rs.getString(1));
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return list;
    }

    public List<String> listerPeriodes(String anneeAcademique) throws SQLException {
        List<String> list = new ArrayList<>();
        String sql = "SELECT DISTINCT periode FROM " + TABLE_ABSENCES + " WHERE periode IS NOT NULL";
        if (anneeAcademique != null && !anneeAcademique.isBlank()) sql += " AND annee_academique = ?";
        sql += " ORDER BY periode ASC";

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                if (anneeAcademique != null && !anneeAcademique.isBlank()) ps.setString(1, anneeAcademique);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) list.add(rs.getString(1));
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return list;
    }

    public List<String> listerClasses(String anneeAcademique) throws SQLException {
        List<String> list = new ArrayList<>();
        String sql = "SELECT DISTINCT classe FROM " + TABLE_ABSENCES + " WHERE classe IS NOT NULL";
        if (anneeAcademique != null && !anneeAcademique.isBlank()) sql += " AND annee_academique = ?";
        sql += " ORDER BY classe ASC";

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                if (anneeAcademique != null && !anneeAcademique.isBlank()) ps.setString(1, anneeAcademique);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) list.add(rs.getString(1));
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return list;
    }

    // =========================================================
    // LISTES POUR FORMULAIRES
    // =========================================================
    public List<Map<String, Object>> listerEtudiantsFiltres(String anneeAcademique,
                                                              String periode,
                                                              String classe) throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT DISTINCT numero_identifiant, 'Étudiant' as nom FROM " + TABLE_ABSENCES
                + " WHERE personne_type = 'ETUDIANT'";

        Connection conn = null;
        try {
            conn = getConnection();
            try (Statement stmt = conn.createStatement()) {
                stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                try (ResultSet rs = stmt.executeQuery(sql)) {
                    while (rs.next()) {
                        Map<String, Object> map = new HashMap<>();
                        map.put("id", rs.getString("numero_identifiant"));
                        map.put("nom", rs.getString("nom"));
                        list.add(map);
                    }
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return list;
    }

    public List<Map<String, Object>> listerProfesseursFiltres(String anneeAcademique,
                                                                String periode,
                                                                String classe) throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT DISTINCT numero_identifiant, 'Professeur' as nom FROM " + TABLE_ABSENCES
                + " WHERE personne_type = 'PROFESSEUR'";

        Connection conn = null;
        try {
            conn = getConnection();
            try (Statement stmt = conn.createStatement()) {
                stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                try (ResultSet rs = stmt.executeQuery(sql)) {
                    while (rs.next()) {
                        Map<String, Object> map = new HashMap<>();
                        map.put("id", rs.getString("numero_identifiant"));
                        map.put("nom", rs.getString("nom"));
                        list.add(map);
                    }
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return list;
    }

    // =========================================================
    // UPDATE
    // =========================================================
    public boolean update(Absence absence) {
        if (absence == null) throw new IllegalArgumentException("Absence nulle");

        Connection conn = getPreferredWriteConnection();
        if (conn == null) return false;

        boolean updated = false;
        String usedSource = null;
        try {
            usedSource = getSource(conn);
            updated = updateSansSync(conn, absence, usedSource);
            if (updated) {
                declencherSyncSiHorsTransaction(conn);
                enregistrerDansOutbox(absence, "UPDATE");
            }
        } catch (SQLException | RuntimeException e) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec update absence sur " + srcErr);
        } finally {
            closeIfNeeded(conn);
        }

        return updated;
    }

    private boolean updateSansSync(Connection conn, Absence absence, String source)
            throws SQLException {
        String sql = "UPDATE " + TABLE_ABSENCES +
                " SET date_absence = ?, justifiee = ?, motif = ?, code_cours = ?, periode = ?, " +
                " promotion = ?, annee_academique = ?, classe = ?, personne_type = ?, " +
                " numero_identifiant = ?, updated_at = ?, source = ? " +
                " WHERE id = ? AND institution_id = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setTimestamp(1, absence.getDateAbsence() != null
                    ? new java.sql.Timestamp(absence.getDateAbsence().getTime())
                    : new java.sql.Timestamp(System.currentTimeMillis()));
            ps.setBoolean(2, absence.isJustifiee());
            ps.setString(3, absence.getMotif());
            ps.setString(4, absence.getCodeCours());
            ps.setString(5, absence.getPeriode());
            ps.setString(6, absence.getPromotion());
            ps.setString(7, absence.getAnneeAcademique());
            ps.setString(8, absence.getClasse());
            ps.setString(9, absence.getPersonneType());
            ps.setString(10, absence.getNumeroIdentifiant());
            ps.setTimestamp(11, new java.sql.Timestamp(System.currentTimeMillis()));
            ps.setString(12, source != null ? source : getSource(conn));
            ps.setInt(13, absence.getId());
            ps.setString(14, absence.getInstitutionId());

            return ps.executeUpdate() > 0;
        }
    }

    // =========================================================
    // DELETE
    // =========================================================
    public boolean delete(int id, String institutionId) {
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
                String cle = institutionId + "|" + id;
                DeleteTracker.enregistrerSuppression(conn, TABLE_ABSENCES, cle, usedSource);

                // ② DELETE réel
                String sql = "DELETE FROM " + TABLE_ABSENCES
                        + " WHERE id = ? AND institution_id = ?";
                int rows;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                    ps.setInt(1, id);
                    ps.setString(2, institutionId);
                    rows = ps.executeUpdate();
                }

                conn.commit();

                if (rows > 0) {
                    enregistrerDeleteDansOutbox(institutionId, id, usedSource);
                    declencherSyncSiHorsTransaction(conn);
                    LOGGER.info(() -> "✅ Absence supprimée : " + id + " + outbox");
                }
                return rows > 0;

            } catch (SQLException e) {
                try { conn.rollback(); } catch (SQLException ignored) {}
                LOGGER.log(Level.SEVERE, "❌ Erreur delete absence", e);
                return false;
            } finally {
                try { conn.setAutoCommit(autoCommitOriginal); } catch (SQLException ignored) {}
            }

        } catch (SQLException | RuntimeException e) {
            LOGGER.log(Level.SEVERE, e, () -> "❌ Échec delete absence : " + id);
            return false;
        } finally {
            closeIfNeeded(conn);
        }
    }

    // =========================================================
    // READ
    // =========================================================
    public Absence findById(int id) throws SQLException {
        String sql = "SELECT * FROM " + TABLE_ABSENCES + " WHERE id = ?";
        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return mapResultSetToAbsence(rs);
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return null;
    }

    public List<Absence> findByPersonne(String institutionId, String numeroIdentifiant,
                                          String personneType) throws SQLException {
        List<Absence> absences = new ArrayList<>();
        String sql = "SELECT * FROM " + TABLE_ABSENCES +
                " WHERE institution_id = ? AND numero_identifiant = ? AND personne_type = ?" +
                " ORDER BY date_absence DESC";
        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                ps.setString(2, numeroIdentifiant);
                ps.setString(3, personneType);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) absences.add(mapResultSetToAbsence(rs));
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return absences;
    }

    public List<Absence> findByClasse(String institutionId, String classe,
                                        String anneeAcademique) throws SQLException {
        List<Absence> absences = new ArrayList<>();
        String sql = "SELECT * FROM " + TABLE_ABSENCES +
                " WHERE institution_id = ? AND classe = ? AND annee_academique = ?" +
                " ORDER BY date_absence DESC";
        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                ps.setString(2, classe);
                ps.setString(3, anneeAcademique);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) absences.add(mapResultSetToAbsence(rs));
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return absences;
    }

    public List<Absence> findWithFilters(String institutionId, String personneType,
                                           String classe, String anneeAcademique,
                                           String periode, String codeCours) throws SQLException {
        List<Absence> absences = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM " + TABLE_ABSENCES + " WHERE institution_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(institutionId);

        if (personneType != null && !personneType.isEmpty()) {
            sql.append(" AND personne_type = ?"); params.add(personneType);
        }
        if (classe != null && !classe.isEmpty()) {
            sql.append(" AND classe = ?"); params.add(classe);
        }
        if (anneeAcademique != null && !anneeAcademique.isEmpty()) {
            sql.append(" AND annee_academique = ?"); params.add(anneeAcademique);
        }
        if (periode != null && !periode.isEmpty()) {
            sql.append(" AND periode = ?"); params.add(periode);
        }
        if (codeCours != null && !codeCours.isEmpty()) {
            sql.append(" AND code_cours = ?"); params.add(codeCours);
        }

        sql.append(" ORDER BY date_absence DESC");

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) absences.add(mapResultSetToAbsence(rs));
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return absences;
    }

    // =========================================================
    // MAPPING
    // =========================================================
    private Absence mapResultSetToAbsence(ResultSet rs) throws SQLException {
        Absence a = new Absence();
        a.setId(rs.getInt("id"));
        a.setInstitutionId(rs.getString("institution_id"));
        a.setDateAbsence(rs.getTimestamp("date_absence"));
        a.setJustifiee(rs.getBoolean("justifiee"));
        a.setMotif(rs.getString("motif"));
        a.setCodeCours(rs.getString("code_cours"));
        a.setPeriode(rs.getString("periode"));
        a.setPromotion(rs.getString("promotion"));
        a.setAnneeAcademique(rs.getString("annee_academique"));
        a.setClasse(rs.getString("classe"));
        a.setPersonneType(rs.getString("personne_type"));
        a.setNumeroIdentifiant(rs.getString("numero_identifiant"));
        a.setCreatedBy(rs.getString("created_by"));
        a.setCreatedAt(rs.getTimestamp("created_at"));
        a.setUpdatedAt(rs.getTimestamp("updated_at"));
        try { a.setSource(rs.getString("source")); } catch (SQLException ignored) {}
        return a;
    }

    // =========================================================
    // OUTBOX
    // =========================================================
    private void enregistrerDansOutbox(Absence absence, String operation) {
        String sql = """
            INSERT INTO sync_outbox
              (table_name, operation, primary_key, row_data, target_source, statut)
            VALUES (?, ?, ?, ?, 'REMOTE', 'PENDING')
        """;
        try (Connection conn = DatabaseManager.getInstance().getLocalConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(OUTBOX_TIMEOUT_SECONDS);
            ps.setString(1, TABLE_ABSENCES);
            ps.setString(2, operation);
            ps.setString(3, absence.getInstitutionId() + "|" + absence.getId());
            ps.setString(4, toJson(absence));
            ps.executeUpdate();
            LOGGER.fine(() -> "📥 Outbox " + operation + " : " + absence.getId());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Impossible d'enregistrer dans l'outbox : " + absence.getId());
        }
    }

    private void enregistrerDeleteDansOutbox(String institutionId, int id, String source) {
        String sql = """
            INSERT INTO sync_outbox
              (table_name, operation, primary_key, row_data, target_source, statut)
            VALUES (?, 'DELETE', ?, ?, 'REMOTE', 'PENDING')
        """;
        try (Connection conn = DatabaseManager.getInstance().getLocalConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(OUTBOX_TIMEOUT_SECONDS);
            ps.setString(1, TABLE_ABSENCES);
            ps.setString(2, institutionId + "|" + id);
            ps.setString(3, "{\"id\":" + id + ",\"institutionId\":\""
                    + escapeJson(institutionId) + "\",\"source\":\""
                    + escapeJson(source) + "\"}");
            ps.executeUpdate();
            LOGGER.fine(() -> "📥 Outbox DELETE : " + id);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Impossible d'enregistrer le DELETE dans l'outbox");
        }
    }

    private String toJson(Absence a) {
        try {
            return GSON.toJson(a);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, e, () -> "Impossible de sérialiser l'absence en JSON");
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
                DatabaseManager.getInstance().declencherSyncImmediateAsync();
                LOGGER.fine("⚡ Sync asynchrone déclenchée (hors transaction).");
            } else {
                LOGGER.fine("ℹ️ Sync différée : transaction en cours (autoCommit=false).");
            }
        } catch (SQLException e) {
            LOGGER.log(Level.FINE, "Impossible de déclencher la sync", e);
        }
    }
}