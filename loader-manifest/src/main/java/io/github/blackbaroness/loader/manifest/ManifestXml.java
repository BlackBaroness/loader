package io.github.blackbaroness.loader.manifest;

import javax.xml.stream.*;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ManifestXml {
    private static final String VERSION = "2";

    private ManifestXml() {
    }

    public static Manifest read(String text) throws IOException {
        try {
            return readXml(text);
        } catch (XMLStreamException | IllegalArgumentException e) {
            throw new IOException("Invalid loader manifest", e);
        }
    }

    private static Manifest readXml(String text) throws XMLStreamException, IOException {
        var factory = XMLInputFactory.newDefaultFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        var xml = factory.createXMLStreamReader(new StringReader(text));
        try {
            return read(xml);
        } finally {
            xml.close();
        }
    }

    private static Manifest read(XMLStreamReader xml) throws XMLStreamException, IOException {
        xml.nextTag();
        xml.require(XMLStreamConstants.START_ELEMENT, null, "loader");
        if (!VERSION.equals(required(xml, "version"))) {
            throw new IOException("Unsupported loader manifest version; rebuild with loader-plugin 2.x");
        }
        var repositories = new ArrayList<String>();
        var dependencies = new ArrayList<Manifest.Dependency>();
        var relocations = new LinkedHashMap<String, String>();
        while (xml.nextTag() == XMLStreamConstants.START_ELEMENT) {
            String element = xml.getLocalName();
            switch (element) {
                case "repository" -> repositories.add(required(xml, "url"));
                case "dependency" ->
                    dependencies.add(new Manifest.Dependency(required(xml, "path"), required(xml, "sha256")));
                case "relocation" -> readRelocation(xml, relocations);
                default -> throw new IOException("Unknown loader manifest element: " + element);
            }
            xml.nextTag();
            xml.require(XMLStreamConstants.END_ELEMENT, null, element);
        }
        xml.require(XMLStreamConstants.END_ELEMENT, null, "loader");
        while (xml.hasNext()) xml.next();
        return new Manifest(repositories, dependencies, relocations);
    }

    private static void readRelocation(XMLStreamReader xml, Map<String, String> relocations) throws IOException {
        String from = required(xml, "from");
        String to = required(xml, "to");
        if (relocations.putIfAbsent(from, to) != null) {
            throw new IOException("Duplicate relocation prefix: " + from);
        }
    }

    private static String required(XMLStreamReader xml, String attribute) throws IOException {
        String value = xml.getAttributeValue(null, attribute);
        if (value == null || value.isBlank()) {
            throw new IOException("Missing loader manifest attribute: " + xml.getLocalName() + "." + attribute);
        }
        return value;
    }

    public static String write(Manifest manifest) throws IOException {
        var text = new StringWriter();
        try {
            var xml = XMLOutputFactory.newDefaultFactory().createXMLStreamWriter(text);
            try {
                xml.writeStartDocument(StandardCharsets.UTF_8.name(), "1.0");
                xml.writeCharacters("\n");
                xml.writeStartElement("loader");
                xml.writeAttribute("version", VERSION);
                for (String url : manifest.repositories()) {
                    xml.writeCharacters("\n  ");
                    xml.writeEmptyElement("repository");
                    xml.writeAttribute("url", url);
                }
                for (var dependency : manifest.dependencies()) {
                    xml.writeCharacters("\n  ");
                    xml.writeEmptyElement("dependency");
                    xml.writeAttribute("path", dependency.path());
                    xml.writeAttribute("sha256", dependency.sha256());
                }
                for (var rule : manifest.relocations().entrySet()) {
                    xml.writeCharacters("\n  ");
                    xml.writeEmptyElement("relocation");
                    xml.writeAttribute("from", rule.getKey());
                    xml.writeAttribute("to", rule.getValue());
                }
                xml.writeCharacters("\n");
                xml.writeEndElement();
                xml.writeCharacters("\n");
                xml.writeEndDocument();
            } finally {
                xml.close();
            }
        } catch (XMLStreamException e) {
            throw new IOException("Cannot write loader manifest", e);
        }
        return text.toString();
    }
}
