import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CoursLienManager {

    private static final Logger LOGGER = Logger.getLogger(CoursLienManager.class.getName());
    private static final String TABLE_NAME = "cours_liens";

    /**
     * Liste tous les liens d'un professeur
     */
    public List<CoursLien> listerParProfesseur(String professeurId, String institutionId) throws SQLException {
        List<CoursLien> liens = new ArrayList<>();
        String sql = "SELECT * FROM " + TABLE_NAME + " WHERE professeur_id = ? AND institution_id = ? ORDER BY date_debut DESC";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, professeurId);
            ps.setString(2, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    liens.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur listerParProfesseur", e);
            throw e;
        }
        return liens;
    }

    /**
     * Liste les liens disponibles pour un étudiant (ceux qui ne sont pas encore terminés)
     */
    public List<CoursLien> listerPourEtudiant(String institutionId) throws SQLException {
        List<CoursLien> liens = new ArrayList<>();
        String sql = "SELECT * FROM " + TABLE_NAME + " WHERE institution_id = ? AND date_fin >= NOW() ORDER BY date_debut ASC";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    liens.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur listerPourEtudiant", e);
            throw e;
        }
        return liens;
    }

    /**
     * Récupère un lien par son ID
     */
    public CoursLien trouverParId(String id, String institutionId) throws SQLException {
        String sql = "SELECT * FROM " + TABLE_NAME + " WHERE id = ? AND institution_id = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur trouverParId", e);
            throw e;
        }
        return null;
    }

    /**
     * Crée un nouveau lien
     */
    public boolean creer(CoursLien lien) throws SQLException {
        String sql = """
            INSERT INTO cours_liens (id, titre, description, url, date_debut, date_fin, professeur_id, institution_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, lien.getId());
            ps.setString(2, lien.getTitre());
            ps.setString(3, lien.getDescription());
            ps.setString(4, lien.getUrl());
            ps.setTimestamp(5, Timestamp.valueOf(lien.getDateDebut()));
            ps.setTimestamp(6, Timestamp.valueOf(lien.getDateFin()));
            ps.setString(7, lien.getProfesseurId());
            ps.setString(8, lien.getInstitutionId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur creer", e);
            throw e;
        }
    }

    /**
     * Met à jour un lien existant
     */
    public boolean mettreAJour(CoursLien lien) throws SQLException {
        String sql = """
            UPDATE cours_liens SET titre = ?, description = ?, url = ?, date_debut = ?, date_fin = ?
            WHERE id = ? AND institution_id = ?
            """;
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, lien.getTitre());
            ps.setString(2, lien.getDescription());
            ps.setString(3, lien.getUrl());
            ps.setTimestamp(4, Timestamp.valueOf(lien.getDateDebut()));
            ps.setTimestamp(5, Timestamp.valueOf(lien.getDateFin()));
            ps.setString(6, lien.getId());
            ps.setString(7, lien.getInstitutionId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur mettreAJour", e);
            throw e;
        }
    }

    /**
     * Supprime un lien
     */
    public boolean supprimer(String id, String institutionId) throws SQLException {
        String sql = "DELETE FROM " + TABLE_NAME + " WHERE id = ? AND institution_id = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, institutionId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur supprimer", e);
            throw e;
        }
    }

    // ==================== MAPPER ====================

    private CoursLien mapRow(ResultSet rs) throws SQLException {
        CoursLien lien = new CoursLien();
        lien.setId(rs.getString("id"));
        lien.setTitre(rs.getString("titre"));
        lien.setDescription(rs.getString("description"));
        lien.setUrl(rs.getString("url"));
        Timestamp tsDebut = rs.getTimestamp("date_debut");
        if (tsDebut != null) lien.setDateDebut(tsDebut.toLocalDateTime());
        Timestamp tsFin = rs.getTimestamp("date_fin");
        if (tsFin != null) lien.setDateFin(tsFin.toLocalDateTime());
        lien.setProfesseurId(rs.getString("professeur_id"));
        lien.setInstitutionId(rs.getString("institution_id"));
        return lien;
    }
}