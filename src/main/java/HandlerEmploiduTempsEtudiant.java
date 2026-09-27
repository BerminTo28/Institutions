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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class HandlerEmploiduTempsEtudiant implements HttpHandler {

    private static final Logger LOGGER =
            Logger.getLogger(HandlerEmploiduTempsEtudiant.class.getName());

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();

        // OPTIONS CORS
        if (ResponseUtil.isOptionsRequest(exchange)) {
            ResponseUtil.sendOptions(exchange);
            return;
        }

        if (!"GET".equalsIgnoreCase(method)) {
            ResponseUtil.sendError(exchange, 405, "Méthode non autorisée");
            return;
        }

        try {
            String numeroEtudiant = getNumeroEtudiantFromSession(exchange);
            String institutionId = getInstitutionIdFromSession(exchange);

            if (numeroEtudiant == null || institutionId == null) {
                exchange.getResponseHeaders().set("Location", "/connexion");
                exchange.sendResponseHeaders(302, -1);
                return;
            }

            // ✅ Vérification des droits dans le try-with-resources
            boolean aDroitEdt = verifierDroitEdt(numeroEtudiant, institutionId);
            if (!aDroitEdt) {
                LOGGER.warning(() -> "🚫 Accès EDT refusé pour " + numeroEtudiant);
                String html = UIEmploiduTempsEtudiant.renderError(
                        "Vous n'avez pas l'autorisation de consulter l'emploi du temps.");
                ResponseUtil.sendHtml(exchange, 403, html);
                return;
            }

            handleGet(exchange, numeroEtudiant, institutionId);

        } catch (IOException e) {
            if (isNetworkException(e)) {
                LOGGER.fine(() -> "Client déconnecté : " + e.getMessage());
                return;
            }
            LOGGER.log(Level.SEVERE, "Erreur I/O dans HandlerEmploiduTempsEtudiant", e);
            safeSendError(exchange, 500, "Erreur interne.");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur SQL dans HandlerEmploiduTempsEtudiant", e);
            safeSendError(exchange, 500, "Erreur base de données.");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erreur inattendue dans HandlerEmploiduTempsEtudiant", e);
            safeSendError(exchange, 500, "Erreur interne.");
        }
    }

    // ============================================================
    // GET — AFFICHAGE DE L'EMPLOI DU TEMPS
    // ============================================================
    private void handleGet(HttpExchange exchange, String numeroEtudiant, String institutionId)
            throws IOException, SQLException {

        Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());

        String action = params.getOrDefault("action", "");
        String anneeParam = params.getOrDefault("annee", "");
        String periodeParam = params.getOrDefault("periode", "");
        String jourParam = params.getOrDefault("jour", "");

        LOGGER.info(() -> "📋 EDT étudiant=" + numeroEtudiant
                + " | action=" + action
                + " | annee=" + anneeParam
                + " | periode=" + periodeParam
                + " | jour=" + jourParam);

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {

            // 1. Récupérer l'étudiant
            EtudiantData etudiantData = new EtudiantData(conn);
            Etudiant etudiant = etudiantData.read(numeroEtudiant,institutionId);
            if (etudiant == null) {
                ResponseUtil.sendHtml(exchange, 404,
                        UIEmploiduTempsEtudiant.renderError("Étudiant non trouvé."));
                return;
            }

            final String classe = etudiant.getClasse();
            if (classe == null || classe.isBlank()) {
                ResponseUtil.sendHtml(exchange, 404,
                        UIEmploiduTempsEtudiant.renderError(
                                "Aucune classe associée à cet étudiant."));
                return;
            }

            // 2. Années disponibles (avec fallback)
            ProfesseurData profData = new ProfesseurData(conn);
            final List<String> anneesDispo = chargerAnneesDisponibles(
                    conn, profData, institutionId, classe);

            // 3. Sélection automatique de l'année
            final String annee = anneeParam.isEmpty() && !anneesDispo.isEmpty()
                    ? anneesDispo.get(0)
                    : anneeParam;

            LOGGER.info(() -> "📅 Années=" + anneesDispo + " | sélectionnée=" + annee);

            // 4. Périodes disponibles (avec fallback)
            final List<String> periodesDispo = chargerPeriodesDisponibles(
                    conn, profData, institutionId, classe, annee);

            final String periode = periodeParam.isEmpty() && !periodesDispo.isEmpty()
                    ? periodesDispo.get(0)
                    : periodeParam;

            LOGGER.info(() -> "📅 Périodes=" + periodesDispo + " | sélectionnée=" + periode);

            // 5. Jours disponibles (avec fallback) — ✅ pas de conn
            final List<String> joursDispo = chargerJoursDisponibles(
                    profData, institutionId, classe, annee, periode);

            LOGGER.info(() -> "📅 Jours=" + joursDispo);

            // 6. Créneaux
            final boolean demandeResultats = "filter".equals(action)
                    || (!annee.isEmpty() && !periode.isEmpty());

            final List<Map<String, Object>> creneaux = demandeResultats
                    ? safeList(profData.getCreneauxParClasse(
                            institutionId, classe, annee, periode, jourParam))
                    : new ArrayList<>();

            final boolean showResults = demandeResultats;

            // 7. Message
            final String message = construireMessage(demandeResultats, creneaux, action);

            LOGGER.info(() -> "📊 showResults=" + showResults
                    + " | creneaux=" + creneaux.size()
                    + " | message=" + message);

            // 8. HTML
            String html = UIEmploiduTempsEtudiant.render(
                    classe, anneesDispo, periodesDispo, joursDispo,
                    annee, periode, jourParam, creneaux, showResults, message);
            ResponseUtil.sendHtml(exchange, 200, html);
        }
    }

    // ============================================================
    // UTILITAIRES (assignations uniques)
    // ============================================================

    /**
     * ✅ Charge les années disponibles pour une classe.
     *    Fallback : années académiques de l'institution.
     */
    private static List<String> chargerAnneesDisponibles(Connection conn,
                                                          ProfesseurData profData,
                                                          String institutionId,
                                                          String classe) throws SQLException {
        List<String> annees = safeList(profData.getAnneesByClasse(institutionId, classe));
        if (!annees.isEmpty()) return annees;

        LOGGER.info(() -> "Aucune année trouvée pour classe=" + classe
                + " → fallback institution");
        AnneeAcademiqueData anneeData = new AnneeAcademiqueData(conn);
        return anneeData.listByInstitution(institutionId).stream()
                .map(AnneeAcademique::getAnneeAcademique)
                .toList();
    }

    /**
     * ✅ Charge les périodes disponibles pour une classe et une année.
     *    Fallback : périodes de l'institution (référentiel global).
     */
    private static List<String> chargerPeriodesDisponibles(Connection conn,
                                                            ProfesseurData profData,
                                                            String institutionId,
                                                            String classe,
                                                            String annee) throws SQLException {
        if (annee.isEmpty()) return new ArrayList<>();

        List<String> periodes = safeList(profData.getPeriodesByClasse(
                institutionId, classe, annee));
        if (!periodes.isEmpty()) return periodes;

        LOGGER.info(() -> "Aucune période trouvée pour classe=" + classe
                + " / annee=" + annee + " → fallback institution");
        PeriodeData periodeData = new PeriodeData(conn);
        return periodeData.listByInstitution(institutionId).stream()
                .map(Periode::getPeriode)   // adapter au getter réel
                .toList();
    }

    /**
     * ✅ Charge les jours disponibles pour une classe, une année et une période.
     *    Fallback : jours standards de la semaine si aucun jour n'est trouvé.
     */
    private static List<String> chargerJoursDisponibles(ProfesseurData profData,
                                                         String institutionId,
                                                         String classe,
                                                         String annee,
                                                         String periode) throws SQLException {
        if (annee.isEmpty() || periode.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> jours = safeList(profData.getJoursByClasse(
                institutionId, classe, annee, periode));
        if (!jours.isEmpty()) return jours;

        LOGGER.info(() -> "Aucun jour trouvé pour classe=" + classe
                + " / annee=" + annee + " / periode=" + periode
                + " → fallback jours standards");
        return List.of("Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi");
    }

    /**
     * ✅ Construit le message d'erreur ou d'information à afficher.
     *    Retourne null si aucun message n'est nécessaire.
     */
    private static String construireMessage(boolean demandeResultats,
                                             List<Map<String, Object>> creneaux,
                                             String action) {
        if (demandeResultats && creneaux.isEmpty()) {
            return "Aucun cours trouvé pour les filtres sélectionnés.";
        }
        if (!action.isEmpty() && !demandeResultats) {
            return "Veuillez sélectionner une année et une période.";
        }
        return null;
    }

    // ============================================================
    // VÉRIFICATION DES DROITS
    // ============================================================
    private boolean verifierDroitEdt(String numeroEtudiant, String institutionId) {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            AccesEtudiantData accesData = new AccesEtudiantData(conn);
            AccesEtudiant acces = accesData.getByEtudiant(numeroEtudiant, institutionId);
            LOGGER.info(() -> "🔍 Vérif acces EDT pour " + numeroEtudiant
                    + " | acces=" + (acces == null ? "NULL" : "trouvé")
                    + (acces != null ? " | edt=" + acces.isEdt() : ""));
            return acces != null && acces.isEdt();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur vérification droits EDT", e);
            return false;
        }
    }

    // ============================================================
    // HELPERS
    // ============================================================
    private static <T> List<T> safeList(List<T> list) {
        return list != null ? list : new ArrayList<>();
    }

    private void safeSendError(HttpExchange exchange, int status, String message) {
        try {
            String html = UIEmploiduTempsEtudiant.renderError(message);
            ResponseUtil.sendHtml(exchange, status, html);
        } catch (IOException e) {
            if (!isNetworkException(e)) {
                LOGGER.log(Level.WARNING, "Impossible d'envoyer la page d'erreur", e);
            }
        }
    }

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

    // ============================================================
    // SESSION
    // ============================================================
    private String getNumeroEtudiantFromSession(HttpExchange exchange) {
        HttpSession session = getSession(exchange);
        return session == null ? null : (String) session.getAttribute("userId");
    }

    private String getInstitutionIdFromSession(HttpExchange exchange) {
        HttpSession session = getSession(exchange);
        if (session == null) return null;
        String instId = (String) session.getAttribute("institutionId");
        if (instId == null || instId.isBlank()) {
            instId = (String) session.getAttribute("institution_id");
        }
        return instId;
    }

    private HttpSession getSession(HttpExchange exchange) {
        try {
            String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
            if (cookieHeader == null) return null;
            String sessionId = extractSessionId(cookieHeader);
            if (sessionId == null) return null;
            return SessionManager.getSession(sessionId);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erreur récupération session", e);
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

    // ============================================================
    // PARSING
    // ============================================================
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
                } catch (Exception ignored) {}
            }
        }
        return params;
    }
}