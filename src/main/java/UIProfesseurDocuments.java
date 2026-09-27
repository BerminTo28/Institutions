import java.text.SimpleDateFormat;
import java.time.Year;
import java.util.List;

public class UIProfesseurDocuments {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy HH:mm");

    public static String render(String professeurId,
                                List<DocumentProfesseur> documents,
                                List<String> matieres,
                                List<String> classesDispo,
                                List<String> periodesDispo,      // pour les filtres (périodes distinctes des documents)
                                String matiereSel,
                                String classeSel,
                                String periodeSel,
                                String institutionId,
                                List<AnneeAcademique> annees,    // ← toutes les années académiques de l'admin
                                List<Periode> periodes,          // ← toutes les périodes de l'admin
                                String anneeActive,              // année active du professeur
                                String periodeActive,            // période active du professeur
                                String msgType,
                                String msg) {

        if (matiereSel == null) matiereSel = "";
        if (classeSel == null) classeSel = "";
        if (periodeSel == null) periodeSel = "";
        if (anneeActive == null) anneeActive = "";
        if (periodeActive == null) periodeActive = "";

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>");
        sb.append("<html lang=\"fr\">");
        sb.append("<head>");
        sb.append("<meta charset=\"UTF-8\">");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        sb.append("<title>Mes Documents</title>");
        sb.append("<link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css\">");
        sb.append("<link href=\"https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@300;400;500;600;700;800&display=swap\" rel=\"stylesheet\">");
        sb.append("<style>");
        sb.append(renderStyles());
        sb.append("</style>");
        sb.append("</head>");
        sb.append("<body>");
        sb.append("<div class=\"container\">");

        // Header
        sb.append(renderHeader(professeurId, institutionId));

        // Message
        if (msg != null && msgType != null && !msg.isBlank()) {
            sb.append("<div class=\"message ").append(msgType).append("\">");
            sb.append("<i class=\"fas fa-").append(msgType.equals("success") ? "check-circle" : "exclamation-triangle").append("\"></i> ");
            sb.append(echapperHtml(msg));
            sb.append("</div>");
        }

        // Filtres
        sb.append(renderFilters(matieres, classesDispo, periodesDispo, matiereSel, classeSel, periodeSel));

        // Bouton Ajouter
        sb.append("<div class=\"actions\">");
        sb.append("<button class=\"btn-add\" onclick=\"toggleForm()\"><i class=\"fas fa-plus\"></i> Ajouter un document</button>");
        sb.append("</div>");

        // Formulaire d'ajout avec listes déroulantes pour année et période
        sb.append(renderFormAjout(matieres, classesDispo, annees, periodes, institutionId, anneeActive, periodeActive));

        // Tableau
        sb.append(renderTable(documents));

        // Footer
        sb.append("<div class=\"footer\">");
        sb.append("© ").append(Year.now().getValue()).append(" M-TECH Academy - Tous droits réservés");
        sb.append("</div>");

        sb.append("</div>");
        sb.append("<script>");
        sb.append("function toggleForm() {");
        sb.append("  var form = document.getElementById('formAjout');");
        sb.append("  if (form.style.display === 'none' || form.style.display === '') {");
        sb.append("    form.style.display = 'block';");
        sb.append("  } else {");
        sb.append("    form.style.display = 'none';");
        sb.append("  }");
        sb.append("}");
        sb.append("</script>");
        sb.append("</body>");
        sb.append("</html>");

        return sb.toString();
    }

    public static String renderError(String message) {
        return "<!DOCTYPE html>"
            + "<html><head><meta charset='UTF-8'><title>Erreur</title>"
            + "<style>"
            + "*{margin:0;padding:0;box-sizing:border-box;font-family:sans-serif;}"
            + "body{background:#f4f6f9;display:flex;justify-content:center;align-items:center;height:100vh;}"
            + ".error{background:#fff;padding:40px;border-radius:16px;text-align:center;max-width:500px;}"
            + ".error i{font-size:64px;color:#dc2626;margin-bottom:20px;}"
            + ".error h1{color:#1e293b;margin-bottom:10px;}"
            + ".error p{color:#64748b;}"
            + ".error .btn{display:inline-block;margin-top:20px;padding:10px 24px;background:#1e40af;color:#fff;text-decoration:none;border-radius:8px;}"
            + "</style>"
            + "</head><body>"
            + "<div class='error'>"
            + "<i class='fas fa-exclamation-triangle'></i>"
            + "<h1>Erreur</h1>"
            + "<p>" + echapperHtml(message) + "</p>"
            + "<a href='/professeur/documents' class='btn'><i class='fas fa-arrow-left'></i> Retour</a>"
            + "</div></body></html>";
    }

    // ==================== STYLES ====================
    private static String renderStyles() {
        return ""
            + "* { margin:0; padding:0; box-sizing:border-box; }"
            + "html, body { min-height:100vh; font-family:'Plus Jakarta Sans',sans-serif; background:#f0f4f8; }"
            + ".container { width:100%; max-width:1400px; margin:0 auto; padding:20px; display:flex; flex-direction:column; }"
            + ".header { background:linear-gradient(135deg,#1e293b,#0f172a); color:white; padding:20px 30px; border-radius:16px; margin-bottom:20px; }"
            + ".header h1 { font-size:24px; font-weight:700; }"
            + ".header h1 i { color:#3b82f6; margin-right:10px; }"
            + ".header-nav { display:flex; justify-content:center; gap:20px; margin-top:10px; flex-wrap:wrap; }"
            + ".header-nav a { color:#94a3b8; text-decoration:none; font-size:14px; padding:4px 12px; border-radius:20px; transition:0.2s; }"
            + ".header-nav a:hover { color:white; background:rgba(255,255,255,0.1); }"
            + ".header-nav a.active { color:white; background:rgba(255,255,255,0.15); font-weight:600; }"
            + ".header-info { font-size:13px; color:#94a3b8; margin-top:8px; text-align:center; }"
            + ".message { padding:12px 18px; border-radius:8px; margin-bottom:20px; }"
            + ".message.success { background:#d1fae5; color:#16a34a; border-left:4px solid #16a34a; }"
            + ".message.error { background:#fee2e2; color:#dc2626; border-left:4px solid #dc2626; }"
            + ".filters { background:white; border-radius:12px; padding:20px; margin-bottom:20px; display:flex; gap:15px; flex-wrap:wrap; align-items:end; border:1px solid #e2e8f0; }"
            + ".filters .form-group { flex:1; min-width:150px; }"
            + ".filters label { display:block; font-weight:600; color:#475569; margin-bottom:4px; font-size:13px; }"
            + ".filters select { width:100%; padding:8px 12px; border:1px solid #cbd5e1; border-radius:8px; background:white; }"
            + ".filters .btn-filter { background:#1e40af; color:white; border:none; padding:8px 20px; border-radius:8px; cursor:pointer; font-weight:600; transition:0.2s; }"
            + ".filters .btn-filter:hover { background:#1e3a8a; }"
            + ".actions { text-align:right; margin-bottom:15px; }"
            + ".btn-add { background:#16a34a; color:white; border:none; padding:10px 20px; border-radius:8px; cursor:pointer; font-weight:600; transition:0.2s; }"
            + ".btn-add:hover { background:#15803d; }"
            + ".form-ajout { background:white; border-radius:12px; padding:20px; margin-bottom:20px; border:1px solid #e2e8f0; display:none; }"
            + ".form-ajout .row { display:flex; gap:15px; flex-wrap:wrap; margin-bottom:10px; }"
            + ".form-ajout .form-group { flex:1; min-width:150px; }"
            + ".form-ajout label { display:block; font-weight:600; color:#475569; margin-bottom:4px; font-size:13px; }"
            + ".form-ajout input, .form-ajout select, .form-ajout textarea { width:100%; padding:8px 12px; border:1px solid #cbd5e1; border-radius:8px; }"
            + ".form-ajout .btn-submit { background:#1e40af; color:white; border:none; padding:10px 20px; border-radius:8px; cursor:pointer; font-weight:600; margin-right:10px; }"
            + ".form-ajout .btn-submit:hover { background:#1e3a8a; }"
            + ".table-wrapper { background:white; border-radius:12px; padding:20px; overflow-x:auto; border:1px solid #e2e8f0; flex:1; }"
            + "table { width:100%; border-collapse:collapse; font-size:14px; }"
            + "th, td { padding:10px 12px; text-align:left; border-bottom:1px solid #e2e8f0; }"
            + "th { background:#f8fafc; font-weight:600; color:#475569; }"
            + ".badge { padding:3px 12px; border-radius:12px; font-size:12px; font-weight:600; }"
            + ".badge-primary { background:#dbeafe; color:#2563eb; }"
            + ".empty-state { text-align:center; padding:40px; background:white; border-radius:12px; border:1px solid #e2e8f0; }"
            + ".empty-state i { font-size:48px; color:#94a3b8; margin-bottom:12px; }"
            + ".empty-state p { color:#64748b; font-size:16px; }"
            + ".footer { margin-top:20px; text-align:center; color:#94a3b8; font-size:13px; padding:10px 0; }"
            + "@media (max-width:768px) { .filters, .form-ajout .row { flex-direction:column; } .container { padding:10px; } }";
    }

    // ==================== COMPOSANTS ====================
    private static String renderHeader(String professeurId, String institutionId) {
        return ""
            + "<div class=\"header\">"
            + "  <h1><i class=\"fas fa-folder-open\"></i> Mes Documents</h1>"
            + "  <div class=\"header-nav\">"
            + "    <a href=\"/dashboard/professeur\"><i class=\"fas fa-home\"></i> Dashboard</a>"
            + "    <a href=\"/professeur/profil\"><i class=\"fas fa-user\"></i> Mon Profil</a>"
            + "    <a href=\"/professeur/cours\"><i class=\"fas fa-book\"></i> Mes Cours</a>"
            + "    <a href=\"/professeur/documents\" class=\"active\"><i class=\"fas fa-file-pdf\"></i> Documents</a>"
            + "    <a href=\"/professeur/absences\"><i class=\"fas fa-user-slash\"></i> Absences</a>"
            + "    <a href=\"/logout\"><i class=\"fas fa-sign-out-alt\"></i> Déconnexion</a>"
            + "  </div>"
            + "  <div class=\"header-info\">ID: " + echapperHtml(professeurId) + " · Établissement: " + echapperHtml(institutionId) + "</div>"
            + "</div>";
    }

    private static String renderFilters(List<String> matieres, List<String> classes, List<String> periodes,
                                        String matiereSel, String classeSel, String periodeSel) {
        StringBuilder sb = new StringBuilder();
        sb.append("<form method=\"GET\" action=\"/professeur/documents\" class=\"filters\">");

        sb.append("<div class=\"form-group\"><label>Matière</label><select name=\"matiere\">");
        sb.append("<option value=\"\">Toutes</option>");
        for (String m : matieres) {
            String selected = m.equals(matiereSel) ? "selected" : "";
            sb.append("<option value=\"").append(echapperHtml(m)).append("\" ").append(selected).append(">")
              .append(echapperHtml(m)).append("</option>");
        }
        sb.append("</select></div>");

        sb.append("<div class=\"form-group\"><label>Classe</label><select name=\"classe\">");
        sb.append("<option value=\"\">Toutes</option>");
        for (String c : classes) {
            String selected = c.equals(classeSel) ? "selected" : "";
            sb.append("<option value=\"").append(echapperHtml(c)).append("\" ").append(selected).append(">")
              .append(echapperHtml(c)).append("</option>");
        }
        sb.append("</select></div>");

        sb.append("<div class=\"form-group\"><label>Période</label><select name=\"periode\">");
        sb.append("<option value=\"\">Toutes</option>");
        for (String p : periodes) {
            String selected = p.equals(periodeSel) ? "selected" : "";
            sb.append("<option value=\"").append(echapperHtml(p)).append("\" ").append(selected).append(">")
              .append(echapperHtml(p)).append("</option>");
        }
        sb.append("</select></div>");

        sb.append("<div><button type=\"submit\" class=\"btn-filter\"><i class=\"fas fa-filter\"></i> Filtrer</button></div>");
        sb.append("</form>");
        return sb.toString();
    }

    // Formulaire avec listes déroulantes pour année et période
    private static String renderFormAjout(List<String> matieres, List<String> classes,
                                          List<AnneeAcademique> annees, List<Periode> periodes,
                                          String institutionId, String anneeActive, String periodeActive) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div id=\"formAjout\" class=\"form-ajout\">");
        sb.append("<h3><i class=\"fas fa-upload\"></i> Ajouter un document</h3>");
        sb.append("<form method=\"POST\" action=\"/professeur/documents\" enctype=\"multipart/form-data\">");
        sb.append("<input type=\"hidden\" name=\"action\" value=\"ajouter\">");
        sb.append("<input type=\"hidden\" name=\"institutionId\" value=\"").append(echapperHtml(institutionId)).append("\">");

        // Ligne 1 : Titre et Type
        sb.append("<div class=\"row\">");
        sb.append("<div class=\"form-group\"><label>Titre *</label><input type=\"text\" name=\"titre\" required></div>");
        sb.append("<div class=\"form-group\"><label>Type</label><select name=\"type\">");
        sb.append("<option value=\"cours\">Cours</option>");
        sb.append("<option value=\"exercice\">Exercice</option>");
        sb.append("<option value=\"examen\">Examen</option>");
        sb.append("<option value=\"corrige\">Corrigé</option>");
        sb.append("<option value=\"autre\">Autre</option>");
        sb.append("</select></div>");
        sb.append("</div>");

        // Ligne 2 : Matière et Classe
        sb.append("<div class=\"row\">");
        sb.append("<div class=\"form-group\"><label>Matière *</label><select name=\"matiere\" required>");
        sb.append("<option value=\"\">-- Sélectionner --</option>");
        for (String m : matieres) {
            sb.append("<option value=\"").append(echapperHtml(m)).append("\">").append(echapperHtml(m)).append("</option>");
        }
        sb.append("</select></div>");
        sb.append("<div class=\"form-group\"><label>Classe *</label><select name=\"classe\" required>");
        sb.append("<option value=\"\">-- Sélectionner --</option>");
        for (String c : classes) {
            sb.append("<option value=\"").append(echapperHtml(c)).append("\">").append(echapperHtml(c)).append("</option>");
        }
        sb.append("</select></div>");
        sb.append("</div>");

        // Ligne 3 : Année et Période (listes déroulantes)
        sb.append("<div class=\"row\">");
        sb.append("<div class=\"form-group\"><label>Année académique *</label><select name=\"annee_academique\" required>");
        for (AnneeAcademique a : annees) {
            String selected = a.getAnneeAcademique().equals(anneeActive) ? "selected" : "";
            sb.append("<option value=\"").append(echapperHtml(a.getAnneeAcademique())).append("\" ").append(selected).append(">")
              .append(echapperHtml(a.getAnneeAcademique())).append("</option>");
        }
        sb.append("</select></div>");
        sb.append("<div class=\"form-group\"><label>Période *</label><select name=\"periode\" required>");
        for (Periode p : periodes) {
            String selected = p.getPeriode().equals(periodeActive) ? "selected" : "";
            sb.append("<option value=\"").append(echapperHtml(p.getPeriode())).append("\" ").append(selected).append(">")
              .append(echapperHtml(p.getPeriode())).append("</option>");
        }
        sb.append("</select></div>");
        sb.append("</div>");

        // Description
        sb.append("<div class=\"form-group\"><label>Description</label><textarea name=\"description\" rows=\"2\"></textarea></div>");

        // Fichier et URL
        sb.append("<div class=\"form-group\"><label>Fichier (PDF, DOC, etc.)</label><input type=\"file\" name=\"fichier\"></div>");
        sb.append("<div class=\"form-group\"><label>URL (lien externe)</label><input type=\"url\" name=\"url\" placeholder=\"https://...\"></div>");

        // Public
        sb.append("<div class=\"form-group\">");
        sb.append("<input type=\"checkbox\" name=\"est_public\" id=\"public\"> <label for=\"public\" style=\"display:inline;\">Rendre public</label>");
        sb.append("</div>");

        sb.append("<div style=\"margin-top:15px;\">");
        sb.append("<button type=\"submit\" class=\"btn-submit\"><i class=\"fas fa-save\"></i> Enregistrer</button>");
        sb.append("<button type=\"button\" onclick=\"toggleForm()\">Annuler</button>");
        sb.append("</div>");

        sb.append("</form>");
        sb.append("</div>");
        return sb.toString();
    }

    private static String renderTable(List<DocumentProfesseur> documents) {
        if (documents == null || documents.isEmpty()) {
            return "<div class=\"empty-state\"><i class=\"fas fa-folder-open\"></i><p>Aucun document trouvé.</p></div>";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"table-wrapper\"><table>");
        sb.append("<thead><tr>");
        sb.append("<th>Titre</th><th>Matière</th><th>Classe</th><th>Période</th><th>Type</th><th>Taille</th><th>Date</th><th>Actions</th>");
        sb.append("</tr></thead><tbody>");

        for (DocumentProfesseur d : documents) {
            sb.append("<tr>");
            sb.append("<td><strong>").append(echapperHtml(d.getTitre())).append("</strong></td>");
            sb.append("<td>").append(echapperHtml(d.getMatiere())).append("</td>");
            sb.append("<td>").append(echapperHtml(d.getClasse())).append("</td>");
            sb.append("<td>").append(echapperHtml(d.getPeriode())).append("</td>");
            sb.append("<td><span class=\"badge badge-primary\">").append(echapperHtml(d.getType())).append("</span></td>");
            sb.append("<td>").append(echapperHtml(d.getTailleFormatee())).append("</td>");
            sb.append("<td>").append(d.getDateCreation() != null ? DATE_FORMAT.format(d.getDateCreation()) : "").append("</td>");
            sb.append("<td>");
            sb.append("<a href=\"/professeur/documents/download?id=").append(d.getId()).append("\" style=\"color:#2563eb;margin-right:10px;\"><i class=\"fas fa-download\"></i></a>");
            sb.append("<button onclick=\"if(confirm('Supprimer ?')){ document.getElementById('deleteForm_").append(d.getId()).append("').submit(); }\" style=\"color:#dc2626;background:none;border:none;cursor:pointer;\"><i class=\"fas fa-trash\"></i></button>");
            sb.append("<form id=\"deleteForm_").append(d.getId()).append("\" method=\"POST\" style=\"display:none;\">");
            sb.append("<input type=\"hidden\" name=\"action\" value=\"supprimer\">");
            sb.append("<input type=\"hidden\" name=\"id\" value=\"").append(d.getId()).append("\">");
            sb.append("</form>");
            sb.append("</td>");
            sb.append("</tr>");
        }

        sb.append("</tbody></table></div>");
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