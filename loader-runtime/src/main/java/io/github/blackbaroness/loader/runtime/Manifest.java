package io.github.blackbaroness.loader.runtime;

import lombok.Value;

import java.nio.file.Path;
import java.util.Set;

@Value
public class Manifest {

    Set<String> repositories;
    Set<Dependency> dependencies;

    @Value
    public static class Dependency {

        String groupId;
        String artifactId;
        String version;
        String classifier;
        String sha1;

        String versionWithClassifier;
        Path jarFile;
        Path jarSha1File;

        public Dependency(String groupId, String artifactId, String version, String sha1, String classifier, Path directory, String relocationsHash) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
            this.sha1 = sha1;
            this.classifier = classifier;

            this.versionWithClassifier = version + (classifier == null ? "" : ("-" + classifier));

            this.jarFile = directory
                    .resolve(groupId)
                    .resolve(artifactId)
                    .resolve(versionWithClassifier)
                    .resolve(sha1 + "-" + relocationsHash + ".jar");

            this.jarSha1File = jarFile.resolveSibling(jarFile.getFileName() + ".sha1");
        }

        public String toJarHttpUrl(String baseUrl) {
            return baseUrl + '/' + groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/" + artifactId + "-" + versionWithClassifier + ".jar";
        }

        public String toJarSha1HttpUrl(String baseUrl) {
            return toJarHttpUrl(baseUrl) + ".sha1";
        }

        @Override
        public String toString() {
            return groupId + ":" + artifactId + ":" + version + versionWithClassifier;
        }
    }
}
