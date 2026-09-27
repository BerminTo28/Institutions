import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.EnumSet;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class Home {

    private static final Logger LOGGER = Logger.getLogger(Home.class.getName());

    // ============================================================
    // CONFIGURATION
    // ============================================================
    private static final int PORT_PRINCIPAL = 8080;

    /** Hostname personnalisé (défini dans le fichier hosts). */
    private static final String HOSTNAME_LOCAL = "mtech-study-for-your.life";

    /** Chemin par défaut. */
    private static final String PATH_DEFAUT = "/mtech";

    /** Sources de détermination de l'institution. */
    private static final String ENV_INSTITUTION_ID    = "INSTITUTION_ID";
    private static final String ENV_MTECH_INSTITUTION = "MTECH_INSTITUTION";
    private static final String PROP_INSTITUTION_ID   = "institutionId";
    private static final String CONFIG_FILE           = "config.properties";
    private static final String DB_CONFIG_FILE        = "database.properties";

    /** Préfixe d'argument CLI. */
    private static final String ARG_INSTITUTION_PREFIX = "--institution=";

    /** Clés de configuration de la base secondaire. */
    private static final String DB_SECONDARY_HOST     = "db.secondary.host";
    private static final String DB_SECONDARY_PORT     = "db.secondary.port";
    private static final String DB_SECONDARY_DATABASE = "db.secondary.database";
    private static final String DB_SECONDARY_USERNAME = "db.secondary.username";
    private static final String DB_SECONDARY_PASSWORD = "db.secondary.password";

    /** Requête et URL JDBC. */
    private static final String SQL_SELECT_INSTITUTION =
            "SELECT institution_id FROM institutions LIMIT 1";
    private static final String JDBC_URL_TEMPLATE =
            "jdbc:mysql://%s:%s/%s?useSSL=false&serverTimezone=UTC"
            + "&allowPublicKeyRetrieval=true&connectTimeout=5000&socketTimeout=5000";

    /** Nettoyage des sessions. */
    private static final int NETTOYAGE_MAX_DEPTH = 2;
    private static final int NETTOYAGE_MAX_FILES = 500;
    private static final String[] NETTOYAGE_PATTERNS =
            {".log", ".tmp", ".temp", ".lock", ".lck"};

    // ============================================================
    // CONSTRUCTEUR PRIVÉ (classe utilitaire)
    // ============================================================
    private Home() {
        // Classe utilitaire — non instanciable
    }

    // ============================================================
    // POINT D'ENTRÉE
    // ============================================================
    public static void main(String[] args) {
        final long t0 = System.currentTimeMillis();
        System.out.println("🚀 Démarrage de l'application...");

        nettoyerSessionsJava();

        final String institutionId;
        try {
            institutionId = determinerInstitutionId(args);
            LOGGER.info(() -> "🏢 Institution : " + institutionId);
        } catch (IllegalStateException e) {
            LOGGER.log(Level.SEVERE, "❌ Impossible de déterminer l'institution", e);
            System.err.println("   → définis INSTITUTION_ID en variable d'environnement,");
            System.err.println("     ou -DinstitutionId=XXX, ou dans config.properties.");
            System.exit(1);
            return;
        }

        DatabaseManager db = initialiserDatabaseManager(institutionId);
        demanderSyncImmediate(db);

        HomeConnect.detecterConfigurationReseau();
        HomeHandlers.demarrerServeurUnique(PORT_PRINCIPAL, HomeConnect.getHostPrincipal());

        ouvrirInterface(db);

        final long duree = System.currentTimeMillis() - t0;
        LOGGER.info(() -> "✅ Application démarrée en " + duree
                + " ms (institution=" + institutionId + ")");
    }

    // ============================================================
    // INITIALISATION
    // ============================================================
    private static DatabaseManager initialiserDatabaseManager(String institutionId) {
        try {
            DatabaseManager.initialiser(institutionId);
            DatabaseManager db = DatabaseManager.getInstance();
            LOGGER.info(() -> "✅ DatabaseManager initialisé pour " + institutionId);
            return db;
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE,
                    "❌ Erreur initialisation DatabaseManager pour " + institutionId, e);
            return null;
        }
    }

    private static void demanderSyncImmediate(DatabaseManager db) {
        if (db == null) return;
        db.declencherSyncImmediateAsync();
        LOGGER.info("🔄 Sync immédiate demandée au démarrage");
    }

    private static void ouvrirInterface(DatabaseManager db) {
        final boolean primaryConnected =
                (db != null && db.isRemoteConfigured() && db.isRemoteHealthy());

        if (primaryConnected) {
            HomeConnect.preparerNgrok(PORT_PRINCIPAL);
            new Thread(() -> HomeConnect.demarrerNgrok(PORT_PRINCIPAL), "ngrok-start").start();
            new Thread(HomeConnect::ouvrirNavigateurIntelligent, "open-browser").start();
        } else {
            LOGGER.info("🔗 Base primaire non connectée — ngrok ignoré, mode local.");
            final String localUrl = construireUrlLocale();
            LOGGER.info(() -> "🌐 Ouverture sur : " + localUrl);
            new Thread(() -> HomeConnect.ouvrirNavigateur(localUrl), "open-browser-local").start();
        }
    }

    // ============================================================
    // DÉTERMINATION DE L'INSTITUTION
    // ============================================================
    private static String determinerInstitutionId(String[] args) {
        String fromArgs = lireInstitutionDepuisArgs(args);
        if (fromArgs != null) {
            LOGGER.info(() -> "🏢 institutionId depuis argument : " + fromArgs);
            return fromArgs;
        }

        String fromEnv = lireEnv(ENV_INSTITUTION_ID);
        if (fromEnv != null) {
            LOGGER.info(() -> "🏢 institutionId depuis $" + ENV_INSTITUTION_ID + " : " + fromEnv);
            return fromEnv;
        }

        String fromEnv2 = lireEnv(ENV_MTECH_INSTITUTION);
        if (fromEnv2 != null) {
            LOGGER.info(() -> "🏢 institutionId depuis $" + ENV_MTECH_INSTITUTION + " : " + fromEnv2);
            return fromEnv2;
        }

        String fromProp = lireProprieteSysteme();
        if (fromProp != null) {
            LOGGER.info(() -> "🏢 institutionId depuis -D" + PROP_INSTITUTION_ID + " : " + fromProp);
            return fromProp;
        }

        String fromFile = lireInstitutionDepuisConfig();
        if (fromFile != null) {
            LOGGER.info(() -> "🏢 institutionId depuis " + CONFIG_FILE + " : " + fromFile);
            return fromFile;
        }

        String fromDb = lireInstitutionDepuisBd();
        if (fromDb != null) {
            LOGGER.info(() -> "🏢 institutionId depuis BD locale : " + fromDb);
            return fromDb;
        }

        throw new IllegalStateException(
                "Aucune institution trouvée. Sources testées : args CLI, $"
                + ENV_INSTITUTION_ID + ", $" + ENV_MTECH_INSTITUTION
                + ", -D" + PROP_INSTITUTION_ID + ", " + CONFIG_FILE + ", BD locale.");
    }

    private static String lireInstitutionDepuisArgs(String[] args) {
        if (args == null || args.length == 0) return null;

        for (String arg : args) {
            if (arg == null || arg.isBlank()) continue;

            if (arg.startsWith(ARG_INSTITUTION_PREFIX)) {
                String val = arg.substring(ARG_INSTITUTION_PREFIX.length()).trim();
                if (!val.isEmpty()) return val;
            } else if (!arg.startsWith("-")) {
                String val = arg.trim();
                if (!val.isEmpty()) return val;
            }
        }
        return null;
    }

    private static String lireEnv(String nom) {
        String val = System.getenv(nom);
        return (val == null || val.isBlank()) ? null : val.trim();
    }

    private static String lireProprieteSysteme() {
        String val = System.getProperty(PROP_INSTITUTION_ID);
        return (val == null || val.isBlank()) ? null : val.trim();
    }

    private static String lireInstitutionDepuisConfig() {
        // a) Classpath
        try (InputStream in = Home.class.getResourceAsStream("/" + CONFIG_FILE)) {
            if (in != null) {
                Properties p = new Properties();
                p.load(in);
                String v = p.getProperty(PROP_INSTITUTION_ID);
                if (v != null && !v.isBlank()) return v.trim();
            }
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "Lecture config (classpath) échouée", e);
        }

        // b) Répertoire courant
        Path p = Paths.get(CONFIG_FILE);
        if (Files.exists(p)) {
            Properties props = new Properties();
            try (InputStream in = Files.newInputStream(p)) {
                props.load(in);
                String v = props.getProperty(PROP_INSTITUTION_ID);
                if (v != null && !v.isBlank()) return v.trim();
            } catch (IOException e) {
                LOGGER.log(Level.FINE, "Lecture config (cwd) échouée", e);
            }
        }

        return null;
    }

    private static String lireInstitutionDepuisBd() {
        Properties cfg = new Properties();

        try (InputStream in = Home.class.getClassLoader()
                .getResourceAsStream(DB_CONFIG_FILE)) {
            if (in == null) {
                LOGGER.fine(() -> DB_CONFIG_FILE + " introuvable dans le classpath");
                return null;
            }
            cfg.load(in);
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "Chargement " + DB_CONFIG_FILE + " échoué", e);
            return null;
        }

        String host = cfg.getProperty(DB_SECONDARY_HOST);
        String port = cfg.getProperty(DB_SECONDARY_PORT);
        String db   = cfg.getProperty(DB_SECONDARY_DATABASE);
        String user = cfg.getProperty(DB_SECONDARY_USERNAME);
        String pass = cfg.getProperty(DB_SECONDARY_PASSWORD);

        if (host == null || port == null || db == null) {
            LOGGER.fine("Configuration secondaire incomplète (host/port/database)");
            return null;
        }

        String url = String.format(JDBC_URL_TEMPLATE, host, port, db);

        try (Connection conn = DriverManager.getConnection(url, user, pass);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(SQL_SELECT_INSTITUTION)) {

            if (rs.next()) {
                String instId = rs.getString(1);
                if (instId != null && !instId.isBlank()) {
                    return instId.trim();
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.FINE, "Lecture institution depuis BD échouée", e);
        }
        return null;
    }

    // ============================================================
    // URL LOCALE
    // ============================================================
  private static String construireUrlLocale() {
    String host = (HOSTNAME_LOCAL != null && !HOSTNAME_LOCAL.isBlank())
            ? HOSTNAME_LOCAL
            : "localhost";

    String pathPart = (PATH_DEFAUT == null || PATH_DEFAUT.isBlank())
            ? ""
            : (PATH_DEFAUT.startsWith("/") ? PATH_DEFAUT : "/" + PATH_DEFAUT);

    return "http://" + host + ":" + PORT_PRINCIPAL + pathPart;
}
    // ============================================================
    // NETTOYAGE DES SESSIONS
    // ============================================================
    private static void nettoyerSessionsJava() {
        final Path basePath = Paths.get(System.getProperty("user.dir"));
        final int[] compteur = {0};
        final int[] visites = {0};

        try {
            Files.walkFileTree(
                    basePath,
                    EnumSet.of(FileVisitOption.FOLLOW_LINKS),
                    NETTOYAGE_MAX_DEPTH,
                    new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                            if (visites[0]++ > NETTOYAGE_MAX_FILES) {
                                return FileVisitResult.TERMINATE;
                            }
                            String name = file.toString().toLowerCase();
                            for (String pattern : NETTOYAGE_PATTERNS) {
                                if (name.endsWith(pattern)) {
                                    try {
                                        Files.delete(file);
                                        compteur[0]++;
                                    } catch (IOException ignored) {
                                        // Fichier déjà supprimé ou verrouillé
                                    }
                                    break;
                                }
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });

            if (compteur[0] > 0) {
                final int n = compteur[0];
                LOGGER.fine(() -> "🧹 " + n + " fichiers de session supprimés.");
            }
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "Erreur nettoyage sessions", e);
        }
    }
}