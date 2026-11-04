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

import io.github.blackbaroness.loader.manifest.Manifest;

public final class Relocation {
    private final String classPrefix;
    private final String relocatedClassPrefix;
    private final String pathPrefix;
    private final String relocatedPathPrefix;

    public Relocation(String prefix, String relocatedPrefix) {
        this.classPrefix = Manifest.normalizeRelocationPrefix(prefix);
        this.relocatedClassPrefix = Manifest.normalizeRelocationPrefix(relocatedPrefix);
        this.pathPrefix = this.classPrefix.replace('.', '/');
        this.relocatedPathPrefix = this.relocatedClassPrefix.replace('.', '/');
    }

    private static boolean matches(String value, String prefix, char separator) {
        return value.equals(prefix) || (value.startsWith(prefix)
            && value.length() > prefix.length() && value.charAt(prefix.length()) == separator);
    }

    boolean canRelocatePath(String path) {
        String name = path.endsWith(".class") ? path.substring(0, path.length() - 6) : path;
        return matches(name, pathPrefix, '/')
            || (name.startsWith("/") && matches(name.substring(1), pathPrefix, '/'));
    }

    boolean canRelocateClass(String name) {
        return name.indexOf('/') < 0 && matches(name, classPrefix, '.');
    }

    String relocatePath(String path) {
        int start = path.startsWith("/") ? 1 : 0;
        return path.substring(0, start) + relocatedPathPrefix + path.substring(start + pathPrefix.length());
    }

    String relocateClass(String name) {
        return relocatedClassPrefix + name.substring(classPrefix.length());
    }
}
