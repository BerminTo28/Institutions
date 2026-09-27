import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * Configuration et démarrage du serveur HTTP unique.
 *
 * OPTIMISATIONS :
 *  - Executor avec pool de threads (30 par défaut).
 *  - Handlers dédiés pour favicon, /api/health et /data/photos/* :
 *      → aucun accès DB, aucun contrôle de session, cache mémoire.
 *  - Ordre d'enregistrement des contexts : du plus spécifique au plus général,
 *    pour éviter que "/" ne capture tout.
 */
public class HomeHandlers {

    private static final Logger LOGGER = Logger.getLogger(HomeHandlers.class.getName());
    private static final int HTTP_THREAD_POOL_SIZE = 30;

    // ==================== SERVEUR ====================

    public static void demarrerServeurUnique(int portPrincipal, String hostPrincipal) {
        try {
            HttpServer server = HttpServer.create(
                    new InetSocketAddress("0.0.0.0", portPrincipal), 0);

            registerHandlers(server, hostPrincipal);

            // ✅ Pool de threads nommés + daemon
            server.setExecutor(Executors.newFixedThreadPool(HTTP_THREAD_POOL_SIZE, r -> {
                Thread t = new Thread(r, "http-worker-" + System.nanoTime());
                t.setDaemon(true);
                return t;
            }));

            server.start();
            System.out.println("✅ Serveur démarré sur le port " + portPrincipal
                    + " (" + HTTP_THREAD_POOL_SIZE + " threads)");
        } catch (IOException e) {
            System.err.println("[ERREUR] Serveur: " + e.getMessage());
            LOGGER.log(Level.SEVERE, "Impossible de démarrer le serveur", e);
        }
    }

    // ==================== ENREGISTREMENT DES HANDLERS ====================

    private static void registerHandlers(HttpServer server, String hostPrincipal) {

        // ============================================================
        // 1) HANDLERS STATIQUES ET PUBLICS (les plus spécifiques d'abord)
        // ============================================================

        // Favicon dédié : cache mémoire, aucun accès DB, aucune session
        server.createContext("/favicon.ico", new HandlerFavicon());

        // Santé du serveur : public, aucun accès DB
        server.createContext("/api/health", new HandlerHealth());

        // Ping : public, ultra-rapide
        server.createContext("/api/ping", exchange -> {
            byte[] bytes = "pong".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        // Logo M-TECH (asset interne, cache long)
        registerLogoHandler(server);

        // Photos : cache mémoire, aucune session, aucun accès DB
        server.createContext("/data/photos/", new HandlerPhotos());

        // ============================================================
        // 2) API AUTHENTIFICATION (avant /api générique)
        // ============================================================
        server.createContext("/api/connexion", new LoginHandler());
        server.createContext("/api/db/status", new DbStatusHandler());
        server.createContext("/api/inscription/admin", new HandlerInscriptionInstitutions());

        // ============================================================
        // 3) MODULES ADMIN
        // ============================================================
        server.createContext("/admin/classes", new HandlerClasse());
        server.createContext("/admin/matieres", new HandlerMatiere());
        server.createContext("/admin/professeurs", new HandlerProfesseur());
        server.createContext("/admin/etudiants", new HandlerEtudiant());
        server.createContext("/admin/absences", new HandlerAbsence());
        server.createContext("/admin/attestations", new HandlerAttestations());
        server.createContext("/admin/evenements", new HandlerEvenement());
        server.createContext("/admin/bulletins", new HandlerBulletins());
        server.createContext("/admin/palmares", new HandlerPalmares());
        server.createContext("/admin/emploidutemps", new HandlerEmploiduTemps());
        server.createContext("/admin/communication", new HandlerCommunication());
        server.createContext("/admin/communication/", new HandlerCommunication());
        server.createContext("/admin/examens", new HandlerExamenAdmin());
        server.createContext("/admin/validation-notes", new HandlerValidationNotes());
        server.createContext("/admin/economie", new HandlerEconomie());
        server.createContext("/admin/statistiques", new HandlerStatistiques());
        server.createContext("/admin/notes", new HandlerNotes());
        server.createContext("/admin/inscription-institution", new HandlerInscriptionInstitutions());

        // Paramètres
        server.createContext("/admin/parametres/profil", new HandlerParametresProfil());
        server.createContext("/admin/parametres/annees", new HandlerParametresAnnees());
        server.createContext("/admin/parametres/periodes", new HandlerParametresPeriodes());
        server.createContext("/admin/parametres/options", new HandlerOptions());
        server.createContext("/admin/parametres/acces", new UIParametresAccesEtudiant());
        server.createContext("/admin/parametres/double-saisie", new HandlerParametresDoubleSaisie());
        server.createContext("/admin/parametres/sauvegarde", new HandlerSauvegarde());
        server.createContext("/admin/parametres/acces-professeurs", new HandlerParametresAccesProfesseurs());
        server.createContext("/admin/parametres/inscription", new HandlerParametresInscription());
        server.createContext("/admin/parametres/promotions", new HandlerPromotions());
        server.createContext("/admin/parametres", new HandlerParametres());

        // ============================================================
        // 4) DASHBOARDS
        // ============================================================
        server.createContext("/dashboard/admin", new DashboardWebService());
        server.createContext("/dashboard/professeur", new ProfesseurDashboardHandler());
        server.createContext("/dashboard", new DashboardWebService());
        server.createContext("/etudiant/dashboard", new EtudiantDashboard());

        // ============================================================
        // 5) ÉTUDIANT
        // ============================================================
        server.createContext("/etudiant/profil", new HandlerEtudiantProfil());
        server.createContext("/etudiant/notes", new HandlerEtudiantNotes());
        server.createContext("/etudiant/bulletins", new HandlerEtudiantBulletins());
        server.createContext("/etudiant/paiements/retour", new MonCashReturnHandler());
        server.createContext("/etudiant/paiements", new HandlerEtudiantPaiements());
        server.createContext("/etudiant/edt", new HandlerEmploiduTempsEtudiant());
        server.createContext("/etudiant/assistance", new HandlerEtudiantQuiz());
        server.createContext("/etudiant/attestations", new HandlerEtudiantAttestation());
        server.createContext("/etudiant/messages", new HandlerMessagesEtudiant());
        server.createContext("/etudiant/activites", new HandlerEtudiantActivites());
        server.createContext("/etudiant/documents", new HandlerEtudiantDocuments());
        server.createContext("/etudiant/absences", new HandlerAbsenceEtudiant());
        server.createContext("/etudiant/statistiques-notes", new HandlerStatistiqueEtudiant());
        server.createContext("/etudiant/quiz", new HandlerEtudiantQuiz());
        server.createContext("/etudiant/cours-en-ligne", new EtudiantCoursLienHandler());

        // ============================================================
        // 6) PROFESSEUR
        // ============================================================
        server.createContext("/professeur/profil", new HandlerProfesseurProfil());
        server.createContext("/professeur/notes", new HandlerProfesseurNotes());
        server.createContext("/professeur/absences", new HandlerAbsenceProfesseur());
        server.createContext("/professeur/messages", new HandlerMessagesProfesseur());
        server.createContext("/professeur/cours", new HandlerProfesseurCours());
        server.createContext("/professeur/documents", new HandlerProfesseurDocuments());
        server.createContext("/professeur/programmes", new HandlerProgrammeProfesseur());
        server.createContext("/professeur/examens", new HandlerExamenProfesseur());
        server.createContext("/professeur/activites", new HandlerProfesseurActivites());
        server.createContext("/professeur/quiz", new HandlerProfesseurQuiz());
        server.createContext("/professeur/cours-en-ligne", new ProfesseurCoursLienHandler());

        // ============================================================
        // 7) INSCRIPTIONS
        // ============================================================
        server.createContext("/inscription/etudiant", new HandlerInscriptionEtudiant());

        // ============================================================
        // 8) WEBHOOKS
        // ============================================================
        server.createContext("/webhooks/moncash", new MonCashWebhookHandler());

        // ============================================================
        // 9) ACCÈS ADMINS (dépend d'une connexion DB)
        // ============================================================
        Connection conn = null;
        try {
            conn = DatabaseManager.getInstance().getConnection();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Impossible d'obtenir la connexion admin", e);
        }

        if (conn != null) {
            server.createContext("/admin/acces", new UIAccesAdmin(conn));
            server.createContext("/admin/acces/form", new UIFormulaireAcces(conn));
        } else {
            HttpHandler errorHandler = exchange ->
                    ResponseUtil.sendError(exchange, 503, "Service indisponible (connexion DB)");
            server.createContext("/admin/acces", errorHandler);
            server.createContext("/admin/acces/form", errorHandler);
        }

        // ============================================================
        // 10) CONTEXTS GÉNÉRIQUES — TOUJOURS EN DERNIER
        // ============================================================

        // Fallback API : tout /api/* non capturé plus haut
        server.createContext("/api", new HandlerEconomie());

        // Fichiers statiques : /data/* (mais /data/photos/ déjà capturé au-dessus)
        server.createContext("/data", new StaticFileHandler("/data"));

        // Uploads
        server.createContext("/uploads", new UploadHandler());

        // Home — capture TOUT le reste, doit être EN DERNIER
        server.createContext("/", exchange -> {
            try {
                int port = exchange.getLocalAddress().getPort();
                String html = HomeHTMLGenerator.generate(hostPrincipal, port);
                ResponseUtil.sendHtml(exchange, 200, html);
            } catch (IOException e) {
                try {
                    ResponseUtil.sendError(exchange, 500, e.getMessage());
                } catch (IOException ignored) {}
            }
        });
    }

    // ==================== HANDLER LOGO INTERNE ====================

    /**
     * Handler pour le logo M-TECH (asset interne en mémoire, cache long).
     */
    private static void registerLogoHandler(HttpServer server) {
        HttpHandler logoHandler = exchange -> {
            String method = exchange.getRequestMethod();
            if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            try (var in = HomeHandlers.class.getResourceAsStream("/MTECH.PNG")) {
                if (in == null) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }
                byte[] bytes = in.readAllBytes();
                exchange.getResponseHeaders().set("Content-Type", "image/png");
                exchange.getResponseHeaders().set("Cache-Control",
                        "public, max-age=31536000, immutable");
                exchange.getResponseHeaders().set("Content-Length", String.valueOf(bytes.length));
                if ("HEAD".equalsIgnoreCase(method)) {
                    exchange.sendResponseHeaders(200, -1);
                    return;
                }
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Erreur logo", e);
                try { exchange.sendResponseHeaders(500, -1); } catch (IOException ignored) {}
            }
        };

        server.createContext("/assets/logo.png", logoHandler);
        server.createContext("/MTech.PNG", logoHandler);
        server.createContext("/data/MTECH.PNG", logoHandler);
    }

    // ==================== HANDLERS DÉDIÉS ====================

    /**
     * Sert /favicon.ico depuis le classpath, en cache mémoire.
     * Aucun accès DB, aucune session.
     */
    public static class HandlerFavicon implements HttpHandler {
        private static final byte[] FAVICON = chargerFavicon();

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (FAVICON.length == 0) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            exchange.getResponseHeaders().set("Content-Type", "image/x-icon");
            exchange.getResponseHeaders().set("Cache-Control", "public, max-age=86400");
            exchange.sendResponseHeaders(200, FAVICON.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(FAVICON);
            }
        }

        private static byte[] chargerFavicon() {
            try (var in = HomeHandlers.class.getResourceAsStream("/favicon.ico")) {
                return in != null ? in.readAllBytes() : new byte[0];
            } catch (IOException e) {
                return new byte[0];
            }
        }
    }

    /**
     * Endpoint public /api/health.
     * Réponse ultra-rapide, aucun accès DB, aucune session.
     */
    public static class HandlerHealth implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String body = "{\"status\":\"UP\",\"timestamp\":" + System.currentTimeMillis() + "}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    /**
     * Sert /data/photos/* avec cache mémoire.
     * Aucune vérification de session, aucun accès DB.
     */
    public static class HandlerPhotos implements HttpHandler {
        private static final Path PHOTOS_DIR =
                Paths.get("data/photos").toAbsolutePath().normalize();
        private static final Map<String, byte[]> CACHE = new ConcurrentHashMap<>();

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String filename = path.substring("/data/photos/".length());

            // Sécurité : pas de remontée de répertoire
            if (filename.isEmpty()
                    || filename.contains("..")
                    || filename.contains("/")
                    || filename.contains("\\")) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }

            Path file = PHOTOS_DIR.resolve(filename).normalize();
            if (!file.startsWith(PHOTOS_DIR) || !Files.exists(file)
                    || !Files.isRegularFile(file)) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }

            byte[] data = CACHE.computeIfAbsent(filename, k -> {
                try { return Files.readAllBytes(file); }
                catch (IOException e) { return null; }
            });
            if (data == null) {
                exchange.sendResponseHeaders(500, -1);
                return;
            }

            String ct = detecterContentType(filename);
            exchange.getResponseHeaders().set("Content-Type", ct);
            exchange.getResponseHeaders().set("Cache-Control", "public, max-age=86400");
            exchange.sendResponseHeaders(200, data.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(data);
            }
        }

        private static String detecterContentType(String filename) {
            String lower = filename.toLowerCase();
            if (lower.endsWith(".png"))  return "image/png";
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
            if (lower.endsWith(".gif"))  return "image/gif";
            if (lower.endsWith(".webp")) return "image/webp";
            if (lower.endsWith(".svg"))  return "image/svg+xml";
            if (lower.endsWith(".ico"))  return "image/x-icon";
            return "application/octet-stream";
        }
    }

    // ==================== HANDLERS STATIQUES ====================

    public static class UploadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            ResponseUtil.sendNotFound(exchange, "Upload handler non implémenté");
        }
    }

    public static class DbStatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            ResponseUtil.sendJson(exchange, 200, "{\"local\":true,\"remote\":false}");
        }
    }
}