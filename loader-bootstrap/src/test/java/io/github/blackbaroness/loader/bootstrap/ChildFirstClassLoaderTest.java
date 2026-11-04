package io.github.blackbaroness.loader.bootstrap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChildFirstClassLoaderTest {
    @TempDir
    Path directory;

    @Test
    void loadsLocalClassesBeforeParentAndKeepsNormalDelegation() throws Exception {
        Path local = Files.createDirectory(directory.resolve("local"));
        Path inherited = Files.createDirectory(directory.resolve("parent"));
        copyClass(Sample.class, local);
        copyClass(Sample.class, inherited);
        copyClass(ParentOnly.class, inherited);
        try (var parent = new URLClassLoader(new URL[]{inherited.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
             var child = new ChildFirstClassLoader(new URL[]{local.toUri().toURL()}, parent)) {
            assertSame(child, child.loadClass(Sample.class.getName()).getClassLoader());
            assertSame(child.loadClass(Sample.class.getName()), child.loadClass(Sample.class.getName()));
            assertSame(parent.loadClass(ParentOnly.class.getName()), child.loadClass(ParentOnly.class.getName()));
            assertSame(String.class, child.loadClass("java.lang.String"));
            assertThrows(ClassNotFoundException.class, () -> child.loadClass("missing.Type"));
        }
        try (var child = new ChildFirstClassLoader(new URL[0], null)) {
            assertSame(Object.class, child.loadClass("java.lang.Object"));
            assertThrows(ClassNotFoundException.class, () -> child.loadClass(Sample.class.getName()));
        }
    }

    @Test
    void returnsLocalResourcesFirstWithoutDuplicates() throws Exception {
        Path local = Files.createDirectory(directory.resolve("local"));
        Path inherited = Files.createDirectory(directory.resolve("parent"));
        Files.writeString(local.resolve("fixture.txt"), "local");
        Files.writeString(inherited.resolve("fixture.txt"), "parent");
        URL localUrl = local.toUri().toURL();
        URL parentUrl = inherited.toUri().toURL();
        try (var parent = new URLClassLoader(new URL[]{parentUrl}, null);
             var child = new ChildFirstClassLoader(new URL[]{localUrl, parentUrl}, parent)) {
            var resources = Collections.list(child.getResources("fixture.txt"));
            assertEquals(List.of(local.resolve("fixture.txt").toUri().toURL(),
                inherited.resolve("fixture.txt").toUri().toURL()), resources);
            assertEquals(resources.get(0), child.getResource("fixture.txt"));
            try (var input = child.getResourceAsStream("fixture.txt")) {
                assertNotNull(input);
                assertEquals("local", new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            }
        }
    }

    private static void copyClass(Class<?> type, Path directory) throws Exception {
        String resource = type.getName().replace('.', '/') + ".class";
        Path target = directory.resolve(resource);
        Files.createDirectories(target.getParent());
        try (var input = type.getResourceAsStream("/" + resource)) {
            assertNotNull(input);
            Files.copy(input, target);
        }
    }

    public static class Sample {
    }

    public static class ParentOnly {
    }
}
