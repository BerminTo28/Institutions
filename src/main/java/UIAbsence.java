public class UIAbsence {

    public static String renderPage(String institutionId, String ongletActif,
                                    boolean canCreate, boolean canUpdate, boolean canDelete) {
        String activeType = "professeur".equalsIgnoreCase(ongletActif) ? "PROFESSEUR" : "ETUDIANT";

        // Construction du bouton Ajouter conditionnel
        String addButtonHtml = canCreate ?
                "<button class=\"btn btn-primary w-100 mt-4\" onclick=\"openModal()\"><i class=\"fas fa-plus me-1\"></i> Ajouter</button>" :
                "<span class=\"text-muted w-100 mt-4 d-block text-center\" style=\"font-size:0.9rem;\"><i class=\"fas fa-lock me-1\"></i> Création restreinte</span>";

        return """
        <!DOCTYPE html>
        <html lang="fr">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Gestion des Absences</title>
            <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
            <link href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css" rel="stylesheet">
            <style>
                body { background-color: #f4f6f9; color: #333; }
                .container-fluid { width: 100%%; padding-left: 2rem; padding-right: 2rem; margin-top: 25px; margin-bottom: 25px; }
                .page-header { text-align: center; margin-bottom: 25px; }
                .page-header h2 { font-weight: 700; color: #2c3e50; }
                .nav-pills { justify-content: center; margin-bottom: 25px; }
                .nav-pills .nav-link { font-weight: 600; padding: 10px 25px; border-radius: 50rem; color: #495057; background-color: #e9ecef; margin: 0 5px; transition: all 0.3s ease; cursor: pointer; }
                .nav-pills .nav-link.active { background-color: #0d6efd; color: #fff; box-shadow: 0 4px 10px rgba(13, 110, 253, 0.3); }
                .filter-bar { background: #ffffff; padding: 20px; border-radius: 12px; box-shadow: 0 4px 6px rgba(0,0,0,0.02); margin-bottom: 25px; border: 1px solid rgba(0,0,0,0.05); }
                .card { border: none; border-radius: 12px; box-shadow: 0 4px 6px rgba(0,0,0,0.02); width: 100%%; }
                .table th { font-weight: 600; color: #495057; background-color: #f8f9fa; border-bottom: 2px solid #dee2e6; }
                .btn-icon-only { border: none; background: transparent; padding: 0 4px; }
                .btn-icon-only:hover { opacity: 0.7; }
                .text-muted-lock { font-size: 0.85rem; color: #94a3b8; display: flex; align-items: center; justify-content: center; gap: 4px; height: 100%%; min-height: 38px; }
            </style>
        </head>
        <body>
            <div class="container-fluid">
                <div class="page-header">
                    <h2><i class="fas fa-calendar-times text-primary me-2"></i>Gestion des Absences</h2>
                    <p class="text-muted">Suivi et administration des absences par catégorie</p>
                </div>

                <!-- Onglets -->
                <ul class="nav nav-pills" id="absenceTabs">
                    <li class="nav-item">
                        <a class="nav-link %s" id="tab-etudiant" onclick="switchTab('ETUDIANT')"><i class="fas fa-user-graduate me-2"></i> Étudiants</a>
                    </li>
                    <li class="nav-item">
                        <a class="nav-link %s" id="tab-professeur" onclick="switchTab('PROFESSEUR')"><i class="fas fa-chalkboard-teacher me-2"></i> Professeurs</a>
                    </li>
                </ul>

                <!-- Filtres -->
                <div class="filter-bar row g-3 align-items-center">
                    <div class="col-xxl-2 col-xl-2 col-lg-3 col-md-4">
                        <label class="form-label small text-muted fw-bold">Année</label>
                        <select id="filterAnnee" class="form-select" onchange="onAnneeChange()">
                            <option value="">Toutes les années</option>
                        </select>
                    </div>
                    <div class="col-xxl-2 col-xl-2 col-lg-3 col-md-4">
                        <label class="form-label small text-muted fw-bold">Période</label>
                        <select id="filterPeriode" class="form-select" onchange="loadAbsences()">
                            <option value="">Toutes les périodes</option>
                        </select>
                    </div>
                    <div class="col-xxl-3 col-xl-3 col-lg-3 col-md-4">
                        <label class="form-label small text-muted fw-bold">Classe</label>
                        <select id="filterClasse" class="form-select" onchange="loadAbsences()">
                            <option value="">Toutes les classes</option>
                        </select>
                    </div>
                    <div class="col-xxl-3 col-xl-3 col-lg-3 col-md-6">
                        <label class="form-label small text-muted fw-bold">Matière</label>
                        <select id="filterMatiere" class="form-select" onchange="loadAbsences()">
                            <option value="">Toutes les matières</option>
                        </select>
                    </div>
                    <div class="col-xxl-2 col-xl-2 col-lg-3 col-md-6 d-flex align-items-end">
                        %s
                    </div>
                </div>

                <!-- Tableau -->
                <div class="card">
                    <div class="card-body p-0">
                        <div class="table-responsive">
                            <table class="table table-hover align-middle mb-0">
                                <thead>
                                    <tr>
                                        <th class="ps-3">Date</th>
                                        <th>Identifiant</th>
                                        <th>Nom Complet</th>
                                        <th>Classe</th>
                                        <th>Matière</th>
                                        <th>Période</th>
                                        <th>Statut</th>
                                        <th class="text-end pe-3">Actions</th>
                                    </tr>
                                </thead>
                                <tbody id="tableBody">
                                </tbody>
                            </table>
                        </div>
                    </div>
                </div>
            </div>

            <!-- Modal -->
            <div class="modal fade" id="absenceModal" tabindex="-1">
                <div class="modal-dialog modal-lg">
                    <div class="modal-content">
                        <div class="modal-header">
                            <h5 class="modal-title fw-bold" id="modalTitle">Enregistrer une absence</h5>
                            <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                        </div>
                        <div class="modal-body">
                            <form id="absenceForm">
                                <input type="hidden" id="absenceId">
                                <div class="row mb-3">
                                    <div class="col-md-6">
                                        <label class="form-label">Année Académique</label>
                                        <select class="form-select" id="formAnnee" required onchange="onFormAnneeChange()"></select>
                                    </div>
                                    <div class="col-md-6">
                                        <label class="form-label">Période</label>
                                        <select class="form-select" id="formPeriode" required></select>
                                    </div>
                                </div>
                                <div class="row mb-3">
                                    <div class="col-md-6">
                                        <label class="form-label">Classe</label>
                                        <select class="form-select" id="formClasse" required onchange="loadPersonnesForForm()"></select>
                                    </div>
                                    <div class="col-md-6">
                                        <label class="form-label">Matière / Cours</label>
                                        <select class="form-select" id="formMatiere" required></select>
                                    </div>
                                </div>
                                <div class="mb-3">
                                    <label class="form-label" id="labelPersonne">Personne concernée</label>
                                    <select class="form-select" id="formPersonne" required>
                                        <option value="">Sélectionnez d'abord les filtres...</option>
                                    </select>
                                </div>
                                <div class="mb-3">
                                    <label class="form-label">Date de l'absence</label>
                                    <input type="date" class="form-control" id="formDate" required>
                                </div>
                                <div class="form-check form-switch mb-3">
                                    <input class="form-check-input" type="checkbox" id="formJustifiee">
                                    <label class="form-check-label" for="formJustifiee">Absence Justifiée</label>
                                </div>
                                <div class="mb-3">
                                    <label class="form-label">Motif (Optionnel)</label>
                                    <textarea class="form-control" id="formMotif" rows="2"></textarea>
                                </div>
                            </form>
                        </div>
                        <div class="modal-footer">
                            <button type="button" class="btn btn-light" data-bs-dismiss="modal">Annuler</button>
                            <button type="button" class="btn btn-primary" onclick="saveAbsence()">Enregistrer</button>
                        </div>
                    </div>
                </div>
            </div>

            <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
            <script>
                let currentTab = '%s';
                const API_BASE = '/admin/absences/api';
                let absenceModal;
                const canUpdate = %s;
                const canDelete = %s;

                document.addEventListener('DOMContentLoaded', () => {
                    absenceModal = new bootstrap.Modal(document.getElementById('absenceModal'));
                    initApp();
                });

                async function initApp() {
                    await loadAnnees();
                    await loadMatieres();
                    loadAbsences();
                }

                function switchTab(type) {
                    currentTab = type;
                    document.getElementById('tab-etudiant').classList.remove('active');
                    document.getElementById('tab-professeur').classList.remove('active');
                    if (type === 'ETUDIANT') {
                        document.getElementById('tab-etudiant').classList.add('active');
                    } else {
                        document.getElementById('tab-professeur').classList.add('active');
                    }
                    loadAbsences();
                }

                async function loadAnnees() {
                    const res = await fetch(API_BASE + '/annees');
                    const json = await res.json();
                    if (json.success) {
                        populateSelect('filterAnnee', json.data);
                        populateSelect('formAnnee', json.data);
                    }
                }

                async function loadMatieres() {
                    const res = await fetch(API_BASE + '/matieres');
                    const json = await res.json();
                    if (json.success) {
                        populateSelect('filterMatiere', json.data);
                        populateSelect('formMatiere', json.data);
                    }
                }

                async function loadPeriodes(annee) {
                    let url = API_BASE + '/periodes';
                    if (annee) url += '?anneeAcademique=' + encodeURIComponent(annee);
                    const res = await fetch(url);
                    const json = await res.json();
                    if (json.success) {
                        populateSelect('filterPeriode', json.data);
                        populateSelect('formPeriode', json.data);
                    }
                }

                async function loadClasses(annee) {
                    let url = API_BASE + '/classes';
                    if (annee) url += '?anneeAcademique=' + encodeURIComponent(annee);
                    const res = await fetch(url);
                    const json = await res.json();
                    if (json.success) {
                        populateSelect('filterClasse', json.data);
                        populateSelect('formClasse', json.data);
                    }
                }

                function populateSelect(selectId, data) {
                    const select = document.getElementById(selectId);
                    if (!select) return;
                    const currentValue = select.value;
                    const defaultOption = select.options.length > 0 ? select.options[0].outerHTML : '';
                    select.innerHTML = defaultOption;
                    data.forEach(item => {
                        select.innerHTML += `<option value="${item}">${item}</option>`;
                    });
                    if (currentValue && [...select.options].some(opt => opt.value === currentValue)) {
                        select.value = currentValue;
                    }
                }

                async function onAnneeChange() {
                    const annee = document.getElementById('filterAnnee').value;
                    await loadPeriodes(annee);
                    await loadClasses(annee);
                    loadAbsences();
                }

                async function onFormAnneeChange() {
                    const annee = document.getElementById('formAnnee').value;
                    await loadPeriodes(annee);
                    await loadClasses(annee);
                    loadPersonnesForForm();
                }

                async function loadAbsences() {
                    const annee = document.getElementById('filterAnnee').value;
                    const periode = document.getElementById('filterPeriode').value;
                    const classe = document.getElementById('filterClasse').value;
                    const matiere = document.getElementById('filterMatiere').value;

                    const params = new URLSearchParams({
                        type: currentTab,
                        anneeAcademique: annee,
                        periode: periode,
                        classe: classe,
                        matiere: matiere
                    });

                    const res = await fetch(`${API_BASE}/list?${params.toString()}`);
                    const json = await res.json();

                    const tbody = document.getElementById('tableBody');
                    tbody.innerHTML = '';

                    if (json.success && json.data) {
                        json.data.forEach(a => {
                            const badgeClass = a.justifiee ? 'bg-success bg-opacity-10 text-success' : 'bg-danger bg-opacity-10 text-danger';
                            const badgeText = a.justifiee ? 'Justifiée' : 'Non justifiée';

                            let actionsHtml = '';
                            if (canUpdate) {
                                actionsHtml += `<button class="btn btn-sm btn-light text-primary me-1" onclick="editAbsence(${a.id})" title="Modifier"><i class="fas fa-edit"></i></button>`;
                            } else {
                                actionsHtml += `<span class="text-muted me-1" title="Modification non autorisée"><i class="fas fa-lock" style="font-size:0.8rem;"></i></span>`;
                            }
                            if (canDelete) {
                                actionsHtml += `<button class="btn btn-sm btn-light text-danger" onclick="deleteAbsence(${a.id})" title="Supprimer"><i class="fas fa-trash"></i></button>`;
                            } else {
                                actionsHtml += `<span class="text-muted" title="Suppression non autorisée"><i class="fas fa-lock" style="font-size:0.8rem;"></i></span>`;
                            }

                            tbody.innerHTML += `
                                <tr>
                                    <td class="ps-3">${a.date}</td>
                                    <td>${a.numeroIdentifiant}</td>
                                    <td class="fw-bold">${a.nomComplet}</td>
                                    <td>${a.classe || '-'}</td>
                                    <td>${a.codeCours}</td>
                                    <td>${a.periode}</td>
                                    <td><span class="badge ${badgeClass} px-2 py-1">${badgeText}</span></td>
                                    <td class="text-end pe-3">${actionsHtml}</td>
                                </tr>
                            `;
                        });

                        if(json.data.length === 0) {
                             tbody.innerHTML = `<tr><td colspan="8" class="text-center text-muted py-4">Aucune absence trouvée</td></tr>`;
                        }
                    }
                }

                async function openModal() {
                    document.getElementById('absenceForm').reset();
                    document.getElementById('absenceId').value = '';
                    document.getElementById('modalTitle').innerText = `Enregistrer une absence (${currentTab === 'ETUDIANT' ? 'Étudiant' : 'Professeur'})`;

                    if (document.getElementById('filterAnnee').value) document.getElementById('formAnnee').value = document.getElementById('filterAnnee').value;
                    if (document.getElementById('filterPeriode').value) document.getElementById('formPeriode').value = document.getElementById('filterPeriode').value;
                    if (document.getElementById('filterClasse').value) document.getElementById('formClasse').value = document.getElementById('filterClasse').value;
                    if (document.getElementById('filterMatiere').value) document.getElementById('formMatiere').value = document.getElementById('filterMatiere').value;

                    document.getElementById('labelPersonne').innerText = currentTab === 'ETUDIANT' ? 'Étudiant' : 'Professeur';

                    const annee = document.getElementById('formAnnee').value;
                    await loadPeriodes(annee);
                    await loadClasses(annee);

                    if (document.getElementById('filterPeriode').value) {
                        const fp = document.getElementById('filterPeriode').value;
                        if ([...document.getElementById('formPeriode').options].some(opt => opt.value === fp))
                            document.getElementById('formPeriode').value = fp;
                    }
                    if (document.getElementById('filterClasse').value) {
                        const fc = document.getElementById('filterClasse').value;
                        if ([...document.getElementById('formClasse').options].some(opt => opt.value === fc))
                            document.getElementById('formClasse').value = fc;
                    }

                    await loadPersonnesForForm();
                    absenceModal.show();
                }

                async function loadPersonnesForForm(selectedId = null) {
                    const annee = document.getElementById('formAnnee').value;
                    const periode = document.getElementById('formPeriode').value;
                    const classe = document.getElementById('formClasse').value;

                    const endpoint = currentTab === 'ETUDIANT' ? '/etudiants' : '/professeurs';
                    const params = new URLSearchParams({ anneeAcademique: annee, periode: periode, classe: classe });

                    const res = await fetch(`${API_BASE}${endpoint}?${params.toString()}`);
                    const json = await res.json();

                    const formPersonne = document.getElementById('formPersonne');
                    formPersonne.innerHTML = '<option value="">-- Sélectionner --</option>';

                    if (json.success && json.data) {
                        json.data.forEach(p => {
                            formPersonne.innerHTML += `<option value="${p.id}">${p.id} - ${p.nom}</option>`;
                        });
                    }

                    if (selectedId) {
                        formPersonne.value = selectedId;
                    }
                }

                async function saveAbsence() {
                    const idStr = document.getElementById('absenceId').value;
                    const isUpdate = idStr !== '';

                    const data = {
                        personneType: currentTab,
                        anneeAcademique: document.getElementById('formAnnee').value,
                        periode: document.getElementById('formPeriode').value,
                        classe: document.getElementById('formClasse').value,
                        codeCours: document.getElementById('formMatiere').value,
                        numeroIdentifiant: document.getElementById('formPersonne').value,
                        date: document.getElementById('formDate').value,
                        justifiee: document.getElementById('formJustifiee').checked,
                        motif: document.getElementById('formMotif').value
                    };

                    if (isUpdate) {
                        data.id = parseInt(idStr, 10);
                    }

                    if (!data.numeroIdentifiant || !data.date || !data.codeCours) {
                        alert("Veuillez remplir tous les champs obligatoires via les listes.");
                        return;
                    }

                    const url = isUpdate ? API_BASE + '/update' : API_BASE + '/add';
                    const res = await fetch(url, {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify(data)
                    });

                    const json = await res.json();
                    if (json.success) {
                        absenceModal.hide();
                        loadAbsences();
                    } else {
                        alert("Erreur : " + json.error);
                    }
                }

                async function editAbsence(id) {
                    const res = await fetch(`${API_BASE}/get?id=${id}`);
                    const json = await res.json();

                    if (json.success && json.data) {
                        const a = json.data;
                        document.getElementById('absenceId').value = a.id;
                        document.getElementById('formAnnee').value = a.anneeAcademique || '';

                        await onFormAnneeChange();

                        document.getElementById('formPeriode').value = a.periode || '';
                        document.getElementById('formClasse').value = a.classe || '';
                        document.getElementById('formMatiere').value = a.codeCours || '';
                        document.getElementById('formDate').value = a.date || '';
                        document.getElementById('formJustifiee').checked = a.justifiee || false;
                        document.getElementById('formMotif').value = a.motif || '';

                        document.getElementById('modalTitle').innerText = "Modifier l'absence";

                        if (a.personneType !== currentTab) {
                            switchTab(a.personneType);
                        }

                        await loadPersonnesForForm(a.numeroIdentifiant);
                        absenceModal.show();
                    }
                }

                async function deleteAbsence(id) {
                    if (confirm("Êtes-vous sûr de vouloir supprimer cette absence ?")) {
                        const res = await fetch(`${API_BASE}/delete`, {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({ id: id })
                        });
                        const json = await res.json();
                        if (json.success) {
                            loadAbsences();
                        } else {
                            alert("Erreur lors de la suppression : " + json.error);
                        }
                    }
                }
            </script>
        </body>
        </html>
        """.formatted(
            "ETUDIANT".equals(activeType) ? "active" : "",
            "PROFESSEUR".equals(activeType) ? "active" : "",
            addButtonHtml,
            activeType,
            canUpdate,
            canDelete
        );
    }

    /**
     * Page d'erreur pour le handler.
     */
    public static String renderError(String message) {
        return """
        <!DOCTYPE html>
        <html lang="fr">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Erreur - Gestion des Absences</title>
            <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
            <link href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css" rel="stylesheet">
            <style>
                body { background-color: #f4f6f9; display: flex; justify-content: center; align-items: center; min-height: 100vh; padding: 20px; }
                .error-box { max-width: 600px; background: white; border-radius: 16px; padding: 40px; box-shadow: 0 4px 12px rgba(0,0,0,0.05); text-align: center; }
                .error-box i { font-size: 48px; color: #dc2626; margin-bottom: 20px; }
                .error-box h1 { color: #1e293b; font-size: 24px; margin-bottom: 10px; }
                .error-box p { color: #64748b; font-size: 14px; }
                .error-box .btn { margin-top: 20px; }
            </style>
        </head>
        <body>
            <div class="error-box">
                <i class="fas fa-exclamation-triangle"></i>
                <h1>Erreur</h1>
                <p>%s</p>
                <a href="/admin/absences" class="btn btn-primary"><i class="fas fa-arrow-left me-2"></i>Retour</a>
            </div>
        </body>
        </html>
        """.formatted(escapeHtml(message != null ? message : "Une erreur inattendue s'est produite."));
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