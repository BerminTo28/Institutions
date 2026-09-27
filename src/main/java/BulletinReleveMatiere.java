import java.util.LinkedHashMap;
import java.util.Map;

public class BulletinReleveMatiere {

    private final String anneeAcademique;
    private final String codeCours;
    private final String intituleMatiere;

    private final double coefficient;
    private final double noteMaximale;

    private final Map<String, Double> notesParSession =
            new LinkedHashMap<>();

    public BulletinReleveMatiere(
            String anneeAcademique,
            String codeCours,
            String intituleMatiere,
            double coefficient,
            double noteMaximale) {

        this.anneeAcademique = anneeAcademique;
        this.codeCours = codeCours;
        this.intituleMatiere = intituleMatiere;
        this.coefficient = coefficient;
        this.noteMaximale = noteMaximale;
    }

    public String getAnneeAcademique() {
        return anneeAcademique;
    }

    public String getCodeCours() {
        return codeCours;
    }

    public String getIntituleMatiere() {
        return intituleMatiere;
    }

    public double getCoefficient() {
        return coefficient;
    }

    public double getNoteMaximale() {
        return noteMaximale;
    }

    public void ajouterNote(String session, double note) {
        notesParSession.put(session, note);
    }

    public double getNoteBrute(String session) {
        return notesParSession.getOrDefault(session, -1.0);
    }

    public double getNotePourcentage(String session) {

        double brute = getNoteBrute(session);

        if (brute < 0) {
            return -1.0;
        }

        return (brute / noteMaximale) * 100.0;
    }
}