import com.google.gson.Gson;

public class UIPalmares {

    private static final Gson GSON = new Gson();

    public static String renderPage(String institutionId, boolean canExport) {
        if (institutionId == null) institutionId = "ADMIN12345";

        String institutionIdJson = GSON.toJson(institutionId);
        String institutionIdEscape = echapperJson(institutionId);

        StringBuilder sb = new StringBuilder();

        sb.append("<!DOCTYPE html>");
        sb.append("<html lang=\"fr\">");
        sb.append("<head>");
        sb.append("<meta charset=\"UTF-8\">");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, viewport-fit=cover\">");
        sb.append("<meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge\">");
        sb.append("<title>Palmarès de la classe</title>");
        sb.append("<link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css\">");
        sb.append("<link href=\"https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&display=swap\" rel=\"stylesheet\">");
        sb.append("""
        <style>
            :root {
                --safe-top: env(safe-area-inset-top, 0px);
                --safe-bottom: env(safe-area-inset-bottom, 0px);
                --safe-left: env(safe-area-inset-left, 0px);
                --safe-right: env(safe-area-inset-right, 0px);
            }

            /* ============================================================
               RESET + PLEIN ÉCRAN
               ============================================================ */
            *, *::before, *::after { margin: 0; padding: 0; box-sizing: border-box; }

            html, body {
                width: 100%;
                min-width: 320px;
                height: 100vh;
                height: 100dvh;
                min-height: 100vh;
                min-height: 100dvh;
                font-family: 'Plus Jakarta Sans', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
                background: #f4f6f9;
                display: flex;
                flex-direction: column;
                overflow: hidden;
                -webkit-text-size-adjust: 100%;
                -webkit-tap-highlight-color: transparent;
                overscroll-behavior: none;
            }

            /* ============================================================
               HEADER
               ============================================================ */
            .header {
                background: linear-gradient(135deg, #1e293b, #0f172a);
                padding: 10px 20px;
                padding-top: calc(10px + var(--safe-top));
                padding-left: calc(20px + var(--safe-left));
                padding-right: calc(20px + var(--safe-right));
                display: flex;
                flex-direction: column;
                align-items: center;
                justify-content: center;
                text-align: center;
                box-shadow: 0 4px 20px rgba(0,0,0,0.15);
                flex-shrink: 0;
                z-index: 10;
                gap: 4px;
                max-height: 140px;
            }
            .header-logo {
                max-height: 44px;
                border-radius: 8px;
                display: none;
            }
            .header-title {
                color: white;
                font-size: 18px;
                font-weight: 700;
                display: flex;
                align-items: center;
                justify-content: center;
                gap: 10px;
            }
            .header-title i { color: #f5cd79; }
            .header-devise {
                color: #94a3b8;
                font-size: 12px;
                font-style: italic;
            }
            .header-admin {
                color: #64748b;
                font-size: 11px;
                display: flex;
                align-items: center;
                gap: 6px;
            }
            .header-admin i { color: #3b82f6; }

            /* ============================================================
               CONTAINER — occupe tout l'espace restant
               ============================================================ */
            .container {
                flex: 1;
                min-height: 0;
                max-width: none;
                margin: 0;
                padding: 8px;
                padding-left: calc(8px + var(--safe-left));
                padding-right: calc(8px + var(--safe-right));
                padding-bottom: calc(8px + var(--safe-bottom));
                width: 100%;
                display: flex;
                flex-direction: column;
                gap: 8px;
                overflow: hidden;
            }

            /* ============================================================
               FILTRES
               ============================================================ */
            .filters-card {
                background: white;
                border-radius: 12px;
                padding: 10px 14px;
                border: 1px solid #e2e8f0;
                display: flex;
                flex-wrap: wrap;
                align-items: flex-end;
                gap: 12px;
                box-shadow: 0 4px 15px rgba(0,0,0,0.03);
                flex-shrink: 0;
            }
            .filter-group {
                display: flex;
                flex-direction: column;
                gap: 4px;
                flex: 1 1 180px;
                min-width: 0;
            }
            .filter-group label {
                font-size: 11px;
                font-weight: 600;
                color: #334155;
                text-transform: uppercase;
                letter-spacing: 0.3px;
            }
            .filter-group select {
                padding: 8px 12px;
                border: 1px solid #cbd5e1;
                border-radius: 8px;
                font-size: 13px;
                background: white;
                color: #0f172a;
                cursor: pointer;
                font-family: inherit;
                height: 38px;
            }
            .filter-group select:focus {
                outline: none;
                border-color: #1e40af;
                box-shadow: 0 0 0 3px rgba(30,64,175,0.1);
            }

            /* ============================================================
               BOUTONS
               ============================================================ */
            .btn {
                padding: 8px 18px;
                border: none;
                border-radius: 8px;
                font-weight: 600;
                cursor: pointer;
                transition: all 0.15s;
                font-size: 12.5px;
                display: inline-flex;
                align-items: center;
                justify-content: center;
                gap: 6px;
                height: 38px;
                font-family: inherit;
                white-space: nowrap;
            }
            .btn-primary {
                background: linear-gradient(135deg, #1e40af, #3b82f6);
                color: white;
                box-shadow: 0 4px 12px rgba(59,130,246,0.25);
            }
            .btn-primary:hover { opacity: 0.9; transform: translateY(-1px); }
            .btn-success {
                background: #16a34a;
                color: white;
                box-shadow: 0 4px 12px rgba(22,163,74,0.25);
            }
            .btn-success:hover { background: #15803d; transform: translateY(-1px); }
            .btn-danger {
                background: #dc2626;
                color: white;
                box-shadow: 0 4px 12px rgba(220,38,38,0.25);
            }
            .btn-danger:hover { background: #b91c1c; transform: translateY(-1px); }
            .btn-disabled { opacity: 0.5; cursor: not-allowed; pointer-events: none; }

            /* ============================================================
               TOOLBAR
               ============================================================ */
            .table-toolbar {
                display: flex;
                justify-content: space-between;
                align-items: center;
                flex-wrap: wrap;
                gap: 8px;
                flex-shrink: 0;
            }
            .table-toolbar .left {
                display: flex;
                gap: 8px;
                align-items: center;
                font-weight: 700;
                color: #1e293b;
                font-size: 14px;
            }
            .table-toolbar .left i { color: #f59e0b; }
            .table-toolbar .right {
                display: flex;
                gap: 8px;
                align-items: center;
                flex-wrap: wrap;
            }
            .export-restricted {
                color: #94a3b8;
                font-size: 12px;
                padding: 6px 12px;
                background: #f1f5f9;
                border-radius: 6px;
                display: inline-flex;
                align-items: center;
                gap: 6px;
            }

            /* ============================================================
               TABLEAU — occupe l'espace restant
               ============================================================ */
            .table-container {
                flex: 1;
                min-height: 0;
                overflow: auto;
                border: 1px solid #e2e8f0;
                border-radius: 12px;
                background: white;
                box-shadow: 0 4px 15px rgba(0,0,0,0.03);
                -webkit-overflow-scrolling: touch;
            }
            table {
                width: 100%;
                border-collapse: collapse;
                font-size: 13px;
                white-space: nowrap;
            }
            th {
                background: #1e293b;
                color: white;
                padding: 10px 12px;
                text-align: center;
                font-weight: 600;
                position: sticky;
                top: 0;
                z-index: 2;
                font-size: 12px;
            }
            td {
                padding: 10px 12px;
                border-bottom: 1px solid #e2e8f0;
                text-align: center;
                color: #0f172a;
                font-size: 13px;
            }
            tbody tr:hover { background: #f8fafc; }
            tbody tr:last-child td { border-bottom: none; }

            .note-null { color: #94a3b8; font-style: italic; }
            .empty-state {
                text-align: center;
                padding: 60px 20px;
                color: #94a3b8;
                display: flex;
                flex-direction: column;
                align-items: center;
                gap: 12px;
            }
            .empty-state i { font-size: 42px; color: #cbd5e1; }
            .empty-state p { font-size: 14px; font-weight: 500; }
            .badge-mention {
                background: #e0f2fe;
                color: #0369a1;
                padding: 3px 10px;
                border-radius: 20px;
                font-size: 11px;
                font-weight: 700;
                display: inline-block;
            }

            /* ============================================================
               TOAST
               ============================================================ */
            .toast {
                position: fixed;
                bottom: calc(20px + var(--safe-bottom));
                right: calc(20px + var(--safe-right));
                background: #1e293b;
                color: white;
                padding: 12px 20px;
                border-radius: 12px;
                z-index: 1100;
                animation: fadeInOut 3s ease;
                box-shadow: 0 4px 12px rgba(0,0,0,0.2);
                font-size: 13.5px;
                max-width: calc(100vw - 40px);
            }
            .toast.error { background: #dc2626; }
            @keyframes fadeInOut {
                0%   { opacity: 0; transform: translateY(20px); }
                15%  { opacity: 1; transform: translateY(0); }
                85%  { opacity: 1; }
                100% { opacity: 0; transform: translateY(20px); }
            }

            /* ============================================================
               RESPONSIVE — 4K / ULTRA-WIDE
               ============================================================ */
            @media (min-width: 1920px) {
                .header { padding: 14px 24px; }
                .header-title { font-size: 22px; }
                .header-logo { max-height: 56px; }
                .container { padding: 12px; gap: 12px; }
                .filters-card { padding: 14px 18px; }
                .filter-group label { font-size: 12px; }
                .filter-group select { height: 42px; font-size: 14px; }
                .btn { height: 42px; font-size: 13.5px; }
                table { font-size: 14px; }
                th { padding: 12px 14px; font-size: 13px; }
                td { padding: 12px 14px; font-size: 14px; }
            }

            /* ============================================================
               RESPONSIVE — MOBILE
               ============================================================ */
            @media (max-width: 768px) {
                html, body { overflow: auto; height: auto; min-height: 100dvh; }
                .header {
                    padding: 8px 14px;
                    max-height: none;
                }
                .header-title { font-size: 15px; }
                .header-devise { font-size: 11px; }
                .header-admin { font-size: 10px; }

                .container {
                    padding: 6px;
                    gap: 6px;
                    height: auto;
                    overflow: visible;
                }

                .filters-card {
                    flex-direction: column;
                    align-items: stretch;
                    padding: 10px;
                    gap: 8px;
                }
                .filter-group { flex: 1 1 auto; }
                .filter-group select { width: 100%; }
                .filters-card > div[style*="flex: 0"] {
                    flex: 1 1 auto !important;
                    min-width: 0 !important;
                }
                .filters-card #btnCharger { width: 100% !important; }

                .table-toolbar { flex-direction: column; align-items: stretch; gap: 6px; }
                .table-toolbar .right { justify-content: stretch; }
                .table-toolbar .right .btn {
                    flex: 1;
                    justify-content: center;
                    padding: 8px 12px;
                }
                .export-restricted {
                    justify-content: center;
                    text-align: center;
                }

                .table-container {
                    flex: none;
                    max-height: 65vh;
                    border-radius: 10px;
                }
                table { font-size: 11.5px; }
                th { padding: 8px 8px; font-size: 10.5px; }
                td { padding: 8px 8px; font-size: 11.5px; }
                .badge-mention { font-size: 10px; padding: 2px 8px; }
                .toast {
                    bottom: calc(12px + var(--safe-bottom));
                    right: calc(12px + var(--safe-right));
                    left: calc(12px + var(--safe-left));
                    max-width: none;
                }
            }

            /* ============================================================
               RESPONSIVE — TRÈS PETIT MOBILE
               ============================================================ */
            @media (max-width: 400px) {
                .header-title { font-size: 13.5px; }
                .header-title i { font-size: 12px; }
                .header-admin { display: none; }
                table { font-size: 11px; }
                th { padding: 6px 6px; font-size: 10px; }
                td { padding: 6px 6px; font-size: 11px; }
                .badge-mention { font-size: 9.5px; padding: 2px 6px; }
                .table-toolbar .left { font-size: 12.5px; }
            }

            /* ============================================================
               RESPONSIVE — PAYSAGE MOBILE
               ============================================================ */
            @media (max-height: 500px) and (orientation: landscape) {
                .header {
                    padding: 4px 12px;
                    max-height: 60px;
                }
                .header-logo { display: none !important; }
                .header-title { font-size: 13px; }
                .header-devise, .header-admin { display: none; }
                .container { padding: 4px; gap: 4px; }
                .filters-card { padding: 6px 10px; gap: 6px; }
                .filter-group label { display: none; }
                .filter-group select { height: 32px; padding: 4px 8px; font-size: 12px; }
                .btn { height: 32px; padding: 4px 12px; font-size: 11.5px; }
                .table-toolbar .left { font-size: 12px; }
                .table-container { max-height: 55vh; }
            }
        </style>
        """);
        sb.append("</head><body>");

        // EN-TÊTE
        sb.append("    <header class=\"header\">\n");
        sb.append("        <img src=\"\" alt=\"Logo\" class=\"header-logo\" id=\"logoInstitution\">\n");
        sb.append("        <div class=\"header-title\" id=\"nomInstitution\"><i class=\"fas fa-trophy\"></i> Palmarès de la classe</div>\n");
        sb.append("        <div class=\"header-devise\" id=\"deviseInstitution\"></div>\n");
        sb.append("        <div class=\"header-admin\">\n");
        sb.append("            <i class=\"fas fa-user-circle\"></i> <span>").append(echapperHtml(institutionId)).append("</span>\n");
        sb.append("        </div>\n");
        sb.append("    </header>\n");

        sb.append("<div class=\"container\">");

        // Filtres
        sb.append("<div class=\"filters-card\">");
        sb.append("<div class=\"filter-group\"><label><i class=\"fas fa-calendar\"></i> Année académique</label>");
        sb.append("<select id=\"anneeSelect\"><option value=\"\">Chargement...</option></select></div>");

        sb.append("<div class=\"filter-group\"><label><i class=\"fas fa-clock\"></i> Session / Période</label>");
        sb.append("<select id=\"sessionSelect\"><option value=\"\">Sélectionner une année d'abord</option></select></div>");

        sb.append("<div class=\"filter-group\"><label><i class=\"fas fa-school\"></i> Classe</label>");
        sb.append("<select id=\"classeSelect\"><option value=\"\">Chargement...</option></select></div>");

        sb.append("<div class=\"filter-group\" style=\"flex: 0 0 auto; min-width: 150px;\">");
        sb.append("<label>&nbsp;</label>");
        sb.append("<button class=\"btn btn-primary\" id=\"btnCharger\"><i class=\"fas fa-search\"></i> CONSULTER</button>");
        sb.append("</div></div>");

        // Table toolbar
        sb.append("<div class=\"table-toolbar\">");
        sb.append("<div class=\"left\"><span><i class=\"fas fa-list-ol\"></i> Classement des étudiants</span></div>");
        sb.append("<div class=\"right\">");
        if (canExport) {
            sb.append("<button class=\"btn btn-danger\" id=\"btnExportPDF\"><i class=\"fas fa-file-pdf\"></i> EXPORTER PDF</button>");
            sb.append("<button class=\"btn btn-success\" id=\"btnExportExcel\"><i class=\"fas fa-file-excel\"></i> EXPORTER EXCEL</button>");
        } else {
            sb.append("<span class=\"export-restricted\">");
            sb.append("<i class=\"fas fa-lock\"></i> Export restreint (droits insuffisants)");
            sb.append("</span>");
        }
        sb.append("</div></div>");

        // Table
        sb.append("<div class=\"table-container\">");
        sb.append("<table id=\"palmaresTable\">");
        sb.append("<thead id=\"tableHead\">");
        sb.append("<tr><th>Rang</th><th>Matricule</th><th>Nom</th><th>Prénom</th><th>Moyenne</th><th>Mention</th></tr>");
        sb.append("</thead>");
        sb.append("<tbody id=\"palmaresBody\">");
        sb.append("<tr><td colspan=\"20\"><div class=\"empty-state\"><i class=\"fas fa-clipboard-list\"></i><p>Sélectionnez les filtres et cliquez sur Consulter pour afficher le palmarès.</p></div></td></tr>");
        sb.append("</tbody></table>");
        sb.append("</div>");

        sb.append("</div>");

        // ========== JAVASCRIPT ==========
        sb.append("<script>\n");
        sb.append("// === DONNÉES INJECTÉES ===\n");
        sb.append("const institutionId = ").append(institutionIdJson).append(";\n");
        sb.append("const institutionIdEscaped = '").append(institutionIdEscape).append("';\n");
        sb.append("const canExport = ").append(canExport).append(";\n");
        sb.append("console.log('📋 Institution ID:', institutionId);\n");
        sb.append("console.log('📋 canExport:', canExport);\n");

        sb.append("""
        function escapeHtml(str) { if (!str) return ''; return String(str).replace(/[&<>]/g, m => m === '&' ? '&amp;' : m === '<' ? '&lt;' : '&gt;'); }

        function showToast(msg, type = '') {
            const t = document.createElement('div');
            t.className = 'toast ' + type;
            t.textContent = msg;
            document.body.appendChild(t);
            setTimeout(() => t.remove(), 3000);
        }

        function loadFilters() {
            fetch('/admin/palmares/api/annees')
                .then(res => res.json())
                .then(data => {
                    const select = document.getElementById('anneeSelect');
                    select.innerHTML = '<option value="">-- Sélectionner --</option>';
                    if (data.success && data.data) {
                        data.data.forEach(a => {
                            const opt = document.createElement('option');
                            opt.value = a; opt.textContent = a;
                            select.appendChild(opt);
                        });
                    }
                }).catch(() => {});

            fetch('/admin/palmares/api/classes')
                .then(res => res.json())
                .then(data => {
                    const select = document.getElementById('classeSelect');
                    select.innerHTML = '<option value="">-- Sélectionner --</option>';
                    if (data.success && data.data) {
                        data.data.forEach(c => {
                            const opt = document.createElement('option');
                            opt.value = c; opt.textContent = c;
                            select.appendChild(opt);
                        });
                    }
                }).catch(() => {});
        }

        document.getElementById('anneeSelect').addEventListener('change', function() {
            const annee = this.value;
            const select = document.getElementById('sessionSelect');
            select.innerHTML = '<option value="">Chargement...</option>';

            if (annee) {
                fetch('/admin/palmares/api/sessions?annee=' + encodeURIComponent(annee))
                    .then(res => res.json())
                    .then(data => {
                        select.innerHTML = '<option value="">-- Sélectionner --</option>';
                        if (data.success && data.data) {
                            data.data.forEach(s => {
                                const opt = document.createElement('option');
                                opt.value = s; opt.textContent = s;
                                select.appendChild(opt);
                            });
                        }
                    }).catch(() => select.innerHTML = '<option value="">Erreur</option>');
            } else {
                select.innerHTML = '<option value="">Sélectionner une année d\\'abord</option>';
            }
        });

        function loadPalmares() {
            const annee = document.getElementById('anneeSelect').value;
            const session = document.getElementById('sessionSelect').value;
            const classe = document.getElementById('classeSelect').value;

            if (!annee || !session || !classe) {
                showToast('Veuillez sélectionner une année, une session et une classe.', 'error');
                return;
            }

            const tbody = document.getElementById('palmaresBody');
            tbody.innerHTML = '<tr><td colspan="20"><div class="empty-state"><i class="fas fa-spinner fa-spin"></i><p>Génération du palmarès en cours...</p></div></td></tr>';

            const params = new URLSearchParams({ annee, session, classe });

            fetch('/admin/palmares/api/data?' + params)
                .then(res => res.json())
                .then(data => {
                    if (data.success && data.data && data.data.length > 0) {
                        buildTable(data.data);
                    } else {
                        tbody.innerHTML = '<tr><td colspan="20"><div class="empty-state"><i class="fas fa-folder-open"></i><p>Aucun étudiant trouvé pour ces critères.</p></div></td></tr>';
                        document.getElementById('tableHead').innerHTML = '<tr><th>Rang</th><th>Matricule</th><th>Nom</th><th>Prénom</th><th>Moyenne</th><th>Mention</th></tr>';
                    }
                }).catch(err => {
                    tbody.innerHTML = '<tr><td colspan="20"><div class="empty-state" style="color:#dc2626;"><i class="fas fa-exclamation-triangle"></i><p>Erreur lors du chargement des données.</p></div></td></tr>';
                });
        }

        function buildTable(entries) {
            const matiereSet = new Set();
            entries.forEach(e => {
                if(e.matieres) e.matieres.forEach(m => matiereSet.add(m.nom));
            });
            const matieres = Array.from(matiereSet);

            let headerRow = '<tr><th>Rang</th><th>Matricule</th><th>Nom</th><th>Prénom</th>';
            matieres.forEach(m => {
                headerRow += '<th>' + escapeHtml(m) + '</th>';
            });
            headerRow += '<th>Moyenne</th><th>Mention</th></tr>';
            document.getElementById('tableHead').innerHTML = headerRow;

            let tbody = document.getElementById('palmaresBody');
            let rows = '';

            entries.forEach(e => {
                let noteMap = {};
                if(e.matieres) {
                    e.matieres.forEach(m => { noteMap[m.nom] = m.note; });
                }

                rows += '<tr>';
                let rangStyle = '';
                if(e.rang === 1) rangStyle = 'font-weight:900; color:#f59e0b; font-size:15px;';
                else if(e.rang === 2) rangStyle = 'font-weight:800; color:#94a3b8; font-size:14px;';
                else if(e.rang === 3) rangStyle = 'font-weight:800; color:#b45309; font-size:14px;';
                else rangStyle = 'font-weight:600;';

                rows += `<td style="${rangStyle}">${e.rang}</td>`;
                rows += '<td>' + escapeHtml(e.matricule || '-') + '</td>';
                rows += '<td><strong>' + escapeHtml(e.nom) + '</strong></td>';
                rows += '<td>' + escapeHtml(e.prenom) + '</td>';

                matieres.forEach(m => {
                    let note = noteMap[m];
                    if (note !== undefined && note >= 0) {
                        rows += '<td>' + note.toFixed(2) + '</td>';
                    } else {
                        rows += '<td class="note-null">-</td>';
                    }
                });

                rows += '<td><strong>' + (e.moyenne ? e.moyenne.toFixed(2) : '-') + '</strong></td>';
                rows += `<td><span class="badge-mention">${escapeHtml(e.mention || '-')}</span></td>`;
                rows += '</tr>';
            });
            tbody.innerHTML = rows;
        }

        function exportPDF() {
            if (!canExport) { showToast('Vous n\\'avez pas la permission d\\'exporter.', 'error'); return; }
            const annee = document.getElementById('anneeSelect').value;
            const session = document.getElementById('sessionSelect').value;
            const classe = document.getElementById('classeSelect').value;
            if (!annee || !session || !classe) { showToast('Sélectionnez les filtres avant d\\'exporter', 'error'); return; }
            window.open('/admin/palmares/api/export/pdf?annee=' + encodeURIComponent(annee) + '&session=' + encodeURIComponent(session) + '&classe=' + encodeURIComponent(classe), '_blank');
        }

        function exportExcel() {
            if (!canExport) { showToast('Vous n\\'avez pas la permission d\\'exporter.', 'error'); return; }
            const annee = document.getElementById('anneeSelect').value;
            const session = document.getElementById('sessionSelect').value;
            const classe = document.getElementById('classeSelect').value;
            if (!annee || !session || !classe) { showToast('Sélectionnez les filtres avant d\\'exporter', 'error'); return; }
            window.open('/admin/palmares/api/export/excel?annee=' + encodeURIComponent(annee) + '&session=' + encodeURIComponent(session) + '&classe=' + encodeURIComponent(classe), '_blank');
        }

        document.getElementById('btnCharger').addEventListener('click', loadPalmares);

        // ✅ Sécurisation : n'attacher les boutons d'export QUE s'ils existent
        const btnPDF = document.getElementById('btnExportPDF');
        const btnExcel = document.getElementById('btnExportExcel');
        if (btnPDF) btnPDF.addEventListener('click', exportPDF);
        if (btnExcel) btnExcel.addEventListener('click', exportExcel);

        loadFilters();

        fetch('/admin/parametres/api/institution')
            .then(res => res.json())
            .then(data => {
                if (data.success) {
                    if (data.nom) {
                        document.getElementById('nomInstitution').innerHTML = '<i class="fas fa-trophy"></i> Palmarès - ' + escapeHtml(data.nom);
                    }
                    if (data.devise) {
                        document.getElementById('deviseInstitution').textContent = data.devise;
                    }
                    if (data.logo) {
                        const logoEl = document.getElementById('logoInstitution');
                        logoEl.src = data.logo;
                        logoEl.style.display = 'block';
                    }
                }
            }).catch(e => console.log('Info institution non disponible'));
        """);

        sb.append("</script>");
        sb.append("</body></html>");

        return sb.toString();
    }

    private static String echapperHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }

    private static String echapperJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }
}