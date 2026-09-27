import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DeleteTracker {

    private static final Logger LOGGER = Logger.getLogger(DeleteTracker.class.getName());

    private DeleteTracker() {}

    /**
     * Enregistre une suppression dans la table deleted_rows.
     * À appeler AVANT le DELETE réel, dans la MÊME transaction.
     *
     * @param conn     connexion de la base où la suppression a lieu
     * @param table    nom de la table concernée (ex: "etudiants")
     * @param pkValue  valeur de la clé primaire (ou "val1|val2" si composite)
     * @param source   "LOCAL" ou "REMOTE"
     */
    public static void enregistrerSuppression(Connection conn, String table,
                                                String pkValue, String source) throws SQLException {

        if (conn == null) {
            throw new SQLException("Connexion nulle — impossible d'enregistrer la suppression");
        }
        if (table == null || pkValue == null) {
            throw new SQLException("Paramètres invalides : table=" + table + ", pk=" + pkValue);
        }

        // ✅ 1) S'assurer que la table existe (idempotent)
        creerTableSiAbsente(conn);

        // ✅ 2) Normaliser la source
        String sourceNorm = (source == null || source.isBlank()) ? "LOCAL" : source;

        String sql = "INSERT INTO deleted_rows "
                + "(table_name, primary_key_value, source, deleted_at) "
                + "VALUES (?, ?, ?, NOW())";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, table);
            ps.setString(2, pkValue);
            ps.setString(3, sourceNorm);
            ps.executeUpdate();

            LOGGER.fine(() -> "🗑️ Tombstone enregistrée : " + table
                    + " pk=" + pkValue + " source=" + sourceNorm);

        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "❌ Échec enregistrement tombstone "
                    + table + " pk=" + pkValue, e);
            throw e;   // On propage : la transaction parente doit rollback
        }
    }

    /**
     * Crée la table deleted_rows si elle n'existe pas.
     * Structure alignée avec TableSchemaSQL.createDeletedRowsTable()
     * et MigrationManager.createSyncDeletionLogTable().
     */
    private static void creerTableSiAbsente(Connection conn) throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS deleted_rows ("
                + " id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                + " table_name VARCHAR(100) NOT NULL,"
                + " primary_key_value VARCHAR(255) NOT NULL,"
                + " deleted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                + " source VARCHAR(10) DEFAULT 'LOCAL',"
                + " last_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                + " INDEX idx_deleted_at (deleted_at),"
                + " INDEX idx_table_pk (table_name, primary_key_value)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";

        try (var st = conn.createStatement()) {
            st.executeUpdate(sql);
        }
    }
}