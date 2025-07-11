plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    kotlin("jvm") version "2.2.0"
}

repositories {
    mavenCentral()
}

dependencies {
}

gradlePlugin {
    website.set("https://github.com/BlackBaroness/loader")
    vcsUrl.set("https://github.com/BlackBaroness/loader")

    plugins {
        create("loader-plugin") {
            id = "io.github.johndoe.greeting"
            implementationClass = "io.github.blackbaroness.loader.plugin.LoaderPlugin"
            displayName = "Loader plugin"
            description = "A plugin to generate a manifest for BlackBaroness/loader-runtime"
            tags.set(setOf("dependency", "manifest", "loader"))
        }
    }
}
