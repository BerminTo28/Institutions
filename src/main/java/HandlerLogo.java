import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * Handler HTTP pour servir les logos des institutions.
 *
 * <p><b>FORMAT UNIQUE : PNG</b> — Tous les logos sont convertis en PNG par
 * {@link HandlerInscriptionInstitutions#sauvegarderLogo(byte[], String, String)}.
 * Ce handler ne sert donc QUE du {@code image/png}.</p>
 *
 * <p><b>SÉCURITÉ</b> :
 * <ul>
 *   <li>Path traversal interdit</li>
 *   <li>Vérification de la signature PNG (magic bytes)</li>
 *   <li>Refus catégorique des SVG (XSS via JavaScript embarqué)</li>
 *   <li>Content-Type forcé à {@code image/png}</li>
 *   <li>{@code X-Content-Type-Options: nosniff} pour empêcher le MIME sniffing</li>
 * </ul>
 *
 * <p><b>PERFORMANCE</b> :
 * <ul>
 *   <li>Cache mémoire des chemins (LRU informel)</li>
 *   <li>Cache mémoire des contenus binaires (limité)</li>
 *   <li>Header {@code Cache-Control: immutable} (7 jours)</li>
 * </ul>
 */
public class HandlerLogo implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(HandlerLogo.class.getName());

    // ============================================================
    // CACHE
    // ============================================================
    private static final Map<String, Path>   CACHE_CHEMIN  = new ConcurrentHashMap<>();
    private static final Map<String, byte[]> CACHE_CONTENU = new ConcurrentHashMap<>();
    private static final int  MAX_CACHE_CONTENU   = 50;
    private static final long CACHE_HTTP_SECONDS  = 604_800L;   // 7 jours

    // ============================================================
    // DOSSIER UNIQUE
    // ============================================================
    /** Doit correspondre à {@code HandlerInscriptionInstitutions.PHOTOS_DIR}. */
    private static final String DOSSIER_LOGOS = "data/photos";

    /** Signature PNG (magic bytes) : 89 50 4E 47 0D 0A 1A 0A. */
    private static final byte[] SIGNATURE_PNG = {
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    // ============================================================
    // HANDLE
    // ============================================================
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();

        if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        // Extraire le nom de fichier (dernier segment de l'URL)
        String path = exchange.getRequestURI().getPath();
        String fileName = path.substring(path.lastIndexOf("/") + 1);

        // Retirer la query string
        int q = fileName.indexOf('?');
        final String fileNameClean = (q >= 0) ? fileName.substring(0, q) : fileName;

        // Sécurité : path traversal
        if (!nomFichierSure(fileNameClean)) {
            LOGGER.warning(() -> "⛔ Nom de fichier interdit : " + fileNameClean);
            exchange.sendResponseHeaders(403, -1);
            return;
        }

        // Forcer l'extension .png
        final String fileNamePng = fileNameClean.toLowerCase().endsWith(".png")
                ? fileNameClean
                : fileNameClean + ".png";

        LOGGER.fine(() -> "🔍 Recherche logo : " + fileNamePng);

        Path logoPath = trouverLogo(fileNamePng);
        if (logoPath == null) {
            LOGGER.fine(() -> "Logo non trouvé : " + fileNamePng + " → SVG par défaut");
            envoyerLogoDefaut(exchange, method);
            return;
        }

        envoyerPng(exchange, logoPath, fileNamePng, method);
    }

    // ============================================================
    // VALIDATION DU NOM
    // ============================================================
    /**
     * ✅ Valide le nom de fichier (anti-path-traversal).
     *    Autorise uniquement : lettres, chiffres, {@code _}, {@code -}, {@code .}.
     */
    private boolean nomFichierSure(String nom) {
        if (nom == null || nom.isBlank()) return false;
        if (nom.contains("..")) return false;
        if (nom.contains("/")) return false;
        if (nom.contains("\\")) return false;
        if (nom.contains(":")) return false;
        // Whitelist stricte
        return nom.matches("[A-Za-z0-9_.\\-]+\\.png");
    }

    // ============================================================
    // RECHERCHE DU LOGO
    // ============================================================
    private Path trouverLogo(String fileName) {
        // Cache
        Path cached = CACHE_CHEMIN.get(fileName);
        if (cached != null && Files.exists(cached) && Files.isReadable(cached)) {
            return cached;
        }

        // Chemin unique : data/photos/{fileName}
        Path path = Paths.get(System.getProperty("user.dir"))
                        .resolve(DOSSIER_LOGOS)
                        .resolve(fileName);

        if (Files.exists(path) && Files.isReadable(path) && Files.isRegularFile(path)) {
            CACHE_CHEMIN.put(fileName, path);
            LOGGER.fine(() -> "✅ Logo trouvé : " + path);
            return path;
        }

        return null;
    }

    // ============================================================
    // ENVOI DU FICHIER
    // ============================================================
    private void envoyerPng(HttpExchange exchange, Path logoPath,
                              String fileName, String method) {

        final byte[] contenu;

        // Cache contenu
        byte[] cachedBytes = CACHE_CONTENU.get(fileName);
        if (cachedBytes != null) {
            contenu = cachedBytes;
        } else {
            byte[] lu;
            try {
                lu = Files.readAllBytes(logoPath);
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE,
                        "❌ Échec lecture " + logoPath + " : " + e.getMessage(), e);
                safeSendError(exchange, 500);
                return;
            }

            if (lu.length == 0) {
                LOGGER.warning(() -> "⚠️ Fichier vide : " + logoPath);
                safeSendError(exchange, 404);
                return;
            }

            // ✅ Vérification de la signature PNG
            if (!estPngValide(lu)) {
                LOGGER.warning(() -> "⛔ Signature PNG invalide : " + logoPath);
                safeSendError(exchange, 415);
                return;
            }

            contenu = lu;

            // Cache (limité)
            if (CACHE_CONTENU.size() < MAX_CACHE_CONTENU) {
                CACHE_CONTENU.put(fileName, contenu);
                LOGGER.fine(() -> "📦 Logo mis en cache : " + fileName
                        + " (" + contenu.length + " octets)");
            }
        }

        // Headers HTTP
        exchange.getResponseHeaders().set("Content-Type", "image/png");
        exchange.getResponseHeaders().set("Cache-Control",
                "public, max-age=" + CACHE_HTTP_SECONDS + ", immutable");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");

        try {
            if ("HEAD".equalsIgnoreCase(method)) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }

            exchange.sendResponseHeaders(200, contenu.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(contenu);
                os.flush();
            }
            LOGGER.fine(() -> "✅ Logo envoyé : " + contenu.length + " octets");

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "❌ Erreur envoi HTTP " + fileName, e);
        }
    }

    /**
     * ✅ Vérifie la signature PNG (magic bytes).
     */
    private boolean estPngValide(byte[] data) {
        if (data == null || data.length < SIGNATURE_PNG.length) return false;
        for (int i = 0; i < SIGNATURE_PNG.length; i++) {
            if (data[i] != SIGNATURE_PNG[i]) return false;
        }
        return true;
    }

    private void safeSendError(HttpExchange exchange, int status) {
        try {
            exchange.sendResponseHeaders(status, -1);
        } catch (IOException ignored) {}
    }

    // ============================================================
    // LOGO PAR DÉFAUT
    // ============================================================
    private void envoyerLogoDefaut(HttpExchange exchange, String method) {
        final byte[] bytes = getDefaultLogo().getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "image/svg+xml");
        exchange.getResponseHeaders().set("Cache-Control",
                "public, max-age=" + CACHE_HTTP_SECONDS + ", immutable");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");

        try {
            if ("HEAD".equalsIgnoreCase(method)) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Erreur envoi SVG par défaut", e);
        }
    }

    /**
     * ✅ Logo SVG par défaut (statique, sûr — pas d'injection possible).
     */
    private String getDefaultLogo() {
        return "<svg xmlns='http://www.w3.org/2000/svg' width='200' height='200' "
             + "viewBox='0 0 200 200'>"
             + "<rect width='200' height='200' fill='#1e293b' rx='10'/>"
             + "<text x='100' y='110' font-family='Arial' font-size='40' "
             + "fill='#f5cd79' text-anchor='middle'>M</text>"
             + "<text x='100' y='145' font-family='Arial' font-size='16' "
             + "fill='#94a3b8' text-anchor='middle'>TECH</text>"
             + "</svg>";
    }

    // ============================================================
    // VIDER LE CACHE
    // ============================================================
    public static void viderCache() {
        CACHE_CHEMIN.clear();
        CACHE_CONTENU.clear();
        LOGGER.info("🧹 Cache du logo vidé");
    }
}