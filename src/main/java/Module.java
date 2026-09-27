public class Module {
    private String id;
    private String nom;
    private String icone;
    private String lien;
    private String description;
    private int ordre;
    private String permissionKey; // Correspond à AccesAdmin.Module

    public Module() {
    }

    public Module(String id, String nom, String icone, String lien, String description, int ordre, String permissionKey) {
        this.id = id;
        this.nom = nom;
        this.icone = icone;
        this.lien = lien;
        this.description = description;
        this.ordre = ordre;
        this.permissionKey = permissionKey;
    }

    // Getters et Setters pour tous les champs
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public String getIcone() { return icone; }
    public void setIcone(String icone) { this.icone = icone; }

    public String getLien() { return lien; }
    public void setLien(String lien) { this.lien = lien; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getOrdre() { return ordre; }
    public void setOrdre(int ordre) { this.ordre = ordre; }

    public String getPermissionKey() { return permissionKey; }
    public void setPermissionKey(String permissionKey) { this.permissionKey = permissionKey; }

    /**
     * Vérifie si l'utilisateur a accès en lecture à ce module.
     * @param acces l'objet AccesAdmin contenant les permissions
     * @return true si la permission de lecture est accordée, false sinon
     */
    public boolean isAccessible(AccesAdmin acces) {
        if (permissionKey == null || permissionKey.isBlank()) {
            return false;
        }
        try {
            AccesAdmin.Module moduleEnum = AccesAdmin.Module.valueOf(permissionKey.toUpperCase());
            return acces.hasPermission(moduleEnum, AccesAdmin.Action.READ);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public String toString() {
        return "Module{" +
                "id='" + id + '\'' +
                ", nom='" + nom + '\'' +
                ", icone='" + icone + '\'' +
                ", lien='" + lien + '\'' +
                ", description='" + description + '\'' +
                ", ordre=" + ordre +
                ", permissionKey='" + permissionKey + '\'' +
                '}';
    }
}