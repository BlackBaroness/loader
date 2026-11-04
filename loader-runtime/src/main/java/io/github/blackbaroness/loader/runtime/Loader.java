package io.github.blackbaroness.loader.runtime;

import io.github.blackbaroness.loader.manifest.Manifest;
import lombok.NonNull;
import lombok.SneakyThrows;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.*;

public final class Loader {
    private final LoaderSettings settings;
    private final List<Manifest.Dependency> dependencies;
    private final DependencyResolver resolver;

    public Loader(@NonNull LoaderSettings settings, @NonNull Manifest manifest) {
        this.settings = settings;
        this.dependencies = uniqueDependencies(manifest.dependencies());
        this.resolver = new DependencyResolver(settings, manifest);
    }

    public List<Path> prepare() throws IOException {
        return prepare(null);
    }

    public List<Path> prepare(Path core) throws IOException {
        int total = dependencies.size() + (core == null ? 0 : 1);
        try (var progress = new ProgressReporter(settings.logger(), total)) {
            var classpath = new ArrayList<Path>();
            if (core != null) {
                classpath.add(resolver.prepareCore(core));
                progress.advance();
            }
            classpath.addAll(resolveDependencies(progress));
            resolver.removeUnusedFiles(classpath);
            return List.copyOf(classpath);
        }
    }

    private static List<Manifest.Dependency> uniqueDependencies(List<Manifest.Dependency> dependencies) {
        var unique = new LinkedHashMap<String, Manifest.Dependency>();
        for (var dependency : dependencies) unique.putIfAbsent(dependency.sha256(), dependency);
        return List.copyOf(unique.values());
    }

    @SneakyThrows
    private List<Path> resolveDependencies(ProgressReporter progress) {
        if (dependencies.isEmpty()) return List.of();
        var executor = Executors.newFixedThreadPool(Math.min(4, dependencies.size()));
        try {
            var tasks = new ArrayList<Callable<Path>>(dependencies.size());
            for (var dependency : dependencies) {
                tasks.add(() -> {
                    Path jar = resolver.resolve(dependency);
                    progress.advance();
                    return jar;
                });
            }
            var results = new ArrayList<Path>(dependencies.size());
            for (var future : executor.invokeAll(tasks)) results.add(future.get());
            return List.copyOf(results);
        } catch (ExecutionException e) {
            throw e.getCause();
        } finally {
            shutdownAndAwaitTermination(executor);
        }
    }

    private static void shutdownAndAwaitTermination(ExecutorService executor) {
        executor.shutdownNow();
        boolean interrupted = false;
        while (!executor.isTerminated()) {
            try {
                //noinspection StatementWithEmptyBody
                while (!executor.awaitTermination(1, TimeUnit.DAYS)) {
                }
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }

}
