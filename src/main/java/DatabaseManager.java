import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;


public final class DatabaseManager {

    private static final Logger LOGGER = Logger.getLogger(DatabaseManager.class.getName());
    private final CountDownLatch basesSainesLatch = new CountDownLatch(1);
    private final CompletableFuture<Void> basesSainesFuture = new CompletableFuture<>();
    // ============================================================
    // CONFIGURATION
    // ============================================================
    private static final int  SYNC_STARTUP_DELAY_SECONDS         = 5;
    private static final int  SYNC_INITIAL_DELAY_SECONDS         = 10;
    private static final int  SYNC_PERIOD_SECONDS                = 5;
    private static final long SYNC_DEBOUNCE_MS                   = 2_000L;
    private static final int  HEALTH_CHECK_INTERVAL_SECONDS      = 30;
    private static final int  HEALTH_CHECK_FAILURES_BEFORE_RETRY = 5;
    private static final int  MAX_BATCH_DEFERRED                 = 500;
    private static final int  MAX_DEFERRED_QUEUE_SIZE            = 10_000;
    private static final long RESYNC_MIN_INTERVAL_MS             = 30_000L;

    // Watchdog
    private static final long WATCHDOG_INTERVAL_SECONDS          = 60;
    private static final long SYNC_STALE_THRESHOLD_MS            = 120_000L;   // 2 min
    private static final long SYNC_FLAG_STUCK_THRESHOLD_MS       = 300_000L;   // 5 min
    private static final long AUDIT_INTERVAL_SECONDS             = 300;        // 5 min
    private static final int  AUDIT_DIVERGENCE_THRESHOLD         = 0;

    // Outbox
    private static final int  OUTBOX_INTERVAL_SECONDS            = 30;

    // Sync au démarrage
    private static final long SYNC_STARTUP_MAX_WAIT_MS           = 60_000L;    // 60 s

    // ============================================================
    // DÉPENDANCES
    // ============================================================
    private final DataSource dataSource;
    private final SyncService syncService;
    private final SyncOutboxService syncOutboxService;

    /** Institution de cette instance (obligatoire, immuable). */
    private final String institutionId;

    // ============================================================
    // ÉTAT INTERNE
    // ============================================================
    private final BlockingQueue<Set<String>> syncRequests = new LinkedBlockingQueue<>();
    private final AtomicBoolean syncEnCours = new AtomicBoolean(false);
    private final AtomicBoolean resyncEnCours = new AtomicBoolean(false);
    private volatile long dernierResyncMs = 0L;
    private volatile long derniereSyncReussieMs = 0L;

    private final ScheduledExecutorService syncExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "SyncService-" + safeName());
        t.setDaemon(true);
        return t;
    });

    private final ExecutorService syncTriggerExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SyncTrigger-" + safeName());
        t.setDaemon(true);
        return t;
    });

    private final ScheduledExecutorService healthCheckExecutor = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "DatabaseHealthCheck-" + safeName());
        t.setDaemon(true);
        return t;
    });

    public enum FailoverStrategy {
        REMOTE_PRIORITY, LOCAL_PRIORITY, BEST_AVAILABLE, HYBRID
    }

    private final Queue<DeferredOperation> deferredOperations = new ConcurrentLinkedQueue<>();

    private volatile boolean migrationExecuted         = false;
    private volatile boolean migrationDistanteTerminee = false;
    private volatile boolean isLocalHealthy            = false;
    private volatile boolean isRemoteHealthy           = false;
    private volatile FailoverStrategy strategy         = FailoverStrategy.REMOTE_PRIORITY;

    private volatile int primaryHealthCheckFailures   = 0;
    private volatile int secondaryHealthCheckFailures = 0;

    private volatile long dernierSyncImmediateMs = 0L;

    // ============================================================
    // CLASSE INTERNE
    // ============================================================
    private static class DeferredOperation {
        final String sql;
        final Object[] params;
        final long timestamp;

        DeferredOperation(String sql, Object[] params) {
            this.sql = sql;
            this.params = params;
            this.timestamp = System.currentTimeMillis();
        }
    }

    // ============================================================
    // HOLDER — Initialisation explicite obligatoire
    // ============================================================
    private static class Holder {
        private static volatile DatabaseManager INSTANCE;
        private static volatile String institutionId;
    }

    /**
     * Initialise le singleton avec l'institutionId.
     * À appeler UNE SEULE FOIS au démarrage de l'application.
     */
    public static synchronized void initialiser(String institutionId) {
        if (institutionId == null || institutionId.isBlank()) {
            throw new IllegalArgumentException(
                "DatabaseManager.initialiser : institutionId obligatoire");
        }
        String trimmed = institutionId.trim();

        if (Holder.INSTANCE != null) {
            if (!Holder.institutionId.equals(trimmed)) {
                throw new IllegalStateException(
                    "DatabaseManager déjà initialisé pour " + Holder.institutionId
                    + " — impossible de changer pour " + trimmed);
            }
            return; // idempotent
        }

        Holder.institutionId = trimmed;
        Holder.INSTANCE = new DatabaseManager(trimmed);
    }

    /**
     * Récupère l'instance. Échoue si non initialisée.
     */
    public static DatabaseManager getInstance() {
        DatabaseManager instance = Holder.INSTANCE;
        if (instance == null) {
            throw new IllegalStateException(
                "DatabaseManager non initialisé. Appelle DatabaseManager.initialiser(institutionId).");
        }
        return instance;
    }

    /**
     * Raccourci : initialise (si nécessaire) puis retourne l'instance.
     */
    public static DatabaseManager getInstance(String institutionId) {
        initialiser(institutionId);
        return getInstance();
    }

    /**
     * Indique si le singleton a déjà été initialisé.
     */
    public static boolean isInitialise() {
        return Holder.INSTANCE != null;
    }

    // ============================================================
    // CONSTRUCTEUR PRIVÉ
    // ============================================================
    private DatabaseManager(String institutionId) {
        this.institutionId = institutionId;

        LOGGER.info(() -> "🚀 Initialisation DatabaseManager pour institution="
                + this.institutionId + "...");

        this.dataSource = new DataSource();

        // Les deux services reçoivent l'institutionId → filtrage strict
        this.syncService       = new SyncService(this, this.institutionId);
        this.syncOutboxService = new SyncOutboxService(this, this.institutionId);

        // Stratégie
        Properties config = dataSource.getConfig();
        String strategyName = config.getProperty("db.strategy", "LOCAL_PRIORITY");
        try {
            this.strategy = FailoverStrategy.valueOf(strategyName.toUpperCase());
            LOGGER.info(() -> "📋 Stratégie: " + this.strategy);
        } catch (IllegalArgumentException e) {
            this.strategy = FailoverStrategy.LOCAL_PRIORITY;
        }

        // Health initial
        if (dataSource.isSecondaryConfigured()) {
            boolean localOk = dataSource.checkHealth(false);
            setSecondaryHealthy(localOk);
            LOGGER.info(localOk ? "✅ Connexion locale vérifiée"
                                : "⚠️ Connexion locale INDISPONIBLE");
        } else {
            setSecondaryHealthy(false);
        }

        setPrimaryHealthy(false);
        if (dataSource.isPrimaryConfigured()) {
            setPrimaryHealthy(dataSource.testPrimaryConnectionWithTimeout());
        }

        // Démarrages
        startHealthCheckService();
        startSyncService();
        startWatchdog();
        startAuditPeriodique();

        healthCheckExecutor.submit(this::executeMigrationAtStartup);
        healthCheckExecutor.submit(this::verifierEtatSyncAuDemarrage);
        addShutdownHook();

        LOGGER.info(() -> "✅ DatabaseManager prêt pour institution=" + this.institutionId);
    }

    // ============================================================
    // ACCESSEURS
    // ============================================================
    public String getInstitutionId()                 { return institutionId; }
    public DataSource getDataSource()                { return dataSource; }
    public SyncService getSyncService()              { return syncService; }
    public SyncOutboxService getSyncOutboxService()  { return syncOutboxService; }

    private boolean isPrimaryHealthy()           { return isRemoteHealthy; }
    private void setPrimaryHealthy(boolean h)    { this.isRemoteHealthy = h; }
    private boolean isSecondaryHealthy()         { return isLocalHealthy; }
    private void setSecondaryHealthy(boolean h)  { this.isLocalHealthy = h; }

    private String safeName() {
        return institutionId == null ? "unknown" : institutionId;
    }

    // ============================================================
    // HEALTH CHECK
    // ============================================================
    private void startHealthCheckService() {
        healthCheckExecutor.scheduleAtFixedRate(
            this::updateHealthStatus,
            HEALTH_CHECK_INTERVAL_SECONDS,
            HEALTH_CHECK_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        );
        LOGGER.info(() -> "🏥 Health check démarré (" + HEALTH_CHECK_INTERVAL_SECONDS + " s)");
    }

   private synchronized void updateHealthStatus() {
    boolean secondaryHealth = dataSource.isSecondaryConfigured() && dataSource.checkHealth(false);

    boolean primaryHealth;
    if (!dataSource.isPrimaryConfigured()) {
        primaryHealth = false;
    } else if (isPrimaryHealthy() || primaryHealthCheckFailures >= HEALTH_CHECK_FAILURES_BEFORE_RETRY) {
        primaryHealth = dataSource.checkHealth(true);
        primaryHealthCheckFailures = primaryHealth ? 0 : primaryHealthCheckFailures + 1;
    } else {
        primaryHealth = isPrimaryHealthy();
        primaryHealthCheckFailures++;
    }

    secondaryHealthCheckFailures = secondaryHealth ? 0 : secondaryHealthCheckFailures + 1;

    boolean secondaryChanged = (secondaryHealth != isSecondaryHealthy());
    boolean primaryChanged   = (primaryHealth   != isPrimaryHealthy());

    setSecondaryHealthy(secondaryHealth);
    setPrimaryHealthy(primaryHealth);

    if (secondaryChanged) {
        LOGGER.info(() -> (secondaryHealth ? "✅" : "❌")
                + " Secondaire (local) [" + institutionId + "]: "
                + (secondaryHealth ? "SAIN" : "DÉFAILLANT"));
    }
    if (primaryChanged) {
        LOGGER.info(() -> (primaryHealth ? "✅" : "❌")
                + " Primaire (distant) [" + institutionId + "]: "
                + (primaryHealth ? "SAIN" : "DÉFAILLANT"));
    }

    // ✅ Complète le future dès que les DEUX bases sont saines (idempotent)
    if (isLocalHealthy() && isRemoteHealthy()) {
        basesSainesFuture.complete(null);
    }

    // Sync immédiate dès que la santé redevient OK
    boolean toutSain = isLocalHealthy() && isRemoteHealthy();
    if (toutSain && (primaryChanged || secondaryChanged)) {
        LOGGER.info(() -> "🔄 Santé rétablie [" + institutionId
                + "] — sync immédiate");
        declencherSyncImmediateAsync();
    }

    if (primaryHealth && isSecondaryHealthy() && primaryChanged) {
        tenterResyncComplete();
    }

    if (primaryHealth && !deferredOperations.isEmpty()) {
        syncDeferredOperations();
    }
}
    private void tenterResyncComplete() {
        long now = System.currentTimeMillis();
        if (now - dernierResyncMs < RESYNC_MIN_INTERVAL_MS) return;
        if (!resyncEnCours.compareAndSet(false, true)) return;

        dernierResyncMs = now;
        try {
            LOGGER.info(() -> "🔄 Resync complète déclenchée [" + institutionId + "]...");
            declencherSyncImmediateAsync();
        } finally {
            resyncEnCours.set(false);
        }
    }

    // ============================================================
    // SYNC SERVICE
    // ============================================================
    private void startSyncService() {
        // 1) ✅ Sync garantie au démarrage (attend les bases saines)
        syncExecutor.schedule(this::syncAuDemarrage,
                SYNC_STARTUP_DELAY_SECONDS, TimeUnit.SECONDS);

        // 2) Sync périodique (toutes les 5s)
        syncExecutor.scheduleAtFixedRate(() -> {
            if (!isPrimaryHealthy() || !isSecondaryHealthy()) return;
            declencherSyncImmediateAsync();
        }, SYNC_INITIAL_DELAY_SECONDS, SYNC_PERIOD_SECONDS, TimeUnit.SECONDS);

        // 3) Rejeu outbox (toutes les 30s)
        syncExecutor.scheduleAtFixedRate(() -> {
            if (!isPrimaryHealthy() || !isSecondaryHealthy()) return;
            try {
                syncOutboxService.rejouerOutbox();
            } catch (RuntimeException e) {
                LOGGER.log(Level.FINE, "Erreur rejeu outbox", e);
            }
        }, OUTBOX_INTERVAL_SECONDS, OUTBOX_INTERVAL_SECONDS, TimeUnit.SECONDS);

        LOGGER.info(() -> "🔄 Sync scheduler démarré [" + institutionId
                + "] — périodique " + SYNC_PERIOD_SECONDS + "s, outbox "
                + OUTBOX_INTERVAL_SECONDS + "s");
    }

    /**
     * ✅ Sync garantie au démarrage :
     *    attend que les DEUX bases soient saines (jusqu'à 60s),
     *    puis force une resync + sync immédiate.
     *
     *    Bloque le thread "SyncService" — acceptable car la sync périodique
     *    ne pourrait rien faire de toute façon.
     */
  private void syncAuDemarrage() {
    LOGGER.info(() -> "🚀 [SyncStartup] Attente bases saines ["
            + institutionId + "]...");

    try {
        // Cas rapide : bases déjà saines
        boolean localOk  = isLocalConfigured()  && isLocalHealthy();
        boolean remoteOk = isRemoteConfigured() && isRemoteHealthy();

        if (!(localOk && remoteOk)) {
            // ✅ Attente réactive : réveil dès que le latch est libéré
            boolean saines = basesSainesLatch.await(
                    SYNC_STARTUP_MAX_WAIT_MS, TimeUnit.MILLISECONDS);

            if (!saines) {
                LOGGER.warning(() -> "⏱️ [SyncStartup] Timeout attente bases saines ["
                        + institutionId + "] — la sync périodique prendra le relais");
                return;
            }
        }

        LOGGER.info(() -> "✅ [SyncStartup] Bases saines — sync forcée ["
                + institutionId + "]");

        // Force la resync (ignore l'intervalle minimum)
        forcerResyncAuDemarrage();
        // Puis sync immédiate
        declencherSyncImmediateAsync();

    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        LOGGER.warning("[SyncStartup] Interrompu");
    }
}

    /**
     * ✅ Force une resync en ignorant {@code RESYNC_MIN_INTERVAL_MS}.
     *    Utilisé UNIQUEMENT au démarrage.
     */
    private void forcerResyncAuDemarrage() {
        if (!dataSource.isSecondaryConfigured() || !dataSource.isPrimaryConfigured()) return;
        if (!isSecondaryHealthy() || !isPrimaryHealthy()) return;

        if (!resyncEnCours.compareAndSet(false, true)) return;

        try {
            LOGGER.info(() -> "🔄 [Resync Startup] Complète [" + institutionId + "]...");
            declencherSyncImmediateAsync();
        } finally {
            resyncEnCours.set(false);
        }
    }

   
    // ============================================================
    // DÉCLENCHEMENT D'UNE SYNC
    // ============================================================
    public void declencherSyncImmediateAsync() {
        declencherSyncImmediateAsync(null);
    }

    public void declencherSyncImmediateAsync(Set<String> tables) {
        long now = System.currentTimeMillis();
        Set<String> demande = (tables != null) ? tables : new HashSet<>();

        if (now - dernierSyncImmediateMs < SYNC_DEBOUNCE_MS) {
            syncRequests.offer(demande);
            return;
        }
        dernierSyncImmediateMs = now;
        syncRequests.offer(demande);
        traiterFileSync();
    }

    private void traiterFileSync() {
        syncTriggerExecutor.submit(() -> {
            Set<String> tables = syncRequests.poll();
            if (tables == null) return;

            if (!syncEnCours.compareAndSet(false, true)) {
                syncRequests.offer(tables);
                return;
            }
            try {
                if (isSecondaryHealthy() && isPrimaryHealthy()) {
                    // Le SyncService applique déjà le filtre par institution
                    syncService.synchroniser(tables.isEmpty() ? null : tables);
                }
            } catch (SQLException e) {
                LOGGER.log(Level.FINE, "Sync async échouée", e);
            } catch (RuntimeException e) {
                LOGGER.log(Level.WARNING, "Sync async erreur inattendue", e);
            } finally {
                syncEnCours.set(false);
                if (!syncRequests.isEmpty()) traiterFileSync();
            }
        });
    }

    // ============================================================
    // WATCHDOG & AUTO-RÉPARATION
    // ============================================================
    private void startWatchdog() {
        healthCheckExecutor.scheduleAtFixedRate(() -> {
            try {
                long depuisDerniereSync = System.currentTimeMillis() - derniereSyncReussieMs;

                if (derniereSyncReussieMs > 0
                        && depuisDerniereSync > SYNC_STALE_THRESHOLD_MS) {
                    LOGGER.warning(String.format(
                        "🐕 Watchdog [%s] : aucune sync depuis %d s — relance forcée",
                        institutionId, depuisDerniereSync / 1000));
                    if (isLocalHealthy() && isRemoteHealthy()) {
                        declencherSyncImmediateAsync();
                    }
                }

                if (syncEnCours.get()
                        && (System.currentTimeMillis() - derniereSyncReussieMs)
                            > SYNC_FLAG_STUCK_THRESHOLD_MS) {
                    LOGGER.severe(() -> "🐕 Watchdog [" + institutionId
                            + "] : syncEnCours bloqué — reset forcé");
                    forcerResetSyncFlag();
                }

            } catch (RuntimeException e) {
                LOGGER.log(Level.SEVERE, "🐕 Watchdog erreur", e);
            }
        }, WATCHDOG_INTERVAL_SECONDS, WATCHDOG_INTERVAL_SECONDS, TimeUnit.SECONDS);

        LOGGER.info(() -> "🐕 Watchdog démarré [" + institutionId
                + "] (" + WATCHDOG_INTERVAL_SECONDS + " s)");
    }

    private void forcerResetSyncFlag() {
        syncEnCours.set(false);
        syncRequests.clear();
        syncRequests.offer(new HashSet<>());
        LOGGER.warning(() -> "🔓 syncEnCours réinitialisé par le watchdog [" + institutionId + "]");
    }

    public void signalerSyncReussie() {
        this.derniereSyncReussieMs = System.currentTimeMillis();
    }

    public long getDerniereSyncReussieMs() {
        return derniereSyncReussieMs;
    }

    public boolean isSyncEnCours() {
        return syncEnCours.get();
    }

    // ============================================================
    // AUTO-RÉPARATION AU DÉMARRAGE
    // ============================================================
    private void verifierEtatSyncAuDemarrage() {
        try (Connection conn = getLocalConnection()) {
            String sql = "SELECT last_success, last_error FROM sync_heartbeat WHERE id = 1";
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {
                if (rs.next()) {
                    Timestamp lastSuccess = rs.getTimestamp("last_success");
                    String lastError = rs.getString("last_error");

                    if (lastSuccess == null) {
                        LOGGER.warning(() -> "⚠️ Aucune sync réussie enregistrée — sync forcée ["
                                + institutionId + "]");
                    } else {
                        long ecartMs = System.currentTimeMillis() - lastSuccess.getTime();
                        long ecartMin = ecartMs / 60_000;
                        if (ecartMin > 5) {
                            LOGGER.warning(String.format(
                                "⚠️ Dernière sync il y a %d min — sync forcée [%s]",
                                ecartMin, institutionId));
                        } else {
                            LOGGER.fine(() -> "✅ Dernière sync il y a "
                                    + (ecartMs / 1000) + " s [" + institutionId + "]");
                        }
                    }

                    if (lastError != null && !lastError.isBlank()) {
                        LOGGER.warning(() -> "⚠️ Dernière erreur sync : " + lastError
                                + " [" + institutionId + "]");
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.FINE, "Impossible de lire sync_heartbeat", e);
        }

        // ✅ Forcer une sync à CHAQUE démarrage
        LOGGER.info(() -> "🔄 Sync forcée au démarrage [" + institutionId + "]");
        declencherSyncImmediateAsync();
    }

    // ============================================================
    // AUDIT DE COHÉRENCE — SCOPÉ PAR INSTITUTION
    // ============================================================
    private void startAuditPeriodique() {
        healthCheckExecutor.scheduleAtFixedRate(() -> {
            try {
                if (!isLocalHealthy() || !isRemoteHealthy()) return;
                auditerCoherence();
            } catch (RuntimeException e) {
                LOGGER.log(Level.FINE, "Audit cohérence erreur", e);
            }
        }, AUDIT_INTERVAL_SECONDS, AUDIT_INTERVAL_SECONDS, TimeUnit.SECONDS);

        LOGGER.info(() -> "🔍 Audit cohérence démarré [" + institutionId
                + "] (" + AUDIT_INTERVAL_SECONDS + " s)");
    }

    private void auditerCoherence() {
        Connection local = null;
        Connection remote = null;
        try {
            local = getLocalConnection();
            remote = getRemoteConnection();

            int divergences = 0;

            for (String table : SyncService.TABLE_ORDER) {
                try {
                    final String where = construireWhereInstitution(table);

                    long countLocal  = compterLignes(local, table, where);
                    long countRemote = compterLignes(remote, table, where);

                    if (Math.abs(countLocal - countRemote) > AUDIT_DIVERGENCE_THRESHOLD) {
                        LOGGER.warning(String.format(
                            "⚠️ Divergence %s [%s] : LOCAL=%d, REMOTE=%d (écart=%d)",
                            table, institutionId,
                            countLocal, countRemote, countLocal - countRemote));
                        divergences++;
                    }
                } catch (SQLException e) {
                    LOGGER.fine(() -> "Audit " + table + " échoué : " + e.getMessage());
                }
            }

            if (divergences > 0) {
                LOGGER.warning(String.format(
                    "⚠️ %d table(s) divergente(s) pour %s — sync forcée",
                    divergences, institutionId));
                declencherSyncImmediateAsync();
            } else {
                LOGGER.fine(() -> "✅ Audit cohérence OK pour " + institutionId);
            }

        } catch (SQLException e) {
            LOGGER.fine(() -> "Audit cohérence impossible : " + e.getMessage());
        } finally {
            closeQuietly(local);
            closeQuietly(remote);
        }
    }

    private String construireWhereInstitution(String table) {
        if (MigrationManager.TABLE_INSTITUTIONS.equals(table)) {
            return "";
        }
        return " WHERE institution_id = '" + escapeSql(institutionId) + "'";
    }

    private long compterLignes(Connection conn, String table, String where)
            throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + escapeIdentifier(table) + where;
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    private String escapeSql(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("'", "''");
    }

    // ============================================================
    // CONNEXIONS
    // ============================================================
    public Connection getLocalConnection() throws SQLException {
        return dataSource.getSecondaryConnection();
    }

    public Connection getRemoteConnection() throws SQLException {
        return dataSource.getPrimaryConnection();
    }

    public Connection getConnection() throws SQLException {
        boolean preferRemote = (strategy == FailoverStrategy.REMOTE_PRIORITY);

        if (preferRemote) {
            Connection remoteConn = essayerConnexionDistant();
            if (remoteConn != null) return remoteConn;
            Connection localConn = essayerConnexionLocale();
            if (localConn != null) return localConn;
        } else {
            Connection localConn = essayerConnexionLocale();
            if (localConn != null) return localConn;
            Connection remoteConn = essayerConnexionDistant();
            if (remoteConn != null) return remoteConn;
        }
        throw new SQLException("Aucune base (ni locale, ni distante) accessible.");
    }

    private Connection essayerConnexionLocale() {
        if (dataSource.isSecondaryConfigured() && isSecondaryHealthy()) {
            try {
                Connection conn = dataSource.getSecondaryConnection();
                if (conn != null && !conn.isClosed() && conn.isValid(2)) return conn;
                if (conn != null) conn.close();
                setSecondaryHealthy(false);
            } catch (SQLException e) {
                setSecondaryHealthy(false);
            }
        }
        return null;
    }

    private Connection essayerConnexionDistant() {
        if (dataSource.isPrimaryConfigured() && isRemoteHealthy()) {
            try {
                Connection conn = dataSource.getPrimaryConnection();
                if (conn != null && !conn.isClosed() && conn.isValid(2)) return conn;
                if (conn != null) conn.close();
                setPrimaryHealthy(false);
            } catch (SQLException e) {
                setPrimaryHealthy(false);
            }
        }
        return null;
    }

    // ============================================================
    // MIGRATION
    // ============================================================
    private void executeMigrationAtStartup() {
        synchronized (this) {
            if (migrationExecuted) return;
            migrationExecuted = true;
        }

        if (dataSource.isSecondaryConfigured()) {
            Thread t = new Thread(this::executerMigrationLocale,
                    "migration-local-" + safeName());
            t.setDaemon(true);
            t.start();
        }
        if (dataSource.isPrimaryConfigured()) {
            Thread t = new Thread(this::executerMigrationDistante,
                    "migration-remote-" + safeName());
            t.setDaemon(true);
            t.start();
        } else {
            migrationDistanteTerminee = true;
        }
    }

    private void executerMigrationLocale() {
        for (int tentative = 1; tentative <= 6; tentative++) {
            try (Connection conn = dataSource.getSecondaryConnection()) {
                long start = System.currentTimeMillis();
                new MigrationManager(conn).createAllTables("LOCAL");
                LOGGER.info(() -> "✅ Migration locale OK ("
                        + (System.currentTimeMillis() - start) + " ms) ["
                        + institutionId + "]");
                setSecondaryHealthy(true);
                return;
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "⚠️ Migration locale échouée #" + tentative, e);
                if (tentative < 6) sleep(3000L * (1L << (tentative - 1)));
            }
        }
        LOGGER.severe(() -> "❌ Migration locale abandonnée [" + institutionId + "].");
    }

    private void executerMigrationDistante() {
        for (int tentative = 1; tentative <= 6; tentative++) {
            try (Connection conn = dataSource.getPrimaryConnection()) {
                long start = System.currentTimeMillis();
                new MigrationManager(conn).createAllTables("REMOTE");
                LOGGER.info(() -> "✅ Migration distante OK ("
                        + (System.currentTimeMillis() - start) + " ms) ["
                        + institutionId + "]");
                setPrimaryHealthy(true);
                migrationDistanteTerminee = true;
                return;
            } catch (SQLException e) {
                Throwable cause = unwrapException(e);
                boolean permanent = estErreurPermanente(cause);
                LOGGER.log(Level.WARNING, "⚠️ Migration distante échouée #" + tentative, e);

                if (permanent) {
                    LOGGER.severe(() -> "❌ Migration distante abandonnée : erreur permanente ["
                            + institutionId + "]");
                    return;
                }
                if (tentative < 6) sleep(3000L * (1L << (tentative - 1)));
            }
        }
        LOGGER.severe(() -> "❌ Migration distante abandonnée après 6 tentatives ["
                + institutionId + "]");
    }

    /**
     * Classification UNIQUE des erreurs permanentes.
     */
    public static boolean estErreurPermanente(Throwable t) {
        if (t == null) return false;
        if (t instanceof java.net.UnknownHostException) return true;
        if (t instanceof java.net.NoRouteToHostException) return true;
        if (t instanceof java.sql.SQLInvalidAuthorizationSpecException) return true;
        if (t instanceof java.sql.SQLSyntaxErrorException) return true;
        if (t instanceof java.sql.SQLIntegrityConstraintViolationException) return true;

        String msg = t.getMessage();
        if (msg != null) {
            if (msg.contains("UnknownHost"))        return true;
            if (msg.contains("Access denied"))      return true;
            if (msg.contains("Unknown database"))   return true;
            if (msg.contains("Unknown column"))     return true;
            if (msg.contains("doesn't exist"))      return true;
        }
        return t.getCause() != null && t.getCause() != t && estErreurPermanente(t.getCause());
    }

    private Throwable unwrapException(Throwable t) {
        if (t == null) return null;
        if (t instanceof java.lang.reflect.UndeclaredThrowableException ute) {
            Throwable cause = ute.getUndeclaredThrowable();
            if (cause != null) return unwrapException(cause);
        }
        if (t instanceof SQLException) {
            Throwable cause = t.getCause();
            if (cause != null && cause != t) return unwrapException(cause);
        }
        return t;
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrompu pendant le sleep", e);
        }
    }

    // ============================================================
    // RESYNCHRONISATION COMPLÈTE
    // ============================================================
    public void forcerResynchronisationComplete() {
        if (!dataSource.isSecondaryConfigured() || !dataSource.isPrimaryConfigured()) return;
        if (!isSecondaryHealthy() || !isPrimaryHealthy()) return;

        long now = System.currentTimeMillis();
        if (now - dernierResyncMs < RESYNC_MIN_INTERVAL_MS) return;
        if (!resyncEnCours.compareAndSet(false, true)) return;

        dernierResyncMs = now;
        try {
            LOGGER.info(() -> "🔄 [Resync] Complète [" + institutionId + "]...");
            declencherSyncImmediateAsync();
        } finally {
            resyncEnCours.set(false);
        }
    }

    // ============================================================
    // SYNCHRONISATION DIFFÉRÉE
    // ============================================================
    private void syncDeferredOperations() {
        if (deferredOperations.isEmpty()) return;

        List<DeferredOperation> ops = new ArrayList<>();
        int count = 0;
        DeferredOperation op;
        while (count < MAX_BATCH_DEFERRED && (op = deferredOperations.poll()) != null) {
            ops.add(op);
            count++;
        }

        int syncCount = 0, abandonnees = 0, reessayees = 0;
        try (Connection conn = dataSource.getPrimaryConnection()) {
            for (DeferredOperation operation : ops) {
                long ageMs = System.currentTimeMillis() - operation.timestamp;
                if (ageMs > 3_600_000L) { abandonnees++; continue; }

                try (PreparedStatement ps = conn.prepareStatement(operation.sql)) {
                    if (operation.params != null) {
                        for (int i = 0; i < operation.params.length; i++) {
                            ps.setObject(i + 1, operation.params[i]);
                        }
                    }
                    ps.executeUpdate();
                    syncCount++;
                } catch (SQLException e) {
                    Throwable cause = unwrapException(e);
                    if (estErreurPermanente(cause)) {
                        abandonnees++;
                        LOGGER.warning(() -> "⛔ Op abandonnée : " + operation.sql);
                    } else {
                        deferredOperations.offer(operation);
                        reessayees++;
                    }
                }
            }
            if (syncCount > 0 || abandonnees > 0 || reessayees > 0) {
                LOGGER.info(String.format(
                    "🔄 Sync différée [%s] : %d exécutées, %d abandonnées, %d réessayées",
                    institutionId, syncCount, abandonnees, reessayees));
            }
        } catch (SQLException e) {
            for (DeferredOperation operation : ops) deferredOperations.offer(operation);
        }
    }

    // ============================================================
    // UTILITAIRES PUBLICS
    // ============================================================
    public int executeUpdate(String sql, Object... params) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            int result = ps.executeUpdate();
            try {
                if (conn.getAutoCommit()) declencherSyncImmediateAsync();
            } catch (SQLException ignored) { /* best effort */ }
            return result;
        } catch (SQLException e) {
            if (deferredOperations.size() < MAX_DEFERRED_QUEUE_SIZE) {
                deferredOperations.offer(new DeferredOperation(sql, params));
            } else {
                LOGGER.severe(() -> "❌ File différée pleine (" + MAX_DEFERRED_QUEUE_SIZE
                        + ") — opération perdue : " + sql);
            }
            throw e;
        }
    }

    public boolean isLocalHealthy()  { return isSecondaryHealthy(); }
    public boolean isRemoteHealthy() { return isPrimaryHealthy(); }
    public boolean isLocalConfigured()  { return dataSource.isSecondaryConfigured(); }
    public boolean isRemoteConfigured() { return dataSource.isPrimaryConfigured(); }
    public boolean isMigrationDistanteTerminee() { return migrationDistanteTerminee; }
    public int getPendingOperationsCount() { return deferredOperations.size(); }

    public String getSystemStatus() {
        return String.format("[%s] Primaire=%s, Secondaire=%s, Stratégie=%s, En attente=%d",
            institutionId,
            isPrimaryHealthy()   ? "✅" : "❌",
            isSecondaryHealthy() ? "✅" : "❌",
            strategy, getPendingOperationsCount());
    }

    public String getLocalPoolStats()  { return dataSource.getSecondaryPoolStats(); }
    public String getRemotePoolStats() { return dataSource.getPrimaryPoolStats(); }

    public void closeAllConnections() { dataSource.closeAll(); }

    // ============================================================
    // UTILITAIRES PRIVÉS
    // ============================================================
    private String escapeIdentifier(String ident) {
        if (ident == null) return "``";
        return "`" + ident.replace("`", "``") + "`";
    }

    private void closeQuietly(Connection conn) {
        if (conn == null) return;
        try { if (!conn.isClosed()) conn.close(); } catch (SQLException ignored) {}
    }

    private void addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.info(() -> "🛑 Arrêt [" + institutionId + "]...");
            healthCheckExecutor.shutdownNow();
            syncExecutor.shutdownNow();
            syncTriggerExecutor.shutdownNow();
            dataSource.closeAll();
        }, "db-shutdown-" + safeName()));
    }
}