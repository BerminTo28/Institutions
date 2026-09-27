import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HandlerEtudiantNotes implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(HandlerEtudiantNotes.class.getName());

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        if (!"GET".equalsIgnoreCase(method)) {
            ResponseUtil.sendError(exchange, 405, "Méthode non autorisée");
            return;
        }

        try {
            // 1. Récupérer l'étudiant depuis la session
            String numeroEtudiant = getNumeroEtudiantFromSession(exchange);
            String institutionId = getInstitutionIdFromSession(exchange);

            if (numeroEtudiant == null || institutionId == null) {
                exchange.getResponseHeaders().set("Location", "/connexion");
                exchange.sendResponseHeaders(302, -1);
                return;
            }

            // 2. Vérifier les droits d'accès (notes)
            boolean aDroitNotes = verifierDroitNotes(numeroEtudiant, institutionId);
            if (!aDroitNotes) {
                LOGGER.warning(() -> "🚫 Accès notes refusé pour " + numeroEtudiant);
                String html = UINotesEtudiant.renderError(
                        "Vous n'avez pas l'autorisation de consulter vos notes.");
                ResponseUtil.sendHtml(exchange, 403, html);
                return;
            }

            // 3. Récupérer les paramètres de filtrage (annee, periode)
            Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
            final String annee = params.getOrDefault("annee", "");
            final String periode = params.getOrDefault("periode", "");

            // 4. Traitement principal
            try (Connection conn = DatabaseManager.getInstance().getConnection()) {
                NoteData noteData = new NoteData(conn);

                // ✅ Années : une seule assignation → effectivement final
                final List<String> anneesDispo = getAnneesInstitution(conn, institutionId);
                LOGGER.info(() -> "📅 Années=" + anneesDispo);

                // ✅ Périodes : une seule assignation via méthode dédiée
                final List<String> periodesDispo = annee.isEmpty()
                        ? new ArrayList<>()
                        : getPeriodesInstitution(conn, institutionId, annee);
                LOGGER.info(() -> "📅 Périodes=" + periodesDispo);

                // ✅ Notes : une seule assignation via méthode dédiée
                final List<Note> notes = (!annee.isEmpty() && !periode.isEmpty())
                        ? noteData.getNotesByEtudiant(numeroEtudiant, institutionId, annee, periode)
                        : null;
                LOGGER.info(() -> "📝 Notes trouvées : "
                        + (notes == null ? "null" : notes.size()));

                // 5. Générer la page HTML
                String html = UINotesEtudiant.render(numeroEtudiant, anneesDispo,
                        periodesDispo, annee, periode, notes);
                ResponseUtil.sendHtml(exchange, 200, html);

            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "Erreur base de données", e);
                String html = UINotesEtudiant.renderError(
                        "Erreur lors de la récupération des notes.");
                ResponseUtil.sendHtml(exchange, 500, html);
            }

        } catch (IOException e) {
            if (isNetworkException(e)) {
                LOGGER.fine(() -> "Client déconnecté : " + e.getMessage());
                return;
            }
            LOGGER.log(Level.SEVERE, "Erreur I/O dans HandlerEtudiantNotes", e);
            try {
                String html = UINotesEtudiant.renderError("Erreur interne.");
                ResponseUtil.sendHtml(exchange, 500, html);
            } catch (IOException ignored) {}
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erreur inattendue dans HandlerEtudiantNotes", e);
            try {
                String html = UINotesEtudiant.renderError("Erreur interne.");
                ResponseUtil.sendHtml(exchange, 500, html);
            } catch (IOException ignored) {}
        }
    }

    // ==================== VÉRIFICATION DES DROITS ====================

    /**
     * ✅ Vérifie que l'étudiant a bien le droit `notes`.
     *    Ouvre sa propre connexion (pas de try-with-resources vide).
     */
    private boolean verifierDroitNotes(String numeroEtudiant, String institutionId) {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            AccesEtudiantData accesData = new AccesEtudiantData(conn);
            AccesEtudiant acces = accesData.getByEtudiant(numeroEtudiant, institutionId);

            LOGGER.info(() -> "🔍 Vérif acces pour " + numeroEtudiant
                    + " | institution=" + institutionId
                    + " | acces=" + (acces == null ? "NULL" : "trouvé")
                    + (acces != null ? " | notes=" + acces.isNotes() : ""));

            return acces != null && acces.isNotes();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur vérification droits notes", e);
            return false;
        }
    }

    // ==================== RÉCUPÉRATION ANNÉES / PÉRIODES ====================

    /**
     * ✅ Récupère TOUTES les années académiques de l'institution
     *    (sans filtre étudiant, comme HandlerEtudiantDocuments).
     */
    private List<String> getAnneesInstitution(Connection conn, String institutionId)
            throws SQLException {
        List<String> annees = new ArrayList<>();
        String sql = "SELECT annee_academique FROM " + MigrationManager.TABLE_ANNEES_ACADEMIQUES
                + " WHERE institution_id = ? ORDER BY annee_academique DESC";
        try (java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    annees.add(rs.getString("annee_academique"));
                }
            }
        }
        return annees;
    }

    /**
     * ✅ Récupère TOUTES les périodes de l'institution pour une année donnée
     *    (sans filtre étudiant, comme HandlerEtudiantDocuments).
     */
    private List<String> getPeriodesInstitution(Connection conn, String institutionId,
                                                  String annee) throws SQLException {
        List<String> periodes = new ArrayList<>();
        String sql = "SELECT periode FROM " + MigrationManager.TABLE_PERIODES
                + " WHERE institution_id = ? AND annee_academique = ? ORDER BY periode";
        try (java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            ps.setString(2, annee);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    periodes.add(rs.getString("periode"));
                }
            }
        }
        return periodes;
    }

    // ==================== MÉTHODES DE SESSION ====================

    private String getNumeroEtudiantFromSession(HttpExchange exchange) {
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

    // ==================== PARSING DES PARAMÈTRES ====================

    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null) return params;
        for (String pair : query.split("&")) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                try {
                    String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                    String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                    params.put(key, value);
                } catch (Exception e) {
                    // ignore
                }
            }
        }
        return params;
    }

    // ==================== DÉTECTION ERREUR RÉSEAU ====================

    private static boolean isNetworkException(IOException e) {
        if (e == null) return false;
        String msg = e.getMessage();
        if (msg == null) {
            Throwable cause = e.getCause();
            if (cause != null && cause.getMessage() != null) {
                msg = cause.getMessage();
            }
        }
        if (msg == null) return false;
        String lower = msg.toLowerCase();
        return lower.contains("aborted")
            || lower.contains("broken pipe")
            || lower.contains("connection reset")
            || lower.contains("insufficient bytes")
            || lower.contains("connection closed")
            || lower.contains("software in your host machine");
    }
}