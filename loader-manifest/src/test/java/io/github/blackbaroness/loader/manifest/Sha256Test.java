package io.github.blackbaroness.loader.manifest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Sha256Test {
    @TempDir
    Path directory;

    @Test
    void hashesKnownValuesAndStreamsFilesLargerThanTheBuffer() throws Exception {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Sha256.hash(new byte[0]));
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Sha256.hash("abc".getBytes(StandardCharsets.UTF_8)));
        byte[] bytes = new byte[20000];
        Arrays.fill(bytes, (byte) 42);
        Path file = Files.write(directory.resolve("fixture"), bytes);
        assertEquals(Sha256.hash(bytes), Sha256.hash(file));
    }
}
