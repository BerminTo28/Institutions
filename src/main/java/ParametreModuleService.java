import java.util.ArrayList;
import java.util.List;

public class ParametreModuleService {

    /** Construit la liste des modules disponibles selon le contexte. */
    public List<UIParametres.ParametreModule> listerModules() {
        List<UIParametres.ParametreModule> modules = new ArrayList<>();

        modules.add(new UIParametres.ParametreModule(
            "Profil", "fas fa-user",
            "Gérer les informations de l'institution",
            "/admin/parametres/profil", true));

        modules.add(new UIParametres.ParametreModule(
            "Année", "fas fa-calendar",
            "Gérer les années académiques",
            "/admin/parametres/annees", true));

        modules.add(new UIParametres.ParametreModule(
            "Période", "fas fa-clock",
            "Gérer les périodes",
            "/admin/parametres/periodes", true));

        modules.add(new UIParametres.ParametreModule(
            "Promotions", "fas fa-graduation-cap",
            "Gérer les promotions",
            "/admin/parametres/promotions", true));

        modules.add(new UIParametres.ParametreModule(
            "Options", "fas fa-list-ul",
            "Gérer les options (filières)",
            "/admin/parametres/options", true));

        modules.add(new UIParametres.ParametreModule(
            "Double Saisie", "fas fa-clipboard-list",
            "Configurer la double saisie",
            "/admin/parametres/double-saisie", true));

        modules.add(new UIParametres.ParametreModule(
            "Accès", "fas fa-lock",
            "Gérer les permissions des administrateurs",
            "/admin/acces", true));

        modules.add(new UIParametres.ParametreModule(
            "Inscriptions", "fas fa-door-open",
            "Configurer les inscriptions",
            "/admin/parametres/inscription", true));

        modules.add(new UIParametres.ParametreModule(
            "Sauvegarde", "fas fa-database",
            "Gérer les sauvegardes",
            "/admin/parametres/sauvegarde", true));

        return modules;
    }
}