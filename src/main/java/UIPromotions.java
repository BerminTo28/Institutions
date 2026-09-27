import java.util.List;

/**
 * Interface Utilisateur pour la gestion des promotions.
 *
 * ✅ ARCHITECTURE MULTI-INSTITUTIONS : clé composite (institution_id, annee_academique, promotion).
 * ✅ CHARGEMENT DYNAMIQUE DES ANNÉES : le <select> du modal est rempli par AJAX.
 */
public class UIPromotions {

    private UIPromotions() {}

    public static String rendrePage(
            List<AnneeAcademique> anneesDisponibles,
            String institutionId,
            String successMessage,
            String errorMessage,
            boolean canCreate,
            boolean canModify,
            boolean canDelete) {

        if (anneesDisponibles == null) anneesDisponibles = List.of();
        if (institutionId == null || institutionId.isBlank()) institutionId = "INSTITUTION";
        if (successMessage == null) successMessage = "";
        if (errorMessage == null) errorMessage = "";

        StringBuilder sb = new StringBuilder();

        // ============================================================
        // HEAD HTML
        // ============================================================
        sb.append("<!DOCTYPE html>\n")
          .append("<html lang=\"fr\" class=\"h-100\">\n")
          .append("<head>\n")
          .append("    <meta charset=\"UTF-8\">\n")
          .append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n")
          .append("    <title>Gestion des Promotions</title>\n")
          .append("    <link href=\"https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css\" rel=\"stylesheet\">\n")
          .append("    <link href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css\" rel=\"stylesheet\">\n")
          .append("    <link href=\"https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700;800&display=swap\" rel=\"stylesheet\">\n")
          .append("    <style>\n")
          .append("        :root {\n")
          .append("            --primary: #4f46e5;\n")
          .append("            --primary-hover: #4338ca;\n")
          .append("            --primary-light: #eef2ff;\n")
          .append("            --success: #16a34a;\n")
          .append("            --danger: #dc2626;\n")
          .append("            --warning: #f59e0b;\n")
          .append("            --bg: #f1f5f9;\n")
          .append("            --card: #ffffff;\n")
          .append("            --text: #0f172a;\n")
          .append("            --text-muted: #64748b;\n")
          .append("            --border: #e2e8f0;\n")
          .append("            --shadow: 0 1px 3px rgba(0,0,0,0.06);\n")
          .append("            --shadow-lg: 0 10px 30px rgba(0,0,0,0.08);\n")
          .append("            --radius: 16px;\n")
          .append("            --radius-sm: 10px;\n")
          .append("        }\n")
          .append("        * { margin: 0; padding: 0; box-sizing: border-box; }\n")
          .append("        html, body { height: 100%; font-family: 'Inter', -apple-system, BlinkMacSystemFont, sans-serif; background: var(--bg); color: var(--text); }\n")
          .append("        .main-wrapper { height: 100vh; display: flex; flex-direction: column; padding: 20px; gap: 16px; overflow: hidden; }\n")
          .append("        \n")
          .append("        .header-glass {\n")
          .append("            background: linear-gradient(135deg, #4f46e5 0%, #3b82f6 100%);\n")
          .append("            border-radius: var(--radius);\n")
          .append("            padding: 18px 24px;\n")
          .append("            color: white;\n")
          .append("            display: flex;\n")
          .append("            justify-content: space-between;\n")
          .append("            align-items: center;\n")
          .append("            flex-shrink: 0;\n")
          .append("            box-shadow: 0 8px 25px rgba(79, 70, 229, 0.30);\n")
          .append("        }\n")
          .append("        .header-glass h4 { font-weight: 700; font-size: 1.2rem; margin: 0; letter-spacing: -0.3px; }\n")
          .append("        .header-glass h4 i { margin-right: 10px; opacity: 0.9; }\n")
          .append("        .header-glass .subtitle { font-size: 0.8rem; opacity: 0.8; margin: 2px 0 0 0; font-weight: 400; }\n")
          .append("        .header-glass .btn-back {\n")
          .append("            background: rgba(255,255,255,0.18);\n")
          .append("            border: 1px solid rgba(255,255,255,0.25);\n")
          .append("            color: white;\n")
          .append("            border-radius: 50px;\n")
          .append("            padding: 7px 18px;\n")
          .append("            font-size: 0.8rem;\n")
          .append("            font-weight: 500;\n")
          .append("            text-decoration: none;\n")
          .append("            transition: all 0.2s;\n")
          .append("            backdrop-filter: blur(8px);\n")
          .append("        }\n")
          .append("        .header-glass .btn-back:hover { background: rgba(255,255,255,0.30); color: white; transform: translateX(-3px); }\n")
          .append("        \n")
          .append("        .toolbar {\n")
          .append("            display: flex;\n")
          .append("            justify-content: space-between;\n")
          .append("            align-items: center;\n")
          .append("            flex-wrap: wrap;\n")
          .append("            gap: 12px;\n")
          .append("            flex-shrink: 0;\n")
          .append("        }\n")
          .append("        .toolbar-title { font-weight: 700; font-size: 0.95rem; color: var(--text); display: flex; align-items: center; gap: 8px; }\n")
          .append("        .toolbar-title i { color: var(--primary); }\n")
          .append("        .toolbar-actions { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; }\n")
          .append("        .toolbar-actions .form-control,\n")
          .append("        .toolbar-actions .form-select {\n")
          .append("            border-radius: var(--radius-sm);\n")
          .append("            border: 1px solid var(--border);\n")
          .append("            padding: 7px 14px;\n")
          .append("            font-size: 0.85rem;\n")
          .append("            transition: all 0.2s;\n")
          .append("            background: white;\n")
          .append("            height: 38px;\n")
          .append("        }\n")
          .append("        .toolbar-actions .form-control:focus,\n")
          .append("        .toolbar-actions .form-select:focus {\n")
          .append("            border-color: var(--primary);\n")
          .append("            box-shadow: 0 0 0 3px rgba(79, 70, 229, 0.12);\n")
          .append("            outline: none;\n")
          .append("        }\n")
          .append("        .toolbar-actions .form-control { width: 200px; }\n")
          .append("        .toolbar-actions .form-select { width: 160px; }\n")
          .append("        .btn-sm-custom {\n")
          .append("            height: 38px;\n")
          .append("            padding: 0 16px;\n")
          .append("            border-radius: var(--radius-sm);\n")
          .append("            font-size: 0.85rem;\n")
          .append("            font-weight: 500;\n")
          .append("            border: none;\n")
          .append("            transition: all 0.2s;\n")
          .append("            display: inline-flex;\n")
          .append("            align-items: center;\n")
          .append("            gap: 6px;\n")
          .append("        }\n")
          .append("        .btn-sm-custom.btn-primary { background: var(--primary); color: white; }\n")
          .append("        .btn-sm-custom.btn-primary:hover { background: var(--primary-hover); transform: translateY(-1px); box-shadow: 0 4px 12px rgba(79, 70, 229, 0.35); }\n")
          .append("        .btn-sm-custom.btn-success { background: var(--success); color: white; }\n")
          .append("        .btn-sm-custom.btn-success:hover { background: #15803d; transform: translateY(-1px); box-shadow: 0 4px 12px rgba(22, 163, 74, 0.35); }\n")
          .append("        .btn-sm-custom.btn-outline-secondary { background: transparent; color: var(--text-muted); border: 1px solid var(--border); }\n")
          .append("        .btn-sm-custom.btn-outline-secondary:hover { background: var(--bg); border-color: #cbd5e1; }\n")
          .append("        .btn-sm-custom.btn-outline-danger { background: transparent; color: var(--danger); border: 1px solid #fecaca; }\n")
          .append("        .btn-sm-custom.btn-outline-danger:hover { background: #fef2f2; border-color: var(--danger); }\n")
          .append("        \n")
          .append("        .table-container {\n")
          .append("            flex: 1;\n")
          .append("            min-height: 0;\n")
          .append("            display: flex;\n")
          .append("            flex-direction: column;\n")
          .append("            background: var(--card);\n")
          .append("            border-radius: var(--radius);\n")
          .append("            box-shadow: var(--shadow-lg);\n")
          .append("            overflow: hidden;\n")
          .append("        }\n")
          .append("        .table-scroll { flex: 1; overflow-y: auto; padding: 0; }\n")
          .append("        .table-scroll::-webkit-scrollbar { width: 6px; height: 6px; }\n")
          .append("        .table-scroll::-webkit-scrollbar-track { background: transparent; }\n")
          .append("        .table-scroll::-webkit-scrollbar-thumb { background: #cbd5e1; border-radius: 10px; }\n")
          .append("        .table-scroll::-webkit-scrollbar-thumb:hover { background: #94a3b8; }\n")
          .append("        .table-custom { width: 100%; border-collapse: collapse; font-size: 0.9rem; }\n")
          .append("        .table-custom thead {\n")
          .append("            position: sticky;\n")
          .append("            top: 0;\n")
          .append("            z-index: 10;\n")
          .append("            background: #f8fafc;\n")
          .append("            border-bottom: 2px solid var(--border);\n")
          .append("        }\n")
          .append("        .table-custom thead th {\n")
          .append("            padding: 14px 16px;\n")
          .append("            font-size: 0.7rem;\n")
          .append("            font-weight: 700;\n")
          .append("            text-transform: uppercase;\n")
          .append("            letter-spacing: 0.5px;\n")
          .append("            color: var(--text-muted);\n")
          .append("            text-align: left;\n")
          .append("            white-space: nowrap;\n")
          .append("        }\n")
          .append("        .table-custom thead th:last-child { text-align: center; }\n")
          .append("        .table-custom tbody tr { border-bottom: 1px solid var(--border); transition: background 0.15s; }\n")
          .append("        .table-custom tbody tr:hover { background: #fafbfc; }\n")
          .append("        .table-custom tbody tr:last-child { border-bottom: none; }\n")
          .append("        .table-custom tbody td { padding: 12px 16px; vertical-align: middle; }\n")
          .append("        .table-custom tbody td:last-child { text-align: center; }\n")
          .append("        .table-custom .badge-id {\n")
          .append("            background: var(--primary-light);\n")
          .append("            color: var(--primary);\n")
          .append("            font-weight: 600;\n")
          .append("            padding: 4px 12px;\n")
          .append("            border-radius: 50px;\n")
          .append("            font-size: 0.75rem;\n")
          .append("        }\n")
          .append("        .table-custom .badge-active {\n")
          .append("            background: #dcfce7;\n")
          .append("            color: #166534;\n")
          .append("            font-weight: 600;\n")
          .append("            padding: 5px 14px;\n")
          .append("            border-radius: 50px;\n")
          .append("            font-size: 0.75rem;\n")
          .append("            display: inline-flex;\n")
          .append("            align-items: center;\n")
          .append("            gap: 5px;\n")
          .append("        }\n")
          .append("        .table-custom .badge-inactive {\n")
          .append("            background: #f1f5f9;\n")
          .append("            color: var(--text-muted);\n")
          .append("            font-weight: 500;\n")
          .append("            padding: 5px 14px;\n")
          .append("            border-radius: 50px;\n")
          .append("            font-size: 0.75rem;\n")
          .append("        }\n")
          .append("        .table-custom .btn-action {\n")
          .append("            width: 32px;\n")
          .append("            height: 32px;\n")
          .append("            padding: 0;\n")
          .append("            border-radius: 8px;\n")
          .append("            border: 1px solid var(--border);\n")
          .append("            background: transparent;\n")
          .append("            color: var(--text-muted);\n")
          .append("            transition: all 0.2s;\n")
          .append("            display: inline-flex;\n")
          .append("            align-items: center;\n")
          .append("            justify-content: center;\n")
          .append("            font-size: 0.8rem;\n")
          .append("        }\n")
          .append("        .table-custom .btn-action:hover { background: var(--primary-light); color: var(--primary); border-color: var(--primary); }\n")
          .append("        .table-custom .btn-action.btn-edit:hover { background: #dbeafe; color: #2563eb; border-color: #93c5fd; }\n")
          .append("        .table-custom .btn-action.btn-activate:hover { background: #dcfce7; color: #16a34a; border-color: #86efac; }\n")
          .append("        .table-custom .btn-action.btn-delete:hover { background: #fee2e2; color: var(--danger); border-color: #fca5a5; }\n")
          .append("        .table-custom .btn-action + .btn-action { margin-left: 4px; }\n")
          .append("        .table-custom .empty-state { padding: 60px 20px; text-align: center; color: var(--text-muted); }\n")
          .append("        .table-custom .empty-state i { font-size: 3rem; color: #d1d5db; display: block; margin-bottom: 16px; }\n")
          .append("        .table-custom .empty-state strong { color: var(--text); }\n")
          .append("        \n")
          .append("        .modal-content { border-radius: var(--radius); border: none; box-shadow: var(--shadow-lg); }\n")
          .append("        .modal-header { border-bottom: none; padding: 24px 28px 8px 28px; }\n")
          .append("        .modal-header .modal-title { font-weight: 700; font-size: 1.15rem; color: var(--text); }\n")
          .append("        .modal-header .btn-close { padding: 8px; }\n")
          .append("        .modal-body { padding: 16px 28px 20px 28px; }\n")
          .append("        .modal-footer { border-top: none; padding: 8px 28px 24px 28px; gap: 10px; }\n")
          .append("        .modal-footer .btn { border-radius: var(--radius-sm); padding: 9px 24px; font-weight: 600; font-size: 0.9rem; }\n")
          .append("        .modal-footer .btn-primary { background: var(--primary); border: none; }\n")
          .append("        .modal-footer .btn-primary:hover { background: var(--primary-hover); }\n")
          .append("        .modal-footer .btn-secondary { background: var(--bg); border: none; color: var(--text); }\n")
          .append("        .modal-footer .btn-secondary:hover { background: #e2e8f0; }\n")
          .append("        .form-label { font-weight: 600; font-size: 0.85rem; color: var(--text); margin-bottom: 5px; }\n")
          .append("        .form-label .text-danger { color: var(--danger); }\n")
          .append("        .form-control, .form-select {\n")
          .append("            border-radius: var(--radius-sm);\n")
          .append("            border: 1px solid var(--border);\n")
          .append("            padding: 9px 14px;\n")
          .append("            font-size: 0.9rem;\n")
          .append("            transition: all 0.2s;\n")
          .append("        }\n")
          .append("        .form-control:focus, .form-select:focus {\n")
          .append("            border-color: var(--primary);\n")
          .append("            box-shadow: 0 0 0 3px rgba(79, 70, 229, 0.12);\n")
          .append("            outline: none;\n")
          .append("        }\n")
          .append("        .form-check-input:checked { background-color: var(--primary); border-color: var(--primary); }\n")
          .append("        .form-check-input:focus { box-shadow: 0 0 0 3px rgba(79, 70, 229, 0.15); border-color: var(--primary); }\n")
          .append("        \n")
          .append("        .toast-container { position: fixed; bottom: 30px; right: 30px; z-index: 9999; display: flex; flex-direction: column; gap: 10px; }\n")
          .append("        .toast-custom {\n")
          .append("            padding: 14px 24px;\n")
          .append("            border-radius: var(--radius-sm);\n")
          .append("            font-weight: 500;\n")
          .append("            font-size: 0.9rem;\n")
          .append("            color: white;\n")
          .append("            box-shadow: 0 8px 25px rgba(0,0,0,0.15);\n")
          .append("            animation: slideUp 0.4s ease forwards;\n")
          .append("            min-width: 280px;\n")
          .append("            max-width: 420px;\n")
          .append("        }\n")
          .append("        .toast-custom.success { background: var(--success); }\n")
          .append("        .toast-custom.error { background: var(--danger); }\n")
          .append("        .toast-custom.info { background: var(--primary); }\n")
          .append("        @keyframes slideUp {\n")
          .append("            0% { opacity: 0; transform: translateY(20px) scale(0.96); }\n")
          .append("            100% { opacity: 1; transform: translateY(0) scale(1); }\n")
          .append("        }\n")
          .append("        .toast-custom.removing { animation: slideDown 0.3s ease forwards; }\n")
          .append("        @keyframes slideDown {\n")
          .append("            0% { opacity: 1; transform: translateY(0) scale(1); }\n")
          .append("            100% { opacity: 0; transform: translateY(20px) scale(0.96); }\n")
          .append("        }\n")
          .append("        \n")
          .append("        @media (max-width: 992px) {\n")
          .append("            .main-wrapper { padding: 12px; gap: 12px; }\n")
          .append("            .header-glass { flex-direction: column; align-items: flex-start; gap: 10px; padding: 16px 20px; }\n")
          .append("            .header-glass .btn-back { align-self: flex-start; }\n")
          .append("        }\n")
          .append("        @media (max-width: 768px) {\n")
          .append("            .toolbar { flex-direction: column; align-items: stretch; gap: 10px; }\n")
          .append("            .toolbar-actions { flex-wrap: wrap; }\n")
          .append("            .toolbar-actions .form-control { width: 100%; }\n")
          .append("            .toolbar-actions .form-select { width: 100%; }\n")
          .append("            .toolbar-actions .btn-sm-custom { flex: 1; justify-content: center; }\n")
          .append("            .table-custom thead th { font-size: 0.6rem; padding: 10px 10px; }\n")
          .append("            .table-custom tbody td { padding: 10px 10px; font-size: 0.8rem; }\n")
          .append("            .modal-body { padding: 12px 16px 16px 16px; }\n")
          .append("            .modal-footer { padding: 8px 16px 16px 16px; flex-wrap: wrap; }\n")
          .append("            .modal-footer .btn { flex: 1; }\n")
          .append("            .toast-custom { min-width: unset; width: 100%; }\n")
          .append("            .toast-container { left: 16px; right: 16px; bottom: 16px; }\n")
          .append("        }\n")
          .append("        @media (max-width: 480px) {\n")
          .append("            .header-glass h4 { font-size: 1rem; }\n")
          .append("            .table-custom thead th:nth-child(3),\n")
          .append("            .table-custom thead th:nth-child(4),\n")
          .append("            .table-custom tbody td:nth-child(4),\n")
          .append("            .table-custom tbody td:nth-child(5) { display: none; }\n")
          .append("        }\n")
          .append("    </style>\n")
          .append("</head>\n")
          .append("<body>\n")
          .append("<div class=\"main-wrapper\">\n");

        // ============================================================
        // HEADER
        // ============================================================
        sb.append("    <header class=\"header-glass\">\n")
          .append("        <div>\n")
          .append("            <h4><i class=\"fa-solid fa-graduation-cap\"></i>Gestion des Promotions</h4>\n")
          .append("            <p class=\"subtitle\">").append(echapperHtml(institutionId)).append("</p>\n")
          .append("        </div>\n")
          .append("        <a href=\"/admin/parametres\" class=\"btn-back\">\n")
          .append("            <i class=\"fa-solid fa-arrow-left me-1\"></i> Paramètres\n")
          .append("        </a>\n")
          .append("    </header>\n");

        // ============================================================
        // ALERTS
        // ============================================================
        if (!errorMessage.isEmpty()) {
            sb.append("    <div class=\"alert alert-danger alert-dismissible fade show border-0 shadow-sm rounded-3 py-2 mb-0\" role=\"alert\" style=\"flex-shrink:0;\">\n")
              .append("        <i class=\"fa-solid fa-circle-exclamation me-2\"></i>").append(echapperHtml(errorMessage)).append("\n")
              .append("        <button type=\"button\" class=\"btn-close py-2\" data-bs-dismiss=\"alert\"></button>\n")
              .append("    </div>\n");
        }
        if (!successMessage.isEmpty()) {
            sb.append("    <div class=\"alert alert-success alert-dismissible fade show border-0 shadow-sm rounded-3 py-2 mb-0\" role=\"alert\" style=\"flex-shrink:0;\">\n")
              .append("        <i class=\"fa-solid fa-circle-check me-2\"></i>").append(echapperHtml(successMessage)).append("\n")
              .append("        <button type=\"button\" class=\"btn-close py-2\" data-bs-dismiss=\"alert\"></button>\n")
              .append("    </div>\n");
        }

        // ============================================================
        // TOOLBAR (avec filtre principal)
        // ============================================================
        sb.append("    <div class=\"toolbar\">\n")
          .append("        <div class=\"toolbar-title\"><i class=\"fa-solid fa-list\"></i>Liste des promotions</div>\n")
          .append("        <div class=\"toolbar-actions\">\n")
          .append("            <input type=\"text\" id=\"inputRecherche\" placeholder=\"Rechercher...\" class=\"form-control form-control-sm\">\n")
          .append("            <select id=\"inputFiltreAnnee\" class=\"form-select form-select-sm\">\n")
          .append("                <option value=\"\">Toutes les années</option>\n");
        // ✅ Filtre principal : peut rester statique (rechargé par la page)
        for (AnneeAcademique annee : anneesDisponibles) {
            String anneeValue = annee.getAnneeAcademique();
            sb.append("                <option value=\"").append(echapperHtml(anneeValue)).append("\">")
              .append(echapperHtml(anneeValue)).append("</option>\n");
        }
        sb.append("            </select>\n")
          .append("            <button class=\"btn-sm-custom btn-primary\" id=\"btnRechercher\"><i class=\"fa-solid fa-search\"></i></button>\n")
          .append("            <button class=\"btn-sm-custom btn-outline-secondary\" id=\"btnReinitialiser\" title=\"Réinitialiser les filtres\"><i class=\"fa-solid fa-rotate-left\"></i></button>\n");
        if (canCreate) {
            sb.append("            <button class=\"btn-sm-custom btn-success\" id=\"btnAjouter\"><i class=\"fa-solid fa-plus\"></i> Ajouter</button>\n");
        }
        sb.append("        </div>\n")
          .append("    </div>\n");

        // ============================================================
        // TABLE
        // ============================================================
        sb.append("    <div class=\"table-container\">\n")
          .append("        <div class=\"table-scroll\">\n")
          .append("            <table class=\"table-custom\">\n")
          .append("                <thead>\n")
          .append("                    <tr>\n")
          .append("                        <th style=\"width:60px;\">#</th>\n")
          .append("                        <th>Nom Promotion</th>\n")
          .append("                        <th>Année Académique</th>\n")
          .append("                        <th>Date Début</th>\n")
          .append("                        <th>Date Fin</th>\n")
          .append("                        <th>Statut</th>\n")
          .append("                        <th style=\"width:130px;text-align:center;\">Actions</th>\n")
          .append("                    </tr>\n")
          .append("                </thead>\n")
          .append("                <tbody id=\"promotionsBody\">\n")
          .append("                    <tr><td colspan=\"7\">\n")
          .append("                        <div class=\"empty-state\">\n")
          .append("                            <i class=\"fa-solid fa-spinner fa-spin\"></i>\n")
          .append("                            <span>Chargement des promotions...</span>\n")
          .append("                        </div>\n")
          .append("                    </td></tr>\n")
          .append("                </tbody>\n")
          .append("            </table>\n")
          .append("        </div>\n")
          .append("    </div>\n");

        sb.append("</div>\n");

        // ============================================================
        // MODAL (avec <select> VIDE pour AJAX)
        // ============================================================
        sb.append("""
        <div class="modal fade" id="promotionModal" tabindex="-1" aria-hidden="true">
            <div class="modal-dialog modal-dialog-centered">
                <div class="modal-content">
                    <div class="modal-header">
                        <h5 class="modal-title" id="modalTitle"><i class="fa-solid fa-pen-to-square me-2"></i>Ajouter une promotion</h5>
                        <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                    </div>
                    <form id="promotionForm" novalidate>
                        <div class="modal-body">
                            <input type="hidden" id="editOldAnnee">
                            <input type="hidden" id="editOldPromotion">
                            <div class="mb-3">
                                <label for="inputPromotion" class="form-label">Libellé <span class="text-danger">*</span></label>
                                <input type="text" class="form-control" id="inputPromotion" placeholder="Ex: Promotion 2025-2029" required>
                            </div>
                            <div class="mb-3">
                                <label for="inputAnnee" class="form-label">
                                    Année Académique <span class="text-danger">*</span>
                                    <span id="anneeLoading" style="font-size:0.75rem; color:var(--text-muted); display:none;">
                                        <i class="fa-solid fa-spinner fa-spin"></i> Chargement...
                                    </span>
                                </label>
                                <!-- ✅ SELECT VIDE : options injectées par AJAX -->
                                <select class="form-select" id="inputAnnee" required>
                                    <option value="">-- Sélectionner --</option>
                                </select>
                            </div>
                            <div class="row">
                                <div class="col-md-6 mb-3">
                                    <label for="inputDateDebut" class="form-label">Date Début <span class="text-danger">*</span></label>
                                    <input type="date" class="form-control" id="inputDateDebut" required>
                                </div>
                                <div class="col-md-6 mb-3">
                                    <label for="inputDateFin" class="form-label">Date Fin <span class="text-danger">*</span></label>
                                    <input type="date" class="form-control" id="inputDateFin" required>
                                </div>
                            </div>
                            <div class="mb-0">
                                <div class="form-check">
                                    <input class="form-check-input" type="checkbox" id="inputEstActive">
                                    <label class="form-check-label fw-semibold" for="inputEstActive">
                                        Définir comme promotion active
                                    </label>
                                </div>
                            </div>
                        </div>
                        <div class="modal-footer">
                            <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Annuler</button>
                            <button type="submit" class="btn btn-primary" id="btnSavePromotion">
                                <i class="fa-solid fa-save me-1"></i>Enregistrer
                            </button>
                        </div>
                    </form>
                </div>
            </div>
        </div>
        """);

        // ============================================================
        // TOAST CONTAINER
        // ============================================================
        sb.append("    <div class=\"toast-container\" id=\"toastContainer\"></div>\n");

        // ============================================================
        // JAVASCRIPT
        // ============================================================
        sb.append("<script src=\"https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js\"></script>\n");
        sb.append("<script>\n");
        sb.append("const API_BASE = '/admin/parametres/promotions/api/promotions';\n");
        sb.append("const API_ANNEES = '/admin/parametres/promotions/api/annees';\n");
        sb.append("const modal = new bootstrap.Modal(document.getElementById('promotionModal'));\n");
        sb.append("const form = document.getElementById('promotionForm');\n");
        sb.append("const tbody = document.getElementById('promotionsBody');\n");
        sb.append("const canModify = ").append(canModify).append(";\n");
        sb.append("const canDelete = ").append(canDelete).append(";\n");

        sb.append("""
        // ============================================================
        // UTILITAIRES
        // ============================================================
        function showToast(message, type) {
            const container = document.getElementById('toastContainer');
            const div = document.createElement('div');
            div.className = 'toast-custom ' + type;
            div.textContent = message;
            container.appendChild(div);
            setTimeout(() => {
                div.classList.add('removing');
                setTimeout(() => div.remove(), 300);
            }, 3000);
        }

        function escapeHtml(str) {
            if (!str) return '';
            return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
        }

        /**
         * ✅ Construit la clé URL depuis annee + promotion (encodée).
         */
        function buildCleUrl(annee, promotion) {
            return encodeURIComponent(annee || '') + '/' + encodeURIComponent(promotion || '');
        }

        // ============================================================
        // ✅ CHARGEMENT DYNAMIQUE DES ANNÉES POUR LE MODAL
        // ============================================================
        function chargerAnneesPourModal(callback) {
            console.log('📅 Chargement des années pour le modal...');
            const select = document.getElementById('inputAnnee');
            const loading = document.getElementById('anneeLoading');
            const currentValue = select.value;

            if (loading) loading.style.display = 'inline-block';

            fetch(API_ANNEES, {
                method: 'GET',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'same-origin'
            })
            .then(res => {
                if (!res.ok) throw new Error('Erreur HTTP: ' + res.status);
                return res.json();
            })
            .then(data => {
                if (loading) loading.style.display = 'none';

                if (data.success) {
                    select.innerHTML = '<option value="">-- Sélectionner --</option>';
                    data.data.forEach(a => {
                        const opt = document.createElement('option');
                        opt.value = a.anneeAcademique;
                        opt.textContent = a.anneeAcademique;
                        select.appendChild(opt);
                    });
                    // Restaurer la sélection si elle existe
                    if (currentValue) {
                        select.value = currentValue;
                    }
                    console.log('✅ Années chargées :', data.data.length);
                } else {
                    console.warn('⚠️ Erreur chargement années :', data.error);
                    showToast('Impossible de charger les années : ' + (data.error || 'inconnue'), 'error');
                }
                if (callback) callback();
            })
            .catch(err => {
                if (loading) loading.style.display = 'none';
                console.error('❌ Erreur réseau chargement années :', err);
                showToast('Erreur réseau lors du chargement des années', 'error');
                if (callback) callback();
            });
        }

        // ============================================================
        // CHARGEMENT DES PROMOTIONS
        // ============================================================
        function chargerPromotions(filtre, motCle) {
            let url = API_BASE;
            const params = new URLSearchParams();
            if (filtre) params.append('annee', filtre);
            if (motCle) params.append('q', motCle);
            if (params.toString()) url += '?' + params.toString();

            fetch(url, {
                method: 'GET',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'same-origin'
            })
            .then(res => {
                if (!res.ok) throw new Error('Erreur HTTP: ' + res.status);
                return res.json();
            })
            .then(data => {
                if (data.success) {
                    afficherTableau(data.data);
                } else {
                    tbody.innerHTML = `
                        <tr><td colspan="7">
                            <div class="empty-state">
                                <i class="fa-solid fa-triangle-exclamation"></i>
                                <span>Erreur: ${escapeHtml(data.error || 'inconnue')}</span>
                            </div>
                        </td></tr>`;
                    showToast('Erreur chargement: ' + (data.error || 'inconnue'), 'error');
                }
            })
            .catch(err => {
                console.error(err);
                tbody.innerHTML = `
                    <tr><td colspan="7">
                        <div class="empty-state">
                            <i class="fa-solid fa-wifi"></i>
                            <span>Erreur réseau: ${escapeHtml(err.message)}</span>
                        </div>
                    </td></tr>`;
                showToast('Erreur réseau: ' + err.message, 'error');
            });
        }

        // ============================================================
        // AFFICHAGE DU TABLEAU
        // ============================================================
        function afficherTableau(promotions) {
            if (!promotions || promotions.length === 0) {
                tbody.innerHTML = `
                    <tr><td colspan="7">
                        <div class="empty-state">
                            <i class="fa-solid fa-inbox"></i>
                            <span>Aucune promotion enregistrée.<br><strong>Cliquez sur "Ajouter"</strong> pour en créer une.</span>
                        </div>
                    </td></tr>`;
                return;
            }

            let html = '';
            promotions.forEach((p, index) => {
                const estActive = p.estActive === true;
                const statutHtml = estActive
                    ? '<span class="badge-active"><i class="fa-solid fa-check-circle"></i>Active</span>'
                    : '<span class="badge-inactive">Inactive</span>';

                const anneeEsc = escapeHtml(p.anneeAcademique || '');
                const promotionEsc = escapeHtml(p.promotion || '');

                let actionsHtml = '';
                if (canModify) {
                    actionsHtml += `<button class="btn-action btn-edit" data-annee="${anneeEsc}" data-promotion="${promotionEsc}" title="Modifier"><i class="fa-solid fa-pen"></i></button>`;
                    actionsHtml += `<button class="btn-action btn-activate" data-annee="${anneeEsc}" data-promotion="${promotionEsc}" title="Activer"><i class="fa-solid fa-check-double"></i></button>`;
                }
                if (canDelete) {
                    actionsHtml += `<button class="btn-action btn-delete" data-annee="${anneeEsc}" data-promotion="${promotionEsc}" title="Supprimer"><i class="fa-solid fa-trash"></i></button>`;
                }

                html += `
                    <tr>
                        <td><span class="badge-id">${index + 1}</span></td>
                        <td><strong>${promotionEsc}</strong></td>
                        <td>${anneeEsc}</td>
                        <td>${escapeHtml(p.dateDebut || '-')}</td>
                        <td>${escapeHtml(p.dateFin || '-')}</td>
                        <td>${statutHtml}</td>
                        <td>${actionsHtml}</td>
                    </tr>`;
            });
            tbody.innerHTML = html;

            document.querySelectorAll('.btn-edit').forEach(btn => {
                btn.addEventListener('click', function() {
                    ouvrirEdition(this.dataset.annee, this.dataset.promotion);
                });
            });
            document.querySelectorAll('.btn-activate').forEach(btn => {
                btn.addEventListener('click', function() {
                    const annee = this.dataset.annee;
                    const promotion = this.dataset.promotion;
                    if (confirm('Définir la promotion "' + promotion + '" comme active ?')) {
                        activerPromotion(annee, promotion);
                    }
                });
            });
            document.querySelectorAll('.btn-delete').forEach(btn => {
                btn.addEventListener('click', function() {
                    const annee = this.dataset.annee;
                    const promotion = this.dataset.promotion;
                    if (confirm('Supprimer la promotion "' + promotion + '" ?')) {
                        supprimerPromotion(annee, promotion);
                    }
                });
            });
        }

        // ============================================================
        // ÉDITION (avec chargement dynamique des années)
        // ============================================================
        function ouvrirEdition(annee, promotion) {
            const url = API_BASE + '/' + buildCleUrl(annee, promotion);

            fetch(url, {
                method: 'GET',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'same-origin'
            })
            .then(res => {
                if (!res.ok) throw new Error('Erreur HTTP: ' + res.status);
                return res.json();
            })
            .then(data => {
                if (data.success) {
                    const p = data.data;
                    document.getElementById('editOldAnnee').value = p.anneeAcademique || '';
                    document.getElementById('editOldPromotion').value = p.promotion || '';
                    document.getElementById('inputPromotion').value = p.promotion || '';
                    document.getElementById('inputDateDebut').value = p.dateDebut || '';
                    document.getElementById('inputDateFin').value = p.dateFin || '';
                    document.getElementById('inputEstActive').checked = !!p.estActive;
                    document.getElementById('modalTitle').innerHTML =
                        '<i class="fa-solid fa-pen-to-square me-2"></i>Modifier la promotion';

                    // ✅ Charger les années PUIS sélectionner celle de la promotion
                    chargerAnneesPourModal(function() {
                        document.getElementById('inputAnnee').value = p.anneeAcademique || '';
                        modal.show();
                    });
                } else {
                    showToast('Erreur: ' + (data.error || 'inconnue'), 'error');
                }
            })
            .catch(err => {
                console.error(err);
                showToast('Erreur réseau: ' + err.message, 'error');
            });
        }

        // ============================================================
        // RÉINITIALISATION DU FORMULAIRE
        // ============================================================
        function reinitialiserFormulaire() {
            document.getElementById('editOldAnnee').value = '';
            document.getElementById('editOldPromotion').value = '';
            document.getElementById('inputPromotion').value = '';
            document.getElementById('inputAnnee').value = '';
            document.getElementById('inputDateDebut').value = '';
            document.getElementById('inputDateFin').value = '';
            document.getElementById('inputEstActive').checked = false;
            document.getElementById('modalTitle').innerHTML =
                '<i class="fa-solid fa-plus-circle me-2"></i>Ajouter une promotion';
        }

        // ============================================================
        // SAUVEGARDE
        // ============================================================
        function sauvegarderPromotion(event) {
            event.preventDefault();
            const oldAnnee = document.getElementById('editOldAnnee').value;
            const oldPromotion = document.getElementById('editOldPromotion').value;
            const promotion = document.getElementById('inputPromotion').value.trim();
            const anneeAcademique = document.getElementById('inputAnnee').value;
            const dateDebut = document.getElementById('inputDateDebut').value;
            const dateFin = document.getElementById('inputDateFin').value;
            const estActive = document.getElementById('inputEstActive').checked;

            if (!promotion || !anneeAcademique || !dateDebut || !dateFin) {
                showToast('Tous les champs obligatoires (*) doivent être remplis.', 'error');
                return;
            }

            const payload = { promotion, anneeAcademique, dateDebut, dateFin, estActive };
            const isEdit = oldAnnee && oldPromotion;
            const method = isEdit ? 'PUT' : 'POST';
            const url = isEdit
                ? API_BASE + '/' + buildCleUrl(oldAnnee, oldPromotion)
                : API_BASE;

            fetch(url, {
                method: method,
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload),
                credentials: 'same-origin'
            })
            .then(res => {
                if (!res.ok) throw new Error('Erreur HTTP: ' + res.status);
                return res.json();
            })
            .then(data => {
                if (data.success) {
                    modal.hide();
                    showToast(data.message || 'Opération réussie', 'success');
                    reinitialiserFormulaire();
                    chargerPromotions(
                        document.getElementById('inputFiltreAnnee').value,
                        document.getElementById('inputRecherche').value
                    );
                } else {
                    showToast('Erreur: ' + (data.error || 'inconnue'), 'error');
                }
            })
            .catch(err => {
                console.error(err);
                showToast('Erreur réseau: ' + err.message, 'error');
            });
        }

        // ============================================================
        // ACTIVATION
        // ============================================================
        function activerPromotion(annee, promotion) {
            const url = API_BASE + '/' + buildCleUrl(annee, promotion) + '/activate';

            fetch(url, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'same-origin'
            })
            .then(res => {
                if (!res.ok) throw new Error('Erreur HTTP: ' + res.status);
                return res.json();
            })
            .then(data => {
                if (data.success) {
                    showToast('Promotion définie comme active', 'success');
                    chargerPromotions(
                        document.getElementById('inputFiltreAnnee').value,
                        document.getElementById('inputRecherche').value
                    );
                } else {
                    showToast('Erreur: ' + (data.error || 'inconnue'), 'error');
                }
            })
            .catch(err => {
                console.error(err);
                showToast('Erreur réseau: ' + err.message, 'error');
            });
        }

        // ============================================================
        // SUPPRESSION
        // ============================================================
        function supprimerPromotion(annee, promotion) {
            const url = API_BASE + '/' + buildCleUrl(annee, promotion);

            fetch(url, {
                method: 'DELETE',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'same-origin'
            })
            .then(res => {
                if (!res.ok) throw new Error('Erreur HTTP: ' + res.status);
                return res.json();
            })
            .then(data => {
                if (data.success) {
                    showToast('Promotion supprimée', 'success');
                    chargerPromotions(
                        document.getElementById('inputFiltreAnnee').value,
                        document.getElementById('inputRecherche').value
                    );
                } else {
                    showToast('Erreur: ' + (data.error || 'inconnue'), 'error');
                }
            })
            .catch(err => {
                console.error(err);
                showToast('Erreur réseau: ' + err.message, 'error');
            });
        }

        // ============================================================
        // ÉCOUTEURS
        // ============================================================
        document.getElementById('btnAjouter')?.addEventListener('click', function() {
            reinitialiserFormulaire();
            // ✅ Charger les années AVANT d'ouvrir le modal
            chargerAnneesPourModal(function() {
                modal.show();
            });
        });

        document.getElementById('btnRechercher').addEventListener('click', function() {
            chargerPromotions(
                document.getElementById('inputFiltreAnnee').value,
                document.getElementById('inputRecherche').value
            );
        });

        document.getElementById('btnReinitialiser').addEventListener('click', function() {
            document.getElementById('inputRecherche').value = '';
            document.getElementById('inputFiltreAnnee').value = '';
            chargerPromotions('', '');
        });

        document.getElementById('inputRecherche').addEventListener('keypress', function(e) {
            if (e.key === 'Enter') document.getElementById('btnRechercher').click();
        });

        document.getElementById('inputFiltreAnnee').addEventListener('change', function() {
            document.getElementById('btnRechercher').click();
        });

        form.addEventListener('submit', sauvegarderPromotion);

        // Chargement initial
        chargerPromotions('', '');
        """);
        sb.append("</script>\n");
        sb.append("</body>\n");
        sb.append("</html>");

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
}