import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;

public class UIAttestation {

    private static final Gson GSON = new Gson();
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    public static String renderPage(
            String institutionId,
            List<Map<String, String>> promotions,
            List<Map<String, String>> annees,
            List<Map<String, String>> periodes,
            List<Map<String, String>> classes,
            List<Map<String, Object>> etudiants,
            String selectedPromo,
            String selectedAnnee,
            String selectedPeriode,
            String selectedClasse,
            String selectedEtudiant,
            String selectedType,
            String reference,
            String numero,
            String dateStr,
            String option,
            String previewImageBase64) {

        if (institutionId == null) institutionId = "INSTITUTION";

        // Sérialisation sécurisée
        String institutionIdJson = GSON.toJson(institutionId);
        String institutionIdEscape = echapperJson(institutionId);

        // Construction des options
        String promoOpts = buildOptions(promotions, "promotion", selectedPromo);
        String anneeOpts = buildOptions(annees, "annee", selectedAnnee);
        String periodeOpts = buildOptions(periodes, "periode", selectedPeriode);
        String classeOpts = buildOptions(classes, "classe", selectedClasse);
        String etudiantOpts = buildEtudiantOptions(etudiants, selectedEtudiant);
        String typeOpts = buildTypeOptions(selectedType);

        String dateDefault = dateStr != null ? dateStr : DATE_FORMAT.format(new Date());
        String previewSrc = previewImageBase64 != null && !previewImageBase64.isEmpty() 
                ? "data:image/png;base64," + previewImageBase64 
                : "";

        StringBuilder sb = new StringBuilder();

        sb.append("<!DOCTYPE html>\n")
          .append("<html lang=\"fr\" class=\"h-100\">\n")
          .append("<head>\n")
          .append("    <meta charset=\"UTF-8\">\n")
          .append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n")
          .append("    <title>Gestion des Attestations</title>\n")
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
          .append("        /* HEADER */\n")
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
          .append("        /* LAYOUT */\n")
          .append("        .content-wrapper { flex: 1; display: flex; gap: 20px; min-height: 0; }\n")
          .append("        .panel {\n")
          .append("            background: var(--card);\n")
          .append("            border-radius: var(--radius);\n")
          .append("            box-shadow: var(--shadow-lg);\n")
          .append("            padding: 24px;\n")
          .append("            overflow-y: auto;\n")
          .append("        }\n")
          .append("        .panel::-webkit-scrollbar { width: 6px; height: 6px; }\n")
          .append("        .panel::-webkit-scrollbar-track { background: transparent; }\n")
          .append("        .panel::-webkit-scrollbar-thumb { background: #cbd5e1; border-radius: 10px; }\n")
          .append("        .panel::-webkit-scrollbar-thumb:hover { background: #94a3b8; }\n")
          .append("        \n")
          .append("        .panel-left { flex: 0 0 420px; }\n")
          .append("        .panel-right { flex: 1; display: flex; flex-direction: column; }\n")
          .append("        \n")
          .append("        .panel-title { font-size: 1rem; font-weight: 700; color: var(--text); margin-bottom: 4px; display: flex; align-items: center; gap: 10px; }\n")
          .append("        .panel-title i { color: var(--primary); }\n")
          .append("        .panel-sub { font-size: 0.8rem; color: var(--text-muted); margin-bottom: 18px; border-bottom: 1px solid var(--border); padding-bottom: 12px; }\n")
          .append("        \n")
          .append("        /* FORMULAIRES */\n")
          .append("        .field-group { margin-bottom: 14px; }\n")
          .append("        .field-group label { display: block; font-size: 0.7rem; font-weight: 600; color: #475569; margin-bottom: 4px; text-transform: uppercase; letter-spacing: 0.3px; }\n")
          .append("        .field-group label .required { color: var(--danger); }\n")
          .append("        .field-group select, .field-group input {\n")
          .append("            width: 100%;\n")
          .append("            padding: 8px 14px;\n")
          .append("            border: 1px solid var(--border);\n")
          .append("            border-radius: var(--radius-sm);\n")
          .append("            font-size: 0.9rem;\n")
          .append("            background: white;\n")
          .append("            transition: all 0.2s;\n")
          .append("            color: var(--text);\n")
          .append("        }\n")
          .append("        .field-group select:focus, .field-group input:focus {\n")
          .append("            border-color: var(--primary);\n")
          .append("            box-shadow: 0 0 0 3px rgba(79, 70, 229, 0.12);\n")
          .append("            outline: none;\n")
          .append("        }\n")
          .append("        .field-group select[readonly], .field-group input[readonly] {\n")
          .append("            background: #f1f5f9;\n")
          .append("            color: #64748b;\n")
          .append("            cursor: not-allowed;\n")
          .append("        }\n")
          .append("        .field-row { display: flex; gap: 14px; }\n")
          .append("        .field-row .field-group { flex: 1; }\n")
          .append("        \n")
          .append("        /* APERÇU */\n")
          .append("        .preview-container {\n")
          .append("            flex: 1;\n")
          .append("            background: #f8fafc;\n")
          .append("            border-radius: var(--radius-sm);\n")
          .append("            border: 2px dashed var(--border);\n")
          .append("            display: flex;\n")
          .append("            align-items: center;\n")
          .append("            justify-content: center;\n")
          .append("            min-height: 500px;\n")
          .append("            position: relative;\n")
          .append("            overflow: auto;\n")
          .append("            padding: 20px;\n")
          .append("        }\n")
          .append("        .preview-container img {\n")
          .append("            max-width: 100%;\n")
          .append("            max-height: 100%;\n")
          .append("            object-fit: contain;\n")
          .append("            border-radius: 8px;\n")
          .append("            box-shadow: 0 4px 20px rgba(0,0,0,0.06);\n")
          .append("        }\n")
          .append("        .preview-placeholder {\n")
          .append("            color: var(--text-muted);\n")
          .append("            text-align: center;\n")
          .append("            font-size: 0.95rem;\n")
          .append("        }\n")
          .append("        .preview-placeholder i { font-size: 48px; color: #d1d5db; display: block; margin-bottom: 12px; }\n")
          .append("        .preview-placeholder small { color: #94a3b8; font-size: 0.8rem; }\n")
          .append("        \n")
          .append("        /* ACTIONS */\n")
          .append("        .action-bar {\n")
          .append("            display: flex;\n")
          .append("            justify-content: flex-end;\n")
          .append("            gap: 12px;\n")
          .append("            padding-top: 16px;\n")
          .append("            margin-top: 16px;\n")
          .append("            border-top: 1px solid var(--border);\n")
          .append("            flex-shrink: 0;\n")
          .append("        }\n")
          .append("        \n")
          .append("        /* BOUTONS */\n")
          .append("        .btn {\n")
          .append("            padding: 9px 22px;\n")
          .append("            border: none;\n")
          .append("            border-radius: var(--radius-sm);\n")
          .append("            font-weight: 600;\n")
          .append("            font-size: 0.85rem;\n")
          .append("            cursor: pointer;\n")
          .append("            transition: all 0.2s;\n")
          .append("            display: inline-flex;\n")
          .append("            align-items: center;\n")
          .append("            gap: 8px;\n")
          .append("            text-decoration: none;\n")
          .append("        }\n")
          .append("        .btn-primary { background: var(--primary); color: white; }\n")
          .append("        .btn-primary:hover { background: var(--primary-hover); transform: translateY(-2px); box-shadow: 0 4px 12px rgba(79, 70, 229, 0.35); }\n")
          .append("        .btn-success { background: var(--success); color: white; }\n")
          .append("        .btn-success:hover { background: #15803d; transform: translateY(-2px); box-shadow: 0 4px 12px rgba(22, 163, 74, 0.35); }\n")
          .append("        .btn-danger { background: var(--danger); color: white; }\n")
          .append("        .btn-danger:hover { background: #b91c1c; transform: translateY(-2px); box-shadow: 0 4px 12px rgba(220, 38, 38, 0.35); }\n")
          .append("        .btn-secondary { background: #e8edf4; color: var(--text); }\n")
          .append("        .btn-secondary:hover { background: #d1d9e6; transform: translateY(-2px); }\n")
          .append("        .btn-print { background: #475569; color: white; }\n")
          .append("        .btn-print:hover { background: #334155; transform: translateY(-2px); box-shadow: 0 4px 12px rgba(71, 85, 105, 0.35); }\n")
          .append("        .btn-refresh { background: var(--primary); color: white; }\n")
          .append("        .btn-refresh:hover { background: var(--primary-hover); transform: translateY(-2px); box-shadow: 0 4px 12px rgba(79, 70, 229, 0.35); }\n")
          .append("        \n")
          .append("        /* TOAST */\n")
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
          .append("        /* RESPONSIVE */\n")
          .append("        @media (max-width: 1100px) {\n")
          .append("            .content-wrapper { flex-direction: column; }\n")
          .append("            .panel-left { flex: 1; }\n")
          .append("            .panel-right { flex: 1; }\n")
          .append("            .preview-container { min-height: 300px; }\n")
          .append("        }\n")
          .append("        @media (max-width: 768px) {\n")
          .append("            .main-wrapper { padding: 12px; gap: 12px; }\n")
          .append("            .header-glass { flex-direction: column; align-items: flex-start; gap: 10px; padding: 16px 20px; }\n")
          .append("            .header-glass .btn-back { align-self: flex-start; }\n")
          .append("            .field-row { flex-direction: column; gap: 0; }\n")
          .append("            .action-bar { flex-direction: column; align-items: stretch; }\n")
          .append("            .action-bar .btn { justify-content: center; }\n")
          .append("            .toast-custom { min-width: unset; width: 100%; }\n")
          .append("            .toast-container { left: 16px; right: 16px; bottom: 16px; }\n")
          .append("        }\n")
          .append("        @media (max-width: 480px) {\n")
          .append("            .header-glass h4 { font-size: 1rem; }\n")
          .append("            .panel { padding: 16px; }\n")
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
          .append("            <h4><i class=\"fa-solid fa-file-alt\"></i>Gestion des Attestations</h4>\n")
          .append("            <p class=\"subtitle\">").append(echapperHtml(institutionId)).append("</p>\n")
          .append("        </div>\n")
          .append("        <a href=\"/admin/parametres\" class=\"btn-back\">\n")
          .append("            <i class=\"fa-solid fa-arrow-left me-1\"></i> Paramètres\n")
          .append("        </a>\n")
          .append("    </header>\n");

        // ============================================================
        // CONTENU
        // ============================================================
        sb.append("    <div class=\"content-wrapper\">\n");

        // --- PANEL GAUCHE ---
        sb.append("        <div class=\"panel panel-left\">\n");
        sb.append("            <div class=\"panel-title\"><i class=\"fa-solid fa-sliders-h\"></i> Configuration</div>\n");
        sb.append("            <div class=\"panel-sub\">Paramètres officiels de génération</div>\n");

        // Promotion
        sb.append("            <div class=\"field-group\">\n")
          .append("                <label>Promotion <span class=\"required\">*</span></label>\n")
          .append("                <select id=\"promotion\" onchange=\"chargerAnnees()\">").append(promoOpts).append("</select>\n")
          .append("            </div>\n");

        // Année académique
        sb.append("            <div class=\"field-group\">\n")
          .append("                <label>Année académique <span class=\"required\">*</span></label>\n")
          .append("                <select id=\"annee\" onchange=\"chargerPeriodes()\">").append(anneeOpts).append("</select>\n")
          .append("            </div>\n");

        // Période
        sb.append("            <div class=\"field-group\">\n")
          .append("                <label>Période <span class=\"required\">*</span></label>\n")
          .append("                <select id=\"periode\" onchange=\"chargerClasses()\">").append(periodeOpts).append("</select>\n")
          .append("            </div>\n");

        // Classe
        sb.append("            <div class=\"field-group\">\n")
          .append("                <label>Classe <span class=\"required\">*</span></label>\n")
          .append("                <select id=\"classe\" onchange=\"chargerEtudiants()\">").append(classeOpts).append("</select>\n")
          .append("            </div>\n");

        // Option
        sb.append("            <div class=\"field-group\">\n")
          .append("                <label>Option de l'étudiant</label>\n")
          .append("                <input type=\"text\" id=\"option\" value=\"").append(escapeHtml(option != null ? option : "")).append("\" placeholder=\"Ex: Sciences économiques\">\n")
          .append("            </div>\n");

        // Étudiant
        sb.append("            <div class=\"field-group\">\n")
          .append("                <label>Étudiant <span class=\"required\">*</span></label>\n")
          .append("                <select id=\"etudiant\" onchange=\"rafraichirPreview()\">").append(etudiantOpts).append("</select>\n")
          .append("            </div>\n");

        sb.append("            <hr style=\"border: none; border-top: 1px solid var(--border); margin: 16px 0;\">\n");

        // Type d'attestation
        sb.append("            <div class=\"field-group\">\n")
          .append("                <label>Nature de l'attestation <span class=\"required\">*</span></label>\n")
          .append("                <select id=\"type\" onchange=\"rafraichirPreview()\">").append(typeOpts).append("</select>\n")
          .append("            </div>\n");

        // Référence et Numéro
        sb.append("            <div class=\"field-row\">\n")
          .append("                <div class=\"field-group\">\n")
          .append("                    <label>Référence</label>\n")
          .append("                    <input type=\"text\" id=\"reference\" value=\"").append(escapeHtml(reference != null ? reference : "")).append("\" placeholder=\"ATT-xxx\">\n")
          .append("                </div>\n")
          .append("                <div class=\"field-group\">\n")
          .append("                    <label>Numéro</label>\n")
          .append("                    <input type=\"text\" id=\"numero\" value=\"").append(escapeHtml(numero != null ? numero : "")).append("\" placeholder=\"Auto-généré\">\n")
          .append("                </div>\n")
          .append("            </div>\n");

        // Date
        sb.append("            <div class=\"field-group\">\n")
          .append("                <label>Date d'émission officielle</label>\n")
          .append("                <input type=\"date\" id=\"date\" value=\"").append(dateDefault).append("\">\n")
          .append("            </div>\n");

        sb.append("        </div>\n");

        // --- PANEL DROIT ---
        sb.append("        <div class=\"panel panel-right\">\n");
        sb.append("            <div class=\"panel-title\"><i class=\"fa-solid fa-eye\"></i> Aperçu de l'attestation</div>\n");
        sb.append("            <div class=\"panel-sub\">Visualisation en temps réel du document</div>\n");

        sb.append("            <div class=\"preview-container\" id=\"previewContainer\">\n");
        sb.append("                <img id=\"previewImage\" src=\"").append(previewSrc).append("\" alt=\"Aperçu\" style=\"display: ").append(previewImageBase64 != null && !previewImageBase64.isEmpty() ? "block" : "none").append(";\">\n");
        sb.append("                <div id=\"previewPlaceholder\" class=\"preview-placeholder\" style=\"display: ").append(previewImageBase64 == null || previewImageBase64.isEmpty() ? "block" : "none").append(";\">\n");
        sb.append("                    <i class=\"fa-solid fa-file-alt\"></i>\n");
        sb.append("                    Aucun aperçu disponible<br>\n");
        sb.append("                    <small>Sélectionnez un étudiant et les paramètres</small>\n");
        sb.append("                </div>\n");
        sb.append("            </div>\n");

        // Actions
        sb.append("            <div class=\"action-bar\">\n");
        sb.append("                <button class=\"btn btn-secondary\" onclick=\"fermer()\"><i class=\"fa-solid fa-arrow-left\"></i> Annuler</button>\n");
        sb.append("                <button class=\"btn btn-refresh\" onclick=\"rafraichirPreview()\"><i class=\"fa-solid fa-sync\"></i> Rafraîchir</button>\n");
        sb.append("                <button class=\"btn btn-print\" onclick=\"imprimer()\"><i class=\"fa-solid fa-print\"></i> Imprimer</button>\n");
        sb.append("                <button class=\"btn btn-success\" onclick=\"sauvegarder()\"><i class=\"fa-solid fa-save\"></i> Enregistrer</button>\n");
        sb.append("            </div>\n");

        sb.append("        </div>\n");
        sb.append("    </div>\n");

        sb.append("</div>\n");

        // ============================================================
        // TOAST CONTAINER
        // ============================================================
        sb.append("    <div class=\"toast-container\" id=\"toastContainer\"></div>\n");

        // ============================================================
        // JAVASCRIPT
        // ============================================================
        sb.append("<script src=\"https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js\"></script>\n");
        sb.append("<script>\n");
        sb.append("const institutionId = ").append(institutionIdJson).append(";\n");
        sb.append("const institutionIdEscaped = '").append(institutionIdEscape).append("';\n");
        sb.append("let currentPromo = '").append(selectedPromo != null ? escapeJs(selectedPromo) : "").append("';\n");
        sb.append("let currentAnnee = '").append(selectedAnnee != null ? escapeJs(selectedAnnee) : "").append("';\n");
        sb.append("let currentPeriode = '").append(selectedPeriode != null ? escapeJs(selectedPeriode) : "").append("';\n");
        sb.append("let currentClasse = '").append(selectedClasse != null ? escapeJs(selectedClasse) : "").append("';\n");
        sb.append("let currentEtudiant = '").append(selectedEtudiant != null ? escapeJs(selectedEtudiant) : "").append("';\n");

        sb.append("""
        function escapeHtml(str) { if (!str) return ''; return String(str).replace(/[&<>]/g, function(m) { return m === '&' ? '&amp;' : m === '<' ? '&lt;' : '&gt;'; }); }

        function showToast(msg, type) {
            const container = document.getElementById('toastContainer');
            const div = document.createElement('div');
            div.className = 'toast-custom ' + type;
            div.textContent = msg;
            container.appendChild(div);
            setTimeout(function() {
                div.classList.add('removing');
                setTimeout(function() { div.remove(); }, 300);
            }, 3000);
        }

        function chargerAnnees() {
            const promo = document.getElementById('promotion').value;
            if (!promo) { resetSelect('annee'); resetSelect('periode'); resetSelect('classe'); resetSelect('etudiant'); return; }
            fetch('/admin/attestations/api/listes/annees?promo=' + encodeURIComponent(promo))
                .then(function(r) { return r.json(); })
                .then(function(data) {
                    if (data.success) {
                        remplirSelect('annee', data.data, 'annee');
                        if (data.data.length > 0) { chargerPeriodes(); } else { resetSelect('periode'); resetSelect('classe'); resetSelect('etudiant'); }
                    } else { showToast('Erreur chargement ann\u00e9es: ' + data.error, 'error'); }
                }).catch(function() { showToast('Erreur r\u00e9seau', 'error'); });
        }

        function chargerPeriodes() {
            const promo = document.getElementById('promotion').value;
            const annee = document.getElementById('annee').value;
            if (!promo || !annee) { resetSelect('periode'); resetSelect('classe'); resetSelect('etudiant'); return; }
            fetch('/admin/attestations/api/listes/periodes?promo=' + encodeURIComponent(promo) + '&annee=' + encodeURIComponent(annee))
                .then(function(r) { return r.json(); })
                .then(function(data) {
                    if (data.success) {
                        remplirSelect('periode', data.data, 'periode');
                        if (data.data.length > 0) { chargerClasses(); } else { resetSelect('classe'); resetSelect('etudiant'); }
                    } else { showToast('Erreur chargement p\u00e9riodes: ' + data.error, 'error'); }
                }).catch(function() { showToast('Erreur r\u00e9seau', 'error'); });
        }

        function chargerClasses() {
            const promo = document.getElementById('promotion').value;
            const annee = document.getElementById('annee').value;
            const periode = document.getElementById('periode').value;
            if (!promo || !annee || !periode) { resetSelect('classe'); resetSelect('etudiant'); return; }
            fetch('/admin/attestations/api/listes/classes?promo=' + encodeURIComponent(promo) + '&annee=' + encodeURIComponent(annee) + '&periode=' + encodeURIComponent(periode))
                .then(function(r) { return r.json(); })
                .then(function(data) {
                    if (data.success) {
                        remplirSelect('classe', data.data, 'classe');
                        if (data.data.length > 0) { chargerEtudiants(); } else { resetSelect('etudiant'); }
                    } else { showToast('Erreur chargement classes: ' + data.error, 'error'); }
                }).catch(function() { showToast('Erreur r\u00e9seau', 'error'); });
        }

        function chargerEtudiants() {
            const promo = document.getElementById('promotion').value;
            const annee = document.getElementById('annee').value;
            const periode = document.getElementById('periode').value;
            const classe = document.getElementById('classe').value;
            if (!promo || !annee || !periode || !classe) { resetSelect('etudiant'); return; }
            fetch('/admin/attestations/api/listes/etudiants?promo=' + encodeURIComponent(promo) + '&annee=' + encodeURIComponent(annee) + '&periode=' + encodeURIComponent(periode) + '&classe=' + encodeURIComponent(classe))
                .then(function(r) { return r.json(); })
                .then(function(data) {
                    if (data.success) {
                        remplirSelect('etudiant', data.data, 'etudiant');
                        if (data.data.length > 0) { document.getElementById('etudiant').selectedIndex = 1; rafraichirPreview(); }
                    } else { showToast('Erreur chargement \u00e9tudiants: ' + data.error, 'error'); }
                }).catch(function() { showToast('Erreur r\u00e9seau', 'error'); });
        }

        function remplirSelect(id, data, type) {
            const select = document.getElementById(id);
            select.innerHTML = '<option value="">-- S\u00e9lectionner --</option>';
            if (type === 'etudiant') {
                data.forEach(function(item) {
                    const opt = document.createElement('option');
                    opt.value = item.id;
                    opt.textContent = item.id + ' - ' + item.nom + ' ' + item.prenom;
                    if (item.id === currentEtudiant) opt.selected = true;
                    select.appendChild(opt);
                });
            } else {
                data.forEach(function(item) {
                    const val = item[type];
                    const opt = document.createElement('option');
                    opt.value = val;
                    opt.textContent = val;
                    if (val === currentSelection(type)) opt.selected = true;
                    select.appendChild(opt);
                });
            }
        }

        function currentSelection(type) {
            if (type === 'annee') return currentAnnee;
            if (type === 'periode') return currentPeriode;
            if (type === 'classe') return currentClasse;
            return '';
        }

        function resetSelect(id) {
            const select = document.getElementById(id);
            select.innerHTML = '<option value="">-- Aucune donn\u00e9e --</option>';
            if (id === 'etudiant') {
                document.getElementById('previewPlaceholder').style.display = 'block';
                document.getElementById('previewImage').style.display = 'none';
            }
        }

        function rafraichirPreview() {
            const etudiant = document.getElementById('etudiant').value;
            if (!etudiant) return;
            const data = {
                etudiantId: etudiant,
                type: document.getElementById('type').value,
                reference: document.getElementById('reference').value,
                numero: document.getElementById('numero').value,
                date: document.getElementById('date').value,
                option: document.getElementById('option').value
            };
            document.getElementById('previewPlaceholder').style.display = 'block';
            document.getElementById('previewPlaceholder').innerHTML = '<i class="fa-solid fa-spinner fa-spin" style="font-size:32px;display:block;margin-bottom:10px;color:var(--primary);"></i>G\u00e9n\u00e9ration en cours...';
            document.getElementById('previewImage').style.display = 'none';

            fetch('/admin/attestations/api/preview', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(data)
            })
            .then(function(r) { return r.json(); })
            .then(function(result) {
                if (result.success) {
                    document.getElementById('previewImage').src = 'data:image/png;base64,' + result.image;
                    document.getElementById('previewImage').style.display = 'block';
                    document.getElementById('previewPlaceholder').style.display = 'none';
                } else {
                    document.getElementById('previewPlaceholder').innerHTML = '<span style="color:var(--danger);">Erreur: ' + escapeHtml(result.error) + '</span>';
                }
            })
            .catch(function() {
                document.getElementById('previewPlaceholder').innerHTML = '<span style="color:var(--danger);">Erreur r\u00e9seau</span>';
            });
        }

        function sauvegarder() {
            const etudiant = document.getElementById('etudiant').value;
            if (!etudiant) { showToast('S\u00e9lectionnez un \u00e9tudiant.', 'error'); return; }
            const data = {
                etudiantId: etudiant,
                type: document.getElementById('type').value,
                reference: document.getElementById('reference').value,
                numero: document.getElementById('numero').value,
                date: document.getElementById('date').value,
                option: document.getElementById('option').value,
                periode: document.getElementById('periode').value,
                anneeAcademique: document.getElementById('annee').value,
                classe: document.getElementById('classe').value,
                institutionId: institutionId
            };
            fetch('/admin/attestations/api/save', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(data)
            })
            .then(function(r) { return r.json(); })
            .then(function(result) {
                if (result.success) {
                    showToast('Attestation enregistr\u00e9e avec succ\u00e8s !', 'success');
                    setTimeout(function() { window.location.href = '/admin/attestations'; }, 1500);
                } else {
                    showToast('Erreur: ' + result.message, 'error');
                }
            })
            .catch(function() { showToast('Erreur r\u00e9seau', 'error'); });
        }

        function imprimer() {
            const etudiant = document.getElementById('etudiant').value;
            if (!etudiant) { showToast('S\u00e9lectionnez un \u00e9tudiant.', 'error'); return; }
            const data = {
                etudiantId: etudiant,
                type: document.getElementById('type').value,
                reference: document.getElementById('reference').value,
                numero: document.getElementById('numero').value,
                date: document.getElementById('date').value,
                option: document.getElementById('option').value
            };
            const form = document.createElement('form');
            form.method = 'POST';
            form.action = '/admin/attestations/api/print';
            form.target = '_blank';
            const input = document.createElement('input');
            input.type = 'hidden';
            input.name = 'data';
            input.value = JSON.stringify(data);
            form.appendChild(input);
            document.body.appendChild(form);
            form.submit();
            document.body.removeChild(form);
        }

        function fermer() {
            if (confirm('Quitter sans enregistrer ?')) { window.location.href = '/admin/attestations'; }
        }

        // Chargement initial
        window.onload = function() {
            if (currentPromo) {
                document.getElementById('promotion').value = currentPromo;
                chargerAnnees();
                setTimeout(function() {
                    if (currentAnnee) {
                        document.getElementById('annee').value = currentAnnee;
                        chargerPeriodes();
                        setTimeout(function() {
                            if (currentPeriode) {
                                document.getElementById('periode').value = currentPeriode;
                                chargerClasses();
                                setTimeout(function() {
                                    if (currentClasse) {
                                        document.getElementById('classe').value = currentClasse;
                                        chargerEtudiants();
                                        setTimeout(function() {
                                            if (currentEtudiant) {
                                                document.getElementById('etudiant').value = currentEtudiant;
                                                rafraichirPreview();
                                            }
                                        }, 300);
                                    }
                                }, 300);
                            }
                        }, 300);
                    }
                }, 300);
            }
        };
        """);

        sb.append("</script>\n");
        sb.append("</body>\n");
        sb.append("</html>");

        return sb.toString();
    }

    // ============================================================
    // MÉTHODES UTILITAIRES
    // ============================================================

    private static String buildOptions(List<Map<String, String>> items, String key, String selected) {
        StringBuilder sb = new StringBuilder();
        sb.append("<option value=\"\">-- S\u00e9lectionner --</option>");
        if (items == null) return sb.toString();
        for (Map<String, String> item : items) {
            String value = item.get(key);
            if (value == null) continue;
            String sel = value.equals(selected) ? "selected" : "";
            sb.append("<option value=\"").append(escapeHtml(value)).append("\" ").append(sel).append(">")
              .append(escapeHtml(value)).append("</option>");
        }
        return sb.toString();
    }

    private static String buildEtudiantOptions(List<Map<String, Object>> etudiants, String selected) {
        StringBuilder sb = new StringBuilder();
        sb.append("<option value=\"\">-- S\u00e9lectionner --</option>");
        if (etudiants == null) return sb.toString();
        for (Map<String, Object> e : etudiants) {
            String id = (String) e.get("id");
            String nom = (String) e.get("nom");
            String prenom = (String) e.get("prenom");
            String label = id + " - " + nom + " " + prenom;
            String sel = id != null && id.equals(selected) ? "selected" : "";
            sb.append("<option value=\"").append(escapeHtml(id)).append("\" ").append(sel).append(">")
              .append(escapeHtml(label)).append("</option>");
        }
        return sb.toString();
    }

    private static String buildTypeOptions(String selected) {
        StringBuilder sb = new StringBuilder();
        sb.append("<option value=\"\">-- S\u00e9lectionner --</option>");
        String[] types = {
            "Attestation de scolarité",
            "Attestation de réussite",
            "Attestation de stage",
            "Attestation de travail",
            "Attestation de bourse",
            "Attestation de résidence",
            "Attestation de bonne conduite",
            "Attestation de salaire",
            "Attestation de présence",
            "Attestation de formation"
        };
        for (String type : types) {
            String sel = type.equals(selected) ? "selected" : "";
            sb.append("<option value=\"").append(escapeHtml(type)).append("\" ").append(sel).append(">")
              .append(escapeHtml(type)).append("</option>");
        }
        return sb.toString();
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String escapeJs(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    

    private static String echapperJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
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
}