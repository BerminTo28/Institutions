import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.HttpExchange;

public class ResponseUtil {

    public static void sendHtml(HttpExchange exchange, int statusCode, String html) throws IOException {
        sendResponse(exchange, statusCode, "text/html", html.getBytes(StandardCharsets.UTF_8));
    }

    public static void sendJson(HttpExchange exchange, int statusCode, String json) throws IOException {
        sendResponse(exchange, statusCode, "application/json", json.getBytes(StandardCharsets.UTF_8));
    }

    public static void sendResponse(HttpExchange exchange, int statusCode, String contentType, byte[] data) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
        }
    }

    /**
     * Envoie une redirection HTTP (code 302).
     */
    public static void sendRedirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.getResponseBody().close();
    }

    /**
     * Envoie une réponse d'erreur avec un message HTML simple.
     */
    public static void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        String html = "<html><body><h1>Erreur " + statusCode + "</h1><p>" + message + "</p></body></html>";
        sendHtml(exchange, statusCode, html);
    }

    /**
     * Ajoute les en-têtes CORS à la réponse.
     */
    public static void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, Cookie");
        exchange.getResponseHeaders().set("Access-Control-Allow-Credentials", "true");
    }

    /**
     * Gère les requêtes OPTIONS (pré-vol CORS).
     * AJOUTÉE
     */
    public static void sendOptions(HttpExchange exchange) throws IOException {
        addCorsHeaders(exchange);
        exchange.sendResponseHeaders(204, -1); // No Content
        exchange.getResponseBody().close();
    }

    /**
     * Vérifie si la requête est de type OPTIONS (pré-vol CORS).
     * AJOUTÉE
     */
    public static boolean isOptionsRequest(HttpExchange exchange) {
        return "OPTIONS".equalsIgnoreCase(exchange.getRequestMethod());
    }

    /**
     * Vérifie si la requête est de type GET.
     * AJOUTÉE
     */
    public static boolean isGetRequest(HttpExchange exchange) {
        return "GET".equalsIgnoreCase(exchange.getRequestMethod());
    }

    /**
     * Vérifie si la requête est de type POST.
     * AJOUTÉE
     */
    public static boolean isPostRequest(HttpExchange exchange) {
        return "POST".equalsIgnoreCase(exchange.getRequestMethod());
    }

    /**
     * Vérifie si la requête est de type PUT.
     * AJOUTÉE
     */
    public static boolean isPutRequest(HttpExchange exchange) {
        return "PUT".equalsIgnoreCase(exchange.getRequestMethod());
    }

    /**
     * Vérifie si la requête est de type DELETE.
     * AJOUTÉE
     */
    public static boolean isDeleteRequest(HttpExchange exchange) {
        return "DELETE".equalsIgnoreCase(exchange.getRequestMethod());
    }

    /**
     * Envoie une réponse JSON avec les en-têtes CORS.
     * AJOUTÉE
     */
    public static void sendJsonWithCors(HttpExchange exchange, int statusCode, String json) throws IOException {
        addCorsHeaders(exchange);
        sendJson(exchange, statusCode, json);
    }

    /**
     * Envoie une réponse HTML avec les en-têtes CORS.
     * AJOUTÉE
     */
    public static void sendHtmlWithCors(HttpExchange exchange, int statusCode, String html) throws IOException {
        addCorsHeaders(exchange);
        sendHtml(exchange, statusCode, html);
    }

    /**
     * Envoie une réponse 404 Not Found avec message personnalisé.
     * AJOUTÉE
     */
    public static void sendNotFound(HttpExchange exchange, String message) throws IOException {
        sendError(exchange, 404, message != null ? message : "Ressource non trouvée");
    }

    /**
     * Envoie une réponse 400 Bad Request avec message personnalisé.
     * AJOUTÉE
     */
    public static void sendBadRequest(HttpExchange exchange, String message) throws IOException {
        sendError(exchange, 400, message != null ? message : "Requête invalide");
    }

    /**
     * Envoie une réponse 500 Internal Server Error avec message personnalisé.
     * AJOUTÉE
     */
    public static void sendInternalError(HttpExchange exchange, String message) throws IOException {
        sendError(exchange, 500, message != null ? message : "Erreur interne du serveur");
    }

    /**
     * Envoie une réponse 401 Unauthorized.
     * AJOUTÉE
     */
    public static void sendUnauthorized(HttpExchange exchange) throws IOException {
        sendError(exchange, 401, "Non autorisé");
    }

    /**
     * Envoie une réponse 403 Forbidden.
     * AJOUTÉE
     */
    public static void sendForbidden(HttpExchange exchange) throws IOException {
        sendError(exchange, 403, "Accès interdit");
    }
}