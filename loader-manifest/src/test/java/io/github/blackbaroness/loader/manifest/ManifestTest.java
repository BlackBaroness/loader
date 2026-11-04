package io.github.blackbaroness.loader.manifest;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ManifestTest {
    @Test
    void readsEscapedAttributesAndPreservesRelocationOrder() throws IOException {
        var manifest = ManifestXml.read("""
            <?xml version="1.0" encoding="UTF-8"?>
            <loader version="2">
              <repository url="https://example.org/libraries/a&amp;b"/>
              <dependency path="group/demo.jar" sha256="%s"/>
              <relocation from="org.old.special" to="hidden.&quot;literal&quot;"/>
              <relocation from="org.old" to="hidden"/>
            </loader>
            """.formatted("a".repeat(64)));
        assertEquals(List.of(URI.create("https://example.org/libraries/a&b").toASCIIString()), manifest.repositories());
        assertEquals(new Manifest.Dependency("group/demo.jar", "a".repeat(64)), manifest.dependencies().get(0));
        assertEquals(List.of("org.old.special", "org.old"), List.copyOf(manifest.relocations().keySet()));
        assertEquals("hidden.\"literal\"", manifest.relocations().get("org.old.special"));
        String xml = ManifestXml.write(manifest);
        assertEquals(manifest, ManifestXml.read(xml));
        assertEquals(xml, ManifestXml.write(manifest));

    }

    @Test
    void normalizesAndDeduplicatesRepositories() throws IOException {
        var manifest = ManifestXml.read("""
            <loader version="2">
              <repository url=" https://example.org/repo/../maven/// "/>
              <repository url="https://example.org/maven"/>
              <repository url="https://example.org/"/>
            </loader>
            """);
        assertEquals(List.of("https://example.org/maven", "https://example.org"), manifest.repositories());
        for (String url : List.of("file:///tmp/repo", "https://example.org/repo?q=1", "https://example.org/#fragment")) {
            assertThrows(IOException.class, () -> ManifestXml.read("<loader version=\"2\"><repository url=\"" + url + "\"/></loader>"));
        }
    }

    @Test
    void rejectsInvalidXmlAndExternalEntities() {
        for (String text : List.of(
            "<loader version=\"1\"/>",
            "<loader version=\"2\"><repository/></loader>",
            "<loader version=\"2\"><unknown/></loader>",
            "<loader version=\"2\"><repository url=\"x\"><repository url=\"y\"/></repository></loader>",
            "<loader version=\"2\"/><loader version=\"2\"/>",
            "<loader version=\"2\">",
            "<!DOCTYPE loader [<!ENTITY external SYSTEM \"file:///loader-must-not-read\">]><loader version=\"2\">&external;</loader>"
        )) {
            assertThrows(IOException.class, () -> ManifestXml.read(text), text);
        }
    }

    @Test
    void rejectsDuplicateOrInvalidRelocationRulesAndPathsOutsideRepository() {
        for (String rules : List.of(
            "<relocation from=\"old\" to=\"one\"/><relocation from=\"old\" to=\"two\"/>",
            "<relocation from=\"old\" to=\"one\"/><relocation from=\"old\" to=\"one\"/>",
            "<relocation from=\"com/old\" to=\"one\"/><relocation from=\"com.old\" to=\"two\"/>",
            "<relocation from=\"old.\" to=\"hidden\"/>"
        )) {
            assertThrows(IOException.class, () -> ManifestXml.read("<loader version=\"2\">" + rules + "</loader>"));
        }
        for (String path : List.of("", "../demo.jar", "group/../demo.jar", "%2e%2e/demo.jar", "/demo.jar",
            "group/%2E%2E/demo.jar", "group//demo.jar", "group/%5cdemo.jar")) {
            assertThrows(IllegalArgumentException.class, () -> new Manifest.Dependency(path, "a".repeat(64)), path);
        }
    }

    @Test
    void normalizesRelocationsWhenConstructedWithoutXml() throws IOException {
        var manifest = new Manifest(List.of(" https://example.org/repo/// "), List.of(),
            java.util.Map.of("com/old", "hidden/$literal"));
        assertEquals(List.of("https://example.org/repo"), manifest.repositories());
        assertEquals(java.util.Map.of("com.old", "hidden.$literal"), manifest.relocations());
        assertEquals(manifest, ManifestXml.read(ManifestXml.write(manifest)));
        assertThrows(IllegalArgumentException.class, () ->
            new Manifest(List.of(), List.of(), java.util.Map.of("old.", "hidden")));
    }

}
