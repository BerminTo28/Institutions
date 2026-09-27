import java.text.SimpleDateFormat;
import java.time.Year;
import java.util.List;

public class UIAbsenceProfesseur {

    private static final String DEFAULT_AVATAR = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='150' height='150'%3E%3Crect width='150' height='150' fill='%231e40af'/%3E%3Ctext x='75' y='95' font-size='60' text-anchor='middle' fill='white' font-family='sans-serif'%3E👨‍🏫%3C/text%3E%3C/svg%3E";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy HH:mm");

    /**
     * Génère la page HTML complète pour la gestion des absences du professeur.
     *
     * @param professeurId Identifiant du professeur (pour affichage)
     * @param annees       Liste des années académiques disponibles
     * @param periodes     Liste des périodes disponibles
     * @param absences     Liste des absences du professeur (déjà filtrées)
     * @param anneeSel     Année sélectionnée dans le filtre
     * @param periodeSel   Période sélectionnée dans le filtre
     * @param matiereSel   Matière sélectionnée (code cours) dans le filtre
     * @param msgType      Type de message (success, error, info)
     * @param msg          Message à afficher
     * @return Chaîne HTML complète
     */
    public static String render(String professeurId,
                                List<AnneeAcademique> annees,
                                List<Periode> periodes,
                                List<Absence> absences,
                                String anneeSel,
                                String periodeSel,
                                String matiereSel,
                                String msgType,
                                String msg) {

        // Nettoyer les valeurs nulles
        if (anneeSel == null) anneeSel = "";
        if (periodeSel == null) periodeSel = "";
        if (matiereSel == null) matiereSel = "";

        // Calcul des statistiques
        int total = absences != null ? absences.size() : 0;
        long justifiees = absences != null ? absences.stream().filter(Absence::isJustifiee).count() : 0;
        long nonJustifiees = total - justifiees;

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>");
        sb.append("<html lang=\"fr\">");
        sb.append("<head>");
        sb.append("<meta charset=\"UTF-8\">");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        sb.append("<title>Mes Absences - Professeur</title>");
        sb.append("<link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css\">");
        sb.append("<link href=\"https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@300;400;500;600;700;800&display=swap\" rel=\"stylesheet\">");
        sb.append("<style>");
        sb.append(renderStyles());
        sb.append("</style>");
        sb.append("</head>");
        sb.append("<body>");
        sb.append("<div class=\"container\">");

        // ===== HEADER =====
        sb.append(renderHeader(professeurId));

        // ===== MESSAGE =====
        if (msg != null && msgType != null && !msg.isBlank()) {
            sb.append("<div class=\"message ").append(msgType).append("\">");
            sb.append("<i class=\"fas fa-").append(msgType.equals("success") ? "check-circle" : "exclamation-triangle").append("\"></i> ");
            sb.append(echapperHtml(msg));
            sb.append("</div>");
        }

        // ===== FILTRES =====
        sb.append(renderFilters(annees, periodes, anneeSel, periodeSel, matiereSel));

        // ===== STATISTIQUES =====
        sb.append("<div class=\"stats-grid\">");
        sb.append("<div class=\"stat-card\"><div class=\"number\">").append(total).append("</div><div class=\"label\">Total absences</div></div>");
        sb.append("<div class=\"stat-card\"><div class=\"number\">").append(justifiees).append("</div><div class=\"label\">✅ Justifiées</div></div>");
        sb.append("<div class=\"stat-card\"><div class=\"number\">").append(nonJustifiees).append("</div><div class=\"label\">❌ Non justifiées</div></div>");
        sb.append("</div>");

        // ===== TABLEAU DES ABSENCES =====
        sb.append(renderTable(absences));

        // ===== FOOTER =====
        sb.append("<div class=\"footer\">");
        sb.append("© ").append(Year.now().getValue()).append(" M-TECH Academy - Tous droits réservés");
        sb.append("</div>");

        sb.append("</div>");
        sb.append("</body>");
        sb.append("</html>");

        return sb.toString();
    }

    /**
     * Page d'erreur simple.
     */
    public static String renderError(String message) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>");
        sb.append("<html lang=\"fr\">");
        sb.append("<head>");
        sb.append("<meta charset=\"UTF-8\">");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        sb.append("<title>Erreur - Absences</title>");
        sb.append("<link href=\"https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;600&display=swap\" rel=\"stylesheet\">");
        sb.append("<style>");
        sb.append("* { margin:0; padding:0; box-sizing:border-box; font-family:'Plus Jakarta Sans',sans-serif; }");
        sb.append("body { background:#f4f6f9; display:flex; justify-content:center; align-items:center; height:100vh; }");
        sb.append(".error-container { background:white; padding:40px; border-radius:16px; text-align:center; max-width:500px; }");
        sb.append(".error-container i { font-size:64px; color:#dc2626; margin-bottom:20px; }");
        sb.append(".error-container h1 { color:#1e293b; margin-bottom:10px; }");
        sb.append(".error-container p { color:#64748b; }");
        sb.append(".error-container .btn { display:inline-block; margin-top:20px; padding:10px 24px; background:#1e40af; color:white; text-decoration:none; border-radius:8px; }");
        sb.append(".error-container .btn:hover { background:#1e3a8a; }");
        sb.append("</style>");
        sb.append("</head>");
        sb.append("<body>");
        sb.append("<div class=\"error-container\">");
        sb.append("<i class=\"fas fa-exclamation-triangle\"></i>");
        sb.append("<h1>Erreur</h1>");
        sb.append("<p>").append(echapperHtml(message)).append("</p>");
        sb.append("<a href=\"/professeur/absences\" class=\"btn\"><i class=\"fas fa-arrow-left\"></i> Retour</a>");
        sb.append("</div>");
        sb.append("</body>");
        sb.append("</html>");
        return sb.toString();
    }

    // ==================== STYLES CSS ====================

    private static String renderStyles() {
        return """
        * { margin:0; padding:0; box-sizing:border-box; }
        body { font-family:'Plus Jakarta Sans',sans-serif; background:#f0f4f8; min-height:100vh; padding:20px; display:flex; justify-content:center; align-items:center; }
        .container { max-width:1200px; width:100%; margin:0 auto; }

        .header { background:linear-gradient(135deg,#1e293b,#0f172a); color:white; padding:25px 30px; border-radius:16px; text-align:center; margin-bottom:25px; position:relative; overflow:hidden; }
        .header-avatar { width:80px; height:80px; border-radius:50%; border:3px solid #3b82f6; object-fit:cover; display:inline-block; box-shadow:0 8px 25px rgba(59,130,246,0.3); position:relative; z-index:1; }
        .header h1 { font-size:24px; font-weight:700; margin-top:10px; position:relative; z-index:1; }
        .header h1 i { color:#3b82f6; margin-right:10px; }
        .header-nav { display:flex; justify-content:center; gap:20px; margin-top:12px; flex-wrap:wrap; position:relative; z-index:1; }
        .header-nav a { color:#94a3b8; text-decoration:none; font-size:14px; transition:0.2s; padding:4px 12px; border-radius:20px; }
        .header-nav a:hover { color:white; background:rgba(255,255,255,0.1); }
        .header-nav a.active { color:white; background:rgba(255,255,255,0.15); font-weight:600; }

        .message { padding:12px 18px; border-radius:8px; margin-bottom:20px; }
        .message.success { background:#d1fae5; color:#16a34a; border-left:4px solid #16a34a; }
        .message.error { background:#fee2e2; color:#dc2626; border-left:4px solid #dc2626; }
        .message.info { background:#dbeafe; color:#2563eb; border-left:4px solid #2563eb; }

        .filters { background:white; border-radius:12px; padding:20px; margin-bottom:20px; border:1px solid #e2e8f0; display:flex; gap:15px; flex-wrap:wrap; align-items:end; }
        .filters .form-group { flex:1; min-width:150px; }
        .filters label { display:block; font-weight:600; color:#475569; margin-bottom:4px; font-size:13px; }
        .filters select, .filters input[type="text"] { width:100%; padding:8px 12px; border:1px solid #cbd5e1; border-radius:8px; font-size:14px; background:white; }
        .filters .btn-filter { background:#1e40af; color:white; border:none; padding:8px 20px; border-radius:8px; cursor:pointer; font-weight:600; transition:0.2s; }
        .filters .btn-filter:hover { background:#1e3a8a; }

        .stats-grid { display:grid; grid-template-columns:repeat(auto-fit,minmax(150px,1fr)); gap:15px; margin-bottom:20px; }
        .stat-card { background:white; border-radius:12px; padding:15px; text-align:center; border:1px solid #e2e8f0; }
        .stat-card .number { font-size:28px; font-weight:800; color:#1e40af; }
        .stat-card .label { font-size:13px; color:#64748b; }

        .table-wrapper { background:white; border-radius:12px; padding:20px; overflow-x:auto; border:1px solid #e2e8f0; }
        table { width:100%; border-collapse:collapse; font-size:14px; }
        th, td { padding:10px 12px; text-align:left; border-bottom:1px solid #e2e8f0; }
        th { background:#f8fafc; font-weight:600; color:#475569; }
        .badge { padding:3px 12px; border-radius:12px; font-size:12px; font-weight:600; }
        .badge-success { background:#d1fae5; color:#16a34a; }
        .badge-danger { background:#fee2e2; color:#dc2626; }

        .empty-state { text-align:center; padding:40px; background:white; border-radius:12px; border:1px solid #e2e8f0; }
        .empty-state i { font-size:48px; color:#94a3b8; margin-bottom:12px; }
        .empty-state p { color:#64748b; font-size:16px; }

        .footer { margin-top:30px; text-align:center; color:#94a3b8; font-size:13px; }

        @media (max-width:768px) { .filters { flex-direction:column; } }
        """;
    }

    // ==================== COMPOSANTS HTML ====================

    private static String renderHeader(String professeurId) {
        System.out.println("Bienvenue Professeur ID: " + professeurId); // Debug log
        return """
        <div class="header">
            <img src=\"""" + DEFAULT_AVATAR + "\" alt=\"Avatar\" class=\"header-avatar\">" +
            "<h1><i class=\"fas fa-user-slash\"></i> Mes Absences</h1>" +
            "<div class=\"header-nav\">" +
            "<a href=\"/dashboard/professeur\"><i class=\"fas fa-home\"></i> Dashboard</a>" +
            "<a href=\"/professeur/profil\"><i class=\"fas fa-user\"></i> Mon Profil</a>" +
            "<a href=\"/professeur/cours\"><i class=\"fas fa-book\"></i> Mes Cours</a>" +
            "<a href=\"/professeur/absences\" class=\"active\"><i class=\"fas fa-user-slash\"></i> Absences</a>" +
            "<a href=\"/logout\"><i class=\"fas fa-sign-out-alt\"></i> Déconnexion</a>" +
            "</div>" +
            "</div>";
    }

    private static String renderFilters(List<AnneeAcademique> annees,
                                        List<Periode> periodes,
                                        String anneeSel,
                                        String periodeSel,
                                        String matiereSel) {
        StringBuilder sb = new StringBuilder();
        sb.append("<form method=\"GET\" action=\"/professeur/absences\" class=\"filters\">");

        // Année
        sb.append("<div class=\"form-group\">");
        sb.append("<label for=\"annee\">Année académique</label>");
        sb.append("<select name=\"annee\" id=\"annee\">");
        sb.append("<option value=\"\">Toutes</option>");
        for (AnneeAcademique a : annees) {
            String val = a.getAnneeAcademique();
            String selected = val.equals(anneeSel) ? "selected" : "";
            sb.append("<option value=\"").append(echapperHtml(val)).append("\" ").append(selected).append(">")
              .append(echapperHtml(val)).append("</option>");
        }
        sb.append("</select>");
        sb.append("</div>");

        // Période
        sb.append("<div class=\"form-group\">");
        sb.append("<label for=\"periode\">Période</label>");
        sb.append("<select name=\"periode\" id=\"periode\">");
        sb.append("<option value=\"\">Toutes</option>");
        for (Periode p : periodes) {
            String val = p.getPeriode();
            String selected = val.equals(periodeSel) ? "selected" : "";
            sb.append("<option value=\"").append(echapperHtml(val)).append("\" ").append(selected).append(">")
              .append(echapperHtml(val)).append("</option>");
        }
        sb.append("</select>");
        sb.append("</div>");

        // Matière (champ texte)
        sb.append("<div class=\"form-group\">");
        sb.append("<label for=\"matiere\">Matière (code cours)</label>");
        sb.append("<input type=\"text\" name=\"matiere\" id=\"matiere\" value=\"").append(echapperHtml(matiereSel)).append("\" placeholder=\"Ex: MATH101\">");
        sb.append("</div>");

        // Bouton
        sb.append("<div>");
        sb.append("<button type=\"submit\" class=\"btn-filter\"><i class=\"fas fa-filter\"></i> Filtrer</button>");
        sb.append("</div>");
        sb.append("</form>");
        return sb.toString();
    }

    private static String renderTable(List<Absence> absences) {
        if (absences == null || absences.isEmpty()) {
            return "<div class=\"empty-state\"><i class=\"fas fa-calendar-check\"></i><p>Aucune absence trouvée pour les filtres sélectionnés.</p></div>";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"table-wrapper\">");
        sb.append("<table>");
        sb.append("<thead><tr>");
        sb.append("<th>Date</th>");
        sb.append("<th>Cours</th>");
        sb.append("<th>Période</th>");
        sb.append("<th>Année</th>");
        sb.append("<th>Statut</th>");
        sb.append("<th>Motif</th>");
        sb.append("</tr></thead>");
        sb.append("<tbody>");

        for (Absence a : absences) {
            String dateStr = a.getDateAbsence() != null ? DATE_FORMAT.format(a.getDateAbsence()) : "";
            String statut = a.isJustifiee()
                ? "<span class=\"badge badge-success\">✅ Justifiée</span>"
                : "<span class=\"badge badge-danger\">❌ Non justifiée</span>";

            sb.append("<tr>");
            sb.append("<td>").append(echapperHtml(dateStr)).append("</td>");
            sb.append("<td><strong>").append(echapperHtml(a.getCodeCours())).append("</strong></td>");
            sb.append("<td>").append(echapperHtml(a.getPeriode())).append("</td>");
            sb.append("<td>").append(echapperHtml(a.getAnneeAcademique())).append("</td>");
            sb.append("<td>").append(statut).append("</td>");
            sb.append("<td>").append(echapperHtml(a.getMotif())).append("</td>");
            sb.append("</tr>");
        }

        sb.append("</tbody>");
        sb.append("</table>");
        sb.append("</div>");
        return sb.toString();
    }

    // ==================== UTILITAIRE ====================

    private static String echapperHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }
}