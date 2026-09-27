
public enum TypeDevoir {
    QUESTIONNAIRE("Questionnaire"),
    PROJET("Projet"),
    RECHERCHE("Recherche"),
    RAPPORT("Rapport"),
    PRESENTATION("Présentation");
    
    private final String libelle;
    
    TypeDevoir(String libelle) {
        this.libelle = libelle;
    }
    
    public String getLibelle() {
        return libelle;
    }
    
    @Override
    public String toString() {
        return libelle;
    }
}