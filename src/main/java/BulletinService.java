import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class BulletinService {

    private final BulletinGenerator generator;

    public BulletinService(String institutionId) {
        try {
            this.generator = new BulletinGenerator(institutionId);
        } catch (SQLException e) {
            throw new RuntimeException("Impossible d'initialiser BulletinGenerator : " + e.getMessage(), e);
        }
    }

    public BulletinService(String institutionId, Connection connection) {
        this.generator = new BulletinGenerator(institutionId, connection);
    }

   public BulletinData buildBulletinData(Etudiant etudiant, String annee, String session, Map<String, Integer> rangs) {
    if (etudiant == null || annee == null || session == null) {
        throw new IllegalArgumentException("Paramètres invalides pour buildBulletinData");
    }

    BulletinData data = new BulletinData();
    data.setEtudiant(etudiant);
    data.setAnnee(annee);
    data.setSession(session);

    List<BulletinReleveMatiere> releve = generator.getNotesEtudiant(
            etudiant.getNumeroIdentifiantEtudiant(), annee, session, etudiant.getClasse());
    data.setReleves(releve);

    double moyenneSession = BulletinGenerator.calculerMoyenneGenerale(releve, session);
    data.setMoyenneSession(moyenneSession);

    List<String> sessions = generator.getPeriodesForYear(annee, etudiant.getClasse());
    double moyenneAnnuelle = generator.calculerMoyenneAnnuelle(
            etudiant.getNumeroIdentifiantEtudiant(), annee, etudiant.getClasse(), sessions);
    data.setMoyenneGenerale(moyenneAnnuelle);
    data.setDecision(moyenneAnnuelle >= 50 ? "ADMIS" : "ÉCHEC");
    data.setDecisionColorHex(moyenneAnnuelle >= 50 ? "#008000" : "#FF0000");

    // ✅ Correction du unboxing potentiellement null
    int rang = 0;
    if (rangs != null) {
        Integer rangValue = rangs.get(etudiant.getNumeroIdentifiantEtudiant());
        if (rangValue != null) {
            rang = rangValue;
        }
    }
    data.setRang(rang);

    return data;
}

    public Map<String, Integer> calculerRangsParClasse(String classe, String annee, String session) {
        if (classe == null || annee == null || session == null) {
            throw new IllegalArgumentException("Paramètres invalides pour calculerRangsParClasse");
        }
        BulletinGenerator.StatsClasse stats = generator.calculerStatsClasse(classe, annee, session);
        return stats.rangs;
    }

    public double calculerMoyenneAnnuelle(Etudiant etudiant, String annee, String classe) {
        if (etudiant == null || annee == null || classe == null) {
            throw new IllegalArgumentException("Paramètres invalides pour calculerMoyenneAnnuelle");
        }
        List<String> sessions = generator.getPeriodesForYear(annee, classe);
        return generator.calculerMoyenneAnnuelle(
                etudiant.getNumeroIdentifiantEtudiant(), annee, classe, sessions);
    }
}