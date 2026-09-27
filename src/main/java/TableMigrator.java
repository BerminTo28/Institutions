import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class TableMigrator {

    private static final Logger LOGGER = Logger.getLogger(TableMigrator.class.getName());

    // ============================================================
    // MIGRATIONS SPÉCIFIQUES
    // ============================================================

    /**
     * Corrige la table matieres : remplace l'index unique simple sur code_cours
     * par un index composite (institution_id, code_cours).
     *
     * ✅ Vérifie d'abord si la PK est déjà composite (auquel cas l'index est redondant).
     */
    public static void migrateMatiereTable(Statement stmt) throws SQLException {
        String table = MigrationManager.TABLE_MATIERES;

        // ✅ Si la PK est déjà composite (institution_id, code_cours),
        // l'index unique est redondant → skip
        if (aPrimaryKeyComposite(stmt, table, "institution_id", "code_cours")) {
            LOGGER.fine(() -> "ℹ️ " + table + " : PK déjà composite, index unique redondant → skip");
            return;
        }

        // Vérifier si l'ancien index simple existe
        if (!indexExiste(stmt, table, "code_cours")) {
            return; // index déjà corrigé
        }

        LOGGER.info(() -> "🔄 Migration de " + table
                + " : suppression de la contrainte UNIQUE simple sur code_cours...");

        // Récupérer les clés étrangères dépendantes
        Map<String, String> fkMap = recupererFkReferencant(stmt, table, "code_cours");

        // Supprimer temporairement ces FK
        for (Map.Entry<String, String> entry : fkMap.entrySet()) {
            try {
                stmt.executeUpdate("ALTER TABLE " + entry.getKey()
                        + " DROP FOREIGN KEY " + entry.getValue());
            } catch (SQLException e) {
                LOGGER.warning(() -> "   ⚠️ Impossible de supprimer la FK "
                        + entry.getValue() + " : " + e.getMessage());
            }
        }

        // Drop de l'index protégé par try/catch
        try {
            stmt.executeUpdate("DROP INDEX code_cours ON " + table);
        } catch (SQLException e) {
            LOGGER.warning(() -> "   ⚠️ Impossible de supprimer l'index code_cours : " + e.getMessage());
        }

        // Création de l'index composite avec log en cas d'échec
        try {
            stmt.executeUpdate("CREATE UNIQUE INDEX unique_cours_institution ON "
                    + table + " (institution_id, code_cours)");
            LOGGER.info(() -> "   ✅ Index composite (institution_id, code_cours) créé sur " + table);
        } catch (SQLException e) {
            LOGGER.warning(() -> "   ⚠️ Impossible de créer l'index composite : " + e.getMessage());
        }

        // Recréation des FK avec drop préalable du même nom
        for (Map.Entry<String, String> entry : fkMap.entrySet()) {
            String tableEnfant = entry.getKey();
            String fkName = "fk_" + tableEnfant + "_code_cours";
            try {
                stmt.executeUpdate("ALTER TABLE " + tableEnfant + " DROP FOREIGN KEY " + fkName);
            } catch (SQLException ignored) {
                // la FK n'existait pas, c'est OK
            }
            try {
                stmt.executeUpdate("ALTER TABLE " + tableEnfant +
                        " ADD CONSTRAINT " + fkName +
                        " FOREIGN KEY (institution_id, code_cours) " +
                        "REFERENCES " + table + "(institution_id, code_cours) " +
                        "ON DELETE CASCADE ON UPDATE CASCADE");
            } catch (SQLException e) {
                LOGGER.warning(() -> "   ⚠️ Impossible de recréer la FK sur "
                        + tableEnfant + ": " + e.getMessage());
            }
        }
    }

    /**
     * Corrige la table annees_academiques : remplace l'index unique simple sur
     * annee_academique par un index composite (institution_id, annee_academique).
     *
     * ✅ Vérifie d'abord si la PK est déjà composite (auquel cas l'index est redondant).
     */
    public static void migrateAnneeAcademiqueTable(Statement stmt) throws SQLException {
        String table = MigrationManager.TABLE_ANNEES_ACADEMIQUES;

        // ✅ Si la PK est déjà composite (institution_id, annee_academique),
        // l'index unique est redondant → skip
        if (aPrimaryKeyComposite(stmt, table, "institution_id", "annee_academique")) {
            LOGGER.fine(() -> "ℹ️ " + table + " : PK déjà composite, index unique redondant → skip");
            return;
        }

        // Vérifier si l'ancien index simple existe
        if (!indexExiste(stmt, table, "annee_academique")) {
            return;
        }

        LOGGER.info(() -> "🔄 Migration durable de " + table + " vers un schéma composite...");

        Map<String, String> fkMap = recupererFkReferencant(stmt, table, "annee_academique");

        // Supprimer les anciennes FK simples
        for (Map.Entry<String, String> entry : fkMap.entrySet()) {
            try {
                stmt.executeUpdate("ALTER TABLE " + entry.getKey()
                        + " DROP FOREIGN KEY " + entry.getValue());
                LOGGER.info(() -> "   ✅ Ancien FK " + entry.getValue()
                        + " supprimé de " + entry.getKey());
            } catch (SQLException e) {
                LOGGER.warning(() -> "   ⚠️ Impossible de supprimer l'ancienne FK : " + e.getMessage());
            }
        }

        // Supprimer l'ancien index simple
        try {
            stmt.executeUpdate("DROP INDEX annee_academique ON " + table);
        } catch (SQLException e) {
            LOGGER.warning(() -> "   ⚠️ Impossible de supprimer l'index annee_academique : " + e.getMessage());
        }

        // Création de l'index composite avec log en cas d'échec
        try {
            stmt.executeUpdate("CREATE UNIQUE INDEX unique_annee_institution ON "
                    + table + " (institution_id, annee_academique)");
            LOGGER.info(() -> "   ✅ Index composite (institution_id, annee_academique) créé sur " + table);
        } catch (SQLException e) {
            LOGGER.warning(() -> "   ⚠️ Impossible de créer l'index composite : " + e.getMessage());
        }

        // Reconstruire les index locaux + FKs composites pour les tables enfants
        for (String tableEnfant : fkMap.keySet()) {
            String newFkName = "fk_" + tableEnfant + "_annee_composite";
            String indexName = "idx_" + tableEnfant + "_inst_annee";

            try {
                stmt.executeUpdate("CREATE INDEX " + indexName + " ON "
                        + tableEnfant + " (institution_id, annee_academique)");
            } catch (SQLException ignored) {
                // l'index existe peut-être déjà
            }

            try {
                stmt.executeUpdate("ALTER TABLE " + tableEnfant + " DROP FOREIGN KEY " + newFkName);
            } catch (SQLException ignored) {
                // la FK n'existait pas
            }

            try {
                stmt.executeUpdate("ALTER TABLE " + tableEnfant +
                        " ADD CONSTRAINT " + newFkName +
                        " FOREIGN KEY (institution_id, annee_academique) " +
                        "REFERENCES " + table + "(institution_id, annee_academique) " +
                        "ON DELETE CASCADE ON UPDATE CASCADE");
                LOGGER.info(() -> "   ✅ FK composite " + newFkName + " appliquée avec succès.");
            } catch (SQLException e) {
                LOGGER.warning(() -> "   ⚠️ Échec de création de la FK composite "
                        + newFkName + ": " + e.getMessage());
            }
        }
    }

    // ============================================================
    // MIGRATION professeur_matieres — ✅ SUPPRIMÉE
    // ============================================================
    // La méthode migrateProfesseurMatieresTable a été SUPPRIMÉE.
    // La migration de professeur_matieres est désormais gérée par
    // MigrationManager.migrerTablesVersPKComposite().

    // ============================================================
    // UTILITAIRES
    // ============================================================

    /**
     * Vérifie si un index existe dans une table.
     */
    private static boolean indexExiste(Statement stmt, String table, String indexName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM information_schema.statistics " +
                     "WHERE table_schema = DATABASE() " +
                     "AND table_name = '" + table + "' " +
                     "AND index_name = '" + indexName + "'";
        try (ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    /**
     * ✅ Vérifie si la PK d'une table est composite avec les colonnes données.
     */
    private static boolean aPrimaryKeyComposite(Statement stmt, String table,
                                                 String... colonnes) throws SQLException {
        String sql = "SELECT COUNT(*) FROM information_schema.key_column_usage " +
                     "WHERE table_schema = DATABASE() " +
                     "AND table_name = '" + table + "' " +
                     "AND constraint_name = 'PRIMARY'";
        try (ResultSet rs = stmt.executeQuery(sql)) {
            if (!rs.next() || rs.getInt(1) != colonnes.length) {
                return false;
            }
        }

        // Vérifier que les colonnes correspondent
        String sqlCols = "SELECT column_name FROM information_schema.key_column_usage " +
                         "WHERE table_schema = DATABASE() " +
                         "AND table_name = '" + table + "' " +
                         "AND constraint_name = 'PRIMARY' " +
                         "ORDER BY ordinal_position";
        try (ResultSet rs = stmt.executeQuery(sqlCols)) {
            int i = 0;
            while (rs.next()) {
                if (i >= colonnes.length || !colonnes[i].equals(rs.getString("column_name"))) {
                    return false;
                }
                i++;
            }
            return i == colonnes.length;
        }
    }

    private static Map<String, String> recupererFkReferencant(
            Statement stmt, String table, String colonne) throws SQLException {

        Map<String, String> fkMap = new HashMap<>();
        // Ajout de DISTINCT pour éviter les doublons de lignes liés aux clés composites
        String sql = "SELECT DISTINCT CONSTRAINT_NAME, TABLE_NAME " +
                     "FROM information_schema.KEY_COLUMN_USAGE " +
                     "WHERE REFERENCED_TABLE_SCHEMA = DATABASE() " +
                     "AND REFERENCED_TABLE_NAME = '" + table + "' " +
                     "AND REFERENCED_COLUMN_NAME = '" + colonne + "' " +
                     "AND CONSTRAINT_NAME <> 'PRIMARY'";
                     
        try (ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                String constraintName = rs.getString("CONSTRAINT_NAME");
                // Évite d'écraser si une table a plusieurs contraintes distinctes
                fkMap.put(tableName, constraintName);
            }
        }
        return fkMap;
    }
}