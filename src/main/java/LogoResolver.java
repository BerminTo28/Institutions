import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;


public final class LogoResolver {

    private static final Logger LOGGER = Logger.getLogger(LogoResolver.class.getName());

    /** Dossier unique où sont stockés les logos (aligné avec HandlerInscriptionInstitutions). */
    private static final String DOSSIER_LOGOS = "data/photos";

    /** Signature PNG (magic bytes) : 89 50 4E 47 0D 0A 1A 0A. */
    private static final byte[] SIGNATURE_PNG = {
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    // ============================================================
    // CONSTRUCTEUR PRIVÉ (classe utilitaire)
    // ============================================================
    private LogoResolver() {
        // Non instanciable
    }

    // ============================================================
    // RÉSOLUTION
    // ============================================================
    /**
     * ✅ Recherche le fichier logo PNG sur le disque.
     *
     * @param fileName Nom du fichier ou URL (ex: {@code logo_INST_ABC.png?v=1})
     * @return le {@link Path} vers le fichier physique, ou {@code null} si introuvable
     */
    public static Path trouverLogo(String fileName) {
        // 1) Nettoyage : retirer query string et espaces
        final String fileNameClean = nettoyerNomFichier(fileName);
        if (fileNameClean == null) {
            return null;
        }

        // 2) Forcer l'extension .png
        final String fileNamePng = fileNameClean.toLowerCase().endsWith(".png")
                ? fileNameClean
                : fileNameClean + ".png";

        // 3) Chemin unique
        final Path candidate = Paths.get(System.getProperty("user.dir"))
                                    .resolve(DOSSIER_LOGOS)
                                    .resolve(fileNamePng);

        // 4) Vérification
        if (Files.exists(candidate)
                && Files.isReadable(candidate)
                && Files.isRegularFile(candidate)) {

            LOGGER.fine(() -> "✅ Logo résolu : " + candidate);
            return candidate;
        }

        LOGGER.fine(() -> "⚠️ Logo introuvable : " + fileNamePng);
        return null;
    }

    // ============================================================
    // NETTOYAGE
    // ============================================================
    /**
     * ✅ Nettoie un nom de fichier :
     *    - Retire la query string ({@code ?v=...})
     *    - Retire les espaces
     *    - Extrait uniquement le nom (pas de chemin)
     *    - Rejette les noms dangereux ({@code ..}, {@code /}, ...)
     *
     * @return le nom nettoyé, ou {@code null} si invalide
     */
    private static String nettoyerNomFichier(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }

        // Retirer la query string
        String nettoye = fileName;
        int q = nettoye.indexOf('?');
        if (q >= 0) {
            nettoye = nettoye.substring(0, q);
        }

        // Trim
        nettoye = nettoye.trim();
        if (nettoye.isEmpty()) {
            return null;
        }

        // Extraire uniquement le nom de fichier (au cas où un chemin serait fourni)
        try {
            Path p = Paths.get(nettoye);
            if (p.getFileName() != null) {
                nettoye = p.getFileName().toString();
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Nom de fichier invalide : " + fileName, e);
            return null;
        }

        // Sécurité : refuser les noms dangereux
        if (nettoye.contains("..") || nettoye.contains("/")
                || nettoye.contains("\\") || nettoye.contains(":")) {
            LOGGER.warning(() -> "⛔ Nom de fichier dangereux : " + fileName);
            return null;
        }

        // Whitelist : uniquement [A-Za-z0-9_.-]
        if (!nettoye.matches("[A-Za-z0-9_.\\-]+")) {
            LOGGER.warning(() -> "⛔ Caractères non autorisés dans : " + fileName);
            return null;
        }

        return nettoye;
    }

    public static boolean estPngValide(Path file) {
        if (file == null || !Files.exists(file)) return false;

        try {
            byte[] header = new byte[SIGNATURE_PNG.length];
            try (var in = Files.newInputStream(file)) {
                int lus = in.read(header);
                if (lus != SIGNATURE_PNG.length) return false;
            }
            for (int i = 0; i < SIGNATURE_PNG.length; i++) {
                if (header[i] != SIGNATURE_PNG[i]) return false;
            }
            return true;
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "Erreur vérification PNG : " + file, e);
            return false;
        }
    }
}