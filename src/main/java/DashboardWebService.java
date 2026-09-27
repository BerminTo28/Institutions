import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class DashboardWebService implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(DashboardWebService.class.getName());

 @Override
public void handle(HttpExchange exchange) throws IOException {
    final String reqId = java.util.UUID.randomUUID().toString().substring(0, 8);

    try {
        // 0) No-cache headers
        exchange.getResponseHeaders().set("Cache-Control",
                "no-store, no-cache, must-revalidate, max-age=0");
        exchange.getResponseHeaders().set("Pragma", "no-cache");
        exchange.getResponseHeaders().set("Expires", "0");

        // 1) Session
        String institutionId = getInstitutionId(exchange);
        String utilisateurId = getUtilisateurId(exchange);

        LOGGER.info(() -> "[" + reqId + "] 🎯 Dashboard : institution=" + institutionId
                + ", utilisateur=" + utilisateurId);

        // 2) Service + renderer
        DashboardAdminService service = new DashboardAdminService();
        DashboardAdminRenderer renderer = new DashboardAdminRenderer();

        // 3) Nom institution
        String nomInstitution = null;
        try {
            nomInstitution = service.getNomInstitution(institutionId);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING,
                    "[" + reqId + "] ⚠️ Impossible de récupérer le nom d'institution", e);
        }
        if (nomInstitution == null || nomInstitution.isBlank()) {
            nomInstitution = "Institution";
        }

        // 4) Logo
        String logoPath = null;
        try {
            logoPath = service.findLogoFile(institutionId);
            if (logoPath == null) {
                logoPath = service.getLogoPathFromDB(institutionId);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING,
                    "[" + reqId + "] ⚠️ Impossible de récupérer le logo", e);
        }
        if (logoPath == null || logoPath.isBlank()) {
            logoPath = DashboardConstants.DEFAULT_LOGO;
        }

        // 5) Modules
        List<Module> modules = List.of();
        try {
            modules = service.obtenirModulesAccessibles(institutionId, utilisateurId);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE,
                    "[" + reqId + "] ❌ Erreur chargement modules", e);
        }
        if (modules == null) modules = List.of();

        // ✅ LOG de diagnostic (avant le rendu, pas de capture de html)
        final int moduleCount = modules.size();
        LOGGER.info(() -> "[" + reqId + "] 📊 Nombre de modules pour "
                + utilisateurId + " : " + moduleCount);

        if (moduleCount == 0) {
            LOGGER.warning(() -> "[" + reqId + "] ⚠️ Aucun module accessible ! "
                    + "Vérifiez les permissions de l'utilisateur.");
        }

        // 6) Rendu
        String html;
        try {
            html = renderer.genererDashboard(
                    modules, nomInstitution, logoPath, utilisateurId, institutionId);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE,
                    "[" + reqId + "] ❌ Erreur rendu dashboard", e);
            html = genererPageErreur("Erreur lors du rendu de la page : "
                    + e.getClass().getSimpleName());
        }

        // ✅ Variables FINALES pour la lambda
        final int sizeKo = html.length() / 1024;

        // 7) Envoi
        safeHtml(exchange, 200, html);

        LOGGER.info(() -> "[" + reqId + "] ✅ Dashboard envoyé ("
                + moduleCount + " modules, " + sizeKo + " Ko)");

    } catch (Throwable t) {
        LOGGER.log(Level.SEVERE,
                "[" + reqId + "] ❌ Exception non catchée dans DashboardWebService", t);
        try {
            String err = genererPageErreur("Erreur interne du serveur : "
                    + t.getClass().getSimpleName());
            safeHtml(exchange, 500, err);
        } catch (Throwable ignored) {
            // Si même le fallback échoue, on ne peut plus rien faire
        }
    } finally {
        try { exchange.close(); } catch (Exception ignored) {}
    }
}

    // ============================================================
    // ENVOI HTML SÛR
    // ============================================================
    private void safeHtml(HttpExchange exchange, int status, String html) {
        if (html == null) html = "";
        try {
            if (exchange.getResponseCode() != -1) return;   // déjà envoyé
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
                os.flush();
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "⚠️ safeHtml : envoi impossible", e);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "❌ safeHtml : erreur inattendue", e);
        }
    }

    // ============================================================
    // PAGE D'ERREUR
    // ============================================================
    private String genererPageErreur(String message) {
        String safeMessage = echapperHtml(message);
        return "<!DOCTYPE html>"
                + "<html lang='fr'><head><meta charset='UTF-8'>"
                + "<title>Erreur</title>"
                + "<style>"
                + "body{font-family:sans-serif;padding:40px;background:#07090e;color:#f8fafc;}"
                + "h1{color:#f59e0b;}"
                + "a{color:#818cf8;text-decoration:none;}"
                + "a:hover{text-decoration:underline;}"
                + "</style>"
                + "</head><body>"
                + "<h1>⚠️ Erreur</h1>"
                + "<p>" + safeMessage + "</p>"
                + "<p><a href='/dashboard'>← Revenir au tableau de bord</a></p>"
                + "</body></html>";
    }

    // ============================================================
    // SESSION — extraction robuste
    // ============================================================
    private String getInstitutionId(HttpExchange exchange) {
        try {
            HttpSession session = resolveSession(exchange);
            if (session == null) return null;

            String instId = readAttributeAsString(session, "institutionId");
            if (instId == null) instId = readAttributeAsString(session, "institution_id");
            if (instId == null) instId = readAttributeAsString(session, "INSTITUTION_ID");
            return instId;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erreur getInstitutionId", e);
            return null;
        }
    }

    private String getUtilisateurId(HttpExchange exchange) {
        try {
            HttpSession session = resolveSession(exchange);
            if (session == null) return null;

            String userId = readAttributeAsString(session, "userId");
            if (userId == null) userId = readAttributeAsString(session, "user_id");
            if (userId == null) userId = readAttributeAsString(session, "utilisateurId");
            if (userId == null) userId = readAttributeAsString(session, "username");
            if (userId == null) userId = readAttributeAsString(session, "email");
            return userId;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erreur getUtilisateurId", e);
            return null;
        }
    }

    private HttpSession resolveSession(HttpExchange exchange) {
        String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookieHeader == null) return null;
        String sessionId = extractSessionId(cookieHeader);
        if (sessionId == null) return null;
        return SessionManager.getSession(sessionId);
    }

    private String extractSessionId(String cookieHeader) {
        for (String cookie : cookieHeader.split(";")) {
            String trimmed = cookie.trim();
            int eq = trimmed.indexOf('=');
            if (eq <= 0) continue;
            String name  = trimmed.substring(0, eq).trim();
            String value = trimmed.substring(eq + 1).trim();
            if ("SESSION_ID".equals(name) && !value.isEmpty()) {
                return value;
            }
        }
        return null;
    }

    /**
     * ✅ Lit un attribut de session et le convertit en String quel que soit
     *    son type réel (String, Long, Integer, UUID…).
     */
    private String readAttributeAsString(HttpSession session, String name) {
        Object value = session.getAttribute(name);
        if (value == null) return null;
        String s = value.toString();
        return s.isBlank() ? null : s;
    }

    // ============================================================
    // UTILITAIRES
    // ============================================================
    private static String echapperHtml(String input) {
        if (input == null) return "";
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}