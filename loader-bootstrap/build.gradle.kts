plugins {
    `java-library`
    `maven-publish`
    alias(libs.plugins.lombok)
    alias(libs.plugins.publish)
}

repositories {
    mavenCentral()
}

dependencies {
    api(projects.loaderRuntime)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(11)
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates("io.github.blackbaroness", "loader-bootstrap", rootProject.version.toString())

    pom {
        name.set("Loader (Boostrap)")
        description.set("A simple tool that helps your JVM project download, relocate, verify and load your dependencies at runtime.")
        inceptionYear.set("2025")
        url.set("https://github.com/BlackBaroness/loader")
        licenses {
            license {
                name.set("The MIT License")
                url.set("https://opensource.org/license/mit")
                distribution.set("https://opensource.org/license/mit")
            }
        }
        developers {
            developer {
                id.set("blackbaroness")
                name.set("BlackBaroness")
                url.set("https://github.com/BlackBaroness")
            }
        }
        scm {
            url.set("https://github.com/BlackBaroness/loader")
            connection.set("scm:git:git://github.com/BlackBaroness/loader.git")
            developerConnection.set("scm:git:ssh://git@github.com/BlackBaroness/loader.git")
        }
    }
}

