import java.text.SimpleDateFormat;
import java.util.List;

public class UIFormulaireProfesseur {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    private static final String[] JOURS_SEMAINE = {
        "Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi", "Dimanche"
    };

    private static final String[] PERIODES = { "Trimestre 1", "Trimestre 2", "Trimestre 3",
        "1ere Periode", "2eme Periode", "3eme Periode", "4eme Periode",
        "Annuelle"
    };

    public static String rendreFormulaire(Professeur professeur, String institutionId,
                                          List<Professeur.Creneau> creneaux, List<Matiere> matieres) {
        if (institutionId == null || institutionId.isBlank()) {
            institutionId = "ADMIN12345";
        }
        if (creneaux == null) creneaux = List.of();
        if (matieres == null) matieres = List.of();

        boolean estModification = (professeur != null);
        String titre = estModification ? "Modifier le professeur" : "Ajouter un professeur";
        String action = estModification ? "update" : "add";

        // Valeurs du professeur (null-safe)
        String numId = safe(professeur != null ? professeur.getNumeroIdentifiantProfesseur() : null);
        String nom = safe(professeur != null ? professeur.getNom() : null);
        String prenom = safe(professeur != null ? professeur.getPrenom() : null);
        String sexe = safe(professeur != null ? professeur.getSexe() : null);
        String dateNaissance = (professeur != null && professeur.getDateNaissance() != null)
                ? DATE_FORMAT.format(professeur.getDateNaissance()) : "";
        String groupeSanguin = safe(professeur != null ? professeur.getGroupeSanguin() : null);
        String departementNaissance = safe(professeur != null ? professeur.getDepartementNaissance() : null);
        String communeNaissance = safe(professeur != null ? professeur.getCommuneNaissance() : null);
        String telephone = safe(professeur != null ? professeur.getTelephone() : null);
        String email = safe(professeur != null ? professeur.getEmail() : null);
        String adresse = safe(professeur != null ? professeur.getAdresse() : null);
        String paysHabitation = safe(professeur != null ? professeur.getPaysHabitation() : null);
        String departementHabitation = safe(professeur != null ? professeur.getDepartementHabitation() : null);
        String communeHabitation = safe(professeur != null ? professeur.getCommuneHabitation() : null);
        String matricule = safe(professeur != null ? professeur.getMatricule() : null);
        String ninu = safe(professeur != null ? professeur.getNinu() : null);
        String diplome = safe(professeur != null ? professeur.getDiplome() : null);
        String specialite = safe(professeur != null ? professeur.getSpecialite() : null);
        String dateEmbauche = (professeur != null && professeur.getDateEmbauche() != null)
                ? DATE_FORMAT.format(professeur.getDateEmbauche()) : "";
        String salaire = (professeur != null) ? String.valueOf(professeur.getSalaire()) : "0.00";
        String statut = safe(professeur != null ? professeur.getStatut() : null);
        if (statut.isEmpty()) statut = "ACTIF";
        String observations = safe(professeur != null ? professeur.getObservations() : null);
        String situationMatrimoniale = safe(professeur != null ? professeur.getSituationMatrimoniale() : null);
        boolean biometrieActive = professeur != null && professeur.isBiometrieActive();

        String photoPath = safe(professeur != null ? professeur.getPhotoPath() : null);
        String photoPreviewSrc = buildPhotoPreviewSrc(photoPath);

        String empreintePath = safe(professeur != null ? professeur.getEmpreintePath() : null);
        String visagePath = safe(professeur != null ? professeur.getVisagePath() : null);

        StringBuilder sb = new StringBuilder();

        // ============================================================
        // STYLE
        // ============================================================
        sb.append("""
        <style>
            .modal-overlay {
                display: none;
                position: fixed;
                inset: 0;
                background: rgba(15, 23, 42, 0.6);
                backdrop-filter: blur(4px);
                z-index: 1050;
                justify-content: center;
                align-items: center;
                overflow-y: auto;
                padding: 20px;
            }
            .modal-overlay.active { display: flex; }
            .modal-dialog-custom {
                background: #ffffff;
                border-radius: 16px;
                padding: 30px 35px;
                max-width: 1200px;
                width: 100%;
                max-height: 92vh;
                overflow-y: auto;
                box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.15), 0 10px 10px -5px rgba(0, 0, 0, 0.06);
                margin: auto;
            }
            .modal-dialog-custom h2 {
                color: #1e293b;
                font-weight: 700;
                font-size: 1.35rem;
                border-bottom: 2px solid #e2e8f0;
                padding-bottom: 12px;
                margin-bottom: 20px;
                text-align: center;
            }
            .modal-dialog-custom h3 {
                color: #4f46e5;
                font-weight: 600;
                font-size: 1rem;
                margin-top: 22px;
                margin-bottom: 12px;
                display: flex;
                align-items: center;
                gap: 8px;
                border-left: 3px solid #4f46e5;
                padding-left: 10px;
            }
            .form-row-custom {
                display: grid;
                grid-template-columns: 1fr 1fr;
                gap: 16px;
                margin-bottom: 12px;
            }
            .form-group-custom {
                display: flex;
                flex-direction: column;
                gap: 6px;
            }
            .form-group-custom label {
                font-size: 0.85rem;
                font-weight: 600;
                color: #475569;
            }
            .form-group-custom input,
            .form-group-custom select,
            .form-group-custom textarea {
                padding: 10px 14px;
                border: 1px solid #cbd5e1;
                border-radius: 10px;
                font-size: 0.9rem;
                font-family: 'Inter', sans-serif;
                transition: all 0.2s ease;
                background-color: #f8fafc;
                width: 100%;
                box-sizing: border-box;
            }
            .form-group-custom input:focus,
            .form-group-custom select:focus,
            .form-group-custom textarea:focus {
                outline: none;
                border-color: #4f46e5;
                background-color: #ffffff;
                box-shadow: 0 0 0 3px rgba(79, 70, 229, 0.15);
            }
            .creneau-card {
                display: grid;
                grid-template-columns: 2fr 1fr 1fr 0.8fr 0.8fr 1fr 1.2fr 0.6fr 0.3fr;
                gap: 8px;
                align-items: center;
                padding: 12px;
                background: #f8fafc;
                border: 1px solid #e2e8f0;
                border-radius: 12px;
                margin-bottom: 8px;
            }
            @media (max-width: 1100px) {
                .creneau-card {
                    grid-template-columns: 1fr 1fr;
                }
            }
            .creneau-card select,
            .creneau-card input {
                padding: 8px 10px;
                border: 1px solid #cbd5e1;
                border-radius: 8px;
                font-size: 0.85rem;
                background: #fff;
                width: 100%;
                box-sizing: border-box;
            }
            .creneau-card .btn-danger {
                padding: 6px 12px;
                font-size: 0.85rem;
                border-radius: 8px;
            }
            .photo-upload-container {
                display: flex;
                align-items: center;
                gap: 20px;
                background: #f8fafc;
                border: 1px dashed #cbd5e1;
                padding: 15px;
                border-radius: 12px;
            }
            .photo-preview-box {
                width: 90px;
                height: 90px;
                border-radius: 50%;
                background: #e2e8f0;
                display: flex;
                align-items: center;
                justify-content: center;
                overflow: hidden;
                border: 2px solid #cbd5e1;
                flex-shrink: 0;
            }
            .photo-preview-box img {
                width: 100%;
                height: 100%;
                object-fit: cover;
            }
            .photo-preview-box i {
                font-size: 40px;
                color: #94a3b8;
            }
            .modal-footer-custom {
                display: flex;
                justify-content: center;
                gap: 12px;
                margin-top: 25px;
                padding-top: 20px;
                border-top: 1px solid #e2e8f0;
            }
            .modal-footer-custom .btn {
                padding: 10px 30px;
                font-weight: 600;
                font-size: 0.9rem;
                border-radius: 10px;
            }
            @media (max-width: 768px) {
                .form-row-custom { grid-template-columns: 1fr; }
                .modal-dialog-custom { padding: 20px; }
                .modal-footer-custom { flex-direction: column; }
                .modal-footer-custom .btn { width: 100%; }
            }
        </style>
        """);

        // ============================================================
        // STRUCTURE MODALE
        // ============================================================
        sb.append("<div id=\"modalForm\" class=\"modal-overlay\">\n")
          .append("<div class=\"modal-dialog-custom\">\n")
          .append("<h2 id=\"modalTitle\">").append(titre).append("</h2>\n")
          .append("<form method=\"POST\" action=\"/admin/professeurs\" id=\"modalFormInner\">\n");

        // Champs cachés
        sb.append("<input type=\"hidden\" name=\"action\" id=\"formAction\" value=\"").append(echapperHtml(action)).append("\">\n")
          .append("<input type=\"hidden\" name=\"institutionId\" value=\"").append(echapperHtml(institutionId)).append("\">\n")
          .append("<input type=\"hidden\" name=\"photoPathActuelle\" id=\"photoPathActuelle\" value=\"").append(echapperHtml(photoPath)).append("\">\n")
          .append("<input type=\"hidden\" name=\"photoBase64\" id=\"photoBase64\">\n")
          .append("<input type=\"hidden\" name=\"empreintePath\" id=\"empreintePath\" value=\"").append(echapperHtml(empreintePath)).append("\">\n")
          .append("<input type=\"hidden\" name=\"visagePath\" id=\"visagePath\" value=\"").append(echapperHtml(visagePath)).append("\">\n");

        // ===== Identité & Photo =====
        sb.append("<h3><i class=\"fa-solid fa-user-shield\"></i> Identité & Photo</h3>\n")
          .append("<div class=\"photo-upload-container mb-3\">\n")
          .append("    <div class=\"photo-preview-box\">\n");

        if (!photoPreviewSrc.isEmpty()) {
            sb.append("        <img id=\"imgPreview\" src=\"").append(photoPreviewSrc).append("\" alt=\"Aperçu\" ")
              .append("onerror=\"this.style.display='none';document.getElementById('defaultPhotoIcon').style.display='block';\">\n")
              .append("        <i id=\"defaultPhotoIcon\" class=\"fa-solid fa-user\" style=\"display:none;\"></i>\n");
        } else {
            sb.append("        <img id=\"imgPreview\" src=\"\" alt=\"Aperçu\" style=\"display:none;\">\n")
              .append("        <i id=\"defaultPhotoIcon\" class=\"fa-solid fa-user\"></i>\n");
        }

        sb.append("    </div>\n")
          .append("    <div style=\"flex:1;\">\n")
          .append("        <label class=\"form-label fw-bold text-secondary mb-1\" style=\"font-size:0.8rem;\">Photo du professeur</label>\n")
          .append("        <input type=\"file\" class=\"form-control form-control-sm\" id=\"inputPhotoFile\" accept=\"image/png, image/jpeg, image/gif, image/webp\">\n")
          .append("        <div class=\"form-text text-muted mt-1\" style=\"font-size:0.75rem;\">Formats : JPG, PNG, GIF, WEBP (max 5 Mo)</div>\n")
          .append("    </div>\n")
          .append("</div>\n");

        sb.append("<div class=\"form-row-custom\">\n")
          .append("    <div class=\"form-group-custom\"><label>Identifiant (Généré)</label>\n")
          .append("        <input type=\"text\" name=\"numeroIdentifiantProfesseur\" id=\"numId\" value=\"").append(echapperHtml(numId)).append("\" readonly style=\"background:#e2e8f0; color:#64748b; font-weight:600;\">\n")
          .append("    </div>\n")
          .append("    <div class=\"form-group-custom\"><label>Nom *</label><input type=\"text\" name=\"nom\" id=\"nom\" value=\"").append(echapperHtml(nom)).append("\" required></div>\n")
          .append("</div>\n")
          .append("<div class=\"form-row-custom\">\n")
          .append("    <div class=\"form-group-custom\"><label>Prénom</label><input type=\"text\" name=\"prenom\" id=\"prenom\" value=\"").append(echapperHtml(prenom)).append("\"></div>\n")
          .append("    <div class=\"form-group-custom\"><label>Sexe</label><select name=\"sexe\" id=\"sexe\">").append(genererOptions(Constantes.SEXES, sexe)).append("</select></div>\n")
          .append("</div>\n")
          .append("<div class=\"form-row-custom\">\n")
          .append("    <div class=\"form-group-custom\"><label>Date de naissance</label><input type=\"date\" name=\"dateNaissance\" id=\"dateNaissance\" value=\"").append(echapperHtml(dateNaissance)).append("\"></div>\n")
          .append("    <div class=\"form-group-custom\"><label>Groupe sanguin</label><select name=\"groupeSanguin\" id=\"groupeSanguin\">").append(genererOptions(Constantes.GROUPES_SANGUINS, groupeSanguin)).append("</select></div>\n")
          .append("</div>\n")
          .append("<div class=\"form-row-custom\">\n")
          .append("    <div class=\"form-group-custom\"><label>Département naissance</label>").append(genererSelectDepartement("departementNaissance", departementNaissance, "communeNaissance")).append("</div>\n")
          .append("    <div class=\"form-group-custom\"><label>Commune naissance</label>").append(genererSelectCommune("communeNaissance", communeNaissance, departementNaissance)).append("</div>\n")
          .append("</div>\n");

        // ===== Contact & Adresse =====
        sb.append("<h3><i class=\"fa-solid fa-address-book\"></i> Contact & Adresse</h3>\n")
          .append("<div class=\"form-row-custom\">\n")
          .append("    <div class=\"form-group-custom\"><label>Téléphone</label><input type=\"text\" name=\"telephone\" id=\"telephone\" value=\"").append(echapperHtml(telephone)).append("\"></div>\n")
          .append("    <div class=\"form-group-custom\"><label>Email</label><input type=\"email\" name=\"email\" id=\"email\" value=\"").append(echapperHtml(email)).append("\"></div>\n")
          .append("</div>\n")
          .append("<div class=\"form-group-custom mb-3\"><label>Adresse</label><textarea name=\"adresse\" id=\"adresse\" rows=\"2\">").append(echapperHtml(adresse)).append("</textarea></div>\n")
          .append("<div class=\"form-row-custom\">\n")
          .append("    <div class=\"form-group-custom\"><label>Pays habitation</label><input type=\"text\" name=\"paysHabitation\" id=\"paysHabitation\" value=\"").append(echapperHtml(paysHabitation)).append("\"></div>\n")
          .append("    <div class=\"form-group-custom\"><label>Situation matrimoniale</label><select name=\"situationMatrimoniale\" id=\"situationMatrimoniale\">").append(genererOptions(Constantes.SITUATIONS_MATRIMONIALES, situationMatrimoniale)).append("</select></div>\n")
          .append("</div>\n")
          .append("<div class=\"form-row-custom\">\n")
          .append("    <div class=\"form-group-custom\"><label>Département habitation</label>").append(genererSelectDepartement("departementHabitation", departementHabitation, "communeHabitation")).append("</div>\n")
          .append("    <div class=\"form-group-custom\"><label>Commune habitation</label>").append(genererSelectCommune("communeHabitation", communeHabitation, departementHabitation)).append("</div>\n")
          .append("</div>\n");

        // ===== Professionnel =====
        sb.append("<h3><i class=\"fa-solid fa-briefcase\"></i> Informations Professionnelles</h3>\n")
          .append("<div class=\"form-row-custom\">\n")
          .append("    <div class=\"form-group-custom\"><label>Matricule</label><input type=\"text\" name=\"matricule\" id=\"matricule\" value=\"").append(echapperHtml(matricule)).append("\"></div>\n")
          .append("    <div class=\"form-group-custom\"><label>NINU</label><input type=\"text\" name=\"ninu\" id=\"ninu\" value=\"").append(echapperHtml(ninu)).append("\"></div>\n")
          .append("</div>\n")
          .append("<div class=\"form-row-custom\">\n")
          .append("    <div class=\"form-group-custom\"><label>Diplôme</label><input type=\"text\" name=\"diplome\" id=\"diplome\" value=\"").append(echapperHtml(diplome)).append("\"></div>\n")
          .append("    <div class=\"form-group-custom\"><label>Spécialité</label><input type=\"text\" name=\"specialite\" id=\"specialite\" value=\"").append(echapperHtml(specialite)).append("\"></div>\n")
          .append("</div>\n")
          .append("<div class=\"form-row-custom\">\n")
          .append("    <div class=\"form-group-custom\"><label>Date d'embauche</label><input type=\"date\" name=\"dateEmbauche\" id=\"dateEmbauche\" value=\"").append(echapperHtml(dateEmbauche)).append("\"></div>\n")
          .append("    <div class=\"form-group-custom\"><label>Salaire</label><input type=\"number\" step=\"0.01\" name=\"salaire\" id=\"salaire\" value=\"").append(echapperHtml(salaire)).append("\"></div>\n")
          .append("</div>\n")
          .append("<div class=\"form-row-custom\">\n")
          .append("    <div class=\"form-group-custom\"><label>Statut</label><select name=\"statut\" id=\"statut\">\n")
          .append("        <option value=\"ACTIF\" ").append("ACTIF".equals(statut) ? "selected" : "").append(">ACTIF</option>\n")
          .append("        <option value=\"INACTIF\" ").append("INACTIF".equals(statut) ? "selected" : "").append(">INACTIF</option>\n")
          .append("    </select></div>\n")
          .append("    <div class=\"form-group-custom\"><label>Biométrie active</label><select name=\"biometrieActive\" id=\"biometrieActive\">\n")
          .append("        <option value=\"true\" ").append(biometrieActive ? "selected" : "").append(">Oui</option>\n")
          .append("        <option value=\"false\" ").append(!biometrieActive ? "selected" : "").append(">Non</option>\n")
          .append("    </select></div>\n")
          .append("</div>\n");

        // ===== Observations =====
        sb.append("<h3><i class=\"fa-solid fa-comment-dots\"></i> Observations</h3>\n")
          .append("<div class=\"form-group-custom mb-3\"><textarea name=\"observations\" id=\"observations\" rows=\"2\">").append(echapperHtml(observations)).append("</textarea></div>\n");

        // ===== Créneaux =====
        sb.append("<h3><i class=\"fa-solid fa-calendar-days\"></i> Créneaux horaires & Affectations</h3>\n")
          .append("<div id=\"creneauxContainer\"></div>\n")
          .append("<button type=\"button\" class=\"btn btn-primary btn-sm mt-2\" onclick=\"ajouterCreneau('','','','','','','','','')\">")
          .append("<i class=\"fa-solid fa-plus me-1\"></i> Ajouter un créneau</button>\n");

        // ===== Boutons centrés =====
        sb.append("<div class=\"modal-footer-custom\">\n")
          .append("    <button type=\"button\" class=\"btn btn-secondary\" onclick=\"fermerModal()\">")
          .append("<i class=\"fa-solid fa-xmark me-1\"></i> Annuler</button>\n")
          .append("    <button type=\"submit\" class=\"btn btn-success\">")
          .append("<i class=\"fa-solid fa-floppy-disk me-1\"></i> Enregistrer</button>\n")
          .append("</div>\n");

        sb.append("</form>\n</div>\n</div>\n");

        // ============================================================
        // JAVASCRIPT
        // ============================================================
        sb.append("<script>\n");
        sb.append("window.matieresDisponibles = ").append(toJson(matieres)).append(";\n");
        sb.append("window.joursSemaine = ").append(toJson(JOURS_SEMAINE)).append(";\n");
        sb.append("window.periodesDisponibles = ").append(toJson(PERIODES)).append(";\n");
        sb.append("window.creneauxInitiaux = ").append(toJson(creneaux)).append(";\n");
        sb.append("window.anneeParDefaut = \"").append(echapperJson(getAnneeParDefaut())).append("\";\n");
        sb.append("window.periodeParDefaut = \"").append(echapperJson(getPeriodeParDefaut())).append("\";\n");

        // ✅ GÉNÉRATION DES COMMUNES PAR DÉPARTEMENT
        sb.append("window.communesParDepartement = {");
        String[][] deptCommunes = Constantes.getDepartementsCommunes();
        if (deptCommunes != null) {
            for (int i = 0; i < deptCommunes.length; i++) {
                String[] ligne = deptCommunes[i];
                if (ligne == null || ligne.length == 0) continue;
                String departement = ligne[0];
                sb.append("\"").append(echapperJson(departement)).append("\": [");
                for (int j = 1; j < ligne.length; j++) {
                    sb.append("\"").append(echapperJson(ligne[j])).append("\"");
                    if (j < ligne.length - 1) sb.append(", ");
                }
                sb.append("]");
                if (i < deptCommunes.length - 1) sb.append(", ");
            }
        }
        sb.append("};\n");

        sb.append("""
        // ============================================================
        // CHARGEMENT DYNAMIQUE DES COMMUNES
        // ============================================================
        function chargerCommunes(departement, selectId) {
            var select = document.getElementById(selectId);
            if (!select) {
                console.warn('⚠️ Select introuvable :', selectId);
                return;
            }

            var currentValue = select.value;
            select.innerHTML = '<option value="">Sélectionner</option>';

            var communes = (window.communesParDepartement && window.communesParDepartement[departement]) || [];
            console.log('📋 Communes pour', departement, ':', communes.length);

            communes.forEach(function(commune) {
                var opt = document.createElement('option');
                opt.value = commune;
                opt.textContent = commune;
                select.appendChild(opt);
            });

            if (currentValue) {
                for (var i = 0; i < select.options.length; i++) {
                    if (select.options[i].value === currentValue) {
                        select.value = currentValue;
                        break;
                    }
                }
            }
        }

        // ============================================================
        // GÉNÉRATION DES OPTIONS
        // ============================================================
        function genererOptionsCours(selectedCode) {
            var html = '<option value="">Sélectionner une matière</option>';
            if (window.matieresDisponibles && window.matieresDisponibles.length > 0) {
                window.matieresDisponibles.forEach(function(m) {
                    var sel = (m.codeCours === selectedCode) ? 'selected' : '';
                    html += '<option value="' + m.codeCours + '" ' + sel + ' data-matiere="' + (m.nomMatiere || '') + '" data-classe="' + (m.classe || '') + '">' + m.codeCours + ' - ' + (m.nomMatiere || '') + (m.classe ? ' (' + m.classe + ')' : '') + '</option>';
                });
            }
            return html;
        }

        function genererOptionsJours(selectedJour) {
            var html = '<option value="">Jour</option>';
            if (window.joursSemaine && window.joursSemaine.length > 0) {
                window.joursSemaine.forEach(function(j) {
                    var sel = (j === selectedJour) ? 'selected' : '';
                    html += '<option value="' + j + '" ' + sel + '>' + j + '</option>';
                });
            }
            return html;
        }

        function genererOptionsPeriodes(selectedPeriode) {
            var html = '<option value="">Période</option>';
            if (window.periodesDisponibles && window.periodesDisponibles.length > 0) {
                window.periodesDisponibles.forEach(function(p) {
                    var sel = (p === selectedPeriode) ? 'selected' : '';
                    html += '<option value="' + p + '" ' + sel + '>' + p + '</option>';
                });
            }
            return html;
        }

        // ============================================================
        // GESTION DES CRÉNEAUX
        // ============================================================
        var creneauIndex = 0;

        function ajouterCreneau(code, matiere, classe, jour, debut, fin, annee, periode, coefficient) {
            var container = document.getElementById('creneauxContainer');
            if (!container) return;

            var div = document.createElement('div');
            div.className = 'creneau-card';
            var idx = creneauIndex++;

            var selectCoursHtml = genererOptionsCours(code || '');
            var selectJourHtml = genererOptionsJours(jour || '');
            var selectPeriodeHtml = genererOptionsPeriodes(periode || '');

            var anneeValue = annee || window.anneeParDefaut || '';
            var coeffValue = (coefficient != null && coefficient !== '') ? coefficient : 1.0;

            div.innerHTML = `
                <select name="creneau_code_${idx}" onchange="onMatiereChange(this, 'creneau_matiere_${idx}', 'creneau_classe_${idx}')">
                    ${selectCoursHtml}
                </select>
                <input type="hidden" name="creneau_matiere_${idx}" id="creneau_matiere_${idx}" value="${matiere || ''}">
                <input type="text" name="creneau_classe_${idx}" id="creneau_classe_${idx}" placeholder="Classe" value="${classe || ''}" readonly style="background:#f1f5f9;">
                <select name="creneau_jour_${idx}">
                    ${selectJourHtml}
                </select>
                <input type="time" name="creneau_debut_${idx}" value="${debut || ''}">
                <input type="time" name="creneau_fin_${idx}" value="${fin || ''}">
                <input type="text" name="creneau_annee_${idx}" placeholder="Année (ex: 2026-2027)" value="${anneeValue}">
                <select name="creneau_periode_${idx}">
                    ${selectPeriodeHtml}
                </select>
                <input type="number" step="0.01" name="creneau_coefficient_${idx}" placeholder="Coef" value="${coeffValue}">
                <button type="button" class="btn btn-danger btn-sm" onclick="supprimerCreneau(this)"><i class="fa-solid fa-xmark"></i></button>
            `;
            container.appendChild(div);
        }

        function onMatiereChange(selectElement, matiereInputId, classeInputId) {
            var matiereInput = document.getElementById(matiereInputId);
            var classeInput = document.getElementById(classeInputId);
            var selectedOption = selectElement.options[selectElement.selectedIndex];
            if (matiereInput) matiereInput.value = selectedOption ? (selectedOption.getAttribute('data-matiere') || '') : '';
            if (classeInput) classeInput.value = selectedOption ? (selectedOption.getAttribute('data-classe') || '') : '';
        }

        function supprimerCreneau(btn) {
            var card = btn.closest('.creneau-card');
            if (card) card.remove();
        }

        // ============================================================
        // FERMETURE DE LA MODALE
        // ============================================================
        function fermerModal() {
            var modal = document.getElementById('modalForm');
            if (!modal) return;
            modal.classList.remove('active');
            modal.style.display = 'none';

            var form = document.getElementById('modalFormInner');
            if (form) form.reset();

            var container = document.getElementById('creneauxContainer');
            if (container) container.innerHTML = '';

            creneauIndex = 0;
        }

        // ============================================================
        // INITIALISATION DES CRÉNEAUX
        // ============================================================
        function initialiserCreneaux() {
            var container = document.getElementById('creneauxContainer');
            if (!container) return;
            container.innerHTML = '';
            creneauIndex = 0;

            var init = window.creneauxInitiaux || [];
            console.log('📅 Initialisation créneaux :', init.length);

            if (init.length > 0) {
                init.forEach(function(c) {
                    ajouterCreneau(
                        c.codeCours || '',
                        c.nomMatiere || '',
                        c.classe || '',
                        c.jourSemaine || '',
                        c.heureDebut || '',
                        c.heureFin || '',
                        c.anneeAcademique || '',
                        c.periode || '',
                        c.coefficient || 1.0
                    );
                });
            } else {
                ajouterCreneau('', '', '', '', '', '', '', '', '');
            }
        }

        window.initialiserCreneaux = initialiserCreneaux;

        // ============================================================
        // INITIALISATION AU CHARGEMENT
        // ============================================================
        document.addEventListener('DOMContentLoaded', function() {
            console.log('🚀 UIFormulaireProfesseur - DOMContentLoaded');

            // --- Gestion de la photo ---
            var fileInput = document.getElementById('inputPhotoFile');
            if (fileInput) {
                fileInput.addEventListener('change', function(event) {
                    var file = event.target.files[0];
                    if (!file) return;
                    if (file.size > 5 * 1024 * 1024) {
                        alert("L'image est trop volumineuse (max 5 Mo).");
                        this.value = "";
                        return;
                    }
                    var reader = new FileReader();
                    reader.onload = function(e) {
                        var base64 = e.target.result;
                        var hidden = document.getElementById('photoBase64');
                        if (hidden) hidden.value = base64;
                        var imgPreview = document.getElementById('imgPreview');
                        var iconDefault = document.getElementById('defaultPhotoIcon');
                        if (imgPreview) { imgPreview.src = base64; imgPreview.style.display = 'block'; }
                        if (iconDefault) iconDefault.style.display = 'none';
                    };
                    reader.readAsDataURL(file);
                });
            }

            // --- Initialisation des communes ---
            var depNaiss = document.getElementById('departementNaissance');
            if (depNaiss && depNaiss.value) {
                console.log('📍 Init commune naissance pour :', depNaiss.value);
                chargerCommunes(depNaiss.value, 'communeNaissance');
            }
            var depHab = document.getElementById('departementHabitation');
            if (depHab && depHab.value) {
                console.log('📍 Init commune habitation pour :', depHab.value);
                chargerCommunes(depHab.value, 'communeHabitation');
            }

            // ✅ AUTO-INITIALISATION DES CRÉNEAUX
            // Sans cet appel, les créneaux ne s'affichent jamais en modification.
            initialiserCreneaux();
        });

        // ============================================================
        // FERMETURE AU CLIC SUR L'OVERLAY
        // ============================================================
        document.addEventListener('click', function(event) {
            var modal = document.getElementById('modalForm');
            if (modal && event.target === modal) {
                fermerModal();
            }
        });

        // ============================================================
        // FERMETURE AVEC ÉCHAP
        // ============================================================
        document.addEventListener('keydown', function(event) {
            if (event.key === 'Escape') {
                fermerModal();
            }
        });

        // ============================================================
        // EXPOSITION DE LA FONCTION D'OUVERTURE
        // ============================================================
        window.ouvrirModalProfesseur = function(mode, profData) {
            var modal = document.getElementById('modalForm');
            if (!modal) return;
            modal.classList.add('active');
            modal.style.display = 'flex';

            // ✅ Mettre à jour l'action du formulaire selon le mode
            var formAction = document.getElementById('formAction');
            if (formAction) {
                formAction.value = (mode === 'edit') ? 'update' : 'add';
            }

            // Réinitialiser et réinitialiser les créneaux
            if (mode === 'edit' && profData) {
                // Le formulaire est déjà pré-rempli côté serveur.
                // On réinitialise juste les créneaux avec ceux du professeur.
                initialiserCreneaux();
            } else {
                // Création : vider les créneaux et en ajouter un vide
                var container = document.getElementById('creneauxContainer');
                if (container) container.innerHTML = '';
                creneauIndex = 0;
                ajouterCreneau('', '', '', '', '', '', '', '', '');
            }
        };
        """);
        sb.append("</script>\n");

        return sb.toString();
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private static String getAnneeParDefaut() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        int annee = cal.get(java.util.Calendar.YEAR);
        int mois = cal.get(java.util.Calendar.MONTH); // 0 = janvier
        if (mois >= 7) {
            return annee + "-" + (annee + 1);
        } else {
            return (annee - 1) + "-" + annee;
        }
    }

    private static String getPeriodeParDefaut() {
        return "1ere Periode";
    }

    private static String safe(String s) {
        return s != null ? s : "";
    }

    private static String buildPhotoPreviewSrc(String photoPath) {
        if (photoPath == null || photoPath.isEmpty()) return "";
        if (photoPath.startsWith("/data/")) return photoPath;
        if (photoPath.startsWith("uploads/")) return "/data/" + photoPath;
        return "/data/uploads/photos_professeurs/" + photoPath;
    }

    private static String genererOptions(String[] options, String valeur) {
        StringBuilder sb = new StringBuilder();
        sb.append("<option value=\"\">Sélectionner</option>");
        if (options != null && options.length > 0) {
            String valeurEffective = (valeur != null && !valeur.isBlank()) ? valeur : options[0];
            for (String opt : options) {
                String selected = (opt != null && opt.equals(valeurEffective)) ? " selected" : "";
                sb.append("<option value=\"").append(echapperHtml(opt)).append("\"").append(selected).append(">")
                  .append(echapperHtml(opt)).append("</option>");
            }
        }
        return sb.toString();
    }

    private static String genererSelectDepartement(String id, String valeur, String communeSelectId) {
        StringBuilder sb = new StringBuilder();
        sb.append("<select name=\"").append(id).append("\" id=\"").append(id).append("\"");
        sb.append(" onchange=\"chargerCommunes(this.value, '").append(communeSelectId).append("')\">");
        sb.append("<option value=\"\">Sélectionner</option>");
        if (Constantes.DEPARTEMENTS != null) {
            for (String dep : Constantes.DEPARTEMENTS) {
                String selected = (dep != null && dep.equals(valeur)) ? " selected" : "";
                sb.append("<option value=\"").append(echapperHtml(dep)).append("\"").append(selected).append(">")
                  .append(echapperHtml(dep)).append("</option>");
            }
        }
        sb.append("</select>");
        return sb.toString();
    }

    private static String genererSelectCommune(String id, String valeur, String departement) {
        StringBuilder sb = new StringBuilder();
        sb.append("<select name=\"").append(id).append("\" id=\"").append(id).append("\">");
        sb.append("<option value=\"\">Sélectionner</option>");
        if (departement != null && !departement.isEmpty()) {
            String[] communes = Constantes.getCommunes(departement);
            if (communes != null) {
                for (String commune : communes) {
                    String selected = (commune != null && commune.equals(valeur)) ? " selected" : "";
                    sb.append("<option value=\"").append(echapperHtml(commune)).append("\"").append(selected).append(">")
                      .append(echapperHtml(commune)).append("</option>");
                }
            }
        }
        sb.append("</select>");
        return sb.toString();
    }

    private static String toJson(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof String string) {
            return "\"" + echapperJson(string) + "\"";
        }
        if (obj instanceof String[] arr) {
            if (arr.length == 0) return "[]";
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < arr.length; i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(echapperJson(arr[i])).append("\"");
            }
            sb.append("]");
            return sb.toString();
        }
        if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            if (list.isEmpty()) return "[]";
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(toJson(list.get(i)));
            }
            sb.append("]");
            return sb.toString();
        }
        if (obj instanceof Matiere m) {
            StringBuilder sb = new StringBuilder("{");
            sb.append("\"codeCours\":\"").append(echapperJson(m.getCodeCours())).append("\",");
            sb.append("\"nomMatiere\":\"").append(echapperJson(m.getNomMatiere())).append("\",");
            sb.append("\"classe\":\"").append(echapperJson(m.getClasseMatiere())).append("\"");
            sb.append("}");
            return sb.toString();
        }
        if (obj instanceof Professeur.Creneau c) {
            StringBuilder sb = new StringBuilder("{");
            sb.append("\"codeCours\":\"").append(echapperJson(c.getCodeCours())).append("\",");
            sb.append("\"nomMatiere\":\"").append(echapperJson(c.getNomMatiere())).append("\",");
            sb.append("\"classe\":\"").append(echapperJson(c.getClasse())).append("\",");
            sb.append("\"jourSemaine\":\"").append(echapperJson(c.getJourSemaine())).append("\",");
            sb.append("\"heureDebut\":\"").append(echapperJson(c.getHeureDebut())).append("\",");
            sb.append("\"heureFin\":\"").append(echapperJson(c.getHeureFin())).append("\",");
            sb.append("\"anneeAcademique\":\"").append(echapperJson(c.getAnneeAcademique())).append("\",");
            sb.append("\"periode\":\"").append(echapperJson(c.getPeriode())).append("\",");
            Double coeff = c.getCoefficient();
            sb.append("\"coefficient\":").append(coeff != null ? coeff : 1.0);
            sb.append("}");
            return sb.toString();
        }
        return "null";
    }

    private static String echapperHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }

    private static String echapperJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }
}