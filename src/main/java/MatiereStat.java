public class MatiereStat {
    private final String nom;
    private final double moyenneNormalisee;
    private final double coefficient;
    private final double pourcentage;

    // constructeur, getters, setters
    public MatiereStat(String nom, double moyenneNormalisee, double coefficient, double pourcentage) {
        this.nom = nom;
        this.moyenneNormalisee = moyenneNormalisee;
        this.coefficient = coefficient;
        this.pourcentage = pourcentage;
    }

    public String getNom() { return nom; }
    public double getMoyenneNormalisee() { return moyenneNormalisee; }
    public double getCoefficient() { return coefficient; }
    public double getPourcentage() { return pourcentage; }
}