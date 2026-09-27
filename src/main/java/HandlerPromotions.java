import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class HandlerPromotions implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(HandlerPromotions.class.getName());

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(LocalDate.class, (JsonSerializer<LocalDate>) (src, typeOfSrc, context) ->
                    src != null ? new JsonPrimitive(src.toString()) : null)
            .registerTypeAdapter(LocalDate.class, (JsonDeserializer<LocalDate>) (json, typeOfT, context) ->
                    json != null && !json.getAsString().isBlank() ? LocalDate.parse(json.getAsString()) : null)
            .registerTypeAdapter(LocalDateTime.class, (JsonSerializer<LocalDateTime>) (src, typeOfSrc, context) ->
                    src != null ? new JsonPrimitive(src.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)) : null)
            .registerTypeAdapter(LocalDateTime.class, (JsonDeserializer<LocalDateTime>) (json, typeOfT, context) ->
                    json != null && !json.getAsString().isBlank() ? LocalDateTime.parse(json.getAsString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null)
            .registerTypeAdapter(LocalTime.class, (JsonSerializer<LocalTime>) (src, typeOfSrc, context) ->
                    src != null ? new JsonPrimitive(src.format(DateTimeFormatter.ISO_LOCAL_TIME)) : null)
            .registerTypeAdapter(LocalTime.class, (JsonDeserializer<LocalTime>) (json, typeOfT, context) ->
                    json != null && !json.getAsString().isBlank() ? LocalTime.parse(json.getAsString(), DateTimeFormatter.ISO_LOCAL_TIME) : null)
            .registerTypeAdapter(ZonedDateTime.class, (JsonSerializer<ZonedDateTime>) (src, typeOfSrc, context) ->
                    src != null ? new JsonPrimitive(src.format(DateTimeFormatter.ISO_ZONED_DATE_TIME)) : null)
            .registerTypeAdapter(ZonedDateTime.class, (JsonDeserializer<ZonedDateTime>) (json, typeOfT, context) ->
                    json != null && !json.getAsString().isBlank() ? ZonedDateTime.parse(json.getAsString(), DateTimeFormatter.ISO_ZONED_DATE_TIME) : null)
            .serializeNulls()
            .disableHtmlEscaping()
            .create();

    private final PromotionManager promotionManager = new PromotionManager();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // CORS
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        final String path = exchange.getRequestURI().getPath();
        final String method = exchange.getRequestMethod();

        LOGGER.info(() -> "📥 HandlerPromotions - " + method + " " + path);

        final String sessionId = getCookieValue(exchange, "SESSION_ID");
        final HttpSession session = (sessionId != null) ? SessionManager.getSession(sessionId) : null;

        if (session == null) {
            LOGGER.warning("⚠️ Session non trouvée");
            redirigerVers(exchange, "/admin/parametres");
            return;
        }

        String institutionId = (String) session.getAttribute("institutionId");
        if (institutionId == null || institutionId.isBlank()) {
            institutionId = (String) session.getAttribute("institution_id");
        }

        if (institutionId == null || institutionId.isBlank()) {
            LOGGER.warning("⚠️ institutionId non trouvé en session");
            redirigerVers(exchange, "/admin/parametres");
            return;
        }

        final String institutionIdFinal = institutionId;
        final String role = (String) session.getAttribute("role");
        final boolean isAdmin = "ADMINISTRATEUR".equalsIgnoreCase(role)
                || "ADMIN".equalsIgnoreCase(role)
                || role == null;

        try {
            // --- PAGE HTML ---
            if (path.equals("/admin/parametres/promotions") && "GET".equals(method)) {
                handlePageHtml(exchange, institutionIdFinal, isAdmin);
                return;
            }

            // ============================================================
            // ✅ API : RÉCUPÉRER LES ANNÉES DISPONIBLES (AJAX)
            // ============================================================
            if (path.equals("/admin/parametres/promotions/api/annees") && "GET".equals(method)) {
                handleGetAnnees(exchange, institutionIdFinal);
                return;
            }

            // --- API REST ---
            if (path.equals("/admin/parametres/promotions/api/promotions") && "GET".equals(method)) {
                handleGetAll(exchange, institutionIdFinal);
                return;
            }

            if (path.equals("/admin/parametres/promotions/api/promotions") && "POST".equals(method)) {
                handleCreate(exchange, institutionIdFinal);
                return;
            }

            // Routes avec clé composite : /api/promotions/{annee}/{promotion}
            // et activation : /api/promotions/{annee}/{promotion}/activate
            if (path.startsWith("/admin/parametres/promotions/api/promotions/")) {
                String reste = path.substring("/admin/parametres/promotions/api/promotions/".length());

                boolean activate = reste.endsWith("/activate");
                if (activate) {
                    reste = reste.substring(0, reste.length() - "/activate".length());
                }

                String[] parts = reste.split("/", 2);
                if (parts.length < 2) {
                    sendJsonError(exchange, 400,
                            "Clé composite manquante : annee + promotion requis");
                    return;
                }

                String annee = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
                String promotion = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);

                if (activate) {
                    if ("PUT".equals(method)) {
                        handleActivate(exchange, institutionIdFinal, annee, promotion);
                        return;
                    }
                } else {
                    if ("GET".equals(method)) {
                        handleGetByCle(exchange, institutionIdFinal, annee, promotion);
                        return;
                    }
                    if ("PUT".equals(method)) {
                        handleUpdate(exchange, institutionIdFinal, annee, promotion);
                        return;
                    }
                    if ("DELETE".equals(method)) {
                        handleDelete(exchange, institutionIdFinal, annee, promotion);
                        return;
                    }
                }
            }

            sendJsonError(exchange, 404, "Endpoint non trouvé: " + path);

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur SQL", e);
            sendJsonError(exchange, 500, "Erreur base de données: " + e.getMessage());
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Erreur", e);
            sendJsonError(exchange, 500, "Erreur interne: " + e.getMessage());
        }
    }

    // ============================================================
    // ✅ NOUVEAU : API – GET ANNEES DISPONIBLES
    // ============================================================
    /**
     * Renvoie la liste des années académiques disponibles pour l'institution.
     * Utilisé par le JS pour remplir dynamiquement le <select> du modal.
     */
    private void handleGetAnnees(HttpExchange exchange, String institutionId) throws IOException {
        LOGGER.info(() -> "📅 API récupération des années pour: " + institutionId);

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            final AnneeAcademiqueData anneeData = new AnneeAcademiqueData(conn);
            final List<AnneeAcademique> annees = anneeData.listByInstitution(institutionId);

            // ✅ Construire une liste simplifiée {anneeAcademique: "..."}
            final List<Map<String, Object>> anneesSimples = annees.stream()
                    .map(a -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("anneeAcademique", a.getAnneeAcademique());
                        return m;
                    })
                    .toList();

            final Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", anneesSimples);
            result.put("count", anneesSimples.size());

            LOGGER.info(() -> "✅ " + anneesSimples.size() + " années retournées");
            sendJson(exchange, 200, result);

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur récupération années", e);
            sendJsonError(exchange, 500, "Erreur base de données: " + e.getMessage());
        }
    }

    // ============================================================
    // PAGE HTML
    // ============================================================
    private void handlePageHtml(HttpExchange exchange, String institutionId, boolean isAdmin)
            throws IOException, SQLException {
        LOGGER.info("📄 Génération de la page HTML des promotions");

        List<AnneeAcademique> anneesDisponiblesTemp;
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            final AnneeAcademiqueData anneeData = new AnneeAcademiqueData(conn);
            anneesDisponiblesTemp = anneeData.listByInstitution(institutionId);
            final int taille = anneesDisponiblesTemp.size();
            LOGGER.info(() -> "📅 Années académiques trouvées: " + taille);
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erreur récupération années académiques", e);
            anneesDisponiblesTemp = List.of();
        }

        final List<AnneeAcademique> anneesDisponibles = anneesDisponiblesTemp;

        final String htmlOutput = UIPromotions.rendrePage(
                anneesDisponibles,
                institutionId,
                null,
                null,
                isAdmin,
                isAdmin,
                isAdmin
        );

        sendResponse(exchange, 200, htmlOutput);
    }

    // ============================================================
    // API – GET ALL avec filtres
    // ============================================================
    private void handleGetAll(HttpExchange exchange, String institutionId) throws IOException {
        LOGGER.info(() -> "📋 Récupération de toutes les promotions pour: " + institutionId);

        final String query = exchange.getRequestURI().getQuery();
        final Map<String, String> params = parseQuery(query);

        final String motCle = params.get("q");
        final String anneeFiltre = params.get("annee");

        final List<Promotion> promotionsTemporaires;
        if (anneeFiltre != null && !anneeFiltre.isBlank()) {
            promotionsTemporaires = promotionManager.obtenirPromotionsParAnnee(institutionId, anneeFiltre);
        } else {
            promotionsTemporaires = promotionManager.obtenirToutesLesPromotions(institutionId);
        }

        final List<Promotion> promotions;
        if (motCle != null && !motCle.isBlank()) {
            final String q = motCle.toLowerCase();
            promotions = promotionsTemporaires.stream()
                    .filter(p -> (p.getPromotion() != null && p.getPromotion().toLowerCase().contains(q))
                            || (p.getAnneeAcademique() != null && p.getAnneeAcademique().toLowerCase().contains(q)))
                    .toList();
        } else {
            promotions = promotionsTemporaires;
        }

        final Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", promotions);
        result.put("count", promotions.size());

        sendJson(exchange, 200, result);
    }

    // ============================================================
    // API – GET BY CLÉ COMPOSITE
    // ============================================================
    private void handleGetByCle(HttpExchange exchange, String institutionId,
                                  String annee, String promotion) throws IOException {
        LOGGER.info(() -> "🔍 Récupération promotion: " + annee + "/" + promotion);

        final Optional<Promotion> optPromotion =
                promotionManager.obtenirParCle(institutionId, annee, promotion);
        if (optPromotion.isEmpty()) {
            sendJsonError(exchange, 404, "Promotion non trouvée");
            return;
        }

        final Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", optPromotion.get());
        sendJson(exchange, 200, result);
    }

    // ============================================================
    // API – CREATE
    // ============================================================
    private void handleCreate(HttpExchange exchange, String institutionId) throws IOException {
        final String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        LOGGER.info(() -> "📝 Création promotion - body: " + body);

        final Promotion promotion;
        try {
            promotion = GSON.fromJson(body, Promotion.class);
        } catch (JsonSyntaxException e) {
            LOGGER.warning(() -> "❌ JSON invalide: " + e.getMessage());
            sendJsonError(exchange, 400, "JSON invalide");
            return;
        }

        if (promotion.getPromotion() == null || promotion.getPromotion().isBlank()
                || promotion.getAnneeAcademique() == null || promotion.getAnneeAcademique().isBlank()
                || promotion.getDateDebut() == null || promotion.getDateFin() == null) {
            sendJsonError(exchange, 400, "Champs promotion, anneeAcademique, dateDebut, dateFin requis");
            return;
        }

        // Vérifier que l'année existe
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            final AnneeAcademiqueData anneeData = new AnneeAcademiqueData(conn);
            final List<AnneeAcademique> anneesDisponibles = anneeData.listByInstitution(institutionId);
            final boolean anneeValide = anneesDisponibles.stream()
                    .anyMatch(a -> a.getAnneeAcademique().equals(promotion.getAnneeAcademique()));
            if (!anneeValide) {
                sendJsonError(exchange, 400, "L'année académique sélectionnée n'est pas valide.");
                return;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erreur validation année", e);
            sendJsonError(exchange, 500, "Erreur de validation de l'année académique");
            return;
        }

        promotion.setInstitutionId(institutionId);
        if (promotion.getEstActive() == null) {
            promotion.setEstActive(false);
        }

        try {
            final boolean succes = promotionManager.enregistrerPromotion(promotion);
            if (succes) {
                if (Boolean.TRUE.equals(promotion.getEstActive())) {
                    promotionManager.activerPromotion(
                            institutionId,
                            promotion.getAnneeAcademique(),
                            promotion.getPromotion());
                }
                final Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("message", "Promotion créée avec succès");
                result.put("data", promotion);
                sendJson(exchange, 201, result);
            } else {
                sendJsonError(exchange, 500, "Échec de l'enregistrement de la promotion.");
            }
        } catch (IllegalArgumentException e) {
            LOGGER.warning(() -> "❌ Erreur validation: " + e.getMessage());
            sendJsonError(exchange, 400, e.getMessage());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur SQL", e);
            sendJsonError(exchange, 500, "Erreur base de données: " + e.getMessage());
        }
    }

    // ============================================================
    // API – UPDATE (par clé composite)
    // ============================================================
    private void handleUpdate(HttpExchange exchange, String institutionId,
                                String ancienneAnnee, String anciennePromotion) throws IOException {
        final String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        LOGGER.info(() -> "📝 Mise à jour promotion " + ancienneAnnee + "/"
                + anciennePromotion + " - body: " + body);

        final Promotion nouvelle;
        try {
            nouvelle = GSON.fromJson(body, Promotion.class);
        } catch (JsonSyntaxException e) {
            LOGGER.warning(() -> "❌ JSON invalide: " + e.getMessage());
            sendJsonError(exchange, 400, "JSON invalide");
            return;
        }

        final Optional<Promotion> optExisting =
                promotionManager.obtenirParCle(institutionId, ancienneAnnee, anciennePromotion);
        if (optExisting.isEmpty()) {
            sendJsonError(exchange, 404, "Promotion non trouvée");
            return;
        }

        final Promotion existing = optExisting.get();

        String nouvelleAnnee = (nouvelle.getAnneeAcademique() != null
                && !nouvelle.getAnneeAcademique().isBlank())
                ? nouvelle.getAnneeAcademique() : existing.getAnneeAcademique();
        String nouvellePromotion = (nouvelle.getPromotion() != null
                && !nouvelle.getPromotion().isBlank())
                ? nouvelle.getPromotion() : existing.getPromotion();

        boolean cleChange = !nouvelleAnnee.equals(ancienneAnnee)
                || !nouvellePromotion.equals(anciennePromotion);

        existing.setDateDebut(nouvelle.getDateDebut() != null
                ? nouvelle.getDateDebut() : existing.getDateDebut());
        existing.setDateFin(nouvelle.getDateFin() != null
                ? nouvelle.getDateFin() : existing.getDateFin());
        if (nouvelle.getEstActive() != null) {
            existing.setEstActive(nouvelle.getEstActive());
        }

        try {
            if (cleChange) {
                Promotion nouvelleCle = new Promotion();
                nouvelleCle.setInstitutionId(institutionId);
                nouvelleCle.setAnneeAcademique(nouvelleAnnee);
                nouvelleCle.setPromotion(nouvellePromotion);
                nouvelleCle.setDateDebut(existing.getDateDebut());
                nouvelleCle.setDateFin(existing.getDateFin());
                nouvelleCle.setEstActive(existing.getEstActive());

                promotionManager.mettreAJourAvecChangementDeCle(
                        institutionId, ancienneAnnee, anciennePromotion, nouvelleCle);
            } else {
                promotionManager.enregistrerPromotion(existing);
            }

            if (Boolean.TRUE.equals(existing.getEstActive())) {
                promotionManager.activerPromotion(institutionId, nouvelleAnnee, nouvellePromotion);
            }

            final Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Promotion mise à jour avec succès");
            result.put("data", existing);
            sendJson(exchange, 200, result);

        } catch (IllegalArgumentException e) {
            LOGGER.warning(() -> "❌ Erreur validation: " + e.getMessage());
            sendJsonError(exchange, 400, e.getMessage());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur SQL", e);
            sendJsonError(exchange, 500, "Erreur base de données: " + e.getMessage());
        }
    }

    // ============================================================
    // API – DELETE (par clé composite)
    // ============================================================
    private void handleDelete(HttpExchange exchange, String institutionId,
                                String annee, String promotion) throws IOException {
        LOGGER.info(() -> "🗑️ Suppression promotion: " + annee + "/" + promotion);

        final Optional<Promotion> optExisting =
                promotionManager.obtenirParCle(institutionId, annee, promotion);
        if (optExisting.isEmpty()) {
            sendJsonError(exchange, 404, "Promotion non trouvée");
            return;
        }

        try {
            final boolean succes = promotionManager.supprimerPromotion(institutionId, annee, promotion);
            if (succes) {
                final Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("message", "Promotion supprimée avec succès");
                sendJson(exchange, 200, result);
            } else {
                sendJsonError(exchange, 500, "Impossible de supprimer la promotion");
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur SQL", e);
            sendJsonError(exchange, 500, "Erreur base de données: " + e.getMessage());
        }
    }

    // ============================================================
    // API – ACTIVATE (par clé composite)
    // ============================================================
    private void handleActivate(HttpExchange exchange, String institutionId,
                                  String annee, String promotion) throws IOException {
        LOGGER.info(() -> "✅ Activation promotion: " + annee + "/" + promotion);

        final Optional<Promotion> optExisting =
                promotionManager.obtenirParCle(institutionId, annee, promotion);
        if (optExisting.isEmpty()) {
            sendJsonError(exchange, 404, "Promotion non trouvée");
            return;
        }

        final boolean succes = promotionManager.activerPromotion(institutionId, annee, promotion);
        if (succes) {
            final Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Promotion activée avec succès");
            sendJson(exchange, 200, result);
        } else {
            sendJsonError(exchange, 500, "Impossible d'activer la promotion");
        }
    }

    // ============================================================
    // UTILITAIRES
    // ============================================================

    private void redirigerVers(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(302, -1);
    }

    private Map<String, String> parseQuery(String query) {
        final Map<String, String> params = new HashMap<>();
        if (query == null || query.isBlank()) return params;
        for (String pair : query.split("&")) {
            final int idx = pair.indexOf("=");
            if (idx > 0) {
                try {
                    final String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                    final String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                    params.put(key, value);
                } catch (Exception ignored) {}
            }
        }
        return params;
    }

    private String getCookieValue(HttpExchange exchange, String cookieName) {
        final String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookieHeader == null) return null;
        for (String cookie : cookieHeader.split(";")) {
            final String[] parts = cookie.trim().split("=");
            if (parts.length == 2 && cookieName.equals(parts[0])) {
                return parts[1];
            }
        }
        return null;
    }

    private void sendJson(HttpExchange exchange, int statusCode, Map<String, Object> data) throws IOException {
        final String json = GSON.toJson(data);
        final byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
            os.flush();
        }
    }

    private void sendJsonError(HttpExchange exchange, int statusCode, String message) throws IOException {
        final Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", message);
        sendJson(exchange, statusCode, error);
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String html) throws IOException {
        final byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
            os.flush();
        }
    }
}