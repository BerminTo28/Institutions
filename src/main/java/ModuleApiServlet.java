import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.google.gson.Gson;

@WebServlet("/api/modules")
public class ModuleApiServlet extends HttpServlet {

    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {

        // 1. Vérification de la session
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("adminId") == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            try (PrintWriter out = response.getWriter()) {
                out.print("{\"status\":401,\"error\":\"Non autorisé\"}");
            }
            return;
        }

        // 2. Récupération des IDs
        String adminId = (String) session.getAttribute("adminId");
        String institutionId = (String) session.getAttribute("institutionId");
        if (institutionId == null || institutionId.isBlank()) {
            institutionId = (String) session.getAttribute("institution_id");
        }
        if (institutionId == null || institutionId.isBlank()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("application/json;charset=UTF-8");
            try (PrintWriter out = response.getWriter()) {
                out.print("{\"status\":400,\"error\":\"Institution non trouvée\"}");
            }
            return;
        }

        // 3. Charger les modules filtrés
        List<Module> modules = chargerModulesAvecPermissions(institutionId, adminId);

        // 4. Convertir en JSON et envoyer
        String json = convertToJson(modules);
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.print(json);
        }
    }

    /**
     * Charge les modules de l'institution et filtre selon les permissions de l'administrateur.
     */
    private List<Module> chargerModulesAvecPermissions(String institutionId, String adminId) {
        List<Module> modulesAccessibles = new ArrayList<>();

        // 1. Charger tous les modules de l'institution (depuis la base ou par défaut)
        List<Module> tousModules = chargerModules(institutionId);

        // 2. Charger les permissions de l'administrateur
        AccesAdmin acces = chargerPermissions(adminId, institutionId);
        if (acces == null) {
            // Si aucune permission trouvée, on retourne une liste vide (sécurité)
            return modulesAccessibles;
        }

        // 3. Filtrer les modules selon les permissions
        for (Module module : tousModules) {
            if (acces.hasAccessForModule(module.getPermissionKey())) {
                modulesAccessibles.add(module);
            }
        }

        return modulesAccessibles;
    }

    /**
     * Charge les modules depuis la base de données, ou utilise la liste par défaut si la table est vide.
     */
    private List<Module> chargerModules(String institutionId) {
        List<Module> modules = new ArrayList<>();
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, nom_module, icone, lien, description, ordre, permission_key " +
                 "FROM modules WHERE institution_id = ? ORDER BY ordre")) {
            ps.setString(1, institutionId);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Module m = new Module();
                    m.setId(rs.getString("id"));
                    m.setNom(rs.getString("nom_module"));
                    m.setIcone(rs.getString("icone"));
                    m.setLien(rs.getString("lien"));
                    m.setDescription(rs.getString("description"));
                    m.setOrdre(rs.getInt("ordre"));
                    m.setPermissionKey(rs.getString("permission_key"));
                    modules.add(m);
                }
            }
        } catch (SQLException e) {
            // Table non existante → utiliser la liste par défaut
            modules = getModulesParDefaut();
        }
        if (modules.isEmpty()) {
            modules = getModulesParDefaut();
        }
        return modules;
    }

    /**
     * Charge les permissions de l'administrateur depuis la table acces_admin.
     */
    private AccesAdmin chargerPermissions(String adminId, String institutionId) {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            AccesAdminData dao = new AccesAdminData(conn);
            return dao.loadByUtilisateurId(adminId, institutionId);
        } catch (SQLException e) {
            // Si la table n'existe pas, on retourne un objet avec toutes les permissions à false
            return new AccesAdmin();
        }
    }

    /**
     * Liste par défaut des modules (si la base est vide ou inaccessible).
     * Chaque module est associé à une clé de permission.
     */
    private List<Module> getModulesParDefaut() {
        List<Module> modules = new ArrayList<>();
        // Données : id, nom, icône, lien, description, ordre, permissionKey
        Object[][] data = {
            {"classe", "Classes", "fa-solid fa-users-between-lines", "/admin/classes", "Gestion des classes", 1, "CLASSE"},
            {"matiere", "Matières", "fa-solid fa-book-open", "/admin/matieres", "Gestion des matières", 2, "MATIERE"},
            {"professeur", "Professeurs", "fa-solid fa-chalkboard-teacher", "/admin/professeurs", "Gestion des professeurs", 3, "PROFESSEUR"},
            {"etudiant", "Étudiants", "fa-solid fa-graduation-cap", "/admin/etudiants", "Gestion des étudiants", 4, "ETUDIANTS"},
            {"note", "Notes & Examens", "fa-solid fa-file-pen", "/admin/notes", "Gestion des notes", 5, "NOTE"},
            {"bulletin", "Bulletins", "fa-solid fa-file-pdf", "/admin/bulletins", "Génération des bulletins", 6, "BULLETIN"},
            {"palmares", "Palmarès", "fa-solid fa-trophy", "/admin/palmares", "Classements et résultats", 7, "PALMARES"},
            {"attestation", "Attestations", "fa-solid fa-certificate", "/admin/attestations", "Génération d'attestations", 8, "ATTESTATION"},
            {"evenement", "Événements", "fa-solid fa-calendar-days", "/admin/evenements", "Gestion des événements", 9, "EVENEMENTS"},
            {"absence", "Mes Absences", "fa-solid fa-user-slash", "/etudiant/absences", "Consultation de vos absences", 10, "ABSENCE_ETUDIANT"},
            {"parametres", "Paramètres", "fa-solid fa-gear", "/admin/parametres", "Configuration de l'application", 11, "PARAMETRES"},
            {"economie", "Économie", "fa-solid fa-coins", "/admin/economie", "Gestion des paiements et finances", 12, "ECONOMIE"},
            {"communication", "Communication", "fa-solid fa-envelope", "/admin/communication", "Messagerie et notifications", 13, "COMMUNICATION"},
            {"statistiques", "Statistiques", "fa-solid fa-chart-pie", "/admin/statistiques", "Tableaux de bord et analyses", 14, "STATISTIQUES"},
            {"emploidutemps", "Emploi du Temps", "fa-solid fa-book", "/admin/emploidutemps", "Planification des horaires", 15, "EMPLOI_DU_TEMPS"},
            {"examen", "Examens", "fa-solid fa-pencil-alt", "/admin/examens", "Gestion des examens", 16, "EXAMEN"}
        };

        for (Object[] d : data) {
            Module m = new Module();
            m.setId((String) d[0]);
            m.setNom((String) d[1]);
            m.setIcone((String) d[2]);
            m.setLien((String) d[3]);
            m.setDescription((String) d[4]);
            m.setOrdre((int) d[5]);
            m.setPermissionKey((String) d[6]);
            modules.add(m);
        }
        return modules;
    }

    /**
     * Convertit la liste de modules en chaîne JSON.
     */
    private String convertToJson(List<Module> modules) {
        if (modules == null || modules.isEmpty()) {
            return "[]";
        }

        List<Map<String, Object>> jsonList = new ArrayList<>();
        for (Module module : modules) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", module.getId());
            map.put("nom", module.getNom());
            map.put("icone", module.getIcone());
            map.put("lien", module.getLien());
            map.put("description", module.getDescription());
            map.put("ordre", module.getOrdre());
            // On peut aussi ajouter la permissionKey si nécessaire
            map.put("permissionKey", module.getPermissionKey());
            jsonList.add(map);
        }
        return gson.toJson(jsonList);
    }
}