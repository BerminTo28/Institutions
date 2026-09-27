import java.awt.Graphics2D;
import java.util.List;
import java.util.Map;

public class BulletinBodySelector {

    public static float draw(Graphics2D g2,
                             List<BulletinReleveMatiere> matieres,
                             List<String> sessions,
                             Map<String, String> sessionLabels,
                             float startY,
                             int rang,
                             Institution institution,
                             double moyenneAnnuelle,
                             List<Double> moyennesParSession,
                             List<Integer> rangsParSession) {

        // Si l'institution est null, on utilise par défaut la version privée
        if (institution == null) {
            return BulletinBodyPrivate.draw(g2, matieres, sessions, sessionLabels, startY, rang,
                    "", "", "", moyenneAnnuelle, moyennesParSession, rangsParSession);
        }

        // Même logique que BulletinHeader : publique uniquement si la catégorie est "Publique"
        String categorie = institution.getCategorieInstitution();
        boolean isPublic = (categorie != null && categorie.trim().equalsIgnoreCase("Publique"));

        if (isPublic) {
            return BulletinBody.draw(g2, matieres, sessions, sessionLabels, startY, rang,
                    institution.getSigleInstitution(),
                    institution.getResponsableInstitution(),
                    institution.getPosteResponsableInstitution(),
                    moyenneAnnuelle, moyennesParSession, rangsParSession);
        } else {
            // Toute autre catégorie (privé, null, vide, etc.) → version privée
            return BulletinBodyPrivate.draw(g2, matieres, sessions, sessionLabels, startY, rang,
                    institution.getSigleInstitution(),
                    institution.getResponsableInstitution(),
                    institution.getPosteResponsableInstitution(),
                    moyenneAnnuelle, moyennesParSession, rangsParSession);
        }
    }
}