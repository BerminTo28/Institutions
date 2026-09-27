import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AttestationDAO {

    private static final Logger LOGGER = Logger.getLogger(AttestationDAO.class.getName());
    private static final String TABLE = MigrationManager.TABLE_ATTESTATIONS;

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

    private AttestationDAO() {}

    // =========================================================
    // HELPERS
    // =========================================================
    private static DatabaseManager db() { return DatabaseManager.getInstance(); }

    private static boolean isConnectionAlive(Connection conn) {
        if (conn == null) return false;
        try { return conn.isValid(5); } catch (SQLException e) { return false; }
    }

    /**
     * ✅ Détermine la source (LOCAL/REMOTE) par comparaison d'URL JDBC.
     */
    private static String getSource(Connection conn) {
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
    private static Connection getPreferredWriteConnection() {
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

    private static void closeIfNeeded(Connection conn) {
        if (conn != null) {
            try {
                if (!conn.isClosed()) conn.close();
            } catch (SQLException ignored) {}
        }
    }

    /**
     * ✅ Clé composite pour les tombstones.
     */
    private static String cleComposite(String id, String institutionId) {
        return id + "|" + institutionId;
    }

    // =========================================================
    // READ ALL
    // =========================================================
    public static List<Attestation> getAll(String institutionId) {
        List<Attestation> list = new ArrayList<>();
        String sql = "SELECT * FROM " + TABLE
                + " WHERE institution_id = ? ORDER BY date_emission DESC";
        try (Connection conn = db().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setString(1, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToAttestation(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur getAll attestations", e);
        }
        return list;
    }

    // =========================================================
    // SAVE (insert ou update)
    // =========================================================
    public static boolean save(Attestation a) {
        if (a == null) return false;
        if (a.getId() == null || a.getId().isBlank()) {
            return insert(a);
        } else {
            return update(a);
        }
    }

    // =========================================================
    // INSERT — LOCAL uniquement + outbox
    // =========================================================
    private static boolean insert(Attestation a) {
        // ✅ Générer un UUID si absent
        if (a.getId() == null || a.getId().isBlank()) {
            a.setId(UUID.randomUUID().toString());
        }

        Connection conn = getPreferredWriteConnection();
        if (conn == null) {
            LOGGER.severe(() -> "❌ Aucune connexion disponible pour insert attestation "
                    + a.getNumero());
            return false;
        }

        boolean ok = false;
        String usedSource = null;
        try {
            usedSource = getSource(conn);
            ok = insertSansSync(conn, a, usedSource);
            if (ok) declencherSyncSiHorsTransaction(conn);
        } catch (SQLException | RuntimeException e) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec insert attestation sur " + srcErr
                            + " : " + a.getNumero());
        } finally {
            closeIfNeeded(conn);
        }

        if (ok) {
            enregistrerDansOutbox(a, "INSERT");

            final String src = usedSource;
            LOGGER.info(() -> "✅ Attestation créée sur " + src
                    + " + outbox : " + a.getNumero());
        }
        return ok;
    }

    private static boolean insertSansSync(Connection conn, Attestation a, String source)
            throws SQLException {
        String sql = "INSERT INTO " + TABLE
                + " (institution_id, id, numero_identifiant, type, date_emission, contenu_html, "
                + "reference, numero, classe, annee_academique, session, `option`, periode, source) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            int i = 1;
            ps.setString(i++, a.getInstitutionId());
            ps.setString(i++, a.getId());
            ps.setString(i++, a.getEtudiantId());
            ps.setString(i++, a.getType());
            ps.setDate(i++, a.getDateEmission() != null
                    ? new java.sql.Date(a.getDateEmission().getTime())
                    : new java.sql.Date(System.currentTimeMillis()));
            ps.setString(i++, a.getContenuHTML());
            ps.setString(i++, a.getReference());
            ps.setString(i++, a.getNumero());
            ps.setString(i++, a.getClasse());
            ps.setString(i++, a.getAnneeAcademique());
            ps.setString(i++, a.getSession());
            ps.setString(i++, a.getOption());
            ps.setString(i++, a.getPeriode());
            ps.setString(i, source != null ? source : getSource(conn));

            return ps.executeUpdate() > 0;
        }
    }

    // =========================================================
    // UPDATE — LOCAL uniquement + outbox
    // =========================================================
    private static boolean update(Attestation a) {
        if (a.getId() == null || a.getId().isBlank()) {
            LOGGER.warning("update() : id null ou vide");
            return false;
        }

        Connection conn = getPreferredWriteConnection();
        if (conn == null) return false;

        boolean ok = false;
        String usedSource = null;
        try {
            usedSource = getSource(conn);
            ok = updateSansSync(conn, a, usedSource);
            if (ok) declencherSyncSiHorsTransaction(conn);
        } catch (SQLException | RuntimeException e) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec update attestation sur " + srcErr
                            + " : " + a.getNumero());
        } finally {
            closeIfNeeded(conn);
        }

        if (ok) {
            enregistrerDansOutbox(a, "UPDATE");

            final String src = usedSource;
            LOGGER.info(() -> "✅ Attestation mise à jour sur " + src
                    + " + outbox : " + a.getNumero());
        }
        return ok;
    }

    private static boolean updateSansSync(Connection conn, Attestation a, String source)
            throws SQLException {
        // ✅ Ajout de source = ?
        String sql = "UPDATE " + TABLE + " SET numero_identifiant=?, type=?, date_emission=?, "
                + "contenu_html=?, reference=?, numero=?, classe=?, annee_academique=?, "
                + "session=?, `option`=?, periode=?, source=? "
                + "WHERE institution_id=? AND id=?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            int i = 1;
            ps.setString(i++, a.getEtudiantId());
            ps.setString(i++, a.getType());
            ps.setDate(i++, a.getDateEmission() != null
                    ? new java.sql.Date(a.getDateEmission().getTime())
                    : new java.sql.Date(System.currentTimeMillis()));
            ps.setString(i++, a.getContenuHTML());
            ps.setString(i++, a.getReference());
            ps.setString(i++, a.getNumero());
            ps.setString(i++, a.getClasse());
            ps.setString(i++, a.getAnneeAcademique());
            ps.setString(i++, a.getSession());
            ps.setString(i++, a.getOption());
            ps.setString(i++, a.getPeriode());
            ps.setString(i++, source != null ? source : getSource(conn));
            ps.setString(i++, a.getInstitutionId());
            ps.setString(i, a.getId());

            return ps.executeUpdate() > 0;
        }
    }

    // =========================================================
    // DELETE — LOCAL uniquement + tombstone + outbox
    // =========================================================
    public static boolean delete(String id, String institutionId) {
        if (id == null || id.isBlank()) {
            LOGGER.warning("delete() : id null ou vide");
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
                        conn, TABLE, cleComposite(id, institutionId), usedSource);

                // ② DELETE réel
                String sql = "DELETE FROM " + TABLE
                        + " WHERE institution_id = ? AND id = ?";
                int rows;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                    ps.setString(1, institutionId);
                    ps.setString(2, id);
                    rows = ps.executeUpdate();
                }

                conn.commit();

                if (rows > 0) {
                    enregistrerDeleteDansOutbox(id, institutionId, usedSource);
                    declencherSyncSiHorsTransaction(conn);
                    LOGGER.info(() -> "✅ Attestation supprimée : " + id + " + outbox");
                }
                return rows > 0;

            } catch (SQLException e) {
                try { conn.rollback(); } catch (SQLException ignored) {}
                LOGGER.log(Level.SEVERE, "❌ Erreur delete attestation", e);
                return false;
            } finally {
                try { conn.setAutoCommit(autoCommitOriginal); } catch (SQLException ignored) {}
            }

        } catch (SQLException | RuntimeException e) {
            LOGGER.log(Level.SEVERE, e, () -> "❌ Échec delete attestation : " + id);
            return false;
        } finally {
            closeIfNeeded(conn);
        }
    }

    // =========================================================
    // GENERER NUMERO
    // =========================================================
    public static String genererNumero(String institutionId) {
        String sql = "SELECT MAX(CAST(numero AS UNSIGNED)) FROM " + TABLE
                + " WHERE institution_id = ? AND numero REGEXP '^[0-9]+$'";
        try (Connection conn = db().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setString(1, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                int max = 0;
                if (rs.next()) max = rs.getInt(1);
                return String.valueOf(max + 1);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur genererNumero", e);
            return "1";
        }
    }

    // =========================================================
    // GET BY ID
    // =========================================================
    public static Attestation getById(String id, String institutionId) {
        if (id == null || id.isBlank()) return null;

        String sql = "SELECT * FROM " + TABLE
                + " WHERE institution_id = ? AND id = ?";
        try (Connection conn = db().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setString(1, institutionId);
            ps.setString(2, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToAttestation(rs);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur getById attestation", e);
        }
        return null;
    }

    // =========================================================
    // GET ALL WITH FILTERS
    // =========================================================
    public static List<Attestation> getAllWithFilters(String institutionId, String etudiantId,
                                                       String annee, String session,
                                                       String classe, String periode) {
        List<Attestation> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM " + TABLE
                + " WHERE institution_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(institutionId);

        if (etudiantId != null && !etudiantId.isEmpty()) {
            sql.append(" AND numero_identifiant = ?");
            params.add(etudiantId);
        }
        if (annee != null && !annee.isEmpty()) {
            sql.append(" AND annee_academique = ?");
            params.add(annee);
        }
        if (session != null && !session.isEmpty()) {
            sql.append(" AND session = ?");
            params.add(session);
        }
        if (classe != null && !classe.isEmpty()) {
            sql.append(" AND classe = ?");
            params.add(classe);
        }
        if (periode != null && !periode.isEmpty()) {
            sql.append(" AND periode = ?");
            params.add(periode);
        }

        sql.append(" ORDER BY date_emission DESC");

        try (Connection conn = db().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToAttestation(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur getAll attestations avec filtres", e);
        }
        return list;
    }

    // =========================================================
    // OUTBOX
    // =========================================================
    private static void enregistrerDansOutbox(Attestation a, String operation) {
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
            ps.setString(3, cleComposite(a.getId(), a.getInstitutionId()));
            ps.setString(4, toJson(a));
            ps.executeUpdate();
            LOGGER.fine(() -> "📥 Outbox " + operation + " : " + a.getNumero());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Impossible d'enregistrer dans l'outbox : " + a.getNumero());
        }
    }

    private static void enregistrerDeleteDansOutbox(String id, String institutionId, String source) {
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

    private static String toJson(Attestation a) {
        try {
            return GSON.toJson(a);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, e,
                    () -> "Impossible de sérialiser l'attestation en JSON");
            return "{}";
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // =========================================================
    // MAPPING
    // =========================================================
    private static Attestation mapResultSetToAttestation(ResultSet rs) throws SQLException {
        Attestation a = new Attestation();
        try { a.setId(rs.getString("id")); } catch (SQLException ignored) {}

        a.setInstitutionId(rs.getString("institution_id"));
        a.setEtudiantId(rs.getString("numero_identifiant"));
        a.setType(rs.getString("type"));
        a.setReference(rs.getString("reference"));
        a.setNumero(rs.getString("numero"));
        a.setDateEmission(rs.getDate("date_emission"));
        a.setContenuHTML(rs.getString("contenu_html"));
        a.setOption(rs.getString("option"));
        a.setAnneeAcademique(rs.getString("annee_academique"));
        a.setSession(rs.getString("session"));
        a.setClasse(rs.getString("classe"));
        a.setPeriode(rs.getString("periode"));

        try { a.setSource(rs.getString("source")); } catch (SQLException ignored) {}

        return a;
    }

    // =========================================================
    // ETUDIANT ET INSTITUTION (lectures)
    // =========================================================
    public static Etudiant getEtudiant(String etudiantId, String institutionId) {
        String sql = "SELECT * FROM " + MigrationManager.TABLE_ETUDIANTS
                + " WHERE institution_id = ? AND numero_identifiant = ?";
        try (Connection conn = db().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setString(1, institutionId);
            ps.setString(2, etudiantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Etudiant e = new Etudiant();
                    e.setNumeroIdentifiantEtudiant(rs.getString("numero_identifiant"));
                    e.setNom(rs.getString("nom"));
                    e.setPrenom(rs.getString("prenom"));
                    e.setSexe(rs.getString("sexe"));
                    e.setClasse(rs.getString("classe"));
                    e.setAnneeAcademique(rs.getString("annee_academique"));
                    e.setPromotion(rs.getString("promotion"));
                    e.setEmail(rs.getString("email"));
                    e.setTelephone(rs.getString("telephone"));
                    e.setAdresse(rs.getString("adresse"));
                    return e;
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur getEtudiant", e);
        }
        return null;
    }

    public static Institution getInstitution(String institutionId) {
        String sql = "SELECT * FROM " + MigrationManager.TABLE_INSTITUTIONS
                + " WHERE institution_id = ?";
        try (Connection conn = db().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setString(1, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Institution inst = new Institution();
                    inst.setInstitutionId(rs.getString("institution_id"));
                    inst.setNomInstitution(rs.getString("nom_institution"));
                    inst.setSigleInstitution(rs.getString("sigle_institution"));
                    inst.setAdresseInstitution(rs.getString("adresse_institution"));
                    inst.setLogoInstitution(rs.getString("logo_institution"));
                    inst.setMailPrimaire(rs.getString("mail_primaire"));
                    return inst;
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur getInstitution", e);
        }
        return null;
    }

    // =========================================================
    // SYNCHRONISATION
    // =========================================================
    private static void declencherSyncSiHorsTransaction(Connection conn) {
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