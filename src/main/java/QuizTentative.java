import java.util.Date;
import java.util.List;

public class QuizTentative {

    private String id;               // UUID
    private String institutionId;
    private String quizId;
    private String etudiantId;
    private Date dateDebut;
    private Date dateFin;
    private double coteObtenue;
    private String statut = "EN_COURS";  // EN_COURS / TERMINEE / EXPIREE
    private String source;
    private List<QuizReponseEtudiant> reponses;

    // Getters / Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getInstitutionId() { return institutionId; }
    public void setInstitutionId(String institutionId) { this.institutionId = institutionId; }
    public String getQuizId() { return quizId; }
    public void setQuizId(String quizId) { this.quizId = quizId; }
    public String getEtudiantId() { return etudiantId; }
    public void setEtudiantId(String etudiantId) { this.etudiantId = etudiantId; }
    public Date getDateDebut() { return dateDebut; }
    public void setDateDebut(Date dateDebut) { this.dateDebut = dateDebut; }
    public Date getDateFin() { return dateFin; }
    public void setDateFin(Date dateFin) { this.dateFin = dateFin; }
    public double getCoteObtenue() { return coteObtenue; }
    public void setCoteObtenue(double coteObtenue) { this.coteObtenue = coteObtenue; }
    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public List<QuizReponseEtudiant> getReponses() { return reponses; }
    public void setReponses(List<QuizReponseEtudiant> reponses) { this.reponses = reponses; }
}