import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class EtudiantDashboard implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(EtudiantDashboard.class.getName());

    private static final ModuleInfo[] MODULES = {
        new ModuleInfo("Mon Profil", "fas fa-user", "/etudiant/profil", "profil"),
        new ModuleInfo("Mes Notes", "fas fa-file-pen", "/etudiant/notes", "notes"),
        new ModuleInfo("Mon Bulletin", "fas fa-scroll", "/etudiant/bulletins", "bulletin"),
        new ModuleInfo("Paiement", "fas fa-credit-card", "/etudiant/paiements", "paiement"),
        new ModuleInfo("Messages", "fas fa-envelope", "/etudiant/messages", "messages"),
        new ModuleInfo("Documents", "fas fa-folder-open", "/etudiant/documents", "documents"),
        new ModuleInfo("Emploi du Temps", "fas fa-clock", "/etudiant/edt", "edt"),
        new ModuleInfo("Attestations", "fas fa-file-alt", "/etudiant/attestations", "attestations"),
        new ModuleInfo("Absences", "fas fa-user-slash", "/etudiant/absences", "absences"),
        new ModuleInfo("Quiz ", "fas fa-headset", "/etudiant/assistance", "assistance"),
        new ModuleInfo("Activités", "fas fa-calendar-alt", "/etudiant/activites", "activites"),
        new ModuleInfo("Statistiques des Notes", "fas fa-chart-simple", "/etudiant/statistiques-notes", "statistiques")
    };

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String etudiantId = getEtudiantIdFromSession(exchange);
            String institutionId = getInstitutionIdFromSession(exchange);

            // 1) Session obligatoire
            if (etudiantId == null || institutionId == null) {
                redirect(exchange, "/connexion");
                return;
            }

            // 2) Récupération des droits — fail-closed si absents
            Map<String, Boolean> droits = getDroitsAcces(etudiantId, institutionId);

            // 3) ✅ Contrôle du droit `connexion` — sinon refus immédiat
            if (!droits.getOrDefault("connexion", false)) {
                LOGGER.warning(() -> "🚫 Accès dashboard refusé (connexion=false) : "
                        + etudiantId);
                redirect(exchange, "/connexion?error=acces_refuse");
                return;
            }

            // 4) Récupération des infos étudiant
            EtudiantInfo etudiantInfo = getEtudiantInfo(etudiantId, institutionId);

            // 5) Génération de la page
            String html = genererHtml(etudiantInfo, droits);
            ResponseUtil.sendHtml(exchange, 200, html);

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Erreur dans EtudiantDashboard", e);
            String errorHtml = "<html><body><h1>Erreur</h1><p>"
                    + echapperHtml(e.getMessage()) + "</p></body></html>";
            ResponseUtil.sendHtml(exchange, 500, errorHtml);
        }
    }

    // ==================== RÉCUPÉRATION SESSION ====================

    private String getEtudiantIdFromSession(HttpExchange exchange) {
        try {
            String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
            if (cookieHeader == null) return null;
            String sessionId = extractSessionId(cookieHeader);
            if (sessionId == null) return null;
            HttpSession session = SessionManager.getSession(sessionId);
            if (session == null) return null;
            return (String) session.getAttribute("userId");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Impossible de récupérer l'ID étudiant", e);
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

    private void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(302, -1);
    }

    // ==================== INFOS ÉTUDIANT ====================

    private EtudiantInfo getEtudiantInfo(String etudiantId, String institutionId) {
        String sql = "SELECT nom, prenom, photo_path FROM etudiants "
                   + "WHERE numero_identifiant = ? AND institution_id = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, etudiantId);
            ps.setString(2, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String nom = rs.getString("nom");
                    String prenom = rs.getString("prenom");
                    String photoPath = rs.getString("photo_path");
                    LOGGER.info(() -> "📸 PhotoPath pour " + etudiantId + " : " + photoPath);
                    return new EtudiantInfo(
                        nom != null ? nom : "Étudiant",
                        prenom != null ? prenom : "",
                        photoPath
                    );
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur récupération infos étudiant", e);
        }
        return new EtudiantInfo("Étudiant", "", null);
    }

    private static class EtudiantInfo {
        final String nom;
        final String prenom;
        final String photoPath;

        EtudiantInfo(String nom, String prenom, String photoPath) {
            this.nom = nom;
            this.prenom = prenom;
            this.photoPath = photoPath;
        }

        String getNomComplet() {
            return (prenom != null && !prenom.isBlank() ? prenom + " " : "") + nom;
        }
    }

    // ==================== DROITS D'ACCÈS (FAIL-CLOSED) ====================

    /**
     * ✅ Récupère les droits depuis la table `acces`.
     *    En cas d'absence de ligne OU d'erreur SQL, TOUS les droits sont à false.
     *    L'étudiant doit explicitement avoir `connexion = true` pour accéder.
     */
    private Map<String, Boolean> getDroitsAcces(String etudiantId, String institutionId) {
        String sql = "SELECT connexion, profil, notes, bulletin, paiement, messages, "
                   + "documents, edt, attestations, absences, assistance, activites, statistiques "
                   + "FROM acces WHERE numero_identifiant = ? AND institution_id = ?";

        Map<String, Boolean> droits = new HashMap<>();

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, etudiantId);
            ps.setString(2, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    droits.put("connexion", rs.getBoolean("connexion"));
                    droits.put("profil", rs.getBoolean("profil"));
                    droits.put("notes", rs.getBoolean("notes"));
                    droits.put("bulletin", rs.getBoolean("bulletin"));
                    droits.put("paiement", rs.getBoolean("paiement"));
                    droits.put("messages", rs.getBoolean("messages"));
                    droits.put("documents", rs.getBoolean("documents"));
                    droits.put("edt", rs.getBoolean("edt"));
                    droits.put("attestations", rs.getBoolean("attestations"));
                    droits.put("absences", rs.getBoolean("absences"));
                    droits.put("assistance", rs.getBoolean("assistance"));
                    droits.put("activites", rs.getBoolean("activites"));
                    droits.put("statistiques", rs.getBoolean("statistiques"));
                } else {
                    // ✅ Aucune ligne acces → REFUS TOTAL (fail-closed)
                    LOGGER.warning(() -> "⚠️ Aucun droit trouvé pour "
                            + etudiantId + " → refus total par défaut");
                    for (ModuleInfo m : MODULES) droits.put(m.cle, false);
                    droits.put("connexion", false);
                }
            }
        } catch (SQLException e) {
            // ✅ Erreur SQL → REFUS TOTAL (fail-closed)
            LOGGER.log(Level.SEVERE,
                    "Erreur lors de la récupération des droits → refus total", e);
            for (ModuleInfo m : MODULES) droits.put(m.cle, false);
            droits.put("connexion", false);
        }
        return droits;
    }

    // ==================== GÉNÉRATION HTML ====================

    private String genererHtml(EtudiantInfo etudiantInfo, Map<String, Boolean> droits) {
        StringBuilder sb = new StringBuilder();

        String nomComplet = etudiantInfo.getNomComplet();
        String photoPath = etudiantInfo.photoPath;

        // Normalisation du chemin de la photo
        if (photoPath == null || photoPath.isBlank()) {
            photoPath = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='100' height='100'%3E%3Crect width='100' height='100' fill='%231e293b'/%3E%3Ctext x='50' y='60' font-size='40' text-anchor='middle' fill='white'%3E👤%3C/text%3E%3C/svg%3E";
        } else {
            photoPath = photoPath.trim().replace("\\", "/");
            while (photoPath.contains("//")) {
                photoPath = photoPath.replace("//", "/");
            }
            if (!photoPath.startsWith("/data/") && !photoPath.startsWith("http://")
                    && !photoPath.startsWith("https://") && !photoPath.startsWith("data:")) {
                if (photoPath.startsWith("uploads/")) {
                    photoPath = "/data/" + photoPath;
                } else if (photoPath.startsWith("data/")) {
                    photoPath = "/" + photoPath;
                } else {
                    photoPath = "/data/" + photoPath;
                }
            }
        }

        int modulesAccessibles = 0;
        for (ModuleInfo m : MODULES) {
            if (droits.getOrDefault(m.cle, false)) {
                modulesAccessibles++;
            }
        }

        sb.append("<!DOCTYPE html>");
        sb.append("<html lang=\"fr\">");
        sb.append("<head>");
        sb.append("<meta charset=\"UTF-8\">");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.5, user-scalable=yes\">");
        sb.append("<title>Espace Étudiant</title>");
        sb.append("<link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css\">");
        sb.append("<link href=\"https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&display=swap\" rel=\"stylesheet\">");
        sb.append("<style>");
        sb.append("* { margin:0; padding:0; box-sizing:border-box; }");
        sb.append("html, body { width: 100%; min-height: 100vh; background: linear-gradient(135deg, #f0f4f8 0%, #d9e2ec 100%); font-family:'Plus Jakarta Sans', sans-serif; }");
        sb.append("body { padding: 0; display: flex; align-items: stretch; justify-content: stretch; }");
        sb.append(".container { width: 100%; min-height: 100vh; margin: 0; background: rgba(255,255,255,0.85); backdrop-filter: blur(10px); border-radius: 0; box-shadow: none; border: none; display: flex; flex-direction: column; overflow: hidden; }");
        sb.append(".header { background: linear-gradient(135deg, #1e293b 0%, #0f172a 100%); padding: 30px 40px 24px; text-align: center; width: 100%; }");
        sb.append(".header-avatar { width: 90px; height: 90px; border-radius: 50%; border: 3px solid white; object-fit: cover; box-shadow: 0 4px 16px rgba(0,0,0,0.15); display: inline-block; background: #fff; }");
        sb.append(".header-avatar-fallback { width: 90px; height: 90px; border-radius: 50%; background: linear-gradient(135deg, #1e40af, #2563EB); color: white; font-size: 38px; display: inline-flex; align-items: center; justify-content: center; border: 3px solid white; box-shadow: 0 4px 16px rgba(0,0,0,0.15); }");
        sb.append(".header-name { color: white; font-size: 24px; font-weight: 700; margin-top: 8px; letter-spacing: -0.3px; }");
        sb.append(".header-sub { color: #94a3b8; font-size: 14px; font-weight: 400; margin-top: 4px; }");
        sb.append(".header-actions { display: flex; justify-content: flex-end; padding: 14px 30px 0 0; background: linear-gradient(135deg, #1e293b 0%, #0f172a 100%); width: 100%; }");
        sb.append(".header-actions a { color: #f5cd79; text-decoration: none; font-size: 13px; font-weight: 500; padding: 6px 14px; border-radius: 20px; background: rgba(255,255,255,0.08); transition: 0.2s; }");
        sb.append(".header-actions a:hover { background: rgba(255,255,255,0.16); }");
        sb.append(".body { padding: 40px; width: 100%; flex: 1; display: flex; flex-direction: column; max-width: 1600px; margin: 0 auto; }");
        sb.append(".modules-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 24px; width: 100%; }");
        sb.append(".module-card { background: white; border-radius: 16px; padding: 28px 20px; text-align: center; text-decoration: none; color: #0f172a; transition: all 0.2s ease; border: 1px solid rgba(226,232,240,0.6); box-shadow: 0 2px 8px rgba(0,0,0,0.02); display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 140px; width: 100%; }");
        sb.append(".module-card:hover { transform: translateY(-3px); box-shadow: 0 12px 28px rgba(37,99,235,0.08); border-color: #2563EB; background: #ffffff; }");
        sb.append(".module-card .icon-circle { width: 60px; height: 60px; border-radius: 16px; background: linear-gradient(135deg, #1e40af, #2563EB); color: white; display: flex; align-items: center; justify-content: center; font-size: 24px; margin-bottom: 14px; box-shadow: 0 4px 12px rgba(37,99,235,0.15); }");
        sb.append(".module-card .module-name { font-size: 15px; font-weight: 600; color: #1e293b; }");
        sb.append(".empty-message { grid-column: 1/-1; text-align: center; padding: 40px; background: white; border-radius: 16px; color: #64748b; width: 100%; }");
        sb.append(".empty-message i { font-size: 40px; display: block; margin-bottom: 10px; color: #94a3b8; }");
        sb.append(".footer { text-align: center; font-size: 13px; color: #64748b; border-top: 1px solid rgba(0,0,0,0.06); padding: 25px 0 15px; margin-top: auto; width: 100%; }");
        sb.append("@media (max-width: 1200px) { .modules-grid { gap: 16px; } }");
        sb.append("@media (max-width: 900px) { .modules-grid { gap: 12px; } .body { padding: 20px; } }");
        sb.append("@media (max-width: 600px) { .modules-grid { gap: 8px; } .header { padding: 20px 15px 15px; } .header-name { font-size: 20px; } .module-card { padding: 12px 4px; min-height: 90px; } .module-card .icon-circle { width: 36px; height: 36px; font-size: 15px; margin-bottom: 6px; } .module-card .module-name { font-size: 10px; } }");
        sb.append("@media (max-width: 400px) { .modules-grid { gap: 6px; } .module-card { padding: 10px 2px; min-height: 80px; } .module-card .icon-circle { width: 30px; height: 30px; font-size: 13px; margin-bottom: 4px; } .module-card .module-name { font-size: 9px; } }");
        sb.append("</style>");
        sb.append("</head>");
        sb.append("<body>");
        sb.append("<div class=\"container\">");

        // ===== EN-TÊTE AVEC PHOTO =====
        sb.append("<div class=\"header-actions\">");
        sb.append("<a href=\"/\"><i class=\"fas fa-home\"></i> Accueil</a>");
        sb.append("</div>");
        sb.append("<div class=\"header\">");
        if (photoPath != null && !photoPath.isBlank()) {
            sb.append("<img src=\"").append(photoPath).append("\" alt=\"Photo\" class=\"header-avatar\" onerror=\"this.style.display='none'; this.nextElementSibling.style.display='inline-flex';\">");
            sb.append("<div class=\"header-avatar-fallback\" style=\"display:none;\"><i class=\"fas fa-user\"></i></div>");
        } else {
            sb.append("<div class=\"header-avatar-fallback\"><i class=\"fas fa-user\"></i></div>");
        }
        sb.append("<div class=\"header-name\">Bienvenue, ").append(echapperHtml(nomComplet)).append("</div>");
        sb.append("<div class=\"header-sub\">Espace étudiant · ").append(modulesAccessibles).append(" modules disponibles</div>");
        sb.append("</div>");

        // ===== CORPS =====
        sb.append("<div class=\"body\">");
        sb.append("<div class=\"modules-grid\">");
        if (modulesAccessibles == 0) {
            sb.append("<div class=\"empty-message\">");
            sb.append("<i class=\"fas fa-ban\"></i>");
            sb.append("<p>Aucun module accessible</p>");
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
        sb.append("<div class=\"footer\">© ").append(java.time.Year.now().getValue()).append(" M-TECH Academy</div>");
        sb.append("</div>");

        sb.append("</div>");
        sb.append("</body>");
        sb.append("</html>");

        return sb.toString();
    }

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

    private static String echapperHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }
}