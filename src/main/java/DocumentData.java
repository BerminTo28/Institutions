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

public class DocumentData {

    private static final Logger LOGGER = Logger.getLogger(DocumentData.class.getName());
    private static final String TABLE = MigrationManager.TABLE_DOCUMENTS_PROFESSEUR;

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

    public DocumentData() {
        this.connection = null;
    }

    public DocumentData(Connection connection) {
        this.connection = connection;
    }

    // =========================================================
    // HELPERS
    // =========================================================
    private DatabaseManager db() { return DatabaseManager.getInstance(); }

    private Connection getConnection() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            return connection;
        }
        return db().getConnection();
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
        return id + "|" + institutionId;
    }

    // =========================================================
    // ✅ NOUVEAU : VALIDATION DES PARAMÈTRES
    // =========================================================
    /**
     * ✅ Vérifie que tous les paramètres sont non-null et non-vides.
     */
    private boolean parametresValides(String... valeurs) {
        if (valeurs == null) return false;
        for (String v : valeurs) {
            if (v == null || v.isBlank()) return false;
        }
        return true;
    }

    // =========================================================
    // ✅ NOUVEAU : VÉRIFICATION D'APPARTENANCE
    // =========================================================
    /**
     * ✅ Vérifie que le professeur appartient bien à l'institution.
     *    Utilise la table {@code acces_admin} comme source de vérité.
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

    // =========================================================
    // CREATE
    // =========================================================
    public boolean create(DocumentProfesseur doc) {
        if (doc == null) {
            LOGGER.warning("create() : document null");
            return false;
        }

        // ✅ Garde en entrée
        if (!parametresValides(doc.getInstitutionId(),
                doc.getProfesseurId(),
                doc.getTitre())) {
            LOGGER.warning("create() : institutionId, professeurId ou titre manquant");
            return false;
        }

        // ✅ Générer un UUID si absent
        if (doc.getId() == null || doc.getId().isBlank()) {
            doc.setId(UUID.randomUUID().toString());
        }

        Connection conn = getPreferredWriteConnection();
        if (conn == null) {
            LOGGER.severe(() -> "❌ Aucune connexion disponible pour créer document "
                    + doc.getId());
            return false;
        }

        boolean ok = false;
        String usedSource = null;
        try {
            usedSource = getSource(conn);

            // ✅ VÉRIFICATION D'APPARTENANCE
            if (!professeurAppartientAInstitution(conn, doc.getProfesseurId(),
                    doc.getInstitutionId())) {
                LOGGER.warning(() -> "⛔ Le professeur " + doc.getProfesseurId()
                        + " n'appartient pas à " + doc.getInstitutionId());
                return false;
            }

            ok = createSansSync(conn, doc, usedSource);
            if (ok) declencherSyncSiHorsTransaction(conn);
        } catch (SQLException | RuntimeException e) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec création document sur " + srcErr
                            + " : " + doc.getId());
        } finally {
            closeIfNeeded(conn);
        }

        if (ok) {
            enregistrerDansOutbox(doc, "INSERT");
            final String src = usedSource;
            LOGGER.info(() -> "✅ Document créé sur " + src
                    + " + outbox : " + doc.getId());
        }
        return ok;
    }

    private boolean createSansSync(Connection conn, DocumentProfesseur doc, String source)
            throws SQLException {
        String sql = "INSERT INTO " + TABLE + " "
                + "(institution_id, id, professeur_id, titre, description, "
                + "fichier_nom, fichier_chemin, url, type, matiere, classe, periode, annee_academique, "
                + "est_public, taille, source) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            int idx = 1;
            ps.setString(idx++, doc.getInstitutionId());
            ps.setString(idx++, doc.getId());
            ps.setString(idx++, doc.getProfesseurId());
            ps.setString(idx++, doc.getTitre());
            ps.setString(idx++, doc.getDescription());
            ps.setString(idx++, doc.getFichierNom());
            ps.setString(idx++, doc.getFichierChemin());
            ps.setString(idx++, doc.getUrl());
            ps.setString(idx++, doc.getType());
            ps.setString(idx++, doc.getMatiere());
            ps.setString(idx++, doc.getClasse());
            ps.setString(idx++, doc.getPeriode());
            ps.setString(idx++, doc.getAnneeAcademique());
            ps.setBoolean(idx++, doc.isEstPublic());
            ps.setLong(idx++, doc.getTaille());
            ps.setString(idx, source != null ? source : getSource(conn));

            return ps.executeUpdate() > 0;
        }
    }

    // =========================================================
    // UPDATE
    // =========================================================
    public boolean update(DocumentProfesseur doc) {
        if (doc == null) {
            LOGGER.warning("update() : document null");
            return false;
        }

        // ✅ Garde en entrée
        if (!parametresValides(doc.getInstitutionId(),
                doc.getProfesseurId(),
                doc.getId())) {
            LOGGER.warning("update() : institutionId, professeurId ou id manquant");
            return false;
        }

        Connection conn = getPreferredWriteConnection();
        if (conn == null) return false;

        boolean ok = false;
        String usedSource = null;
        try {
            usedSource = getSource(conn);

            // ✅ VÉRIFICATION D'APPARTENANCE
            if (!professeurAppartientAInstitution(conn, doc.getProfesseurId(),
                    doc.getInstitutionId())) {
                LOGGER.warning(() -> "⛔ Le professeur " + doc.getProfesseurId()
                        + " n'appartient pas à " + doc.getInstitutionId());
                return false;
            }

            ok = updateSansSync(conn, doc, usedSource);
            if (ok) declencherSyncSiHorsTransaction(conn);
        } catch (SQLException | RuntimeException e) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec update document sur " + srcErr
                            + " : " + doc.getId());
        } finally {
            closeIfNeeded(conn);
        }

        if (ok) {
            enregistrerDansOutbox(doc, "UPDATE");
            final String src = usedSource;
            LOGGER.info(() -> "✅ Document mis à jour sur " + src
                    + " + outbox : " + doc.getId());
        }
        return ok;
    }

    private boolean updateSansSync(Connection conn, DocumentProfesseur doc, String source)
            throws SQLException {
        String sql = "UPDATE " + TABLE + " SET titre=?, description=?, fichier_nom=?, "
                + "fichier_chemin=?, url=?, type=?, matiere=?, classe=?, periode=?, "
                + "annee_academique=?, est_public=?, taille=?, source=? "
                + "WHERE institution_id=? AND id=? AND professeur_id=?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            int idx = 1;
            ps.setString(idx++, doc.getTitre());
            ps.setString(idx++, doc.getDescription());
            ps.setString(idx++, doc.getFichierNom());
            ps.setString(idx++, doc.getFichierChemin());
            ps.setString(idx++, doc.getUrl());
            ps.setString(idx++, doc.getType());
            ps.setString(idx++, doc.getMatiere());
            ps.setString(idx++, doc.getClasse());
            ps.setString(idx++, doc.getPeriode());
            ps.setString(idx++, doc.getAnneeAcademique());
            ps.setBoolean(idx++, doc.isEstPublic());
            ps.setLong(idx++, doc.getTaille());
            ps.setString(idx++, source != null ? source : getSource(conn));
            ps.setString(idx++, doc.getInstitutionId());
            ps.setString(idx++, doc.getId());
            ps.setString(idx, doc.getProfesseurId());

            return ps.executeUpdate() > 0;
        }
    }

    // =========================================================
    // DELETE
    // =========================================================
    public boolean delete(String id, String institutionId, String professeurId) {
        // ✅ Garde en entrée
        if (!parametresValides(id, institutionId, professeurId)) {
            LOGGER.warning("delete() : paramètres invalides (id, institutionId ou professeurId)");
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
                // ① Tombstone composite
                DeleteTracker.enregistrerSuppression(
                        conn, TABLE, cleComposite(id, institutionId), usedSource);

                // ② DELETE réel
                String sql = "DELETE FROM " + TABLE
                        + " WHERE institution_id=? AND id=? AND professeur_id=?";
                int rows;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                    ps.setString(1, institutionId);
                    ps.setString(2, id);
                    ps.setString(3, professeurId);
                    rows = ps.executeUpdate();
                }

                conn.commit();

                if (rows > 0) {
                    enregistrerDeleteDansOutbox(id, institutionId, usedSource);
                    declencherSyncSiHorsTransaction(conn);
                    LOGGER.info(() -> "✅ Document supprimé : " + id + " + outbox");
                }
                return rows > 0;

            } catch (SQLException e) {
                try { conn.rollback(); } catch (SQLException ignored) {}
                LOGGER.log(Level.SEVERE, "❌ Erreur suppression document", e);
                return false;
            } finally {
                try { conn.setAutoCommit(autoCommitOriginal); } catch (SQLException ignored) {}
            }

        } catch (SQLException | RuntimeException e) {
            LOGGER.log(Level.SEVERE, e, () -> "❌ Échec suppression document : " + id);
            return false;
        } finally {
            closeIfNeeded(conn);
        }
    }

    public List<DocumentProfesseur> getDocumentsForStudentStrict(
        String institutionId,
        String classeEtudiant,
        String periodeEtudiant,
        String anneeEtudiant,
        List<String> matieresEtudiant,
        String matiereFiltre,
        String periodeFiltre,
        String anneeFiltre) throws SQLException {

    // ✅ Garde d'entrée
    if (!parametresValides(institutionId, classeEtudiant)) {
        LOGGER.warning("getDocumentsForStudentStrict : paramètres invalides");
        return Collections.emptyList();
    }

    // ✅ Si l'étudiant n'a AUCUNE matière → aucun document
    if (matieresEtudiant == null || matieresEtudiant.isEmpty()) {
        LOGGER.info("⚠️ Aucune matière pour l'étudiant → aucun document");
        return Collections.emptyList();
    }

    List<DocumentProfesseur> list = new ArrayList<>();

    StringBuilder sql = new StringBuilder(
            "SELECT * FROM " + TABLE
            + " WHERE institution_id = ?"
    );
    List<Object> params = new ArrayList<>();
    params.add(institutionId);

    // ✅ CLASSE : document.classe = classe de l'étudiant (insensible à la casse)
    sql.append(" AND (est_public = true OR LOWER(classe) = LOWER(?))");
    params.add(classeEtudiant);

    // ✅ ANNÉE : si l'étudiant a une année, filtrer
    String anneeEffective = (anneeFiltre != null && !anneeFiltre.isBlank())
            ? anneeFiltre
            : anneeEtudiant;
    if (anneeEffective != null && !anneeEffective.isBlank()) {
        sql.append(" AND (annee_academique IS NULL OR annee_academique = ?)");
        params.add(anneeEffective);
    }

    // ✅ PÉRIODE : si l'étudiant a une période, filtrer
    String periodeEffective = (periodeFiltre != null && !periodeFiltre.isBlank())
            ? periodeFiltre
            : periodeEtudiant;
    if (periodeEffective != null && !periodeEffective.isBlank()) {
        sql.append(" AND (periode IS NULL OR periode = ?)");
        params.add(periodeEffective);
    }

    // ✅ MATIÈRE : le document doit être dans les matières de l'étudiant
    StringBuilder matieresClause = new StringBuilder(" AND (");
    for (int i = 0; i < matieresEtudiant.size(); i++) {
        if (i > 0) matieresClause.append(" OR ");
        matieresClause.append("matiere = ?");
        params.add(matieresEtudiant.get(i));
    }
    matieresClause.append(")");
    sql.append(matieresClause);

    // ✅ Filtre optionnel sur une matière spécifique (déjà validée côté handler)
    if (matiereFiltre != null && !matiereFiltre.isBlank()) {
        if (matieresEtudiant.contains(matiereFiltre)) {
            sql.append(" AND matiere = ?");
            params.add(matiereFiltre);
        } else {
            LOGGER.warning(() -> "⛔ Filtre matière non autorisé : " + matiereFiltre);
            return Collections.emptyList();
        }
    }

    sql.append(" ORDER BY date_creation DESC");

    // ✅ Log de diagnostic
    final String sqlLog = sql.toString();
    final List<Object> paramsLog = new ArrayList<>(params);
    LOGGER.info(() -> "🔍 getDocumentsForStudentStrict SQL : " + sqlLog);
    LOGGER.info(() -> "🔍 getDocumentsForStudentStrict params : " + paramsLog);

    Connection conn = null;
    try {
        conn = getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapDocument(rs));
            }
        }
    } finally {
        closeIfNeeded(conn);
    }

    LOGGER.info(() -> "🔍 " + list.size() + " document(s) trouvé(s) pour "
            + "classe='" + classeEtudiant + "'"
            + " periode='" + periodeEffective + "'"
            + " annee='" + anneeEffective + "'");

    return list;
}
    // =========================================================
    // READ
    // =========================================================
    public DocumentProfesseur getById(String id, String institutionId, String professeurId)
            throws SQLException {
        // ✅ Garde en entrée
        if (!parametresValides(id, institutionId, professeurId)) {
            return null;
        }

        String sql = "SELECT * FROM " + TABLE
                + " WHERE institution_id=? AND id=? AND professeur_id=?";

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                ps.setString(2, id);
                ps.setString(3, professeurId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return mapDocument(rs);
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return null;
    }

    public List<DocumentProfesseur> getAll(String institutionId, String professeurId)
            throws SQLException {
        // ✅ Garde en entrée
        if (!parametresValides(institutionId, professeurId)) {
            LOGGER.fine("getAll() : paramètres invalides → retour vide");
            return Collections.emptyList();
        }

        List<DocumentProfesseur> list = new ArrayList<>();
        String sql = "SELECT * FROM " + TABLE
                + " WHERE institution_id=? AND professeur_id=? ORDER BY date_creation DESC";

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                ps.setString(2, professeurId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) list.add(mapDocument(rs));
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return list;
    }

    public List<DocumentProfesseur> getByFilters(String institutionId, String professeurId,
                                                   String matiere, String classe,
                                                   String periode) throws SQLException {
        // ✅ Garde en entrée
        if (!parametresValides(institutionId, professeurId)) {
            LOGGER.fine("getByFilters() : institutionId ou professeurId manquant → vide");
            return Collections.emptyList();
        }

        List<DocumentProfesseur> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT * FROM " + TABLE + " WHERE institution_id=? AND professeur_id=?");
        List<Object> params = new ArrayList<>();
        params.add(institutionId);
        params.add(professeurId);

        if (matiere != null && !matiere.isBlank()) {
            sql.append(" AND matiere = ?"); params.add(matiere);
        }
        if (classe != null && !classe.isBlank()) {
            sql.append(" AND classe = ?"); params.add(classe);
        }
        if (periode != null && !periode.isBlank()) {
            sql.append(" AND periode = ?"); params.add(periode);
        }
        sql.append(" ORDER BY date_creation DESC");

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) list.add(mapDocument(rs));
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return list;
    }

    /**
     * ✅ Lecture d'un document SANS filtrer sur le professeur.
     *    <p>Utile pour le partage de documents au sein d'une institution
     *    (ex: un professeur peut consulter un document public d'un collègue).</p>
     *
     *    <p><b>Sécurité</b> : filtre strictement sur {@code institution_id}.</p>
     */
    public DocumentProfesseur getByIdAndInstitution(String id, String institutionId)
            throws SQLException {
        // ✅ Garde en entrée
        if (!parametresValides(id, institutionId)) {
            return null;
        }

        String sql = "SELECT * FROM " + TABLE
                + " WHERE institution_id=? AND id=?";

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                ps.setString(2, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return mapDocument(rs);
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return null;
    }

    public List<DocumentProfesseur> getDocumentsForStudent(String institutionId,
                                                             String classeEtudiant,
                                                             String matiere, String periode,
                                                             String anneeAcademique)
            throws SQLException {
        // ✅ Garde en entrée : institutionId obligatoire
        if (!parametresValides(institutionId)) {
            LOGGER.fine("getDocumentsForStudent() : institutionId manquant → vide");
            return Collections.emptyList();
        }

        List<DocumentProfesseur> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT * FROM " + TABLE
                + " WHERE institution_id = ? AND (est_public = true OR classe = ?)");
        List<Object> params = new ArrayList<>();
        params.add(institutionId);
        params.add(classeEtudiant != null ? classeEtudiant : "");

        if (matiere != null && !matiere.isBlank()) {
            sql.append(" AND matiere = ?"); params.add(matiere);
        }
        if (periode != null && !periode.isBlank()) {
            sql.append(" AND periode = ?"); params.add(periode);
        }
        if (anneeAcademique != null && !anneeAcademique.isBlank()) {
            sql.append(" AND annee_academique = ?"); params.add(anneeAcademique);
        }
        sql.append(" ORDER BY date_creation DESC");

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) list.add(mapDocument(rs));
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return list;
    }

    // =========================================================
    // OUTBOX
    // =========================================================
    private void enregistrerDansOutbox(DocumentProfesseur doc, String operation) {
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
            ps.setString(3, cleComposite(doc.getId(), doc.getInstitutionId()));
            ps.setString(4, toJson(doc));
            ps.executeUpdate();
            LOGGER.fine(() -> "📥 Outbox " + operation + " : " + doc.getId());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Impossible d'enregistrer dans l'outbox : " + doc.getId());
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

    private String toJson(DocumentProfesseur doc) {
        try {
            return GSON.toJson(doc);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, e,
                    () -> "Impossible de sérialiser le document en JSON");
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
    private DocumentProfesseur mapDocument(ResultSet rs) throws SQLException {
        DocumentProfesseur doc = new DocumentProfesseur();
        try { doc.setId(rs.getString("id")); } catch (SQLException ignored) {}

        doc.setInstitutionId(rs.getString("institution_id"));
        doc.setProfesseurId(rs.getString("professeur_id"));
        doc.setTitre(rs.getString("titre"));
        doc.setDescription(rs.getString("description"));
        doc.setFichierNom(rs.getString("fichier_nom"));
        doc.setFichierChemin(rs.getString("fichier_chemin"));
        doc.setUrl(rs.getString("url"));
        doc.setType(rs.getString("type"));
        doc.setMatiere(rs.getString("matiere"));
        doc.setClasse(rs.getString("classe"));
        doc.setPeriode(rs.getString("periode"));
        doc.setAnneeAcademique(rs.getString("annee_academique"));
        doc.setDateCreation(rs.getTimestamp("date_creation"));
        doc.setDateModification(rs.getTimestamp("date_modification"));
        doc.setEstPublic(rs.getBoolean("est_public"));
        doc.setTaille(rs.getLong("taille"));

        try {
            doc.setSource(rs.getString("source"));
        } catch (SQLException ignored) {}

        return doc;
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