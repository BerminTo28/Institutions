import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class AccesAdmin {

    private String source;
    private Map<Module, EnumSet<Action>> permissions = new HashMap<>();

    public enum Module {
        CLASSE, MATIERE, UTILISATEUR, NOTE, BULLETIN,
        ECONOMIE, ATTESTATION, EMPLOI_DU_TEMPS, EVENEMENTS, ETUDIANTS,
        PALMARES, ABSENCE, EXAMEN, PROFESSEUR, PARAMETRES, COMMUNICATION,
        STATISTIQUES, PROGRAMMES, DOCUMENTS, MESSAGES, ASSISTANCE,
        MODULES, PROMOTIONS, OPTIONS, PERIODES, ANNEES_ACADEMIQUES
    }

    public enum Action {
        READ, CREATE, UPDATE, DELETE
    }

    public AccesAdmin() {
        for (Module module : Module.values()) {
            permissions.put(module, EnumSet.noneOf(Action.class));
        }
    }

    // =========================================================
    // PERMISSIONS
    // =========================================================

    public boolean hasPermission(Module module, Action action) {
        if (module == null || action == null) return false;
        return permissions.getOrDefault(module, EnumSet.noneOf(Action.class)).contains(action);
    }

    public void setPermission(Module module, Action action, boolean value) {
        if (module == null || action == null) return;
        EnumSet<Action> actions = permissions.computeIfAbsent(
                module, k -> EnumSet.noneOf(Action.class));
        if (value) actions.add(action);
        else actions.remove(action);
    }

    public void setPermissionsForModule(Module module, EnumSet<Action> actions) {
        if (module == null) return;
        permissions.put(module, actions != null
                ? EnumSet.copyOf(actions)
                : EnumSet.noneOf(Action.class));
    }

    public EnumSet<Action> getPermissionsForModule(Module module) {
        if (module == null) return EnumSet.noneOf(Action.class);
        EnumSet<Action> actions = permissions.get(module);
        return actions != null ? EnumSet.copyOf(actions) : EnumSet.noneOf(Action.class);
    }

    public boolean hasAccessForModule(String permissionKey) {
        if (permissionKey == null || permissionKey.isBlank()) {
            return false;
        }
        String key = permissionKey.toUpperCase().trim();

        // ① Essayer MODULE_ACTION en testant TOUTES les actions connues
        //    (au lieu de lastIndexOf('_') qui casse sur EMPLOI_DU_TEMPS_*)
        for (Action action : Action.values()) {
            String suffix = "_" + action.name();
            if (key.endsWith(suffix)) {
                String modulePart = key.substring(0, key.length() - suffix.length());
                try {
                    Module module = Module.valueOf(modulePart);
                    return hasPermission(module, action);
                } catch (IllegalArgumentException ignored) {
                    // Le préfixe n'est pas un Module connu → on continue
                }
            }
        }

        // ② Sinon : MODULE seul → READ par défaut
        try {
            Module module = Module.valueOf(key);
            return hasPermission(module, Action.READ);
        } catch (IllegalArgumentException ignored) {
            // Clé inconnue
        }

        return false;
    }

    /**
     * ✅ Variante utilitaire : vérifie une Action précise.
     *    Utile si un jour vous voulez distinguer READ vs CREATE.
     */
    public boolean hasAccessForModule(String permissionKey, Action action) {
        if (permissionKey == null || permissionKey.isBlank() || action == null) {
            return false;
        }
        String key = permissionKey.toUpperCase().trim();

        // Si la clé contient déjà un suffixe d'action, on l'ignore et on
        // utilise l'action passée explicitement.
        for (Action a : Action.values()) {
            String suffix = "_" + a.name();
            if (key.endsWith(suffix)) {
                key = key.substring(0, key.length() - suffix.length());
                break;
            }
        }

        try {
            Module module = Module.valueOf(key);
            return hasPermission(module, action);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    // =========================================================
    // MAP COMPLÈTE — copie profonde
    // =========================================================

    public Map<Module, EnumSet<Action>> getPermissions() {
        Map<Module, EnumSet<Action>> copie = new HashMap<>();
        for (Map.Entry<Module, EnumSet<Action>> e : permissions.entrySet()) {
            copie.put(e.getKey(), EnumSet.copyOf(e.getValue()));
        }
        return Collections.unmodifiableMap(copie);
    }

    public void setPermissions(Map<Module, EnumSet<Action>> nouvelles) {
        this.permissions = new HashMap<>();
        for (Module module : Module.values()) {
            this.permissions.put(module, EnumSet.noneOf(Action.class));
        }
        if (nouvelles == null) return;
        for (Map.Entry<Module, EnumSet<Action>> entry : nouvelles.entrySet()) {
            if (entry.getKey() == null) continue;
            EnumSet<Action> actions = entry.getValue();
            this.permissions.put(entry.getKey(),
                    actions != null ? EnumSet.copyOf(actions) : EnumSet.noneOf(Action.class));
        }
    }

    public void revokeAll() {
        for (Module module : Module.values()) {
            permissions.put(module, EnumSet.noneOf(Action.class));
        }
    }

    public void grantAll() {
        for (Module module : Module.values()) {
            permissions.put(module, EnumSet.allOf(Action.class));
        }
    }

    // =========================================================
    // SOURCE
    // =========================================================

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    // =========================================================
    // EQUALS / HASHCODE / TOSTRING
    // =========================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AccesAdmin other)) return false;
        return Objects.equals(source, other.source)
                && Objects.equals(permissions, other.permissions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, permissions);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("AccesAdmin{source='").append(source).append("', ");
        int total = 0;
        for (Map.Entry<Module, EnumSet<Action>> e : permissions.entrySet()) {
            if (!e.getValue().isEmpty()) {
                sb.append(e.getKey()).append("=").append(e.getValue()).append(" ");
                total += e.getValue().size();
            }
        }
        sb.append("(total=").append(total).append(" actions)}");
        return sb.toString();
    }
}