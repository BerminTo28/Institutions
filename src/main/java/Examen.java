import java.sql.Time;
import java.util.Date;

public class Examen {
    private String id;
    private String institutionId;
    private String professeurId;
    private String codeCours;
    private String classe;
    private String periode;
    private String anneeAcademique;
    private String titre;
    private String description;
    private Date dateExamen;
    private Time heureDebut;
    private Time heureFin;
    private int dureeMinutes;
    private String salle;
    private double coefficient;
    private double noteMaximale;
    private String statut; // PREVU, EN_COURS, TERMINE, ANNULE
    private String fichierNom;
    private String fichierChemin;
    private String fichierType;
    private long taille; // ✅ en long
    private Date createdAt;
    private Date updatedAt;
    private String source; // Source de l'examen (ex: "LOCAL", "SYNC", etc.)
    // Champs d'affichage (non persistés)
    private String nomMatiere;

    // Constructeurs
    public Examen() {}

    // Getters et Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

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

    public String getAnneeAcademique() { return anneeAcademique; }
    public void setAnneeAcademique(String anneeAcademique) { this.anneeAcademique = anneeAcademique; }

    public String getTitre() { return titre; }
    public void setTitre(String titre) { this.titre = titre; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Date getDateExamen() { return dateExamen; }
    public void setDateExamen(Date dateExamen) { this.dateExamen = dateExamen; }

    public Time getHeureDebut() { return heureDebut; }
    public void setHeureDebut(Time heureDebut) { this.heureDebut = heureDebut; }

    public Time getHeureFin() { return heureFin; }
    public void setHeureFin(Time heureFin) { this.heureFin = heureFin; }

    public int getDureeMinutes() { return dureeMinutes; }
    public void setDureeMinutes(int dureeMinutes) { this.dureeMinutes = dureeMinutes; }

    public String getSalle() { return salle; }
    public void setSalle(String salle) { this.salle = salle; }

    public double getCoefficient() { return coefficient; }
    public void setCoefficient(double coefficient) { this.coefficient = coefficient; }

    public double getNoteMaximale() { return noteMaximale; }
    public void setNoteMaximale(double noteMaximale) { this.noteMaximale = noteMaximale; }

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }

    public String getFichierNom() { return fichierNom; }
    public void setFichierNom(String fichierNom) { this.fichierNom = fichierNom; }

    public String getFichierChemin() { return fichierChemin; }
    public void setFichierChemin(String fichierChemin) { this.fichierChemin = fichierChemin; }

    public String getFichierType() { return fichierType; }
    public void setFichierType(String fichierType) { this.fichierType = fichierType; }

    public long getTaille() { return taille; }
    public void setTaille(long taille) { this.taille = taille; } // ✅ en long

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }

    public String getNomMatiere() { return nomMatiere; }
    public void setNomMatiere(String nomMatiere) { this.nomMatiere = nomMatiere; }

    public String getHeureDebutFormate() {
        return heureDebut != null ? heureDebut.toString().substring(0, 5) : "";
    }

    public String getHeureFinFormate() {
        return heureFin != null ? heureFin.toString().substring(0, 5) : "";
    }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    @Override
    public String toString() {
        return "Examen{" +
                "id=" + id +
                ", titre='" + titre + '\'' +
                ", codeCours='" + codeCours + '\'' +
                ", dateExamen=" + dateExamen +
                '}';
    }
}