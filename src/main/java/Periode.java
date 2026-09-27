public class Periode {
    
    private String institutionId;
    private String anneeAcademique;
    private String periode;
    private String dateDebut;
    private String dateFin;
    private boolean estActive;
    private String source;
    
    // ============ Constructeurs ============
    
    public Periode() {}
    
    public Periode(String institutionId, String anneeAcademique, String periode) {
        this.institutionId = institutionId;
        this.anneeAcademique = anneeAcademique;
        this.periode = periode;
    }
    
    public Periode(String institutionId, String anneeAcademique, String periode,
                   String dateDebut, String dateFin, boolean estActive) {
        this.institutionId = institutionId;
        this.anneeAcademique = anneeAcademique;
        this.periode = periode;
        this.dateDebut = dateDebut;
        this.dateFin = dateFin;
        this.estActive = estActive;
    }
    
    // ============ Getters / Setters ============

    
    public String getInstitutionId() { return institutionId; }
    public void setInstitutionId(String institutionId) { this.institutionId = institutionId; }
    
    public String getAnneeAcademique() { return anneeAcademique; }
    public void setAnneeAcademique(String anneeAcademique) { this.anneeAcademique = anneeAcademique; }
    
    public String getPeriode() { return periode; }
    public void setPeriode(String periode) { this.periode = periode; }
    
    public String getDateDebut() { return dateDebut; }
    public void setDateDebut(String dateDebut) { this.dateDebut = dateDebut; }
    
    public String getDateFin() { return dateFin; }
    public void setDateFin(String dateFin) { this.dateFin = dateFin; }
    
    public boolean isEstActive() { return estActive; }
    public void setEstActive(boolean estActive) { this.estActive = estActive; }
    
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    
    // ============ Clé composite ============
    
    /**
     * Retourne la clé composite utilisée pour la synchronisation.
     * Format : institutionId|anneeAcademique|periode
     */
    public String getCleComposite() {
        return institutionId + "|" + anneeAcademique + "|" + periode;
    }
    
    @Override
    public String toString() {
        return "Periode{" +
                "institutionId='" + institutionId + '\'' +
                ", anneeAcademique='" + anneeAcademique + '\'' +
                ", periode='" + periode + '\'' +
                ", dateDebut='" + dateDebut + '\'' +
                ", dateFin='" + dateFin + '\'' +
                ", estActive=" + estActive +
                '}';
    }
}