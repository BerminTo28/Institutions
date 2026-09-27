import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

public class Promotion implements Serializable {

    private static final long serialVersionUID = 1L;

    // ============================================================
    // CHAMPS DE SYNCHRONISATION
    // ============================================================
    private String source;

    // ============================================================
    // CHAMPS MÉTIER — PK composite (institution_id, annee_academique, promotion)
    // ✅ Plus de champ `id`
    // ============================================================
    private String institutionId;
    private String anneeAcademique;
    private String promotion;
    private LocalDate dateDebut;
    private LocalDate dateFin;
    private Boolean estActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ============================================================
    // CONSTRUCTEURS
    // ============================================================

    /** Constructeur vide. */
    public Promotion() {
        this.estActive = false;
    }

    /** Constructeur pour création (sans timestamps). */
    public Promotion(String institutionId, String anneeAcademique, String promotion,
                     LocalDate dateDebut, LocalDate dateFin, Boolean estActive) {
        this.institutionId = institutionId;
        this.anneeAcademique = anneeAcademique;
        this.promotion = promotion;
        this.dateDebut = dateDebut;
        this.dateFin = dateFin;
        this.estActive = estActive != null ? estActive : false;
    }

    /** Constructeur complet. */
    public Promotion(String institutionId, String anneeAcademique, String promotion,
                     LocalDate dateDebut, LocalDate dateFin, Boolean estActive,
                     LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.institutionId = institutionId;
        this.anneeAcademique = anneeAcademique;
        this.promotion = promotion;
        this.dateDebut = dateDebut;
        this.dateFin = dateFin;
        this.estActive = estActive;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // ============================================================
    // GETTERS / SETTERS
    // ============================================================

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getInstitutionId() { return institutionId; }
    public void setInstitutionId(String institutionId) { this.institutionId = institutionId; }

    public String getAnneeAcademique() { return anneeAcademique; }
    public void setAnneeAcademique(String anneeAcademique) { this.anneeAcademique = anneeAcademique; }

    public String getPromotion() { return promotion; }
    public void setPromotion(String promotion) { this.promotion = promotion; }

    public LocalDate getDateDebut() { return dateDebut; }
    public void setDateDebut(LocalDate dateDebut) { this.dateDebut = dateDebut; }

    public LocalDate getDateFin() { return dateFin; }
    public void setDateFin(LocalDate dateFin) { this.dateFin = dateFin; }

    public Boolean getEstActive() { return estActive; }
    public Boolean isEstActive() { return estActive; }
    public void setEstActive(Boolean estActive) { this.estActive = estActive; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    // ============================================================
    // MÉTHODES UTILITAIRES
    // ============================================================

    /**
     * ✅ Clé composite sous forme de chaîne (pour logs / JSON).
     */
    public String getCleComposite() {
        return (institutionId != null ? institutionId : "")
                + "|" + (anneeAcademique != null ? anneeAcademique : "")
                + "|" + (promotion != null ? promotion : "");
    }

    // ============================================================
    // EQUALS / HASHCODE / TOSTRING — basés sur la clé composite
    // ============================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Promotion that = (Promotion) o;
        return Objects.equals(institutionId, that.institutionId)
                && Objects.equals(anneeAcademique, that.anneeAcademique)
                && Objects.equals(promotion, that.promotion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(institutionId, anneeAcademique, promotion);
    }

    @Override
    public String toString() {
        return "Promotion{" +
                "institutionId='" + institutionId + '\'' +
                ", anneeAcademique='" + anneeAcademique + '\'' +
                ", promotion='" + promotion + '\'' +
                ", dateDebut=" + dateDebut +
                ", dateFin=" + dateFin +
                ", estActive=" + estActive +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}