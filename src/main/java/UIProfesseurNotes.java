import java.time.Year;
import java.util.List;
import java.util.Map;

public class UIProfesseurNotes {

    private static final String DEFAULT_AVATAR = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='150' height='150'%3E%3Crect width='150' height='150' fill='%231e40af'/%3E%3Ctext x='75' y='95' font-size='60' text-anchor='middle' fill='white' font-family='sans-serif'%3E👨‍🏫%3C/text%3E%3C/svg%3E";

    public static String render(String professeurId,
                                List<String> annees,
                                List<String> periodes,
                                List<String> classes,
                                List<Map<String, String>> cours,
                                String anneeSel,
                                String periodeSel,
                                String codeCoursSel,
                                String classeSel,
                                List<Etudiant> etudiants,
                                List<Note> notesExistantes,
                                Matiere matiere,
                                String msgType,
                                String msg) {

        Map<String, Note> noteMap = new java.util.HashMap<>();
        if (notesExistantes != null) {
            for (Note n : notesExistantes) {
                String key = n.getNumeroIdentifiantEtudiant() + "_" + n.getCodeCours();
                noteMap.put(key, n);
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>");
        sb.append("<html lang=\"fr\">");
        sb.append("<head>");
        sb.append("<meta charset=\"UTF-8\">");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        sb.append("<title>Saisie des Notes - Professeur</title>");
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

        sb.append(renderFilters(annees, periodes, classes, cours, anneeSel, periodeSel, codeCoursSel, classeSel));

        if (matiere != null) {
            sb.append("<div style=\"background:#f8fafc; border-radius:12px; padding:12px 20px; margin-bottom:20px; display:flex; gap:20px; flex-wrap:wrap; border:1px solid #e2e8f0;\">");
            sb.append("<span><i class=\"fas fa-arrow-down\" style=\"color:#dc2626;\"></i> Note min: <strong>").append(matiere.getNoteMinimale()).append("</strong></span>");
            sb.append("<span><i class=\"fas fa-arrow-up\" style=\"color:#16a34a;\"></i> Note max: <strong>").append(matiere.getNoteMaximale()).append("</strong></span>");
            sb.append("<span><i class=\"fas fa-check-circle\" style=\"color:#f59e0b;\"></i> Passage: <strong>").append(matiere.getNotePassage()).append("</strong></span>");
            sb.append("<span><i class=\"fas fa-star\" style=\"color:#1e40af;\"></i> Coefficient: <strong>").append(matiere.getCoefficient()).append("</strong></span>");
            sb.append("</div>");
        }

        if (codeCoursSel != null && !codeCoursSel.isEmpty() && etudiants != null && !etudiants.isEmpty()) {
            sb.append(renderNotesTable(etudiants, noteMap, codeCoursSel, anneeSel, periodeSel, classeSel, professeurId, matiere));
        } else if (codeCoursSel != null && !codeCoursSel.isEmpty()) {
            sb.append("<div class=\"empty-state\">");
            sb.append("<i class=\"fas fa-users\"></i>");
            sb.append("<p>Aucun étudiant trouvé pour ce cours, cette année et cette période.</p>");
            sb.append("</div>");
        } else {
            sb.append("<div class=\"empty-state\">");
            sb.append("<i class=\"fas fa-info-circle\"></i>");
            sb.append("<p>Sélectionnez une année, une période, une classe et un cours pour saisir les notes.</p>");
            sb.append("</div>");
        }

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
        sb.append("<title>Erreur - Saisie des Notes</title>");
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
        sb.append("<a href=\"/professeur/notes\" class=\"btn\"><i class=\"fas fa-arrow-left\"></i> Retour</a>");
        sb.append("</div>");
        sb.append("</body>");
        sb.append("</html>");
        return sb.toString();
    }

    // ==================== STYLES (ajout pour readonly) ====================
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

        .filters { background:white; border-radius:12px; padding:20px; margin-bottom:20px; border:1px solid #e2e8f0; display:flex; gap:15px; flex-wrap:wrap; align-items:end; }
        .filters .form-group { flex:1; min-width:150px; }
        .filters label { display:block; font-weight:600; color:#475569; margin-bottom:4px; font-size:13px; }
        .filters select { width:100%; padding:8px 12px; border:1px solid #cbd5e1; border-radius:8px; font-size:14px; background:white; }
        .filters .btn-filter { background:#1e40af; color:white; border:none; padding:8px 20px; border-radius:8px; cursor:pointer; font-weight:600; }
        .filters .btn-filter:hover { background:#1e3a8a; }

        .table-wrapper { background:white; border-radius:12px; padding:20px; overflow-x:auto; border:1px solid #e2e8f0; }
        table { width:100%; border-collapse:collapse; font-size:14px; }
        th, td { padding:10px 12px; text-align:center; border-bottom:1px solid #e2e8f0; }
        th { background:#f8fafc; font-weight:600; color:#475569; }
        td:first-child, th:first-child { text-align:left; }

        .note-input { width:80px; padding:6px 8px; border:1px solid #cbd5e1; border-radius:6px; font-size:14px; text-align:center; }
        .note-input:focus { border-color:#1e40af; outline:none; box-shadow:0 0 0 3px rgba(30,64,175,0.1); }
        /* Style pour les champs readonly */
        .note-input-readonly { width:80px; padding:6px 8px; border:1px solid #e2e8f0; border-radius:6px; font-size:14px; text-align:center; background:#f1f5f9; color:#475569; cursor:not-allowed; }

        .btn-save { background:#16a34a; color:white; border:none; padding:10px 30px; border-radius:8px; font-weight:600; cursor:pointer; font-size:15px; transition:0.2s; margin-top:15px; }
        .btn-save:hover { background:#15803d; }
        .btn-save i { margin-right:8px; }
        .btn-cancel { background:#94a3b8; color:white; border:none; padding:10px 30px; border-radius:8px; font-weight:600; cursor:pointer; font-size:15px; transition:0.2s; margin-top:15px; }
        .btn-cancel:hover { background:#64748b; }

        .empty-state { text-align:center; padding:40px; background:white; border-radius:12px; border:1px solid #e2e8f0; }
        .empty-state i { font-size:48px; color:#94a3b8; margin-bottom:12px; }
        .empty-state p { color:#64748b; font-size:16px; }

        .footer { margin-top:30px; text-align:center; color:#94a3b8; font-size:13px; }
        @media (max-width:768px) { .filters { flex-direction:column; } }
        """;
    }

    private static String renderHeader(String professeurId) {
        System.out.print("Bienvenue "+professeurId);
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"header\">");
        sb.append("<img src=\"").append(DEFAULT_AVATAR).append("\" alt=\"Avatar\" class=\"header-avatar\">");
        sb.append("<h1><i class=\"fas fa-pen\"></i> Saisie des Notes</h1>");
        sb.append("<div class=\"header-nav\">");
        sb.append("<a href=\"/dashboard/professeur\"><i class=\"fas fa-home\"></i> Dashboard</a>");
        sb.append("<a href=\"/professeur/profil\"><i class=\"fas fa-user\"></i> Mon Profil</a>");
        sb.append("<a href=\"/professeur/cours\"><i class=\"fas fa-book\"></i> Mes Cours</a>");
        sb.append("<a href=\"/professeur/notes\" class=\"active\"><i class=\"fas fa-pen\"></i> Saisie Notes</a>");
        sb.append("<a href=\"/logout\"><i class=\"fas fa-sign-out-alt\"></i> Déconnexion</a>");
        sb.append("</div>");
        sb.append("</div>");
        return sb.toString();
    }

    // ==================== FILTRES ====================
    private static String renderFilters(List<String> annees,
                                        List<String> periodes,
                                        List<String> classes,
                                        List<Map<String, String>> cours,
                                        String anneeSel,
                                        String periodeSel,
                                        String codeCoursSel,
                                        String classeSel) {

        StringBuilder sb = new StringBuilder();
        sb.append("<form method=\"GET\" action=\"/professeur/notes\" class=\"filters\">");

        sb.append("<div class=\"form-group\">");
        sb.append("<label for=\"annee\">Année académique</label>");
        sb.append("<select name=\"annee\" id=\"annee\" onchange=\"this.form.submit()\">");
        sb.append("<option value=\"\">-- Sélectionner --</option>");
        for (String a : annees) {
            String selected = a.equals(anneeSel) ? "selected" : "";
            sb.append("<option value=\"").append(echapperHtml(a)).append("\" ").append(selected).append(">").append(echapperHtml(a)).append("</option>");
        }
        sb.append("</select>");
        sb.append("</div>");

        sb.append("<div class=\"form-group\">");
        sb.append("<label for=\"periode\">Période</label>");
        sb.append("<select name=\"periode\" id=\"periode\" onchange=\"this.form.submit()\">");
        sb.append("<option value=\"\">-- Sélectionner --</option>");
        for (String p : periodes) {
            String selected = p.equals(periodeSel) ? "selected" : "";
            sb.append("<option value=\"").append(echapperHtml(p)).append("\" ").append(selected).append(">").append(echapperHtml(p)).append("</option>");
        }
        sb.append("</select>");
        sb.append("</div>");

        sb.append("<div class=\"form-group\">");
        sb.append("<label for=\"classe\">Classe</label>");
        sb.append("<select name=\"classe\" id=\"classe\" onchange=\"this.form.submit()\">");
        sb.append("<option value=\"\">-- Sélectionner --</option>");
        for (String cl : classes) {
            String selected = cl.equals(classeSel) ? "selected" : "";
            sb.append("<option value=\"").append(echapperHtml(cl)).append("\" ").append(selected).append(">").append(echapperHtml(cl)).append("</option>");
        }
        sb.append("</select>");
        sb.append("</div>");

        sb.append("<div class=\"form-group\">");
        sb.append("<label for=\"codeCours\">Cours</label>");
        sb.append("<select name=\"codeCours\" id=\"codeCours\" onchange=\"this.form.submit()\">");
        sb.append("<option value=\"\">-- Sélectionner --</option>");
        for (Map<String, String> c : cours) {
            String code = c.get("codeCours");
            String nom = c.get("nomMatiere");
            String selected = code.equals(codeCoursSel) ? "selected" : "";
            sb.append("<option value=\"").append(echapperHtml(code)).append("\" ").append(selected).append(">")
              .append(echapperHtml(nom)).append(" (").append(echapperHtml(code)).append(")</option>");
        }
        sb.append("</select>");
        sb.append("</div>");

        sb.append("<div>");
        sb.append("<button type=\"submit\" class=\"btn-filter\"><i class=\"fas fa-filter\"></i> Filtrer</button>");
        sb.append("</div>");
        sb.append("</form>");
        return sb.toString();
    }

    // ==================== TABLEAU DES NOTES (CORRIGÉ) ====================
    private static String renderNotesTable(List<Etudiant> etudiants,
                                           Map<String, Note> noteMap,
                                           String codeCours,
                                           String annee,
                                           String periode,
                                           String classe,
                                           String professeurId,
                                           Matiere matiere) {
                                            System.out.print("Bienvenue "+professeurId);
        StringBuilder sb = new StringBuilder();
        sb.append("<form method=\"POST\" action=\"/professeur/notes\">");
        sb.append("<input type=\"hidden\" name=\"action\" value=\"saveNotes\">");
        sb.append("<input type=\"hidden\" name=\"annee\" value=\"").append(echapperHtml(annee)).append("\">");
        sb.append("<input type=\"hidden\" name=\"periode\" value=\"").append(echapperHtml(periode)).append("\">");
        sb.append("<input type=\"hidden\" name=\"codeCours\" value=\"").append(echapperHtml(codeCours)).append("\">");
        sb.append("<input type=\"hidden\" name=\"classe\" value=\"").append(echapperHtml(classe)).append("\">");

        sb.append("<div class=\"table-wrapper\">");
        sb.append("<table>");
        sb.append("<thead><tr>");
        sb.append("<th>Étudiant</th>");
        if (matiere != null) {
            sb.append("<th>Note / ").append(matiere.getNoteMaximale()).append("</th>");
        } else {
            sb.append("<th>Note</th>");
        }
        sb.append("<th>Coefficient</th>");
        sb.append("<th>Note de base</th>");
        sb.append("<th>Note sur</th>");
        sb.append("<th>Rang</th>");
        sb.append("</tr></thead>");
        sb.append("<tbody>");

        for (Etudiant e : etudiants) {
            String key = e.getNumeroIdentifiantEtudiant() + "_" + codeCours;
            Note note = noteMap.get(key);
            String noteValue = note != null ? note.getNoteValue() : "";
            String coefficient = note != null && note.getCoefficient() != null ? note.getCoefficient() : "1.0";
            String noteBase = note != null && note.getNoteBase() != null ? note.getNoteBase() : "0";
            String noteSur = note != null && note.getNoteSur() != null ? note.getNoteSur() : (matiere != null ? String.valueOf(matiere.getNoteMaximale()) : "20");
            String rang = note != null && note.getRangEtudiant() != null ? note.getRangEtudiant() : "-";

            sb.append("<tr>");
            sb.append("<td>").append(echapperHtml(e.getNomComplet())).append("</td>");
            // Champ Note (modifiable)
            sb.append("<td><input type=\"text\" name=\"note_").append(e.getNumeroIdentifiantEtudiant()).append("\" value=\"").append(echapperHtml(noteValue)).append("\" class=\"note-input\" placeholder=\"Note\"></td>");
            // Coefficient (lecture seule)
            sb.append("<td><input type=\"text\" name=\"coef_").append(e.getNumeroIdentifiantEtudiant()).append("\" value=\"").append(echapperHtml(coefficient)).append("\" class=\"note-input-readonly\" readonly></td>");
            // Note de base (lecture seule)
            sb.append("<td><input type=\"text\" name=\"base_").append(e.getNumeroIdentifiantEtudiant()).append("\" value=\"").append(echapperHtml(noteBase)).append("\" class=\"note-input-readonly\" readonly></td>");
            // Note sur (lecture seule)
            sb.append("<td><input type=\"text\" name=\"sur_").append(e.getNumeroIdentifiantEtudiant()).append("\" value=\"").append(echapperHtml(noteSur)).append("\" class=\"note-input-readonly\" readonly></td>");
            sb.append("<td>").append(rang).append("</td>");
            sb.append("</tr>");
        }

        sb.append("</tbody>");
        sb.append("</table>");
        sb.append("</div>");
        sb.append("<div style=\"text-align:center;\">");
        sb.append("<button type=\"submit\" class=\"btn-save\"><i class=\"fas fa-save\"></i> Enregistrer toutes les notes</button>");
        sb.append("<a href=\"/professeur/notes\" class=\"btn-cancel\"><i class=\"fas fa-undo\"></i> Réinitialiser</a>");
        sb.append("</div>");
        sb.append("</form>");
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