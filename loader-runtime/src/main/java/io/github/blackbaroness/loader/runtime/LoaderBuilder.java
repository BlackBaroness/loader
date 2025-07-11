package io.github.blackbaroness.loader.runtime;

import lombok.RequiredArgsConstructor;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

@RequiredArgsConstructor
public class LoaderBuilder {

    private final Path directory;
    private final String manifestJson;

    private Logger logger;
    private Map<String, String> relocations;
    private boolean removeUnusedJars = true;

    public LoaderBuilder setLogger(Logger logger) {
        this.logger = logger;
        return this;
    }

    public LoaderBuilder setRelocations(Map<String, String> relocations) {
        this.relocations = relocations;
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
            Objects.requireNonNullElse(relocations, Collections.emptyMap()),
            removeUnusedJars
        );
    }
}
