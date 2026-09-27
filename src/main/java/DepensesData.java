import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DepensesData {

    private static final Logger LOGGER = Logger.getLogger(DepensesData.class.getName());
    private static final String TABLE = MigrationManager.TABLE_DEPENSES;

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

    private final String institutionId;
    private final Connection connection;

    // Colonnes sélectionnées (évite SELECT * pour rester stable si la table évolue)
    private static final String COLONNES =
            "id, institution_id, annee_academique, mois, classes, nom_institution, " +
            "nom_depense, type_depense, description_depense, responsable_depense, " +
            "mode_paiement, numero_facture, ninu, matricule, justification_depense, " +
            "montant, montant_total_depenses, date_depense, article, fournisseur, " +
            "motif, executant, source, last_modified";

    public DepensesData(String institutionId) {
        if (institutionId == null || institutionId.isBlank()) {
            throw new IllegalArgumentException("institutionId requis");
        }
        this.institutionId = institutionId;
        this.connection = null;
    }

    public DepensesData(String institutionId, Connection connection) {
        if (institutionId == null || institutionId.isBlank()) {
            throw new IllegalArgumentException("institutionId requis");
        }
        this.institutionId = institutionId;
        this.connection = connection;
    }

    // =========================================================
    // HELPERS
    // =========================================================
    private DatabaseManager db() { return DatabaseManager.getInstance(); }


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
    private String cleComposite(String id) {
        return id + "|" + institutionId;
    }

    // =========================================================
    // CREATE — LOCAL uniquement + outbox
    // =========================================================
    public boolean ajouterDepense(Depenses d) {
        if (d == null) throw new IllegalArgumentException("Depenses null");
        if (d.getId() == null || d.getId().isBlank()) {
            d.setId(UUID.randomUUID().toString());
        }

        Connection conn = getPreferredWriteConnection();
        if (conn == null) {
            LOGGER.severe(() -> "❌ Aucune connexion disponible pour ajouter dépense "
                    + d.getId());
            return false;
        }

        boolean ok = false;
        String usedSource = null;
        try {
            usedSource = getSource(conn);
            ok = ajouterSansSync(conn, d, usedSource);
            if (ok) declencherSyncSiHorsTransaction(conn);
        } catch (SQLException | RuntimeException e) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec ajout dépense sur " + srcErr
                            + " : id=" + d.getId());
        } finally {
            closeIfNeeded(conn);
        }

        if (ok) {
            enregistrerDansOutbox(d, "INSERT");

            final String src = usedSource;
            LOGGER.info(() -> "✅ Dépense ajoutée sur " + src
                    + " + outbox : id=" + d.getId());
        }
        return ok;
    }

    /** Overload historique — construit un Depenses minimal. */
    public boolean ajouterDepense(String executant, String motif, double montant,
                                    Date date, String article, String fournisseur) {
        Depenses d = new Depenses();
        d.setInstitutionId(institutionId);
        d.setResponsableDepense(executant);
        d.setExecutant(executant);
        d.setMotif(motif);
        d.setMontantDepense(montant);
        d.setDateDepense(date);
        d.setNomDepense(article);
        d.setFournisseur(fournisseur);
        return ajouterDepense(d);
    }

    private boolean ajouterSansSync(Connection conn, Depenses d, String source)
            throws SQLException {
        String sql = "INSERT INTO " + TABLE + " (" +
                "institution_id, id, annee_academique, mois, classes, nom_institution, " +
                "nom_depense, type_depense, description_depense, responsable_depense, " +
                "mode_paiement, numero_facture, ninu, matricule, justification_depense, " +
                "montant, montant_total_depenses, date_depense, article, fournisseur, " +
                "motif, executant, source) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setString(1, institutionId);
            ps.setString(2, d.getId());
            ps.setString(3, d.getAnneeAcademique());
            ps.setString(4, d.getMois());
            ps.setString(5, joinClasses(d.getClasses()));
            ps.setString(6, d.getNomInstitution());
            ps.setString(7, d.getNomDepense());
            ps.setString(8, d.getTypeDepense());
            ps.setString(9, d.getDescriptionDepense());
            ps.setString(10, d.getResponsableDepense());
            ps.setString(11, d.getModePaiement());
            ps.setString(12, d.getNumeroFacture());
            ps.setString(13, d.getNinu());
            ps.setString(14, d.getMatricule());
            ps.setString(15, d.getJustificationDepense());
            ps.setDouble(16, d.getMontantDepense());
            ps.setDouble(17, d.getMontantTotalDepenses());
            ps.setDate(18, d.getDateDepense() != null
                    ? new java.sql.Date(d.getDateDepense().getTime())
                    : new java.sql.Date(System.currentTimeMillis()));
            ps.setString(19, d.getNomDepense());
            ps.setString(20, d.getFournisseur());
            ps.setString(21, d.getMotif());
            ps.setString(22, d.getResponsableDepense());
            ps.setString(23, source != null ? source : getSource(conn));
            return ps.executeUpdate() > 0;
        }
    }

    // =========================================================
    // UPDATE — LOCAL uniquement + outbox
    // =========================================================
    public boolean mettreAJourDepense(Depenses d) {
        if (d == null || d.getId() == null || d.getId().isBlank()) {
            throw new IllegalArgumentException("id requis");
        }

        Connection conn = getPreferredWriteConnection();
        if (conn == null) return false;

        boolean ok = false;
        String usedSource = null;
        try {
            usedSource = getSource(conn);
            ok = mettreAJourSansSync(conn, d, usedSource);
            if (ok) declencherSyncSiHorsTransaction(conn);
        } catch (SQLException | RuntimeException e) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec update dépense sur " + srcErr
                            + " : id=" + d.getId());
        } finally {
            closeIfNeeded(conn);
        }

        if (ok) {
            enregistrerDansOutbox(d, "UPDATE");

            final String src = usedSource;
            LOGGER.info(() -> "✅ Dépense mise à jour sur " + src
                    + " + outbox : id=" + d.getId());
        }
        return ok;
    }

    /** Overload historique — construit un Depenses minimal. */
    public boolean mettreAJourDepense(String id, String executant, String motif,
                                        double montant, Date date,
                                        String article, String fournisseur) {
        Depenses d = new Depenses();
        d.setId(id);
        d.setInstitutionId(institutionId);
        d.setResponsableDepense(executant);
        d.setMotif(motif);
        d.setMontantDepense(montant);
        d.setDateDepense(date);
        d.setNomDepense(article);
        d.setFournisseur(fournisseur);
        return mettreAJourDepense(d);
    }

    private boolean mettreAJourSansSync(Connection conn, Depenses d, String source)
            throws SQLException {
        String sql = "UPDATE " + TABLE + " SET " +
                "annee_academique=?, mois=?, classes=?, nom_institution=?, " +
                "nom_depense=?, type_depense=?, description_depense=?, responsable_depense=?, " +
                "mode_paiement=?, numero_facture=?, ninu=?, matricule=?, justification_depense=?, " +
                "montant=?, montant_total_depenses=?, date_depense=?, article=?, fournisseur=?, " +
                "motif=?, executant=?, source=? " +
                "WHERE id=? AND institution_id=?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setString(1, d.getAnneeAcademique());
            ps.setString(2, d.getMois());
            ps.setString(3, joinClasses(d.getClasses()));
            ps.setString(4, d.getNomInstitution());
            ps.setString(5, d.getNomDepense());
            ps.setString(6, d.getTypeDepense());
            ps.setString(7, d.getDescriptionDepense());
            ps.setString(8, d.getResponsableDepense());
            ps.setString(9, d.getModePaiement());
            ps.setString(10, d.getNumeroFacture());
            ps.setString(11, d.getNinu());
            ps.setString(12, d.getMatricule());
            ps.setString(13, d.getJustificationDepense());
            ps.setDouble(14, d.getMontantDepense());
            ps.setDouble(15, d.getMontantTotalDepenses());
            ps.setDate(16, d.getDateDepense() != null
                    ? new java.sql.Date(d.getDateDepense().getTime())
                    : new java.sql.Date(System.currentTimeMillis()));
            ps.setString(17, d.getNomDepense());
            ps.setString(18, d.getFournisseur());
            ps.setString(19, d.getMotif());
            ps.setString(20, d.getResponsableDepense());
            ps.setString(21, source != null ? source : getSource(conn));
            ps.setString(22, d.getId());
            ps.setString(23, institutionId);
            return ps.executeUpdate() > 0;
        }
    }

    // =========================================================
    // DELETE — LOCAL uniquement + tombstone + outbox
    // =========================================================
    public boolean supprimerDepense(String id) {
        if (id == null || id.isBlank()) return false;

        Connection conn = getPreferredWriteConnection();
        if (conn == null) return false;

        boolean autoCommitOriginal;
        String usedSource ;
        try {
            usedSource = getSource(conn);
            autoCommitOriginal = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try {
                boolean ok = supprimerDansBase(conn, id, usedSource);
                conn.commit();

                if (ok) {
                    enregistrerDeleteDansOutbox(id, usedSource);
                    declencherSyncSiHorsTransaction(conn);
                    LOGGER.info(() -> "✅ Dépense supprimée : id=" + id + " + outbox");
                }
                return ok;

            } catch (SQLException e) {
                try { conn.rollback(); } catch (SQLException ignored) {}
                LOGGER.log(Level.SEVERE, "❌ Erreur suppression dépense id=" + id, e);
                return false;
            } finally {
                try { conn.setAutoCommit(autoCommitOriginal); } catch (SQLException ignored) {}
            }

        } catch (SQLException | RuntimeException e) {
            LOGGER.log(Level.SEVERE, e, () -> "❌ Échec supprimer dépense : " + id);
            return false;
        } finally {
            closeIfNeeded(conn);
        }
    }

    private boolean supprimerDansBase(Connection conn, String id, String source)
            throws SQLException {
        DeleteTracker.enregistrerSuppression(conn, TABLE, cleComposite(id), source);

        String sql = "DELETE FROM " + TABLE
                + " WHERE id = ? AND institution_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setString(1, id);
            ps.setString(2, institutionId);
            return ps.executeUpdate() > 0;
        }
    }

    // =========================================================
    // LECTURES (inchangées — lecture double-base)
    // =========================================================
    public List<Depenses> listerDepenses() throws SQLException {
        Map<String, Depenses> byId = new LinkedHashMap<>();

        try (Connection localConn = db().getLocalConnection()) {
            for (Depenses d : lireDepensesFromConnection(localConn)) {
                if (d.getId() != null) byId.put(d.getId(), d);
            }
        } catch (SQLException e) {
            LOGGER.fine(() -> "LOCAL indisponible pour listerDepenses : " + e.getMessage());
        }

        try (Connection remoteConn = db().getRemoteConnection()) {
            for (Depenses d : lireDepensesFromConnection(remoteConn)) {
                if (d.getId() != null && !byId.containsKey(d.getId())) {
                    byId.put(d.getId(), d);
                }
            }
        } catch (SQLException e) {
            LOGGER.fine(() -> "REMOTE indisponible pour listerDepenses : " + e.getMessage());
        }

        List<Depenses> list = new ArrayList<>(byId.values());
        list.sort((a, b) -> {
            Date da = a.getDateDepense();
            Date dbDate = b.getDateDepense();
            if (da == null && dbDate == null) return 0;
            if (da == null) return 1;
            if (dbDate == null) return -1;
            return dbDate.compareTo(da);
        });
        return list;
    }

    public Depenses lireDepenseParId(String id) throws SQLException {
        if (id == null || id.isBlank()) return null;

        try (Connection localConn = db().getLocalConnection()) {
            Depenses d = lireDepenseParIdFromConnection(localConn, id);
            if (d != null) return d;
        } catch (SQLException e) {
            LOGGER.fine(() -> "LOCAL indisponible : " + e.getMessage());
        }

        try (Connection remoteConn = db().getRemoteConnection()) {
            return lireDepenseParIdFromConnection(remoteConn, id);
        } catch (SQLException e) {
            LOGGER.fine(() -> "REMOTE indisponible : " + e.getMessage());
        }
        return null;
    }

    private Depenses lireDepenseParIdFromConnection(Connection conn, String id)
            throws SQLException {
        String sql = "SELECT " + COLONNES + " FROM " + TABLE
                + " WHERE institution_id = ? AND id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setString(1, institutionId);
            ps.setString(2, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapDepense(rs);
            }
        }
        return null;
    }

    private List<Depenses> lireDepensesFromConnection(Connection conn) throws SQLException {
        List<Depenses> list = new ArrayList<>();
        String sql = "SELECT " + COLONNES + " FROM " + TABLE
                + " WHERE institution_id = ? ORDER BY date_depense DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setString(1, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapDepense(rs));
            }
        }
        return list;
    }

    // =========================================================
    // TOTAL
    // =========================================================
    public double getTotalDepenses() throws SQLException {
        double total = 0.0;
        boolean localOk = false;
        try (Connection localConn = db().getLocalConnection()) {
            total += getTotalDepensesFromConnection(localConn);
            localOk = true;
        } catch (SQLException e) {
            LOGGER.fine(() -> "LOCAL indisponible pour total : " + e.getMessage());
        }
        if (!localOk) {
            try (Connection remoteConn = db().getRemoteConnection()) {
                total += getTotalDepensesFromConnection(remoteConn);
            } catch (SQLException e) {
                LOGGER.fine(() -> "REMOTE indisponible pour total : " + e.getMessage());
            }
        }
        return total;
    }

    private double getTotalDepensesFromConnection(Connection conn) throws SQLException {
        String sql = "SELECT SUM(montant) FROM " + TABLE + " WHERE institution_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setString(1, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0.0;
            }
        }
    }

    // =========================================================
    // OUTBOX
    // =========================================================
    private void enregistrerDansOutbox(Depenses d, String operation) {
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
            ps.setString(3, cleComposite(d.getId()));
            ps.setString(4, toJson(d));
            ps.executeUpdate();
            LOGGER.fine(() -> "📥 Outbox " + operation + " : id=" + d.getId());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Impossible d'enregistrer dans l'outbox : id=" + d.getId());
        }
    }

    private void enregistrerDeleteDansOutbox(String id, String source) {
        String sql = """
            INSERT INTO sync_outbox
              (table_name, operation, primary_key, row_data, target_source, statut)
            VALUES (?, 'DELETE', ?, ?, 'REMOTE', 'PENDING')
        """;
        try (Connection conn = db().getLocalConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(OUTBOX_TIMEOUT_SECONDS);
            ps.setString(1, TABLE);
            ps.setString(2, cleComposite(id));
            ps.setString(3, "{\"id\":\"" + escapeJson(id) + "\","
                    + "\"institutionId\":\"" + escapeJson(institutionId) + "\","
                    + "\"source\":\"" + escapeJson(source) + "\"}");
            ps.executeUpdate();
            LOGGER.fine(() -> "📥 Outbox DELETE : id=" + id);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Impossible d'enregistrer le DELETE dans l'outbox");
        }
    }

    private String toJson(Depenses d) {
        try {
            return GSON.toJson(d);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, e,
                    () -> "Impossible de sérialiser la dépense en JSON");
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
    private Depenses mapDepense(ResultSet rs) throws SQLException {
        Depenses d = new Depenses();
        d.setId(rs.getString("id"));
        d.setInstitutionId(rs.getString("institution_id"));
        d.setAnneeAcademique(rs.getString("annee_academique"));
        d.setMois(rs.getString("mois"));
        d.setClasses(splitClasses(rs.getString("classes")));
        d.setNomInstitution(rs.getString("nom_institution"));
        d.setNomDepense(rs.getString("nom_depense"));
        d.setTypeDepense(rs.getString("type_depense"));
        d.setDescriptionDepense(rs.getString("description_depense"));
        d.setResponsableDepense(rs.getString("responsable_depense"));
        d.setModePaiement(rs.getString("mode_paiement"));
        d.setNumeroFacture(rs.getString("numero_facture"));
        d.setNinu(rs.getString("ninu"));
        d.setMatricule(rs.getString("matricule"));
        d.setJustificationDepense(rs.getString("justification_depense"));
        d.setMontantDepense(rs.getDouble("montant"));
        d.setMontantTotalDepenses(rs.getDouble("montant_total_depenses"));
        d.setDateDepense(rs.getDate("date_depense"));
        d.setFournisseur(rs.getString("fournisseur"));
        d.setMotif(rs.getString("motif"));
        d.setSource(rs.getString("source"));

        try {
            java.sql.Timestamp ts = rs.getTimestamp("last_modified");
            if (ts != null) d.setLastModified(ts.toLocalDateTime());
        } catch (SQLException ignored) {}

        return d;
    }

    // =========================================================
    // UTILITAIRES
    // =========================================================
    private static String joinClasses(List<String> classes) {
        if (classes == null || classes.isEmpty()) return null;
        return String.join(",", classes);
    }

    private static List<String> splitClasses(String csv) {
        if (csv == null || csv.isBlank()) return new ArrayList<>();
        String[] parts = csv.split(",");
        List<String> list = new ArrayList<>(parts.length);
        for (String p : parts) {
            String t = p.trim();
            if (!t.isEmpty()) list.add(t);
        }
        return list;
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