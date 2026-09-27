import java.util.List;
import java.util.Map;

public class UINotes {

    public static String rendrePage(
            List<String> annees,
            List<String> periodes,
            List<String> classes,
            List<String> matieres,
            List<Map<String, Object>> etudiants,
            String institutionId,
            String messageErreur,
            String messageSucces,
            boolean canUpdate) {
        return rendrePage(annees, periodes, classes, matieres, etudiants,
                institutionId, messageErreur, messageSucces, canUpdate,
                "", "", "", "");
    }

    /**
     * ✅ Version avec filtres actifs pré-remplis.
     */
    public static String rendrePage(
            List<String> annees,
            List<String> periodes,
            List<String> classes,
            List<String> matieres,
            List<Map<String, Object>> etudiants,
            String institutionId,
            String messageErreur,
            String messageSucces,
            boolean canUpdate,
            String anneeFiltre,
            String periodeFiltre,
            String classeFiltre,
            String matiereFiltre) {

        if (annees == null) annees = List.of();
        if (periodes == null) periodes = List.of();
        if (classes == null) classes = List.of();
        if (matieres == null) matieres = List.of();
        if (etudiants == null) etudiants = List.of();
        if (messageErreur == null) messageErreur = "";
        if (messageSucces == null) messageSucces = "";
        if (anneeFiltre == null) anneeFiltre = "";
        if (periodeFiltre == null) periodeFiltre = "";
        if (classeFiltre == null) classeFiltre = "";
        if (matiereFiltre == null) matiereFiltre = "";

        StringBuilder sb = new StringBuilder(48_000);

        appendHead(sb);
        appendStyles(sb);
        sb.append("<body>\n<div class=\"main-wrapper\">\n");

        appendHeader(sb);
        appendMessages(sb, messageErreur, messageSucces);
        appendFiltres(sb, annees, periodes, classes, matieres,
                anneeFiltre, periodeFiltre, classeFiltre, matiereFiltre);
        appendTableau(sb, etudiants, canUpdate);
        appendActionBar(sb, canUpdate);
        sb.append("</div>\n");

        appendScripts(sb, canUpdate);

        sb.append("</body>\n</html>");
        return sb.toString();
    }

    // ============================================================
    // HEAD
    // ============================================================
    private static void appendHead(StringBuilder sb) {
        sb.append("<!DOCTYPE html>\n")
          .append("<html lang=\"fr\" class=\"h-100\">\n")
          .append("<head>\n")
          .append("    <meta charset=\"UTF-8\">\n")
          .append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, viewport-fit=cover\">\n")
          .append("    <meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge\">\n")
          .append("    <title>Gestion des Notes</title>\n")
          .append("    <link href=\"https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap\" rel=\"stylesheet\">\n")
          .append("    <link href=\"https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css\" rel=\"stylesheet\">\n")
          .append("    <link href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css\" rel=\"stylesheet\">\n");
    }

    // ============================================================
    // STYLES
    // ============================================================
    private static void appendStyles(StringBuilder sb) {
        sb.append("    <style>\n")
          .append("        :root {\n")
          .append("            --primary: #4f46e5;\n")
          .append("            --primary-hover: #4338ca;\n")
          .append("            --bg-body: #f1f5f9;\n")
          .append("            --card-shadow: 0 4px 12px -4px rgba(0, 0, 0, 0.06);\n")
          .append("            --safe-top: env(safe-area-inset-top, 0px);\n")
          .append("            --safe-bottom: env(safe-area-inset-bottom, 0px);\n")
          .append("            --safe-left: env(safe-area-inset-left, 0px);\n")
          .append("            --safe-right: env(safe-area-inset-right, 0px);\n")
          .append("        }\n")
          .append("        *, *::before, *::after { box-sizing: border-box; }\n")
          .append("        html, body {\n")
          .append("            width: 100%; min-width: 320px;\n")
          .append("            height: 100vh; height: 100dvh;\n")
          .append("            min-height: 100vh; min-height: 100dvh;\n")
          .append("            margin: 0; padding: 0; overflow: hidden;\n")
          .append("            font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;\n")
          .append("            background-color: var(--bg-body);\n")
          .append("            color: #1e293b;\n")
          .append("            -webkit-text-size-adjust: 100%;\n")
          .append("            -webkit-tap-highlight-color: transparent;\n")
          .append("            overscroll-behavior: none;\n")
          .append("        }\n")
          .append("        .main-wrapper {\n")
          .append("            width: 100%; height: 100vh; height: 100dvh;\n")
          .append("            display: flex; flex-direction: column;\n")
          .append("            padding: 8px;\n")
          .append("            padding-top: calc(8px + var(--safe-top));\n")
          .append("            padding-bottom: calc(8px + var(--safe-bottom));\n")
          .append("            padding-left: calc(8px + var(--safe-left));\n")
          .append("            padding-right: calc(8px + var(--safe-right));\n")
          .append("            margin: 0; gap: 8px;\n")
          .append("        }\n")
          .append("        .glass-header {\n")
          .append("            background: linear-gradient(135deg, #4f46e5 0%, #3b82f6 100%);\n")
          .append("            color: white; border-radius: 12px;\n")
          .append("            padding: 10px 18px;\n")
          .append("            box-shadow: 0 8px 20px -8px rgba(79, 70, 229, 0.35);\n")
          .append("            flex-shrink: 0;\n")
          .append("            display: flex; justify-content: space-between; align-items: center;\n")
          .append("            gap: 12px; min-height: 52px;\n")
          .append("        }\n")
          .append("        .glass-header h1 {\n")
          .append("            font-size: 1.15rem; font-weight: 700; margin: 0;\n")
          .append("            display: flex; align-items: center; gap: 0.5rem;\n")
          .append("        }\n")
          .append("        .glass-header .subtitle { opacity: 0.85; font-size: 0.78rem; margin: 0; }\n")
          .append("        .btn-glass {\n")
          .append("            background: rgba(255,255,255,0.15);\n")
          .append("            border: 1px solid rgba(255,255,255,0.25);\n")
          .append("            color: white; border-radius: 50px;\n")
          .append("            padding: 0.35rem 0.9rem; font-weight: 500;\n")
          .append("            text-decoration: none; transition: 0.15s;\n")
          .append("            font-size: 0.82rem; white-space: nowrap;\n")
          .append("            display: inline-flex; align-items: center;\n")
          .append("        }\n")
          .append("        .btn-glass:hover { background: rgba(255,255,255,0.25); color: white; }\n")
          .append("        .filters-bar {\n")
          .append("            background: #ffffff; border-radius: 10px;\n")
          .append("            padding: 8px 12px; box-shadow: var(--card-shadow);\n")
          .append("            border: 1px solid #e2e8f0; flex-shrink: 0;\n")
          .append("        }\n")
          .append("        .filters-bar .form-select {\n")
          .append("            border-radius: 8px; border: 1px solid #e2e8f0;\n")
          .append("            padding: 0.35rem 0.65rem; font-size: 0.82rem;\n")
          .append("            background: #f8fafc; height: 34px; transition: 0.15s;\n")
          .append("        }\n")
          .append("        .filters-bar .form-select:focus {\n")
          .append("            border-color: var(--primary);\n")
          .append("            box-shadow: 0 0 0 3px rgba(79, 70, 229, 0.12);\n")
          .append("            background: white;\n")
          .append("        }\n")
          .append("        .filters-bar .form-select:disabled {\n")
          .append("            background: #f1f5f9; opacity: 0.6; cursor: not-allowed;\n")
          .append("        }\n")
          .append("        .btn-primary-sm {\n")
          .append("            background: var(--primary); border: none;\n")
          .append("            border-radius: 8px; padding: 0.35rem 0.9rem;\n")
          .append("            font-weight: 600; font-size: 0.82rem; color: white;\n")
          .append("            height: 34px; transition: 0.15s; white-space: nowrap;\n")
          .append("            display: inline-flex; align-items: center; gap: 6px;\n")
          .append("        }\n")
          .append("        .btn-primary-sm:hover { background: var(--primary-hover); color: white; }\n")
          .append("        .table-container {\n")
          .append("            flex: 1; min-height: 0; background: #ffffff;\n")
          .append("            border-radius: 10px; box-shadow: var(--card-shadow);\n")
          .append("            border: 1px solid #e2e8f0;\n")
          .append("            display: flex; flex-direction: column; overflow: hidden;\n")
          .append("        }\n")
          .append("        .table-scroll { flex: 1; overflow: auto; }\n")
          .append("        .table-custom {\n")
          .append("            margin-bottom: 0; min-width: 800px; width: 100%;\n")
          .append("            border-collapse: collapse;\n")
          .append("        }\n")
          .append("        .table-custom thead {\n")
          .append("            position: sticky; top: 0; z-index: 10;\n")
          .append("            background-color: #f8fafc;\n")
          .append("        }\n")
          .append("        .table-custom th {\n")
          .append("            padding: 0.6rem 0.8rem; font-weight: 600;\n")
          .append("            font-size: 0.68rem; text-transform: uppercase;\n")
          .append("            letter-spacing: 0.4px; color: #64748b;\n")
          .append("            text-align: left; white-space: nowrap;\n")
          .append("            border-bottom: 2px solid #e2e8f0;\n")
          .append("        }\n")
          .append("        .table-custom td {\n")
          .append("            padding: 0.5rem 0.8rem;\n")
          .append("            border-bottom: 1px solid #f1f5f9;\n")
          .append("            vertical-align: middle; font-size: 0.82rem;\n")
          .append("        }\n")
          .append("        .table-custom tbody tr:hover { background: #f8fafc; }\n")
          .append("        .badge-soft-primary {\n")
          .append("            background-color: #e0e7ff; color: #4338ca;\n")
          .append("            font-weight: 600; border-radius: 6px;\n")
          .append("            padding: 0.25rem 0.6rem; font-size: 0.72rem;\n")
          .append("        }\n")
          .append("        .input-note {\n")
          .append("            max-width: 100px; margin: auto;\n")
          .append("            text-align: center; font-weight: 600;\n")
          .append("            font-size: 0.82rem; border-radius: 8px;\n")
          .append("            border: 1px solid #e2e8f0;\n")
          .append("            padding: 0.35rem; height: 32px;\n")
          .append("        }\n")
          .append("        .input-note:focus {\n")
          .append("            border-color: var(--primary);\n")
          .append("            box-shadow: 0 0 0 3px rgba(79, 70, 229, 0.12);\n")
          .append("            outline: none;\n")
          .append("        }\n")
          .append("        .readonly-field {\n")
          .append("            background-color: #f1f5f9;\n")
          .append("            cursor: not-allowed; opacity: 0.7;\n")
          .append("        }\n")
          .append("        .action-bar {\n")
          .append("            padding: 0.6rem 1rem; background: #f8fafc;\n")
          .append("            border-top: 1px solid #e2e8f0;\n")
          .append("            display: flex; justify-content: flex-end;\n")
          .append("            gap: 0.5rem; flex-shrink: 0;\n")
          .append("        }\n")
          .append("        .btn-action {\n")
          .append("            border-radius: 8px; padding: 0.45rem 1.1rem;\n")
          .append("            font-weight: 600; font-size: 0.82rem; border: none;\n")
          .append("            transition: 0.15s; text-decoration: none;\n")
          .append("            display: inline-flex; align-items: center; gap: 0.4rem;\n")
          .append("            height: 34px; cursor: pointer;\n")
          .append("        }\n")
          .append("        .btn-save { background: #22c55e; color: white; }\n")
          .append("        .btn-save:hover { background: #16a34a; color: white; }\n")
          .append("        .btn-save:disabled { opacity: 0.6; cursor: not-allowed; }\n")
          .append("        .btn-reset { background: #e2e8f0; color: #475569; }\n")
          .append("        .btn-reset:hover { background: #cbd5e1; color: #1e293b; }\n")
          .append("        .toast-container {\n")
          .append("            position: fixed;\n")
          .append("            bottom: calc(20px + var(--safe-bottom));\n")
          .append("            right: calc(20px + var(--safe-right));\n")
          .append("            z-index: 1100;\n")
          .append("        }\n")
          .append("        .toast-custom {\n")
          .append("            background: #0f172a; color: white;\n")
          .append("            padding: 12px 20px; border-radius: 12px;\n")
          .append("            box-shadow: 0 10px 25px -5px rgba(0,0,0,0.2);\n")
          .append("            font-weight: 500; font-size: 13.5px;\n")
          .append("            animation: fadeInOut 4s ease;\n")
          .append("            margin-top: 8px; max-width: 420px;\n")
          .append("            word-wrap: break-word;\n")
          .append("        }\n")
          .append("        .toast-custom.success { background: #16a34a; }\n")
          .append("        .toast-custom.error { background: #dc2626; }\n")
          .append("        .toast-custom.warning { background: #f59e0b; }\n")
          .append("        @keyframes fadeInOut {\n")
          .append("            0%   { opacity: 0; transform: translateY(20px); }\n")
          .append("            15%  { opacity: 1; transform: translateY(0); }\n")
          .append("            85%  { opacity: 1; }\n")
          .append("            100% { opacity: 0; transform: translateY(20px); }\n")
          .append("        }\n")
          .append("        @media (min-width: 1920px) {\n")
          .append("            .main-wrapper { padding: 12px; gap: 12px; }\n")
          .append("            .glass-header { padding: 14px 24px; }\n")
          .append("            .glass-header h1 { font-size: 1.35rem; }\n")
          .append("            .filters-bar { padding: 10px 16px; }\n")
          .append("            .table-custom th { font-size: 0.72rem; padding: 0.7rem 0.9rem; }\n")
          .append("            .table-custom td { font-size: 0.88rem; padding: 0.6rem 0.9rem; }\n")
          .append("            .table-custom { min-width: 1000px; }\n")
          .append("            .input-note { max-width: 120px; height: 36px; font-size: 0.88rem; }\n")
          .append("        }\n")
          .append("        @media (max-width: 768px) {\n")
          .append("            html, body { overflow: auto; height: auto; min-height: 100dvh; }\n")
          .append("            .main-wrapper {\n")
          .append("                height: auto; min-height: 100dvh;\n")
          .append("                overflow: visible; padding: 6px; gap: 6px;\n")
          .append("            }\n")
          .append("            .glass-header { padding: 8px 12px; border-radius: 10px; }\n")
          .append("            .glass-header h1 { font-size: 1rem; }\n")
          .append("            .glass-header .subtitle { display: none; }\n")
          .append("            .btn-glass { font-size: 0.72rem; padding: 0.25rem 0.7rem; }\n")
          .append("            .filters-bar { padding: 8px; border-radius: 8px; }\n")
          .append("            .filters-bar .row { display: flex; flex-direction: column; gap: 6px; }\n")
          .append("            .filters-bar .col-auto { width: 100%; }\n")
          .append("            .filters-bar .form-select { width: 100%; }\n")
          .append("            .filters-bar .btn-primary-sm { width: 100%; justify-content: center; }\n")
          .append("            .table-container { height: auto; max-height: none; border-radius: 8px; }\n")
          .append("            .table-scroll { max-height: 65vh; overflow: auto; -webkit-overflow-scrolling: touch; }\n")
          .append("            .table-custom { min-width: 700px; }\n")
          .append("            .table-custom td, .table-custom th { padding: 0.4rem 0.5rem; font-size: 0.72rem; }\n")
          .append("            .input-note { max-width: 80px; height: 30px; font-size: 0.78rem; }\n")
          .append("            .action-bar { padding: 0.5rem 0.7rem; border-radius: 0 0 8px 8px; flex-wrap: wrap; }\n")
          .append("            .btn-action { padding: 0.4rem 0.8rem; font-size: 0.75rem; height: 32px; flex: 1; justify-content: center; }\n")
          .append("            .toast-container { bottom: calc(12px + var(--safe-bottom)); right: calc(12px + var(--safe-right)); left: calc(12px + var(--safe-left)); }\n")
          .append("            .toast-custom { padding: 10px 14px; font-size: 12.5px; }\n")
          .append("        }\n")
          .append("        @media (max-width: 400px) {\n")
          .append("            .glass-header h1 { font-size: 0.9rem; }\n")
          .append("            .table-custom { min-width: 620px; }\n")
          .append("            .table-custom td, .table-custom th { padding: 0.3rem 0.4rem; font-size: 0.68rem; }\n")
          .append("            .input-note { max-width: 66px; height: 28px; }\n")
          .append("            .badge-soft-primary { font-size: 0.62rem; padding: 0.15rem 0.45rem; }\n")
          .append("        }\n")
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
    private static void appendHeader(StringBuilder sb) {
        sb.append("    <div class=\"glass-header\">\n")
          .append("        <div>\n")
          .append("            <h1><i class=\"fa-solid fa-file-pen\"></i> Saisie & Gestion des Notes</h1>\n")
          .append("            <p class=\"subtitle\">Sélectionnez les filtres pour charger la grille de saisie</p>\n")
          .append("        </div>\n")
          .append("        <div>\n")
          .append("            <a href=\"/dashboard\" class=\"btn-glass\"><i class=\"fa-solid fa-arrow-left me-1\"></i> Tableau de bord</a>\n")
          .append("        </div>\n")
          .append("    </div>\n");
    }

    // ============================================================
    // MESSAGES
    // ============================================================
    private static void appendMessages(StringBuilder sb, String erreur, String succes) {
        if (!erreur.isEmpty()) {
            sb.append("    <div class=\"alert alert-danger alert-dismissible fade show border-0 shadow-sm rounded-3 mb-0 py-2\" role=\"alert\">\n")
              .append("        <i class=\"fa-solid fa-circle-exclamation me-2\"></i>")
              .append(echapperHtml(erreur))
              .append("\n        <button type=\"button\" class=\"btn-close py-2\" data-bs-dismiss=\"alert\"></button>\n")
              .append("    </div>\n");
        }
        if (!succes.isEmpty()) {
            sb.append("    <div class=\"alert alert-success alert-dismissible fade show border-0 shadow-sm rounded-3 mb-0 py-2\" role=\"alert\">\n")
              .append("        <i class=\"fa-solid fa-circle-check me-2\"></i>")
              .append(echapperHtml(succes))
              .append("\n        <button type=\"button\" class=\"btn-close py-2\" data-bs-dismiss=\"alert\"></button>\n")
              .append("    </div>\n");
        }
    }

    // ============================================================
    // FILTRES
    // ============================================================
    private static void appendFiltres(StringBuilder sb,
                                       List<String> annees,
                                       List<String> periodes,
                                       List<String> classes,
                                       List<String> matieres,
                                       String anneeFiltre,
                                       String periodeFiltre,
                                       String classeFiltre,
                                       String matiereFiltre) {
        sb.append("    <div class=\"filters-bar\">\n")
          .append("        <div class=\"row g-2 align-items-center\">\n");

        appendSelect(sb, "anneeSelect", "Année", annees, anneeFiltre);
        appendSelect(sb, "periodeSelect", "Période", periodes, periodeFiltre);
        appendSelect(sb, "classeSelect", "Classe", classes, classeFiltre);
        appendSelect(sb, "matiereSelect", "Matière", matieres, matiereFiltre);

        sb.append("            <div class=\"col-auto\">\n")
          .append("                <button class=\"btn-primary-sm\" id=\"btnCharger\"><i class=\"fa-solid fa-rotate me-1\"></i> Charger</button>\n")
          .append("            </div>\n")
          .append("        </div>\n")
          .append("    </div>\n");
    }

    /** ✅ Génère un <select> avec l'option active marquée `selected`. */
    private static void appendSelect(StringBuilder sb, String id, String placeholder,
                                       List<String> values, String selectedValue) {
        sb.append("            <div class=\"col-auto\">\n")
          .append("                <select id=\"").append(id).append("\" class=\"form-select form-select-sm\">\n")
          .append("                    <option value=\"\">").append(echapperHtml(placeholder)).append("</option>\n");
        for (String v : values) {
            sb.append("                    <option value=\"").append(echapperHtml(v)).append("\"");
            if (v.equals(selectedValue)) sb.append(" selected");
            sb.append(">").append(echapperHtml(v)).append("</option>\n");
        }
        sb.append("                </select>\n")
          .append("            </div>\n");
    }

    // ============================================================
    // TABLEAU
    // ============================================================
    private static void appendTableau(StringBuilder sb,
                                       List<Map<String, Object>> etudiants,
                                       boolean canUpdate) {
        sb.append("    <div class=\"table-container\">\n")
          .append("        <div class=\"table-scroll\">\n")
          .append("            <form id=\"notesForm\" onsubmit=\"return false;\">\n")
          .append("                <table class=\"table-custom\">\n")
          .append("                    <thead>\n")
          .append("                        <tr>\n")
          .append("                            <th>Code Étudiant</th>\n")
          .append("                            <th>Nom</th>\n")
          .append("                            <th>Prénom</th>\n")
          .append("                            <th class=\"text-center\" style=\"width: 150px;\">Note</th>\n")
          .append("                        </tr>\n")
          .append("                    </thead>\n")
          .append("                    <tbody id=\"etudiantsBody\">\n");

        if (etudiants.isEmpty()) {
            sb.append("                        <tr>\n")
              .append("                            <td colspan=\"4\" class=\"text-center py-5 text-muted\">\n")
              .append("                                <i class=\"fa-solid fa-user-graduate fs-1 mb-3 text-black-50 d-block\"></i>\n")
              .append("                                <span>Veuillez sélectionner les 4 filtres ci-dessus puis cliquer sur <strong>\"Charger\"</strong>.</span>\n")
              .append("                            </td>\n")
              .append("                        </tr>\n");
        } else {
            for (Map<String, Object> e : etudiants) {
                String id = (String) e.get("id");
                String nom = (String) e.get("nom");
                String prenom = (String) e.get("prenom");
                String note = e.get("note") != null ? e.get("note").toString() : "";
                String readonlyAttr = canUpdate ? "" : " readonly class=\"input-note readonly-field\"";

                sb.append("                        <tr data-id=\"").append(echapperHtml(id)).append("\">\n")
                  .append("                            <td><span class=\"badge-soft-primary\">").append(echapperHtml(id)).append("</span></td>\n")
                  .append("                            <td class=\"fw-semibold text-dark\">").append(echapperHtml(nom)).append("</td>\n")
                  .append("                            <td class=\"text-secondary\">").append(echapperHtml(prenom)).append("</td>\n")
                  .append("                            <td class=\"text-center\">\n")
                  .append("                                <input type=\"number\" step=\"0.1\" min=\"0\" max=\"100\" name=\"note_").append(echapperHtml(id)).append("\" value=\"").append(echapperHtml(note)).append("\" class=\"input-note\" placeholder=\"Note\" ").append(readonlyAttr).append(">\n")
                  .append("                            </td>\n")
                  .append("                        </tr>\n");
            }
        }

        sb.append("                    </tbody>\n")
          .append("                </table>\n")
          .append("            </form>\n")
          .append("        </div>\n");
    }

    // ============================================================
    // ACTION BAR
    // ============================================================
    private static void appendActionBar(StringBuilder sb, boolean canUpdate) {
        sb.append("        <div class=\"action-bar\">\n");
        if (canUpdate) {
            sb.append("            <button class=\"btn-action btn-save\" id=\"btnEnregistrer\">\n")
              .append("                <i class=\"fa-solid fa-floppy-disk\"></i> Enregistrer\n")
              .append("            </button>\n");
        } else {
            sb.append("            <div class=\"text-muted\" style=\"font-size:0.8rem;display:flex;align-items:center;gap:0.4rem;\">\n")
              .append("                <i class=\"fa-solid fa-info-circle\"></i> Mode lecture seule\n")
              .append("            </div>\n");
        }
        sb.append("            <button class=\"btn-action btn-reset\" onclick=\"window.location.href='/admin/notes'\">\n")
          .append("                <i class=\"fa-solid fa-rotate-left\"></i> Réinitialiser\n")
          .append("            </button>\n")
          .append("        </div>\n")
          .append("    </div>\n");
    }

    // ============================================================
    // SCRIPTS
    // ============================================================
    private static void appendScripts(StringBuilder sb, boolean canUpdate) {
        sb.append("<div class=\"toast-container\" id=\"toastContainer\"></div>\n");
        sb.append("<script src=\"https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js\"></script>\n");
        sb.append("<script>\n")
          .append("(function() {\n")
          .append("    'use strict';\n")
          .append("    const CAN_UPDATE = ").append(canUpdate).append(";\n")
          .append("    const TIMEOUT_MS = 20000;\n")
          .append("    let enregistrementEnCours = false;\n")
          .append("    let chargementEnCours = false;\n")
          .append("\n")

          // Éléments DOM
          .append("    const anneeSelect    = document.getElementById('anneeSelect');\n")
          .append("    const periodeSelect  = document.getElementById('periodeSelect');\n")
          .append("    const classeSelect   = document.getElementById('classeSelect');\n")
          .append("    const matiereSelect  = document.getElementById('matiereSelect');\n")
          .append("    const btnCharger     = document.getElementById('btnCharger');\n")
          .append("    const tbody          = document.getElementById('etudiantsBody');\n")
          .append("    const btnEnregistrer = CAN_UPDATE ? document.getElementById('btnEnregistrer') : null;\n")
          .append("\n")

          // ---------- TOAST ----------
          .append("    function showToast(message, type) {\n")
          .append("        const container = document.getElementById('toastContainer');\n")
          .append("        if (!container) return;\n")
          .append("        const div = document.createElement('div');\n")
          .append("        div.className = 'toast-custom ' + (type || 'info');\n")
          .append("        div.textContent = message;\n")
          .append("        container.appendChild(div);\n")
          .append("        setTimeout(function() { div.remove(); }, 5000);\n")
          .append("    }\n")
          .append("\n")

          // ---------- Fetch avec timeout ----------
          .append("    function fetchAvecTimeout(url, options, timeoutMs) {\n")
          .append("        const controller = new AbortController();\n")
          .append("        const timer = setTimeout(function() { controller.abort(); }, timeoutMs || TIMEOUT_MS);\n")
          .append("        const opts = Object.assign({}, options || {}, { signal: controller.signal });\n")
          .append("        return fetch(url, opts).finally(function() { clearTimeout(timer); });\n")
          .append("    }\n")
          .append("\n")

          // ---------- escapeHtml ----------
          .append("    function escapeHtml(text) {\n")
          .append("        if (text === null || text === undefined) return '';\n")
          .append("        const div = document.createElement('div');\n")
          .append("        div.textContent = text;\n")
          .append("        return div.innerHTML;\n")
          .append("    }\n")
          .append("\n")

          // ---------- Création d'une ligne étudiant ----------
          .append("    function construireLigneEtudiant(e) {\n")
          .append("        const tr = document.createElement('tr');\n")
          .append("        tr.dataset.id = e.id;\n")
          .append("        const noteVal = (e.note !== null && e.note !== undefined) ? e.note : '';\n")
          .append("        const readonlyAttr = CAN_UPDATE ? '' : ' readonly';\n")
          .append("        const readonlyClass = CAN_UPDATE ? '' : ' readonly-field';\n")
          .append("        tr.innerHTML = `\n")
          .append("            <td><span class=\"badge-soft-primary\">${escapeHtml(e.id)}</span></td>\n")
          .append("            <td class=\"fw-semibold text-dark\">${escapeHtml(e.nom)}</td>\n")
          .append("            <td class=\"text-secondary\">${escapeHtml(e.prenom)}</td>\n")
          .append("            <td class=\"text-center\">\n")
          .append("                <input type=\"number\" step=\"0.1\" min=\"0\" max=\"100\"\n")
          .append("                       name=\"note_${escapeHtml(e.id)}\"\n")
          .append("                       value=\"${escapeHtml(noteVal)}\"\n")
          .append("                       class=\"input-note${readonlyClass}\"\n")
          .append("                       placeholder=\"Note\"${readonlyAttr}>\n")
          .append("            </td>`;\n")
          .append("        return tr;\n")
          .append("    }\n")
          .append("\n")

          // ---------- Chargement des étudiants ----------
          .append("    function chargerEtudiants() {\n")
          .append("        if (chargementEnCours) return;\n")
          .append("\n")
          .append("        const annee   = anneeSelect.value;\n")
          .append("        const periode = periodeSelect.value;\n")
          .append("        const classe  = classeSelect.value;\n")
          .append("        const matiere = matiereSelect.value;\n")
          .append("\n")
          .append("        if (!annee || !periode || !classe || !matiere) {\n")
          .append("            tbody.innerHTML = `\n")
          .append("                <tr>\n")
          .append("                    <td colspan=\"4\" class=\"text-center py-5 text-muted\">\n")
          .append("                        <i class=\"fa-solid fa-circle-info fs-1 mb-3 text-primary d-block\"></i>\n")
          .append("                        <span>Veuillez sélectionner <strong>l'année, la période, la classe et la matière</strong> pour charger la grille.</span>\n")
          .append("                    </td>\n")
          .append("                </tr>`;\n")
          .append("            return;\n")
          .append("        }\n")
          .append("\n")
          .append("        chargementEnCours = true;\n")
          .append("        btnCharger.disabled = true;\n")
          .append("        tbody.innerHTML = `\n")
          .append("            <tr>\n")
          .append("                <td colspan=\"4\" class=\"text-center py-5 text-muted\">\n")
          .append("                    <div class=\"spinner-border text-primary mb-2\" role=\"status\"></div>\n")
          .append("                    <p class=\"mb-0\" style=\"font-size:0.85rem;\">Chargement des étudiants...</p>\n")
          .append("                </td>\n")
          .append("            </tr>`;\n")
          .append("\n")
          .append("        const params = new URLSearchParams({\n")
          .append("            annee: annee, periode: periode, classe: classe, matiere: matiere\n")
          .append("        });\n")
          .append("\n")
          .append("        fetchAvecTimeout('/admin/notes/api/etudiants?' + params, {\n")
          .append("            headers: { 'Accept': 'application/json' }\n")
          .append("        })\n")
          .append("        .then(res => {\n")
          .append("            if (res.redirected) throw new Error('Session expirée. Rechargez la page.');\n")
          .append("            if (!res.ok) throw new Error('HTTP ' + res.status);\n")
          .append("            return res.json();\n")
          .append("        })\n")
          .append("        .then(data => {\n")
          .append("            if (!data.success) {\n")
          .append("                showToast('Erreur : ' + (data.error || 'inconnue'), 'error');\n")
          .append("                tbody.innerHTML = `\n")
          .append("                    <tr><td colspan=\"4\" class=\"text-center py-4 text-danger\">\n")
          .append("                        <i class=\"fa-solid fa-triangle-exclamation me-1\"></i> ${escapeHtml(data.error || 'Erreur')}\n")
          .append("                    </td></tr>`;\n")
          .append("                return;\n")
          .append("            }\n")
          .append("\n")
          .append("            tbody.innerHTML = '';\n")
          .append("            if (!data.etudiants || data.etudiants.length === 0) {\n")
          .append("                tbody.innerHTML = `\n")
          .append("                    <tr><td colspan=\"4\" class=\"text-center py-5 text-muted\">\n")
          .append("                        <i class=\"fa-solid fa-folder-open fs-1 mb-3 text-black-50 d-block\"></i>\n")
          .append("                        <span>Aucun étudiant trouvé pour ces critères.</span>\n")
          .append("                    </td></tr>`;\n")
          .append("                return;\n")
          .append("            }\n")
          .append("\n")
          .append("            const fragment = document.createDocumentFragment();\n")
          .append("            data.etudiants.forEach(e => fragment.appendChild(construireLigneEtudiant(e)));\n")
          .append("            tbody.appendChild(fragment);\n")
          .append("        })\n")
          .append("        .catch(err => {\n")
          .append("            console.error(err);\n")
          .append("            if (err.name === 'AbortError') {\n")
          .append("                showToast('Délai dépassé — le serveur ne répond pas', 'error');\n")
          .append("            } else {\n")
          .append("                showToast(err.message || 'Erreur réseau', 'error');\n")
          .append("            }\n")
          .append("        })\n")
          .append("        .finally(() => {\n")
          .append("            chargementEnCours = false;\n")
          .append("            btnCharger.disabled = false;\n")
          .append("        });\n")
          .append("    }\n")
          .append("\n")

          // ---------- Enregistrement ----------
          .append("    function enregistrerNotes() {\n")
          .append("        if (enregistrementEnCours) return;\n")
          .append("        if (!CAN_UPDATE) return;\n")
          .append("\n")
          .append("        // ✅ Vérifier que les filtres sont tous présents\n")
          .append("        const annee   = anneeSelect.value;\n")
          .append("        const periode = periodeSelect.value;\n")
          .append("        const classe  = classeSelect.value;\n")
          .append("        const matiere = matiereSelect.value;\n")
          .append("\n")
          .append("        if (!annee || !periode || !classe || !matiere) {\n")
          .append("            showToast('Veuillez sélectionner tous les filtres avant d\\'enregistrer', 'error');\n")
          .append("            return;\n")
          .append("        }\n")
          .append("\n")
          .append("        const rows = tbody.querySelectorAll('tr[data-id]');\n")
          .append("        if (rows.length === 0) {\n")
          .append("            showToast('Aucun étudiant chargé. Cliquez sur Charger d\\'abord.', 'error');\n")
          .append("            return;\n")
          .append("        }\n")
          .append("\n")
          .append("        const notes = [];\n")
          .append("        rows.forEach(tr => {\n")
          .append("            const id = tr.dataset.id;\n")
          .append("            const input = tr.querySelector('input[type=\"number\"]');\n")
          .append("            if (!input || !id) return;\n")
          .append("            const note = input.value;\n")
          .append("            if (note !== '' && note !== null) {\n")
          .append("                const n = parseFloat(note);\n")
          .append("                if (!isNaN(n)) notes.push({ id: id, note: n });\n")
          .append("            }\n")
          .append("        });\n")
          .append("\n")
          .append("        if (notes.length === 0) {\n")
          .append("            showToast('Aucune note à enregistrer.', 'warning');\n")
          .append("            return;\n")
          .append("        }\n")
          .append("\n")
          .append("        const payload = {\n")
          .append("            annee: annee, periode: periode, classe: classe, matiere: matiere,\n")
          .append("            notes: notes\n")
          .append("        };\n")
          .append("\n")
          .append("        enregistrementEnCours = true;\n")
          .append("        btnEnregistrer.disabled = true;\n")
          .append("        btnEnregistrer.innerHTML = '<i class=\"fa-solid fa-spinner fa-spin\"></i> Enregistrement...';\n")
          .append("\n")
          .append("        fetchAvecTimeout('/admin/notes/api/save', {\n")
          .append("            method: 'POST',\n")
          .append("            headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },\n")
          .append("            body: JSON.stringify(payload)\n")
          .append("        })\n")
          .append("        .then(res => {\n")
          .append("            if (res.redirected) throw new Error('Session expirée. Rechargez la page.');\n")
          .append("            if (!res.ok) throw new Error('HTTP ' + res.status);\n")
          .append("            return res.json();\n")
          .append("        })\n")
          .append("        .then(data => {\n")
          .append("            if (data.success) {\n")
          .append("                if (data.errors && data.errors.length > 0) {\n")
          .append("                    showToast('⚠️ ' + (data.message || 'Certaines notes ont échoué'), 'warning');\n")
          .append("                    data.errors.forEach(e => showToast(e, 'error'));\n")
          .append("                } else {\n")
          .append("                    showToast(data.message || 'Notes enregistrées avec succès', 'success');\n")
          .append("                }\n")
          .append("                chargerEtudiants();\n")
          .append("            } else {\n")
          .append("                showToast('Erreur : ' + (data.error || 'inconnue'), 'error');\n")
          .append("            }\n")
          .append("        })\n")
          .append("        .catch(err => {\n")
          .append("            console.error(err);\n")
          .append("            if (err.name === 'AbortError') {\n")
          .append("                showToast('Délai dépassé — le serveur ne répond pas', 'error');\n")
          .append("            } else {\n")
          .append("                showToast(err.message || 'Erreur réseau', 'error');\n")
          .append("            }\n")
          .append("        })\n")
          .append("        .finally(() => {\n")
          .append("            enregistrementEnCours = false;\n")
          .append("            btnEnregistrer.disabled = false;\n")
          .append("            btnEnregistrer.innerHTML = '<i class=\"fa-solid fa-floppy-disk\"></i> Enregistrer';\n")
          .append("        });\n")
          .append("    }\n")
          .append("\n")

          // ---------- Listeners ----------
          .append("    btnCharger.addEventListener('click', chargerEtudiants);\n")
          .append("\n")
          // ✅ Recharger automatiquement quand un filtre change (évite la désynchronisation)
          .append("    [anneeSelect, periodeSelect, classeSelect, matiereSelect].forEach(sel => {\n")
          .append("        sel.addEventListener('change', function() {\n")
          .append("            const annee   = anneeSelect.value;\n")
          .append("            const periode = periodeSelect.value;\n")
          .append("            const classe  = classeSelect.value;\n")
          .append("            const matiere = matiereSelect.value;\n")
          .append("            if (annee && periode && classe && matiere) {\n")
          .append("                chargerEtudiants();\n")
          .append("            }\n")
          .append("        });\n")
          .append("    });\n")
          .append("\n")
          .append("    if (btnEnregistrer) {\n")
          .append("        btnEnregistrer.addEventListener('click', enregistrerNotes);\n")
          .append("    }\n")
          .append("\n")

          // ✅ Auto-charger si les filtres sont déjà tous présents (rechargement de page)
          .append("    document.addEventListener('DOMContentLoaded', function() {\n")
          .append("        const annee   = anneeSelect.value;\n")
          .append("        const periode = periodeSelect.value;\n")
          .append("        const classe  = classeSelect.value;\n")
          .append("        const matiere = matiereSelect.value;\n")
          .append("        if (annee && periode && classe && matiere && tbody.querySelectorAll('tr[data-id]').length === 0) {\n")
          .append("            chargerEtudiants();\n")
          .append("        }\n")
          .append("    });\n")

          .append("})();\n")
          .append("</script>\n");
    }

    // ============================================================
    // UTILITAIRES
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