import java.sql.SQLException;
import java.util.List;

public class ParametreInscriptionManager {

    private final ParametreInscriptionData data;

    public ParametreInscriptionManager() {
        this.data = new ParametreInscriptionData();
    }

    public List<ParametreInscription> getByInstitutionAnneePeriode(String institutionId, String annee, String periode) throws SQLException {
        return data.getByInstitutionAnneePeriode(institutionId, annee, periode);
    }

    public void saveAll(String institutionId, String annee, String periode, List<ParametreInscription> parametres) throws SQLException {
        data.saveAll(institutionId, annee, periode, parametres);
    }

    public List<String[]> getAnneesPeriodes(String institutionId) throws SQLException {
        return data.getAnneesPeriodes(institutionId);
    }
}