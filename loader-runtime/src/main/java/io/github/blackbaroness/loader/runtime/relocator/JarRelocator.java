package io.github.blackbaroness.loader.runtime.relocator;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

public final class JarRelocator {

    private final Path input;
    private final Path output;
    private final RelocatingRemapper remapper;

    private final AtomicBoolean used = new AtomicBoolean(false);

    public JarRelocator(Path input, Path output, Collection<Relocation> relocations) {
        this.input = input;
        this.output = output;
        this.remapper = new RelocatingRemapper(relocations);
    }

    public JarRelocator(Path input, Path output, Map<String, String> relocations) {
        this.input = input;
        this.output = output;
        Collection<Relocation> c = new ArrayList<>(relocations.size());
        for (Map.Entry<String, String> entry : relocations.entrySet()) {
            c.add(new Relocation(entry.getKey(), entry.getValue()));
        }
        this.remapper = new RelocatingRemapper(c);
    }

    public void run() throws IOException {
        if (this.used.getAndSet(true)) {
            throw new IllegalStateException("#run has already been called on this instance");
        }

        try (JarOutputStream out = new JarOutputStream(new BufferedOutputStream(Files.newOutputStream(this.output)))) {
            out.setLevel(9);
            try (JarFile in = new JarFile(this.input.toFile())) {
                JarRelocatorTask task = new JarRelocatorTask(this.remapper, out, in, List.of(new ServicesResourceTransformer()));
                task.processEntries();
            }
        }
    }

}
