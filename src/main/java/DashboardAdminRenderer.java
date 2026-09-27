import java.util.List;

public class DashboardAdminRenderer {

    private static final String DEFAULT_LOGO = DashboardConstants.DEFAULT_LOGO;
    private static final String PHOTOS_URL_PATH = DashboardConstants.PHOTOS_URL_PATH;

    public String genererDashboard(List<Module> modules, String nomInstitution, String logoPath,
                                    String utilisateurId, String institutionId) {
        if (nomInstitution == null) nomInstitution = "Institution";
        if (logoPath == null) logoPath = DashboardConstants.DEFAULT_LOGO;
        if (modules == null) modules = List.of();

        String normalizedLogoPath = normaliserLogoPath(logoPath);
        String cacheBuster = "?v=" + System.currentTimeMillis();
        String displayName = utilisateurId != null ? utilisateurId : "Administrateur";

        StringBuilder sb = new StringBuilder();
        String nomInstitutionEscaped = echapperHtml(nomInstitution);
        String displayNameEscaped = echapperHtml(displayName);
        String logoPathEscaped = normalizedLogoPath.replace("\"", "\\\"") + cacheBuster;
        String defaultLogoEscaped = DashboardConstants.DEFAULT_LOGO.replace("\"", "\\\"");

        // ============================================================
        // HEAD
        // ============================================================
        sb.append("<!DOCTYPE html>\n");
        sb.append("<html lang=\"fr\" style=\"height:100%; margin:0;\">\n");
        sb.append("<head>\n");
        sb.append("    <meta charset=\"UTF-8\">\n");
        sb.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover\">\n");
        sb.append("    <meta http-equiv=\"Cache-Control\" content=\"no-store, no-cache, must-revalidate\">\n");
        sb.append("    <meta http-equiv=\"Pragma\" content=\"no-cache\">\n");
        sb.append("    <meta http-equiv=\"Expires\" content=\"0\">\n");
        sb.append("    <meta name=\"theme-color\" content=\"#dbeafe\">\n");
        sb.append("    <title>Dashboard - ").append(nomInstitutionEscaped).append("</title>\n");
        sb.append("    <link href=\"https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&display=swap\" rel=\"stylesheet\">\n");
        sb.append("    <link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css\">\n");

        // ============================================================
        // STYLES — 4 modules par ligne à TOUS les breakpoints
        // ============================================================
        sb.append("""
            <style>
                :root {
                    --primary: #3b82f6;
                    --primary-hover: #60a5fa;
                    --primary-dark: #2563eb;
                    --accent: #fbbf24;
                    --accent-glow: rgba(251, 191, 36, 0.28);
                    --danger: #ef4444;
                    --bg-main: #eff6ff;
                    --bg-surface: #dbeafe;
                    --bg-card: #ffffff;
                    --bg-card-hover: #f8fbff;
                    --text-main: #1e293b;
                    --text-muted: #64748b;
                    --border-subtle: rgba(147, 197, 253, 0.45);
                    --border-hover: rgba(59, 130, 246, 0.55);
                    --header-bg: linear-gradient(135deg, #ffffff 0%, #eff6ff 58%, #dbeafe 100%);
                    --safe-top: env(safe-area-inset-top, 0px);
                    --safe-bottom: env(safe-area-inset-bottom, 0px);
                    --safe-left: env(safe-area-inset-left, 0px);
                    --safe-right: env(safe-area-inset-right, 0px);

                    /* ✅ GAP unique utilisé partout (calculé pour 4 modules/ligne) */
                    --grid-gap: clamp(6px, 1.2vw, 16px);
                }

                * { margin: 0; padding: 0; box-sizing: border-box; }

                html, body {
                    width: 100%;
                    height: 100vh;
                    height: 100dvh;
                    min-height: 100vh;
                    min-height: 100dvh;
                    margin: 0;
                    padding: 0;
                    overflow: hidden;
                    font-family: 'Plus Jakarta Sans', sans-serif;
                    background: var(--bg-main);
                    color: var(--text-main);
                    display: flex;
                    flex-direction: column;
                    overscroll-behavior: none;
                    -webkit-text-size-adjust: 100%;
                    -webkit-tap-highlight-color: transparent;
                    touch-action: manipulation;
                }

                /* ============================================================
                   HEADER
                   ============================================================ */
                .header {
                    background: var(--header-bg);
                    padding: calc(16px + var(--safe-top)) 28px 16px;
                    padding-left: calc(28px + var(--safe-left));
                    padding-right: calc(28px + var(--safe-right));
                    flex-shrink: 0;
                    display: flex;
                    flex-direction: column;
                    align-items: center;
                    justify-content: center;
                    position: relative;
                    border-bottom: 1px solid var(--border-subtle);
                    box-shadow: 0 10px 30px -5px rgba(37, 99, 235, 0.16);
                    z-index: 10;
                    text-align: center;
                    max-height: 220px;
                    overflow: hidden;
                    transition:
                        max-height 0.35s cubic-bezier(0.4, 0, 0.2, 1),
                        padding    0.35s cubic-bezier(0.4, 0, 0.2, 1),
                        opacity    0.25s ease,
                        box-shadow 0.35s ease,
                        border-bottom-color 0.35s ease;
                }
                body.module-active .header {
                    max-height: 0;
                    padding-top: 0;
                    padding-bottom: 0;
                    opacity: 0;
                    border-bottom-color: transparent;
                    box-shadow: none;
                    pointer-events: none;
                }
                .header-left {
                    display: flex;
                    flex-direction: column;
                    align-items: center;
                    gap: 8px;
                }
                .header-logo {
                    width: 52px;
                    height: 52px;
                    border-radius: 14px;
                    object-fit: contain;
                    background: #ffffff;
                    padding: 6px;
                    border: 2px solid var(--accent);
                    box-shadow: 0 8px 20px rgba(37, 99, 235, 0.18), 0 0 15px var(--accent-glow);
                }
                .header-title {
                    color: var(--text-main);
                    font-size: 17px;
                    font-weight: 700;
                    letter-spacing: -0.3px;
                    text-shadow: 0 2px 4px rgba(59, 130, 246, 0.12);
                }
                .header-admin {
                    position: absolute;
                    right: 24px;
                    top: 50%;
                    transform: translateY(-50%);
                    color: var(--text-muted);
                    font-size: 12px;
                    display: flex;
                    align-items: center;
                    gap: 16px;
                }
                .header-admin span { color: var(--text-main); font-weight: 600; }
                .header-admin i { color: var(--accent); }
                .btn-logout {
                    background: rgba(239, 68, 68, 0.1);
                    border: 1px solid rgba(239, 68, 68, 0.3);
                    color: #dc2626;
                    padding: 7px 16px;
                    border-radius: 10px;
                    font-size: 11px;
                    font-weight: 600;
                    cursor: pointer;
                    transition: all 0.25s ease;
                    display: flex;
                    align-items: center;
                    gap: 6px;
                    font-family: inherit;
                }
                .btn-logout:hover {
                    background: #ef4444;
                    border-color: #ef4444;
                    color: white;
                    box-shadow: 0 6px 16px rgba(239, 68, 68, 0.4);
                }

                /* ============================================================
                   CONTAINER PRINCIPAL
                   ============================================================ */
                .container {
                    flex: 1;
                    width: 100%;
                    display: flex;
                    flex-direction: column;
                    overflow: hidden;
                    position: relative;
                    min-height: 0;
                    background:
                        radial-gradient(circle at 0% 0%, rgba(96, 165, 250, 0.22), transparent 35%),
                        radial-gradient(circle at 100% 100%, rgba(251, 191, 36, 0.18), transparent 32%),
                        var(--bg-main);
                }

                /* ============================================================
                   GRILLE DE MODULES — 4 PAR LIGNE PARTOUT
                   ============================================================ */
                .modules-grid-wrapper {
                    flex: 1;
                    padding: clamp(10px, 2vw, 24px);
                    padding-left: calc(clamp(10px, 2vw, 24px) + var(--safe-left));
                    padding-right: calc(clamp(10px, 2vw, 24px) + var(--safe-right));
                    background: transparent;
                    overflow-y: auto;
                    transition: all 0.3s ease-in-out;
                    min-height: 0;
                    -webkit-overflow-scrolling: touch;
                }
                .modules-grid-wrapper::-webkit-scrollbar { width: 6px; }
                .modules-grid-wrapper::-webkit-scrollbar-thumb {
                    background: rgba(255, 255, 255, 0.15);
                    border-radius: 4px;
                }
                .modules-grid-wrapper::-webkit-scrollbar-thumb:hover {
                    background: rgba(255, 255, 255, 0.3);
                }
                .modules-grid-wrapper.hidden {
                    flex: 0;
                    max-height: 0;
                    padding: 0 20px;
                    overflow: hidden;
                    opacity: 0;
                }

                .modules-grid {
                    display: flex;
                    flex-wrap: wrap;
                    gap: var(--grid-gap);
                    width: 100%;
                    max-width: none;
                    margin: 0;
                    align-content: flex-start;
                }

                /* ✅ 4 par ligne — formule : 25% - (3*gap/4) */
                .module-card {
                    flex: 0 0 calc(25% - (var(--grid-gap) * 3 / 4));
                    max-width: calc(25% - (var(--grid-gap) * 3 / 4));
                    min-width: 0;
                    box-sizing: border-box;
                    overflow: hidden;
                    background: rgba(255, 255, 255, 0.94);
                    border-radius: 16px;
                    padding: 18px 10px;
                    text-align: center;
                    text-decoration: none;
                    color: var(--text-main);
                    border: 1px solid var(--border-subtle);
                    transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
                    cursor: pointer;
                    display: flex;
                    flex-direction: column;
                    align-items: center;
                    justify-content: center;
                    position: relative;
                    box-shadow: 0 8px 22px -8px rgba(37, 99, 235, 0.28);
                }
                .module-card::before {
                    content: '';
                    position: absolute;
                    top: 0; left: 0; width: 100%; height: 3px;
                    background: linear-gradient(90deg, var(--primary), var(--accent));
                    opacity: 0;
                    transition: opacity 0.25s ease;
                }
                .module-card:hover {
                    transform: translateY(-5px);
                    background: var(--bg-card-hover);
                    box-shadow: 0 16px 32px -4px rgba(37, 99, 235, 0.24), 0 0 20px rgba(251, 191, 36, 0.16);
                    border-color: var(--border-hover);
                }
                .module-card:hover::before { opacity: 1; }

                .module-card .icon-circle {
                    width: 50px;
                    height: 50px;
                    border-radius: 12px;
                    background: linear-gradient(135deg, var(--primary-dark), var(--primary-hover));
                    color: white;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    font-size: 20px;
                    margin-bottom: 10px;
                    box-shadow: 0 8px 20px rgba(59, 130, 246, 0.28);
                    border: 1px solid rgba(255, 255, 255, 0.1);
                    flex-shrink: 0;
                }
                .module-card .module-name {
                    font-size: 12px;
                    font-weight: 700;
                    color: var(--text-main);
                    line-height: 1.25;
                    letter-spacing: -0.2px;
                    overflow: hidden;
                    text-overflow: ellipsis;
                    display: -webkit-box;
                    -webkit-line-clamp: 2;
                    -webkit-box-orient: vertical;
                    word-wrap: break-word;
                    max-width: 100%;
                    width: 100%;
                }
                .module-card .module-desc {
                    font-size: 10px;
                    color: var(--text-muted);
                    margin-top: 4px;
                    overflow: hidden;
                    text-overflow: ellipsis;
                    display: -webkit-box;
                    -webkit-line-clamp: 2;
                    -webkit-box-orient: vertical;
                    line-height: 1.3;
                    max-width: 100%;
                }

                .empty-modules {
                    width: 100%;
                    text-align: center;
                    padding: 50px;
                    color: var(--text-muted);
                    background: var(--bg-card);
                    border-radius: 16px;
                    border: 1px solid var(--border-subtle);
                }

                /* ============================================================
                   IFRAME MODULE
                   ============================================================ */
                .module-content {
                    flex: 1;
                    width: 100%;
                    background: var(--bg-main);
                    position: relative;
                    display: none;
                    min-height: 0;
                }
                .container.active-module .module-content { display: block; }
                .container.active-module .modules-grid-wrapper { display: none; }
                .module-content iframe {
                    width: 100%;
                    height: 100%;
                    border: none;
                    background: white;
                    display: block;
                }

                /* ============================================================
                   BOTTOM DOCK
                   ============================================================ */
                .bottom-dock {
                    flex-shrink: 0;
                    display: flex;
                    width: 100%;
                    height: calc(66px + var(--safe-bottom));
                    padding-bottom: var(--safe-bottom);
                    padding-left: calc(10px + var(--safe-left));
                    padding-right: calc(10px + var(--safe-right));
                    background: rgba(255, 255, 255, 0.94);
                    backdrop-filter: blur(12px);
                    -webkit-backdrop-filter: blur(12px);
                    border-top: 1px solid var(--border-subtle);
                    justify-content: flex-start;
                    align-items: center;
                    box-shadow: 0 -10px 30px rgba(37, 99, 235, 0.14);
                    overflow-x: auto;
                    overflow-y: hidden;
                    scroll-behavior: smooth;
                    z-index: 10;
                    gap: 6px;
                    scrollbar-width: thin;
                    scrollbar-color: rgba(255, 255, 255, 0.2) transparent;
                    -webkit-overflow-scrolling: touch;
                }
                .bottom-dock::-webkit-scrollbar { height: 4px; }
                .bottom-dock::-webkit-scrollbar-track { background: transparent; }
                .bottom-dock::-webkit-scrollbar-thumb {
                    background: rgba(255, 255, 255, 0.2);
                    border-radius: 4px;
                }
                .bottom-dock > .dock-item:first-child { margin-left: auto; }
                .bottom-dock > .dock-item:last-child  { margin-right: auto; }

                .dock-item {
                    display: flex;
                    flex-direction: column;
                    align-items: center;
                    justify-content: center;
                    color: var(--text-muted);
                    text-decoration: none;
                    font-size: 10px;
                    font-weight: 500;
                    min-width: 60px;
                    max-width: 84px;
                    height: calc(100% - 10px);
                    max-height: 56px;
                    padding: 5px 4px;
                    transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
                    border-radius: 12px;
                    flex-shrink: 0;
                    box-sizing: border-box;
                    cursor: pointer;
                    user-select: none;
                    -webkit-user-select: none;
                    border: 1px solid transparent;
                }
                .dock-item i {
                    font-size: 16px;
                    margin-bottom: 2px;
                    color: #cbd5e1;
                    transition: transform 0.2s, color 0.2s;
                }
                .dock-item span {
                    white-space: nowrap;
                    overflow: hidden;
                    text-overflow: ellipsis;
                    max-width: 100%;
                    line-height: 1.1;
                }
                .dock-item:hover {
                    color: var(--text-main);
                    background: rgba(255, 255, 255, 0.04);
                    border-color: rgba(255, 255, 255, 0.06);
                }
                .dock-item.active {
                    color: white;
                    color: var(--primary-dark);
                    background: linear-gradient(135deg, rgba(219, 234, 254, 0.9), rgba(254, 243, 199, 0.9));
                    border-color: rgba(59, 130, 246, 0.35);
                    box-shadow: 0 4px 12px rgba(59, 130, 246, 0.16);
                }
                .dock-item:hover i,
                .dock-item.active i {
                    color: var(--accent);
                    transform: translateY(-2px);
                }

                /* ============================================================
                   RESPONSIVE — ajustement du gap, PAS du nombre de colonnes
                   ============================================================ */

                /* Tablette : on réduit le padding pour laisser respirer */
                @media (max-width: 1024px) {
                    .modules-grid-wrapper { padding: 16px; }
                    :root { --grid-gap: 10px; }
                }

                /* Mobile : on garde 4 par ligne, on réduit les cartes */
                @media (max-width: 768px) {
                    .header {
                        padding: calc(12px + var(--safe-top)) 16px 12px;
                        padding-left: calc(16px + var(--safe-left));
                        padding-right: calc(16px + var(--safe-right));
                        max-height: 190px;
                    }
                    .header-logo { width: 42px; height: 42px; border-radius: 10px; }
                    .header-title { font-size: 14px; }
                    .header-admin {
                        position: static;
                        transform: none;
                        margin-top: 8px;
                        font-size: 11px;
                        gap: 10px;
                    }
                    .btn-logout { padding: 5px 10px; font-size: 10px; }

                    .modules-grid-wrapper {
                        padding: 10px;
                        padding-left: calc(10px + var(--safe-left));
                        padding-right: calc(10px + var(--safe-right));
                    }
                    :root { --grid-gap: 8px; }

                    .module-card {
                        padding: 12px 6px;
                        border-radius: 12px;
                    }
                    .module-card .icon-circle {
                        width: 38px;
                        height: 38px;
                        font-size: 15px;
                        margin-bottom: 6px;
                        border-radius: 10px;
                    }
                    .module-card .module-name { font-size: 10px; line-height: 1.2; }
                    .module-card .module-desc { display: none; }

                    .bottom-dock {
                        height: calc(62px + var(--safe-bottom));
                        padding-left: calc(6px + var(--safe-left));
                        padding-right: calc(6px + var(--safe-right));
                        gap: 4px;
                    }
                    .dock-item {
                        min-width: 56px;
                        max-width: 72px;
                        padding: 4px 3px;
                        font-size: 9px;
                    }
                    .dock-item i { font-size: 14px; margin-bottom: 1px; }
                }

                /* Petit mobile : on garde 4 par ligne, on réduit encore */
                @media (max-width: 400px) {
                    .header {
                        max-height: 160px;
                        padding: calc(8px + var(--safe-top)) 10px 8px;
                    }
                    .header-logo { width: 36px; height: 36px; }
                    .header-title { font-size: 12px; }

                    .modules-grid-wrapper { padding: 8px; }
                    :root { --grid-gap: 6px; }

                    .module-card {
                        padding: 10px 4px;
                        border-radius: 10px;
                    }
                    .module-card .icon-circle {
                        width: 34px;
                        height: 34px;
                        font-size: 14px;
                        margin-bottom: 5px;
                        border-radius: 8px;
                    }
                    .module-card .module-name { font-size: 9px; line-height: 1.15; }

                    .bottom-dock { height: calc(56px + var(--safe-bottom)); }
                    .dock-item {
                        min-width: 50px;
                        max-width: 64px;
                        padding: 3px 2px;
                        font-size: 8px;
                    }
                    .dock-item i { font-size: 13px; }
                }

                /* Paysage mobile : la grille reste à 4 par ligne */
                @media (max-height: 500px) and (orientation: landscape) {
                    .header {
                        max-height: 80px;
                        padding: calc(6px + var(--safe-top)) 16px 6px;
                        flex-direction: row;
                        gap: 12px;
                    }
                    .header-left { flex-direction: row; gap: 10px; }
                    .header-logo { width: 30px; height: 30px; }
                    .header-title { font-size: 12px; }
                    .header-admin { display: none; }
                    .bottom-dock { height: calc(48px + var(--safe-bottom)); }
                    .dock-item {
                        min-width: 48px;
                        max-width: 60px;
                        padding: 3px 2px;
                    }
                    .dock-item i { font-size: 13px; margin: 0; }
                    .dock-item span { font-size: 8px; }

                    .module-card .icon-circle {
                        width: 32px;
                        height: 32px;
                        font-size: 13px;
                    }
                    .module-card .module-name { font-size: 9px; }
                }
            </style>
        """);
        sb.append("</head>\n<body>\n");

        // ============================================================
        // HEADER
        // ============================================================
        sb.append("    <header class=\"header\" id=\"appHeader\">\n");
        sb.append("        <div class=\"header-left\">\n");
        sb.append("            <img src=\"").append(logoPathEscaped)
          .append("\" alt=\"Logo\" class=\"header-logo\" onerror=\"this.src='")
          .append(defaultLogoEscaped).append("'\">\n");
        sb.append("            <div class=\"header-title\">").append(nomInstitutionEscaped).append("</div>\n");
        sb.append("        </div>\n");
        sb.append("        <div class=\"header-admin\">\n");
        sb.append("            <div><i class=\"fas fa-user-circle\"></i> <span>")
          .append(displayNameEscaped).append("</span></div>\n");
        sb.append("            <button class=\"btn-logout\" type=\"button\" onclick=\"deconnexion()\">")
          .append("<i class=\"fas fa-sign-out-alt\"></i> Déconnexion</button>\n");
        sb.append("        </div>\n");
        sb.append("    </header>\n");

        // ============================================================
        // MAIN
        // ============================================================
        sb.append("    <main class=\"container\" id=\"mainContainer\">\n");

        // ----- Cartes de modules -----
        sb.append("        <div class=\"modules-grid-wrapper\" id=\"modulesGridWrapper\">\n");
        sb.append("            <div class=\"modules-grid\">\n");
        if (modules.isEmpty()) {
            sb.append("""
                    <div class="empty-modules">
                        <i class="fas fa-lock" style="font-size: 38px; margin-bottom: 12px; color: var(--accent);"></i>
                        <div style="font-weight: 600; color: var(--text-main); font-size: 15px;">Accès restreint</div>
                        <div style="font-size: 13px; margin-top: 4px; color: var(--text-muted);">Aucun module disponible pour vos permissions.</div>
                    </div>
            """);
        } else {
            for (Module module : modules) {
                String lienBrut = module.getLien() != null ? module.getLien() : "#";
                String lien = echapperHtml(lienBrut);
                String lienJs = echapperJs(lienBrut);
                String icone = module.getIcone() != null ? module.getIcone() : "fas fa-cube";
                String nom = module.getNom() != null ? module.getNom() : "Module";
                String description = module.getDescription() != null ? module.getDescription() : "";

                sb.append("            <a href=\"").append(lien)
                  .append("\" target=\"moduleFrame\" class=\"module-card\" ")
                  .append("onclick=\"event.preventDefault(); selectionnerModule('")
                  .append(lienJs).append("');\">\n");
                sb.append("                <div class=\"icon-circle\"><i class=\"")
                  .append(echapperHtml(icone)).append("\"></i></div>\n");
                sb.append("                <span class=\"module-name\">")
                  .append(echapperHtml(nom)).append("</span>\n");
                if (!description.isEmpty()) {
                    sb.append("                <span class=\"module-desc\">")
                      .append(echapperHtml(description)).append("</span>\n");
                }
                sb.append("            </a>\n");
            }
        }
        sb.append("            </div>\n");
        sb.append("        </div>\n");

        // ----- Iframe -----
        sb.append("        <div class=\"module-content\">\n");
        sb.append("            <iframe name=\"moduleFrame\" id=\"moduleFrame\"></iframe>\n");
        sb.append("        </div>\n");

        sb.append("    </main>\n");

        // ============================================================
        // BOTTOM DOCK
        // ============================================================
        sb.append("    <nav class=\"bottom-dock\" id=\"bottomDock\">\n");
        sb.append("        <a href=\"#\" target=\"moduleFrame\" class=\"dock-item active\" ")
          .append("onclick=\"event.preventDefault(); retourAccueil(event);\">")
          .append("<i class=\"fas fa-home\"></i><span>Accueil</span></a>\n");

        for (Module module : modules) {
            String lienBrut = module.getLien() != null ? module.getLien() : "#";
            String lien = echapperHtml(lienBrut);
            String lienJs = echapperJs(lienBrut);
            String icone = module.getIcone() != null ? module.getIcone() : "fas fa-cube";
            String nom = module.getNom() != null ? module.getNom() : "Module";

            sb.append("        <a href=\"").append(lien)
              .append("\" target=\"moduleFrame\" class=\"dock-item\" data-lien=\"")
              .append(lien).append("\" ")
              .append("onclick=\"event.preventDefault(); selectionnerDockItem(this, '")
              .append(lienJs).append("');\">\n");
            sb.append("            <i class=\"").append(echapperHtml(icone)).append("\"></i>\n");
            sb.append("            <span>").append(echapperHtml(nom)).append("</span>\n");
            sb.append("        </a>\n");
        }
        sb.append("    </nav>\n");

        // ============================================================
        // SCRIPT
        // ============================================================
        sb.append("""
            <script>
                (function() {
                    'use strict';

                    let moduleActif = null;
                    let navigationEnCours = false;

                    function getFrame() {
                        return document.getElementById('moduleFrame');
                    }

                    function activerModeModule() {
                        document.body.classList.add('module-active');
                        const c = document.getElementById('mainContainer');
                        if (c) c.classList.add('active-module');
                    }

                    function desactiverModeModule() {
                        document.body.classList.remove('module-active');
                        const c = document.getElementById('mainContainer');
                        if (c) c.classList.remove('active-module');
                    }

                    function setActiveDock(lien) {
                        document.querySelectorAll('.dock-item').forEach(function(el) {
                            el.classList.remove('active');
                        });
                        if (lien === null) {
                            const home = document.querySelector('.dock-item');
                            if (home) home.classList.add('active');
                            return;
                        }
                        const dock = document.querySelector('.dock-item[data-lien="' + cssEscape(lien) + '"]');
                        if (dock) {
                            dock.classList.add('active');
                            dock.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'center' });
                        }
                    }

                    function cssEscape(s) {
                        if (window.CSS && CSS.escape) return CSS.escape(s);
                        return s.replace(/["\\\\]/g, '\\\\$&');
                    }

                    function chargerModule(lien) {
                        const frame = getFrame();
                        if (!frame) return;
                        if (frame.src && frame.src.indexOf(lien) !== -1) {
                            frame.contentWindow.location.reload();
                        } else {
                            frame.src = lien;
                        }
                    }

                    window.selectionnerModule = function(lien) {
                        if (navigationEnCours) return;
                        if (moduleActif === lien) return;
                        navigationEnCours = true;
                        setTimeout(function() { navigationEnCours = false; }, 400);

                        moduleActif = lien;
                        activerModeModule();
                        setActiveDock(lien);
                        chargerModule(lien);
                    };

                    window.selectionnerDockItem = function(element, lien) {
                        if (navigationEnCours) return;
                        if (moduleActif === lien) return;
                        navigationEnCours = true;
                        setTimeout(function() { navigationEnCours = false; }, 400);

                        moduleActif = lien;
                        activerModeModule();
                        setActiveDock(lien);
                        chargerModule(lien);
                    };

                    window.retourAccueil = function(event) {
                        if (event) event.preventDefault();
                        moduleActif = null;
                        navigationEnCours = false;
                        desactiverModeModule();

                        const frame = getFrame();
                        if (frame) frame.src = 'about:blank';

                        setActiveDock(null);
                    };

                    function resetEtat() {
                        moduleActif = null;
                        navigationEnCours = false;

                        document.body.classList.remove('module-active');
                        const c = document.getElementById('mainContainer');
                        if (c) c.classList.remove('active-module');

                        setActiveDock(null);
                    }

                    if (document.readyState === 'loading') {
                        document.addEventListener('DOMContentLoaded', resetEtat);
                    } else {
                        resetEtat();
                    }

                    window.addEventListener('pageshow', function(e) {
                        if (e.persisted) resetEtat();
                    });

                    window.addEventListener('beforeunload', function() {
                        navigationEnCours = true;
                    });
                })();
            </script>
        </body>
        </html>
        """);

        return sb.toString();
    }

    // ============================================================
    // LOGO
    // ============================================================
    private String normaliserLogoPath(String logoPath) {
        if (logoPath == null || logoPath.isBlank()) {
            return DEFAULT_LOGO;
        }
        String normalized = logoPath.trim().replace("\\", "/");
        if (normalized.startsWith("http://")
                || normalized.startsWith("https://")
                || normalized.startsWith("data:")) {
            return normalized;
        }
        while (normalized.contains("//")) {
            normalized = normalized.replace("//", "/");
        }
        if (normalized.startsWith(DashboardConstants.PHOTOS_DIR)) {
            normalized = "/" + normalized;
        }
        if (!normalized.startsWith(PHOTOS_URL_PATH)) {
            String fileName = normalized.substring(normalized.lastIndexOf("/") + 1);
            if (!fileName.isBlank() && fileName.startsWith("logo_")) {
                normalized = PHOTOS_URL_PATH + fileName;
            } else {
                return DEFAULT_LOGO;
            }
        }
        return normalized;
    }

    // ============================================================
    // ÉCHAPPEMENT
    // ============================================================
    private static String echapperHtml(String input) {
        if (input == null) return "";
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String echapperJs(String input) {
        if (input == null) return "";
        return input
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("</", "<\\/");
    }
}