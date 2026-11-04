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
 * Modifications made by https://github.com/BlackBaroness/loader
 */
package io.github.blackbaroness.loader.runtime.relocator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

final class ServiceDescriptorRelocator {
    private static final String PREFIX = "META-INF/services/";

    private final RelocatingRemapper remapper;
    private final Map<String, Set<String>> providersByDescriptor = new LinkedHashMap<>();

    ServiceDescriptorRelocator(RelocatingRemapper remapper) {
        this.remapper = remapper;
    }

    static boolean isServiceDescriptor(String name) {
        return name.startsWith(PREFIX);
    }

    void collect(String name, InputStream input) throws IOException {
        String serviceName = remapper.mapClassName(name.substring(PREFIX.length()));
        var providers = providersByDescriptor.computeIfAbsent(PREFIX + serviceName, ignored -> new LinkedHashSet<>());
        var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        for (String line; (line = reader.readLine()) != null; ) {
            int comment = line.indexOf('#');
            if (comment >= 0) line = line.substring(0, comment);
            line = line.trim();
            if (!line.isEmpty()) providers.add(remapper.mapClassName(line));
        }
    }

    void writeTo(JarOutputStream output) throws IOException {
        for (var descriptor : providersByDescriptor.entrySet()) {
            if (descriptor.getValue().isEmpty()) continue;
            var text = new StringBuilder();
            for (String provider : descriptor.getValue()) text.append(provider).append('\n');
            output.putNextEntry(new JarEntry(descriptor.getKey()));
            output.write(text.toString().getBytes(StandardCharsets.UTF_8));
        }
    }
}
