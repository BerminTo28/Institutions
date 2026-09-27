public class UIParametresProfil {

    public static String rendreFormulaire(Institution institution, String messageErreur,
                                            String messageSucces) {
        if (institution == null) institution = new Institution();

        // ======================= Préparation =======================
        String logoPath = preparerLogoPath(institution.getLogoInstitution());
        String nomInstitution = valeurOuDefaut(institution.getNomInstitution(), "Institution");
        String sigleInstitution = valeurOuDefaut(institution.getSigleInstitution(),
                "Établissement d'enseignement");
        String statut = valeurOuDefaut(institution.getStatutInstitution(), "ACTIF");
        boolean estActif = "ACTIF".equalsIgnoreCase(statut);

        StringBuilder sb = new StringBuilder();

        // ======================= HEAD =======================
        sb.append("<!DOCTYPE html>");
        sb.append("<html lang=\"fr\">");
        sb.append("<head>");
        sb.append("<meta charset=\"UTF-8\">");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        sb.append("<title>").append(echapperHtml(nomInstitution)).append(" — Paramètres</title>");
        sb.append("<link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css\">");
        sb.append("<link rel=\"preconnect\" href=\"https://fonts.googleapis.com\">");
        sb.append("<link rel=\"preconnect\" href=\"https://fonts.gstatic.com\" crossorigin>");
        sb.append("<link href=\"https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800;900&display=swap\" rel=\"stylesheet\">");

        // ======================= STYLE =======================
        sb.append("""
        <style>
        * { margin:0; padding:0; box-sizing:border-box; }
        :root {
            --primary:#4f46e5;
            --primary-light:#6366f1;
            --primary-dark:#4338ca;
            --primary-bg:#eef2ff;
            --accent:#06b6d4;
            --success:#10b981;
            --success-bg:#ecfdf5;
            --warning:#f59e0b;
            --error:#ef4444;
            --error-bg:#fef2f2;
            --text-dark:#0f172a;
            --text-mid:#475569;
            --text-light:#94a3b8;
            --border:#e2e8f0;
            --border-light:#f1f5f9;
            --bg:#f8fafc;
            --card:#ffffff;
            --radius:18px;
            --radius-sm:12px;
            --radius-xs:8px;
            --shadow-sm:0 1px 2px rgba(0,0,0,0.03);
            --shadow-md:0 4px 20px rgba(0,0,0,0.05);
            --shadow-lg:0 20px 50px -12px rgba(79,70,229,0.25);
        }
        [data-theme="dark"] {
            --primary:#6366f1;
            --primary-light:#818cf8;
            --primary-dark:#4f46e5;
            --primary-bg:#1e1b4b;
            --text-dark:#f1f5f9;
            --text-mid:#cbd5e1;
            --text-light:#64748b;
            --border:#334155;
            --border-light:#1e293b;
            --bg:#0f172a;
            --card:#1e293b;
        }
        html { scroll-behavior:smooth; }
        body {
            font-family:'Inter', system-ui, -apple-system, sans-serif;
            background:var(--bg);
            color:var(--text-dark);
            padding:24px 16px;
            line-height:1.6;
            min-height:100vh;
            transition:background 0.3s, color 0.3s;
            -webkit-font-smoothing:antialiased;
        }
        .container { max-width:1200px; margin:0 auto; }

        /* ========== HEADER PREMIUM (CENTRÉ) ========== */
        .hero {
            background:linear-gradient(135deg, #4f46e5 0%, #06b6d4 100%);
            border-radius:24px;
            padding:48px 36px 40px;
            margin-bottom:24px;
            position:relative;
            box-shadow:var(--shadow-lg);
            overflow:hidden;
        }
        .hero::before {
            content:"";
            position:absolute; top:-50%; right:-10%;
            width:500px; height:500px;
            background:radial-gradient(circle, rgba(255,255,255,0.2), transparent 70%);
            border-radius:50%;
            animation:float 8s ease-in-out infinite;
        }
        .hero::after {
            content:"";
            position:absolute; bottom:-30%; left:-5%;
            width:300px; height:300px;
            background:radial-gradient(circle, rgba(255,255,255,0.1), transparent 70%);
            border-radius:50%;
            animation:float 10s ease-in-out infinite reverse;
        }
        @keyframes float {
            0%, 100% { transform:translate(0, 0) scale(1); }
            50% { transform:translate(20px, -20px) scale(1.05); }
        }

        /* ========== CONTENU CENTRÉ ========== */
        .hero-content {
            position:relative; z-index:1;
            display:flex;
            flex-direction:column;
            align-items:center;
            justify-content:center;
            text-align:center;
            gap:18px;
        }
        .hero-logo-wrap {
            position:relative;
            flex-shrink:0;
        }
        .hero-logo {
            width:110px;
            height:110px;
            border-radius:26px;
            object-fit:cover;
            border:4px solid rgba(255,255,255,0.5);
            background:white;
            box-shadow:0 14px 40px rgba(0,0,0,0.25);
            display:block;
            margin:0 auto;
        }
        .hero-logo-badge {
            position:absolute;
            bottom:-4px; right:-4px;
            width:30px; height:30px;
            border-radius:50%;
            background:var(--success);
            border:3px solid white;
            display:flex; align-items:center; justify-content:center;
            color:white; font-size:13px;
            box-shadow:0 4px 8px rgba(0,0,0,0.15);
        }
        .hero-logo-badge.inactive { background:var(--warning); }

        .hero-text {
            display:flex;
            flex-direction:column;
            align-items:center;
            gap:10px;
            max-width:720px;
        }
        .hero-text .badge {
            display:inline-flex; align-items:center; gap:6px;
            background:rgba(255,255,255,0.2);
            color:white;
            padding:5px 14px;
            border-radius:20px;
            font-size:11px;
            font-weight:700;
            text-transform:uppercase;
            letter-spacing:0.6px;
            backdrop-filter:blur(10px);
            border:1px solid rgba(255,255,255,0.25);
        }
        .hero-text h1 {
            font-size:34px;
            font-weight:800;
            color:white;
            letter-spacing:-0.8px;
            line-height:1.15;
            margin:0;
        }
        .hero-text .subtitle {
            color:rgba(255,255,255,0.88);
            font-size:14px;
            font-weight:500;
            display:flex;
            align-items:center;
            justify-content:center;
            gap:10px;
            flex-wrap:wrap;
        }
        .hero-text .subtitle i { opacity:0.75; }
        .hero-text .subtitle .dot {
            width:4px; height:4px; border-radius:50%;
            background:rgba(255,255,255,0.5);
        }

        .hero-actions {
            position:absolute;
            top:24px; right:24px;
            display:flex; gap:8px; z-index:2;
        }
        .icon-btn {
            width:38px; height:38px;
            border-radius:11px;
            background:rgba(255,255,255,0.15);
            color:white;
            display:flex; align-items:center; justify-content:center;
            text-decoration:none; border:none; cursor:pointer;
            transition:0.2s; backdrop-filter:blur(10px);
            border:1px solid rgba(255,255,255,0.25);
            font-size:14px;
        }
        .icon-btn:hover {
            background:rgba(255,255,255,0.28);
            transform:translateY(-2px);
        }

        /* ========== STATS RAPIDES ========== */
        .stats {
            display:grid;
            grid-template-columns:repeat(auto-fit, minmax(180px, 1fr));
            gap:12px;
            margin-bottom:24px;
        }
        .stat {
            background:var(--card);
            border-radius:var(--radius-sm);
            padding:16px 20px;
            border:1px solid var(--border);
            display:flex; align-items:center; gap:14px;
            transition:0.2s;
        }
        .stat:hover { border-color:var(--primary); transform:translateY(-2px); box-shadow:var(--shadow-md); }
        .stat-icon {
            width:40px; height:40px; border-radius:11px;
            display:flex; align-items:center; justify-content:center;
            font-size:16px; color:white; flex-shrink:0;
        }
        .stat-icon.indigo { background:linear-gradient(135deg, #4f46e5, #4338ca); }
        .stat-icon.cyan { background:linear-gradient(135deg, #06b6d4, #0891b2); }
        .stat-icon.amber { background:linear-gradient(135deg, #f59e0b, #d97706); }
        .stat-icon.emerald { background:linear-gradient(135deg, #10b981, #059669); }
        .stat-info { flex:1; min-width:0; }
        .stat-label {
            font-size:11px; color:var(--text-light);
            text-transform:uppercase; letter-spacing:0.5px;
            font-weight:700;
        }
        .stat-value {
            font-size:15px; color:var(--text-dark);
            font-weight:700; margin-top:2px;
            white-space:nowrap; overflow:hidden; text-overflow:ellipsis;
        }

        /* ========== TABS ========== */
        .tabs {
            display:flex; gap:6px;
            background:var(--card);
            padding:8px;
            border-radius:14px;
            border:1px solid var(--border);
            margin-bottom:20px;
            overflow-x:auto;
            scrollbar-width:none;
        }
        .tabs::-webkit-scrollbar { display:none; }
        .tab {
            flex:1;
            padding:11px 18px;
            border-radius:10px;
            border:none;
            background:transparent;
            color:var(--text-mid);
            font-weight:600;
            font-size:13px;
            cursor:pointer;
            transition:0.2s;
            display:inline-flex; align-items:center; justify-content:center; gap:8px;
            white-space:nowrap;
            font-family:inherit;
        }
        .tab:hover:not(.active) { background:var(--border-light); color:var(--text-dark); }
        .tab.active {
            background:linear-gradient(135deg, var(--primary), var(--primary-light));
            color:white;
            box-shadow:0 4px 12px rgba(79,70,229,0.3);
        }
        .tab i { font-size:13px; }

        /* ========== CARD CONTENT ========== */
        .content {
            background:var(--card);
            border-radius:var(--radius);
            border:1px solid var(--border);
            box-shadow:var(--shadow-md);
            overflow:hidden;
            transition:background 0.3s;
        }

        /* ========== PANELS ========== */
        .panel { display:none; padding:32px; animation:fadeIn 0.35s ease-out; }
        .panel.active { display:block; }
        @keyframes fadeIn {
            from { opacity:0; transform:translateY(8px); }
            to { opacity:1; transform:translateY(0); }
        }
        .panel-header {
            margin-bottom:24px;
            padding-bottom:16px;
            border-bottom:2px solid var(--border-light);
        }
        .panel-title {
            font-size:19px; font-weight:800;
            color:var(--text-dark);
            letter-spacing:-0.3px;
            display:flex; align-items:center; gap:10px;
        }
        .panel-title i { color:var(--primary); }
        .panel-desc {
            font-size:13px; color:var(--text-light);
            margin-top:4px;
        }

        /* ========== FORM ========== */
        .grid {
            display:grid;
            grid-template-columns:repeat(auto-fit, minmax(240px, 1fr));
            gap:20px;
        }
        .field { display:flex; flex-direction:column; gap:8px; }
        .field-label {
            font-size:12px; font-weight:700;
            color:var(--text-mid);
            text-transform:uppercase;
            letter-spacing:0.5px;
            display:flex; align-items:center; gap:6px;
        }
        .field-label .req { color:var(--error); }
        .field-wrapper { position:relative; }
        .field-input,
        .field-select,
        .field-textarea {
            width:100%;
            padding:12px 15px 12px 42px;
            border:1.5px solid var(--border);
            border-radius:var(--radius-xs);
            font-size:14px;
            color:var(--text-dark);
            background:var(--card);
            transition:0.2s;
            font-family:inherit;
        }
        .field-input:focus,
        .field-select:focus {
            outline:none;
            border-color:var(--primary);
            box-shadow:0 0 0 4px rgba(79,70,229,0.1);
            background:var(--card);
        }
        .field-icon {
            position:absolute;
            left:14px; top:50%;
            transform:translateY(-50%);
            color:var(--text-light);
            font-size:14px;
            pointer-events:none;
            transition:0.2s;
        }
        .field-input:focus + .field-icon,
        .field-input:not(:placeholder-shown) + .field-icon {
            color:var(--primary);
        }
        .field-hint {
            font-size:11px; color:var(--text-light);
            display:flex; align-items:center; gap:4px;
        }

        /* ========== LOGO UPLOAD PREMIUM ========== */
        .logo-uploader {
            display:flex; gap:28px;
            padding:24px;
            border-radius:var(--radius-sm);
            background:linear-gradient(135deg, var(--primary-bg), transparent);
            border:2px dashed var(--primary-light);
            align-items:center; flex-wrap:wrap;
            transition:0.2s;
        }
        .logo-uploader.dragover {
            background:var(--primary-bg);
            border-color:var(--primary);
            transform:scale(1.01);
        }
        .logo-box {
            position:relative;
            width:140px; height:140px;
            flex-shrink:0;
        }
        .logo-box img {
            width:100%; height:100%;
            border-radius:20px;
            object-fit:cover;
            border:4px solid var(--card);
            background:var(--card);
            box-shadow:var(--shadow-md);
            transition:0.3s;
        }
        .logo-box::after {
            content:"";
            position:absolute; inset:-4px;
            border-radius:24px;
            background:linear-gradient(135deg, var(--primary), var(--accent));
            z-index:-1;
            opacity:0.3;
            filter:blur(8px);
        }
        .logo-info { flex:1; min-width:240px; }
        .logo-info h4 {
            font-size:15px; font-weight:700;
            color:var(--text-dark);
            margin-bottom:4px;
        }
        .logo-info p {
            font-size:12px; color:var(--text-light);
            margin-bottom:14px;
            line-height:1.5;
        }
        .upload-btn {
            display:inline-flex; align-items:center; gap:8px;
            background:var(--primary); color:white;
            padding:10px 18px;
            border-radius:10px;
            cursor:pointer;
            font-weight:600; font-size:13px;
            transition:0.2s; border:none;
            box-shadow:0 4px 14px rgba(79,70,229,0.25);
            font-family:inherit;
        }
        .upload-btn:hover {
            background:var(--primary-dark);
            transform:translateY(-2px);
            box-shadow:0 8px 20px rgba(79,70,229,0.35);
        }
        input[type="file"] { display:none; }
        .file-info {
            margin-top:12px;
            padding:10px 14px;
            border-radius:10px;
            background:var(--card);
            border:1px solid var(--border);
            font-size:12px;
            color:var(--text-mid);
            display:flex; align-items:center; gap:8px;
            transition:0.2s;
        }
        .file-info.success {
            background:var(--success-bg);
            border-color:#a7f3d0;
            color:#065f46;
        }

        /* ========== ACTIONS (CENTRÉES) ========== */
        .actions {
            display:flex;
            flex-direction:column;
            align-items:center;
            justify-content:center;
            padding:24px 32px;
            background:var(--border-light);
            border-top:1px solid var(--border);
            gap:16px;
        }
        .actions-info {
            font-size:12px; color:var(--text-light);
            display:flex; align-items:center; gap:8px;
        }
        .actions-info .req { color:var(--error); }
        .actions-buttons {
            display:flex;
            justify-content:center;
            align-items:center;
            gap:12px;
            flex-wrap:wrap;
        }
        .btn {
            padding:13px 32px;
            border-radius:11px;
            font-weight:700; font-size:14px;
            cursor:pointer;
            display:inline-flex; align-items:center; justify-content:center;
            gap:8px;
            transition:0.2s; border:none; text-decoration:none;
            font-family:inherit;
            min-width:180px;
        }
        .btn-primary {
            background:linear-gradient(135deg, var(--primary) 0%, var(--primary-light) 100%);
            color:white;
            box-shadow:0 6px 16px rgba(79,70,229,0.3);
        }
        .btn-primary:hover {
            transform:translateY(-2px);
            box-shadow:0 10px 24px rgba(79,70,229,0.4);
        }
        .btn-secondary {
            background:var(--card); color:var(--text-mid);
            border:1.5px solid var(--border);
        }
        .btn-secondary:hover { background:var(--border-light); color:var(--text-dark); }

        /* ========== ALERTES ========== */
        .alert {
            padding:14px 20px;
            border-radius:var(--radius-sm);
            margin-bottom:20px;
            display:flex; align-items:center; gap:12px;
            font-weight:500; font-size:14px;
            animation:slideDown 0.4s ease-out;
            border:1px solid;
        }
        @keyframes slideDown {
            from { opacity:0; transform:translateY(-10px); }
            to { opacity:1; transform:translateY(0); }
        }
        .alert-success {
            background:var(--success-bg); color:#065f46;
            border-color:#a7f3d0;
        }
        .alert-error {
            background:var(--error-bg); color:#991b1b;
            border-color:#fecaca;
        }
        .alert i { font-size:16px; }

        /* ========== FOOTER ========== */
        .footer {
            margin-top:32px; text-align:center;
            color:var(--text-light); font-size:12px;
            padding:16px;
        }
        .footer strong { color:var(--primary); font-weight:700; }

        /* ========== RESPONSIVE ========== */
        @media (max-width: 720px) {
            body { padding:12px; }
            .hero { padding:36px 22px 32px; }
            .hero-content { gap:14px; }
            .hero-text h1 { font-size:24px; }
            .hero-logo { width:90px; height:90px; border-radius:22px; }
            .hero-logo-badge { width:26px; height:26px; font-size:11px; }
            .hero-actions { position:static; justify-content:center; margin-bottom:8px; }
            .panel { padding:20px; }
            .actions { padding:20px; }
            .actions-buttons { flex-direction:column; width:100%; }
            .btn { width:100%; }
            .logo-uploader { flex-direction:column; text-align:center; }
        }
        </style>
        """);

        sb.append("</head>");
        sb.append("<body>");
        sb.append("<div class=\"container\">");

        // ======================= HERO CENTRÉ =======================
        sb.append("<div class=\"hero\">");
        sb.append("<div class=\"hero-actions\">");
        sb.append("<button class=\"icon-btn\" onclick=\"toggleTheme()\" title=\"Changer le thème\">");
        sb.append("<i class=\"fas fa-moon\" id=\"themeIcon\"></i></button>");
        sb.append("<a href=\"/admin/parametres\" class=\"icon-btn\" title=\"Retour\">");
        sb.append("<i class=\"fas fa-arrow-left\"></i></a>");
        sb.append("</div>");
        sb.append("<div class=\"hero-content\">");
        sb.append("<div class=\"hero-logo-wrap\">");
        sb.append("<img src=\"").append(logoPath).append("\" alt=\"Logo\" class=\"hero-logo\" ")
          .append("onerror=\"this.src='").append(DEFAULT_LOGO_DATA_URI).append("'\">");
        sb.append("<div class=\"hero-logo-badge ").append(estActif ? "" : "inactive").append("\">");
        sb.append("<i class=\"fas fa-").append(estActif ? "check" : "pause").append("\"></i>");
        sb.append("</div>");
        sb.append("</div>");
        sb.append("<div class=\"hero-text\">");
        sb.append("<div class=\"badge\"><i class=\"fas fa-shield-halved\"></i> Panneau d'administration</div>");
        sb.append("<h1>").append(echapperHtml(nomInstitution)).append("</h1>");
        sb.append("<div class=\"subtitle\">");
        sb.append("<i class=\"fas fa-graduation-cap\"></i>");
        sb.append("<span>").append(echapperHtml(sigleInstitution)).append("</span>");
        if (institution.getCommuneInstitution() != null && !institution.getCommuneInstitution().isBlank()) {
            sb.append("<span class=\"dot\"></span>");
            sb.append("<i class=\"fas fa-location-dot\"></i>");
            sb.append("<span>").append(echapperHtml(institution.getCommuneInstitution())).append("</span>");
        }
        sb.append("</div>");
        sb.append("</div>");
        sb.append("</div>");
        sb.append("</div>");

        // ======================= STATS RAPIDES =======================
        sb.append("<div class=\"stats\">");
        sb.append(statCard("indigo", "fa-hashtag", "Identifiant",
                valeurOuDefaut(institution.getInstitutionId(), "—")));
        sb.append(statCard("cyan", "fa-calendar", "Créée le",
                valeurOuDefaut(institution.getDateCreationInstitution(), "—")));
        sb.append(statCard("emerald", "fa-toggle-on", "Statut", statut));
        sb.append(statCard("amber", "fa-star", "Moyenne passage",
                valeurOuDefaut(institution.getMoyenneDePassageInstitution(), "—")));
        sb.append("</div>");

        // ======================= ALERTES =======================
        if (messageErreur != null && !messageErreur.isEmpty()) {
            sb.append("<div class=\"alert alert-error\">");
            sb.append("<i class=\"fas fa-circle-exclamation\"></i>");
            sb.append("<span>").append(echapperHtml(messageErreur)).append("</span>");
            sb.append("</div>");
        }
        if (messageSucces != null && !messageSucces.isEmpty()) {
            sb.append("<div class=\"alert alert-success\">");
            sb.append("<i class=\"fas fa-circle-check\"></i>");
            sb.append("<span>").append(echapperHtml(messageSucces)).append("</span>");
            sb.append("</div>");
        }

        // ======================= TABS =======================
        sb.append("<div class=\"tabs\">");
        sb.append("<button type=\"button\" class=\"tab active\" onclick=\"showTab(event, 'tab-identite')\">");
        sb.append("<i class=\"fas fa-building\"></i> Identité</button>");
        sb.append("<button type=\"button\" class=\"tab\" onclick=\"showTab(event, 'tab-localisation')\">");
        sb.append("<i class=\"fas fa-location-dot\"></i> Localisation</button>");
        sb.append("<button type=\"button\" class=\"tab\" onclick=\"showTab(event, 'tab-contact')\">");
        sb.append("<i class=\"fas fa-envelope\"></i> Contact</button>");
        sb.append("<button type=\"button\" class=\"tab\" onclick=\"showTab(event, 'tab-responsable')\">");
        sb.append("<i class=\"fas fa-user-tie\"></i> Responsable</button>");
        sb.append("<button type=\"button\" class=\"tab\" onclick=\"showTab(event, 'tab-academique')\">");
        sb.append("<i class=\"fas fa-graduation-cap\"></i> Académique</button>");
        sb.append("<button type=\"button\" class=\"tab\" onclick=\"showTab(event, 'tab-logo')\">");
        sb.append("<i class=\"fas fa-image\"></i> Logo</button>");
        sb.append("</div>");

        // ======================= FORM =======================
        sb.append("<form method=\"POST\" action=\"/admin/parametres/profil\" ")
          .append("enctype=\"multipart/form-data\" id=\"profilForm\">");
        sb.append("<input type=\"hidden\" name=\"action\" value=\"update\">");

        sb.append("<div class=\"content\">");

        // ---------- PANEL 1 : IDENTITÉ ----------
        sb.append("<div id=\"tab-identite\" class=\"panel active\">");
        sb.append(panelHeader("fa-building", "Identification générale",
                "Nom, sigle et informations principales de votre établissement"));
        sb.append("<div class=\"grid\">");
        sb.append(field("Nom de l'institution", "nomInstitution", "text",
                institution.getNomInstitution(), "fa-building", true,
                "Ex: Institution Mixte MINGOT Technologies"));
        sb.append(field("Sigle", "sigleInstitution", "text",
                institution.getSigleInstitution(), "fa-tag", false,
                "Ex: IMMT"));
        sb.append(field("Devise", "deviseInstitution", "text",
                institution.getDeviseInstitution(), "fa-quote-left", false,
                "Ex: Travail - Excellence"));
        sb.append(field("Type d'institution", "typeInstitution", "text",
                institution.getTypeInstitution(), "fa-school", false,
                "Ex: École supérieure"));
        sb.append(field("Niveau", "niveauInstitution", "text",
                institution.getNiveauInstitution(), "fa-layer-group", false,
                "Ex: Universitaire"));
        sb.append(field("Catégorie", "categorieInstitution", "text",
                institution.getCategorieInstitution(), "fa-certificate", false,
                "Ex: Privée"));
        sb.append("</div>");
        sb.append("</div>");

        // ---------- PANEL 2 : LOCALISATION ----------
        sb.append("<div id=\"tab-localisation\" class=\"panel\">");
        sb.append(panelHeader("fa-location-dot", "Localisation",
                "Adresse physique et informations géographiques"));
        sb.append("<div class=\"grid\">");
        sb.append(field("Adresse physique", "adresseInstitution", "text",
                institution.getAdresseInstitution(), "fa-road", false,
                "Ex: 45, Rue des Écoles"));
        sb.append(field("Code postal", "codePostalInstitution", "text",
                institution.getCodePostalInstitution(), "fa-envelope-open", false,
                "Ex: HT1234"));
        sb.append(field("Commune / Ville", "communeInstitution", "text",
                institution.getCommuneInstitution(), "fa-city", false,
                "Ex: Port-au-Prince"));
        sb.append(field("Département / Région", "departementInstitution", "text",
                institution.getDepartementInstitution(), "fa-map", false,
                "Ex: Ouest"));
        sb.append(field("Pays", "paysInstitution", "text",
                institution.getPaysInstitution(), "fa-globe", false,
                "Ex: Haïti"));
        sb.append("</div>");
        sb.append("</div>");

        // ---------- PANEL 3 : CONTACT ----------
        sb.append("<div id=\"tab-contact\" class=\"panel\">");
        sb.append(panelHeader("fa-envelope", "Contacts & Accès",
                "Emails, téléphones et site web de l'établissement"));
        sb.append("<div class=\"grid\">");
        sb.append(field("Email principal", "mailPrimaire", "email",
                institution.getMailPrimaire(), "fa-at", true,
                "Ex: contact@ecole.edu"));
        sb.append(field("Email secondaire", "mailSecondaire", "email",
                institution.getMailSecondaire(), "fa-envelope", false,
                "Ex: info@ecole.edu"));
        sb.append(field("Téléphone principal", "telephonePrimaireInstitution", "tel",
                institution.getTelephonePrimaireInstitution(), "fa-phone", false,
                "Ex: +509 3700-0000"));
        sb.append(field("Téléphone secondaire", "telephoneSecondaireInstitution", "tel",
                institution.getTelephoneSecondaireInstitution(), "fa-mobile-screen", false,
                "Ex: +509 3700-0001"));
        sb.append(field("Site web", "siteWebInstitution", "text",
                institution.getSiteWebInstitution(), "fa-globe", false,
                "Ex: https://www.ecole.edu"));
        sb.append(fieldPassword("Mot de passe SMTP", "smtpPasswordInstitution",
                institution.getSmtpPasswordInstitution(), "fa-lock",
                "Laisser vide pour ne pas changer"));
        sb.append("</div>");
        sb.append("</div>");

        // ---------- PANEL 4 : RESPONSABLE ----------
        sb.append("<div id=\"tab-responsable\" class=\"panel\">");
        sb.append(panelHeader("fa-user-tie", "Responsable de l'institution",
                "Personne à contacter en cas de besoin"));
        sb.append("<div class=\"grid\">");
        sb.append(field("Nom complet", "responsableInstitution", "text",
                institution.getResponsableInstitution(), "fa-user", false,
                "Ex: M. Jean MINGOT"));
        sb.append(field("Poste occupé", "posteResponsableInstitution", "text",
                institution.getPosteResponsableInstitution(), "fa-briefcase", false,
                "Ex: Directeur Général"));
        sb.append(field("Email du responsable", "mailResponsableInstitution", "email",
                institution.getMailResponsableInstitution(), "fa-envelope", false,
                "Ex: directeur@ecole.edu"));
        sb.append(field("Téléphone du responsable", "telephoneResponsableInstitution", "tel",
                institution.getTelephoneResponsableInstitution(), "fa-phone", false,
                "Ex: +509 3700-0002"));
        sb.append("</div>");
        sb.append("</div>");

        // ---------- PANEL 5 : ACADÉMIQUE ----------
        sb.append("<div id=\"tab-academique\" class=\"panel\">");
        sb.append(panelHeader("fa-graduation-cap", "Paramètres académiques",
                "Configuration pédagogique et statut de l'établissement"));
        sb.append("<div class=\"grid\">");
        sb.append(field("Moyenne de passage", "moyenneDePassageInstitution", "text",
                institution.getMoyenneDePassageInstitution(), "fa-star", false,
                "Ex: 10.00"));
        sb.append(field("Système éducatif", "systemeEducatifInstitution", "text",
                institution.getSystemeEducatifInstitution(), "fa-book", false,
                "Ex: Système haïtien"));
        sb.append(field("Statut", "statutInstitution", "text",
                institution.getStatutInstitution(), "fa-toggle-on", false,
                "Ex: ACTIF"));
        sb.append(field("Date de création", "dateCreationInstitution", "text",
                institution.getDateCreationInstitution(), "fa-calendar", false,
                "Ex: 2024-01-15"));
        sb.append("</div>");
        sb.append("</div>");

        // ---------- PANEL 6 : LOGO ----------
        sb.append("<div id=\"tab-logo\" class=\"panel\">");
        sb.append(panelHeader("fa-image", "Logo de l'institution",
                "Personnalisez l'identité visuelle de votre établissement"));
        sb.append("<div class=\"logo-uploader\" id=\"logoDropZone\">");
        sb.append("<div class=\"logo-box\">");
        sb.append("<img id=\"logoPreview\" src=\"").append(echapperHtml(logoPath))
          .append("\" alt=\"Aperçu du logo\" ")
          .append("onerror=\"this.src='").append(DEFAULT_LOGO_DATA_URI).append("'\">");
        sb.append("</div>");
        sb.append("<div class=\"logo-info\">");
        sb.append("<h4>Changer le logo</h4>");
        sb.append("<p>Formats supportés : PNG, JPG, GIF — Taille recommandée : 512×512 px, max 2 Mo</p>");
        sb.append("<label for=\"logoFile\" class=\"upload-btn\">");
        sb.append("<i class=\"fas fa-cloud-arrow-up\"></i> Choisir un fichier</label>");
        sb.append("<input type=\"file\" id=\"logoFile\" name=\"logoFile\" ")
          .append("accept=\"image/png, image/jpeg, image/gif\" onchange=\"updateFileName(this)\">");
        sb.append("<div class=\"file-info\" id=\"fileNameDisplay\">");
        sb.append("<i class=\"fas fa-circle-info\"></i>");
        sb.append("<span>Aucun nouveau fichier sélectionné</span>");
        sb.append("</div>");
        sb.append("</div>");
        sb.append("</div>");
        sb.append("</div>");

        // ---------- ACTIONS (CENTRÉES) ----------
        sb.append("<div class=\"actions\">");
        sb.append("<div class=\"actions-info\">");
        sb.append("<i class=\"fas fa-circle-info\"></i>");
        sb.append("<span>Les champs marqués d'un <span class=\"req\">*</span> sont obligatoires</span>");
        sb.append("</div>");
        sb.append("<div class=\"actions-buttons\">");
        sb.append("<a href=\"/admin/parametres\" class=\"btn btn-secondary\">");
        sb.append("<i class=\"fas fa-xmark\"></i> Annuler</a>");
        sb.append("<button type=\"submit\" class=\"btn btn-primary\">");
        sb.append("<i class=\"fas fa-floppy-disk\"></i> Enregistrer</button>");
        sb.append("</div>");
        sb.append("</div>");

        sb.append("</div>");  // content
        sb.append("</form>");

        // ======================= FOOTER =======================
        sb.append("<div class=\"footer\">");
        sb.append("© ").append(java.time.Year.now().getValue())
          .append(" <strong>M-TECH</strong> — Tous droits réservés");
        sb.append("</div>");

        sb.append("</div>");  // container

        // ======================= SCRIPTS =======================
        sb.append("""
        <script>
            // === Tabs ===
            function showTab(evt, tabId) {
                document.querySelectorAll('.panel').forEach(p => p.classList.remove('active'));
                document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
                document.getElementById(tabId).classList.add('active');
                evt.currentTarget.classList.add('active');
            }

            // === Thème clair/sombre ===
            function toggleTheme() {
                const html = document.documentElement;
                const current = html.getAttribute('data-theme');
                const next = current === 'dark' ? 'light' : 'dark';
                html.setAttribute('data-theme', next);
                document.getElementById('themeIcon').className =
                    next === 'dark' ? 'fas fa-sun' : 'fas fa-moon';
                localStorage.setItem('theme', next);
            }

            // Charger le thème sauvegardé
            (function() {
                const saved = localStorage.getItem('theme');
                if (saved === 'dark') {
                    document.documentElement.setAttribute('data-theme', 'dark');
                    document.getElementById('themeIcon').className = 'fas fa-sun';
                }
            })();

            // === Upload de logo ===
            function updateFileName(input) {
                const display = document.getElementById('fileNameDisplay');
                const preview = document.getElementById('logoPreview');
                if (input.files && input.files[0]) {
                    const file = input.files[0];
                    display.innerHTML = '<i class="fas fa-circle-check"></i><span>' + file.name + ' (' + formatSize(file.size) + ')</span>';
                    display.classList.add('success');
                    const reader = new FileReader();
                    reader.onload = function(e) { preview.src = e.target.result; };
                    reader.readAsDataURL(file);
                } else {
                    display.innerHTML = '<i class="fas fa-circle-info"></i><span>Aucun nouveau fichier sélectionné</span>';
                    display.classList.remove('success');
                }
            }

            function formatSize(bytes) {
                if (bytes < 1024) return bytes + ' B';
                if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
                return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
            }

            // === Drag and drop sur la zone de logo ===
            const dropZone = document.getElementById('logoDropZone');
            ['dragenter', 'dragover'].forEach(evt => {
                dropZone.addEventListener(evt, e => {
                    e.preventDefault();
                    dropZone.classList.add('dragover');
                });
            });
            ['dragleave', 'drop'].forEach(evt => {
                dropZone.addEventListener(evt, e => {
                    e.preventDefault();
                    dropZone.classList.remove('dragover');
                });
            });
            dropZone.addEventListener('drop', e => {
                const files = e.dataTransfer.files;
                if (files.length > 0) {
                    const fileInput = document.getElementById('logoFile');
                    fileInput.files = files;
                    updateFileName(fileInput);
                }
            });
        </script>
        """);

        sb.append("</body>");
        sb.append("</html>");

        return sb.toString();
    }

    // =========================================================
    // HELPERS
    // =========================================================
    private static final String DEFAULT_LOGO_DATA_URI =
            "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='100' height='100'%3E"
            + "%3Crect width='100' height='100' fill='%234f46e5'/%3E"
            + "%3Ctext x='50' y='62' font-size='42' text-anchor='middle' fill='white'%3E🏛%3C/text%3E"
            + "%3C/svg%3E";

    private static String preparerLogoPath(String logoPath) {
        if (logoPath == null || logoPath.trim().isEmpty()) {
            return DEFAULT_LOGO_DATA_URI;
        }
        String p = logoPath.trim().replace("\\", "/");
        while (p.contains("//")) p = p.replace("//", "/");
        if (!p.startsWith("/") && !p.startsWith("http://")
                && !p.startsWith("https://") && !p.startsWith("data:")) {
            p = "/" + p;
        }
        return p;
    }

    private static String valeurOuDefaut(String valeur, String defaut) {
        return (valeur == null || valeur.isBlank()) ? defaut : valeur;
    }

    private static String panelHeader(String icon, String title, String desc) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"panel-header\">");
        sb.append("<div class=\"panel-title\">");
        sb.append("<i class=\"fas ").append(icon).append("\"></i>");
        sb.append(echapperHtml(title));
        sb.append("</div>");
        sb.append("<div class=\"panel-desc\">").append(echapperHtml(desc)).append("</div>");
        sb.append("</div>");
        return sb.toString();
    }

    private static String statCard(String color, String icon, String label, String value) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"stat\">");
        sb.append("<div class=\"stat-icon ").append(color).append("\">");
        sb.append("<i class=\"fas ").append(icon).append("\"></i>");
        sb.append("</div>");
        sb.append("<div class=\"stat-info\">");
        sb.append("<div class=\"stat-label\">").append(echapperHtml(label)).append("</div>");
        sb.append("<div class=\"stat-value\">").append(echapperHtml(value)).append("</div>");
        sb.append("</div>");
        sb.append("</div>");
        return sb.toString();
    }

    private static String field(String label, String name, String type,
                                  String value, String icon, boolean required,
                                  String placeholder) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"field\">");
        sb.append("<label class=\"field-label\" for=\"").append(name).append("\">");
        sb.append(echapperHtml(label));
        if (required) sb.append(" <span class=\"req\">*</span>");
        sb.append("</label>");
        sb.append("<div class=\"field-wrapper\">");
        sb.append("<input type=\"").append(type).append("\" ")
          .append("id=\"").append(name).append("\" ")
          .append("name=\"").append(name).append("\" ")
          .append("class=\"field-input\" ")
          .append("value=\"").append(echapperHtml(value)).append("\" ");
        if (placeholder != null) {
            sb.append("placeholder=\"").append(echapperHtml(placeholder)).append("\" ");
        }
        if (required) sb.append("required ");
        sb.append(">");
        sb.append("<i class=\"fas ").append(icon).append(" field-icon\"></i>");
        sb.append("</div>");
        sb.append("</div>");
        return sb.toString();
    }

    private static String fieldPassword(String label, String name, String value,
                                          String icon, String hint) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"field\">");
        sb.append("<label class=\"field-label\" for=\"").append(name).append("\">");
        sb.append(echapperHtml(label)).append("</label>");
        sb.append("<div class=\"field-wrapper\">");
        sb.append("<input type=\"password\" ")
          .append("id=\"").append(name).append("\" ")
          .append("name=\"").append(name).append("\" ")
          .append("class=\"field-input\" ")
          .append("value=\"").append(echapperHtml(value)).append("\" ")
          .append("placeholder=\"").append(echapperHtml(hint)).append("\">");
        sb.append("<i class=\"fas ").append(icon).append(" field-icon\"></i>");
        sb.append("</div>");
        sb.append("<div class=\"field-hint\">");
        sb.append("<i class=\"fas fa-info-circle\"></i> ");
        sb.append(echapperHtml(hint));
        sb.append("</div>");
        sb.append("</div>");
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