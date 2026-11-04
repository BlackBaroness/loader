package io.github.blackbaroness.loader.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressReporterTest {
    @Test
    void formatsAsciiProgressAndEta() {
        assertEquals("Loader: preparing dependencies 0/10 (ETA: -)", ProgressReporter.format(0, 10, 0));
        assertEquals("Loader: preparing dependencies 5/10 (ETA: 5s)", ProgressReporter.format(5, 10, 5_000_000_000L));
        assertEquals("Loader: preparing dependencies 1/2 (ETA: 601s)", ProgressReporter.format(1, 2, 601_000_000_000L));
        assertEquals("Loader: prepared dependencies 10/10", ProgressReporter.format(10, 10, 10_000_000_000L));
        assertEquals("Loader: prepared dependencies 0/0", ProgressReporter.format(0, 0, 0));
    }

    @Test
    void reportsPeriodicallyAndPrintsFinalCountOnce() throws Exception {
        List<String> messages = new CopyOnWriteArrayList<>();
        var periodicReport = new CountDownLatch(1);
        var logger = new Logger("loader-progress-test", null) {
            @Override
            public void info(String message) {
                messages.add(message);
                if (messages.size() == 2) periodicReport.countDown();
            }
        };
        try (var progress = new ProgressReporter(logger, 2)) {
            progress.advance();
            assertTrue(periodicReport.await(10, TimeUnit.SECONDS));
            assertTrue(messages.get(1).startsWith("Loader: preparing dependencies 1/2 (ETA: "));
            progress.advance();
        }
        assertEquals("Loader: preparing dependencies 0/2 (ETA: -)", messages.get(0));
        assertEquals("Loader: finished 2/2", messages.get(messages.size() - 1));
        assertEquals(1, messages.stream().filter(message -> message.equals("Loader: finished 2/2")).count());
        assertTrue(messages.stream().allMatch(message -> message.chars().allMatch(c -> c < 128)));
    }
}
