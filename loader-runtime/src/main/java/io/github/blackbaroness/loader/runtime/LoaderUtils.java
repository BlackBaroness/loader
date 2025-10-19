package io.github.blackbaroness.loader.runtime;

import com.grack.nanojson.JsonWriter;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Set;

@UtilityClass
public class LoaderUtils {

    private final char[] hexArray = "0123456789abcdef".toCharArray();

    @SneakyThrows
    public String sha1(Path path) {
        final MessageDigest digest = createSha1Digest();
        try (SeekableByteChannel seekableByteChannel = Files.newByteChannel(path)) {
            final ByteBuffer buffer = ByteBuffer.allocate(1024 * 8);
            while (seekableByteChannel.read(buffer) > 0) {
                buffer.flip();
                digest.update(buffer);
                buffer.clear();
            }
        }

        return bytesToHex(digest.digest());
    }

    @SneakyThrows
    public String sha1(String str) {
        return bytesToHex(createSha1Digest().digest(str.getBytes(StandardCharsets.UTF_8)));
    }

    @SneakyThrows
    public String sha1(Map<String, String> map) {
        return sha1(JsonWriter.string().object(map).done());
    }

    @SneakyThrows
    public String downloadString(HttpClient httpClient, String url) {
        final HttpResponse<String> response = httpClient.send(
            HttpRequest.newBuilder().uri(URI.create(url)).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        );

        validateResponse(response);
        return response.body().trim();
    }

    @SneakyThrows
    public void downloadFile(String url, Path destination, HttpClient httpClient) {
        Files.createDirectories(destination.getParent());

        final HttpResponse<Path> response = httpClient.send(
            HttpRequest.newBuilder().uri(URI.create(url)).GET().build(),
            HttpResponse.BodyHandlers.ofFile(destination, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        );

        validateResponse(response);
    }

    @SneakyThrows
    public URL toURL(Path path) {
        return path.toUri().toURL();
    }

    public String normalizeUrl(String url) {
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }

        return url;
    }

    public void validateResponse(HttpResponse<?> response) {
        int code = response.statusCode();
        if (code < 200 || code >= 300) {
            throw new RuntimeException("Request failed: " + response.uri().toString() + " (" + code + ")");
        }
    }

    public String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    @SneakyThrows
    public void removeFilesFromDirectory(Path root, Set<Path> whitelist) {
        if (!Files.exists(root)) return;

        Set<Path> normalizedWhitelist = whitelist.stream()
            .map(Path::normalize)
            .collect(java.util.stream.Collectors.toSet());

        Files.walkFileTree(root, new SimpleFileVisitor<>() {

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!normalizedWhitelist.contains(file.normalize())) {
                    Files.delete(file);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (dir.equals(root)) {
                    return FileVisitResult.CONTINUE;
                }

                try {
                    Files.delete(dir);
                } catch (DirectoryNotEmptyException ignored) {
                }

                return FileVisitResult.CONTINUE;
            }
        });
    }

    @SneakyThrows
    public void invokeMethod(Object instance, String name) {
        final Method method = instance.getClass().getDeclaredMethod(name);
        if (method.canAccess(instance)) {
            method.invoke(instance);
        } else {
            method.setAccessible(true);
            method.invoke(instance);
            method.setAccessible(false);
        }
    }

    private MessageDigest createSha1Digest() {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
