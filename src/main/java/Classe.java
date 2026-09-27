import java.time.LocalDateTime;
import java.util.Date;

public class Classe {
private LocalDateTime lastModified;
    // Identifiants
    private String institutionId;
    private String nomClasse;
    private String codeClasse;
    private int niveauClasse;
    private int capaciteClasse;
    private int nombreEtudiants;
    private int nombreMatieres;
    private String periode;
    private String anneeAcademique;
    private String statut;
    private Date dateCreation;
    private double moyennePassage;
    private String promotion;
    private String source;   // ✅ AJOUTÉ

    // Constructeur par défaut
    public Classe() {
    }

    // ============================================
    // GETTERS & SETTERS
    // ============================================
public LocalDateTime getLastModified() { return lastModified; }
    public void setLastModified(LocalDateTime lastModified) { this.lastModified = lastModified; }
    public String getInstitutionId() { return institutionId; }
    public void setInstitutionId(String institutionId) { this.institutionId = institutionId; }

    public String getNomClasse() { return nomClasse; }
    public void setNomClasse(String nomClasse) { this.nomClasse = nomClasse; }

    public String getCodeClasse() { return codeClasse; }
    public void setCodeClasse(String codeClasse) { this.codeClasse = codeClasse; }

    public String getPromotion() { return promotion; }
    public void setPromotion(String promotion) { this.promotion = promotion; }

    public int getNiveauClasse() { return niveauClasse; }
    public void setNiveauClasse(int niveauClasse) { this.niveauClasse = niveauClasse; }

    public int getCapaciteClasse() { return capaciteClasse; }
    public void setCapaciteClasse(int capaciteClasse) { this.capaciteClasse = capaciteClasse; }

    public int getNombreEtudiants() { return nombreEtudiants; }
    public void setNombreEtudiants(int nombreEtudiants) { this.nombreEtudiants = nombreEtudiants; }

    public int getNombreMatieres() { return nombreMatieres; }
    public void setNombreMatieres(int nombreMatieres) { this.nombreMatieres = nombreMatieres; }

    public String getPeriode() { return periode; }
    public void setPeriode(String periode) { this.periode = periode; }

    public String getAnneeAcademique() { return anneeAcademique; }
    public void setAnneeAcademique(String anneeAcademique) { this.anneeAcademique = anneeAcademique; }

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }

    public Date getDateCreation() { return dateCreation; }
    public void setDateCreation(Date dateCreation) { this.dateCreation = dateCreation; }

    public double getMoyennePassage() { return moyennePassage; }
    public void setMoyennePassage(double moyennePassage) { this.moyennePassage = moyennePassage; }

    // ✅ AJOUTÉ
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    // ============================================
    // toString (optionnel)
    // ============================================
    @Override
    public String toString() {
        return "Classe{" +
                "institutionId='" + institutionId + '\'' +
                ", nomClasse='" + nomClasse + '\'' +
                ", codeClasse='" + codeClasse + '\'' +
                ", niveauClasse=" + niveauClasse +
                ", capaciteClasse=" + capaciteClasse +
                ", nombreEtudiants=" + nombreEtudiants +
                ", nombreMatieres=" + nombreMatieres +
                ", periode='" + periode + '\'' +
                ", anneeAcademique='" + anneeAcademique + '\'' +
                ", statut='" + statut + '\'' +
                ", dateCreation=" + dateCreation +
                ", moyennePassage=" + moyennePassage +
                ", promotion='" + promotion + '\'' +
                ", source='" + source + '\'' +
                '}';
    }
}