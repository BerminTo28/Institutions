import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MatiereData {

    private static final Logger LOGGER = Logger.getLogger(MatiereData.class.getName());
    private static final String TABLE = MigrationManager.TABLE_MATIERES;

    /** Timeout d'une requête SQL en secondes. */
    private static final int QUERY_TIMEOUT_SECONDS = 15;

    /** Timeout pour l'écriture outbox. */
    private static final int OUTBOX_TIMEOUT_SECONDS = 10;

    private final String institution_id;
    private final Connection connection;

    private static final com.google.gson.Gson GSON = new com.google.gson.GsonBuilder()
            .registerTypeAdapter(java.time.LocalDateTime.class,
                    (com.google.gson.JsonSerializer<java.time.LocalDateTime>)
                            (src, type, ctx) -> new com.google.gson.JsonPrimitive(src.toString()))
            .registerTypeAdapter(java.time.LocalDate.class,
                    (com.google.gson.JsonSerializer<java.time.LocalDate>)
                            (src, type, ctx) -> new com.google.gson.JsonPrimitive(src.toString()))
            .create();

    public MatiereData(String institution_id) {
        this.institution_id = institution_id;
        this.connection = null;
    }

    public MatiereData(String institution_id, Connection connection) {
        this.institution_id = institution_id;
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
     * Détermine la source (LOCAL/REMOTE) par comparaison d'URL JDBC.
     * Cohérent avec ClasseData corrigée.
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

    /**
     * Retourne une connexion d'écriture : LOCAL en priorité, REMOTE en secours.
     * Cohérent avec ClasseData.getPreferredWriteConnection().
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
    // CREATE
    // =========================================================
    public boolean create(Matiere matiere) throws SQLException {
        if (matiere == null) throw new IllegalArgumentException("Matiere nulle");

        Connection conn = getPreferredWriteConnection();
        if (conn == null) {
            LOGGER.severe(() -> "❌ Aucune connexion disponible pour créer "
                    + matiere.getCodeCours());
            return false;
        }

        boolean created = false;
        String usedSource = null;
        try {
            usedSource = getSource(conn);
            created = createSansSync(conn, matiere, usedSource);

            if (created) {
                declencherSyncSiHorsTransaction(conn);
            }
        } catch (RuntimeException e) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec création matière sur " + srcErr + " : "
                            + matiere.getCodeCours());
        } finally {
            closeIfNeeded(conn);
        }

        if (!created) return false;

        // Filet de sécurité : enqueue dans l'outbox
        enregistrerDansOutbox(matiere, "INSERT");

        final String src = usedSource;
        LOGGER.info(() -> "✅ Matière créée sur " + src
                + " + outbox : " + matiere.getCodeCours());
        return true;
    }

    private boolean createSansSync(Connection conn, Matiere matiere, String source) {
        String sql = "INSERT INTO " + TABLE + " " +
                "(institution_id, nom_matiere, coefficient, note_passage, note_minimale, " +
                "classe_matiere, periode, statut, code_cours, annee_academique, " +
                "note_maximale, nombre_credits, duree_cours, source) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);

            ps.setString(1, this.institution_id);
            ps.setString(2, matiere.getNomMatiere());
            ps.setDouble(3, matiere.getCoefficient());
            ps.setDouble(4, matiere.getNotePassage());
            ps.setDouble(5, matiere.getNoteMinimale());
            ps.setString(6, matiere.getClasseMatiere());
            ps.setString(7, matiere.getPeriode());
            ps.setString(8, matiere.getStatut());
            ps.setString(9, matiere.getCodeCours());
            ps.setString(10, matiere.getAnneeAcademique());
            ps.setDouble(11, matiere.getNoteMaximale());
            ps.setInt(12, matiere.getNombreCredits());
            ps.setString(13, matiere.getDureeCours());
            ps.setString(14, source != null ? source : getSource(conn));

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE,
                    "Erreur création matière " + matiere.getCodeCours(), e);
            return false;
        }
    }

    // =========================================================
    // UPDATE
    // =========================================================
    public boolean update(Matiere matiere) throws SQLException {
        if (matiere == null) throw new IllegalArgumentException("Matiere nulle");

        Connection conn = getPreferredWriteConnection();
        if (conn == null) {
            LOGGER.severe(() -> "❌ Aucune connexion disponible pour update "
                    + matiere.getCodeCours());
            return false;
        }

        boolean updated = false;
        String usedSource = null;
        try {
            usedSource = getSource(conn);
            updated = updateSansSync(conn, matiere, usedSource);

            if (updated) {
                declencherSyncSiHorsTransaction(conn);
            }
        } catch (RuntimeException e) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec update matière sur " + srcErr + " : "
                            + matiere.getCodeCours());
        } finally {
            closeIfNeeded(conn);
        }

        if (!updated) return false;

        enregistrerDansOutbox(matiere, "UPDATE");

        final String src = usedSource;
        LOGGER.info(() -> "✅ Matière mise à jour sur " + src
                + " + outbox : " + matiere.getCodeCours());
        return true;
    }

    private boolean updateSansSync(Connection conn, Matiere matiere, String source) {
        String sql = "UPDATE " + TABLE + " SET " +
                "nom_matiere = ?, coefficient = ?, note_passage = ?, note_minimale = ?, " +
                "classe_matiere = ?, periode = ?, statut = ?, annee_academique = ?, " +
                "note_maximale = ?, nombre_credits = ?, duree_cours = ?, source = ? " +
                "WHERE code_cours = ? AND institution_id = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);

            ps.setString(1, matiere.getNomMatiere());
            ps.setDouble(2, matiere.getCoefficient());
            ps.setDouble(3, matiere.getNotePassage());
            ps.setDouble(4, matiere.getNoteMinimale());
            ps.setString(5, matiere.getClasseMatiere());
            ps.setString(6, matiere.getPeriode());
            ps.setString(7, matiere.getStatut());
            ps.setString(8, matiere.getAnneeAcademique());
            ps.setDouble(9, matiere.getNoteMaximale());
            ps.setInt(10, matiere.getNombreCredits());
            ps.setString(11, matiere.getDureeCours());
            ps.setString(12, source != null ? source : getSource(conn));
            ps.setString(13, matiere.getCodeCours());
            ps.setString(14, this.institution_id);

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE,
                    "Erreur update matière " + matiere.getCodeCours(), e);
            return false;
        }
    }

    // =========================================================
    // DELETE
    // =========================================================
    public boolean delete(String codeCours) {
        if (codeCours == null || codeCours.isBlank()) {
            LOGGER.warning("delete() : codeCours null ou vide");
            return false;
        }
        if (this.institution_id == null || this.institution_id.isBlank()) {
            LOGGER.warning("delete() : institution_id null ou vide");
            return false;
        }

        Connection conn = getPreferredWriteConnection();
        if (conn == null) {
            LOGGER.severe(() -> "❌ Aucune connexion disponible pour delete " + codeCours);
            return false;
        }

        boolean ok = false;
        String usedSource = null;
        try {
            usedSource = getSource(conn);
            ok = supprimerDansBase(conn, codeCours, usedSource);

            if (ok) {
                declencherSyncSiHorsTransaction(conn);
            }
        } catch (SQLException | RuntimeException e) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec delete matière sur " + srcErr + " : " + codeCours);
        } finally {
            closeIfNeeded(conn);
        }

        if (!ok) {
            LOGGER.warning(() -> "⚠️ Suppression impossible (déjà supprimée ?) : " + codeCours);
            return false;
        }

        enregistrerDeleteDansOutbox(codeCours, usedSource);

        final String src = usedSource;
        LOGGER.info(() -> "✅ Matière supprimée sur " + src
                + " + outbox : " + codeCours);
        return true;
    }

    /**
     * ✅ PK de `matieres` = (institution_id, code_cours).
     *    Pas de colonne `id`.
     */
    private boolean supprimerDansBase(Connection conn, String codeCours, String source)
            throws SQLException {

        boolean autoCommitOriginal = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            // ① Vérifier existence
            String selectSql = "SELECT 1 FROM " + TABLE
                    + " WHERE code_cours = ? AND institution_id = ?";
            boolean existe;
            try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, codeCours);
                ps.setString(2, this.institution_id);
                try (ResultSet rs = ps.executeQuery()) {
                    existe = rs.next();
                }
            }
            if (!existe) {
                conn.rollback();
                LOGGER.warning(() -> "⚠️ Matière introuvable : " + codeCours);
                return false;
            }

            // ② Tombstone — clé = "institution_id|code_cours"
            String pkValue = this.institution_id + "|" + codeCours;
            DeleteTracker.enregistrerSuppression(conn, TABLE, pkValue, source);

            // ③ DELETE réel
            String sql = "DELETE FROM " + TABLE
                    + " WHERE code_cours = ? AND institution_id = ?";
            int rows;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, codeCours);
                ps.setString(2, this.institution_id);
                rows = ps.executeUpdate();
            }

            conn.commit();
            return rows > 0;

        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ignored) {}
            throw e;
        } finally {
            try { conn.setAutoCommit(autoCommitOriginal); } catch (SQLException ignored) {}
        }
    }

    // =========================================================
    // OUTBOX
    // =========================================================

    /**
     * ✅ Cohérent avec sync_outbox corrigé :
     *    (table_name, operation, primary_key, row_data, target_source, statut)
     */
    private void enregistrerDansOutbox(Matiere matiere, String operation) {
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
            ps.setString(3, this.institution_id + "|" + matiere.getCodeCours());
            ps.setString(4, toJson(matiere));
            ps.executeUpdate();

            LOGGER.fine(() -> "📥 Outbox " + operation + " : " + matiere.getCodeCours());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Impossible d'enregistrer dans l'outbox : "
                            + matiere.getCodeCours());
        }
    }

    private void enregistrerDeleteDansOutbox(String codeCours, String source) {
        String sql = """
            INSERT INTO sync_outbox
              (table_name, operation, primary_key, row_data, target_source, statut)
            VALUES (?, 'DELETE', ?, ?, 'REMOTE', 'PENDING')
        """;
        try (Connection conn = db().getLocalConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setQueryTimeout(OUTBOX_TIMEOUT_SECONDS);
            ps.setString(1, TABLE);
            ps.setString(2, this.institution_id + "|" + codeCours);
            ps.setString(3, "{\"codeCours\":\"" + escapeJson(codeCours)
                    + "\",\"source\":\"" + escapeJson(source) + "\"}");
            ps.executeUpdate();

            LOGGER.fine(() -> "📥 Outbox DELETE : " + codeCours);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Impossible d'enregistrer le DELETE dans l'outbox : " + codeCours);
        }
    }

    private String toJson(Matiere m) {
        try {
            return GSON.toJson(m);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, e,
                    () -> "Impossible de sérialiser la matière en JSON");
            return "{}";
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // =========================================================
    // READ
    // =========================================================
    public Matiere trouverMatiereParCodeCours(String codeCours) {
        String sql = "SELECT * FROM " + TABLE
                + " WHERE code_cours = ? AND institution_id = ?";
        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, codeCours);
                ps.setString(2, this.institution_id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return mapMatiere(rs);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur recherche cours " + codeCours, e);
        } finally {
            closeIfNeeded(conn);
        }
        return null;
    }

    public List<Matiere> readAll() {
        List<Matiere> liste = new ArrayList<>();
        String sql = "SELECT * FROM " + TABLE + " WHERE institution_id = ?";
        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, this.institution_id);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) liste.add(mapMatiere(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lecture matières", e);
        } finally {
            closeIfNeeded(conn);
        }
        return liste;
    }

    public List<Matiere> listByInstitution() { return readAll(); }

    public List<Matiere> trouverToutesMatieresParClasse(String classeMatiere) {
        List<Matiere> liste = new ArrayList<>();
        String sql = "SELECT * FROM " + TABLE
                + " WHERE classe_matiere = ? AND institution_id = ?";
        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, classeMatiere);
                ps.setString(2, this.institution_id);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) liste.add(mapMatiere(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE,
                    "Erreur lecture matières classe " + classeMatiere, e);
        } finally {
            closeIfNeeded(conn);
        }
        return liste;
    }

    public List<Matiere> readByPeriode(String periode) {
        List<Matiere> liste = new ArrayList<>();
        String sql = "SELECT * FROM " + TABLE
                + " WHERE periode = ? AND institution_id = ?";
        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, periode);
                ps.setString(2, this.institution_id);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) liste.add(mapMatiere(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE,
                    "Erreur lecture matières période " + periode, e);
        } finally {
            closeIfNeeded(conn);
        }
        return liste;
    }

    public List<Matiere> readByStatut(String statut) {
        List<Matiere> liste = new ArrayList<>();
        String sql = "SELECT * FROM " + TABLE
                + " WHERE statut = ? AND institution_id = ?";
        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, statut);
                ps.setString(2, this.institution_id);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) liste.add(mapMatiere(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE,
                    "Erreur lecture matières statut " + statut, e);
        } finally {
            closeIfNeeded(conn);
        }
        return liste;
    }

    public int count() {
        String sql = "SELECT COUNT(*) FROM " + TABLE + " WHERE institution_id = ?";
        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, this.institution_id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur count matières", e);
        } finally {
            closeIfNeeded(conn);
        }
        return 0;
    }

    public List<Matiere> search(String keyword) {
        List<Matiere> liste = new ArrayList<>();
        String sql = "SELECT * FROM " + TABLE + " " +
                "WHERE institution_id = ? AND (nom_matiere LIKE ? OR code_cours LIKE ?)";
        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                String pattern = "%" + keyword + "%";
                ps.setString(1, this.institution_id);
                ps.setString(2, pattern);
                ps.setString(3, pattern);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) liste.add(mapMatiere(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur recherche matières " + keyword, e);
        } finally {
            closeIfNeeded(conn);
        }
        return liste;
    }

    public List<Matiere> rechercherAvecFiltres(String motCle, String annee,
                                                 String periode, String classe) {
        List<Matiere> liste = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM " + TABLE
                + " WHERE institution_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(this.institution_id);

        if (annee != null && !annee.isBlank()) {
            sql.append(" AND annee_academique = ?"); params.add(annee);
        }
        if (periode != null && !periode.isBlank()) {
            sql.append(" AND periode = ?"); params.add(periode);
        }
        if (classe != null && !classe.isBlank()) {
            sql.append(" AND classe_matiere = ?"); params.add(classe);
        }
        if (motCle != null && !motCle.isBlank()) {
            sql.append(" AND (nom_matiere LIKE ? OR code_cours LIKE ?)");
            params.add("%" + motCle + "%");
            params.add("%" + motCle + "%");
        }
        sql.append(" ORDER BY nom_matiere ASC");

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) liste.add(mapMatiere(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur recherche avec filtres", e);
        } finally {
            closeIfNeeded(conn);
        }
        return liste;
    }

    // =========================================================
    // MAPPING
    // =========================================================
    private Matiere mapMatiere(ResultSet rs) throws SQLException {
        return new Matiere(
            rs.getString("institution_id"),
            rs.getString("nom_matiere"),
            rs.getDouble("coefficient"),
            rs.getDouble("note_passage"),
            rs.getDouble("note_maximale"),
            rs.getDouble("note_minimale"),
            rs.getString("classe_matiere"),
            rs.getInt("nombre_credits"),
            rs.getString("duree_cours"),
            rs.getString("periode"),
            rs.getString("statut"),
            rs.getString("code_cours"),
            rs.getString("annee_academique"),
            rs.getString("source")
        );
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