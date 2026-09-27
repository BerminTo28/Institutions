public class QuizReponseEtudiant {
    private String id;               // UUID
    private String institutionId;
    private String tentativeId;
    private String questionId;
    private String optionId;

    // Getters / Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getInstitutionId() { return institutionId; }
    public void setInstitutionId(String institutionId) { this.institutionId = institutionId; }
    public String getTentativeId() { return tentativeId; }
    public void setTentativeId(String tentativeId) { this.tentativeId = tentativeId; }
    public String getQuestionId() { return questionId; }
    public void setQuestionId(String questionId) { this.questionId = questionId; }
    public String getOptionId() { return optionId; }
    public void setOptionId(String optionId) { this.optionId = optionId; }
}