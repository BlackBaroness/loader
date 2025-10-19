package io.github.blackbaroness.loader.bootstrap;

import io.github.blackbaroness.loader.runtime.Loader;
import io.github.blackbaroness.loader.runtime.LoaderBuilder;
import io.github.blackbaroness.loader.runtime.LoaderUtils;
import io.github.blackbaroness.loader.runtime.Manifest;
import lombok.Getter;
import lombok.SneakyThrows;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.jar.JarFile;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;

public abstract class LoaderBootstrap {

    private final Path librariesDirectory;
    private final Path tempDirectory;
    private final Logger logger;
    private final String mainClassName;
    private final ClassLoader classLoader;
    private final Path currentJarPath;

    @Getter
    private ClassLoader createdClassLoader;

    @Getter
    private Object mainInstance;

    public LoaderBootstrap(Path librariesDirectory, Path tempDirectory, Logger logger, String mainClassName, ClassLoader classLoader, Path currentJarPath) {
        this.librariesDirectory = librariesDirectory;
        this.tempDirectory = tempDirectory;
        this.logger = logger;
        this.mainClassName = mainClassName;
        this.classLoader = classLoader;
        this.currentJarPath = currentJarPath;
    }

    @SneakyThrows
    public void createMainInstance() {
        createdClassLoader = createClassLoader();
        final Class<?> mainClass = createdClassLoader.loadClass(mainClassName);
        this.mainInstance = createMainInstance0(mainClass);
    }

    @SneakyThrows
    public void invokeMainMethod(String methodName) {
        LoaderUtils.invokeMethod(mainInstance, methodName);
    }

    protected abstract Object createMainInstance0(Class<?> clazz);

    protected ClassLoader createClassLoader(ClassLoader parent, Collection<URL> extraUrls) {
        return new ChildFirstClassLoader(extraUrls.toArray(URL[]::new), parent);
    }

    @SneakyThrows
    private ClassLoader createClassLoader() {
        final String manifestJson = readManifestJson(currentJarPath);

        final Loader loader = new LoaderBuilder(librariesDirectory, manifestJson)
            .setLogger(logger)
            .setTempDirectory(tempDirectory)
            .build();

        // we need to relocate the current jar as well, not only dependencies
        final Path copyOfCurrentJar = createJarCopyOfCurrentJar();
        final Path relocatedJar = createTempJar(tempDirectory);
        loader.relocateJar(copyOfCurrentJar, relocatedJar);
        Files.deleteIfExists(copyOfCurrentJar);

        loader.prepare();

        if (logger != null)
            logger.info("Loader: creating class loader...");

        final Collection<URL> urls = new ArrayList<>();
        urls.add(relocatedJar.toUri().toURL());
        for (Manifest.Dependency dependency : loader.getResolvedDependencies()) {
            urls.add(LoaderUtils.toURL(dependency.getJarFile()));
        }

        return createClassLoader(classLoader, urls);
    }

    private String readManifestJson(Path path) throws IOException {
        final String fileName = "loader-manifest.json";
        try (final JarFile jarFile = new JarFile(path.toFile())) {
            final ZipEntry entry = jarFile.getEntry(fileName);
            if (entry == null)
                throw new NoSuchFileException(fileName + " not found in " + path.toAbsolutePath());

            try (final InputStream inputStream = jarFile.getInputStream(entry)) {
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
    }

    @SneakyThrows
    private Path createJarCopyOfCurrentJar() {
        final Path path = createTempJar(tempDirectory);
        Files.copy(currentJarPath, path, StandardCopyOption.REPLACE_EXISTING);
        return path;
    }

    @SneakyThrows
    private Path createTempJar(Path directory) {
        final Path path;
        if (directory == null) {
            path = Files.createTempFile(null, ".jar");
        } else {
            path = Files.createTempFile(directory, null, ".jar");
        }

        path.toFile().deleteOnExit();
        return path;
    }
}
