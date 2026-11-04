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

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.Remapper;

import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

final class RelocatingRemapper extends Remapper {
    private static final Pattern CLASS_DESCRIPTOR = Pattern.compile("(\\[*)L(.+);");
    private static final Pattern VERSIONED_PATH = Pattern.compile("^(META-INF/versions/\\d+/)(.*)$");

    private final List<Relocation> rules;

    RelocatingRemapper(Collection<Relocation> rules) {
        super(Opcodes.ASM9);
        this.rules = List.copyOf(rules);
    }

    @Override
    public String map(String name) {
        if (name.startsWith("META-INF/versions/")) {
            var versioned = VERSIONED_PATH.matcher(name);
            if (versioned.matches()) return versioned.group(1) + mapPath(versioned.group(2));
        }
        return mapPath(name);
    }

    private String mapPath(String path) {
        for (var rule : rules) {
            if (rule.canRelocatePath(path)) return rule.relocatePath(path);
        }
        return path;
    }

    String mapClassName(String name) {
        for (var rule : rules) {
            if (rule.canRelocateClass(name)) return rule.relocateClass(name);
        }
        return name;
    }

    @Override
    public Object mapValue(Object value) {
        return value instanceof String text ? mapString(text) : super.mapValue(value);
    }

    private String mapString(String value) {
        if (value.startsWith("L") || value.startsWith("[")) {
            var descriptor = CLASS_DESCRIPTOR.matcher(value);
            if (descriptor.matches()) {
                return descriptor.group(1) + "L" + mapStringName(descriptor.group(2)) + ";";
            }
        }
        return mapStringName(value);
    }

    private String mapStringName(String value) {
        if (value.startsWith("META-INF/versions/")) {
            var versioned = VERSIONED_PATH.matcher(value);
            if (versioned.matches()) return versioned.group(1) + mapClassOrPath(versioned.group(2));
        }
        return mapClassOrPath(value);
    }

    private String mapClassOrPath(String value) {
        String className = mapClassName(value);
        return className.equals(value) ? mapPath(value) : className;
    }
}
