import java.util.Date;

public class Attestation {

    // ============================================================
    // CHAMPS DE SYNCHRONISATION
    // ============================================================
    private String source;

    // ============================================================
    // CHAMPS MÉTIER — PK composite (institution_id, id) avec UUID
    // ✅ id est maintenant un String (UUID)
    // ============================================================
    private String id;
    private String etudiantId;
    private String type;
    private Date dateEmission;
    private String contenuHTML;
    private String institutionId;
    private String numero;
    private String reference;
    private String classe;
    private String anneeAcademique;
    private String session;
    private String option;
    private String periode;

    public Attestation() {
    }

    // ============================================================
    // GETTERS
    // ============================================================

    public String getId() {
        return id;
    }

    public String getSource() {
        return source;
    }

    public String getEtudiantId() {
        return etudiantId;
    }

    public String getReference() {
        return reference;
    }

    public String getNumero() {
        return numero;
    }

    public String getType() {
        return type;
    }

    public Date getDateEmission() {
        return dateEmission;
    }

    public String getContenuHTML() {
        return contenuHTML;
    }

    public String getInstitutionId() {
        return institutionId;
    }

    public String getClasse() {
        return classe;
    }

    public String getAnneeAcademique() {
        return anneeAcademique;
    }

    public String getSession() {
        return session;
    }

    public String getOption() {
        return option;
    }

    public String getPeriode() {
        return periode;
    }

    // ============================================================
    // SETTERS
    // ============================================================

    public void setId(String id) {
        this.id = id;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public void setEtudiantId(String etudiantId) {
        this.etudiantId = etudiantId;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public void setNumero(String numero) {
        this.numero = numero;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setDateEmission(Date dateEmission) {
        this.dateEmission = dateEmission;
    }

    public void setContenuHTML(String contenuHTML) {
        this.contenuHTML = contenuHTML;
    }

    public void setInstitutionId(String institutionId) {
        this.institutionId = institutionId;
    }

    public void setClasse(String classe) {
        this.classe = classe;
    }

    public void setAnneeAcademique(String anneeAcademique) {
        this.anneeAcademique = anneeAcademique;
    }

    public void setSession(String session) {
        this.session = session;
    }

    public void setOption(String option) {
        this.option = option;
    }

    public void setPeriode(String periode) {
        this.periode = periode;
    }
}