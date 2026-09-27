import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class StatistiquesData {

    // ==================== KPIs ====================
    public static Map<String, Object> getKPIs(String institutionId, String annee, String periode,
                                              String classe, String matiere) throws SQLException {
        Map<String, Object> kpis = new HashMap<>();

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            // Total étudiants
            String sqlEtud = "SELECT COUNT(*) FROM " + MigrationManager.TABLE_ETUDIANTS +
                             " WHERE institution_id = ?" +
                             (classe != null && !classe.equals("Toutes classes") ? " AND classe = ?" : "");
            try (PreparedStatement ps = conn.prepareStatement(sqlEtud)) {
                ps.setString(1, institutionId);
                if (classe != null && !classe.equals("Toutes classes")) ps.setString(2, classe);
                ResultSet rs = ps.executeQuery();
                kpis.put("totalEtudiants", rs.next() ? rs.getInt(1) : 0);
            }

            // Total professeurs
            String sqlProf = "SELECT COUNT(*) FROM " + MigrationManager.TABLE_PROFESSEURS + " WHERE institution_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sqlProf)) {
                ps.setString(1, institutionId);
                ResultSet rs = ps.executeQuery();
                kpis.put("totalProfesseurs", rs.next() ? rs.getInt(1) : 0);
            }

            // Total matières actives
            String sqlMat = "SELECT COUNT(*) FROM " + MigrationManager.TABLE_MATIERES +
                            " WHERE institution_id = ? AND statut = 'ACTIF'";
            try (PreparedStatement ps = conn.prepareStatement(sqlMat)) {
                ps.setString(1, institutionId);
                ResultSet rs = ps.executeQuery();
                kpis.put("totalMatieres", rs.next() ? rs.getInt(1) : 0);
            }

            // Notes – moyenne, écart-type, min, max
            StringBuilder sqlNotes = new StringBuilder(
                "SELECT AVG(note_value), STDDEV(note_value), MIN(note_value), MAX(note_value) FROM " +
                MigrationManager.TABLE_NOTES + " n JOIN " + MigrationManager.TABLE_ETUDIANTS +
                " e ON n.numero_etudiant = e.numero_identifiant WHERE e.institution_id = ?");
            if (annee != null && !annee.equals("Toutes années")) sqlNotes.append(" AND n.annee_academique = ?");
            if (periode != null && !periode.equals("Toutes periodes") && !periode.equals("Toutes periodes"))
                sqlNotes.append(" AND n.periode = ?");
            if (classe != null && !classe.equals("Toutes classes")) sqlNotes.append(" AND e.classe = ?");
            if (matiere != null && !matiere.equals("Toutes matières")) sqlNotes.append(" AND n.matiere_nom = ?");

            try (PreparedStatement ps = conn.prepareStatement(sqlNotes.toString())) {
                int idx = 1;
                ps.setString(idx++, institutionId);
                if (annee != null && !annee.equals("Toutes années")) ps.setString(idx++, annee);
                if (periode != null && !periode.equals("Toutes periodes") && !periode.equals("Toutes periodes"))
                    ps.setString(idx++, periode);
                if (classe != null && !classe.equals("Toutes classes")) ps.setString(idx++, classe);
                if (matiere != null && !matiere.equals("Toutes matières")) ps.setString(idx++, matiere);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    kpis.put("moyenneGenerale", rs.getObject(1) != null ? rs.getDouble(1) : 0.0);
                    kpis.put("ecartType", rs.getObject(2) != null ? rs.getDouble(2) : 0.0);
                    kpis.put("moinsBonneNote", rs.getObject(3) != null ? rs.getDouble(3) : 0.0);
                    kpis.put("meilleureNote", rs.getObject(4) != null ? rs.getDouble(4) : 0.0);
                } else {
                    kpis.put("moyenneGenerale", 0.0);
                    kpis.put("ecartType", 0.0);
                    kpis.put("moinsBonneNote", 0.0);
                    kpis.put("meilleureNote", 0.0);
                }
            }

            // Taux réussite (moyenne >= 60)
            int totalEtud = (int) kpis.get("totalEtudiants");
            int reussis = 0;
            if (totalEtud > 0) {
                StringBuilder sqlReussite = new StringBuilder(
                    "SELECT COUNT(DISTINCT e.numero_identifiant) FROM " + MigrationManager.TABLE_ETUDIANTS + " e " +
                    "WHERE e.institution_id = ? AND EXISTS (SELECT 1 FROM " + MigrationManager.TABLE_NOTES + " n " +
                    "WHERE n.numero_etudiant = e.numero_identifiant ");
                if (annee != null && !annee.equals("Toutes années")) sqlReussite.append(" AND n.annee_academique = ?");
                if (periode != null && !periode.equals("Toutes periodes") && !periode.equals("Toutes periodes"))
                    sqlReussite.append(" AND n.periode = ?");
                if (matiere != null && !matiere.equals("Toutes matières")) sqlReussite.append(" AND n.matiere_nom = ?");
                sqlReussite.append(" GROUP BY n.numero_etudiant HAVING AVG(n.note_value) >= 60)");
                if (classe != null && !classe.equals("Toutes classes")) sqlReussite.append(" AND e.classe = ?");

                try (PreparedStatement ps = conn.prepareStatement(sqlReussite.toString())) {
                    int idx = 1;
                    ps.setString(idx++, institutionId);
                    if (annee != null && !annee.equals("Toutes années")) ps.setString(idx++, annee);
                    if (periode != null && !periode.equals("Toutes periodes") && !periode.equals("Toutes periodes"))
                        ps.setString(idx++, periode);
                    if (matiere != null && !matiere.equals("Toutes matières")) ps.setString(idx++, matiere);
                    if (classe != null && !classe.equals("Toutes classes")) ps.setString(idx++, classe);
                    ResultSet rs = ps.executeQuery();
                    reussis = rs.next() ? rs.getInt(1) : 0;
                }
            }
            kpis.put("tauxReussite", totalEtud > 0 ? (reussis * 100.0 / totalEtud) : 0.0);

            // Meilleure classe
            String sqlMeilleureClasse = "SELECT e.classe, AVG(n.note_value) as moy FROM " + MigrationManager.TABLE_NOTES + " n " +
                                        "JOIN " + MigrationManager.TABLE_ETUDIANTS + " e ON n.numero_etudiant = e.numero_identifiant " +
                                        "WHERE e.institution_id = ?";
            if (annee != null && !annee.equals("Toutes années")) sqlMeilleureClasse += " AND n.annee_academique = ?";
            if (periode != null && !periode.equals("Toutes periodes") && !periode.equals("Toutes periodes"))
                sqlMeilleureClasse += " AND n.periode = ?";
            if (classe != null && !classe.equals("Toutes classes")) sqlMeilleureClasse += " AND e.classe = ?";
            if (matiere != null && !matiere.equals("Toutes matières")) sqlMeilleureClasse += " AND n.matiere_nom = ?";
            sqlMeilleureClasse += " GROUP BY e.classe ORDER BY moy DESC LIMIT 1";

            try (PreparedStatement ps = conn.prepareStatement(sqlMeilleureClasse)) {
                int idx = 1;
                ps.setString(idx++, institutionId);
                if (annee != null && !annee.equals("Toutes années")) ps.setString(idx++, annee);
                if (periode != null && !periode.equals("Toutes periodes") && !periode.equals("Toutes periodes"))
                    ps.setString(idx++, periode);
                if (classe != null && !classe.equals("Toutes classes")) ps.setString(idx++, classe);
                if (matiere != null && !matiere.equals("Toutes matières")) ps.setString(idx++, matiere);
                ResultSet rs = ps.executeQuery();
                kpis.put("meilleureClasse", rs.next() ? rs.getString("classe") : "--");
            }

            // Promotion dominante
            String sqlPromo = "SELECT promotion, COUNT(*) FROM " + MigrationManager.TABLE_ETUDIANTS +
                              " WHERE institution_id = ? AND promotion IS NOT NULL GROUP BY promotion ORDER BY COUNT(*) DESC LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sqlPromo)) {
                ps.setString(1, institutionId);
                ResultSet rs = ps.executeQuery();
                kpis.put("promoDominante", rs.next() ? rs.getString(1) : "--");
            }

            // Médiane, IQR
            List<Double> notes = new ArrayList<>();
            StringBuilder sqlNotesList = new StringBuilder(
                "SELECT n.note_value FROM " + MigrationManager.TABLE_NOTES + " n " +
                "JOIN " + MigrationManager.TABLE_ETUDIANTS + " e ON n.numero_etudiant = e.numero_identifiant " +
                "WHERE e.institution_id = ?");
            if (annee != null && !annee.equals("Toutes années")) sqlNotesList.append(" AND n.annee_academique = ?");
            if (periode != null && !periode.equals("Toutes periodes") && !periode.equals("Toutes periodes"))
                sqlNotesList.append(" AND n.periode = ?");
            if (classe != null && !classe.equals("Toutes classes")) sqlNotesList.append(" AND e.classe = ?");
            if (matiere != null && !matiere.equals("Toutes matières")) sqlNotesList.append(" AND n.matiere_nom = ?");

            try (PreparedStatement ps = conn.prepareStatement(sqlNotesList.toString())) {
                int idx = 1;
                ps.setString(idx++, institutionId);
                if (annee != null && !annee.equals("Toutes années")) ps.setString(idx++, annee);
                if (periode != null && !periode.equals("Toutes periodes") && !periode.equals("Toutes periodes"))
                    ps.setString(idx++, periode);
                if (classe != null && !classe.equals("Toutes classes")) ps.setString(idx++, classe);
                if (matiere != null && !matiere.equals("Toutes matières")) ps.setString(idx++, matiere);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) notes.add(rs.getDouble(1));
            }

            if (!notes.isEmpty()) {
                Collections.sort(notes);
                int n = notes.size();
                // À la place de la ligne problématique
double mediane = calculerMediane(notes);
                kpis.put("mediane", mediane);
                kpis.put("iqr", n >= 4 ? notes.get(3*n/4) - notes.get(n/4) : 0.0);
            } else {
                kpis.put("mediane", 0.0);
                kpis.put("iqr", 0.0);
            }
        }
        return kpis;
    }


    // ==================== MÉTHODES DÉLÉGUÉES (filtres individuels) ====================

public static List<String> getAnneesDisponibles(String institutionId) throws SQLException {
    List<String> annees = new ArrayList<>();
    String sql = "SELECT DISTINCT annee_academique FROM " + MigrationManager.TABLE_MATIERES
            + " WHERE institution_id = ? ORDER BY annee_academique DESC";
    try (Connection conn = DatabaseManager.getInstance().getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setString(1, institutionId);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            String a = rs.getString(1);
            if (a != null && !a.isBlank()) annees.add(a);
        }
    }
    if (annees.isEmpty()) {
        int an = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
        annees.add((an - 1) + "-" + an);
        annees.add(an + "-" + (an + 1));
    }
    return annees;
}

public static List<String> getPeriodesDisponibles(String institutionId) throws SQLException {
    List<String> periodes = new ArrayList<>();
    String sql = "SELECT DISTINCT periode FROM " + MigrationManager.TABLE_MATIERES
            + " WHERE institution_id = ? AND periode IS NOT NULL AND periode != '' ORDER BY periode";
    try (Connection conn = DatabaseManager.getInstance().getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setString(1, institutionId);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            String p = rs.getString(1);
            if (p != null && !p.isBlank()) periodes.add(p);
        }
    }
    if (periodes.isEmpty()) {
        periodes.add("S1");
        periodes.add("S2");
    }
    return periodes;
}

public static List<String> getClassesDisponibles(String institutionId) throws SQLException {
    List<String> classes = new ArrayList<>();
    String sql = "SELECT DISTINCT classe FROM " + MigrationManager.TABLE_ETUDIANTS
            + " WHERE institution_id = ? AND classe IS NOT NULL AND classe != '' ORDER BY classe";
    try (Connection conn = DatabaseManager.getInstance().getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setString(1, institutionId);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            String c = rs.getString(1);
            if (c != null && !c.isBlank()) classes.add(c);
        }
    }
    return classes;
}

public static List<String> getMatieresDisponibles(String institutionId) throws SQLException {
    List<String> matieres = new ArrayList<>();
    String sql = "SELECT nom_matiere FROM " + MigrationManager.TABLE_MATIERES
            + " WHERE institution_id = ? AND statut = 'ACTIF' ORDER BY nom_matiere";
    try (Connection conn = DatabaseManager.getInstance().getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setString(1, institutionId);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            String m = rs.getString(1);
            if (m != null && !m.isBlank()) matieres.add(m);
        }
    }
    return matieres;
}

    public static double calculerMediane(List<Double> notes) {
    if (notes == null || notes.isEmpty()) return 0.0;
    
    // Filtrer les valeurs null pour éviter les exceptions
    List<Double> valeurs = notes.stream()
            .filter(Objects::nonNull)
            .sorted()
            .collect(Collectors.toList());
    
    if (valeurs.isEmpty()) return 0.0;
    
    int n = valeurs.size();
    if (n % 2 == 0) {
        return (valeurs.get(n/2 - 1) + valeurs.get(n/2)) / 2.0;
    } else {
        return valeurs.get(n/2);
    }
}
    // ==================== TOP / BOTTOM ====================
    public static void getTopBottom(String institutionId, String annee, String periode, String classe,
                                    String matiere, List<Object[]> top, List<Object[]> bottom) throws SQLException {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            StringBuilder sql = new StringBuilder(
                "SELECT e.nom, e.prenom, e.classe, AVG(n.note_value) as moyenne " +
                "FROM " + MigrationManager.TABLE_NOTES + " n " +
                "JOIN " + MigrationManager.TABLE_ETUDIANTS + " e ON n.numero_etudiant = e.numero_identifiant " +
                "WHERE e.institution_id = ?");
            if (annee != null && !annee.equals("Toutes années")) sql.append(" AND n.annee_academique = ?");
            if (periode != null && !periode.equals("Toutes periodes") && !periode.equals("Toutes periodes"))
                sql.append(" AND n.periode = ?");
            if (classe != null && !classe.equals("Toutes classes")) sql.append(" AND e.classe = ?");
            if (matiere != null && !matiere.equals("Toutes matières")) sql.append(" AND n.matiere_nom = ?");
            sql.append(" GROUP BY e.numero_identifiant, e.nom, e.prenom, e.classe ORDER BY moyenne DESC");

            List<Object[]> allStudents = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                int idx = 1;
                ps.setString(idx++, institutionId);
                if (annee != null && !annee.equals("Toutes années")) ps.setString(idx++, annee);
                if (periode != null && !periode.equals("Toutes periodes") && !periode.equals("Toutes periodes"))
                    ps.setString(idx++, periode);
                if (classe != null && !classe.equals("Toutes classes")) ps.setString(idx++, classe);
                if (matiere != null && !matiere.equals("Toutes matières")) ps.setString(idx++, matiere);

                ResultSet rs = ps.executeQuery();
                int rang = 1;
                while (rs.next()) {
                    String nom = rs.getString("nom");
                    String prenom = rs.getString("prenom");
                    String cls = rs.getString("classe");
                    double moyenne = rs.getDouble("moyenne");
                    allStudents.add(new Object[]{rang++, nom, prenom, cls, String.format(Locale.FRANCE, "%.2f", moyenne)});
                }
            }

            int limitTop = Math.min(10, allStudents.size());
            for (int i = 0; i < limitTop; i++) {
                top.add(allStudents.get(i));
            }

            int startBottom = Math.max(0, allStudents.size() - 10);
            for (int i = startBottom; i < allStudents.size(); i++) {
                bottom.add(allStudents.get(i));
            }
        }
    }

    // ==================== STATISTIQUES PAR CLASSE ====================
    public static List<Object[]> getStatsClasse(String institutionId, String annee, String periode,
                                                String classeFilter, String matiere) throws SQLException {
        List<Object[]> result = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
            "SELECT e.classe, COUNT(*) as effectif, AVG(n.note_value) as moyenne, " +
            "MAX(n.note_value) as max, MIN(n.note_value) as min " +
            "FROM " + MigrationManager.TABLE_NOTES + " n " +
            "JOIN " + MigrationManager.TABLE_ETUDIANTS + " e ON n.numero_etudiant = e.numero_identifiant " +
            "WHERE e.institution_id = ?");
        if (annee != null && !annee.equals("Toutes années")) sql.append(" AND n.annee_academique = ?");
        if (periode != null && !periode.equals("Toutes periodes") && !periode.equals("Toutes periodes"))
            sql.append(" AND n.periode = ?");
        if (classeFilter != null && !classeFilter.equals("Toutes classes")) sql.append(" AND e.classe = ?");
        if (matiere != null && !matiere.equals("Toutes matières")) sql.append(" AND n.matiere_nom = ?");
        sql.append(" GROUP BY e.classe ORDER BY e.classe");

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            ps.setString(idx++, institutionId);
            if (annee != null && !annee.equals("Toutes années")) ps.setString(idx++, annee);
            if (periode != null && !periode.equals("Toutes periodes") && !periode.equals("Toutes periodes"))
                ps.setString(idx++, periode);
            if (classeFilter != null && !classeFilter.equals("Toutes classes")) ps.setString(idx++, classeFilter);
            if (matiere != null && !matiere.equals("Toutes matières")) ps.setString(idx++, matiere);

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String classe = rs.getString("classe");
                int effectif = rs.getInt("effectif");
                double moyenne = rs.getDouble("moyenne");
                double max = rs.getDouble("max");
                double min = rs.getDouble("min");
                // taux de réussite (moyenne >= 60) : on pourrait le calculer avec une sous-requête, mais on simplifie ici
                // On ajoute une colonne vide pour l'instant (vous pouvez l'implémenter si besoin)
                result.add(new Object[]{classe, effectif, String.format("%.2f", moyenne),
                                        String.format("%.2f", max), String.format("%.2f", min)});
            }
        }
        return result;
    }

    // ==================== GRAPHIQUES ====================
    public static Map<String, Integer> getEffectifsClasses(String institutionId, String classeFilter) throws SQLException {
        Map<String, Integer> map = new LinkedHashMap<>();
        String sql = "SELECT classe, COUNT(*) FROM " + MigrationManager.TABLE_ETUDIANTS +
                     " WHERE institution_id = ?" +
                     (classeFilter != null && !classeFilter.equals("Toutes classes") ? " AND classe = ?" : "") +
                     " GROUP BY classe ORDER BY classe";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            if (classeFilter != null && !classeFilter.equals("Toutes classes")) ps.setString(2, classeFilter);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) map.put(rs.getString(1), rs.getInt(2));
        }
        return map;
    }

    public static Map<String, Double> getMoyennesClasses(String institutionId, String annee, String periode,
                                                         String classeFilter, String matiere) throws SQLException {
        Map<String, Double> map = new LinkedHashMap<>();
        StringBuilder sql = new StringBuilder(
            "SELECT e.classe, AVG(n.note_value) FROM " + MigrationManager.TABLE_NOTES + " n " +
            "JOIN " + MigrationManager.TABLE_ETUDIANTS + " e ON n.numero_etudiant = e.numero_identifiant " +
            "WHERE e.institution_id = ?");
        if (annee != null && !annee.equals("Toutes années")) sql.append(" AND n.annee_academique = ?");
        if (periode != null && !periode.equals("Toutes periodes") && !periode.equals("Toutes periodes"))
            sql.append(" AND n.periode = ?");
        if (classeFilter != null && !classeFilter.equals("Toutes classes")) sql.append(" AND e.classe = ?");
        if (matiere != null && !matiere.equals("Toutes matières")) sql.append(" AND n.matiere_nom = ?");
        sql.append(" GROUP BY e.classe ORDER BY e.classe");

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            ps.setString(idx++, institutionId);
            if (annee != null && !annee.equals("Toutes années")) ps.setString(idx++, annee);
            if (periode != null && !periode.equals("Toutes periodes") && !periode.equals("Toutes periodes"))
                ps.setString(idx++, periode);
            if (classeFilter != null && !classeFilter.equals("Toutes classes")) ps.setString(idx++, classeFilter);
            if (matiere != null && !matiere.equals("Toutes matières")) ps.setString(idx++, matiere);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) map.put(rs.getString(1), rs.getDouble(2));
        }
        return map;
    }

    public static Map<String, Double> getEvolution(String institutionId, String periode,
                                                   String classeFilter, String matiere) throws SQLException {
        Map<String, Double> map = new LinkedHashMap<>();
        StringBuilder sql = new StringBuilder(
            "SELECT n.annee_academique, AVG(n.note_value) FROM " + MigrationManager.TABLE_NOTES + " n " +
            "JOIN " + MigrationManager.TABLE_ETUDIANTS + " e ON n.numero_etudiant = e.numero_identifiant " +
            "WHERE e.institution_id = ? AND n.annee_academique IS NOT NULL");
        if (periode != null && !periode.equals("Toutes periodes") && !periode.equals("Toutes periodes"))
            sql.append(" AND n.periode = ?");
        if (classeFilter != null && !classeFilter.equals("Toutes classes")) sql.append(" AND e.classe = ?");
        if (matiere != null && !matiere.equals("Toutes matières")) sql.append(" AND n.matiere_nom = ?");
        sql.append(" GROUP BY n.annee_academique ORDER BY n.annee_academique");

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            ps.setString(idx++, institutionId);
            if (periode != null && !periode.equals("Toutes periodes") && !periode.equals("Toutes periodes"))
                ps.setString(idx++, periode);
            if (classeFilter != null && !classeFilter.equals("Toutes classes")) ps.setString(idx++, classeFilter);
            if (matiere != null && !matiere.equals("Toutes matières")) ps.setString(idx++, matiere);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) map.put(rs.getString(1), rs.getDouble(2));
        }
        return map;
    }

    public static Map<String, Integer> getRepartitionPromo(String institutionId, String classeFilter) throws SQLException {
        Map<String, Integer> map = new LinkedHashMap<>();
        String sql = "SELECT promotion, COUNT(*) FROM " + MigrationManager.TABLE_ETUDIANTS +
                     " WHERE institution_id = ? AND promotion IS NOT NULL" +
                     (classeFilter != null && !classeFilter.equals("Toutes classes") ? " AND classe = ?" : "") +
                     " GROUP BY promotion ORDER BY promotion";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            if (classeFilter != null && !classeFilter.equals("Toutes classes")) ps.setString(2, classeFilter);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) map.put(rs.getString(1), rs.getInt(2));
        }
        return map;
    }

    // ==================== FILTRES ====================
    public static Map<String, Object> getFiltres(String institutionId) throws SQLException {
        Map<String, Object> result = new HashMap<>();

        List<String> annees = new ArrayList<>();
        String sqlAnnees = "SELECT DISTINCT annee_academique FROM " + MigrationManager.TABLE_MATIERES +
                           " WHERE institution_id = ? ORDER BY annee_academique DESC";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlAnnees)) {
            ps.setString(1, institutionId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) annees.add(rs.getString(1));
        }
        if (annees.isEmpty()) {
            int an = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
            annees.add((an-1)+"-"+an);
            annees.add(an+"-"+(an+1));
        }
        result.put("annees", annees);

        List<String> periodes = new ArrayList<>();
        String sqlperiodes = "SELECT DISTINCT periode FROM " + MigrationManager.TABLE_MATIERES +
                             " WHERE institution_id = ? AND periode IS NOT NULL AND periode != ''";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlperiodes)) {
            ps.setString(1, institutionId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) periodes.add(rs.getString(1));
        }
        if (periodes.isEmpty()) { periodes.add("S1"); periodes.add("S2"); }
        result.put("periodes", periodes);

        List<String> classes = new ArrayList<>();
        String sqlClasses = "SELECT DISTINCT classe FROM " + MigrationManager.TABLE_ETUDIANTS +
                            " WHERE institution_id = ? AND classe IS NOT NULL AND classe != '' ORDER BY classe";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlClasses)) {
            ps.setString(1, institutionId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) classes.add(rs.getString(1));
        }
        result.put("classes", classes);

        List<String> matieres = new ArrayList<>();
        String sqlMatieres = "SELECT nom_matiere FROM " + MigrationManager.TABLE_MATIERES +
                             " WHERE institution_id = ? AND statut = 'ACTIF' ORDER BY nom_matiere";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlMatieres)) {
            ps.setString(1, institutionId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) matieres.add(rs.getString(1));
        }
        result.put("matieres", matieres);

        return result;
    }
}