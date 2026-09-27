import java.util.List;
import java.util.Map;

/**
 * Générateur de la page HTML pour la gestion des périodes académiques.
 *
 * ✅ ARCHITECTURE MULTI-INSTITUTIONS :
 *   - Clé composite (institution_id, annee_academique, periode)
 *   - Aucune référence à un `id` auto-incrémenté
 *   - Les identifiants composites sont passés en paramètres URL : /{annee}/{periode}
 */
public class UIParametresPeriodes {

    // ============================================================
    // POINT D'ENTRÉE
    // ============================================================
    public static String rendrePage(
            List<Map<String, Object>> periodes,
            List<Map<String, Object>> annees,
            String selectedAnnee,
            String institutionId,
            String messageErreur,
            String messageSucces) {

        if (periodes == null) periodes = List.of();
        if (annees == null) annees = List.of();
        if (selectedAnnee == null) selectedAnnee = "";
        if (messageErreur == null) messageErreur = "";
        if (messageSucces == null) messageSucces = "";
        if (institutionId == null) institutionId = "";

        StringBuilder sb = new StringBuilder(16_384);

        // ===================== HEAD =====================
        sb.append(genererHead());

        sb.append("<body>\n")
          .append("<div class=\"main-wrapper\">\n");

        // ===================== EN-TÊTE =====================
        sb.append(genererHeader());

        // ===================== MESSAGES =====================
        sb.append(genererMessages(messageErreur, messageSucces));

        // ===================== FILTRES + TOOLBAR =====================
        sb.append(genererFiltresEtToolbar(annees, selectedAnnee));

        // ===================== TABLEAU =====================
        sb.append(genererTableau(periodes));

        sb.append("</div>\n");

        // ===================== MODAL =====================
        sb.append(genererModal(annees));

        // ===================== TOAST =====================
        sb.append("    <div class=\"toast-container\" id=\"toastContainer\"></div>\n");

        // ===================== SCRIPTS =====================
        sb.append(genererScripts());

        sb.append("</body>\n</html>");

        return sb.toString();
    }

    // ============================================================
    // HEAD (styles + libs)
    // ============================================================
    private static String genererHead() {
        return """
        <!DOCTYPE html>
        <html lang="fr" class="h-100">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Gestion des Périodes</title>
            <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
            <link href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css" rel="stylesheet">
            <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap" rel="stylesheet">
            <style>
                :root { --primary-color: #4f46e5; --primary-hover: #4338ca; --bg-color: #f8fafc; }
                html, body { height: 100%; margin: 0; overflow: hidden; font-family: 'Inter', sans-serif; background-color: var(--bg-color); color: #1e293b; }
                .main-wrapper { height: 100vh; display: flex; flex-direction: column; padding: 1.5rem; box-sizing: border-box; }
                .glass-header {
                    background: linear-gradient(135deg, #4f46e5 0%, #3b82f6 100%); color: white;
                    border-radius: 16px; padding: 20px 24px; box-shadow: 0 10px 25px -5px rgba(79, 70, 229, 0.3);
                    flex-shrink: 0;
                }
                .custom-card { border: none; border-radius: 16px; box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.05); background: #ffffff; }
                .table-container { flex: 1; min-height: 0; display: flex; flex-direction: column; margin-top: 20px; }
                .table-responsive-custom { flex: 1; overflow-y: auto; border-radius: 16px; }
                .table-custom { margin-bottom: 0; }
                .table-custom thead { position: sticky; top: 0; z-index: 10; background-color: #f8fafc; }
                .btn-primary { background-color: var(--primary-color); border-color: var(--primary-color); border-radius: 10px; padding: 9px 20px; font-weight: 500; }
                .btn-primary:hover { background-color: var(--primary-hover); border-color: var(--primary-hover); }
                .btn-success { border-radius: 10px; padding: 9px 24px; font-weight: 500; }
                .badge-soft-primary { background-color: #e0e7ff; color: #4338ca; font-weight: 600; border-radius: 6px; padding: 6px 10px; }
                .badge-soft-success { background-color: #d1fae5; color: #065f46; font-weight: 600; border-radius: 6px; padding: 6px 10px; }
                .badge-soft-danger { background-color: #fee2e2; color: #991b1b; font-weight: 600; border-radius: 6px; padding: 6px 10px; }
                .filter-bar { background: white; border-radius: 12px; padding: 12px 20px; display: flex; gap: 15px; align-items: center; flex-wrap: wrap; margin-bottom: 16px; }
                .filter-bar .form-group { flex: 1; min-width: 180px; }
                .filter-bar label { display: block; font-weight: 600; color: #475569; margin-bottom: 3px; font-size: 12px; text-transform: uppercase; letter-spacing: 0.5px; }
                .filter-bar select { width: 100%; padding: 8px 12px; border: 1px solid #e2e8f0; border-radius: 10px; font-size: 0.9rem; background: white; }
                .filter-bar select:focus { border-color: var(--primary-color); box-shadow: 0 0 0 3px rgba(79, 70, 229, 0.15); outline: none; }
                .form-control, .form-select { border-radius: 10px; border: 1px solid #e2e8f0; padding: 9px 14px; font-size: 0.9rem; transition: all 0.2s; }
                .form-control:focus, .form-select:focus { border-color: var(--primary-color); box-shadow: 0 0 0 3px rgba(79, 70, 229, 0.15); }
                .toolbar-actions { display: flex; gap: 10px; flex-wrap: wrap; align-items: center; }
                .toolbar-actions .btn { font-size: 13px; padding: 6px 16px; }
                .modal-content { border-radius: 16px; }
                .modal-header { border-bottom: none; padding-bottom: 0; }
                .modal-footer { border-top: none; padding-top: 0; }
                .toast-container { position: fixed; bottom: 30px; right: 30px; z-index: 1100; }
                .toast-custom { background: #0f172a; color: white; padding: 14px 22px; border-radius: 14px; box-shadow: 0 10px 25px -5px rgba(0,0,0,0.2); font-weight: 500; font-size: 14px; animation: fadeInOut 3s ease; }
                .toast-custom.success { background: #16a34a; }
                .toast-custom.error { background: #dc2626; }
                @keyframes fadeInOut { 0% { opacity: 0; transform: translateY(20px); } 15% { opacity: 1; transform: translateY(0); } 85% { opacity: 1; } 100% { opacity: 0; transform: translateY(20px); } }
                @media (max-width: 768px) { .toolbar-actions { flex-direction: column; align-items: stretch; } .filter-bar { flex-direction: column; } .filter-bar .form-group { width: 100%; } }
            </style>
        </head>
        """;
    }

    // ============================================================
    // EN-TÊTE
    // ============================================================
    private static String genererHeader() {
        return """
            <div class="glass-header d-flex justify-content-between align-items-center mb-3">
                <div>
                    <h4 class="m-0 fw-bold"><i class="fa-solid fa-clock me-2"></i>Gestion des Périodes</h4>
                    <p class="m-0 opacity-75 fs-7 mt-1">Gérez les périodes académiques par année</p>
                </div>
                <div>
                    <a href="/admin/parametres" class="btn btn-light text-primary fw-semibold rounded-pill px-3 py-2 fs-7 shadow-sm">
                        <i class="fa-solid fa-arrow-left me-1"></i> Paramètres
                    </a>
                </div>
            </div>
        """;
    }

    // ============================================================
    // MESSAGES (erreur / succès)
    // ============================================================
    private static String genererMessages(String messageErreur, String messageSucces) {
        StringBuilder sb = new StringBuilder();

        if (!messageErreur.isEmpty()) {
            sb.append("    <div class=\"alert alert-danger alert-dismissible fade show border-0 shadow-sm rounded-3 mb-3 py-2\" role=\"alert\">\n")
              .append("        <i class=\"fa-solid fa-circle-exclamation me-2\"></i>")
              .append(echapperHtml(messageErreur)).append("\n")
              .append("        <button type=\"button\" class=\"btn-close py-2\" data-bs-dismiss=\"alert\"></button>\n")
              .append("    </div>\n");
        }
        if (!messageSucces.isEmpty()) {
            sb.append("    <div class=\"alert alert-success alert-dismissible fade show border-0 shadow-sm rounded-3 mb-3 py-2\" role=\"alert\">\n")
              .append("        <i class=\"fa-solid fa-circle-check me-2\"></i>")
              .append(echapperHtml(messageSucces)).append("\n")
              .append("        <button type=\"button\" class=\"btn-close py-2\" data-bs-dismiss=\"alert\"></button>\n")
              .append("    </div>\n");
        }
        return sb.toString();
    }

    // ============================================================
    // FILTRES + TOOLBAR
    // ============================================================
    private static String genererFiltresEtToolbar(List<Map<String, Object>> annees, String selectedAnnee) {
        StringBuilder sb = new StringBuilder();

        sb.append("    <div class=\"filter-bar\">\n")
          .append("        <div class=\"form-group\">\n")
          .append("            <label for=\"filterAnnee\"><i class=\"fa-solid fa-calendar me-1\"></i>Année académique</label>\n")
          .append("            <select id=\"filterAnnee\" onchange=\"applyFilter()\">\n")
          .append("                <option value=\"\">Toutes les années</option>\n");

        for (Map<String, Object> a : annees) {
            String annee = (String) a.get("anneeAcademique");
            if (annee == null) continue;
            String selected = annee.equals(selectedAnnee) ? " selected" : "";
            sb.append("                <option value=\"").append(echapperHtml(annee)).append("\"")
              .append(selected).append(">").append(echapperHtml(annee)).append("</option>\n");
        }

        sb.append("            </select>\n")
          .append("        </div>\n")
          .append("        <div>\n")
          .append("            <button class=\"btn btn-secondary btn-sm\" onclick=\"applyFilter()\">")
          .append("<i class=\"fa-solid fa-filter me-1\"></i>Filtrer</button>\n")
          .append("        </div>\n")
          .append("        <div style=\"margin-left:auto;\">\n")
          .append("            <button class=\"btn btn-primary btn-sm\" id=\"btnAjouter\">")
          .append("<i class=\"fa-solid fa-plus me-1\"></i>Ajouter</button>\n")
          .append("            <button class=\"btn btn-secondary btn-sm\" id=\"btnRafraichir\">")
          .append("<i class=\"fa-solid fa-rotate me-1\"></i>Rafraîchir</button>\n")
          .append("        </div>\n")
          .append("    </div>\n");

        return sb.toString();
    }

    // ============================================================
    // TABLEAU
    // ============================================================
    private static String genererTableau(List<Map<String, Object>> periodes) {
        StringBuilder sb = new StringBuilder();

        sb.append("    <div class=\"custom-card table-container\">\n")
          .append("        <div class=\"table-responsive-custom\">\n")
          .append("            <table class=\"table table-custom align-middle mb-0\">\n")
          .append("                <thead class=\"table-light border-bottom\">\n")
          .append("                    <tr class=\"text-muted fs-7 text-uppercase\">\n")
          .append("                        <th class=\"ps-4 py-3\">#</th>\n")
          .append("                        <th class=\"py-3\">Année académique</th>\n")
          .append("                        <th class=\"py-3\">Période</th>\n")
          .append("                        <th class=\"py-3\">Date début</th>\n")
          .append("                        <th class=\"py-3\">Date fin</th>\n")
          .append("                        <th class=\"py-3\">Statut</th>\n")
          .append("                        <th class=\"pe-4 py-3 text-center\" style=\"width: 140px;\">Actions</th>\n")
          .append("                    </tr>\n")
          .append("                </thead>\n")
          .append("                <tbody id=\"periodesBody\" class=\"border-top-0\">\n");

        if (periodes.isEmpty()) {
            sb.append("                    <tr>\n")
              .append("                        <td colspan=\"7\" class=\"text-center py-5 text-muted\">\n")
              .append("                            <i class=\"fa-solid fa-inbox fs-1 mb-3 text-black-50 d-block\"></i>\n")
              .append("                            <span>Aucune période enregistrée. Cliquez sur <strong>\"Ajouter\"</strong> pour créer une nouvelle période.</span>\n")
              .append("                        </td>\n")
              .append("                    </tr>\n");
        } else {
            int index = 1;
            for (Map<String, Object> p : periodes) {
                String anneeAcademique = (String) p.get("anneeAcademique");
                String periode = (String) p.get("periode");
                String dateDebut = (String) p.get("dateDebut");
                String dateFin = (String) p.get("dateFin");
                Boolean estActive = (Boolean) p.get("estActive");

                String statut = (estActive != null && estActive)
                        ? "<span class=\"badge-soft-success\"><i class=\"fa-solid fa-check-circle me-1\"></i>Active</span>"
                        : "<span class=\"badge-soft-danger\"><i class=\"fa-solid fa-circle-xmark me-1\"></i>Inactive</span>";

                String anneeEsc = echapperHtml(anneeAcademique != null ? anneeAcademique : "");
                String periodeEsc = echapperHtml(periode != null ? periode : "");

                sb.append("                    <tr>\n")
                  .append("                        <td class=\"ps-4\"><span class=\"badge-soft-primary\">").append(index++).append("</span></td>\n")
                  .append("                        <td class=\"fw-semibold\">").append(anneeEsc).append("</td>\n")
                  .append("                        <td class=\"fw-semibold text-dark\">").append(periodeEsc).append("</td>\n")
                  .append("                        <td>").append(echapperHtml(dateDebut != null ? dateDebut : "-")).append("</td>\n")
                  .append("                        <td>").append(echapperHtml(dateFin != null ? dateFin : "-")).append("</td>\n")
                  .append("                        <td>").append(statut).append("</td>\n")
                  .append("                        <td class=\"pe-4 text-center\">\n")
                  .append("                            <button class=\"btn btn-sm btn-outline-primary btn-edit me-1\" ")
                  .append("data-annee=\"").append(anneeEsc).append("\" ")
                  .append("data-periode=\"").append(periodeEsc).append("\">")
                  .append("<i class=\"fa-solid fa-pen\"></i></button>\n")
                  .append("                            <button class=\"btn btn-sm btn-outline-danger btn-delete\" ")
                  .append("data-annee=\"").append(anneeEsc).append("\" ")
                  .append("data-periode=\"").append(periodeEsc).append("\">")
                  .append("<i class=\"fa-solid fa-trash\"></i></button>\n")
                  .append("                        </td>\n")
                  .append("                    </tr>\n");
            }
        }

        sb.append("                </tbody>\n")
          .append("            </table>\n")
          .append("        </div>\n")
          .append("    </div>\n");

        return sb.toString();
    }

    // ============================================================
    // MODAL
    // ============================================================
    private static String genererModal(List<Map<String, Object>> annees) {
        StringBuilder sb = new StringBuilder();

        sb.append("""
        <div class="modal fade" id="periodeModal" tabindex="-1" aria-hidden="true">
            <div class="modal-dialog">
                <div class="modal-content">
                    <div class="modal-header">
                        <h5 class="modal-title" id="modalTitle"><i class="fa-solid fa-plus-circle me-2"></i>Ajouter une période</h5>
                        <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                    </div>
                    <form id="periodeForm">
                        <div class="modal-body">
                            <input type="hidden" id="editMode" value="add">
                            <input type="hidden" id="editOldAnnee" value="">
                            <input type="hidden" id="editOldPeriode" value="">
                            <div class="mb-3">
                                <label for="inputAnnee" class="form-label fw-semibold">Année académique <span class="text-danger">*</span></label>
                                <select class="form-select" id="inputAnnee" required>
                                    <option value="">-- Sélectionner --</option>
        """);

        for (Map<String, Object> a : annees) {
            String annee = (String) a.get("anneeAcademique");
            if (annee == null) continue;
            sb.append("                                    <option value=\"")
              .append(echapperHtml(annee)).append("\">")
              .append(echapperHtml(annee)).append("</option>\n");
        }

        sb.append("""
                                </select>
                            </div>
                            <div class="mb-3">
                                <label for="inputPeriode" class="form-label fw-semibold">Période <span class="text-danger">*</span></label>
                                <input type="text" class="form-control" id="inputPeriode" placeholder="Ex: Semestre 1" required>
                            </div>
                            <div class="row">
                                <div class="col-md-6 mb-3">
                                    <label for="inputDateDebut" class="form-label fw-semibold">Date début <span class="text-danger">*</span></label>
                                    <input type="date" class="form-control" id="inputDateDebut" required>
                                </div>
                                <div class="col-md-6 mb-3">
                                    <label for="inputDateFin" class="form-label fw-semibold">Date fin <span class="text-danger">*</span></label>
                                    <input type="date" class="form-control" id="inputDateFin" required>
                                </div>
                            </div>
                            <div class="mb-3">
                                <div class="form-check">
                                    <input class="form-check-input" type="checkbox" id="inputActive" checked>
                                    <label class="form-check-label fw-semibold" for="inputActive">
                                        Active
                                    </label>
                                </div>
                            </div>
                        </div>
                        <div class="modal-footer">
                            <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Annuler</button>
                            <button type="submit" class="btn btn-primary" id="btnSavePeriode"><i class="fa-solid fa-save me-1"></i>Enregistrer</button>
                        </div>
                    </form>
                </div>
            </div>
        </div>
        """);

        return sb.toString();
    }

    // ============================================================
    // SCRIPTS
    // ============================================================
    private static String genererScripts() {
        return """
        <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
        <script>
            const API_BASE = '/admin/parametres/periodes/api/periodes';
            const modal = new bootstrap.Modal(document.getElementById('periodeModal'));
            const form = document.getElementById('periodeForm');
            const tbody = document.getElementById('periodesBody');

            // ================= UTILITAIRES =================
            function showToast(message, type) {
                const container = document.getElementById('toastContainer');
                const div = document.createElement('div');
                div.className = 'toast-custom ' + type;
                div.textContent = message;
                container.appendChild(div);
                setTimeout(() => div.remove(), 3000);
            }

            function escapeHtml(str) {
                if (str === null || str === undefined) return '';
                return String(str)
                    .replace(/&/g, '&amp;')
                    .replace(/</g, '&lt;')
                    .replace(/>/g, '&gt;')
                    .replace(/"/g, '&quot;')
                    .replace(/'/g, '&#39;');
            }

            /**
             * Construit la clé URL encodée à partir de l'année et de la période.
             * Exemple : "2026-2027/3eme%20Periode"
             */
            function buildCleUrl(annee, periode) {
                return encodeURIComponent(annee || '') + '/' + encodeURIComponent(periode || '');
            }

            // ================= CHARGEMENT =================
            function loadPeriodes() {
                const annee = document.getElementById('filterAnnee').value;
                const url = annee ? API_BASE + '?annee=' + encodeURIComponent(annee) : API_BASE;

                fetch(url)
                    .then(res => res.json())
                    .then(data => {
                        if (data.success) {
                            renderTable(data.data);
                        } else {
                            showToast('Erreur chargement : ' + (data.error || 'inconnue'), 'error');
                        }
                    })
                    .catch(err => {
                        console.error(err);
                        showToast('Erreur réseau', 'error');
                    });
            }

            function applyFilter() {
                loadPeriodes();
            }

            // ================= RENDU DU TABLEAU =================
            function renderTable(periodes) {
                if (!periodes || periodes.length === 0) {
                    tbody.innerHTML = `
                        <tr>
                            <td colspan="7" class="text-center py-5 text-muted">
                                <i class="fa-solid fa-inbox fs-1 mb-3 text-black-50 d-block"></i>
                                <span>Aucune période enregistrée.</span>
                            </td>
                        </tr>`;
                    return;
                }

                let html = '';
                periodes.forEach((p, index) => {
                    const estActive = p.estActive;
                    const statut = estActive
                        ? '<span class="badge-soft-success"><i class="fa-solid fa-check-circle me-1"></i>Active</span>'
                        : '<span class="badge-soft-danger"><i class="fa-solid fa-circle-xmark me-1"></i>Inactive</span>';

                    const anneeEsc = escapeHtml(p.anneeAcademique || '');
                    const periodeEsc = escapeHtml(p.periode || '');

                    html += `
                        <tr>
                            <td class="ps-4"><span class="badge-soft-primary">${index + 1}</span></td>
                            <td class="fw-semibold">${anneeEsc}</td>
                            <td class="fw-semibold text-dark">${periodeEsc}</td>
                            <td>${escapeHtml(p.dateDebut || '-')}</td>
                            <td>${escapeHtml(p.dateFin || '-')}</td>
                            <td>${statut}</td>
                            <td class="pe-4 text-center">
                                <button class="btn btn-sm btn-outline-primary btn-edit me-1"
                                    data-annee="${anneeEsc}"
                                    data-periode="${periodeEsc}">
                                    <i class="fa-solid fa-pen"></i>
                                </button>
                                <button class="btn btn-sm btn-outline-danger btn-delete"
                                    data-annee="${anneeEsc}"
                                    data-periode="${periodeEsc}">
                                    <i class="fa-solid fa-trash"></i>
                                </button>
                            </td>
                        </tr>`;
                });

                tbody.innerHTML = html;

                // Écouteurs
                document.querySelectorAll('.btn-edit').forEach(btn => {
                    btn.addEventListener('click', function () {
                        openEditModal(this.dataset.annee, this.dataset.periode);
                    });
                });

                document.querySelectorAll('.btn-delete').forEach(btn => {
                    btn.addEventListener('click', function () {
                        if (confirm('Voulez-vous vraiment supprimer cette période ?')) {
                            deletePeriode(this.dataset.annee, this.dataset.periode);
                        }
                    });
                });
            }

            // ================= ÉDITION =================
            function openEditModal(annee, periode) {
                const url = API_BASE + '/' + buildCleUrl(annee, periode);

                fetch(url)
                    .then(res => res.json())
                    .then(data => {
                        if (!data.success) {
                            showToast('Erreur : ' + (data.error || 'inconnue'), 'error');
                            return;
                        }
                        const p = data.data;

                        document.getElementById('editMode').value = 'edit';
                        document.getElementById('editOldAnnee').value = p.anneeAcademique || '';
                        document.getElementById('editOldPeriode').value = p.periode || '';
                        document.getElementById('inputAnnee').value = p.anneeAcademique || '';
                        document.getElementById('inputPeriode').value = p.periode || '';
                        document.getElementById('inputDateDebut').value = p.dateDebut || '';
                        document.getElementById('inputDateFin').value = p.dateFin || '';
                        document.getElementById('inputActive').checked = !!p.estActive;
                        document.getElementById('modalTitle').innerHTML =
                            '<i class="fa-solid fa-pen-to-square me-2"></i>Modifier la période';

                        modal.show();
                    })
                    .catch(err => {
                        console.error(err);
                        showToast('Erreur réseau', 'error');
                    });
            }

            function resetForm() {
                document.getElementById('editMode').value = 'add';
                document.getElementById('editOldAnnee').value = '';
                document.getElementById('editOldPeriode').value = '';
                document.getElementById('inputAnnee').value = '';
                document.getElementById('inputPeriode').value = '';
                document.getElementById('inputDateDebut').value = '';
                document.getElementById('inputDateFin').value = '';
                document.getElementById('inputActive').checked = true;
                document.getElementById('modalTitle').innerHTML =
                    '<i class="fa-solid fa-plus-circle me-2"></i>Ajouter une période';
            }

            // ================= SAUVEGARDE =================
            function savePeriode(event) {
                event.preventDefault();

                const mode = document.getElementById('editMode').value;
                const oldAnnee = document.getElementById('editOldAnnee').value;
                const oldPeriode = document.getElementById('editOldPeriode').value;
                const anneeAcademique = document.getElementById('inputAnnee').value;
                const periode = document.getElementById('inputPeriode').value.trim();
                const dateDebut = document.getElementById('inputDateDebut').value;
                const dateFin = document.getElementById('inputDateFin').value;
                const estActive = document.getElementById('inputActive').checked;

                if (!anneeAcademique || !periode || !dateDebut || !dateFin) {
                    showToast('Veuillez remplir tous les champs obligatoires (*)', 'error');
                    return;
                }

                const payload = { anneeAcademique, periode, dateDebut, dateFin, estActive };

                let method, url;
                if (mode === 'edit') {
                    method = 'PUT';
                    url = API_BASE + '/' + buildCleUrl(oldAnnee, oldPeriode);
                } else {
                    method = 'POST';
                    url = API_BASE;
                }

                fetch(url, {
                    method: method,
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(payload)
                })
                .then(res => res.json())
                .then(data => {
                    if (data.success) {
                        modal.hide();
                        showToast(data.message || 'Opération réussie', 'success');
                        loadPeriodes();
                    } else {
                        showToast('Erreur : ' + (data.error || 'inconnue'), 'error');
                    }
                })
                .catch(err => {
                    console.error(err);
                    showToast('Erreur réseau', 'error');
                });
            }

            // ================= SUPPRESSION =================
            function deletePeriode(annee, periode) {
                const url = API_BASE + '/' + buildCleUrl(annee, periode);

                fetch(url, { method: 'DELETE' })
                    .then(res => res.json())
                    .then(data => {
                        if (data.success) {
                            showToast('Période supprimée', 'success');
                            loadPeriodes();
                        } else {
                            showToast('Erreur : ' + (data.error || 'inconnue'), 'error');
                        }
                    })
                    .catch(err => {
                        console.error(err);
                        showToast('Erreur réseau', 'error');
                    });
            }

            // ================= ÉCOUTEURS =================
            document.getElementById('btnAjouter').addEventListener('click', function () {
                resetForm();
                modal.show();
            });

            document.getElementById('btnRafraichir').addEventListener('click', loadPeriodes);
            form.addEventListener('submit', savePeriode);

            // ================= INITIALISATION =================
            loadPeriodes();
        </script>
        """;
    }

    // ============================================================
    // ÉCHAPPEMENT HTML
    // ============================================================
    private static String echapperHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }
}