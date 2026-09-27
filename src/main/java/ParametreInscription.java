public class ParametreInscription {
    private String id;
    private String source;
    private String institutionId;
    private String anneeAcademique;
    private String periode;
    private String classe;
    private boolean actif;
    private String promotion;
    private java.util.Date createdAt;
    private java.util.Date updatedAt;
public String getSource(){
    return source;
}
public void setSource(String source){
    this.source=source;
}
    // Getters et setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getInstitutionId() { return institutionId; }
    public void setInstitutionId(String institutionId) { this.institutionId = institutionId; }

    public String getAnneeAcademique() { return anneeAcademique; }
    public void setAnneeAcademique(String anneeAcademique) { this.anneeAcademique = anneeAcademique; }
public String getPromotion() { return promotion; }
public void setPromotion(String promotion) { this.promotion = promotion; }
    public String getPeriode() { return periode; }
    public void setPeriode(String periode) { this.periode = periode; }

    public String getClasse() { return classe; }
    public void setClasse(String classe) { this.classe = classe; }

    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }

    public java.util.Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(java.util.Date createdAt) { this.createdAt = createdAt; }

    public java.util.Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(java.util.Date updatedAt) { this.updatedAt = updatedAt; }
}