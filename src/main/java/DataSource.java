import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Gère les pools HikariCP (PRIMARY = distant, SECONDARY = local).
 *
 * <p>Principes :
 * <ul>
 *   <li>Retrait de {@code autoReconnect} (déprécié MySQL, casse les transactions)</li>
 *   <li>{@code keepaliveTime < maxLifetime} (contrainte Hikari)</li>
 *   <li>Validation de connexion via {@code isValid()} JDBC 4 (pas de SELECT 1)</li>
 *   <li>Pas de {@code setConnectionTestQuery} ni {@code setDriverClassName}</li>
 *   <li>Tailles de pool distinctes : primaire (distant) vs secondaire (local)</li>
 *   <li>Logs enrichis, mots de passe masqués</li>
 *   <li>Toutes les lambdas capturent des variables finales ou effectivement finales</li>
 * </ul>
 */
public final class DataSource {

    private static final Logger LOGGER = Logger.getLogger(DataSource.class.getName());

    private static final int REMOTE_CONNECTION_TIMEOUT_SECONDS = 5;
    private static final int DEFAULT_SOCKET_TIMEOUT_MS = 120_000;
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 10_000;

    private final Properties config = new Properties();
    private final HikariDataSource primaryDataSource;
    private final HikariDataSource secondaryDataSource;
    private final String primaryUrl;
    private final String secondaryUrl;
    private final boolean primaryConfigured;
    private final boolean secondaryConfigured;

    // ============================================================
    // CONSTRUCTEUR
    // ============================================================
    public DataSource() {
        loadConfiguration();

        // ----- PRIMAIRE (distant) -----
        boolean primaryEnabled = Boolean.parseBoolean(
                config.getProperty("db.primary.enabled", "true"));
        boolean primaryComplete = hasRequiredProperties("db.primary");
        this.primaryConfigured = primaryEnabled && primaryComplete;

        HikariDataSource primaryTemp = null;
        String primaryUrlTemp = null;

        if (primaryConfigured) {
            primaryUrlTemp = buildJdbcUrl("db.primary");
            final String primaryUrlFinal = primaryUrlTemp;

            try {
                primaryTemp = createPool(
                        primaryUrlFinal,
                        config.getProperty("db.primary.username"),
                        config.getProperty("db.primary.password"),
                        "HikariPrimary",
                        10_000L,
                        "primary");
                LOGGER.info(() -> "✅ Pool primaire (distant) configuré ["
                        + masquerMotDePasse(primaryUrlFinal) + "]");
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "⚠️ Échec création pool primaire", e);
                primaryTemp = null;
            }
        } else {
            LOGGER.warning("⚠️ Base primaire non configurée — pool non créé.");
        }
        this.primaryUrl = primaryUrlTemp;
        this.primaryDataSource = primaryTemp;

        // ----- SECONDAIRE (local) -----
        boolean secondaryEnabled = Boolean.parseBoolean(
                config.getProperty("db.secondary.enabled", "true"));
        boolean secondaryComplete = hasRequiredProperties("db.secondary");
        this.secondaryConfigured = secondaryEnabled && secondaryComplete;

        if (secondaryConfigured) {
            String secondaryUrlTemp = buildJdbcUrl("db.secondary");
            this.secondaryUrl = secondaryUrlTemp;
            final String secondaryUrlFinal = secondaryUrlTemp;

            HikariDataSource secondaryTemp = null;
            try {
                secondaryTemp = createPool(
                        secondaryUrlFinal,
                        config.getProperty("db.secondary.username"),
                        config.getProperty("db.secondary.password"),
                        "HikariSecondary",
                        30_000L,
                        "secondary");
                LOGGER.info(() -> "✅ Pool secondaire (local) initialisé ["
                        + masquerMotDePasse(secondaryUrlFinal) + "]");
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "⚠️ Échec création pool secondaire", e);
            }
            this.secondaryDataSource = secondaryTemp;
        } else {
            this.secondaryUrl = null;
            this.secondaryDataSource = null;
            if (!secondaryEnabled) {
                LOGGER.info("ℹ️ Base secondaire désactivée (db.secondary.enabled=false)");
            } else {
                LOGGER.warning("⚠️ Base secondaire non configurée — pool non créé.");
            }
        }

        if (!primaryConfigured && !secondaryConfigured) {
            LOGGER.severe("❌ AUCUNE base configurée (ni primaire, ni secondaire). "
                    + "L'application ne pourra pas fonctionner.");
        }
    }

    // ============================================================
    // CONFIGURATION
    // ============================================================
    private void loadConfiguration() {
        String configFile = "database.properties";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(configFile)) {
            if (input == null) {
                throw new RuntimeException("Fichier '" + configFile + "' introuvable.");
            }
            config.load(input);
            LOGGER.info(() -> "✅ Configuration chargée depuis " + configFile);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "❌ Erreur chargement configuration", e);
            throw new RuntimeException(e);
        }
    }

    private boolean hasRequiredProperties(String prefix) {
        String[] required = {
            prefix + ".host",
            prefix + ".port",
            prefix + ".database",
            prefix + ".username",
            prefix + ".password"
        };
        for (String prop : required) {
            String value = config.getProperty(prop);
            if (value == null || value.isBlank()) {
                final String propFinal = prop;
                LOGGER.fine(() -> "Propriété manquante : " + propFinal);
                return false;
            }
        }
        return true;
    }

    private String buildJdbcUrl(String prefix) {
        String host = config.getProperty(prefix + ".host");
        String port = config.getProperty(prefix + ".port");
        String database = config.getProperty(prefix + ".database");
        if (host == null || port == null || database == null) {
            throw new RuntimeException("Hôte/port/base manquants pour " + prefix);
        }

        String useSSL = config.getProperty(prefix + ".useSSL", "false");
        String serverTimezone = config.getProperty(prefix + ".serverTimezone", "UTC");
        String allowPublicKeyRetrieval = config.getProperty(
                prefix + ".allowPublicKeyRetrieval", "true");

        int connectTimeout = Integer.parseInt(config.getProperty(
                prefix + ".connectTimeoutMs", String.valueOf(DEFAULT_CONNECT_TIMEOUT_MS)));
        int socketTimeout = Integer.parseInt(config.getProperty(
                prefix + ".socketTimeoutMs", String.valueOf(DEFAULT_SOCKET_TIMEOUT_MS)));

        StringBuilder url = new StringBuilder("jdbc:mysql://");
        url.append(host).append(":").append(port).append("/").append(database);
        url.append("?useSSL=").append(useSSL);
        url.append("&serverTimezone=").append(serverTimezone);
        url.append("&allowPublicKeyRetrieval=").append(allowPublicKeyRetrieval);
        // autoReconnect volontairement retiré : déprécié, casse les transactions
        url.append("&connectTimeout=").append(connectTimeout);
        url.append("&socketTimeout=").append(socketTimeout);
        url.append("&useUnicode=true&characterEncoding=UTF-8");

        String sslMode = config.getProperty(prefix + ".sslMode");
        if (sslMode != null) {
            url.append("&sslMode=").append(sslMode);
        }

        final String prefixFinal = prefix;
        final int connectTimeoutFinal = connectTimeout;
        final int socketTimeoutFinal = socketTimeout;
        LOGGER.info(() -> "🔗 URL JDBC [" + prefixFinal + "] : connectTimeout="
                + connectTimeoutFinal + "ms, socketTimeout=" + socketTimeoutFinal + "ms");

        return url.toString();
    }

    /**
     * Crée un pool HikariCP.
     *
     * @param poolTag "primary" ou "secondary" → pour les propriétés de taille distinctes
     */
    private HikariDataSource createPool(String url, String user, String password,
                                          String poolName, long connectionTimeout,
                                          String poolTag) {
        HikariConfig poolConfig = new HikariConfig();

        // Pas de setDriverClassName : auto-détection via SPI (JDBC 4)
        poolConfig.setJdbcUrl(url);
        poolConfig.setUsername(user);
        poolConfig.setPassword(password);

        // Mode dégradé : l'app démarre même si la BD est down
        poolConfig.setInitializationFailTimeout(-1);
        poolConfig.setPoolName(poolName);

        // Tailles distinctes par type de pool
        int defaultMax = "primary".equals(poolTag) ? 5 : 10;
        int defaultMinIdle = "primary".equals(poolTag) ? 1 : 2;

        int maxSize = Integer.parseInt(config.getProperty(
                "pool." + poolTag + ".maxSize",
                config.getProperty("pool.maxSize", String.valueOf(defaultMax))));

        int minIdleLu = Integer.parseInt(config.getProperty(
                "pool." + poolTag + ".minIdle",
                config.getProperty("pool.minIdle", String.valueOf(defaultMinIdle))));

        // Ajustement : minIdle ne peut dépasser maxSize
        int minIdleEffectif = minIdleLu;
        if (minIdleEffectif > maxSize) {
            final int minIdleAvant = minIdleEffectif;
            final int maxSizeWarn = maxSize;
            final String poolNameWarn = poolName;
            LOGGER.warning(() -> "⚠️ minIdle (" + minIdleAvant + ") > maxSize ("
                    + maxSizeWarn + ") pour " + poolNameWarn
                    + " → ajustement à " + maxSizeWarn);
            minIdleEffectif = maxSize;
        }

        poolConfig.setMaximumPoolSize(maxSize);
        poolConfig.setMinimumIdle(minIdleEffectif);
        poolConfig.setConnectionTimeout(connectionTimeout);
        poolConfig.setValidationTimeout(5_000);

        long maxLifetime = 1_500_000L;   // 25 min
        long keepaliveTime = 120_000L;   // 2 min
        poolConfig.setIdleTimeout(600_000L);
        poolConfig.setMaxLifetime(maxLifetime);
        // Contrainte Hikari : keepaliveTime < maxLifetime
        poolConfig.setKeepaliveTime(Math.min(keepaliveTime, maxLifetime - 30_000L));

        // Pas de setConnectionTestQuery : HikariCP utilise isValid() (JDBC 4)

        // Propriétés MySQL (performance)
        poolConfig.addDataSourceProperty("cachePrepStmts", "true");
        poolConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        poolConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        poolConfig.addDataSourceProperty("useServerPrepStmts", "true");
        poolConfig.addDataSourceProperty("useLocalSessionState", "true");
        poolConfig.addDataSourceProperty("rewriteBatchedStatements", "true");
        poolConfig.addDataSourceProperty("cacheResultSetMetadata", "true");
        poolConfig.addDataSourceProperty("cacheServerConfiguration", "true");
        poolConfig.addDataSourceProperty("elideSetAutoCommits", "true");
        poolConfig.addDataSourceProperty("maintainTimeStats", "false");
        poolConfig.addDataSourceProperty("tcpKeepAlive", "true");

        HikariDataSource ds = new HikariDataSource(poolConfig);

        final String poolNameLog = poolName;
        final int maxSizeLog = maxSize;
        final int minIdleLog = minIdleEffectif;
        final long timeoutLog = connectionTimeout;
        LOGGER.info(() -> "🔧 Pool " + poolNameLog + " créé (max=" + maxSizeLog
                + ", minIdle=" + minIdleLog + ", timeout=" + timeoutLog + "ms)");

        return ds;
    }

    // ============================================================
    // ACCESSEURS
    // ============================================================
    public boolean isPrimaryConfigured()   { return primaryConfigured; }
    public boolean isSecondaryConfigured() { return secondaryConfigured; }
    public String getPrimaryUrl()          { return primaryUrl; }
    public String getSecondaryUrl()        { return secondaryUrl; }

    public Connection getPrimaryConnection() throws SQLException {
        if (!primaryConfigured || primaryDataSource == null) {
            throw new SQLException("Base primaire (distante) non configurée.");
        }
        if (primaryDataSource.isClosed()) {
            throw new SQLException("Pool primaire fermé.");
        }
        return primaryDataSource.getConnection();
    }

    public Connection getSecondaryConnection() throws SQLException {
        if (!secondaryConfigured || secondaryDataSource == null) {
            throw new SQLException("Base secondaire (locale) non configurée.");
        }
        if (secondaryDataSource.isClosed()) {
            throw new SQLException("Pool secondaire fermé.");
        }
        return secondaryDataSource.getConnection();
    }

    // ============================================================
    // HEALTH CHECK
    // ============================================================
    public boolean checkHealth(boolean isPrimary) {
        if (isPrimary && !primaryConfigured) return false;
        if (!isPrimary && !secondaryConfigured) return false;

        HikariDataSource ds = isPrimary ? primaryDataSource : secondaryDataSource;
        if (ds == null || ds.isClosed()) return false;

        try (Connection conn = ds.getConnection()) {
            return conn.isValid(3);
        } catch (SQLException e) {
            final boolean primaryFlag = isPrimary;
            LOGGER.fine(() -> "Health check échoué pour "
                    + (primaryFlag ? "primaire (distant)" : "secondaire (local)")
                    + " : " + e.getMessage());
            return false;
        }
    }

    public boolean testPrimaryConnectionWithTimeout() {
        if (primaryDataSource == null || primaryDataSource.isClosed()) return false;

        final int timeoutSeconds = REMOTE_CONNECTION_TIMEOUT_SECONDS;
        LOGGER.info(() -> "🔍 Test de connexion primaire ("
                + timeoutSeconds + "s max)...");

        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "primary-conn-test");
            t.setDaemon(true);
            return t;
        });

        Future<Boolean> future = executor.submit(() -> {
            try (Connection conn = primaryDataSource.getConnection()) {
                return conn.isValid(3);
            } catch (SQLException e) {
                return false;
            }
        });

        try {
            Boolean result = future.get(REMOTE_CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (Boolean.TRUE.equals(result)) {
                LOGGER.info("✅ Connexion primaire établie.");
                return true;
            }
            return false;
        } catch (TimeoutException e) {
            future.cancel(false);
            LOGGER.warning("⏱️ Test connexion primaire trop long");
            return false;
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.fine("Erreur test connexion primaire");
            return false;
        } finally {
            executor.shutdown();
        }
    }

    // ============================================================
    // STATS
    // ============================================================
    public String getPrimaryPoolStats()   { return poolStats(primaryDataSource, "Primaire"); }
    public String getSecondaryPoolStats() { return poolStats(secondaryDataSource, "Secondaire"); }

    private String poolStats(HikariDataSource ds, String name) {
        if (ds == null) return "Pool " + name.toLowerCase() + " non initialisé";
        if (ds.isClosed()) return "Pool " + name.toLowerCase() + " fermé";

        try {
            if (ds.getHikariPoolMXBean() == null) {
                return "Pool " + name.toLowerCase() + " - MXBean indisponible";
            }
            return String.format("%s - Actives: %d, Idle: %d, Total: %d, En attente: %d",
                name,
                ds.getHikariPoolMXBean().getActiveConnections(),
                ds.getHikariPoolMXBean().getIdleConnections(),
                ds.getHikariPoolMXBean().getTotalConnections(),
                ds.getHikariPoolMXBean().getThreadsAwaitingConnection());
        } catch (Exception e) {
            return "Pool " + name.toLowerCase() + " - stats indisponibles";
        }
    }

    // ============================================================
    // FERMETURE
    // ============================================================
    public void closeAll() {
        closeDataSource(primaryDataSource, "primaire (distant)");
        closeDataSource(secondaryDataSource, "secondaire (local)");
    }

    private void closeDataSource(HikariDataSource ds, String name) {
        if (ds != null && !ds.isClosed()) {
            try {
                ds.close();
                final String nameFinal = name;
                LOGGER.info(() -> "✅ Pool " + nameFinal + " fermé");
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "⚠️ Erreur fermeture pool " + name, e);
            }
        }
    }

    // ============================================================
    // UTILITAIRES
    // ============================================================
    public Properties getConfig() { return config; }

    /** Masque le mot de passe d'une URL JDBC avant log. */
    private static String masquerMotDePasse(String url) {
        if (url == null) return "";
        return url.replaceAll("password=[^&]*", "password=***");
    }
}