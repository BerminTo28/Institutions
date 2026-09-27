import java.time.Year;
import java.util.List;
import java.util.Map;

public class UIEmploiduTempsEtudiant {

    public static String render(String classe,
                                List<String> annees,
                                List<String> periodes,
                                List<String> jours,
                                String anneeSel,
                                String periodeSel,
                                String jourSel,
                                List<Map<String, Object>> creneaux,
                                boolean showResults,
                                String message) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>");
        sb.append("<html lang=\"fr\">");
        sb.append("<head>");
        sb.append("<meta charset=\"UTF-8\">");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        sb.append("<title>Mon Emploi du Temps</title>");
        sb.append("<link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css\">");
        sb.append("<link href=\"https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@300;400;500;600;700;800&display=swap\" rel=\"stylesheet\">");
        sb.append("<style>");
        sb.append(renderStyles());
        sb.append("</style>");
        sb.append("</head>");
        sb.append("<body>");
        sb.append("<div class=\"container\">");

        sb.append(renderHeader());
        sb.append(renderFilters(annees, periodes, jours, anneeSel, periodeSel, jourSel));

        // Information classe
        sb.append("<div class=\"info-box\">");
        sb.append("<i class=\"fas fa-info-circle\"></i> Classe : <strong>").append(escapeHtml(classe)).append("</strong>");
        if (anneeSel != null && !anneeSel.isEmpty()) {
            sb.append(" | Année : <strong>").append(escapeHtml(anneeSel)).append("</strong>");
        }
        if (periodeSel != null && !periodeSel.isEmpty()) {
            sb.append(" | Période : <strong>").append(escapeHtml(periodeSel)).append("</strong>");
        }
        if (jourSel != null && !jourSel.isEmpty()) {
            sb.append(" | Jour : <strong>").append(escapeHtml(jourSel)).append("</strong>");
        }
        sb.append("</div>");

        // Affichage des résultats
        if (!showResults) {
            sb.append("<div class=\"empty-state\">");
            sb.append("<i class=\"fas fa-calendar-alt\"></i>");
            if (message != null) {
                sb.append("<p>").append(escapeHtml(message)).append("</p>");
            } else {
                sb.append("<p>Sélectionnez une année et une période, puis cliquez sur \"Filtrer\" pour voir votre emploi du temps.</p>");
            }
            sb.append("</div>");
        } else {
            if (creneaux == null || creneaux.isEmpty()) {
                sb.append("<div class=\"empty-state\">");
                sb.append("<i class=\"fas fa-calendar-times\"></i>");
                sb.append("<p>").append(escapeHtml(message != null ? message : "Aucun cours trouvé.")).append("</p>");
                sb.append("</div>");
            } else {
                sb.append("<div class=\"card\">");
                sb.append("<div class=\"card-title\"><i class=\"fas fa-table\"></i> Emploi du temps</div>");
                sb.append("<div class=\"table-wrapper\">");
                sb.append("<table>");
                sb.append("<thead><tr>");
                sb.append("<th>Jour</th>");
                sb.append("<th>Horaire</th>");
                sb.append("<th>Matière</th>");
                sb.append("<th>Coefficient</th>");
                sb.append("<th>Note minimale</th>");
                sb.append("<th>Professeur</th>");
                sb.append("</tr></thead>");
                sb.append("<tbody>");

                for (Map<String, Object> c : creneaux) {
                    String jour = (String) c.get("jour");
                    String heureDebut = (String) c.get("heureDebut");
                    String heureFin = (String) c.get("heureFin");
                    String nomMatiere = (String) c.get("nomMatiere");
                    double coefficient = (double) c.get("coefficient");
                    double noteMin = (double) c.get("noteMinimale");
                    String profNom = (String) c.get("professeurNom");
                    String profPrenom = (String) c.get("professeurPrenom");

                    String horaire = "";
                    if (heureDebut != null && heureFin != null) {
                        horaire = heureDebut.substring(0, Math.min(5, heureDebut.length())) + " - " +
                                  heureFin.substring(0, Math.min(5, heureFin.length()));
                    } else if (heureDebut != null) {
                        horaire = heureDebut;
                    }

                    String profComplet = (profPrenom != null ? profPrenom : "") + " " + (profNom != null ? profNom : "");
                    if (profComplet.trim().isEmpty()) profComplet = "Non attribué";

                    sb.append("<tr>");
                    sb.append("<td>").append(escapeHtml(jour)).append("</td>");
                    sb.append("<td>").append(escapeHtml(horaire)).append("</td>");
                    sb.append("<td><strong>").append(escapeHtml(nomMatiere)).append("</strong></td>");
                    sb.append("<td>").append(String.format("%.2f", coefficient)).append("</td>");
                    sb.append("<td>").append(String.format("%.2f", noteMin)).append("</td>");
                    sb.append("<td>").append(escapeHtml(profComplet)).append("</td>");
                    sb.append("</tr>");
                }

                sb.append("</tbody>");
                sb.append("</table>");
                sb.append("</div>");
                sb.append("</div>");
            }
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
        body{padding:0;display:flex;flex-direction:column;align-items:stretch;}
        .container{width:100%;min-height:100vh;margin:0;background:rgba(255,255,255,0.85);backdrop-filter:blur(10px);border-radius:0;box-shadow:none;border:none;display:flex;flex-direction:column;overflow-x:hidden;}

        .header{background:linear-gradient(135deg,#1e293b,#0f172a);color:white;padding:30px 40px 24px;border-radius:0;text-align:center;margin-bottom:0;position:relative;overflow:hidden;width:100%;box-shadow:none;}
        .header h1{font-size:24px;font-weight:700;margin-top:8px;position:relative;z-index:1;}
        .header h1 i{color:#3b82f6;margin-right:10px;}
        .header-nav{display:flex;justify-content:center;gap:15px;margin-top:12px;flex-wrap:wrap;position:relative;z-index:1;}
        .header-nav a{color:#94a3b8;text-decoration:none;font-size:13px;transition:0.2s;padding:6px 14px;border-radius:20px;background:rgba(255,255,255,0.08);}
        .header-nav a:hover{color:white;background:rgba(255,255,255,0.16);}
        .header-nav a.active{color:white;background:rgba(255,255,255,0.16);font-weight:600;}

        .info-box{background:#eff6ff;padding:15px 25px;border-radius:12px;margin:30px auto 0 auto;border-left:4px solid #3b82f6;width:calc(100% - 80px);max-width:1600px;border:1px solid #e2e8f0;font-size:14px;color:#334155;}
        .info-box i{margin-right:8px;color:#3b82f6;}

        .filters{background:white;border-radius:12px;padding:25px 40px;margin:25px auto 0 auto;border:1px solid #e2e8f0;display:flex;gap:15px;flex-wrap:wrap;align-items:end;width:calc(100% - 80px);max-width:1600px;}
        .filters .form-group{flex:1;min-width:150px;}
        .filters label{display:block;font-weight:600;color:#475569;margin-bottom:4px;font-size:13px;}
        .filters select{width:100%;padding:10px 14px;border:1px solid #cbd5e1;border-radius:8px;font-size:14px;background:white;}
        .filters select:focus{outline:none;border-color:#1e40af;box-shadow:0 0 0 3px rgba(30,64,175,0.1);}
        .filters .btn-filter{background:#1e40af;color:white;border:none;padding:10px 20px;border-radius:8px;cursor:pointer;font-weight:600;transition:0.2s;height:42px;}
        .filters .btn-filter:hover{background:#1e3a8a;}
        .filters .btn-filter i{margin-right:6px;}
        .filters .btn-reset{background:#e2e8f0;color:#475569;border:none;padding:10px 16px;border-radius:8px;cursor:pointer;font-weight:600;transition:0.2s;height:42px;}
        .filters .btn-reset:hover{background:#cbd5e1;}

        .card{background:white;border-radius:12px;padding:30px 40px;margin:25px auto 0 auto;border:1px solid #e2e8f0;width:calc(100% - 80px);max-width:1600px;}
        .card-title{font-size:18px;font-weight:600;color:#1e40af;margin-bottom:20px;border-bottom:2px solid #1e40af;padding-bottom:8px;display:inline-block;}

        .table-wrapper{overflow-x:auto;}
        table{width:100%;border-collapse:collapse;font-size:14px;}
        th,td{padding:10px 12px;text-align:left;border-bottom:1px solid #e2e8f0;}
        th{background:#f8fafc;font-weight:600;color:#475569;white-space:nowrap;}
        tr:hover{background:#f8fafc;}

        .empty-state{text-align:center;padding:40px;background:white;border-radius:12px;border:1px solid #e2e8f0;width:calc(100% - 80px);max-width:1600px;margin:25px auto 0 auto;}
        .empty-state i{font-size:48px;color:#94a3b8;margin-bottom:12px;}
        .empty-state p{color:#64748b;font-size:16px;}

        .footer{margin-top:auto;text-align:center;color:#64748b;font-size:13px;border-top:1px solid rgba(0,0,0,0.06);padding:25px 0 15px;width:100%;}
        @media (max-width:768px){.header-nav{flex-direction:column;gap:8px;}.filters{flex-direction:column;}.card, .info-box, .filters, .empty-state{width:calc(100% - 40px);margin-left:20px;margin-right:20px;padding:20px;}}
        """;
    }

    private static String renderHeader() {
        return """
        <div class="header">
            <h1><i class="fas fa-calendar-alt"></i> Mon Emploi du Temps</h1>
            <div class="header-nav">
                <a href="/etudiant/dashboard"><i class="fas fa-home"></i> Dashboard</a>
                <a href="/etudiant/profil"><i class="fas fa-user"></i> Mon Profil</a>
                <a href="/etudiant/notes"><i class="fas fa-book"></i> Mes Notes</a>
                <a href="/etudiant/bulletins"><i class="fas fa-file-pdf"></i> Bulletins</a>
                <a href="/etudiant/paiements"><i class="fas fa-coins"></i> Paiements</a>
                <a href="/etudiant/edt" class="active"><i class="fas fa-calendar-alt"></i> Emploi du temps</a>
                <a href="/logout"><i class="fas fa-sign-out-alt"></i> Déconnexion</a>
            </div>
        </div>
        """;
    }

    private static String renderFilters(List<String> annees, List<String> periodes, List<String> jours,
                                        String anneeSel, String periodeSel, String jourSel) {
        StringBuilder sb = new StringBuilder();
        
        // Formulaire avec méthode GET
      sb.append("<form method=\"GET\" action=\"/etudiant/edt\" class=\"filters\" id=\"filterForm\">");
        
        // Champ caché pour l'action
        sb.append("<input type=\"hidden\" name=\"action\" value=\"filter\">");
        
        // Année
        sb.append("<div class=\"form-group\">");
        sb.append("<label for=\"annee\"><i class=\"fas fa-calendar\"></i> Année académique</label>");
        sb.append("<select name=\"annee\" id=\"annee\">");
        sb.append("<option value=\"\">-- Sélectionner --</option>");
        if (annees != null) {
            for (String a : annees) {
                String selected = a.equals(anneeSel) ? "selected" : "";
                sb.append("<option value=\"").append(escapeHtml(a)).append("\" ").append(selected).append(">").append(escapeHtml(a)).append("</option>");
            }
        }
        sb.append("</select>");
        sb.append("</div>");

        // Période
        sb.append("<div class=\"form-group\">");
        sb.append("<label for=\"periode\"><i class=\"fas fa-clock\"></i> Période</label>");
        sb.append("<select name=\"periode\" id=\"periode\">");
        sb.append("<option value=\"\">-- Sélectionner --</option>");
        if (periodes != null) {
            for (String p : periodes) {
                String selected = p.equals(periodeSel) ? "selected" : "";
                sb.append("<option value=\"").append(escapeHtml(p)).append("\" ").append(selected).append(">").append(escapeHtml(p)).append("</option>");
            }
        }
        sb.append("</select>");
        sb.append("</div>");

        // Jour
        sb.append("<div class=\"form-group\">");
        sb.append("<label for=\"jour\"><i class=\"fas fa-day\"></i> Jour</label>");
        sb.append("<select name=\"jour\" id=\"jour\">");
        sb.append("<option value=\"\">-- Tous --</option>");
        if (jours != null) {
            for (String j : jours) {
                String selected = j.equals(jourSel) ? "selected" : "";
                sb.append("<option value=\"").append(escapeHtml(j)).append("\" ").append(selected).append(">").append(escapeHtml(j)).append("</option>");
            }
        }
        sb.append("</select>");
        sb.append("</div>");

        // Boutons
        sb.append("<div class=\"form-group\" style=\"flex:0.5;min-width:auto;display:flex;gap:8px;\">");
        sb.append("<button type=\"submit\" class=\"btn-filter\"><i class=\"fas fa-filter\"></i> Filtrer</button>");
        sb.append("<button type=\"button\" class=\"btn-reset\" onclick=\"resetFilters()\"><i class=\"fas fa-undo\"></i></button>");
        sb.append("</div>");
        
        sb.append("</form>");

        // JavaScript pour la réinitialisation et validation
        sb.append("""
            <script>
                function resetFilters() {
                    document.getElementById('annee').value = '';
                    document.getElementById('periode').value = '';
                    document.getElementById('jour').value = '';
                    document.getElementById('filterForm').submit();
                }
                
                // Validation avant soumission
                document.getElementById('filterForm').addEventListener('submit', function(e) {
                    const annee = document.getElementById('annee').value;
                    const periode = document.getElementById('periode').value;
                    
                    if (!annee) {
                        e.preventDefault();
                        alert('Veuillez sélectionner une année académique.');
                        return false;
                    }
                    if (!periode) {
                        e.preventDefault();
                        alert('Veuillez sélectionner une période.');
                        return false;
                    }
                    return true;
                });
            </script>
            """);

        return sb.toString();
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