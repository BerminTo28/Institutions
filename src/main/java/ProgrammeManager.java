import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ProgrammeManager {

    private static final Logger LOGGER = Logger.getLogger(ProgrammeManager.class.getName());
    private final ProgrammeData programmeData;

    public ProgrammeManager(Connection connection) {
        this.programmeData = new ProgrammeData(connection);
    }

    public boolean ajouterProgramme(Programme programme) throws SQLException {
        return programmeData.create(programme);
    }

    public boolean modifierProgramme(Programme programme) throws SQLException {
        return programmeData.update(programme);
    }

    /**
     * ✅ id est désormais un String (UUID).
     */
    public boolean supprimerProgramme(String id, String institutionId, String professeurId) throws SQLException {
        return programmeData.delete(id, institutionId, professeurId);
    }

    public List<Programme> getProgrammesByProfesseur(String professeurId, String institutionId) {
        try {
            return programmeData.getAllByProfesseur(professeurId, institutionId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur récupération programmes", e);
            return List.of();
        }
    }

    public List<Programme> getProgrammesByFiltres(String professeurId, String institutionId,
                                                    String codeCours, String classe,
                                                    String periode, String annee) {
        try {
            return programmeData.getByFiltres(professeurId, institutionId,
                    codeCours, classe, periode, annee);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur récupération programmes filtrés", e);
            return List.of();
        }
    }

    /**
     * ✅ id est désormais un String (UUID).
     */
    public Programme getProgrammeById(String id, String institutionId, String professeurId) {
        try {
            return programmeData.getById(id, institutionId, professeurId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur récupération programme par ID", e);
            return null;
        }
    }
}