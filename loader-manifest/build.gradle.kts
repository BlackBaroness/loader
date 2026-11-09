plugins {
    `java-library`
    `maven-publish`
    alias(libs.plugins.publish)
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

tasks.test {
    useJUnitPlatform()
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates("io.github.blackbaroness", "loader-manifest", rootProject.version.toString())

    pom {
        name.set("Loader (Manifest)")
        description.set("Shared manifest model and XML format for Loader.")
        inceptionYear.set("2026")
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
