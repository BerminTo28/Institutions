import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HandlerParametresAccesProfesseurs implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(HandlerParametresAccesProfesseurs.class.getName());
    private final ParametresAccesProfesseur service = new ParametresAccesProfesseur();

    private static final String[] MODULES = {"profil", "cours", "notes", "edt", "absences", "documents", "messages", "parametres"};
    private static final Map<String, String> MODULE_LABELS = new HashMap<>();
    static {
        MODULE_LABELS.put("profil", "Profil");
        MODULE_LABELS.put("cours", "Mes Cours");
        MODULE_LABELS.put("notes", "Saisie Notes");
        MODULE_LABELS.put("edt", "Emploi du Temps");
        MODULE_LABELS.put("absences", "Absences");
        MODULE_LABELS.put("documents", "Documents");
        MODULE_LABELS.put("messages", "Messages");
        MODULE_LABELS.put("parametres", "Paramètres");
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
            LOGGER.log(Level.SEVERE, "Erreur: {0}", e.getMessage());
            ResponseUtil.sendError(exchange, 500, "Erreur interne");
        }
    }

    private void handleGet(HttpExchange exchange, String institutionId) throws IOException {
        try {
            List<Professeur> professeurs = getProfesseurs(institutionId);
            List<AccesProfesseur> accesList = service.getAllAcces(institutionId);
            Map<String, AccesProfesseur> accesMap = new HashMap<>();
            for (AccesProfesseur a : accesList) {
                accesMap.put(a.getNumeroIdentifiant(), a);
            }
            String html = genererPage(professeurs, accesMap, institutionId, null, null);
            ResponseUtil.sendHtml(exchange, 200, html);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erreur chargement page: {0}", e.getMessage());
            ResponseUtil.sendError(exchange, 500, "Erreur lors du chargement");
        }
    }

    private void handlePost(HttpExchange exchange, String institutionId) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> params = parseFormData(body);
        String action = params.get("action");

        if ("update".equalsIgnoreCase(action)) {
            try {
                String professeurId = params.get("professeur_id");
                if (professeurId == null || professeurId.isBlank()) {
                    throw new Exception("Identifiant professeur manquant.");
                }

                AccesProfesseur acces = new AccesProfesseur();
                acces.setNumeroIdentifiant(professeurId);
                acces.setInstitutionId(institutionId);

                for (String module : MODULES) {
                    boolean enabled = "on".equalsIgnoreCase(params.get("module_" + module));
                    switch (module) {
                        case "profil" -> acces.setProfil(enabled);
                        case "cours" -> acces.setCours(enabled);
                        case "notes" -> acces.setNotes(enabled);
                        case "edt" -> acces.setEdt(enabled);
                        case "absences" -> acces.setAbsences(enabled);
                        case "documents" -> acces.setDocuments(enabled);
                        case "messages" -> acces.setMessages(enabled);
                        case "parametres" -> acces.setParametres(enabled);
                    }
                }

                service.updateAcces(acces);
                handleGetWithMessage(exchange, institutionId, "success", "Droits mis à jour avec succès.");
            } catch (Exception e) {
                handleGetWithMessage(exchange, institutionId, "error", "Erreur: " + e.getMessage());
            }
        } else {
            handleGetWithMessage(exchange, institutionId, "error", "Action inconnue.");
        }
    }

    private void handleGetWithMessage(HttpExchange exchange, String institutionId, String msgType, String message) throws IOException {
        try {
            List<Professeur> professeurs = getProfesseurs(institutionId);
            List<AccesProfesseur> accesList = service.getAllAcces(institutionId);
            Map<String, AccesProfesseur> accesMap = new HashMap<>();
            for (AccesProfesseur a : accesList) {
                accesMap.put(a.getNumeroIdentifiant(), a);
            }
            String html = genererPage(professeurs, accesMap, institutionId, msgType, message);
            ResponseUtil.sendHtml(exchange, 200, html);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erreur rechargement: {0}", e.getMessage());
            ResponseUtil.sendError(exchange, 500, "Erreur interne");
        }
    }

    private List<Professeur> getProfesseurs(String institutionId) throws Exception {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            ProfesseurData data = new ProfesseurData(conn);
            return data.listerParInstitution(institutionId);
        } catch (SQLException e) {
            LOGGER.severe(() -> "Erreur chargement professeurs: " + e.getMessage());
            throw new Exception("Impossible de charger les professeurs.");
        }
    }

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
            LOGGER.log(Level.WARNING, "Impossible de r\u00e9cup\u00e9rer l''institutionId: {0}", e.getMessage());
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

    // ==================== GÉNÉRATION HTML ====================
    private String genererPage(List<Professeur> professeurs, Map<String, AccesProfesseur> accesMap,
                               String institutionId, String msgType, String message) {
        StringBuilder sb = new StringBuilder();

        sb.append("<!DOCTYPE html>");
        sb.append("<html lang=\"fr\">");
        sb.append("<head>");
        sb.append("<meta charset=\"UTF-8\">");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        sb.append("<title>Gestion des accès professeurs</title>");
        sb.append("<link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css\">");
        sb.append("<style>");
        sb.append("""
        * { margin:0; padding:0; box-sizing:border-box; font-family:'Segoe UI', sans-serif; }
        body { background:#f4f6f9; padding:20px; }
        .container { max-width:1400px; margin:0 auto; }
        .header { background:white; padding:20px 30px; border-radius:12px; display:flex; justify-content:space-between; align-items:center; margin-bottom:25px; }
        .header h1 { font-size:22px; color:#1e293b; }
        .header h1 i { color:#1e40af; margin-right:10px; }
        .header a { color:#1e40af; text-decoration:none; font-weight:600; }
        .header a:hover { text-decoration:underline; }
        .message { padding:12px 18px; border-radius:8px; margin-bottom:20px; }
        .message.success { background:#d1fae5; color:#16a34a; border-left:4px solid #16a34a; }
        .message.error { background:#fee2e2; color:#dc2626; border-left:4px solid #dc2626; }
        .table-wrapper { background:white; border-radius:12px; padding:20px; overflow-x:auto; }
        table { width:100%; border-collapse:collapse; font-size:14px; }
        th, td { padding:10px 8px; text-align:center; border-bottom:1px solid #e2e8f0; }
        th { background:#f8fafc; font-weight:600; color:#475569; position:sticky; top:0; }
        td:first-child, th:first-child { text-align:left; padding-left:12px; }
        .professeur-info { font-weight:500; color:#1e293b; }
        .professeur-info small { font-weight:normal; color:#94a3b8; font-size:12px; display:block; }
        .toggle { position:relative; display:inline-block; width:40px; height:22px; }
        .toggle input { opacity:0; width:0; height:0; }
        .slider { position:absolute; cursor:pointer; top:0; left:0; right:0; bottom:0; background:#cbd5e1; transition:.3s; border-radius:22px; }
        .slider:before { content:""; position:absolute; height:16px; width:16px; left:3px; bottom:3px; background:white; transition:.3s; border-radius:50%; }
        .toggle input:checked + .slider { background:#1e40af; }
        .toggle input:checked + .slider:before { transform:translateX(18px); }
        .btn-save { background:#16a34a; color:white; border:none; padding:10px 30px; border-radius:8px; font-weight:600; cursor:pointer; font-size:15px; transition:0.2s; margin-top:20px; }
        .btn-save:hover { background:#15803d; }
        .btn-save i { margin-right:8px; }
        .empty-row td { padding:30px; color:#94a3b8; font-style:italic; }
        .footer { margin-top:30px; text-align:center; color:#94a3b8; font-size:13px; }
        """);
        sb.append("</style>");
        sb.append("</head>");
        sb.append("<body>");
        sb.append("<div class=\"container\">");

        // En-tête
        sb.append("<div class=\"header\">");
        sb.append("<h1><i class=\"fas fa-user-tie\"></i> Gestion des accès professeurs</h1>");
        sb.append("<a href=\"/admin/parametres\"><i class=\"fas fa-arrow-left\"></i> Retour aux paramètres</a>");
        sb.append("</div>");

        // Message
        if (message != null && msgType != null) {
            sb.append("<div class=\"message ").append(msgType).append("\">");
            sb.append("<i class=\"fas fa-").append(msgType.equals("success") ? "check-circle" : "exclamation-triangle").append("\"></i> ");
            sb.append(message);
            sb.append("</div>");
        }

        // Tableau
        sb.append("<div class=\"table-wrapper\">");
        if (professeurs.isEmpty()) {
            sb.append("<p style=\"padding:20px;text-align:center;color:#94a3b8;\">Aucun professeur trouvé dans cette institution.</p>");
        } else {
            sb.append("<form method=\"POST\" action=\"/admin/parametres/acces-professeurs\">");
            sb.append("<input type=\"hidden\" name=\"action\" value=\"update\">");
            sb.append("<table>");
            sb.append("<thead><tr><th>Professeur</th>");
            for (String module : MODULES) {
                sb.append("<th>").append(MODULE_LABELS.get(module)).append("</th>");
            }
            sb.append("</tr></thead>");
            sb.append("<tbody>");

            for (Professeur p : professeurs) {
                String id = p.getNumeroIdentifiantProfesseur();
                AccesProfesseur acces = accesMap.get(id);
                if (acces == null) {
                    acces = new AccesProfesseur();
                    acces.setNumeroIdentifiant(id);
                    acces.setInstitutionId(institutionId);
                    acces.setProfil(true);
                    acces.setCours(true);
                    acces.setNotes(true);
                    acces.setEdt(true);
                    acces.setAbsences(true);
                    acces.setDocuments(true);
                    acces.setMessages(true);
                    acces.setParametres(true);
                }

                sb.append("<tr>");
                sb.append("<td class=\"professeur-info\">");
                sb.append(echapperHtml(p.getNomComplet()));
                sb.append("<small>").append(echapperHtml(id)).append("</small>");
                sb.append("<input type=\"hidden\" name=\"professeur_id\" value=\"").append(echapperHtml(id)).append("\">");
                sb.append("</td>");

                for (String module : MODULES) {
                    boolean enabled = getModuleValue(acces, module);
                    sb.append("<td>");
                    sb.append("<label class=\"toggle\">");
                    sb.append("<input type=\"checkbox\" name=\"module_").append(module).append("\"");
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
            sb.append("<div style=\"text-align:center;\">");
            sb.append("<button type=\"submit\" class=\"btn-save\"><i class=\"fas fa-save\"></i> Enregistrer tous les changements</button>");
            sb.append("</div>");
            sb.append("</form>");
        }
        sb.append("</div>");

        sb.append("<div class=\"footer\">© ").append(java.time.Year.now().getValue()).append(" M-TECH - Gestion des accès professeurs</div>");
        sb.append("</div>");
        sb.append("</body>");
        sb.append("</html>");

        return sb.toString();
    }

    private boolean getModuleValue(AccesProfesseur acces, String module) {
        return switch (module) {
            case "profil" -> acces.isProfil();
            case "cours" -> acces.isCours();
            case "notes" -> acces.isNotes();
            case "edt" -> acces.isEdt();
            case "absences" -> acces.isAbsences();
            case "documents" -> acces.isDocuments();
            case "messages" -> acces.isMessages();
            case "parametres" -> acces.isParametres();
            default -> false;
        };
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