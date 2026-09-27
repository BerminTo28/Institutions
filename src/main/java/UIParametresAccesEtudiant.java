import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class UIParametresAccesEtudiant implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(UIParametresAccesEtudiant.class.getName());
    private final ParametresAcces service = new ParametresAcces();

    // Liste des modules (identique à EtudiantDashboardHandler)
    private static final String[] MODULES = {
        "connexion", "profil", "notes", "bulletin", "paiement",
        "messages", "documents", "edt", "attestations", "absences", "assistance"
    };
    private static final Map<String, String> MODULE_LABELS = new HashMap<>();
    static {
        MODULE_LABELS.put("connexion", "Connexion");
        MODULE_LABELS.put("profil", "Profil");
        MODULE_LABELS.put("notes", "Notes");
        MODULE_LABELS.put("bulletin", "Bulletin");
        MODULE_LABELS.put("paiement", "Paiement");
        MODULE_LABELS.put("messages", "Messages");
        MODULE_LABELS.put("documents", "Documents");
        MODULE_LABELS.put("edt", "Emploi du temps");
        MODULE_LABELS.put("attestations", "Attestations");
        MODULE_LABELS.put("absences", "Absences");
        MODULE_LABELS.put("assistance", "Assistance");
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
            LOGGER.severe(() -> "Erreur dans HandlerParametresAcces : " + e.getMessage());
            ResponseUtil.sendError(exchange, 500, "Erreur interne du serveur");
        }
    }

    // ------------------ GET ------------------
    private void handleGet(HttpExchange exchange, String institutionId) throws IOException {
        Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
        String annee = params.getOrDefault("annee", "");
        String periode = params.getOrDefault("periode", "");
        String classe = params.getOrDefault("classe", "");

        try {
            List<AnneeAcademique> annees = service.getAnnees(institutionId);
            List<Periode> periodes = new ArrayList<>();
            List<Classe> classes = new ArrayList<>();
            List<ParametresAcces.EtudiantAccesDTO> etudiants = new ArrayList<>();

            if (!annee.isEmpty()) {
                periodes = service.getPeriodes(institutionId, annee);
            }
            if (!annee.isEmpty() && !periode.isEmpty()) {
                classes = service.getClasses(institutionId, annee, periode);
            }
            if (!annee.isEmpty() && !periode.isEmpty() && !classe.isEmpty()) {
                etudiants = service.getEtudiantsWithAcces(institutionId, annee, periode, classe);
            }

            String html = genererPage(annees, periodes, classes, etudiants, annee, periode, classe, institutionId, null, null);
            ResponseUtil.sendHtml(exchange, 200, html);

        } catch (Exception e) {
            LOGGER.severe(() -> "Erreur chargement page: " + e.getMessage());
            ResponseUtil.sendError(exchange, 500, "Erreur lors du chargement");
        }
    }

    // ------------------ POST ------------------
    private void handlePost(HttpExchange exchange, String institutionId) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> params = parseFormData(body);

        String action = params.get("action");
        if ("update".equalsIgnoreCase(action)) {
            try {
                String annee = params.get("annee");
                String periode = params.get("periode");
                String classe = params.get("classe");
                if (annee == null || periode == null || classe == null) {
                    throw new Exception("Filtres manquants pour la sauvegarde.");
                }

                // Récupérer la liste des étudiants de la classe
                List<ParametresAcces.EtudiantAccesDTO> dtos = service.getEtudiantsWithAcces(institutionId, annee, periode, classe);
                Map<String, AccesEtudiant> accesMap = new HashMap<>();
                for (ParametresAcces.EtudiantAccesDTO dto : dtos) {
                    AccesEtudiant a = dto.acces;
                    // Réinitialiser tous les modules à false (seuls les cochés seront activés)
                    a.setConnexion(false);
                    a.setProfil(false);
                    a.setNotes(false);
                    a.setBulletin(false);
                    a.setPaiement(false);
                    a.setMessages(false);
                    a.setDocuments(false);
                    a.setEdt(false);
                    a.setAttestations(false);
                    a.setAbsences(false);
                    a.setAssistance(false);
                    accesMap.put(a.getNumeroIdentifiant(), a);
                }

                // Parcourir les paramètres pour activer les modules cochés
                for (String key : params.keySet()) {
                    if (key.startsWith("module_")) {
                        String[] parts = key.split("_", 3);
                        if (parts.length == 3) {
                            String moduleName = parts[1];
                            String etudiantId = parts[2];
                            if (accesMap.containsKey(etudiantId)) {
                                AccesEtudiant a = accesMap.get(etudiantId);
                                boolean enabled = "on".equalsIgnoreCase(params.get(key));
                                switch (moduleName) {
                                    case "connexion" -> a.setConnexion(enabled);
                                    case "profil" -> a.setProfil(enabled);
                                    case "notes" -> a.setNotes(enabled);
                                    case "bulletin" -> a.setBulletin(enabled);
                                    case "paiement" -> a.setPaiement(enabled);
                                    case "messages" -> a.setMessages(enabled);
                                    case "documents" -> a.setDocuments(enabled);
                                    case "edt" -> a.setEdt(enabled);
                                    case "attestations" -> a.setAttestations(enabled);
                                    case "absences" -> a.setAbsences(enabled);
                                    case "assistance" -> a.setAssistance(enabled);
                                }
                            }
                        }
                    }
                }

                // Sauvegarder tous les accès modifiés
                List<AccesEtudiant> toSave = new ArrayList<>(accesMap.values());
                service.updateAccesBulk(toSave);

                // Recharger la page avec message de succès
                handleGetWithMessage(exchange, institutionId, annee, periode, classe, "success", "Droits d'accès mis à jour avec succès.");

            } catch (Exception e) {
                LOGGER.severe(() -> "Erreur mise à jour: " + e.getMessage());
                handleGetWithMessage(exchange, institutionId, "", "", "", "error", "Erreur: " + e.getMessage());
            }
        } else {
            handleGetWithMessage(exchange, institutionId, "", "", "", "error", "Action inconnue.");
        }
    }

    // ------------------ HELPERS ------------------
    private void handleGetWithMessage(HttpExchange exchange, String institutionId, String annee, String periode, String classe,
                                      String msgType, String message) throws IOException {
        try {
            List<AnneeAcademique> annees = service.getAnnees(institutionId);
            List<Periode> periodes = new ArrayList<>();
            List<Classe> classes = new ArrayList<>();
            List<ParametresAcces.EtudiantAccesDTO> etudiants = new ArrayList<>();

            if (!annee.isEmpty()) {
                periodes = service.getPeriodes(institutionId, annee);
            }
            if (!annee.isEmpty() && !periode.isEmpty()) {
                classes = service.getClasses(institutionId, annee, periode);
            }
            if (!annee.isEmpty() && !periode.isEmpty() && !classe.isEmpty()) {
                etudiants = service.getEtudiantsWithAcces(institutionId, annee, periode, classe);
            }

            String html = genererPage(annees, periodes, classes, etudiants, annee, periode, classe, institutionId, msgType, message);
            ResponseUtil.sendHtml(exchange, 200, html);
        } catch (Exception e) {
            LOGGER.severe(() -> "Erreur rechargement: " + e.getMessage());
            ResponseUtil.sendError(exchange, 500, "Erreur interne");
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

    private Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null) return params;
        for (String pair : query.split("&")) {
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
    private String genererPage(List<AnneeAcademique> annees, List<Periode> periodes, List<Classe> classes,
                               List<ParametresAcces.EtudiantAccesDTO> etudiants,
                               String anneeSel, String periodeSel, String classeSel,
                               String institutionId, String msgType, String message) {
System.out.println("Connecte : "+institutionId);
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>");
        sb.append("<html lang=\"fr\">");
        sb.append("<head>");
        sb.append("<meta charset=\"UTF-8\">");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        sb.append("<title>Gestion des accès étudiants</title>");
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
        .filters { background:white; padding:20px; border-radius:12px; margin-bottom:20px; display:flex; gap:15px; flex-wrap:wrap; align-items:end; }
        .filters .form-group { flex:1; min-width:150px; }
        .filters label { display:block; font-weight:600; color:#475569; margin-bottom:4px; font-size:13px; }
        .filters select { width:100%; padding:8px 12px; border:1px solid #cbd5e1; border-radius:8px; font-size:14px; background:white; }
        .filters .btn-filter { background:#1e40af; color:white; border:none; padding:8px 20px; border-radius:8px; cursor:pointer; font-weight:600; }
        .filters .btn-filter:hover { background:#1e3a8a; }
        .table-wrapper { background:white; border-radius:12px; padding:20px; overflow-x:auto; }
        table { width:100%; border-collapse:collapse; font-size:14px; }
        th, td { padding:10px 8px; text-align:center; border-bottom:1px solid #e2e8f0; }
        th { background:#f8fafc; font-weight:600; color:#475569; position:sticky; top:0; }
        td:first-child, th:first-child { text-align:left; padding-left:12px; }
        .etudiant-info { font-weight:500; color:#1e293b; }
        .etudiant-info small { font-weight:normal; color:#94a3b8; font-size:12px; display:block; }
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
        .message { padding:12px 18px; border-radius:8px; margin-bottom:20px; }
        .message.success { background:#d1fae5; color:#16a34a; border-left:4px solid #16a34a; }
        .message.error { background:#fee2e2; color:#dc2626; border-left:4px solid #dc2626; }
        """);
        sb.append("</style>");
        sb.append("</head>");
        sb.append("<body>");
        sb.append("<div class=\"container\">");

        // En-tête
        sb.append("<div class=\"header\">");
        sb.append("<h1><i class=\"fas fa-lock\"></i> Gestion des accès étudiants</h1>");
        sb.append("<a href=\"/admin/parametres\"><i class=\"fas fa-arrow-left\"></i> Retour aux paramètres</a>");
        sb.append("</div>");

        // Message
        if (message != null && msgType != null) {
            sb.append("<div class=\"message ").append(msgType).append("\">");
            sb.append("<i class=\"fas fa-").append(msgType.equals("success") ? "check-circle" : "exclamation-triangle").append("\"></i> ");
            sb.append(message);
            sb.append("</div>");
        }

        // Filtres
        sb.append("<form method=\"GET\" action=\"/admin/parametres/acces\" id=\"filterForm\">");
        sb.append("<div class=\"filters\">");
        // Année
        sb.append("<div class=\"form-group\">");
        sb.append("<label for=\"annee\">Année académique</label>");
        sb.append("<select name=\"annee\" id=\"annee\" onchange=\"this.form.submit()\">");
        sb.append("<option value=\"\">-- Sélectionner --</option>");
        for (AnneeAcademique a : annees) {
            String selected = a.getAnneeAcademique().equals(anneeSel) ? "selected" : "";
            sb.append("<option value=\"").append(echapperHtml(a.getAnneeAcademique())).append("\" ").append(selected).append(">")
              .append(echapperHtml(a.getAnneeAcademique())).append("</option>");
        }
        sb.append("</select>");
        sb.append("</div>");
        // Période
        sb.append("<div class=\"form-group\">");
        sb.append("<label for=\"periode\">Période</label>");
        sb.append("<select name=\"periode\" id=\"periode\" onchange=\"this.form.submit()\">");
        sb.append("<option value=\"\">-- Sélectionner --</option>");
        for (Periode p : periodes) {
            String selected = p.getPeriode().equals(periodeSel) ? "selected" : "";
            sb.append("<option value=\"").append(echapperHtml(p.getPeriode())).append("\" ").append(selected).append(">")
              .append(echapperHtml(p.getPeriode())).append("</option>");
        }
        sb.append("</select>");
        sb.append("</div>");
        // Classe
        sb.append("<div class=\"form-group\">");
        sb.append("<label for=\"classe\">Classe</label>");
        sb.append("<select name=\"classe\" id=\"classe\" onchange=\"this.form.submit()\">");
        sb.append("<option value=\"\">-- Sélectionner --</option>");
        for (Classe c : classes) {
            String selected = c.getNomClasse().equals(classeSel) ? "selected" : "";
            sb.append("<option value=\"").append(echapperHtml(c.getNomClasse())).append("\" ").append(selected).append(">")
              .append(echapperHtml(c.getNomClasse())).append("</option>");
        }
        sb.append("</select>");
        sb.append("</div>");
        sb.append("<div>");
        sb.append("<button type=\"submit\" class=\"btn-filter\"><i class=\"fas fa-filter\"></i> Filtrer</button>");
        sb.append("</div>");
        sb.append("</div>");
        sb.append("</form>");

        // Tableau
        sb.append("<div class=\"table-wrapper\">");
        if (!classeSel.isEmpty() && !etudiants.isEmpty()) {
            sb.append("<form method=\"POST\" action=\"/admin/parametres/acces\">");
            sb.append("<input type=\"hidden\" name=\"action\" value=\"update\">");
            sb.append("<input type=\"hidden\" name=\"annee\" value=\"").append(echapperHtml(anneeSel)).append("\">");
            sb.append("<input type=\"hidden\" name=\"periode\" value=\"").append(echapperHtml(periodeSel)).append("\">");
            sb.append("<input type=\"hidden\" name=\"classe\" value=\"").append(echapperHtml(classeSel)).append("\">");

            sb.append("<table>");
            sb.append("<thead><tr><th>Étudiant</th>");
            for (String module : MODULES) {
                sb.append("<th>").append(MODULE_LABELS.get(module)).append("</th>");
            }
            sb.append("</tr></thead>");
            sb.append("<tbody>");

            for (ParametresAcces.EtudiantAccesDTO dto : etudiants) {
                Etudiant e = dto.etudiant;
                AccesEtudiant a = dto.acces;
                String id = e.getNumeroIdentifiantEtudiant();

                sb.append("<tr>");
                sb.append("<td class=\"etudiant-info\">");
                sb.append(echapperHtml(e.getNomComplet()));
                sb.append("<small>").append(echapperHtml(id)).append("</small>");
                sb.append("</td>");

                for (String module : MODULES) {
                    boolean enabled = getModuleValue(a, module);
                    sb.append("<td>");
                    sb.append("<label class=\"toggle\">");
                    sb.append("<input type=\"checkbox\" name=\"module_").append(module).append("_").append(id).append("\"");
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
        } else if (!classeSel.isEmpty() && etudiants.isEmpty()) {
            sb.append("<p style=\"padding:20px;text-align:center;color:#94a3b8;\">Aucun étudiant trouvé dans cette classe.</p>");
        } else {
            sb.append("<p style=\"padding:20px;text-align:center;color:#94a3b8;\">Sélectionnez une année, une période et une classe pour afficher les étudiants.</p>");
        }
        sb.append("</div>");

        sb.append("<div class=\"footer\">© ").append(java.time.Year.now().getValue()).append(" M-TECH - Gestion des accès</div>");
        sb.append("</div>");
        sb.append("</body>");
        sb.append("</html>");

        return sb.toString();
    }

    private boolean getModuleValue(AccesEtudiant acces, String module) {
        return switch (module) {
            case "connexion" -> acces.isConnexion();
            case "profil" -> acces.isProfil();
            case "notes" -> acces.isNotes();
            case "bulletin" -> acces.isBulletin();
            case "paiement" -> acces.isPaiement();
            case "messages" -> acces.isMessages();
            case "documents" -> acces.isDocuments();
            case "edt" -> acces.isEdt();
            case "attestations" -> acces.isAttestations();
            case "absences" -> acces.isAbsences();
            case "assistance" -> acces.isAssistance();
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