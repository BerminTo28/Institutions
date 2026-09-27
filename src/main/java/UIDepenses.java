import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class UIDepenses {

    /** ✅ Format comptable : séparateur de milliers = virgule, décimales = point */
    private static final DecimalFormat COMPTA_FORMAT;
    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator(',');
        symbols.setDecimalSeparator('.');
        COMPTA_FORMAT = new DecimalFormat("#,##0.00", symbols);
    }

    /** ✅ Palette de couleurs vives pour les barres du graphique */
    private static final String[] COULEURS_BARRES = {
        "#C62828", "#E53935", "#F4511E", "#FB8C00", "#FDD835",
        "#C0CA33", "#7CB342", "#43A047", "#00897B", "#039BE5",
        "#3949AB", "#5E35B1", "#8E24AA", "#D81B60", "#6D4C41"
    };

    // ============================================================
    // POINT D'ENTRÉE
    // ============================================================
    public static String generateHTML(GestionFinanciereService service,
                                      boolean canCreate,
                                      boolean canUpdate,
                                      boolean canDelete) throws SQLException {

        // ============================================================
        // DONNÉES
        // ============================================================
        List<Depenses> depenses = service.getDepenses();
        double total = service.getTotalDepenses() - service.getTotalSalaires();

        // ============================================================
        // AGRÉGATION PAR MOIS (POUR LE GRAPHIQUE)
        // ============================================================
        Map<String, Double> depensesParMois = new LinkedHashMap<>();
        for (Depenses d : depenses) {
            String mois = extractMois(d);
            if (mois == null || mois.isBlank()) continue;
            double montant = d.getMontantDepense();
            depensesParMois.merge(mois, montant, Double::sum);
        }

        List<Map.Entry<String, Double>> moisTries = new ArrayList<>(depensesParMois.entrySet());
        moisTries.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        Double maxMoisObj = moisTries.isEmpty() ? null : moisTries.get(0).getValue();
        double maxMois = (maxMoisObj != null && maxMoisObj > 0) ? maxMoisObj : 1.0;

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
                    .append(couleur).append(" 0%, ")
                    .append(couleur).append("CC 100%); box-shadow: 0 2px 6px ")
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
        // LIGNES DU TABLEAU (avec data-attributes pour les filtres)
        // ============================================================
        StringBuilder rows = new StringBuilder();
        for (Depenses d : depenses) {
            String id = nvl(d.getId());
            String annee = extractAnnee(d);
            String mois = extractMois(d);
            String classesJoined = joinClasses(d.getClasses());
            String etudiant = nvl(d.getMatricule());

            rows.append("<tr")
                .append(" data-annee=\"").append(escapeHtml(annee)).append("\"")
                .append(" data-classes=\"").append(escapeHtml(classesJoined)).append("\"")
                .append(" data-mois=\"").append(escapeHtml(mois)).append("\"")
                .append(" data-etudiant=\"").append(escapeHtml(etudiant)).append("\">")

                .append("<td class='col-executant'>")
                .append(escapeHtml(nvl(d.getResponsableDepense())))
                .append("</td>")

                .append("<td class='col-classe'>")
                .append(escapeHtml(classesJoined.isEmpty() ? "—" : classesJoined.replace("|", ", ")))
                .append("</td>")

                .append("<td class='col-motif'>")
                .append(escapeHtml(nvl(d.getMotif())))
                .append("</td>")

                .append("<td class='col-montant'>")
                .append(formatComptable(d.getMontantDepense()))
                .append(" <span class='devise'>Gdes</span></td>")

                .append("<td class='col-date'>")
                .append(escapeHtml(formatDate(d.getDateDepense())))
                .append("</td>")

                .append("<td class='col-etudiant'>")
                .append(escapeHtml(etudiant.isEmpty() ? "—" : etudiant))
                .append("</td>")

                .append("<td class='col-article'>")
                .append(escapeHtml(nvl(d.getNomDepense())))
                .append("</td>")

                .append("<td class='col-fournisseur'>")
                .append(escapeHtml(nvl(d.getFournisseur())))
                .append("</td>")

                .append("<td class='col-action'>");

            if (canDelete && !id.isEmpty()) {
                rows.append("<button class='btn-danger' onclick=\"supprimerDepense('")
                    .append(escapeJs(id))
                    .append("')\" title=\"Supprimer\">")
                    .append("<i class='fas fa-trash'></i>")
                    .append("</button>");
            } else {
                rows.append("<span class='lock-icon'><i class='fas fa-lock'></i></span>");
            }
            rows.append("</td></tr>");
        }

        // ============================================================
        // FORMULAIRE D'AJOUT
        // ============================================================
        String formulaireAjout;
        if (canCreate) {
            formulaireAjout = """
                <div class='card'>
                    <div class='card-title'><i class='fas fa-plus-circle'></i> Nouvelle dépense</div>
                    <div class='form-grid'>
                        <div class='form-group'>
                            <label>Exécutant</label>
                            <input type='text' id='executant' placeholder='Nom' />
                        </div>
                        <div class='form-group'>
                            <label>Motif</label>
                            <input type='text' id='motif' placeholder='Motif' />
                        </div>
                        <div class='form-group'>
                            <label>Montant (Gdes)</label>
                            <input type='number' id='montant' step='0.01' placeholder='0.00' />
                        </div>
                        <div class='form-group'>
                            <label>Date</label>
                            <input type='date' id='date' />
                        </div>
                        <div class='form-group'>
                            <label>Article</label>
                            <input type='text' id='article' placeholder='Article' />
                        </div>
                        <div class='form-group'>
                            <label>Fournisseur</label>
                            <input type='text' id='fournisseur' placeholder='Fournisseur' />
                        </div>
                        <div class='form-group'>
                            <label>Classe(s) <small>(séparées par des virgules)</small></label>
                            <input type='text' id='classes' placeholder='6ème, 5ème' />
                        </div>
                        <div class='form-group'>
                            <label>Matricule étudiant</label>
                            <input type='text' id='matricule' placeholder='MAT-2025-001' />
                        </div>
                    </div>
                    <button class='btn-primary' id='btnAjouter'>
                        <i class='fas fa-plus'></i> Ajouter la dépense
                    </button>
                </div>
                """;
        } else {
            formulaireAjout = """
                <div class='card card-locked'>
                    <div class='card-title locked-title'>
                        <i class='fas fa-lock'></i> Ajout de dépenses restreint
                    </div>
                    <p class='locked-message'>
                        Vous ne disposez pas des droits nécessaires pour ajouter des dépenses.
                    </p>
                </div>
                """;
        }

        // ============================================================
        // JS CONDITIONNELS
        // ============================================================
        String jsAjout = canCreate ? """
            document.getElementById('date').value = new Date().toISOString().split('T')[0];
            document.getElementById('btnAjouter').onclick = async () => {
                const formData = new URLSearchParams();
                formData.append('executant', document.getElementById('executant').value);
                formData.append('motif', document.getElementById('motif').value);
                formData.append('montant', document.getElementById('montant').value);
                formData.append('date', document.getElementById('date').value);
                formData.append('article', document.getElementById('article').value);
                formData.append('fournisseur', document.getElementById('fournisseur').value);
                formData.append('classes', document.getElementById('classes').value);
                formData.append('matricule', document.getElementById('matricule').value);
                const resp = await fetch('/api/economie/depenses', { method: 'POST', body: formData });
                const data = await resp.json();
                if (data.success) {
                    showToast('Dépense ajoutée', 'success');
                    setTimeout(() => location.reload(), 500);
                } else {
                    showToast(data.message, 'error');
                }
            };
            """ : "// Ajout de dépenses désactivé";

        String jsSuppression = canDelete ? """
            async function supprimerDepense(id) {
                if (!confirm('Supprimer cette dépense ?')) return;
                const formData = new URLSearchParams();
                formData.append('id', id);
                const resp = await fetch('/api/economie/depense/supprimer', { method: 'POST', body: formData });
                const data = await resp.json();
                if (data.success) {
                    showToast('Supprimée', 'success');
                    setTimeout(() => location.reload(), 500);
                } else {
                    showToast(data.message, 'error');
                }
            }
            """ : "// Suppression de dépenses désactivée";

        // ============================================================
        // JS FILTRES CASCADÉS
        // ============================================================
        String jsFiltres = """
            // ============================================================
            // FILTRES CASCADÉS : Année → Classe → Mois → Étudiant
            // ============================================================
            const allRows = Array.from(document.querySelectorAll('#depensesBody tr')).map(row => ({
                el: row,
                annee:    row.dataset.annee    || '',
                classes:  (row.dataset.classes || '').split('|').filter(x => x),
                mois:     row.dataset.mois     || '',
                etudiant: row.dataset.etudiant || ''
            }));

            function uniqueValues(rows, key) {
                const set = new Set();
                rows.forEach(r => {
                    if (key === 'classes') {
                        r.classes.forEach(c => set.add(c));
                    } else {
                        if (r[key]) set.add(r[key]);
                    }
                });
                return Array.from(set).sort();
            }

            function populateSelect(id, values, placeholder) {
                const sel = document.getElementById(id);
                if (!sel) return;
                const current = sel.value;
                sel.innerHTML = '<option value="">' + placeholder + '</option>';
                values.forEach(v => {
                    const opt = document.createElement('option');
                    opt.value = v;
                    opt.textContent = v;
                    sel.appendChild(opt);
                });
                if (values.includes(current)) sel.value = current;
                else sel.value = '';
            }

            function updateAllFilters() {
                const annee    = document.getElementById('filtreAnnee').value;
                const classe   = document.getElementById('filtreClasse').value;
                const mois     = document.getElementById('filtreMois').value;
                const etudiant = document.getElementById('filtreEtudiant').value;

                // 1. Options de classe = classes présentes dans les lignes filtrées par année
                const rowsAfterAnnee = allRows.filter(r => !annee || r.annee === annee);
                populateSelect('filtreClasse', uniqueValues(rowsAfterAnnee, 'classes'), 'Toutes');

                // 2. Options de mois = mois présents dans les lignes filtrées par année + classe
                const classeNow = document.getElementById('filtreClasse').value;
                const rowsAfterClasse = rowsAfterAnnee.filter(r =>
                    !classeNow || r.classes.includes(classeNow));
                populateSelect('filtreMois', uniqueValues(rowsAfterClasse, 'mois'), 'Tous');

                // 3. Options d'étudiant = étudiants présents dans les lignes filtrées par année + classe + mois
                const moisNow = document.getElementById('filtreMois').value;
                const rowsAfterMois = rowsAfterClasse.filter(r =>
                    !moisNow || r.mois === moisNow);
                populateSelect('filtreEtudiant', uniqueValues(rowsAfterMois, 'etudiant'), 'Tous');

                applyFilters();
            }

            function applyFilters() {
                const annee    = document.getElementById('filtreAnnee').value;
                const classe   = document.getElementById('filtreClasse').value;
                const mois     = document.getElementById('filtreMois').value;
                const etudiant = document.getElementById('filtreEtudiant').value;

                let visible = 0;
                allRows.forEach(r => {
                    let ok = true;
                    if (annee    && r.annee !== annee) ok = false;
                    if (classe   && !r.classes.includes(classe)) ok = false;
                    if (mois     && r.mois !== mois) ok = false;
                    if (etudiant && r.etudiant !== etudiant) ok = false;
                    r.el.style.display = ok ? '' : 'none';
                    if (ok) visible++;
                });

                const counter = document.getElementById('resultCount');
                if (counter) {
                    counter.textContent = visible + ' / ' + allRows.length + ' dépense(s)';
                }
            }

            function resetFilters() {
                ['filtreAnnee', 'filtreClasse', 'filtreMois', 'filtreEtudiant'].forEach(id => {
                    const sel = document.getElementById(id);
                    if (sel) sel.value = '';
                });
                updateAllFilters();
            }

            // Initialisation
            document.addEventListener('DOMContentLoaded', () => {
                // Peupler le filtre "Année" (racine, pas de dépendance)
                populateSelect('filtreAnnee', uniqueValues(allRows, 'annee'), 'Toutes');

                ['filtreAnnee', 'filtreClasse', 'filtreMois', 'filtreEtudiant'].forEach(id => {
                    const sel = document.getElementById(id);
                    if (sel) sel.addEventListener('change', updateAllFilters);
                });

                const resetBtn = document.getElementById('btnResetFilters');
                if (resetBtn) resetBtn.addEventListener('click', resetFilters);

                updateAllFilters();
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
                   <title>Dépenses</title>
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
                       .nav-btn.active { background: #C62828; color: white; }
                       .nav-btn:hover:not(.active) { background: #FFEBEE; }

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
                           color: #C62828; margin-bottom: 20px;
                           border-bottom: 2px solid #C62828;
                           padding-bottom: 8px; display: inline-block;
                       }
                       .card-title.locked-title {
                           color: #94a3b8; border-bottom-color: #94a3b8;
                       }
                       .locked-message { color: #64748b; }

                       .filters-grid {
                           display: grid;
                           grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
                           gap: 16px;
                           width: 100%;
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
                       .form-group label small { font-weight: 400; color: #94a3b8; }
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
                           border-color: #C62828;
                           box-shadow: 0 0 0 3px rgba(198, 40, 40, 0.1);
                       }

                       .form-grid {
                           display: grid;
                           grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
                           gap: 16px; margin-bottom: 20px; width: 100%;
                       }

                       .btn-primary {
                           background: #C62828; color: white; border: none;
                           padding: 12px 24px; border-radius: 8px;
                           cursor: pointer; font-weight: 600; font-size: 14px;
                           transition: background 0.2s;
                           display: inline-flex; align-items: center; gap: 8px;
                       }
                       .btn-primary:hover { background: #B71C1C; }
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
                           border-bottom: 2px solid #C62828;
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
                       tbody tr:hover { background: #FFEBEE; }
                       tbody tr:last-child td { border-bottom: none; }

                       .col-executant { font-weight: 700; color: #5D4037; }
                       .col-motif { color: #64748b; }
                       .col-classe { color: #475569; font-size: 13px; }
                       .col-date { color: #475569; font-size: 13px; }
                       .col-etudiant { color: #475569; font-size: 13px; }
                       .col-article { color: #64748b; }
                       .col-fournisseur { color: #64748b; }
                       .col-montant {
                           text-align: right;
                           font-family: 'Courier New', monospace;
                           font-weight: 700;
                           color: #C62828;
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
                           width: 12px; height: 12px; border-radius: 50%;
                           display: inline-block; flex-shrink: 0;
                           box-shadow: 0 0 0 2px rgba(255,255,255,0.9), 0 1px 3px rgba(0,0,0,0.15);
                       }
                       .graph-bar-container {
                           flex: 1; position: relative;
                           background: #F1F5F9;
                           border-radius: 8px; height: 38px;
                           overflow: hidden;
                           display: flex; align-items: center;
                       }
                       .graph-bar {
                           height: 100%;
                           border-radius: 8px;
                           transition: width 0.8s cubic-bezier(0.4, 0, 0.2, 1);
                           min-width: 6px; position: relative;
                       }
                       .graph-bar::after {
                           content: '';
                           position: absolute;
                           top: 0; left: 0; right: 0; bottom: 0;
                           background: linear-gradient(180deg, rgba(255,255,255,0.25) 0%, rgba(255,255,255,0) 100%);
                           border-radius: 8px; pointer-events: none;
                       }
                       .graph-value {
                           position: absolute; right: 12px;
                           top: 50%; transform: translateY(-50%);
                           font-family: 'Courier New', monospace;
                           font-weight: 700; font-size: 12.5px;
                           color: #1E293B;
                           background: rgba(255,255,255,0.92);
                           padding: 3px 10px; border-radius: 6px;
                           white-space: nowrap;
                           box-shadow: 0 1px 3px rgba(0,0,0,0.08);
                           z-index: 2;
                       }
                       .graph-value .devise {
                           font-size: 10px; color: #94a3b8;
                           font-family: 'Inter', sans-serif;
                       }
                       .graph-empty {
                           text-align: center; padding: 40px;
                           color: #94a3b8; font-style: italic;
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
                           color: #C62828;
                           font-size: 18px;
                           margin-left: 8px;
                       }
                       .total-bar .devise {
                           font-size: 13px; color: #94a3b8;
                           font-weight: 500; margin-left: 4px;
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
                       <a href='/admin/economie/recettes/externes' class='nav-btn'><i class='fas fa-hand-holding-usd'></i> Recettes externes</a>
                       <a href='/admin/economie/depenses' class='nav-btn active'><i class='fas fa-arrow-down'></i> Dépenses</a>
                       <a href='/admin/economie/salaires' class='nav-btn'><i class='fas fa-users'></i> Salaires</a>
                   </div>

                   """ + formulaireAjout + """

                   <div class='card'>
                       <div class='card-title'><i class='fas fa-filter'></i> Filtres</div>
                       <div class='filters-grid'>
                           <div class='form-group'>
                               <label><i class='fas fa-calendar-alt'></i> Année académique</label>
                               <select id='filtreAnnee'>
                                   <option value=''>Toutes</option>
                               </select>
                           </div>
                           <div class='form-group'>
                               <label><i class='fas fa-chalkboard'></i> Classe</label>
                               <select id='filtreClasse'>
                                   <option value=''>Toutes</option>
                               </select>
                           </div>
                           <div class='form-group'>
                               <label><i class='fas fa-clock'></i> Mois</label>
                               <select id='filtreMois'>
                                   <option value=''>Tous</option>
                               </select>
                           </div>
                           <div class='form-group'>
                               <label><i class='fas fa-user-graduate'></i> Étudiant (matricule)</label>
                               <select id='filtreEtudiant'>
                                   <option value=''>Tous</option>
                               </select>
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
                       <div class='card-title'><i class='fas fa-chart-bar'></i> Répartition mensuelle des dépenses</div>
                       <div class='graph-container'>
                           """ + graphBars.toString() + """
                       </div>
                   </div>

                   <div class='card'>
                       <div class='card-title'><i class='fas fa-list'></i> Liste des dépenses</div>
                       <div class='table-responsive'>
                           <table>
                               <thead>
                                   <tr>
                                       <th>Exécutant</th>
                                       <th>Classe</th>
                                       <th>Motif</th>
                                       <th>Montant</th>
                                       <th>Date</th>
                                       <th>Étudiant</th>
                                       <th>Article</th>
                                       <th>Fournisseur</th>
                                       <th>Action</th>
                                   </tr>
                               </thead>
                               <tbody id='depensesBody'>""" + rows.toString() + """
                               </tbody>
                           </table>
                       </div>
                       <div class='total-bar'>
                           💰 Total (hors salaires) :
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

                   """ + jsAjout + """

                   """ + jsSuppression + """

                   """ + jsFiltres + """
               </script>
               </body>
               </html>""";
    }

    // ============================================================
    // UTILITAIRES DE FORMATAGE
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

    private static String formatDate(Date date) {
        if (date == null) return "";
        return new SimpleDateFormat("yyyy-MM-dd").format(date);
    }

    // ============================================================
    // EXTRACTION ANNÉE / MOIS (avec fallback sur dateDepense)
    // ============================================================
    private static String extractAnnee(Depenses d) {
        String a = d.getAnneeAcademique();
        if (a != null && !a.isBlank()) return a;
        Date date = d.getDateDepense();
        if (date == null) return "";
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTime(date);
        int year = cal.get(java.util.Calendar.YEAR);
        int month = cal.get(java.util.Calendar.MONTH) + 1;
        if (month >= 9) return year + "-" + (year + 1);
        return (year - 1) + "-" + year;
    }

    private static String extractMois(Depenses d) {
        String m = d.getMois();
        if (m != null && !m.isBlank()) return m;
        Date date = d.getDateDepense();
        if (date == null) return "";
        return new SimpleDateFormat("yyyy-MM").format(date);
    }

    private static String joinClasses(List<String> classes) {
        if (classes == null || classes.isEmpty()) return "";
        List<String> clean = new ArrayList<>();
        for (String c : classes) {
            if (c != null && !c.isBlank()) clean.add(c.trim());
        }
        return String.join("|", clean);
    }

    // ============================================================
    // ESCAPES
    // ============================================================
    private static String nvl(String s) {
        return s == null ? "" : s;
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