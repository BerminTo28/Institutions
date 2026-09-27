// UIAttestationEtudiant.java - Version complète et intégrale

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

public class UIAttestationEtudiant {

    private static final Logger LOGGER = Logger.getLogger(UIAttestationEtudiant.class.getName());
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRENCH);
    private static final String[] TYPES = {
        "Attestation de scolarité",
        "Attestation de réussite",
        "Attestation de stage",
        "Attestation de bourse",
        "Attestation de fréquentation",
        "Attestation de paiement"
    };

    public static String render(Etudiant etudiant,
                                Institution institution,
                                List<Attestation> attestations,
                                String optionEtudiant,
                                String message,
                                String typeMessage) {
        try {
            StringBuilder sb = new StringBuilder();
            
            sb.append("<!DOCTYPE html>");
            sb.append("<html lang=\"fr\">");
            sb.append("<head>");
            sb.append("<meta charset=\"UTF-8\">");
            sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\">");
            sb.append("<title>Mes Attestations</title>");
            sb.append("<link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css\">");
            sb.append("<link href=\"https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@300;400;500;600;700;800&display=swap\" rel=\"stylesheet\">");
            sb.append("<style>");
            sb.append(renderStyles());
            sb.append("</style>");
            sb.append("</head>");
            sb.append("<body>");

            // ===== CONTENEUR PRINCIPAL =====
            sb.append("<div class=\"container\">");

            // ===== EN-TÊTE AVEC LOGO =====
            sb.append(renderHeader(etudiant, institution));

            // ===== MESSAGE =====
            if (message != null && !message.isEmpty()) {
                String cls = "success".equals(typeMessage) ? "success" : "error";
                sb.append("<div class=\"message ").append(cls).append("\">");
                sb.append("<i class=\"fas fa-").append(cls.equals("success") ? "check-circle" : "exclamation-triangle").append("\"></i> ");
                sb.append(escapeHtml(message));
                sb.append("</div>");
            }

            // ===== INFORMATIONS ÉTUDIANT =====
            sb.append("<div class=\"info-box\">");
            sb.append("<i class=\"fas fa-user-graduate\"></i> ");
            if (etudiant != null) {
                sb.append("<strong>").append(escapeHtml(etudiant.getNomComplet())).append("</strong>");
                sb.append(" | Classe : <strong>").append(escapeHtml(etudiant.getClasse())).append("</strong>");
                sb.append(" | Année : <strong>").append(escapeHtml(etudiant.getAnneeAcademique())).append("</strong>");
                sb.append(" | Période : <strong>").append(escapeHtml(etudiant.getPeriode())).append("</strong>");
                if (optionEtudiant != null && !optionEtudiant.isEmpty()) {
                    sb.append(" | Option : <strong>").append(escapeHtml(optionEtudiant)).append("</strong>");
                }
            }
            sb.append("</div>");

            // ===== FORMULAIRE DE GÉNÉRATION =====
            sb.append("<div class=\"card\">");
            sb.append("<div class=\"card-title\"><i class=\"fas fa-file-alt\"></i> Générer une nouvelle attestation</div>");
            sb.append("<form method=\"POST\" action=\"/etudiant/attestations\" class=\"attestation-form\" id=\"attestationForm\">");
            
            // Ligne 1 : Type + Option
            sb.append("<div class=\"form-row\">");
            
            sb.append("<div class=\"form-group\">");
            sb.append("<label for=\"type\">Type d'attestation *</label>");
            sb.append("<select name=\"type\" id=\"type\" required>");
            sb.append("<option value=\"\">-- Sélectionner --</option>");
            for (String t : TYPES) {
                sb.append("<option value=\"").append(escapeHtml(t)).append("\">").append(escapeHtml(t)).append("</option>");
            }
            sb.append("</select>");
            sb.append("</div>");
            
            sb.append("<div class=\"form-group\">");
            sb.append("<label for=\"option\">Option</label>");
            String optionValue = optionEtudiant != null ? optionEtudiant : "";
            sb.append("<input type=\"text\" id=\"option\" value=\"").append(escapeHtml(optionValue)).append("\" disabled style=\"background:#e9ecef;\">");
            sb.append("<input type=\"hidden\" name=\"option\" value=\"").append(escapeHtml(optionValue)).append("\">");
            sb.append("</div>");
            
            sb.append("</div>");

            // Ligne 2 : Référence + Numéro
            sb.append("<div class=\"form-row\">");
            
            sb.append("<div class=\"form-group\">");
            sb.append("<label for=\"reference\">Référence</label>");
            String reference = "ATT-" + System.currentTimeMillis();
            sb.append("<input type=\"text\" name=\"reference\" id=\"reference\" value=\"").append(reference).append("\">");
            sb.append("</div>");
            
            sb.append("<div class=\"form-group\">");
            sb.append("<label for=\"numero\">Numéro</label>");
            String numero = String.valueOf(System.currentTimeMillis() % 100000);
            sb.append("<input type=\"text\" name=\"numero\" id=\"numero\" value=\"").append(numero).append("\">");
            sb.append("</div>");
            
            sb.append("</div>");

            // Ligne 3 : Date + Boutons
            sb.append("<div class=\"form-row\">");
            
            sb.append("<div class=\"form-group\">");
            sb.append("<label for=\"date\">Date d'émission</label>");
            sb.append("<input type=\"date\" name=\"date\" id=\"date\" value=\"").append(java.time.LocalDate.now()).append("\">");
            sb.append("</div>");
            
            sb.append("<div class=\"form-group\" style=\"display:flex; align-items:flex-end; gap:10px;\">");
            sb.append("<button type=\"button\" class=\"btn-preview\" onclick=\"apercuAttestation()\"><i class=\"fas fa-eye\"></i> Aperçu</button>");
            sb.append("<button type=\"submit\" class=\"btn-generate\"><i class=\"fas fa-file-pdf\"></i> Générer</button>");
            sb.append("</div>");
            
            sb.append("</div>");
            
            sb.append("</form>");
            sb.append("</div>");

            // ===== ZONE D'APERÇU =====
            sb.append("<div class=\"card\" id=\"previewCard\" style=\"display:none;\">");
            sb.append("<div class=\"card-title\"><i class=\"fas fa-eye\"></i> Aperçu de l'attestation</div>");
            sb.append("<div class=\"preview-container\">");
            sb.append("<div id=\"previewLoading\" style=\"display:none; text-align:center; padding:30px;\">");
            sb.append("<i class=\"fas fa-spinner fa-spin\" style=\"font-size:36px; color:#3b82f6;\"></i>");
            sb.append("<p style=\"margin-top:10px;\">Génération de l'aperçu...</p>");
            sb.append("</div>");
            sb.append("<img id=\"previewImage\" src=\"\" alt=\"Aperçu\" style=\"display:none; max-width:100%; max-height:650px; margin:0 auto;\">");
            sb.append("</div>");
            sb.append("</div>");

            // ===== LISTE DES ATTESTATIONS =====
            sb.append("<div class=\"card\">");
            sb.append("<div class=\"card-title\"><i class=\"fas fa-list\"></i> Mes attestations</div>");
            if (attestations == null || attestations.isEmpty()) {
                sb.append("<div class=\"empty-state\">");
                sb.append("<i class=\"fas fa-file-alt\"></i>");
                sb.append("<p>Aucune attestation pour le moment.</p>");
                sb.append("</div>");
            } else {
                sb.append("<div class=\"table-wrapper\">");
                sb.append("<table>");
                sb.append("<thead>");
                sb.append("<tr>");
                sb.append("<th>N°</th>");
                sb.append("<th>Réf.</th>");
                sb.append("<th>Type</th>");
                sb.append("<th>Date</th>");
                sb.append("<th>Classe</th>");
                sb.append("<th>Option</th>");
                sb.append("<th>Action</th>");
                sb.append("</tr>");
                sb.append("</thead>");
                sb.append("<tbody>");
                
                for (Attestation a : attestations) {
                    try {
                        sb.append("<tr>");
                        sb.append("<td>").append(escapeHtml(a.getNumero())).append("</td>");
                        sb.append("<td>").append(escapeHtml(a.getReference())).append("</td>");
                        sb.append("<td>").append(escapeHtml(a.getType())).append("</td>");
                        
                        String dateStr = "-";
                        if (a.getDateEmission() != null) {
                            try {
                                dateStr = DATE_FORMAT.format(a.getDateEmission().toInstant().atZone(java.time.ZoneId.systemDefault()));
                            } catch (Exception e) {
                                dateStr = a.getDateEmission().toString();
                            }
                        }
                        sb.append("<td>").append(dateStr).append("</td>");
                        
                        sb.append("<td>").append(escapeHtml(a.getClasse())).append("</td>");
                        sb.append("<td>").append(escapeHtml(a.getOption())).append("</td>");
                        sb.append("<td>");
                        sb.append("<a href=\"/etudiant/attestations/telecharger?id=").append(a.getId()).append("\" class=\"btn-download\">");
                        sb.append("<i class=\"fas fa-download\"></i>");
                        sb.append("</a>");
                        sb.append("</td>");
                        sb.append("</tr>");
                    } catch (Exception e) {
                        LOGGER.warning(() -> "Erreur sur une attestation: " + e.getMessage());
                    }
                }
                
                sb.append("</tbody>");
                sb.append("</table>");
                sb.append("</div>");
            }
            sb.append("</div>");

            // ===== FOOTER =====
            sb.append("<div class=\"footer\">");
            sb.append("© ").append(Year.now().getValue()).append(" M-TECH Academy - Tous droits réservés");
            sb.append("</div>");

            sb.append("</div>"); // Fin container

            // ===== JAVASCRIPT =====
            sb.append("<script>");
            
            // Variables JavaScript
            String etudiantId = etudiant != null ? etudiant.getNumeroIdentifiantEtudiant() : "";
            String classeEtudiant = etudiant != null ? etudiant.getClasse() : "";
            String anneeEtudiant = etudiant != null ? etudiant.getAnneeAcademique() : "";
            
            sb.append("const ETUDIANT_ID = '").append(escapeJs(etudiantId)).append("';");
            sb.append("const CLASSE_ETUDIANT = '").append(escapeJs(classeEtudiant)).append("';");
            sb.append("const ANNEE_ETUDIANT = '").append(escapeJs(anneeEtudiant)).append("';");
            
            sb.append("""
            function apercuAttestation() {
                const type = document.getElementById('type').value;
                const option = document.getElementById('option').value;
                const date = document.getElementById('date').value;
                const reference = document.getElementById('reference').value;
                const numero = document.getElementById('numero').value;
                
                if (!type) {
                    alert('Veuillez sélectionner un type d\\'attestation.');
                    return;
                }
                
                const previewCard = document.getElementById('previewCard');
                const previewImage = document.getElementById('previewImage');
                const previewLoading = document.getElementById('previewLoading');
                
                previewCard.style.display = 'block';
                previewLoading.style.display = 'block';
                previewImage.style.display = 'none';
                
                const data = {
                    etudiantId: ETUDIANT_ID,
                    type: type,
                    reference: reference,
                    numero: numero,
                    date: date,
                    option: option,
                    classe: CLASSE_ETUDIANT,
                    anneeAcademique: ANNEE_ETUDIANT
                };
                
                fetch('/admin/attestations/api/preview', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(data)
                })
                .then(r => r.json())
                .then(result => {
                    previewLoading.style.display = 'none';
                    if (result.success) {
                        previewImage.src = 'data:image/png;base64,' + result.image;
                        previewImage.style.display = 'block';
                        previewCard.scrollIntoView({ behavior: 'smooth', block: 'start' });
                    } else {
                        alert('Erreur: ' + result.error);
                        previewCard.style.display = 'none';
                    }
                })
                .catch((error) => {
                    previewLoading.style.display = 'none';
                    alert('Erreur réseau lors de la génération de l\\'aperçu');
                    previewCard.style.display = 'none';
                });
            }
            """);
            
            sb.append("</script>");

            sb.append("</body>");
            sb.append("</html>");
            
            return sb.toString();
            
        } catch (Exception e) {
            LOGGER.severe(() -> "ERREUR dans UIAttestationEtudiant.render(): " + e.getMessage());
            return "<html><body><h1>Erreur de rendu</h1><p>" + e.getMessage() + "</p></body></html>";
        }
    }

    public static String renderError(String message) {
        return render(null, null, null, null, message, "error");
    }

    public static String renderMessage(String message, String type) {
        return render(null, null, null, null, message, type);
    }

    // ============================================================
    // EN-TÊTE AVEC LOGO
    // ============================================================

private static String renderHeader(Etudiant etudiant, Institution institution) {
    StringBuilder sb = new StringBuilder();
    System.out.println("Etudiant "+etudiant);
    sb.append("<div class=\"header\">");
    
    // Logo de l'institution - version Base64 direct
    if (institution != null && institution.getLogoInstitution() != null && !institution.getLogoInstitution().isEmpty()) {
        String logoBase64 = encodeImageToBase64(institution.getLogoInstitution());
        if (logoBase64 != null) {
            sb.append("<img src=\"").append(logoBase64).append("\" alt=\"Logo\" class=\"header-logo\">");
        } else {
            // Fallback : essayer de charger via URL
            String logoPath = institution.getLogoInstitution();
            String logoFileName = logoPath.substring(logoPath.lastIndexOf(File.separator) + 1);
            sb.append("<img src=\"/logos/").append(logoFileName).append("\" alt=\"Logo\" class=\"header-logo\">");
        }
    }
    
    sb.append("<h1><i class=\"fas fa-file-alt\"></i> Mes Attestations</h1>");
    
    if (institution != null && institution.getNomInstitution() != null) {
        sb.append("<div class=\"header-sub\">");
        sb.append(escapeHtml(institution.getNomInstitution()));
        if (institution.getSigleInstitution() != null && !institution.getSigleInstitution().isEmpty()) {
            sb.append(" (").append(escapeHtml(institution.getSigleInstitution())).append(")");
        }
        sb.append("</div>");
    }
    
    sb.append("<div class=\"header-nav\">");
    sb.append("<a href=\"/etudiant/dashboard\"><i class=\"fas fa-home\"></i> Dashboard</a>");
    sb.append("<a href=\"/etudiant/profil\"><i class=\"fas fa-user\"></i> Profil</a>");
    sb.append("<a href=\"/etudiant/notes\"><i class=\"fas fa-book\"></i> Notes</a>");
    sb.append("<a href=\"/etudiant/bulletins\"><i class=\"fas fa-file-pdf\"></i> Bulletins</a>");
    sb.append("<a href=\"/etudiant/paiements\"><i class=\"fas fa-coins\"></i> Paiements</a>");
    sb.append("<a href=\"/etudiant/attestations\" class=\"active\"><i class=\"fas fa-file-alt\"></i> Attestations</a>");
    sb.append("<a href=\"/logout\"><i class=\"fas fa-sign-out-alt\"></i> Déconnexion</a>");
    sb.append("</div>");
    sb.append("</div>");
    return sb.toString();
}
    // ============================================================
    // ENCODAGE DU LOGO EN BASE64
    // ============================================================

  // Dans UIAttestationEtudiant.java

private static String encodeImageToBase64(String imagePath) {
    if (imagePath == null || imagePath.isEmpty()) return null;
    
    try {
        // Nettoyer le chemin
        String cleanPath = imagePath;
        
        // Si le chemin commence par /, enlever le slash
        if (cleanPath.startsWith("/")) {
            cleanPath = cleanPath.substring(1);
        }
        
        // Si le chemin commence par data/photos, le garder tel quel
        // Sinon, essayer de reconstruire
        File file ;
        
        // 1. Essayer le chemin tel quel
        file = new File(cleanPath);
        if (file.exists()) {
            return encodeFile(file);
        }
        
        // 2. Essayer avec data/photos/
        file = new File("data/photos/" + cleanPath);
        if (file.exists()) {
            return encodeFile(file);
        }
        
        // 3. Essayer avec data/uploads/photos/
        file = new File("data/uploads/photos/" + cleanPath);
        if (file.exists()) {
            return encodeFile(file);
        }
        
        // 4. Essayer avec le nom du fichier seulement
        String fileName = cleanPath.substring(cleanPath.lastIndexOf(File.separator) + 1);
        String[] directories = {"data/photos", "data/uploads/photos", "photos", "uploads/photos", "logos", "images"};
        for (String dir : directories) {
            file = new File(dir + File.separator + fileName);
            if (file.exists()) {
                return encodeFile(file);
            }
        }
        
        LOGGER.warning(() -> "Fichier logo introuvable: " + imagePath);
        return null;
        
    } catch (IOException e) {
        LOGGER.warning(() -> "Impossible d'encoder le logo en Base64: " + e.getMessage());
        return null;
    }
}

private static String encodeFile(File file) throws IOException {
    byte[] bytes = Files.readAllBytes(file.toPath());
    String base64 = Base64.getEncoder().encodeToString(bytes);
    
    // Déterminer le type MIME
    String mimeType = getMimeTypeFromFile(file);
    
    return "data:" + mimeType + ";base64," + base64;
}

private static String getMimeTypeFromFile(File file) {
    String name = file.getName().toLowerCase();
    if (name.endsWith(".png")) return "image/png";
    if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
    if (name.endsWith(".webp")) return "image/webp";
    if (name.endsWith(".gif")) return "image/gif";
    if (name.endsWith(".svg")) return "image/svg+xml";
    return "image/png";
}

    // ============================================================
    // STYLES CSS
    // ============================================================

    private static String renderStyles() {
        return """
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }
        html, body {
            width: 100%;
            min-height: 100vh;
            font-family: 'Plus Jakarta Sans', sans-serif;
            background: #f4f6f9;
        }
        body {
            padding: 0;
            display: flex;
            flex-direction: column;
            align-items: stretch;
        }
        
        /* ===== CONTENEUR ===== */
        .container {
            max-width: 1400px;
            margin: 0 auto;
            padding: 20px;
            width: 100%;
            flex: 1;
        }
        
        /* ===== EN-TÊTE ===== */
        .header {
            background: linear-gradient(135deg, #1e293b, #0f172a);
            color: white;
            padding: 15px 30px 12px 30px;
            border-radius: 12px;
            text-align: center;
            margin-bottom: 15px;
            box-shadow: 0 4px 20px rgba(0,0,0,0.15);
        }
        .header-logo {
            max-height: 60px;
            border-radius: 8px;
            margin-bottom: 5px;
            display: block;
            margin-left: auto;
            margin-right: auto;
        }
        .header h1 {
            font-size: 22px;
            font-weight: 700;
            margin: 0;
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 10px;
        }
        .header h1 i {
            color: #f5cd79;
        }
        .header-sub {
            font-size: 13px;
            color: #94a3b8;
            font-style: italic;
            margin-top: 2px;
        }
        .header-nav {
            display: flex;
            justify-content: center;
            gap: 8px;
            margin-top: 8px;
            flex-wrap: wrap;
        }
        .header-nav a {
            color: #94a3b8;
            text-decoration: none;
            font-size: 13px;
            transition: 0.2s;
            padding: 5px 14px;
            border-radius: 20px;
            background: rgba(255,255,255,0.06);
        }
        .header-nav a:hover {
            color: white;
            background: rgba(255,255,255,0.14);
        }
        .header-nav a.active {
            color: white;
            background: rgba(255,255,255,0.14);
            font-weight: 600;
        }
        
        /* ===== MESSAGE ===== */
        .message {
            padding: 12px 18px;
            border-radius: 10px;
            margin-bottom: 15px;
            font-size: 14px;
            display: flex;
            align-items: center;
            gap: 10px;
            border-left: 4px solid;
        }
        .message.success {
            background: #d1fae5;
            color: #16a34a;
            border-left-color: #16a34a;
        }
        .message.error {
            background: #fee2e2;
            color: #dc2626;
            border-left-color: #dc2626;
        }
        
        /* ===== INFO BOX ===== */
        .info-box {
            background: #e0edff;
            border-radius: 10px;
            padding: 12px 20px;
            margin-bottom: 15px;
            border-left: 4px solid #1e40af;
            border: 1px solid #e2e8f0;
            font-size: 14px;
            color: #334155;
            display: flex;
            flex-wrap: wrap;
            gap: 6px 20px;
        }
        .info-box i {
            color: #1e40af;
            margin-right: 6px;
        }
        
        /* ===== CARDS ===== */
        .card {
            background: white;
            border-radius: 12px;
            padding: 20px 25px;
            margin-bottom: 15px;
            border: 1px solid #e2e8f0;
            box-shadow: 0 2px 8px rgba(0,0,0,0.04);
        }
        .card-title {
            font-size: 17px;
            font-weight: 600;
            color: #1e40af;
            margin-bottom: 15px;
            border-bottom: 2px solid #1e40af;
            padding-bottom: 6px;
            display: inline-block;
        }
        
        /* ===== FORMULAIRE ===== */
        .form-row {
            display: flex;
            gap: 15px;
            flex-wrap: wrap;
            margin-bottom: 12px;
        }
        .form-group {
            flex: 1;
            min-width: 180px;
        }
        .form-group label {
            display: block;
            font-weight: 600;
            color: #475569;
            margin-bottom: 4px;
            font-size: 13px;
        }
        .form-group select,
        .form-group input {
            width: 100%;
            padding: 9px 14px;
            border: 1px solid #cbd5e1;
            border-radius: 8px;
            font-size: 14px;
            background: white;
            transition: border-color 0.2s;
        }
        .form-group select:focus,
        .form-group input:focus {
            border-color: #1e40af;
            outline: none;
            box-shadow: 0 0 0 3px rgba(30,64,175,0.1);
        }
        .form-group input:disabled {
            background: #e9ecef;
            cursor: not-allowed;
        }
        
        /* ===== BOUTONS ===== */
        .btn-preview {
            background: #3b82f6;
            color: white;
            border: none;
            padding: 9px 24px;
            border-radius: 8px;
            cursor: pointer;
            font-weight: 600;
            font-size: 14px;
            transition: 0.2s;
            display: inline-flex;
            align-items: center;
            gap: 8px;
            height: 42px;
            white-space: nowrap;
        }
        .btn-preview:hover {
            background: #2563eb;
            transform: translateY(-1px);
        }
        .btn-generate {
            background: #16a34a;
            color: white;
            border: none;
            padding: 9px 24px;
            border-radius: 8px;
            cursor: pointer;
            font-weight: 600;
            font-size: 14px;
            transition: 0.2s;
            display: inline-flex;
            align-items: center;
            gap: 8px;
            height: 42px;
            white-space: nowrap;
        }
        .btn-generate:hover {
            background: #15803d;
            transform: translateY(-1px);
        }
        
        /* ===== APERÇU ===== */
        .preview-container {
            background: #f8fafc;
            border-radius: 10px;
            padding: 15px;
            min-height: 200px;
            display: flex;
            justify-content: center;
            align-items: center;
            border: 1px dashed #cbd5e1;
        }
        .preview-container img {
            border-radius: 6px;
            box-shadow: 0 4px 12px rgba(0,0,0,0.08);
        }
        
        /* ===== TABLEAU ===== */
        .table-wrapper {
            overflow-x: auto;
            margin: -5px;
        }
        table {
            width: 100%;
            border-collapse: collapse;
            font-size: 14px;
        }
        th, td {
            padding: 10px 12px;
            text-align: left;
            border-bottom: 1px solid #e2e8f0;
        }
        th {
            background: #f8fafc;
            font-weight: 600;
            color: #475569;
            white-space: nowrap;
        }
        tr:hover {
            background: #f8fafc;
        }
        .btn-download {
            background: #1e40af;
            color: white;
            padding: 6px 12px;
            border-radius: 6px;
            text-decoration: none;
            display: inline-flex;
            align-items: center;
            gap: 4px;
            font-size: 14px;
            transition: 0.2s;
        }
        .btn-download:hover {
            background: #1e3a8a;
            transform: scale(1.05);
        }
        
        /* ===== EMPTY STATE ===== */
        .empty-state {
            text-align: center;
            padding: 30px;
            color: #64748b;
        }
        .empty-state i {
            font-size: 42px;
            color: #94a3b8;
            margin-bottom: 10px;
            display: block;
        }
        .empty-state p {
            font-size: 15px;
        }
        
        /* ===== FOOTER ===== */
        .footer {
            text-align: center;
            color: #64748b;
            font-size: 13px;
            padding: 15px 0 5px 0;
            border-top: 1px solid rgba(0,0,0,0.06);
            margin-top: 10px;
        }
        
        /* ===== RESPONSIVE ===== */
        @media (max-width: 768px) {
            .container { padding: 12px; }
            .header { padding: 12px 15px; }
            .header h1 { font-size: 18px; }
            .header-nav { flex-direction: column; gap: 4px; }
            .header-nav a { padding: 4px 12px; font-size: 12px; }
            .card { padding: 15px; }
            .form-row { flex-direction: column; gap: 10px; }
            .form-group { min-width: 100%; }
            .btn-preview, .btn-generate { width: 100%; justify-content: center; }
            .info-box { flex-direction: column; gap: 4px; }
            th, td { padding: 8px 10px; font-size: 12px; }
        }
        """;
    }

    // ============================================================
    // MÉTHODES D'ÉCHAPPEMENT
    // ============================================================

    private static String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }

    private static String escapeJs(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("'", "\\'")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }
}