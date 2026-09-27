public class AnneeAcademique {
    private String institutionId;
    private String anneeAcademique;
    private String dateDebut;
    private String dateFin;
    private boolean estActive;
    private String source;   // ✅ AJOUTÉ

    // =========================================================
    // Getters / Setters
    // =========================================================
    public String getInstitutionId() { return institutionId; }
    public void setInstitutionId(String institutionId) { this.institutionId = institutionId; }

    public String getAnneeAcademique() { return anneeAcademique; }
    public void setAnneeAcademique(String anneeAcademique) { this.anneeAcademique = anneeAcademique; }

    public String getDateDebut() { return dateDebut; }
    public void setDateDebut(String dateDebut) { this.dateDebut = dateDebut; }

    public String getDateFin() { return dateFin; }
    public void setDateFin(String dateFin) { this.dateFin = dateFin; }

    public boolean isEstActive() { return estActive; }
    public void setEstActive(boolean estActive) { this.estActive = estActive; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    // =========================================================
    // toString (optionnel, pratique pour les logs)
    // =========================================================
    @Override
    public String toString() {
        return "AnneeAcademique{" +
                ", institutionId='" + institutionId + '\'' +
                ", anneeAcademique='" + anneeAcademique + '\'' +
                ", dateDebut='" + dateDebut + '\'' +
                ", dateFin='" + dateFin + '\'' +
                ", estActive=" + estActive +
                ", source='" + source + '\'' +
                '}';
    }
}