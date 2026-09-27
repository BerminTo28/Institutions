import java.util.Date;

public class RecetteInstitution {
    private String institutionId;
    private String source;
    private String anneeAcademique;
    private String mois;
    private String periode; // ex: "S1", "S2"
    private String classe;
    private double montantTotalScolarite; // total annuel pour cette classe
    private int nombreVersements; // nombre de tranches définies pour cette classe
    // Les tranches ne sont pas stockées dans cette table, elles sont dans modalites_paiement

    // Pour un paiement individuel
    private String numeroIdentifiantEtudiant;
    private String nomEtudiant;
    private String prenomEtudiant;
    private double montantVerse;
    private String moyenPaiement;
    private Date datePaiement;
    private String reference;

    // Pour les recettes externes (dons, subventions, ventes)
    private String categorie; // "DON", "SUBVENTION", "VENTE"
    private String donateur;
    private String receveur;
    private String motif;

    // Getters et setters
    public String getSource(){return source;}
    public void setSource(String source){this.source=source;}
    public String getMois(){return mois;}
    public void setMois(String mois){this.mois=mois;}
    public String getInstitutionId() { return institutionId; }
    public void setInstitutionId(String institutionId) { this.institutionId = institutionId; }
    public String getAnneeAcademique() { return anneeAcademique; }
    public void setAnneeAcademique(String anneeAcademique) { this.anneeAcademique = anneeAcademique; }
    public String getPeriode() { return periode; }
    public void setPeriode(String periode) { this.periode = periode; }
    public String getClasse() { return classe; }
    public void setClasse(String classe) { this.classe = classe; }
    public double getMontantTotalScolarite() { return montantTotalScolarite; }
    public void setMontantTotalScolarite(double montantTotalScolarite) { this.montantTotalScolarite = montantTotalScolarite; }
    public int getNombreVersements() { return nombreVersements; }
    public void setNombreVersements(int nombreVersements) { this.nombreVersements = nombreVersements; }
    public String getNumeroIdentifiantEtudiant() { return numeroIdentifiantEtudiant; }
    public void setNumeroIdentifiantEtudiant(String numeroIdentifiantEtudiant) { this.numeroIdentifiantEtudiant = numeroIdentifiantEtudiant; }
    public String getNomEtudiant() { return nomEtudiant; }
    public void setNomEtudiant(String nomEtudiant) { this.nomEtudiant = nomEtudiant; }
    public String getPrenomEtudiant() { return prenomEtudiant; }
    public void setPrenomEtudiant(String prenomEtudiant) { this.prenomEtudiant = prenomEtudiant; }
    public double getMontantVerse() { return montantVerse; }
    public void setMontantVerse(double montantVerse) { this.montantVerse = montantVerse; }
    public String getMoyenPaiement() { return moyenPaiement; }
    public void setMoyenPaiement(String moyenPaiement) { this.moyenPaiement = moyenPaiement; }
    public Date getDatePaiement() { return datePaiement; }
    public void setDatePaiement(Date datePaiement) { this.datePaiement = datePaiement; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public String getCategorie() { return categorie; }
    public void setCategorie(String categorie) { this.categorie = categorie; }
    public String getDonateur() { return donateur; }
    public void setDonateur(String donateur) { this.donateur = donateur; }
    public String getReceveur() { return receveur; }
    public void setReceveur(String receveur) { this.receveur = receveur; }
    public String getMotif() { return motif; }
    public void setMotif(String motif) { this.motif = motif; }
}