import java.time.Year;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UIParametresInscription {

    public static String render(String institutionId,
                                List<AnneeAcademique> annees,
                                List<Periode> periodes,
                                List<Classe> classes,
                                List<ParametreInscription> parametres,
                                String anneeSel,
                                String periodeSel,
                                String msgType,
                                String msg) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>");
        sb.append("<html lang=\"fr\">");
        sb.append("<head>");
        sb.append("<meta charset=\"UTF-8\">");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        sb.append("<title>Paramètres d'inscription</title>");
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

        // ===== FILTRES =====
        sb.append("<div class=\"card\">");
        sb.append("<div class=\"card-title\"><i class=\"fas fa-filter\"></i> Sélectionnez l'année et la période</div>");
        sb.append("<form method=\"GET\" action=\"/admin/parametres/inscription\" class=\"filter-form\">");
        sb.append("<div class=\"form-row\">");
        sb.append("<div class=\"form-group\"><label>Année académique</label>");
        sb.append("<select name=\"annee\" id=\"annee\" onchange=\"this.form.submit()\">");
        sb.append("<option value=\"\">-- Sélectionner --</option>");
        for (AnneeAcademique a : annees) {
            String sel = a.getAnneeAcademique().equals(anneeSel) ? "selected" : "";
            sb.append("<option value=\"").append(escapeHtml(a.getAnneeAcademique())).append("\" ").append(sel).append(">").append(escapeHtml(a.getAnneeAcademique())).append("</option>");
        }
        sb.append("</select></div>");
        sb.append("<div class=\"form-group\"><label>Période</label>");
        sb.append("<select name=\"periode\" id=\"periode\" onchange=\"this.form.submit()\">");
        sb.append("<option value=\"\">-- Sélectionner --</option>");
        for (Periode p : periodes) {
            String sel = p.getPeriode().equals(periodeSel) ? "selected" : "";
            sb.append("<option value=\"").append(escapeHtml(p.getPeriode())).append("\" ").append(sel).append(">").append(escapeHtml(p.getPeriode())).append("</option>");
        }
        sb.append("</select></div>");
        sb.append("</div>");
        sb.append("<button type=\"submit\" class=\"btn-filter\"><i class=\"fas fa-search\"></i> Charger</button>");
        sb.append("</form>");
        sb.append("</div>");

        // ===== TABLEAU DES INSCRIPTIONS =====
        if (!anneeSel.isBlank() && !periodeSel.isBlank()) {
            sb.append("<div class=\"card\">");
            sb.append("<div class=\"card-title\"><i class=\"fas fa-check-double\"></i> Configurer les inscriptions</div>");
            sb.append("<p style=\"color:#64748b; margin-bottom:15px;\">Cochez les classes pour lesquelles les inscriptions sont autorisées pour l'année <strong>").append(escapeHtml(anneeSel)).append("</strong> et la période <strong>").append(escapeHtml(periodeSel)).append("</strong>.</p>");
            sb.append("<form method=\"POST\" action=\"/admin/parametres/inscription\">");
            sb.append("<input type=\"hidden\" name=\"annee\" value=\"").append(escapeHtml(anneeSel)).append("\">");
            sb.append("<input type=\"hidden\" name=\"periode\" value=\"").append(escapeHtml(periodeSel)).append("\">");
            sb.append("<table>");
            sb.append("<thead><tr>");
            sb.append("<th>Classe</th>");
            sb.append("<th>Promotion</th>"); // ✅ Nouvelle colonne Promotion
            sb.append("<th>Code</th>");
            sb.append("<th>Niveau</th>");
            sb.append("<th>Capacité</th>");
            sb.append("<th>Inscriptions autorisées</th>");
            sb.append("</tr></thead>");
            sb.append("<tbody>");
            // Construire une map pour un accès rapide
            Map<String, Boolean> actifMap = new HashMap<>();
            if (parametres != null) {
                for (ParametreInscription p : parametres) {
                    actifMap.put(p.getClasse(), p.isActif());
                }
            }
            for (Classe c : classes) {
                boolean actif = actifMap.getOrDefault(c.getNomClasse(), false);
                String checkboxId = "inscription_" + c.getNomClasse().replaceAll("\\s+", "_");
                sb.append("<tr>");
                sb.append("<td><strong>").append(escapeHtml(c.getNomClasse())).append("</strong></td>");
                sb.append("<td>").append(escapeHtml(c.getPromotion())).append("</td>"); // ✅ Promotion
                sb.append("<td>").append(escapeHtml(c.getCodeClasse())).append("</td>");
                sb.append("<td>").append(c.getNiveauClasse()).append("</td>");
                sb.append("<td>").append(c.getCapaciteClasse()).append("</td>");
                sb.append("<td><label class=\"toggle\"><input type=\"checkbox\" name=\"").append(checkboxId).append("\" ").append(actif ? "checked" : "").append("><span class=\"slider\"></span></label></td>");
                sb.append("</tr>");
            }
            sb.append("</tbody>");
            sb.append("</table>");
            sb.append("<button type=\"submit\" class=\"btn-submit\"><i class=\"fas fa-save\"></i> Enregistrer les modifications</button>");
            sb.append("</form>");
            sb.append("</div>");
        } else {
            sb.append("<div class=\"empty-state\"><i class=\"fas fa-info-circle\"></i><p>Sélectionnez une année et une période pour configurer les inscriptions.</p></div>");
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
        sb.append("<p>").append(escapeHtml(message)).append("</p>");
        sb.append("<a href=\"/admin/parametres/inscription\" class=\"btn\"><i class=\"fas fa-arrow-left\"></i> Retour</a>");
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
        .filter-form .form-group{flex:1;min-width:200px;}
        .filter-form label{display:block;font-weight:600;color:#475569;margin-bottom:4px;font-size:13px;}
        .filter-form select{width:100%;padding:8px 12px;border:1px solid #cbd5e1;border-radius:8px;font-size:14px;background:white;}
        .btn-filter{background:#1e40af;color:white;border:none;padding:8px 24px;border-radius:8px;cursor:pointer;font-weight:600;transition:0.2s;}
        .btn-filter:hover{background:#1e3a8a;}

        table{width:100%;border-collapse:collapse;font-size:14px;}
        th,td{padding:10px 12px;text-align:left;border-bottom:1px solid #e2e8f0;}
        th{background:#f8fafc;font-weight:600;color:#475569;}

        /* Toggle switch */
        .toggle{position:relative;display:inline-block;width:50px;height:26px;}
        .toggle input{opacity:0;width:0;height:0;}
        .slider{position:absolute;cursor:pointer;top:0;left:0;right:0;bottom:0;background:#cbd5e1;transition:0.4s;border-radius:26px;}
        .slider:before{position:absolute;content:"";height:20px;width:20px;left:3px;bottom:3px;background:white;transition:0.4s;border-radius:50%;}
        .toggle input:checked + .slider{background:#22c55e;}
        .toggle input:checked + .slider:before{transform:translateX(24px);}

        .btn-submit{background:#16a34a;color:white;border:none;padding:12px 32px;border-radius:8px;font-weight:600;font-size:16px;cursor:pointer;transition:0.2s;display:inline-flex;align-items:center;gap:10px;margin-top:20px;}
        .btn-submit:hover{background:#15803d;}

        .empty-state{text-align:center;padding:60px 20px;}
        .empty-state i{font-size:48px;color:#94a3b8;margin-bottom:12px;}
        .empty-state p{color:#64748b;font-size:16px;}

        .footer{margin-top:30px;text-align:center;color:#94a3b8;font-size:13px;}
        @media (max-width:768px){.header-nav{flex-direction:column;gap:8px;}.filter-form .form-row{flex-direction:column;}}
        """;
    }

    private static String renderHeader() {
        return """
        <div class="header">
            <h1><i class="fas fa-door-open"></i> Paramètres d'inscription</h1>
            <div class="header-nav">
                <a href="/admin/dashboard"><i class="fas fa-home"></i> Dashboard</a>
                <a href="/admin/parametres"><i class="fas fa-gear"></i> Paramètres</a>
                <a href="/admin/parametres/inscription" class="active"><i class="fas fa-door-open"></i> Inscriptions</a>
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