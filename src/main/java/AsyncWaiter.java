import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Attente asynchrone générique : poll une condition jusqu'à timeout.
 */
public final class AsyncWaiter {

    private static final Logger LOG = Logger.getLogger(AsyncWaiter.class.getName());
    private static final long POLL_INTERVAL_MS = 300L;

    private AsyncWaiter() { }

    /**
     * Attend que {@code condition} retourne true, puis retourne {@code value}.
     * Retourne null si timeout.
     */
    public static <T> T waitFor(BooleanSupplier condition,
                                Supplier<T> value,
                                long timeoutMs,
                                String name) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<T> ref = new AtomicReference<>();
        ScheduledExecutorService scheduler = newSingleScheduler("wait-" + name);

        try {
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    if (condition.getAsBoolean()) {
                        ref.set(value.get());
                        latch.countDown();
                    }
                } catch (Exception e) {
                    LOG.fine(() -> "Erreur condition " + name + " : " + e.getMessage());
                }
            }, 0, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);

            boolean ok = latch.await(timeoutMs, TimeUnit.MILLISECONDS);
            return ok ? ref.get() : null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            scheduler.shutdownNow();
        }
    }

    /**
     * Variante simplifiée : attend juste un booléen.
     */
    public static boolean waitUntil(BooleanSupplier condition, long timeoutMs, String name) {
        return waitFor(condition, () -> Boolean.TRUE, timeoutMs, name) != null;
    }

    public static ScheduledExecutorService newSingleScheduler(String name) {
        return Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        });
    }

    public static void sleepSafe(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}