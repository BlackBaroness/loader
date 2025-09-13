plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "loader"
include(
    "loader-plugin",
    "loader-runtime",
    "loader-bootstrap",
    "loader-bootstrap-bungeecord",
    "loader-bootstrap-bukkit",
)

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
