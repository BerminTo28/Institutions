import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ExamenData {

    private static final Logger LOGGER = Logger.getLogger(ExamenData.class.getName());
    private static final String TABLE = MigrationManager.TABLE_EXAMENS;

    private static final int QUERY_TIMEOUT_SECONDS = 15;
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

    public ExamenData() {
        this.connection = null;
    }

    public ExamenData(Connection connection) {
        this.connection = connection;
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

    private static String cleComposite(String id, String institutionId) {
        return institutionId + "|" + id;
    }

    // ============================================================
    // ✅ HELPERS DE VALIDATION
    // ============================================================
    private boolean parametresValides(String... valeurs) {
        if (valeurs == null) return false;
        for (String v : valeurs) {
            if (v == null || v.isBlank()) return false;
        }
        return true;
    }

    /**
     * ✅ Vérifie que le professeur appartient bien à l'institution.
     *    Utilise {@code acces_admin} comme source de vérité.
     */
    private boolean professeurAppartientAInstitution(Connection conn,
                                                       String professeurId,
                                                       String institutionId)
            throws SQLException {
        if (conn == null || professeurId == null || institutionId == null) {
            return false;
        }
        String sql = "SELECT COUNT(*) FROM acces_admin "
                   + "WHERE utilisateur_id = ? AND institution_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setString(1, professeurId);
            ps.setString(2, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    // ============================================================
    // CREATE
    // ============================================================
    public boolean create(Examen examen) {
        if (examen == null) {
            LOGGER.warning("create() : examen null");
            return false;
        }

        // ✅ Garde en entrée
        if (!parametresValides(examen.getInstitutionId(),
                examen.getProfesseurId(),
                examen.getTitre())) {
            LOGGER.warning("create() : institutionId, professeurId ou titre manquant");
            return false;
        }

        if (examen.getId() == null || examen.getId().isBlank()) {
            examen.setId(UUID.randomUUID().toString());
        }

        Connection conn = getPreferredWriteConnection();
        if (conn == null) {
            LOGGER.severe("❌ Aucune connexion disponible pour créer examen");
            return false;
        }

        boolean ok = false;
        String usedSource = null;
        try {
            usedSource = getSource(conn);

            // ✅ VÉRIFICATION D'APPARTENANCE
            if (!professeurAppartientAInstitution(conn, examen.getProfesseurId(),
                    examen.getInstitutionId())) {
                LOGGER.warning(() -> "⛔ Le professeur " + examen.getProfesseurId()
                        + " n'appartient pas à " + examen.getInstitutionId());
                return false;
            }

            ok = createSansSync(conn, examen, usedSource);
            if (ok) declencherSyncSiHorsTransaction(conn);
        } catch (SQLException | RuntimeException e) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec création examen sur " + srcErr
                            + " : " + examen.getId());
        } finally {
            closeIfNeeded(conn);
        }

        if (ok) {
            enregistrerDansOutbox(examen, "INSERT");
            final String src = usedSource;
            LOGGER.info(() -> "✅ Examen créé sur " + src
                    + " + outbox : " + examen.getId());
        }
        return ok;
    }

    private boolean createSansSync(Connection conn, Examen examen, String source)
            throws SQLException {
        String sql = "INSERT INTO " + TABLE + " " +
                "(institution_id, id, professeur_id, code_cours, classe, periode, annee_academique, " +
                "titre, description, date_examen, heure_debut, heure_fin, duree_minutes, salle, " +
                "coefficient, note_maximale, statut, fichier_nom, fichier_chemin, fichier_type, source) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            int i = 1;
            ps.setString(i++, examen.getInstitutionId());
            ps.setString(i++, examen.getId());
            ps.setString(i++, examen.getProfesseurId());
            ps.setString(i++, examen.getCodeCours());
            ps.setString(i++, examen.getClasse());
            ps.setString(i++, examen.getPeriode());
            ps.setString(i++, examen.getAnneeAcademique());
            ps.setString(i++, examen.getTitre());
            ps.setString(i++, examen.getDescription());
            ps.setDate(i++, examen.getDateExamen() != null
                    ? new java.sql.Date(examen.getDateExamen().getTime()) : null);
            ps.setTime(i++, examen.getHeureDebut());
            ps.setTime(i++, examen.getHeureFin());
            ps.setInt(i++, examen.getDureeMinutes());
            ps.setString(i++, examen.getSalle());
            ps.setDouble(i++, examen.getCoefficient());
            ps.setDouble(i++, examen.getNoteMaximale());
            ps.setString(i++, examen.getStatut());
            ps.setString(i++, examen.getFichierNom());
            ps.setString(i++, examen.getFichierChemin());
            ps.setString(i++, examen.getFichierType());
            ps.setString(i, source != null ? source : getSource(conn));
            return ps.executeUpdate() > 0;
        }
    }

    // ============================================================
    // UPDATE
    // ============================================================
    public boolean update(Examen examen) {
        if (examen == null || examen.getId() == null || examen.getId().isBlank()) {
            LOGGER.warning("update() : examen ou id null");
            return false;
        }

        if (!parametresValides(examen.getInstitutionId(), examen.getProfesseurId())) {
            LOGGER.warning("update() : institutionId ou professeurId manquant");
            return false;
        }

        Connection conn = getPreferredWriteConnection();
        if (conn == null) {
            LOGGER.severe("❌ Aucune connexion disponible pour update examen");
            return false;
        }

        boolean ok = false;
        String usedSource = null;
        try {
            usedSource = getSource(conn);

            // ✅ VÉRIFICATION D'APPARTENANCE
            if (!professeurAppartientAInstitution(conn, examen.getProfesseurId(),
                    examen.getInstitutionId())) {
                LOGGER.warning(() -> "⛔ Le professeur " + examen.getProfesseurId()
                        + " n'appartient pas à " + examen.getInstitutionId());
                return false;
            }

            ok = updateSansSync(conn, examen, usedSource);
            if (ok) declencherSyncSiHorsTransaction(conn);
        } catch (SQLException | RuntimeException e) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec update examen sur " + srcErr
                            + " : " + examen.getId());
        } finally {
            closeIfNeeded(conn);
        }

        if (ok) {
            enregistrerDansOutbox(examen, "UPDATE");
            final String src = usedSource;
            LOGGER.info(() -> "✅ Examen mis à jour sur " + src
                    + " + outbox : " + examen.getId());
        }
        return ok;
    }

    private boolean updateSansSync(Connection conn, Examen examen, String source)
            throws SQLException {
        String sql = "UPDATE " + TABLE + " SET " +
                "code_cours=?, classe=?, periode=?, annee_academique=?, titre=?, description=?, " +
                "date_examen=?, heure_debut=?, heure_fin=?, duree_minutes=?, salle=?, " +
                "coefficient=?, note_maximale=?, statut=?, fichier_nom=?, fichier_chemin=?, fichier_type=?, " +
                "source=? " +
                "WHERE id=? AND institution_id=? AND professeur_id=?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            int i = 1;
            ps.setString(i++, examen.getCodeCours());
            ps.setString(i++, examen.getClasse());
            ps.setString(i++, examen.getPeriode());
            ps.setString(i++, examen.getAnneeAcademique());
            ps.setString(i++, examen.getTitre());
            ps.setString(i++, examen.getDescription());
            ps.setDate(i++, examen.getDateExamen() != null
                    ? new java.sql.Date(examen.getDateExamen().getTime()) : null);
            ps.setTime(i++, examen.getHeureDebut());
            ps.setTime(i++, examen.getHeureFin());
            ps.setInt(i++, examen.getDureeMinutes());
            ps.setString(i++, examen.getSalle());
            ps.setDouble(i++, examen.getCoefficient());
            ps.setDouble(i++, examen.getNoteMaximale());
            ps.setString(i++, examen.getStatut());
            ps.setString(i++, examen.getFichierNom());
            ps.setString(i++, examen.getFichierChemin());
            ps.setString(i++, examen.getFichierType());
            ps.setString(i++, source != null ? source : getSource(conn));
            ps.setString(i++, examen.getId());
            ps.setString(i++, examen.getInstitutionId());
            ps.setString(i, examen.getProfesseurId());
            return ps.executeUpdate() > 0;
        }
    }

    // ============================================================
    // DELETE
    // ============================================================
    public boolean delete(String id, String institutionId, String professeurId) {
        if (!parametresValides(id, institutionId, professeurId)) {
            LOGGER.warning("delete() : paramètres null ou vides");
            return false;
        }

        Connection conn = getPreferredWriteConnection();
        if (conn == null) return false;

        boolean autoCommitOriginal;
        String usedSource;
        try {
            usedSource = getSource(conn);

            // ✅ VÉRIFICATION D'APPARTENANCE
            if (!professeurAppartientAInstitution(conn, professeurId, institutionId)) {
                LOGGER.warning(() -> "⛔ Le professeur " + professeurId
                        + " n'appartient pas à " + institutionId);
                return false;
            }

            autoCommitOriginal = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try {
                String checkSql = "SELECT 1 FROM " + TABLE
                        + " WHERE id = ? AND institution_id = ? AND professeur_id = ?";
                boolean existe;
                try (PreparedStatement check = conn.prepareStatement(checkSql)) {
                    check.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                    check.setString(1, id);
                    check.setString(2, institutionId);
                    check.setString(3, professeurId);
                    try (ResultSet rs = check.executeQuery()) {
                        existe = rs.next();
                    }
                }
                if (!existe) {
                    conn.rollback();
                    LOGGER.warning(() -> "⚠️ Examen introuvable : " + id);
                    return false;
                }

                DeleteTracker.enregistrerSuppression(
                        conn, TABLE, cleComposite(id, institutionId), usedSource);

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
                    LOGGER.info(() -> "✅ Examen supprimé : " + id + " + outbox");
                }
                return rows > 0;

            } catch (SQLException e) {
                try { conn.rollback(); } catch (SQLException ignored) {}
                LOGGER.log(Level.SEVERE, "Erreur suppression examen", e);
                return false;
            } finally {
                try { conn.setAutoCommit(autoCommitOriginal); } catch (SQLException ignored) {}
            }

        } catch (SQLException | RuntimeException e) {
            LOGGER.log(Level.SEVERE, e, () -> "❌ Échec delete examen : " + id);
            return false;
        } finally {
            closeIfNeeded(conn);
        }
    }

    // ============================================================
    // READ — PROFESSEUR (filtre strict)
    // ============================================================
    /**
     * ✅ Lecture d'un examen par un PROFESSEUR.
     *    Filtre strict sur {@code professeur_id} + {@code institution_id}.
     */
    public Examen getByIdForProfesseur(String id, String institutionId, String professeurId)
            throws SQLException {
        if (!parametresValides(id, institutionId, professeurId)) return null;

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

    /**
     * ✅ Alias rétro-compatible.
     * @deprecated Utilisez {@link #getByIdForProfesseur}.
     */
    @Deprecated
    public Examen getById(String id, String institutionId, String professeurId)
            throws SQLException {
        return getByIdForProfesseur(id, institutionId, professeurId);
    }

    // ============================================================
    // READ — ADMIN (filtre institution uniquement)
    // ============================================================
    /**
     * ✅ Lecture d'un examen par un ADMIN.
     *    Filtre sur {@code institution_id} UNIQUEMENT.
     *
     *    <p><b>⚠️ Sécurité</b> : à n'appeler QUE si l'appelant est ADMIN.
     *    Le handler DOIT vérifier le rôle au préalable.</p>
     */
    public Examen getByIdForAdmin(String id, String institutionId) throws SQLException {
        if (!parametresValides(id, institutionId)) return null;

        String sql = "SELECT * FROM " + TABLE
                + " WHERE id = ? AND institution_id = ?";

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, id);
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

    // ============================================================
    // LISTES
    // ============================================================
    public List<Examen> getByProfesseur(String professeurId, String institutionId)
            throws SQLException {
        if (!parametresValides(professeurId, institutionId)) {
            return Collections.emptyList();
        }

        List<Examen> list = new ArrayList<>();
        String sql = "SELECT * FROM " + TABLE +
                " WHERE professeur_id = ? AND institution_id = ? " +
                "ORDER BY date_examen DESC, heure_debut";

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

    public List<Examen> getByFiltres(String professeurId, String institutionId,
                                       String codeCours, String classe,
                                       String periode, String annee) throws SQLException {
        if (!parametresValides(professeurId, institutionId)) {
            return Collections.emptyList();
        }

        List<Examen> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT * FROM " + TABLE +
                " WHERE professeur_id = ? AND institution_id = ?");
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
        sql.append(" ORDER BY date_examen DESC, heure_debut");

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

    /**
     * ✅ Liste tous les examens d'une institution — ADMIN UNIQUEMENT.
     */
    public List<Examen> getAllByInstitution(String institutionId, String codeCours,
                                              String classe, String periode,
                                              String annee, String statut)
            throws SQLException {
        if (!parametresValides(institutionId)) {
            return Collections.emptyList();
        }

        List<Examen> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM " + TABLE
                + " WHERE institution_id = ?");
        List<Object> params = new ArrayList<>();
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
        if (statut != null && !statut.isBlank()) {
            sql.append(" AND statut = ?"); params.add(statut);
        }
        sql.append(" ORDER BY date_examen DESC, heure_debut");

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

    // ============================================================
    // LISTES POUR ÉTUDIANTS (filtre classe + statut)
    // ============================================================
    public List<Examen> getByClasse(String institutionId, String classe,
                                      String annee, String periode)
            throws SQLException {
        if (!parametresValides(institutionId, classe)) {
            return Collections.emptyList();
        }

        List<Examen> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT e.*, m.nom_matiere FROM " + TABLE + " e " +
                "LEFT JOIN " + MigrationManager.TABLE_MATIERES + " m "
                + " ON e.code_cours = m.code_cours "
                + " AND e.institution_id = m.institution_id " +
                "WHERE e.institution_id = ? AND e.classe = ?");
        List<Object> params = new ArrayList<>();
        params.add(institutionId);
        params.add(classe);

        if (annee != null && !annee.isBlank()) {
            sql.append(" AND e.annee_academique = ?"); params.add(annee);
        }
        if (periode != null && !periode.isBlank()) {
            sql.append(" AND e.periode = ?"); params.add(periode);
        }
        sql.append(" AND e.statut IN ('PREVU', 'EN_COURS', 'TERMINE')");
        sql.append(" ORDER BY e.date_examen DESC, e.heure_debut");

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
                        Examen e = mapResultSet(rs);
                        e.setNomMatiere(rs.getString("nom_matiere"));
                        list.add(e);
                    }
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return list;
    }

    // ============================================================
    // LISTES AUXILIAIRES
    // ============================================================
    public List<String> getAnneesDisponibles(String institutionId, String classe)
            throws SQLException {
        if (!parametresValides(institutionId, classe)) {
            return Collections.emptyList();
        }

        List<String> annees = new ArrayList<>();
        String sql = "SELECT DISTINCT annee_academique FROM " + TABLE +
                " WHERE institution_id = ? AND classe = ? ORDER BY annee_academique DESC";

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                ps.setString(2, classe);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) annees.add(rs.getString("annee_academique"));
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return annees;
    }

    public List<String> getPeriodesDisponibles(String institutionId, String classe,
                                                 String annee) throws SQLException {
        if (!parametresValides(institutionId, classe, annee)) {
            return Collections.emptyList();
        }

        List<String> periodes = new ArrayList<>();
        String sql = "SELECT DISTINCT periode FROM " + TABLE +
                " WHERE institution_id = ? AND classe = ? AND annee_academique = ? ORDER BY periode";

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                ps.setString(2, classe);
                ps.setString(3, annee);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) periodes.add(rs.getString("periode"));
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return periodes;
    }

    public List<String> getCoursDisponibles(String institutionId, String classe,
                                              String annee, String periode)
            throws SQLException {
        if (!parametresValides(institutionId, classe, annee, periode)) {
            return Collections.emptyList();
        }

        List<String> cours = new ArrayList<>();
        String sql = "SELECT DISTINCT e.code_cours, m.nom_matiere FROM "
                + TABLE + " e " +
                "LEFT JOIN " + MigrationManager.TABLE_MATIERES + " m "
                + " ON e.code_cours = m.code_cours "
                + " AND e.institution_id = m.institution_id " +
                "WHERE e.institution_id = ? AND e.classe = ? AND e.annee_academique = ? "
                + "AND e.periode = ? ORDER BY m.nom_matiere";

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                ps.setString(2, classe);
                ps.setString(3, annee);
                ps.setString(4, periode);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String code = rs.getString("code_cours");
                        String nom = rs.getString("nom_matiere");
                        if (nom == null || nom.isBlank()) nom = code;
                        cours.add(code + " - " + nom);
                    }
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return cours;
    }

    // ============================================================
    // OUTBOX
    // ============================================================
    private void enregistrerDansOutbox(Examen examen, String operation) {
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
            ps.setString(3, cleComposite(examen.getId(), examen.getInstitutionId()));
            ps.setString(4, toJson(examen));
            ps.executeUpdate();
            LOGGER.fine(() -> "📥 Outbox " + operation + " : " + examen.getId());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Impossible d'enregistrer dans l'outbox : "
                            + examen.getId());
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

    private String toJson(Examen e) {
        try {
            return GSON.toJson(e);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, ex,
                    () -> "Impossible de sérialiser l'examen en JSON");
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
    private Examen mapResultSet(ResultSet rs) throws SQLException {
        Examen e = new Examen();
        e.setId(rs.getString("id"));
        e.setInstitutionId(rs.getString("institution_id"));
        e.setProfesseurId(rs.getString("professeur_id"));
        e.setCodeCours(rs.getString("code_cours"));
        e.setClasse(rs.getString("classe"));
        e.setPeriode(rs.getString("periode"));
        e.setAnneeAcademique(rs.getString("annee_academique"));
        e.setTitre(rs.getString("titre"));
        e.setDescription(rs.getString("description"));
        e.setDateExamen(rs.getDate("date_examen"));
        e.setHeureDebut(rs.getTime("heure_debut"));
        e.setHeureFin(rs.getTime("heure_fin"));
        e.setDureeMinutes(rs.getInt("duree_minutes"));
        e.setSalle(rs.getString("salle"));
        e.setCoefficient(rs.getDouble("coefficient"));
        e.setNoteMaximale(rs.getDouble("note_maximale"));
        e.setStatut(rs.getString("statut"));
        e.setFichierNom(rs.getString("fichier_nom"));
        e.setFichierChemin(rs.getString("fichier_chemin"));
        e.setFichierType(rs.getString("fichier_type"));
        e.setCreatedAt(rs.getTimestamp("created_at"));
        e.setUpdatedAt(rs.getTimestamp("updated_at"));
        try { e.setSource(rs.getString("source")); } catch (SQLException ignored) {}
        return e;
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