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

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.jar.*;
import java.util.regex.Pattern;

/**
 * A task that copies {@link JarEntry jar entries} from a {@link JarFile jar input} to a
 * {@link JarOutputStream jar output}, applying the relocations defined by a
 * {@link RelocatingRemapper}.
 */
final class JarRelocatorTask {

    /**
     * Relocation invalidates JAR signatures
     * <a href="https://docs.oracle.com/en/java/javase/11/docs/specs/jar/jar.html#signed-jar-file">Specification</a>
     */
    private static final Pattern SIGNATURE_FILE_PATTERN = Pattern.compile("META-INF/(?:[^/]+\\.(?:DSA|RSA|SF|EC)|SIG-[^/]+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern SIGNATURE_PROPERTY_PATTERN = Pattern.compile(".*-Digest(?:-.*)?", Pattern.CASE_INSENSITIVE);

    private final RelocatingRemapper remapper;
    private final JarOutputStream jarOut;
    private final JarFile jarIn;
    private final ServiceDescriptorRelocator serviceDescriptors;

    private final Set<String> directories = new HashSet<>();
    private final byte[] buffer = new byte[8192];

    JarRelocatorTask(RelocatingRemapper remapper, JarOutputStream jarOut, JarFile jarIn) {
        this.remapper = remapper;
        this.jarOut = jarOut;
        this.jarIn = jarIn;
        this.serviceDescriptors = new ServiceDescriptorRelocator(remapper);
    }

    void processEntries() throws IOException {
        for (Enumeration<JarEntry> entries = jarIn.entries(); entries.hasMoreElements(); ) {
            JarEntry entry = entries.nextElement();

            String name = entry.getName();
            if (name.equals("META-INF/INDEX.LIST") || entry.isDirectory()) {
                continue;
            }

            if (name.regionMatches(true, 0, "META-INF/", 0, 9) && SIGNATURE_FILE_PATTERN.matcher(name).matches()) {
                continue;
            }

            try (InputStream entryIn = jarIn.getInputStream(entry)) {
                processEntry(entry, entryIn);
            }
        }

        serviceDescriptors.writeTo(jarOut);
    }

    private void processEntry(JarEntry entry, InputStream entryIn) throws IOException {
        String name = entry.getName();
        String mappedName = remapper.map(name);
        ensureDirectories(mappedName, entry.getTime());

        if (name.endsWith(".class")) {
            processClass(entry, mappedName, entryIn);
            return;
        }
        if (name.equals("META-INF/MANIFEST.MF")) {
            processManifest(mappedName, entryIn, entry.getTime());
            return;
        }
        processResource(mappedName, entryIn, entry.getTime());
    }

    private void ensureDirectories(String name, long lastModified) throws IOException {
        for (int slash = name.indexOf('/'); slash >= 0; slash = name.indexOf('/', slash + 1)) {
            String directory = name.substring(0, slash + 1);
            if (directories.add(directory)) startEntry(directory, lastModified);
        }
    }

    private void startEntry(String name, long lastModified) throws IOException {
        var entry = new JarEntry(name);
        if (lastModified >= 0) entry.setTime(lastModified);

        // JarOutputStream rejects duplicate names, including transformer output
        jarOut.putNextEntry(entry);
    }

    private void processManifest(String name, InputStream entryIn, long lastModified) throws IOException {
        var manifest = new Manifest(entryIn);
        var main = manifest.getMainAttributes();
        removeSignatures(main);
        for (String attribute : List.of("Main-Class", "Premain-Class", "Agent-Class", "Launcher-Agent-Class")) {
            String value = main.getValue(attribute);
            if (value != null) main.putValue(attribute, (String) remapper.mapValue(value));
        }

        var sections = new LinkedHashMap<String, Attributes>();
        for (var section : manifest.getEntries().entrySet()) {
            var attributes = section.getValue();
            removeSignatures(attributes);
            if (attributes.isEmpty()) continue;
            String sectionName = remapper.map(section.getKey());
            var existing = sections.putIfAbsent(sectionName, attributes);
            if (existing != null) mergeAttributes(existing, attributes, sectionName);
        }
        manifest.getEntries().clear();
        manifest.getEntries().putAll(sections);

        startEntry(name, lastModified);
        manifest.write(jarOut);
    }

    private static void removeSignatures(Attributes attributes) {
        attributes.keySet().removeIf(key -> key.toString().equalsIgnoreCase("Signature-Version")
            || SIGNATURE_PROPERTY_PATTERN.matcher(key.toString()).matches());
    }

    private static void mergeAttributes(Attributes target, Attributes source, String section) throws IOException {
        for (var attribute : source.entrySet()) {
            Object previous = target.putIfAbsent(attribute.getKey(), attribute.getValue());
            if (previous != null && !previous.equals(attribute.getValue())) {
                throw new IOException("Conflicting manifest attribute " + attribute.getKey() + " in section " + section);
            }
        }
    }

    private void processResource(String name, InputStream entryIn, long lastModified) throws IOException {
        if (ServiceDescriptorRelocator.isServiceDescriptor(name)) {
            serviceDescriptors.collect(name, entryIn);
            return;
        }

        startEntry(name, lastModified);
        for (int count; (count = entryIn.read(buffer)) != -1; ) {
            jarOut.write(buffer, 0, count);
        }
    }

    private void processClass(JarEntry entry, String mappedName, InputStream entryIn) throws IOException {
        String name = entry.getName();
        try {
            var reader = new ClassReader(entryIn);
            var writer = new ClassWriter(0);
            reader.accept(new ClassRemapper(writer, remapper), 0);
            startEntry(mappedName, entry.getTime());
            jarOut.write(writer.toByteArray());
        } catch (RuntimeException e) {
            throw new IOException("Error relocating class " + name, e);
        }
    }
}
