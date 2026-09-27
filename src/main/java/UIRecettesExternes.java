import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class UIRecettesExternes {

    private static final DecimalFormat COMPTA_FORMAT;
    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator(',');
        symbols.setDecimalSeparator('.');
        COMPTA_FORMAT = new DecimalFormat("#,##0.00", symbols);
    }

    private static final String[] COULEURS_BARRES = {
        "#F57C00", "#E53935", "#8E24AA", "#3949AB", "#039BE5",
        "#00897B", "#43A047", "#7CB342", "#C0CA33", "#FDD835",
        "#FB8C00", "#6D4C41", "#546E7A", "#D81B60", "#5E35B1"
    };

    public static String generateHTML(GestionFinanciereService service,
                                      boolean canCreate,
                                      boolean canUpdate,
                                      boolean canDelete) throws SQLException {

        // ============================================================
        // DONNÉES
        // ============================================================
        List<Map<String, Object>> recettes = service.getRecettesExternes();
        double total = service.getTotalRecettesExternes();

        // ============================================================
        // AGRÉGATION PAR MOIS (POUR LE GRAPHIQUE)
        // ============================================================
        Map<String, Double> recettesParMois = new LinkedHashMap<>();
        for (Map<String, Object> r : recettes) {
            String mois = extractMois(r);
            if (mois == null || mois.isBlank()) continue;
            double montant = asDouble(r.get("montant"));
            recettesParMois.merge(mois, montant, Double::sum);
        }

        List<Map.Entry<String, Double>> moisTries = new ArrayList<>(recettesParMois.entrySet());
        moisTries.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        double maxMois = 1.0;
        if (!moisTries.isEmpty() && moisTries.get(0).getValue() > 0) {
            maxMois = moisTries.get(0).getValue();
        }

        // ============================================================
        // GRAPHIQUE EN BARRES HORIZONTALES
        // ============================================================
        StringBuilder graphBars = new StringBuilder();
        if (moisTries.isEmpty()) {
            graphBars.append("<div class='graph-empty'>Aucune donnée mensuelle disponible</div>");
        } else {
            int index = 0;
            for (Map.Entry<String, Double> entry : moisTries) {
                String mois = entry.getKey();
                double montant = entry.getValue();
                double pourcentage = (montant / maxMois) * 100.0;
                String couleur = COULEURS_BARRES[index % COULEURS_BARRES.length];

                graphBars.append("<div class='graph-row'>")
                    .append("<div class='graph-label'>")
                    .append("<span class='graph-color-dot' style='background:")
                    .append(couleur).append(";'></span>")
                    .append(escapeHtml(formatMois(mois)))
                    .append("</div>")
                    .append("<div class='graph-bar-container'>")
                    .append("<div class='graph-bar' style='width:")
                    .append(String.format(Locale.US, "%.2f", pourcentage))
                    .append("%; background:linear-gradient(90deg, ")
                    .append(couleur).append(" 0%, ").append(couleur)
                    .append("CC 100%); box-shadow: 0 2px 6px ")
                    .append(couleur).append("55;'></div>")
                    .append("<span class='graph-value'>")
                    .append(formatComptable(montant))
                    .append(" <span class='devise'>Gdes</span></span>")
                    .append("</div>")
                    .append("</div>");
                index++;
            }
        }

        // ============================================================
        // LIGNES DU TABLEAU (avec data-annee et data-mois)
        // ============================================================
        StringBuilder rows = new StringBuilder();
        for (Map<String, Object> r : recettes) {
            Object idObj = r.get("id");
            if (idObj == null) continue;
            String id = idObj.toString();

            String annee = extractAnnee(r);
            String mois = extractMois(r);

            rows.append("<tr")
                .append(" data-annee=\"").append(escapeHtml(annee)).append("\"")
                .append(" data-mois=\"").append(escapeHtml(mois)).append("\">")

                .append("<td class='col-categorie'>")
                .append(escapeHtml(nvl(r.get("categorie"))))
                .append("</td>")

                .append("<td class='col-date'>")
                .append(escapeHtml(nvl(r.get("date_recette"))))
                .append("</td>")

                .append("<td class='col-montant'>")
                .append(formatComptable(asDouble(r.get("montant"))))
                .append(" <span class='devise'>Gdes</span></td>")

                .append("<td class='col-nom'>")
                .append(escapeHtml(nvl(r.get("donateur"))))
                .append("</td>")

                .append("<td class='col-nom'>")
                .append(escapeHtml(nvl(r.get("receveur"))))
                .append("</td>")

                .append("<td class='col-motif'>")
                .append(escapeHtml(nvl(r.get("motif"))))
                .append("</td>")

                .append("<td class='col-action'>");

            if (canDelete && !id.isEmpty()) {
                rows.append("<button class='btn-danger' onclick=\"supprimerRecette('")
                    .append(escapeJs(id)).append("')\" title=\"Supprimer\">")
                    .append("<i class='fas fa-trash'></i></button>");
            } else {
                rows.append("<span class='lock-icon'><i class='fas fa-lock'></i></span>");
            }
            rows.append("</td></tr>");
        }

        // ============================================================
        // FORMULAIRE D'AJOUT
        // ============================================================
        String formulaireHtml;
        if (canCreate) {
            formulaireHtml = """
                <div class='card'>
                    <div class='card-title'><i class='fas fa-plus-circle'></i> Ajouter une recette externe</div>
                    <div class='form-grid'>
                        <div class='form-group'>
                            <label>Catégorie</label>
                            <select id='categorie'>
                                <option value='DON'>Don</option>
                                <option value='SUBVENTION'>Subvention</option>
                                <option value='VENTE'>Vente</option>
                                <option value='AUTRE'>Autre</option>
                            </select>
                        </div>
                        <div class='form-group'>
                            <label>Date</label>
                            <input type='date' id='date' />
                        </div>
                        <div class='form-group'>
                            <label>Montant (Gdes)</label>
                            <input type='number' id='montant' step='0.01' placeholder='0.00' />
                        </div>
                        <div class='form-group'>
                            <label>Donateur / Vendeur</label>
                            <input type='text' id='donateur' placeholder='Nom' />
                        </div>
                        <div class='form-group'>
                            <label>Receveur / Acheteur</label>
                            <input type='text' id='receveur' placeholder='Nom' />
                        </div>
                        <div class='form-group'>
                            <label>Motif</label>
                            <input type='text' id='motif' placeholder='Motif' />
                        </div>
                    </div>
                    <button class='btn-primary' id='btnAjouter'>
                        <i class='fas fa-save'></i> Enregistrer
                    </button>
                </div>
                """;
        } else {
            formulaireHtml = """
                <div class='card card-locked'>
                    <div class='card-title locked-title'>
                        <i class='fas fa-lock'></i> Ajout de recettes externes restreint
                    </div>
                    <p class='locked-message'>
                        Vous ne disposez pas des droits nécessaires pour ajouter des recettes externes.
                    </p>
                </div>
                """;
        }

        // ============================================================
        // JS AJOUT / SUPPRESSION
        // ============================================================
        String jsCreate = canCreate ? """
            document.getElementById('date').value = new Date().toISOString().split('T')[0];
            document.getElementById('btnAjouter').onclick = async () => {
                const formData = new URLSearchParams();
                formData.append('categorie', document.getElementById('categorie').value);
                formData.append('date', document.getElementById('date').value);
                formData.append('montant', document.getElementById('montant').value);
                formData.append('donateur', document.getElementById('donateur').value);
                formData.append('receveur', document.getElementById('receveur').value);
                formData.append('motif', document.getElementById('motif').value);
                const resp = await fetch('/api/economie/recettes-externes', { method: 'POST', body: formData });
                const data = await resp.json();
                if (data.success) {
                    showToast('Recette ajoutée', 'success');
                    setTimeout(() => location.reload(), 500);
                } else {
                    showToast(data.message, 'error');
                }
            };
            """ : "// Ajout de recettes externes désactivé";

        String jsDelete = canDelete ? """
            async function supprimerRecette(id) {
                if (!confirm('Supprimer cette recette ?')) return;
                const formData = new URLSearchParams();
                formData.append('id', id);
                const resp = await fetch('/api/economie/recette-externe/supprimer', { method: 'POST', body: formData });
                const data = await resp.json();
                if (data.success) {
                    showToast('Supprimée', 'success');
                    setTimeout(() => location.reload(), 500);
                } else {
                    showToast(data.message, 'error');
                }
            }
            """ : "// Suppression de recettes externes désactivée";

        // ============================================================
        // JS FILTRES CASCADÉS : Année → Mois
        // ============================================================
        String jsFiltres = """
            // ============================================================
            // FILTRES CASCADÉS : Année → Mois
            // ============================================================
            const allRows = Array.from(document.querySelectorAll('#recettesBody tr')).map(row => ({
                el: row,
                annee: row.dataset.annee || '',
                mois:  row.dataset.mois  || ''
            }));

            function uniqueValues(rows, key) {
                const set = new Set();
                rows.forEach(r => { if (r[key]) set.add(r[key]); });
                return Array.from(set).sort();
            }

            function populateSelect(id, values, placeholder, labelFn) {
                const sel = document.getElementById(id);
                if (!sel) return;
                const current = sel.value;
                sel.innerHTML = '<option value="">' + placeholder + '</option>';
                values.forEach(v => {
                    const opt = document.createElement('option');
                    opt.value = v;
                    opt.textContent = labelFn ? labelFn(v) : v;
                    sel.appendChild(opt);
                });
                if (values.includes(current)) sel.value = current;
                else sel.value = '';
            }

            function updateFilters() {
                const annee = document.getElementById('filtreAnnee').value;

                // Mois présents dans les lignes filtrées par année
                const rowsAfterAnnee = allRows.filter(r => !annee || r.annee === annee);
                populateSelect('filtreMois', uniqueValues(rowsAfterAnnee, 'mois'), 'Tous', formatMois);

                applyFilters();
            }

            function applyFilters() {
                const annee = document.getElementById('filtreAnnee').value;
                const mois  = document.getElementById('filtreMois').value;

                let visible = 0;
                allRows.forEach(r => {
                    let ok = true;
                    if (annee && r.annee !== annee) ok = false;
                    if (mois  && r.mois  !== mois)  ok = false;
                    r.el.style.display = ok ? '' : 'none';
                    if (ok) visible++;
                });

                const counter = document.getElementById('resultCount');
                if (counter) counter.textContent = visible + ' / ' + allRows.length + ' recette(s)';
            }

            function resetFilters() {
                ['filtreAnnee', 'filtreMois'].forEach(id => {
                    const sel = document.getElementById(id);
                    if (sel) sel.value = '';
                });
                updateFilters();
            }

            function formatMois(moisIso) {
                if (!moisIso || moisIso.length < 7) return moisIso;
                const noms = ['Janvier','Février','Mars','Avril','Mai','Juin',
                              'Juillet','Août','Septembre','Octobre','Novembre','Décembre'];
                const parts = moisIso.split('-');
                const m = parseInt(parts[1], 10);
                if (m >= 1 && m <= 12) return noms[m - 1] + ' ' + parts[0];
                return moisIso;
            }

            document.addEventListener('DOMContentLoaded', () => {
                populateSelect('filtreAnnee', uniqueValues(allRows, 'annee'), 'Toutes');

                ['filtreAnnee', 'filtreMois'].forEach(id => {
                    const sel = document.getElementById(id);
                    if (sel) sel.addEventListener('change', updateFilters);
                });

                const resetBtn = document.getElementById('btnResetFilters');
                if (resetBtn) resetBtn.addEventListener('click', resetFilters);

                updateFilters();
            });
            """;

        // ============================================================
        // HTML FINAL
        // ============================================================
        return """
               <!DOCTYPE html>
               <html lang='fr'>
               <head>
                   <meta charset='UTF-8'>
                   <meta name='viewport' content='width=device-width, initial-scale=1.0'>
                   <title>Recettes externes</title>
                   <link href='https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700;800&display=swap' rel='stylesheet'>
                   <link rel='stylesheet' href='https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css'>
                   <style>
                       * { margin: 0; padding: 0; box-sizing: border-box; }
                       html, body {
                           width: 100%; min-height: 100vh;
                           background: #FAFBFB;
                           font-family: 'Inter', sans-serif;
                       }
                       body { padding: 20px; display: flex; justify-content: center; }
                       .container { width: 100%; max-width: 1600px; margin: 0 auto; }

                       .nav-menu {
                           display: flex; gap: 8px;
                           background: white; border-radius: 12px;
                           padding: 8px; margin-bottom: 20px;
                           border: 1px solid #DCE1E6; flex-wrap: wrap;
                           width: 100%; justify-content: center;
                       }
                       .nav-btn {
                           flex: 1 1 180px; text-align: center;
                           padding: 12px 20px;
                           background: none; border: none; cursor: pointer;
                           font-weight: 600; border-radius: 8px;
                           text-decoration: none; color: #333;
                           transition: all 0.2s ease-in-out;
                       }
                       .nav-btn.active { background: #F57C00; color: white; }
                       .nav-btn:hover:not(.active) { background: #FFF3E0; }

                       .card {
                           background: white; border-radius: 16px;
                           padding: 24px; margin-bottom: 20px;
                           box-shadow: 0 2px 8px rgba(0,0,0,0.06);
                           border: 1px solid #DCE1E6; width: 100%;
                           text-align: center;
                       }
                       .card-locked { background: #f8fafc; }
                       .card-title {
                           font-size: 18px; font-weight: 700;
                           color: #F57C00; margin-bottom: 20px;
                           border-bottom: 2px solid #F57C00;
                           padding-bottom: 8px; display: inline-block;
                       }
                       .card-title.locked-title {
                           color: #94a3b8; border-bottom-color: #94a3b8;
                       }
                       .locked-message { color: #64748b; }

                       .filters-grid {
                           display: grid;
                           grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
                           gap: 16px;
                           width: 100%;
                           max-width: 800px;
                           margin: 0 auto 12px;
                       }
                       .filters-actions {
                           display: flex; justify-content: center;
                           gap: 12px; margin-top: 8px;
                           align-items: center; flex-wrap: wrap;
                       }
                       .btn-reset {
                           background: #64748b; color: white; border: none;
                           padding: 10px 20px; border-radius: 8px;
                           cursor: pointer; font-weight: 600; font-size: 13px;
                           display: inline-flex; align-items: center; gap: 8px;
                       }
                       .btn-reset:hover { background: #475569; }
                       .result-count {
                           font-size: 13px; color: #64748b;
                           font-weight: 500;
                       }

                       .form-group {
                           display: flex; flex-direction: column; gap: 6px;
                           align-items: center;
                       }
                       .form-group label {
                           font-size: 13px; font-weight: 600; color: #334155;
                           display: flex; align-items: center; justify-content: center; gap: 6px;
                       }
                       .form-group input, .form-group select {
                           padding: 10px 14px;
                           border: 1px solid #DCE1E6;
                           border-radius: 8px;
                           font-size: 14px;
                           font-family: 'Inter', sans-serif;
                           width: 100%;
                           background: white;
                           text-align: center;
                       }
                       .form-group input:focus, .form-group select:focus {
                           outline: none;
                           border-color: #F57C00;
                           box-shadow: 0 0 0 3px rgba(245, 124, 0, 0.1);
                       }

                       .form-grid {
                           display: grid;
                           grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
                           gap: 20px; margin-bottom: 20px; width: 100%;
                       }

                       .btn-primary {
                           background: #F57C00; color: white; border: none;
                           padding: 12px 24px; border-radius: 8px;
                           cursor: pointer; font-weight: 600; font-size: 14px;
                           transition: background 0.2s;
                           display: inline-flex; align-items: center; gap: 8px;
                       }
                       .btn-primary:hover { background: #E65100; }
                       .btn-danger {
                           background: #C62828; color: white; border: none;
                           padding: 8px 12px; border-radius: 6px;
                           cursor: pointer; font-weight: 500; font-size: 13px;
                           transition: opacity 0.2s;
                           display: inline-flex; align-items: center; justify-content: center;
                           min-width: 40px;
                       }
                       .btn-danger:hover { opacity: 0.85; }
                       .lock-icon { color: #94a3b8; font-size: 14px; }

                       .table-responsive {
                           width: 100%; overflow-x: auto;
                           border-radius: 12px;
                           border: 1px solid #DCE1E6;
                       }
                       table { width: 100%; border-collapse: collapse; font-size: 14px; }
                       th {
                           background: #FFF5C8;
                           padding: 14px 16px;
                           font-weight: 700;
                           border-bottom: 2px solid #F57C00;
                           font-size: 13px;
                           color: #5D4037;
                           text-transform: uppercase;
                           letter-spacing: 0.5px;
                           text-align: center;
                       }
                       td {
                           padding: 12px 16px;
                           border-bottom: 1px solid #E2E8F0;
                           font-size: 14px;
                           vertical-align: middle;
                           text-align: center;
                       }
                       tbody tr:hover { background: #FFFBF0; }
                       tbody tr:last-child td { border-bottom: none; }

                       .col-categorie { font-weight: 700; color: #5D4037; }
                       .col-date { color: #475569; font-size: 13px; }
                       .col-nom { color: #475569; }
                       .col-motif { color: #64748b; }
                       .col-montant {
                           text-align: right;
                           font-family: 'Courier New', monospace;
                           font-weight: 700;
                           color: #16a34a;
                           font-size: 14.5px;
                       }
                       .col-montant .devise {
                           font-size: 11px;
                           color: #94a3b8;
                           font-weight: 500;
                           margin-left: 4px;
                           font-family: 'Inter', sans-serif;
                       }
                       .col-action { width: 90px; }

                       .graph-container {
                           width: 100%;
                           max-width: 1000px;
                           margin: 0 auto;
                           padding: 10px;
                       }
                       .graph-row {
                           display: flex;
                           align-items: center;
                           margin-bottom: 14px;
                           gap: 14px;
                       }
                       .graph-label {
                           width: 180px;
                           text-align: right;
                           font-weight: 700;
                           color: #5D4037;
                           font-size: 13px;
                           flex-shrink: 0;
                           display: flex;
                           align-items: center;
                           justify-content: flex-end;
                           gap: 8px;
                       }
                       .graph-color-dot {
                           width: 12px;
                           height: 12px;
                           border-radius: 50%;
                           display: inline-block;
                           flex-shrink: 0;
                           box-shadow: 0 0 0 2px rgba(255,255,255,0.9), 0 1px 3px rgba(0,0,0,0.15);
                       }
                       .graph-bar-container {
                           flex: 1;
                           position: relative;
                           background: #F1F5F9;
                           border-radius: 8px;
                           height: 38px;
                           overflow: hidden;
                           display: flex;
                           align-items: center;
                       }
                       .graph-bar {
                           height: 100%;
                           border-radius: 8px;
                           transition: width 0.8s cubic-bezier(0.4, 0, 0.2, 1);
                           min-width: 6px;
                           position: relative;
                       }
                       .graph-bar::after {
                           content: '';
                           position: absolute;
                           top: 0; left: 0; right: 0; bottom: 0;
                           background: linear-gradient(180deg, rgba(255,255,255,0.25) 0%, rgba(255,255,255,0) 100%);
                           border-radius: 8px;
                           pointer-events: none;
                       }
                       .graph-value {
                           position: absolute;
                           right: 12px;
                           top: 50%;
                           transform: translateY(-50%);
                           font-family: 'Courier New', monospace;
                           font-weight: 700;
                           font-size: 12.5px;
                           color: #1E293B;
                           background: rgba(255,255,255,0.92);
                           padding: 3px 10px;
                           border-radius: 6px;
                           white-space: nowrap;
                           box-shadow: 0 1px 3px rgba(0,0,0,0.08);
                           z-index: 2;
                       }
                       .graph-value .devise {
                           font-size: 10px;
                           color: #94a3b8;
                           font-family: 'Inter', sans-serif;
                       }
                       .graph-empty {
                           text-align: center;
                           padding: 40px;
                           color: #94a3b8;
                           font-style: italic;
                       }

                       .total-bar {
                           margin-top: 20px;
                           text-align: right;
                           font-weight: bold;
                           font-size: 16px;
                           padding: 16px 20px;
                           background: white;
                           border-radius: 12px;
                           border: 1px solid #DCE1E6;
                           color: #333;
                       }
                       .total-bar .total-value {
                           font-family: 'Courier New', monospace;
                           font-weight: 800;
                           color: #16a34a;
                           font-size: 18px;
                           margin-left: 8px;
                       }
                       .total-bar .devise {
                           font-size: 13px;
                           color: #94a3b8;
                           font-weight: 500;
                           margin-left: 4px;
                           font-family: 'Inter', sans-serif;
                       }

                       .toast {
                           position: fixed; bottom: 30px; right: 30px;
                           background: #333; color: white;
                           padding: 12px 20px; border-radius: 8px;
                           z-index: 1100; animation: fadeInOut 3s ease;
                           font-weight: 500;
                           box-shadow: 0 4px 12px rgba(0,0,0,0.15);
                       }
                       .toast.success { background: #27ae60; }
                       .toast.error { background: #ef4444; }
                       @keyframes fadeInOut {
                           0% { opacity: 0; transform: translateY(10px); }
                           15% { opacity: 1; transform: translateY(0); }
                           85% { opacity: 1; transform: translateY(0); }
                           100% { opacity: 0; transform: translateY(10px); }
                       }
                   </style>
               </head>
               <body>
               <div class='container'>
                   <div class='nav-menu'>
                       <a href='/admin/economie/budget' class='nav-btn'><i class='fas fa-chart-pie'></i> Budget</a>
                       <a href='/admin/economie/recettes/internes' class='nav-btn'><i class='fas fa-school'></i> Recettes internes</a>
                       <a href='/admin/economie/recettes/externes' class='nav-btn active'><i class='fas fa-hand-holding-usd'></i> Recettes externes</a>
                       <a href='/admin/economie/depenses' class='nav-btn'><i class='fas fa-arrow-down'></i> Dépenses</a>
                       <a href='/admin/economie/salaires' class='nav-btn'><i class='fas fa-users'></i> Salaires</a>
                   </div>

                   """ + formulaireHtml + """

                   <div class='card'>
                       <div class='card-title'><i class='fas fa-filter'></i> Filtres</div>
                       <div class='filters-grid'>
                           <div class='form-group'>
                               <label><i class='fas fa-calendar-alt'></i> Année académique</label>
                               <select id='filtreAnnee'><option value=''>Toutes</option></select>
                           </div>
                           <div class='form-group'>
                               <label><i class='fas fa-clock'></i> Mois</label>
                               <select id='filtreMois'><option value=''>Tous</option></select>
                           </div>
                       </div>
                       <div class='filters-actions'>
                           <button id='btnResetFilters' class='btn-reset' type='button'>
                               <i class='fas fa-undo'></i> Réinitialiser
                           </button>
                           <span id='resultCount' class='result-count'></span>
                       </div>
                   </div>

                   <div class='card'>
                       <div class='card-title'><i class='fas fa-chart-bar'></i> Répartition mensuelle des recettes</div>
                       <div class='graph-container'>
                           """ + graphBars.toString() + """
                       </div>
                   </div>

                   <div class='card'>
                       <div class='card-title'><i class='fas fa-list'></i> Liste des recettes externes</div>
                       <div class='table-responsive'>
                           <table>
                               <thead>
                                   <tr>
                                       <th>Catégorie</th>
                                       <th>Date</th>
                                       <th>Montant</th>
                                       <th>Donateur</th>
                                       <th>Receveur</th>
                                       <th>Motif</th>
                                       <th>Action</th>
                                   </tr>
                               </thead>
                               <tbody id='recettesBody'>""" + rows.toString() + """
                               </tbody>
                           </table>
                       </div>
                       <div class='total-bar'>
                           💰 Total :
                           <span class='total-value'>""" + formatComptable(total) + """
                           </span>
                           <span class='devise'>Gdes</span>
                       </div>
                   </div>
               </div>
               <script>
                   function showToast(msg, type) {
                       const t = document.createElement('div');
                       t.className = 'toast ' + type;
                       t.innerText = msg;
                       document.body.appendChild(t);
                       setTimeout(() => t.remove(), 3000);
                   }

                   """ + jsCreate + """

                   """ + jsDelete + """

                   """ + jsFiltres + """
               </script>
               </body>
               </html>""";
    }

    // ============================================================
    // UTILITAIRES
    // ============================================================
    private static String formatComptable(double number) {
        return COMPTA_FORMAT.format(number);
    }

    private static String formatMois(String moisIso) {
        if (moisIso == null || moisIso.length() < 7) return nvl(moisIso);
        try {
            String[] parts = moisIso.split("-");
            int annee = Integer.parseInt(parts[0]);
            int mois = Integer.parseInt(parts[1]);
            String[] noms = {"Janvier", "Février", "Mars", "Avril", "Mai", "Juin",
                             "Juillet", "Août", "Septembre", "Octobre", "Novembre", "Décembre"};
            if (mois >= 1 && mois <= 12) return noms[mois - 1] + " " + annee;
        } catch (NumberFormatException ignored) {}
        return moisIso;
    }

    // ============================================================
    // EXTRACTION ANNÉE / MOIS (robuste vis-à-vis des formats)
    // ============================================================
    private static String extractAnnee(Map<String, Object> r) {
        // Priorité à une colonne dédiée si elle existe
        Object annee = r.get("annee_academique");
        if (annee != null && !annee.toString().isBlank()) return annee.toString();

        // Sinon dériver de la date
        Object date = r.get("date_recette");
        if (date == null) return "";

        String dateStr = date.toString();   // "2025-09-15" ou "2025-09-15 00:00:00"
        if (dateStr.length() < 7) return "";

        int year, month;
        try {
            year = Integer.parseInt(dateStr.substring(0, 4));
            month = Integer.parseInt(dateStr.substring(5, 7));
        } catch (NumberFormatException e) {
            return "";
        }
        // Année académique : sept→déc = année-1/année, janv→août = année/année+1
        return (month >= 9) ? year + "-" + (year + 1) : (year - 1) + "-" + year;
    }

    private static String extractMois(Map<String, Object> r) {
        Object date = r.get("date_recette");
        if (date == null) return "";
        String dateStr = date.toString();
        return dateStr.length() >= 7 ? dateStr.substring(0, 7) : dateStr;
    }

    private static double asDouble(Object o) {
        if (o == null) return 0.0;
        if (o instanceof Number number) return number.doubleValue();
        try { return Double.parseDouble(o.toString()); }
        catch (NumberFormatException e) { return 0.0; }
    }

    private static String nvl(Object o) {
        return o == null ? "" : o.toString();
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String escapeJs(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}