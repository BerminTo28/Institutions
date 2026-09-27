import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public class MigrationManager {

    private static final Logger LOGGER = Logger.getLogger(MigrationManager.class.getName());
    private final Connection connection;

    /**
     * ✅ Version actuelle du schéma.
     * Historique :
     *   3.1.2 → version précédente
     *   3.2.0 → ajout migrerSyncOutbox/... + validerSchemaFinal()
     *   3.2.1 → ajout DEFAULT '' sur colonnes NOT NULL fréquemment NULL
     *           (classes.nom_classe, etudiants.nom, professeurs.nom)
     */
    public static final String SCHEMA_VERSION = "3.2.2";

    // ============================================================
    // NOMS DES TABLES
    // ============================================================
    public static final String TABLE_INSTITUTIONS              = "institutions";
    public static final String TABLE_ACCES_ADMIN                = "acces_admin";
    public static final String TABLE_ANNEES_ACADEMIQUES         = "annees_academiques";
    public static final String TABLE_PERIODES                   = "periodes";
    public static final String TABLE_CLASSES                    = "classes";
    public static final String TABLE_MODULES                    = "modules";
    public static final String TABLE_PROMOTIONS                 = "promotions";
    public static final String TABLE_ETUDIANTS                  = "etudiants";
    public static final String TABLE_ACCES                      = "acces";
    public static final String TABLE_OPTIONS                    = "options";
    public static final String TABLE_ASSISTANCE                 = "assistance";
    public static final String TABLE_PARAMETRES_INSCRIPTION     = "parametres_inscription";
    public static final String TABLE_PROFESSEURS                = "professeurs";
    public static final String TABLE_COURS_LIENS                = "cours_liens";
    public static final String TABLE_ACCES_PROFESSEUR           = "acces_professeur";

    public static final String TABLE_QUIZ                  = "quiz";
    public static final String TABLE_QUIZ_SECTION          = "quiz_section";
    public static final String TABLE_QUIZ_QUESTION         = "quiz_question";
    public static final String TABLE_QUIZ_OPTION           = "quiz_option";
    public static final String TABLE_QUIZ_TENTATIVE        = "quiz_tentative";
    public static final String TABLE_QUIZ_REPONSE_ETUDIANT = "quiz_reponse_etudiant";

    public static final String TABLE_MATIERES                   = "matieres";
    public static final String TABLE_NOTES                      = "notes";
    public static final String TABLE_ABSENCES                   = "absences";
    public static final String TABLE_MESSAGES                   = "messages";
    public static final String TABLE_ATTESTATIONS               = "attestations";
    public static final String TABLE_PROFESSEUR_MATIERES        = "professeur_matieres";
    public static final String TABLE_PROGRAMMES                 = "programmes";
    public static final String TABLE_DOCUMENTS_PROFESSEUR       = "documents_professeur";
    public static final String TABLE_EVENEMENTS                 = "evenements";
    public static final String TABLE_FRAIS_SCOLAIRES            = "frais_scolaires";
    public static final String TABLE_MODALITES_PAIEMENT         = "modalites_paiement";
    public static final String TABLE_EXAMENS                    = "examens";
    public static final String TABLE_PAIEMENTS_ETUDIANTS        = "paiements_etudiants";
    public static final String TABLE_RECETTES_EXTERNES          = "recettes_externes";
    public static final String TABLE_DEPENSES                   = "depenses";
    public static final String TABLE_EMPLOYES                   = "employes";
    public static final String TABLE_BUDGET_CLASSE              = "budget_classe";

    public static final Set<String> TECHNICAL_TABLES = Set.of(
        "change_log", "sync_cursor", "sync_metadata",
        "sync_deletion_log", "deleted_rows", "schema_version",
        "sync_errors", "sync_outbox", "schema_migrations",
        "sync_heartbeat"
    );

    private static final String[] ALL_TABLES = {
        TABLE_INSTITUTIONS,
        TABLE_ACCES_ADMIN,
        TABLE_ANNEES_ACADEMIQUES,
        TABLE_PERIODES,
        TABLE_CLASSES,
        TABLE_MODULES,
        TABLE_PROMOTIONS,
        TABLE_ETUDIANTS,
        TABLE_ACCES,
        TABLE_OPTIONS,
        TABLE_ASSISTANCE,
        TABLE_PARAMETRES_INSCRIPTION,
        TABLE_PROFESSEURS,
        TABLE_COURS_LIENS,
        TABLE_ACCES_PROFESSEUR,

        TABLE_QUIZ,
        TABLE_QUIZ_SECTION,
        TABLE_QUIZ_QUESTION,
        TABLE_QUIZ_OPTION,
        TABLE_QUIZ_TENTATIVE,
        TABLE_QUIZ_REPONSE_ETUDIANT,

        TABLE_MATIERES,
        TABLE_NOTES,
        TABLE_ABSENCES,
        TABLE_MESSAGES,
        TABLE_ATTESTATIONS,
        TABLE_PROFESSEUR_MATIERES,
        TABLE_PROGRAMMES,
        TABLE_DOCUMENTS_PROFESSEUR,
        TABLE_EVENEMENTS,
        TABLE_FRAIS_SCOLAIRES,
        TABLE_MODALITES_PAIEMENT,
        TABLE_EXAMENS,
        TABLE_PAIEMENTS_ETUDIANTS,
        TABLE_RECETTES_EXTERNES,
        TABLE_DEPENSES,
        TABLE_EMPLOYES,
        TABLE_BUDGET_CLASSE
    };

    private static final Map<String, List<String>> KNOWN_PRIMARY_KEYS = new HashMap<>();
    static {
        KNOWN_PRIMARY_KEYS.put(TABLE_INSTITUTIONS,        List.of("institution_id"));
        KNOWN_PRIMARY_KEYS.put(TABLE_ACCES_ADMIN,         List.of("institution_id", "utilisateur_id"));
        KNOWN_PRIMARY_KEYS.put(TABLE_ANNEES_ACADEMIQUES,  List.of("institution_id", "annee_academique"));
        KNOWN_PRIMARY_KEYS.put(TABLE_PERIODES,            List.of("institution_id", "annee_academique", "periode"));
        KNOWN_PRIMARY_KEYS.put(TABLE_PROMOTIONS,          List.of("institution_id", "annee_academique", "promotion"));
        KNOWN_PRIMARY_KEYS.put(TABLE_CLASSES,             List.of("institution_id", "code_classe"));
        KNOWN_PRIMARY_KEYS.put(TABLE_OPTIONS,             List.of("institution_id", "option", "classe", "annee_academique"));
        KNOWN_PRIMARY_KEYS.put(TABLE_ETUDIANTS,           List.of("institution_id", "numero_identifiant"));
        KNOWN_PRIMARY_KEYS.put(TABLE_PROFESSEURS,         List.of("institution_id", "numero_identifiant_professeur"));
        KNOWN_PRIMARY_KEYS.put(TABLE_MATIERES,            List.of("institution_id", "code_cours"));
        KNOWN_PRIMARY_KEYS.put(TABLE_ACCES,               List.of("institution_id", "numero_identifiant"));
        KNOWN_PRIMARY_KEYS.put(TABLE_ACCES_PROFESSEUR,    List.of("institution_id", "numero_identifiant"));
        KNOWN_PRIMARY_KEYS.put(TABLE_MODULES,               List.of("institution_id", "id"));
        KNOWN_PRIMARY_KEYS.put(TABLE_COURS_LIENS,           List.of("institution_id", "id"));
        KNOWN_PRIMARY_KEYS.put(TABLE_PROFESSEUR_MATIERES,   List.of("institution_id", "id"));
        KNOWN_PRIMARY_KEYS.put(TABLE_NOTES,                 List.of("institution_id", "id"));
        KNOWN_PRIMARY_KEYS.put(TABLE_ABSENCES,              List.of("institution_id", "id"));
        KNOWN_PRIMARY_KEYS.put(TABLE_MESSAGES,              List.of("institution_id", "id"));
        KNOWN_PRIMARY_KEYS.put(TABLE_ATTESTATIONS,          List.of("institution_id", "id"));
        KNOWN_PRIMARY_KEYS.put(TABLE_PROGRAMMES,            List.of("institution_id", "id"));
        KNOWN_PRIMARY_KEYS.put(TABLE_DOCUMENTS_PROFESSEUR,  List.of("institution_id", "id"));
        KNOWN_PRIMARY_KEYS.put(TABLE_EVENEMENTS,            List.of("institution_id", "id"));
        KNOWN_PRIMARY_KEYS.put(TABLE_FRAIS_SCOLAIRES,       List.of("institution_id", "id"));
        KNOWN_PRIMARY_KEYS.put(TABLE_MODALITES_PAIEMENT,    List.of("institution_id", "id"));
        KNOWN_PRIMARY_KEYS.put(TABLE_EXAMENS,               List.of("institution_id", "id"));
        KNOWN_PRIMARY_KEYS.put(TABLE_PAIEMENTS_ETUDIANTS,   List.of("institution_id", "id"));
        KNOWN_PRIMARY_KEYS.put(TABLE_RECETTES_EXTERNES,     List.of("institution_id", "id"));
        KNOWN_PRIMARY_KEYS.put(TABLE_DEPENSES,              List.of("institution_id", "id"));
        KNOWN_PRIMARY_KEYS.put(TABLE_EMPLOYES,              List.of("institution_id", "id"));
        KNOWN_PRIMARY_KEYS.put(TABLE_BUDGET_CLASSE,         List.of("institution_id", "id"));
        KNOWN_PRIMARY_KEYS.put(TABLE_ASSISTANCE,            List.of("institution_id", "id"));
        KNOWN_PRIMARY_KEYS.put(TABLE_PARAMETRES_INSCRIPTION, List.of("institution_id", "id"));

        KNOWN_PRIMARY_KEYS.put(TABLE_QUIZ,                  List.of("institution_id", "id"));
        KNOWN_PRIMARY_KEYS.put(TABLE_QUIZ_SECTION,          List.of("institution_id", "id"));
        KNOWN_PRIMARY_KEYS.put(TABLE_QUIZ_QUESTION,         List.of("institution_id", "id"));
        KNOWN_PRIMARY_KEYS.put(TABLE_QUIZ_OPTION,           List.of("institution_id", "id"));
        KNOWN_PRIMARY_KEYS.put(TABLE_QUIZ_TENTATIVE,        List.of("institution_id", "id"));
        KNOWN_PRIMARY_KEYS.put(TABLE_QUIZ_REPONSE_ETUDIANT, List.of("institution_id", "id"));
    }

    /**
     * ✅ Colonnes NOT NULL fréquemment absentes de row_data,
     *    à corriger automatiquement avec un DEFAULT ''.
     *    Format : {table, colonne, définition_ALTER}
     */
    private static final String[][] COLONNES_DEFAULT_A_AJOUTER = {
        // ✅ Table classes — nom_classe obligatoire sans défaut
        {TABLE_CLASSES,     "nom_classe",    "VARCHAR(255) NOT NULL DEFAULT ''"},
        {TABLE_CLASSES,     "niveau_classe", "INT NOT NULL DEFAULT 1"},

        // ✅ Table etudiants
        {TABLE_ETUDIANTS,   "nom",           "VARCHAR(255) NOT NULL DEFAULT ''"},
        {TABLE_ETUDIANTS,   "sexe",          "VARCHAR(20) NOT NULL DEFAULT 'NON_PRECISE'"},
        {TABLE_ETUDIANTS,   "periode",       "VARCHAR(20) NOT NULL DEFAULT ''"},

        // ✅ Table professeurs
        {TABLE_PROFESSEURS, "nom",           "VARCHAR(255) NOT NULL DEFAULT ''"},
        {TABLE_PROFESSEURS, "sexe",          "VARCHAR(20) NOT NULL DEFAULT 'NON_PRECISE'"},

        // ✅ Table matieres
        {TABLE_MATIERES,    "nom_matiere",   "VARCHAR(255) NOT NULL DEFAULT ''"},

        // ✅ Table modules
        {TABLE_MODULES,     "nom_module",    "VARCHAR(255) NOT NULL DEFAULT ''"},

        // ✅ Table acces_admin
        {TABLE_ACCES_ADMIN, "nom",           "VARCHAR(255) NOT NULL DEFAULT ''"},
        {TABLE_ACCES_ADMIN, "prenom",        "VARCHAR(255) NOT NULL DEFAULT ''"},
        {TABLE_ACCES_ADMIN, "email",         "VARCHAR(255) NOT NULL DEFAULT ''"},
        {TABLE_ACCES_ADMIN, "mot_de_passe",  "VARCHAR(255) NOT NULL DEFAULT ''"},
    };

    public MigrationManager(Connection connection) {
        this.connection = connection;
    }

    // ============================================================
    // POINT D'ENTRÉE
    // ============================================================
    public void createAllTables(String source) throws SQLException {
        long start = System.currentTimeMillis();
        int etapes = 0;

        // 1) Tables techniques
        createSyncMetadataTable();
        createSyncDeletionLogTable();
        createChangeLogTable();
        createSyncCursorTable();
        createSyncErrorsTable();
        createSyncOutboxTable();
        createSchemaMigrationsTable();
        createSyncHeartbeatTable();
        etapes += 8;

        // 1bis) Migrations idempotentes tables techniques
        migrerSyncOutbox();
        migrerSyncErrors();
        migrerSyncCursor();
        migrerChangeLog();
        migrerSyncHeartbeat();
        etapes += 5;

        // ✅ 1ter) NOUVEAU : Correction des colonnes NOT NULL sans défaut
        //     AVANT le check estSchemaAJour(), pour que les bases
        //     déjà en 3.2.0 reçoivent quand même le fix.
        corrigerColonnesNotNullSansDefaut();
        etapes += 1;

        // 2) Vérification version
        try {
            if (estSchemaAJour()) {
                long duree = System.currentTimeMillis() - start;
                LOGGER.info(String.format(
                    "✅ Schéma déjà à jour (version %s) — skip migration métier (%d ms, source=%s)",
                    SCHEMA_VERSION, duree, source));
                validerSchemaFinal();
                return;
            }
        } catch (SQLException e) {
            LOGGER.fine(String.format(
                "Vérification version échouée, migration complète : %s", e.getMessage()));
        }

        LOGGER.info(String.format(
            "🔄 Migration du schéma vers version %s (source=%s)", SCHEMA_VERSION, source));

        // 3) Créer les tables métier
        try (Statement stmt = connection.createStatement()) {
            for (String table : ALL_TABLES) {
                String sql = TableSchemaSQL.getCreateTableSQL(table);
                if (sql != null) {
                    try {
                        stmt.executeUpdate(sql);
                        etapes++;
                    } catch (SQLException e) {
                        LOGGER.fine(String.format(
                                "CREATE TABLE %s : %s", table, e.getMessage()));
                    }
                } else {
                    LOGGER.fine(String.format(
                        "%s : SQL de création introuvable dans TableSchemaSQL", table));
                }
            }

            try {
                stmt.executeUpdate(TableSchemaSQL.createDeletedRowsTable());
            } catch (SQLException e) {
                LOGGER.fine(String.format("deleted_rows : %s", e.getMessage()));
            }
        }

        // 4) Migration PK composite
        try (Statement st = connection.createStatement()) {
            st.executeUpdate("SET FOREIGN_KEY_CHECKS = 0");
            try {
                migrerTablesVersPKComposite(source);
            } finally {
                st.executeUpdate("SET FOREIGN_KEY_CHECKS = 1");
            }
        } catch (SQLException e) {
            LOGGER.fine(String.format(
                "Migration PK : %s", e.getMessage()));
        }

        // 5) Triggers change_log
        if ("LOCAL".equals(source)) {
            ensureChangeLogTriggers();
        } else {
            LOGGER.fine("ℹ️ [REMOTE] Triggers change_log ignorés");
        }

        // 6) Colonnes de synchronisation
        ensureSyncColumns(source);

        // 7) Index
        ensureRequiredIndexes();

        // 8) Migrations spécifiques
        applySpecificMigrations();

        // 9) Migration modalites_paiement
        migrerModalitesPaiementIdVersUuid();

        // 10) Diagnostic (FINE)
        try {
            diagnostiquerSchema(source);
        } catch (SQLException e) {
            LOGGER.fine(String.format(
                "Diagnostic PK : %s", e.getMessage()));
        }

        // 11) Reset curseurs
        resetCursorsIfNeeded();

        // 12) Marquer le schéma à jour
        marquerSchemaAJour();

        // 13) Validation finale
        validerSchemaFinal();

        long duree = System.currentTimeMillis() - start;
        LOGGER.info(String.format(
            "✅ Migration terminée en %d ms (%d étapes, source=%s)",
            duree, etapes, source));
    }

    // ============================================================
    // ✅ CORRECTION DES COLONNES NOT NULL SANS DÉFAUT
    // ============================================================

    /**
     * ✅ Corrige les colonnes NOT NULL sans valeur par défaut
     *    qui posent problème à la sync (INSERT sans colonne → erreur MySQL).
     *
     *    Idempotent : ne touche que les colonnes encore problématiques.
     */
    private void corrigerColonnesNotNullSansDefaut() throws SQLException {
        int corrigees = 0;

        for (String[] entry : COLONNES_DEFAULT_A_AJOUTER) {
            final String table = entry[0];
            final String colonne = entry[1];
            final String definition = entry[2];

            if (!tableExists(table)) continue;
            if (!columnExiste(table, colonne)) continue;

            // Vérifier si la colonne est NOT NULL sans défaut
            if (!colonneSansDefaut(table, colonne)) continue;

            try (Statement st = connection.createStatement()) {
                st.executeUpdate("ALTER TABLE " + escapeIdentifier(table)
                        + " MODIFY COLUMN " + escapeIdentifier(colonne) + " "
                        + definition);
                corrigees++;
                LOGGER.fine(() -> "✅ Colonne corrigée : " + table + "." + colonne
                        + " → " + definition);
            } catch (SQLException e) {
                LOGGER.fine(() -> "⚠️ ALTER " + table + "." + colonne
                        + " : " + e.getMessage());
            }
        }

        if (corrigees > 0) {
            final int n = corrigees;
            LOGGER.info(() -> "✅ " + n + " colonne(s) NOT NULL corrigée(s) avec DEFAULT");
        }
    }

    /**
     * ✅ Vérifie si une colonne est NOT NULL sans valeur par défaut.
     */
    private boolean colonneSansDefaut(String table, String colonne) throws SQLException {
        String sql = "SELECT IS_NULLABLE, COLUMN_DEFAULT "
                   + "FROM information_schema.columns "
                   + "WHERE table_schema = DATABASE() "
                   + "  AND table_name = ? AND column_name = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, table);
            ps.setString(2, colonne);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String isNullable = rs.getString("IS_NULLABLE");
                    String colDef = rs.getString("COLUMN_DEFAULT");
                    return "NO".equalsIgnoreCase(isNullable) && colDef == null;
                }
            }
        }
        return false;
    }

    // ============================================================
    // MIGRATIONS IDEMPOTENTES TABLES TECHNIQUES (allégées)
    // ============================================================
    private void migrerSyncOutbox() throws SQLException {
        if (!tableExists("sync_outbox")) return;

        String[][] colonnesAttendues = {
            {"date_traitement",         "TIMESTAMP NULL DEFAULT NULL"},
            {"date_derniere_tentative", "TIMESTAMP NULL DEFAULT NULL"},
            {"raison_echec",            "VARCHAR(500) DEFAULT NULL"},
            {"tentatives",              "INT DEFAULT 0"},
            {"statut",                  "VARCHAR(20) DEFAULT 'PENDING'"},
            {"target_source",           "VARCHAR(10) NOT NULL DEFAULT 'REMOTE'"},
            {"row_data",                "JSON DEFAULT NULL"},
            {"created_at",              "TIMESTAMP DEFAULT CURRENT_TIMESTAMP"},
            {"updated_at",              "TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"},
        };

        try (Statement st = connection.createStatement()) {
            for (String[] col : colonnesAttendues) {
                if (!columnExiste("sync_outbox", col[0])) {
                    try {
                        st.executeUpdate("ALTER TABLE sync_outbox ADD COLUMN "
                                + escapeIdentifier(col[0]) + " " + col[1]);
                    } catch (SQLException e) {
                        LOGGER.fine(() -> "ALTER sync_outbox." + col[0] + " : " + e.getMessage());
                    }
                }
            }

            creerIndexSiAbsent("sync_outbox", "idx_statut", "statut");
            creerIndexSiAbsent("sync_outbox", "idx_table_pk", "table_name, primary_key");
            creerIndexSiAbsent("sync_outbox", "idx_created", "created_at");
        }
    }

    private void migrerSyncErrors() throws SQLException {
        if (!tableExists("sync_errors")) return;

        String[][] colonnesAttendues = {
            {"change_log_id", "BIGINT DEFAULT 0"},
            {"date_erreur",   "TIMESTAMP DEFAULT CURRENT_TIMESTAMP"},
            {"direction",     "VARCHAR(20) NOT NULL DEFAULT ''"},
            {"curseur",       "BIGINT DEFAULT 0"},
        };

        try (Statement st = connection.createStatement()) {
            for (String[] col : colonnesAttendues) {
                if (!columnExiste("sync_errors", col[0])) {
                    try {
                        st.executeUpdate("ALTER TABLE sync_errors ADD COLUMN "
                                + escapeIdentifier(col[0]) + " " + col[1]);
                    } catch (SQLException e) {
                        LOGGER.fine(() -> "ALTER sync_errors." + col[0] + " : " + e.getMessage());
                    }
                }
            }

            creerIndexSiAbsent("sync_errors", "idx_date_erreur", "date_erreur");
            creerIndexSiAbsent("sync_errors", "idx_table", "table_name");
        }
    }

    private void migrerSyncCursor() throws SQLException {
        if (!tableExists("sync_cursor")) return;

        String[][] colonnesAttendues = {
            {"last_local_id",  "BIGINT NOT NULL DEFAULT 0"},
            {"last_remote_id", "BIGINT NOT NULL DEFAULT 0"},
            {"updated_at",     "TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"},
        };

        try (Statement st = connection.createStatement()) {
            for (String[] col : colonnesAttendues) {
                if (!columnExiste("sync_cursor", col[0])) {
                    try {
                        st.executeUpdate("ALTER TABLE sync_cursor ADD COLUMN "
                                + escapeIdentifier(col[0]) + " " + col[1]);
                    } catch (SQLException e) {
                        LOGGER.fine(() -> "ALTER sync_cursor." + col[0] + " : " + e.getMessage());
                    }
                }
            }
        }
    }

    private void migrerChangeLog() throws SQLException {
        if (!tableExists("change_log")) return;

        String[][] colonnesAttendues = {
            {"table_name", "VARCHAR(100) NOT NULL DEFAULT ''"},
            {"operation",  "VARCHAR(20) NOT NULL DEFAULT 'INSERT'"},
            {"pk_value",   "VARCHAR(500) NOT NULL DEFAULT ''"},
            {"row_data",   "JSON DEFAULT NULL"},
            {"created_at", "TIMESTAMP DEFAULT CURRENT_TIMESTAMP"},
        };

        try (Statement st = connection.createStatement()) {
            for (String[] col : colonnesAttendues) {
                if (!columnExiste("change_log", col[0])) {
                    try {
                        st.executeUpdate("ALTER TABLE change_log ADD COLUMN "
                                + escapeIdentifier(col[0]) + " " + col[1]);
                    } catch (SQLException e) {
                        LOGGER.fine(() -> "ALTER change_log." + col[0] + " : " + e.getMessage());
                    }
                }
            }

            creerIndexSiAbsent("change_log", "idx_table_id", "table_name, id");
            creerIndexSiAbsent("change_log", "idx_created_at", "created_at");
            creerIndexSiAbsent("change_log", "idx_table", "table_name");
        }
    }

    private void migrerSyncHeartbeat() throws SQLException {
        if (!tableExists("sync_heartbeat")) return;

        String[][] colonnesAttendues = {
            {"last_attempt",       "TIMESTAMP NULL DEFAULT NULL"},
            {"last_success",       "TIMESTAMP NULL DEFAULT NULL"},
            {"last_cursor_local",  "BIGINT DEFAULT 0"},
            {"last_cursor_remote", "BIGINT DEFAULT 0"},
            {"last_error",         "TEXT DEFAULT NULL"},
            {"updated_at",         "TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"},
        };

        try (Statement st = connection.createStatement()) {
            for (String[] col : colonnesAttendues) {
                if (!columnExiste("sync_heartbeat", col[0])) {
                    try {
                        st.executeUpdate("ALTER TABLE sync_heartbeat ADD COLUMN "
                                + escapeIdentifier(col[0]) + " " + col[1]);
                    } catch (SQLException e) {
                        LOGGER.fine(() -> "ALTER sync_heartbeat." + col[0] + " : " + e.getMessage());
                    }
                }
            }
        }
    }

    // ============================================================
    // VALIDATION FINALE
    // ============================================================
    private void validerSchemaFinal() throws SQLException {
        List<String> erreurs = new ArrayList<>();

        String[] tablesRequises = {
            "sync_outbox", "sync_cursor", "sync_errors",
            "sync_heartbeat", "change_log", "schema_version",
            "schema_migrations", "sync_metadata", "sync_deletion_log"
        };
        for (String t : tablesRequises) {
            if (!tableExists(t)) erreurs.add("Table manquante : " + t);
        }

        if (tableExists("sync_outbox")) {
            String[] colonnes = {
                "id", "table_name", "operation", "primary_key",
                "row_data", "target_source", "statut", "tentatives",
                "date_traitement", "date_derniere_tentative"
            };
            for (String c : colonnes) {
                if (!columnExiste("sync_outbox", c)) {
                    erreurs.add("Colonne manquante : sync_outbox." + c);
                }
            }
        }

        if (tableExists("change_log")) {
            String[] colonnes = {"id", "table_name", "operation", "pk_value", "created_at"};
            for (String c : colonnes) {
                if (!columnExiste("change_log", c)) {
                    erreurs.add("Colonne manquante : change_log." + c);
                }
            }
        }

        if (tableExists("sync_cursor")) {
            String[] colonnes = {"id", "last_local_id", "last_remote_id"};
            for (String c : colonnes) {
                if (!columnExiste("sync_cursor", c)) {
                    erreurs.add("Colonne manquante : sync_cursor." + c);
                }
            }
        }

        if (!erreurs.isEmpty()) {
            StringBuilder sb = new StringBuilder("❌ Schéma invalide :");
            for (String e : erreurs) sb.append("\n  - ").append(e);
            String msg = sb.toString();
            LOGGER.severe(msg);
            throw new SQLException(msg);
        }

        LOGGER.fine("✅ Validation du schéma : OK");
    }

    // ============================================================
    // VERSIONING
    // ============================================================
    private boolean estSchemaAJour() throws SQLException {
        if (!tableExists("schema_version")) return false;

        String sql = "SELECT version FROM schema_version WHERE id = 1";
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) {
                return SCHEMA_VERSION.equals(rs.getString("version"));
            }
        }
        return false;
    }

    private void marquerSchemaAJour() throws SQLException {
        String createSql = "CREATE TABLE IF NOT EXISTS schema_version ("
                + " id INT PRIMARY KEY DEFAULT 1,"
                + " version VARCHAR(50) NOT NULL,"
                + " applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

        try (Statement st = connection.createStatement()) {
            st.executeUpdate(createSql);

            String upsert = "INSERT INTO schema_version (id, version) VALUES (1, ?) "
                    + "ON DUPLICATE KEY UPDATE version = ?, applied_at = NOW()";
            try (PreparedStatement ps = connection.prepareStatement(upsert)) {
                ps.setString(1, SCHEMA_VERSION);
                ps.setString(2, SCHEMA_VERSION);
                ps.executeUpdate();
            }
        }
    }

    private void createSchemaMigrationsTable() throws SQLException {
        String createSql = "CREATE TABLE IF NOT EXISTS schema_migrations ("
                + " version VARCHAR(50) PRIMARY KEY,"
                + " applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(createSql);
        }
    }

    // ============================================================
    // TABLES TECHNIQUES
    // ============================================================
    private void createSyncMetadataTable() throws SQLException {
        String createSql = "CREATE TABLE IF NOT EXISTS sync_metadata ("
                + " id INT PRIMARY KEY DEFAULT 1,"
                + " last_sync TIMESTAMP NOT NULL DEFAULT '1970-01-02 00:00:00',"
                + " updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(createSql);
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM sync_metadata WHERE id = 1")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    stmt.executeUpdate(
                        "INSERT INTO sync_metadata (id, last_sync) "
                        + "VALUES (1, '1970-01-02 00:00:00')");
                }
            }
        }
    }

    private void createSyncDeletionLogTable() throws SQLException {
        String createSql = "CREATE TABLE IF NOT EXISTS sync_deletion_log ("
                + " id INT AUTO_INCREMENT PRIMARY KEY,"
                + " table_name VARCHAR(100) NOT NULL,"
                + " record_id VARCHAR(255) NOT NULL,"
                + " deleted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                + " INDEX idx_table_name (table_name),"
                + " INDEX idx_deleted_at (deleted_at)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(createSql);
        }
    }

    private void createChangeLogTable() throws SQLException {
        String createSql = "CREATE TABLE IF NOT EXISTS change_log ("
                + " id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                + " table_name VARCHAR(100) NOT NULL,"
                + " operation ENUM('INSERT','UPDATE','DELETE') NOT NULL,"
                + " pk_value VARCHAR(500) NOT NULL,"
                + " row_data JSON DEFAULT NULL,"
                + " created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                + " INDEX idx_table_id (table_name, id),"
                + " INDEX idx_created_at (created_at),"
                + " INDEX idx_table (table_name)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(createSql);
        }
    }

    private void createSyncCursorTable() throws SQLException {
        String createSql = "CREATE TABLE IF NOT EXISTS sync_cursor ("
                + " id INT PRIMARY KEY DEFAULT 1,"
                + " last_local_id BIGINT NOT NULL DEFAULT 0,"
                + " last_remote_id BIGINT NOT NULL DEFAULT 0,"
                + " updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(createSql);
            stmt.executeUpdate(
                "INSERT INTO sync_cursor (id, last_local_id, last_remote_id) "
                + "VALUES (1, 0, 0) ON DUPLICATE KEY UPDATE id = id");
        }
    }

    private void createSyncErrorsTable() throws SQLException {
        String createSql = "CREATE TABLE IF NOT EXISTS sync_errors ("
                + " id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                + " table_name VARCHAR(100) NOT NULL,"
                + " primary_key VARCHAR(500) NOT NULL,"
                + " operation VARCHAR(20) NOT NULL,"
                + " direction VARCHAR(20) NOT NULL,"
                + " erreur TEXT,"
                + " change_log_id BIGINT DEFAULT 0,"
                + " date_erreur TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                + " INDEX idx_date_erreur (date_erreur),"
                + " INDEX idx_table (table_name)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(createSql);
        }
    }

    private void createSyncOutboxTable() throws SQLException {
        String createSql = "CREATE TABLE IF NOT EXISTS sync_outbox ("
                + " id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                + " table_name VARCHAR(100) NOT NULL,"
                + " operation VARCHAR(20) NOT NULL,"
                + " primary_key VARCHAR(500) NOT NULL,"
                + " row_data JSON DEFAULT NULL,"
                + " target_source VARCHAR(10) NOT NULL,"
                + " statut VARCHAR(20) DEFAULT 'PENDING',"
                + " tentatives INT DEFAULT 0,"
                + " raison_echec VARCHAR(500) DEFAULT NULL,"
                + " date_traitement TIMESTAMP NULL DEFAULT NULL,"
                + " date_derniere_tentative TIMESTAMP NULL DEFAULT NULL,"
                + " created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                + " updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                + " INDEX idx_statut (statut),"
                + " INDEX idx_created (created_at),"
                + " INDEX idx_table_pk (table_name, primary_key)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(createSql);
        }
    }

    private void createSyncHeartbeatTable() throws SQLException {
        String createSql = "CREATE TABLE IF NOT EXISTS sync_heartbeat ("
                + " id INT PRIMARY KEY DEFAULT 1,"
                + " last_attempt TIMESTAMP NULL DEFAULT NULL,"
                + " last_success TIMESTAMP NULL DEFAULT NULL,"
                + " last_cursor_local BIGINT DEFAULT 0,"
                + " last_cursor_remote BIGINT DEFAULT 0,"
                + " last_error TEXT DEFAULT NULL,"
                + " updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(createSql);
            stmt.executeUpdate(
                "INSERT INTO sync_heartbeat (id) VALUES (1) "
                + "ON DUPLICATE KEY UPDATE id = id");
        }
    }

    private void resetCursorsIfNeeded() throws SQLException {
        String versionSql = "SELECT version FROM schema_version WHERE id = 1";
        String oldVersion = null;
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(versionSql)) {
            if (rs.next()) oldVersion = rs.getString("version");
        } catch (SQLException ignored) {}

        boolean premiereInstallation = (oldVersion == null);
        boolean upgrade = !premiereInstallation && !SCHEMA_VERSION.equals(oldVersion);

        if (!premiereInstallation && !upgrade) return;

        try (Statement st = connection.createStatement()) {
            int rows = st.executeUpdate(
                "UPDATE sync_cursor SET last_local_id = 0, last_remote_id = 0 WHERE id = 1");
            if (rows > 0) {
                final String ov = oldVersion;
                LOGGER.info(() -> String.format(
                    "🔁 Reset automatique de sync_cursor (première install=%b, upgrade %s→%s)",
                    premiereInstallation, ov, SCHEMA_VERSION));
            }
        }
    }

    // ============================================================
    // INDEX
    // ============================================================
    private void ensureRequiredIndexes() throws SQLException {
        int ajoutes = 0;
        try (Statement stmt = connection.createStatement()) {

            String[][] specificIndexes = {
                {TABLE_ETUDIANTS, "idx_etudiants_inst", "institution_id"},
                {TABLE_ETUDIANTS, "idx_etudiants_matricule", "institution_id, matricule"},
                {TABLE_ETUDIANTS, "idx_etudiants_ninu", "institution_id, ninu"},
                {TABLE_PROFESSEURS, "idx_professeurs_inst", "institution_id"},
                {TABLE_PROFESSEURS, "idx_professeurs_email", "institution_id, email"},
                {TABLE_PROFESSEURS, "idx_professeurs_matricule", "institution_id, matricule"},
                {TABLE_CLASSES, "idx_classes_inst", "institution_id"},
                {TABLE_ACCES_ADMIN, "idx_acces_admin_inst", "institution_id"},
            };

            for (String[] def : specificIndexes) {
                String table = def[0], indexName = def[1], columns = def[2];
                if (!tableExists(table)) continue;
                if (indexExiste(table, indexName)) continue;

                String[] cols = columns.split(",\\s*");
                boolean toutesExistent = true;
                for (String col : cols) {
                    if (!columnExiste(table, col.trim())) {
                        toutesExistent = false;
                        break;
                    }
                }
                if (!toutesExistent) continue;

                try {
                    stmt.executeUpdate("CREATE INDEX " + indexName
                            + " ON " + table + " (" + columns + ")");
                    ajoutes++;
                } catch (SQLException e) {
                    LOGGER.fine(() -> "Index " + indexName + " : " + e.getMessage());
                }
            }

            for (String table : ALL_TABLES) {
                if (!tableExists(table)) continue;
                if (!columnExiste(table, "last_modified")) continue;
                if (indexExiste(table, "idx_last_modified")) continue;

                try {
                    stmt.executeUpdate("CREATE INDEX idx_last_modified ON "
                            + table + " (last_modified)");
                    ajoutes++;
                } catch (SQLException e) {
                    LOGGER.fine(() -> "idx_last_modified sur " + table + " : " + e.getMessage());
                }
            }
        }

        if (ajoutes > 0) {
            final int n = ajoutes;
            LOGGER.fine(() -> "✅ " + n + " index ajoutés.");
        }
    }

    private void creerIndexSiAbsent(String table, String indexName, String columns)
            throws SQLException {
        if (!tableExists(table)) return;
        if (indexExiste(table, indexName)) return;

        try (Statement st = connection.createStatement()) {
            st.executeUpdate("CREATE INDEX " + escapeIdentifier(indexName)
                    + " ON " + escapeIdentifier(table) + " (" + columns + ")");
        } catch (SQLException e) {
            LOGGER.fine(() -> "Index " + indexName + " : " + e.getMessage());
        }
    }

    private boolean indexExiste(String table, String indexName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM information_schema.statistics "
                   + "WHERE table_schema = DATABASE() "
                   + "  AND table_name = ? AND index_name = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, table);
            ps.setString(2, indexName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private boolean columnExiste(String table, String column) throws SQLException {
        String sql = "SELECT COUNT(*) FROM information_schema.columns "
                   + "WHERE table_schema = DATABASE() "
                   + "  AND table_name = ? AND column_name = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    // ============================================================
    // TRIGGERS change_log
    // ============================================================
    private void ensureChangeLogTriggers() throws SQLException {
        supprimerTriggersChangeLog();

        int crees = 0;
        for (String table : ALL_TABLES) {
            if (!tableExists(table)) continue;
            if (TECHNICAL_TABLES.contains(table)) continue;

            List<String> columns = getColumns(table);
            if (columns.isEmpty()) continue;

            List<String> pkCols = TableSchemaSQL.getPrimaryKeyColumnsForTrigger(table);
            if (pkCols.isEmpty()) {
                LOGGER.fine(() -> "⚠️ " + table + " : aucune PK déclarée pour trigger");
                continue;
            }

            boolean pkValide = true;
            for (String col : pkCols) {
                if (!columns.contains(col)) {
                    LOGGER.fine(() -> "⚠️ " + table + " : colonne PK " + col + " absente.");
                    pkValide = false;
                    break;
                }
            }
            if (!pkValide) continue;

            String pkExprNew = buildPkExpression("NEW", pkCols);
            String pkExprOld = buildPkExpression("OLD", pkCols);
            String jsonExprNew = buildJsonObject("NEW", columns);

            creerTriggerSiAbsent(
                "trg_" + table + "_cl_insert",
                "AFTER INSERT ON " + escapeIdentifier(table),
                "INSERT INTO change_log (table_name, operation, pk_value, row_data) "
                + "VALUES ('" + table + "', 'INSERT', " + pkExprNew + ", " + jsonExprNew + ")"
            );

            creerTriggerSiAbsent(
                "trg_" + table + "_cl_update",
                "AFTER UPDATE ON " + escapeIdentifier(table),
                "INSERT INTO change_log (table_name, operation, pk_value, row_data) "
                + "VALUES ('" + table + "', 'UPDATE', " + pkExprNew + ", " + jsonExprNew + ")"
            );

            creerTriggerSiAbsent(
                "trg_" + table + "_cl_delete",
                "AFTER DELETE ON " + escapeIdentifier(table),
                "INSERT INTO change_log (table_name, operation, pk_value, row_data) "
                + "VALUES ('" + table + "', 'DELETE', " + pkExprOld + ", NULL)"
            );

            crees += 3;
        }

        if (crees > 0) {
            final int n = crees;
            LOGGER.info(() -> "✅ " + n + " triggers change_log créés.");
        }
    }

    private String buildPkExpression(String prefix, List<String> pkCols) {
        if (pkCols.size() == 1) {
            return prefix + "." + escapeIdentifier(pkCols.get(0));
        }
        StringBuilder sb = new StringBuilder("CONCAT_WS('|'");
        for (String col : pkCols) {
            sb.append(", ").append(prefix).append(".").append(escapeIdentifier(col));
        }
        sb.append(")");
        return sb.toString();
    }

    private String buildJsonObject(String prefix, List<String> columns) {
        StringBuilder sb = new StringBuilder("JSON_OBJECT(");
        boolean first = true;
        for (String col : columns) {
            if (!first) sb.append(", ");
            sb.append("'").append(col.replace("'", "''")).append("', ");
            sb.append(prefix).append(".").append(escapeIdentifier(col));
            first = false;
        }
        sb.append(")");
        return sb.toString();
    }

    private void creerTriggerSiAbsent(String triggerName, String timingAndTable, String body) {
        try {
            if (triggerExiste(triggerName)) return;
        } catch (SQLException e) {
            LOGGER.fine(() -> "Vérification trigger " + triggerName + " : " + e.getMessage());
            return;
        }

        String sql = "CREATE TRIGGER " + escapeIdentifier(triggerName) + " "
                   + timingAndTable + " FOR EACH ROW " + body;

        try (Statement st = connection.createStatement()) {
            st.executeUpdate(sql);
        } catch (SQLException e) {
            LOGGER.fine(() -> "Trigger " + triggerName + " non créé : " + e.getMessage());
        }
    }

    private boolean triggerExiste(String triggerName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM information_schema.triggers "
                   + "WHERE trigger_schema = DATABASE() AND trigger_name = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, triggerName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    // ============================================================
    // MIGRATION MODALITES_PAIEMENT
    // ============================================================
    private void migrerModalitesPaiementIdVersUuid() throws SQLException {
        String table = TABLE_MODALITES_PAIEMENT;
        if (!tableExists(table)) return;

        String typeActuel = getColumnType(table, "id");
        if (typeActuel == null) return;

        if (typeActuel.toLowerCase().contains("varchar")) {
            return;
        }

        LOGGER.info(() -> "🔄 Migration " + table + " : id " + typeActuel + " → VARCHAR(36)");

        supprimerFkVersTable(table);

        try (Statement st = connection.createStatement()) {
            try {
                st.executeUpdate("ALTER TABLE " + table + " DROP PRIMARY KEY");
            } catch (SQLException e) {
                LOGGER.fine(() -> "DROP PRIMARY KEY : " + e.getMessage());
            }

            st.executeUpdate("ALTER TABLE " + table
                    + " MODIFY COLUMN id VARCHAR(36) NOT NULL");

            st.executeUpdate("ALTER TABLE " + table
                    + " ADD PRIMARY KEY (institution_id, id)");

            LOGGER.info(() -> "✅ " + table + " : migration id → VARCHAR(36) réussie");
        }
    }

    private String getColumnType(String table, String column) throws SQLException {
        String sql = "SELECT DATA_TYPE, CHARACTER_MAXIMUM_LENGTH "
                   + "FROM information_schema.columns "
                   + "WHERE table_schema = DATABASE() "
                   + "AND table_name = ? AND column_name = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String type = rs.getString("DATA_TYPE");
                    int len = rs.getInt("CHARACTER_MAXIMUM_LENGTH");
                    return len > 0 ? type + "(" + len + ")" : type;
                }
            }
        }
        return null;
    }

    private List<String> getColumns(String table) throws SQLException {
        List<String> cols = new ArrayList<>();
        String sql = "SELECT column_name FROM information_schema.columns "
                   + "WHERE table_schema = DATABASE() AND table_name = ? "
                   + "ORDER BY ordinal_position";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) cols.add(rs.getString("column_name"));
            }
        }
        return cols;
    }

    // ============================================================
    // COLONNES DE SYNCHRONISATION
    // ============================================================
    public void ensureSyncColumns(String sourceValue) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            Set<String> existantes = new HashSet<>();
            String sql = "SELECT table_name, column_name FROM information_schema.columns "
                       + "WHERE table_schema = DATABASE() "
                       + "AND column_name IN ('last_modified', 'source')";
            try (ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    existantes.add(rs.getString("table_name") + "."
                            + rs.getString("column_name"));
                }
            }

            for (String table : ALL_TABLES) {
                if (!tableExists(table)) continue;

                boolean manqueLastModified = !existantes.contains(table + ".last_modified");
                boolean manqueSource = !existantes.contains(table + ".source");
                if (!manqueLastModified && !manqueSource) continue;

                StringBuilder alter = new StringBuilder("ALTER TABLE ").append(table);
                if (manqueLastModified) {
                    alter.append(" ADD COLUMN last_modified TIMESTAMP "
                               + "DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");
                }
                if (manqueSource) {
                    if (manqueLastModified) alter.append(", ");
                    alter.append(" ADD COLUMN source VARCHAR(10) DEFAULT 'LOCAL'");
                }
                try {
                    stmt.executeUpdate(alter.toString());
                } catch (SQLException e) {
                    LOGGER.fine(() -> "ALTER " + table + " : " + e.getMessage());
                }
            }

            for (String table : ALL_TABLES) {
                if (!tableExists(table)) continue;
                try {
                    String countSql = "SELECT COUNT(*) FROM " + table
                                    + " WHERE source IS NULL OR source = ''";
                    boolean aCorriger = false;
                    try (ResultSet rs = stmt.executeQuery(countSql)) {
                        if (rs.next() && rs.getInt(1) > 0) aCorriger = true;
                    }
                    if (!aCorriger) continue;

                    stmt.executeUpdate("UPDATE " + table
                        + " SET source = '" + escapeSql(sourceValue) + "' "
                        + "WHERE source IS NULL OR source = ''");
                } catch (SQLException ignored) {}
            }
        }
    }

    // ============================================================
    // MIGRATIONS SPÉCIFIQUES
    // ============================================================
    private void applySpecificMigrations() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            TableMigrator.migrateMatiereTable(stmt);
            TableMigrator.migrateAnneeAcademiqueTable(stmt);
        } catch (SQLException e) {
            LOGGER.fine(() -> "Erreur migrations spécifiques : " + e.getMessage());
        }
    }

    // ============================================================
    // MIGRATION PK COMPOSITE
    // ============================================================
    private void migrerTablesVersPKComposite(String source) throws SQLException {
        int migrees = 0, erreurs = 0, dejaOk = 0;

        for (Map.Entry<String, List<String>> entry : KNOWN_PRIMARY_KEYS.entrySet()) {
            String table = entry.getKey();
            List<String> pkMetier = entry.getValue();

            if (TECHNICAL_TABLES.contains(table)) continue;
            if (TABLE_INSTITUTIONS.equals(table)) continue;

            try {
                boolean resultat = migrerTableVersPKComposite(table, pkMetier);
                if (resultat) migrees++; else dejaOk++;
            } catch (SQLException e) {
                erreurs++;
                LOGGER.fine(() -> "Migration " + table + " échouée : " + e.getMessage());
            }
        }

        if (erreurs > 0) {
            final int m = migrees, d = dejaOk, e = erreurs;
            final String s = source;
            LOGGER.warning(() -> String.format(
                "⚠️ [%s] Migration PK : %d migrées, %d déjà OK, %d erreurs",
                s, m, d, e));
        } else if (migrees > 0) {
            final int m = migrees, d = dejaOk;
            final String s = source;
            LOGGER.info(() -> String.format(
                "✅ [%s] Migration PK : %d migrées, %d déjà OK",
                s, m, d));
        }
    }

    private boolean migrerTableVersPKComposite(String table, List<String> pkMetier)
            throws SQLException {

        if (!tableExists(table)) return false;

        List<String> pkReelle = getPrimaryKeyColumnsFromSchema(table);

        if (pkReelle.equals(pkMetier)) return false;

        for (String col : pkMetier) {
            if (!columnExiste(table, col)) {
                throw new SQLException("Table " + table
                        + " : colonne PK métier manquante : " + col);
            }
        }

        int nbDoublons = compterDoublonsPK(table, pkMetier);
        if (nbDoublons > 0) {
            LOGGER.severe(String.format(
                "❌ [%s] %d doublon(s) PK — migration refusée.", table, nbDoublons));
            return false;
        }

        String backupName = table + "_backup_" + System.currentTimeMillis();
        boolean backupCree = false;
        try (Statement st = connection.createStatement()) {
            st.executeUpdate("CREATE TABLE " + backupName
                    + " AS SELECT * FROM " + table);
            backupCree = true;
        } catch (SQLException e) {
            LOGGER.fine(() -> "Sauvegarde " + table + " impossible : " + e.getMessage());
        }

        try {
            supprimerFkVersTable(table);

            boolean aColonneId = pkReelle.contains("id") && columnExiste(table, "id");
            boolean aColonneIdSeule = aColonneId && pkReelle.size() == 1;

            try (Statement st = connection.createStatement()) {

                if (aColonneIdSeule) {
                    st.executeUpdate("ALTER TABLE " + escapeIdentifier(table)
                            + " MODIFY COLUMN " + escapeIdentifier("id") + " VARCHAR(36) NOT NULL");
                }

                if (!pkReelle.isEmpty()) {
                    st.executeUpdate("ALTER TABLE " + escapeIdentifier(table)
                            + " DROP PRIMARY KEY");
                }

                if (aColonneId && !pkMetier.contains("id")) {
                    st.executeUpdate("ALTER TABLE " + escapeIdentifier(table)
                            + " DROP COLUMN " + escapeIdentifier("id"));
                }

                StringBuilder pkSql = new StringBuilder();
                for (int i = 0; i < pkMetier.size(); i++) {
                    if (i > 0) pkSql.append(", ");
                    pkSql.append(escapeIdentifier(pkMetier.get(i)));
                }
                st.executeUpdate("ALTER TABLE " + escapeIdentifier(table)
                        + " ADD PRIMARY KEY (" + pkSql + ")");
            }

            if (backupCree) {
                try (Statement st = connection.createStatement()) {
                    st.executeUpdate("DROP TABLE IF EXISTS " + escapeIdentifier(backupName));
                } catch (SQLException ignored) {}
            }

            return true;

        } catch (SQLException e) {
            if (backupCree) {
                LOGGER.severe(String.format(
                    "❌ [%s] Migration échouée. Backup conservé : %s",
                    table, backupName));
            }
            throw e;
        }
    }

    private int compterDoublonsPK(String table, List<String> pkMetier) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM (SELECT 1 FROM ")
            .append(escapeIdentifier(table))
            .append(" GROUP BY ");

        for (int i = 0; i < pkMetier.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append(escapeIdentifier(pkMetier.get(i)));
        }
        sql.append(" HAVING COUNT(*) > 1) AS d");

        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(sql.toString())) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private void supprimerFkVersTable(String table) throws SQLException {
        if (table == null || table.isBlank()) return;

        String sql = "SELECT TABLE_NAME, CONSTRAINT_NAME "
                   + "FROM information_schema.KEY_COLUMN_USAGE "
                   + "WHERE REFERENCED_TABLE_SCHEMA = DATABASE() "
                   + "  AND REFERENCED_TABLE_NAME = ? "
                   + "  AND CONSTRAINT_NAME IS NOT NULL";

        List<String[]> fks = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    String constraintName = rs.getString("CONSTRAINT_NAME");
                    if (tableName != null && constraintName != null) {
                        fks.add(new String[]{tableName, constraintName});
                    }
                }
            }
        }

        if (fks.isEmpty()) return;

        try (Statement st = connection.createStatement()) {
            for (String[] fk : fks) {
                try {
                    st.executeUpdate("ALTER TABLE " + escapeIdentifier(fk[0])
                            + " DROP FOREIGN KEY " + escapeIdentifier(fk[1]));
                } catch (SQLException e) {
                    LOGGER.fine(() -> "FK " + fk[0] + "." + fk[1] + " : " + e.getMessage());
                }
            }
        }
    }

    // ============================================================
    // DIAGNOSTIC (FINE)
    // ============================================================
    public void diagnostiquerSchema(String source) throws SQLException {
        int ok = 0, divergentes = 0, manquantes = 0;

        for (String table : ALL_TABLES) {
            if (!tableExists(table)) {
                LOGGER.fine(() -> table + " : INEXISTANTE");
                manquantes++;
                continue;
            }

            List<String> pkReelle = getPrimaryKeyColumnsFromSchema(table);
            List<String> pkAttendue = KNOWN_PRIMARY_KEYS.getOrDefault(table, List.of());

            if (pkAttendue.isEmpty()) {
                if (!pkReelle.isEmpty()) ok++;
                else divergentes++;
                continue;
            }

            if (pkReelle.equals(pkAttendue)) {
                ok++;
            } else {
                final String t = table;
                final List<String> r = pkReelle;
                final List<String> a = pkAttendue;
                LOGGER.fine(() -> t + " : PK=" + r + ", attendue=" + a);
                divergentes++;
            }
        }

        final int o = ok, d = divergentes, m = manquantes;
        final String s = source;
        LOGGER.fine(() -> String.format(
            "Diagnostic schéma (%s) : %d OK, %d divergentes, %d manquantes",
            s, o, d, m));
    }

    private List<String> getPrimaryKeyColumnsFromSchema(String table) throws SQLException {
        List<String> cols = new ArrayList<>();
        String sql = "SELECT column_name FROM information_schema.key_column_usage "
                   + "WHERE table_schema = DATABASE() "
                   + "  AND table_name = ? "
                   + "  AND constraint_name = 'PRIMARY' "
                   + "ORDER BY ordinal_position";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) cols.add(rs.getString("column_name"));
            }
        }
        return cols;
    }

    // ============================================================
    // UTILITAIRES
    // ============================================================
    public boolean tableExists(String tableName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM information_schema.tables "
                   + "WHERE table_schema = DATABASE() AND table_name = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    public void executeUpdate(String sql) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(sql);
        }
    }

    private String escapeSql(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("'", "''");
    }

    private String escapeIdentifier(String ident) {
        if (ident == null) return "``";
        return "`" + ident.replace("`", "``") + "`";
    }

    // ============================================================
    // SUPPRESSION TOTALE
    // ============================================================
    public void supprimerToutesLesTables(String source) throws SQLException {
        if (!"REMOTE".equals(source)) {
            throw new SQLException("Refus : suppression autorisée uniquement sur REMOTE");
        }

        LOGGER.warning(() -> "⚠️ SUPPRESSION DE TOUTES LES TABLES DE " + source);

        try (Statement st = connection.createStatement()) {
            st.executeUpdate("SET FOREIGN_KEY_CHECKS = 0");
        }

        supprimerTriggersChangeLog();

        try (Statement st = connection.createStatement()) {
            for (String table : ALL_TABLES) {
                try {
                    st.executeUpdate("DROP TABLE IF EXISTS " + escapeIdentifier(table));
                } catch (SQLException e) {
                    LOGGER.fine(() -> "Suppression " + table + " : " + e.getMessage());
                }
            }

            for (String tech : TECHNICAL_TABLES) {
                try {
                    st.executeUpdate("DROP TABLE IF EXISTS " + escapeIdentifier(tech));
                } catch (SQLException ignored) {}
            }
        }

        try (Statement st = connection.createStatement()) {
            st.executeUpdate("SET FOREIGN_KEY_CHECKS = 1");
        }
    }

    private void supprimerTriggersChangeLog() throws SQLException {
        String sql = "SELECT trigger_name FROM information_schema.triggers "
                   + "WHERE trigger_schema = DATABASE() "
                   + "  AND trigger_name LIKE 'trg\\_%\\_cl\\_%' ESCAPE '\\\\'";
        List<String> triggers = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) triggers.add(rs.getString("trigger_name"));
        }

        if (triggers.isEmpty()) return;

        try (Statement st = connection.createStatement()) {
            for (String trigger : triggers) {
                try {
                    st.executeUpdate("DROP TRIGGER IF EXISTS " + escapeIdentifier(trigger));
                } catch (SQLException e) {
                    LOGGER.fine(() -> "Trigger " + trigger + " : " + e.getMessage());
                }
            }
        }

        final int n = triggers.size();
        LOGGER.info(() -> "✅ " + n + " triggers change_log supprimés.");
    }
}