import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class HandlerEtudiantActivites implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(HandlerEtudiantActivites.class.getName());

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
        String classeEtudiant = getSessionAttribute(exchange, "classe");

        LOGGER.log(Level.INFO, "👤 Étudiant: {0}, email: {1}, classe: {2}", 
                   new Object[]{userId, email, classeEtudiant});

        if (userId == null || institutionId == null) {
            exchange.getResponseHeaders().set("Location", "/connexion");
            exchange.sendResponseHeaders(302, -1);
            return;
        }

        // 🔥 VÉRIFICATION DES DROITS D'ACCÈS
        AccesEtudiantData accesData = new AccesEtudiantData();
        AccesEtudiant acces = null;
        try {
            acces = accesData.getByEtudiant(userId, institutionId);
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "❌ Erreur lors de la vérification des droits d'accès", e);
        }

        // Si aucun droit trouvé ou si l'étudiant n'a pas le droit d'accéder aux activités
        if (acces == null || !acces.isActivites()) {
            LOGGER.log(Level.WARNING, "⛔ Accès refusé pour l'étudiant {0} - Pas de droit 'activites'", userId);
            String html = """
                <!DOCTYPE html>
                <html lang='fr'>
                <head>
                    <meta charset='UTF-8'>
                    <meta name='viewport' content='width=device-width, initial-scale=1.0'>
                    <title>Accès refusé</title>
                    <link href='https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700;800&display=swap' rel='stylesheet'>
                    <link rel='stylesheet' href='https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css'>
                    <style>
                        * { margin: 0; padding: 0; box-sizing: border-box; }
                        body { font-family: 'Inter', sans-serif; background: #FAFBFB; display: flex; justify-content: center; align-items: center; min-height: 100vh; }
                        .card { background: white; padding: 40px; border-radius: 16px; border: 1px solid #DCE1E6; text-align: center; max-width: 500px; box-shadow: 0 4px 16px rgba(0,0,0,0.08); }
                        .card i { font-size: 56px; color: #EF4444; margin-bottom: 16px; }
                        .card h1 { color: #1E293B; font-size: 24px; margin-bottom: 8px; }
                        .card p { color: #64748B; font-size: 16px; margin-bottom: 20px; }
                        .btn { display: inline-block; padding: 10px 24px; background: #2563EB; color: white; border-radius: 8px; text-decoration: none; font-weight: 600; }
                        .btn:hover { background: #1D4ED8; }
                    </style>
                </head>
                <body>
                    <div class='card'>
                        <i class='fa-solid fa-lock'></i>
                        <h1>Accès restreint</h1>
                        <p>Vous n'avez pas les droits nécessaires pour accéder aux activités.</p>
                        <a href='/' class='btn'><i class='fa-solid fa-arrow-left'></i> Retour à l'accueil</a>
                    </div>
                </body>
                </html>
            """;
            sendResponse(exchange, 403, html);
            return;
        }

        // Récupération des paramètres de filtrage (année, période, classe)
        Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
        String anneeSel = params.getOrDefault("annee", "").trim();
        String periodeSel = params.getOrDefault("periode", "").trim();
        String classeSel = params.getOrDefault("classe", "").trim();

        // Utilisation du gestionnaire
        EvenementManager manager = new EvenementManager(institutionId);

        try {
            // Récupérer TOUS les événements de l'institution
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
            String html = UIEtudiantActivites.render(
                activitesFiltrees,
                anneesDisponibles,
                periodesDisponibles,
                classesDisponibles,
                anneeSel,
                periodeSel,
                classeSel,
                userId,
                email,
                classeEtudiant
            );

            sendResponse(exchange, 200, html);

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "❌ Erreur dans HandlerEtudiantActivites", e);
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