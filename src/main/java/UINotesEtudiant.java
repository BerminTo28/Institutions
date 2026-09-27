import java.time.Year;
import java.util.List;

public class UINotesEtudiant {

    public static String render(String numeroEtudiant,
                                List<String> annees,
                                List<String> periodes,
                                String anneeSel,
                                String periodeSel,
                                List<Note> notes) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>");
        sb.append("<html lang=\"fr\">");
        sb.append("<head>");
        sb.append("<meta charset=\"UTF-8\">");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        sb.append("<title>Mes Notes</title>");
        sb.append("<link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css\">");
        sb.append("<link href=\"https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@300;400;500;600;700;800&display=swap\" rel=\"stylesheet\">");
        sb.append("<style>");
        sb.append(renderStyles());
        sb.append("</style>");
        sb.append("</head>");
        sb.append("<body>");
        sb.append("<div class=\"container\">");

        // En-tête
        sb.append(renderHeader());

        // Filtres
        sb.append(renderFilters(annees, periodes, anneeSel, periodeSel));

        // Tableau des notes
        if (notes != null && !notes.isEmpty()) {
            sb.append(renderNotesTable(notes));
        } else if (anneeSel != null && !anneeSel.isEmpty() && periodeSel != null && !periodeSel.isEmpty()) {
            sb.append("<div class=\"empty-state\"><i class=\"fas fa-info-circle\"></i><p>Aucune note trouvée pour cette année et cette période.</p></div>");
        } else {
            sb.append("<div class=\"empty-state\"><i class=\"fas fa-info-circle\"></i><p>Sélectionnez une année et une période pour voir vos notes.</p></div>");
        }

        sb.append("<div class=\"footer\">© ").append(Year.now().getValue()).append(" M-TECH Academy - Tous droits réservés</div>");
        sb.append("</div>");
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
    sb.append("<p>").append(echapperHtml(message)).append("</p>");
    sb.append("<a href=\"/etudiant/dashboard\" class=\"btn\"><i class=\"fas fa-arrow-left\"></i> Retour</a>");
    sb.append("</div>");
    sb.append("</body>");
    sb.append("</html>");
    return sb.toString();
}

    private static String renderStyles() {
        return """
        *{margin:0;padding:0;box-sizing:border-box;}
        html, body { width: 100%; min-height: 100vh; background: linear-gradient(135deg, #f0f4f8 0%, #d9e2ec 100%); font-family:'Plus Jakarta Sans',sans-serif; }
        body{padding:0;display:flex;flex-direction:column;}
        .container{width:100%;min-height:100vh;margin:0;background:rgba(255,255,255,0.85);backdrop-filter:blur(10px);border-radius:0;box-shadow:none;border:none;display:flex;flex-direction:column;overflow-x:hidden;}

        .header{background:linear-gradient(135deg,#1e293b,#0f172a);color:white;padding:30px 40px 24px;border-radius:0;text-align:center;margin-bottom:0;position:relative;overflow:hidden;width:100%;}
        .header h1{font-size:24px;font-weight:700;margin-top:8px;position:relative;z-index:1;}
        .header h1 i{color:#3b82f6;margin-right:10px;}
        .header-nav{display:flex;justify-content:center;gap:15px;margin-top:12px;flex-wrap:wrap;position:relative;z-index:1;}
        .header-nav a{color:#94a3b8;text-decoration:none;font-size:13px;transition:0.2s;padding:6px 14px;border-radius:20px;background:rgba(255,255,255,0.08);}
        .header-nav a:hover{color:white;background:rgba(255,255,255,0.16);}
        .header-nav a.active{color:#f5cd79;background:rgba(255,255,255,0.16);font-weight:600;}

        .filters{background:white;border-radius:12px;padding:25px 30px;margin:30px auto 0 auto;border:1px solid #e2e8f0;display:flex;gap:15px;flex-wrap:wrap;align-items:end;width:calc(100% - 80px);max-width:1600px;}
        .filters .form-group{flex:1;min-width:150px;}
        .filters label{display:block;font-weight:600;color:#475569;margin-bottom:4px;font-size:13px;}
        .filters select{width:100%;padding:8px 12px;border:1px solid #cbd5e1;border-radius:8px;font-size:14px;background:white;}
        .filters .btn-filter{background:#1e40af;color:white;border:none;padding:8px 20px;border-radius:8px;cursor:pointer;font-weight:600;}
        .filters .btn-filter:hover{background:#1e3a8a;}

        .table-wrapper{background:white;border-radius:12px;padding:30px 40px;overflow-x:auto;border:1px solid #e2e8f0;width:calc(100% - 80px);max-width:1600px;margin:25px auto 0 auto;}
        table{width:100%;border-collapse:collapse;font-size:14px;}
        th,td{padding:10px 12px;text-align:center;border-bottom:1px solid #e2e8f0;}
        th{background:#f8fafc;font-weight:600;color:#475569;}
        td:first-child, th:first-child{text-align:left;}
        .mention-admis{color:#16a34a;font-weight:bold;}
        .mention-maintenu{color:#dc2626;font-weight:bold;}

        .empty-state{text-align:center;padding:40px;background:white;border-radius:12px;border:1px solid #e2e8f0;width:calc(100% - 80px);max-width:1600px;margin:25px auto;}
        .empty-state i{font-size:48px;color:#94a3b8;margin-bottom:12px;}
        .empty-state p{color:#64748b;font-size:16px;}

        .footer{margin-top:auto;text-align:center;color:#64748b;font-size:13px;border-top:1px solid rgba(0,0,0,0.06);padding:25px 0 15px;width:100%;}
        @media (max-width:768px){.filters{flex-direction:column;width:calc(100% - 40px);margin-left:20px;margin-right:20px;}.table-wrapper, .empty-state{width:calc(100% - 40px);margin-left:20px;margin-right:20px;padding:20px;}}
        """;
    }

    private static String renderHeader() {
        return """
        <div class="header">
            <h1><i class="fas fa-graduation-cap"></i> Mes Notes</h1>
            <div class="header-nav">
                <a href="/etudiant/dashboard"><i class="fas fa-home"></i> Dashboard</a>
                <a href="/etudiant/profil"><i class="fas fa-user"></i> Mon Profil</a>
                <a href="/etudiant/notes" class="active"><i class="fas fa-book"></i> Mes Notes</a>
                <a href="/etudiant/messages"><i class="fas fa-envelope"></i> Messages</a>
                <a href="/logout"><i class="fas fa-sign-out-alt"></i> Déconnexion</a>
            </div>
        </div>
        """;
    }

    private static String renderFilters(List<String> annees, List<String> periodes, String anneeSel, String periodeSel) {
        StringBuilder sb = new StringBuilder();
        sb.append("<form method=\"GET\" action=\"/etudiant/notes\" class=\"filters\">");
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
        sb.append("<div>");
        sb.append("<button type=\"submit\" class=\"btn-filter\"><i class=\"fas fa-filter\"></i> Voir les notes</button>");
        sb.append("</div>");
        sb.append("</form>");
        return sb.toString();
    }

    private static String renderNotesTable(List<Note> notes) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"table-wrapper\">");
        sb.append("<table>");
        sb.append("<thead><tr>");
        sb.append("<th>Matière</th>");
        sb.append("<th>Note</th>");
        sb.append("<th>Coefficient</th>");
        sb.append("<th>Moyenne (pondérée)</th>");
        sb.append("<th>Rang</th>");
        sb.append("<th>Mention</th>");
        sb.append("</tr></thead>");
        sb.append("<tbody>");

        // Calcul de la moyenne générale pondérée
        double totalPondere = 0;
        double totalCoef = 0;
        for (Note n : notes) {
            try {
                double note = Double.parseDouble(n.getNoteValue());
                double coef = Double.parseDouble(n.getCoefficient());
                totalPondere += note * coef;
                totalCoef += coef;
            } catch (NumberFormatException ignored) {}
        }
        double moyenneGenerale = totalCoef > 0 ? totalPondere / totalCoef : 0;
        String mention = "";
        if (moyenneGenerale >= 16) mention = "Très bien";
        else if (moyenneGenerale >= 14) mention = "Bien";
        else if (moyenneGenerale >= 12) mention = "Assez bien";
        else if (moyenneGenerale >= 10) mention = "Passable";
        else mention = "Insuffisant";

        for (Note n : notes) {
            String noteStr = n.getNoteValue();
            String coef = n.getCoefficient();
            String rang = n.getRangEtudiant() != null ? n.getRangEtudiant() : "-";
            double noteVal = 0;
            try { noteVal = Double.parseDouble(noteStr); } catch (NumberFormatException ignored) {}
            String mentionMat = noteVal >= 10 ? "Admis" : "Maintenu";
            String cls = noteVal >= 10 ? "mention-admis" : "mention-maintenu";

            sb.append("<tr>");
            sb.append("<td>").append(echapperHtml(n.getMatiereNom())).append("</td>");
            sb.append("<td>").append(echapperHtml(noteStr)).append("</td>");
            sb.append("<td>").append(echapperHtml(coef)).append("</td>");
            sb.append("<td>").append(String.format("%.2f", noteVal * Double.parseDouble(coef))).append("</td>");
            sb.append("<td>").append(echapperHtml(rang)).append("</td>");
            sb.append("<td class=\"").append(cls).append("\">").append(mentionMat).append("</td>");
            sb.append("</tr>");
        }

        // Ligne de moyenne générale
        sb.append("<tr style=\"font-weight:bold;background:#f8fafc;\">");
        sb.append("<td colspan=\"2\" style=\"text-align:right;\">Moyenne générale</td>");
        sb.append("<td>").append(String.format("%.2f", moyenneGenerale)).append("</td>");
        sb.append("<td colspan=\"3\" style=\"text-align:center;\">").append(mention).append("</td>");
        sb.append("</tr>");

        sb.append("</tbody>");
        sb.append("</table>");
        sb.append("</div>");
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