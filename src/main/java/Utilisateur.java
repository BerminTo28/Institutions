import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class Utilisateur {
    private String institutionId;
    private final String identifiant;
    private final String nom;
    private final String prenom;
    private final String email;
    private final String numeroIdentifiant;
    private final int niveau;
    private final AccesAdmin acces;
    private final List<String> modules;
    private final String dateCreation;

    public Utilisateur(String identifiant, String nom, String prenom, String email, int niveau, AccesAdmin acces, List<String> modules) {
        this(identifiant, nom, prenom, email, "", niveau, acces, modules);
    }

    public Utilisateur(String identifiant, String nom, String prenom, String email, String numeroIdentifiant, int niveau, AccesAdmin acces, List<String> modules) {
        this.identifiant = Objects.requireNonNullElse(identifiant, "");
        this.nom = Objects.requireNonNullElse(nom, "");
        this.prenom = Objects.requireNonNullElse(prenom, "");
        this.email = Objects.requireNonNullElse(email, "");
        this.numeroIdentifiant = Objects.requireNonNullElse(numeroIdentifiant, "");
        this.niveau = niveau;
        this.acces = acces != null ? acces : new AccesAdmin();
        this.modules = modules;
        this.dateCreation = new Date().toString();
    }
public String getInstitutionId(){
    return institutionId;
}
public void setInstitutionId(String institutionId){
    this.institutionId=institutionId;

}
    public String getIdentifiant() {
        return identifiant;
    }

    public String getNom() {
        return nom;
    }

    public String getPrenom() {
        return prenom;
    }

    public String getEmail() {
        return email;
    }

    public int getNiveau() {
        return niveau;
    }

    public AccesAdmin getAcces() {
        return acces;
    }

    public List<String> getModules() {
        return modules;
    }

    public String getNumeroIdentifiant() {
        return numeroIdentifiant;
    }

    public String getDateCreation() {
        return dateCreation;
    }

    /**
     * Vérifie si l'utilisateur a le droit de créer pour le module CLASSE.
     * @return true s'il a la permission CREATE sur CLASSE
     */
    public boolean canCreate() {
        return acces != null && acces.hasPermission(AccesAdmin.Module.CLASSE, AccesAdmin.Action.CREATE);
    }

    /**
     * Vérifie si l'utilisateur a le droit de modifier pour le module CLASSE.
     * @return true s'il a la permission UPDATE sur CLASSE
     */
    public boolean canModify() {
        return acces != null && acces.hasPermission(AccesAdmin.Module.CLASSE, AccesAdmin.Action.UPDATE);
    }

    /**
     * Vérifie si l'utilisateur a le droit de supprimer pour le module CLASSE.
     * @return true s'il a la permission DELETE sur CLASSE
     */
    public boolean canDelete() {
        return acces != null && acces.hasPermission(AccesAdmin.Module.CLASSE, AccesAdmin.Action.DELETE);
    }

    public String getModulesAsString() {
        if (modules == null || modules.isEmpty()) {
            return "Aucun module";
        }
        return modules.stream().map(String::valueOf).collect(Collectors.joining(", "));
    }

    /**
     * Retourne une chaîne décrivant les droits CRUD pour le module CLASSE.
     */
    public String getDroitsAsString() {
        StringBuilder sb = new StringBuilder();
        if (canCreate()) sb.append("Création, ");
        if (canModify()) sb.append("Modification, ");
        if (canDelete()) sb.append("Suppression, ");
        if (sb.isEmpty()) {
            return "Aucun droit CRUD sur CLASSE";
        }
        return sb.substring(0, sb.length() - 2);
    }
}