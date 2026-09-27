import java.util.ArrayList;
import java.util.List;

public class PalmaresEntry {
    private String id;
    private String nom;
    private String prenom;
    private String matricule;
    private double moyenne;
    private String mention;
    private int rang;
    private List<MatiereNote> matieres = new ArrayList<>();

    // Getters et Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public String getPrenom() { return prenom; }
    public void setPrenom(String prenom) { this.prenom = prenom; }

    public String getMatricule() { return matricule; }
    public void setMatricule(String matricule) { this.matricule = matricule; }

    public double getMoyenne() { return moyenne; }
    public void setMoyenne(double moyenne) { this.moyenne = moyenne; }

    public String getMention() { return mention; }
    public void setMention(String mention) { this.mention = mention; }

    public int getRang() { return rang; }
    public void setRang(int rang) { this.rang = rang; }

    public List<MatiereNote> getMatieres() { return matieres; }
    public void setMatieres(List<MatiereNote> matieres) { this.matieres = matieres; }

    public static class MatiereNote {
        private String nom;
        private double note;
        private double noteSur;
        private double coefficient;

        public String getNom() { return nom; }
        public void setNom(String nom) { this.nom = nom; }

        public double getNote() { return note; }
        public void setNote(double note) { this.note = note; }

        public double getNoteSur() { return noteSur; }
        public void setNoteSur(double noteSur) { this.noteSur = noteSur; }

        public double getCoefficient() { return coefficient; }
        public void setCoefficient(double coefficient) { this.coefficient = coefficient; }
    }
}