import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class ProfesseurDashboardHandler implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(ProfesseurDashboardHandler.class.getName());

    // ============================================================
    // LISTE DES MODULES DU PROFESSEUR (avec 2 nouveaux modules)
    // ============================================================
    private static final ModuleInfo[] MODULES = {
        new ModuleInfo("Mon Profil", "fas fa-user", "/professeur/profil", "profil"),
        new ModuleInfo("Mes Cours", "fas fa-book", "/professeur/cours", "cours"),
        new ModuleInfo("Saisie des Notes", "fas fa-pen", "/professeur/notes", "notes"),
        new ModuleInfo("Examens", "fas fa-file-alt", "/professeur/examens", "examens"),
        new ModuleInfo("Absences", "fas fa-user-slash", "/professeur/absences", "absences"),
        new ModuleInfo("Documents", "fas fa-folder-open", "/professeur/documents", "documents"),
        new ModuleInfo("Messages", "fas fa-envelope", "/professeur/messages", "messages"),
        new ModuleInfo("Paramètres", "fas fa-cog", "/professeur/parametres", "parametres"),
        new ModuleInfo("Programmes", "fas fa-book-open", "/professeur/programmes", "programmes"),
        new ModuleInfo("Activités", "fas fa-calendar-alt", "/professeur/activites", "activites"),
        // ══════════ NOUVEAUX MODULES POUR LES COURS EN LIGNE ══════════
        new ModuleInfo("Cours en Ligne", "fas fa-video", "/professeur/cours-en-ligne", "coursEnLigne"),
        new ModuleInfo("Quiz en Ligne", "fas fa-question-circle", "/professeur/quiz-en-ligne", "quizEnLigne")
        // ═══════════════════════════════════════════════════════════════
    };

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String professeurId = getProfesseurIdFromSession(exchange);
            String institutionId = getInstitutionIdFromSession(exchange);

            if (professeurId == null || institutionId == null) {
                exchange.getResponseHeaders().set("Location", "/connexion");
                exchange.sendResponseHeaders(302, -1);
                return;
            }

            Professeur professeur = null;
            String logoInstitutionSrc = null;

            try (Connection conn = DatabaseManager.getInstance().getConnection()) {
                ProfesseurData profData = new ProfesseurData(conn);
                professeur = profData.trouverParId(professeurId, institutionId);

                InstitutionData institutionData = new InstitutionData(conn);
                Institution institution = institutionData.readByIdOrInstitutionId(institutionId);
                if (institution != null && institution.getLogoInstitution() != null && !institution.getLogoInstitution().isEmpty()) {
                    String logo = institution.getLogoInstitution();
                    if (logo.startsWith("/")) {
                        logo = logo.substring(1);
                    }
                    logoInstitutionSrc = "/data/" + logo;
                }
            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "Erreur lors de la récupération des données", e);
            }

            if (professeur == null) {
                exchange.getResponseHeaders().set("Location", "/connexion");
                exchange.sendResponseHeaders(302, -1);
                return;
            }

            Map<String, Boolean> droits = getDroitsAcces(professeurId, institutionId);
            if (droits == null) {
                droits = new HashMap<>();
                for (ModuleInfo m : MODULES) {
                    droits.put(m.cle, true);
                }
            }

            int modulesAccessibles = 0;
            for (ModuleInfo m : MODULES) {
                if (droits.getOrDefault(m.cle, false)) {
                    modulesAccessibles++;
                }
            }

            String html = genererHtml(professeur, droits, modulesAccessibles, logoInstitutionSrc);
            ResponseUtil.sendHtml(exchange, 200, html);

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Erreur dans ProfesseurDashboardHandler", e);
            String errorHtml = "<html><body><h1>Erreur</h1><p>" + e.getMessage() + "</p></body></html>";
            ResponseUtil.sendHtml(exchange, 500, errorHtml);
        }
    }

    // ==================== SESSION ====================

    private String getProfesseurIdFromSession(HttpExchange exchange) {
        try {
            String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
            if (cookieHeader == null) return null;
            String sessionId = extractSessionId(cookieHeader);
            if (sessionId == null) return null;
            HttpSession session = SessionManager.getSession(sessionId);
            if (session == null) return null;
            return (String) session.getAttribute("userId");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Impossible de récupérer l'ID professeur", e);
            return null;
        }
    }

    private String getInstitutionIdFromSession(HttpExchange exchange) {
        try {
            String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
            if (cookieHeader == null) return null;
            String sessionId = extractSessionId(cookieHeader);
            if (sessionId == null) return null;
            HttpSession session = SessionManager.getSession(sessionId);
            if (session == null) return null;
            String instId = (String) session.getAttribute("institutionId");
            if (instId == null || instId.isBlank()) {
                instId = (String) session.getAttribute("institution_id");
            }
            return instId;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Impossible de récupérer l'institutionId", e);
            return null;
        }
    }

    private String extractSessionId(String cookieHeader) {
        for (String cookie : cookieHeader.split(";")) {
            String[] parts = cookie.trim().split("=");
            if (parts.length == 2 && "SESSION_ID".equals(parts[0])) {
                return parts[1];
            }
        }
        return null;
    }

    // ==================== DROITS ====================

    private Map<String, Boolean> getDroitsAcces(String professeurId, String institutionId) {
        // Initialiser tous les modules à true par défaut
        Map<String, Boolean> droits = new HashMap<>();
        for (ModuleInfo m : MODULES) {
            droits.put(m.cle, true);
        }

        // Charger les droits depuis la base de données (si la table contient les nouvelles colonnes)
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            AccesProfesseurData data = new AccesProfesseurData(conn);
            AccesProfesseur acces = data.getByProfesseur(professeurId, institutionId);
            if (acces != null) {
                droits.put("profil", acces.isProfil());
                droits.put("cours", acces.isCours());
                droits.put("notes", acces.isNotes());
                droits.put("edt", acces.isEdt());
                droits.put("absences", acces.isAbsences());
                droits.put("documents", acces.isDocuments());
                droits.put("messages", acces.isMessages());
                droits.put("parametres", acces.isParametres());
                droits.put("programmes", acces.isProgrammes());
                droits.put("activites", acces.isActivites());
                // ══════════ RÉCUPÉRATION DES NOUVEAUX DROITS ══════════
                // Si la classe AccesProfesseur possède les méthodes correspondantes
                // droits.put("coursEnLigne", acces.isCoursEnLigne());
                // droits.put("quizEnLigne", acces.isQuizEnLigne());
                // Sinon, ils restent à true par défaut
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur chargement droits professeur", e);
        }
        return droits;
    }

    // ==================== GÉNÉRATION HTML ====================

    private String genererHtml(Professeur professeur, Map<String, Boolean> droits, int modulesAccessibles, String logoInstitutionSrc) {
        String nomComplet = professeur.getNomComplet();
        String photoPath = professeur.getPhotoPath();
        
        String avatarSrc = null;
        if (photoPath != null && !photoPath.isEmpty()) {
            if (photoPath.startsWith("/data/")) {
                avatarSrc = photoPath;
            } else if (photoPath.startsWith("uploads/")) {
                avatarSrc = "/data/" + photoPath;
            } else {
                avatarSrc = "/data/uploads/photos_professeurs/" + photoPath;
            }
            LOGGER.log(Level.INFO, "📸 Photo professeur : {0}", avatarSrc);
        }
        if (avatarSrc == null && logoInstitutionSrc != null && !logoInstitutionSrc.isEmpty()) {
            avatarSrc = logoInstitutionSrc;
        }
        boolean hasAvatar = (avatarSrc != null && !avatarSrc.isEmpty());

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>");
        sb.append("<html lang=\"fr\">");
        sb.append("<head>");
        sb.append("<meta charset=\"UTF-8\">");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        sb.append("<title>Dashboard Professeur</title>");
        sb.append("<link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css\">");
        sb.append("<link href=\"https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&display=swap\" rel=\"stylesheet\">");
        sb.append("<style>");
        sb.append("* { margin:0; padding:0; box-sizing:border-box; }");
        sb.append("body { font-family:'Plus Jakarta Sans',sans-serif; background:#f0f4f8; min-height:100vh; padding:0; margin:0; display:flex; flex-direction:column; }");
        sb.append(".container { flex:1; width:100%; max-width:100%; margin:0; padding:15px 25px; }");
        sb.append(".header { background:linear-gradient(135deg,#1e293b,#0f172a); color:white; border-radius:16px; padding:20px 25px; margin-bottom:20px; display:flex; flex-direction:column; align-items:center; text-align:center; box-shadow:0 8px 30px rgba(0,0,0,0.15); width:100%; }");
        sb.append(".header-top { display:flex; flex-direction:column; align-items:center; gap:10px; margin-bottom:12px; }");
        sb.append(".header-avatar { width:90px; height:90px; border-radius:50%; border:4px solid #3b82f6; object-fit:cover; background:#1e293b; box-shadow:0 4px 20px rgba(59,130,246,0.3); flex-shrink:0; overflow:hidden; display:flex; align-items:center; justify-content:center; }");
        sb.append(".header-avatar i { font-size:50px; line-height:90px; text-align:center; width:100%; color:#94a3b8; }");
        sb.append(".header-avatar img { width:100%; height:100%; border-radius:50%; object-fit:cover; }");
        sb.append(".header-info h1 { font-size:1.8rem; font-weight:700; margin:0; }");
        sb.append(".header-info h1 i { color:#3b82f6; margin-right:8px; }");
        sb.append(".header-info .subtitle { color:#94a3b8; font-size:1rem; font-weight:400; margin-top:4px; }");
        sb.append(".header-nav { display:flex; flex-wrap:wrap; justify-content:center; gap:10px; margin-top:8px; }");
        sb.append(".header-nav a { color:#94a3b8; text-decoration:none; font-size:0.85rem; transition:0.2s; padding:5px 14px; border-radius:20px; }");
        sb.append(".header-nav a:hover { color:white; background:rgba(255,255,255,0.1); }");
        sb.append(".header-nav a.active { color:white; background:rgba(255,255,255,0.15); font-weight:600; }");
        sb.append(".header-nav a i { margin-right:6px; }");
        sb.append("@media (max-width:768px) { .header-info h1 { font-size:1.3rem; } .header-avatar { width:70px; height:70px; } .header-avatar i { font-size:40px; line-height:70px; } }");
        
        sb.append(".modules-grid { display:grid; grid-template-columns:repeat(4, 1fr); gap:20px; margin-top:10px; width:100%; }");
        sb.append("@media (max-width: 1200px) { .modules-grid { grid-template-columns:repeat(3, 1fr); } }");
        sb.append("@media (max-width: 768px) { .modules-grid { grid-template-columns:repeat(2, 1fr); } }");
        
        sb.append(".module-card { background:white; border-radius:14px; padding:20px 10px 15px; text-align:center; text-decoration:none; color:#0f172a; border:1px solid #e2e8f0; transition:all 0.3s ease; display:flex; flex-direction:column; align-items:center; justify-content:center; min-height:140px; box-shadow:0 2px 8px rgba(0,0,0,0.02); width:100%; }");
        sb.append(".module-card:hover { transform:translateY(-5px); box-shadow:0 12px 30px rgba(30,64,175,0.10); border-color:#3b82f6; }");
        sb.append(".module-card .icon-circle { width:56px; height:56px; border-radius:14px; background:linear-gradient(135deg,#1e40af,#3b82f6); color:white; display:flex; align-items:center; justify-content:center; font-size:24px; margin-bottom:10px; box-shadow:0 4px 15px rgba(59,130,246,0.20); }");
        sb.append(".module-card .module-name { font-size:14px; font-weight:600; }");
        sb.append(".empty-message { grid-column:1/-1; text-align:center; padding:50px 20px; background:white; border-radius:12px; color:#64748b; }");
        sb.append(".empty-message i { font-size:48px; display:block; margin-bottom:12px; color:#94a3b8; }");
        sb.append(".footer { margin-top:30px; text-align:center; color:#94a3b8; font-size:13px; border-top:1px solid #e2e8f0; padding-top:15px; width:100%; }");
        sb.append("</style>");
        sb.append("</head>");
        sb.append("<body>");
        sb.append("<div class=\"container\">");

        // ===== EN-TÊTE CENTRÉ =====
        sb.append("<div class=\"header\">");
        sb.append("<div class=\"header-top\">");
        sb.append("<div class=\"header-avatar\">");
        if (hasAvatar) {
            String cacheBuster = "?v=" + System.currentTimeMillis();
            sb.append("<img src=\"").append(echapperHtml(avatarSrc)).append(cacheBuster).append("\" alt=\"Avatar\" onerror=\"this.style.display='none';this.parentElement.innerHTML='<i class=\\\\'fas fa-user-graduate\\\\'></i>';\">");
        } else {
            sb.append("<i class=\"fas fa-user-graduate\"></i>");
        }
        sb.append("</div>");
        sb.append("<div class=\"header-info\">");
        sb.append("<h1><i class=\"fas fa-chalkboard-teacher\"></i> Bienvenue, ").append(echapperHtml(nomComplet)).append("</h1>");
        sb.append("<div class=\"subtitle\"><i class=\"fas fa-id-badge\"></i> Professeur · ").append(echapperHtml(professeur.getNumeroIdentifiantProfesseur())).append("</div>");
        sb.append("</div>");
        sb.append("</div>");
        sb.append("<div class=\"header-nav\">");
        sb.append("<a href=\"/dashboard/professeur\" class=\"active\"><i class=\"fas fa-home\"></i> Dashboard</a>");
        sb.append("<a href=\"/professeur/profil\"><i class=\"fas fa-user\"></i> Profil</a>");
        sb.append("<a href=\"/professeur/cours\"><i class=\"fas fa-book\"></i> Cours</a>");
        sb.append("<a href=\"/professeur/notes\"><i class=\"fas fa-pen\"></i> Notes</a>");
        sb.append("<a href=\"/professeur/examens\"><i class=\"fas fa-file-alt\"></i> Examens</a>");
        sb.append("<a href=\"/professeur/absences\"><i class=\"fas fa-user-slash\"></i> Absences</a>");
        sb.append("<a href=\"/professeur/documents\"><i class=\"fas fa-folder-open\"></i> Documents</a>");
        sb.append("<a href=\"/professeur/messages\"><i class=\"fas fa-envelope\"></i> Messages</a>");
        sb.append("<a href=\"/professeur/parametres\"><i class=\"fas fa-cog\"></i> Paramètres</a>");
        // ══════════ LIENS VERS LES NOUVEAUX MODULES ══════════
        sb.append("<a href=\"/professeur/cours-en-ligne\"><i class=\"fas fa-video\"></i> Cours en Ligne</a>");
        sb.append("<a href=\"/professeur/quiz-en-ligne\"><i class=\"fas fa-question-circle\"></i> Quiz en Ligne</a>");
        // ══════════════════════════════════════════════════════
        sb.append("<a href=\"/logout\"><i class=\"fas fa-sign-out-alt\"></i> Déconnexion</a>");
        sb.append("</div>");
        sb.append("</div>");

        // ===== MODULES =====
        sb.append("<div class=\"modules-grid\">");
        if (modulesAccessibles == 0) {
            sb.append("<div class=\"empty-message\">");
            sb.append("<i class=\"fas fa-ban\"></i>");
            sb.append("<p>Aucun module n'est actuellement accessible.</p>");
            sb.append("<p style=\"font-size:0.9rem;margin-top:6px;\">Veuillez contacter l'administration.</p>");
            sb.append("</div>");
        } else {
            for (ModuleInfo m : MODULES) {
                if (droits.getOrDefault(m.cle, false)) {
                    sb.append("<a href=\"").append(m.url).append("\" class=\"module-card\">");
                    sb.append("<div class=\"icon-circle\"><i class=\"").append(m.icone).append("\"></i></div>");
                    sb.append("<span class=\"module-name\">").append(m.nom).append("</span>");
                    sb.append("</a>");
                }
            }
        }
        sb.append("</div>");

        sb.append("<div class=\"footer\">");
        sb.append("© ").append(java.time.Year.now().getValue()).append(" M-TECH Academy · Tous droits réservés");
        sb.append("</div>");

        sb.append("</div>");
        sb.append("</body>");
        sb.append("</html>");

        return sb.toString();
    }

    // ==================== CLASSE INTERNE ====================

    private static class ModuleInfo {
        final String nom;
        final String icone;
        final String url;
        final String cle;

        ModuleInfo(String nom, String icone, String url, String cle) {
            this.nom = nom;
            this.icone = icone;
            this.url = url;
            this.cle = cle;
        }
    }

    // ==================== ÉCHAPPEMENT ====================

    private static String echapperHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }
}