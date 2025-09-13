plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    `maven-publish`
    alias(libs.plugins.kotlin.jvm)
}

repositories {
    mavenCentral()
}

dependencies {
}

kotlin {
    jvmToolchain(17)
}

gradlePlugin {
    website.set("https://github.com/BlackBaroness/loader")
    vcsUrl.set("https://github.com/BlackBaroness/loader")

    plugins {
        create("loader-plugin") {
            id = "${rootProject.group}.loader.plugin"
            version = rootProject.version
            implementationClass = "io.github.blackbaroness.loader.plugin.LoaderPlugin"
            displayName = "Loader (Plugin)"
            description =
                "A simple tool that helps your JVM project download, relocate, verify and load your dependencies at runtime."
            tags.set(setOf("dependency", "manifest", "loader", "classpath"))
        }
    }
}

