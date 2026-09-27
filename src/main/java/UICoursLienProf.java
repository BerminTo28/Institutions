
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Interface utilisateur pour la gestion des liens de cours en ligne par le professeur
 */
public class UICoursLienProf {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /**
     * Affiche la liste des liens de cours
     */
    public static String renderListe(List<CoursLien> liens, String messageSucces, String messageErreur) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>");
        sb.append("<html lang=\"fr\">");
        sb.append("<head>");
        sb.append("<meta charset=\"UTF-8\">");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        sb.append("<title>Gestion des liens de cours</title>");
        sb.append("<link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css\">");
        sb.append("<link href=\"https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@300;400;500;600;700;800&display=swap\" rel=\"stylesheet\">");
        sb.append("<style>");
        sb.append(renderStyles());
        sb.append("</style>");
        sb.append("</head>");
        sb.append("<body>");
        sb.append("<div class=\"container\">");

        // En-tête
        sb.append("<div class=\"header\">");
        sb.append("<h1><i class=\"fas fa-video\"></i> Liens de cours en ligne</h1>");
        sb.append("<p class=\"subtitle\">Partagez les liens de vos réunions (Zoom, Google Meet, etc.)</p>");
        sb.append("</div>");

        if (messageSucces != null && !messageSucces.isEmpty()) {
            sb.append("<div class=\"message success\"><i class=\"fas fa-check-circle\"></i> ").append(echapperHtml(messageSucces)).append("</div>");
        }
        if (messageErreur != null && !messageErreur.isEmpty()) {
            sb.append("<div class=\"message error\"><i class=\"fas fa-exclamation-triangle\"></i> ").append(echapperHtml(messageErreur)).append("</div>");
        }

        sb.append("<div class=\"actions\">");
        sb.append("<a href=\"/professeur/cours-en-ligne/creer\" class=\"btn-primary\"><i class=\"fas fa-plus-circle\"></i> Ajouter un lien</a>");
        sb.append("</div>");

        if (liens == null || liens.isEmpty()) {
            sb.append("<div class=\"empty-state\">");
            sb.append("<i class=\"fas fa-video-slash\"></i>");
            sb.append("<p>Aucun lien de cours pour le moment.</p>");
            sb.append("</div>");
        } else {
            sb.append("<div class=\"table-container\">");
            sb.append("<table>");
            sb.append("<thead><tr>");
            sb.append("<th>Titre</th>");
            sb.append("<th>Lien</th>");
            sb.append("<th>Date de début</th>");
            sb.append("<th>Date de fin</th>");
            sb.append("<th>Actions</th>");
            sb.append("</tr></thead>");
            sb.append("<tbody>");
            for (CoursLien lien : liens) {
                sb.append("<tr>");
                sb.append("<td><strong>").append(echapperHtml(lien.getTitre())).append("</strong></td>");
                sb.append("<td><a href=\"").append(echapperHtml(lien.getUrl())).append("\" target=\"_blank\" class=\"lien\">").append(echapperHtml(lien.getUrl())).append(" <i class=\"fas fa-external-link-alt\"></i></a></td>");
                sb.append("<td>").append(lien.getDateDebut() != null ? lien.getDateDebut().format(DATE_FORMATTER) : "").append("</td>");
                sb.append("<td>").append(lien.getDateFin() != null ? lien.getDateFin().format(DATE_FORMATTER) : "").append("</td>");
                sb.append("<td class=\"actions-cell\">");
                sb.append("<a href=\"/professeur/cours-en-ligne/").append(lien.getId()).append("/modifier\" class=\"btn-sm btn-edit\" title=\"Modifier\"><i class=\"fas fa-edit\"></i></a>");
                sb.append("<a href=\"/professeur/cours-en-ligne/").append(lien.getId()).append("/supprimer\" class=\"btn-sm btn-danger\" title=\"Supprimer\" onclick=\"return confirm('Supprimer ce lien ?')\"><i class=\"fas fa-trash\"></i></a>");
                sb.append("</td>");
                sb.append("</tr>");
            }
            sb.append("</tbody>");
            sb.append("</table>");
            sb.append("</div>");
        }

        sb.append("<div class=\"footer\">");
        sb.append("<a href=\"/dashboard/professeur\"><i class=\"fas fa-arrow-left\"></i> Retour au tableau de bord</a>");
        sb.append("</div>");

        sb.append("</div>");
        sb.append("</body>");
        sb.append("</html>");
        return sb.toString();
    }

    /**
     * Formulaire de création / modification d'un lien
     */
    public static String renderFormulaire(CoursLien lien, String messageErreur, String messageSucces, boolean isModification) {
        if (lien == null) lien = new CoursLien();

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>");
        sb.append("<html lang=\"fr\">");
        sb.append("<head>");
        sb.append("<meta charset=\"UTF-8\">");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        sb.append("<title>").append(isModification ? "Modifier" : "Ajouter").append(" un lien de cours</title>");
        sb.append("<link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css\">");
        sb.append("<link href=\"https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@300;400;500;600;700;800&display=swap\" rel=\"stylesheet\">");
        sb.append("<style>");
        sb.append(renderFormStyles());
        sb.append("</style>");
        sb.append("</head>");
        sb.append("<body>");
        sb.append("<div class=\"container\">");

        sb.append("<div class=\"header\">");
        sb.append("<h1><i class=\"fas fa-video\"></i> ").append(isModification ? "Modifier" : "Ajouter").append(" un lien de cours</h1>");
        sb.append("</div>");

        if (messageErreur != null && !messageErreur.isEmpty()) {
            sb.append("<div class=\"message error\">").append(echapperHtml(messageErreur)).append("</div>");
        }
        if (messageSucces != null && !messageSucces.isEmpty()) {
            sb.append("<div class=\"message success\">").append(echapperHtml(messageSucces)).append("</div>");
        }

        sb.append("<div class=\"card\">");
        sb.append("<form method=\"POST\" action=\"/professeur/cours-en-ligne").append(isModification ? "/" + lien.getId() + "/modifier" : "/creer").append("\">");
        sb.append("<input type=\"hidden\" name=\"action\" value=\"").append(isModification ? "update" : "create").append("\">");

        sb.append("<div class=\"form-group\">");
        sb.append("<label for=\"titre\">Titre *</label>");
        sb.append("<input type=\"text\" id=\"titre\" name=\"titre\" value=\"").append(echapperHtml(lien.getTitre())).append("\" required>");
        sb.append("</div>");

        sb.append("<div class=\"form-group\">");
        sb.append("<label for=\"url\">Lien (URL) *</label>");
        sb.append("<input type=\"url\" id=\"url\" name=\"url\" value=\"").append(echapperHtml(lien.getUrl())).append("\" placeholder=\"https://zoom.us/j/... ou https://meet.google.com/...\" required>");
        sb.append("</div>");

        sb.append("<div class=\"form-group\">");
        sb.append("<label for=\"description\">Description (facultatif)</label>");
        sb.append("<textarea id=\"description\" name=\"description\" rows=\"2\">").append(echapperHtml(lien.getDescription())).append("</textarea>");
        sb.append("</div>");

        sb.append("<div class=\"form-row\">");
        sb.append("<div class=\"form-group\">");
        sb.append("<label for=\"dateDebut\">Date / Heure de début *</label>");
        sb.append("<input type=\"datetime-local\" id=\"dateDebut\" name=\"dateDebut\" value=\"").append(lien.getDateDebut() != null ? lien.getDateDebut().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : "").append("\" required>");
        sb.append("</div>");
        sb.append("<div class=\"form-group\">");
        sb.append("<label for=\"dateFin\">Date / Heure de fin *</label>");
        sb.append("<input type=\"datetime-local\" id=\"dateFin\" name=\"dateFin\" value=\"").append(lien.getDateFin() != null ? lien.getDateFin().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : "").append("\" required>");
        sb.append("</div>");
        sb.append("</div>");

        sb.append("<div class=\"form-actions\">");
        sb.append("<button type=\"submit\" class=\"btn-primary\"><i class=\"fas fa-save\"></i> ").append(isModification ? "Mettre à jour" : "Ajouter").append("</button>");
        sb.append("<a href=\"/professeur/cours-en-ligne\" class=\"btn-secondary\">Annuler</a>");
        sb.append("</div>");

        sb.append("</form>");
        sb.append("</div>");

        sb.append("<div class=\"footer\">");
        sb.append("<a href=\"/professeur/cours-en-ligne\"><i class=\"fas fa-arrow-left\"></i> Retour à la liste</a>");
        sb.append("</div>");

        sb.append("</div>");
        sb.append("</body>");
        sb.append("</html>");
        return sb.toString();
    }

    // ==================== STYLES ====================

    private static String renderStyles() {
        return """
        * { margin:0; padding:0; box-sizing:border-box; }
        body { font-family:'Plus Jakarta Sans',sans-serif; background:#f0f4f8; padding:20px; min-height:100vh; }
        .container { max-width:1100px; margin:0 auto; }
        .header { text-align:center; margin-bottom:25px; }
        .header h1 { font-size:28px; color:#0f172a; }
        .header h1 i { color:#3b82f6; margin-right:10px; }
        .subtitle { color:#64748b; font-size:16px; }
        .message { padding:12px 18px; border-radius:8px; margin-bottom:20px; }
        .message.success { background:#d1fae5; color:#16a34a; border-left:4px solid #16a34a; }
        .message.error { background:#fee2e2; color:#dc2626; border-left:4px solid #dc2626; }
        .actions { margin:20px 0; display:flex; gap:15px; flex-wrap:wrap; }
        .btn-primary { background:#1e40af; color:white; padding:10px 20px; border-radius:8px; text-decoration:none; display:inline-flex; align-items:center; gap:8px; transition:0.2s; border:none; cursor:pointer; font-weight:600; }
        .btn-primary:hover { background:#1e3a8a; transform:translateY(-2px); }
        .btn-secondary { background:#e2e8f0; color:#1e293b; padding:10px 20px; border-radius:8px; text-decoration:none; display:inline-flex; align-items:center; gap:8px; transition:0.2s; border:none; cursor:pointer; font-weight:600; }
        .btn-secondary:hover { background:#cbd5e1; }
        .btn-sm { padding:6px 10px; border-radius:6px; text-decoration:none; display:inline-flex; align-items:center; gap:4px; font-size:13px; transition:0.2s; }
        .btn-edit { background:#fef3c7; color:#b45309; }
        .btn-edit:hover { background:#fde68a; }
        .btn-danger { background:#fee2e2; color:#dc2626; }
        .btn-danger:hover { background:#fecaca; }
        .table-container { overflow-x:auto; background:white; border-radius:12px; padding:15px; box-shadow:0 2px 10px rgba(0,0,0,0.05); }
        table { width:100%; border-collapse:collapse; }
        th, td { padding:12px 15px; text-align:left; border-bottom:1px solid #e2e8f0; }
        th { background:#f8fafc; font-weight:600; color:#475569; }
        tr:hover { background:#f8fafc; }
        .lien { color:#1e40af; text-decoration:none; font-size:13px; word-break:break-all; }
        .lien:hover { text-decoration:underline; }
        .lien i { margin-left:4px; font-size:12px; }
        .empty-state { text-align:center; padding:60px 20px; background:white; border-radius:12px; }
        .empty-state i { font-size:64px; color:#cbd5e1; margin-bottom:15px; }
        .footer { margin-top:30px; text-align:center; }
        .footer a { color:#64748b; text-decoration:none; }
        .footer a:hover { color:#1e293b; }
        .actions-cell { display:flex; gap:6px; flex-wrap:wrap; }
        """;
    }

    private static String renderFormStyles() {
        return """
        * { margin:0; padding:0; box-sizing:border-box; }
        body { font-family:'Plus Jakarta Sans',sans-serif; background:#f0f4f8; padding:20px; min-height:100vh; }
        .container { max-width:700px; margin:0 auto; }
        .header { text-align:center; margin-bottom:25px; }
        .header h1 { font-size:28px; color:#0f172a; }
        .header h1 i { color:#3b82f6; margin-right:10px; }
        .card { background:white; border-radius:16px; padding:30px; box-shadow:0 4px 20px rgba(0,0,0,0.05); }
        .form-group { margin-bottom:18px; }
        .form-group label { display:block; font-weight:600; color:#475569; margin-bottom:5px; font-size:14px; }
        .form-group input, .form-group select, .form-group textarea { width:100%; padding:10px 14px; border:1px solid #cbd5e1; border-radius:8px; font-size:14px; transition:0.2s; }
        .form-group input:focus, .form-group select:focus, .form-group textarea:focus { outline:none; border-color:#1e40af; box-shadow:0 0 0 3px rgba(30,64,175,0.1); }
        .form-group textarea { resize:vertical; min-height:60px; }
        .form-row { display:flex; gap:20px; flex-wrap:wrap; }
        .form-row .form-group { flex:1; min-width:200px; }
        .form-actions { display:flex; gap:15px; margin-top:25px; justify-content:flex-end; flex-wrap:wrap; }
        .btn-primary { background:#1e40af; color:white; padding:10px 24px; border-radius:8px; border:none; cursor:pointer; font-weight:600; transition:0.2s; display:inline-flex; align-items:center; gap:8px; }
        .btn-primary:hover { background:#1e3a8a; }
        .btn-secondary { background:#e2e8f0; color:#1e293b; padding:10px 24px; border-radius:8px; text-decoration:none; display:inline-flex; align-items:center; gap:8px; transition:0.2s; border:none; cursor:pointer; font-weight:600; }
        .btn-secondary:hover { background:#cbd5e1; }
        .message { padding:12px 18px; border-radius:8px; margin-bottom:20px; }
        .message.success { background:#d1fae5; color:#16a34a; border-left:4px solid #16a34a; }
        .message.error { background:#fee2e2; color:#dc2626; border-left:4px solid #dc2626; }
        .footer { margin-top:25px; text-align:center; }
        .footer a { color:#64748b; text-decoration:none; }
        .footer a:hover { color:#1e293b; }
        """;
    }

    private static String echapperHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }
}