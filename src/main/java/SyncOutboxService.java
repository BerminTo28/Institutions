import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service de rejeu des opérations de l'outbox.
 *
 * ✅ RÈGLE D'ISOLATION STRICTE :
 *   - La table `institutions` est traitée GLOBALEMENT (toutes institutions)
 *   - TOUTES les autres tables sont FILTRÉES par `institution_id`
 *   - Toute opération de l'outbox dont l'`institution_id` ne correspond pas
 *     à celui de cette instance est marquée FAILED avec raison "autre institution"
 *     et n'est JAMAIS rejouée (ni sur LOCAL, ni sur REMOTE)
 */
public class SyncOutboxService {

    private static final Logger LOGGER = Logger.getLogger(SyncOutboxService.class.getName());

    private static final int MAX_TENTATIVES   = 5;
    private static final int BATCH_SIZE       = 100;
    private static final int MAX_BATCHES      = 10;
    private static final int MAX_AGE_HOURS    = 24;

    private final DatabaseManager dbManager;

    /** ✅ Institution de cette instance (obligatoire). */
    private final String monInstitutionId;

    /**
     * ✅ Constructeur principal — l'institutionId est OBLIGATOIRE.
     */
    public SyncOutboxService(DatabaseManager dbManager, String institutionId) {
        if (dbManager == null) {
            throw new IllegalArgumentException("SyncOutboxService : dbManager obligatoire");
        }
        if (institutionId == null || institutionId.isBlank()) {
            throw new IllegalArgumentException(
                "SyncOutboxService : institutionId obligatoire (filtrage par institution)");
        }
        this.dbManager = dbManager;
        this.monInstitutionId = institutionId.trim();
        LOGGER.info(() -> "🔧 SyncOutboxService initialisé pour institution="
                + this.monInstitutionId);
    }

    /**
     * ⚠️ Constructeur legacy — déduit l'institution depuis la BD locale.
     */
    public SyncOutboxService(DatabaseManager dbManager) {
        this(dbManager, devinerInstitutionCourante(dbManager));
    }

    private static String devinerInstitutionCourante(DatabaseManager dbManager) {
        try (Connection c = dbManager.getLocalConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT institution_id FROM institutions LIMIT 1");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getString(1);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Impossible de déduire l'institution courante", e);
        }
        throw new IllegalStateException(
            "SyncOutboxService : institutionId introuvable — utilise le constructeur explicite");
    }

    // ============================================================
    // CONTRÔLE D'APPARTENANCE
    // ============================================================

    /**
     * ✅ Détermine si une table doit être filtrée par institution.
     *    Exception : `institutions` → jamais filtrée.
     */
    private boolean fautFiltrerParInstitution(String table) {
        if (table == null) return true;
        return !MigrationManager.TABLE_INSTITUTIONS.equals(table);
    }

    /**
     * ✅ Extrait l'`institution_id` depuis la clé primaire de l'entrée.
     *    La PK est au format `institution_id|id` ou `institution_id|...`.
     *    Retourne null si la table n'a pas d'`institution_id` dans sa PK.
     */
    private String extraireInstitutionId(String table, String primaryKey) {
        if (table == null || primaryKey == null) return null;

        List<String> pkCols = SyncService.getKeyColumns(table);
        if (pkCols == null || pkCols.isEmpty()) return null;

        int idxInst = pkCols.indexOf("institution_id");
        if (idxInst < 0) return null;

        String[] parts = primaryKey.split("\\|", -1);
        if (idxInst >= parts.length) return null;

        return parts[idxInst];
    }

    /**
     * ✅ Vérifie qu'une entrée appartient bien à mon institution.
     *    - `institutions` : toujours OK
     *    - autres tables : exige institution_id == monInstitutionId
     */
    private boolean entreeAutorisee(OutboxEntry e) {
        if (e == null || e.table == null) return false;
        if (!fautFiltrerParInstitution(e.table)) return true;

        String instId = extraireInstitutionId(e.table, e.primaryKey);
        if (instId == null) {
            LOGGER.warning(() -> String.format(
                "⛔ Outbox #%d : pas d'institution_id dans la PK (%s pk=%s)",
                e.id, e.table, e.primaryKey));
            return false;
        }
        return monInstitutionId.equals(instId);
    }

    /**
     * Rejoue toutes les opérations PENDING de l'outbox.
     */
    public void rejouerOutbox() {
        if (!dbManager.isLocalHealthy() || !dbManager.isRemoteHealthy()) {
            LOGGER.fine("⏭️ Outbox : bases non saines, rejeu ignoré");
            return;
        }

        long startGlobal = System.currentTimeMillis();
        int totalOk = 0, totalEchecs = 0, totalAbandonnees = 0, totalIgnorees = 0;
        int batchsTraites = 0;

        for (int i = 0; i < MAX_BATCHES; i++) {
            BatchResult result = traiterUnBatch();
            if (result == null) break;

            totalOk          += result.ok;
            totalEchecs      += result.echecs;
            totalAbandonnees += result.abandonnees;
            totalIgnorees    += result.ignorees;
            batchsTraites++;

            if (result.totalTraite < BATCH_SIZE) break;
            if (result.totalTraite == 0) break;
        }

        // ✅ Log INFO UNIQUEMENT s'il y a eu de l'activité
        if (batchsTraites == 0) return;
        if (totalOk == 0 && totalEchecs == 0
                && totalAbandonnees == 0 && totalIgnorees == 0) return;

        long duree = System.currentTimeMillis() - startGlobal;

        if (totalEchecs == 0 && totalAbandonnees == 0) {
            final int ok = totalOk;
            final int ig = totalIgnorees;
            final long d = duree;
            LOGGER.info(() -> "📦 Outbox [" + monInstitutionId + "] : "
                    + ok + " opération(s) rejouée(s)"
                    + (ig > 0 ? ", " + ig + " ignorée(s) (autre institution)" : "")
                    + " en " + d + " ms");
        } else {
            final int ok = totalOk;
            final int ko = totalEchecs;
            final int ab = totalAbandonnees;
            final int ig = totalIgnorees;
            final long d = duree;
            LOGGER.warning(() -> "⚠️ Outbox [" + monInstitutionId + "] : "
                    + ok + " OK, " + ko + " échecs, "
                    + ab + " abandonnées"
                    + (ig > 0 ? ", " + ig + " ignorées" : "")
                    + " en " + d + " ms");
        }
    }

    /**
     * Traite un batch d'opérations PENDING.
     */
    private BatchResult traiterUnBatch() {
        Connection conn = null;
        try {
            conn = dbManager.getLocalConnection();

            List<OutboxEntry> entries = lirePending(conn);
            if (entries.isEmpty()) return null;

            BatchResult result = new BatchResult();
            result.totalTraite = entries.size();

            for (OutboxEntry e : entries) {

                // ✅ GARDE-FOU 1 : contrôle d'appartenance AVANT tout rejeu
                if (!entreeAutorisee(e)) {
                    marquerEchec(conn, e.id,
                            "Opération d'une autre institution ("
                            + e.table + " pk=" + e.primaryKey
                            + " attendu=" + monInstitutionId + ")");
                    result.ignorees++;
                    final long id = e.id;
                    final String tbl = e.table;
                    final String pk = e.primaryKey;
                    LOGGER.warning(() -> "🚫 Outbox #" + id
                            + " ignoré (autre institution) : " + tbl + "/" + pk);
                    continue;
                }

                try {
                    if (estTropVieux(e)) {
                        marquerEchec(conn, e.id, "Opération trop ancienne (> "
                                + MAX_AGE_HOURS + "h)");
                        result.abandonnees++;
                        final long id = e.id;
                        final String tbl = e.table;
                        final String pk = e.primaryKey;
                        LOGGER.warning(() -> "⛔ Outbox #" + id
                                + " abandonné (trop vieux) : " + tbl + "/" + pk);
                        continue;
                    }

                    RejeuResult rejeu = rejouerOperation(e);

                    if (rejeu.succes) {
                        marquerTermine(conn, e.id);
                        result.ok++;
                        final long id = e.id;
                        final String tbl = e.table;
                        final String pk = e.primaryKey;
                        LOGGER.fine(() -> "✅ Outbox #" + id + " rejoué : "
                                + tbl + "/" + pk);
                    } else {
                        if (rejeu.permanent) {
                            marquerEchec(conn, e.id,
                                    "Erreur permanente : " + rejeu.message);
                            result.abandonnees++;
                            final long id = e.id;
                            final String tbl = e.table;
                            final String pk = e.primaryKey;
                            final String msg = rejeu.message;
                            LOGGER.warning(() -> "⛔ Outbox #" + id
                                    + " abandonné (erreur permanente) : "
                                    + tbl + "/" + pk + " — " + msg);
                        } else {
                            incrementerTentative(conn, e.id);
                            int nouvellesTentatives = e.tentatives + 1;

                            if (nouvellesTentatives >= MAX_TENTATIVES) {
                                marquerEchec(conn, e.id,
                                        "Tentatives épuisées (" + nouvellesTentatives
                                        + "/" + MAX_TENTATIVES + ") : " + rejeu.message);
                                result.abandonnees++;
                                final long id = e.id;
                                final String tbl = e.table;
                                final String pk = e.primaryKey;
                                LOGGER.warning(() -> "⛔ Outbox #" + id
                                        + " abandonné (tentatives épuisées) : "
                                        + tbl + "/" + pk);
                            } else {
                                result.echecs++;
                                final long id = e.id;
                                final String tbl = e.table;
                                final String pk = e.primaryKey;
                                final int nt = nouvellesTentatives;
                                final String msg = rejeu.message;
                                LOGGER.warning(() -> "⚠️ Outbox #" + id
                                        + " échec (tentative " + nt
                                        + "/" + MAX_TENTATIVES + ") : "
                                        + tbl + "/" + pk + " — " + msg);
                            }
                        }
                    }
                } catch (SQLException ex) {
                    final long id = e.id;
                    LOGGER.fine(() -> "Erreur traitement outbox #" + id
                            + " : " + ex.getMessage());
                    result.echecs++;
                }
            }

            return result;

        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "❌ Erreur lecture outbox", ex);
            return null;
        } finally {
            closeQuietly(conn);
        }
    }

    /**
     * Lit un batch d'opérations PENDING.
     */
    private List<OutboxEntry> lirePending(Connection conn) throws SQLException {
        String sql = "SELECT id, table_name, operation, primary_key, row_data, "
                   + "target_source, tentatives, created_at "
                   + "FROM sync_outbox "
                   + "WHERE statut = 'PENDING' AND tentatives < ? "
                   + "ORDER BY created_at ASC LIMIT ?";

        List<OutboxEntry> entries = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, MAX_TENTATIVES);
            ps.setInt(2, BATCH_SIZE);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    OutboxEntry e = new OutboxEntry();
                    e.id           = rs.getLong("id");
                    e.table        = rs.getString("table_name");
                    e.operation    = rs.getString("operation");
                    e.primaryKey   = rs.getString("primary_key");
                    e.rowData      = rs.getString("row_data");
                    e.targetSource = rs.getString("target_source");
                    e.tentatives   = rs.getInt("tentatives");
                    e.createdAt    = rs.getTimestamp("created_at");
                    entries.add(e);
                }
            }
        }
        return entries;
    }

    /**
     * Rejoue une opération sur la connexion cible.
     */
    private RejeuResult rejouerOperation(OutboxEntry e) {
        Connection cible = null;
        try {
            cible = "REMOTE".equals(e.targetSource)
                    ? dbManager.getRemoteConnection()
                    : dbManager.getLocalConnection();

            // ✅ SyncService.rejouerOperationDirecte refait le contrôle d'appartenance
            boolean succes = SyncService.rejouerOperationDirecte(
                    cible, e.table, e.operation, e.primaryKey, e.rowData);

            if (succes) {
                return RejeuResult.succes();
            }

            boolean permanent = (e.tentatives + 1) >= MAX_TENTATIVES;
            return RejeuResult.echec(permanent,
                    "Échec rejeu (tentative " + (e.tentatives + 1) + ")");

        } catch (SQLException ex) {
            boolean permanent = DatabaseManager.estErreurPermanente(ex);
            return RejeuResult.echec(permanent,
                    "Connexion indisponible : " + ex.getMessage());
        } catch (RuntimeException ex) {
            boolean permanent = DatabaseManager.estErreurPermanente(ex);
            return RejeuResult.echec(permanent,
                    "Erreur inattendue : " + ex.getMessage());
        } finally {
            closeQuietly(cible);
        }
    }

    /**
     * Vérifie si l'opération est trop ancienne.
     */
    private boolean estTropVieux(OutboxEntry e) {
        if (e.createdAt == null) return false;
        long ageMs = System.currentTimeMillis() - e.createdAt.getTime();
        return ageMs > MAX_AGE_HOURS * 3600_000L;
    }

    // ============================================================
    // MARQUAGE EN BASE
    // ============================================================
    private void marquerTermine(Connection conn, long id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE sync_outbox SET "
                + " statut = 'DONE',"
                + " date_traitement = NOW(),"
                + " date_derniere_tentative = NOW(),"
                + " raison_echec = NULL "
                + "WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    private void marquerEchec(Connection conn, long id, String raison) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE sync_outbox SET "
                + " statut = 'FAILED',"
                + " date_traitement = NOW(),"
                + " date_derniere_tentative = NOW(),"
                + " raison_echec = ? "
                + "WHERE id = ?")) {
            ps.setString(1, tronquer(raison, 500));
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    private void incrementerTentative(Connection conn, long id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE sync_outbox SET "
                + " tentatives = tentatives + 1,"
                + " date_derniere_tentative = NOW() "
                + "WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    private String tronquer(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }

    private void closeQuietly(Connection conn) {
        if (conn == null) return;
        try { if (!conn.isClosed()) conn.close(); }
        catch (SQLException ignored) {}
    }

    // ============================================================
    // CLASSES INTERNES
    // ============================================================
    private static class OutboxEntry {
        long id;
        String table;
        String operation;
        String primaryKey;
        String rowData;
        String targetSource;
        int tentatives;
        java.sql.Timestamp createdAt;
    }

    private static class BatchResult {
        int totalTraite = 0;
        int ok = 0;
        int echecs = 0;
        int abandonnees = 0;
        int ignorees = 0;   // ✅ NOUVEAU
    }

    private static class RejeuResult {
        final boolean succes;
        final boolean permanent;
        final String message;

        private RejeuResult(boolean succes, boolean permanent, String message) {
            this.succes = succes;
            this.permanent = permanent;
            this.message = message;
        }

        static RejeuResult succes() {
            return new RejeuResult(true, false, null);
        }

        static RejeuResult echec(boolean permanent, String message) {
            return new RejeuResult(false, permanent, message);
        }
    }
}