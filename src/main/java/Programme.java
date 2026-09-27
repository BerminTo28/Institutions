import java.util.Date;

public class Programme {
    private String id;
    private String source;
    private String institutionId;
    private String professeurId;
    private String codeCours;
    private String classe;
    private String periode;
    private String anneeAcademique;
    private String titreChapitre;
    private String matiere;
    private String description;
    private Date dateDebut;
    private Date dateFin;
    private boolean estTermine;
    private int cotation;
    private Date createdAt;
    private Date updatedAt;

    // Getters et Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSource(){return source;}
    public void setSource(String source){this.source=source;}
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
    
    public String getMatiere(){return matiere;}
    public void setMatiere(String matiere){this.matiere=matiere;}
    public String getAnneeAcademique() { return anneeAcademique; }
    public void setAnneeAcademique(String anneeAcademique) { this.anneeAcademique = anneeAcademique; }

    public String getTitreChapitre() { return titreChapitre; }
    public void setTitreChapitre(String titreChapitre) { this.titreChapitre = titreChapitre; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Date getDateDebut() { return dateDebut; }
    public void setDateDebut(Date dateDebut) { this.dateDebut = dateDebut; }

    public Date getDateFin() { return dateFin; }
    public void setDateFin(Date dateFin) { this.dateFin = dateFin; }

    public boolean isEstTermine() { return estTermine; }
    public void setEstTermine(boolean estTermine) { this.estTermine = estTermine; }

    public int getCotation() { return cotation; }
    public void setCotation(int cotation) { this.cotation = cotation; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
}