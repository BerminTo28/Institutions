import java.time.format.DateTimeFormatter;
import java.util.List;

import com.google.gson.Gson;

public class UIEvenement {

    private static final Gson GSON = new Gson();
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public static String rendrePage(List<Evenement> evenements,
                                    List<Object[]> etudiants,
                                    List<Object[]> professeurs,
                                    String messageErreur,
                                    String messageSucces,
                                    String institutionId,
                                    List<String> anneesDisponibles,
                                    List<String> periodesDisponibles,
                                    List<String> classesDisponibles,
                                    String anneeFiltre,
                                    String periodeFiltre,
                                    String classeFiltre,
                                    boolean canCreate,
                                    boolean canUpdate,
                                    boolean canDelete) {

        if (evenements == null) evenements = List.of();
        if (etudiants == null) etudiants = List.of();
        if (professeurs == null) professeurs = List.of();
        if (messageErreur == null) messageErreur = "";
        if (messageSucces == null) messageSucces = "";
        if (institutionId == null) institutionId = "ADMIN12345";
        if (anneesDisponibles == null) anneesDisponibles = List.of();
        if (periodesDisponibles == null) periodesDisponibles = List.of();
        if (classesDisponibles == null) classesDisponibles = List.of();
        if (anneeFiltre == null) anneeFiltre = "";
        if (periodeFiltre == null) periodeFiltre = "";
        if (classeFiltre == null) classeFiltre = "";

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>");
        sb.append("<html lang=\"fr\">");
        sb.append("<head>");
        sb.append("<meta charset=\"UTF-8\">");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, viewport-fit=cover\">");
        sb.append("<meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge\">");
        sb.append("<title>Gestion des Événements</title>");
        sb.append("<link href=\"https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700;800;900&display=swap\" rel=\"stylesheet\">");
        sb.append("<link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.0/css/all.min.css\">");
        sb.append("<style>");
        sb.append("""
        * { margin: 0; padding: 0; box-sizing: border-box; }
        :root {
            --bg-main: #f1f5f9;
            --primary: #4f46e5;
            --primary-dark: #4338ca;
            --primary-light: #818cf8;
            --card-bg: #ffffff;
            --card-border: #e2e8f0;
            --shadow: 0 10px 25px -5px rgba(15,23,42,0.06), 0 8px 10px -6px rgba(15,23,42,0.04);
            --radius: 12px;
            --safe-top: env(safe-area-inset-top, 0px);
            --safe-bottom: env(safe-area-inset-bottom, 0px);
            --safe-left: env(safe-area-inset-left, 0px);
            --safe-right: env(safe-area-inset-right, 0px);
        }

        /* ============================================================
           RESET + PLEIN ÉCRAN
           ============================================================ */
        *, *::before, *::after { box-sizing: border-box; }
        html, body {
            width: 100%;
            min-width: 320px;
            height: 100vh;
            height: 100dvh;
            min-height: 100vh;
            min-height: 100dvh;
            background: var(--bg-main);
            font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
            margin: 0;
            padding: 0;
            display: flex;
            flex-direction: column;
            overflow: hidden;
            -webkit-text-size-adjust: 100%;
            -webkit-tap-highlight-color: transparent;
            overscroll-behavior: none;
        }

        /* ============================================================
           CONTAINER — occupe tout l'écran
           ============================================================ */
        .container {
            width: 100%;
            max-width: none;
            height: 100vh;
            height: 100dvh;
            margin: 0;
            padding: 8px;
            padding-top: calc(8px + var(--safe-top));
            padding-bottom: calc(8px + var(--safe-bottom));
            padding-left: calc(8px + var(--safe-left));
            padding-right: calc(8px + var(--safe-right));
            background: transparent;
            display: flex;
            flex-direction: column;
            gap: 8px;
            overflow: hidden;
        }

        /* ============================================================
           HEADER
           ============================================================ */
        .header {
            background: var(--card-bg);
            border: 1px solid var(--card-border);
            border-radius: var(--radius);
            box-shadow: var(--shadow);
            padding: 12px 18px;
            display: flex;
            justify-content: space-between;
            align-items: center;
            flex-wrap: wrap;
            gap: 10px;
            flex-shrink: 0;
            min-height: 52px;
        }
        .header-left {
            display: flex;
            flex-direction: column;
            gap: 3px;
        }
        .header h1 {
            font-size: 1.15rem;
            font-weight: 800;
            color: #0f172a;
            display: flex;
            align-items: center;
            gap: 10px;
            line-height: 1.2;
        }
        .header h1 i { color: var(--primary); }
        .header .subtitle {
            color: #64748b;
            font-size: 0.78rem;
        }
        .institution-badge {
            background: #eef2ff;
            color: var(--primary);
            border: 1px solid #c7d2fe;
            padding: 5px 12px;
            border-radius: 30px;
            font-size: 0.75rem;
            font-weight: 700;
            display: inline-flex;
            align-items: center;
            gap: 6px;
            white-space: nowrap;
        }

        /* ============================================================
           ALERTES
           ============================================================ */
        .alert {
            padding: 10px 16px;
            border-radius: 10px;
            display: flex;
            align-items: center;
            gap: 10px;
            font-weight: 500;
            font-size: 0.85rem;
            box-shadow: var(--shadow);
            flex-shrink: 0;
        }
        .alert-error {
            background: #fef2f2;
            color: #991b1b;
            border: 1px solid #fecaca;
        }
        .alert-success {
            background: #f0fdf4;
            color: #166534;
            border: 1px solid #bbf7d0;
        }

        /* ============================================================
           FILTRES
           ============================================================ */
        .filters {
            background: var(--card-bg);
            padding: 10px 14px;
            border-radius: 10px;
            border: 1px solid var(--card-border);
            box-shadow: var(--shadow);
            flex-shrink: 0;
        }
        .filters form {
            display: flex;
            flex-wrap: wrap;
            gap: 10px;
            align-items: flex-end;
        }
        .filters .form-group {
            display: flex;
            flex-direction: column;
            gap: 4px;
            flex: 1 1 160px;
            min-width: 0;
        }
        .filters .form-group label {
            font-size: 0.66rem;
            font-weight: 700;
            color: #475569;
            text-transform: uppercase;
            letter-spacing: 0.5px;
        }
        .filters .form-group select {
            padding: 7px 10px;
            border: 1px solid #cbd5e1;
            border-radius: 8px;
            background: #f8fafc;
            font-size: 0.82rem;
            font-family: inherit;
            color: #1e293b;
            outline: none;
            transition: all 0.2s;
            width: 100%;
            height: 34px;
        }
        .filters .form-group select:focus {
            background: white;
            border-color: var(--primary);
            box-shadow: 0 0 0 3px rgba(79,70,229,0.1);
        }
        .btn-group {
            display: flex;
            gap: 8px;
            align-items: center;
            flex-shrink: 0;
        }

        /* ============================================================
           BOUTONS
           ============================================================ */
        .btn {
            padding: 8px 16px;
            border: none;
            border-radius: 8px;
            font-weight: 600;
            font-size: 0.82rem;
            font-family: inherit;
            cursor: pointer;
            transition: all 0.15s ease;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            gap: 6px;
            text-decoration: none;
            white-space: nowrap;
            height: 34px;
        }
        .btn-primary {
            background: var(--primary);
            color: white;
            box-shadow: 0 4px 12px rgba(79,70,229,0.25);
        }
        .btn-primary:hover {
            background: var(--primary-dark);
            transform: translateY(-1px);
        }
        .btn-success {
            background: #10b981;
            color: white;
            box-shadow: 0 4px 12px rgba(16,185,129,0.25);
        }
        .btn-success:hover {
            background: #059669;
            transform: translateY(-1px);
        }
        .btn-warning { background: #f59e0b; color: white; }
        .btn-warning:hover { background: #d97706; }
        .btn-danger { background: #ef4444; color: white; }
        .btn-danger:hover { background: #dc2626; }
        .btn-secondary {
            background: #64748b;
            color: white;
            border: 1px solid #52606d;
            box-shadow: 0 4px 12px rgba(100,116,139,0.25);
        }
        .btn-secondary:hover {
            background: #475569;
            transform: translateY(-1px);
        }

        /* ============================================================
           CARTE / TABLEAU
           ============================================================ */
        .card {
            background: var(--card-bg);
            border-radius: 10px;
            border: 1px solid var(--card-border);
            box-shadow: var(--shadow);
            overflow: hidden;
            flex: 1;
            min-height: 0;
            display: flex;
            flex-direction: column;
        }
        .table-responsive {
            overflow: auto;
            width: 100%;
            flex: 1;
            min-height: 0;
            -webkit-overflow-scrolling: touch;
        }
        table {
            width: 100%;
            border-collapse: separate;
            border-spacing: 0;
            font-size: 0.82rem;
            text-align: left;
            min-width: 1100px;
        }
        th {
            background: #f8fafc;
            padding: 10px 14px;
            font-weight: 700;
            color: #334155;
            border-bottom: 2px solid #e2e8f0;
            text-transform: uppercase;
            font-size: 0.66rem;
            letter-spacing: 0.8px;
            white-space: nowrap;
            position: sticky;
            top: 0;
            z-index: 2;
        }
        td {
            padding: 10px 14px;
            border-bottom: 1px solid #f1f5f9;
            vertical-align: middle;
            color: #1e293b;
            font-weight: 400;
        }
        tbody tr { transition: background-color 0.15s ease; }
        tbody tr:last-child td { border-bottom: none; }
        tbody tr:hover { background-color: #f8fafc; }

        /* ============================================================
           BADGES
           ============================================================ */
        .badge {
            display: inline-flex;
            align-items: center;
            padding: 4px 10px;
            border-radius: 20px;
            font-size: 0.72rem;
            font-weight: 600;
            letter-spacing: 0.3px;
        }
        .badge-upcoming { background: #eff6ff; color: #1d4ed8; border: 1px solid #dbeafe; }
        .badge-ongoing { background: #fffbeb; color: #b45309; border: 1px solid #fef3c7; }
        .badge-done { background: #ecfdf5; color: #047857; border: 1px solid #d1fae5; }
        .badge-cancelled { background: #fef2f2; color: #b91c1c; border: 1px solid #fee2e2; }
        .participant-tag {
            display: inline-flex;
            align-items: center;
            gap: 5px;
            background: #f1f5f9;
            color: #334155;
            padding: 3px 8px;
            border-radius: 8px;
            font-size: 0.72rem;
            font-weight: 500;
            margin: 2px;
            border: 1px solid #e2e8f0;
        }
        .participant-tag i { color: var(--primary); }
        .participant-tag.prof {
            background: #fffbeb;
            color: #b45309;
            border-color: #fef3c7;
        }
        .participant-tag.prof i { color: #d97706; }
        .empty-state {
            text-align: center;
            padding: 60px 20px;
            color: #94a3b8;
        }
        .empty-state i {
            font-size: 42px;
            margin-bottom: 12px;
            color: #cbd5e1;
            display: block;
        }
        .empty-state p {
            font-size: 0.9rem;
            font-weight: 500;
        }

        /* ============================================================
           MODALE
           ============================================================ */
        .modal {
            display: none;
            position: fixed;
            inset: 0;
            background: rgba(15,23,42,0.6);
            backdrop-filter: blur(6px);
            justify-content: center;
            align-items: center;
            z-index: 1050;
            padding: 12px;
            padding-top: calc(12px + var(--safe-top));
            padding-bottom: calc(12px + var(--safe-bottom));
        }
        .modal-content {
            background: white;
            border-radius: 16px;
            padding: 20px;
            width: 100%;
            max-width: 900px;
            max-height: 92vh;
            overflow-y: auto;
            box-shadow: 0 25px 50px -12px rgba(0,0,0,0.25);
            -webkit-overflow-scrolling: touch;
        }
        .modal-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            border-bottom: 1px solid #e2e8f0;
            padding-bottom: 12px;
            margin-bottom: 16px;
        }
        .modal-header h3 {
            font-size: 1.05rem;
            font-weight: 700;
            color: #0f172a;
            display: flex;
            align-items: center;
            gap: 8px;
        }
        .modal-header h3 i { color: var(--primary); }
        .modal-header .close {
            font-size: 24px;
            cursor: pointer;
            color: #94a3b8;
            transition: 0.2s;
            line-height: 1;
            padding: 0 4px;
        }
        .modal-header .close:hover { color: #0f172a; }
        .form-grid {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 12px;
        }
        .form-grid .full-width { grid-column: 1 / -1; }
        .form-group {
            display: flex;
            flex-direction: column;
            gap: 5px;
            margin-bottom: 10px;
        }
        .form-group label {
            font-size: 0.78rem;
            font-weight: 600;
            color: #334155;
        }
        .form-group input,
        .form-group select,
        .form-group textarea {
            padding: 8px 12px;
            border: 1px solid #cbd5e1;
            border-radius: 8px;
            font-size: 0.85rem;
            font-family: inherit;
            transition: 0.2s;
            background: white;
            color: #1e293b;
        }
        .form-group input:focus,
        .form-group select:focus,
        .form-group textarea:focus {
            outline: none;
            border-color: var(--primary);
            box-shadow: 0 0 0 3px rgba(79,70,229,0.1);
        }
        .form-group textarea {
            resize: vertical;
            min-height: 70px;
        }
        .participants-section {
            margin-top: 16px;
            border-top: 1px solid #e2e8f0;
            padding-top: 12px;
        }
        .participants-section h4 {
            font-size: 0.82rem;
            color: #0f172a;
            margin-bottom: 8px;
            display: flex;
            align-items: center;
            gap: 6px;
        }
        .participants-section h4 i { color: var(--primary); }
        .participants-list {
            display: flex;
            flex-direction: column;
            gap: 3px;
            max-height: 130px;
            overflow-y: auto;
            border: 1px solid #cbd5e1;
            border-radius: 8px;
            padding: 10px;
            background: #f8fafc;
        }
        .participants-list label {
            display: flex;
            align-items: center;
            gap: 8px;
            font-size: 0.78rem;
            color: #334155;
            cursor: pointer;
            padding: 3px 5px;
            border-radius: 5px;
            transition: background 0.1s;
        }
        .participants-list label:hover { background: #e2e8f0; }
        .participants-list input[type="checkbox"] {
            accent-color: var(--primary);
            width: 15px;
            height: 15px;
            flex-shrink: 0;
        }
        .participants-list .hidden { display: none; }
        .modal-footer {
            display: flex;
            justify-content: flex-end;
            gap: 10px;
            margin-top: 20px;
            padding-top: 14px;
            border-top: 1px solid #e2e8f0;
            flex-wrap: wrap;
        }

        /* ============================================================
           RESPONSIVE — 4K
           ============================================================ */
        @media (min-width: 1920px) {
            .container { padding: 12px; gap: 12px; }
            .header { padding: 16px 24px; }
            .header h1 { font-size: 1.35rem; }
            .filters { padding: 14px 18px; }
            th { padding: 12px 16px; font-size: 0.72rem; }
            td { padding: 12px 16px; font-size: 0.88rem; }
            table { min-width: 1400px; }
            .btn { height: 38px; font-size: 0.88rem; padding: 10px 20px; }
        }

        /* ============================================================
           RESPONSIVE — TABLETTE
           ============================================================ */
        @media (max-width: 900px) {
            .filters form { gap: 8px; }
            .filters .form-group { flex: 1 1 130px; }
        }

        /* ============================================================
           RESPONSIVE — MOBILE
           ============================================================ */
        @media (max-width: 768px) {
            html, body { overflow: auto; height: auto; min-height: 100dvh; }

            .container {
                height: auto;
                min-height: 100dvh;
                overflow: visible;
                padding: 6px;
                gap: 6px;
            }

            .header {
                padding: 10px 12px;
                min-height: 48px;
            }
            .header h1 { font-size: 1rem; }
            .header .subtitle { display: none; }
            .institution-badge { font-size: 0.7rem; padding: 4px 10px; }

            .filters { padding: 10px; }
            .filters form { flex-direction: column; align-items: stretch; }
            .filters .form-group { flex: 1 1 auto; }
            .filters .form-group select { width: 100%; }
            .btn-group { width: 100%; justify-content: stretch; }
            .btn-group .btn { flex: 1; }

            .card {
                flex: none;
                max-height: 65vh;
            }
            .table-responsive { max-height: 65vh; }
            table { min-width: 900px; font-size: 0.75rem; }
            th { padding: 8px 8px; font-size: 0.6rem; }
            td { padding: 8px 8px; font-size: 0.75rem; }

            .form-grid { grid-template-columns: 1fr; }

            .modal-content {
                padding: 16px;
                max-height: 94vh;
            }
            .modal-header h3 { font-size: 0.95rem; }

            .participants-list { max-height: 110px; }

            .modal-footer {
                flex-direction: column;
                gap: 8px;
            }
            .modal-footer .btn { width: 100%; }
        }

        /* ============================================================
           RESPONSIVE — TRÈS PETIT MOBILE
           ============================================================ */
        @media (max-width: 400px) {
            .header h1 { font-size: 0.9rem; }
            .header h1 i { font-size: 0.85rem; }
            table { min-width: 800px; font-size: 0.7rem; }
            th { padding: 6px 6px; font-size: 0.56rem; }
            td { padding: 6px 6px; font-size: 0.7rem; }
            .badge { font-size: 0.62rem; padding: 3px 8px; }
            .participant-tag { font-size: 0.65rem; padding: 2px 6px; }
            .btn { font-size: 0.75rem; padding: 6px 12px; height: 32px; }
        }

        /* ============================================================
           RESPONSIVE — PAYSAGE MOBILE
           ============================================================ */
        @media (max-height: 500px) and (orientation: landscape) {
            .container { padding: 4px; gap: 4px; }
            .header { padding: 6px 12px; min-height: 42px; }
            .header h1 { font-size: 0.9rem; }
            .header .subtitle { display: none; }
            .header .institution-badge { display: none; }
            .filters { padding: 6px 10px; }
            .filters .form-group label { display: none; }
            .filters .form-group select { height: 30px; padding: 4px 8px; font-size: 0.75rem; }
            .btn { height: 30px; padding: 4px 12px; font-size: 0.75rem; }
            .card { max-height: 55vh; }
            .table-responsive { max-height: 55vh; }
        }
        """);
        sb.append("</style>");
        sb.append("</head><body>");
        sb.append("<div class=\"container\">");

        // ==================== EN-TÊTE ====================
        sb.append("<div class=\"header\">");
        sb.append("<div class=\"header-left\">");
        sb.append("<h1><i class=\"fas fa-calendar-days\"></i> Gestion des Événements</h1>");
        sb.append("<div class=\"subtitle\">Plateforme centralisée de planification et de suivi</div>");
        sb.append("</div>");
        sb.append("<div style=\"display:flex; align-items:center; gap:12px; flex-wrap:wrap;\">");
        sb.append("<span class=\"institution-badge\"><i class=\"fas fa-building\"></i> ").append(echapperHtml(institutionId)).append("</span>");
        if (canCreate) {
            sb.append("<button class=\"btn btn-success\" onclick=\"ouvrirFormulaireAjout()\"><i class=\"fas fa-plus\"></i> Nouvel Événement</button>");
        } else {
            sb.append("<span style=\"color:#94a3b8; font-size:0.78rem;\"><i class=\"fas fa-lock\"></i> Création restreinte</span>");
        }
        sb.append("</div>");
        sb.append("</div>");

        // Messages
        if (!messageErreur.isEmpty()) {
            sb.append("<div class=\"alert alert-error\"><i class=\"fas fa-circle-exclamation fa-lg\"></i> ").append(echapperHtml(messageErreur)).append("</div>");
        }
        if (!messageSucces.isEmpty()) {
            sb.append("<div class=\"alert alert-success\"><i class=\"fas fa-circle-check fa-lg\"></i> ").append(echapperHtml(messageSucces)).append("</div>");
        }

        // ==================== FILTRES ====================
        sb.append("<div class=\"filters\">");
        sb.append("<form method=\"GET\" action=\"/admin/evenements\">");

        sb.append("<div class=\"form-group\">");
        sb.append("<label for=\"anneeFiltre\">Année</label>");
        sb.append("<select name=\"annee\" id=\"anneeFiltre\">");
        sb.append("<option value=\"\">Toutes les années</option>");
        for (String a : anneesDisponibles) {
            String sel = a.equals(anneeFiltre) ? "selected" : "";
            sb.append("<option value=\"").append(echapperHtml(a)).append("\" ").append(sel).append(">").append(echapperHtml(a)).append("</option>");
        }
        sb.append("</select></div>");

        sb.append("<div class=\"form-group\">");
        sb.append("<label for=\"periodeFiltre\">Période</label>");
        sb.append("<select name=\"periode\" id=\"periodeFiltre\">");
        sb.append("<option value=\"\">Toutes les périodes</option>");
        for (String p : periodesDisponibles) {
            String sel = p.equals(periodeFiltre) ? "selected" : "";
            sb.append("<option value=\"").append(echapperHtml(p)).append("\" ").append(sel).append(">").append(echapperHtml(p)).append("</option>");
        }
        sb.append("</select></div>");

        sb.append("<div class=\"form-group\">");
        sb.append("<label for=\"classeFiltre\">Classe</label>");
        sb.append("<select name=\"classe\" id=\"classeFiltre\">");
        sb.append("<option value=\"\">Toutes les classes</option>");
        for (String c : classesDisponibles) {
            String sel = c.equals(classeFiltre) ? "selected" : "";
            sb.append("<option value=\"").append(echapperHtml(c)).append("\" ").append(sel).append(">").append(echapperHtml(c)).append("</option>");
        }
        sb.append("</select></div>");

        sb.append("<div class=\"btn-group\">");
        sb.append("<button type=\"submit\" class=\"btn btn-primary\"><i class=\"fas fa-filter\"></i> Filtrer</button>");
        sb.append("<a href=\"/admin/evenements\" class=\"btn btn-secondary\" title=\"Réinitialiser\"><i class=\"fas fa-undo\"></i> Réinitialiser</a>");
        sb.append("</div>");

        sb.append("</form>");
        sb.append("</div>");

        // ==================== TABLEAU ====================
        sb.append("<div class=\"card\">");
        sb.append("<div class=\"table-responsive\">");
        sb.append("<form id=\"evenementForm\" method=\"POST\" action=\"/admin/evenements\">");
        sb.append("<input type=\"hidden\" name=\"action\" id=\"actionHidden\">");
        sb.append("<table>");
        sb.append("<thead><tr>");
        sb.append("<th style=\"width:50px;\">#</th>");
        sb.append("<th>Titre</th>");
        sb.append("<th>Début</th>");
        sb.append("<th>Fin</th>");
        sb.append("<th>Lieu</th>");
        sb.append("<th>Session / Période</th>");
        sb.append("<th>Classe</th>");
        sb.append("<th>Participants</th>");
        sb.append("<th>Statut</th>");
        sb.append("<th style=\"width:120px; text-align:center;\">Actions</th>");
        sb.append("</tr></thead><tbody>");

        if (evenements.isEmpty()) {
            sb.append("<tr><td colspan=\"10\" class=\"empty-state\"><i class=\"fas fa-calendar-xmark\"></i><p>Aucun événement trouvé pour les critères sélectionnés.</p></td></tr>");
        } else {
            int idx = 1;
            for (Evenement e : evenements) {
                sb.append("<tr>");
                sb.append("<td style=\"color:#94a3b8; font-weight:600;\">").append(idx++).append("</td>");
                sb.append("<td><strong style=\"color:#0f172a;\">").append(echapperHtml(e.getTitre())).append("</strong></td>");

                String dateDebutStr = e.getDateDebut() != null
                        ? e.getDateDebut().toLocalDateTime().format(DATE_FORMAT)
                        : "";
                String dateFinStr = e.getDateFin() != null
                        ? e.getDateFin().toLocalDateTime().format(DATE_FORMAT)
                        : "";
                sb.append("<td style=\"white-space:nowrap;\">").append(dateDebutStr).append("</td>");
                sb.append("<td style=\"white-space:nowrap;\">").append(dateFinStr).append("</td>");

                sb.append("<td>").append(echapperHtml(e.getLieu())).append("</td>");
                sb.append("<td style=\"white-space:nowrap;\"><span style=\"font-weight:500;\">")
                        .append(echapperHtml(e.getAnneeAcademique()))
                        .append("</span><br><span style=\"font-size:0.72rem; color:#64748b;\">")
                        .append(echapperHtml(e.getPeriode()))
                        .append("</span></td>");

                String classe = e.getClasse();
                if (classe != null && !classe.isEmpty()) {
                    sb.append("<td><span style=\"background:#f1f5f9; padding:3px 8px; border-radius:6px; font-weight:500;\">")
                            .append(echapperHtml(classe))
                            .append("</span></td>");
                } else {
                    sb.append("<td>-</td>");
                }

                sb.append("<td>");
                if (e.getEtudiantsEmails().isEmpty() && e.getProfesseursEmails().isEmpty()) {
                    sb.append("<span style=\"color:#94a3b8; font-style:italic;\">Aucun</span>");
                } else {
                    for (String email : e.getEtudiantsEmails()) {
                        sb.append("<span class=\"participant-tag\"><i class=\"fas fa-user-graduate\"></i> ")
                                .append(echapperHtml(email)).append("</span>");
                    }
                    for (String email : e.getProfesseursEmails()) {
                        sb.append("<span class=\"participant-tag prof\"><i class=\"fas fa-chalkboard-user\"></i> ")
                                .append(echapperHtml(email)).append("</span>");
                    }
                }
                sb.append("</td>");

                sb.append("<td>").append(getBadgeStatut(e.getStatut())).append("</td>");

                sb.append("<td style=\"text-align:center;\">");
                sb.append("<div style=\"display:inline-flex; gap:6px; justify-content:center;\">");

                String evenementId = e.getId() != null ? e.getId() : "";
                if (canUpdate) {
                    sb.append("<button type=\"button\" class=\"btn btn-warning\" ")
                            .append("style=\"padding:5px 9px; font-size:0.72rem; height:auto;\" ")
                            .append("title=\"Modifier\" ")
                            .append("onclick=\"modifierEvenement('").append(evenementId).append("')\">")
                            .append("<i class=\"fas fa-pen\"></i></button>");
                } else {
                    sb.append("<span style=\"color:#94a3b8; font-size:0.72rem;\"><i class=\"fas fa-lock\"></i></span>");
                }
                if (canDelete) {
                    sb.append("<button type=\"button\" class=\"btn btn-danger\" ")
                            .append("style=\"padding:5px 9px; font-size:0.72rem; height:auto;\" ")
                            .append("title=\"Supprimer\" ")
                            .append("onclick=\"supprimerEvenement('").append(evenementId).append("')\">")
                            .append("<i class=\"fas fa-trash\"></i></button>");
                } else {
                    sb.append("<span style=\"color:#94a3b8; font-size:0.72rem;\"><i class=\"fas fa-lock\"></i></span>");
                }
                sb.append("</div>");
                sb.append("</td>");
                sb.append("</tr>");
            }
        }

        sb.append("</tbody></table>");
        sb.append("</form>");
        sb.append("</div>");
        sb.append("</div>");

        // ==================== MODALE ====================
        sb.append(genererModal(etudiants, professeurs, institutionId, anneesDisponibles, periodesDisponibles, classesDisponibles));

        // ==================== JAVASCRIPT ====================
        sb.append("<script>");
        String jsonEvenements = GSON.toJson(evenements).replace("</", "<\\/");
        String jsonEtudiants = GSON.toJson(etudiants).replace("</", "<\\/");
        String jsonProfesseurs = GSON.toJson(professeurs).replace("</", "<\\/");

        sb.append("window.evenementsData = ").append(jsonEvenements).append(";");
        sb.append("window.etudiantsData = ").append(jsonEtudiants).append(";");
        sb.append("window.professeursData = ").append(jsonProfesseurs).append(";");
        sb.append("""
        function ouvrirFormulaireAjout() {
            document.getElementById('modalTitle').innerHTML = '<i class="fas fa-calendar-plus"></i> Ajouter un événement';
            document.getElementById('formAction').value = 'add';
            document.getElementById('evenementId').value = '';
            document.getElementById('titre').value = '';
            document.getElementById('description').value = '';
            document.getElementById('dateDebut').value = '';
            document.getElementById('dateFin').value = '';
            document.getElementById('lieu').value = '';
            document.getElementById('statut').value = 'À venir';
            document.getElementById('anneeSelect').value = '';
            document.getElementById('periodeSelect').value = '';
            document.getElementById('classeSelect').value = '';
            document.querySelectorAll('.participant-checkbox-etudiant').forEach(cb => cb.checked = false);
            document.querySelectorAll('.participant-checkbox-professeur').forEach(cb => cb.checked = false);
            filtrerParticipantsParClasse('');
            document.getElementById('modalForm').style.display = 'flex';
        }

        function modifierEvenement(id) {
            var e = window.evenementsData.find(function(item) { return item.id === id; });
            if (!e) { alert('Événement non trouvé.'); return; }
            document.getElementById('modalTitle').innerHTML = '<i class="fas fa-pen-to-square"></i> Modifier l\\'événement';
            document.getElementById('formAction').value = 'update';
            document.getElementById('evenementId').value = e.id;
            document.getElementById('titre').value = e.titre || '';
            document.getElementById('description').value = e.description || '';
            document.getElementById('dateDebut').value = e.dateDebut ? e.dateDebut.substring(0,16) : '';
            document.getElementById('dateFin').value = e.dateFin ? e.dateFin.substring(0,16) : '';
            document.getElementById('lieu').value = e.lieu || '';
            document.getElementById('statut').value = e.statut || 'À venir';
            document.getElementById('anneeSelect').value = e.anneeAcademique || '';
            document.getElementById('periodeSelect').value = e.periode || '';
            document.getElementById('classeSelect').value = e.classe || '';
            var etudiantsPart = e.etudiantsEmails || [];
            document.querySelectorAll('.participant-checkbox-etudiant').forEach(cb => {
                cb.checked = etudiantsPart.includes(cb.value);
            });
            var professeursPart = e.professeursEmails || [];
            document.querySelectorAll('.participant-checkbox-professeur').forEach(cb => {
                cb.checked = professeursPart.includes(cb.value);
            });
            filtrerParticipantsParClasse(e.classe || '');
            document.getElementById('modalForm').style.display = 'flex';
        }

        function supprimerEvenement(id) {
            if (!confirm('Supprimer définitivement cet événement ?')) return;
            var form = document.getElementById('evenementForm');
            document.getElementById('actionHidden').value = 'delete';
            var hiddenId = document.createElement('input');
            hiddenId.type = 'hidden';
            hiddenId.name = 'id';
            hiddenId.value = id;
            form.appendChild(hiddenId);
            form.submit();
        }

        function fermerModal() {
            document.getElementById('modalForm').style.display = 'none';
        }

        function filtrerParticipantsParClasse(classe) {
            document.querySelectorAll('.participant-checkbox-etudiant').forEach(cb => {
                var cbClasse = cb.getAttribute('data-classe');
                if (!classe || classe === '') {
                    cb.parentElement.classList.remove('hidden');
                } else {
                    cb.parentElement.classList.toggle('hidden', cbClasse !== classe);
                }
            });
            document.querySelectorAll('.participant-checkbox-professeur').forEach(cb => {
                cb.parentElement.classList.remove('hidden');
            });
        }

        document.getElementById('classeSelect').addEventListener('change', function() {
            filtrerParticipantsParClasse(this.value);
        });

        window.onclick = function(event) {
            var modal = document.getElementById('modalForm');
            if (event.target === modal) fermerModal();
        };
        """);
        sb.append("</script>");

        sb.append("</div>");
        sb.append("</body></html>");
        return sb.toString();
    }

    // ==================== MODALE ====================
    private static String genererModal(List<Object[]> etudiants,
                                       List<Object[]> professeurs,
                                       String institutionId,
                                       List<String> annees,
                                       List<String> periodes,
                                       List<String> classes) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div id=\"modalForm\" class=\"modal\">");
        sb.append("<div class=\"modal-content\">");
        sb.append("<div class=\"modal-header\">");
        sb.append("<h3 id=\"modalTitle\"><i class=\"fas fa-calendar-plus\"></i> Ajouter un événement</h3>");
        sb.append("<span class=\"close\" onclick=\"fermerModal()\">&times;</span>");
        sb.append("</div>");
        sb.append("<form method=\"POST\" action=\"/admin/evenements\" id=\"modalFormInner\">");
        sb.append("<input type=\"hidden\" name=\"action\" id=\"formAction\" value=\"add\">");
        sb.append("<input type=\"hidden\" name=\"id\" id=\"evenementId\" value=\"\">");
        sb.append("<input type=\"hidden\" name=\"institutionId\" value=\"").append(echapperHtml(institutionId)).append("\">");
        sb.append("<div class=\"form-group full-width\"><label>Titre de l'événement *</label><input type=\"text\" name=\"titre\" id=\"titre\" required placeholder=\"Ex: Réunion pédagogique\"></div>");
        sb.append("<div class=\"form-group full-width\"><label>Description</label><textarea name=\"description\" id=\"description\" placeholder=\"Détails optionnels...\"></textarea></div>");
        sb.append("<div class=\"form-grid\">");
        sb.append("<div class=\"form-group\"><label>Date de début *</label><input type=\"datetime-local\" name=\"dateDebut\" id=\"dateDebut\" required></div>");
        sb.append("<div class=\"form-group\"><label>Date de fin *</label><input type=\"datetime-local\" name=\"dateFin\" id=\"dateFin\" required></div>");
        sb.append("</div>");
        sb.append("<div class=\"form-grid\">");
        sb.append("<div class=\"form-group\"><label>Lieu</label><input type=\"text\" name=\"lieu\" id=\"lieu\" placeholder=\"Ex: Salle 102 / Amphi B\"></div>");
        sb.append("<div class=\"form-group\"><label>Statut</label><select name=\"statut\" id=\"statut\">");
        sb.append("<option value=\"À venir\">À venir</option>");
        sb.append("<option value=\"En cours\">En cours</option>");
        sb.append("<option value=\"Terminé\">Terminé</option>");
        sb.append("<option value=\"Annulé\">Annulé</option>");
        sb.append("</select></div>");
        sb.append("</div>");
        sb.append("<div class=\"form-grid\">");
        sb.append("<div class=\"form-group\"><label>Année académique</label><select name=\"anneeAcademique\" id=\"anneeSelect\"><option value=\"\">-- Sélectionner --</option>");
        for (String a : annees) {
            sb.append("<option value=\"").append(echapperHtml(a)).append("\">").append(echapperHtml(a)).append("</option>");
        }
        sb.append("</select></div>");
        sb.append("<div class=\"form-group\"><label>Période</label><select name=\"periode\" id=\"periodeSelect\"><option value=\"\">-- Sélectionner --</option>");
        for (String p : periodes) {
            sb.append("<option value=\"").append(echapperHtml(p)).append("\">").append(echapperHtml(p)).append("</option>");
        }
        sb.append("</select></div>");
        sb.append("</div>");
        sb.append("<div class=\"form-group\"><label>Classe concernée</label><select name=\"classe\" id=\"classeSelect\"><option value=\"\">-- Toutes les classes --</option>");
        for (String c : classes) {
            sb.append("<option value=\"").append(echapperHtml(c)).append("\">").append(echapperHtml(c)).append("</option>");
        }
        sb.append("</select></div>");

        // Étudiants
        sb.append("<div class=\"participants-section\">");
        sb.append("<h4><i class=\"fas fa-user-graduate\"></i> Étudiants participants</h4>");
        sb.append("<div class=\"participants-list\">");
        for (Object[] etu : etudiants) {
            String email = (String) etu[3];
            String nom = (String) etu[1];
            String prenom = (String) etu[2];
            String classe = (String) etu[4];
            sb.append("<label><input type=\"checkbox\" name=\"participants_etudiants\" value=\"")
                    .append(echapperHtml(email))
                    .append("\" class=\"participant-checkbox-etudiant\" data-classe=\"")
                    .append(echapperHtml(classe))
                    .append("\"> ");
            sb.append("<span><strong>").append(echapperHtml(nom)).append("</strong> ")
                    .append(echapperHtml(prenom))
                    .append(" <span style=\"color:#64748b;\">(")
                    .append(echapperHtml(email)).append(")</span></span>");
            sb.append("</label>");
        }
        sb.append("</div></div>");

        // Professeurs
        sb.append("<div class=\"participants-section\">");
        sb.append("<h4><i class=\"fas fa-chalkboard-user\"></i> Professeurs participants</h4>");
        sb.append("<div class=\"participants-list\">");
        for (Object[] prof : professeurs) {
            String email = (String) prof[3];
            String nom = (String) prof[1];
            String prenom = (String) prof[2];
            sb.append("<label><input type=\"checkbox\" name=\"participants_professeurs\" value=\"")
                    .append(echapperHtml(email))
                    .append("\" class=\"participant-checkbox-professeur\"> ");
            sb.append("<span><strong>").append(echapperHtml(nom)).append("</strong> ")
                    .append(echapperHtml(prenom))
                    .append(" <span style=\"color:#64748b;\">(")
                    .append(echapperHtml(email)).append(")</span></span>");
            sb.append("</label>");
        }
        sb.append("</div></div>");

        sb.append("<div class=\"modal-footer\">");
        sb.append("<button type=\"button\" class=\"btn btn-secondary\" onclick=\"fermerModal()\">Annuler</button>");
        sb.append("<button type=\"submit\" class=\"btn btn-success\"><i class=\"fas fa-save\"></i> Enregistrer</button>");
        sb.append("</div>");
        sb.append("</form></div></div>");
        return sb.toString();
    }

    private static String getBadgeStatut(String statut) {
        if (statut == null) statut = "À venir";
        String cls = switch (statut) {
            case "Terminé" -> "badge-done";
            case "Annulé" -> "badge-cancelled";
            case "En cours" -> "badge-ongoing";
            default -> "badge-upcoming";
        };
        return "<span class=\"badge " + cls + "\">" + echapperHtml(statut) + "</span>";
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