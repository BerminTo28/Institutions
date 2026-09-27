import java.util.ArrayList;
import java.util.List;

public class UIProfesseur {

    public static String rendrePage(
            List<Professeur> professeurs,
            List<Matiere> matieres,
            String motCle,
            String institutionId,
            String messageErreur,
            boolean canCreate,
            boolean canUpdate,
            boolean canDelete) {

        if (professeurs == null) professeurs = new ArrayList<>();
        if (matieres == null) matieres = new ArrayList<>();
        if (motCle == null) motCle = "";
        if (institutionId == null) institutionId = "ADMIN12345";
        if (messageErreur == null) messageErreur = "";

        StringBuilder sb = new StringBuilder();

        // ===== HEAD =====
        sb.append("<!DOCTYPE html>\n")
          .append("<html lang=\"fr\">\n")
          .append("<head>\n")
          .append("    <meta charset=\"UTF-8\">\n")
          .append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, viewport-fit=cover\">\n")
          .append("    <meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge\">\n")
          .append("    <title>Gestion des Professeurs</title>\n")
          .append("    <link href=\"https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap\" rel=\"stylesheet\">\n")
          .append("    <link href=\"https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css\" rel=\"stylesheet\">\n")
          .append("    <link href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css\" rel=\"stylesheet\">\n")
          .append("    <style>\n")

          // ---------- ROOT ----------
          .append("        :root {\n")
          .append("            --primary:#4f46e5;\n")
          .append("            --primary-hover:#4338ca;\n")
          .append("            --bg-body:#f1f5f9;\n")
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
          .append("            background-color: var(--bg-body);\n")
          .append("            color: #1e293b;\n")
          .append("            -webkit-text-size-adjust: 100%;\n")
          .append("            -webkit-tap-highlight-color: transparent;\n")
          .append("            overscroll-behavior: none;\n")
          .append("        }\n")

          // ---------- MAIN WRAPPER ----------
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
          .append("        .btn-glass:hover { background: rgba(255,255,255,0.25); color: white; transform: translateY(-1px); }\n")

          // ---------- FILTRES ----------
          .append("        .filters-bar {\n")
          .append("            background: #ffffff;\n")
          .append("            border-radius: 10px;\n")
          .append("            padding: 8px 12px;\n")
          .append("            box-shadow: 0 4px 12px -4px rgba(0,0,0,0.06);\n")
          .append("            border: 1px solid #e2e8f0;\n")
          .append("            flex-shrink: 0;\n")
          .append("        }\n")
          .append("        .filters-bar .form-control {\n")
          .append("            border-radius: 8px;\n")
          .append("            border: 1px solid #e2e8f0;\n")
          .append("            padding: 0.35rem 0.65rem;\n")
          .append("            font-size: 0.82rem;\n")
          .append("            background: #f8fafc;\n")
          .append("            height: 34px;\n")
          .append("            transition: 0.15s;\n")
          .append("        }\n")
          .append("        .filters-bar .form-control:focus {\n")
          .append("            border-color: var(--primary);\n")
          .append("            box-shadow: 0 0 0 3px rgba(79,70,229,0.12);\n")
          .append("            background: white;\n")
          .append("            outline: none;\n")
          .append("        }\n")
          .append("        .btn-primary-sm {\n")
          .append("            background: var(--primary);\n")
          .append("            border: none;\n")
          .append("            border-radius: 8px;\n")
          .append("            padding: 0.35rem 0.9rem;\n")
          .append("            font-weight: 600;\n")
          .append("            font-size: 0.82rem;\n")
          .append("            color: white;\n")
          .append("            height: 34px;\n")
          .append("            white-space: nowrap;\n")
          .append("            display: inline-flex;\n")
          .append("            align-items: center;\n")
          .append("            gap: 6px;\n")
          .append("            transition: 0.15s;\n")
          .append("        }\n")
          .append("        .btn-primary-sm:hover { background: var(--primary-hover); color: white; }\n")
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
          .append("            text-decoration: none;\n")
          .append("            transition: 0.15s;\n")
          .append("        }\n")
          .append("        .btn-outline-sm:hover { background: #f1f5f9; color: var(--primary); }\n")

          // ---------- TABLEAU ----------
          .append("        .table-container {\n")
          .append("            flex: 1;\n")
          .append("            min-height: 0;\n")
          .append("            background: #ffffff;\n")
          .append("            border-radius: 10px;\n")
          .append("            box-shadow: 0 4px 12px -4px rgba(0,0,0,0.06);\n")
          .append("            border: 1px solid #e2e8f0;\n")
          .append("            display: flex;\n")
          .append("            flex-direction: column;\n")
          .append("            overflow: hidden;\n")
          .append("        }\n")
          .append("        .table-scroll {\n")
          .append("            flex: 1;\n")
          .append("            overflow-y: auto;\n")
          .append("            overflow-x: auto;\n")
          .append("            -webkit-overflow-scrolling: touch;\n")
          .append("        }\n")
          .append("        .table-custom {\n")
          .append("            margin-bottom: 0;\n")
          .append("            min-width: 900px;\n")
          .append("            width: 100%;\n")
          .append("            border-collapse: collapse;\n")
          .append("        }\n")
          .append("        .table-custom thead {\n")
          .append("            position: sticky;\n")
          .append("            top: 0;\n")
          .append("            z-index: 10;\n")
          .append("            background: #f8fafc;\n")
          .append("        }\n")
          .append("        .table-custom th {\n")
          .append("            padding: 0.6rem 0.8rem;\n")
          .append("            font-weight: 600;\n")
          .append("            font-size: 0.66rem;\n")
          .append("            text-transform: uppercase;\n")
          .append("            letter-spacing: 0.4px;\n")
          .append("            color: #64748b;\n")
          .append("            text-align: left;\n")
          .append("            white-space: nowrap;\n")
          .append("            border-bottom: 2px solid #e2e8f0;\n")
          .append("        }\n")
          .append("        .table-custom td {\n")
          .append("            padding: 0.5rem 0.8rem;\n")
          .append("            border-bottom: 1px solid #f1f5f9;\n")
          .append("            vertical-align: middle;\n")
          .append("            font-size: 0.82rem;\n")
          .append("        }\n")
          .append("        .table-custom tbody tr { transition: 0.1s; }\n")
          .append("        .table-custom tbody tr:hover { background: #f8fafc; }\n")
          .append("        .table-custom tbody tr:last-child td { border-bottom: none; }\n")
          .append("        .col-select { width: 50px; text-align: center; }\n")
          .append("        .badge-soft-primary {\n")
          .append("            background: #e0e7ff;\n")
          .append("            color: #4338ca;\n")
          .append("            font-weight: 600;\n")
          .append("            border-radius: 6px;\n")
          .append("            padding: 0.2rem 0.55rem;\n")
          .append("            font-size: 0.72rem;\n")
          .append("            display: inline-block;\n")
          .append("        }\n")
          .append("        .badge-soft-info {\n")
          .append("            background: #e0f2fe;\n")
          .append("            color: #0369a1;\n")
          .append("            font-weight: 600;\n")
          .append("            border-radius: 6px;\n")
          .append("            padding: 0.18rem 0.5rem;\n")
          .append("            font-size: 0.68rem;\n")
          .append("            display: inline-block;\n")
          .append("            margin: 0.1rem 0.15rem 0.1rem 0;\n")
          .append("        }\n")

          // ---------- ACTION BAR ----------
          .append("        .action-bar {\n")
          .append("            padding: 0.6rem 1rem;\n")
          .append("            background: #f8fafc;\n")
          .append("            border-top: 1px solid #e2e8f0;\n")
          .append("            display: flex;\n")
          .append("            justify-content: flex-end;\n")
          .append("            gap: 8px;\n")
          .append("            flex-shrink: 0;\n")
          .append("            border-radius: 0 0 10px 10px;\n")
          .append("        }\n")
          .append("        .btn-action {\n")
          .append("            border-radius: 8px;\n")
          .append("            padding: 0.45rem 1.1rem;\n")
          .append("            font-weight: 600;\n")
          .append("            font-size: 0.82rem;\n")
          .append("            border: none;\n")
          .append("            display: inline-flex;\n")
          .append("            align-items: center;\n")
          .append("            gap: 0.4rem;\n")
          .append("            height: 34px;\n")
          .append("            text-decoration: none;\n")
          .append("            cursor: pointer;\n")
          .append("            transition: 0.15s;\n")
          .append("        }\n")
          .append("        .btn-create { background: #22c55e; color: white; }\n")
          .append("        .btn-create:hover { background: #16a34a; color: white; }\n")
          .append("        .btn-update { background: #f59e0b; color: white; }\n")
          .append("        .btn-update:hover { background: #d97706; color: white; }\n")
          .append("        .btn-delete { background: #ef4444; color: white; }\n")
          .append("        .btn-delete:hover { background: #dc2626; color: white; }\n")

          // ---------- RESPONSIVE — 4K ----------
          .append("        @media (min-width: 1920px) {\n")
          .append("            .main-wrapper { padding: 12px; gap: 12px; }\n")
          .append("            .glass-header { padding: 14px 24px; }\n")
          .append("            .glass-header h1 { font-size: 1.35rem; }\n")
          .append("            .filters-bar { padding: 10px 16px; }\n")
          .append("            .table-custom th { padding: 0.7rem 0.9rem; font-size: 0.72rem; }\n")
          .append("            .table-custom td { padding: 0.6rem 0.9rem; font-size: 0.88rem; }\n")
          .append("            .table-custom { min-width: 1100px; }\n")
          .append("        }\n")

          // ---------- RESPONSIVE — MOBILE ----------
          .append("        @media (max-width: 768px) {\n")
          .append("            html, body { overflow: auto; height: auto; min-height: 100dvh; }\n")
          .append("            .main-wrapper {\n")
          .append("                height: auto;\n")
          .append("                min-height: 100dvh;\n")
          .append("                overflow: visible;\n")
          .append("                padding: 6px;\n")
          .append("                gap: 6px;\n")
          .append("            }\n")
          .append("            .glass-header { padding: 8px 12px; border-radius: 10px; min-height: 48px; }\n")
          .append("            .glass-header h1 { font-size: 1rem; }\n")
          .append("            .glass-header .subtitle { display: none; }\n")
          .append("            .btn-glass { padding: 0.25rem 0.7rem; font-size: 0.72rem; }\n")
          .append("            .filters-bar { padding: 8px; border-radius: 8px; }\n")
          .append("            .filters-bar form.row {\n")
          .append("                display: flex;\n")
          .append("                flex-direction: column;\n")
          .append("                gap: 6px !important;\n")
          .append("            }\n")
          .append("            .filters-bar form .col,\n")
          .append("            .filters-bar form .col-auto { width: 100% !important; }\n")
          .append("            .filters-bar form .form-control { width: 100%; }\n")
          .append("            .filters-bar form .btn-primary-sm { width: 100%; justify-content: center; }\n")
          .append("            .table-container {\n")
          .append("                height: auto;\n")
          .append("                max-height: none;\n")
          .append("                border-radius: 8px;\n")
          .append("            }\n")
          .append("            .table-scroll { max-height: 65vh; }\n")
          .append("            .table-custom { min-width: 800px; }\n")
          .append("            .table-custom th { padding: 0.4rem 0.5rem; font-size: 0.62rem; }\n")
          .append("            .table-custom td { padding: 0.4rem 0.5rem; font-size: 0.75rem; }\n")
          .append("            .action-bar {\n")
          .append("                padding: 0.5rem 0.7rem;\n")
          .append("                flex-wrap: wrap;\n")
          .append("                border-radius: 0 0 8px 8px;\n")
          .append("            }\n")
          .append("            .btn-action {\n")
          .append("                padding: 0.4rem 0.8rem;\n")
          .append("                font-size: 0.75rem;\n")
          .append("                height: 32px;\n")
          .append("                flex: 1;\n")
          .append("                justify-content: center;\n")
          .append("                min-width: 0;\n")
          .append("            }\n")
          .append("        }\n")

          // ---------- RESPONSIVE — TRÈS PETIT MOBILE ----------
          .append("        @media (max-width: 400px) {\n")
          .append("            .glass-header h1 { font-size: 0.85rem; }\n")
          .append("            .glass-header h1 i { font-size: 0.8rem; }\n")
          .append("            .table-custom { min-width: 700px; }\n")
          .append("            .table-custom th { padding: 0.3rem 0.4rem; font-size: 0.58rem; }\n")
          .append("            .table-custom td { padding: 0.3rem 0.4rem; font-size: 0.7rem; }\n")
          .append("            .badge-soft-primary { font-size: 0.62rem; padding: 0.15rem 0.45rem; }\n")
          .append("            .badge-soft-info { font-size: 0.58rem; padding: 0.12rem 0.4rem; }\n")
          .append("        }\n")

          // ---------- RESPONSIVE — PAYSAGE MOBILE ----------
          .append("        @media (max-height: 500px) and (orientation: landscape) {\n")
          .append("            .main-wrapper { padding: 4px; gap: 4px; }\n")
          .append("            .glass-header { padding: 6px 12px; min-height: 40px; }\n")
          .append("            .glass-header h1 { font-size: 0.9rem; }\n")
          .append("            .glass-header .subtitle { display: none; }\n")
          .append("            .filters-bar { padding: 6px 8px; }\n")
          .append("            .table-scroll { max-height: 55vh; }\n")
          .append("            .action-bar { padding: 0.4rem 0.6rem; }\n")
          .append("        }\n")
          .append("    </style>\n")
          .append("</head>\n")
          .append("<body>\n")
          .append("<div class=\"main-wrapper\">\n");

        // ===== HEADER =====
        sb.append("    <div class=\"glass-header\">\n")
          .append("        <div>\n")
          .append("            <h1><i class=\"fa-solid fa-chalkboard-user\"></i> Gestion des Professeurs</h1>\n")
          .append("            <p class=\"subtitle\">Corps enseignant et créneaux d'affectation</p>\n")
          .append("        </div>\n")
          .append("        <div>\n")
          .append("            <a href=\"/dashboard\" class=\"btn-glass\"><i class=\"fa-solid fa-arrow-left me-1\"></i> Tableau de bord</a>\n")
          .append("        </div>\n")
          .append("    </div>\n");

        // ===== MESSAGE D'ERREUR =====
        if (!messageErreur.isEmpty()) {
            sb.append("    <div class=\"alert alert-danger alert-dismissible fade show border-0 shadow-sm rounded-3 mb-0 py-2\" role=\"alert\">\n")
              .append("        <i class=\"fa-solid fa-circle-exclamation me-2\"></i>").append(echapperHtml(messageErreur)).append("\n")
              .append("        <button type=\"button\" class=\"btn-close py-2\" data-bs-dismiss=\"alert\"></button>\n")
              .append("    </div>\n");
        }

        // ===== FILTRES =====
        sb.append("    <div class=\"filters-bar\">\n")
          .append("        <form method=\"GET\" action=\"/admin/professeurs\" class=\"row g-2 align-items-center\">\n")
          .append("            <div class=\"col\">\n")
          .append("                <input type=\"text\" name=\"q\" class=\"form-control\" placeholder=\"Rechercher par nom, prénom, identifiant...\" value=\"").append(echapperHtml(motCle)).append("\">\n")
          .append("            </div>\n")
          .append("            <div class=\"col-auto d-flex gap-2\">\n")
          .append("                <button type=\"submit\" class=\"btn-primary-sm\"><i class=\"fa-solid fa-filter me-1\"></i> Rechercher</button>\n")
          .append("                <a href=\"/admin/professeurs\" class=\"btn-outline-sm\" title=\"Réinitialiser\"><i class=\"fa-solid fa-rotate-right\"></i></a>\n")
          .append("            </div>\n")
          .append("        </form>\n")
          .append("    </div>\n");

        // ===== TABLEAU =====
        sb.append("    <div class=\"table-container\">\n")
          .append("        <div class=\"table-scroll\">\n")
          .append("            <table class=\"table-custom\">\n")
          .append("                <thead>\n")
          .append("                    <tr>\n")
          .append("                        <th class=\"col-select\">Sél.</th>\n")
          .append("                        <th>Identifiant</th>\n")
          .append("                        <th>Enseignant</th>\n")
          .append("                        <th>Email</th>\n")
          .append("                        <th>Spécialité</th>\n")
          .append("                        <th>Créneaux</th>\n")
          .append("                    </tr>\n")
          .append("                </thead>\n")
          .append("                <tbody>\n");

        if (professeurs.isEmpty()) {
            sb.append("                    <tr>\n")
              .append("                        <td colspan=\"6\" class=\"text-center py-5 text-muted\">\n")
              .append("                            <i class=\"fa-solid fa-user-slash fs-1 mb-3 text-black-50 d-block\"></i>\n")
              .append("                            <span>Aucun professeur trouvé dans la base de données.</span>\n")
              .append("                        </td>\n")
              .append("                    </tr>\n");
        } else {
            for (Professeur p : professeurs) {
                List<Professeur.Creneau> creneauxProf = p.getCreneaux() != null
                        ? p.getCreneaux() : List.of();
                String id = p.getNumeroIdentifiantProfesseur();

                sb.append("                    <tr>\n")
                  .append("                        <td class=\"col-select\">\n")
                  .append("                            <input type=\"radio\" name=\"selectedId\" class=\"form-check-input\" value=\"").append(echapperHtml(id)).append("\">\n")
                  .append("                        </td>\n")
                  .append("                        <td><span class=\"badge-soft-primary\">").append(echapperHtml(id)).append("</span></td>\n")
                  .append("                        <td class=\"fw-semibold text-dark\">")
                  .append(echapperHtml(p.getNom())).append(" ").append(echapperHtml(p.getPrenom()))
                  .append("</td>\n")
                  .append("                        <td class=\"text-secondary\">")
                  .append(echapperHtml(p.getEmail() != null ? p.getEmail() : "-"))
                  .append("</td>\n")
                  .append("                        <td><span class=\"badge bg-light text-dark border\">")
                  .append(echapperHtml(p.getSpecialite() != null ? p.getSpecialite() : "-"))
                  .append("</span></td>\n")
                  .append("                        <td>\n");

                if (creneauxProf.isEmpty()) {
                    sb.append("                            <span class=\"text-muted\" style=\"font-size:0.75rem;font-style:italic;\">Aucun créneau</span>\n");
                } else {
                    for (Professeur.Creneau c : creneauxProf) {
                        String jour = c.getJourSemaine() != null && c.getJourSemaine().length() >= 3
                                ? c.getJourSemaine().substring(0, 3) : "";
                        String heure = c.getHeureDebut() != null && c.getHeureDebut().length() >= 5
                                ? c.getHeureDebut().substring(0, 5) : "";

                        sb.append("                            <span class=\"badge-soft-info\">")
                          .append(echapperHtml(c.getCodeCours())).append(" - ")
                          .append(echapperHtml(c.getClasse()))
                          .append(" <span style=\"opacity:0.7;\">(")
                          .append(echapperHtml(jour)).append(" ").append(echapperHtml(heure))
                          .append(")</span></span>\n");
                    }
                }

                sb.append("                        </td>\n")
                  .append("                    </tr>\n");
            }
        }

        sb.append("                </tbody>\n")
          .append("            </table>\n")
          .append("        </div>\n");

        // ===== ACTION BAR =====
        sb.append("        <div class=\"action-bar\">\n");
        if (canCreate) {
            sb.append("            <button type=\"button\" class=\"btn-action btn-create\" onclick=\"ouvrirAjout()\">\n")
              .append("                <i class=\"fa-solid fa-plus\"></i> Ajouter\n")
              .append("            </button>\n");
        }
        if (canUpdate) {
            sb.append("            <button type=\"button\" class=\"btn-action btn-update\" onclick=\"ouvrirModif()\">\n")
              .append("                <i class=\"fa-solid fa-pen\"></i> Modifier\n")
              .append("            </button>\n");
        }
        if (canDelete) {
            sb.append("            <button type=\"button\" class=\"btn-action btn-delete\" onclick=\"supprimerProfesseur()\">\n")
              .append("                <i class=\"fa-solid fa-trash\"></i> Supprimer\n")
              .append("            </button>\n");
        }
        sb.append("        </div>\n")
          .append("    </div>\n")
          .append("</div>\n");

        // ===== FORMULAIRE MODAL (appel à UIFormulaireProfesseur) =====
        try {
            String formulaireHtml = UIFormulaireProfesseur.rendreFormulaire(
                    null, institutionId, List.of(), matieres);
            sb.append(formulaireHtml);
        } catch (Throwable t) {
            System.err.println("⚠️ Erreur UIFormulaireProfesseur : " + t.getMessage());
            sb.append(getFormulaireFallback());
        }

        // ===== SCRIPTS =====
        sb.append("<script src=\"https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js\"></script>\n");
        sb.append("<script>\n");
        sb.append(buildProfesseursJson(professeurs));
        sb.append(buildMatieresJson(matieres));

        sb.append("""
        function getSelectedId() {
            var radios = document.getElementsByName('selectedId');
            for (var i = 0; i < radios.length; i++) {
                if (radios[i].checked) return radios[i].value;
            }
            return null;
        }

        function ouvrirAjout() {
            document.getElementById('modalTitle').innerText = 'Ajouter un professeur';
            document.getElementById('formAction').value = 'add';
            var numId = document.getElementById('numId');
            if (numId) { numId.value = ''; numId.readOnly = false; }
            document.getElementById('modalForm').style.display = 'flex';
        }

        function ouvrirModif() {
            var id = getSelectedId();
            if (!id) { alert('Veuillez sélectionner un professeur.'); return; }
            var p = window.professeursData.find(function(item) {
                return item.numeroIdentifiantProfesseur === id;
            });
            if (!p) { alert('Professeur non trouvé.'); return; }
            document.getElementById('modalTitle').innerText = 'Modifier le professeur';
            document.getElementById('formAction').value = 'update';
            var numId = document.getElementById('numId');
            if (numId) { numId.value = p.numeroIdentifiantProfesseur || ''; numId.readOnly = true; }
            remplirFormulaire(p);
            document.getElementById('modalForm').style.display = 'flex';
        }

        function supprimerProfesseur() {
            var id = getSelectedId();
            if (!id) { alert('Veuillez sélectionner un professeur.'); return; }
            if (confirm('Supprimer définitivement ce professeur et tous ses créneaux ?')) {
                var form = document.createElement('form');
                form.method = 'POST';
                form.action = '/admin/professeurs';
                var a = document.createElement('input');
                a.type = 'hidden'; a.name = 'action'; a.value = 'delete';
                form.appendChild(a);
                var b = document.createElement('input');
                b.type = 'hidden'; b.name = 'id'; b.value = id;
                form.appendChild(b);
                document.body.appendChild(form);
                form.submit();
            }
        }

        function setVal(id, val) {
            var el = document.getElementById(id);
            if (el) el.value = val != null ? val : '';
        }

        function remplirFormulaire(p) {
            setVal('nom', p.nom);
            setVal('prenom', p.prenom);
            setVal('sexe', p.sexe);
            setVal('dateNaissance', p.dateNaissance);
            setVal('groupeSanguin', p.groupeSanguin);
            setVal('departementNaissance', p.departementNaissance);
            setVal('communeNaissance', p.communeNaissance);
            setVal('telephone', p.telephone);
            setVal('email', p.email);
            setVal('adresse', p.adresse);
            setVal('paysHabitation', p.paysHabitation);
            setVal('departementHabitation', p.departementHabitation);
            setVal('communeHabitation', p.communeHabitation);
            setVal('situationMatrimoniale', p.situationMatrimoniale);
            setVal('matricule', p.matricule);
            setVal('ninu', p.ninu);
            setVal('diplome', p.diplome);
            setVal('specialite', p.specialite);
            setVal('dateEmbauche', p.dateEmbauche);
            setVal('salaire', p.salaire);
            setVal('statut', p.statut || 'ACTIF');
            setVal('biometrieActive', p.biometrieActive ? 'true' : 'false');
            setVal('observations', p.observations);
        }

        window.onclick = function(event) {
            var modal = document.getElementById('modalForm');
            if (event.target == modal) {
                modal.style.display = 'none';
            }
        };
        """);
        sb.append("</script>\n");

        sb.append("</body>\n</html>");
        return sb.toString();
    }

    // ============================================================
    // JSON MANUEL (pas de GSON)
    // ============================================================
    private static String buildProfesseursJson(List<Professeur> professeurs) {
        StringBuilder sb = new StringBuilder("window.professeursData = [");
        boolean first = true;
        for (Professeur p : professeurs) {
            if (!first) sb.append(",");
            first = false;
            sb.append("{");
            sb.append("\"numeroIdentifiantProfesseur\":\"").append(esc(p.getNumeroIdentifiantProfesseur())).append("\"");
            sb.append(",\"nom\":\"").append(esc(p.getNom())).append("\"");
            sb.append(",\"prenom\":\"").append(esc(p.getPrenom())).append("\"");
            sb.append(",\"sexe\":\"").append(esc(p.getSexe())).append("\"");
            sb.append(",\"email\":\"").append(esc(p.getEmail())).append("\"");
            sb.append(",\"telephone\":\"").append(esc(p.getTelephone())).append("\"");
            sb.append(",\"specialite\":\"").append(esc(p.getSpecialite())).append("\"");
            sb.append(",\"diplome\":\"").append(esc(p.getDiplome())).append("\"");
            sb.append(",\"statut\":\"").append(esc(p.getStatut())).append("\"");
            sb.append(",\"adresse\":\"").append(esc(p.getAdresse())).append("\"");
            sb.append(",\"groupeSanguin\":\"").append(esc(p.getGroupeSanguin())).append("\"");
            sb.append(",\"departementNaissance\":\"").append(esc(p.getDepartementNaissance())).append("\"");
            sb.append(",\"communeNaissance\":\"").append(esc(p.getCommuneNaissance())).append("\"");
            sb.append(",\"paysHabitation\":\"").append(esc(p.getPaysHabitation())).append("\"");
            sb.append(",\"departementHabitation\":\"").append(esc(p.getDepartementHabitation())).append("\"");
            sb.append(",\"communeHabitation\":\"").append(esc(p.getCommuneHabitation())).append("\"");
            sb.append(",\"situationMatrimoniale\":\"").append(esc(p.getSituationMatrimoniale())).append("\"");
            sb.append(",\"matricule\":\"").append(esc(p.getMatricule())).append("\"");
            sb.append(",\"ninu\":\"").append(esc(p.getNinu())).append("\"");
            sb.append(",\"observations\":\"").append(esc(p.getObservations())).append("\"");
            sb.append(",\"photoPath\":\"").append(esc(p.getPhotoPath())).append("\"");
            sb.append(",\"biometrieActive\":").append(p.isBiometrieActive());
            sb.append(",\"salaire\":").append(p.getSalaire());
            sb.append("}");
        }
        sb.append("];\n");
        return sb.toString();
    }

    private static String buildMatieresJson(List<Matiere> matieres) {
        StringBuilder sb = new StringBuilder("window.matieresData = [");
        boolean first = true;
        for (Matiere m : matieres) {
            if (!first) sb.append(",");
            first = false;
            sb.append("{");
            sb.append("\"codeCours\":\"").append(esc(m.getCodeCours())).append("\"");
            sb.append(",\"nomMatiere\":\"").append(esc(m.getNomMatiere())).append("\"");
            sb.append(",\"classeMatiere\":\"").append(esc(m.getClasseMatiere())).append("\"");
            sb.append("}");
        }
        sb.append("];\n");
        return sb.toString();
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String echapperHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }

    // ============================================================
    // FORMULAIRE DE SECOURS (si UIFormulaireProfesseur plante)
    // ============================================================
    private static String getFormulaireFallback() {
        StringBuilder sb = new StringBuilder();
        sb.append("<div id=\"modalForm\" style=\"display:none;position:fixed;inset:0;background:rgba(0,0,0,0.5);z-index:1050;justify-content:center;align-items:center;padding:20px;\">");
        sb.append("<div style=\"background:white;border-radius:14px;max-width:600px;width:100%;padding:24px;\">");
        sb.append("<h3 id=\"modalTitle\" style=\"font-size:1.1rem;font-weight:700;margin-bottom:16px;\">Professeur</h3>");
        sb.append("<form id=\"profForm\" method=\"POST\" action=\"/admin/professeurs\">");
        sb.append("<input type=\"hidden\" name=\"action\" id=\"formAction\" value=\"add\">");
        sb.append("<div style=\"display:grid;grid-template-columns:1fr 1fr;gap:12px;\">");

        sb.append("<div><label style=\"font-size:0.8rem;font-weight:600;\">Identifiant</label>");
        sb.append("<input type=\"text\" name=\"numeroIdentifiantProfesseur\" id=\"numId\" readonly style=\"width:100%;padding:8px;border:1px solid #cbd5e1;border-radius:6px;\"></div>");

        sb.append("<div><label style=\"font-size:0.8rem;font-weight:600;\">Nom *</label>");
        sb.append("<input type=\"text\" name=\"nom\" id=\"nom\" required style=\"width:100%;padding:8px;border:1px solid #cbd5e1;border-radius:6px;\"></div>");

        sb.append("<div><label style=\"font-size:0.8rem;font-weight:600;\">Prénom</label>");
        sb.append("<input type=\"text\" name=\"prenom\" id=\"prenom\" style=\"width:100%;padding:8px;border:1px solid #cbd5e1;border-radius:6px;\"></div>");

        sb.append("<div><label style=\"font-size:0.8rem;font-weight:600;\">Email</label>");
        sb.append("<input type=\"email\" name=\"email\" id=\"email\" style=\"width:100%;padding:8px;border:1px solid #cbd5e1;border-radius:6px;\"></div>");

        sb.append("<div><label style=\"font-size:0.8rem;font-weight:600;\">Téléphone</label>");
        sb.append("<input type=\"text\" name=\"telephone\" id=\"telephone\" style=\"width:100%;padding:8px;border:1px solid #cbd5e1;border-radius:6px;\"></div>");

        sb.append("<div><label style=\"font-size:0.8rem;font-weight:600;\">Spécialité</label>");
        sb.append("<input type=\"text\" name=\"specialite\" id=\"specialite\" style=\"width:100%;padding:8px;border:1px solid #cbd5e1;border-radius:6px;\"></div>");

        sb.append("</div>");

        sb.append("<div style=\"display:flex;justify-content:flex-end;gap:8px;margin-top:20px;\">");
        sb.append("<button type=\"button\" onclick=\"document.getElementById('modalForm').style.display='none'\" ");
        sb.append("style=\"padding:10px 20px;border:1px solid #cbd5e1;border-radius:8px;background:white;cursor:pointer;\">Annuler</button>");
        sb.append("<button type=\"submit\" style=\"padding:10px 20px;border:none;border-radius:8px;background:#4f46e5;color:white;font-weight:600;cursor:pointer;\">Enregistrer</button>");
        sb.append("</div>");

        sb.append("</form></div></div>");
        return sb.toString();
    }
}