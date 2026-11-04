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
 * Original file from https://github.com/lucko/jar-relocator
 * Modifications made by https://github.com/BlackBaroness/loader.
 */
package io.github.blackbaroness.loader.runtime.relocator;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

public final class JarRelocator {

    private final Path input;
    private final Path output;
    private final RelocatingRemapper remapper;

    public JarRelocator(Path input, Path output, Collection<Relocation> relocations) {
        this.input = input.toAbsolutePath();
        this.output = output.toAbsolutePath();
        this.remapper = new RelocatingRemapper(relocations);
    }

    public JarRelocator(Path input, Path output, Map<String, String> relocations) {
        this(input, output, relocations.entrySet().stream()
            .map(entry -> new Relocation(entry.getKey(), entry.getValue()))
            .toList());
    }

    public void run() throws IOException {
        if (Files.exists(output) && Files.isSameFile(input, output)) {
            throw new IOException("Input and output JAR must be different files: " + output);
        }
        try (var in = new JarFile(input.toFile(), false); var out = openOutput()) {
            out.setLevel(9);
            new JarRelocatorTask(remapper, out, in).processEntries();
        }
    }

    private JarOutputStream openOutput() throws IOException {
        Files.createDirectories(output.getParent());
        return new JarOutputStream(new BufferedOutputStream(Files.newOutputStream(output)));
    }
}
