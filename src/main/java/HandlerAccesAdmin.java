import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.logging.Logger;

public class HandlerAccesAdmin {

    private static final Logger LOGGER = Logger.getLogger(HandlerAccesAdmin.class.getName());

    private final AccesAdminData data;
    private AccesAdmin currentPermissions;
    private String currentUserId;
    private String currentInstitutionId;
    private boolean autoSave = true;

    /** ✅ Trace les modifications non sauvegardées. */
    private boolean dirty = false;

    /**
     * Constructeur avec la connexion (crée un AccesAdminData en interne).
     */
    public HandlerAccesAdmin(Connection connection) {
        this.data = new AccesAdminData(connection);
        this.currentPermissions = null;
        this.currentUserId = null;
        this.currentInstitutionId = null;
    }

    /**
     * Constructeur avec un AccesAdminData déjà instancié.
     */
    public HandlerAccesAdmin(AccesAdminData data) {
        if (data == null) {
            throw new IllegalArgumentException("AccesAdminData ne peut pas être nul");
        }
        this.data = data;
        this.currentPermissions = null;
        this.currentUserId = null;
        this.currentInstitutionId = null;
    }

    // =========================================================
    // CHARGEMENT
    // =========================================================

    /**
     * Charge les permissions pour un utilisateur donné dans une institution donnée.
     */
    public HandlerAccesAdmin loadUser(String utilisateurId, String institutionId) {
        if (utilisateurId == null || utilisateurId.isBlank()) {
            throw new IllegalArgumentException(
                    "L'identifiant utilisateur ne peut pas être null ou vide");
        }
        if (institutionId == null || institutionId.isBlank()) {
            throw new IllegalArgumentException(
                    "L'identifiant institution ne peut pas être null ou vide");
        }

        if (dirty) {
            LOGGER.warning("⚠️ Modifications non sauvegardées — elles seront perdues");
        }

        this.currentUserId = utilisateurId;
        this.currentInstitutionId = institutionId;
        this.currentPermissions = data.loadByUtilisateurId(utilisateurId, institutionId);
        this.dirty = false;

        LOGGER.info(() -> "Permissions chargées pour l'utilisateur "
                + utilisateurId + " dans " + institutionId);
        return this;
    }

    public String getCurrentUserId() {
        return currentUserId;
    }

    public String getCurrentInstitutionId() {
        return currentInstitutionId;
    }

    public AccesAdmin getPermissions() {
        return currentPermissions;
    }

    /** ✅ Indique si des modifications n'ont pas été sauvegardées. */
    public boolean isDirty() {
        return dirty;
    }

    // =========================================================
    // VÉRIFICATION DES PERMISSIONS
    // =========================================================

    public boolean hasPermission(AccesAdmin.Module module, AccesAdmin.Action action) {
        if (module == null || action == null) return false;
        if (currentPermissions == null) {
            LOGGER.warning("Aucun utilisateur chargé, permission refusée par défaut");
            return false;
        }
        return currentPermissions.hasPermission(module, action);
    }

    public boolean hasAccessForModule(String permissionKey) {
        if (permissionKey == null || permissionKey.isBlank()) return false;
        if (currentPermissions == null) {
            LOGGER.warning("Aucun utilisateur chargé, accès refusé par défaut");
            return false;
        }
        return currentPermissions.hasAccessForModule(permissionKey);
    }

    // =========================================================
    // COMMODITÉ (délégation)
    // =========================================================

    public boolean canReadClasse() {
        return hasPermission(AccesAdmin.Module.CLASSE, AccesAdmin.Action.READ);
    }
    public boolean canCreateClasse() {
        return hasPermission(AccesAdmin.Module.CLASSE, AccesAdmin.Action.CREATE);
    }
    public boolean canUpdateClasse() {
        return hasPermission(AccesAdmin.Module.CLASSE, AccesAdmin.Action.UPDATE);
    }
    public boolean canDeleteClasse() {
        return hasPermission(AccesAdmin.Module.CLASSE, AccesAdmin.Action.DELETE);
    }

    // =========================================================
    // MODIFICATION DES PERMISSIONS
    // =========================================================

    public boolean setPermission(AccesAdmin.Module module,
                                  AccesAdmin.Action action, boolean value) {
        if (module == null || action == null) {
            LOGGER.warning("setPermission() : module ou action null");
            return false;
        }
        checkUserLoaded();
        currentPermissions.setPermission(module, action, value);
        dirty = true;
        return persistIfNeeded();
    }

    public boolean setPermissionsForModule(AccesAdmin.Module module,
                                             EnumSet<AccesAdmin.Action> actions) {
        if (module == null || actions == null) {
            LOGGER.warning("setPermissionsForModule() : module ou actions null");
            return false;
        }
        checkUserLoaded();
        currentPermissions.setPermissionsForModule(module, actions);
        dirty = true;
        return persistIfNeeded();
    }

    public boolean setPermissions(Map<AccesAdmin.Module, EnumSet<AccesAdmin.Action>> permissions) {
        if (permissions == null) {
            LOGGER.warning("setPermissions() : permissions null");
            return false;
        }
        checkUserLoaded();
        currentPermissions.setPermissions(permissions);
        dirty = true;
        return persistIfNeeded();
    }

    public boolean grantAll() {
        checkUserLoaded();
        currentPermissions.grantAll();
        dirty = true;
        return persistIfNeeded();
    }

    public boolean revokeAll() {
        checkUserLoaded();
        currentPermissions.revokeAll();
        dirty = true;
        return persistIfNeeded();
    }

    /**
     * ✅ Factorise la logique "autoSave ? save() : true".
     *    Si autoSave=false, retourne true (modif en mémoire, dirty=true).
     *    Si autoSave=true, retourne le résultat de save().
     */
    private boolean persistIfNeeded() {
        if (!autoSave) {
            return true;
        }
        return save();
    }

    // =========================================================
    // SAUVEGARDE
    // =========================================================

    /**
     * Sauvegarde les permissions courantes dans la base de données.
     *
     * @return true si la sauvegarde a réussi, false sinon
     */
    public boolean save() {
        if (currentPermissions == null) {
            LOGGER.warning("Impossible de sauvegarder : aucun utilisateur chargé");
            return false;
        }
        if (currentUserId == null || currentUserId.isBlank()) {
            LOGGER.warning("Impossible de sauvegarder : utilisateurId manquant");
            return false;
        }
        if (currentInstitutionId == null || currentInstitutionId.isBlank()) {
            LOGGER.warning("Impossible de sauvegarder : institutionId manquant");
            return false;
        }

        boolean ok = false;
        try {
            ok = data.saveOrUpdate(currentPermissions,
                    currentUserId, currentInstitutionId); // ✅ Utilise le LOGGER de la classe (cohérent avec le reste)
            // ✅ Utilise le LOGGER de la classe (cohérent avec le reste)
        } catch (SQLException ex) {
            System.getLogger(HandlerAccesAdmin.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        }
        if (ok) {
            dirty = false;
            LOGGER.fine(() -> "💾 Permissions sauvegardées pour "
                    + currentUserId + " / " + currentInstitutionId);
        } else {
            LOGGER.warning(() -> "❌ Échec de sauvegarde pour "
                    + currentUserId + " / " + currentInstitutionId);
        }
        return ok;
    }

    public void setAutoSave(boolean autoSave) {
        this.autoSave = autoSave;
    }

    public boolean isAutoSave() {
        return autoSave;
    }

    // =========================================================
    // SUPPRESSION
    // =========================================================

    /**
     * Supprime les permissions de l'utilisateur courant.
     * Réinitialise l'état du handler en cas de succès.
     *
     * @return true si la suppression a réussi
     */
    public boolean deleteUser() {
        if (currentUserId == null || currentInstitutionId == null) {
            LOGGER.warning("deleteUser() : aucun utilisateur chargé");
            return false;
        }

        // ✅ Capture les IDs AVANT de les mettre à null (sinon log = null)
        final String userId = currentUserId;
        final String instId = currentInstitutionId;

        boolean ok = data.delete(userId, instId);
        if (ok) {
            currentPermissions = null;
            currentUserId = null;
            currentInstitutionId = null;
            dirty = false;
            LOGGER.info(() -> "✅ Permissions supprimées pour "
                    + userId + " / " + instId);
        } else {
            LOGGER.warning(() -> "❌ Échec de suppression pour "
                    + userId + " / " + instId);
        }
        return ok;
    }

    // =========================================================
    // MÉTHODES INTERNES
    // =========================================================

    private void checkUserLoaded() {
        if (currentPermissions == null) {
            throw new IllegalStateException(
                    "Aucun utilisateur chargé. Appelez loadUser() d'abord.");
        }
    }

    // =========================================================
    // AUTRES MÉTHODES UTILES
    // =========================================================

    /**
     * ✅ Retourne une map vide si aucun utilisateur chargé (évite NPE).
     */
    public Map<AccesAdmin.Module, EnumSet<AccesAdmin.Action>> getPermissionsMap() {
        if (currentPermissions == null) {
            return Collections.emptyMap();
        }
        return currentPermissions.getPermissions();
    }

    /**
     * Recharge les permissions depuis la base (écrase les modifications non sauvegardées).
     * ✅ Robuste : ne lance pas d'exception si les IDs sont invalides.
     */
    public HandlerAccesAdmin reload() {
        if (dirty) {
            LOGGER.warning("⚠️ Modifications non sauvegardées — elles seront perdues");
        }

        if (currentUserId == null || currentInstitutionId == null) {
            LOGGER.warning("Impossible de recharger : aucun utilisateur ou institution chargé");
            return this;
        }

        try {
            String userId = currentUserId;
            String instId = currentInstitutionId;
            loadUser(userId, instId);
        } catch (IllegalArgumentException e) {
            LOGGER.warning(() -> "Rechargement impossible : " + e.getMessage());
        }
        return this;
    }
}