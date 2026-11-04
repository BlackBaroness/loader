package io.github.blackbaroness.loader.bootstrap;

import io.github.blackbaroness.loader.manifest.Manifest;
import io.github.blackbaroness.loader.manifest.ManifestXml;
import io.github.blackbaroness.loader.runtime.Loader;
import io.github.blackbaroness.loader.runtime.LoaderSettings;
import lombok.Getter;
import lombok.SneakyThrows;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
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

    public LoaderBootstrap(Path librariesDirectory, Path tempDirectory, Logger logger, String mainClassName,
                           ClassLoader classLoader, Path currentJarPath) {
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
        var method = mainInstance.getClass().getDeclaredMethod(methodName);
        boolean accessible = method.canAccess(mainInstance);
        try {
            if (!accessible)
                method.setAccessible(true);
            method.invoke(mainInstance);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException cause)
                throw cause;
            if (e.getCause() instanceof Error cause)
                throw cause;
            throw e;
        } finally {
            if (!accessible)
                method.setAccessible(false);
        }
    }

    protected abstract Object createMainInstance0(Class<?> clazz);

    protected ClassLoader createClassLoader(ClassLoader parent, Collection<URL> extraUrls) {
        return new ChildFirstClassLoader(extraUrls.toArray(URL[]::new), parent);
    }

    @SneakyThrows
    private ClassLoader createClassLoader() {
        final LoaderSettings settings = LoaderSettings.builder()
            .directory(librariesDirectory)
            .logger(logger)
            .tempDirectory(tempDirectory)
            .build();

        final Loader loader = new Loader(settings, ManifestXml.read(readManifest(currentJarPath)));
        final var classpath = loader.prepare(currentJarPath);

        if (logger != null) {
            logger.info("Loader: creating class loader...");
        }

        final Collection<URL> urls = new ArrayList<>();
        for (Path jar : classpath) {
            urls.add(jar.toUri().toURL());
        }

        return createClassLoader(classLoader, urls);
    }

    private String readManifest(Path path) throws IOException {
        final String fileName = Manifest.FILE_NAME;
        try (final JarFile jarFile = new JarFile(path.toFile())) {
            final ZipEntry entry = jarFile.getEntry(fileName);
            if (entry == null)
                throw new NoSuchFileException(fileName + " not found in " + path.toAbsolutePath());

            try (final InputStream inputStream = jarFile.getInputStream(entry)) {
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
    }

}
