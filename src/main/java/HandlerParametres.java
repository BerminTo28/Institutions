import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.mindrot.jbcrypt.BCrypt;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class HandlerParametres implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(HandlerParametres.class.getName());

    private static final String PHOTOS_DIR = "data/photos/";
    private static final String PHOTOS_URL_PATH = "/data/photos/";

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        String query = exchange.getRequestURI().getQuery();

        LOGGER.fine(() -> "🚀 Paramètres - Requête reçue: " + method + " " + path
                + (query != null ? "?" + query : ""));

        String sessionId = getSessionId(exchange);
        String utilisateurId = getUtilisateurIdFromSession(sessionId);
        String institutionId = getInstitutionIdFromSession(sessionId);

        boolean hasPermission = checkUserPermission(utilisateurId, "PARAMETRES", institutionId);
        boolean isAuthenticated = (sessionId != null && isParametresAuthenticated(sessionId));

        if ("POST".equals(method)) {
            handlePost(exchange, sessionId, utilisateurId, hasPermission, isAuthenticated);
            return;
        }

        if ("GET".equals(method) && query != null && query.contains("action=logout")) {
            logout(exchange, sessionId);
            return;
        }

        if (isAuthenticated && hasPermission) {
            Institution institution = getInstitutionById(institutionId);
            if (institution == null) {
                logout(exchange, sessionId);
                redirect(exchange, "/admin/parametres");
                return;
            }

            String logoPath = institution.getLogoInstitution();
            if (logoPath != null && !logoPath.isBlank()) {
                logoPath = normaliserCheminLogo(logoPath);
                institution.setLogoInstitution(logoPath);
            }

            // Messages
            String successMessage = null;
            String errorMessage = null;

            if (query != null) {
                if (query.contains("success=1")) {
                    successMessage = "✅ Mise à jour effectuée avec succès";
                } else if (query.contains("error=1")) {
                    errorMessage = "❌ Erreur lors de la mise à jour des données";
                } else if (query.contains("error=403")) {
                    errorMessage = "⛔ Vous n'avez pas les droits pour effectuer cette opération";
                } else if (query.contains("error=inter-institution")) {
                    errorMessage = "⛔ Accès refusé : ces identifiants appartiennent à une autre institution.";
                }
            }

            if (successMessage != null || errorMessage != null) {
                if (successMessage != null) {
                    saveMessageToSession(sessionId, successMessage, "success");
                } else {
                    saveMessageToSession(sessionId, errorMessage, "error");
                }
                redirect(exchange, "/admin/parametres/profil");
                return;
            }

            String sessionMsg = getMessageFromSession(sessionId);
            String sessionMsgType = getMessageTypeFromSession(sessionId);

            // ✅ Préparer les modules (métier) — UI ne fait que le rendu
            List<UIParametres.ParametreModule> modules = construireListeModules();

            // ✅ Nouvelle signature : nomInstitution, logo, modules, messageType, message
            String html = UIParametres.rendreDashboard(
                    institution.getNomInstitution(),
                    institution.getLogoInstitution(),
                    modules,
                    sessionMsgType,
                    sessionMsg
            );
            sendResponse(exchange, 200, html);

            clearMessagesFromSession(sessionId);
            return;
        }

        if (isAuthenticated && !hasPermission) {
            String html = generateAccessDeniedPage();
            sendResponse(exchange, 403, html);
            return;
        }

        // Page login
        String error = (query != null && query.contains("error=1")) ? "Identifiants invalides" : null;
        if (query != null && query.contains("error=inter-institution")) {
            error = "Accès refusé : ces identifiants appartiennent à une autre institution.";
        }
        String html = UIParametres.rendrePageLogin(error);
        sendResponse(exchange, 200, html);
    }

    // ============================================================
    // CONSTRUCTION DE LA LISTE DES MODULES (métier)
    // ============================================================
    private List<UIParametres.ParametreModule> construireListeModules() {
        List<UIParametres.ParametreModule> modules = new ArrayList<>();

        modules.add(new UIParametres.ParametreModule(
                "Profil", "fas fa-user",
                "Gérer les informations de l'institution",
                "/admin/parametres/profil", true));

        modules.add(new UIParametres.ParametreModule(
                "Année", "fas fa-calendar",
                "Gérer les années académiques",
                "/admin/parametres/annees", true));

        modules.add(new UIParametres.ParametreModule(
                "Période", "fas fa-clock",
                "Gérer les périodes",
                "/admin/parametres/periodes", true));

        modules.add(new UIParametres.ParametreModule(
                "Promotions", "fas fa-graduation-cap",
                "Gérer les promotions",
                "/admin/parametres/promotions", true));

        modules.add(new UIParametres.ParametreModule(
                "Options", "fas fa-list-ul",
                "Gérer les options (filières)",
                "/admin/parametres/options", true));

        modules.add(new UIParametres.ParametreModule(
                "Double Saisie", "fas fa-clipboard-list",
                "Configurer la double saisie",
                "/admin/parametres/double-saisie", true));

        modules.add(new UIParametres.ParametreModule(
                "Accès", "fas fa-lock",
                "Gérer les permissions des administrateurs",
                "/admin/acces", true));

        modules.add(new UIParametres.ParametreModule(
                "Inscriptions", "fas fa-door-open",
                "Configurer les inscriptions",
                "/admin/parametres/inscription", true));

        modules.add(new UIParametres.ParametreModule(
                "Sauvegarde", "fas fa-database",
                "Gérer les sauvegardes",
                "/admin/parametres/sauvegarde", true));

        return modules;
    }

    // ============================================================
    // TRAITEMENT DES POST
    // ============================================================
    private void handlePost(HttpExchange exchange, String sessionId, String utilisateurId,
                              boolean hasPermission, boolean isAuthenticated) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> params = parseFormData(body);
        String action = params.get("action");

        if (null == action) {
            redirect(exchange, "/admin/parametres");
            return;
        }

        switch (action) {
            case "login" -> {
                String mail = params.get("mail_primaire");
                String password = params.get("mot_de_passe");
                if (mail == null || password == null || mail.isBlank() || password.isBlank()) {
                    redirect(exchange, "/admin/parametres?error=1");
                    return;
                }

                // ⚠️ 1) Institution de la SESSION COURANTE (utilisateur déjà connecté)
                String sessionInstitution = getInstitutionIdFromSession(sessionId);

                // ⚠️ 2) Authentifier les identifiants fournis
                Institution institution = authenticate(mail, password);

                if (institution == null) {
                    redirect(exchange, "/admin/parametres?error=1");
                    return;
                }

                // ⚠️ 3) CONTRÔLE CRITIQUE : institution différente = accès refusé
                if (sessionInstitution == null || sessionInstitution.isBlank()) {
                    LOGGER.warning(() -> "⛔ Accès paramètres refusé : pas d'institution en session");
                    redirect(exchange, "/admin/parametres?error=1");
                    return;
                }

                String institutionCible = institution.getInstitutionId();
                if (institutionCible == null
                        || !institutionCible.equals(sessionInstitution)) {
                    LOGGER.warning(() -> "⛔ TENTATIVE D'ACCÈS INTER-INSTITUTIONS : "
                            + "session=" + sessionInstitution
                            + " | credentials=" + institutionCible
                            + " | email=" + mail);
                    redirect(exchange, "/admin/parametres?error=inter-institution");
                    return;
                }

                // ✅ 4) OK : même institution, ouvrir la session paramètres
                setParametresAuthenticated(sessionId, institution.getInstitutionId(), mail);
                LOGGER.info(() -> "✅ Re-authentification Paramètres réussie pour : " + mail);
                redirect(exchange, "/admin/parametres");
            }

            case "update" -> {
                if (!hasPermission || !isAuthenticated) {
                    redirect(exchange, "/admin/parametres?error=403");
                    return;
                }

                String currentUserId = getUtilisateurIdFromSession(sessionId);
                if (currentUserId == null || !currentUserId.equals(utilisateurId)) {
                    LOGGER.warning(() -> "⚠️ Tentative de modification par un utilisateur "
                            + "non autorisé: " + currentUserId);
                    redirect(exchange, "/admin/parametres?error=403");
                    return;
                }

                String institutionId = getInstitutionIdFromSession(sessionId);
                if (institutionId == null) {
                    redirect(exchange, "/admin/parametres?error=1");
                    return;
                }

                String nomInstitution = params.get("nom_institution");
                String sigle = params.get("sigle_institution");
                String mailPrimaire = params.get("mail_primaire");
                String logoInstitution = params.get("logo_institution");
                String adresse = params.get("adresse_institution");
                String telephone = params.get("telephone_primaire");
                String responsable = params.get("responsable_institution");
                String posteResponsable = params.get("poste_responsable");

                if (logoInstitution != null && !logoInstitution.isBlank()) {
                    logoInstitution = normaliserCheminLogo(logoInstitution);
                }

                boolean success = updateInstitution(institutionId, nomInstitution, sigle,
                        mailPrimaire, logoInstitution, adresse, telephone,
                        responsable, posteResponsable);

                if (success) {
                    saveMessageToSession(sessionId,
                            "✅ Mise à jour effectuée avec succès", "success");
                } else {
                    saveMessageToSession(sessionId,
                            "❌ Erreur lors de la mise à jour des données", "error");
                }

                redirect(exchange, "/admin/parametres/profil");
            }

            default -> redirect(exchange, "/admin/parametres");
        }
    }

    // ============================================================
    // NORMALISATION DU CHEMIN DU LOGO
    // ============================================================
    private String normaliserCheminLogo(String logoPath) {
        if (logoPath == null || logoPath.isBlank()) return null;

        String normalized = logoPath.trim().replace("\\", "/");

        if (normalized.startsWith("http://") || normalized.startsWith("https://")
                || normalized.startsWith("data:")) {
            return normalized;
        }

        while (normalized.contains("//")) {
            normalized = normalized.replace("//", "/");
        }

        if (normalized.startsWith(PHOTOS_DIR)) {
            normalized = "/" + normalized;
        }

        if (!normalized.startsWith(PHOTOS_URL_PATH)) {
            String fileName = normalized.substring(normalized.lastIndexOf("/") + 1);
            if (!fileName.isBlank() && fileName.startsWith("logo_")) {
                normalized = PHOTOS_URL_PATH + fileName;
            } else {
                return normalized;
            }
        }

        return normalized;
    }

    // ========== Gestion des messages en session ==========
    private void saveMessageToSession(String sessionId, String message, String type) {
        if (sessionId == null) return;
        try {
            HttpSession session = SessionManager.getSession(sessionId);
            if (session != null) {
                session.setAttribute("parametresMessage", message);
                session.setAttribute("parametresMessageType", type);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erreur sauvegarde message en session: {0}",
                    e.getMessage());
        }
    }

    private String getMessageFromSession(String sessionId) {
        if (sessionId == null) return null;
        try {
            HttpSession session = SessionManager.getSession(sessionId);
            if (session == null) return null;
            return (String) session.getAttribute("parametresMessage");
        } catch (Exception e) {
            return null;
        }
    }

    private String getMessageTypeFromSession(String sessionId) {
        if (sessionId == null) return null;
        try {
            HttpSession session = SessionManager.getSession(sessionId);
            if (session == null) return null;
            return (String) session.getAttribute("parametresMessageType");
        } catch (Exception e) {
            return null;
        }
    }

    private void clearMessagesFromSession(String sessionId) {
        if (sessionId == null) return;
        try {
            HttpSession session = SessionManager.getSession(sessionId);
            if (session != null) {
                session.removeAttribute("parametresMessage");
                session.removeAttribute("parametresMessageType");
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erreur nettoyage messages en session: {0}",
                    e.getMessage());
        }
    }

    private boolean checkUserPermission(String utilisateurId, String permissionKey,
                                          String institutionId) {
        if (utilisateurId == null || utilisateurId.isBlank()) return false;
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            AccesAdminData dao = new AccesAdminData(conn);
            AccesAdmin acces = dao.loadByUtilisateurId(utilisateurId, institutionId);
            if (acces == null) return false;
            return acces.hasAccessForModule(permissionKey);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erreur vérification permission: {0}", e.getMessage());
            return false;
        }
    }

    private Institution authenticate(String mail, String password) {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            String sql = "SELECT a.*, i.* FROM acces_admin a "
                    + "JOIN institutions i ON a.institution_id = i.institution_id "
                    + "WHERE a.email = ? OR a.utilisateur_id = ?";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, mail);
                ps.setString(2, mail);
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    String hash = rs.getString("a.mot_de_passe");
                    if (hash != null && BCrypt.checkpw(password, hash)) {
                        Institution inst = new Institution();
                        inst.setInstitutionId(rs.getString("i.institution_id"));
                        inst.setNomInstitution(rs.getString("i.nom_institution"));
                        inst.setSigleInstitution(rs.getString("i.sigle_institution"));
                        inst.setMailPrimaire(rs.getString("i.mail_primaire"));
                        inst.setLogoInstitution(
                                normaliserCheminLogo(rs.getString("i.logo_institution")));
                        inst.setAdresseInstitution(rs.getString("i.adresse_institution"));
                        inst.setTelephonePrimaireInstitution(rs.getString("i.telephone_primaire"));
                        inst.setResponsableInstitution(rs.getString("i.responsable_institution"));
                        inst.setPosteResponsableInstitution(rs.getString("i.poste_responsable"));
                        return inst;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erreur authentification paramètres: {0}", e.getMessage());
        }
        return null;
    }

    private Institution getInstitutionById(String institutionId) {
        if (institutionId == null) return null;
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT * FROM institutions WHERE institution_id = ?")) {
            ps.setString(1, institutionId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Institution inst = new Institution();
                inst.setInstitutionId(rs.getString("institution_id"));
                inst.setNomInstitution(rs.getString("nom_institution"));
                inst.setSigleInstitution(rs.getString("sigle_institution"));
                inst.setMailPrimaire(rs.getString("mail_primaire"));
                inst.setLogoInstitution(
                        normaliserCheminLogo(rs.getString("logo_institution")));
                inst.setAdresseInstitution(rs.getString("adresse_institution"));
                inst.setTelephonePrimaireInstitution(rs.getString("telephone_primaire"));
                inst.setResponsableInstitution(rs.getString("responsable_institution"));
                inst.setPosteResponsableInstitution(rs.getString("poste_responsable"));
                return inst;
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erreur getInstitutionById: {0}", e.getMessage());
        }
        return null;
    }

    private boolean updateInstitution(String institutionId, String nom, String sigle,
                                        String mail, String logo, String adresse,
                                        String telephone, String responsable,
                                        String posteResponsable) {
        if (institutionId == null) return false;
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE institutions SET nom_institution = ?, sigle_institution = ?, "
                 + "mail_primaire = ?, logo_institution = ?, adresse_institution = ?, "
                 + "telephone_primaire = ?, responsable_institution = ?, "
                 + "poste_responsable = ? WHERE institution_id = ?")) {
            ps.setString(1, nom);
            ps.setString(2, sigle);
            ps.setString(3, mail);
            ps.setString(4, normaliserCheminLogo(logo));
            ps.setString(5, adresse);
            ps.setString(6, telephone);
            ps.setString(7, responsable);
            ps.setString(8, posteResponsable);
            ps.setString(9, institutionId);
            int result = ps.executeUpdate();
            LOGGER.fine(() -> "✅ Mise à jour institution " + institutionId
                    + " - " + result + " ligne(s)");
            return result > 0;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erreur updateInstitution: {0}", e.getMessage());
            return false;
        }
    }

    // ========== Gestion de session ==========
    private String getSessionId(HttpExchange exchange) {
        try {
            String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
            if (cookieHeader == null) return null;
            for (String cookie : cookieHeader.split(";")) {
                String[] parts = cookie.trim().split("=");
                if (parts.length == 2 && "SESSION_ID".equals(parts[0])) {
                    return parts[1];
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erreur getSessionId: {0}", e.getMessage());
        }
        return null;
    }

    private String getUtilisateurIdFromSession(String sessionId) {
        if (sessionId == null) return null;
        try {
            HttpSession session = SessionManager.getSession(sessionId);
            if (session == null) return null;
            String userId = (String) session.getAttribute("userId");
            if (userId == null || userId.isBlank()) {
                userId = (String) session.getAttribute("username");
            }
            if (userId == null || userId.isBlank()) {
                userId = (String) session.getAttribute("email");
            }
            return userId;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isParametresAuthenticated(String sessionId) {
        if (sessionId == null) return false;
        try {
            HttpSession session = SessionManager.getSession(sessionId);
            if (session == null) return false;
            Boolean flag = (Boolean) session.getAttribute("parametresAuthenticated");
            return flag != null && flag;
        } catch (Exception e) {
            return false;
        }
    }

    private void setParametresAuthenticated(String sessionId, String institutionId,
                                              String utilisateurId) {
        if (sessionId == null) return;
        try {
            HttpSession session = SessionManager.getSession(sessionId);
            if (session == null) return;
            session.setAttribute("parametresAuthenticated", true);
            session.setAttribute("institutionId", institutionId);
            session.setAttribute("userId", utilisateurId);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erreur setParametresAuthenticated: {0}", e.getMessage());
        }
    }

    private String getInstitutionIdFromSession(String sessionId) {
        if (sessionId == null) return null;
        try {
            HttpSession session = SessionManager.getSession(sessionId);
            if (session == null) return null;
            String instId = (String) session.getAttribute("institutionId");
            if (instId == null || instId.isBlank()) {
                instId = (String) session.getAttribute("institution_id");
            }
            return instId;
        } catch (Exception e) {
            return null;
        }
    }

    private void logout(HttpExchange exchange, String sessionId) throws IOException {
        if (sessionId != null) {
            try {
                HttpSession session = SessionManager.getSession(sessionId);
                if (session != null) {
                    session.setAttribute("parametresAuthenticated", false);
                    clearMessagesFromSession(sessionId);
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Erreur logout: {0}", e.getMessage());
            }
        }
        redirect(exchange, "/admin/parametres");
    }

    // ========== PAGE D'ACCÈS REFUSÉ ==========
    private String generateAccessDeniedPage() {
        return """
            <!DOCTYPE html>
            <html lang="fr">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Accès refusé</title>
                <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700&display=swap" rel="stylesheet">
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body {
                        font-family: 'Plus Jakarta Sans', sans-serif;
                        background: #f4f6f9;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        min-height: 100vh;
                        padding: 20px;
                    }
                    .card {
                        background: white;
                        border-radius: 20px;
                        padding: 50px 40px;
                        max-width: 500px;
                        width: 100%;
                        text-align: center;
                        box-shadow: 0 20px 60px rgba(0,0,0,0.08);
                    }
                    .card .icon {
                        font-size: 64px;
                        color: #ef4444;
                        margin-bottom: 16px;
                    }
                    .card h1 {
                        font-size: 24px;
                        color: #0f172a;
                        margin-bottom: 8px;
                    }
                    .card p {
                        color: #64748b;
                        font-size: 15px;
                        line-height: 1.6;
                        margin-bottom: 24px;
                    }
                    .card .btn {
                        display: inline-block;
                        padding: 10px 28px;
                        background: linear-gradient(135deg, #1e40af, #3b82f6);
                        color: white;
                        border-radius: 10px;
                        text-decoration: none;
                        font-weight: 600;
                        font-size: 14px;
                        transition: opacity 0.2s;
                    }
                    .card .btn:hover {
                        opacity: 0.9;
                    }
                </style>
            </head>
            <body>
                <div class="card">
                    <div class="icon">🔒</div>
                    <h1>Accès refusé</h1>
                    <p>Vous n'avez pas les permissions nécessaires pour accéder à la page des paramètres.</p>
                    <a href="/dashboard" class="btn">Retour au tableau de bord</a>
                </div>
            </body>
            </html>
        """;
    }

    // ========== UTILITAIRES ==========
    private void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(302, -1);
        LOGGER.fine(() -> "🔄 Redirection vers: " + location);
    }

    private Map<String, String> parseFormData(String body) {
        Map<String, String> params = new HashMap<>();
        if (body == null || body.isBlank()) return params;
        for (String pair : body.split("&")) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                try {
                    String key = URLDecoder.decode(pair.substring(0, idx),
                            StandardCharsets.UTF_8);
                    String value = URLDecoder.decode(pair.substring(idx + 1),
                            StandardCharsets.UTF_8);
                    params.put(key, value);
                } catch (Exception e) {
                    // ignore
                }
            }
        }
        LOGGER.fine(() -> "📝 Données du formulaire: " + params);
        return params;
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String html)
            throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
            os.flush();
        }
        LOGGER.fine(() -> "📤 Réponse envoyée - Status: " + statusCode
                + " - Taille: " + bytes.length + " octets");
    }
}