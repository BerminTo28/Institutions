import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
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

public class LoginHandler implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(LoginHandler.class.getName());

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "http://scolarite-local.intra:8080");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        exchange.getResponseHeaders().set("Access-Control-Allow-Credentials", "true");

        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        if ("POST".equals(exchange.getRequestMethod())) {
            handlePost(exchange);
        } else {
            sendResponse(exchange, 405,
                    "{\"success\":false,\"message\":\"Méthode non autorisée\"}");
        }
    }

    private void handlePost(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> params = parseFormData(body);

        final String identifiant = params.get("identifiant");
        final String complement  = params.get("complement");
        final String roleParam   = params.get("role");

        if (identifiant == null || identifiant.isBlank()
                || complement == null || complement.isBlank()) {
            sendResponse(exchange, 400,
                    "{\"success\":false,\"message\":\"Veuillez remplir tous les champs.\"}");
            return;
        }

        Connection conn = null;
        try {
            conn = DatabaseManager.getInstance().getConnection();
            DialogueConnexion dialogueConnexion = new DialogueConnexion(conn);
            DialogueBase dialogueBase = new DialogueBase(conn);

            System.out.println("🔐 Tentative connexion : "
                    + "identifiant=" + identifiant
                    + " | complement=" + complement
                    + " | role=" + roleParam);

            // ============================================================
            // DÉTECTION ROBUSTE — Cascade automatique
            // ============================================================
            LoginResult result = new LoginResult();

            // ① ÉTUDIANT
            if (!result.success) {
                result = tenterLoginEtudiant(dialogueBase, identifiant, complement);
                if (result.success) {
                    System.out.println("✅ Auth étudiant réussie : "
                            + "userId=" + result.userId
                            + ", email=" + result.email
                            + ", institution=" + result.institutionId);
                }
            }

            // ② PROFESSEUR
            if (!result.success) {
                result = tenterLoginProfesseur(conn, dialogueBase, identifiant, complement);
                if (result.success) {
                    System.out.println("✅ Auth professeur réussie : "
                            + "userId=" + result.userId
                            + ", displayName=" + result.displayName
                            + ", institution=" + result.institutionId);
                }
            }

            // ③ ADMIN
            if (!result.success) {
                result = tenterLoginAdmin(dialogueConnexion, identifiant, complement);
                if (result.success) {
                    System.out.println("✅ Auth admin réussie : "
                            + "institution=" + result.institutionId);
                }
            }

            // ============================================================
            // CRÉATION DE LA SESSION
            // ============================================================
            if (result.success && result.userId != null && result.institutionId != null) {
                String sessionId = SessionManager.createSession();
                HttpSession session = SessionManager.getSession(sessionId);

                session.setAttribute("userId", result.userId);
                session.setAttribute("role", result.roleName);
                session.setAttribute("displayName",
                        (result.displayName != null && !result.displayName.isBlank())
                                ? result.displayName : result.userId);
                session.setAttribute("institutionId", result.institutionId);
                session.setAttribute("institution_id", result.institutionId);

                if (result.email != null) {
                    session.setAttribute("email", result.email);
                }
                if (result.codeCours != null) {
                    session.setAttribute("codeCours", result.codeCours);
                }

                String cookie = "SESSION_ID=" + sessionId
                        + "; Path=/; Max-Age=1800; HttpOnly; SameSite=Lax";
                exchange.getResponseHeaders().set("Set-Cookie", cookie);

                System.out.println("✅ Session créée : " + sessionId);
                System.out.println("📦 Attributs : " + session.getAttributes());

                String json = String.format(
                        "{\"success\":true,\"message\":\"Connexion réussie\","
                                + "\"redirect\":\"%s\",\"role\":\"%s\"}",
                        result.redirect, result.roleName
                );
                sendResponse(exchange, 200, json);
                return;
            }

            System.out.println("❌ Échec connexion : " + identifiant);
            sendResponse(exchange, 401,
                    "{\"success\":false,\"message\":\"Identifiants invalides.\"}");

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur SQL connexion", e);
            sendResponse(exchange, 500,
                    "{\"success\":false,\"message\":\"Erreur serveur.\"}");
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Erreur inattendue connexion", e);
            sendResponse(exchange, 500,
                    "{\"success\":false,\"message\":\"Erreur serveur.\"}");
        } finally {
            if (conn != null) {
                try { if (!conn.isClosed()) conn.close(); } catch (SQLException ignored) {}
            }
        }
    }

    // ============================================================
    // ÉTUDIANT
    // ============================================================
    private LoginResult tenterLoginEtudiant(DialogueBase dialogueBase,
                                              String a, String b) {
        LoginResult r = new LoginResult();

        // Détection email + numéro
        String emailDetecte = null;
        String numeroDetecte = null;

        if (a != null && a.contains("@")) {
            emailDetecte = a; numeroDetecte = b;
        } else if (b != null && b.contains("@")) {
            emailDetecte = b; numeroDetecte = a;
        }

        if (emailDetecte == null || numeroDetecte == null) return r;

        // ✅ Copies finales pour lambda
        final String email = emailDetecte;
        final String numero = numeroDetecte;

        try {
            // ✅ Déclare SQLException — catch SQLException valide
            boolean ok = dialogueBase.authentifierEtudiant(numero, email);
            if (!ok) return r;

            String instId = dialogueBase.trouverInstitutionParEmail(email);
            if (instId == null || instId.isBlank()) {
                LOGGER.warning(() -> "❌ Institution introuvable pour : " + email);
                return r;
            }

            r.success = true;
            r.redirect = "/etudiant/dashboard";
            r.roleName = "ETUDIANT";
            r.institutionId = instId;
            r.userId = numero;
            r.email = email;
            r.displayName = email;
            return r;

        } catch (SQLException e) {
            LOGGER.log(Level.FINE, "Auth étudiant échouée", e);
            return r;
        }
    }

    // ============================================================
    // PROFESSEUR
    // ============================================================
    private LoginResult tenterLoginProfesseur(Connection conn, DialogueBase dialogueBase,
                                                String a, String b) {
        LoginResult r = new LoginResult();

        String codeCoursDetecte ;
        String numeroProfDetecte ;

        if (estNumeroIdentifiant(a)) {
            numeroProfDetecte = a; codeCoursDetecte = b;
        } else if (estNumeroIdentifiant(b)) {
            numeroProfDetecte = b; codeCoursDetecte = a;
        } else {
            return r;
        }

        if (codeCoursDetecte == null || codeCoursDetecte.isBlank()) return r;

        final String codeCours = codeCoursDetecte;
        final String numeroProf = numeroProfDetecte;

        try {
            // ✅ Déclare SQLException — catch SQLException valide
            String instId = dialogueBase.authentifierProfesseurEtObtenirInstitution(
                    codeCours, numeroProf);

            if (instId == null) return r;

            Professeur prof = getProfesseurByNumero(conn, numeroProf, instId);

            r.success = true;
            r.redirect = "/dashboard/professeur";
            r.roleName = "PROFESSEUR";
            r.institutionId = instId;
            r.userId = numeroProf;
            r.email = (prof != null && prof.getEmail() != null)
                    ? prof.getEmail() : numeroProf;
            r.displayName = (prof != null && prof.getNomComplet() != null)
                    ? prof.getNomComplet() : numeroProf;
            r.codeCours = codeCours;
            return r;

        } catch (SQLException e) {
            LOGGER.log(Level.FINE, "Auth professeur échouée", e);
            return r;
        }
    }

    // ============================================================
    // ADMIN
    // ============================================================
    private LoginResult tenterLoginAdmin(DialogueConnexion dialogueConnexion,
                                           String email, String motDePasse) {
        LoginResult r = new LoginResult();

        try {
            // ✅ `connecterAdmin` déclare SQLException
            Institution institution = dialogueConnexion.connecterAdmin(email, motDePasse);
            if (institution == null) return r;

            r.success = true;
            r.redirect = "/dashboard/admin";
            r.roleName = "ADMINISTRATEUR";
            r.institutionId = institution.getInstitutionId();
            r.displayName = institution.getNomInstitution();
            r.userId = email;
            r.email = email;
            return r;

        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Auth admin échouée (non-SQL)", e);
            return r;
        }
    }

    // ============================================================
    // UTILITAIRES
    // ============================================================
    private boolean estNumeroIdentifiant(String s) {
        if (s == null || s.isBlank()) return false;
        return s.startsWith("CLEG") || s.matches("^[A-Z0-9]{15,}$");
    }

    private Professeur getProfesseurByNumero(Connection conn, String numeroIdentifiant,
                                              String institutionId) {
        String sql = "SELECT * FROM " + MigrationManager.TABLE_PROFESSEURS
                + " WHERE numero_identifiant_professeur = ? AND institution_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, numeroIdentifiant);
            ps.setString(2, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Professeur p = new Professeur();
                    p.setNumeroIdentifiantProfesseur(rs.getString("numero_identifiant_professeur"));
                    p.setNom(rs.getString("nom"));
                    p.setPrenom(rs.getString("prenom"));
                    p.setEmail(rs.getString("email"));
                    p.setTelephone(rs.getString("telephone"));
                    p.setInstitutionId(rs.getString("institution_id"));
                    return p;
                }
            }
        } catch (SQLException e) {
            LOGGER.fine(() -> "Erreur récupération professeur : " + e.getMessage());
        }
        return null;
    }

    private Map<String, String> parseFormData(String body) {
        Map<String, String> params = new HashMap<>();
        if (body == null || body.isBlank()) return params;
        for (String pair : body.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2) {
                try {
                    params.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                               URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
                } catch (Exception ignored) {}
            }
        }
        return params;
    }

    private void sendResponse(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    // ============================================================
    // CLASSE INTERNE
    // ============================================================
    private static class LoginResult {
        boolean success = false;
        String redirect = "/dashboard";
        String roleName = "";
        String institutionId = null;
        String userId = null;
        String email = null;
        String displayName = "";
        String codeCours = null;
    }
}