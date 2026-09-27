import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;


public class EvenementManager {

    private static final Logger LOGGER = Logger.getLogger(EvenementManager.class.getName());

    private final String institutionId;

    public EvenementManager(String institutionId) {
        if (institutionId == null || institutionId.isBlank()) {
            throw new IllegalArgumentException("institutionId ne peut pas être nul ou vide.");
        }
        this.institutionId = institutionId;
    }

    // ==================== LISTES D'ÉVÉNEMENTS ====================

    /**
     * Récupère tous les événements de l'institution.
     */
    public List<Evenement> listerTous() {
        return EvenementDAO.getAll(institutionId);
    }

    /**
     * Récupère les événements filtrés par année, période et classe.
     */
    public List<Evenement> listerFiltres(String annee, String periode, String classe) {
        return EvenementDAO.getFiltres(institutionId, annee, periode, classe);
    }

    /**
     * ✅ Récupère un événement par son UUID.
     *
     * @param id UUID de l'événement (String)
     */
    public Evenement obtenirParId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return EvenementDAO.getById(id, institutionId);
    }

    // ==================== SAUVEGARDE / SUPPRESSION ====================

    /**
     * Sauvegarde un événement (insertion ou mise à jour).
     * Le DAO génère l'UUID à l'insertion.
     */
    public boolean sauvegarder(Evenement evenement) {
        if (evenement == null) {
            LOGGER.warning("sauvegarder() : événement nul");
            return false;
        }
        evenement.setInstitutionId(institutionId);
        return EvenementDAO.save(evenement);
    }

    /**
     * ✅ Supprime un événement par son UUID.
     *
     * @param id UUID de l'événement (String)
     */
    public boolean supprimer(String id) {
        if (id == null || id.isBlank()) {
            LOGGER.warning("supprimer() : id null ou vide");
            return false;
        }
        return EvenementDAO.delete(id, institutionId);
    }

    // ==================== LISTES POUR LES FILTRES ====================

    public List<String> getAnneesDisponibles() {
        return EvenementDAO.getAnneesAcademiquesFromTable(institutionId);
    }

    public List<String> getPeriodesDisponibles() {
        return EvenementDAO.getPeriodesFromTable(institutionId);
    }

    public List<String> getClassesDisponibles() {
        return EvenementDAO.getClassesFromTable(institutionId);
    }

    // ==================== LISTES DE PARTICIPANTS ====================

    /**
     * Récupère la liste des étudiants actifs pour la sélection des participants.
     * Retourne une liste d'objets : [id, nom, prenom, email, classe]
     */
    public List<Object[]> obtenirEtudiantsActifs() {
        return EvenementDAO.getEtudiantsActifs(institutionId);
    }

    /**
     * Récupère la liste des professeurs actifs pour la sélection des participants.
     * Retourne une liste d'objets : [id, nom, prenom, email]
     */
    public List<Object[]> obtenirProfesseursActifs() {
        return EvenementDAO.getProfesseursActifs(institutionId);
    }

    // ==================== LISTES PAR PARTICIPANT ====================

    /**
     * Récupère tous les événements où un étudiant (par son email) est invité.
     */
    public List<Evenement> listerParEtudiant(String email) {
        if (email == null || email.isBlank()) return Collections.emptyList();
        return EvenementDAO.getByEtudiantEmail(institutionId, email);
    }

    /**
     * Récupère tous les événements où un professeur (par son email) est invité.
     */
    public List<Evenement> listerParProfesseur(String email) {
        if (email == null || email.isBlank()) return Collections.emptyList();
        return EvenementDAO.getByProfesseurEmail(institutionId, email);
    }
}