import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AbsenceManager {

    private static final Logger LOGGER = Logger.getLogger(AbsenceManager.class.getName());
    private final AbsenceData absenceData;

    public AbsenceManager() {
        this.absenceData = new AbsenceData();
    }

    /**
     * Enregistre une nouvelle absence dans le système.
     */
  public int enregistrerAbsence(Absence absence) throws SQLException {
    if (absence.getInstitutionId() == null || absence.getInstitutionId().isBlank()) {
        throw new IllegalArgumentException("L'ID de l'institution est obligatoire.");
    }
    if (absence.getNumeroIdentifiant() == null || absence.getNumeroIdentifiant().isBlank()) {
        throw new IllegalArgumentException("Le numéro d'identification est obligatoire.");
    }
    if (absence.getPersonneType() == null || absence.getPersonneType().isBlank()) {
        throw new IllegalArgumentException("Le type de personne (ETUDIANT/PROFESSEUR) est obligatoire.");
    }
    if (absence.getDateAbsence() == null) {
        throw new IllegalArgumentException("La date d'absence est obligatoire.");
    }
    if (absence.getCodeCours() == null || absence.getCodeCours().isBlank()) {
        throw new IllegalArgumentException("Le code du cours est obligatoire.");
    }

    // SUPPRIMÉ : absence.setJustifiee(false); -> On garde la valeur transmise par le formulaire/objet
    
    if (absence.getCreatedBy() == null || absence.getCreatedBy().isBlank()) {
        absence.setCreatedBy("SYSTEM");
    }

    int id = absenceData.insert(absence);
    LOGGER.log(Level.INFO, () -> "Absence enregistrée avec l'ID : " + id);
    return id;
}


/**
     * Recherche des absences avec les filtres complets utilisés par HandlerAbsence et HandlerAbsenceEtudiant.
     */
    public List<Absence> rechercherAbsences(String type, String annee, String periode, String classe, String matiere, String numeroIdentifiant) throws SQLException {
        // Si vous gérez l'institutionId globalement ou via un contexte, adaptez le premier paramètre selon votre implémentation de absenceData
        return absenceData.rechercherAbsences(type, annee, periode, classe, matiere, numeroIdentifiant);
    }

    /**
     * Liste toutes les années académiques disponibles.
     */
    public List<String> listerAnneesAcademiques() throws SQLException {
        return absenceData.listerAnneesAcademiques();
    }

    /**
     * Liste les périodes pour une année académique donnée.
     */
    public List<String> listerPeriodes(String anneeAcademique) throws SQLException {
        return absenceData.listerPeriodes(anneeAcademique);
    }

    /**
     * Liste les classes pour une année académique donnée.
     */
    public List<String> listerClasses(String anneeAcademique) throws SQLException {
        return absenceData.listerClasses(anneeAcademique);
    }

    /**
     * Liste les étudiants filtrés selon les critères.
     */
    public List<Map<String, Object>> listerEtudiantsFiltres(String anneeAcademique, String periode, String classe) throws SQLException {
        return absenceData.listerEtudiantsFiltres(anneeAcademique, periode, classe);
    }

    /**
     * Liste les professeurs filtrés selon les critères.
     */
    public List<Map<String, Object>> listerProfesseursFiltres(String anneeAcademique, String periode, String classe) throws SQLException {
        return absenceData.listerProfesseursFiltres(anneeAcademique, periode, classe);
    }

    /**
     * Alias de compatibilité pour obtenir une absence par ID.
     */
    public Absence obtenirAbsenceParId(int id) throws SQLException {
        return getAbsenceById(id);
    }

    /**
     * Alias de compatibilité pour modifier une absence.
     */
    public boolean modifierAbsence(Absence absence) throws SQLException {
        return mettreAJourAbsence(absence);
    }
    /**
     * Met à jour une absence existante.
     */
    public boolean mettreAJourAbsence(Absence absence) throws SQLException {
        if (absence.getId() <= 0) {
            throw new IllegalArgumentException("L'ID de l'absence est requis pour la mise à jour.");
        }
        Absence existing = absenceData.findById(absence.getId());
        if (existing == null) {
            throw new IllegalArgumentException("Absence introuvable avec l'ID : " + absence.getId());
        }
        if (absence.getInstitutionId() == null) {
            absence.setInstitutionId(existing.getInstitutionId());
        }
        boolean updated = absenceData.update(absence);
        if (updated) {
            LOGGER.log(Level.INFO, () -> "Absence mise à jour : " + absence.getId());
        }
        return updated;
    }

    /**
     * Supprime une absence.
     */
    public boolean supprimerAbsence(int id, String institutionId) throws SQLException {
        boolean deleted = absenceData.delete(id, institutionId);
        if (deleted) {
            LOGGER.log(Level.INFO, () -> "Absence supprimée : " + id);
        }
        return deleted;
    }

    /**
     * Marque une absence comme justifiée.
     */
    public boolean justifierAbsence(int id, String institutionId, String motif) throws SQLException {
        Absence absence = absenceData.findById(id);
        if (absence == null) {
            throw new IllegalArgumentException("Absence introuvable : " + id);
        }
        if (!absence.getInstitutionId().equals(institutionId)) {
            throw new SecurityException("L'absence n'appartient pas à cette institution.");
        }
        absence.setJustifiee(true);
        if (motif != null && !motif.isBlank()) {
            absence.setMotif(motif);
        }
        return absenceData.update(absence);
    }

    /**
     * Récupère les absences d'une personne.
     */
    public List<Absence> getAbsencesByPersonne(String institutionId, String numeroIdentifiant, String personneType) throws SQLException {
        return absenceData.findByPersonne(institutionId, numeroIdentifiant, personneType);
    }

    /**
     * Récupère les absences d'une classe.
     */
    public List<Absence> getAbsencesByClasse(String institutionId, String classe, String anneeAcademique) throws SQLException {
        return absenceData.findByClasse(institutionId, classe, anneeAcademique);
    }

    /**
     * Récupère les absences avec l'ensemble des filtres avancés (Corrigé).
     */
    public List<Absence> getAbsencesFiltrees(String institutionId, String personneType, String classe, 
                                             String anneeAcademique, String periode, String codeCours) throws SQLException {
        return absenceData.findWithFilters(institutionId, personneType, classe, anneeAcademique, periode, codeCours);
    }

    /**
     * Récupère une absence par son ID.
     */
    public Absence getAbsenceById(int id) throws SQLException {
        return absenceData.findById(id);
    }
}