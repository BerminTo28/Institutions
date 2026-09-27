import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Service de synchronisation LOCAL ↔ REMOTE.
 *
 * ✅ RÈGLE D'ISOLATION STRICTE :
 *   - La table `institutions` est synchronisée GLOBALEMENT
 *     (toutes les institutions, pour permettre la découverte inter-machines)
 *   - TOUTES les autres tables sont synchronisées FILTRÉES par `institution_id`
 *     (chaque poste ne reçoit QUE ses propres données)
 *   - Toute entrée dont l'`institution_id` ne correspond pas à celui du poste
 *     est REJETÉE, à la lecture comme à l'application
 */
public class SyncService {

    private static final Logger LOGGER = Logger.getLogger(SyncService.class.getName());

    // ============================================================
    // CONFIGURATION
    // ============================================================
    private static final int BATCH_SIZE                 = 5_000;
    private static final int CHANGELOG_RETENTION_DAYS   = 7;
    private static final int SYNC_ERRORS_RETENTION_DAYS = 30;
    private static final int PURGE_EVERY_N_CYCLES       = 20;
    private static final int MAX_ERREURS_PAR_CYCLE      = 50;
    private static final int MAX_ERREURS_PAR_ENTREE     = 3;
    private static final int COMMIT_EVERY_N             = 100;

    private final DatabaseManager dbManager;
    private final ObjectMapper jsonMapper = new ObjectMapper();

    /** ✅ Institution de cette instance de SyncService (obligatoire). */
    private final String monInstitutionId;

    private static volatile SyncService INSTANCE_PARTAGEE;
    private static final Map<String, Set<String>> CACHE_COLONNES = new ConcurrentHashMap<>();
    private final Map<String, Integer> compteurErreurs = new ConcurrentHashMap<>();
    private int cyclesDepuisPurge = 0;

    public static final List<String> TABLE_ORDER = List.of(
        MigrationManager.TABLE_INSTITUTIONS,
        MigrationManager.TABLE_ACCES_ADMIN,
        MigrationManager.TABLE_ANNEES_ACADEMIQUES,
        MigrationManager.TABLE_PERIODES,
        MigrationManager.TABLE_PROMOTIONS,
        MigrationManager.TABLE_CLASSES,
        MigrationManager.TABLE_MODULES,
        MigrationManager.TABLE_ETUDIANTS,
        MigrationManager.TABLE_PROFESSEURS,
        MigrationManager.TABLE_ACCES,
        MigrationManager.TABLE_ACCES_PROFESSEUR,
        MigrationManager.TABLE_OPTIONS,
        MigrationManager.TABLE_ASSISTANCE,
        MigrationManager.TABLE_PARAMETRES_INSCRIPTION,
        MigrationManager.TABLE_MATIERES,
        MigrationManager.TABLE_COURS_LIENS,
        MigrationManager.TABLE_PROFESSEUR_MATIERES,
        MigrationManager.TABLE_NOTES,
        MigrationManager.TABLE_ABSENCES,
        MigrationManager.TABLE_MESSAGES,
        MigrationManager.TABLE_ATTESTATIONS,
        MigrationManager.TABLE_PROGRAMMES,
        MigrationManager.TABLE_DOCUMENTS_PROFESSEUR,
        MigrationManager.TABLE_EVENEMENTS,
        MigrationManager.TABLE_FRAIS_SCOLAIRES,
        MigrationManager.TABLE_MODALITES_PAIEMENT,
        MigrationManager.TABLE_EXAMENS,
        MigrationManager.TABLE_PAIEMENTS_ETUDIANTS,
        MigrationManager.TABLE_RECETTES_EXTERNES,
        MigrationManager.TABLE_DEPENSES,
        MigrationManager.TABLE_EMPLOYES,
        MigrationManager.TABLE_BUDGET_CLASSE
    );

    private static final Map<String, List<String>> KEY_COLUMNS = new HashMap<>();
    static {
        KEY_COLUMNS.put(MigrationManager.TABLE_INSTITUTIONS,         List.of("institution_id"));
        KEY_COLUMNS.put(MigrationManager.TABLE_ACCES_ADMIN,           List.of("institution_id", "utilisateur_id"));
        KEY_COLUMNS.put(MigrationManager.TABLE_ANNEES_ACADEMIQUES,    List.of("institution_id", "annee_academique"));
        KEY_COLUMNS.put(MigrationManager.TABLE_PERIODES,              List.of("institution_id", "annee_academique", "periode"));
        KEY_COLUMNS.put(MigrationManager.TABLE_PROMOTIONS,            List.of("institution_id", "annee_academique", "promotion"));
        KEY_COLUMNS.put(MigrationManager.TABLE_CLASSES,               List.of("institution_id", "code_classe"));
        KEY_COLUMNS.put(MigrationManager.TABLE_ETUDIANTS,             List.of("institution_id", "numero_identifiant"));
        KEY_COLUMNS.put(MigrationManager.TABLE_PROFESSEURS,           List.of("institution_id", "numero_identifiant_professeur"));
        KEY_COLUMNS.put(MigrationManager.TABLE_MATIERES,              List.of("institution_id", "code_cours"));
        KEY_COLUMNS.put(MigrationManager.TABLE_ACCES,                 List.of("institution_id", "numero_identifiant"));
        KEY_COLUMNS.put(MigrationManager.TABLE_ACCES_PROFESSEUR,      List.of("institution_id", "numero_identifiant"));
        KEY_COLUMNS.put(MigrationManager.TABLE_OPTIONS,               List.of("institution_id", "option", "classe", "annee_academique"));
        KEY_COLUMNS.put(MigrationManager.TABLE_MODULES,               List.of("institution_id", "id"));
        KEY_COLUMNS.put(MigrationManager.TABLE_COURS_LIENS,           List.of("institution_id", "id"));
        KEY_COLUMNS.put(MigrationManager.TABLE_PROFESSEUR_MATIERES,   List.of("institution_id", "id"));
        KEY_COLUMNS.put(MigrationManager.TABLE_NOTES,                 List.of("institution_id", "id"));
        KEY_COLUMNS.put(MigrationManager.TABLE_ABSENCES,              List.of("institution_id", "id"));
        KEY_COLUMNS.put(MigrationManager.TABLE_MESSAGES,              List.of("institution_id", "id"));
        KEY_COLUMNS.put(MigrationManager.TABLE_ATTESTATIONS,          List.of("institution_id", "id"));
        KEY_COLUMNS.put(MigrationManager.TABLE_PROGRAMMES,            List.of("institution_id", "id"));
        KEY_COLUMNS.put(MigrationManager.TABLE_DOCUMENTS_PROFESSEUR,  List.of("institution_id", "id"));
        KEY_COLUMNS.put(MigrationManager.TABLE_EVENEMENTS,            List.of("institution_id", "id"));
        KEY_COLUMNS.put(MigrationManager.TABLE_FRAIS_SCOLAIRES,       List.of("institution_id", "id"));
        KEY_COLUMNS.put(MigrationManager.TABLE_MODALITES_PAIEMENT,    List.of("institution_id", "id"));
        KEY_COLUMNS.put(MigrationManager.TABLE_EXAMENS,               List.of("institution_id", "id"));
        KEY_COLUMNS.put(MigrationManager.TABLE_PAIEMENTS_ETUDIANTS,   List.of("institution_id", "id"));
        KEY_COLUMNS.put(MigrationManager.TABLE_RECETTES_EXTERNES,     List.of("institution_id", "id"));
        KEY_COLUMNS.put(MigrationManager.TABLE_DEPENSES,              List.of("institution_id", "id"));
        KEY_COLUMNS.put(MigrationManager.TABLE_EMPLOYES,              List.of("institution_id", "id"));
        KEY_COLUMNS.put(MigrationManager.TABLE_BUDGET_CLASSE,         List.of("institution_id", "id"));
        KEY_COLUMNS.put(MigrationManager.TABLE_ASSISTANCE,            List.of("institution_id", "id"));
        KEY_COLUMNS.put(MigrationManager.TABLE_PARAMETRES_INSCRIPTION, List.of("institution_id", "id"));
    }

    // ============================================================
    // CONSTRUCTEURS
    // ============================================================

    /**
     * ✅ Constructeur principal — l'institutionId est OBLIGATOIRE.
     */
    public SyncService(DatabaseManager dbManager, String institutionId) {
        if (dbManager == null) {
            throw new IllegalArgumentException("SyncService : dbManager obligatoire");
        }
        if (institutionId == null || institutionId.isBlank()) {
            throw new IllegalArgumentException(
                "SyncService : institutionId obligatoire (filtrage par institution)");
        }
        this.dbManager = dbManager;
        this.monInstitutionId = institutionId.trim();
        INSTANCE_PARTAGEE = this;
        LOGGER.info(() -> "🔧 SyncService initialisé pour institution=" + this.monInstitutionId);
    }

    /**
     * ⚠️ Constructeur legacy — déduit l'institution depuis la BD locale.
     *    À utiliser uniquement si tu ne peux pas modifier les appelants.
     */
    public SyncService(DatabaseManager dbManager) {
        this(dbManager, devinerInstitutionCourante(dbManager));
    }

    private static String devinerInstitutionCourante(DatabaseManager dbManager) {
        try (Connection c = dbManager.getLocalConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT institution_id FROM institutions LIMIT 1")) {
            if (rs.next()) {
                return rs.getString(1);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Impossible de déduire l'institution courante", e);
        }
        throw new IllegalStateException(
            "SyncService : institutionId introuvable — utilise le constructeur explicite");
    }

    // ============================================================
    // FILTRAGE PAR INSTITUTION
    // ============================================================

    /**
     * ✅ Détermine si une table doit être filtrée par institution.
     *    Exception : `institutions` → JAMAIS filtrée (découverte globale).
     */
    private boolean fautFiltrerParInstitution(String table) {
        if (table == null) return true;
        return !MigrationManager.TABLE_INSTITUTIONS.equals(table);
    }

    /**
     * ✅ Extrait l'`institution_id` d'une clé primaire composite.
     *    Retourne null si la table n'a pas d'`institution_id` dans sa PK.
     */
    private String extraireInstitutionId(String table, String pkValue) {
        if (table == null || pkValue == null) return null;

        List<String> pkCols = getKeyColumns(table);
        if (pkCols == null || pkCols.isEmpty()) return null;

        int idxInst = pkCols.indexOf("institution_id");
        if (idxInst < 0) return null;

        String[] parts = pkValue.split("\\|", -1);
        if (idxInst >= parts.length) return null;

        return parts[idxInst];
    }

    /**
     * ✅ Vérifie qu'une entrée appartient bien à mon institution.
     *    - `institutions` : toujours OK
     *    - autres tables : exige institution_id == monInstitutionId
     */
    private boolean entreeAutorisee(ChangeEntry e) {
        if (e == null || e.table == null) return false;
        if (!fautFiltrerParInstitution(e.table)) return true;

        String instId = extraireInstitutionId(e.table, e.pkValue);
        if (instId == null) {
            // Pas d'institution_id dans la PK → entrée suspecte
            LOGGER.warning(() -> String.format(
                "⛔ Entrée sans institution_id dans la PK : %s pk=%s",
                e.table, e.pkValue));
            return false;
        }
        return monInstitutionId.equals(instId);
    }

    // ====================================================================
    // POINT D'ENTRÉE
    // ====================================================================
    public void synchroniser() throws SQLException {
        synchroniser(null);
    }

    public void synchroniser(Set<String> tablesCibles) throws SQLException {
        final long startGlobal = System.currentTimeMillis();

        Connection localConn = null;
        Connection remoteConn = null;
        boolean erreursCeCycle = false;

        try {
            if (!dbManager.isLocalHealthy() || !dbManager.isRemoteHealthy()) {
                LOGGER.fine("⏭️ Sync ignorée : LOCAL ou REMOTE non sain");
                return;
            }
            if (!dbManager.isMigrationDistanteTerminee()) {
                LOGGER.fine("⏭️ Sync ignorée : migration distante non terminée");
                return;
            }

            try { localConn = dbManager.getLocalConnection(); } catch (SQLException e) { localConn = null; }
            try { remoteConn = dbManager.getRemoteConnection(); } catch (SQLException e) { remoteConn = null; }

            if (localConn == null || remoteConn == null) {
                LOGGER.fine("⏭️ Sync ignorée : connexion indisponible");
                return;
            }

            mettreAJourHeartbeat(localConn, null, null, null);

            final long cursorLocal  = lireCurseur(localConn, "last_local_id");
            final long cursorRemote = lireCurseur(remoteConn, "last_remote_id");

            // ✅ Lecture AVEC FILTRE par institution
            final ChangeLogBatch batchLocal  = lireChangeLog(localConn, cursorLocal, tablesCibles);
            final ChangeLogBatch batchRemote = lireChangeLog(remoteConn, cursorRemote, tablesCibles);

            final List<ChangeEntry> entriesLocal  = batchLocal.entries;
            final List<ChangeEntry> entriesRemote = batchRemote.entries;

            // ✅ Le curseur doit avancer jusqu'au maxIdVu (même pour les entrées ignorées)
            long dernierIdLocal  = Math.max(cursorLocal,  batchLocal.maxIdVu);
            long dernierIdRemote = Math.max(cursorRemote, batchRemote.maxIdVu);

            if (entriesLocal.isEmpty() && entriesRemote.isEmpty()) {
                if (!compteurErreurs.isEmpty()) compteurErreurs.clear();

                // ✅ Avancer quand même les curseurs si on a lu des entrées ignorées
                if (dernierIdLocal > cursorLocal) {
                    ecrireCurseur(localConn, "last_local_id", dernierIdLocal);
                    ecrireCurseur(remoteConn, "last_local_id", dernierIdLocal);
                }
                if (dernierIdRemote > cursorRemote) {
                    ecrireCurseur(localConn, "last_remote_id", dernierIdRemote);
                    ecrireCurseur(remoteConn, "last_remote_id", dernierIdRemote);
                }

                dbManager.signalerSyncReussie();
                mettreAJourHeartbeat(localConn, dernierIdLocal, dernierIdRemote, null);
                return;
            }

            LOGGER.info(String.format(
                "🔄 Sync [%s] — L→R=%d, R→L=%d",
                monInstitutionId, entriesLocal.size(), entriesRemote.size()));

            // ============================================================
            // 1) LOCAL → REMOTE
            // ============================================================
            int appliqueesLocal = 0, erreursLocal = 0;
            boolean arretLocal = false;

            remoteConn.setAutoCommit(false);
            int compteurLocal = 0;
            try {
                for (ChangeEntry e : entriesLocal) {
                    if (erreursLocal >= MAX_ERREURS_PAR_CYCLE) {
                        arretLocal = true;
                        break;
                    }
                    try {
                        appliquer(remoteConn, e);
                        appliqueesLocal++;
                        if (e.id > dernierIdLocal) dernierIdLocal = e.id;

                        if (++compteurLocal % COMMIT_EVERY_N == 0) {
                            remoteConn.commit();
                            final int c = compteurLocal;
                            LOGGER.fine(() -> "💾 Commit intermédiaire LOCAL→REMOTE ("
                                    + c + " entrées)");
                        }
                    } catch (SQLException | RuntimeException ex) {
                        erreursLocal++;
                        erreursCeCycle = true;
                        enregistrerErreur(e, ex, "LOCAL→REMOTE");
                    }
                }
                remoteConn.commit();
            } catch (SQLException | RuntimeException e) {
                try { remoteConn.rollback(); } catch (SQLException ignored) {}
                dernierIdLocal = cursorLocal;
                throw e;
            } finally {
                try { remoteConn.setAutoCommit(true); } catch (SQLException ignored) {}
            }

            // ============================================================
            // 2) REMOTE → LOCAL
            // ============================================================
            int appliqueesRemote = 0, erreursRemote = 0;
            boolean arretRemote = false;

            localConn.setAutoCommit(false);
            int compteurRemote = 0;
            try {
                for (ChangeEntry e : entriesRemote) {
                    if (erreursRemote >= MAX_ERREURS_PAR_CYCLE) {
                        arretRemote = true;
                        break;
                    }
                    try {
                        appliquer(localConn, e);
                        appliqueesRemote++;
                        if (e.id > dernierIdRemote) dernierIdRemote = e.id;

                        if (++compteurRemote % COMMIT_EVERY_N == 0) {
                            localConn.commit();
                            final int c = compteurRemote;
                            LOGGER.fine(() -> "💾 Commit intermédiaire REMOTE→LOCAL ("
                                    + c + " entrées)");
                        }
                    } catch (SQLException | RuntimeException ex) {
                        erreursRemote++;
                        erreursCeCycle = true;
                        enregistrerErreur(e, ex, "REMOTE→LOCAL");
                    }
                }
                localConn.commit();
            } catch (SQLException | RuntimeException e) {
                try { localConn.rollback(); } catch (SQLException ignored) {}
                dernierIdRemote = cursorRemote;
                throw e;
            } finally {
                try { localConn.setAutoCommit(true); } catch (SQLException ignored) {}
            }

            // ============================================================
            // 3) MISE À JOUR DES CURSEURS
            // ============================================================
            if (dernierIdLocal > cursorLocal) {
                ecrireCurseur(localConn, "last_local_id", dernierIdLocal);
                ecrireCurseur(remoteConn, "last_local_id", dernierIdLocal);
            }
            if (dernierIdRemote > cursorRemote) {
                ecrireCurseur(localConn, "last_remote_id", dernierIdRemote);
                ecrireCurseur(remoteConn, "last_remote_id", dernierIdRemote);
            }

            // ============================================================
            // 4) PURGE
            // ============================================================
            cyclesDepuisPurge++;
            if (cyclesDepuisPurge >= PURGE_EVERY_N_CYCLES) {
                cyclesDepuisPurge = 0;
                purgerChangeLogSynchronise(localConn);
                purgerChangeLogSynchronise(remoteConn);
                purgerSyncErrors(localConn);
            }

            if (!erreursCeCycle) compteurErreurs.clear();

            final int fAppLocal = appliqueesLocal;
            final int fErrLocal = erreursLocal;
            final int fAppRemote = appliqueesRemote;
            final int fErrRemote = erreursRemote;
            final boolean fArret = (arretLocal || arretRemote);
            final long fDuree = System.currentTimeMillis() - startGlobal;

            if (erreursCeCycle) {
                LOGGER.warning(() -> String.format(
                    "⚠️ Sync [%s] terminée avec erreurs (%d ms) — L→R: %d OK/%d err, R→L: %d OK/%d err%s",
                    monInstitutionId, fDuree, fAppLocal, fErrLocal, fAppRemote, fErrRemote,
                    fArret ? " [ARRÊT PARTIEL]" : ""));
            } else {
                LOGGER.info(() -> String.format(
                    "✅ Sync [%s] OK (%d ms) — L→R: %d, R→L: %d",
                    monInstitutionId, fDuree, fAppLocal, fAppRemote));
            }

            dbManager.signalerSyncReussie();
            mettreAJourHeartbeat(localConn, dernierIdLocal, dernierIdRemote, null);

        } catch (SQLException | RuntimeException e) {
            if (localConn != null) {
                mettreAJourHeartbeat(localConn, null, null, e.getMessage());
            }
            throw e;

        } finally {
            closeQuietly(localConn);
            closeQuietly(remoteConn);
        }
    }

    // ====================================================================
    // HEARTBEAT
    // ====================================================================
    private void mettreAJourHeartbeat(Connection conn, Long cursorLocal,
                                       Long cursorRemote, String erreur) {
        if (conn == null) return;
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE sync_heartbeat SET "
                + " last_attempt = NOW(),"
                + " last_success = CASE WHEN ? IS NULL THEN last_success ELSE NOW() END,"
                + " last_cursor_local = COALESCE(?, last_cursor_local),"
                + " last_cursor_remote = COALESCE(?, last_cursor_remote),"
                + " last_error = ? "
                + "WHERE id = 1")) {
            ps.setString(1, erreur);
            if (cursorLocal != null) ps.setLong(2, cursorLocal);
            else ps.setNull(2, java.sql.Types.BIGINT);
            if (cursorRemote != null) ps.setLong(3, cursorRemote);
            else ps.setNull(3, java.sql.Types.BIGINT);
            ps.setString(4, erreur);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.fine(() -> "Impossible d'écrire sync_heartbeat : " + e.getMessage());
        }
    }

    // ====================================================================
    // REJEU D'UNE OPÉRATION (appelé par SyncOutboxService)
    // ====================================================================
    public static boolean rejouerOperationDirecte(Connection cible, String table,
                                                    String operation, String pkValue,
                                                    String rowData) {
        if (cible == null || table == null || operation == null || pkValue == null) {
            LOGGER.warning("⚠️ rejouerOperationDirecte : paramètres null");
            return false;
        }

        SyncService instance = INSTANCE_PARTAGEE;
        if (instance == null) {
            LOGGER.warning("⚠️ rejouerOperationDirecte : SyncService non initialisé");
            return false;
        }

        try {
            ChangeEntry e = new ChangeEntry();
            e.table = table;
            e.operation = operation;
            e.pkValue = pkValue;
            e.rowData = rowData;

            // ✅ Contrôle d'appartenance avant d'appliquer
            if (!instance.entreeAutorisee(e)) {
                LOGGER.warning(() -> String.format(
                    "⛔ rejouerOperationDirecte refusé (autre institution) : %s pk=%s",
                    table, pkValue));
                return false;
            }

            instance.appliquer(cible, e);
            return true;

        } catch (SQLException | RuntimeException ex) {
            Throwable reel = unwrap(ex);
            String msg = (reel != null && reel.getMessage() != null)
                    ? reel.getMessage() : ex.getMessage();
            LOGGER.log(Level.WARNING, String.format(
                "❌ rejouerOperationDirecte %s op=%s pk=%s : %s",
                table, operation, pkValue, msg));
            return false;
        }
    }

    // ====================================================================
    // LECTURE DU CHANGE LOG — FILTRÉE PAR INSTITUTION
    // ====================================================================
    /**
     * ✅ Lit les entrées de `change_log` applicables à MON institution.
     *    - `institutions` : toutes les entrées sont conservées
     *    - autres tables : uniquement celles dont institution_id == monInstitutionId
     *
     *    Retourne aussi `maxIdVu` (le plus grand id lu, même si l'entrée a été
     *    ignorée) pour avancer correctement le curseur.
     */
    private ChangeLogBatch lireChangeLog(Connection conn, long sinceId,
                                           Set<String> tablesCibles) throws SQLException {

        ChangeLogBatch batch = new ChangeLogBatch();
        batch.entries = new ArrayList<>();
        batch.maxIdVu = sinceId;

        String sql = "SELECT id, table_name, operation, pk_value, row_data "
                   + "FROM change_log WHERE id > ? ORDER BY id ASC LIMIT " + BATCH_SIZE;

        int ignorees = 0;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, sinceId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong("id");
                    batch.maxIdVu = Math.max(batch.maxIdVu, id);

                    String table = rs.getString("table_name");
                    if (tablesCibles != null && !tablesCibles.contains(table)) continue;

                    ChangeEntry e = new ChangeEntry();
                    e.id = id;
                    e.table = table;
                    e.operation = rs.getString("operation");
                    e.pkValue = rs.getString("pk_value");
                    e.rowData = rs.getString("row_data");

                    // ✅ FILTRE : n'accepter que les entrées de mon institution
                    //    (sauf pour `institutions`)
                    if (!entreeAutorisee(e)) {
                        ignorees++;
                        continue;
                    }

                    batch.entries.add(e);
                }
            }
        }

        if (ignorees > 0) {
            final int n = ignorees;
            LOGGER.fine(() -> "🚫 " + n + " entrée(s) ignorée(s) (autres institutions)");
        }

        return batch;
    }

    // ====================================================================
    // APPLICATION D'UNE ENTRÉE — AVEC CONTRÔLE D'APPARTENANCE
    // ====================================================================
    private void appliquer(Connection cible, ChangeEntry e) throws SQLException {

        // ✅ GARDE-FOU : refuser toute entrée qui n'est pas de mon institution
        if (!entreeAutorisee(e)) {
            LOGGER.warning(() -> String.format(
                "⛔ Entrée refusée (autre institution) : %s op=%s pk=%s",
                e.table, e.operation, e.pkValue));
            return;
        }

        switch (e.operation) {
            case "INSERT", "UPDATE" -> appliquerUpsert(cible, e);
            case "DELETE" -> appliquerDelete(cible, e);
            default -> LOGGER.warning(String.format(
                "Opération inconnue : %s", e.operation));
        }
    }

    @SuppressWarnings("unchecked")
    private void appliquerUpsert(Connection cible, ChangeEntry e) throws SQLException {
        if (e.rowData == null) {
            LOGGER.warning(String.format(
                "row_data NULL pour %s op=%s pk=%s",
                e.table, e.operation, e.pkValue));
            return;
        }

        Map<String, Object> data;
        try {
            data = jsonMapper.readValue(e.rowData, LinkedHashMap.class);
        } catch (JsonProcessingException ex) {
            throw new SQLException("JSON invalide pour " + e.table
                    + " pk=" + e.pkValue, ex);
        }

        injecterColonnesPk(data, e);
        if (data.isEmpty()) return;

        data = filtrerColonnesExistantes(cible, e.table, data);
        if (data.isEmpty()) {
            LOGGER.fine(() -> "⏭️ Aucune colonne valide pour " + e.table
                    + " pk=" + e.pkValue + " — entrée ignorée");
            return;
        }

        final List<String> pkCols = getKeyColumns(e.table);
        final String[] pkValues = e.pkValue.split("\\|", -1);

        if (pkValues.length != pkCols.size()) {
            throw new SQLException("PK composite invalide pour " + e.table
                    + " : attendu=" + pkCols.size() + ", reçu=" + pkValues.length
                    + " (" + e.pkValue + ")");
        }

        final boolean existe = ligneExiste(cible, e.table, pkCols, pkValues);

        if (existe) {
            // ---------- UPDATE ----------
            data.remove("last_modified");
            for (String pk : pkCols) data.remove(pk);
            if (data.isEmpty()) return;

            StringBuilder sql = new StringBuilder("UPDATE "
                    + escapeIdentifier(e.table) + " SET ");
            List<String> cols = new ArrayList<>(data.keySet());
            for (int i = 0; i < cols.size(); i++) {
                sql.append(escapeIdentifier(cols.get(i))).append(" = ?");
                if (i < cols.size() - 1) sql.append(", ");
            }
            sql.append(" WHERE ");
            for (int i = 0; i < pkCols.size(); i++) {
                if (i > 0) sql.append(" AND ");
                sql.append(escapeIdentifier(pkCols.get(i))).append(" = ?");
            }

            try (PreparedStatement ps = cible.prepareStatement(sql.toString())) {
                int idx = 1;
                for (String col : cols) ps.setObject(idx++, data.get(col));
                for (String pkVal : pkValues) ps.setObject(idx++, pkVal);
                ps.executeUpdate();
            }

            LOGGER.fine(() -> "UPDATE " + e.table + " pk=" + e.pkValue);

        } else {
            // ---------- INSERT ----------
            Map<String, Object> dataComplet = completerColonnesObligatoires(
                    cible, e.table, data);

            if (dataComplet == null) {
                LOGGER.warning(() -> "⏭️ INSERT ignoré pour " + e.table
                        + " pk=" + e.pkValue
                        + " : colonnes NOT NULL manquantes");
                return;
            }

            StringBuilder sql = new StringBuilder("INSERT INTO "
                    + escapeIdentifier(e.table) + " (");
            List<String> cols = new ArrayList<>(dataComplet.keySet());
            for (int i = 0; i < cols.size(); i++) {
                sql.append(escapeIdentifier(cols.get(i)));
                if (i < cols.size() - 1) sql.append(", ");
            }
            sql.append(") VALUES (");
            for (int i = 0; i < cols.size(); i++) {
                sql.append("?");
                if (i < cols.size() - 1) sql.append(", ");
            }
            sql.append(")");

            try (PreparedStatement ps = cible.prepareStatement(sql.toString())) {
                int idx = 1;
                for (String col : cols) ps.setObject(idx++, dataComplet.get(col));
                ps.executeUpdate();
            }

            LOGGER.fine(() -> "INSERT " + e.table + " pk=" + e.pkValue);
        }
    }

    private Map<String, Object> completerColonnesObligatoires(
            Connection cible, String table, Map<String, Object> data) throws SQLException {

        List<String> colonnesObligatoires = getColonnesNotNullSansDefaut(cible, table);

        List<String> manquantes = new ArrayList<>();
        for (String col : colonnesObligatoires) {
            if (!data.containsKey(col) || data.get(col) == null) {
                manquantes.add(col);
            }
        }

        if (manquantes.isEmpty()) return data;

        LOGGER.warning(() -> "⚠️ Colonnes obligatoires manquantes pour " + table
                + " : " + manquantes);

        Map<String, Object> copie = new LinkedHashMap<>(data);
        List<String> irreparables = new ArrayList<>();

        for (String col : manquantes) {
            Object defaut = valeurParDefautMetier(table, col);
            if (defaut != null) {
                copie.put(col, defaut);
                LOGGER.fine(() -> "🔧 Colonne '" + col + "' injectée : " + defaut);
            } else {
                irreparables.add(col);
            }
        }

        if (!irreparables.isEmpty()) {
            LOGGER.warning(() -> "❌ Colonnes NOT NULL sans valeur pour " + table
                    + " : " + irreparables + " → INSERT ignoré");
            return null;
        }

        return copie;
    }

    private List<String> getColonnesNotNullSansDefaut(Connection cible, String table)
            throws SQLException {
        List<String> result = new ArrayList<>();
        DatabaseMetaData meta = cible.getMetaData();
        try (ResultSet rs = meta.getColumns(cible.getCatalog(), null, table, null)) {
            while (rs.next()) {
                String colName = rs.getString("COLUMN_NAME");
                String isNullable = rs.getString("IS_NULLABLE");
                String colDef = rs.getString("COLUMN_DEF");
                if ("NO".equalsIgnoreCase(isNullable) && colDef == null) {
                    result.add(colName.toLowerCase(Locale.ROOT));
                }
            }
        }
        return result;
    }

    private Object valeurParDefautMetier(String table, String colonne) {
        switch (table) {
            case "classes" -> {
                switch (colonne) {
                    case "nom_classe" -> { return ""; }
                    case "code_classe" -> { return "SANS_CODE"; }
                    case "niveau_classe" -> { return 1; }
                }
            }
            case "etudiants" -> {
                switch (colonne) {
                    case "nom" -> { return "INCONNU"; }
                    case "sexe" -> { return "NON_PRECISE"; }
                    case "periode" -> { return "N/A"; }
                }
            }
            case "professeurs" -> {
                switch (colonne) {
                    case "nom" -> { return "INCONNU"; }
                    case "sexe" -> { return "NON_PRECISE"; }
                }
            }
        }
        return null;
    }

    private void injecterColonnesPk(Map<String, Object> data, ChangeEntry e) {
        if (data == null || e == null) return;
        if (e.pkValue == null || e.pkValue.isBlank()) return;

        final List<String> pkCols = getKeyColumns(e.table);
        if (pkCols == null || pkCols.isEmpty()) return;

        final String[] pkParts = e.pkValue.split("\\|", -1);
        if (pkParts.length == 0) return;

        int injectees = 0;
        for (int i = 0; i < pkCols.size() && i < pkParts.length; i++) {
            final String col = pkCols.get(i);
            final String val = pkParts[i];

            if (col == null || col.isBlank()) continue;
            if (val == null || val.isBlank()) continue;

            final Object existing = data.get(col);
            if (existing != null && !(existing instanceof String s && s.isBlank())) {
                continue;
            }

            data.put(col, val);
            injectees++;

            final String table = e.table;
            LOGGER.fine(() -> "🔧 " + col + " injecté depuis pk_value : " + val
                    + " (table=" + table + ")");
        }

        if (injectees > 0) {
            final int n = injectees;
            final String table = e.table;
            final String pk = e.pkValue;
            LOGGER.fine(() -> "🔧 " + n + " colonne(s) PK injectée(s) pour "
                    + table + " (pk=" + pk + ")");
        }
    }

    private void appliquerDelete(Connection cible, ChangeEntry e) throws SQLException {
        final List<String> pkCols = getKeyColumns(e.table);
        final String[] pkValues = e.pkValue.split("\\|", -1);

        if (pkValues.length != pkCols.size()) {
            throw new SQLException("PK composite invalide pour " + e.table
                    + " : attendu=" + pkCols.size() + ", reçu=" + pkValues.length
                    + " (" + e.pkValue + ")");
        }

        StringBuilder sql = new StringBuilder("DELETE FROM "
                + escapeIdentifier(e.table) + " WHERE ");
        for (int i = 0; i < pkCols.size(); i++) {
            if (i > 0) sql.append(" AND ");
            sql.append(escapeIdentifier(pkCols.get(i))).append(" = ?");
        }

        try (PreparedStatement ps = cible.prepareStatement(sql.toString())) {
            for (int i = 0; i < pkValues.length; i++) ps.setString(i + 1, pkValues[i]);
            ps.executeUpdate();
        }

        LOGGER.fine(() -> "DELETE " + e.table + " pk=" + e.pkValue);
    }

    private boolean ligneExiste(Connection conn, String table,
                                 List<String> pkCols, String[] pkValues) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT 1 FROM "
                + escapeIdentifier(table) + " WHERE ");
        for (int i = 0; i < pkCols.size(); i++) {
            if (i > 0) sql.append(" AND ");
            sql.append(escapeIdentifier(pkCols.get(i))).append(" = ?");
        }
        sql.append(" LIMIT 1");

        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < pkValues.length; i++) ps.setString(i + 1, pkValues[i]);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    // ====================================================================
    // FILTRE DE COLONNES
    // ====================================================================
    private Set<String> getColonnesTable(Connection conn, String table) throws SQLException {
        String url;
        try {
            url = conn.getMetaData().getURL();
        } catch (SQLException e) {
            url = "unknown";
        }
        final String cacheKey = url + "|" + table;
        Set<String> cached = CACHE_COLONNES.get(cacheKey);
        if (cached != null) return cached;

        Set<String> colonnes = new HashSet<>();
        String sql = "SELECT COLUMN_NAME FROM information_schema.COLUMNS "
                   + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) colonnes.add(rs.getString(1).toLowerCase());
            }
        }

        if (colonnes.isEmpty()) {
            LOGGER.fine(() -> "⚠️ Table introuvable dans information_schema : " + table
                    + " → filtre désactivé pour cette entrée");
            return null;
        }

        CACHE_COLONNES.put(cacheKey, colonnes);
        return colonnes;
    }

    private Map<String, Object> filtrerColonnesExistantes(Connection conn, String table,
                                                           Map<String, Object> data)
            throws SQLException {
        Set<String> colonnes = getColonnesTable(conn, table);
        if (colonnes == null) return data;

        Map<String, Object> filtre = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            final String col = entry.getKey();
            if (colonnes.contains(col.toLowerCase())) {

                // ✅ GARDE-FOU : si on injecte institution_id, il doit être le mien
                if ("institution_id".equalsIgnoreCase(col)
                        && fautFiltrerParInstitution(table)) {
                    Object val = entry.getValue();
                    if (val != null && !monInstitutionId.equals(val.toString())) {
                        LOGGER.warning(() -> String.format(
                            "⛔ Tentative d'injection d'une autre institution dans %s : %s",
                            table, val));
                        return new LinkedHashMap<>(); // ← refuse tout
                    }
                }

                filtre.put(col, entry.getValue());
            } else {
                LOGGER.fine(() -> "⏭️ Colonne ignorée (absente de " + table + ") : " + col);
            }
        }
        return filtre;
    }

    public static void viderCacheColonnes() {
        CACHE_COLONNES.clear();
        LOGGER.fine("🧹 Cache des colonnes vidé");
    }

    // ====================================================================
    // CURSEURS
    // ====================================================================
    private long lireCurseur(Connection conn, String column) throws SQLException {
        if (!tableExists(conn, "sync_cursor")) return 0L;

        String sql = "SELECT " + column + " FROM sync_cursor WHERE id = 1";
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) return rs.getLong(1);
        }
        return 0L;
    }

    private void ecrireCurseur(Connection conn, String column, long valeur) throws SQLException {
        final boolean autoCommitOriginal = conn.getAutoCommit();
        try {
            conn.setAutoCommit(true);
            String sql = "UPDATE sync_cursor SET " + column + " = GREATEST(" + column + ", ?) "
                       + "WHERE id = 1";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, valeur);
                ps.executeUpdate();
            }
        } finally {
            try { conn.setAutoCommit(autoCommitOriginal); } catch (SQLException ignored) {}
        }
    }

    // ====================================================================
    // GESTION DES ERREURS
    // ====================================================================
    private static Throwable unwrap(Throwable t) {
        if (t == null) return null;
        if (t instanceof java.lang.reflect.UndeclaredThrowableException ute) {
            Throwable cause = ute.getUndeclaredThrowable();
            if (cause != null) return unwrap(cause);
        }
        if (t.getCause() != null && t.getCause() != t) return unwrap(t.getCause());
        return t;
    }

    private void enregistrerErreur(ChangeEntry e, Exception ex, String direction) {
        final Throwable reel = unwrap(ex);
        final boolean permanent = DatabaseManager.estErreurPermanente(reel);

        final String cle = e.table + "|" + e.pkValue + "|" + direction;
        final int count = compteurErreurs.merge(cle, 1, Integer::sum);

        if (count <= MAX_ERREURS_PAR_ENTREE) {
            final String msgErr = (reel != null && reel.getMessage() != null)
                    ? reel.getMessage() : "(pas de message)";
            final String className = (reel != null)
                    ? reel.getClass().getSimpleName() : ex.getClass().getSimpleName();
            final String permTag = permanent ? " [PERMANENT]" : "";

            LOGGER.warning(() -> String.format("❌ %s %s op=%s pk=%s : %s (%s)%s",
                    direction, e.table, e.operation, e.pkValue, msgErr, className, permTag));

        } else if (count == MAX_ERREURS_PAR_ENTREE + 1) {
            LOGGER.warning(() -> String.format(
                "⛔ Erreurs répétées pour %s pk=%s — logs suspendus",
                e.table, e.pkValue));
        }

        try (Connection conn = dbManager.getLocalConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO sync_errors "
                 + "(table_name, primary_key, operation, direction, erreur, change_log_id, date_erreur) "
                 + "VALUES (?, ?, ?, ?, ?, ?, NOW())")) {
            ps.setString(1, e.table);
            ps.setString(2, e.pkValue);
            ps.setString(3, e.operation);
            ps.setString(4, direction);

            String errMsg;
            if (reel != null && reel.getMessage() != null) {
                errMsg = reel.getMessage();
            } else if (reel != null) {
                errMsg = reel.getClass().getName();
            } else {
                errMsg = ex.getClass().getName();
            }
            ps.setString(5, errMsg);
            ps.setLong(6, e.id);
            ps.executeUpdate();

        } catch (SQLException logErr) {
            LOGGER.fine(() -> "Impossible d'enregistrer l'erreur : "
                    + logErr.getMessage());
        }
    }

    // ====================================================================
    // PURGE
    // ====================================================================
    private void purgerChangeLogSynchronise(Connection conn) throws SQLException {
        if (!tableExists(conn, "change_log")) return;
        if (!tableExists(conn, "sync_cursor")) return;

        final long curseurLocal  = lireCurseur(conn, "last_local_id");
        final long curseurRemote = lireCurseur(conn, "last_remote_id");
        final long curseurMax    = Math.max(curseurLocal, curseurRemote);

        if (curseurMax == 0) return;

        String sql = "DELETE FROM change_log "
                   + "WHERE id <= ? "
                   + "  AND created_at < DATE_SUB(NOW(), INTERVAL "
                   + CHANGELOG_RETENTION_DAYS + " DAY)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, curseurMax);
            int purged = ps.executeUpdate();
            if (purged > 0) {
                final int p = purged;
                LOGGER.fine(() -> "🧹 " + p + " entrées purgées du change_log");
            }
        }
    }

    private void purgerSyncErrors(Connection conn) throws SQLException {
        if (!tableExists(conn, "sync_errors")) return;
        String sql = "DELETE FROM sync_errors "
                   + "WHERE date_erreur < DATE_SUB(NOW(), INTERVAL "
                   + SYNC_ERRORS_RETENTION_DAYS + " DAY)";
        try (Statement st = conn.createStatement()) {
            int purged = st.executeUpdate(sql);
            if (purged > 0) {
                final int p = purged;
                LOGGER.fine(() -> "🧹 " + p + " entrées purgées de sync_errors");
            }
        }
    }

    // ====================================================================
    // UTILITAIRES
    // ====================================================================
      /**
     * ✅ Expose les colonnes de la PK métier d'une table.
     *    Utilisé par SyncOutboxService pour filtrer par institution.
     */
    public static List<String> getKeyColumns(String table) {
        List<String> cols = KEY_COLUMNS.get(table);
        if (cols == null || cols.isEmpty()) {
            LOGGER.warning(() -> "⚠️ Aucune PK déclarée pour " + table
                    + " → fallback sur (institution_id, id)");
            return List.of("institution_id", "id");
        }
        return cols;
    }
    
    private String escapeIdentifier(String ident) {
        if (ident == null) return "``";
        if (ident.startsWith("`") && ident.endsWith("`")) return ident;
        return "`" + ident.replace("`", "``") + "`";
    }

    private boolean tableExists(Connection conn, String tableName) {
        try {
            String sql = "SELECT COUNT(*) FROM information_schema.tables "
                    + "WHERE table_schema = DATABASE() AND table_name = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tableName);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() && rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            return false;
        }
    }

    private void closeQuietly(Connection conn) {
        if (conn == null) return;
        try { if (!conn.isClosed()) conn.close(); }
        catch (SQLException ignored) {}
    }

    // ====================================================================
    // CLASSES INTERNES
    // ====================================================================
    private static class ChangeEntry {
        long id;
        String table;
        String operation;
        String pkValue;
        String rowData;
    }


    private static class ChangeLogBatch {
        List<ChangeEntry> entries;
        long maxIdVu;
    }
}