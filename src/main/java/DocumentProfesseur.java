import java.util.Date;

public class DocumentProfesseur {
    private String id;
    private String institutionId;
    private String professeurId;
    private String titre;
    private String description;
    private String fichierNom;
    private String fichierChemin;
    private String url;
    private String type;
    private String matiere;
    private String classe;
    private String periode;
    private String anneeAcademique;
    private Date dateCreation;
    private Date dateModification;
    private boolean estPublic;
    private long taille;
     private String source;
    public DocumentProfesseur() {
        this.dateCreation = new Date();
        this.dateModification = new Date();
        this.estPublic = false;
        this.taille = 0;
    }

    // Getters et Setters (à générer)
    // ...
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getInstitutionId() { return institutionId; }
    public void setInstitutionId(String institutionId) { this.institutionId = institutionId; }
    public String getProfesseurId() { return professeurId; }
    public void setProfesseurId(String professeurId) { this.professeurId = professeurId; }
    public String getTitre() { return titre; }
    public void setTitre(String titre) { this.titre = titre; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getFichierNom() { return fichierNom; }
    public void setFichierNom(String fichierNom) { this.fichierNom = fichierNom; }
    public String getFichierChemin() { return fichierChemin; }
    public void setFichierChemin(String fichierChemin) { this.fichierChemin = fichierChemin; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getMatiere() { return matiere; }
    public void setMatiere(String matiere) { this.matiere = matiere; }
    public String getClasse() { return classe; }
    public void setClasse(String classe) { this.classe = classe; }
    public String getPeriode() { return periode; }
    public void setPeriode(String periode) { this.periode = periode; }
    public String getAnneeAcademique() { return anneeAcademique; }
    public void setAnneeAcademique(String anneeAcademique) { this.anneeAcademique = anneeAcademique; }
    public Date getDateCreation() { return dateCreation; }
    public void setDateCreation(Date dateCreation) { this.dateCreation = dateCreation; }
    public Date getDateModification() { return dateModification; }
    public void setDateModification(Date dateModification) { this.dateModification = dateModification; }
    public boolean isEstPublic() { return estPublic; }
    public void setEstPublic(boolean estPublic) { this.estPublic = estPublic; }
    public long getTaille() { return taille; }
    public void setTaille(long taille) { this.taille = taille; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getTailleFormatee() {
        if (taille < 1024) return taille + " o";
        else if (taille < 1048576) return (taille / 1024) + " Ko";
        else return (taille / 1048576) + " Mo";
    }
}