import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class HandlerProfesseurActivites implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(HandlerProfesseurActivites.class.getName());

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        if (!"GET".equalsIgnoreCase(method)) {
            ResponseUtil.sendError(exchange, 405, "Méthode non autorisée");
            return;
        }

        // Récupération des identifiants depuis la session
        String userId = getSessionAttribute(exchange, "userId");
        String email = getSessionAttribute(exchange, "email");
        String institutionId = getInstitutionId(exchange);

        LOGGER.log(Level.INFO, "👤 Professeur: {0}, email: {1}", new Object[]{userId, email});

        if (userId == null || institutionId == null) {
            exchange.getResponseHeaders().set("Location", "/connexion");
            exchange.sendResponseHeaders(302, -1);
            return;
        }

        // Récupération des paramètres de filtrage
        Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
        String anneeSel = params.getOrDefault("annee", "").trim();
        String periodeSel = params.getOrDefault("periode", "").trim();
        String classeSel = params.getOrDefault("classe", "").trim();

        // Utilisation du gestionnaire
        EvenementManager manager = new EvenementManager(institutionId);

        try {
            // 🔥 CHANGEMENT IMPORTANT: Récupérer TOUS les événements de l'institution
            // Plus besoin de filtrer par email du professeur
            List<Evenement> tousLesEvenements = manager.listerTous();

            LOGGER.log(Level.INFO, "📅 {0} événements trouvés pour l'institution", 
                       tousLesEvenements.size());

            // Application des filtres optionnels (année, période, classe)
            List<Evenement> activitesFiltrees = tousLesEvenements.stream()
                .filter(ev -> {
                    // Filtre par année académique
                    boolean matchAnnee = anneeSel.isBlank() || 
                                         (ev.getAnneeAcademique() != null && 
                                          anneeSel.equals(ev.getAnneeAcademique()));
                    
                    // Filtre par période
                    boolean matchPeriode = periodeSel.isBlank() || 
                                           (ev.getPeriode() != null && 
                                            periodeSel.equals(ev.getPeriode()));
                    
                    // Filtre par classe (optionnel)
                    boolean matchClasse = classeSel.isBlank() || 
                                          (ev.getClasse() != null && 
                                           classeSel.equals(ev.getClasse()));
                    
                    return matchAnnee && matchPeriode && matchClasse;
                })
                .toList();

            LOGGER.log(Level.INFO, "📅 {0} événements après filtrage", activitesFiltrees.size());

            // Récupération des listes pour les filtres
            List<String> anneesDisponibles = manager.getAnneesDisponibles();
            List<String> periodesDisponibles = manager.getPeriodesDisponibles();
            List<String> classesDisponibles = manager.getClassesDisponibles();

            // Génération de la vue
            String html = UIProfesseurActivites.render(
                activitesFiltrees,
                anneesDisponibles,
                periodesDisponibles,
                classesDisponibles,
                anneeSel,
                periodeSel,
                classeSel,
                userId,
                email
            );

            sendResponse(exchange, 200, html);

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "❌ Erreur dans HandlerProfesseurActivites", e);
            ResponseUtil.sendError(exchange, 500, "Erreur interne du serveur");
        }
    }

    // ==================== UTILITAIRES DE SESSION ====================

    private String getSessionAttribute(HttpExchange exchange, String attributeName) {
        try {
            String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
            if (cookieHeader == null) return null;
            String sessionId = extractSessionId(cookieHeader);
            if (sessionId == null) return null;
            HttpSession session = SessionManager.getSession(sessionId);
            return (session != null) ? (String) session.getAttribute(attributeName) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String getInstitutionId(HttpExchange exchange) {
        String id = getSessionAttribute(exchange, "institutionId");
        return (id != null) ? id : getSessionAttribute(exchange, "institution_id");
    }

    private String extractSessionId(String cookieHeader) {
        for (String cookie : cookieHeader.split(";")) {
            String[] parts = cookie.trim().split("=");
            if (parts.length == 2 && "SESSION_ID".equals(parts[0])) {
                return parts[1];
            }
        }
        return null;
    }

    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new java.util.HashMap<>();
        if (query == null || query.isBlank()) return params;
        try {
            for (String pair : query.split("&")) {
                int idx = pair.indexOf("=");
                if (idx > 0) {
                    String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                    String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                    params.put(key, value);
                }
            }
        } catch (Exception e) {
            LOGGER.warning(() -> "Erreur parsing query: " + e.getMessage());
        }
        return params;
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
            os.flush();
        }
    }
}