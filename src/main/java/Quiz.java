import java.util.Date;
import java.util.List;

public class Quiz {
    private String id;                 // UUID
    private String institutionId;
    private String professeurId;
    private String codeCours;
    private String classe;
    private String periode;
    private String anneeAcademique;
    private String titre;
    private String description;
    private Date dateDebut;
    private Date dateExpiration;
    private int dureeMinutes = 30;
    private double noteTotale;         // somme des cotes de sections
    private String statut = "BROUILLON";  // BROUILLON / PUBLIE / ARCHIVE
    private String source;
    private Date dateCreation;
    private Date dateModification;

    // Relations chargées à la demande
    private List<QuizSection> sections;

    // ============================================================
    // ✅ Méthodes métier
    // ============================================================

    /** Le quiz est-il actuellement accessible ? */
    public boolean isAccessible() {
        if (!"PUBLIE".equalsIgnoreCase(statut)) return false;
        Date now = new Date();
        if (dateDebut != null && now.before(dateDebut)) return false;
        return dateExpiration != null && now.before(dateExpiration);
    }

    /** Le quiz est-il expiré ? */
    public boolean isExpire() {
        return dateExpiration != null && new Date().after(dateExpiration);
    }

    // ===== Getters / Setters =====
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getInstitutionId() { return institutionId; }
    public void setInstitutionId(String institutionId) { this.institutionId = institutionId; }
    public String getProfesseurId() { return professeurId; }
    public void setProfesseurId(String professeurId) { this.professeurId = professeurId; }
    public String getCodeCours() { return codeCours; }
    public void setCodeCours(String codeCours) { this.codeCours = codeCours; }
    public String getClasse() { return classe; }
    public void setClasse(String classe) { this.classe = classe; }
    public String getPeriode() { return periode; }
    public void setPeriode(String periode) { this.periode = periode; }
    public String getAnneeAcademique() { return anneeAcademique; }
    public void setAnneeAcademique(String anneeAcademique) { this.anneeAcademique = anneeAcademique; }
    public String getTitre() { return titre; }
    public void setTitre(String titre) { this.titre = titre; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Date getDateDebut() { return dateDebut; }
    public void setDateDebut(Date dateDebut) { this.dateDebut = dateDebut; }
    public Date getDateExpiration() { return dateExpiration; }
    public void setDateExpiration(Date dateExpiration) { this.dateExpiration = dateExpiration; }
    public int getDureeMinutes() { return dureeMinutes; }
    public void setDureeMinutes(int dureeMinutes) { this.dureeMinutes = dureeMinutes; }
    public double getNoteTotale() { return noteTotale; }
    public void setNoteTotale(double noteTotale) { this.noteTotale = noteTotale; }
    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Date getDateCreation() { return dateCreation; }
    public void setDateCreation(Date dateCreation) { this.dateCreation = dateCreation; }
    public Date getDateModification() { return dateModification; }
    public void setDateModification(Date dateModification) { this.dateModification = dateModification; }
    public List<QuizSection> getSections() { return sections; }
    public void setSections(List<QuizSection> sections) { this.sections = sections; }
}