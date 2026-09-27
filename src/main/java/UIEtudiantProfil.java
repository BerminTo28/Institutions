import java.text.SimpleDateFormat;
import java.time.Year;
import java.util.Date;

public class UIEtudiantProfil {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy");

    public static String render(Etudiant etudiant) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>");
        sb.append("<html lang=\"fr\">");
        sb.append("<head>");
        sb.append("<meta charset=\"UTF-8\">");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        sb.append("<title>Mon Profil</title>");
        sb.append("<link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css\">");
        sb.append("<link href=\"https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@300;400;500;600;700;800&display=swap\" rel=\"stylesheet\">");
        sb.append("<style>");
        sb.append(renderStyles());
        sb.append("</style>");
        sb.append("</head>");
        sb.append("<body>");
        sb.append("<div class=\"container\">");

        // En-tête
        sb.append(renderHeader());

        // Profil
        sb.append("<div class=\"card\">");
        sb.append("<div class=\"profile-header\">");
        sb.append("<div class=\"avatar\">");
        sb.append("<i class=\"fas fa-user-graduate fa-5x\"></i>");
        sb.append("</div>");
        sb.append("<div class=\"profile-name\">");
        sb.append("<h2>").append(escapeHtml(etudiant.getNomComplet())).append("</h2>");
        sb.append("<p class=\"subtitle\">").append(escapeHtml(etudiant.getClasse())).append("</p>");
        sb.append("</div>");
        sb.append("</div>");

        // Informations
        sb.append("<div class=\"info-grid\">");

        // Identité
        sb.append("<div class=\"info-group\">");
        sb.append("<h3><i class=\"fas fa-id-card\"></i> Identité</h3>");
        sb.append("<div class=\"info-row\"><span class=\"label\">Identifiant</span><span class=\"value\">").append(escapeHtml(etudiant.getNumeroIdentifiantEtudiant())).append("</span></div>");
        sb.append("<div class=\"info-row\"><span class=\"label\">Matricule</span><span class=\"value\">").append(escapeHtml(etudiant.getMatricule())).append("</span></div>");
        sb.append("<div class=\"info-row\"><span class=\"label\">NINU</span><span class=\"value\">").append(escapeHtml(etudiant.getNinu())).append("</span></div>");
        sb.append("<div class=\"info-row\"><span class=\"label\">Sexe</span><span class=\"value\">").append(escapeHtml(etudiant.getSexe())).append("</span></div>");
        sb.append("<div class=\"info-row\"><span class=\"label\">Date de naissance</span><span class=\"value\">").append(formatDate(etudiant.getDateNaissance())).append("</span></div>");
        sb.append("<div class=\"info-row\"><span class=\"label\">Lieu de naissance</span><span class=\"value\">").append(escapeHtml(etudiant.getCommuneNaissance())).append(", ").append(escapeHtml(etudiant.getDepartementNaissance())).append("</span></div>");
        sb.append("<div class=\"info-row\"><span class=\"label\">Groupe sanguin</span><span class=\"value\">").append(escapeHtml(etudiant.getGroupeSanguin())).append("</span></div>");
        sb.append("</div>");

        // Scolarité
        sb.append("<div class=\"info-group\">");
        sb.append("<h3><i class=\"fas fa-graduation-cap\"></i> Scolarité</h3>");
        sb.append("<div class=\"info-row\"><span class=\"label\">Classe</span><span class=\"value\">").append(escapeHtml(etudiant.getClasse())).append("</span></div>");
        sb.append("<div class=\"info-row\"><span class=\"label\">Année académique</span><span class=\"value\">").append(escapeHtml(etudiant.getAnneeAcademique())).append("</span></div>");
        sb.append("<div class=\"info-row\"><span class=\"label\">Période</span><span class=\"value\">").append(escapeHtml(etudiant.getPeriode())).append("</span></div>");
        sb.append("<div class=\"info-row\"><span class=\"label\">Promotion</span><span class=\"value\">").append(escapeHtml(etudiant.getPromotion())).append("</span></div>");
        sb.append("<div class=\"info-row\"><span class=\"label\">Statut</span><span class=\"value\">").append(escapeHtml(etudiant.getStatut())).append("</span></div>");
        sb.append("<div class=\"info-row\"><span class=\"label\">Date d'inscription</span><span class=\"value\">").append(formatDate(etudiant.getDateInscription())).append("</span></div>");
        sb.append("</div>");

        // Contacts
        sb.append("<div class=\"info-group\">");
        sb.append("<h3><i class=\"fas fa-address-book\"></i> Contacts</h3>");
        sb.append("<div class=\"info-row\"><span class=\"label\">Téléphone</span><span class=\"value\">").append(escapeHtml(etudiant.getTelephone())).append("</span></div>");
        sb.append("<div class=\"info-row\"><span class=\"label\">Email</span><span class=\"value\">").append(escapeHtml(etudiant.getEmail())).append("</span></div>");
        sb.append("<div class=\"info-row\"><span class=\"label\">Adresse</span><span class=\"value\">").append(escapeHtml(etudiant.getAdresse())).append("</span></div>");
        sb.append("<div class=\"info-row\"><span class=\"label\">Téléphone parents</span><span class=\"value\">").append(escapeHtml(etudiant.getTelephoneParents())).append("</span></div>");
        sb.append("</div>");

        // Famille / Responsable
        sb.append("<div class=\"info-group\">");
        sb.append("<h3><i class=\"fas fa-users\"></i> Famille / Responsable</h3>");
        sb.append("<div class=\"info-row\"><span class=\"label\">Père</span><span class=\"value\">").append(escapeHtml(etudiant.getPrenomPere())).append(" ").append(escapeHtml(etudiant.getNomPere())).append("</span></div>");
        sb.append("<div class=\"info-row\"><span class=\"label\">Mère</span><span class=\"value\">").append(escapeHtml(etudiant.getPrenomMere())).append(" ").append(escapeHtml(etudiant.getNomMere())).append("</span></div>");
        if (etudiant.getNomResponsable() != null && !etudiant.getNomResponsable().isEmpty()) {
            sb.append("<div class=\"info-row\"><span class=\"label\">Responsable légal</span><span class=\"value\">").append(escapeHtml(etudiant.getPrenomResponsable())).append(" ").append(escapeHtml(etudiant.getNomResponsable())).append(" (").append(escapeHtml(etudiant.getTypeResponsable())).append(")</span></div>");
        }
        sb.append("<div class=\"info-row\"><span class=\"label\">Lien parenté</span><span class=\"value\">").append(escapeHtml(etudiant.getLienParente())).append("</span></div>");
        sb.append("<div class=\"info-row\"><span class=\"label\">Résidence parents</span><span class=\"value\">").append(escapeHtml(etudiant.getResidenceParents())).append("</span></div>");
        sb.append("</div>");

        sb.append("</div>"); // fin info-grid

        sb.append("</div>"); // fin card

        sb.append("<div class=\"footer\">© ").append(Year.now().getValue()).append(" M-TECH Academy - Tous droits réservés</div>");
        sb.append("</div>");
        sb.append("</body>");
        sb.append("</html>");
        return sb.toString();
    }

    public static String renderError(String message) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>");
        sb.append("<html>");
        sb.append("<head><meta charset=\"UTF-8\"><title>Erreur</title>");
        sb.append("<link href=\"https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;600&display=swap\" rel=\"stylesheet\">");
        sb.append("<style>");
        sb.append("*{margin:0;padding:0;box-sizing:border-box;font-family:'Plus Jakarta Sans',sans-serif;}");
        sb.append("body{background:#f4f6f9;display:flex;justify-content:center;align-items:center;height:100vh;}");
        sb.append(".error-container{background:white;padding:40px;border-radius:16px;text-align:center;max-width:500px;}");
        sb.append(".error-container i{font-size:64px;color:#dc2626;margin-bottom:20px;}");
        sb.append(".error-container h1{color:#1e293b;margin-bottom:10px;}");
        sb.append(".error-container p{color:#64748b;}");
        sb.append(".error-container .btn{display:inline-block;margin-top:20px;padding:10px 24px;background:#1e40af;color:white;text-decoration:none;border-radius:8px;}");
        sb.append("</style>");
        sb.append("</head>");
        sb.append("<body>");
        sb.append("<div class=\"error-container\">");
        sb.append("<i class=\"fas fa-exclamation-triangle\"></i>");
        sb.append("<h1>Erreur</h1>");
        sb.append("<p>").append(escapeHtml(message)).append("</p>");
        sb.append("<a href=\"/etudiant/dashboard\" class=\"btn\"><i class=\"fas fa-arrow-left\"></i> Retour</a>");
        sb.append("</div>");
        sb.append("</body>");
        sb.append("</html>");
        return sb.toString();
    }

    private static String renderStyles() {
        return """
        *{margin:0;padding:0;box-sizing:border-box;}
        html, body { width: 100%; min-height: 100vh; background: linear-gradient(135deg, #f0f4f8 0%, #d9e2ec 100%); font-family:'Plus Jakarta Sans',sans-serif; }
        body{padding:0;display:flex;flex-direction:column;}
        .container{width:100%;min-height:100vh;margin:0;background:rgba(255,255,255,0.85);backdrop-filter:blur(10px);border-radius:0;box-shadow:none;border:none;display:flex;flex-direction:column;overflow:hidden;}

        .header{background:linear-gradient(135deg,#1e293b,#0f172a);color:white;padding:30px 40px 24px;border-radius:0;text-align:center;margin-bottom:0;position:relative;overflow:hidden;width:100%;}
        .header h1{font-size:24px;font-weight:700;margin-top:8px;position:relative;z-index:1;}
        .header h1 i{color:#3b82f6;margin-right:10px;}
        .header-nav{display:flex;justify-content:center;gap:15px;margin-top:12px;flex-wrap:wrap;position:relative;z-index:1;}
        .header-nav a{color:#94a3b8;text-decoration:none;font-size:13px;transition:0.2s;padding:6px 14px;border-radius:20px;background:rgba(255,255,255,0.08);}
        .header-nav a:hover{color:white;background:rgba(255,255,255,0.16);}
        .header-nav a.active{color:#f5cd79;background:rgba(255,255,255,0.16);font-weight:600;}

        .card{background:white;border-radius:0;padding:40px;border:none;box-shadow:none;width:100%;max-width:1600px;margin:0 auto;flex:1;}

        .profile-header{display:flex;align-items:center;gap:25px;margin-bottom:30px;padding-bottom:20px;border-bottom:2px solid #e2e8f0;}
        .avatar{background:#e2e8f0;width:100px;height:100px;border-radius:50%;display:flex;align-items:center;justify-content:center;color:#1e40af;}
        .profile-name h2{font-size:28px;color:#1e293b;margin-bottom:4px;}
        .profile-name .subtitle{font-size:16px;color:#64748b;}

        .info-grid{display:grid;grid-template-columns:repeat(2, minmax(0, 1fr));gap:30px;}
        .info-group{background:#f8fafc;border-radius:12px;padding:20px;border:1px solid #e2e8f0;}
        .info-group h3{font-size:16px;color:#1e40af;margin-bottom:15px;border-bottom:2px solid #1e40af;padding-bottom:8px;display:inline-block;}
        .info-row{display:flex;padding:6px 0;border-bottom:1px solid #e2e8f0;}
        .info-row:last-child{border-bottom:none;}
        .info-row .label{font-weight:600;color:#475569;width:150px;flex-shrink:0;}
        .info-row .value{color:#0f172a;word-break:break-word;}

        .footer{margin-top:auto;text-align:center;color:#64748b;font-size:13px;border-top:1px solid rgba(0,0,0,0.06);padding:25px 0 15px;width:100%;}
        @media (max-width:1200px){.info-grid{grid-template-columns:repeat(2, minmax(0, 1fr));}}
        @media (max-width:900px){.profile-header{flex-direction:column;text-align:center;}.info-grid{grid-template-columns:1fr;}}
        """;
    }

    private static String renderHeader() {
        return """
        <div class="header">
            <h1><i class="fas fa-user"></i> Mon Profil</h1>
            <div class="header-nav">
                <a href="/etudiant/dashboard"><i class="fas fa-home"></i> Dashboard</a>
                <a href="/etudiant/profil" class="active"><i class="fas fa-user"></i> Mon Profil</a>
                <a href="/etudiant/notes"><i class="fas fa-book"></i> Mes Notes</a>
                <a href="/etudiant/bulletins"><i class="fas fa-file-pdf"></i> Bulletins</a>
                <a href="/etudiant/paiements"><i class="fas fa-coins"></i> Paiements</a>
                <a href="/etudiant/emploi-du-temps"><i class="fas fa-calendar-alt"></i> Emploi du temps</a>
                <a href="/logout"><i class="fas fa-sign-out-alt"></i> Déconnexion</a>
            </div>
        </div>
        """;
    }

    private static String formatDate(Date date) {
        if (date == null) return "-";
        return DATE_FORMAT.format(date);
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