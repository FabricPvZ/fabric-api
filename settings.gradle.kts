// Fabric-API-shaped multi-module build: one Gradle module per API feature, each with its own
// fabric.mod.json and its own published artifact, plus this root project as the aggregator that mods
// depend on (`cn.enaium.fabricpvz:fabric-api`).
//
// The modules are Gradle subprojects here because they are built together, but they are consumed as
// Maven artifacts: the game, the loader and the mods resolve them from the Fabric maven exactly like the
// Fabric API modules are resolved from the Fabric maven.
pluginManagement {
    repositories {
        // The mod toolchain plugin (cn.enaium.fabricpvz.loom) comes from the Gradle Plugin Portal:
        // the aggregator does not apply it, but it asks it for the jar-in-jar mechanism its build
        // script uses (`JarInJar.include`), which means the plugin's artifact has to be resolvable
        // here.
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        // Standalone build: the loader and the game are consumed as Maven artifacts published by
        // their own builds (publishToMavenLocal), not as Gradle subprojects.
        mavenCentral()
        // net.fabricmc:fabric-loader (and the Mixin/ASM jars its metadata pulls in) lives here; Fabric
        // Loader is not on Maven Central. The API's own event bus is in-tree, so no Fabric API module
        // is resolved from this repository.
        maven {
            name = "Fabric"
            url = uri("https://maven.fabricmc.net/")
        }
    }
}

rootProject.name = "fabric-api"

include("api-base")
include("api-events")
include("api-plant")
include("api-zombie")
