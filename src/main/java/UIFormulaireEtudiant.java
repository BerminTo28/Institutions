import java.text.SimpleDateFormat;
import java.util.List;

public class UIFormulaireEtudiant {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    public static String rendreFormulaire(
            Etudiant etudiant,
            String institutionId,
            List<String> anneesDisponibles,
            List<String> periodesDisponibles,
            List<String> classesDisponibles,
            List<String> promotionsDisponibles,
            List<Options> optionsDisponibles,
            String successMessage,
            String errorMessage) {

        // ============================================================
        // INITIALISATION
        // ============================================================
        if (institutionId == null) institutionId = "ADMIN12345";
        if (anneesDisponibles == null) anneesDisponibles = List.of();
        if (periodesDisponibles == null) periodesDisponibles = List.of();
        if (classesDisponibles == null) classesDisponibles = List.of();
        if (promotionsDisponibles == null) promotionsDisponibles = List.of();
        if (optionsDisponibles == null) optionsDisponibles = List.of();
        if (successMessage == null) successMessage = "";
        if (errorMessage == null) errorMessage = "";

        boolean estModification = (etudiant != null);
        String titre = estModification ? "✏️ Modifier l'étudiant" : "➕ Ajouter un étudiant";
        String action = estModification ? "update" : "add";

        // ============================================================
        // VALEURS
        // ============================================================
        String numId = etudiant != null ? safe(etudiant.getNumeroIdentifiantEtudiant()) : "";
        String nom = etudiant != null ? safe(etudiant.getNom()) : "";
        String prenom = etudiant != null ? safe(etudiant.getPrenom()) : "";

        String sexe = (etudiant != null && etudiant.getSexe() != null && !etudiant.getSexe().isBlank())
                ? etudiant.getSexe()
                : (Constantes.SEXES.length > 0 ? Constantes.SEXES[0] : "");

        String groupeSanguin = (etudiant != null && etudiant.getGroupeSanguin() != null
                && !etudiant.getGroupeSanguin().isBlank())
                ? etudiant.getGroupeSanguin()
                : (Constantes.GROUPES_SANGUINS.length > 0 ? Constantes.GROUPES_SANGUINS[0] : "");

        String dateNaissance = (etudiant != null && etudiant.getDateNaissance() != null)
                ? DATE_FORMAT.format(etudiant.getDateNaissance()) : "";
        String telephone = etudiant != null ? safe(etudiant.getTelephone()) : "";
        String email = etudiant != null ? safe(etudiant.getEmail()) : "";
        String adresse = etudiant != null ? safe(etudiant.getAdresse()) : "";
        String departementNaissance = etudiant != null ? safe(etudiant.getDepartementNaissance()) : "";
        String communeNaissance = etudiant != null ? safe(etudiant.getCommuneNaissance()) : "";
        String departementResidence = etudiant != null ? safe(etudiant.getDepartementResidence()) : "";
        String communeResidence = etudiant != null ? safe(etudiant.getCommuneResidence()) : "";
        String classe = etudiant != null ? safe(etudiant.getClasse()) : "";
        String anneeAcademique = etudiant != null ? safe(etudiant.getAnneeAcademique()) : "";
        String periode = etudiant != null ? safe(etudiant.getPeriode()) : "";
        String promotion = etudiant != null ? safe(etudiant.getPromotion()) : "";
        String matricule = etudiant != null ? safe(etudiant.getMatricule()) : "";
        String ninu = etudiant != null ? safe(etudiant.getNinu()) : "";

        String statut = (etudiant != null && etudiant.getStatut() != null && !etudiant.getStatut().isBlank())
                ? etudiant.getStatut() : "ACTIF";

        String dateInscription = (etudiant != null && etudiant.getDateInscription() != null)
                ? DATE_FORMAT.format(etudiant.getDateInscription()) : "";
        String option = etudiant != null ? safe(etudiant.getOption()) : "";
        String nomPere = etudiant != null ? safe(etudiant.getNomPere()) : "";
        String prenomPere = etudiant != null ? safe(etudiant.getPrenomPere()) : "";
        String nomMere = etudiant != null ? safe(etudiant.getNomMere()) : "";
        String prenomMere = etudiant != null ? safe(etudiant.getPrenomMere()) : "";
        String telephoneParents = etudiant != null ? safe(etudiant.getTelephoneParents()) : "";
        String residenceParents = etudiant != null ? safe(etudiant.getResidenceParents()) : "";
        String lienParente = etudiant != null ? safe(etudiant.getLienParente()) : "";
        String typeResponsable = etudiant != null ? safe(etudiant.getTypeResponsable()) : "";
        String nomResponsable = etudiant != null ? safe(etudiant.getNomResponsable()) : "";
        String prenomResponsable = etudiant != null ? safe(etudiant.getPrenomResponsable()) : "";
        String photoPath = etudiant != null ? safe(etudiant.getPhotoPath()) : "";
        String observations = etudiant != null ? safe(etudiant.getObservations()) : "";
        String empreintePath = etudiant != null ? safe(etudiant.getEmpreintePath()) : "";
        String visagePath = etudiant != null ? safe(etudiant.getVisagePath()) : "";
        boolean biometrieActive = etudiant != null && etudiant.isBiometrieActive();

        String ninuDisplay = !ninu.isEmpty() ? ninu : "";
        String telephoneDisplay = !telephone.isEmpty() ? telephone : "";
        String telephoneParentsDisplay = !telephoneParents.isEmpty() ? telephoneParents : "";
        String photoUrl = (!photoPath.isEmpty()) ? "/" + photoPath : "";

        StringBuilder sb = new StringBuilder();

        // ============================================================
        // STYLES
        // ============================================================
        sb.append("""
        <style>
            @import url('https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap');
            :root {
                --primary: #3b82f6;
                --primary-dark: #2563eb;
                --primary-light: #dbeafe;
                --primary-gradient: linear-gradient(135deg, #3b82f6 0%, #1d4ed8 100%);
                --success: #22c55e;
                --danger: #ef4444;
                --gray-50: #f8fafc;
                --gray-100: #f1f5f9;
                --gray-200: #e2e8f0;
                --gray-300: #cbd5e1;
                --gray-400: #94a3b8;
                --gray-500: #64748b;
                --gray-600: #475569;
                --gray-700: #334155;
                --gray-800: #1e293b;
                --gray-900: #0f172a;
                --radius: 12px;
                --shadow: 0 4px 6px -1px rgba(0,0,0,0.05), 0 2px 4px -1px rgba(0,0,0,0.03);
                --shadow-lg: 0 10px 15px -3px rgba(0,0,0,0.08), 0 4px 6px -2px rgba(0,0,0,0.03);
            }
            * { box-sizing: border-box; }
            body { font-family: 'Inter', sans-serif; background: var(--gray-50); margin: 0; padding: 20px; }
            .modal-form-wrapper { max-width: 1200px; margin: 0 auto; background: white; border-radius: var(--radius); box-shadow: var(--shadow-lg); overflow: hidden; }
            .modal-header { background: var(--primary-gradient); color: white; padding: 20px 30px; display: flex; justify-content: space-between; align-items: center; }
            .modal-header h2 { margin: 0; font-weight: 600; font-size: 1.4rem; display: flex; align-items: center; gap: 12px; }
            .btn-close-modal { background: rgba(255,255,255,0.2); border: none; color: white; width: 36px; height: 36px; border-radius: 50%; font-size: 1.2rem; cursor: pointer; transition: 0.2s; display: flex; align-items: center; justify-content: center; }
            .btn-close-modal:hover { background: rgba(255,255,255,0.3); transform: rotate(90deg); }
            .modal-body { padding: 30px; }
            .alert { padding: 12px 18px; border-radius: 8px; margin-bottom: 20px; font-size: 0.9rem; border-left: 4px solid transparent; display: flex; align-items: center; gap: 10px; }
            .alert-success { background: #dcfce7; color: #166534; border-color: #22c55e; }
            .alert-danger { background: #fee2e2; color: #991b1b; border-color: #ef4444; }
            .form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 24px; }
            @media (max-width: 992px) { .form-grid { grid-template-columns: 1fr; } }
            .section-card { background: var(--gray-50); border-radius: var(--radius); padding: 20px; border: 1px solid var(--gray-200); transition: 0.2s; }
            .section-card:hover { border-color: var(--primary); box-shadow: 0 2px 8px rgba(59,130,246,0.08); }
            .section-card.full-width { grid-column: 1 / -1; }
            .section-title { font-size: 0.85rem; font-weight: 600; color: var(--gray-700); text-transform: uppercase; letter-spacing: 0.5px; margin-bottom: 16px; display: flex; align-items: center; justify-content: center; gap: 10px; border-bottom: 2px solid var(--gray-200); padding-bottom: 10px; }
            .section-title i { color: var(--primary); font-size: 1.1rem; }
            .field-row { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 14px; }
            .field-row.single { grid-template-columns: 1fr; }
            .field-row.three { grid-template-columns: 1fr 1fr 1fr; }
            @media (max-width: 768px) { .field-row { grid-template-columns: 1fr; } .field-row.three { grid-template-columns: 1fr; } }
            .field-group { display: flex; flex-direction: column; align-items: center; }
            .field-group label { font-size: 0.8rem; font-weight: 500; color: var(--gray-600); margin-bottom: 4px; display: flex; align-items: center; gap: 4px; }
            .field-group label .required { color: var(--danger); font-weight: 600; }
            .field-group input, .field-group select, .field-group textarea {
                padding: 8px 12px; border: 1px solid var(--gray-300); border-radius: 8px;
                font-size: 0.9rem; font-family: inherit; transition: 0.2s;
                background: white; width: 100%; max-width: 400px; text-align: center;
            }
            .field-group input:focus, .field-group select:focus, .field-group textarea:focus {
                outline: none; border-color: var(--primary); box-shadow: 0 0 0 3px var(--primary-light);
            }
            .field-group input:disabled { background: var(--gray-100); cursor: not-allowed; }
            .field-group input:invalid:not(:placeholder-shown), .field-group select:invalid:not(:placeholder-shown) {
                border-color: var(--danger); box-shadow: 0 0 0 2px rgba(239,68,68,0.15);
            }
            .field-group textarea { resize: vertical; min-height: 60px; text-align: left; }
            .field-group .hint { font-size: 0.7rem; color: var(--gray-400); margin-top: 3px; }
            .phone-wrapper { display: flex; align-items: center; gap: 4px; width: 100%; max-width: 400px; }
            .phone-wrapper .phone-prefix { background: #f1f5f9; padding: 8px 12px; border: 1px solid var(--gray-300); border-radius: 8px 0 0 8px; font-weight: 600; color: var(--gray-600); font-size: 0.85rem; white-space: nowrap; }
            .phone-wrapper input { border-radius: 0 8px 8px 0; flex: 1; }
            .ninu-wrapper { width: 100%; max-width: 400px; }
            .ninu-wrapper input { width: 100%; padding: 8px 12px; border: 1px solid var(--gray-300); border-radius: 8px; font-size: 0.9rem; transition: 0.2s; background: white; font-family: monospace; letter-spacing: 1px; text-align: center; }
            .ninu-wrapper input:focus { outline: none; border-color: var(--primary); box-shadow: 0 0 0 3px var(--primary-light); }
            .field-hint { font-size: 0.7rem; color: var(--gray-400); margin-top: 3px; text-align: center; display: block; }
            .photo-upload-area { display: flex; align-items: center; gap: 16px; padding: 12px; background: white; border: 2px dashed var(--gray-300); border-radius: 8px; transition: 0.2s; flex-wrap: wrap; justify-content: center; }
            .photo-upload-area:hover { border-color: var(--primary); background: var(--gray-50); }
            .photo-preview { width: 100px; height: 100px; border-radius: 50%; background: var(--gray-100); overflow: hidden; display: flex; align-items: center; justify-content: center; flex-shrink: 0; border: 3px solid white; box-shadow: 0 2px 8px rgba(0,0,0,0.1); }
            .photo-preview img { width: 100%; height: 100%; object-fit: cover; display: block; }
            .photo-preview i { font-size: 3rem; color: var(--gray-400); }
            .photo-upload-area input[type="file"] { display: none; }
            .photo-upload-label { background: var(--primary); color: white; padding: 8px 18px; border-radius: 6px; font-size: 0.85rem; font-weight: 500; cursor: pointer; transition: 0.2s; display: inline-block; }
            .photo-upload-label:hover { background: var(--primary-dark); transform: scale(1.02); }
            .btn-remove-photo { background: var(--danger); color: white; border: none; padding: 6px 12px; border-radius: 6px; font-size: 0.75rem; cursor: pointer; margin-left: 6px; display: none; }
            .btn-remove-photo.visible { display: inline-block; }
            .file-name-display { font-size: 0.8rem; color: var(--gray-500); margin-top: 4px; }
            .form-actions { margin-top: 30px; display: flex; justify-content: center; gap: 16px; padding-top: 20px; border-top: 1px solid var(--gray-200); }
            .btn { padding: 10px 28px; border-radius: 8px; font-weight: 600; font-size: 0.9rem; border: none; cursor: pointer; transition: 0.2s; display: inline-flex; align-items: center; gap: 8px; }
            .btn-secondary { background: var(--gray-200); color: var(--gray-700); }
            .btn-secondary:hover { background: var(--gray-300); }
            .btn-primary { background: var(--primary-gradient); color: white; box-shadow: 0 2px 8px rgba(59,130,246,0.3); }
            .btn-primary:hover:not(:disabled) { transform: translateY(-2px); box-shadow: 0 4px 12px rgba(59,130,246,0.4); }
            .btn-primary:active { transform: translateY(0); }
            .btn-primary:disabled { opacity: 0.6; cursor: not-allowed; }
            @media (max-width: 600px) { .modal-header h2 { font-size: 1.1rem; } .modal-body { padding: 16px; } .section-card { padding: 14px; } .photo-upload-area { flex-wrap: wrap; justify-content: center; } .phone-wrapper { flex-wrap: wrap; } .phone-wrapper .phone-prefix { border-radius: 8px; width: 100%; text-align: center; } .phone-wrapper input { border-radius: 8px; } }
        </style>
        """);

        // ============================================================
        // STRUCTURE HTML
        // ============================================================
        sb.append("<div class=\"modal-form-wrapper\" id=\"modalForm\">");
        sb.append("<div class=\"modal-header\">");
        sb.append("<h2><i class=\"fa-solid fa-user-graduate\"></i> ").append(titre).append("</h2>");
        sb.append("<button type=\"button\" class=\"btn-close-modal\" onclick=\"fermerModal()\" aria-label=\"Fermer\"><i class=\"fa-solid fa-xmark\"></i></button>");
        sb.append("</div>");
        sb.append("<div class=\"modal-body\">");

        if (!successMessage.isEmpty()) {
            sb.append("<div class=\"alert alert-success\"><i class=\"fa-solid fa-circle-check\"></i> ")
              .append(echapperHtml(successMessage)).append("</div>");
        }
        if (!errorMessage.isEmpty()) {
            sb.append("<div class=\"alert alert-danger\"><i class=\"fa-solid fa-circle-exclamation\"></i> ")
              .append(echapperHtml(errorMessage)).append("</div>");
        }

        // ============================================================
        // FORMULAIRE
        // ============================================================
        sb.append("<form method=\"POST\" action=\"/admin/etudiants\" enctype=\"multipart/form-data\" id=\"formEtudiant\">");

        sb.append("<input type=\"hidden\" name=\"action\" value=\"").append(echapperHtml(action)).append("\">");
        sb.append("<input type=\"hidden\" name=\"institutionId\" id=\"institutionIdHidden\" value=\"").append(echapperHtml(institutionId)).append("\">");
        sb.append("<input type=\"hidden\" name=\"numId\" id=\"numId\" value=\"").append(echapperHtml(numId)).append("\">");
        sb.append("<input type=\"hidden\" name=\"numeroIdentifiant\" value=\"").append(echapperHtml(numId)).append("\">");
        sb.append("<input type=\"hidden\" name=\"photoPathActuelle\" id=\"photoPathActuelle\" value=\"").append(echapperHtml(photoPath)).append("\">");
        sb.append("<input type=\"hidden\" name=\"empreintePathActuelle\" value=\"").append(echapperHtml(empreintePath)).append("\">");
        sb.append("<input type=\"hidden\" name=\"visagePathActuelle\" value=\"").append(echapperHtml(visagePath)).append("\">");
        sb.append("<input type=\"hidden\" name=\"ninu_force\" id=\"ninu_force\" value=\"\">");

        sb.append("<div class=\"form-grid\">");

        // ================== SECTION 1 : IDENTITÉ ==================
        sb.append("<div class=\"section-card\">");
        sb.append("<div class=\"section-title\"><i class=\"fa-solid fa-id-card\"></i> Identité & État civil</div>");

        sb.append("<div class=\"field-row\">");
        sb.append("<div class=\"field-group\"><label>ID Unique</label><input type=\"text\" value=\"")
          .append(echapperHtml(numId)).append("\" disabled></div>");
        sb.append("<div class=\"field-group\"><label>Date de naissance <span class=\"required\">*</span></label>")
          .append("<input type=\"date\" name=\"dateNaissance\" id=\"dateNaissance\" value=\"")
          .append(echapperHtml(dateNaissance)).append("\" required></div>");
        sb.append("</div>");

        sb.append("<div class=\"field-row\">");
        sb.append("<div class=\"field-group\"><label>Nom <span class=\"required\">*</span></label><input type=\"text\" name=\"nom\" id=\"nom\" value=\"")
          .append(echapperHtml(nom)).append("\" required minlength=\"2\"></div>");
        sb.append("<div class=\"field-group\"><label>Prénom <span class=\"required\">*</span></label><input type=\"text\" name=\"prenom\" id=\"prenom\" value=\"")
          .append(echapperHtml(prenom)).append("\" required minlength=\"2\"></div>");
        sb.append("</div>");

        sb.append("<div class=\"field-row\">");
        sb.append("<div class=\"field-group\"><label>Sexe <span class=\"required\">*</span></label>");
        sb.append("<select name=\"sexe\" id=\"sexe\" required>");
        sb.append("<option value=\"\">-- Sélectionner --</option>");
        for (String s : Constantes.SEXES) {
            String sel = s.equals(sexe) ? " selected" : "";
            sb.append("<option value=\"").append(echapperHtml(s)).append("\"").append(sel).append(">")
              .append(echapperHtml(s)).append("</option>");
        }
        sb.append("</select></div>");

        sb.append("<div class=\"field-group\"><label>Groupe sanguin <span class=\"required\">*</span></label>");
        sb.append("<select name=\"groupeSanguin\" id=\"groupeSanguin\" required>");
        sb.append("<option value=\"\">-- Sélectionner --</option>");
        for (String g : Constantes.GROUPES_SANGUINS) {
            String sel = g.equals(groupeSanguin) ? " selected" : "";
            sb.append("<option value=\"").append(echapperHtml(g)).append("\"").append(sel).append(">")
              .append(echapperHtml(g)).append("</option>");
        }
        sb.append("</select></div>");
        sb.append("</div>");

        sb.append("</div>");

        // ================== SECTION 2 : COORDONNÉES ==================
        sb.append("<div class=\"section-card\">");
        sb.append("<div class=\"section-title\"><i class=\"fa-solid fa-address-book\"></i> Coordonnées & Origine</div>");

        sb.append("<div class=\"field-row\">");
        sb.append("<div class=\"field-group\"><label>Téléphone <span class=\"required\">*</span></label>");
        sb.append("<div class=\"phone-wrapper\">");
        sb.append("<span class=\"phone-prefix\">(+509)</span>");
        sb.append("<input type=\"text\" name=\"telephone\" id=\"telephone\" placeholder=\"32-45-67-89\" maxlength=\"11\" value=\"")
          .append(echapperHtml(telephoneDisplay)).append("\" required>");
        sb.append("</div>");
        sb.append("<span class=\"field-hint\">Format : 32-45-67-89</span>");
        sb.append("</div>");
        sb.append("<div class=\"field-group\"><label>Email <span class=\"required\">*</span></label><input type=\"email\" name=\"email\" id=\"email\" value=\"")
          .append(echapperHtml(email)).append("\" required></div>");
        sb.append("</div>");

        sb.append("<div class=\"field-row single\">");
        sb.append("<div class=\"field-group\"><label>Adresse résidentielle <span class=\"required\">*</span></label><input type=\"text\" name=\"adresse\" id=\"adresse\" value=\"")
          .append(echapperHtml(adresse)).append("\" required minlength=\"3\"></div>");
        sb.append("</div>");

        sb.append("<div class=\"field-row\">");
        sb.append("<div class=\"field-group\"><label>Département de naissance <span class=\"required\">*</span></label>")
          .append(genererSelectDepartement("departementNaissance", departementNaissance, "communeNaissance")).append("</div>");
        sb.append("<div class=\"field-group\"><label>Commune de naissance <span class=\"required\">*</span></label>")
          .append(genererSelectCommune("communeNaissance", communeNaissance, departementNaissance)).append("</div>");
        sb.append("</div>");

        sb.append("</div>");

        // ================== SECTION 3 : RÉSIDENCE ==================
        sb.append("<div class=\"section-card\">");
        sb.append("<div class=\"section-title\"><i class=\"fa-solid fa-home\"></i> Lieu de résidence</div>");

        sb.append("<div class=\"field-row\">");
        sb.append("<div class=\"field-group\"><label>Département de résidence <span class=\"required\">*</span></label>")
          .append(genererSelectDepartement("departementResidence", departementResidence, "communeResidence")).append("</div>");
        sb.append("<div class=\"field-group\"><label>Commune de résidence <span class=\"required\">*</span></label>")
          .append(genererSelectCommune("communeResidence", communeResidence, departementResidence)).append("</div>");
        sb.append("</div>");

        sb.append("</div>");

        // ================== SECTION 4 : ACADÉMIQUE ==================
        sb.append("<div class=\"section-card full-width\">");
        sb.append("<div class=\"section-title\"><i class=\"fa-solid fa-graduation-cap\"></i> Informations académiques</div>");

        sb.append("<div class=\"field-row three\">");
        sb.append("<div class=\"field-group\"><label>Année académique <span class=\"required\">*</span></label>");
        sb.append("<select name=\"anneeAcademique\" id=\"anneeAcademique\" required>");
        sb.append("<option value=\"\">Sélectionner</option>");
        for (String a : anneesDisponibles) {
            String sel = a.equals(anneeAcademique) ? " selected" : "";
            sb.append("<option value=\"").append(echapperHtml(a)).append("\"").append(sel).append(">")
              .append(echapperHtml(a)).append("</option>");
        }
        sb.append("</select></div>");

        sb.append("<div class=\"field-group\"><label>Période <span class=\"required\">*</span></label>");
        sb.append("<select name=\"periode\" id=\"periode\" required>");
        sb.append("<option value=\"\">Sélectionner</option>");
        for (String p : periodesDisponibles) {
            String sel = p.equals(periode) ? " selected" : "";
            sb.append("<option value=\"").append(echapperHtml(p)).append("\"").append(sel).append(">")
              .append(echapperHtml(p)).append("</option>");
        }
        sb.append("</select></div>");

        sb.append("<div class=\"field-group\"><label>Classe <span class=\"required\">*</span></label>");
        sb.append("<select name=\"classe\" id=\"classe\" required>");
        sb.append("<option value=\"\">Sélectionner</option>");
        for (String c : classesDisponibles) {
            String sel = c.equals(classe) ? " selected" : "";
            sb.append("<option value=\"").append(echapperHtml(c)).append("\"").append(sel).append(">")
              .append(echapperHtml(c)).append("</option>");
        }
        sb.append("</select></div>");
        sb.append("</div>");

        sb.append("<div class=\"field-row three\">");
        sb.append("<div class=\"field-group\"><label>Promotion</label>");
        sb.append("<select name=\"promotion\" id=\"promotion\">");
        sb.append("<option value=\"\">Sélectionner</option>");
        for (String prom : promotionsDisponibles) {
            String sel = prom.equals(promotion) ? " selected" : "";
            sb.append("<option value=\"").append(echapperHtml(prom)).append("\"").append(sel).append(">")
              .append(echapperHtml(prom)).append("</option>");
        }
        sb.append("</select></div>");

        sb.append("<div class=\"field-group\"><label>Option / Filière</label>");
        sb.append("<select name=\"option\" id=\"option\">");
        sb.append("<option value=\"\">Aucune</option>");
        for (Options opt : optionsDisponibles) {
            String optValue = opt.getOption();
            if (optValue != null && !optValue.isBlank()) {
                String selected = optValue.equals(option) ? " selected" : "";
                sb.append("<option value=\"").append(echapperHtml(optValue)).append("\"").append(selected).append(">")
                  .append(echapperHtml(optValue)).append("</option>");
            }
        }
        sb.append("</select></div>");

        sb.append("<div class=\"field-group\"><label>Matricule</label><input type=\"text\" name=\"matricule\" id=\"matricule\" value=\"")
          .append(echapperHtml(matricule)).append("\"></div>");
        sb.append("</div>");

        sb.append("<div class=\"field-row three\">");
        sb.append("<div class=\"field-group\"><label>NINU <span class=\"required\">*</span></label>");
        sb.append("<div class=\"ninu-wrapper\">");
        sb.append("<input type=\"text\" name=\"ninu\" id=\"ninu\" placeholder=\"123-456-789-0\" maxlength=\"14\" value=\"")
          .append(echapperHtml(ninuDisplay)).append("\" required>");
        sb.append("</div>");
        sb.append("<span class=\"field-hint\">Format : 123-456-789-0</span>");
        sb.append("</div>");

        sb.append("<div class=\"field-group\"><label>Statut</label><select name=\"statut\" id=\"statut\">");
        String[] statuts = {"ACTIF", "INACTIF", "SUSPENDU", "DIPLOME"};
        for (String s : statuts) {
            String sel = s.equals(statut) ? " selected" : "";
            sb.append("<option value=\"").append(s).append("\"").append(sel).append(">").append(s).append("</option>");
        }
        sb.append("</select></div>");

        sb.append("<div class=\"field-group\"><label>Date d'inscription</label><input type=\"date\" name=\"dateInscription\" id=\"dateInscription\" value=\"")
          .append(echapperHtml(dateInscription)).append("\"></div>");
        sb.append("</div>");

        sb.append("</div>");

        // ================== SECTION 5 : FILIATION ==================
        sb.append("<div class=\"section-card full-width\">");
        sb.append("<div class=\"section-title\"><i class=\"fa-solid fa-people-roof\"></i> Filiation & Responsable légal</div>");

        sb.append("<div class=\"field-row\">");
        sb.append("<div class=\"field-group\"><label>Nom du père <span class=\"required\">*</span></label><input type=\"text\" name=\"nomPere\" id=\"nomPere\" value=\"")
          .append(echapperHtml(nomPere)).append("\" required minlength=\"2\"></div>");
        sb.append("<div class=\"field-group\"><label>Prénom du père <span class=\"required\">*</span></label><input type=\"text\" name=\"prenomPere\" id=\"prenomPere\" value=\"")
          .append(echapperHtml(prenomPere)).append("\" required minlength=\"2\"></div>");
        sb.append("<div class=\"field-group\"><label>Nom de la mère <span class=\"required\">*</span></label><input type=\"text\" name=\"nomMere\" id=\"nomMere\" value=\"")
          .append(echapperHtml(nomMere)).append("\" required minlength=\"2\"></div>");
        sb.append("<div class=\"field-group\"><label>Prénom de la mère <span class=\"required\">*</span></label><input type=\"text\" name=\"prenomMere\" id=\"prenomMere\" value=\"")
          .append(echapperHtml(prenomMere)).append("\" required minlength=\"2\"></div>");
        sb.append("</div>");

        sb.append("<div class=\"field-row\">");
        sb.append("<div class=\"field-group\"><label>Téléphone des parents <span class=\"required\">*</span></label>");
        sb.append("<div class=\"phone-wrapper\">");
        sb.append("<span class=\"phone-prefix\">(+509)</span>");
        sb.append("<input type=\"text\" name=\"telephoneParents\" id=\"telephoneParents\" placeholder=\"32-45-67-89\" maxlength=\"11\" value=\"")
          .append(echapperHtml(telephoneParentsDisplay)).append("\" required>");
        sb.append("</div>");
        sb.append("<span class=\"field-hint\">Format : 32-45-67-89</span>");
        sb.append("</div>");
        sb.append("<div class=\"field-group\"><label>Résidence des parents <span class=\"required\">*</span></label><input type=\"text\" name=\"residenceParents\" id=\"residenceParents\" value=\"")
          .append(echapperHtml(residenceParents)).append("\" required minlength=\"3\"></div>");
        sb.append("</div>");

        sb.append("<div class=\"field-row\">");
        sb.append("<div class=\"field-group\">");
        sb.append("<label for=\"lienParente\">Lien de parenté <span class=\"required\">*</span></label>");
        sb.append("<select name=\"lienParente\" id=\"lienParente\" required onchange=\"autoRemplirParent(this.value)\">");
        sb.append("<option value=\"\">-- Sélectionner --</option>");
        String[] liens = {"Père", "Mère", "Tuteur", "Curateur", "Grand-père", "Grand-mère", "Oncle", "Tante", "Frère", "Sœur", "Autre"};
        for (String lien : liens) {
            String sel = lien.equals(lienParente) ? " selected" : "";
            sb.append("<option value=\"").append(echapperHtml(lien)).append("\"").append(sel).append(">")
              .append(echapperHtml(lien)).append("</option>");
        }
        sb.append("</select>");
        sb.append("</div>");

        sb.append("<div class=\"field-group\">");
        sb.append("<label for=\"typeResponsable\">Type de responsable <span class=\"required\">*</span></label>");
        sb.append("<select name=\"typeResponsable\" id=\"typeResponsable\" required>");
        sb.append("<option value=\"\">-- Sélectionner --</option>");
        String[] types = {"Légal", "Conventionnel", "Naturel", "Autre"};
        for (String type : types) {
            String sel = type.equals(typeResponsable) ? " selected" : "";
            sb.append("<option value=\"").append(echapperHtml(type)).append("\"").append(sel).append(">")
              .append(echapperHtml(type)).append("</option>");
        }
        sb.append("</select>");
        sb.append("</div>");
        sb.append("</div>");

        sb.append("<div class=\"field-row\">");
        sb.append("<div class=\"field-group\"><label>Nom du responsable <span class=\"required\">*</span></label><input type=\"text\" name=\"nomResponsable\" id=\"nomResponsable\" value=\"")
          .append(echapperHtml(nomResponsable)).append("\" required minlength=\"2\"></div>");
        sb.append("<div class=\"field-group\"><label>Prénom du responsable <span class=\"required\">*</span></label><input type=\"text\" name=\"prenomResponsable\" id=\"prenomResponsable\" value=\"")
          .append(echapperHtml(prenomResponsable)).append("\" required minlength=\"2\"></div>");
        sb.append("</div>");

        sb.append("</div>");

        // ================== SECTION 6 : PHOTO / BIOMÉTRIE ==================
        sb.append("<div class=\"section-card full-width\">");
        sb.append("<div class=\"section-title\"><i class=\"fa-solid fa-camera\"></i> Photo, Biométrie & Observations</div>");

        String requiredPhoto = estModification ? "" : " required";
        sb.append("<div class=\"field-row single\">");
        sb.append("<div class=\"field-group\"><label>Photographie d'identité ")
          .append(estModification ? "" : "<span class=\"required\">*</span>")
          .append("</label>");
        sb.append("<div class=\"photo-upload-area\">");
        sb.append("<div class=\"photo-preview\" id=\"photoPreview\">");
        if (!photoUrl.isEmpty()) {
            sb.append("<img src=\"").append(echapperHtml(photoUrl)).append("\" id=\"imgPreview\" alt=\"Photo actuelle\" style=\"display:block;\">");
        } else {
            sb.append("<img id=\"imgPreview\" alt=\"Prévisualisation\" style=\"display:none;\">");
            sb.append("<i class=\"fa-solid fa-user\" id=\"defaultPhotoIcon\"></i>");
        }
        sb.append("</div>");
        sb.append("<div style=\"display:flex;flex-direction:column;align-items:center;gap:4px;\">");
        sb.append("<input type=\"file\" name=\"photoPath\" id=\"inputPhotoFile\" accept=\"image/*\" onchange=\"previsualiserPhoto(event)\"")
          .append(requiredPhoto).append(">");
        sb.append("<label for=\"inputPhotoFile\" class=\"photo-upload-label\"><i class=\"fa-solid fa-upload\"></i> Choisir une photo</label>");
        sb.append("<button type=\"button\" class=\"btn-remove-photo\" id=\"btnRemovePhoto\" onclick=\"supprimerPhoto()\">Supprimer</button>");
        sb.append("<div id=\"fileNameDisplay\" class=\"file-name-display\">");
        if (!photoPath.isEmpty()) {
            sb.append("Fichier actuel : ").append(echapperHtml(photoPath.substring(photoPath.lastIndexOf('/') + 1)));
        } else {
            sb.append("Aucun fichier sélectionné");
        }
        sb.append("</div>");
        sb.append("</div>");
        sb.append("</div>");
        sb.append("</div>");
        sb.append("</div>");

        sb.append("<div class=\"field-row\">");
        sb.append("<div class=\"field-group\"><label>Empreinte digitale</label><input type=\"file\" name=\"empreintePath\" accept=\".bin,.dat\">");
        if (!empreintePath.isEmpty()) {
            sb.append("<span class=\"hint\" style=\"color:var(--success)\">Actuel : ").append(echapperHtml(empreintePath)).append("</span>");
        }
        sb.append("</div>");
        sb.append("<div class=\"field-group\"><label>Visage (facial)</label><input type=\"file\" name=\"visagePath\" accept=\"image/*\">");
        if (!visagePath.isEmpty()) {
            sb.append("<span class=\"hint\" style=\"color:var(--success)\">Actuel : ").append(echapperHtml(visagePath)).append("</span>");
        }
        sb.append("</div>");
        sb.append("<div class=\"field-group\"><label>Biométrie active</label><select name=\"biometrieActive\" id=\"biometrieActive\">");
        sb.append("<option value=\"true\"").append(biometrieActive ? " selected" : "").append(">Oui</option>");
        sb.append("<option value=\"false\"").append(!biometrieActive ? " selected" : "").append(">Non</option>");
        sb.append("</select></div>");
        sb.append("</div>");

        sb.append("<div class=\"field-row single\">");
        sb.append("<div class=\"field-group\">");
        sb.append("<label for=\"observations\"><i class=\"fa-solid fa-comment\"></i> Observations / Remarques</label>");
        sb.append("<textarea name=\"observations\" id=\"observations\" rows=\"3\" placeholder=\"Ajoutez des remarques ou observations sur l'étudiant...\">")
          .append(echapperHtml(observations))
          .append("</textarea>");
        sb.append("<span class=\"hint\">Ce champ est facultatif.</span>");
        sb.append("</div>");
        sb.append("</div>");

        sb.append("</div>");

        sb.append("</div>"); // fin form-grid

        // Boutons
        sb.append("<div class=\"form-actions\">");
        sb.append("<button type=\"button\" class=\"btn btn-secondary\" onclick=\"fermerModal()\"><i class=\"fa-solid fa-times\"></i> Annuler</button>");
        sb.append("<button type=\"submit\" class=\"btn btn-primary\" id=\"btnSubmitEtudiant\"><i class=\"fa-solid fa-check\"></i> Enregistrer</button>");
        sb.append("</div>");

        sb.append("</form>");
        sb.append("</div>");
        sb.append("</div>");

        // ============================================================
        // JAVASCRIPT
        // ============================================================
        sb.append("<script>");
        sb.append("window.communesParDepartement = {");
        String[][] deptCommunes = Constantes.getDepartementsCommunes();
        for (int i = 0; i < deptCommunes.length; i++) {
            String[] ligne = deptCommunes[i];
            String departement = ligne[0];
            sb.append("\"").append(echapperJson(departement)).append("\": [");
            for (int j = 1; j < ligne.length; j++) {
                sb.append("\"").append(echapperJson(ligne[j])).append("\"");
                if (j < ligne.length - 1) sb.append(", ");
            }
            sb.append("]");
            if (i < deptCommunes.length - 1) sb.append(", ");
        }
        sb.append("};\n");

        sb.append("""
        // ============================================================
        // CHARGEMENT DES COMMUNES
        // ============================================================
        function chargerCommunes(departement, selectId) {
            var select = document.getElementById(selectId);
            if (!select) return;
            select.innerHTML = '<option value="">Sélectionner</option>';
            var communes = window.communesParDepartement[departement] || [];
            communes.forEach(function(commune) {
                var opt = document.createElement('option');
                opt.value = commune;
                opt.textContent = commune;
                select.appendChild(opt);
            });
        }

        // ============================================================
        // AUTO-REMPLISSAGE RESPONSABLE
        // ============================================================
        function autoRemplirParent(lien) {
            var nomPere = document.getElementById('nomPere');
            var prenomPere = document.getElementById('prenomPere');
            var nomMere = document.getElementById('nomMere');
            var prenomMere = document.getElementById('prenomMere');
            var nomResponsable = document.getElementById('nomResponsable');
            var prenomResponsable = document.getElementById('prenomResponsable');

            if (lien === 'Père') {
                if (nomPere && nomResponsable) nomResponsable.value = nomPere.value || '';
                if (prenomPere && prenomResponsable) prenomResponsable.value = prenomPere.value || '';
            } else if (lien === 'Mère') {
                if (nomMere && nomResponsable) nomResponsable.value = nomMere.value || '';
                if (prenomMere && prenomResponsable) prenomResponsable.value = prenomMere.value || '';
            }
        }

        // ============================================================
        // VALIDATION NINU
        // ============================================================
        function validerNINU(ninu) {
            var clean = ninu.replace(/-/g, '');
            if (!/^\\d{10}$/.test(clean)) {
                return { valide: false, message: 'Le NINU doit contenir exactement 10 chiffres.' };
            }
            if (!clean.startsWith('1')) {
                return { valide: false, message: 'Le NINU doit commencer par le chiffre 1.' };
            }
            return { valide: true };
        }

        function formaterNINU(input) {
            var value = input.value.replace(/[^0-9]/g, '');
            var formatted = '';
            for (var i = 0; i < value.length && i < 10; i++) {
                if (i === 3 || i === 6) formatted += '-';
                formatted += value[i];
                if (i === 8) formatted += '-';
            }
            input.value = formatted;
        }

        // ============================================================
        // VALIDATION TÉLÉPHONE
        // ============================================================
        function validerTelephone(telephone) {
            var clean = telephone.replace(/-/g, '');
            if (!/^\\d{8}$/.test(clean)) {
                return { valide: false, message: 'Le téléphone doit contenir 8 chiffres.' };
            }
            return { valide: true };
        }

        function formaterTelephone(input) {
            var value = input.value.replace(/[^0-9]/g, '');
            var formatted = '';
            for (var i = 0; i < value.length && i < 8; i++) {
                if (i === 2 || i === 4) formatted += '-';
                formatted += value[i];
                if (i === 5) formatted += '-';
            }
            input.value = formatted;
        }

        // ============================================================
        // CHARGEMENT OPTIONS
        // ============================================================
        function loadOptions() {
            var annee = document.getElementById('anneeAcademique').value;
            var periode = document.getElementById('periode').value;
            var classe = document.getElementById('classe').value;
            var promotion = document.getElementById('promotion').value;
            var optionSelect = document.getElementById('option');
            var institutionId = document.getElementById('institutionIdHidden').value;

            if (!annee || !periode || !classe || !institutionId) {
                optionSelect.innerHTML = '<option value="">Aucune</option>';
                return;
            }

            var url = '/admin/etudiants/api/options?institutionId=' + encodeURIComponent(institutionId) +
                      '&annee=' + encodeURIComponent(annee) +
                      '&periode=' + encodeURIComponent(periode) +
                      '&classe=' + encodeURIComponent(classe) +
                      (promotion ? '&promotion=' + encodeURIComponent(promotion) : '');

            fetch(url)
                .then(response => response.json())
                .then(data => {
                    if (data.success) {
                        var currentValue = optionSelect.value;
                        optionSelect.innerHTML = '<option value="">Aucune</option>';
                        data.data.forEach(function(opt) {
                            var option = document.createElement('option');
                            option.value = opt.option;
                            option.textContent = opt.option;
                            optionSelect.appendChild(option);
                        });
                        if (currentValue) optionSelect.value = currentValue;
                    }
                })
                .catch(err => console.error('Erreur options:', err));
        }

        // ============================================================
        // PRÉVISUALISATION PHOTO
        // ============================================================
        function previsualiserPhoto(event) {
            var file = event.target.files[0];
            if (!file) return;
            var img = document.getElementById('imgPreview');
            var defaultIcon = document.getElementById('defaultPhotoIcon');
            var fileNameDisplay = document.getElementById('fileNameDisplay');
            var btnRemove = document.getElementById('btnRemovePhoto');

            var reader = new FileReader();
            reader.onload = function(e) {
                if (img) {
                    img.src = e.target.result;
                    img.style.display = 'block';
                }
                if (defaultIcon) defaultIcon.style.display = 'none';
                if (fileNameDisplay) fileNameDisplay.textContent = 'Fichier sélectionné : ' + file.name;
                if (btnRemove) btnRemove.classList.add('visible');
            };
            reader.readAsDataURL(file);
        }

        function supprimerPhoto() {
            var input = document.getElementById('inputPhotoFile');
            var img = document.getElementById('imgPreview');
            var defaultIcon = document.getElementById('defaultPhotoIcon');
            var fileNameDisplay = document.getElementById('fileNameDisplay');
            var btnRemove = document.getElementById('btnRemovePhoto');

            if (input) input.value = '';
            if (img) { img.src = ''; img.style.display = 'none'; }
            if (defaultIcon) defaultIcon.style.display = 'block';
            if (fileNameDisplay) fileNameDisplay.textContent = 'Aucun fichier sélectionné';
            if (btnRemove) btnRemove.classList.remove('visible');
        }

        function fermerModal() {
            var modal = document.getElementById('modalForm');
            if (modal) modal.style.display = 'none';
        }

        // ============================================================
        // VALIDATION MÉTIER
        // ============================================================
        function validerChamps() {
            var erreurs = [];

            var ninuInput = document.getElementById('ninu');
            var ninuForce = document.getElementById('ninu_force');
            if (ninuInput && ninuInput.value) {
                var v = validerNINU(ninuInput.value);
                if (!v.valide) {
                    erreurs.push('NINU : ' + v.message);
                    ninuInput.setCustomValidity(v.message);
                } else {
                    ninuInput.setCustomValidity('');
                    if (ninuForce) ninuForce.value = ninuInput.value.replace(/-/g, '');
                }
            } else {
                erreurs.push('NINU obligatoire');
            }

            var tel = document.getElementById('telephone');
            if (tel && tel.value) {
                var vt = validerTelephone(tel.value);
                if (!vt.valide) { erreurs.push('Téléphone : ' + vt.message); tel.setCustomValidity(vt.message); }
                else tel.setCustomValidity('');
            } else erreurs.push('Téléphone obligatoire');

            var telP = document.getElementById('telephoneParents');
            if (telP && telP.value) {
                var vtp = validerTelephone(telP.value);
                if (!vtp.valide) { erreurs.push('Téléphone parents : ' + vtp.message); telP.setCustomValidity(vtp.message); }
                else telP.setCustomValidity('');
            } else erreurs.push('Téléphone parents obligatoire');

            var sexe = document.getElementById('sexe');
            if (sexe && !sexe.value) erreurs.push('Sexe obligatoire');

            var gs = document.getElementById('groupeSanguin');
            if (gs && !gs.value) erreurs.push('Groupe sanguin obligatoire');

            return erreurs;
        }

        // ============================================================
        // VERROU ANTI-DOUBLE-SOUMISSION
        // ============================================================
        var formDejaEnvoye = false;

        function verrouillerSoumission() {
            if (formDejaEnvoye) {
                console.warn('Soumission déjà en cours, ignorée');
                return false;
            }
            formDejaEnvoye = true;
            var btn = document.getElementById('btnSubmitEtudiant');
            if (btn) {
                btn.disabled = true;
                btn.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> Envoi...';
            }
            setTimeout(function() {
                formDejaEnvoye = false;
                if (btn) {
                    btn.disabled = false;
                    btn.innerHTML = '<i class="fa-solid fa-check"></i> Enregistrer';
                }
            }, 15000);
            return true;
        }

        function deverrouillerSoumission() {
            formDejaEnvoye = false;
            var btn = document.getElementById('btnSubmitEtudiant');
            if (btn) {
                btn.disabled = false;
                btn.innerHTML = '<i class="fa-solid fa-check"></i> Enregistrer';
            }
        }

        // ============================================================
        // INITIALISATION
        // ============================================================
        document.addEventListener('DOMContentLoaded', function() {
            var ninuInput = document.getElementById('ninu');
            if (ninuInput) ninuInput.addEventListener('input', function() { formaterNINU(this); });

            var telephoneInput = document.getElementById('telephone');
            if (telephoneInput) telephoneInput.addEventListener('input', function() { formaterTelephone(this); });
            var telephoneParentsInput = document.getElementById('telephoneParents');
            if (telephoneParentsInput) telephoneParentsInput.addEventListener('input', function() { formaterTelephone(this); });

            var depNaiss = document.getElementById('departementNaissance');
            if (depNaiss && depNaiss.value) chargerCommunes(depNaiss.value, 'communeNaissance');
            var depRes = document.getElementById('departementResidence');
            if (depRes && depRes.value) chargerCommunes(depRes.value, 'communeResidence');

            loadOptions();
            ['anneeAcademique', 'periode', 'classe', 'promotion'].forEach(function(id) {
                var el = document.getElementById(id);
                if (el) el.addEventListener('change', loadOptions);
            });

            var lienParenteSelect = document.getElementById('lienParente');
            if (lienParenteSelect && lienParenteSelect.value) autoRemplirParent(lienParenteSelect.value);

            var photoExistante = document.getElementById('photoPathActuelle');
            var btnRemove = document.getElementById('btnRemovePhoto');
            if (photoExistante && photoExistante.value && btnRemove) {
                btnRemove.classList.add('visible');
            }

            // ============================================================
            // SOUMISSION — un seul écouteur sur 'submit'
            // ============================================================
            var form = document.getElementById('formEtudiant');
            if (!form) return;

            form.addEventListener('submit', function(e) {
                console.log('Submit intercepté');

                // ① Verrou anti-double
                if (!verrouillerSoumission()) {
                    e.preventDefault();
                    return false;
                }

                // ② Validation métier
                var erreurs = validerChamps();
                if (erreurs.length > 0) {
                    e.preventDefault();
                    alert('Veuillez corriger les erreurs suivantes :\\n\\n- ' + erreurs.join('\\n- '));
                    deverrouillerSoumission();
                    return false;
                }

                // ③ Validation HTML5
                if (!form.checkValidity()) {
                    e.preventDefault();
                    form.reportValidity();
                    deverrouillerSoumission();
                    return false;
                }

                // ④ Log des valeurs
                var fd = new FormData(form);
                console.log('=== VALEURS SOUMISES ===');
                for (var pair of fd.entries()) {
                    if (pair[1] instanceof File) {
                        console.log('   ' + pair[0] + ' = [FICHIER] ' + (pair[1].name || '(vide)'));
                    } else {
                        console.log('   ' + pair[0] + ' = ' + JSON.stringify(pair[1]));
                    }
                }

                console.log('Formulaire valide → envoi');
                return true;   // soumission native
            });
        });
        """);
        sb.append("</script>");

        return sb.toString();
    }

    // ============================================================
    // UTILITAIRES
    // ============================================================
    private static String safe(String s) {
        return s != null ? s : "";
    }

    private static String genererSelectDepartement(String id, String valeur, String communeSelectId) {
        StringBuilder sb = new StringBuilder();
        sb.append("<select name=\"").append(id).append("\" id=\"").append(id).append("\"");
        sb.append(" onchange=\"chargerCommunes(this.value, '").append(communeSelectId).append("')\" required>");
        sb.append("<option value=\"\">Sélectionner</option>");
        for (String dep : Constantes.DEPARTEMENTS) {
            String selected = (dep != null && dep.equals(valeur)) ? " selected" : "";
            sb.append("<option value=\"").append(echapperHtml(dep)).append("\"").append(selected).append(">")
              .append(echapperHtml(dep)).append("</option>");
        }
        sb.append("</select>");
        return sb.toString();
    }

    private static String genererSelectCommune(String id, String valeur, String departement) {
        StringBuilder sb = new StringBuilder();
        sb.append("<select name=\"").append(id).append("\" id=\"").append(id).append("\" required>");
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