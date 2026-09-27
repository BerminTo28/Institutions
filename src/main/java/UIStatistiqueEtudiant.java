import java.util.List;
import java.util.Map;

public class UIStatistiqueEtudiant {

    public static String render(Map<String, Double> moyennes,
                                Map<String, Double> coefficients,
                                List<String> annees,
                                List<String> periodes,
                                String classeSel,
                                String anneeSel,
                                String periodeSel) {

        // ============================================================
        // CALCUL DE LA MOYENNE GÉNÉRALE PONDÉRÉE
        // ============================================================
        double sommeNotesPonderees = 0.0;
        double sommeCoefficients = 0.0;

        if (moyennes != null) {
            for (Map.Entry<String, Double> e : moyennes.entrySet()) {
                double coeff = 1.0;
                if (coefficients != null && coefficients.get(e.getKey()) != null) {
                    coeff = coefficients.get(e.getKey());
                }
                Double valeur = e.getValue();
double note = valeur != null ? valeur : 0.0;
                sommeNotesPonderees += note * coeff;
                sommeCoefficients += coeff;
            }
        }

        double moyenneGenerale = sommeCoefficients > 0
                ? sommeNotesPonderees / sommeCoefficients
                : 0.0;

        // ============================================================
        // PRÉPARATION DES DONNÉES POUR CHART.JS
        // ============================================================
        StringBuilder labels = new StringBuilder("[");
        StringBuilder data = new StringBuilder("[");
        StringBuilder coeffs = new StringBuilder("[");
        StringBuilder colors = new StringBuilder("[");

        String[] palette = {
            "#3b82f6", "#ef4444", "#22c55e", "#f59e0b", "#8b5cf6",
            "#ec4899", "#14b8a6", "#f97316", "#6366f1", "#06b6d4"
        };

        int i = 0;
        if (moyennes != null) {
            for (Map.Entry<String, Double> entry : moyennes.entrySet()) {
                if (i > 0) {
                    labels.append(",");
                    data.append(",");
                    coeffs.append(",");
                    colors.append(",");
                }
                double coeff = 1.0;
                if (coefficients != null && coefficients.get(entry.getKey()) != null) {
                    coeff = coefficients.get(entry.getKey());
                }
                labels.append("\"").append(escapeJs(entry.getKey())).append("\"");
                Double noteObj = entry.getValue();
double noteValue = (noteObj != null) ? noteObj : 0.0;
data.append(noteValue);
                coeffs.append(coeff);
                colors.append("\"").append(palette[i % palette.length]).append("\"");
                i++;
            }
        }
        labels.append("]");
        data.append("]");
        coeffs.append("]");
        colors.append("]");

        // ============================================================
        // HTML
        // ============================================================
        StringBuilder sb = new StringBuilder();

        sb.append("<!DOCTYPE html>");
        sb.append("<html lang=\"fr\">");
        sb.append("<head>");
        sb.append("<meta charset=\"UTF-8\">");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        sb.append("<title>Statistiques des Notes</title>");
        sb.append("<link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css\">");
        sb.append("<link href=\"https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700&display=swap\" rel=\"stylesheet\">");
        sb.append("<script src=\"https://cdn.jsdelivr.net/npm/chart.js\"></script>");
        sb.append("<style>");
        sb.append(renderStyles());
        sb.append("</style>");
        sb.append("</head>");
        sb.append("<body>");
        sb.append("<div class=\"container\">");

        // ---------- En-tête ----------
        sb.append("<div class=\"header\">");
        sb.append("<h2><i class=\"fas fa-chart-pie\"></i> Statistiques des Notes</h2>");
        sb.append("<div class=\"header-actions\">");
        sb.append("<a href=\"/etudiant/dashboard\"><i class=\"fas fa-arrow-left\"></i> Retour</a>");
        sb.append("</div>");
        sb.append("</div>");

        sb.append("<div class=\"body\">");

        // ---------- Filtres ----------
        sb.append("<form method=\"GET\" action=\"/etudiant/statistiques-notes\" class=\"filters\">");

        sb.append("  <div class=\"form-group\">");
        sb.append("    <label>Classe</label>");
        sb.append("    <input type=\"text\" value=\"").append(escapeHtml(classeSel))
          .append("\" readonly class=\"readonly-input\">");
        sb.append("  </div>");

        sb.append("  <div class=\"form-group\">");
        sb.append("    <label>Année académique</label>");
        sb.append("    <select name=\"annee\">");
        sb.append("      <option value=\"\">Toutes</option>");
        if (annees != null) {
            for (String a : annees) {
                String sel = a.equals(anneeSel) ? "selected" : "";
                sb.append("      <option value=\"").append(escapeHtml(a)).append("\" ")
                  .append(sel).append(">").append(escapeHtml(a)).append("</option>");
            }
        }
        sb.append("    </select>");
        sb.append("  </div>");

        sb.append("  <div class=\"form-group\">");
        sb.append("    <label>Période</label>");
        sb.append("    <select name=\"periode\">");
        sb.append("      <option value=\"\">Toutes</option>");
        if (periodes != null) {
            for (String p : periodes) {
                String sel = p.equals(periodeSel) ? "selected" : "";
                sb.append("      <option value=\"").append(escapeHtml(p)).append("\" ")
                  .append(sel).append(">").append(escapeHtml(p)).append("</option>");
            }
        }
        sb.append("    </select>");
        sb.append("  </div>");

        sb.append("  <button type=\"submit\" class=\"btn-filtrer\">")
          .append("<i class=\"fas fa-filter\"></i> Filtrer</button>");
        sb.append("</form>");

        // ---------- Carte : moyenne générale pondérée ----------
        if (moyennes != null && !moyennes.isEmpty()) {
            String couleurMoy = moyenneGenerale >= 10 ? "#22c55e"
                    : (moyenneGenerale >= 8 ? "#f59e0b" : "#ef4444");

            sb.append("<div class=\"moyenne-card\">");
            sb.append("  <div class=\"moyenne-label\">");
            sb.append("    <i class=\"fas fa-trophy\"></i> Moyenne générale pondérée");
            sb.append("  </div>");
            sb.append("  <div class=\"moyenne-value\" style=\"color:").append(couleurMoy).append("\">");
            sb.append(String.format("%.2f", moyenneGenerale)).append(" <span>/ 100</span>");
            sb.append("  </div>");
            sb.append("  <div class=\"moyenne-detail\">");
            sb.append("    Basée sur ").append(moyennes.size()).append(" matière(s)");
            sb.append("  </div>");
            sb.append("</div>");
        }

        // ---------- Graphique ----------
        sb.append("<div class=\"chart-container\">");
        if (moyennes == null || moyennes.isEmpty()) {
            sb.append("<div class=\"empty-message\">");
            sb.append("<i class=\"fas fa-folder-open\"></i>");
            sb.append("<p>Aucune note trouvée pour les filtres sélectionnés.</p>");
            sb.append("</div>");
        } else {
            sb.append("<div class=\"chart-wrapper\">");
            sb.append("<canvas id=\"statsChart\"></canvas>");
            sb.append("</div>");
            sb.append("<div class=\"legend\" id=\"legendContainer\"></div>");
        }
        sb.append("</div>");

        sb.append("<div class=\"footer\">© ").append(java.time.Year.now().getValue())
          .append(" M-TECH Academy</div>");
        sb.append("</div>");
        sb.append("</div>");

        // ============================================================
        // SCRIPT CHART.JS
        // ============================================================
        if (moyennes != null && !moyennes.isEmpty()) {
            sb.append("<script>");
            sb.append("const labels = ").append(labels).append(";");
            sb.append("const dataValues = ").append(data).append(";");
            sb.append("const coeffs = ").append(coeffs).append(";");
            sb.append("const colors = ").append(colors).append(";");

            // ✅ Données pondérées : note × coefficient
            sb.append("const weightedValues = dataValues.map((v, i) => v * coeffs[i]);");
            sb.append("const totalWeighted = weightedValues.reduce((a, b) => a + b, 0);");
            sb.append("const totalCoeffs = coeffs.reduce((a, b) => a + b, 0);");

            sb.append("const ctx = document.getElementById('statsChart').getContext('2d');");

            sb.append("new Chart(ctx, {");
            sb.append("  type: 'doughnut',");
            sb.append("  data: {");
            sb.append("    labels: labels,");
            sb.append("    datasets: [{");
            // ✅ Utilisation des valeurs pondérées
            sb.append("      data: weightedValues,");
            sb.append("      backgroundColor: colors,");
            sb.append("      borderWidth: 2,");
            sb.append("      borderColor: '#ffffff',");
            sb.append("      hoverOffset: 6");
            sb.append("    }]");
            sb.append("  },");
            sb.append("  options: {");
            sb.append("    responsive: true,");
            sb.append("    maintainAspectRatio: true,");
            sb.append("    cutout: '60%',");
            sb.append("    plugins: {");
            sb.append("      legend: { display: false },");
            sb.append("      tooltip: {");
            sb.append("        callbacks: {");
            sb.append("          label: function(context) {");
            sb.append("            const i = context.dataIndex;");
            sb.append("            const note = dataValues[i];");
            sb.append("            const coeff = coeffs[i];");
            sb.append("            const weighted = weightedValues[i];");
            sb.append("            const percent = totalWeighted > 0");
            sb.append("                ? ((weighted / totalWeighted) * 100).toFixed(1)");
            sb.append("                : '0.0';");
            sb.append("            return [");
            sb.append("              ' ' + labels[i],");
            sb.append("              '  Note    : ' + note.toFixed(2) + ' / 100',");
            sb.append("              '  Coeff   : x' + coeff,");
            sb.append("              '  Pondéré : ' + weighted.toFixed(2) + ' / ' + (100 * coeff),");
            sb.append("              '  Poids   : ' + percent + '%'");
            sb.append("            ];");
            sb.append("          }");
            sb.append("        }");
            sb.append("      }");
            sb.append("    }");
            sb.append("  },");
            // Plugin pour afficher les pourcentages pondérés sur les arcs
            sb.append("  plugins: [{");
            sb.append("    id: 'centerPercentages',");
            sb.append("    afterDatasetsDraw(chart) {");
            sb.append("      const {ctx} = chart;");
            sb.append("      ctx.save();");
            sb.append("      const meta = chart.getDatasetMeta(0);");
            sb.append("      meta.data.forEach((element, index) => {");
            sb.append("        const weighted = weightedValues[index];");
            sb.append("        const percent = totalWeighted > 0");
            sb.append("            ? ((weighted / totalWeighted) * 100).toFixed(0) + '%'");
            sb.append("            : '0%';");
            sb.append("        const position = element.tooltipPosition();");
            sb.append("        ctx.font = 'bold 11px \"Plus Jakarta Sans\", sans-serif';");
            sb.append("        ctx.fillStyle = '#ffffff';");
            sb.append("        ctx.textAlign = 'center';");
            sb.append("        ctx.textBaseline = 'middle';");
            sb.append("        if (parseInt(percent) > 5) {");
            sb.append("          ctx.fillText(percent, position.x, position.y);");
            sb.append("        }");
            sb.append("      });");
            sb.append("      ctx.restore();");
            sb.append("    }");
            sb.append("  }]");
            sb.append("});");

            // ---------- Légende personnalisée ----------
            sb.append("const legendContainer = document.getElementById('legendContainer');");
            sb.append("for (let i = 0; i < labels.length; i++) {");
            sb.append("  const item = document.createElement('div');");
            sb.append("  item.className = 'legend-item';");
            sb.append("  const colorBox = document.createElement('span');");
            sb.append("  colorBox.className = 'legend-color';");
            sb.append("  colorBox.style.backgroundColor = colors[i];");
            sb.append("  const percent = totalWeighted > 0");
            sb.append("      ? ((weightedValues[i] / totalWeighted) * 100).toFixed(1)");
            sb.append("      : '0.0';");
            sb.append("  const text = document.createElement('span');");
            sb.append("  text.innerHTML = labels[i]");
            sb.append("    + ' : <strong>' + dataValues[i].toFixed(2) + '/100</strong>'");
            sb.append("    + ' <em>(coeff x' + coeffs[i] + ')</em>'");
            sb.append("    + ' — ' + percent + '%';");
            sb.append("  item.appendChild(colorBox);");
            sb.append("  item.appendChild(text);");
            sb.append("  legendContainer.appendChild(item);");
            sb.append("}");
            sb.append("</script>");
        }

        sb.append("</body>");
        sb.append("</html>");

        return sb.toString();
    }

    // ============================================================
    // STYLES
    // ============================================================
    private static String renderStyles() {
        return """
        * { margin:0; padding:0; box-sizing:border-box; }
        html, body { width: 100%; min-height: 100vh; background: linear-gradient(135deg, #f0f4f8 0%, #d9e2ec 100%); font-family:'Plus Jakarta Sans', sans-serif; }
        body { padding:0; display:flex; flex-direction:column; align-items:stretch; }
        .container { width:100%; min-height:100vh; margin:0; background:rgba(255,255,255,0.85); backdrop-filter:blur(10px); display:flex; flex-direction:column; overflow-x:hidden; }
        .header { background: #ffffff; padding: 25px 40px 15px; border-bottom: 1px solid #f1f5f9; display: flex; justify-content: space-between; align-items: center; width: 100%; }
        .header h2 { color: #0f172a; font-weight: 700; font-size: 20px; display: flex; align-items: center; gap: 10px; }
        .header h2 i { color: #3b82f6; }
        .header-actions a { color: #64748b; text-decoration: none; font-size: 13px; font-weight: 500; padding: 6px 14px; border-radius: 8px; background: #f1f5f9; transition: 0.2s; display: flex; align-items: center; gap: 6px; }
        .header-actions a:hover { background: #e2e8f0; color: #0f172a; }
        .body { padding: 30px 40px; width: 100%; max-width: 1600px; margin: 0 auto; flex: 1; display: flex; flex-direction: column; }

        .filters { display: flex; gap: 15px; flex-wrap: wrap; margin-bottom: 25px; align-items: flex-end; background: #f8fafc; padding: 20px; border-radius: 12px; border: 1px solid #f1f5f9; width: 100%; }
        .filters .form-group { flex:1; min-width:150px; }
        .filters label { display: block; font-size: 11px; font-weight: 600; color: #64748b; margin-bottom: 4px; text-transform: uppercase; letter-spacing: 0.5px; }
        .filters select, .filters .readonly-input { width:100%; padding: 10px 14px; border:1px solid #cbd5e1; border-radius:8px; font-size:14px; background:white; color: #334155; transition:0.2s; }
        .filters select:focus { outline:none; border-color:#3b82f6; box-shadow:0 0 0 3px rgba(59,130,246,0.1); }
        .filters .readonly-input { background: #f1f5f9; color: #475569; cursor: not-allowed; font-weight: 600; }
        .btn-filtrer { background:#3b82f6; color:white; border:none; padding:10px 20px; border-radius:8px; font-weight:600; font-size:14px; cursor:pointer; transition:0.2s; display: flex; align-items: center; gap: 6px; height: 41px; }
        .btn-filtrer:hover { background:#2563eb; }

        /* ✅ Carte de la moyenne générale pondérée */
        .moyenne-card { background: linear-gradient(135deg, #1e293b 0%, #0f172a 100%); color: white; border-radius: 16px; padding: 25px 30px; margin-bottom: 25px; display: flex; flex-direction: column; align-items: center; box-shadow: 0 10px 25px -5px rgba(30,41,59,0.3); }
        .moyenne-label { font-size: 13px; color: #94a3b8; font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px; margin-bottom: 8px; display: flex; align-items: center; gap: 8px; }
        .moyenne-label i { color: #f5cd79; }
        .moyenne-value { font-size: 48px; font-weight: 800; line-height: 1; }
        .moyenne-value span { font-size: 22px; font-weight: 500; opacity: 0.7; }
        .moyenne-detail { font-size: 13px; color: #94a3b8; margin-top: 10px; }

        .chart-container { display:flex; flex-direction:column; align-items:center; background: #ffffff; padding: 30px; border-radius: 12px; border: 1px solid #e2e8f0; width: 100%; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05); }
        .chart-wrapper { width:100%; max-width:500px; margin:0 auto; }
        .legend { display:flex; flex-wrap:wrap; gap:12px; justify-content:center; margin-top:25px; }
        .legend-item { display:flex; align-items:center; gap:8px; font-size:13px; color: #334155; font-weight: 500; background: #f8fafc; padding: 8px 12px; border-radius: 8px; border: 1px solid #f1f5f9; }
        .legend-color { width:12px; height:12px; border-radius:4px; flex-shrink: 0; }
        .legend-item em { color: #64748b; font-style: normal; font-size: 12px; }
        .legend-item strong { color: #0f172a; }
        .empty-message { text-align:center; padding:40px; color:#64748b; font-size: 15px; }
        .empty-message i { font-size:48px; display:block; margin-bottom:12px; color:#cbd5e1; }
        .footer { text-align:center; font-size:13px; color:#64748b; border-top:1px solid rgba(0,0,0,0.06); padding: 25px 0 15px; margin-top: auto; width: 100%; }
        @media (max-width:768px){ .filters{ flex-direction:column; } .filters .form-group{ min-width:100%; } .body{ padding: 20px; } .header{ padding: 20px; } .moyenne-value{ font-size: 36px; } }
        """;
    }

    // ============================================================
    // ÉCHAPPEMENT
    // ============================================================
    private static String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }

    private static String escapeJs(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t")
                    .replace("</", "<\\/");
    }
}