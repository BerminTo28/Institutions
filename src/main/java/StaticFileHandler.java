import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * Handler HTTP pour servir des fichiers statiques.
 *
 * Stratégie de résolution (dans l'ordre) :
 *   1. Fichiers sur disque à côté de l'exécutable (exe/jar) → permet à l'utilisateur
 *      de personnaliser/override les ressources sans recompiler.
 *   2. Ressources embarquées dans le JAR/exe → filet de sécurité ABSOLU, toujours présent.
 *
 * Pour un logo "stable" (ex: MTech.png) :
 *   - Le placer dans src/main/resources/static/MTech.png
 *   - Instancier : new StaticFileHandler("static")
 *   - Il sera TOUJOURS servi, même si les fichiers externes sont supprimés.
 */
public class StaticFileHandler implements HttpHandler {

    private static final Logger LOG = Logger.getLogger(StaticFileHandler.class.getName());

    /** Durée de cache pour les ressources stables (1 an). */
    private static final String CACHE_CONTROL = "public, max-age=31536000, immutable";

    private final String subPath; // ex: "static", "assets", "" pour racine
    private final Path baseDir;   // dossier racine résolu une fois pour toutes

    public StaticFileHandler(String basePath) {
        this.subPath = normalizeSubPath(basePath);
        this.baseDir = resolveBaseDir();
        LOG.info(() -> "StaticFileHandler initialisé : subPath=[" + this.subPath
                + "] baseDir=[" + this.baseDir + "]");
    }

    // =========================================================================
    // POINT D'ENTRÉE
    // =========================================================================

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String method = exchange.getRequestMethod();
            boolean isHead = "HEAD".equalsIgnoreCase(method);

            // 1. Méthodes autorisées uniquement
            if (!"GET".equalsIgnoreCase(method) && !isHead) {
                exchange.getResponseHeaders().set("Allow", "GET, HEAD");
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            // 2. Extraction du chemin relatif (avec anti-traversal)
            String relative = extractSafeRelative(exchange);
            if (relative == null) {
                sendError(exchange, 400, "Requête invalide");
                return;
            }
            if (relative.isEmpty()) {
                sendError(exchange, 404, "Fichier non trouvé");
                return;
            }

            LOG.fine(() -> "Requête " + method + " → subPath=[" + subPath
                    + "] relative=[" + relative + "]");

            // 3. Tentative sur disque
            Path diskFile = findOnDisk(relative);
            if (diskFile != null) {
                serveDisk(exchange, diskFile, isHead);
                return;
            }

            // 4. Fallback : ressource interne embarquée (JAR/exe)
            if (serveInternalResource(exchange, relative, isHead)) {
                return;
            }

            // 5. Rien trouvé
            LOG.info(() -> "Fichier introuvable : " + relative);
            sendError(exchange, 404, "Fichier non trouvé");

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Erreur inattendue dans StaticFileHandler", e);
            // Envoi d'un 500 propre si les headers ne sont pas déjà partis
            try {
                exchange.sendResponseHeaders(500, -1);
            } catch (IOException ignored) {
                // headers déjà envoyés, on ne peut plus rien faire
            }
        }
    }

    // =========================================================================
    // EXTRACTION ET SÉCURISATION DU CHEMIN
    // =========================================================================

    private String normalizeSubPath(String basePath) {
        if (basePath == null) return "";
        return basePath.replaceAll("^/+", "").replaceAll("/+$", "");
    }

    /**
     * Extrait le chemin relatif demandé, en filtrant toute tentative
     * de directory traversal (../, ..\, %2e%2e, etc.).
     *
     * Retourne null si le chemin est invalide/suspect.
     */
    private String extractSafeRelative(HttpExchange exchange) {
        // getRawPath() : version encodée, non décodée par le serveur
        String rawPath = exchange.getRequestURI().getRawPath();
        if (rawPath == null) return null;

        // Décodage manuel contrôlé
        String decoded;
        try {
            decoded = java.net.URLDecoder.decode(rawPath, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }

        // Normalisation des séparateurs (Windows/Linux)
        decoded = decoded.replace('\\', '/');

        // Suppression du préfixe subPath si présent
        String relative = decoded.replaceAll("^/+", "");
        if (!subPath.isEmpty()) {
            if (relative.equals(subPath)) {
                relative = "";
            } else if (relative.startsWith(subPath + "/")) {
                relative = relative.substring(subPath.length() + 1);
            }
        }

        // Rejet explicite de tout segment ".."
        for (String segment : relative.split("/")) {
            if ("..".equals(segment)) {
                LOG.log(Level.WARNING, "Tentative de directory traversal bloqu\u00e9e : {0}", decoded);
                return null;
            }
        }

        // Normalisation finale
        relative = relative.replaceAll("/{2,}", "/");
        if (relative.startsWith("/")) relative = relative.substring(1);

        return relative;
    }

    // =========================================================================
    // RÉSOLUTION DU DOSSIER DE BASE (compatible exe, jar, IDE)
    // =========================================================================

    private Path resolveBaseDir() {
        File dir = locateExecutableDir();
        return dir != null ? dir.toPath().toAbsolutePath().normalize()
                           : Paths.get(System.getProperty("user.dir"));
    }

    /**
     * Localise le dossier de l'exécutable en cours, quel que soit
     * le mode de packaging :
     *   - jpackage (Java 14+) → propriété jpackage.app-path
     *   - JAR classique        → parent du .jar
     *   - IDE / classes        → dossier des classes
     *   - fallback             → user.dir
     */
    private File locateExecutableDir() {
        // 1. jpackage : chemin absolu vers l'exécutable natif
        try {
            String appPath = System.getProperty("jpackage.app-path");
            if (appPath != null && !appPath.isEmpty()) {
                File f = new File(appPath);
                if (f.isFile()) return f.getParentFile();
            }
        } catch (Exception ignored) { }

        // 2. JAR classique / classes IDE via ProtectionDomain
        try {
            URI uri = StaticFileHandler.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI();
            File f = new File(uri);
            if (f.isFile()) return f.getParentFile(); // JAR
            return f;                                  // dossier de classes
        } catch (URISyntaxException | NullPointerException e) {
            LOG.fine(() -> "Impossible de localiser l'exe via ProtectionDomain : " + e);
        }

        // 3. Fallback
        return new File(System.getProperty("user.dir"));
    }

    // =========================================================================
    // RECHERCHE SUR DISQUE
    // =========================================================================

    private Path findOnDisk(String relative) {
        if (relative.isEmpty()) return null;

        // Candidats possibles, du plus prioritaire au moins prioritaire
        Path[] candidats;
        if (subPath.isEmpty()) {
            candidats = new Path[] {
                baseDir.resolve(relative),
                baseDir.resolve("resources").resolve(relative),
                Paths.get(System.getProperty("user.dir")).resolve(relative)
            };
        } else {
            candidats = new Path[] {
                baseDir.resolve(subPath).resolve(relative),
                baseDir.resolve("resources").resolve(subPath).resolve(relative),
                Paths.get(System.getProperty("user.dir")).resolve(subPath).resolve(relative)
            };
        }

        for (Path p : candidats) {
            Path normalized = p.normalize().toAbsolutePath();
            // Double vérification anti-traversal après resolve()
            if (!normalized.startsWith(baseDir) &&
                !normalized.startsWith(Paths.get(System.getProperty("user.dir")).toAbsolutePath())) {
                continue;
            }
            if (Files.isRegularFile(normalized) && Files.isReadable(normalized)) {
                return normalized;
            }
        }
        return null;
    }

    // =========================================================================
    // SERVICE DEPUIS LE DISQUE
    // =========================================================================

    private void serveDisk(HttpExchange exchange, Path file, boolean isHead) throws IOException {
        long size;
        try {
            size = Files.size(file); // peut lancer IOException si disparu (TOCTOU)
        } catch (IOException e) {
            LOG.warning(() -> "Fichier disparu entre détection et lecture : " + file);
            sendError(exchange, 404, "Fichier non trouvé");
            return;
        }

        LOG.fine(() -> "Fichier servi depuis le disque : " + file + " (" + size + " octets)");

        exchange.getResponseHeaders().set("Content-Type", getMimeType(file.toString()));
        exchange.getResponseHeaders().set("Cache-Control", CACHE_CONTROL);

        if (isHead) {
            exchange.sendResponseHeaders(200, -1);
            return;
        }

        exchange.sendResponseHeaders(200, size);
        try (InputStream in = Files.newInputStream(file);
             OutputStream out = exchange.getResponseBody()) {
            in.transferTo(out); // streaming, pas de chargement en RAM
        }
    }

    // =========================================================================
    // SERVICE DEPUIS LES RESSOURCES INTERNES
    // =========================================================================

    private boolean serveInternalResource(HttpExchange exchange,
                                          String relative, boolean isHead) throws IOException {
        // Construction du chemin interne : "/subPath/relative" ou "/relative"
        String resourcePath = subPath.isEmpty()
                ? "/" + relative
                : "/" + subPath + "/" + relative;

        LOG.fine(() -> "Recherche interne : " + resourcePath);

        try (InputStream in = StaticFileHandler.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                LOG.fine(() -> "Ressource interne absente : " + resourcePath);
                return false;
            }

            byte[] bytes = in.readAllBytes(); // OK pour des ressources typiques (logo, css, js)

            LOG.fine(() -> "Ressource interne servie : " + resourcePath
                    + " (" + bytes.length + " octets)");

            exchange.getResponseHeaders().set("Content-Type", getMimeType(relative));
            exchange.getResponseHeaders().set("Cache-Control", CACHE_CONTROL);

            if (isHead) {
                exchange.sendResponseHeaders(200, -1);
                return true;
            }

            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
            return true;
        }
    }

    // =========================================================================
    // MIME TYPES
    // =========================================================================

    private String getMimeType(String path) {
        if (path == null) return "application/octet-stream";

        // 1. Tentative via Files.probeContentType (dépend du système)
        try {
            String probed = Files.probeContentType(Paths.get(path));
            if (probed != null) return probed;
        } catch (IOException ignored) { }

        // 2. Fallback manuel étendu
        String lower = path.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".bmp")) return "image/bmp";
        if (lower.endsWith(".ico")) return "image/x-icon";
        if (lower.endsWith(".tiff") || lower.endsWith(".tif")) return "image/tiff";
        if (lower.endsWith(".css")) return "text/css; charset=UTF-8";
        if (lower.endsWith(".js")) return "application/javascript; charset=UTF-8";
        if (lower.endsWith(".mjs")) return "application/javascript; charset=UTF-8";
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html; charset=UTF-8";
        if (lower.endsWith(".txt")) return "text/plain; charset=UTF-8";
        if (lower.endsWith(".xml")) return "application/xml; charset=UTF-8";
        if (lower.endsWith(".json")) return "application/json; charset=UTF-8";
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".woff")) return "font/woff";
        if (lower.endsWith(".woff2")) return "font/woff2";
        if (lower.endsWith(".ttf")) return "font/ttf";
        if (lower.endsWith(".otf")) return "font/otf";
        if (lower.endsWith(".mp4")) return "video/mp4";
        if (lower.endsWith(".webm")) return "video/webm";
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".wasm")) return "application/wasm";
        if (lower.endsWith(".zip")) return "application/zip";

        return "application/octet-stream";
    }

    // =========================================================================
    // ERREURS
    // =========================================================================

    private void sendError(HttpExchange exchange, int status, String message) throws IOException {
        String body = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\">"
                + "<title>" + status + "</title></head><body>"
                + "<h1>" + status + " - " + message + "</h1>"
                + "</body></html>";
        byte[] data = body.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(status, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
        }
    }
}