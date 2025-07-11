package io.github.blackbaroness.loader.runtime;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import io.github.blackbaroness.loader.runtime.relocator.JarRelocator;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import java.net.URL;
import java.net.URLClassLoader;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RequiredArgsConstructor
public class Loader {

    private final Path directory;
    private final String manifestJson;
    private final Logger logger;
    private final Map<String, String> relocations;
    private final boolean removeUnusedJars;

    private HttpClient httpClient;

    @Getter
    private Set<Manifest.Dependency> resolvedDependencies;

    public void prepare() {
        resolvedDependencies = resolveDependencies(loadManifest());
        if (removeUnusedJars) removeUnusedJars(resolvedDependencies);
    }

    public URLClassLoader loadToNewClassLoader(ClassLoader base, Collection<URL> extraUrls) {
        if (logger != null) logger.info("loading resolved dependencies...");

        final URL[] urls = Stream.concat(
                extraUrls.stream(),
                resolvedDependencies.stream().map(Manifest.Dependency::getJarFile).map(Utils::toURL)
        ).toArray(URL[]::new);

        return new URLClassLoader(urls, base);
    }

    @SneakyThrows
    private Manifest loadManifest() {
        final JsonObject root = JsonParser.object().from(manifestJson);

        final JsonArray repositoriesArray = root.getArray("repositories");
        final Set<String> repositories = new LinkedHashSet<>(repositoriesArray.size());
        for (int i = 0; i < repositoriesArray.size(); i++) {
            repositories.add(repositoriesArray.getString(i));
        }

        final String relocationsHash = Utils.sha1(relocations);
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

        return new Manifest(repositories, dependencies);
    }

    private Set<Manifest.Dependency> resolveDependencies(Manifest manifest) {
        httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        final ExecutorService executor = Executors.newFixedThreadPool(Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
        final Collection<CompletableFuture<Manifest.Dependency>> tasks = manifest.getDependencies().stream()
                .map(dependency -> resolveDependencyAsync(manifest, dependency, executor))
                .collect(Collectors.toUnmodifiableList());

        final Set<Manifest.Dependency> resolvedDependencies = new LinkedHashSet<>();
        final ProgressNotifier progressNotifier = new ProgressNotifier(logger, tasks.size());
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

    private CompletableFuture<Manifest.Dependency> resolveDependencyAsync(Manifest manifest, Manifest.Dependency dependency, Executor executor) {
        return CompletableFuture.supplyAsync(() -> resolveDependency(manifest, dependency), executor);
    }

    @SneakyThrows
    private Manifest.Dependency resolveDependency(Manifest manifest, Manifest.Dependency dependency) {
        if (!Files.exists(dependency.getJarFile()) || !Files.exists(dependency.getJarSha1File())) {
            downloadDependency(manifest, dependency);
            return dependency;
        }

        final String expectedHash = Files.readString(dependency.getJarSha1File());
        final String actualHash = Utils.sha1(dependency.getJarFile());
        if (!expectedHash.equals(actualHash)) {
            downloadDependency(manifest, dependency);
        }

        return dependency;
    }

    @SneakyThrows
    private void downloadDependency(Manifest manifest, Manifest.Dependency dependency) {
        final List<Throwable> errors = new ArrayList<>(manifest.getRepositories().size());

        for (final String repository : manifest.getRepositories()) {
            try {
                downloadDependency(dependency, repository);
                return;
            } catch (Throwable t) {
                errors.add(t);
            }
        }

        final Exception exception = new RuntimeException("Failed to download " + dependency);
        errors.forEach(exception::addSuppressed);
        throw exception;
    }

    @SneakyThrows
    private void downloadDependency(Manifest.Dependency dependency, String repository) {
        // check is repository has a valid jar
        final String remoteHash = Utils.fetchString(httpClient, dependency.toJarSha1HttpUrl(repository));
        if (!remoteHash.equals(dependency.getSha1()))
            throw new IllegalStateException("Repository " + repository + " returned invalid sha1 for dependency " + dependency);

        // download a new jar and validate it
        final Path temporaryFile = Files.createTempFile(dependency.getGroupId(), dependency.getArtifactId());
        Utils.downloadFile(dependency.toJarHttpUrl(repository), temporaryFile, httpClient);
        if (!Objects.equals(Utils.sha1(temporaryFile), remoteHash))
            throw new IllegalStateException("Downloaded file of " + dependency + " has invalid sha1");

        // perform relocation
        new JarRelocator(temporaryFile, dependency.getJarFile(), relocations).run();
        Files.deleteIfExists(temporaryFile);

        // save new checksum
        Files.writeString(dependency.getJarSha1File(), Utils.sha1(dependency.getJarFile()));
    }

    private void removeUnusedJars(Set<Manifest.Dependency> resolvedDependencies) {
        Utils.removeFilesFromDirectory(
                directory,
                resolvedDependencies.stream()
                        .flatMap(it -> Stream.of(it.getJarFile(), it.getJarSha1File()))
                        .collect(Collectors.toUnmodifiableSet())
        );
    }
}
