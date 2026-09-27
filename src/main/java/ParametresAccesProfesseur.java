import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ParametresAccesProfesseur {

    private static final Logger LOGGER = Logger.getLogger(ParametresAccesProfesseur.class.getName());

    public AccesProfesseur getAcces(String numeroIdentifiant, String institutionId) throws Exception {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            AccesProfesseurData data = new AccesProfesseurData(conn);
            return data.getByProfesseur(numeroIdentifiant, institutionId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur r\u00e9cup\u00e9ration acc\u00e8s professeur: {0}", e.getMessage());
            throw new Exception("Erreur technique");
        }
    }

    public List<AccesProfesseur> getAllAcces(String institutionId) throws Exception {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            AccesProfesseurData data = new AccesProfesseurData(conn);
            return data.getAllByInstitution(institutionId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur r\u00e9cup\u00e9ration acc\u00e8s professeurs: {0}", e.getMessage());
            throw new Exception("Erreur technique");
        }
    }

    public boolean updateAcces(AccesProfesseur acces) throws Exception {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            AccesProfesseurData data = new AccesProfesseurData(conn);
            AccesProfesseur existing = data.getByProfesseur(acces.getNumeroIdentifiant(), acces.getInstitutionId());
            if (existing == null) {
                return data.insert(acces);
            } else {
                return data.update(acces);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur mise \u00e0 jour acc\u00e8s professeur: {0}", e.getMessage());
            throw new Exception("Impossible de mettre à jour les droits.");
        }
    }
}