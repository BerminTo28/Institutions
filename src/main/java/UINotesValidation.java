import java.time.Year;
import java.util.List;

public class UINotesValidation {

    public static String render(List<String> annees,
                                List<String> periodes,
                                List<String> classes,
                                List<String> matieres,
                                String anneeSel,
                                String periodeSel,
                                String classeSel,
                                String matiereSel,
                                List<Note> notes,
                                String msgType,
                                String msg) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>");
        sb.append("<html lang=\"fr\">");
        sb.append("<head>");
        sb.append("<meta charset=\"UTF-8\">");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        sb.append("<title>Validation des notes</title>");
        sb.append("<link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css\">");
        sb.append("<link href=\"https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@300;400;500;600;700;800&display=swap\" rel=\"stylesheet\">");
        sb.append("<style>");
        sb.append(renderStyles());
        sb.append("</style>");
        sb.append("</head>");
        sb.append("<body>");
        sb.append("<div class=\"container\">");

        sb.append(renderHeader());

        if (msg != null && msgType != null) {
            sb.append("<div class=\"message ").append(msgType).append("\"><i class=\"fas fa-").append(msgType.equals("success") ? "check-circle" : "exclamation-triangle").append("\"></i> ").append(escapeHtml(msg)).append("</div>");
        }

        // Filtres
        sb.append("<div class=\"card\">");
        sb.append("<div class=\"card-title\"><i class=\"fas fa-filter\"></i> Filtres</div>");
        sb.append("<form method=\"GET\" action=\"/admin/validation-notes\" class=\"filter-form\">");
        sb.append("<div class=\"form-row\">");
        sb.append("<div class=\"form-group\"><label>Année</label><select name=\"annee\">");
        sb.append("<option value=\"\">Toutes</option>");
        for (String a : annees) {
            String sel = a.equals(anneeSel) ? "selected" : "";
            sb.append("<option value=\"").append(escapeHtml(a)).append("\" ").append(sel).append(">").append(escapeHtml(a)).append("</option>");
        }
        sb.append("</select></div>");
        sb.append("<div class=\"form-group\"><label>Période</label><select name=\"periode\">");
        sb.append("<option value=\"\">Toutes</option>");
        for (String p : periodes) {
            String sel = p.equals(periodeSel) ? "selected" : "";
            sb.append("<option value=\"").append(escapeHtml(p)).append("\" ").append(sel).append(">").append(escapeHtml(p)).append("</option>");
        }
        sb.append("</select></div>");
        sb.append("<div class=\"form-group\"><label>Classe</label><select name=\"classe\">");
        sb.append("<option value=\"\">Toutes</option>");
        for (String c : classes) {
            String sel = c.equals(classeSel) ? "selected" : "";
            sb.append("<option value=\"").append(escapeHtml(c)).append("\" ").append(sel).append(">").append(escapeHtml(c)).append("</option>");
        }
        sb.append("</select></div>");
        sb.append("<div class=\"form-group\"><label>Matière</label><select name=\"matiere\">");
        sb.append("<option value=\"\">Toutes</option>");
        for (String m : matieres) {
            String sel = m.equals(matiereSel) ? "selected" : "";
            sb.append("<option value=\"").append(escapeHtml(m)).append("\" ").append(sel).append(">").append(escapeHtml(m)).append("</option>");
        }
        sb.append("</select></div>");
        sb.append("</div>");
        sb.append("<button type=\"submit\" class=\"btn-filter\"><i class=\"fas fa-search\"></i> Filtrer</button>");
        sb.append("</form>");
        sb.append("</div>");

        // Liste des notes
        sb.append("<div class=\"card\">");
        sb.append("<div class=\"card-title\"><i class=\"fas fa-list\"></i> Notes en attente de validation</div>");
        if (notes == null || notes.isEmpty()) {
            sb.append("<div class=\"empty-state\"><i class=\"fas fa-check-circle\"></i><p>Aucune note en attente de validation.</p></div>");
        } else {
            sb.append("<div class=\"table-wrapper\">");
            sb.append("<table>");
            sb.append("<thead><tr>");
            sb.append("<th>Étudiant</th><th>Matière</th><th>Classe</th><th>Période</th>");
            sb.append("<th>Note</th><th>Professeur</th><th>Actions</th>");
            sb.append("</tr></thead>");
            sb.append("<tbody>");
            for (Note n : notes) {
                sb.append("<tr>");
                sb.append("<td>").append(escapeHtml(n.getEtudiantNom())).append(" ").append(escapeHtml(n.getEtudiantPrenom())).append("</td>");
                sb.append("<td>").append(escapeHtml(n.getMatiereNom())).append("</td>");
                sb.append("<td>").append(escapeHtml(n.getClasse())).append("</td>");
                sb.append("<td>").append(escapeHtml(n.getPeriode())).append("</td>");
                sb.append("<td><strong>").append(escapeHtml(n.getNoteValue())).append(" / ").append(escapeHtml(n.getNoteSur())).append("</strong></td>");
                sb.append("<td>").append(escapeHtml(n.getEnseignantNom())).append(" ").append(escapeHtml(n.getEnseignantPrenom())).append("</td>");
                sb.append("<td>");
                sb.append("<button class=\"btn-success btn-sm\" onclick=\"validerNote(").append(n.getNoteId()).append(")\"><i class=\"fas fa-check\"></i> Valider</button> ");
                sb.append("<button class=\"btn-danger btn-sm\" onclick=\"rejeterNote(").append(n.getNoteId()).append(")\"><i class=\"fas fa-times\"></i> Rejeter</button>");
                sb.append("</td>");
                sb.append("</tr>");
            }
            sb.append("</tbody>");
            sb.append("</table>");
            sb.append("</div>");
        }
        sb.append("</div>");

        sb.append("<div class=\"footer\">© ").append(Year.now().getValue()).append(" M-TECH Academy - Tous droits réservés</div>");
        sb.append("</div>");

        // JavaScript
        sb.append("""
        <script>
            async function validerNote(id) {
                if (!confirm('Valider cette note ?')) return;
                const formData = new URLSearchParams();
                formData.append('id', id);
                try {
                    const resp = await fetch('/admin/validation-notes/api/valider', {
                        method: 'POST',
                        body: formData
                    });
                    const data = await resp.json();
                    if (data.success) {
                        alert('Note validée avec succès !');
                        location.reload();
                    } else {
                        alert('Erreur : ' + data.message);
                    }
                } catch (e) {
                    alert('Erreur réseau');
                }
            }

            async function rejeterNote(id) {
                if (!confirm('Rejeter cette note ?')) return;
                const formData = new URLSearchParams();
                formData.append('id', id);
                try {
                    const resp = await fetch('/admin/validation-notes/api/rejeter', {
                        method: 'POST',
                        body: formData
                    });
                    const data = await resp.json();
                    if (data.success) {
                        alert('Note rejetée.');
                        location.reload();
                    } else {
                        alert('Erreur : ' + data.message);
                    }
                } catch (e) {
                    alert('Erreur réseau');
                }
            }
        </script>
        """);

        sb.append("</body>");
        sb.append("</html>");
        return sb.toString();
    }

    public static String renderError(String message) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>");
        sb.append("<html>");
        sb.append("<head><meta charset=\"UTF-8\"><title>Erreur</title>");
        sb.append("<link href=\"https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;600&display=swap\" rel=\"stylesheet\">");
        sb.append("<style>");
        sb.append("*{margin:0;padding:0;box-sizing:border-box;font-family:'Plus Jakarta Sans',sans-serif;}");
        sb.append("body{background:#f4f6f9;display:flex;justify-content:center;align-items:center;height:100vh;}");
        sb.append(".error-container{background:white;padding:40px;border-radius:16px;text-align:center;max-width:500px;}");
        sb.append(".error-container i{font-size:64px;color:#dc2626;margin-bottom:20px;}");
        sb.append(".error-container h1{color:#1e293b;margin-bottom:10px;}");
        sb.append(".error-container p{color:#64748b;}");
        sb.append(".error-container .btn{display:inline-block;margin-top:20px;padding:10px 24px;background:#1e40af;color:white;text-decoration:none;border-radius:8px;}");
        sb.append("</style>");
        sb.append("</head>");
        sb.append("<body>");
        sb.append("<div class=\"error-container\">");
        sb.append("<i class=\"fas fa-exclamation-triangle\"></i>");
        sb.append("<h1>Erreur</h1>");
        sb.append("<p>").append(escapeHtml(message)).append("</p>");
        sb.append("<a href=\"/admin/validation-notes\" class=\"btn\"><i class=\"fas fa-arrow-left\"></i> Retour</a>");
        sb.append("</div>");
        sb.append("</body>");
        sb.append("</html>");
        return sb.toString();
    }

    private static String renderStyles() {
        return """
        *{margin:0;padding:0;box-sizing:border-box;}
        body{font-family:'Plus Jakarta Sans',sans-serif;background:#f0f4f8;min-height:100vh;padding:20px;display:flex;justify-content:center;align-items:center;}
        .container{max-width:1200px;width:100%;margin:0 auto;}

        .header{background:linear-gradient(135deg,#1e293b,#0f172a);color:white;padding:25px 30px;border-radius:16px;text-align:center;margin-bottom:25px;position:relative;overflow:hidden;}
        .header h1{font-size:24px;font-weight:700;margin-top:10px;position:relative;z-index:1;}
        .header h1 i{color:#3b82f6;margin-right:10px;}
        .header-nav{display:flex;justify-content:center;gap:20px;margin-top:12px;flex-wrap:wrap;position:relative;z-index:1;}
        .header-nav a{color:#94a3b8;text-decoration:none;font-size:14px;transition:0.2s;padding:4px 12px;border-radius:20px;}
        .header-nav a:hover{color:white;background:rgba(255,255,255,0.1);}
        .header-nav a.active{color:white;background:rgba(255,255,255,0.15);font-weight:600;}

        .message{padding:12px 18px;border-radius:8px;margin-bottom:20px;}
        .message.success{background:#d1fae5;color:#16a34a;border-left:4px solid #16a34a;}
        .message.error{background:#fee2e2;color:#dc2626;border-left:4px solid #dc2626;}

        .card{background:white;border-radius:12px;padding:25px;margin-bottom:25px;border:1px solid #e2e8f0;}
        .card-title{font-size:18px;font-weight:600;color:#1e40af;margin-bottom:20px;border-bottom:2px solid #1e40af;padding-bottom:8px;display:inline-block;}

        .filter-form .form-row{display:flex;gap:15px;flex-wrap:wrap;margin-bottom:15px;}
        .filter-form .form-group{flex:1;min-width:150px;}
        .filter-form label{display:block;font-weight:600;color:#475569;margin-bottom:4px;font-size:13px;}
        .filter-form select{width:100%;padding:8px 12px;border:1px solid #cbd5e1;border-radius:8px;font-size:14px;background:white;}
        .btn-filter{background:#1e40af;color:white;border:none;padding:8px 24px;border-radius:8px;cursor:pointer;font-weight:600;transition:0.2s;}
        .btn-filter:hover{background:#1e3a8a;}

        .table-wrapper{overflow-x:auto;}
        table{width:100%;border-collapse:collapse;font-size:14px;}
        th,td{padding:10px 12px;text-align:left;border-bottom:1px solid #e2e8f0;}
        th{background:#f8fafc;font-weight:600;color:#475569;}
        .btn-sm{padding:4px 10px;border:none;border-radius:4px;cursor:pointer;font-size:12px;font-weight:600;transition:0.2s;}
        .btn-success{background:#16a34a;color:white;}
        .btn-success:hover{background:#15803d;}
        .btn-danger{background:#dc2626;color:white;}
        .btn-danger:hover{background:#b91c1c;}

        .empty-state{text-align:center;padding:40px;}
        .empty-state i{font-size:48px;color:#94a3b8;margin-bottom:12px;}
        .empty-state p{color:#64748b;font-size:16px;}

        .footer{margin-top:30px;text-align:center;color:#94a3b8;font-size:13px;}
        @media (max-width:768px){.header-nav{flex-direction:column;gap:8px;}.filter-form .form-row{flex-direction:column;}}
        """;
    }

    private static String renderHeader() {
        return """
        <div class="header">
            <h1><i class="fas fa-check-double"></i> Validation des notes</h1>
            <div class="header-nav">
                <a href="/admin/dashboard"><i class="fas fa-home"></i> Dashboard</a>
                <a href="/admin/notes"><i class="fas fa-file-pen"></i> Notes</a>
                <a href="/admin/validation-notes" class="active"><i class="fas fa-check-double"></i> Validation</a>
                <a href="/logout"><i class="fas fa-sign-out-alt"></i> Déconnexion</a>
            </div>
        </div>
        """;
    }

    private static String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }
}