package io.github.blackbaroness.loader.runtime;

import lombok.RequiredArgsConstructor;

import java.nio.file.Path;
import java.util.logging.Logger;

@RequiredArgsConstructor
public class LoaderBuilder {

    private final Path directory;
    private final String manifestJson;

    private Logger logger;
    private boolean removeUnusedJars = true;

    public LoaderBuilder setLogger(Logger logger) {
        this.logger = logger;
        return this;
    }

    public LoaderBuilder setRemoveUnusedJars(boolean removeUnusedJars) {
        this.removeUnusedJars = removeUnusedJars;
        return this;
    }

    public Loader build() {
        return new Loader(
            directory,
            manifestJson,
            logger,
            removeUnusedJars
        );
    }
}
