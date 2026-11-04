package io.github.blackbaroness.loader.runtime;

import com.sun.net.httpserver.HttpServer;
import io.github.blackbaroness.loader.manifest.ManifestXml;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class SnapshotDownloadTest {
    @TempDir
    Path directory;

    @Test
    void verifiesDownloadReusesCacheAndRecoversFromCorruption() throws Exception {
        byte[] jar = jar("fixture");
        Path core = Files.write(directory.resolve("source.jar"), jar("core"));
        var response = new AtomicReference<>(jar);
        var requests = new AtomicInteger();
        var failedMirrorRequests = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/bad/demo-1.0-20261029.120000-7.jar", exchange -> {
            failedMirrorRequests.incrementAndGet();
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.createContext("/repo/demo-1.0-20261029.120000-7.jar", exchange -> {
            requests.incrementAndGet();
            byte[] body = response.get();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        String manifest = """
            <loader version="2">
              <repository url="http://127.0.0.1:%d/bad"/>
              <repository url="http://127.0.0.1:%d/repo"/>
              <dependency path="demo-1.0-20261029.120000-7.jar" sha256="%s"/>
            </loader>
            """.formatted(server.getAddress().getPort(), server.getAddress().getPort(), hash(jar));
        Path cached;
        try {
            var loader = loader(directory.resolve("cache"), manifest);
            var classpath = loader.prepare(core);
            Path cachedCore = classpath.get(0);
            cached = classpath.get(1);
            assertTrue(Files.exists(cachedCore));
            assertTrue(Files.exists(checksum(cachedCore)));
            assertArrayEquals(jar, Files.readAllBytes(cached));
            loader.prepare(core);
            assertEquals(1, requests.get()); // One successful GET; no remote checksum request.
            assertEquals(1, failedMirrorRequests.get()); // Warm cache does not contact either mirror.

            Files.writeString(cached, "corrupted");
            response.set("bad checksum".getBytes());
            Path oldFile = Files.writeString(directory.resolve("cache/old.jar"), "keep until success");
            assertThrows(IOException.class, () -> loader.prepare(core));
            assertTrue(Files.exists(oldFile));
            assertTrue(Files.exists(cachedCore));
            assertEquals("corrupted", Files.readString(cached)); // No partial replacement.
            try (var files = Files.walk(directory)) {
                assertFalse(files.anyMatch(p -> p.getFileName().toString().startsWith(".loader-")));
            }
            response.set(jar);
            loader.prepare(core);
            assertFalse(Files.exists(oldFile));
            assertArrayEquals(jar, Files.readAllBytes(cached));
        } finally {
            server.stop(0);
        }
        loader(directory.resolve("cache"), manifest).prepare(core); // Offline.
        assertArrayEquals(jar, Files.readAllBytes(cached));
    }

    @Test
    void cachesRelocatedCoreWithoutRewritingItOnWarmStart() throws Exception {
        Path core = directory.resolve("source.jar");
        Files.write(core, jar("first"));
        var loader = loader(directory.resolve("cache"), """
            <loader version="2">
              <relocation from="old" to="relocated"/>
            </loader>
            """);
        Path result = loader.prepare(core).get(0);
        try (var archive = new JarFile(result.toFile())) {
            assertNotNull(archive.getEntry("relocated/fixture.txt"));
        }
        try (var classes = new URLClassLoader(new java.net.URL[]{result.toUri().toURL()}, null)) {
            assertEquals("relocated.Example", classes.loadClass("relocated.Example").getName());
        }
        Files.setLastModifiedTime(result, FileTime.fromMillis(1000));
        assertEquals(result, loader.prepare(core).get(0));
        assertEquals(1000, Files.getLastModifiedTime(result).toMillis());
        Files.write(core, jar("second"));
        Path updated = loader.prepare(core).get(0);
        assertNotEquals(result, updated);
        assertFalse(Files.exists(result));
        assertFalse(Files.exists(checksum(result)));
    }

    @Test
    void cleansFilesOutsideFinalClasspath() throws Exception {
        Path cache = directory.resolve("cache");
        Path temp = Files.createDirectory(directory.resolve("custom-temp"));
        Path source = directory.resolve("source.jar");
        Files.write(source, jar("fixture"));
        var settings = LoaderSettings.builder().directory(cache).tempDirectory(temp).build();
        var loader = new Loader(settings, ManifestXml.read("<loader version=\"2\"/>"));
        Path core = loader.prepare(source).get(0);
        Path dependencies = Files.createDirectories(cache.resolve("dependencies"));
        Path unusedJar = Files.writeString(dependencies.resolve("unused.jar"), "old");
        Path unusedChecksum = Files.writeString(checksum(unusedJar), "old");
        Path orphanChecksum = Files.writeString(dependencies.resolve("orphan.jar.sha256"), "old");
        Path staged = Files.writeString(core.getParent().resolve(".loader-1.part"), "partial");
        Path download = Files.writeString(temp.resolve(".loader-2.download"), "partial");
        Path otherTemp = Files.writeString(temp.resolve("other.tmp"), "keep");
        Path legacyDirectory = Files.createDirectories(cache.resolve("org.old/library/1.0"));
        String legacyName = "a".repeat(40) + "-" + "b".repeat(40) + ".jar";
        Files.writeString(legacyDirectory.resolve(legacyName), "old");
        Files.writeString(legacyDirectory.resolve(legacyName + ".sha1"), "old");
        Path note = Files.writeString(cache.resolve("note.txt"), "unused");
        Path nested = Files.createDirectories(cache.resolve("unknown/empty"));
        Files.writeString(nested.resolve("leftover.bin"), "unused");
        loader.prepare(source);
        for (Path obsolete : java.util.List.of(unusedJar, unusedChecksum, orphanChecksum, staged, download, legacyDirectory)) {
            assertFalse(Files.exists(obsolete), obsolete.toString());
        }
        assertEquals(hash(Files.readAllBytes(core)), Files.readString(checksum(core)));
        assertTrue(Files.exists(otherTemp));
        assertFalse(Files.exists(note));
        assertFalse(Files.exists(cache.resolve("unknown")));

        Files.createDirectories(unusedJar.getParent());
        Files.writeString(unusedJar, "retained by option");
        new Loader(LoaderSettings.builder().directory(cache).tempDirectory(temp).removeUnusedJars(false).build(),
            ManifestXml.read("<loader version=\"2\"/>")).prepare();
        assertTrue(Files.exists(unusedJar));
    }

    @Test
    void interruptionStopsWorkersAndRemovesPartialDownloads() throws Exception {
        var requested = new CountDownLatch(1);
        var active = new AtomicReference<com.sun.net.httpserver.HttpExchange>();
        var failure = new AtomicReference<Throwable>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/wait.jar", exchange -> {
            active.set(exchange);
            exchange.sendResponseHeaders(200, 8); // Keep the body unfinished until the caller interrupts.
            exchange.getResponseBody().flush();
            requested.countDown();
        });
        server.start();
        var loader = loader(directory.resolve("cache"), """
            <loader version="2">
              <repository url="http://127.0.0.1:%d"/>
              <dependency path="wait.jar" sha256="%s"/>
            </loader>
            """.formatted(server.getAddress().getPort(), "a".repeat(64)));
        var caller = new Thread(() -> {
            try {
                loader.prepare();
            } catch (Throwable error) {
                failure.set(error);
            }
        });
        try {
            caller.start();
            assertTrue(requested.await(10, TimeUnit.SECONDS));
            caller.interrupt();
            caller.join(5000);
            assertFalse(caller.isAlive(), "Loader returned only after stopping its workers");
            assertInstanceOf(InterruptedException.class, failure.get());
            try (var files = Files.walk(directory)) {
                assertFalse(files.anyMatch(path -> path.getFileName().toString().startsWith(".loader-")));
            }
        } finally {
            caller.interrupt();
            if (active.get() != null) active.get().close();
            server.stop(0);
            caller.join(5000);
        }
    }

    private static Loader loader(Path directory, String manifest) throws IOException {
        return new Loader(LoaderSettings.builder().directory(directory).build(), ManifestXml.read(manifest));
    }

    private static Path checksum(Path jar) {
        return jar.resolveSibling(jar.getFileName() + ".sha256");
    }

    private static String hash(byte[] bytes) throws Exception {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static byte[] jar(String value) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var jar = new JarOutputStream(bytes)) {
            var type = new ClassWriter(0);
            type.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "old/Example", null, "java/lang/Object", null);
            type.visitEnd();
            jar.putNextEntry(new JarEntry("old/Example.class"));
            jar.write(type.toByteArray());
            jar.closeEntry();
            jar.putNextEntry(new JarEntry("old/fixture.txt"));
            jar.write(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            jar.closeEntry();
        }
        return bytes.toByteArray();
    }
}
