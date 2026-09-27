import java.text.SimpleDateFormat;
import java.time.Year;
import java.util.List;
import java.util.Locale;

public class UIExamenAdmin {

    // ✅ SimpleDateFormat n'est PAS thread-safe → instance locale par appel
    private static final String DATE_PATTERN = "dd/MM/yyyy";

    // ============================================================
    // RENDER PRINCIPAL
    // ============================================================
    public static String render(String institutionId,
                                List<Examen> examens,
                                List<String> codesCours,
                                List<String> classes,
                                List<String> periodes,
                                List<String> annees,
                                List<String> statuts,
                                String codeCoursSel,
                                String classeSel,
                                String periodeSel,
                                String anneeSel,
                                String statutSel,
                                String msgType,
                                String msg,
                                boolean canCreate,
                                boolean canUpdate,
                                boolean canDelete) {

        SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_PATTERN, Locale.FRENCH);

        StringBuilder sb = new StringBuilder(32768);

        // ============================================================
        // HEAD
        // ============================================================
        sb.append("""
            <!DOCTYPE html>
            <html lang="fr">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Examens — Administration</title>
                <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
                <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700;800&display=swap" rel="stylesheet">
            """);

        sb.append("<style>").append(renderStyles()).append("</style>");
        sb.append("</head><body>");
        sb.append("<div class=\"container\">");

        // ============================================================
        // NAVIGATION
        // ============================================================
        sb.append("""
            <div class="nav-menu">
                <a href="/admin/dashboard" class="nav-btn"><i class="fas fa-home"></i> Dashboard</a>
                <a href="/admin/notes" class="nav-btn"><i class="fas fa-file-pen"></i> Notes</a>
                <a href="/admin/classes" class="nav-btn"><i class="fas fa-users-between-lines"></i> Classes</a>
                <a href="/admin/parametres" class="nav-btn"><i class="fas fa-gear"></i> Paramètres</a>
                <a href="/admin/examens" class="nav-btn active"><i class="fas fa-pencil-alt"></i> Examens</a>
            </div>
            """);

        // ============================================================
        // MESSAGE
        // ============================================================
        if (msg != null && msgType != null && !msg.isBlank()) {
            String icon = "success".equals(msgType) ? "check-circle" : "exclamation-triangle";
            sb.append("<div class=\"message ").append(escapeHtml(msgType)).append("\">")
              .append("<i class=\"fas fa-").append(icon).append("\"></i> ")
              .append(escapeHtml(msg))
              .append("</div>");
        }

        // ============================================================
        // FILTRES
        // ============================================================
        sb.append("<div class=\"card filters-card\">");
        sb.append("<div class=\"card-title\"><i class=\"fas fa-filter\"></i> Filtres</div>");

        sb.append("<form method=\"GET\" action=\"/admin/examens\" class=\"filter-form\" id=\"filterForm\">");

        // ✅ Champ caché pour conserver l'institution
        if (institutionId != null && !institutionId.isBlank()) {
            sb.append("<input type=\"hidden\" name=\"institutionId\" value=\"")
              .append(escapeHtml(institutionId)).append("\">");
        }

        sb.append("<div class=\"form-row\">");

        // Année
        sb.append(renderSelect("annee", "Année", annees, anneeSel, "Toutes"));

        // Période
        sb.append(renderSelect("periode", "Période", periodes, periodeSel, "Toutes"));

        // Classe
        sb.append(renderSelect("classe", "Classe", classes, classeSel, "Toutes"));

        // Matière (auto-submit)
        sb.append(renderSelectAuto("codeCours", "Matière", codesCours, codeCoursSel, "Toutes"));

        // Statut
        sb.append(renderSelect("statut", "Statut", statuts, statutSel, "Tous"));

        sb.append("</div>");  // /form-row

        sb.append("""
            <div class="filter-actions">
                <button type="submit" class="btn-filter"><i class="fas fa-search"></i> Filtrer</button>
                <a href="/admin/examens" class="btn-filter btn-reset"><i class="fas fa-undo"></i> Réinitialiser</a>
            </div>
            """);

        sb.append("</form></div>");

        // ============================================================
        // LISTE DES EXAMENS
        // ============================================================
        sb.append("<div class=\"card\">");
        sb.append("<div class=\"card-title centered\"><i class=\"fas fa-list\"></i> Liste des Examens</div>");

        // Bouton d'ajout conditionnel
        if (canCreate) {
            sb.append("""
                <div style="text-align:right; margin-bottom:12px;">
                    <a href="/admin/examens?action=add" class="btn-filter btn-add">
                        <i class="fas fa-plus"></i> Ajouter un examen
                    </a>
                </div>
                """);
        }

        if (examens == null || examens.isEmpty()) {
            sb.append("""
                <div class="empty-state">
                    <i class="fas fa-file-alt"></i>
                    <p>Aucun examen trouvé.</p>
                </div>
                """);
        } else {
            sb.append("<div class=\"table-wrapper\">");
            sb.append("<table>");
            sb.append("<thead><tr>");
            sb.append("<th>Code</th><th>Matière</th><th>Classe</th><th>Période</th><th>Année</th>");
            sb.append("<th>Titre</th><th>Date</th><th>Heure</th><th>Statut</th><th>Fichier</th>");
            if (canUpdate || canDelete) sb.append("<th>Actions</th>");
            sb.append("</tr></thead>");
            sb.append("<tbody>");

            for (Examen e : examens) {
                sb.append("<tr>");

                sb.append("<td>").append(escapeHtml(e.getCodeCours())).append("</td>");
                sb.append("<td>").append(escapeHtml(e.getNomMatiere())).append("</td>");
                sb.append("<td>").append(escapeHtml(e.getClasse())).append("</td>");
                sb.append("<td>").append(escapeHtml(e.getPeriode())).append("</td>");
                sb.append("<td>").append(escapeHtml(e.getAnneeAcademique())).append("</td>");

                // Titre + description
                sb.append("<td><strong>").append(escapeHtml(e.getTitre())).append("</strong>");
                if (e.getDescription() != null && !e.getDescription().isBlank()) {
                    sb.append("<br><small style=\"color:#64748b;\">")
                      .append(escapeHtml(e.getDescription()))
                      .append("</small>");
                }
                sb.append("</td>");

                // Date
                String dateStr = e.getDateExamen() != null
                        ? dateFormat.format(e.getDateExamen())
                        : "-";
                sb.append("<td>").append(escapeHtml(dateStr)).append("</td>");

                // Heure
                sb.append("<td>").append(escapeHtml(formatHeure(e))).append("</td>");

                // Statut
                String statut = e.getStatut();
                sb.append("<td><span class=\"badge ").append(getStatutClass(statut))
                  .append("\">").append(escapeHtml(statut)).append("</span></td>");

                // Fichier
                sb.append("<td>");
                if (e.getFichierChemin() != null && !e.getFichierChemin().isBlank()) {
                    sb.append("<a href=\"/admin/examens/download/").append(escapeHtml(e.getId()))
                      .append("\" class=\"btn-download\" title=\"")
                      .append(escapeHtml(e.getFichierNom() != null ? e.getFichierNom() : "Télécharger"))
                      .append("\"><i class=\"fas fa-download\"></i></a>");
                } else {
                    sb.append("<span style=\"color:#94a3b8;\">Aucun</span>");
                }
                sb.append("</td>");

                // Actions
                if (canUpdate || canDelete) {
                    sb.append("<td><div class=\"actions-cell\">");

                    if (canUpdate) {
                        sb.append("<a href=\"/admin/examens?action=edit&id=")
                          .append(escapeHtml(e.getId()))
                          .append("\" class=\"btn-action btn-edit\" title=\"Modifier\">")
                          .append("<i class=\"fas fa-pen\"></i></a>");
                    }

                    if (canDelete) {
                        sb.append("<a href=\"/admin/examens?action=delete&id=")
                          .append(escapeHtml(e.getId()))
                          .append("\" class=\"btn-action btn-delete\" title=\"Supprimer\" ")
                          .append("onclick=\"return confirm('Supprimer cet examen ?');\">")
                          .append("<i class=\"fas fa-trash\"></i></a>");
                    }

                    sb.append("</div></td>");
                }

                sb.append("</tr>");
            }

            sb.append("</tbody></table></div>");
        }

        sb.append("</div>");

        // ============================================================
        // FOOTER
        // ============================================================
        sb.append("<div class=\"footer\">© ").append(Year.now().getValue())
          .append(" M-TECH Academy — Tous droits réservés</div>");

        sb.append("</div></body></html>");
        return sb.toString();
    }

    // ============================================================
    // HELPERS DE RENDU
    // ============================================================

    /** ✅ Rendu d'un <select> générique. */
    private static String renderSelect(String name, String label,
                                        List<String> options, String selected,
                                        String placeholder) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"form-group\">");
        sb.append("<label for=\"").append(escapeHtml(name)).append("\">")
          .append(escapeHtml(label)).append("</label>");
        sb.append("<select name=\"").append(escapeHtml(name))
          .append("\" id=\"").append(escapeHtml(name)).append("\">");
        sb.append("<option value=\"\">").append(escapeHtml(placeholder)).append("</option>");

        if (options != null) {
            for (String opt : options) {
                if (opt == null || opt.isBlank()) continue;
                String sel = opt.equals(selected) ? "selected" : "";
                sb.append("<option value=\"").append(escapeHtml(opt))
                  .append("\" ").append(sel).append(">")
                  .append(escapeHtml(opt)).append("</option>");
            }
        }

        sb.append("</select></div>");
        return sb.toString();
    }

    /** ✅ Rendu d'un <select> avec auto-submit (pour la matière). */
    private static String renderSelectAuto(String name, String label,
                                             List<String> options, String selected,
                                             String placeholder) {
        String base = renderSelect(name, label, options, selected, placeholder);
        // Injecter onchange dans le <select>
        return base.replace(
                "id=\"" + escapeHtml(name) + "\">",
                "id=\"" + escapeHtml(name) + "\" "
                + "onchange=\"document.getElementById('filterForm').submit();\">"
        );
    }

    /** ✅ Formate les heures "HH:MM - HH:MM" de façon null-safe. */
    private static String formatHeure(Examen e) {
        StringBuilder sb = new StringBuilder();
        if (e.getHeureDebut() != null) {
            String s = e.getHeureDebut().toString();
            sb.append(s.length() >= 5 ? s.substring(0, 5) : s);
        }
        if (e.getHeureFin() != null) {
            String s = e.getHeureFin().toString();
            if (sb.length() > 0) sb.append(" - ");
            sb.append(s.length() >= 5 ? s.substring(0, 5) : s);
        }
        return sb.toString();
    }

    /** ✅ Détermine la classe CSS du badge selon le statut. */
    private static String getStatutClass(String statut) {
        if (statut == null) return "badge-secondary";
        return switch (statut.toUpperCase()) {
            case "PREVU" -> "badge-primary";
            case "EN_COURS" -> "badge-warning";
            case "TERMINE" -> "badge-success";
            case "ANNULE" -> "badge-danger";
            default -> "badge-secondary";
        };
    }

    // ============================================================
    // PAGE ERREUR
    // ============================================================
    public static String renderError(String message) {
        return """
            <!DOCTYPE html>
            <html lang="fr">
            <head>
                <meta charset="UTF-8">
                <title>Erreur</title>
                <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
                <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;600&display=swap" rel="stylesheet">
                <style>
                    *{margin:0;padding:0;box-sizing:border-box;font-family:'Inter',sans-serif;}
                    body{background:#FAFBFB;display:flex;justify-content:center;align-items:center;min-height:100vh;padding:20px;}
                    .error-container{background:white;padding:40px;border-radius:16px;text-align:center;max-width:500px;border:1px solid #DCE1E6;box-shadow:0 4px 12px rgba(0,0,0,0.05);}
                    .error-container i{font-size:54px;color:#C62828;margin-bottom:20px;}
                    .error-container h1{color:#1E293B;margin-bottom:10px;font-size:22px;}
                    .error-container p{color:#64748B;font-size:14px;}
                    .error-container .btn{display:inline-flex;align-items:center;gap:8px;margin-top:20px;padding:10px 24px;background:#3B82F6;color:white;text-decoration:none;border-radius:8px;font-weight:600;}
                    .error-container .btn:hover{background:#2563EB;}
                </style>
            </head>
            <body>
                <div class="error-container">
                    <i class="fas fa-exclamation-triangle"></i>
                    <h1>Erreur</h1>
                    <p>""" + escapeHtml(message) + """
                    </p>
                    <a href="/admin/examens" class="btn">
                        <i class="fas fa-arrow-left"></i> Retour
                    </a>
                </div>
            </body>
            </html>
            """;
    }

    // ============================================================
    // STYLES
    // ============================================================
    private static String renderStyles() {
        return """
        * { margin: 0; padding: 0; box-sizing: border-box; }
        html, body { width: 100%; min-height: 100vh; background: #FAFBFB; font-family: 'Inter', sans-serif; }
        body { padding: 20px; }
        .container { width: 100%; max-width: 100%; margin: 0 auto; }

        /* ---------- Navigation ---------- */
        .nav-menu { display: flex; gap: 8px; background: white; border-radius: 12px; padding: 8px; margin-bottom: 20px; border: 1px solid #DCE1E6; flex-wrap: wrap; width: 100%; }
        .nav-btn { flex: 1 1 180px; text-align: center; padding: 12px 20px; background: none; border: none; cursor: pointer; font-weight: 600; border-radius: 8px; transition: all 0.2s ease-in-out; text-decoration: none; color: #333; font-size: 14px; }
        .nav-btn.active { background: #3B82F6; color: white; }
        .nav-btn:hover:not(.active) { background: #EFF6FF; color: #1D4ED8; }

        /* ---------- Messages ---------- */
        .message { padding: 14px 18px; border-radius: 12px; margin-bottom: 20px; font-weight: 600; font-size: 14px; }
        .message.success { background: #DCFCE7; color: #166534; border: 1px solid #86EFAC; }
        .message.error { background: #FEE2E2; color: #991B1B; border: 1px solid #FCA5A5; }

        /* ---------- Cards ---------- */
        .card { background: white; border-radius: 16px; padding: 24px; margin-bottom: 20px; border: 1px solid #DCE1E6; box-shadow: 0 2px 8px rgba(0,0,0,0.06); width: 100%; }
        .card-title { font-size: 16px; font-weight: 700; color: #1E40AF; margin-bottom: 16px; border-bottom: 2px solid #DBEAFE; padding-bottom: 8px; display: inline-block; }
        .card-title.centered { display: block; text-align: center; border-bottom: 2px solid #E2E8F0; padding-bottom: 12px; }
        .filters-card .card-title { display: block; text-align: center; border-bottom: 2px solid #E2E8F0; padding-bottom: 12px; }

        /* ---------- Filtres ---------- */
        .filter-form .form-row { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 16px; margin-bottom: 16px; width: 100%; }
        .filter-form .form-group { display: flex; flex-direction: column; gap: 6px; }
        .filter-form label { font-weight: 600; color: #334155; font-size: 13px; }
        .filter-form select { width: 100%; padding: 10px 14px; border: 1px solid #DCE1E6; border-radius: 8px; font-size: 14px; background: white; font-family: 'Inter', sans-serif; transition: all 0.2s; }
        .filter-form select:focus { border-color: #3B82F6; outline: none; box-shadow: 0 0 0 3px rgba(59,130,246,0.1); }
        .filter-actions { display: flex; justify-content: center; gap: 10px; margin-top: 10px; }

        .btn-filter { background: #3B82F6; color: white; border: none; padding: 10px 24px; border-radius: 8px; cursor: pointer; font-weight: 600; font-size: 14px; transition: all 0.2s; display: inline-flex; align-items: center; gap: 8px; text-decoration: none; }
        .btn-filter:hover { background: #2563EB; }
        .btn-filter.btn-reset { background: #94A3B8; }
        .btn-filter.btn-reset:hover { background: #64748B; }
        .btn-filter.btn-add { background: #10B981; }
        .btn-filter.btn-add:hover { background: #059669; }

        /* ---------- Table ---------- */
        .table-wrapper { width: 100%; overflow-x: auto; border-radius: 12px; border: 1px solid #DCE1E6; }
        table { width: 100%; border-collapse: collapse; font-size: 14px; }
        th, td { padding: 12px 16px; text-align: left; border-bottom: 1px solid #E2E8F0; vertical-align: middle; }
        th { background: #EFF6FF; font-weight: 600; color: #1E293B; font-size: 13px; }
        tr:hover { background: #FAFBFB; }
        tr:last-child td { border-bottom: none; }

        /* ---------- Badges ---------- */
        .badge { padding: 4px 10px; border-radius: 12px; font-size: 12px; font-weight: 600; display: inline-block; }
        .badge-primary { background: #DBEAFE; color: #1E40AF; }
        .badge-warning { background: #FEF3C7; color: #92400E; }
        .badge-success { background: #DCFCE7; color: #166534; }
        .badge-danger { background: #FEE2E2; color: #991B1B; }
        .badge-secondary { background: #F1F5F9; color: #475569; }

        /* ---------- Boutons d'action ---------- */
        .btn-download { background: #3B82F6; color: white; padding: 6px 12px; border-radius: 6px; text-decoration: none; display: inline-flex; align-items: center; gap: 6px; font-size: 13px; transition: all 0.2s; }
        .btn-download:hover { background: #2563EB; }

        .actions-cell { display: flex; gap: 6px; }
        .btn-action { padding: 6px 10px; border-radius: 6px; text-decoration: none; display: inline-flex; align-items: center; justify-content: center; font-size: 13px; transition: all 0.2s; width: 32px; height: 32px; }
        .btn-edit { background: #FEF3C7; color: #92400E; }
        .btn-edit:hover { background: #FCD34D; }
        .btn-delete { background: #FEE2E2; color: #991B1B; }
        .btn-delete:hover { background: #FCA5A5; }

        /* ---------- États ---------- */
        .empty-state { text-align: center; padding: 40px; }
        .empty-state i { font-size: 42px; color: #CBD5E1; margin-bottom: 12px; }
        .empty-state p { color: #64748B; font-size: 15px; }

        /* ---------- Footer ---------- */
        .footer { margin-top: 30px; text-align: center; color: #64748B; font-size: 13px; }

        /* ---------- Responsive ---------- */
        @media (max-width: 768px) {
            .filter-form .form-row { grid-template-columns: 1fr; }
            .nav-btn { flex: 1 1 100%; }
        }
        """;
    }

    // ============================================================
    // ÉCHAPPEMENT HTML
    // ============================================================
    private static String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }
}