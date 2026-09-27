import java.util.List;

public class QuizSection {
    private String id;               // UUID
    private String institutionId;
    private String quizId;
    private String titre;
    private String description;
    private int ordre;
    private double coteSection;       // ✅ somme des cotes des questions
    private List<QuizQuestion> questions;

    /** Recalcule coteSection à partir des questions. */
    public void recalculerCote() {
        if (questions == null) return;
        coteSection = questions.stream()
                .mapToDouble(QuizQuestion::getCote)
                .sum();
    }

    // Getters / Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getInstitutionId() { return institutionId; }
    public void setInstitutionId(String institutionId) { this.institutionId = institutionId; }
    public String getQuizId() { return quizId; }
    public void setQuizId(String quizId) { this.quizId = quizId; }
    public String getTitre() { return titre; }
    public void setTitre(String titre) { this.titre = titre; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public int getOrdre() { return ordre; }
    public void setOrdre(int ordre) { this.ordre = ordre; }
    public double getCoteSection() { return coteSection; }
    public void setCoteSection(double coteSection) { this.coteSection = coteSection; }
    public List<QuizQuestion> getQuestions() { return questions; }
    public void setQuestions(List<QuizQuestion> questions) { this.questions = questions; }
}