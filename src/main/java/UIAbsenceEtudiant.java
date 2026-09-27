public class UIAbsenceEtudiant {

    public static String renderPage(String institutionId, String numeroIdentifiantEtudiant) {
        return """
        <!DOCTYPE html>
        <html lang="fr">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Mes Absences - Espace Étudiant</title>
            <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
            <link href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css" rel="stylesheet">
            <style>
                body { background-color: #f4f6f9; color: #333; }
                .container-fluid {
                    width: 100%%;
                    padding-left: 2rem;
                    padding-right: 2rem;
                    margin-top: 25px;
                    margin-bottom: 25px;
                }
                .page-header {
                    text-align: center;
                    margin-bottom: 25px;
                }
                .page-header h2 {
                    font-weight: 700;
                    color: #2c3e50;
                }
                .filter-bar {
                    background: #ffffff;
                    padding: 20px;
                    border-radius: 12px;
                    box-shadow: 0 4px 6px rgba(0,0,0,0.02);
                    margin-bottom: 25px;
                    border: 1px solid rgba(0,0,0,0.05);
                }
                .card {
                    border: none;
                    border-radius: 12px;
                    box-shadow: 0 4px 6px rgba(0,0,0,0.02);
                    width: 100%%;
                }
                .table th {
                    font-weight: 600;
                    color: #495057;
                    background-color: #f8f9fa;
                    border-bottom: 2px solid #dee2e6;
                }
            </style>
        </head>
        <body>
            <div class="container-fluid">
                <div class="page-header">
                    <h2><i class="fas fa-user-clock text-primary me-2"></i>Mes Absences</h2>
                    <p class="text-muted">Consultez l'historique et le statut de vos absences</p>
                </div>

                <!-- Filtres -->
                <div class="filter-bar row g-3 align-items-center">
                    <div class="col-md-4">
                        <label class="form-label small text-muted fw-bold">Année Académique</label>
                        <select id="filterAnnee" class="form-select" onchange="onAnneeChange()">
                            <option value="">Toutes les années</option>
                        </select>
                    </div>
                    <div class="col-md-4">
                        <label class="form-label small text-muted fw-bold">Période</label>
                        <select id="filterPeriode" class="form-select" onchange="loadMesAbsences()">
                            <option value="">Toutes les périodes</option>
                        </select>
                    </div>
                    <div class="col-md-4">
                        <label class="form-label small text-muted fw-bold">Classe</label>
                        <select id="filterClasse" class="form-select" onchange="loadMesAbsences()">
                            <option value="">Toutes les classes</option>
                        </select>
                    </div>
                </div>

                <!-- Tableau Plein Écran -->
                <div class="card">
                    <div class="card-body p-0">
                        <div class="table-responsive">
                            <table class="table table-hover align-middle mb-0">
                                <thead>
                                    <tr>
                                        <th class="ps-3">Date</th>
                                        <th>Matière / Cours</th>
                                        <th>Période</th>
                                        <th>Classe</th>
                                        <th>Statut</th>
                                        <th class="pe-3">Motif</th>
                                    </tr>
                                </thead>
                                <tbody id="tableBody">
                                </tbody>
                            </table>
                        </div>
                    </div>
                </div>
            </div>

            <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
            <script>
                // URL pointant vers le HandlerAbsenceEtudiant
                const API_BASE = '/etudiant/absences/api';
                const numeroEtudiant = '%s';

                document.addEventListener('DOMContentLoaded', () => {
                    initApp();
                });

                async function initApp() {
                    await loadAnnees();
                    loadMesAbsences();
                }

                async function loadAnnees() {
                    const res = await fetch(API_BASE + '/annees');
                    const json = await res.json();
                    if (json.success) {
                        const select = document.getElementById('filterAnnee');
                        select.innerHTML = '<option value="">Toutes les années</option>';
                        json.data.forEach(item => {
                            select.innerHTML += `<option value="${item}">${item}</option>`;
                        });
                    }
                }

                async function onAnneeChange() {
                    const annee = document.getElementById('filterAnnee').value;
                    await loadPeriodes(annee);
                    await loadClasses(annee);
                    loadMesAbsences();
                }

                async function loadPeriodes(annee) {
                    let url = API_BASE + '/periodes';
                    if (annee) url += '?anneeAcademique=' + encodeURIComponent(annee);
                    const res = await fetch(url);
                    const json = await res.json();
                    if (json.success) {
                        const select = document.getElementById('filterPeriode');
                        select.innerHTML = '<option value="">Toutes les périodes</option>';
                        json.data.forEach(item => {
                            select.innerHTML += `<option value="${item}">${item}</option>`;
                        });
                    }
                }

                async function loadClasses(annee) {
                    let url = API_BASE + '/classes';
                    if (annee) url += '?anneeAcademique=' + encodeURIComponent(annee);
                    const res = await fetch(url);
                    const json = await res.json();
                    if (json.success) {
                        const select = document.getElementById('filterClasse');
                        select.innerHTML = '<option value="">Toutes les classes</option>';
                        json.data.forEach(item => {
                            select.innerHTML += `<option value="${item}">${item}</option>`;
                        });
                    }
                }

                async function loadMesAbsences() {
                    const annee = document.getElementById('filterAnnee').value;
                    const periode = document.getElementById('filterPeriode').value;
                    const classe = document.getElementById('filterClasse').value;

                    const params = new URLSearchParams({
                        numeroIdentifiant: numeroEtudiant,
                        anneeAcademique: annee,
                        periode: periode,
                        classe: classe
                    });

                    const res = await fetch(`${API_BASE}/list?${params.toString()}`);
                    const json = await res.json();
                    
                    const tbody = document.getElementById('tableBody');
                    tbody.innerHTML = '';

                    if (json.success && json.data) {
                        json.data.forEach(a => {
                            const badgeClass = a.justifiee ? 'bg-success bg-opacity-10 text-success' : 'bg-danger bg-opacity-10 text-danger';
                            const badgeText = a.justifiee ? 'Justifiée' : 'Non justifiée';
                            
                            tbody.innerHTML += `
                                <tr>
                                    <td class="ps-3">${a.date}</td>
                                    <td class="fw-bold">${a.codeCours}</td>
                                    <td>${a.periode}</td>
                                    <td>${a.classe || '-'}</td>
                                    <td><span class="badge ${badgeClass} px-2 py-1">${badgeText}</span></td>
                                    <td class="pe-3 text-muted">${a.motif || '-'}</td>
                                </tr>
                            `;
                        });
                        
                        if(json.data.length === 0) {
                             tbody.innerHTML = `<tr><td colspan="6" class="text-center text-muted py-4">Aucune absence enregistrée</td></tr>`;
                        }
                    }
                }
            </script>
        </body>
        </html>
        """.formatted(numeroIdentifiantEtudiant);
    }

       public static String renderError(String message) {
        return "<!DOCTYPE html>"
            + "<html><head><meta charset='UTF-8'><title>Erreur</title>"
            + "<style>"
            + "*{margin:0;padding:0;box-sizing:border-box;font-family:sans-serif;}"
            + "body{background:#f4f6f9;display:flex;justify-content:center;align-items:center;height:100vh;}"
            + ".error{background:#fff;padding:40px;border-radius:16px;text-align:center;max-width:500px;}"
            + ".error i{font-size:64px;color:#dc2626;margin-bottom:20px;}"
            + ".error h1{color:#1e293b;margin-bottom:10px;}"
            + ".error p{color:#64748b;}"
            + ".error .btn{display:inline-block;margin-top:20px;padding:10px 24px;background:#1e40af;color:#fff;text-decoration:none;border-radius:8px;}"
            + "</style>"
            + "</head><body>"
            + "<div class='error'>"
            + "<i class='fas fa-exclamation-triangle'></i>"
            + "<h1>Erreur</h1>"
            + "<p>" + echapperHtml(message) + "</p>"
            + "<a href='/etudiant/dashboard' class='btn'><i class='fas fa-arrow-left'></i> Retour au dashboard</a>"
            + "</div></body></html>";
    }
    private static String echapperHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }
}