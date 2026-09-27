import java.time.Year;
import java.util.List;
import java.util.stream.Collectors;

public class UIProfesseurProgramme {

    private static final String DEFAULT_AVATAR = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='150' height='150'%3E%3Crect width='150' height='150' fill='%231e40af'/%3E%3Ctext x='75' y='95' font-size='60' text-anchor='middle' fill='white' font-family='sans-serif'%3E👨‍🏫%3C/text%3E%3C/svg%3E";

    /**
     * Rend la page des programmes.
     */
    public static String render(String professeurId,
                                List<String> codesCoursProf,
                                List<String> classesProf,
                                List<String> periodesProf,
                                List<String> anneesProf,
                                boolean peutAjouter,
                                List<Programme> programmes,
                                String codeCoursSel,
                                String classeSel,
                                String periodeSel,
                                String anneeSel,
                                String vueActuelle,
                                String msgType,
                                String msg) {

        int total = programmes != null ? programmes.size() : 0;
        int termines = programmes != null ? (int) programmes.stream().filter(Programme::isEstTermine).count() : 0;
        int restants = total - termines;
        int pourcentage = total > 0 ? (termines * 100) / total : 0;

        List<Programme> programmesFiltres = programmes;
        if (programmes != null) {
            if ("termines".equals(vueActuelle)) {
                programmesFiltres = programmes.stream().filter(Programme::isEstTermine).collect(Collectors.toList());
            } else if ("restants".equals(vueActuelle)) {
                programmesFiltres = programmes.stream().filter(p -> !p.isEstTermine()).collect(Collectors.toList());
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>");
        sb.append("<html lang=\"fr\">");
        sb.append("<head>");
        sb.append("<meta charset=\"UTF-8\">");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        sb.append("<title>Programmes - Professeur</title>");
        sb.append("<link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css\">");
        sb.append("<link href=\"https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@300;400;500;600;700;800&display=swap\" rel=\"stylesheet\">");
        sb.append("<style>");
        sb.append(renderStyles());
        sb.append("</style>");
        sb.append("</head>");
        sb.append("<body>");
        sb.append("<div class=\"container\">");

        sb.append(renderHeader(professeurId));

        if (msg != null && msgType != null) {
            sb.append("<div class=\"message ").append(msgType).append("\">");
            sb.append("<i class=\"fas fa-").append(msgType.equals("success") ? "check-circle" : "exclamation-triangle").append("\"></i> ");
            sb.append(msg);
            sb.append("</div>");
        }

        sb.append(renderFilters(codesCoursProf, classesProf, periodesProf, anneesProf,
                                codeCoursSel, classeSel, periodeSel, anneeSel, peutAjouter));

        sb.append(renderStats(total, termines, restants, pourcentage));

        // ✅ Onglets centrés
        sb.append(renderTabs(vueActuelle));

        if ("graphique".equals(vueActuelle)) {
            sb.append(renderGraphique(total, termines, pourcentage));
        } else {
            sb.append(renderListe(programmesFiltres));
        }

        sb.append(renderModal(codesCoursProf, classesProf, periodesProf, anneesProf, peutAjouter));

        sb.append("<div class=\"footer\">");
        sb.append("© ").append(Year.now().getValue()).append(" M-TECH Academy - Tous droits réservés");
        sb.append("</div>");

        sb.append("</div>");
        sb.append("</body>");
        sb.append("</html>");

        return sb.toString();
    }

    public static String renderError(String message) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>");
        sb.append("<html lang=\"fr\">");
        sb.append("<head>");
        sb.append("<meta charset=\"UTF-8\">");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        sb.append("<title>Erreur - Programmes</title>");
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
        sb.append("<a href=\"/professeur/programmes\" class=\"btn\"><i class=\"fas fa-arrow-left\"></i> Retour</a>");
        sb.append("</div>");
        sb.append("</body>");
        sb.append("</html>");
        return sb.toString();
    }

    // ==================== STYLES ====================
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

        .filters { background:white; border-radius:12px; padding:20px; margin-bottom:20px; border:1px solid #e2e8f0; display:flex; gap:15px; flex-wrap:wrap; align-items:end; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.02); }
        .filters .form-group { flex:1; min-width:150px; }
        .filters label { display:block; font-weight:600; color:#475569; margin-bottom:4px; font-size:13px; }
        .filters select { width:100%; padding:9px 12px; border:1px solid #cbd5e1; border-radius:8px; font-size:14px; background:white; color:#334155; transition: all 0.2s ease; }
        .filters select:focus { border-color:#3b82f6; outline:none; box-shadow:0 0 0 3px rgba(59,130,246,0.15); }

        .filters .btn-filter { background:linear-gradient(135deg, #2563eb, #1d4ed8); color:white; border:none; padding:10px 22px; border-radius:8px; cursor:pointer; font-weight:600; display:inline-flex; align-items:center; gap:8px; box-shadow:0 4px 12px rgba(37,99,235,0.25); transition:all 0.25s ease; }
        .filters .btn-filter:hover { background:linear-gradient(135deg, #1d4ed8, #1e40af); transform: translateY(-1px); box-shadow:0 6px 15px rgba(37,99,235,0.35); }
        .filters .btn-filter:active { transform: translateY(0); }

        .filters .btn-add { background:linear-gradient(135deg, #22c55e, #16a34a); color:white; border:none; padding:10px 22px; border-radius:8px; cursor:pointer; font-weight:600; display:inline-flex; align-items:center; gap:8px; box-shadow:0 4px 12px rgba(34,197,94,0.25); transition:all 0.25s ease; }
        .filters .btn-add:hover { background:linear-gradient(135deg, #16a34a, #15803d); transform: translateY(-1px); box-shadow:0 6px 15px rgba(34,197,94,0.35); }
        .filters .btn-add:disabled { opacity:0.5; cursor:not-allowed; box-shadow:none; transform:none; }

        .stats-grid { display:grid; grid-template-columns:repeat(auto-fit,minmax(200px,1fr)); gap:20px; margin-bottom:20px; justify-content:center; justify-items:center; }
        .stat-card { background:white; border-radius:12px; padding:20px; text-align:center; border:1px solid #e2e8f0; width:100%; max-width:280px; box-shadow:0 4px 6px -1px rgba(0,0,0,0.02); transition: transform 0.2s ease; }
        .stat-card:hover { transform: translateY(-2px); }
        .stat-card .number { font-size:30px; font-weight:800; color:#1e40af; margin-bottom:4px; }
        .stat-card .label { font-size:13px; color:#64748b; font-weight:500; }

        /* ✅ Onglets centrés */
        .tabs { display:flex; justify-content:center; gap:5px; margin-bottom:20px; border-bottom:2px solid #e2e8f0; }
        .tab { padding:10px 24px; cursor:pointer; font-weight:600; color:#64748b; border-radius:8px 8px 0 0; transition:0.2s; text-decoration:none; }
        .tab:hover { color:#1e293b; background:#f8fafc; }
        .tab.active { color:#1e40af; border-bottom:3px solid #1e40af; }

        .table-wrapper { background:white; border-radius:12px; padding:20px; overflow-x:auto; border:1px solid #e2e8f0; }
        table { width:100%; border-collapse:collapse; font-size:14px; }
        th, td { padding:10px 12px; text-align:left; border-bottom:1px solid #e2e8f0; }
        th { background:#f8fafc; font-weight:600; color:#475569; }
        .btn { padding:6px 14px; border:none; border-radius:6px; cursor:pointer; font-size:13px; font-weight:600; text-decoration:none; display:inline-flex; align-items:center; gap:6px; transition:0.2s; }
        .btn-primary { background:#1e40af; color:white; }
        .btn-primary:hover { background:#1e3a8a; }
        .btn-warning { background:#f59e0b; color:white; }
        .btn-warning:hover { background:#d97706; }
        .btn-danger { background:#dc2626; color:white; }
        .btn-danger:hover { background:#b91c1c; }
        .btn-success { background:#16a34a; color:white; }
        .btn-success:hover { background:#15803d; }
        .badge { padding:3px 12px; border-radius:12px; font-size:12px; font-weight:600; }
        .badge-success { background:#d1fae5; color:#16a34a; }
        .badge-danger { background:#fee2e2; color:#dc2626; }
        .badge-warning { background:#fef3c7; color:#d97706; }

        .progress-container { background:white; border-radius:12px; padding:30px; border:1px solid #e2e8f0; text-align:center; }
        .progress-circle { width:200px; height:200px; border-radius:50%; display:inline-flex; align-items:center; justify-content:center; margin:20px auto; position:relative; }
        .progress-circle .inner { width:160px; height:160px; border-radius:50%; background:white; display:flex; flex-direction:column; align-items:center; justify-content:center; }
        .progress-circle .inner .percent { font-size:48px; font-weight:800; color:#1e293b; }
        .progress-circle .inner .label { font-size:14px; color:#64748b; }

        .empty-state { text-align:center; padding:40px; background:white; border-radius:12px; border:1px solid #e2e8f0; }
        .empty-state i { font-size:48px; color:#94a3b8; margin-bottom:12px; }
        .empty-state p { color:#64748b; font-size:16px; }

        .footer { margin-top:30px; text-align:center; color:#94a3b8; font-size:13px; }

        /* MODALE */
        .modal { display:none; position:fixed; top:0; left:0; width:100%; height:100%; background:rgba(0,0,0,0.5); justify-content:center; align-items:center; z-index:1000; }
        .modal.active { display:flex; }
        .modal-content { background:white; border-radius:16px; padding:30px; max-width:600px; width:90%; max-height:90vh; overflow-y:auto; }
        .modal-content h2 { margin-bottom:20px; color:#1e293b; }
        .form-group { margin-bottom:15px; }
        .form-group label { display:block; font-weight:600; color:#475569; margin-bottom:5px; }
        .form-group input, .form-group select, .form-group textarea { width:100%; padding:10px; border:1px solid #cbd5e1; border-radius:8px; font-size:14px; }
        .form-row { display:flex; gap:15px; }
        .form-row .form-group { flex:1; }
        .modal-actions { display:flex; justify-content:flex-end; gap:10px; margin-top:20px; }
        .disabled-field { opacity:0.6; pointer-events:none; }

        @media (max-width:768px) { .filters { flex-direction:column; } .form-row { flex-direction:column; } }
        """;
    }

    // ==================== COMPOSANTS ====================
    private static String renderHeader(String professeurId) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"header\">");
        sb.append("<img src=\"").append(DEFAULT_AVATAR).append("\" alt=\"Avatar\" class=\"header-avatar\">");
        sb.append("<h1><i class=\"fas fa-book-open\"></i> Mes Programmes</h1>");
        sb.append("<div class=\"header-nav\">");
        sb.append("<a href=\"/dashboard/professeur\"><i class=\"fas fa-home\"></i> Dashboard</a>");
        sb.append("<a href=\"/professeur/profil\"><i class=\"fas fa-user\"></i> Mon Profil</a>");
        sb.append("<a href=\"/professeur/cours\"><i class=\"fas fa-book\"></i> Mes Cours</a>");
        sb.append("<a href=\"/professeur/programmes\" class=\"active\"><i class=\"fas fa-book-open\"></i> Programmes</a>");
        sb.append("<a href=\"/logout\"><i class=\"fas fa-sign-out-alt\"></i> Déconnexion</a>");
        sb.append("</div>");
        sb.append("</div>");
        return sb.toString();
    }

    private static String renderFilters(List<String> codesCours, List<String> classes,
                                        List<String> periodes, List<String> annees,
                                        String codeCoursSel, String classeSel,
                                        String periodeSel, String anneeSel,
                                        boolean peutAjouter) {
        StringBuilder sb = new StringBuilder();
        sb.append("<form method=\"GET\" action=\"/professeur/programmes\" class=\"filters\">");
        sb.append("<div class=\"form-group\"><label>Année</label><select name=\"annee\">");
        sb.append("<option value=\"\">Toutes</option>");
        for (String a : annees) {
            String sel = a.equals(anneeSel) ? "selected" : "";
            sb.append("<option value=\"").append(echapperHtml(a)).append("\" ").append(sel).append(">").append(echapperHtml(a)).append("</option>");
        }
        sb.append("</select></div>");

        sb.append("<div class=\"form-group\"><label>Période</label><select name=\"periode\">");
        sb.append("<option value=\"\">Toutes</option>");
        for (String p : periodes) {
            String sel = p.equals(periodeSel) ? "selected" : "";
            sb.append("<option value=\"").append(echapperHtml(p)).append("\" ").append(sel).append(">").append(echapperHtml(p)).append("</option>");
        }
        sb.append("</select></div>");

        sb.append("<div class=\"form-group\"><label>Classe</label><select name=\"classe\">");
        sb.append("<option value=\"\">Toutes</option>");
        for (String c : classes) {
            String sel = c.equals(classeSel) ? "selected" : "";
            sb.append("<option value=\"").append(echapperHtml(c)).append("\" ").append(sel).append(">").append(echapperHtml(c)).append("</option>");
        }
        sb.append("</select></div>");

        sb.append("<div class=\"form-group\"><label>Cours</label><select name=\"codeCours\">");
        sb.append("<option value=\"\">Tous</option>");
        for (String c : codesCours) {
            String sel = c.equals(codeCoursSel) ? "selected" : "";
            sb.append("<option value=\"").append(echapperHtml(c)).append("\" ").append(sel).append(">").append(echapperHtml(c)).append("</option>");
        }
        sb.append("</select></div>");

        sb.append("<div><button type=\"submit\" class=\"btn-filter\"><i class=\"fas fa-filter\"></i> Filtrer</button></div>");
        sb.append("<div style=\"margin-left:auto;\">");
        sb.append("<button type=\"button\" class=\"btn-add\" onclick=\"openAddModal()\" ").append(peutAjouter ? "" : "disabled").append(">");
        sb.append("<i class=\"fas fa-plus\"></i> Nouveau chapitre");
        sb.append("</button>");
        sb.append("</div>");
        sb.append("</form>");
        return sb.toString();
    }

    private static String renderStats(int total, int termines, int restants, int pourcentage) {
        return "<div class=\"stats-grid\">" +
                "<div class=\"stat-card\"><div class=\"number\">" + total + "</div><div class=\"label\">Total chapitres</div></div>" +
                "<div class=\"stat-card\"><div class=\"number\">" + termines + "</div><div class=\"label\">✅ Terminés</div></div>" +
                "<div class=\"stat-card\"><div class=\"number\">" + restants + "</div><div class=\"label\">⏳ Restants</div></div>" +
                "<div class=\"stat-card\"><div class=\"number\">" + pourcentage + "%</div><div class=\"label\">📊 Achèvement</div></div>" +
                "</div>";
    }

    private static String renderTabs(String vueActuelle) {
        String[] vues = {"liste", "termines", "restants", "graphique"};
        String[] libelles = {"📋 Liste", "✅ Terminés", "⏳ Restants", "📊 Graphique"};
        StringBuilder sb = new StringBuilder("<div class=\"tabs\">");
        for (int i = 0; i < vues.length; i++) {
            String active = vues[i].equals(vueActuelle) || (vueActuelle.isEmpty() && vues[i].equals("liste")) ? "active" : "";
            sb.append("<a href=\"/professeur/programmes?vue=").append(vues[i]).append("\" class=\"tab ").append(active).append("\">").append(libelles[i]).append("</a>");
        }
        sb.append("</div>");
        return sb.toString();
    }

    private static String renderListe(List<Programme> programmes) {
        if (programmes == null || programmes.isEmpty()) {
            return "<div class=\"empty-state\"><i class=\"fas fa-book-open\"></i><p>Aucun chapitre trouvé.</p></div>";
        }

        StringBuilder sb = new StringBuilder("<div class=\"table-wrapper\"><table>");
        sb.append("<thead><tr><th>Titre</th><th>Cours</th><th>Classe</th><th>Période</th><th>Début</th><th>Fin</th><th>Cotation</th><th>Statut</th><th>Actions</th></tr></thead><tbody>");
        for (Programme p : programmes) {
            String statut = p.isEstTermine() ? "<span class=\"badge badge-success\">✅ Terminé</span>" : "<span class=\"badge badge-warning\">⏳ En cours</span>";
            sb.append("<tr>");
            sb.append("<td><strong>").append(echapperHtml(p.getTitreChapitre())).append("</strong>");
            if (p.getDescription() != null && !p.getDescription().isBlank()) {
                sb.append("<br><small style=\"color:#64748b;\">").append(echapperHtml(p.getDescription())).append("</small>");
            }
            sb.append("</td>");
            sb.append("<td>").append(echapperHtml(p.getCodeCours())).append("</td>");
            sb.append("<td>").append(echapperHtml(p.getClasse())).append("</td>");
            sb.append("<td>").append(echapperHtml(p.getPeriode())).append("</td>");
            sb.append("<td>").append(p.getDateDebut() != null ? p.getDateDebut().toString() : "-").append("</td>");
            sb.append("<td>").append(p.getDateFin() != null ? p.getDateFin().toString() : "-").append("</td>");
            sb.append("<td>").append(p.getCotation()).append("</td>");
            sb.append("<td>").append(statut).append("</td>");
            sb.append("<td>");
            sb.append("<button class=\"btn btn-primary\" onclick='openEditModal(")
              .append(p.getId()).append(",\"")
              .append(echapperHtml(p.getTitreChapitre())).append("\",\"")
              .append(echapperHtml(p.getDescription())).append("\",\"")
              .append(echapperHtml(p.getCodeCours())).append("\",\"")
              .append(echapperHtml(p.getClasse())).append("\",\"")
              .append(echapperHtml(p.getPeriode())).append("\",\"")
              .append(echapperHtml(p.getAnneeAcademique())).append("\",\"")
              .append(p.getDateDebut() != null ? p.getDateDebut().toString() : "").append("\",\"")
              .append(p.getDateFin() != null ? p.getDateFin().toString() : "").append("\",")
              .append(p.isEstTermine()).append(",")
              .append(p.getCotation()).append(")'><i class=\"fas fa-edit\"></i></button>");
            sb.append("<button class=\"btn btn-danger\" onclick='confirmDelete(").append(p.getId()).append(")'><i class=\"fas fa-trash\"></i></button>");
            sb.append("</td></tr>");
        }
        sb.append("</tbody></table></div>");
        return sb.toString();
    }

    private static String renderGraphique(int total, int termines, int pourcentage) {
        return "<div class=\"progress-container\">" +
                "<h3>Avancement du programme</h3>" +
                "<div class=\"progress-circle\" style=\"background:conic-gradient(#1e40af 0% " + pourcentage + "%, #e2e8f0 " + pourcentage + "% 100%);\">" +
                "<div class=\"inner\"><div class=\"percent\">" + pourcentage + "%</div><div class=\"label\">" + termines + "/" + total + " chapitres</div></div>" +
                "</div>" +
                "<p style=\"color:#64748b;\">" + (total - termines) + " chapitre(s) restant(s)</p>" +
                "</div>";
    }

    private static String renderModal(List<String> codesCours, List<String> classes,
                                      List<String> periodes, List<String> annees,
                                      boolean peutAjouter) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
        <div id="modalForm" class="modal">
            <div class="modal-content">
                <h2 id="modalTitle">Ajouter un chapitre</h2>
                <form method="POST" action="/professeur/programmes" id="programmeForm">
                    <input type="hidden" name="action" id="formAction" value="ajouter">
                    <input type="hidden" name="id" id="formId" value="">
        """);

        sb.append("<div class=\"form-group\"><label>Code cours *</label><select name=\"codeCours\" id=\"codeCours\" required>");
        sb.append("<option value=\"\">-- Sélectionner --</option>");
        for (String c : codesCours) {
            sb.append("<option value=\"").append(echapperHtml(c)).append("\">").append(echapperHtml(c)).append("</option>");
        }
        sb.append("</select></div>");

        sb.append("<div class=\"form-group\"><label>Classe *</label><select name=\"classe\" id=\"classe\" required>");
        sb.append("<option value=\"\">-- Sélectionner --</option>");
        for (String c : classes) {
            sb.append("<option value=\"").append(echapperHtml(c)).append("\">").append(echapperHtml(c)).append("</option>");
        }
        sb.append("</select></div>");

        sb.append("<div class=\"form-group\"><label>Période *</label><select name=\"periode\" id=\"periode\" required>");
        sb.append("<option value=\"\">-- Sélectionner --</option>");
        for (String p : periodes) {
            sb.append("<option value=\"").append(echapperHtml(p)).append("\">").append(echapperHtml(p)).append("</option>");
        }
        sb.append("</select></div>");

        sb.append("<div class=\"form-group\"><label>Année académique *</label><select name=\"annee_academique\" id=\"annee_academique\" required>");
        sb.append("<option value=\"\">-- Sélectionner --</option>");
        for (String a : annees) {
            sb.append("<option value=\"").append(echapperHtml(a)).append("\">").append(echapperHtml(a)).append("</option>");
        }
        sb.append("</select></div>");

        sb.append("<div class=\"form-group\"><label>Titre du chapitre *</label><input type=\"text\" name=\"titre\" id=\"titre\" required></div>");
        sb.append("<div class=\"form-group\"><label>Description</label><textarea name=\"description\" id=\"description\" rows=\"3\"></textarea></div>");

        sb.append("<div class=\"form-row\">");
        sb.append("<div class=\"form-group\"><label>Date début</label><input type=\"date\" name=\"date_debut\" id=\"date_debut\"></div>");
        sb.append("<div class=\"form-group\"><label>Date fin</label><input type=\"date\" name=\"date_fin\" id=\"date_fin\"></div>");
        sb.append("</div>");

        sb.append("<div class=\"form-row\">");
        sb.append("<div class=\"form-group\"><label>Cotation (points)</label><input type=\"number\" name=\"cotation\" id=\"cotation\" value=\"0\" min=\"0\"></div>");
        sb.append("<div class=\"form-group\"><label><input type=\"checkbox\" name=\"est_termine\" id=\"est_termine\"> Terminé</label></div>");
        sb.append("</div>");

        sb.append("<div class=\"modal-actions\">");
        sb.append("<button type=\"button\" class=\"btn btn-danger\" onclick=\"closeModal()\">Annuler</button>");
        sb.append("<button type=\"submit\" class=\"btn btn-success\"><i class=\"fas fa-save\"></i> Enregistrer</button>");
        sb.append("</div></form></div></div>");

        sb.append("""
        <div id="modalDelete" class="modal">
            <div class="modal-content">
                <h2>Confirmer la suppression</h2>
                <p>Êtes-vous sûr de vouloir supprimer ce chapitre ?</p>
                <form method="POST" action="/professeur/programmes">
                    <input type="hidden" name="action" value="delete">
                    <input type="hidden" name="id" id="deleteId" value="">
                    <div class="modal-actions">
                        <button type="button" class="btn btn-primary" onclick="closeDeleteModal()">Annuler</button>
                        <button type="submit" class="btn btn-danger"><i class="fas fa-trash"></i> Supprimer</button>
                    </div>
                </form>
            </div>
        </div>
        """);

        sb.append("""
        <script>
            function openAddModal() {
                document.getElementById('modalTitle').textContent = 'Ajouter un chapitre';
                document.getElementById('formAction').value = 'ajouter';
                document.getElementById('formId').value = '';
                document.getElementById('codeCours').value = '';
                document.getElementById('classe').value = '';
                document.getElementById('periode').value = '';
                document.getElementById('annee_academique').value = '';
                document.getElementById('titre').value = '';
                document.getElementById('description').value = '';
                document.getElementById('date_debut').value = '';
                document.getElementById('date_fin').value = '';
                document.getElementById('cotation').value = '0';
                document.getElementById('est_termine').checked = false;
                document.getElementById('modalForm').classList.add('active');
            }

            function openEditModal(id, titre, description, codeCours, classe, periode, annee, dateDebut, dateFin, estTermine, cotation) {
                document.getElementById('modalTitle').textContent = 'Modifier le chapitre';
                document.getElementById('formAction').value = 'modifier';
                document.getElementById('formId').value = id;
                document.getElementById('codeCours').value = codeCours;
                document.getElementById('classe').value = classe;
                document.getElementById('periode').value = periode;
                document.getElementById('annee_academique').value = annee;
                document.getElementById('titre').value = titre;
                document.getElementById('description').value = description;
                document.getElementById('date_debut').value = dateDebut;
                document.getElementById('date_fin').value = dateFin;
                document.getElementById('cotation').value = cotation;
                document.getElementById('est_termine').checked = estTermine;
                document.getElementById('modalForm').classList.add('active');
            }

            function closeModal() { document.getElementById('modalForm').classList.remove('active'); }
            function confirmDelete(id) { document.getElementById('deleteId').value = id; document.getElementById('modalDelete').classList.add('active'); }
            function closeDeleteModal() { document.getElementById('modalDelete').classList.remove('active'); }

            document.getElementById('modalForm').addEventListener('click', function(e) { if (e.target === this) closeModal(); });
            document.getElementById('modalDelete').addEventListener('click', function(e) { if (e.target === this) closeDeleteModal(); });
        </script>
        """);

        return sb.toString();
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