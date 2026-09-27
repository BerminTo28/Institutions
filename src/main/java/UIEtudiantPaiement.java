import java.time.Year;
import java.util.List;
import java.util.Map;

public class UIEtudiantPaiement {

    public static String render(Etudiant etudiant,
                                String annee,
                                double fraisTotal,
                                Map<Integer, Double> modalites,
                                double totalPaye,
                                double reste,
                                List<Map<String, Object>> paiements) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>");
        sb.append("<html lang=\"fr\">");
        sb.append("<head>");
        sb.append("<meta charset=\"UTF-8\">");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        sb.append("<title>Mes Paiements</title>");
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

        // Cartes de synthèse
        sb.append("<div class=\"stats-grid\">");
        sb.append("<div class=\"stat-card\"><div class=\"stat-label\">Frais totaux</div><div class=\"stat-value\">").append(formatNumber(fraisTotal)).append(" Gdes</div></div>");
        sb.append("<div class=\"stat-card\"><div class=\"stat-label\">Déjà payé</div><div class=\"stat-value positive\">").append(formatNumber(totalPaye)).append(" Gdes</div></div>");
        sb.append("<div class=\"stat-card\"><div class=\"stat-label\">Reste à payer</div><div class=\"stat-value ").append(reste > 0 ? "negative" : "positive").append("\">").append(formatNumber(reste)).append(" Gdes</div></div>");
        sb.append("</div>");

        // Modalités de paiement
        if (modalites != null && !modalites.isEmpty()) {
            sb.append("<div class=\"card\">");
            sb.append("<div class=\"card-title\"><i class=\"fas fa-list-ol\"></i> Modalités de paiement</div>");
            sb.append("<table>");
            sb.append("<thead><tr><th>Versement</th><th>Montant (Gdes)</th></tr></thead>");
            sb.append("<tbody>");
            for (Map.Entry<Integer, Double> entry : modalites.entrySet()) {
                sb.append("<tr><td>Versement n°").append(entry.getKey()).append("</td><td>").append(formatNumber(entry.getValue())).append(" Gdes</td></tr>");
            }
            sb.append("</tbody>");
            sb.append("</table>");
            sb.append("</div>");
        }

        // Formulaire de paiement
        sb.append("<div class=\"card payment-card\">");
        sb.append("<div class=\"card-title\"><i class=\"fas fa-credit-card\"></i> Effectuer un paiement</div>");
        sb.append("<div class=\"payment-form\">");
        sb.append("<div class=\"form-group\">");
        sb.append("<label>Montant à payer (Gdes)</label>");
        sb.append("<input type=\"number\" id=\"montant\" step=\"0.01\" value=\"").append(reste > 0 ? String.format("%.2f", reste) : "0.00").append("\" min=\"0.01\">");
        sb.append("</div>");
        sb.append("<div class=\"form-group\">");
        sb.append("<label>Moyen de paiement</label>");
        sb.append("<select id=\"moyen\">");
        sb.append("<option value=\"BNC\">BNC</option>");
        sb.append("<option value=\"BRH\">BRH</option>");
        sb.append("<option value=\"SOGESOL\">SOGESOL</option>");
        sb.append("<option value=\"UNIBANK\">UNIBANK</option>");
        sb.append("<option value=\"MonCash\">MonCash</option>");
        sb.append("<option value=\"Carte\">Carte</option>");
        sb.append("</select>");
        sb.append("</div>");
        sb.append("<button class=\"btn-pay\" id=\"btnPayer\"><i class=\"fas fa-money-bill-wave\"></i> Payer maintenant</button>");
        sb.append("</div>");
        sb.append("</div>");

        // Historique des paiements
        sb.append("<div class=\"card\">");
        sb.append("<div class=\"card-title\"><i class=\"fas fa-history\"></i> Historique des paiements</div>");
        if (paiements == null || paiements.isEmpty()) {
            sb.append("<p style=\"text-align:center; color:#94a3b8; padding:20px;\">Aucun paiement enregistré.</p>");
        } else {
            sb.append("<table>");
            sb.append("<thead><tr><th>Date</th><th>Montant</th><th>Moyen</th><th>Référence</th><th>Statut</th></tr></thead>");
            sb.append("<tbody>");
            for (Map<String, Object> p : paiements) {
                sb.append("<tr>");
                sb.append("<td>").append(p.get("date_paiement")).append("</td>");
                sb.append("<td>").append(formatNumber((double) p.get("montant"))).append(" Gdes</td>");
                sb.append("<td>").append(escapeHtml(String.valueOf(p.get("moyen_paiement")))).append("</td>");
                sb.append("<td>").append(escapeHtml(String.valueOf(p.get("reference")))).append("</td>");
                sb.append("<td><span class=\"status ").append("VALIDÉ".equals(p.get("statut")) ? "status-success" : "status-pending").append("\">").append(p.get("statut")).append("</span></td>");
                sb.append("</tr>");
            }
            sb.append("</tbody>");
            sb.append("</table>");
        }
        sb.append("</div>");

        sb.append("<div class=\"footer\">© ").append(Year.now().getValue()).append(" M-TECH Academy - Tous droits réservés</div>");
        sb.append("</div>");

        // JavaScript
        sb.append("""
        <script>
            document.getElementById('btnPayer').addEventListener('click', async function() {
                const montant = document.getElementById('montant').value;
                const moyen = document.getElementById('moyen').value;
                if (!montant || parseFloat(montant) <= 0) {
                    alert('Veuillez entrer un montant valide.');
                    return;
                }
                const formData = new URLSearchParams();
                formData.append('montant', montant);
                formData.append('moyen', moyen);
                try {
                    const resp = await fetch('/etudiant/paiements', {
                        method: 'POST',
                        body: formData
                    });
                    const data = await resp.json();
                    if (data.success) {
                        alert('✅ Paiement enregistré avec succès !\\nRéférence: ' + data.reference);
                        location.reload();
                    } else {
                        alert('❌ Erreur: ' + data.message);
                    }
                } catch (e) {
                    alert('Erreur réseau: ' + e.message);
                }
            });
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

        .stats-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:20px;margin:30px auto 0 auto;max-width:1600px;width:calc(100% - 80px);}
        .stat-card{background:white;border-radius:12px;padding:20px;text-align:center;border:1px solid #e2e8f0;}
        .stat-label{font-size:14px;color:#64748b;margin-bottom:5px;}
        .stat-value{font-size:28px;font-weight:700;color:#1e293b;}
        .stat-value.positive{color:#16a34a;}
        .stat-value.negative{color:#dc2626;}

        .card{background:white;border-radius:12px;padding:30px 40px;border:1px solid #e2e8f0;width:calc(100% - 80px);max-width:1600px;margin:25px auto 0 auto;}
        .card-title{font-size:18px;font-weight:600;color:#1e40af;margin-bottom:20px;border-bottom:2px solid #1e40af;padding-bottom:8px;display:inline-block;}

        table{width:100%;border-collapse:collapse;}
        th{background:#f8fafc;padding:10px 12px;text-align:left;font-weight:600;color:#475569;border-bottom:2px solid #e2e8f0;}
        td{padding:10px 12px;border-bottom:1px solid #e2e8f0;font-size:14px;}
        .status{display:inline-block;padding:4px 12px;border-radius:20px;font-size:12px;font-weight:600;}
        .status-success{background:#d1fae5;color:#16a34a;}
        .status-pending{background:#fef3c7;color:#d97706;}

        .payment-form{display:flex;gap:20px;flex-wrap:wrap;align-items:end;}
        .payment-form .form-group{flex:1;min-width:200px;}
        .payment-form label{display:block;font-weight:600;color:#475569;margin-bottom:4px;font-size:13px;}
        .payment-form input,.payment-form select{width:100%;padding:10px 12px;border:1px solid #cbd5e1;border-radius:8px;font-size:14px;}
        .btn-pay{background:#16a34a;color:white;border:none;padding:12px 30px;border-radius:8px;cursor:pointer;font-weight:600;font-size:16px;transition:0.2s;display:inline-flex;align-items:center;gap:10px;}
        .btn-pay:hover{background:#15803d;}

        .footer{margin-top:auto;text-align:center;color:#64748b;font-size:13px;border-top:1px solid rgba(0,0,0,0.06);padding:25px 0 15px;width:100%;}
        @media (max-width:768px){.stats-grid{grid-template-columns:1fr;width:calc(100% - 40px);margin-left:20px;margin-right:20px;}.payment-form{flex-direction:column;}.card{width:calc(100% - 40px);padding:20px;margin-left:20px;margin-right:20px;}}
        """;
    }

    private static String renderHeader() {
        return """
        <div class="header">
            <h1><i class="fas fa-coins"></i> Mes Paiements</h1>
            <div class="header-nav">
                <a href="/etudiant/dashboard"><i class="fas fa-home"></i> Dashboard</a>
                <a href="/etudiant/profil"><i class="fas fa-user"></i> Mon Profil</a>
                <a href="/etudiant/notes"><i class="fas fa-book"></i> Mes Notes</a>
                <a href="/etudiant/bulletins"><i class="fas fa-file-pdf"></i> Bulletins</a>
                <a href="/etudiant/paiements" class="active"><i class="fas fa-coins"></i> Paiements</a>
                <a href="/etudiant/messages"><i class="fas fa-envelope"></i> Messages</a>
                <a href="/logout"><i class="fas fa-sign-out-alt"></i> Déconnexion</a>
            </div>
        </div>
        """;
    }

    private static String formatNumber(double number) {
        return String.format("%,.2f", number);
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}