import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Cache des statistiques (nombre d'étudiants par classe).
 * 
 * ✅ Version avec cache en mémoire : les stats sont mises en cache par institution
 *    et ne sont rechargées que si on les invalide explicitement.
 */
public class StatsCache {

    private static final Logger LOGGER = Logger.getLogger(StatsCache.class.getName());

    /** Cache : institutionId -> (codeClasse -> nbEtudiants) */
    private static final Map<String, Map<String, Integer>> CACHE = new ConcurrentHashMap<>();

    private StatsCache() {}

    /**
     * Récupère les stats depuis le cache, ou les recharge si absent.
     */
    public static Map<String, Integer> getStatsEtudiants(String institutionId) {
        if (institutionId == null || institutionId.isBlank()) {
            return Map.of();
        }
        return CACHE.computeIfAbsent(institutionId, StatsCache::chargerDepuisDb);
    }

    /**
     * ✅ Invalide le cache pour une institution.
     * À appeler après chaque CRUD sur les classes ou les étudiants.
     */
    public static void invalidate(String institutionId) {
        if (institutionId == null || institutionId.isBlank()) return;
        CACHE.remove(institutionId);
        LOGGER.fine(() -> "🗑️ StatsCache invalidé pour " + institutionId);
    }

    /**
     * ✅ Vide complètement le cache.
     */
    public static void invalidateAll() {
        CACHE.clear();
        LOGGER.fine("🗑️ StatsCache complètement vidé");
    }

    /**
     * Charge les statistiques depuis la base de données.
     */
    private static Map<String, Integer> chargerDepuisDb(String institutionId) {
        Map<String, Integer> stats = new HashMap<>();

        String sql = """
            SELECT
                c.code_classe,
                COUNT(e.numero_identifiant) AS nb_etudiants
            FROM classes c
            LEFT JOIN etudiants e
                ON e.institution_id = c.institution_id
                AND e.classe = c.nom_classe
                AND e.annee_academique = c.annee_academique
                AND e.periode = c.periode
            WHERE c.institution_id = ?
            GROUP BY c.code_classe
        """;

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    stats.put(rs.getString("code_classe"), rs.getInt("nb_etudiants"));
                }
            }
            LOGGER.fine(() -> "📊 Stats rechargées pour " + institutionId 
                    + " (" + stats.size() + " classes)");
        } catch (SQLException e) {
            LOGGER.severe(() -> "❌ Erreur StatsCache.chargerDepuisDb: " + e.getMessage());
        }
        return stats;
    }
}