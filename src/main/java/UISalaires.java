import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class UISalaires {

    private static final DecimalFormat COMPTA_FORMAT;
    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator(',');
        symbols.setDecimalSeparator('.');
        COMPTA_FORMAT = new DecimalFormat("#,##0.00", symbols);
    }

    private static final String[] COULEURS_BARRES = {
        "#F39C12", "#E67E22", "#E74C3C", "#C0392B", "#9B59B6",
        "#8E44AD", "#3498DB", "#2980B9", "#1ABC9C", "#16A085",
        "#2ECC71", "#27AE60", "#F1C40F", "#F39C12", "#D35400"
    };

    public static String generateHTML(GestionFinanciereService service,
                                      boolean canCreate,
                                      boolean canUpdate,
                                      boolean canDelete) throws SQLException {

        // ============================================================
        // DONNÉES
        // ============================================================
        List<Employe> employes = service.getEmployes();
        double total = service.getTotalSalaires();

        // ============================================================
        // GRAPHIQUE : salaires par mois (agrégation sur data-mois)
        // ============================================================
        Map<String, Double> salairesParMois = new LinkedHashMap<>();
        for (Employe e : employes) {
            String mois = extractMois(e);
            if (mois == null || mois.isBlank()) continue;
            salairesParMois.merge(mois, e.getSalaireNet(), Double::sum);
        }
        List<Map.Entry<String, Double>> moisTries = new ArrayList<>(salairesParMois.entrySet());
        moisTries.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        double maxMois = 1.0;
        if (!moisTries.isEmpty() && moisTries.get(0).getValue() > 0) {
            maxMois = moisTries.get(0).getValue();
        }

        StringBuilder graphMois = new StringBuilder();
        if (moisTries.isEmpty()) {
            graphMois.append("<div class='graph-empty'>Aucune donnée mensuelle disponible</div>");
        } else {
            int index = 0;
            for (Map.Entry<String, Double> entry : moisTries) {
                String mois = entry.getKey();
                double montant = entry.getValue();
                double pourcentage = (montant / maxMois) * 100.0;
                String couleur = COULEURS_BARRES[index % COULEURS_BARRES.length];

                graphMois.append("<div class='graph-row'>")
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
                    .append("</div></div>");
                index++;
            }
        }

        // ============================================================
        // GRAPHIQUE : salaires par employé
        // ============================================================
        Map<String, Double> salairesParEmploye = new LinkedHashMap<>();
        for (Employe e : employes) {
            if (e.getNom() != null) {
                salairesParEmploye.merge(e.getNom(), e.getSalaireNet(), Double::sum);
            }
        }
        List<Map.Entry<String, Double>> employesTries = new ArrayList<>(salairesParEmploye.entrySet());
        employesTries.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        double maxEmploye = 1.0;
        if (!employesTries.isEmpty() && employesTries.get(0).getValue() > 0) {
            maxEmploye = employesTries.get(0).getValue();
        }

        StringBuilder graphEmployes = new StringBuilder();
        if (employesTries.isEmpty()) {
            graphEmployes.append("<div class='graph-empty'>Aucune donnée employé disponible</div>");
        } else {
            int index = 0;
            for (Map.Entry<String, Double> entry : employesTries) {
                String nom = entry.getKey();
                double montant = entry.getValue();
                double pourcentage = (montant / maxEmploye) * 100.0;
                String couleur = COULEURS_BARRES[index % COULEURS_BARRES.length];

                graphEmployes.append("<div class='graph-row'>")
                    .append("<div class='graph-label'>")
                    .append("<span class='graph-color-dot' style='background:")
                    .append(couleur).append(";'></span>")
                    .append(escapeHtml(nom))
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
                    .append("</div></div>");
                index++;
            }
        }

        // ============================================================
        // LIGNES DU TABLEAU
        // ============================================================
        StringBuilder rows = new StringBuilder();
        for (Employe e : employes) {
            String id = String.valueOf(e.getId());
            String annee = extractAnnee(e);
            String mois = extractMois(e);
            String classesJoined = joinClasses(e.getClasses());

            rows.append("<tr")
                .append(" data-annee=\"").append(escapeHtml(annee)).append("\"")
                .append(" data-classes=\"").append(escapeHtml(classesJoined)).append("\"")
                .append(" data-mois=\"").append(escapeHtml(mois)).append("\"")
                .append(" data-employe=\"").append(escapeHtml(id)).append("\">");

            rows.append("<td class='col-id'>").append(escapeHtml(id)).append("</td>");
            rows.append("<td class='col-nom'>").append(escapeHtml(nvl(e.getNom()))).append("</td>");
            rows.append("<td class='col-poste'>").append(escapeHtml(nvl(e.getPoste()))).append("</td>");
            rows.append("<td class='col-classe'>")
                .append(escapeHtml(classesJoined.isEmpty() ? "—" : classesJoined.replace("|", ", ")))
                .append("</td>");
            rows.append("<td class='col-montant'>").append(formatComptable(e.getSalaireBase()))
                .append(" <span class='devise'>Gdes</span></td>");
            rows.append("<td class='col-montant col-deduction'>").append(formatComptable(e.getDeductions()))
                .append(" <span class='devise'>Gdes</span></td>");
            rows.append("<td class='col-montant col-net'><strong>").append(formatComptable(e.getSalaireNet()))
                .append("</strong> <span class='devise'>Gdes</span></td>");
            rows.append("<td class='col-action'>");

            if (canUpdate && !id.isEmpty() && !"0".equals(id)) {
                rows.append("<button class='btn-edit' onclick=\"modifierEmploye(")
                    .append(escapeJs(id)).append(")\" title=\"Modifier\">")
                    .append("<i class='fas fa-pen'></i></button> ");
            } else {
                rows.append("<span class='lock-icon'><i class='fas fa-lock'></i></span> ");
            }
            if (canDelete && !id.isEmpty() && !"0".equals(id)) {
                rows.append("<button class='btn-danger' onclick=\"supprimerEmploye(")
                    .append(escapeJs(id)).append(")\" title=\"Supprimer\">")
                    .append("<i class='fas fa-trash'></i></button>");
            } else {
                rows.append("<span class='lock-icon'><i class='fas fa-lock'></i></span>");
            }
            rows.append("</td></tr>");
        }

        // ============================================================
        // FORMULAIRE D'AJOUT
        // ============================================================
        StringBuilder formulaireAjout = new StringBuilder();
        if (canCreate) {
            formulaireAjout.append("<div class='card'>");
            formulaireAjout.append("<div class='card-title'><i class='fas fa-user-plus'></i> Ajouter un employé</div>");
            formulaireAjout.append("<div class='form-grid'>");
            formulaireAjout.append("<div class='form-group'><label>Nom</label><input type='text' id='nom' placeholder='Nom complet' /></div>");
            formulaireAjout.append("<div class='form-group'><label>Poste</label><input type='text' id='poste' placeholder='Poste' /></div>");
            formulaireAjout.append("<div class='form-group'><label>Année académique</label><input type='text' id='anneeAcademique' placeholder='2025-2026' /></div>");
            formulaireAjout.append("<div class='form-group'><label>Mois</label><input type='month' id='mois' /></div>");
            formulaireAjout.append("<div class='form-group'><label>Classe(s) <small>(virgules)</small></label><input type='text' id='classes' placeholder='6ème, 5ème' /></div>");
            formulaireAjout.append("<div class='form-group'><label>Salaire de base (Gdes)</label><input type='number' id='salaireBase' step='0.01' placeholder='0.00' /></div>");
            formulaireAjout.append("<div class='form-group'><label>Déductions (Gdes)</label><input type='number' id='deductions' step='0.01' value='0' /></div>");
            formulaireAjout.append("</div>");
            formulaireAjout.append("<button class='btn-primary' id='btnAjouter'><i class='fas fa-plus'></i> Ajouter l'employé</button>");
            formulaireAjout.append("</div>");
        } else {
            formulaireAjout.append("<div class='card card-locked'>");
            formulaireAjout.append("<div class='card-title locked-title'><i class='fas fa-lock'></i> Ajout d'employés restreint</div>");
            formulaireAjout.append("<p class='locked-message'>Vous ne disposez pas des droits nécessaires pour ajouter des employés.</p>");
            formulaireAjout.append("</div>");
        }

        // ============================================================
        // MODALE MODIFIER
        // ============================================================
        StringBuilder modalHtml = new StringBuilder();
        if (canUpdate) {
            modalHtml.append("<div id='modalModifier' class='modal'>");
            modalHtml.append("<div class='modal-content'>");
            modalHtml.append("<div class='modal-header'>");
            modalHtml.append("<h3><i class='fas fa-pen'></i> Modifier l'employé</h3>");
            modalHtml.append("<span class='close-modal' onclick='fermerModal()'>&times;</span>");
            modalHtml.append("</div>");
            modalHtml.append("<div class='form-grid'>");
            modalHtml.append("<input type='hidden' id='editId' />");
            modalHtml.append("<div class='form-group'><label>Nom</label><input type='text' id='editNom' /></div>");
            modalHtml.append("<div class='form-group'><label>Poste</label><input type='text' id='editPoste' /></div>");
            modalHtml.append("<div class='form-group'><label>Année</label><input type='text' id='editAnnee' /></div>");
            modalHtml.append("<div class='form-group'><label>Mois</label><input type='month' id='editMois' /></div>");
            modalHtml.append("<div class='form-group'><label>Classes</label><input type='text' id='editClasses' /></div>");
            modalHtml.append("<div class='form-group'><label>Salaire de base</label><input type='number' id='editBase' step='0.01' /></div>");
            modalHtml.append("<div class='form-group'><label>Déductions</label><input type='number' id='editDeductions' step='0.01' /></div>");
            modalHtml.append("</div>");
            modalHtml.append("<button class='btn-primary' id='btnModifier' style='width:100%;'><i class='fas fa-save'></i> Enregistrer</button>");
            modalHtml.append("</div></div>");
        }

        // ============================================================
        // JS AJOUT
        // ============================================================
        StringBuilder jsAjout = new StringBuilder();
        if (canCreate) {
            jsAjout.append("document.getElementById('btnAjouter').onclick = async () => {");
            jsAjout.append("const formData = new URLSearchParams();");
            jsAjout.append("formData.append('nom', document.getElementById('nom').value);");
            jsAjout.append("formData.append('poste', document.getElementById('poste').value);");
            jsAjout.append("formData.append('anneeAcademique', document.getElementById('anneeAcademique').value);");
            jsAjout.append("formData.append('mois', document.getElementById('mois').value);");
            jsAjout.append("formData.append('classes', document.getElementById('classes').value);");
            jsAjout.append("formData.append('salaireBase', document.getElementById('salaireBase').value);");
            jsAjout.append("formData.append('deductions', document.getElementById('deductions').value);");
            jsAjout.append("const resp = await fetch('/api/economie/employes', { method: 'POST', body: formData });");
            jsAjout.append("const data = await resp.json();");
            jsAjout.append("if (data.success) { showToast('Employé ajouté', 'success'); setTimeout(() => location.reload(), 500); }");
            jsAjout.append("else showToast(data.message, 'error');");
            jsAjout.append("};");
        } else {
            jsAjout.append("// Ajout désactivé");
        }

        // ============================================================
        // JS MODIFICATION
        // ============================================================
        StringBuilder jsModification = new StringBuilder();
        if (canUpdate) {
            jsModification.append("function modifierEmploye(id) {");
            jsModification.append("fetch('/api/economie/employes').then(r => r.json()).then(data => {");
            jsModification.append("const emp = (data.employes || []).find(e => String(e.id) === String(id));");
            jsModification.append("if (!emp) { showToast('Employé introuvable', 'error'); return; }");
            jsModification.append("document.getElementById('editId').value = emp.id;");
            jsModification.append("document.getElementById('editNom').value = emp.nom || '';");
            jsModification.append("document.getElementById('editPoste').value = emp.poste || '';");
            jsModification.append("document.getElementById('editAnnee').value = emp.anneeAcademique || '';");
            jsModification.append("document.getElementById('editMois').value = emp.mois || '';");
            jsModification.append("document.getElementById('editClasses').value = (emp.classes || []).join(', ');");
            jsModification.append("document.getElementById('editBase').value = emp.salaireBase;");
            jsModification.append("document.getElementById('editDeductions').value = emp.deductions;");
            jsModification.append("document.getElementById('modalModifier').classList.add('active');");
            jsModification.append("});}");
            jsModification.append("function fermerModal() {");
            jsModification.append("document.getElementById('modalModifier').classList.remove('active');");
            jsModification.append("}");
            jsModification.append("document.getElementById('btnModifier').onclick = async () => {");
            jsModification.append("const formData = new URLSearchParams();");
            jsModification.append("formData.append('id', document.getElementById('editId').value);");
            jsModification.append("formData.append('nom', document.getElementById('editNom').value);");
            jsModification.append("formData.append('poste', document.getElementById('editPoste').value);");
            jsModification.append("formData.append('anneeAcademique', document.getElementById('editAnnee').value);");
            jsModification.append("formData.append('mois', document.getElementById('editMois').value);");
            jsModification.append("formData.append('classes', document.getElementById('editClasses').value);");
            jsModification.append("formData.append('salaireBase', document.getElementById('editBase').value);");
            jsModification.append("formData.append('deductions', document.getElementById('editDeductions').value);");
            jsModification.append("const resp = await fetch('/api/economie/employe/modifier', { method: 'POST', body: formData });");
            jsModification.append("const data = await resp.json();");
            jsModification.append("if (data.success) { showToast('Modifié', 'success'); fermerModal(); setTimeout(() => location.reload(), 500); }");
            jsModification.append("else showToast(data.message, 'error');");
            jsModification.append("};");
            jsModification.append("window.onclick = function(event) {");
            jsModification.append("if (event.target === document.getElementById('modalModifier')) fermerModal();");
            jsModification.append("};");
        } else {
            jsModification.append("// Modification désactivée");
        }

        // ============================================================
        // JS SUPPRESSION
        // ============================================================
        StringBuilder jsSuppression = new StringBuilder();
        if (canDelete) {
            jsSuppression.append("async function supprimerEmploye(id) {");
            jsSuppression.append("if (!confirm('Supprimer cet employé ?')) return;");
            jsSuppression.append("const formData = new URLSearchParams();");
            jsSuppression.append("formData.append('id', id);");
            jsSuppression.append("const resp = await fetch('/api/economie/employe/supprimer', { method: 'POST', body: formData });");
            jsSuppression.append("const data = await resp.json();");
            jsSuppression.append("if (data.success) { showToast('Supprimé', 'success'); setTimeout(() => location.reload(), 500); }");
            jsSuppression.append("else showToast(data.message, 'error');");
            jsSuppression.append("}");
        } else {
            jsSuppression.append("// Suppression désactivée");
        }

        // ============================================================
        // JS FILTRES CASCADÉS
        // ============================================================
        String jsFiltres = """
            // ============================================================
            // FILTRES CASCADÉS : Année → Classe → Mois → Employé
            // ============================================================
            const allRows = Array.from(document.querySelectorAll('#employesBody tr')).map(row => ({
                el: row,
                annee:      row.dataset.annee    || '',
                classes:    (row.dataset.classes || '').split('|').filter(x => x),
                mois:       row.dataset.mois     || '',
                employe:    row.dataset.employe  || '',
                nom:        (row.querySelector('.col-nom') || {}).textContent?.trim() || ''
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

            function updateAllFilters() {
                const annee    = document.getElementById('filtreAnnee').value;
                const classe   = document.getElementById('filtreClasse').value;
                const mois     = document.getElementById('filtreMois').value;

                // 1) Classes présentes dans les lignes filtrées par année
                const rowsAfterAnnee = allRows.filter(r => !annee || r.annee === annee);
                populateSelect('filtreClasse', uniqueValues(rowsAfterAnnee, 'classes'), 'Toutes');

                // 2) Mois présents dans les lignes filtrées par année + classe
                const classeNow = document.getElementById('filtreClasse').value;
                const rowsAfterClasse = rowsAfterAnnee.filter(r =>
                    !classeNow || r.classes.includes(classeNow));
                populateSelect('filtreMois', uniqueValues(rowsAfterClasse, 'mois'), 'Tous',
                    m => m);

                // 3) Employés présents dans les lignes filtrées par année + classe + mois
                const moisNow = document.getElementById('filtreMois').value;
                const rowsAfterMois = rowsAfterClasse.filter(r =>
                    !moisNow || r.mois === moisNow);

                const empMap = new Map();
                rowsAfterMois.forEach(r => {
                    if (r.employe) empMap.set(r.employe, r.nom);
                });
                const empIds = Array.from(empMap.keys()).sort((a, b) =>
                    (empMap.get(a) || '').localeCompare(empMap.get(b) || ''));
                populateSelect('filtreEmploye', empIds, 'Tous',
                    id => empMap.get(id) || id);

                applyFilters();
            }

            function applyFilters() {
                const annee    = document.getElementById('filtreAnnee').value;
                const classe   = document.getElementById('filtreClasse').value;
                const mois     = document.getElementById('filtreMois').value;
                const employe  = document.getElementById('filtreEmploye').value;

                let visible = 0;
                allRows.forEach(r => {
                    let ok = true;
                    if (annee   && r.annee !== annee) ok = false;
                    if (classe  && !r.classes.includes(classe)) ok = false;
                    if (mois    && r.mois !== mois) ok = false;
                    if (employe && r.employe !== employe) ok = false;
                    r.el.style.display = ok ? '' : 'none';
                    if (ok) visible++;
                });

                const counter = document.getElementById('resultCount');
                if (counter) {
                    counter.textContent = visible + ' / ' + allRows.length + ' employé(s)';
                }
            }

            function resetFilters() {
                ['filtreAnnee', 'filtreClasse', 'filtreMois', 'filtreEmploye'].forEach(id => {
                    const sel = document.getElementById(id);
                    if (sel) sel.value = '';
                });
                updateAllFilters();
            }

            document.addEventListener('DOMContentLoaded', () => {
                populateSelect('filtreAnnee', uniqueValues(allRows, 'annee'), 'Toutes');

                ['filtreAnnee', 'filtreClasse', 'filtreMois', 'filtreEmploye'].forEach(id => {
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
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>");
        html.append("<html lang='fr'>");
        html.append("<head>");
        html.append("<meta charset='UTF-8'>");
        html.append("<meta name='viewport' content='width=device-width, initial-scale=1.0'>");
        html.append("<title>Salaires</title>");
        html.append("<link href='https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700;800&display=swap' rel='stylesheet'>");
        html.append("<link rel='stylesheet' href='https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css'>");
        html.append("<style>");
        html.append("* { margin: 0; padding: 0; box-sizing: border-box; }");
        html.append("html, body { width: 100%; min-height: 100vh; background: #FAFBFB; font-family: 'Inter', sans-serif; }");
        html.append("body { padding: 20px; display: flex; justify-content: center; }");
        html.append(".container { width: 100%; max-width: 1600px; margin: 0 auto; }");
        html.append(".nav-menu { display: flex; gap: 8px; background: white; border-radius: 12px; padding: 8px; margin-bottom: 20px; border: 1px solid #DCE1E6; flex-wrap: wrap; width: 100%; justify-content: center; }");
        html.append(".nav-btn { flex: 1 1 180px; text-align: center; padding: 12px 20px; background: none; border: none; cursor: pointer; font-weight: 600; border-radius: 8px; transition: all 0.2s ease-in-out; text-decoration: none; color: #333; }");
        html.append(".nav-btn.active { background: #F39C12; color: white; }");
        html.append(".nav-btn:hover:not(.active) { background: #FFF8E1; }");
        html.append(".card { background: white; border-radius: 16px; padding: 24px; margin-bottom: 20px; box-shadow: 0 2px 8px rgba(0,0,0,0.06); border: 1px solid #DCE1E6; width: 100%; text-align: center; }");
        html.append(".card-locked { background: #f8fafc; }");
        html.append(".card-title { font-size: 18px; font-weight: 700; color: #F39C12; margin-bottom: 20px; border-bottom: 2px solid #F39C12; padding-bottom: 8px; display: inline-block; }");
        html.append(".card-title.locked-title { color: #94a3b8; border-bottom-color: #94a3b8; }");
        html.append(".locked-message { color: #64748b; }");
        html.append(".filters-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 16px; width: 100%; margin: 0 auto 12px; }");
        html.append(".filters-actions { display: flex; justify-content: center; gap: 12px; margin-top: 8px; align-items: center; flex-wrap: wrap; }");
        html.append(".btn-reset { background: #64748b; color: white; border: none; padding: 10px 20px; border-radius: 8px; cursor: pointer; font-weight: 600; font-size: 13px; display: inline-flex; align-items: center; gap: 8px; }");
        html.append(".btn-reset:hover { background: #475569; }");
        html.append(".result-count { font-size: 13px; color: #64748b; font-weight: 500; }");
        html.append(".form-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: 16px; margin-bottom: 20px; width: 100%; }");
        html.append(".form-group { display: flex; flex-direction: column; gap: 6px; align-items: center; }");
        html.append(".form-group label { font-size: 13px; font-weight: 600; color: #334155; display: flex; align-items: center; justify-content: center; gap: 6px; }");
        html.append(".form-group label small { font-weight: 400; color: #94a3b8; }");
        html.append(".form-group input, .form-group select { padding: 10px 14px; border: 1px solid #DCE1E6; border-radius: 8px; font-size: 14px; font-family: 'Inter', sans-serif; width: 100%; background: white; text-align: center; }");
        html.append(".form-group input:focus, .form-group select:focus { outline: none; border-color: #F39C12; box-shadow: 0 0 0 3px rgba(243, 156, 18, 0.1); }");
        html.append(".btn-primary { background: #F39C12; color: white; border: none; padding: 12px 24px; border-radius: 8px; cursor: pointer; font-weight: 600; font-size: 14px; transition: background 0.2s; display: inline-flex; align-items: center; gap: 8px; }");
        html.append(".btn-primary:hover { background: #D68910; }");
        html.append(".btn-edit { background: #F39C12; color: white; border: none; padding: 8px 12px; border-radius: 6px; cursor: pointer; font-size: 13px; display: inline-flex; align-items: center; justify-content: center; min-width: 36px; }");
        html.append(".btn-edit:hover { opacity: 0.85; }");
        html.append(".btn-danger { background: #C62828; color: white; border: none; padding: 8px 12px; border-radius: 6px; cursor: pointer; font-size: 13px; display: inline-flex; align-items: center; justify-content: center; min-width: 36px; }");
        html.append(".btn-danger:hover { opacity: 0.85; }");
        html.append(".lock-icon { color: #94a3b8; font-size: 14px; }");
        html.append(".table-responsive { width: 100%; overflow-x: auto; border-radius: 12px; border: 1px solid #DCE1E6; }");
        html.append("table { width: 100%; border-collapse: collapse; font-size: 14px; }");
        html.append("th { background: #FFF5C8; padding: 14px 16px; font-weight: 700; border-bottom: 2px solid #F39C12; font-size: 13px; color: #5D4037; text-transform: uppercase; letter-spacing: 0.5px; text-align: center; }");
        html.append("td { padding: 12px 16px; border-bottom: 1px solid #E2E8F0; font-size: 14px; vertical-align: middle; text-align: center; }");
        html.append("tbody tr:hover { background: #FFF8E1; }");
        html.append("tbody tr:last-child td { border-bottom: none; }");
        html.append(".col-id { text-align: center; font-family: 'Courier New', monospace; font-size: 12px; color: #64748b; }");
        html.append(".col-nom { text-align: center; font-weight: 700; color: #5D4037; }");
        html.append(".col-poste { text-align: center; color: #475569; }");
        html.append(".col-classe { text-align: center; color: #475569; font-size: 13px; }");
        html.append(".col-montant { text-align: right; font-family: 'Courier New', monospace; font-weight: 700; color: #475569; font-size: 14px; }");
        html.append(".col-montant.col-deduction { color: #C62828; }");
        html.append(".col-montant.col-net { color: #16a34a; font-size: 15px; font-weight: 800; }");
        html.append(".col-montant .devise { font-size: 11px; color: #94a3b8; font-weight: 500; margin-left: 4px; font-family: 'Inter', sans-serif; }");
        html.append(".col-action { text-align: center; width: 130px; }");
        html.append(".graph-container { width: 100%; max-width: 1000px; margin: 0 auto; padding: 10px; }");
        html.append(".graph-row { display: flex; align-items: center; margin-bottom: 14px; gap: 14px; }");
        html.append(".graph-label { width: 180px; text-align: right; font-weight: 700; color: #5D4037; font-size: 13px; flex-shrink: 0; display: flex; align-items: center; justify-content: flex-end; gap: 8px; }");
        html.append(".graph-color-dot { width: 12px; height: 12px; border-radius: 50%; display: inline-block; flex-shrink: 0; box-shadow: 0 0 0 2px rgba(255,255,255,0.9), 0 1px 3px rgba(0,0,0,0.15); }");
        html.append(".graph-bar-container { flex: 1; position: relative; background: #F1F5F9; border-radius: 8px; height: 38px; overflow: hidden; display: flex; align-items: center; }");
        html.append(".graph-bar { height: 100%; border-radius: 8px; transition: width 0.8s cubic-bezier(0.4, 0, 0.2, 1); min-width: 6px; position: relative; }");
        html.append(".graph-bar::after { content: ''; position: absolute; top: 0; left: 0; right: 0; bottom: 0; background: linear-gradient(180deg, rgba(255,255,255,0.25) 0%, rgba(255,255,255,0) 100%); border-radius: 8px; pointer-events: none; }");
        html.append(".graph-value { position: absolute; right: 12px; top: 50%; transform: translateY(-50%); font-family: 'Courier New', monospace; font-weight: 700; font-size: 12.5px; color: #1E293B; background: rgba(255,255,255,0.92); padding: 3px 10px; border-radius: 6px; white-space: nowrap; box-shadow: 0 1px 3px rgba(0,0,0,0.08); z-index: 2; }");
        html.append(".graph-value .devise { font-size: 10px; color: #94a3b8; font-family: 'Inter', sans-serif; }");
        html.append(".graph-empty { text-align: center; padding: 40px; color: #94a3b8; font-style: italic; }");
        html.append(".total-bar { margin-top: 20px; text-align: right; font-weight: bold; font-size: 16px; padding: 16px 20px; background: white; border-radius: 12px; border: 1px solid #DCE1E6; color: #333; }");
        html.append(".total-bar .total-value { font-family: 'Courier New', monospace; font-weight: 800; color: #F39C12; font-size: 18px; margin-left: 8px; }");
        html.append(".total-bar .devise { font-size: 13px; color: #94a3b8; font-weight: 500; margin-left: 4px; font-family: 'Inter', sans-serif; }");
        html.append(".modal { display: none; position: fixed; top: 0; left: 0; width: 100%; height: 100%; background: rgba(0,0,0,0.5); justify-content: center; align-items: center; z-index: 1000; padding: 20px; }");
        html.append(".modal.active { display: flex; }");
        html.append(".modal-content { background: white; padding: 30px; border-radius: 16px; max-width: 600px; width: 100%; box-shadow: 0 10px 25px rgba(0,0,0,0.2); }");
        html.append(".modal-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; border-bottom: 2px solid #F39C12; padding-bottom: 8px; }");
        html.append(".modal-header h3 { color: #F39C12; font-size: 18px; display: flex; align-items: center; gap: 8px; }");
        html.append(".close-modal { font-size: 28px; cursor: pointer; color: #64748B; transition: color 0.2s; line-height: 1; }");
        html.append(".close-modal:hover { color: #0F172A; }");
        html.append(".toast { position: fixed; bottom: 30px; right: 30px; background: #333; color: white; padding: 12px 20px; border-radius: 8px; z-index: 1100; animation: fadeInOut 3s ease; font-weight: 500; box-shadow: 0 4px 12px rgba(0,0,0,0.15); }");
        html.append(".toast.success { background: #27ae60; }");
        html.append(".toast.error { background: #ef4444; }");
        html.append("@keyframes fadeInOut { 0% { opacity: 0; transform: translateY(10px); } 15% { opacity: 1; transform: translateY(0); } 85% { opacity: 1; transform: translateY(0); } 100% { opacity: 0; transform: translateY(10px); } }");
        html.append("</style>");
        html.append("</head>");
        html.append("<body>");
        html.append("<div class='container'>");
        html.append("<div class='nav-menu'>");
        html.append("<a href='/admin/economie/budget' class='nav-btn'><i class='fas fa-chart-pie'></i> Budget</a>");
        html.append("<a href='/admin/economie/recettes/internes' class='nav-btn'><i class='fas fa-school'></i> Recettes internes</a>");
        html.append("<a href='/admin/economie/recettes/externes' class='nav-btn'><i class='fas fa-hand-holding-usd'></i> Recettes externes</a>");
        html.append("<a href='/admin/economie/depenses' class='nav-btn'><i class='fas fa-arrow-down'></i> Dépenses</a>");
        html.append("<a href='/admin/economie/salaires' class='nav-btn active'><i class='fas fa-users'></i> Salaires</a>");
        html.append("</div>");

        html.append(formulaireAjout);

        // ----- FILTRES -----
        html.append("<div class='card'>");
        html.append("<div class='card-title'><i class='fas fa-filter'></i> Filtres</div>");
        html.append("<div class='filters-grid'>");
        html.append("<div class='form-group'>");
        html.append("<label><i class='fas fa-calendar-alt'></i> Année académique</label>");
        html.append("<select id='filtreAnnee'><option value=''>Toutes</option></select>");
        html.append("</div>");
        html.append("<div class='form-group'>");
        html.append("<label><i class='fas fa-chalkboard'></i> Classe</label>");
        html.append("<select id='filtreClasse'><option value=''>Toutes</option></select>");
        html.append("</div>");
        html.append("<div class='form-group'>");
        html.append("<label><i class='fas fa-clock'></i> Mois</label>");
        html.append("<select id='filtreMois'><option value=''>Tous</option></select>");
        html.append("</div>");
        html.append("<div class='form-group'>");
        html.append("<label><i class='fas fa-user'></i> Employé</label>");
        html.append("<select id='filtreEmploye'><option value=''>Tous</option></select>");
        html.append("</div>");
        html.append("</div>");
        html.append("<div class='filters-actions'>");
        html.append("<button id='btnResetFilters' class='btn-reset' type='button'><i class='fas fa-undo'></i> Réinitialiser</button>");
        html.append("<span id='resultCount' class='result-count'></span>");
        html.append("</div>");
        html.append("</div>");

        // ----- GRAPHIQUES -----
        html.append("<div class='card'>");
        html.append("<div class='card-title'><i class='fas fa-chart-bar'></i> Répartition mensuelle des salaires</div>");
        html.append("<div class='graph-container'>").append(graphMois).append("</div>");
        html.append("</div>");

        html.append("<div class='card'>");
        html.append("<div class='card-title'><i class='fas fa-chart-bar'></i> Consommation des salaires par employé</div>");
        html.append("<div class='graph-container' id='graphEmployesBody'>").append(graphEmployes).append("</div>");
        html.append("</div>");

        // ----- TABLEAU -----
        html.append("<div class='card'>");
        html.append("<div class='card-title'><i class='fas fa-users'></i> Liste des employés</div>");
        html.append("<div class='table-responsive'>");
        html.append("<table>");
        html.append("<thead><tr>");
        html.append("<th>ID</th><th>Nom</th><th>Poste</th><th>Classe</th>");
        html.append("<th>Salaire base</th><th>Déductions</th><th>Salaire net</th>");
        html.append("<th>Actions</th>");
        html.append("</tr></thead>");
        html.append("<tbody id='employesBody'>").append(rows).append("</tbody>");
        html.append("</table>");
        html.append("</div>");
        html.append("<div class='total-bar'>");
        html.append("💰 Total des salaires : ");
        html.append("<span class='total-value'>").append(formatComptable(total)).append("</span>");
        html.append(" <span class='devise'>Gdes</span>");
        html.append("</div>");
        html.append("</div>");

        html.append("</div>"); // container
        html.append(modalHtml);

        html.append("<script>");
        html.append("function showToast(msg, type) {");
        html.append("const t = document.createElement('div');");
        html.append("t.className = 'toast ' + type;");
        html.append("t.innerText = msg;");
        html.append("document.body.appendChild(t);");
        html.append("setTimeout(() => t.remove(), 3000);");
        html.append("}");
        html.append(jsAjout);
        html.append(jsModification);
        html.append(jsSuppression);
        html.append(jsFiltres);
        html.append("</script>");

        html.append("</body></html>");

        return html.toString();
    }

    // ============================================================
    // HELPERS DE FORMATAGE
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
    // EXTRACTION ANNÉE / MOIS / CLASSES
    // ============================================================
    private static String extractAnnee(Employe e) {
        String a = e.getAnneeAcademique();
        if (a != null && !a.isBlank()) return a;
        return "";
    }

    private static String extractMois(Employe e) {
        String m = e.getMois();
        if (m != null && !m.isBlank()) return m;
        return "";
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