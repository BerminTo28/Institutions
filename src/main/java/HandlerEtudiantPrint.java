import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class HandlerEtudiantPrint implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(HandlerEtudiantPrint.class.getName());

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();

        if (!"GET".equalsIgnoreCase(method)) {
            ResponseUtil.sendError(exchange, 405, "Méthode non autorisée");
            return;
        }

        try {
            String institutionId = getInstitutionIdFromSession(exchange);
            String utilisateurId = getUtilisateurIdFromSession(exchange);

            if (institutionId == null || institutionId.isBlank() || utilisateurId == null || utilisateurId.isBlank()) {
                exchange.getResponseHeaders().set("Location", "/login?error=Session+expirée");
                exchange.sendResponseHeaders(302, -1);
                return;
            }

            AccesAdmin acces = chargerPermissions(utilisateurId, institutionId);
            if (acces == null || !acces.hasPermission(AccesAdmin.Module.ETUDIANTS, AccesAdmin.Action.READ)) {
                exchange.getResponseHeaders().set("Location", "/dashboard?error=Accès+non+autorisé");
                exchange.sendResponseHeaders(302, -1);
                return;
            }

            // Récupération des paramètres de filtrage pour l'impression
            Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
            String classeFiltre = params.getOrDefault("classe", "");
            String anneeFiltre = params.getOrDefault("anneeAcademique", "");
            String periodeFiltre = params.getOrDefault("periode", "");
            String motCle = params.getOrDefault("q", "");

            List<Etudiant> etudiants;
            try (Connection conn = DatabaseManager.getInstance().getConnection()) {
                EtudiantData etudiantData = new EtudiantData(conn);
                if (!motCle.isBlank()) {
                    etudiants = etudiantData.search(institutionId, motCle);
                } else {
                    etudiants = etudiantData.readWithFilters(institutionId, classeFiltre, anneeFiltre, periodeFiltre);
                }
            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "Erreur lors du chargement des étudiants pour impression", e);
                ResponseUtil.sendError(exchange, 500, "Erreur base de données");
                return;
            }

            String html = genererPageImpression(etudiants, institutionId);
            sendHtml(exchange, 200, html);

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Erreur dans HandlerEtudiantPrint", e);
            ResponseUtil.sendError(exchange, 500, "Erreur interne");
        }
    }

    // ==================== GÉNÉRATION HTML D'IMPRESSION ====================

    private String genererPageImpression(List<Etudiant> etudiants, String institutionId) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n")
          .append("<html>\n")
          .append("<head>\n")
          .append("    <meta charset=\"UTF-8\">\n")
          .append("    <title>Liste des étudiants - Impression</title>\n")
          .append("    <style>\n")
          .append("        body { font-family: 'Segoe UI', sans-serif; padding: 20px; background: white; }\n")
          .append("        h1 { color: #1e293b; border-bottom: 2px solid #1e40af; padding-bottom: 10px; }\n")
          .append("        table { width: 100%; border-collapse: collapse; margin-top: 20px; }\n")
          .append("        th, td { border: 1px solid #cbd5e1; padding: 8px 12px; text-align: left; }\n")
          .append("        th { background-color: #f1f5f9; font-weight: 600; }\n")
          .append("        .footer { margin-top: 30px; text-align: center; color: #94a3b8; font-size: 12px; }\n")
          .append("        @media print { .no-print { display: none; } }\n")
          .append("    </style>\n")
          .append("</head>\n")
          .append("<body>\n")
          .append("    <h1>Liste des étudiants</h1>\n")
          .append("    <p>Institution : ").append(echapperHtml(institutionId)).append("</p>\n")
          .append("    <p>Date d'impression : ").append(new java.util.Date()).append("</p>\n");

        if (etudiants.isEmpty()) {
            sb.append("    <p>Aucun étudiant trouvé.</p>\n");
        } else {
            sb.append("    <table>\n")
              .append("        <thead>\n")
              .append("            <tr>\n")
              .append("                <th>ID</th>\n")
              .append("                <th>Matricule</th>\n")
              .append("                <th>Nom</th>\n")
              .append("                <th>Prénom</th>\n")
              .append("                <th>Classe</th>\n")
              .append("                <th>Période</th>\n")
              .append("                <th>Année</th>\n")
              .append("            </tr>\n")
              .append("        </thead>\n")
              .append("        <tbody>\n");

            for (Etudiant e : etudiants) {
                sb.append("            <tr>\n")
                  .append("                <td>").append(echapperHtml(e.getNumeroIdentifiantEtudiant())).append("</td>\n")
                  .append("                <td>").append(echapperHtml(e.getMatricule())).append("</td>\n")
                  .append("                <td>").append(echapperHtml(e.getNom())).append("</td>\n")
                  .append("                <td>").append(echapperHtml(e.getPrenom())).append("</td>\n")
                  .append("                <td>").append(echapperHtml(e.getClasse())).append("</td>\n")
                  .append("                <td>").append(echapperHtml(e.getPeriode())).append("</td>\n")
                  .append("                <td>").append(echapperHtml(e.getAnneeAcademique())).append("</td>\n")
                  .append("            </tr>\n");
            }

            sb.append("        </tbody>\n")
              .append("    </table>\n")
              .append("    <p><strong>Total : ").append(etudiants.size()).append(" étudiants</strong></p>\n");
        }

        sb.append("    <div class=\"footer\">\n")
          .append("        © ").append(java.time.Year.now().getValue()).append(" M-TECH - Impression générée depuis Scolarité\n")
          .append("    </div>\n")
          .append("    <div class=\"no-print\" style=\"text-align:center; margin-top:20px;\">\n")
          .append("        <button onclick=\"window.print()\" style=\"padding:10px 20px; background:#1e40af; color:white; border:none; border-radius:6px; cursor:pointer;\">\n")
          .append("            <i class=\"fas fa-print\"></i> Imprimer\n")
          .append("        </button>\n")
          .append("        <button onclick=\"window.close()\" style=\"padding:10px 20px; background:#94a3b8; color:white; border:none; border-radius:6px; cursor:pointer; margin-left:10px;\">\n")
          .append("            Fermer\n")
          .append("        </button>\n")
          .append("    </div>\n")
          .append("</body>\n")
          .append("</html>");
        return sb.toString();
    }

    // ==================== MÉTHODES UTILITAIRES ====================

    private String getInstitutionIdFromSession(HttpExchange exchange) {
        try {
            String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
            if (cookieHeader == null) return null;
            String sessionId = extractSessionId(cookieHeader);
            if (sessionId == null) return null;
            HttpSession session = SessionManager.getSession(sessionId);
            if (session == null) return null;
            return (String) session.getAttribute("institutionId");
        } catch (Exception e) {
            LOGGER.warning(() -> "Erreur getInstitutionIdFromSession: " + e.getMessage());
            return null;
        }
    }

    private String getUtilisateurIdFromSession(HttpExchange exchange) {
        try {
            String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
            if (cookieHeader == null) return null;
            String sessionId = extractSessionId(cookieHeader);
            if (sessionId == null) return null;
            HttpSession session = SessionManager.getSession(sessionId);
            if (session == null) return null;
            String userId = (String) session.getAttribute("userId");
            if (userId == null || userId.isBlank()) {
                userId = (String) session.getAttribute("username");
            }
            return userId;
        } catch (Exception e) {
            LOGGER.warning(() -> "Erreur getUtilisateurIdFromSession: " + e.getMessage());
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

    private AccesAdmin chargerPermissions(String utilisateurId, String institutionId) {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            AccesAdminData dao = new AccesAdminData(conn);
            return dao.loadByUtilisateurId(utilisateurId, institutionId);
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erreur chargement permissions", e);
            return new AccesAdmin();
        }
    }

    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null) return params;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=");
            if (kv.length == 2) {
                try {
                    params.put(java.net.URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                               java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
                } catch (Exception ignored) {}
            }
        }
        return params;
    }

    private void sendHtml(HttpExchange exchange, int status, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String echapperHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }
}