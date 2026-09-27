import java.time.LocalDate;
import java.time.Year;
import java.util.List;

public class UIInscriptionEtudiant {

    private static final String[] SEXES = Constantes.getSexes();
    private static final String[] GROUPES_SANGUINS = Constantes.getGroupesSanguins();
    private static final String[] DEPARTEMENTS = Constantes.getDepartements();

    public static String render(String message,
                                List<Institution> institutions,
                                List<String> annees,
                                List<String> periodes,
                                List<Classe> classes,
                                String institutionSel,
                                String anneeSel,
                                String periodeSel,
                                List<Options> options) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>");
        sb.append("<html lang=\"fr\">");
        sb.append("<head>");
        sb.append("<meta charset=\"UTF-8\">");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        sb.append("<title>Inscription Étudiant</title>");
        sb.append("<link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css\">");
        sb.append("<link href=\"https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@300;400;500;600;700;800&display=swap\" rel=\"stylesheet\">");
        sb.append("<style>");
        sb.append(renderStyles());
        sb.append("</style>");
        sb.append("</head>");
        sb.append("<body>");
        sb.append("<div class=\"container\">");

        // ===== HEADER =====
        sb.append("<div class=\"header\">");
        sb.append("<div class=\"header-content\">");
        
        // Logo dynamique de l'institution
        String logoPath = "";
        String nomInstitution = "";
        String sigleInstitution = "";
        if (institutions != null && !institutions.isEmpty()) {
            for (Institution inst : institutions) {
                if (inst.getInstitutionId().equals(institutionSel)) {
                    nomInstitution = inst.getNomInstitution();
                    sigleInstitution = inst.getSigleInstitution();
                    logoPath = inst.getLogoInstitution();
                    break;
                }
            }
            if (nomInstitution.isEmpty()) {
                Institution first = institutions.get(0);
                nomInstitution = first.getNomInstitution();
                sigleInstitution = first.getSigleInstitution();
                logoPath = first.getLogoInstitution();
            }
        }

        // Logo avec fallback
        sb.append("<div class=\"logo-container\" id=\"logoContainer\">");
        if (logoPath != null && !logoPath.isEmpty()) {
            String logoUrl = logoPath.startsWith("/data/") ? logoPath : "/data/" + logoPath;
            sb.append("<img src=\"").append(escapeHtml(logoUrl)).append("\" alt=\"Logo\" id=\"institutionLogo\" onerror=\"this.style.display='none';document.getElementById('logoFallback').style.display='flex';\">");
            sb.append("<div id=\"logoFallback\" style=\"display:none;\"><i class=\"fas fa-university\"></i></div>");
        } else {
            sb.append("<i class=\"fas fa-university\" id=\"logoFallback\"></i>");
        }
        sb.append("</div>");

        // Titre
        sb.append("<h1>Inscription Étudiant</h1>");
        sb.append("<p class=\"subtitle\">Créez votre compte et rejoignez notre communauté éducative</p>");
        sb.append("</div>");

        // ===== BANNIÈRE INSTITUTION CENTRÉE =====
        if (!nomInstitution.isEmpty()) {
            sb.append("<div class=\"institution-banner\" id=\"institutionBanner\">");
            sb.append("<div class=\"institution-badge\">");
            sb.append("<i class=\"fas fa-university\"></i>");
            sb.append("</div>");
            sb.append("<div class=\"institution-info\">");
            sb.append("<span class=\"institution-name\">").append(escapeHtml(nomInstitution)).append("</span>");
            if (sigleInstitution != null && !sigleInstitution.isEmpty()) {
                sb.append("<span class=\"institution-sigle\">").append(escapeHtml(sigleInstitution)).append("</span>");
            }
            sb.append("</div>");
            sb.append("</div>");
        }

        sb.append("</div>");

        // ===== MESSAGE =====
        if (message != null && !message.isEmpty()) {
            if (message.startsWith("✅")) {
                sb.append("<div class=\"message success\">").append(message).append("</div>");
            } else {
                sb.append("<div class=\"message error\"><i class=\"fas fa-exclamation-triangle\"></i> ").append(escapeHtml(message)).append("</div>");
            }
        }

        sb.append("<div class=\"card\">");
        sb.append("<form method=\"POST\" action=\"/inscription/etudiant\" class=\"inscription-form\" enctype=\"multipart/form-data\" id=\"inscriptionForm\">");

        // ============================================================
        // 1. INSTITUTION
        // ============================================================
        sb.append("<div class=\"section\">");
        sb.append("<h2><i class=\"fas fa-university\"></i> Institution</h2>");
        sb.append("<div class=\"form-row center-row\">");
        sb.append("<div class=\"form-group full-width centered-group\">");
        sb.append("<label for=\"institutionId\">Choisissez votre institution <span class=\"required\">*</span></label>");
        sb.append("<div class=\"select-wrapper\">");
        sb.append("<select name=\"institutionId\" id=\"institutionId\" required>");
        sb.append("<option value=\"\">-- Sélectionner --</option>");
        if (institutions != null) {
            for (Institution inst : institutions) {
                String sel = inst.getInstitutionId().equals(institutionSel) ? "selected" : "";
                sb.append("<option value=\"").append(escapeHtml(inst.getInstitutionId())).append("\" ").append(sel).append(">")
                  .append(escapeHtml(inst.getNomInstitution())).append(" (").append(escapeHtml(inst.getSigleInstitution())).append(")</option>");
            }
        }
        sb.append("</select>");
        sb.append("<i class=\"fas fa-chevron-down select-arrow\"></i>");
        sb.append("</div>");
        sb.append("<div class=\"filter-hint\">Sélectionnez votre institution pour charger les années et périodes disponibles</div>");
        sb.append("</div>");
        sb.append("</div>");
        sb.append("</div>");

        // ===== 2. IDENTITÉ =====
        sb.append("<div class=\"section\">");
        sb.append("<h2><i class=\"fas fa-user\"></i> Identité</h2>");
        sb.append("<div class=\"form-row\">");
        sb.append("<div class=\"form-group\"><label for=\"nom\">Nom <span class=\"required\">*</span></label><input type=\"text\" id=\"nom\" name=\"nom\" required></div>");
        sb.append("<div class=\"form-group\"><label for=\"prenom\">Prénom <span class=\"required\">*</span></label><input type=\"text\" id=\"prenom\" name=\"prenom\" required></div>");
        sb.append("</div>");
        sb.append("<div class=\"form-row\">");
        sb.append("<div class=\"form-group\"><label for=\"sexe\">Sexe <span class=\"required\">*</span></label>");
        sb.append("<select id=\"sexe\" name=\"sexe\" required>");
        sb.append("<option value=\"\">-- Sélectionner --</option>");
        for (String s : SEXES) {
            sb.append("<option value=\"").append(escapeHtml(s)).append("\">").append(escapeHtml(s)).append("</option>");
        }
        sb.append("</select></div>");
        sb.append("<div class=\"form-group\"><label for=\"dateNaissance\">Date de naissance <span class=\"required\">*</span></label><input type=\"date\" id=\"dateNaissance\" name=\"dateNaissance\" value=\"").append(LocalDate.now().minusYears(18)).append("\" required></div>");
        sb.append("</div>");
        sb.append("<div class=\"form-row\">");
        sb.append("<div class=\"form-group\"><label for=\"groupeSanguin\">Groupe sanguin</label>");
        sb.append("<select id=\"groupeSanguin\" name=\"groupeSanguin\">");
        sb.append("<option value=\"\">-- Non renseigné --</option>");
        for (String g : GROUPES_SANGUINS) {
            sb.append("<option value=\"").append(escapeHtml(g)).append("\">").append(escapeHtml(g)).append("</option>");
        }
        sb.append("</select></div>");
        sb.append("</div>");
        sb.append("</div>");

        // ===== 3. COORDONNÉES =====
        sb.append("<div class=\"section\">");
        sb.append("<h2><i class=\"fas fa-address-book\"></i> Coordonnées</h2>");
        sb.append("<div class=\"form-row\">");
        sb.append("<div class=\"form-group\"><label for=\"telephone\">Téléphone <span class=\"required\">*</span></label>");
        sb.append("<div class=\"phone-wrapper\">");
        sb.append("<span class=\"phone-prefix\">(+509)</span>");
        sb.append("<input type=\"text\" id=\"telephone\" name=\"telephone\" placeholder=\"32-45-67-89\" maxlength=\"11\" required>");
        sb.append("</div>");
        sb.append("<span class=\"field-hint\">Format : 32-45-67-89</span>");
        sb.append("</div>");
        sb.append("<div class=\"form-group\"><label for=\"email\">Email <span class=\"required\">*</span></label><input type=\"email\" id=\"email\" name=\"email\" required></div>");
        sb.append("</div>");
        sb.append("<div class=\"form-row\">");
        sb.append("<div class=\"form-group full-width\"><label for=\"adresse\">Adresse <span class=\"required\">*</span></label><input type=\"text\" id=\"adresse\" name=\"adresse\" required></div>");
        sb.append("</div>");
        sb.append("</div>");

        // ===== 4. LIEU DE NAISSANCE =====
        sb.append("<div class=\"section\">");
        sb.append("<h2><i class=\"fas fa-map-pin\"></i> Lieu de naissance</h2>");
        sb.append("<div class=\"form-row\">");
        sb.append("<div class=\"form-group\"><label for=\"departementNaissance\">Département <span class=\"required\">*</span></label>");
        sb.append("<select id=\"departementNaissance\" name=\"departementNaissance\" required>");
        sb.append("<option value=\"\">Sélectionner</option>");
        for (String dep : DEPARTEMENTS) {
            sb.append("<option value=\"").append(escapeHtml(dep)).append("\">").append(escapeHtml(dep)).append("</option>");
        }
        sb.append("</select></div>");
        sb.append("<div class=\"form-group\"><label for=\"communeNaissance\">Commune <span class=\"required\">*</span></label>");
        sb.append("<select id=\"communeNaissance\" name=\"communeNaissance\" required>");
        sb.append("<option value=\"\">Sélectionner d'abord un département</option>");
        sb.append("</select></div>");
        sb.append("</div>");
        sb.append("</div>");

        // ===== 5. PARENTS =====
        sb.append("<div class=\"section\">");
        sb.append("<h2><i class=\"fas fa-users\"></i> Parents</h2>");
        sb.append("<div class=\"form-row\">");
        sb.append("<div class=\"form-group\"><label for=\"nomPere\">Nom du père <span class=\"required\">*</span></label><input type=\"text\" id=\"nomPere\" name=\"nomPere\" required></div>");
        sb.append("<div class=\"form-group\"><label for=\"prenomPere\">Prénom du père <span class=\"required\">*</span></label><input type=\"text\" id=\"prenomPere\" name=\"prenomPere\" required></div>");
        sb.append("</div>");
        sb.append("<div class=\"form-row\">");
        sb.append("<div class=\"form-group\"><label for=\"nomMere\">Nom de la mère <span class=\"required\">*</span></label><input type=\"text\" id=\"nomMere\" name=\"nomMere\" required></div>");
        sb.append("<div class=\"form-group\"><label for=\"prenomMere\">Prénom de la mère <span class=\"required\">*</span></label><input type=\"text\" id=\"prenomMere\" name=\"prenomMere\" required></div>");
        sb.append("</div>");
        sb.append("<div class=\"form-row\">");
        sb.append("<div class=\"form-group\"><label for=\"telephoneParents\">Téléphone des parents <span class=\"required\">*</span></label>");
        sb.append("<div class=\"phone-wrapper\">");
        sb.append("<span class=\"phone-prefix\">(+509)</span>");
        sb.append("<input type=\"text\" id=\"telephoneParents\" name=\"telephoneParents\" placeholder=\"32-45-67-89\" maxlength=\"11\" required>");
        sb.append("</div>");
        sb.append("<span class=\"field-hint\">Format : 32-45-67-89</span>");
        sb.append("</div>");
        sb.append("<div class=\"form-group\"><label for=\"residenceParents\">Résidence des parents <span class=\"required\">*</span></label><input type=\"text\" id=\"residenceParents\" name=\"residenceParents\" required></div>");
        sb.append("</div>");
        sb.append("</div>");

        // ===== 6. LIEU DE RÉSIDENCE =====
        sb.append("<div class=\"section\">");
        sb.append("<h2><i class=\"fas fa-home\"></i> Lieu de résidence</h2>");
        sb.append("<div class=\"form-row\">");
        sb.append("<div class=\"form-group\"><label for=\"departementResidence\">Département de résidence <span class=\"required\">*</span></label>");
        sb.append("<select id=\"departementResidence\" name=\"departementResidence\" required>");
        sb.append("<option value=\"\">Sélectionner</option>");
        for (String dep : DEPARTEMENTS) {
            sb.append("<option value=\"").append(escapeHtml(dep)).append("\">").append(escapeHtml(dep)).append("</option>");
        }
        sb.append("</select></div>");
        sb.append("<div class=\"form-group\"><label for=\"communeResidence\">Commune de résidence <span class=\"required\">*</span></label>");
        sb.append("<select id=\"communeResidence\" name=\"communeResidence\" required>");
        sb.append("<option value=\"\">Sélectionner d'abord un département</option>");
        sb.append("</select></div>");
        sb.append("</div>");
        sb.append("</div>");

        // ===== 7. RESPONSABLE LÉGAL =====
        sb.append("<div class=\"section\">");
        sb.append("<h2><i class=\"fas fa-user-tie\"></i> Responsable légal</h2>");
        sb.append("<div class=\"form-row\">");
        sb.append("<div class=\"form-group\"><label for=\"lienParente\">Lien de parenté <span class=\"required\">*</span></label>");
        sb.append("<select id=\"lienParente\" name=\"lienParente\" required>");
        sb.append("<option value=\"\">-- Sélectionner --</option>");
        sb.append("<option value=\"Père\">Père</option>");
        sb.append("<option value=\"Mère\">Mère</option>");
        sb.append("<option value=\"Tuteur\">Tuteur</option>");
        sb.append("<option value=\"Curateur\">Curateur</option>");
        sb.append("<option value=\"Autre\">Autre</option>");
        sb.append("</select></div>");
        sb.append("<div class=\"form-group\"><label for=\"typeResponsable\">Type de responsable <span class=\"required\">*</span></label>");
        sb.append("<select id=\"typeResponsable\" name=\"typeResponsable\" required>");
        sb.append("<option value=\"\">-- Sélectionner --</option>");
        sb.append("<option value=\"Légal\">Légal</option>");
        sb.append("<option value=\"Conventionnel\">Conventionnel</option>");
        sb.append("<option value=\"Autre\">Autre</option>");
        sb.append("</select></div>");
        sb.append("</div>");
        sb.append("<div class=\"form-row\">");
        sb.append("<div class=\"form-group\"><label for=\"nomResponsable\">Nom du responsable <span class=\"required\">*</span></label><input type=\"text\" id=\"nomResponsable\" name=\"nomResponsable\" required></div>");
        sb.append("<div class=\"form-group\"><label for=\"prenomResponsable\">Prénom du responsable <span class=\"required\">*</span></label><input type=\"text\" id=\"prenomResponsable\" name=\"prenomResponsable\" required></div>");
        sb.append("</div>");
        sb.append("</div>");

        // ===== 8. OBSERVATIONS =====
        sb.append("<div class=\"section\">");
        sb.append("<h2><i class=\"fas fa-pen\"></i> Observations</h2>");
        sb.append("<div class=\"form-row\">");
        sb.append("<div class=\"form-group full-width\">");
        sb.append("<label for=\"observations\">Observations (optionnel)</label>");
        sb.append("<textarea id=\"observations\" name=\"observations\" rows=\"3\" placeholder=\"Informations complémentaires...\"></textarea>");
        sb.append("</div>");
        sb.append("</div>");
        sb.append("</div>");

        // ===== 9. PHOTO =====
        sb.append("<div class=\"section\">");
        sb.append("<h2><i class=\"fas fa-camera\"></i> Photo</h2>");
        sb.append("<div class=\"form-row\">");
        sb.append("<div class=\"form-group full-width\">");
        sb.append("<label for=\"photo\">Photo d'identité <span class=\"required\">*</span></label>");
        sb.append("<div class=\"file-upload\">");
        sb.append("<input type=\"file\" id=\"photo\" name=\"photo\" accept=\"image/png,image/jpeg,image/gif,image/webp\" onchange=\"previewPhoto(event)\" required>");
        sb.append("<label for=\"photo\" class=\"photo-upload-btn\"><i class=\"fas fa-camera\"></i> Choisir une photo</label>");
        sb.append("<div id=\"photoPreview\" class=\"photo-preview\">");
        sb.append("<i class=\"fas fa-user-circle\"></i>");
        sb.append("<p>Aucune photo sélectionnée</p>");
        sb.append("</div>");
        sb.append("</div>");
        sb.append("<span class=\"file-hint\">Formats acceptés : PNG, JPG, GIF, WEBP (max 5 Mo)</span>");
        sb.append("</div>");
        sb.append("</div>");
        sb.append("</div>");

        // ===== 10. INSCRIPTION ACADÉMIQUE =====
        sb.append("<div class=\"section\">");
        sb.append("<h2><i class=\"fas fa-graduation-cap\"></i> Inscription académique</h2>");
        sb.append("<div class=\"form-row\">");
        sb.append("<div class=\"form-group\"><label for=\"annee\">Année académique <span class=\"required\">*</span></label>");
        sb.append("<select id=\"annee\" name=\"annee\" required>");
        sb.append("<option value=\"\">-- Sélectionner d'abord une institution --</option>");
        if (annees != null) {
            for (String a : annees) {
                String sel = a.equals(anneeSel) ? "selected" : "";
                sb.append("<option value=\"").append(escapeHtml(a)).append("\" ").append(sel).append(">").append(escapeHtml(a)).append("</option>");
            }
        }
        sb.append("</select></div>");
        sb.append("<div class=\"form-group\"><label for=\"periode\">Période <span class=\"required\">*</span></label>");
        sb.append("<select id=\"periode\" name=\"periode\" required>");
        sb.append("<option value=\"\">-- Sélectionner d'abord une institution --</option>");
        if (periodes != null) {
            for (String p : periodes) {
                String sel = p.equals(periodeSel) ? "selected" : "";
                sb.append("<option value=\"").append(escapeHtml(p)).append("\" ").append(sel).append(">").append(escapeHtml(p)).append("</option>");
            }
        }
        sb.append("</select></div>");
        sb.append("</div>");

        sb.append("<div class=\"form-row\">");
        sb.append("<div class=\"form-group\"><label for=\"classe\">Classe <span class=\"required\">*</span></label>");
        sb.append("<select id=\"classe\" name=\"classe\" required>");
        sb.append("<option value=\"\">-- Sélectionner d'abord année et période --</option>");
        if (classes != null) {
            for (Classe c : classes) {
                String promotionDisplay = c.getPromotion() != null ? " (" + escapeHtml(c.getPromotion()) + ")" : "";
                sb.append("<option value=\"").append(escapeHtml(c.getNomClasse())).append("\">")
                  .append(escapeHtml(c.getNomClasse())).append(promotionDisplay).append("</option>");
            }
        }
        sb.append("</select></div>");
        sb.append("<div class=\"form-group\"><label for=\"promotion\">Promotion</label>");
        sb.append("<input type=\"text\" id=\"promotion\" name=\"promotion\" readonly placeholder=\"Déterminée automatiquement\" style=\"background:#f1f5f9; cursor:not-allowed;\">");
        sb.append("</div>");
        sb.append("</div>");

        // ===== OPTION =====
        sb.append("<div class=\"form-row\">");
        sb.append("<div class=\"form-group\"><label for=\"option\">Option (si applicable)</label>");
        sb.append("<select id=\"option\" name=\"option\">");
        sb.append("<option value=\"\">Aucune</option>");
        if (options != null) {
            for (Options opt : options) {
                String optValue = opt.getOption();
                if (optValue != null && !optValue.isBlank()) {
                    sb.append("<option value=\"").append(escapeHtml(optValue)).append("\">").append(escapeHtml(optValue)).append("</option>");
                }
            }
        }
        sb.append("</select></div>");
        sb.append("<div class=\"form-group\"><label for=\"matricule\">Matricule</label>");
        sb.append("<input type=\"text\" id=\"matricule\" name=\"matricule\" placeholder=\"À saisir (ex: M-ABC123)\">");
        sb.append("</div>");
        sb.append("</div>");

        // ===== NINU OBLIGATOIRE AVEC FORMAT =====
        sb.append("<div class=\"form-row\">");
        sb.append("<div class=\"form-group full-width\">");
        sb.append("<label for=\"ninu\">NINU <span class=\"required\">*</span></label>");
        sb.append("<div class=\"ninu-wrapper\">");
        sb.append("<input type=\"text\" id=\"ninu\" name=\"ninu\" placeholder=\"123-456-789-0\" maxlength=\"14\" required>");
        sb.append("</div>");
        sb.append("<span class=\"field-hint\">Format : 123-456-789-0 (10 chiffres commençant par 1)</span>");
        sb.append("</div>");
        sb.append("</div>");

        // ===== CHAMPS CACHÉS POUR FORCER L'ENVOI =====
        sb.append("<input type=\"hidden\" name=\"ninu_force\" id=\"ninu_force\" value=\"\">");
        sb.append("<input type=\"hidden\" name=\"form_submit\" id=\"form_submit\" value=\"1\">");

        sb.append("</div>"); // fin section académique

        sb.append("<button type=\"submit\" class=\"btn-submit\" id=\"btnSubmit\"><i class=\"fas fa-user-plus\"></i> S'inscrire</button>");
        sb.append("</form>");
        sb.append("</div>");

        sb.append("<div class=\"footer\">© ").append(Year.now().getValue()).append(" M-TECH Academy - Tous droits réservés</div>");
        sb.append("</div>");

        // ===== JAVASCRIPT =====
        String communesJson = buildCommunesJson();
        sb.append("<script>");
        sb.append("// Données des communes\n");
        sb.append("const communesData = ").append(communesJson).append(";\n");
        sb.append("\n");

        sb.append("// Éléments du DOM\n");
        sb.append("const institutionSelect = document.getElementById('institutionId');\n");
        sb.append("const anneeSelect = document.getElementById('annee');\n");
        sb.append("const periodeSelect = document.getElementById('periode');\n");
        sb.append("const classeSelect = document.getElementById('classe');\n");
        sb.append("const promotionInput = document.getElementById('promotion');\n");
        sb.append("const optionSelect = document.getElementById('option');\n");
        sb.append("const departementNaissanceSelect = document.getElementById('departementNaissance');\n");
        sb.append("const communeNaissanceSelect = document.getElementById('communeNaissance');\n");
        sb.append("const departementResidenceSelect = document.getElementById('departementResidence');\n");
        sb.append("const communeResidenceSelect = document.getElementById('communeResidence');\n");
        sb.append("const ninuInput = document.getElementById('ninu');\n");
        sb.append("const ninuForce = document.getElementById('ninu_force');\n");
        sb.append("const form = document.getElementById('inscriptionForm');\n");
        sb.append("const telephoneInput = document.getElementById('telephone');\n");
        sb.append("const telephoneParentsInput = document.getElementById('telephoneParents');\n");
        sb.append("\n");

        // ===== DONNÉES DES INSTITUTIONS =====
        sb.append("// Données des institutions\n");
        sb.append("const institutionsData = {");
        if (institutions != null) {
            for (int i = 0; i < institutions.size(); i++) {
                Institution inst = institutions.get(i);
                String logo = inst.getLogoInstitution();
                if (logo == null) logo = "";
                String logoUrl = logo.startsWith("/data/") ? logo : "/data/" + logo;
                sb.append("\"").append(escapeJson(inst.getInstitutionId())).append("\": {");
                sb.append("\"nom\":\"").append(escapeJson(inst.getNomInstitution())).append("\",");
                sb.append("\"sigle\":\"").append(escapeJson(inst.getSigleInstitution())).append("\",");
                sb.append("\"logo\":\"").append(escapeJson(logoUrl)).append("\"");
                sb.append("}");
                if (i < institutions.size() - 1) sb.append(",");
            }
        }
        sb.append("};\n");
        sb.append("\n");

        // ===== FONCTIONS DE VALIDATION =====
        sb.append("// ---------- Validation du NINU ----------\n");
        sb.append("function validerNINU(ninu) {\n");
        sb.append("    // Enlever les tirets\n");
        sb.append("    const clean = ninu.replace(/-/g, '');\n");
        sb.append("    // Vérifier qu'il y a exactement 10 chiffres\n");
        sb.append("    if (!/^\\d{10}$/.test(clean)) {\n");
        sb.append("        return { valide: false, message: 'Le NINU doit contenir exactement 10 chiffres.' };\n");
        sb.append("    }\n");
        sb.append("    // Vérifier que le premier chiffre est 1\n");
        sb.append("    if (!clean.startsWith('1')) {\n");
        sb.append("        return { valide: false, message: 'Le NINU doit commencer par le chiffre 1.' };\n");
        sb.append("    }\n");
        sb.append("    return { valide: true };\n");
        sb.append("}\n");
        sb.append("\n");

        sb.append("// ---------- Formatage automatique du NINU ----------\n");
        sb.append("function formaterNINU(input) {\n");
        sb.append("    let value = input.value.replace(/[^0-9]/g, '');\n");
        sb.append("    let formatted = '';\n");
        sb.append("    for (let i = 0; i < value.length && i < 10; i++) {\n");
        sb.append("        if (i === 3 || i === 6) {\n");
        sb.append("            formatted += '-';\n");
        sb.append("        }\n");
        sb.append("        formatted += value[i];\n");
        sb.append("        if (i === 8) {\n");
        sb.append("            formatted += '-';\n");
        sb.append("        }\n");
        sb.append("    }\n");
        sb.append("    input.value = formatted;\n");
        sb.append("}\n");
        sb.append("\n");

        sb.append("// ---------- Validation du téléphone ----------\n");
        sb.append("function validerTelephone(telephone) {\n");
        sb.append("    // Enlever les tirets\n");
        sb.append("    const clean = telephone.replace(/-/g, '');\n");
        sb.append("    // Vérifier qu'il y a exactement 8 chiffres\n");
        sb.append("    if (!/^\\d{8}$/.test(clean)) {\n");
        sb.append("        return { valide: false, message: 'Le téléphone doit contenir 8 chiffres au format 32-45-67-89.' };\n");
        sb.append("    }\n");
        sb.append("    return { valide: true };\n");
        sb.append("}\n");
        sb.append("\n");

        sb.append("// ---------- Formatage automatique du téléphone ----------\n");
        sb.append("function formaterTelephone(input) {\n");
        sb.append("    let value = input.value.replace(/[^0-9]/g, '');\n");
        sb.append("    let formatted = '';\n");
        sb.append("    for (let i = 0; i < value.length && i < 8; i++) {\n");
        sb.append("        if (i === 2 || i === 4) {\n");
        sb.append("            formatted += '-';\n");
        sb.append("        }\n");
        sb.append("        formatted += value[i];\n");
        sb.append("        if (i === 5) {\n");
        sb.append("            formatted += '-';\n");
        sb.append("        }\n");
        sb.append("    }\n");
        sb.append("    input.value = formatted;\n");
        sb.append("}\n");
        sb.append("\n");

        sb.append("// ---------- Mise à jour du logo et de la bannière ----------\n");
        sb.append("function updateInstitutionInfo() {\n");
        sb.append("    const selectedId = institutionSelect.value;\n");
        sb.append("    const data = institutionsData[selectedId];\n");
        sb.append("    const logoContainer = document.getElementById('logoContainer');\n");
        sb.append("    const logoFallback = document.getElementById('logoFallback');\n");
        sb.append("    const banner = document.getElementById('institutionBanner');\n");
        sb.append("    \n");
        sb.append("    if (data && data.nom) {\n");
        sb.append("        if (data.logo && data.logo !== '/data/') {\n");
        sb.append("            logoContainer.innerHTML = '<img src=\"' + data.logo + '\" alt=\"Logo\" id=\"institutionLogo\" onerror=\"this.style.display=\\'none\\';document.getElementById(\\'logoFallback\\').style.display=\\'flex\\';\">';\n");
        sb.append("            logoContainer.innerHTML += '<div id=\"logoFallback\" style=\"display:none;\"><i class=\"fas fa-university\"></i></div>';\n");
        sb.append("        } else {\n");
        sb.append("            logoContainer.innerHTML = '<i class=\"fas fa-university\" id=\"logoFallback\"></i>';\n");
        sb.append("        }\n");
        sb.append("        \n");
        sb.append("        if (banner) {\n");
        sb.append("            banner.innerHTML = '';\n");
        sb.append("            const badge = document.createElement('div');\n");
        sb.append("            badge.className = 'institution-badge';\n");
        sb.append("            badge.innerHTML = '<i class=\"fas fa-university\"></i>';\n");
        sb.append("            const info = document.createElement('div');\n");
        sb.append("            info.className = 'institution-info';\n");
        sb.append("            info.innerHTML = '<span class=\"institution-name\">' + data.nom + '</span>';\n");
        sb.append("            if (data.sigle && data.sigle !== '') {\n");
        sb.append("                info.innerHTML += '<span class=\"institution-sigle\">' + data.sigle + '</span>';\n");
        sb.append("            }\n");
        sb.append("            banner.appendChild(badge);\n");
        sb.append("            banner.appendChild(info);\n");
        sb.append("        }\n");
        sb.append("    } else {\n");
        sb.append("        logoContainer.innerHTML = '<i class=\"fas fa-university\" id=\"logoFallback\"></i>';\n");
        sb.append("        if (banner) {\n");
        sb.append("            banner.innerHTML = '';\n");
        sb.append("        }\n");
        sb.append("    }\n");
        sb.append("}\n");
        sb.append("\n");

        sb.append("// ---------- Validation et envoi du formulaire ----------\n");
        sb.append("function validerEtEnvoyer() {\n");
        sb.append("    // Vérifier le NINU\n");
        sb.append("    const ninuValue = ninuInput ? ninuInput.value.trim() : '';\n");
        sb.append("    const ninuValidation = validerNINU(ninuValue);\n");
        sb.append("    if (!ninuValidation.valide) {\n");
        sb.append("        alert('NINU invalide : ' + ninuValidation.message);\n");
        sb.append("        if (ninuInput) ninuInput.focus();\n");
        sb.append("        return false;\n");
        sb.append("    }\n");
        sb.append("    \n");
        sb.append("    // Vérifier le téléphone\n");
        sb.append("    if (telephoneInput) {\n");
        sb.append("        const telValue = telephoneInput.value.trim();\n");
        sb.append("        const telValidation = validerTelephone(telValue);\n");
        sb.append("        if (!telValidation.valide) {\n");
        sb.append("            alert('Téléphone invalide : ' + telValidation.message);\n");
        sb.append("            telephoneInput.focus();\n");
        sb.append("            return false;\n");
        sb.append("        }\n");
        sb.append("    }\n");
        sb.append("    \n");
        sb.append("    // Vérifier le téléphone des parents\n");
        sb.append("    if (telephoneParentsInput) {\n");
        sb.append("        const telParentValue = telephoneParentsInput.value.trim();\n");
        sb.append("        const telParentValidation = validerTelephone(telParentValue);\n");
        sb.append("        if (!telParentValidation.valide) {\n");
        sb.append("            alert('Téléphone des parents invalide : ' + telParentValidation.message);\n");
        sb.append("            telephoneParentsInput.focus();\n");
        sb.append("            return false;\n");
        sb.append("        }\n");
        sb.append("    }\n");
        sb.append("    \n");
        sb.append("    // FORCER L'ENVOI DU NINU via le champ caché\n");
        sb.append("    if (ninuForce && ninuInput) {\n");
        sb.append("        const cleanNinu = ninuInput.value.replace(/-/g, '');\n");
        sb.append("        ninuForce.value = cleanNinu;\n");
        sb.append("        console.log('✅ NINU forcé :', ninuForce.value);\n");
        sb.append("    }\n");
        sb.append("    \n");
        sb.append("    return true;\n");
        sb.append("}\n");
        sb.append("\n");

        sb.append("// ---------- Événements de formatage ----------\n");
        sb.append("if (ninuInput) {\n");
        sb.append("    ninuInput.addEventListener('input', function() {\n");
        sb.append("        formaterNINU(this);\n");
        sb.append("    });\n");
        sb.append("}\n");
        sb.append("\n");
        sb.append("if (telephoneInput) {\n");
        sb.append("    telephoneInput.addEventListener('input', function() {\n");
        sb.append("        formaterTelephone(this);\n");
        sb.append("    });\n");
        sb.append("}\n");
        sb.append("\n");
        sb.append("if (telephoneParentsInput) {\n");
        sb.append("    telephoneParentsInput.addEventListener('input', function() {\n");
        sb.append("        formaterTelephone(this);\n");
        sb.append("    });\n");
        sb.append("}\n");
        sb.append("\n");

        sb.append("// Attacher l'événement submit\n");
        sb.append("if (form) {\n");
        sb.append("    form.addEventListener('submit', function(e) {\n");
        sb.append("        if (!validerEtEnvoyer()) {\n");
        sb.append("            e.preventDefault();\n");
        sb.append("        }\n");
        sb.append("    });\n");
        sb.append("}\n");
        sb.append("\n");

        sb.append("// ---------- Mise à jour des communes de naissance ----------\n");
        sb.append("function updateCommunesNaissance() {\n");
        sb.append("    const departement = departementNaissanceSelect.value;\n");
        sb.append("    communeNaissanceSelect.innerHTML = '<option value=\"\">Sélectionner</option>';\n");
        sb.append("    if (departement && communesData[departement] && Array.isArray(communesData[departement])) {\n");
        sb.append("        communesData[departement].forEach(function(commune) {\n");
        sb.append("            const option = document.createElement('option');\n");
        sb.append("            option.value = commune;\n");
        sb.append("            option.textContent = commune;\n");
        sb.append("            communeNaissanceSelect.appendChild(option);\n");
        sb.append("        });\n");
        sb.append("    }\n");
        sb.append("}\n");
        sb.append("\n");
        sb.append("departementNaissanceSelect.addEventListener('change', updateCommunesNaissance);\n");
        sb.append("\n");

        sb.append("// ---------- Mise à jour des communes de résidence ----------\n");
        sb.append("function updateCommunesResidence() {\n");
        sb.append("    const departement = departementResidenceSelect.value;\n");
        sb.append("    communeResidenceSelect.innerHTML = '<option value=\"\">Sélectionner</option>';\n");
        sb.append("    if (departement && communesData[departement] && Array.isArray(communesData[departement])) {\n");
        sb.append("        communesData[departement].forEach(function(commune) {\n");
        sb.append("            const option = document.createElement('option');\n");
        sb.append("            option.value = commune;\n");
        sb.append("            option.textContent = commune;\n");
        sb.append("            communeResidenceSelect.appendChild(option);\n");
        sb.append("        });\n");
        sb.append("    }\n");
        sb.append("}\n");
        sb.append("\n");
        sb.append("if (departementResidenceSelect) {\n");
        sb.append("    departementResidenceSelect.addEventListener('change', updateCommunesResidence);\n");
        sb.append("}\n");
        sb.append("\n");

        sb.append("// ---------- Mise à jour des années et périodes ----------\n");
        sb.append("function loadAnneesPeriodes() {\n");
        sb.append("    const institutionId = institutionSelect.value;\n");
        sb.append("    if (!institutionId) {\n");
        sb.append("        anneeSelect.innerHTML = '<option value=\"\">-- Sélectionner d\\'abord une institution --</option>';\n");
        sb.append("        periodeSelect.innerHTML = '<option value=\"\">-- Sélectionner d\\'abord une institution --</option>';\n");
        sb.append("        classeSelect.innerHTML = '<option value=\"\">-- Sélectionner d\\'abord une institution --</option>';\n");
        sb.append("        optionSelect.innerHTML = '<option value=\"\">Aucune</option>';\n");
        sb.append("        return;\n");
        sb.append("    }\n");
        sb.append("    anneeSelect.innerHTML = '<option value=\"\">Chargement...</option>';\n");
        sb.append("    periodeSelect.innerHTML = '<option value=\"\">Chargement...</option>';\n");
        sb.append("    const url = '/inscription/etudiant/api/annees-periodes?institutionId=' + encodeURIComponent(institutionId);\n");
        sb.append("    fetch(url)\n");
        sb.append("        .then(response => response.json())\n");
        sb.append("        .then(data => {\n");
        sb.append("            if (data.success) {\n");
        sb.append("                anneeSelect.innerHTML = '<option value=\"\">-- Sélectionner --</option>';\n");
        sb.append("                data.annees.forEach(function(annee) {\n");
        sb.append("                    const opt = document.createElement('option');\n");
        sb.append("                    opt.value = annee;\n");
        sb.append("                    opt.textContent = annee;\n");
        sb.append("                    anneeSelect.appendChild(opt);\n");
        sb.append("                });\n");
        sb.append("                periodeSelect.innerHTML = '<option value=\"\">-- Sélectionner --</option>';\n");
        sb.append("                data.periodes.forEach(function(periode) {\n");
        sb.append("                    const opt = document.createElement('option');\n");
        sb.append("                    opt.value = periode;\n");
        sb.append("                    opt.textContent = periode;\n");
        sb.append("                    periodeSelect.appendChild(opt);\n");
        sb.append("                });\n");
        sb.append("                if (anneeSelect.value && periodeSelect.value) {\n");
        sb.append("                    loadClasses();\n");
        sb.append("                }\n");
        sb.append("            }\n");
        sb.append("        })\n");
        sb.append("        .catch(err => console.error('Erreur:', err));\n");
        sb.append("}\n");
        sb.append("\n");
        sb.append("institutionSelect.addEventListener('change', function() {\n");
        sb.append("    loadAnneesPeriodes();\n");
        sb.append("    updateInstitutionInfo();\n");
        sb.append("});\n");
        sb.append("\n");

        sb.append("// ---------- Chargement des options dynamiques ----------\n");
        sb.append("function loadOptions() {\n");
        sb.append("    const institutionId = institutionSelect.value;\n");
        sb.append("    const annee = anneeSelect.value;\n");
        sb.append("    const periode = periodeSelect.value;\n");
        sb.append("    const classe = classeSelect.value;\n");
        sb.append("    const promotion = promotionInput.value;\n");
        sb.append("    if (!institutionId || !annee || !periode || !classe) {\n");
        sb.append("        optionSelect.innerHTML = '<option value=\"\">Aucune</option>';\n");
        sb.append("        return;\n");
        sb.append("    }\n");
        sb.append("    const url = '/inscription/etudiant/api/options?institutionId=' + encodeURIComponent(institutionId) +\n");
        sb.append("        '&annee=' + encodeURIComponent(annee) +\n");
        sb.append("        '&periode=' + encodeURIComponent(periode) +\n");
        sb.append("        '&classe=' + encodeURIComponent(classe) +\n");
        sb.append("        (promotion ? '&promotion=' + encodeURIComponent(promotion) : '');\n");
        sb.append("    fetch(url)\n");
        sb.append("        .then(response => response.json())\n");
        sb.append("        .then(data => {\n");
        sb.append("            if (data.success) {\n");
        sb.append("                optionSelect.innerHTML = '<option value=\"\">Aucune</option>';\n");
        sb.append("                data.data.forEach(function(opt) {\n");
        sb.append("                    const option = document.createElement('option');\n");
        sb.append("                    option.value = opt.option;\n");
        sb.append("                    option.textContent = opt.option;\n");
        sb.append("                    optionSelect.appendChild(option);\n");
        sb.append("                });\n");
        sb.append("            }\n");
        sb.append("        })\n");
        sb.append("        .catch(err => console.error('Erreur chargement options:', err));\n");
        sb.append("}\n");
        sb.append("\n");

        sb.append("// ---------- Mise à jour des classes et promotion ----------\n");
        sb.append("function loadClasses() {\n");
        sb.append("    const institutionId = institutionSelect.value;\n");
        sb.append("    const annee = anneeSelect.value;\n");
        sb.append("    const periode = periodeSelect.value;\n");
        sb.append("    if (!institutionId || !annee || !periode) {\n");
        sb.append("        classeSelect.innerHTML = '<option value=\"\">-- Sélectionner d\\'abord année et période --</option>';\n");
        sb.append("        promotionInput.value = '';\n");
        sb.append("        optionSelect.innerHTML = '<option value=\"\">Aucune</option>';\n");
        sb.append("        return;\n");
        sb.append("    }\n");
        sb.append("    classeSelect.innerHTML = '<option value=\"\">Chargement...</option>';\n");
        sb.append("    const url = '/inscription/etudiant/api/classes?institutionId=' + encodeURIComponent(institutionId) + '&annee=' + encodeURIComponent(annee) + '&periode=' + encodeURIComponent(periode);\n");
        sb.append("    fetch(url)\n");
        sb.append("        .then(response => response.json())\n");
        sb.append("        .then(data => {\n");
        sb.append("            if (data.success) {\n");
        sb.append("                classeSelect.innerHTML = '<option value=\"\">Sélectionner</option>';\n");
        sb.append("                data.data.forEach(function(cl) {\n");
        sb.append("                    const opt = document.createElement('option');\n");
        sb.append("                    opt.value = cl.nomClasse;\n");
        sb.append("                    opt.textContent = cl.nomClasse + (cl.promotion ? ' (' + cl.promotion + ')' : '');\n");
        sb.append("                    classeSelect.appendChild(opt);\n");
        sb.append("                });\n");
        sb.append("                const selectedClasse = classeSelect.value;\n");
        sb.append("                const selectedData = data.data.find(cl => cl.nomClasse === selectedClasse);\n");
        sb.append("                if (selectedData && selectedData.promotion) {\n");
        sb.append("                    promotionInput.value = selectedData.promotion;\n");
        sb.append("                } else {\n");
        sb.append("                    promotionInput.value = '';\n");
        sb.append("                }\n");
        sb.append("                if (classeSelect.value) {\n");
        sb.append("                    loadOptions();\n");
        sb.append("                }\n");
        sb.append("            }\n");
        sb.append("        })\n");
        sb.append("        .catch(err => console.error('Erreur:', err));\n");
        sb.append("}\n");
        sb.append("\n");
        sb.append("classeSelect.addEventListener('change', function() {\n");
        sb.append("    const institutionId = institutionSelect.value;\n");
        sb.append("    const annee = anneeSelect.value;\n");
        sb.append("    const periode = periodeSelect.value;\n");
        sb.append("    if (institutionId && annee && periode) {\n");
        sb.append("        const url = '/inscription/etudiant/api/classes?institutionId=' + encodeURIComponent(institutionId) + '&annee=' + encodeURIComponent(annee) + '&periode=' + encodeURIComponent(periode);\n");
        sb.append("        fetch(url)\n");
        sb.append("            .then(response => response.json())\n");
        sb.append("            .then(data => {\n");
        sb.append("                if (data.success) {\n");
        sb.append("                    const selectedClasse = classeSelect.value;\n");
        sb.append("                    const selectedData = data.data.find(cl => cl.nomClasse === selectedClasse);\n");
        sb.append("                    if (selectedData && selectedData.promotion) {\n");
        sb.append("                        promotionInput.value = selectedData.promotion;\n");
        sb.append("                    } else {\n");
        sb.append("                        promotionInput.value = '';\n");
        sb.append("                    }\n");
        sb.append("                    loadOptions();\n");
        sb.append("                }\n");
        sb.append("            });\n");
        sb.append("    }\n");
        sb.append("});\n");
        sb.append("\n");
        sb.append("anneeSelect.addEventListener('change', function() {\n");
        sb.append("    if (anneeSelect.value && periodeSelect.value) loadClasses();\n");
        sb.append("});\n");
        sb.append("periodeSelect.addEventListener('change', function() {\n");
        sb.append("    if (anneeSelect.value && periodeSelect.value) loadClasses();\n");
        sb.append("});\n");
        sb.append("\n");

        sb.append("// ---------- Prévisualisation de la photo ----------\n");
        sb.append("function previewPhoto(event) {\n");
        sb.append("    const file = event.target.files[0];\n");
        sb.append("    const preview = document.getElementById('photoPreview');\n");
        sb.append("    if (file) {\n");
        sb.append("        const reader = new FileReader();\n");
        sb.append("        reader.onload = function(e) {\n");
        sb.append("            preview.innerHTML = '<img src=\"' + e.target.result + '\" alt=\"Photo\">';\n");
        sb.append("        };\n");
        sb.append("        reader.readAsDataURL(file);\n");
        sb.append("    } else {\n");
        sb.append("        preview.innerHTML = '<i class=\"fas fa-user-circle\"></i><p>Aucune photo sélectionnée</p>';\n");
        sb.append("    }\n");
        sb.append("}\n");
        sb.append("\n");

        sb.append("// ---------- Initialisation ----------\n");
        sb.append("document.addEventListener('DOMContentLoaded', function() {\n");
        sb.append("    if (departementNaissanceSelect.value) updateCommunesNaissance();\n");
        sb.append("    if (departementResidenceSelect.value) updateCommunesResidence();\n");
        sb.append("    if (institutionSelect.value) {\n");
        sb.append("        loadAnneesPeriodes();\n");
        sb.append("        updateInstitutionInfo();\n");
        sb.append("    }\n");
        sb.append("    \n");
        sb.append("    console.log('🔍 Champ NINU présent :', document.getElementById('ninu') !== null);\n");
        sb.append("});\n");
        sb.append("</script>");

        sb.append("</body>");
        sb.append("</html>");
        return sb.toString();
    }

    // ========== MÉTHODES UTILITAIRES ==========
    private static String buildCommunesJson() {
        StringBuilder sb = new StringBuilder("{");
        for (String dep : Constantes.getDepartements()) {
            String[] communes = Constantes.getCommunes(dep);
            sb.append("\"").append(escapeJson(dep)).append("\":[");
            for (int i = 0; i < communes.length; i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(escapeJson(communes[i])).append("\"");
            }
            sb.append("],");
        }
        if (sb.length() > 1) sb.deleteCharAt(sb.length() - 1);
        sb.append("}");
        return sb.toString();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String renderStyles() {
        return """
        *{margin:0;padding:0;box-sizing:border-box;}
        body{font-family:'Plus Jakarta Sans',sans-serif;background:#f0f4f8;min-height:100vh;padding:20px;display:flex;justify-content:center;align-items:center;}
        .container{max-width:900px;width:100%;margin:0 auto;}

        .header{background:linear-gradient(135deg,#1e293b,#0f172a);color:white;padding:30px 30px 25px;border-radius:16px 16px 0 0;text-align:center;position:relative;overflow:hidden;}
        .header::before{content:'';position:absolute;top:-50%;left:-50%;width:200%;height:200%;background:radial-gradient(circle at center,rgba(59,130,246,0.1) 0%,transparent 70%);pointer-events:none;}
        .header-content{position:relative;z-index:1;}
        .logo-container{width:70px;height:70px;background:rgba(255,255,255,0.12);border-radius:50%;display:flex;align-items:center;justify-content:center;margin:0 auto 12px;border:2px solid rgba(255,255,255,0.15);backdrop-filter:blur(4px);transition:0.3s;overflow:hidden;}
        .logo-container:hover{transform:scale(1.05);background:rgba(255,255,255,0.2);}
        .logo-container img{width:100%;height:100%;object-fit:cover;border-radius:50%;}
        .logo-container i{font-size:32px;color:#3b82f6;}
        .header h1{font-size:26px;font-weight:700;margin-bottom:4px;letter-spacing:-0.5px;}
        .header .subtitle{color:#94a3b8;font-size:14px;font-weight:300;}

        .institution-banner{background:rgba(255,255,255,0.08);border-radius:12px;padding:12px 20px;margin-top:16px;display:inline-flex;align-items:center;gap:14px;border:1px solid rgba(255,255,255,0.06);backdrop-filter:blur(4px);}
        .institution-badge{width:36px;height:36px;background:rgba(59,130,246,0.2);border-radius:50%;display:flex;align-items:center;justify-content:center;font-size:16px;color:#3b82f6;flex-shrink:0;}
        .institution-info{display:flex;flex-direction:column;align-items:center;text-align:center;}
        .institution-name{font-size:15px;font-weight:600;color:#ffffff;line-height:1.2;}
        .institution-sigle{font-size:12px;color:#94a3b8;font-weight:400;}

        .message{padding:12px 18px;border-radius:8px;margin-bottom:20px;}
        .message.success{background:#d1fae5;color:#16a34a;border-left:4px solid #16a34a;}
        .message.error{background:#fee2e2;color:#dc2626;border-left:4px solid #dc2626;}

        .card{background:white;border-radius:0 0 16px 16px;padding:35px 30px;border:1px solid #e2e8f0;border-top:none;box-shadow:0 4px 20px rgba(0,0,0,0.04);}

        .section{margin-bottom:32px;}
        .section:last-of-type{margin-bottom:0;}
        .section h2{font-size:18px;font-weight:700;color:#1e40af;text-align:center;border-bottom:2px solid #e2e8f0;padding-bottom:12px;margin-bottom:20px;display:block;width:100%;position:relative;}
        .section h2 i{color:#3b82f6;margin-right:10px;}

        .form-row{display:flex;gap:20px;flex-wrap:wrap;margin-bottom:15px;}
        .form-row.center-row{justify-content:center;}
        .form-group{flex:1;min-width:200px;}
        .form-group.full-width{flex:1 1 100%;}
        .form-group.centered-group{max-width:600px;margin:0 auto;text-align:center;}

        .form-group label{display:block;font-weight:600;color:#475569;margin-bottom:5px;font-size:14px;text-align:center;}
        .form-group label .required{color:#ef4444;}

        /* === CENTRAGE DU CHAMP INSTITUTION === */
        .select-wrapper {
            position: relative;
            max-width: 500px;
            margin: 0 auto;
        }
        .select-wrapper select {
            width: 100%;
            padding: 10px 40px 10px 40px;
            text-align: center;
            text-align-last: center;
            border: 1px solid #cbd5e1;
            border-radius: 8px;
            font-size: 14px;
            transition: 0.2s;
            background: white;
            appearance: none;
            -webkit-appearance: none;
            -moz-appearance: none;
            cursor: pointer;
        }
        .select-wrapper select:focus {
            outline: none;
            border-color: #1e40af;
            box-shadow: 0 0 0 3px rgba(30,64,175,0.1);
        }
        .select-wrapper .select-arrow {
            position: absolute;
            right: 16px;
            top: 50%;
            transform: translateY(-50%);
            color: #94a3b8;
            pointer-events: none;
            font-size: 12px;
        }
        .select-wrapper select:focus + .select-arrow {
            color: #1e40af;
        }

        .filter-hint{font-size:12px;color:#94a3b8;margin-top:8px;text-align:center;}

        .phone-wrapper{display:flex;align-items:center;gap:4px;width:100%;}
        .phone-wrapper .phone-prefix{background:#f1f5f9;padding:10px 12px;border:1px solid #cbd5e1;border-radius:8px 0 0 8px;font-weight:600;color:#475569;font-size:14px;white-space:nowrap;}
        .phone-wrapper input{border-radius:0 8px 8px 0;flex:1;}

        .ninu-wrapper input{width:100%;padding:10px 14px;border:1px solid #cbd5e1;border-radius:8px;font-size:14px;transition:0.2s;background:white;font-family:monospace;letter-spacing:1px;}
        .ninu-wrapper input:focus{outline:none;border-color:#1e40af;box-shadow:0 0 0 3px rgba(30,64,175,0.1);}

        .field-hint{font-size:11px;color:#94a3b8;display:block;margin-top:4px;}

        .form-group input,.form-group select,.form-group textarea{width:100%;padding:10px 14px;border:1px solid #cbd5e1;border-radius:8px;font-size:14px;transition:0.2s;background:white;}
        .form-group input:focus,.form-group select:focus,.form-group textarea:focus{outline:none;border-color:#1e40af;box-shadow:0 0 0 3px rgba(30,64,175,0.1);}
        .form-group input[readonly]{background:#f1f5f9;color:#64748b;cursor:not-allowed;}
        .form-group textarea{resize:vertical;min-height:80px;}

        .file-upload{display:flex;flex-direction:column;align-items:center;gap:12px;padding:20px;border:2px dashed #cbd5e1;border-radius:12px;background:#fafbfc;transition:0.2s;}
        .file-upload:hover{border-color:#1e40af;background:#f0f4ff;}
        .file-upload input[type="file"]{display:none;}
        .photo-upload-btn {
            display:inline-block;
            padding:10px 20px;
            background:#1e40af;
            color:white;
            border-radius:8px;
            cursor:pointer;
            font-weight:600;
            transition:0.2s;
            margin-bottom:10px;
        }
        .photo-upload-btn:hover{background:#1e3a8a;transform:translateY(-2px);box-shadow:0 4px 12px rgba(30,64,175,0.3);}
        .photo-upload-btn i{margin-right:8px;}
        .photo-preview{width:120px;height:120px;border-radius:50%;background:#f1f5f9;display:flex;flex-direction:column;align-items:center;justify-content:center;color:#94a3b8;font-size:14px;overflow:hidden;border:3px solid #e2e8f0;transition:0.3s;}
        .photo-preview:hover{border-color:#3b82f6;}
        .photo-preview i{font-size:64px;color:#94a3b8;}
        .photo-preview img{width:100%;height:100%;object-fit:cover;}
        .photo-preview p{margin-top:4px;font-size:12px;}
        .file-hint{font-size:12px;color:#94a3b8;display:block;margin-top:6px;text-align:center;}

        .btn-submit{background:linear-gradient(135deg,#16a34a,#15803d);color:white;border:none;padding:14px 40px;border-radius:10px;font-weight:700;font-size:16px;cursor:pointer;transition:0.3s;display:inline-flex;align-items:center;gap:12px;margin-top:25px;width:100%;justify-content:center;box-shadow:0 4px 15px rgba(22,163,74,0.3);}
        .btn-submit:hover{background:linear-gradient(135deg,#15803d,#166534);transform:translateY(-2px);box-shadow:0 8px 25px rgba(22,163,74,0.4);}
        .btn-submit:active{transform:translateY(0);}

        .footer{margin-top:30px;text-align:center;color:#94a3b8;font-size:13px;}

        @media (max-width:768px){.form-row{flex-direction:column;}.form-group{min-width:100%;}.card{padding:20px 15px;}.header{padding:20px 15px;}.header h1{font-size:22px;}.institution-banner{flex-wrap:wrap;justify-content:center;text-align:center;}.institution-info{align-items:center;text-align:center;}.select-wrapper{max-width:100%;}.form-group.centered-group{max-width:100%;}.phone-wrapper{flex-wrap:wrap;}.phone-wrapper .phone-prefix{border-radius:8px;width:100%;text-align:center;}.phone-wrapper input{border-radius:8px;}}
        """;
    }

    private static String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }
}