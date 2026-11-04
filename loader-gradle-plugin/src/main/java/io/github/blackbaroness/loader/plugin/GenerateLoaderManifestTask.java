package io.github.blackbaroness.loader.plugin;

import io.github.blackbaroness.loader.manifest.Manifest;
import io.github.blackbaroness.loader.manifest.ManifestXml;
import io.github.blackbaroness.loader.manifest.Sha256;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.*;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@CacheableTask
public abstract class GenerateLoaderManifestTask extends DefaultTask {
    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @Nested
    public abstract ListProperty<ArtifactInput> getArtifacts();

    @Input
    public abstract ListProperty<String> getRepositories();

    @Input
    public abstract ListProperty<String> getOverrideRepositories();

    @Input
    public abstract MapProperty<String, String> getRelocations();

    @Input
    public List<String> getRelocationOrder() {
        return new ArrayList<>(getRelocations().get().keySet());
    }

    public GenerateLoaderManifestTask() {
        getRepositories().convention(List.of());
        getOverrideRepositories().convention(List.of());
        getRelocations().convention(Map.of());
    }

    @TaskAction
    public void generate() throws IOException {
        var urls = getOverrideRepositories().get();
        if (urls.isEmpty()) urls = getRepositories().get();
        var dependencies = new ArrayList<Manifest.Dependency>();
        for (var artifact : getArtifacts().get().stream().sorted(Comparator.comparing(ArtifactInput::path)).toList()) {
            dependencies.add(new Manifest.Dependency(artifact.path(), Sha256.hash(artifact.file().toPath())));
        }
        var manifest = new Manifest(urls, dependencies, getRelocations().get());
        var output = getOutputFile().get().getAsFile().toPath();
        Files.createDirectories(output.getParent());
        Files.writeString(output, ManifestXml.write(manifest), StandardCharsets.UTF_8);
        getLogger().lifecycle("Generated loader manifest: {}", output);
    }

    public record ArtifactInput(String path, File file) implements Serializable {
        @Input
        public String getPath() {
            return path;
        }

        @InputFile
        @PathSensitive(PathSensitivity.NONE)
        public File getFile() {
            return file;
        }
    }
}
