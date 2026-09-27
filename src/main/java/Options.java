public class Options {
    private String institutionId;
    private String option;
    private String classe;
    private String anneeAcademique;
    private String periode;
    private String promotion;
    private int id;
    private String source;
    // --- Getters ---
public int getId(){
    return id;
}

public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
public void setId(int id){
    this.id=id;
}
    public String getInstitutionId() {
        return institutionId;
    }

    public String getOption() {
        return option;
    }

    public String getClasse() {
        return classe;
    }

    public String getAnneeAcademique() {
        return anneeAcademique;
    }

    public String getPeriode() {
        return periode;
    }

    public String getPromotion() {
        return promotion;
    }

    // --- Setters ---

    public void setInstitutionId(String institutionId) {
        this.institutionId = institutionId;
    }

    public void setOption(String option) {
        this.option = option;
    }

    public void setClasse(String classe) {
        this.classe = classe;
    }

    public void setAnneeAcademique(String anneeAcademique) {
        this.anneeAcademique = anneeAcademique;
    }

    public void setPeriode(String periode) {
        this.periode = periode;
    }

    public void setPromotion(String promotion) {
        this.promotion = promotion;
    }

    // --- (Optionnel) toString() pour faciliter le débogage ---

    @Override
    public String toString() {
        return "Options{" +
                "institutionId='" + institutionId + '\'' +
                ", option='" + option + '\'' +
                ", classe='" + classe + '\'' +
                ", anneeAcademique='" + anneeAcademique + '\'' +
                ", periode='" + periode + '\'' +
                ", promotion='" + promotion + '\'' +
                '}';
    }
}