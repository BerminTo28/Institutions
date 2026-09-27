import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class MonCashReturnHandler implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(MonCashReturnHandler.class.getName());

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // Ajouter les en-têtes CORS
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Cookie");
        exchange.getResponseHeaders().set("Access-Control-Allow-Credentials", "true");

        // Gérer les requêtes OPTIONS (CORS)
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            exchange.getResponseBody().close();
            return;
        }

        try {
            // Récupérer les paramètres de la requête
            final String query = exchange.getRequestURI().getQuery();
            final Map<String, String> params = parseQuery(query);

            final String orderId = params.get("orderId");
            final String status = params.get("status");
            final String transactionId = params.get("transactionId");

            LOGGER.info(() -> "📥 Retour MonCash - orderId: " + orderId + ", status: " + status + ", transactionId: " + transactionId);

            // Vérifier si l'orderId est présent
            if (orderId == null || orderId.isEmpty()) {
                LOGGER.warning("❌ OrderId manquant dans le retour MonCash");
                sendErrorPage(exchange, "OrderId manquant", "La référence de paiement est introuvable.");
                return;
            }

            // Récupérer la session de l'utilisateur
            final String sessionId = getSessionIdFromExchange(exchange);
            String tempEtudiantId = null;
            String tempInstitutionId = null;
            double tempMontant = 0.0;

            if (sessionId != null) {
                HttpSession session = SessionManager.getSession(sessionId);
                if (session != null) {
                    tempEtudiantId = (String) session.getAttribute("pending_payment_etudiant");
                    tempInstitutionId = (String) session.getAttribute("pending_payment_institution");
                    Double montantObj = (Double) session.getAttribute("pending_payment_montant");
                    if (montantObj != null) {
                        tempMontant = montantObj;
                    }
                }
            }

            final String etudiantId = tempEtudiantId;
            final String institutionId = tempInstitutionId;
            final double montant = tempMontant;

            // Vérifier si on a les informations nécessaires
            if (etudiantId == null || institutionId == null) {
                LOGGER.warning("❌ Informations de paiement manquantes en session");
                sendErrorPage(exchange, "Session expirée", "Votre session a expiré. Veuillez réessayer le paiement.");
                return;
            }

            // Vérifier le statut du paiement
            boolean tempSuccess ;

            if ("SUCCESS".equalsIgnoreCase(status) || "CAPTURED".equalsIgnoreCase(status)) {
                tempSuccess = true;
            } else {
                try {
                    String clientId = System.getenv("MONCASH_CLIENT_ID");
                    String clientSecret = System.getenv("MONCASH_CLIENT_SECRET");
                    
                    if (clientId == null || clientId.isEmpty()) {
                        clientId = "votre_client_id";
                    }
                    if (clientSecret == null || clientSecret.isEmpty()) {
                        clientSecret = "votre_client_secret";
                    }

                    MonCashService monCash = new MonCashService(clientId, clientSecret);
                    tempSuccess = monCash.verifyPayment(orderId);
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "❌ Erreur vérification MonCash", e);
                    tempSuccess = false;
                }
            }

            final boolean success = tempSuccess;

            // Traiter le résultat
            if (success) {
                try {
                    GestionFinanciereService service = new GestionFinanciereService(institutionId);
                    final String reference = "MONCASH-" + (transactionId != null && !transactionId.isEmpty() ? transactionId : orderId);
                    service.enregistrerPaiementEtudiant(etudiantId, montant, "MonCash", reference);
                    
                    LOGGER.info(() -> "✅ Paiement enregistré - Référence: " + reference + ", Étudiant: " + etudiantId);
                    
                    final String redirectUrl = "/etudiant/paiements?success=1&reference=" + reference;
                    sendRedirect(exchange, redirectUrl);
                    
                } catch (IOException | SQLException e) {
                    LOGGER.log(Level.SEVERE, "❌ Erreur enregistrement paiement", e);
                    sendErrorPage(exchange, "Erreur d'enregistrement", "Le paiement a été accepté mais l'enregistrement a échoué. Contactez le support.");
                }
            } else {
                LOGGER.warning(() -> "❌ Paiement échoué pour orderId: " + orderId);
                final String redirectUrl = "/etudiant/paiements?error=paiement_echoue&orderId=" + orderId;
                sendRedirect(exchange, redirectUrl);
            }

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "❌ Erreur dans MonCashReturnHandler", e);
            sendErrorPage(exchange, "Erreur technique", "Une erreur inattendue s'est produite: " + e.getMessage());
        }
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isBlank()) {
            return params;
        }
        
        for (String pair : query.split("&")) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                try {
                    String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                    String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                    params.put(key, value);
                } catch (Exception ignored) {
                }
            }
        }
        return params;
    }

    private String getSessionIdFromExchange(HttpExchange exchange) {
        try {
            String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
            if (cookieHeader == null) {
                return null;
            }

            for (String cookie : cookieHeader.split(";")) {
                String[] parts = cookie.trim().split("=", 2);
                if (parts.length == 2) {
                    String name = parts[0].trim();
                    if ("SESSION_ID".equalsIgnoreCase(name) || "JSESSIONID".equalsIgnoreCase(name)) {
                        return parts[1].trim();
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warning(() -> "❌ Erreur extraction session ID: " + e.getMessage());
        }
        return null;
    }

    private void sendRedirect(HttpExchange exchange, String location) throws IOException {
        LOGGER.info(() -> "🔄 Redirection vers: " + location);
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.getResponseBody().close();
    }

    private void sendErrorPage(HttpExchange exchange, String title, String message) throws IOException {
        String html = """
            <!DOCTYPE html>
            <html lang="fr">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Erreur - Paiement MonCash</title>
                <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700&display=swap" rel="stylesheet">
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body {
                        font-family: 'Plus Jakarta Sans', sans-serif;
                        background: #f4f6f9;
                        min-height: 100vh;
                        display: flex;
                        justify-content: center;
                        align-items: center;
                        padding: 20px;
                    }
                    .error-container {
                        max-width: 500px;
                        background: white;
                        border-radius: 20px;
                        padding: 40px;
                        text-align: center;
                        box-shadow: 0 8px 30px rgba(0,0,0,0.08);
                    }
                    .error-icon { font-size: 64px; margin-bottom: 16px; }
                    .error-title { font-size: 24px; font-weight: 700; color: #0f172a; margin-bottom: 8px; }
                    .error-message { font-size: 16px; color: #64748b; margin-bottom: 24px; line-height: 1.5; }
                    .btn {
                        display: inline-block;
                        padding: 12px 24px;
                        background: #1e40af;
                        color: white;
                        text-decoration: none;
                        border-radius: 12px;
                        font-weight: 600;
                        transition: all 0.2s;
                    }
                    .btn:hover { background: #1e3a8a; transform: translateY(-2px); }
                    .btn-secondary { background: #e2e8f0; color: #0f172a; margin-left: 8px; }
                    .btn-secondary:hover { background: #cbd5e1; }
                </style>
            </head>
            <body>
                <div class="error-container">
                    <div class="error-icon">⚠️</div>
                    <div class="error-title">%s</div>
                    <div class="error-message">%s</div>
                    <div>
                        <a href="/etudiant/paiements" class="btn">Retour aux paiements</a>
                        <a href="/" class="btn btn-secondary">Accueil</a>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(title, message);

        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(400, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}