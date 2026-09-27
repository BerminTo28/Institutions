public class ModuleApp {

    private Long id;
    private String codeModule;      // Ex: "CLASSE", "MATIERE", "PROFESSEUR"
    private String nomModule;       // Ex: "Gestion des Classes"
    private String icone;           // Nom de l'icône FontAwesome ou URL SVG (ex: "fa-chalkboard")
    private String lienUrl;         // URL ou route web (ex: "/admin/classes")
    private int ordreAffichage;
    private boolean actif;

    public ModuleApp() {}

    public ModuleApp(Long id, String codeModule, String nomModule, String icone, String lienUrl) {
        this.id = id;
        this.codeModule = codeModule;
        this.nomModule = nomModule;
        this.icone = icone;
        this.lienUrl = lienUrl;
        this.actif = true;
    }

    // --- Getters et Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCodeModule() { return codeModule; }
    public void setCodeModule(String codeModule) { this.codeModule = codeModule; }

    public String getNomModule() { return nomModule; }
    public void setNomModule(String nomModule) { this.nomModule = nomModule; }

    public String getIcone() { return icone; }
    public void setIcone(String icone) { this.icone = icone; }

    public String getLienUrl() { return lienUrl; }
    public void setLienUrl(String lienUrl) { this.lienUrl = lienUrl; }

    public int getOrdreAffichage() { return ordreAffichage; }
    public void setOrdreAffichage(int ordreAffichage) { this.ordreAffichage = ordreAffichage; }

    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }
}