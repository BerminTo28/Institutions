import java.time.LocalDateTime;
import java.util.Objects;

public class Matiere {
   private LocalDateTime lastModified;
    private String institutionId;
    private String nomMatiere;
    private double coefficient;
    private double notePassage;
    private double noteMaximale;
    private double noteMinimale;
    private String classeMatiere;
    private int nombreCredits;
    private String dureeCours;
    private String periode;
    private String statut;
    private String codeCours;
    private String anneeAcademique;
    private String source;

    // ============================================================
    // 1) Constructeur par défaut
    // ============================================================
    public Matiere() {
    }

    // ============================================================
    // 2) Constructeur à 10 paramètres (minimal, sans source)
    // ============================================================
    public Matiere(String institutionId, String nomMatiere, double coefficient,
                   double notePassage, double noteMinimale, String classeMatiere,
                   String periode, String statut, String codeCours,
                   String anneeAcademique) {
        this.institutionId = institutionId;
        this.nomMatiere = nomMatiere;
        this.coefficient = coefficient;
        this.notePassage = notePassage;
        this.noteMinimale = noteMinimale;
        this.classeMatiere = classeMatiere;
        this.periode = periode;
        this.statut = statut;
        this.codeCours = codeCours;
        this.anneeAcademique = anneeAcademique;
    }

    // ============================================================
    // 3) Constructeur complet à 13 paramètres (SANS source)
    //    ⚠️ C'est CE constructeur que le handler et mapMatiere() utilisent.
    // ============================================================
    public Matiere(String institutionId, String nomMatiere, double coefficient,
                   double notePassage, double noteMaximale, double noteMinimale,
                   String classeMatiere, int nombreCredits, String dureeCours,
                   String periode, String statut, String codeCours,
                   String anneeAcademique) {
        this.institutionId = institutionId;
        this.nomMatiere = nomMatiere;
        this.coefficient = coefficient;
        this.notePassage = notePassage;
        this.noteMaximale = noteMaximale;
        this.noteMinimale = noteMinimale;
        this.classeMatiere = classeMatiere;
        this.nombreCredits = nombreCredits;
        this.dureeCours = dureeCours;
        this.periode = periode;
        this.statut = statut;
        this.codeCours = codeCours;
        this.anneeAcademique = anneeAcademique;
        // `source` reste null — elle sera lue depuis la base par mapMatiere()
    }

    // ============================================================
    // 4) Constructeur complet à 14 paramètres (AVEC source)
    //    Utilisé si besoin de créer un objet avec source explicite.
    // ============================================================
    public Matiere(String institutionId, String nomMatiere, double coefficient,
                   double notePassage, double noteMaximale, double noteMinimale,
                   String classeMatiere, int nombreCredits, String dureeCours,
                   String periode, String statut, String codeCours,
                   String anneeAcademique, String source) {
        this(institutionId, nomMatiere, coefficient, notePassage, noteMaximale,
             noteMinimale, classeMatiere, nombreCredits, dureeCours, periode,
             statut, codeCours, anneeAcademique);
        this.source = source;
    }
public LocalDateTime getLastModified() {
    return lastModified;
}

public void setLastModified(LocalDateTime lastModified) {
    this.lastModified = lastModified;
}
    // ============================================================
    // Getters / Setters
    // ============================================================

    public String getInstitutionId() { return institutionId; }
    public void setInstitutionId(String institutionId) { this.institutionId = institutionId; }

    public String getNomMatiere() { return nomMatiere; }
    public void setNomMatiere(String nomMatiere) { this.nomMatiere = nomMatiere; }

    public double getCoefficient() { return coefficient; }
    public void setCoefficient(double coefficient) { this.coefficient = coefficient; }

    public double getNotePassage() { return notePassage; }
    public void setNotePassage(double notePassage) { this.notePassage = notePassage; }

    public double getNoteMaximale() { return noteMaximale; }
    public void setNoteMaximale(double noteMaximale) { this.noteMaximale = noteMaximale; }

    public double getNoteMinimale() { return noteMinimale; }
    public void setNoteMinimale(double noteMinimale) { this.noteMinimale = noteMinimale; }

    public String getClasseMatiere() { return classeMatiere; }
    public void setClasseMatiere(String classeMatiere) { this.classeMatiere = classeMatiere; }

    public int getNombreCredits() { return nombreCredits; }
    public void setNombreCredits(int nombreCredits) { this.nombreCredits = nombreCredits; }

    public String getDureeCours() { return dureeCours; }
    public void setDureeCours(String dureeCours) { this.dureeCours = dureeCours; }

    public String getPeriode() { return periode; }
    public void setPeriode(String periode) { this.periode = periode; }

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }

    public String getCodeCours() { return codeCours; }
    public void setCodeCours(String codeCours) { this.codeCours = codeCours; }

    public String getAnneeAcademique() { return anneeAcademique; }
    public void setAnneeAcademique(String anneeAcademique) { this.anneeAcademique = anneeAcademique; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    // ============================================================
    // Utilitaires
    // ============================================================

    @Override
    public String toString() {
        return "Matiere{" +
                "codeCours='" + codeCours + '\'' +
                ", nomMatiere='" + nomMatiere + '\'' +
                ", coefficient=" + coefficient +
                ", notePassage=" + notePassage +
                ", noteMinimale=" + noteMinimale +
                ", noteMaximale=" + noteMaximale +
                ", nombreCredits=" + nombreCredits +
                ", anneeAcademique='" + anneeAcademique + '\'' +
                ", source='" + source + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Matiere matiere = (Matiere) o;
        return Objects.equals(codeCours, matiere.codeCours) &&
               Objects.equals(institutionId, matiere.institutionId) &&
               Objects.equals(anneeAcademique, matiere.anneeAcademique);
    }

    @Override
    public int hashCode() {
        return Objects.hash(codeCours, institutionId, anneeAcademique);
    }
}