import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class UIAccesAdmin implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(UIAccesAdmin.class.getName());
    private final UtilisateurData utilisateurData;

    public UIAccesAdmin(Connection connection) {
        this.utilisateurData = new UtilisateurData(connection);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        try {
            String institutionId = getInstitutionIdFromSession(exchange);
            if (institutionId == null) {
                ResponseUtil.sendRedirect(exchange, "/admin/parametres?error=session");
                return;
            }

            if ("GET".equalsIgnoreCase(method)) {
                handleGet(exchange, institutionId);
            } else if ("POST".equalsIgnoreCase(method)) {
                handlePost(exchange, institutionId);
            } else {
                ResponseUtil.sendError(exchange, 405, "Méthode non autorisée");
            }
        } catch (IOException e) {
            LOGGER.severe(() -> "Erreur dans UIAccesAdmin : " + e.getMessage());
            ResponseUtil.sendError(exchange, 500, "Erreur interne");
        }
    }

    // =========================================================
    // GET — Liste des utilisateurs de l'institution
    // =========================================================
    private void handleGet(HttpExchange exchange, String institutionId) throws IOException {
        // ✅ Signature simplifiée (institutionId suffit)
        // ✅ Utilise readAllByInstitution (filtré)
        List<Utilisateur> utilisateurs = utilisateurData.readAllByInstitution(institutionId);

        LOGGER.fine(() -> "GET /admin/acces → " + utilisateurs.size()
                + " utilisateurs pour institution " + institutionId);

        String html = genererListe(utilisateurs, institutionId);
        ResponseUtil.sendHtml(exchange, 200, html);
    }

    // =========================================================
    // POST — Suppression d'un utilisateur
    // =========================================================
    private void handlePost(HttpExchange exchange, String institutionId) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8);
        Map<String, String> params = parseFormData(body);
        String action = params.get("action");

        if (!"delete".equals(action)) {
            ResponseUtil.sendRedirect(exchange, "/admin/acces");
            return;
        }

        String userId = params.get("userId");
        if (userId == null || userId.isBlank()) {
            ResponseUtil.sendRedirect(exchange, "/admin/acces?error=missing_user");
            return;
        }

        // ✅ Vérification : l'utilisateur appartient-il à cette institution ?
        boolean appartient = utilisateurData.existsInInstitution(userId, institutionId);
        if (!appartient) {
            LOGGER.warning(() -> "⚠️ Tentative de suppression d'un utilisateur "
                    + "d'une autre institution : " + userId
                    + " (institution=" + institutionId + ")");
            ResponseUtil.sendRedirect(exchange, "/admin/acces?error=forbidden");
            return;
        }

        // ✅ Suppression filtrée par institution
        boolean ok = utilisateurData.delete(userId, institutionId);
        if (ok) {
            LOGGER.info(() -> "✅ Utilisateur supprimé : " + userId);
            ResponseUtil.sendRedirect(exchange, "/admin/acces?msg=delete_success");
        } else {
            LOGGER.warning(() -> "⚠️ Échec suppression utilisateur : " + userId);
            ResponseUtil.sendRedirect(exchange, "/admin/acces?error=delete_failed");
        }
    }

    // =========================================================
    // Helpers session
    // =========================================================
    private String getInstitutionIdFromSession(HttpExchange exchange) {
        try {
            String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
            if (cookieHeader == null) return null;
            String sessionId = extractSessionId(cookieHeader);
            if (sessionId == null) return null;
            HttpSession session = SessionManager.getSession(sessionId);
            if (session == null) return null;
            return (String) session.getAttribute("institutionId");
        } catch (Exception e) {
            LOGGER.warning(() -> "Impossible de récupérer l'institutionId: " + e.getMessage());
            return null;
        }
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

    private Map<String, String> parseFormData(String body) {
        Map<String, String> params = new HashMap<>();
        if (body == null || body.isBlank()) return params;
        for (String pair : body.split("&")) {
            String[] kv = pair.split("=");
            if (kv.length == 2) {
                try {
                    params.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8.name()),
                               URLDecoder.decode(kv[1], StandardCharsets.UTF_8.name()));
                } catch (UnsupportedEncodingException e) { /* ignore */ }
            }
        }
        return params;
    }

    // =========================================================
    // Génération HTML
    // =========================================================
    private String genererListe(List<Utilisateur> utilisateurs, String institutionId) {
        // ✅ Logger au lieu de System.out.println (supprimé les doublons)
        LOGGER.fine(() -> "Génération liste pour institution " + institutionId
                + " (" + (utilisateurs != null ? utilisateurs.size() : 0) + " utilisateurs)");

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>");
        sb.append("<html lang=\"fr\">");
        sb.append("<head>");
        sb.append("<meta charset=\"UTF-8\">");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        sb.append("<title>Gestion des utilisateurs</title>");
        sb.append("<link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css\">");
        sb.append("<style>");
        sb.append("""
        * { margin:0; padding:0; box-sizing:border-box; font-family:'Segoe UI', sans-serif; }
        body { background:#f4f6f9; padding:20px; }
        .container { max-width:1200px; margin:0 auto; }
        .header { background:white; padding:20px 30px; border-radius:12px; display:flex; justify-content:space-between; align-items:center; margin-bottom:25px; }
        .header h1 { font-size:22px; color:#1e293b; }
        .header h1 i { color:#1e40af; margin-right:10px; }
        .header a { color:#1e40af; text-decoration:none; font-weight:600; }
        .header a:hover { text-decoration:underline; }
        .actions { margin-bottom:20px; }
        .actions .btn-add { background:#1e40af; color:white; border:none; padding:10px 20px; border-radius:8px; cursor:pointer; font-weight:600; text-decoration:none; display:inline-block; }
        .actions .btn-add:hover { background:#1e3a8a; }
        .table-wrapper { background:white; border-radius:12px; padding:20px; overflow-x:auto; }
        table { width:100%; border-collapse:collapse; font-size:14px; }
        th, td { padding:12px 10px; text-align:left; border-bottom:1px solid #e2e8f0; }
        th { background:#f8fafc; font-weight:600; color:#475569; }
        .actions-cell a { color:#1e40af; text-decoration:none; margin-right:15px; }
        .actions-cell a:hover { text-decoration:underline; }
        .actions-cell .delete { color:#dc2626; cursor:pointer; background:none; border:none; font-size:14px; }
        .actions-cell .delete:hover { text-decoration:underline; }
        .empty-row td { padding:30px; color:#94a3b8; font-style:italic; text-align:center; }
        .footer { margin-top:30px; text-align:center; color:#94a3b8; font-size:13px; }
        """);
        sb.append("</style>");
        sb.append("</head>");
        sb.append("<body>");
        sb.append("<div class=\"container\">");

        sb.append("<div class=\"header\">");
        sb.append("<div><h1><i class=\"fas fa-users-cog\"></i> Gestion des utilisateurs</h1></div>");
        sb.append("<a href=\"/admin/parametres\"><i class=\"fas fa-arrow-left\"></i> Retour aux paramètres</a>");
        sb.append("</div>");

        sb.append("<div class=\"actions\">");
        sb.append("<a href=\"/admin/acces/form?action=add\" class=\"btn-add\"><i class=\"fas fa-plus\"></i> Ajouter un utilisateur</a>");
        sb.append("</div>");

        sb.append("<div class=\"table-wrapper\">");
        if (utilisateurs != null && !utilisateurs.isEmpty()) {
            sb.append("<table>");
            sb.append("<thead><tr><th>Identifiant</th><th>Nom</th><th>Prénom</th><th>Email</th><th>Niveau</th><th>Actions</th></tr></thead>");
            sb.append("<tbody>");
            for (Utilisateur u : utilisateurs) {
                String niveauStr = switch (u.getNiveau()) {
                    case 1 -> "Administrateur";
                    case 2 -> "Professeur";
                    case 3 -> "Étudiant";
                    default -> "Inconnu";
                };
                sb.append("<tr>");
                sb.append("<td><strong>").append(echapperHtml(u.getIdentifiant())).append("</strong></td>");
                sb.append("<td>").append(echapperHtml(u.getNom())).append("</td>");
                sb.append("<td>").append(echapperHtml(u.getPrenom())).append("</td>");
                sb.append("<td>").append(echapperHtml(u.getEmail())).append("</td>");
                sb.append("<td>").append(niveauStr).append("</td>");
                sb.append("<td class=\"actions-cell\">");
                sb.append("<a href=\"/admin/acces/form?action=edit&userId=")
                  .append(echapperHtml(u.getIdentifiant()))
                  .append("\"><i class=\"fas fa-edit\"></i> Modifier</a>");
                sb.append("<form method=\"POST\" action=\"/admin/acces\" style=\"display:inline;\">");
                sb.append("<input type=\"hidden\" name=\"action\" value=\"delete\">");
                sb.append("<input type=\"hidden\" name=\"userId\" value=\"")
                  .append(echapperHtml(u.getIdentifiant())).append("\">");
                sb.append("<button type=\"submit\" class=\"delete\" ")
                  .append("onclick=\"return confirm('Supprimer cet utilisateur ?');\">");
                sb.append("<i class=\"fas fa-trash-alt\"></i> Supprimer");
                sb.append("</button>");
                sb.append("</form>");
                sb.append("</td>");
                sb.append("</tr>");
            }
            sb.append("</tbody>");
            sb.append("</table>");
        } else {
            sb.append("<p class=\"empty-row\">Aucun utilisateur enregistré.</p>");
        }
        sb.append("</div>");

        sb.append("<div class=\"footer\">© ")
          .append(java.time.Year.now().getValue())
          .append(" M-TECH - Gestion des utilisateurs</div>");
        sb.append("</div>");
        sb.append("</body>");
        sb.append("</html>");

        return sb.toString();
    }

    private String echapperHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }
}