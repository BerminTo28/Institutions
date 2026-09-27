import java.util.ArrayList;
import java.util.List;

public class Employe {

    // =========================================================
    // CHAMPS
    // =========================================================
    private String id;
    private String anneeAcademique;
    private String mois;
    private List<String> classes;
    private String nom;
    private String poste;
    private double salaireBase;
    private double deductions;
    private String institutionId;
    private String source;

    // =========================================================
    // CONSTRUCTEURS
    // =========================================================
    public Employe() {
        this.classes = new ArrayList<>();
    }

    public Employe(String id, String nom, String poste,
                   double salaireBase, double deductions,
                   String institutionId) {
        this.id = id;
        this.nom = nom;
        this.poste = poste;
        this.salaireBase = salaireBase;
        this.deductions = deductions;
        this.institutionId = institutionId;
        this.classes = new ArrayList<>();
    }

    // =========================================================
    // GETTERS / SETTERS
    // =========================================================
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public String getPoste() { return poste; }
    public void setPoste(String poste) { this.poste = poste; }

    public double getSalaireBase() { return salaireBase; }
    public void setSalaireBase(double salaireBase) { this.salaireBase = salaireBase; }

    public double getDeductions() { return deductions; }
    public void setDeductions(double deductions) { this.deductions = deductions; }

    /** Salaire net = base - déductions (calculé, pas de setter). */
    public double getSalaireNet() {
        return salaireBase - deductions;
    }

    public String getInstitutionId() { return institutionId; }
    public void setInstitutionId(String institutionId) { this.institutionId = institutionId; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    // ✅ AJOUTÉS
    public String getAnneeAcademique() { return anneeAcademique; }
    public void setAnneeAcademique(String anneeAcademique) { this.anneeAcademique = anneeAcademique; }

    public String getMois() { return mois; }
    public void setMois(String mois) { this.mois = mois; }

    public List<String> getClasses() { return classes; }
    public void setClasses(List<String> classes) {
        this.classes = (classes != null) ? classes : new ArrayList<>();
    }

}