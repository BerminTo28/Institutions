import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

public class Depenses {

    // =========================================================
    // CHAMPS
    // =========================================================
    private String id;
    private LocalDateTime lastModified;
    private String institutionId;
    private String anneeAcademique;
    private String mois;
    private List<String> classes;
    private String nomInstitution;
    private double montantTotalDepenses;
    private String nomDepense;
    private String typeDepense;
    private Date dateDepense;
    private double montantDepense;
    private String descriptionDepense;
    private String responsableDepense;
    private String modePaiement;
    private String fournisseur;
    private String numeroFacture;
    private String ninu;
    private String motif;
    private String matricule;
    private String justificationDepense;
    private String source;
    private String executant;

    // =========================================================
    // CONSTRUCTEUR
    // =========================================================
    public Depenses() {
    }

    // =========================================================
    // GETTERS / SETTERS
    // =========================================================
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public LocalDateTime getLastModified() { return lastModified; }
    public void setLastModified(LocalDateTime lastModified) { this.lastModified = lastModified; }

    public String getInstitutionId() { return institutionId; }
    public void setInstitutionId(String institutionId) { this.institutionId = institutionId; }

    public String getAnneeAcademique() { return anneeAcademique; }
    public void setAnneeAcademique(String anneeAcademique) { this.anneeAcademique = anneeAcademique; }

    public String getMois() { return mois; }
    public void setMois(String mois) { this.mois = mois; }

    public List<String> getClasses() { return classes; }
    public void setClasses(List<String> classes) { this.classes = classes; }

    public String getNomInstitution() { return nomInstitution; }
    public void setNomInstitution(String nomInstitution) { this.nomInstitution = nomInstitution; }

    public double getMontantTotalDepenses() { return montantTotalDepenses; }
    public void setMontantTotalDepenses(double montantTotalDepenses) { this.montantTotalDepenses = montantTotalDepenses; }

    public String getNomDepense() { return nomDepense; }
    public void setNomDepense(String nomDepense) { this.nomDepense = nomDepense; }

    public String getTypeDepense() { return typeDepense; }
    public void setTypeDepense(String typeDepense) { this.typeDepense = typeDepense; }

    public Date getDateDepense() { return dateDepense; }
    public void setDateDepense(Date dateDepense) { this.dateDepense = dateDepense; }

    public double getMontantDepense() { return montantDepense; }
    public void setMontantDepense(double montantDepense) { this.montantDepense = montantDepense; }

    public String getDescriptionDepense() { return descriptionDepense; }
    public void setDescriptionDepense(String descriptionDepense) { this.descriptionDepense = descriptionDepense; }

    public String getResponsableDepense() { return responsableDepense; }
    public void setResponsableDepense(String responsableDepense) { this.responsableDepense = responsableDepense; }

    public String getModePaiement() { return modePaiement; }
    public void setModePaiement(String modePaiement) { this.modePaiement = modePaiement; }

    public String getFournisseur() { return fournisseur; }
    public void setFournisseur(String fournisseur) { this.fournisseur = fournisseur; }

    public String getNumeroFacture() { return numeroFacture; }
    public void setNumeroFacture(String numeroFacture) { this.numeroFacture = numeroFacture; }

    public String getJustificationDepense() { return justificationDepense; }
    public void setJustificationDepense(String justificationDepense) { this.justificationDepense = justificationDepense; }

    public String getNinu() { return ninu; }
    public void setNinu(String ninu) { this.ninu = ninu; }

    public String getMatricule() { return matricule; }
    public void setMatricule(String matricule) { this.matricule = matricule; }

    public String getMotif() { return motif; }
    public void setMotif(String motif) { this.motif = motif; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
// =========================================================
public String getExecutant(){return executant;}
public void setExecutant(String executant){this.executant=executant;}
}