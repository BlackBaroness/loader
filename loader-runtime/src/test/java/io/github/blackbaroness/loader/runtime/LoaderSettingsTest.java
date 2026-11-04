package io.github.blackbaroness.loader.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LoaderSettingsTest {
    @TempDir
    Path directory;

    @Test
    void appliesDefaultsAndBuildsIndependentImmutableSettings() {
        var builder = LoaderSettings.builder().directory(directory);
        var defaults = builder.build();
        assertEquals(directory.toAbsolutePath().normalize(), defaults.directory());
        assertEquals(defaults.directory().resolve("tmp"), defaults.tempDirectory());
        assertTrue(defaults.removeUnusedJars());
        assertNull(defaults.logger());

        var changed = builder.tempDirectory(directory.resolve("other")).removeUnusedJars(false).build();
        assertTrue(defaults.removeUnusedJars());
        assertFalse(changed.removeUnusedJars());
        assertEquals(directory.resolve("other"), changed.tempDirectory());
        assertThrows(NullPointerException.class, () -> LoaderSettings.builder().build());
    }
}
