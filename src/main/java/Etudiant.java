import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Etudiant implements Serializable {

    private static final long serialVersionUID = 1L;

    // =========================
    // 🔷 IDENTITÉ
    // =========================
    public String numeroIdentifiant;
    public String nom;

    private String prenom;
    private String sexe;
    private Date dateNaissance;
    private String groupeSanguin;

    // =========================
    // 🔷 CONTACT
    // =========================
    private String telephone;
    private String email;
    private String adresse;
    private String communeResidence;
    private String departementResidence;
    // =========================
    // 🔷 SCOLARITÉ
    // =========================
    public String classe;

    private String anneeAcademique;
    private String promotion;
    private String periode;
    private String matricule;
    private String ninu;
    private String institutionId;
    private String statut;
    private Date dateInscription;
    // =========================
    // 🔷 NAISSANCE
    // =========================
    private String departementNaissance;
    private String communeNaissance;


    // =========================
    // 🔷 PARENTS
    // =========================
    private String nomPere;
    private String prenomPere;
    private String nomMere;
    private String prenomMere;
    private String telephoneParents;
    // =========================
    // 🔷 RESPONSABLE
    // =========================
    private String residenceParents;
    private String lienParente;
    private String typeResponsable;
    private String nomResponsable;
    private String prenomResponsable;

    // =========================
    // 🔷 MÉDIAS
    // =========================
    private String photoPath;
    private String observations;
    private String option;
    // =========================
    // 🔷 BIOMÉTRIE
    // =========================
    private String empreintePath;
    private String visagePath;
    private boolean biometrieActive;
    private LocalDateTime lastModified;
    private String source; // ✅ AJOUTÉ

    // =========================
    // 🔷 COURS
    // =========================
    private List<InscriptionCours> inscriptionsCours;

    // =========================
    // 🔷 CONSTRUCTEURS
    // =========================
    public Etudiant() {

        // Valeurs par défaut
        this.biometrieActive = false;
        this.statut = "ACTIF";
    }

    // =========================
    // 🔷 GETTERS / SETTERS
    // =========================

    public String getNumeroIdentifiantEtudiant() {
        return numeroIdentifiant;
    }

    public void setNumeroIdentifiantEtudiant(String numeroIdentifiant) {
        this.numeroIdentifiant = numeroIdentifiant;
    }

    public String getNom() {
        return nom;
    }
    public void setNom(String nom) {
        this.nom = nom;
    }

    public String getOption() {
        return option;
    }
    public void setOption(String option) {
        this.option = option;
    }

    public String getPeriode(){
        return periode;
    }

    public void setPeriode(String periode){
        this.periode=periode;
    }
    
    public String getPrenom() {
        return prenom;
    }

    public void setPrenom(String prenom) {
        this.prenom = prenom;
    }

    public String getSexe() {
        return sexe;
    }

    public void setSexe(String sexe) {
        this.sexe = sexe;
    }

    public Date getDateNaissance() {
        return dateNaissance;
    }

    public void setDateNaissance(Date dateNaissance) {
        this.dateNaissance = dateNaissance;
    }

    public String getGroupeSanguin() {
        return groupeSanguin;
    }

    public void setGroupeSanguin(String groupeSanguin) {
        this.groupeSanguin = groupeSanguin;
    }

    public String getTelephone() {
        return telephone;
    }

    public void setTelephone(String telephone) {
        this.telephone = telephone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAdresse() {
        return adresse;
    }

    public void setAdresse(String adresse) {
        this.adresse = adresse;
    }

    public String getClasse() {
        return classe;
    }

    public void setClasse(String classe) {
        this.classe = classe;
    }

    public String getAnneeAcademique() {
        return anneeAcademique;
    }

    public void setAnneeAcademique(String anneeAcademique) {
        this.anneeAcademique = anneeAcademique;
    }

    public String getPromotion() {
        return promotion;
    }

    public void setPromotion(String promotion) {
        this.promotion = promotion;
    }

    public String getMatricule() {
        return matricule;
    }

    public void setMatricule(String matricule) {
        this.matricule = matricule;
    }

    public String getNinu() {
        return ninu;
    }

    public void setNinu(String ninu) {
        this.ninu = ninu;
    }

    public String getInstitutionId() {
        return institutionId;
    }

    public void setInstitutionId(String institutionId) {
        this.institutionId = institutionId;
    }

    public String getStatut() {
        return statut;
    }

    public void setStatut(String statut) {
        this.statut = statut;
    }

    public Date getDateInscription() {
        return dateInscription;
    }

    public void setDateInscription(Date dateInscription) {
        this.dateInscription = dateInscription;
    }

    public String getNomPere() {
        return nomPere;
    }

    public void setNomPere(String nomPere) {
        this.nomPere = nomPere;
    }

    public String getPrenomPere() {
        return prenomPere;
    }

    public void setPrenomPere(String prenomPere) {
        this.prenomPere = prenomPere;
    }

    public String getNomMere() {
        return nomMere;
    }

    public void setNomMere(String nomMere) {
        this.nomMere = nomMere;
    }

    public String getPrenomMere() {
        return prenomMere;
    }

    public void setPrenomMere(String prenomMere) {
        this.prenomMere = prenomMere;
    }

    public String getTelephoneParents() {
        return telephoneParents;
    }

    public void setTelephoneParents(String telephoneParents) {
        this.telephoneParents = telephoneParents;
    }

    public String getPhotoPath() {
        return photoPath;
    }

    public void setPhotoPath(String photoPath) {
        this.photoPath = photoPath;
    }

    public String getDepartementNaissance() {
        return departementNaissance;
    }

    public void setDepartementNaissance(String departementNaissance) {
        this.departementNaissance = departementNaissance;
    }

    public String getCommuneNaissance() {
        return communeNaissance;
    }

    public void setCommuneNaissance(String communeNaissance) {
        this.communeNaissance = communeNaissance;
    }

    public String getObservations() {
        return observations;
    }

    public void setObservations(String observations) {
        this.observations = observations;
    }

    public String getResidenceParents() {
        return residenceParents;
    }

    public void setResidenceParents(String residenceParents) {
        this.residenceParents = residenceParents;
    }

    public String getLienParente() {
        return lienParente;
    }

    public void setLienParente(String lienParente) {
        this.lienParente = lienParente;
    }

    public String getTypeResponsable() {
        return typeResponsable;
    }

    public void setTypeResponsable(String typeResponsable) {
        this.typeResponsable = typeResponsable;
    }

    public String getNomResponsable() {
        return nomResponsable;
    }

    public void setNomResponsable(String nomResponsable) {
        this.nomResponsable = nomResponsable;
    }

    public String getPrenomResponsable() {
        return prenomResponsable;
    }

    public void setPrenomResponsable(String prenomResponsable) {
        this.prenomResponsable = prenomResponsable;
    }

    public String getEmpreintePath() {
        return empreintePath;
    }

    public void setEmpreintePath(String empreintePath) {
        this.empreintePath = empreintePath;
    }

    public String getVisagePath() {
        return visagePath;
    }

    public void setVisagePath(String visagePath) {
        this.visagePath = visagePath;
    }

    public boolean isBiometrieActive() {
        return biometrieActive;
    }

    public void setBiometrieActive(boolean biometrieActive) {
        this.biometrieActive = biometrieActive;
    }

    // =========================
    // 🔷 ACCÈS
    // =========================


public LocalDateTime getLastModified() {
    return lastModified;
}

public void setLastModified(LocalDateTime lastModified) {
    this.lastModified = lastModified;
}
    // =========================
    // 🔷 COURS
    // =========================

    public List<InscriptionCours> getInscriptionsCours() {
        return inscriptionsCours;
    }

    public void setInscriptionsCours(List<InscriptionCours> inscriptionsCours) {
        this.inscriptionsCours = inscriptionsCours;
    }

    public void ajouterInscriptionCours(InscriptionCours inscription) {

        if (this.inscriptionsCours == null) {
            this.inscriptionsCours = new ArrayList<>();
        }

        this.inscriptionsCours.add(inscription);
    }

    // =========================
    // 🔷 MÉTHODES UTILITAIRES
    // =========================

    public String getNomComplet() {

        return (nom != null ? nom : "")
                + " "
                + (prenom != null ? prenom : "");
    }

    public boolean estValide() {

        return numeroIdentifiant != null
                && !numeroIdentifiant.trim().isEmpty()
                && nom != null
                && !nom.trim().isEmpty()
                && prenom != null
                && !prenom.trim().isEmpty();
    }

    public String getDepartementResidence(){
        return departementResidence;
    }

    public String getCommuneResidence(){
        return communeResidence;
    }
   public void setDepartementResidence(String departementResidence){
    this.departementResidence=departementResidence;
   }

   public void setCommuneResidence(String communeResidence){
    this.communeResidence=communeResidence;
   }
    public String getSource() {
        return source;
    }
    public void setSource(String source) {
        this.source = source;
    }

    @Override
    public String toString() {

        return "Etudiant{"
                + "numeroIdentifiant='" + numeroIdentifiant + '\''
                + ", nom='" + nom + '\''
                + ", prenom='" + prenom + '\''
                + ", classe='" + classe + '\''
                + ", institutionId='" + institutionId + '\''
                + ", statut='" + statut + '\''
                + '}';
    }
}