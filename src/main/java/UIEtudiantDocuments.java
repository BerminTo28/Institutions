import java.text.SimpleDateFormat;
import java.time.Year;
import java.util.List;
import java.util.Locale;

public class UIEtudiantDocuments {

    /** ✅ Format thread-safe : instancié localement. */
    private static final String DATE_PATTERN = "dd/MM/yyyy HH:mm";

    // ============================================================
    // RENDER PRINCIPAL
    // ============================================================
    public static String render(String etudiantId,
                                String institutionId,
                                String nomComplet,
                                String classeEtudiant,
                                List<AnneeAcademique> annees,
                                List<Periode> periodes,
                                List<String> matieresDisponibles,
                                List<DocumentProfesseur> documents,
                                String matiereSel,
                                String periodeSel,
                                String anneeSel,
                                String msgType,
                                String msg) {

        // ✅ Normalisation null-safe
        if (matiereSel == null) matiereSel = "";
        if (periodeSel == null) periodeSel = "";
        if (anneeSel == null) anneeSel = "";
        if (classeEtudiant == null || classeEtudiant.isBlank()) classeEtudiant = "Non définie";
        if (nomComplet == null || nomComplet.isBlank()) nomComplet = "Étudiant";
        if (etudiantId == null) etudiantId = "";
        if (institutionId == null) institutionId = "";

        StringBuilder sb = new StringBuilder(16384);

        // HEAD
        sb.append("""
            <!DOCTYPE html>
            <html lang="fr">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Mes Documents — Étudiant</title>
                <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
                <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@300;400;500;600;700;800&display=swap" rel="stylesheet">
            """);

        sb.append("<style>").append(renderStyles()).append("</style>");
        sb.append("</head><body>");
        sb.append("<div class=\"container\">");

        // Header
        sb.append(renderHeader(etudiantId, nomComplet, classeEtudiant, institutionId));

        // Message flash
        if (msg != null && msgType != null && !msg.isBlank()) {
            String icon = "success".equals(msgType) ? "check-circle" : "exclamation-triangle";
            sb.append("<div class=\"message ").append(echapperHtml(msgType)).append("\">")
              .append("<i class=\"fas fa-").append(icon).append("\"></i> ")
              .append(echapperHtml(msg))
              .append("</div>");
        }

        // Filtres
        sb.append(renderFilters(matieresDisponibles, annees, periodes,
                matiereSel, periodeSel, anneeSel));

        // Tableau
        sb.append(renderTable(documents));

        // Footer
        sb.append("<div class=\"footer\">© ")
          .append(Year.now().getValue())
          .append(" M-TECH Academy — Tous droits réservés</div>");

        sb.append("</div></body></html>");
        return sb.toString();
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
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Erreur</title>
                <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
                <style>
                    *{margin:0;padding:0;box-sizing:border-box;font-family:'Plus Jakarta Sans',sans-serif;}
                    body{background:#f4f6f9;display:flex;justify-content:center;align-items:center;min-height:100vh;padding:20px;}
                    .error{background:#fff;padding:40px;border-radius:16px;text-align:center;max-width:500px;box-shadow:0 4px 12px rgba(0,0,0,0.05);}
                    .error i{font-size:64px;color:#dc2626;margin-bottom:20px;}
                    .error h1{color:#1e293b;margin-bottom:10px;font-size:22px;}
                    .error p{color:#64748b;margin-bottom:24px;font-size:14px;}
                    .error .btn{display:inline-block;padding:10px 24px;background:#1e40af;color:#fff;text-decoration:none;border-radius:8px;font-weight:600;transition:0.2s;}
                    .error .btn:hover{background:#1e3a8a;}
                </style>
            </head>
            <body>
                <div class="error">
                    <i class="fas fa-exclamation-triangle"></i>
                    <h1>Erreur</h1>
                    <p>""" + echapperHtml(message != null ? message : "Une erreur inattendue est survenue.") + """
                    </p>
                    <a href="/etudiant/dashboard" class="btn">
                        <i class="fas fa-arrow-left"></i> Retour au dashboard
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
            * { margin:0; padding:0; box-sizing:border-box; }
            html, body { min-height:100vh; font-family:'Plus Jakarta Sans',sans-serif; background:#f0f4f8; }
            body { padding:20px; }
            .container { width:100%; max-width:1400px; margin:0 auto; display:flex; flex-direction:column; gap:20px; }

            /* ---------- Header ---------- */
            .header { background:linear-gradient(135deg,#1e293b,#0f172a); color:white; padding:24px 30px; border-radius:16px; }
            .header h1 { font-size:24px; font-weight:700; display:flex; align-items:center; gap:10px; justify-content:center; }
            .header h1 i { color:#3b82f6; }
            .header-nav { display:flex; justify-content:center; gap:12px; margin-top:12px; flex-wrap:wrap; }
            .header-nav a { color:#94a3b8; text-decoration:none; font-size:13px; padding:6px 14px; border-radius:20px; transition:0.2s; display:inline-flex; align-items:center; gap:6px; }
            .header-nav a:hover { color:white; background:rgba(255,255,255,0.1); }
            .header-nav a.active { color:white; background:rgba(59,130,246,0.3); font-weight:600; }
            .header-info { font-size:12px; color:#94a3b8; margin-top:12px; text-align:center; }

            /* ---------- Message ---------- */
            .message { padding:14px 18px; border-radius:10px; display:flex; align-items:center; gap:10px; font-weight:500; }
            .message.success { background:#d1fae5; color:#16a34a; border-left:4px solid #16a34a; }
            .message.error { background:#fee2e2; color:#dc2626; border-left:4px solid #dc2626; }

            /* ---------- Filtres ---------- */
            .filters { background:white; border-radius:12px; padding:20px; display:flex; gap:15px; flex-wrap:wrap; align-items:flex-end; border:1px solid #e2e8f0; }
            .filters .form-group { flex:1; min-width:160px; }
            .filters label { display:block; font-weight:600; color:#475569; margin-bottom:6px; font-size:13px; }
            .filters select { width:100%; padding:10px 12px; border:1px solid #cbd5e1; border-radius:8px; background:white; font-family:inherit; font-size:14px; transition:0.15s; }
            .filters select:focus { border-color:#1e40af; outline:none; box-shadow:0 0 0 3px rgba(30,64,175,0.1); }
            .filters .actions { display:flex; gap:8px; }
            .btn { padding:10px 20px; border:none; border-radius:8px; font-weight:600; font-size:14px; cursor:pointer; transition:0.15s; display:inline-flex; align-items:center; gap:8px; text-decoration:none; }
            .btn-filter { background:#1e40af; color:white; }
            .btn-filter:hover { background:#1e3a8a; }
            .btn-reset { background:#e2e8f0; color:#475569; }
            .btn-reset:hover { background:#cbd5e1; }

            /* ---------- Table ---------- */
            .table-wrapper { background:white; border-radius:12px; padding:20px; overflow-x:auto; border:1px solid #e2e8f0; }
            table { width:100%; border-collapse:collapse; font-size:14px; }
            th, td { padding:12px; text-align:left; border-bottom:1px solid #e2e8f0; }
            th { background:#f8fafc; font-weight:600; color:#475569; font-size:13px; text-transform:uppercase; letter-spacing:0.03em; }
            tbody tr:hover { background:#f8fafc; }
            tbody tr:last-child td { border-bottom:none; }

            /* ---------- Badges ---------- */
            .badge { padding:3px 12px; border-radius:12px; font-size:12px; font-weight:600; display:inline-block; }
            .badge-primary { background:#dbeafe; color:#2563eb; }

            /* ---------- Actions ---------- */
            .action-link { color:#2563eb; text-decoration:none; display:inline-flex; align-items:center; gap:6px; font-weight:500; padding:6px 10px; border-radius:6px; transition:0.15s; }
            .action-link:hover { background:#eff6ff; }
            .action-link.no-doc { color:#94a3b8; cursor:not-allowed; }

            /* ---------- Empty state ---------- */
            .empty-state { text-align:center; padding:60px 20px; background:white; border-radius:12px; border:1px solid #e2e8f0; }
            .empty-state i { font-size:48px; color:#cbd5e1; margin-bottom:16px; display:block; }
            .empty-state p { color:#64748b; font-size:16px; margin-bottom:8px; }
            .empty-state small { color:#94a3b8; font-size:13px; }

            /* ---------- Footer ---------- */
            .footer { text-align:center; color:#94a3b8; font-size:13px; padding:10px 0; }

            /* ---------- Responsive ---------- */
            @media (max-width:768px) {
                .filters { flex-direction:column; align-items:stretch; }
                .filters .actions { width:100%; }
                .filters .actions .btn { flex:1; justify-content:center; }
                .container { padding:0; }
                body { padding:12px; }
            }
            """;
    }

    // ============================================================
    // COMPOSANTS
    // ============================================================
    private static String renderHeader(String etudiantId, String nomComplet,
                                        String classe, String institutionId) {
        return """
            <div class="header">
                <h1><i class="fas fa-folder-open"></i> Mes Documents</h1>
                <div class="header-nav">
                    <a href="/etudiant/dashboard"><i class="fas fa-home"></i> Dashboard</a>
                    <a href="/etudiant/profil"><i class="fas fa-user"></i> Mon Profil</a>
                    <a href="/etudiant/documents" class="active"><i class="fas fa-file-pdf"></i> Documents</a>
                    <a href="/etudiant/absences"><i class="fas fa-user-slash"></i> Absences</a>
                    <a href="/logout"><i class="fas fa-sign-out-alt"></i> Déconnexion</a>
                </div>
                <div class="header-info">
                    """ + echapperHtml(nomComplet)
                    + " · Classe : " + echapperHtml(classe)
                    + " · ID : " + echapperHtml(etudiantId)
                    + " · Établissement : " + echapperHtml(institutionId) + """
                </div>
            </div>
            """;
    }

    // ============================================================
    // FILTRES
    // ============================================================
    private static String renderFilters(List<String> matieres,
                                        List<AnneeAcademique> annees,
                                        List<Periode> periodes,
                                        String matiereSel,
                                        String periodeSel,
                                        String anneeSel) {

        StringBuilder sb = new StringBuilder(2048);
        sb.append("<form method=\"GET\" action=\"/etudiant/documents\" class=\"filters\">");

        // Matière
        sb.append("<div class=\"form-group\">");
        sb.append("<label for=\"filtre-matiere\">Matière</label>");
        sb.append("<select name=\"matiere\" id=\"filtre-matiere\">");
        sb.append("<option value=\"\">Toutes les matières</option>");
        if (matieres != null) {
            for (String m : matieres) {
                if (m == null || m.isBlank()) continue;
                String sel = m.equals(matiereSel) ? "selected" : "";
                sb.append("<option value=\"").append(echapperHtml(m)).append("\" ")
                  .append(sel).append(">").append(echapperHtml(m)).append("</option>");
            }
        }
        sb.append("</select></div>");

        // Période
        sb.append("<div class=\"form-group\">");
        sb.append("<label for=\"filtre-periode\">Période</label>");
        sb.append("<select name=\"periode\" id=\"filtre-periode\">");
        sb.append("<option value=\"\">Toutes les périodes</option>");
        if (periodes != null) {
            for (Periode p : periodes) {
                if (p == null) continue;
                String val = p.getPeriode();
                if (val == null || val.isBlank()) continue;
                String sel = val.equals(periodeSel) ? "selected" : "";
                sb.append("<option value=\"").append(echapperHtml(val)).append("\" ")
                  .append(sel).append(">").append(echapperHtml(val)).append("</option>");
            }
        }
        sb.append("</select></div>");

        // Année académique
        sb.append("<div class=\"form-group\">");
        sb.append("<label for=\"filtre-annee\">Année académique</label>");
        sb.append("<select name=\"annee\" id=\"filtre-annee\">");
        sb.append("<option value=\"\">Toutes les années</option>");
        if (annees != null) {
            for (AnneeAcademique a : annees) {
                if (a == null) continue;
                String val = a.getAnneeAcademique();
                if (val == null || val.isBlank()) continue;
                String sel = val.equals(anneeSel) ? "selected" : "";
                sb.append("<option value=\"").append(echapperHtml(val)).append("\" ")
                  .append(sel).append(">").append(echapperHtml(val)).append("</option>");
            }
        }
        sb.append("</select></div>");

        // Actions
        sb.append("<div class=\"actions\">");
        sb.append("<button type=\"submit\" class=\"btn btn-filter\">")
          .append("<i class=\"fas fa-filter\"></i> Filtrer</button>");
        sb.append("<a href=\"/etudiant/documents\" class=\"btn btn-reset\">")
          .append("<i class=\"fas fa-times\"></i> Réinitialiser</a>");
        sb.append("</div>");

        sb.append("</form>");
        return sb.toString();
    }

    // ============================================================
    // TABLEAU
    // ============================================================
    private static String renderTable(List<DocumentProfesseur> documents) {

        // ✅ Cas vide
        if (documents == null || documents.isEmpty()) {
            return """
                <div class="empty-state">
                    <i class="fas fa-folder-open"></i>
                    <p>Aucun document disponible.</p>
                    <small>Les documents publiés par vos professeurs apparaîtront ici.</small>
                </div>
                """;
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_PATTERN, Locale.FRENCH);

        StringBuilder sb = new StringBuilder(8192);
        sb.append("<div class=\"table-wrapper\"><table>");
        sb.append("<thead><tr>");
        sb.append("<th>Titre</th>");
        sb.append("<th>Matière</th>");
        sb.append("<th>Classe</th>");
        sb.append("<th>Type</th>");
        sb.append("<th>Taille</th>");
        sb.append("<th>Date</th>");
        sb.append("<th>Action</th>");
        sb.append("</tr></thead><tbody>");

        for (DocumentProfesseur d : documents) {
            if (d == null) continue;

            sb.append("<tr>");

            // Titre
            sb.append("<td><strong>").append(echapperHtml(d.getTitre())).append("</strong></td>");

            // Matière
            sb.append("<td>").append(echapperHtml(d.getMatiere())).append("</td>");

            // Classe
            sb.append("<td>").append(echapperHtml(d.getClasse())).append("</td>");

            // Type
            sb.append("<td>");
            if (d.getType() != null && !d.getType().isBlank()) {
                sb.append("<span class=\"badge badge-primary\">")
                  .append(echapperHtml(d.getType()))
                  .append("</span>");
            } else {
                sb.append("—");
            }
            sb.append("</td>");

            // Taille
            sb.append("<td>").append(echapperHtml(d.getTailleFormatee())).append("</td>");

            // Date
            String dateStr = "—";
            if (d.getDateCreation() != null) {
                try {
                    dateStr = dateFormat.format(d.getDateCreation());
                } catch (Exception ignored) {
                    dateStr = "—";
                }
            }
            sb.append("<td>").append(echapperHtml(dateStr)).append("</td>");

            // Action
            sb.append("<td>");
            String url = d.getUrl();
            String fichierChemin = d.getFichierChemin();
            String docId = d.getId();

            if (url != null && !url.isBlank()) {
                // Lien externe
                sb.append("<a href=\"").append(echapperHtml(url))
                  .append("\" target=\"_blank\" rel=\"noopener noreferrer\" ")
                  .append("class=\"action-link\" title=\"Ouvrir le lien\">")
                  .append("<i class=\"fas fa-external-link-alt\"></i> Lien</a>");

            } else if (fichierChemin != null && !fichierChemin.isBlank()
                    && docId != null && !docId.isBlank()) {
                // Téléchargement
                sb.append("<a href=\"/etudiant/documents/download?id=")
                  .append(echapperHtml(docId))
                  .append("\" class=\"action-link\" title=\"Télécharger\">")
                  .append("<i class=\"fas fa-download\"></i> Télécharger</a>");

            } else {
                // Indisponible
                sb.append("<span class=\"action-link no-doc\" title=\"Non disponible\">")
                  .append("<i class=\"fas fa-times\"></i> Non disponible</span>");
            }
            sb.append("</td>");

            sb.append("</tr>");
        }

        sb.append("</tbody></table></div>");
        return sb.toString();
    }

    // ============================================================
    // ÉCHAPPEMENT HTML
    // ============================================================
    private static String echapperHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }
}