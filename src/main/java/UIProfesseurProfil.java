import java.time.Year;
import java.util.List;

public class UIProfesseurProfil {

    private static final String DEFAULT_AVATAR = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='150' height='150'%3E%3Crect width='150' height='150' fill='%231e40af'/%3E%3Ctext x='75' y='95' font-size='60' text-anchor='middle' fill='white' font-family='sans-serif'%3E👨‍🏫%3C/text%3E%3C/svg%3E";

    /**
     * Rend la page de profil du professeur.
     */
    public static String render(Professeur professeur) {
        if (professeur == null) {
            return renderError("Professeur non trouvé.");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>");
        sb.append("<html lang=\"fr\">");
        sb.append("<head>");
        sb.append("<meta charset=\"UTF-8\">");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        sb.append("<title>Mon Profil - Professeur</title>");
        sb.append("<link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css\">");
        sb.append("<link href=\"https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@300;400;500;600;700;800&display=swap\" rel=\"stylesheet\">");
        sb.append("<style>");
        sb.append(renderStyles());
        sb.append("</style>");
        sb.append("</head>");
        sb.append("<body>");
        sb.append("<div class=\"container\">");

        // ===== HEADER =====
        sb.append(renderHeader(professeur));

        // ===== CONTENU PRINCIPAL =====
        sb.append("<div class=\"main-content\">");
        sb.append("<div class=\"profile-grid\">");

        // Carte d'identité
        sb.append(renderIdentityCard(professeur));

        // Informations personnelles
        sb.append(renderPersonalInfo(professeur));

        // Informations professionnelles
        sb.append(renderProfessionalInfo(professeur));

        // Créneaux / Cours
        sb.append(renderCreneaux(professeur.getCreneaux()));

        // Statistiques
        sb.append(renderStats(professeur));

        sb.append("</div>"); // fin profile-grid
        sb.append("</div>"); // fin main-content

        sb.append("<div class=\"footer\">");
        sb.append("© ").append(Year.now().getValue()).append(" M-TECH Academy - Tous droits réservés");
        sb.append("</div>");

        sb.append("</div>"); // fin container
        sb.append("</body>");
        sb.append("</html>");

        return sb.toString();
    }

    /**
     * Rend une page d'erreur.
     */
    public static String renderError(String message) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>");
        sb.append("<html lang=\"fr\">");
        sb.append("<head>");
        sb.append("<meta charset=\"UTF-8\">");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        sb.append("<title>Erreur - Profil Professeur</title>");
        sb.append("<link href=\"https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;600&display=swap\" rel=\"stylesheet\">");
        sb.append("<style>");
        sb.append("* { margin:0; padding:0; box-sizing:border-box; font-family:'Plus Jakarta Sans',sans-serif; }");
        sb.append("body { background:#f4f6f9; display:flex; justify-content:center; align-items:center; height:100vh; }");
        sb.append(".error-container { background:white; padding:40px; border-radius:16px; text-align:center; max-width:500px; }");
        sb.append(".error-container i { font-size:64px; color:#dc2626; margin-bottom:20px; }");
        sb.append(".error-container h1 { color:#1e293b; margin-bottom:10px; }");
        sb.append(".error-container p { color:#64748b; }");
        sb.append(".error-container .btn { display:inline-block; margin-top:20px; padding:10px 24px; background:#1e40af; color:white; text-decoration:none; border-radius:8px; }");
        sb.append(".error-container .btn:hover { background:#1e3a8a; }");
        sb.append("</style>");
        sb.append("</head>");
        sb.append("<body>");
        sb.append("<div class=\"error-container\">");
        sb.append("<i class=\"fas fa-exclamation-triangle\"></i>");
        sb.append("<h1>Erreur</h1>");
        sb.append("<p>").append(echapperHtml(message)).append("</p>");
        sb.append("<a href=\"/dashboard/professeur\" class=\"btn\"><i class=\"fas fa-arrow-left\"></i> Retour au tableau de bord</a>");
        sb.append("</div>");
        sb.append("</body>");
        sb.append("</html>");
        return sb.toString();
    }

    // ==================== STYLES ====================

    private static String renderStyles() {
        return """
        * { margin:0; padding:0; box-sizing:border-box; }
        body { font-family:'Plus Jakarta Sans',sans-serif; background:#f0f4f8; min-height:100vh; padding:20px; }
        .container { max-width:1200px; margin:0 auto; }

        /* HEADER */
        .header { background:linear-gradient(135deg,#1e293b,#0f172a); color:white; padding:30px; border-radius:16px; text-align:center; margin-bottom:30px; position:relative; overflow:hidden; }
        .header::before { content:''; position:absolute; top:-50%; right:-20%; width:300px; height:300px; background:rgba(59,130,246,0.1); border-radius:50%; }
        .header::after { content:''; position:absolute; bottom:-40%; left:-10%; width:200px; height:200px; background:rgba(59,130,246,0.08); border-radius:50%; }
        .header-avatar { width:120px; height:120px; border-radius:50%; border:4px solid #3b82f6; object-fit:cover; display:inline-block; box-shadow:0 8px 30px rgba(59,130,246,0.3); position:relative; z-index:1; }
        .header h1 { font-size:28px; font-weight:700; margin-top:15px; position:relative; z-index:1; }
        .header h1 i { color:#3b82f6; margin-right:10px; }
        .header .subtitle { font-size:16px; color:#94a3b8; margin-top:5px; position:relative; z-index:1; }
        .header .badge { display:inline-block; background:#3b82f6; padding:4px 16px; border-radius:20px; font-size:12px; font-weight:600; margin-top:10px; position:relative; z-index:1; }
        .header-actions { display:flex; justify-content:center; gap:10px; margin-top:15px; position:relative; z-index:1; flex-wrap:wrap; }
        .header-actions .btn { padding:8px 20px; border:none; border-radius:8px; cursor:pointer; font-weight:600; text-decoration:none; display:inline-block; font-size:13px; transition:0.2s; }
        .btn-primary { background:#1e40af; color:white; }
        .btn-primary:hover { background:#1e3a8a; }
        .btn-secondary { background:#334155; color:white; }
        .btn-secondary:hover { background:#1e293b; }

        /* GRILLE */
        .profile-grid { display:grid; grid-template-columns:1fr 1fr; gap:20px; }
        .card { background:white; border-radius:16px; padding:24px; box-shadow:0 2px 8px rgba(0,0,0,0.04); border:1px solid #e2e8f0; transition:0.2s; }
        .card:hover { box-shadow:0 8px 24px rgba(0,0,0,0.06); }
        .card h3 { color:#1e293b; font-size:16px; font-weight:700; margin-bottom:16px; }
        .card h3 i { color:#1e40af; margin-right:8px; }

        /* CARTE IDENTITÉ */
        .identity-card { grid-column:1; grid-row:1; }
        .identity-card .info-item { display:flex; align-items:center; gap:12px; padding:8px 0; border-bottom:1px solid #f1f5f9; }
        .identity-card .info-item:last-child { border-bottom:none; }
        .identity-card .info-item i { width:20px; color:#1e40af; font-size:14px; }
        .identity-card .info-item .label { color:#64748b; font-size:13px; }
        .identity-card .info-item .value { font-weight:600; color:#0f172a; font-size:14px; }

        /* INFO PERSONNELLES */
        .personal-info { grid-column:1; grid-row:2; }
        .personal-info .info-row { display:flex; justify-content:space-between; padding:8px 0; border-bottom:1px solid #f1f5f9; }
        .personal-info .info-row:last-child { border-bottom:none; }
        .personal-info .info-row .label { color:#64748b; font-size:13px; }
        .personal-info .info-row .value { font-weight:500; color:#0f172a; font-size:14px; }

        /* INFO PROFESSIONNELLES */
        .professional-info { grid-column:2; grid-row:1; }
        .professional-info .info-row { display:flex; justify-content:space-between; padding:8px 0; border-bottom:1px solid #f1f5f9; }
        .professional-info .info-row:last-child { border-bottom:none; }
        .professional-info .info-row .label { color:#64748b; font-size:13px; }
        .professional-info .info-row .value { font-weight:500; color:#0f172a; font-size:14px; }

        /* CRÉNEAUX */
        .creneaux { grid-column:2; grid-row:2; }
        .creneau-item { display:flex; justify-content:space-between; align-items:center; padding:10px 12px; background:#f8fafc; border-radius:8px; margin-bottom:8px; border-left:3px solid #1e40af; }
        .creneau-item:last-child { margin-bottom:0; }
        .creneau-item .matiere { font-weight:600; color:#0f172a; font-size:14px; }
        .creneau-item .details { font-size:12px; color:#64748b; }
        .creneau-item .details i { margin-right:4px; }
        .creneau-empty { color:#94a3b8; font-size:14px; text-align:center; padding:20px; }

        /* STATISTIQUES */
        .stats { grid-column:1/3; grid-row:3; }
        .stats-grid { display:grid; grid-template-columns:repeat(auto-fit,minmax(150px,1fr)); gap:15px; }
        .stat-item { text-align:center; padding:15px; background:#f8fafc; border-radius:12px; }
        .stat-item .number { font-size:32px; font-weight:800; color:#1e40af; }
        .stat-item .label { font-size:13px; color:#64748b; margin-top:5px; }

        /* FOOTER */
        .footer { margin-top:30px; text-align:center; color:#94a3b8; font-size:13px; }

        @media (max-width:768px) {
            .profile-grid { grid-template-columns:1fr; }
            .stats { grid-column:1; grid-row:auto; }
            .creneaux { grid-column:1; grid-row:auto; }
            .professional-info { grid-column:1; grid-row:auto; }
            .personal-info { grid-column:1; grid-row:auto; }
            .identity-card { grid-column:1; grid-row:auto; }
        }
        """;
    }

    // ==================== COMPOSANTS HTML ====================

    private static String renderHeader(Professeur p) {
        String avatar = p.getPhotoPath() != null && !p.getPhotoPath().isBlank() 
            ? p.getPhotoPath() 
            : DEFAULT_AVATAR;
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"header\">");
        sb.append("<img src=\"").append(echapperHtml(avatar)).append("\" alt=\"Avatar\" class=\"header-avatar\" onerror=\"this.src='").append(DEFAULT_AVATAR).append("'\">");
        sb.append("<h1><i class=\"fas fa-chalkboard-teacher\"></i> ").append(echapperHtml(p.getNomComplet())).append("</h1>");
        sb.append("<div class=\"subtitle\"><i class=\"fas fa-map-pin\"></i> ").append(echapperHtml(p.getInstitutionId())).append("</div>");
        sb.append("<span class=\"badge\"><i class=\"fas fa-check-circle\"></i> ").append(p.getStatut() != null ? p.getStatut() : "ACTIF").append("</span>");
        sb.append("<div class=\"header-actions\">");
        sb.append("<a href=\"/dashboard/professeur\" class=\"btn btn-secondary\"><i class=\"fas fa-arrow-left\"></i> Retour</a>");
        sb.append("<a href=\"/professeur/profil/edit\" class=\"btn btn-primary\"><i class=\"fas fa-edit\"></i> Modifier</a>");
        sb.append("</div>");
        sb.append("</div>");
        return sb.toString();
    }

    private static String renderIdentityCard(Professeur p) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"card identity-card\">");
        sb.append("<h3><i class=\"fas fa-id-card\"></i> Carte d'identité</h3>");
        sb.append("<div class=\"info-item\"><i class=\"fas fa-qrcode\"></i> <span class=\"label\">Identifiant</span> <span class=\"value\">").append(echapperHtml(p.getNumeroIdentifiantProfesseur())).append("</span></div>");
        sb.append("<div class=\"info-item\"><i class=\"fas fa-id-badge\"></i> <span class=\"label\">Matricule</span> <span class=\"value\">").append(echapperHtml(p.getMatricule())).append("</span></div>");
        if (p.getNinu() != null && !p.getNinu().isBlank()) {
            sb.append("<div class=\"info-item\"><i class=\"fas fa-fingerprint\"></i> <span class=\"label\">NINU</span> <span class=\"value\">").append(echapperHtml(p.getNinu())).append("</span></div>");
        }
        sb.append("<div class=\"info-item\"><i class=\"fas fa-calendar-plus\"></i> <span class=\"label\">Date d'embauche</span> <span class=\"value\">").append(p.getDateEmbauche() != null ? p.getDateEmbauche().toString() : "Non renseignée").append("</span></div>");
        sb.append("</div>");
        return sb.toString();
    }

    private static String renderPersonalInfo(Professeur p) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"card personal-info\">");
        sb.append("<h3><i class=\"fas fa-user\"></i> Informations personnelles</h3>");
        sb.append("<div class=\"info-row\"><span class=\"label\">Nom complet</span> <span class=\"value\">").append(echapperHtml(p.getNomComplet())).append("</span></div>");
        if (p.getSexe() != null && !p.getSexe().isBlank()) {
            sb.append("<div class=\"info-row\"><span class=\"label\">Sexe</span> <span class=\"value\">").append(echapperHtml(p.getSexe())).append("</span></div>");
        }
        if (p.getDateNaissance() != null) {
            sb.append("<div class=\"info-row\"><span class=\"label\">Date de naissance</span> <span class=\"value\">").append(p.getDateNaissance().toString()).append("</span></div>");
        }
        if (p.getGroupeSanguin() != null && !p.getGroupeSanguin().isBlank()) {
            sb.append("<div class=\"info-row\"><span class=\"label\">Groupe sanguin</span> <span class=\"value\">").append(echapperHtml(p.getGroupeSanguin())).append("</span></div>");
        }
        if (p.getSituationMatrimoniale() != null && !p.getSituationMatrimoniale().isBlank()) {
            sb.append("<div class=\"info-row\"><span class=\"label\">Situation matrimoniale</span> <span class=\"value\">").append(echapperHtml(p.getSituationMatrimoniale())).append("</span></div>");
        }
        sb.append("<div class=\"info-row\"><span class=\"label\">Téléphone</span> <span class=\"value\">").append(echapperHtml(p.getTelephone())).append("</span></div>");
        sb.append("<div class=\"info-row\"><span class=\"label\">Email</span> <span class=\"value\">").append(echapperHtml(p.getEmail())).append("</span></div>");
        if (p.getAdresse() != null && !p.getAdresse().isBlank()) {
            sb.append("<div class=\"info-row\"><span class=\"label\">Adresse</span> <span class=\"value\">").append(echapperHtml(p.getAdresse())).append("</span></div>");
        }
        sb.append("</div>");
        return sb.toString();
    }

    private static String renderProfessionalInfo(Professeur p) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"card professional-info\">");
        sb.append("<h3><i class=\"fas fa-briefcase\"></i> Informations professionnelles</h3>");
        if (p.getDiplome() != null && !p.getDiplome().isBlank()) {
            sb.append("<div class=\"info-row\"><span class=\"label\">Diplôme</span> <span class=\"value\">").append(echapperHtml(p.getDiplome())).append("</span></div>");
        }
        if (p.getSpecialite() != null && !p.getSpecialite().isBlank()) {
            sb.append("<div class=\"info-row\"><span class=\"label\">Spécialité</span> <span class=\"value\">").append(echapperHtml(p.getSpecialite())).append("</span></div>");
        }
        sb.append("<div class=\"info-row\"><span class=\"label\">Salaire</span> <span class=\"value\">").append(String.format("%,.2f", p.getSalaire())).append(" €</span></div>");
        sb.append("<div class=\"info-row\"><span class=\"label\">Statut</span> <span class=\"value\"><span class=\"badge\">").append(p.getStatut() != null ? p.getStatut() : "ACTIF").append("</span></span></div>");
        if (p.getObservations() != null && !p.getObservations().isBlank()) {
            sb.append("<div class=\"info-row\"><span class=\"label\">Observations</span> <span class=\"value\">").append(echapperHtml(p.getObservations())).append("</span></div>");
        }
        if (p.isBiometrieActive()) {
            sb.append("<div class=\"info-row\"><span class=\"label\">Biométrie</span> <span class=\"value\" style=\"color:#16a34a;\"><i class=\"fas fa-check-circle\"></i> Activée</span></div>");
        }
        sb.append("</div>");
        return sb.toString();
    }

    private static String renderCreneaux(List<Professeur.Creneau> creneaux) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"card creneaux\">");
        sb.append("<h3><i class=\"fas fa-book-open\"></i> Mes cours (").append(creneaux != null ? creneaux.size() : 0).append(")</h3>");
        if (creneaux == null || creneaux.isEmpty()) {
            sb.append("<div class=\"creneau-empty\"><i class=\"fas fa-info-circle\"></i> Aucun cours assigné.</div>");
        } else {
            for (Professeur.Creneau c : creneaux) {
                sb.append("<div class=\"creneau-item\">");
                sb.append("<div>");
                sb.append("<div class=\"matiere\"><i class=\"fas fa-graduation-cap\"></i> ").append(echapperHtml(c.getNomMatiere())).append("</div>");
                sb.append("<div class=\"details\"><i class=\"fas fa-users\"></i> ").append(echapperHtml(c.getClasse())).append(" • ");
                sb.append("<i class=\"fas fa-calendar\"></i> ").append(echapperHtml(c.getAnneeAcademique())).append(" • ");
                sb.append("<i class=\"fas fa-clock\"></i> ").append(echapperHtml(c.getPeriode())).append("</div>");
                sb.append("</div>");
                sb.append("<div class=\"details\" style=\"text-align:right;\">");
                if (c.getJourSemaine() != null && !c.getJourSemaine().isBlank()) {
                    sb.append("<div><i class=\"fas fa-calendar-day\"></i> ").append(echapperHtml(c.getJourSemaine())).append("</div>");
                }
                sb.append("<div><i class=\"fas fa-clock\"></i> ").append(echapperHtml(c.getHoraireFormate())).append("</div>");
                sb.append("<div><i class=\"fas fa-star\"></i> Coef. ").append(c.getCoefficient() != null ? c.getCoefficient() : 1.0).append("</div>");
                sb.append("</div>");
                sb.append("</div>");
            }
        }
        sb.append("</div>");
        return sb.toString();
    }

    private static String renderStats(Professeur p) {
        int nbCours = p.getCreneaux() != null ? p.getCreneaux().size() : 0;
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"card stats\">");
        sb.append("<h3><i class=\"fas fa-chart-simple\"></i> Statistiques</h3>");
        sb.append("<div class=\"stats-grid\">");
        sb.append("<div class=\"stat-item\"><div class=\"number\">").append(nbCours).append("</div><div class=\"label\">Cours assignés</div></div>");
        sb.append("<div class=\"stat-item\"><div class=\"number\">").append(p.getSalaire() > 0 ? "1" : "0").append("</div><div class=\"label\">Années d'expérience</div></div>");
        sb.append("<div class=\"stat-item\"><div class=\"number\">").append(p.isBiometrieActive() ? "✅" : "❌").append("</div><div class=\"label\">Biométrie</div></div>");
        sb.append("<div class=\"stat-item\"><div class=\"number\">").append(p.getStatut() != null ? "✅" : "❌").append("</div><div class=\"label\">Statut</div></div>");
        sb.append("</div>");
        sb.append("</div>");
        return sb.toString();
    }

    // ==================== UTILITAIRES ====================

    private static String echapperHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }
}