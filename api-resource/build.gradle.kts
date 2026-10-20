plugins {
    id("java-library")
    // Maven Central (Central Portal) publication of this module: see the root build for the credentials
    alias(libs.plugins.maven.publish)
}

group = "cn.enaium.fabricpvz"
version = "1.2.2"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

dependencies {
    // The module wires itself into the game through the published events (`GameLifecycleEvents`,
    // `LawnEvents`), so it compiles against api-events; api-base (the event bus itself) comes with it
    // transitively. Both are `api` dependencies: a mod that uses this module needs their types too.
    api(project(":api-base"))
    api(project(":api-events"))

    // The entry point implements net.fabricmc.api.ModInitializer, and the tests instantiate it, so
    // Fabric Loader (runtime-provided by the loader, never published) is needed here too.
    compileOnly(libs.fabric.loader)
    testImplementation(libs.fabric.loader)

    compileOnly("cn.enaium.fabricpvz:pvz-portable:1.2.1")
    testImplementation("cn.enaium.fabricpvz:pvz-portable:1.2.1")

    // Compile-time only: the Mixin annotations the resource-reload Mixin is written against. The real
    // Mixin engine, with the same API, is supplied by Fabric Loader at runtime (through
    // launch libraries), so this artifact must never be shipped in the module jar.
    compileOnly("net.fabricmc:sponge-mixin:0.17.4+mixin.0.8.7")

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// The game's published POM depends on the API aggregator (Fabric parity), so resolving the game
// would drag the API back onto this module's class path (Gradle then refuses the implicit jar
// dependency). The modules are compiled and tested from their own sources, so exclude self.
configurations.configureEach {
    exclude(group = "cn.enaium.fabricpvz", module = "fabric-api")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 25
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// The publication to Maven Central (the Central Portal); the credentials and the signing key are the
// ones the root build documents.
mavenPublishing {
    publishToMavenCentral(true)
    signAllPublications()

    coordinates("cn.enaium.fabricpvz", "api-resource", "1.2.2")

    pom {
        name.set("api-resource")
        description.set("PvZ Fabric API: ship a mod's own images in the game's resource table")
        url.set("https://github.com/FabricPvZ/fabric-api")
        licenses {
            license {
                name.set("MIT")
                url.set("https://opensource.org/licenses/MIT")
            }
        }
        developers {
            developer {
                id.set("enaium")
                name.set("Enaium")
                url.set("https://github.com/Enaium")
            }
        }
        scm {
            url.set("https://github.com/FabricPvZ/fabric-api")
            connection.set("scm:git:git://github.com/FabricPvZ/fabric-api.git")
            developerConnection.set("scm:git:ssh://git@github.com/FabricPvZ/fabric-api.git")
        }
    }
}
