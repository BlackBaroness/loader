package io.github.blackbaroness.loader.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project

class LoaderPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val runtimeLibrary = project.configurations.create("runtimeLibrary")
        runtimeLibrary.isCanBeResolved = true
        runtimeLibrary.isCanBeConsumed = false
        project.configurations.getByName("compileClasspath") { extendsFrom(runtimeLibrary) }
        project.configurations.getByName("testCompileClasspath") { extendsFrom(runtimeLibrary) }
        project.configurations.getByName("testRuntimeClasspath") { extendsFrom(runtimeLibrary) }

        project.tasks.register("generateLoaderManifest", GenerateLoaderManifestTask::class.java) {
            this.runtimeLibrary.set(runtimeLibrary)
            this.outputFile.set(project.layout.buildDirectory.dir("generated").map { it.file("loader-manifest.json") })
        }
    }
}
