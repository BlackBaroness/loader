/*
 * Copyright Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Original file from: https://github.com/lucko/jar-relocator
 * Modifications made by https://github.com/BlackBaroness/loader/.
 */
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

        Files.createDirectories(this.output.getParent());

        try (JarOutputStream out = new JarOutputStream(new BufferedOutputStream(Files.newOutputStream(this.output)))) {
            out.setLevel(9);
            try (JarFile in = new JarFile(this.input.toFile())) {
                JarRelocatorTask task = new JarRelocatorTask(this.remapper, out, in, List.of(new ServicesResourceTransformer()));
                task.processEntries();
            }
        }
    }

}
