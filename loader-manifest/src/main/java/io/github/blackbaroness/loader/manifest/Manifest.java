package io.github.blackbaroness.loader.manifest;

import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record Manifest(List<String> repositories, List<Dependency> dependencies, Map<String, String> relocations) {
    public static final String FILE_NAME = "loader-manifest.xml";

    public Manifest {
        repositories = repositories.stream().map(Manifest::normalizeRepository).distinct().toList();
        dependencies = List.copyOf(dependencies);
        relocations = validatedRelocations(relocations);
    }

    private static Map<String, String> validatedRelocations(Map<String, String> relocations) {
        var normalized = new LinkedHashMap<String, String>();
        for (var rule : relocations.entrySet()) {
            String from = normalizeRelocationPrefix(rule.getKey());
            String to = normalizeRelocationPrefix(rule.getValue());
            if (normalized.putIfAbsent(from, to) != null) {
                throw new IllegalArgumentException("Duplicate relocation prefix: " + from);
            }
        }
        return Collections.unmodifiableMap(normalized);
    }

    public static String normalizeRelocationPrefix(String prefix) {
        String normalized = prefix.replace('/', '.');
        if (normalized.isEmpty() || normalized.startsWith(".") || normalized.endsWith(".") || normalized.contains("..")
            || normalized.codePoints().anyMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c))) {
            throw new IllegalArgumentException("Invalid relocation prefix: " + prefix);
        }
        return normalized;
    }

    private static String normalizeRepository(String value) {
        var uri = URI.create(value.strip()).normalize();
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
            || uri.getHost() == null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("Expected an HTTP(S) Maven repository URL without query or fragment: " + value);
        }
        String url = uri.toASCIIString();
        int end = url.length();
        while (url.charAt(end - 1) == '/') end--;
        return url.substring(0, end);
    }

    public record Dependency(String path, String sha256) {
        public Dependency {
            validatePath(path);
            if (!sha256.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Invalid SHA-256: " + sha256);
        }

        private static void validatePath(String path) {
            var uri = URI.create(path);
            if (path.isBlank() || uri.isAbsolute() || uri.getRawAuthority() != null
                || uri.getRawQuery() != null || uri.getRawFragment() != null
                || uri.getPath().startsWith("/") || uri.getPath().contains("\\")) {
                throw new IllegalArgumentException("Expected a relative artifact path: " + path);
            }
            for (String segment : uri.getPath().split("/", -1)) {
                if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                    throw new IllegalArgumentException("Invalid artifact path segment: " + path);
                }
            }
        }
    }
}
