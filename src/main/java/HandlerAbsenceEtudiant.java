import java.io.IOException;
import java.io.OutputStream;
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

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class HandlerAbsenceEtudiant implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(HandlerAbsenceEtudiant.class.getName());
    private final AbsenceManager absenceManager;
    private final Gson gson = new Gson();

    public HandlerAbsenceEtudiant() {
        this.absenceManager = new AbsenceManager();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        final String path = exchange.getRequestURI().getPath();
        final String method = exchange.getRequestMethod();

        LOGGER.info(() -> "HandlerAbsenceEtudiant appelé : " + method + " " + path);

        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

        if ("OPTIONS".equalsIgnoreCase(method)) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        if (!"GET".equalsIgnoreCase(method)) {
            sendError(exchange, 405, "Méthode non autorisée (seul GET est supporté)");
            return;
        }

        try {
            final String etudiantId = getEtudiantIdFromSession(exchange);
            final String institutionId = getInstitutionIdFromSession(exchange);

            if (etudiantId == null || institutionId == null) {
                exchange.getResponseHeaders().set("Location", "/connexion");
                exchange.sendResponseHeaders(302, -1);
                return;
            }

            final boolean aDroitAbsences = verifierDroitAbsences(etudiantId, institutionId);
            if (!aDroitAbsences) {
                LOGGER.warning(() -> "🚫 Accès absences refusé pour " + etudiantId);
                if (estRouteApi(path)) {
                    sendError(exchange, 403,
                            "Accès non autorisé : vous n'avez pas le droit 'absences'.");
                } else {
                    exchange.getResponseHeaders().set("Location",
                            "/etudiant/dashboard?error=acces_refuse_absences");
                    exchange.sendResponseHeaders(302, -1);
                }
                return;
            }

            // --- PAGE HTML ---
            if (path.equals("/etudiant/absences") || path.equals("/etudiant/absences/")) {
                handlePageHtml(exchange, etudiantId, institutionId);
                return;
            }

            // --- ROUTES API ---
            if (path.endsWith("/list")) {
                handleList(exchange, etudiantId, institutionId);
            } else if (path.endsWith("/annees")) {
                handleGetAnnees(exchange, institutionId);
            } else if (path.endsWith("/periodes")) {
                handleGetPeriodes(exchange, institutionId);
            } else if (path.endsWith("/classes")) {
                handleGetClasses(exchange, etudiantId, institutionId);
            } else {
                sendError(exchange, 404, "Route GET non trouvée");
            }

        } catch (IOException e) {
            if (isNetworkException(e)) {
                LOGGER.fine(() -> "Client déconnecté : " + e.getMessage());
                return;
            }
            LOGGER.log(Level.SEVERE, "Erreur I/O dans HandlerAbsenceEtudiant", e);
            safeSendError(exchange, 500, "Erreur interne.");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur SQL dans HandlerAbsenceEtudiant", e);
            safeSendError(exchange, 500, "Erreur base de données.");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erreur inattendue dans HandlerAbsenceEtudiant", e);
            safeSendError(exchange, 500, "Erreur interne.");
        }
    }

    // ==================== VÉRIFICATION DES DROITS ====================
    private boolean verifierDroitAbsences(String etudiantId, String institutionId) {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            AccesEtudiantData accesData = new AccesEtudiantData(conn);
            AccesEtudiant acces = accesData.getByEtudiant(etudiantId, institutionId);
            LOGGER.info(() -> "🔍 Vérif acces absences pour " + etudiantId
                    + " | acces=" + (acces == null ? "NULL" : "trouvé")
                    + (acces != null ? " | absences=" + acces.isAbsences() : ""));
            return acces != null && acces.isAbsences();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur vérification droits absences", e);
            return false;
        }
    }

    // ================== PAGE HTML ==================
    private void handlePageHtml(HttpExchange exchange, String etudiantId, String institutionId)
            throws IOException {
        String html = UIAbsenceEtudiant.renderPage(institutionId, etudiantId);
        sendHtml(exchange, 200, html);
    }

    // ================== API ==================

    /**
     * ✅ Absences filtrées — utilise le numéro identifiant de la SESSION.
     */
   /**
 * ✅ Recherche des absences — la classe est FORCÉE à celle de l'étudiant.
 *    L'étudiant ne peut JAMAIS voir les absences d'une autre classe.
 */
private void handleList(HttpExchange exchange, String etudiantId, String institutionId)
        throws IOException, SQLException {

    Map<String, String> queryParams = parseQueryParams(exchange.getRequestURI().getQuery());
    final String annee = queryParams.get("anneeAcademique");
    final String periode = queryParams.get("periode");

    // ✅ Récupérer la classe de l'étudiant DEPUIS LA BASE (pas depuis le client)
    String classeEtudiant = null;
    try (Connection conn = DatabaseManager.getInstance().getConnection()) {
        EtudiantData etudiantData = new EtudiantData(conn);
        Etudiant etudiant = etudiantData.read(etudiantId,institutionId);
        if (etudiant != null && institutionId.equals(etudiant.getInstitutionId())) {
            classeEtudiant = etudiant.getClasse();
        }
    }

    if (classeEtudiant == null || classeEtudiant.isBlank()) {
        LOGGER.warning(() -> "⚠️ Aucune classe pour l'étudiant " + etudiantId);
        sendSuccess(exchange, new ArrayList<>());
        return;
    }

    // ✅ Classe effectivement finale pour les lambdas
    final String classe = classeEtudiant;

    LOGGER.info(() -> "🔍 Recherche absences : étudiant=" + etudiantId
            + " | classe=" + classe
            + " | annee=" + annee
            + " | periode=" + periode);

    List<Absence> absences = absenceManager.rechercherAbsences(
            "ETUDIANT", annee, periode, classe, institutionId, etudiantId);

    LOGGER.info(() -> "📋 Absences trouvées : "
            + (absences == null ? "null" : absences.size()));

    sendSuccess(exchange, absences);
}

    /**
     * ✅ Années académiques : depuis la table `annees_academiques` (référentiel).
     *    Plus de dépendance à la table `absences`.
     */
    private void handleGetAnnees(HttpExchange exchange, String institutionId)
            throws IOException, SQLException {

        LOGGER.info(() -> "📅 Chargement des années académiques pour " + institutionId);

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            AnneeAcademiqueData anneeData = new AnneeAcademiqueData(conn);
            List<AnneeAcademique> annees = anneeData.listByInstitution(institutionId);

            // ✅ Convertir en liste de libellés
            List<String> libelles = annees.stream()
                    .map(AnneeAcademique::getAnneeAcademique)
                    .toList();

            LOGGER.info(() -> "📅 Années trouvées : " + libelles);
            sendSuccess(exchange, libelles);
        }
    }

    /**
     * ✅ Périodes : depuis la table `periodes` (référentiel).
     *    Filtre optionnel sur l'année.
     */
    private void handleGetPeriodes(HttpExchange exchange, String institutionId)
            throws IOException, SQLException {

        Map<String, String> queryParams = parseQueryParams(exchange.getRequestURI().getQuery());
        final String annee = queryParams.get("anneeAcademique");

        LOGGER.info(() -> "📅 Chargement des périodes pour " + institutionId
                + " | annee=" + annee);

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            PeriodeData periodeData = new PeriodeData(conn);
            List<Periode> periodes = periodeData.listByInstitution(institutionId);

            // ✅ Filtrer par année si fournie
            List<String> libelles = periodes.stream()
                    .filter(p -> annee == null || annee.isBlank()
                            || annee.equals(p.getAnneeAcademique()))
                    .map(Periode::getPeriode)
                    .toList();

            LOGGER.info(() -> "📅 Périodes trouvées : " + libelles);
            sendSuccess(exchange, libelles);
        }
    }

    /**
     * ✅ Classes : depuis la table `classes` (référentiel).
     *    Filtre optionnel sur l'année.
     */
  /**
 * ✅ Retourne UNIQUEMENT la classe de l'étudiant (pas toutes les classes).
 *    L'étudiant ne peut voir que ses propres absences.
 */
private void handleGetClasses(HttpExchange exchange, String etudiantId, String institutionId)
        throws IOException, SQLException {

    LOGGER.info(() -> "📚 Chargement de la classe de l'étudiant " + etudiantId);

    try (Connection conn = DatabaseManager.getInstance().getConnection()) {
        EtudiantData etudiantData = new EtudiantData(conn);
        Etudiant etudiant = etudiantData.read(etudiantId,institutionId);

        if (etudiant == null || !institutionId.equals(etudiant.getInstitutionId())) {
            LOGGER.warning(() -> "⚠️ Étudiant introuvable : " + etudiantId);
            sendSuccess(exchange, new ArrayList<String>());
            return;
        }

        String classe = etudiant.getClasse();
        if (classe == null || classe.isBlank()) {
            LOGGER.warning(() -> "⚠️ Aucune classe pour l'étudiant " + etudiantId);
            sendSuccess(exchange, new ArrayList<String>());
            return;
        }

        List<String> classes = List.of(classe);
        LOGGER.info(() -> "📚 Classe de l'étudiant : " + classes);
        sendSuccess(exchange, classes);
    }
}
    // ================== SESSION ==================
    private String getEtudiantIdFromSession(HttpExchange exchange) {
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
            String[] parts = cookie.trim().split("=", 2);
            if (parts.length == 2 && "SESSION_ID".equals(parts[0])) {
                return parts[1];
            }
        }
        return null;
    }

    // ================== OUTILS ==================
    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> map = new HashMap<>();
        if (query != null && !query.isBlank()) {
            for (String param : query.split("&")) {
                String[] pair = param.split("=", 2);
                if (pair.length == 2) {
                    try {
                        map.put(pair[0], URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
                    } catch (Exception ignored) {}
                } else if (pair.length == 1) {
                    map.put(pair[0], "");
                }
            }
        }
        return map;
    }

    private static boolean estRouteApi(String path) {
        return path != null && (path.endsWith("/list")
                || path.endsWith("/annees")
                || path.endsWith("/periodes")
                || path.endsWith("/classes")
                || path.contains("/api/"));
    }

    // ================== SEND ==================
    private void sendSuccess(HttpExchange exchange, Object data) throws IOException {
        if (exchange.getResponseCode() != -1) return;
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("data", data);
        byte[] bytes = gson.toJson(resp).getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendError(HttpExchange exchange, int status, String message) throws IOException {
        if (exchange.getResponseCode() != -1) return;
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", false);
        resp.put("error", message);
        resp.put("message", message);
        byte[] bytes = gson.toJson(resp).getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendHtml(HttpExchange exchange, int status, String html) throws IOException {
        if (exchange.getResponseCode() != -1) return;
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void safeSendError(HttpExchange exchange, int status, String message) {
        try {
            sendError(exchange, status, message);
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
}