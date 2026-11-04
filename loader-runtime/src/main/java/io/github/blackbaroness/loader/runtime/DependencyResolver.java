package io.github.blackbaroness.loader.runtime;

import io.github.blackbaroness.loader.manifest.Manifest;
import io.github.blackbaroness.loader.manifest.Sha256;
import io.github.blackbaroness.loader.runtime.relocator.JarRelocator;
import lombok.SneakyThrows;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

final class DependencyResolver {

    private static final String CACHE_VERSION = "4";

    private final LoaderSettings settings;
    private final Manifest manifest;
    private final String relocationHash;

    private HttpClient httpClient;

    public DependencyResolver(LoaderSettings settings, Manifest manifest) {
        this.settings = settings;
        this.manifest = manifest;
        this.relocationHash = hashRelocations(manifest.relocations());
    }

    public Path resolve(Manifest.Dependency dependency) throws IOException {
        Path cachedJar = cachePath("dependencies", dependency.sha256());
        if (hasValidChecksum(cachedJar)) return cachedJar;

        Files.createDirectories(settings.tempDirectory());
        var failure = new IOException("Cannot download " + dependency.path());
        for (String repository : manifest.repositories()) {
            Path downloadedJar = Files.createTempFile(settings.tempDirectory(), ".loader-", ".download");
            try {
                URI uri = URI.create(repository + "/" + dependency.path());
                downloadVerifiedJar(uri, downloadedJar, dependency.sha256());
                processJar(downloadedJar, cachedJar);
                return cachedJar;
            } catch (IOException e) {
                if (Thread.currentThread().isInterrupted()) throw e;
                failure.addSuppressed(new IOException("Repository " + repository + ": " + e.getMessage(), e));
            } finally {
                Files.deleteIfExists(downloadedJar);
            }
        }
        throw failure;
    }

    public Path prepareCore(Path source) throws IOException {
        Path target = cachePath("core", Sha256.hash(source));
        if (!hasValidChecksum(target)) processJar(source, target);
        return target;
    }

    public void removeUnusedFiles(Collection<Path> classpath) throws IOException {
        if (!settings.removeUnusedJars()) return;
        Path root = settings.directory();
        var retained = new HashSet<Path>();
        retained.add(root);
        for (Path jar : classpath) {
            retained.add(jar);
            retained.add(checksumPath(jar));
            for (Path parent = jar.getParent(); parent != null && parent.startsWith(root); parent = parent.getParent()) {
                retained.add(parent);
            }
        }
        if (Files.isDirectory(root)) {
            final List<Path> unused;
            try (var files = Files.walk(root)) {
                unused = files.filter(path -> !retained.contains(path)).sorted(Comparator.reverseOrder()).toList();
            }
            for (Path path : unused) Files.delete(path);
        }
        removeExternalTemporaryFiles();
    }

    private synchronized HttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        }
        return httpClient;
    }

    @SneakyThrows(InterruptedException.class)
    private void downloadVerifiedJar(URI uri, Path destination, String expectedHash) throws IOException {
        var request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofMinutes(1))
            .GET()
            .build();

        var response = getHttpClient().send(
            request,
            HttpResponse.BodyHandlers
                .ofFile(destination, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)
        );

        if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode() + " from " + uri);
        if (!Sha256.hash(destination).equals(expectedHash)) throw new IOException("SHA-256 mismatch: " + uri);
    }

    private static boolean hasValidChecksum(Path jar) throws IOException {
        final Path checksum = checksumPath(jar);
        return Files.isRegularFile(jar) &&
            Files.isRegularFile(checksum) &&
            Files.readString(checksum).equals(Sha256.hash(jar));
    }

    private Path cachePath(String subdirectory, String sourceHash) {
        return settings.directory().resolve(subdirectory).resolve(sourceHash + "-" + relocationHash + ".jar");
    }

    private void processJar(Path source, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        Path stagedJar = Files.createTempFile(target.getParent(), ".loader-", ".part");
        Path stagedChecksum = checksumPath(stagedJar);
        try {
            if (manifest.relocations().isEmpty()) {
                Files.copy(source, stagedJar, StandardCopyOption.REPLACE_EXISTING);
            } else {
                new JarRelocator(source, stagedJar, manifest.relocations()).run();
            }
            Files.writeString(stagedChecksum, Sha256.hash(stagedJar));
            atomicMove(stagedJar, target);
            atomicMove(stagedChecksum, checksumPath(target));
        } finally {
            Files.deleteIfExists(stagedJar);
            Files.deleteIfExists(stagedChecksum);
        }
    }

    private static void atomicMove(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Path checksumPath(Path jar) {
        return jar.resolveSibling(jar.getFileName() + ".sha256");
    }

    private static String hashRelocations(Map<String, String> relocations) {
        var text = new StringBuilder(CACHE_VERSION);
        relocations.forEach((from, to) -> text.append(from).append('\0').append(to).append('\0'));
        return Sha256.hash(text.toString().getBytes(StandardCharsets.UTF_8));
    }

    private void removeExternalTemporaryFiles() throws IOException {
        Path temporary = settings.tempDirectory();
        if (temporary.startsWith(settings.directory()) || !Files.isDirectory(temporary)) return;
        try (var files = Files.newDirectoryStream(temporary, ".loader-*")) {
            for (Path file : files) {
                String name = file.getFileName().toString();
                if (Files.isRegularFile(file) && (name.endsWith(".part") || name.endsWith(".download")))
                    Files.delete(file);
            }
        }
    }
}
