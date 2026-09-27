import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class MonCashWebhookHandler implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(MonCashWebhookHandler.class.getName());

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // Ajouter les en-têtes CORS
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        exchange.getResponseHeaders().set("Access-Control-Allow-Credentials", "true");

        // Gérer les requêtes OPTIONS (CORS)
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            exchange.getResponseBody().close();
            return;
        }

        // Vérifier la méthode HTTP
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Method Not Allowed - Seulement POST est accepté");
            return;
        }

        try {
            // Lire le corps de la requête
            String body = readRequestBody(exchange);
            LOGGER.info(() -> "📥 Webhook MonCash reçu: " + body);

            // Traiter le webhook
            boolean success = processWebhook(body);

            if (success) {
                LOGGER.info("✅ Webhook MonCash traité avec succès");
                sendResponse(exchange, 200, "OK");
            } else {
                LOGGER.warning("⚠️ Webhook MonCash traité avec erreur");
                sendResponse(exchange, 400, "Bad Request - Erreur de traitement");
            }

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "❌ Erreur lors du traitement du webhook MonCash", e);
            sendResponse(exchange, 500, "Internal Server Error");
        }
    }

    /**
     * Lit le corps de la requête
     */
    private String readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            byte[] bytes = is.readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    /**
     * Traite le webhook reçu de MonCash
     */
    private boolean processWebhook(String body) {
        if (body == null || body.isBlank()) {
            LOGGER.warning("❌ Corps du webhook vide");
            return false;
        }

        try {
            // Extraire les informations du webhook
            String orderId = extractValue(body, "orderId");
            String transactionId = extractValue(body, "transactionId");
            String status = extractValue(body, "status");
            String amountStr = extractValue(body, "amount");
            String paymentToken = extractValue(body, "paymentToken");

            LOGGER.info(() -> "📋 Webhook - orderId: " + orderId + 
                             ", transactionId: " + transactionId + 
                             ", status: " + status + 
                             ", amount: " + amountStr);

            // Vérifier les informations obligatoires
            if (orderId == null || orderId.isEmpty()) {
                LOGGER.warning("❌ orderId manquant dans le webhook");
                return false;
            }

            // Utiliser amountStr et paymentToken pour éviter les warnings
            double amount = 0.0;
            if (amountStr != null && !amountStr.isEmpty()) {
                try {
                    amount = Double.parseDouble(amountStr);
                } catch (NumberFormatException e) {
                    LOGGER.warning(() -> "⚠️ Montant invalide: " + amountStr);
                }
            }

            if (paymentToken != null && !paymentToken.isEmpty()) {
                LOGGER.fine(() -> "🔑 PaymentToken présent: " + paymentToken.substring(0, Math.min(10, paymentToken.length())) + "...");
            }

            // Traiter selon le statut
            if ("SUCCESS".equalsIgnoreCase(status) || "CAPTURED".equalsIgnoreCase(status)) {
                // Paiement réussi - Enregistrer le paiement
                return handleSuccessfulPayment(orderId, transactionId, amount);
            } else if ("FAILED".equalsIgnoreCase(status) || "CANCELLED".equalsIgnoreCase(status)) {
                // Paiement échoué
                return handleFailedPayment(orderId, status);
            } else {
                // Statut inconnu
                LOGGER.warning(() -> "⚠️ Statut inconnu: " + status);
                return false;
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "❌ Erreur lors du traitement du webhook", e);
            return false;
        }
    }

    /**
     * Extrait une valeur d'une chaîne JSON (simplifié)
     */
    private String extractValue(String json, String key) {
        if (json == null || json.isBlank()) return null;
        
        String searchKey = "\"" + key + "\"";
        int keyIndex = json.indexOf(searchKey);
        if (keyIndex == -1) return null;
        
        int colonIndex = json.indexOf(":", keyIndex + searchKey.length());
        if (colonIndex == -1) return null;
        
        int startQuote = json.indexOf("\"", colonIndex + 1);
        if (startQuote == -1) {
            // Essayer de récupérer une valeur numérique
            int endValue = json.indexOf(",", colonIndex + 1);
            if (endValue == -1) {
                endValue = json.indexOf("}", colonIndex + 1);
            }
            if (endValue != -1) {
                String value = json.substring(colonIndex + 1, endValue).trim();
                if (!value.isEmpty()) {
                    return value;
                }
            }
            return null;
        }
        
        int endQuote = json.indexOf("\"", startQuote + 1);
        if (endQuote == -1) return null;
        
        return json.substring(startQuote + 1, endQuote);
    }

    /**
     * Gère un paiement réussi
     */
    private boolean handleSuccessfulPayment(String orderId, String transactionId, double amount) {
        try {
            LOGGER.info(() -> "✅ Traitement paiement réussi - orderId: " + orderId + ", montant: " + amount);

            // Récupérer les informations de session associées à ce paiement
            String sessionId = getSessionIdForOrder(orderId);
            if (sessionId == null) {
                LOGGER.warning(() -> "⚠️ Aucune session trouvée pour orderId: " + orderId);
                return false;
            }

            // Créer des variables finales pour le lambda
            final String finalEtudiantId;
            final String finalInstitutionId;
            final double finalMontant;

            HttpSession session = SessionManager.getSession(sessionId);
            if (session != null) {
                finalEtudiantId = (String) session.getAttribute("pending_payment_etudiant");
                finalInstitutionId = (String) session.getAttribute("pending_payment_institution");
                Double montantObj = (Double) session.getAttribute("pending_payment_montant");
                finalMontant = montantObj != null ? montantObj : amount;
                
                // Nettoyer les attributs de paiement en session
                session.removeAttribute("pending_payment_etudiant");
                session.removeAttribute("pending_payment_institution");
                session.removeAttribute("pending_payment_montant");
                session.removeAttribute("pending_payment_orderId");
            } else {
                LOGGER.warning(() -> "⚠️ Session introuvable pour ID: " + sessionId);
                return false;
            }

            // Vérifier les informations
            if (finalEtudiantId == null || finalEtudiantId.isEmpty()) {
                LOGGER.warning("⚠️ etudiantId manquant en session");
                return false;
            }
            if (finalInstitutionId == null || finalInstitutionId.isEmpty()) {
                LOGGER.warning("⚠️ institutionId manquant en session");
                return false;
            }

            // Vérifier si le paiement n'a pas déjà été enregistré
            if (isPaymentAlreadyProcessed(orderId)) {
                LOGGER.info(() -> "ℹ️ Paiement déjà traité - orderId: " + orderId);
                return true;
            }

            // Utiliser les variables finales dans les logs
            LOGGER.info(() -> "💳 Enregistrement paiement - Étudiant: " + finalEtudiantId + 
                             ", Institution: " + finalInstitutionId + 
                             ", Montant: " + finalMontant);

            // Enregistrer le paiement
            GestionFinanciereService service = new GestionFinanciereService(finalInstitutionId);
            String reference = "MONCASH-WEBHOOK-" + (transactionId != null ? transactionId : orderId);
            service.enregistrerPaiementEtudiant(finalEtudiantId, finalMontant, "MonCash", reference);

            LOGGER.info(() -> "✅ Paiement enregistré via webhook - Référence: " + reference);

            return true;

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "❌ Erreur enregistrement paiement", e);
            return false;
        }
    }

    /**
     * Gère un paiement échoué
     */
    private boolean handleFailedPayment(String orderId, String status) {
        LOGGER.info(() -> "❌ Paiement échoué - orderId: " + orderId + ", status: " + status);
        
        try {
            // Nettoyer les informations de session si nécessaire
            String sessionId = getSessionIdForOrder(orderId);
            if (sessionId != null) {
                HttpSession session = SessionManager.getSession(sessionId);
                if (session != null) {
                    session.removeAttribute("pending_payment_etudiant");
                    session.removeAttribute("pending_payment_institution");
                    session.removeAttribute("pending_payment_montant");
                    session.removeAttribute("pending_payment_orderId");
                    LOGGER.info(() -> "🧹 Session nettoyée après échec - sessionId: " + sessionId);
                }
            }
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "⚠️ Erreur lors du nettoyage après échec", e);
            return false;
        }
    }

    /**
     * Récupère l'ID de session associé à une commande
     */
    private String getSessionIdForOrder(String orderId) {
        // À implémenter selon votre logique de stockage
        // Par exemple, vous pouvez stocker la correspondance orderId -> sessionId en base de données
        // Cette méthode est appelée mais retourne null pour l'instant
        LOGGER.fine(() -> "🔍 Recherche session pour orderId: " + orderId);
        return null;
    }

    /**
     * Vérifie si un paiement a déjà été traité
     */
    private boolean isPaymentAlreadyProcessed(String orderId) {
        // À implémenter selon votre logique
        // Vérifier en base de données si la référence existe déjà
        LOGGER.fine(() -> "🔍 Vérification paiement déjà traité: " + orderId);
        return false;
    }

    /**
     * Envoie une réponse HTTP
     */
    private void sendResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (var os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}