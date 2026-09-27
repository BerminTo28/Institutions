import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

public class Professeur implements Serializable {

    private static final long serialVersionUID = 1L;

    // ============================================================
    // CHAMPS DE SYNCHRONISATION
    // ============================================================
    private LocalDateTime lastModified;
    private String source;

    // ============================================================
    // CHAMPS PROFESSEUR
    // ============================================================
    private String id; // (obsolète, conservé pour compat)
    private String numeroIdentifiantProfesseur;
    private String nom;
    private String prenom;
    private String sexe;
    private Date dateNaissance;
    private String groupeSanguin;
    private String departementNaissance;
    private String communeNaissance;
    private String telephone;
    private String email;
    private String adresse;
    private String paysHabitation;
    private String departementHabitation;
    private String communeHabitation;
    private String matricule;
    private String ninu;
    private String institutionId;
    private String diplome;
    private String specialite;
    private Date dateEmbauche;
    private double salaire;
    private String statut;
    private String photoPath;
    private String observations;
    private String situationMatrimoniale;
    private String empreintePath;
    private String visagePath;
    private boolean biometrieActive;

    // ============================================================
    // CHAMPS CRÉNEAUX
    // ============================================================
    private List<Creneau> creneaux = new ArrayList<>();

    // ============================================================
    // CLASSE INTERNE CRENEAU
    // ============================================================
    public static class Creneau implements Serializable {
        private static final long serialVersionUID = 1L;

        // ✅ id UUID (String)
        private String id;

        private String codeCours;
        private String nomMatiere;
        private Double coefficient;
        private Date dateAffectation;
        private String heureDebut;
        private String heureFin;
        private int dureeMinutes;
        private String classe;
        private String anneeAcademique;
        private String periode;
        private String jourSemaine;
        private String source;

        public Creneau() {
            this.coefficient = 1.0;
            this.dateAffectation = new Date();
            this.dureeMinutes = 0;
        }

        // ============================================================
        // GETTERS / SETTERS
        // ============================================================

        // ✅ id UUID — getters/setters PUBLIC
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }

        public String getCodeCours() { return codeCours; }
        public void setCodeCours(String codeCours) { this.codeCours = codeCours; }

        public String getNomMatiere() { return nomMatiere; }
        public void setNomMatiere(String nomMatiere) { this.nomMatiere = nomMatiere; }

        public Double getCoefficient() { return coefficient; }
        public void setCoefficient(Double coefficient) { this.coefficient = coefficient; }

        public Date getDateAffectation() { return dateAffectation; }
        public void setDateAffectation(Date dateAffectation) { this.dateAffectation = dateAffectation; }

        public String getHeureDebut() { return heureDebut; }
        public void setHeureDebut(String heureDebut) {
            this.heureDebut = heureDebut;
            calculerDuree();
        }

        public String getHeureFin() { return heureFin; }
        public void setHeureFin(String heureFin) {
            this.heureFin = heureFin;
            calculerDuree();
        }

        public int getDureeMinutes() { return dureeMinutes; }
        public void setDureeMinutes(int dureeMinutes) { this.dureeMinutes = dureeMinutes; }

        public String getClasse() { return classe; }
        public void setClasse(String classe) { this.classe = classe; }

        public String getAnneeAcademique() { return anneeAcademique; }
        public void setAnneeAcademique(String anneeAcademique) { this.anneeAcademique = anneeAcademique; }

        public String getPeriode() { return periode; }
        public void setPeriode(String periode) { this.periode = periode; }

        public String getJourSemaine() { return jourSemaine; }
        public void setJourSemaine(String jourSemaine) { this.jourSemaine = jourSemaine; }

        // ============================================================
        // UTILITAIRES
        // ============================================================
        public String getHoraireFormate() {
            if (heureDebut != null && heureFin != null) {
                String d = heureDebut.length() > 5 ? heureDebut.substring(0, 5) : heureDebut;
                String f = heureFin.length() > 5 ? heureFin.substring(0, 5) : heureFin;
                return d + " - " + f;
            }
            return "";
        }

        private void calculerDuree() {
            if (heureDebut != null && heureFin != null
                    && !heureDebut.isEmpty() && !heureFin.isEmpty()) {
                try {
                    String[] d = heureDebut.split(":");
                    String[] f = heureFin.split(":");
                    int debut = Integer.parseInt(d[0]) * 60 + Integer.parseInt(d[1]);
                    int fin = Integer.parseInt(f[0]) * 60 + Integer.parseInt(f[1]);
                    int duree = fin - debut;
                    this.dureeMinutes = (duree < 0) ? duree + 24 * 60 : duree;
                } catch (NumberFormatException e) {
                    this.dureeMinutes = 0;
                }
            }
        }

        @Override
        public String toString() {
            return (codeCours != null ? codeCours : "")
                    + " / " + (classe != null ? classe : "")
                    + " / " + getHoraireFormate();
        }
    }

    // ============================================================
    // CONSTRUCTEURS
    // ============================================================

    public Professeur() {
        this.statut = "ACTIF";
        this.biometrieActive = false;
        this.salaire = 0.0;
        this.creneaux = new ArrayList<>();
    }

    public Professeur(String numeroIdentifiantProfesseur, String nom,
                       String prenom, String institutionId) {
        this();
        this.numeroIdentifiantProfesseur = numeroIdentifiantProfesseur;
        this.nom = nom;
        this.prenom = prenom;
        this.institutionId = institutionId;
    }

    // ============================================================
    // GETTERS / SETTERS - SYNCHRONISATION
    // ============================================================

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
        if (this.creneaux != null) {
            for (Creneau c : this.creneaux) {
                if (c.getSource() == null || c.getSource().isEmpty()) {
                    c.setSource(source);
                }
            }
        }
    }

    // ============================================================
    // GETTERS / SETTERS - PROFESSEUR
    // ============================================================

    // ✅ id Professeur (obsolète mais conservé)
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getNumeroIdentifiantProfesseur() { return numeroIdentifiantProfesseur; }
    public void setNumeroIdentifiantProfesseur(String numeroIdentifiantProfesseur) {
        this.numeroIdentifiantProfesseur = numeroIdentifiantProfesseur;
    }

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public String getPrenom() { return prenom; }
    public void setPrenom(String prenom) { this.prenom = prenom; }

    public String getSexe() { return sexe; }
    public void setSexe(String sexe) { this.sexe = sexe; }

    public Date getDateNaissance() { return dateNaissance; }
    public void setDateNaissance(Date dateNaissance) { this.dateNaissance = dateNaissance; }

    public String getGroupeSanguin() { return groupeSanguin; }
    public void setGroupeSanguin(String groupeSanguin) { this.groupeSanguin = groupeSanguin; }

    public String getDepartementNaissance() { return departementNaissance; }
    public void setDepartementNaissance(String departementNaissance) { this.departementNaissance = departementNaissance; }

    public String getCommuneNaissance() { return communeNaissance; }
    public void setCommuneNaissance(String communeNaissance) { this.communeNaissance = communeNaissance; }

    public String getTelephone() { return telephone; }
    public void setTelephone(String telephone) { this.telephone = telephone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getAdresse() { return adresse; }
    public void setAdresse(String adresse) { this.adresse = adresse; }

    public String getPaysHabitation() { return paysHabitation; }
    public void setPaysHabitation(String paysHabitation) { this.paysHabitation = paysHabitation; }

    public String getDepartementHabitation() { return departementHabitation; }
    public void setDepartementHabitation(String departementHabitation) { this.departementHabitation = departementHabitation; }

    public String getCommuneHabitation() { return communeHabitation; }
    public void setCommuneHabitation(String communeHabitation) { this.communeHabitation = communeHabitation; }

    public String getMatricule() { return matricule; }
    public void setMatricule(String matricule) { this.matricule = matricule; }

    public String getNinu() { return ninu; }
    public void setNinu(String ninu) { this.ninu = ninu; }

    public String getInstitutionId() { return institutionId; }
    public void setInstitutionId(String institutionId) { this.institutionId = institutionId; }

    public String getDiplome() { return diplome; }
    public void setDiplome(String diplome) { this.diplome = diplome; }

    public String getSpecialite() { return specialite; }
    public void setSpecialite(String specialite) { this.specialite = specialite; }

    public Date getDateEmbauche() { return dateEmbauche; }
    public void setDateEmbauche(Date dateEmbauche) { this.dateEmbauche = dateEmbauche; }

    public double getSalaire() { return salaire; }
    public void setSalaire(double salaire) { this.salaire = salaire; }

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }

    public String getPhotoPath() { return photoPath; }
    public void setPhotoPath(String photoPath) { this.photoPath = photoPath; }

    public String getObservations() { return observations; }
    public void setObservations(String observations) { this.observations = observations; }

    public String getSituationMatrimoniale() { return situationMatrimoniale; }
    public void setSituationMatrimoniale(String situationMatrimoniale) { this.situationMatrimoniale = situationMatrimoniale; }

    public String getEmpreintePath() { return empreintePath; }
    public void setEmpreintePath(String empreintePath) { this.empreintePath = empreintePath; }

    public String getVisagePath() { return visagePath; }
    public void setVisagePath(String visagePath) { this.visagePath = visagePath; }

    public boolean isBiometrieActive() { return biometrieActive; }
    public void setBiometrieActive(boolean biometrieActive) { this.biometrieActive = biometrieActive; }

    // ============================================================
    // GETTERS / SETTERS - CRÉNEAUX
    // ============================================================

    public List<Creneau> getCreneaux() { return creneaux; }

    public void setCreneaux(List<Creneau> creneaux) {
        this.creneaux = creneaux;
        if (this.creneaux != null && this.source != null) {
            for (Creneau c : this.creneaux) {
                if (c.getSource() == null || c.getSource().isEmpty()) {
                    c.setSource(this.source);
                }
            }
        }
    }

    public void ajouterCreneau(Creneau creneau) {
        if (this.creneaux == null) {
            this.creneaux = new ArrayList<>();
        }
        if (creneau != null && (creneau.getSource() == null || creneau.getSource().isEmpty())) {
            creneau.setSource(this.source);
        }
        this.creneaux.add(creneau);
    }

    // ============================================================
    // MÉTHODES UTILITAIRES
    // ============================================================

    public String getNomComplet() {
        return (prenom != null ? prenom : "") + " " + (nom != null ? nom : "");
    }

    public boolean estValide() {
        return numeroIdentifiantProfesseur != null && !numeroIdentifiantProfesseur.isEmpty()
                && nom != null && !nom.isEmpty()
                && institutionId != null && !institutionId.isEmpty();
    }

    // ============================================================
    // EQUALS, HASHCODE, TOSTRING
    // ============================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Professeur that = (Professeur) o;
        return Objects.equals(numeroIdentifiantProfesseur, that.numeroIdentifiantProfesseur) &&
                Objects.equals(institutionId, that.institutionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(numeroIdentifiantProfesseur, institutionId);
    }

    @Override
    public String toString() {
        return getNomComplet();
    }
}