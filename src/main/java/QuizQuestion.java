import java.util.List;

public class QuizQuestion {

    public enum Type { SIMPLE, MULTIPLE }

    private String id;               // UUID
    private String institutionId;
    private String sectionId;
    private String enonce;
    private Type type = Type.SIMPLE;
    private double cote = 1.0;
    private int ordre;
    private String explication;
    private List<QuizOption> options;

    /** Nombre de réponses correctes. */
    public long getNombreCorrectes() {
        if (options == null) return 0;
        return options.stream().filter(QuizOption::isEstCorrecte).count();
    }

    /** Valide la cohérence (simple = 1 seule bonne réponse). */
    public boolean isValide() {
        if (options == null || options.size() < 2) return false;
        long correctes = getNombreCorrectes();
        if (type == Type.SIMPLE) return correctes == 1;
        return correctes >= 1;   // MULTIPLE : au moins 1
    }

    // Getters / Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getInstitutionId() { return institutionId; }
    public void setInstitutionId(String institutionId) { this.institutionId = institutionId; }
    public String getSectionId() { return sectionId; }
    public void setSectionId(String sectionId) { this.sectionId = sectionId; }
    public String getEnonce() { return enonce; }
    public void setEnonce(String enonce) { this.enonce = enonce; }
    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }
    public double getCote() { return cote; }
    public void setCote(double cote) { this.cote = cote; }
    public int getOrdre() { return ordre; }
    public void setOrdre(int ordre) { this.ordre = ordre; }
    public String getExplication() { return explication; }
    public void setExplication(String explication) { this.explication = explication; }
    public List<QuizOption> getOptions() { return options; }
    public void setOptions(List<QuizOption> options) { this.options = options; }
}