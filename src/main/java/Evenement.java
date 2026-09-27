import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Entité représentant un événement planifié.
 * Peut concerner des étudiants et/ou des professeurs.
 */
public class Evenement {
    private String id;
    private String anneeAcademique;
    private String periode;
    private String titre;
    private String description;
    private Timestamp dateDebut;
    private Timestamp dateFin;
    private String lieu;
    private List<String> etudiantsEmails;   // emails des étudiants participants
    private List<String> professeursEmails; // emails des professeurs participants
    private String statut; // "À venir", "En cours", "Terminé", "Annulé"
    private String institutionId;
    private String classe;
    private String source; // Source de l'événement (ex: "LOCAL", "SYNC", etc.)
    // Champs additionnels (peuvent être utilisés pour des affichages enrichis)
    private String nomEtudiant;
    private String prenomEtudiant;
    private String nomProfesseur;  // corrigé : anciennement "nomPreofesseur"
    private String prenomProfesseur;
    private String codeCours;

    public Evenement() {
        this.etudiantsEmails = new ArrayList<>();
        this.professeursEmails = new ArrayList<>();
    }

    // ==================== GETTERS / SETTERS ====================
    
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getAnneeAcademique() { return anneeAcademique; }
    public void setAnneeAcademique(String anneeAcademique) { this.anneeAcademique = anneeAcademique; }

    public String getPeriode() { return periode; }
    public void setPeriode(String periode) { this.periode = periode; }

    public String getTitre() { return titre; }
    public void setTitre(String titre) { this.titre = titre; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Timestamp getDateDebut() { return dateDebut; }
    public void setDateDebut(Timestamp dateDebut) { this.dateDebut = dateDebut; }

    public Timestamp getDateFin() { return dateFin; }
    public void setDateFin(Timestamp dateFin) { this.dateFin = dateFin; }

    public String getLieu() { return lieu; }
    public void setLieu(String lieu) { this.lieu = lieu; }

    public List<String> getEtudiantsEmails() { return etudiantsEmails; }
    public void setEtudiantsEmails(List<String> etudiantsEmails) {
        this.etudiantsEmails = etudiantsEmails != null ? etudiantsEmails : new ArrayList<>();
    }

    public List<String> getProfesseursEmails() { return professeursEmails; }
    public void setProfesseursEmails(List<String> professeursEmails) {
        this.professeursEmails = professeursEmails != null ? professeursEmails : new ArrayList<>();
    }

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }

    public String getInstitutionId() { return institutionId; }
    public void setInstitutionId(String institutionId) { this.institutionId = institutionId; }

    public String getClasse() { return classe; }
    public void setClasse(String classe) { this.classe = classe; }

    // ---- Champs supplémentaires ----
    public String getNomEtudiant() { return nomEtudiant; }
    public void setNomEtudiant(String nomEtudiant) { this.nomEtudiant = nomEtudiant; }

    public String getPrenomEtudiant() { return prenomEtudiant; }
    public void setPrenomEtudiant(String prenomEtudiant) { this.prenomEtudiant = prenomEtudiant; }

    public String getNomProfesseur() { return nomProfesseur; }
    public void setNomProfesseur(String nomProfesseur) { this.nomProfesseur = nomProfesseur; }

    public String getPrenomProfesseur() { return prenomProfesseur; }
    public void setPrenomProfesseur(String prenomProfesseur) { this.prenomProfesseur = prenomProfesseur; }

    public String getCodeCours() { return codeCours; }
    public void setCodeCours(String codeCours) { this.codeCours = codeCours; }
public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    // ==================== MÉTHODES POUR LES CSV ====================

    /**
     * Convertit la liste des étudiants en chaîne CSV.
     */
    public String getEtudiantsCsv() {
        return String.join(",", etudiantsEmails);
    }

    /**
     * Convertit la liste des professeurs en chaîne CSV.
     */
    public String getProfesseursCsv() {
        return String.join(",", professeursEmails);
    }

    /**
     * Initialise la liste des étudiants à partir d'une chaîne CSV.
     */
    public void setEtudiantsFromCsv(String csv) {
        etudiantsEmails.clear();
        if (csv != null && !csv.isBlank()) {
            for (String email : csv.split(",")) {
                String trimmed = email.trim();
                if (!trimmed.isEmpty()) {
                    etudiantsEmails.add(trimmed);
                }
            }
        }
    }

    /**
     * Initialise la liste des professeurs à partir d'une chaîne CSV.
     */
    public void setProfesseursFromCsv(String csv) {
        professeursEmails.clear();
        if (csv != null && !csv.isBlank()) {
            for (String email : csv.split(",")) {
                String trimmed = email.trim();
                if (!trimmed.isEmpty()) {
                    professeursEmails.add(trimmed);
                }
            }
        }
    }

    /**
     * Retourne la chaîne CSV brute des professeurs (pour l'affichage/formulaires).
     */
    public String getProfesseursFromCsv() {
        return getProfesseursCsv();
    }

    /**
     * Retourne la chaîne CSV brute des étudiants (pour l'affichage/formulaires).
     */
    public String getEtudiantsFromCsv() {
        return getEtudiantsCsv();
    }

    /**
     * Retourne tous les participants (étudiants + professeurs) sous forme de liste combinée.
     * Utile pour l'affichage.
     */
    public List<String> getAllParticipantsEmails() {
        List<String> all = new ArrayList<>();
        all.addAll(etudiantsEmails);
        all.addAll(professeursEmails);
        return all;
    }

    /**
     * Retourne le nombre total de participants.
     */
    public int getNbParticipants() {
        return etudiantsEmails.size() + professeursEmails.size();
    }
}