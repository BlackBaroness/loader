## Loader

A simple tool that helps your JVM project download, relocate, verify and load your dependencies at runtime.

- You declare dependencies in Gradle like usual
- They are downloaded into a directory you choose
- Once downloaded, the project can run completely offline
- If you change relocation rules, dependencies are updated automatically
- Extra files in jars are removed to keep things clean

#### How it works

1. Apply the Gradle plugin
2. Use `runtimeLibrary` instead of `implementation` for your dependencies
3. The plugin creates a manifest during the build and puts it in your Jar
4. In your main class, call `loader-runtime` to set things up
5. Loader reads the manifest, prepare the jars, and starts a new classloader

#### Bootstraps

Using bootstraps is a recommended way to boot your program in different environments.

#### Using it in your project

Bootstraps:

```kotlin
dependencies {
    implementation("io.github.blackbaroness:loader-bootstrap-bungeecord:1.3.0")
    implementation("io.github.blackbaroness:loader-bootstrap-bukkit:1.3.0")
}
```

Gradle plugin:

```kotlin
plugins {
    id("io.github.blackbaroness.loader.plugin") version "1.3.0"
}
```
