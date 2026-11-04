package io.github.blackbaroness.loader.bootstrap;

import io.github.blackbaroness.loader.manifest.Manifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class LoaderBootstrapTest {
    @TempDir
    Path directory;

    @Test
    void loadsMainFromJarAndPropagatesItsExceptions() throws Exception {
        Path source = directory.resolve("plugin.jar");
        String classFile = Main.class.getName().replace('.', '/') + ".class";
        try (var jar = new JarOutputStream(Files.newOutputStream(source));
             var fixture = Main.class.getResourceAsStream("/" + classFile)) {
            assertNotNull(fixture);
            jar.putNextEntry(new JarEntry(Manifest.FILE_NAME));
            jar.write("<loader version=\"2\"/>".getBytes(StandardCharsets.UTF_8));
            jar.putNextEntry(new JarEntry(classFile));
            fixture.transferTo(jar);
        }
        var bootstrap = new LoaderBootstrap(directory.resolve("cache"), null, null, Main.class.getName(),
            ClassLoader.getPlatformClassLoader(), source) {
            @Override
            protected Object createMainInstance0(Class<?> type) {
                try {
                    return type.getConstructor().newInstance();
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
            }
        };
        bootstrap.createMainInstance();
        try (var classes = (URLClassLoader) bootstrap.getCreatedClassLoader()) {
            var main = bootstrap.getMainInstance();
            assertSame(classes, main.getClass().getClassLoader());
            bootstrap.invokeMainMethod("start");
            assertTrue(main.getClass().getField("started").getBoolean(main));
            var error = assertThrows(IllegalArgumentException.class, () -> bootstrap.invokeMainMethod("fail"));
            assertEquals("fixture", error.getMessage());
        }
    }

    public static class Main {
        public boolean started;

        public void start() {
            started = true;
        }

        public void fail() {
            throw new IllegalArgumentException("fixture");
        }
    }
}
