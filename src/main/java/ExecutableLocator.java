import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

public final class ExecutableLocator {

    private static final Logger LOG = Logger.getLogger(ExecutableLocator.class.getName());
    private static volatile Path cachedDir;

    private ExecutableLocator() { }

    /**
     * Retourne le dossier de l'exécutable (exe, jar, ou classes IDE).
     * Résultat mis en cache pour éviter les appels répétés.
     */
    public static Path getExecutableDir() {
        if (cachedDir != null) return cachedDir;

        synchronized (ExecutableLocator.class) {
            if (cachedDir != null) return cachedDir;
            cachedDir = resolve();
            LOG.info(() -> "Dossier d'exécution détecté : " + cachedDir);
            return cachedDir;
        }
    }

    private static Path resolve() {
        // 1. jpackage (Java 14+)
        try {
            String appPath = System.getProperty("jpackage.app-path");
            if (appPath != null && !appPath.isBlank()) {
                File f = new File(appPath);
                if (f.isFile()) return f.getParentFile().toPath().toAbsolutePath().normalize();
            }
        } catch (Exception ignored) { }

        // 2. JAR / classes via ProtectionDomain
        try {
            URI uri = ExecutableLocator.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI();
            File f = new File(uri);
            if (f.isFile()) {
                return f.getParentFile().toPath().toAbsolutePath().normalize();
            }
            // Si on est dans target/classes, remonter pour trouver la racine projet
            Path p = f.toPath().toAbsolutePath().normalize();
            if (p.endsWith("classes") || p.endsWith("build")) {
                Path parent = p.getParent();
                if (parent != null) return parent;
            }
            return p;
        } catch (URISyntaxException e) {
            LOG.fine(() -> "ProtectionDomain indisponible : " + e.getMessage());
        }

        // 3. Fallback : user.dir
        return Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
    }
}