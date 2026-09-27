import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class UIFormulaireAcces implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(UIFormulaireAcces.class.getName());
    private final UtilisateurData utilisateurData;

    public UIFormulaireAcces(Connection connection) {
        this.utilisateurData = new UtilisateurData(connection);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        try {
            String institutionId = getInstitutionIdFromSession(exchange);
            if (institutionId == null) {
                ResponseUtil.sendRedirect(exchange, "/admin/acces?error=session");
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
            LOGGER.severe(() -> "Erreur dans UIFormulaireAcces : " + e.getMessage());
            ResponseUtil.sendError(exchange, 500, "Erreur interne");
        }
    }

    // =========================================================
    // GET — Affiche le formulaire
    // =========================================================
    private void handleGet(HttpExchange exchange, String institutionId) throws IOException {
        Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
        String action = params.getOrDefault("action", "add");
        String userId = params.get("userId");

        Utilisateur utilisateur;
        if ("edit".equals(action) && userId != null && !userId.isBlank()) {
            utilisateur = utilisateurData.read(userId, institutionId);
            if (utilisateur == null) {
                LOGGER.warning(() -> "⚠️ Tentative d'édition d'un utilisateur inexistant "
                        + "ou d'une autre institution : " + userId
                        + " (institution=" + institutionId + ")");
                ResponseUtil.sendRedirect(exchange, "/admin/acces?error=user_not_found");
                return;
            }
        } else {
            String identifiant = userId != null ? userId : "";
            utilisateur = new Utilisateur(identifiant, "", "", identifiant, 3,
                    new AccesAdmin(), null);
        }

        String html = genererFormulaire(utilisateur, action, institutionId, null, null);
        ResponseUtil.sendHtml(exchange, 200, html);
    }

    // =========================================================
    // POST — Enregistre (création ou mise à jour)
    // =========================================================
    private void handlePost(HttpExchange exchange, String institutionId) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8);
        Map<String, String> params = parseFormData(body);

        String action = params.get("action");
        String userId = params.get("userId");
        if (userId == null || userId.isBlank()) {
            ResponseUtil.sendRedirect(exchange, "/admin/acces?error=missing_user_id");
            return;
        }

        try {
            String nom = params.getOrDefault("nom", "").trim();
            String prenom = params.getOrDefault("prenom", "").trim();
            String email = params.getOrDefault("email", "").trim();
            String motDePasse = params.get("mot_de_passe");
            int niveau = parseNiveau(params.get("niveau"));

            // ✅ Validation
            if (nom.isEmpty() || prenom.isEmpty() || email.isEmpty()) {
                ResponseUtil.sendRedirect(exchange,
                        "/admin/acces/form?action=" + action + "&userId=" + userId
                        + "&error=missing_fields");
                return;
            }

            // Construire les permissions
            AccesAdmin acces = new AccesAdmin();
            for (AccesAdmin.Module module : AccesAdmin.Module.values()) {
                for (AccesAdmin.Action act : AccesAdmin.Action.values()) {
                    String paramName = module.name() + "_" + act.name();
                    boolean checked = "on".equalsIgnoreCase(params.get(paramName));
                    acces.setPermission(module, act, checked);
                }
            }

            // Construire l'utilisateur
            Utilisateur utilisateur = new Utilisateur(userId, nom, prenom, email,
                    niveau, acces, null);

            // ✅ Initialisation à false
            boolean success ;

            if ("edit".equals(action)) {
                // ----- MODE ÉDITION -----
                if (!utilisateurData.existsInInstitution(userId, institutionId)) {
                    LOGGER.warning(() -> "⚠️ Tentative d'édition d'un utilisateur "
                            + "d'une autre institution : " + userId);
                    ResponseUtil.sendRedirect(exchange, "/admin/acces?error=forbidden");
                    return;
                }

                // ✅ update retourne boolean — plus de try/catch
                success = utilisateurData.update(
                        utilisateur,
                        (motDePasse != null && !motDePasse.isBlank()) ? motDePasse : null,
                        institutionId);

            } else {
                // ----- MODE CRÉATION -----
                if (motDePasse == null || motDePasse.isBlank()) {
                    ResponseUtil.sendRedirect(exchange,
                            "/admin/acces/form?action=add&error=password_required");
                    return;
                }

                if (utilisateurData.existsInInstitution(userId, institutionId)) {
                    LOGGER.warning(() -> "⚠️ Utilisateur déjà existant dans cette "
                            + "institution : " + userId);
                    ResponseUtil.sendRedirect(exchange,
                            "/admin/acces/form?action=add&error=already_exists");
                    return;
                }

                success = utilisateurData.create(utilisateur, motDePasse, institutionId);
            }

            // ✅ Redirection selon le résultat
            if (success) {
                ResponseUtil.sendRedirect(exchange, "/admin/acces?msg=save_success");
            } else {
                ResponseUtil.sendRedirect(exchange,
                        "/admin/acces/form?action=" + action
                        + "&userId=" + userId + "&error=save_failed");
            }

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Erreur sauvegarde utilisateur " + userId, e);
            ResponseUtil.sendRedirect(exchange, "/admin/acces?error=exception");
        }
    }

    private int parseNiveau(String value) {
        if (value == null) return 3;
        try {
            int n = Integer.parseInt(value);
            if (n < 1 || n > 3) {
                LOGGER.warning(() -> "Niveau invalide : " + value + " → 3 par défaut");
                return 3;
            }
            return n;
        } catch (NumberFormatException e) {
            return 3;
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
            String[] parts = cookie.trim().split("=", 2);   // ✅ limité à 2
            if (parts.length == 2 && "SESSION_ID".equals(parts[0])) {
                return parts[1];
            }
        }
        return null;
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null) return params;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);   // ✅ limité à 2
            if (kv.length == 2) {
                try {
                    params.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8.name()),
                               URLDecoder.decode(kv[1], StandardCharsets.UTF_8.name()));
                } catch (UnsupportedEncodingException e) { /* ignore */ }
            }
        }
        return params;
    }

    private Map<String, String> parseFormData(String body) {
        Map<String, String> params = new HashMap<>();
        if (body == null || body.isBlank()) return params;
        for (String pair : body.split("&")) {
            String[] kv = pair.split("=", 2);   // ✅ limité à 2
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
    private String genererFormulaire(Utilisateur utilisateur, String action,
                                       String institutionId, String msgType,
                                       String message) {
        LOGGER.fine(() -> "Génération formulaire " + action
                + " pour institution " + institutionId);

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>");
        sb.append("<html lang=\"fr\">");
        sb.append("<head>");
        sb.append("<meta charset=\"UTF-8\">");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        sb.append("<title>")
          .append("edit".equals(action) ? "Modifier" : "Ajouter")
          .append(" un utilisateur</title>");
        sb.append("<link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css\">");
        sb.append("<style>");
        sb.append("""
        * { margin:0; padding:0; box-sizing:border-box; font-family:'Segoe UI', sans-serif; }
        body { background:#f4f6f9; padding:20px; }
        .container { max-width:1100px; margin:0 auto; }
        .header { background:white; padding:20px 30px; border-radius:12px; display:flex; justify-content:space-between; align-items:center; margin-bottom:25px; }
        .header h1 { font-size:22px; color:#1e293b; }
        .header h1 i { color:#1e40af; margin-right:10px; }
        .header a { color:#1e40af; text-decoration:none; font-weight:600; }
        .header a:hover { text-decoration:underline; }
        .form-card { background:white; border-radius:12px; padding:25px; }
        .form-grid { display:grid; grid-template-columns:1fr 1fr; gap:20px; }
        .form-group { margin-bottom:15px; }
        .form-group label { display:block; font-weight:600; color:#475569; margin-bottom:5px; }
        .form-group input, .form-group select { width:100%; padding:10px 14px; border:1px solid #cbd5e1; border-radius:8px; font-size:14px; }
        .form-group input:focus, .form-group select:focus { border-color:#1e40af; outline:none; }
        .table-perms { width:100%; border-collapse:collapse; font-size:14px; margin-top:20px; }
        .table-perms th, .table-perms td { padding:8px 6px; text-align:center; border-bottom:1px solid #e2e8f0; }
        .table-perms th { background:#f8fafc; font-weight:600; color:#475569; }
        .table-perms td:first-child { text-align:left; font-weight:500; padding-left:12px; }
        .toggle { position:relative; display:inline-block; width:40px; height:22px; }
        .toggle input { opacity:0; width:0; height:0; }
        .slider { position:absolute; cursor:pointer; top:0; left:0; right:0; bottom:0; background:#cbd5e1; transition:.3s; border-radius:22px; }
        .slider:before { content:""; position:absolute; height:16px; width:16px; left:3px; bottom:3px; background:white; transition:.3s; border-radius:50%; }
        .toggle input:checked + .slider { background:#1e40af; }
        .toggle input:checked + .slider:before { transform:translateX(18px); }
        .btn-submit { background:#1e40af; color:white; border:none; padding:12px 40px; border-radius:8px; font-weight:600; cursor:pointer; font-size:15px; transition:0.2s; }
        .btn-submit:hover { background:#1e3a8a; }
        .btn-submit i { margin-right:8px; }
        .footer { margin-top:30px; text-align:center; color:#94a3b8; font-size:13px; }
        .message { padding:12px 18px; border-radius:8px; margin-bottom:20px; }
        .message.success { background:#d1fae5; color:#16a34a; border-left:4px solid #16a34a; }
        .message.error { background:#fee2e2; color:#dc2626; border-left:4px solid #dc2626; }
        """);
        sb.append("</style>");
        sb.append("</head>");
        sb.append("<body>");
        sb.append("<div class=\"container\">");

        sb.append("<div class=\"header\">");
        sb.append("<h1><i class=\"fas fa-user-cog\"></i> ");
        sb.append("edit".equals(action) ? "Modifier l'utilisateur" : "Ajouter un utilisateur");
        sb.append("</h1>");
        sb.append("<a href=\"/admin/acces\"><i class=\"fas fa-arrow-left\"></i> Retour à la liste</a>");
        sb.append("</div>");

        if (message != null && msgType != null) {
            sb.append("<div class=\"message ").append(msgType).append("\">");
            sb.append("<i class=\"fas fa-")
              .append(msgType.equals("success") ? "check-circle" : "exclamation-triangle")
              .append("\"></i> ");
            sb.append(message);
            sb.append("</div>");
        }

        sb.append("<div class=\"form-card\">");
        sb.append("<form method=\"POST\" action=\"/admin/acces/form\">");
        sb.append("<input type=\"hidden\" name=\"action\" value=\"").append(action).append("\">");

        sb.append("<div class=\"form-grid\">");

        sb.append("<div class=\"form-group\">");
        sb.append("<label for=\"userId\">Identifiant <span style=\"color:red;\">*</span></label>");
        sb.append("<input type=\"text\" name=\"userId\" id=\"userId\" value=\"")
          .append(echapperHtml(utilisateur.getIdentifiant())).append("\"");
        if ("edit".equals(action)) sb.append(" readonly");
        sb.append(" required>");
        sb.append("</div>");

        sb.append("<div class=\"form-group\">");
        sb.append("<label for=\"nom\">Nom <span style=\"color:red;\">*</span></label>");
        sb.append("<input type=\"text\" name=\"nom\" id=\"nom\" value=\"")
          .append(echapperHtml(utilisateur.getNom())).append("\" required>");
        sb.append("</div>");

        sb.append("<div class=\"form-group\">");
        sb.append("<label for=\"prenom\">Prénom <span style=\"color:red;\">*</span></label>");
        sb.append("<input type=\"text\" name=\"prenom\" id=\"prenom\" value=\"")
          .append(echapperHtml(utilisateur.getPrenom())).append("\" required>");
        sb.append("</div>");

        sb.append("<div class=\"form-group\">");
        sb.append("<label for=\"email\">Email <span style=\"color:red;\">*</span></label>");
        sb.append("<input type=\"email\" name=\"email\" id=\"email\" value=\"")
          .append(echapperHtml(utilisateur.getEmail())).append("\" required>");
        sb.append("</div>");

        sb.append("<div class=\"form-group\">");
        sb.append("<label for=\"mot_de_passe\">");
        if ("edit".equals(action)) {
            sb.append("Nouveau mot de passe <span style=\"color:gray;\">")
              .append("(laisser vide pour ne pas changer)</span>");
        } else {
            sb.append("Mot de passe <span style=\"color:red;\">*</span>");
        }
        sb.append("</label>");
        sb.append("<input type=\"password\" name=\"mot_de_passe\" id=\"mot_de_passe\"");
        if (!"edit".equals(action)) sb.append(" required");
        sb.append(">");
        sb.append("</div>");

        sb.append("<div class=\"form-group\">");
        sb.append("<label for=\"niveau\">Niveau <span style=\"color:red;\">*</span></label>");
        sb.append("<select name=\"niveau\" id=\"niveau\" required>");
        int currentNiveau = utilisateur.getNiveau();
        sb.append("<option value=\"1\" ")
          .append(currentNiveau == 1 ? "selected" : "")
          .append(">Administrateur</option>");
        sb.append("<option value=\"2\" ")
          .append(currentNiveau == 2 ? "selected" : "")
          .append(">Professeur</option>");
        sb.append("<option value=\"3\" ")
          .append(currentNiveau == 3 ? "selected" : "")
          .append(">Étudiant</option>");
        sb.append("</select>");
        sb.append("</div>");
        sb.append("</div>");

        sb.append("<h3 style=\"margin:20px 0 10px; color:#1e293b;\">Permissions par module</h3>");
        sb.append("<div style=\"overflow-x:auto;\">");
        sb.append("<table class=\"table-perms\">");
        sb.append("<thead><tr><th>Module</th>");
        for (AccesAdmin.Action act : AccesAdmin.Action.values()) {
            sb.append("<th>").append(act.name()).append("</th>");
        }
        sb.append("</tr></thead>");
        sb.append("<tbody>");

        AccesAdmin acces = utilisateur.getAcces() != null
                ? utilisateur.getAcces() : new AccesAdmin();
        for (AccesAdmin.Module module : AccesAdmin.Module.values()) {
            sb.append("<tr>");
            sb.append("<td>").append(module.name()).append("</td>");
            for (AccesAdmin.Action act : AccesAdmin.Action.values()) {
                boolean enabled = acces.hasPermission(module, act);
                String paramName = module.name() + "_" + act.name();
                sb.append("<td>");
                sb.append("<label class=\"toggle\">");
                sb.append("<input type=\"checkbox\" name=\"")
                  .append(paramName).append("\"");
                if (enabled) sb.append(" checked");
                sb.append(">");
                sb.append("<span class=\"slider\"></span>");
                sb.append("</label>");
                sb.append("</td>");
            }
            sb.append("</tr>");
        }
        sb.append("</tbody>");
        sb.append("</table>");
        sb.append("</div>");

        sb.append("<div style=\"text-align:center; margin-top:25px;\">");
        sb.append("<button type=\"submit\" class=\"btn-submit\">")
          .append("<i class=\"fas fa-save\"></i> Enregistrer</button>");
        sb.append("</div>");

        sb.append("</form>");
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