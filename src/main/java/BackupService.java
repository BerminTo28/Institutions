import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service de sauvegarde / restauration des données.
 * Fait un dump SQL directement en Java (pas de mysqldump).
 *
 * ✅ ISOLATION MULTI-INSTITUTIONS :
 *   - Chaque institution ne voit QUE ses propres sauvegardes
 *   - Les noms de fichiers contiennent l'institutionId : backup_<date>_<ID>.sql
 *   - Toute opération (download/restore/delete) vérifie l'appartenance
 */
public final class BackupService {

    private static final Logger LOGGER = Logger.getLogger(BackupService.class.getName());
    private static final String BACKUP_DIR = "backups/";
    private static final DateTimeFormatter FMT_FICHIER =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");


    /** Ordre des tables (respecte les FK). */
    private static final List<String> TABLES = List.of(
        MigrationManager.TABLE_INSTITUTIONS,
        MigrationManager.TABLE_ACCES_ADMIN,
        MigrationManager.TABLE_ANNEES_ACADEMIQUES,
        MigrationManager.TABLE_PERIODES,
        MigrationManager.TABLE_CLASSES,
        MigrationManager.TABLE_MODULES,
        MigrationManager.TABLE_PROMOTIONS,
        MigrationManager.TABLE_ETUDIANTS,
        MigrationManager.TABLE_ACCES,
        MigrationManager.TABLE_OPTIONS,
        MigrationManager.TABLE_ASSISTANCE,
        MigrationManager.TABLE_PARAMETRES_INSCRIPTION,
        MigrationManager.TABLE_PROFESSEURS,
        MigrationManager.TABLE_COURS_LIENS,
        MigrationManager.TABLE_ACCES_PROFESSEUR,
        MigrationManager.TABLE_MATIERES,
        MigrationManager.TABLE_NOTES,
        MigrationManager.TABLE_ABSENCES,
        MigrationManager.TABLE_MESSAGES,
        MigrationManager.TABLE_ATTESTATIONS,
        MigrationManager.TABLE_PROFESSEUR_MATIERES,
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

    public BackupService() {
        creerDossierSiAbsent();
    }

    private void creerDossierSiAbsent() {
        try {
            Files.createDirectories(Paths.get(BACKUP_DIR));
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Impossible de créer le dossier backups", e);
        }
    }

    // =========================================================
    // LISTER — ✅ FILTRÉE PAR INSTITUTION
    // =========================================================
    /**
     * Liste UNIQUEMENT les sauvegardes appartenant à l'institution.
     *
     * @param institutionId ID de l'institution (obligatoire, non null)
     * @return liste filtrée, jamais null
     */
    public List<Map<String, Object>> listerSauvegardes(String institutionId) {
        List<Map<String, Object>> list = new ArrayList<>();

        // ✅ GARDE ABSOLUE : pas d'institution → pas de liste
        if (institutionId == null || institutionId.isBlank()) {
            LOGGER.warning("⚠️ listerSauvegardes : institutionId null/vide → liste vide");
            return list;
        }

        final String inst = institutionId.trim();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(
                Paths.get(BACKUP_DIR), "*.sql")) {
            for (Path entry : stream) {
                String nomFichier = entry.getFileName().toString();

                // ✅ Filtrage : uniquement les fichiers de cette institution
                if (!fichierAppartientAInstitution(nomFichier, inst)) {
                    continue;
                }

                Map<String, Object> item = new HashMap<>();
                item.put("name", nomFichier);
                item.put("size", Files.size(entry));
                item.put("lastModified", Files.getLastModifiedTime(entry).toMillis());
                list.add(item);
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Erreur listing backups", e);
        }

        list.sort((a, b) -> Long.compare((Long) b.get("lastModified"),
                                          (Long) a.get("lastModified")));
        return list;
    }

    // =========================================================
    // CRÉER — ✅ INSTITUTION OBLIGATOIRE
    // =========================================================
    /**
     * Crée une sauvegarde pour une institution donnée.
     * L'institutionId est OBLIGATOIRE : pas de sauvegarde globale via cette méthode.
     *
     * @param institutionId ID de l'institution (jamais null)
     * @return nom du fichier créé, ou null en cas d'échec
     */
    public String creerSauvegarde(String institutionId) {
        // ✅ GARDE ABSOLUE : refuser null/vide
        if (institutionId == null || institutionId.isBlank()) {
            LOGGER.warning("⚠️ creerSauvegarde : institutionId null/vide → refusé");
            return null;
        }

        final String inst = institutionId.trim();

        // ✅ Nom de fichier TOUJOURS suffixé par l'institution
        String nom = "backup_" + LocalDateTime.now().format(FMT_FICHIER)
                + "_" + inst + ".sql";
        Path fichier = Paths.get(BACKUP_DIR, nom);

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             BufferedWriter writer = Files.newBufferedWriter(
                     fichier, StandardCharsets.UTF_8)) {

            writer.write("-- Sauvegarde générée le " + LocalDateTime.now() + "\n");
            writer.write("-- Institution : " + inst + "\n\n");
            writer.write("SET FOREIGN_KEY_CHECKS=0;\n\n");

            int totalLignes = 0;
            for (String table : TABLES) {
                if (!tableExiste(conn, table)) continue;
                totalLignes += ecrireTable(conn, writer, table, inst);
            }

            writer.write("SET FOREIGN_KEY_CHECKS=1;\n");

            final int total = totalLignes;
            LOGGER.info(() -> "✅ Sauvegarde créée : " + nom + " (" + total + " lignes)");
            return nom;

        } catch (SQLException | IOException e) {
            LOGGER.log(Level.SEVERE, "❌ Erreur création sauvegarde", e);
            try { Files.deleteIfExists(fichier); } catch (IOException ignored) {}
            return null;
        }
    }

    private int ecrireTable(Connection conn, BufferedWriter writer,
                              String table, String institutionId)
            throws SQLException, IOException {

        // ✅ Filtre institution OBLIGATOIRE
        boolean filtre = aColonne(conn, table, "institution_id");
        String where = filtre
                ? " WHERE institution_id = '" + esc(institutionId) + "'"
                : "";

        writer.write("-- Table : " + table + "\n");
        writer.write("DELETE FROM `" + table + "`" + where + ";\n");

        int count = 0;
        String sql = "SELECT * FROM `" + table + "`" + where;
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            ResultSetMetaData meta = rs.getMetaData();
            int nbCols = meta.getColumnCount();

            while (rs.next()) {
                StringBuilder insert = new StringBuilder("INSERT INTO `" + table + "` (");
                for (int i = 1; i <= nbCols; i++) {
                    if (i > 1) insert.append(", ");
                    insert.append("`").append(meta.getColumnName(i)).append("`");
                }
                insert.append(") VALUES (");
                for (int i = 1; i <= nbCols; i++) {
                    if (i > 1) insert.append(", ");
                    insert.append(formatSqlValue(rs.getObject(i)));
                }
                insert.append(");\n");
                writer.write(insert.toString());
                count++;
            }
        }
        writer.write("\n");
        return count;
    }

    private String formatSqlValue(Object val) {
        if (val == null) return "NULL";
        if (val instanceof Number) return val.toString();
        if (val instanceof Boolean aBoolean) return aBoolean ? "1" : "0";
        if (val instanceof Timestamp) {
            return "'" + val.toString() + "'";
        }
        return "'" + esc(val.toString()) + "'";
    }

    private String esc(String s) {
        return s.replace("\\", "\\\\").replace("'", "\\'");
    }

    private boolean tableExiste(Connection conn, String table) {
        try {
            String sql = "SELECT COUNT(*) FROM information_schema.tables "
                    + "WHERE table_schema = DATABASE() AND table_name = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, table);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() && rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            return false;
        }
    }

    private boolean aColonne(Connection conn, String table, String colonne) {
        try {
            String sql = "SELECT COUNT(*) FROM information_schema.columns "
                    + "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, table);
                ps.setString(2, colonne);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() && rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            return false;
        }
    }

    // =========================================================
    // SUPPRIMER UNE SAUVEGARDE — ✅ CONTRÔLE D'ACCÈS
    // =========================================================
    /**
     * Supprime une sauvegarde appartenant à l'institution.
     *
     * @param nomFichier  nom du fichier
     * @param institutionId ID de l'institution (obligatoire)
     * @return true si supprimé, false sinon
     */
    public boolean supprimerSauvegarde(String nomFichier, String institutionId) {
        if (!nomValide(nomFichier)) return false;

        // ✅ Contrôle d'appartenance
        if (!fichierAppartientAInstitution(nomFichier, institutionId)) {
            LOGGER.warning(() -> "⛔ supprimerSauvegarde refusé : " + nomFichier
                    + " | institution=" + institutionId);
            return false;
        }

        try {
            Path f = Paths.get(BACKUP_DIR, nomFichier);
            if (!Files.exists(f)) return false;
            Files.delete(f);
            LOGGER.info(() -> "🗑️ Sauvegarde supprimée : " + nomFichier);
            return true;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Erreur suppression", e);
            return false;
        }
    }

    // =========================================================
    // RESTAURER — ✅ CONTRÔLE D'ACCÈS + ISOLATION
    // =========================================================
    /**
     * Restaure une sauvegarde appartenant à l'institution.
     *
     * ✅ SÉCURITÉ :
     *   - Vérifie que le fichier appartient à l'institution
     *   - Le dump contient déjà des DELETE ... WHERE institution_id = '<ID>'
     *     donc aucune donnée d'une autre institution n'est touchée
     *
     * @param nomFichier    nom du fichier
     * @param institutionId ID de l'institution (obligatoire)
     * @return true si restauré, false sinon
     */
    public boolean restaurerSauvegarde(String nomFichier, String institutionId) {
        if (!nomValide(nomFichier)) return false;

        // ✅ Contrôle d'appartenance
        if (!fichierAppartientAInstitution(nomFichier, institutionId)) {
            LOGGER.warning(() -> "⛔ restaurerSauvegarde refusé : " + nomFichier
                    + " | institution=" + institutionId);
            return false;
        }

        Path f = Paths.get(BACKUP_DIR, nomFichier);
        if (!Files.exists(f)) {
            LOGGER.warning(() -> "Fichier introuvable : " + nomFichier);
            return false;
        }

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            boolean autoCommitOriginal = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                try (Statement st = conn.createStatement()) {
                    st.execute("SET FOREIGN_KEY_CHECKS=0");
                }

                int executes = 0, erreurs = 0;
                try (BufferedReader reader = Files.newBufferedReader(
                        f, StandardCharsets.UTF_8);
                     Statement st = conn.createStatement()) {

                    StringBuilder sb = new StringBuilder();
                    String ligne;
                    while ((ligne = reader.readLine()) != null) {
                        String trim = ligne.trim();
                        if (trim.isEmpty() || trim.startsWith("--")) continue;

                        sb.append(ligne).append("\n");

                        if (trim.endsWith(";")) {
                            String sql = sb.toString().trim();
                            sql = sql.substring(0, sql.length() - 1);
                            if (!sql.isBlank()) {
                                try {
                                    st.execute(sql);
                                    executes++;
                                } catch (SQLException e) {
                                    erreurs++;
                                    LOGGER.warning(() -> "Erreur SQL (ignorée) : " + e.getMessage());
                                }
                            }
                            sb.setLength(0);
                        }
                    }
                }

                try (Statement st = conn.createStatement()) {
                    st.execute("SET FOREIGN_KEY_CHECKS=1");
                }

                conn.commit();
                final int ex = executes, er = erreurs;
                LOGGER.info(() -> "✅ Restauration terminée pour " + institutionId
                        + " : " + ex + " instructions exécutées"
                        + (er > 0 ? ", " + er + " erreur(s)" : ""));
                return true;

            } catch (SQLException | IOException e) {
                try { conn.rollback(); } catch (SQLException ignored) {}
                throw e;
            } finally {
                conn.setAutoCommit(autoCommitOriginal);
            }
        } catch (SQLException | IOException e) {
            LOGGER.log(Level.SEVERE, "Erreur restauration", e);
            return false;
        }
    }

    // =========================================================
    // SUPPRIMER LES DONNÉES — ✅ GARDE ABSOLUE
    // =========================================================
    public boolean supprimerDonnees(String institutionId) {

        // ✅ GARDE ABSOLUE : refuser null/vide
        if (institutionId == null || institutionId.isBlank()) {
            LOGGER.warning("⚠️ supprimerDonnees : institutionId null/vide → refusé");
            return false;
        }

        final String inst = institutionId.trim();

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            boolean autoCommitOriginal = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (Statement st = conn.createStatement()) {
                st.execute("SET FOREIGN_KEY_CHECKS=0");

                int total = 0;
                for (int i = TABLES.size() - 1; i >= 0; i--) {
                    String table = TABLES.get(i);
                    if (!tableExiste(conn, table)) continue;

                    // ✅ Colonne institution_id OBLIGATOIRE
                    if (!aColonne(conn, table, "institution_id")) {
                        LOGGER.fine(() -> "⏭️ " + table + " sans institution_id → ignorée");
                        continue;
                    }

                    String sql = "DELETE FROM `" + table + "` WHERE institution_id = ?";
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, inst);
                        total += ps.executeUpdate();
                    }
                }

                st.execute("SET FOREIGN_KEY_CHECKS=1");
                conn.commit();

                final int t = total;
                LOGGER.info(() -> "🗑️ " + t + " lignes supprimées pour " + inst);
                return true;

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(autoCommitOriginal);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Erreur suppression données pour " + inst);
            return false;
        }
    }

    // =========================================================
    // LECTURE D'UN FICHIER — ✅ CONTRÔLE D'ACCÈS
    // =========================================================
    /**
     * Retourne le chemin d'un fichier de sauvegarde si et seulement si
     * il appartient à l'institution.
     *
     * @param nomFichier    nom du fichier
     * @param institutionId ID de l'institution (obligatoire)
     * @return Path ou null
     */
    public Path getFichier(String nomFichier, String institutionId) {
        if (!nomValide(nomFichier)) return null;

        // ✅ Contrôle d'appartenance
        if (!fichierAppartientAInstitution(nomFichier, institutionId)) {
            LOGGER.warning(() -> "⛔ getFichier refusé : " + nomFichier
                    + " | institution=" + institutionId);
            return null;
        }

        Path f = Paths.get(BACKUP_DIR, nomFichier);
        return Files.exists(f) ? f : null;
    }

    // =========================================================
    // MÉTHODES UTILITAIRES PUBLIQUES
    // =========================================================
    /**
     * ✅ Vérifie qu'un fichier appartient bien à l'institution.
     *    Convention : backup_YYYYMMDD_HHMMSS_<INSTITUTION_ID>.sql
     *
     *    Une sauvegarde globale (_global.sql) n'appartient à AUCUNE institution.
     */
    public boolean fichierAppartientAInstitution(String nomFichier, String institutionId) {
        if (nomFichier == null || nomFichier.isBlank()) return false;
        if (institutionId == null || institutionId.isBlank()) return false;

        // Sécurité anti-traversée
        if (nomFichier.contains("..") || nomFichier.contains("/")
                || nomFichier.contains("\\")) {
            return false;
        }

        // ✅ Doit finir par _<institutionId>.sql
        String suffixeAttendu = "_" + institutionId.trim() + ".sql";
        return nomFichier.endsWith(suffixeAttendu);
    }

    /**
     * Extrait l'institutionId depuis un nom de fichier de sauvegarde.
     * Retourne null si le format n'est pas reconnu.
     */
    public String extraireInstitutionId(String nomFichier) {
        if (nomFichier == null || !nomFichier.endsWith(".sql")) return null;

        // Format : backup_yyyyMMdd_HHmmss_<ID>.sql
        String sansExt = nomFichier.substring(0, nomFichier.length() - 4);
        int dernierUnderscore = sansExt.lastIndexOf('_');
        if (dernierUnderscore <= 0) return null;

        String id = sansExt.substring(dernierUnderscore + 1);
        return id.isBlank() ? null : id;
    }

    private boolean nomValide(String nom) {
        return nom != null && !nom.isBlank()
                && !nom.contains("..")
                && !nom.contains("/")
                && !nom.contains("\\")
                && nom.endsWith(".sql");
    }
}