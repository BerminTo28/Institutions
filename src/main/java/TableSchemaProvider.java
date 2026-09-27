import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;


public final class TableSchemaProvider {

    private static final Logger LOGGER = Logger.getLogger(TableSchemaProvider.class.getName());

    // Cache thread-safe : nom de table (normalisé en majuscules) -> définition
    private static final Map<String, TableDefinition> DEFINITION_CACHE = new ConcurrentHashMap<>();

    // Cache thread-safe : nom de table -> SQL de création
    private static final Map<String, String> SQL_CACHE = new ConcurrentHashMap<>();

    private TableSchemaProvider() {
        // Classe utilitaire : pas d'instanciation
        throw new AssertionError("No instances of TableSchemaProvider");
    }

    // ============================================================
    // ACCÈS AUX DÉFINITIONS
    // ============================================================

    /**
     * Retourne le SQL de création de la table demandée.
     *
     * @param tableName nom de la table (sensible à la casse du catalogue interne)
     * @return le SQL, jamais null
     * @throws IllegalArgumentException si la table est inconnue ou si le nom est vide
     */
    public static String getCreateTableSQL(String tableName) {
        validateTableName(tableName);
        return SQL_CACHE.computeIfAbsent(tableName, key -> {
            String sql = TableSchemaSQL.getCreateTableSQL(key);
            if (sql == null) {
                throw new IllegalArgumentException("Table inconnue : " + key);
            }
            return sql;
        });
    }

    /**
     * Retourne la définition structurée de la table demandée.
     *
     * @param tableName nom de la table
     * @return la définition immuable, jamais null
     * @throws IllegalArgumentException si la table est inconnue ou si le nom est vide
     */
    public static TableDefinition getTableDefinition(String tableName) {
        validateTableName(tableName);
        String cacheKey = tableName.toUpperCase();
        return DEFINITION_CACHE.computeIfAbsent(cacheKey, key -> {
            TableDefinition def = TableSchemaColumns.getTableDefinition(tableName);
            if (def == null) {
                throw new IllegalArgumentException("Définition inconnue pour la table : " + tableName);
            }
            return def;
        });
    }

    /**
     * Indique si une table est connue du fournisseur.
     */
    public static boolean tableExists(String tableName) {
        if (tableName == null || tableName.isBlank()) return false;
        try {
            getTableDefinition(tableName);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Vide les caches (utile en test ou si le catalogue change dynamiquement).
     */
    public static void clearCache() {
        DEFINITION_CACHE.clear();
        SQL_CACHE.clear();
        LOGGER.info("🧹 Cache des définitions de schéma vidé.");
    }

    // ============================================================
    // COMPARAISON DE DÉFINITIONS
    // ============================================================

    /**
     * Compare deux définitions de table et retourne la liste des différences.
     * Utile pour détecter une divergence entre le schéma local et le schéma distant.
     *
     * @return liste de messages décrivant les différences (vide si identiques)
     */
    public static List<String> compare(TableDefinition expected, TableDefinition actual) {
        List<String> differences = new ArrayList<>();

        if (expected == null && actual == null) return differences;
        if (expected == null) {
            differences.add("Définition attendue absente.");
            return differences;
        }
        if (actual == null) {
            differences.add("Définition réelle absente.");
            return differences;
        }

        Map<String, ColumnDefinition> expectedByName = expected.toMap();
        Map<String, ColumnDefinition> actualByName = actual.toMap();

        // Colonnes manquantes dans actual
        for (String name : expectedByName.keySet()) {
            if (!actualByName.containsKey(name)) {
                differences.add("Colonne manquante : " + name);
            }
        }

        // Colonnes en trop dans actual
        for (String name : actualByName.keySet()) {
            if (!expectedByName.containsKey(name)) {
                differences.add("Colonne en trop : " + name);
            }
        }

        // Colonnes présentes des deux côtés : vérifier type et nullabilité
        for (Map.Entry<String, ColumnDefinition> entry : expectedByName.entrySet()) {
            String name = entry.getKey();
            ColumnDefinition exp = entry.getValue();
            ColumnDefinition act = actualByName.get(name);
            if (act == null) continue; // déjà signalé

            if (!Objects.equals(normalizeType(exp.type), normalizeType(act.type))) {
                differences.add("Type différent pour " + name
                        + " : attendu=" + exp.type + ", réel=" + act.type);
            }
            if (!Objects.equals(exp.nullable, act.nullable)) {
                differences.add("Nullabilité différente pour " + name
                        + " : attendu=" + exp.nullable + ", réel=" + act.nullable);
            }
            if (!Objects.equals(exp.defaultValue, act.defaultValue)) {
                differences.add("Valeur par défaut différente pour " + name
                        + " : attendu=" + exp.defaultValue + ", réel=" + act.defaultValue);
            }
        }

        return differences;
    }

    private static String normalizeType(String type) {
        if (type == null) return null;
        return type.trim().toUpperCase().replaceAll("\\s+", " ");
    }

    // ============================================================
    // VALIDATION
    // ============================================================

    private static void validateTableName(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("Le nom de la table ne peut pas être vide.");
        }
    }

    // ============================================================
    // DÉFINITIONS DE COLONNES ET DE TABLE
    // ============================================================

    /**
     * Description immuable d'une colonne.
     */
    public static final class ColumnDefinition {
        public final String name;
        public final String type;
        public final String defaultValue;
        public final Boolean nullable;

        public ColumnDefinition(String name, String type, String defaultValue, Boolean nullable) {
            this.name = name;
            this.type = type;
            this.defaultValue = defaultValue;
            this.nullable = nullable;
        }

        public boolean hasDefault() {
            return defaultValue != null && !defaultValue.isBlank();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ColumnDefinition)) return false;
            ColumnDefinition that = (ColumnDefinition) o;
            return Objects.equals(name, that.name)
                    && Objects.equals(type, that.type)
                    && Objects.equals(defaultValue, that.defaultValue)
                    && Objects.equals(nullable, that.nullable);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, type, defaultValue, nullable);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(name).append(' ').append(type);
            if (Boolean.FALSE.equals(nullable)) sb.append(" NOT NULL");
            if (hasDefault()) sb.append(" DEFAULT ").append(defaultValue);
            return sb.toString();
        }
    }

    /**
     * Description immuable d'une table.
     */
    public static final class TableDefinition {
        public final List<ColumnDefinition> columns;
        private final Map<String, ColumnDefinition> byName;

        public TableDefinition(List<ColumnDefinition> columns) {
            if (columns == null) {
                this.columns = Collections.emptyList();
            } else {
                this.columns = Collections.unmodifiableList(new ArrayList<>(columns));
            }
            Map<String, ColumnDefinition> map = new HashMap<>();
            for (ColumnDefinition col : this.columns) {
                if (col != null && col.name != null) {
                    map.put(col.name.toLowerCase(), col);
                }
            }
            this.byName = Collections.unmodifiableMap(map);
        }

        /**
         * Recherche une colonne par son nom (insensible à la casse).
         *
         * @return la colonne, ou null si elle n'existe pas
         */
        public ColumnDefinition findColumn(String name) {
            if (name == null) return null;
            return byName.get(name.toLowerCase());
        }

        /**
         * Retourne une copie de la map {nom -> colonne} en minuscules.
         */
        public Map<String, ColumnDefinition> toMap() {
            return byName;
        }

        /**
         * Nombre de colonnes.
         */
        public int size() {
            return columns.size();
        }

        /**
         * Vrai si la table n'a aucune colonne.
         */
        public boolean isEmpty() {
            return columns.isEmpty();
        }

        /**
         * Retourne les noms des colonnes dans l'ordre du schéma.
         */
        public List<String> columnNames() {
            List<String> names = new ArrayList<>(columns.size());
            for (ColumnDefinition col : columns) {
                names.add(col.name);
            }
            return names;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("TableDefinition{\n");
            for (ColumnDefinition col : columns) {
                sb.append("  ").append(col).append('\n');
            }
            sb.append('}');
            return sb.toString();
        }
    }

    // ============================================================
    // LOG (optionnel, pour debug)
    // ============================================================

    static {
        // Log au premier chargement pour tracer l'initialisation
        LOGGER.log(Level.FINE, "TableSchemaProvider initialisé.");
    }
}