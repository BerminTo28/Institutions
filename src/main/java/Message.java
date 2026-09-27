import java.util.Date;

public class Message {

    // ============================================================
    // CHAMPS MÉTIER — PK composite (institution_id, id) avec UUID
    // ✅ id est un String (UUID)
    // ✅ reponseA est un String (UUID du message parent)
    // ============================================================
    private String id;
    private String institutionId;
    private String expediteurId;
    private String expediteurType;       // PROFESSEUR, ETUDIANT, ADMIN
    private String destinataireId;
    private String destinataireType;     // PROFESSEUR, ETUDIANT, ADMIN
    private String sujet;
    private String contenu;
    private boolean lu;
    private Date dateEnvoi;
    private String reponseA;             // ✅ UUID du message parent (au lieu de Integer)
    private boolean estSupprimeExpediteur;
    private boolean estSupprimeDestinataire;

    // ============================================================
    // CHAMP DE SYNCHRONISATION
    // ============================================================
    private String source;               // ✅ LOCAL ou REMOTE

    // ============================================================
    // CHAMPS D'AFFICHAGE (non persistés)
    // ============================================================
    private String nomExpediteur;
    private String nomDestinataire;

    // ============================================================
    // CONSTRUCTEURS
    // ============================================================
    public Message() {
    }

    public Message(String institutionId, String expediteurId, String expediteurType,
                   String destinataireId, String destinataireType,
                   String sujet, String contenu) {
        this.institutionId = institutionId;
        this.expediteurId = expediteurId;
        this.expediteurType = expediteurType;
        this.destinataireId = destinataireId;
        this.destinataireType = destinataireType;
        this.sujet = sujet;
        this.contenu = contenu;
        this.dateEnvoi = new Date();
        this.lu = false;
        this.estSupprimeExpediteur = false;
        this.estSupprimeDestinataire = false;
    }

    // ============================================================
    // GETTERS
    // ============================================================
    public String getId() { return id; }
    public String getInstitutionId() { return institutionId; }
    public String getExpediteurId() { return expediteurId; }
    public String getExpediteurType() { return expediteurType; }
    public String getDestinataireId() { return destinataireId; }
    public String getDestinataireType() { return destinataireType; }
    public String getSujet() { return sujet; }
    public String getContenu() { return contenu; }
    public boolean isLu() { return lu; }
    public Date getDateEnvoi() { return dateEnvoi; }
    public String getReponseA() { return reponseA; }              // ✅ String
    public boolean isEstSupprimeExpediteur() { return estSupprimeExpediteur; }
    public boolean isEstSupprimeDestinataire() { return estSupprimeDestinataire; }
    public String getSource() { return source; }
    public String getNomExpediteur() { return nomExpediteur; }
    public String getNomDestinataire() { return nomDestinataire; }

    // ============================================================
    // SETTERS
    // ============================================================
    public void setId(String id) { this.id = id; }
    public void setInstitutionId(String institutionId) { this.institutionId = institutionId; }
    public void setExpediteurId(String expediteurId) { this.expediteurId = expediteurId; }
    public void setExpediteurType(String expediteurType) { this.expediteurType = expediteurType; }
    public void setDestinataireId(String destinataireId) { this.destinataireId = destinataireId; }
    public void setDestinataireType(String destinataireType) { this.destinataireType = destinataireType; }
    public void setSujet(String sujet) { this.sujet = sujet; }
    public void setContenu(String contenu) { this.contenu = contenu; }
    public void setLu(boolean lu) { this.lu = lu; }
    public void setDateEnvoi(Date dateEnvoi) { this.dateEnvoi = dateEnvoi; }
    public void setReponseA(String reponseA) { this.reponseA = reponseA; }   // ✅ String
    public void setEstSupprimeExpediteur(boolean estSupprimeExpediteur) { this.estSupprimeExpediteur = estSupprimeExpediteur; }
    public void setEstSupprimeDestinataire(boolean estSupprimeDestinataire) { this.estSupprimeDestinataire = estSupprimeDestinataire; }
    public void setSource(String source) { this.source = source; }
    public void setNomExpediteur(String nomExpediteur) { this.nomExpediteur = nomExpediteur; }
    public void setNomDestinataire(String nomDestinataire) { this.nomDestinataire = nomDestinataire; }

    // ============================================================
    // UTILITAIRES
    // ============================================================
    @Override
    public String toString() {
        return "Message{" +
                "id='" + id + '\'' +
                ", institutionId='" + institutionId + '\'' +
                ", expediteurId='" + expediteurId + '\'' +
                ", destinataireId='" + destinataireId + '\'' +
                ", sujet='" + sujet + '\'' +
                ", lu=" + lu +
                ", dateEnvoi=" + dateEnvoi +
                '}';
    }
}