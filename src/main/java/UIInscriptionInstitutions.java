import java.time.Year;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Rendu HTML du formulaire d'inscription d'une institution.
 *
 * <p><b>COHÉRENCE AVEC LE SERVEUR</b> :
 * <ul>
 *   <li>Action du formulaire = route réelle du handler</li>
 *   <li>Champs obligatoires côté HTML = validations côté serveur</li>
 *   <li>Longueur minimale du mot de passe synchronisée</li>
 * </ul>
 *
 * <p><b>SÉCURITÉ</b> :
 * <ul>
 *   <li>Échappement HTML strict (XSS)</li>
 *   <li>Échappement JSON pour les données injectées dans {@code <script>}</li>
 *   <li>Validation JS côté client (confort UX) doublée côté serveur (sécurité)</li>
 * </ul>
 */
public class UIInscriptionInstitutions {

    private static final Logger LOGGER =
            Logger.getLogger(UIInscriptionInstitutions.class.getName());

    // ============================================================
    // CONSTANTES
    // ============================================================
    private static final String[] DEPARTEMENTS = Constantes.getDepartements();
    private static final String[] TYPES        = Constantes.TYPES_INSTITUTION;
    private static final String[] NIVEAUX      = Constantes.NIVEAUX_INSTITUTION;
    private static final String[] CATEGORIES   = Constantes.CATEGORIES_INSTITUTION;

    /** Doit être aligné avec {@code HandlerInscriptionInstitutions.TAILLE_MAX_LOGO}. */
    private static final long MAX_LOGO_BYTES = 5L * 1024 * 1024;   // 5 Mo

    /** Doit être aligné avec {@code HandlerInscriptionInstitutions.LONGUEUR_MIN_MOT_DE_PASSE}. */
    private static final int LONGUEUR_MIN_MOT_DE_PASSE = 8;

    /** Statuts possibles (alignés avec le handler). */
    private static final String[] STATUTS = {"ACTIF", "INACTIF", "SUSPENDU"};

    /** Moyennes de passage possibles (alignées avec le handler). */
    private static final String[][] MOYENNES = {
        {"10.00", "10"},
        {"100.00", "100"}
    };

    /** Capacité initiale du StringBuilder (optimisation). */
    private static final int SB_CAPACITY = 16384;

    /** Route réelle du handler POST. */
    private static final String FORM_ACTION = "/api/inscription/admin";

    // ============================================================
    // RENDU PRINCIPAL
    // ============================================================
    public static String render(String error, String success) {
        StringBuilder sb = new StringBuilder(SB_CAPACITY);

        // ------- Head -------
        sb.append("<!DOCTYPE html>");
        sb.append("<html lang=\"fr\">");
        sb.append("<head>");
        sb.append("<meta charset=\"UTF-8\">");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        sb.append("<title>Inscription d'une institution</title>");
        sb.append("<link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css\">");
        sb.append("<link href=\"https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@300;400;500;600;700;800&display=swap\" rel=\"stylesheet\">");
        sb.append("<style>").append(renderStyles()).append("</style>");
        sb.append("</head>");
        sb.append("<body>");
        sb.append("<div class=\"container\">");

        // ------- En-tête -------
        sb.append("<div class=\"header\">");
        sb.append("<h1><i class=\"fas fa-university\"></i> Inscription d'une institution</h1>");
        sb.append("<p class=\"subtitle\">Créez une nouvelle institution dans le système</p>");
        sb.append("</div>");

        // ------- Messages -------
        appendMessage(sb, error, "error", "fa-exclamation-triangle");
        appendMessage(sb, success, "success", "fa-check-circle");

        // ------- Formulaire -------
        sb.append("<div class=\"card\">");
        sb.append("<form method=\"POST\" action=\"").append(FORM_ACTION).append("\" ")
          .append("class=\"inscription-form\" enctype=\"multipart/form-data\" ")
          .append("autocomplete=\"off\" onsubmit=\"return validerFormulaire(event);\">");

        // ===== IDENTIFICATION =====
        appendSectionTitle(sb, "fa-building", "Identification");

        appendFormRow(sb, new FormField[]{
            FormField.text("nomInstitution", "Nom de l'institution", true, 255, null),
            FormField.text("sigleInstitution", "Sigle", true, 100, null)
        });

        appendFormRow(sb, new FormField[]{
            FormField.text("deviseInstitution", "Devise", false, 255, null),
            FormField.select("typeInstitution", "Type", true, TYPES)
        });

        appendFormRow(sb, new FormField[]{
            FormField.select("niveauInstitution", "Niveau", true, NIVEAUX),
            FormField.select("categorieInstitution", "Catégorie", true, CATEGORIES)
        });

        // ===== LOCALISATION =====
        appendSectionTitle(sb, "fa-map-marker-alt", "Localisation");

        appendFormRow(sb, new FormField[]{
            FormField.text("adresseInstitution", "Adresse", false, 255, null),
            FormField.text("codePostalInstitution", "Code postal", false, 20, "readonly")
                .withId("codePostal")
                .withPlaceholder("Sélectionnez d'abord la commune")
        });

        appendFormRow(sb, new FormField[]{
            FormField.selectWithId("departementInstitution", "Département", false,
                    DEPARTEMENTS, "departement", "updateCommunes()"),
            FormField.selectWithId("communeInstitution", "Commune", false,
                    new String[]{"Sélectionner d'abord un département"},
                    "commune", "updateCodePostal()")
        });

        appendFormRow(sb, new FormField[]{
            FormField.text("paysInstitution", "Pays", false, 100, "Haïti")
        });

        // ===== CONTACTS =====
        appendSectionTitle(sb, "fa-address-book", "Contacts");

        appendFormRow(sb, new FormField[]{
            FormField.email("mailPrimaire", "Email principal", true, 255),
            FormField.email("mailSecondaire", "Email secondaire", false, 255)
        });

        appendFormRow(sb, new FormField[]{
            FormField.text("telephonePrimaireInstitution", "Téléphone principal", false, 50, null),
            FormField.text("telephoneSecondaireInstitution", "Téléphone secondaire", false, 50, null)
        });

        appendFormRow(sb, new FormField[]{
            FormField.text("siteWebInstitution", "Site web", false, 255, null),
            FormField.password("smtpPasswordInstitution", "Mot de passe SMTP", false, 255, null, null)
        });

        // ===== RESPONSABLE =====
        appendSectionTitle(sb, "fa-user-tie", "Responsable");

        appendFormRow(sb, new FormField[]{
            FormField.text("responsableInstitution", "Nom du responsable", false, 255, null),
            FormField.text("posteResponsableInstitution", "Poste occupé", false, 100, null)
        });

        appendFormRow(sb, new FormField[]{
            FormField.email("mailResponsableInstitution", "Email du responsable", false, 255),
            FormField.text("telephoneResponsableInstitution", "Téléphone du responsable", false, 50, null)
        });

        // ===== SYSTÈME ÉDUCATIF =====
        appendSectionTitle(sb, "fa-graduation-cap", "Système éducatif");

        appendFormRow(sb, new FormField[]{
            FormField.selectWithValueLabel("moyenneDePassageInstitution",
                    "Moyenne de Base", true, MOYENNES),
            FormField.text("systemeEducatifInstitution", "Régime éducatif", false, 100, null)
        });

        appendFormRow(sb, new FormField[]{
            FormField.select("statutInstitution", "Statut", false, STATUTS)
                .withSelected("ACTIF"),
            FormField.date("dateCreationInstitution", "Date de création",
                    java.time.LocalDate.now().toString())
        });

        // ===== SÉCURITÉ ET LOGO =====
        appendSectionTitle(sb, "fa-lock", "Sécurité & Logo");

        // Mot de passe
        sb.append("<div class=\"form-group\">");
        sb.append("<label>Mot de passe institution <span class=\"required\">*</span></label>");
        sb.append("<input type=\"password\" name=\"motDePasseInstitution\" required ")
          .append("autocomplete=\"new-password\" minlength=\"").append(LONGUEUR_MIN_MOT_DE_PASSE).append("\" ")
          .append("maxlength=\"255\">");
        sb.append("</div>");

        // Logo
        appendLogoUpload(sb);

        // Bouton
        sb.append("<button type=\"submit\" class=\"btn-submit\">")
          .append("<i class=\"fas fa-save\"></i> Créer l'institution</button>");
        sb.append("</form>");
        sb.append("</div>");

        // ------- Footer -------
        sb.append("<div class=\"footer\">© ").append(Year.now().getValue())
          .append(" M-TECH Academy - Tous droits réservés</div>");
        sb.append("</div>");

        // ------- JavaScript -------
        appendScript(sb);

        sb.append("</body>");
        sb.append("</html>");
        return sb.toString();
    }

    // ============================================================
    // HELPERS DE RENDU
    // ============================================================

    private static void appendMessage(StringBuilder sb, String message,
                                        String cssClass, String icon) {
        if (message == null || message.isEmpty()) return;
        sb.append("<div class=\"message ").append(cssClass).append("\">")
          .append("<i class=\"fas ").append(icon).append("\"></i> ")
          .append(escapeHtml(message))
          .append("</div>");
    }

    private static void appendSectionTitle(StringBuilder sb, String icon, String title) {
        sb.append("<div class=\"section-title\">")
          .append("<i class=\"fas ").append(icon).append("\"></i> ")
          .append(escapeHtml(title))
          .append("</div>");
    }

    private static void appendFormRow(StringBuilder sb, FormField[] fields) {
        sb.append("<div class=\"form-row\">");
        for (FormField f : fields) {
            f.render(sb);
        }
        sb.append("</div>");
    }

    private static void appendLogoUpload(StringBuilder sb) {
        sb.append("<div class=\"form-group\" style=\"text-align:center;\">");
        sb.append("<label>Logo (image)</label>");
        sb.append("<div class=\"logo-upload-area\">");
        sb.append("<input type=\"file\" name=\"logoFile\" id=\"logoFile\" ")
          .append("accept=\"image/png,image/jpeg,image/gif,image/webp,image/bmp\" ")
          .append("onchange=\"previewLogo(event)\">");
        sb.append("<label for=\"logoFile\" class=\"logo-upload-btn\">")
          .append("<i class=\"fas fa-upload\"></i> Choisir un logo</label>");
        sb.append("<div id=\"logoPreview\" class=\"logo-preview\">")
          .append("<i class=\"fas fa-image\"></i><span>Aucun logo sélectionné</span>")
          .append("</div>");
        sb.append("</div>");
        sb.append("<span class=\"file-hint\">Formats acceptés : PNG, JPG, GIF, WEBP, BMP (max 5 Mo)</span>");
        sb.append("</div>");
    }

    // ============================================================
    // FORM FIELD (modèle)
    // ============================================================
    private static final class FormField {
        String type;
        String name;
        String label;
        boolean required;
        int maxLength;
        String value;
        String id;
        String placeholder;
        String extraAttr;
        String[] options;
        String[][] optionsWithLabels;
        String selected;

        static FormField text(String name, String label, boolean required,
                                int maxLength, String defaultValue) {
            FormField f = new FormField();
            f.type = "text";
            f.name = name;
            f.label = label;
            f.required = required;
            f.maxLength = maxLength;
            f.value = defaultValue;
            return f;
        }

        static FormField email(String name, String label, boolean required, int maxLength) {
            FormField f = text(name, label, required, maxLength, null);
            f.type = "email";
            return f;
        }

        static FormField password(String name, String label, boolean required,
                                    int maxLength, String minLength, String placeholder) {
            FormField f = text(name, label, required, maxLength, null);
            f.type = "password";
            f.extraAttr = "autocomplete=\"new-password\"";
            f.placeholder = placeholder;
            return f;
        }

        static FormField date(String name, String label, String defaultValue) {
            FormField f = new FormField();
            f.type = "date";
            f.name = name;
            f.label = label;
            f.required = false;
            f.value = defaultValue;
            return f;
        }

        static FormField select(String name, String label, boolean required, String[] options) {
            FormField f = new FormField();
            f.type = "select";
            f.name = name;
            f.label = label;
            f.required = required;
            f.options = options;
            return f;
        }

        static FormField selectWithId(String name, String label, boolean required,
                                        String[] options, String id, String onChange) {
            FormField f = select(name, label, required, options);
            f.id = id;
            f.extraAttr = "onchange=\"" + onChange + "\"";
            return f;
        }

        static FormField selectWithValueLabel(String name, String label, boolean required,
                                                String[][] options) {
            FormField f = new FormField();
            f.type = "select";
            f.name = name;
            f.label = label;
            f.required = required;
            f.optionsWithLabels = options;
            return f;
        }

        FormField withId(String id) {
            this.id = id;
            return this;
        }

        FormField withPlaceholder(String placeholder) {
            this.placeholder = placeholder;
            return this;
        }

        FormField withSelected(String value) {
            this.selected = value;
            return this;
        }

        void render(StringBuilder sb) {
            sb.append("<div class=\"form-group\">");
            sb.append("<label>").append(escapeHtml(label));
            if (required) sb.append(" <span class=\"required\">*</span>");
            sb.append("</label>");

            switch (type) {
                case "select" -> renderSelect(sb);
                default -> renderInput(sb);
            }

            sb.append("</div>");
        }

        private void renderInput(StringBuilder sb) {
            sb.append("<input type=\"").append(type).append("\" ")
              .append("name=\"").append(escapeHtml(name)).append("\" ");
            if (id != null)          sb.append("id=\"").append(escapeHtml(id)).append("\" ");
            if (required)            sb.append("required ");
            if (maxLength > 0)       sb.append("maxlength=\"").append(maxLength).append("\" ");
            if (value != null)       sb.append("value=\"").append(escapeHtml(value)).append("\" ");
            if (placeholder != null) sb.append("placeholder=\"").append(escapeHtml(placeholder)).append("\" ");
            if (extraAttr != null)   sb.append(extraAttr).append(" ");
            sb.append(">");
        }

        private void renderSelect(StringBuilder sb) {
            sb.append("<select name=\"").append(escapeHtml(name)).append("\" ");
            if (id != null)    sb.append("id=\"").append(escapeHtml(id)).append("\" ");
            if (required)      sb.append("required ");
            if (extraAttr != null) sb.append(extraAttr).append(" ");
            sb.append(">");

            sb.append("<option value=\"\">-- Sélectionner --</option>");

            if (optionsWithLabels != null) {
                for (String[] opt : optionsWithLabels) {
                    sb.append("<option value=\"").append(escapeHtml(opt[0])).append("\">")
                      .append(escapeHtml(opt[1])).append("</option>");
                }
            } else if (options != null) {
                for (String opt : options) {
                    sb.append("<option value=\"").append(escapeHtml(opt)).append("\"");
                    if (selected != null && selected.equals(opt)) sb.append(" selected");
                    sb.append(">").append(escapeHtml(opt)).append("</option>");
                }
            }
            sb.append("</select>");
        }
    }

    // ============================================================
    // JAVASCRIPT
    // ============================================================
    private static void appendScript(StringBuilder sb) {
        String communesJson     = buildCommunesJson();
        String codesPostauxJson = buildCodesPostauxJson();

        sb.append("<script>\n");
        sb.append("const communesData = ").append(communesJson).append(";\n");
        sb.append("const codesPostaux = ").append(codesPostauxJson).append(";\n");
        sb.append("const MAX_LOGO_BYTES = ").append(MAX_LOGO_BYTES).append(";\n");
        sb.append("const MIN_PASSWORD_LENGTH = ").append(LONGUEUR_MIN_MOT_DE_PASSE).append(";\n");

        sb.append("""
        function updateCommunes() {
            const departement = document.getElementById('departement').value;
            const communeSelect = document.getElementById('commune');
            const codePostalInput = document.getElementById('codePostal');
            communeSelect.innerHTML = '<option value="">Sélectionner</option>';
            codePostalInput.value = '';
            if (departement && communesData[departement]) {
                communesData[departement].forEach(function(commune) {
                    const option = document.createElement('option');
                    option.value = commune;
                    option.textContent = commune;
                    communeSelect.appendChild(option);
                });
            }
        }

        function updateCodePostal() {
            const commune = document.getElementById('commune').value;
            const codePostalInput = document.getElementById('codePostal');
            if (commune && codesPostaux[commune]) {
                codePostalInput.value = codesPostaux[commune];
            } else {
                codePostalInput.value = '';
            }
        }

        function previewLogo(event) {
            const file = event.target.files[0];
            const preview = document.getElementById('logoPreview');

            if (!file) {
                preview.innerHTML = '<i class="fas fa-image"></i><span>Aucun logo sélectionné</span>';
                return;
            }

            if (file.size > MAX_LOGO_BYTES) {
                alert('Le fichier est trop volumineux (max 5 Mo).');
                event.target.value = '';
                preview.innerHTML = '<i class="fas fa-image"></i><span>Aucun logo sélectionné</span>';
                return;
            }

            const allowed = ['image/png', 'image/jpeg', 'image/gif',
                             'image/webp', 'image/bmp'];
            if (!allowed.includes(file.type)) {
                alert('Format non supporté. Utilisez PNG, JPG, GIF, WEBP ou BMP.');
                event.target.value = '';
                preview.innerHTML = '<i class="fas fa-image"></i><span>Aucun logo sélectionné</span>';
                return;
            }

            const reader = new FileReader();
            reader.onload = function(e) {
                preview.innerHTML = '<img src="' + e.target.result + '" alt="Logo">';
            };
            reader.readAsDataURL(file);
        }

        /**
         * Validation finale avant soumission.
         * Bloque l'envoi si un fichier trop gros a été sélectionné en fraude,
         * ou si le mot de passe est trop court.
         */
        function validerFormulaire(event) {
            // 1) Taille du logo
            const fileInput = document.getElementById('logoFile');
            if (fileInput && fileInput.files && fileInput.files.length > 0) {
                const file = fileInput.files[0];
                if (file.size > MAX_LOGO_BYTES) {
                    alert('Le logo dépasse 5 Mo. Veuillez choisir un fichier plus petit.');
                    event.preventDefault();
                    return false;
                }
            }

            // 2) Longueur du mot de passe
            const mdp = document.querySelector('input[name="motDePasseInstitution"]');
            if (mdp && mdp.value.length < MIN_PASSWORD_LENGTH) {
                alert('Le mot de passe doit contenir au moins ' + MIN_PASSWORD_LENGTH + ' caractères.');
                event.preventDefault();
                return false;
            }

            return true;
        }

        document.addEventListener('DOMContentLoaded', function() {
            const departement = document.getElementById('departement').value;
            if (departement) {
                updateCommunes();
                const commune = document.getElementById('commune').value;
                if (commune) updateCodePostal();
            }
        });
        """);
        sb.append("</script>");
    }

    // ============================================================
    // JSON POUR LE <script>
    // ============================================================
    private static String buildCommunesJson() {
        StringBuilder sb = new StringBuilder("{");
        boolean premier = true;
        for (String dep : DEPARTEMENTS) {
            String[] communes = Constantes.getCommunes(dep);
            if (communes == null) communes = new String[0];

            if (!premier) sb.append(",");
            premier = false;

            sb.append("\"").append(escapeJsonForHtml(dep)).append("\":[");
            for (int i = 0; i < communes.length; i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(escapeJsonForHtml(communes[i])).append("\"");
            }
            sb.append("]");
        }
        sb.append("}");
        return sb.toString();
    }

    private static String buildCodesPostauxJson() {
        Map<String, String> map = new LinkedHashMap<>();
        for (String dep : DEPARTEMENTS) {
            String[] communes = Constantes.getCommunes(dep);
            String[] codes    = Constantes.getCodesPostaux(dep);
            if (communes == null) communes = new String[0];
            if (codes == null)    codes = new String[0];

            if (communes.length != codes.length) {
                LOGGER.log(Level.WARNING,
                        "Désalignement communes/codesPostaux pour {0} : {1} communes, {2} codes.",
                        new Object[]{dep, communes.length, codes.length});
            }

            for (int i = 0; i < communes.length && i < codes.length; i++) {
                map.put(communes[i], codes[i]);
            }
        }
        StringBuilder sb = new StringBuilder("{");
        boolean premier = true;
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (!premier) sb.append(",");
            premier = false;
            sb.append("\"").append(escapeJsonForHtml(entry.getKey())).append("\":\"")
              .append(escapeJsonForHtml(entry.getValue())).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    // ============================================================
    // ÉCHAPPEMENTS
    // ============================================================

    /**
     * ✅ Échappe une chaîne pour insertion dans un littéral JSON à l'intérieur
     *    d'une balise {@code <script>}. Empêche notamment la séquence
     *    {@code </script>} de casser le contexte HTML.
     */
    private static String escapeJsonForHtml(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"'  -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '<'  -> sb.append("\\u003c");
                case '>'  -> sb.append("\\u003e");
                case '&'  -> sb.append("\\u0026");
                case '\u2028' -> sb.append("\\u2028");
                case '\u2029' -> sb.append("\\u2029");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    /**
     * ✅ Échappe une chaîne pour insertion dans du contenu HTML ou dans un
     *    attribut entouré de guillemets doubles.
     *
     * <p><b>Note</b> : {@code /} n'est PAS échappé pour éviter de casser les
     * URLs dans les attributs {@code value} ou {@code href}.</p>
     */
    private static String escapeHtml(String input) {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder(input.length() + 16);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '&'  -> sb.append("&amp;");
                case '<'  -> sb.append("&lt;");
                case '>'  -> sb.append("&gt;");
                case '"'  -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> {
                    if (c < 0x20 && c != '\t' && c != '\n' && c != '\r') {
                        // Caractères de contrôle ignorés
                        continue;
                    }
                    sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    // ============================================================
    // STYLES
    // ============================================================
    private static String renderStyles() {
        return """
        *{margin:0;padding:0;box-sizing:border-box;}
        body{font-family:'Plus Jakarta Sans',sans-serif;background:#f0f4f8;min-height:100vh;padding:20px;display:flex;justify-content:center;align-items:center;}
        .container{max-width:900px;width:100%;margin:0 auto;}

        .header{background:linear-gradient(135deg,#1e293b,#0f172a);color:white;padding:25px 30px;border-radius:16px;text-align:center;margin-bottom:25px;}
        .header h1{font-size:24px;font-weight:700;margin-top:10px;}
        .header h1 i{color:#3b82f6;margin-right:10px;}
        .header .subtitle{color:#94a3b8;font-size:14px;}

        .message{padding:12px 18px;border-radius:8px;margin-bottom:20px;text-align:center;}
        .message.success{background:#d1fae5;color:#16a34a;border-left:4px solid #16a34a;}
        .message.error{background:#fee2e2;color:#dc2626;border-left:4px solid #dc2626;}

        .card{background:white;border-radius:16px;padding:30px;border:1px solid #e2e8f0;}

        .inscription-form .section-title{font-size:18px;font-weight:600;color:#1e40af;margin:25px 0 15px 0;border-bottom:2px solid #1e40af;padding-bottom:8px;display:block;text-align:center;}

        .form-row{display:flex;gap:20px;flex-wrap:wrap;margin-bottom:15px;}
        .form-group{flex:1;min-width:200px;text-align:center;}
        .form-group label{display:block;font-weight:600;color:#475569;margin-bottom:5px;font-size:13px;}
        .form-group label .required{color:#ef4444;}
        .form-group input,.form-group select{width:100%;padding:10px 14px;border:1px solid #cbd5e1;border-radius:8px;font-size:14px;transition:0.2s;text-align:center;font-family:inherit;}
        .form-group input:focus,.form-group select:focus{outline:none;border-color:#1e40af;box-shadow:0 0 0 3px rgba(30,64,175,0.1);}
        .form-group input[readonly]{background:#f1f5f9;color:#475569;}

        .logo-upload-area{display:flex;flex-direction:column;align-items:center;gap:10px;padding:15px;background:#f8fafc;border:2px dashed #cbd5e1;border-radius:12px;transition:0.2s;}
        .logo-upload-area:hover{border-color:#1e40af;background:#f0f4ff;}
        .logo-upload-area input[type="file"]{display:none;}
        .logo-upload-btn{display:inline-block;padding:8px 20px;background:#1e40af;color:white;border-radius:8px;cursor:pointer;font-weight:600;font-size:14px;transition:0.2s;}
        .logo-upload-btn:hover{background:#1e3a8a;}
        .logo-preview{width:120px;height:120px;border-radius:8px;background:white;display:flex;flex-direction:column;align-items:center;justify-content:center;color:#94a3b8;font-size:13px;border:1px solid #e2e8f0;overflow:hidden;}
        .logo-preview img{width:100%;height:100%;object-fit:contain;}
        .logo-preview i{font-size:40px;margin-bottom:5px;}

        .file-hint{font-size:12px;color:#94a3b8;display:block;margin-top:6px;text-align:center;}

        .btn-submit{background:#16a34a;color:white;border:none;padding:12px 32px;border-radius:8px;font-weight:600;font-size:16px;cursor:pointer;transition:0.2s;display:flex;align-items:center;justify-content:center;gap:10px;margin-top:15px;width:100%;font-family:inherit;}
        .btn-submit:hover{background:#15803d;}

        .footer{margin-top:30px;text-align:center;color:#94a3b8;font-size:13px;}
        @media (max-width:768px){.form-row{flex-direction:column;}}
        """;
    }
}