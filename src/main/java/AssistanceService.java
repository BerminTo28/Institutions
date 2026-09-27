import java.sql.Connection;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;

public class AssistanceService {
    private final AssistanceData data;

    public AssistanceService(Connection connection) {
        this.data = new AssistanceData(connection);
    }

    public boolean enregistrerDemande(String etudiantId, String institutionId, String typeDemande, String sujet, String message, String fichierJoint) throws SQLException {
        Assistance assistance = new Assistance();
        assistance.setEtudiantId(etudiantId);
        assistance.setInstitutionId(institutionId);
        assistance.setTypeDemande(typeDemande);
        assistance.setSujet(sujet);
        assistance.setMessage(message);
        assistance.setStatut("EN_ATTENTE");
        assistance.setDateCreation(new Date());
        assistance.setFichierJoint(fichierJoint);
        return data.create(assistance);
    }

    public List<Assistance> getHistorique(String etudiantId, String institutionId) throws SQLException {
        return data.getByEtudiant(etudiantId, institutionId);
    }
}