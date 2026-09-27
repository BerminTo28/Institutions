import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Représente la progression d'un étudiant dans le cours
 */
public class ProgressionEtudiant {
    private String source;
    private String etudiantId;
    private double progression;
    private List<String> leconsTerminees;
    private List<String> devoirsSoumis;
    private List<String> quizsTermines;
    private double moyenneGlobale;
    private LocalDateTime dateDerniereActivite;
    private LocalDateTime dateInscription;
    private boolean aObtenuCertificat;
    private double noteFinale;
    private final List<NoteDevoir> notesDevoirs;
    private final List<NoteQuiz> notesQuizs;
    
    public ProgressionEtudiant(String etudiantId) {
        this.etudiantId = etudiantId;
        this.progression = 0.0;
        this.leconsTerminees = new ArrayList<>();
        this.devoirsSoumis = new ArrayList<>();
        this.quizsTermines = new ArrayList<>();
        this.notesDevoirs = new ArrayList<>();
        this.notesQuizs = new ArrayList<>();
        this.moyenneGlobale = 0.0;
        this.dateDerniereActivite = LocalDateTime.now();
        this.dateInscription = LocalDateTime.now();
        this.aObtenuCertificat = false;
        this.noteFinale = 0.0;
    }
    
    public void marquerLeconTerminee(String leconId) {
        if (!this.leconsTerminees.contains(leconId)) {
            this.leconsTerminees.add(leconId);
            this.dateDerniereActivite = LocalDateTime.now();
            calculerProgression();
        }
    }
    
    public void soumettreDevoir(String devoirId) {
        if (!this.devoirsSoumis.contains(devoirId)) {
            this.devoirsSoumis.add(devoirId);
            this.dateDerniereActivite = LocalDateTime.now();
            calculerProgression();
        }
    }
    
    public void terminerQuiz(String quizId) {
        if (!this.quizsTermines.contains(quizId)) {
            this.quizsTermines.add(quizId);
            this.dateDerniereActivite = LocalDateTime.now();
            calculerProgression();
        }
    }
    
    public void ajouterNoteDevoir(String devoirId, double note, double noteMax) {
        NoteDevoir noteDevoir = new NoteDevoir(devoirId, note, noteMax);
        this.notesDevoirs.add(noteDevoir);
        calculerMoyenneGlobale();
    }
    
    public void ajouterNoteQuiz(String quizId, double note, double noteMax) {
        NoteQuiz noteQuiz = new NoteQuiz(quizId, note, noteMax);
        this.notesQuizs.add(noteQuiz);
        calculerMoyenneGlobale();
    }
    
    private void calculerProgression() {
        // Logique de calcul de progression (à adapter selon votre système)
        int total = 0;
        int effectue = 0;
        
        if (!leconsTerminees.isEmpty()) {
            total += 100;
            effectue += leconsTerminees.size();
        }
        if (!devoirsSoumis.isEmpty()) {
            total += 50;
            effectue += devoirsSoumis.size();
        }
        if (!quizsTermines.isEmpty()) {
            total += 50;
            effectue += quizsTermines.size();
        }
        
        if (total > 0) {
            this.progression = Math.min(100, (effectue * 100.0 / total));
        }
    }
    public void setSource(String source) {
        this.source = source;
    }
    public String getSource() {
        return source;
    }
    private void calculerMoyenneGlobale() {
        double total = 0.0;
        int count = 0;
        
        for (NoteDevoir nd : notesDevoirs) {
            total += (nd.getNote() / nd.getNoteMax()) * 100;
            count++;
        }
        for (NoteQuiz nq : notesQuizs) {
            total += (nq.getNote() / nq.getNoteMax()) * 100;
            count++;
        }
        
        this.moyenneGlobale = count > 0 ? total / count : 0.0;
    }
    
    // Getters et Setters
    public String getEtudiantId() { return etudiantId; }
    public void setEtudiantId(String etudiantId) { this.etudiantId = etudiantId; }
    
    public double getProgression() { return progression; }
    public void setProgression(double progression) { this.progression = progression; }
    
    public List<String> getLeconsTerminees() { return leconsTerminees; }
    public void setLeconsTerminees(List<String> leconsTerminees) { this.leconsTerminees = leconsTerminees; }
    
    public List<String> getDevoirsSoumis() { return devoirsSoumis; }
    public void setDevoirsSoumis(List<String> devoirsSoumis) { this.devoirsSoumis = devoirsSoumis; }
    
    public List<String> getQuizsTermines() { return quizsTermines; }
    public void setQuizsTermines(List<String> quizsTermines) { this.quizsTermines = quizsTermines; }
    
    public double getMoyenneGlobale() { return moyenneGlobale; }
    public void setMoyenneGlobale(double moyenneGlobale) { this.moyenneGlobale = moyenneGlobale; }
    
    public LocalDateTime getDateDerniereActivite() { return dateDerniereActivite; }
    public void setDateDerniereActivite(LocalDateTime dateDerniereActivite) { this.dateDerniereActivite = dateDerniereActivite; }
    
    public LocalDateTime getDateInscription() { return dateInscription; }
    public void setDateInscription(LocalDateTime dateInscription) { this.dateInscription = dateInscription; }
    
    public boolean isaObtenuCertificat() { return aObtenuCertificat; }
    public void setaObtenuCertificat(boolean aObtenuCertificat) { this.aObtenuCertificat = aObtenuCertificat; }
    
    public double getNoteFinale() { return noteFinale; }
    public void setNoteFinale(double noteFinale) { this.noteFinale = noteFinale; }
    
    public List<NoteDevoir> getNotesDevoirs() { return notesDevoirs; }
    public List<NoteQuiz> getNotesQuizs() { return notesQuizs; }
    
    // Classes internes
    public static class NoteDevoir {
        private String devoirId;
        private double note;
        private double noteMax;
        private LocalDateTime dateSoumission;
        
        public NoteDevoir(String devoirId, double note, double noteMax) {
            this.devoirId = devoirId;
            this.note = note;
            this.noteMax = noteMax;
            this.dateSoumission = LocalDateTime.now();
        }
        
        // Getters et Setters
        public String getDevoirId() { return devoirId; }
        public void setDevoirId(String devoirId) { this.devoirId = devoirId; }
        
        public double getNote() { return note; }
        public void setNote(double note) { this.note = note; }
        
        public double getNoteMax() { return noteMax; }
        public void setNoteMax(double noteMax) { this.noteMax = noteMax; }
        
        public LocalDateTime getDateSoumission() { return dateSoumission; }
        public void setDateSoumission(LocalDateTime dateSoumission) { this.dateSoumission = dateSoumission; }
    }
    
    public static class NoteQuiz {
        private String quizId;
        private double note;
        private double noteMax;
        private LocalDateTime dateCompletion;
        
        public NoteQuiz(String quizId, double note, double noteMax) {
            this.quizId = quizId;
            this.note = note;
            this.noteMax = noteMax;
            this.dateCompletion = LocalDateTime.now();
        }
        
        // Getters et Setters
        public String getQuizId() { return quizId; }
        public void setQuizId(String quizId) { this.quizId = quizId; }
        
        public double getNote() { return note; }
        public void setNote(double note) { this.note = note; }
        
        public double getNoteMax() { return noteMax; }
        public void setNoteMax(double noteMax) { this.noteMax = noteMax; }
        
        public LocalDateTime getDateCompletion() { return dateCompletion; }
        public void setDateCompletion(LocalDateTime dateCompletion) { this.dateCompletion = dateCompletion; }
    }
}