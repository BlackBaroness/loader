plugins {
    `java-library`
    id("io.freefair.lombok") version "8.14"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.grack:nanojson:1.10")
    implementation("org.ow2.asm:asm:9.8")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(11)
    }
}
