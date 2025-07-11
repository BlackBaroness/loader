plugins {
    `java-library`
    `maven-publish`
    id("io.freefair.lombok") version "8.14"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.grack:nanojson:1.10")

    val asm = "9.8"
    implementation("org.ow2.asm:asm:$asm")
    implementation("org.ow2.asm:asm-commons:$asm")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(11)
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            groupId = rootProject.group as String
            artifactId = "runtime"
            version = rootProject.version as String
        }
    }
}

