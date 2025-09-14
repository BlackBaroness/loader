package io.github.blackbaroness.loader.bootstrap;

import io.github.blackbaroness.loader.runtime.Loader;
import io.github.blackbaroness.loader.runtime.LoaderBuilder;
import lombok.Getter;
import lombok.SneakyThrows;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

public abstract class LoaderBootstrap {

    private final Path librariesDirectory;
    private final Path tempDirectory;
    private final Logger logger;
    private final String mainClassName;
    private final ClassLoader classLoader;
    private final Path currentJarPath;

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
        //noinspection resource
        final URLClassLoader classLoader = createClassLoader();
        final Class<?> mainClass = classLoader.loadClass(mainClassName);
        this.mainInstance = createMainInstance0(mainClass);
    }

    @SneakyThrows
    public void invokeMainMethod(String methodName) {
        final Method method = mainInstance.getClass().getDeclaredMethod(methodName);
        if (method.canAccess(mainInstance)) {
            method.invoke(mainInstance);
        } else {
            method.setAccessible(true);
            method.invoke(mainInstance);
            method.setAccessible(false);
        }
    }

    protected abstract Object createMainInstance0(Class<?> clazz);

    @SneakyThrows
    private URLClassLoader createClassLoader() {
        final String manifestJson;
        try (final InputStream inputStream = getClass().getClassLoader().getResourceAsStream("loader-manifest.json")) {
            Objects.requireNonNull(inputStream, "loader-manifest.json not found");
            manifestJson = new String(inputStream.readAllBytes());
        }

        final Loader loader = new LoaderBuilder(librariesDirectory, manifestJson)
            .setLogger(logger)
            .setTempDirectory(tempDirectory)
            .build();

        final Path notRelocatedJarCopy = createJarCopy();
        final Path finalJar = Files.createTempFile(null, ".jar");
        finalJar.toFile().deleteOnExit();
        loader.relocateJar(notRelocatedJarCopy, finalJar);
        Files.deleteIfExists(notRelocatedJarCopy);

        loader.prepare();
        return loader.loadToNewClassLoader(classLoader, List.of(finalJar.toUri().toURL()));
    }

    @SneakyThrows
    private Path createJarCopy() {
        final Path tempLocation = Files.createTempFile(null, ".jar");

        Files.copy(
            currentJarPath,
            tempLocation,
            StandardCopyOption.REPLACE_EXISTING
        );

        return tempLocation;
    }
}
