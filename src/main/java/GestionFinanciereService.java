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

public class GestionFinanciereService {

    private static final Logger LOGGER = Logger.getLogger(GestionFinanciereService.class.getName());

    private final String institutionId;

    public GestionFinanciereService(String institutionId) {
        this.institutionId = institutionId;
    }

    // ============================================================
    // ANNÉES ACADÉMIQUES
    // ============================================================
    public List<String> getAnneesAcademiques() throws SQLException {
        List<String> annees = new ArrayList<>();
        String sql = "SELECT DISTINCT annee_academique FROM " + MigrationManager.TABLE_ANNEES_ACADEMIQUES
                + " WHERE institution_id = ? ORDER BY annee_academique DESC";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) annees.add(rs.getString("annee_academique"));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erreur getAnneesAcademiques", e);
        }
        return annees;
    }

    // ============================================================
    // PÉRIODES
    // ============================================================
    public List<String> getPeriodesParAnnee(String annee) throws SQLException {
        List<String> periodes = new ArrayList<>();
        String sql = "SELECT DISTINCT periode FROM " + MigrationManager.TABLE_PERIODES
                + " WHERE institution_id = ? AND annee_academique = ? ORDER BY periode";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            ps.setString(2, annee);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) periodes.add(rs.getString("periode"));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erreur getPeriodesParAnnee", e);
        }
        return periodes;
    }

    // ============================================================
    // CLASSES
    // ============================================================
    public List<String> getClasses() throws SQLException {
        List<String> classes = new ArrayList<>();
        String sql = "SELECT DISTINCT nom_classe FROM " + MigrationManager.TABLE_CLASSES
                + " WHERE institution_id = ? ORDER BY nom_classe";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) classes.add(rs.getString("nom_classe"));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erreur getClasses", e);
        }
        return classes;
    }

    public List<String> getClassesFiltrees(String annee, String periode) throws SQLException {
        List<String> classes = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT DISTINCT nom_classe FROM " + MigrationManager.TABLE_CLASSES
                + " WHERE institution_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(institutionId);

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (annee != null && !annee.isBlank() && columnExists("classes", "annee_academique")) {
                sql.append(" AND annee_academique = ?");
                args.add(annee);
            }
            if (periode != null && !periode.isBlank() && columnExists("classes", "periode")) {
                sql.append(" AND periode = ?");
                args.add(periode);
            }
            sql.append(" ORDER BY nom_classe");

            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                for (int i = 0; i < args.size(); i++) ps.setObject(i + 1, args.get(i));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) classes.add(rs.getString("nom_classe"));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erreur getClassesFiltrees", e);
        }
        return classes;
    }

    public List<String> getClassesUtilisees(String table, String annee, String mois) throws SQLException {
        List<String> classes = new ArrayList<>();
        if (!tableExists(table) || !columnExists(table, "classes")) return classes;

        StringBuilder sql = new StringBuilder(
                "SELECT DISTINCT classes FROM " + table + " WHERE institution_id = ? AND classes IS NOT NULL");
        List<Object> args = new ArrayList<>();
        args.add(institutionId);

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (annee != null && !annee.isBlank() && columnExists(table, "annee_academique")) {
                sql.append(" AND annee_academique = ?");
                args.add(annee);
            }
            if (mois != null && !mois.isBlank() && columnExists(table, "mois")) {
                sql.append(" AND mois = ?");
                args.add(mois);
            }

            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
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
        }
        return classes;
    }

    // ============================================================
    // ÉTUDIANTS
    // ============================================================
    public List<Map<String, Object>> getEtudiantsFiltres(String annee, String periode, String classe)
            throws SQLException {
        List<Map<String, Object>> result = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT numero_identifiant, nom, prenom, classe FROM " + MigrationManager.TABLE_ETUDIANTS
                + " WHERE institution_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(institutionId);

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (classe != null && !classe.isBlank()) {
                sql.append(" AND classe = ?");
                args.add(classe);
            }
            if (annee != null && !annee.isBlank() && columnExists("etudiants", "annee_academique")) {
                sql.append(" AND annee_academique = ?");
                args.add(annee);
            }
            sql.append(" ORDER BY nom, prenom");

            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                for (int i = 0; i < args.size(); i++) ps.setObject(i + 1, args.get(i));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> e = new LinkedHashMap<>();
                        e.put("id", rs.getString("numero_identifiant"));
                        e.put("nom", rs.getString("nom"));
                        e.put("prenom", rs.getString("prenom"));
                        e.put("classe", rs.getString("classe"));
                        result.add(e);
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erreur getEtudiantsFiltres", e);
        }
        return result;
    }

    public List<Map<String, Object>> getEtudiantsAvecFinances(String annee, String periode,
                                                               String classe, String etudiantId)
            throws SQLException {
        List<Map<String, Object>> result = new ArrayList<>();

        StringBuilder sql = new StringBuilder(
                "SELECT numero_identifiant, nom, prenom, classe, annee_academique "
                + "FROM " + MigrationManager.TABLE_ETUDIANTS
                + " WHERE institution_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(institutionId);

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (annee != null && !annee.isBlank() && columnExists("etudiants", "annee_academique")) {
                sql.append(" AND annee_academique = ?");
                args.add(annee);
            }
            if (classe != null && !classe.isBlank()) {
                sql.append(" AND classe = ?");
                args.add(classe);
            }
            if (etudiantId != null && !etudiantId.isBlank()) {
                sql.append(" AND numero_identifiant = ?");
                args.add(etudiantId);
            }
            sql.append(" ORDER BY classe, nom, prenom");

            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                for (int i = 0; i < args.size(); i++) ps.setObject(i + 1, args.get(i));

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String numId = rs.getString("numero_identifiant");
                        String cl = rs.getString("classe");
                        String an = rs.getString("annee_academique");

                        double fraisAnnuels = getFraisScolaire(cl);
                        double montantVerse = getMontantPayeParEtudiant(numId);

                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("numero_identifiant", numId);
                        row.put("annee", an != null ? an : "");
                        row.put("classe", cl != null ? cl : "");
                        row.put("nom", rs.getString("nom"));
                        row.put("prenom", rs.getString("prenom"));
                        row.put("montantVerse", montantVerse);
                        row.put("montantAPayer", Math.max(0, fraisAnnuels - montantVerse));
                        result.add(row);
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erreur getEtudiantsAvecFinances", e);
        }
        return result;
    }

    private double getMontantPayeParEtudiant(String etudiantId) throws SQLException {
        String sql = "SELECT COALESCE(SUM(montant), 0) FROM paiements_etudiants "
                + "WHERE institution_id = ? AND numero_identifiant = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            ps.setString(2, etudiantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble(1);
            }
        }
        return 0.0;
    }

    // ============================================================
    // INFOS FINANCIÈRES ÉTUDIANT
    // ============================================================
    public Map<String, Object> getInfosFinancieresEtudiant(String etudiantId, String annee, String classe)
            throws SQLException {
        double fraisAnnuels = 0.0;
        try {
            fraisAnnuels = getFraisScolaire(classe);
        } catch (SQLException ignored) {}

        double totalPaye = getMontantPayeParEtudiant(etudiantId);

        Map<String, Object> infos = new LinkedHashMap<>();
        infos.put("fraisAnnuels", fraisAnnuels);
        infos.put("totalPaye", totalPaye);
        infos.put("resteAPayer", Math.max(0, fraisAnnuels - totalPaye));
        return infos;
    }

    // ============================================================
    // FRAIS SCOLAIRES
    // ============================================================
    public double getFraisScolaire(String classe) throws SQLException {
        String sql = "SELECT COALESCE(SUM(montant), 0) FROM modalites_paiement "
                + "WHERE institution_id = ? AND classe = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            ps.setString(2, classe);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble(1);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erreur getFraisScolaire", e);
        }
        return 0.0;
    }

    public void setFraisScolaire(String classe, double montant) throws SQLException {
        String sql = "INSERT INTO frais_scolaires (institution_id, classe, montant) "
                + "VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE montant = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            ps.setString(2, classe);
            ps.setDouble(3, montant);
            ps.setDouble(4, montant);
            ps.executeUpdate();
        }
    }

    public List<String> getMoisDisponibles() throws SQLException {
        List<String> mois = new ArrayList<>();
        String sql = "SELECT DISTINCT DATE_FORMAT(date_recette, '%Y-%m') AS mois "
                + "FROM recettes_externes WHERE institution_id = ? "
                + "UNION "
                + "SELECT DISTINCT DATE_FORMAT(date_depense, '%Y-%m') "
                + "FROM depenses WHERE institution_id = ? "
                + "UNION "
                + "SELECT DISTINCT DATE_FORMAT(date_paiement, '%Y-%m') "
                + "FROM paiements_etudiants WHERE institution_id = ? "
                + "ORDER BY mois DESC";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            ps.setString(2, institutionId);
            ps.setString(3, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String m = rs.getString("mois");
                    if (m != null) mois.add(m);
                }
            }
        }
        return mois;
    }

    public List<String> getMoisDisponibles(String table, String annee, String classe) throws SQLException {
        List<String> mois = new ArrayList<>();
        if (!tableExists(table)) return mois;

        String colDate = columnDateForTable(table);
        String colMois = columnExists(table, "mois") ? "mois" : null;
        String colAnnee = columnExists(table, "annee_academique") ? "annee_academique" : null;

        if (colDate == null && colMois == null) return mois;

        StringBuilder sql = new StringBuilder("SELECT DISTINCT ");
        if (colMois != null) {
            sql.append(colMois).append(" AS mois");
        } else {
            sql.append("DATE_FORMAT(").append(colDate).append(", '%Y-%m') AS mois");
        }
        sql.append(" FROM ").append(table).append(" WHERE institution_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(institutionId);

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
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
        }
        return mois;
    }

    // ============================================================
    // MODALITÉS  ✅ CORRIGÉ : versement_num (pas numero_versement)
    // ============================================================
    public Map<Integer, Double> getModalites(String classe) throws SQLException {
        Map<Integer, Double> modalites = new LinkedHashMap<>();
        String sql = "SELECT versement_num, montant FROM modalites_paiement "
                + "WHERE institution_id = ? AND classe = ? ORDER BY versement_num";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            ps.setString(2, classe);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    modalites.put(rs.getInt("versement_num"), rs.getDouble("montant"));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erreur getModalites", e);
        }
        return modalites;
    }

    public void saveModalites(String classe, Map<Integer, Double> modalites) throws SQLException {
        String deleteSql = "DELETE FROM modalites_paiement WHERE institution_id = ? AND classe = ?";
        String insertSql = "INSERT INTO modalites_paiement "
                + "(institution_id, id, classe, versement_num, montant) "
                + "VALUES (?, ?, ?, ?, ?)";

        Connection conn = null;
        try {
            conn = DatabaseManager.getInstance().getConnection();
            conn.setAutoCommit(false);

            try (PreparedStatement del = conn.prepareStatement(deleteSql)) {
                del.setString(1, institutionId);
                del.setString(2, classe);
                del.executeUpdate();
            }

            try (PreparedStatement ins = conn.prepareStatement(insertSql)) {
                for (Map.Entry<Integer, Double> e : modalites.entrySet()) {
                    ins.setString(1, institutionId);
                    ins.setString(2, UUID.randomUUID().toString());
                    ins.setString(3, classe);
                    ins.setInt(4, e.getKey());
                    ins.setDouble(5, e.getValue());
                    ins.addBatch();
                }
                ins.executeBatch();
            }

            conn.commit();
        } catch (SQLException e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ignored) {}
            throw e;
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
                try { conn.close(); } catch (SQLException ignored) {}
            }
        }
    }

    // ============================================================
    // PAIEMENTS ÉTUDIANTS  ✅ CORRIGÉ : e.classe au lieu de p.classe
    // ============================================================
    public List<Map<String, Object>> getPaiementsEtudiants() throws SQLException {
        return getPaiementsEtudiants(null, null, null);
    }

    public List<Map<String, Object>> getPaiementsEtudiants(String annee, String mois, String classe)
            throws SQLException {
        List<Map<String, Object>> paiements = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT p.id, p.numero_identifiant, e.nom, e.prenom, " +
                "       e.classe AS etudiant_classe, " +
                "       p.montant, p.date_paiement, p.moyen_paiement, p.reference " +
                "FROM " + MigrationManager.TABLE_PAIEMENTS_ETUDIANTS + " p " +
                "LEFT JOIN " + MigrationManager.TABLE_ETUDIANTS + " e " +
                "  ON p.numero_identifiant = e.numero_identifiant " +
                "  AND p.institution_id = e.institution_id " +
                "WHERE p.institution_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(institutionId);

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (annee != null && !annee.isBlank()
                    && columnExists("paiements_etudiants", "annee_academique")) {
                sql.append(" AND p.annee_academique = ?");
                args.add(annee);
            }
            if (mois != null && !mois.isBlank()) {
                sql.append(" AND DATE_FORMAT(p.date_paiement, '%Y-%m') = ?");
                args.add(mois);
            }
            if (classe != null && !classe.isBlank()) {
                sql.append(" AND e.classe = ?");
                args.add(classe);
            }
            sql.append(" ORDER BY p.date_paiement DESC");

            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                for (int i = 0; i < args.size(); i++) ps.setObject(i + 1, args.get(i));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> p = new LinkedHashMap<>();
                        p.put("id", rs.getString("id"));
                        p.put("numero_identifiant", rs.getString("numero_identifiant"));
                        p.put("nom", rs.getString("nom"));
                        p.put("prenom", rs.getString("prenom"));
                        p.put("classe", rs.getString("etudiant_classe"));
                        p.put("montant", rs.getDouble("montant"));
                        p.put("date_paiement", rs.getString("date_paiement"));
                        p.put("moyen_paiement", rs.getString("moyen_paiement"));
                        p.put("reference", rs.getString("reference"));
                        paiements.add(p);
                    }
                }
            }
        }
        return paiements;
    }

    /** Ancienne signature (sans anneeAcademique) — conservée pour compatibilité. */
    public void enregistrerPaiementEtudiant(String etudiantId, double montant,
                                              String moyen, String reference) throws SQLException {
        enregistrerPaiementEtudiant(etudiantId, montant, moyen, reference, getAnneeCourante());
    }

    /** Nouvelle signature avec anneeAcademique. */
    public void enregistrerPaiementEtudiant(String etudiantId, double montant,
                                              String moyen, String reference,
                                              String anneeAcademique) throws SQLException {
        String sql = "INSERT INTO paiements_etudiants "
                + "(institution_id, id, numero_identifiant, montant, moyen_paiement, reference, "
                + " annee_academique, date_paiement, statut) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), 'VALIDÉ')";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            ps.setString(2, UUID.randomUUID().toString());
            ps.setString(3, etudiantId);
            ps.setDouble(4, montant);
            ps.setString(5, moyen);
            ps.setString(6, reference);
            ps.setString(7, anneeAcademique);
            ps.executeUpdate();
        }
    }

    public void supprimerPaiementEtudiant(String id) throws SQLException {
        String sql = "DELETE FROM paiements_etudiants WHERE id = ? AND institution_id = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, institutionId);
            ps.executeUpdate();
        }
    }

    // ============================================================
    // TOTAUX RECETTES INTERNES
    // ============================================================
    public double getTotalRecettesInternes() throws SQLException {
        String sql = "SELECT COALESCE(SUM(montant), 0) FROM paiements_etudiants "
                + "WHERE institution_id = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble(1);
            }
        }
        return 0.0;
    }

    public double getTotalRecettesInternes(String annee, String mois) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT COALESCE(SUM(montant), 0) FROM paiements_etudiants "
                + "WHERE institution_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(institutionId);

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (annee != null && !annee.isBlank()
                    && columnExists("paiements_etudiants", "annee_academique")) {
                sql.append(" AND annee_academique = ?");
                args.add(annee);
            }
            if (mois != null && !mois.isBlank()) {
                sql.append(" AND DATE_FORMAT(date_paiement, '%Y-%m') = ?");
                args.add(mois);
            }

            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                for (int i = 0; i < args.size(); i++) ps.setObject(i + 1, args.get(i));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getDouble(1);
                }
            }
        }
        return 0.0;
    }

    // ============================================================
    // RECETTES EXTERNES
    // ============================================================
    public List<Map<String, Object>> getRecettesExternes() throws SQLException {
        return getRecettesExternes(null, null);
    }

    public List<Map<String, Object>> getRecettesExternes(String annee, String mois) throws SQLException {
        List<Map<String, Object>> recettes = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT id, categorie, date_recette, montant, donateur, receveur, motif "
                + "FROM recettes_externes WHERE institution_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(institutionId);

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (annee != null && !annee.isBlank()
                    && columnExists("recettes_externes", "annee_academique")) {
                sql.append(" AND annee_academique = ?");
                args.add(annee);
            }
            if (mois != null && !mois.isBlank()) {
                sql.append(" AND DATE_FORMAT(date_recette, '%Y-%m') = ?");
                args.add(mois);
            }
            sql.append(" ORDER BY date_recette DESC");

            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                for (int i = 0; i < args.size(); i++) ps.setObject(i + 1, args.get(i));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> r = new LinkedHashMap<>();
                        r.put("id", rs.getString("id"));
                        r.put("categorie", rs.getString("categorie"));
                        r.put("date_recette", rs.getString("date_recette"));
                        r.put("montant", rs.getDouble("montant"));
                        r.put("donateur", rs.getString("donateur"));
                        r.put("receveur", rs.getString("receveur"));
                        r.put("motif", rs.getString("motif"));
                        recettes.add(r);
                    }
                }
            }
        }
        return recettes;
    }

    public void ajouterRecetteExterne(String categorie, java.util.Date date, double montant,
                                       String donateur, String receveur, String motif)
            throws SQLException {
        String sql = "INSERT INTO recettes_externes "
                + "(institution_id, id, categorie, date_recette, montant, donateur, receveur, motif) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            ps.setString(2, UUID.randomUUID().toString());
            ps.setString(3, categorie);
            ps.setDate(4, new java.sql.Date(date.getTime()));
            ps.setDouble(5, montant);
            ps.setString(6, donateur);
            ps.setString(7, receveur);
            ps.setString(8, motif);
            ps.executeUpdate();
        }
    }

    public void supprimerRecetteExterne(String id) throws SQLException {
        String sql = "DELETE FROM recettes_externes WHERE id = ? AND institution_id = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, institutionId);
            ps.executeUpdate();
        }
    }

    public double getTotalRecettesExternes() throws SQLException {
        String sql = "SELECT COALESCE(SUM(montant), 0) FROM recettes_externes "
                + "WHERE institution_id = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble(1);
            }
        }
        return 0.0;
    }

    public double getTotalRecettesExternes(String annee, String mois) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT COALESCE(SUM(montant), 0) FROM recettes_externes "
                + "WHERE institution_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(institutionId);

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (annee != null && !annee.isBlank()
                    && columnExists("recettes_externes", "annee_academique")) {
                sql.append(" AND annee_academique = ?");
                args.add(annee);
            }
            if (mois != null && !mois.isBlank()) {
                sql.append(" AND DATE_FORMAT(date_recette, '%Y-%m') = ?");
                args.add(mois);
            }

            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                for (int i = 0; i < args.size(); i++) ps.setObject(i + 1, args.get(i));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getDouble(1);
                }
            }
        }
        return 0.0;
    }

    // ============================================================
    // DÉPENSES — retourne List<Depenses> + filtres
    // ============================================================
    public List<Depenses> getDepenses() throws SQLException {
        return getDepenses(null, null, null);
    }

    public List<Depenses> getDepenses(String annee, String mois, String classe) throws SQLException {
        List<Depenses> depenses = new ArrayList<>();

        StringBuilder sql = new StringBuilder(
                "SELECT id, institution_id, annee_academique, mois, classes, " +
                "       nom_depense, type_depense, description_depense, responsable_depense, " +
                "       mode_paiement, numero_facture, ninu, matricule, justification_depense, " +
                "       montant, montant_total_depenses, date_depense, article, fournisseur, " +
                "       motif, executant, source " +
                "FROM depenses WHERE institution_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(institutionId);

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (annee != null && !annee.isBlank() && columnExists("depenses", "annee_academique")) {
                sql.append(" AND annee_academique = ?");
                args.add(annee);
            }
            if (mois != null && !mois.isBlank() && columnExists("depenses", "mois")) {
                sql.append(" AND mois = ?");
                args.add(mois);
            } else if (mois != null && !mois.isBlank()) {
                sql.append(" AND DATE_FORMAT(date_depense, '%Y-%m') = ?");
                args.add(mois);
            }
            if (classe != null && !classe.isBlank() && columnExists("depenses", "classes")) {
                sql.append(" AND FIND_IN_SET(?, REPLACE(classes, ' ', '')) > 0");
                args.add(classe);
            }
            sql.append(" ORDER BY date_depense DESC");

            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                for (int i = 0; i < args.size(); i++) ps.setObject(i + 1, args.get(i));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        depenses.add(mapDepense(rs));
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erreur getDepenses (filtres)", e);
        }
        return depenses;
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
        try { d.setMontantTotalDepenses(rs.getDouble("montant_total_depenses")); } catch (SQLException ignored) {}
        d.setDateDepense(rs.getDate("date_depense"));
        d.setFournisseur(getSafe(rs, "fournisseur"));
        d.setMotif(getSafe(rs, "motif"));
        d.setSource(getSafe(rs, "source"));
        return d;
    }

    public void ajouterDepense(Depenses d) throws SQLException {
        if (d == null) throw new IllegalArgumentException("Depenses null");
        if (d.getId() == null || d.getId().isBlank()) {
            d.setId(UUID.randomUUID().toString());
        }
        String sql = "INSERT INTO depenses "
                + "(institution_id, id, annee_academique, mois, classes, "
                + " nom_depense, type_depense, description_depense, responsable_depense, "
                + " mode_paiement, numero_facture, ninu, matricule, justification_depense, "
                + " montant, montant_total_depenses, date_depense, article, fournisseur, "
                + " motif, executant, source) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
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
            ps.setString(22, "LOCAL");
            ps.executeUpdate();
        }
    }

    public void ajouterDepense(String executant, String motif, double montant,
                                java.util.Date date, String article, String fournisseur)
            throws SQLException {
        Depenses d = new Depenses();
        d.setInstitutionId(institutionId);
        d.setResponsableDepense(executant);
        d.setMotif(motif);
        d.setMontantDepense(montant);
        d.setDateDepense(date);
        d.setNomDepense(article);
        d.setFournisseur(fournisseur);
        ajouterDepense(d);
    }

    public void supprimerDepense(String id) throws SQLException {
        String sql = "DELETE FROM depenses WHERE id = ? AND institution_id = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, institutionId);
            ps.executeUpdate();
        }
    }

    public double getTotalDepenses() throws SQLException {
        String sql = "SELECT COALESCE(SUM(montant), 0) FROM depenses WHERE institution_id = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble(1);
            }
        }
        return 0.0;
    }

    public double getTotalDepenses(String annee, String mois) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT COALESCE(SUM(montant), 0) FROM depenses WHERE institution_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(institutionId);

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (annee != null && !annee.isBlank()
                    && columnExists("depenses", "annee_academique")) {
                sql.append(" AND annee_academique = ?");
                args.add(annee);
            }
            if (mois != null && !mois.isBlank()) {
                sql.append(" AND DATE_FORMAT(date_depense, '%Y-%m') = ?");
                args.add(mois);
            }

            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                for (int i = 0; i < args.size(); i++) ps.setObject(i + 1, args.get(i));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getDouble(1);
                }
            }
        }
        return 0.0;
    }

    // ============================================================
    // EMPLOYÉS / SALAIRES
    // ============================================================
    public List<Employe> getEmployes() throws SQLException {
        return getEmployes(null, null, null);
    }

    public List<Employe> getEmployes(String annee, String mois, String classe) throws SQLException {
        List<Employe> employes = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT id, institution_id, annee_academique, mois, classes, " +
                "       nom, poste, salaire_base, deductions, source " +
                "FROM employes WHERE institution_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(institutionId);

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (annee != null && !annee.isBlank() && columnExists("employes", "annee_academique")) {
                sql.append(" AND annee_academique = ?");
                args.add(annee);
            }
            if (mois != null && !mois.isBlank() && columnExists("employes", "mois")) {
                sql.append(" AND mois = ?");
                args.add(mois);
            }
            if (classe != null && !classe.isBlank() && columnExists("employes", "classes")) {
                sql.append(" AND FIND_IN_SET(?, REPLACE(classes, ' ', '')) > 0");
                args.add(classe);
            }
            sql.append(" ORDER BY nom");

            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
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
                        employes.add(e);
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erreur getEmployes (filtres)", e);
        }
        return employes;
    }

    public void ajouterEmploye(Employe e) throws SQLException {
        if (e == null) throw new IllegalArgumentException("Employe null");
        String sql = "INSERT INTO employes "
                + "(institution_id, id, employe_id, annee_academique, mois, classes, "
                + " nom, poste, salaire_base, deductions, source) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            ps.setString(2, UUID.randomUUID().toString());
            ps.setInt(3, 0);
            ps.setString(4, e.getAnneeAcademique());
            ps.setString(5, e.getMois());
            ps.setString(6, joinCsv(e.getClasses()));
            ps.setString(7, e.getNom());
            ps.setString(8, e.getPoste());
            ps.setDouble(9, e.getSalaireBase());
            ps.setDouble(10, e.getDeductions());
            ps.setString(11, "LOCAL");
            ps.executeUpdate();
        }
    }

    public void ajouterEmploye(String nom, String poste, double salaireBase, double deductions)
            throws SQLException {
        Employe e = new Employe();
        e.setInstitutionId(institutionId);
        e.setNom(nom);
        e.setPoste(poste);
        e.setSalaireBase(salaireBase);
        e.setDeductions(deductions);
        ajouterEmploye(e);
    }

    public void modifierEmploye(String id, String nom, String poste,
                                 double salaireBase, double deductions) throws SQLException {
        String sql = "UPDATE employes SET nom = ?, poste = ?, salaire_base = ?, deductions = ? "
                + "WHERE id = ? AND institution_id = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nom);
            ps.setString(2, poste);
            ps.setDouble(3, salaireBase);
            ps.setDouble(4, deductions);
            ps.setString(5, id);
            ps.setString(6, institutionId);
            ps.executeUpdate();
        }
    }

    public void supprimerEmploye(String id) throws SQLException {
        String sql = "DELETE FROM employes WHERE id = ? AND institution_id = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, institutionId);
            ps.executeUpdate();
        }
    }

    public double getTotalSalaires() throws SQLException {
        String sql = "SELECT COALESCE(SUM(salaire_base - deductions), 0) "
                + "FROM employes WHERE institution_id = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble(1);
            }
        }
        return 0.0;
    }

    public double getTotalSalaires(String annee, String mois) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT COALESCE(SUM(salaire_base - deductions), 0) "
                + "FROM employes WHERE institution_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(institutionId);

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (annee != null && !annee.isBlank() && columnExists("employes", "annee_academique")) {
                sql.append(" AND annee_academique = ?");
                args.add(annee);
            }
            if (mois != null && !mois.isBlank() && columnExists("employes", "mois")) {
                sql.append(" AND mois = ?");
                args.add(mois);
            }

            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                for (int i = 0; i < args.size(); i++) ps.setObject(i + 1, args.get(i));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getDouble(1);
                }
            }
        }
        return 0.0;
    }

    // ============================================================
    // BUDGET FILTRÉ
    // ============================================================
    public Map<String, Object> getBudgetFiltre(String annee, String mois) throws SQLException {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("recettesInternes", getTotalRecettesInternes(annee, mois));
        data.put("recettesExternes", getTotalRecettesExternes(annee, mois));
        data.put("depenses", getTotalDepenses(annee, mois));
        data.put("salaires", getTotalSalaires(annee, mois));
        return data;
    }

    // ============================================================
    // SOLDE
    // ============================================================
    public double getSolde() throws SQLException {
        return getTotalRecettesInternes() + getTotalRecettesExternes()
                - getTotalDepenses() - getTotalSalaires();
    }

    // ============================================================
    // ANNÉE COURANTE
    // ============================================================
    public String getAnneeCourante() throws SQLException {
        String sql = "SELECT annee_academique FROM " + MigrationManager.TABLE_ANNEES_ACADEMIQUES
                + " WHERE institution_id = ? AND est_active = 1 LIMIT 1";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("annee_academique");
            }
        }
        return "";
    }

    // ============================================================
    // UTILITAIRES
    // ============================================================
    private boolean tableExists(String table) {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
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
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
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