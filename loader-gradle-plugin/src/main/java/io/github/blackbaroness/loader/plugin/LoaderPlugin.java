package io.github.blackbaroness.loader.plugin;

import io.github.blackbaroness.loader.manifest.Manifest;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.internal.artifacts.repositories.resolver.MavenUniqueSnapshotComponentIdentifier;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;

import java.util.List;
import java.util.regex.Pattern;

public final class LoaderPlugin implements Plugin<Project> {
    private static final Pattern TIMESTAMPED_SNAPSHOT = Pattern.compile("-\\d{8}\\.\\d{6}-\\d+$");

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(JavaPlugin.class);
        var libraries = project.getConfigurations().dependencyScope("runtimeLibrary");
        var classpath = createRuntimeClasspath(project, libraries);
        configureCompilation(project, libraries, classpath);
        var manifestTask = registerManifestTask(project, classpath.map(LoaderPlugin::resolveArtifacts));
        configureRepositories(project, manifestTask);
    }

    private static Provider<? extends Configuration> createRuntimeClasspath(
        Project project, Provider<? extends Configuration> libraries
    ) {
        return project.getConfigurations().resolvable("loaderRuntimeClasspath", configuration -> {
            configuration.extendsFrom(libraries.get());
            var attributes = configuration.getAttributes();
            var objects = project.getObjects();
            attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, Usage.JAVA_RUNTIME));
            attributes.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.class, Category.LIBRARY));
            attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.class, LibraryElements.JAR));
            attributes.attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.class, Bundling.EXTERNAL));
        });
    }

    private static void configureCompilation(
        Project project, Provider<? extends Configuration> libraries, Provider<? extends Configuration> classpath
    ) {
        for (String name : List.of(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME, JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME)) {
            project.getConfigurations().named(name, configuration -> configuration.extendsFrom(libraries.get()));
        }
        for (String name : List.of(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME,
            JavaPlugin.TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME, JavaPlugin.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME)) {
            project.getConfigurations().named(name, configuration -> configuration.shouldResolveConsistentlyWith(classpath.get()));
        }
    }

    private static TaskProvider<GenerateLoaderManifestTask> registerManifestTask(
        Project project, Provider<List<GenerateLoaderManifestTask.ArtifactInput>> artifacts
    ) {
        return project.getTasks().register("generateLoaderManifest", GenerateLoaderManifestTask.class, task -> {
            task.getOutputFile().convention(project.getLayout().getBuildDirectory().file("generated/" + Manifest.FILE_NAME));
            task.getArtifacts().set(artifacts);
        });
    }

    private static void configureRepositories(Project project, TaskProvider<GenerateLoaderManifestTask> manifestTask) {
        // Capture plain URLs after the repository DSL has been configured.
        project.afterEvaluate(ignored -> {
            var urls = project.getRepositories().withType(MavenArtifactRepository.class).stream()
                .map(MavenArtifactRepository::getUrl)
                .filter(uri -> "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                .map(Object::toString).toList();
            manifestTask.configure(task -> task.getRepositories().convention(urls));
        });
    }

    private static List<GenerateLoaderManifestTask.ArtifactInput> resolveArtifacts(Configuration classpath) {
        return classpath.getResolvedConfiguration().getResolvedArtifacts().stream()
            .map(LoaderPlugin::artifactInput).toList();
    }

    private static GenerateLoaderManifestTask.ArtifactInput artifactInput(ResolvedArtifact artifact) {
        if (!(artifact.getId().getComponentIdentifier() instanceof ModuleComponentIdentifier)) {
            throw new GradleException("runtimeLibrary supports external Maven JARs only: " + artifact.getId());
        }
        if (!"jar".equals(artifact.getExtension())) {
            throw new GradleException("runtimeLibrary requires JARs: " + artifact.getId());
        }
        return new GenerateLoaderManifestTask.ArtifactInput(mavenPath(artifact), artifact.getFile());
    }

    private static String mavenPath(ResolvedArtifact artifact) {
        var module = artifact.getModuleVersion().getId();
        String version = resolvedVersion(artifact);
        String directoryVersion = TIMESTAMPED_SNAPSHOT.matcher(version).replaceFirst("-SNAPSHOT");
        return module.getGroup().replace('.', '/') + "/" + module.getName() + "/" + directoryVersion
            + "/" + artifact.getName() + "-" + version + artifactSuffix(artifact);
    }

    private static String resolvedVersion(ResolvedArtifact artifact) {
        String version = artifact.getModuleVersion().getId().getVersion();
        if (!version.endsWith("-SNAPSHOT")) return version;

        // Cached snapshot files can keep the generic -SNAPSHOT name.
        if (artifact.getId().getComponentIdentifier() instanceof MavenUniqueSnapshotComponentIdentifier snapshot) {
            return snapshot.getTimestampedVersion();
        }

        String fileName = artifact.getFile().getName();
        String prefix = artifact.getName() + "-";
        String suffix = artifactSuffix(artifact);
        if (fileName.startsWith(prefix) && fileName.endsWith(suffix)) {
            String resolved = fileName.substring(prefix.length(), fileName.length() - suffix.length());
            if (TIMESTAMPED_SNAPSHOT.matcher(resolved).find()) return resolved;
        }
        throw new GradleException("Cannot determine the resolved snapshot timestamp: " + artifact.getId());
    }

    private static String artifactSuffix(ResolvedArtifact artifact) {
        return (artifact.getClassifier() == null ? "" : "-" + artifact.getClassifier()) + ".jar";
    }
}
