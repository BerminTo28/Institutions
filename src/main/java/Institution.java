import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Représente une institution dans le système M-TECH.
 *
 * <p><b>RÔLE</b> : entité racine du modèle multi-institutions. Chaque institution
 * possède un identifiant unique ({@code institution_id}) qui sert de clé de
 * partitionnement pour toutes les autres tables.</p>
 *
 * <p><b>SYNCHRONISATION</b> : cette entité est synchronisée GLOBALEMENT
 * (pas de filtre par institution) pour permettre la découverte inter-machines.</p>
 *
 * <p><b>CHAMPS DE SYNCHRONISATION</b> :
 * <ul>
 *   <li>{@code lastModified} → horodatage pour la résolution de conflits (le plus récent gagne)</li>
 *   <li>{@code source} → LOCAL ou REMOTE (origine de la dernière écriture)</li>
 *   <li>{@code statutValidation} → PENDING, VALIDATED, REJECTED (workflow d'approbation)</li>
 * </ul>
 */
public class Institution implements Serializable {

    private static final long serialVersionUID = 1L;

    // ============================================================
    // MÉTADONNÉES DE SYNCHRONISATION
    // ============================================================
    /** Horodatage de la dernière modification (résolution de conflits). */
    private LocalDateTime lastModified;

    /** Source de la dernière écriture : LOCAL ou REMOTE. */
    private String source;

    // ============================================================
    // IDENTIFICATION
    // ============================================================
    private String institutionId;
    private String nomInstitution;
    private String sigleInstitution;
    private String deviseInstitution;
    private String typeInstitution;
    private String niveauInstitution;
    private String categorieInstitution;
    private String logoInstitution;
    private String QRCodeInstitution;

    // ============================================================
    // COORDONNÉES
    // ============================================================
    private String adresseInstitution;
    private String codePostalInstitution;
    private String communeInstitution;
    private String departementInstitution;
    private String paysInstitution;

    // ============================================================
    // CONTACTS
    // ============================================================
    private String mailPrimaire;
    private String mailSecondaire;
    private String telephonePrimaireInstitution;
    private String telephoneSecondaireInstitution;
    private String siteWebInstitution;
    private String smtpPasswordInstitution;

    // ============================================================
    // RESPONSABLE
    // ============================================================
    private String responsableInstitution;
    private String posteResponsableInstitution;
    private String mailResponsableInstitution;
    private String telephoneResponsableInstitution;

    // ============================================================
    // SYSTÈME ÉDUCATIF
    // ============================================================
    private String moyenneDePassageInstitution;
    private String systemeEducatifInstitution;

    // ============================================================
    // STATUT
    // ============================================================
    private String statutInstitution;
    private String statutValidation;
    private String dateCreationInstitution;

    // ============================================================
    // SÉCURITÉ (transitoire, non persisté)
    // ============================================================
    private String motDePasseInstitution;

    // ============================================================
    // CONSTRUCTEURS
    // ============================================================
    public Institution() {
    }

    public Institution(String institutionId, String nomInstitution) {
        this.institutionId = institutionId;
        this.nomInstitution = nomInstitution;
    }

    // ============================================================
    // GETTERS & SETTERS
    // ============================================================

    // ----- Métadonnées de synchronisation -----

    public LocalDateTime getLastModified() {
        return lastModified;
    }

    public void setLastModified(LocalDateTime lastModified) {
        this.lastModified = lastModified;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    // ----- Identification -----

    public String getInstitutionId() {
        return institutionId;
    }

    public void setInstitutionId(String institutionId) {
        this.institutionId = institutionId;
    }

    public String getNomInstitution() {
        return nomInstitution;
    }

    public void setNomInstitution(String nomInstitution) {
        this.nomInstitution = nomInstitution;
    }

    public String getSigleInstitution() {
        return sigleInstitution;
    }

    public void setSigleInstitution(String sigleInstitution) {
        this.sigleInstitution = sigleInstitution;
    }

    public String getDeviseInstitution() {
        return deviseInstitution;
    }

    public void setDeviseInstitution(String deviseInstitution) {
        this.deviseInstitution = deviseInstitution;
    }

    public String getTypeInstitution() {
        return typeInstitution;
    }

    public void setTypeInstitution(String typeInstitution) {
        this.typeInstitution = typeInstitution;
    }

    public String getNiveauInstitution() {
        return niveauInstitution;
    }

    public void setNiveauInstitution(String niveauInstitution) {
        this.niveauInstitution = niveauInstitution;
    }

    public String getCategorieInstitution() {
        return categorieInstitution;
    }

    public void setCategorieInstitution(String categorieInstitution) {
        this.categorieInstitution = categorieInstitution;
    }

    public String getLogoInstitution() {
        return logoInstitution;
    }

    public void setLogoInstitution(String logoInstitution) {
        this.logoInstitution = logoInstitution;
    }

    public String getQRCodeInstitution() {
        return QRCodeInstitution;
    }

    public void setQRCodeInstitution(String QRCodeInstitution) {
        this.QRCodeInstitution = QRCodeInstitution;
    }

    // ----- Coordonnées -----

    public String getAdresseInstitution() {
        return adresseInstitution;
    }

    public void setAdresseInstitution(String adresseInstitution) {
        this.adresseInstitution = adresseInstitution;
    }

    public String getCodePostalInstitution() {
        return codePostalInstitution;
    }

    public void setCodePostalInstitution(String codePostalInstitution) {
        this.codePostalInstitution = codePostalInstitution;
    }

    public String getCommuneInstitution() {
        return communeInstitution;
    }

    public void setCommuneInstitution(String communeInstitution) {
        this.communeInstitution = communeInstitution;
    }

    public String getDepartementInstitution() {
        return departementInstitution;
    }

    public void setDepartementInstitution(String departementInstitution) {
        this.departementInstitution = departementInstitution;
    }

    public String getPaysInstitution() {
        return paysInstitution;
    }

    public void setPaysInstitution(String paysInstitution) {
        this.paysInstitution = paysInstitution;
    }

    // ----- Contacts -----

    public String getMailPrimaire() {
        return mailPrimaire;
    }

    public void setMailPrimaire(String mailPrimaire) {
        this.mailPrimaire = mailPrimaire;
    }

    public String getMailSecondaire() {
        return mailSecondaire;
    }

    public void setMailSecondaire(String mailSecondaire) {
        this.mailSecondaire = mailSecondaire;
    }

    public String getTelephonePrimaireInstitution() {
        return telephonePrimaireInstitution;
    }

    public void setTelephonePrimaireInstitution(String telephonePrimaireInstitution) {
        this.telephonePrimaireInstitution = telephonePrimaireInstitution;
    }

    public String getTelephoneSecondaireInstitution() {
        return telephoneSecondaireInstitution;
    }

    public void setTelephoneSecondaireInstitution(String telephoneSecondaireInstitution) {
        this.telephoneSecondaireInstitution = telephoneSecondaireInstitution;
    }

    public String getSiteWebInstitution() {
        return siteWebInstitution;
    }

    public void setSiteWebInstitution(String siteWebInstitution) {
        this.siteWebInstitution = siteWebInstitution;
    }

    public String getSmtpPasswordInstitution() {
        return smtpPasswordInstitution;
    }

    public void setSmtpPasswordInstitution(String smtpPasswordInstitution) {
        this.smtpPasswordInstitution = smtpPasswordInstitution;
    }

    // ----- Responsable -----

    public String getResponsableInstitution() {
        return responsableInstitution;
    }

    public void setResponsableInstitution(String responsableInstitution) {
        this.responsableInstitution = responsableInstitution;
    }

    public String getPosteResponsableInstitution() {
        return posteResponsableInstitution;
    }

    public void setPosteResponsableInstitution(String posteResponsableInstitution) {
        this.posteResponsableInstitution = posteResponsableInstitution;
    }

    public String getMailResponsableInstitution() {
        return mailResponsableInstitution;
    }

    public void setMailResponsableInstitution(String mailResponsableInstitution) {
        this.mailResponsableInstitution = mailResponsableInstitution;
    }

    public String getTelephoneResponsableInstitution() {
        return telephoneResponsableInstitution;
    }

    public void setTelephoneResponsableInstitution(String telephoneResponsableInstitution) {
        this.telephoneResponsableInstitution = telephoneResponsableInstitution;
    }

    // ----- Système éducatif -----

    public String getMoyenneDePassageInstitution() {
        return moyenneDePassageInstitution;
    }

    public void setMoyenneDePassageInstitution(String moyenneDePassageInstitution) {
        this.moyenneDePassageInstitution = moyenneDePassageInstitution;
    }

    public String getSystemeEducatifInstitution() {
        return systemeEducatifInstitution;
    }

    public void setSystemeEducatifInstitution(String systemeEducatifInstitution) {
        this.systemeEducatifInstitution = systemeEducatifInstitution;
    }

    // ----- Statut -----

    public String getStatutInstitution() {
        return statutInstitution;
    }

    public void setStatutInstitution(String statutInstitution) {
        this.statutInstitution = statutInstitution;
    }

    public String getStatutValidation() {
        return statutValidation;
    }

    public void setStatutValidation(String statutValidation) {
        this.statutValidation = statutValidation;
    }

    public String getDateCreationInstitution() {
        return dateCreationInstitution;
    }

    public void setDateCreationInstitution(String dateCreationInstitution) {
        this.dateCreationInstitution = dateCreationInstitution;
    }

    // ----- Sécurité -----

    public String getMotDePasseInstitution() {
        return motDePasseInstitution;
    }

    public void setMotDePasseInstitution(String motDePasseInstitution) {
        this.motDePasseInstitution = motDePasseInstitution;
    }

    // ============================================================
    // MÉTHODES UTILITAIRES
    // ============================================================

    /**
     * Indique si l'institution est validée (visible dans l'UI standard).
     */
    public boolean estValidee() {
        return "VALIDATED".equalsIgnoreCase(statutValidation);
    }

    /**
     * Indique si l'institution est en attente de validation.
     */
    public boolean estEnAttente() {
        return statutValidation == null || "PENDING".equalsIgnoreCase(statutValidation);
    }

    /**
     * Vérifie que l'entité a un identifiant valide.
     */
    public boolean estValide() {
        return institutionId != null && !institutionId.isBlank();
    }

    // ============================================================
    // EQUALS / HASHCODE (basés sur institutionId)
    // ============================================================
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Institution that = (Institution) o;
        return Objects.equals(institutionId, that.institutionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(institutionId);
    }

    // ============================================================
    // TOSTRING (résumé concis, sans secrets)
    // ============================================================
    @Override
    public String toString() {
        return "Institution{" +
                "institutionId='" + institutionId + '\'' +
                ", nomInstitution='" + nomInstitution + '\'' +
                ", sigleInstitution='" + sigleInstitution + '\'' +
                ", statutValidation='" + statutValidation + '\'' +
                ", source='" + source + '\'' +
                ", lastModified=" + lastModified +
                '}';
    }
}