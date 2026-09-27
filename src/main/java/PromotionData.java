import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PromotionData {

    private static final Logger LOGGER = Logger.getLogger(PromotionData.class.getName());
    private static final String TABLE = MigrationManager.TABLE_PROMOTIONS;

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

    /** Connexion partagée optionnelle (null = mode autonome). */
    private final Connection connection;

    public PromotionData() {
        this.connection = null;
    }

    public PromotionData(Connection connection) {
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

    // =========================================================
    // CREATE
    // =========================================================
    public boolean créer(Promotion promotion) throws SQLException {
        if (promotion == null) throw new IllegalArgumentException("Promotion nulle");

        Connection conn = getPreferredWriteConnection();
        if (conn == null) {
            LOGGER.severe(() -> "❌ Aucune connexion disponible pour créer "
                    + promotion.getPromotion());
            return false;
        }

        boolean ok = false;
        String usedSource = null;
        try {
            usedSource = getSource(conn);
            ok = createSansSync(conn, promotion, usedSource);
            if (ok) declencherSyncSiHorsTransaction(conn);
        } catch (RuntimeException e) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec création promotion sur " + srcErr
                            + " : " + promotion.getPromotion());
        } finally {
            closeIfNeeded(conn);
        }

        if (!ok) return false;

        enregistrerDansOutbox(promotion, "INSERT");

        final String src = usedSource;
        LOGGER.info(() -> "✅ Promotion créée sur " + src
                + " + outbox : " + promotion.getPromotion());
        return true;
    }

    private boolean createSansSync(Connection conn, Promotion promotion, String source) {
        String sql = "INSERT INTO " + TABLE + " "
                   + "(institution_id, annee_academique, promotion, date_debut, date_fin, est_active, source) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            stmt.setString(1, promotion.getInstitutionId());
            stmt.setString(2, promotion.getAnneeAcademique());
            stmt.setString(3, promotion.getPromotion());
            stmt.setDate(4, Date.valueOf(promotion.getDateDebut()));
            stmt.setDate(5, Date.valueOf(promotion.getDateFin()));
            stmt.setBoolean(6, Boolean.TRUE.equals(promotion.getEstActive()));
            stmt.setString(7, source != null ? source : getSource(conn));

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur création promotion", e);
            return false;
        }
    }

    // =========================================================
    // READ — par clé composite
    // =========================================================
    public Optional<Promotion> trouverParCle(String institutionId,
                                               String anneeAcademique,
                                               String promotion) throws SQLException {
        String sql = "SELECT * FROM " + TABLE + " "
                   + "WHERE institution_id = ? AND annee_academique = ? AND promotion = ?";

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                stmt.setString(1, institutionId);
                stmt.setString(2, anneeAcademique);
                stmt.setString(3, promotion);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapResultSetToPromotion(rs));
                    }
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return Optional.empty();
    }

    public List<Promotion> listerParInstitution(String institutionId) throws SQLException {
        List<Promotion> liste = new ArrayList<>();
        String sql = "SELECT * FROM " + TABLE + " "
                   + "WHERE institution_id = ? "
                   + "ORDER BY annee_academique DESC, promotion ASC";

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                stmt.setString(1, institutionId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) liste.add(mapResultSetToPromotion(rs));
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return liste;
    }

    public List<Promotion> listerParInstitutionEtAnnee(String institutionId,
                                                         String anneeAcademique) throws SQLException {
        List<Promotion> liste = new ArrayList<>();
        String sql = "SELECT * FROM " + TABLE + " "
                   + "WHERE institution_id = ? AND annee_academique = ? "
                   + "ORDER BY promotion ASC";

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                stmt.setString(1, institutionId);
                stmt.setString(2, anneeAcademique);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) liste.add(mapResultSetToPromotion(rs));
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return liste;
    }

    public Optional<Promotion> trouverActiveParInstitution(String institutionId) throws SQLException {
        String sql = "SELECT * FROM " + TABLE + " "
                   + "WHERE institution_id = ? AND est_active = TRUE LIMIT 1";

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                stmt.setString(1, institutionId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapResultSetToPromotion(rs));
                    }
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return Optional.empty();
    }

    // =========================================================
    // UPDATE — clé composite
    // =========================================================
    public boolean mettreAJour(Promotion promotion) throws SQLException {
        if (promotion == null) throw new IllegalArgumentException("Promotion nulle");

        Connection conn = getPreferredWriteConnection();
        if (conn == null) return false;

        boolean ok = false;
        String usedSource = null;
        try {
            usedSource = getSource(conn);
            ok = updateSansSync(conn, promotion, usedSource);
            if (ok) declencherSyncSiHorsTransaction(conn);
        } catch (RuntimeException e) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec update promotion sur " + srcErr
                            + " : " + promotion.getPromotion());
        } finally {
            closeIfNeeded(conn);
        }

        if (ok) enregistrerDansOutbox(promotion, "UPDATE");

        return ok;
    }

    private boolean updateSansSync(Connection conn, Promotion promotion, String source) {
        String sql = "UPDATE " + TABLE + " "
                   + "SET date_debut = ?, date_fin = ?, est_active = ?, source = ? "
                   + "WHERE institution_id = ? AND annee_academique = ? AND promotion = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            stmt.setDate(1, Date.valueOf(promotion.getDateDebut()));
            stmt.setDate(2, Date.valueOf(promotion.getDateFin()));
            stmt.setBoolean(3, Boolean.TRUE.equals(promotion.getEstActive()));
            stmt.setString(4, source != null ? source : getSource(conn));
            stmt.setString(5, promotion.getInstitutionId());
            stmt.setString(6, promotion.getAnneeAcademique());
            stmt.setString(7, promotion.getPromotion());

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur mise à jour promotion", e);
            return false;
        }
    }

    /**
     * ✅ Mise à jour avec changement de clé composite (si le nom change).
     * Effectue DELETE + INSERT dans une transaction.
     */
    public boolean mettreAJourAvecChangementDeCle(String institutionId,
                                                    String ancienneAnnee,
                                                    String anciennePromotion,
                                                    Promotion nouvelle) {
        if (nouvelle == null) throw new IllegalArgumentException("Promotion nulle");

        Connection conn = getPreferredWriteConnection();
        if (conn == null) return false;

        boolean autoCommitOriginal;
        String usedSource = null;
        try {
            usedSource = getSource(conn);
            autoCommitOriginal = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try {
                // ① Tombstone de l'ancienne clé
                String ancienneCle = institutionId + "|" + ancienneAnnee + "|" + anciennePromotion;
                DeleteTracker.enregistrerSuppression(conn, TABLE, ancienneCle, usedSource);

                // ② Supprimer l'ancienne
                String deleteSql = "DELETE FROM " + TABLE + " "
                                 + "WHERE institution_id = ? AND annee_academique = ? AND promotion = ?";
                try (PreparedStatement del = conn.prepareStatement(deleteSql)) {
                    del.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                    del.setString(1, institutionId);
                    del.setString(2, ancienneAnnee);
                    del.setString(3, anciennePromotion);
                    del.executeUpdate();
                }

                // ③ Insérer la nouvelle
                String insertSql = "INSERT INTO " + TABLE + " "
                                 + "(institution_id, annee_academique, promotion, date_debut, date_fin, est_active, source) "
                                 + "VALUES (?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ins = conn.prepareStatement(insertSql)) {
                    ins.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                    ins.setString(1, nouvelle.getInstitutionId());
                    ins.setString(2, nouvelle.getAnneeAcademique());
                    ins.setString(3, nouvelle.getPromotion());
                    ins.setDate(4, Date.valueOf(nouvelle.getDateDebut()));
                    ins.setDate(5, Date.valueOf(nouvelle.getDateFin()));
                    ins.setBoolean(6, Boolean.TRUE.equals(nouvelle.getEstActive()));
                    ins.setString(7, usedSource);
                    ins.executeUpdate();
                }

                conn.commit();

            } catch (SQLException e) {
                try { conn.rollback(); } catch (SQLException ignored) {}
                LOGGER.log(Level.SEVERE, "Erreur mettreAJourAvecChangementDeCle", e);
                return false;
            } finally {
                try { conn.setAutoCommit(autoCommitOriginal); } catch (SQLException ignored) {}
            }

        } catch (SQLException | RuntimeException e) {
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec mettreAJourAvecChangementDeCle promotion");
            return false;
        } finally {
            closeIfNeeded(conn);
        }

        // Sync + outbox
        enregistrerDansOutbox(nouvelle, "UPDATE");
        declencherSyncSiHorsTransaction(conn);

        final String src = usedSource;
        LOGGER.info(() -> "✅ Promotion renommée sur " + src
                + " + outbox : " + anciennePromotion + " → " + nouvelle.getPromotion());
        return true;
    }

    // =========================================================
    // DÉFINIR ACTIVE — clé composite
    // =========================================================
    public boolean definirCommeActive(String institutionId,
                                        String anneeAcademique,
                                        String promotion) {

        Connection conn = getPreferredWriteConnection();
        if (conn == null) return false;

        boolean autoCommitOriginal;
        String usedSource ;
        try {
            usedSource = getSource(conn);
            autoCommitOriginal = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try {
                // ① Tombstone de toutes les promotions actives à désactiver
                String selectSql = "SELECT annee_academique, promotion FROM " + TABLE
                                 + " WHERE institution_id = ? AND est_active = TRUE";
                List<String[]> actives = new ArrayList<>();
                try (PreparedStatement sel = conn.prepareStatement(selectSql)) {
                    sel.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                    sel.setString(1, institutionId);
                    try (ResultSet rs = sel.executeQuery()) {
                        while (rs.next()) {
                            actives.add(new String[]{
                                    rs.getString("annee_academique"),
                                    rs.getString("promotion")
                            });
                        }
                    }
                }

                for (String[] a : actives) {
                    String cle = institutionId + "|" + a[0] + "|" + a[1];
                    DeleteTracker.enregistrerSuppression(conn, TABLE, cle, usedSource);
                }

                // ② Désactiver toutes les promotions de l'institution
                String resetSql = "UPDATE " + TABLE + " SET est_active = FALSE, source = ? "
                                + "WHERE institution_id = ?";
                try (PreparedStatement resetStmt = conn.prepareStatement(resetSql)) {
                    resetStmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                    resetStmt.setString(1, usedSource);
                    resetStmt.setString(2, institutionId);
                    resetStmt.executeUpdate();
                }

                // ③ Activer la promotion choisie
                String activateSql = "UPDATE " + TABLE + " SET est_active = TRUE, source = ? "
                                   + "WHERE institution_id = ? AND annee_academique = ? AND promotion = ?";
                int count;
                try (PreparedStatement activateStmt = conn.prepareStatement(activateSql)) {
                    activateStmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                    activateStmt.setString(1, usedSource);
                    activateStmt.setString(2, institutionId);
                    activateStmt.setString(3, anneeAcademique);
                    activateStmt.setString(4, promotion);
                    count = activateStmt.executeUpdate();
                }

                conn.commit();

                if (count > 0) {
                    // ✅ Outbox UPDATE pour la promotion activée
                    Promotion active = new Promotion();
                    active.setInstitutionId(institutionId);
                    active.setAnneeAcademique(anneeAcademique);
                    active.setPromotion(promotion);
                    active.setEstActive(true);
                    enregistrerDansOutbox(active, "UPDATE");
                    declencherSyncSiHorsTransaction(conn);
                }
                return count > 0;

            } catch (SQLException e) {
                try { conn.rollback(); } catch (SQLException ignored) {}
                LOGGER.log(Level.SEVERE, "Erreur definirCommeActive", e);
                return false;
            } finally {
                try { conn.setAutoCommit(autoCommitOriginal); } catch (SQLException ignored) {}
            }

        } catch (SQLException | RuntimeException e) {
            LOGGER.log(Level.SEVERE, e, () -> "❌ Échec definirCommeActive");
            return false;
        } finally {
            closeIfNeeded(conn);
        }
    }

    // =========================================================
    // DELETE — clé composite
    // =========================================================
    public boolean supprimer(String institutionId,
                               String anneeAcademique,
                               String promotion) {

        if (institutionId == null || institutionId.isBlank()
                || anneeAcademique == null || anneeAcademique.isBlank()
                || promotion == null || promotion.isBlank()) {
            LOGGER.warning("supprimer() : paramètres null ou vides");
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
                // ① Tombstone
                String cle = institutionId + "|" + anneeAcademique + "|" + promotion;
                DeleteTracker.enregistrerSuppression(conn, TABLE, cle, usedSource);

                // ② DELETE réel
                String sql = "DELETE FROM " + TABLE + " "
                           + "WHERE institution_id = ? AND annee_academique = ? AND promotion = ?";
                int rows;
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                    stmt.setString(1, institutionId);
                    stmt.setString(2, anneeAcademique);
                    stmt.setString(3, promotion);
                    rows = stmt.executeUpdate();
                }

                conn.commit();

                if (rows > 0) {
                    enregistrerDeleteDansOutbox(institutionId, anneeAcademique,
                            promotion, usedSource);
                    declencherSyncSiHorsTransaction(conn);
                    LOGGER.info(() -> "✅ Promotion supprimée : "
                            + anneeAcademique + "/" + promotion + " + outbox");
                }
                return rows > 0;

            } catch (SQLException e) {
                try { conn.rollback(); } catch (SQLException ignored) {}
                LOGGER.log(Level.SEVERE, "Erreur suppression promotion", e);
                return false;
            } finally {
                try { conn.setAutoCommit(autoCommitOriginal); } catch (SQLException ignored) {}
            }

        } catch (SQLException | RuntimeException e) {
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec suppression promotion : " + promotion);
            return false;
        } finally {
            closeIfNeeded(conn);
        }
    }

    // =========================================================
    // OUTBOX
    // =========================================================
    private void enregistrerDansOutbox(Promotion promotion, String operation) {
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
            ps.setString(3, promotion.getInstitutionId() + "|"
                    + promotion.getAnneeAcademique() + "|" + promotion.getPromotion());
            ps.setString(4, toJson(promotion));
            ps.executeUpdate();
            LOGGER.fine(() -> "📥 Outbox " + operation + " : " + promotion.getPromotion());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Impossible d'enregistrer dans l'outbox : "
                            + promotion.getPromotion());
        }
    }

    private void enregistrerDeleteDansOutbox(String institutionId, String annee,
                                                String promotion, String source) {
        String sql = """
            INSERT INTO sync_outbox
              (table_name, operation, primary_key, row_data, target_source, statut)
            VALUES (?, 'DELETE', ?, ?, 'REMOTE', 'PENDING')
        """;
        try (Connection conn = db().getLocalConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(OUTBOX_TIMEOUT_SECONDS);
            ps.setString(1, TABLE);
            ps.setString(2, institutionId + "|" + annee + "|" + promotion);
            ps.setString(3, "{\"institutionId\":\"" + escapeJson(institutionId) + "\","
                    + "\"anneeAcademique\":\"" + escapeJson(annee) + "\","
                    + "\"promotion\":\"" + escapeJson(promotion) + "\","
                    + "\"source\":\"" + escapeJson(source) + "\"}");
            ps.executeUpdate();
            LOGGER.fine(() -> "📥 Outbox DELETE : " + promotion);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Impossible d'enregistrer le DELETE dans l'outbox");
        }
    }

    private String toJson(Promotion p) {
        try {
            return GSON.toJson(p);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, e,
                    () -> "Impossible de sérialiser la promotion en JSON");
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
    private Promotion mapResultSetToPromotion(ResultSet rs) throws SQLException {
        Promotion p = new Promotion();
        p.setInstitutionId(rs.getString("institution_id"));
        p.setAnneeAcademique(rs.getString("annee_academique"));
        p.setPromotion(rs.getString("promotion"));

        Date dateDebut = rs.getDate("date_debut");
        if (dateDebut != null) {
            p.setDateDebut(dateDebut.toLocalDate());
        }

        Date dateFin = rs.getDate("date_fin");
        if (dateFin != null) {
            p.setDateFin(dateFin.toLocalDate());
        }

        p.setEstActive(rs.getBoolean("est_active"));

        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            p.setCreatedAt(createdAt.toLocalDateTime());
        }

        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) {
            p.setUpdatedAt(updatedAt.toLocalDateTime());
        }

        try {
            p.setSource(rs.getString("source"));
        } catch (SQLException e) {
            // Colonne source absente
        }

        return p;
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