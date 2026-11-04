package io.github.blackbaroness.loader.runtime;

import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Logger;

@lombok.Builder(builderClassName = "Builder")
public record LoaderSettings(Path directory, Path tempDirectory, Logger logger, boolean removeUnusedJars) {
    public LoaderSettings {
        directory = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
        tempDirectory = tempDirectory == null ? directory.resolve("tmp") : tempDirectory.toAbsolutePath().normalize();
    }

    public static class Builder {
        private final boolean removeUnusedJars = true;
    }
}
