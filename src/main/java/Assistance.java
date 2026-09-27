import java.util.Date;

public class Assistance {
    private String id;
    private String source;
    private String institutionId;
    private String etudiantId;
    private String typeDemande;
    private String sujet;
    private String message;
    private String statut; // "EN_ATTENTE", "EN_COURS", "TRAITE"
    private Date dateCreation;
    private String fichierJoint; // chemin ou nom du fichier

    // Getters et Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getInstitutionId() { return institutionId; }
    public void setInstitutionId(String institutionId) { this.institutionId = institutionId; }

    public String getEtudiantId() { return etudiantId; }
    public void setEtudiantId(String etudiantId) { this.etudiantId = etudiantId; }

    public String getTypeDemande() { return typeDemande; }
    public void setTypeDemande(String typeDemande) { this.typeDemande = typeDemande; }

    public String getSujet() { return sujet; }
    public void setSujet(String sujet) { this.sujet = sujet; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }

    public Date getDateCreation() { return dateCreation; }
    public void setDateCreation(Date dateCreation) { this.dateCreation = dateCreation; }

    public String getFichierJoint() { return fichierJoint; }
    public void setFichierJoint(String fichierJoint) { this.fichierJoint = fichierJoint; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}