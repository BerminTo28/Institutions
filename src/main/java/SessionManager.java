import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SessionManager {
    private static final Logger LOGGER = Logger.getLogger(SessionManager.class.getName());
    private static final Map<String, HttpSession> sessions = new ConcurrentHashMap<>();
    private static final long SESSION_TIMEOUT_MS = 30 * 60 * 1000; // 30 minutes
    private static final long CLEANUP_INTERVAL_MS = 5 * 60 * 1000; // Nettoyage toutes les 5 minutes
     private static final ScheduledExecutorService cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
    Thread t = new Thread(r, "SessionCleanup-Thread");
    t.setDaemon(true);
    return t;
});
    static {
    cleanupScheduler.scheduleAtFixedRate(() -> {
        try {
            cleanupExpiredSessions();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erreur lors du nettoyage des sessions", e);
        }
    }, CLEANUP_INTERVAL_MS, CLEANUP_INTERVAL_MS, TimeUnit.MILLISECONDS);
    
    LOGGER.info("🧹 Planificateur de nettoyage des sessions démarré");
}

    /**
     * Crée une session avec un objet HttpSession existant
     */
    public static String createSession(HttpSession session) {
        if (session == null) {
            LOGGER.warning("❌ Tentative de création de session avec un objet null");
            return null;
        }
        
        String sessionId = UUID.randomUUID().toString();
        session.setCreationTime(System.currentTimeMillis());
        session.setLastAccessTime(System.currentTimeMillis());
        sessions.put(sessionId, session);
        
        LOGGER.info(() -> "✅ Session créée: " + sessionId + " avec attributs: " + session.getAttributes());
        return sessionId;
    }

    /**
     * Crée une session vide
     */
    public static String createSession() {
        String sessionId = UUID.randomUUID().toString();
        HttpSession session = new HttpSession(sessionId);
        session.setCreationTime(System.currentTimeMillis());
        session.setLastAccessTime(System.currentTimeMillis());
        sessions.put(sessionId, session);
        
        LOGGER.info(() -> "✅ Session créée: " + sessionId);
        return sessionId;
    }

    /**
     * Récupère une session par son ID
     */
    public static HttpSession getSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            LOGGER.warning("❌ Tentative de récupération d'une session avec un ID null ou vide");
            return null;
        }

        HttpSession session = sessions.get(sessionId);
        if (session == null) {
            LOGGER.fine(() -> "❌ Session non trouvée: " + sessionId);
            return null;
        }

        // Vérifier l'expiration
        long now = System.currentTimeMillis();
        long lastAccess = session.getLastAccessTime();
        if (now - lastAccess > SESSION_TIMEOUT_MS) {
            LOGGER.info(() -> "⏰ Session expirée (inactive depuis " + (now - lastAccess) / 1000 + "s): " + sessionId);
            sessions.remove(sessionId);
            return null;
        }

        // Mettre à jour le temps du dernier accès
        session.setLastAccessTime(now);
        LOGGER.fine(() -> "✅ Session trouvée: " + sessionId);
        return session;
    }

    /**
     * Invalide une session
     */
    public static void invalidateSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            LOGGER.warning("❌ Tentative d'invalidation d'une session avec un ID null ou vide");
            return;
        }

        HttpSession removed = sessions.remove(sessionId);
        if (removed != null) {
            LOGGER.info(() -> "🗑️ Session invalidée: " + sessionId);
        } else {
            LOGGER.fine(() -> "⚠️ Session déjà supprimée ou inexistante: " + sessionId);
        }
    }

    /**
     * Vérifie si une session est valide
     */
    public static boolean isValid(String sessionId) {
        return getSession(sessionId) != null;
    }

    /**
     * Nettoie les sessions expirées
     */
    private static void cleanupExpiredSessions() {
        long now = System.currentTimeMillis();
        int count = 0;
        
        for (Map.Entry<String, HttpSession> entry : sessions.entrySet()) {
            HttpSession session = entry.getValue();
            if (now - session.getLastAccessTime() > SESSION_TIMEOUT_MS) {
                sessions.remove(entry.getKey());
                count++;
                LOGGER.fine(() -> "🧹 Session expirée supprimée: " + entry.getKey());
            }
        }
        
        if (count > 0) {
            LOGGER.log(Level.INFO, "\ud83e\uddf9 {0} sessions expir\u00e9es nettoy\u00e9es", count);
        }
    }

    /**
     * Retourne le nombre de sessions actives
     */
    public static int getActiveSessionCount() {
        return sessions.size();
    }

    /**
     * Retourne toutes les sessions actives (pour debug)
     */
    public static Map<String, HttpSession> getAllSessions() {
        return new ConcurrentHashMap<>(sessions);
    }
}