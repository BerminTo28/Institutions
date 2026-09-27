import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class DashboardAdminService {

    private static final Logger LOGGER = Logger.getLogger(DashboardAdminService.class.getName());

    private static final long CACHE_TTL_MS = 30_000L;

    private volatile List<Module> cachedModules = null;
    private volatile long cacheTimestamp = 0L;
    private volatile String cachedInstitutionId = null;

    // =========================================================
    // INSTITUTION
    // =========================================================

    public String getNomInstitution(String institutionId) {
        if (institutionId == null) return "Institution";
        String sql = "SELECT nom_institution FROM institutions WHERE institution_id = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String nom = rs.getString("nom_institution");
                    return (nom != null && !nom.isBlank()) ? nom : "Institution";
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erreur getNomInstitution", e);
        }
        return "Institution";
    }

    // =========================================================
    // LOGO
    // =========================================================

    public String findLogoFile(String institutionId) {
        if (institutionId == null) return null;
        Path baseDir = Paths.get(DashboardConstants.PHOTOS_DIR);
        if (!Files.exists(baseDir)) return null;

        try (Stream<Path> paths = Files.list(baseDir)) {
            return paths
                    .filter(p -> p.getFileName().toString().startsWith("logo_" + institutionId))
                    .map(p -> DashboardConstants.PHOTOS_URL_PATH + p.getFileName().toString())
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            LOGGER.fine(() -> "Logo introuvable pour " + institutionId);
            return null;
        }
    }

    public String getLogoPathFromDB(String institutionId) {
        if (institutionId == null) return null;
        String sql = "SELECT logo_institution FROM institutions WHERE institution_id = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String logo = rs.getString("logo_institution");
                    if (logo != null && !logo.isBlank()) return logo;
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erreur getLogoPathFromDB", e);
        }
        return null;
    }

    // =========================================================
    // MODULES & ACCÈS  ✅ CORRIGÉ
    // =========================================================

    public List<Module> obtenirModulesAccessibles(String institutionId, String utilisateurId) {
        List<Module> charge = chargerModulesAvecCache(institutionId);
        if (charge.isEmpty()) {
            charge = getModulesParDefaut();
        }
        final List<Module> allModules = charge;

        // ✅ CORRIGÉ : plus de "mode dégradé" qui renvoie TOUS les modules.
        //    Si on n'a pas d'utilisateur, on ne peut PAS déterminer les accès → denyAll.
        if (utilisateurId == null || utilisateurId.isBlank()) {
            LOGGER.warning("⚠️ utilisateurId null/vide → AUCUN module retourné (denyAll)");
            return new ArrayList<>();
        }

        AccesAdmin acces = chargerPermissions(utilisateurId, institutionId);
        if (acces == null) {
            LOGGER.warning(() -> "AccesAdmin null pour " + utilisateurId + " → aucun module");
            return new ArrayList<>();
        }

        // ✅ Log de diagnostic pour voir ce qui est réellement chargé
        LOGGER.info(() -> "🔐 AccesAdmin chargé pour " + utilisateurId
                + " [institution=" + institutionId + "] : " + acces);

        List<Module> modulesAccessibles = new ArrayList<>();
        List<String> modulesSansCle = new ArrayList<>();
        List<String> modulesRefuses = new ArrayList<>();

        for (Module module : allModules) {
            String permissionKey = module.getPermissionKey();

            if (permissionKey == null || permissionKey.isBlank()) {
                modulesSansCle.add(module.getNom() != null ? module.getNom() : module.getId());
                modulesAccessibles.add(module);
                continue;
            }

            boolean ok = acces.hasAccessForModule(permissionKey);
            if (ok) {
                modulesAccessibles.add(module);
            } else {
                modulesRefuses.add(module.getNom() + "(" + permissionKey + ")");
            }
        }

        if (!modulesSansCle.isEmpty()) {
            final List<String> sansCle = new ArrayList<>(modulesSansCle);
            LOGGER.warning(() -> "⚠️ Modules sans permission_key (affichés par défaut) : " + sansCle);
        }

        if (!modulesRefuses.isEmpty()) {
            final List<String> refuses = new ArrayList<>(modulesRefuses);
            LOGGER.info(() -> "🚫 Modules refusés pour " + utilisateurId + " : " + refuses);
        }

        LOGGER.info(() -> "📋 " + modulesAccessibles.size() + "/" + allModules.size()
                + " modules accessibles pour " + utilisateurId);

        return modulesAccessibles;
    }

    private List<Module> chargerModulesAvecCache(String institutionId) {
        long now = System.currentTimeMillis();
        boolean cacheValide = cachedModules != null
                && cachedInstitutionId != null
                && cachedInstitutionId.equals(institutionId)
                && (now - cacheTimestamp) < CACHE_TTL_MS;

        if (cacheValide) {
            return cachedModules;
        }

        List<Module> modules = chargerModules(institutionId);
        cachedModules = modules;
        cachedInstitutionId = institutionId;
        cacheTimestamp = now;
        return modules;
    }

    public void invaliderCacheModules() {
        cachedModules = null;
        cachedInstitutionId = null;
        cacheTimestamp = 0L;
    }

    private AccesAdmin chargerPermissions(String utilisateurId, String institutionId) {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            AccesAdminData dao = new AccesAdminData(conn);
            AccesAdmin acces = dao.loadByUtilisateurId(utilisateurId, institutionId);
            // ✅ Ne JAMAIS retourner null silencieusement
            if (acces == null) {
                LOGGER.warning(() -> "⚠️ AccesAdminData.loadByUtilisateurId a retourné null "
                        + "pour user=" + utilisateurId + " inst=" + institutionId);
                return new AccesAdmin(); // denyAll
            }
            return acces;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Erreur SQL chargement permissions user=" + utilisateurId
                            + " inst=" + institutionId);
            return new AccesAdmin(); // denyAll
        }
    }

    private List<Module> chargerModules(String institutionId) {
        List<Module> modules = new ArrayList<>();
        if (institutionId == null || institutionId.isBlank()) {
            return modules;
        }

        String sql = "SELECT id, nom_module, icone, lien, description, permission_key "
                + "FROM modules WHERE institution_id = ? ORDER BY ordre";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Module module = new Module();
                    module.setId(rs.getString("id"));
                    module.setNom(rs.getString("nom_module"));
                    module.setIcone(rs.getString("icone"));
                    module.setLien(rs.getString("lien"));
                    module.setDescription(rs.getString("description"));
                    module.setPermissionKey(rs.getString("permission_key"));
                    modules.add(module);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erreur chargement modules depuis DB", e);
        }
        return modules;
    }

    /**
     * ✅ CORRIGÉ : les clés de permission DOIVENT correspondre EXACTEMENT
     *    aux constantes de AccesAdmin.Module.
     *    "ETUDIANTS" (pluriel) est utilisé — aligné sur AccesAdmin.Module.ETUDIANTS.
     */
    private List<Module> getModulesParDefaut() {
        List<Module> modules = new ArrayList<>();
        Object[][] data = {
                {"Classes",         "fas fa-users-between-lines",   "/admin/classes",       "Gestion des classes",              "CLASSE"},
                {"Matières",        "fas fa-book-open",              "/admin/matieres",      "Gestion des matières",             "MATIERE"},
                {"Professeurs",     "fas fa-chalkboard-teacher",     "/admin/professeurs",   "Gestion des professeurs",          "PROFESSEUR"},
                {"Étudiants",       "fas fa-graduation-cap",         "/admin/etudiants",     "Gestion des étudiants",            "ETUDIANTS"},
                {"Notes",           "fas fa-file-pen",               "/admin/notes",         "Gestion des notes",                "NOTE"},
                {"Examens",         "fas fa-pencil-alt",             "/admin/examens",       "Gestion des examens",              "EXAMEN"},
                {"Bulletins",       "fas fa-file-pdf",               "/admin/bulletins",     "Génération des bulletins",         "BULLETIN"},
                {"Palmarès",        "fas fa-trophy",                 "/admin/palmares",      "Classements et résultats",         "PALMARES"},
                {"Attestations",    "fas fa-certificate",            "/admin/attestations",  "Génération d'attestations",        "ATTESTATION"},
                {"Événements",      "fas fa-calendar-days",          "/admin/evenements",    "Gestion des événements",           "EVENEMENTS"},
                {"Absences",        "fas fa-user-slash",             "/admin/absences",      "Suivi des absences",               "ABSENCE"},
                {"Emploi du Temps", "fas fa-book",                   "/admin/emploidutemps", "Planification des horaires",       "EMPLOI_DU_TEMPS"},
                {"Paramètres",      "fas fa-gear",                   "/admin/parametres",    "Configuration de l'application",   "PARAMETRES"},
                {"Économie",        "fas fa-coins",                  "/admin/economie",      "Gestion des paiements et finances","ECONOMIE"},
                {"Communication",   "fas fa-bullhorn",               "/admin/communication", "Messagerie et notifications",      "COMMUNICATION"},
                {"Statistiques",    "fas fa-chart-pie",              "/admin/statistiques",  "Tableaux de bord et analyses",     "STATISTIQUES"}
        };

        for (Object[] d : data) {
            Module m = new Module();
            m.setId("mod_" + d[4]);
            m.setNom((String) d[0]);
            m.setIcone((String) d[1]);
            m.setLien((String) d[2]);
            m.setDescription((String) d[3]);
            m.setPermissionKey((String) d[4]);
            modules.add(m);
        }
        return modules;
    }
}