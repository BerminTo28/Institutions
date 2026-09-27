import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

public class UIBudget {

    private static final DecimalFormat COMPTA_FORMAT;
    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator(',');
        symbols.setDecimalSeparator('.');
        COMPTA_FORMAT = new DecimalFormat("#,##0.00", symbols);
    }

    public static String generateHTML(GestionFinanciereService service,
                                      boolean canCreate,
                                      boolean canUpdate,
                                      boolean canDelete) throws SQLException {

        // ============================================================
        // DONNÉES
        // ============================================================
        double recettesInternes = service.getTotalRecettesInternes();
        double recettesExternes = service.getTotalRecettesExternes();
        double depenses = service.getTotalDepenses();
        double salaires = service.getTotalSalaires();
        double depensesHorsSalaires = depenses - salaires;
        double totalRecettes = recettesInternes + recettesExternes;
        double solde = totalRecettes - depenses;

        if (recettesInternes < 0) recettesInternes = 0;
        if (recettesExternes < 0) recettesExternes = 0;
        if (depensesHorsSalaires < 0) depensesHorsSalaires = 0;
        if (salaires < 0) salaires = 0;

        String soldeClass = solde >= 0 ? "positive" : "negative";
        String soldeLabel = solde >= 0 ? "Excédent" : "Déficit";

        // ============================================================
        // OPTIONS ANNÉES / MOIS
        // ============================================================
        List<String> anneesAcademiques = service.getAnneesAcademiques();

        StringBuilder optionsAnnees = new StringBuilder();
        optionsAnnees.append("<option value=''>Toutes</option>");
        for (String a : anneesAcademiques) {
            optionsAnnees.append("<option value='").append(escapeHtml(a)).append("'>");
            optionsAnnees.append(escapeHtml(a));
            optionsAnnees.append("</option>");
        }

        List<String> moisDisponibles = service.getMoisDisponibles();

        StringBuilder optionsMois = new StringBuilder();
        optionsMois.append("<option value=''>Tous</option>");
        for (String m : moisDisponibles) {
            optionsMois.append("<option value='").append(escapeHtml(m)).append("'>")
                       .append(formatMois(m)).append("</option>");
        }

        // ============================================================
        // DONNÉES DU GRAPHIQUE
        // ============================================================
        String donutLabels = "[\"Recettes internes\", \"Recettes externes\", \"Dépenses hors salaires\", \"Salaires\"]";
        String donutData = String.format(Locale.US, "[%.2f, %.2f, %.2f, %.2f]",
                recettesInternes, recettesExternes, depensesHorsSalaires, salaires);
        String donutColors = "[\"#2E7D32\", \"#1565C0\", \"#C62828\", \"#F57C00\"]";

        // ============================================================
        // CONSTRUCTION HTML
        // ============================================================
        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html>");
        html.append("<html lang='fr'>");
        html.append("<head>");
        html.append("<meta charset='UTF-8'>");
        html.append("<meta name='viewport' content='width=device-width, initial-scale=1.0, viewport-fit=cover'>");
        html.append("<meta http-equiv='X-UA-Compatible' content='IE=edge'>");
        html.append("<title>Budget - Synthèse</title>");
        html.append("<link href='https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700;800&display=swap' rel='stylesheet'>");
        html.append("<link rel='stylesheet' href='https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css'>");
        html.append("<script src='https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js'></script>");
        html.append("<script src='https://cdn.jsdelivr.net/npm/chartjs-plugin-datalabels@2.2.0/dist/chartjs-plugin-datalabels.min.js'></script>");
        html.append("<style>");

        // ============================================================
        // RESET + PLEIN ÉCRAN
        // ============================================================
        html.append("*, *::before, *::after { margin: 0; padding: 0; box-sizing: border-box; }");

        html.append("html, body {");
        html.append("  width: 100%;");
        html.append("  min-width: 320px;");          // très petit mobile
        html.append("  height: 100%;");
        html.append("  min-height: 100vh;");
        html.append("  min-height: 100dvh;");        // mobile moderne
        html.append("  margin: 0;");
        html.append("  padding: 0;");
        html.append("  background: #FAFBFB;");
        html.append("  font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;");
        html.append("  font-size: 14px;");
        html.append("  color: #1E293B;");
        html.append("  overflow-x: hidden;");
        html.append("  -webkit-text-size-adjust: 100%;");
        html.append("  -webkit-tap-highlight-color: transparent;");
        html.append("  overscroll-behavior: none;");
        html.append("}");

        html.append("body {");
        html.append("  display: block;");
        html.append("  padding: 0;");
        html.append("  padding-left: env(safe-area-inset-left, 0);");
        html.append("  padding-right: env(safe-area-inset-right, 0);");
        html.append("  padding-bottom: env(safe-area-inset-bottom, 0);");
        html.append("}");

        // ============================================================
        // CONTAINER PLEIN ÉCRAN — AUCUNE LIMITE
        // ============================================================
        html.append(".container {");
        html.append("  width: 100%;");
        html.append("  max-width: none;");           // ⚠️ supprime la limite
        html.append("  min-height: 100vh;");
        html.append("  min-height: 100dvh;");
        html.append("  margin: 0;");
        html.append("  padding: 8px;");               // padding minimal
        html.append("  display: flex;");
        html.append("  flex-direction: column;");
        html.append("  gap: 12px;");
        html.append("}");

        // ============================================================
        // NAV
        // ============================================================
        html.append(".nav-menu {");
        html.append("  display: flex;");
        html.append("  gap: 6px;");
        html.append("  background: white;");
        html.append("  border-radius: 10px;");
        html.append("  padding: 6px;");
        html.append("  border: 1px solid #DCE1E6;");
        html.append("  flex-wrap: wrap;");
        html.append("  width: 100%;");
        html.append("  justify-content: center;");
        html.append("  box-shadow: 0 1px 3px rgba(0,0,0,0.04);");
        html.append("}");
        html.append(".nav-btn {");
        html.append("  flex: 1 1 140px;");
        html.append("  text-align: center;");
        html.append("  padding: 10px 12px;");
        html.append("  background: none;");
        html.append("  border: none;");
        html.append("  cursor: pointer;");
        html.append("  font-weight: 600;");
        html.append("  font-size: 13px;");
        html.append("  border-radius: 8px;");
        html.append("  text-decoration: none;");
        html.append("  color: #333;");
        html.append("  transition: all 0.2s;");
        html.append("  white-space: nowrap;");
        html.append("}");
        html.append(".nav-btn.active { background: #1565C0; color: white; }");
        html.append(".nav-btn:hover:not(.active) { background: #E3F2FD; }");
        html.append(".nav-btn i { margin-right: 6px; }");

        // ============================================================
        // STATS — grille auto-adaptative
        // ============================================================
        html.append(".stats-grid {");
        html.append("  display: grid;");
        html.append("  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));");
        html.append("  gap: 12px;");
        html.append("  width: 100%;");
        html.append("}");
        html.append(".stat-card {");
        html.append("  background: white;");
        html.append("  border-radius: 12px;");
        html.append("  padding: 16px 12px;");
        html.append("  text-align: center;");
        html.append("  box-shadow: 0 1px 4px rgba(0,0,0,0.05);");
        html.append("  border: 1px solid #DCE1E6;");
        html.append("  transition: transform 0.15s, box-shadow 0.15s;");
        html.append("}");
        html.append(".stat-card:hover { transform: translateY(-2px); box-shadow: 0 4px 12px rgba(0,0,0,0.08); }");
        html.append(".stat-value {");
        html.append("  font-size: clamp(18px, 2.2vw, 26px);");
        html.append("  font-weight: 800;");
        html.append("  font-family: 'Courier New', monospace;");
        html.append("  line-height: 1.2;");
        html.append("  word-break: break-word;");
        html.append("}");
        html.append(".stat-label {");
        html.append("  font-size: 11px;");
        html.append("  color: #666;");
        html.append("  margin-top: 6px;");
        html.append("  font-weight: 600;");
        html.append("  text-transform: uppercase;");
        html.append("  letter-spacing: 0.5px;");
        html.append("}");
        html.append(".positive { color: #2E7D32; }");
        html.append(".negative { color: #C62828; }");

        // ============================================================
        // CARDS
        // ============================================================
        html.append(".card {");
        html.append("  background: white;");
        html.append("  border-radius: 12px;");
        html.append("  padding: 16px;");
        html.append("  box-shadow: 0 1px 4px rgba(0,0,0,0.05);");
        html.append("  border: 1px solid #DCE1E6;");
        html.append("  width: 100%;");
        html.append("  text-align: center;");
        html.append("}");
        html.append(".card-title {");
        html.append("  font-size: 16px;");
        html.append("  font-weight: 700;");
        html.append("  color: #1565C0;");
        html.append("  margin-bottom: 14px;");
        html.append("  border-bottom: 2px solid #1565C0;");
        html.append("  padding-bottom: 6px;");
        html.append("  display: inline-block;");
        html.append("}");

        // ============================================================
        // FILTRES
        // ============================================================
        html.append(".filters-grid {");
        html.append("  display: grid;");
        html.append("  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));");
        html.append("  gap: 12px;");
        html.append("  width: 100%;");
        html.append("  align-items: end;");
        html.append("}");
        html.append(".form-group {");
        html.append("  display: flex;");
        html.append("  flex-direction: column;");
        html.append("  gap: 4px;");
        html.append("  align-items: center;");
        html.append("}");
        html.append(".form-group label {");
        html.append("  font-size: 12px;");
        html.append("  font-weight: 600;");
        html.append("  color: #334155;");
        html.append("  display: flex;");
        html.append("  align-items: center;");
        html.append("  justify-content: center;");
        html.append("  gap: 5px;");
        html.append("}");
        html.append(".form-group select {");
        html.append("  padding: 9px 12px;");
        html.append("  border: 1px solid #DCE1E6;");
        html.append("  border-radius: 8px;");
        html.append("  font-size: 13px;");
        html.append("  font-family: 'Inter', sans-serif;");
        html.append("  width: 100%;");
        html.append("  background: white;");
        html.append("  text-align: center;");
        html.append("  cursor: pointer;");
        html.append("}");
        html.append(".form-group select:focus {");
        html.append("  outline: none;");
        html.append("  border-color: #1565C0;");
        html.append("  box-shadow: 0 0 0 3px rgba(21,101,192,0.1);");
        html.append("}");
        html.append(".btn-reset {");
        html.append("  background: #64748B;");
        html.append("  color: white;");
        html.append("  border: none;");
        html.append("  padding: 9px 16px;");
        html.append("  border-radius: 8px;");
        html.append("  cursor: pointer;");
        html.append("  font-weight: 600;");
        html.append("  font-size: 13px;");
        html.append("  transition: background 0.2s;");
        html.append("  display: inline-flex;");
        html.append("  align-items: center;");
        html.append("  justify-content: center;");
        html.append("  gap: 6px;");
        html.append("  width: 100%;");
        html.append("  height: 38px;");
        html.append("}");
        html.append(".btn-reset:hover { background: #475569; }");

        // ============================================================
        // TABLEAU
        // ============================================================
        html.append(".table-responsive {");
        html.append("  width: 100%;");
        html.append("  overflow-x: auto;");
        html.append("  border-radius: 10px;");
        html.append("  border: 1px solid #DCE1E6;");
        html.append("  -webkit-overflow-scrolling: touch;");
        html.append("}");
        html.append("table {");
        html.append("  width: 100%;");
        html.append("  border-collapse: collapse;");
        html.append("  font-size: 13px;");
        html.append("  min-width: 500px;");
        html.append("}");
        html.append("th {");
        html.append("  background: #FFF5C8;");
        html.append("  padding: 10px 12px;");
        html.append("  font-weight: 700;");
        html.append("  border-bottom: 2px solid #1565C0;");
        html.append("  font-size: 11px;");
        html.append("  color: #0D47A1;");
        html.append("  text-transform: uppercase;");
        html.append("  letter-spacing: 0.5px;");
        html.append("  text-align: center;");
        html.append("  white-space: nowrap;");
        html.append("}");
        html.append("td {");
        html.append("  padding: 10px 12px;");
        html.append("  border-bottom: 1px solid #E2E8F0;");
        html.append("  font-size: 13px;");
        html.append("  text-align: center;");
        html.append("  vertical-align: middle;");
        html.append("}");
        html.append("td.col-montant {");
        html.append("  text-align: right;");
        html.append("  font-family: 'Courier New', monospace;");
        html.append("  font-weight: 600;");
        html.append("  white-space: nowrap;");
        html.append("}");
        html.append("tbody tr:hover { background: #F1F8FF; }");
        html.append("tbody tr:last-child td { border-bottom: none; }");
        html.append(".budget-row-total td { background: #FFF8E1; font-weight: 800; border-top: 2px solid #1565C0; }");
        html.append(".budget-row-net td { background: #E8F5E9; font-weight: 800; border-top: 2px solid #2E7D32; }");
        html.append(".rubrique-label { text-align: left; font-weight: 600; color: #1E293B; }");

        // ============================================================
        // CHART
        // ============================================================
        html.append(".chart-container {");
        html.append("  display: flex;");
        html.append("  justify-content: center;");
        html.append("  width: 100%;");
        html.append("}");
        html.append(".chart-box {");
        html.append("  width: 100%;");
        html.append("  max-width: 640px;");        // le graphique reste lisible
        html.append("  background: white;");
        html.append("  border-radius: 12px;");
        html.append("  padding: 14px;");
        html.append("  border: 1px solid #DCE1E6;");
        html.append("  box-shadow: 0 1px 4px rgba(0,0,0,0.05);");
        html.append("}");
        html.append(".chart-box canvas { width: 100% !important; height: auto !important; max-height: 60vh; }");
        html.append(".chart-title {");
        html.append("  text-align: center;");
        html.append("  font-weight: 600;");
        html.append("  margin-bottom: 10px;");
        html.append("  color: #1565C0;");
        html.append("  font-size: 14px;");
        html.append("}");

        // ============================================================
        // RESPONSIVE — adaptation aux petits écrans
        // ============================================================
        html.append("@media (max-width: 600px) {");
        html.append("  .container { padding: 6px; gap: 8px; }");
        html.append("  .nav-btn { flex: 1 1 100px; font-size: 11px; padding: 8px 8px; }");
        html.append("  .nav-btn i { display: block; margin: 0 0 2px 0; font-size: 14px; }");
        html.append("  .stat-card { padding: 12px 8px; }");
        html.append("  .stat-value { font-size: 16px; }");
        html.append("  .stat-label { font-size: 10px; }");
        html.append("  .card { padding: 12px; }");
        html.append("  .card-title { font-size: 14px; }");
        html.append("  th { padding: 8px 6px; font-size: 10px; }");
        html.append("  td { padding: 8px 6px; font-size: 12px; }");
        html.append("  .chart-box { padding: 10px; }");
        html.append("}");

        // ============================================================
        // RESPONSIVE — très grand écran (le contenu s'étale, ne reste pas centré étroit)
        // ============================================================
        html.append("@media (min-width: 1920px) {");
        html.append("  .stats-grid { grid-template-columns: repeat(4, 1fr); }");
        html.append("  .chart-box { max-width: 800px; }");
        html.append("  html, body { font-size: 15px; }");
        html.append("}");

        // ============================================================
        // IFRAME — quand ouvert dans un module du dashboard
        // ============================================================
        html.append("@media (display-mode: fullscreen) {");
        html.append("  .container { padding: 8px; }");
        html.append("}");

        html.append("</style>");
        html.append("</head>");
        html.append("<body>");
        html.append("<div class='container'>");

        // NAV
        html.append("<div class='nav-menu'>");
        html.append("<a href='/admin/economie/budget' class='nav-btn active'><i class='fas fa-chart-pie'></i> Budget</a>");
        html.append("<a href='/admin/economie/recettes/internes' class='nav-btn'><i class='fas fa-school'></i> Recettes internes</a>");
        html.append("<a href='/admin/economie/recettes/externes' class='nav-btn'><i class='fas fa-hand-holding-usd'></i> Recettes externes</a>");
        html.append("<a href='/admin/economie/depenses' class='nav-btn'><i class='fas fa-arrow-down'></i> Dépenses</a>");
        html.append("<a href='/admin/economie/salaires' class='nav-btn'><i class='fas fa-users'></i> Salaires</a>");
        html.append("</div>");

        // FILTRES
        html.append("<div class='card'>");
        html.append("<div class='card-title'><i class='fas fa-filter'></i> Filtres</div>");
        html.append("<div class='filters-grid'>");
        html.append("<div class='form-group'>");
        html.append("<label><i class='fas fa-calendar-alt'></i> Année académique</label>");
        html.append("<select id='filtreAnnee'>").append(optionsAnnees).append("</select>");
        html.append("</div>");
        html.append("<div class='form-group'>");
        html.append("<label><i class='fas fa-clock'></i> Mois</label>");
        html.append("<select id='filtreMois'>").append(optionsMois).append("</select>");
        html.append("</div>");
        html.append("<div class='form-group'>");
        html.append("<label>&nbsp;</label>");
        html.append("<button class='btn-reset' id='btnReset'><i class='fas fa-rotate-left'></i> Réinitialiser</button>");
        html.append("</div>");
        html.append("</div>");
        html.append("</div>");

        // STATS
        html.append("<div class='stats-grid'>");
        html.append("<div class='stat-card'><div class='stat-value positive' id='statRecettesInternes'>")
            .append(formatComptable(recettesInternes))
            .append(" <span style='font-size:12px;color:#94a3b8;'>Gdes</span></div>")
            .append("<div class='stat-label'>Recettes internes</div></div>");
        html.append("<div class='stat-card'><div class='stat-value positive' id='statRecettesExternes'>")
            .append(formatComptable(recettesExternes))
            .append(" <span style='font-size:12px;color:#94a3b8;'>Gdes</span></div>")
            .append("<div class='stat-label'>Recettes externes</div></div>");
        html.append("<div class='stat-card'><div class='stat-value negative' id='statDepenses'>")
            .append(formatComptable(depenses))
            .append(" <span style='font-size:12px;color:#94a3b8;'>Gdes</span></div>")
            .append("<div class='stat-label'>Dépenses totales</div></div>");
        html.append("<div class='stat-card'><div class='stat-value ").append(soldeClass)
            .append("' id='statSolde'>")
            .append(formatComptable(solde))
            .append(" <span style='font-size:12px;color:#94a3b8;'>Gdes</span></div>")
            .append("<div class='stat-label' id='statSoldeLabel'>").append(soldeLabel).append("</div></div>");
        html.append("</div>");

        // GRAPHIQUE
        html.append("<div class='chart-container'>");
        html.append("<div class='chart-box'>");
        html.append("<div class='chart-title'><i class='fas fa-chart-pie'></i> Répartition des flux</div>");
        html.append("<canvas id='donutChart'></canvas>");
        html.append("</div>");
        html.append("</div>");

        // TABLEAU
        html.append("<div class='card'>");
        html.append("<div class='card-title'><i class='fas fa-table'></i> Synthèse du budget</div>");
        html.append("<div class='table-responsive'>");
        html.append("<table>");
        html.append("<thead><tr>");
        html.append("<th>Rubrique</th>");
        html.append("<th>Recettes (Gdes)</th>");
        html.append("<th>Dépenses (Gdes)</th>");
        html.append("<th>Solde (Gdes)</th>");
        html.append("</tr></thead>");
        html.append("<tbody>");

        html.append("<tr><td class='rubrique-label'>Recettes Internes</td>");
        html.append("<td class='col-montant positive' id='tdRecettesInternes'>")
            .append(formatComptable(recettesInternes)).append("</td>");
        html.append("<td class='col-montant'>-</td>");
        html.append("<td class='col-montant positive' id='tdSoldeRecettesInternes'>")
            .append(formatComptable(recettesInternes)).append("</td></tr>");

        html.append("<tr><td class='rubrique-label'>Recettes Externes</td>");
        html.append("<td class='col-montant positive' id='tdRecettesExternes'>")
            .append(formatComptable(recettesExternes)).append("</td>");
        html.append("<td class='col-montant'>-</td>");
        html.append("<td class='col-montant positive' id='tdSoldeRecettesExternes'>")
            .append(formatComptable(recettesExternes)).append("</td></tr>");

        html.append("<tr class='budget-row-total'><td class='rubrique-label'>TOTAL RECETTES</td>");
        html.append("<td class='col-montant positive' id='tdTotalRecettes'>")
            .append(formatComptable(totalRecettes)).append("</td>");
        html.append("<td class='col-montant'>-</td>");
        html.append("<td class='col-montant positive' id='tdSoldeTotalRecettes'>")
            .append(formatComptable(totalRecettes)).append("</td></tr>");

        html.append("<tr><td class='rubrique-label'>Dépenses (hors salaires)</td>");
        html.append("<td class='col-montant'>-</td>");
        html.append("<td class='col-montant negative' id='tdDepensesHorsSalaires'>")
            .append(formatComptable(depensesHorsSalaires)).append("</td>");
        html.append("<td class='col-montant'>-</td></tr>");

        html.append("<tr><td class='rubrique-label'><i class='fas fa-users' style='color:#F57C00;'></i> Salaires</td>");
        html.append("<td class='col-montant'>-</td>");
        html.append("<td class='col-montant negative' id='tdSalaires'>")
            .append(formatComptable(salaires)).append("</td>");
        html.append("<td class='col-montant'>-</td></tr>");

        html.append("<tr style='background:#FFEBEE;'><td class='rubrique-label'><strong>TOTAL DÉPENSES</strong></td>");
        html.append("<td class='col-montant'>-</td>");
        html.append("<td class='col-montant negative' id='tdTotalDepenses'><strong>")
            .append(formatComptable(depenses)).append("</strong></td>");
        html.append("<td class='col-montant negative'><strong>-</strong></td></tr>");

        html.append("<tr class='budget-row-net'><td class='rubrique-label'><strong>BUDGET NET</strong></td>");
        html.append("<td class='col-montant'>-</td>");
        html.append("<td class='col-montant'>-</td>");
        html.append("<td class='col-montant ").append(soldeClass).append("' id='tdSolde'><strong>")
            .append(formatComptable(solde)).append("</strong></td></tr>");

        html.append("</tbody></table>");
        html.append("</div>");
        html.append("</div>");

        html.append("</div>");

        // ============================================================
        // SCRIPT
        // ============================================================
        html.append("<script>");

        html.append("if (typeof Chart === 'undefined') {");
        html.append("  document.getElementById('donutChart').outerHTML = ");
        html.append("    '<div style=\"text-align:center;padding:40px;color:#C62828;\">' +");
        html.append("    '<i class=\"fas fa-exclamation-triangle\"></i> ' +");
        html.append("    'Impossible de charger Chart.js.</div>';");
        html.append("} else {");

        html.append("  if (typeof ChartDataLabels !== 'undefined') {");
        html.append("    Chart.register(ChartDataLabels);");
        html.append("  }");

        html.append("  const ctxDonut = document.getElementById('donutChart').getContext('2d');");
        html.append("  const dataValues = ").append(donutData).append(";");
        html.append("  let total = dataValues.reduce((a,b) => a + b, 0);");
        html.append("  let donutChart = null;");

        html.append("  if (total <= 0) {");
        html.append("    document.getElementById('donutChart').outerHTML = ");
        html.append("      '<div style=\"text-align:center;padding:40px;color:#94a3b8;\">' +");
        html.append("      '<i class=\"fas fa-chart-pie\" style=\"font-size:32px;\"></i><br>' +");
        html.append("      'Aucune donnée à afficher.</div>';");
        html.append("  } else {");

        html.append("    donutChart = new Chart(ctxDonut, {");
        html.append("      type: 'doughnut',");
        html.append("      data: {");
        html.append("        labels: ").append(donutLabels).append(",");
        html.append("        datasets: [{");
        html.append("          data: dataValues,");
        html.append("          backgroundColor: ").append(donutColors).append(",");
        html.append("          borderWidth: 2,");
        html.append("          borderColor: '#ffffff'");
        html.append("        }]");
        html.append("      },");
        html.append("      options: {");
        html.append("        responsive: true,");
        html.append("        maintainAspectRatio: true,");
        html.append("        cutout: '65%',");
        html.append("        plugins: {");
        html.append("          legend: {");
        html.append("            position: 'bottom',");
        html.append("            labels: {");
        html.append("              padding: 10,");
        html.append("              boxWidth: 12,");
        html.append("              font: { size: 11, family: 'Inter' },");
        html.append("              generateLabels: function(chart) {");
        html.append("                const data = chart.data;");
        html.append("                return data.labels.map((label, i) => ({");
        html.append("                  text: label + ' (' + ((data.datasets[0].data[i] / total) * 100).toFixed(1) + '%)',");
        html.append("                  fillStyle: data.datasets[0].backgroundColor[i],");
        html.append("                  strokeStyle: '#fff',");
        html.append("                  lineWidth: 1,");
        html.append("                  hidden: false,");
        html.append("                  index: i");
        html.append("                }));");
        html.append("              }");
        html.append("            }");
        html.append("          },");
        html.append("          tooltip: {");
        html.append("            callbacks: {");
        html.append("              label: function(context) {");
        html.append("                const percentage = ((context.parsed / total) * 100).toFixed(1);");
        html.append("                return context.label + ': ' + context.parsed.toLocaleString() + ' Gdes (' + percentage + '%)';");
        html.append("              }");
        html.append("            }");
        html.append("          },");
        html.append("          datalabels: {");
        html.append("            color: '#fff',");
        html.append("            font: { weight: 'bold', size: 12, family: 'Inter' },");
        html.append("            formatter: (value, context) => {");
        html.append("              const pct = ((value / total) * 100).toFixed(1);");
        html.append("              return pct > 5 ? pct + '%' : '';");   // évite le chevauchement sur petits segments
        html.append("            },");
        html.append("            backgroundColor: (context) => context.dataset.backgroundColor,");
        html.append("            borderRadius: 6,");
        html.append("            padding: 4,");
        html.append("            display: (context) => context.dataset.data[context.dataIndex] > 0,");
        html.append("            anchor: 'center',");
        html.append("            align: 'center',");
        html.append("            offset: 0");
        html.append("          }");
        html.append("        }");
        html.append("      },");
        html.append("      plugins: [{");
        html.append("        id: 'centerText',");
        html.append("        afterDraw(chart) {");
        html.append("          const { ctx, chartArea: { top, bottom, left, right } } = chart;");
        html.append("          const centerX = (left + right) / 2;");
        html.append("          const centerY = (top + bottom) / 2;");
        html.append("          ctx.save();");
        html.append("          ctx.font = 'bold 14px Inter, sans-serif';");
        html.append("          ctx.fillStyle = '#333';");
        html.append("          ctx.textAlign = 'center';");
        html.append("          ctx.fillText('Total: ' + total.toLocaleString() + ' Gdes', centerX, centerY - 6);");
        html.append("          ctx.font = '12px Inter, sans-serif';");
        html.append("          ctx.fillText('Budget net: ").append(formatComptable(solde)).append(" Gdes', centerX, centerY + 14);");
        html.append("          ctx.restore();");
        html.append("        }");
        html.append("      }]");
        html.append("    });");

        html.append("  }");

        html.append("  function formatComptableJS(n) {");
        html.append("    if (n === null || n === undefined) return '0.00';");
        html.append("    return Number(n).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });");
        html.append("  }");

        html.append("  async function chargerDonneesFiltrees() {");
        html.append("    const annee = document.getElementById('filtreAnnee').value;");
        html.append("    const mois = document.getElementById('filtreMois').value;");
        html.append("    const params = new URLSearchParams();");
        html.append("    if (annee) params.append('annee', annee);");
        html.append("    if (mois) params.append('mois', mois);");
        html.append("    try {");
        html.append("      const resp = await fetch('/api/economie/budget/filtre?' + params);");
        html.append("      const data = await resp.json();");
        html.append("      if (data.success) mettreAJourAffichage(data);");
        html.append("    } catch (e) { console.error('Erreur filtre:', e); }");
        html.append("  }");

        html.append("  function mettreAJourAffichage(data) {");
        html.append("    const ri = data.recettesInternes || 0;");
        html.append("    const re = data.recettesExternes || 0;");
        html.append("    const dep = data.depenses || 0;");
        html.append("    const sal = data.salaires || 0;");
        html.append("    const depHorsSal = Math.max(0, dep - sal);");
        html.append("    const totalRec = ri + re;");
        html.append("    const solde = totalRec - dep;");

        html.append("    document.getElementById('statRecettesInternes').innerHTML = formatComptableJS(ri) + ' <span style=\"font-size:12px;color:#94a3b8;\">Gdes</span>';");
        html.append("    document.getElementById('statRecettesExternes').innerHTML = formatComptableJS(re) + ' <span style=\"font-size:12px;color:#94a3b8;\">Gdes</span>';");
        html.append("    document.getElementById('statDepenses').innerHTML = formatComptableJS(dep) + ' <span style=\"font-size:12px;color:#94a3b8;\">Gdes</span>';");
        html.append("    document.getElementById('statSolde').innerHTML = formatComptableJS(solde) + ' <span style=\"font-size:12px;color:#94a3b8;\">Gdes</span>';");
        html.append("    document.getElementById('statSoldeLabel').textContent = solde >= 0 ? 'Excédent' : 'Déficit';");

        html.append("    document.getElementById('tdRecettesInternes').textContent = formatComptableJS(ri);");
        html.append("    document.getElementById('tdSoldeRecettesInternes').textContent = formatComptableJS(ri);");
        html.append("    document.getElementById('tdRecettesExternes').textContent = formatComptableJS(re);");
        html.append("    document.getElementById('tdSoldeRecettesExternes').textContent = formatComptableJS(re);");
        html.append("    document.getElementById('tdTotalRecettes').textContent = formatComptableJS(totalRec);");
        html.append("    document.getElementById('tdSoldeTotalRecettes').textContent = formatComptableJS(totalRec);");
        html.append("    document.getElementById('tdDepensesHorsSalaires').textContent = formatComptableJS(depHorsSal);");
        html.append("    document.getElementById('tdSalaires').textContent = formatComptableJS(sal);");
        html.append("    document.getElementById('tdTotalDepenses').innerHTML = '<strong>' + formatComptableJS(dep) + '</strong>';");
        html.append("    document.getElementById('tdSolde').innerHTML = '<strong>' + formatComptableJS(solde) + '</strong>';");

        html.append("    if (donutChart && donutChart.data) {");
        html.append("      donutChart.data.datasets[0].data = [ri, re, depHorsSal, sal];");
        html.append("      total = ri + re + depHorsSal + sal;");
        html.append("      donutChart.update();");
        html.append("    }");
        html.append("  }");

        html.append("  function reinitialiserFiltres() {");
        html.append("    document.getElementById('filtreAnnee').value = '';");
        html.append("    document.getElementById('filtreMois').value = '';");
        html.append("    chargerDonneesFiltrees();");
        html.append("  }");

        html.append("  document.getElementById('filtreAnnee').addEventListener('change', chargerDonneesFiltrees);");
        html.append("  document.getElementById('filtreMois').addEventListener('change', chargerDonneesFiltrees);");
        html.append("  document.getElementById('btnReset').addEventListener('click', reinitialiserFiltres);");

        html.append("}");  // fin if Chart defined

        html.append("</script>");
        html.append("</body>");
        html.append("</html>");

        return html.toString();
    }

    // ============================================================
    // UTILITAIRES
    // ============================================================
    private static String formatComptable(double number) {
        return COMPTA_FORMAT.format(number);
    }

    private static String formatMois(String moisIso) {
        if (moisIso == null || moisIso.length() < 7) return moisIso;
        try {
            String[] parts = moisIso.split("-");
            int annee = Integer.parseInt(parts[0]);
            int mois = Integer.parseInt(parts[1]);
            String[] noms = {"Janvier", "Février", "Mars", "Avril", "Mai", "Juin",
                             "Juillet", "Août", "Septembre", "Octobre", "Novembre", "Décembre"};
            if (mois >= 1 && mois <= 12) {
                return noms[mois - 1] + " " + annee;
            }
        } catch (NumberFormatException ignored) {}
        return moisIso;
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}