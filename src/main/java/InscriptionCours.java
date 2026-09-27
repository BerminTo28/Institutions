
import java.util.Date;
import java.util.Objects;

/**
 * Représente l'inscription d'un étudiant à un cours/matière
 */
public class InscriptionCours {
    private String codeCours;
    private String institutionId;
    private Date dateInscription;
    private Date dateModification;
    private String statut;
    private Double noteFinale;

    // Constructeurs
    public InscriptionCours() {
        this.dateInscription = new Date();
        this.statut = "ACTIF";
    }

    public InscriptionCours(String codeCours, String institutionId) {
        this();
        this.codeCours = codeCours;
        this.institutionId = institutionId;
    }

    // Getters et Setters
    public String getCodeCours() {
        return codeCours;
    }

    public void setCodeCours(String codeCours) {
        this.codeCours = codeCours;
    }

    public String getInstitutionId() {
        return institutionId;
    }

    public void setInstitutionId(String institutionId) {
        this.institutionId = institutionId;
    }

    public Date getDateInscription() {
        return dateInscription;
    }

    public void setDateInscription(Date dateInscription) {
        this.dateInscription = dateInscription;
    }

    public Date getDateModification() {
        return dateModification;
    }

    public void setDateModification(Date dateModification) {
        this.dateModification = dateModification;
    }

    public String getStatut() {
        return statut;
    }

    public void setStatut(String statut) {
        this.statut = statut;
    }

    public Double getNoteFinale() {
        return noteFinale;
    }

    public void setNoteFinale(Double noteFinale) {
        this.noteFinale = noteFinale;
    }

    // Méthodes standards
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InscriptionCours that = (InscriptionCours) o;
        return Objects.equals(codeCours, that.codeCours) &&
                Objects.equals(institutionId, that.institutionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(codeCours, institutionId);
    }

    @Override
    public String toString() {
        return "InscriptionCours{" +
                "codeCours='" + codeCours + '\'' +
                ", institutionId='" + institutionId + '\'' +
                ", statut='" + statut + '\'' +
                '}';
    }
}