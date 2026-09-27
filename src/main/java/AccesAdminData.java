import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Accès aux permissions administratives.
 *
 * <p><b>Stratégie de chargement :</b></p>
 * <ol>
 *   <li><b>BD</b> — Table {@code acces_admin} : SOURCE DE VÉRITÉ.</li>
 *   <li><b>Sync</b> — Si la BD répond, cache local écrit pour hors-ligne.</li>
 *   <li><b>Local</b> — Fallback si BD indisponible.</li>
 *   <li><b>DenyAll</b> — Si rien trouvé.</li>
 * </ol>
 *
 * <p><b>Logs</b> : uniquement {@code SEVERE} pour les erreurs critiques.
 * Tous les diagnostics sont silencieux.</p>
 */
public class AccesAdminData {

    private static final Logger LOGGER = Logger.getLogger(AccesAdminData.class.getName());

    // =========================================================
    // CACHE LOCAL
    // =========================================================
    private static final Path LOCAL_CACHE_DIR = Paths.get("data", "acces_admin_cache");
    private static final String LOCAL_FILE_SUFFIX = ".properties";

    // =========================================================
    // CONNEXION
    // =========================================================
    private final Connection conn;

    public AccesAdminData(Connection conn) {
        this.conn = conn;
    }

    // =========================================================
    // MAPPING COLONNE SQL → MODULE
    // =========================================================
    private static final Map<String, AccesAdmin.Module> COLUMN_TO_MODULE = new HashMap<>();

    static {
        register("classe",              AccesAdmin.Module.CLASSE);
        register("classes",             AccesAdmin.Module.CLASSE);
        register("matiere",             AccesAdmin.Module.MATIERE);
        register("matieres",            AccesAdmin.Module.MATIERE);
        register("utilisateur",         AccesAdmin.Module.UTILISATEUR);
        register("utilisateurs",        AccesAdmin.Module.UTILISATEUR);
        register("note",                AccesAdmin.Module.NOTE);
        register("notes",               AccesAdmin.Module.NOTE);
        register("bulletin",            AccesAdmin.Module.BULLETIN);
        register("bulletins",           AccesAdmin.Module.BULLETIN);
        register("economie",            AccesAdmin.Module.ECONOMIE);
        register("economies",           AccesAdmin.Module.ECONOMIE);
        register("attestation",         AccesAdmin.Module.ATTESTATION);
        register("attestations",        AccesAdmin.Module.ATTESTATION);
        register("emploi_du_temps",     AccesAdmin.Module.EMPLOI_DU_TEMPS);
        register("emplois_du_temps",    AccesAdmin.Module.EMPLOI_DU_TEMPS);
        register("emploidutemps",       AccesAdmin.Module.EMPLOI_DU_TEMPS);
        register("evenement",           AccesAdmin.Module.EVENEMENTS);
        register("evenements",          AccesAdmin.Module.EVENEMENTS);
        register("etudiant",            AccesAdmin.Module.ETUDIANTS);
        register("etudiants",           AccesAdmin.Module.ETUDIANTS);
        register("palmares",            AccesAdmin.Module.PALMARES);
        register("absence",             AccesAdmin.Module.ABSENCE);
        register("absences",            AccesAdmin.Module.ABSENCE);
        register("examen",              AccesAdmin.Module.EXAMEN);
        register("examens",             AccesAdmin.Module.EXAMEN);
        register("professeur",          AccesAdmin.Module.PROFESSEUR);
        register("professeurs",         AccesAdmin.Module.PROFESSEUR);
        register("parametre",           AccesAdmin.Module.PARAMETRES);
        register("parametres",          AccesAdmin.Module.PARAMETRES);
        register("communication",       AccesAdmin.Module.COMMUNICATION);
        register("communications",      AccesAdmin.Module.COMMUNICATION);
        register("statistique",         AccesAdmin.Module.STATISTIQUES);
        register("statistiques",        AccesAdmin.Module.STATISTIQUES);
        register("programme",           AccesAdmin.Module.PROGRAMMES);
        register("programmes",          AccesAdmin.Module.PROGRAMMES);
        register("document",            AccesAdmin.Module.DOCUMENTS);
        register("documents",           AccesAdmin.Module.DOCUMENTS);
        register("message",             AccesAdmin.Module.MESSAGES);
        register("messages",            AccesAdmin.Module.MESSAGES);
        register("assistance",          AccesAdmin.Module.ASSISTANCE);
        register("module",              AccesAdmin.Module.MODULES);
        register("modules",             AccesAdmin.Module.MODULES);
        register("promotion",           AccesAdmin.Module.PROMOTIONS);
        register("promotions",          AccesAdmin.Module.PROMOTIONS);
        register("option",              AccesAdmin.Module.OPTIONS);
        register("options",             AccesAdmin.Module.OPTIONS);
        register("periode",             AccesAdmin.Module.PERIODES);
        register("periodes",            AccesAdmin.Module.PERIODES);
        register("annee_academique",    AccesAdmin.Module.ANNEES_ACADEMIQUES);
        register("annees_academiques",  AccesAdmin.Module.ANNEES_ACADEMIQUES);
        register("anneeacademique",     AccesAdmin.Module.ANNEES_ACADEMIQUES);
    }

    private static void register(String columnBase, AccesAdmin.Module module) {
        COLUMN_TO_MODULE.put(columnBase.toLowerCase(Locale.ROOT), module);
    }

    // =========================================================
    // MÉTHODE PUBLIQUE PRINCIPALE
    // =========================================================

    public AccesAdmin loadByUtilisateurId(String utilisateurId, String institutionId) {
        if (utilisateurId == null || utilisateurId.isBlank()) {
            return denyAll("empty");
        }

        final String user = utilisateurId.trim();
        final String inst = (institutionId != null && !institutionId.isBlank())
                ? institutionId.trim() : null;

        // ① BD en priorité
        AccesAdmin fromDb = tryLoadFromDatabase(user, inst);
        if (fromDb != null && !isVide(fromDb)) {
            // ② Synchroniser le cache local
            safeWriteLocalCache(user, inst, fromDb);
            return fromDb;
        }

        // ③ Fallback local
        AccesAdmin fromLocal = tryReadLocalCache(user, inst);
        if (fromLocal != null && !isVide(fromLocal)) {
            fromLocal.setSource("LOCAL_FALLBACK");
            return fromLocal;
        }

        // ④ Deny all
        return denyAll("none");
    }

    // =========================================================
    // CHARGEMENT BD
    // =========================================================

    private AccesAdmin tryLoadFromDatabase(String user, String inst) {
        try {
            return loadFromDatabase(user, inst);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, e,
                    () -> "BD inaccessible pour user=" + user);
            return null;
        }
    }

    private AccesAdmin loadFromDatabase(String user, String inst) throws SQLException {
        final String sql = (inst != null)
                ? "SELECT * FROM acces_admin WHERE utilisateur_id = ? AND institution_id = ?"
                : "SELECT * FROM acces_admin WHERE utilisateur_id = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user);
            if (inst != null) ps.setString(2, inst);
            ps.setQueryTimeout(15);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    if (inst != null) {
                        // Retenter sans filtre institution
                        return loadFromDatabase(user, null);
                    }
                    return null;
                }

                AccesAdmin acces = new AccesAdmin();
                acces.setSource("db");

                ResultSetMetaData meta = rs.getMetaData();
                Set<String> colonnes = new HashSet<>();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    colonnes.add(meta.getColumnLabel(i).toLowerCase(Locale.ROOT));
                }

                for (String colonne : colonnes) {
                    AccesAdmin.Module module = resolveModuleFromColumn(colonne);
                    if (module == null) continue;

                    String valeur;
                    try {
                        valeur = rs.getString(colonne);
                    } catch (SQLException e) {
                        continue;
                    }
                    if (valeur == null || valeur.isBlank()) continue;

                    parseAndApply(valeur, module, acces);
                }

                return acces;
            }
        }
    }

    // =========================================================
    // CACHE LOCAL
    // =========================================================

    private void safeWriteLocalCache(String user, String inst, AccesAdmin acces) {
        try {
            writeLocalCache(user, inst, acces);
        } catch (IOException e) {
            // Silencieux
        }
    }

    private void writeLocalCache(String user, String inst, AccesAdmin acces) throws IOException {
        Path fichier = localFileFor(user, inst);
        Files.createDirectories(fichier.getParent());

        Properties props = new Properties();
        props.setProperty("_source", "BD");
        props.setProperty("_utilisateur_id", user);
        props.setProperty("_institution_id", inst != null ? inst : "");
        props.setProperty("_synchronise_le", Instant.now().toString());

        for (Map.Entry<AccesAdmin.Module, EnumSet<AccesAdmin.Action>> e
                : acces.getPermissions().entrySet()) {
            if (e.getValue().isEmpty()) continue;
            StringBuilder sb = new StringBuilder();
            for (AccesAdmin.Action a : e.getValue()) {
                if (sb.length() > 0) sb.append(',');
                sb.append(a.name());
            }
            props.setProperty(e.getKey().name(), sb.toString());
        }

        try (var out = Files.newBufferedWriter(fichier, StandardCharsets.UTF_8)) {
            props.store(out, "Cache local AccesAdmin");
        }
    }

    private AccesAdmin tryReadLocalCache(String user, String inst) {
        try {
            return readLocalCache(user, inst);
        } catch (IOException e) {
            return null;
        }
    }

    private AccesAdmin readLocalCache(String user, String inst) throws IOException {
        Path fichier = localFileFor(user, inst);
        if (!Files.exists(fichier)) return null;

        Properties props = new Properties();
        try (var in = Files.newBufferedReader(fichier, StandardCharsets.UTF_8)) {
            props.load(in);
        }

        AccesAdmin acces = new AccesAdmin();
        acces.setSource("LOCAL");

        for (String cle : props.stringPropertyNames()) {
            if (cle.startsWith("_")) continue;
            try {
                AccesAdmin.Module module = AccesAdmin.Module.valueOf(cle);
                parseAndApply(props.getProperty(cle, ""), module, acces);
            } catch (IllegalArgumentException ignored) {
                // Clé inconnue
            }
        }
        return acces;
    }

    private Path localFileFor(String user, String inst) {
        String safeUser = user.replaceAll("[^a-zA-Z0-9._-]", "_");
        String safeInst = (inst != null)
                ? inst.replaceAll("[^a-zA-Z0-9._-]", "_")
                : "noinst";
        return LOCAL_CACHE_DIR.resolve(safeUser + "@" + safeInst + LOCAL_FILE_SUFFIX);
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private AccesAdmin.Module resolveModuleFromColumn(String colonne) {
        if (colonne == null) return null;
        String c = colonne.toLowerCase(Locale.ROOT).trim();

        switch (c) {
            case "id", "utilisateur_id", "user_id", "institution_id", "created_at", "updated_at", "last_modified", "source", "role", "nom", "prenom", "email", "mot_de_passe", "niveau" -> {
                return null;
            }
        }

        // Colonnes obsolètes (doublons)
        switch (c) {
            case "parametre_perms", "parametre_permissions" -> {
                return null;
            }
        }

        // Retirer préfixes
        for (String prefix : new String[]{"perm_", "permission_", "acces_"}) {
            if (c.startsWith(prefix)) {
                c = c.substring(prefix.length());
                break;
            }
        }

        // Retirer suffixes
        for (String suffix : new String[]{"_permissions", "_perms", "_access", "_perm"}) {
            if (c.endsWith(suffix)) {
                c = c.substring(0, c.length() - suffix.length());
                break;
            }
        }

        AccesAdmin.Module m = COLUMN_TO_MODULE.get(c);
        if (m != null) return m;

        if (c.endsWith("s")) {
            m = COLUMN_TO_MODULE.get(c.substring(0, c.length() - 1));
            if (m != null) return m;
        }

        try {
            return AccesAdmin.Module.valueOf(c.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void parseAndApply(String valeur, AccesAdmin.Module module, AccesAdmin acces) {
        if (valeur == null) return;
        String v = valeur.trim();
        if (v.isEmpty()) return;

        // Bitmask décimal
        if (v.matches("\\d{1,3}")) {
            try {
                int mask = Integer.parseInt(v);
                if (mask == 0) return;
                if ((mask & 1) != 0) acces.setPermission(module, AccesAdmin.Action.READ, true);
                if ((mask & 2) != 0) acces.setPermission(module, AccesAdmin.Action.CREATE, true);
                if ((mask & 4) != 0) acces.setPermission(module, AccesAdmin.Action.UPDATE, true);
                if ((mask & 8) != 0) acces.setPermission(module, AccesAdmin.Action.DELETE, true);
                return;
            } catch (NumberFormatException ignored) {
                // pas un décimal
            }
        }

        // Bitmask binaire
        if (v.matches("[01]{1,4}")) {
            AccesAdmin.Action[] order = {
                    AccesAdmin.Action.READ, AccesAdmin.Action.CREATE,
                    AccesAdmin.Action.UPDATE, AccesAdmin.Action.DELETE
            };
            for (int i = 0; i < v.length() && i < order.length; i++) {
                if (v.charAt(i) == '1') acces.setPermission(module, order[i], true);
            }
            return;
        }

        // Booléens
        if (v.toLowerCase(Locale.ROOT)
                .matches("(true|false)([,\\|\\s]+(true|false)){0,3}")) {
            String[] parts = v.split("[,\\|\\s]+");
            AccesAdmin.Action[] order = {
                    AccesAdmin.Action.READ, AccesAdmin.Action.CREATE,
                    AccesAdmin.Action.UPDATE, AccesAdmin.Action.DELETE
            };
            for (int i = 0; i < parts.length && i < order.length; i++) {
                if ("true".equalsIgnoreCase(parts[i].trim())) {
                    acces.setPermission(module, order[i], true);
                }
            }
            return;
        }

        // Tokens
        for (String tok : v.split("[,\\|\\s;]+")) {
            String t = tok.trim().toUpperCase(Locale.ROOT);
            if (t.isEmpty()) continue;
            switch (t) {
                case "R", "READ", "LIRE", "LECTURE", "VIEW", "CONSULTER" ->
                        acces.setPermission(module, AccesAdmin.Action.READ, true);
                case "C", "CREATE", "CREER", "AJOUT", "ADD", "WRITE", "ECRIRE" ->
                        acces.setPermission(module, AccesAdmin.Action.CREATE, true);
                case "U", "UPDATE", "MODIFIER", "EDIT", "EDITER" ->
                        acces.setPermission(module, AccesAdmin.Action.UPDATE, true);
                case "D", "DELETE", "SUPPRIMER", "REMOVE", "EFFACER" ->
                        acces.setPermission(module, AccesAdmin.Action.DELETE, true);
                default -> {
                    try {
                        acces.setPermission(module, AccesAdmin.Action.valueOf(t), true);
                    } catch (IllegalArgumentException ignored) {
                        // Token inconnu
                    }
                }
            }
        }
    }

    private boolean isVide(AccesAdmin acces) {
        if (acces == null) return true;
        for (EnumSet<AccesAdmin.Action> s : acces.getPermissions().values()) {
            if (!s.isEmpty()) return false;
        }
        return true;
    }

    private AccesAdmin denyAll(String source) {
        AccesAdmin vide = new AccesAdmin();
        vide.setSource(source);
        return vide;
    }

    // =========================================================
    // MÉTHODES PUBLIQUES (utilisées par HandlerAccesAdmin)
    // =========================================================

    /**
     * Sauvegarde ou met à jour les permissions d'un utilisateur.
     */
    public boolean saveOrUpdate(AccesAdmin acces, String utilisateurId, String institutionId)
            throws SQLException {
        if (acces == null || utilisateurId == null || institutionId == null) {
            return false;
        }

        StringBuilder setClause = new StringBuilder();
        LinkedHashMap<String, String> valeurs = new LinkedHashMap<>();

        for (Map.Entry<AccesAdmin.Module, EnumSet<AccesAdmin.Action>> e
                : acces.getPermissions().entrySet()) {
            String colonne = e.getKey().name().toLowerCase(Locale.ROOT) + "_perms";
            String valeur = actionsToString(e.getValue());
            setClause.append(colonne).append(" = ?, ");
            valeurs.put(colonne, valeur);
        }

        if (setClause.length() == 0) return false;
        setClause.setLength(setClause.length() - 2);

        String sql = "UPDATE acces_admin SET " + setClause
                + " WHERE utilisateur_id = ? AND institution_id = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            for (String v : valeurs.values()) {
                ps.setString(idx++, v);
            }
            ps.setString(idx++, utilisateurId);
            ps.setString(idx, institutionId);

            int rows = ps.executeUpdate();
            if (rows == 0) {
                return insertNew(acces, utilisateurId, institutionId);
            }
            return true;
        }
    }

    private boolean insertNew(AccesAdmin acces, String utilisateurId, String institutionId)
            throws SQLException {
        StringBuilder cols = new StringBuilder("utilisateur_id, institution_id");
        StringBuilder vals = new StringBuilder("?, ?");
        List<String> valeurs = new java.util.ArrayList<>();
        valeurs.add(utilisateurId);
        valeurs.add(institutionId);

        for (Map.Entry<AccesAdmin.Module, EnumSet<AccesAdmin.Action>> e
                : acces.getPermissions().entrySet()) {
            cols.append(", ").append(e.getKey().name().toLowerCase(Locale.ROOT)).append("_perms");
            vals.append(", ?");
            valeurs.add(actionsToString(e.getValue()));
        }

        String sql = "INSERT INTO acces_admin (" + cols + ") VALUES (" + vals + ")";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < valeurs.size(); i++) {
                ps.setString(i + 1, valeurs.get(i));
            }
            return ps.executeUpdate() > 0;
        }
    }

    private String actionsToString(EnumSet<AccesAdmin.Action> actions) {
        if (actions == null || actions.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (AccesAdmin.Action a : actions) {
            if (sb.length() > 0) sb.append(',');
            sb.append(a.name());
        }
        return sb.toString();
    }

    /**
     * Supprime les permissions d'un utilisateur.
     */
    public boolean delete(String utilisateurId, String institutionId) {
        if (utilisateurId == null || institutionId == null) return false;
        String sql = "DELETE FROM acces_admin WHERE utilisateur_id = ? AND institution_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, utilisateurId);
            ps.setString(2, institutionId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, e, () -> "Erreur delete pour " + utilisateurId);
            return false;
        }
    }

    /**
     * Invalide le cache local d'un utilisateur.
     */
    public static void invaliderCacheLocal(String user, String inst) {
        try {
            String safeUser = user.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
            String safeInst = (inst != null)
                    ? inst.trim().replaceAll("[^a-zA-Z0-9._-]", "_")
                    : "noinst";
            Path f = LOCAL_CACHE_DIR.resolve(safeUser + "@" + safeInst + LOCAL_FILE_SUFFIX);
            Files.deleteIfExists(f);
        } catch (IOException ignored) {
            // Silencieux
        }
    }
}