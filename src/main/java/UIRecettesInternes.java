import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class UIRecettesInternes {

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
        List<String> classes = service.getClasses();
        String annee = service.getAnneeCourante();

        // ============================================================
        // TABLEAU DES MODALITÉS
        // ============================================================
        StringBuilder modalitesTable = new StringBuilder();
        for (String classe : classes) {
            Map<Integer, Double> modalites = service.getModalites(classe);
            double totalFrais = service.getFraisScolaire(classe);

            modalitesTable.append("<tr data-classe=\"").append(escapeHtml(classe)).append("\">");
            modalitesTable.append("<td class='col-classe'>").append(escapeHtml(classe)).append("</td>");

            int nbVersements = Math.max(modalites.size(), 1);
            for (int i = 1; i <= nbVersements; i++) {
                double montant = modalites.getOrDefault(i, 0.0);
                modalitesTable.append("<td class='col-versement'>")
                              .append("<input type='number' class='modalite-input' data-classe='")
                              .append(escapeHtml(classe))
                              .append("' data-versement='")
                              .append(i)
                              .append("' value='")
                              .append(montant)
                              .append("' step='0.01'")
                              .append(canUpdate ? "" : " disabled")
                              .append(">")
                              .append("</td>");
            }

            modalitesTable.append("<td class='col-total total-frais' data-classe='")
                          .append(escapeHtml(classe))
                          .append("'>")
                          .append(formatComptable(totalFrais))
                          .append(" <span class='devise'>Gdes</span></td>");

            modalitesTable.append("<td class='col-action'>");
            if (canUpdate) {
                modalitesTable.append("<button class='btn-add-versement' data-classe='")
                              .append(escapeHtml(classe))
                              .append("' title='Ajouter une tranche'><i class='fas fa-plus'></i></button>");
            } else {
                modalitesTable.append("<span class='lock-icon'><i class='fas fa-lock'></i></span>");
            }
            modalitesTable.append("</td>");
            modalitesTable.append("</tr>");
        }

        // ============================================================
        // HISTORIQUE DES PAIEMENTS
        // ============================================================
        List<Map<String, Object>> paiements = service.getPaiementsEtudiants();
        StringBuilder paiementsRows = new StringBuilder();
        for (Map<String, Object> p : paiements) {
            Object idObj = p.get("id");
            String id = (idObj != null) ? idObj.toString() : "";

            paiementsRows.append("<tr>")
                         .append("<td class='col-id-etudiant'>")
                         .append(escapeHtml(String.valueOf(p.get("numero_identifiant"))))
                         .append("</td>")
                         .append("<td class='col-nom'>")
                         .append(escapeHtml(String.valueOf(p.get("nom"))))
                         .append(" ").append(escapeHtml(String.valueOf(p.get("prenom"))))
                         .append("</td>")
                         .append("<td class='col-montant'>")
                         .append(formatComptable((double) p.get("montant")))
                         .append(" <span class='devise'>Gdes</span></td>")
                         .append("<td class='col-date'>")
                         .append(escapeHtml(String.valueOf(p.get("date_paiement"))))
                         .append("</td>")
                         .append("<td class='col-moyen'>")
                         .append(escapeHtml(String.valueOf(p.get("moyen_paiement"))))
                         .append("</td>")
                         .append("<td class='col-ref'>")
                         .append(escapeHtml(String.valueOf(p.get("reference"))))
                         .append("</td>")
                         .append("<td class='col-action'>");
            if (canDelete && !id.isEmpty()) {
                paiementsRows.append("<button class='btn-danger' onclick=\"supprimerPaiement('")
                             .append(escapeJs(id))
                             .append("')\" title='Supprimer'><i class='fas fa-trash'></i></button>");
            } else {
                paiementsRows.append("<span class='lock-icon'><i class='fas fa-lock'></i></span>");
            }
            paiementsRows.append("</td></tr>");
        }

        // ============================================================
        // ACTIONS MODALITÉS
        // ============================================================
        String modalitesActions;
        if (canUpdate) {
            modalitesActions = """
                <div class='action-bar'>
                    <button class='btn-success' id='btnEnregistrerModalites'>
                        <i class='fas fa-save'></i> Enregistrer les modalités
                    </button>
                    <button class='btn-primary' id='btnAjouterTrancheGlobale'>
                        <i class='fas fa-plus'></i> Ajouter une tranche
                    </button>
                    <button class='btn-danger' id='btnSupprimerTrancheGlobale'>
                        <i class='fas fa-minus'></i> Supprimer la dernière tranche
                    </button>
                </div>
                """;
        } else {
            modalitesActions = """
                <div class='locked-banner'>
                    <i class='fas fa-lock'></i> Modification des modalités restreinte
                </div>
                """;
        }

        // ============================================================
        // PANNEAU DE PAIEMENT — avec filtres en cascade
        // ============================================================
        String paymentPanel;
        if (canCreate) {
            paymentPanel = """
                <div class='payment-panel'>
                    <div class='filter-cascade'>
                        <div class='form-group'>
                            <label><i class='fas fa-calendar-alt'></i> Année</label>
                            <select id='cbAnnee' class='form-select'>
                                <option value=''>-- Sélectionner --</option>
                            </select>
                        </div>
                        <div class='form-group'>
                            <label><i class='fas fa-clock'></i> Période</label>
                            <select id='cbPeriode' class='form-select' disabled>
                                <option value=''>-- Année d'abord --</option>
                            </select>
                        </div>
                        <div class='form-group'>
                            <label><i class='fas fa-users'></i> Classe</label>
                            <select id='cbClasse' class='form-select' disabled>
                                <option value=''>-- Période d'abord --</option>
                            </select>
                        </div>
                        <div class='form-group'>
                            <label><i class='fas fa-user-graduate'></i> Étudiant</label>
                            <select id='cbEtudiant' class='form-select' disabled>
                                <option value=''>-- Classe d'abord --</option>
                            </select>
                        </div>
                    </div>

                    <div class='student-info' id='studentInfo' style='display:none;'>
                        <div class='student-info-header'>
                            <i class='fas fa-id-card'></i> Informations de l'étudiant
                        </div>
                        <div class='student-info-body'>
                            <div class='info-item'>
                                <span class='info-label'>ID Étudiant</span>
                                <span class='info-value' id='infoId'>-</span>
                            </div>
                            <div class='info-item'>
                                <span class='info-label'>Nom complet</span>
                                <span class='info-value' id='infoNom'>-</span>
                            </div>
                            <div class='info-item'>
                                <span class='info-label'>Classe</span>
                                <span class='info-value' id='infoClasse'>-</span>
                            </div>
                            <div class='info-item'>
                                <span class='info-label'>Frais annuels</span>
                                <span class='info-value' id='infoFrais'>-</span>
                            </div>
                            <div class='info-item'>
                                <span class='info-label'>Total payé</span>
                                <span class='info-value info-paid' id='infoPaye'>-</span>
                            </div>
                            <div class='info-item'>
                                <span class='info-label'>Reste à payer</span>
                                <span class='info-value info-due' id='infoReste'>-</span>
                            </div>
                        </div>
                    </div>

                    <div class='payment-row'>
                        <div class='form-group'>
                            <label><i class='fas fa-money-bill-wave'></i> Montant (Gdes)</label>
                            <input type='number' id='txtMontant' step='0.01' placeholder='0.00' min='0'>
                        </div>
                        <div class='form-group'>
                            <label><i class='fas fa-credit-card'></i> Moyen</label>
                            <select id='cbMoyen'>
                                <option value='BNC'>BNC</option>
                                <option value='BRH'>BRH</option>
                                <option value='SOGESOL'>SOGESOL</option>
                                <option value='UNIBANK'>UNIBANK</option>
                                <option value='MonCash'>MonCash</option>
                                <option value='Carte'>Carte</option>
                                <option value='Espèces'>Espèces</option>
                            </select>
                        </div>
                        <div class='form-group'>
                            <label><i class='fas fa-hashtag'></i> Référence</label>
                            <input type='text' id='txtReference' placeholder='Réf. transaction'>
                        </div>
                        <div class='form-group-btn'>
                            <button class='btn-primary' id='btnEnregistrerPaiement'>
                                <i class='fas fa-check'></i> ENREGISTRER
                            </button>
                        </div>
                    </div>
                </div>
                """;
        } else {
            paymentPanel = """
                <div class='locked-banner'>
                    <i class='fas fa-lock'></i> Enregistrement des paiements restreint
                </div>
                """;
        }

        // ============================================================
        // JS MODALITÉS (conditionnel canUpdate)
        // ============================================================
        String jsModalites = canUpdate ? """
            document.getElementById('btnAjouterTrancheGlobale').onclick = ajouterTrancheGlobale;
            document.getElementById('btnSupprimerTrancheGlobale').onclick = supprimerTrancheGlobale;
            document.getElementById('btnEnregistrerModalites').onclick = enregistrerModalites;
            document.querySelectorAll('.btn-add-versement').forEach(btn => {
                btn.onclick = function() {
                    const classe = this.dataset.classe;
                    const row = this.closest('tr');
                    const inputs = row.querySelectorAll('.modalite-input');
                    const nouveauNum = inputs.length + 1;
                    const td = document.createElement('td');
                    td.className = 'col-versement';
                    const input = document.createElement('input');
                    input.type = 'number';
                    input.className = 'modalite-input';
                    input.dataset.classe = classe;
                    input.dataset.versement = nouveauNum;
                    input.value = 0;
                    input.step = '0.01';
                    input.addEventListener('input', recalculerTotaux);
                    td.appendChild(input);
                    const totalTd = row.querySelector('.total-frais');
                    row.insertBefore(td, totalTd);
                    recalculerTotaux();
                };
            });
            """ : "// Actions de modification des modalités désactivées";

        // ============================================================
        // JS FILTRES (TOUJOURS INJECTÉ — READ)
        // ============================================================
        String jsFiltres = """
            // ============================================================
            // FILTRES EN CASCADE + TABLEAU RÉACTIF
            // ============================================================

            document.getElementById('cbAnnee').addEventListener('change', () => {
                chargerPeriodes();
                rafraichirTableau();
            });
            document.getElementById('cbPeriode').addEventListener('change', () => {
                chargerClasses();
                rafraichirTableau();
            });
            document.getElementById('cbClasse').addEventListener('change', () => {
                chargerEtudiants();
                rafraichirTableau();
            });
            document.getElementById('cbEtudiant').addEventListener('change', () => {
                chargerInfosEtudiant();
                rafraichirTableau();
            });

            // Chargement initial des années
            fetch('/api/economie/annees')
                .then(r => r.json())
                .then(data => {
                    const select = document.getElementById('cbAnnee');
                    if (data.success && data.data) {
                        data.data.forEach(a => {
                            const opt = document.createElement('option');
                            opt.value = a;
                            opt.textContent = a;
                            select.appendChild(opt);
                        });
                    }
                })
                .catch(e => console.error('Erreur années:', e));

            async function chargerPeriodes() {
                const annee = document.getElementById('cbAnnee').value;
                const cbPeriode = document.getElementById('cbPeriode');
                const cbClasse = document.getElementById('cbClasse');
                const cbEtudiant = document.getElementById('cbEtudiant');

                cbPeriode.innerHTML = '<option value="">-- Sélectionner --</option>';
                cbClasse.innerHTML = '<option value="">-- Période d\\'abord --</option>';
                cbEtudiant.innerHTML = '<option value="">-- Classe d\\'abord --</option>';
                cbPeriode.disabled = true;
                cbClasse.disabled = true;
                cbEtudiant.disabled = true;
                document.getElementById('studentInfo').style.display = 'none';

                if (!annee) return;
                cbPeriode.innerHTML = '<option value="">Chargement...</option>';
                try {
                    const resp = await fetch('/api/economie/periodes?annee=' + encodeURIComponent(annee));
                    const data = await resp.json();
                    cbPeriode.innerHTML = '<option value="">-- Sélectionner --</option>';
                    if (data.success && data.data) {
                        data.data.forEach(p => {
                            const opt = document.createElement('option');
                            opt.value = p;
                            opt.textContent = p;
                            cbPeriode.appendChild(opt);
                        });
                    }
                    cbPeriode.disabled = false;
                } catch (e) {
                    cbPeriode.innerHTML = '<option value="">Erreur</option>';
                }
            }

            async function chargerClasses() {
                const annee = document.getElementById('cbAnnee').value;
                const periode = document.getElementById('cbPeriode').value;
                const cbClasse = document.getElementById('cbClasse');
                const cbEtudiant = document.getElementById('cbEtudiant');

                cbClasse.innerHTML = '<option value="">-- Sélectionner --</option>';
                cbEtudiant.innerHTML = '<option value="">-- Classe d\\'abord --</option>';
                cbClasse.disabled = true;
                cbEtudiant.disabled = true;
                document.getElementById('studentInfo').style.display = 'none';

                if (!periode) return;
                cbClasse.innerHTML = '<option value="">Chargement...</option>';
                try {
                    const resp = await fetch('/api/economie/classes?annee=' + encodeURIComponent(annee)
                        + '&periode=' + encodeURIComponent(periode));
                    const data = await resp.json();
                    cbClasse.innerHTML = '<option value="">-- Sélectionner --</option>';
                    if (data.success && data.data) {
                        data.data.forEach(c => {
                            const opt = document.createElement('option');
                            opt.value = c;
                            opt.textContent = c;
                            cbClasse.appendChild(opt);
                        });
                    }
                    cbClasse.disabled = false;
                } catch (e) {
                    cbClasse.innerHTML = '<option value="">Erreur</option>';
                }
            }

            async function chargerEtudiants() {
                const annee = document.getElementById('cbAnnee').value;
                const periode = document.getElementById('cbPeriode').value;
                const classe = document.getElementById('cbClasse').value;
                const cbEtudiant = document.getElementById('cbEtudiant');

                cbEtudiant.innerHTML = '<option value="">-- Sélectionner --</option>';
                cbEtudiant.disabled = true;
                document.getElementById('studentInfo').style.display = 'none';

                if (!classe) return;
                cbEtudiant.innerHTML = '<option value="">Chargement...</option>';
                try {
                    const resp = await fetch('/api/economie/etudiants?annee=' + encodeURIComponent(annee)
                        + '&periode=' + encodeURIComponent(periode)
                        + '&classe=' + encodeURIComponent(classe));
                    const data = await resp.json();
                    cbEtudiant.innerHTML = '<option value="">-- Tous les étudiants --</option>';
                    if (data.success && data.data) {
                        data.data.forEach(etu => {
                            const opt = document.createElement('option');
                            opt.value = etu.id;
                            opt.textContent = etu.id + ' — ' + etu.nom + ' ' + etu.prenom;
                            opt.dataset.nom = etu.nom;
                            opt.dataset.prenom = etu.prenom;
                            opt.dataset.classe = etu.classe || classe;
                            cbEtudiant.appendChild(opt);
                        });
                    }
                    cbEtudiant.disabled = false;
                } catch (e) {
                    cbEtudiant.innerHTML = '<option value="">Erreur</option>';
                }
            }

            async function chargerInfosEtudiant() {
                const etudiantId = document.getElementById('cbEtudiant').value;
                const annee = document.getElementById('cbAnnee').value;
                const classe = document.getElementById('cbClasse').value;
                const infoBox = document.getElementById('studentInfo');

                if (!etudiantId) { infoBox.style.display = 'none'; return; }

                const opt = document.querySelector('#cbEtudiant option[value="' + CSS.escape(etudiantId) + '"]');
                const nom = opt ? opt.dataset.nom : '';
                const prenom = opt ? opt.dataset.prenom : '';

                document.getElementById('infoId').textContent = etudiantId;
                document.getElementById('infoNom').textContent = nom + ' ' + prenom;
                document.getElementById('infoClasse').textContent = classe;
                document.getElementById('infoFrais').textContent = '...';
                document.getElementById('infoPaye').textContent = '...';
                document.getElementById('infoReste').textContent = '...';
                infoBox.style.display = 'block';

                try {
                    const resp = await fetch('/api/economie/etudiant/infos?etudiantId=' + encodeURIComponent(etudiantId)
                        + '&annee=' + encodeURIComponent(annee)
                        + '&classe=' + encodeURIComponent(classe));
                    const data = await resp.json();
                    if (data.success) {
                        document.getElementById('infoFrais').textContent = formatComptableJS(data.fraisAnnuels) + ' Gdes';
                        document.getElementById('infoPaye').textContent = formatComptableJS(data.totalPaye) + ' Gdes';
                        document.getElementById('infoReste').textContent = formatComptableJS(data.resteAPayer) + ' Gdes';
                    }
                } catch (e) {
                    document.getElementById('infoFrais').textContent = 'Erreur';
                }
            }

            let currentRequest = null;

            async function rafraichirTableau() {
                const annee = document.getElementById('cbAnnee').value;
                const periode = document.getElementById('cbPeriode').value;
                const classe = document.getElementById('cbClasse').value;
                const etudiantId = document.getElementById('cbEtudiant').value;

                const tbody = document.getElementById('etudiantsFinancesBody');
                tbody.innerHTML = '<tr><td colspan="7" class="text-center loading-row"><div class="spinner"></div> Chargement...</td></tr>';

                if (currentRequest) currentRequest.abort();
                const controller = new AbortController();
                currentRequest = controller;

                try {
                    const params = new URLSearchParams();
                    if (annee) params.append('annee', annee);
                    if (periode) params.append('periode', periode);
                    if (classe) params.append('classe', classe);
                    if (etudiantId) params.append('etudiantId', etudiantId);

                    const resp = await fetch('/api/economie/etudiants-finances?' + params, {
                        signal: controller.signal
                    });
                    const data = await resp.json();

                    if (data.success && data.data && data.data.length > 0) {
                        let html = '';
                        let totalVerse = 0;
                        let totalAPayer = 0;
                        data.data.forEach(r => {
                            totalVerse += r.montantVerse;
                            totalAPayer += r.montantAPayer;
                            html += '<tr>'
                                + '<td class="col-annee">' + escapeHtml(r.annee) + '</td>'
                                + '<td class="col-classe">' + escapeHtml(r.classe) + '</td>'
                                + '<td class="col-nom">' + escapeHtml(r.nom) + '</td>'
                                + '<td class="col-prenom">' + escapeHtml(r.prenom) + '</td>'
                                + '<td class="col-montant">' + formatComptableJS(r.montantVerse) + ' <span class="devise">Gdes</span></td>'
                                + '<td class="col-montant col-due">' + formatComptableJS(r.montantAPayer) + ' <span class="devise">Gdes</span></td>'
                                + '<td class="col-action">'
                                + '<button class="btn-small" onclick="preselectionnerEtudiant(\\'' + escapeJs(r.numero_identifiant) + '\\')" title="Payer">'
                                + '<i class="fas fa-money-bill"></i>'
                                + '</button>'
                                + '</td>'
                                + '</tr>';
                        });
                        html += '<tr class="total-row">'
                            + '<td colspan="4" class="text-right"><strong>Total</strong></td>'
                            + '<td class="col-montant"><strong>' + formatComptableJS(totalVerse) + '</strong> <span class="devise">Gdes</span></td>'
                            + '<td class="col-montant col-due"><strong>' + formatComptableJS(totalAPayer) + '</strong> <span class="devise">Gdes</span></td>'
                            + '<td></td>'
                            + '</tr>';
                        tbody.innerHTML = html;
                    } else if (data.success) {
                        tbody.innerHTML = '<tr><td colspan="7" class="text-center empty-row"><i class="fas fa-inbox"></i> Aucun étudiant pour ces critères.</td></tr>';
                    } else {
                        tbody.innerHTML = '<tr><td colspan="7" class="text-center error-row">Erreur: ' + escapeHtml(data.message || '') + '</td></tr>';
                    }
                } catch (e) {
                    if (e.name === 'AbortError') return;
                    tbody.innerHTML = '<tr><td colspan="7" class="text-center error-row">Erreur réseau</td></tr>';
                }
            }

            function preselectionnerEtudiant(id) {
                const cbEtudiant = document.getElementById('cbEtudiant');
                for (const opt of cbEtudiant.options) {
                    if (opt.value === id) {
                        cbEtudiant.value = id;
                        chargerInfosEtudiant();
                        document.getElementById('txtMontant').focus();
                        return;
                    }
                }
            }

            function formatComptableJS(n) {
                if (n === null || n === undefined) return '0.00';
                return Number(n).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
            }

            // Chargement initial du tableau
            rafraichirTableau();
            """;

        // ============================================================
        // JS PAIEMENT (conditionnel canCreate)
        // ============================================================
        String jsPaiement = canCreate ? """
            document.getElementById('btnEnregistrerPaiement').onclick = enregistrerPaiement;

            async function enregistrerPaiement() {
                const etudiantId = document.getElementById('cbEtudiant').value;
                const annee = document.getElementById('cbAnnee').value;
                const periode = document.getElementById('cbPeriode').value;
                const classe = document.getElementById('cbClasse').value;
                const montant = document.getElementById('txtMontant').value;
                const moyen = document.getElementById('cbMoyen').value;
                const reference = document.getElementById('txtReference').value.trim();

                if (!etudiantId) { showToast('Veuillez sélectionner un étudiant', 'error'); return; }
                if (!montant || parseFloat(montant) <= 0) { showToast('Montant invalide', 'error'); return; }

                const fd = new URLSearchParams();
                fd.append('etudiantId', etudiantId);
                fd.append('annee', annee);
                fd.append('periode', periode);
                fd.append('classe', classe);
                fd.append('montant', montant);
                fd.append('moyen', moyen);
                fd.append('reference', reference);

                try {
                    const resp = await fetch('/api/economie/paiements', { method: 'POST', body: fd });
                    const data = await resp.json();
                    if (data.success) {
                        showToast('Paiement enregistré', 'success');
                        document.getElementById('txtMontant').value = '';
                        document.getElementById('txtReference').value = '';
                        rafraichirTableau();
                        chargerInfosEtudiant();
                        setTimeout(() => location.reload(), 1500);
                    } else {
                        showToast(data.message || 'Erreur', 'error');
                    }
                } catch (e) {
                    showToast('Erreur réseau', 'error');
                }
            }
            """ : "// Enregistrement des paiements désactivé";

        // ============================================================
        // HTML FINAL
        // ============================================================
        return """
               <!DOCTYPE html>
               <html lang='fr'>
               <head>
                   <meta charset='UTF-8'>
                   <meta name='viewport' content='width=device-width, initial-scale=1.0'>
                   <title>Recettes internes</title>
                   <link href='https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700;800&display=swap' rel='stylesheet'>
                   <link rel='stylesheet' href='https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css'>
                   <style>
                       * { margin: 0; padding: 0; box-sizing: border-box; }
                       html, body { width: 100%; min-height: 100vh; background: #FAFBFB; font-family: 'Inter', sans-serif; }
                       body { padding: 20px; display: flex; justify-content: center; }
                       .container { width: 100%; max-width: 1600px; margin: 0 auto; }

                       /* ---------- NAV ---------- */
                       .nav-menu { display: flex; gap: 8px; background: white; border-radius: 12px; padding: 8px; margin-bottom: 20px; border: 1px solid #DCE1E6; flex-wrap: wrap; width: 100%; justify-content: center; }
                       .nav-btn { flex: 1 1 180px; text-align: center; padding: 12px 20px; background: none; border: none; cursor: pointer; font-weight: 600; border-radius: 8px; transition: all 0.2s ease-in-out; text-decoration: none; color: #333; }
                       .nav-btn.active { background: #2E7D32; color: white; }
                       .nav-btn:hover:not(.active) { background: #E8F5E9; }

                       /* ---------- CARDS ---------- */
                       .card { background: white; border-radius: 16px; padding: 24px; margin-bottom: 20px; box-shadow: 0 2px 8px rgba(0,0,0,0.06); border: 1px solid #DCE1E6; width: 100%; text-align: center; }
                       .card-title { font-size: 18px; font-weight: 700; color: #2E7D32; margin-bottom: 20px; border-bottom: 2px solid #2E7D32; padding-bottom: 8px; display: inline-block; }

                       /* ---------- TABLEAU ---------- */
                       .table-responsive { width: 100%; overflow-x: auto; border-radius: 12px; border: 1px solid #DCE1E6; }
                       table { width: 100%; border-collapse: collapse; font-size: 14px; }
                       th {
                           background: #FFF5C8;
                           padding: 14px 16px;
                           font-weight: 700;
                           border-bottom: 2px solid #2E7D32;
                           font-size: 13px;
                           color: #1B5E20;
                           text-transform: uppercase;
                           letter-spacing: 0.5px;
                           text-align: center;
                       }
                       td {
                           padding: 12px 16px;
                           border-bottom: 1px solid #E2E8F0;
                           font-size: 14px;
                           text-align: center;
                           vertical-align: middle;
                       }
                       tbody tr:hover { background: #F1F8E9; }
                       tbody tr:last-child td { border-bottom: none; }

                       .col-classe { text-align: center; font-weight: 700; color: #1B5E20; }
                       .col-annee { text-align: center; color: #64748b; font-size: 13px; }
                       .col-nom { text-align: center; font-weight: 600; }
                       .col-prenom { text-align: center; }
                       .col-montant {
                           text-align: right;
                           font-family: 'Courier New', monospace;
                           font-weight: 700;
                           color: #2E7D32;
                       }
                       .col-montant.col-due { color: #C62828; }
                       .col-montant .devise {
                           font-size: 11px;
                           color: #94a3b8;
                           font-weight: 500;
                           margin-left: 4px;
                           font-family: 'Inter', sans-serif;
                       }
                       .col-action { text-align: center; width: 70px; }

                       .total-row td { background: #F1F8E9; font-weight: 700; border-top: 2px solid #2E7D32; }
                       .text-right { text-align: right; }

                       .loading-row, .empty-row, .error-row {
                           padding: 40px !important;
                           color: #64748b;
                           font-size: 14px;
                       }
                       .empty-row i, .error-row i { font-size: 24px; display: block; margin-bottom: 8px; color: #CBD5E1; }
                       .error-row { color: #C62828; }

                       .spinner {
                           display: inline-block;
                           width: 16px;
                           height: 16px;
                           border: 2px solid #CBD5E1;
                           border-top-color: #2E7D32;
                           border-radius: 50%;
                           animation: spin 0.8s linear infinite;
                           vertical-align: middle;
                           margin-right: 8px;
                       }
                       @keyframes spin { to { transform: rotate(360deg); } }

                       /* ---------- BOUTONS ---------- */
                       .btn-primary { background: #1565C0; color: white; border: none; padding: 12px 24px; border-radius: 8px; cursor: pointer; font-weight: 600; font-size: 14px; transition: background 0.2s; display: inline-flex; align-items: center; gap: 8px; }
                       .btn-primary:hover { background: #0D47A1; }
                       .btn-success { background: #2E7D32; color: white; border: none; padding: 12px 24px; border-radius: 8px; cursor: pointer; font-weight: 600; font-size: 14px; transition: background 0.2s; display: inline-flex; align-items: center; gap: 8px; }
                       .btn-success:hover { background: #1B5E20; }
                       .btn-danger { background: #C62828; color: white; border: none; padding: 8px 12px; border-radius: 6px; cursor: pointer; font-weight: 500; font-size: 13px; transition: opacity 0.2s; display: inline-flex; align-items: center; justify-content: center; min-width: 40px; }
                       .btn-danger:hover { opacity: 0.85; }
                       .btn-add-versement { background: #1976D2; color: white; border: none; padding: 8px 12px; border-radius: 6px; cursor: pointer; font-weight: bold; font-size: 14px; transition: 0.2s; display: inline-flex; align-items: center; justify-content: center; min-width: 40px; }
                       .btn-add-versement:hover { background: #115293; }
                       .btn-small { background: #1976D2; color: white; border: none; padding: 6px 10px; border-radius: 6px; cursor: pointer; font-size: 12px; transition: 0.2s; }
                       .btn-small:hover { background: #115293; }

                       .modalite-input { width: 100px; padding: 8px 10px; border: 1px solid #DCE1E6; border-radius: 6px; font-size: 13px; text-align: right; font-family: 'Courier New', monospace; font-weight: 600; }
                       .modalite-input:focus { outline: none; border-color: #2E7D32; box-shadow: 0 0 0 3px rgba(46,125,50,0.1); }
                       .modalite-input:disabled { background: #f1f5f9; cursor: not-allowed; }

                       /* ---------- ACTION BAR ---------- */
                       .action-bar { display: flex; gap: 10px; flex-wrap: wrap; margin-bottom: 20px; justify-content: center; }
                       .locked-banner { padding: 14px 18px; background: #f1f5f9; border-radius: 8px; color: #64748b; margin-bottom: 20px; display: flex; align-items: center; justify-content: center; gap: 10px; }
                       .lock-icon { color: #94a3b8; font-size: 14px; }

                       /* ---------- PAYMENT PANEL ---------- */
                       .payment-panel { background: #E8F0FE; padding: 24px; border-radius: 12px; border: 1px solid #DCE1E6; }

                       .filter-cascade {
                           display: grid;
                           grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
                           gap: 16px;
                           margin-bottom: 20px;
                       }
                       .form-group { display: flex; flex-direction: column; gap: 6px; align-items: center; }
                       .form-group label { font-size: 13px; font-weight: 600; color: #334155; display: flex; align-items: center; gap: 6px; justify-content: center; }
                       .form-group input, .form-group select, .form-select {
                           padding: 10px 14px;
                           border: 1px solid #DCE1E6;
                           border-radius: 8px;
                           font-size: 14px;
                           font-family: 'Inter', sans-serif;
                           width: 100%;
                           background: white;
                           text-align: center;
                       }
                       .form-group input:focus, .form-group select:focus, .form-select:focus {
                           outline: none;
                           border-color: #1565C0;
                           box-shadow: 0 0 0 3px rgba(21,101,192,0.1);
                       }
                       .form-select:disabled { background: #f8fafc; cursor: not-allowed; color: #94a3b8; }

                       /* ---------- STUDENT INFO ---------- */
                       .student-info {
                           background: white;
                           border: 1px solid #BBDEFB;
                           border-radius: 12px;
                           padding: 16px;
                           margin-bottom: 20px;
                           animation: fadeIn 0.3s ease;
                       }
                       @keyframes fadeIn { from { opacity: 0; transform: translateY(-5px); } to { opacity: 1; transform: translateY(0); } }
                       .student-info-header {
                           font-weight: 700;
                           color: #1565C0;
                           margin-bottom: 12px;
                           display: flex;
                           align-items: center;
                           justify-content: center;
                           gap: 8px;
                           font-size: 14px;
                           border-bottom: 1px solid #E3F2FD;
                           padding-bottom: 8px;
                       }
                       .student-info-body {
                           display: grid;
                           grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
                           gap: 12px;
                       }
                       .info-item { display: flex; flex-direction: column; gap: 4px; align-items: center; }
                       .info-label { font-size: 11px; color: #64748b; text-transform: uppercase; letter-spacing: 0.5px; font-weight: 600; }
                       .info-value { font-size: 14px; color: #1E293B; font-weight: 700; font-family: 'Courier New', monospace; }
                       .info-value.info-paid { color: #2E7D32; }
                       .info-value.info-due { color: #C62828; }

                       /* ---------- PAYMENT ROW ---------- */
                       .payment-row {
                           display: grid;
                           grid-template-columns: 1.2fr 1fr 1fr 0.8fr;
                           gap: 16px;
                           align-items: end;
                       }
                       .form-group-btn { display: flex; align-items: flex-end; }
                       .form-group-btn .btn-primary { width: 100%; height: 44px; justify-content: center; }

                       @media (max-width: 768px) {
                           .payment-row { grid-template-columns: 1fr; }
                       }

                       /* ---------- TOAST ---------- */
                       .toast { position: fixed; bottom: 30px; right: 30px; background: #333; color: white; padding: 12px 20px; border-radius: 8px; z-index: 1100; animation: fadeInOut 3s ease; font-weight: 500; box-shadow: 0 4px 12px rgba(0,0,0,0.15); }
                       .toast.success { background: #27ae60; }
                       .toast.error { background: #ef4444; }
                       @keyframes fadeInOut { 0% { opacity: 0; transform: translateY(10px); } 15% { opacity: 1; transform: translateY(0); } 85% { opacity: 1; transform: translateY(0); } 100% { opacity: 0; transform: translateY(10px); } }
                   </style>
               </head>
               <body>
               <div class='container'>
                   <div class='nav-menu'>
                       <a href='/admin/economie/budget' class='nav-btn'><i class='fas fa-chart-pie'></i> Budget</a>
                       <a href='/admin/economie/recettes/internes' class='nav-btn active'><i class='fas fa-school'></i> Recettes internes</a>
                       <a href='/admin/economie/recettes/externes' class='nav-btn'><i class='fas fa-hand-holding-usd'></i> Recettes externes</a>
                       <a href='/admin/economie/depenses' class='nav-btn'><i class='fas fa-arrow-down'></i> Dépenses</a>
                       <a href='/admin/economie/salaires' class='nav-btn'><i class='fas fa-users'></i> Salaires</a>
                   </div>

                   <div class='card'>
                       <div class='card-title'><i class='fas fa-calendar-alt'></i> Année académique : """ + escapeHtml(annee) + """
                       </div>
                   </div>

                   <div class='card'>
                       <div class='card-title'><i class='fas fa-credit-card'></i> Enregistrer un paiement étudiant</div>
                       """ + paymentPanel + """
                   </div>

                   <div class='card'>
                       <div class='card-title'><i class='fas fa-table'></i> Suivi des paiements par étudiant</div>
                       <div class='table-responsive'>
                           <table id='etudiantsFinancesTable'>
                               <thead>
                                   <tr>
                                       <th>Année</th>
                                       <th>Classe</th>
                                       <th>Nom</th>
                                       <th>Prénom</th>
                                       <th>Montant Versé</th>
                                       <th>Montant à Payer</th>
                                       <th>Action</th>
                                   </tr>
                               </thead>
                               <tbody id='etudiantsFinancesBody'>
                                   <tr><td colspan='7' class='text-center loading-row'><div class='spinner'></div> Chargement...</td></tr>
                               </tbody>
                           </table>
                       </div>
                   </div>

                   <div class='card'>
                       <div class='card-title'><i class='fas fa-list-ol'></i> Définition des tranches par classe</div>
                       """ + modalitesActions + """
                       <div class='table-responsive'>
                           <table id='modalitesTable'>
                               <thead>
                                   <tr>
                                       <th>Classe</th>
                                       <th colspan='4' id='versementHeader'>Versements (Gdes)</th>
                                       <th>Total frais</th>
                                       <th>Actions</th>
                                   </tr>
                               </thead>
                               <tbody id='modalitesBody'>
                                   """ + modalitesTable.toString() + """
                               </tbody>
                           </table>
                       </div>
                   </div>

                   <div class='card'>
                       <div class='card-title'><i class='fas fa-history'></i> Historique des paiements</div>
                       <div class='table-responsive'>
                           <table>
                               <thead>
                                   <tr>
                                       <th>ID Étudiant</th>
                                       <th>Nom complet</th>
                                       <th>Montant</th>
                                       <th>Date</th>
                                       <th>Moyen</th>
                                       <th>Référence</th>
                                       <th>Action</th>
                                   </tr>
                               </thead>
                               <tbody id='paiementsBody'>
                                   """ + paiementsRows.toString() + """
                               </tbody>
                           </table>
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

                   function escapeHtml(s) {
                       if (!s) return '';
                       return String(s).replace(/[&<>"']/g, m => ({
                           '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
                       }[m]));
                   }

                   function escapeJs(s) {
                       if (!s) return '';
                       return String(s).replace(/\\\\/g, '\\\\\\\\').replace(/'/g, "\\\\'")
                           .replace(/"/g, '\\\\"').replace(/\\n/g, '\\\\n').replace(/\\r/g, '\\\\r');
                   }

                   function getNbVersements() {
                       const firstRow = document.querySelector('#modalitesBody tr');
                       if (!firstRow) return 0;
                       return firstRow.querySelectorAll('.modalite-input').length;
                   }

                   function ajouterTrancheGlobale() {
                       const rows = document.querySelectorAll('#modalitesBody tr');
                       if (rows.length === 0) return;
                       const nouveauNum = getNbVersements() + 1;
                       rows.forEach(row => {
                           const classe = row.dataset.classe;
                           const td = document.createElement('td');
                           td.className = 'col-versement';
                           const input = document.createElement('input');
                           input.type = 'number';
                           input.className = 'modalite-input';
                           input.dataset.classe = classe;
                           input.dataset.versement = nouveauNum;
                           input.value = 0;
                           input.step = '0.01';
                           input.addEventListener('input', recalculerTotaux);
                           td.appendChild(input);
                           const totalTd = row.querySelector('.total-frais');
                           row.insertBefore(td, totalTd);
                       });
                       recalculerTotaux();
                   }

                   function supprimerTrancheGlobale() {
                       const nb = getNbVersements();
                       if (nb <= 1) { showToast('Impossible de supprimer la seule tranche restante', 'error'); return; }
                       if (!confirm('Supprimer la dernière tranche pour toutes les classes ?')) return;
                       document.querySelectorAll('#modalitesBody tr').forEach(row => {
                           const inputs = row.querySelectorAll('.modalite-input');
                           const dernier = inputs[inputs.length - 1];
                           if (dernier) dernier.parentElement.remove();
                       });
                       recalculerTotaux();
                   }

                   function recalculerTotaux() {
                       document.querySelectorAll('#modalitesBody tr').forEach(row => {
                           const inputs = row.querySelectorAll('.modalite-input');
                           let total = 0;
                           inputs.forEach(inp => total += parseFloat(inp.value) || 0);
                           const totalTd = row.querySelector('.total-frais');
                           if (totalTd) {
                               totalTd.innerHTML = formatComptableJS(total) + ' <span class="devise">Gdes</span>';
                           }
                       });
                   }

                   async function enregistrerModalites() {
                       const rows = document.querySelectorAll('#modalitesBody tr');
                       let hasError = false;
                       for (const row of rows) {
                           const classe = row.dataset.classe;
                           const inputs = row.querySelectorAll('.modalite-input');
                           const modalites = {};
                           inputs.forEach(inp => {
                               modalites[parseInt(inp.dataset.versement)] = parseFloat(inp.value) || 0;
                           });
                           let total = 0;
                           Object.values(modalites).forEach(v => total += v);
                           try {
                               const fd1 = new URLSearchParams();
                               fd1.append('classe', classe);
                               fd1.append('modalites', JSON.stringify(modalites));
                               let resp = await fetch('/api/economie/modalites', { method: 'POST', body: fd1 });
                               let data = await resp.json();
                               if (!data.success) { showToast('Erreur modalités ' + classe, 'error'); hasError = true; continue; }

                               const fd2 = new URLSearchParams();
                               fd2.append('classe', classe);
                               fd2.append('montant', total);
                               resp = await fetch('/api/economie/frais', { method: 'POST', body: fd2 });
                               data = await resp.json();
                               if (!data.success) { showToast('Erreur frais ' + classe, 'error'); hasError = true; }
                           } catch (e) {
                               showToast('Erreur réseau pour ' + classe, 'error');
                               hasError = true;
                           }
                       }
                       if (!hasError) {
                           showToast('✅ Modalités enregistrées', 'success');
                           setTimeout(() => location.reload(), 1000);
                       }
                   }

                   async function supprimerPaiement(id) {
                       if (!confirm('Supprimer ce paiement ?')) return;
                       const fd = new URLSearchParams();
                       fd.append('id', id);
                       try {
                           const resp = await fetch('/api/economie/paiement/supprimer', { method: 'POST', body: fd });
                           const data = await resp.json();
                           if (data.success) { showToast('Paiement supprimé', 'success'); setTimeout(() => location.reload(), 500); }
                           else showToast(data.message, 'error');
                       } catch (e) { showToast('Erreur réseau', 'error'); }
                   }

                   document.addEventListener('DOMContentLoaded', function() {
                       document.querySelectorAll('.modalite-input').forEach(inp => {
                           inp.addEventListener('input', recalculerTotaux);
                       });

                       """ + jsModalites + """

                       """ + jsFiltres + """

                       """ + jsPaiement + """
                   });
               </script>
               </body>
               </html>""";
    }

    // ============================================================
    // FORMAT COMPTABLE
    // ============================================================
    private static String formatComptable(double number) {
        return COMPTA_FORMAT.format(number);
    }

    @SuppressWarnings("unused")
    private static String formatNumber(double number) {
        return String.format("%,.0f", number);
    }

    // ============================================================
    // ÉCHAPPEMENT
    // ============================================================
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