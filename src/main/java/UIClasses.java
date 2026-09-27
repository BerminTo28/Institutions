import java.util.List;
import java.util.Map;

/**
 * Générateur HTML pour la page de gestion des classes.
 *
 * STRATÉGIE DE MISE À JOUR :
 *  - CRUD via POST classique + redirection 302 (comme UIMatiere).
 *  - Rechargement complet de la page après chaque opération réussie.
 *  - Aucune dépendance à renderTable/JSON côté client pour la mise à jour.
 *
 * AVANTAGES :
 *  - Fiabilité maximale : l'interface reflète TOUJOURS l'état serveur.
 *  - Pas de risque de désynchronisation.
 *  - Fonctionne même si l'AJAX échoue.
 */
public class UIClasses {

    // ============================================================
    // POINT D'ENTRÉE
    // ============================================================
    public static String rendrePage(
            List<Classe> classes,
            String motCle,
            String anneeFiltre,
            String periodeFiltre,
            String nomClasseFiltre,
            List<String> annees,
            List<String> periodes,
            List<String> promotions,
            List<String> nomsClasses,
            String institutionId,
            String successMessage,
            String errorMessage,
            boolean canCreate,
            boolean canModify,
            boolean canDelete) {

        // ---------- Sécurisation ----------
        classes = classes == null ? List.of() : classes;
        annees = annees == null ? List.of() : annees;
        periodes = periodes == null ? List.of() : periodes;
        promotions = promotions == null ? List.of() : promotions;
        nomsClasses = nomsClasses == null ? List.of() : nomsClasses;
        motCle = motCle == null ? "" : motCle;
        anneeFiltre = anneeFiltre == null ? "" : anneeFiltre;
        periodeFiltre = periodeFiltre == null ? "" : periodeFiltre;
        nomClasseFiltre = nomClasseFiltre == null ? "" : nomClasseFiltre;
        institutionId = institutionId == null ? "" : institutionId;
        successMessage = successMessage == null ? "" : successMessage;
        errorMessage = errorMessage == null ? "" : errorMessage;

        Map<String, Integer> statsEtudiants = StatsCache.getStatsEtudiants(institutionId);

        StringBuilder sb = new StringBuilder(32_000);

        appendHead(sb);
        appendStyles(sb);

        sb.append("<body>\n")
          .append("<div class=\"main-wrapper\">\n");

        appendHeader(sb);
        appendMessages(sb, successMessage, errorMessage);
        appendFiltres(sb, motCle, anneeFiltre, periodeFiltre, nomClasseFiltre,
                annees, periodes, nomsClasses);
        appendTableau(sb, classes, statsEtudiants, canModify, canDelete);
        appendActionBar(sb, canCreate);

        sb.append("</div>\n"); // .main-wrapper

        // Modal (délégué à UIFormulaireClasse)
        sb.append(UIFormulaireClasse.rendreModal(
                institutionId, annees, periodes, promotions));

        sb.append("<div class=\"toast-container\" id=\"toastContainer\"></div>\n");
        appendScripts(sb, canModify, canDelete, institutionId);

        sb.append("</body>\n</html>\n");
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
          .append("    <title>Gestion des Classes</title>\n")
          .append("    <script src=\"https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js\"></script>\n")
          .append("    <link href=\"https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css\" rel=\"stylesheet\">\n")
          .append("    <link href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css\" rel=\"stylesheet\">\n")
          .append("    <link href=\"https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap\" rel=\"stylesheet\">\n");
    }

    // ============================================================
    // STYLES
    // ============================================================
    private static void appendStyles(StringBuilder sb) {
        sb.append("""
            <style>
                :root {
                    --primary-color: #4f46e5;
                    --primary-hover: #4338ca;
                    --bg-color: #f1f5f9;
                    --card-shadow: 0 4px 12px -4px rgba(0,0,0,0.06);
                    --safe-top: env(safe-area-inset-top, 0px);
                    --safe-bottom: env(safe-area-inset-bottom, 0px);
                    --safe-left: env(safe-area-inset-left, 0px);
                    --safe-right: env(safe-area-inset-right, 0px);
                }
                *, *::before, *::after { box-sizing: border-box; }
                html, body {
                    width: 100%; min-width: 320px;
                    height: 100vh; height: 100dvh;
                    min-height: 100vh; min-height: 100dvh;
                    margin: 0; padding: 0; overflow: hidden;
                    font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
                    background-color: var(--bg-color);
                    color: #1e293b;
                    -webkit-text-size-adjust: 100%;
                    -webkit-tap-highlight-color: transparent;
                    overscroll-behavior: none;
                }
                .main-wrapper {
                    width: 100%; height: 100vh; height: 100dvh;
                    display: flex; flex-direction: column;
                    padding: 8px;
                    padding-top: calc(8px + var(--safe-top));
                    padding-bottom: calc(8px + var(--safe-bottom));
                    padding-left: calc(8px + var(--safe-left));
                    padding-right: calc(8px + var(--safe-right));
                    gap: 8px;
                }
                .glass-header {
                    background: linear-gradient(135deg, #4f46e5 0%, #3b82f6 100%);
                    color: white; border-radius: 12px;
                    padding: 10px 18px;
                    box-shadow: 0 8px 20px -8px rgba(79,70,229,0.35);
                    flex-shrink: 0;
                    display: flex; justify-content: space-between; align-items: center;
                    gap: 12px; min-height: 52px;
                }
                .glass-header h1 {
                    font-size: 1.15rem; font-weight: 700; margin: 0;
                    display: flex; align-items: center; gap: 0.5rem;
                }
                .glass-header .subtitle { opacity: 0.85; font-size: 0.78rem; margin: 0; }
                .btn-glass {
                    background: rgba(255,255,255,0.15);
                    border: 1px solid rgba(255,255,255,0.25);
                    color: white; border-radius: 50px;
                    padding: 0.35rem 0.9rem; font-weight: 500;
                    text-decoration: none; transition: 0.15s;
                    font-size: 0.82rem; white-space: nowrap;
                    display: inline-flex; align-items: center;
                }
                .btn-glass:hover { background: rgba(255,255,255,0.25); color: white; }

                .filters-bar {
                    background: #ffffff; border-radius: 10px;
                    padding: 8px 12px; box-shadow: var(--card-shadow);
                    border: 1px solid #e2e8f0; flex-shrink: 0;
                }
                .filters-bar .form-control,
                .filters-bar .form-select {
                    border-radius: 8px; border: 1px solid #e2e8f0;
                    padding: 0.35rem 0.65rem; font-size: 0.82rem;
                    background: #f8fafc; height: 34px; transition: 0.15s;
                }
                .filters-bar .form-control:focus,
                .filters-bar .form-select:focus {
                    border-color: var(--primary-color);
                    box-shadow: 0 0 0 3px rgba(79,70,229,0.12);
                    background: white;
                }
                .filters-bar .input-group-text {
                    background: #f8fafc; border-color: #e2e8f0;
                    height: 34px; padding: 0 0.55rem; font-size: 0.82rem;
                }
                .btn-primary-sm {
                    background: var(--primary-color); border: none;
                    border-radius: 8px; padding: 0.35rem 0.9rem;
                    font-weight: 600; font-size: 0.82rem; color: white;
                    height: 34px; transition: 0.15s; white-space: nowrap;
                    display: inline-flex; align-items: center; gap: 6px;
                }
                .btn-primary-sm:hover { background: var(--primary-hover); }
                .btn-outline-sm {
                    border: 1px solid #e2e8f0; border-radius: 8px;
                    background: white; color: #64748b; height: 34px;
                    padding: 0 0.65rem;
                    display: inline-flex; align-items: center; justify-content: center;
                    transition: 0.15s;
                }
                .btn-outline-sm:hover { background: #f1f5f9; color: var(--primary-color); }

                .table-container {
                    flex: 1; min-height: 0; background: #ffffff;
                    border-radius: 10px; box-shadow: var(--card-shadow);
                    border: 1px solid #e2e8f0;
                    display: flex; flex-direction: column; overflow: hidden;
                }
                .table-scroll { flex: 1; overflow: auto; }
                .table-custom {
                    margin-bottom: 0; min-width: 1100px; width: 100%;
                    border-collapse: collapse;
                }
                .table-custom thead {
                    position: sticky; top: 0; z-index: 10;
                    background-color: #f8fafc;
                }
                .table-custom th {
                    padding: 0.4rem 0.65rem; font-weight: 600;
                    font-size: 0.66rem; text-transform: uppercase;
                    letter-spacing: 0.3px; color: #64748b;
                    text-align: left; white-space: nowrap;
                    border-bottom: 2px solid #e2e8f0; line-height: 1.2;
                }
                .table-custom td {
                    padding: 0.32rem 0.65rem;
                    border-bottom: 1px solid #f1f5f9;
                    vertical-align: middle; font-size: 0.78rem; line-height: 1.3;
                }
                .table-custom tbody tr { transition: background 0.12s ease; }
                .table-custom tbody tr:hover {
                    background: #f8fafc;
                    box-shadow: inset 3px 0 0 var(--primary-color);
                }
                .table-custom tbody tr:last-child td { border-bottom: none; }
                .table-custom code {
                    font-size: 0.72rem; background: #f1f5f9;
                    padding: 0.1rem 0.35rem; border-radius: 4px; color: #475569;
                }
                .badge-soft-primary {
                    background-color: #e0e7ff; color: #4338ca;
                    font-weight: 600; border-radius: 5px;
                    padding: 0.15rem 0.5rem; font-size: 0.68rem;
                    line-height: 1.2; display: inline-block;
                }
                .badge-active {
                    background-color: #d1fae5; color: #065f46;
                    font-weight: 600; border-radius: 5px;
                    padding: 0.15rem 0.5rem; font-size: 0.68rem;
                    line-height: 1.2; display: inline-block;
                }
                .badge-inactive {
                    background-color: #f1f5f9; color: #64748b;
                    font-weight: 600; border-radius: 5px;
                    padding: 0.15rem 0.5rem; font-size: 0.68rem;
                    line-height: 1.2; display: inline-block;
                }
                .niveau-display {
                    display: inline-block; padding: 0.15rem 0.5rem;
                    border-radius: 5px; background: #f1f5f9;
                    color: #475569; font-weight: 600;
                    font-size: 0.68rem; line-height: 1.2;
                }
                .progress-bar-custom {
                    width: 50px; height: 5px; background: #e9ecef;
                    border-radius: 3px; display: inline-block;
                    vertical-align: middle; margin-left: 5px;
                }
                .progress-bar-fill {
                    height: 100%; border-radius: 3px;
                    transition: width 0.3s ease;
                }
                .badge-capacite {
                    font-size: 0.58rem; padding: 0.1rem 0.4rem;
                    border-radius: 4px; line-height: 1.2;
                }
                .action-bar {
                    padding: 0.6rem 1rem; background: #f8fafc;
                    border-top: 1px solid #e2e8f0;
                    border-radius: 0 0 10px 10px;
                    display: flex; justify-content: flex-end;
                    gap: 0.5rem; flex-shrink: 0;
                }
                .btn-action {
                    border-radius: 8px; padding: 0.45rem 1.1rem;
                    font-weight: 600; font-size: 0.82rem; border: none;
                    transition: 0.15s; text-decoration: none;
                    display: inline-flex; align-items: center; gap: 0.4rem;
                    height: 34px; cursor: pointer;
                }
                .btn-create { background: #22c55e; color: white; }
                .btn-create:hover { background: #16a34a; color: white; }

                .btn-icon {
                    width: 26px; height: 26px; border-radius: 6px;
                    display: inline-flex; align-items: center; justify-content: center;
                    border: 1px solid #e2e8f0; background: white;
                    transition: 0.15s; text-decoration: none;
                    cursor: pointer; font-size: 0.72rem;
                }
                .btn-icon-edit { color: #f59e0b; }
                .btn-icon-edit:hover {
                    background: #fef3c7; color: #d97706; border-color: #fcd34d;
                }
                .btn-icon-delete { color: #ef4444; }
                .btn-icon-delete:hover {
                    background: #fee2e2; color: #dc2626; border-color: #fca5a5;
                }

                .toast-container {
                    position: fixed;
                    bottom: calc(20px + var(--safe-bottom));
                    right: calc(20px + var(--safe-right));
                    z-index: 1100;
                }
                .toast-custom {
                    background: #0f172a; color: white;
                    padding: 12px 20px; border-radius: 12px;
                    box-shadow: 0 10px 25px -5px rgba(0,0,0,0.2);
                    font-weight: 500; font-size: 13.5px;
                    animation: fadeInOut 3s ease;
                    margin-top: 8px; max-width: 420px;
                    word-wrap: break-word;
                }
                .toast-custom.success { background: #16a34a; }
                .toast-custom.error { background: #dc2626; }
                .toast-custom.warning { background: #f59e0b; }
                @keyframes fadeInOut {
                    0%   { opacity: 0; transform: translateY(20px); }
                    15%  { opacity: 1; transform: translateY(0); }
                    85%  { opacity: 1; }
                    100% { opacity: 0; transform: translateY(20px); }
                }
                .loading-overlay {
                    position: fixed; top: 0; left: 0; right: 0; bottom: 0;
                    background: rgba(255,255,255,0.6);
                    z-index: 2000; display: none;
                    align-items: center; justify-content: center;
                }
                .loading-overlay.active { display: flex; }
                .loading-spinner {
                    width: 40px; height: 40px;
                    border: 4px solid #e2e8f0;
                    border-top-color: var(--primary-color);
                    border-radius: 50%;
                    animation: spin 0.8s linear infinite;
                }
                @keyframes spin { to { transform: rotate(360deg); } }

                @media (min-width: 1920px) {
                    .main-wrapper { padding: 12px; gap: 12px; }
                    .glass-header { padding: 14px 24px; }
                    .glass-header h1 { font-size: 1.35rem; }
                    .table-custom th { font-size: 0.72rem; padding: 0.5rem 0.8rem; }
                    .table-custom td { font-size: 0.86rem; padding: 0.4rem 0.8rem; }
                    .table-custom { min-width: 1300px; }
                    .filters-bar { padding: 10px 16px; }
                }
                @media (max-width: 768px) {
                    html, body { overflow: auto; height: auto; min-height: 100dvh; }
                    .main-wrapper {
                        height: auto; min-height: 100dvh;
                        overflow: visible; padding: 6px; gap: 6px;
                    }
                    .glass-header { padding: 8px 12px; border-radius: 10px; }
                    .glass-header h1 { font-size: 1rem; }
                    .glass-header .subtitle { display: none; }
                    .btn-glass { font-size: 0.72rem; padding: 0.25rem 0.7rem; }
                    .filters-bar { padding: 8px; border-radius: 8px; }
                    .filters-bar form {
                        display: flex; flex-direction: column !important;
                        gap: 6px !important;
                    }
                    .filters-bar form .col,
                    .filters-bar form .col-auto { width: 100% !important; }
                    .filters-bar form .form-control,
                    .filters-bar form .form-select { width: 100% !important; }
                    .filters-bar form .d-flex { justify-content: stretch; }
                    .filters-bar form .btn-primary-sm { flex: 1; justify-content: center; }
                    .filters-bar form .btn-outline-sm { flex: 0 0 auto; }
                    .table-container {
                        height: auto; max-height: none; border-radius: 8px;
                    }
                    .table-scroll {
                        max-height: 65vh; overflow: auto;
                        -webkit-overflow-scrolling: touch;
                    }
                    .table-scroll td, .table-scroll th {
                        padding: 0.3rem 0.45rem; font-size: 0.7rem;
                    }
                    .table-custom { min-width: 900px; }
                    .action-bar {
                        padding: 0.5rem 0.7rem; border-radius: 0 0 8px 8px;
                    }
                    .btn-action {
                        padding: 0.4rem 0.8rem; font-size: 0.75rem;
                        height: 32px; flex: 1; justify-content: center;
                    }
                    .btn-icon { width: 24px; height: 24px; font-size: 0.68rem; }
                    .toast-container {
                        bottom: calc(12px + var(--safe-bottom));
                        right: calc(12px + var(--safe-right));
                        left: calc(12px + var(--safe-left));
                    }
                    .toast-custom { padding: 10px 14px; font-size: 12.5px; }
                }
                @media (max-width: 400px) {
                    .glass-header h1 { font-size: 0.9rem; }
                    .glass-header h1 i { font-size: 0.85rem; }
                    .table-scroll td, .table-scroll th {
                        padding: 0.25rem 0.4rem; font-size: 0.66rem;
                    }
                    .table-custom { min-width: 800px; }
                    .table-custom code { font-size: 0.6rem; }
                    .badge-soft-primary, .badge-active, .badge-inactive,
                    .niveau-display { font-size: 0.6rem; padding: 0.1rem 0.4rem; }
                    .progress-bar-custom { width: 36px; }
                }
                @media (max-height: 500px) and (orientation: landscape) {
                    .main-wrapper { padding: 4px; gap: 4px; }
                    .glass-header { padding: 6px 12px; min-height: 42px; }
                    .glass-header h1 { font-size: 0.9rem; }
                    .glass-header .subtitle { display: none; }
                    .filters-bar { padding: 6px 8px; }
                    .table-scroll { max-height: 55vh; }
                    .action-bar { padding: 0.4rem 0.6rem; }
                }
            </style>
            </head>
            """);
    }

    // ============================================================
    // HEADER
    // ============================================================
    private static void appendHeader(StringBuilder sb) {
        sb.append("""
            <div class="glass-header">
                <div>
                    <h1><i class="fa-solid fa-school"></i> Gestion des Classes</h1>
                    <p class="subtitle">Gérez les classes par niveau, année et période</p>
                </div>
                <div>
                    <a href="/admin/parametres" class="btn-glass">
                        <i class="fa-solid fa-arrow-left me-1"></i> Paramètres
                    </a>
                </div>
            </div>
            """);
    }

    // ============================================================
    // MESSAGES
    // ============================================================
    private static void appendMessages(StringBuilder sb, String success, String error) {
        if (!error.isEmpty()) {
            sb.append("    <div class=\"alert alert-danger alert-dismissible fade show border-0 shadow-sm rounded-3 mb-0 py-2\" role=\"alert\">\n")
              .append("        <i class=\"fa-solid fa-circle-exclamation me-2\"></i>")
              .append(echapperHtml(error))
              .append("\n        <button type=\"button\" class=\"btn-close py-2\" data-bs-dismiss=\"alert\"></button>\n")
              .append("    </div>\n");
        }
        if (!success.isEmpty()) {
            sb.append("    <div class=\"alert alert-success alert-dismissible fade show border-0 shadow-sm rounded-3 mb-0 py-2\" role=\"alert\">\n")
              .append("        <i class=\"fa-solid fa-circle-check me-2\"></i>")
              .append(echapperHtml(success))
              .append("\n        <button type=\"button\" class=\"btn-close py-2\" data-bs-dismiss=\"alert\"></button>\n")
              .append("    </div>\n");
        }
    }

    // ============================================================
    // FILTRES
    // ============================================================
    private static void appendFiltres(StringBuilder sb,
                                       String motCle,
                                       String anneeFiltre,
                                       String periodeFiltre,
                                       String nomClasseFiltre,
                                       List<String> annees,
                                       List<String> periodes,
                                       List<String> nomsClasses) {
        sb.append("    <div class=\"filters-bar\">\n")
          .append("        <form method=\"GET\" action=\"/admin/classes\" class=\"row g-2 align-items-center\" id=\"filtresForm\">\n")

          .append("            <div class=\"col\">\n")
          .append("                <div class=\"input-group input-group-sm\">\n")
          .append("                    <span class=\"input-group-text\">\n")
          .append("                        <i class=\"fa-solid fa-magnifying-glass text-muted\"></i>\n")
          .append("                    </span>\n")
          .append("                    <input type=\"text\" name=\"q\" class=\"form-control\"\n")
          .append("                           placeholder=\"Rechercher...\" value=\"")
          .append(echapperHtml(motCle)).append("\">\n")
          .append("                </div>\n")
          .append("            </div>\n")

          .append("            <div class=\"col-auto\">\n")
          .append("                <select name=\"annee\" class=\"form-select form-select-sm\">\n")
          .append("                    <option value=\"\">Toutes années</option>\n");
        for (String a : annees) {
            sb.append("                    <option value=\"").append(echapperHtml(a)).append("\"")
              .append(a.equals(anneeFiltre) ? " selected" : "")
              .append(">").append(echapperHtml(a)).append("</option>\n");
        }
        sb.append("                </select>\n")
          .append("            </div>\n")

          .append("            <div class=\"col-auto\">\n")
          .append("                <select name=\"periode\" class=\"form-select form-select-sm\">\n")
          .append("                    <option value=\"\">Toutes périodes</option>\n");
        for (String p : periodes) {
            sb.append("                    <option value=\"").append(echapperHtml(p)).append("\"")
              .append(p.equals(periodeFiltre) ? " selected" : "")
              .append(">").append(echapperHtml(p)).append("</option>\n");
        }
        sb.append("                </select>\n")
          .append("            </div>\n")

          .append("            <div class=\"col-auto\">\n")
          .append("                <select name=\"nomClasseFiltre\" class=\"form-select form-select-sm\">\n")
          .append("                    <option value=\"\">Toutes les classes</option>\n");
        for (String n : nomsClasses) {
            sb.append("                    <option value=\"").append(echapperHtml(n)).append("\"")
              .append(n.equals(nomClasseFiltre) ? " selected" : "")
              .append(">").append(echapperHtml(n)).append("</option>\n");
        }
        sb.append("                </select>\n")
          .append("            </div>\n")

          .append("            <div class=\"col-auto d-flex gap-2\">\n")
          .append("                <button type=\"submit\" class=\"btn-primary-sm\">\n")
          .append("                    <i class=\"fa-solid fa-filter me-1\"></i> Filtrer\n")
          .append("                </button>\n")
          .append("                <a href=\"/admin/classes\" class=\"btn-outline-sm\" title=\"Réinitialiser\">\n")
          .append("                    <i class=\"fa-solid fa-rotate-right\"></i>\n")
          .append("                </a>\n")
          .append("            </div>\n")

          .append("        </form>\n")
          .append("    </div>\n");
    }

    // ============================================================
    // TABLEAU
    // ============================================================
    private static void appendTableau(StringBuilder sb,
                                       List<Classe> classes,
                                       Map<String, Integer> statsEtudiants,
                                       boolean canModify,
                                       boolean canDelete) {
        sb.append("    <div class=\"table-container\">\n")
          .append("        <div class=\"table-scroll\">\n")
          .append("            <table class=\"table-custom\">\n")
          .append("                <thead>\n")
          .append("                    <tr>\n")
          .append("                        <th>#</th>\n")
          .append("                        <th>Code</th>\n")
          .append("                        <th>Nom</th>\n")
          .append("                        <th>Niveau</th>\n")
          .append("                        <th>Année</th>\n")
          .append("                        <th>Période</th>\n")
          .append("                        <th>Promotion</th>\n")
          .append("                        <th>Capacité</th>\n")
          .append("                        <th class=\"text-center\">Étudiants</th>\n")
          .append("                        <th>Statut</th>\n")
          .append("                        <th class=\"text-end\" style=\"width: 90px;\">Actions</th>\n")
          .append("                    </tr>\n")
          .append("                </thead>\n")
          .append("                <tbody id=\"classesBody\">\n");

        if (classes.isEmpty()) {
            appendTableauVide(sb);
        } else {
            appendTableauLignes(sb, classes, statsEtudiants, canModify, canDelete);
        }

        sb.append("                </tbody>\n")
          .append("            </table>\n")
          .append("        </div>\n")
          .append("    </div>\n");
    }

    private static void appendTableauVide(StringBuilder sb) {
        sb.append("""
                    <tr>
                        <td colspan="11" class="text-center py-5 text-muted">
                            <i class="fa-solid fa-inbox fs-1 mb-3 text-black-50 d-block"></i>
                            <span>
                                Aucune classe enregistrée. Cliquez sur
                                <strong>"Créer"</strong> pour ajouter une classe.
                            </span>
                        </td>
                    </tr>
            """);
    }

    private static void appendTableauLignes(StringBuilder sb,
                                             List<Classe> classes,
                                             Map<String, Integer> statsEtudiants,
                                             boolean canModify,
                                             boolean canDelete) {
        int index = 1;
        for (Classe c : classes) {
            String code = c.getCodeClasse();
            int niveau = c.getNiveauClasse();
            int capacite = c.getCapaciteClasse();
            int nbEtudiants = statsEtudiants.getOrDefault(code, 0);

            boolean isActif = "ACTIF".equalsIgnoreCase(c.getStatut());
            String statutClass = isActif ? "badge-active" : "badge-inactive";
            String statutLabel = isActif ? "Active" : "Inactive";
            String niveauDisplay = formatNiveau(niveau);

            String pourcentage = "";
            String barreProgression = "";
            String badgeCapacite = "";
            if (capacite > 0) {
                double pct = Math.min(100.0, (nbEtudiants * 100.0) / capacite);
                String color = pct < 70 ? "success" : (pct < 90 ? "warning" : "danger");
                int pctInt = (int) Math.round(pct);

                pourcentage = "<span class='badge-capacite badge bg-" + color + "'>"
                        + String.format("%.0f", pct) + "%</span>";
                barreProgression = "<div class='progress-bar-custom'>"
                        + "<div class='progress-bar-fill bg-" + color + "' style='width:" + pctInt + "%'></div>"
                        + "</div>";
                if (pct >= 100) {
                    badgeCapacite = "<span class='badge bg-danger badge-capacite'>"
                            + "<i class='fa-solid fa-triangle-exclamation'></i> Pleine</span>";
                }
            }

            sb.append("                    <tr")
              .append(" data-code=\"").append(echapperHtml(code)).append("\"")
              .append(" data-nom=\"").append(echapperHtml(c.getNomClasse())).append("\"")
              .append(" data-niveau=\"").append(niveau).append("\"")
              .append(" data-annee=\"").append(echapperHtml(c.getAnneeAcademique())).append("\"")
              .append(" data-periode=\"").append(echapperHtml(c.getPeriode())).append("\"")
              .append(" data-promotion=\"").append(echapperHtml(c.getPromotion())).append("\"")
              .append(" data-capacite=\"").append(capacite).append("\"")
              .append(" data-statut=\"").append(echapperHtml(c.getStatut())).append("\"")
              .append(" data-moyenne=\"").append(c.getMoyennePassage()).append("\"")
              .append(">\n")

              .append("                        <td><span class=\"badge-soft-primary\">")
              .append(index++).append("</span></td>\n")

              .append("                        <td><code>")
              .append(echapperHtml(code)).append("</code></td>\n")

              .append("                        <td class=\"fw-semibold text-dark\">")
              .append(echapperHtml(c.getNomClasse())).append("</td>\n")

              .append("                        <td><span class=\"niveau-display\">")
              .append(niveauDisplay).append("</span></td>\n")

              .append("                        <td>")
              .append(echapperHtml(c.getAnneeAcademique())).append("</td>\n")

              .append("                        <td>")
              .append(echapperHtml(c.getPeriode())).append("</td>\n")

              .append("                        <td>")
              .append(echapperHtml(c.getPromotion())).append("</td>\n")

              .append("                        <td>").append(capacite).append("</td>\n")

              .append("                        <td class=\"text-center\">\n")
              .append("                            <div class=\"d-flex align-items-center justify-content-center gap-1\">\n")
              .append("                                <span class=\"fw-bold\" style=\"font-size:0.8rem;\">").append(nbEtudiants).append("</span>\n")
              .append("                                ").append(pourcentage).append("\n")
              .append("                                ").append(barreProgression).append("\n")
              .append("                                ").append(badgeCapacite).append("\n")
              .append("                            </div>\n")
              .append("                        </td>\n")

              .append("                        <td><span class=\"").append(statutClass).append("\">")
              .append(statutLabel).append("</span></td>\n")

              .append("                        <td class=\"text-end\">\n");

            if (canModify) {
                sb.append("                            <button class=\"btn-icon btn-icon-edit btn-edit me-1\"\n")
                  .append("                                    data-code=\"").append(echapperHtml(code)).append("\"\n")
                  .append("                                    title=\"Modifier\">\n")
                  .append("                                <i class=\"fa-solid fa-pen\"></i>\n")
                  .append("                            </button>\n");
            }
            if (canDelete) {
                sb.append("                            <button class=\"btn-icon btn-icon-delete btn-delete\"\n")
                  .append("                                    data-code=\"").append(echapperHtml(code)).append("\"\n")
                  .append("                                    data-nom=\"").append(echapperHtml(c.getNomClasse())).append("\"\n")
                  .append("                                    title=\"Supprimer\">\n")
                  .append("                                <i class=\"fa-solid fa-trash\"></i>\n")
                  .append("                            </button>\n");
            }

            sb.append("                        </td>\n")
              .append("                    </tr>\n");
        }
    }

    // ============================================================
    // ACTION BAR
    // ============================================================
    private static void appendActionBar(StringBuilder sb, boolean canCreate) {
        sb.append("    <div class=\"action-bar\">\n");
        if (canCreate) {
            sb.append("        <button type=\"button\"\n")
              .append("                class=\"btn-action btn-create\"\n")
              .append("                id=\"btnAjouter\">\n")
              .append("            <i class=\"fa-solid fa-plus\"></i> Créer une classe\n")
              .append("        </button>\n");
        }
        sb.append("    </div>\n");
    }

    // ============================================================
    // SCRIPTS — STRATÉGIE "RECHARGEMENT COMPLET APRÈS POST"
    // ============================================================
    private static void appendScripts(StringBuilder sb,
                                       boolean canModify,
                                       boolean canDelete,
                                       String institutionId) {
        sb.append("<div class=\"loading-overlay\" id=\"loadingOverlay\">\n")
          .append("    <div class=\"loading-spinner\"></div>\n")
          .append("</div>\n");

        sb.append("<script>\n")
          .append("(function() {\n")
          .append("    'use strict';\n")
          .append("\n")
          .append("    const CAN_MODIFY = ").append(canModify).append(";\n")
          .append("    const CAN_DELETE = ").append(canDelete).append(";\n")
          .append("    const INSTITUTION_ID = '").append(echapperJs(institutionId)).append("';\n")
          .append("    const TIMEOUT_MS = 15000;\n")
          .append("    let submitting = false;\n")
          .append("\n")

          // ---------- TOAST ----------
          .append("    window.showToast = function(message, type) {\n")
          .append("        const container = document.getElementById('toastContainer');\n")
          .append("        if (!container) return;\n")
          .append("        const div = document.createElement('div');\n")
          .append("        div.className = 'toast-custom ' + (type || 'info');\n")
          .append("        div.textContent = message;\n")
          .append("        container.appendChild(div);\n")
          .append("        setTimeout(function() { div.remove(); }, 5000);\n")
          .append("    };\n")
          .append("\n")

          // ---------- LOADING ----------
          .append("    function showLoading() {\n")
          .append("        const el = document.getElementById('loadingOverlay');\n")
          .append("        if (el) el.classList.add('active');\n")
          .append("    }\n")
          .append("    function hideLoading() {\n")
          .append("        const el = document.getElementById('loadingOverlay');\n")
          .append("        if (el) el.classList.remove('active');\n")
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

          // ---------- Extraction message d'erreur ----------
          .append("    async function extraireErreur(res) {\n")
          .append("        try {\n")
          .append("            const txt = await res.text();\n")
          .append("            const trimmed = txt.trim();\n")
          .append("            if (trimmed.startsWith('{')) {\n")
          .append("                const j = JSON.parse(trimmed);\n")
          .append("                return j.error || j.message || ('HTTP ' + res.status);\n")
          .append("            }\n")
          .append("            return 'HTTP ' + res.status;\n")
          .append("        } catch (e) {\n")
          .append("            return 'HTTP ' + res.status;\n")
          .append("        }\n")
          .append("    }\n")
          .append("\n")

          // ---------- RECHARGEMENT COMPLET DE LA PAGE ----------
          // C'est LA clé : après succès, on recharge /admin/classes
          // → l'interface reflète TOUJOURS l'état serveur.
          .append("    function rechargerPage() {\n")
          .append("        window.location.href = '/admin/classes';\n")
          .append("    }\n")
          .append("\n")

          // ---------- SUPPRESSION ----------
          .append("    function deleteClasse(code, nom) {\n")
          .append("        if (!CAN_DELETE) {\n")
          .append("            window.showToast('Suppression non autorisée', 'error');\n")
          .append("            return;\n")
          .append("        }\n")
          .append("        if (!confirm('Voulez-vous vraiment supprimer la classe \"' + nom + '\" ?')) return;\n")
          .append("\n")
          .append("        showLoading();\n")
          .append("        fetchAvecTimeout('/admin/classes', {\n")
          .append("            method: 'POST',\n")
          .append("            headers: {\n")
          .append("                'Content-Type': 'application/x-www-form-urlencoded',\n")
          .append("                'X-Requested-With': 'XMLHttpRequest'\n")
          .append("            },\n")
          .append("            body: 'action=delete&codeClasse=' + encodeURIComponent(code),\n")
          .append("            redirect: 'manual'\n")
          .append("        })\n")
          .append("        .then(function(res) {\n")
          .append("            hideLoading();\n")
          .append("            if (res.status >= 400) {\n")
          .append("                return extraireErreur(res).then(function(msg) {\n")
          .append("                    window.showToast('Erreur : ' + msg, 'error');\n")
          .append("                });\n")
          .append("            }\n")
          // ✅ Succès → rechargement complet
          .append("            rechargerPage();\n")
          .append("        })\n")
          .append("        .catch(function(err) {\n")
          .append("            hideLoading();\n")
          .append("            if (err.name === 'AbortError') {\n")
          .append("                window.showToast('Délai dépassé — le serveur ne répond pas', 'error');\n")
          .append("            } else {\n")
          .append("                window.showToast('Erreur réseau : ' + err.message, 'error');\n")
          .append("            }\n")
          .append("        });\n")
          .append("    }\n")
          .append("\n")

          // ---------- DÉLÉGATION D'ÉVÉNEMENTS (attachée UNE SEULE FOIS) ----------
          .append("    function attachDelegatedEvents() {\n")
          .append("        const tbody = document.getElementById('classesBody');\n")
          .append("        if (!tbody || tbody.dataset.delegated === '1') return;\n")
          .append("        tbody.dataset.delegated = '1';\n")
          .append("\n")
          .append("        tbody.addEventListener('click', function(ev) {\n")
          .append("            const btnEdit = ev.target.closest('.btn-edit');\n")
          .append("            if (btnEdit) {\n")
          .append("                const code = btnEdit.dataset.code;\n")
          .append("                if (typeof window.openEditClasseModal === 'function') {\n")
          .append("                    window.openEditClasseModal(code);\n")
          .append("                } else {\n")
          .append("                    window.showToast('Fonction d\\'édition non disponible', 'error');\n")
          .append("                }\n")
          .append("                return;\n")
          .append("            }\n")
          .append("            const btnDelete = ev.target.closest('.btn-delete');\n")
          .append("            if (btnDelete) {\n")
          .append("                deleteClasse(btnDelete.dataset.code, btnDelete.dataset.nom);\n")
          .append("            }\n")
          .append("        });\n")
          .append("    }\n")
          .append("\n")

          // ---------- SOUMISSION DU FORMULAIRE MODAL ----------
          // ✅ On n'envoie PLUS ajax=1 : on veut une redirection 302 classique.
          .append("    window.attachFormSubmit = function() {\n")
          .append("        const form = document.getElementById('classeForm');\n")
          .append("        if (!form || form.dataset.ajaxAttached === '1') return;\n")
          .append("        form.dataset.ajaxAttached = '1';\n")
          .append("\n")
          .append("        form.addEventListener('submit', function(ev) {\n")
          .append("            ev.preventDefault();\n")
          .append("            if (submitting) return;\n")
          .append("\n")
          .append("            // Validation côté client des select\n")
          .append("            const annee = (document.getElementById('anneeAcademique') || {}).value || '';\n")
          .append("            const periode = (document.getElementById('periode') || {}).value || '';\n")
          .append("            const promotion = (document.getElementById('promotion') || {}).value || '';\n")
          .append("            const nom = (document.getElementById('nomClasse') || {}).value || '';\n")
          .append("\n")
          .append("            if (!nom.trim()) {\n")
          .append("                window.showToast('Le nom de la classe est requis', 'error');\n")
          .append("                return;\n")
          .append("            }\n")
          .append("            if (!annee || !periode || !promotion) {\n")
          .append("                window.showToast('Veuillez sélectionner année, période et promotion', 'error');\n")
          .append("                return;\n")
          .append("            }\n")
          .append("\n")
          .append("            submitting = true;\n")
          .append("            showLoading();\n")
          .append("\n")
          .append("            const formData = new FormData(form);\n")
          // ⛔ NE PAS AJOUTER ajax=1 → on veut une 302 redirect
          .append("            const body = new URLSearchParams(formData).toString();\n")
          .append("\n")
          .append("            fetchAvecTimeout('/admin/classes', {\n")
          .append("                method: 'POST',\n")
          .append("                headers: {\n")
          .append("                    'Content-Type': 'application/x-www-form-urlencoded',\n")
          .append("                    'X-Requested-With': 'XMLHttpRequest'\n")
          .append("                },\n")
          .append("                body: body,\n")
          .append("                redirect: 'manual'\n")
          .append("            })\n")
          .append("            .then(function(res) {\n")
          .append("                hideLoading();\n")
          .append("                submitting = false;\n")
          .append("                if (res.status >= 400) {\n")
          .append("                    return extraireErreur(res).then(function(msg) {\n")
          .append("                        window.showToast('Erreur : ' + msg, 'error');\n")
          .append("                    });\n")
          .append("                }\n")
          // ✅ Succès → fermer modal et recharger
          .append("                if (typeof window.closeClasseModal === 'function') {\n")
          .append("                    window.closeClasseModal();\n")
          .append("                }\n")
          .append("                rechargerPage();\n")
          .append("            })\n")
          .append("            .catch(function(err) {\n")
          .append("                hideLoading();\n")
          .append("                submitting = false;\n")
          .append("                if (err.name === 'AbortError') {\n")
          .append("                    window.showToast('Délai dépassé — le serveur ne répond pas', 'error');\n")
          .append("                } else {\n")
          .append("                    window.showToast('Erreur réseau : ' + err.message, 'error');\n")
          .append("                }\n")
          .append("            });\n")
          .append("        });\n")
          .append("    };\n")
          .append("\n")

          // ---------- INITIALISATION ----------
          .append("    document.addEventListener('DOMContentLoaded', function() {\n")
          .append("        attachDelegatedEvents();\n")
          .append("        window.attachFormSubmit();\n")
          .append("\n")
          .append("        const btnAjouter = document.getElementById('btnAjouter');\n")
          .append("        if (btnAjouter) {\n")
          .append("            btnAjouter.addEventListener('click', function() {\n")
          .append("                if (typeof window.openCreateClasseModal === 'function') {\n")
          .append("                    window.openCreateClasseModal();\n")
          .append("                } else {\n")
          .append("                    if (typeof window.resetClasseForm === 'function') window.resetClasseForm();\n")
          .append("                    if (typeof window.openClasseModal === 'function') {\n")
          .append("                        window.openClasseModal();\n")
          .append("                    } else {\n")
          .append("                        window.showToast('Modal indisponible', 'error');\n")
          .append("                    }\n")
          .append("                }\n")
          .append("            });\n")
          .append("        }\n")
          .append("\n")
          // Afficher les messages d'alerte en toast puis les retirer
          .append("        const alertSuccess = document.querySelector('.alert-success');\n")
          .append("        const alertError = document.querySelector('.alert-danger');\n")
          .append("        if (alertSuccess) {\n")
          .append("            const msg = alertSuccess.textContent.trim();\n")
          .append("            if (msg) window.showToast(msg, 'success');\n")
          .append("            alertSuccess.remove();\n")
          .append("        }\n")
          .append("        if (alertError) {\n")
          .append("            const msg = alertError.textContent.trim();\n")
          .append("            if (msg) window.showToast(msg, 'error');\n")
          .append("            alertError.remove();\n")
          .append("        }\n")
          .append("    });\n")
          .append("})();\n")
          .append("</script>\n");
    }

    // ============================================================
    // FORMATAGE NIVEAU
    // ============================================================
    private static String formatNiveau(int niveau) {
        if (niveau <= 0) return "-";
        if (niveau == 1) return "1ère";
        return niveau + "ème";
    }

    // ============================================================
    // ÉCHAPPEMENT
    // ============================================================
    private static String echapperHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }

    private static String echapperJs(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                    .replace("'", "\\'")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r");
    }
}