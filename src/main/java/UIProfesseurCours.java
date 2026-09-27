import java.time.Year;
import java.util.List;
import java.util.stream.Collectors;

public class UIProfesseurCours {

    private static final String DEFAULT_AVATAR = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='150' height='150'%3E%3Crect width='150' height='150' fill='%231e40af'/%3E%3Ctext x='75' y='95' font-size='60' text-anchor='middle' fill='white' font-family='sans-serif'%3E👨‍🏫%3C/text%3E%3C/svg%3E";

    public static String render(Professeur professeur, List<Professeur.Creneau> creneaux) {
        if (professeur == null) {
            return renderError("Professeur non trouvé.");
        }

        int nbCours = creneaux != null ? creneaux.size() : 0;
        long nbClasses = creneaux != null ? creneaux.stream().map(Professeur.Creneau::getClasse).distinct().count() : 0;
        long nbAnnee = creneaux != null ? creneaux.stream().map(Professeur.Creneau::getAnneeAcademique).distinct().count() : 0;

        var coursParAnnee = creneaux != null ? creneaux.stream().collect(Collectors.groupingBy(Professeur.Creneau::getAnneeAcademique)) : null;

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>");
        sb.append("<html lang=\"fr\">");
        sb.append("<head>");
        sb.append("<meta charset=\"UTF-8\">");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        sb.append("<title>Mes Cours - Professeur</title>");
        sb.append("<link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css\">");
        sb.append("<link href=\"https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@300;400;500;600;700;800&display=swap\" rel=\"stylesheet\">");
        sb.append("<style>");
        sb.append(renderStyles());
        sb.append("</style>");
        sb.append("</head>");
        sb.append("<body>");
        sb.append("<div class=\"container\">");

        sb.append(renderHeader(professeur));
        sb.append(renderStats(nbCours, nbClasses, nbAnnee, professeur));
        sb.append(renderCours(creneaux, coursParAnnee));

        sb.append("<div class=\"footer\">");
        sb.append("© ").append(Year.now().getValue()).append(" M-TECH Academy - Tous droits réservés");
        sb.append("</div>");

        sb.append("</div>");
        sb.append("</body>");
        sb.append("</html>");

        return sb.toString();
    }

    public static String renderError(String message) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>");
        sb.append("<html lang=\"fr\">");
        sb.append("<head>");
        sb.append("<meta charset=\"UTF-8\">");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        sb.append("<title>Erreur - Mes Cours</title>");
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
        sb.append("<a href=\"/dashboard/professeur\" class=\"btn\"><i class=\"fas fa-arrow-left\"></i> Retour</a>");
        sb.append("</div>");
        sb.append("</body>");
        sb.append("</html>");
        return sb.toString();
    }

    private static String renderStyles() {
        return """
        * { margin:0; padding:0; box-sizing:border-box; }
        body {
            font-family:'Plus Jakarta Sans',sans-serif;
            background:#f0f4f8;
            min-height:100vh;
            padding:20px;
            display:flex;
            justify-content:center;
            align-items:center;
        }
        .container { max-width:1200px; width:100%; margin:0 auto; }

        .header { background:linear-gradient(135deg,#1e293b,#0f172a); color:white; padding:25px 30px; border-radius:16px; text-align:center; margin-bottom:25px; position:relative; overflow:hidden; }
        .header::before { content:''; position:absolute; top:-50%; right:-20%; width:300px; height:300px; background:rgba(59,130,246,0.1); border-radius:50%; }
        .header-avatar { width:80px; height:80px; border-radius:50%; border:3px solid #3b82f6; object-fit:cover; display:inline-block; box-shadow:0 8px 25px rgba(59,130,246,0.3); position:relative; z-index:1; }
        .header h1 { font-size:24px; font-weight:700; margin-top:10px; position:relative; z-index:1; }
        .header h1 i { color:#3b82f6; margin-right:10px; }
        .header .subtitle { font-size:14px; color:#94a3b8; margin-top:3px; position:relative; z-index:1; }
        .header-nav { display:flex; justify-content:center; gap:20px; margin-top:12px; flex-wrap:wrap; position:relative; z-index:1; }
        .header-nav a { color:#94a3b8; text-decoration:none; font-size:14px; transition:0.2s; padding:4px 12px; border-radius:20px; }
        .header-nav a:hover { color:white; background:rgba(255,255,255,0.1); }
        .header-nav a.active { color:white; background:rgba(255,255,255,0.15); font-weight:600; }

        .stats-grid { display:grid; grid-template-columns:repeat(auto-fit,minmax(180px,1fr)); gap:15px; margin-bottom:25px; }
        .stat-card { background:white; border-radius:12px; padding:18px 20px; text-align:center; border:1px solid #e2e8f0; transition:0.2s; }
        .stat-card:hover { box-shadow:0 8px 20px rgba(0,0,0,0.04); transform:translateY(-2px); }
        .stat-card .number { font-size:28px; font-weight:800; color:#1e40af; }
        .stat-card .label { font-size:13px; color:#64748b; margin-top:4px; }

        .cours-section { margin-top:10px; }
        .cours-section h2 { color:#1e293b; font-size:20px; font-weight:700; margin-bottom:16px; text-align:center; }
        .cours-section h2 i { color:#1e40af; margin-right:10px; }
        
        /* Grille centrée dynamiquement avec flexbox pour aligner proprement les cartes */
        .cours-grid { display:flex; flex-wrap:wrap; gap:20px; justify-content:center; align-items:stretch; }
        
        .cours-card { background:white; border-radius:16px; padding:20px; border:1px solid #e2e8f0; transition:0.3s; width:340px; flex:0 1 340px; }
        .cours-card:hover { box-shadow:0 12px 30px rgba(0,0,0,0.06); border-color:#3b82f6; }
        .cours-card .header-card { display:flex; justify-content:space-between; align-items:start; margin-bottom:12px; }
        .cours-card .matiere { font-weight:700; font-size:18px; color:#0f172a; }
        .cours-card .matiere i { color:#1e40af; margin-right:8px; }
        .cours-card .badge { background:#e2e8f0; padding:2px 12px; border-radius:12px; font-size:12px; font-weight:600; color:#475569; }
        .cours-card .details { display:grid; grid-template-columns:1fr 1fr; gap:6px 12px; font-size:14px; color:#475569; margin:12px 0; }
        .cours-card .details i { width:18px; color:#1e40af; margin-right:6px; }
        .cours-card .actions { display:flex; gap:8px; flex-wrap:wrap; margin-top:12px; padding-top:12px; border-top:1px solid #f1f5f9; justify-content:center; }
        .cours-card .actions .btn { padding:6px 14px; border:none; border-radius:8px; font-size:13px; font-weight:600; cursor:pointer; text-decoration:none; display:inline-flex; align-items:center; gap:6px; transition:0.2s; }
        .btn-sm { background:#f1f5f9; color:#1e293b; }
        .btn-sm:hover { background:#e2e8f0; }
        .btn-primary-sm { background:#1e40af; color:white; }
        .btn-primary-sm:hover { background:#1e3a8a; }
        .btn-success-sm { background:#16a34a; color:white; }
        .btn-success-sm:hover { background:#15803d; }
        .btn-warning-sm { background:#f59e0b; color:white; }
        .btn-warning-sm:hover { background:#d97706; }

        .empty-state { text-align:center; padding:40px; background:white; border-radius:16px; border:1px solid #e2e8f0; }
        .empty-state i { font-size:48px; color:#94a3b8; margin-bottom:12px; }
        .empty-state p { color:#64748b; font-size:16px; }

        .footer { margin-top:30px; text-align:center; color:#94a3b8; font-size:13px; }

        @media (max-width:768px) {
            .cours-grid { flex-direction:column; align-items:center; }
            .cours-card { width:100%; max-width:400px; }
            .stats-grid { grid-template-columns:1fr 1fr; }
            .details { grid-template-columns:1fr !important; }
        }
        """;
    }

    private static String renderHeader(Professeur p) {
        String avatar = p.getPhotoPath() != null && !p.getPhotoPath().isBlank()
                ? p.getPhotoPath()
                : DEFAULT_AVATAR;
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"header\">");
        sb.append("<img src=\"").append(echapperHtml(avatar)).append("\" alt=\"Avatar\" class=\"header-avatar\" onerror=\"this.src='").append(DEFAULT_AVATAR).append("'\">");
        sb.append("<h1><i class=\"fas fa-chalkboard-teacher\"></i> ").append(echapperHtml(p.getNomComplet())).append("</h1>");
        sb.append("<div class=\"subtitle\"><i class=\"fas fa-map-pin\"></i> ").append(echapperHtml(p.getInstitutionId())).append("</div>");
        sb.append("<div class=\"header-nav\">");
        sb.append("<a href=\"/dashboard/professeur\"><i class=\"fas fa-home\"></i> Dashboard</a>");
        sb.append("<a href=\"/professeur/profil\"><i class=\"fas fa-user\"></i> Mon Profil</a>");
        sb.append("<a href=\"/professeur/cours\" class=\"active\"><i class=\"fas fa-book\"></i> Mes Cours</a>");
        sb.append("<a href=\"/professeur/notes\"><i class=\"fas fa-pen\"></i> Saisie Notes</a>");
        sb.append("<a href=\"/logout\"><i class=\"fas fa-sign-out-alt\"></i> Déconnexion</a>");
        sb.append("</div>");
        sb.append("</div>");
        return sb.toString();
    }

    private static String renderStats(int nbCours, long nbClasses, long nbAnnee, Professeur p) {
        int totalMinutes = 0;
        if (p.getCreneaux() != null) {
            for (Professeur.Creneau c : p.getCreneaux()) {
                totalMinutes += c.getDureeMinutes();
            }
        }
        int totalHeures = totalMinutes / 60;
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"stats-grid\">");
        sb.append("<div class=\"stat-card\"><div class=\"number\">").append(nbCours).append("</div><div class=\"label\"><i class=\"fas fa-book\"></i> Cours assignés</div></div>");
        sb.append("<div class=\"stat-card\"><div class=\"number\">").append(nbClasses).append("</div><div class=\"label\"><i class=\"fas fa-users\"></i> Classes</div></div>");
        sb.append("<div class=\"stat-card\"><div class=\"number\">").append(nbAnnee).append("</div><div class=\"label\"><i class=\"fas fa-calendar\"></i> Années académiques</div></div>");
        sb.append("<div class=\"stat-card\"><div class=\"number\">").append(totalHeures).append("h</div><div class=\"label\"><i class=\"fas fa-clock\"></i> Total heures</div></div>");
        sb.append("</div>");
        return sb.toString();
    }

    private static String renderCours(List<Professeur.Creneau> creneaux, java.util.Map<String, List<Professeur.Creneau>> coursParAnnee) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"cours-section\">");

        if (creneaux == null || creneaux.isEmpty()) {
            sb.append("<div class=\"empty-state\">");
            sb.append("<i class=\"fas fa-book-open\"></i>");
            sb.append("<p>Aucun cours assigné pour le moment.</p>");
            sb.append("</div>");
            sb.append("</div>");
            return sb.toString();
        }

        for (var entry : coursParAnnee.entrySet()) {
            String annee = entry.getKey();
            List<Professeur.Creneau> cours = entry.getValue();
            sb.append("<h2><i class=\"fas fa-calendar-alt\"></i> ").append(echapperHtml(annee)).append("</h2>");
            sb.append("<div class=\"cours-grid\">");
            for (Professeur.Creneau c : cours) {
                sb.append(renderCoursCard(c));
            }
            sb.append("</div>");
        }

        sb.append("</div>");
        return sb.toString();
    }

    private static String renderCoursCard(Professeur.Creneau c) {
        Double coefObj = c.getCoefficient();
        double coefficient = (coefObj != null) ? coefObj : 1.0;
        String horaire = c.getHoraireFormate() != null ? c.getHoraireFormate() : "Non défini";

        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"cours-card\">");
        sb.append("<div class=\"header-card\">");
        sb.append("<div class=\"matiere\"><i class=\"fas fa-graduation-cap\"></i> ").append(echapperHtml(c.getNomMatiere())).append("</div>");
        sb.append("<span class=\"badge\">Coef. ").append(coefficient).append("</span>");
        sb.append("</div>");
        sb.append("<div class=\"details\">");
        sb.append("<div><i class=\"fas fa-users\"></i> ").append(echapperHtml(c.getClasse())).append("</div>");
        sb.append("<div><i class=\"fas fa-calendar-day\"></i> ").append(echapperHtml(c.getJourSemaine())).append("</div>");
        sb.append("<div><i class=\"fas fa-clock\"></i> ").append(echapperHtml(horaire)).append("</div>");
        sb.append("<div><i class=\"fas fa-calendar\"></i> ").append(echapperHtml(c.getPeriode())).append("</div>");
        sb.append("</div>");
        sb.append("<div class=\"actions\">");
        sb.append("<a href=\"/professeur/cours/etudiants?codeCours=").append(echapperHtml(c.getCodeCours()))
          .append("&classe=").append(echapperHtml(c.getClasse()))
          .append("&annee=").append(echapperHtml(c.getAnneeAcademique()))
          .append("&periode=").append(echapperHtml(c.getPeriode()))
          .append("\" class=\"btn btn-sm\"><i class=\"fas fa-users\"></i> Étudiants</a>");
        sb.append("<a href=\"/professeur/cours/notes?codeCours=").append(echapperHtml(c.getCodeCours()))
          .append("&classe=").append(echapperHtml(c.getClasse()))
          .append("&annee=").append(echapperHtml(c.getAnneeAcademique()))
          .append("&periode=").append(echapperHtml(c.getPeriode()))
          .append("\" class=\"btn btn-primary-sm\"><i class=\"fas fa-pen\"></i> Notes</a>");
        sb.append("<a href=\"/professeur/cours/absences?codeCours=").append(echapperHtml(c.getCodeCours()))
          .append("&classe=").append(echapperHtml(c.getClasse()))
          .append("&annee=").append(echapperHtml(c.getAnneeAcademique()))
          .append("&periode=").append(echapperHtml(c.getPeriode()))
          .append("\" class=\"btn btn-warning-sm\"><i class=\"fas fa-user-slash\"></i> Absences</a>");
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