package io.github.blackbaroness.loader.runtime.relocator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class JarRelocatorTest {
    @TempDir
    Path directory;

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void preservesFramesMultiReleaseServicesAndManifestMetadata() throws Exception {
        Path service = directory.resolve("Service.java");
        Path implementation = directory.resolve("Impl.java");
        Files.writeString(service, "package old; public interface Service { String value(int number); }");
        Files.writeString(implementation, """
            package old;
            public class Impl implements Service {
                public String value(int number) {
                    try {
                        if (number < 0) throw new IllegalArgumentException();
                        return number == 0 ? "zero" : "positive";
                    } catch (IllegalArgumentException e) {
                        return "negative";
                    }
                }
            }
            """);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
            "--release", "17", "-d", directory.toString(), service.toString(), implementation.toString()));
        Path input = directory.resolve("input.jar");
        Path output = directory.resolve("output.jar");
        try (var jar = new JarOutputStream(Files.newOutputStream(input))) {
            entry(jar, "META-INF/MANIFEST.MF", """
                Manifest-Version: 1.0
                Multi-Release: true
                Main-Class: old.Impl
                SHA-256-Digest-Manifest: obsolete

                Name: old/
                Sealed: true

                Name: old/Impl.class
                SHA-256-Digest: obsolete

                """.getBytes(StandardCharsets.UTF_8));
            entry(jar, "old/Service.class", Files.readAllBytes(directory.resolve("old/Service.class")));
            byte[] type = Files.readAllBytes(directory.resolve("old/Impl.class"));
            entry(jar, "old/Impl.class", type);
            entry(jar, "META-INF/versions/17/old/Impl.class", type);
            entry(jar, "META-INF/services/old.Service", "# comment\n old.Impl # provider\nold.Impl\n".getBytes(StandardCharsets.UTF_8));
            entry(jar, "META-INF/services/hidden.Service", "old.Impl\n".getBytes(StandardCharsets.UTF_8));
            entry(jar, "META-INF/services/unused.Service", "# empty\n".getBytes(StandardCharsets.UTF_8));
            entry(jar, "META-INF/TEST.EC", new byte[0]);
        }
        new JarRelocator(input, output, Map.of("old", "hidden")).run();
        try (var jar = new JarFile(output.toFile())) {
            assertNull(jar.getEntry("META-INF/TEST.EC"));
            assertNull(jar.getEntry("META-INF/services/unused.Service"));
            try (var providers = jar.getInputStream(jar.getEntry("META-INF/services/hidden.Service"))) {
                assertEquals("hidden.Impl\n", new String(providers.readAllBytes(), StandardCharsets.UTF_8));
            }
            assertNotNull(jar.getEntry("META-INF/versions/17/hidden/Impl.class"));
            var manifest = jar.getManifest();
            assertEquals("hidden.Impl", manifest.getMainAttributes().getValue("Main-Class"));
            assertNull(manifest.getMainAttributes().getValue("SHA-256-Digest-Manifest"));
            assertEquals("true", manifest.getAttributes("hidden/").getValue("Sealed"));
            assertNull(manifest.getAttributes("old/Impl.class"));
        }
        try (var loader = new URLClassLoader(new URL[]{output.toUri().toURL()}, null)) {
            Class contract = loader.loadClass("hidden.Service");
            Object instance = ServiceLoader.load(contract, loader).findFirst().orElseThrow();
            var method = contract.getMethod("value", int.class);
            assertEquals("negative", method.invoke(instance, -1));
            assertEquals("zero", method.invoke(instance, 0));
            assertEquals("positive", method.invoke(instance, 1));
            assertTrue(instance.getClass().getPackage().isSealed());
        }
    }

    @Test
    void rejectsTheSameFileAndPreservesOutputWhenInputIsInvalid() throws Exception {
        Path input = directory.resolve("input.jar");
        writeJar(input, Map.of("fixture.txt", "keep"));
        byte[] original = Files.readAllBytes(input);
        assertThrows(IOException.class, () -> new JarRelocator(input, input, Map.of()).run());
        Path alias = Files.createDirectory(directory.resolve("sub")).resolve("../input.jar");
        assertThrows(IOException.class, () -> new JarRelocator(input, alias, Map.of()).run());
        assertArrayEquals(original, Files.readAllBytes(input));

        Path invalid = Files.writeString(directory.resolve("invalid.jar"), "invalid");
        Path output = Files.writeString(directory.resolve("output.jar"), "keep");
        assertThrows(IOException.class, () -> new JarRelocator(invalid, output, Map.of()).run());
        assertEquals("keep", Files.readString(output));
    }

    @Test
    void rejectsResourceCollisionsAfterRelocation() throws Exception {
        Path input = directory.resolve("input.jar");
        writeJar(input, Map.of("old/fixture.txt", "first", "hidden/fixture.txt", "second"));
        var relocator = new JarRelocator(input, directory.resolve("output.jar"), Map.of("old", "hidden"));
        var error = assertThrows(IOException.class, relocator::run);
        assertTrue(error.getMessage().contains("hidden/fixture.txt"), error.getMessage());
    }

    @Test
    void mergesCompatibleManifestSectionsAndRejectsConflictingAttributes() throws Exception {
        Path input = directory.resolve("input.jar");
        Path output = directory.resolve("output.jar");
        String manifest = """
            Manifest-Version: 1.0

            Name: old/
            Sealed: true

            Name: hidden/
            Implementation-Version: 1

            """;
        writeJar(input, Map.of("META-INF/MANIFEST.MF", manifest));
        var relocator = new JarRelocator(input, output, Map.of("old", "hidden"));
        relocator.run();
        relocator.run();
        try (var jar = new JarFile(output.toFile())) {
            var attributes = jar.getManifest().getAttributes("hidden/");
            assertEquals("true", attributes.getValue("Sealed"));
            assertEquals("1", attributes.getValue("Implementation-Version"));
        }
        writeJar(input, Map.of("META-INF/MANIFEST.MF", manifest.replace("Implementation-Version: 1", "Sealed: false")));
        var error = assertThrows(IOException.class, relocator::run);
        assertTrue(error.getMessage().contains("Sealed"), error.getMessage());
        assertTrue(error.getMessage().contains("hidden/"), error.getMessage());
    }

    private static void writeJar(Path path, Map<String, String> contents) throws Exception {
        try (var jar = new JarOutputStream(Files.newOutputStream(path))) {
            for (var file : contents.entrySet()) {
                entry(jar, file.getKey(), file.getValue().getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    private static void entry(JarOutputStream jar, String name, byte[] bytes) throws Exception {
        var entry = new JarEntry(name);
        entry.setTime(1700000000000L);
        jar.putNextEntry(entry);
        jar.write(bytes);
        jar.closeEntry();
    }
}
