import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GestionFinanciereData {

    private static final Logger LOGGER = Logger.getLogger(GestionFinanciereData.class.getName());

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

    public GestionFinanciereData(String institutionId) {
        if (institutionId == null || institutionId.isBlank()) {
            throw new IllegalArgumentException("institutionId requis");
        }
        this.institutionId = institutionId;
        this.connection = null;
    }

    public GestionFinanciereData(String institutionId, Connection connection) {
        if (institutionId == null || institutionId.isBlank()) {
            throw new IllegalArgumentException("institutionId requis");
        }
        this.institutionId = institutionId;
        this.connection = connection;
    }

    // =========================================================
    // CONNEXION
    // =========================================================
    private DatabaseManager db() { return DatabaseManager.getInstance(); }

    private Connection getConnection() throws SQLException {
        if (connection != null && !connection.isClosed()) return connection;
        return db().getConnection();
    }

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

    private static String cleComposite(String id, String institutionId) {
        return institutionId + "|" + id;
    }

    public String getAnneeAcademiqueCourante() {
        int an = Calendar.getInstance().get(Calendar.YEAR);
        int mois = Calendar.getInstance().get(Calendar.MONTH);
        return (mois < 6) ? (an - 1) + "-" + an : an + "-" + (an + 1);
    }

    // =========================================================
    // ANNÉES / CLASSES / MOIS
    // =========================================================
    public List<String> getAnneesAcademiques() throws SQLException {
        List<String> annees = new ArrayList<>();
        String sql = "SELECT DISTINCT annee_academique FROM " + MigrationManager.TABLE_ANNEES_ACADEMIQUES
                + " WHERE institution_id = ? ORDER BY annee_academique DESC";
        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) annees.add(rs.getString("annee_academique"));
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return annees;
    }

    public List<String> getClasses() throws SQLException {
        List<String> classes = new ArrayList<>();
        String sql = "SELECT DISTINCT nom_classe FROM " + MigrationManager.TABLE_CLASSES
                + " WHERE institution_id = ? ORDER BY nom_classe";
        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) classes.add(rs.getString("nom_classe"));
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        if (classes.isEmpty()) classes.add("Aucune classe");
        return classes;
    }

    public List<String> getClassesUtilisees(String table, String annee, String mois)
            throws SQLException {
        List<String> classes = new ArrayList<>();
        if (!tableExists(table) || !columnExists(table, "classes")) return classes;

        StringBuilder sql = new StringBuilder(
                "SELECT DISTINCT classes FROM " + table
                + " WHERE institution_id = ? AND classes IS NOT NULL");
        List<Object> args = new ArrayList<>();
        args.add(institutionId);

        Connection conn = null;
        try {
            conn = getConnection();
            if (annee != null && !annee.isBlank() && columnExists(table, "annee_academique")) {
                sql.append(" AND annee_academique = ?"); args.add(annee);
            }
            if (mois != null && !mois.isBlank() && columnExists(table, "mois")) {
                sql.append(" AND mois = ?"); args.add(mois);
            }

            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                for (int i = 0; i < args.size(); i++) ps.setObject(i + 1, args.get(i));
                try (ResultSet rs = ps.executeQuery()) {
                    java.util.Set<String> set = new java.util.TreeSet<>();
                    while (rs.next()) {
                        String csv = rs.getString("classes");
                        if (csv == null) continue;
                        for (String c : csv.split(",")) {
                            String t = c.trim();
                            if (!t.isEmpty()) set.add(t);
                        }
                    }
                    classes.addAll(set);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.FINE, "Erreur getClassesUtilisees sur " + table, e);
        } finally {
            closeIfNeeded(conn);
        }
        return classes;
    }

    public List<String> getMoisDisponibles(String table, String annee, String classe)
            throws SQLException {
        List<String> mois = new ArrayList<>();
        if (!tableExists(table)) return mois;

        String colMois = columnExists(table, "mois") ? "mois" : null;
        String colDate = columnDateForTable(table);
        String colAnnee = columnExists(table, "annee_academique") ? "annee_academique" : null;

        if (colMois == null && colDate == null) return mois;

        StringBuilder sql = new StringBuilder("SELECT DISTINCT ");
        if (colMois != null) {
            sql.append(colMois).append(" AS mois");
        } else {
            sql.append("DATE_FORMAT(").append(colDate).append(", '%Y-%m') AS mois");
        }
        sql.append(" FROM ").append(table).append(" WHERE institution_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(institutionId);

        Connection conn = null;
        try {
            conn = getConnection();
            if (annee != null && !annee.isBlank() && colAnnee != null) {
                sql.append(" AND ").append(colAnnee).append(" = ?");
                args.add(annee);
            }
            if (classe != null && !classe.isBlank() && columnExists(table, "classes")) {
                sql.append(" AND FIND_IN_SET(?, REPLACE(classes, ' ', '')) > 0");
                args.add(classe);
            }
            sql.append(" ORDER BY mois DESC");

            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                for (int i = 0; i < args.size(); i++) ps.setObject(i + 1, args.get(i));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String m = rs.getString("mois");
                        if (m != null) mois.add(m);
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.FINE, "Erreur getMoisDisponibles sur " + table, e);
        } finally {
            closeIfNeeded(conn);
        }
        return mois;
    }

    // =========================================================
    // FRAIS SCOLAIRES
    // =========================================================
    public double getFraisScolaire(String classe, String anneeAcademique) throws SQLException {
        String sql = "SELECT montant_total FROM " + MigrationManager.TABLE_FRAIS_SCOLAIRES
                + " WHERE institution_id = ? AND classe = ? AND annee_academique = ?";
        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                ps.setString(2, classe);
                ps.setString(3, anneeAcademique);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getDouble("montant_total") : 0.0;
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
    }

    public boolean setFraisScolaire(String classe, String anneeAcademique, double montantTotal) {
        Connection conn = getPreferredWriteConnection();
        if (conn == null) return false;

        boolean ok = false;
        String usedSource = null;
        try {
            usedSource = getSource(conn);
            ok = setFraisScolaireSansSync(conn, classe, anneeAcademique, montantTotal, usedSource);
            if (ok) declencherSyncSiHorsTransaction(conn);
        } catch (SQLException | RuntimeException e) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec setFraisScolaire sur " + srcErr + " : " + classe);
        } finally {
            closeIfNeeded(conn);
        }

        if (ok) {
            String pk = institutionId + "|" + classe + "|" + anneeAcademique;
            Map<String, Object> payload = new HashMap<>();
            payload.put("institutionId", institutionId);
            payload.put("classe", classe);
            payload.put("anneeAcademique", anneeAcademique);
            payload.put("montantTotal", montantTotal);
            enregistrerDansOutbox(MigrationManager.TABLE_FRAIS_SCOLAIRES,
                    pk, GSON.toJson(payload), "UPSERT");
        }
        return ok;
    }

    private boolean setFraisScolaireSansSync(Connection conn, String classe,
                                                String anneeAcademique,
                                                double montantTotal, String source)
            throws SQLException {
        String checkSql = "SELECT COUNT(*) FROM " + MigrationManager.TABLE_FRAIS_SCOLAIRES
                + " WHERE institution_id = ? AND classe = ? AND annee_academique = ?";
        boolean exists;
        try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setString(1, institutionId);
            ps.setString(2, classe);
            ps.setString(3, anneeAcademique);
            try (ResultSet rs = ps.executeQuery()) {
                exists = rs.next() && rs.getInt(1) > 0;
            }
        }
        if (exists) {
            String updateSql = "UPDATE " + MigrationManager.TABLE_FRAIS_SCOLAIRES
                    + " SET montant_total = ?, source = ? "
                    + "WHERE institution_id = ? AND classe = ? AND annee_academique = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setDouble(1, montantTotal);
                ps.setString(2, source);
                ps.setString(3, institutionId);
                ps.setString(4, classe);
                ps.setString(5, anneeAcademique);
                return ps.executeUpdate() > 0;
            }
        } else {
            String insertSql = "INSERT INTO " + MigrationManager.TABLE_FRAIS_SCOLAIRES
                    + " (institution_id, id, classe, annee_academique, montant_total, source) "
                    + "VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                ps.setString(2, UUID.randomUUID().toString());
                ps.setString(3, classe);
                ps.setString(4, anneeAcademique);
                ps.setDouble(5, montantTotal);
                ps.setString(6, source);
                return ps.executeUpdate() > 0;
            }
        }
    }

    // =========================================================
    // MODALITÉS
    // =========================================================
    public Map<Integer, Double> getModalites(String classe, String anneeAcademique)
            throws SQLException {
        Map<Integer, Double> map = new TreeMap<>();
        String sql = "SELECT versement_num, montant FROM " + MigrationManager.TABLE_MODALITES_PAIEMENT
                + " WHERE institution_id = ? AND classe = ? AND annee_academique = ? "
                + "ORDER BY versement_num";
        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                ps.setString(2, classe);
                ps.setString(3, anneeAcademique);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        map.put(rs.getInt("versement_num"), rs.getDouble("montant"));
                    }
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return map;
    }

    public boolean saveModalites(String classe, String anneeAcademique,
                                   Map<Integer, Double> modalites) {
        Connection conn = getPreferredWriteConnection();
        if (conn == null) return false;

        boolean autoCommitOriginal;
        String usedSource = null;
        try {
            usedSource = getSource(conn);
            autoCommitOriginal = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try {
                // ① Tombstones pour les anciennes modalités
                String selectSql = "SELECT id FROM " + MigrationManager.TABLE_MODALITES_PAIEMENT
                        + " WHERE institution_id = ? AND classe = ? AND annee_academique = ?";
                List<String> anciensIds = new ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                    ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                    ps.setString(1, institutionId);
                    ps.setString(2, classe);
                    ps.setString(3, anneeAcademique);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) anciensIds.add(rs.getString("id"));
                    }
                }
                for (String ancienId : anciensIds) {
                    DeleteTracker.enregistrerSuppression(conn,
                            MigrationManager.TABLE_MODALITES_PAIEMENT,
                            cleComposite(ancienId, institutionId), usedSource);
                }

                // ② DELETE
                String deleteSql = "DELETE FROM " + MigrationManager.TABLE_MODALITES_PAIEMENT
                        + " WHERE institution_id = ? AND classe = ? AND annee_academique = ?";
                try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                    ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                    ps.setString(1, institutionId);
                    ps.setString(2, classe);
                    ps.setString(3, anneeAcademique);
                    ps.executeUpdate();
                }

                // ③ INSERT
                String insertSql = "INSERT INTO " + MigrationManager.TABLE_MODALITES_PAIEMENT
                        + " (institution_id, id, classe, annee_academique, versement_num, montant, source) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                    for (Map.Entry<Integer, Double> entry : modalites.entrySet()) {
                        ps.setString(1, institutionId);
                        ps.setString(2, UUID.randomUUID().toString());
                        ps.setString(3, classe);
                        ps.setString(4, anneeAcademique);
                        ps.setInt(5, entry.getKey());
                        ps.setDouble(6, entry.getValue());
                        ps.setString(7, usedSource);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                conn.commit();

            } catch (SQLException e) {
                try { conn.rollback(); } catch (SQLException ignored) {}
                LOGGER.log(Level.SEVERE, "Erreur saveModalites", e);
                return false;
            } finally {
                try { conn.setAutoCommit(autoCommitOriginal); } catch (SQLException ignored) {}
            }

        } catch (SQLException | RuntimeException e) {
            LOGGER.log(Level.SEVERE, e, () -> "❌ Échec saveModalites");
            return false;
        } finally {
            closeIfNeeded(conn);
        }

        String pk = institutionId + "|" + classe + "|" + anneeAcademique;
        enregistrerDansOutbox(MigrationManager.TABLE_MODALITES_PAIEMENT,
                pk, "{\"classe\":\"" + escapeJson(classe) + "\","
                        + "\"annee\":\"" + escapeJson(anneeAcademique) + "\"}",
                "REPLACE");
        declencherSyncSiHorsTransaction(conn);

        final String src = usedSource;
        LOGGER.info(() -> "✅ Modalités enregistrées sur " + src + " + outbox : " + classe);
        return true;
    }

    // =========================================================
    // PAIEMENTS ÉTUDIANTS
    // =========================================================
    public List<Map<String, Object>> getPaiementsEtudiants(String anneeAcademique)
            throws SQLException {
        return getPaiementsEtudiants(anneeAcademique, null, null);
    }

    public List<Map<String, Object>> getPaiementsEtudiants(String annee, String mois, String classe)
            throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT p.id, p.numero_identifiant, e.nom, e.prenom, e.classe AS etudiant_classe, "
                + "p.montant, p.date_paiement, p.moyen_paiement, p.reference "
                + "FROM " + MigrationManager.TABLE_PAIEMENTS_ETUDIANTS + " p "
                + "JOIN " + MigrationManager.TABLE_ETUDIANTS + " e "
                + "  ON p.institution_id = e.institution_id "
                + " AND p.numero_identifiant = e.numero_identifiant "
                + "WHERE p.institution_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(institutionId);

        Connection conn = null;
        try {
            conn = getConnection();
            if (annee != null && !annee.isBlank()) {
                sql.append(" AND p.annee_academique = ?"); args.add(annee);
            }
            if (mois != null && !mois.isBlank()) {
                sql.append(" AND DATE_FORMAT(p.date_paiement, '%Y-%m') = ?"); args.add(mois);
            }
            if (classe != null && !classe.isBlank()) {
                sql.append(" AND e.classe = ?"); args.add(classe);
            }
            sql.append(" ORDER BY p.date_paiement DESC");

            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                for (int i = 0; i < args.size(); i++) ps.setObject(i + 1, args.get(i));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new HashMap<>();
                        row.put("id", rs.getString("id"));
                        row.put("numero_identifiant", rs.getString("numero_identifiant"));
                        row.put("nom", rs.getString("nom"));
                        row.put("prenom", rs.getString("prenom"));
                        row.put("classe", rs.getString("etudiant_classe"));
                        row.put("montant", rs.getDouble("montant"));
                        row.put("date_paiement", rs.getTimestamp("date_paiement"));
                        row.put("moyen_paiement", rs.getString("moyen_paiement"));
                        row.put("reference", rs.getString("reference"));
                        list.add(row);
                    }
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return list;
    }

    public boolean enregistrerPaiementEtudiant(String etudiantId, double montant, String moyen,
                                                 String reference, String anneeAcademique) {
        Connection conn = getPreferredWriteConnection();
        if (conn == null) return false;

        boolean ok = false;
        String usedSource = null;
        String id = UUID.randomUUID().toString();
        try {
            usedSource = getSource(conn);
            String sql = "INSERT INTO " + MigrationManager.TABLE_PAIEMENTS_ETUDIANTS
                    + " (institution_id, id, numero_identifiant, montant, moyen_paiement, reference, "
                    + "annee_academique, date_paiement, statut, source) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), 'VALIDÉ', ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                ps.setString(2, id);
                ps.setString(3, etudiantId);
                ps.setDouble(4, montant);
                ps.setString(5, moyen);
                ps.setString(6, reference);
                ps.setString(7, anneeAcademique);
                ps.setString(8, usedSource);
                ok = ps.executeUpdate() > 0;
            }
            if (ok) declencherSyncSiHorsTransaction(conn);
        } catch (SQLException | RuntimeException e) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec enregistrerPaiementEtudiant sur " + srcErr);
        } finally {
            closeIfNeeded(conn);
        }

        if (ok) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("etudiantId", etudiantId);
            payload.put("montant", montant);
            payload.put("moyen", moyen);
            payload.put("reference", reference);
            payload.put("annee", anneeAcademique);
            enregistrerDansOutbox(MigrationManager.TABLE_PAIEMENTS_ETUDIANTS,
                    cleComposite(id, institutionId),
                    GSON.toJson(payload), "INSERT");
        }
        return ok;
    }

    public boolean supprimerPaiementEtudiant(String paiementId) {
        Connection conn = getPreferredWriteConnection();
        if (conn == null) return false;

        boolean ok = false;
        String usedSource = null;
        try {
            usedSource = getSource(conn);
            ok = supprimerDansBase(conn, MigrationManager.TABLE_PAIEMENTS_ETUDIANTS,
                    paiementId, usedSource);
            if (ok) declencherSyncSiHorsTransaction(conn);
        } catch (SQLException | RuntimeException e) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec supprimerPaiementEtudiant sur " + srcErr);
        } finally {
            closeIfNeeded(conn);
        }

        if (ok) {
            enregistrerDeleteDansOutbox(MigrationManager.TABLE_PAIEMENTS_ETUDIANTS,
                    paiementId, usedSource);
        }
        return ok;
    }

    public double getTotalPaiementsEtudiants(String anneeAcademique) throws SQLException {
        String sql = "SELECT SUM(montant) FROM " + MigrationManager.TABLE_PAIEMENTS_ETUDIANTS
                + " WHERE institution_id = ? AND annee_academique = ? AND statut = 'VALIDÉ'";
        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                ps.setString(2, anneeAcademique);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getDouble(1) : 0.0;
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
    }

    // =========================================================
    // RECETTES EXTERNES
    // =========================================================
    public List<Map<String, Object>> getRecettesExternes() throws SQLException {
        return getRecettesExternes(null, null);
    }

    public List<Map<String, Object>> getRecettesExternes(String annee, String mois)
            throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT id, categorie, date_recette, montant, donateur, receveur, motif "
                + "FROM " + MigrationManager.TABLE_RECETTES_EXTERNES
                + " WHERE institution_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(institutionId);

        Connection conn = null;
        try {
            conn = getConnection();
            if (annee != null && !annee.isBlank()
                    && columnExists("recettes_externes", "annee_academique")) {
                sql.append(" AND annee_academique = ?"); args.add(annee);
            }
            if (mois != null && !mois.isBlank()) {
                sql.append(" AND DATE_FORMAT(date_recette, '%Y-%m') = ?"); args.add(mois);
            }
            sql.append(" ORDER BY date_recette DESC");

            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                for (int i = 0; i < args.size(); i++) ps.setObject(i + 1, args.get(i));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new HashMap<>();
                        row.put("id", rs.getString("id"));
                        row.put("categorie", rs.getString("categorie"));
                        row.put("date_recette", rs.getDate("date_recette"));
                        row.put("montant", rs.getDouble("montant"));
                        row.put("donateur", rs.getString("donateur"));
                        row.put("receveur", rs.getString("receveur"));
                        row.put("motif", rs.getString("motif"));
                        list.add(row);
                    }
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return list;
    }

    public boolean ajouterRecetteExterne(String categorie, Date date, double montant,
                                           String donateur, String receveur, String motif) {
        Connection conn = getPreferredWriteConnection();
        if (conn == null) return false;

        boolean ok = false;
        String usedSource = null;
        String id = UUID.randomUUID().toString();
        try {
            usedSource = getSource(conn);
            String sql = "INSERT INTO " + MigrationManager.TABLE_RECETTES_EXTERNES
                    + " (institution_id, id, categorie, date_recette, montant, donateur, receveur, motif, source) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                ps.setString(2, id);
                ps.setString(3, categorie);
                ps.setDate(4, date != null ? new java.sql.Date(date.getTime()) : null);
                ps.setDouble(5, montant);
                ps.setString(6, donateur);
                ps.setString(7, receveur);
                ps.setString(8, motif);
                ps.setString(9, usedSource);
                ok = ps.executeUpdate() > 0;
            }
            if (ok) declencherSyncSiHorsTransaction(conn);
        } catch (SQLException | RuntimeException e) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec ajouterRecetteExterne sur " + srcErr);
        } finally {
            closeIfNeeded(conn);
        }

        if (ok) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("categorie", categorie);
            payload.put("date", date);
            payload.put("montant", montant);
            payload.put("donateur", donateur);
            payload.put("receveur", receveur);
            enregistrerDansOutbox(MigrationManager.TABLE_RECETTES_EXTERNES,
                    cleComposite(id, institutionId),
                    GSON.toJson(payload), "INSERT");
        }
        return ok;
    }

    public boolean supprimerRecetteExterne(String id) {
        Connection conn = getPreferredWriteConnection();
        if (conn == null) return false;

        boolean ok = false;
        String usedSource = null;
        try {
            usedSource = getSource(conn);
            ok = supprimerDansBase(conn, MigrationManager.TABLE_RECETTES_EXTERNES,
                    id, usedSource);
            if (ok) declencherSyncSiHorsTransaction(conn);
        } catch (SQLException | RuntimeException e) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec supprimerRecetteExterne sur " + srcErr);
        } finally {
            closeIfNeeded(conn);
        }

        if (ok) {
            enregistrerDeleteDansOutbox(MigrationManager.TABLE_RECETTES_EXTERNES,
                    id, usedSource);
        }
        return ok;
    }

    public double getTotalRecettesExternes() throws SQLException {
        return getTotalRecettesExternes(null, null);
    }

    public double getTotalRecettesExternes(String annee, String mois) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT SUM(montant) FROM " + MigrationManager.TABLE_RECETTES_EXTERNES
                + " WHERE institution_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(institutionId);

        Connection conn = null;
        try {
            conn = getConnection();
            if (annee != null && !annee.isBlank()
                    && columnExists("recettes_externes", "annee_academique")) {
                sql.append(" AND annee_academique = ?"); args.add(annee);
            }
            if (mois != null && !mois.isBlank()) {
                sql.append(" AND DATE_FORMAT(date_recette, '%Y-%m') = ?"); args.add(mois);
            }
            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                for (int i = 0; i < args.size(); i++) ps.setObject(i + 1, args.get(i));
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getDouble(1) : 0.0;
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
    }

    // =========================================================
    // DÉPENSES
    // =========================================================
    public List<Depenses> getDepenses() throws SQLException {
        return getDepenses(null, null, null);
    }

    public List<Depenses> getDepenses(String annee, String mois, String classe)
            throws SQLException {
        List<Depenses> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT id, institution_id, annee_academique, mois, classes, " +
                "       nom_depense, type_depense, description_depense, responsable_depense, " +
                "       mode_paiement, numero_facture, ninu, matricule, justification_depense, " +
                "       montant, montant_total_depenses, date_depense, article, fournisseur, " +
                "       motif, executant, source " +
                "FROM " + MigrationManager.TABLE_DEPENSES + " WHERE institution_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(institutionId);

        Connection conn = null;
        try {
            conn = getConnection();
            if (annee != null && !annee.isBlank() && columnExists("depenses", "annee_academique")) {
                sql.append(" AND annee_academique = ?"); args.add(annee);
            }
            if (mois != null && !mois.isBlank() && columnExists("depenses", "mois")) {
                sql.append(" AND mois = ?"); args.add(mois);
            } else if (mois != null && !mois.isBlank()) {
                sql.append(" AND DATE_FORMAT(date_depense, '%Y-%m') = ?"); args.add(mois);
            }
            if (classe != null && !classe.isBlank() && columnExists("depenses", "classes")) {
                sql.append(" AND FIND_IN_SET(?, REPLACE(classes, ' ', '')) > 0");
                args.add(classe);
            }
            sql.append(" ORDER BY date_depense DESC");

            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                for (int i = 0; i < args.size(); i++) ps.setObject(i + 1, args.get(i));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) list.add(mapDepense(rs));
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return list;
    }

    private Depenses mapDepense(ResultSet rs) throws SQLException {
        Depenses d = new Depenses();
        d.setId(rs.getString("id"));
        d.setInstitutionId(rs.getString("institution_id"));
        d.setAnneeAcademique(getSafe(rs, "annee_academique"));
        d.setMois(getSafe(rs, "mois"));
        d.setClasses(splitCsv(getSafe(rs, "classes")));
        d.setNomDepense(getSafe(rs, "nom_depense"));
        d.setTypeDepense(getSafe(rs, "type_depense"));
        d.setDescriptionDepense(getSafe(rs, "description_depense"));
        d.setResponsableDepense(firstNonNull(
                getSafe(rs, "responsable_depense"), getSafe(rs, "executant")));
        d.setModePaiement(getSafe(rs, "mode_paiement"));
        d.setNumeroFacture(getSafe(rs, "numero_facture"));
        d.setNinu(getSafe(rs, "ninu"));
        d.setMatricule(getSafe(rs, "matricule"));
        d.setJustificationDepense(getSafe(rs, "justification_depense"));
        d.setMontantDepense(rs.getDouble("montant"));
        try { d.setMontantTotalDepenses(rs.getDouble("montant_total_depenses")); }
        catch (SQLException ignored) {}
        d.setDateDepense(rs.getDate("date_depense"));
        d.setFournisseur(getSafe(rs, "fournisseur"));
        d.setMotif(getSafe(rs, "motif"));
        d.setSource(getSafe(rs, "source"));
        return d;
    }

    public boolean ajouterDepense(Depenses d) {
        if (d == null) throw new IllegalArgumentException("Depenses null");
        if (d.getId() == null || d.getId().isBlank()) {
            d.setId(UUID.randomUUID().toString());
        }

        Connection conn = getPreferredWriteConnection();
        if (conn == null) return false;

        boolean ok = false;
        String usedSource = null;
        try {
            usedSource = getSource(conn);
            String sql = "INSERT INTO " + MigrationManager.TABLE_DEPENSES
                    + " (institution_id, id, annee_academique, mois, classes, " +
                    "  nom_depense, type_depense, description_depense, responsable_depense, " +
                    "  mode_paiement, numero_facture, ninu, matricule, justification_depense, " +
                    "  montant, montant_total_depenses, date_depense, article, fournisseur, " +
                    "  motif, executant, source) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                ps.setString(2, d.getId());
                ps.setString(3, d.getAnneeAcademique());
                ps.setString(4, d.getMois());
                ps.setString(5, joinCsv(d.getClasses()));
                ps.setString(6, d.getNomDepense());
                ps.setString(7, d.getTypeDepense());
                ps.setString(8, d.getDescriptionDepense());
                ps.setString(9, d.getResponsableDepense());
                ps.setString(10, d.getModePaiement());
                ps.setString(11, d.getNumeroFacture());
                ps.setString(12, d.getNinu());
                ps.setString(13, d.getMatricule());
                ps.setString(14, d.getJustificationDepense());
                ps.setDouble(15, d.getMontantDepense());
                ps.setDouble(16, d.getMontantTotalDepenses());
                ps.setDate(17, d.getDateDepense() != null
                        ? new java.sql.Date(d.getDateDepense().getTime()) : null);
                ps.setString(18, d.getNomDepense());
                ps.setString(19, d.getFournisseur());
                ps.setString(20, d.getMotif());
                ps.setString(21, d.getResponsableDepense());
                ps.setString(22, usedSource);
                ok = ps.executeUpdate() > 0;
            }
            if (ok) declencherSyncSiHorsTransaction(conn);
        } catch (SQLException | RuntimeException e) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, e, () -> "❌ Échec ajouterDepense sur " + srcErr);
        } finally {
            closeIfNeeded(conn);
        }

        if (ok) {
            enregistrerDansOutbox(MigrationManager.TABLE_DEPENSES,
                    cleComposite(d.getId(), institutionId),
                    GSON.toJson(d), "INSERT");
        }
        return ok;
    }

    public boolean ajouterDepense(String executant, String motif, double montant, Date date,
                                    String article, String fournisseur) {
        Depenses d = new Depenses();
        d.setInstitutionId(institutionId);
        d.setResponsableDepense(executant);
        d.setMotif(motif);
        d.setMontantDepense(montant);
        d.setDateDepense(date);
        d.setNomDepense(article);
        d.setFournisseur(fournisseur);
        return ajouterDepense(d);
    }

    public boolean supprimerDepense(String id) {
        Connection conn = getPreferredWriteConnection();
        if (conn == null) return false;

        boolean ok = false;
        String usedSource = null;
        try {
            usedSource = getSource(conn);
            ok = supprimerDansBase(conn, MigrationManager.TABLE_DEPENSES, id, usedSource);
            if (ok) declencherSyncSiHorsTransaction(conn);
        } catch (SQLException | RuntimeException e) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, e, () -> "❌ Échec supprimerDepense sur " + srcErr);
        } finally {
            closeIfNeeded(conn);
        }

        if (ok) {
            enregistrerDeleteDansOutbox(MigrationManager.TABLE_DEPENSES, id, usedSource);
        }
        return ok;
    }

    public double getTotalDepenses() throws SQLException {
        return getTotalDepenses(null, null);
    }

    public double getTotalDepenses(String annee, String mois) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT SUM(montant) FROM " + MigrationManager.TABLE_DEPENSES
                + " WHERE institution_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(institutionId);

        Connection conn = null;
        try {
            conn = getConnection();
            if (annee != null && !annee.isBlank()
                    && columnExists("depenses", "annee_academique")) {
                sql.append(" AND annee_academique = ?"); args.add(annee);
            }
            if (mois != null && !mois.isBlank()) {
                sql.append(" AND DATE_FORMAT(date_depense, '%Y-%m') = ?"); args.add(mois);
            }
            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                for (int i = 0; i < args.size(); i++) ps.setObject(i + 1, args.get(i));
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getDouble(1) : 0.0;
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
    }

    // =========================================================
    // EMPLOYÉS
    // =========================================================
    public List<Employe> getEmployes() throws SQLException {
        return getEmployes(null, null, null);
    }

    public List<Employe> getEmployes(String annee, String mois, String classe)
            throws SQLException {
        List<Employe> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT id, institution_id, annee_academique, mois, classes, " +
                "       nom, poste, salaire_base, deductions, source " +
                "FROM " + MigrationManager.TABLE_EMPLOYES + " WHERE institution_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(institutionId);

        Connection conn = null;
        try {
            conn = getConnection();
            if (annee != null && !annee.isBlank() && columnExists("employes", "annee_academique")) {
                sql.append(" AND annee_academique = ?"); args.add(annee);
            }
            if (mois != null && !mois.isBlank() && columnExists("employes", "mois")) {
                sql.append(" AND mois = ?"); args.add(mois);
            }
            if (classe != null && !classe.isBlank() && columnExists("employes", "classes")) {
                sql.append(" AND FIND_IN_SET(?, REPLACE(classes, ' ', '')) > 0");
                args.add(classe);
            }
            sql.append(" ORDER BY nom");

            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                for (int i = 0; i < args.size(); i++) ps.setObject(i + 1, args.get(i));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Employe e = new Employe();
                        e.setId(rs.getString("id"));
                        e.setInstitutionId(rs.getString("institution_id"));
                        e.setAnneeAcademique(getSafe(rs, "annee_academique"));
                        e.setMois(getSafe(rs, "mois"));
                        e.setClasses(splitCsv(getSafe(rs, "classes")));
                        e.setNom(rs.getString("nom"));
                        e.setPoste(rs.getString("poste"));
                        e.setSalaireBase(rs.getDouble("salaire_base"));
                        e.setDeductions(rs.getDouble("deductions"));
                        try { e.setSource(rs.getString("source")); } catch (SQLException ignored) {}
                        list.add(e);
                    }
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return list;
    }

    public boolean ajouterEmploye(Employe e) {
        if (e == null) throw new IllegalArgumentException("Employe null");

        Connection conn = getPreferredWriteConnection();
        if (conn == null) return false;

        boolean ok = false;
        String usedSource = null;
        String id = UUID.randomUUID().toString();
        try {
            usedSource = getSource(conn);
            String sql = "INSERT INTO " + MigrationManager.TABLE_EMPLOYES
                    + " (institution_id, id, employe_id, annee_academique, mois, classes, "
                    + "  nom, poste, salaire_base, deductions, source) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                ps.setString(2, id);
                ps.setInt(3, 0);
                ps.setString(4, e.getAnneeAcademique());
                ps.setString(5, e.getMois());
                ps.setString(6, joinCsv(e.getClasses()));
                ps.setString(7, e.getNom());
                ps.setString(8, e.getPoste());
                ps.setDouble(9, e.getSalaireBase());
                ps.setDouble(10, e.getDeductions());
                ps.setString(11, usedSource);
                ok = ps.executeUpdate() > 0;
            }
            if (ok) declencherSyncSiHorsTransaction(conn);
        } catch (SQLException | RuntimeException ex) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, ex, () -> "❌ Échec ajouterEmploye sur " + srcErr);
        } finally {
            closeIfNeeded(conn);
        }

        if (ok) {
            enregistrerDansOutbox(MigrationManager.TABLE_EMPLOYES,
                    cleComposite(id, institutionId), GSON.toJson(e), "INSERT");
        }
        return ok;
    }

    public boolean ajouterEmploye(String nom, String poste, double salaireBase, double deductions) {
        Employe e = new Employe();
        e.setNom(nom);
        e.setPoste(poste);
        e.setSalaireBase(salaireBase);
        e.setDeductions(deductions);
        return ajouterEmploye(e);
    }

    public boolean supprimerEmploye(String id) {
        Connection conn = getPreferredWriteConnection();
        if (conn == null) return false;

        boolean ok = false;
        String usedSource = null;
        try {
            usedSource = getSource(conn);
            ok = supprimerDansBase(conn, MigrationManager.TABLE_EMPLOYES, id, usedSource);
            if (ok) declencherSyncSiHorsTransaction(conn);
        } catch (SQLException | RuntimeException e) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, e, () -> "❌ Échec supprimerEmploye sur " + srcErr);
        } finally {
            closeIfNeeded(conn);
        }

        if (ok) {
            enregistrerDeleteDansOutbox(MigrationManager.TABLE_EMPLOYES, id, usedSource);
        }
        return ok;
    }

    public boolean modifierEmploye(String id, String nom, String poste,
                                     double salaireBase, double deductions) {
        Connection conn = getPreferredWriteConnection();
        if (conn == null) return false;

        boolean ok = false;
        String usedSource = null;
        try {
            usedSource = getSource(conn);
            String sql = "UPDATE " + MigrationManager.TABLE_EMPLOYES
                    + " SET nom=?, poste=?, salaire_base=?, deductions=?, source=? "
                    + "WHERE institution_id=? AND id=?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, nom);
                ps.setString(2, poste);
                ps.setDouble(3, salaireBase);
                ps.setDouble(4, deductions);
                ps.setString(5, usedSource);
                ps.setString(6, institutionId);
                ps.setString(7, id);
                ok = ps.executeUpdate() > 0;
            }
            if (ok) declencherSyncSiHorsTransaction(conn);
        } catch (SQLException | RuntimeException e) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, e, () -> "❌ Échec modifierEmploye sur " + srcErr);
        } finally {
            closeIfNeeded(conn);
        }

        if (ok) {
            enregistrerDansOutbox(MigrationManager.TABLE_EMPLOYES,
                    cleComposite(id, institutionId),
                    "{\"id\":\"" + escapeJson(id) + "\","
                            + "\"nom\":\"" + escapeJson(nom) + "\","
                            + "\"poste\":\"" + escapeJson(poste) + "\","
                            + "\"salaireBase\":" + salaireBase + ","
                            + "\"deductions\":" + deductions + "}",
                    "UPDATE");
        }
        return ok;
    }

    public double getTotalSalaires() throws SQLException {
        return getTotalSalaires(null, null);
    }

    public double getTotalSalaires(String annee, String mois) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT SUM(salaire_base - deductions) FROM " + MigrationManager.TABLE_EMPLOYES
                + " WHERE institution_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(institutionId);

        Connection conn = null;
        try {
            conn = getConnection();
            if (annee != null && !annee.isBlank() && columnExists("employes", "annee_academique")) {
                sql.append(" AND annee_academique = ?"); args.add(annee);
            }
            if (mois != null && !mois.isBlank() && columnExists("employes", "mois")) {
                sql.append(" AND mois = ?"); args.add(mois);
            }
            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                for (int i = 0; i < args.size(); i++) ps.setObject(i + 1, args.get(i));
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getDouble(1) : 0.0;
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
    }

    // =========================================================
    // BUDGET PAR CLASSE
    // =========================================================
    public List<Map<String, Object>> getBudgetsParClasse(String anneeAcademique)
            throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT classe, total_recettes, total_depenses, solde "
                + "FROM " + MigrationManager.TABLE_BUDGET_CLASSE
                + " WHERE institution_id = ? AND annee_academique = ?";
        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                ps.setString(2, anneeAcademique);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new HashMap<>();
                        row.put("classe", rs.getString("classe"));
                        row.put("recettes", rs.getDouble("total_recettes"));
                        row.put("depenses", rs.getDouble("total_depenses"));
                        row.put("solde", rs.getDouble("solde"));
                        list.add(row);
                    }
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return list;
    }

    // =========================================================
    // ÉTUDIANTS
    // =========================================================
    public boolean etudiantExiste(String etudiantId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + MigrationManager.TABLE_ETUDIANTS
                + " WHERE institution_id = ? AND numero_identifiant = ?";
        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                ps.setString(2, etudiantId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() && rs.getInt(1) > 0;
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
    }

    public Etudiant getEtudiant(String etudiantId) throws SQLException {
        String sql = "SELECT * FROM " + MigrationManager.TABLE_ETUDIANTS
                + " WHERE institution_id = ? AND numero_identifiant = ?";
        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                ps.setString(2, etudiantId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        Etudiant e = new Etudiant();
                        e.setNumeroIdentifiantEtudiant(rs.getString("numero_identifiant"));
                        e.setNom(rs.getString("nom"));
                        e.setPrenom(rs.getString("prenom"));
                        e.setClasse(rs.getString("classe"));
                        e.setEmail(rs.getString("email"));
                        e.setTelephone(rs.getString("telephone"));
                        return e;
                    }
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return null;
    }

    public List<Map<String, Object>> getPaiementsEtudiant(String etudiantId,
                                                            String anneeAcademique)
            throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT id, montant, date_paiement, moyen_paiement, reference, statut "
                + "FROM " + MigrationManager.TABLE_PAIEMENTS_ETUDIANTS
                + " WHERE institution_id = ? AND numero_identifiant = ? AND annee_academique = ? "
                + "ORDER BY date_paiement DESC";
        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                ps.setString(2, etudiantId);
                ps.setString(3, anneeAcademique);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new HashMap<>();
                        row.put("id", rs.getString("id"));
                        row.put("montant", rs.getDouble("montant"));
                        row.put("date_paiement", rs.getTimestamp("date_paiement"));
                        row.put("moyen_paiement", rs.getString("moyen_paiement"));
                        row.put("reference", rs.getString("reference"));
                        row.put("statut", rs.getString("statut"));
                        list.add(row);
                    }
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return list;
    }

    public double getTotalPayeEtudiant(String etudiantId, String anneeAcademique)
            throws SQLException {
        String sql = "SELECT SUM(montant) FROM " + MigrationManager.TABLE_PAIEMENTS_ETUDIANTS
                + " WHERE institution_id = ? AND numero_identifiant = ? AND annee_academique = ? "
                + "AND statut = 'VALIDÉ'";
        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                ps.setString(2, etudiantId);
                ps.setString(3, anneeAcademique);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getDouble(1) : 0.0;
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
    }

    // =========================================================
    // OUTBOX
    // =========================================================
    private void enregistrerDansOutbox(String table, String primaryKey,
                                         String json, String operation) {
        String sql = """
            INSERT INTO sync_outbox
              (table_name, operation, primary_key, row_data, target_source, statut)
            VALUES (?, ?, ?, ?, 'REMOTE', 'PENDING')
        """;
        try (Connection conn = db().getLocalConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(OUTBOX_TIMEOUT_SECONDS);
            ps.setString(1, table);
            ps.setString(2, operation);
            ps.setString(3, primaryKey);
            ps.setString(4, json);
            ps.executeUpdate();
            LOGGER.fine(() -> "📥 Outbox " + operation + " : " + table + "/" + primaryKey);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Impossible d'enregistrer dans l'outbox : " + table);
        }
    }

    private void enregistrerDeleteDansOutbox(String table, String id, String source) {
        String sql = """
            INSERT INTO sync_outbox
              (table_name, operation, primary_key, row_data, target_source, statut)
            VALUES (?, 'DELETE', ?, ?, 'REMOTE', 'PENDING')
        """;
        try (Connection conn = db().getLocalConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(OUTBOX_TIMEOUT_SECONDS);
            ps.setString(1, table);
            ps.setString(2, cleComposite(id, institutionId));
            ps.setString(3, "{\"id\":\"" + escapeJson(id) + "\","
                    + "\"institutionId\":\"" + escapeJson(institutionId) + "\","
                    + "\"source\":\"" + escapeJson(source) + "\"}");
            ps.executeUpdate();
            LOGGER.fine(() -> "📥 Outbox DELETE : " + table + "/" + id);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Impossible d'enregistrer le DELETE dans l'outbox");
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // =========================================================
    // SUPPRESSION GÉNÉRIQUE
    // =========================================================
    private boolean supprimerDansBase(Connection conn, String table, String id,
                                        String source) throws SQLException {
        boolean autoCommitOriginal = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            DeleteTracker.enregistrerSuppression(conn, table,
                    cleComposite(id, institutionId), source);
            String sql = "DELETE FROM " + table + " WHERE institution_id = ? AND id = ?";
            int rows;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                ps.setString(2, id);
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
    // SYNCHRONISATION
    // =========================================================
    private void declencherSyncSiHorsTransaction(Connection conn) {
        try {
            if (conn != null && conn.getAutoCommit()) {
                db().declencherSyncImmediateAsync();
                LOGGER.fine("⚡ Sync asynchrone déclenchée (hors transaction).");
            }
        } catch (SQLException ignored) {}
    }

    // =========================================================
    // UTILITAIRES
    // =========================================================
    private boolean tableExists(String table) {
        try (Connection conn = getConnection()) {
            String sql = "SELECT COUNT(*) FROM information_schema.TABLES "
                    + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, table);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() && rs.getInt(1) > 0;
                }
            }
        } catch (SQLException ignored) {}
        return false;
    }

    private boolean columnExists(String table, String column) {
        try (Connection conn = getConnection()) {
            String sql = "SELECT COUNT(*) FROM information_schema.COLUMNS "
                    + "WHERE TABLE_SCHEMA = DATABASE() "
                    + "  AND TABLE_NAME = ? AND COLUMN_NAME = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, table);
                ps.setString(2, column);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException ignored) {}
        return false;
    }

    private String columnDateForTable(String table) {
        return switch (table) {
            case "depenses" -> "date_depense";
            case "recettes_externes" -> "date_recette";
            case "paiements_etudiants" -> "date_paiement";
            default -> null;
        };
    }

    private String getSafe(ResultSet rs, String column) {
        try { return rs.getString(column); }
        catch (SQLException e) { return null; }
    }

    private List<String> splitCsv(String csv) {
        List<String> list = new ArrayList<>();
        if (csv == null || csv.isBlank()) return list;
        for (String p : csv.split(",")) {
            String t = p.trim();
            if (!t.isEmpty()) list.add(t);
        }
        return list;
    }

    private String joinCsv(List<String> values) {
        if (values == null || values.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (String v : values) {
            if (v == null || v.isBlank()) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(v.trim());
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private String firstNonNull(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }
}