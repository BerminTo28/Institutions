public class UIEmploiduTemps {

    public static String renderPage(String institutionId) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>");
        sb.append("<html lang='fr'>");
        sb.append("<head>");
        sb.append("<meta charset='UTF-8'>");
        sb.append("<meta name='viewport' content='width=device-width, initial-scale=1.0'>");
        sb.append("<title>Emploi du temps</title>");
        sb.append("<link href='https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700;800&display=swap' rel='stylesheet'>");
        sb.append("<link rel='stylesheet' href='https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css'>");
        sb.append("""
            <style>
                * { margin: 0; padding: 0; box-sizing: border-box; }
                html, body { width: 100%; min-height: 100vh; background: #FAFBFB; font-family: 'Inter', sans-serif; }
                body { padding: 20px; }
                
                /* Conteneur fluide à 100% */
                .container { width: 100%; max-width: 100%; margin: 0 auto; }
                
                /* Navigation fluide et étirée */
                .nav-menu { display: flex; gap: 8px; background: white; border-radius: 12px; padding: 8px; margin-bottom: 20px; border: 1px solid #DCE1E6; flex-wrap: wrap; width: 100%; }
                .nav-btn { flex: 1 1 180px; text-align: center; padding: 12px 20px; background: none; border: none; cursor: pointer; font-weight: 600; border-radius: 8px; transition: all 0.2s ease-in-out; text-decoration: none; color: #333; }
                .nav-btn.active { background: #2563EB; color: white; }
                .nav-btn:hover:not(.active) { background: #EFF6FF; }

                /* En-tête moderne */
                .header {
                    background: white;
                    color: #1E293B;
                    padding: 24px;
                    border-radius: 16px;
                    margin-bottom: 20px;
                    box-shadow: 0 2px 8px rgba(0,0,0,0.06);
                    border: 1px solid #DCE1E6;
                    display: flex;
                    justify-content: space-between;
                    align-items: center;
                    flex-wrap: wrap;
                    gap: 15px;
                }
                .header-title-group h1 {
                    font-size: 20px;
                    font-weight: 700;
                    color: #2563EB;
                    display: flex;
                    align-items: center;
                    gap: 10px;
                }
                .header-title-group p {
                    font-size: 13px;
                    color: #64748B;
                    margin-top: 4px;
                }
                .header .retour {
                    display: inline-flex;
                    align-items: center;
                    gap: 8px;
                    color: #64748B;
                    background: #FAFBFB;
                    padding: 10px 18px;
                    border-radius: 8px;
                    text-decoration: none;
                    font-weight: 600;
                    font-size: 14px;
                    transition: all 0.2s;
                    border: 1px solid #DCE1E6;
                }
                .header .retour:hover {
                    background: #F1F5F9;
                    color: #0F172A;
                }

                /* Filtres en grille adaptative */
                .filters {
                    display: flex;
                    justify-content: space-between;
                    flex-wrap: wrap;
                    gap: 16px;
                    background: white;
                    padding: 24px;
                    border-radius: 16px;
                    margin-bottom: 20px;
                    box-shadow: 0 2px 8px rgba(0,0,0,0.06);
                    border: 1px solid #DCE1E6;
                    align-items: flex-end;
                }
                .filter-group {
                    display: flex;
                    flex-direction: column;
                    gap: 6px;
                    flex: 1;
                    min-width: 200px;
                }
                .filter-group label {
                    font-weight: 600;
                    font-size: 13px;
                    color: #334155;
                    display: flex;
                    align-items: center;
                    gap: 6px;
                }
                .filter-group select {
                    padding: 10px 14px;
                    border: 1px solid #DCE1E6;
                    border-radius: 8px;
                    font-size: 14px;
                    background: white;
                    transition: all 0.2s;
                    width: 100%;
                    font-family: 'Inter', sans-serif;
                }
                .filter-group select:focus {
                    border-color: #2563EB;
                    outline: none;
                    box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.1);
                }
                .filter-actions {
                    display: flex;
                    align-items: flex-end;
                    gap: 10px;
                }
                .btn {
                    padding: 10px 20px;
                    border: none;
                    border-radius: 8px;
                    font-weight: 600;
                    font-size: 14px;
                    cursor: pointer;
                    transition: all 0.2s;
                    display: inline-flex;
                    align-items: center;
                    gap: 8px;
                    height: 42px;
                }
                .btn-primary {
                    background: #2563EB;
                    color: white;
                }
                .btn-primary:hover {
                    background: #1D4ED8;
                }
                .btn-secondary {
                    background: #C62828;
                    color: white;
                }
                .btn-secondary:hover {
                    opacity: 0.85;
                }

                /* Tableau réactif */
                .card { background: white; border-radius: 16px; padding: 24px; margin-bottom: 20px; box-shadow: 0 2px 8px rgba(0,0,0,0.06); border: 1px solid #DCE1E6; width: 100%; }
                .table-responsive { width: 100%; overflow-x: auto; }
                table { width: 100%; border-collapse: collapse; }
                th {
                    background: #EFF6FF;
                    padding: 14px 16px;
                    font-weight: 600;
                    font-size: 13px;
                    text-align: center;
                    border-bottom: 1px solid #DCE1E6;
                    color: #1E293B;
                }
                td {
                    padding: 12px 16px;
                    border-bottom: 1px solid #E2E8F0;
                    text-align: center;
                    vertical-align: middle;
                    font-size: 14px;
                }
                tr:hover {
                    background: #FAFBFB;
                }
                .cours-cell {
                    background: #EFF6FF;
                    font-weight: 600;
                    color: #1E40AF;
                    border-radius: 6px;
                }
                .empty-state {
                    text-align: center;
                    padding: 50px 20px;
                    color: #64748B;
                }
                .empty-state i {
                    font-size: 42px;
                    color: #CBD5E1;
                    margin-bottom: 15px;
                }

                /* Responsive */
                @media (max-width: 768px) {
                    .filters {
                        flex-direction: column;
                        align-items: stretch;
                    }
                    .filter-group {
                        min-width: 100%;
                    }
                    .filter-actions {
                        width: 100%;
                        justify-content: flex-end;
                    }
                }
            </style>
        """);
        sb.append("</head>");
        sb.append("<body>");
        sb.append("<div class='container'>");

        // Menu de navigation contextuel
        sb.append("<div class='nav-menu'>");
        sb.append("<a href='/dashboard' class='nav-btn'><i class='fa-solid fa-arrow-left'></i> Tableau de bord</a>");
        sb.append("<a href='/admin/emploidutemps' class='nav-btn active'><i class='fa-solid fa-calendar-days'></i> Emploi du temps</a>");
        sb.append("</div>");

        // En-tête moderne
        sb.append("<div class='header'>");
        sb.append("<div class='header-title-group'>");
        sb.append("<h1><i class='fa-solid fa-calendar-days'></i> Emploi du temps</h1>");
        sb.append("<p>Consultez les créneaux par classe, période et année académique</p>");
        sb.append("</div>");
        sb.append("<a href='/dashboard' class='retour'><i class='fa-solid fa-arrow-left'></i> Retour</a>");
        sb.append("</div>");

        // Filtres
        sb.append("<div class='filters'>");
        sb.append("<div class='filter-group'>");
        sb.append("<label><i class='fa-solid fa-calendar'></i> Année</label>");
        sb.append("<select id='anneeSelect'><option value=''>Chargement...</option></select>");
        sb.append("</div>");
        sb.append("<div class='filter-group'>");
        sb.append("<label><i class='fa-solid fa-clock'></i> Période</label>");
        sb.append("<select id='periodeSelect'><option value=''>Chargement...</option></select>");
        sb.append("</div>");
        sb.append("<div class='filter-group'>");
        sb.append("<label><i class='fa-solid fa-graduation-cap'></i> Classe</label>");
        sb.append("<select id='classeSelect'><option value=''>Chargement...</option></select>");
        sb.append("</div>");
        sb.append("<div class='filter-actions'>");
        sb.append("<button class='btn btn-primary' id='btnCharger'><i class='fa-solid fa-search'></i> Afficher</button>");
        sb.append("<button class='btn btn-secondary' id='btnReset' title='Réinitialiser'><i class='fa-solid fa-rotate-left'></i></button>");
        sb.append("</div>");
        sb.append("</div>");

        // Tableau dans une carte
        sb.append("<div class='card'>");
        sb.append("<div class='table-responsive'>");
        sb.append("<table id='emploiTable'>");
        sb.append("<thead><tr>");
        sb.append("<th>Jour</th><th>Heure</th><th>Matière</th><th>Classe</th><th>Professeur</th>");
        sb.append("</tr></thead>");
        sb.append("<tbody id='emploiBody'>");
        sb.append("<tr><td colspan='5'><div class='empty-state'><i class='fa-regular fa-clock'></i><p>Sélectionnez les filtres et cliquez sur Afficher</p></div></td></tr>");
        sb.append("</tbody></table>");
        sb.append("</div>");
        sb.append("</div>");

        sb.append("</div>");

        // JavaScript
        sb.append("""
            <script>
                const institutionId = '%s';

                function loadFilters() {
                    fetch('/admin/emploidutemps/api/annees')
                        .then(res => res.json())
                        .then(data => {
                            const select = document.getElementById('anneeSelect');
                            select.innerHTML = '<option value="">Sélectionner</option>';
                            if (data.success && data.data) {
                                data.data.forEach(a => {
                                    const opt = document.createElement('option');
                                    opt.value = a;
                                    opt.textContent = a;
                                    select.appendChild(opt);
                                });
                            }
                        })
                        .catch(err => console.error('Erreur chargement années:', err));

                    fetch('/admin/emploidutemps/api/classes')
                        .then(res => res.json())
                        .then(data => {
                            const select = document.getElementById('classeSelect');
                            select.innerHTML = '<option value="">Toutes</option>';
                            if (data.success && data.data) {
                                data.data.forEach(c => {
                                    const opt = document.createElement('option');
                                    opt.value = c;
                                    opt.textContent = c;
                                    select.appendChild(opt);
                                });
                            }
                        })
                        .catch(err => console.error('Erreur chargement classes:', err));
                }

                document.getElementById('anneeSelect').addEventListener('change', function() {
                    const annee = this.value;
                    const select = document.getElementById('periodeSelect');
                    select.innerHTML = '<option value="">Chargement...</option>';
                    if (annee) {
                        fetch('/admin/emploidutemps/api/periodes?annee=' + encodeURIComponent(annee))
                            .then(res => res.json())
                            .then(data => {
                                select.innerHTML = '<option value="">Sélectionner</option>';
                                if (data.success && data.data) {
                                    data.data.forEach(p => {
                                        const opt = document.createElement('option');
                                        opt.value = p;
                                        opt.textContent = p;
                                        select.appendChild(opt);
                                    });
                                }
                            })
                            .catch(err => {
                                console.error('Erreur chargement périodes:', err);
                                select.innerHTML = '<option value="">Erreur</option>';
                            });
                    } else {
                        select.innerHTML = '<option value="">Sélectionner</option>';
                    }
                });

                function loadEmploi() {
                    const annee = document.getElementById('anneeSelect').value;
                    const periode = document.getElementById('periodeSelect').value;
                    const classe = document.getElementById('classeSelect').value;

                    if (!annee || !periode) {
                        alert('Veuillez sélectionner une année et une période.');
                        return;
                    }

                    const params = new URLSearchParams();
                    params.append('annee', annee);
                    params.append('periode', periode);
                    if (classe) {
                        params.append('classe', classe);
                    }

                    fetch('/admin/emploidutemps/api/creneaux?' + params)
                        .then(res => res.json())
                        .then(data => {
                            const tbody = document.getElementById('emploiBody');
                            if (data.success && data.data && data.data.length > 0) {
                                tbody.innerHTML = '';
                                const jourOrdre = {'Lundi':1,'Mardi':2,'Mercredi':3,'Jeudi':4,'Vendredi':5,'Samedi':6,'Dimanche':7};
                                data.data.sort((a,b) => (jourOrdre[a.jour] || 0) - (jourOrdre[b.jour] || 0) || a.heureDebut.localeCompare(b.heureDebut));
                                data.data.forEach(c => {
                                    const tr = document.createElement('tr');
                                    tr.innerHTML = `
                                        <td><strong>${c.jour || ''}</strong></td>
                                        <td>${c.heureDebut || ''} - ${c.heureFin || ''}</td>
                                        <td class="cours-cell">${c.nomMatiere || ''} (${c.codeCours || ''})</td>
                                        <td>${c.classe || ''}</td>
                                        <td>${c.professeurNom || ''}</td>
                                    `;
                                    tbody.appendChild(tr);
                                });
                            } else {
                                tbody.innerHTML = `<tr><td colspan="5"><div class="empty-state"><i class="fa-regular fa-circle-xmark"></i><p>Aucun créneau trouvé pour ces critères</p></div></td></tr>`;
                            }
                        })
                        .catch(err => {
                            console.error('Erreur chargement emploi:', err);
                            document.getElementById('emploiBody').innerHTML = `<tr><td colspan="5"><div class="empty-state"><i class="fa-solid fa-exclamation-triangle"></i><p>Erreur de chargement</p></div></td></tr>`;
                        });
                }

                document.getElementById('btnCharger').addEventListener('click', loadEmploi);
                document.getElementById('btnReset').addEventListener('click', function() {
                    document.getElementById('anneeSelect').value = '';
                    document.getElementById('periodeSelect').innerHTML = '<option value="">Sélectionner</option>';
                    document.getElementById('classeSelect').value = '';
                    document.getElementById('emploiBody').innerHTML = `<tr><td colspan="5"><div class="empty-state"><i class="fa-regular fa-clock"></i><p>Sélectionnez les filtres et cliquez sur Afficher</p></div></td></tr>`;
                });

                loadFilters();
            </script>
        """.formatted(institutionId));

        sb.append("</body></html>");
        return sb.toString();
    }
}
