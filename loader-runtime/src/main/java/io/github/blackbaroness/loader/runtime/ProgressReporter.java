package io.github.blackbaroness.loader.runtime;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

final class ProgressReporter implements AutoCloseable {
    private final Logger logger;
    private final int total;
    private final long started = System.nanoTime();
    private final AtomicInteger completed = new AtomicInteger();
    private final ScheduledExecutorService scheduler;
    private boolean closed;

    ProgressReporter(Logger logger, int total) {
        this.logger = logger;
        this.total = total;
        scheduler = logger == null || total == 0 ? null : Executors.newSingleThreadScheduledExecutor(task -> {
            var thread = new Thread(task, "loader-progress");
            thread.setDaemon(true);
            return thread;
        });
        report();
        if (scheduler != null) scheduler.scheduleAtFixedRate(this::report, 2, 2, TimeUnit.SECONDS);
    }

    void advance() {
        if (completed.incrementAndGet() == total) report();
    }

    private synchronized void report() {
        if (closed || logger == null) return;
        int current = completed.get();
        logger.info(format(current, total, System.nanoTime() - started));
        if (current == total) closed = true;
    }

    static String format(int current, int total, long elapsedNanos) {
        if (current == total) return "Loader: finished " + current + "/" + total;
        String eta = current == 0 ? "-"
            : (long) Math.ceil(elapsedNanos / 1_000_000_000.0 * (total - current) / current) + "s";
        return "Loader: preparing dependencies " + current + "/" + total + " (ETA: " + eta + ")";
    }

    @Override
    public synchronized void close() {
        closed = true;
        if (scheduler != null) scheduler.shutdownNow();
    }
}
