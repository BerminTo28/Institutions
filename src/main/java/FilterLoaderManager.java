import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FilterLoaderManager {

    private static final Logger LOGGER = Logger.getLogger(FilterLoaderManager.class.getName());
    private final Connection connection;

    public FilterLoaderManager(Connection connection) {
        this.connection = connection;
    }

    /**
     * 1. Alimente les années académiques pour une institution.
     */
    public List<String> chargerAnneesAcademiques(String institutionId) {
        List<String> annees = new ArrayList<>();
        String sql = "SELECT annee_academique FROM annees_academiques WHERE institution_id = ? ORDER BY date_debut DESC";
        
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    annees.add(rs.getString("annee_academique"));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors du chargement des années académiques", e);
        }
        return annees;
    }

    /**
     * 2. Alimente les périodes en fonction de l'institution et de l'année académique[cite: 14].
     */
    public List<String> chargerPeriodes(String institutionId, String anneeAcademique) {
        List<String> periodes = new ArrayList<>();
        String sql = "SELECT periode FROM periodes WHERE institution_id = ? AND annee_academique = ? ORDER BY id ASC";
        
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            ps.setString(2, anneeAcademique);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    periodes.add(rs.getString("periode"));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors du chargement des périodes", e);
        }
        return periodes;
    }

    /**
     * 3. Alimente les classes pour une institution (et optionnellement une année)[cite: 14].
     */
    public List<String> chargerClasses(String institutionId, String anneeAcademique) {
        List<String> classes = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT nom_classe FROM classes WHERE institution_id = ?");
        
        if (anneeAcademique != null && !anneeAcademique.isEmpty()) {
            sql.append(" AND annee_academique = ?");
        }
        sql.append(" ORDER BY niveau_classe ASC, nom_classe ASC");

        try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            ps.setString(1, institutionId);
            if (anneeAcademique != null && !anneeAcademique.isEmpty()) {
                ps.setString(2, anneeAcademique);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    classes.add(rs.getString("nom_classe"));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors du chargement des classes", e);
        }
        return classes;
    }

    /**
     * 4. Alimente les professeurs en croisant les tables professeurs et professeur_matieres[cite: 14].
     * Retourne une Map sous la forme (ID du Professeur -> Nom Complet).
     */
    public Map<String, String> chargerProfesseursParFiltre(String institutionId, String codeCours, String classe) {
        Map<String, String> professeurs = new LinkedHashMap<>();
        
        StringBuilder sql = new StringBuilder(
            "SELECT DISTINCT p.numero_identifiant_professeur, p.nom, p.prenom " +
            "FROM professeurs p " +
            "JOIN professeur_matieres pm ON p.numero_identifiant_professeur = pm.numero_identifiant " +
            "WHERE p.institution_id = ?"
        );

        if (codeCours != null && !codeCours.isEmpty()) {
            sql.append(" AND pm.code_cours = ?");
        }
        if (classe != null && !classe.isEmpty()) {
            sql.append(" AND pm.classe = ?");
        }
        sql.append(" ORDER BY p.nom ASC, p.prenom ASC");

        try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            int paramIndex = 1;
            ps.setString(paramIndex++, institutionId);
            
            if (codeCours != null && !codeCours.isEmpty()) {
                ps.setString(paramIndex++, codeCours);
            }
            if (classe != null && !classe.isEmpty()) {
                ps.setString(paramIndex++, classe);
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String id = rs.getString("numero_identifiant_professeur");
                    String nomComplet = rs.getString("nom") + " " + (rs.getString("prenom") != null ? rs.getString("prenom") : "");
                    professeurs.put(id, nomComplet.trim());
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors du chargement de la liste des professeurs", e);
        }
        return professeurs;
    }
}