import java.util.HashMap;
import java.util.Map;

/**
 * Statistiques globales d'une classe pour une session donnée.
 */
public class BulletinStatsClasse {
    public int effectif;
    public double moyenneClasse;
    public Map<String, Integer> rangs = new HashMap<>();
    
    /**
     * Retourne le rang d'un étudiant par son identifiant.
     * @param etudiantId l'identifiant de l'étudiant
     * @return le rang de l'étudiant, ou 0 s'il n'est pas trouvé
     */
    public int getRang(String etudiantId) {
        if (etudiantId == null || rangs == null) {
            return 0;
        }
        return rangs.getOrDefault(etudiantId, 0);
    }
    

    public int getRangOrDefault(String etudiantId, int defaultValue) {
        if (etudiantId == null || rangs == null) {
            return defaultValue;
        }
        return rangs.getOrDefault(etudiantId, defaultValue);
    }
}