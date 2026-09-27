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

public class HandlerEtudiantQuiz implements HttpHandler {

    private static final Logger LOGGER =
            Logger.getLogger(HandlerEtudiantQuiz.class.getName());

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        try {
            final String etudiantId = getEtudiantIdFromSession(exchange);
            final String institutionId = getInstitutionIdFromSession(exchange);

            if (etudiantId == null || institutionId == null) {
                exchange.getResponseHeaders().set("Location", "/connexion");
                exchange.sendResponseHeaders(302, -1);
                return;
            }

            if ("GET".equalsIgnoreCase(method)) {
                handleGet(exchange, etudiantId, institutionId);
            } else if ("POST".equalsIgnoreCase(method)) {
                handlePost(exchange, etudiantId, institutionId);
            } else {
                ResponseUtil.sendError(exchange, 405, "Méthode non autorisée");
            }
        } catch (IOException e) {
            if (isNetworkException(e)) {
                LOGGER.fine(() -> "Client déconnecté : " + e.getMessage());
                return;
            }
            LOGGER.log(Level.SEVERE, "Erreur I/O dans HandlerEtudiantQuiz", e);
            safeSendError(exchange, 500, "Erreur interne.");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erreur dans HandlerEtudiantQuiz", e);
            safeSendError(exchange, 500, "Erreur interne.");
        }
    }

    // ============================================================
    // GET — Liste ou passation
    // ============================================================
    private void handleGet(HttpExchange exchange, String etudiantId, String institutionId)
            throws IOException {

        // ✅ `action` retiré : seule `id` est utilisée ici
        Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
        String quizId = params.getOrDefault("id", "");

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            QuizService service = new QuizService(conn, institutionId);

            // --- Passer un quiz ---
            if (!quizId.isBlank()) {
                Quiz quiz = service.getQuizPourEtudiant(quizId, etudiantId);
                if (quiz == null) {
                    String html = UIEtudiantQuiz.renderError(
                            "Ce quiz n'est plus disponible ou a expiré.");
                    ResponseUtil.sendHtml(exchange, 403, html);
                    return;
                }

                QuizTentative tentative = service.getTentative(quizId, etudiantId);
                String html = UIEtudiantQuiz.renderQuiz(quiz, tentative);
                ResponseUtil.sendHtml(exchange, 200, html);
                return;
            }

            // --- Liste des quiz accessibles ---
            List<Quiz> quizList = service.getQuizsPourEtudiant(etudiantId);

            String html = UIEtudiantQuiz.renderListe(etudiantId, quizList, null, null);
            ResponseUtil.sendHtml(exchange, 200, html);

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur SQL (GET)", e);
            safeSendError(exchange, 500, "Erreur base de données.");
        }
    }

    // ============================================================
    // POST — Soumission d'une tentative
    // ============================================================
    private void handlePost(HttpExchange exchange, String etudiantId, String institutionId)
            throws IOException {

        String body = new String(exchange.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8);
        Map<String, List<String>> multiParams = parseMultiFormData(body);

        // ✅ `action` EST utilisée ici pour vérifier qu'il s'agit d'une soumission
        String action = first(multiParams, "action");
        String quizId = first(multiParams, "quizId");

        if (!"soumettre".equals(action) || isBlank(quizId)) {
            safeSendError(exchange, 400, "Requête invalide.");
            return;
        }

        // ⏰ Re-vérification de l'expiration à la soumission
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            QuizService service = new QuizService(conn, institutionId);

            Quiz quiz = service.getQuizPourEtudiant(quizId, etudiantId);
            if (quiz == null) {
                safeSendError(exchange, 403,
                        "Quiz expiré ou non disponible. Soumission refusée.");
                return;
            }

            // Détecter une tentative déjà terminée
            QuizTentative existante = service.getTentative(quizId, etudiantId);
            if (existante != null && "TERMINEE".equalsIgnoreCase(existante.getStatut())) {
                safeSendError(exchange, 409,
                        "Vous avez déjà soumis ce quiz. Une seule tentative est autorisée.");
                return;
            }

            // Construire la map questionId → liste optionIds
            Map<String, List<String>> reponses = new HashMap<>();
            for (QuizSection section : quiz.getSections()) {
                for (QuizQuestion q : section.getQuestions()) {
                    String key = "q_" + q.getId();
                    List<String> choix = multiParams.get(key);
                    if (choix != null && !choix.isEmpty()) {
                        reponses.put(q.getId(), choix);
                    }
                }
            }

            // Enregistrer la tentative + calculer la cote
            double cote = service.soumettreTentative(quizId, etudiantId, reponses);

            LOGGER.info(() -> "✅ Tentative soumise : étudiant=" + etudiantId
                    + " | quiz=" + quizId + " | cote=" + cote
                    + "/" + quiz.getNoteTotale());

            // Recharger pour afficher le résultat
            String html = UIEtudiantQuiz.renderResultat(
                    quiz, cote, service.getTentative(quizId, etudiantId));
            ResponseUtil.sendHtml(exchange, 200, html);

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur soumission tentative", e);
            safeSendError(exchange, 500, "Erreur lors de la soumission.");
        }
    }

    // ============================================================
    // SESSION
    // ============================================================
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
            LOGGER.log(Level.WARNING, "Erreur session", e);
            return null;
        }
    }

    private String extractSessionId(String cookieHeader) {
        for (String cookie : cookieHeader.split(";")) {
            String[] parts = cookie.trim().split("=", 2);
            if (parts.length == 2 && "SESSION_ID".equals(parts[0])) return parts[1];
        }
        return null;
    }

    // ============================================================
    // PARSING
    // ============================================================
    private Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null) return params;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                try {
                    params.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                               URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
                } catch (Exception ignored) {}
            }
        }
        return params;
    }

    private Map<String, List<String>> parseMultiFormData(String body) {
        Map<String, List<String>> params = new HashMap<>();
        if (body == null || body.isBlank()) return params;
        for (String pair : body.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                try {
                    String key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
                    String val = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                    params.computeIfAbsent(key, k -> new ArrayList<>()).add(val);
                } catch (Exception ignored) {}
            }
        }
        return params;
    }

    private static String first(Map<String, List<String>> m, String key) {
        List<String> l = m.get(key);
        return (l == null || l.isEmpty()) ? null : l.get(0);
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }

    // ============================================================
    // SEND HELPERS
    // ============================================================
    private void safeSendError(HttpExchange exchange, int status, String message) {
        try {
            if (exchange.getResponseCode() != -1) return;
            String html = UIEtudiantQuiz.renderError(message);
            ResponseUtil.sendHtml(exchange, status, html);
        } catch (IOException e) {
            if (!isNetworkException(e)) {
                LOGGER.log(Level.WARNING, "Impossible d'envoyer l'erreur", e);
            }
        }
    }

    private static boolean isNetworkException(IOException e) {
        if (e == null) return false;
        String msg = e.getMessage();
        if (msg == null) {
            Throwable c = e.getCause();
            if (c != null && c.getMessage() != null) msg = c.getMessage();
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