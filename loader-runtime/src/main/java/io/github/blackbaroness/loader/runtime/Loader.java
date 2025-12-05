package io.github.blackbaroness.loader.runtime;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import io.github.blackbaroness.loader.runtime.relocator.JarRelocator;
import lombok.Getter;
import lombok.SneakyThrows;

import java.net.URL;
import java.net.URLClassLoader;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Loader {

    private final Path directory;
    private final Path tempDirectory;
    private final Logger logger;
    private final boolean removeUnusedJars;
    private final Manifest manifest;

    private HttpClient httpClient;

    @Getter
    private Set<Manifest.Dependency> resolvedDependencies;

    @SneakyThrows
    public Loader(Path directory, Path tempDirectory, String manifestJson, Logger logger, boolean removeUnusedJars) {
        this.directory = directory;
        this.tempDirectory = tempDirectory;
        this.logger = logger;
        this.removeUnusedJars = removeUnusedJars;
        this.manifest = loadManifest(manifestJson);

        LoaderUtils.removeFilesFromDirectory(tempDirectory, Collections.emptySet());
        Files.createDirectories(tempDirectory);
    }

    public void prepare() {
        resolvedDependencies = resolveDependencies();
        if (removeUnusedJars) removeUnusedJars(resolvedDependencies);
    }

    public URLClassLoader loadToNewClassLoader(ClassLoader base, Collection<URL> extraUrls) {
        if (logger != null) logger.info("Loader: creating isolated class loader...");

        final URL[] urls = Stream.concat(
            resolvedDependencies.stream().map(Manifest.Dependency::getJarFile).map(LoaderUtils::toURL),
            extraUrls.stream()
        ).toArray(URL[]::new);

        return new URLClassLoader(urls, base);
    }

    @SneakyThrows
    public void relocateJar(Path input, Path output) {
        if (manifest.getRelocations().isEmpty()) {
            // we can avoid using ASM and simply copy the jar
            Files.createDirectories(output.getParent());
            Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING);
            return;
        }

        new JarRelocator(input, output, manifest.getRelocations()).run();
    }

    @SneakyThrows
    private Manifest loadManifest(String manifestJson) {
        final JsonObject root = JsonParser.object().from(manifestJson);

        final JsonArray repositoriesArray = root.getArray("repositories");
        final Set<String> repositories = new LinkedHashSet<>(repositoriesArray.size());
        for (int i = 0; i < repositoriesArray.size(); i++) {
            repositories.add(repositoriesArray.getString(i));
        }

        final Map<String, String> relocations = new LinkedHashMap<>();
        for (final Map.Entry<String, Object> entry : root.getObject("relocations").entrySet()) {
            relocations.put(entry.getKey(), entry.getValue().toString());
        }

        final String relocationsHash = LoaderUtils.sha1(relocations);
        final JsonArray dependenciesArray = root.getArray("dependencies");
        final Set<Manifest.Dependency> dependencies = new LinkedHashSet<>(dependenciesArray.size());
        for (int i = 0; i < dependenciesArray.size(); i++) {
            final JsonObject dependencyObject = dependenciesArray.getObject(i);
            dependencies.add(
                new Manifest.Dependency(
                    dependencyObject.getString("group"),
                    dependencyObject.getString("artifact"),
                    dependencyObject.getString("version"),
                    dependencyObject.getString("classifier"),
                    dependencyObject.getString("sha1"),
                    directory,
                    relocationsHash
                )
            );
        }

        return new Manifest(repositories, dependencies, relocations);
    }

    private Set<Manifest.Dependency> resolveDependencies() {
        httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

        final ProgressNotifier progressNotifier = new ProgressNotifier(logger, manifest.getDependencies().size());
        final ExecutorService executor = Executors.newFixedThreadPool(Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
        final Collection<CompletableFuture<Manifest.Dependency>> tasks = manifest.getDependencies().stream()
            .map(dependency -> resolveDependencyAsync(dependency, executor, progressNotifier))
            .collect(Collectors.toUnmodifiableList());

        final Set<Manifest.Dependency> resolvedDependencies = new LinkedHashSet<>();
        try {
            progressNotifier.start();
            tasks.forEach(task -> resolvedDependencies.add(task.join()));
        } finally {
            progressNotifier.interrupt();
            httpClient = null;
            executor.shutdownNow();
        }

        return resolvedDependencies;
    }

    private CompletableFuture<Manifest.Dependency> resolveDependencyAsync(Manifest.Dependency dependency, Executor executor, ProgressNotifier progressNotifier) {
        return CompletableFuture.supplyAsync(() -> {
            final Manifest.Dependency result = resolveDependency(dependency);
            progressNotifier.increment();
            return result;
        }, executor);
    }

    @SneakyThrows
    private Manifest.Dependency resolveDependency(Manifest.Dependency dependency) {
        if (!Files.exists(dependency.getJarFile()) || !Files.exists(dependency.getJarSha1File())) {
            downloadDependency(dependency);
            return dependency;
        }

        final String expectedHash = Files.readString(dependency.getJarSha1File());
        final String actualHash = LoaderUtils.sha1(dependency.getJarFile());
        if (!expectedHash.equals(actualHash)) {
            downloadDependency(dependency);
        }

        return dependency;
    }

    @SneakyThrows
    private void downloadDependency(Manifest.Dependency dependency) {
        final List<Throwable> errors = new ArrayList<>(manifest.getRepositories().size());

        for (final String repository : manifest.getRepositories()) {
            try {
                downloadDependency(dependency, repository);
                return;
            } catch (Throwable t) {
                final Exception exception = new RuntimeException("Failed to download " + dependency + " from repository " + repository, t);
                errors.add(exception);
            }
        }

        final Exception exception = new RuntimeException("Failed to download " + dependency);
        errors.forEach(exception::addSuppressed);
        throw exception;
    }

    @SneakyThrows
    private void downloadDependency(Manifest.Dependency dependency, String repository) {
        // check is repository has a valid jar
        final String remoteHash = LoaderUtils.downloadString(httpClient, dependency.toJarSha1HttpUrl(repository)).trim().split(" ")[0];
        if (!remoteHash.equals(dependency.getSha1()))
            throw new IllegalStateException("Repository " + repository + " returned invalid sha1 '" + remoteHash + "' for dependency " + dependency);

        // download a new jar and validate it
        final Path temporaryFile = createTempFile(dependency.getGroupId(), dependency.getArtifactId());
        final String downloadUrl = dependency.toJarHttpUrl(repository);
        LoaderUtils.downloadFile(downloadUrl, temporaryFile, httpClient);
        if (!Objects.equals(LoaderUtils.sha1(temporaryFile), remoteHash))
            throw new IllegalStateException("File " + temporaryFile.toAbsolutePath() + " downloaded from " + downloadUrl + " to resolve " + dependency + " has invalid sha1");

        // perform relocation
        relocateJar(temporaryFile, dependency.getJarFile());
        Files.deleteIfExists(temporaryFile);

        // save new checksum
        Files.writeString(dependency.getJarSha1File(), LoaderUtils.sha1(dependency.getJarFile()));
    }

    private void removeUnusedJars(Set<Manifest.Dependency> resolvedDependencies) {
        LoaderUtils.removeFilesFromDirectory(
            directory,
            resolvedDependencies.stream()
                .flatMap(it -> Stream.of(it.getJarFile(), it.getJarSha1File()))
                .collect(Collectors.toUnmodifiableSet())
        );
    }

    @SneakyThrows
    private Path createTempFile(String prefix, String suffix) {
        if (tempDirectory == null)
            return Files.createTempFile(prefix, suffix);

        return Files.createTempFile(tempDirectory, prefix, suffix);
    }
}
