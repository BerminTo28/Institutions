import java.util.Date;

public class Absence {
    private int id;
    private String institutionId;
    private Date dateAbsence;
    private boolean justifiee;
    private String motif;
    private String codeCours;
    private String periode;
    private String promotion;
    private String anneeAcademique;
    private String classe;
    private String personneType;
    private String numeroIdentifiant;
    private String createdBy; // Nouveau champ
    private Date createdAt;
    private Date updatedAt;
    private String source; // Nouveau champ pour la source de l'absence
    // Champs d'affichage
    private String nomComplet;
    private String matricule;
    private String nomProfesseur;

    public Absence() {}

    // Constructeur complet
    public Absence(int id, String personneType, String numeroIdentifiant, Date dateAbsence,
                   boolean justifiee, String motif, String codeCours, String institutionId,
                   String anneeAcademique, String classe, String promotion, String periode,
                   String createdBy) {
        this.id = id;
        this.personneType = personneType;
        this.numeroIdentifiant = numeroIdentifiant;
        this.dateAbsence = dateAbsence;
        this.justifiee = justifiee;
        this.motif = motif;
        this.codeCours = codeCours;
        this.institutionId = institutionId;
        this.anneeAcademique = anneeAcademique;
        this.classe = classe;
        this.promotion = promotion;
        this.periode= periode;
        this.createdBy = createdBy;
    }

    // Getters et Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getInstitutionId() { return institutionId; }
    public void setInstitutionId(String institutionId) { this.institutionId = institutionId; }

    public Date getDateAbsence() { return dateAbsence; }
    public void setDateAbsence(Date dateAbsence) { this.dateAbsence = dateAbsence; }

    public boolean isJustifiee() { return justifiee; }
    public void setJustifiee(boolean justifiee) { this.justifiee = justifiee; }

    public String getMotif() { return motif; }
    public void setMotif(String motif) { this.motif = motif; }

    public String getCodeCours() { return codeCours; }
    public void setCodeCours(String codeCours) { this.codeCours = codeCours; }

    public String getPeriode() { return periode; }
    public void setPeriode(String periode) { this.periode = periode; }

    public String getPromotion() { return promotion; }
    public void setPromotion(String promotion) { this.promotion = promotion; }

    public String getAnneeAcademique() { return anneeAcademique; }
    public void setAnneeAcademique(String anneeAcademique) { this.anneeAcademique = anneeAcademique; }

    public String getClasse() { return classe; }
    public void setClasse(String classe) { this.classe = classe; }

    public String getPersonneType() { return personneType; }
    public void setPersonneType(String personneType) { this.personneType = personneType; }

    public String getNumeroIdentifiant() { return numeroIdentifiant; }
    public void setNumeroIdentifiant(String numeroIdentifiant) { this.numeroIdentifiant = numeroIdentifiant; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }

    public String getNomComplet() { return nomComplet; }
    public void setNomComplet(String nomComplet) { this.nomComplet = nomComplet; }

    public String getMatricule() { return matricule; }
    public void setMatricule(String matricule) { this.matricule = matricule; }

    public String getNomProfesseur() { return nomProfesseur; }
    public void setNomProfesseur(String nomProfesseur) { this.nomProfesseur = nomProfesseur; }
public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    @Override
    public String toString() {
        return "Absence{" +
                "id=" + id +
                ", personne=" + (nomComplet != null ? nomComplet : numeroIdentifiant) +
                ", date=" + dateAbsence +
                ", justifiee=" + justifiee +
                '}';
    }
}