import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class HandlerEtudiantPaiements implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(HandlerEtudiantPaiements.class.getName());

    // ============================================================
    // CONFIGURATION MONCASH (via variables d'environnement)
    // ============================================================
    private static String getMonCashClientId() {
        String v = System.getenv("MONCASH_CLIENT_ID");
        if (v == null || v.isBlank()) {
            throw new IllegalStateException(
                    "❌ Variable d'environnement MONCASH_CLIENT_ID non définie.");
        }
        return v;
    }

    private static String getMonCashClientSecret() {
        String v = System.getenv("MONCASH_CLIENT_SECRET");
        if (v == null || v.isBlank()) {
            throw new IllegalStateException(
                    "❌ Variable d'environnement MONCASH_CLIENT_SECRET non définie.");
        }
        return v;
    }

    // ============================================================
    // MÉTHODE PRINCIPALE
    // ============================================================
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        LOGGER.info(() -> "💳 HandlerEtudiantPaiements - " + method + " " + path);

        try {
            String numeroEtudiant = getNumeroEtudiantFromSession(exchange);
            String institutionId = getInstitutionIdFromSession(exchange);

            if (numeroEtudiant == null || numeroEtudiant.isBlank()
                    || institutionId == null || institutionId.isBlank()) {
                LOGGER.warning("⛔ Session invalide - Redirection vers connexion");
                exchange.getResponseHeaders().set("Location", "/connexion");
                exchange.sendResponseHeaders(302, -1);
                return;
            }

            // Vérifier les droits d'accès (paiement)
            AccesEtudiantData accesData = new AccesEtudiantData();
            AccesEtudiant acces = accesData.getByEtudiant(numeroEtudiant, institutionId);
            if (acces == null || !acces.isPaiement()) {
                LOGGER.warning(() -> "🚫 Accès paiement refusé pour " + numeroEtudiant);
                String html = UIEtudiantPaiement.renderError(
                        "Vous n'avez pas l'autorisation de consulter vos paiements.");
                ResponseUtil.sendHtml(exchange, 403, html);
                return;
            }

            if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)) {
                handleGet(exchange, numeroEtudiant, institutionId);
            } else if ("POST".equalsIgnoreCase(method)) {
                handlePost(exchange, numeroEtudiant, institutionId);
            } else if ("OPTIONS".equalsIgnoreCase(method)) {
                exchange.getResponseHeaders().set("Allow", "GET, POST, HEAD, OPTIONS");
                exchange.sendResponseHeaders(204, -1);
            } else {
                ResponseUtil.sendError(exchange, 405, "Méthode non autorisée");
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "❌ Erreur SQL dans HandlerEtudiantPaiements", e);
            try {
                String html = UIEtudiantPaiement.renderError(
                        "Erreur base de données : " + e.getMessage());
                ResponseUtil.sendHtml(exchange, 500, html);
            } catch (IOException ignored) {}
        } catch (IOException e) {
            if (isNetworkException(e)) {
                LOGGER.fine(() -> "Client déconnecté : " + e.getMessage());
                return;
            }
            LOGGER.log(Level.SEVERE, "❌ Erreur I/O dans HandlerEtudiantPaiements", e);
            try {
                String html = UIEtudiantPaiement.renderError("Erreur interne.");
                ResponseUtil.sendHtml(exchange, 500, html);
            } catch (IOException ignored) {}
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "❌ Erreur inattendue dans HandlerEtudiantPaiements", e);
            try {
                String html = UIEtudiantPaiement.renderError("Erreur interne.");
                ResponseUtil.sendHtml(exchange, 500, html);
            } catch (IOException ignored) {}
        }
    }

    // ============================================================
    // GET – AFFICHAGE DE LA PAGE
    // ============================================================
  private void handleGet(HttpExchange exchange, String numeroEtudiant, String institutionId)
        throws IOException, SQLException {

    GestionFinanciereService service = new GestionFinanciereService(institutionId);

    Etudiant etudiant = getEtudiant(numeroEtudiant, institutionId);
    if (etudiant == null) {
        String html = UIEtudiantPaiement.renderError("Étudiant non trouvé.");
        ResponseUtil.sendHtml(exchange, 404, html);
        return;
    }

    final String classe = etudiant.getClasse();

    // ✅ Année : une seule assignation via ternaire
    final String anneeBrute = service.getAnneeCourante();
    final String annee = (anneeBrute == null || anneeBrute.isBlank())
            ? getAnneeCouranteFallback()
            : anneeBrute;

    // ✅ Frais + modalités via GestionFinanciereData
    GestionFinanciereData data = new GestionFinanciereData(institutionId);
    final double fraisTotal = data.getFraisScolaire(classe, annee);
    final Map<Integer, Double> modalites = data.getModalites(classe, annee);

    // ✅ Total payé : extrait dans une méthode utilitaire → assignation unique
    final double totalPaye = extraireTotalPaye(
            service.getInfosFinancieresEtudiant(numeroEtudiant, annee, classe));

    final double reste = Math.max(0, fraisTotal - totalPaye);

    // ✅ Paiements : une seule assignation
    final List<Map<String, Object>> paiements = service.getPaiementsEtudiants()
            .stream()
            .filter(p -> numeroEtudiant.equals(p.get("numero_identifiant")))
            .toList();

    LOGGER.info(() -> "📊 Bilan : classe=" + classe
            + " | annee=" + annee
            + " | fraisTotal=" + fraisTotal
            + " | totalPaye=" + totalPaye
            + " | reste=" + reste
            + " | modalites=" + modalites.size()
            + " | paiements=" + paiements.size());

    String html = UIEtudiantPaiement.render(
            etudiant, annee, fraisTotal, modalites, totalPaye, reste, paiements);
    ResponseUtil.sendHtml(exchange, 200, html);
}

// ============================================================
// UTILITAIRE : extraction du total payé
// ============================================================
/**
 * ✅ Extrait la valeur "totalPaye" de la map renvoyée par le service.
 *    Retourne 0.0 si la clé est absente ou non numérique.
 */
private static double extraireTotalPaye(Map<String, Object> infos) {
    if (infos == null) return 0.0;
    Object v = infos.get("totalPaye");
    if (v instanceof Number n) {
        return n.doubleValue();
    }
    return 0.0;
}
    // ============================================================
    // POST – TRAITEMENT DU PAIEMENT
    // ============================================================
   private void handlePost(HttpExchange exchange, String numeroEtudiant, String institutionId)
        throws IOException {

    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    Map<String, String> params = parseFormData(body);
    String montantStr = params.get("montant");
    String moyen = params.get("moyen");

    if (montantStr == null || montantStr.isBlank()
            || moyen == null || moyen.isBlank()) {
        sendJsonError(exchange, "Montant et moyen de paiement requis.");
        return;
    }

    double montant;
    try {
        montant = Double.parseDouble(montantStr);
    } catch (NumberFormatException e) {
        sendJsonError(exchange, "Montant invalide.");
        return;
    }

    if (montant <= 0) {
        sendJsonError(exchange, "Le montant doit être supérieur à zéro.");
        return;
    }

    try {
        // ✅ Utiliser GestionFinanciereData (génère UUID + source)
        GestionFinanciereService service = new GestionFinanciereService(institutionId);
        GestionFinanciereData data = new GestionFinanciereData(institutionId);

        if (getEtudiant(numeroEtudiant, institutionId) == null) {
            sendJsonError(exchange, "Étudiant non trouvé.");
            return;
        }

        if ("MonCash".equalsIgnoreCase(moyen)) {
            handleMonCashPayment(exchange, numeroEtudiant, institutionId, montant, moyen);
            return;
        }

        // ✅ Année courante
        String anneeBrute = service.getAnneeCourante();
        String annee = (anneeBrute == null || anneeBrute.isBlank())
                ? getAnneeCouranteFallback()
                : anneeBrute;

        String reference = "PAY-" + System.currentTimeMillis() + "-" + numeroEtudiant;

        // ✅ Appel à GestionFinanciereData (génère UUID, colonne id OK)
        data.enregistrerPaiementEtudiant(
                numeroEtudiant, montant, moyen, reference, annee);

        LOGGER.info(() -> "✅ Paiement enregistré : " + reference
                + " | étudiant=" + numeroEtudiant
                + " | montant=" + montant
                + " | moyen=" + moyen
                + " | annee=" + annee);

        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("message", "Paiement enregistré avec succès. Référence: " + reference);
        resp.put("reference", reference);
        sendJson(exchange, resp);

    } catch (SQLException e) {
        LOGGER.log(Level.SEVERE, "Erreur enregistrement paiement", e);
        sendJsonError(exchange, "Erreur lors de l'enregistrement : " + e.getMessage());
    } catch (Exception e) {
        LOGGER.log(Level.SEVERE, "Erreur lors du paiement", e);
        sendJsonError(exchange, "Erreur lors du paiement : " + e.getMessage());
    }
}

    // ============================================================
    // PAIEMENT MONCASH
    // ============================================================
    private void handleMonCashPayment(HttpExchange exchange, String numeroEtudiant,
                                       String institutionId, double montant, String moyen)
            throws IOException, Exception {

        final String clientId = getMonCashClientId();   // ✅ validation stricte

        LOGGER.info(() -> "💳 MonCash : clientId longueur=" + clientId.length()
                + " | montant=" + montant);

        String orderId = "ORDER-" + System.currentTimeMillis() + "-" + numeroEtudiant;

        String sessionId = getSessionId(exchange);
        if (sessionId != null) {
            HttpSession session = SessionManager.getSession(sessionId);
            if (session != null) {
                session.setAttribute("pending_payment_orderId", orderId);
                session.setAttribute("pending_payment_montant", montant);
                session.setAttribute("pending_payment_etudiant", numeroEtudiant);
                session.setAttribute("pending_payment_institution", institutionId);
                session.setAttribute("pending_payment_moyen", moyen);
            }
        }

        MonCashService monCash = new MonCashService(clientId, getMonCashClientSecret());
        String redirectUrl = monCash.createPayment(orderId, montant);

        LOGGER.info(() -> "✅ MonCash : redirection");

        exchange.getResponseHeaders().set("Location", redirectUrl);
        exchange.sendResponseHeaders(302, -1);
    }

    // ============================================================
    // HELPERS ÉTUDIANT
    // ============================================================
    private Etudiant getEtudiant(String numeroEtudiant, String institutionId)
            throws SQLException {
        try (java.sql.Connection conn = DatabaseManager.getInstance().getConnection()) {
            EtudiantData dao = new EtudiantData(conn);
            Etudiant e = dao.read(numeroEtudiant,institutionId);
            if (e == null || !institutionId.equals(e.getInstitutionId())) {
                return null;
            }
            return e;
        }
    }

    private String getAnneeCouranteFallback() {
        int an = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
        int mois = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH);
        return (mois < 6) ? (an - 1) + "-" + an : an + "-" + (an + 1);
    }

    // ============================================================
    // SESSION
    // ============================================================
    private String getSessionId(HttpExchange exchange) {
        try {
            String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
            if (cookieHeader == null) return null;
            for (String cookie : cookieHeader.split(";")) {
                String[] parts = cookie.trim().split("=", 2);
                if (parts.length == 2 && "SESSION_ID".equals(parts[0])) {
                    return parts[1];
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erreur récupération sessionId", e);
        }
        return null;
    }

    private String getNumeroEtudiantFromSession(HttpExchange exchange) {
        HttpSession session = getSession(exchange);
        if (session == null) return null;
        return (String) session.getAttribute("userId");
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

    // ============================================================
    // PARSING
    // ============================================================
    private Map<String, String> parseFormData(String body) {
        Map<String, String> params = new HashMap<>();
        if (body == null || body.isBlank()) return params;
        for (String pair : body.split("&")) {
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

    // ============================================================
    // JSON
    // ============================================================
    private void sendJson(HttpExchange exchange, Map<String, Object> data) throws IOException {
        if (exchange.getResponseCode() != -1) return;
        String json = new com.google.gson.Gson().toJson(data);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendJsonError(HttpExchange exchange, String message) throws IOException {
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("message", message);
        sendJson(exchange, error);
    }

    // ============================================================
    // DÉTECTION ERREUR RÉSEAU
    // ============================================================
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