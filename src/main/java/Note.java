import java.time.LocalDateTime;

import com.google.gson.annotations.SerializedName;

/**
 * Représente une note d'un étudiant pour une matière donnée.
 *
 * ✅ @SerializedName : fait correspondre chaque champ Java au nom EXACT
 *    de la colonne SQL. Indispensable pour que SyncService (outbox → REMOTE)
 *    retrouve les colonnes NOT NULL (numero_etudiant, annee_academique, code_cours).
 */
public class Note {

    // ============================================================
    // IDENTIFIANTS
    // ============================================================
    @SerializedName("id")
    private String id;

    /** ID interne (numérique) — pas une colonne SQL, non sérialisé. */
    private transient int noteId;

    @SerializedName("institution_id")
    private String institutionId;

    @SerializedName("source")
    private String source;

    @SerializedName("last_modified")
    private LocalDateTime lastModified;

    // ============================================================
    // ÉTUDIANT
    // ============================================================
    @SerializedName("numero_etudiant")
    private String numeroIdentifiantEtudiant;

    @SerializedName("nom_etudiant")
    private String etudiantNom;

    @SerializedName("prenom_etudiant")
    private String etudiantPrenom;

    @SerializedName("annee_academique")
    private String anneeAcademique;

    // ============================================================
    // MATIÈRE
    // ============================================================
    @SerializedName("matiere_nom")
    private String matiereNom;

    @SerializedName("code_cours")
    private String codeCours;

    @SerializedName("classe")
    private String classe;

    // ============================================================
    // ENSEIGNANT
    // ============================================================
    @SerializedName("numero_enseignant")
    private String numeroIdentifiantEnseignant;

    @SerializedName("nom_enseignant")
    private String enseignantNom;

    @SerializedName("prenom_enseignant")
    private String enseignantPrenom;

    // ============================================================
    // NOTE
    // ============================================================
    @SerializedName("note_value")
    private String noteValue;

    @SerializedName("note_sur")
    private String noteSur;

    @SerializedName("note_base")
    private String noteBase;

    @SerializedName("periode")
    private String periode;

    @SerializedName("coefficient")
    private String coefficient;

    @SerializedName("note_minimale")
    private String noteMinimale;

    @SerializedName("note_maximale")
    private String noteMaximale;

    @SerializedName("note_passage")
    private String noteDePassage;

    @SerializedName("rang_etudiant")
    private String rangEtudiant;

    @SerializedName("est_validee")
    private boolean estValidee;

    @SerializedName("is_verified")
    private boolean isVerified;

    @SerializedName("verified_by")
    private String verifiedBy;

    // ============================================================
    // GETTERS / SETTERS
    // ============================================================

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public int getNoteId() { return noteId; }
    public void setNoteId(int noteId) { this.noteId = noteId; }

    public String getInstitutionId() { return institutionId; }
    public void setInstitutionId(String institutionId) { this.institutionId = institutionId; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public LocalDateTime getLastModified() { return lastModified; }
    public void setLastModified(LocalDateTime lastModified) { this.lastModified = lastModified; }

    // ---------- Étudiant ----------
    public String getNumeroIdentifiantEtudiant() { return numeroIdentifiantEtudiant; }
    public void setNumeroIdentifiantEtudiant(String numeroIdentifiantEtudiant) {
        this.numeroIdentifiantEtudiant = numeroIdentifiantEtudiant;
    }

    public String getEtudiantNom() { return etudiantNom; }
    public void setEtudiantNom(String etudiantNom) { this.etudiantNom = etudiantNom; }

    public String getEtudiantPrenom() { return etudiantPrenom; }
    public void setEtudiantPrenom(String etudiantPrenom) { this.etudiantPrenom = etudiantPrenom; }

    public String getAnneeAcademique() { return anneeAcademique; }
    public void setAnneeAcademique(String anneeAcademique) { this.anneeAcademique = anneeAcademique; }

    // ---------- Matière ----------
    public String getMatiereNom() { return matiereNom; }
    public void setMatiereNom(String matiereNom) { this.matiereNom = matiereNom; }

    public String getCodeCours() { return codeCours; }
    public void setCodeCours(String codeCours) { this.codeCours = codeCours; }

    public String getClasse() { return classe; }
    public void setClasse(String classe) { this.classe = classe; }

    // ---------- Enseignant ----------
    public String getNumeroIdentifiantEnseignant() { return numeroIdentifiantEnseignant; }
    public void setNumeroIdentifiantEnseignant(String numeroIdentifiantEnseignant) {
        this.numeroIdentifiantEnseignant = numeroIdentifiantEnseignant;
    }

    public String getEnseignantNom() { return enseignantNom; }
    public void setEnseignantNom(String enseignantNom) { this.enseignantNom = enseignantNom; }

    public String getEnseignantPrenom() { return enseignantPrenom; }
    public void setEnseignantPrenom(String enseignantPrenom) { this.enseignantPrenom = enseignantPrenom; }

    // ---------- Note ----------
    public String getNoteValue() { return noteValue; }
    public void setNoteValue(String noteValue) { this.noteValue = noteValue; }

    public String getNoteSur() { return noteSur; }
    public void setNoteSur(String noteSur) { this.noteSur = noteSur; }

    public String getNoteBase() { return noteBase; }
    public void setNoteBase(String noteBase) { this.noteBase = noteBase; }

    public String getPeriode() { return periode; }
    public void setPeriode(String periode) { this.periode = periode; }

    public String getCoefficient() { return coefficient; }
    public void setCoefficient(String coefficient) { this.coefficient = coefficient; }

    public String getNoteMinimale() { return noteMinimale; }
    public void setNoteMinimale(String noteMinimale) { this.noteMinimale = noteMinimale; }

    public String getNoteMaximale() { return noteMaximale; }
    public void setNoteMaximale(String noteMaximale) { this.noteMaximale = noteMaximale; }

    public String getNoteDePassage() { return noteDePassage; }
    public void setNoteDePassage(String noteDePassage) { this.noteDePassage = noteDePassage; }

    public String getRangEtudiant() { return rangEtudiant; }
    public void setRangEtudiant(String rangEtudiant) { this.rangEtudiant = rangEtudiant; }

    public boolean getEstValidee() { return estValidee; }
    public void setEstValidee(boolean estValidee) { this.estValidee = estValidee; }

    public boolean getIsVerified() { return isVerified; }
    public void setIsVerified(boolean isVerified) { this.isVerified = isVerified; }

    public String getVerifiedBy() { return verifiedBy; }
    public void setVerifiedBy(String verifiedBy) { this.verifiedBy = verifiedBy; }
}