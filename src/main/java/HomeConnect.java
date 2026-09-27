import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class HomeConnect {

    private static final Logger LOGGER = Logger.getLogger(HomeConnect.class.getName());
    private static final int NGROK_API_PORT = 4040;

    /** ✅ Alias DNS principal (sans slash). */
    private static final String DEFAULT_ALIAS_PRINCIPAL = "mtech-study-for-your.life";

    /** ✅ Hostname local (défini dans le fichier hosts, sans slash). */
    private static final String HOSTNAME_LOCAL = "mtech-study-for-your.life";

    /** ✅ Chemin par défaut. */
    private static final String PATH_DEFAUT = "/mtech";

    /** ✅ Port principal par défaut (aligné avec Home.PORT_PRINCIPAL). */
    private static final int PORT_PRINCIPAL_DEFAUT = 8080;

    private static String hostPrincipal = "localhost";

    private static final long NGROK_STARTUP_TIMEOUT_MS = 8_000L;
    private static volatile Process ngrokProcess;

    public static String getHostPrincipal() {
        return hostPrincipal;
    }

    // ==================== CONFIGURATION RÉSEAU ====================

    public static void detecterConfigurationReseau() {
        String envHost = System.getenv("APP_HOST");
        if (envHost != null && !envHost.isBlank()) {
            hostPrincipal = envHost;
            LOGGER.fine(() -> "Hôte principal (env): " + envHost);
            return;
        }

        String envMode = System.getenv("APP_MODE");
        if ("dev".equalsIgnoreCase(envMode) || "development".equalsIgnoreCase(envMode)) {
            hostPrincipal = HOSTNAME_LOCAL;
            LOGGER.fine("Mode développement : " + HOSTNAME_LOCAL);
            return;
        }

        if (estDomaineResolvable(DEFAULT_ALIAS_PRINCIPAL)) {
            hostPrincipal = DEFAULT_ALIAS_PRINCIPAL;
            LOGGER.fine(() -> "Hôte principal (DNS): " + DEFAULT_ALIAS_PRINCIPAL);
        } else {
            String localIp = getLocalIpAddress();
            if (localIp != null) {
                hostPrincipal = localIp;
                LOGGER.fine(() -> "Hôte principal (IP): " + localIp);
            }
        }
    }

    private static boolean estDomaineResolvable(String domain) {
        try { InetAddress.getByName(domain); return true; }
        catch (java.net.UnknownHostException e) { return false; }
    }

    private static String getLocalIpAddress() {
        try {
            InetAddress ip = InetAddress.getLocalHost();
            if (!ip.isLoopbackAddress()) return ip.getHostAddress();
        } catch (java.net.UnknownHostException ignored) {}
        return null;
    }

    // ==================== NAVIGATION ====================

    public static void ouvrirNavigateurIntelligent() {
        String ngrokUrl = getNgrokPublicUrl();
        if (ngrokUrl != null && !ngrokUrl.isBlank()) {
            System.out.println("🌐 Ouverture sur ngrok : " + ngrokUrl);
            ouvrirNavigateur(ngrokUrl);
            return;
        }

        System.out.println("⏳ Attente de ngrok (max " + (NGROK_STARTUP_TIMEOUT_MS / 1000) + "s)...");
        String url = attendreNgrokAvecTimeout(NGROK_STARTUP_TIMEOUT_MS, PORT_PRINCIPAL_DEFAUT);

        if (url != null && !url.isBlank()) {
            System.out.println("🌐 Ouverture sur ngrok : " + url);
            ouvrirNavigateur(url);
        } else {
            String localUrl = construireUrlLocale(PORT_PRINCIPAL_DEFAUT);
            System.out.println("🔗 Ngrok indisponible — ouverture sur : " + localUrl);
            ouvrirNavigateur(localUrl);
        }
    }

    /**
     * ✅ Construit l'URL locale avec le hostname personnalisé.
     */
    private static String construireUrlLocale(int port) {
        StringBuilder url = new StringBuilder("http://");
        url.append(HOSTNAME_LOCAL != null && !HOSTNAME_LOCAL.isBlank()
                ? HOSTNAME_LOCAL
                : "localhost");
        if (port != 80 && port != 443) {
            url.append(":").append(port);
        }
        url.append(PATH_DEFAUT != null && !PATH_DEFAUT.isBlank() ? PATH_DEFAUT : "/mtech");
        return url.toString();
    }

    private static String attendreNgrokAvecTimeout(long timeoutMs, int portPrincipal) {
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        final AtomicReference<String> urlRef = new AtomicReference<>();
        final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ngrok-wait-browser");
            t.setDaemon(true);
            return t;
        });

        try {
            scheduler.scheduleAtFixedRate(() -> {
                String j = fetchNgrokTunnelsJson();
                if (ngrokTourneSurPort(j, portPrincipal)) {
                    String url = getNgrokPublicUrl();
                    if (url != null && !url.isBlank()) {
                        urlRef.set(url);
                        latch.countDown();
                    }
                }
            }, 0, 500, TimeUnit.MILLISECONDS);

            boolean ok = latch.await(timeoutMs, TimeUnit.MILLISECONDS);
            return ok ? urlRef.get() : null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            scheduler.shutdownNow();
        }
    }

    public static void ouvrirNavigateur(String url) {
        try {
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            } else {
                System.out.println("🔗 Ouvrez votre navigateur sur : " + url);
            }
        } catch (IOException | URISyntaxException e) {
            System.out.println("🔗 Ouvrez votre navigateur sur : " + url);
        }
    }

    // ==================== PRÉPARATION & ÉTAT NGROK ====================

    public static void preparerNgrok(int portPrincipal) {
        String json = fetchNgrokTunnelsJson();
        if (json == null) return;

        if (ngrokTourneSurPort(json, portPrincipal)) {
            LOGGER.fine(() -> "✅ Ngrok déjà actif sur le port " + portPrincipal);
            return;
        }

        LOGGER.warning("⚠️ Ngrok tourne sur un mauvais port. Arrêt...");
        tuerTousLesNgrok();
        attendrePortLibre(NGROK_API_PORT, 3000);
    }

    private static void tuerTousLesNgrok() {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                Process p = new ProcessBuilder("taskkill", "/F", "/IM", "ngrok.exe")
                        .redirectErrorStream(true).start();
                p.waitFor(3, TimeUnit.SECONDS);
            } else {
                Process p = new ProcessBuilder("pkill", "-f", "ngrok").start();
                p.waitFor(3, TimeUnit.SECONDS);
            }
        } catch (IOException | InterruptedException e) {
            LOGGER.fine(() -> "Aucun ngrok à tuer: " + e.getMessage());
        } finally {
            ngrokProcess = null;
        }
    }

    private static void attendrePortLibre(int port, long timeoutMs) {
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "port-wait");
            t.setDaemon(true);
            return t;
        });

        try {
            scheduler.scheduleAtFixedRate(() -> {
                if (isPortLibre(port)) latch.countDown();
            }, 0, 200, TimeUnit.MILLISECONDS);

            latch.await(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            scheduler.shutdownNow();
        }
    }

    private static boolean isPortLibre(int port) {
        try (java.net.ServerSocket s = new java.net.ServerSocket(port)) {
            s.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static String fetchNgrokTunnelsJson() {
        HttpURLConnection conn = null;
        try {
            URI uri = URI.create("http://127.0.0.1:" + NGROK_API_PORT + "/api/tunnels");
            conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(800);
            conn.setReadTimeout(800);
            if (conn.getResponseCode() != 200) return null;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                return sb.toString();
            }
        } catch (IOException e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static boolean ngrokTourneSurPort(String json, int expectedPort) {
        if (json == null) return false;
        Pattern p = Pattern.compile("\"addr\"\\s*:\\s*\"http://(?:localhost|127\\.0\\.0\\.1):(\\d+)\"");
        Matcher m = p.matcher(json);
        while (m.find()) {
            try { if (Integer.parseInt(m.group(1)) == expectedPort) return true; }
            catch (NumberFormatException ignored) {}
        }
        return false;
    }

    private static String getNgrokPublicUrl() {
        String json = fetchNgrokTunnelsJson();
        if (json == null) return null;
        Pattern p = Pattern.compile("\"public_url\"\\s*:\\s*\"(https?://[^\"]+)\"");
        Matcher m = p.matcher(json);
        String fallback = null;
        while (m.find()) {
            String url = m.group(1);
            if (url.startsWith("https://")) return url;
            fallback = url;
        }
        return fallback;
    }

    // ==================== DÉMARRAGE NGROK ====================

    public static void demarrerNgrok(int portPrincipal) {
        LOGGER.fine("🚀 Démarrage ngrok (arrière-plan)...");

        String json = fetchNgrokTunnelsJson();
        if (ngrokTourneSurPort(json, portPrincipal)) {
            String url = getNgrokPublicUrl();
            if (url != null) afficherUrlPublique(url, portPrincipal);
            return;
        }

        String command = trouverNgrokExecutable();
        if (command == null) {
            LOGGER.warning(() -> "📌 Ngrok non trouvé. Lancez: ngrok http " + portPrincipal);
            return;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    command, "http", String.valueOf(portPrincipal), "--log=stdout");
            pb.redirectErrorStream(true);
            ngrokProcess = pb.start();

            Thread logReader = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(ngrokProcess.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) System.out.println("[ngrok] " + line);
                } catch (IOException ignored) {}
            }, "ngrok-log-reader");
            logReader.setDaemon(true);
            logReader.start();

            String publicUrl = attendreNgrok(NGROK_STARTUP_TIMEOUT_MS, portPrincipal);
            if (publicUrl != null) {
                afficherUrlPublique(publicUrl, portPrincipal);
            } else {
                LOGGER.warning(() -> "⚠️ Ngrok ne répond pas après "
                        + (NGROK_STARTUP_TIMEOUT_MS / 1000) + "s.");
            }

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "❌ Erreur ngrok: " + e.getMessage(), e);
        }
    }

    private static String attendreNgrok(long timeoutMs, int portPrincipal) {
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        final AtomicReference<String> urlRef = new AtomicReference<>();
        final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ngrok-wait");
            t.setDaemon(true);
            return t;
        });

        try {
            scheduler.scheduleAtFixedRate(() -> {
                String j = fetchNgrokTunnelsJson();
                if (ngrokTourneSurPort(j, portPrincipal)) {
                    String url = getNgrokPublicUrl();
                    if (url != null) {
                        urlRef.set(url);
                        latch.countDown();
                    }
                }
            }, 0, 500, TimeUnit.MILLISECONDS);

            boolean ok = latch.await(timeoutMs, TimeUnit.MILLISECONDS);
            return ok ? urlRef.get() : null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            scheduler.shutdownNow();
        }
    }

    private static String trouverNgrokExecutable() {
        String path = trouverNgrok();
        if (path != null) return path;

        try {
            Process p = new ProcessBuilder("ngrok", "--version").redirectErrorStream(true).start();
            if (p.waitFor(3, TimeUnit.SECONDS) && p.exitValue() == 0) return "ngrok";
        } catch (IOException | InterruptedException ignored) {}

        return null;
    }

    private static String trouverNgrok() {
        LOGGER.fine("🔍 Recherche de ngrok.exe...");

        String userHome = System.getProperty("user.home");
        String userDir = System.getProperty("user.dir");

        List<String> searchPaths = new ArrayList<>();
        searchPaths.add(userHome + "\\OneDrive\\Users\\mingo\\Desktop\\inconshreveable-ngrok-09cd37a\\ngrok.exe");
        searchPaths.add(userHome + "\\OneDrive\\Desktop\\inconshreveable-ngrok-09cd37a\\ngrok.exe");
        searchPaths.add(userHome + "\\Desktop\\inconshreveable-ngrok-09cd37a\\ngrok.exe");
        searchPaths.add(userHome + "\\Desktop\\ngrok\\ngrok.exe");
        searchPaths.add(userHome + "\\ngrok\\ngrok.exe");
        searchPaths.add(userHome + "\\AppData\\Local\\ngrok\\ngrok.exe");
        searchPaths.add(userHome + "\\AppData\\Local\\Programs\\ngrok\\ngrok.exe");
        searchPaths.add(userHome + "\\Downloads\\ngrok\\ngrok.exe");
        searchPaths.add(userHome + "\\Downloads\\ngrok-v3-stable-windows-amd64\\ngrok.exe");
        searchPaths.add("C:\\ngrok\\ngrok.exe");
        searchPaths.add("C:\\Program Files\\ngrok\\ngrok.exe");
        searchPaths.add(userDir + "\\ngrok.exe");
        searchPaths.add(userDir + "\\ngrok\\ngrok.exe");
        searchPaths.add(userDir + "\\tools\\ngrok\\ngrok.exe");

        for (String path : searchPaths) {
            if (path == null || path.isBlank()) continue;
            try {
                Path p = Paths.get(path);
                if (Files.exists(p) && Files.isRegularFile(p) && Files.isReadable(p)) {
                    return p.toString();
                }
            } catch (Exception ignored) {}
        }

        for (Path base : new Path[] { Paths.get(userHome), Paths.get(userDir) }) {
            try (Stream<Path> paths = Files.find(base, 3,
                    (p, attrs) -> Files.isRegularFile(p)
                            && p.getFileName().toString().equalsIgnoreCase("ngrok.exe"))) {
                Optional<Path> result = paths.findFirst();
                if (result.isPresent()) {
                    String s = result.get().toString().toLowerCase();
                    if (s.contains("windowsapps")) continue;
                    return result.get().toString();
                }
            } catch (Exception ignored) {}
        }

        return null;
    }

    /**
     * ✅ Affiche les URLs publiques et locales.
     *    Utilise le hostname personnalisé pour l'URL locale.
     */
    private static void afficherUrlPublique(String url, int portPrincipal) {
        String localUrl = construireUrlLocale(portPrincipal);

        System.out.println();
        System.out.println("✅ ==================================================");
        System.out.println("✅  LIEN PUBLIC NGROK  : " + url);
        System.out.println("✅  SERVEUR LOCAL      : " + localUrl);
        System.out.println("✅  INTERFACE NGROK    : http://localhost:" + NGROK_API_PORT);
        System.out.println("✅ ==================================================");
        System.out.println();
    }
}