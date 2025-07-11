package io.github.blackbaroness.loader.runtime;

import lombok.RequiredArgsConstructor;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

@RequiredArgsConstructor
public class ProgressNotifier extends Thread {

    private final Logger logger;
    private final int total;

    private final long startedAt = System.currentTimeMillis();
    private final AtomicInteger completed = new AtomicInteger(0);

    @SuppressWarnings({"InfiniteLoopStatement", "BusyWait"})
    @Override
    public void run() {
        if (logger == null) return;

        try {
            while (true) {
                logger.info("preparing dependencies " + completed.get() + "/" + total + " (ETA: " + getEta() + ")");
                Thread.sleep(500);
            }
        } catch (InterruptedException ignored) {
        }
    }

    public void increment() {
        completed.incrementAndGet();
    }

    private String getEta() {
        final int completed = this.completed.get();
        if (completed == 0) return "-";

        final long millisPassed = System.currentTimeMillis() - startedAt;
        final long millisPerDependency = millisPassed / completed;

        final int dependenciesLeft = total - completed;
        final long millisLeft = millisPerDependency * dependenciesLeft;

        return TimeUnit.MILLISECONDS.toSeconds(millisLeft) + "s";
    }
}
