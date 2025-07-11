package io.github.blackbaroness.loader.plugin

import groovy.json.JsonOutput
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.security.MessageDigest
import java.util.HexFormat
import kotlin.collections.ArrayDeque
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

abstract class GenerateLoaderManifestTask : DefaultTask() {

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:Internal
    abstract val runtimeLibrary: Property<Configuration>

    @TaskAction
    fun generate() {
        val manifestFile = outputFile.asFile.get().toPath()
        manifestFile.parent.createDirectories()

        val manifest = generateManifest()
        manifestFile.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(manifest)))

        println("Generated libraries manifest at: ${manifestFile.absolutePathString()}")
    }

    private fun generateManifest(): Map<String, Collection<Any>> = mapOf(
        "repositories" to generateRepositories(),
        "dependencies" to generateDependencies(),
    )

    private fun generateRepositories(): Collection<String> = buildSet {
        for (repository in project.repositories) {
            if (repository !is MavenArtifactRepository) continue
            this += repository.url.toString()
        }
    }

    private fun generateDependencies() = buildSet {
        val rootProject = project.rootProject
        val runtimeLibrary = runtimeLibrary.get()

        // contains all dependencies, may contain duplicates (same artifact, different version)
        val modulesDirty = runtimeLibrary.incoming.resolutionResult.root.dependencies.asSequence()
            .flatMap { getModuleVersions(it) }
            .filter { it.group != rootProject.name && rootProject.subprojects.none { sub -> sub.name == it.name } }
            .toSet()

        // contains all dependencies with duplicates removed (the highest version selected)
        val modulesClean = buildSet {
            for (module in modulesDirty) {
                val allVersions = modulesDirty.asSequence()
                    .filter { it.group == module.group && it.name == module.name }
                    .toList()

                val single = allVersions.singleOrNull()
                if (single != null) {
                    add(single)
                    continue
                }

                val newest = allVersions.maxWith { a, b -> compareSemVer(a.version, b.version) }
                add(newest)
            }
        }

        for (module in modulesClean) {
            val artifacts = runtimeLibrary.resolvedConfiguration.resolvedArtifacts.filter {
                it.moduleVersion.id == module && it.file.exists() && it.file.extension == "jar"
            }

            for (artifact in artifacts) {
                val sha1 = sha1(artifact.file)
                this += mapOf(
                    "group" to module.group,
                    "artifact" to module.name,
                    "version" to module.version,
                    "classifier" to artifact.classifier,
                    "sha1" to sha1,
                )
                logger.info("Resolved $artifact to sha1=$sha1")
            }
        }
    }
}

private fun getModuleVersions(dependencyResult: DependencyResult): Set<ModuleVersionIdentifier> {
    val result = mutableSetOf<ModuleVersionIdentifier>()
    val stack = ArrayDeque<DependencyResult>()
    stack += dependencyResult

    while (stack.isNotEmpty()) {
        val current = stack.removeLast()

        val module = getModuleVersion(current)
        if (module != null && result.add(module)) {
            if (current is ResolvedDependencyResult) {
                stack.addAll(current.selected.dependencies)
            }
        }
    }

    return result
}

private fun getModuleVersion(dependencyResult: DependencyResult): ModuleVersionIdentifier? {
    if (dependencyResult !is ResolvedDependencyResult) return null
    return dependencyResult.selected.moduleVersion
}

private fun parseSemVer(version: String): Triple<Int, Int, Int> {
    val match = Regex("""^(\d+)\.(\d+)\.(\d+)$""").matchEntire(version)
        ?: error("Invalid SemVer format: '$version'. Only 'MAJOR.MINOR.PATCH' format allowed.")
    val (major, minor, patch) = match.destructured
    return Triple(major.toInt(), minor.toInt(), patch.toInt())
}

private fun compareSemVer(v1: String, v2: String): Int {
    val (major1, minor1, patch1) = parseSemVer(v1)
    val (major2, minor2, patch2) = parseSemVer(v2)

    return when {
        major1 != major2 -> major1.compareTo(major2)
        minor1 != minor2 -> minor1.compareTo(minor2)
        else -> patch1.compareTo(patch2)
    }
}

private fun sha1(file: File): String {
    val digest = MessageDigest.getInstance("SHA-1")
    file.inputStream().use { input ->
        val buffer = ByteArray(8192)
        var read: Int
        while (input.read(buffer).also { read = it } > 0) {
            digest.update(buffer, 0, read)
        }
    }
    return HexFormat.of().formatHex(digest.digest())
}
