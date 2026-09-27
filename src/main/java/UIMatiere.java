import java.util.List;

public class UIMatiere {

    // ============================================================
    // POINT D'ENTRÉE
    // ============================================================
    public static String rendrePage(
            List<Matiere> matieres,
            String queryRecherche,
            String anneeFiltre,
            String periodeFiltre,
            String classeFiltre,
            List<String> anneesDisponibles,
            List<String> periodesDisponibles,
            List<String> classesDisponibles,
            String institutionId,
            String successMessage,
            String errorMessage,
            boolean canCreate,
            boolean canModify,
            boolean canDelete
    ) {
        if (matieres == null) matieres = List.of();
        if (anneesDisponibles == null) anneesDisponibles = List.of();
        if (periodesDisponibles == null) periodesDisponibles = List.of();
        if (classesDisponibles == null) classesDisponibles = List.of();
        if (queryRecherche == null) queryRecherche = "";
        if (anneeFiltre == null) anneeFiltre = "";
        if (periodeFiltre == null) periodeFiltre = "";
        if (classeFiltre == null) classeFiltre = "";
        if (institutionId == null) institutionId = "";
        if (successMessage == null) successMessage = "";
        if (errorMessage == null) errorMessage = "";

        StringBuilder html = new StringBuilder(32_000);

        appendHead(html);
        appendStyles(html);
        html.append("<body>\n")
            .append("<div class=\"main-wrapper\">\n");

        appendHeader(html, institutionId);
        appendMessages(html, successMessage, errorMessage);
        appendFiltres(html, queryRecherche, anneeFiltre, periodeFiltre, classeFiltre,
                anneesDisponibles, periodesDisponibles, classesDisponibles,
                institutionId);
        appendTableau(html, matieres, canModify, canDelete, institutionId);
        appendActionBar(html, canCreate);
        appendModal(html, anneesDisponibles, periodesDisponibles, classesDisponibles, institutionId);

        html.append("</div>\n"); // .main-wrapper

        appendScripts(html, canModify, canDelete, institutionId);
        html.append("</body>\n</html>\n");

        return html.toString();
    }

    // ============================================================
    // HEAD
    // ============================================================
    private static void appendHead(StringBuilder html) {
        html.append("<!DOCTYPE html>\n")
            .append("<html lang=\"fr\" class=\"h-100\">\n")
            .append("<head>\n")
            .append("    <meta charset=\"UTF-8\">\n")
            .append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n")
            .append("    <title>Gestion des Matières</title>\n")
            // ✅ Bootstrap JS chargé EN PREMIER
            .append("    <script src=\"https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js\"></script>\n")
            .append("    <link href=\"https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap\" rel=\"stylesheet\">\n")
            .append("    <link href=\"https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css\" rel=\"stylesheet\">\n")
            .append("    <link href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css\" rel=\"stylesheet\">\n");
    }

    // ============================================================
    // STYLES
    // ============================================================
       // ============================================================
    // STYLES — plein écran
    // ============================================================
    private static void appendStyles(StringBuilder html) {
        html.append("    <style>\n")
            .append("        :root {\n")
            .append("            --primary-color: #4f46e5;\n")
            .append("            --primary-hover: #4338ca;\n")
            .append("            --bg-color: #f1f5f9;\n")
            .append("            --card-shadow: 0 4px 12px -4px rgba(0, 0, 0, 0.06);\n")
            .append("            --safe-top: env(safe-area-inset-top, 0px);\n")
            .append("            --safe-bottom: env(safe-area-inset-bottom, 0px);\n")
            .append("            --safe-left: env(safe-area-inset-left, 0px);\n")
            .append("            --safe-right: env(safe-area-inset-right, 0px);\n")
            .append("        }\n")

            // ---------- RESET + PLEIN ÉCRAN ----------
            .append("        *, *::before, *::after { box-sizing: border-box; }\n")
            .append("        html, body {\n")
            .append("            width: 100%;\n")
            .append("            min-width: 320px;\n")
            .append("            height: 100vh;\n")
            .append("            height: 100dvh;\n")
            .append("            min-height: 100vh;\n")
            .append("            min-height: 100dvh;\n")
            .append("            margin: 0;\n")
            .append("            padding: 0;\n")
            .append("            overflow: hidden;\n")
            .append("            font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;\n")
            .append("            background-color: var(--bg-color);\n")
            .append("            color: #1e293b;\n")
            .append("            -webkit-text-size-adjust: 100%;\n")
            .append("            -webkit-tap-highlight-color: transparent;\n")
            .append("            overscroll-behavior: none;\n")
            .append("        }\n")

            // ---------- MAIN WRAPPER — occupe tout l'écran ----------
            .append("        .main-wrapper {\n")
            .append("            width: 100%;\n")
            .append("            max-width: none;\n")
            .append("            height: 100vh;\n")
            .append("            height: 100dvh;\n")
            .append("            display: flex;\n")
            .append("            flex-direction: column;\n")
            .append("            padding: 8px;\n")
            .append("            padding-top: calc(8px + var(--safe-top));\n")
            .append("            padding-bottom: calc(8px + var(--safe-bottom));\n")
            .append("            padding-left: calc(8px + var(--safe-left));\n")
            .append("            padding-right: calc(8px + var(--safe-right));\n")
            .append("            margin: 0;\n")
            .append("            gap: 8px;\n")
            .append("        }\n")

            // ---------- HEADER ----------
            .append("        .glass-header {\n")
            .append("            background: linear-gradient(135deg, #4f46e5 0%, #3b82f6 100%);\n")
            .append("            color: white;\n")
            .append("            border-radius: 12px;\n")
            .append("            padding: 10px 18px;\n")
            .append("            box-shadow: 0 8px 20px -8px rgba(79,70,229,0.35);\n")
            .append("            flex-shrink: 0;\n")
            .append("            display: flex;\n")
            .append("            justify-content: space-between;\n")
            .append("            align-items: center;\n")
            .append("            gap: 12px;\n")
            .append("            min-height: 52px;\n")
            .append("        }\n")
            .append("        .glass-header h1 {\n")
            .append("            font-size: 1.15rem;\n")
            .append("            font-weight: 700;\n")
            .append("            margin: 0;\n")
            .append("            display: flex;\n")
            .append("            align-items: center;\n")
            .append("            gap: 0.5rem;\n")
            .append("        }\n")
            .append("        .glass-header .subtitle {\n")
            .append("            opacity: 0.85;\n")
            .append("            font-size: 0.78rem;\n")
            .append("            margin: 0;\n")
            .append("        }\n")
            .append("        .btn-glass {\n")
            .append("            background: rgba(255,255,255,0.15);\n")
            .append("            border: 1px solid rgba(255,255,255,0.25);\n")
            .append("            color: white;\n")
            .append("            border-radius: 50px;\n")
            .append("            padding: 0.35rem 0.9rem;\n")
            .append("            font-weight: 500;\n")
            .append("            text-decoration: none;\n")
            .append("            transition: 0.15s;\n")
            .append("            font-size: 0.82rem;\n")
            .append("            white-space: nowrap;\n")
            .append("            display: inline-flex;\n")
            .append("            align-items: center;\n")
            .append("        }\n")
            .append("        .btn-glass:hover { background: rgba(255,255,255,0.25); color: white; }\n")

            // ---------- FILTRES ----------
            .append("        .filters-bar {\n")
            .append("            background: #ffffff;\n")
            .append("            border-radius: 10px;\n")
            .append("            padding: 8px 12px;\n")
            .append("            box-shadow: var(--card-shadow);\n")
            .append("            border: 1px solid #e2e8f0;\n")
            .append("            flex-shrink: 0;\n")
            .append("        }\n")
            .append("        .filters-bar .form-control,\n")
            .append("        .filters-bar .form-select {\n")
            .append("            border-radius: 8px;\n")
            .append("            border: 1px solid #e2e8f0;\n")
            .append("            padding: 0.35rem 0.65rem;\n")
            .append("            font-size: 0.82rem;\n")
            .append("            background: #f8fafc;\n")
            .append("            height: 34px;\n")
            .append("            transition: 0.15s;\n")
            .append("        }\n")
            .append("        .filters-bar .form-control:focus,\n")
            .append("        .filters-bar .form-select:focus {\n")
            .append("            border-color: var(--primary-color);\n")
            .append("            box-shadow: 0 0 0 3px rgba(79,70,229,0.12);\n")
            .append("            background: white;\n")
            .append("        }\n")
            .append("        .filters-bar .input-group-text {\n")
            .append("            background: #f8fafc;\n")
            .append("            border-color: #e2e8f0;\n")
            .append("            height: 34px;\n")
            .append("            padding: 0 0.55rem;\n")
            .append("            font-size: 0.82rem;\n")
            .append("        }\n")
            .append("        .btn-primary-sm {\n")
            .append("            background: var(--primary-color);\n")
            .append("            border: none;\n")
            .append("            border-radius: 8px;\n")
            .append("            padding: 0.35rem 0.9rem;\n")
            .append("            font-weight: 600;\n")
            .append("            font-size: 0.82rem;\n")
            .append("            color: white;\n")
            .append("            height: 34px;\n")
            .append("            transition: 0.15s;\n")
            .append("            white-space: nowrap;\n")
            .append("            display: inline-flex;\n")
            .append("            align-items: center;\n")
            .append("            gap: 6px;\n")
            .append("        }\n")
            .append("        .btn-primary-sm:hover { background: var(--primary-hover); }\n")
            .append("        .btn-outline-sm {\n")
            .append("            border: 1px solid #e2e8f0;\n")
            .append("            border-radius: 8px;\n")
            .append("            background: white;\n")
            .append("            color: #64748b;\n")
            .append("            height: 34px;\n")
            .append("            padding: 0 0.65rem;\n")
            .append("            display: inline-flex;\n")
            .append("            align-items: center;\n")
            .append("            justify-content: center;\n")
            .append("            transition: 0.15s;\n")
            .append("        }\n")
            .append("        .btn-outline-sm:hover { background: #f1f5f9; color: var(--primary-color); }\n")

            // ---------- TABLEAU ----------
            .append("        .table-container {\n")
            .append("            flex: 1;\n")
            .append("            min-height: 0;\n")
            .append("            background: #ffffff;\n")
            .append("            border-radius: 10px;\n")
            .append("            box-shadow: var(--card-shadow);\n")
            .append("            border: 1px solid #e2e8f0;\n")
            .append("            display: flex;\n")
            .append("            flex-direction: column;\n")
            .append("            overflow: hidden;\n")
            .append("        }\n")
            .append("        .table-scroll { flex: 1; overflow: auto; }\n")
            .append("        .table-custom {\n")
            .append("            margin-bottom: 0;\n")
            .append("            min-width: 1000px;\n")
            .append("            width: 100%;\n")
            .append("            border-collapse: collapse;\n")
            .append("        }\n")
            .append("        .table-custom thead {\n")
            .append("            position: sticky;\n")
            .append("            top: 0;\n")
            .append("            z-index: 10;\n")
            .append("            background-color: #f8fafc;\n")
            .append("        }\n")
            .append("        .table-custom th {\n")
            .append("            padding: 0.4rem 0.65rem;\n")
            .append("            font-weight: 600;\n")
            .append("            font-size: 0.66rem;\n")
            .append("            text-transform: uppercase;\n")
            .append("            letter-spacing: 0.3px;\n")
            .append("            color: #64748b;\n")
            .append("            text-align: left;\n")
            .append("            white-space: nowrap;\n")
            .append("            border-bottom: 2px solid #e2e8f0;\n")
            .append("            line-height: 1.2;\n")
            .append("        }\n")
            .append("        .table-custom td {\n")
            .append("            padding: 0.32rem 0.65rem;\n")
            .append("            border-bottom: 1px solid #f1f5f9;\n")
            .append("            vertical-align: middle;\n")
            .append("            font-size: 0.78rem;\n")
            .append("            line-height: 1.3;\n")
            .append("        }\n")
            .append("        .table-custom tbody tr { transition: background 0.12s ease; }\n")
            .append("        .table-custom tbody tr:hover {\n")
            .append("            background: #f8fafc;\n")
            .append("            box-shadow: inset 3px 0 0 var(--primary-color);\n")
            .append("        }\n")
            .append("        .table-custom tbody tr:last-child td { border-bottom: none; }\n")
            .append("        .table-custom code {\n")
            .append("            font-size: 0.72rem;\n")
            .append("            background: #f1f5f9;\n")
            .append("            padding: 0.1rem 0.35rem;\n")
            .append("            border-radius: 4px;\n")
            .append("            color: #475569;\n")
            .append("        }\n")

            // ---------- BADGES ----------
            .append("        .badge-soft-primary {\n")
            .append("            background-color: #e0e7ff;\n")
            .append("            color: #4338ca;\n")
            .append("            font-weight: 600;\n")
            .append("            border-radius: 5px;\n")
            .append("            padding: 0.15rem 0.5rem;\n")
            .append("            font-size: 0.68rem;\n")
            .append("            line-height: 1.2;\n")
            .append("            display: inline-block;\n")
            .append("        }\n")
            .append("        .badge-soft-info {\n")
            .append("            background-color: #e0f2fe;\n")
            .append("            color: #0369a1;\n")
            .append("            font-weight: 600;\n")
            .append("            border-radius: 5px;\n")
            .append("            padding: 0.15rem 0.5rem;\n")
            .append("            font-size: 0.68rem;\n")
            .append("            line-height: 1.2;\n")
            .append("            display: inline-block;\n")
            .append("        }\n")
            .append("        .badge-actif {\n")
            .append("            background-color: #d1fae5;\n")
            .append("            color: #065f46;\n")
            .append("            font-weight: 600;\n")
            .append("            border-radius: 5px;\n")
            .append("            padding: 0.15rem 0.5rem;\n")
            .append("            font-size: 0.68rem;\n")
            .append("            line-height: 1.2;\n")
            .append("            display: inline-block;\n")
            .append("        }\n")
            .append("        .badge-inactif {\n")
            .append("            background-color: #f1f5f9;\n")
            .append("            color: #64748b;\n")
            .append("            font-weight: 600;\n")
            .append("            border-radius: 5px;\n")
            .append("            padding: 0.15rem 0.5rem;\n")
            .append("            font-size: 0.68rem;\n")
            .append("            line-height: 1.2;\n")
            .append("            display: inline-block;\n")
            .append("        }\n")
            .append("        .badge-periode {\n")
            .append("            background: #f8fafc;\n")
            .append("            color: #475569;\n")
            .append("            border: 1px solid #e2e8f0;\n")
            .append("            font-weight: 600;\n")
            .append("            border-radius: 5px;\n")
            .append("            padding: 0.15rem 0.5rem;\n")
            .append("            font-size: 0.68rem;\n")
            .append("            line-height: 1.2;\n")
            .append("            display: inline-block;\n")
            .append("        }\n")
            .append("        .note-min { color: #dc2626; font-weight: 600; }\n")
            .append("        .note-pass { color: #d97706; font-weight: 700; }\n")
            .append("        .note-max { color: #16a34a; font-weight: 700; }\n")
            .append("        .duree-badge { font-size: 0.66rem; color: #64748b; }\n")

            // ---------- ACTION BAR ----------
            .append("        .action-bar {\n")
            .append("            padding: 0.6rem 1rem;\n")
            .append("            background: #f8fafc;\n")
            .append("            border-top: 1px solid #e2e8f0;\n")
            .append("            border-radius: 0 0 10px 10px;\n")
            .append("            display: flex;\n")
            .append("            justify-content: flex-end;\n")
            .append("            gap: 0.5rem;\n")
            .append("            flex-shrink: 0;\n")
            .append("        }\n")
            .append("        .btn-action {\n")
            .append("            border-radius: 8px;\n")
            .append("            padding: 0.45rem 1.1rem;\n")
            .append("            font-weight: 600;\n")
            .append("            font-size: 0.82rem;\n")
            .append("            border: none;\n")
            .append("            transition: 0.15s;\n")
            .append("            text-decoration: none;\n")
            .append("            display: inline-flex;\n")
            .append("            align-items: center;\n")
            .append("            gap: 0.4rem;\n")
            .append("            height: 34px;\n")
            .append("            cursor: pointer;\n")
            .append("        }\n")
            .append("        .btn-create { background: #22c55e; color: white; }\n")
            .append("        .btn-create:hover { background: #16a34a; color: white; }\n")
            .append("        .btn-icon {\n")
            .append("            width: 26px;\n")
            .append("            height: 26px;\n")
            .append("            border-radius: 6px;\n")
            .append("            display: inline-flex;\n")
            .append("            align-items: center;\n")
            .append("            justify-content: center;\n")
            .append("            border: 1px solid #e2e8f0;\n")
            .append("            background: white;\n")
            .append("            transition: 0.15s;\n")
            .append("            text-decoration: none;\n")
            .append("            cursor: pointer;\n")
            .append("            font-size: 0.72rem;\n")
            .append("        }\n")
            .append("        .btn-icon-edit { color: #f59e0b; }\n")
            .append("        .btn-icon-edit:hover {\n")
            .append("            background: #fef3c7;\n")
            .append("            color: #d97706;\n")
            .append("            border-color: #fcd34d;\n")
            .append("        }\n")
            .append("        .btn-icon-delete { color: #ef4444; }\n")
            .append("        .btn-icon-delete:hover {\n")
            .append("            background: #fee2e2;\n")
            .append("            color: #dc2626;\n")
            .append("            border-color: #fca5a5;\n")
            .append("        }\n")

            // ---------- MODAL ----------
            .append("        .modal-content {\n")
            .append("            border: none;\n")
            .append("            border-radius: 16px;\n")
            .append("            box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.25);\n")
            .append("        }\n")
            .append("        .modal-header {\n")
            .append("            border-bottom: 1px solid #f1f5f9;\n")
            .append("            padding: 1rem 1.5rem;\n")
            .append("        }\n")
            .append("        .modal-footer {\n")
            .append("            border-top: 1px solid #f1f5f9;\n")
            .append("            padding: 0.8rem 1.5rem;\n")
            .append("        }\n")
            .append("        .modal-body { padding: 1.2rem 1.5rem; }\n")

            // ---------- RESPONSIVE — 4K ----------
            .append("        @media (min-width: 1920px) {\n")
            .append("            .main-wrapper { padding: 12px; gap: 12px; }\n")
            .append("            .glass-header { padding: 14px 24px; }\n")
            .append("            .glass-header h1 { font-size: 1.35rem; }\n")
            .append("            .table-custom th { font-size: 0.72rem; padding: 0.5rem 0.8rem; }\n")
            .append("            .table-custom td { font-size: 0.86rem; padding: 0.4rem 0.8rem; }\n")
            .append("            .table-custom { min-width: 1300px; }\n")
            .append("            .filters-bar { padding: 10px 16px; }\n")
            .append("        }\n")

            // ---------- RESPONSIVE — MOBILE ----------
            .append("        @media (max-width: 768px) {\n")
            .append("            html, body {\n")
            .append("                overflow: auto;\n")
            .append("                height: auto;\n")
            .append("                min-height: 100dvh;\n")
            .append("            }\n")
            .append("            .main-wrapper {\n")
            .append("                height: auto;\n")
            .append("                min-height: 100dvh;\n")
            .append("                overflow: visible;\n")
            .append("                padding: 6px;\n")
            .append("                gap: 6px;\n")
            .append("            }\n")
            .append("            .glass-header {\n")
            .append("                padding: 8px 12px;\n")
            .append("                border-radius: 10px;\n")
            .append("            }\n")
            .append("            .glass-header h1 { font-size: 1rem; }\n")
            .append("            .glass-header .subtitle { display: none; }\n")
            .append("            .btn-glass { font-size: 0.72rem; padding: 0.25rem 0.7rem; }\n")
            .append("            .filters-bar { padding: 8px; border-radius: 8px; }\n")
            .append("            .filters-bar form {\n")
            .append("                display: flex;\n")
            .append("                flex-direction: column !important;\n")
            .append("                gap: 6px !important;\n")
            .append("            }\n")
            .append("            .filters-bar form .col,\n")
            .append("            .filters-bar form .col-auto { width: 100% !important; }\n")
            .append("            .filters-bar form .form-control,\n")
            .append("            .filters-bar form .form-select { width: 100% !important; }\n")
            .append("            .filters-bar form .d-flex { justify-content: stretch; }\n")
            .append("            .filters-bar form .btn-primary-sm { flex: 1; justify-content: center; }\n")
            .append("            .filters-bar form .btn-outline-sm { flex: 0 0 auto; }\n")
            .append("            .table-container {\n")
            .append("                height: auto;\n")
            .append("                max-height: none;\n")
            .append("                border-radius: 8px;\n")
            .append("            }\n")
            .append("            .table-scroll {\n")
            .append("                max-height: 65vh;\n")
            .append("                overflow: auto;\n")
            .append("                -webkit-overflow-scrolling: touch;\n")
            .append("            }\n")
            .append("            .table-scroll td, .table-scroll th {\n")
            .append("                padding: 0.3rem 0.45rem;\n")
            .append("                font-size: 0.7rem;\n")
            .append("            }\n")
            .append("            .table-custom { min-width: 900px; }\n")
            .append("            .action-bar {\n")
            .append("                padding: 0.5rem 0.7rem;\n")
            .append("                border-radius: 0 0 8px 8px;\n")
            .append("            }\n")
            .append("            .btn-action {\n")
            .append("                padding: 0.4rem 0.8rem;\n")
            .append("                font-size: 0.75rem;\n")
            .append("                height: 32px;\n")
            .append("                flex: 1;\n")
            .append("                justify-content: center;\n")
            .append("            }\n")
            .append("            .btn-icon { width: 24px; height: 24px; font-size: 0.68rem; }\n")
            .append("        }\n")

            // ---------- RESPONSIVE — TRÈS PETIT MOBILE ----------
            .append("        @media (max-width: 400px) {\n")
            .append("            .glass-header h1 { font-size: 0.9rem; }\n")
            .append("            .table-scroll td, .table-scroll th {\n")
            .append("                padding: 0.25rem 0.4rem;\n")
            .append("                font-size: 0.66rem;\n")
            .append("            }\n")
            .append("            .table-custom { min-width: 800px; }\n")
            .append("            .table-custom code { font-size: 0.6rem; }\n")
            .append("            .badge-soft-primary,\n")
            .append("            .badge-soft-info,\n")
            .append("            .badge-actif,\n")
            .append("            .badge-inactif,\n")
            .append("            .badge-periode { font-size: 0.6rem; padding: 0.1rem 0.4rem; }\n")
            .append("        }\n")

            // ---------- RESPONSIVE — PAYSAGE MOBILE ----------
            .append("        @media (max-height: 500px) and (orientation: landscape) {\n")
            .append("            .main-wrapper { padding: 4px; gap: 4px; }\n")
            .append("            .glass-header { padding: 6px 12px; min-height: 42px; }\n")
            .append("            .glass-header h1 { font-size: 0.9rem; }\n")
            .append("            .glass-header .subtitle { display: none; }\n")
            .append("            .filters-bar { padding: 6px 8px; }\n")
            .append("            .table-scroll { max-height: 55vh; }\n")
            .append("            .action-bar { padding: 0.4rem 0.6rem; }\n")
            .append("        }\n")
            .append("    </style>\n")
            .append("</head>\n");
    }
    // ============================================================
    // HEADER
    // ============================================================
    private static void appendHeader(StringBuilder html, String institutionId) {
        html.append("    <div class=\"glass-header\">\n")
            .append("        <div>\n")
            .append("            <h1><i class=\"fa-solid fa-book-open\"></i> Gestion des Matières</h1>\n")
            .append("            <p class=\"subtitle\">Catalogue des cours, barèmes, coefficients et crédits</p>\n")
            .append("        </div>\n")
            .append("        <div>\n")
            .append("            <a href=\"/dashboard?institutionId=")
            .append(urlEncode(institutionId))
            .append("\" class=\"btn-glass\"><i class=\"fa-solid fa-arrow-left me-1\"></i> Tableau de bord</a>\n")
            .append("        </div>\n")
            .append("    </div>\n");
    }

    // ============================================================
    // MESSAGES
    // ============================================================
    private static void appendMessages(StringBuilder html, String success, String error) {
        if (!success.isBlank()) {
            html.append("    <div class=\"alert alert-success alert-dismissible fade show border-0 shadow-sm rounded-3 mb-0 py-2\" role=\"alert\">\n")
                .append("        <i class=\"fa-solid fa-circle-check me-2\"></i>").append(echapperHtml(success)).append("\n")
                .append("        <button type=\"button\" class=\"btn-close py-2\" data-bs-dismiss=\"alert\"></button>\n")
                .append("    </div>\n");
        }
        if (!error.isBlank()) {
            html.append("    <div class=\"alert alert-danger alert-dismissible fade show border-0 shadow-sm rounded-3 mb-0 py-2\" role=\"alert\">\n")
                .append("        <i class=\"fa-solid fa-circle-exclamation me-2\"></i>").append(echapperHtml(error)).append("\n")
                .append("        <button type=\"button\" class=\"btn-close py-2\" data-bs-dismiss=\"alert\"></button>\n")
                .append("    </div>\n");
        }
    }

    // ============================================================
    // FILTRES
    // ============================================================
    private static void appendFiltres(StringBuilder html,
                                       String queryRecherche,
                                       String anneeFiltre,
                                       String periodeFiltre,
                                       String classeFiltre,
                                       List<String> anneesDisponibles,
                                       List<String> periodesDisponibles,
                                       List<String> classesDisponibles,
                                       String institutionId) {
        html.append("    <div class=\"filters-bar\">\n")
            .append("        <form method=\"GET\" action=\"/admin/matieres\" class=\"row g-2 align-items-center\">\n")
            // ✅ institutionId propagé en champ caché
            .append("            <input type=\"hidden\" name=\"institutionId\" value=\"")
            .append(echapperHtml(institutionId))
            .append("\">\n")

            .append("            <div class=\"col\">\n")
            .append("                <div class=\"input-group input-group-sm\">\n")
            .append("                    <span class=\"input-group-text\"><i class=\"fa-solid fa-magnifying-glass text-muted\"></i></span>\n")
            .append("                    <input type=\"text\" name=\"q\" class=\"form-control\" placeholder=\"Rechercher par code ou nom...\" value=\"").append(echapperHtml(queryRecherche)).append("\">\n")
            .append("                </div>\n")
            .append("            </div>\n")

            // Année
            .append("            <div class=\"col-auto\">\n")
            .append("                <select name=\"annee\" class=\"form-select form-select-sm\">\n")
            .append("                    <option value=\"\">Toutes les années</option>\n");
        for (String a : anneesDisponibles) {
            html.append("                    <option value=\"").append(echapperHtml(a)).append("\"")
                .append(a.equals(anneeFiltre) ? " selected" : "").append(">")
                .append(echapperHtml(a)).append("</option>\n");
        }
        html.append("                </select>\n")
            .append("            </div>\n")

            // Période
            .append("            <div class=\"col-auto\">\n")
            .append("                <select name=\"periode\" class=\"form-select form-select-sm\">\n")
            .append("                    <option value=\"\">Toutes les périodes</option>\n");
        for (String p : periodesDisponibles) {
            html.append("                    <option value=\"").append(echapperHtml(p)).append("\"")
                .append(p.equals(periodeFiltre) ? " selected" : "").append(">")
                .append(echapperHtml(p)).append("</option>\n");
        }
        html.append("                </select>\n")
            .append("            </div>\n")

            // Classe
            .append("            <div class=\"col-auto\">\n")
            .append("                <select name=\"classe\" class=\"form-select form-select-sm\">\n")
            .append("                    <option value=\"\">Toutes les classes</option>\n");
        for (String c : classesDisponibles) {
            html.append("                    <option value=\"").append(echapperHtml(c)).append("\"")
                .append(c.equals(classeFiltre) ? " selected" : "").append(">")
                .append(echapperHtml(c)).append("</option>\n");
        }
        html.append("                </select>\n")
            .append("            </div>\n")

            // Boutons
            .append("            <div class=\"col-auto d-flex gap-2\">\n")
            .append("                <button type=\"submit\" class=\"btn-primary-sm\"><i class=\"fa-solid fa-filter me-1\"></i> Filtrer</button>\n")
            // ✅ institutionId propagé dans le lien de réinitialisation
            .append("                <a href=\"/admin/matieres?institutionId=")
            .append(urlEncode(institutionId))
            .append("\" class=\"btn-outline-sm\" title=\"Réinitialiser\"><i class=\"fa-solid fa-rotate-right\"></i></a>\n")
            .append("            </div>\n")
            .append("        </form>\n")
            .append("    </div>\n");
    }

    // ============================================================
    // TABLEAU
    // ============================================================
    private static void appendTableau(StringBuilder html,
                                       List<Matiere> matieres,
                                       boolean canModify,
                                       boolean canDelete,
                                       String institutionId) {
        html.append("    <div class=\"table-container\">\n")
            .append("        <div class=\"table-scroll\">\n")
            .append("            <table class=\"table-custom\">\n")
            .append("                <thead>\n")
            .append("                    <tr>\n")
            .append("                        <th>Code</th>\n")
            .append("                        <th>Intitulé</th>\n")
            .append("                        <th>Classe & Année</th>\n")
            .append("                        <th class=\"text-center\">Période</th>\n")
            .append("                        <th class=\"text-center\">Coeff / Crédits</th>\n")
            .append("                        <th class=\"text-center\">Barème (Min/Pass/Max)</th>\n")
            .append("                        <th class=\"text-center\">Statut</th>\n")
            .append("                        <th class=\"text-end\" style=\"width: 90px;\">Actions</th>\n")
            .append("                    </tr>\n")
            .append("                </thead>\n")
            .append("                <tbody>\n");

        if (matieres.isEmpty()) {
            html.append("                    <tr>\n")
                .append("                        <td colspan=\"8\" class=\"text-center py-5 text-muted\">\n")
                .append("                            <i class=\"fa-solid fa-folder-open fs-1 mb-3 text-black-50 d-block\"></i>\n")
                .append("                            <span>Aucune matière trouvée.</span>\n")
                .append("                        </td>\n")
                .append("                    </tr>\n");
        } else {
            for (Matiere m : matieres) {
                appendLigneMatiere(html, m, canModify, canDelete, institutionId);
            }
        }

        html.append("                </tbody>\n")
            .append("            </table>\n")
            .append("        </div>\n")
            .append("    </div>\n");
    }

    private static void appendLigneMatiere(StringBuilder html, Matiere m,
                                            boolean canModify, boolean canDelete,
                                            String institutionId) {
        boolean isActif = "Actif".equalsIgnoreCase(m.getStatut())
                || "ACTIF".equalsIgnoreCase(m.getStatut());
        String badgeStatut = isActif
            ? "<span class=\"badge-actif\">Actif</span>"
            : "<span class=\"badge-inactif\">Inactif</span>";

        html.append("                    <tr>\n")
            .append("                        <td><code>")
            .append(echapperHtml(m.getCodeCours())).append("</code></td>\n")

            .append("                        <td>\n")
            .append("                            <div class=\"fw-semibold text-dark\" style=\"font-size:0.8rem;\">")
            .append(echapperHtml(m.getNomMatiere())).append("</div>\n")
            .append("                            <small class=\"duree-badge\"><i class=\"fa-regular fa-clock me-1\"></i>")
            .append(echapperHtml(m.getDureeCours() != null ? m.getDureeCours() : "-")).append("</small>\n")
            .append("                        </td>\n")

            .append("                        <td>\n")
            .append("                            <div style=\"font-size:0.78rem;\"><i class=\"fa-solid fa-users-rectangle me-1 text-muted\"></i>")
            .append(echapperHtml(m.getClasseMatiere())).append("</div>\n")
            .append("                            <small class=\"duree-badge\">")
            .append(echapperHtml(m.getAnneeAcademique())).append("</small>\n")
            .append("                        </td>\n")

            .append("                        <td class=\"text-center\"><span class=\"badge-periode\">")
            .append(echapperHtml(m.getPeriode())).append("</span></td>\n")

            .append("                        <td class=\"text-center\">\n")
            .append("                            <div style=\"font-size:0.78rem;\"><strong>Coeff:</strong> ").append(m.getCoefficient()).append("</div>\n")
            .append("                            <span class=\"badge-soft-info\">").append(m.getNombreCredits()).append(" Crédits</span>\n")
            .append("                        </td>\n")

            .append("                        <td class=\"text-center\" style=\"font-size:0.78rem;\">\n")
            .append("                            <span class=\"note-min\">").append(m.getNoteMinimale()).append("</span>\n")
            .append("                            <span class=\"text-muted\">/</span>\n")
            .append("                            <span class=\"note-pass\">").append(m.getNotePassage()).append("</span>\n")
            .append("                            <span class=\"text-muted\">/</span>\n")
            .append("                            <span class=\"note-max\">").append(m.getNoteMaximale()).append("</span>\n")
            .append("                        </td>\n")

            .append("                        <td class=\"text-center\">").append(badgeStatut).append("</td>\n")
            .append("                        <td class=\"text-end\">\n");

        // ✅ Bouton Éditer : affiché uniquement si canModify
        if (canModify) {
            html.append("                            <button type=\"button\" class=\"btn-icon btn-icon-edit btn-edit-matiere me-1\"")
                .append(" title=\"Éditer\"")
                .append(" data-code=\"").append(echapperHtml(m.getCodeCours())).append("\"")
                .append(" data-nom=\"").append(echapperHtml(m.getNomMatiere())).append("\"")
                .append(" data-classe=\"").append(echapperHtml(m.getClasseMatiere())).append("\"")
                .append(" data-annee=\"").append(echapperHtml(m.getAnneeAcademique())).append("\"")
                .append(" data-periode=\"").append(echapperHtml(m.getPeriode())).append("\"")
                .append(" data-coeff=\"").append(m.getCoefficient()).append("\"")
                .append(" data-credits=\"").append(m.getNombreCredits()).append("\"")
                .append(" data-notemin=\"").append(m.getNoteMinimale()).append("\"")
                .append(" data-notepass=\"").append(m.getNotePassage()).append("\"")
                .append(" data-notemax=\"").append(m.getNoteMaximale()).append("\"")
                .append(" data-duree=\"").append(echapperHtml(m.getDureeCours() != null ? m.getDureeCours() : "")).append("\"")
                .append(" data-statut=\"").append(echapperHtml(m.getStatut())).append("\"")
                .append("><i class=\"fa-solid fa-pen\"></i></button>\n");
        }

        // ✅ Bouton Supprimer : affiché uniquement si canDelete
        if (canDelete) {
            html.append("                            <a href=\"/admin/matieres?action=delete&institutionId=")
                .append(urlEncode(institutionId))
                .append("&code=")
                .append(urlEncode(m.getCodeCours()))
                .append("\" class=\"btn-icon btn-icon-delete\" title=\"Supprimer\"")
                .append(" onclick=\"return confirm('Êtes-vous sûr de vouloir supprimer cette matière ?');\">")
                .append("<i class=\"fa-solid fa-trash-can\"></i></a>\n");
        }

        html.append("                        </td>\n")
            .append("                    </tr>\n");
    }

    // ============================================================
    // ACTION BAR
    // ============================================================
    private static void appendActionBar(StringBuilder html, boolean canCreate) {
        html.append("        <div class=\"action-bar\">\n");
        if (canCreate) {
            html.append("            <button type=\"button\" class=\"btn-action btn-create\"")
                .append(" id=\"btnNouvelleMatiere\"")
                .append(" data-bs-toggle=\"modal\"")
                .append(" data-bs-target=\"#modalMatiere\"")
                .append(" onclick=\"if(typeof window.resetFormMatiere==='function') window.resetFormMatiere();\">\n")
                .append("                <i class=\"fa-solid fa-plus\"></i> Créer une matière\n")
                .append("            </button>\n");
        }
        html.append("        </div>\n")
            .append("    </div>\n");
    }

    // ============================================================
    // MODAL
    // ============================================================
    private static void appendModal(StringBuilder html,
                                     List<String> anneesDisponibles,
                                     List<String> periodesDisponibles,
                                     List<String> classesDisponibles,
                                     String institutionId) {
        html.append("<div class=\"modal fade\" id=\"modalMatiere\" tabindex=\"-1\" aria-hidden=\"true\">\n")
            .append("    <div class=\"modal-dialog modal-dialog-centered modal-lg\">\n")
            .append("        <div class=\"modal-content\">\n")
            .append("            <form method=\"POST\" action=\"/admin/matieres\" id=\"formMatiere\">\n")
            // ✅ institutionId propagé en champ caché (POST création/modification)
            .append("                <input type=\"hidden\" name=\"institutionId\" value=\"")
            .append(echapperHtml(institutionId))
            .append("\">\n")
            .append("                <input type=\"hidden\" name=\"action\" id=\"formAction\" value=\"add\">\n")
            .append("                <div class=\"modal-header\">\n")
            .append("                    <h5 class=\"modal-title fw-bold text-dark\" id=\"modalTitle\"><i class=\"fa-solid fa-book-open text-primary me-2\"></i>Nouvelle Matière</h5>\n")
            .append("                    <button type=\"button\" class=\"btn-close\" data-bs-dismiss=\"modal\"></button>\n")
            .append("                </div>\n")
            .append("                <div class=\"modal-body\">\n")
            .append("                    <div class=\"row g-3\">\n")

            .append("                        <div class=\"col-md-4\">\n")
            .append("                            <label class=\"form-label fw-semibold fs-7 text-uppercase text-muted\">Code Cours</label>\n")
            .append("                            <input type=\"text\" name=\"codeCours\" id=\"formCodeCours\" class=\"form-control bg-light\" readonly placeholder=\"Généré automatiquement\">\n")
            .append("                        </div>\n")
            .append("                        <div class=\"col-md-8\">\n")
            .append("                            <label class=\"form-label fw-semibold fs-7 text-uppercase text-muted\">Intitulé de la Matière *</label>\n")
            .append("                            <input type=\"text\" name=\"nomMatiere\" id=\"formNomMatiere\" class=\"form-control\" required placeholder=\"ex: Mathématiques\">\n")
            .append("                        </div>\n")

            // Classe
            .append("                        <div class=\"col-md-4\">\n")
            .append("                            <label class=\"form-label fw-semibold fs-7 text-uppercase text-muted\">Classe *</label>\n")
            .append("                            <div id=\"boxClasseSelect\">\n")
            .append("                                <select name=\"classeMatiereSelect\" id=\"formClasseSelect\" class=\"form-select\">\n")
            .append("                                    <option value=\"\">-- Sélectionner --</option>\n");
        for (String c : classesDisponibles) {
            html.append("                                    <option value=\"").append(echapperHtml(c)).append("\">").append(echapperHtml(c)).append("</option>\n");
        }
        html.append("                                </select>\n")
            .append("                            </div>\n")
            .append("                            <input type=\"text\" name=\"classeMatiere\" id=\"formClasseReadonly\" class=\"form-control bg-light d-none\" readonly>\n")
            .append("                        </div>\n")

            // Année
            .append("                        <div class=\"col-md-4\">\n")
            .append("                            <label class=\"form-label fw-semibold fs-7 text-uppercase text-muted\">Année Académique *</label>\n")
            .append("                            <div id=\"boxAnneeSelect\">\n")
            .append("                                <select name=\"anneeAcademiqueSelect\" id=\"formAnneeSelect\" class=\"form-select\">\n")
            .append("                                    <option value=\"\">-- Sélectionner --</option>\n");
        for (String a : anneesDisponibles) {
            html.append("                                    <option value=\"").append(echapperHtml(a)).append("\">").append(echapperHtml(a)).append("</option>\n");
        }
        html.append("                                </select>\n")
            .append("                            </div>\n")
            .append("                            <input type=\"text\" name=\"anneeAcademique\" id=\"formAnneeReadonly\" class=\"form-control bg-light d-none\" readonly>\n")
            .append("                        </div>\n")

            // Période
            .append("                        <div class=\"col-md-4\">\n")
            .append("                            <label class=\"form-label fw-semibold fs-7 text-uppercase text-muted\">Période *</label>\n")
            .append("                            <div id=\"boxPeriodeSelect\">\n")
            .append("                                <select name=\"periodeSelect\" id=\"formPeriodeSelect\" class=\"form-select\">\n")
            .append("                                    <option value=\"\">-- Sélectionner --</option>\n");
        for (String p : periodesDisponibles) {
            html.append("                                    <option value=\"").append(echapperHtml(p)).append("\">").append(echapperHtml(p)).append("</option>\n");
        }
        html.append("                                </select>\n")
            .append("                            </div>\n")
            .append("                            <input type=\"text\" name=\"periode\" id=\"formPeriodeReadonly\" class=\"form-control bg-light d-none\" readonly>\n")
            .append("                        </div>\n")

            .append("                        <div class=\"col-md-3\">\n")
            .append("                            <label class=\"form-label fw-semibold fs-7 text-uppercase text-muted\">Coefficient *</label>\n")
            .append("                            <input type=\"number\" step=\"0.1\" name=\"coefficient\" id=\"formCoefficient\" class=\"form-control\" value=\"1.0\" min=\"0.1\" required>\n")
            .append("                        </div>\n")
            .append("                        <div class=\"col-md-3\">\n")
            .append("                            <label class=\"form-label fw-semibold fs-7 text-uppercase text-muted\">Crédits *</label>\n")
            .append("                            <input type=\"number\" name=\"nombreCredits\" id=\"formNombreCredits\" class=\"form-control\" value=\"3\" min=\"1\" required>\n")
            .append("                        </div>\n")
            .append("                        <div class=\"col-md-3\">\n")
            .append("                            <label class=\"form-label fw-semibold fs-7 text-uppercase text-muted\">Durée Cours</label>\n")
            .append("                            <input type=\"text\" name=\"dureeCours\" id=\"formDureeCours\" class=\"form-control\" placeholder=\"ex: 45 Heures\">\n")
            .append("                        </div>\n")
            .append("                        <div class=\"col-md-3\">\n")
            .append("                            <label class=\"form-label fw-semibold fs-7 text-uppercase text-muted\">Statut</label>\n")
            .append("                            <select name=\"statut\" id=\"formStatut\" class=\"form-select\">\n")
            .append("                                <option value=\"ACTIF\">Actif</option>\n")
            .append("                                <option value=\"INACTIF\">Inactif</option>\n")
            .append("                            </select>\n")
            .append("                        </div>\n")

            .append("                        <div class=\"col-md-4\">\n")
            .append("                            <label class=\"form-label fw-semibold fs-7 text-uppercase text-muted\">Note Minimale *</label>\n")
            .append("                            <input type=\"number\" step=\"0.1\" name=\"noteMinimale\" id=\"formNoteMinimale\" class=\"form-control\" value=\"0.0\" required>\n")
            .append("                        </div>\n")
            .append("                        <div class=\"col-md-4\">\n")
            .append("                            <label class=\"form-label fw-semibold fs-7 text-uppercase text-muted\">Note de Passage *</label>\n")
            .append("                            <input type=\"number\" step=\"0.1\" name=\"notePassage\" id=\"formNotePassage\" class=\"form-control\" value=\"50.0\" required>\n")
            .append("                        </div>\n")
            .append("                        <div class=\"col-md-4\">\n")
            .append("                            <label class=\"form-label fw-semibold fs-7 text-uppercase text-muted\">Note Maximale *</label>\n")
            .append("                            <input type=\"number\" step=\"0.1\" name=\"noteMaximale\" id=\"formNoteMaximale\" class=\"form-control\" value=\"100.0\" required>\n")
            .append("                        </div>\n")

            .append("                    </div>\n")
            .append("                </div>\n")
            .append("                <div class=\"modal-footer bg-light-subtle\">\n")
            .append("                    <button type=\"button\" class=\"btn btn-light border fw-medium px-4 rounded-3\" data-bs-dismiss=\"modal\">Annuler</button>\n")
            .append("                    <button type=\"submit\" class=\"btn btn-primary px-4 rounded-3\" id=\"btnSaveMatiere\"><i class=\"fa-solid fa-check me-2\"></i>Enregistrer</button>\n")
            .append("                </div>\n")
            .append("            </form>\n")
            .append("        </div>\n")
            .append("    </div>\n")
            .append("</div>\n");
    }

    // ============================================================
    // SCRIPTS
    // ============================================================
    private static void appendScripts(StringBuilder html, boolean canModify, boolean canDelete, String institutionId) {
        html.append("<script>\n")
            .append("document.addEventListener('DOMContentLoaded', function() {\n")

            // ✅ Permissions + institution injectées côté client
            .append("    const canModify = ").append(canModify).append(";\n")
            .append("    const canDelete = ").append(canDelete).append(";\n")
            .append("    const institutionId = '").append(echapperJs(institutionId)).append("';\n")
            .append("\n")

            // Vérification Bootstrap
            .append("    if (typeof bootstrap === 'undefined' || !bootstrap.Modal) {\n")
            .append("        console.error('❌ Bootstrap Modal non chargé');\n")
            .append("        return;\n")
            .append("    }\n")

            // Instance modale
            .append("    const modalElement = document.getElementById('modalMatiere');\n")
            .append("    if (!modalElement) { console.error('❌ Modal introuvable'); return; }\n")
            .append("    const modalInstance = new bootstrap.Modal(modalElement);\n")
            .append("    window.matiereModalInstance = modalInstance;\n")
            .append("\n")

            // resetFormMatiere
            .append("    window.resetFormMatiere = function() {\n")
            .append("        document.getElementById('formAction').value = 'add';\n")
            .append("        document.getElementById('formCodeCours').value = 'Généré automatiquement';\n")
            .append("        document.getElementById('formNomMatiere').value = '';\n")
            .append("        document.getElementById('boxClasseSelect').classList.remove('d-none');\n")
            .append("        document.getElementById('formClasseSelect').required = true;\n")
            .append("        document.getElementById('formClasseReadonly').classList.add('d-none');\n")
            .append("        document.getElementById('formClasseSelect').value = '';\n")
            .append("        document.getElementById('boxAnneeSelect').classList.remove('d-none');\n")
            .append("        document.getElementById('formAnneeSelect').required = true;\n")
            .append("        document.getElementById('formAnneeReadonly').classList.add('d-none');\n")
            .append("        document.getElementById('formAnneeSelect').value = '';\n")
            .append("        document.getElementById('boxPeriodeSelect').classList.remove('d-none');\n")
            .append("        document.getElementById('formPeriodeSelect').required = true;\n")
            .append("        document.getElementById('formPeriodeReadonly').classList.add('d-none');\n")
            .append("        document.getElementById('formPeriodeSelect').value = '';\n")
            .append("        document.getElementById('formCoefficient').value = '1.0';\n")
            .append("        document.getElementById('formNombreCredits').value = '3';\n")
            .append("        document.getElementById('formDureeCours').value = '';\n")
            .append("        document.getElementById('formStatut').value = 'ACTIF';\n")
            .append("        document.getElementById('formNoteMinimale').value = '0.0';\n")
            .append("        document.getElementById('formNotePassage').value = '50.0';\n")
            .append("        document.getElementById('formNoteMaximale').value = '100.0';\n")
            .append("        document.getElementById('modalTitle').innerHTML = '<i class=\"fa-solid fa-book-open text-primary me-2\"></i>Nouvelle Matière';\n")
            .append("    };\n")
            .append("\n")

            // ouvrirModalEditionMatiere
            .append("    window.ouvrirModalEditionMatiere = function(btn) {\n")
            .append("        if (!canModify) {\n")
            .append("            alert('Vous n\\'avez pas la permission de modifier une matière.');\n")
            .append("            return;\n")
            .append("        }\n")
            .append("        document.getElementById('formAction').value = 'update';\n")
            .append("        document.getElementById('formCodeCours').value = btn.dataset.code || '';\n")
            .append("        document.getElementById('formNomMatiere').value = btn.dataset.nom || '';\n")
            .append("        document.getElementById('boxClasseSelect').classList.add('d-none');\n")
            .append("        document.getElementById('formClasseSelect').required = false;\n")
            .append("        document.getElementById('formClasseReadonly').classList.remove('d-none');\n")
            .append("        document.getElementById('formClasseReadonly').value = btn.dataset.classe || '';\n")
            .append("        document.getElementById('boxAnneeSelect').classList.add('d-none');\n")
            .append("        document.getElementById('formAnneeSelect').required = false;\n")
            .append("        document.getElementById('formAnneeReadonly').classList.remove('d-none');\n")
            .append("        document.getElementById('formAnneeReadonly').value = btn.dataset.annee || '';\n")
            .append("        document.getElementById('boxPeriodeSelect').classList.add('d-none');\n")
            .append("        document.getElementById('formPeriodeSelect').required = false;\n")
            .append("        document.getElementById('formPeriodeReadonly').classList.remove('d-none');\n")
            .append("        document.getElementById('formPeriodeReadonly').value = btn.dataset.periode || '';\n")
            .append("        document.getElementById('formCoefficient').value = btn.dataset.coeff || '1.0';\n")
            .append("        document.getElementById('formNombreCredits').value = btn.dataset.credits || '1';\n")
            .append("        document.getElementById('formNoteMinimale').value = btn.dataset.notemin || '0.0';\n")
            .append("        document.getElementById('formNotePassage').value = btn.dataset.notepass || '50.0';\n")
            .append("        document.getElementById('formNoteMaximale').value = btn.dataset.notemax || '100.0';\n")
            .append("        document.getElementById('formDureeCours').value = btn.dataset.duree || '';\n")
            .append("        document.getElementById('formStatut').value = btn.dataset.statut || 'ACTIF';\n")
            .append("        document.getElementById('modalTitle').innerHTML = '<i class=\"fa-solid fa-pen-to-square text-warning me-2\"></i>Modifier : ' + btn.dataset.code;\n")
            .append("        modalInstance.show();\n")
            .append("    };\n")
            .append("\n")

            // ✅ Listeners boutons "Éditer" — UNIQUEMENT si canModify
            .append("    if (canModify) {\n")
            .append("        document.querySelectorAll('.btn-edit-matiere').forEach(function(btn) {\n")
            .append("            btn.addEventListener('click', function() {\n")
            .append("                window.ouvrirModalEditionMatiere(this);\n")
            .append("            });\n")
            .append("        });\n")
            .append("    } else {\n")
            .append("        document.querySelectorAll('.btn-edit-matiere').forEach(function(btn) {\n")
            .append("            btn.remove();\n")
            .append("        });\n")
            .append("    }\n")
            .append("\n")

            // ✅ Sécurité suppression : bloquer le clic si canDelete=false
            .append("    if (!canDelete) {\n")
            .append("        document.querySelectorAll('.btn-icon-delete').forEach(function(btn) {\n")
            .append("            btn.addEventListener('click', function(e) {\n")
            .append("                e.preventDefault();\n")
            .append("                alert('Vous n\\'avez pas la permission de supprimer une matière.');\n")
            .append("                return false;\n")
            .append("            });\n")
            .append("        });\n")
            .append("    }\n")
            .append("\n")

            // Soumission du formulaire
            .append("    const form = document.getElementById('formMatiere');\n")
            .append("    if (form) {\n")
            .append("        form.addEventListener('submit', function(event) {\n")
            .append("            event.preventDefault();\n")
            .append("\n")
            .append("            const action = document.getElementById('formAction').value;\n")
            .append("            const code = document.getElementById('formCodeCours').value;\n")
            .append("            const nom = document.getElementById('formNomMatiere').value.trim();\n")
            .append("\n")
            .append("            // ✅ Contrôle permission côté client\n")
            .append("            if (!canModify) {\n")
            .append("                alert('Vous n\\'avez pas la permission d\\'enregistrer une matière.');\n")
            .append("                return;\n")
            .append("            }\n")
            .append("\n")
            .append("            const classe = document.getElementById('formClasseSelect').classList.contains('d-none')\n")
            .append("                ? document.getElementById('formClasseReadonly').value\n")
            .append("                : document.getElementById('formClasseSelect').value;\n")
            .append("            const annee = document.getElementById('formAnneeSelect').classList.contains('d-none')\n")
            .append("                ? document.getElementById('formAnneeReadonly').value\n")
            .append("                : document.getElementById('formAnneeSelect').value;\n")
            .append("            const periode = document.getElementById('formPeriodeSelect').classList.contains('d-none')\n")
            .append("                ? document.getElementById('formPeriodeReadonly').value\n")
            .append("                : document.getElementById('formPeriodeSelect').value;\n")
            .append("            const coefficient = document.getElementById('formCoefficient').value;\n")
            .append("            const credits = document.getElementById('formNombreCredits').value;\n")
            .append("            const duree = document.getElementById('formDureeCours').value;\n")
            .append("            const statut = document.getElementById('formStatut').value;\n")
            .append("            const noteMin = document.getElementById('formNoteMinimale').value;\n")
            .append("            const notePass = document.getElementById('formNotePassage').value;\n")
            .append("            const noteMax = document.getElementById('formNoteMaximale').value;\n")
            .append("\n")
            .append("            if (!nom || !classe || !annee || !periode) {\n")
            .append("                alert('Veuillez remplir tous les champs obligatoires (*)');\n")
            .append("                return;\n")
            .append("            }\n")
            .append("            if (parseFloat(noteMin) >= parseFloat(noteMax)) {\n")
            .append("                alert('La note minimale doit être inférieure à la note maximale.');\n")
            .append("                return;\n")
            .append("            }\n")
            .append("            if (parseFloat(notePass) < parseFloat(noteMin) || parseFloat(notePass) > parseFloat(noteMax)) {\n")
            .append("                alert('La note de passage doit être comprise entre la note minimale et la note maximale.');\n")
            .append("                return;\n")
            .append("            }\n")
            .append("\n")
            .append("            try { modalInstance.hide(); } catch (e) { console.warn(e); }\n")
            .append("\n")
            .append("            const btnSave = document.getElementById('btnSaveMatiere');\n")
            .append("            if (btnSave) btnSave.disabled = true;\n")
            .append("\n")
            .append("            const formData = new URLSearchParams();\n")
            // ✅ institutionId propagé dans le POST
            .append("            formData.append('institutionId', institutionId);\n")
            .append("            formData.append('action', action);\n")
            .append("            if (code) formData.append('codeCours', code);\n")
            .append("            formData.append('nomMatiere', nom);\n")
            .append("            formData.append(action === 'add' ? 'classeMatiereSelect' : 'classeMatiere', classe);\n")
            .append("            formData.append(action === 'add' ? 'anneeAcademiqueSelect' : 'anneeAcademique', annee);\n")
            .append("            formData.append(action === 'add' ? 'periodeSelect' : 'periode', periode);\n")
            .append("            formData.append('coefficient', coefficient);\n")
            .append("            formData.append('nombreCredits', credits);\n")
            .append("            formData.append('dureeCours', duree);\n")
            .append("            formData.append('statut', statut);\n")
            .append("            formData.append('noteMinimale', noteMin);\n")
            .append("            formData.append('notePassage', notePass);\n")
            .append("            formData.append('noteMaximale', noteMax);\n")
            .append("\n")
            .append("            const controller = new AbortController();\n")
            .append("            const timeoutId = setTimeout(function() { controller.abort(); }, 30000);\n")
            .append("\n")
            .append("            fetch('/admin/matieres', {\n")
            .append("                method: 'POST',\n")
            .append("                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },\n")
            .append("                body: formData.toString(),\n")
            .append("                redirect: 'manual',\n")
            .append("                signal: controller.signal\n")
            .append("            })\n")
            .append("            .then(function() {\n")
            .append("                clearTimeout(timeoutId);\n")
            // ✅ institutionId propagé dans la redirection
            .append("                window.location.href = '/admin/matieres?institutionId=' + encodeURIComponent(institutionId);\n")
            .append("            })\n")
            .append("            .catch(function(err) {\n")
            .append("                clearTimeout(timeoutId);\n")
            .append("                console.error('Erreur:', err);\n")
            .append("                window.location.href = '/admin/matieres?institutionId=' + encodeURIComponent(institutionId) + '&error=' + encodeURIComponent('Erreur réseau');\n")
            .append("            });\n")
            .append("        });\n")
            .append("    }\n")

            .append("});\n")
            .append("</script>\n");
    }

    // ============================================================
    // UTILITAIRES
    // ============================================================
    private static String urlEncode(String value) {
        if (value == null) return "";
        try {
            return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }

    private static String echapperHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }

    /**
     * Échappe une chaîne pour injection dans un littéral JS entre guillemets simples.
     */
    private static String echapperJs(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                    .replace("'", "\\'")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r");
    }
}