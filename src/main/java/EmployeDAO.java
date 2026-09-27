import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class EmployeDAO {

    private static final Logger LOGGER = Logger.getLogger(EmployeDAO.class.getName());
    private static final String TABLE = MigrationManager.TABLE_EMPLOYES;

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

    private static final String COLONNES =
            "id, employe_id, institution_id, annee_academique, mois, classes, " +
            "nom, poste, salaire_base, deductions, date_embauche, source, last_modified";

    // =========================================================
    // HELPERS
    // =========================================================
    private static DatabaseManager db() { return DatabaseManager.getInstance(); }

    private static Connection getConnection() throws SQLException {
        return db().getConnection();
    }

    private static void closeIfNeeded(Connection conn) {
        if (conn != null) {
            try { if (!conn.isClosed()) conn.close(); } catch (SQLException ignored) {}
        }
    }

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

    /**
     * ✅ Clé composite pour les tombstones.
     */
    private static String cleComposite(String id, String institutionId) {
        return id + "|" + institutionId;
    }

    private static boolean isNew(Employe e) {
        return e.getId() == null || e.getId().isBlank();
    }

    // =========================================================
    // SAVE (INSERT ou UPDATE) — LOCAL uniquement + outbox
    // =========================================================
    public static boolean save(Employe e) throws SQLException {
        if (e == null) return false;

        // ✅ Détecter "nouveau" AVANT de générer l'UUID
        boolean wasNew = isNew(e);
        if (wasNew) {
            e.setId(UUID.randomUUID().toString());
        }

        Connection conn = getPreferredWriteConnection();
        if (conn == null) {
            LOGGER.severe(() -> "❌ Aucune connexion disponible pour save employé");
            return false;
        }

        boolean ok = false;
        String usedSource = null;
        try {
            usedSource = getSource(conn);
            ok = wasNew ? insertSansSync(conn, e, usedSource)
                        : updateSansSync(conn, e, usedSource);

            // Si UPDATE n'a rien changé (ligne absente), tenter un INSERT
            if (!ok && !wasNew) {
                ok = insertSansSync(conn, e, usedSource);
            }

            if (ok) declencherSyncSiHorsTransaction(conn);
        } catch (RuntimeException ex) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, ex,
                    () -> "❌ Échec save employé sur " + srcErr + " : id=" + e.getId());
        } finally {
            closeIfNeeded(conn);
        }

        if (ok) {
            enregistrerDansOutbox(e, wasNew ? "INSERT" : "UPDATE");

            final String src = usedSource;
            LOGGER.info(() -> "✅ Employé sauvegardé sur " + src
                    + " + outbox : id=" + e.getId());
        }
        return ok;
    }

    // =========================================================
    // INSERT — avec id UUID
    // =========================================================
    private static boolean insertSansSync(Connection conn, Employe e, String source) {
        if (e.getId() == null || e.getId().isBlank()) {
            e.setId(UUID.randomUUID().toString());
        }

        String sql = "INSERT INTO " + TABLE
                + " (institution_id, id, employe_id, annee_academique, mois, classes, " +
                "  nom, poste, salaire_base, deductions, source) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        Connection c = (conn != null) ? conn : null;
        try {
            if (c == null) c = getConnection();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, e.getInstitutionId());
                ps.setString(2, e.getId());
                ps.setInt(3, 0);
                ps.setString(4, e.getAnneeAcademique());
                ps.setString(5, e.getMois());
                ps.setString(6, joinClasses(e.getClasses()));
                ps.setString(7, e.getNom());
                ps.setString(8, e.getPoste());
                ps.setDouble(9, e.getSalaireBase());
                ps.setDouble(10, e.getDeductions());
                ps.setString(11, source != null ? source : getSource(c));
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Erreur insert employé", ex);
            return false;
        }
    }

    // =========================================================
    // UPDATE
    // =========================================================
    private static boolean updateSansSync(Connection conn, Employe e, String source) {
        if (e.getId() == null || e.getId().isBlank()) return false;

        String sql = "UPDATE " + TABLE
                + " SET annee_academique=?, mois=?, classes=?, " +
                "    nom=?, poste=?, salaire_base=?, deductions=?, source=? " +
                "WHERE id=? AND institution_id=?";

        Connection c = (conn != null) ? conn : null;
        try {
            if (c == null) c = getConnection();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, e.getAnneeAcademique());
                ps.setString(2, e.getMois());
                ps.setString(3, joinClasses(e.getClasses()));
                ps.setString(4, e.getNom());
                ps.setString(5, e.getPoste());
                ps.setDouble(6, e.getSalaireBase());
                ps.setDouble(7, e.getDeductions());
                ps.setString(8, source != null ? source : getSource(c));
                ps.setString(9, e.getId());
                ps.setString(10, e.getInstitutionId());
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Erreur update employé", ex);
            return false;
        }
    }

    // =========================================================
    // DELETE — LOCAL uniquement + tombstone + outbox
    // =========================================================
    public static boolean delete(String id, String institutionId) {
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
                boolean ok = deleteInBase(conn, id, institutionId, usedSource);
                conn.commit();

                if (ok) {
                    enregistrerDeleteDansOutbox(id, institutionId, usedSource);
                    declencherSyncSiHorsTransaction(conn);
                    LOGGER.info(() -> "✅ Employé supprimé : id=" + id + " + outbox");
                }
                return ok;

            } catch (SQLException ex) {
                try { conn.rollback(); } catch (SQLException ignored) {}
                LOGGER.log(Level.SEVERE, "Erreur delete employé", ex);
                return false;
            } finally {
                try { conn.setAutoCommit(autoCommitOriginal); } catch (SQLException ignored) {}
            }

        } catch (SQLException | RuntimeException ex) {
            LOGGER.log(Level.SEVERE, ex, () -> "❌ Échec delete employé : id=" + id);
            return false;
        } finally {
            closeIfNeeded(conn);
        }
    }

    private static boolean deleteInBase(Connection conn, String id, String institutionId,
                                          String source) throws SQLException {
        DeleteTracker.enregistrerSuppression(conn, TABLE, cleComposite(id, institutionId), source);

        String sql = "DELETE FROM " + TABLE + " WHERE id=? AND institution_id=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setString(1, id);
            ps.setString(2, institutionId);
            return ps.executeUpdate() > 0;
        }
    }

    // =========================================================
    // READ ALL — fusion LOCAL + REMOTE
    // =========================================================
    public static List<Employe> getAll(String institutionId) {
        Map<String, Employe> byId = new LinkedHashMap<>();

        try (Connection localConn = db().getLocalConnection()) {
            for (Employe e : getAllFromConnection(localConn, institutionId)) {
                if (e.getId() != null && !e.getId().isBlank()) {
                    byId.put(e.getId(), e);
                }
            }
        } catch (SQLException ex) {
            LOGGER.fine(() -> "LOCAL indisponible pour getAll : " + ex.getMessage());
        }

        try (Connection remoteConn = db().getRemoteConnection()) {
            for (Employe e : getAllFromConnection(remoteConn, institutionId)) {
                if (e.getId() != null && !e.getId().isBlank()
                        && !byId.containsKey(e.getId())) {
                    byId.put(e.getId(), e);
                }
            }
        } catch (SQLException ex) {
            LOGGER.fine(() -> "REMOTE indisponible pour getAll : " + ex.getMessage());
        }

        return new ArrayList<>(byId.values());
    }

    public static List<Employe> getByAnnee(String institutionId, String anneeAcademique) {
        return filterAll(institutionId, e ->
                anneeAcademique == null || anneeAcademique.isBlank()
                || anneeAcademique.equals(e.getAnneeAcademique()));
    }

    public static List<Employe> getByMois(String institutionId, String mois) {
        return filterAll(institutionId, e ->
                mois == null || mois.isBlank() || mois.equals(e.getMois()));
    }

    public static Employe getById(String institutionId, String id) {
        if (id == null || id.isBlank()) return null;

        try (Connection localConn = db().getLocalConnection()) {
            Employe e = getByIdFromConnection(localConn, institutionId, id);
            if (e != null) return e;
        } catch (SQLException ex) {
            LOGGER.fine(() -> "LOCAL indisponible : " + ex.getMessage());
        }

        try (Connection remoteConn = db().getRemoteConnection()) {
            return getByIdFromConnection(remoteConn, institutionId, id);
        } catch (SQLException ex) {
            LOGGER.fine(() -> "REMOTE indisponible : " + ex.getMessage());
        }
        return null;
    }

    private static Employe getByIdFromConnection(Connection conn, String institutionId, String id)
            throws SQLException {
        String sql = "SELECT " + COLONNES + " FROM " + TABLE
                + " WHERE institution_id = ? AND id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setString(1, institutionId);
            ps.setString(2, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapEmploye(rs);
            }
        }
        return null;
    }

    private static List<Employe> filterAll(String institutionId,
                                            java.util.function.Predicate<Employe> predicate) {
        List<Employe> all = getAll(institutionId);
        List<Employe> filtered = new ArrayList<>();
        for (Employe e : all) {
            if (predicate.test(e)) filtered.add(e);
        }
        return filtered;
    }

    // =========================================================
    // OUTBOX
    // =========================================================
    private static void enregistrerDansOutbox(Employe e, String operation) {
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
            ps.setString(3, cleComposite(e.getId(), e.getInstitutionId()));
            ps.setString(4, toJson(e));
            ps.executeUpdate();
            LOGGER.fine(() -> "📥 Outbox " + operation + " : id=" + e.getId());
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, ex,
                    () -> "❌ Impossible d'enregistrer dans l'outbox : id=" + e.getId());
        }
    }

    private static void enregistrerDeleteDansOutbox(String id, String institutionId,
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
            ps.setString(2, cleComposite(id, institutionId));
            ps.setString(3, "{\"id\":\"" + escapeJson(id) + "\","
                    + "\"institutionId\":\"" + escapeJson(institutionId) + "\","
                    + "\"source\":\"" + escapeJson(source) + "\"}");
            ps.executeUpdate();
            LOGGER.fine(() -> "📥 Outbox DELETE : id=" + id);
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, ex,
                    () -> "❌ Impossible d'enregistrer le DELETE dans l'outbox");
        }
    }

    private static String toJson(Employe e) {
        try {
            return GSON.toJson(e);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, ex,
                    () -> "Impossible de sérialiser l'employé en JSON");
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
    private static List<Employe> getAllFromConnection(Connection conn, String institutionId) {
        List<Employe> list = new ArrayList<>();
        String sql = "SELECT " + COLONNES + " FROM " + TABLE
                + " WHERE institution_id = ? ORDER BY nom";

        if (conn == null) return list;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setString(1, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapEmploye(rs));
            }
        } catch (SQLException ex) {
            if (ex.getMessage() == null || !ex.getMessage().contains("doesn't exist")) {
                LOGGER.log(Level.FINE, "Erreur getAll sur une base", ex);
            }
        }
        return list;
    }

    private static Employe mapEmploye(ResultSet rs) throws SQLException {
        Employe e = new Employe();
        e.setId(rs.getString("id"));
        e.setInstitutionId(rs.getString("institution_id"));
        e.setAnneeAcademique(rs.getString("annee_academique"));
        e.setMois(rs.getString("mois"));
        e.setClasses(splitClasses(rs.getString("classes")));
        e.setNom(rs.getString("nom"));
        e.setPoste(rs.getString("poste"));
        e.setSalaireBase(rs.getDouble("salaire_base"));
        e.setDeductions(rs.getDouble("deductions"));
        try { e.setSource(rs.getString("source")); } catch (SQLException ignored) {}
        return e;
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

    // =========================================================
    // UTILITAIRES classes (List<String> ↔ CSV)
    // =========================================================
    private static String joinClasses(List<String> classes) {
        if (classes == null || classes.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (String c : classes) {
            if (c == null || c.isBlank()) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(c.trim());
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private static List<String> splitClasses(String csv) {
        List<String> list = new ArrayList<>();
        if (csv == null || csv.isBlank()) return list;
        for (String p : csv.split(",")) {
            String t = p.trim();
            if (!t.isEmpty()) list.add(t);
        }
        return list;
    }
}