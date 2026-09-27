import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;


public class MonCashService {
    // URLs MonCash (Sandbox)
    private static final String BASE_URL = "https://sandbox.moncashbutton.digicelgroup.com";
    private static final String OAUTH_URL = BASE_URL + "/Api/oauth/token";
    private static final String CREATE_PAYMENT_URL = BASE_URL + "/Api/v1/CreatePayment";
    private static final String REDIRECT_BASE = BASE_URL + "/Moncash-middleware/Payment/Redirect";

    private final String clientId;
    private final String clientSecret;
    private String accessToken;
    private long tokenExpiry;

    public MonCashService(String clientId, String clientSecret) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    /**
     * Récupère un token d'accès OAuth2 (avec mise en cache)
     */
    private String getAccessToken() throws Exception {
        if (accessToken != null && System.currentTimeMillis() < tokenExpiry) {
            return accessToken;
        }

        String credentials = clientId + ":" + clientSecret;
        String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OAUTH_URL))
                .header("Authorization", "Basic " + encodedCredentials)
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("scope=read,write&grant_type=client_credentials"))
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new Exception("Erreur d'authentification MonCash: " + response.body());
        }

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        accessToken = json.get("access_token").getAsString();
        int expiresIn = json.get("expires_in").getAsInt();
        tokenExpiry = System.currentTimeMillis() + (expiresIn * 1000L);

        return accessToken;
    }

    /**
     * Crée une transaction MonCash et retourne l'URL de redirection
     */
    public String createPayment(String orderId, double amount) throws Exception {
        String token = getAccessToken();

        JsonObject payload = new JsonObject();
        payload.addProperty("amount", amount);
        payload.addProperty("orderId", orderId);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(CREATE_PAYMENT_URL))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new Exception("Erreur création paiement MonCash: " + response.body());
        }

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        String paymentToken = json.get("payment_token").getAsString();

        return REDIRECT_BASE + "?token=" + paymentToken;
    }

    /**
     * Vérifie le statut d'un paiement à partir de son orderId (pour le retour)
     */
    public boolean verifyPayment(String orderId) throws Exception {
        String token = getAccessToken();

        JsonObject payload = new JsonObject();
        payload.addProperty("orderId", orderId);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/Api/v1/CapturePaymentByOrderId"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            return false;
        }

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        String status = json.get("status").getAsString();
        return "SUCCESS".equalsIgnoreCase(status) || "CAPTURED".equalsIgnoreCase(status);
    }
}